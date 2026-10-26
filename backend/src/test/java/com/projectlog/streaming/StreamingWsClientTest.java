package com.projectlog.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyList;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectlog.auth.AdminSeeder;
import com.projectlog.auth.LoginController;
import com.projectlog.auth.TokenService;
import com.projectlog.query.FiltrosConsulta.Filtros;
import com.projectlog.query.dto.LogDto;
import com.projectlog.query.repository.LogsQueryRepository;
import com.projectlog.query.repository.LogsQueryRepository.FilaBatch;

/**
 * E7.2/E7.3/E7.4/E7.7/E7.8/E7.16 [L] (R7.33, design §7): cliente WebSocket
 * REAL del JDK ({@code java.net.http}) contra Tomcat REAL en RANDOM_PORT —
 * upgrade real, handler real ({@code LogSocketHandler}), interceptor real
 * ({@code JwtHandshakeInterceptor}); {@code LogsQueryRepository} MOCKEADO
 * ({@code @MockitoBean} — FilaBatch fabricados; el mock SIMULA el contrato de
 * porIds, el reorden REAL en Java con PG17 lo pinnean E7.5/E7.19) y
 * {@code ObjectMapper} REAL del contexto; el lote se dispara INVOCANDO el
 * bean real {@code LiveBroadcaster.onLotePersistido(...)} (broadcast a mano,
 * sin BD); JWT emitido por el {@code TokenService} del contexto (patrón
 * {@code PanelAuthMvcTest}); token expirado con TTL negativo (patrón
 * {@code TokenServiceTest}); {@code frontend-origin} por properties (R7.29 —
 * application-test.yml INTOCABLE).
 *
 * <p>Frontera C7 (P-2 CERRADO): SIN Testcontainers, SIN BD — el contexto
 * completo arranca con DataSource/Flyway/JdbcTemplate excluidos por
 * properties y las piezas de repositorio mockeadas; la pila real con PG17 la
 * certifica el smoke E7.x.</p>
 *
 * <p>Determinismo: cada cliente envía set_filters con UN project_id ÚNICO
 * por test y {@link #esperarFiltros(long, int)} espera (sondeo por el seam
 * del paquete, ATTR_FILTROS bajo lock) a que el handler REAL lo haya aplicado
 * ANTES de emitir el lote — jamás un sleep nominal.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                "projectlog.security.frontend-origin=http://consola-test.example" })
@ActiveProfiles("test")
class StreamingWsClientTest {

    @LocalServerPort
    private int puerto;

    @Autowired
    private LiveBroadcaster broadcaster;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LogSocketHandler handler;

    @Value("${JWT_SECRET}")
    private String jwtSecret; // SOLO para fabricar el token expirado de E7.7

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AdminSeeder adminSeeder;

    @MockitoBean
    private LogsQueryRepository logsQueryRepository;

    private final List<WebSocket> abiertos = new ArrayList<>();

    private static final long ESPERA_MS = 5000;
    private static final long NADA_MS = 500; // ventana de AUSENCIA

    // — helpers —

    /** Listener del JDK: colecciona textos y recuerda el close. */
    static class Escucha implements WebSocket.Listener {

        final BlockingQueue<String> textos = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> cerrado = new CompletableFuture<>();

        @Override
        public void onOpen(WebSocket ws) {
            // super responde el pong al ping automáticamente (RFC 6455)
            WebSocket.Listener.super.onOpen(ws);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data,
                boolean last) {
            textos.add(data.toString());
            return WebSocket.Listener.super.onText(ws, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode,
                String reason) {
            cerrado.complete(statusCode);
            return WebSocket.Listener.super.onClose(ws, statusCode, reason);
        }
    }

    private String urlConToken(String token) {
        return "ws://localhost:" + puerto + "/ws/logs?token=" + token;
    }

    /** Conexión REAL por el interceptor: sin header Origin → continúa (S7-12). */
    private WebSocket conectar(Escucha escucha, String url) {
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(url), escucha)
                .join(); // WebSocketHandshakeException dentro de CompletionException
        abiertos.add(ws);
        return ws;
    }

    private Escucha escucha() {
        return new Escucha();
    }

    private String pollTexto(Escucha escucha, long ms) throws InterruptedException {
        return escucha.textos.poll(ms, TimeUnit.MILLISECONDS);
    }

    private void enviar(WebSocket ws, String json) {
        ws.sendText(json, true).join();
    }

    /** LogDto COMPLETO (11 componentes, nulls incluidos — D-C7-1). */
    private static LogDto logDto(long id, long projectId, String level,
            String environment, String mensaje) {
        return new LogDto(id, projectId, level, environment,
                "2026-10-25T10:00:00Z", "2026-10-25T10:00:00Z", mensaje, null,
                null, "fuente-test", "corr-" + id);
    }

    private static FilaBatch fila(LogDto log) {
        return new FilaBatch(log,
                OffsetDateTime.parse("2026-10-25T10:00:00.123456Z"));
    }

    private void stubPorIds(List<Long> ids, List<FilaBatch> filas) {
        org.mockito.Mockito.when(logsQueryRepository.porIds(ids))
                .thenReturn(filas);
    }

    /**
     * Espera determinista (sondeo del ATTR_FILTROS — mismo paquete, patrón
     * oficial de acceso bajo lock) a que EXACTAMENTE {@code esperadas}
     * sesiones registradas del handler REAL tengan el {@code projectId}
     * aplicado. El projectId ÚNICO por test aísla de sesiones moribundas de
     * tests anteriores (cada cual con su propio id).
     */
    private void esperarFiltros(long projectId, int esperadas)
            throws InterruptedException {
        long fin = System.currentTimeMillis() + ESPERA_MS * 2;
        int vistas;
        do {
            vistas = 0;
            for (ConcurrentWebSocketSessionDecorator s : handler
                    .sesionesVivas()) {
                Filtros f;
                synchronized (s.getAttributes()) {
                    f = (Filtros) s.getAttributes()
                            .get(LogSocketHandler.ATTR_FILTROS);
                }
                if (f != null
                        && Long.valueOf(projectId).equals(f.projectId())) {
                    vistas++;
                }
            }
            if (vistas >= esperadas) {
                return;
            }
            Thread.sleep(10);
        } while (System.currentTimeMillis() < fin);
        fail("set_filters no aplicado para project " + projectId
                + " (vistas=" + vistas + ", esperadas=" + esperadas + ")");
    }

    @AfterEach
    void cerrarClientes() {
        for (WebSocket ws : abiertos) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "fin de prueba");
            } catch (RuntimeException ex) {
                // ya cerrado — sin aserto
            }
        }
        abiertos.clear();
    }

    // — E7.7 —
    @Test
    void e77Handshake401PreUpgradeTresCausasSinOraculo() {
        // (a) sin ?token — pinneado ADEMÁS el CUERPO VACÍO del rechazo (H5):
        // los interceptores JAMÁS escriben cuerpo en el 401 pre-upgrade.
        // Matiz técnico de la API pública del JDK (javadoc getResponse()):
        // en un fallo de upgrade el cuerpo llega por HttpResponse.body(); la
        // clase del cuerpo no está especificada entre implementaciones — se
        // acepta String EN BLANCO o null como "vacío" (ambos = sin
        // contenido); si el JDK lo expusiera como otra clase sería una
        // LIMITACIÓN TÉCNICA documentada, no un cuerpo con contenido.
        assertThatThrownBy(() -> conectar(escucha(),
                "ws://localhost:" + puerto + "/ws/logs"))
                .isInstanceOfSatisfying(CompletionException.class, ex -> {
                    HttpResponse<?> r = ((WebSocketHandshakeException)
                            ex.getCause()).getResponse();
                    assertThat(r.statusCode()).isEqualTo(401);
                    Object datos = r.body();
                    if (datos instanceof String texto) {
                        assertThat(texto.isBlank())
                                .as("cuerpo del 401 pre-upgrade vacío")
                                .isTrue();
                    } else {
                        assertThat(datos)
                                .as("cuerpo del 401 pre-upgrade ausente")
                                .isNull();
                    }
                });
        // (b) token EXPIRADO (TTL negativo — patrón TokenServiceTest), misma firma
        String expirado = new TokenService(jwtSecret, -8)
                .issue("admin", "ADMIN").token();
        assertThatThrownBy(() -> conectar(escucha(), urlConToken(expirado)))
                .isInstanceOfSatisfying(CompletionException.class,
                        ex -> assertThat(
                                ((WebSocketHandshakeException) ex.getCause())
                                        .getResponse().statusCode())
                                .isEqualTo(401));
        // (c) token inválido (basura) — MISMO status (sin oráculo de causa)
        assertThatThrownBy(() -> conectar(escucha(),
                urlConToken("token-basura-invalido")))
                .isInstanceOfSatisfying(CompletionException.class,
                        ex -> assertThat(
                                ((WebSocketHandshakeException) ex.getCause())
                                        .getResponse().statusCode())
                                .isEqualTo(401));
        // NUNCA 101: los 3 fallos son WebSocketHandshakeException, nunca un
        // upgrade consumado (los causas de arriba ya lo pinnean).
    }

    // — E7.8 —
    @Test
    void e78OriginValidadaExactaYJamasComodin() throws Exception {
        // (a) Origin ≠ frontend-origin de test (EXACTA, sin normalizar) → 403
        assertThatThrownBy(() -> HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .header("Origin", "http://evil.example")
                .buildAsync(URI.create(urlConToken(tokenValido())), escucha())
                .join())
                .isInstanceOfSatisfying(CompletionException.class,
                        ex -> assertThat(
                                ((WebSocketHandshakeException) ex.getCause())
                                        .getResponse().statusCode())
                                .isEqualTo(403));
        // (b) Origin == frontend-origin → 101 + ciclo set_filters→batch normal
        Escucha conOrigin = escucha();
        WebSocket wsOrigin = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .header("Origin", "http://consola-test.example")
                .buildAsync(URI.create(urlConToken(tokenValido())), conOrigin)
                .join();
        abiertos.add(wsOrigin);
        enviar(wsOrigin, jsonSetFilters(77L));
        stubPorIds(List.of(771L), List.of(fila(
                logDto(771L, 77L, "ERROR", "production", "origen-valido"))));
        esperarFiltros(77L, 1);
        broadcaster.onLotePersistido(77L, ahora(), List.of(771L));
        String frame = awaitFrame(conOrigin);
        assertThat(leer(frame, "batch_id")).matches(
                "^b_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
        // (c) SIN header Origin → 101 (cliente no-browser legítimo — S7-12):
        // todos los demás tests conectan sin Origin y reciben frames.
        assertThat(handler.sesionesVivas()).isNotEmpty(); // upgrade ocurrido
    }

    // — E7.2 —
    @Test
    void e72FrameTrasLoteFormaExacta35() throws Exception {
        Escucha escucha = escucha();
        WebSocket ws = conectar(escucha, urlConToken(tokenValido()));
        enviar(ws, jsonSetFilters(71L));
        stubPorIds(List.of(711L, 712L), List.of(
                fila(logDto(711L, 71L, "ERROR", "production", "primer log")),
                fila(logDto(712L, 71L, "WARN", "staging", "segundo log"))));
        esperarFiltros(71L, 1);
        OffsetDateTime t = ahora();

        broadcaster.onLotePersistido(71L, t, List.of(711L, 712L));

        String frame = awaitFrame(escucha);
        // EXACTAMENTE UN TextMessage por lote (R7.15)
        assertThat(pollTexto(escucha, NADA_MS)).as("no hay 2º frame").isNull();
        JsonNode root = objectMapper.readTree(frame);
        // 5 claves top-level EN ORDEN (D-7.3 — LinkedHashMap literal §3.5)
        assertThat(nombres(root)).containsExactly("type", "batch_id",
                "project_id", "received_at", "logs");
        assertThat(root.get("type").asText()).isEqualTo("batch");
        assertThat(root.get("batch_id").asText()).matches(
                "^b_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
        assertThat(root.get("project_id").asLong()).isEqualTo(71L);
        // received_at == render del común truncado a segundos (D-C7-2)
        assertThat(root.get("received_at").asText()).isEqualTo(
                t.format(LoginController.EXPIRES_AT_FORMATTER));
        // cada elemento con EXACTAMENTE las 11 claves del LogDto EN ORDEN
        // (nulls incluidos — D-C7-1)
        JsonNode logs = root.get("logs");
        assertThat(logs).hasSize(2);
        List<String> claves11 = List.of("id", "project_id", "level",
                "environment", "timestamp", "received_at", "message",
                "exception", "metadata", "source", "correlation_id");
        for (JsonNode log : logs) {
            assertThat(nombres(log)).containsExactlyElementsOf(claves11);
        }
        assertThat(logs.get(0).get("message").asText()).isEqualTo("primer log");
        assertThat(logs.get(1).get("id").asLong()).isEqualTo(712L);
        assertThat(logs.get(0).get("metadata").isNull()).isTrue();
        assertThat(logs.get(0).get("exception").isNull()).isTrue();
    }

    // — E7.3 —
    @Test
    void e73BatchDeUnoOrdenDelLoteYIdsVacioSinFrame() throws Exception {
        Escucha escucha = escucha();
        WebSocket ws = conectar(escucha, urlConToken(tokenValido()));
        enviar(ws, jsonSetFilters(72L));
        esperarFiltros(72L, 1);

        // lote de UNO → frame de 1 log (individual = batch de 1, literal §3.5)
        stubPorIds(List.of(72L), List.of(
                fila(logDto(72L, 72L, "INFO", "development", "solo"))));
        broadcaster.onLotePersistido(72L, ahora(), List.of(72L));
        String uno = awaitFrame(escucha);
        assertThat(objectMapper.readTree(uno).get("logs")).hasSize(1);
        String batchIdUno = objectMapper.readTree(uno).get("batch_id").asText();

        // orden del lote PRESERVADO: ids [5,2,9,1,7] → ids en el frame
        // [5,2,9,1,7] (el mock SIMULA el contrato de porIds — el reorden REAL
        // en Java con PG17 lo pinnean E7.5/E7.19)
        List<Long> ids = List.of(5L, 2L, 9L, 1L, 7L);
        List<FilaBatch> filas = new ArrayList<>();
        for (long id : ids) {
            filas.add(fila(logDto(id, 72L, "INFO", "development", "l-" + id)));
        }
        stubPorIds(ids, filas);
        broadcaster.onLotePersistido(72L, ahora(), ids);
        String varios = awaitFrame(escucha);
        JsonNode logs = objectMapper.readTree(varios).get("logs");
        assertThat(logs).hasSize(5);
        List<Long> ordenFrame = new ArrayList<>();
        for (JsonNode log : logs) {
            ordenFrame.add(log.get("id").asLong());
        }
        assertThat(ordenFrame).containsExactly(5L, 2L, 9L, 1L, 7L);
        // batch_id distintos entre frames
        assertThat(objectMapper.readTree(varios).get("batch_id").asText())
                .isNotEqualTo(batchIdUno);

        // ids VACÍO → NINGÚN frame (defensiva R7.17 — distribución interna)
        broadcaster.onLotePersistido(72L, ahora(), List.of());
        assertThat(pollTexto(escucha, NADA_MS)).as("ids vacío → nada").isNull();
        // y NINGÚN porIds ADICIONAL: el mock acumuló las DOS invocaciones
        // legítimas de arriba (lote de 1 y lote de 5 — cada awaitFrame retorna
        // DESPUÉS de su porIds); la vacía retorna ANTES (distribuir, paso 1)
        org.mockito.Mockito.verify(logsQueryRepository,
                org.mockito.Mockito.times(2)).porIds(anyList());
    }

    // — E7.4 —
    @Test
    void e74SetFiltersPorSesionTresClientes() throws Exception {
        Escucha e1 = escucha();
        Escucha e2 = escucha();
        Escucha e3 = escucha();
        WebSocket c1 = conectar(e1, urlConToken(tokenValido()));
        WebSocket c2 = conectar(e2, urlConToken(tokenValido()));
        conectar(e3, urlConToken(tokenValido())); // c3 SIN set_filters
        enviar(c1, jsonSetFilters(73L));
        enviar(c2, jsonSetFilters(74L));
        org.mockito.Mockito.when(logsQueryRepository.porIds(anyList()))
                .thenAnswer(inv -> List.of(fila(logDto(9L, 73L, "INFO",
                        "development", "x"))));

        esperarFiltros(73L, 1);
        esperarFiltros(74L, 1);
        // lote del 73: SÓLO c1 (c2/c3 nada — c3 sin calificar no recibe, S7-10)
        broadcaster.onLotePersistido(73L, ahora(), List.of(9L));
        assertThat(awaitFrame(e1)).contains("batch");
        assertThat(pollTexto(e2, NADA_MS)).isNull();
        assertThat(pollTexto(e3, NADA_MS)).isNull();

        // lote del 74: sólo c2
        broadcaster.onLotePersistido(74L, ahora(), List.of(9L));
        assertThat(awaitFrame(e2)).contains("batch");
        assertThat(pollTexto(e1, NADA_MS)).isNull();
        assertThat(pollTexto(e3, NADA_MS)).isNull();

        // c2 RECALIFICA a project 73 en caliente → lote del 73: c1 Y c2
        enviar(c2, jsonSetFilters(73L));
        esperarFiltros(73L, 2);
        broadcaster.onLotePersistido(73L, ahora(), List.of(9L));
        assertThat(awaitFrame(e1)).contains("batch");
        assertThat(awaitFrame(e2)).contains("batch");
        assertThat(pollTexto(e3, NADA_MS)).isNull();
    }

    // — E7.16 —
    @Test
    void e76NoContratoIgnoradoSinCierreYVigentesConservados() throws Exception {
        Escucha e1 = escucha();
        WebSocket ws = conectar(e1, urlConToken(tokenValido()));
        enviar(ws, jsonSetFilters(75L));
        esperarFiltros(75L, 1);
        org.mockito.Mockito.when(logsQueryRepository.porIds(anyList()))
                .thenAnswer(inv -> List.of(fila(logDto(1L, 75L, "INFO",
                        "development", "n"))));

        // (a) texto plano → ignorado, sesión SIGUE
        enviar(ws, "texto-plano-no-json");
        assertThat(pollTexto(e1, NADA_MS)).as("texto plano no genera frame")
                .isNull();
        // (b) {"type":"ping"} (no-contrato) → idem
        enviar(ws, "{\"type\":\"ping\"}");
        assertThat(pollTexto(e1, NADA_MS)).isNull();
        // (c) frame BINARIO → idem (R7.12)
        ws.sendBinary(ByteBuffer.allocate(8), true).join();
        assertThat(pollTexto(e1, NADA_MS)).isNull();
        // la sesión SIGUE y el frame del lote SÍ llega (la sesión no cerró)
        broadcaster.onLotePersistido(75L, ahora(), List.of(1L));
        assertThat(awaitFrame(e1)).contains("batch");

        // (d) INVÁLIDOS tras un set_filters VÁLIDO → conserva los VIGENTES
        // (R-7.g: NO cierra, NO aplica, NO responde)
        List<String> invalidos = List.of(
                // check 2 no numérico:
                "{\"type\":\"set_filters\",\"filters\":{\"project_id\":\"abc\"}}",
                // check 4 level inválido:
                "{\"type\":\"set_filters\",\"filters\":{\"project_id\":75,\"level\":\"error\"}}",
                // clave EXTRA (no-contrato — fuera del conjunto cerrado de 6):
                "{\"type\":\"set_filters\",\"filters\":{\"project_id\":75,\"limit\":10}}",
                // JSON malformado:
                "{",
                // environment "" PRESENTE (inválido en environment):
                "{\"type\":\"set_filters\",\"filters\":{\"project_id\":75,\"environment\":\"\"}}",
                // booleano en environment (no String ni null):
                "{\"type\":\"set_filters\",\"filters\":{\"project_id\":75,\"environment\":true}}"
        );
        for (String invalido : invalidos) {
            enviar(ws, invalido);
            broadcaster.onLotePersistido(75L, ahora(), List.of(1L));
            // el siguiente lote llega según el VIGENTE (project 75)
            assertThat(awaitFrame(e1)).as("vigente tras inválido: " + invalido)
                    .contains("batch");
        }
        // y el socket sigue abierto (NUNCA cierra por esto): más texto OK
        enviar(ws, "otro-no-contrato");
        assertThat(pollTexto(e1, NADA_MS)).isNull();
    }

    @Test
    void e76eSinReplayTrasDesconexion() throws Exception {
        // lote emitido mientras el cliente está desconectado NO aparece al
        // reconectar (R7.21/S7-11); lo entrado no se re-emite.
        Escucha e1 = escucha();
        WebSocket ws = conectar(e1, urlConToken(tokenValido()));
        enviar(ws, jsonSetFilters(76L));
        esperarFiltros(76L, 1);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "caida").join();
        org.mockito.Mockito.when(logsQueryRepository.porIds(anyList()))
                .thenAnswer(inv -> List.of(fila(logDto(42L, 76L, "INFO",
                        "development", "gap"))));
        broadcaster.onLotePersistido(76L, ahora(), List.of(42L)); // emitido sin oyentes

        // NUEVA conexión + set_filters de nuevo (obligación del cliente) — con
        // project ÚNICO (78) para no heredar la sesión moribunda del 76
        Escucha e2 = escucha();
        WebSocket ws2 = conectar(e2, urlConToken(tokenValido()));
        enviar(ws2, jsonSetFilters(78L));
        esperarFiltros(78L, 1);
        assertThat(pollTexto(e2, NADA_MS)).as("sin replay del gap").isNull();
        // un lote NUEVO SÍ llega
        broadcaster.onLotePersistido(78L, ahora(), List.of(42L));
        assertThat(awaitFrame(e2)).contains("42");
    }

    /**
     * H13 — vacío del matching completo R7.16 (decisión del orquestador:
     * R7.16 es contrato literal; §2.5 no exigía pareja): sesión con el
     * lote-filtros COMPLETO {project_id, from, to, text} — from/to
     * INCLUSIVOS sobre el RAW del común (== from y == to LEGAL) y texto
     * crudo contains case-INSENSITIVE (texto de prueba con MAYÚSCULAS en el
     * filtro y minúsculas en el mensaje). Mecánica idéntica a E7.2: lote
     * disparado invocando {@code onLotePersistido} con FilaBatch mockeados.
     */
    @Test
    void r716MatchingFromToInclusivoYTextoCaseInsensitive() throws Exception {
        Escucha escucha = escucha();
        WebSocket ws = conectar(escucha, urlConToken(tokenValido()));
        // filtro con from/to/text; la ventana [09:30Z, 10:30Z] cubre al RAW
        // 10:00:00.123456Z y sus fronteras exactas; el texto con mayúscula
        // ("Timeout") debe matchear el mensaje en minúscula ("timeout …")
        enviar(ws, "{\"type\":\"set_filters\",\"filters\":{\"project_id\":79,"
                + "\"environment\":null,\"level\":null,"
                + "\"from\":\"2026-10-25T09:30:00Z\","
                + "\"to\":\"2026-10-25T10:30:00Z\",\"text\":\"Timeout\"}}");
        stubPorIds(List.of(791L), List.of(fila(logDto(791L, 79L, "INFO",
                "production", "timeout de consulta"))));
        esperarFiltros(79L, 1);

        OffsetDateTime dentro = OffsetDateTime.parse(
                "2026-10-25T10:00:00.123456Z");
        broadcaster.onLotePersistido(79L, dentro, List.of(791L));
        assertThat(awaitFrame(escucha)).contains("timeout de consulta");
        // EXACTAMENTE un TextMessage por lote (R7.15)
        assertThat(pollTexto(escucha, NADA_MS)).as("no hay 2º frame")
                .isNull();

        // frontera INCLUSIVA == from → también frame
        broadcaster.onLotePersistido(79L,
                OffsetDateTime.parse("2026-10-25T09:30:00Z"), List.of(791L));
        assertThat(awaitFrame(escucha)).contains("batch");
        assertThat(pollTexto(escucha, NADA_MS)).as("no hay 2º frame")
                .isNull();
        // frontera INCLUSIVA == to → también frame
        broadcaster.onLotePersistido(79L,
                OffsetDateTime.parse("2026-10-25T10:30:00Z"), List.of(791L));
        assertThat(awaitFrame(escucha)).contains("batch");
        assertThat(pollTexto(escucha, NADA_MS)).as("no hay 2º frame")
                .isNull();

        // lote JUSTO FUERA del rango (< from) → NINGÚN frame
        broadcaster.onLotePersistido(79L,
                OffsetDateTime.parse("2026-10-25T09:29:59.999999Z"),
                List.of(791L));
        assertThat(pollTexto(escucha, NADA_MS)).as("fuera por from no genera frame")
                .isNull();
        // lote JUSTO FUERA (> to) → NINGÚN frame
        broadcaster.onLotePersistido(79L,
                OffsetDateTime.parse("2026-10-25T10:30:00.000001Z"),
                List.of(791L));
        assertThat(pollTexto(escucha, NADA_MS)).as("fuera por to no genera frame")
                .isNull();
        // el texto filtra: mensaje SIN el texto (rango sí) → NINGÚN frame
        stubPorIds(List.of(792L), List.of(fila(logDto(792L, 79L, "INFO",
                "production", "red interna estable"))));
        broadcaster.onLotePersistido(79L, dentro, List.of(792L));
        assertThat(pollTexto(escucha, NADA_MS)).as("texto no contenido no genera frame")
                .isNull();
    }

    // — helpers de JSON / tiempos —
    private String tokenValido() {
        return tokenService.issue("admin", "ADMIN").token();
    }

    private static OffsetDateTime ahora() {
        return OffsetDateTime.now().withNano(0);
    }

    private static String jsonSetFilters(long projectId) {
        return "{\"type\":\"set_filters\",\"filters\":{\"project_id\":"
                + projectId + ",\"environment\":null,\"level\":null,"
                + "\"from\":null,\"to\":null,\"text\":null}}";
    }

    private String awaitFrame(Escucha escucha) throws InterruptedException {
        String frame = pollTexto(escucha, ESPERA_MS);
        assertThat(frame).as("frame batch esperado").isNotNull();
        return frame;
    }

    private List<String> nombres(JsonNode nodo) {
        List<String> nombres = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(nombres::add);
        return nombres;
    }

    private String leer(String frame, String campo) {
        try {
            return objectMapper.readTree(frame).get(campo).asText();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}