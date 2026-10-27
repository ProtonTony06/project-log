// Ciclo de vida del WS de /logs (S8-4/D-C8-5.5 — R8.28–R8.31): el hook es
// USADO SOLO en pages/Logs.tsx — UNA única conexión, creada al montar y
// cerrada en cleanup (desmontar / logout / 401 desmontan la vista). NUNCA
// fuera de /logs existe indicador NI socket; reentrar a /logs puede reabrir.
import { useEffect, useRef, useState } from "react";
import { me } from "../api/panel";
import {
  ClienteWsLogs,
  type EstadoWs,
} from "../ws/client";
import type { FrameBatch, SetFilters } from "../api/types";

export interface ParametrosStream {
  /** false ⇒ no conecta (sin sesión, hook fuera de /logs, etc.). */
  activo: boolean;
  token: string;
  filtros: SetFilters;
  /** Frame batch validado llega: la página fusiona por dedup (D-C8-4). */
  alFrame(frame: FrameBatch): void;
  /** Al reconectar el WS: refrescar la primera página de GET /logs (E8.30). */
  alReconectar(): void;
}

export interface EstadoStream {
  estado: EstadoWs;
  /** N del "Reconectando (intento N)…" (E8.29). */
  intento: number;
}

export function useLogsStream(p: ParametrosStream): EstadoStream {
  const [estado, setEstado] = useState<EstadoWs>("desconectado");
  const [intento, setIntento] = useState(0);

  // refs de los callbacks: el efecto vive con el MONTAJE (no se re-crea el
  // socket por cambio de filtros — esos van vía aplicarFiltros/R8.22).
  const refCallbacks = useRef(p);
  refCallbacks.current = p;

  // Última vez conectado: para detectar reconexión (refresco página 1).

  const clienteRef = useRef<ClienteWsLogs | null>(null);

  useEffect(() => {
    if (!p.activo) return;
    let cancelado = false;

    const cliente = new ClienteWsLogs({
      alFrame: (frame) => refCallbacks.current.alFrame(frame),
      alEstado: (nuevo, intentoActual) => {
        if (cancelado) return;
        setEstado((previo) => {
          if (nuevo === "conectado" && previo === "reconectando") {
            // reconexión exitosa: reenvío de set_filters ya hecho por el
            // client (onopen) — la página refresca su primera página (E8.30).
            refCallbacks.current.alReconectar();
          }
          return nuevo;
        });
        setIntento(intentoActual);
      },
      alCierreInaugural: () => {
        // D-C8-5.6: el client NO diagnostica el status del handshake (no hay
        // tal API en el navegador). Verificación con me():
        // - 401 ⇒ interceptor global (redirect /login + banner expiración)
        // - 200 ⇒ era caída/Origin 403 — backoff normal (reconectar).
        me()
          .then(() => {
            if (!cancelado) cliente.reconectar();
          })
          .catch(() => {
            // 401: el interceptor ya descartó token y redirige; la instancia
            // muere con el desmontaje de /logs.
          });
      },
    });
    clienteRef.current = cliente;
    cliente.conectar(p.token, p.filtros);

    return () => {
      cancelado = true;
      cliente.cerrar();
      if (clienteRef.current === cliente) clienteRef.current = null;
    };
    // el effect vive con el montaje; filtros viajan por ref (aplicarFiltros).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [p.activo, p.token]);

  // Cambio de filtro ⇒ set_filters reenviado (R8.22/S8-8) sin reconectar.
  const filtrosRef = useRef(p.filtros);
  useEffect(() => {
    filtrosRef.current = p.filtros;
    clienteRef.current?.aplicarFiltros(p.filtros);
  }, [p.filtros]);

  return { estado, intento };
}