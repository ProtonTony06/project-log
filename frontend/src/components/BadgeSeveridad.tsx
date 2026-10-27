// Badge de severidad (V3 — R8.20): clase literal `lv lv-<LEVEL>` de la
// maqueta 02 (estilos en index.css, SIN redefinir colores). Texto SIEMPRE
// visible junto al color (no solo la pastilla) — E8.19.
import type { Nivel } from "../api/types";

export function BadgeSeveridad({
  level,
  texto,
}: {
  level: Nivel;
  /** Conteo opcional (p. ej. "FATAL 1" en tarjetas/resumen). */
  texto?: string;
}) {
  return <span className={`lv lv-${level}`}>{texto ?? level}</span>;
}