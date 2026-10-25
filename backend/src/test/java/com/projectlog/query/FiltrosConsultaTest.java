package com.projectlog.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * E6.13 [L] — pin del {@code patronIlike} (R6.20/S6-5; cierre de la S-3 de
 * C4 — P3 del gate): unit puro SIN Spring/BD. Literales Java: {@code "a_b"}
 * → {@code %a\_b%} (el {@code _} queda ESCAPADO, no wildcard);
 * {@code "100%"} → {@code %100\%%}; {@code "a\b"} → {@code %a\\b%} (la barra
 * se dobla); el ORDEN pinado con {@code _} y {@code \} JUNTOS (matriz del
 * cierre S6-5 — solo el orden \-primero lo produce correcto); contra-caso de
 * la AGUJA ({@code patronIlike("smoke_c6_X")} ←→ un needle con el _
 * sustituido no matchearía) y {@code ""} → {@code "%%"} (S6-2, sin case
 * especial).
 */
class FiltrosConsultaTest {

    @Test
    void elGuionBajoQuedaEscapadoNoWildcard() {
        assertThat(FiltrosConsulta.patronIlike("a_b")).isEqualTo("%a\\_b%");
    }

    @Test
    void elPorcentajeQuedaEscapadoNoWildcard() {
        assertThat(FiltrosConsulta.patronIlike("100%")).isEqualTo("%100\\%%");
    }

    @Test
    void laBarraInvertidaSeDoblaPrimero() {
        // Java-literal en ENTRADA "a\\b" = texto real a\b → salida real %a\\b%
        assertThat(FiltrosConsulta.patronIlike("a\\b")).isEqualTo("%a\\\\b%");
    }

    @Test
    void elOrdenGuionMasBarraJuntosSoloEsCorrectoConBarraPrimero() {
        // Texto real: a_b\c  → correcto: a\_b\\c (la barra del cliente se
        // dobla PRIMERO; después las barras INSERTADAS del _escape no se
        // doblan). Si el orden fuese _-primero, el \→\\ posterior DOBLARÍA
        // la barra recién insertada del \_ (matriz del cierre S6-5).
        assertThat(FiltrosConsulta.patronIlike("a_b\\c")).isEqualTo("%a\\_b\\\\c%");
        // texto real \_%: la barra se dobla (2), el % se escapa (\%) y el
        // _ (\_); las barras INSERTADAS ya no se re-doblan → \\_\% real
        assertThat(FiltrosConsulta.patronIlike("\\_%"))
                .isEqualTo("%\\\\\\_\\%%");
    }

    @Test
    void contracasoDeLaAgujaElGuionBajoNoEsComodin() {
        // cierre de la S-3 de C4: el patrón de la aguja lleva el _ ESCAPADO
        String patronAguja = FiltrosConsulta.patronIlike("smoke_c6_X");
        assertThat(patronAguja).isEqualTo("%smoke\\_c6\\_X%");
        // un needle "gemelo" con el _ sustituido por otra letra produce OTRO
        // patrón y NO matchearía con el de la aguja (los caracteres del
        // needle no son comodines: quedan literales en el patrón)
        assertThat(patronAguja).isNotEqualTo(FiltrosConsulta.patronIlike("needleXc6"));
        // el patrón NUNCA contiene un _ crudo sin escapar del needle
        assertThat(patronAguja).contains("\\_").doesNotContainPattern(
                "(?<!\\\\)_");
        assertThat(patronAguja).doesNotContain("%smoke_c6_X%");
    }

    @Test
    void textoVacioProducePatronUniversalSinCaseEspecial() {
        // S6-2: "" es LEGAL → "%%" (la fórmula lo produce naturalmente)
        assertThat(FiltrosConsulta.patronIlike("")).isEqualTo("%%");
    }
}