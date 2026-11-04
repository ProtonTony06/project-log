package com.projectlog.projects.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code ProjectDto} del Schema §3.3 (R3.9/R3.7/R3.8, CD-3.1): record con
 * componentes <b>snake_case literales</b> — Jackson serializa el accessor con
 * SU nombre, así que las claves JSON salen exactamente
 * {@code id, name, description, status, created_at, updated_at,
 * api_key_prefix, has_key} sin {@code @JsonProperty} ni config (patrón
 * probado de {@code LoginResponse}/{@code MeResponse} de C2).
 *
 * <ul>
 *   <li><b>{@code summary} (C10 — D-C10-4, G4-a)</b>: 9.º componente con
 *       {@code @JsonInclude(NON_NULL)} sobre el record — cuando vale
 *       {@code null} la clave queda <b>AUSENTE</b> del JSON (no {@code null}):
 *       {@code GET /projects} sin include, {@code GET /projects/{id}},
 *       POST 201 y PATCH 200 quedan byte-idénticos a C8 (REQ-C10-RESUMEN-2).
 *       Solo la lista pedida con {@code include=summary} lo compone el
 *       controller vía {@link #conResumen} (M3).</li>
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
 *
 * <p><b>R2</b>: el constructor de 8 args (previo a C10) permanece intacto y
 * delega en el canónico con {@code summary = null} — CERO cambios en las
 * llamadas existentes ({@code FILA} del repo, tests de C3–C8).</p>
 */
public record ProjectDto(
        Long id,
        String name,
        String description,
        String status,
        String created_at,
        String updated_at,
        String api_key_prefix,
        boolean has_key,
        @JsonInclude(JsonInclude.Include.NON_NULL) SummaryDto summary) {

    /** R2: constructor comodín PRE-C10 — delega con {@code summary = null}. */
    public ProjectDto(Long id, String name, String description, String status,
            String created_at, String updated_at, String api_key_prefix,
            boolean has_key) {
        this(id, name, description, status, created_at, updated_at,
                api_key_prefix, has_key, null);
    }

    /** C10 (D-C10-4): composición del include de la lista — el controller
     * llama {@code p.conResumen(resumen)}; inyecta {@code summary} sin mutar
     * nada más (records inmutables). */
    public ProjectDto conResumen(SummaryDto summary) {
        return new ProjectDto(id, name, description, status, created_at,
                updated_at, api_key_prefix, has_key, summary);
    }
}