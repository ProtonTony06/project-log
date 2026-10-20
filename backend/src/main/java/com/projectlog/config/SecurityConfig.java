package com.projectlog.config;

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

/**
 * Las TRES {@code SecurityFilterChain} del corte C2 (S1–S4, CD-1):
 *
 * <ul>
 *   <li><b>Cadena 1 (@Order(1))</b> — {@code /api/v1/ingest/**}:
 *       deny-by-default (S2). El mecanismo real {@code X-API-Key} llega en
 *       C5; aquí la cadena solo declara el rechazo con 401 JSON (entry
 *       point compartido). Sin {@code cors()} (TRD §5). El entry point se
 *       registra también como {@code accessDeniedHandler}: nunca 403 HTML
 *       en la superficie declarada.</li>
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

    /** Cadena 1: ingesta declarada deny-by-default (S2; se usa en C5). */
    @Bean
    @Order(1)
    public SecurityFilterChain ingestChain(HttpSecurity http) throws Exception {
        return common(http)
                .securityMatcher("/api/v1/ingest/**")
                // Sin cors() (TRD §5: los clientes de ingesta son servicios).
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint)) // nunca 403 HTML
                .build();
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