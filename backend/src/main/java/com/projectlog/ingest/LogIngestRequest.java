package com.projectlog.ingest;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * C5 (R5.24-a/b, S5-1, S5-2, S5-15): record de ENTRADA cruda de un log — los
 * 8 campos literales del Schema §3.4 en SU orden, con la CLAVE JSON
 * {@code correlation_id} (la columna de V1) y {@code metadata} como
 * {@code JsonNode} CRUDO (nada lo serializa aquí: UNA serialización en el
 * {@code IngestService}, S5-8/R-D3).
 *
 * <p>La factoría {@link #desdeJson(JsonNode)} usa el patrón LITERAL C3
 * {@code UpdateProjectRequest.desdeJson}: {@code valor(nodo, campo)} →
 * {@code null} si el nodo es {@code null}/{@code isNull()}, si no
 * {@code asText()} (coerción Jackson determinista: escalar → su forma texto,
 * contenedor → {@code ""}). SÓLO los 8 nombres literales se leen — cualquier
 * clave desconocida (incl. un {@code project_id}/{@code received_at}
 * falsificado por el cliente) JAMÁS se lee ni persiste (S5-2, CD-3.9). SIN
 * normalización case/trim/vacío→NULL en NINGÚN campo: una cadena vacía
 * presente en los opcionales es VÁLIDA y se persiste vacía (R5.24-a; los
 * enums se validan DESPUÉS sobre el String, case-sensitive — S5-4/S5-6).</p>
 */
public record LogIngestRequest(
        String level,
        String timestamp,
        String message,
        String exception,
        JsonNode metadata,
        String environment,
        String source,
        String correlationId) {

    /** Nombres literales de los 8 campos del Schema §3.4 (los ÚNICOS leídos). */
    public static final String CAMPO_LEVEL = "level";
    public static final String CAMPO_TIMESTAMP = "timestamp";
    public static final String CAMPO_MESSAGE = "message";
    public static final String CAMPO_EXCEPTION = "exception";
    public static final String CAMPO_METADATA = "metadata";
    public static final String CAMPO_ENVIRONMENT = "environment";
    public static final String CAMPO_SOURCE = "source";
    public static final String CAMPO_CORRELATION_ID = "correlation_id";

    /**
     * Factoría única de lectura (patrón C3 literal): el nodo del registro —
     * el propio cuerpo en individual o el elemento i-ésimo del array
     * {@code logs} en batch — materializa el record. {@code nodo} ausente/
     * null → todos los campos null (el service reporta el 400 del primer
     * requerido: {@code environment}).
     */
    public static LogIngestRequest desdeJson(JsonNode nodo) {
        if (nodo == null || nodo.isNull() || nodo.isMissingNode()) {
            return new LogIngestRequest(null, null, null, null, null,
                    null, null, null);
        }
        return new LogIngestRequest(
                valor(nodo, CAMPO_LEVEL),
                valor(nodo, CAMPO_TIMESTAMP),
                valor(nodo, CAMPO_MESSAGE),
                valor(nodo, CAMPO_EXCEPTION),
                nodo.get(CAMPO_METADATA),
                valor(nodo, CAMPO_ENVIRONMENT),
                valor(nodo, CAMPO_SOURCE),
                valor(nodo, CAMPO_CORRELATION_ID));
    }

    /** Valor textual del campo, {@code null} si es JSON null / ausente. */
    private static String valor(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return (valor == null || valor.isNull()) ? null : valor.asText();
    }
}