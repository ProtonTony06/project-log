package com.projectlog.query;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * C6 (P1 del gate 2026-10-25; Schema §3.3: "next_cursor es un token OPACO
 * para el cliente; internamente codifica (received_at, id) en Base64url".
 * R-6.a (hallazgo CRÍTICO del explore): la clave "r" lleva el instante SIN
 * truncar — el formateador de casa (EXPIRES_AT_FORMATTER) trunca a segundos
 * y PG TIMESTAMP guarda MICROSEGUNDOS (todo batch comparte un solo
 * received_at, regla 9); un cursor truncado SALTARÍA las filas del MISMO
 * instante que la última fila entregada (ni received_at &lt; r ni = r —
 * desaparecerían entre páginas, Done literal roto).
 *
 * <p>Formato EXACTO del token: JSON compacto {@code {"r":"<Instant
 * ISO-8601>","i":<id>}} → Base64url WITHOUT-PADDING
 * ({@code Base64.getUrlEncoder().withoutPadding()}). El cliente lo trata
 * como OPACO (nunca lo parsea — literal §3.3). Pieza PURA: SOLO métodos
 * static, SIN Spring y SIN BD (unit-testable — E6.12). Threading: el
 * {@code ObjectMapper} static es thread-safe (uso documentado, D-R6.2).</p>
 */
public final class CursorCodec {

    /**
     * D-6.3: el {@code ObjectMapper} static es PROPIO del codec y SOLO para
     * la DECODIFICACIÓN (el ENCODE es composición manual — byte-exacto con
     * el ejemplo del Schema, sonda P-1; el codec NUNCA usa el bean del
     * contexto — D-R6.2).
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CursorCodec() {
    }

    /**
     * ENCODE (R6.15-a, fórmula EXACTA): (1) {@code r} =
     * {@code receivedAt.toInstant().toString()} — ISO-8601 UTC del instante
     * COMPLETO (sin fracciones si PG no las trajo, {@code .123456} a micros
     * si sí; NUNCA truncado, NUNCA re-formateado por el formatter de C2 —
     * R6.15-c: el RAW viaja del RowMapper);
     * (2) JSON por COMPOSICIÓN MANUAL (NO ObjectMapper en encode, NO
     * {@code Map.of}: el orden de claves debe ser determinista
     * {@code r}-luego-{@code i}) — el instante ISO-8601 no puede contener
     * {@code "} ni {@code \}, así que la composición es segura;
     * (3) Base64url SIN padding sobre los bytes UTF-8.
     * Pin P-1: {@code codificar(parse("2026-10-16T22:31:04Z"), 12301)} ===
     * {@code eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0}.
     */
    public static String codificar(OffsetDateTime receivedAt, long id) {
        String r = receivedAt.toInstant().toString();
        String json = "{\"r\":\"" + r + "\",\"i\":" + id + "}";
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodificación ESTRECHA (P1, R6.15-b — los 6 pasos EN ORDEN;
     * cualquier fallo en CUALQUIER paso → la MISMA
     * {@link CursorInvalidoException}, SIN oráculo de causa): (1)
     * null/vacío; (2) rechazo EXPLÍCITO del padding '='; (3) decode
     * Base64url (el alfabeto clásico '+'/'/' lo rechaza el decoder);
     * (4) readTree JSON (fail si no parseable); (5) estructura estrecha:
     * raíz objeto, EXACTAMENTE 2 claves 'r'+'i' (extras → inválido), 'r'
     * textual parseable con {@code Instant.parse} (SOLO UTC-Z: '+02:00' NO
     * es instante-parseable — el codec propio jamás lo emite; NUNCA
     * re-derivado ni truncado), 'i' integral {@code longValueExact()} ≥ 1;
     * (6) {@code Cursor(r, i)} con el instante EXACTO al micro/nano.
     *
     * <p>El 'r' decodificado NUNCA se valida contra la BD (sin oráculo de
     * instante): un cursor "de futuro" responde 200 con página vacía —
     * comparación keyset legítima (cierre §8.2).</p>
     *
     * @throws CursorInvalidoException ante cualquier ilegibilidad (el
     *         controller la traduce al 400 filter_cursor_invalid, §1.D
     *         check 8)
     */
    public static Cursor decodificar(String token) {
        try {
            if (token == null || token.isEmpty()) {
                throw new CursorInvalidoException(); // paso 1
            }
            if (token.contains("=")) { // paso 2: SIN padding (anti-laxitud)
                throw new CursorInvalidoException();
            }
            byte[] bytes = Base64.getUrlDecoder().decode(token); // paso 3
            JsonNode raiz = MAPPER.readTree(bytes); // paso 4
            if (!raiz.isObject() || raiz.size() != 2
                    || raiz.path("r").isMissingNode()
                    || raiz.path("i").isMissingNode()) {
                throw new CursorInvalidoException(); // paso 5a
            }
            JsonNode r = raiz.get("r");
            JsonNode i = raiz.get("i");
            if (!r.isTextual() || !i.isIntegralNumber() || i.longValue() < 1) {
                throw new CursorInvalidoException(); // paso 5b
            }
            // paso 5c: SOLO instante UTC-Z. El spec R6.15-b (§1.F) asumió que
            // Instant.parse de ISO-8601 estricto rechazaba '2026-...T...+02:00',
            // pero desde JDK 8u222+/JDK12 el formatter ISO_INSTANT PARSEA
            // offsets y los NORMALIZA a UTC (sonda P-3 empírica de C6: JUnit
            // E6.9(e) devolvía 200). El guard cierra el contrato: el codec
            // propio jamás emite un 'r' que no acabe en 'Z'.
            String rTexto = r.asText();
            if (!rTexto.endsWith("Z")) {
                throw new CursorInvalidoException();
            }
            Instant instante = Instant.parse(r.asText()); // SOLO UTC-Z
            // longValueExact sobre BigInteger: overflow (i gigante) →
            // ArithmeticException atrapada abajo, misma tolerancia
            return new Cursor(instante, i.bigIntegerValue().longValueExact());
        } catch (CursorInvalidoException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // IllegalArgumentException (Base64/Instant.parse) y
            // ArithmeticException del longValueExact — MISMA excepción,
            // SIN oráculo de causa (P1). IAE es RuntimeException: una sola rama.
            throw new CursorInvalidoException();
        } catch (Exception ex) {
            // checked del readTree (IOException) — misma tolerancia estrecha.
            throw new CursorInvalidoException();
        }
    }

    /** Moneda del decode: el instante EXACTO + el id (R6.15). */
    public record Cursor(Instant r, long i) {
    }

    /** Excepción única de tolerancia estrecha (sin oráculo de causa). */
    public static final class CursorInvalidoException extends RuntimeException {
    }
}