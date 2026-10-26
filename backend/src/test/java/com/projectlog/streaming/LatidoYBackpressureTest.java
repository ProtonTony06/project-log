package com.projectlog.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
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
 * E7.12/E7.13 [L] (R7.33, design §7): latido + backpressure con properties
 * ENCOGIDAS por {@code @SpringBootTest(properties=…)} (R7.29 — el
 * {@code application-test.yml} NO se toca): heartbeat 1 s / idle 2 s;
 * send-time-limit 1 ms / buffer 1 byte.
 *
 * <p>Las sesiones MUDA y LENTA son mocks de {@code WebSocketSession}
 * registrados en el mapa REAL del handler vía
 * {@code handler.afterConnectionEstablished(mock)} (el cliente JDK REAL
 * responde pong AUTOMÁTICAMENTE — no puede simular mudez); el cliente JDK
 * REAL de E7.12b/c pasa por el upgrade REAL (interceptor + handler +
 * scheduler reales). El 1013 de E7.13 corre sobre el DECORADOR REAL de
 * {@code ConcurrentWebSocketSessionDecorator} (spring-websocket 6.2.19):
 * {@code SessionLimitExceededException} por sendTime (a) y por buffer (b) →
 * {@code cerrarPorLentitud} → close <b>1013 al DELEGADO</b> (el decorador
 * sustituye el status de todo close posterior al vencimiento por
 * {@code SESSION_NOT_RELIABLE} — por eso el 1013 contractual sale al
 * delegado) + limpieza (R-7.c) + broadcast posterior INTACTO a las demás +
 * {@code onLotePersistido} retorna limpio (R7.23).</p>
 *
 * <p>Limpieza (E7.12a/E7.13a): {@code afterConnectionClosed} es el ÚNICO
 * punto de remove (R7.13/R-7.c) y es el CONTENEDOR quien lo dispara; para
 * una mock no hay contenedor: el test SIMULA ese dispatch llamando
 * {@code handler.afterConnectionClosed(mock, …)} tras el close observado
 * (el close REAL lo ejecuta el scheduler real — eso es lo pinnado). Los
 * clientes REALES sí son limpiados por el contenedor real (E7.12c espera su
 * remove SIN simulación alguna).</p>
 *
 * <p>Mismo esquema que {@link StreamingWsClientTest} (parejas [L]/[S] §2.5):
 * {@code LogsQueryRepository} MOCKEADO, {@code ObjectMapper} REAL, lote
 * disparado invocando {@code LiveBroadcaster.onLotePersistido(...)}; JWT por
 * el {@code TokenService} del contexto; higiene: NINGÚN log con token,
 * filtros ni mensajes (R7.7/R7.12/R7.35).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                "projectlog.security.frontend-origin=http://consola-test.example",
                "projectlog.streaming.heartbeat-seconds=1",    // encogido (E7.12)
                "projectlog.streaming.idle-timeout-seconds=2", // encogido (E7.12)
                "projectlog.streaming.send-time-limit-ms=1",   // encogido (E7.13a)
                "projectlog.streaming.buffer-size-bytes=1" })  // encogido (E7.13b)
@ActiveProfiles("test")
class LatidoYBackpressureTest {

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
    private String jwtSecret;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AdminSeeder adminSeeder;

    @MockitoBean
    private LogsQueryRepository logsQueryRepository;

    private final List<WebSocket> abiertos = new ArrayList<>();

    private static final long ESPERA_MS = 9000;

    // — helpers ——

    /** Listener del JDK (idéntico al de StreamingWsClientTest). */
    static class Escucha implements WebSocket.Listener {

        final BlockingQueue<String> textos = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> cerrado = new CompletableFuture<>();

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

    /**
     * Mock base: id único + attributes REALES (el handler los usa bajo
     * lock). Si {@code drena} es no-null, su {@code sendMessage} bloquea
     * SOLO en los TextMessage (los pings de control 0x9 pasan SIEMPRE — el
     * scheduler monohilo jamás queda bloqueado por el mock; el TCP lento
     * real del cliente lo modela el frame batch del broadcast).
     */
    private WebSocketSession mockSesion(String id, CountDownLatch drena)
            throws IOException {
        WebSocketSession mock = Mockito.mock(WebSocketSession.class);
        Mockito.when(mock.getId()).thenReturn(id);
        Mockito.when(mock.getAttributes()).thenReturn(new HashMap<>());
        Mockito.doAnswer(inv -> {
            if (drena != null && inv.getArgument(0) instanceof TextMessage) {
                drena.await(); // InterruptedException — Answer lo permite
            }
            return null;
        }).when(mock).sendMessage(any()); // stubbing: la IOException es del
                                          // signature, no corre
        return mock;
    }

    private WebSocket conectar(Escucha escucha, String url) {
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(url), escucha).join();
        abiertos.add(ws);
        return ws;
    }

    private String urlConToken(String token) {
        return "ws://localhost:" + puerto + "/ws/logs?token=" + token;
    }

    private void enviar(WebSocket ws, String json) {
        ws.sendText(json, true).join();
    }

    private String tokenValido() {
        return tokenService.issue("admin", "ADMIN").token();
    }

    private static LogDto logDto(long id, long projectId, String mensaje) {
        return new LogDto(id, projectId, "INFO", "production",
                "2026-10-26T10:00:00Z", "2026-10-26T10:00:00Z", mensaje, null,
                null, "fuente-test", "corr-" + id);
    }

    private static FilaBatch fila(LogDto log) {
        return new FilaBatch(log,
                OffsetDateTime.parse("2026-10-26T10:00:00.123456Z"));
    }

    private static FilaBatch filaProyecto(long id, long projectId,
            String mensaje) {
        return fila(logDto(id, projectId, mensaje));
    }

    /**
     * Califica una mock DIRECTAMENTE con el atributo de filtros bajo lock
     * (el set_filters REAL lo aplica el handler a las sesiones reales; para
     * las mocks el atributo es lo que el matching del broadcaster Lee).
     */
    private void calificar(WebSocketSession mock, long projectId) {
        synchronized (mock.getAttributes()) {
            mock.getAttributes().put(LogSocketHandler.ATTR_FILTROS,
                    new Filtros(projectId, null, null, null, null, null, null));
        }
    }

    /** Refresca el umbral de pong de una mock (su pong "puntual"). */
    private void pongRefresco(WebSocketSession mock) {
        synchronized (mock.getAttributes()) {
            mock.getAttributes().put(LogSocketHandler.ATTR_ULTIMO_PONG,
                    System.nanoTime());
        }
    }

    private Optional<ConcurrentWebSocketSessionDecorator> envueltaDeId(
            String id) {
        return handler.sesionesVivas().stream().filter(
                envuelta -> id.equals(envuelta.getDelegate().getId()))
                .findFirst();
    }

    /** Espera condicional con sondeo (sin Awaitility — NO hay dep nueva). */
    private static boolean esperar(long ms,
            java.util.function.BooleanSupplier cond) {
        long fin = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < fin) {
            if (cond.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return cond.getAsBoolean();
    }

    /** Espera a que la envuelta esté IN-FLIGHT (drain ya en marcha —
     *  {@code sendStartTime} > 0) y bloqueada en el mock, o FAIL. */
    private void esperarInFlight(
            ConcurrentWebSocketSessionDecorator envuelta) {
        if (!esperar(ESPERA_MS, () -> envuelta.getTimeSinceSendStarted() > 0)) {
            fail("el envío no llegó a quedar in-flight (sendStartTime == 0)");
        }
    }

    /** Espera a que la sesión con ese id salga del registro, o FAIL. */
    private void esperarFueronDelRegistro(String id) {
        if (!esperar(ESPERA_MS, () -> envueltaDeId(id).isEmpty())) {
            fail("la sesión " + id + " sigue registrada (sin limpieza)");
        }
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

    // — E7.12 — cierre por latido (1001), limpieza y reconexión de cero ——
    @Test
    void e12LatidoCierraMuda1001LimpiaYElRealPermaneceYReconexion()
            throws Exception {
        // (a) sesión MUDA: mock SIN pong, registrada en el mapa REAL — el
        //     scheduler REAL la cierra con GOING_AWAY (1001, decisión S7-9):
        WebSocketSession muda = mockSesion("e12-muda", null);
        handler.afterConnectionEstablished(muda);
        assertThat(envueltaDeId("e12-muda")).as("registrada").isPresent();

        // (b) cliente JDK REAL en el MISMO contexto: responde pong
        //     AUTOMÁTICAMENTE (el scheduler real le pina cada 1 s):
        Escucha escuchaVivo = new Escucha();
        WebSocket vivo = conectar(escuchaVivo, urlConToken(tokenValido()));

        // (a) el close 1001 de la mock por el scheduler REAL:
        Mockito.verify(muda, Mockito.timeout(ESPERA_MS))
                .close(CloseStatus.GOING_AWAY);
        // limpieza R-7.c: el contenedor dispara afterConnectionClosed (único
        // punto de remove) — para la mock se SIMULA el dispatch (el close
        // 1001 REAL ya ocurrió y fue pinnado arriba):
        handler.afterConnectionClosed(muda, CloseStatus.GOING_AWAY);
        esperarFueronDelRegistro("e12-muda");

        // (b) el real PERMANECE tras varios ciclos (> idle 2 s) — aserto de
        //     NO-cierre (java.net.http.WebSocket no tiene isOpen: los canales
        //     abiertos se consultan por isInputClosed/isOutputClosed):
        Thread.sleep(4200); // ≥ 4 ticks con heartbeat 1 s
        assertThat(vivo.isInputClosed()).as("entrada del real sigue abierta")
                .isFalse();
        assertThat(vivo.isOutputClosed()).as("salida del real sigue abierta")
                .isFalse();
        assertThat(escuchaVivo.cerrado.isDone())
                .as("sin close del latido para el real").isFalse();

        // (c) cierre limpio de la conexión real; su remove lo hace el
        //     CONTENEDOR real (sin simulación), y una NUEVA conexión completa
        //     funciona de CERO (handshake + set_filters + frame — S7-11):
        vivo.sendClose(WebSocket.NORMAL_CLOSURE, "cierre de prueba").join();
        if (!esperar(ESPERA_MS, () -> handler.sesionesVivas().stream()
                .noneMatch(envuelta -> envuelta.getDelegate()
                        instanceof StandardWebSocketSession))) {
            fail("la sesión real cerrada no fue limpiada por el contenedor");
        }
        stubPorIds(812L, 8121L, "renacida");
        Escucha escuchaNueva = new Escucha();
        WebSocket renacida = conectar(escuchaNueva, urlConToken(tokenValido()));
        enviar(renacida, "{\"type\":\"set_filters\",\"filters\":{"
                + "\"project_id\":812,\"environment\":null,\"level\":null,"
                + "\"from\":null,\"to\":null,\"text\":null}}");
        if (!esperar(ESPERA_MS, () -> calificadasCon(812L) >= 1)) {
            fail("set_filters de la nueva conexión no aplicado");
        }
        broadcaster.onLotePersistido(812L,
                OffsetDateTime.parse("2026-10-26T10:00:00.123Z"),
                List.of(8121L));
        String frame = escuchaNueva.textos.poll(ESPERA_MS,
                TimeUnit.MILLISECONDS);
        assertThat(frame).as("la reconexión funciona de CERO (frame)")
                .isNotNull();
        assertThat(objectMapper.readTree(frame).get("project_id").asLong())
                .isEqualTo(812L);
        assertThat(objectMapper.readTree(frame).get("type").asText())
                .isEqualTo("batch");
    }

    // — E7.13 — cierre 1013 por AMBOS límites y los demás INTACTOS ————
    @Test
    void e13Cierre1013AmbosLimitesYLosDemasIntactos() throws Exception {
        // (a) sendTimeLimit (1 ms del CONTEXTO) vía el DECORADOR REAL del
        //     handler: la lenta bloquea en el TextMessage del broadcast.
        CountDownLatch drena = new CountDownLatch(1);
        WebSocketSession lenta = mockSesion("e13-lenta", drena);
        handler.afterConnectionEstablished(lenta);
        calificar(lenta, 901L);
        // sesión SANA (instantánea) — la "otra sesión" del inciso (c):
        BlockingQueue<String> sanaEnviados = new LinkedBlockingQueue<>();
        WebSocketSession sana = mockSesion("e13-sana", null);
        Mockito.doAnswer(inv -> {
            if (inv.getArgument(0) instanceof TextMessage texto) {
                sanaEnviados.add(texto.getPayload());
            }
            return null;
        }).when(sana).sendMessage(any());
        handler.afterConnectionEstablished(sana);
        calificar(sana, 901L);
        stubPorIds(901L, 9011L, "del-lote");

        ConcurrentWebSocketSessionDecorator envueltaLenta =
                envueltaDeId("e13-lenta").orElseThrow();

        // broadcast: el hilo del executor despliega el frame → BLOQUEADO en
        // el sendMessage del mock lento (el TCP lento modelado):
        OffsetDateTime t1 = OffsetDateTime.now().withNano(0);
        broadcaster.onLotePersistido(901L, t1, List.of(9011L));
        esperarInFlight(envueltaLenta);

        // el SIGUIENTE tick no puede flusher (flushLock en vuelo) →
        // checkSessionLimits → sendTime (1 ms) VENCIDO →
        // SessionLimitExceededException en el hilo del SCHEDULER →
        // cerrarPorLentitud → close 1013 AL DELEGADO (el decorador, una vez
        // limitExceeded, reemplazaría el status por SESSION_NOT_RELIABLE):
        Mockito.verify(lenta, Mockito.timeout(ESPERA_MS)).close(
                Mockito.argThat(estado -> estado != null
                        && estado.getCode() == 1013));
        // la mudez de la lenta nunca produce un 1001 (el latido no la GC-a:
        // cada tick falla ANTES, por sendTime):
        Mockito.verify(lenta, Mockito.never()).close(
                Mockito.argThat(estado -> estado != null
                        && estado.getCode() == 1001));

        // limpieza R-7.c (dispatch SIMULADO — la mock no tiene contenedor):
        handler.afterConnectionClosed(lenta, new CloseStatus(1013));
        esperarFueronDelRegistro("e13-lenta");
        // liberar el cuello: el frame "drena" y el executor queda LIBRE:
        drena.countDown();
        if (!esperar(ESPERA_MS,
                () -> envueltaLenta.getTimeSinceSendStarted() == 0)) {
            fail("el hilo del broadcast no liberó la envuelta de la lenta");
        }

        // (c) tras el 1013 de la lenta, un broadcast posterior llega INTACTO
        //     a las OTRAS sesiones y onLotePersistido retorna LIMPIO (R7.23 —
        //     NUNCA bloquea ni propaga: SOLO encola):
        // DRENAJE primero: la sana YA recibió el frame del lote t1 (el
        // broadcast es para TODAS las sesiones calificadas) — el poll de (c)
        // debe leer SOLO el frame NUEVO, no el viejo.
        sanaEnviados.clear();
        pongRefresco(sana); // la sana "responde pong" — sigue viva
        OffsetDateTime t2 = OffsetDateTime.now().withNano(0);
        long t0 = System.nanoTime();
        broadcaster.onLotePersistido(901L, t2, List.of(9022L));
        long encoladoNanos = System.nanoTime() - t0;
        assertThat(encoladoNanos).as("onLotePersistido NO bloquea (encola)")
                .isLessThan(500_000_000L);
        String frameSana = sanaEnviados.poll(ESPERA_MS, TimeUnit.MILLISECONDS);
        assertThat(frameSana).as("la sesión sana SIGUE recibiendo").isNotNull();
        JsonNode rootSana = objectMapper.readTree(frameSana);
        assertThat(rootSana.get("type").asText()).isEqualTo("batch");
        assertThat(rootSana.get("project_id").asLong()).isEqualTo(901L);
        assertThat(rootSana.get("received_at").asText()).isEqualTo(
                t2.format(LoginController.EXPIRES_AT_FORMATTER));

        // (b) desborda bufferSizeLimit → idem 1013. Con send-time 1 ms la
        //     barrera de TIEMPO dispara SIEMPRE antes por construcción
        //     (checkSessionLimits evalúa el tiempo primero), así que la
        //     barrera de buffer se PINNEA con un decorador REAL de la MISMA
        //     clase con el send-time generosificado y el buffer límite del
        //     CONTEXTO (1 byte):
        CountDownLatch drenaB = new CountDownLatch(1);
        WebSocketSession lentaB = mockSesion("e13-lenta-b", drenaB);
        ConcurrentWebSocketSessionDecorator envueltaB =
                new ConcurrentWebSocketSessionDecorator(lentaB,
                        /* send-time generosificado (aisla la de buffer) */
                        30_000,
                        /* buffer límite del CONTEXTO: 1 byte */
                        1);
        Thread emisor = new Thread(() -> {
            try {
                envueltaB.sendMessage(new TextMessage("primero-in-flight"));
            } catch (IOException ex) {
                // no debería suceder: el bloqueo es del mock
            }
        }, "e13-b-envio");
        emisor.start();
        esperarInFlight(envueltaB);
        assertThat(envueltaB.getSendTimeLimit()).isEqualTo(30_000);
        // 2º mensaje (hilo llamador, como el broadcaster): tryLock falla →
        // tiempo NO vencido (< 30 s) → BUFFER (1 byte) desbordado →
        // SessionLimitExceededException por BUFFER:
        assertThatThrownBy(() -> envueltaB
                .sendMessage(new TextMessage("desborde-del-buffer")))
                .as("SessionLimitExceededException por bufferSize")
                .isInstanceOf(SessionLimitExceededException.class);
        // mismo camino del broadcaster (catch por sesión) → 1013 al delegado:
        handler.cerrarPorLentitud(envueltaB);
        Mockito.verify(lentaB, Mockito.timeout(ESPERA_MS)).close(
                Mockito.argThat(estado -> estado != null
                        && estado.getCode() == 1013));
        drenaB.countDown();
        emisor.join(ESPERA_MS);
    }

    // — helpers de stubbing ——

    private void stubPorIds(long projectId, long id, String mensaje) {
        org.mockito.Mockito.when(logsQueryRepository.porIds(anyList()))
                .thenAnswer(inv -> List.of(
                        filaProyecto(id, projectId, mensaje)));
    }

    private int calificadasCon(long projectId) {
        int vistas = 0;
        for (ConcurrentWebSocketSessionDecorator s : handler.sesionesVivas()) {
            Filtros f;
            synchronized (s.getAttributes()) {
                f = (Filtros) s.getAttributes()
                        .get(LogSocketHandler.ATTR_FILTROS);
            }
            if (f != null && Long.valueOf(projectId).equals(f.projectId())) {
                vistas++;
            }
        }
        return vistas;
    }
}