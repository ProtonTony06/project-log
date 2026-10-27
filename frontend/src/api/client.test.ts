// Vitest puro SIN DOM (D-C8-11/E8.56): tolerancias del cliente API
// (E8.14–E8.17). El mock de fetch CRASHEA si se lee el body en un 401 —
// aserto de no-invocación de la regla R8.14 (decisión solo con status).
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  asignarBearer,
  registrarInterceptor401,
  request,
} from "./client";

function respJson(status: number, cuerpo: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(typeof cuerpo === "string" ? cuerpo : JSON.stringify(cuerpo)),
  } as unknown as Response;
}

function resp401Trampa(): Response & { __leido: () => boolean } {
  let bodyLeido = false;
  const r = {
    ok: false,
    status: 401,
    json: () => {
      bodyLeido = true;
      throw new Error("json() invocado en 401 — prohibido (R8.14)");
    },
    text: () => {
      bodyLeido = true;
      throw new Error("text() invocado en 401 — prohibido (R8.14)");
    },
    __leido: () => bodyLeido,
  };
  return r as unknown as Response & { __leido: () => boolean };
}

afterEach(() => {
  vi.unstubAllGlobals();
  asignarBearer(null);
});

describe("401: NUNCA se lee el body (E8.14/R8.14)", () => {
  it("con bearer y interceptor registrado: interceptor + ApiError sin crash", async () => {
    const resp = resp401Trampa();
    const fetchMock = vi.fn().mockResolvedValue(resp);
    vi.stubGlobal("fetch", fetchMock);

    const llamados: string[] = [];
    registrarInterceptor401(() => {
      llamados.push("cb");
    });
    asignarBearer("tok");

    let err: unknown = null;
    try {
      await request("/api/v1/panel/me");
    } catch (e) {
      err = e;
    }

    expect(err).toBeInstanceOf(ApiError);
    const apiErr = err as ApiError;
    expect(apiErr.status).toBe(401);
    expect(apiErr.message).toBe("HTTP 401");
    expect(llamados).toEqual(["cb"]);
    expect(resp.__leido()).toBe(false); // aserto de no-invocación
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("con bearer:false (login): NO pasa por el interceptor (única excepción R8.8)", async () => {
    const resp = resp401Trampa();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(resp));
    const llamados: string[] = [];
    registrarInterceptor401(() => {
      llamados.push("cb");
    });

    let err: unknown = null;
    try {
      await request("/api/v1/panel/auth/login", { method: "POST", bearer: false });
    } catch (e) {
      err = e;
    }
    expect((err as ApiError).status).toBe(401);
    expect(llamados).toEqual([]);
    expect(resp.__leido()).toBe(false);
  });

  it("repite el callback en cada 401 (idempotencia por repetición, R8.16)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(resp401Trampa()),
    );
    let n = 0;
    registrarInterceptor401(() => {
      n += 1;
    });
    asignarBearer("tok");
    await expect(request("/a")).rejects.toBeInstanceOf(ApiError);
    await expect(request("/a")).rejects.toBeInstanceOf(ApiError);
    expect(n).toBe(2);
  });
});

describe("no-200 tolerantes (E8.15/R8.15)", () => {
  it("405 en HTML (Spring): fallback 'HTTP 405'", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        respJson(405, `<!DOCTYPE html><html><body>Whitelabel Error</body></html>`),
      ),
    );
    const err = (await request("/x").catch((e) => e)) as ApiError;
    expect(err.status).toBe(405);
    expect(err.code).toBeUndefined();
    expect(err.message).toBe("HTTP 405");
  });

  it("502 en texto plano de proxy: fallback 'HTTP 502'", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(respJson(502, "nginx: 502 Bad Gateway")),
    );
    const err = (await request("/x").catch((e) => e)) as ApiError;
    expect(err.status).toBe(502);
    expect(err.message).toBe("HTTP 502");
  });

  it("cuerpo VACÍO (300/409 sin body): fallback 'HTTP <status>'", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respJson(409, "")));
    const err = (await request("/x").catch((e) => e)) as ApiError;
    expect(err.status).toBe(409);
    expect(err.message).toBe("HTTP 409");
  });

  it("404 JSON: code/message/field PARSEADOS (E8.15)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        respJson(404, {
          code: "not_found",
          message: "El log no existe",
          field: "id",
        }),
      ),
    );
    const err = (await request("/x").catch((e) => e)) as ApiError;
    expect(err.status).toBe(404);
    expect(err.code).toBe("not_found");
    expect(err.message).toBe("El log no existe");
    expect(err.field).toBe("id");
  });
});

describe("éxito y cabeceras", () => {
  it("Bearer en todo MENOS login (bearer false) (E8.15)", async () => {
    const fetchMock = vi.fn().mockResolvedValue(respJson(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    asignarBearer("secreto");

    await request("/api/v1/panel/projects");
    expect((fetchMock.mock.calls[0][1] as RequestInit).headers).toMatchObject({
      Authorization: "Bearer secreto",
    });

    await request("/api/v1/panel/auth/login", {
      method: "POST",
      body: { username: "antonio", password: "x" },
      bearer: false,
    });
    const headersLogin = (fetchMock.mock.calls[1][1] as RequestInit).headers as Record<
      string,
      string
    >;
    expect(headersLogin["Authorization"]).toBeUndefined();
    expect(headersLogin["Content-Type"]).toBe("application/json");
  });

  it("204 ⇒ undefined SIN parsear body (revocar key)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 204,
        text: () => Promise.reject(new Error("no se debe leer un 204")),
      } as unknown as Response),
    );
    const r = await request<void>("/x");
    expect(r).toBeUndefined();
  });

  it("headers extra viajan (X-Confirm-Log-Id — S8-2)", async () => {
    const fetchMock = vi.fn().mockResolvedValue(respJson(200, {}));
    vi.stubGlobal("fetch", fetchMock);
    await request("/x", { method: "DELETE", headers: { "X-Confirm-Log-Id": "48211" } });
    expect((fetchMock.mock.calls[0][1] as RequestInit).headers).toMatchObject({
      "X-Confirm-Log-Id": "48211",
    });
  });
});

describe("red caída (E8.17/F-C11)", () => {
  it("fetch rechaza ⇒ ApiError status 0 message 'HTTP 0'", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("network down")));
    const err = (await request("/x").catch((e) => e)) as ApiError;
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(0);
    expect(err.message).toBe("HTTP 0");
  });
});