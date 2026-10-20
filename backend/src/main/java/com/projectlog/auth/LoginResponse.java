package com.projectlog.auth;

/**
 * Salida 2xx del login (N9, CD-6, L1): {token, username, expires_at}, con
 * {@code expires_at} en ISO-8601 UTC con sufijo Z (Schema §3.1/L2,
 * formatter determinista de CD-6).
 */
public record LoginResponse(String token, String username, String expires_at) {
}