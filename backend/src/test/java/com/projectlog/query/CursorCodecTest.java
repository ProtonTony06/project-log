package com.projectlog.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * E6.12 [L] (P1, OBLIGATORIO) — unit puro del {@code CursorCodec} SIN Spring
 * NI BD: (a) round-trip con instante a MICROSECONDS (ningún dígito perdido —
 * R-6.a/D-R6.a); (b) el pin del EJEMPLO del Schema (sonda P-1): encode
 * byte-exacto {@code eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0} y
 * decode → (2026-10-16T22:31:04Z, 12301); (c) round-trip sin fracciones y
 * con nanos; (d) anti-truncado: truncar a segundos → re-encode produce token
 * DIFERENTE (el codec NUNCA trunca — R6.15-c).
 */
class CursorCodecTest {

    @Test
    void roundTripConMicrosPreservaElInstanteExacto() {
        String instante = "2026-10-16T22:31:04.123456Z";
        OffsetDateTime raw = OffsetDateTime.parse(instante);
        String token = CursorCodec.codificar(raw, 987);
        CursorCodec.Cursor cursor = CursorCodec.decodificar(token);
        // R-6.a: ningún dígito perdido a micros
        assertThat(cursor.r()).isEqualTo(Instant.parse(instante));
        assertThat(cursor.i()).isEqualTo(987L);
    }

    @Test
    void pinP1EncodeByteExactoConElEjemploDelSchema() {
        String token = CursorCodec.codificar(
                OffsetDateTime.parse("2026-10-16T22:31:04Z"), 12301);
        assertThat(token)
                .isEqualTo("eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0");
    }

    @Test
    void pinP1DecodeDevuelveElParExactoDelEjemplo() {
        CursorCodec.Cursor cursor = CursorCodec.decodificar(
                "eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0");
        assertThat(cursor.r()).isEqualTo(Instant.parse("2026-10-16T22:31:04Z"));
        assertThat(cursor.i()).isEqualTo(12301L);
    }

    @Test
    void roundTripSinFraccionesYConNanos() {
        // sin fracciones (PG no las trajo — p. ej. received_at en ":04")
        OffsetDateTime sinFracciones = OffsetDateTime.parse("2026-01-02T03:04:05Z");
        String token = CursorCodec.codificar(sinFracciones, 7);
        CursorCodec.Cursor de = CursorCodec.decodificar(token);
        assertThat(de.r()).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"));
        assertThat(de.i()).isEqualTo(7L);
        assertThat(token).doesNotContain("="); // withoutPadding SIEMPRE

        // con nanos de conveniencia (el driver puede traer precision > micros)
        OffsetDateTime conNanos = OffsetDateTime.parse("2026-01-02T03:04:05.000000007Z");
        CursorCodec.Cursor deNanos = CursorCodec.decodificar(
                CursorCodec.codificar(conNanos, 9));
        assertThat(deNanos.r()).isEqualTo(Instant.parse("2026-01-02T03:04:05.000000007Z"));
        assertThat(deNanos.i()).isEqualTo(9L);
    }

    @Test
    void offsetNoUtcEsRechazadoAunQueInstantParseLoAceptaria() {
        // Sonda P-3 empírica de C6: Instant.parse moderno ACEPTA
        // '2026-10-16T22:31:04+02:00' y lo normaliza a UTC — el paso 5c del
        // codec exige 'Z' para cerrar el contrato del R6.15-b (el E6.9(e)
        // exige filter_cursor_invalid, no 200 con instante convertido)
        String conOffset = tokenDe("{\"r\":\"2026-10-16T22:31:04+02:00\",\"i\":1}");
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> CursorCodec.decodificar(conOffset))
                .isInstanceOf(CursorCodec.CursorInvalidoException.class);
    }

    private static String tokenDe(String json) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void antiTruncadoTruncarAlSegundoProduceTokenDistinto() {
        String conMicros = "2026-10-16T22:31:04.724014Z";
        OffsetDateTime raw = OffsetDateTime.parse(conMicros);
        OffsetDateTime truncado = OffsetDateTime.parse("2026-10-16T22:31:04Z");
        // el truncado cambia el byte (el codec NUNCA trunca — R6.15-a/c):
        assertThat(CursorCodec.codificar(truncado, 1))
                .isNotEqualTo(CursorCodec.codificar(raw, 1));
        // y el round-trip con truncado NO es semánticamente neutro: el
        // instante truncado ya NO es el raw original
        assertThat(CursorCodec.decodificar(
                CursorCodec.codificar(truncado, 1)).r())
                .isNotEqualTo(raw.toInstant());
    }
}