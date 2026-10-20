package com.projectlog.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * E2.18 [L] (CD-9): unit sobre {@link CorsConfig} — sin FRONTEND_ORIGIN no
 * se registra NINGÚN origen (CORS cerrado por defecto, S26); con origen
 * seteado se refleja EXACTO (nunca "*", nunca allowedOriginPatterns), sin
 * credenciales, con los headers/métodos exactos de S25 (G1).
 */
class CorsConfigTest {

    @Test
    void conOrigenVacioNoSeRegistraNingunaConfiguracion() {
        CorsConfigurationSource source = new CorsConfig("").corsConfigurationSource();
        UrlBasedCorsConfigurationSource urlSource =
                (UrlBasedCorsConfigurationSource) source;
        assertThat(urlSource.getCorsConfigurations()).isEmpty();
        // Cualquier petición (panel o no) recibe null ⇒ sin cabeceras CORS.
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS",
                "/api/v1/panel/auth/login");
        request.addHeader("Origin", "http://atacante.example");
        request.addHeader("Access-Control-Request-Method", "POST");
        assertThat(source.getCorsConfiguration(request)).isNull();
    }

    @Test
    void conOrigenSeteadoSeReflejaExactamenteYSinComodines() {
        CorsConfiguration source = configDeOrigenSeteado();
        assertThat(source.getAllowedOrigins())
                .containsExactly("http://panel.example:5173");
        assertThat(source.getAllowedOriginPatterns()).isNullOrEmpty(); // nunca comodines (G1)
    }

    @Test
    void conOrigenSeteadoLosHeadersMetodosYCredencialesSonLosDeS25() {
        CorsConfiguration source = configDeOrigenSeteado();
        assertThat(source.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PATCH", "DELETE", "OPTIONS");
        assertThat(source.getAllowedHeaders())
                .containsExactlyInAnyOrder("Authorization", "Content-Type", "X-Confirm-*");
        assertThat(source.getAllowCredentials()).isFalse();
        assertThat(source.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void laConfiguracionRegistradaAplicaAlPathDePanel() {
        UrlBasedCorsConfigurationSource urlSource =
                (UrlBasedCorsConfigurationSource) new CorsConfig(
                        "http://panel.example:5173").corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/panel/me");
        request.addHeader("Origin", "http://panel.example:5173");
        CorsConfiguration cfg = urlSource.getCorsConfiguration(request);
        assertThat(cfg).isNotNull();
        assertThat(cfg.getAllowedOrigins()).containsExactly("http://panel.example:5173");
    }

    private CorsConfiguration configDeOrigenSeteado() {
        UrlBasedCorsConfigurationSource urlSource =
                (UrlBasedCorsConfigurationSource) new CorsConfig(
                        "http://panel.example:5173").corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/panel/me");
        request.addHeader("Origin", "http://panel.example:5173");
        return urlSource.getCorsConfiguration(request);
    }
}