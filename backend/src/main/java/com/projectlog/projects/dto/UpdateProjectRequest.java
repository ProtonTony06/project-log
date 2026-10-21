package com.projectlog.projects.dto;

import java.util.LinkedHashSet;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Cuerpo de entrada de {@code PATCH /api/v1/panel/projects/{id}} (G2,
 * CD-3.1/CD-3.7): record con <b>presencia explícita</b> — la distinción
 * ausente-vs-{@code null} es la clave del PATCH parcial (G2) y un record
 * plano la pierde, de modo que el controlador lee el {@code JsonNode} y
 * materializa el conjunto de campos PRESENTES.
 *
 * <p>{@link #desdeJson(JsonNode)} marca presencia solo con
 * {@code nodo.has(CAMPO)}, INDEPENDIENTE del valor: {@code {}}
 * → {@code camposPresentes=[]}; {@code {"description": null}} →
 * {@code description == null} PERO {@code camposPresentes.contains(
 * "description")} (únicola limpieza G2); solo las 3 claves literales se
 * leen, cualquier otra se IGNORA (CD-3.9 — sin rechazar campos
 * desconocidos: exigiría un literal sin ancla).</p>
 *
 * <p>Sin dependencias nuevas: {@code jackson-databind} ya viene con
 * {@code starter-web}.</p>
 */
public record UpdateProjectRequest(
        String name,
        String description,
        String status,
        Set<String> camposPresentes) {

    /** Nombres literales de los 3 campos PATCH (compartidos por repo/controller). */
    public static final String CAMPO_NAME = "name";
    public static final String CAMPO_DESCRIPTION = "description";
    public static final String CAMPO_STATUS = "status";

    /**
     * Factoría única de lectura del PATCH (G2): presencia marcada con
     * {@code has(CAMPO)}; el valor de un campo presente-{@code null} es
     * {@code null} ({@code NullNode.asText()} sería el literal {@code "null"},
     * por eso se consulta {@code isNull()}). Un nodo ausente/NULL (
     * {@code null} o cuerpo JSON {@code null}) equivale a {@code {}} — nada
     * presente (G2).
     */
    public static UpdateProjectRequest desdeJson(JsonNode nodo) {
        if (nodo == null || nodo.isNull() || nodo.isMissingNode()) {
            return new UpdateProjectRequest(null, null, null, Set.of());
        }
        Set<String> presentes = new LinkedHashSet<>();
        if (nodo.has(CAMPO_NAME)) {
            presentes.add(CAMPO_NAME);
        }
        if (nodo.has(CAMPO_DESCRIPTION)) {
            presentes.add(CAMPO_DESCRIPTION);
        }
        if (nodo.has(CAMPO_STATUS)) {
            presentes.add(CAMPO_STATUS);
        }
        return new UpdateProjectRequest(
                valor(nodo, CAMPO_NAME),
                valor(nodo, CAMPO_DESCRIPTION),
                valor(nodo, CAMPO_STATUS),
                presentes);
    }

    /** Valor textual del campo presente, {@code null} si es un JSON null. */
    private static String valor(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return (valor == null || valor.isNull()) ? null : valor.asText();
    }
}