package com.projectlog.keys;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * E4.3 / E4.11 / E4.12 [L] (Done 5 de la proposal + Done 1 parcial): test
 * test PURO de las primitivas de {@link ApiKeyService} — SIN BD, SIN Spring
 * context, sin mocks (design §1.2: frontera crypto pura).
 *
 * <p>Ancla el formato plk_* (47 chars, entropía en muestra de 100), el
 * SHA-256 del String COMPLETO UTF-8 (vector fijo {@code sha256("abc")}) y
 * la comparación en tiempo constante (pareja correcta = true; hash anterior
 * vs nueva = false — exactamente lo que C5 hará en el hot-path).</p>
 */
class ApiKeyServiceTest {

    private static final String REGEX_PLK_47 = "^plk_[A-Za-z0-9_-]{43}$";

    /** Vector fijo del spec E4.11 (hex de sha256("abc"), UTF-8). */
    private static final String SHA_256_DE_ABC_HEX =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private final ApiKeyService servicio = new ApiKeyService();

    // ------------------------------------------------------------------
    // E4.3 — newKey: 47 chars, formato plk_*, entropía en muestra
    // ------------------------------------------------------------------

    @Test
    void newKeyGenera47CharsConEntropiaEnMuestra() {
        Set<String> claves = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String key = servicio.newKey();
            assertThat(key).matches(REGEX_PLK_47); // 47 chars EXACTOS (32 B Base64url)
            claves.add(key);
        }
        assertThat(claves).hasSize(100); // TODAS distintas (entropía 256 bits)
    }

    // ------------------------------------------------------------------
    // E4.11 — sha256 del String COMPLETO + pareja correcta → true
    // ------------------------------------------------------------------

    @Test
    void sha256EsDelStringCompletoYParejaCorrectaDaTrue() {
        String k = servicio.newKey();
        byte[] hashK = servicio.sha256(k);

        // pareja correcta: sha256(K) vs sha256(K) → true (primitiva del hot-path C5)
        assertThat(servicio.tiempoConstanteIgual(hashK, servicio.sha256(k))).isTrue();

        // vector fijo: sha256("abc") hex ba7816bf…5ad (digest UTF-8 de 32 bytes)
        assertThat(hex(servicio.sha256("abc")))
                .isEqualTo(SHA_256_DE_ABC_HEX);
    }

    // ------------------------------------------------------------------
    // E4.12 — hash anterior vs key nueva → false
    // ------------------------------------------------------------------

    @Test
    void tiempoConstanteIgualConHashAnteriorDaFalse() {
        String kAnterior = servicio.newKey();
        String kNueva = servicio.newKey();
        assertThat(kNueva).isNotEqualTo(kAnterior); // dos generaciones independientes
        assertThat(servicio.tiempoConstanteIgual(
                servicio.sha256(kAnterior), servicio.sha256(kNueva))).isFalse();
    }

    /** Hex minúscula de un digest binario (solo para el vector fijo del test). */
    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}