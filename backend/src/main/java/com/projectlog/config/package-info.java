/**
 * Configuración transversal del backend. Contenido real en C1:
 * {@code WebSocketConfig} (registro nativo del handler en /ws/logs) y
 * {@code StartupSecretsValidator} (fail-fast de secretos, registrado como
 * EnvironmentPostProcessor en META-INF/spring.factories).
 *
 * <p>La configuración de Flyway y de actuator NO son beans: vive como
 * propiedades en application.yml. Seguridad/CORS/límites llegan en C2+.</p>
 */
package com.projectlog.config;