package com.projectlog.projects.dto;

import java.util.Map;

/**
 * {@code SummaryDto} del Schema §3.3 (C10 — D-C10-4, G4-a): record con
 * componentes <b>snake_case literales</b> — las claves JSON salgan EXACTAS
 * {@code by_level, by_environment, total, last_log_at} por el patrón probado
 * de {@code ProjectDto} (accessor con SU nombre, sin {@code @JsonProperty}).
 *
 * <p>Semántica de flujo 9 (REQ-C10-RESUMEN-4): los mapas contienen SOLO los
 * niveles/entornos PRESENTES (sin claves a 0); {@code total} es el SUMAR de
 * logs del proyecto (independiente de filtros/paginación); {@code last_log_at}
 * es el máximo {@code received_at} YA formateado ISO-8601 UTC con sufijo
 * {@code Z} (el formatter de C2 lo aplica el repo, mismo patrón del
 * RowMapper de {@code ProjectsRepository}).</p>
 *
 * <p>A DIFERENCIA de {@code ProjectDto.summary} (NON_NULL), este record NO
 * lleva {@code @JsonInclude}: sus 4 claves viajan SIEMPRE — con proyecto sin
 * logs la serialización EXACTA es
 * {@code {"by_level": {}, "by_environment": {}, "total": 0, "last_log_at":
 * null}} (null EXPLÍCITO de {@code last_log_at}, literal §3.3/flujo 9).</p>
 */
public record SummaryDto(Map<String, Long> by_level,
                         Map<String, Long> by_environment,
                         long total,
                         String last_log_at) {
}