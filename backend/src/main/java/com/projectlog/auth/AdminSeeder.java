package com.projectlog.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seed idempotente del admin inicial (S21–S23, CD-2, gate G4). Semántica
 * literal del Schema §6.3/L5: "si no existe ningún {@code users}, crea el
 * admin con {@code username} = ADMIN_INITIAL_USERNAME y
 * {@code password_hash} = Argon2id(ADMIN_INITIAL_PASSWORD)" — condición
 * GLOBAL de tabla-vacía (COUNT), no por-username: cambiar
 * {@code ADMIN_INITIAL_USERNAME} tras el primer seed NO crea un segundo
 * admin (S22/E2.9).
 *
 * <p>Orden de arranque garantizado por Boot sin configuración extra:
 * validador de C1 (EnvironmentPostProcessor, pre-refresh) → refresh (Flyway
 * aplica V1) → {@code ApplicationRunner}s. No re-valida presencia de
 * credenciales (S23): sin {@code ADMIN_INITIAL_PASSWORD} el validador de C1
 * aborta antes del refresh (E4 idéntico — fichero intocable).</p>
 *
 * <p>Nunca se hashea en SQL ni en Flyway (L5/S24: el hash depende de
 * configuración): V1 queda intacto. El {@code ON CONFLICT} sobre
 * {@code users_username_unique} (nombre literal de V1) es la segunda
 * barrera de idempotencia tras el COUNT (E2.8). Reinicio normal: coste 0
 * (COUNT + 0 hashes — COUNT evita re-hashing, §4.5). {@code role='ADMIN'}
 * EXPLÍCITO en el INSERT (S21/E2.22). Logs sin valores: jamás username ni
 * hash (S16/E2.19).</p>
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final JdbcTemplate jdbcTemplate;

    private final PasswordEncoder passwordEncoder;

    private final Environment env;

    public AdminSeeder(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
            Environment env) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        long filas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users", Long.class);
        if (filas > 0) {
            // G4/S22: tabla no vacía ⇒ no-acción, también si el username del
            // env no existe entre los usuarios (E2.9). Sin error.
            log.info("Seed omitido: la tabla users ya tiene {} fila(s).", filas);
            return;
        }
        String hash = passwordEncoder.encode(
                env.getProperty("ADMIN_INITIAL_PASSWORD"));
        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, role)
                VALUES (?, ?, 'ADMIN')
                ON CONFLICT ON CONSTRAINT users_username_unique DO NOTHING
                """,
                env.getProperty("ADMIN_INITIAL_USERNAME"),
                hash);
        log.info("Admin inicial sembrado (users vacía)."); // sin valores ni hash
    }
}