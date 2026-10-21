---
change: "C3"
artifact: proposal
last_updated: 2026-10-21
status: approved
gate: >
  Aprobado por el usuario 2026-10-21 en los 5 puntos del GATE (con las
  recomendaciones): (1) literales del 400 field_too_long + field_required
  tal cual; (2) PATCH parcial tal cual (ausente=sin cambio; description
  null limpia; name/status null → 400; {} → 200 idempotente); (3) ?status=
  y status de PATCH inválidos → 400 fail-fast (filter_invalid_value), nunca
  silencio; (4) tests patrón C2 (unit + smokes podman), SIN Testcontainers
  en C3 (reevalúa C5); (5) maquetas 04/05 servibles como copias adaptadas
  en frontend/public/mockups/ (login inline + fetch, /docs/mockups intacto,
  puente desechable hasta C8).
---

# Proposal — C3 · Proyectos: CRUD + archivado

> Fase sdd-propose del flujo SDD para el corte C3 (`07_SLICING_PLAN.md`,
> detalle C3, estado pendiente). Resuelve las decisiones abiertas P-0…P-6 de
> `explore.md` §2 (las marcadas como gate P-1…P-5), propone el plan de
> trabajo y deja listas las preguntas del gate. Los literales de los docs se
> **citan, nunca se redefinen** (regla del 07). Nada de esto se ejecuta hasta
> que el usuario apruebe el gate: no se toca código, no se hacen commits,
> no se crean otros ficheros.
>
> Heredado de C2 (archivado PASS-with-notes, HEAD `829cc70`): NO romper las
> 3 cadenas de `SecurityConfig` ni el CORS mínimo (Q24, preflight 200 —
> divergencia ya registrada); el 401 del entry point sale ISO-8859-1 (los
> smokes curl NO parsean JSON estricto, WARNING 1 → fix en C8/C9);
> anti-veneno de env en smokes (WARNING 2 / R-nuevo-3: env del proceso UNSET
> + `.env` + `recreate`, nunca `restart`); `application-test.yml` dummy y el
> validador de C1 son INTOCABLES.

## 1. Objetivo y alcance

### 1.1 Objetivo

Implementar el CRUD de proyectos y el archivado/desarchivado sobre la tabla
`projects` (ya migrada en V1, C1), dentro de la superficie protegida del
panel (`/api/v1/panel/**`, cadena 2 — los 4 endpoints **nacen protegidos**,
Q10/S19 ya lo verificó en C2), y dejar los endpoints consumibles por las
maquetas 04/05 servidas desde el frontend. Sin DELETE, sin API keys, sin
summary, sin frontend real de panel.

### 1.2 Entregables

1. **`ProjectsRepository`** — clase en `com.projectlog.projects`, `JdbcTemplate`
   con SQL explícito, patrón C2 (paquete `projects` ya existe con
   `package-info.java` marcador de C1). Expone
   `findAll(status?)`, `findById(id)`, `insert(...)`, `update(...)`.
   Sin capa de servicio intermedia (resolución P-0, ver §2.0).
2. **`ProjectsController`** — controller fino que orquesta validación manual →
   repo → DTO, con `@ExceptionHandler` local para la captura de la UNIQUE
   (`DuplicateKeyException`/`DataIntegrityViolationException` → 409
   `name_duplicate`) según R-1.
3. **DTOs records literales del Schema §3.3** — `ProjectDto` con EXACTAMENTE
   los campos `id, name, description, status, created_at, updated_at,
   api_key_prefix, has_key` — **`summary` AUSENTE** (es C10; explorar ya lo
   ancla: "ausente salvo GET /projects con include=summary"). `has_key` es
   **derivado** (`api_key_hash IS NOT NULL`), nunca columna. `description`
   puede ser `null`; proyecto recién creado sale siempre
   `api_key_prefix: null, has_key: false` (creación C3 nunca toca la key:
   AppFlow flujo 2 paso 4).
4. **Validación manual** con literales de error a aprobar en el gate (P-1):
   sin `spring-boot-starter-validation` — el pom no lo trae y C2 validó
   manualmente con constantes de literal y cuerpo via `ObjectMapper`
   (patrón `LoginController.credencialesInvalidas()`).
5. **409 por carrera R-1**: INSERT directo + catch de `projects_name_unique`;
   el check-then-insert (SELECT previo) **pierde** la carrera y queda
   descartado (ver §5, Riesgos).
6. **`updated_at` explícito en el UPDATE** — sin trigger en V1, el DEFAULT
   solo aplica al INSERT; el SQL debe llevar `updated_at = now()` en el SET,
   porque la verificación del AppFlow flujo 2 ("Cambios guardados" con
   `updated_at` refrescado) depende de ello (riesgo funcional identificado en
   explore §1.4).
7. **Maquetas 04/05 servibles** — copias adaptadas en `frontend/public/mockups/`
   servidas por el nginx de C1 en `:8080/mockups/…` con login inline + fetch
   mínimo contra la API real (resolución P-4; los originales de `/docs/mockups/`
   quedan congelados e intactos).
8. **Smoke de estados** en la pila real (podman-compose, puerto único 8080,
   anti-veneno de env): el criterio del Done, ver §3 paso 7.

### 1.3 Fuera de alcance (con ancla)

| Excluido | Qué es | Corte que lo resuelve (ancla) |
|---|---|---|
| `DELETE /projects/{id}` + `X-Confirm-Name` | borrado con CASCADE | C10 (Schema §3.2 marca P1; 07 lo lista en C10) |
| API key regenerate/revoke | `POST …/api-key/regenerate` 201, `DELETE …/api-key` 204 | C4 (07) |
| Regla 403 `project_archived` | rechazo de ingesta sobre archivado | C5 (07; nota de cruce: "aquí existe el DATO status, allí la regla") |
| `summary` / `include=summary` / `GET …/summary` | SummaryDto §3.3 | C10 |
| Keyset pagination | `GET /logs` `{items, next_cursor}` | C6 (keyset exclusivo de LOGS; proyectos = lista simple, explore §1.5) |
| Frontend real `/proyectos` | React del panel según maquetas | C8 |
| `X-Confirm-*` | ya configurable en CORS (C2), se usa en C10 | C10 (no se toca aquí) |

No se crea ninguna migración: V1 ya contiene `projects` íntegra
(explore §1.4 verificó las columnas y las constraints
`projects_name_unique` y `projects_status_check`) y cero migraciones nuevas
es el estado contractual de C2 (S24/E3).

## 2. Resolución de puntos abiertos

> P-0 y P-6 (arquitectura de capas, orden de construcción) los resuelve el
> explore con recomendación clara y no exigen gate: se fijan aquí sin
> pregunta. P-1…P-5 llevan pregunta explícita al gate (§4 y §7).

### P-0 — Repositorio JdbcTemplate, controller fino, sin capa de servicio (RECOMENDACIÓN: aprobar)

- **Recomendación**: `JdbcTemplate` directo + `ProjectsRepository` +
  `ProjectsController` que orquesta, **sin capa de servicio intermedia**.
- Justificación anclada: el pom NO trae `spring-boot-starter-jpa` (trae
  `spring-boot-starter-jdbc`); C2 usó `JdbcTemplate` con SQL inline
  (`LoginController`) y el esquema es de Flyway, no de Hibernate. Con 4
  operaciones mono-sentencia (cada una INSERT o UPDATE de una fila), el
  autocommit de JdbcTemplate basta; `@Transactional` en el repo queda como
  documentación/defensa, el batch all-or-nothing de C5 y el CASCADE de C10
  introducen lo multi-sentencia.
- `ProjectsRepository` centraliza las consultas porque **C4 necesita
  `findById`** (el modal de regeneración lee `api_key_prefix`/`has_key` de
  `GET /projects/{id}`, AppFlow flujo 3 paso 1) y C6 mapea el nombre desde
  `GET /projects` — la pieza es compartida downstream (explore §4).
- Alternativas descartadas: capa de servicio extra (4 operaciones no pagan el
  indirection); Hibernate/`JpaRepository` (requeriría starter nuevo y
  mapear un esquema que Flyway ya gobierna).

### P-1 — Literales del 400 de validación de campos (GATE 1 · RECOMENDACIÓN: `field_too_long` / `field_required`)

- **Recomendación**: 400 de longitud →
  `{"code": "field_too_long", "field": "name"|"description"}` reutilizando el
  patrón del propio Schema (§4.1 lo usa para la ingesta: "400
  `field_too_long`, `field`"); 400 de nombre ausente/vacío →
  `{"code": "field_required", "field": "name"}`. Cuerpo estándar §3.1
  (`{"code", "message", "field"?}`), `message` en español descriptivo.
- Justificación: el AppFlow flujo 2 paso 3 solo exige "400 validación
  (nombre vacío o demasiado largo) → errores por campo"; el `code` exacto es
  hueco de contrato, y la reutilización del patrón existente del Schema evita
  inventar un vocabulario nuevo. Coherencia con §3.1: "errores siempre
  explícitos, nunca silenciosos".
- Alternativas descartadas: literal nuevo e invención propia
  (`project_name_invalid`, etc. — rompe el patrón §4.1 y añade vocabulario
  sin ancla); `field_too_long` solo para description y otra cosa para name
  (dos literales para la misma causa es inconsistencia innecesaria).
- Validación **manual** en el controller (sin `spring-boot-starter-validation`):
  ver §5 (por qué NO añadirlo ahora sigue siendo correcto).

### P-2 — Semántica PATCH parcial (GATE 2 · RECOMENDACIÓN: ausente=sin cambio; description null limpia; name/status null → 400; {} → 200)

- **Recomendación** (4 reglas):
  1. **Campo ausente = sin cambio** (PATCH parcial natural; AppFlow flujo 2:
     "solo los campos modificados").
  2. **`description: null` explícito LIMPIA la descripción** — es el único
     campo nullable del DTO (§3.3: "description puede ser null"); es la única
     operación de limpieza posible vía API y es barata de implementar.
  3. **`name: null` o `status: null` → 400** `field_required, field: "name"|`
     `"status"` — no existe valor legal nulo para esos dos (name es NOT NULL
     con UNIQUE; status NOT NULL con CHECK).
  4. **PATCH `{}` vacío → 200** sin cambios, devolviendo el DTO actual
     (idempotente; un 400 con `field_required` castigaría un PATCH válido
     pero vacío, y el flujo 2 no contempla rechazarlo).
- Coexistencia name+status en la misma llamada: permitida por el Schema (los
  3 campos en la misma entrada §3.3) y el flujo 7 paso 6 ya contempla el 409
  combinado; el 409 viaja igual (R-1).
- Alternativas descartadas: PUT completo (el contrato es PATCH parcial);
  `description: null` ignorado (la limpieza quedaría imposible hasta C10);
  PATCH vacío → 400 (regla sin ancla y anti-idempotente).

### P-3 — Query `?status=` (GATE 3 · RECOMENDACIÓN: inválido → 400 `filter_invalid_value`)

- **Recomendación**: `GET /projects?status=` con valor fuera de
  `"ACTIVE"|"ARCHIVED"` → **400 `{"code": "filter_invalid_value", "field":
  "status"}`**, fail-fast antes de tocar la BBDD, reutilizando el literal
  existente del Schema para filtros inválidos (§3.3 logs:
  "`environment`/`level` fuera de enum (`filter_invalid_value`)").
- Justificación: ignorar el filtro en silencio es un "error silencioso" —
  anti-patrón declarado en §3.1 (métrica 2 del PRD). El patrón del Schema
  para valor fuera de enum es 400; no hay razón para que el único filtro de
  proyectos se comporte distinto al de logs.
- Sin `status` = **todos** (el filtro es opcional; lista simple `[ProjectDto]`
  sin paginación — explore §1.5: TRD §2.4 "Para la lista de proyectos (pocas
  filas), offset es irrelevante"). `status` distingue MAYÚSCULAS (literal del
  CHECK); minúsculas/mal escrito → 400, no normalización silenciosa.
- Prolongación coherente (misma pregunta de gate, punto 3): el `status` del
  PATCH con el mismo tratamiento de borde (regla 10 del Schema obliga y, si
  escapara, el CHECK convertiría el fallo en 500 — R-2): presente fuera de
  enum → 400 `{"code": "filter_invalid_value", "field": "status"}`;
  `status: null` explícito → 400 `{"code": "field_required", "field":
  "status"}` (regla 2 del gate: no hay valor legal nulo). Así la query y el
  campo comparten un solo vocabulario, sin literal nuevo sin ancla.

### P-4 — Maquetas 04/05 servibles (GATE 5 · RECOMENDACIÓN: copias adaptadas + login inline + fetch mínimo)

- **Recomendación**: copias adaptadas de
  `docs/mockups/04-proyectos.html` y `docs/mockups/05-proyecto-detail.html`
  en `frontend/public/mockups/` (el pipeline Vite de C1 copia `public/` a
  `dist/` en el build — verificado en explore), servidas por el nginx de C1
  en `:8080/mockups/04-proyectos.html` y `…/05-proyecto-detail.html`,
  **mismo origen** (el proxy ya enruta `/api/v1/**`: cero CORS, cero
  infra nueva). La capa mínima de wiring:
  - login inline: un pequeño form que llama `POST /api/v1/panel/auth/login`
    y guarda el token en memoria (`sessionStorage` local de la copia);
  - `04-proyectos.html`: pintar la lista desde `GET /api/v1/panel/projects`
    (+ toggle de filtro `?status=`), y acción Archivar/Desarchivar con PATCH
    `{status}` por fila;
  - `05-proyecto-detail.html`: pintar ficha desde
    `GET /api/v1/panel/projects/{id}`, botón Archivar/Desarchivar → PATCH.
  Se prueba dentro del smoke del Done: abrir
  `http://<host>:8080/mockups/04-proyectos.html`, loguear, operar CRUD real.
- **Por qué NO se tocan los originales**: `/docs` es un paquete de
  documentación **congelado y validado por gates** (04/05 = maquetas
  aprobadas 2026-10-19, memory del proyecto); modificarlas las sustraería del
  registro de aprobación y mezcla el artefacto de diseño con el puente de
  validación. La servibilidad del Done es un puente desechable: C8 rehace el
  panel en React real y las copias se retiran entonces.
- Alternativas descartadas: (a) abrir los HTML con `file://` contra la API —
  rompe por CORS (otro origen) y no es "servible" en la pila; (b) servidor de
  mockups aparte — viola el puerto único (TRD §6.1/§9); (c) wirear los
  originales con un script que los inyecte — equivalente a tocarlos.
- **No es el frontend final**: sin React, sin rutas SPA, sin estados del
  AppFlow §4 más allá del mínimo para operar; los formularios
  `/proyectos/nuevo` y `/proyectos/:id/editar` van SIN maqueta (decisión de
  coherencia ya registrada), así que el POST/PATCH se prueba con la copia de
  04 vía fetch mínimo o con curl del smoke.

### P-5 — Estrategia de tests (GATE 4 · RECOMENDACIÓN: patrón C2, sin Testcontainers)

- **Recomendación**: mantener el patrón C2 —
  - tests unitarios [L] con `@WebMvcTest`/slices sobre el controller
    (validación de borde, literales, formas del DTO; el repo se stubbea o se
    ejercita con la BD real en smoke), respetando el `application-test.yml`
    dummy del validador de C1 (INTOCABLE);
  - **smoke en pila real** (podman-compose, `:8080` por el proxy, JWT real de
    `POST /auth/login`) que ejerce TODO el CRUD + archivado contra Postgres 17
    real: es exactamente lo que el Done pide ("smoke de estados") y lo que C2
    ya hizo con el seed (22 escenarios E2.x).
- Justificación: el SQL de projects son 4 statements triviales; introducir
  Testcontainers en C3 añade imagen + red bajo podman rootless + un
  mecanismo de test nunca probado en este repo, para cubrir lo que el smoke
  de pila ya cubre. Se re-evalúa en C5 (all-or-nothing transaccional del
  batch), donde sí merece la pena un test de integración repetible.
- Idempotencia del smoke: nombres de proyecto con sufijo por ejecución
  (timestamp de la corrida, p. ej. `smoke-c3-<ts>`) para poder repetir el
  smoke sin limpiar volumen y sin depender de borrado (DELETE es C10). Se
  fija definitivamente en tasks.

## 3. Plan de trabajo por pasos (con verificación observable)

> Cada paso cierra con su verificación observable, mapeada a un criterio del
> Done ("CRUD+archivado con smoke de estados (409 y 400 incluidos); maquetas
> 04/05 servibles contra esta API"). Sin DELETE, sin summary, sin keys.

**Paso 1 — Proyecto Dto (record) + formatter de fechas compartido.**
`ProjectDto` §3.3 literal (sin `summary`), reutilizando el patrón de formatter
UTC con `Z` (tipo `LoginController.EXPIRES_AT_FORMATTER`, R-6) para
`created_at`/`updated_at`; `has_key` derivado en la consulta SQL (expresión
`api_key_hash IS NOT NULL`) o post-mapeo.
*Verificación*: el record compila y su JSON de prueba (unit) tiene exactamente
las claves §3.3; `new ProjectDto(...).status()` = literal `ACTIVE`.

**Paso 2 — `ProjectsRepository` (JdbcTemplate, SQL explícito).**
`findAll(status?)` (WHERE opcional por `status`), `findById`, `insert`
(INSERT directo, sin SELECT previo — R-1), `update` (UPDATE por columnas
acotadas: `name`/`description`/`status` solo los presentes; SET SIEMPRE con
`updated_at = now()` — R-4; NUNCA toca `api_key_hash`/`api_key_prefix`/
`key_created_at`). `@Transactional` como defensa documentada.
*Verificación*: SQL inspeccionado en review; en smoke, un PATCH con
`description: null` deja la columna NULL y `updated_at` cambia (comparar
antes/después).

**Paso 3 — POST /api/v1/panel/projects (201 · 400 · 409).**
Validación manual: `name` ausente/vacío → 400 `field_required, field: name`;
longitud >200 → 400 `field_too_long, field: name`; `description` >2000 →
400 `field_too_long, field: description`. INSERT directo + catch de
`DuplicateKeyException` (sobre `projects_name_unique`) → 409
`name_duplicate` (R-1). 201 con el DTO completo, `api_key_prefix: null,
has_key: false` (nunca se genera key aquí: flujo 2 paso 4).
*Verificación (smoke)*: 201 con y sin description; 409 replicando nombre
(ida y vuelta inmediata, la carrera R-1 cubierta por la UNIQUE);
400 vacío y >200.

**Paso 4 — GET /api/v1/panel/projects (lista + ?status=) y
GET /api/v1/panel/projects/{id} (200 · 404).**
Lista simple `[ProjectDto]` sin paginación; `?status=` fuera de enum → 400
`filter_invalid_value, field: status` (gate 3); sin query → todos. Ficha:
200 o 404 `{"code": "not_found"}` (JSON estándar; nunca se traduce 404 como
vacío — convención AppFlow §5).
*Verificación (smoke)*: GET lista tras crear N proyectos; filtro ACTIVE/ARCHIVED;
404 con id inexistente; 401 sin JWT en ambas (cadena 2, ya heredada —
re-verificar).

**Paso 5 — PATCH /api/v1/panel/projects/{id} (editar + archivar/desarchivar).**
Semántica del gate 2 (ausente=sin cambio; `description: null` limpia;
`name`/`status` null → 400; `{}` → 200). `status` validado en el borde
(BORDER ANTES de BD, regla 10): fuera de enum → 400 (R-2); UPDATE por
columnas + `updated_at = now()`. 404 si no existe; 409 si el cambio de
nombre choca con la UNIQUE (R-1, misma técnica del catch). Archivado/desarchivado
es SOLO un UPDATE de `status` (regla 3: "archivar nunca borra nada"; regla 11:
no toca la key) — R-3.
*Verificación (smoke)*: PATCH de name; PATCH `{status: "ARCHIVED"}` → 200 con
`status: ARCHIVED`; PATCH `{status: "ACTIVE"}` → vuelve a ACTIVE; la key (aún
inexistente en C3) y los logs (ninguno) no se tocan; `updated_at` refrescado
en cada PATCH.

**Paso 6 — Maquetas 04/05 copias servibles.**
Copias en `frontend/public/mockups/` + adaptación mínima (login inline +
fetch). Sin tocar `/docs/mockups/**`. Reconstruir imagen frontend
(`up -d --build`, con anti-veneno de env R-7).
*Verificación (smoke)*: `http://<host>:8080/mockups/04-proyectos.html`
loguea, pinta los proyectos reales del smoke; desde 05 se archiva un
proyecto y la lista lo muestra con etiqueta y filtro ARCHIVED.

**Paso 7 — Smoke de estados completo (criterio del Done) + re-verificación de no-regresiones.**
Suite de curl en la pila real: 201 (con/sin description), 409 POST,
200 lista/ficha, 404 ficha, 400 (name vacío, name >200, description>2000),
PATCH name/description (`null` incluido), PATCH ARCHIVED → ACTIVE,
`?status=` inválido → 400, 401 sin JWT en las 4 rutas, `updated_at`
refrescado. Re-ejecutar los smokes de C2: Q8–Q10 (login, 401
`invalid_credentials` en las 3 ramas, 401 `invalid_token` en `/me` y en
`/api/v1/panel/projects` — esta última ahora **con** controller y sigue 401
sin JWT), y el preflight CORS Q24 (preflight 200/204 sin tocar `CorsConfig`).
Scripts sin parse JSON estricto de los 401 (R-8, ISO-8859-1).
*Verificación*: log del smoke con los estados literalmente anotados;
`git diff` sobre `SecurityConfig`, `CorsConfig`, `RestAuthenticationEntryPoint`,
`V1__init.sql`, `application-test.yml` = 0 líneas.

## 4. Preguntas para el gate del usuario (máx. 5)

1. **Literales del 400 de validación** (P-1): ¿aprobar `field_too_long`
   (`field: name|description`, patrón §4.1) y `field_required`
   (`field: name`)? → **Recomendación: sí** (reutilizar el vocabulario
   existente del Schema). Alternativa: otros literales que el usuario
   proponga.
2. **Semántica PATCH** (P-2): ¿ausente=sin cambio; `description: null`
   limpia; `name`/`status` null → 400; `{}` vacío → 200 idempotente?
   → **Recomendación: tal cual**. Alternativa: `{}` → 400 o
   `description: null` ignorado.
3. **`?status=` inválido en GET** (P-3): ¿400 `filter_invalid_value, field:
   status` (y el mismo literal para status inválido en PATCH, con
   `field_required` cuando es null)? → **Recomendación: 400 con ese literal**
   (fail-fast, patrón de filtros del Schema). Alternativa: ignorar el filtro
   en silencio.
4. **Estrategia de tests** (P-5): ¿mantener el patrón C2 (unit [L] + smoke en
   pila real, **sin** Testcontainers en C3)? → **Recomendación: sí**;
   Testcontainers se reevalúa en C5. Alternativa: introducir Testcontainers
   ya.
5. **Forma de "maquetas servibles"** (P-4): ¿copias adaptadas de 04/05 en
   `frontend/public/mockups/` con login inline + fetch real, servidas en
   `:8080/mockups/…`, con `/docs/mockups/` intacto? → **Recomendación: tal
   cual**. Alternativa: otra forma de servibilidad que el usuario defina.

## 5. Riesgos y rollback

- **R-1 · Carrera en el 409 `name_duplicate` (crítico)**: check-then-insert
  (SELECT previo + INSERT) pierde la carrera ante dos POST/PATCH simultáneos
  con el mismo name. **Mitigación**: INSERT (o UPDATE) directo y capturar
  `DuplicateKeyException`/`DataIntegrityViolationException` sobre
  `projects_name_unique` (el garante último es la constraint de la BD, V1)
  → 409 `name_duplicate`. En PATCH con cambio de nombre, la misma técnica
  obligatoria. Rollback del caso: no hay — la técnica ES el diseño.
- **R-2 · status fuera de enum → 500 si escapa**: la validación de borde
  (regla 10 del Schema: CHECK **y** borde, 400 antes de insertar) es
  obligatoria y va antes del UPDATE; si llegara a la BD, `projects_status_check`
  convertiría la petición en 500. Test del borde en unit + smoke. Mitigación
  estructural: la única escritura de `status` es el SQL acotado del repo tras
  validar el controller.
- **Validación manual vs Bean Validation («¿por qué NO añadir
  starter-validation ahora?»)**: sigue siendo correcto NO añadirlo porque
  (a) C2 fijó el patrón de validación manual con literales constantes
  (`CODE_*`) y cuerpo via `ObjectMapper` y los 400 de C3 son 3 reglas
  triviales (required/too_long/enum) — el starter añadiría una dependencia
  nueva y una segunda vía de mensajes de error que complicaría la
  uniformidad del cuerpo `{code, message, field}`; (b) sin starter no hay
  hibernate-validator en el classpath, así que `@Valid` sería un no-op
  silencioso (riesgo mayor que el que resuelve). Se añadiría si C5+ (ingesta,
  13+ campos del LogIngestRequest) lo justifica — decisión de ese corte.
- **`updated_at` sin trigger**: el DEFAULT de V1 solo aplica al INSERT; si el
  UPDATE no lo fija, la verificación "Cambios guardados con updated_at
  refrescado" (AppFlow flujo 2) fracasa en silencio. Mitigación: SET
  explícito `updated_at = now()` en el único UPDATE del repo + aserción en
  smoke (comparar antes/después). Sin migrar triggers: fuera de alcance y
  V1 intocable.
- **No-regresiones C2 (bloqueos heredados)**: C3 añade clases en
  `com.projectlog.projects` y nada más: `SecurityConfig` (3 cadenas),
  `CorsConfig` (preflight 200 — divergencia registrada), entry point
  (ISO-8859-1), validador de C1, `application-test.yml`, `V1__init.sql` y
  `compose.yaml` sin tocar. Los smokes Q8–Q10/Q24 de C2 re-ejecutables
  contra la misma pila tras C3 (paso 7) son la red de detección. Los 401 de
  las 4 rutas nuevas nacen de la cadena 2 — no hay cambio de seguridad que
  verificar más allá del 0 diff.
- **R-7 · Veneno de env en smokes**: recomponer la pila con el env del
  proceso UNSET + `.env` cargado de forma robusta, y `recreate` (nunca
  `restart`) si toca reconstruir. Heredado del verify C2 (WARNING 2).
- **Rollback general de C3**: revertir los commits de C3 (solo clases del
  paquete `projects` + copias `public/mockups/`) devuelve el repo al estado
  C2 verificado (`829cc70`); ninguna migración ni config compartida que
  deshacer. Ese es el motivo de la regla "C3 añade, no modifica".
- **`.env` disciplina**: `.env` NUNCA a git (TRD §6.3); los smokes de C3
  consumen los mismos secrets y no duplican credenciales.

## 6. Trazabilidad

| Fuente | Qué ancla en esta propuesta |
|---|---|
| Schema §3.2 (`05_BACKEND_SCHEMA.md` valido v1.0) | rutas exactas de los 4 endpoints, auth JWT, request → response (201/200 · 400 · 401 · 404 · 409), DELETE P1 excluido |
| Schema §3.3 | `ProjectDto` literal (sin `summary`), entradas `{name: 1..200, description?: ≤2000}` y PATCH `{name?, description?, status?}`, `has_key` derivado, `api_key_prefix: null` al crear |
| Schema §3.1 | cuerpo de error estándar `{"code","message","field"?}`, fechas ISO-8601 UTC `Z`, "errores siempre explícitos", 429 no existe |
| Schema §4 (reglas 2, 3, 10, 11, 12) + §4.2 | 409 `name_duplicate` por violación UNIQUE; archivar nunca borra; borde antes de BD; archivar no toca la key; archivado no afecta consulta; soft vs hard delete |
| Schema §6.1 / V1__init.sql (verificado) | columnas y constraints `projects_name_unique`, `projects_status_check`; sin trigger de `updated_at` |
| AppFlow flujo 2 (`03_APPFLOW.md` valido v1.0) | crear/editar: mensajes "Proyecto creado…", 409 in-line "Ya existe un proyecto con ese nombre", errores por campo, "Cambios guardados" + `updated_at` refrescado, key nunca en la creación |
| AppFlow flujo 7 | archivar/desarchivar vía PATCH `{status}` (nunca DELETE); etiqueta/etiqueta "Archivado"; key y logs intactos; 409 combinado |
| AppFlow §4/§5 | estados de `/proyectos` y ficha; manejo global de 400/404/409; nunca 404→vacío |
| TRD §2.3 (valido v1.1) | modelo de datos; "Archivar nunca borra nada: solo cambia status" |
| TRD §2.4 | lista de proyectos sin paginación (keyset exclusivo de logs) |
| TRD §2.5/§2.6/§9 | base `/api/v1/panel`, `Authorization: Bearer`, tabla de endpoints; proxy enruta solo `/api/v1/**` + `/ws/logs` |
| 07_SLICING_PLAN.md (C3, pendiente) | alcance y Done literal; nota de cruce C5 |
| 06_IMPLEMENTATION_PLAN.md (Fase 1) | stories 2, 13, 14 del orden de construcción |
| C2 heredados (`.atl/sdd/C2/verify-report.md` PASS-with-notes; archive-report) | 3 cadenas/CORS/entry point intocables; anti-veneno de env (R-nuevo-3); 401 ISO-8859-1 (R-nuevo-4, fix en C8/C9); Q8–Q10/Q24 re-ejecutables |
| C1 heredado (archive-report) | `/docs` congelado (04/05 aprobados 2026-10-19); pipeline Vite `public/`→`dist/`; validador de C1 intocable |
| PRD §3 (stories 2, 13, 14) | crear/editar proyectos; archivar/desarchivar; archivados conservan logs consultables |

## GATE: puntos a aprobar por el usuario

1. **Literales del 400 de validación de campos**: aprobar
   `field_too_long` (`field: name|description`, reutilizando el patrón §4.1
   de la ingesta) y `field_required` (`field: name`) — o los literales
   alternativos que el usuario defina. *(Recomendado: aprobar tal cual.)*
2. **Semántica PATCH parcial**: ausente = sin cambio; `description: null`
   presente LIMPIA la descripción; `name`/`status` con `null` → 400;
   PATCH `{}` vacío → 200 idempotente (devuelve el DTO sin cambios).
   *(Recomendado: aprobar tal cual.)*
3. **Query `?status=` inválida (y `status` inválido en PATCH)**: 400 antes
   de tocar la BD con `filter_invalid_value, field: status` (patrón de
   filtros §3.3) y `field_required, field: status` cuando es `null`
   explícito, en vez de ignorar el filtro en silencio.
   *(Recomendado: 400 fail-fast con esos literales.)*
4. **Estrategia de tests**: mantener el patrón C2 (unit [L] con slices +
   smoke E2E en pila podman contra Postgres real, SIN Testcontainers en C3);
   Testcontainers se reevalúa en C5 para el batch all-or-nothing.
   *(Recomendado: mantener patrón C2.)*
5. **Forma de "maquetas 04/05 servibles"**: copias adaptadas en
   `frontend/public/mockups/` (04 y 05) servidas por el nginx de C1 en
   `:8080/mockups/…`, mismo origen, con login inline (`POST
   /api/v1/panel/auth/login`) + fetch mínimo contra la API real;
   `/docs/mockups/` queda congelado e intacto; las copias son puente
   desechable hasta C8. *(Recomendado: aprobar tal cual.)*