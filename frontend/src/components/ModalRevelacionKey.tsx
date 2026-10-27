// Modal de revelación ÚNICA de API key (R8.40/V8/E8.41–E8.43): key completa
// plk_… + aviso LITERAL "No se volverá a mostrar; cópiala ahora." + Copiar +
// Cerrar. La key vive SOLO aquí (prop del momento); NUNCA entra al estado
// global ni al storage — al cerrar queda solo el prefix en la ficha (R8.40).
import { useState } from "react";
import { Modal } from "./Modal";

async function copiarAlPortapapeles(texto: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(texto);
    return true;
  } catch {
    // fallback: selección temporal (permiso de clipboard puede faltar)
    try {
      const ta = document.createElement("textarea");
      ta.value = texto;
      document.body.appendChild(ta);
      ta.select();
      const ok = document.execCommand("copy");
      document.body.removeChild(ta);
      return ok;
    } catch {
      return false;
    }
  }
}

export interface ModalRevelacionKeyProps {
  abierto: boolean;
  /** Key COMPLETA del 201 — solo para esta ventana. */
  apiKey: string;
  alCerrar: () => void;
}

export function ModalRevelacionKey({
  abierto,
  apiKey,
  alCerrar,
}: ModalRevelacionKeyProps) {
  const [copiado, setCopiado] = useState(false);

  const cerrar = () => {
    setCopiado(false);
    alCerrar(); // el padre descarta la key (solo queda el prefix — R8.40)
  };

  const copiar = async () => {
    const ok = await copiarAlPortapapeles(apiKey);
    setCopiado(ok);
  };

  return (
    <Modal abierto={abierto} titulo="Nueva API key generada" tamano="max-w-lg" alCerrar={cerrar}>
      <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2 mb-4 text-xs">
        No se volverá a mostrar; cópiala ahora. La key anterior ya está invalidada para todas las
        apps de este proyecto.
      </p>
      <div className="flex items-center gap-2 bg-stone-50 border border-stone-300 rounded-lg px-3 py-2.5 mb-4">
        <code className="mono text-xs break-all select-all">{apiKey}</code>
      </div>
      {copiado && (
        <p className="text-xs text-green-700 mb-4" role="status">
          Copiada al portapapeles.
        </p>
      )}
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={copiar}
          className="bg-teal-700 hover:bg-teal-800 text-white text-sm font-medium rounded-lg px-4 py-2"
        >
          Copiar
        </button>
        <button
          type="button"
          onClick={cerrar}
          className="border border-stone-300 rounded-lg px-3 py-2 text-sm hover:bg-stone-50"
        >
          He copiado la key; cerrar
        </button>
      </div>
    </Modal>
  );
}