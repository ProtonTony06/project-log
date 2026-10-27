// ÚNICO fetch de la app (R8.13): todo el panel pasa por request().
// Tolerancias heredadas (R8.14/R8.15, patrón mockups 04+05 del puente C3/C4.5):
// - 401: decisión SOLO con response.status — NUNCA se lee el body (ni json()
//   ni text()): el 401 históricamente salía ISO-8859-1 y el interceptor se
//   basta con el status (regla igual antes/después del fix D1).
// - resto de no-200: r.text() + JSON.parse en try/catch, fallback "HTTP <stat>".
// Cero logging (R8.12): este módulo no loguea nada.

export class ApiError extends Error {
  /** 0 = red caída (fetch sin respuesta); no se construye con status 401-leyendo-body. */
  status: number;
  code?: string;
  message: string;
  field?: string;

  constructor(
    status: number,
    code: string | undefined,
    message: string,
    field?: string,
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.message = message;
    this.field = field;
  }
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  /** Se serializa como JSON si está presente. */
  body?: unknown;
  /** Cabeceras extra (p. ej. X-Confirm-Log-Id / X-Confirm-Name — S8-2). */
  headers?: Record<string, string>;
  /** DEFAULT true; false SOLO para POST /auth/login (R8.8). */
  bearer?: boolean;
}

/** Callback del interceptor global 401 (R8.14/R8.16): descarta token + cierra
 * WS + redirige con destino. Registrado por App.tsx (D-C8-3.4). */
let interceptor401: (() => void) | null = null;

export function registrarInterceptor401(cb: () => void): void {
  interceptor401 = cb;
}

/** Fuente del Bearer: la asigna el bootstrap vía asignarBearer() desde useAuth
 * (client NO importa hooks — dependencia única dirección hooks→api). */
let bearerToken: string | null = null;

export function asignarBearer(token: string | null): void {
  bearerToken = token;
}

export async function request<T>(
  path: string,
  opciones: RequestOptions = {},
): Promise<T> {
  const { method = "GET", body, headers, bearer = true } = opciones;

  const cabeceras: Record<string, string> = { ...headers };
  if (bearer && bearerToken) {
    cabeceras["Authorization"] = `Bearer ${bearerToken}`;
  }
  if (body !== undefined) {
    cabeceras["Content-Type"] = "application/json";
  }

  let resp: Response;
  try {
    resp = await fetch(path, {
      method,
      headers: cabeceras,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    // Red caída (fetch sin respuesta) — E8.17: banner "Sin conexión".
    throw new ApiError(0, undefined, "HTTP 0");
  }

  return finalizar<T>(resp, bearer);
}

async function finalizar<T>(resp: Response, bearer: boolean): Promise<T> {
  if (resp.status === 401) {
    // REGLA 401 (R8.14): decide SOLO con el status; el body NUNCA se lee aquí.
    // El login (bearer:false) no pasa por el interceptor (única excepción — R8.8).
    if (bearer && interceptor401 !== null) {
      interceptor401();
    }
    throw new ApiError(resp.status, undefined, `HTTP ${resp.status}`);
  }

  if (resp.ok) {
    if (resp.status === 204) {
      return undefined as T;
    }
    // Éxito: parse tolerante también (text()+parse) — los 200/201 vienen en JSON
    // por contrato, pero nunca se acopla la UI a un cuerpo inesperado.
    return (await parseSeguro<T>(resp)) as T;
  }

  // Resto de no-200 (R8.15): text() respeta el charset del header; parse en
  // try/catch con fallback "HTTP <status>" (405 de Spring en HTML, 502 de
  // nginx en texto, cuerpo vacío...).
  const texto = await resp.text();
  let code: string | undefined;
  let message: string | undefined;
  let field: string | undefined;
  try {
    const parsed = JSON.parse(texto) as {
      code?: string;
      message?: string;
      field?: string;
    };
    if (parsed && typeof parsed === "object") {
      code = typeof parsed.code === "string" ? parsed.code : undefined;
      message = typeof parsed.message === "string" ? parsed.message : undefined;
      field = typeof parsed.field === "string" ? parsed.field : undefined;
    }
  } catch {
    // cuerpo no-JSON: caemos al fallback.
  }
  throw new ApiError(
    resp.status,
    code,
    message ?? `HTTP ${resp.status}`,
    field,
  );
}

async function parseSeguro<T>(resp: Response): Promise<T> {
  const texto = await resp.text();
  if (texto.length === 0) {
    return undefined as T;
  }
  try {
    return JSON.parse(texto) as T;
  } catch {
    // 200/201 con cuerpo no-JSON inesperado: no romper la UI.
    return undefined as T;
  }
}