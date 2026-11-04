---
project: "Project_Log"
artifact: context
last_updated: 2026-10-29
status: active
---

# Project_Log — Contexto SDD

## Qué es
Servicio de ingesta, consulta y streaming en vivo de logs de runtime para el
homelab del usuario, desplegado con Docker Compose en una VM Proxmox por red
interna. Monousuario, sin registro público, sin integraciones externas.

## Estado actual (2026-10-29)
- Repo git activo en `main` (base C9: 27b62cb, sin push). Pila local podman
  `:8080` arriba; deploy en VM 108 «projectlog» (192.168.1.58, docker compose
  v5.6.1, firewall nftables GATE #11) cerrado en C9.
- Suites: mvn 177/177, Vitest 55/55, smokes canónicos `scripts/qa_c*`,
  arnés visual Playwright `scripts/qa_vis_c9.{py,sh}` (26 checks).
- Cortes C1–C9: `estado: hecho`. Siguiente (y ÚLTIMO de Fase 1+2): **C10**
  (Fase 2: borrados definitivos, resumen, acceso remoto) + fixes ratificados
  H-C9-WS-COLGADO / E9.13 / E9.2 / E9.10 + UX-401-logout (DEV-C8-4).

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