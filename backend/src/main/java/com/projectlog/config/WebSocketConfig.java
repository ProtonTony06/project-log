package com.projectlog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registro nativo del WebSocket en la ruta DEFINITIVA /ws/logs (TRD §9/§2.8:
 * única ruta WS; sin SockJS — ADR-5). C7: el handler real es
 * {@code LogSocketHandler} (dueño del registro, set_filters y latido) con el
 * {@code JwtHandshakeInterceptor} (JWT+Origin pre-upgrade) — sustitución
 * anunciada por el propio javadoc del EchoSocketHandler de C1.
 *
 * <p>ORIGEN EXACTO del registro (hallazgo del test E7.8 con el cliente real
 * del JDK, 2026-10-26): {@code AbstractWebSocketHandlerRegistration} de
 * spring-websocket 6.2.19 AÑADE SIEMPRE un {@code OriginHandshakeInterceptor}
 * por defecto (403 con {@code CorsConfiguration} de la registro); con la
 * lista de orígenes VACÍA rechaza CUALQUIER {@code Origin} cross-origin —
 * el 403 del by-pass llegaba ANTES de nuestro {@code JwtHandshakeInterceptor}
 * y sin política conocida. Se DECLARA el orígen EXACTO de
 * {@code projectlog.security.frontend-origin} (misma propiedad que CorsConfig
 * — CD-7): {@code CorsConfiguration.checkOrigin} lo devuelve → 101 por
 * nginx/consola; JAMÁS {@code "*"} y JAMÁS {@code setAllowedOriginPatterns}
 * (G1 literal de CorsConfig). Con la propiedad VACÍA/S26 el dueño de la
 * política SIGUE SIENDO el {@code JwtHandshakeInterceptor} (403 a TODO,
 * D-7.9): el registro no declara {@code setAllowedOrigins} (la lista queda
 * vacía y el default se mantiene inerte — nunca se alcanza).</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final com.projectlog.streaming.LogSocketHandler logSocketHandler;

    private final com.projectlog.streaming.JwtHandshakeInterceptor
            jwtHandshakeInterceptor;

    /** ÚNICO orígen EXACTO permitido (S26: vacío ⇒ sin configuración). */
    private final String frontendOrigin;

    public WebSocketConfig(
            com.projectlog.streaming.LogSocketHandler logSocketHandler,
            com.projectlog.streaming.JwtHandshakeInterceptor
                    jwtHandshakeInterceptor,
            @Value("${projectlog.security.frontend-origin:}")
            String frontendOrigin) {
        this.logSocketHandler = logSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration
                registro = registry.addHandler(logSocketHandler, "/ws/logs")
                        .addInterceptors(jwtHandshakeInterceptor);
        if (frontendOrigin != null && !frontendOrigin.isBlank()) {
            // EXACTO de la propiedad (nunca "*" ni patterns — G1): el
            // OriginHandshakeInterceptor ADICIONADO por defecto por
            // AbstractWebSocketHandlerRegistration lo verifica con
            // CorsConfiguration.checkOrigin (403 lo pone el INTERCEPTOR
            // propio PRIMERO — D-7.9 — el default es la red de seguridad).
            registro.setAllowedOrigins(frontendOrigin);
        }
        // C7 ancla resuelta: .addInterceptors(jwtHandshakeInterceptor) — JWT+Origin
        // pre-upgrade (spec §1.B); sin .withSockJS() — ADR-5 (frase conservada)
    }
}