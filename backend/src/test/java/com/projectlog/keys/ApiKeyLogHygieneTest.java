package com.projectlog.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.projectlog.auth.JwtAuthFilter;
import com.projectlog.auth.JwtPrincipal;
import com.projectlog.auth.TokenService;
import com.projectlog.config.CorsConfig;
import com.projectlog.config.RestAuthenticationEntryPoint;
import com.projectlog.config.SecurityConfig;
import com.projectlog.projects.dto.ProjectDto;
import com.projectlog.projects.repository.ProjectsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * E4.14 [L] (R4.13 / Done 6, design §5): higiene de logs de la feature de
 * API keys — réplica del patrón {@code LogHygieneTest} de C2 (ListAppender
 * de logback). Acopla un appender a los loggers de TODAS las clases de
 * {@code com.projectlog.keys} (nivel DEBUG = máxima cobertura) mientras se
 * ejecutan un regenerate (201) y un delete (204) con mocks (mismo armado
 * que {@code ApiKeyControllerMvcTest}), y aserta que NINGÚN evento contiene
 * {@code plk_}, la key del test ni fragmentos suyos.
 */
@ActiveProfiles("test")
@WebMvcTest(ApiKeyController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, CorsConfig.class})
class ApiKeyLogHygieneTest {

    private static final String SECRETO_TEST =
            "secreto-de-test-solo-para-la-suite-c4-abcd1234abcd1234";

    private static final String URL_REGEN =
            "/api/v1/panel/projects/7/api-key/regenerate";
    private static final String URL_REVOCAR =
            "/api/v1/panel/projects/7/api-key";

    private static final String KEY_FIXTURE =
            "plk_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";

    /** Los loggers de TODAS las clases del paquete com.projectlog.keys. */
    private static final List<String> LOGGERS_OBSERVADOS = List.of(
            "com.projectlog.keys.ApiKeyController",
            "com.projectlog.keys.ApiKeyService",
            "com.projectlog.keys.ApiKeyResponse");

    /** Literales que NINGÚN log de keys puede contener (R4.13). */
    private static final List<String> LITERALES_PROHIBIDOS = List.of(
            "plk_",
            KEY_FIXTURE,
            KEY_FIXTURE.substring(0, 10), // el prefix tampoco es secreto de log
            KEY_FIXTURE.substring(10, 22)); // fragmento de la key

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectsRepository projectsRepository;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private TokenService tokenService;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final List<Level> nivelesOriginales = new ArrayList<>();

    @BeforeEach
    void acoplarAppender() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        nivelesOriginales.clear();
        appender.start();
        for (String nombre : LOGGERS_OBSERVADOS) {
            Logger logger = contexto.getLogger(nombre);
            nivelesOriginales.add(logger.getLevel());
            // máxima cobertura de higiene (R4.13: ni a INFO ni a DEBUG)
            logger.setLevel(Level.DEBUG);
            logger.addAppender(appender);
        }
    }

    @AfterEach
    void separarAppender() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (int i = 0; i < LOGGERS_OBSERVADOS.size(); i++) {
            Logger logger = contexto.getLogger(LOGGERS_OBSERVADOS.get(i));
            logger.detachAppender(appender);
            logger.setLevel(nivelesOriginales.get(i));
        }
        appender.stop();
    }

    @Test
    void ningunLogDeKeysContieneLaKey() throws Exception {
        // — Armado idéntico al del MVC test (regenerate 201) —
        when(tokenService.verify(anyString())).thenReturn(Optional.of(
                new JwtPrincipal("admin", "ADMIN", Instant.now().plusSeconds(3600))));
        String bearer = "Bearer " + new TokenService(SECRETO_TEST, 8)
                .issue("admin", "ADMIN").token();
        when(apiKeyService.newKey()).thenReturn(KEY_FIXTURE);
        when(apiKeyService.prefixDe(KEY_FIXTURE)).thenReturn(KEY_FIXTURE.substring(0, 10));
        when(apiKeyService.sha256(KEY_FIXTURE)).thenReturn(new byte[] {1, 2, 3});
        when(projectsRepository.findById(7L)).thenReturn(Optional.of(
                new ProjectDto(7L, "payments-service", "descripción de prueba",
                        "ACTIVE", "2026-10-21T10:00:00Z", "2026-10-21T11:30:05Z",
                        null, false)));
        when(projectsRepository.regenerateKey(anyLong(), any(), anyString()))
                .thenReturn(Optional.of("2026-10-24T12:34:56Z"));

        mockMvc.perform(post(URL_REGEN).header("Authorization", bearer))
                .andExpect(status().isCreated());

        // — delete (204) sin body en el request —
        Mockito.reset(projectsRepository);
        Mockito.reset(apiKeyService);
        when(projectsRepository.revokeKey(7L)).thenReturn(true);

        mockMvc.perform(delete(URL_REVOCAR).header("Authorization", bearer))
                .andExpect(status().isNoContent());

        // — Aserción (patrón LogHygieneTest: mensajes + excepciones) —
        List<String> mensajes = new ArrayList<>();
        for (ILoggingEvent evento : appender.list) {
            mensajes.add(evento.getFormattedMessage());
            IThrowableProxy throwable = evento.getThrowableProxy();
            if (throwable != null && throwable.getMessage() != null) {
                mensajes.add(throwable.getMessage());
            }
        }
        for (String literal : LITERALES_PROHIBIDOS) {
            assertThat(mensajes)
                    .as("higiene R4.13/E4.14: ningún log de keys puede contener \"%s\"", literal)
                    .noneMatch(mensaje -> mensaje.contains(literal));
        }
    }
}