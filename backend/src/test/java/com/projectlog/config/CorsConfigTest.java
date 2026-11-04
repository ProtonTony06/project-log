package com.projectlog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.projectlog.auth.JwtAuthFilter;
import com.projectlog.auth.TokenService;
import com.projectlog.config.RestAuthenticationEntryPoint;
import com.projectlog.config.SecurityConfig;
import com.projectlog.projects.controller.ProjectsController;
import com.projectlog.projects.repository.ProjectsRepository;
import com.projectlog.query.repository.LogsBorradoRepository;
import com.projectlog.query.repository.LogsQueryRepository;

/**
 * E2.18 [L] (CD-9): unit sobre {@link CorsConfig} — sin FRONTEND_ORIGIN no
 * se registra NINGÚN origen (CORS cerrado por defecto, S26); con origen
 * seteado se refleja EXACTO (nunca "*", nunca allowedOriginPatterns), sin
 * credenciales, con los headers/métodos exactos de S25 (G1).
 *
 * <p>C10 (D-C10-15, REQ-C10-CORS-3/REQ-C10-QA): además del pin de
 * CONFIGURACIÓN de la lista (arriba — ahora con los literales exactos
 * {@code X-Confirm-Name} / {@code X-Confirm-Log-Id}, porque el
 * {@code DefaultCorsProcessor} de Spring matchea los
 * {@code Access-Control-Request-Headers} del preflight por IGUALDAD), un
 * slice {@code @Nested} pin de PROFUNDIDAD el COMPORTAMIENTO: preflight REAL
 * de extremo a extremo por la cadena 2 (CorsFilter → DefaultCorsProcessor),
 * la capa que decidió el 403 «Invalid CORS request» que pilló E10.15 en la
 * pila viva. La UI es same-origin por proxy (jamás preflightea), de ahí que
 * solo el arnés visual lo viera: este test fija el comportamiento LATENTE
 * (el caso a+ de la lista G1-a).</p>
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
        // Literales EXACTOS, no «X-Confirm-*»: el DefaultCorsProcessor matchea
        // los Access-Control-Request-Headers por IGUALDAD (D-C10-15) — ver el
        // pin de PROFUNDIDAD del preflight real más abajo.
        assertThat(source.getAllowedHeaders())
                .containsExactlyInAnyOrder("Authorization", "Content-Type",
                        "X-Confirm-Name", "X-Confirm-Log-Id");
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

    // ==================================================================
    // C10 (T10.11 — G1-a latente, D-C10-6, REQ-C10-CORS-1..4): lista
    // separada por coma de la MISMA env var — split + trim + filtrado
    // ==================================================================

    @Test
    void unaUnicaOriginProduceListaIdenticaAlCanonC9SinCambioObservable() {
        // REQ-C10-CORS-2 (latente): con una sola origin el comportamiento es
        // byte-idéntico al de C9 — lista de igual contenido (D-C10-6).
        UrlBasedCorsConfigurationSource urlSource =
                (UrlBasedCorsConfigurationSource) new CorsConfig(
                        "http://localhost:8080").corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/panel/me");
        request.addHeader("Origin", "http://localhost:8080");
        CorsConfiguration cfg = urlSource.getCorsConfiguration(request);
        assertThat(cfg).isNotNull();
        assertThat(cfg.getAllowedOrigins())
                .containsExactly("http://localhost:8080");
        assertThat(cfg.getAllowedOriginPatterns()).isNullOrEmpty(); // G1-C2
    }

    @Test
    void dosOrigensPorComaReflejaAmbasExactas() {
        // REQ-C10-CORS-1: ambas origins reflejadas EXACTAS, en el orden dado
        CorsConfiguration cfg = configDe(
                "http://localhost:8080,http://192.168.1.58:8080");
        assertThat(cfg.getAllowedOrigins()).containsExactly(
                "http://localhost:8080", "http://192.168.1.58:8080");
        assertThat(cfg.getAllowedOriginPatterns()).isNullOrEmpty();
    }

    @Test
    void espaciosYVaciosIntermediosSeTrimmeanYFiltran() {
        // D-C10-6: trim + filtrado de vacíos — `a, b , ,c` → [a, b, c]
        CorsConfiguration cfg = configDe("a, b , ,c");
        assertThat(cfg.getAllowedOrigins()).containsExactly("a", "b", "c");
    }

    @Test
    void comaSolitariaOVacioCierraCorsPorDefectoS26() {
        // S26/E2.18 ampliado (D-C10-6): guard origins.isEmpty() — una ","
        // solitaria tras split+trim+filtrado ⇒ CERO configuraciones (cierre);
        // idéntico al vacío y al ausente.
        assertThat(configuracionesDe(",")).isEmpty();
        assertThat(configuracionesDe(" ")).isEmpty();
        assertThat(configuracionesDe("")).isEmpty();
        // y cualquier petición recibe null ⇒ sin cabeceras CORS
        UrlBasedCorsConfigurationSource urlSource =
                (UrlBasedCorsConfigurationSource) new CorsConfig(
                        ",").corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS",
                "/api/v1/panel/projects");
        request.addHeader("Origin", "http://atacante.example");
        request.addHeader("Access-Control-Request-Method", "DELETE");
        assertThat(urlSource.getCorsConfiguration(request)).isNull();
    }

    @Test
    void originNoListadaEnListaNoRecibeCabecerasCors() {
        // REQ-C10-CORS-4: Origin no listada → sin cabeceras CORS (rechazo)
        UrlBasedCorsConfigurationSource urlSource =
                (UrlBasedCorsConfigurationSource) new CorsConfig(
                        "http://localhost:8080").corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/panel/projects");
        request.addHeader("Origin", "http://otra-cosa:8080");
        CorsConfiguration cfg = urlSource.getCorsConfiguration(request);
        if (cfg != null) {
            // la config REGISTRADA no lista ese origen: su lista no lo contiene
            assertThat(cfg.getAllowedOrigins())
                    .doesNotContain("http://otra-cosa:8080");
            assertThat(cfg.checkOrigin("http://otra-cosa:8080")).isNull();
        }
        assertThat(new CorsConfig("http://localhost:8080")
                .corsConfigurationSource().getCorsConfiguration(request))
                .isNotNull();
    }

    private CorsConfiguration configDe(String frontendOrigin) {
        UrlBasedCorsConfigurationSource urlSource =
                (UrlBasedCorsConfigurationSource) new CorsConfig(
                        frontendOrigin).corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/panel/me");
        request.addHeader("Origin", "http://localhost:8080");
        return urlSource.getCorsConfiguration(request);
    }

    private java.util.Map<String, CorsConfiguration> configuracionesDe(
            String frontendOrigin) {
        UrlBasedCorsConfigurationSource urlSource =
                (UrlBasedCorsConfigurationSource) new CorsConfig(
                        frontendOrigin).corsConfigurationSource();
        return urlSource.getCorsConfigurations();
    }

    // ==================================================================
    // C10 (D-C10-15 — pin de PROFUNDIDAD, REQ-C10-CORS-3 + REQ-C10-QA):
    // preflight REAL de extremo a extremo por la cadena 2 (CorsFilter →
    // DefaultCorsProcessor) — la capa que decide y que el 403 «Invalid
    // CORS request» de E10.15 expuso. Réplica del fixture de
    // ProjectsControllerMvcTest (cadena 2 + application-test.yml), con
    // FRONTEND_ORIGIN = http://localhost:8080 (la origin con la que el
    // arnés visual sondeó la pila viva).
    // ==================================================================

    @Nested
    @ActiveProfiles("test")
    @WebMvcTest(controllers = ProjectsController.class,
            properties = "projectlog.security.frontend-origin=http://localhost:8080")
    @Import({SecurityConfig.class, JwtAuthFilter.class,
            RestAuthenticationEntryPoint.class, CorsConfig.class})
    class PreflightRealDeExtremoAExtremo {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean ProjectsRepository projectsRepository;
        @MockitoBean LogsQueryRepository logsQueryRepository;
        @MockitoBean LogsBorradoRepository logsBorradoRepository;
        @MockitoBean TokenService tokenService;

        @Test
        void preflightDelDeleteConXConfirmNameSeAdmiteCon200() throws Exception {
            // El preflight REAL de E10.15 contra la pila viva, ahora en test:
            // OPTIONS sobre el mapping real de listar, Origin de la lista +
            // ACR-Method DELETE + ACR-Headers x-confirm-name (pedida en
            // minúsculas, como la manda el browser) ⇒ 200 con ACAO reflejada
            // EXACTA, ACAM con DELETE y ACAH admitiendo la cabecera (el
            // DefaultCorsProcessor matchea por IGUALDAD contra los literales
            // EXACTOS de D-C10-15).
            mockMvc.perform(options("/api/v1/panel/projects")
                            .header(HttpHeaders.ORIGIN, "http://localhost:8080")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                    "DELETE")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                    "x-confirm-name"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                            "http://localhost:8080"))
                    .andExpect(header().string(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                            containsString("DELETE")))
                    .andExpect(header().string(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                            containsString("x-confirm-name")));
        }

        @Test
        void preflightConOriginAjenaSeRechazaCon403YSinCabecerasCors() throws Exception {
            // Pieza espejo del arnés (origin-ajena(403)=True): la misma
            // petición con Origin NO listada ⇒ 403 «Invalid CORS request» del
            // DefaultCorsProcessor y SIN cabeceras CORS de respuesta.
            mockMvc.perform(options("/api/v1/panel/projects")
                            .header(HttpHeaders.ORIGIN, "http://atacante.example")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                    "DELETE")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                    "x-confirm-name"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                    .andExpect(header().doesNotExist(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
        }
    }
}