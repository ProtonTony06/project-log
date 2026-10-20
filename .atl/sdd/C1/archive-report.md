---
change: "C1"
artifact: archive-report
project: "Project_Log"
last_updated: 2026-10-20
status: archived
---

# Archive — C1 · Fase 0: Setup de pila y repo

## 1. Resumen de cierre

Repo mono-módulo creado: 6 commits en `main` (`bd60b0d` init → `521582b`
backend → `c331148` frontend → `e861d7b` docker + fixes `2394c4c` D4 y
`26236f8` D5), **32 ficheros versionados** = design §1.1 1:1 (D6: la lista
`[1]–[32]` es autoridad sobre el «31»). Pila completa (postgres/backend/
frontend, red `project_log_net`, volumen `project_log_pgdata`) **green con
podman 5.8.7 rootless**. Versiones exactas: Boot 3.5.16 · Java 21 (Temurin
21.0.12) · Maven 3.9.16 · Flyway 11.7.2 · Vite 7.3.7 · React 19.3.1 · TS
5.9.3 · nginx:stable-alpine · postgres:17-alpine; cero `latest`.
Fases: explore → proposal (aprobada 5/5) → spec → design → tasks (26/26) →
apply (D1–D7) → verify **PASS-with-notes**: 12/12 escenarios E1–E12 CONFIRMADO
con evidencia, R1–R15 cubiertos, contratos literales OK, cero CRITICAL.

## 2. Registros nuevos (aplicados en C1)

- **R-nuevo-1** — `spring-boot-starter-jdbc` (fix `2394c4c`): en Boot 3.5.x,
  sin spring-jdbc+HikariCP no se autoconfigura `DataSource` y el arranque sube
  Tomcat **sin ejecutar Flyway** (R6/E3 inalcanzables). `spring-boot-starter-
  flyway` no existe en 3.5.x (es de Boot 4). Infra mínima: sin beans de
  negocio ni JPA; `ddl-auto` sigue `none`. Sin eco en docs.
- **R-nuevo-2** — Docker/Compose v2 ausentes en el host dev: verificación con
  podman rootless equivalente (red/DNS, healthchecks, volumen, restart:
  totales; encadenado `depends_on` y `docker compose config`: emulados, no
  canónicos). Smoke canónico `docker compose` **obligatorio re-verificar en C9**.

## 3. Acciones heredadas a cortes futuros

| Corte | Acción |
|---|---|
| **C9** (obligatorio) | Primera acción: `docker compose config` sin errores → `docker compose up -d` en la VM → smoke canónico completo (`scripts/ws_echo_test.py`, incl. inactividad) contra pila Docker real (W1/R-nuevo-2). |
| **C7** | Reutilizar `ws_echo_test.py --solo-handshake` y `--token` tal cual (R4); sustituir Echo por LogSocketHandler cambiando solo el registro en `WebSocketConfig`. |
| **C2** | Fijar versiones de `frontend/package.json` sin prefijos `^`/`~` (S2). |
| Docs (próx. validación) | Alinear 2 líneas del bloque de ejemplo TRD §6.3 con su propia tabla (`SPRING_DATASOURCE_USERNAME`/`PASSWORD`, D7) — NO bloqueante. Corregir atribución del hash de D7: fix en `e861d7b`, no en `26236f8` (S1). |

## 4. Estado de artefactos

| Fichero | Estado final |
|---|---|
| explore.md, spec.md, design.md, tasks.md | done (tasks 26/26) |
| proposal.md | aprobado (2026-10-20, 5/5) |
| apply-progress.md | done (D1–D7 documentadas) |
| verify-report.md | done (PASS-with-notes) |
| archive-report.md | archived (este fichero) |

## 5. Lo que NO se hizo

- Sin `qa-report.md` (decisión del coordinador: verificación apoyada en apply-progress + spot-check estático de verify; limitación, no fallo).
- Sin push a remote (no existe); solo `main` local, identidad provisional reescribible.
- Sin tocar `/docs`: ningún doc `valido` fue modificado (ecos en §3).