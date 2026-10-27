// Píldora de entorno (V4 — R8.20): clase literal `env env-<environment>` de
// la maqueta 02; production SIEMPRE oscura (estilo .env-production en
// index.css). Texto SIEMPRE visible junto al color.
import type { Entorno } from "../api/types";

export function PildoraEntorno({
  environment,
  texto,
}: {
  environment: Entorno;
  /** Conteo opcional (p. ej. "production 23 988" en tarjetas/resumen). */
  texto?: string;
}) {
  return <span className={`env env-${environment}`}>{texto ?? environment}</span>;
}