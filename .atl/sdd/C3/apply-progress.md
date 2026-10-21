---
change: "C3"
artifact: apply-progress
last_updated: 2026-10-21
status: done
---

# Apply Progress — C3 · Proyectos: CRUD + archivado

Fase sdd-apply. Código real en `backend/src/main/com/projectlog/projects/**`
(8 ficheros), 2 maquetas en `frontend/public/mockups/`,
`scripts/qa_c3_smoke.sh`. 5 commits de fase (#1–#5) + 1 commit de smoke
(+ 2 fixes). Fuentes citadas: `spec.md` (R3.1–R3.20, E3.1–E3.26),
`design.md` (CD-3.1…CD-3.14), `tasks.md` (F0–F6).

## Checks por fase

### F0 — DTOs y requests (tasks 1.1–1.4) ✔
- [x] 1.1 `dto/ProjectDto.java` — record con los 8 componentes snake_case
  literales §3.3, sin `summary`; fechas como String ya formateadas.
- [x] 1.2 `dto/CreateProjectRequest.java` + `dto/UpdateProjectRequest.java`
  — factoría `desdeJson(JsonNode)` con `camposPresentes` vía `nodo.has(CAMPO)`
  independiente del valor; constantes `CAMPO_NAME/DESCRIPTION/STATUS`.
- [x] 1.3 `dto/ProjectDtoSerializationTest.java` (E3.25 [L]) — 2 tests:
  claves exactas sin summary; `description: null` + regex fecha `Z`.
- [x] 1.4 build limpio → **COMMIT #1** (`a97de25`, 3 ficheros exactos).

### F1 — ProjectsRepository (tasks 2.1–2.4) ✔
- [x] 2.1 COLS única fuente + RowMapper con
  `LoginController.EXPIRES_AT_FORMATTER` (C2 por referencia, R3.8) +
  `findAll(String)` / `findById(long)` con `ORDER BY created_at DESC, id DESC`.
- [x] 2.2 `insert` con `INSERT … RETURNING {COLS}` — una sola ida, sin
  check-then-insert ni GeneratedKeyHolder (R-1); el INSERT nunca menciona
  columnas de key ni status.
- [x] 2.3 `conjuntoSet(Set)` función pura package-private, 7 ramas
  literales, TODAS con `updated_at = now()`, ninguna toca columnas de key;
  `update(id, req)` enlaza name/description/status en ese orden + id al
  final; combo vacío → `IllegalArgumentException`.
- [x] 2.4 build limpio → **COMMIT #2** (`f1da852`, 1 fichero).

### F2 — ProjectsController (tasks 3.1–3.5) ✔
- [x] 3.1 Constantes `CODE_*` (L5), helper `error(...)` con
  `objectMapper.valueToTree`; GET lista con fail-fast G3 (enum cerrado
  ANTES de BD; `?status=` vacío también 400) → array plano.
- [x] 3.2 POST con límites literales (name ausente/vacío → `field_required`;
  name >200 / description >2000 sin trimming → `field_too_long`);
  **UN único** `@ExceptionHandler(DuplicateKeyException)` → 409
  `name_duplicate`; `HttpMessageNotReadableException` → 400 `field_required`
  SIN field.
- [x] 3.3 GET /{id} → 404 `not_found` "No existe (quizás fue eliminado)"
  (nunca cuerpo vacío).
- [x] 3.4 PATCH G2 completo: orden nulls → enum (antes de BD, R-2) →
  longitudes; `{}` → 200 SIN llamar a update; 404 antes de UPDATE.
- [x] 3.5 build limpio → **COMMIT #3** (`0867a54`).

### F3 — Tests ligeros [L] (tasks 4.1–4.3) ✔
- [x] 4.1 `ProjectsControllerMvcTest` — 25 tests (mock @MockitoBean repo +
  TokenService, JWT real con `new TokenService(secreto, 8)`), matriz PATCH
  completa, fail-fast con `verifyNoInteractions`, 401 en las 4 rutas, claves
  §3.3 sin summary. Corrección durante apply: `Mockito.anyLong()` para el
  primitivo `long id` (NPE por unboxing).
- [x] 4.2 `ProjectsUpdateSqlTest` — 7 tests: 7 SET literales, `updated_at`
  en todas, sin columnas de key, envoltorio UPDATE…RETURNING, combo vacío.
- [x] 4.3 `mvn test` COMPLETO verde: **60 tests, 0 fallos** (9 suites: 6 de
  C2 + 3 de C3) → **COMMIT #4** (`272b56d`, 2 ficheros de test).

### F4 — Maquetas 04/05 (tasks 5.1–5.3) ✔
- [x] 5.1 `frontend/public/mockups/04-proyectos.html` — copia con estilos
  intactos + login inline a `/api/v1/panel/auth/login` con
  `sessionStorage["mockups_token"]`, helper fetch (~30 líneas) que ante 401
  SOLO mira `response.status` (sin parsear body ISO-8859-1), filtro Todos/
  Activos/Archivados, Archivar/Desarchivar → PATCH `{status}` +
  reload, nota "creación vía API — fuera del puente".
- [x] 5.2 `frontend/public/mockups/05-proyecto-detail.html` — mismo patrón
  duplicado inline; lee `?id=`, GET ficha, PATCH status, 404/409 inline,
  enlace volver a 04.
- [x] 5.3 `npm ci && npm run build` OK (EXIT=0) con `dist/mockups/` con los
  2 HTML → **COMMIT #5** (`723a8c1`, solo los 2 ficheros; `docs/mockups`
  diff-0).

### F5/F6 — Smoke E2E (tasks 6.1–6.3) ✔
- [x] 6.1 `scripts/qa_c3_smoke.sh` (+x, `bash -n` limpio) — helpers PROPIOS
  réplica del esqueleto de C2 (no `source`, NO invoca el script de C2 con
  su Q13 `down -v`); anti-veneno (env del proceso UNSET + `.env` línea a
  línea SIN exportar); higiene S16 (JWT solo en tmpdir 700, nada del token
  a stdout); 401 ISO-8859-1 sin parse estricto (grep del literal +
  decodificación tolerante); `sleep 2` en comparativos de updated_at (E3.15
  igualdad ESTRICTA, resto `<` estricto en epoch); idempotencia
  `smoke-c3-<TS>-*` con checks de membresía; exit 0/1/2.
  → **COMMIT smoke** (`71c3e3b`).
- [x] 6.2 Ejecución completa E3.1–E3.22 — **24/24 PASS en la corrida 3**
  (2 corridas previas con fallos documentados abajo y corregidos con fixes
  `4939d04` y `d9853a0`).
- [x] 6.3 No-regresiones E3.23/E3.24 PASS + `mvn test` completo verde tras
  los smokes + `git status` limpio (solo los 3 docs `.atl` de la fase) +
  pila 3× healthy; `db/migration/` con SOLO `V1__init.sql`.

## Evidencia [S] por escenario (corrida 3, pila con imágenes C3)

Veredictos de `bash scripts/qa_c3_smoke.sh` (exit 0):
```
=== VEREDICTOS QA C3 (por ID del spec) ===
E3.1: PASS   E3.2: PASS   E3.3: PASS   E3.4: PASS   E3.5: PASS   E3.6: PASS
E3.12: PASS  E3.13: PASS  E3.15: PASS  E3.16: PASS  E3.17: PASS  E3.18: PASS
E3.19: PASS  E3.14: PASS  E3.20: PASS  E3.7: PASS   E3.8: PASS   E3.9: PASS
E3.10: PASS  E3.11: PASS  E3.21: PASS  E3.22: PASS  E3.23: PASS  E3.24: PASS
```
Muestras manuales adicionales contra la pila (POST tras la corrida):
- E3.1: POST → `201` con
  `{"id":9,"name":"evidencia-c3-01","description":"muestra","status":"ACTIVE","created_at":"2026-10-21T19:28:55Z","updated_at":"2026-10-21T19:28:55Z","api_key_prefix":null,"has_key":false}`
  (8 claves exactas, sin summary).
- E3.7: GET → `200` con array plano `[{"id":9,…},{"id":8,"name":"smoke-c3-20261021…`
  (sin `{items}`).
- E3.3: POST duplicado → `409`
  `{"code":"name_duplicate","message":"Ya existe un proyecto con ese nombre","field":"name"}`.
- E3.17: PATCH `{"status":"ACTIVO"}` → `400`
  `{"code":"filter_invalid_value","message":"Valor de status no válido; use ACTIVE o ARCHIVED","field":"status"}`
  (nunca 500; enum antes de BD).
- E3.20: dos GET consecutivos de la ficha → mismo `updated_at`
  (`2026-10-21T19:26:45Z` dos veces) — leer nunca modifica.
- E3.24: `psql SELECT version FROM flyway_schema_history` → `1` (solo V1);
  `git diff --exit-code` sobre intocables §0.3 → vacío;
  `db/migration/` = 1 fichero.
- E3.22: `GET :8080/mockups/04-proyectos.html` → `200` con las marcas
  `api/v1/panel/auth/login` y `api/v1/panel/projects` (grep count 4);
  E3.23: login 200 con claves `expires_at,token,username`, 401
  `invalid_credentials` ×3 idéntico, `invalid_token` en /me y en
  GET /panel/projects sin JWT, preflight `20[46]` con ACAO reflejada y
  PATCH en Allow-Methods.

## Fallos y resolución

| Corrida | Fallo | Resolución (commit) |
|---|---|---|
| 1 | E3.1–E3.20 con 401 y E3.22 con fallback SPA: `podman compose up -d --build` reconstruye la imagen pero NO recrea los contenedores (`project_log_backend_1` seguía en `293fe4b5…`, la nueva era `63d62a2e…`) | `fix(smoke)` `4939d04`: `--force-recreate` en la recomposición única (recreate, nunca restart) |
| 2 | `campo_json` imprimía el repr Python (`has_key=False`) en vez del literal JSON `false`; E3.1 solo capturaba el id con e vacío (cascada de BLOQUEADOs); E3.7/E3.8 corrían antes de crear `-c`/renombrar `-b` | `fix(smoke)` `d9853a0`: booleanos como literal JSON, captura de id incondicional, listar al final (orden CD-3.14) |
| 3 | — | 24/24 PASS |

Ningún fallo correspondió a código de producto: los 3 commits de fix
corrigieron el script de smoke. El producto no requirió fix (mvn test y el
smoke sobre la pila C3 salieron verdes al primer contacto directo, corrida
2 para E3.3–E3.6/E3.19–E3.24 y corrida 3 completa).

## Desviaciones

1. Orden de validación del PATCH: `tasks.md` §3.4 listaba longitudes antes
   que enum, pero `design.md` CD-3.6 manda "primero los null, luego enum
   (antes de tocar BD), luego longitudes" — implementado según design
   (nulls → enum → blank/longitudes). Sin cambio funcional observable.
2. Commits: son 6 + 2 fixes en vez de 5 — el 6º es el script de smoke
   (`test(smoke)` `71c3e3b`, exigido por el Done de 6.1 con working tree
   limpio) y los 2 fixes son los de la tabla de arriba. Ninguno toca
   intocables.
3. El smoke verifica el filtro E3.8 por MEMBRESÍA (los `-a`/`-b` del TS
   en su status esperado), no exclusividad de la lista — DELETE es C10 y
   hay filas de corridas previas.

## Commits

| # | Hash | Mensaje |
|---|---|---|
| 1 | `a97de25` | feat(projects): DTOs del panel de proyectos — ProjectDto §3.3 sin summary y requests de POST/PATCH |
| 2 | `f1da852` | feat(projects): ProjectsRepository — SELECT/INSERT/UPDATE sobre projects con RETURNING y updated_at explícito |
| 3 | `0867a54` | feat(projects): CRUD del panel — GET/POST/PATCH con validación manual, 409 por UNIQUE y archivado por status |
| 4 | `272b56d` | test(projects): tests ligeros de C3 — matriz PATCH G2, fail-fast de status, 409 UNIQUE y serialization del DTO |
| 5 | `723a8c1` | feat(frontend): maquetas 04/05 servibles en /mockups con login y fetch a la API real (copia, no originales) |
| 6 | `71c3e3b` | test(smoke): scripts/qa_c3_smoke.sh codificando E3.1–E3.24 [S] |
| fix | `4939d04` | fix(smoke): recomposición única con --force-recreate (E3.1/E3.22 rojos contra imagen vieja) |
| fix | `d9853a0` | fix(smoke): booleanos JSON en campo_json + captura de id incondicional + listar al final (E3.1/E3.7/E3.8) |

Todos con trailer `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
Sin push (según instrucción).

## Intocables (E3.24, diff-0)

SecurityConfig, CorsConfig, RestAuthenticationEntryPoint, JwtAuthFilter,
TokenService, AdminSeeder, StartupSecretsValidator + spring.factories,
application.yml, application-test.yml, V1__init.sql, pom.xml, compose.yaml,
nginx.conf, Dockerfiles, vite.config.ts, docs/mockups/**, qa_c2_smoke.sh,
ws_echo_test.py — todos diff-0 verificados (`git diff --exit-code` vacío).
`package-info.java` de C1 intacto. Cero migraciones nuevas.

## Estado de cierre

- `mvn test` completo: verde (60 tests — ver sección F3; re-corrido tras los
  smokes en 6.3).
- Pila podman: postgres/backend/frontend `Up (healthy)` tras recreate con
  las imágenes C3.
- Working tree: limpio salvo los 3 docs `.atl/sdd/C3/*.md` untracked
  (los commitea el orquestador en archive).
- Higiene S16: ningún secreto ni JWT a stdout ni en commits.