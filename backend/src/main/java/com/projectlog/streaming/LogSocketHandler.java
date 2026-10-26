package com.projectlog.streaming;

import java.io.IOException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectlog.query.FiltrosConsulta;
import com.projectlog.query.FiltrosConsulta.Filtros;

/**
 * C7 (R7.9–R7.13/R7.19–R7.23, D-7.1/D-7.8/D-7.10/D-7.2): handler REAL de
 * /ws/logs — dueño del REGISTRO de sesiones (mapa interno
 * {@code ConcurrentHashMap}; NO bean separado, D-7.1), del {@code set_filters}
 * (shape EXACTO §3.5, D-7.8), del latido (scheduler PROPIO, D-7.10) y de la
 * limpieza total en {@code afterConnectionClosed} (ÚNICO punto de remove —
 * R-7.c). Toda sesión registrada va envuelta en
 * {@code ConcurrentWebSocketSessionDecorator} (doble barrera 1013, §1.G).
 *
 * <p>El estado por sesión (filtros parseados + último pong) vive EN LOS
 * ATTRIBUTES de sesión (R7.9 literal) con claves {@code static final} y
 * acceso SIEMPRE bajo {@code synchronized (sesion.getAttributes())} (patrón
 * oficial de Spring WebSocket para estado por sesión).</p>
 *
 * <p>Higiene (R7.7/R7.12/R7.35): NINGÚN logueo con contenido de mensajes,
 * filtros, tokens o query — solo líneas genéricas.</p>
 */
@Component
public class LogSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(LogSocketHandler.class);

    /** Dueño del registro (D-7.1): sessionId → envoltura decoradora. */
    private final ConcurrentHashMap<String,
            ConcurrentWebSocketSessionDecorator> registradas =
            new ConcurrentHashMap<>();

    /** Claves de estado por sesión (R7.9 — access SIEMPRE bajo lock). */
    static final String ATTR_FILTROS = "streaming.filtros";       // Filtros|null
    static final String ATTR_ULTIMO_PONG = "streaming.ultimo-pong"; // Long (nanoTime)

    /** Conjunto CERRADO de claves de filters de §3.5 (D-7.8 — sin extras). */
    private static final Set<String> CLAVES_FILTERS = Set.of("project_id",
            "environment", "level", "from", "to", "text");

    /** R7.29 — @Value de las 4 properties por constructor. */
    private final long heartbeatSeconds;
    private final long idleTimeoutSeconds;
    private final long sendTimeLimitMs;
    private final int bufferSizeBytes;

    private final ObjectMapper objectMapper;

    /** Scheduler PROPIO del latido (D-7.10): single-thread daemon. */
    private final ScheduledExecutorService latido = Executors
            .newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "streaming-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public LogSocketHandler(
            @Value("${projectlog.streaming.heartbeat-seconds}") long heartbeatSeconds,
            @Value("${projectlog.streaming.idle-timeout-seconds}") long idleTimeoutSeconds,
            @Value("${projectlog.streaming.send-time-limit-ms}") long sendTimeLimitMs,
            @Value("${projectlog.streaming.buffer-size-bytes}") int bufferSizeBytes,
            ObjectMapper objectMapper) {
        this.heartbeatSeconds = heartbeatSeconds;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        this.sendTimeLimitMs = sendTimeLimitMs;
        this.bufferSizeBytes = bufferSizeBytes;
        this.objectMapper = objectMapper;
        latido.scheduleAtFixedRate(this::latidoTick, heartbeatSeconds,
                heartbeatSeconds, TimeUnit.SECONDS);
    }

    /** Registro envuelto en el decorador + umbral desde ahora (D-7.10). */
    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) {
        ConcurrentWebSocketSessionDecorator envuelta =
                new ConcurrentWebSocketSessionDecorator(sesion,
                        (int) sendTimeLimitMs, bufferSizeBytes);
        registradas.put(sesion.getId(), envuelta);
        synchronized (sesion.getAttributes()) {
            sesion.getAttributes().put(ATTR_ULTIMO_PONG, System.nanoTime());
        }
    }

    /**
     * Shape ESTRECHO de set_filters (R7.10/R7.11/D-7.8): SOLO el objeto
     * {@code {"type":"set_filters","filters":{…}}} con type EXACTO y claves
     * del conjunto cerrado de 6 (sin extras — JAMÁS cursor/limit). Clave
     * ausente o valor null = filtro sin aplicar; PRESENTE no-null debe ser
     * String JSON salvo {@code project_id} que admite también número JSON
     * (normalizado a String antes del parser). OK → {@code attributes.put}
     * bajo lock + RETURN (el servidor JAMÁS responde nada — R7.11);
     * {@code FiltroInvalido} → NO aplica el cambio (conserva vigentes), NO
     * cierra, log WARN sin contenido (R-7.g). Mensaje no-contrato →
     * IGNORADO sin cerrar, log DEBUG sin contenido (R7.12).
     */
    @Override
    protected void handleTextMessage(WebSocketSession sesion,
            TextMessage mensaje) {
        JsonNode root;
        try {
            root = objectMapper.readTree(mensaje.getPayload());
        } catch (IOException ex) { // JsonProcessingException (check)
            LOG.debug("mensaje no-contrato ignorado");
            return;
        }
        if (!root.isObject() || root.size() != 2) {
            LOG.debug("mensaje no-contrato ignorado");
            return;
        }
        JsonNode tipo = root.get("type");
        JsonNode filters = root.get("filters");
        if (tipo == null || !tipo.isTextual()
                || !"set_filters".equals(tipo.asText()) || filters == null
                || !filters.isObject()) {
            LOG.debug("mensaje no-contrato ignorado");
            return;
        }
        // TODA clave DEBE pertenecer al conjunto cerrado de 6 — cualquier
        // extra (incl. cursor/limit) → no aplica el cambio (R7.10/R7.12).
        java.util.Iterator<String> nombres = filters.fieldNames();
        while (nombres.hasNext()) {
            if (!CLAVES_FILTERS.contains(nombres.next())) {
                LOG.debug("mensaje no-contrato ignorado");
                return;
            }
        }
        String projectId = valorComoString(filters.get("project_id"), "project_id");
        String environment = valorComoString(filters.get("environment"), null);
        String level = valorComoString(filters.get("level"), null);
        String from = valorComoString(filters.get("from"), null);
        String to = valorComoString(filters.get("to"), null);
        String text = valorComoString(filters.get("text"), null);
        if (projectId == null && filters.has("project_id")
                && !filters.get("project_id").isNull()) {
            LOG.debug("mensaje no-contrato ignorado"); // booleano/array/objeto
            return;
        }
        if ((environment == null && filters.has("environment")
                && !filters.get("environment").isNull())
                || (level == null && filters.has("level")
                        && !filters.get("level").isNull())
                || (from == null && filters.has("from")
                        && !filters.get("from").isNull())
                || (to == null && filters.has("to")
                        && !filters.get("to").isNull())
                || (text == null && filters.has("text")
                        && !filters.get("text").isNull())) {
            LOG.debug("mensaje no-contrato ignorado");
            return;
        }
        Filtros filtros;
        try {
            // ÚNICA fuente ADR-7 (D-7.12): la MISMA semántica que GET /logs
            filtros = FiltrosConsulta.parsear(projectId, environment, level,
                    from, to, text);
        } catch (FiltrosConsulta.FiltroInvalido ex) {
            // INVÁLIDO: conserva vigentes, NO cierra, WARN sin contenido
            LOG.warn("set_filters inválido: filtros vigentes conservados");
            return;
        }
        // OK: recalificación inmediato para los SIGUIENTES lotes.
        synchronized (sesion.getAttributes()) {
            sesion.getAttributes().put(ATTR_FILTROS, filtros);
        }
        // el servidor JAMÁS contesta nada (R7.11 — literal §3.5)
    }

    /** Frame binario: IGNORADO sin cerrar (R7.12/S7-6 — log sin contenido). */
    @Override
    protected void handleBinaryMessage(WebSocketSession sesion,
            BinaryMessage mensaje) {
        LOG.debug("mensaje no-contrato ignorado");
    }

    /** Pong RFC del cliente alimenta el latido (R7.19/S7-9). */
    @Override
    protected void handlePongMessage(WebSocketSession sesion,
            PongMessage mensaje) {
        synchronized (sesion.getAttributes()) {
            sesion.getAttributes().put(ATTR_ULTIMO_PONG, System.nanoTime());
        }
    }

    /** Cualquier código de cierre → ÚNICO punto de remove (R7.13/R-7.c). */
    @Override
    public void afterConnectionClosed(WebSocketSession sesion,
            CloseStatus estado) {
        registradas.remove(sesion.getId());
    }

    /**
     * 1013 por lentitud (D-7.2/R7.22) — WARN ÚNICA línea sin contenido.
     *
     * <p>El close SE HACE sobre el DELEGADO (hallazgo del test E7.13 sobre el
     * decorador real 6.2.19): una vez {@code limitExceeded}, el propio
     * {@code ConcurrentWebSocketSessionDecorator.close(status)} SUSTITUYE el
     * status pedido por {@code SESSION_NOT_RELIABLE} (1011) — cerrándolo a
     * través del decorador, el cliente jamás vería el 1013 contractual
     * (§1.G: el ÚNICO código pineado por lentitud). Desenvolver
     * {@code WebSocketSessionDecorator.getDelegate()} lo garantiza; además,
     * el close del delegado en las sesiones reales mantiene idempotente al
     * contenedor ante el close del decorador de los ticks posteriores.</p>
     */
    void cerrarPorLentitud(WebSocketSession sesion) {
        try {
            WebSocketSession objetivo = sesion;
            if (sesion instanceof WebSocketSessionDecorator decorador) {
                objetivo = decorador.getDelegate();
            }
            // 1013 (Spring no lo pinnea como constant — literal del §1.G)
            objetivo.close(new CloseStatus(1013));
        } catch (IOException | SessionLimitExceededException ex) {
            // la sesión ya está muerta — la limpieza hará su trabajo
        }
        LOG.warn("sesión cerrada por lentitud (1013)");
    }

    /** Snapshot (lista COPIA) de sesiones vivas para el broadcaster. */
    List<ConcurrentWebSocketSessionDecorator> sesionesVivas() {
        return List.copyOf(registradas.values());
    }

    /** Ciclo de vida del scheduler (patrón igual al broadcaster). */
    @PreDestroy
    void apagar() {
        latido.shutdown();
        try {
            if (!latido.awaitTermination(5, TimeUnit.SECONDS)) {
                latido.shutdownNow();
            }
        } catch (InterruptedException ex) {
            latido.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // — Tick del latido (D-7.10): (a) PING RFC 0x9 a cada sesión vía el
    // decorador — NUNCA JSON (R7.19); (b) GC: sin pong dentro del umbral →
    // close GOING_AWAY (1001 — decisión anclada S7-9; el ÚNICO código
    // pineado por lentitud es 1013) + WARN genérico; la limpieza la hace
    // afterConnectionClosed. —
    private void latidoTick() {
        for (ConcurrentWebSocketSessionDecorator envuelta : sesionesVivas()) {
            try {
                envuelta.sendMessage(new PingMessage()); // frame CONTROL 0x9
            } catch (SessionLimitExceededException ex) {
                cerrarPorLentitud(envuelta);
                continue;
            } catch (IOException ex) {
                LOG.warn("latido: ping fallido a una sesión");
            }
            Long ultimoPong;
            synchronized (envuelta.getAttributes()) {
                ultimoPong = (Long) envuelta.getAttributes()
                        .get(ATTR_ULTIMO_PONG);
            }
            long umbral = idleTimeoutSeconds * 1_000_000_000L;
            if (ultimoPong == null
                    || System.nanoTime() - ultimoPong > umbral) {
                try {
                    envuelta.close(CloseStatus.GOING_AWAY); // 1001
                } catch (IOException ex) {
                    LOG.warn("latido: cierre fallido de una sesión muda");
                }
                LOG.warn("latido: sesión sin pong vencida, cierre 1001");
            }
        }
    }

    /**
     * Normaliza el valor de un filtro §3.3: clave ausente o {@code null} →
     * {@code null} (filtro sin aplicar); PRESENTE no-null String JSON → su
     * texto (el {@code ""} PRESENTE VIAJA al parser — inválido en
     * environment/level/from/to, LEGAL en text — patrón C6 S6-2);
     * {@code project_id} admite TAMBIÉN número JSON (normalizado a String —
     * R7.10). Booleano/array/objeto → {@code null} marcado con {@code
     * presenteNoString} (inválido → no-contrato, el llamador lo detecta).
     */
    private String valorComoString(JsonNode nodo, String keyNumerica) {
        if (nodo == null || nodo.isNull()) {
            return null;
        }
        if (nodo.isTextual()) {
            return nodo.asText();
        }
        if (keyNumerica != null && nodo.isNumber()) {
            return nodo.asText(); // p. ej. 7 → "7" (normalizado a String)
        }
        return null; // booleano/array/objeto → inválido (no String)
    }
}