package com.projectlog.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.projectlog.auth.JwtAuthFilter;
import com.projectlog.auth.TokenService;
import com.projectlog.config.CorsConfig;
import com.projectlog.config.RestAuthenticationEntryPoint;
import com.projectlog.config.SecurityConfig;
import com.projectlog.keys.ApiKeyService;
import com.projectlog.projects.dto.ProjectDto;
import com.projectlog.projects.repository.ProjectsRepository;

/**
 * E5.17 [L] (tasks T5.17, R5.15): higiene de logs de C5 — patrón
 * {@code LogHygieneTest} de C2: un {@code ListAppender} de logback acoplado
 * al logger raíz {@code com.projectlog} a DEBUG mientras se ejecutan un
 * ingest 202 (con key real de test) y un 401: NINGÚN evento emitido
 * (incl. los mensajes de las excepciones) contiene la key, un fragmento
 * suyo, el header {@code X-API-Key} ni el hash. Las clases de C5 NO
 * guardan eventos (R5.15: sin loggers) — la higiene NO depende de que
 * hubiera emitidos (corrección del QA de C2, aplicada).
 *
 * <p>Mismo armado que {@link IngestControllerMvcTest} (cadena 1 completa +
 * service real + repos mock).</p>
 */
@WebMvcTest(IngestController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, CorsConfig.class,
        IngestGuardFilter.class, IngestAuthenticationEntryPoint.class,
        IngestService.class})
class IngestLogHygieneTest {

    /** Key de test con fragmento distintivo (JAMÁS en logs — R5.15). */
    private static final String KEY_FIXTURE =
            "plk_ingestaC5HigieneDeTestClaveDe47CharsFijaAquiX0";

    private static final byte[] HASH_FIXTURE = {1, 2, 3};

    private static final String URL_INDIVIDUAL = "/api/v1/ingest";

    /** Registro VÁLIDO de armado (el 202 necesita todo en regla). */
    private static final String VALIDO =
            "{\"level\":\"INFO\",\"timestamp\":\"2026-10-16T22:31:04Z\""
                    + ",\"message\":\"hola\",\"environment\":\"development\"}";

    /** Logger raíz del proyecto: TODO evento de com.projectlog.* pasa aquí. */
    private static final String LOGGERS_OBSERVADOS =
            "com.projectlog";

    /** Literales que de NINGUNA manera pueden aparecer en los logs (E5.17). */
    private static final List<String> LITERALES_PROHIBIDOS = List.of(
            KEY_FIXTURE,
            "plk_ingestaC5Higiene",    // fragmento distintivo de la key
            "X-API-Key",               // el header, tal cual
            "api_key_hash");           // el hash (columna V1) jamás se loguea

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

    private final ch.qos.logback.core.read.ListAppender<
            ch.qos.logback.classic.spi.ILoggingEvent> appender =
            new ch.qos.logback.core.read.ListAppender<>();
    private ch.qos.logback.classic.Level nivelOriginal;

    @BeforeEach
    void acoplarAppender() {
        ch.qos.logback.classic.LoggerContext contexto =
                (ch.qos.logback.classic.LoggerContext)
                        org.slf4j.LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger =
                contexto.getLogger(LOGGERS_OBSERVADOS);
        nivelOriginal = logger.getLevel();
        appender.start();
        // a DEBUG (máxima cobertura de higiene, patrón C2); se restaura abajo
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        logger.addAppender(appender);
    }

    @AfterEach
    void separarAppender() {
        ch.qos.logback.classic.LoggerContext contexto =
                (ch.qos.logback.classic.LoggerContext)
                        org.slf4j.LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger =
                contexto.getLogger(LOGGERS_OBSERVADOS);
        logger.detachAppender(appender);
        logger.setLevel(nivelOriginal);
        appender.stop();
    }

    @Test
    @DisplayName("E5.17: ningún log del proyecto contiene la key, un"
            + " fragmento, el header X-API-Key ni el hash (R5.15)")
    void ningunLogFiltraLaKeyNiSuFragmentoNiElHeaderNiElHash() throws Exception {
        // — armado: flujo de ingesta REAL (guard + service real) —
        when(apiKeyService.sha256(anyString())).thenReturn(HASH_FIXTURE);
        when(projectsRepository.findByApiKeyHash(HASH_FIXTURE))
                .thenReturn(Optional.of(proyecto(7L, "ACTIVE")));

        // 1) ingest 202 (key en el header, pasando por sha256/lookup/insert)
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(VALIDO))
                .andExpect(status().isAccepted());

        // 2) 401: la key en el header, rechazada (sin match)
        when(projectsRepository.findByApiKeyHash(HASH_FIXTURE))
                .thenReturn(Optional.empty());
        mockMvc.perform(post(URL_INDIVIDUAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-API-Key", KEY_FIXTURE)
                        .content(VALIDO))
                .andExpect(status().isUnauthorized());

        // — Aserción (E5.17): TODO lo emitido (mensajes + excepciones) —
        List<String> mensajes = new ArrayList<>();
        for (ch.qos.logback.classic.spi.ILoggingEvent evento : appender.list) {
            mensajes.add(evento.getFormattedMessage());
            ch.qos.logback.classic.spi.IThrowableProxy throwable =
                    evento.getThrowableProxy();
            if (throwable != null && throwable.getMessage() != null) {
                mensajes.add(throwable.getMessage());
            }
        }
        for (String literal : LITERALES_PROHIBIDOS) {
            assertThat(mensajes)
                    .as("higiene R5.15/E5.17: ninguna línea de log puede"
                            + " contener \"%s\"", literal)
                    .noneMatch(mensaje -> mensaje.contains(literal));
        }
    }

    private static ProjectDto proyecto(Long id, String status) {
        return new ProjectDto(id, "payments-service", "descripción de prueba",
                status, "2026-10-21T10:00:00Z", "2026-10-21T11:30:05Z", null,
                false);
    }
}