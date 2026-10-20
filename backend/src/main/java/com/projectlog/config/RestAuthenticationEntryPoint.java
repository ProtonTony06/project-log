package com.projectlog.config;

import java.io.IOException;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * ÚNICO escritor del 401 de la API (S5, CD-4): cualquier fallo de
 * autenticación — sin JWT, token malformado, firma inválida o expirado —
 * responde el MISMO cuerpo JSON genérico {@code {"code": "invalid_token",
 * "message": "Autenticación requerida"}}, sin subtipos ni oráculo de cuál
 * fue la causa (un solo literal evita apoyar la enumeración; S5).
 *
 * <p>Compartido por las TRES {@code SecurityFilterChain} (CD-1): en las
 * cadenas 1 y 3 también se registra como {@code accessDeniedHandler} (esta
 * clase es a la vez {@link AccessDeniedHandler}) para que
 * {@code denyAll()} jamás produzca un 403 HTML — siempre el JSON estándar
 * {@code {code, message}} (Schema §3.1, L2).</p>
 *
 * <p>Higiene (S16/§4.8): NO lee ni loguea el header {@code Authorization};
 * la clase no contiene ningún logger.</p>
 */
@Component
public class RestAuthenticationEntryPoint
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Literal del 401, único para los cuatro casos (CD-4, fijo para C2). */
    static final String CODE_INVALID_TOKEN = "invalid_token";

    /** Mensaje genérico IDÉNTICO para ausente/malformado/inválido/expirado. */
    private static final String MENSAJE = "Autenticación requerida";

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        // Sin lectura ni log del header Authorization (S16).
        escribir401(response);
    }

    /** {@code denyAll()} de las cadenas 1 y 3 con principal autenticado:
     * mismo cuerpo JSON, nunca 403 HTML (CD-1). */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        escribir401(response);
    }

    private void escribir401(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                Map.of("code", CODE_INVALID_TOKEN, "message", MENSAJE));
    }
}