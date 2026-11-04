// Store del acumulado keyset (E8.35 post-parche D-C8-8): al abrir el
// detalle (ruta hermana) /logs se desmonta; la instantánea transporta el
// acumulado al re-montaje. Los FILTROS no pasan por aquí (viven en la
// query string — D-C8-6). Tests puristas SIN DOM ni router.
import { describe, expect, it } from "vitest";
import {
  consumirListado,
  guardarListado,
  leerListado,
} from "./storeListado";
import { expulsarPorId } from "./usePagination";
import type { LogListItemDto } from "../api/types";

const item: LogListItemDto = {
  id: 42,
  project_id: 1,
  level: "ERROR",
  environment: "development",
  timestamp: "2026-10-28T09:59:59+02:00",
  received_at: "2026-10-28T10:00:00Z",
  message: "smoke-store-uno",
  source: "smoke",
  correlation_id: "corr-store-1",
};

describe("storeListado (E8.35 — acumulado entre el detalle y el retorno)", () => {
  it("por defecto NO hay instantánea: /logs arranca limpio", () => {
    expect(leerListado()).toBeNull();
    expect(consumirListado("")).toBeNull();
  });

  it("guardar → consumir con la MISMA search devuelve el acumulado entero", () => {
    guardarListado("project_id=3&level=ERROR", {
      items: [item],
      nextCursor: "cursor-1",
    });
    const tomada = consumirListado("project_id=3&level=ERROR");
    expect(tomada).not.toBeNull();
    expect(tomada?.search).toBe("project_id=3&level=ERROR");
    expect(tomada?.acumulado.items).toEqual([item]);
    expect(tomada?.acumulado.nextCursor).toBe("cursor-1");
  });

  it("consumir ES destructivo: la 2.ª vez devuelve null (no rehidrata vieja)", () => {
    expect(consumirListado("project_id=3&level=ERROR")).toBeNull();
    expect(leerListado()).toBeNull();
  });

  it("search distinta (carga directa / retorno de TopBar) ⇒ null y descarta", () => {
    guardarListado("text=hola", {
      items: [item],
      nextCursor: null,
    });
    // otra entrada a /logs SIN la query guardada ⇒ arranca limpio (R8.20),
    // y la instantánea NO sobrevive para sorprender después.
    expect(consumirListado("")).toBeNull();
    expect(consumirListado("text=hola")).toBeNull();
  });

  it("guardar SUSTITUYE (volver al detalle dos veces conserva la última)", () => {
    guardarListado("text=a", { items: [], nextCursor: null });
    guardarListado("text=b", { items: [item], nextCursor: "c2" });
    const tomada = consumirListado("text=b");
    expect(tomada?.search).toBe("text=b");
    expect(tomada?.acumulado.nextCursor).toBe("c2");
  });

  it("pin F7 (D-C10-10/REQ-C10-E913-1): la search viaja CON su «?» inicial y coincide EXACTA", () => {
    // Forma nueva de las pages (fix E9.13): location.search tal cual —
    // `?text=…`. La igualdad EXACTA del consumo la recupera (round-trip E8.35).
    guardarListado("?text=qa-c10", { items: [item], nextCursor: null });
    const tomada = consumirListado("?text=qa-c10");
    expect(tomada?.search).toBe("?text=qa-c10");
    expect(tomada?.acumulado.items).toEqual([item]);
  });

  it("la search MALFORMADA con doble «?» JAMÁS consume la snapshot viva", () => {
    // La forma antigua (bug E9.13: «?» interpolado sobre location.search)
    // daba "??text=…" — la igualdad EXACTA del store la rechaza ⇒ el
    // acumulado se hubiese perdido. Pin: con snapshot viva, «??» ⇒ null.
    guardarListado("?text=qa-c10", { items: [item], nextCursor: null });
    expect(consumirListado("??text=qa-c10")).toBeNull();
  });

  it("fix fila stale 8b (C10): round-trip con search EXACTA + expulsión del borrado", () => {
    // Nota c10-nota-8b: tras el 204 del DELETE la vuelta consume la
    // snapshot (E8.35, sin refetch) y la fila borrada se EXPULSA por id —
    // el store NO se toca: la expulsión actúa sobre lo CONSUMIDO.
    const vecino: LogListItemDto = { ...item, id: 43 };
    guardarListado("?text=qa-c10", { items: [item, vecino], nextCursor: null });

    // La vuelta del detalle usa la MISMA search EXACTA (con su «?»)…
    const tomada = consumirListado("?text=qa-c10");
    expect(tomada?.search).toBe("?text=qa-c10");
    // …y el id del borrado (location.state.idBorrado) expulsa SOLO esa fila.
    const tras = expulsarPorId(tomada!.acumulado, 42);
    expect(tras.items.map((i) => i.id)).toEqual([43]); // fila stale fuera
    expect(tras.nextCursor).toBeNull();
    // La snapshot consumida NO mutó (la expulsión es inmediata y pura).
  });
});