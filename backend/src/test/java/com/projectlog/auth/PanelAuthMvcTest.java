package com.projectlog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.projectlog.config.CorsConfig;
import com.projectlog.config.RestAuthenticationEntryPoint;
import com.projectlog.config.SecurityConfig;

/**
 * E2.4 [L] / E2.6 / E2.19 [L] (CD-9): el panel responde 401 JSON
 * {@code {"code": "invalid_token"}} para token ausente, malformado, con
 * firma alterada y expirado (un solo literal, sin subtipos — S5); el cuerpo
 * nace del entry point con {@code Content-Type: application/json} (CD-4,
 * nunca HTML); y NINGUNA línea de log produce el header {@code Authorization},
 * la palabra {@code Bearer} ni el JWT (S16/E2.19 — ListAppender de logback
 * sobre los loggers de JwtAuthFilter/RestAuthenticationEntryPoint/TokenService).
 */
@ActiveProfiles("test")
@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, CorsConfig.class})
class PanelAuthMvcTest {

    /** ≥32 bytes UTF-8, solo para CONSTRUIR el JWT de prueba (string). */
    private static final String SECRETO_TEST =
            "secreto-de-test-solo-para-la-suite-c2-abcd1234abcd1234";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    /** JWT de prueba emitido con un TokenService REAL (solo el string). */
    private String jwt;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private final List<ILoggingEvent> eventos = new ArrayList<>();

    private Logger loggerRaiz;

    @BeforeEach
    void preparar() {
        jwt = new TokenService(SECRETO_TEST, 8).issue("admin", "ADMIN").token();
        eventos.clear();
        appender.list = eventos;
        loggerRaiz = (Logger) LoggerFactory.getLogger("com.projectlog");
        loggerRaiz.addAppender(appender);
        loggerRaiz.setLevel(ch.qos.logback.classic.Level.DEBUG);
        Mockito.reset(tokenService); // sin stubs sueltos: cada test stubbea lo que usa
    }

    @AfterEach
    void limpiar() {
        loggerRaiz.detachAppender(appender);
    }

    @Test
    void meSinHeaderResponde401JsonInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/panel/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("invalid_token"));
    }

    @Test
    void meConTokenMalformadoResponde401InvalidToken() throws Exception {
        when(tokenService.verify(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/panel/me")
                        .header("Authorization", "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("invalid_token"));
    }

    @Test
    void meConFirmaAlteradaResponde401InvalidToken() throws Exception {
        // verify (mockeado) devuelve vacío ⇒ mismo resultado que firma alterada real.
        when(tokenService.verify(anyString())).thenReturn(Optional.empty());
        String[] partes = jwt.split("\\.");
        String adulterado = partes[0] + "." + partes[1] + "." + partes[2] + "x";
        mockMvc.perform(get("/api/v1/panel/me")
                        .header("Authorization", "Bearer " + adulterado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"));
    }

    @Test
    void meConTokenExpiradoResponde401InvalidTokenYSinSubtipos() throws Exception {
        when(tokenService.verify(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/panel/me")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"))
                .andExpect(jsonPath("$.field").doesNotExist()); // un solo literal (CD-4)
    }

    @Test
    void el401NaceDelEntryPointConJsonEstandarYNoHtml() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/panel/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Autenticación requerida"))
                .andReturn();
        String cuerpo = resultado.getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("<html"); // nunca HTML (CD-4)
    }

    @Test
    void hygieneDeLogsNingunaLineaConAuthorizationBearerNiElJwt() throws Exception {
        when(tokenService.verify(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/panel/me")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/panel/me"))
                .andExpect(status().isUnauthorized());

        // E2.19: ninguna línea de log contiene el header, la palabra Bearer
        // ni el JWT emitido (ni un fragmento significativo del mismo).
        String fragmento = jwt.substring(0, 20);
        assertThat(eventos)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(msg -> msg.contains("Authorization")
                        || msg.contains("Bearer")
                        || msg.contains(jwt)
                        || msg.contains(fragmento));
    }
}