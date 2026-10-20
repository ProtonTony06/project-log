package com.projectlog.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fail-fast de secretos (R5/DD-3, E4): valida en el punto MÁS TEMPRANO del
 * arranque (antes del refresh del contexto: sin DataSource, sin Flyway, sin
 * Tomcat) que las 6 variables exigidas de configuración estén presentes y no
 * en blanco. Con cualquier ausente lanza UN único {@link IllegalStateException}
 * que lista TODOS los problemas de golpe (nombre de la variable + qué se
 * esperaba; nunca un valor ni una lista parcial).
 *
 * <p>Solo chequea presencia de configuración: firma JWT, seed del admin y
 * SecurityFilterChain son C2 y NO se anticipan aquí. No exige ninguna variable
 * con default (JWT_TTL_HOURS, INGEST_MAX_*, WS_HEARTBEAT_SECONDS,
 * LOGGING_LEVEL, SPRING_PROFILES_ACTIVE, TZ, FRONTEND_ORIGIN, FRONTEND_BIND).</p>
 *
 * <p>Registrado en {@code META-INF/spring.factories} con la clave
 * {@code org.springframework.boot.env.EnvironmentPostProcessor} — sin ese
 * fichero este validador NO corre.</p>
 */
public class StartupSecretsValidator implements EnvironmentPostProcessor {

    /** Variables exigidas (R5): nombre → descripción de lo que se esperaba. */
    private static final Map<String, String> REQUERIDAS = Map.of(
            "SPRING_DATASOURCE_URL",
                "URL JDBC del PostgreSQL, p. ej. jdbc:postgresql://postgres:5432/project_log",
            "SPRING_DATASOURCE_USERNAME",
                "usuario de la BBDD (el de POSTGRES_USER)",
            "SPRING_DATASOURCE_PASSWORD",
                "contraseña de la BBDD (la de POSTGRES_PASSWORD)",
            "JWT_SECRET",
                "clave de firma JWT, ≥ 32 bytes aleatorios (generar: openssl rand -base64 48)",
            "ADMIN_INITIAL_USERNAME",
                "nombre de usuario del admin inicial (seed idempotente en C2)",
            "ADMIN_INITIAL_PASSWORD",
                "contraseña del admin inicial, sin default (seed idempotente en C2)");

    /** Longitud mínima de JWT_SECRET: ≥ 32 bytes UTF-8 (R5, literal). */
    private static final int JWT_SECRET_MIN_BYTES = 32;

    public StartupSecretsValidator() {
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
            SpringApplication application) {
        List<String> fallos = new ArrayList<>();
        for (Map.Entry<String, String> requerida : REQUERIDAS.entrySet()) {
            String nombre = requerida.getKey();
            String valor = environment.getProperty(nombre);
            if (esBlanco(valor)) {
                fallos.add("Falta la variable de configuración " + nombre
                        + " (" + requerida.getValue() + ")");
            } else if ("JWT_SECRET".equals(nombre)
                    && valor.getBytes(StandardCharsets.UTF_8).length < JWT_SECRET_MIN_BYTES) {
                fallos.add(nombre + " debe medir al menos " + JWT_SECRET_MIN_BYTES
                        + " bytes (UTF-8); mide "
                        + valor.getBytes(StandardCharsets.UTF_8).length);
            }
        }
        if (!fallos.isEmpty()) {
            throw new IllegalStateException(
                    "Arranque del backend abortado (fail-fast de secretos): faltan "
                            + fallos.size() + " de las " + REQUERIDAS.size()
                            + " variables exigidas. Ficha .env de la pila:\n - "
                            + String.join("\n - ", fallos));
        }
    }

    private static boolean esBlanco(String valor) {
        return valor == null || valor.isBlank();
    }
}