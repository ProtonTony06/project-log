package com.projectlog.query.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@code LogDto} del Schema §3.3 (R6.9/R6.10, P5/D1): los 9 campos del
 * listado MÁS {@code exception} ({@code String}, o {@code null}) y
 * {@code metadata} (objeto JSON o {@code null}) — 11 componentes EXACTOS.
 * {@code exception} y {@code metadata} nullable pero SIEMPRE serializados
 * (Jackson default: nulls incluidos — {@code application.yml} SIN config
 * Jackson; el objeto EXACTO del jsonb sale tal cual).
 *
 * <p>Cierre D-S6-c: el boceto {@code Object metadata /*JsonNode&#42;/} del
 * R6.9 se cierra como {@link JsonNode} por el R6.10 (D-6.9) — el pin E6.2
 * pina el tipo. El RowMapper lo obtiene con {@code rs.getString("metadata")}
 * → {@code objectMapper.readTree(...)}: NUNCA Map, NUNCA el String crudo
 * (R6.10/D-6.5 — objeto JSON EXACTO sin re-interpretación).</p>
 */
public record LogDto(
        Long id,
        Long project_id,
        String level,
        String environment,
        String timestamp,
        String received_at,
        String message,
        String exception,
        JsonNode metadata,
        String source,
        String correlation_id) {
}