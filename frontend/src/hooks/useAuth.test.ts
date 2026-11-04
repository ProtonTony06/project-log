// Vitest puro SIN DOM (D-C8-11): helpers PUROS del arranque post-login de
// Login (D-C10-11 — fix H-C9-E9.2/REQ-C10-E92-1/2). La certificación real es
// el re-run E9.2 endurecido del arnés (URL PERDURANDO ~700 ms, learned 1).
import { describe, expect, it } from "vitest";
import {
  destinoTrasLogin,
  elNavigateGana,
} from "./useAuth";

describe("destinoTrasLogin (D-C10-11/REQ-C10-E92-2)", () => {
  it("sin destino guardado ⇒ /logs (REQ-C10-E92-2, sin doble salto)", () => {
    expect(destinoTrasLogin(null)).toBe("/logs");
  });

  it("destino ya determinado ⇒ viaja TAL CUAL (sin completar a /logs)", () => {
    expect(destinoTrasLogin("/logs")).toBe("/logs");
    expect(destinoTrasLogin("/proyectos")).toBe("/proyectos");
  });

  it("destino CON query intacta — ruta+search del guard (R8.5/E8.3/R9.2.2)", () => {
    expect(destinoTrasLogin("/proyectos/99999999")).toBe(
      "/proyectos/99999999",
    );
    expect(destinoTrasLogin("/proyectos/99999999?text=qa-c10")).toBe(
      "/proyectos/99999999?text=qa-c10",
    );
    expect(destinoTrasLogin("/logs?project_id=3&level=ERROR")).toBe(
      "/logs?project_id=3&level=ERROR",
    );
  });
});

describe("elNavigateGana (D-C10-11/REQ-C10-E92-1 — predicate EXACTO del render)", () => {
  it("autenticado SIN transición propia (llegada ya autenticada) ⇒ Navigate a /", () => {
    expect(elNavigateGana("autenticado", false)).toBe(true);
  });

  it("autenticado EN la transición propia del login ⇒ el Navigate NO gana (una sola navegación)", () => {
    expect(elNavigateGana("autenticado", true)).toBe(false);
  });

  it("no-autenticado no gana jamás (anonimo/comprobando)", () => {
    expect(elNavigateGana("anonimo", false)).toBe(false);
    expect(elNavigateGana("comprobando", false)).toBe(false);
    expect(elNavigateGana("anonimo", true)).toBe(false);
  });
});