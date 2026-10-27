// Filtros de /logs EN LA QUERY STRING (D-C8-6/R8.22 — Riesgo R-7 obligatorio):
// `useSearchParams` como ÚNICA fuente del estado de filtros (F5 conserva,
// vista compartible, retorno desde el detalle intacto — AppFlow flujo 4).
// El mapeo bidireccional es de funciones PURAS exportadas (E8.21 — test sin
// DOM); las fechas viajan ISO-8601 CON offset local del navegador (S8-9).
import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import type { Entorno, FiltrosLogs, Nivel, SetFilters } from "../api/types";

// Re-export del tipo compartido (definido en api/types.ts SIN ciclo api↔hooks).
export type { FiltrosLogs, SetFilters };

const NIVELES: readonly Nivel[] = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"];
const ENTORNOS: readonly Entorno[] = ["development", "staging", "production"];

/** Ida: query string → filtros (solo claves con valor se leen; inválidos ⇒ null). */
export function filtrosDesdeSearch(sp: URLSearchParams): FiltrosLogs {
  const pid = sp.get("project_id");
  const env = sp.get("environment");
  const lvl = sp.get("level");
  return {
    project_id:
      pid !== null && pid !== "" && /^\d+$/.test(pid) ? Number(pid) : null,
    environment:
      env !== null && (ENTORNOS as readonly string[]).includes(env)
        ? (env as Entorno)
        : null,
    level:
      lvl !== null && (NIVELES as readonly string[]).includes(lvl)
        ? (lvl as Nivel)
        : null,
    from: sp.get("from") || null,
    to: sp.get("to") || null,
    text: sp.get("text") || null,
  };
}

/** Vuelta: filtros → query string — SOLO claves con valor (sin nulls ni
 * vacíos), en orden ESTABLE (project_id, environment, level, from, to, text).
 * Ida y vuelta idempotente (E8.21). */
export function searchDesdeFiltros(f: FiltrosLogs): string {
  const sp = new URLSearchParams();
  if (f.project_id !== null) sp.set("project_id", String(f.project_id));
  if (f.environment !== null) sp.set("environment", f.environment);
  if (f.level !== null) sp.set("level", f.level);
  if (f.from !== null) sp.set("from", f.from);
  if (f.to !== null) sp.set("to", f.to);
  if (f.text !== null) sp.set("text", f.text);
  return sp.toString();
}

/** Shape EXACTO de set_filters (S8-8): 6 claves SIEMPRE presentes, null donde
 * no aplica; JAMÁS claves extra. `project_id` como number cuando hay valor. */
export function toSetFilters(f: FiltrosLogs): SetFilters {
  return {
    project_id: f.project_id,
    environment: f.environment,
    level: f.level,
    from: f.from,
    to: f.to,
    text: f.text,
  };
}

/**
 * `datetime-local` → ISO-8601 CON offset local del navegador (S8-9/E8.23):
 * p. ej. "2026-10-26T10:30" (en UTC+2) → "2026-10-26T10:30:00+02:00".
 * NUNCA `toISOString()` (destruiría el offset viajando a UTC).
 * `offsetMinutos` inyectable para tests (por defecto getTimezoneOffset()
 * del Date interpretado LOCAL: JS usa la convención UTC - local, p. ej.
 * +02:00 ⇒ -120).
 */
export function datetimeLocalAIsoConOffset(
  v: string,
  offsetMinutos?: number,
): string {
  const limpio = v.trim();
  if (limpio === "") return limpio;
  // idempotencia: ya es ISO-8601 con offset o Z — viaja tal cual
  if (/(?:Z|[+-]\d{2}:\d{2})$/.test(limpio)) {
    return limpio;
  }
  const offset =
    offsetMinutos ?? new Date(limpio).getTimezoneOffset();
  const conSegundos = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(limpio)
    ? `${limpio}:00`
    : limpio;
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(conSegundos)) {
    return limpio; // no es datetime-local: viaja tal cual
  }
  const signo = offset <= 0 ? "+" : "-";
  const abs = Math.abs(offset);
  const hh = String(Math.floor(abs / 60)).padStart(2, "0");
  const mm = String(abs % 60).padStart(2, "0");
  return `${conSegundos}${signo}${hh}:${mm}`;
}

export interface ValorFiltros {
  filtros: FiltrosLogs;
  /** Cambia un filtro y actualiza la URL (solo claves con valor). El efecto
   * de refetch/página 1/set_filters se dispara en Logs.tsx con el cambio
   * del search (E8.22). */
  actualizar(parcial: Partial<FiltrosLogs>): void;
  /** Reiniciar ⇒ query string VACÍA (R8.22). */
  reiniciar(): void;
}

/** Hook de /logs: la query string es la ÚNICA fuente de verdad. */
export function useFiltrosLogs(): ValorFiltros {
  const [searchParams, setSearchParams] = useSearchParams();

  const filtros = useMemo(
    () => filtrosDesdeSearch(searchParams),
    [searchParams],
  );

  const actualizar = useCallback(
    (parcial: Partial<FiltrosLogs>) => {
      const nuevos: FiltrosLogs = { ...filtros, ...parcial };
      // normaliza fechas de entrada datetime-local → ISO con offset
      const normalizados: FiltrosLogs = {
        ...nuevos,
        from:
          nuevos.from !== null && nuevos.from !== ""
            ? datetimeLocalAIsoConOffset(nuevos.from)
            : null,
        to:
          nuevos.to !== null && nuevos.to !== ""
            ? datetimeLocalAIsoConOffset(nuevos.to)
            : null,
      };
      setSearchParams(searchDesdeFiltros(normalizados));
    },
    [filtros, setSearchParams],
  );

  const reiniciar = useCallback(() => {
    setSearchParams(new URLSearchParams());
  }, [setSearchParams]);

  return { filtros, actualizar, reiniciar };
}