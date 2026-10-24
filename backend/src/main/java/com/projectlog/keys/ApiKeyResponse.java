package com.projectlog.keys;

/**
 * {@code ApiKeyResponse} (R4.7, design §1.3): record de salida de la 201 del
 * POST {@code …/api-key/regenerate} — claves JSON EXACTAS {@code key,
 * prefix, created_at} en ESE orden (el orden de componentes del record fija
 * el orden de las claves JSON; Jackson serializa los accessors snake_case
 * con SU nombre, sin {@code @JsonProperty} — patrón probado de
 * {@code ProjectDto}/{@code LoginResponse}/{@code MeResponse} de C1/C2).
 *
 * <p>Este record es el ÚNICO lugar donde la key COMPLETA (47 chars) vive
 * como valor transportado (R4.13): nace en {@code ApiKeyService.newKey()},
 * viaja UNA única vez en el cuerpo de la 201 y muere al terminar el request
 * — jamás logueada (E4.14) ni persistida en claro (Solo
 * {@code api_key_hash} en BD).</p>
 *
 * @param key        key completa {@code plk_<43 chars Base64url>} (47 chars)
 * @param prefix     primeros 10 chars ({@code plk_} + 6) — derivado NO secreto
 * @param created_at {@code key_created_at} ya formateado por el repo con el
 *                   formatter público de C2 ({@code yyyy-MM-dd'T'HH:mm:ss'Z'}
 *                   UTC) — llega como {@code String} ISO-Z (cierre S4)
 */
public record ApiKeyResponse(String key, String prefix, String created_at) {
}