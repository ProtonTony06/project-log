---
change: C6
artifact: explore
last_updated: 2026-10-25
status: draft
---

# Explore — C6 · Consulta keyset + filtros + detalle

> Fase sdd-explore. Fuentes (solo lectura, congeladas): **Schema**
> `docs/05_BACKEND_SCHEMA.md` §3.2 (tabla de endpoints), §3.3 (DTOs y
> contrato de `GET /logs` con el SQL de keyset LITERAL), §2 (`logs` + Índices
> con justificación por filtro), §4 (reglas 9, 13; §4.1 límites),
> §6.3 (valido v1.0), **TRD** `docs/02_TRD.md` §2.4 (índices y rendimiento,
> keyset), §2.7 (límites), ADR-7/ADR-8/ADR-10 (valido v1.1),
> **AppFlow** `docs/03_APPFLOW.md` flujo 4 (consulta/filtros/paginación),
> flujo 5 (detalle), flujo 6b (llegada en vivo: dedup por id), §4
> (estados de pantalla), §5 (manejo de errores; solo lectura, valido v1.0),
> **PRD** `docs/01_PRD.md` §4 P0-6 (valido v1.2), **07_SLICING_PLAN.md**
> ficha C6.
>
> NOTA DE NOMENCLATURA (C5 ya la corrigió y aquí se ratifica): los ficheros
> REALES congelados son `docs/05_BACKEND_SCHEMA.md` y `docs/03_APPFLOW.md`
> (no `03_SCHEMA.md`/`04_APPFLOW.md`). OTRAS DOS discrepancias de este
> encargo corregidas CONTRA EL SCHEMA (el Schema manda): (1) los params de
> rango NO se llaman `date_from`/`date_to` — el literal es `from?`/`to?`; el
> filtro de texto NO se llama `q` — el literal es `text?` (§3.3, verbatim
> más abajo). (2) El encargo enumeraba "columnas REALES de logs" sin
> `project_id` ni `source`: V1 y la BD viva (verificada abajo) las tienen —
> `project_id` y `source` EXISTEN y aparecen en `LogListItemDto`.
>
> Código real inspeccionado SIN modificar nada:
> `backend/src/main/java/com/projectlog/{query,projects,ingest,keys,auth,
> config,streaming,common}`, `backend/src/main/resources/db/migration/
> V1__init.sql`, `backend/pom.xml`, tests patrón
> (`ProjectsControllerMvcTest`, `LogsRepoSqlTest`), `scripts/qa_c5_smoke.sh`
> (solo lectura) y `compose.yaml`. Pila viva inspectable: `podman compose ps`
> → 3×healthy (up ~2 h); **psql \d logs de LECTURA verificado contra la pila
> real** (1877 filas heredadas de los smokes de C5, sufijo-nombradas).

---

## 1. Resumen del corte

La ficha C6 de `07_SLICING_PLAN.md` pide: **"GET /logs con filtros
combinables (SQL literal del Schema §3.3, cursor keyset, LIMIT+1 →
`{items, next_cursor?}`), GET /logs/{id} (LogDto completo con
exception/metadata; la lista va SIN esos dos campos); 400
`filter_from_to_inverted` y 404 según AppFlow §5"**. Done: **"keyset
paginado estable (insertar logs nuevos entre páginas NO duplica ni salta),
filtros combinados y detalle con smoke"**.

Ambos endpoints son de la superficie del panel: los paths EXACTOS del
Schema §3.2 son `GET /api/v1/panel/logs` y `GET /api/v1/panel/logs/{id}`
(NUNCA `/api/v1/logs` — la ingesta vive aparte en `/api/v1/ingest/**` y no
tiene lectura). Autenticación JWT (cadena 2 ya la cubre: `securityMatcher
("/api/v1/panel/**")` en `SecurityConfig.panelChain` — **cero líneas de
seguridad tocadas**, S19: "C3–C6 nacen behind 401").

### Literales de contrato fijados (citan 05/02/03/01; C6 no los redefine)

- **Query params EXACTOS** (Schema §3.3): `project_id?`, `environment?`,
  `level?`, `from?`, `to?`, `text?` (todos combinables), `cursor?`,
  `limit?` — **defecto 50, máx. 200** (§3.3 y §4.1).
- **Rango temporal**: `from`/`to` en **ISO-8601 con offset** (normalizado a
  UTC); filtra sobre **`received_at`** (§4 regla 9). Con el SQL literal de
  §3.3, el rango es **INCLUSIVE por ambos extremos**:
  `received_at >= :from_ts` y `received_at <= :to_ts`.
- **Respuesta del listado**: 200 `{"items": [...], "next_cursor": "…"}` con
  `next_cursor: null` = fin de resultados.
- **LogListItemDto** (cada fila de `items`) — 9 campos sin `exception` ni
  `metadata`: `id, project_id, level, environment, timestamp, received_at,
  message, source, correlation_id`. **El nombre del proyecto NO viaja en la
  fila**: el panel ya cargó `GET /projects` y mapea por `project_id`
  (AppFlow flujo 4 paso 3). Fechas ISO-8601 UTC con `Z` (§3.1).
- **LogDto** (detalle `GET /logs/{id}`): los campos del listado **más**
  `exception` (string o `null`) y `metadata` (objeto JSON o `null`).
  404 → `{"code": "not_found"}`.
- **Error codes 400 del endpoint** (§3.3, todos con ancla — NINGUNO se
  inventa): `filter_from_to_inverted` (`from > to`),
  `filter_invalid_value` (`environment`/`level` fuera de enum),
  `filter_limit_invalid` (`limit` fuera de 1..200), `filter_cursor_invalid`
  (cursor ilegible). Cuerpo estándar `{code, message, field?}` (§3.1);
  AppFlow flujo 4 paso 6 y §5: el panel conserva la última lista válida.
- **Texto**: "`text` se recibe sin comodines y se busca como `%<text>%`
  (escapando `%`, `_` y `\`)" con `ILIKE` (SQL literal §3.3). Semántica
  EXACTAMENTE igual que los filtros del stream (§3.5, "set_filters" — ADR-7).
- **Keyset** (§3.3 + TRD §2.4, §4 regla 13, ADR-10): orden
  `ORDER BY received_at DESC, id DESC`; **cursor opaco** para el cliente
  que internamente codifica `(received_at, id)` en **Base64url**;
  `LIMIT :limit + 1` — la fila de más decide si hay `next_cursor`.
  Offset descartado (ADR-10).
- **Estabilidad** (regla 13): páginas estables aunque la ingesta siga
  entrando; un mismo batch comparte un solo `received_at` y el lote queda
  desempatado por `id` ASC **en inserción** (regla 9) — coherente con leerlo
  `id DESC` en la página (entre filas del MISMO `received_at`, el orden
  invertido visible es determinista y estable; AppFlow flujo 6b-3 lo
  respeta también en el cliente).
- **Vocabulario de errores ya APROBADO en código** (C3, `ProjectsController`,
  reutilizable por referencia): `filter_invalid_value` y `not_found` existen
  hoy; `filter_from_to_inverted`, `filter_limit_invalid` y
  `filter_cursor_invalid` son NUEVOS en el vocabulario pero CON ancla en
  Schema §3.3 (patrón C5 igual: los literales heredados se reutilizan, y los
  nuevos citan su ancla del congelado).
- **PRD P0-6** (consulta desde el panel): filtros por nivel, rango temporal
  y texto del mensaje, filtrado por proyecto + entorno (combinables);
  paginado; vista de detalle con excepción, metadata y contexto. Proyectos
  ARCHIVED: sus logs **siguen consultables** (§4 regla 12).

**Descartado por encargo corregido contra el Schema**: `date_from`/`date_to`
y `q` NO existen literales — usar `from`/`to`/`text` tal cual §3.3.

## 2. Mapa del código relevante (estado hoy, HEAD 43ea041, todo pushed)

| Pieza | Estado respecto a C6 |
|---|---|
| `backend/src/main/java/com/projectlog/query/package-info.java` | ÚNICO fichero del paquete `query`: esqueleto C1 — "Feature de consulta (C6): paginación keyset del dashboard y detalle de log". **Aquí nace todo lo del corte.** |
| `.../ingest/LogsRepository.java` | INSERT-only (javadoc C5 explícito: "las LECTURAS nacen en C6 en su corte"). **No tocarlo para lecturas**: su frontera es "ÚNICA fuente de SQL sobre logs PARA INSERTS". |
| `.../ingest/LogValidado.java` | Record moneda del INSERT (C5). C6 NO lo reutiliza para lectura: sirve para escribir, no para leer filas. |
| `.../projects/repository/ProjectsRepository.java` | Patrón a replicar: `COLS` única fuente de columnas, RowMapper `FILA` único, `JdbcTemplate` con SQL inline pin-testable, `@Transactional` como defensa documentada, `Optional` por 404, sin `queryForObject` cuando la fila-0 es caso legal. |
| `.../projects/controller/ProjectsController.java` | Patrón a replicar: controller fino SIN bean validation (`@Valid` sería no-op — no hay starter-validation), validación manual fail-fast con literal de error, `error(...)` helper con cuerpo `{code, message, field?}`, `filter_invalid_value` para query fuera de enum, 404 `not_found` para id inexistente. |
| `.../projects/dto/ProjectDto.java` | Patrón DTO: record con componentes snake_case LITERALES (Jackson serializa el accessor con su nombre sin `@JsonProperty`). |
| `.../config/SecurityConfig.java` | Cadena 2 ya cubre `/api/v1/panel/**` → **`/api/v1/panel/logs[/...]` nace autenticada sin toque**. Cadena 3 no cambia. NOTA (C5, ratificada): `@WebMvcTest` solo incluye los `Filter` (el `JwtAuthFilter` existe); los pattern de test C3/C5 importan la cadena completa. |
| `backend/src/main/resources/db/migration/V1__init.sql` | INTOCABLE. Índices de logs ya presentes (ver §e). Sin columna `is_deleted`/soft-delete (no aplica). |
| `backend/pom.xml` | Dependencias: web, jdbc, actuator, websocket, flyway + postgres driver, security, bouncycastle, jjwt, starter-test. **Sin starter-validation, SIN Testcontainers** (ver §f). C6 idealmente NO añade ninguna dependencia. |
| `backend/src/test/java/...` | Patrón [L]: `ProjectsControllerMvcTest` (`@WebMvcTest` + cadena 2 importada + repo mockeado + JWT real de test con `TokenService` local, `@ActiveProfiles("test")`); `LogsRepoSqlTest` (C5: pin del SQL como constantes package-private en el MISMO paquete, JUnit puro sin Spring ni BD). Hay 18 ficheros de test; 108/108 verdes en C5. |
| `scripts/qa_c5_smoke.sh` | Patrón [S] a replicar: recomposición ÚNICA `podman compose up -d --force-recreate` (NUNCA `down -v` — pgdata), psql `-qAt`, conteos RELATIVOS antes/después, nombres sufijados por ejecución (`smoke-c6-${TS}-a`), tmpdir 700 con trap, higiene de key/JWT (jamás a consola), smoke-of-the-smoke antes de la canónica, exit codes 0/1/2. |
| `compose.yaml` | postgres:17-alpine + pgdata, backend interno, frontend único puerto 8080 (host). Sin cambios para C6 (nginx `client_max_body_size` es deuda de C9). |
| `.atl/sdd/{C2,C3,C4,C5}/*` | Insumos y deudas heredadas: S-3 LIKE-underscore (ver §b), R-nuevo-6 (mvn dentro del builder sin docker), smoke docker compose canónico → C9, 401 ISO-8859-1 → C8/C9. |

## 3. Análisis de opciones (lo nuevo)

### a) Paginación KEYSET — columna de orden, cursor, LIMIT+1

**Columna de orden: fijada por contrato** — `received_at DESC, id DESC`
(Schema §3.3 SQL literal, §2 logs "received_at: columna canónica de orden,
rango temporal y paginación keyset", §4 reglas 9/13, TRD §2.4, ADR-10).
`id` es el tiebreaker exacto del índice maestro. No hay espacio de
decisión aquí — el cursor y el SQL se citan, no se diseñan.

**SQL de implementación: el LITERAL de §3.3** (patrón fijo con
`(:x IS NULL OR ...)`, la "única sentencia pin-testable" del corte):

```sql
SELECT id, project_id, level, environment, timestamp, received_at,
       message, source, correlation_id
FROM logs
WHERE (:project_id IS NULL OR project_id = :project_id)
  AND (:environment IS NULL OR environment = :environment)
  AND (:level IS NULL OR level = :level)
  AND (:from_ts IS NULL OR received_at >= :from_ts)
  AND (:to_ts IS NULL OR received_at <= :to_ts)
  AND (:text IS NULL OR message ILIKE :text)
  AND (received_at < :cursor_r OR (received_at = :cursor_r AND id < :cursor_i))
ORDER BY received_at DESC, id DESC
LIMIT :limit + 1;
```

(Opciones en tabla:)

| Opción | Diseño | Pros | Contras | Veredicto |
|---|---|---|---|---|
| E1 (RECOMENDADA) | Sentencia ÚNICA y fija con el patrón `(:x IS NULL OR ...)` del Schema; parámetros siempre presentes, NULL cuando no hay filtro; cursor → 2 parámetros (`:cursor_r`/`:cursor_i`) | Literal EXACTO del congelado (pin-testable con `LogsRepoSqlTest`-style); cero SQL dinámico (convención C3: "nunca combina SQL dinámico por filtros"); un solo estamento → sin ramas a probar por combinación; el planner PG maneja los OR con parámetros NULL estáticos por plan genérico | Planner algo menos óptimo que SQL dinámico por combinación de filtros — irrelevante a volúmenes del homelab (justificado en §2 Índices del Schema) | ELEGIR E1. E2 (montaje dinámico de WHERE por filtros presentes) descartada: rompe la convención C3, no hay literal congelado que pinar y multiplica los combos de validación |

**Formato del cursor: Fijado por contrato — opaco, `(received_at, id)` en
Base64url** (Schema §3.3). El ejemplo del Schema decodificable así:

```
next_cursor: eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0
→ (Base64url) {"r":"2026-10-16T22:31:04Z","i":12301}
```

| Sub-opción del cursor | Pros | Contras | Veredicto |
|---|---|---|---|
| C1 (RECOMENDADA): `{"r": "<ISO-8601 UTC>", "i": <id>}` serializado JSON + `Base64.getUrlEncoder().withoutPadding()` | Coincide byte a byte con el ejemplo del Schema; codec puro (unit test sin BD, sin Spring); el cliente lo trata como token opaco (nunca lo parsea — literal). | Internamente legible si a alguien se le ocurre intentarlo — irrelevante: es interno y el JWT de quien lo pidiera es el mismo que ve el log. | ELEGIR C1 |
| C2: cursor simple de solo `id` | Más corto | FALSIFICADO contra el SQL literal: el keyset compara TAMBIÉN `received_at < :cursor_r` — un cursor de solo id no alimenta esa comparación; además el índice del dashboard no arranca por id | DESCARTADA |

**⚠ PUNTO CRÍTICO DE PRECISIÓN (hallazgo de este explore — para design/
propose):** el formateador de fechas vigente (`LoginController
.EXPIRES_AT_FORMATTER`, patrón `yyyy-MM-dd'T'HH:mm:ss'Z'`) TRUNCA las
fracciones de segundo. PG `TIMESTAMPTZ` tiene precisión de MICROSEGUNDOS y
`now()` del servidor trae micros reales (los smokes de C5 insertaron 1877
filas con `now()`). **Truncar el `received_at` al codificar el cursor
SALTA filas**: las filas con el mismo `received_at` real que la última de la
página (todo un batch comparte recibido — regla 9) dejarían de cumplir
`received_at = :cursor_r` (su instante real > el truncado) y tampoco
cumplicen `received_at < :cursor_r` → **desaparecerían entre páginas**,
rompiendo literalmente el Done. Decisión RECOMENDADA para el codec: la
clave `r` del cursor debe llevar el instante SIN pérdida (p. ej.
`Instant.toString()` de la columna leída — ISO-8601 con microsegundos
automáticos cuando existen), y en pantalla el LogListItemDto sigue con el
formatter Z de C2 (la precisión del RENDER es otra cosa: el DTO de salida
puede usar el formatter vigente; el cursor NO). El smoke debe cubrir
explícitamente un batch (mismo `received_at`) que SEPARA las páginas.

**LIMIT+1 → trim**: se piden `limit + 1`; si viene la fila de más, hay
`next_cursor` (codificado con `(received_at, id)` de la ÚLTIMA fila
ENTREGADA) y `items` queda recortado a `limit`; si no viene, `next_cursor:
null`. La primera página (sin `cursor` y sin 2 parámetros de cursor) es el
mismo estamento con `:cursor_r`/`:cursor_i` NULL → la rama del keyset no
filtra. `next_cursor` NUNCA presente con la última página vacía del
recorrido.

**Estabilidad (la prueba del Done)**: con `received_at DESC, id DESC`
sobre índice que fija AMBAS claves, insertar logs NUEVOS entre página 1 y
página 2 no recoloca a los ya entregados (su posición relativa no cambia)
y el cursor no depende de filas no-materializadas (offset descartado —
ADR-10). La prueba del smoke: página 1 → ingesta batch de K logs nuevos →
página 2 con el cursor de la página 1 → asertos: ningún id repetido entre
páginas, ningún id de la página 1 reaparece, la primera fila de la página 2
es EXACTAMENTE la fila siguiente al cursor (los recién llegados solo
aparecen al volver a página 1 o en el stream de C7).

### b) Filtros combinables — S-3 LIKE-underscore HEREDADA

- `project_id`, `environment`, `level`: igualdad estricta, sin
  normalización de minúsculas (patrón C3 "minúsculas no se normalizan —
  CHECK literal"). `environment` ∈ {development, staging, production};
  `level` ∈ {TRACE, DEBUG, INFO, WARN, ERROR, FATAL} (CHECK de V1 + §4
  regla 10). Valor fuera de enum → 400 `filter_invalid_value` con su
  `field` (corte S-9/S3.1 C3 fail-fast).
- `from`/`to`: ISO-8601 CON offset (AppFlow flujo 4 paso 2 — el cliente
  envía desde datetime-local con offset; el backend normaliza a UTC al
  comparar con `received_at` TIMESTAMPTZ). INCLUSIVE ambos (>= / <=, literal
  SQL §3.3). `from > to` → 400 `filter_from_to_inverted`.
- `text`: sin comodines del cliente; el backend construye `%<text>%` con
  ESCAPE de `%`, `_` y `\` (§3.3 literal).

**S-3 LIKE-underscore (heredada de C4, decisión aquí)**: en C4 el único
LIKE de un smoke usaba una aguja con `_` que operaba como comodín
(`.atl/sdd/C4/archive-report.md` S-3). C6 TIENE filtro de texto en la BD
(no solo en un psql del smoke). Decisión RECOMENDADA: (1) el filtro del
endpoint escapa SIEMPRE `%`, `_` y `\` con `\` (el escape default de
LIKE/ILIKE en PG) — literal del Schema §3.3, así que es de contrato, no
elección; el código NO debe aceptar ningún comodín del cliente;
(2) el SMOKE usa una aguja con `_` real (p. ej. mensaje
`smoke_c6_timeout_e` insertado por ingesta) y un mensaje que contenga
literal `_` para afirmar que coincide por igualdad, más uno sin `_` con
prefijo igual para afirmar que NO se coló como comodín; (3) en los asertos
psql del smoke NUNCA pasar la aguja cruda a un `LIKE` psql sin escapar
(lección S-3): si el psql compara algo patrónico, escapar en el script. Se
ANOTA para el gate: S-3 se cierra EN C6 (el primer filter de texto real),
la deuda del smoke queda referenciada.

### c) 400 `invalid_value` de filtros — literales EXACTOS con ancla

Con ancla directa en Schema §3.3 (cero inventos):

| Caso | code | field | Ancla |
|---|---|---|---|
| `environment` fuera de enum | `filter_invalid_value` | environment | Schema §3.3 |
| `level` fuera de enum | `filter_invalid_value` | level | Schema §3.3 |
| `from` > `to` | `filter_from_to_inverted` | — (o from) | Schema §3.3 |
| `limit` fuera de 1..200 | `filter_limit_invalid` | limit | Schema §3.3 / §4.1 |
| cursor ilegible | `filter_cursor_invalid` | cursor | Schema §3.3 |
| id inexistente en detalle | `not_found` (404) | — | Schema §3.3 / AppFlow flujo 5 paso 6 |

**Tres casos SIN ancla literal textual** (para propose y GATE, propuesta —
no invento aún): (1) `project_id` no-numérico → RECOMENDADO
`filter_invalid_value` con `field: "project_id"` (el literal y el field
existen), reutilizando el vocabulario vigente; (2) `from`/`to` presente
pero NO parseable ISO-8601 con offset → RECOMENDADO `filter_invalid_value`
con `field: "from"`/`"to"` (mismo argumento: es "valor inválido del
filtro"); (3) `limit` no-numérico → RECOMENDADO `filter_limit_invalid` con
`field: "limit"`. `text` NO tiene límite de tamaño declarado en el
congelado — NO inventar cap (pregunta abierta, §6).

**Orden de evaluación FAIL-FAST (determinista, primer fallo gana)**: el
congelado no fija el orden de los 400 en `GET /logs` (en ingesta, C5 sí lo
fijó). Propuesta RECOMENDADA para propose (a validar en gate): un orden
fijo y determinista con el patrón C3/C5 — `limit` → `project_id` →
`environment` → `level` → `from`/`to` parse → `from > to` → `cursor`,
TODOS antes de tocar la BD y con `verifyNoInteractions` en el mock de test
(patrz C3 "el enum ANTES de la BD — regla 10").

### d) DTOs — records con snake_case (lista vs detalle)

| Opción | Diseño | Pros | Contras | Veredicto |
|---|---|---|---|---|
| D1 (RECOMENDADA) | DOS records: `LogListItemDto` (9 campos, exactos de §3.3) y `LogDto` (11 campos: los 9 + `exception` + `metadata`) | Literal EXACTO del congelado (nombres `LogListItemDto`/`LogDto` con las reglas del texto); la LISTA no puede transportar exception/metadata ni accidentalmente (protección estructural, no por orden de build); payload de lista liviano (métrica 3 del PRD / TRD §2.4) | Dos clases casi gemelas | ELEGIR D1 — el congelado les da nombre y reglas distintas; un solo record con campos nullable violaría literal y permitiría fuga de campos |
| D2 descartada | UN record con los 11 y exclusión de campos en el RowMapper de lista | Menos código | El JSON de lista llevaría nulls si el record no se recorta, o exigiría `@JsonInclude` por vista — mezcla de contratos en 1 clase; el congelado NOMBRA 2 DTOs | DESCARTADA |

`metadata` (JSONB) en el detalle: la fila llega como `PGobject`/String →
`ObjectMapper.readValue(... JsonNode)` en el RowMapper y el record lleva
`JsonNode metadata` (null si la columna es null) — Jackson serializa el
nodo tal cual (objeto exacto, sin re-interpretación). Patrón C3 de
fechas: el repo formatea con el formatter de C2 reutilizado por
referencia (`LoginController.EXPIRES_AT_FORMATTER`) y el controller nunca
toca fechas; el codec del cursor es puro y testea fuera de Spring.
`timestamp`/`received_at` viajan ya formateados en `LogListItemDto`/`LogDto`
(como en `ProjectDto`) y ADEMÁS SIN truncar en el componente interno que
alimenta el cursor (ver §a).

### e) Índices: ¿bastan para el keyset?

**V1__init.sql leído Y verificado contra la pila viva con `\d logs` de
solo-lectura** (podman exec psql): `logs_pkey` (id),
`logs_dashboard_idx` btree `(project_id, environment, level, received_at
DESC, id DESC)`, `logs_received_idx` btree `(received_at DESC)`,
`logs_correlation_idx` parcial, `projects_api_key_hash_idx` único parcial.

- **Con filtro de proyecto** (la ruta del dashboard): `logs_dashboard_idx`
  es un cascarón EXACTO del filtro `project_id (+environment, +level)` y del
  orden keyset — el Schema lo llama "el índice maestro del dashboard" y
  "sin sort, coste plano por página": índice-scan descendiente directo,
  SIN sort. SUFFICIENTE, tal cual diseño del congelado.
- **Sin filtro de proyecto**: `logs_received_idx` es monocolunar — resuelve
  `received_at DESC` y la comparación `received_at < :cursor_r`, pero el
  desempate por `id DESC` lo saca el planner con incremental sort (PG ≥ 13)
  o el scan es de tamaño acotado (los lotes comparten `received_at` — regla
  9 — así que los grupos de iguales son ≤ tamaño de batch). A volúmenes del
  homelab es aceptable y el CONGELADO lo preveía ("Los filtros
  level/environment sin proyecto degradan a logs_received_idx + scan
  filtrado — aceptable en v1").
- **VEREDICTO (anota claramente para el gate)**: NO FALTA NINGÚN ÍNDICE para
  C6. No se propone migración ni índice nuevo (V1 INTOCABLE por encargo y
  por el gate 2026-10-19). El índice trigram GIN de `message`
  (`V2__add_message_trigram.sql`) está COMENTADO y reservado en V1: se deja
  documentado, NO se activa en C6 (opción futura cuando el volumen lo
  justifique).

### f) Testcontainers — reevaluación EXPLÍCITA

**Recomendación: NO Testcontainers en C6 (ratificar la lógica C5),
reforzar el patrón vigente con la prueba del Done en el smoke.**

- El argumento honesto A FAVOR: C6 es el corte donde la BD real más
  importa hasta ahora (keyset con cursor de 2 claves, ILIKE con escape,
  rango inclusive sobre TIMESTAMPTZ, jsonb en el detalle, estabilidad ante
  inserts paralelos, 1877 filas ya presentes para presionar el índice).
  Con Testcontainers todo eso correría en `mvn test` sin pila viva.
- El argumento del PATRÓN VIGENTE y las deudas: el smoke [S] SOBRE LA PILA
  REAL YA demuestra esto — C5 lo hizo con `CAST(? AS jsonb)` y
  `received_at` común al lote, C4 con bytea y un índice único parcial; la
  prueba del Done (§a) es 100% scriptable en el smoke (ingesta de pila +
  dos GET paginados + psql), la pila ya está UP 3×healthy y el patrón es
  canónico en este repo. Añadir Testcontainers implica (C5, R-nuevo-6
  heredado): 2 dependencias nuevas en pom, pull de imagen por entorno,
  contextos Spring más pesados, y probar docker-in-`mvn` DENTRO de la
  imagen builder `maven:3.9-eclipse-temurin-21` — no está demostrado en
  esta VM y el builder corre los tests en build, no en pila.
- Refuerzos concretos que el corte C6 SÍ debe llevar (en lugar de TC):
  (1) escenario SMOKE de estabilidad keyset (Done literal, §a); (2)
  escenario SMOKE del filtro `text` con aguja `_`/`%` (cierra S-3 para el
  endpoint, §b); (3) unit puro del codec Base64url (sin BD, es puro); (4)
  unit pin del SQL literal + unit Mvc sobre controller con repo
  mockeado (patrón C3/C5 intacto); (5) smoke de detalle 404 y de
  exception/metadata SOLO en detalle.
- Reevaluación REAL queda apuntada a C7 (streaming WS — protocolo real)
  donde el beneficio de BD+socket real en `mvn` podría pesar más.

### g) Seam C7/streaming y separación de lecturas (NO tocar: streaming, resumen, DELETE)

- **Lecturas**: NACEN en `com.projectlog.query` (package-by-feature),
  replicando la arquitectura de projects (controller/dto/repository). El
  INSERT de logs sigue en `ingest`; su javadoc ya marca la frontera. Se
  recomienda un `LogsQueryRepository` NUEVO en query (nada se añade al
  repo de ingest que es de escritura — frontera documentada en C5). Los
  404/400 de la lista y del detalle nacen tras la cadena 2 (S19).
- **Seam C7**: el congelado dice que los filtros del stream tienen
  "exactamente la misma semántica" que los query params de `GET /logs`
  (§3.5) — RECOMENDACIÓN de propose: la VALIDACIÓN+normalización de
  filtros (parse/escape/upper) vive en query como lógica de paquete
  reutilizable; C7 se suscribe a esa misma semántica sin duplicarla (ADR-7:
  coherencia vivo/listado; el LogDto completo del frame reutilizaría el
  record de query — precedente de reutilización cross-feature: el
  formatter de C2 y los estamentos de key compartidos con C4/C5).
- **C7 NO entra**: ni HandshakeInterceptor, ni LiveBroadcaster, ni frames
  (C7), ni `include=summary` ni `GET /projects/{id}/summary` (C10),
  **ni NINGÚN DELETE** — `DELETE /logs/{id}` (C10), purga
  `/projects/{id}/logs` (C10) y cascade del proyecto (C10) son P1: la
  ficha C6 no los menciona y el congelado los lista para flujo 8. El
  panel frontend `/logs` (C8) no entra — C6 solo expone la REST.
- El smoke de C5 dejó el seam IngestListener tras COMMIT; C6 NO lo
  conecta a nada (el listener queda sin beans suscriptos — C7 decide).

## 4. Riesgos del corte

| R-ID | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| **R-6.a** | cursor con `received_at` truncado a segundos SAlTA filas del mismo instante (batches con `received_at` común, regla 9) — rompe el Done literal | CRITICO | Codec del cursor SIN pérdida: codificar el instante con TODOS los dígitos que produzca PG (p. ej. `Instant.toString()` con micros); smoke con un batch que separe páginas; unit del codec |
| **R-6.b** | comodines del cliente (`%`, `_`) actúan como wildcard en ILIKE (S-3 C4 se manifiesta EN PRODUCTO, no solo smoke) | ALTO | Escape server-side OBLIGATORIO `%`/`_`/`\` (literal §3.3); smoke con aguja `_` real y contra-caso de igualdad; anota cierre de S-3 para el gate |
| **R-6.c** | `project_id`/`limit` no-numéricos → Spring default 400 (cuerpo NO estándar, sin `{code,message,field}`) rompe el contrata del §3.1 | MEDIO | Parseo manual en controller (nunca binding directo a `long`), con `filter_invalid_value`/`filter_limit_invalid` RECOMENDADOS (a gate); test Mvc cubre |
| **R-6.d** | `cursor` presente con filtros DIFERENTES de los de la página 1 (cliente defectuoso o F5 con filtros cambiados) puede producir página semánticamente incoherente | BAJO (la UI nunca lo hace — AppFlow flujo 4 paso 2: "cursor reiniciado" con nuevos filtros) | No se rechaza: el cursor es opaco y la semántica SQL es válida (continuidad desde `(r,i)`); documentar; alternativa de rechazo (tie del cursor al filtro por hash) es sobre-ingeniería — pregunta abierta §6 |
| **R-6.e** | keyset sin filtro de proyecto con `logs_received_idx` monocolunar requiere sort incremental para `id DESC` | BAJO (volumen homelab; el congelado lo preveía) | Aceptado v1; NO migración; se deja observado (si smokes muestran degradation real, anotarlo como deuda de índice para C9/C10 — NO C6) |
| **R-6.f** | keyset/filtros validados en unit con repo MOCKEAADO → si el estamento real falla, el fallo es tarde (en smoke) | MEDIO | Pin del SQL literal (patrón `LogsRepoSqlTest`), smoke-of-the-smoke antes de canónica, higiene tmpdir 700 R-4.13 |
| **R-6.g** | DTO de lista SIN `exception`/`metadata` violado accidentalmente (fuga de payload de KiB por página) | ALTO (métrica 3 del PRD) | D1: 2 records estructuralmente distintos; pin test del DTO de lista de 9 componentes exactos |
| **R-6.h** | pila viva + 1877 filas de smokes previos: los conteos absolutos psql romperían el smoke | BAJO (patrón ya resuelto en C5) | contens RELATIVOS antes/después, nombres sufijados por ejecución, recomposición `--force-recreate` NUNCA `down -v` |

## 5. NO es C6 (fuera de alcance — NO-alcanza)

- **Streaming WebSocket `/ws/logs`** (HandshakeInterceptor,
  LiveBroadcaster, frames `batch`, heartbeat, backoff) → **C7**. C6 solo
  FIJA la semántica de filtros compartida (ADR-7) y el seam de lecturas.
- **`DELETE /logs/{id}`, `DELETE /projects/{id}/logs`, cascade con
  `X-Confirm-*`** → **C10** (P1 del PRD, flujo 8).
- **Resumen `/projects/{id}/summary` y `include=summary`** → **C10**.
- **Frontend del panel `/logs` UI (tabla, Cargar más, estados de pantalla
  §4 del AppFlow, query-string compartible)** → **C8**. C6 solo garantiza
  la REST que la UI del flujo 4 pide.
- **`nginx` proxy / `client_max_body_size` / 401 ISO-8859-1** → deuda de
  **C9** (o C8/C9); C6 no toca nginx ni compose.
- **Índice trigram GIN `V2__add_message_trigram.sql`** → reservado en V1
  (comentado), FUERA de C6 (opción futura según volumen).
- **Cualquier migración/`ALTER`** → INTOCABLE (gate 2026-10-19: V1 es el
  literal; los índices existentes BASTAN, ver §e).
- **Rate limiting / 429, backups, retención, multiusuario** → fuera de
  alcance de v1 (§7 del Schema).

## 6. Preguntas abiertas para sdd-propose C6

1. **Códigos 400 para los 3 casos sin literal textual exacto**
   (`project_id` no-numérico, `from`/`to` no parseable, `limit`
   no-numérico): reutilizar `filter_invalid_value`/`filter_limit_invalid`
   (RECOMENDACIÓN, ver §c) o proponer literal nuevo con ancla del gate.
2. **Orden fail-fast de los 400** de `GET /logs` (propuesta determinista
   §c; anclar igual que se fijó el orden del 413→401 en C5).
3. **Precisión del cursor** (R-6.a): fijar en design que la clave `r` del
   JSON cursor codifica el `received_at` SIN truncar (`Instant.toString()`
   del valor leído de la BD) mientras el DTO de pantalla usa el formatter Z
   de C2. Peso bajo, decisión clave.
4. **Texto**: ¿cap de longitud para `text`? El congelado NO declara
   límite → NO inventar: consultar (aunque el SQL lo soporta a cualquier
   longitud con volúmenes v1). Igual para `cursor` (el Base64url máximo es
   acotado por definición).
5. **Nombres de piezas nuevas** (proponer): `LogsController` (o
   `PanelLogsController`), `LogListItemDto`, `LogDto`,
   `LogsQueryRepository`, codec del cursor, sub-paquetes
   `query/{controller,dto,repository}` vs plano en `query` — el patrón
   projects (sub-paquetes) y el paquete `streaming` (plano) NO concuerdan
   entre sí: decidir.
6. **¿El codec del cursor acepta también base64 estándar (con padding) y sin
   JSON válido?** — RECOMENDADO no: un Base64url decodificado que no sea
   JSON válido o sin las claves `r`/`i` es `filter_cursor_invalid` (mismo
   literal); tolerar variantes suelta falsificación del contrato "opaco".