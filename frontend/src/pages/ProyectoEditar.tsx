// /proyectos/:id/editar (R8.43/E8.46 — flujo 2): precarga GET /projects/{id};
// PATCH con SOLO campos modificados → 200 → ficha con confirmación "Cambios
// guardados" y updated_at refrescado; 409/400 → errores por campo; 404 → §5
// y retorno a /proyectos. JAMÁS control de status aquí (van en la ficha).
import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { editarProyecto, proyecto as proyectoApi } from "../api/panel";
import { ApiError } from "../api/client";
import { Banner } from "../components/Banner";

export default function ProyectoEditar() {
  const { id } = useParams();
  const idNum = Number(id);
  const navegar = useNavigate();

  const [name, setName] = useState<string | null>(null); // null = precargando
  const [nombreOriginal, setNombreOriginal] = useState("");
  const [description, setDescription] = useState<string | null>(null);
  const [descOriginal, setDescOriginal] = useState<string | null>(null);
  const [noExiste, setNoExiste] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [errorCampo, setErrorCampo] = useState<{ field: string; message: string } | null>(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    if (!Number.isFinite(idNum) || Number.isNaN(idNum)) {
      setNoExiste(true);
      return;
    }
    proyectoApi(idNum)
      .then((p) => {
        setName(p.name);
        setNombreOriginal(p.name);
        setDescription(p.description ?? "");
        setDescOriginal(p.description ?? null);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) setNoExiste(true);
        else setError(err instanceof ApiError ? err : new ApiError(0, undefined, "HTTP 0"));
      });
  }, [idNum]);

  if (noExiste || (error !== null && name === null)) {
    return (
      <main className="max-w-2xl mx-auto px-4 py-6" role="alert">
        <div className="mb-5">
          <Banner
            variante="rojo"
            code={error?.code ?? null}
            message={
              noExiste
                ? "Este proyecto no existe (o fue eliminado)"
                : `HTTP ${error?.status} · ${error?.message}`
            }
          />
        </div>
        <button
          type="button"
          onClick={() => navegar("/proyectos")}
          className="border border-stone-300 bg-white hover:bg-stone-50 rounded-lg px-3 py-2 text-sm"
        >
          Volver a proyectos
        </button>
      </main>
    );
  }

  if (name === null || description === null) {
    return (
      <main className="max-w-2xl mx-auto px-4 py-6">
        <p className="text-sm text-stone-500">Cargando proyecto…</p>
      </main>
    );
  }

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    if (guardando) return;
    setGuardando(true);
    setErrorCampo(null);
    setError(null);
    // PATCH con SOLO campos modificados (R8.43) — nunca el status.
    const cambios: { name?: string; description?: string | null } = {};
    if (name !== nombreOriginal) cambios.name = name;
    if (description !== (descOriginal ?? "")) {
      cambios.description = description === "" ? null : description;
    }
    if (Object.keys(cambios).length === 0) {
      navegar(`/proyectos/${idNum}`, { state: { aviso: "Cambios guardados" } });
      return;
    }
    try {
      await editarProyecto(idNum, cambios);
      navegar(`/proyectos/${idNum}`, { state: { aviso: "Cambios guardados" } });
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        navegar("/proyectos");
      } else if (err instanceof ApiError && err.field != null) {
        setErrorCampo({ field: err.field, message: err.message });
      } else if (err instanceof ApiError) {
        setError(err);
      } else {
        setError(new ApiError(0, undefined, "HTTP 0"));
      }
    } finally {
      setGuardando(false);
    }
  };

  const claseInput = (campo: string) =>
    "w-full border rounded-lg px-3 py-2 text-sm " +
    (errorCampo?.field === campo ? "border-red-300 bg-red-50/40" : "border-stone-300");

  return (
    <main className="max-w-2xl mx-auto px-4 py-6">
      <div className="mb-5">
        <Link to={`/proyectos/${idNum}`} className="text-sm text-teal-700 hover:underline">
          ← Volver a la ficha
        </Link>
      </div>

      <h1 className="text-lg font-semibold tracking-tight mb-1">
        Editar proyecto <span className="mono text-xs text-stone-400">#{idNum}</span>
      </h1>
      <p className="text-sm text-stone-500 mb-5">
        PATCH /projects/{idNum} · solo los campos modificados. El status se cambia desde la ficha.
      </p>

      {error !== null && (
        <div className="mb-5">
          <Banner
            variante="rojo"
            code={error.code ?? null}
            message={`HTTP ${error.status} · ${error.message}`}
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
            spellCheck={false}
            onChange={(e) => setName(e.target.value)}
            className={claseInput("name")}
            aria-invalid={errorCampo?.field === "name"}
          />
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
            value={description ?? ""}
            maxLength={2000}
            rows={3}
            onChange={(e) => setDescription(e.target.value)}
            className={claseInput("description")}
            aria-invalid={errorCampo?.field === "description"}
          />
          <p className="text-xs text-stone-500 mt-1">{(description ?? "").length}/2000</p>
          {errorCampo?.field === "description" && (
            <p role="alert" className="text-xs text-red-700 mt-1">
              {errorCampo.message}
            </p>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={() => navegar(`/proyectos/${idNum}`)}
            className="border border-stone-300 bg-white hover:bg-stone-50 rounded-lg px-3 py-2 text-sm"
          >
            Cancelar
          </button>
          <button
            type="submit"
            disabled={guardando || name.trim() === ""}
            className={
              "text-white text-sm font-medium rounded-lg px-4 py-2 " +
              (guardando || name.trim() === ""
                ? "bg-teal-400 cursor-not-allowed"
                : "bg-teal-700 hover:bg-teal-800")
            }
          >
            {guardando ? "Guardando…" : "Guardar cambios"}
          </button>
        </div>
      </form>
    </main>
  );
}