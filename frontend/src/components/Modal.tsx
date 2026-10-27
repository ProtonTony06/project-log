// Modal genérico (D-C8-9): overlay stone-900/50 + caja blanca rounded-xl
// shadow-xl; compartido por 8a (eliminar proyecto), 8b (borrar log) y
// revelación de key. Exporta el comparador EXACTO V10/V11 (E8.37/E8.47c):
// SOLO trim() === trim() — sin lowercasing, sin normalización de espacios
// internos.
import { useEffect } from "react";
import type { ReactNode } from "react";

export function coincideLiteralConTrim(esperado: string, texto: string): boolean {
  return texto.trim() === esperado.trim();
}

export interface ModalProps {
  abierto: boolean;
  titulo: string;
  alCerrar: () => void;
  tamano?: "max-w-md" | "max-w-lg";
  children: ReactNode;
}

export function Modal({
  abierto,
  titulo,
  alCerrar,
  tamano = "max-w-md",
  children,
}: ModalProps) {
  useEffect(() => {
    if (!abierto) return;
    const alTecla = (e: KeyboardEvent) => {
      if (e.key === "Escape") alCerrar();
    };
    window.addEventListener("keydown", alTecla);
    return () => window.removeEventListener("keydown", alTecla);
  }, [abierto, alCerrar]);

  if (!abierto) return null;

  return (
    <div
      className="fixed inset-0 z-50 overflow-y-auto"
      role="presentation"
      onClick={(e) => {
        if (e.target === e.currentTarget) alCerrar();
      }}
    >
      <div className="fixed inset-0 bg-stone-900/50" aria-hidden="true" />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        className={`relative mx-auto my-8 ${tamano} bg-white rounded-xl shadow-xl p-6`}
      >
        <h3 id="modal-title" className="font-semibold mb-2">
          {titulo}
        </h3>
        {children}
      </div>
    </div>
  );
}