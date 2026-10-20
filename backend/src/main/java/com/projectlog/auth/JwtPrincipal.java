package com.projectlog.auth;

import java.time.Instant;

/**
 * Principal que el {@link JwtAuthFilter} deposita en el {@code SecurityContext}
 * (N4/CD-4): los claims verificados del JWT. Es el puente para cualquier
 * controller futuro de panel (C3–C6) y para {@code MeController}: entrega
 * {@code sub}/{@code role}/{@code exp} sin consulta a BBDD (S18).
 */
public record JwtPrincipal(String username, String role, Instant expiresAt) {
}