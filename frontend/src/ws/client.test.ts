// Vitest puro SIN DOM (D-C8-11/E8.56): backoff con jitter inyectado
// (E8.30/R8.29), shape byte-exacto de set_filters (S8-8) y parser defensivo
// de frames (E8.33).
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import {
  ClienteWsLogs,
  parsearFrame,
  retrasoReconexion,
  serializarSetFilters,
} from "./client";

describe("retrasoReconexion (E8.30/R8.29)", () => {
  it("secuencia exponencial con jitter neutro (aleatorio=0) — 1 s → 2 s → 4 s", () => {
    const aleatorio = () => 0; // jitter mínimo ×0.8
    // ×0.8: 800, 1600, 3200...
    expect(retrasoReconexion(1, aleatorio)).toBe(800);
    expect(retrasoReconexion(2, aleatorio)).toBe(1600);
    expect(retrasoReconexion(3, aleatorio)).toBe(3200);
    expect(retrasoReconexion(4, aleatorio)).toBe(6400);
  });

  it("jitter máximo (aleatorio≈1) ×1.2 — 1200, 2400, 4800", () => {
    const aleatorio = () => 0.999999;
    expect(Math.round(retrasoReconexion(1, aleatorio))).toBeCloseTo(1200, 0);
    expect(Math.round(retrasoReconexion(3, aleatorio))).toBeCloseTo(4800, 0);
  });

  it("CAP 30 s alcanzado: base ya en cap (intento ≥ 6)", () => {
    const aleatorio = () => 0.5;
    expect(retrasoReconexion(6, aleatorio)).toBe(30000); // base 32000 ≥ cap 30 s; ×1.0
    expect(retrasoReconexion(7, aleatorio)).toBe(30000);
  });

  it("todo resultado dentro de los límites [800, 30000] para intentos 1..20", () => {
    const aleatorio = () => 0.73; // cualquier valor fijo
    for (let n = 1; n <= 20; n++) {
      const r = retrasoReconexion(n, aleatorio);
      expect(r).toBeGreaterThanOrEqual(800);
      expect(r).toBeLessThanOrEqual(30000);
    }
  });

  it("jitter uniforme ×[0.8, 1.2): intento 1 — 800 ms mínimo (E8.30 límite)", () => {
    const minimo = Math.min(
      retrasoReconexion(1, () => 0),
      retrasoReconexion(1, () => 0.4),
    );
    expect(minimo).toBe(800);
  });
});

describe("serializarSetFilters (S8-8)", () => {
  it("shape byte-exacto: 6 claves SIEMPRE, nulls, sin claves extra", () => {
    const s = serializarSetFilters({
      project_id: null,
      environment: null,
      level: null,
      from: null,
      to: null,
      text: null,
    });
    expect(JSON.parse(s)).toEqual({
      type: "set_filters",
      filters: {
        project_id: null,
        environment: null,
        level: null,
        from: null,
        to: null,
        text: null,
      },
    });
    expect(JSON.parse(s).filters).toEqual({
      project_id: null,
      environment: null,
      level: null,
      from: null,
      to: null,
      text: null,
    });
  });

  it("con valores: project_id number, sin cursor/limit (no hay claves extra)", () => {
    const s = serializarSetFilters({
      project_id: 3,
      environment: "production",
      level: "ERROR",
      from: "2026-10-19T10:30:00+02:00",
      to: null,
      text: "upstream",
    });
    const parsed = JSON.parse(s) as { type?: string; filters: Record<string, unknown> };
    expect(parsed.type).toBe("set_filters");
    expect(Object.keys(parsed.filters).length).toBe(6);
    expect(parsed.filters.project_id).toBe(3);
    expect(parsed.filters.to).toBeNull();
  });
});

describe("parsearFrame (E8.33 — defensiva)", () => {
  const frameValido = {
    type: "batch",
    batch_id: "b_1a9f4c2e",
    project_id: 3,
    received_at: "2026-10-19T21:47:58.204123Z",
    logs: [
      {
        id: 48211,
        project_id: 3,
        level: "ERROR",
        environment: "production",
        timestamp: "2026-10-19T21:47:58.102Z",
        received_at: "2026-10-19T21:47:58.204123Z",
        message: "x",
        exception: null,
        metadata: null,
        source: "checkout-api",
        correlation_id: "req_9f3a72",
      },
    ],
  };

  it("frame batch válido pasa al callback completo", () => {
    const f = parsearFrame(JSON.stringify(frameValido));
    expect(f).not.toBeNull();
    expect(f?.type).toBe("batch");
    expect(f?.batch_id).toBe("b_1a9f4c2e");
    expect(f?.logs.length).toBe(1);
    expect(f?.logs[0].id).toBe(48211);
  });

  it("no-JSON, JSON ajeno o shape roto ⇒ null SIN lanzar (ignorado)", () => {
    expect(parsearFrame("no es json")).toBeNull();
    expect(parsearFrame("[1,2,3]")).toBeNull();
    expect(parsearFrame('{"type":"ping"}')).toBeNull();
    expect(parsearFrame('{"type":"batch"}')).toBeNull(); // sin las 5 claves
    expect(
      parsearFrame('{"type":"batch","batch_id":1,"project_id":3,"received_at":"x","logs":[]}'),
    ).toBeNull(); // batch_id no-string
    expect(
      parsearFrame(
        '{"type":"batch","batch_id":"b","project_id":"3","received_at":"x","logs":[]}',
      ),
    ).toBeNull(); // project_id no-number
    expect(
      parsearFrame('{"type":"batch","batch_id":"b","project_id":3,"received_at":"x","logs":{}}'),
    ).toBeNull(); // logs no-array
    expect(parsearFrame(null)).toBeNull();
    expect(parsearFrame(undefined)).toBeNull();
    expect(parsearFrame("")).toBeNull();
  });

  it("logs vacíos (batch sin novedades) ES frame válido", () => {
    const f = parsearFrame(
      JSON.stringify({ ...frameValido, logs: [] }),
    );
    expect(f?.logs).toEqual([]);
  });
});

describe("ClienteWsLogs — ciclo sin red (WebSocket y location stubbed)", () => {
  const VACIO = {
    project_id: null,
    environment: null,
    level: null,
    from: null,
    to: null,
    text: null,
  };

  const REGISTROS: { url: string; cerrados: number[]; envio?: string }[] = [];

  class WsStub {
    static CONNECTING = 0;
    static OPEN = 1;
    static CLOSING = 2;
    static CLOSED = 3;
    url: string;
    readyState = 0;
    onopen: (() => void) | null = null;
    onmessage: ((ev: { data: string }) => void) | null = null;
    onclose: (() => void) | null = null;
    onerror: (() => void) | null = null;

    constructor(url: string) {
      this.url = url;
      REGISTROS.push({ url, cerrados: [] });
    }

    send(data: string): void {
      // el registro del ÚLTIMO constructor — 1 conexión viva por instancia
      REGISTROS[REGISTROS.length - 1].envio = data;
    }

    close(code?: number): void {
      REGISTROS[REGISTROS.length - 1].cerrados.push(code ?? 1005);
    }
  }

  beforeAll(() => {
    vi.stubGlobal("WebSocket", WsStub as unknown as typeof WebSocket);
    vi.stubGlobal("location", { protocol: "http:", host: "localhost" });
  });

  afterAll(() => {
    vi.unstubAllGlobals();
  });

  it("handshake literal: ws://host/ws/logs?token=<encodeURIComponent> (S8-5)", () => {
    REGISTROS.length = 0;
    const cliente = new ClienteWsLogs({
      alFrame: () => {},
      alEstado: () => {},
      alCierreInaugural: () => {},
    });
    cliente.conectar("tok_abc/1+2", VACIO);
    expect(REGISTROS.length).toBe(1);
    expect(REGISTROS[0].url).toBe(
      // protocolo http ⇒ "ws"; token SIEMPRE en la query, codificado
      "ws://localhost/ws/logs?token=tok_abc%2F1%2B2",
    );
    cliente.cerrar();
  });

  it("https ⇒ wss:// (mismo patrón)", () => {
    vi.stubGlobal("location", { protocol: "https:", host: "panel.example.org" });
    REGISTROS.length = 0;
    const cliente = new ClienteWsLogs({
      alFrame: () => {},
      alEstado: () => {},
      alCierreInaugural: () => {},
    });
    cliente.conectar("t", VACIO);
    expect(REGISTROS[0].url).toBe("wss://panel.example.org/ws/logs?token=t");
    vi.stubGlobal("location", { protocol: "http:", host: "localhost" });
    cliente.cerrar();
  });

  it("cerrar() envía close(1000) UNA sola vez (idempotente)", () => {
    REGISTROS.length = 0;
    const cliente = new ClienteWsLogs({
      alFrame: () => {},
      alEstado: () => {},
      alCierreInaugural: () => {},
    });
    cliente.conectar("t", VACIO);
    cliente.cerrar();
    cliente.cerrar();
    expect(REGISTROS[0].cerrados).toEqual([1000]);
  });

  it("aplicarFiltros antes de conectar NO envía ni lanza (readyState ≠ OPEN)", () => {
    const cliente = new ClienteWsLogs({
      alFrame: () => {},
      alEstado: () => {},
      alCierreInaugural: () => {},
    });
    expect(() => cliente.aplicarFiltros(VACIO)).not.toThrow();
  });

  it("set_filters al abrir y tras cada cambio de filtro; reconexión reusa la ruta (R8.22/S8-8)", () => {
    REGISTROS.length = 0;
    const cliente = new ClienteWsLogs({
      alFrame: () => {},
      alEstado: () => {},
      alCierreInaugural: () => {},
    });
    cliente.conectar("t", VACIO);
    const socket = (cliente as unknown as { socket: unknown }).socket as unknown as {
      readyState: number;
      send: (d: string) => void;
      onopen: (() => void) | null;
    };
    expect(socket).toBeTruthy();

    const envios: string[] = [];
    socket.send = (d: string) => envios.push(d);
    socket.readyState = 1; // OPEN simulado
    socket.onopen!(); // el servidor acepta → handler asignado en abrir()

    expect(envios.length).toBe(1);
    expect(JSON.parse(envios[0]).type).toBe("set_filters");
    expect(JSON.parse(envios[0]).filters).toEqual(VACIO);

    // cambio de filtro → REENVÍO (R8.22) sin reconectar
    const filtros2 = { ...VACIO, project_id: 7, text: "timeout" };
    cliente.aplicarFiltros(filtros2);
    expect(envios.length).toBe(2);
    expect(JSON.parse(envios[1]).filters).toEqual(filtros2);

    cliente.cerrar();
  });
});