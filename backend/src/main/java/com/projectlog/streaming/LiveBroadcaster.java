package com.projectlog.streaming;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.TimeUnit;
import java.time.OffsetDateTime;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectlog.auth.LoginController;
import com.projectlog.ingest.IngestListener;
import com.projectlog.query.FiltrosConsulta.Filtros;
import com.projectlog.query.repository.LogsQueryRepository;
import com.projectlog.query.repository.LogsQueryRepository.FilaBatch;

/**
 * C7 (R7.14–R7.18, D-7.2/D-7.3/D-7.4/D-7.6/D-7.7) — PRIMER bean del seam
 * C5: {@code implements IngestListener}; el bean nuevo ACTIVA el seam de
 * {@code IngestService} (L126, lista inyectada) SIN diff en {@code ingest/**}.
 * El nombre es CONTRACTUAL (TRD §2.9 / Schema §3.5).
 *
 * <p>{@code onLotePersistido} corre en el HILO TOMCAT de la 202 y SOLO
 * ENCOLA en su executor monohilo daemon {@code streaming-broadcast} (P-6,
 * FIFO — orden de lotes preservado por construcción; cola sin límite
 * declarado — R-7.a cerrado). JAMÁS propaga una excepción al seam (R7.18;
 * D-12 de C5 deja las excepciones del listener sin capturar — el catch vive
 * AQUÍ). La emisión SÓLO ocurre tras COMMIT (el hook ya lo es — at-least-once
 * por el COMMIT del seam, sin replay contractual — R-7.i).</p>
 *
 * <p>Frame batch §3.5 CONTRACTO (D-7.3): LinkedHashMap con EXACTAMENTE 5
 * claves EN ORDEN (type/batch_id/project_id/received_at/logs), Jackson del
 * BEAN único de Boot (D-3), UN TextMessage — JAMÁS fragmentado, JAMÁS varios
 * frames por lote, JAMÁS un JSON del servidor distinto de {@code batch}.</p>
 *
 * <p>Matching por sesión EN SERVIDOR (S7-10/D-7.6/D-7.7) a NIVEL DE LOTE:
 * el frame SIEMPRE íntegro (jamás recortado por log). {@code ATTR_FILTROS
 * == null} ⇒ sin calificar ⇒ sin frames.</p>
 *
 * <p>Higiene (R7.7/R7.35): NINGÚN logueo de contenido de mensajes ni de
 * filtros — solo líneas genéricas.</p>
 */
@Component
public class LiveBroadcaster implements IngestListener {

    private static final Logger LOG =
            LoggerFactory.getLogger(LiveBroadcaster.class);

    private final LogSocketHandler handler;
    private final LogsQueryRepository logsQueryRepository;
    private final ObjectMapper objectMapper;   // bean ÚNICO de Boot (D-3)
    private final ExecutorService broadcast;   // monohilo FIFO daemon (P-6)

    public LiveBroadcaster(LogSocketHandler handler,
            LogsQueryRepository logsQueryRepository, ObjectMapper objectMapper) {
        this.handler = handler;
        this.logsQueryRepository = logsQueryRepository;
        this.objectMapper = objectMapper;
        this.broadcast = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "streaming-broadcast");
            t.setDaemon(true);
            return t;
        });
    }

    /** Hilo Tomcat de la 202: SOLO encola (cero BD, cero envíos, R7.17). */
    @Override
    public void onLotePersistido(long projectId, OffsetDateTime receivedAt,
            List<Long> ids) {
        try {
            broadcast.execute(() -> distribuir(projectId, receivedAt, ids));
        } catch (RuntimeException ex) { // incl. RejectedExecutionException
            // (subclass — el multi-catch con RuntimeException no compila):
            // JAMÁS propaga al seam (R7.18); WARN sin contenido.
            LOG.warn("lote no encolado para broadcast");
        }
    }

    /** Ciclo de vida ANCLADO al bean (R7.18/D-7.2): graceful 5 s. */
    @PreDestroy
    void apagar() {
        broadcast.shutdown();
        try {
            if (!broadcast.awaitTermination(5, TimeUnit.SECONDS)) {
                broadcast.shutdownNow(); // resto descartado — sin replay
            }
        } catch (InterruptedException ex) {
            broadcast.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Tarea del executor, en orden EXACTO (R7.17/D-7.2). */
    private void distribuir(long projectId, OffsetDateTime receivedAt,
            List<Long> ids) {
        // (1) ids null/vacío → nada (defensiva — el seam siempre trae ids)
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // (2) porIds — UNA select por lote (P-8); lista vacía → nada
        List<FilaBatch> filas;
        try {
            filas = logsQueryRepository.porIds(ids);
        } catch (RuntimeException ex) {
            // fallo DEL LOTE → swallow + log ERROR; NO re-emite, NO reintenta
            // (at-least-once YA garantizado por el COMMIT del seam — R-7.i)
            LOG.error("broadcast: fallo materializando el lote");
            return;
        }
        if (filas.isEmpty()) {
            return;
        }
        // (3) batch_id — UNA vez por lote (D-7.4): UUID v4 minúsculas
        String batchId = "b_" + UUID.randomUUID().toString();
        // (4) por cada sesión del snapshot (copia — sin iterar el mapa vivo)
        String frame = renderFrame(projectId, receivedAt, batchId, filas);
        List<ConcurrentWebSocketSessionDecorator> sesiones =
                handler.sesionesVivas();
        for (ConcurrentWebSocketSessionDecorator envuelta : sesiones) {
            Filtros filtros;
            synchronized (envuelta.getAttributes()) {
                filtros = (Filtros) envuelta.getAttributes()
                        .get(LogSocketHandler.ATTR_FILTROS);
            }
            if (!matchea(filtros, projectId, receivedAt, filas)) {
                continue; // sin calificar (null) o lote no matchea → nada
            }
            try {
                envuelta.sendMessage(new TextMessage(frame));
            } catch (SessionLimitExceededException ex) {
                // doble barrera vencida → 1013 (único código por lentitud)
                handler.cerrarPorLentitud(envuelta);
            } catch (IOException ex) {
                // excepción por SESIÓN (no-Limit): swallow + WARN genérico —
                // un cliente malo no tumba a los demás; si la sesión cerró,
                // la limpieza la hace afterConnectionClosed (R-7.c).
                LOG.warn("broadcast: envío fallido a una sesión");
            } catch (RuntimeException ex) {
                LOG.warn("broadcast: envío fallido a una sesión");
            }
        }
        // (5) política de excepciones ya aplicada: nada propaga al hilo
        //     Tomcat (el catch del encolado es la otra capa — D-7.2).
    }

    /** Frame §3.5 byte-exacto: 5 claves EN ORDEN (D-7.3, D-C7-1/D-C7-2). */
    private String renderFrame(long projectId, OffsetDateTime receivedAt,
            String batchId, List<FilaBatch> filas) {
        LinkedHashMap<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "batch");
        frame.put("batch_id", batchId);
        frame.put("project_id", projectId);
        // formatter C2 reutilizado por REFERENCIA — truncado a segundos
        frame.put("received_at",
                receivedAt.format(LoginController.EXPIRES_AT_FORMATTER));
        // LogDto COMPLETO de 11 campos (D-C7-1: prevalece el literal), nulls
        // incluidos, EN EL ORDEN DEL LOTE (reorden de porIds)
        frame.put("logs", filas.stream().map(FilaBatch::log).toList());
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            // imposible por construcción (LinkedHashMap de valores simples);
            // si sucediera, RuntimeException honesta — nunca silenciada
            throw new IllegalStateException("frame batch no serializable", ex);
        }
    }

    /**
     * Conjunción de filtros activos (S7-10/D-7.6): project_id igualdad Long
     * contra el projectId del seam; environment/level igualdad estricta
     * case-sensitive (el lote matchea si ALGÚN registro cumple); from/to
     * INCLUSIVE sobre el instante RAW del común (from == to LEGAL); texto
     * crudo contains case-insensitive Locale.ROOT (el escape ILIKE es SOLO
     * transporte SQL — semántica equivalente EXACTA). Filtro null ⇒ no
     * restringe; sesión SIN filtros (null) ⇒ NO matchea (D-7.7).
     */
    private boolean matchea(Filtros filtros, long projectId,
            OffsetDateTime receivedAt, List<FilaBatch> filas) {
        if (filtros == null) {
            return false; // sin set_filters ⇒ sin frames (S7-10)
        }
        if (filtros.projectId() != null
                && filtros.projectId().longValue() != projectId) {
            return false;
        }
        if (filtros.environment() != null && filas.stream().noneMatch(
                fb -> filtros.environment().equals(fb.log().environment()))) {
            return false;
        }
        if (filtros.level() != null && filas.stream().noneMatch(
                fb -> filtros.level().equals(fb.log().level()))) {
            return false;
        }
        if (filtros.from() != null && receivedAt.isBefore(filtros.from())) {
            return false;
        }
        if (filtros.to() != null && receivedAt.isAfter(filtros.to())) {
            return false;
        }
        if (filtros.texto() != null && filas.stream().noneMatch(
                fb -> fb.log().message() != null && fb.log().message()
                        .toLowerCase(Locale.ROOT).contains(filtros.texto()
                                .toLowerCase(Locale.ROOT)))) {
            return false;
        }
        return true;
    }

}