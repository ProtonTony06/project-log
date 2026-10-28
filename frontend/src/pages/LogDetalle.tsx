// Detalle de log (maqueta 03 — R8.32–R8.36/E8.34–E8.38): LogDto COMPLETO de
// 11 campos; bloques "Excepción y stacktrace" y "Metadata" SOLO si no nulos
// (se ocultan, no quedan vacíos); modal 8b con sentencia literal y trim;
// Volver a /logs conserva filtros (search en la URL — D-C8-6) y acumulado
// (store en memoria — E8.35 post-parche D-C8-8: el detalle es RUTA HERMANA;
// al montar aquí /logs se desmonta y su WS se cierra — decisión C).
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { listaProyectos, log } from "../api/panel";
import { ApiError } from "../api/client";
import type { ProjectDto } from "../api/types";
import { EsqueletoTabla } from "../components/Esqueleto";
import { ModalBorrado8b } from "../components/ModalBorrado8b";

function Copiar({
  texto,
  etiqueta = "Copiar",
}: {
  texto: string;
  etiqueta?: string;
}) {
  const [copiado, setCopiado] = useState(false);
  return (
    <button
      type="button"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(texto);
          setCopiado(true);
          setTimeout(() => setCopiado(false), 1500);
        } catch {
          setCopiado(false);
        }
      }}
      className="text-xs border border-stone-300 rounded-md px-2 py-0.5 bg-white hover:bg-stone-50"
    >
      {copiado ? "Copiado" : etiqueta}
    </button>
  );
}

export default function LogDetalle() {
  const { id } = useParams();
  const location = useLocation();
  const idNum = Number(id);
  const [dto, setDto] = useState<Awaited<ReturnType<typeof log>> | null>(null);
  const [cargando, setCargando] = useState(true);
  const [noExiste, setNoExiste] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modalAbierto, setModalAbierto] = useState(false);
  const [proyectos, setProyectos] = useState<ProjectDto[]>([]);

  const refCarga = useRef(0);
  useEffect(() => {
    if (!Number.isFinite(idNum) || id === undefined || Number.isNaN(idNum)) {
      setNoExiste(true);
      setCargando(false);
      return;
    }
    const miCarga = ++refCarga.current;
    setCargando(true);
    setNoExiste(false);
    setError(null);
    log(idNum)
      .then((r) => {
        if (miCarga !== refCarga.current) return;
        setDto(r);
        setCargando(false);
      })
      .catch((err) => {
        if (miCarga !== refCarga.current) return;
        setCargando(false);
        if (err instanceof ApiError && err.status === 404) {
          setNoExiste(true); // R8.34 — nunca como vacío
        } else if (err instanceof ApiError) {
          setError(`HTTP ${err.status}`);
        } else {
          setError("HTTP 0");
        }
      });
  }, [idNum, id]);

  useEffect(() => {
    // nombre del proyecto para cabecera (S8-6); fallo ⇒ id crudo.
    listaProyectos()
      .then(setProyectos)
      .catch(() => {});
  }, []);

  const nombreProyecto = useMemo(
    () => (dto !== null ? (proyectos.find((p) => p.id === dto.project_id)?.name ?? String(dto.project_id)) : ""),
    [dto, proyectos],
  );

  const metadataJson = useMemo(
    () => (dto?.metadata != null ? JSON.stringify(dto.metadata, null, 2) : null),
    [dto],
  );

  if (cargando) {
    return (
      <main className="max-w-6xl mx-auto px-4 py-6">
        <EsqueletoTabla filas={4} />
      </main>
    );
  }

  if (noExiste || error !== null) {
    return (
      <main className="max-w-6xl mx-auto px-4 py-6" role="alert">
        <div className="mb-5">
          <Link
            to={`/logs${location.search ? `?${location.search}` : ""}`}
            className="text-sm text-teal-700 hover:underline"
          >
            ← Volver a logs
          </Link>
        </div>
        <div className="bg-white border border-stone-200 rounded-xl p-8 text-center">
          <p className="text-stone-700 mb-2">
            {noExiste
              ? "Este registro no existe (o fue eliminado)" // R8.34 literal
              : `No se pudo cargar el registro · ${error}`}
          </p>
          {error !== null && (
            <p className="mono text-xs text-stone-400">{error}</p>
          )}
        </div>
      </main>
    );
  }

  if (dto === null) return null;

  return (
    <main className="max-w-6xl mx-auto px-4 py-6">
      <div className="flex flex-wrap items-center gap-3 mb-5">
        <Link
          to={`/logs${location.search ? `?${location.search}` : ""}`}
          className="text-sm text-teal-700 hover:underline"
        >
          ← Volver a logs (filtros y página intactos)
        </Link>
        <p className="text-xs text-stone-500 mono ml-auto">
          GET /logs/{dto.id} → LogDto completo
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-3 mb-6">
        <span className={`lv lv-${dto.level}`}>{dto.level}</span>
        <h1 className="text-lg font-semibold tracking-tight">
          Registro <span className="mono">#{dto.id}</span>
        </h1>
        <span className="text-xs text-stone-500">
          {nombreProyecto} · {dto.environment}
        </span>
      </div>

      {/* Cabecera: los 9 campos comunes (R8.32) */}
      <section className="bg-white border border-stone-200 rounded-xl p-5 mb-5">
        <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-x-6 gap-y-4 text-sm">
          <div>
            <dt className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-0.5">Level</dt>
            <dd><span className={`lv lv-${dto.level}`}>{dto.level}</span></dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-0.5">Environment</dt>
            <dd><span className={`env env-${dto.environment}`}>{dto.environment}</span></dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-0.5">Proyecto</dt>
            <dd>
              {nombreProyecto}{" "}
              <span className="mono text-xs text-stone-400">id {dto.project_id}</span>
            </dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-0.5">Source</dt>
            <dd className="mono text-xs">{dto.source ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-0.5">
              Timestamp (evento, app de origen)
            </dt>
            <dd className="mono text-xs">{dto.timestamp}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-0.5">
              Received_at (servidor, UTC)
            </dt>
            <dd className="mono text-xs">{dto.received_at}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-0.5">Id</dt>
            <dd className="mono text-xs">{dto.id}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-0.5">Correlation_id</dt>
            <dd className="flex items-center gap-2">
              <span className="mono text-xs select-all">{dto.correlation_id ?? "—"}</span>
              {dto.correlation_id != null && <Copiar texto={dto.correlation_id} />}
            </dd>
          </div>
        </dl>
      </section>

      {/* Message */}
      <section className="bg-white border border-stone-200 rounded-xl p-5 mb-5">
        <h2 className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-2">Message</h2>
        <p className="text-sm whitespace-pre-wrap">{dto.message}</p>
      </section>

      {/* Excepción y stacktrace — SOLO si exception != null (se oculta, no vacía) */}
      {dto.exception != null && (
        <section className="bg-white border border-stone-200 rounded-xl overflow-hidden mb-5">
          <div className="px-5 py-2.5 border-b border-stone-200 bg-stone-50 flex items-center justify-between">
            <h2 className="text-xs font-medium text-stone-500 uppercase tracking-wide">
              Excepción y stacktrace
            </h2>
            <Copiar texto={dto.exception} />
          </div>
          <pre className="mono text-xs leading-5 p-5 overflow-x-auto whitespace-pre">
            {dto.exception}
          </pre>
        </section>
      )}

      {/* Metadata — SOLO si != null (JSON formateado, <pre> mono) */}
      {metadataJson !== null && (
        <section className="bg-white border border-stone-200 rounded-xl overflow-hidden mb-5">
          <div className="px-5 py-2.5 border-b border-stone-200 bg-stone-50 flex items-center justify-between">
            <h2 className="text-xs font-medium text-stone-500 uppercase tracking-wide">
              Metadata (jsonb)
            </h2>
            <Copiar texto={metadataJson} />
          </div>
          <pre className="mono text-xs leading-5 p-5 overflow-x-auto">{metadataJson}</pre>
        </section>
      )}

      {/* Zona destructiva (P1, flujo 8b): separada de la lectura */}
      <section className="border border-red-200 bg-red-50 rounded-xl p-5 flex flex-wrap items-center gap-4">
        <div className="text-sm">
          <p className="font-medium text-red-900">Zona destructiva</p>
          <p className="text-xs text-red-800/80">
            Los registros son inmutables: solo se pueden eliminar, nunca editar. Operación
            irreversible (P1) con doble confirmación por sentencia literal.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setModalAbierto(true)}
          className="ml-auto bg-red-700 hover:bg-red-800 text-white text-sm font-medium rounded-lg px-4 py-2"
        >
          Eliminar registro
        </button>
      </section>

      {/* Modal 8b V11 (R8.35–R8.36): 405/404 se muestran SIN fingir éxito (E8.38). */}
      <ModalBorrado8b
        abierto={modalAbierto}
        id={dto.id}
        nombreProyecto={nombreProyecto}
        alCerrar={() => setModalAbierto(false)}
      />
    </main>
  );
}