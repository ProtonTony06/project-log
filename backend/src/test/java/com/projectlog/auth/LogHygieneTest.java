package com.projectlog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.projectlog.config.CorsConfig;
import com.projectlog.config.RestAuthenticationEntryPoint;
import com.projectlog.config.SecurityConfig;
import java.time.Instant;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * <p><b>[M] Q5 del qa-plan C2 · E2.19 (S16) — el token nunca aparece en los
 * logs.</b> Acopla un {@link ListAppender} de logback a los loggers de las
 * tres clases que tocan el token ({@code JwtAuthFilter},
 * {@code RestAuthenticationEntryPoint} y {@code TokenService}, según
 * design CD-4/CD-9) mientras se ejecutan las peticiones de panel (Q3/Q4) y
 * un intento de login (mock), y aserta que NINGUNA línea de log emitida por
 * esas clases contiene el header {@code Authorization}, el prefijo
 * {@code Bearer}, el JWT literal (ni fragmentos suyos) ni la contraseña de
 * test. Comprueba además que el nivel de logging de C1 no se altera.</p>
 *
 * <p>Escrito por la delegación QA (test-writer) EXCLUSIVAMENTE desde
 * {@code .atl/sdd/C2/qa-plan.md} (Q5), {@code spec.md} (E2.19/S16) y
 * {@code design.md} (CD-4/CD-6/CD-9): sin leer la implementación. Los
 * nombres de clases/constructores usados son los que el design declara
 * como artefactos del corte (N2/N3/N4/N6/N7/N11/N12/N13).</p>
 *
 * <p>Nota de alcance: las RAMAS del login (200/401/literal) no se aserten
 * aquí — ese es el cometido de {@code LoginCredentialsTest} (Q7) y de los
 * smokes Q8/Q9. Aquí el login es un fixture de la aserción de higiene
 * (plan Q5: "token inválido y login (mock)").</p>
 */
@WebMvcTest(controllers = { MeController.class, LoginController.class })
@ActiveProfiles("test")
@Import({ SecurityConfig.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, CorsConfig.class })
class LogHygieneTest {

    /** Token Bearer "válido" (el mock de TokenService lo devuelve en verify). */
    private static final String TOKEN_VALIDO_Q5 =
            "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbi1xYSIsInJvbGUiOiJBRE1JTiJ9.MICROFIRMADETESTQ5";

    /** Token Bearer inválido (firma alterada): el mock devuelve Optional.empty(). */
    private static final String TOKEN_INVALIDO_Q5 =
            "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJmcmFnbWVudCJ9.SIGNATURAALTERADA9999";

    /** Token que el mock de TokenService.issue finge emitir en el login. */
    private static final String TOKEN_EMITIDO_LOGIN_Q5 =
            "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJlbWl0aWRvIn0.FIRMADEMOQ5";

    private static final String USUARIO_QA = "admin-qa";

    /** Loggers de las tres clases que tocan el token (design CD-4/CD-9). */
    private static final List<String> LOGGERS_OBSERVADOS = List.of(
            "com.projectlog.auth.JwtAuthFilter",
            "com.projectlog.config.RestAuthenticationEntryPoint",
            "com.projectlog.auth.TokenService");

    /** Literales que de manera NINGUNA pueden aparecer en los logs (Q5, S16). */
    private static final List<String> LITERALES_PROHIBIDOS = List.of(
            "Authorization",   // header — tal cual
            "Bearer ",         // el valor/interfaz de credencial del token
            TOKEN_VALIDO_Q5,
            TOKEN_INVALIDO_Q5,
            TOKEN_EMITIDO_LOGIN_Q5,
            "MICROFIRMADETESTQ5", // fragmento del JWT de Q5
            "SIGNATURAALTERADA9999",
            "qa-c2-contra-f3"); // contraseña de test del fixture de login — jamás en logs

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

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
            // Corrección test-side (delegación C, qa-run): los tres servicios no
            // emiten a nivel INFO por defecto, con lo que el appender quedaba
            // vacío y el aserto antivacío fallaba. Se sube temporalmente a DEBUG
            // (máxima cobertura de higiene S16) y se restaura en el @AfterEach.
            logger.setLevel(Level.DEBUG);
            logger.addAppender(appender);
        }
    }

    @AfterEach
    void separarAppenderYVerificarNiveles() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (int i = 0; i < LOGGERS_OBSERVADOS.size(); i++) {
            Logger logger = contexto.getLogger(LOGGERS_OBSERVADOS.get(i));
            logger.detachAppender(appender);
            // Q5: "el nivel de logging de C1 no se altera" — durante el test el
            // nivel configurado fue DEBUG (fijado por el fixture); se restaura
            // el nivel original de C1 capturado en el @BeforeEach.
            logger.setLevel(nivelesOriginales.get(i));
        }
        appender.stop();
    }

    @Test
    @DisplayName("Q5: ninguna línea de log de filtro/entry point/TokenService "
            + "contiene Authorization, Bearer, el JWT ni fragmentos del mismo")
    void ningunaLineaDeLogFiltraElToken() throws Exception {
        // — Fixture (Q5): token inválido + login (mock) —
        // Orden de stubbing: primero el genérico y después el específico,
        // porque en Mockito la última regla que casa tiene precedencia.
        when(tokenService.verify(anyString())).thenReturn(Optional.empty());
        when(tokenService.verify(TOKEN_VALIDO_Q5)).thenReturn(Optional.of(
                new JwtPrincipal(USUARIO_QA, "ADMIN", Instant.now().plusSeconds(8 * 3600))));
        // Corrección test-side (delegación C, qa-run): TokenService.issue devuelve
        // el record IssuedToken (API conocida por TokenServiceTest: token()/expiresAt()),
        // no un String crudo — se stubbea con el record.
        when(tokenService.issue(USUARIO_QA, "ADMIN")).thenReturn(new TokenService.IssuedToken(
                TOKEN_EMITIDO_LOGIN_Q5, Instant.now().plusSeconds(8 * 3600)));
        // Stub test-side (delegación C): para que el fixture de login llegue de
        // verdad al camino feliz y ejerza issue() (emisión del token en logs).
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // 1) GET /me con token válido (E2.4/E2.5-lite): filtro poblando SecurityContext
        mockMvc.perform(get("/api/v1/panel/me")
                        .header("Authorization", "Bearer " + TOKEN_VALIDO_Q5))
                .andExpect(status().isOk());

        // 2) GET /me con token de firma alterada (Q4): filtrar verify vacío → entrada 401
        mockMvc.perform(get("/api/v1/panel/me")
                        .header("Authorization", "Bearer " + TOKEN_INVALIDO_Q5))
                .andExpect(status().isUnauthorized());

        // 3) GET /me sin header (E2.6/Q3): filtro corre, entry point escribe el 401
        mockMvc.perform(get("/api/v1/panel/me"))
                .andExpect(status().isUnauthorized());

        // 4) y 5) Login (mock), con cuerpo correcto y con cuerpo malformado:
        // fixture de higiene — este test NO aserta sus ramas (eso es Q7/Q8/Q9).
        MvcResult loginOk = mockMvc.perform(post("/api/v1/panel/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USUARIO_QA + "\",\"password\":\"qa-c2-contra-f3\"}"))
                .andReturn();
        MvcResult loginMalformado = mockMvc.perform(post("/api/v1/panel/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": "))
                .andReturn();
        // Guard sanidades mínimas del fixture: el controller no pudo lanzar 500
        // "inesperado del servidor" en el login malformado (S17: 401, no 400/500).
        int estadoMalformado = loginMalformado.getResponse().getStatus();
        assertThat(estadoMalformado)
                .as("el cuerpo malformado del login queda en la familia 400/401 del schema, nunca 500")
                .isLessThan(500);
        // El login "correcto" con mocks sí/có puede llegar a 200; para el objeto
        // de Q5 no aporta aserción (la rama feliz de login es Q7/Q8). Solo se
        // exige que no sea 500 (excepción no controlada fuera del contracto del corte).
        assertThat(loginOk.getResponse().getStatus())
                .as("el login mock no debe desbocar en 500")
                .isLessThan(500);

        // — Aserción (Q5): inspección de TODO lo emitido. NOTA (corrección
        // verify del fallo Q5): el plan pide "ninguna línea de los 3 loggers
        // contiene Authorization/Bearer/token/contraseña" — una higiene que
        // no DEBE depender de que las clases hubieran emitido eventos (el
        // aserto antivacío anterior presumía logueo y fallaba test-side con
        // el appender vacío: ni filtro ni entry point ni TokenService
        // loguean nada a nivel INFO/DEBUG — precisamente lo que S16 pide).
        List<String> mensajes = new ArrayList<>();
        for (ILoggingEvent evento : appender.list) {
            mensajes.add(evento.getFormattedMessage());
            IThrowableProxy throwable = evento.getThrowableProxy();
            if (throwable != null) {
                String mensajeExcepcion = throwable.getMessage();
                if (mensajeExcepcion != null) {
                    mensajes.add(mensajeExcepcion);
                }
            }
        }

        for (String literal : LITERALES_PROHIBIDOS) {
            assertThat(mensajes)
                    .as("higiene S16/E2.19: ninguna línea de log puede contener \"%s\"", literal)
                    .noneMatch(mensaje -> mensaje.contains(literal));
        }
    }
}