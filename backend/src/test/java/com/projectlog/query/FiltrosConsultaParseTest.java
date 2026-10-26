package com.projectlog.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * E7.20 [L] — pin unitario puro de {@link FiltrosConsulta.parsear}
 * (C7/R7.27, D-7.12): los 6 checks compartidos EN ORDEN (2→7,
 * primer-fallo-gana) con {@code FiltroInvalido.code/message/field} EXACTOS,
 * literales byte-idénticos a los E6.x de C5/C6 (tabla R6.22); caso feliz
 * tipado completo con {@code patronTexto} Y texto crudo SEPARADOS (D-7.13);
 * aguja {@code _} (el patrón lleva {@code \_}, el crudo NO — S7-10);
 * {@code text=""} → patrón {@code %%} + crudo {@code ""} (S6-2); {@code to}
 * null → null. SIN Spring ni BD. {@code patronIlike} re-corrida byte-a-byte
 * (su test original C6 queda INTOCABLE).
 */
class FiltrosConsultaParseTest {

    @Test
    void check2ProjectIdNoNumericoPrimeroEnOrden() {
        // primero-fallo-gana: con project_id Y environment ambos inválidos,
        // el check 2 (project_id) gana.
        assertThatThrownBy(() -> FiltrosConsulta.parsear("abc", "qa", null,
                null, null, null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> {
                            assertThat(ex.code).isEqualTo("filter_invalid_value");
                            assertThat(ex.message).isEqualTo(
                                    "El filtro project_id debe ser un número");
                            assertThat(ex.field).isEqualTo("project_id");
                        });
    }

    @Test
    void check3EnvironmentFueraDeEnumIncluidoVacio() {
        assertThatThrownBy(() -> FiltrosConsulta.parsear(null, "qa", null,
                null, null, null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> {
                            assertThat(ex.code).isEqualTo("filter_invalid_value");
                            assertThat(ex.message).isEqualTo(
                                    "Valor de environment no válido; use development, staging o production");
                            assertThat(ex.field).isEqualTo("environment");
                        });
        assertThatThrownBy(() -> FiltrosConsulta.parsear(null, "", null,
                null, null, null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> {
                            assertThat(ex.code).isEqualTo("filter_invalid_value");
                            assertThat(ex.field).isEqualTo("environment");
                        });
    }

    @Test
    void check4LevelFueraDeEnumIncluidoVacio() {
        assertThatThrownBy(() -> FiltrosConsulta.parsear(null, null, "error",
                null, null, null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> {
                            assertThat(ex.code).isEqualTo("filter_invalid_value");
                            assertThat(ex.message).isEqualTo(
                                    "Valor de level no válido; use TRACE, DEBUG, INFO, WARN, ERROR o FATAL");
                            assertThat(ex.field).isEqualTo("level");
                        });
        assertThatThrownBy(() -> FiltrosConsulta.parsear(null, null, "",
                null, null, null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> assertThat(ex.field).isEqualTo("level"));
    }

    @Test
    void checks5y6FromToNaiveSinOffsetRechazados() {
        // patrón S5-5 de C5: OffsetDateTime.parse exige offset — naive → from.
        assertThatThrownBy(() -> FiltrosConsulta.parsear(null, null, null,
                "2026-10-16T22:31:04", null, null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> {
                            assertThat(ex.code).isEqualTo("filter_invalid_value");
                            assertThat(ex.message).isEqualTo(
                                    "El filtro from debe ser una fecha ISO-8601 con zona horaria");
                            assertThat(ex.field).isEqualTo("from");
                        });
        assertThatThrownBy(() -> FiltrosConsulta.parsear(null, null, null,
                null, "no-es-fecha", null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> {
                            assertThat(ex.code).isEqualTo("filter_invalid_value");
                            assertThat(ex.message).isEqualTo(
                                    "El filtro to debe ser una fecha ISO-8601 con zona horaria");
                            assertThat(ex.field).isEqualTo("to");
                        });
    }

    @Test
    void check7FromPosteriorAToSinField() {
        assertThatThrownBy(() -> FiltrosConsulta.parsear(null, null, null,
                "2026-10-18T00:00:00Z", "2026-10-16T00:00:00Z", null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> {
                            assertThat(ex.code)
                                    .isEqualTo("filter_from_to_inverted");
                            assertThat(ex.message).isEqualTo(
                                    "El rango temporal no es válido: from es posterior a to");
                            // R6.22: filter_from_to_inverted SIN field.
                            assertThat(ex.field).isNull();
                        });
        // from == to es LEGAL (rango de punto) — no lanza.
        FiltrosConsulta.Filtros punto = FiltrosConsulta.parsear(null, null,
                null, "2026-10-16T00:00:00Z", "2026-10-16T00:00:00Z", null);
        assertThat(punto.from()).isEqualTo(punto.to());
    }

    @Test
    void casoFelizTipadoCompletoConPatronYTextoCrudoSeparados() {
        FiltrosConsulta.Filtros f = FiltrosConsulta.parsear("7",
                "production", "ERROR", "2026-10-16T00:00:00Z",
                "2026-10-18T00:00:00Z", "timeout");
        assertThat(f.projectId()).isEqualTo(7L);
        assertThat(f.environment()).isEqualTo("production");
        assertThat(f.level()).isEqualTo("ERROR");
        assertThat(f.from()).isEqualTo(OffsetDateTime.parse("2026-10-16T00:00:00Z"));
        assertThat(f.to()).isEqualTo(OffsetDateTime.parse("2026-10-18T00:00:00Z"));
        // D-7.13: texto crudo SEPARADO del patrón ILIKE.
        assertThat(f.texto()).isEqualTo("timeout");
        assertThat(f.patronTexto()).isEqualTo("%timeout%");
    }

    @Test
    void agujaElPatronLlevaElEscapeYElCrudoNo() {
        FiltrosConsulta.Filtros f = FiltrosConsulta.parsear(null, null, null,
                null, null, "a_b");
        assertThat(f.patronTexto()).isEqualTo("%a\\_b%"); // patrón escapado
        assertThat(f.texto()).isEqualTo("a_b");          // crudo SIN escape
    }

    @Test
    void textoVacioPatronPorcentajeDobleYCrudoVacio() {
        FiltrosConsulta.Filtros f = FiltrosConsulta.parsear(null, null, null,
                null, null, "");
        assertThat(f.patronTexto()).isEqualTo("%%"); // S6-2, sin case especial
        assertThat(f.texto()).isEmpty();
    }

    @Test
    void toNullAusenteViajaNullYPatronTextoNullSinText() {
        FiltrosConsulta.Filtros f = FiltrosConsulta.parsear(null, null, null,
                null, null, null);
        assertThat(f.to()).isNull();
        assertThat(f.from()).isNull();
        assertThat(f.projectId()).isNull();
        assertThat(f.environment()).isNull();
        assertThat(f.level()).isNull();
        assertThat(f.texto()).isNull();
        assertThat(f.patronTexto()).isNull(); // filtro ausente → null
    }

    @Test
    void patronIlikeSeRecorreByteAByteMatrizOriginal() {
        // re-corrida (E7.20f) — sin tocar FiltrosConsultaTest de C6.
        assertThat(FiltrosConsulta.patronIlike("a_b")).isEqualTo("%a\\_b%");
        assertThat(FiltrosConsulta.patronIlike("100%")).isEqualTo("%100\\%%");
        assertThat(FiltrosConsulta.patronIlike("a\\b")).isEqualTo("%a\\\\b%");
        assertThat(FiltrosConsulta.patronIlike("a_b\\c"))
                .isEqualTo("%a\\_b\\\\c%");
        assertThat(FiltrosConsulta.patronIlike("\\_%"))
                .isEqualTo("%\\\\\\_\\%%");
        assertThat(FiltrosConsulta.patronIlike("")).isEqualTo("%%");
    }

    /**
     * H12 — pin del ORDEN GLOBAL 2→7 del parser en UNA CADENA con
     * sub-casos COMBINADOS (pares inválidos simultáneos; el check
     * MENOR-NUMERADO SIEMPRE gana — primer-fallo-gana R7.28): 2 sobre 3,
     * 3 sobre 4, 4 sobre 5, 5 sobre 6 y el 7 cierra el orden con
     * {@code field} null. Literales VERBATIM de la tabla R6.22 (los
     * mismos del resto del fichero).
     */
    @Test
    void ordenGlobalChecks2a7PrimerFalloGana() {
        // (2) project_id inválido + environment inválido → gana project_id
        assertThatThrownBy(() -> FiltrosConsulta.parsear("abc", "qa", null,
                null, null, null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> {
                            assertThat(ex.code).isEqualTo("filter_invalid_value");
                            assertThat(ex.message).isEqualTo(
                                    "El filtro project_id debe ser un número");
                            assertThat(ex.field).isEqualTo("project_id");
                        });
        // (3) environment inválido + level inválido → gana environment
        assertThatThrownBy(() -> FiltrosConsulta.parsear(null, "qa", "error",
                null, null, null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> {
                            assertThat(ex.message).isEqualTo(
                                    "Valor de environment no válido; use development, staging o production");
                            assertThat(ex.field).isEqualTo("environment");
                        });
        // (4) level inválido + from naive (sin offset) → gana level
        assertThatThrownBy(() -> FiltrosConsulta.parsear(null, null, "error",
                "2026-10-16T22:31:04", null, null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> {
                            assertThat(ex.message).isEqualTo(
                                    "Valor de level no válido; use TRACE, DEBUG, INFO, WARN, ERROR o FATAL");
                            assertThat(ex.field).isEqualTo("level");
                        });
        // (5) from naive + to no-fecha → gana from
        assertThatThrownBy(() -> FiltrosConsulta.parsear(null, null, null,
                "2026-10-16T22:31:04", "no-es-fecha", null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> assertThat(ex.field).isEqualTo("from"));
        // (6/7) from>to con AMBOS válidos → check 7 cierra el orden global
        // con su SIN-field (R6.22: filter_from_to_inverted SIN field) —
        // nada de los checks 2–6 está presente para poder ganar antes.
        assertThatThrownBy(() -> FiltrosConsulta.parsear(null, null, null,
                "2026-10-18T00:00:00Z", "2026-10-16T00:00:00Z", null))
                .isInstanceOfSatisfying(FiltrosConsulta.FiltroInvalido.class,
                        ex -> {
                            assertThat(ex.code)
                                    .isEqualTo("filter_from_to_inverted");
                            assertThat(ex.message).isEqualTo(
                                    "El rango temporal no es válido: from es posterior a to");
                            assertThat(ex.field).isNull();
                        });
    }
}