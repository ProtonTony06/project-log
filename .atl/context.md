---
project: "Project_Log"
artifact: context
last_updated: 2026-10-20
status: active
---

# Project_Log — Contexto SDD

## Qué es
Servicio de ingesta, consulta y streaming en vivo de logs de runtime para el
homelab del usuario, desplegado con Docker Compose en una VM Proxmox por red
interna. Monousuario, sin registro público, sin integraciones externas.

## Estado actual (2026-10-20)
- **El proyecto NO tiene código todavía**, solo documentación en `/docs`
  (paquete de definición cerrado: 7/7 documentos `valido`).
- No es repositorio git aún (el repo se crea en el corte C1 / Fase 0 Setup).
- Pipeline de planificación: `06_IMPLEMENTATION_PLAN.md` (Fases 0–3, stories
  P0-1…P0-14 + P1 + P2) y `07_SLICING_PLAN.md` (10 cortes C1–C10, cada uno =
  una ejecución completa del flujo SDD; estado por corte en campo `estado:`).
- Todos los cortes C1–C10: `estado: pendiente`. Siguiente: **C1**.

## Stack definido (documentado, no implementado aún)
- Backend: Spring Boot (Java), package-by-feature: `auth, projects, keys,
  ingest, query, streaming` + `config/common`.
- Frontend: Vite + React + TypeScript.
- BD: PostgreSQL (`postgres:17-alpine`) + Flyway (`V1__init.sql` literal del
  Schema §6.1, JSONB + TIMESTAMPTZ).
- Auth: login propio, JWT HS256 (TTL 8 h), Argon2id; ingesta REST con
  API key por proyecto (`plk_`+43 Base64url, solo hash SHA-256).
- Streaming: WebSocket `/ws/logs?token=<JWT>` (handler nativo), emisión
  tras COMMIT, heartbeat 30 s.
- Deploy: Docker Compose (3 servicios), nginx proxy (`/api/v1` + `/ws/logs`),
  único puerto 8080, sin mem_limit (ADR-27).
- Docs de definición (congelados, fuente de verdad de literales):
  `docs/01_PRD.md` v1.2, `02_TRD.md` v1.1, `03_APPFLOW.md` v1.0,
  `04_UIUX.md` v1.0, `05_BACKEND_SCHEMA.md` v1.0, `06_IMPLEMENTATION_PLAN.md`
  v1.0, `07_SLICING_PLAN.md` v1.0, maquetas en `docs/mockups/` (contrato visual).

## Reglas del flujo SDD en este proyecto (de 07_SLICING_PLAN.md)
- Un cambio SDD por corte; no se mezclan stories de cortes distintos.
- En cada corte, `sdd-propose` se presenta al usuario antes de escribir código
  de la pieza nueva (gate de alcance fino).
- Los literales de API/UI son contrato: los specs los CITAN, nunca redefinen.
- `estado:` de cada corte se actualiza al cerrar su `sdd-archive`.
- Fase 3 (P2) no se slicea: solo si el usuario la activa.
- Toda añadidura fuera de cortes = nueva versión del PRD, nunca en el slicing.

## Convenciones del entorno
- Persistencia SDD: **atl** (`.atl/` file-based, sin engram).
- Idioma de la documentación y comunicación: español.
- No hay linters/CI definidos todavía (se fijan en C1).