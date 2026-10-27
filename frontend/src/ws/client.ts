// ÚNICO constructor de WebSocket de la app (R8.27/D-C8-5): cliente de
// /ws/logs?token= según §3.5 y el patrón ws-console.html. NADA fuera de este
// fichero construye `new WebSocket`.
// - set_filters EXACTO de 6 claves con nulls (S8-8) al abrir, tras cada
//   cambio de filtro y tras cada reconexión.
// - frames entrantes: parser defensivo — solo un frame batch con las 5 claves
//   pasa al callback; cualquier otra cosa se IGNORA sin crashear (E8.33).
// - backoff exponencial + jitter 1 s → cap 30 s (retrasoReconexion, E8.30);
//   cierres del servidor tratados UNIFORMEMENTE sin oráculo (R8.30).
// - close SIN open previo ⇒ alCierreInaugural(): el hook lo resuelve con
//   me() (401 → interceptor; 200 → backoff normal — D-C8-5.6).
import type { FrameBatch, SetFilters } from "../api/types";

export type EstadoWs = "conectado" | "desconectado" | "reconectando";

export interface WsCallbacks {
  alFrame(batch: FrameBatch): void;
  alEstado(estado: EstadoWs, intento: number): void;
  /** close sin open previo (401 pre-upgrade / Origin 403 / red): el client
   * NO diagnostica el status (no hay tal API) — el hook decide con me(). */
  alCierreInaugural(): void;
}

/** Backoff exponencial con jitter (§3.5): base 1 s → 2 s → 4 s … cap 30 s;
 * jitter multiplicativo uniforme ×[0.8, 1.2) ⇒ resultado SIEMPRE ≥ 800 ms. */
export function retrasoReconexion(
  intento: number, // 1-based
  aleatorio: () => number = Math.random,
): number {
  const n = Math.max(1, intento);
  const base = Math.min(30000, 1000 * 2 ** (n - 1));
  return Math.min(30000, base * (0.8 + 0.4 * aleatorio()));
}

/** Envío EXACTO del frame set_filters (S8-8): SIEMPRE las 6 claves presentes,
 * null donde no aplica; JAMÁS claves extra (cursor/limit). */
export function serializarSetFilters(f: SetFilters): string {
  return JSON.stringify({
    type: "set_filters",
    filters: {
      project_id: f.project_id,
      environment: f.environment,
      level: f.level,
      from: f.from,
      to: f.to,
      text: f.text,
    },
  });
}

export interface FrameBatchValido extends FrameBatch {}

/** Parser defensivo (E8.33): solo un objeto con type "batch" y las 5 claves
 * (batch_id string, project_id number, received_at string, logs array) pasa;
 * no-JSON, JSON ajeno o shape rota ⇒ null (ignorado sin lanzar). */
export function parsearFrame(texto: string | null | undefined): FrameBatch | null {
  if (typeof texto !== "string") return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(texto);
  } catch {
    return null;
  }
  if (parsed === null || typeof parsed !== "object") return null;
  const p = parsed as Partial<FrameBatch> & { type?: unknown };
  if (p.type !== "batch") return null;
  if (typeof p.batch_id !== "string") return null;
  if (typeof p.project_id !== "number") return null;
  if (typeof p.received_at !== "string") return null;
  if (!Array.isArray(p.logs)) return null;
  return p as FrameBatch;
}

interface Reloj {
  setTimeout: typeof setTimeout;
  clearTimeout: typeof clearTimeout;
}

export class ClienteWsLogs {
  private readonly callbacks: WsCallbacks;
  private readonly reloj: Reloj;

  private socket: WebSocket | null = null;
  private token: string | null = null;
  private filtros: SetFilters | null = null;
  private intento = 0;
  private detenido = false;
  private abiertoAlgunaVez = false;
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(callbacks: WsCallbacks, reloj: Reloj = { setTimeout, clearTimeout }) {
    this.callbacks = callbacks;
    this.reloj = reloj;
  }

  /** Handshake literal (S8-5): token SOLO en query de la URL. */
  conectar(token: string, filtros: SetFilters): void {
    this.detenido = false;
    this.token = token;
    this.filtros = filtros;
    this.abrir();
  }

  /** Cambio de filtro: reenvía set_filters SIEMPRE 6 claves (R8.22/S8-8). */
  aplicarFiltros(f: SetFilters): void {
    this.filtros = f;
    if (
      this.socket !== null &&
      this.socket.readyState === WebSocket.OPEN
    ) {
      this.socket.send(serializarSetFilters(f));
    }
  }

  /** Cierre definitivo: SIN reconexión ulterior (S8-4 — desmontar /logout/401). */
  cerrar(): void {
    this.detenido = true;
    if (this.timer !== null) {
      this.reloj.clearTimeout(this.timer);
      this.timer = null;
    }
    if (this.socket !== null) {
      const socket = this.socket;
      this.socket = null;
      try {
        socket.close(1000, "cerrar");
      } catch {
        // ya cerrado
      }
    }
  }

  /** Reintento manual tras un cierre inaugural (D-C8-5.6): el hook lo llama
   * CUANDO me() responde 200 (era una caída/Origin 403 — backoff normal);
   * con 401 el interceptor redirige y la instancia muere desmontada. */
  reconectar(): void {
    if (this.detenido || this.socket !== null || this.timer !== null) return;
    this.abrir();
  }

  /** UNA sola conexión por instancia: guard de idempotencia (S8-4). */
  private abrir(): void {
    if (this.detenido || this.socket !== null) return;
    const protocolo = location.protocol === "https:" ? "wss" : "ws";
    const url =
      protocolo +
      "://" +
      location.host +
      "/ws/logs?token=" +
      encodeURIComponent(this.token ?? "");
    const socket = new WebSocket(url);
    this.socket = socket;

    socket.onopen = () => {
      this.abiertoAlgunaVez = true;
      this.intento = 0;
      this.callbacks.alEstado("conectado", 0);
      this.enviarFiltros();
    };

    socket.onmessage = (ev: MessageEvent) => {
      const frame = parsearFrame(typeof ev.data === "string" ? ev.data : null);
      if (frame !== null) {
        this.callbacks.alFrame(frame);
      }
      // anything else: ignorado (defensiva — E8.33).
    };

    socket.onclose = () => this.alCierre();
    socket.onerror = () => {
      // el cierre llega por onclose — R8.30: camino uniforme, sin oráculo.
    };
  }

  private enviarFiltros(): void {
    if (this.socket !== null && this.filtros !== null) {
      this.socket.send(serializarSetFilters(this.filtros));
    }
  }

  private alCierre(): void {
    this.socket = null;
    if (this.detenido) return;

    // R8.30: onclose por CUALQUIER código ⇒ mismo camino (sin oráculo).
    if (!this.abiertoAlgunaVez) {
      // close SIN open: 401 pre-upgrade / Origin 403 / red (D-C8-5.6).
      this.callbacks.alCierreInaugural();
      this.callbacks.alEstado("desconectado", this.intento);
      return;
    }

    this.intento += 1;
    this.callbacks.alEstado("desconectado", this.intento);
    const retraso = retrasoReconexion(this.intento);
    this.callbacks.alEstado("reconectando", this.intento);
    this.timer = this.reloj.setTimeout(() => {
      this.timer = null;
      this.abrir();
    }, retraso);
  }
}