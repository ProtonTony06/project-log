---
project: Project_Log
change: C6
artifact: qa-plan
last_updated: 2026-10-25
---

# QA Plan — C6 · Consulta keyset + filtros + detalle

> Plan de QA del corte C6 derivado EXCLUSIVAMENTE de `spec.md` (R6.1–R6.29,
> cierres S6-1…S6-10, D-C6-1, E6.1–E6.23) y `design.md` (D-6.1…D-6.11,
> divergencias D-S6-a/b/c, D-R6.*). Aislamiento de autoría: el plan NO se
> apoya en el código implementado ni en los tests escritos — a un test FAIL
> debe poder señalársele SU caso del PLAN (QP-6.x), no al revés.

## 1. Estrategia y cobertura

- **23 casos QP-6.x** (1:1 con E6.1–E6.23): **15 [L]** (MVC/unit-pin,
  `backend/src/test/java/com/projectlog/query/**` + pins) y **8 [S]** (psql
  smoke `scripts/qa_c6_smoke.sh`, patrón `qa_c5_smoke.sh`, salida
  `E6.n: PASS | FAIL | BLOQUEADO (detalle)` y exit 0/1/2).
- Las **12 parejas mock↔psql** del spec §2.6 quedan todas cubiertas (col.
  «Pareja» de la tabla §3): cada literal/code certificado en [L] tiene su
  [S] contra la pila.
- Regla de asertos: en TODO caso [M] se pina HTTP + CUERPO (code/message/
  field VERBATIM de la tabla R6.22 del spec) y, cuando aplique, estado de
  BD (psql: conteos RELATIVOS/delta — nunca absolutos, R-6.h; filas base
  heredadas de C5 intocadas; ORDER BY efectivo). Status-codes solos NO
  bastan.
- Pila smoke: recomposición ÚNICA `podman compose up -d --force-recreate`
  (NUNCA `down -v`); las **1877 filas heredadas NO se purgan** (R6.28):
  todo aserto de BD es relativo (delta = agujas insertadas por el propio
  smoke); tmpdir 700 con trap — JWT y API key SOLO a tmpdir, jamás a
  consola; needles sufijados por ejecución (`smoke-c6-<TS>-*`,
  `smoke_c6_<TS>_*`); smoke-of-the-smoke antes de la canónica (sondeo
  `GET /me` sin token → 401 `invalid_token`).

## 2. Asertos transversales (aplican a varios casos)

### 2.A Cursor (P-1 / D-R6.a — pin P1 del gate)

1. **Round-trip sin pérdida de micros**: para un instante con micros
   (`2026-10-16T22:31:04.123456Z`), encode → decode ⇒ MISMO instante exacto
   al micro/nano; ningún dígito de fracción se pierde ni se trunca
   (QP-6.12; pareja [S] QP-6.17 materializa lo mismo contra PG).
2. **Re-página que corta DENTRO de un mismo instante** (regla 9 del
   congelado: 1 batch = 1 solo `received_at`; sonda P-0: 67 instants /
   1877 filas): una página cuyo ÚLTIMO entregado comparte `received_at`
   (a MICROSEGUNDOS, leído por psql) con filas NO entregadas — el cursor
   con `r` a micros hace que la rama `received_at = :cursor_r AND id < ?`
   siga entregando las hermanas sin duplicar ni saltar (QP-6.17, criterio
   Done 2). El guion VERIFICA esa división y re-intenta con el límite
   mínimo que la produzca.
3. **Tolerancia ESTRECHA del decode**: CUALQUIER fallo de los 6 pasos
   (null/vacío, padding `=`, alfabeto no-url-safe, no-JSON, estructura/
   `r` no-parseable con `Instant.parse` (solo Z), claves extras,
   `i` no-integral/overflow/≤ 0) ⇒ LA MISMA excepción → LA MISMA respuesta
   400 `filter_cursor_invalid`, SIN oráculo de causa (anti-oráculo P1)
   (QP-6.9; pareja [S] QP-6.21).
4. **Sin oráculo de BD**: el `r` decodificado NUNCA se valida contra la
   BD — `r` futuro ⇒ 200 `{"items": [], "next_cursor": null}` (keyset
   legítimo; QP-6.9/QP-6.12).
5. **Byte-exacto del token del ejemplo del Schema** (sonda P-1):
   `codificar(parse("2026-10-16T22:31:04Z"), 12301)` ⇒
   `eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0` y el decode
   inverso ⇒ `{"r":"2026-10-16T22:31:04Z","i":12301}` (QP-6.12b).

### 2.B SQL pin (E6.11 / literal R6.16 — divergencia D-S6-a)

Asertos del pin `LogsQuerySqlTest` (constants package-private, MISMO
paquete de test `query/repository`; patrón `LogsRepoSqlTest`):

1. **17 placeholders `?`** contados DESDE EL LITERAL `SELECT_LISTADO`
   de R6.16 — NO desde la lista de 16 de R6.17 (D-S6-a):
   12 de los 6 filtros (duplicados IS-NULL/igualdad) + 3 de `cursorR`
   (guard IS NULL + `<` + `=`) + 1 de `cursorI` + 1 de `limit`.
2. **7 CASTs** en las posiciones IS-NULL, en este orden de tipos EXACTO:
   `bigint` (project_id), `text` (environment), `text` (level),
   `timestamptz` (from), `timestamptz` (to), `text` (text),
   `timestamptz` (guard del keyset) — D-C6-1, sondas P-2/P-3.
3. **Guard del keyset**: `(CAST(? AS timestamptz) IS NULL OR
   (received_at < ? OR (received_at = ? AND id < ?)))` — con AMBAS
   comparaciones `<` y `=` y el `id <`.
4. **`LIMIT ? + 1` VERBATIM** (sonda P-5); el Java NO suma +1.
5. `ORDER BY received_at DESC, id DESC` (literal, y el smoke certifica el
   ORDEN EFECTIVO en pila).
6. `SELECT_POR_ID`: 11 columnas EXACTAS (`exception` y `metadata` SÍ
   presentes) + `WHERE id = ?` (1 `?`).
7. **NO-`ESCAPE`**: el estamento NO contiene la palabra ESCAPE (el `\` es
   el escape DEFAULT de PG — S6-5).
8. El listado NO menciona `exception`/`metadata` (las 9 columnas del
   listado en su orden exacto).
9. (familia del fail-fast) `verifyNoInteractions` del repo ante TODO 400.

### 2.C Fail-fast (P6 / design §4 — 8 checks)

Orden contractual `limit → project_id → environment → level → from →
to → from>to → cursor`, primer-fallo-gana, TODOS antes de tocar la BD:
los asertos de orden están en QP-6.10 (`SIEMPRE` el 400 del check
previo ante múltiples fallos) + la verificación de frontera del repo
(`verifyNoInteractions`) en TODO caso 400 de QP-6.5–QP-6.10.

### 2.D PatronIlike — agujas del escape (P3 / D-R6.b)

Orden OBLIGATORIO `\` PRIMERO, luego `%`, luego `_` (escapar `%`→`\%`
antes que `\`→`\\` DOBLARÍA las barras — E6.13 lo pina). Aguja con `_`
literal ⇒ NO wildcard (contra-caso de la S-3 de C4). En psql las agujas
con `_` NUNCA van a un LIKE/ILIKE crudo: se comparan por `=` o
`position()` (o escapadas `\_`) — regla R6.20-a.

## 3. Tabla de casos QP-6.1…QP-6.23

Parejas de §2.6 del spec: 1 E6.1↔E6.16/23 · 2 E6.2↔E6.20 · 3 E6.3↔E6.20 ·
4 E6.4↔E6.20 · 5 E6.5↔E6.21 · 6 E6.6↔E6.21 · 7 E6.7↔E6.22 · 8 E6.9↔E6.21 ·
9 E6.12↔E6.17 · 10 E6.13↔E6.18 · 11 E6.14↔E6.22 · 12 E6.15↔pre-fase.

| ID | Fuente | Caso de prueba | Tipo | Fixtures | Pareja | Asertos [M] obligatorios |
|---|---|---|---|---|---|---|
| QP-6.1 | E6.1 `listadoShapeExactoItemsYNextCursor` | happy (página con trim) + frontera (sin fila extra) | [L] | JWT test (TokenService, patrón C3); mock repo: 51 filas y, en 2.ª request, 50 filas con limit 50 | 1 (→E6.16/E6.23) | 200; shape EXACTO `{"items":[9 campos por item], "next_cursor":"…"}`; el valor del token ⇒ decodificación de VUELTA (D-6.11) == (r, i) de la fila 50 del mock (la ÚLTIMA ENTREGADA, jamás la extra); en la request sin fila extra: `next_cursor: null` EXPLÍCITO (la CLAVE aparece en el JSON, no omitida); los items NO llevan `exception` ni `metadata` |
| QP-6.2 | E6.2 `pinDeLosDtos…` | pin (happy estructural) | [L] | JUnit puro + reflection sobre records; sin Spring | 2 (→E6.20) | `LogListItemDto` EXACTAMENTE 9 componentes con nombres literales (`id, project_id, level, environment, timestamp, received_at, message, source, correlation_id`); `LogDto` EXACTAMENTE 11 (los 9 + `exception: String` + `metadata: JsonNode` — pin del TIPO, D-S6-c); fuga imposible de compilar; serialización con claves literales (sin @JsonProperty) |
| QP-6.3 | E6.3 `detalleResponde200…` | happy + frontera (nulls) | [L] | JWT; mock repo con exception no-nula + metadata objeto (y otra fila con ambas null) | 3 (→E6.20) | 200 `LogDto` con las 11 columnas; `exception` = String tal cual; `metadata` = JsonNode con el objeto EXACTO (claves del jsonb intactas — `rs.getString`→`readTree`, NUNCA Map ni String crudo); con nulls → ambos campos `null` EXPLÍCITO en el JSON; fechas `timestamp`/`received_at` render C2 truncado a SEGUNDOS (R-6.i) mientras el instante completo solo viaja en el cursor |
| QP-6.4 | E6.4 `detalleIdInexistente…` | inversa (fila-0 legal) | [L] | JWT; mock `Optional.empty()` | 4 (→E6.20b) | 404; CUERPO EXACTO `{"code":"not_found","message":"No existe (quizás fue eliminado)"}` SIN `field`; NUNCA 200-lista ni queryForObject |
| QP-6.5 | E6.5 enums fuera | inversa + frontera (`""`, case) | [L] | JWT; mock repo (nunca llamado) | 5 (→E6.21) | `?environment=qa` y `?level=error` (minúscula) y `?environment=` → 400 `filter_invalid_value` con `field` culpable y message VERBATIM (`Valor de environment no válido; use development, staging o production` / `…TRACE, DEBUG, INFO, WARN, ERROR o FATAL`); en cada 400: `verifyNoInteractions(repo)` |
| QP-6.6 | E6.6 `fromMayorTo…` | inversa + frontera (from==to) | [L] | JWT | 6 (→E6.21) | `from=2026-01-02T00:00:00Z&to=2026-01-01T00:00:00Z` → 400 `filter_from_to_inverted`, message VERBATIM, **SIN field**; frontera `from == to` LEGAL (llega al repo — verify call) |
| QP-6.7 | E6.7 `limitFueraDeRangoONaN…` | inversa + frontera (1 y 200) | [L] | JWT | 7 (→E6.22) | `limit=0`, `201`, `abc`, `""` → 400 `filter_limit_invalid`, field `limit`, message VERBATIM; EL MISMO cuerpo para rango y no-numérico (anti-oráculo S6-6); `limit=1` y `limit=200` LEGALES |
| QP-6.8 | E6.8 id/instantes no parseables (3 P2) | inversa | [L] | JWT | vía E6.21 | `project_id=abc` → 400 `filter_invalid_value` field `project_id` msg «El filtro project_id debe ser un número»; `from=no-fecha` → campo `from`; `to=no-fecha` → campo `to` (msgs VERBATIM); `from=2026-10-16T22:31:04` (NAIVE, sin offset) → EL MISMO 400 de `from` (patrón S5-5) |
| QP-6.9 | E6.9 `cursorIlegible…` | inversa ×7 sub-casos | [L] | JWT; tokens adversos: (a) no-Base64url (`ñ`, `+`/`/`), (b) con padding `=`, (c) decodifica-no-JSON, (d) sin `r` o sin `i`, (e) `r` no-parseable/`+02:00`, (f) claves extra, (g) `i` no-entero/−1 | 8 (→E6.21) | TODOS → EL MISMO 400 `filter_cursor_invalid`, field `cursor`, message «El parámetro cursor no es válido» (anti-oráculo: sin causa); + `r` FUTURO válido → 200 `{"items":[],"next_cursor":null}` (keyset legítimo, sin 400) |
| QP-6.10 | E6.10 `ordenFailFast…` | inversa de ORDEN | [L] | JWT; queries con múltiples params mal | — | TODOS mal (`limit=abc` + `project_id=abc` + `level=error` + from naive + to no-fecha + cursor basura) → SIEMPRE 400 del check 1 (`filter_limit_invalid`), jamás el 2º/3º; limit OK + project_id+level mal → SIEMPRE project_id (check 2); to no-fecha + restantes mal → SIEMPRE to (check 6 antes del 7); from/to parseados invertidos + cursor basura → SIEMPRE `filter_from_to_inverted` (check 7 antes del 8); en TODO 400 de los 8: `verifyNoInteractions(repo)` — la BD NUNCA se toca |
| QP-6.11 | E6.11 `pinDeLos2Estamentos…` (dual fusionado) | pin SQL | [L] | `LogsQuerySqlTest` en `query/repository` (paquete igual al repo, sin reflection) | con E6.16/E6.20 | Pin del LITERAL R6.16 TAL CUAL (§2.B de este plan completo: 17 `?` desde el literal — D-S6-a; 7 CASTs tipados en orden; guard keyset; ORDER BY; `LIMIT ? + 1`; NO-ESCAPE; 9 columnas listado; 11 columnas detalle; NO exception/metadata en listado) + `verifyNoInteractions` de la familia E6.10 |
| QP-6.12 | E6.12 `codecRoundTrip…` | happy + frontera + inversa (anti-truncado) | [L] | Unit puro, sin Spring ni BD | 9 (→E6.17) | (a) round-trip `2026-10-16T22:31:04.123456Z` micros EXACTOS (transversal 2.A-1); (b) pin byte-exacto P-1 (transversal 2.A-5); (c) round-trip token sin fracciones y con nanos; (d) truncar el instante ⇒ re-encode produce token DIFERENTE (el codec NUNCA trunca) |
| QP-6.13 | E6.13 `escapeIlike…` | happy + orden + contra-caso | [L] | Unit puro | 10 (→E6.18) | `"a_b"` ⇒ `%a\_b%`; `"100%"` ⇒ `%100\%%`; `"a\b"` ⇒ `%a\\b%`; MATRIZ de orden con `_` y `\` juntos ⇒ resultado correcto SOLO con `\`-primero; contra-caso de aguja: `patronIlike("smoke_c6_X")` ⇒ `%smoke\_c6\_X%` y ese patrón NO matchea el needle con `X` en vez de `_` (cierre S-3 — mismo patrón usado en E6.18 contra la pila) |
| QP-6.14 | E6.14 `limitDefecto50YLegal200AlRepo` | frontera (defecto y máximo) | [L] | JWT; mock + ArgumentCaptor sobre la llamada `listar` | 11 (→E6.22) | sin `limit` → el repo recibe `50`; `?limit=200` → recibe `200`; la rama del trim usa limit+1 filas del mock y la extra se DESCARTA (never transportada); `text`/`cursor` sin cap: sin check de `length()` (D-6.10/S6-10) |
| QP-6.15 | E6.15 `ambosEndpointsNacenTras401…` | inversa (seguridad) | [L] | Sin JWT; JWT con `X-API-Key` sin JWT; JWT válido | 12 (→pre-fase smoke) | SIN JWT → 401 `invalid_token` del entry point del PANEL (CD-4; JAMÁS `api_key_invalid`); con `X-API-Key: plk_…` y sin JWT → TAMBIÉN 401 `invalid_token` (la API key no está en la cadena 2); con JWT válido → pasa al controller (firma del flujo del 200); SIN tocar NINGUNA línea de seguridad (R6.3) |
| QP-6.16 | E6.16 filtros combinables | happy | [S] | Recomposición `up -d --force-recreate`; API key vía `regenerate`; JWT a tmpdir; batch de agujas `smoke-c6-<TS>-k1..k3` (env/level distinguidos, p.ej. production/ERROR) | 1 | GET `?project_id=<id>&environment=production&level=ERROR` → 200 con SOLO los ids de las agujas; psql con la MISMA cláusula WHERE combinada → ids IDENTICOS (igualdad estricta REST↔psql); página 1 SIN filtros → 200 con 50 primeras y shape con `next_cursor`; BD: delta = SOLO las 3 agujas insertadas, las filas heredadas intocadas (ningún UPDATE/DELETE), ORDER BY efectivo `received_at DESC, id DESC` |
| QP-6.17 | E6.17 keyset ESTABLE (Done literal) | frontera (corte dentro del mismo instante) | [S] | Página 1 con `limit=3` (o límite mínimo que corte dentro de un instante — sonda P-0); captura psql del `received_at` a MICS de la última entregada; batch nuevo de K logs ENTRE páginas (received_at nuevo, más reciente) | 9 | (a) psql: decode del token ⇒ `r == received_at` COMPLETO a micros (NO truncado a segundos — pareja del E6.12a); (b) página 2 con ese cursor: SIN duplicados entre páginas, SIN reaparición de ids de la página 1, y la PRIMERA fila de la página 2 es la SIGUIENTE en el orden keyset (id adyacente bajo el MISMO received_at a micros); (c) con truncado las filas hermanas saltarían — este aserto materializa el Done «insertar entre páginas NO duplica ni salta» (D-R6.a); BD: sin DELETE/UPDATE, delta = batch K |
| QP-6.18 | E6.18 aguja `_` (cierre S-3) | happy + contra-caso | [S] | Ingesta individual: needle `smoke_c6_<TS>_timeout` (con `_` literal) + contraguja `smoke-c6-<TS>-sin-guion-bajo` con el MISMO prefijo y SIN `_` | 10 | GET `?text=smoke_c6_<TS>_timeout` → 200 con conteo == 1 (SOLO la aguja) y la contraguja NO matcheada (si `_` fuese comodín, matchearía); needle psql por `=`/`position()` (NUNCA la aguja cruda en un LIKE psql — R6.20-a); BD: delta = 2 agujas |
| QP-6.19 | E6.19 rango from/to offset e inclusive | frontera | [S] | Fila real de pila (frontera `from == received_at` de una aguja) ; query con offset NO-UTC `+02:00` y su equivalente `Z` | vía E6.16 | GET `?from=<t>+02:00&to=<t>+02:00` → 200 y MISMO resultado que el equivalente en `Z` (psql idénticos — normalización TRD §2.6); extremos INCLUSIVE: el log con `received_at == from` y otro con `== to` APARECEN (psql: los 2 ids presentes — sonda P-4); ORDER BY efectivo |
| QP-6.20 | E6.20 detalle en pila | happy + inversa (404) | [S] | Ingesta individual con `exception` no-nula y `metadata` objeto real `{"span":"checkout","attempt":2}`; id 999999999 inexistente | 2/3/4 | GET `/{id}` → 200: `exception` = texto COMPLETO (hasta 128 KiB solo en detalle) y `metadata` = OBJETO EXACTO (claves `span`/`attempt` legibles por needle — nada de string JSON entrecomillado); GET `/panel/logs/999999999` → 404 `{code:"not_found", message:"No existe (quizás fue eliminado)"}` sin field; BD: delta = 1 aguja |
| QP-6.21 | E6.21 codes 400 reales ×5 | inversa | [S] | JWT de tmpdir; 5 query strings adversos | 5/6/7/8 (y E6.8) | `from=..&to=..` invertidos → `filter_from_to_inverted`; `?level=error` → `filter_invalid_value` (field level); `?limit=0` → `filter_limit_invalid`; `?cursor=basura` → `filter_cursor_invalid`; `?project_id=abc` → `filter_invalid_value` (project_id) — TODOS con needle del CUERPO EXACTO (code Y field/message de R6.22), no solo status; BD: los 400 NO insertan ni modifican nada (delta 0) |
| QP-6.22 | E6.22 limit frontera en pila | frontera + inversa | [S] | JWT; pila con ≥200 filas accesibles (recorrido) | 7/11 | `?limit=200` → la página trae 200 (delta/comparativa psql sobre el recorrido) y `next_cursor` presente si hay fila 201; `?limit=201` → 400 `filter_limit_invalid`; SIN limit → la página trae 50 (la pareja del E6.14) |
| QP-6.23 | E6.23 fin de resultados + recorrido completo | frontera + happy (recorrido) | [S] | Aguja con `limit` pequeño (p.ej. 2) sobre UN SOLO batch (todos comparten received_at — regla 9); cursor encadenado | 1 | Recorrido encadenando `cursor=next_cursor` hasta `next_cursor: null` EXPLÍCITO; ids entregados SIN duplicados y SIN saltos (cubre el set completo del batch; última página sin extras); estable en cada punto (AppFlow flujo 4 paso 4: «Cargar más»); BD: ORDER BY efectivo + delta = agujas del batch |

Cuenta: 23 casos — 15 [L] (QP-6.1–6.15) + 8 [S] (QP-6.16–6.23); 12 parejas
cubiertas (incluidas las compartidas: E6.20 absorbe las parejas 2/3/4;
E6.21 absorbe 5/6/7/8 y el caso de E6.8; E6.22 absorbe 7/11).

## 4. Fixtures globales

| Fixture | [L]/[S] | Detalle |
|---|---|---|
| JWT de test | [L] | `TokenService(SECRETO_TEST, 8).issue(...)` → header `Authorization: Bearer` (patrón `ProjectsControllerMvcTest`; `application-test.yml` INTOCABLE) |
| Mock `LogsQueryRepository` | [L] | `@MockitoBean` (patrón C3/C5); filas semilla como `FilaConsulta`/`LogDto` de conveniencia |
| Suite de seguridad [L] | [L] | `@WebMvcTest(LogsController.class)` + `@Import(SecurityConfig.class, JwtAuthFilter.class, RestAuthenticationEntryPoint.class, CorsConfig.class)` + `@ActiveProfiles("test")` |
| Recomposición pila | [S] | `podman compose up -d --force-recreate` — UNA sola vez; NUNCA `down -v`; espera healthy (3×) |
| Credenciales de smoke | [S] | login → 200 (JWT a tmpdir); `POST /projects` → 201 `smoke-c6-<TS>-a` (id capturado incondicionalmente); `POST /projects/{id}/api-key/regenerate` → 201 (key a tmpdir); helpers `curl_ingest KEY …` y `curl_get JWT …` |
| Agujas / needles | [S] | sufijo `<TS>` por ejecución; con guión `-` y con `_` según escenario; en psql escapadas `\_` o comparación por `=`/`position()` (R6.20-a) |
| tmpdir | [S] | 700 + trap (JWT y key jamás a consola — R6.27) |
| Conteos BD | [S] | SIEMPRE relativos: snapshot antes → delta después (R-6.h); las 1877 filas heredadas de C5 intocadas (R6.28) |

## 5. Fuera del alcance de este plan («NOT cubierta»)

Para que `verify` posea la frontera: el plan de QA de C6 NO cubre:

1. **C10**: borrado de logs (`DELETE /logs/{id}`, flujo 8b), purga
   `/projects/{id}/logs` (8c), CASCADE (8a), botón «Eliminar registro», e
   `include=summary` / `GET /projects/{id}/summary`.
2. **C7 (streaming real)**: `HandshakeInterceptor`, `LiveBroadcaster`,
   frames `batch`/heartbeat/backoff; consumo del seam `IngestListener`
   (queda sin beans suscriptos); solo se pina que la SEMÁNTICA de filtros
   (patrón ILIKE) es reutilizable — no su reutilización.
3. **C8 (front)**: tabla del panel, «Cargar más», estados de pantalla,
   query-string compartible, dedup flujo 6b del LADO CLIENTE.
4. **C9 (nginx/deuda)**: `client_max_body_size`; smoke docker compose
   canónico (deuda heredada de C9); 401 ISO-8859-1 (deuda C8/C9) —
   RETENIDAS, sin escenario nuevo.
5. **Rendimiento de índices** más allá de un EXPLAIN básico (si se
   captura): no hay benchmark de `logs_dashboard_idx` vs
   `logs_received_idx` ni comprobación de planes; la degradación «aceptable
   en v1» es LITERAL del congelado y no se mide.
6. **Seguridad más allá de JWT-vs-sin-JWT en los 2 endpoints** (E6.15 +
   pareja 12): SIN roles/`@PreAuthorize`, sin escenarios de escalamiento,
   sin rate limiting (429 fuera de alcance v1, ADR-26).
7. **Testcontainers** (P4 — reevaluar en C7); **migraciones/pg_trgm/V2**
   (reservado, comentado).
8. **Caps de `text`/`cursor`** y normalización case de enums (S6-10 /
   D-6.10: sin ancla, NO se inventan); **oráculo de causa** en el 400 de
   cursor y del 401 (anti-oráculos P1/CD-4).
9. **`/api/v1/logs/{id}` no-mapeado y `GET /logs/abc`** con id no-numérico:
   ratificado SIN toque (R6.6) — sin handler ni escenario (sería code sin
   ancla, CD-3.9).
10. **Higiene de logging**: sin escenario NUEVO — no hay secretos en las
    lecturas (R6.26; el smoke de C5 ya cubre la superficie de ingesta).

## 6. Riesgos del plan

| Riesgo | Manejo |
|---|---|
| La ventana del corte dentro del mismo instante no se produce con limit=3 | Guion E6.17 re-intenta con el límite mínimo que la produzca antes de declarar FAIL (sonda P-0 la hace altamente probable) |
| Escapar la aguja `_` en psql la rompería o falsearía el contra-caso | Comparación por `=`/`position()` exclusivamente (R6.20-a) — jamás LIKE crudo |
| Confusión 16-vs-17 placeholders en el pin (D-S6-a) | El pin de QP-6.11 ANCLA la cuenta a 17 DESDE el literal R6.16; un FAIL de cuenta se señala al caso QP-6.11, no a la lista de R6.17 |
| Conteos absolutos de BD romperían el smoke ante pila heredada | Solo deltas/relativos (R-6.h, R6.28) — FAIL por conteo absoluto sería error del PLAN, no del feature |
| El token example P-1 depende de JDK exacto en Base64url | El pin es byte-exacto (R6.15-a composición manual); solo JDK ≥17 en builder — estable |
| Smoke contra pila NO levantada | Pre-fase: si la recomposición/health falla → salidas `E6.16..E6.23: BLOQUEADO (pila)` con exit 2, nunca FALSO PASS |