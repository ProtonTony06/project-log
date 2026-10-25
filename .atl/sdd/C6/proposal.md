---
change: C6
artifact: proposal
last_updated: 2026-10-25
status: approved
gate: "APROBADO por el usuario 2026-10-25 (6/6: P1 cursor opaco Base64url con micros/tolerancia estrecha filter_cursor_invalid, P2 filter_invalid_value/filter_limit_invalid sin codes nuevos, P3 escape ILIKE verbatim, P4 sin Testcontainers [reeval C7], P5 dos records, P6 orden fail-fast)"
---

# Proposal — C6 · Consulta keyset + filtros + detalle

> Fase sdd-propose. Se presenta al usuario como GATE (aprobación de los 6
> puntos de decisión de la §5) antes de pasar a spec/design/tasks/apply.
> Sin este aprobado no se escribe código de C6. Insumos: `explore.md` de C6
> (fuentes congeladas: Schema `docs/05_BACKEND_SCHEMA.md` §2/§3.2/§3.3/§4
> reglas 9/12/13/§4.1/§5, TRD `docs/02_TRD.md` §2.4/§2.7, AppFlow
> `docs/03_APPFLOW.md` flujos 4/5 + flujo 6b + §4/§5, PRD `docs/01_PRD.md`
> P0-6, `07_SLICING_PLAN.md` ficha C6), frontera C5 (LogsRepository
> INSERT-only + `find-keyhash`; las LECTURAS de logs nacen en C6),
> verify/archive-report de C4 (S-3 LIKE-underscore) y el código real
> verificado en HEAD 43ea041 (todo pushed).
>
> NOTA de nomenclatura heredada del explore: los ficheros REALES congelados
> son `docs/05_BACKEND_SCHEMA.md` y `docs/03_APPFLOW.md` (número = posición
> en /docs; los literales a continuación se citan VERBATIM de ellos).

## 1. Problema y motivación

C5 dejó la ingesta REAL: hoy la tabla `logs` acumula filas verdaderas (los
smokes de C5 insertaron 1877 filas heredadas en la pila viva, psql
verificado en el explore) — **y ningún endpoint las consulta**. El paquete
`com.projectlog.query` es solo un `package-info.java` esqueleto de C1
("Feature de consulta (C6): paginación keyset del dashboard y detalle de
log"). El flujo de valor del panel queda colgado justo aquí: sin
`GET /logs` el panel no puede diagnosticar nada, y el corte siguiente
real (C7, streaming) reutiliza exactamente la semántica de filtros de este
endpoint (§3.5 del Schema: "exactamente la misma semántica") — C6 fija esa
base o C7 duplica la discusión.

Es **el primer corte SOLO-LECTURA del proyecto**: la escritura de `logs`
vive en `ingest/LogsRepository` (INSERT-only por frontera C5, Javadoc
explícito: "las LECTURAS nacen en C6 en su corte") y el C6 crea sus
lecturas en `query/` con SU PROPIO repository — el repo de ingesta queda
byte-a-byte intacto. La ficha C6 del 07 lo define: "GET /logs con filtros
combinables (SQL literal del Schema §3.3, cursor keyset, LIMIT+1 →
`{items, next_cursor?}`), GET /logs/{id} (LogDto completo con
exception/metadata; la lista va SIN esos dos campos); 400
`filter_from_to_inverted` y 404 según AppFlow §5".

Ambos endpoints son superficie de PANEL: los paths EXACTOS del Schema
§3.2 son `GET /api/v1/panel/logs` y `GET /api/v1/panel/logs/{id}` (nunca
`/api/v1/logs` — la ingesta vive aparte). **La cadena 2 ya los cubre**:
`securityMatcher("/api/v1/panel/**")` en `SecurityConfig.panelChain`
(`@Order(2)`) — los endpoints NACEN autenticados sin tocar UNA línea de
seguridad (S19 heredada: "C3–C6 nacen behind 401").

**Por qué ahora el hallazgo crítico**: el formateador vigente
(`LoginController.EXPIRES_AT_FORMATTER`, patrón
`yyyy-MM-dd'T'HH:mm:ss'Z'`) TRUNCA las fracciones de segundo. PG
`TIMESTAMPTZ` guarda MICROSEGUNDOS reales (`now()` del servidor trae
micros; los 1877 filas de C5 lo materializan) y **todo un batch comparte
un solo `received_at`** (regla 9). Si el cursor codificara el instante
truncado a segundos, las filas del MISMO instante que la última fila de la
página dejarían de cumplir `received_at = :cursor_r` (su instante real > el
truncado) y tampoco `received_at < :cursor_r` → **desaparecerían entre
páginas** — rompería literalmente el Done. La decisión de precisión del
codec del cursor es el Punto 1 del gate (§5, P1).

## 2. Alcance propuesto

### 2.1 `GET /api/v1/panel/logs` — listado keyset con filtros combinables

Fila verbatim de Schema §3.2 (CONTRATO, cita literal):

> | GET | `/api/v1/panel/logs` | JWT | Listado keyset con filtros combinables | query (§3.3) → 200 `{items, next_cursor?}` · 400 · 401 |

**Query params EXACTOS** — verbatim de Schema §3.3: "`project_id?`,
`environment?`, `level?`, `from?`, `to?`, `text?` (todos combinables),
`cursor?`, `limit?` (defecto **50**, máx. **200**, TRD §2.7). Rango
`from`/`to` en ISO-8601 con offset (normalizado a UTC); filtra sobre
`received_at` (§4, regla 9)." Los nombres son los literales (los mismos de
los `filters` del stream, §3.5): NUNCA `date_from`/`date_to`/`q` (no
existen literales — explore, corregido y ratificado aquí).

- **`environment`** ∈ `development|staging|production`; **`level`** ∈
  `TRACE|DEBUG|INFO|WARN|ERROR|FATAL` — enum cerrado con CHECK de V1 y
  validación en el borde; case-sensitive EXACTO, sin normalización
  minúsculas (patrón C3: "Minúsculas no se normalizan (CHECK literal)").
  Fuera de enum → 400 `filter_invalid_value` con su `field` (con ancla).
- **`project_id`**: igualdad estricta; se PARSEA manualmente a `long`
  (R-6.c: binding directo a `long` produciría el 400 genérico de Spring,
  fuera del contrato `{code, message, field?}`).
- **`from`/`to`**: ISO-8601 CON offset (AppFlow flujo 4 paso 2: el cliente
  envía desde datetime-local con offset; el backend normaliza a UTC al
  comparar con `received_at` TIMESTAMPTZ). **INCLUSIVE por ambos extremos**
  (`received_at >= :from_ts` y `received_at <= :to_ts`, SQL literal §3.3).
  `from > to` → 400 `filter_from_to_inverted` (con ancla). Proyectos
  ARCHIVED: sus logs SIGUEN consultables (§4 regla 12; el filtro de texto y
  el rango ignoran el estado del proyecto — no hay filtro por status de
  proyecto en este endpoint).
- **`text`**: verbatim §3.3 — "«text» se recibe sin comodines y se busca
  como `%<text>%` (escapando `%`, `_` y `\`)" con `ILIKE`. El escape
  server-side es CONTRATO (cierre de la deuda S-3 heredada de C4 — Punto 3
  del gate); el backend NUNCA acepta un comodín del cliente.
- **`limit`**: defecto 50, máx. 200 (rango 1..200). Fuera de rango → 400
  `filter_limit_invalid` (con ancla).
- **`cursor`**: token OPACO para el cliente (nunca lo parsea — literal).
  Internamente `(received_at, id)` en Base64url (P1 del gate). Ilegible →
  400 `filter_cursor_invalid` (con ancla).

**SQL de implementación** — el LITERAL `(:x IS NULL OR ...)` de Schema
§3.3, LA sentencia ÚNICA y fija del corte (E1 del explore, ratificada:
cero SQL dinámico por filtros — convención C3; un solo estamento
pin-testable estilo `LogsRepoSqlTest`; sin ramas a probar por combinación):

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
LIMIT :limit + 1;  -- 1 fila de más decide si hay next_cursor
```

- **Orden keyset**: `received_at DESC, id DESC` (§4 regla 13 verbatim:
  "Paginación keyset estable | (received_at DESC, id DESC); offset
  descartado (TRD ADR-10). Coste plano ante el crecimiento; páginas
  estables con ingesta en paralelo; nunca se carga la tabla entera en
  memoria (métrica 3 del PRD)"). El `id` es el desempate EXACTO del índice
  maestro (`logs_dashboard_idx` termina en `received_at DESC, id DESC`) —
  coherente con la regla 9 (el lote queda desempatado por `id` creciente
  en inserción; leerlo `id DESC` es determinista y estable, y AppFlow
  flujo 6b-3 respeta el mismo orden en el cliente).
- **LIMIT+1 → trim (mecánica exacta)**: se piden `limit + 1` filas; si
  viene la fila de más → hay `next_cursor` (codificado con
  `(received_at, id)` de la ÚLTIMA fila ENTREGADA) y `items` queda
  recortado a `limit`; si no viene → `next_cursor: null` (= "fin de
  resultados", literal §3.3). La primera página (sin `cursor`) es el MISMO
  estamento con `:cursor_r`/`:cursor_i` NULL → la rama del keyset no
  filtra. El cursor lleva SIEMPRE el par (r, i): la comparación
  `received_at < :cursor_r OR (received_at = :cursor_r AND id <
  :cursor_i)` exige AMBOS parámetros (un cursor de solo `id` es
  falsificado contra el SQL literal — C2 del explore, descartada).
- **Respuesta 200**: `{"items": [LogListItemDto...], "next_cursor": "…"}`
  — el shape EXACTO del ejemplo de §3.3. Sin `cursor` y sin resultados →
  `200 {"items": [], "next_cursor": null}` (estado vacío legítimo;
  AppFlow §4 `/logs` vacío: "No hay logs que cumplan los filtros").

### 2.2 `GET /api/v1/panel/logs/{id}` — detalle

Fila verbatim de Schema §3.2:

> | GET | `/api/v1/panel/logs/{id}` | JWT | Detalle de log | → 200 `LogDto` · 401 · 404 |

- **200 `LogDto`**: los 9 campos del listado **más** `exception` (string o
  `null`) y `metadata` (objeto JSON o `null`) — literal §3.3 verbatim:
  "los campos del listado **más** `exception` (string o `null`) y
  `metadata` (objeto JSON o `null`)". La `metadata` JSONB llega como
  String/PGobject → `JsonNode` con Jackson (objeto exacto serializado tal
  cual, sin re-interpretación).
- **404** `{"code": "not_found"}` + `message` — cuerpo estándar §3.1;
  literales C3 EXISTENTES reutilizados por referencia
  (`CODE_NOT_FOUND`/"No existe (quizás fue eliminado)", patrón
  `ProjectsController`; AppFlow flujo 5 paso 6: "Este registro no existe
  (o fue eliminado)"). Sin pre-SELECT doble: una lectura por id con
  `Optional` (patrón `ProjectsRepository.findById`, NUNCA
  `queryForObject` cuando la fila-0 es el caso legal del 404).
- El `id` no-numérico en el path (`/logs/abc`): Spring no mapea y responde
  su 404 genérico de matching — se ratifica sin toque (fuera del enum de
  codes; patrón igual que `GET /projects/{id}` de C3, ya operado así).

### 2.3 Dos records: `LogListItemDto` (9) y `LogDto` (11) — D1 del explore

| Opción | Diseño | Veredicto |
|---|---|---|
| **D1 (RECOMENDADA)** | DOS records: **`LogListItemDto`** con EXACTAMENTE los 9 campos del SELECT (nombres Literales del Schema §3.3: `id, project_id, level, environment, timestamp, received_at, message, source, correlation_id` — con `timestamp` y `received_at` como String YA formateados ISO-8601 UTC con `Z`, patrón `ProjectDto`) y **`LogDto`** con los 11 (los 9 + `exception: String` + `metadata: JsonNode`, ambos nullable) | **ELEGIR** — el congelado NOMBRA 2 DTOs con reglas distintas ("LogListItemDto: sin exception ni metadata — no se transportan en la lista (TRD §2.4)"); una CLASE distinta hace la fuga de los 2 campos pesados (hasta 160+ KiB por página, métrica 3 del PRD) ESTRUCTURALMENTE IMPOSSIBLE (R-6.g: sin `@JsonInclude` por vistas ni recortes de RowMapper — protección por compilar, no por convención) |
| D2 (descartada) | UN record de 11 con exclusión de campos en el RowMapper de lista | El JSON de lista llevaría nulls (falsifica el literal de §3.3) o exigiría vistas de serialización — mezcla de contratos en 1 clase |

- El nombre del proyecto NO viaja en la fila: el panel ya cargó
  `GET /projects` y mapea por `project_id` (AppFlow flujo 4 paso 3 —
  literal del congelado, no decisión de C6).
- Las fechas en el DTO usan el formatter de C2 reutilizado por referencia
  (`LoginController.EXPIRES_AT_FORMATTER`, truncado a segundos) — el
  RENDER es otra cosa y mantiene la convención viva de
  `ProjectDto`/`LoginResponse`; el componente interno con precisión
  completa para EL CURSOR es aparte (P1). El codec del cursor es puro y
  unit-testable fuera de Spring.
- `message` puede ser grande (≤32 KiB) y `exception` hasta 128 KiB — solo
  `LogDto` (detalle) los transporta en su totalidad.

### 2.4 Fail-fast 400 — codes verbatim + los 3 sin literal + orden

**Códigos CON ancla directa en Schema §3.3** (verbatim: "400 si: `from >
to` (`filter_from_to_inverted`), `environment`/`level` fuera de enum
(`filter_invalid_value`), `limit` fuera de 1..200
(`filter_limit_invalid`), cursor ilegible (`filter_cursor_invalid`). 400 →
el panel conserva la última lista válida"):

| Caso | HTTP | `code` | `field` |
|---|---|---|---|
| `from > to` (parseados) | 400 | `filter_from_to_inverted` | — (propuesto: sin field; P2/c spec cierra) |
| `environment` fuera de enum | 400 | `filter_invalid_value` | `environment` |
| `level` fuera de enum | 400 | `filter_invalid_value` | `level` |
| `limit` fuera de 1..200 | 400 | `filter_limit_invalid` | `limit` |
| cursor ilegible (Base64url/JSON/keys) | 400 | `filter_cursor_invalid` | `cursor` |
| `id` inexistente en detalle | 404 | `not_found` | — |

El vocabulario EXISTENTE aprobado en C3 (`filter_invalid_value`,
`not_found`) se reutiliza; `filter_from_to_inverted`, `filter_limit_invalid`
y `filter_cursor_invalid` son NUEVOS en el vocabulario PERO CON ancla en el
congelado (patrón C5 exacto: nuevas codes citan su ancla del Schema).

**Los 3 casos SIN literal textual en el congelado** (explore §6.1 — van
PROPUESTOS al gate P2, PROPUESTAS de vocabulario EXISTENTE, sin codes
nuevos, sin anclar hasta ratificación):

| Caso | code PROPUESTO | `field` | Justificación |
|---|---|---|---|
| `project_id` presente NO numérico | `filter_invalid_value` | `project_id` | Es "valor inválido de un filtro": literal y field EXISTEN hoy (C3) — sin inventar code nuevo |
| `from` o `to` presente NO parseable a ISO-8601 con offset | `filter_invalid_value` | `from` / `to` | Idem; el valor inválido se reporta con su campo culpable |
| `limit` presente NO numérico | `filter_limit_invalid` | `limit` | Es límite ilegible: MISMO code del rango 1..200 (ancla §3.3/§4.1) |

**Orden fail-fast de parámetros (propuesta determinista, primer fallo
gana — P6 del gate; el congelado NO fija el orden de los 400 de GET /logs,
patrón C3/C5 "el enum ANTES de la BD — regla 10")**: TODOS los checks
ANTES de tocar la BD (con `verifyNoInteractions` del repo mockeado en test):

1. `limit` — no numérico o fuera de 1..200 → 400 `filter_limit_invalid` (limit).
2. `project_id` — presente no numérico → 400 `filter_invalid_value` (project_id).
3. `environment` — fuera de enum → 400 `filter_invalid_value` (environment).
4. `level` — fuera de enum → 400 `filter_invalid_value` (level).
5. `from` — presente no parseable → 400 `filter_invalid_value` (from).
6. `to` — presente no parseable → 400 `filter_invalid_value` (to).
7. `from > to` (ambos parseados, sobre UTC) → 400 `filter_from_to_inverted`.
8. `cursor` — ilegible (no Base64url / no JSON / sin `r`/`i` / `r` no
   parseable) → 400 `filter_cursor_invalid` (cursor).

Solo tras los 8: el repositorio ejecuta la sentencia única. El orden
limit→enums→rango→cursor replica el patrón C3/C5 (límites y enums primero,
parseo después, token final al ser el único parámetro que consulta la BD
en su propia rama); el detalle concretará si el cursor debe subir de
posición — P2/P6 y spec lo anclan como lista EXACTA (como se fijó el
orden 413→401→403→400 en C5).

- **`text` SIN cap de longitud**: el congelado NO declara límite para
  `text` (ni para `cursor`) — NO se inventa cap (pregunta abierta §6.4; el
  SQL lo soporta a cualquier longitud a volúmenes v1; P2 lo nota, el spec
  lo documenta como "sin límite — sin ancla").

### 2.5 Pin-testability

El estamento único (§2.1) se pina como constantes package-private en el
MISMO paquete de test (patrón `LogsRepoSqlTest` de C5, sin reflection ni
BD): pin del SELECT literal (columnas exactas, el patrón
`(:x IS NULL OR ...)` completo, la rama keyset con AMBAS comparaciones,
`ORDER BY received_at DESC, id DESC`, `LIMIT`), pin de la construcción de
`%<escaped>%` para `text` (el ESCAPE de `%`/`_`/`\` es función pura
testeable sin BD — S-3) y pin de los 2 records (9 / 11 componentes exactos,
nombres snake_case). Sin SQL dinámico en NINGÚN punto (E1 ratificada).

## 3. Qué NO hace C6 (fuera de alcance — NO-alcanza)

- **Streaming real (C7)**: ni HandshakeInterceptor ni `LiveBroadcaster` ni
  frames `batch`/heartbeat/backoff; `streaming/**` (Echo +
  WebSocketConfig) queda byte-a-byte intacto. C6 solo FIJA la semántica de
  filtros compartida (§3.5/ADR-7: "exactamente la misma semántica") y deja
  el seam de lecturas vivas sin conectar (el `IngestListener` de C5 queda
  sin beans suscriptos — C7 decide).
- **Flujo 6b llegada en vivo (cliente)**: del lado servidor NO hace nada;
  la única ligadura es que el keyset estable del Done es condición del
  dedup por `id` del flujo 6b — C6 establece la REST; el cliente lo usa en
  C7/C8.
- **Resumen / DELETE** (C10): ni `include=summary` ni
  `GET /projects/{id}/summary`; NI NINGÚN `DELETE` —
  `DELETE /logs/{id}` (C10), purga `/projects/{id}/logs` (C10) y CASCADE
  son P1 del PRD (flujo 8). El botón "Eliminar registro" del detalle
  (flujo 5 paso 5) es C10.
- **Frontend del panel `/logs`** (C8): tabla, "Cargar más", estados de
  pantalla §4 del AppFlow, query-string compartible — C6 solo expone la
  REST que la UI del flujo 4 pide.
- **Nginx (C9)**: sin cambios en `compose.yaml` ni proxy
  (`client_max_body_size` es deuda de C9).
- **Migraciones**: `V1__init.sql` INTOCABLE (gate 2026-10-19: V1 es el
  literal). **NI FALTA NINGÚN ÍNDICE** — el explore lo verificó contra la
  pila VIVA con `\d logs` de solo-lectura: `logs_dashboard_idx` btree
  `(project_id, environment, level, received_at DESC, id DESC)` es el
  cascarón EXACTO de la ruta del dashboard (índice-scan descendiente, sin
  sort — el congelado lo llama "el índice maestro del dashboard");
  `logs_received_idx` btree `(received_at DESC)` cubre los filtros
  globales sin proyecto (el desempate `id DESC` lo saca el planner con
  incremental sort PG≥13, aceptado v1 por literal del Schema: "Los filtros
  level/environment sin proyecto degradan a logs_received_idx + scan
  filtrado — aceptable en v1" — R-6.e). El índice trigram GIN de `message`
  (`V2`, requiere pg_trgm) está COMENTADO y reservado en V1: se documenta,
  NO se activa en C6.
- **Testcontainers (P4): NO en C6.**
- Sin rate limiting 429 (ADR-26), sin retención, sin multiusuario (§7 del
  Schema).

## 4. Enfoque técnico recomendado (del explore, ratificado)

- **Paquete `com.projectlog.query`** (package-by-feature; su package-info
  ya declara el rol). Sub-paquetes al patrón `projects` RECOMENDADO:
  `query/controller/`, `query/dto/`, `query/repository/` (el codificador
  del cursor y la lógica de parseo/escape de filtros como piezas del
  paquete — naming final en spec, pregunta §8). Los sub-paquetes también
  dejan `query.repository.LogsQueryRepository` desambiguado del
  `ingest.LogsRepository` de escritura.
- **`LogsQueryRepository` NUEVO en `query`**: SQL inline sobre `logs` con
  `JdbcTemplate` (patrón `ProjectsRepository`: constantes, RowMapper/`FILA`
  propio, `@Transactional` como defensa documentada mono-sentencia,
  `Optional` por 404, NUNCA `queryForObject` cuando la fila-0 es el 404
  legal, sin SQL dinámico). La lista devuelve una filas de moneda de
  cursor (`received_at` como `OffsetDateTime` del driver — precisión
  completa) + las 2 listas de DTO; el detalle lee `exception`/`metadata`.
- **Cero lecturas añadidas a `ingest/LogsRepository`** (frontera C5:
  INSERT-only + `findByApiKeyHash` en `projects`); `LogValidado` (record de
  escritura) NO se reutiliza para leer filas.
- **Los 2 mappings en un `LogsController` fino** con validación MANUAL de
  borde (patrón `ProjectsController`: sin `@Valid` — no hay
  starter-validation; helper `error(...)` con `{code, message, field?}`;
  sin servicios extra: un solo controller + repo, como projects). Detalle
  y listado JAMÁS comparten mapper recortado — 2 métodos del repo.
- **Fechas**: `from`/`to` parseados a `OffsetDateTime` y pasados como
  parámetro (PG compara contra `received_at` TIMESTAMPTZ normalizando a
  UTC — TRD §2.6 "siempre se normaliza a UTC al guardar"); el DTO formatea
  con el formatter de C2; EL CURSOR lleva el instante sin truncar (P1).
- **Tests**: patrón vigente `[L]` — `@WebMvcTest` del controller con
  cadenas de seguridad importadas (patrón C3/C5: `panelChain` + entry
  point del panel; los endpoints nacen behind 401 y el test lo pina) +
  repo mockeado; pin SQL estilo `LogsRepoSqlTest`; unit puro del codec del
  cursor (sin BD, sin Spring); higiene heredada no aplica a loggers
  sensibles aquí (no hay keys en query) — la suite actual 108/108 debe
  seguir verde sin tocar test de C1–C5.
- **Smoke [S]** `scripts/qa_c6_smoke.sh` NUEVO (patrón `qa_c5_smoke.sh`):
  recomposición ÚNICA `podman compose up -d --force-recreate` (NUNCA
  `down -v` — pgdata), psql `-qAt`, conteos RELATIVOS antes/después,
  nombres sufijados por ejecución (`smoke-c6-<TS>-a`), tmpdir 700 con
  trap, JWT solo a tmpdir, smoke-of-the-smoke antes de la canónica,
  needles `plk_*` de listas con escape `\_`/`position()` (lección S-3), exit 0/1/2.

## 5. Decisiones/riesgos para el GATE — 6 puntos

> Los 6 numerados se presentan para aprobación (patrón C4/C5: 6/6). Se
> marca cada uno como DE GATE (fondo) o de paquete.

### P1 (DE GATE) — Codec del cursor: precisión del instante SIN pérdidas + Base64url opaco + tolerancia del parse

**Enunciado (CRÍTICO — hallazgo R-6.a del explore).** El formateador
vigente de la casa (`EXPIRES_AT_FORMATTER`, `yyyy-MM-dd'T'HH:mm:ss'Z'`)
trunca fracciones de segundo; PG `TIMESTAMPTZ` guarda microsegundos y la
regla 9 da a todo un batch UN SOLO `received_at`. Codificar el cursor con
el instante truncado SALTARÍA las filas del mismo instante que la última
entregada: tampoco cumplimentan `received_at < :cursor_r` ni
`received_at = :cursor_r` → desaparecerían entre páginas (Done roto,
literalmente "NO salta").

Formato del token (fijado por contrato §3.3 — NO se rediseña): opaco
Base64url de `{"r": <received_at>, "i": <id>}` — el ejemplo del Schema
decodifica exactamente así: `eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIj
oxMjMwMX0` → `{"r":"2026-10-16T22:31:04Z","i":12301}`. La CONCRECIÓN del
codec MÍNIMO propuesta:

- **`r` = String ISO-8601 del instante SIN truncar**: p. ej.
  `Instant.toString()` (o equivalente JDK) del `received_at` leído de la
  columna — ISO-8601 UTC con las fracciones que produzca PG (ninguna, o
  `.123456` a micros; longitud variable). Decodificación EXACTA: parse del
  String a `Instant` — re-dondear la precisión es INVÁLIDO (los micros
  perdidos no se recuperan y romperían la igualdad `received_at =
  :cursor_r`). Estructuralmente compatible byte a byte con el ejemplo del
  congelado (que es una instancia sin fracciones).
- **`i`**: el `id` BIGINT tal cual (`long` JSON).
- **Codificación**: JSON compacto + `Base64.getUrlEncoder().withoutPadding()`
  (puro, unit-testable, sin BD ni Spring). El cliente lo trata como token
  opaco (nunca lo parsea — literal §3.3).
- **En pantalla (DTO)**: el formatter Z de C2 SE MANTIENE (render de
  `LogListItemDto`/`LogDto` igual que `ProjectDto`) — la precisión
  completa es del CURSOR, no del JSON visible; coteja el literal: la
  salida del ejemplo del congelado también va truncada a segundos, así que
  el render no introduce divergencia nueva.
- **Tolerancia del parse (propuesta)**: SOLO Base64url estándar
  (`getUrlDecoder`, sin aceptar padding clásico ni variaciones laxas —
  pregunta abierta §6.6 del explore, RECOMENDADO "no"): cualquier fallo —
  decodificación, JSON inválido, sin claves `r`/`i`, `r` no parseable —
  es EL MISMO 400 `filter_cursor_invalid` (ancla §3.3), sin oráculo de
  causa. Tolerar variantes falsificaría el contrato "opaco".

**Recomendación de gate**: aprobar el codec mínimo tal cual (el spec
fija el literal EXACTO del encode/decode y el parse del `r`).

### P2 (DE GATE) — Los 3 codes 400 sin literal textual (sobre vocabulario EXISTENTE)

**Enunciado.** El §3.3 enumera los 4 codes de filtro y la §3.1 cierra el
set de codes del API; los casos `project_id` no-numérico, `from`/`to` no
parseable y `limit` no-numérico NO tienen literal textual en el congelado
(explore §6.1). C5/C3 cerraron el precedente: NO se inventan codes sin
ancla (CD-3.9/S5-2).

**Recomendación (PROPUESTA, sin anclar hasta ratificación)**: reutilizar
el vocabulario EXISTENTE — `filter_invalid_value` con `field:
"project_id"` / `field: "from"` / `field: "to"`; `filter_limit_invalid`
con `field: "limit"` para el no-numérico (el MISMO code del rango). Ningún
code nuevo; los `message` en español se fijan en el spec (patrón
`CODE_*`/`MSG_*` de C3; los docs fijan `code` y `field`, el message no —
herencia C5 Punto 6).

### P3 (DE GATE) — S-3 LIKE-underscore se CIERRA en C6 con el escape de contrato

**Enunciado.** C4 dejó la deuda S-3 (aguja con `_` opera como comodín en
LIKE en el smoke) cuando no había filtro de texto real en la BD. C6
introduce el PRIMERO (`text` con ILIKE) — y el escape NO es elección:
§3.3 verbatim "escapando `%`, `_` y `\`" (el `\` es el escape default de
LIKE/ILIKE en PG).

**Recomendación (de paquete con ancla de contrato)**: (a) el endpoint
escapa SIEMPRE `%`, `_` y `\` con `\` y construye `%<text>%` alrededor
(sin aceptar comodines del cliente — R-6.b); (b) el SMOKE usa una aguja
con `_` REAL insertada por ingesta: mensaje `needle_c6_<TS>` presente +
un contra-caso `needleXc6<TS>` (sin `_`, mismo prefijo) que NO debe
matchear — si `_` actuase como comodín, el contra-caso colaría: asertos
de igualdad estricta; (c) needles psql del smoke NUNCA llevan la aguja
cruda a un LIKE psql sin escapar (lección S-3 literal). Con esta
ratificación el archive de C6 cita la S-3 de C4 COMO CERRADA.

### P4 (de paquete) — Testcontainers: NO en C6 (reevaluar en C7)

**Enunciado.** C5 aprobó "reevaluar en C6/C7". C6 es el corte donde la BD
real más importa hasta ahora (keyset de 2 claves, ILIKE con escape, rango
inclusive sobre TIMESTAMPTZ, JSONB en detalle, estabilidad ante inserts en
paralelo).

**Recomendación**: **NO en C6** — ratificar la lógica C5. La prueba del
Done es 100% scriptable en el smoke [S] sobre la PILA REAL (ingesta de
pila + 2 GET paginados + asertos psql — patrón canónico C4/C5); añadir
Testcontainers implica 2 dependencias test-nuevas, pull de imagen, y
probar docker-in-`mvn` dentro del builder `maven:3.9-eclipse-temurin-21`
(R-nuevo-6 heredado: no demostrado). Refuerzos del corte que lo cubren:
(1) smoke de ESTABILIDAD keyset con batch que separa páginas (P1/Done);
(2) smoke con aguja `_`/`%` (P3); (3) unit puro del codec (precisión sin
pérdidas — donde Testcontainers NO añadiría nada); (4) pin SQL + unit MVC
con repositorio mockeado; (5) smoke con las 1877 filas heredadas presionando
el índice (conteos relativos, R-6.h). **Reevaluación REAL apuntada a C7**
(socket real + BD real bajo `mvn` podría pesar más ahí).

### P5 (DE GATE) — Dos records D1: la fuga de exception/metadata estructuralmente imposible

**Enunciado.** §3.3 verbatim: "**LogListItemDto**: sin `exception` ni
`metadata` — **no se transportan en la lista** (TRD §2.4)". Un payload de
lista con los 2 campos pesados (hasta ~160 KiB por fila con exception de
128 KiB) rompe la métrica 3 del PRD ("nunca se carga la tabla entera en
memoria"+ "al listar exception y metadata no se transportan completos" —
TRD §2.4).

**Recomendación (de paquete con ancla)**: **D1 tal como §2.3** — 2 records
con nombres literales del congelado; el pin de test afirma los 9/exactos
componentes del `LogListItemDto` (sin `exception`, sin `metadata`) y los
11 de `LogDto`. La fuga queda imposible de compilar, no de disciplina.

### P6 (de paquete, con cierre para spec) — Orden fail-fast de los 400 (lista EXACTA)

**Enunciado.** El congelado NO fija el orden de evaluación de los 400 de
`GET /logs`; sin lista exacta, dos corridas ante la misma query con 2
fallos devolverían codes distintos (no-determinismo — patrón C5 que fijó
el orden 413→401→403→400 por gate).

**Recomendación**: aprobar la lista numerada EXACTA de §2.4 (8 checks:
limit → project_id → environment → level → from-parse → to-parse →
from>to → cursor) — TODOS antes de tocar la BD, primer-fallo-gana,
`verifyNoInteractions` del repo en test. El spec la reancla con cita y el
verify la pina (al igual que el pin del orden por campo interno del
registro de C5).

## 6. Criterios de Done de C6 (de la ficha del 07, trazados a E6.x)

1. **Listado keyset**: `GET /api/v1/panel/logs` → 200 `{items,
   next_cursor?}` EXACTO; defecto 50 (sin `limit`), 200 máximo; cada
   `LogListItemDto` con los 9 campos literales; `next_cursor: null` en
   fin de resultados; cursor opaco NUNCA requerido parseable por el
   cliente. — **E6.1, E6.2, E6.22, E6.23**
2. **Keyset ESTABLE (lit. Done del 07)**: insertar logs nuevos entre
   páginas NO duplica ni salta — smoke: página 1 → ingesta batch de K
   logs (mismo `received_at`, regla 9) → página 2 con el cursor de la 1 →
   asertos psql de que ningún id se repite, ningún id de la página 1
   reaparece, y la página 2 arranca EXACTAMENTE en la fila siguiente al
   cursor; INCLUYE un batch cuyo grupo comparte el `received_at` de la
   última fila de página 1 y SE SEPARA entre páginas (la prueba del P1:
   precisión de micros del `r`). — **E6.17 (y E6.12 en unit del codec)**
3. **Filtros combinables**: project_id + environment + level + from/to
   (inclusive ambos extremos, offset normalizado a UTC) + text (escape
   `%`/`_`/`\`; aguja `_` real, cierre S-3) — cualquier combinación en una
   sola sentencia. — **E6.16, E6.18, E6.19**
4. **Detalle con smoke**: `GET /panel/logs/{id}` → 200 `LogDto` (11
   campos: exception/metadata SÍ viaja, JSONB legible) y 404 `not_found`
   con cuerpo estándar en pila. — **E6.3/E6.4 [L], E6.20 [S]**
5. **Fail-fast 400 según §3.3**: los 4 codes anclados contra la pila +
   los 3 propuestos (P2) + el orden P6 determinista; 400 → (cliente, C8)
   conserva la última lista válida. — **E6.5–E6.10 [L], E6.21 [S]**
6. **Autenticación**: ambos endpoints nacen tras la cadena 2 del PANEL —
   sin JWT → 401 `invalid_token` (contrato CD-4 de C2, intocable); reuse
   de la cadena sin diff. — **E6.15**
7. **NO pasa (fronteras)**: streaming C7, flujo 6b del cliente, resumen y
   DELETE (C10), front (C8), nginx (C9), migraciones, touch en
   `ingest/**`/`config/**`/`streaming/**`/`auth/**`/`keys/**`,
   Testcontainers (P4), SQL dinámico, códigos sin ancla fuera de los 3
   propuestos de P2.

### Tabla de escenarios E6.x propuesta (para spec/qa-plan)

| Familia | IDs | Tipo / cobertura |
|---|---|---|
| MVC 200 listado | E6.1 (shape `{items, next_cursor}` exacto), E6.14 (limit defecto 50 / 200 legal / 201 y 0 y -1 → 400), E6.2 (9 campos SIN exception/metadata — pin de DTO) | [L] |
| MVC 200 detalle | E6.3 (11 campos: exception String, metadata JsonNode con objeto exacto), E6.4 (404 not_found literal exacto) | [L] |
| MVC 400 anclados | E6.5 (filter_invalid_value environment/level), E6.6 (filter_from_to_inverted — frontera from == to → LEGAL), E6.7 (filter_limit_invalid 0/201), E6.9 (filter_cursor_invalid: no b64url, no JSON, sin r/i, r no parseable — los 4 sub-casos, mismo body) | [L] |
| MVC 400 propuestos (P2) | E6.8 (project_id no-numérico, from/to no parseable, limit no numérico — codes/fields PROPUESTOS, a ratificar) | [L] |
| Determinismo | E6.10 (orden fail-fast exacto de la lista P6 — ante query con TODOS los parámetros mal el 400 es SIEMPRE el del check 1), E6.11 (verifyNoInteractions del repo ante cualquier 400) | [L] |
| Pin | E6.11 (pin SQL del listado: columnas exactas, patrón `(:x IS NULL OR ...)`, rama keyset con 2 comparaciones, ORDER BY, LIMIT — patrón LogsRepoSqlTest), E6.12 (unit codec: round-trip con micros reales preservados — `2026-10-16T22:31:04.123456Z`; truncado → rechazado), E6.13 (pin escape de text: `%`/`_`/`\` escapados + envoltura `%<text>%`), E6.2 (pin DTO 9/11) | [L] |
| Seguridad | E6.15 (sin JWT → 401 `invalid_token` del entry point del PANEL; jwt jamás con API key) | [L] |
| Pila happy | E6.16 (listado con filtros combinables reales — needle por proyecto+env+level en psql y REST), E6.19 (rango from/to con offset `+02:00` → mismo resultado que su equivalente UTC; fronteras inclusive), E6.22 (limit frontera 200/201 y defecto 50 en pila), E6.23 (fin de resultados: `next_cursor` null en la última página; primera página del recorrido con cursor de la anterior) | [S] |
| Pila estable | E6.17 (keyset estable: página 1 → batch de ingesta mismo `received_at` → página 2 → 0 duplicados, 0 saltos, primera fila de página 2 = fila siguiente al cursor; sub-caso de batch que COMPARTE received_at con el fin de página 1 y queda PARTIDO entre páginas) | [S] |
| Pila text/S-3 | E6.18 (aguja `_` real e ingestada + contra-caso de igualdad estricta — cierre de la S-3 de C4) | [S] |
| Pila detalle | E6.20 (detalle 200 con exception/metadata reales + 404 con id inexistente en pila) | [S] |
| Pila 400 | E6.21 (los 5 codes de 400 reales en pila: 4 anclados + 1 propuesto mínimo — needle del cuerpo `{code, message/field}`) | [S] |

> Total ≈ 23 escenarios (~15 [L] + 8 [S]); el spec puede fusionarlos/
> refinarse con su Parejas obligatorias mock↔psql (patrón C5), pero los
> literales Done (estabilidad keyset, filtros combinables, detalle) quedan
> cubiertos CONTRA LA PILA como el 07 exige.

## 7. Estimación de alcance

**Ficheros NUEVOS (backend, en `com.projectlog.query`; naming final en
spec)**:

| Fichero | Contenido |
|---|---|
| `query/controller/LogsController.java` | 2 mappings literales (listado + detalle), validación MANUAL de query params fail-fast, helper `error(...)` (patrón ProjectsController) |
| `query/dto/LogListItemDto.java` | record de los 9 campos (§2.3/D1) |
| `query/dto/LogDto.java` | record de 11 campos (+exception String, +metadata JsonNode) |
| `query/dto/LogsPaginaResponse.java` (o equivalente — nombre spec) | wrapper `{items, next_cursor}` de la respuesta |
| `query/repository/LogsQueryRepository.java` | SELECT keyset ÚNICO + SELECT por id; RowMapper propio; `received_at` como OffsetDateTime (precisión para el cursor); metadata→JsonNode |
| `query/CursorCodec.java` (nombre propuesto) | codec puro Base64url/JSON del token (`{"r","i"}` con `r` sin truncar); encode/decode; fallos → excepción propia que el controller traduce a 400 `filter_cursor_invalid` |
| `query/` (apoyo, si lo exige design) | parseo/normalización de filtros (enums, ISO-8601, escape LIKE) reutilizable por C7 (seam §3.5/ADR-7) — puede vivir en el controller o en 1 clase de paquete |

**Ficheros a MODIFICAR: NINGUNO** (propiedad limpia del corte):

- `ingest/**` INTOCABLE — `LogsRepository` (de C5) queda INSERT-only +
  `LogValidado` y el `IngestListener` (seam) sin cambiar; NO se añade ni
  un método de lectura (las lecturas nacen en `query/` con SU repo).
- `config/**` INTOCABLE — `panelChain` (cadena 2) ya cubre
  `/api/v1/panel/logs[/...]` sin edición; `RestAuthenticationEntryPoint`
  (panel, `invalid_token`, CD-4) byte-a-byte.
- `auth/**` INTACTO — `EXPIRES_AT_FORMATTER` reutilizado por REFERENCIA
  para el render del DTO (mismo patrón de `ProjectsRepository`, C3) — el
  formatter JAMÁS se toca; la precisión del cursor es codec aparte.
- `streaming/**`, `keys/**`, `projects/**` (solo lectura del patrón),
  `login/me`, `common/**` — sin diff.
- `V1__init.sql` INTOCABLE — sin migración (índices SUFFICIENTES, §e del
  explore; trigram GIN reservado SIN activar).
- `backend/pom.xml` — SIN dependencias nuevas (sin starter-validation,
  sin Testcontainers, sin pg_trgm).
- `application.yml` / `.env.example` / `application-test.yml` /
  `compose.yaml` — sin cambios (C6 no introduce env vars).
- Tests NUEVOS en `backend/src/test/java/com/projectlog/query/` ( MvcTest
  del controller + pin SQL + unit del codec + pin de DTOs ) — sin tocar
  UN test de C1–C5 (re-corrida 108/108 debe seguir verde).
- `scripts/qa_c6_smoke.sh` — NUEVO (patrón `qa_c5_smoke.sh`).

**Migraciones: NO. Front: NO. Dependencias: NO. Env vars: NO.**

## 8. Preguntas para sdd-spec (huecos que el spec de C6 debe cerrar)

1. Literal EXACTO del codec del cursor (P1): método de encode
   (serializador JSON del pair — `ObjectMapper` o string builder puro),
   variante Base64url EXACTA (`withoutPadding()` confirmado), parse del
   `r` (`Instant.parse`/`OffsetDateTime.parse` con fallback), firma de
   las funciones y Javadoc del ancla (§3.3 + R-6.a), y cómo viaja el
   valor sin truncar desde el RowMapper (`getObject(...,
   OffsetDateTime.class)`) al codec SIN pasar por el formatter.
2. Tolerancia de decodificación (P1): rechazo de padding Base64 clásico y
   del JSON con claves extra (¿se ignoran o rechazan?); el `i` NO
   numérico; `r` en el futuro o más reciente que la última fila
   (cursor "hacia adelante") — 400 o página vacía legal (recomendación:
   página vacía — el keyset es una comparación SQL legítima, sin
   semántica especial).
3. Escapes ILIKE EXACTOS (P3): cadena de escape (orden `%`→`_`→`\`,
   literal §3.3), constantes del `\` (PG default sin `ESCAPE`
   explícito — ¿declarar `ESCAPE '\'` en la sentencia o confiar al
   default? — recomendación: default del PG, pin del literal; el spec
   decide si se ancla el `ESCAPE` explícito en el estamento), y si el
   parámetro `:text` del literal SQL lleva el `%...%` construido YA
   escapado (recomendación: sí — el estamento mantiene `%<text>%`
   y el parámetro llega `%needle%` escapado).
4. Orden de gates (P6): lista exacta de los 8 checks con sus parejas
   code/field/message en CONSTANTS (patrón C3 `CODE_*`/`MSG_*`) y el
   pin del orden (test de 2 fallos simultáneos → SIEMPRE el primero).
5. Tests EX6 de parejas obligatorias mock↔psql (patrón C5 §2.3): desglose
   numérico/nominado E6.1–E6.23 (o el refinado del spec), qué [S]
   duplican qué [L] obligatoriamente, y el guion EXACTO del smoke
   (pasos curl + psql, orden de los escenarios, recomposición única
   `--force-recreate`, tmpdir, needles de `plk_` escapados — S-3
   patrón heredado, conteos RELATIVOS sobre las 1877 filas heredadas).
6. Naming final de las piezas (explore §6.5): `LogsController`
   (propuesto) vs `PanelLogsController`; `CursorCodec` (propuesto) vs
   otra; sub-paquetes `query/controller,dto,repository` (propuesto,
   patrón projects) vs plano (patrón streaming) — el spec decide con
   Javadoc de package-info actualizado (¿se EDITA el package-info de
   query?, hoy dice "C6" — actualización del rol: aditiva, sin
   reescribirlo).
7. Respuesta del detalle y del listado: Content-Type y emisión
   (`ResponseEntity.ok(body)` con el objecto ya tree — mismo patrón C3 de
   `objectMapper.valueToTree`); y si el listado con `items: []` lleva
   `next_cursor: null` EXPLÍCITO en el JSON (recomendación: sí — el
   ejemplo de §3.3 lo muestra y su ausencia rompería el toggle del
   "Cargar más" del flujo 4 paso 4) vs OMITIR la clave cuando es null
   (divergencia a anclar: NO — el literal muestra la clave).
8. `timestamp` en el DTO va truncado a segundos por el formatter de C2
   (literal vigente de casa, ratificado en C3 para ProjectDto) — coteja
   en el spec que ninguna doc exige micros en la SALIDA del panel (los
   ejemplo §3.3 van a segundos) y que el P1 solo introduce precisión en
   el cursor (divergencia interna documentada, sin tocar el formatter).
9. `text`/`cursor` sin cap (P2/anexo): el spec documenta "límites sin
   ancla = no inventados" y cómo un `text` gigante no rompe el fail-fast
   (¿comparar `length()`? — no: sin ancla, se pasa tal cual al SQL).

## 9. Riesgos del corte

| ID | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| **R-6.a** | Cursor con `received_at` truncado a segundos SALTA filas del mismo instante (todo batch comparte `received_at` — regla 9): rompe el Done literal | **CRÍTICO** | P1: la clave `r` lleva el instante SIN pérdida (ISO-8601 con micros del valor leído — `Instant.toString()`); smoke con batch que SEPARA páginas; unit del codec round-trip con micros |
| **R-6.b** | Comodines del cliente (`%`, `_`, `\`) actúan como wildcard en ILIKE — en PRODUCTO (no solo smoke; S-3 de C4 se manifiesta aquí de verdad) | ALTO | Escape server-side OBLIGATORIO `%`/`_`/`\` (CONTRATO §3.3, P3); smoke con aguja `_` + contra-caso de igualdad; cierre S-3 anotado |
| **R-6.g** | Fuga accidental de `exception`/`metadata` en la lista (hasta 160+ KiB por fila) — rompe la métrica 3 del PRD | ALTO | D1/P5: DOS records estructuralmente distintos; pin del DTO de lista de 9 componentes exactos |
| **R-6.c** | `project_id`/`limit` no-numéricos vía binding directo de Spring → 400 genérico (cuerpo fuera del contrato §3.1) | MEDIO | Parseo manual en el controller (nunca binding tipado directo); 400 con code/field PROPUESTOS (P2); test Mvc cubre |
| **R-6.f** | SQL validado en unit con repo MOCKEADO — si el estamento real falla (tipos del keyset, escape), el fallo es tarde (en smoke) | MEDIO | Pin del SQL literal (estilo `LogsRepoSqlTest`), parámetros tipados del keyset (OffsetDateTime/long — jamás String para el instante), smoke-of-the-smoke antes de canónica |
| **R-6.e** | Keyset sin filtro de proyecto con `logs_received_idx` monocolunar necesita incremental sort para `id DESC` | BAJO | Aceptado v1 POR LITERAL del Schema ("Los filtros level/environment sin proyecto degradan … aceptable en v1"); sin migración; si los smokes evidencian degradación → deuda de índice para C9/C10 (NO C6) |
| **R-6.d** | `cursor` presente con filtros DIFERENTES de los de la página producida (F5 con filtros cambiados) → página semánticamente incoherente | BAJO | NO se rechaza (el cursor es opaco y la SQL es legítima; AppFlow flujo 4 paso 2 reinicia cursor con cambio de filtros — la UI nunca lo hace); documentado; tie del cursor al filtro por hash descartado como sobre-ingeniería |
| **R-6.h** | Pila viva con 1877 filas heredadas: asertos de conteo psql absolutos romperían el smoke | BAJO | Patrón C5: conteos RELATIVOS antes/después, nombres de agujas sufijados por ejecución (`smoke-c6-<TS>`), recomposición `--force-recreate` NUNCA `down -v` |
| **R-6.i**: (nuevo) | El render del DTO con el formatter truncado a segundos puede CONFUNDIR al diagóstico (2 filas del mismo batch muestran el mismo `received_at` en pantalla) | BAJO | Es el render VIGENTE de la casa (C2/C3 ratificado); el cursor porta la precisión (P1) — el orden visible es determinista por `id`; si C7/C8 lo reclaman, se discute EN C7/C8 |

---

> **STOP del flujo:** este proposal se presenta COMO GATE. La siguiente
> fase (sdd-spec) NO arranca hasta la aprobación explícita del usuario de
> los 6 puntos (P1–P6) — patrón C4/C5 (aprobación 6/6, con las
> matizaciones que el usuario quiera anotar).