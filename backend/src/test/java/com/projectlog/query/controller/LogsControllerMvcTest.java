package com.projectlog.query.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectlog.auth.JwtAuthFilter;
import com.projectlog.auth.JwtPrincipal;
import com.projectlog.auth.LoginController;
import com.projectlog.auth.TokenService;
import com.projectlog.config.CorsConfig;
import com.projectlog.config.RestAuthenticationEntryPoint;
import com.projectlog.config.SecurityConfig;
import com.projectlog.query.CursorCodec;
import com.projectlog.query.repository.LogsQueryRepository;
import com.projectlog.query.repository.LogsQueryRepository.FilaConsulta;
import com.projectlog.query.dto.LogDto;
import com.projectlog.query.dto.LogListItemDto;

/**
 * E6.1/E6.3–E6.10/E6.14/E6.15 [L] (R6.25): {@code @WebMvcTest} sobre
 * {@code LogsController} con el {@code LogsQueryRepository} mockeado —
 * réplica EXACTA del fixture de {@code ProjectsControllerMvcTest}/
 * {@code PanelAuthMvcTest} (C2/C3): cadena 2 importada
 * ({@code SecurityConfig + JwtAuthFilter + RestAuthenticationEntryPoint +
 * CorsConfig}), JWT real construido con un {@code TokenService} local de
 * test y {@code @ActiveProfiles("test")} sobre el
 * {@code application-test.yml} dummy (INTOCABLE). SIN contenedor ni BD.
 *
 * <p>NINGUNA línea de seguridad tocada (R6.3/D-6.7): E6.15 pina que los 2
 * mappings NACEN detrás del 401 {@code invalid_token} (jamás
 * {@code api_key_invalid}) y con JWT válido llegan al controller. La
 * aserción del next_cursor (E6.1) es DECODIFICACIÓN DE VUELTA (D-S6-b/
 * D-6.11): el "ArgumentCaptor del codec" del spec no es capturable tal cual
 * (codec static puro) — se decodifica el token de la respuesta contra el par
 * (r, i) de la fila limit-1 del mock; NI mockStatic NI reflection.</p>
 */
@ActiveProfiles("test")
@WebMvcTest(LogsController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, CorsConfig.class})
class LogsControllerMvcTest {

    /** ≥32 bytes UTF-8, solo para CONSTRUIR el JWT de prueba (string). */
    private static final String SECRETO_TEST =
            "secreto-de-test-solo-para-la-suite-c6-abcd1234abcd1234";

    private static final String URL_LISTA = "/api/v1/panel/logs";

    /** Formatter C2 solo para CONSTRUIR los mocks (referencia, jamás editado). */
    private static final DateTimeFormatter FORMATTER =
            LoginController.EXPIRES_AT_FORMATTER;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LogsQueryRepository logsQueryRepository;

    @MockitoBean
    private TokenService tokenService;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** JWT real (string) firmado con un TokenService local de test. */
    private static String jwtDePrueba() {
        return new TokenService(SECRETO_TEST, 8).issue("admin", "ADMIN").token();
    }

    /** Stub del mock para el filtro + credencial Bearer de la petición. */
    private String bearer() {
        when(tokenService.verify(anyString())).thenReturn(Optional.of(
                new JwtPrincipal("admin", "ADMIN", Instant.now().plusSeconds(3600))));
        return "Bearer " + jwtDePrueba();
    }

    /** Fila moneda del mock: item (fechas formateadas C2) + RAW + id. */
    private static FilaConsulta fila(long id, OffsetDateTime raw) {
        LogListItemDto item = new LogListItemDto(id, 5L, "ERROR", "production",
                raw.format(FORMATTER), raw.format(FORMATTER), "m-" + id,
                "payments", "corr-" + id);
        return new FilaConsulta(item, raw, id);
    }

    /** Stub del listado SIN filtros (el 9.º arg es el limit). */
    private void stubListado(List<FilaConsulta> filas, int limit) {
        when(logsQueryRepository.listar(any(), any(), any(), any(), any(),
                any(), any(), any(), Mockito.eq(limit))).thenReturn(filas);
    }

    private static List<String> clavesDe(JsonNode nodo) {
        List<String> claves = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(claves::add);
        return claves;
    }

    /** Token de prueba ARBITRARIO (para los inválidos del E6.9 — decode). */
    private static String tokenDe(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes());
    }

    // ------------------------------------------------------------------
    // E6.15 — seguridad: los 2 mappings NACEN tras el 401 (cadena 2 intocable)
    // ------------------------------------------------------------------

    @Test
    void ambosEndpointsNacenTras401InvalidToken() throws Exception {
        // (a) sin JWT: listado y detalle → 401 invalid_token (entry point del
        // PANEL — jamás api_key_invalid)
        mockMvc.perform(get(URL_LISTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"));
        mockMvc.perform(get(URL_LISTA + "/7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"));
        // (b) con X-API-Key Y sin JWT: la API key NO autentica el panel (la
        // cadena 2 la ignora) → el MISMO 401 invalid_token
        mockMvc.perform(get(URL_LISTA).header("X-API-Key", "plk_basura_de_test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"));
        mockMvc.perform(get(URL_LISTA + "/7").header("X-API-Key", "plk_basura_de_test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"));
        Mockito.verifyNoInteractions(logsQueryRepository);
    }

    @Test
    void conJwtValidoLlegaAlControllerFirmaDelFlujoDel200() throws Exception {
        stubListado(List.of(), 50);
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer()))
                .andExpect(status().isOk());
        Mockito.verify(logsQueryRepository).listar(any(), any(), any(), any(),
                any(), any(), any(), any(), Mockito.eq(50));
    }

    // ------------------------------------------------------------------
    // E6.1 — shape exacto {items, next_cursor} + decode-back (D-S6-b)
    // ------------------------------------------------------------------

    @Test
    void listadoShapeExactoItemsYNextCursor() throws Exception {
        OffsetDateTime base = OffsetDateTime.parse("2026-10-16T22:31:04Z");
        List<FilaConsulta> filas = new ArrayList<>();
        for (long id = 1; id <= 51; id++) {
            filas.add(fila(id, base.plusSeconds(id)));
        }
        stubListado(filas, 50);

        var resultado = mockMvc.perform(
                        get(URL_LISTA).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode nodo = objectMapper.readTree(
                resultado.getResponse().getContentAsString());
        assertThat(nodo.has("items")).isTrue();
        assertThat(nodo.get("items").size()).isEqualTo(50); // trim exacto
        // claves del item EXACTAS (9 literales — R6.9)
        List<String> claves = clavesDe(nodo.get("items").get(0));
        assertThat(claves).containsExactly("id", "project_id", "level",
                "environment", "timestamp", "received_at", "message", "source",
                "correlation_id");
        assertThat(nodo.get("items").get(0).get("received_at").asText())
                .isEqualTo("2026-10-16T22:31:05Z"); // render truncado a segundos
        // D-S6-b: DECODIFICACIÓN DE VUELTA del token emitido (sin mockStatic,
        // sin ArgumentCaptor del codec, sin reflection)
        String next = nodo.get("next_cursor").asText();
        assertThat(next).isNotEmpty();
        CursorCodec.Cursor cursor = CursorCodec.decodificar(next);
        FilaConsulta ultima = filas.get(49); // la ÚLTIMA ENTREGADA (no la 51.ª)
        assertThat(cursor.r()).isEqualTo(ultima.received_at().toInstant());
        assertThat(cursor.i()).isEqualTo(ultima.id());
    }

    @Test
    void listadoSinFilaExtraDejaNextCursorNullExplicito() throws Exception {
        OffsetDateTime base = OffsetDateTime.parse("2026-10-16T22:31:04Z");
        List<FilaConsulta> filas = new ArrayList<>();
        for (long id = 1; id <= 50; id++) {
            filas.add(fila(id, base.plusSeconds(id)));
        }
        stubListado(filas, 50);

        var resultado = mockMvc.perform(
                        get(URL_LISTA).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn();
        String cuerpo = resultado.getResponse().getContentAsString();
        // la CLAVE aparece EXPLÍCITA con null (R6.11 — jamás omitida)
        assertThat(cuerpo).contains("\"next_cursor\":null");
        JsonNode nodo = objectMapper.readTree(cuerpo);
        assertThat(nodo.get("next_cursor").isNull()).isTrue();
        assertThat(nodo.get("items").size()).isEqualTo(50);
    }

    @Test
    void listadoVacioSinCursorEs200EstadoLegitimo() throws Exception {
        stubListado(List.of(), 50);
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ------------------------------------------------------------------
    // E6.14 — limit defecto 50 y 200 legales AL REPO (ArgumentCaptor del bean)
    // ------------------------------------------------------------------

    @Test
    void limitDefecto50YLegal200AlRepo() throws Exception {
        bearer();
        stubListado(List.of(), 50);
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer()))
                .andExpect(status().isOk());
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(logsQueryRepository).listar(any(), any(), any(), any(),
                any(), any(), any(), any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(50); // defecto §3.3 AUSENTE
    }

    @Test
    void limit200LegalAlRepoYSinLimitElDefectoManda() throws Exception {
        bearer();
        stubListado(List.of(), 200);
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer())
                        .param("limit", "200"))
                .andExpect(status().isOk());
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(logsQueryRepository).listar(any(), any(), any(), any(),
                any(), any(), any(), any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // E6.3 / E6.4 — detalle: 200 con los 11 campos / 404 not_found
    // ------------------------------------------------------------------

    @Test
    void detalleResponde200ConLos11CamposYMetadataNodoExacto() throws Exception {
        String bearer = bearer();
        JsonNode meta = objectMapper.readTree(
                "{\"span\":\"checkout\",\"attempt\":2}");
        assertThat(meta).isNotNull();
        LogDto dto = new LogDto(42L, 5L, "ERROR", "production",
                "2026-10-16T22:31:04Z", "2026-10-16T22:31:04Z",
                "boom en el paso 3", "java.lang.NullPointerException: boom",
                meta, "payments", "corr-42");
        when(logsQueryRepository.porId(42L)).thenReturn(Optional.of(dto));

        var resultado = mockMvc.perform(
                        get(URL_LISTA + "/42").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode nodo = objectMapper.readTree(
                resultado.getResponse().getContentAsString());
        assertThat(clavesDe(nodo)).containsExactly("id", "project_id", "level",
                "environment", "timestamp", "received_at", "message",
                "exception", "metadata", "source", "correlation_id");
        assertThat(nodo.get("exception").asText())
                .isEqualTo("java.lang.NullPointerException: boom"); // tal cual
        assertThat(nodo.get("metadata").get("span").asText()).isEqualTo("checkout");
        assertThat(nodo.get("metadata").get("attempt").asInt()).isEqualTo(2);
        assertThat(nodo.get("metadata").isObject()).isTrue(); // objeto EXACTO

        // exception/metadata NULOS → los 2 campos con null EXPLÍCITO
        when(logsQueryRepository.porId(43L)).thenReturn(Optional.of(new LogDto(
                43L, 5L, "INFO", "development", "2026-10-16T22:31:05Z",
                "2026-10-16T22:31:05Z", "sin exception", null, null,
                null, null)));
        var nulo = mockMvc.perform(
                        get(URL_LISTA + "/43").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        String cuerpo = nulo.getResponse().getContentAsString();
        assertThat(cuerpo).contains("\"exception\":null");
        assertThat(cuerpo).contains("\"metadata\":null");
    }

    @Test
    void detalleIdInexistenteResponde404NotFound() throws Exception {
        String bearer = bearer();
        when(logsQueryRepository.porId(999999L)).thenReturn(Optional.empty());

        mockMvc.perform(get(URL_LISTA + "/999999")
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message")
                        .value("No existe (quizás fue eliminado)"))
                .andExpect(jsonPath("$.field").doesNotExist()); // SIN field
    }

    // ------------------------------------------------------------------
    // E6.5 — environment/level fuera de enum (o vacío) → filter_invalid_value
    // ------------------------------------------------------------------

    @Test
    void environmentYLevelFueraDeEnumResponde400FilterInvalidValue()
            throws Exception {
        String bearer = bearer();
        // environment fuera de enum (case-sensitive, SIN normalizar)
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("environment", "qa"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                .andExpect(jsonPath("$.field").value("environment"))
                .andExpect(jsonPath("$.message").value(
                        "Valor de environment no válido; use development, staging o production"));
        Mockito.verifyNoInteractions(logsQueryRepository);

        // level minúscula: SIN normalización (patrón C3)
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("level", "error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                .andExpect(jsonPath("$.field").value("level"))
                .andExpect(jsonPath("$.message").value(
                        "Valor de level no válido; use TRACE, DEBUG, INFO, WARN, ERROR o FATAL"));
        Mockito.verifyNoInteractions(logsQueryRepository);

        // presente-y-vacío → TAMBIÉN inválido (patrón C3)
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("environment", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                .andExpect(jsonPath("$.field").value("environment"));
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("level", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                .andExpect(jsonPath("$.field").value("level"));
        Mockito.verifyNoInteractions(logsQueryRepository);
    }

    // ------------------------------------------------------------------
    // E6.6 — from > to → filter_from_to_inverted (SIN field); from == to LEGAL
    // ------------------------------------------------------------------

    @Test
    void fromMayorToResponde400FromToInverted() throws Exception {
        String bearer = bearer();
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("from", "2026-01-02T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_from_to_inverted"))
                .andExpect(jsonPath("$.message").value(
                        "El rango temporal no es válido: from es posterior a to"))
                .andExpect(jsonPath("$.field").doesNotExist());
        Mockito.verifyNoInteractions(logsQueryRepository);
    }

    @Test
    void fronteraFromIgualToEsLegalYLlegaAlRepo() throws Exception {
        String bearer = bearer();
        stubListado(List.of(), 50);
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isOk());
        Mockito.verify(logsQueryRepository).listar(any(), any(), any(),
                Mockito.any(OffsetDateTime.class),
                Mockito.any(OffsetDateTime.class), any(), any(), any(),
                Mockito.eq(50)); // rango de punto: NO 400
    }

    // ------------------------------------------------------------------
    // E6.7 — limit fuera de rango o no-numérico → el MISMO cuerpo
    // ------------------------------------------------------------------

    @Test
    void limitFueraDeRangoONaNResponde400FilterLimitInvalid() throws Exception {
        String bearer = bearer();
        for (String invalido : new String[] {"0", "201", "abc", "-5", ""}) {
            mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                            .param("limit", invalido))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("filter_limit_invalid"))
                    .andExpect(jsonPath("$.field").value("limit"))
                    .andExpect(jsonPath("$.message").value(
                            "El filtro limit debe ser un número entre 1 y 200"));
            Mockito.verifyNoInteractions(logsQueryRepository);
        }
        // fronteras LEGALES: 1 y 200 (stubbed arriba / verificación al repo)
        stubListado(List.of(), 1);
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("limit", "1"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // E6.8 — project_id/from/to no-parseables → filter_invalid_value + field
    // ------------------------------------------------------------------

    @Test
    void projectIdFromToNoParseablesResponde400FilterInvalidValue()
            throws Exception {
        String bearer = bearer();
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("project_id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                .andExpect(jsonPath("$.field").value("project_id"))
                .andExpect(jsonPath("$.message").value(
                        "El filtro project_id debe ser un número"));
        Mockito.verifyNoInteractions(logsQueryRepository);

        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("from", "no-fecha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                .andExpect(jsonPath("$.field").value("from"))
                .andExpect(jsonPath("$.message").value(
                        "El filtro from debe ser una fecha ISO-8601 con zona horaria"));
        Mockito.verifyNoInteractions(logsQueryRepository);

        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("to", "no-fecha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                .andExpect(jsonPath("$.field").value("to"))
                .andExpect(jsonPath("$.message").value(
                        "El filtro to debe ser una fecha ISO-8601 con zona horaria"));
        Mockito.verifyNoInteractions(logsQueryRepository);

        // naive SIN offset → el MISMO 400 de from (patrón S5-5 de C5)
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("from", "2026-10-16T22:31:04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                .andExpect(jsonPath("$.field").value("from"));
        Mockito.verifyNoInteractions(logsQueryRepository);
    }

    // ------------------------------------------------------------------
    // E6.9 — cursor ilegible (7 variantes, MISMO cuerpo — anti-oráculo P1)
    // ------------------------------------------------------------------

    @Test
    void cursorIlegibleResponde400FilterCursorInvalid() throws Exception {
        String bearer = bearer();
        List<String> tokens = List.of(
                "ñ%20%2B",                       // (a) con ñ y % — no Base64url
                "a+b/cd",                        // (a) alfabeto clásico + / / —
                "eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0=",
                tokenDe("hello"),                // (c) decodifica, NO es JSON
                tokenDe("{\"i\":1}"),            // (d) sin r
                tokenDe("{\"r\":\"2026-10-16T22:31:04Z\"}"), // (d) sin i
                tokenDe("{\"r\":\"no-es-instant\",\"i\":1}"),   // (e)
                tokenDe("{\"r\":\"2026-10-16T22:31:04+02:00\",\"i\":1}"), // (e)
                tokenDe("{\"r\":\"2026-10-16T22:31:04Z\",\"i\":1,\"x\":true}"), // (f)
                tokenDe("{\"r\":\"2026-10-16T22:31:04Z\",\"i\":1.5}"),          // (g)
                tokenDe("{\"r\":\"2026-10-16T22:31:04Z\",\"i\":-1}"));          // (g)
        for (String token : tokens) {
            mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                            .param("cursor", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("filter_cursor_invalid"))
                    .andExpect(jsonPath("$.field").value("cursor"))
                    .andExpect(jsonPath("$.message")
                            .value("El parámetro cursor no es válido"));
            Mockito.verifyNoInteractions(logsQueryRepository);
        }
        // cursor ausente es legal (va directo al flujo del 200 — stubbed)
        stubListado(List.of(), 50);
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer))
                .andExpect(status().isOk());
    }

    /**
     * E6.9 transversal 2.A-4 (QP-6.9, hallazgo H1 del test-side): el
     * {@code r} decodificado NUNCA se valida contra la BD — un instante
     * FUTURO es keyset LEGÍTIMO: 200 con el cuerpo EXACTO
     * {@code {"items":[],"next_cursor":null}} (página vacía + fin de
     * resultados con la clave EXPLÍCITA, R6.11/R6.12) — JAMÁS el 400
     * {@code filter_cursor_invalid} (S6-3: "cursor con r futuro → 200
     * página vacía, sin case especial").
     */
    @Test
    void cursorConRFuturoResponde200ListaVaciaYNextCursorNullExplicito()
            throws Exception {
        String bearer = bearer();
        stubListado(List.of(), 50); // límite por defecto (limit AUSENTE)
        String futuro = tokenDe("{\"r\":\"2030-01-01T00:00:00Z\",\"i\":1}");
        var resultado = mockMvc.perform(
                        get(URL_LISTA).header("Authorization", bearer)
                                .param("cursor", futuro))
                .andExpect(status().isOk()) // keyset legítimo: SIN 400
                .andReturn();
        // aserto [M] del cuerpo COMPLETO verbatim (QP-6.9)
        assertThat(resultado.getResponse().getContentAsString())
                .isEqualTo("{\"items\":[],\"next_cursor\":null}");
    }

    // ------------------------------------------------------------------
    // E6.10 — orden fail-fast de los 8 checks + verifyNoInteractions
    // ------------------------------------------------------------------

    @Test
    void ordenFailFastDe8ChecksEsDeterminista() throws Exception {
        String bearer = bearer();
        // (1) TODO mal: SIEMPRE el check 1 (filter_limit_invalid)
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("limit", "abc").param("project_id", "abc")
                        .param("level", "error").param("environment", "qa")
                        .param("from", "2026-10-16T22:31:04")
                        .param("to", "no-fecha")
                        .param("cursor", "basura"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_limit_invalid"))
                .andExpect(jsonPath("$.field").value("limit"));
        Mockito.verifyNoInteractions(logsQueryRepository);

        // (2) limit OK + 2 fallos (project_id + level) → SIEMPRE project_id
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("limit", "50").param("project_id", "abc")
                        .param("level", "error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                .andExpect(jsonPath("$.field").value("project_id"));
        Mockito.verifyNoInteractions(logsQueryRepository);

        // (3) solo from naive → campo from
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("from", "2026-10-16T22:31:04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("from"));
        Mockito.verifyNoInteractions(logsQueryRepository);

        // (4) from/to PARSEADOS invertidos + cursor basura → SIEMPRE el
        //     check 7 (filter_from_to_inverted) ANTES del 8 (cursor)
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("from", "2026-01-02T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z")
                        .param("cursor", "basura"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_from_to_inverted"));
        Mockito.verifyNoInteractions(logsQueryRepository);

        // (5) to no-fecha + restantes parámetros mal (cursor basura) →
        //     SIEMPRE `to`: el check 6 GANA (primer-fallo-gana), jamás el
        //     7 (from>to) ni el 8 (cursor) — QP-6.10 del plan (hallazgo H2
        //     del test-side; desde/to quedan a null ante un `to`
        //     imparsable, así que un reorden 7-vs-6 ya no puede robar el
        //     ganador a `to`)
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer)
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "no-fecha")
                        .param("cursor", "basura"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                .andExpect(jsonPath("$.field").value("to"))
                .andExpect(jsonPath("$.message").value(
                        "El filtro to debe ser una fecha ISO-8601 con zona horaria"));
        Mockito.verifyNoInteractions(logsQueryRepository);
    }
}