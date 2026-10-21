---
change: "C3"
artifact: explore
last_updated: 2026-10-21
status: done
---

# Explore — C3 · Proyectos: CRUD + archivado

> Fase sdd-explore. Fuentes: Schema §3.1–§3.3/§4/§6.1 (`05_BACKEND_SCHEMA.md`,
> valido v1.0), AppFlow flujos 2 y 7 + §4/§5 (`03_APPFLOW.md`, valido v1.0),
> TRD §2.3/§2.4/§2.5/§9 (`02_TRD.md`, valido v1.1), `07_SLICING_PLAN.md`
> (C3, estado pendiente), `06_IMPLEMENTATION_PLAN.md` (Fase 1) y el cierre
> heredado **solo** `.atl/sdd/C2/verify-report.md` (PASS-with-notes). El código
> real del repo (C1+C2, HEAD `829cc70`) se inspeccionó SIN modificar nada:
> `backend/src/main/**`, `pom.xml`, `compose.yaml`, `frontend/nginx.conf`,
> `src/main/resources/db/migration/V1__init.sql`, mockups `04/05`, PRD §3
> (stories 2, 13 y 14: crear/editar proyectos; archivar/desarchivar; los
> archivados conservan sus logs consultables).

## 1. Decisiones cerradas (con ancla exacta)

### 1.1 Rutas EXACTAS (NOTA de rutas del corte, resuelta)

Los endpoints de proyectos viven **dentro de la superficie protegida del
panel**, prefijo literal **`/api/v1/panel/`** — las filas del Schema §3.2 lo
dicen explícitamente:

| Método | Ruta literal (Schema §3.2) | Auth | Request → Response |
|---|---|---|---|
| GET | `/api/v1/panel/projects` | JWT | query `status?`, `include=summary?` → 200 `[ProjectDto]` · 401 |
| POST | `/api/v1/panel/projects` | JWT | `{name, description?}` → 201 `ProjectDto` · 400 · 409 |
| GET | `/api/v1/panel/projects/{id}` | JWT | → 200 `ProjectDto` · 401 · 404 |
| PATCH | `/api/v1/panel/projects/{id}` | JWT | `{name?, description?, status?}` → 200 `ProjectDto` · 400 · 401 · 404 · 409 |

- Coherente con C2: `SecurityConfig.panelChain` protege `/api/v1/panel/**`
  (cadena 2, `@Order(2)`, `anyRequest().authenticated()`): los 4 endpoints de
  C3 **nacen protegidos sin tocar ninguna cadena** (S19 del verify C2:
  "el panel nace protegido"; QA/Q22 ya vio las rutas futuras responder 401).
- El proxy nginx (C1, `frontend/nginx.conf`) enruta `/api/v1/**` completo al
  backend: ninguna modificación de infra para C3.
- TRD §2.5 y §9 confirman: base `/api/v1/panel`, `Authorization: Bearer`,
  y el proxy enruta solo `/api/v1/**` + `/ws/logs`.
- **DELETE `/projects/{id}` NO se implementa en C3** (es P1/C10: slicing
  C10 lo lista; Schema §3.2 lo marca P1 con `X-Confirm-Name`).

### 1.2 ProjectDto — literales exactos (Schema §3.3)

Salida de GET/POST/PATCH projects y de la lista (orden y nombres EXACTOS):

```json
{
  "id": 7,
  "name": "payments-service",
  "description": "Servicio de pagos",
  "status": "ACTIVE",
  "created_at": "2026-10-16T20:00:00Z",
  "updated_at": "2026-10-16T21:00:00Z",
  "api_key_prefix": "plk_Ab3xQw",
  "has_key": true,
  "summary": { }   // AUSENTE salvo GET /projects con include=summary (C10)
}
```

- `description` puede ser `null`; `api_key_prefix` = `null` y `has_key: false`
  si la key está revocada o **aún no generada** (todo proyecto recién creado
  en C3 sale `has_key: false`, `api_key_prefix: null` — Schema §6.3 "sin otras
  semillas", AppFlow flujo 2 paso 3: "con `api_key_prefix` vacío y
  `has_key: false`").
- `summary` **ausente** del DTO en C3 (es P1/C10: SummaryDto §3.3, flujo 9).
- `has_key` es derivado (`api_key_hash IS NOT NULL`), no columna (Schema §2,
  invariantes de `projects`).

### 1.3 Entradas y validaciones con longitud anclada (Schema §3.3)

- **Creación**: `{name: string (1..200), description?: string (≤2000)}`.
- **Edición (PATCH, todos opcionales)**: `{name?, description?, status?
  ("ACTIVE"|"ARCHIVED")}` — "archivar/desarchivar va **vía PATCH con
  `status`**, nunca por DELETE (AppFlow flujo 7)" (Schema §3.3, literal).
- Longitudes: `name` máx. **200** y `description` máx. **2000** son
  "concreción" del Schema §2 (tabla `projects`) y aparecen en §3.3.
- AppFlow flujo 2 paso 3: 400 = "nombre vacío o demasiado largo" → errores por
  campo; UI conserva datos.
- **Cuerpo de error estándar** (Schema §3.1 / TRD §2.6):
  `{"code", "message", "field"?}` — errores siempre explícitos, nunca
  silenciosos (métrica 2 del PRD).
- **409**: literales `name_duplicate` (Schema §3.1 códigos;
  §4 regla 2: "violación → 409 `name_duplicate`"; AppFlow flujo 2/§5: mensaje
  en línea "Ya existe un proyecto con ese nombre", sin perder datos).
- **404**: el panel devuelve JSON estándar; el literal `{"code": "not_found"}`
  está anclado en §3.3 para `GET /logs/{id}` y, por la convención común de §3.1
  ("errores siempre explícitos"), `not_found` es el literal panel-wide para
  `GET/PATCH /projects/{id}` inexistente (AppFlow §5 fila 404: "No existe
  (quizás fue eliminado)" + Volver a la lista).
- Ojo con `status` en PATCH: valor fuera de enum → **400 antes de tocar la
  BBDD**. El patrón anclado de "fuera de enum → 400" es `filter_invalid_value`
  en los filtros de logs (§3.3) y `level_invalid` en ingesta (§3.4); para el
  `status` de proyecto **no hay literal propio anclado** → punto abierto P-1.

### 1.4 Columnas de `projects` en el V1__init.sql (aplicado en C1; idéntico a Schema §6.1)

`backend/src/main/resources/db/migration/V1__init.sql` (verificado en disco):

```sql
CREATE TABLE projects (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            TEXT        NOT NULL,
    description     TEXT,                                 -- opcional
    status          TEXT        NOT NULL DEFAULT 'ACTIVE',
    api_key_hash    BYTEA,                                -- NULL = revocada/sin key
    api_key_prefix  TEXT,
    key_created_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT projects_name_unique UNIQUE (name),
    CONSTRAINT projects_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);
```

- `projects_name_unique` (UNIQUE en BD) es el **garante último** del 409
  (Schema §4 regla 2) y `projects_status_check` respalda la validación del
  borde (regla 10: estados válidos forzados por CHECK **y** validación en el
  borde, 400 antes de insertar).
- Sin trigger de `updated_at` en V1: **el UPDATE debe fijarlo el SQL explícito**
  (`updated_at = now()`), porque el DEFAULT solo aplica al INSERT. La
  verificación "updated_at refrescado" del AppFlow flujo 2 (paso 3 de editar)
  depende de esto.
- Índice parcial `projects_api_key_hash_idx` (UNIQUE WHERE NOT NULL) no
  interfiere: los INSERT de C3 van con `api_key_hash` NULL (múltiples NULLs
  permitidos en índice parcial).

### 1.5 Paginación: NO — GET /projects es lista simple

- Schema §3.2: la salida de GET es **`[ProjectDto]`**, un array plano, sin
  `items/next_cursor` (contraste con `GET /logs`, que sí devuelve
  `{items, next_cursor}`, keyset §3.3 — el keyset es exclusivo de LOGS y su
  impl C6).
- TRD §2.4: "Para la lista de proyectos (pocas filas), offset es irrelevante";
  la paginación keyset `(received_at DESC, id DESC)` es de logs, no de
  proyectos.
- Parámetro `?status=`: query de filtrado (activo/archivado); AppFlow:
  `/proyectos` muestra "activos y archivados con su etiqueta" → el frontend
  pedirá las dos vistas; sin `status` = todos (la semántica "sin query = lista
  completa" es la lectura natural de `status?`; el filtro es opcional).

### 1.6 AppFlow flujos 2 y 7 — pasos y estados (contrato de comportamiento)

**Flujo 2 — Crear y editar proyecto (PRD P0-2, story 2):**

- Crear: `/proyectos` → **Nuevo proyecto** → `/proyectos/nuevo` (form vacío) →
  `{name, description?}` → `POST /projects` → 201 → **redirige a
  `/proyectos/{id}` con aviso "Proyecto creado; genera su API key para que las
  apps puedan ingestar"** (la key se genera aparte, C4/flujo 3; nunca en la
  creación). 409 → error en línea bajo el campo "Ya existe un proyecto con ese
  nombre", datos conservados; 400 → errores por campo.
- Editar: ficha → **Editar** → `/proyectos/:id/editar` pre-cargado con
  name/description de `GET /projects/{id}` → PATCH con **solo los campos
  modificados** → 200 → volver a la ficha con "Cambios guardados" y
  `updated_at` refrescado; 409/400 → errores en el formulario; 404 → mensaje
  y retorno a `/proyectos`.
- Estados §4: `/proyectos` esqueleto / "No hay proyectos todavía" + CTA Nuevo
  proyecto (vacío legítimo) / banner+Reintentar (error); `/proyectos/nuevo`
  botón "Creando…" deshabilitado (anti doble submit); 409 in-line; 400 por
  campo; formulario conserva datos.

**Flujo 7 — Archivar / desarchivar (PRD P0-7, stories 13 y 14):**

1. **Archivar** desde ficha o lista → confirmación: "Dejará de aceptar
   ingesta; sus logs se conservan consultables; NO se borra nada".
2. Confirmado → `PATCH /projects/{id}` con **`{status: "ARCHIVED"}`** (nunca
   DELETE).
3. 200 `ProjectDto` con `status: ARCHIVED` → ficha y listado muestran etiqueta
   "Archivado"; el botón pasa a **Desarchivar**.
4. Efectos derivados: ingesta futura → 403 `project_archived` (regla de C5);
   streaming deja de recibir (sin ingesta no hay flujo); **logs históricos
   siguen filtrables y consultables** (flujo 4/5 intactos).
5. **Desarchivar** → confirmación → `PATCH` con `{status: "ACTIVE"}` → 200 →
   si `has_key: true`, la misma key vuelve a aceptarse (archivar nunca toca la
   key ni los logs — Schema §4 regla 11).
6. 409 en PATCH (si se combinara con cambio de nombre) → manejo §5.

Anclas de integridad (Schema §4): regla 3 ("archivar **nunca** borra nada:
solo cambia `status`"), regla 10 (enums cerrados CHECK + borde), regla 12
("la ingesta solo sobre proyectos ACTIVE... el archivado afecta al flujo, no
a la consulta"), regla 11 (archivar no toca la key; al desarchivar la misma
key vuelve a aceptarse), §4.2 ("archivar es un cambio de status del proyecto,
reversible, nunca destruye datos").

### 1.7 "Done cuando" — desempate exacto (del 06 vía 07_SLICING_PLAN C3)

> "CRUD+archivado con smoke de estados (409 y 400 incluidos); maquetas 04/05
> servibles contra esta API (los paneles aún no existen)".

- **Smoke de estados** significa: en la pila real (patrón C2: curl contra
  `:8080` por el proxy con JWT válido), probar al menos: 201
  happy path (con y sin description), 409 `name_duplicate` en POST, 200 GET
  lista y ficha, 404 ficha inexistente, 400 validación (name vacío y/o >200),
  PATCH de name/description, PATCH archivado → `status: ARCHIVED`, PATCH
  desarchivado → `ACTIVE`, 401 sin JWT en las 4 rutas (ya cubierto por la
  cadena 2, se re-verifica) y `updated_at` que se refuerza. El smoke de C5
  (`403 project_archived`) NO entra: aquí solo existe el Dato `status`
  (nota de cruce del corte).
- **"Maquetas 04/05 servibles contra esta API"** — interpretación propuesta
  (la decisión la aprueba el gate; ver punto abierto P-4): las maquetas de
  `/docs/mockups/` son HTML estático (Tailwind CDN, datos de ejemplo incrustados,
  sin `fetch` — verificado), y `/docs` está congelado. Servibles = copias
  adaptadas en el frontend, servidas por el nginx de C1 en el puerto único
  8080, con una capa mínima de fetch contra la API REAL: login (captura el
  token de `POST /auth/login`, quizá un prompt/form para credenciales dev),
  `GET /projects` pinta la lista de 04, y 05 pinta ficha desde
  `GET /projects/{id}` con Archivar/Desarchivar → PATCH. Se prueba con el
  mismo smoke: abrir `http://<host>:8080/mockups/04-proyectos.html` y
  operar CRUD real. **No** es el frontend React final (C8); es la
  servibilidad de las maquetas tal cual pide el Done.

## 2. Puntos de diseño abiertos (para propose / design)

### P-0 — Capa de servicio vs repositorio (y JPA vs JdbcTemplate)

- Convenciones del repo tras C1/C2 (código real): **package-by-feature** (los
  paquetes `projects`, `keys`, `ingest`, `query`, `streaming` ya existen con
  `package-info.java` como marcadores C1; TRD §2.1); el pom NO trae
  `spring-boot-starter-jpa` — trae `spring-boot-starter-jdbc` y C2 usó
  **`JdbcTemplate` directo** (`LoginController` consulta SQL inline;
  `application.yml` solo tiene el guard `jpa.hibernate.ddl-auto: none` de C1 —
  `spring-boot-starter-jpa no está en el pom`).
- **Propuesta**: mantener `JdbcTemplate` (coherencia con C2, cero deps nuevas,
  el esquema es de Flyway no de Hibernate); para C3, con 4 endpoints y una
  tabla, un `ProjectsRepository` (clase en `com.projectlog.projects`, SQL
  explícito) + un `ProjectsController` que orquesta validación → repo →
  DTO; **sin capa de servicio intermedia** (C2 puso la lógica en el
  controller; con 4 operaciones el extra no paga — si el gate prefiere
  servicio, es trivial). C4/C6 necesitarán el mismo repo (GET /projects/{id}
  alimenta regeneración y el modal de C4), lo que apoya centralizar las
  consultas en `ProjectsRepository` aunque el controlador quede delgado.
- Paginación de proyectos = lista simple (§1.5), así que el repo expone
  `findAll(status?)` / `findById(id)` / `insert(...)` / `update(...)`; el
  keyset queda íntegro para C6 (nota: Schema §3.3 lo exige para LOGS).
- **Transacciones**: cada operación de C3 es mono-sentencia (INSERT o UPDATE
  de una fila) ⇒ el autocommit de JdbcTemplate basta; `@Transactional` en
  mutating del repo como documentación/défense (Spring JDBC con un solo
  statement ya es atómico). Nada multi-sentencia entra hasta C5
  (batch all-or-nothing) y C10 (CASCADE con DELETE).

### P-1 — Validación de entrada: Bean Validation vs manual (con literales EXACTOS)

- El pom NO trae `spring-boot-starter-validation` (hibernate-validator); C2
  validó manualmente en el controller con constantes de literal
  (`CODE_INVALID_CREDENTIALS`) y cuerpo via `ObjectMapper`.
- Literales anclados para C3: 409 = `name_duplicate` (§3.1/§4-2). Para los
  400 de campo NO hay literal de proyecto en el Schema — el patrón existente
  del Schema usa `field_too_long` (ingesta, §4.1: "400 `field_too_long`,
  `field`"). **Propuesta**: reutilizar ese patrón — 400
  `{"code": "field_too_long", "field": "name"|"description"}` para longitudes
  y `{"code": "field_required", "field": "name"}` para name ausente/vacío
  (AppFlow flujo 2 solo exige "400 validación... errores por campo" — el
  `code` exacto es hueco de contrato que el gate debe fijar para no
  inventarlo en apply). Manual en controller, sin añadir starter-validation
  (una dependencia nueva para esto no compensa; mismo criterio C2).

### P-2 — Semántica PATCH (parcial: ausente vs null)

- Schema §3.3: PATCH "todos opcionales" `{name?, description?, status?}` —
  lo natural (y lo que AppFlow flujo 2 pide: "solo los campos modificados") es
  **absente = sin cambio**. El hueco: ¿`description: null` explícito BORRA la
  descripción o se ignora? El Schema no lo dice (description es el único
  campo nullable del DTO). **Propuesta**: JSON presente con `null` en
  `description` → limpia la descripción (operación útil, barata); `name: null`
  o `status: null` → 400 (no hay valor legal nulo para esos dos). PATCH con
  `status` coexistiendo con `name` modificado en la misma llamada: permitido
  por el Schema (los 3 campos en la misma entrada) — flujo 7 paso 6 contempla
  ese 409. PATCH vacío `{}` → 200 sin cambios (o 400 — hueco menor; propuesta:
  200 idempotente, devuelve el DTO actual). Decisión de gate (P-1/P-2 juntos
  si conviene).

### P-3 — status enum y query `?status=`

- `status` viaja como string `"ACTIVE"|"ARCHIVED"` (mayúsculas, literal del
  CHECK y del DTO). Validación en el borde (400 antes de UPDATE; regla 10 del
  Schema: borde + CHECK). Para el literal del 400 sobre un status inválido no
  hay ancla (punto abierto P-1 general).
- `GET /projects?status=` con valor fuera de enum: el patrón del Schema para
  filtros inválidos es 400 (`filter_invalid_value`, §3.3 para logs) —
  **propuesta**: 400 aquí también (fail-fast, misma filosofía), en vez de
  ignorar el filtro en silencio (que sería un "error silencioso", anti-patrón
  de §3.1). Hueco de ancla que el gate debe aprobar.

### P-4 — Done de maquetas: cómo servibles (decisión del gate)

Diseñada en §1.7: copias adaptadas de `04-proyectos.html` y `05-proyecto-detail.html`
en `frontend/public/mockups/` (Vite copia `public/` a `dist/` en el build —
verificado que C1 ya usa ese pipeline), servidas por el nginx C1 en
`:8080/mockups/...` mismo origen (sin CORS), login inline via
`POST /api/v1/panel/auth/login` con credenciales que el smoke teclea, y fetch
mínimo contra GET/POST/PATCH reales. `/docs/mockups/` queda intacto (congelado).
Alternativas: (a) abrir los HTML con file:// contra la API (rompe por CORS en
dev y no es "servible"), (b) servidor aparte (viola el puerto único). La
propuesta (a-bis) es la única coherente con la pila; el gate aprueba la forma
exacta del wiring (¿login en la página? ¿token pegado a mano?).

### P-5 — Estrategia de tests (Testcontainers?) e idempotencia del smoke

- C1 difería Testcontainers a "cuando toque"; C2 cerró con: tests unitarios
  [L] (`@WebMvcTest` + slices, `application-test.yml` dummy para el validador
  de C1 que corre en cada contexto — INTOCABLE) + **smoke en pila real** con
  curl/podman (22 escenarios E2.x).
- Para C3 el repositorio es JdbcTemplate con SQL propio: un test unitario del
  controller no ejercita el SQL. Opciones: (a) Testcontainers/postgres real
  en `mvn test` — coste: imagen + red bajo podman rootless, nuevo mecanismo
  nunca probado en este repo; (b) mantener patrón C2: tests [L] de
  validación/estructura + **smoke pila** que ejerce TODO el CRUD contra
  Postgres real (que es lo que ya pasa con el seed en C2).
- **Propuesta**: (b) — el Done pide "smoke de estados", la pila ya corre
  Postgres 17 real y el SQL de projects es trivial (4 statements); Testcontainers
  se re-evaluaría en C5 (all-or-nothing transaccional) donde sí merece la pena
  un test de integración repetible. Idempotencia del smoke: usar nombres de
  proyecto con sufijo por ejecución (timestamp) para poder repetir sin limpiar
  volumen, o secuencia determinista con borrado lógico previo — patrón a fijar
  en tasks.

### P-6 — Orden de construcción de endpoints

Propuesta (para tasks): 1) repositorio + DTO/serializer de fechas
(`yyyy-MM-dd'T'HH:mm:ss'Z'` UTC, formatter compartido del patrón C2), 2) POST
(con 409 por UNIQUE + catch de dup), 3) GET lista + GET ficha, 4) PATCH
(incluye status/archivado), 5) maquetas 04/05 servibles, 6) smoke completo.
Sin DELETE (C10), sin summary (C10), sin api-key endpoints (C4).

## 3. Riesgos aplicables

- **R-1 · Carrera en el 409 `name_duplicate`**: dos POST simultáneos con el
  mismo name: un check-then-insert (SELECT previo) pierde la carrera. El
  garante es `projects_name_unique` (V1): **propuesta**: INSERT directo y
  capturar `DataIntegrityViolationException`/`DuplicateKeyException` sobre la
  constraint → 409 `name_duplicate` (misma técnica obligatoria en el PATCH con
  cambio de nombre). La UI ya acepta el 409 así (AppFlow flujo 2: error en
  línea, datos conservados).
- **R-2 · status como string vs CHECK**: "ARCHIVE" (mal escrito), minúsculas
  o null en PATCH: sin validación de borde llegaría a la BD y `CHECK` la
  convertiría en 500 (DataIntegrityViolation) en vez del 400 pedido (regla 10
  del Schema: borde ANTES de BD). Test del borde obligatorio.
- **R-3 · Archivado y logs consultables**: en C3 solo cambia `status` (UPDATE
  de una columna, ni toque a `logs` ni a la key — reglas 3 y 11); la regla de
  rechazo (403 `project_archived`) es C5. Peligro de sobre-alcance: no
  implementar nada de ingesta/keys aquí, solo asegurar que GET lista/ficha y
  los (futuros) listados de logs ven al archivado con su histórico intacto
  (nada que bloquear en datos: la FK ni el borrado existen aquí).
- **R-4 · PATCH sobre campos sensibles**: el DTO de entrada del Schema §3.3
  NO incluye `api_key_hash/api_key_prefix/key_created_at`: el UPDATE de C3
  debe construirse **solo** con name/description/status (SQL por columnas,
  nunca UPDATE sin SET acotado ni mapeo automático de entity — inexistente en
  este stack, pero anclar el límite). `updated_at` SIEMPRE en el SET (ver
  §1.4).
- **R-5 · No romper cadenas/CORS de C2** (heredado del verify): C3 añade solo
  clases en el paquete `projects`; ninguna modificación a `SecurityConfig`,
  `CorsConfig`, entry point, validador de C1 ni `V1__init.sql`
  (intocables de C1 + comportamiento contractual de C2). La cadena 2 ya cubre
  `/api/v1/panel/projects/**`: C3 nace 401 sin JWT. `X-Confirm-*` ya está
  configurado en el CORS (reservado C3/P1) — se usa en C10, no aquí.
- **R-6 · Formato de fechas**: el contrato §3.1 es ISO-8601 UTC con sufijo
  `Z`; C2 ya estandarizó el formatter de segundos `'Z'`
  (`LoginController.EXPIRES_AT_FORMATTER`). Serializarlo por campo o
  globalmente en el DTO sin cambiar el patrón compartido (riesgo bajo,
  recordatorio).
- **R-7 · Veneno de env en smokes** (heredado WARNING 2 del verify C2): los
  smokes de C3 deben recomponer con el env del proceso UNSET + `.env` de forma
  robusta y `recreate` (nunca `restart`) si tocan la pila.
- **R-8 · Nota heredada (WARNING 1 del verify, C8 no C3)**: el 401 del entry
  point sale ISO-8859-1; los clientes que hagan `.json()` estricto fallarán en
  el parse del message. Para C3 no bloquea (los códigos/status correctos), y
  la recomendación del verify (fix en C8) sostiene; solo recordar en los
  scripts del smoke no depender del parse estricto del body de los 401.

## 4. Downstream ligado

- **C4 — API keys**: el modal de regeneración de la ficha (maqueta 05) usa el
  `GET /projects/{id}` de C3 para mostrar `api_key_prefix`/`has_key`
  (AppFlow flujo 3 paso 1); `ProjectsRepository.findById` es pieza compartida.
- **C5 — Ingesta**: el 403 `project_archived` es consecuencia del campo
  `status` que C3 crea y gestiona (nota de cruce del corte: aquí existe el
  Dato, allí la regla — el lookup por hash devuelto por su índice decidirá con
  `status == 'ARCHIVED'`).
- **C6 — Consulta**: `GET /logs` filtra por `project_id`; el panel mapea el
  nombre del proyecto desde `GET /projects` (Schema §3.3 sobre LogListItemDto:
  "el panel ya cargó GET /projects y mapea por project_id"). Un proyecto
  ARCHIVED sigue siendo consultable (regla 12).
- **C8 — Frontend**: `/proyectos` (04) y `/proyectos/:id` (05) se renderizan
  contra esta API con los estados del AppFlow §4; los formularios
  `/proyectos/nuevo` y `/proyectos/:id/editar` van SIN maqueta (de flujo 2, por
  decisión de coherencia ya registrada). Las maquetas servibles de C3 son el
  puente de validación API, no el producto final.
- **C10 — P1**: DELETE con `X-Confirm-Name` (CASCADE) y summary
  (`include=summary` + `GET /projects/{id}/summary`): C3 NO implementa nada de
  eso, pero el repo debería dejar la consulta por id lista para extender.

## 5. Preguntas para el gate de propuesta (máx. 5)

1. **Literal del 400 de validación de campos** (P-1): ¿aprobar
   `field_too_long` (reutilizando el patrón §4.1, con `field: name|description`)
   y `field_required` para name ausente/vacío, u otro literal?
2. **Semántica PATCH** (P-2): ¿`description: null` presente borra la
   descripción (y `name`/`status` con null → 400), y PATCH `{}` vacío → 200
   idempotente?
3. **Query `?status=` inválida en GET /projects** (P-3): ¿400
   (propuesta, patrón `filter_invalid_value` de filtros) o ignorar el filtro
   hasta C10?
4. **Tests** (P-5): ¿mantener el patrón C2 (unit [L] + smoke en pila real, sin
   Testcontainers) como proponemos, o introducir Testcontainers ya en C3?
5. **Maquetas servibles** (P-4): ¿copias adaptadas de 04/05 en
   `frontend/public/mockups/` con login inline + fetch real, servidas en
   `:8080/mockups/...` (docs/mockups intactos), como decisión del Done?