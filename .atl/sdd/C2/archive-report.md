---
change: "C2"
artifact: archive-report
last_updated: 2026-10-21
status: archived
---

# Archive report — C2 · Seguridad base + auth del panel

## 1. Resumen de cierre

C2 entregó la seguridad base y la auth del panel sobre C1, tocando exactamente 3 ficheros de C1 (pom, application.yml, compose.yaml) y añadiendo 13 src/main ([N1]–[N13]) + 5 tests: SecurityFilterChain 3 cadenas (@Order 1 ingesta deny-by-default, 2 panel JWT, 3 resto con health/info/ws permitAll + denyAll, stateless/CSRF-off/sin httpBasic-formLogin, usuario in-memory inerte vía exclude de UserDetailsServiceAutoConfiguration — fix 829cc70); Argon2id OWASP 2024 (16,32,1,19456,2) con bcprov-jdk18on 1.86 y PHC; JWT jjwt 0.13.0 HS256, claims cerrados {sub,role,iat,exp}, TTL 8 h, sin skew/refresh/logout; seed idempotente AdminSeeder (COUNT tabla-vacía + ON CONFLICT DO NOTHING, literal Schema §6.3); login/me según Schema (401 constantes `invalid_credentials`/`invalid_token`); CORS mínimo FRONTEND_ORIGIN (reflejo exacto, sin wildcard/credentials, maxAge 3600, cerrado sin la var). Flujo: explore → proposal gate 5/5 (2026-10-20) → spec (32R/E2.1–22) → design CD-1…CD-9 → tasks 30 → apply DONE → QA 22/27 → verify **PASS-with-notes** (22/22 escenarios, `mvn test` 26/26 verde tras fix Q5). Commits C2: 3c60e86, c051047, f69830c, bdbb258 + fix 829cc70; **HEAD 829cc70**, re-apply Q24 sin diff en main (0 cambios de código, sin commit). Archivado en modo **atl**: audit trail en `.atl/sdd/C2/` (sin sync openspec ni movimientos).

## 2. Cierre de hallazgos QA (todos resueltos; CRITICAL: 0)

- **Q24 (FAIL-real → RESUELTO)**: entorno en 2 capas (env del proceso precede a .env en interpolación compose; .env con placeholder `<ip-vm>` + comentario en línea que `source .env` trunca) — recreate con vars UNSET y .env en forma limpia → 8/8 VERDE, sin tocar `backend/src/main`.
- **Q5 (FAIL test-side → CORREGIDO por verify)**: aserto antivacío del ListAppender en LogHygieneTest eliminado (las 3 clases no loguean, justo S16) + contraseña de test a literales prohibidos; suite 26/26.
- **Q18/Q19 (plan-vs-routing → RESUELTOS)**: re-capturados con `podman inspect` (.State.Health.Status, RestartCount, FailingStreak) y sondeos en-red; `/actuator/**` por proxy = SPA es routing C1 documentado (sin regresión; env → 401 sin contenido en red).
- **Q21 (BLOQUEADO → RESUELTO)**: PASS vs hashes base exactos de la captura QA + `git diff fa42751…HEAD` = 0 líneas (intocables C1).

## 3. Registros nuevos

- **R-nuevo-3 — Veneno de env proceso-vs-.env (patrón Q24)**: la env del PROCESO precede a `.env` en la interpolación `${VAR}` de compose y `restart` no re-lee env ⇒ mutaciones de env de compose se ejecutan SIEMPRE con variables UNSET (disciplina anti-veneno), con recreate (nunca restart); `.env` nunca con placeholders `<>` ni comentarios en línea (bash los trunca).
- **R-nuevo-4 — WARNING: entry point 401 en ISO-8859-1**: el 401 del entry point sale `charset=ISO-8859-1` (Tomcat getWriter() por defecto); JSON no UTF-8 estricto — el frontend de C8 DEBE ser tolerante (no parseo estricto del body en no-200) o el fix es de una línea (`setCharacterEncoding("UTF-8")`) en C8/C9.

## 4. Acciones heredadas

| Corte | Acción |
|---|---|
| C8 | Tolerancia a bodies 401 no-UTF-8 (R-nuevo-4); CORS dev ya cubierto por G1 |
| C9 | Smoke canónico docker compose con disciplina anti-veneno (R-nuevo-3); `.env` real de VM sin placeholders/comentarios; opcional caso de rotación JWT_SECRET (S15) |
| Scripts QA futuros | Preflight CORS asertar `20[46]`/2xx+cabeceras (no 204 literal); salud por `podman inspect`/en-red cuando el routing no cubra la ruta |
| Próx. validación docs | Sugerencia de una línea 829cc70 (nota CD-8a en TRD §6.3/ADR) si se re-edita el TRD; SUGGESTION de redacción ACAH y typo `jti` del qa-report si se re-editan |

## 5. Estado de artefactos y lo que NO se hizo

| Artefacto | Status |
|---|---|
| explore / proposal / spec / design / tasks | done (proposal approved 5/5, 2026-10-20) |
| apply-progress | done + re-apply Q24-fixed |
| qa-plan / qa-log.txt / tests-written / qa-report | done — referencia (22/27, hallazgos cerrados en verify) |
| verify-report | done — **PASS-with-notes** (2026-10-21) |
| archive-report | archived (este fichero) |

NO se hizo: qa-plan previo en C1 (no existió), commits de artefactos `.atl` de C2 (quedan sin versionar, pendientes de decisión del usuario — igual que C1), push, tocar `/docs`, código más allá del fix test-side de LogHygieneTest (verify). Working tree sin ficheros de código pendientes; pila F1 3× healthy; sin secretos versionados (.env fuera de `git ls-files`).

## 6. Decisiones de spec abiertas para cortes futuros

- **S15 rotación de JWT_SECRET**: propiedad garantizada por construcción (clave derivada del env en arranque; Q12c demuestra rechazo con otro secreto) — falta el caso E2.x (emitir → reiniciar con nuevo secreto → 401); candidato a incluirse en el smoke canónico de C9 (decisión no bloqueante).

## Memory pointer

- [SDD C2](/home/acuelop369/Escritorio/Proyectos/Project_Log/.atl/sdd/C2/) — C2 seguridad base + auth (3 cadenas, Argon2id OWASP 2024, JWT HS256 8h, seed idempotente, CORS mínimo) archived PASS-with-notes 22/22, suite 26/26; HEAD 829cc70; registros R-nuevo-3/4 y acciones heredadas C8/C9; artefactos en el project .atl/