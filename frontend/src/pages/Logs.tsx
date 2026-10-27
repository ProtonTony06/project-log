// /logs (maqueta 02 — R8.20–R8.27/D-C8-7b): keyset acumulado + filtros en la
// query string (useFiltrosLogs) + WS en vivo (useLogsStream, S8-4 — UNA
// conexión viva solo mientras esta vista está montada) + banda "● En vivo"
// + dedup fusionarEnVivo + banner 400 con campo culpable conservando la
// última lista válida. El indicador del TopBar entra por portal (R8.28).
import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Outlet, useNavigate } from "react-router-dom";
import { ApiError } from "../api/client";
import { listaProyectos, logs } from "../api/panel";
import type { ProjectDto } from "../api/types";
import {
  toSetFilters,
  useFiltrosLogs,
} from "../hooks/useLogFilters";
import {
  acumular,
  esFinDeResultados,
  paginaInicial,
  primeraPagina,
  type Acumulado,
} from "../hooks/usePagination";
import { useLogsStream } from "../hooks/useLogsStream";
import { leerTokenSesion } from "../hooks/useAuth";
import { fusionarEnVivo } from "../ws/dedup";
import type { LogListItemDto } from "../api/types";
import { EstadoConexion } from "../components/EstadoConexion";
import { BandaEnVivo } from "../components/BandaEnVivo";
import { FiltrosLogs } from "../components/FiltrosLogs";
import { TablaLogs } from "../components/TablaLogs";
import { EsqueletoTabla } from "../components/Esqueleto";
import { Banner } from "../components/Banner";

interface InfoFrame {
  batchId: string;
  nuevos: number;
  recibido: string;
}

export default function Logs() {
  const { filtros, actualizar, reiniciar } = useFiltrosLogs();
  const navegar = useNavigate();

  const [proyectos, setProyectos] = useState<ProjectDto[]>([]);
  const [acumulado, setAcumulado] =
    useState<Acumulado<LogListItemDto>>(paginaInicial());
  // espejo para mutaciones coherentes (frames / Cargar más / refetch)
  const refAcumulado = useRef(acumulado);
  const [cargandoPrimera, setCargandoPrimera] = useState(true);
  const [cargandoMas, setCargandoMas] = useState(false);
  const [error400, setError400] = useState<ApiError | null>(null);
  const [errorGeneral, setErrorGeneral] = useState<ApiError | null>(null);

  // último frame batch recibido (banda "● En vivo", R8.25)
  const [frame, setFrame] = useState<InfoFrame | null>(null);
  const [huboConexion, setHuboConexion] = useState(false);

  const nombres = useMemo(
    () => new Map(proyectos.map((p) => [p.id, p.name])),
    [proyectos],
  );

  useEffect(() => {
    let vigente = true;
    // S8-6: nombres para la tabla/selector — fallo silencioso (id crudo).
    listaProyectos()
      .then((ps) => {
        if (vigente) setProyectos(ps);
      })
      .catch(() => {});
    return () => {
      vigente = false;
    };
  }, []);

  // PRIMERA página en cada cambio de la query (E8.22); el 400 deja la lista
  // anterior pintada y solo levanta el banner con campo culpable (E8.26).
  useEffect(() => {
    let vigente = true;
    setCargandoPrimera(true);
    setError400(null);
    setErrorGeneral(null);
    logs(filtros, null)
      .then((pagina) => {
        if (!vigente) return;
        paginar(primeraPagina(pagina));
        setCargandoPrimera(false);
      })
      .catch((err) => {
        if (!vigente) return;
        clasificarError(err);
        setCargandoPrimera(false);
      });
    return () => {
      vigente = false;
    };
    // filtros es el objeto memoizado de useSearchParams (cambia con la URL).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtros]);

  /** Mutación ÚNICA del acumulado: espejo + setState (identidad estable). */
  function paginar(siguiente: Acumulado<LogListItemDto>): void {
    refAcumulado.current = siguiente;
    setAcumulado(siguiente);
  }

  function clasificarError(err: unknown): void {
    if (err instanceof ApiError) {
      if (err.status === 400) setError400(err);
      else setErrorGeneral(err);
    } else {
      setErrorGeneral(new ApiError(0, undefined, "HTTP 0"));
    }
  }

  // ── WS en vivo (S8-4: solo esta vista) ────────────────────────────────
  const estadoStream = useLogsStream({
    activo: true,
    token: leerTokenSesion() ?? "",
    filtros: toSetFilters(filtros),
    alFrame: (frameBatch) => {
      const previo = refAcumulado.current;
      const ids = new Set(previo.items.map((l) => l.id));
      const nuevos = frameBatch.logs.filter((l) => !ids.has(l.id)).length;
      setFrame({
        batchId: frameBatch.batch_id,
        nuevos: nuevos,
        recibido: frameBatch.received_at,
      });
      paginar({
        items: fusionarEnVivo(previo.items, frameBatch.logs),
        nextCursor: previo.nextCursor,
      });
    },
    alReconectar: () => {
      // E8.30/R8.29: refetch de página 1 al reconectar — dedup evita duplicados.
      logs(filtros, null)
        .then((pagina) => {
          paginar({
            items: fusionarEnVivo(refAcumulado.current.items, pagina.items),
            nextCursor:
              refAcumulado.current.items.length === 0
                ? pagina.next_cursor
                : refAcumulado.current.nextCursor,
          });
        })
        .catch(() => {});
    },
  });

  useEffect(() => {
    if (estadoStream.estado === "conectado") setHuboConexion(true);
  }, [estadoStream.estado]);

  const alCargarMas = async () => {
    if (cargandoMas || acumulado.nextCursor === null) return;
    setCargandoMas(true);
    setError400(null);
    setErrorGeneral(null);
    try {
      const pagina = await logs(filtros, acumulado.nextCursor);
      paginar(acumular(refAcumulado.current, pagina)); // R8.23: acumula, no reinicia
    } catch (err) {
      clasificarError(err);
    } finally {
      setCargandoMas(false);
    }
  };

  const reintentarPrimera = () => {
    setError400(null);
    setErrorGeneral(null);
    setCargandoPrimera(true);
    logs(filtros, null)
      .then((pagina) => {
        paginar(primeraPagina(pagina));
        setCargandoPrimera(false);
      })
      .catch((err) => {
        clasificarError(err);
        setCargandoPrimera(false);
      });
  };

  // ── Indicador del TopBar por portal (R8.28: SOLO /logs) ───────────────
  const [slotIndicador, setSlotIndicador] = useState<HTMLElement | null>(null);
  useEffect(() => {
    setSlotIndicador(document.getElementById("indicador-conexion"));
  }, []);

  const hayFiltros =
    filtros.project_id !== null ||
    filtros.environment !== null ||
    filtros.level !== null ||
    filtros.from !== null ||
    filtros.to !== null ||
    filtros.text !== null;

  const finDeResultados = esFinDeResultados(acumulado) && !cargandoPrimera;
  const sinNada = !cargandoPrimera && acumulado.items.length === 0;

  return (
    <>
      <main className="max-w-7xl mx-auto px-4 py-6">
        {slotIndicador !== null &&
          createPortal(
            <EstadoConexion
              estado={estadoStream.estado}
              intento={estadoStream.intento}
            />,
            slotIndicador,
          )}

        <div className="flex items-baseline justify-between flex-wrap gap-2 mb-4">
          <h1 className="text-lg font-semibold tracking-tight">Logs</h1>
          <p className="text-xs text-stone-500 mono">
            GET /logs · keyset (received_at DESC, id DESC) · {"{items, next_cursor}"} · página de 50
          </p>
        </div>

        <FiltrosLogs
          proyectos={proyectos}
          filtros={filtros}
          actualizar={actualizar}
          reiniciar={reiniciar}
        />

        {/* Banner 400 con campo culpable (E8.26/R8.26): lista conservada. */}
        {error400 !== null && (
          <div className="mb-5">
            <Banner
              variante="rojo"
              code={error400.code ?? null}
              message={error400.message}
              alReintentar={reintentarPrimera}
            >
              {error400.field != null && (
                <span>
                  Campo culpable: <span className="mono font-semibold">{error400.field}</span>
                </span>
              )}
            </Banner>
          </div>
        )}

        {/* Error no-400 (500/502/proxy/red): banner + Reintentar (§5/E8.17/E8.18). */}
        {errorGeneral !== null && (
          <div className="mb-5">
            <Banner
              variante={errorGeneral.status === 0 || errorGeneral.status === 502 ? "ambar" : "rojo"}
              code={errorGeneral.code ?? null}
              message={
                errorGeneral.message === "" || errorGeneral.message === `HTTP ${errorGeneral.status}`
                  ? `HTTP ${errorGeneral.status}`
                  : errorGeneral.message
              }
              alReintentar={reintentarPrimera}
            />
          </div>
        )}

        {/* Banner de streaming (R8.28): conectado ⇒ ninguno; reconectando/desconectado ⇒
            literales §4 — la tabla sigue por REST (fallback del PRD). */}
        {estadoStream.estado !== "conectado" && !errorGeneral && (
          <div className="mb-5">
            <Banner
              variante="ambar"
              message={
                huboConexion
                  ? "Streaming inactivo: los datos en pantalla no se actualizan en vivo"
                  : "Sin conexión en vivo"
              }
            />
          </div>
        )}

        {/* Banda ● En vivo (R8.25): sobre la tabla, con el último batch. */}
        <BandaEnVivo
          activa={estadoStream.estado === "conectado" || frame !== null}
          batchId={frame?.batchId ?? null}
          nuevos={frame?.nuevos ?? 0}
          recibido={frame?.recibido ?? null}
        />

        {cargandoPrimera && refAcumulado.current.items.length === 0 && (
          <EsqueletoTabla />
        )}

        {!cargandoPrimera && !sinNada && (
          <TablaLogs
            items={refAcumulado.current.items}
            nombresProyectos={nombres}
            alVerDetalle={(id) => navegar(`/logs/${id}`)}
          />
        )}

        {/* Estados §4 diferenciados (E8.32): vacío por filtros ≠ vacío legítimo;
            NUNCA un error se muestra como vacío (los banners van antes). */}
        {sinNada && error400 === null && errorGeneral === null && (
          <section className="bg-white border border-stone-200 rounded-xl p-6 text-center">
            {hayFiltros ? (
              <>
                <p className="text-stone-700 mb-1">No hay logs que cumplan los filtros.</p>
                <p className="text-xs text-stone-500 mb-4">
                  Prueba a reiniciar filtros o amplía el rango temporal.
                </p>
                <button
                  type="button"
                  onClick={reiniciar}
                  className="border border-stone-300 bg-white hover:bg-stone-50 text-stone-700 text-sm font-medium rounded-lg px-4 py-2"
                >
                  Reiniciar filtros
                </button>
              </>
            ) : (
              <>
                <p className="text-stone-700 mb-1">No hay logs todavía.</p>
                <p className="text-xs text-stone-500">
                  Los logs aparecen con la ingesta (POST /api/v1/ingest) o en vivo por WebSocket.
                </p>
              </>
            )}
          </section>
        )}

        {/* Paginación keyset (R8.23/E8.24): Cargar más con cursor; fin con null. */}
        {!sinNada && error400 === null && errorGeneral === null && (
          <div className="flex items-center justify-center gap-4 border-t border-stone-200 px-4 py-3 bg-stone-50 rounded-b-xl">
            <span className="text-xs text-stone-500 mono">
              {refAcumulado.current.items.length} logs
              {acumulado.nextCursor !== null && !finDeResultados
                ? " · cursor disponible"
                : ""}
            </span>
            <button
              type="button"
              onClick={alCargarMas}
              disabled={finDeResultados || cargandoMas}
              className={
                "border border-stone-300 bg-white text-sm font-medium rounded-lg px-4 py-2 " +
                (finDeResultados || cargandoMas
                  ? "text-stone-400 line-through cursor-not-allowed"
                  : "hover:bg-stone-100 text-stone-800")
              }
            >
              {cargandoMas ? "Cargando…" : "Cargar más"}
            </button>
            {finDeResultados && (
              <span className="text-xs text-stone-400">fin de resultados</span>
            )}
          </div>
        )}
      </main>

      {/* Detalle anidado (D-C8-7a): el listado arriba sigue montado — E8.35. */}
      <Outlet />
    </>
  );
}