// DTOs LITERALES de docs/05_BACKEND_SCHEMA.md §3.2/§3.3/§3.5 — R8.19 (D-C8-6).
// NADA se redefine: citados. Resumen por proyecto (flujo 9, C10): el campo
// `summary` (§3.3) solo viaja en `GET /projects?include=summary` (R8.18 C9
// matizado por REQ-C10-UI-8 — la ficha usa el endpoint GET /projects/{id}/summary).

export type Nivel = "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR" | "FATAL";
export type Entorno = "development" | "staging" | "production";
export type EstadoProyecto = "ACTIVE" | "ARCHIVED";

// §3.2 login — orden literal {token, username, expires_at}
export interface LoginResponse {
  token: string;
  username: string;
  expires_at: string;
}

// §3.2 — GET /me
export interface MeResponse {
  username: string;
  role: "ADMIN";
  expires_at: string;
}

// §3.3 — ProjectDto SIN summary salvo en la lista pedida con
// `?include=summary` (C10: el backend lo omite con @JsonInclude NON_NULL —
// ausencia, no null). La ficha consume GET /projects/{id}/summary (M3).
export interface ProjectDto {
  id: number;
  name: string;
  description: string | null;
  status: EstadoProyecto;
  created_at: string;
  updated_at: string;
  api_key_prefix: string | null;
  has_key: boolean;
  /** SOLO en GET /projects?include=summary (REQ-C10-RESUMEN-1/2). */
  summary?: SummaryDto;
}

// §3.3 — SummaryDto del resumen (flujo 9, literal EXACTO de 4 claves):
// `{"by_level": {...}, "by_environment": {...}, "total": 55, "last_log_at":
// "2026-10-16T22:31:04Z"}` — mapas con recuento por cada nivel/entorno
// PRESENTE (REQ-C10-RESUMEN-4, solo claves presentes); `last_log_at` es
// `null` con proyecto sin logs (estado vacío informativo, flujo 9 paso 4).
export interface SummaryDto {
  by_level: Partial<Record<Nivel, number>>;
  by_environment: Partial<Record<Entorno, number>>;
  total: number;
  last_log_at: string | null;
}

// §3.3 — regenerate 201, orden literal {key, prefix, created_at}
export interface ApiKeyResponse {
  key: string;
  prefix: string;
  created_at: string;
}

// Listado — LogListItemDto de 9, SIN exception/metadata (V6)
export interface LogListItemDto {
  id: number;
  project_id: number;
  level: Nivel;
  environment: Entorno;
  timestamp: string;
  received_at: string;
  message: string;
  source: string | null;
  correlation_id: string | null;
}

// Detalle — LogDto de 11 (los 9 del listado + estos 2)
export interface LogDto extends LogListItemDto {
  exception: string | null;
  metadata: Record<string, unknown> | null;
}

// GET /logs — cursor opaco keyset; next_cursor null = fin (explícito, R6.12)
export interface LogsPagina {
  items: LogListItemDto[];
  next_cursor: string | null;
}

// Filtros de GET /logs (D-C8-6): nulls = filtro no aplicado. Definido aquí
// (módulo de infraestructura) para que api/ y hooks/ lo compartan SIN ciclo;
// hooks/useLogFilters.ts lo re-exporta (forma idéntica a D-C8-6).
export interface FiltrosLogs {
  project_id: number | null;
  environment: Entorno | null;
  level: Nivel | null;
  from: string | null;
  to: string | null;
  text: string | null;
}

// Errores 400/403/404/409 — {code, message, field?}
export interface ErrorBody {
  code: string;
  message: string;
  field?: string;
}

// §3.5 set_filters — 6 claves SIEMPRE presentes, null donde no aplica (S8-8)
export interface SetFilters {
  project_id: number | null;
  environment: Entorno | null;
  level: Nivel | null;
  from: string | null;
  to: string | null;
  text: string | null;
}

// §3.5 frame batch — 5 claves; loguea LogDto COMPLETO (11)
export interface FrameBatch {
  type: "batch";
  batch_id: string;
  project_id: number;
  received_at: string;
  logs: LogDto[];
}