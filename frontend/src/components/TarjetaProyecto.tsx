// Tarjeta de proyecto (R8.37/E8.39, maqueta 04): name, description,
// api_key_prefix + has_key; STATUS literal "Activo"|"Archivado"; CUANDO el
// `summary` viaja (GET /projects?include=summary — REQ-C10-UI-6, flujo 9)
// se renderiza la franja de resumen DENTRO del <button> íntegro como texto
// plano (ResumenLogs — nada clicable nuevo, la navegación por clic intacta);
// sin summary la tarjeta queda como siempre (degradación honesta, R8.18 C9).
// Archivada con opacidad (histórico consultable) y resumen INTACTO.
// Clic → /proyectos/:id (S8-6/flujo 8).
import { useNavigate } from "react-router-dom";
import type { ProjectDto } from "../api/types";
import { ResumenLogs } from "./ResumenLogs";

export function TarjetaProyecto({ proyecto }: { proyecto: ProjectDto }) {
  const navegar = useNavigate();
  const archivado = proyecto.status === "ARCHIVED";
  return (
    <button
      type="button"
      onClick={() => navegar(`/proyectos/${proyecto.id}`)}
      className={
        "block w-full text-left bg-white border rounded-xl p-4 transition-colors " +
        (archivado
          ? "border-stone-200 opacity-80 hover:opacity-100 hover:border-stone-400 transition-all"
          : "border-stone-200 hover:border-stone-400")
      }
    >
      <div className="flex items-start justify-between gap-2 mb-2">
        <div>
          <h2 className="font-semibold">{proyecto.name}</h2>
          {proyecto.description != null && (
            <p className="text-xs text-stone-500 mt-0.5">{proyecto.description}</p>
          )}
        </div>
        <span
          className={
            "text-xs font-medium rounded-md px-2 py-0.5 shrink-0 " +
            (archivado
              ? "text-stone-600 bg-stone-200 border border-stone-300"
              : "text-green-700 bg-green-50 border border-green-200")
          }
        >
          {archivado ? "Archivado" : "Activo"}
        </span>
      </div>
      <p className="mono text-xs text-stone-500 mb-3">
        {proyecto.api_key_prefix ?? "sin key"} · has_key: {proyecto.has_key}
      </p>
      {/* Franja de resumen (maqueta 04) — SOLO con summary presente (include). */}
      {proyecto.summary != null && (
        <div className="mb-3">
          <ResumenLogs resumen={proyecto.summary} variante="franja" />
        </div>
      )}
      <div className="flex items-center justify-between text-xs text-stone-500 border-t border-stone-100 pt-2">
        <span className="mono">#{proyecto.id}</span>
        <span>
          actualizado: <span className="mono">{proyecto.updated_at}</span>
        </span>
      </div>
    </button>
  );
}