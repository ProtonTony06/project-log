package com.projectlog.query;

import java.time.OffsetDateTime;

/**
 * C6 (S-3 de C4 se CIERRA aquí — P3 del gate; §3.3 verbatim: "«text» se
 * recibe SIN comodines y se busca como %&lt;text&gt;% (escapando %, _ y \)":
 * el cliente NUNCA aporta comodines; el escape es server-side CONTRATO).
 *
 * <p>ORDEN OBLIGATORIO: {@code \} primero, luego {@code %}, luego
 * {@code _} (escapar %{@code %}/{@code _} antes que {@code \} DOBLARÍA las
 * barras recién insertadas — E6.13 lo pina). El {@code \} es el escape
 * DEFAULT de LIKE/ILIKE en PG (el congelado no declara {@code ESCAPE ''} —
 * S6-5: el estamento NO lleva la palabra ESCAPE); el resultado va YA
 * envuelto {@code %...%} y el parámetro del estamento llega ya
 * escapado+envuelto (S6-5).</p>
 *
 * <p>La fase 1 del contrato (R6.20): el controller JAMÁS llama a esta
 * función con {@code null} — {@code text} AUSENTE es "filtro ausente" y viaja
 * DIRECTO al repo como parámetro patrón {@code null} (la rama
 * {@code CAST(? AS text) IS NULL} del estamento único lo absorbe); el vacío
 * LEGAL llega como {@code ""} y la fórmula (sin case especial) produce
 * naturalmente {@code "%%"} — matchea todos los messages (S6-2). El texto NO
 * se trimma NI se normaliza ni lleva cap de longitud (D-6.10/S6-10).</p>
 *
 * <p>Función pura: sin BD, sin Spring (unit E6.13). Seam C7: la misma
 * semántica de filtros del stream (§3.5) la reutiliza.</p>
 */
public final class FiltrosConsulta {

    private FiltrosConsulta() {
    }

    // — Constants mudadas del controller (C7/D-7.12 — única fuente ADR-7).
    // NOMBRES IGUALES y LITERALES byte-idénticos a los de C6; package-private
    // (el controller deja de declararlas en T7.2 y ya no las lee de aquí:
    // consume los campos de FiltroInvalido). —
    static final String CODE_FILTER_FROM_TO_INVERTED = "filter_from_to_inverted";
    static final String CODE_FILTER_INVALID_VALUE = "filter_invalid_value";

    static final String MSG_FROM_TO_INVERTED =
            "El rango temporal no es válido: from es posterior a to";
    static final String MSG_ENVIRONMENT_INVALID =
            "Valor de environment no válido; use development, staging o production";
    static final String MSG_LEVEL_INVALID =
            "Valor de level no válido; use TRACE, DEBUG, INFO, WARN, ERROR o FATAL";
    static final String MSG_PROJECT_ID_INVALID =
            "El filtro project_id debe ser un número";
    static final String MSG_FROM_INVALID =
            "El filtro from debe ser una fecha ISO-8601 con zona horaria";
    static final String MSG_TO_INVALID =
            "El filtro to debe ser una fecha ISO-8601 con zona horaria";

    /**
     * Construye el patrón ILIKE escapado y envuelto: (1) vacío →
     * {@code "%%"} (S6-2, sin case especial); (2) {@code \} → {@code \\}
     * PRIMERO; (3) {@code %} → {@code \%}; (4) {@code _} → {@code \_};
     * (5) envolver {@code %...%}.
     *
     * @param texto texto del cliente (JAMÁS null — ver precondición arriba)
     * @return patrón {@code %<texto-escapado>%} listo para el estamento
     */
    public static String patronIlike(String texto) {
        String escapado = texto.replace("\\", "\\\\")          // \  primero
                .replace("%", "\\%")                            // %  luego
                .replace("_", "\\_");                           // _  después
        return "%" + escapado + "%";
    }

    /**
     * C7 (P-8): ÚNICA fuente de la semántica de filtros ADR-7 — la usan
     * GET /logs (checks 2–7) y set_filters del stream (SIN limit/cursor:
     * no hay paginación en el stream). MISMOS checks, MISMO orden (checks
     * 2→7: project_id → environment → level → from → to → from&gt;to),
     * primer-fallo-gana, MISMAS parejas code/message/field (tabla R6.22 de
     * C6). PatronIlike (byte-a-byte) genera patronTexto. Devuelve TAMBIÉN el
     * texto CRUDO: el matching del stream lo necesita sin escape (S7-10).
     * El texto NO se trimma.
     *
     * @param projectId   query param project_id (null = filtro ausente)
     * @param environment query param environment (null = ausente; "" inválido)
     * @param level       query param level (null = ausente; "" inválido)
     * @param from        query param from (null = ausente; naive rechazado)
     * @param to          query param to (null = ausente; naive rechazado)
     * @param text        query param text (null = ausente; "" LEGAL → "%%")
     * @return Filtros tipados (projectId Long, fechas parseadas, texto crudo
     *         y patrón ILIKE separados)
     * @throws FiltroInvalido primer check fallido (2→7), runtime
     */
    public static Filtros parsear(String projectId, String environment,
            String level, String from, String to, String text) {
        // — check 2: project_id presente no-numérico → filter_invalid_value —
        Long projectIdNum = null;
        if (projectId != null) {
            try {
                projectIdNum = Long.parseLong(projectId);
            } catch (NumberFormatException ex) {
                throw new FiltroInvalido(CODE_FILTER_INVALID_VALUE,
                        MSG_PROJECT_ID_INVALID, "project_id");
            }
        }

        // — check 3: environment fuera del enum cerrado ("" incluido;
        //   case-sensitive, sin normalizar) → filter_invalid_value —
        if (environment != null && !"development".equals(environment)
                && !"staging".equals(environment)
                && !"production".equals(environment)) {
            throw new FiltroInvalido(CODE_FILTER_INVALID_VALUE,
                    MSG_ENVIRONMENT_INVALID, "environment");
        }

        // — check 4: level idem —
        if (level != null && !"TRACE".equals(level) && !"DEBUG".equals(level)
                && !"INFO".equals(level) && !"WARN".equals(level)
                && !"ERROR".equals(level) && !"FATAL".equals(level)) {
            throw new FiltroInvalido(CODE_FILTER_INVALID_VALUE,
                    MSG_LEVEL_INVALID, "level");
        }

        // — checks 5 y 6: from/to presentes NO parseables (incl. naive sin
        //   offset — patrón S5-5 de C5: OffsetDateTime.parse exige offset) —
        OffsetDateTime desde = null;
        if (from != null) {
            try {
                desde = OffsetDateTime.parse(from);
            } catch (Exception ex) { // DateTimeParseException
                throw new FiltroInvalido(CODE_FILTER_INVALID_VALUE,
                        MSG_FROM_INVALID, "from");
            }
        }
        OffsetDateTime hasta = null;
        if (to != null) {
            try {
                hasta = OffsetDateTime.parse(to);
            } catch (Exception ex) { // DateTimeParseException
                throw new FiltroInvalido(CODE_FILTER_INVALID_VALUE,
                        MSG_TO_INVALID, "to");
            }
        }

        // — check 7: from > to (ambos parseados, sobre los Instants;
        //   from == to es LEGAL — rango de punto) → filter_from_to_inverted
        //   SIN field (R6.22) —
        if (desde != null && hasta != null
                && desde.toInstant().isAfter(hasta.toInstant())) {
            throw new FiltroInvalido(CODE_FILTER_FROM_TO_INVERTED,
                    MSG_FROM_TO_INVERTED, null);
        }

        // — texto crudo y patrón (patronIlike byte-a-byte reutilizado) —
        return new Filtros(projectIdNum, environment, level, desde, hasta,
                text, text == null ? null : patronIlike(text));
    }

    /** Filtros tipados del corte (record PÚBLICO — C7/R7.27). */
    public record Filtros(Long projectId, String environment, String level,
            OffsetDateTime from, OffsetDateTime to, String texto,
            String patronTexto) {
    }

    /**
     * Única excepción del parser: code/message/field EXACTOS de la tabla
     * R6.22 de C6 (el field es null en {@code filter_from_to_inverted} —
     * R6.22; el helper 400 del controller jamás emite la clave con null).
     */
    public static final class FiltroInvalido extends RuntimeException {

        public final String code;
        public final String message;
        public final String field;

        FiltroInvalido(String code, String message, String field) {
            super(message);
            this.code = code;
            this.message = message;
            this.field = field;
        }
    }
}