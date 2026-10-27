// Tabla densa de logs (V6 — R8.20/R8.24): 8 columnas de la maqueta 02,
// SIN exception ni metadata (esos viven SOLO en el detalle). Timestamps UTC
// mono tal cual (S8-9 — sin reformato de zona). FATAL/ERROR/WARN con borde
// izquierdo según maqueta. Nombre de proyecto desde Map (S8-6: id CRUDO si
// desconocido — JAMÁS vacío).
import type { LogListItemDto, Nivel } from "../api/types";

const BORDE_NIVEL: Partial<Record<Nivel, string>> = {
  FATAL: "border-l-2 border-l-red-700",
  ERROR: "border-l-2 border-l-red-300",
  WARN: "border-l-2 border-l-amber-400",
};

export interface TablaLogsProps {
  items: LogListItemDto[];
  /** Map project_id → name (S8-6); desconocido ⇒ String(id). */
  nombresProyectos: Map<number, string>;
  alVerDetalle: (id: number) => void;
}

export function TablaLogs({ items, nombresProyectos, alVerDetalle }: TablaLogsProps) {
  return (
    <section className="bg-white border border-stone-200 rounded-xl overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm min-w-[960px]">
          <thead>
            <tr className="text-left text-xs text-stone-500 border-b border-stone-200 bg-stone-50">
              <th className="py-2.5 pl-4 pr-2 font-medium">Level</th>
              <th className="py-2.5 px-2 font-medium">Timestamp (UTC)</th>
              <th className="py-2.5 px-2 font-medium">Env</th>
              <th className="py-2.5 px-2 font-medium">Proyecto</th>
              <th className="py-2.5 px-2 font-medium">Source</th>
              <th className="py-2.5 px-2 font-medium">Mensaje</th>
              <th className="py-2.5 px-2 font-medium">Correlación</th>
              <th className="py-2.5 pr-4 pl-2 font-medium" aria-label="Acciones" />
            </tr>
          </thead>
          <tbody className="divide-y divide-stone-100">
            {items.map((log) => (
              <tr
                key={log.id}
                className={(BORDE_NIVEL[log.level] ?? "") + " hover:bg-stone-50"}
              >
                <td className="py-2 pl-4 pr-2">
                  <span className={`lv lv-${log.level}`}>{log.level}</span>
                </td>
                <td className="py-2 px-2 mono text-xs whitespace-nowrap">{log.timestamp}</td>
                <td className="py-2 px-2">
                  <span className={`env env-${log.environment}`}>{log.environment}</span>
                </td>
                <td className="py-2 px-2 whitespace-nowrap">
                  {nombresProyectos.get(log.project_id) ?? String(log.project_id)}
                </td>
                <td className="py-2 px-2 mono text-xs text-stone-500">
                  {log.source ?? "—"}
                </td>
                <td className="py-2 px-2 max-w-md">
                  <span className="block truncate">{log.message}</span>
                </td>
                <td className="py-2 px-2 mono text-xs text-stone-500">
                  {log.correlation_id ?? "—"}
                </td>
                <td className="py-2 pr-4 pl-2 text-right">
                  <button
                    type="button"
                    onClick={() => alVerDetalle(log.id)}
                    className="text-xs text-teal-700 hover:underline"
                  >
                    Detalle
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}