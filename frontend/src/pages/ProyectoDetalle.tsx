// Ficha de proyecto (maqueta 05 — R8.38–R8.41/R8.44/E8.41–E8.44+E8.47):
// datos + bloque "API key de ingesta" (flujo 3: generar/regenerar → 201 →
// revelación ÚNICA; revocar → 204) + Archivar/Desarchivar SIEMPRE por PATCH
// {status} (NUNCA DELETE — E8.41) + zona destructiva con modales 8a/8c
// construidos NO operativos (DELETE + X-Confirm-Name formados → hoy 405/404
// — §5 sin fingir éxito, E8.47). 404 del detalle → nunca vacío (R8.34-like).
import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import {
  cambiarEstado,
  eliminarProyecto,
  proyecto as proyectoApi,
  purgarLogs,
  regenerarApiKey,
  revocarApiKey,
} from "../api/panel";
import { ApiError } from "../api/client";
import type { ProjectDto } from "../api/types";
import { Modal, coincideLiteralConTrim } from "../components/Modal";
import { ModalRevelacionKey } from "../components/ModalRevelacionKey";
import { Banner } from "../components/Banner";

type Confirmacion =
  | null
  | "archivar"
  | "desarchivar"
  | "generar"
  | "revocar"
  | "purgar"
  | "eliminar";

export default function ProyectoDetalle() {
  const { id } = useParams();
  const idNum = Number(id);
  const navegar = useNavigate();
  const location = useLocation();
  // aviso de la navegación previa ("Proyecto creado…", "Cambios guardados")
  const aviso = (location.state as { aviso?: string } | null)?.aviso ?? null;

  const [dto, setDto] = useState<ProjectDto | null>(null);
  const [cargando, setCargando] = useState(true);
  const [noExiste, setNoExiste] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [errorAccion, setErrorAccion] = useState<ApiError | null>(null);
  const [confirmacion, setConfirmacion] = useState<Confirmacion>(null);
  const [ejecutando, setEjecutando] = useState(false);
  // key del 201 — SOLO durante la revelación única (R8.40); luego descartada.
  const [keyRecienGenerada, setKeyRecienGenerada] = useState<string | null>(null);

  const cargar = () => {
    setCargando(true);
    setNoExiste(false);
    setError(null);
    if (!Number.isFinite(idNum) || Number.isNaN(idNum)) {
      setNoExiste(true);
      setCargando(false);
      return;
    }
    proyectoApi(idNum)
      .then((p) => {
        setDto(p);
        setCargando(false);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) setNoExiste(true);
        else setError(err instanceof ApiError ? err : new ApiError(0, undefined, "HTTP 0"));
        setCargando(false);
      });
  };

  useEffect(cargar, [idNum]);

  const archivado = dto?.status === "ARCHIVED";

  const ejecutarConfirmado = async () => {
    if (dto === null || confirmacion === null || ejecutando) return;
    setEjecutando(true);
    setErrorAccion(null);
    try {
      if (confirmacion === "archivar") {
        await cambiarEstado(dto.id, "ARCHIVED"); // PATCH, nunca DELETE (E8.41)
        setConfirmacion(null);
        cargar(); // refresca status + updated_at
      } else if (confirmacion === "desarchivar") {
        await cambiarEstado(dto.id, "ACTIVE");
        setConfirmacion(null);
        cargar();
      } else if (confirmacion === "generar") {
        // E8.43: 201 {key, prefix, created_at} → revelación ÚNICA
        const r = await regenerarApiKey(dto.id);
        setKeyRecienGenerada(r.key); // al cerrar el modal se descarta
        setConfirmacion(null);
      } else if (confirmacion === "revocar") {
        await revocarApiKey(dto.id); // 204 (E8.44)
        setConfirmacion(null);
        cargar();
      } else if (confirmacion === "purgar") {
        // S8-2/E8.47: header X-Confirm-Name FORMADO — hoy 405/404, §5.
        await purgarLogs(dto.id, dto.name);
        // inalcanzable hoy; si C10 lo hace, el refresco es correcto:
        setConfirmacion(null);
        cargar();
      } else if (confirmacion === "eliminar") {
        await eliminarProyecto(dto.id, dto.name);
        setConfirmacion(null);
        navegar("/proyectos");
      }
    } catch (err) {
      setErrorAccion(
        err instanceof ApiError ? err : new ApiError(0, undefined, "HTTP 0"),
      );
      // el modal de 8a/8c queda abierto con el error visible (E8.47/R8.36)
      if (confirmacion !== "purgar" && confirmacion !== "eliminar") {
        setConfirmacion(null);
      }
    } finally {
      setEjecutando(false);
    }
  };

  if (cargando && dto === null) {
    return (
      <main className="max-w-6xl mx-auto px-4 py-6">
        <p className="text-sm text-stone-500">Cargando proyecto…</p>
      </main>
    );
  }

  if (noExiste || error !== null) {
    return (
      <main className="max-w-6xl mx-auto px-4 py-6" role="alert">
        <div className="mb-5">
          <Link to="/proyectos" className="text-sm text-teal-700 hover:underline">
            ← Volver a proyectos
          </Link>
        </div>
        <div className="bg-white border border-stone-200 rounded-xl p-8 text-center">
          <p className="text-stone-700 mb-2">
            {noExiste
              ? "Este proyecto no existe (o fue eliminado)"
              : "No se pudo cargar el proyecto"}
          </p>
          {error !== null && (
            <p className="mono text-xs text-stone-400">HTTP {error.status}</p>
          )}
        </div>
      </main>
    );
  }

  if (dto === null) return null;

  return (
    <main className="max-w-6xl mx-auto px-4 py-6">
      <div className="flex flex-wrap items-center gap-3 mb-5">
        <Link to="/proyectos" className="text-sm text-teal-700 hover:underline">
          ← Volver a proyectos
        </Link>
        <p className="text-xs text-stone-500 mono ml-auto">
          GET /projects/{dto.id} → ProjectDto
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-3 mb-6">
        <h1 className="text-lg font-semibold tracking-tight">{dto.name}</h1>
        <span
          className={
            "text-xs font-medium rounded-md px-2 py-0.5 " +
            (archivado
              ? "text-stone-600 bg-stone-200 border border-stone-300"
              : "text-green-700 bg-green-50 border border-green-200")
          }
        >
          {archivado ? "Archivado" : "Activo"}
        </span>
        <div className="ml-auto flex flex-wrap gap-2">
          <Link
            to={`/proyectos/${dto.id}/editar`}
            className="border border-stone-300 bg-white hover:bg-stone-50 text-sm font-medium rounded-lg px-3 py-2"
          >
            Editar
          </Link>
          <button
            type="button"
            onClick={() => setConfirmacion(archivado ? "desarchivar" : "archivar")}
            className="border border-stone-300 bg-white hover:bg-stone-50 text-sm font-medium rounded-lg px-3 py-2"
          >
            {archivado ? "Desarchivar" : "Archivar"}
          </button>
          <Link
            to={`/logs?project_id=${dto.id}`}
            className="border border-stone-300 bg-white hover:bg-stone-50 text-sm font-medium rounded-lg px-3 py-2"
          >
            Ver logs del proyecto
          </Link>
        </div>
      </div>

      {aviso !== null && (
        <div className="mb-5" role="status">
          <Banner variante="info" message={aviso} />
        </div>
      )}

      {errorAccion !== null && confirmacion === null && (
        <div className="mb-5">
          <Banner
            variante="rojo"
            code={errorAccion.code ?? null}
            message={`HTTP ${errorAccion.status} · ${errorAccion.message}`}
          />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <div className="lg:col-span-2 space-y-5">
          {/* Datos del proyecto */}
          <section className="bg-white border border-stone-200 rounded-xl p-5">
            <h2 className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-3">
              Datos del proyecto
            </h2>
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 text-sm">
              <div>
                <dt className="text-xs text-stone-500 mb-0.5">Name (único)</dt>
                <dd>{dto.name}</dd>
              </div>
              <div>
                <dt className="text-xs text-stone-500 mb-0.5">Status</dt>
                <dd>
                  {dto.status}{" "}
                  <span className="text-xs text-stone-400">
                    (PATCH /projects/{dto.id} con status)
                  </span>
                </dd>
              </div>
              <div className="sm:col-span-2">
                <dt className="text-xs text-stone-500 mb-0.5">Description</dt>
                <dd>{dto.description ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-xs text-stone-500 mb-0.5">Created_at</dt>
                <dd className="mono text-xs">{dto.created_at}</dd>
              </div>
              <div>
                <dt className="text-xs text-stone-500 mb-0.5">Updated_at</dt>
                <dd className="mono text-xs">{dto.updated_at}</dd>
              </div>
            </dl>
          </section>

          {/* API key de ingesta (flujo 3) */}
          <section className="bg-white border border-stone-200 rounded-xl overflow-hidden">
            <div className="px-5 py-2.5 border-b border-stone-200 bg-stone-50">
              <h2 className="text-xs font-medium text-stone-500 uppercase tracking-wide">
                API key de ingesta · header X-API-Key
              </h2>
            </div>
            <div className="p-5">
              <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 text-sm mb-4">
                <div>
                  <dt className="text-xs text-stone-500 mb-0.5">Prefix (visible)</dt>
                  <dd className="mono">{dto.api_key_prefix ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-xs text-stone-500 mb-0.5">has_key</dt>
                  <dd
                    className={
                      dto.has_key ? "text-green-700 font-medium" : "text-stone-500 font-medium"
                    }
                  >
                    {String(dto.has_key)}
                  </dd>
                </div>
              </dl>
              <p className="text-xs text-stone-500 mb-4">
                La key se guarda solo como hash SHA-256: es imposible volver a mostrarla.
                Completa únicamente en la ventana de revelación tras generar o regenerar.
              </p>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => setConfirmacion("generar")}
                  className="bg-teal-700 hover:bg-teal-800 text-white text-sm font-medium rounded-lg px-4 py-2"
                >
                  {dto.has_key ? "Regenerar" : "Generar"}
                </button>
                {dto.has_key && (
                  <button
                    type="button"
                    onClick={() => setConfirmacion("revocar")}
                    className="border border-stone-300 bg-white hover:bg-stone-50 text-sm font-medium rounded-lg px-4 py-2"
                  >
                    Revocar
                  </button>
                )}
              </div>
              <p className="text-xs text-stone-500 mt-3">
                Regenerar invalida la key anterior al instante para todas las apps de este
                proyecto; revocar deja el proyecto sin ingesta activa (401 en las apps) hasta
                generar una nueva.
              </p>
            </div>
          </section>

          {/* Zona destructiva P1 (flujos 8a y 8c): separada y diferenciada */}
          <section className="border border-red-200 bg-red-50 rounded-xl p-5">
            <h2 className="text-xs font-semibold text-red-900 uppercase tracking-wide mb-2">
              Zona destructiva (P1, irreversible)
            </h2>
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-3">
                <div className="text-sm flex-1 min-w-[220px]">
                  <p className="font-medium text-stone-800">Purgar logs</p>
                  <p className="text-xs text-stone-600">
                    Elimina todos los logs del proyecto; este y su API key sobreviven y siguen
                    aceptando ingesta.
                  </p>
                  <p className="text-xs text-stone-500 mono mt-1">
                    DELETE /projects/{dto.id}/logs · X-Confirm-Name: {dto.name}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setConfirmacion("purgar")}
                  className="border border-red-300 bg-white hover:bg-red-100 text-red-800 text-sm font-medium rounded-lg px-4 py-2"
                >
                  Purgar logs
                </button>
              </div>
              <div className="flex flex-wrap items-center gap-3 border-t border-red-200 pt-3">
                <div className="text-sm flex-1 min-w-[220px]">
                  <p className="font-medium text-stone-800">Eliminar definitivamente</p>
                  <p className="text-xs text-stone-600">
                    Borra el proyecto y todos sus logs (ON DELETE CASCADE) de forma irreversible.
                  </p>
                  <p className="text-xs text-stone-500 mono mt-1">
                    DELETE /projects/{dto.id} · X-Confirm-Name: {dto.name}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setConfirmacion("eliminar")}
                  className="bg-red-700 hover:bg-red-800 text-white text-sm font-medium rounded-lg px-4 py-2"
                >
                  Eliminar definitivamente
                </button>
              </div>
            </div>
          </section>
        </div>

        {/* Columna lateral */}
        <aside className="space-y-5">
          <section className="bg-white border border-stone-200 rounded-xl p-5 text-sm">
            <h2 className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-3">
              Estado de ingesta
            </h2>
            <ul className="space-y-2 text-sm">
              <li className="flex justify-between gap-2">
                <span className="text-stone-600">Key activa</span>
                <span className={dto.has_key ? "text-green-700 font-medium" : "text-stone-500"}>
                  {dto.has_key ? "Sí" : "No (401 en las apps)"}
                </span>
              </li>
              <li className="flex justify-between gap-2">
                <span className="text-stone-600">Streaming en vivo</span>
                <span className="text-stone-500 mono">/ws/logs · set_filters</span>
              </li>
            </ul>
            <p className="text-xs text-stone-500 mt-3 border-t border-stone-100 pt-3">
              Si el proyecto estuviera archivado, la ingesta respondería{" "}
              <span className="mono">403 {"{\"code\":\"project_archived\"}"}</span> y dejaría de
              emitir en vivo (archivar afecta al flujo, no a la consulta).
            </p>
          </section>
          <section className="bg-white border border-stone-200 rounded-xl p-5 text-sm">
            <h2 className="text-xs font-medium text-stone-500 uppercase tracking-wide mb-3">
              Ejemplo para las apps
            </h2>
            <pre className="mono text-[11px] leading-5 bg-stone-50 border border-stone-200 rounded-lg p-3 overflow-x-auto">
              {`POST /api/v1/ingest
X-API-Key: plk_…

{
  "level": "ERROR",
  "timestamp": "2026-01-01T00:00:00Z",
  "message": "…",
  "environment": "production",
  "source": "mi-app",
  "correlation_id": "req_…"
}`}
            </pre>
            <p className="text-xs text-stone-500 mt-2">
              El cliente no envía project_id ni received_at: campos de confianza impuestos por el
              servidor. environment es obligatorio (400 environment_required si falta); timestamp
              exige offset (ISO-8601).
            </p>
          </section>
        </aside>
      </div>

      {/* ── Confirmaciones estándar (archivar/desarchivar/generar/revocar) ── */}
      <Modal
        abierto={confirmacion === "archivar" || confirmacion === "desarchivar"}
        titulo={confirmacion === "archivar" ? "Archivar proyecto" : "Desarchivar proyecto"}
        alCerrar={() => setConfirmacion(null)}
      >
        <p className="text-sm text-stone-600 mb-4">
          {confirmacion === "archivar" ? (
            <>
              El proyecto dejará de aceptar ingesta (<span className="mono">403 project_archived</span>)
              y dejará de emitir en vivo. <span className="font-medium">NO se borra nada</span>: sus
              logs siguen consultables y puedes desarchivarlo en cualquier momento.
            </>
          ) : (
            <>
              El proyecto volverá a aceptar ingesta y a emitir en vivo.{" "}
              <span className="font-medium">NO se borra nada</span> (E8.41: nunca es lo mismo que
              eliminar).
            </>
          )}
        </p>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={() => setConfirmacion(null)}
            className="border border-stone-300 rounded-lg px-3 py-2 text-sm hover:bg-stone-50"
          >
            Cancelar
          </button>
          <button
            type="button"
            disabled={ejecutando}
            onClick={ejecutarConfirmado}
            className="bg-teal-700 hover:bg-teal-800 text-white text-sm font-medium rounded-lg px-3 py-2 disabled:opacity-50"
          >
            {ejecutando ? "Aplicando…" : "Confirmar (PATCH status)"}
          </button>
        </div>
      </Modal>

      <Modal
        abierto={confirmacion === "generar" || confirmacion === "revocar"}
        titulo={confirmacion === "generar" ? "Generar nueva API key" : "Revocar API key"}
        alCerrar={() => setConfirmacion(null)}
      >
        <p className="text-sm text-stone-600 mb-4">
          {confirmacion === "generar" ? (
            <>
              Se generará una nueva key y la ANTERIOR quedará invalidada al instante para todas las
              apps de este proyecto. La key completa solo se mostrará UNA VEZ.
            </>
          ) : (
            <>
              El proyecto quedará sin ingesta activa (401 en las apps) hasta generar una nueva.
              Los logs existentes se conservan.
            </>
          )}
        </p>
        {errorAccion !== null && (
          <p className="mb-4 text-xs mono text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
            HTTP {errorAccion.status}
            {errorAccion.code != null ? " · code: " + errorAccion.code : ""} · {errorAccion.message}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={() => setConfirmacion(null)}
            className="border border-stone-300 rounded-lg px-3 py-2 text-sm hover:bg-stone-50"
          >
            Cancelar
          </button>
          <button
            type="button"
            disabled={ejecutando}
            onClick={ejecutarConfirmado}
            className="bg-teal-700 hover:bg-teal-800 text-white text-sm font-medium rounded-lg px-3 py-2 disabled:opacity-50"
          >
            {ejecutando ? "Aplicando…" : "Confirmar"}
          </button>
        </div>
      </Modal>

      {/* ── Modal 8c: purgar logs — nombre EXACTO + trim; hoy 405/404 (§5) ── */}
      <ModalPurga8c
        abierto={confirmacion === "purgar"}
        id={dto.id}
        nombre={dto.name}
        ejecutando={ejecutando}
        errorAccion={confirmacion === "purgar" ? errorAccion : null}
        alCerrar={() => setConfirmacion(null)}
        alConfirmar={ejecutarConfirmado}
      />

      {/* ── Modal 8a: eliminar definitiva — nombre EXACTO; hoy 405/404 (§5) ── */}
      <ModalEliminar8a
        abierto={confirmacion === "eliminar"}
        id={dto.id}
        nombre={dto.name}
        ejecutando={ejecutando}
        errorAccion={
          confirmacion === "eliminar" ? errorAccion : null
        }
        alCerrar={() => setConfirmacion(null)}
        alConfirmar={ejecutarConfirmado}
      />

      {/* Revelación ÚNICA de la key del 201 (E8.43): al cerrar queda el prefix */}
      {keyRecienGenerada !== null && (
        <ModalRevelacionKey
          abierto
          apiKey={keyRecienGenerada}
          alCerrar={() => {
            setKeyRecienGenerada(null); // descarte inmediato (R8.40)
            cargar();
          }}
        />
      )}
    </main>
  );
}

/** Modal 8c — purgar logs (E8.47): exige el nombre EXACTO (trim); el header
 * X-Confirm-Name se envía formado; hoy el backend responde 405/404 → §5. */
function ModalPurga8c(props: {
  abierto: boolean;
  id: number;
  nombre: string;
  ejecutando: boolean;
  errorAccion: ApiError | null;
  alCerrar: () => void;
  alConfirmar: () => void;
}) {
  const { abierto, id, nombre, ejecutando, errorAccion, alCerrar, alConfirmar } = props;
  const [texto, setTexto] = useState("");
  const coincide = coincideLiteralConTrim(nombre, texto);
  return (
    <Modal abierto={abierto} titulo={`Purgar logs de ${nombre}`} alCerrar={alCerrar}>
      <p className="text-sm text-stone-600 mb-4">
        Se eliminarán todos los logs del proyecto <span className="font-medium">{nombre}</span>; el
        proyecto y su API key sobreviven y siguen aceptando ingesta. Operación irreversible (P1).
      </p>
      <label
        className="block text-xs font-medium text-stone-600 mb-1"
        htmlFor={`confirm-name-purga-${id}`}
      >
        Escribe el nombre exacto del proyecto para confirmar
      </label>
      <input
        id={`confirm-name-purga-${id}`}
        type="text"
        value={texto}
        spellCheck={false}
        onChange={(e) => setTexto(e.target.value)}
        className="w-full border border-stone-300 rounded-lg px-3 py-2 text-sm mono mb-4"
      />
      {!coincide && texto.trim() !== "" && (
        <p className="text-xs text-red-700 mb-4">
          La confirmación no coincide. El botón solo se habilita con el nombre exacto.
        </p>
      )}
      {coincide && errorAccion !== null && (
        <p className="mb-4 text-xs mono text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2" role="alert">
          HTTP {errorAccion.status}
          {errorAccion.code != null ? " · code: " + errorAccion.code : ""} · {errorAccion.message}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={alCerrar}
          className="border border-stone-300 rounded-lg px-3 py-2 text-sm hover:bg-stone-50"
        >
          Cancelar
        </button>
        <button
          type="button"
          disabled={!coincide || ejecutando}
          onClick={alConfirmar}
          title="Envía DELETE /projects/{id}/logs con header X-Confirm-Name"
          className={
            "text-white text-sm font-medium rounded-lg px-4 py-2 " +
            (coincide && !ejecutando
              ? "bg-red-700 hover:bg-red-800"
              : "bg-red-300 cursor-not-allowed")
          }
        >
          {ejecutando ? "Purgando…" : "Purgar logs"}
        </button>
      </div>
    </Modal>
  );
}

/** Modal 8a — eliminar definitivamente (E8.47): nombre EXACTO + trim; texto
 * con la consecuencia; hoy 405/404 → error visible, ficha intacta. */
function ModalEliminar8a(props: {
  abierto: boolean;
  id: number;
  nombre: string;
  ejecutando: boolean;
  errorAccion: ApiError | null;
  alCerrar: () => void;
  alConfirmar: () => void;
}) {
  const { abierto, id, nombre, ejecutando, errorAccion, alCerrar, alConfirmar } = props;
  const [texto, setTexto] = useState("");
  const coincide = coincideLiteralConTrim(nombre, texto);
  return (
    <Modal abierto={abierto} titulo="Eliminar definitivamente el proyecto" tamano="max-w-lg" alCerrar={alCerrar}>
      <p className="text-sm text-stone-600 mb-4">
        Se borrarán <span className="font-medium">{nombre}</span> y{" "}
        <span className="font-medium">todos sus logs</span> de forma irreversible (eliminación en
        cascada). Archivar no es esto: archivar nunca borra nada.
      </p>
      <label
        className="block text-xs font-medium text-stone-600 mb-1"
        htmlFor={`confirm-name-del-${id}`}
      >
        Escribe el nombre exacto del proyecto para confirmar
      </label>
      <input
        id={`confirm-name-del-${id}`}
        type="text"
        value={texto}
        spellCheck={false}
        onChange={(e) => setTexto(e.target.value)}
        className="w-full border border-stone-300 rounded-lg px-3 py-2 text-sm mono mb-1"
      />
      {!coincide && texto.trim() !== "" && (
        <p className="text-xs text-red-700 mb-4">
          La confirmación no coincide. El botón solo se habilita con el nombre exacto.
        </p>
      )}
      {coincide && errorAccion !== null && (
        <p className="mb-4 text-xs mono text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2" role="alert">
          HTTP {errorAccion.status}
          {errorAccion.code != null ? " · code: " + errorAccion.code : ""} · {errorAccion.message}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={alCerrar}
          className="border border-stone-300 rounded-lg px-3 py-2 text-sm hover:bg-stone-50"
        >
          Cancelar
        </button>
        <button
          type="button"
          disabled={!coincide || ejecutando}
          onClick={alConfirmar}
          title={`Envía DELETE /projects/${id} con header X-Confirm-Name`}
          className={
            "text-white text-sm font-medium rounded-lg px-4 py-2 " +
            (coincide && !ejecutando
              ? "bg-red-700 hover:bg-red-800"
              : "bg-red-300 cursor-not-allowed")
          }
        >
          {ejecutando ? "Eliminando…" : "Eliminar definitivamente"}
        </button>
      </div>
    </Modal>
  );
}