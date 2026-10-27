// Vitest puro SIN DOM (D-C8-11/E8.56): acumulado keyset (E8.24).
import { describe, expect, it } from "vitest";
import {
  acumular,
  esFinDeResultados,
  paginaInicial,
  primeraPagina,
} from "./usePagination";
import type { LogListItemDto } from "../api/types";

function item(id: number): LogListItemDto {
  return {
    id,
    project_id: 1,
    level: "INFO",
    environment: "production",
    timestamp: "2026-10-19T20:00:00.000Z",
    received_at: "2026-10-19T20:00:00.000Z",
    message: `m${id}`,
    source: null,
    correlation_id: null,
  };
}

describe("paginaInicial", () => {
  it("vacío legítimo: {items: [], nextCursor: null} (estado ≠ 404 ≠ error)", () => {
    const a = paginaInicial<LogListItemDto>();
    expect(a.items).toEqual([]);
    expect(esFinDeResultados(a)).toBe(true); // null ⇒ fin (sin páginas aún)
    expect(a.items.length).toBe(0);
  });
});

describe("acumular", () => {
  it("acumula páginas y guarda el nuevo cursor (E8.24)", () => {
    const p1 = { items: [item(10), item(9)], next_cursor: "eyJyYXciOjV9" };
    let a = acumular(paginaInicial<LogListItemDto>(), p1);
    expect(a.items.map((i) => i.id)).toEqual([10, 9]);
    expect(a.nextCursor).toBe("eyJyYXciOjV9");

    const p2 = { items: [item(8), item(7)], next_cursor: "eyJyYXciOjN9" };
    a = acumular(a, p2);
    expect(a.items.map((i) => i.id)).toEqual([10, 9, 8, 7]); // NO renuncia al
    // acumulado: R8.23 "Cargar más" pide cursor y ACUMULA
    expect(a.nextCursor).toBe("eyJyYXciOjN9");
  });

  it("NUNCA muta el acumulado previo", () => {
    const previo = { items: [item(10)], nextCursor: "c2" as string | null };
    const siguiente = acumular(previo, { items: [item(9)], next_cursor: null });
    expect(previo.items.map((i) => i.id)).toEqual([10]);
    expect(siguiente).not.toBe(previo);
  });
});

describe("esFinDeResultados / primeraPagina", () => {
  it("next_cursor null ⇒ fin de resultados (tras ≥1 página)", () => {
    const a = acumular(paginaInicial<LogListItemDto>(), {
      items: [item(10)],
      next_cursor: null,
    });
    expect(esFinDeResultados(a)).toBe(true);
  });

  it("primeraPagina reemplaza TODO (cambio de filtros / refetch reconexión)", () => {
    const a = acumular(paginaInicial<LogListItemDto>(), {
      items: [item(10), item(9)],
      next_cursor: "c2",
    });
    const b = primeraPagina<LogListItemDto>({ items: [item(1)], next_cursor: "n1" });
    expect(b.items.map((i) => i.id)).toEqual([1]);
    expect(b.nextCursor).toBe("n1");
    expect(a.items.length).toBe(2); // a no tocado
  });
});