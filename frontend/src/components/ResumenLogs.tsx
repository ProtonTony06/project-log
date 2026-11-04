// Resumen de logs (flujo 9 — maquetas 04/05, D-C10-9): componente COMPARTIDO
// por la tarjeta (variante "franja") y la ficha (variante "bloque").
// TEXTO PLANO PURO: solo <span>s y texto — NADA clicable nuevo, porque la
// TarjetaProyecto es un <button> íntegro y la navegación por clic NO se rompe
// (REQ-C10-UI-6/R7 del proposal). Badges REUTILIZADOS por referencia:
// BadgeSeveridad (`lv lv-<level>`) y PildoraEntorno (`env env-<env>`), con la
// paleta ya existente en index.css (04_UIUX §2 — sin color nuevo).
// Estado vacío (total === 0): «sin logs todavía» como texto informativo, NUNCA
// un error (M4/E8.32 — la franja/bloque se vacía, no falla).
// Archivado: cubierto por la opacidad ya existente del botón (sin rama
// especial — resumen INTACTO, flujo 9 paso 3).
// El ORDEN de las bandas es el del JSON recibido (solo claves PRESENTES,
// REQ-C10-RESUMEN-4): una sola fuente del orden — el CASE del SQL del backend
// (D-C10-3). La UI jamás reordena (evita una segunda fuente del orden).
import type { Entorno, SummaryDto, Nivel } from "../api/types";
import { BadgeSeveridad } from "./BadgeSeveridad";
import { PildoraEntorno } from "./PildoraEntorno";

export type VarianteResumen = "franja" | "bloque";

/** «25 330», «1 000» — separador de miles con espacio (maqueta 04). */
function formatear(n: number): string {
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
}

export function ResumenLogs({
  resumen,
  variante,
}: {
  resumen: SummaryDto;
  variante: VarianteResumen;
}) {
  if (resumen.total === 0) {
    return (
      <span className="text-xs text-stone-400 italic">sin logs todavía</span>
    );
  }

  // Los mapas viajan con SOLO claves presentes; el orden en JSON es el del
  // backend (CASE del SQL — determinista, D-C10-3). Cero reordenado aquí.
  const niveles = Object.entries(resumen.by_level) as [Nivel, number][];
  const entornos = Object.entries(resumen.by_environment) as [Entorno, number][];

  return (
    <div>
      {/* Bloque de ficha (maqueta 05): total grande + último received_at. */}
      {variante === "bloque" && (
        <div className="flex flex-wrap items-baseline gap-2 mb-3">
          <span className="text-2xl font-semibold">
            {formatear(resumen.total)}
          </span>
          <span className="text-xs text-stone-500">
            logs totales · último <span className="mono">{resumen.last_log_at ?? "—"}</span>
          </span>
        </div>
      )}

      {/* Bandas de severidad (FATAL primero en el JSON del backend). */}
      {niveles.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-2">
          {niveles.map(([nivel, conteo]) => (
            <BadgeSeveridad
              key={nivel}
              level={nivel}
              texto={`${nivel} ${formatear(conteo)}`}
            />
          ))}
        </div>
      )}

      {/* Bandas de entorno (orden alfabético en el JSON del backend). */}
      {entornos.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {entornos.map(([entorno, conteo]) => (
            <PildoraEntorno
              key={entorno}
              environment={entorno}
              texto={`${entorno} ${formatear(conteo)}`}
            />
          ))}
        </div>
      )}

      {/* Franja de totals de la tarjeta (maqueta 04): tras border-t. */}
      {variante === "franja" && (
        <div className="flex items-center justify-between text-xs text-stone-500 border-t border-stone-100 pt-2 mt-2">
          <span>
            <span className="font-semibold text-stone-700">
              {formatear(resumen.total)}
            </span>{" "}
            logs
          </span>
          <span>
            último: <span className="mono">{resumen.last_log_at ?? "—"}</span>
          </span>
        </div>
      )}
    </div>
  );
}