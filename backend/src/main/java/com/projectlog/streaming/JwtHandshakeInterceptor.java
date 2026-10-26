package com.projectlog.streaming;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.server.HandshakeInterceptor;
import com.projectlog.auth.TokenService;

/**
 * C7 (R7.5–R7.8, D-7.9): HandshakeInterceptor de /ws/logs — el auth vive
 * SOLO aquí (la cadena 3 {@code permitAll()} de {@code /ws/logs} queda tal
 * cual — CERO diff en SecurityConfig; una API key JAMÁS autentica el socket).
 *
 * <p>ORDEN DEL REJECT VINCULANTE (S7-12): <b>Origin PRIMERO</b> →
 * <b>token LUEGO</b>. El rechazo es SIEMPRE pre-upgrade con status 403
 * (familia Origin) o 401 (familia token), cuerpo VACÍO (NUNCA se escribe
 * body — el literal {@code invalid_token}/entry point es del PANEL REST,
 * otra superficie, S7-3/CD-4), SIN tocar recursos del handler y SIN crear
 * sesión. NUNCA se escribe en attributes (NINGÚN JWT queda en sesión).
 * La propiedad {@code frontend-origin} vacía rechaza TODO handshake (S26
 * extendido — NUNCA {@code "*"}); la comparación de Origin es EXACTA (sin
 * normalizar); header Origin AUSENTE continúa (clientes RFC no-browser
 * legítimos — S7-12).</p>
 *
 * <p>Higiene (R7.7/S16/R-7.d): el rechazo loguea UNA línea WARN genérica
 * {@code "handshake WS rechazado"} — sin token, sin fragmentos, sin causa
 * distinguible, sin la cadena de query (anti-oráculo, patrón CD-4).</p>
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger LOG =
            LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final TokenService tokenService;   // por referencia (C2) — SOLO
                                               // verify (R7.8)
    private final String frontendOrigin;       // falta ⇒ TODO rechazado (S26)

    public JwtHandshakeInterceptor(TokenService tokenService,
            @Value("${projectlog.security.frontend-origin:}") String frontendOrigin) {
        this.tokenService = tokenService;
        this.frontendOrigin = frontendOrigin;
    }

    /**
     * Origin PRIMERO (403) → token LUEGO (401). Devuelve {@code true} solo
     * si ambos checks pasan (upgrade procede).
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
            ServerHttpResponse response, WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        // — 1. Origin (S7-12/D-7.9): propiedad vacía → rechazo de TODO
        //   handshake; header PRESENTE y distinto → rechazo; header AUSENTE
        //   → continúa (clientes RFC no-browser legítimos). Comparación
        //   EXACTA, sin normalizar. —
        if (frontendOrigin == null || frontendOrigin.isEmpty()) {
            rechazar(response, HttpStatus.FORBIDDEN);
            return false;
        }
        List<String> origins = request.getHeaders().get("Origin");
        if (origins == null) {
            // ausente → continúa hacia el token
        } else if (origins.size() != 1
                || !frontendOrigin.equals(origins.get(0))) {
            // presente (una o varias) y ≠ propiedad EXACTA → 403; jamás "*"
            rechazar(response, HttpStatus.FORBIDDEN);
            return false;
        }

        // — 2. token (query param EXACTO "token" — v1 contractual): ausente
        //   o vacío → 401; verify vacío (expirado/inválido — Optional SIN
        //   distinguir causa, S5/R-7.d) → 401. —
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || token.isEmpty()
                || tokenService.verify(token).isEmpty()) {
            rechazar(response, HttpStatus.UNAUTHORIZED);
            return false;
        }
        return true;
    }

    private static void rechazar(ServerHttpResponse response,
            HttpStatus estado) {
        // pre-upgrade: status + cuerpo VACÍO (NUNCA se escribe body); UNA
        // línea WARN genérica — sin causa distinguible (R7.7 anti-oráculo).
        response.setStatusCode(estado);
        LOG.warn("handshake WS rechazado");
    }

    /** No-op (D-7.9). */
    @Override
    public void afterHandshake(ServerHttpRequest request,
            ServerHttpResponse response, WebSocketHandler wsHandler,
            Exception exception) {
        // sin estado, sin logs
    }
}