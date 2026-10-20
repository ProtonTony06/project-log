package com.projectlog.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS mínimo del corte C2 (S25/S26, CD-7, gate G1): un único origen, el
 * valor de {@code FRONTEND_ORIGIN}, mapeado desde
 * {@code projectlog.security.frontend-origin} (application.yml, CD-7). El
 * bean se aplica SOLO en la cadena 2 ({@code /api/v1/panel/**} — G1) porque
 * el {@code securityMatcher} de esa cadena limita el efecto del patrón
 * {@code "/**"} registrado aquí; la cadena 1 (ingesta, TRD §5: "sus clientes
 * son servicios") no tiene {@code cors()} jamás.
 *
 * <p>Si {@code FRONTEND_ORIGIN} falta/vacío (S26) NO se registra ninguna
 * configuración: CORS cerrado por defecto (la misma-origen por proxy de
 * producción no lo necesita ni lo nota — TRD §6.2).</p>
 */
@Configuration
public class CorsConfig {

    /** Origen del frontend; cadena vacía si la variable falta (S26). */
    private final String frontendOrigin;

    public CorsConfig(@Value("${projectlog.security.frontend-origin:}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (frontendOrigin == null || frontendOrigin.isBlank()) {
            // S26/E2.18: cero configuraciones registradas ⇒ ninguna respuesta
            // lleva cabeceras CORS ⇒ CORS cerrado por defecto.
            return source;
        }
        CorsConfiguration config = new CorsConfiguration();
        // Nunca "*", nunca setAllowedOriginPatterns (G1): reflejo EXACTO del origen.
        config.setAllowedOrigins(List.of(frontendOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        // X-Confirm-* reservado a C3 (P1); Authorization/Content-Type para el panel.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Confirm-*"));
        // JWT por header, no cookies ⇒ credenciales fuera (S25).
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        // El securityMatcher de la cadena 2 limita el efecto a /api/v1/panel/**.
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}