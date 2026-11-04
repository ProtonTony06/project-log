// Vitest puro SIN DOM (D-C8-11): helperPURO `debeReenviarFiltros` del guard
// por contenido de useLogsStream (D-C10-12 — fix H-C9-E9.10/REQ-C10-E910-1).
// La certificación real es el re-run E9.10 del arnés (WsContador: delta 1
// de frames set_filters por cambio real, delta 0 en renders ajenos).
import { describe, expect, it } from "vitest";
import { debeReenviarFiltros } from "./useLogsStream";
import type { SetFilters } from "../api/types";

const VACIO: SetFilters = {
  project_id: null,
  environment: null,
  level: null,
  from: null,
  to: null,
  text: null,
};

describe("debeReenviarFiltros (D-C10-12/REQ-C10-E910-1)", () => {
  it("primera vez (clave previa null) ⇒ true — el primer set_filters se envía", () => {
    expect(debeReenviarFiltros(null, VACIO)).toBe(true);
  });

  it("MISMA clase (contenidos iguales) ⇒ false — delta 0 en renders ajenos", () => {
    expect(debeReenviarFiltros(JSON.stringify(VACIO), VACIO)).toBe(false);
    // el caso REAL de E9.10: otra IDENTIDAD con el mismo contenido (el hook
    // compara por contenido, no por referencia)
    const mismaClase = { ...VACIO };
    expect(debeReenviarFiltros(JSON.stringify(VACIO), mismaClase)).toBe(false);
    // un objeto reconstruido con claves en el mismo orden también matchea
    const reconstruido: SetFilters = {
      project_id: null,
      environment: null,
      level: null,
      from: null,
      to: null,
      text: null,
    };
    expect(debeReenviarFiltros(JSON.stringify(reconstruido), reconstruido)).toBe(false);
  });

  it("cambio REAL de contenido ⇒ true — UN frame por cambio (delta 1)", () => {
    const conTexto: SetFilters = { ...VACIO, text: "qa-c10" };
    expect(debeReenviarFiltros(JSON.stringify(VACIO), conTexto)).toBe(true);
    // y cada cambio sucesivo distinto también
    const otroTexto: SetFilters = { ...conTexto, text: "otro" };
    expect(debeReenviarFiltros(JSON.stringify(conTexto), otroTexto)).toBe(true);
    const conProyecto: SetFilters = { ...otroTexto, project_id: 3 };
    expect(debeReenviarFiltros(JSON.stringify(otroTexto), conProyecto)).toBe(
      true,
    );
  });

  it("null ⇒ valor ≠ null: cualquier filtro concreto tras null reenvía", () => {
    const conProyecto: SetFilters = { ...VACIO, project_id: 7 };
    expect(debeReenviarFiltros(null, conProyecto)).toBe(true);
  });
});