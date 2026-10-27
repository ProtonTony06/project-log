// Vitest puro SIN DOM (D-C8-11/E8.56): dedup del frame batch en el acumulado
// (E8.25/E8.28) y propiedad de no-mutación (React depende de identidad).
import { describe, expect, it } from "vitest";
import { comparadorRecibidoDesc, fusionarEnVivo } from "./dedup";
import type { LogListItemDto } from "../api/types";

function log(
  id: number,
  received_at: string,
  level: LogListItemDto["level"] = "INFO",
): LogListItemDto {
  return {
    id,
    project_id: 3,
    level,
    environment: "production",
    timestamp: "2026-10-19T21:47:58.100Z",
    received_at,
    message: `msg ${id}`,
    source: null,
    correlation_id: null,
  };
}

describe("comparadorRecibidoDesc", () => {
  it("orden global received_at DESC, id DESC (E8.28)", () => {
    const a = log(1, "2026-10-19T21:47:58.100Z");
    const b = log(2, "2026-10-19T21:48:00.000Z");
    // b más reciente ⇒ sort DESC lo pone primero
    expect(comparadorRecibidoDesc(a, b)).toBeGreaterThan(0);
    expect(comparadorRecibidoDesc(b, a)).toBeLessThan(0);
  });

  it("empate con micros: desempate id DESC (batch comparte received_at)", () => {
    const base = "2026-10-19T21:47:58.204123Z";
    const b6 = log(6, base);
    const b7 = log(7, base);
    const b5 = log(5, base);
    const ordenados = [b6, b5, b7].sort(comparadorRecibidoDesc);
    expect(ordenados.map((l) => l.id)).toEqual([7, 6, 5]); // id DESC
  });

  it("ids iguales ⇒ 0 (estable)", () => {
    const a = log(9, "2026-10-19T21:47:58.100Z");
    expect(comparadorRecibidoDesc(a, a)).toBe(0);
  });
});

describe("fusionarEnVivo", () => {
  it("id ya presente (llegó por Cargar más o refetch) se IGNORA (E8.28)", () => {
    const acumulado = [log(10, "2026-10-19T21:48:00.000Z"), log(5, "2026-10-19T21:40:00.000Z")];
    const lote = [log(10, "2026-10-19T21:48:00.000Z")]; // duplicado
    const resultado = fusionarEnVivo(acumulado, lote);
    expect(resultado).toBe(acumulado); // sin cambios: identidad conservada
    expect(resultado.map((l) => l.id)).toEqual([10, 5]);
  });

  it("lote desordenado vs orden global: se reordena TODO (E8.28 orden global)", () => {
    const acumulado = [log(100, "2026-10-19T21:00:00.000Z")];
    const lote = [
      log(2, "2026-10-19T21:47:58.100Z"),
      log(3, "2026-10-19T21:47:58.100Z"),
      log(1, "2026-10-19T21:50:00.000Z"),
    ];
    const resultado = fusionarEnVivo(acumulado, lote);
    // el más reciente primero (id 1), luego el grupo del lote por id DESC,
    // luego el antiguo del keyset
    expect(resultado.map((l) => l.id)).toEqual([1, 3, 2, 100]);
  });

  it("el lote entra como GRUPO coherente en el empate de received_at (E8.25/E8.27)", () => {
    const base = "2026-10-19T21:47:58.204000Z";
    const acumulado = [log(50, "2026-10-19T21:40:00.000Z")];
    const lote = [log(11, base), log(12, base), log(13, base)];
    const resultado = fusionarEnVivo(acumulado, lote);
    expect(resultado.map((l) => l.id)).toEqual([13, 12, 11, 50]);
  });

  it("NUNCA muta los arrays de entrada", () => {
    const acumulado = [log(100, "2026-10-19T21:00:00.000Z"), log(50, "2026-10-19T20:00:00.000Z")];
    const lote = [log(1, "2026-10-19T22:00:00.000Z"), log(2, "2026-10-19T22:00:00.000Z")];
    const copiaAcumulado = [...acumulado];
    const copiaLote = [...lote];
    const resultado = fusionarEnVivo(acumulado, lote);
    expect(acumulado).toEqual(copiaAcumulado);
    expect(lote).toEqual(copiaLote);
    expect(resultado).not.toBe(acumulado);
    expect(resultado.length).toBe(4);
    // los objetos originales son referencias compartidas, pero el ARRAY nuevo
    expect(resultado[0].id).toBe(2);
  });
});