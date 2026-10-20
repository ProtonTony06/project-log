package com.projectlog.auth;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro de autenticación del panel (S5, CD-4). Registrado SOLO en la
 * cadena 2 ({@code /api/v1/panel/**}) vía {@code addFilterBefore(...)};
 * no es bean global en la cadena principal, así que no ve el tráfico de las
 * cadenas 1 y 3 (superficie mínima).
 *
 * <p>Flujo (CD-4): (1) con header {@code Authorization: Bearer <jwt>}
 * válido → {@code JwtPrincipal} en el {@code SecurityContext}, autoridades
 * vacías (sin checks por rol — S20/ADR-14). (2) ausente/malformado/
 * inválido/expirado → NO escribe respuesta y NO setea contexto: continúa la
 * cadena vacío, la regla {@code authenticated()} falla y el
 * {@code RestAuthenticationEntryPoint} (único escritor, CD-4) produce el
 * 401 {@code {"code": "invalid_token", ...}}.</p>
 *
 * <p>Higiene (S16/E2.19): el filtro NO loguea el header, el token ni
 * fragmentos — no contiene ningún logger y jamás lee la respuesta.</p>
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Prefijo EXACTO del header (sin trim agresivo del token, CD-4). */
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public JwtAuthFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            // Sin credencial: contexto vacío; el entry point escribirá el 401 (CD-4).
            filterChain.doFilter(request, response);
            return;
        }
        tokenService.verify(header.substring(BEARER_PREFIX.length()))
                .ifPresent(principal -> {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
        filterChain.doFilter(request, response);
    }
}