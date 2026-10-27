// Modal 8b — eliminar log (R8.35–R8.36/D-C8-10, V11/E8.37): sentencia EXACTA
// `DELETE /logs/<id> · header X-Confirm-Log-Id: <id>` (id REAL), comparación
// coincideLiteralConTrim; botón bg-red-300 deshabilitado → bg-red-700 al
// coincidir. Al confirmar llama eliminarLog (D2): hoy 405/404 del backend ⇒
// §5 SIN fingir éxito (E8.38) — el error se muestra literal dentro del modal.
import { useState } from "react";
import { eliminarLog } from "../api/panel";
import { ApiError } from "../api/client";
import { Modal, coincideLiteralConTrim } from "./Modal";

export interface ModalBorrado8bProps {
  abierto: boolean;
  id: number;
  /** Nombre del proyecto para la frase literal (puede llegar del Map S8-6). */
  nombreProyecto: string;
  alCerrar: () => void;
}

export function ModalBorrado8b({
  abierto,
  id,
  nombreProyecto,
  alCerrar,
}: ModalBorrado8bProps) {
  const [texto, setTexto] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const sentencia = `DELETE /logs/${id} · header X-Confirm-Log-Id: ${id}`;
  const coincide = coincideLiteralConTrim(sentencia, texto);

  const confirmar = async () => {
    if (!coincide || enviando) return;
    setEnviando(true);
    setError(null);
    try {
      // Promise<never> POR DISEÑO (D2): 405/404 hoy — nunca se finge éxito.
      await eliminarLog(id, id);
    } catch (e) {
      if (e instanceof ApiError) setError(e);
      else setError(new ApiError(0, undefined, "HTTP 0"));
    } finally {
      setEnviando(false);
    }
  };

  const cerrar = () => {
    setTexto("");
    setError(null);
    setEnviando(false);
    alCerrar();
  };

  return (
    <Modal abierto={abierto} titulo="Eliminar registro" alCerrar={cerrar}>
      <p className="text-sm text-stone-600 mb-4">
        Se eliminará definitivamente el registro <span className="mono">#{id}</span> de{" "}
        <span className="font-medium">{nombreProyecto}</span>. Esta acción no se puede deshacer.
      </p>
      <p className="text-sm text-stone-700 mb-1">
        Para habilitar el botón, <span className="font-medium">escribe la sentencia exacta</span>{" "}
        que se enviará (se ignoran espacios al principio y al final):
      </p>
      <p className="text-xs text-stone-700 mb-2 mono bg-stone-100 border border-stone-200 rounded-lg px-2.5 py-1.5 select-all">
        {sentencia}
      </p>
      <label className="block mb-1 text-xs font-medium text-stone-600" htmlFor={`confirm-log-${id}`}>
        Sentencia de borrado
      </label>
      <input
        id={`confirm-log-${id}`}
        type="text"
        value={texto}
        spellCheck={false}
        onChange={(e) => setTexto(e.target.value)}
        className="w-full border border-stone-300 rounded-lg px-2.5 py-2 text-sm mono bg-white"
      />
      <p className="text-xs text-stone-500 mt-1.5 mb-4">
        Comparación literal con recorte de espacios en blanco (trim). El botón permanece{" "}
        <span className="font-medium">deshabilitado</span> hasta que el texto coincida exactamente
        con la sentencia.
      </p>

      {error !== null && (
        <div
          role="alert"
          className="mb-4 text-xs mono text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2"
        >
          HTTP {error.status}
          {error.code != null ? " · code: " + error.code : ""} · {error.message}
        </div>
      )}

      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={cerrar}
          className="border border-stone-300 rounded-lg px-3 py-2 text-sm hover:bg-stone-50"
        >
          Cancelar
        </button>
        <button
          type="button"
          disabled={!coincide || enviando}
          onClick={confirmar}
          title={
            coincide
              ? "Envía DELETE /logs/{id} con header X-Confirm-Log-Id"
              : "Se habilita cuando el texto coincide con la sentencia"
          }
          className={
            "text-white rounded-lg px-3 py-2 text-sm font-medium " +
            (coincide && !enviando ? "bg-red-700 hover:bg-red-800" : "bg-red-300 cursor-not-allowed")
          }
        >
          Eliminar registro
        </button>
      </div>
    </Modal>
  );
}