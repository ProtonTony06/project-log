---
change: "C3"
artifact: tasks
last_updated: 2026-10-21
status: done
---

# Tasks — C3 · Proyectos: CRUD + archivado

> Desglose mecánico para `sdd-apply` con **contexto fresco**. Cada task es
> autosuficiente: contiene los ficheros que toca (rutas del árbol de 11
> ficheros nuevos de `design.md` §1.1), las referencias exactas (R3.n ·
> CD-3.n · E3.n con su verificación [L]/[S] · G-n y § de los docs) y un
> criterio "Done" observable. Este artefacto NO reescribe literales; apunta
> a la sección exacta del doc/spec donde se **copian**.
>
> Contexto del arranque: el repo ya contiene **C1 + C2 completos** (pila
> podman green, auth del panel activa, 3 cadenas de seguridad, seed
> idempotente, maquetas solo en `/docs/**`). C3 se limita a **AÑADIR**:
> 8 ficheros nuevos de backend (5 main + 3 test, todos en
> `com.projectlog.projects`), 2 copias adaptadas en
> `frontend/public/mockups/` y 1 script `scripts/qa_c3_smoke.sh` —
> **CERO ficheros de C1/C2 modificados** (diff-0 exigido, §0.3).
>
> Verificación SIEMPRE contra la pila completa con **podman**:
> `podman compose up -d --build` — `docker compose` NO está disponible en
> este entorno (heredado de C1/C2).

---

## 0. Recordatorios de contrato (leer ANTES de escribir nada)

### 0.1 Literales que se CITAN de los docs, nunca se redefinen

| # | Literal | Fuente exacta |
|---|---|---|
| L1 | 4 endpoints EXACTOS: `GET /api/v1/panel/projects` (query `status?` → **200** `[ProjectDto]` · 400 · 401) · `POST /api/v1/panel/projects` (`{name, description?}` → **201** `ProjectDto` · 400 · 409) · `GET /api/v1/panel/projects/{id}` (→ **200** · 401 · 404) · `PATCH /api/v1/panel/projects/{id}` (`{name?, description?, status?}` → **200** · 400 · 401 · 404 · 409). NO existe DELETE, ni `/api-key/regenerate`, ni `/summary`, ni `include=summary` en C3 | `docs/05_BACKEND_SCHEMA.md` §3.2 (filas 3–6); spec §1.C (R3.9) |
| L2 | `ProjectDto` claves EXACTAS `id, name, description, status, created_at, updated_at, api_key_prefix, has_key` — **`summary` AUSENTE** (C10); `description` puede ser `null`; `api_key_prefix: null` y `has_key: false` siempre en C3; `has_key` derivado de `api_key_hash IS NOT NULL` | `docs/05_BACKEND_SCHEMA.md` §3.3 + §2/§4 regla 11 (R3.7) |
| L3 | Entradas y límites: creación `{name: string 1..200, description?: string ≤2000}`; PATCH todos opcionales `{name?, description?, status?: "ACTIVE"\|"ARCHIVED"}`; "archivar/desarchivar va vía PATCH con `status`, **nunca por DELETE**" | `docs/05_BACKEND_SCHEMA.md` §3.3 (R3.13/R3.16) |
| L4 | Cuerpo de error estándar `{"code", "message", "field"?}` (sin HTML, sin cuerpos vacíos); fechas ISO-8601 UTC con sufijo `Z`; 425/429 no existen | `docs/05_BACKEND_SCHEMA.md` §3.1 (R3.12) |
| L5 | Literales de error APROBADOS: `field_required` (field: name, status) · `field_too_long` (patrón §4.1 de la ingesta "400 `field_too_long`, `field`" — field: name, description) · `filter_invalid_value` (patrón §3.3 logs — field: status, query y PATCH) · `name_duplicate` (409, §4 regla 2) · `not_found` (404) · `invalid_token` (401, entry point C2 exclusivo) | Gate G1/G3 2026-10-21; `docs/05_BACKEND_SCHEMA.md` §4.1, §3.3, §3.1, §4 regla 2 (R3.11/R3.12) |
| L6 | Reglas de integridad del Schema §4: regla 2 (UNIQUE name → **409 en línea**, datos conservados) · regla 3 ("Archivar **nunca** borra nada: solo cambia `status`") · regla 10 (enums cerrados forzados por CHECK **y** validación en el borde, **400 antes de insertar**) · regla 11 ("Archivar **no** toca la key ni los logs") · regla 12 ("el archivado afecta al flujo, no a la consulta") · §4.2 (sin soft-delete; archivar reversible) | `docs/05_BACKEND_SCHEMA.md` §4/§4.2 (R3.13/R3.15/R3.16) |
| L7 | Tabla `projects` de V1 (columnas y constraints literales): `id BIGINT IDENTITY PK`; `name TEXT NOT NULL`; `description TEXT`; `status TEXT NOT NULL DEFAULT 'ACTIVE'`; `api_key_hash BYTEA`; `api_key_prefix TEXT`; `key_created_at TIMESTAMPTZ`; `created_at`/`updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`; `CONSTRAINT projects_name_unique UNIQUE (name)`; `CONSTRAINT projects_status_check CHECK (status IN ('ACTIVE','ARCHIVED'))`; índice parcial `projects_api_key_hash_idx`; **SIN trigger de `updated_at`** (el SET explícito del repo lo cubre) | `backend/src/main/resources/db/migration/V1__init.sql` (aplicado en C1, INTOCABLE) + Schema §2/§6.1 |
| L8 | AppFlow flujo 2 (crear/editar): 201 → ficha; **409 in-line "Ya existe un proyecto con ese nombre", datos conservados**; PATCH con **solo los campos modificados** → "Cambios guardados" + `updated_at` refrescado; la key nunca en la creación | `docs/03_APPFLOW.md` flujo 2 (R3.14/R3.15) |
| L9 | AppFlow flujo 7 (archivar): `PATCH /projects/{id}` con `{status: "ARCHIVED"}` (nunca DELETE); etiqueta "Archivado" y botón Desarchivar; logs históricos siguen consultables; desarchivar → `{status: "ACTIVE"}` · AppFlow §5 fila 404: "No existe (quizás fue eliminado)" — un 404 NUNCA se traduce a respuesta vacía o lista vacía | `docs/03_APPFLOW.md` flujo 7 y §5 (R3.11/R3.16) |
| L10 | Lista SIN paginación: array plano `[ProjectDto]` (sin `{items, next_cursor}` — keyset exclusivo de LOGS, C6); "Para la lista de proyectos (pocas filas), offset es irrelevante"; vacío legítimo → `200 []` | `docs/05_BACKEND_SCHEMA.md` §3.2/§3.3; `docs/02_TRD.md` §2.4; AppFlow §4 (R3.4) |

### 0.2 Decisiones del gate (5/5 aprobadas 2026-10-21) — VINCULANTES

- **G1 Literales del 400**: `field_too_long` (`field: name|description`, patrón
  §4.1) + `field_required` (`field: name`) — tal cual.
- **G2 PATCH parcial**: ausente = sin cambio; `description: null` **LIMPIA**
  (columna NULL); `name`/`status` null → 400 `field_required`; `{}` vacío →
  **200 idempotente sin UPDATE** (el controller no llama a `repo.update`).
- **G3 `?status=` y `status` de PATCH inválidos**: 400 fail-fast
  `filter_invalid_value` (`field: status`), **nunca silencio** (anti-patrón
  "error silencioso", métrica 2 del PRD); `status: null` → 400
  `field_required`. Un vocabulario único para query y PATCH.
- **G4 Tests**: patrón C2 (unit [L] sin contenedor ni BBDD + smokes [S]
  podman en pila real), **SIN Testcontainers ni H2 en C3** (reevaluación en
  C5). El SqlTest verifica la CONSTRUCCIÓN de la constante SQL, nunca su
  ejecución.
- **G5 Maquetas servibles**: **copias adaptadas** de `docs/mockups/04|05` en
  `frontend/public/mockups/` (login inline + fetch mínimo a la API real),
  servidas en `:8080/mockups/…` mismo origen; `/docs/mockups/**` congelado e
  intocado; puente desechable hasta C8.
- Resuelto con recomendación (no gate, ya aprobado en proposal): P-0
  `ProjectsRepository` (JdbcTemplate) + controller fino **sin capa de
  servicio**; R-1 INSERT/UPDATE directo + catch de la UNIQUE → 409
  (check-then-insert descartado); `updated_at = now()` explícito en cada
  UPDATE; fechas con el formatter de C2; orden de construcción: DTOs → repo
  → POST → GET → PATCH → maquetas → smoke (P-6).

### 0.3 Intocables de C1/C2 (diff vacío EXIGIDO — E3.24 y design §1.2/§5)

C3 **solo añade**. `git diff --exit-code` sobre `HEAD` para cada uno de
estos ficheros (lista literal del design §5) DEBE salir vacío:

- Seguridad: `backend/src/main/java/com/projectlog/config/SecurityConfig.java`,
  `config/CorsConfig.java`, `config/RestAuthenticationEntryPoint.java`,
  `auth/TokenService.java`, `auth/JwtAuthFilter.java`, `auth/AdminSeeder.java`
  — los 4 endpoints **nacen protegidos** por `anyRequest().authenticated()`
  de la cadena 2 bajo `/api/v1/panel/**` (S19; cero líneas tocadas en C3).
- Validador C1: `config/StartupSecretsValidator.java` +
  `backend/src/main/resources/META-INF/spring.factories` (S29, javadoc
  incluido).
- Config: `backend/src/main/resources/application.yml` y
  `application-test.yml` (dummy, INTOCABLE — los [L] lo respetan con
  `@ActiveProfiles("test")`).
- BD: `backend/src/main/resources/db/migration/V1__init.sql` y
  `db/migration/` **sin ficheros nuevos** (cero migraciones; Flyway
  `flyway_schema_history` = solo versión 1).
- Build/infra: `backend/pom.xml` (sin starter-validation ni JPA; sin
  dependencias nuevas — `jackson-databind` ya viene con `starter-web`),
  `compose.yaml`, `frontend/nginx.conf`, `frontend/Dockerfile`,
  `frontend/vite.config.ts`.
- Congelados: `docs/mockups/**` (los 6 HTML aprobados 2026-10-19 — C3 crea
  **copias**, jamás toca los originales), `scripts/qa_c2_smoke.sh` (se
  replica el patrón en un script NUEVO, nunca se muta ni se invoca),
  `scripts/ws_echo_test.py`.
- Marco: `com.projectlog.projects/package-info.java` (rol marcador C1, su
  javadoc ya anuncia C3) — NO se modifica.

### 0.4 Disciplina heredada de C2 (obligatoria en F5/F6)

- **Anti-veneno de env** (R-nuevo-3 de C2): si toca recomponer la pila, env
  del proceso UNSET + `.env` cargada de forma robusta línea a línea **sin
  exportar** y `recreate` (NUNCA `restart`) tras rebuild.
- **401 ISO-8859-1 sin parse estricto** (R-nuevo-4/WARNING 1 de C2): el 401
  del entry point NO se parsea como JSON estricto en los smokes; se
  verifican status y código con decodificación tolerante. Fix real en
  C8/C9, no en C3.
- **Preflight CORS → 200 o 204** (divergencia ya registrada en C2): el
  criterio es `20[46]` con ACAO reflejada y PATCH en Allow-Methods.
- **Salud de la pila por podman** (NO curl `/actuator` desde el host): los 3
  servicios `Up (healthy)` via `podman compose ps` / `podman inspect`; la
  espera activa del smoke sondea `GET /api/v1/panel/me` → 401 `invalid_token`
  real (el proxy responde 200 index si el backend está caído).
- **Podman, nunca `docker compose`**; `podman compose up -d --build` /
  `podman compose exec …`.

---

## 1. Grafo de dependencias (orden de ejecución)

```
1.1 ProjectDto ── 1.2 Create/Update requests ── 1.3 ProjectDtoSerializationTest ── 1.4 build + COMMIT #1
2.1 COLS + RowMapper + findAll/findById ── 2.2 insert RETURNING ── 2.3 conjuntoSet + update ── 2.4 build + COMMIT #2
3.1 constantes + errores + GET lista ── 3.2 POST + handler 409 ── 3.3 GET {id} 404 ── 3.4 PATCH G2 ── 3.5 build + COMMIT #3
4.1 ProjectsControllerMvcTest ──┐
4.2 ProjectsUpdateSqlTest ──────┴── 4.3 mvn test completo + COMMIT #4
5.1 copia 04 ── 5.2 copia 05 ── 5.3 build Vite verificado + COMMIT #5
6.1 script qa_c3_smoke.sh ── 6.2 ejecución E3.1–E3.22 ── 6.3 no-regresiones E3.23/E3.24 + cierre
 (6.x SIN commit nuevo salvo fix; 6.2 necesita COMMIT #5 — la imagen frontend
  ya lleva las mockups y el backend ya lleva C3)
```

- Bloqueos clave: cada fase bloquea la siguiente; F3 bloquea el build final
  con tests; F4 bloquea E3.22 del smoke; F5/F6 bloquean el "Done" del corte
  (§5). `PatchUpdateProjectRequest.desdeJson(JsonNode)` (1.2) es prerrequisito
  de 2.3 (firma `update(id, req)`) y de 3.4 (PATCH).

---

## 2. Tasks

### Fase F0 — DTOs y requests (package `com.projectlog.projects.dto`)

#### 1.1 [x] · Crear `dto/ProjectDto.java`

- **Ficheros**: `backend/src/main/java/com/projectlog/projects/dto/ProjectDto.java`.
- **Cubre**: R3.1, R3.9 (claves §3.3), R3.7, R3.8; CD-3.1; L2.
- **Contenido** (CD-3.1, literal): record
  `ProjectDto(Long id, String name, String description, String status,
  String created_at, String updated_at, String api_key_prefix, boolean
  has_key)` — componentes con nombres **snake_case literales**; Jackson
  serializa el nombre del componente → claves JSON exactas sin
  `@JsonProperty` ni config (patrón `LoginResponse`/`MeResponse` de C2).
  **Sin `summary`** de ninguna manera. Las fechas van como `String` YA
  formateadas (el mapper las entrega con `Z`).
- **Depende de**: estado C1/C2.
- **Done**: compila sin anotaciones Jackson; ningún campo más allá de los 8
  literales (grep: cero `summary`).

#### 1.2 [x] · Crear `dto/CreateProjectRequest.java` y `dto/UpdateProjectRequest.java` (matriz JsonNode)

- **Ficheros**: `backend/src/main/java/com/projectlog/projects/dto/CreateProjectRequest.java`;
  `.../dto/UpdateProjectRequest.java`.
- **Cubre**: R3.1, R3.15; CD-3.1, CD-3.3, CD-3.7; G2.
- **Contenido**:
  - `CreateProjectRequest(String name, String description)` — binding
    Jackson estándar de POST; `description` ausente y `null` convergen en
    `null` (POST no tiene semántica parcial).
  - `UpdateProjectRequest(String name, String description, String status,
    Set<String> camposPresentes)` con factoría estática
    `static UpdateProjectRequest desdeJson(JsonNode nodo)` que marca
    presencia con `nodo.has(CAMPO)` **independiente del valor** (solo las 3
    claves `{name, description, status}` se leen; cualquier otra se ignora
    — CD-3.9). Es la pieza que ejecuta el G2: `{}` → `camposPresentes=[]`;
    `{"description": null}` → `description==null` PERO
    `camposPresentes.contains("description")`. Sin dependencias nuevas.
  - Constantes de nombre de campo (p. ej. `CAMPO_NAME = "name"`) compartidas
    por los records para evitar cadenas dispersas.
- **Depende de**: 1.1 (paquete).
- **Done**: compila; unit smoke rápido (o aserción dentro de 1.3/4.1)
  confirma: `{}` → set vacío; `{"description": null}` → presente sin valor;
  `{"name": "x", "status": "ARCHIVED"}` → 2 presentes con valores; campo
  desconocido `{"nmae": "x"}` → ignorado.

#### 1.3 [x] · Crear `dto/ProjectDtoSerializationTest.java` (E3.25 [L])

- **Ficheros**: `backend/src/test/java/com/projectlog/projects/dto/ProjectDtoSerializationTest.java`.
- **Cubre**: E3.25 [L]; CD-3.1/CD-3.2, R3.8; G4.
- **Contenido**: JUnit puro con el `ObjectMapper` de Boot (sin Spring
  context, sin BD): serializar un `ProjectDto` de prueba y asercion —
  claves JSON EXACTAMENTE `id, name, description, status, created_at,
  updated_at, api_key_prefix, has_key` (sin `summary`); `description: null`
  admitido; `status` = literal `"ACTIVE"`; `has_key` booleano; fechas con
  sufijo `Z` (regex sobre el String que le pasan: el mapeo a String lo hace
  el RowMapper de 2.1 — el test fija la forma del record, no el formatter).
- **Depende de**: 1.1.
- **Done**: `mvn -q -f backend/pom.xml test -Dtest=ProjectDtoSerializationTest`
  verde en segundos (sin contenedor, sin BD — G4).

#### 1.4 [x] · Verificar compilación de F0 y COMMIT #1

- **Ficheros**: ninguno nuevo (verificación).
- **Acción**: `mvn -q -DskipTests -f backend/pom.xml package` → retorno 0;
  correr el test de 1.3.
- **Depende de**: 1.1–1.3.
- **Commit #1**: `feat(projects): DTOs del panel de proyectos — ProjectDto §3.3 sin summary y requests de POST/PATCH` + trailer §3.
- **Done**: build limpio; el commit contiene EXACTAMENTE los 3 ficheros
  nuevos de F0 y NINGÚN otro (`git diff HEAD~1 --stat` = 3 ficheros);
  nada de §0.3 tocado.

---

### Fase F1 — `ProjectsRepository` (package `…projects.repository`)

#### 2.1 [x] · Crear `repository/ProjectsRepository.java` — constante COLS, RowMapper y lecturas

- **Ficheros**: `backend/src/main/java/com/projectlog/projects/repository/ProjectsRepository.java`.
- **Cubre**: R3.2, R3.4, R3.7, R3.8, R3.10 (contexto); CD-3.2, CD-3.3, CD-3.10.
- **Contenido** (CD-3.3): clase `@Repository` con `JdbcTemplate` (SQL
  inline, patrón C2) y `@Transactional` **como defensa documentada**
  (mono-sentencia: el autocommit basta). Constantes estáticas:
  - `COLS` (única fuente de las 3 lecturas):
    `id, name, description, status, created_at, updated_at, api_key_prefix,
    (api_key_hash IS NOT NULL) AS has_key`.
  - `RowMapper<ProjectDto> FILA` (privado, `static final`):
    `rs.getObject(col, OffsetDateTime.class)` →
    `.format(LoginController.EXPIRES_AT_FORMATTER)` para
    `created_at`/`updated_at` — **formatter de C2 REUTILIZADO POR
    REFERENCIA, no copiado** (R3.8); `has_key` via
    `rs.getBoolean("has_key")`.
  - `List<ProjectDto> findAll(String status)`: sin filtro →
    `SELECT {COLS} FROM projects ORDER BY created_at DESC, id DESC`;
    con filtro → `… WHERE status = ? ORDER BY created_at DESC, id DESC`
    (CD-3.10; el repo NUNCA recibe un valor fuera de enum: lo valida el
    controller, CD-3.7 — sin branch `IN` ni SQL dinámico aquí).
  - `Optional<ProjectDto> findById(long id)`:
    `SELECT {COLS} FROM projects WHERE id = ?`.
- **Depende de**: 1.1, 1.4 (records), `LoginController` de C2 (referencia al
  formatter, sin modificación).
- **Done**: compila; grep confirma una ÚNICA definición de columnas (COLS)
  compartida; el formatter se importa de `com.projectlog.auth.LoginController`
  (cero copia de patrón `yyyy-MM-dd…` en este paquete); el ORDER BY es
  literal `created_at DESC, id DESC` en ambas ramas de `findAll`.

#### 2.2 [x] · Añadir `insert(name, description)` con `INSERT … RETURNING` (una sola ida)

- **Ficheros**: el mismo de 2.1 (se amplía).
- **Cubre**: R3.5, R3.7, R3.14; CD-3.4; R-1.
- **Contenido** (CD-3.4): `ProjectDto insert(String name, String description)`
  → `INSERT INTO projects (name, description) VALUES (?, ?) RETURNING {COLS}`
  con `queryForObject(…, FILA, …)` — **INSERT directo, SIN SELECT previo
  (check-then-insert descartado, R-1)**; `description` nulo pasa como
  parámetro NULL en los varargs (el driver lo infiere como `text`). Los
  DEFAULT de V1 aplican solos: `status='ACTIVE'`, `created_at`/`updated_at =
  now()`; `api_key_prefix: null` y `has_key: false` salen del SQL de la fila
  (los INSERT de C3 no mencionan columnas de la key). La violación de
  `projects_name_unique` la traduce Spring JDBC a `DuplicateKeyException`
  (PG 23505) — se captura ARRIBA (controller, 3.2), no aquí (el repo no
  traduce errores).
- **Depende de**: 2.1.
- **Done**: compila; el SQL es UNA sentencia con RETURNING (sin
  `GeneratedKeyHolder`, sin re-lectura, sin `queryForObject` posterior);
  grep confirma cero `SELECT` de check en `insert` y cero columnas de key
  en el INSERT.

#### 2.3 [x] · Añadir `conjuntoSet(Set<String>)` y `update(id, req)` con `updated_at = now()`

- **Ficheros**: el mismo de 2.1 (se amplía).
- **Cubre**: R3.6, R3.15, R3.16; CD-3.5; G2/R-4.
- **Contenido** (CD-3.5):
  - `static String conjuntoSet(Set<String> presentes)` — **función pura,
    package-private, SIN `StringBuilder` frágil**: cadena de `if`/`if-else`
    sobre los **7 combos no vacíos** de `{name, description, status}`
    (tabla exacta del design CD-3.5; orden de los `?` = orden de enlazado
    del repo, misma iteración sobre el combo). Cada rama SIEMPRE incluye
    `updated_at = now()`; **NINGUNA rama menciona**
    `api_key_hash`/`api_key_prefix`/`key_created_at`. Sin `COALESCE`
    (un `{"description": null}` debe materializar `description = NULL`).
    Ante el combo vacío lanza `IllegalArgumentException` (contrato "≥1
    campo" — su test [L] lo fija en 4.2).
  - `ProjectDto update(long id, UpdateProjectRequest campos)`: el CONTROL
    de "¿hay algo que hacer?" vive ANTES (el controller no llama con set
    vacío, 3.4); aquí: `UPDATE projects SET <conjuntoSet(camposPresentes)>
    WHERE id = ? RETURNING {COLS}` con los parámetros enlazados en el MISMO
    orden del SET. Las partes fijas (`"UPDATE projects SET "`, `" WHERE id
    = ? RETURNING " + COLS`) son constantes estáticas.
- **Depende de**: 1.2 (record con `camposPresentes`), 2.2.
- **Done**: compila; grep confirma `updated_at = now()` presente en TODAS
  las ramas del `conjuntoSet`; CERO apariciones de columnas de key en el
  UPDATE; el método `update` rechaza (delega en la función pura) combos
  vacíos.

#### 2.4 [x] · Verificar compilación de F1 y COMMIT #2

- **Ficheros**: ninguno nuevo (verificación).
- **Acción**: `mvn -q -DskipTests -f backend/pom.xml package` → retorno 0.
- **Depende de**: 2.1–2.3.
- **Commit #2**: `feat(projects): ProjectsRepository — SELECT/INSERT/UPDATE sobre projects con RETURNING y updated_at explícito` + trailer §3.
- **Done**: build limpio; el commit contiene EXACTAMENTE el fichero del repo
  (1 fichero); `git diff HEAD~1` no toca nada de §0.3; el `package-info.java`
  marcador de C1 sigue intacto.

---

### Fase F2 — `ProjectsController` (package `…projects.controller`)

#### 3.1 [x] · Crear `controller/ProjectsController.java` — constantes de literales, helper de error y GET lista con fail-fast

- **Ficheros**: `backend/src/main/java/com/projectlog/projects/controller/ProjectsController.java`.
- **Cubre**: R3.3, R3.4, R3.9, R3.10, R3.12; CD-3.6, CD-3.7, CD-3.11; G1/G3.
- **Contenido**: `@RestController @RequestMapping("/api/v1/panel/projects")`
  (rutas literales L1; nacen protegidos por la cadena 2 — cero cambios de
  seguridad). Inyecta `ProjectsRepository` y `ObjectMapper`. **Sin capa de
  servicio** (R3.2); **sin `@Valid`/Bean Validation** (R3.3 — no existe
  hibernate-validator en el classpath; el patrón es
  `LoginController.credencialesInvalidas()` de C2):
  - Constantes de literal: `CODE_FIELD_REQUIRED`, `CODE_FIELD_TOO_LONG`,
    `CODE_FILTER_INVALID_VALUE`, `CODE_NAME_DUPLICATE`, `CODE_NOT_FOUND`
    (L5 — cero literales nuevos sin ancla; el 401 es exclusivo del entry
    point de C2).
  - Helper privado `error(HttpStatus, code, message, String field…)` con
    `objectMapper.valueToTree(...)` (la clave `field` SOLO cuando aplica —
    `Map.of` no admite `null`).
  - **`GET ""`** (`listar`): valida `status` con el enum cerrado
    **ANTES de tocar la BBDD** (CD-3.7, fail-fast G3) — `null` →
    `findAll(null)` (todas las filas); `ACTIVE`/`ARCHIVED` (distingue
    MAYÚSCULAS, literal del CHECK) → `findAll(status)`;
    cualquier otro valor, incluida la clave presente y vacía
    (`?status=` — CD-3.11), → **400 `filter_invalid_value, field: status`**
    sin invocar al repo. Responde **200** con el array plano
    `[ProjectDto]` (sin `{items, next_cursor}`, L10); vacío → `200 []`
    legítimo.
  - `GET /{id}` → 3.3 · `POST` → 3.2 · `PATCH` → 3.4 (se construyen en
    tasks posteriores; esta task cierra el esqueleto de clase + helper +
    listar).
- **Depende de**: 2.4 (repo completo).
- **Done**: compila; grep confirma las 5 constantes `CODE_*` con los
  literales L5; el chequeo de enum precede a cualquier llamada
  a `findAll` (lectura del fuente: la validación está antes del `if
  (status == null)`); minúsculas (`?status=active`) no se normalizan.

#### 3.2 [x] · Añadir `POST` → 201 y `@ExceptionHandler(DuplicateKeyException)` ÚNICO → 409

- **Ficheros**: el mismo de 3.1 (se amplía).
- **Cubre**: R3.5, R3.13, R3.14; CD-3.4, CD-3.6, CD-3.9; L5/L6/L8.
- **Contenido**:
  - `crear(@RequestBody CreateProjectRequest)`: validación de borde con
    límites LITERALES del Schema §3.3 — `name == null || isBlank` → 400
    `field_required, field: name`; `name.length() > 200` (200 sí vale, sin
    trimming) → 400 `field_too_long, field: name`;
    `description != null && length() > 2000` (`""` es legal) → 400
    `field_too_long, field: description` (patrón §4.1 — G1). Válido →
    `repo.insert(name, description)` → **201** con el `ProjectDto` de la
    fila recién creada (header `Location` opcional pero NO obligatorio —
    no está anclado; omitirlo es más simple).
  - **UN único** `@ExceptionHandler(DuplicateKeyException.class)` en el
    PROPIO controller → **409**
    `{"code": "name_duplicate", "message": "Ya existe un proyecto con ese
    nombre", "field": "name"}` (L8; mensaje de AppFlow flujo 2, datos
    conservados). Sirve a POST **y** PATCH (3.4) con el mismo punto — NO se
    mapea `DataIntegrityViolationException` genérico (enmascararía otras
    violaciones; el genérico cae a 500, correcto: no hay causa C3 legítima).
  - `@ExceptionHandler(HttpMessageNotReadableException.class)` local →
    **400** `{"code": "field_required", "message": "El cuerpo de la
    petición debe ser JSON válido"}` **SIN `field`** (CD-3.9: no se reutiliza
    `filter_invalid_value` ni se inventa `body_invalid`; el 401 de login
    es otro método y no lo alcanza).
- **Depende de**: 3.1.
- **Done**: compila; existe EXACTAMENTE UN `@ExceptionHandler` para
  `DuplicateKeyException` en el proyecto (grep); el literal del mensaje 409
  es el de AppFlow flujo 2; el 400 de cuerpo ilegible no lleva `field`.

#### 3.3 [x] · Añadir `GET /{id}` → 200/404 `not_found`

- **Ficheros**: el mismo de 3.1 (se amplía).
- **Cubre**: R3.11; CD-3.8; L9.
- **Contenido** (CD-3.8): `obtener(@PathVariable long id)` →
  `repo.findById(id)`; `Optional.empty()` → **404**
  `{"code": "not_found", "message": "No existe (quizás fue eliminado)"}`
  (sin `field` — el ejemplo §3.3 ancla el cuerpo como `{"code":
  "not_found"}`); presente → **200** con el DTO completo. NUNCA respuesta
  vacía ni lista vacía (AppFlow §5).
- **Depende de**: 3.1.
- **Done**: compila; el 404 lleva cuerpo JSON estándar (nunca `void`/cuerpo
  vacío); el literal es `not_found`.

#### 3.4 [x] · Añadir `PATCH /{id}` — semántica G2 completa con JsonNode

- **Ficheros**: el mismo de 3.1 (se amplía).
- **Cubre**: R3.6, R3.13, R3.15, R3.16; CD-3.5, CD-3.7, CD-3.8; G2/G3; E3.12–E3.20.
- **Contenido** (orden determinista "primer fallo gana", CD-3.6):
  1. `@RequestBody JsonNode` → `UpdateProjectRequest.desdeJson(nodo)` (1.2).
  2. **Validación antes de cualquier BD** (regla 10 / R-2): `name` presente
     y `null` → 400 `field_required, field: name`; `name` presente, no
     null, `isBlank` → 400 `field_required, field: name` (coherente con
     POST, E3.4); `name.length() > 200` → 400 `field_too_long, field:
     name`; `description` presente y `> 2000` → 400 `field_too_long, field:
     description`; `status` presente y `null` → 400 `field_required, field:
     status` (G3); `status` presente y fuera de `ACTIVE|ARCHIVED` exacto →
     400 `filter_invalid_value, field: status` (G3 — **nunca** llega a la
     BD: el CHECK lo convertiría en 500 prohibido, R-2).
  3. Si `camposPresentes.isEmpty()` (`{}`): **SIN llamar a `repo.update`** —
     `repo.findById(id)` + 200 con el DTO actual e `updated_at` intacto
     (G2/E3.15).
  4. Caso con campos: `repo.findById(id)` primero (404 `not_found` sin
     UPDATE — E3.19, CD-3.8) → `repo.update(id, req)` → `DuplicateKeyException`
     la captura el handler ÚNICO de 3.2 (409 — E3.18) → **200** con el DTO
     retornado (con `updated_at = now()`).
  El archivado/desarchivado ES este PATCH con `{"status": "ARCHIVED"|"ACTIVE"}`
  (R3.16 — nada de DELETE; el UPDATE nunca toca key ni logs).
- **Depende de**: 3.1, 3.2 (handler compartido), 3.3 (mismo patrón 404), 2.3.
- **Done**: compila; el flujo del PATCH sigue el orden literal
  validación → findById (404) → update/retorno; grep confirma que el camino
  `{}` no contiene ninguna llamada a `update` (solo `findById`); el enum
  distingue MAYÚSCULAS sin normalización.

#### 3.5 [x] · Verificar compilación de F2 y COMMIT #3

- **Ficheros**: ninguno nuevo (verificación).
- **Acción**: `mvn -q -DskipTests -f backend/pom.xml package` → retorno 0.
- **Depende de**: 3.1–3.4.
- **Commit #3**: `feat(projects): CRUD del panel — GET/POST/PATCH con validación manual, 409 por UNIQUE y archivado por status` + trailer §3.
- **Done**: build limpio; el commit contiene EXACTAMENTE el fichero del
  controller; con este commit `src/main/java/com/projectlog/projects`
  coincide 1:1 con design §1.1 (5 ficheros main, marcador C1 incluido);
  `grep -r "api/v1/panel/projects"` solo aparece en el controller (rutas
  literales, sin duplicar).

---

### Fase F3 — Tests ligeros [L] (CD-3.13 — sin Spring context completo, sin BD, sin Testcontainers/H2 — G4)

> Regla de fase: los tests [L] usan SOLO `spring-boot-starter-test` (JUnit 5
> + AssertJ + MockMvc + Mockito con `@MockitoBean`, Boot 3.5), respetan el
> `application-test.yml` dummy del validador de C1 (**INTOCABLE**) con
> `@ActiveProfiles("test")`, y arrancan en segundos. **No** se mockea el
> datasource para simular BD y NO se introduce H2/Testcontainers (G4). El
> SQL del repo se verifica en smoke (pila) y review — el unit verifica la
> CONSTRUCCIÓN, no la ejecución (R3.18).

#### 4.1 [x] · Crear `ProjectsControllerMvcTest.java` (E3.26 [L] + 401 [L])

- **Ficheros**: `backend/src/test/java/com/projectlog/projects/ProjectsControllerMvcTest.java`.
- **Cubre**: E3.26 [L] (y el 401 [L] adicional del design); CD-3.13.1; G4.
- **Contenido**: `@WebMvcTest(ProjectsController.class)` + `@Import({SecurityConfig,
  JwtAuthFilter, RestAuthenticationEntryPoint, CorsConfig})` + `@MockitoBean
  ProjectsRepository` + `@MockitoBean TokenService` + JWT real construido
  con un `TokenService` `new` local con secreto de test — **réplica EXACTA
  del fixture de `PanelAuthMvcTest` de C2** (el conjunto exacto de `@Import`
  se decide al escribir el test, como en C2). `@ActiveProfiles("test")`.
  Casos (con `Authorization: Bearer` del helper, salvo el 401):
  - POST: 201 con claves exactas §3.3 sin `summary`, límites válidos
    (name 200 / description 2000 aceptados); `name` vacío → 400
    `field_required, field: name`; name 201 → 400 `field_too_long, field:
    name`; description 2001 → 400 `field_too_long, field: description`.
  - GET: `?status=` y `?status=active|ARCHIVE|=...` → 400
    `filter_invalid_value, field: status` con `verifyNoInteractions(repo)`
    (fail-fast, CD-3.7); `?status=ACTIVE` → `findAll("ACTIVE")` invocado.
  - GET/PATCH `{id}`: `Optional.empty()` del mock → 404 `not_found`.
  - PATCH (tabla R3.15 completa): `{name: null}`/`{status: null}` → 400
    `field_required`; `status` fuera de enum ×3 → 400
    `filter_invalid_value` (antes de BD — el orden con `verify` sobre el
    mock lo demuestra); `{}` → 200 con `verify(repo, never()).update(…)`
    (G2/E3.15); `{"description": null}` → llamada a `update` con el combo
    correcto (`camposPresentes` contiene `description`, valor null);
    `DuplicateKeyException` lanzada por el mock → 409
    `name_duplicate` con cuerpo estándar (R-1 en [L] — E3.26).
  - 401 sin JWT en las 4 rutas → `invalid_token` del entry point
    (corrobora en [L] que el código nuevo nace tras el filtro).
  - DTO de salida con EXACTAMENTE las claves §3.3 sin `summary`.
- **Depende de**: 3.5 (controller completo), 4.1 requiere 1.2 (`desdeJson`).
- **Done**: `mvn -q -f backend/pom.xml test
  -Dtest=ProjectsControllerMvcTest` verde; la matriz R3.15 está completa en
  aserciones (revisar el método de test línea a línea contra la tabla del
  spec); `application-test.yml` sigue sin diffs.

#### 4.2 [x] · Crear `repository/ProjectsUpdateSqlTest.java` (construcción pura del SET)

- **Ficheros**: `backend/src/test/java/com/projectlog/projects/repository/ProjectsUpdateSqlTest.java`.
- **Cubre**: CD-3.5, CD-3.13.3 (cobertura [L] adicional); R3.6/R-4.
- **Contenido**: JUnit puro, **sin Spring context, sin JdbcTemplate real,
  sin BD**: fija la función pura `conjuntoSet(Set<String>)` — los 7 SET
  literales de la tabla CD-3.5, que TODOS incluyen `updated_at = now()`, que
  NINGUNA rama menciona `api_key_hash`/`api_key_prefix`/`key_created_at`,
  el envoltorio (`UPDATE projects SET ` … ` WHERE id = ? RETURNING …`) y el
  combo vacío → `IllegalArgumentException`.
- **Depende de**: 2.3.
- **Done**: `mvn -q -f backend/pom.xml test -Dtest=ProjectsUpdateSqlTest`
  verde; ninguna otra relación con SQL (sin BD, G4).

#### 4.3 [x] · `mvn test` completo y COMMIT #4

- **Ficheros**: ninguno nuevo (verificación).
- **Acción**: `mvn -q -f backend/pom.xml test` → TODAS las suites verdes
  (las nuevas de C3 Y las heredadas de C2: `Argon2PasswordTest`,
  `TokenServiceTest`, `PanelAuthMvcTest`, `CorsConfigTest`,
  `LoginCredentialsTest` — no-regresión [L] obligatoria).
- **Depende de**: 4.1, 4.2.
- **Commit #4**: `test(projects): tests ligeros de C3 — matriz PATCH G2, fail-fast de status, 409 UNIQUE y serialization del DTO` + trailer §3.
- **Done**: `mvn -q -f backend/pom.xml test` retorno 0; el commit contiene
  exactamente los 2 ficheros de test nuevos; `src/test/java/com/projectlog/projects`
  contiene SOLO esas 3 clases (MvcTest + SerializationTest de F0 + SqlTest
  — sin tests de seed ni Testcontainers ni H2, G4); la suite de C2 sigue
  verde.

---

### Fase F4 — Maquetas 04/05 servibles (copias, G5)

#### 5.1 [x] · Crear `frontend/public/mockups/04-proyectos.html` (copia adaptada)

- **Ficheros**: `frontend/public/mockups/04-proyectos.html` (NUEVO — copia
  del original `docs/mockups/04-proyectos.html`, que queda intacto).
- **Cubre**: R3.17; CD-3.12; G5; E3.22.
- **Contenido**: copia con estilos originales intactos (sin estilos nuevos)
  + wiring mínimo inline (sin `.js` compartido, fichero autocontenido):
  1. **Login inline**: form que hace `fetch POST /api/v1/panel/auth/login`
     (credenciales dev del `.env`, introducidas a mano) y guarda el JWT en
     `sessionStorage["mockups_token"]`; ante 401 borra token y vuelve a
     mostrar el form (re-login; NUNCA panel vacío con error JS).
  2. **Lista real**: `fetch GET /api/v1/panel/projects` (rutas ABSOLUTAS,
     mismo origen, sin tocar `/docs/**`) → tarjetas `name`, `description`,
     etiqueta `Activo|Archivado`, línea `api_key_prefix … has_key`
     (`null` → "sin key · has_key: false"), `created_at` con `Z`, botón
     Archivar/Desarchivar por fila → `PATCH {status}` + `location.reload()`.
  3. **Filtro**: toggle Todos/Activos/Archivados → query con o sin
     `?status=`; estado vacío → "No hay proyectos todavía" (AppFlow §4).
  4. **Helper fetch** de ~30 líneas: JSON + `Authorization: Bearer` + ante
     cualquier 401 limpia el token y re-loguea (solo mira
     `response.status`, **nunca parsea el body del 401** — ISO-8859-1 del
     entry point); 400/404/409 se muestran inline.
  **Qué NO hace (cota del Done, CD-3.12)**: sin React/routing SPA (enlaces
  `<a>` relativos 04↔05, nav al resto recortada), sin esqueleto/banner
  AppFlow, sin formularios nuevo/editar («creación vía API — fuera del
  puente»; el POST se prueba con curl del smoke), sin modales, **sin
  bloque `summary`** (el DTO no lo trae — C10), sin gestión de keys (C4),
  sin WebSocket.
- **Depende de**: 3.5 (API real que el fetch va a consumir).
- **Done**: el fichero existe en `frontend/public/mockups/` y NO en
  `docs/mockups/` (diff-0 allí); grep del fichero contiene los literales
  `/api/v1/panel/auth/login` y `/api/v1/panel/projects`; no importa ningún
  `.js` externo ni modifica estilos de la maqueta original.

#### 5.2 [x] · Crear `frontend/public/mockups/05-proyecto-detail.html` (copia adaptada)

- **Ficheros**: `frontend/public/mockups/05-proyecto-detail.html` (NUEVO —
  copia de `docs/mockups/05-proyecto-detail.html`).
- **Cubre**: R3.17; CD-3.12; G5; E3.22.
- **Contenido**: copia con el MISMO login inline + helper fetch de 5.1
  (duplicado inline, fichero autocontenido): lee `?id=` del query,
  `fetch GET /api/v1/panel/projects/{id}`, pinta la ficha (name, status,
  description, api_key_prefix/has_key, created_at/updated_at con `Z`),
  botón Archivar/Desarchivar → `PATCH {status}`, enlace volver a `04`.
  Ante 404/409 muestra el error inline (cuerpo estándar §3.1). Mismas
  cotas del Done de CD-3.12 (sin summary, sin keys, sin formularios).
- **Depende de**: 5.1 (patrón compartido).
- **Done**: el fichero existe; grep contiene `GET /api/v1/panel/projects/{id}`
  (o su construcción desde `?id=`) y el PATCH de `status`; `docs/mockups/`
  sigue diff-0.

#### 5.3 [x] · Verificar build Vite (dist incluye mockups) y COMMIT #5

- **Ficheros**: ninguno nuevo (verificación).
- **Acción**: `cd frontend && npm ci && npm run build` → retorno 0; verificar
  `ls frontend/dist/mockups/` = los 2 HTML (Vite copia `public/**` verbatim
  a `dist/**`; `package.json`/`vite.config.ts` SIN tocar).
- **Depende de**: 5.1, 5.2.
- **Commit #5**: `feat(frontend): maquetas 04/05 servibles en /mockups con login y fetch a la API real (copia, no originales)` + trailer §3.
- **Done**: build Vite limpio y verificado ANTES de declarar las maquetas
  servibles; `dist/mockups/04-proyectos.html` y `…/05-proyecto-detail.html`
  existen; el commit contiene SOLO los 2 ficheros de `frontend/public/mockups/`;
  cero ficheros de `docs/` modificados.

---

### Fase F5/F6 — Smoke E2E contra la pila podman (sin commit nuevo salvo fix)

> Regla de fase: si un escenario sale rojo, volver a apply con el fallo
> concreto (qué task, qué literal se desvió), corregir con commit de fix
> mínimo y RE-ejecutar desde el escenario afectado hacia adelante. Órdenes
> con PODMAN: `podman compose up -d --build` / `podman compose exec …`.
> La verificación de carrera del 409 en [L] NO simula hilos — el garante
> último es la constraint `projects_name_unique` de la BD; en [L] se fija
> la vía de traducción (mock lanza `DuplicateKeyException` → 409, E3.26).

#### 6.1 [x] · Crear `scripts/qa_c3_smoke.sh` (helpers propios, patrón C2)

- **Ficheros**: `scripts/qa_c3_smoke.sh` (+x, fuera de toda imagen).
- **Cubre**: R3.18, R3.19; CD-3.14; G4; E3.1–E3.24.
- **Contenido**: réplica del esqueleto de `qa_c2_smoke.sh` con helpers
  **PROPIOS replicados (no `source`-ados y NO invoca `qa_c2_smoke.sh`** —
  su `Q13` hace `down -v` y destruiría el volumen con datos del smoke):
  `COMPOSE_BIN` con timeout, `esperar_healthy` sondeando `GET /me` → 401
  `invalid_token` real, `req`, `campo_json`/`claves_json` con decodificación
  tolerante utf-8/replace, `psql_q`, `epoch_a_iso`/`iso_a_epoch`,
  `cabecera`. Sin secretos ni JWT a stdout (higiene S16), `trap` de
  restauración, exit codes 0/1/2.
  - **Precondiciones**: `.env` F1 cargada línea a línea SIN exportar
    (anti-veneno, WARNING 2 de C2); credenciales admin + `POSTGRES_*`
    presentes; healthy por `/me`→401. Recomposición única inicial
    `podman compose up -d --build` (recrea contenedores — NUNCA `restart`
    de un contenedor sin rebuild) con timeout acotado.
  - **Idempotencia**: `TS="$(date +%Y%m%d%H%M%S)"` → proyectos
    `smoke-c3-${TS}-a|-b|-c|-dup|-largo-…`; el smoke es repetible SIN
    limpiar volumen y SIN depender de borrado (DELETE es C10); las
    verificaciones de lista son de **membresía**, no exclusividad (tolera
    sobras de corridas previas; el filtro E3.8 verifica a los `-a`/`-b` del
    TS propio en su status esperado).
  - **Casos** (un veredicto `E3.n: PASS|FAIL|BLOQUEADO` por ID):
    E3.1–E3.6 creación (201 con/sin description, claves exactas
    `id,api_key_prefix,created_at,description,has_key,name,status,updated_at`
    sin `summary`, `status: "ACTIVE"`, `has_key: false`, regex `Z`; 409
    inmediato; 400 `field_required`/`field_too_long` name 201 y description
    2001) → E3.7–E3.11 listado/ficha (array plano sin `{items}`, filtro
    `?status=` ambos valores, 400 en `active|ARCHIVE|`, ficha 200/404 con
    999999) → E3.12–E3.20 edición/archivado (PATCH name/description/
    `{"description": null}` limpiando, ARCHIVED→ re-GET lista+ficha con
    dato intacto, ACTIVE de vuelta, `{}` → 200 con `updated_at` INTACTO,
    nulls → 400, status inválido ×3 → 400 nunca 500, choque de name → 409
    con B intacto, 999999 → 404, E3.20 con `sleep 2` antes de cada PATCH
    comparativo) → E3.21 (4 rutas sin `Authorization` → 401, sin parse
    estricto del body) → E3.22 (maquetas: `GET :8080/mockups/04|05…` → 200
    HTML con marcas `auth/login` y `panel/projects`; `GET :8080/docs/mockups/…`
    NO devuelve el HTML del repo) → E3.23 (login 200 con forma JSON; 401
    `invalid_credentials` ×3; 401 `invalid_token` en `/me` y en
    `GET /panel/projects`; preflight `OPTIONS` PATCH → **200 o 204** con
    ACAO y PATCH en Allow-Methods) → E3.24 (`psql_q 'SELECT version FROM
    flyway_schema_history'` → solo `1`; `git diff --exit-code` sobre la
    lista intocable §0.3).
  - **Orden interno**: crear → parchear (archivar incluido) → listar, para
    que E3.8 vea el `-a` ya ARCHIVED (CD-3.14).
- **Depende de**: COMMIT #5 (imagen frontend con mockups + backend con C3).
- **Done**: `bash -n scripts/qa_c3_smoke.sh` limpio; el script NO menciona
  `qa_c2_smoke` ni `down -v`; grep confirma `sleep 2` presente en las
  comparaciones de `updated_at` y ausencia de parse estricto del body 401
  (decodificación tolerante); ejecutable (`ls -l` muestra la x).

#### 6.2 [x] · Ejecución completa del smoke (E3.1–E3.22)

- **Acción**: `bash scripts/qa_c3_smoke.sh` contra la pila completa (curl
  por `:8080` con JWT real de `POST /api/v1/panel/auth/login` con las
  credenciales del seed — el admin del `.env`). Si un caso sale FAIL, no se
  continúa al ciego: corregir en apply y re-ejecutar desde el caso afectado.
- **Cubre**: E3.1–E3.22 [S] (Done literal del 07: "CRUD+archivado con smoke
  de estados (409 y 400 incluidos); maquetas 04/05 servibles").
- **Depende de**: 6.1.
- **Done**: veredicto PASS para E3.1–E3.22; los claves exactas sin
  `summary` verificados en E3.1/E3.10; E3.15 muestra `updated_at` idéntico
  (igualdad ESTRICTA post-`sleep`); E3.17 nunca 500; E3.22 servido por el
  nginx con fetch inline y `/docs/mockups` no servido. La operación de
  ratón (login + clic Archivar en navegador) queda como verificación manual
  del qa-run (CD-3.14), documentada en qa.

#### 6.3 [x] · No-regresiones E3.23/E3.24 y cierre del corte

- **Acción**:
  - (E3.23 [S]) veredictos del smoke sobre cadenas auth de C2: login OK,
    `invalid_credentials` ×3 con literal idéntico, `invalid_token` en `/me`
    y en `GET /api/v1/panel/projects` **con controller y sin JWT** (S19),
    preflight CORS `20[46]` sin tocar `CorsConfig`.
  - (E3.24 [S]) veredictos: `flyway_schema_history` = solo versión 1;
    `git diff --exit-code` sobre TODA la lista intocable de §0.3
    (SecurityConfig, CorsConfig, entry point, `JwtAuthFilter`, `TokenService`,
    `AdminSeeder`, validador + `spring.factories`, `application*.yml`,
    `V1__init.sql`, `pom.xml`, `compose.yaml`, `nginx.conf`, Dockerfiles,
    `vite.config.ts`, `docs/mockups/**`, `qa_c2_smoke.sh`,
    `ws_echo_test.py`) y cero migraciones nuevas; **Echo WS intacto por
    diff-0** (el smoke de C7 lo ejercitará — C3 no lo corre).
  - (echo WS, opcional) `python3 scripts/ws_echo_test.py --sin-inactividad`
    si se quiere la evidencia viva del Echo tras C3 (intocable, R3.20).
  - Cierre: `mvn -q -f backend/pom.xml test` verde completo (C2 + C3);
    `git status` limpio; árbol = 8 ficheros backend nuevos + 2 mockups +
    1 script; pila `Up (healthy)` por `podman compose ps`.
- **Depende de**: 6.2.
- **Sin commit**: F5/F6 no genera commit nuevo salvo fixes
  (`fix(<área>): <fallo de E3.n>` + trailer §3).
- **Done**: E3.23 y E3.24 verdes con evidencia (salida del script + diff
  vacío + psql); diff-0 confirmado; ningún secreto a stdout en la corrida;
  NINGÚN fichero fuera del árbol del design.

---

## 3. Commits sugeridos (atómicos, por fase)

| # | Tras task | Mensaje + trailer |
|---|---|---|
| 1 | 1.4 | `feat(projects): DTOs del panel de proyectos — ProjectDto §3.3 sin summary y requests de POST/PATCH` |
| 2 | 2.4 | `feat(projects): ProjectsRepository — SELECT/INSERT/UPDATE sobre projects con RETURNING y updated_at explícito` |
| 3 | 3.5 | `feat(projects): CRUD del panel — GET/POST/PATCH con validación manual, 409 por UNIQUE y archivado por status` |
| 4 | 4.3 | `test(projects): tests ligeros de C3 — matriz PATCH G2, fail-fast de status, 409 UNIQUE y serialization del DTO` |
| 5 | 5.3 | `feat(frontend): maquetas 04/05 servibles en /mockups con login y fetch a la API real (copia, no originales)` |
| — | 6.x | F5/F6 no genera commit; fixes → `fix(<área>): <fallo de E3.n>` |

Trailer en TODOS:
`Co-Authored-By: Claude Code <noreply@anthropic.com>`.

## 4. Advertencias para apply (del design §2/§6, memorizar antes de escribir)

1. **`RETURNING` en una sola ida (CD-3.4)**: el INSERT y el UPDATE devuelven
   la fila con `RETURNING {COLS}` — NUNCA INSERT/UPDATE + `SELECT` posterior
   (dos sentencias podrían ver estados distintos ante concurrencia de nombre,
   y sobra una ida y vuelta). Sin `GeneratedKeyHolder`.
2. **NO check-then-insert (R-1, crítico)**: la carrera del
   `name_duplicate` NO se resuelve con un SELECT previo (descartado); INSERT/
   UPDATE directo y el catch de `DuplicateKeyException` traducido por
   Spring JDBC desde el 23505 de `projects_name_unique` — el **garante
   último es la constraint de la BD**, no el código. Su verificación en
   [L] fija la VÍA (mock lanza la excepción → 409, E3.26), no una carrera
   simulada.
3. **Un solo `@ExceptionHandler(DuplicateKeyException)` (CD-3.4)**: único
   punto que sirve POST y PATCH. NO mapear `DataIntegrityViolationException`
   genérico (enmascararía otras violaciones como 409; el genérico cae a 500,
   correcto en C3). El `HttpMessageNotReadableException` → 400
   `field_required` SIN `field` (CD-3.9).
4. **Formatter de C2 REUTILIZADO POR REFERENCIA (R3.8)**: importar
   `LoginController.EXPIRES_AT_FORMATTER` en el RowMapper — jamás copiar un
   segundo patrón `yyyy-MM-dd'T'HH:mm:ss'Z'` en `com.projectlog.projects`
   (cero segunda convención de formateo).
5. **`updated_at` y resolución de segundos (R3.6, riesgo del design)**: el
   formatter trunca a segundos — TODAS las comparaciones de refresco
   (E3.12–E3.15 y E3.20) llevan `sleep 2` antes del PATCH comparativo y la
   aserción es `<` estricta sobre epoch (E3.15 usa esa pausa para la
   igualdad ESTRICTA de `{}`: intacto fiable en ambas direcciones). `now()`
   es tiempo de transacción: dos PATCH del mismo segundo empatan — sin
   `sleep` el smoke sería falso-positivo/negativo.
6. **Semántica G2 EXACTA en el PATCH (CD-3.6/CD-3.7)**:
   validación ("primer fallo gana": nulls → enum → longitudes) **antes**
   de cualquier BD (si el status inválido escapara, `projects_status_check`
   lo convertiría en 500 prohibido — R-2/regla 10) → `camposPresentes`
   vacío (**`{}`**) → 200 SIN llamar a `update` (`updated_at` intacto) →
   `findById` (404 sin UPDATE, E3.19) → `update` (409 vía handler, E3.18) →
   200. `{"description": null}` LIMPIA (columna NULL, sin `COALESCE`);
   `name`/`status` null → 400 `field_required` (no existe valor legal
   nulo).
7. **Mockup fetch sin parsear el body del 401 (riesgo del design)**: el
   helper ante cualquier 401 solo mira `response.status`, limpia
   `sessionStorage["mockups_token"]` y re-loguea — el body del entry point
   sale ISO-8859-1 (heredado, fix en C8/C9). Resto de errores (400/404/409)
   se muestran inline sobre el panel.
8. **Build Vite verificado antes de decir "servible" (5.3)**: la maqueta se
   considera servible SOLO tras `npm run build` limpio con
   `dist/mockups/*` existente (pipeline `public/`→`dist/` verbatim) y tras
   el 200 HTML del smoke E3.22. Sin tocar `vite.config.ts` ni
   `package.json`.
9. **El smoke NO invoca `qa_c2_smoke.sh` (CD-3.14, hallazgo C2)**: sus
   helpers se REPLICAN en el script nuevo (no se `source`-a) — el `Q13` de
   C2 hace `down -v` y destruiría el volumen con los datos del smoke. Las
   cadena-auth de C2 se re-verifican como subset PROPIO (E3.23).
10. **`mvn test` completo verde al cierre (4.3/6.3)**: no basta con
    `-Dtest=…` por clase — la corrida final ejecuta la suite COMPLETA (C2 +
    C3) para cazar interferencias entre slices. `application-test.yml` y el
    validador de C1: diff-0.
11. **Podman-compose `recreate`, nunca `restart` (advertencia §4.11 de
    C2)**: tras rebuild del backend/frontend (`up -d --build`) el contenedor
    se RECREA; un `restart` no introduce la imagen nueva. Anti-veneno de
    env: `.env` cargada línea a línea SIN exportar; salud por
    `podman compose ps`/`inspect` (never curl `/actuator` del host como
    proxy de salud).
12. **No adelantar C4–C10 (§7 del design)**: sin API keys (C4), sin regla
    403 `project_archived` de ingesta (C5 — en C3 solo existe el DATO
    `status`), sin keyset/paginación (C6), sin WS real (C7), sin panel
    React (C8), sin `DELETE`+`X-Confirm-Name` ni `summary` (C10).
    `api_key_hash`/`api_key_prefix`/`key_created_at` quedan INTACTOS por
    C3 (NINGUNA rama del UPDATE los menciona; los INSERT no los tocan).
    Sin dependencias nuevas en el pom ni migraciones nuevas.

## 5. Criterios de salida de fase

- F3 solo está "Done" con `mvn -q -f backend/pom.xml test` verde COMPLETO
  (las 3 clases de C3 —incluida `ProjectDtoSerializationTest`— y las 5 de
  C2 heredadas verdes).
- La fase de verificación (F5/F6) SOLO puede declararse "Done" si:
  - pasan TODOS los escenarios [S] E3.1–E3.24 del mapa del spec §6, cada
    uno con el veredicto de su ID y la evidencia que lista su sección
    (HTTP status con cuerpo/literal exacto, claves §3.3 sin `summary`,
    `updated_at` comparado con `sleep 2`, salida de `psql_q`,
    `git diff` vacío sobre intocables);
  - `mvn -q -f backend/pom.xml test` sigue verde tras los smokes;
  - los intocables de §0.3 están **diff-0 verificados** (E3.24) y
    `db/migration/` no tiene ficheros nuevos;
  - el árbol final es EXACTAMENTE el del design §1.1: 8 ficheros
    `src/main|test` de `com.projectlog.projects` + 2 copias en
    `frontend/public/mockups/` + `scripts/qa_c3_smoke.sh` — NADA más;
  - ningún secreto ni JWT versionado o a stdout (higiene S16 heredada).
- Cualquier escenario rojo = volver a apply con el fallo concreto (qué task
  lo produce, qué literal se desvió), corregir con commit de fix mínimo y
  re-ejecutar desde el escenario afectado hasta el final. Recordar al
  reportar un rojo de 409: la verificación de la carrera es [L] (vía de
  traducción de la excepción, E3.26) y el garante estructural es la
  constraint `projects_name_unique` de la BD (E3.3/E3.18 con ida-vuelta
  inmediata en smoke) — la concurrencia real NO es exigible en smoke.
- Estado final esperado al cerrar: repo con 5 commits de fase (+ fixes si
  los hubo), pila podman green, los 4 endpoints de C3 vivos tras el proxy,
  maquetas servibles en `:8080/mockups/`, cero líneas tocadas de C1/C2, y
  el Done literal del 07 cubierto: "CRUD+archivado con smoke de estados
  (409 y 400 incluidos); maquetas 04/05 servibles contra esta API".