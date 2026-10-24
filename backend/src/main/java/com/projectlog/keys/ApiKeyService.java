package com.projectlog.keys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * {@code ApiKeyService} (R4.11, design §1.2): FRONTERA CRYPTO pura de la
 * feature de API keys (C4) — las ÚNICAS 4 primitivas del corte, SIN estado
 * de instancia, SIN BD y SIN logging (R4.13: ninguna clase de
 * {@code com.projectlog.keys} loguea {@code plk_} ni la key).
 *
 * <p>Solo se usa con {@code @Service} (y no como utilidad 100 % estática)
 * para que el controller la reciba como bean inyectable y los tests MVC de
 * C4 la mockeen ({@code @MockitoBean} → {@code verifyNoInteractions} en
 * E4.5/E4.6).</p>
 *
 * <ul>
 *   <li>{@link #newKey()}: {@code plk_} + Base64url UNPADDED de 32 bytes de
 *       {@link SecureRandom} = 47 chars; una generación POR llamada.</li>
 *   <li>{@link #sha256(String)}: digest SHA-256 (32 bytes binarios, BYTEA)
 *       del String COMPLETO de la key en UTF-8 — ni trim de {@code plk_},
 *       ni hex, ni salt (contrato del spec §0.2); MessageDigest NUEVO por
 *       llamada (no es thread-safe como estado compartido).</li>
 *   <li>{@link #prefixDe(String)}: los primeros 10 caracteres de la key
 *       (Schema §2 — {@code api_key_prefix}, derivado NO secreto, único
 *       derivado persistido en claro).</li>
 *   <li>{@link #tiempoConstanteIgual(byte[], byte[])}: wrapper de
 *       {@link MessageDigest#isEqual(byte[], byte[])} (primitiva C5 del
 *       hot-path — Decisión 5 del gate: primitivas en C4 SÍ, lookup NO).</li>
 * </ul>
 *
 * <p>Las únicas constantes {@code static} son el {@link SecureRandom}
 * (thread-safe por contrato del JDK) y el prefijo literal; cero Argon2id/
 * BCrypt (ADR-4: hash rápido = lookup O(1) sobre el índice parcial único —
 * el KDF lento lo destruiría).</p>
 */
@Service
public class ApiKeyService {

    /** Generador criptográfico thread-safe del JDK (una generación por llamada). */
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Prefijo literal del formato plk_* (TRD §2.6 / Schema §5). */
    private static final String PREFIJO = "plk_";

    /**
     * Genera UNA key nueva por llamada: {@code plk_} + Base64url sin padding
     * de 32 bytes de entropía = 47 chars, casando el literal
     * {@code ^plk_[A-Za-z0-9_-]{43}$}.
     */
    public String newKey() {
        byte[] bytes32 = new byte[32];
        ALEATORIO.nextBytes(bytes32);
        return PREFIJO + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes32);
    }

    /**
     * SHA-256 binario (32 bytes, BYTEA) del String COMPLETO de la key —
     * bytes UTF-8 íntegros ({@code MessageDigest} NUEVO por llamada). El
     * digest es lo ÚNICO que se persiste ({@code api_key_hash}); escritura
     * ciega en el repo (R4.4: el repo no calcula ni re-hashea nada).
     */
    public byte[] sha256(String key) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            // JDK 21: SHA-256 es obligatorio en toda implementación — inalcanzable.
            throw new IllegalStateException("SHA-256 no disponible en el JDK", ex);
        }
    }

    /** Los primeros 10 caracteres de la key ({@code api_key_prefix} del Schema §2). */
    public String prefixDe(String key) {
        return key.substring(0, 10);
    }

    /** Comparación en TIEMPO CONSTANTE (wrapper de {@link MessageDigest#isEqual}). */
    public boolean tiempoConstanteIgual(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}