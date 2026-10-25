package com.projectlog.ingest;

import java.time.OffsetDateTime;

/**
 * C5 (S5-15, S5-8, R5.11-d): record MONEDA del INSERT — nace en
 * {@code IngestService} tras validar el registro COMPLETO (regla 6). El
 * {@code timestamp} ya viene parseado ({@code OffsetDateTime}, D-11 — viaja
 * al INSERT tal cual: el driver lo manda como TIMESTAMPTZ y PG normaliza a
 * UTC al guardar, TRD §2.6) y {@code metadataJson} ya serializado
 * (UNA {@code writeValueAsString}, su String alimenta medida Y parámetro
 * {@code CAST(? AS jsonb)} — R-D3; {@code null} si no hay metadata).
 *
 * <p>Es lo ÚNICO que {@code LogsRepository} recibe: el repo NO valida, NO
 * parsea, NO serializa (frontera de capas).</p>
 */
public record LogValidado(
        String level,
        String environment,
        OffsetDateTime timestamp,
        String message,
        String exception,
        String metadataJson,
        String source,
        String correlationId) {
}