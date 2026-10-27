// Vitest puro SIN DOM (D-C8-11/E8.56): comparador del modal V11
// (E8.37/E8.47c) — LITERAL con trim, compartido por 8a/8b/8c.
import { describe, expect, it } from "vitest";
import { coincideLiteralConTrim } from "./Modal";

describe("coincideLiteralConTrim (E8.37/E8.47c — V10/V11)", () => {
  it("coincidencia exacta → true", () => {
    expect(coincideLiteralConTrim("DELETE /logs/48211", "DELETE /logs/48211")).toBe(true);
  });

  it("espacios al INICIO y al FINAL se recortan (trim) → true", () => {
    expect(coincideLiteralConTrim("payments-service", "  payments-service  ")).toBe(true);
    expect(coincideLiteralConTrim("payments-service", "\tpayments-service\n")).toBe(true);
  });

  it("texto incompleto → false", () => {
    expect(
      coincideLiteralConTrim(
        "DELETE /logs/48211 · header X-Confirm-Log-Id: 48211",
        "DELETE /logs/48211 · header X-Confirm-Log-Id",
      ),
    ).toBe(false);
  });

  it("texto distinto (una letra de diferencia) → false", () => {
    expect(coincideLiteralConTrim("payments-service", "paymens-service")).toBe(false);
  });

  it("case-sensitivity: NO se normaliza capitalización (literal)", () => {
    expect(coincideLiteralConTrim("payments-service", "Payments-Service")).toBe(false);
  });

  it("espacios INTERNOS NO se normalizan (solo trim inicial/final)", () => {
    expect(
      coincideLiteralConTrim(
        "DELETE /logs/48211 · header X-Confirm-Log-Id: 48211",
        "DELETE  /logs/48211 · header X-Confirm-Log-Id: 48211",
      ),
    ).toBe(false);
  });

  it("sentencia real de 8b con espacios en los extremos → true (E8.38 flujo)", () => {
    const sentencia = "DELETE /logs/48211 · header X-Confirm-Log-Id: 48211";
    expect(coincideLiteralConTrim(sentencia, ` ${sentencia} `)).toBe(true);
  });

  it("cadena vacía contra esperado no vacío → false", () => {
    expect(coincideLiteralConTrim("48211", "")).toBe(false);
    expect(coincideLiteralConTrim("   ", "")).toBe(true); // ambos trimmeados a ""
  });
});