package com.projectlog.query.dto;

import java.util.List;

/**
 * {@code LogsPaginaResponse} del Schema §3.3 (R6.11): wrapper del listado
 * keyset con {@code items} y {@code next_cursor}. La clave
 * {@code next_cursor} DEBE aparecer EXPLÍCITA con {@code null} cuando es fin
 * de resultados (NO omitirse — literal §3.3: "`next_cursor: null` = última
 * página"; el toggle "Cargar más" del AppFlow flujo 4 paso 4 la consume):
 * Jackson la emite SIN configuración alguna (default: nulls incluidos —
 * {@code application.yml} INTOCABLE SIN config Jackson).
 */
public record LogsPaginaResponse(
        List<LogListItemDto> items,
        String next_cursor) {
}