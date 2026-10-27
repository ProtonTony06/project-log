// Indicador de conexión del WS (V2 — R8.28): SOLO se renderiza en /logs
// (S8-4/R8.29 — el TopBar lo recibe como slot y /proyectos no pasa nada).
// Literales: "Conectado" / "Desconectado" / "Reconectando (intento N)…".
// JAMÁS expone tokens ni códigos close (R8.30 — sin oráculo).
import type { EstadoWs } from "../ws/client";

export function EstadoConexion({
  estado,
  intento,
}: {
  estado: EstadoWs;
  intento: number;
}) {
  if (estado === "conectado") {
    return (
      <span
        className="flex items-center gap-1.5 font-medium text-green-700"
        title="WebSocket /ws/logs con JWT válido; set_filters enviado"
      >
        <span className="w-2 h-2 rounded-full bg-green-600" /> Conectado
      </span>
    );
  }
  if (estado === "reconectando") {
    return (
      <span
        className="flex items-center gap-1.5 font-medium text-amber-700"
        title="Backoff 1 s → 30 s; el listado sigue por REST"
      >
        <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
        Reconectando (intento {intento})…
      </span>
    );
  }
  return (
    <span
      className="flex items-center gap-1.5 font-medium text-stone-500"
      title="Streaming inactivo: los datos en pantalla no se actualizan en vivo"
    >
      <span className="w-2 h-2 rounded-full border border-stone-400" />
      Desconectado
    </span>
  );
}