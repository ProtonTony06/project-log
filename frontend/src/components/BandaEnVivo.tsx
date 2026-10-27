// Banda EN VIVO (V5 — R8.25/R8.26): borde discontinuo verde sobre la tabla;
// cada frame batch del WS entra como UN grupo que marca los recién llegados
// (1 frame por batch, orden del lote). Sin batch visible ⇒ banda vacía.
// Literales de la maqueta 02: "● En vivo" + batch_id · N logs · hora.
import type { ReactNode } from "react";

export interface BandaEnVivoProps {
  /** true ⇒ la banda existe (WS conectado) aunque aún sin batch. */
  activa: boolean;
  /** batch_id del último frame recibido (tal cual, S8-7). */
  batchId: string | null;
  /** Logs del grupo recién llegado (conteo literal). */
  nuevos: number;
  /** received_at del último frame tal cual (mono, sin reformatar). */
  recibido: string | null;
  children?: ReactNode;
}

export function BandaEnVivo({
  activa,
  batchId,
  nuevos,
  recibido,
  children,
}: BandaEnVivoProps) {
  if (!activa) return null;
  return (
    <section className="mb-6 border border-dashed border-green-500/60 rounded-xl bg-green-50/60 overflow-hidden">
      <div className="flex items-center justify-between flex-wrap gap-2 px-4 py-2 bg-green-100/50 border-b border-green-500/40">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-green-800 uppercase tracking-wider">
            ● En vivo
          </span>
          {batchId !== null && (
            <span className="text-xs text-green-800 mono">
              batch_id {batchId} · {nuevos} logs · insertado {recibido}
            </span>
          )}
          {batchId === null && (
            <span className="text-xs text-green-700 mono">
              esperando frames… (set_filters enviado)
            </span>
          )}
        </div>
        <span className="text-xs text-green-700 mono hidden lg:inline">
          {'frame: {"type":"batch"} · tras COMMIT · deduplicado por id'}
        </span>
      </div>
      {children != null && (
        <ul className="divide-y divide-green-500/20 text-sm">{children}</ul>
      )}
    </section>
  );
}