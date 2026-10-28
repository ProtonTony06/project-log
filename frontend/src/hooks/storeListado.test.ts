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
});