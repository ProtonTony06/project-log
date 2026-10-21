package com.projectlog.projects.dto;

/**
 * {@code ProjectDto} del Schema §3.3 (R3.9/R3.7/R3.8, CD-3.1): record con
 * componentes <b>snake_case literales</b> — Jackson serializa el accessor con
 * SU nombre, así que las claves JSON salen exactamente
 * {@code id, name, description, status, created_at, updated_at,
 * api_key_prefix, has_key} sin {@code @JsonProperty} ni config (patrón
 * probado de {@code LoginResponse}/{@code MeResponse} de C2).
 *
 * <ul>
 *   <li><b>Sin {@code summary}</b> de ninguna manera (es C10 — R3.9).</li>
 *   <li>{@code description} puede ser {@code null} (columna TEXT opcional de
 *       V1).</li>
 *   <li>Las fechas llegan como {@code String} YA formateadas ISO-8601 UTC
 *       con sufijo {@code Z} (el RowMapper del repo las entrega con
 *       {@code LoginController.EXPIRES_AT_FORMATTER} — R3.8, formatter de C2
 *       reutilizado por referencia).</li>
 *   <li>{@code api_key_prefix}: {@code null} siempre en C3 (la creación no
 *       toca la key); {@code has_key} es derivado en SQL
 *       ({@code api_key_hash IS NOT NULL}) — NUNCA columna (Schema §4 regla
 *       11, R3.7).</li>
 * </ul>
 */
public record ProjectDto(
        Long id,
        String name,
        String description,
        String status,
        String created_at,
        String updated_at,
        String api_key_prefix,
        boolean has_key) {
}