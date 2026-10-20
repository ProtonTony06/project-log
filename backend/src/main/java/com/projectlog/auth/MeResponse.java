package com.projectlog.auth;

/**
 * Salida 2xx de /me (N10, CD-6, L1): {username, role, expires_at} — rol
 * literal "ADMIN" (Schema §3.3) y expires_at ISO-8601 UTC con Z, ambos
 * salidos de los claims del token (S18, sin consulta BBDD).
 */
public record MeResponse(String username, String role, String expires_at) {
}