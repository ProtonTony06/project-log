package com.projectlog.streaming;

import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handler de ECO temporal en /ws/logs — infraestructura de prueba del proxy
 * WS y del smoke scripts/ws_echo_test.py.
 *
 * <p>Es el ÚNICO punto de sustitución del corte C7, donde el registro en
 * {@code WebSocketConfig} pasa a usar {@code LogSocketHandler} (JWT 401
 * pre-upgrade). Devuelve cada frame de texto con el mismo payload; sin
 * broadcast, sin estado, sin frames binarios (el protocolo del smoke solo
 * usa texto).</p>
 */
@Component
public class EchoSocketHandler extends TextWebSocketHandler {

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws IOException {
        session.sendMessage(new TextMessage(message.getPayload()));
    }
}