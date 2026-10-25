package com.projectlog.query.dto;

/**
 * {@code LogListItemDto} del Schema §3.3 (R6.9, P5/D1 del gate): record con
 * EXACTAMENTE 9 componentes y los NOMBRES LITERALES de §3.3
 * ({@code project_id}, {@code correlation_id}) — Jackson serializa el
 * accessor con SU nombre sin {@code @JsonProperty} (patrón
 * {@code ProjectDto}). La fuga de {@code exception}/{@code metadata} en la
 * LISTA queda ESTRUCTURALMENTE IMPOSIBLE de compilar (no existe el campo —
 * protección por compilar, no por disciplina; TRD §2.4: no se transportan).
 *
 * <p>Las fechas van como {@code String} YA renderizadas con el formatter de
 * C2 REUTILIZADO por referencia ({@code LoginController.EXPIRES_AT_FORMATTER},
 * truncado a segundos — R6.13); la precisión COMPLETA del instante es SOLO
 * del cursor ({@code CursorCodec}, P1). El RowMapper del repo formatea; el
 * controller nunca toca fechas.</p>
 */
public record LogListItemDto(
        Long id,
        Long project_id,
        String level,
        String environment,
        String timestamp,
        String received_at,
        String message,
        String source,
        String correlation_id) {
}