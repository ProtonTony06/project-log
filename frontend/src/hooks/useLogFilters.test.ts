// Vitest puro SIN DOM (D-C8-11/E8.56): mapeo bidireccional filtros⇄query
// (E8.21) y fechas ISO-8601 CON offset (E8.23/S8-9).
import { describe, expect, it } from "vitest";
import {
  datetimeLocalAIsoConOffset,
  filtrosDesdeSearch,
  searchDesdeFiltros,
  toSetFilters,
} from "./useLogFilters";
import type { FiltrosLogs } from "../api/types";

const VACIOS: FiltrosLogs = {
  project_id: null,
  environment: null,
  level: null,
  from: null,
  to: null,
  text: null,
};

describe("ida y vuelta filtros⇄query (E8.21)", () => {
  it("solo claves con valor, orden estable (project_id, environment, level, from, to, text)", () => {
    const f: FiltrosLogs = {
      project_id: 3,
      environment: "production",
      level: "ERROR",
      from: "2026-10-19T00:00:00+02:00",
      to: null,
      text: "upstream",
    };
    const qs = searchDesdeFiltros(f);
    expect(decodeURIComponent(qs.split("&").join("\n"))).toBe(
      "project_id=3\nenvironment=production\nlevel=ERROR\nfrom=2026-10-19T00:00:00+02:00\ntext=upstream",
    );
  });

  it("ida y vuelta IDEMPOTENTE — parse(search(search(f))) === f", () => {
    const f: FiltrosLogs = {
      project_id: 12,
      environment: "staging",
      level: "WARN",
      from: "2026-10-19T08:30:00-05:00",
      to: "2026-10-20T22:00:00+02:00",
      text: "timeout",
    };
    const sp = new URLSearchParams(searchDesdeFiltros(f));
    const f2 = filtrosDesdeSearch(sp);
    expect(f2).toEqual(f);
    const sp2 = new URLSearchParams(searchDesdeFiltros(f2));
    expect(f2).toEqual(filtrosDesdeSearch(sp2)); // 2ª vuelta estable
  });

  it("query VACÍA ⇒ filtros nulls; id/valor inválido ⇒ null (defensiva)", () => {
    expect(filtrosDesdeSearch(new URLSearchParams(""))).toEqual(VACIOS);
    expect(
      filtrosDesdeSearch(new URLSearchParams("project_id=12ab&level=CRITICAL")),
    ).toEqual({ ...VACIOS, project_id: null, level: null });
    expect(filtrosDesdeSearch(new URLSearchParams("project_id=7"))).toEqual({
      ...VACIOS,
      project_id: 7,
    });
  });

  it("from == to es legal (rango de un punto)", () => {
    const f = { ...VACIOS, from: "x", to: "x" };
    const sp = new URLSearchParams(searchDesdeFiltros(f));
    expect(filtrosDesdeSearch(sp)).toEqual(f);
  });
});

describe("datetimeLocalAIsoConOffset (E8.23/S8-9)", () => {
  it("datetime-local + offset inyectado (UTC+2 ⇒ -120) → ISO con +02:00", () => {
    expect(datetimeLocalAIsoConOffset("2026-10-19T10:30", -120)).toBe(
      "2026-10-19T10:30:00+02:00",
    );
  });

  it("offset negativo (UTC-5 ⇒ +300) → -05:00; cero → +00:00", () => {
    expect(datetimeLocalAIsoConOffset("2026-10-19T10:30", 300)).toBe(
      "2026-10-19T10:30:00-05:00",
    );
    expect(datetimeLocalAIsoConOffset("2026-10-19T10:30", 0)).toBe(
      "2026-10-19T10:30:00+00:00",
    );
  });

  it("IDEMPOTENTE: un ISO ya con offset viaja TAL CUAL (sin doble offset)", () => {
    const iso = "2026-10-19T10:30:00+02:00";
    expect(datetimeLocalAIsoConOffset(iso, -120)).toBe(iso);
    const z = "2026-10-19T10:30:00Z";
    expect(datetimeLocalAIsoConOffset(z, -120)).toBe(z);
  });

  it("minuto-only añade :00; cadena ajena viaja sin romper", () => {
    expect(datetimeLocalAIsoConOffset("2026-10-19T10:30", -60)).toBe(
      "2026-10-19T10:30:00+01:00",
    );
    expect(datetimeLocalAIsoConOffset("", -120)).toBe("");
  });
});

describe("toSetFilters (S8-8)", () => {
  it("SIEMPRE 6 claves con nulls — sin claves extra del frame (type/filters)", () => {
    const sf = toSetFilters({ ...VACIOS, project_id: 4, level: "FATAL" });
    expect(Object.keys(sf).sort()).toEqual([
      "environment",
      "from",
      "level",
      "project_id",
      "text",
      "to",
    ]);
    expect(sf).toEqual({
      project_id: 4,
      environment: null,
      level: "FATAL",
      from: null,
      to: null,
      text: null,
    });
    // round-trip: serializarSetFilters añade SOLO type:"set_filters"+"filters"
    expect(JSON.parse(JSON.stringify(sf))).toEqual(sf);
  });
});