package com.projectlog.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.projectlog.config.CorsConfig;
import com.projectlog.config.RestAuthenticationEntryPoint;
import com.projectlog.config.SecurityConfig;

/**
 * E2.2 [componente L] (CD-9): las TRES ramas de fallo del login — usuario
 * inexistente, password incorrecto y cuerpo malformado — responden 401 con
 * el MISMO literal {@code invalid_credentials} (anti user-enumeración,
 * S17; siempre la MISMA constante del controller). E2.2 [S] lo confirma
 * contra la pila podman.
 *
 * <p>El slice importa la seguridad REAL de C2 (SecurityConfig/JwtAuthFilter/
 * entry point/CorsConfig): sin ella Boot registraría su seguridad por
 * defecto (403 + CSRF) y el 401 de S17 nunca se ejercitaría.</p>
 */
@ActiveProfiles("test")
@WebMvcTest(LoginController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, CorsConfig.class})
class LoginCredentialsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private TokenService tokenService;

    @BeforeEach
    void preparar() {
        // El constructor del controller hashea el dummy de hardening de tiempo.
        when(passwordEncoder.encode(anyString())).thenReturn("phc-dummy-de-test");
    }

    @Test
    void usuarioInexistenteResponde401InvalidCredentials() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class),
                eq("usuario-fantasma"))).thenReturn(List.of());
        mockMvc.perform(post("/api/v1/panel/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"usuario-fantasma\","
                                + "\"password\":\"lo-que-sea\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code")
                        .value(LoginController.CODE_INVALID_CREDENTIALS));
    }

    @Test
    void passwordIncorrectoResponde401InvalidCredentials() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class),
                eq("admin"))).thenReturn(List.of("phc-real-de-bbdd"));
        when(passwordEncoder.matches(eq("password-mal"), eq("phc-real-de-bbdd")))
                .thenReturn(false);
        mockMvc.perform(post("/api/v1/panel/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\","
                                + "\"password\":\"password-mal\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value(LoginController.CODE_INVALID_CREDENTIALS));
    }

    @Test
    void cuerpoMalformadoResponde401InvalidCredentialsNo400() throws Exception {
        mockMvc.perform(post("/api/v1/panel/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("no-es-json"))
                .andExpect(status().isUnauthorized()) // NO el 400 por defecto (S17)
                .andExpect(jsonPath("$.code")
                        .value(LoginController.CODE_INVALID_CREDENTIALS));
    }

    @Test
    void cuerpoConCamposNulosResponde401InvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/panel/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value(LoginController.CODE_INVALID_CREDENTIALS));
    }
}