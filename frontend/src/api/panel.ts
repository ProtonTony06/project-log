// Una función por endpoint del panel (§5.3, D-C8-3/D-C8-10). Rutas RELATIVAS
// de mismo origen (R8.53). Sin ?include=summary en C8 (R8.18).
import {
  asignarBearer,
  request,
} from "./client";
import type {
  ApiKeyResponse,
  EstadoProyecto,
  FiltrosLogs,
  LoginResponse,
  LogDto,
  LogsPagina,
  MeResponse,
  ProjectDto,
} from "./types";

/** POST /api/v1/panel/auth/login — ÚNICO endpoint sin Bearer y FUERA del
 * interceptor 401 (R8.8): su 401 cae como ApiError inline (E8.8). */
export async function login(
  username: string,
  password: string,
): Promise<LoginResponse> {
  const r = await request<LoginResponse>("/api/v1/panel/auth/login", {
    method: "POST",
    body: { username, password },
    bearer: false,
  });
  asignarBearer(r.token);
  return r;
}

/** GET /api/v1/panel/me — restauración de sesión (R8.2). */
export function me(): Promise<MeResponse> {
  return request<MeResponse>("/api/v1/panel/me");
}

export function listaProyectos(): Promise<ProjectDto[]> {
  return request<ProjectDto[]>("/api/v1/panel/projects");
}

export function proyecto(id: number): Promise<ProjectDto> {
  return request<ProjectDto>(`/api/v1/panel/projects/${id}`);
}

/** POST /api/v1/panel/projects — 201 ProjectDto (name 1..200, description ≤2000). */
export function crearProyecto(d: {
  name: string;
  description?: string;
}): Promise<ProjectDto> {
  return request<ProjectDto>("/api/v1/panel/projects", {
    method: "POST",
    body: d,
  });
}

/** PATCH /api/v1/panel/projects/{id} — SOLO campos modificados (R8.43);
 * description a null la vacía (borra el campo en el backend). */
export function editarProyecto(
  id: number,
  d: { name?: string; description?: string | null },
): Promise<ProjectDto> {
  return request<ProjectDto>(`/api/v1/panel/projects/${id}`, {
    method: "PATCH",
    body: d,
  });
}

/** PATCH {status} — archivar/desarchivar va por PATCH, NUNCA DELETE (R8.39). */
export function cambiarEstado(
  id: number,
  status: EstadoProyecto,
): Promise<ProjectDto> {
  return request<ProjectDto>(`/api/v1/panel/projects/${id}`, {
    method: "PATCH",
    body: { status },
  });
}

/** POST .../api-key/regenerate — 201 {key, prefix, created_at} (E8.43). */
export function regenerarApiKey(id: number): Promise<ApiKeyResponse> {
  return request<ApiKeyResponse>(`/api/v1/panel/projects/${id}/api-key/regenerate`, {
    method: "POST",
  });
}

/** DELETE .../api-key — 204 (E8.44). */
export function revocarApiKey(id: number): Promise<void> {
  return request<void>(`/api/v1/panel/projects/${id}/api-key`, {
    method: "DELETE",
  });
}

/** GET /api/v1/panel/logs — keyset {items, next_cursor}; sin filtros ⇒ sin
 * parámetros (S8-7: defecto limit 50); nulls se serializan FUERA. */
export function logs(
  f: FiltrosLogs,
  cursor: string | null,
): Promise<LogsPagina> {
  const params = new URLSearchParams();
  if (f.project_id !== null) params.set("project_id", String(f.project_id));
  if (f.environment !== null) params.set("environment", f.environment);
  if (f.level !== null) params.set("level", f.level);
  if (f.from !== null) params.set("from", f.from);
  if (f.to !== null) params.set("to", f.to);
  if (f.text !== null) params.set("text", f.text);
  if (cursor !== null) params.set("cursor", cursor);
  const qs = params.toString();
  return request<LogsPagina>(`/api/v1/panel/logs${qs ? `?${qs}` : ""}`);
}

/** GET /api/v1/panel/logs/{id} — LogDto 11 campos; 404 not_found (E8.36). */
export function log(id: number | string): Promise<LogDto> {
  return request<LogDto>(`/api/v1/panel/logs/${id}`);
}

// ─── DELETEs de C10: hoy 405/404 — Promise<never> documenta que no hay happy
// path sin fingir éxito. Las cabeceras X-Confirm-* ya se envían FORMADAS (S8-2).

/** DELETE /api/v1/panel/projects/{id} con X-Confirm-Name (modal 8a). */
export async function eliminarProyecto(
  id: number,
  nombreConfirmado: string,
): Promise<never> {
  return request<never>(`/api/v1/panel/projects/${id}`, {
    method: "DELETE",
    headers: { "X-Confirm-Name": nombreConfirmado },
  });
}

/** DELETE /api/v1/panel/projects/{id}/logs con X-Confirm-Name (modal 8c). */
export async function purgarLogs(
  id: number,
  nombreConfirmado: string,
): Promise<never> {
  return request<never>(`/api/v1/panel/projects/${id}/logs`, {
    method: "DELETE",
    headers: { "X-Confirm-Name": nombreConfirmado },
  });
}

/** DELETE /api/v1/panel/logs/{id} con X-Confirm-Log-Id (modal 8b — E8.38). */
export async function eliminarLog(
  id: number,
  confirmacionId: number,
): Promise<never> {
  return request<never>(`/api/v1/panel/logs/${id}`, {
    method: "DELETE",
    headers: { "X-Confirm-Log-Id": String(confirmacionId) },
  });
}