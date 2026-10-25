package com.projectlog.ingest;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Seam C7 (frame batch): invocado EXCLUSIVAMENTE tras el COMMIT del
 * lote persistido (estructura: la única @Transactional del corte está en
 * LogsRepository.insert; este hook es la sentencia siguiente de un
 * método de servicio NO transaccional — D-4). JAMÁS invocado ante
 * 401/403/400/parse ni rollback. C5 NO implementa streaming (sin frames,
 * sin handler, sin LiveBroadcaster). C5 no captura excepciones del
 * suscriptor: la fila ya está en COMMIT — at-least-once (D-12).
 */
public interface IngestListener {

    void onLotePersistido(long projectId, OffsetDateTime receivedAt,
            List<Long> ids);
}