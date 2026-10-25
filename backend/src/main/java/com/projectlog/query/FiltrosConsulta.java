package com.projectlog.query;

/**
 * C6 (S-3 de C4 se CIERRA aquí — P3 del gate; §3.3 verbatim: "«text» se
 * recibe SIN comodines y se busca como %&lt;text&gt;% (escapando %, _ y \)":
 * el cliente NUNCA aporta comodines; el escape es server-side CONTRATO).
 *
 * <p>ORDEN OBLIGATORIO: {@code \} primero, luego {@code %}, luego
 * {@code _} (escapar %{@code %}/{@code _} antes que {@code \} DOBLARÍA las
 * barras recién insertadas — E6.13 lo pina). El {@code \} es el escape
 * DEFAULT de LIKE/ILIKE en PG (el congelado no declara {@code ESCAPE ''} —
 * S6-5: el estamento NO lleva la palabra ESCAPE); el resultado va YA
 * envuelto {@code %...%} y el parámetro del estamento llega ya
 * escapado+envuelto (S6-5).</p>
 *
 * <p>La fase 1 del contrato (R6.20): el controller JAMÁS llama a esta
 * función con {@code null} — {@code text} AUSENTE es "filtro ausente" y viaja
 * DIRECTO al repo como parámetro patrón {@code null} (la rama
 * {@code CAST(? AS text) IS NULL} del estamento único lo absorbe); el vacío
 * LEGAL llega como {@code ""} y la fórmula (sin case especial) produce
 * naturalmente {@code "%%"} — matchea todos los messages (S6-2). El texto NO
 * se trimma NI se normaliza ni lleva cap de longitud (D-6.10/S6-10).</p>
 *
 * <p>Función pura: sin BD, sin Spring (unit E6.13). Seam C7: la misma
 * semántica de filtros del stream (§3.5) la reutiliza.</p>
 */
public final class FiltrosConsulta {

    private FiltrosConsulta() {
    }

    /**
     * Construye el patrón ILIKE escapado y envuelto: (1) vacío →
     * {@code "%%"} (S6-2, sin case especial); (2) {@code \} → {@code \\}
     * PRIMERO; (3) {@code %} → {@code \%}; (4) {@code _} → {@code \_};
     * (5) envolver {@code %...%}.
     *
     * @param texto texto del cliente (JAMÁS null — ver precondición arriba)
     * @return patrón {@code %<texto-escapado>%} listo para el estamento
     */
    public static String patronIlike(String texto) {
        String escapado = texto.replace("\\", "\\\\")          // \  primero
                .replace("%", "\\%")                            // %  luego
                .replace("_", "\\_");                           // _  después
        return "%" + escapado + "%";
    }
}