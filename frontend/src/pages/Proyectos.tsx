// /proyectos (maqueta 04 — R8.37/E8.39–E8.40 + flujo 9 C10): tarjetas con
// resumen vía `GET /projects?include=summary` (REQ-C10-UI-6); archivados
// VISIBLES con toggle (histórico consultable, resumen intacto); estado vacío
// ≠ error (E8.40); esqueleto y banner con Reintentar; banner de `aviso` de
// location.state (regreso post-8a — D-C10-8) y botón «Actualizar» que
// re-invoca cargar() (flujo 9 paso 5: el resumen NO se recalcula en vivo).
import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { listaProyectosConResumen } from "../api/panel";
import { ApiError } from "../api/client";
import type { ProjectDto } from "../api/types";
import { TarjetaProyecto } from "../components/TarjetaProyecto";
import { EsqueletoTarjetas } from "../components/Esqueleto";
import { Banner } from "../components/Banner";

export default function Proyectos() {
  const location = useLocation();
  // Aviso de la navegación previa (post-8a: «Proyecto y sus logs eliminados
  // definitivamente»; también «No existe (quizás fue eliminado)» — D-C10-8).
  const aviso = (location.state as { aviso?: string } | null)?.aviso ?? null;
  const [proyectos, setProyectos] = useState<ProjectDto[] | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [cargando, setCargando] = useState(true);
  const [mostrarArchivados, setMostrarArchivados] = useState(true);

  const cargar = () => {
    setCargando(true);
    setError(null);
    listaProyectosConResumen()
      .then((ps) => {
        setProyectos(ps);
        setCargando(false);
      })
      .catch((err) => {
        setError(
          err instanceof ApiError ? err : new ApiError(0, undefined, "HTTP 0"),
        );
        setCargando(false);
      });
  };

  useEffect(cargar, []);

  const visibles = (proyectos ?? []).filter((p) =>
    mostrarArchivados ? true : p.status === "ACTIVE",
  );
  const archivados = (proyectos ?? []).filter((p) => p.status === "ARCHIVED").length;

  return (
    <main className="max-w-7xl mx-auto px-4 py-6">
      <div className="flex flex-wrap items-center gap-3 mb-5">
        <h1 className="text-lg font-semibold tracking-tight">Proyectos</h1>
        <p className="text-xs text-stone-500 mono">
          GET /projects?include=summary · {proyectos === null ? "…" : `${proyectos.length} proyectos · ${archivados} archivados`}
        </p>
        <Link
          to="/proyectos/nuevo"
          className="bg-teal-700 hover:bg-teal-800 text-white text-sm font-medium rounded-lg px-4 py-2"
        >
          + Nuevo proyecto
        </Link>
        <button
          type="button"
          onClick={cargar}
          // flujo 9 paso 5 (REQ-C10-RESUMEN-7): el resumen se refresca al
          // recargar o con Actualizar — nunca en vivo por WebSocket.
          className="ml-auto border border-stone-300 bg-white hover:bg-stone-50 text-stone-700 text-sm font-medium rounded-lg px-4 py-2"
        >
          Actualizar
        </button>
      </div>

      {aviso !== null && (
        <div className="mb-5" role="status">
          <Banner variante="info" message={aviso} />
        </div>
      )}

      <label className="flex items-center gap-2 text-xs text-stone-600 mb-5">
        <input
          type="checkbox"
          checked={mostrarArchivados}
          onChange={(e) => setMostrarArchivados(e.target.checked)}
        />
        Mostrar archivados ({archivados} · históricos consultables, nunca borrados)
      </label>

      {error !== null && (
        <div className="mb-5">
          <Banner
            variante="rojo"
            code={error.code ?? null}
            message={`HTTP ${error.status} · ${error.message}`}
            alReintentar={cargar}
          />
        </div>
      )}

      {cargando && proyectos === null && <EsqueletoTarjetas />}

      {/* E8.40: estado vacío legítimo ≠ error — con CTA propio. */}
      {!cargando && error === null && proyectos !== null && proyectos.length === 0 && (
        <section className="bg-white border border-stone-200 rounded-xl p-8 text-center">
          <p className="text-stone-700 mb-1">No hay proyectos todavía.</p>
          <p className="text-xs text-stone-500 mb-4">
            Crea el primero y genera su API key para que las apps puedan ingestar.
          </p>
          <Link
            to="/proyectos/nuevo"
            className="inline-block bg-teal-700 hover:bg-teal-800 text-white text-sm font-medium rounded-lg px-4 py-2"
          >
            + Nuevo proyecto
          </Link>
        </section>
      )}

      {!cargando && error === null && proyectos !== null && proyectos.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {visibles.map((p) => (
            <TarjetaProyecto key={p.id} proyecto={p} />
          ))}
        </div>
      )}
    </main>
  );
}