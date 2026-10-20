package com.projectlog.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/panel/me (N7, CD-6, S18/L1) — protegida por la cadena 2 con
 * JWT. Responde 200 {@code {username, role, expires_at}} con {@code role} y
 * {@code expires_at} salidos de los claims del token (S18): SIN consulta
 * BBDD — no inyecta {@code JdbcTemplate}. Si el admin cambia de password,
 * el token sigue válido hasta {@code exp} (Schema §5, cierre local — S20).
 *
 * <p>{@code expires_at} usa el MISMO formatter UTC del login
 * ({@code LoginController.EXPIRES_AT_FORMATTER}) — idéntico formato con
 * sufijo Z (E2.5). Sin logging (S16).</p>
 */
@RestController
public class MeController {

    @GetMapping("/api/v1/panel/me")
    public MeResponse me() {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return new MeResponse(
                principal.username(),
                principal.role(),
                LoginController.EXPIRES_AT_FORMATTER.format(principal.expiresAt()));
    }
}