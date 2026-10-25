package com.projectlog.ingest;

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
 * ÚNICO escritor del 401 de la superficie de ingesta (R5.5, S5-11, D-3):
 * cualquier fallo de credencial — header {@code X-API-Key} ausente,
 * malformado, con hash sin match en BD o key revocada — responde el MISMO
 * cuerpo JSON {@code {"code": "api_key_invalid", "message": "API key de
 * ingesta ausente o inválida"}}, sin subtipos ni oráculo de cuál fue la
 * causa (anti-oráculo C2/S5; E5.12/E5.24).
 *
 * <p>Réplica LITERAL del patrón {@code RestAuthenticationEntryPoint} del
 * panel (C2): {@code ObjectMapper} bean por constructor, SIN logger, SIN
 * leer el header. Se registra como entry point Y como
 * {@code accessDeniedHandler} de la cadena 1 para que JAMÁS exista un
 * 403 HTML en la superficie. El 403 {@code project_archived} JAMÁS sale por
 * aquí: es del borde de ingesta del controller con key VÁLIDA (R5.9) — una
 * {@code AccessDeniedException} que llegara a este handler sería un
 * defecto, no contrato.</p>
 *
 * <p>Constantes {@code CODE_API_KEY_INVALID} y {@code MSG_API_KEY_INVALID}
 * package-private: el {@code IngestController} las REUTILIZA para su
 * 401-honesto de defecto estructural (D-2/mismo paquete) — el cuerpo del 401
 * es EXACTAMENTE el de este entry point (anti-oráculo; desviación menor del
 * literal R5.9 del spec, que declara el MSG como {@code private}: misma
 * cadena, SIN duplicar el literal).</p>
 */
@Component
public class IngestAuthenticationEntryPoint
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Literal del 401 de ingesta (Schema §3.4; visible al paquete ingest, D-2). */
    static final String CODE_API_KEY_INVALID = "api_key_invalid";

    /** Mensaje genérico IDÉNTICO para ausente/malformado/sin match/revocada
     * (package-private: reutilizado por el 401-honesto D-2 del controller). */
    static final String MSG_API_KEY_INVALID =
            "API key de ingesta ausente o inválida";

    private final ObjectMapper objectMapper;

    public IngestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        // Sin lectura ni log del header X-API-Key (R5.15).
        escribir401(response);
    }

    /** AccessDeniedHandler de la cadena 1: mismo cuerpo JSON, nunca 403 HTML (R5.5). */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        escribir401(response);
    }

    private void escribir401(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                Map.of("code", CODE_API_KEY_INVALID, "message", MSG_API_KEY_INVALID));
    }
}