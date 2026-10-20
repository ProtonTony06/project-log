package com.projectlog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registro nativo del WebSocket en la ruta DEFINITIVA /ws/logs (TRD §9/§2.8:
 * única ruta WS; sin SockJS — ADR-5). Es exactamente la mecánica que C7
 * reutiliza: allí solo cambia el handler (EchoSocketHandler →
 * LogSocketHandler + HandshakeInterceptor JWT); ni el nginx ni el smoke
 * cambian.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final com.projectlog.streaming.EchoSocketHandler echoSocketHandler;

    public WebSocketConfig(com.projectlog.streaming.EchoSocketHandler echoSocketHandler) {
        this.echoSocketHandler = echoSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(echoSocketHandler, "/ws/logs");
        // C1: sin .withSockJS() — ADR-5; sin .addInterceptors(...) — C7
    }
}