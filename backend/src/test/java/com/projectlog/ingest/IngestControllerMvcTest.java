package com.projectlog.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectlog.auth.JwtAuthFilter;
import com.projectlog.auth.TokenService;
import com.projectlog.config.CorsConfig;
import com.projectlog.config.RestAuthenticationEntryPoint;
import com.projectlog.config.SecurityConfig;
import com.projectlog.keys.ApiKeyService;
import com.projectlog.projects.dto.ProjectDto;
import com.projectlog.projects.repository.ProjectsRepository;

/**
 * E5.1–E5.14, E5.12, E5.13, E5.16, E5.24-c [L] (design §13, tasks T5.14):
 * réplica del patrón de {@code PanelAuthMvcTest}/{@code ApiKeyControllerMvcTest}
 * — {@code @WebMvcTest} sobre {@link IngestController} con la cadena 1
 * importada EXACTA ({@code SecurityConfig + IngestGuardFilter +
 * IngestAuthenticationEntryPoint}) + {@code @ActiveProfiles("test")}
 * (application-test.yml INTOCABLE) + el {@code IngestService} REAL con el
 * {@code LogsRepository} mock (design §13: así la validación corre de verdad).
 *
 * <p>Desviación documentada del literal del design §13 (omisión del
 * armado): la inyección del constructor de {@code SecurityConfig} exige
 * además {@code JwtAuthFilter + RestAuthenticationEntryPoint + CorsConfig}
 * y un {@code @MockitoBean TokenService} (idéntico al armado C4). El
 * principal lo armará el GUARD REAL: los mocks stubbean
 * {@code ApiKeyService.sha256} → hash fijo y
 * {@code ProjectsRepository.findByApiKeyHash} → {@code ProjectDto}, el
 * flujo 401/403/400/202 es el de la cadena completa.</p>
 */
@ActiveProfiles("test")
@WebMvcTest(IngestController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, CorsConfig.class,
        IngestGuardFilter.class, IngestAuthenticationEntryPoint.class,
        IngestService.class})
class IngestControllerMvcTest {

    /** ≥32 bytes, SOLO para construir el JWT de prueba (string). */
    private static final String SECRETO_TEST =
            "secreto-de-test-solo-para-la-suite-c5-abcd1234abcd1234";

    private static final String URL_INDIVIDUAL = "/api/v1/ingest";
    private static final String URL_BATCH = "/api/v1/ingest/batch";

    /** Key de test, forma plk_ (JAMÁS se loguea — R5.15). */
    private static final String KEY_FIXTURE =
            "plk_ingestaC5DeTestDe47CaracteresExactamenteAqui00";

    /** Digest del mock (escritura ciega — sin SHA real en este slice). */
    private static final byte[] HASH_FIXTURE = {1, 2, 3};

    private static final String MSG_ENV =
            "El campo environment es obligatorio (development, staging o"
                    + " production)";
    private static final String MSG_TS =
            "El campo timestamp debe ser ISO-8601 con zona horaria";

    /** Registro VÁLIDO individual (los 4 campos requeridos). */
    private static final String VALIDO =
            "{\"level\":\"INFO\",\"timestamp\":\"2026-10-16T22:31:04Z\""
                    + ",\"message\":\"hola\",\"environment\":\"development\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectsRepository projectsRepository;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private LogsRepository logsRepository;

    @MockitoBean
    private TokenService tokenService;

    // ------------------------------------------------------------------
    // Armado + helpers (patrón C4)
    // ------------------------------------------------------------------

    /** Stub del flujo de autenticación del guard para el status indicado. */
    private void autorizar(String status) {
        when(apiKeyService.sha256(Mockito.anyString())).thenReturn(HASH_FIXTURE);
        when(projectsRepository.findByApiKeyHash(HASH_FIXTURE))
                .thenReturn(Optional.of(proyecto(7L, status)));
    }

    /** Sin match: el 401 sale del entry point, JAMÁS toca la BD de logs. */
    private void autorizarSinMatch() {
        when(apiKeyService.sha256(Mockito.anyString())).thenReturn(HASH_FIXTURE);
        when(projectsRepository.findByApiKeyHash(HASH_FIXTURE))
                .thenReturn(Optional.empty());
    }

    private static ProjectDto proyecto(Long id, String status) {
        return new ProjectDto(id, "payments-service", "descripción de prueba",
                status, "2026-10-21T10:00:00Z", "2026-10-21T11:30:05Z",
                "plk_abcdefgh", false);
    }

    private static String loteDe(String... registros) {
        return "{\"logs\":[" + String.join(",", registros) + "]}";
    }

    /** Respuesta decodificada como UTF-8 (los bodies llevan español). */
    private static String cuerpo(MvcResult result)
            throws java.io.UnsupportedEncodingException {
        return new String(result.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // 202 — R5.9/R5.10-a: JSON SIN espacios, SOLO la clave accepted
    // ------------------------------------------------------------------

    @Test
    void ingestIndividualResponde202Accepted1Exacto() throws Exception {
        autorizar("ACTIVE");
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(VALIDO))
                .andExpect(status().isAccepted())
                .andExpect(content().string("{\"accepted\":1}"));
        Mockito.verify(logsRepository).insert(Mockito.eq(7L), Mockito.anyList(),
                Mockito.any());
        // QP-12: el digest es SIEMPRE del header COMPLETO (con prefijo,
        // sin recortes) — la clave de test exacta fue la entrada del sha256
        Mockito.verify(apiKeyService).sha256(KEY_FIXTURE);
    }

    @Test
    void ingestBatchResponde202AcceptedNExacto() throws Exception {
        autorizar("ACTIVE");
        mockMvc.perform(post(URL_BATCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(loteDe(VALIDO, VALIDO, VALIDO)))
                .andExpect(status().isAccepted())
                .andExpect(content().string("{\"accepted\":3}"));
    }

    // ------------------------------------------------------------------
    // 400 por campo — S5-3: sin index en individual; los 4 de S5-4 en E5.3
    // ------------------------------------------------------------------

    @Test
    void environmentAusenteOInvalidoResponde400EnvironmentRequired() throws Exception {
        autorizar("ACTIVE");
        String[] cuerpos = {
                "{\"level\":\"INFO\",\"timestamp\":\"2026-10-16T22:31:04Z\""
                        + ",\"message\":\"hola\"}",                    // ausente
                "{\"environment\":null,\"level\":\"INFO\""
                        + ",\"timestamp\":\"2026-10-16T22:31:04Z\""
                        + ",\"message\":\"hola\"}",                    // null
                "{\"environment\":\"\",\"level\":\"INFO\""
                        + ",\"timestamp\":\"2026-10-16T22:31:04Z\""
                        + ",\"message\":\"hola\"}",                    // vacío
                "{\"environment\":\"development \",\"level\":\"INFO\""
                        + ",\"timestamp\":\"2026-10-16T22:31:04Z\""
                        + ",\"message\":\"hola\"}",                     // fuera del enum
                "{\"environment\":5,\"level\":\"INFO\""
                        + ",\"timestamp\":\"2026-10-16T22:31:04Z\""
                        + ",\"message\":\"hola\"}"};                   // coerción→"5"
        for (String cuerpoJson : cuerpos) {
            mockMvc.perform(post(URL_INDIVIDUAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .header("X-API-Key", KEY_FIXTURE)
                            .content(cuerpoJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(IngestService.CODE_ENVIRONMENT_REQUIRED))
                    .andExpect(jsonPath("$.message").value(MSG_ENV))
                    .andExpect(jsonPath("$.field")
                            .value(LogIngestRequest.CAMPO_ENVIRONMENT));
        }
        // sin index en individual (E5.3: el mismo fallo NO lleva index)
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content("{\n}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(
                        "{\"code\":\"environment_required\",\"message\":\""
                                + MSG_ENV + "\",\"field\":\"environment\"}"))
                .andExpect(jsonPath("$.index").doesNotExist());
    }

    @Test
    void timestampNaiveONoParseableResponde400TimestampWithoutTimezone()
            throws Exception {
        autorizar("ACTIVE");
        String[] timestamps = {"2026-10-16T22:31:04", "naive", "2026-10-16"};
        for (String ts : timestamps) {
            mockMvc.perform(post(URL_INDIVIDUAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .header("X-API-Key", KEY_FIXTURE)
                            .content("{\"environment\":\"production\",\"level\":"
                                    + "\"INFO\",\"message\":\"hola\",\"timestamp\":"
                                    + "\"" + ts + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(
                            IngestService.CODE_TIMESTAMP_WITHOUT_TIMEZONE))
                    .andExpect(jsonPath("$.message").value(MSG_TS))
                    .andExpect(jsonPath("$.field")
                            .value(LogIngestRequest.CAMPO_TIMESTAMP));
        }
        // "Z" y offsets parsean (D-11) → 202
        String[] validas = {"2026-10-16T22:31:04Z", "2026-10-16T22:31:04+02:00"};
        for (String ts : validas) {
            mockMvc.perform(post(URL_INDIVIDUAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .header("X-API-Key", KEY_FIXTURE)
                            .content("{\"environment\":\"production\",\"level\":"
                                    + "\"INFO\",\"message\":\"hola\",\"timestamp\":"
                                    + "\"" + ts + "\"}"))
                    .andExpect(status().isAccepted());
        }
    }

    @Test
    void levelFueraDeEnumResponde400LevelInvalid() throws Exception {
        autorizar("ACTIVE");
        String[] niveles = {"error", "TRACE ", "info", "WARNING"};
        for (String nivelJson : niveles) {
            mockMvc.perform(post(URL_INDIVIDUAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .header("X-API-Key", KEY_FIXTURE)
                            .content("{\"environment\":\"development\",\"level\":"
                                    + "\"" + nivelJson + "\",\"timestamp\":"
                                    + "\"2026-10-16T22:31:04Z\",\"message\":"
                                    + "\"hola\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(IngestService.CODE_LEVEL_INVALID))
                    .andExpect(jsonPath("$.field")
                            .value(LogIngestRequest.CAMPO_LEVEL));
        }
        for (String cuerpoJson : validosConLevelNulo()) {
            mockMvc.perform(post(URL_INDIVIDUAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .header("X-API-Key", KEY_FIXTURE)
                            .content(cuerpoJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(IngestService.CODE_LEVEL_INVALID));
        }
    }

    /** level ausente y null (misma pareja code/field). */
    private static String[] validosConLevelNulo() {
        return new String[] {
                "{\"environment\":\"development\",\"timestamp\":"
                        + "\"2026-10-16T22:31:04Z\",\"message\":\"hola\"}",
                "{\"environment\":\"development\",\"level\":null,"
                        + "\"timestamp\":\"2026-10-16T22:31:04Z\""
                        + ",\"message\":\"hola\"}"};
    }

    @Test
    void messageVacioResponde400MessageRequired() throws Exception {
        autorizar("ACTIVE");
        String[] mensajes = {null, "", "   "};
        for (String mensaje : mensajes) {
            String campo = mensaje == null ? ""
                    : "\"message\":" + quoted(mensaje) + ",";
            mockMvc.perform(post(URL_INDIVIDUAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .header("X-API-Key", KEY_FIXTURE)
                            .content("{\"environment\":\"development\",\"level\":"
                                    + "\"INFO\"," + campo
                                    + "\"timestamp\":\"2026-10-16T22:31:04Z\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(IngestService.CODE_MESSAGE_REQUIRED))
                    .andExpect(jsonPath("$.field")
                            .value(LogIngestRequest.CAMPO_MESSAGE));
        }
    }

    private static String quoted(String valor) {
        return "\"" + valor + "\"";
    }

    // ------------------------------------------------------------------
    // Fronteras de bytes/chars — E5.7/E5.9 (R5.10/S5-8)
    // ------------------------------------------------------------------

    @Test
    void messageBytes32769Responde400FieldTooLongFrontera() throws Exception {
        autorizar("ACTIVE");
        // 32768 bytes con CARÁCTER MULTIBYTE (bytes ≠ chars: 16384 chars) → 202
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content("{\"environment\":\"development\",\"level\":"
                                + "\"INFO\",\"message\":\"" + "ñ".repeat(16384)
                                + "\",\"timestamp\":\"2026-10-16T22:31:04Z\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("{\"accepted\":1}"));
        // 32769 bytes → 400 field_too_long field message
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content("{\"environment\":\"development\",\"level\":"
                                + "\"INFO\",\"message\":\"" + "x".repeat(32769)
                                + "\",\"timestamp\":\"2026-10-16T22:31:04Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value(IngestService.CODE_FIELD_TOO_LONG))
                .andExpect(jsonPath("$.field").value("message"))
                // QP-7: el mensaje compuesto lleva el límite EXACTO
                .andExpect(jsonPath("$.message")
                        .value("El campo message no puede superar 32768"
                                + " bytes"));
    }

    @Test
    void fieldTooLongDeExceptionMetadataSourceYCorrelationId() throws Exception {
        autorizar("ACTIVE");
        // exception 131072 → 202 | 131073 → 400
        String base = "{\"environment\":\"development\",\"level\":\"INFO\","
                + "\"message\":\"hola\",\"timestamp\":\"2026-10-16T22:31:04Z\"";
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(base + ",\"exception\":\""
                                + "x".repeat(131072) + "\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(base + ",\"exception\":\""
                                + "x".repeat(131073) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("exception"))
                .andExpect(jsonPath("$.message")
                        .value("El campo exception no puede superar 131072"
                                + " bytes"));
        // metadata 65536 serializado → 202 | 65537 → 400
        // (serializado {"clave":"m…m"} = 12 + len(value))
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(base + ",\"metadata\":{\"clave\":\""
                                + "m".repeat(65524) + "\"}}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(base + ",\"metadata\":{\"clave\":\""
                                + "m".repeat(65525) + "\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("metadata"))
                .andExpect(jsonPath("$.message")
                        .value("El campo metadata no puede superar 65536"
                                + " bytes"));
        // source 200 chars → 202 | 201 → 400 (CARACTERES, R5.10)
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(base + ",\"source\":\""
                                + "s".repeat(200) + "\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(base + ",\"source\":\""
                                + "s".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("source"))
                .andExpect(jsonPath("$.message")
                        .value("El campo source no puede superar 200"
                                + " caracteres"));
        // correlation_id 200 → 202 | 201 → 400
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(base + ",\"correlation_id\":\""
                                + "c".repeat(200) + "\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(base + ",\"correlation_id\":\""
                                + "c".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("correlation_id"))
                .andExpect(jsonPath("$.message")
                        .value("El campo correlation_id no puede superar 200"
                                + " caracteres"));
    }

    // ------------------------------------------------------------------
    // Batch — E5.10/E5.11 (S5-3/R5.24-c) y metadata_not_object (E5.8)
    // ------------------------------------------------------------------

    @Test
    void metadataArrayOEscalarResponde400MetadataNotObject() throws Exception {
        autorizar("ACTIVE");
        for (String metadataJson : new String[] {"[1,2]", "\"texto\"", "5",
                "true"}) {
            mockMvc.perform(post(URL_INDIVIDUAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .header("X-API-Key", KEY_FIXTURE)
                            .content("{\"environment\":\"development\",\"level\":"
                                    + "\"INFO\",\"message\":\"hola\",\"timestamp\":"
                                    + "\"2026-10-16T22:31:04Z\",\"metadata\":"
                                    + metadataJson + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(IngestService.CODE_METADATA_NOT_OBJECT))
                    .andExpect(jsonPath("$.field")
                            .value(LogIngestRequest.CAMPO_METADATA));
        }
        // {} (objeto vacío) es VÁLIDO → 202
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content("{\"environment\":\"development\",\"level\":"
                                + "\"INFO\",\"message\":\"hola\",\"timestamp\":"
                                + "\"2026-10-16T22:31:04Z\",\"metadata\":{}}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void batchVacioONoArrayY201Responde400FieldLogs() throws Exception {
        autorizar("ACTIVE");
        String[] cuerpos = {"{\"logs\":[]}", "{}", "{\"logs\":\"texto\"}",
                "{\"otro\":1}"};
        for (String cuerpoBatch : cuerpos) {
            mockMvc.perform(post(URL_BATCH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .header("X-API-Key", KEY_FIXTURE)
                            .content(cuerpoBatch))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(IngestController.CODE_BATCH_EMPTY))
                    .andExpect(jsonPath("$.field").value("logs"));
        }
        // 201 registros → batch_too_large (mensaje compuesto con el límite)
        String doscientosUno = "{\"logs\":["
                + String.join(",", Collections.nCopies(201, "{}")) + "]}";
        mockMvc.perform(post(URL_BATCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(doscientosUno))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value(IngestController.CODE_BATCH_TOO_LARGE))
                .andExpect(jsonPath("$.message")
                        .value("El campo logs no puede contener más de 200"
                                + " registros"))
                .andExpect(jsonPath("$.field").value("logs"));
        // 200 (el límite) pasa el gate de lote → falla en validación de campo
        mockMvc.perform(post(URL_BATCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content("{\"logs\":["
                                + String.join(",", Collections.nCopies(200, "{}"))
                                + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value(IngestService.CODE_ENVIRONMENT_REQUIRED))
                .andExpect(jsonPath("$.index").value(0));
        Mockito.verify(logsRepository, Mockito.never()).insert(Mockito.anyLong(),
                Mockito.anyList(), Mockito.any());
    }

    @Test
    void batchRegistroIntermedioLlevaIndexYFieldPrimerFalloGana() throws Exception {
        autorizar("ACTIVE");
        String nivelMalo = VALIDO.replace("\"level\":\"INFO\"", "\"level\":\"error\"");
        String mensajeBlancos = VALIDO.replace("\"message\":\"hola\"",
                "\"message\":\"   \"");
        String lote = loteDe(VALIDO, VALIDO, nivelMalo, VALIDO, mensajeBlancos);
        mockMvc.perform(post(URL_BATCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(lote))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(IngestService.CODE_LEVEL_INVALID))
                .andExpect(jsonPath("$.field").value("level"))
                .andExpect(jsonPath("$.index").value(2));
        // el mismo fallo en INDIVIDUAL nunca lleva index (S5-3)
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(nivelMalo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(IngestService.CODE_LEVEL_INVALID))
                .andExpect(jsonPath("$.index").doesNotExist());
        Mockito.verify(logsRepository, Mockito.never()).insert(Mockito.anyLong(),
                Mockito.anyList(), Mockito.any());
    }

    @Test
    void ordenFailFastDeCamposEsDeterminista() throws Exception {
        autorizar("ACTIVE");
        // TODOS los campos mal → SIEMPRE environment (check 1, R5.24)
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content("{\n}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value(IngestService.CODE_ENVIRONMENT_REQUIRED));
        // timestamp + level mal → timestamp (check 2 antes de level, check 3)
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content("{\"environment\":\"development\",\"level\":"
                                + "\"error\",\"timestamp\":\"naive\",\"message\":"
                                + "\"hola\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value(IngestService.CODE_TIMESTAMP_WITHOUT_TIMEZONE));
    }

    // ------------------------------------------------------------------
    // Ilegibilidad — E5.24-c (S5-1/D-10): nunca 415, el 400 por URI
    // ------------------------------------------------------------------

    @Test
    void cuerpoNoJsonYContentTypeAjenoResponde400PorUri() throws Exception {
        autorizar("ACTIVE");
        // cuerpo no-JSON individual → environment_required
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content("no-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value(IngestService.CODE_ENVIRONMENT_REQUIRED))
                .andExpect(jsonPath("$.field")
                        .value(LogIngestRequest.CAMPO_ENVIRONMENT));
        // cuerpo no-JSON batch → batch_empty
        mockMvc.perform(post(URL_BATCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content("no-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value(IngestController.CODE_BATCH_EMPTY));
        // Content-Type text/plain → el MISMO 400 (nunca 415, D-10)
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.TEXT_PLAIN)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content("texto sin json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value(IngestService.CODE_ENVIRONMENT_REQUIRED));
    }

    // ------------------------------------------------------------------
    // 401 — E5.12/E5.16 (un ÚNICO cuerpo, anti-oráculo; jamás invalid_token)
    // ------------------------------------------------------------------

    @Test
    void sinHeaderMalformadaONoMatchResponde401ApiKeyInvalid() throws Exception {
        // (a) SIN header — es el cuerpo de referencia del 401
        MvcResult primera = mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(VALIDO))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode nodo = new ObjectMapper().readTree(cuerpo(primera));
        assertThat(nodo.size()).isEqualTo(2); // SOLO code+message, sin field
        assertThat(nodo.get("code").asText())
                .isEqualTo(IngestAuthenticationEntryPoint.CODE_API_KEY_INVALID);
        assertThat(nodo.get("message").asText())
                .isEqualTo(IngestAuthenticationEntryPoint.MSG_API_KEY_INVALID);
        // (b) header basura no-plk y (c) key de forma válida SIN match —
        // el MISMO cuerpo byte-a-byte (anti-oráculo: sin varianza por causa)
        autorizarSinMatch();
        MvcResult conBasura = mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", "basura-sin-formato-c5")
                        .content(VALIDO))
                .andExpect(status().isUnauthorized()).andReturn();
        MvcResult sinMatch = mockMvc.perform(post(URL_BATCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(loteDe(VALIDO)))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(cuerpo(conBasura)).isEqualTo(cuerpo(primera));
        assertThat(cuerpo(sinMatch)).isEqualTo(cuerpo(primera));
        // el 401 JAMÁS toca la BD de logs (service/repo inalcanzables)
        Mockito.verifyNoInteractions(logsRepository);
    }

    // ------------------------------------------------------------------
    // 403 — E5.13: gate 3 ANTES de validar campos (R5.8)
    // ------------------------------------------------------------------

    @Test
    void proyectoArchivadoResponde403ProjectArchivedAntesDeValidar() throws Exception {
        autorizar("ARCHIVED");
        // con cuerpo VÁLIDO y con cuerpo INVÁLIDO: ambos 403 (gate 3 primero)
        for (String cuerpoJson : new String[] {VALIDO, "{\n}"}) {
            MvcResult resultado = mockMvc.perform(post(URL_INDIVIDUAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .header("X-API-Key", KEY_FIXTURE)
                            .content(cuerpoJson))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code")
                            .value(IngestController.CODE_PROJECT_ARCHIVED))
                    .andExpect(jsonPath("$.field").doesNotExist())
                    .andReturn();
            // cuerpo EXACTO (el 403 es el ÚNICO literal con acento: se lee
            // como UTF-8 desde los bytes, no por getContentAsString())
            assertThat(cuerpo(resultado)).isEqualTo(
                    "{\"code\":\"project_archived\",\"message\":\"El proyecto"
                            + " está archivado: no acepta logs\"}");
        }
        Mockito.verify(logsRepository, Mockito.never()).insert(Mockito.anyLong(),
                Mockito.anyList(), Mockito.any());
    }

    // ------------------------------------------------------------------
    // E5.16: un JWT jamás autentica la ingesta (Schema §5 verbatim)
    // ------------------------------------------------------------------

    @Test
    void jwtNuncaAutenticaIngestaYCadena1SinInvalidToken() throws Exception {
        String jwt = new TokenService(SECRETO_TEST, 8)
                .issue("admin", "ADMIN").token();
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("Authorization", "Bearer " + jwt)
                        .content(VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value(IngestAuthenticationEntryPoint.CODE_API_KEY_INVALID))
                .andExpect(jsonPath("$.field").doesNotExist());
        // la superficie de ingesta JAMÁS emite el literal del panel (CD-4)
        assertThat(cuerpo(mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("Authorization", "Bearer " + jwt)
                        .content(VALIDO))
                .andReturn())).doesNotContain("invalid_token");
        Mockito.verifyNoInteractions(logsRepository);
    }
}