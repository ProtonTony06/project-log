package com.projectlog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * E2.20 [L] (CD-9): unit puro SIN Spring — el PHC producido por
 * {@code Argon2PasswordEncoder(16, 32, 1, 19456, 2)} (los parámetros OWASP
 * 2024 del bean de C2) es {@code $argon2id$v=19$m=19456,t=2,p=1$…}, dos
 * encode() producen hashes diferentes (salt aleatorio) y {@code matches}
 * distingue raw correcto/incorrecto. Si aquí apareciera
 * {@code NoClassDefFoundError} de Bouncy Castle, es el issue spring-security
 * #8842 — corregir el scope runtime de {@code bcprov-jdk18on} ANTES de
 * seguir (S7, advertencia §4.1).
 */
class Argon2PasswordTest {

    /** salt 16 · hash 32 · p=1 · m=19456 KiB · t=2 — OWASP 2024 (S6). */
    private final Argon2PasswordEncoder encoder =
            new Argon2PasswordEncoder(16, 32, 1, 19456, 2);

    @Test
    void elPhcProducidoEmpiezaPorArgon2idConParametrosOwasp2024() {
        String hash = encoder.encode("contrasena-de-prueba");
        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
    }

    @Test
    void dosEncodeDelMismoRawProducenHashesDiferentesPorSalAleatoria() {
        String hash1 = encoder.encode("misma-contrasena");
        String hash2 = encoder.encode("misma-contrasena");
        assertThat(hash1).isNotEqualTo(hash2);
        // Y ambos siguen siendo válidos para matches (PHC autodescripto, S8).
        assertThat(encoder.matches("misma-contrasena", hash1)).isTrue();
        assertThat(encoder.matches("misma-contrasena", hash2)).isTrue();
    }

    @Test
    void matchesEsTrueConElRawCorrectoYFalseConOtro() {
        String hash = encoder.encode("contrasena-correcta");
        assertThat(encoder.matches("contrasena-correcta", hash)).isTrue();
        assertThat(encoder.matches("contrasena-equivocada", hash)).isFalse();
    }

    @Test
    void matchesFuncionaConLosParametrosAutodescritosDelPhc() {
        // S8: aunque el bean evolucionara, matches respeta los parámetros
        // codificados dentro del propio PHC (aquí, los producidos por este
        // constructor 19456/2/p=1).
        String hashOtrasParams = encoder.encode("otra-contrasena");
        assertThat(hashOtrasParams).contains("m=19456,t=2,p=1");
        assertThat(encoder.matches("otra-contrasena", hashOtrasParams)).isTrue();
    }
}