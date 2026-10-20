package com.projectlog.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Bean {@link PasswordEncoder} Argon2id con parámetros OWASP 2024 (S6, CD-5):
 * salt 16 bytes, hash 32 bytes, paralelismo {@code p=1}, memoria
 * {@code m=19456 KiB}, iteraciones {@code t=2}. Constructor EXPLÍCITO —
 * NO {@code defaultsForSpringSecurity_v5_8} (m=16384 ≠ OWASP 2024, S6).
 *
 * <p>Produce el PHC {@code $argon2id$v=19$m=19456,t=2,p=1$…} que espera el
 * comentario literal de {@code V1__init.sql} ("Hash Argon2id (formato PHC)",
 * L6/S8). El PHC autodescribe sus parámetros: {@code matches(raw, hash)}
 * seguirá ok aunque este bean evolucionara (S8). {@code bcprov-jdk18on}
 * está en scope runtime (issue spring-security #8842 — §4.1) porque el
 * {@code Argon2PasswordEncoder} delega en el generador de BC en runtime.</p>
 */
@Configuration
public class PasswordConfig {

    /** salt(16) · hash(32) · p=1 · m=19456 KiB · t=2 — OWASP 2024 (S6). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }
}