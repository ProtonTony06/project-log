// Banner de estado/errores (D-C8-8 — R8.17): literales code+message TAL CUAL
// (nunca traducidos ni suavizados). Variantes: info / ámbar (502-proxy,
// E8.18) / rojo (500, E8.17) / neutral. Soporte Reintentar según §5.
import type { ReactNode } from "react";

export type VarianteBanner = "info" | "ambar" | "rojo" | "neutral";

const CLASES: Record<VarianteBanner, string> = {
  info: "border-teal-200 bg-teal-50 text-teal-800",
  ambar: "border-amber-200 bg-amber-50 text-amber-800",
  rojo: "border-red-200 bg-red-50 text-red-700",
  neutral: "border-stone-200 bg-stone-100 text-stone-600",
};

export interface BannerProps {
  variante: VarianteBanner;
  /** Literal tal cual (p. ej. "filter_from_to_inverted"); sin code ⇒ null. */
  code?: string | null;
  /** Literal tal cual. Pueden venir enriquecidos (children tras el texto). */
  message: string;
  alReintentar?: () => void;
  /** Extra opcional bajo el mensaje (p. ej. campo culpable). */
  children?: ReactNode;
}

export function Banner({
  variante,
  code,
  message,
  alReintentar,
  children,
}: BannerProps) {
  return (
    <div
      role={variante === "rojo" ? "alert" : "status"}
      className={`flex flex-wrap items-center gap-3 border rounded-lg px-3 py-2.5 text-sm ${CLASES[variante]}`}
    >
      <div className="flex-1 min-w-[240px]">
        <p>
          {code != null && (
            <span className="mono font-medium">{code} · </span>
          )}
          {message}
        </p>
        {children != null && (
          <div className="mt-1 text-xs opacity-90">{children}</div>
        )}
      </div>
      {alReintentar != null && (
        <button
          type="button"
          onClick={alReintentar}
          className="border border-current bg-white/60 hover:bg-white text-xs font-medium rounded-lg px-3 py-1.5"
        >
          Reintentar
        </button>
      )}
    </div>
  );
}