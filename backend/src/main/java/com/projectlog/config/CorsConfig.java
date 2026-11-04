package com.projectlog.config;

import java.util.Arrays;
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
 * <p>C10 (G1-a LATENTE, D-C10-6): {@code FRONTEND_ORIGIN} acepta LISTA
 * separada por coma (split + trim + filtrado de vacíos sobre la MISMA env
 * var — sin env nueva); cada origin viaja REFLEJADA EXACTA en
 * {@code setAllowedOrigins}. Sin "*" y SIN {@code setAllowedOriginPatterns}
 * (G1-C2 vigente). Con una sola origin el comportamiento es byte-idéntico al
 * C9 («sin cambio observable» — la activación del segundo origin es un paso
 * de deploy futuro PENDIENTE DE RATIFICACIÓN; la VM 108 y su {@code .env}
 * NO se tocan en este corte, REQ-C10-CORS-5).</p>
 *
 * <p>Si {@code FRONTEND_ORIGIN} falta/vacío/solo comas (S26) NO se registra
 * ninguna configuración: CORS cerrado por defecto (la misma-origen por proxy
 * de producción no lo necesita ni lo nota — TRD §6.2).</p>
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
        // G1-a (latente): FRONTEND_ORIGIN acepta lista separada por coma. Sin
        // "*", sin setAllowedOriginPatterns (G1-C2): reflejo EXACTO de cada origin.
        List<String> origins = Arrays.stream(frontendOrigin.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            // S26/E2.18: cero configuraciones ⇒ CORS cerrado por defecto
            // (incluida una "," solitaria tras split+trim+filtrado).
            return source;
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        // X-Confirm-Name / X-Confirm-Log-Id reservados a C3 (P1) — LITERALES
        // EXACTOS (D-C10-15): el DefaultCorsProcessor de Spring valida los
        // Access-Control-Request-Headers del preflight por IGUALDAD contra esta
        // lista (solo el "*" completo es wildcard a nivel HTTP), de modo que un
        // sufijo comodín «X-Confirm-*» jamás admite «x-confirm-name» y el
        // preflight real devolvía 403 «Invalid CORS request» (E10.15),
        // contradiciendo el COMPORTAMIENTO que exige design REQ-C10-CORS-3
        // (design L146/L238). Authorization/Content-Type para el panel.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type",
                "X-Confirm-Name", "X-Confirm-Log-Id"));
        // JWT por header, no cookies ⇒ credenciales fuera (S25).
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        // El securityMatcher de la cadena 2 limita el efecto a /api/v1/panel/**.
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}