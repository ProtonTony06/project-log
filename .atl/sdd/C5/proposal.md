---
change: C5
artifact: proposal
last_updated: 2026-10-25
status: approved
gate: "APROBADO por el usuario 2026-10-25 (6/6 puntos tal cual: B1 con D-C5-1, unidades bytes/caracteres, G-1 no reabrir + G-2 cierre activo E5.31, sin Testcontainers, seam IngestListener EN C5, literales 401/403/413; all-or-nothing del batch aclarado al usuario y aceptado: 'perfecto, me parece bien')"
---

# Proposal — C5 · Ingesta REST

> Fase sdd-propose. Se presenta al usuario como GATE (aprobación de los 6
> puntos de decisión de la §5) antes de pasar a spec/design/tasks/apply.
> Sin este aprobado no se escribe código de C5. Insumos: `explore.md` de C5
> (fuentes congeladas: Schema `docs/05_BACKEND_SCHEMA.md` §2/§3.4/§4.1/§5/§6.3,
> TRD `docs/02_TRD.md` §2.5/§2.6/§2.7/§2.9 + ADR-4/11/15/25/26/28, AppFlow
> `docs/03_APPFLOW.md` flujos 3/7/10 + §4/§5, PRD `docs/01_PRD.md` §6,
> 07_SLICING_PLAN.md ficha C5), frontera C4 (`spec.md` §7),
> `verify-report.md` + `archive-report.md` de C4 (huecos G-1/G-2, S-3
> LIKE-underscore) y el código real verificado en HEAD 0d93a68/6cc609f.
>
> NOTA de nomenclatura heredada del explore: los ficheros REALES congelados
> son `docs/05_BACKEND_SCHEMA.md` y `docs/03_APPFLOW.md` (no existe
> `docs/03_SCHEMA.md` ni `docs/04_APPFLOW.md`: el enunciado inicial los citó
> mal y el explore lo corrigió — número = posición en /docs).

## 1. Problema y motivación

La cadena de ingesta está **apagada y sin habitantes**: C4 dejó lista la
columna de confianza (`api_key_hash` BYTEA, índice parcial único
`projects_api_key_hash_idx` ya creado), las primitivas crypto
(`ApiKeyService.sha256` / `tiempoConstanteIgual`) y el ciclo de gestión del
panel (generar/regenerar/revocar) — pero **ninguna key autentica nada** hoy.
`SecurityConfig.ingestChain()` (@Order(1), `securityMatcher
"/api/v1/ingest/**"`) sigue con su `denyAll()` **TEMPORAL** respondiendo 401
`invalid_token` (contrato del PANEL) a todo intento de ingestar, con o sin
`X-API-Key`. El `com.projectlog.ingest` es solo un `package-info.java`.

Es el siguiente corte natural del 07 §C5 (stories 5, 6, 7 del PRD: **P0-4
ingesta individual, P0-5 batch, P0-6 validación/límites**), y el flujo de
valor entero del proyecto pasa por aquí: sin ingesta no hay logs, sin logs no
hay consulta (C6) ni streaming (C7) ni panel útil. Además la ingesta es la
**única forma de crear logs** ("no existe la vista crear log": AppFlow flujo
10, PRD §6 "un log se recibe solo por ingesta y nunca se edita") y es la
cara que verán los clientes externos: los literales de su contrato (202, 400
de campo, 401, 403, 413) están ya fijados en los docs congelados y C5 debe
hacerlos reales tal cual, no reinventarlos.

El 07 ficha C5 lo define: dos endpoints con campos de confianza
(`project_id` deducido de la key, `received_at` UTC fijado por el servidor),
validación fail-fast con literales, límites (1 MiB, 32/128/64 KiB, batch
1…200), 403 `project_archived`, all-or-nothing en UNA transacción y
respuesta **202 `{accepted: N}`** (decisión cerrada: no es 200).

## 2. Alcance propuesto

### 2.1 Endpoints (literales CITADOS de Schema §3.4 / TRD §2.6 / AppFlow flujo 10)

La superficie `/api/v1/ingest/**` es **API de servicio-a-servicio
autenticada por `X-API-Key`; sin pantalla ni acciones de usuario** (AppFlow
flujo 10); sin rate limiting (ADR-26: "Sin rate limiting por ventana temporal
en ingesta (v1)... Solo aplican los límites por petición de §4.1"); sin CORS
(TRD §5, cadena 1 sin `cors()`, `CorsConfig` sin `X-API-Key` — deliberado S7
de C4).

1. **`POST /api/v1/ingest`** — log individual.
   - Entrada: **`LogIngestRequest`** JSON — Schema §3.4 (los 8 campos
     literales, nombres EXACTOS):
     `{level, timestamp, message, exception, metadata, environment, source,
     correlation_id}`.
   - **Éxito: 202 `{"accepted": 1}`** (Schema §3.4: "`POST /api/v1/ingest`
     (1 log) → **202 `{"accepted": 1}`**"; 06: misma literal; el 202 es
     decisión cerrada, NO 200).
2. **`POST /api/v1/ingest/batch`** — batch.
   - Entrada: `{"logs": [LogIngestRequest, …]}` con **1…200 registros**
     (Schema §3.4, límite `INGEST_MAX_BATCH_SIZE` de Schema §4.1).
   - **Éxito: 202 `{"accepted": N}`**. Batch vacío (0) → 400 `batch_empty` y
     >200 → 400 `batch_too_large`, ambos con `field: "logs"` (literales
     Schema §3.4/§4.1, verbatim).

**Tabla de errores de ingesta** (Schema §3.4 — la fila "Respuestas de error
de ingesta", literales VERBATIM):

| Situación | HTTP | `code` |
|---|---|---|
| Key ausente/inválida/revocada | 401 | `api_key_invalid` |
| Proyecto archivado | 403 | `project_archived` (el TRD §2.6 lo nombra `ARCHIVED`; mismo significado, único cuerpo `{"code": "project_archived"}`) |
| Validación de campo | 400 | el correspondiente, con `field` |
| Petición > 1 MiB | 413 | `payload_too_large` |

- **Códigos 400 exactos** (Schema §3.4/§4.1, verbatim en el enum de
  validación): `environment_required` (falta `environment`, sin default —
  ADR-25/Schema §4 regla 7, GATE 2026-10-19), `timestamp_without_timezone`
  (ISO-8601 sin offset), `level_invalid` (fuera de
  `TRACE/DEBUG/INFO/WARN/ERROR/FATAL`), `message_required` (vacío),
  `metadata_not_object` (array o escalar), `field_too_long` (message,
  exception, metadata, source, correlation_id — con `field` del culpable),
  `batch_empty` / `batch_too_large` (`field: "logs"`).
- **Regla 6 del Schema §4, verbatim**: "El lote se valida **completo antes de
  tocar la BBDD** (fail-fast: enums, tamaños, ISO-8601 con offset,
  `environment` presente); si un registro falla → 400 con `index`/`field` del
  culpable y **nada se persiste**; la inserción del lote es **una única
  transacción**; 202 solo tras el COMMIT (ADR-11)". En batch, el 400 del
  registro culpable lleva **`index`** (posición en el array) además de
  `field`.
- **Orden de evaluación** (Schema §3.4, verbatim): "tamaño (413, descartado
  antes de parsear) → key (401) → archivado (403) → validación (400). Todos
  **fail-fast antes de tocar la BBDD** (§4)".
- **413**: el `code` del cuerpo es **`payload_too_large`** (Schema §4.1, fila
  "Petición HTTP de ingesta | 1 MiB (`INGEST_MAX_REQUEST_BYTES`) | 413
  `payload_too_large`"). `INGEST_MAX_REQUEST_BYTES` es el nombre del **límite**
  (env/propiedad, TRD §6.3 y Schema §4.1), NO del code.
- **403 `project_archived`**: es del SERVICIO, no de seguridad — key VÁLIDA
  contra proyecto con `status = 'ARCHIVED'` (Schema §4 regla 12, verbatim:
  "**Ingesta solo sobre proyectos ACTIVE** — Proyecto ARCHIVED → 403
  `project_archived` para toda ingesta; sus logs siguen consultables"). La
  cadena 1 JAMÁS produce un 403 propio (su access-denied sigue escribiendo el
  JSON estándar, nunca HTML — heredado de C1).
- **401**: un ÚNICO literal para los 3 casos (header ausente, malformado,
  hash sin matchea / revocada — la revocación pone los 3 campos de key a NULL
  y el WHERE por igualdad ya no la encuentra, Schema §4 regla 11). Sin
  oráculo de causa, patrón anti-enumeración de C2/S5. `message` propuesto en
  el Punto 6 del gate.
- **Cuerpo de error estándar**: `{code, message, field?}` (TRD §2.6, verbatim:
  "Errores explícitos siempre, con cuerpo `{code, message, field?}` — nunca
  silenciosos"). El campo extra `index` (batch) y los literales de `message`
  los fija el spec (los docs fijan el `code` y el `field`; el `message` de
  los códigos de ingesta NO está fijado en ningún doc — Punto 6 del gate).
- **Sin 404**: no hay 404 en ingesta (la key deduce el proyecto; "no existe"
  se materializa como 401 para no dar oráculo).
- **Sin 429** (ADR-26). **Errores sin UI**: "los scripts de las apps los
  reciben tal cual" (AppFlow flujo 10).

### 2.2 Autenticación (X-API-Key — filtro de la cadena 1)

- **Patrón A1 del explore**: filtro `OncePerRequestFilter` en
  `com.projectlog.ingest` (`IngestGuardFilter`), réplica del `JwtAuthFilter`
  de C2 (leer header → derivar credencial → lookup → principal en
  `SecurityContext`; sin match → contexto VACÍO y seguir la cadena: la regla
  `authenticated()` falla y un **entry point NUEVO** de la cadena 1 escribe
  el 401 JSON). Un solo escritor del 401 **por superficie** (el CD-4 de C2
  era sobre la API de panel; el literal `invalid_token` de
  `RestAuthenticationEntryPoint` es CONTRATO del panel, CD-4, y **no se
  toca**, fichero intocable).
- **Mecánica exacta**: header `X-API-Key` → `ApiKeyService.sha256(header)`
  (String COMPLETO, UTF-8, sin trim de `plk_` — mismo contrato del hash que
  C4 fijó y probó en pila con E4.19: `api_key_hash = sha256('<key>'::bytea`)
  → `ProjectsRepository.findByApiKeyHash(byte[])` (Punto 1 del gate) → con
  match: principal nuevo (`IngestPrincipal` con `project_id` + `status`) en
  el `SecurityContext` con `UsernamePasswordAuthenticationToken(principal,
  null, List.of())`; sin match: continuar sin contexto.
- **El 401 lo escribe el entry point de la cadena 1 con `{"code":
  "api_key_invalid", "message": "…"}`** (literal propuesto en Punto 6):
  ausente, malformado y no-match producen el MISMO cuerpo.
- **El controller/service NUNCA re-hacen el lookup** (una sola ida por
  request); `project_id` del principal, jamás del cliente (Schema §4 regla 5,
  verbatim: "`project_id` deducido de la API key (el cliente no lo manda; un
  log no puede colarse en el proyecto equivocado — métrica 2 del PRD)").
- **CERO JWT en ingesta** (Schema §5, verbatim: "Un JWT **jamás** autentica
  la ingesta (TRD ADR-15)"; y una API key "jamás autentica panel ni socket").
  Nacen bajo la cadena 1: **el panel y sus cadenas quedan byte-a-byte
  intactos** (solo cambia el cuerpo del bean `ingestChain` — Punto del §7 de
  estimación; R-3).
- **Comparación de tiempo constante post-lookup**: con la firma recomendada
  (`ProjectDto` completo, Punto 1) el stored hash NO viaja, así que la
  comparación extra `tiempoConstanteIgual` NO es posible en este diseño; la
  igualdad la resuelve el `WHERE api_key_hash = ?` del SELECT por índice. Es
  la divergencia declarada del Punto 1 (decisión de gate).

### 2.3 Persistencia (tabla `logs` de V1 — sin migraciones)

- **`LogsRepository` NUEVO en `com.projectlog.ingest`** (SQL inline, patrón
  C2/C3 de `ProjectsRepository`: SQL inline como `static final String`,
  RowMapper/extractor propio, `@Transactional` declarativa). C6 creará sus
  consultas de lectura en su corte; los INSERTs de `logs` viven aquí (la
  única fuente de columnas de `logs` del código).
- **Columnas pobladas**: `project_id` (del principal), `level`, `environment`
  (enums validados en el borde), `timestamp` (parseada de la entrada),
  `received_at` (servidor), `message`, `exception` (opcional), `metadata`
  (opcional JSONB), `source` (opcional), `correlation_id` (opcional). `id` lo
  genera la BD (`GENERATED ALWAYS AS IDENTITY`).
- **INSERT multi-VALUES + RETURNING id**: un estamento único
  `INSERT INTO logs (…) VALUES (…),(…)… RETURNING id` (para el individual es
  el MISMO estamento con 1 fila). El `RETURNING id` es la moneda del frame
  `batch` de C7 (los ids en orden de VALUES; coste nulo) — recomendado desde
  C5 aunque la respuesta HTTP de C5 no los exponga (la 202 solo lleva
  `accepted`).
- **`received_at` fijado UNA VEZ en Java, común al lote** (opción (ii) del
  explore): `OffsetDateTime` calculado UNA vez en el servicio y pasado como
  parámetro — el valor exacto es determinista, testeable y es el mismo que
  viajará al frame WS de C7 (el seam lo necesita). Es lo que exige la regla
  9 del Schema §4, verbatim: "**Todos los logs de un mismo batch comparten un
  mismo `received_at`** (fijado en el momento de la transacción)". (Con un
  solo INSERT multi-VALUES el `now()` de PG también sería constante; se
  prefiere Java por determinismo y visibilidad del valor en el servicio —
  R-5.)
- **`timestamp` de la entrada**: parse estricto `OffsetDateTime.parse` (acepta
  `Z` y offsets como `+02:00`; rechaza el naive sin offset → 400
  `timestamp_without_timezone`); PG lo almacena en UTC internamente
  (`TIMESTAMPTZ`): "siempre se normaliza a UTC al guardar" (TRD §2.6).
- **`metadata` JSONB** (R-4): solo objeto JSON (array/escalar → 400
  `metadata_not_object`); al insertar, `CAST(? AS jsonb)` con el String
  serializado (sin cast fallaría: R-4). El tamaño se valida SOBRE el JSON
  serializado (Schema §4.1: "metadata | ≤ 64 KiB (**serializado**)").
- **`correlation_id`**: la APORTA el cliente (opcional, ≤200 chars, columnas
  ÚNICA ADR-28/regla 8); NINGUNA generación server-side ("ningún doc la
  genera" — Schema §3.4 la lista como campo de request).
- **Los tamaños de mensaje/exception/metadata ya caben en `TEXT`/`JSONB`
  sin migración**: V1 fijó las CHECKs de enums y las columnas; los límites de
  bytes los aplica el borde (§2.4), no la BD.

### 2.4 Límites (valores y UNIDADES — se fijan para spec)

| Límite | Valor | Unidad (propuesta de cierre) | Dónde se aplica |
|---|---|---|---|
| Petición | **1 MiB** (`INGEST_MAX_REQUEST_BYTES`) | bytes del cuerpo | filtro (413): **Content-Length primera barrera** (413 sin leer el cuerpo) + **`BoundedInputStream` segunda barrera** (chunked / sin header → el parser muere al cruzarlo, R-1) |
| Batch | **1…200** (`INGEST_MAX_BATCH_SIZE`) | registros | borde del controller (400 `batch_empty`/`batch_too_large`, `field: "logs"`) |
| `message` | ≤ **32 KiB** = 32768 | **bytes UTF-8** | borde (400 `field_too_long`, `field: "message"`) |
| `exception` | ≤ **128 KiB** = 131072 | **bytes UTF-8** | borde (400 `field_too_long`, `field: "exception"`) |
| `metadata` | ≤ **64 KiB** = 65536 | **bytes del JSON serializado** | borde (400 `field_too_long`, `field: "metadata"`) |
| `source` | ≤ **200** | **caracteres** | borde (400 `field_too_long`, `field: "source"`) |
| `correlation_id` | ≤ **200** | **caracteres** | borde (400 `field_too_long`, `field: "correlation_id"`) |

- UNIDADES (fijadas para spec, Punto 2 del gate): fiel al literal del Schema
  — "KiB" para message/exception/metadata ⇒ **bytes UTF-8** (los docs hablan
  de KiB, no de chars); los docs dicen **"≤ 200 caracteres" explícito** para
  source y correlation_id (Schema §2, tabla `logs`; §3.4 "≤ 200 caracteres
  (concreción)") ⇒ `String.length()`, patrón C3.
- Propiedades `projectlog.ingest.max-request-bytes` /
  `projectlog.ingest.max-batch-size` en `application.yml` alimentadas desde
  `INGEST_MAX_REQUEST_BYTES` / `INGEST_MAX_BATCH_SIZE` (compose.yaml ya las
  interpola — verificado grep compose.yaml:39-40; `.env.example` ya trae
  `INGEST_MAX_REQUEST_BYTES=1MiB` / `INGEST_MAX_BATCH_SIZE=200`; el sufijo
  `MiB` obliga a decisión de bind — R-6, Punto 2 del gate).
- Validación **en el borde del controller, MANUAL** (patrón C3): sin
  `spring-boot-starter-validation` (no está en el pom y añadirlo sería una
  dependencia nueva incoherente con C3/C4). Batch con lote:
  primer-fallo-gana, registro por registro en orden del array, `index`
  creciente del primer culpable (R-9: determinismo).
- **413 en el filtro, ANTES del parser JSON** (Schema §3.4: "tamaño (413,
  descartado antes de parsear)"). El guard único (D1 del explore) mira el
  tamaño y después autentica: el orden 413→401 queda determinista en UNA
  clase. Batch >200 NO es 413: el límite de registros es 400 (fail-fast en el
  borde), independiente del límite de bytes (R-13).

### 2.5 All-or-nothing (una transacción)

- Validación COMPLETA del batch ANTES de abrir la transacción (barrera que
  vuelve trivial "nada se persiste" ante registro inválido — regla 6).
- `@Transactional` única en el método del servicio (primera transacción real
  multi-registro del codebase; C3/C4 eran mono-sentencia con autocommit — el
  Javadoc de `ProjectsRepository` ya lo anticipa: "lo multi-sentencia (batch
  all-or-nothing, CASCADE) llega en C5/C10"). Con el INSERT multi-VALUES es
  en la práctica mono-sentencia, pero el `@Transactional` queda como semántica
  explícita y defensa (regla 6 / ADR-11).
- **Sin captura de `DataIntegrityViolationException` ni de
  `DuplicateKeyException`** (en `logs` no hay UNIQUE aparte de la PK): los
  fallos reales de INSERT son teóricos (FK `project_id` si el proyecto
  desaparece en carrera → **500 honesto**, política S6 de C4). Rollback
  automático cubre el resto.

### 2.6 Respuesta 202 — SOLO tras COMMIT

- `{"accepted": N}` (individual fija `accepted: 1`) SIN campos adicionales
  (ni ids ni received_at — no es contrato; C7 los lleva en el frame desde el
  seam). Content-Type JSON. **Nada del lote fallido cruza el 202** (regla 6,
  "202 solo tras el COMMIT (ADR-11)"; ADR-9 en espíritu).

### 2.7 Seam para C7 (streaming) — si se aprueba el Punto 5

- Interfaz `com.projectlog.ingest.IngestListener` con un único método
  (firma propuesta, a fijar en spec): `onLotePersistido(long projectId,
  OffsetDateTime receivedAt, List<Long> ids)` — el frame `batch` de C7 lleva
  `project_id`, `received_at` y los `logs[]` con sus `id` (todo lo que el
  seam porta; `batch_id` lo genera C7, no C5).
- Invocado DESPUÉS del COMMIT (llamada del controller tras el método
  transaccional: el 202 y la emisión quedan fuera de la transacción),
  SIN suscriptores en C5 (lista inyectada vacía / `Optional` — cero clientes).
- C5 NO implementa streaming (ni handler ni frames ni `LiveBroadcaster`).

## 3. Qué NO hace C5 (fuera de alcance — NO-alcanza)

- **Streaming WebSocket real (C7)**: ni frames, ni `LiveBroadcaster`, ni el
  handler real de `/ws/logs`; SOLO el seam del Punto 5 (si se aprueba).
  `streaming/` (EchoSocketHandler + WebSocketConfig) queda byte-a-byte
  intacto.
- **Consulta keyset, filtros, detalle (C6)**: ni `GET /logs` ni LogDto ni
  cursor; el repo de lectura de logs NO se anticipa.
- **Frontend (C8)**: los errores de ingesta no tienen UI (AppFlow flujo 10).
- **Migraciones**: `V1__init.sql` INTOCABLE — la tabla `logs` está completa
  (received_at, environment, correlation_id, JSONB, CHECKs, índices) y el
  índice parcial único de hash ya existe (R4.1 heredado).
- **El entry point del panel NO se toca**: `RestAuthenticationEntryPoint`
  (`invalid_token`, CD-4, contrato del panel y de C2) queda INTACTO; el 401
  `api_key_invalid` nace en un entry point NUEVO registrado SOLO en la cadena
  1 (R-3).
- **NO reabre G-1** (QP-30 de C4): la rama "empty-RETURNING→404 en
  `regenerateKey`" queda CERRADA como hueco aceptado en el archive de C4
  (S-1); C5 no toca ese método y no le asigna escenario (Punto 3).
- **No rate limiting 429 (ADR-26), no retención, no borrados (C10), no
  summary (C10)**, no cambios en `panelChain`/`defaultChain`, sin
  `starter-validation`, sin Testcontainers en C5 (Punto 4).

## 4. Enfoque técnico recomendado (del explore, ratificado)

- **Paquete `com.projectlog.ingest`** (package-by-feature; el package-info de
  C1 ya declara "REST individual y batch con X-API-Key, límites
  INGEST_MAX_*, 413/403/400, all-or-nothing"). En `ingest` nacen: el FILTRO
  de guard (`IngestGuardFilter`: 413 primera barrera + auth de `X-API-Key` +
  segunda barrera `BoundedInputStream`), el entry point de la cadena 1, el
  principal (`IngestPrincipal`), `LogIngestRequest` (record, snake_case),
  `IngestController`, `IngestService`, `LogsRepository`, la respuesta
  `{"accepted": N}` y (si el Punto 5 aprueba) `IngestListener`.
- **Dependencias entre features**: `ingest` importa `keys` (las primitivas
  `sha256`) y `projects` (el repo); `keys` NO gana dependencias hacia
  `ingest`.
- **`SecurityConfig`: SOLO el cuerpo del bean `ingestChain` cambia** (quitar
  `denyAll()` → `authenticated()`, `addFilterBefore(guard,
  UsernamePasswordAuthenticationFilter.class)`, entry point propio de la
  cadena 1, `accessDeniedHandler` del entry point de la cadena 1 MANTENIDO —
  nunca 403 HTML; `ProjectDto` del principal lleva el `status` que el 403
  necesita). Cadenas 2 y 3 byte-a-byte iguales.
- **`ProjectsRepository`: +1 método ADITIVO**
  `findByApiKeyHash(byte[])` (Punto 1); reusa `COLS`/`FILA` (una sola fuente
  de columnas, CD-3.2); jamás toca los estamentos de C3/C4 (pins
  `ProjectsKeySqlTest` siguen).
- **SQL de `logs`**: INSERT multi-VALUES con `RETURNING id`;
  `metadata` con `CAST(? AS jsonb)`; literal exacto a fijar en spec con pin
  SQL (patrón `ProjectsKeySqlTest` → `LogsRepoSqlTest`).
- **Tests NUEVOS** (no se toca un solo test de C1–C4): patrón
  `@WebMvcTest` + `@Import(SecurityConfig, guard, entryPoint-ingesta,
  CorsConfig)` con `@MockitoBean` (patrón `ApiKeyControllerMvcTest`);
  unit del normalizador de timestamp y del servicio; pin SQL; higiene
  ListAppender (ningún log del paquete `ingest`/`keys` contiene el header
  `X-API-Key`, la key `plk_`o el hash — patrón R4.13).
- **Smoke**: `scripts/qa_c5_smoke.sh` (patrón `qa_c4_smoke.sh`): curl + psql
  contra la PALA REAL, recomposición ÚNICA `--force-recreate`, asertos de
  conteo psql (`count(*)`) antes/después para all-or-nothing, smoke-of-the-
  smoke con precondiciones, needles sin underscore sin escapar (S-3 de C4:
  en needles `plk_*` usar escape `\_` o `position()`/`=`).

## 5. Decisiones/riesgos para el GATE — 6 puntos

> Los 6 numerados deben aprobarse (aprobación 6/6, como C4). Se marca cada
> uno si es decisión de fondo del usuario o si va de paquete.

### Punto 1 (DE GATE) — Firma del lookup `findByApiKeyHash` y el uso/ausencia de `tiempoConstanteIgual` en el hot-path

**Enunciado.** C4 spec §7 (frontera, verbatim) documentó la firma
"`Optional<ProjectDto> findByApiKeyHash(byte[] hash)` sobre `SELECT <COLS>
FROM projects WHERE api_key_hash = ?`" Y su uso: "`sha256(header)` (String
completo, este mismo contrato) → lookup → `tiempoConstanteIgual` como
comparación final". El problema: `ProjectDto` NO porta `api_key_hash` (no
existe en `COLS`), así que con la firma documentada la comparación extra
`tiempoConstanteIgual` post-lookup NO es posible. Dos salidas:

- **B1 (RECOMENDADA)**: firma EXACTA del ancla C4:
  `Optional<ProjectDto> findByApiKeyHash(byte[] hash)` con `SELECT <COLS>`.
  La igualdad la resuelve el `WHERE api_key_hash = ?` del índice B-tree; el
  matching binario de 32 bytes ya es exacto. Coste: declarar la DIVERGENCIA
  con el literal `tiempoConstanteIgual` del C4 spec §7 como **D-C5-1**: la
  comparación final en tiempo constante se cumple **estructuralmente** (el
  SELECT por igualdad no filtra por timing observable: la comparación es de
  bytes completos en índice, y ante un atacante sin memoria previa del hash
  — 256 bits de entropía — no hay timing-oracle útil; ADR-4 justifica el hash
  rápido del hot-path). La ventaja: sin DTO estrecho, sin re-hasheo, sin
  migración, y con la firma QUE YA C4 ANCLÓ (sin gate extra sobre el repo).
- B2 (alternativa): query estrecha `SELECT id, status, api_key_hash …` con
  record propio y cadena completa sha256 → lookup →
  `tiempoConstanteIgual(hash, storedHash)` — cumple el literal §7 al pie de
  la letra, pero diverge de la firma documentada (el mismo camino de C4 con
  sus D-1..D-6: legal, pero exige su propio cierre S-x + ancla) y coloca un
  DTO de ingesta en el repo del panel.

**Recomendación (de gate, se decide aquí).** B1. El 403 necesita `status`
(que `ProjectDto` porta) y el servicio necesita `project_id` (también); ambos
van gratis en `COLS`. La D-C5-1 se documentará en el spec y quedará cerrada
en el archive con esta ratificación del gate. Nada de segunda comparación
con `MessageDigest` en ingestión por diseño (ADR-4: la entropía de la key es
el blindaje, no la comparación post-hoc). Riesgo residual certificado por
código + smoke (E5.21/E5.24).

### Punto 2 (de paquete, con cierre para spec) — UNIDADES de los límites y mecánica del 413

**Enunciado.** (a) El Schema habla de "KiB" en message/exception/metadata
(por defecto: bytes) y de "caracteres" en source/correlation_id (chars). C3
midio `String.length()` (caracteres) para name/description — mezclar
convenciones rompe el literal del Schema. (b) Con `Content-Length` el 413 es
fail-fast sin leer el cuerpo, pero un cliente chunked sin ese header lo
evade (R-1).

**Recomendación (de paquete).** (a) **Bytes UTF-8** (32768 / 131072 / 65536)
para message/exception/metadata (fiel a "KiB" del Schema §3.4/§4.1 y TRD
§2.7) y **caracteres** (`String.length()`) para source/correlation_id (el
Schema dice "≤ 200 caracteres" EXPLÍCITO). Se fija con esta redacción:
el spec lo reafirma con la cita y los smokes llevan fronteras de BYTES
exactos (32768/32769 de un mensaje real) para message. (b) **Doble barrera
del 413** (explore §d-i): primera `Content-Length` (comparación directa →
413 sin leer el cuerpo) + segunda `BoundedInputStream(limit = max+1)`
envuelto en el mismo filtro por petición (el parser muere determinista al
cruzar el límite). Es refuerzo barato (~15 líneas) y cierra el caso
`Transfer-Encoding: chunked` en smoke (E5.28). Mínimo aceptable del Done sería
solo (i), pero se recomienda la doble barrera completa.

### Punto 3 (de paquete, pero afecta al Done) — Cierre declarado de G-1 y cierre ACTIVO de G-2

**Enunciado.** C4 verify/archive dejaron: **G-1** (QP-30, rama
empty-RETURNING→404 de `regenerateKey`) como S-1 "reabrible en C5 si lo
considera relevante por carreras", y **G-2** (semántica regla 11, "al
desarchivar, la misma key vuelve a aceptarse") como S-2 "opcional, C5".

**Recomendación (de paquete).** (a) **G-1: NO reabrir — queda cerrado como
hueco aceptado** (rama teórica de carrera en un método que C5 NO toca; el
qa-plan de C4 ya lo marcó N-A opcional; obligar a C5 a testear una rama de
C4 ensancha falsamente el corte). (b) **G-2: SÍ se cierra ACTIVAMENTE en el
smoke de C5**, casi gratis: el flujo 403 `project_archived` ya necesita el
"desarchivar" — un paso final PATCH `status=ACTIVE` → ingesta con la MISMA
key → **202 `{"accepted":1}`** corrobora las reglas 11 Y 12 juntas (coste
~10 líneas: E5.31). El archive de C5 citará E5.31 como cierre de G-2. Sin
este paso activo, la semántica de la regla 11 quedaría de nuevo sin
evidencia de ciclo completo.

### Punto 4 (de paquete) — Testcontainers: NO en C5 (reevaluar en C6/C7)

**Enunciado.** C3 dejó "reevaluar Testcontainers en C5" (BD real bajo
`mvn test` para JSONB/bytea/TIMESTAMPTZ/all-or-nothing).

**Recomendación (de paquete).** **NO en C5**. El patrón vigente (`[L]`
MVC-mocks + smoke `[S]` contra la pila REAL, PG 17 con V1 aplicado, conteos y
asertos psql con `--force-recreate`) ya certifica bytea (C4 lo probó con
E4.19 en escritura), JSONB, TIMESTAMPTZ y all-or-nothing con menos coste, sin
2 dependencias test-nuevas, sin pull de imagen en cada entorno y el mismo
patrón de suite que los 77/77. C5 añade los refuerzos concretos: conteo
psql antes/después del batch inválido (all-or-nothing real), verificación de
valores materializados (`timestamp` UTC, `received_at` común, environment y
level exactos) y smoke del 413 y del batch de 200. Testcontainers se
reevaluará en C6/C7 SOLO si el ciclo de iteración con BD real fuera de la
pila se vuelve necesario.

### Punto 5 (DE GATE) — Seam para C7: incluir ya el `IngestListener` (o no)

**Enunciado.** C7 exigirá **emisión tras COMMIT** del lote persistido (frame
único `batch` con ids y `received_at`; ADR-9/§3.5: "Emisión solo tras
COMMIT", "jamás se emite un log que luego fallara al guardar"). El servicio
de C5 es el ÚNICO punto donde existirá ese evento. Opciones: **E1 seam mínimo
(hoy)** — interfaz `IngestListener` + llamada tras COMMIT, sin suscriptores —
vs **E2 nada** (C7 refactoriza el servicio y la transacción de C5).

**Recomendación (de gate).** **E1, incluir el seam YA** (1 interfaz +
≤10 líneas: riesgo de scope-creep acotado). El servicio ya calcula
`received_at` común (§2.3) y los ids con `RETURNING id` (§2.3): el 100 % de
lo que el frame `batch` de C7 necesita (menos el `batch_id`, que genera C7)
ya existe en C5 como producto de la propia ingesta. C7 sustituye/añade un
bean sin tocar el core de ingesta; con `@Transactional` acotado al método del
repo/servicio, la invocación del listener queda NATURALMENTE tras el COMMIT
(por estar fuera del método transaccional), sin maquinaria extra
(`ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` —
E3 del explore — descartado como maquinaria mayor para un único consumidor
futuro). Firma propuesta (a fijar en spec): `onLotePersistido(long
projectId, OffsetDateTime receivedAt, List<Long> ids)`. Si C7 rediseña el
seam, el coste de borrar una interfaz es ≈ 0.

### Punto 6 (de paquete con anclaje de literales) — Mensajes exactos de 401/403/413 de INGESTA

**Enunciado.** Los docs fijan los `code` (`api_key_invalid`,
`project_archived`, `payload_too_large`) y el `field` de los 400, pero
NINGUNA literal de `message` para los códigos de INGESTA ("El message del
panel es el literal S3 de este spec; el de ingesta lo fijará el spec de C5" —
C4 spec §7). Además el code `project_archived` viste 409 en PANEL (C4) y 403
en INGESTA (C5): misma `code`, distinta superficie.

**Recomendación (de paquete, con los 3 literales PROPUESTOS aquí para
ratificar).** Cuerpo estándar `{code, message, field?}` de TRD §2.6 en
español (patrón C2/C3/C4), un ÚNICO `message` por code sin varianza según
causa (anti-oráculo):

- **401** (entry point de la cadena 1, SOLO en ingestChain):
  `{"code": "api_key_invalid", "message": "API key de ingesta ausente o
  inválida"}`.
- **403** (servicio, key válida + proyecto ARCHIVED):
  `{"code": "project_archived", "message": "El proyecto está archivado: no
  acepta logs"}` (mensajes DISTINTOS del panel con el MISMO code — el panel
  es "…desarchívalo antes de generar o regenerar su API key": contexto
  distinto, mismo idioma/patrón C3).
- **413** (filtro):
  `{"code": "payload_too_large", "message": "La petición supera el límite de
  1 MiB"}`.

Los tres se fijarán con constants en el código (patrón `CODE_NOT_FOUND`/
`MSG_NOT_FOUND` de C3) y quedan a reafirmar con cita en spec. Nota menor: la
tabla de Schema §3.4 para `project_archived` dice "único cuerpo
`{"code": "project_archived"}`" — se interpreta como "un ÚNICO cuerpo
sin varianza por condición" (coherente con el envelope `{code, message,
field?}` del TRD §2.6 citado por el mismo doc); el spec reafirma esta
lectura. Los `message` de los 400 (por campo: `environment_required`,
`timestamp_without_timezone`…) se proponen en el mismo patrón y los fija el
spec (los docs no los fijan — Punto del §8).

## 6. Criterios de Done de C5 (propuesta, trazados a escenarios E5.x)

1. `POST /api/v1/ingest` con key real: **202 `{"accepted": 1}`** EXACTO; fila
   en psql con `project_id` deducido, `level`, `environment`, `timestamp`
   almacenada UTC, `received_at` del servidor, `correlation_id` del cliente,
   `metadata` JSONB legible. — **E5.21, E5.29, E5.30**
2. `POST /api/v1/ingest/batch` con 200 registros: **202 `{"accepted":
   200}`**; las 200 filas comparten `received_at` EXACTO (regla 9) y los ids
   crecen en orden de VALUES. — **E5.22**
3. All-or-nothing: batch con un registro inválido INTERMEDIO → 400 con
   `index`/`field` del culpable y **psql `count(*)` invariante antes/después**
   (nada se persiste a medias). — **E5.23, E5.11**
4. Los 5 errores de la ficha 07 contra la pila: **401 `api_key_invalid`**
   (sin header / malformada / sin matchea o revocada — E5.24), **403
   `project_archived`** con key VÁLIDA (E5.25), **400
   `environment_required`** (E5.26), **400 `timestamp_without_timezone`**
   (E5.26, naive rechazado), **413 `payload_too_large`** (E5.27). Orden
   fail-fast 413→401→403→400 en pila (E5.33).
5. G-2 (si Punto 3 en paz): desarchivar → ingesta con la MISMA key → 202
   (reglas 11+12 juntas, cierre ACTIVO de G-2). — **E5.31**
6. Fronteras de batch y 413: 0 → 400 `batch_empty`; 201 → 400
   `batch_too_large`; 1 MiB exacto vs 1 byte más (413 en frontera); 413 con
   cuerpo chunked SIN `Content-Length` (segunda barrera, si Punto 2 en paz).
   — **E5.32, E5.27, E5.28**
7. Validación por campo: `level_invalid`, `message_required`,
   `metadata_not_object`, `field_too_long` (por campo, con `field`) y sus
   fronteras de BYTES (message 32768/32769). — **E5.5–E5.9, E5.30**
8. Tests [L] MVC/unit: 202 literales, 401 del entry point NUEVO (`api_key_invalid`,
   NO `invalid_token`), 403 con principal ARCHIVED, 400 por code/field/index
   con orden determinista primer-fallo-gana, pin SQL del INSERT con
   `CAST(? AS jsonb)`, pin de SecurityConfig (cadenas 2/3 byte-a-byte),
   higiene ListAppender (sin `plk_`/header en logs de `ingest`/`keys`),
   unit del parse de timestamp (+02:00 → UTC, naive → rechazo) y del
   `received_at` común por lote. — **E5.1–E5.20**
9. Seam (si Punto 5 en paz): `IngestListener` invocado UNA vez tras COMMIT
   (no ante 401/403/400 ni ante rollback) con `projectId`, `receivedAt`,
   `ids`; sin suscriptores = no-op verificable. — **E5.20**
10. NO pasa (fronteras): streaming C7 real, consulta C6, front, migraciones,
    `panelChain`/`defaultChain` alterados, `RestAuthenticationEntryPoint`
    (panel) tocado, `CorsConfig` con `X-API-Key`, `starter-validation`,
    Testcontainers, captura de DIVE, G-1 reabierto.

### Tabla de escenarios E5.x propuesta (lista exhaustiva para spec/qa-plan)

| Familia | IDs | Tipo / cobertura |
|---|---|---|
| MVC 200/202 | E5.1, E5.2 | individual `{"accepted":1}` y batch `{"accepted":N}` — literales EXACTOS |
| MVC 400 de campo | E5.3 (environment_required), E5.4 (timestamp_without_timezone), E5.5 (level_invalid), E5.6 (message_required), E5.7 (field_too_long message BYTES 32768/32769), E5.8 (metadata_not_object), E5.9 (field_too_long de exception/metadata/source/correlation_id) |
| MVC 400 de lote | E5.10 (batch_empty/batch_too_large con `field:"logs"`), E5.11 (`index`/`field` del culpable; primer-fallo-gana, R-9) |
| MVC 401/403 | E5.12 (401 `api_key_invalid` del entry point NUEVO — sin header, malformada, no-match), E5.13 (403 `project_archived` con principal ARCHIVED; NO toca nada de panel) |
| Determinismo y pin | E5.14 (orden fail-fast de validación, primer-fallo-gana), E5.15 (pin SQL literal del INSERT multi-VALUES + RETURNING id + CAST(? AS jsonb), patrón ProjectsKeySqlTest), E5.16 (SecurityConfig: cadenas 2/3 byte-a-byte + chain 1 con entry point propio) |
| Higiene | E5.17 (ListAppender: ningún evento de `ingest`/`keys` con `plk_`, header X-API-Key o hash) |
| Unit servicio | E5.18 (timestamp: `+02:00` y `Z` → equivalente UTC; naive → rechazo), E5.19 (received_at fijado UNA vez, común al lote) |
| Seam C7 | E5.20 (IngestListener INVOCADO solo tras COMMIT: no en 401/403/400/rollback; args exactos) |
| Pila happy | E5.21 (individual real: 202 + fila completa en psql), E5.22 (batch 200: 202 + received_at idéntico EXACTO en las 200 filas + ids por orden de VALUES) |
| Pila all-or-nothing | E5.23 (batch con intermedio inválido: 400 + `count(*)` psql invariante antes/después) |
| Pila 401 | E5.24 (3 variantes contra pila: sin header / malformada / revocada o no-match — mismo cuerpo) |
| Pila 403 | E5.25 (ACTIVE con key → PATCH ARCHIVED → ingestar misma key → 403) |
| Pila 400 | E5.26 (`environment_required` y `timestamp_without_timezone` reales en pila) |
| Pila 413 | E5.27 (Content-Length: frontera 1 MiB exacto / 1 más → 413 `payload_too_large`), E5.28 (chunked sin Content-Length: segunda barrera, si Punto 2 en paz) |
| Pila confianza | E5.29 (timestamp `+02:00` → psql UTC; `received_at` del servidor), E5.30 (metadata JSONB `metadata->>'span'` legible; array → 400; frontera 64 KiB serializado) |
| Pila G-2 | E5.31 (desarchivar → MISMA key → 202; cierre G-2) |
| Pila fronteras | E5.32 (batch 0 → `batch_empty`, 201 → `batch_too_large`), E5.33 (orden en pila: 413 con key inválida → 413, no 401) |

> 33 escenarios (20 [L] + 13 [S]); el spec puede fusionarlos/apilarlos con su
> criterio de Parejas obligatorias, pero TODOS los códigos de la ficha (401,
> 403, 400×2, 413) y el all-or-nothing quedan cubiertos CONTRA LA PILA, como
> el Done del 07 exige.

## 7. Estimación de alcance

**Ficheros NUEVOS (backend, en `com.projectlog.ingest`):**

| Fichero | Contenido |
|---|---|
| `ingest/IngestGuardFilter.java` | filtro de la cadena 1: 413 (Content-Length + BoundedInputStream) + auth `X-API-Key` (sha256 → findByApiKeyHash → IngestPrincipal); patrón JwtAuthFilter; sin loggers |
| `ingest/IngestAuthenticationEntryPoint.java` | entry point de la cadena 1: 401 `{"code": "api_key_invalid", "message": "…"}` (también accessDeniedHandler de la cadena 1) |
| `ingest/IngestPrincipal.java` | record (project_id, status) — análogo a JwtPrincipal |
| `ingest/IngestController.java` | 2 mappings literales; devolución 202 `{accepted: N}` |
| `ingest/IngestService.java` | validación completa del lote (fail-fast, primer-fallo-gana, index) + `@Transactional` + received_at común + llamada del parse de timestamp + invocación del seam |
| `ingest/LogsRepository.java` | INSERT multi-VALUES + RETURNING id; SQL inline + pin de literales (patrón ProjectsRepository) |
| `ingest/LogIngestRequest.java` | record de los 8 campos del Schema §3.4 (snake_case de entrada) |
| `ingest/IngestBatchRequest.java` y `ingest/IngestAcceptedResponse.java` | wrapper `{logs: [...]}` y record `(accepted)` de la respuesta 202 |
| `ingest/IngestListener.java` | seam C7 (si Punto 5 en paz): interfaz con `onLotePersistido(projectId, receivedAt, ids)` |

**Ficheros a MODIFICAR (aditivos, sin reescritura):**

| Fichero | Cambio |
|---|---|
| `config/SecurityConfig.java` | SOLO el cuerpo del bean `ingestChain` (quitar `denyAll()` → `authenticated()` + guard delante + entry point propio de la cadena 1) + su Javadoc; cadenas 2/3 byte-a-byte |
| `projects/repository/ProjectsRepository.java` | +1 estamento aditivo `findByApiKeyHash(byte[])` (SELECT COLS … WHERE api_key_hash = ?) + Javadoc; nada de C3/C4 tocado |
| `application.yml` | +`projectlog.ingest.max-request-bytes` / `max-batch-size` (bind desde env; ver R-6/Punto 2) |
| `backend/src/main/resources/application-test.yml` | NO tocar (intocable, heredado); los límites defaultean al bind en test si hace falta — decisión de spec |
| `.env.example` | solo si R-6 exige reescribir `1MiB` → `1048576` (plan B; es literal del TRD §6.3 — NO tocar si el bind `DataSize` funciona) |
| Tests NUEVOS (`backend/src/test/java/com/projectlog/ingest/`) | MvcTest del IngestController (patrón ApiKeyControllerMvcTest con @Import del guard/entry-point de la cadena 1), unit del servicio/normalizador, pin SQL `LogsRepoSqlTest`, ListAppender de higiene — TODOS NUEVOS, sin tocar un test de C1–C4 |
| `scripts/qa_c5_smoke.sh` | NUEVO, patrón `qa_c4_smoke.sh`: codifica E5.21–E5.33 [S] |

**Intocables (sin diff en el apply de C5):** resto de `config/`
(`panelChain`/`defaultChain`, `JwtAuthFilter.java`,
`RestAuthenticationEntryPoint.java` (panel, CD-4), `CorsConfig.java`,
`StartupSecretsValidator.java`, `WebSocketConfig.java`), `auth/**`,
`keys/**` (las primitivas ya sirven: sin cambios), `streaming/**`,
`common/**`, `login`/`me`, `db/migration/V1__init.sql` (única migración),
`compose.yaml` (ya interpola las 2 env), `application-test.yml`,
`backend/pom.xml` (SIN dependencias nuevas — no Testcontainers [Punto 4],
no starter-validation [patrón C3]), `docs/**`,
`frontend/public/mockups/**`, los tests y smoke scripts de C1–C4
(re-corridos: 77 + 15 deben seguir verdes).

**Migraciones: NO. Front: NO.**

## 8. Preguntas para sdd-spec (huecos que el spec de C5 debe cerrar)

1. (heredada explore §6.1) Declaración formal de la D-C5-1: divergencia con
   el literal `tiempoConstanteIgual` del C4 spec §7 (Punto 1 B1): firma
   exacta final, Javadoc del método del repo y del filtro, y coste del
   matching por igualdad en el SELECT.
2. (heredada §6.2) Reafirmación de las unidades de límites (bytes UTF-8 vs
   caracteres) con cita al literal del Schema (Punto 2) y mecanismo de
   conteo exacto del tamaño de metadata (bytes del String serializado
   re-serializado — misma convención que el conteo del 1 MiB).
3. (heredada §6.3) Mecánica exacta de la doble barrera del 413 (BoundedInputStream con limite = max+1: dónde se envuelve, order del filtro
   respecto a Tomcat, y si el "limite+1" produce respuesta al parser o excepción capturable) + frontera EXACTA (max inclusive / max+1 → 413).
4. (heredada §6.4) Firma EXACTA del `IngestListener` (propuesta:
   `onLotePersistido(long projectId, OffsetDateTime receivedAt, List<Long>
   ids)`) y del mecanismo de suscripción (lista inyectada vacía vs
   `Optional<IngestListener>`): garantizar que la llamada quedará tras el
   COMMIT (si acaso el método transaccional es del repo y la invocación del
   seam en el controller lo documenta).
5. (heredada §6.5) Cierre G-1 (NO reabrible, declarado) y G-2 (activo
   E5.31): el spec documenta el cierre, el archive de C5 lo cita.
6. (heredada §6.6) Los 3 mensajes EXACTOS de 401/403/413 (Punto 6) + los
   `message` de los códigos 400 (todos, con/ sin `field`/`index`): anclaje
   como constants nombradas (patrón S3/C3) — con cita del envelope
   `{code, message, field?}` del TRD §2.6.
7. **NUEVA** — Literal EXACTO del estamento SQL del INSERT multi-VALUES
   (columnas literales, `CAST(? AS jsonb)` o `PGobject`, `RETURNING id`) y
   su pin (patrón `ProjectsKeySqlTest` → `LogsRepoSqlTest`).
8. **NUEVA** — Jackson: parsing estricto o laxo del `LogIngestRequest`
   (campos desconocidos → 400 o ignorar; `level`/`environment` case-sensitive:
   los enums cerrados del Schema dan a entender EXACT match; cuerpo no-JSON o
   sin `logs` en batch → 400 con qué code/field). Pregunta: el code de
   códigos 400 genéricos de parseo (`parse_error`/`malformed_body`?) NO está
   en el enum de §3.4 — decidir si un cuerpo no-JSON entra como 400 con el
   code de campo (p. ej. `message_required`) o como un code nuevo a
   proponer, SIN inventar códigos fuera del enum.
9. **NUEVA** — Bind de `INGEST_MAX_REQUEST_BYTES=1MiB`: ¿`DataSize` de Spring
   (@ConfigurationProperties) o @Value con `DataSize.parse`? Verificar el
   sufijo exacto "MiB" (R-6: el soporte de "MiB" no está verificado en este
   repo); plan B: convertir a bytes en `.env` (1048576) pero NUNCA tocar el
   literal de `.env.example` sin ancla (es literal del TRD §6.3).
10. **NUEVA** — Aplicación del 413 en el guard: solo a las 2 rutas POST del
    matcher `/api/v1/ingest/**`, o a toda sub-ruta (incluso rutas
    inexistentes)? Y: ¿exigir/gateway a Content-Type `application/json` o
    tolerarlo (los docs no lo fijan)? Y ¿el límite de tamaño aplica TAMBIÉN a
    la respuesta 202? (R: no — solo petición).
11. **NUEVA** — Interacción de límites: batch de 200 registros por máximo
    32 KiB de message cada uno ⇒ un lote teórico pasa 6 MiB de message pero el
    1 MiB total lo corta antes (¿el 413 es la única respuesta posible, o la
    validación de `batch_too_large` por registro antes del 413? — el orden
    del Schema: 413 del tamaño ANTES de todo, y el `batch_too_large` es 400 a
    nivel registros, no bytes: se debe reafirmar que 413 no se
    "re-especializa" en 400).
12. **NUEVA** — Desglose exacto de tests (número/nombre, parejas [L]/[S]
    del qa-plan — patrón C4) y del guion del smoke (pasos curl + psql con los
    patrones R-7 heredados: epoch contextuales, recomposición única
    `--force-recreate`, smoke-of-the-smoke, needles sin `_` de `plk_` sin
    escape o con `position()` — S-3 de C4).
13. **NUEVA** — Nombres finales de ficheros (el proposal usa
    `IngestGuardFilter`/`IngestAuthenticationEntryPoint`/`IngestPrincipal` —
    los nombres EXACTOS los fija el spec con Javadoc, patrón "los nombres
    finales los fija el spec").

## 9. Riesgos del corte

| ID | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| R-1 | 413 solo por `Content-Length`: un cliente chunked sin header evade el límite (cliente defectuoso, sin rate limit — ADR-26) | Medio | Doble barrera en el guard (Punto 2): Content-Length primera + `BoundedInputStream` segunda; smoke chunked E5.28 |
| R-2 | Mapeo bytea en la LECTURA (`WHERE api_key_hash = ?` con `byte[]`): C4 probó la escritura (E4.19), no la dirección de consulta | Bajo-Medio | el driver mapea `byte[]`↔`bytea` nativo (`setBytes`/`setObject`); E5.21/E5.24 lo certifican E2E (key real → 202, falsificada → 401) |
| R-3 | El nuevo literal `api_key_invalid` contamina el PANEL (`invalid_token`, CD-4, contrato C2) si el entry point se comparte | Alto (rompería C2) | Entry point NUEVO SOLO en `ingestChain`; cadenas 2/3 byte-a-byte intactas; pin E5.16 + `PanelAuthMvcTest` re-corrido (ya espera `invalid_token`) |
| R-4 | INSERT JSONB vía `JdbcTemplate`: `String` → jsonb sin cast explícito falla ("column "metadata" is of type jsonb but expression is of type character varying") | Medio | `CAST(? AS jsonb)` con el String serializado (pin SQL E5.15); smoke aserta lectura `metadata->>'span'` (E5.30) |
| R-5 | `received_at` NO idéntico en el lote (regla 9) si se fijara por fila con `now()` por sentencia | Medio | fijarlo UNA VEZ en Java, común al lote (§2.3); smoke aserta igualdad EXACTA entre las 200 filas (E5.22) |
| R-6 | `.env.example` trae `INGEST_MAX_REQUEST_BYTES=1MiB`: el bind a `long` fallaría; el parse de sufijos "MiB" a `DataSize` NO está verificado en este repo | Bajo | Punto 2/§8.9: verificar el bind (prueba unitaria de config); plan B si falla: valor en bytes en `.env` real SIN tocar el literal `.env.example` sin ancla |
| R-7 | Unidades de límites de campo: bytes (KiB del Schema) vs caracteres — medir MAL rompe el literal | Medio | Punto 2: bytes UTF-8 32768/131072/65536 para message/exception/metadata; caracteres para source/correlation_id (Schema §3.4 "caracteres" explícito); smoke con fronteras exactas 32768/32769 |
| R-8 | Humo cruzado con el smoke de C4: E4.15/E4.28 sondean `/me` y rutas del PANEL con `Authorization` (401 `invalid_token`) — NO tocan `/api/v1/ingest`; el cambio de la cadena 1 NO rompe la re-corrida de C4 | Bajo | verificado en `qa_c4_smoke.sh` (explore §2); el smoke de C5 re-corre la de C4 en su canónica (solo pila C5) |
| R-9 | Orden de errores 400 dentro de un registro/lote NO determinista (dos corridas ante el mismo lote devuelven códigos distintos) | Bajo | orden de checks por campo FIJO y primer-fallo-gana (patrón C3); en batch, por orden de array con `index` del primer culpable |
| R-10 | `timestamp` con offset pero formato laxo / fuera de rango | Bajo | `OffsetDateTime.parse` (ISO por defecto, estricto); smoke con `+02:00` real y con naive; verificación psql del UTC (E5.18/E5.29) |
| R-11 | Scope creep de la ingesta: validaciones extra/reintentos/rate-limit 429 (ADR-26 dice NO) o emisión WS adelantada | Medio | NO-alcanza (§3); el seam E1 acotado (1 interfaz, Punto 5) |
| R-12 (nuevo) | Respuesta 413 enviada SIN leer el cuerpo: algunos clientes/curl verían "connection reset" en vez del 413 (Tomcat aborta la conexión al descartar el cuerpo) | Bajo-Medio | el guard responde 413 y el filtro NO lee el resto — si curl lo manifiesta, el spec decide (leer-y-descartar acotado por BoundedInputStream); E5.27 usa el patrón curl estándar, y el E5.28 chunked cubre el caso peor |
| R-13 (nuevo) | Interacción de límites: 200 registros × 32 KiB ≫ 1 MiB — el límite de peticiones corta ANTES que el de registro: ¿400 `field_too_long` o 413? | Bajo | orden contractual del Schema §3.4: 413 del tamaño (antes de parsear) → luego toda la validación 400; E5.11/E5.27 lo dejan determinista |
| R-14 (nuevo) | Jackson parsing: cuerpos con campos desconocidos o no-JSON (no cubiertos por el enum de códigos §3.4) podían romper el anti-oráculo | Bajo | decisión del spec (§8.8): fail o ignore fijado, un único code de parseo a fijar; smoke cubre cuerpo no-JSON |
| R-15 (nuevo) | El smoke de C5 contra una pila cuyo `pgdata` conserva proyectos/keys de C4: asertos de conteo absoluto frágiles (ruido del volumen) | Bajo | patrón C4 heredado: conteo RELATIVO (count antes vs después en cada escenario) y nombres de proyecto con sufijo por ejecución (idéntico patrón E4.x) |