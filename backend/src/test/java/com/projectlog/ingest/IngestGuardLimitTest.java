package com.projectlog.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * E5.34 [L] (tasks T5.16, S5-9/D-6, R-6, OBLIGATORIO): unit del
 * normalizador del bind — {@code limiteBytes(String)} SIN Spring. Casas:
 * {@code 1MiB}→1048576; {@code 1KB}/{@code 1KiB}→1024; dígitos puros
 * {@code 1048576}; {@code 1MB}→1048576 (los KB/MB/GB de Spring SON
 * binarios, spring-core); literal no parseable → excepción (fail-fast de
 * arranque). Y el default del {@code @Value} del filtro es EXACTAMENTE
 * {@code 1MiB} (assert sobre la anotación via reflection del constructor —
 * sin duplicación frágil del literal).
 */
class IngestGuardLimitTest {

    @Test
    void limiteBytesNormalizaElSufijoIbYLosLitaresDeSpring() {
        // el valor del .env.example (INTOCABLE) y default del @Value
        assertThat(IngestGuardFilter.limiteBytes("1MiB")).isEqualTo(1048576L);
        // KB de Spring es BINARIO (1024-base, spring-core 6.2.19)
        assertThat(IngestGuardFilter.limiteBytes("1KB")).isEqualTo(1024L);
        assertThat(IngestGuardFilter.limiteBytes("1KiB")).isEqualTo(1024L);
        // dígitos puros pasan tal cual
        assertThat(IngestGuardFilter.limiteBytes("1048576")).isEqualTo(1048576L);
        // 1MB equivale binariamente a 1MiB (evidencia spring-core)
        assertThat(IngestGuardFilter.limiteBytes("1MB")).isEqualTo(1048576L);
        assertThat(IngestGuardFilter.limiteBytes("1GiB")).isEqualTo(1073741824L);
    }

    @Test
    void literalNoParseableFallaEnArranque() {
        // fail-fast (S5-9): "abc" → excepción en el constructor, jamás null/0
        assertThatThrownBy(() -> IngestGuardFilter.limiteBytes("abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IngestGuardFilter.limiteBytes("x"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void elDefaultDelValueEsExactamente1MiB() throws Exception {
        // (gate 2026-10-25): los 2 primeros parámetros son ObjectProvider
        // (cierre del bloqueo T5.5) — el @Value del límite sigue en el 4.º
        Constructor<IngestGuardFilter> constructor =
                IngestGuardFilter.class.getDeclaredConstructor(
                        org.springframework.beans.factory.ObjectProvider.class,
                        org.springframework.beans.factory.ObjectProvider.class,
                        com.fasterxml.jackson.databind.ObjectMapper.class,
                        String.class);
        Parameter parametro = constructor.getParameters()[3];
        Value anotacion = parametro.getAnnotation(Value.class);
        assertThat(anotacion).isNotNull();
        assertThat(anotacion.value())
                .isEqualTo("${projectlog.ingest.max-request-bytes:1MiB}");
    }
}