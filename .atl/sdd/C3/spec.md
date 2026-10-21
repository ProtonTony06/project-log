---
change: "C3"
artifact: spec
last_updated: 2026-10-21
status: done
---

# Spec — C3 · Proyectos: CRUD + archivado

> Especificación normativa del corte C3 para la implementación (sdd-design →
> tasks → apply → qa → verify). Fuente: `proposal.md` C3 (status **approved**
> — gate aprobado por el usuario 2026-10-21, 5/5 con las recomendaciones) y
> anclas de `explore.md` C3. Los literales de API/BD/errores se **citan** del
> Backend Schema (`05_BACKEND_SCHEMA.md` valido v1.0), del AppFlow
> (`03_APPFLOW.md` valido v1.0), del TRD (`02_TRD.md` valido v1.1) y del
> `V1__init.sql` aplicado en C1 — nunca se redefinen (regla del 07). Lo
> heredado de C1/C2 es intocable donde se indica.

## 0. Decisiones de gate ya aprobadas (resumen vinculante)

| Gate | Decisión aprobada (2026-10-21, 5/5) |
|---|---|
| G1 Literales del 400 de campos | `field_too_long` (`field: name\|description`, patrón §4.1) + `field_required` (`field: name`) — tal cual |
| G2 PATCH parcial | ausente = sin cambio; `description: null` limpia; `name`/`status` null → 400 `field_required`; `{}` vacío → 200 idempotente — tal cual |
| G3 `?status=` y `status` de PATCH inválidos | 400 fail-fast `filter_invalid_value` (`field: status`), nunca silencio; `status: null` → 400 `field_required` |
| G4 Tests | patrón C2 (unit [L] sin contenedor + smokes podman en pila real), **SIN Testcontainers en C3** (reevalúa C5) |
| G5 Maquetas servibles | copias adaptadas de 04/05 en `frontend/public/mockups/` (login inline + fetch mínimo a la API real), servidas en `:8080/mockups/…`, mismo origen, `/docs/mockups/` intacto; puente desechable hasta C8 |

Y, de la propuesta (no gate, resuelto con recomendación): arquitectura
`ProjectsRepository` (JdbcTemplate) + `ProjectsController` fino **sin capa de
servicio** (P-0), INSERT/UPDATE directo + catch de la UNIQUE → 409 (R-1,
check-then-insert descartado), `updated_at = now()` explícito en el UPDATE,
fechas ISO-8601 UTC con `Z` (formatter de C2), orden de construcción P-6.

## 1. Requisitos normativos (RFC 2119)

En este documento **MUST/DEBE**, **MUST NOT/NO DEBE**, **SHOULD** y **MAY**
se interpretan según RFC 2119. Cada requisito es verificable por al menos un
escenario de la sección 2.

### 1.A Estructura y dependencias

- **R3.1** — El código nuevo del corte DEBE vivir en el paquete
  `com.projectlog.projects` (package-by-feature, TRD §2.1: el paquete ya
  existe con `package-info.java` marcador de C1) con la estructura mínima:
  `repository` (`ProjectsRepository`), `controller` (`ProjectsController`)
  y records de DTO en `dto` (`ProjectDto` §3.3; records de entrada si el
  design los necesita). El `package-info.java` de C1 NO DEBE perder su rol
  marcador. C3 NO DEBE crear dependencias nuevas: **cero cambios en
  `pom.xml`** salvo que design/tasks demuestren necesidad explícita; en
  particular NO se añade `spring-boot-starter-validation` ni ningún starter
  JPA (ver R3.12 y propuesta §5). C3 NO DEBE crear ninguna migración:
  `V1__init.sql` queda intacto (cero migraciones nuevas es el estado
  contractual de C2, S24/E3).

- **R3.2** — NO DEBE existir capa de servicio intermedia: el
  `ProjectsController` orquesta **validación manual → `ProjectsRepository`
  → ProjectDto** (propuesta §2.0). `ProjectsRepository` DEBE centralizar
  las consultas porque es pieza compartida downstream (C4 usa `findById`
  para el modal de regeneración, AppFlow flujo 3 paso 1; C6 mapea el nombre
  desde `GET /projects`). Las 4 operaciones de C3 son mono-sentencia:
  el autocommit de `JdbcTemplate` basta; `@Transactional` en el repo queda
  como documentación/defensa — lo multi-sentencia (batch all-or-nothing,
  CASCADE) llega en C5/C10.

- **R3.3** — La validación de entrada DEBE ser **manual en el controller**,
  con constantes de literal (`CODE_*`) y cuerpos de error via
  `ObjectMapper`, siguiendo el patrón de
  `LoginController.credencialesInvalidas()` de C2. `@Valid`/Bean Validation
  NO DEBE usarse (sin starter-validation no hay hibernate-validator en el
  classpath: `@Valid` sería no-op silencioso — riesgo mayor que el que
  resuelve, propuesta §5).

### 1.B Repositorio — SQL sobre `projects` de V1

- **R3.4** — `ProjectsRepository` (clase, `JdbcTemplate`, SQL explícito
  inline) DEBE exponer `findAll(status?)`, `findById(id)`, `insert(…)` y
  `update(…)`. El SQL opera sobre la tabla `projects` de `V1__init.sql`
  (columnas exactas citadas en §3 de esta spec). `findAll(status?)` DEBE
  aplicar el `WHERE status = …` **solo** cuando `status` viene informado;
  sin filtro = todas las filas. La lista es **array plano** ordenado por el
  criterio que design fije (sin paginación ni keyset: `[ProjectDto]` —
  Schema §3.2; TRD §2.4: "Para la lista de proyectos (pocas filas), offset
  es irrelevante"; el keyset es exclusivo de LOGS, C6).

- **R3.5** — El INSERT DEBE ser **directo, sin SELECT previo
  (check-then-insert descartado, R-1)**: la violación de
  `projects_name_unique` capturada como
  `DuplicateKeyException`/`DataIntegrityViolationException` DEBE traducirse
  a **409 `name_duplicate`** (Schema §4 regla 2: "UNIQUE en BD; violación →
  409 `name_duplicate`"). El `@ExceptionHandler` local del
  `ProjectsController` materializa el 409. La misma técnica DEBE aplicarse
  al PATCH cuando el cambio de `name` choque con la UNIQUE (garante último
  de la carrera: la constraint de la BD). El índice parcial
  `projects_api_key_hash_idx` (UNIQUE WHERE NOT NULL) no interfiere: los
  INSERT de C3 van con `api_key_hash` NULL (múltiples NULLs permitidos en
  índice parcial — explore §1.4).

- **R3.6** — El UPDATE DEBE ser **por columnas acotadas**: SET solo de
  `name`/`description`/`status`, y únicamente los presentes en el PATCH
  (semántica aprobada, R3.15); NUNCA DEBE tocar
  `api_key_hash`/`api_key_prefix`/`key_created_at` (el DTO de entrada del
  Schema §3.3 no los incluye — R-4). El SET DEBE incluir **SIEMPRE
  `updated_at = now()`** en cada UPDATE ejecutado: V1 no tiene trigger y el
  DEFAULT solo aplica al INSERT (explore §1.4); la verificación "Cambios
  guardados" del AppFlow flujo 2 con `updated_at` refrescado depende de
  ello. Un PATCH **sin ningún campo modificable** (`{}`) NO DEBE ejecutar
  UPDATE (idempotencia aprobada en G2: devuelve el DTO actual "sin
  cambios", con su `updated_at` intacto).

- **R3.7** — `has_key` es **derivado, nunca columna** (Schema §2 y §4
  regla 11): DEBE calcularse como `api_key_hash IS NOT NULL` en la consulta
  SQL o en post-mapeo. Todo proyecto recién creado por C3 DEBE salir
  siempre `api_key_prefix: null` y `has_key: false` (la creación nunca
  genera ni toca la key — AppFlow flujo 2 paso 4: "con `api_key_prefix`
  vacío y `has_key: false`"; Schema §6.3 "sin otras semillas").

- **R3.8** — Todas las fechas serializadas (`created_at`, `updated_at`) DEBEN
  salir en **ISO-8601 UTC con sufijo `Z`** (Schema §3.1), reutilizando el
  patrón de formatter compartido de C2 (tipo
  `LoginController.EXPIRES_AT_FORMATTER`, `yyyy-MM-dd'T'HH:mm:ss'Z'` UTC —
  R-6); sin introducir una segunda convención de formateo.

### 1.C Endpoints, rutas y cuerpo del DTO

- **R3.9** — En C3 DEBEN existir EXACTAMENTE estos 4 endpoints, en las
  rutas literales del Schema §3.2, todos **protegidos por JWT Bearer desde
  la cadena 2 de `SecurityConfig`** (los 4 nacen protegidos sin tocar
  ninguna cadena — S19 de C2 ya lo verificó; el proxy nginx de C1 enruta
  `/api/v1/**` completo: cero infra nueva):

  | Método | Ruta | Request → Response |
  |---|---|---|
  | GET | `/api/v1/panel/projects` | query `status?` → **200** `[ProjectDto]` · 400 · 401 |
  | POST | `/api/v1/panel/projects` | `{name, description?}` → **201** `ProjectDto` · 400 · 409 |
  | GET | `/api/v1/panel/projects/{id}` | → **200** `ProjectDto` · 401 · 404 |
  | PATCH | `/api/v1/panel/projects/{id}` | `{name?, description?, status?}` → **200** `ProjectDto` · 400 · 401 · 404 · 409 |

  `DELETE /projects/{id}`, `POST …/api-key/regenerate`,
  `DELETE …/api-key`, `GET …/summary` y el parámetro `include=summary` NO
  DEBEN existir en C3 (§4, fuera de alcance). `ProjectDto` DEBE tener
  EXACTAMENTE los campos literales del Schema §3.3 — **`summary` AUSENTE**
  (es C10): `id, name, description, status, created_at, updated_at,
  api_key_prefix, has_key` (JSON de ejemplo citado en §3.2 de esta spec).

- **R3.10** — `GET /api/v1/panel/projects` con query `status=` DEBE filtrar
  por el literal del enum; `status` distingue **MAYÚSCULAS** (literal del
  CHECK de V1 y del DTO). Sin `status` = todos (filtro opcional). Un valor
  fuera de `"ACTIVE"|"ARCHIVED"` (mal escrito, minúsculas) DEBE producir
  **400 `{"code": "filter_invalid_value", "field": "status", …}**,
  **fail-fast antes de tocar la BBDD** (G3); ignorar el filtro en silencio
  NO DEBE suceder (anti-patrón "error silencioso", §3.1: "errores siempre
  explícitos, nunca silenciosos", métrica 2 del PRD).

- **R3.11** — `GET/PATCH /api/v1/panel/projects/{id}` con id inexistente
  DEBEN responder **404** con el cuerpo estándar y el literal
  **`not_found`** (literal anclado en §3.3 para `GET /logs/{id}` —
  `404 {"code": "not_found"}` — y convención panel-wide de §3.1;
  AppFlow §5 fila 404: "No existe (quizás fue eliminado)"). Un 404 NUNCA
  DEBE traducirse a respuesta vacía o a lista vacía (convención AppFlow §5).

- **R3.12** — Todo error 4xx/409 de controller DEBE salir con el cuerpo
  estándar del Schema §3.1 **`{"code", "message", "field"?}`** (`message`
  descriptivo en español) y con los literales APROBADOS en el gate:
  - **`field_required`** — campo requerido ausente/vacío (`field: name`;
    también `field: status` cuando `status: null` explícito en PATCH, G3).
  - **`field_too_long`** — campo demasiado largo (`field: name` > 200;
    `field: description` > 2000 — patrón §4.1 de la ingesta: "400
    `field_too_long`, `field`").
  - **`filter_invalid_value`** — valor fuera de enum en filtro o campo
    (`field: status` — patrón §3.3 de logs: "`environment`/`level` fuera de
    enum (`filter_invalid_value`)" — y G3 para ambos, query y PATCH).
  - **`name_duplicate`** — 409 por UNIQUE (§3.1 códigos; §4 regla 2).
  - **`not_found`** — 404 (R3.11).
  Ningún literal NUEVO sin ancla DEBE inventarse. El 401 sigue siendo
  exclusivo de la cadena 2 (`invalid_token`, entry point de C2 intocable).

- **R3.13** — Los rangos de validación de borde DEBEN ser los del Schema
  §3.3: `name` string **1..200**; `description` string opcional
  **≤2000**; `status` con valor en el enum cerrado **`"ACTIVE"|"ARCHIVED"`**
  (lo fuerza el CHECK de V1: `CONSTRAINT projects_status_check CHECK
  (status IN ('ACTIVE', 'ARCHIVED'))` — regla 10 del Schema: enums cerrados
  forzados por CHECK **y** validación en el borde, **400 antes de
  insertar**). La validación de `status` en PATCH DEBE ocurrir ANTES de
  cualquier UPDATE: si escapara a la BD, `projects_status_check`
  convertiría la petición en 500 (R-2), lo cual está prohibido.

- **R3.14** — `POST /api/v1/panel/projects` con cuerpo válido DEBE crear la
  fila (INSERT directo) y responder **201** con el `ProjectDto` completo de
  la fila recién creada, incluyendo `status: "ACTIVE"` (DEFAULT de V1),
  `api_key_prefix: null`, `has_key: false`, y `created_at`/`updated_at`
  ISO-8601 `Z`.

### 1.D PATCH parcial — semántica aprobada (G2, tabla de casos)

- **R3.15** — `PATCH /api/v1/panel/projects/{id}` DEBE aplicar EXACTAMENTE
  esta semántica sobre la entrada `{name?, description?, status?}` (todos
  opcionales, Schema §3.3):

  | Cuerpo PATCH | Comportamiento | Respuesta |
  |---|---|---|
  | `{}` (nada presente) | idempotente: sin UPDATE; devuelve el DTO actual, `updated_at` intacto (G2) | **200** |
  | `{"name": "<nuevo>"}` | cambia solo `name` (+`updated_at = now()`); si choca con la UNIQUE → 409 | **200** · **409** |
  | `{"description": "<texto>"}` | fija la descripción | **200** |
  | `{"description": null}` | **LIMPIA** la descripción (columna `NULL` en BD) — único campo nullable del DTO §3.3 | **200** |
  | `{"status": "ARCHIVED"}` | **archiva** (solo cambia `status`) | **200** |
  | `{"status": "ACTIVE"}` | **desarchiva** (reversible; archivar nunca borra nada — §4 regla 3) | **200** |
  | `{"name": null}` / `{"status": null}` | no existe valor legal nulo para esos dos (NOT NULL + UNIQUE / NOT NULL + CHECK en V1) | **400** `field_required, field: name\|status` |
  | `{"status": "<fuera de enum>"}` | borde ANTES de BD (regla 10; si escapara → 500 prohibido, R-2) | **400** `filter_invalid_value, field: status` |
  | combinados (p. ej. `{"name": …, "status": …}`) | coexistencia permitida por el Schema (los 3 campos en la misma entrada §3.3); el 409 combinado está contemplado (AppFlow flujo 7 paso 6) | **200** · **400** · **404** · **409** |

  La coherencia del vocabulario es vinculante: el `status` de la query y el
  del PATCH comparten un solo tratamiento de borde (G3) — fuera de enum →
  `filter_invalid_value`; `null` explícito → `field_required`.
  **Nota para design** (fuera del gate, sin alterar lo aprobado): el
  comportamiento ante cuerpos no-JSON o campos desconocidos no quedó
  regulado por el gate — design lo decidirá como decisión menor, sin
  introducir literales nuevos y respetando el cuerpo estándar §3.1.

### 1.E Archivado / desarchivado

- **R3.16** — El archivado y el desarchivado DEBEN ser **exclusivamente** un
  PATCH con `{"status": "ARCHIVED"|"ACTIVE"}` — nunca por DELETE (AppFlow
  flujo 7; Schema §3.3: "archivar/desarchivar va vía PATCH con `status`,
  nunca por DELETE"). "Archivar **nunca** borra nada: solo cambia `status`"
  (Schema §4 regla 3; §4.2: "archivar es un cambio de status del proyecto,
  reversible, nunca destruye datos"). El UPDATE NUNCA DEBE tocar la key ni
  los logs (§4 regla 11: "Archivar **no** toca la key ni los logs; al
  desarchivar, la misma key vuelve a aceptarse"; §4 regla 12: "el archivado
  afecta al flujo, no a la consulta"). En C3 SOLO DEBE existir el DATO
  `status`: la regla de rechazo 403 `project_archived` de la ingesta es C5
  (nota de cruce del 07) y NO DEBE implementarse aquí.

### 1.F Maquetas 04/05 servibles (G5)

- **R3.17** — DEBEN crearse **copias adaptadas** de
  `docs/mockups/04-proyectos.html` y `docs/mockups/05-proyecto-detail.html`
  en `frontend/public/mockups/` (el pipeline Vite de C1 copia `public/` a
  `dist/` en el build — explore), servidas por el nginx de C1 en
  `:8080/mockups/04-proyectos.html` y `:8080/mockups/05-proyecto-detail.html`
  — **mismo origen** (el proxy ya enruta `/api/v1/**`: cero CORS, cero
  infra nueva). `/docs/mockups/**` DEBE quedar **congelado e intacto**
  (paquete de diseño aprobado por gates el 2026-10-19; las copias son el
  puente desechable hasta C8, que lo retira). La capa mínima de wiring:
  - **Login inline**: un form que llama `POST /api/v1/panel/auth/login` y
    guarda el token en memoria/`sessionStorage` local de la copia;
  - **04-proyectos.html**: pinta la lista desde
    `GET /api/v1/panel/projects` (+ toggle del filtro `?status=`) y acción
    Archivar/Desarchivar con PATCH `{status}` por fila;
  - **05-proyecto-detail.html**: pinta la ficha desde
    `GET /api/v1/panel/projects/{id}` y botón Archivar/Desarchivar → PATCH.
  Sin React, sin rutas SPA, sin estados del AppFlow §4 más allá del mínimo
  para operar (los formularios `/proyectos/nuevo` y `/proyectos/:id/editar`
  van SIN maqueta, decisión de coherencia ya registrada: el POST/PATCH se
  prueba con la copia de 04 o con curl del smoke). La reconstrucción de la
  imagen frontend (`up -d --build`) DEBE seguir el anti-veneno de env
  (R3.19). Alternativas descartadas por el gate: `file://` (rompe por CORS,
  otro origen), servidor de mockups aparte (viola el puerto único, TRD
  §6.1/§9), inyectar script en los originales (equivalente a tocarlos).

### 1.G Tests y smokes (G4)

- **R3.18** — La estrategia DEBE seguir el patrón C2: tests unitarios
  **[L]** sin contenedor ni BBDD (JUnit + `spring-boot-starter-test`,
  `@WebMvcTest`/slices sobre el controller con el repo mockeado:
  validación de borde, literales, formas del DTO y mapeo de errores;
  respetando el `application-test.yml` dummy del validador de C1,
  **INTOCABLE**) + **smokes [S]** en la pila real podman-compose contra
  Postgres 17 real (curl por `:8080` con JWT real de `POST /auth/login`),
  ejerciendo TODO el CRUD + archivado (criterio del Done: "smoke de
  estados"). **SIN Testcontainers ni H2 en C3** (G4; se reevalúa en C5
  para el batch all-or-nothing). El SQL del repo se verifica en smoke
  (la pila) y en review — el unit no lo ejercita.

- **R3.19** — Los smokes DEBEN heredar la disciplina del verify C2:
  - **Anti-veneno de env (R-nuevo-3)**: si toca recomponer la pila, env del
    proceso UNSET + `.env` cargado de forma robusta y `recreate` (NUNCA
    `restart`);
  - **Idempotencia**: nombres de proyecto con sufijo por ejecución
    (`smoke-c3-<timestamp>`), para repetir el smoke sin limpiar volumen y
    sin depender de borrado (DELETE es C10) — el detalle se fija en tasks;
  - **401 sin parse JSON estricto (R-8, WARNING 1 de C2)**: el 401 del
    entry point sale ISO-8859-1; los scripts de smoke NO DEBEN depender del
    parse estricto del body de los 401 (los códigos/status sí se verifican).

### 1.H No-regresiones C2 — bloqueos heredados

- **R3.20** — C3 DEBE limitarse a **añadir** clases en `com.projectlog.projects`
  y las copias en `frontend/public/mockups/`; NADA más. `git diff` sobre
  `SecurityConfig` (3 cadenas), `CorsConfig`, `RestAuthenticationEntryPoint`,
  `TokenService`/`JwtAuthFilter`/`AdminSeeder`, validador de C1
  (`StartupSecretsValidator`), `application-test.yml`, `V1__init.sql`,
  `compose.yaml` y `/docs/**` DEBE ser de **0 líneas**. Las siguientes
  cadenas de C2 DEBEN seguir funcionando tras C3: login/`invalid_credentials`
  en las 3 ramas (Q8), `invalid_token` en `/me` y ahora en
  `/api/v1/panel/projects` **con** controller y sin JWT — sigue 401 (Q9,
  S19), seed idempotente (Q10/S21–S24), preflight CORS 200/204 (Q24 —
  divergencia ya registrada) y Echo WS `/ws/logs` (C7 lo cambia).

## 2. Escenarios de aceptación

Numeración E3.x (corte C3). Cada uno indica su verificación: **[L]** = test
ligero sin contenedor (JUnit + spring-boot-starter-test, sin BBDD,
segundos) · **[S]** = smoke curl contra la pila podman completa (patrón C2:
`:8080` por el proxy, JWT real de login, disciplina R3.19).

### Creación (POST)

- **E3.1 — POST 201 con name + description [S]** — Given: pila levantada,
  admin sembrado (C2), JWT válido. When: `POST /api/v1/panel/projects` con
  `{name: "smoke-c3-<ts>-a", description: "…<≤2000>"}`. Then: **201** con el
  `ProjectDto` completo; claves EXACTAS `id, name, description, status,
  created_at, updated_at, api_key_prefix, has_key` — **sin `summary`**;
  `status: "ACTIVE"`; `api_key_prefix: null`; `has_key: false`;
  `created_at`/`updated_at` en ISO-8601 UTC con sufijo `Z`.

- **E3.2 — POST 201 sin description [S]** — When: POST solo con `name`.
  Then: **201**; `description: null` en el DTO; resto idéntico a E3.1
  (`has_key: false`, `api_key_prefix: null`).

- **E3.3 — POST 409 name_duplicate [S]** — Given: el proyecto de E3.1
  existe. When: POST inmediato con el MISMO `name` (ida y vuelta
  inmediata). Then: **409** `{"code": "name_duplicate", "message": …}` con
  cuerpo estándar §3.1. Nota R-1: la carrera concurrente real NO es
  exigible en smoke — el 409 secuencial inmediato + la técnica
  INSERT directo / catch de la UNIQUE (R3.5) es lo que cubre R-1 (el
  garante último es `projects_name_unique`); el mapeo de la excepción se
  verifica en [L] (E3.24).

- **E3.4 — POST 400 name ausente o vacío [S]** — When: POST con `{}` y con
  `{"name": ""}`. Then: **400** `{"code": "field_required", "field":
  "name", …}` en ambos casos.

- **E3.5 — POST 400 name >200 [S]** — When: POST con `name` de 201
  caracteres. Then: **400** `{"code": "field_too_long", "field": "name", …}`.

- **E3.6 — POST 400 description >2000 [S]** — When: POST con `description`
  de 2001 caracteres. Then: **400** `{"code": "field_too_long", "field":
  "description", …}` (con `name` válido y dentro de rango).

### Listado y ficha (GET)

- **E3.7 — GET lista 200 [S]** — Given: N proyectos creados por el smoke
  (`smoke-c3-<ts>-*`). When: `GET /api/v1/panel/projects`. Then: **200**
  con un **array plano** de `ProjectDto` (sin `{items, next_cursor}` —
  lista simple, sin paginación), conteniendo los proyectos del smoke.

- **E3.8 — GET lista con filtro `?status=` [S]** — When:
  `GET /api/v1/panel/projects?status=ACTIVE` y `…?status=ARCHIVED`. Then:
  **200** en ambos; el primero contiene los proyectos ACTIVE y NO los
  ARCHIVED, y viceversa tras archivar (E3.14); con estado vacío de un
  filtro → **200 []** (vacío legítimo, AppFlow §4). Sin query → todos.

- **E3.9 — GET lista `?status=` inválido → 400 [S]** — When:
  `GET /api/v1/panel/projects?status=active` (minúsculas) y
  `?status=ARCHIVE`. Then: **400** `{"code": "filter_invalid_value",
  "field": "status", …}` — fail-fast, nunca filtro silenciado (G3).

- **E3.10 — GET ficha existente 200 [S]** — When: `GET
  /api/v1/panel/projects/{id}` de un proyecto del smoke. Then: **200** con
  el `ProjectDto` completo, idénticas claves que E3.1.

- **E3.11 — GET ficha inexistente 404 [S]** — When: `GET
  /api/v1/panel/projects/999999`. Then: **404** con
  `{"code": "not_found", …}` (cuerpo JSON estándar; nunca vacío — AppFlow
  §5).

### Edición y archivado (PATCH)

- **E3.12 — PATCH solo name [S]** — When: `PATCH …/{id}` con
  `{"name": "<nuevo>"}`. Then: **200** con el DTO: `name` cambia,
  `description` y `status` intactos (ausente = sin cambio),
  **`updated_at` refrescado** y `created_at` SIN cambiar.

- **E3.13 — PATCH description: texto y `null` limpia [S]** — When:
  `PATCH` con `{"description": "texto nuevo"}` → **200** con la
  descripción cambiada; luego `PATCH` con `{"description": null}` → **200**
  con `description: null`, `name`/`status` intactos y `updated_at`
  refrescado (comparar antes/después; también verificable en BD: columna
  NULL — explore §1.4).

- **E3.14 — PATCH status ARCHIVED → ARCHIVED; desarchivar → ACTIVE [S]** —
  When: `PATCH …/{id}` con `{"status": "ARCHIVED"}`. Then: **200** con
  `status: "ARCHIVED"`; re-GET (lista con `?status=ARCHIVED` y ficha) lo
  muestra con el dato persistido e **intacto** (mismo `name`/
  `description`; nada borrado: §4 regla 3). When: `PATCH` con
  `{"status": "ACTIVE"}`. Then: **200** con `status: "ACTIVE"` again
  (reversible, §4.2). Ni la key (inexistente en C3) ni los logs (ninguno)
  se tocan — R3.16.

- **E3.15 — PATCH {} vacío → 200 idempotente [S]** — When: `PATCH …/{id}`
  con cuerpo `{}`. Then: **200** con el DTO actual SIN cambios y
  `updated_at` INTACTO (igual al valor previo): idempotente, sin UPDATE
  (G2/R3.6).

- **E3.16 — PATCH name/status null → 400 [S]** — When: `PATCH` con
  `{"name": null}` y con `{"status": null}`. Then: **400**
  `{"code": "field_required", "field": "name"}` y **400**
  `{"code": "field_required", "field": "status"}` respectivamente (G2/G3).

- **E3.17 — PATCH status inválido → 400 antes de BD [S]** — When: `PATCH`
  con `{"status": "ACTIVO"}`, `{"status": "active"}`, `{"status":
  "ARCHIVE"}`. Then: **400** `{"code": "filter_invalid_value", "field":
  "status", …}` en todos; NO DEBE producir 500 (R-2: si escapara, el CHECK
  lo convertiría en 500) ni tocar la fila.

- **E3.18 — PATCH con choque de name → 409 [S]** — Given: proyectos A y B
  del smoke. When: `PATCH …/{idA}` con `{"name": "<name de B>"}`. Then:
  **409** `{"code": "name_duplicate", …}`; B intacto.

- **E3.19 — PATCH id inexistente → 404 [S]** — When: `PATCH …/999999` con
  cualquier campo válido. Then: **404** `{"code": "not_found", …}`.

- **E3.20 — updated_at cambia en PATCH con campos y NO en GET [S]** —
  When: crear un proyecto, capturar `updated_at`; repetir GET de ficha;
  PATCH con un campo; repetir GET. Then: los GET SIN PATCH
  previo devuelven el mismo `updated_at` (leer NUNCA modifica);
  tras un PATCH con ≥1 campo presente, el `updated_at` es estrictamente
  posterior (R3.6).

### Autenticación (cadena 2, heredada)

- **E3.21 — 401 sin JWT en las 4 rutas [S]** — Given: pila levantada.
  When: `GET /api/v1/panel/projects`, `POST /api/v1/panel/projects`,
  `GET /api/v1/panel/projects/{id}` y `PATCH /api/v1/panel/projects/{id}`
  SIN header `Authorization`. Then: **401** con el cuerpo del entry point
  de la cadena 2 (`invalid_token` — se verifica código/status, sin parse
  estricto del body, R3.19). No hay cambio de seguridad que verificar más
  allá de esto + el diff 0 (R3.20).

### Maquetas 04/05 servibles (G5)

- **E3.22 — Maquetas servibles con fetch real [S]** — Given: la imagen
  frontend reconstruida (anti-veneno, R3.19). When: abrir
  `http://<host>:8080/mockups/04-proyectos.html`, loguear con el login
  inline (credenciales dev, `POST /api/v1/panel/auth/login`),
  observar la lista y operar. Then: la copia se sirve por el nginx (HTTP
  200, HTML); la lista pinta los proyectos REALES del smoke (fetch a
  `GET /api/v1/panel/projects`, mismo origen); el toggle `?status=`
  filtra; Archivar/Desarchivar desde la copia de 04 (o desde 05 con su
  ficha `GET …/{id}`) ejecuta PATCH `{status}` y la lista refleja la
  etiqueta "Archivado" y el filtro ARCHIVED; `/docs/mockups/` queda
  intacto (no se sirvió ni modificó ningún original — diff 0, R3.20).

### No-regresiones C2 (re-ejecución de smokes)

- **E3.23 — Cadenas auth de C2 intactas [S]** — Given: la misma pila tras
  C3. When: re-ejecutar los smokes Q8–Q10 y Q24 de C2: login OK (200
  `{token, username, expires_at}`); 401 `invalid_credentials` en las 3
  ramas; 401 `invalid_token` en `/me`; y en `GET /api/v1/panel/projects`
  (ahora **con** controller y sigue 401 sin JWT — S19); preflight CORS
  `OPTIONS` 200/204 sin tocar `CorsConfig`. Then: todos verdes, mismos
  estados y literales que en C2 (heredados: anti-veneno, 401
  ISO-8859-1 sin parse estricto).
- **E3.24 — Diff 0 en intocables [S]** — When: `git diff` sobre
  `SecurityConfig`, `CorsConfig`, `RestAuthenticationEntryPoint`,
  validador de C1, `application-test.yml`, `V1__init.sql`, `compose.yaml`
  y `/docs/**`, y sobre Echo WS (`scripts/ws_echo_test.py` si se desea).
  Then: **6 archivos → 0 líneas** de diff (R3.20); cero migraciones nuevas
  en `db/migration/`; Flyway sigue "Schema up to date" (E3 de C1).

### Verificación ligera [L] (sin contenedor)

- **E3.25 — ProjectDto serialization [L]** — When: serializar un `ProjectDto`
  de prueba (unit, con el mapeador/formatter del design). Then: el JSON
  tiene EXACTAMENTE las claves §3.3 sin `summary`; `description: null`
  admitido; `status()` = literal `"ACTIVE"`; fechas con sufijo `Z`
  (formatter patrón C2).

- **E3.26 — Validación de borde y mapeo de errores [L]** — Given: MockMvc
  (`@WebMvcTest`) sobre `ProjectsController` con `ProjectsRepository`
  mockeado, respetando el `application-test.yml` del validador (INTOCABLE).
  When: ejercitar cada caso de la tabla R3.15 y las rutas: `name` vacío →
  400 `field_required, field: name`; `name` ≤boundary 200 aceptado y 201
  rechazado → 400 `field_too_long, field: name`; `description` 2000/2001
  ídem; `?status=` y `status` fuera de enum → 400 `filter_invalid_value,
  field: status` (y verificado ANTES de cualquier llamada al repo moqueado:
  fail-fast); `name`/`status` null → 400 `field_required`; `{}` → 200 sin
  invocar `update`; choque de UNIQUE (`DuplicateKeyException` lanzada por
  el mock) → 409 `name_duplicate`; id inexistente → 404 `not_found`; DTO
  de salida con EXACTAMENTE las claves §3.3 sin `summary`. Then: cada
  status + literal verificado en unit.

## 3. Contratos citados (fuente única de verdad)

| Contrato | Contenido citado | Fuente |
|---|---|---|
| Rutas de proyectos | `GET /api/v1/panel/projects` (query `status?`, `include=summary?` → 200 `[ProjectDto]` · 401) · `POST /api/v1/panel/projects` (`{name, description?}` → 201 `ProjectDto` · 400 · 409) · `GET /api/v1/panel/projects/{id}` (→ 200 `ProjectDto` · 401 · 404) · `PATCH /api/v1/panel/projects/{id}` (`{name?, description?, status?}` → 200 `ProjectDto` · 400 · 401 · 404 · 409) | Schema §3.2 (fila 3–6); TRD §2.5/§9 |
| ProjectDto | claves exactas `id, name, description, status, created_at, updated_at, api_key_prefix, has_key` + `summary` ausente salvo `include=summary` (C10); ejemplo literal §3.3; `description` puede ser `null`; `api_key_prefix: null` y `has_key: false` si la key está revocada o aún no generada; `has_key` derivado de `api_key_hash` | Schema §3.3; §2 invariantes |
| Entradas y límites | creación `{name: string (1..200), description?: string (≤2000)}`; edición PATCH todos opcionales `{name?, description?, status? ("ACTIVE"\|"ARCHIVED")}` — "archivar/desarchivar va vía PATCH con `status`, nunca por DELETE" | Schema §3.3 |
| Errores | cuerpo estándar `{"code", "message", "field"?}`; "errores siempre explícitos, nunca silenciosos" (métrica 2); fechas ISO-8601 UTC con `Z`; 409 `name_duplicate`; 404 `{"code": "not_found"}`; 425/429 no existen en v1 | Schema §3.1, §3.3, §4 regla 2 |
| Literales 400 aprobados (gate 2026-10-21) | `field_too_long` (`field`) — patrón §4.1 ("400 `field_too_long`, `field`"); `field_required`; `filter_invalid_value` — patrón §3.3 logs ("`environment`/`level` fuera de enum (`filter_invalid_value`)") | Gate 1/3; Schema §4.1, §3.3 |
| PATCH semántica | ausente = sin cambio; `description: null` limpia; `name`/`status` null → 400 `field_required`; `{}` → 200 idempotente; coexistencia de los 3 campos permitida (409 combinado contemplado) | Gate 2 (2026-10-21); AppFlow flujo 2 "solo los campos modificados"; AppFlow flujo 7 paso 6 |
| Reglas de integridad | regla 2 (UNIQUE name → 409 en línea, datos conservados); regla 3 ("Archivar **nunca** borra nada: solo cambia `status`"); regla 10 (estatus `ACTIVE, ARCHIVED` — CHECK **y** borde, 400 antes de insertar); regla 11 ("Archivar **no** toca la key ni los logs; al desarchivar, la misma key vuelve a aceptarse"); regla 12 ("el archivado afecta al flujo, no a la consulta"); §4.2 (sin soft-delete; archivar reversible, nunca destruye datos) | Schema §4, §4.2 |
| Tabla `projects` | `id BIGINT IDENTITY PK`; `name TEXT NOT NULL`; `description TEXT` (opcional); `status TEXT NOT NULL DEFAULT 'ACTIVE'`; `api_key_hash BYTEA` (NULL = revocada/sin key); `api_key_prefix TEXT`; `key_created_at TIMESTAMPTZ`; `created_at`/`updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`; `CONSTRAINT projects_name_unique UNIQUE (name)`; `CONSTRAINT projects_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))`; índice parcial `projects_api_key_hash_idx`; **sin trigger de `updated_at`** | `V1__init.sql` aplicado en C1 (límites idénticos §2/§6.1 del Schema) |
| AppFlow flujo 2 | crear: 201 → ficha con aviso de generar key después; 409 in-line "Ya existe un proyecto con ese nombre", datos conservados; 400 errores por campo; editar: PATCH con solo los modificados → "Cambios guardados" + `updated_at` refrescado; la key nunca en la creación | `03_APPFLOW.md` flujo 2 |
| AppFlow flujo 7 | archivar desde ficha o lista con confirmación; `PATCH /projects/{id}` con `{status: "ARCHIVED"}` (nunca DELETE); etiqueta "Archivado" y botón Desarchivar; logs históricos siguen consultables; desarchivar → `{status: "ACTIVE"}` | `03_APPFLOW.md` flujo 7 |
| AppFlow §4/§5 | estados de `/proyectos` y ficha (vacío legítimo "No hay proyectos todavía", esqueleto, banner+Reintentar); manejo global 400/404/409; nunca 404→vacío | `03_APPFLOW.md` §4/§5 |
| Lista sin paginación | `[ProjectDto]` array plano; "Para la lista de proyectos (pocas filas), offset es irrelevante"; keyset exclusivo de logs | Schema §3.2/§3.3; TRD §2.4 |
| Archivo `V1` sin migración | cero migraciones nuevas; esquema ya aplicado; sin otras semillas (§6.3) | explore §1.4; Schema §6.1/§6.3; estado C2 |
| Maquetas 04/05 | copias adaptadas en `frontend/public/mockups/`; nginx `:8080/mockups/…` mismo origen; `/docs/mockups` congelado (aprobadas 2026-10-19); pipeline Vite `public/`→`dist/` | Gate 5 (2026-10-21); explore §1.7, P-4; C1 archive-report |
| Heredados C2 | 3 cadenas/CORS/entry point intocables; panel nace protegido (S19); anti-veneno de env (R-nuevo-3: UNSET + `.env` + `recreate`, nunca `restart`); 401 del entry point ISO-8859-1 (R-nuevo-4, fix C8/C9 — solo no parsear el body en smokes); Q8–Q10/Q24 re-ejecutables; `application-test.yml` dummy intocable; validador C1 intocable | `.atl/sdd/C2/verify-report.md` (PASS-with-notes), archive-report, spec C2 S29/S31/S19 |
| Paquetes y stack | package-by-feature `com.projectlog.projects`; `spring-boot-starter-jdbc` presente, sin starter JPA/validación; `JdbcTemplate` SQL inline patrón C2 | TRD §2.1; explore P-0, P-1 |

## 4. Fuera de alcance (explícito)

- **`DELETE /projects/{id}` + `X-Confirm-Name`** — borrado con CASCADE:
  C10 (Schema §3.2 marca P1; 07 lo lista en C10). Las cabeceras
  `X-Confirm-*` ya están configurables en CORS (C2) pero NO se usan aquí.
- **API key** — `POST …/{id}/api-key/regenerate` (201 una única vez) y
  `DELETE …/{id}/api-key` (204): C4. C3 nunca genera, muestra ni revoca
  keys; `api_key_hash`/`api_key_prefix`/`key_created_at` quedan intactos
  por C3.
- **Regla 403 `project_archived`** — rechazo de ingesta sobre archivado:
  C5 (nota de cruce: en C3 existe el DATO `status`, en C5 la regla).
  C3 NO toca la ingesta, ni `X-API-Key`, ni la cadena 1.
- **`summary` / `include=summary` / `GET …/{id}/summary`** — SummaryDto:
  C10 (§ R3.9: el DTO va sin `summary`).
- **Keyset pagination** — `{items, next_cursor}` es exclusivo de LOGS
  (C6); proyectos = lista simple sin paginación.
- **Frontend real del panel** — React `/proyectos` con estados del AppFlow
  §4: C8. Las copias de C3 son puente desechable; los formularios
  `/proyectos/nuevo` y `/proyectos/:id/editar` van sin maqueta (decisión
  de coherencia registrada).
- **Rate limiting / 429** — no existen en v1 (Schema §3.1). **TLS/dominio**
  — HTTP plano en LAN (ADR-23).
- **Migraciones** — cero: `V1__init.sql` y el esquema quedan intactos; sin
  trigger de `updated_at` (el SET explícito del repo lo cubre).
- **Dependencias** — sin starter-validation ni JPA; sin cambios de pom
  salvo demostración en design/tasks (R3.1).
- **Testcontainers/H2** — diferidos (reevaluación en C5, G4).

## 5. Trazabilidad

| Fuente | Qué cubre en este spec |
|---|---|
| **07 C3** (alcance literal) | GET (query `status?`) + POST (201·400·409 `name_duplicate`) + GET/PATCH `/projects/{id}` (archivar/desarchivar vía `status`); archivado conserva logs consultables; 409/400 con los literales del Schema → R3.9–R3.16 |
| **07 C3** (Done cuando: "CRUD+archivado con smoke de estados (409 y 400 incluidos); maqueta 04/05 servibles contra esta API") | smokes de E3.1–E3.21 (409/400 incluidos) y E3.22 (maquetas con fetch real) |
| **07 C3** (nota de cruce) | "aquí existe el dato (status), en C5 la regla": 403 `project_archived` excluido → §4, R3.16 |
| **Gate 2026-10-21 (5/5)** | G1 literales 400 → R3.12/R3.13, E3.4–E3.6 · G2 PATCH parcial → R3.15, E3.12–E3.16 · G3 fail-fast status query+PATCH → R3.10/R3.13/R3.15, E3.9/E3.17 · G4 tests patrón C2 sin Testcontainers → R3.18, §6 · G5 maquetas → R3.17, E3.22 |
| **Propuesta APROBADA (decisiones no gate)** | P-0 repo JdbcTemplate + controller fino sin servicio → R3.1/R3.2 · R-1 INSERT directo + catch UNIQUE → R3.5, E3.3/E3.18 · `updated_at = now()` explícito → R3.6, E3.20 · formatter fechas C2 → R3.8, E3.25 · sin starter-validation → R3.3 · @Transactional como defensa → R3.2 |
| **Insumos del 07** | Schema §3.2/§3.3 → R3.9–R3.12; AppFlow flujos 2 y 7 → R3.14–R3.16, E3.12–E3.14; TRD §2.3 (entidades, "Archivar nunca borra nada") → R3.16 |
| **Heredados C2 (verify PASS-with-notes)** | anti-veneno de env → R3.19/E3.22 · 401 ISO-8859-1 (no parse estricto en smokes) → R3.19/E3.21 · preflight 200 (Q24, divergencia registrada) → R3.20/E3.23 · S19 panel nace protegido → R3.14/E3.21 · `application-test.yml` y validador C1 intocables (S29) → R3.18/R3.20/E3.24 · seed idempotente (Q10) y Echo WS intocables → R3.20/E3.23/E3.24 |
| **PRD §3** (stories 2, 13, 14) | crear/editar proyectos (story 2) → R3.10–R3.15 · archivar/desarchivar (stories 13/14) → R3.16 · archivados conservan logs consultables → R3.16 (solo el dato) |
| **06_IMPLEMENTATION_PLAN** (Fase 1) | orden de construcción stories 2 → 13 → 14; P-6 del proposal (repo → POST → GET → PATCH → maquetas → smoke) → guía para tasks |

## 6. Mapa de verificación

| Escenario | Verificación | Tipo |
|---|---|---|
| E3.1 | Smoke curl: POST 201 con name+description → DTO §3.3 sin summary, `has_key: false`, `api_key_prefix: null`, fechas `Z` | [S] podman |
| E3.2 | Smoke: POST 201 sin description → `description: null` | [S] podman |
| E3.3 | Smoke: POST duplicado inmediato → 409 `name_duplicate` | [S] podman |
| E3.4 | Smoke: POST `{}` y `{"name": ""}` → 400 `field_required, field: name` | [S] podman |
| E3.5 | Smoke: POST `name` 201 chars → 400 `field_too_long, field: name` | [S] podman |
| E3.6 | Smoke: POST `description` 2001 chars → 400 `field_too_long, field: description` | [S] podman |
| E3.7 | Smoke: GET lista → 200 array plano `[ProjectDto]` | [S] podman |
| E3.8 | Smoke: `?status=ACTIVE` / `?status=ARCHIVED` filtra; vacío → 200 [] | [S] podman |
| E3.9 | Smoke: `?status=active` / `?status=ARCHIVE` → 400 `filter_invalid_value, field: status` | [S] podman |
| E3.10 | Smoke: GET ficha → 200 DTO completo | [S] podman |
| E3.11 | Smoke: GET ficha inexistente → 404 `not_found` | [S] podman |
| E3.12 | Smoke: PATCH name → 200, resto intacto, `updated_at` refrescado | [S] podman |
| E3.13 | Smoke: PATCH description texto y `{"description": null}` → limpia, `updated_at` refrescado | [S] podman |
| E3.14 | Smoke: PATCH `{status: "ARCHIVED"}` → 200 ARCHIVED; dato intacto; desarchivar → ACTIVE | [S] podman |
| E3.15 | Smoke: PATCH `{}` → 200 idempotente, `updated_at` intacto | [S] podman |
| E3.16 | Smoke: PATCH `{"name": null}` / `{"status": null}` → 400 `field_required` | [S] podman |
| E3.17 | Smoke: PATCH status fuera de enum ×3 → 400 `filter_invalid_value` (nunca 500) | [S] podman |
| E3.18 | Smoke: PATCH name que choca con otro proyecto → 409 `name_duplicate` | [S] podman |
| E3.19 | Smoke: PATCH id inexistente → 404 `not_found` | [S] podman |
| E3.20 | Smoke: GET repetido no altera `updated_at`; PATCH con campo sí | [S] podman |
| E3.21 | Smoke: 401 sin JWT en las 4 rutas (GET×2/POST/PATCH) | [S] podman |
| E3.22 | Smoke: `:8080/mockups/04-proyectos.html` + `05-proyecto-detail.html`: login inline, lista real, filtro, archivar/desarchivar con fetch; `/docs/mockups` intacto | [S] podman |
| E3.23 | Smoke: re-ejecución Q8–Q10/Q24 de C2 (login, `invalid_credentials` ×3, `invalid_token` `/me` y `/panel/projects`, preflight 200) | [S] podman |
| E3.24 | Diff 0 sobre SecurityConfig/CorsConfig/entry point/validador/application-test.yml/V1/compose.yaml//docs; cero migraciones | [S] |
| E3.25 | Unit: serializar `ProjectDto` → claves exactas sin `summary`, `description: null`, status literal, fechas `Z` | [L] |
| E3.26 | `@WebMvcTest` con repo mockeado: tabla de casos R3.15 completa, límites 200/2000 ídem de borde, fail-fast de `status`, catch UNIQUE → 409, 404, sin `update()` en `{}` | [L] |

**Reglas del mapa**: los tests [L] usan `spring-boot-starter-test` (scope
test, BOM) **sin contenedor ni BBDD** (arrancan en segundos; sin
Testcontainers ni H2 — G4/reevaluación C5) y respetan el
`application-test.yml` dummy del validador de C1 (INTOCABLE). Los smokes
[S] se corren contra la pila podman completa (curl por `:8080` con JWT
real de `POST /api/v1/panel/auth/login`): **idempotencia** con sufijos de
timestamp por ejecución (`smoke-c3-<ts>`) — repetibles sin limpiar volumen
y sin depender de borrado (DELETE es C10); **anti-veneno de env** (env del
proceso UNSET + `.env` de forma robusta, `recreate` y NUNCA `restart` si
toca recomponer/reconstruir); **sin parse JSON estricto del body de los
401** (ISO-8859-1 del entry point — heredado, fix en C8/C9). El Done
literal del 07 (smoke de estados con 409 y 400 incluidos + maquetas
servibles) queda cubierto por E3.1–E3.21 y E3.22; E3.23/E3.24 son los
pasos de no-regresión obligatorios (R3.20).