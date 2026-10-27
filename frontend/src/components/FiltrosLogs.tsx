// Filtros de /logs (R8.20/E8.21/E8.22): grid de la maqueta 02
// (1 → 2 → 6 columnas) con proyecto, environment, level, desde/hasta
// datetime-local, Reiniciar y texto del mensaje. El estado vive SIEMPRE en
// la query string (hook useFiltrosLogs, R8.22): cada cambio ⇒ setSearchParams
// (+ reinicio de cursor y set_filters desde Logs.tsx); Reiniciar ⇒ query vacía.
import { useEffect, useRef, useState } from "react";
import type { Entorno, FiltrosLogs, Nivel, ProjectDto } from "../api/types";

const NIVELES: readonly Nivel[] = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"];
const ENTORNOS: readonly Entorno[] = ["development", "staging", "production"];
const inputCls = "w-full border border-stone-300 rounded-lg px-2.5 py-2 text-sm bg-white";

/** datetime-local display: ISO con offset/Z ⇒ parte local (el usuario tecleó
 * su hora local; se re-muestra tal cual, sin doble conversión). */
function aValorDatetimeLocal(iso: string | null): string {
  if (iso === null) return "";
  return iso.replace(/(?:Z|[+-]\d{2}:\d{2})$/, "");
}

export interface FiltrosLogsProps {
  proyectos: ProjectDto[];
  filtros: FiltrosLogs;
  actualizar: (parcial: Partial<FiltrosLogs>) => void;
  reiniciar: () => void;
}

export function FiltrosLogs({
  proyectos,
  filtros,
  actualizar,
  reiniciar,
}: FiltrosLogsProps) {
  // Texto con debounce pequeño: no spamear setSearchParams/set_filters por tecla.
  const [texto, setTexto] = useState(filtros.text ?? "");
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setTexto(filtros.text ?? "");
  }, [filtros.text]);

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) clearTimeout(timerRef.current);
    };
  }, []);

  const alCambiarTexto = (v: string) => {
    setTexto(v);
    if (timerRef.current !== null) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      actualizar({ text: v.trim() === "" ? null : v });
    }, 400);
  };

  return (
    <section className="bg-white border border-stone-200 rounded-xl p-4 mb-5">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-3 items-end">
        <div className="lg:col-span-2">
          <label className="block text-xs font-medium text-stone-600 mb-1" htmlFor="f-project">
            Proyecto
          </label>
          <select
            id="f-project"
            className={inputCls}
            value={filtros.project_id === null ? "" : String(filtros.project_id)}
            onChange={(e) =>
              actualizar({
                project_id: e.target.value === "" ? null : Number(e.target.value),
              })
            }
          >
            <option value="">Todos los proyectos</option>
            {proyectos.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-stone-600 mb-1" htmlFor="f-env">
            Environment
          </label>
          <select
            id="f-env"
            className={inputCls}
            value={filtros.environment ?? ""}
            onChange={(e) =>
              actualizar({ environment: e.target.value === "" ? null : (e.target.value as Entorno) })
            }
          >
            <option value="">Todos</option>
            {ENTORNOS.map((env) => (
              <option key={env} value={env}>
                {env}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-stone-600 mb-1" htmlFor="f-level">
            Level
          </label>
          <select
            id="f-level"
            className={inputCls}
            value={filtros.level ?? ""}
            onChange={(e) =>
              actualizar({ level: e.target.value === "" ? null : (e.target.value as Nivel) })
            }
          >
            <option value="">Todos</option>
            {NIVELES.map((lvl) => (
              <option key={lvl} value={lvl}>
                {lvl}
              </option>
            ))}
          </select>
        </div>
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label className="block text-xs font-medium text-stone-600 mb-1" htmlFor="f-from">
              Desde
            </label>
            <input
              id="f-from"
              type="datetime-local"
              className="w-full border border-stone-300 rounded-lg px-2 py-2 text-xs mono"
              value={aValorDatetimeLocal(filtros.from)}
              onChange={(e) => actualizar({ from: e.target.value === "" ? null : e.target.value })}
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-stone-600 mb-1" htmlFor="f-to">
              Hasta
            </label>
            <input
              id="f-to"
              type="datetime-local"
              className="w-full border border-stone-300 rounded-lg px-2 py-2 text-xs mono"
              value={aValorDatetimeLocal(filtros.to)}
              onChange={(e) => actualizar({ to: e.target.value === "" ? null : e.target.value })}
            />
          </div>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={reiniciar}
            className="flex-1 border border-stone-300 bg-white hover:bg-stone-50 text-stone-700 text-sm font-medium rounded-lg px-3 py-2"
          >
            Reiniciar
          </button>
        </div>
        <div className="sm:col-span-2 lg:col-span-6">
          <label className="block text-xs font-medium text-stone-600 mb-1" htmlFor="f-text">
            Texto del mensaje
          </label>
          <input
            id="f-text"
            type="search"
            placeholder="p. ej. upstream, timeout, NullPointerException…"
            className="w-full border border-stone-300 rounded-lg px-2.5 py-2 text-sm"
            value={texto}
            onChange={(e) => alCambiarTexto(e.target.value)}
          />
        </div>
      </div>
    </section>
  );
}