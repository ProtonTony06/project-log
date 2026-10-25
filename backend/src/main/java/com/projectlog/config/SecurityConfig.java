package com.projectlog.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.projectlog.auth.JwtAuthFilter;
import com.projectlog.ingest.IngestAuthenticationEntryPoint;
import com.projectlog.ingest.IngestGuardFilter;

/**
 * Las TRES {@code SecurityFilterChain} del corte C2 (S1–S4, CD-1):
 *
 * <ul>
 *   <li><b>Cadena 1 (@Order(1))</b> — {@code /api/v1/ingest/**}: (C5, R5.6)
 *       ingesta autenticada por {@code X-API-Key}: el guard
 *       {@code IngestGuardFilter} aplica el orden contractual 413→401 (el
 *       lookup B1 vive en el filtro, D-1) y el entry point PROPIO de ingesta
 *       {@code IngestAuthenticationEntryPoint} escribe el 401
 *       {@code api_key_invalid} (Schema §3.4) — JAMÁS el del panel
 *       {@code invalid_token} (CD-4, intocable). Sin {@code cors()} (TRD
 *       §5). El 403 {@code project_archived} es del controlador con key
 *       VÁLIDA (R5.8), no de esta cadena.</li>
 *   <li><b>Cadena 2 (@Order(2))</b> — {@code /api/v1/panel/**}: público SOLO
 *       el login exacto {@code POST /api/v1/panel/auth/login}
 *       (método+ruta literales, S3); todo el resto
 *       {@code authenticated()} — sin {@code hasRole} (ADR-14,S20).
 *       Stateless, CSRF off, CORS con la fuente de {@link CorsConfig},
 *       {@link JwtAuthFilter} delante del filtro estándar; sin
 *       {@code httpBasic} ni {@code formLogin}: el usuario aleatorio
 *       in-memory de Boot queda INERTE (S3, CD-8a).</li>
 *   <li><b>Cadena 3 (@Order(3), sin matcher)</b> — resto: {@code permitAll}
 *       únicamente para {@code /actuator/health}, {@code /actuator/info}
 *       (S30) y {@code /ws/logs} (S31, gate G2 — sin ella el Echo de C1
 *       moriría con 401); todo lo demás {@code denyAll()} (S4): el panel
 *       "protegido por defecto", C3–C6 nacen behind 401 (S19). Sin matcher
 *       porque Spring Security exige una chain incondicional como último
 *       recurso — con matchers exactos en 1 y 2, la 3 cubre lo demás.</li>
 * </ul>
 *
 * <p>Las tres cadenas comparten el MISMO {@link RestAuthenticationEntryPoint}
 * por constructor: un solo escritor del 401 (CD-4) con el literal único
 * {@code invalid_token} (S5).</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Filtro JWT de la cadena 2 (CD-4: registrado SOLO allí). */
    private final JwtAuthFilter jwtAuthFilter;

    /** Único escritor del 401 JSON, compartido por las 3 cadenas (CD-4). */
    private final RestAuthenticationEntryPoint entryPoint;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
            RestAuthenticationEntryPoint entryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.entryPoint = entryPoint;
    }

    /**
     * Cadena 1 (C5): ingesta autenticada por X-API-Key — guard + entry
     * point PROPIOS (api_key_invalid, Schema §3.4); JAMÁS el entry point del
     * panel (invalid_token, CD-4, intocable). Sin cors() (TRD §5).
     *
     * <p>Los 2 parámetros llegan como {@code ObjectProvider} (RATIFICADO en
     * gate 2026-10-25, cierre del bloqueo T5.5): {@code ObjectProvider} es
     * inyectable en CUALQUIER contexto y AQUÍ se resuelve SOLO al hacer
     * request — {@code @WebMvcTest} incluye en su escaneo a los {@code
     * Filter} (el guard existe) pero NO a los {@code
     * AuthenticationEntryPoint} (el entry point de ingesta NO existe en los
     * slices), por lo que el {@code getObject()} a build-time estallaría en
     * los slices de C1–C4: los delegantes lazy de abajo (lambdas) hacen la
     * resolución en EL MOMENTO DEL REQUEST, inexistente en esos tests. En
     * PROD los beans existen y el comportamiento es idéntico. Los asserts
     * de C1–C4 NO cambian.</p>
     */
    @Bean
    @Order(1)
    public SecurityFilterChain ingestChain(HttpSecurity http,
            ObjectProvider<IngestGuardFilter> ingestGuardFilter,
            ObjectProvider<IngestAuthenticationEntryPoint> entryPointIngesta) throws Exception {
        return common(http)
                .securityMatcher("/api/v1/ingest/**")
                // Sin cors() (TRD §5: los clientes de ingesta son servicios).
                .addFilterBefore((request, response, chain) ->
                                ingestGuardFilter.getObject()
                                        .doFilter(request, response, chain),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((request, response, authException) ->
                                entryPointIngesta.getObject()
                                        .commence(request, response, authException))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                entryPointIngesta.getObject()
                                        .handle(request, response, accessDeniedException)))
                .build();
    }

    /**
     * C5 (T5.4/D-8, hallazgo del smoke E2E E5.28): el guard es miembro
     * EXCLUSIVO de la cadena 1 (addFilterBefore) — su auto-registración
     * global como {@code jakarta.servlet.Filter} bean se DESHABILITA aquí.
     *
     * <p>Con la doble registración, el pase global (fuera del
     * FilterChainProxy) envuelve el request y CONSUME el marker de
     * {@code OncePerRequestFilter}: el pase de la cadena 1 queda en SKIP y
     * el dispatcher lee el envoltorio FUERA del try/catch del guard (el
     * skip-path de OncePerRequestFilter no tiene capturador) — la
     * {@code CuerpoDemasiadoGrandeException} de la barrera 2 (chunked,
     * E5.28) escapaba hasta Tomcat → error dispatch "/error" → 403/401 del
     * panel en lugar del 413 {payload_too_large}. Con el guard SOLO en la
     * cadena 1, su try/catch rodea TODO el consumo del cuerpo. La
     * auto-registración de {@code JwtAuthFilter} NO cambia (C2, intocable:
     * allí el skip es inofensivo porque no envuelve ni lanza).</p>
     */
    @Bean
    public FilterRegistrationBean<IngestGuardFilter> filtroGuardSoloCadena1(
            IngestGuardFilter guard) {
        FilterRegistrationBean<IngestGuardFilter> bean =
                new FilterRegistrationBean<>(guard);
        bean.setEnabled(false);
        return bean;
    }

    /** Cadena 2: panel JWT — login público exacto, resto autenticado (S3). */
    @Bean
    @Order(2)
    public SecurityFilterChain panelChain(HttpSecurity http) throws Exception {
        return common(http)
                .securityMatcher("/api/v1/panel/**")
                .cors(cors -> { /* withDefaults(): fuente de CorsConfig (G1) */ })
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Método+ruta EXACTOS: un matcher de solo ruta dejaría
                        // público GET y sub-rutas /auth/login/** (CD-1).
                        .requestMatchers(HttpMethod.POST, "/api/v1/panel/auth/login")
                            .permitAll()
                        .anyRequest().authenticated()) // no hasRole — ADR-14
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
                .build();
    }

    /** Cadena 3: resto — health/info y /ws/logs permitAll, demás denyAll (S4). */
    @Bean
    @Order(3)
    public SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        return common(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info",
                                "/ws/logs").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint))
                .build();
    }

    /**
     * Reglas comunes a las tres cadenas (CD-1): API token-based sin sesión
     * y sin CSRF. Sin UserDetailsService propio: el usuario in-memory de
     * Boot queda inerte (S3, CD-8a — "Using generated security password"
     * no debe aparecer).
     */
    private HttpSecurity common(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}