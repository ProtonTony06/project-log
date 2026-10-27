// /proyectos/nuevo (R8.42/E8.45 — flujo 2 SIN maqueta, coherente con el
// sistema visual): name 1..200 + description ≤2000; 201 → redirect a la
// ficha con aviso; 409 name_duplicate → inline SIN perder datos; 400 →
// errores POR CAMPO; "Creando…" deshabilitado (anti doble-submit).
import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { crearProyecto } from "../api/panel";
import { ApiError } from "../api/client";
import { Banner } from "../components/Banner";

export default function ProyectoNuevo() {
  const navegar = useNavigate();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [creando, setCreando] = useState(false);
  const [errorCampo, setErrorCampo] = useState<{ field: string; message: string } | null>(null);
  const [errorGeneral, setErrorGeneral] = useState<ApiError | null>(null);

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    if (creando) return;
    setCreando(true);
    setErrorCampo(null);
    setErrorGeneral(null);
    try {
      const p = await crearProyecto({
        name: name,
        description: description === "" ? undefined : description,
      });
      navegar(`/proyectos/${p.id}`, {
        state: {
          aviso: "Proyecto creado; genera su API key para que las apps puedan ingestar",
        },
      });
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setErrorCampo({ field: "name", message: "Ya existe un proyecto con ese nombre" });
      } else if (err instanceof ApiError && err.field != null) {
        // 400 con campo culpable: error pegado AL CAMPO (§5/E8.45)
        setErrorCampo({ field: err.field, message: err.message });
      } else if (err instanceof ApiError) {
        setErrorGeneral(err);
      } else {
        setErrorGeneral(new ApiError(0, undefined, "HTTP 0"));
      }
    } finally {
      setCreando(false);
    }
  };

  const claseInput = (campo: string) =>
    "w-full border rounded-lg px-3 py-2 text-sm " +
    (errorCampo?.field === campo ? "border-red-300 bg-red-50/40" : "border-stone-300");

  return (
    <main className="max-w-2xl mx-auto px-4 py-6">
      <div className="mb-5">
        <Link to="/proyectos" className="text-sm text-teal-700 hover:underline">
          ← Volver a proyectos
        </Link>
      </div>

      <h1 className="text-lg font-semibold tracking-tight mb-1">Nuevo proyecto</h1>
      <p className="text-sm text-stone-500 mb-5">
        POST /projects · name único (1..200), description opcional (≤2000).
      </p>

      {errorGeneral !== null && (
        <div className="mb-5">
          <Banner
            variante="rojo"
            code={errorGeneral.code ?? null}
            message={`HTTP ${errorGeneral.status} · ${errorGeneral.message}`}
          />
        </div>
      )}

      <form onSubmit={enviar} className="bg-white border border-stone-200 rounded-xl p-5 space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1" htmlFor="p-name">
            Name
          </label>
          <input
            id="p-name"
            type="text"
            value={name}
            maxLength={200}
            autoFocus
            spellCheck={false}
            onChange={(e) => setName(e.target.value)}
            className={claseInput("name")}
            aria-invalid={errorCampo?.field === "name"}
          />
          <p className="text-xs text-stone-500 mt-1">
            Único, 1..200 caracteres. Identificador literal para las apps y X-Confirm-Name.
          </p>
          {errorCampo?.field === "name" && (
            <p role="alert" className="text-xs text-red-700 mt-1">
              {errorCampo.message}
            </p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium mb-1" htmlFor="p-desc">
            Description
          </label>
          <textarea
            id="p-desc"
            value={description}
            maxLength={2000}
            rows={3}
            onChange={(e) => setDescription(e.target.value)}
            className={claseInput("description")}
            aria-invalid={errorCampo?.field === "description"}
          />
          <p className="text-xs text-stone-500 mt-1">{description.length}/2000</p>
          {errorCampo?.field === "description" && (
            <p role="alert" className="text-xs text-red-700 mt-1">
              {errorCampo.message}
            </p>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Link
            to="/proyectos"
            className="border border-stone-300 bg-white hover:bg-stone-50 rounded-lg px-3 py-2 text-sm"
          >
            Cancelar
          </Link>
          <button
            type="submit"
            disabled={creando || name.trim() === ""}
            className={
              "text-white text-sm font-medium rounded-lg px-4 py-2 " +
              (creando || name.trim() === ""
                ? "bg-teal-400 cursor-not-allowed"
                : "bg-teal-700 hover:bg-teal-800")
            }
          >
            {creando ? "Creando…" : "Crear proyecto"}
          </button>
        </div>
      </form>
    </main>
  );
}