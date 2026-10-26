---
project: Project_Log
change: C7
artifact: qa-plan
last_updated: 2026-10-26
status: draft
---

# QA Plan — C7 · Streaming WebSocket en tiempo real

> Plan de QA del corte C7 derivado EXCLUSIVAMENTE de `spec.md` (R7.1–R7.37,
> cierres S7-1…S7-12, divergencias D-C7-1/D-C7-2, E7.1–E7.20) y `design.md`
> (D-7.1…D-7.15, riesgos R-7.a…R-7.k). Aislamiento de autoría: el plan NO se
> apoya en el código implementado ni en los tests escritos — a un test FAIL
> debe poder señalársele SU caso del PLAN (QP-7.x), no al revés.

## 1. Estrategia y cobertura

- **20 casos QP-7.x** (1:1 con E7.1–E7.20): **11 [L]** (mvn en
  `backend/src/test/**`: `StreamingWsClientTest` — E7.2/3/4/7/8/16 —,
  `LatidoYBackpressureTest` — E7.12/13 —, pins puros sin BD
  `PorIdsSqlPinTest`/`FiltrosConsultaParseTest` — E7.19/E7.20 — y regresión
  `mvn test` completo — E7.18) y **9 [S]** (smoke nuevo
  `scripts/qa_c7_smoke.sh` — patrón canon C5/C6, salida
  `E7.n: PASS | FAIL | BLOQUEADO (detalle)` y exit 0/1/2; E7.17 invoca
  `scripts/ws_echo_test.py --solo-handshake --token <JWT>` SIN edición).
- **Las 10 parejas mock↔pila del spec §2.5 quedan cubiertas** (col.
  «Pareja» de la tabla §3). Absorciones declaradas: **E7.9 absorbe las
  parejas 4 y 5** (401×3 y Origin comparten caso de pila); **E7.10/E7.11
  reparten la pareja 6** (↔E7.12a y ↔E7.12c) y E7.10 lleva la pareja 10
  (parcial-OK con E7.16); **E7.5 absorbe las parejas 2 y 8**; **E7.6 absorbe
  las 3 y 9**. **E7.15 y E7.17 NO tienen pareja en §2.5** (E7.15 es DERIVADO
  del flujo 7 paso 4 — 403 `project_archived` pre-COMMIT ⇒ sin seam ⇒ sin
  frame; E7.17 es el permanente S7-7 del proxy con el artefacto C1) — cubren
  sus Done SIN [1:1] de pila dedicado, como el spec permite para los pins
  puros y derivados.
- **Regla de asertos** (§2): en TODO caso [L] se pina el TEXTO del frame /
  el status del handshake + el payload verbatim, y estado de BD/sesión
  cuando aplique; en [S], conteos RELATIVOS/delta y needles sufijados.
- **Pila smoke**: recomposición ÚNICA `podman compose up -d --build
  --force-recreate` (NUNCA `down -v` — pgdata; la recomposición TAMBIÉN
  publica `ws-console.html`); pila 3×healthy; **guard R-7.j ANTES de
  arrancar**: `FRONTEND_ORIGIN` ausente en `.env` ⇒ per S7-12/R7.5 el
  handshake CIERRA TODO (403) ⇒ escenarios WS **BLOQUEADO** con nota, exit 2
  global (NUNCA `"*"`, NUNCA default inventado en código); tmpdir 700 con
  trap — JWT y API key SOLO a tmpdir; needles sufijados
  `smoke-c7-<TS>-*`; smoke-of-the-smoke antes de la canónica (GET /me sin
  token → 401 `invalid_token`).
- **Suite [L]** (R7.33): `@SpringBootTest(RANDOM_PORT)` + cliente WS REAL
  del JDK contra Tomcat REAL (upgrade, handler, interceptor reales) con
  `LogsQueryRepository` `@MockitoBean` y `ObjectMapper` REAL del contexto;
  lote disparado INVOCANDO el bean real `LiveBroadcaster.onLotePersistido`
  (broadcast a mano, sin BD); JWT del `TokenService` del contexto; token
  expirado con TTL negativo (patrón `TokenServiceTest`); umbrales/LÍMITES de
  test por properties (R7.29) — **`application-test.yml` INTOCABLE**.

## 2. Regla de asertos (transversal a los casos)

### 2.A Frame `batch` §3.5 — CONTRATO byte-exacto (D-7.3/D-7.4, D-C7-1, D-C7-2)

1. **Shape top-level**: EXACTAMENTE **5 claves EN ESTE ORDEN** verbatim del
   §3.5 — `type`, `batch_id`, `project_id`, `received_at`, `logs` —,
   `type == "batch"`; el orden se verifica por la CADENA JSON (Jackson
   serializa el orden de inserción del `LinkedHashMap` — D-7.3).
2. **`batch_id`**: matchea `^b_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-
   [89ab][0-9a-f]{3}-[0-9a-f]{12}$` (UUID **v4** minúsculas, 36 chars);
   DISTINTO entre frames, NUNCA reutilizado (regex pinada en QP-7.2,
   unicidad en QP-7.3/QP-7.5).
3. **11 claves por elemento** de `logs` (LogDto COMPLETO, D-C7-1), snake_case
   EN ORDEN del DTO: `id, project_id, level, environment, timestamp,
   received_at, message, exception, metadata, source, correlation_id`,
   **nulls INCLUIDOS** (`exception`/`metadata` aparecen como `null`, jamás
   omitidos). El ejemplo de §3.5 muestra 10 (omite `project_id`) — el aserto
   del plan es de **11 DESDE EL LITERAL** "LogDto completo"; un FAIL de
   cuenta se señala a QP-7.2/QP-7.1, jamás al ejemplo abreviado.
4. **Orden del lote**: ids del frame == ids de ENTRADA (`porIds` reordena en
   Java — R7.24; el SELECT NO ordena, SIN `array_position`).
5. **`received_at` (top-level y por elemento) == TRUNCADO A SEGUNDOS** del
   común del lote (formatter C2 reutilizado — D-C7-2). En [S] la igualdad es
   contra el **psql truncado** (`to_char`/cast a segundos de la fila), NUNCA
   contra el instante crudo: la precisión completa NO viaja en el frame; un
   FAIL por "micros que faltan" sería error del PLAN.
6. **UN `TextMessage` por lote** — JAMÁS fragmentado, JAMÁS varios frames por
   lote, JAMÁS un mensaje JSON del servidor distinto de `batch` (aserto de
   AUSENCIA transversal: QP-7.10 lo confirma en pila; QP-7.16 en mock).
7. **Matching EN SERVIDOR** (S7-10): la unidad es el LOTE ÍNTEGRO (jamás
   recortado por log); conjunción de filtros activos; `environment`/`level`
   estrictos case-sensitive; `from`/`to` INCLUSIVE sobre el INSTANTE RAW
   (`from == to` LEGAL); `text` = contains case-insensitive del texto CRUDO
   (aguja `_`: el crudo NUNCA lleva escape — pin QP-7.20); sesión sin
   `set_filters` NO recibe frames (aserto QP-7.4-c).

### 2.B Handshake — sin oráculo de causa (anti-oráculo R-7.d)

1. **401 pre-upgrade** en las TRES causas de token (ausente/vacío, expirado,
   inválido): status único **401**, NUNCA 101, SIN cuerpo JSON, SIN
   `invalid_token` (familia REST del PANEL — otra superficie), jamás el
   entry point REST. La causa se distingue SOLO por el estímulo del setup,
   JAMÁS por la respuesta.
2. **403** para la familia Origin (`Origin` presente ≠ `frontend-origin` —
   comparación EXACTA sin normalizar; propiedad vacía → 403 a TODO
   handshake; **jamás `"*"`**); `Origin` AUSENTE → CONTINÚA (101 — clientes
   no-browser legítimos).
3. En [S] la **línea de status del proxy NO se reescribe** (se pinnea tal
   cual por nginx — QP-7.9).
4. NINGÚN JWT en los attributes de sesión; el rechazo loguea UNA línea WARN
   genérica (`"handshake WS rechazado"`) sin token/fragmentos/causa/query.

### 2.C Ciclo de vida — códigos de cierre contractual

1. **1013 (`TRY_AGAIN_LATER`)**: ÚNICO código de cierre por lentitud, para
   AMBOS vencimientos (sendTimeLimit y bufferSizeLimit — S7-9).
2. **1001 (`GOING_AWAY`)**: cierre por latido vencido (umbral desde el
   ÚLTIMO pong o desde la conexión; default 75 > 30 tolera un ping
   perdido).
3. **Limpieza** (R-7.c): tras close de CUALQUIER código, la sesión queda
   FUERA del registro — aserto observable: broadcast posterior no entrega
   nada a la cerrada y las OTRAS siguen (QP-7.12a/QP-7.13c).
4. **No-contrato y `set_filters` inválido**: sesión SIGUE VIVA (sin cierre)
   y NADA llega; el inválido CONSERVA los vigentes (el siguiente lote llega
   según el VIGENTE — QP-7.16d); el server JAMÁS contesta un `set_filters`.

### 2.D En [S] (pila)

- **Conteos RELATIVOS**: psql snapshot antes → delta después (agujas
  insertadas por el propio smoke); las filas heredadas de C5/C6 intocadas —
  jamás conteo absoluto.
- **Needles sufijados** `smoke-c7-<TS>-*` por ejecución; con `_` en psql
  comparados por `=`/`position()` (nunca LIKE crudo — regla C6).
- **Truncado D-C7-2 vía psql**: `received_at` del frame == truncado-a-
  segundos del psql de la fila (§2.A-5).
- **202 sin bloqueo** (R-7.a observable): latencias de ingesta ≤ ~2 s en
  E7.14a.
- Higiene: 0 hits `plk_`/`eyJ` en la salida del smoke (tmpdir 700 + trap).

## 3. Tabla de casos QP-7.1…QP-7.20

Parejas de §2.5 del spec: 1 E7.2↔E7.1 · 2 E7.3↔E7.5 · 3 E7.4↔E7.6 ·
4 E7.7↔E7.9 · 5 E7.8↔E7.9 · 6 E7.12↔E7.10/E7.11 · 7 E7.13↔E7.14 (tolerancia
R-7.k) · 8 E7.19↔E7.5 · 9 E7.20↔E7.6 · 10 E7.16↔E7.10 (parcial OK).

| ID | Fuente (E7.x) | Caso de prueba | Tipo | Fixtures | Pareja | Asertos obligatorios | Evidencia (dónde vive) |
|---|---|---|---|---|---|---|---|
| QP-7.1 | E7.1 `ingestFramesPorNginx` | ciclo end-to-end por nginx: handshake WS → `set_filters` → `POST /ingest` real → frame | [S] | pila post-recomposición; JWT+key a tmpdir; needle `smoke-c7-<TS>-a`; cliente Python heredoc SIN `Origin` (S7-12), frames MASKED | 1 | 101 + `Sec-WebSocket-Accept` correcto (SHA-1+base64); EXACTAMENTE 1 frame `batch`; shape §3.5 completo (§2.A-1/2/3: 5 claves en orden, batch_id v4, 11 campos); `logs.0.message == needle`; `received_at` == truncado-a-segundos del psql (D-C7-2); BD: delta = la aguja | `qa_c7_smoke.sh`: case `E7.1` |
| QP-7.2 | E7.2 `frameTrasCommitFormaExacta§3.5` | cliente JDK + set_filters calificando; `onLotePersistido(7, t, [12301, 12302])` con mock: 2 `FilaBatch` | [L] | RANDOM_PORT; repo `@MockitoBean`; `ObjectMapper` real; JWT del `TokenService` del contexto | 1 | §2.A-1..5: EXACTAMENTE UN `TextMessage`; 5 claves top-level EN ORDEN; `batch_id` matchea la regex UUID v4; `project_id == 7`; `received_at` == render del común (segundos); CADA elemento con las 11 claves del LogDto, nulls incluidos (D-C7-1); orden [12301, 12302] | `StreamingWsClientTest` · método previsto `frameTrasCommitFormaExacta§3.5` (T7.15) |
| QP-7.3 | E7.3 `batchDeUnoYOrdenDelLote` | invariante del lote: 1 elemento, orden y unicidad | [L] | ídem; mock devuelve filas DESORDENADAS | 2 | lote [501] → frame de 1 log (individual = batch de 1); lote [5,2,9,1,7] → frame PRESERVA el ORDEN DE ENTRADA (§2.A-4); `batch_id` DISTINTOS entre frames; `ids` vacío → CERO frames (defensiva R7.17) | `StreamingWsClientTest` · `batchDeUnoYOrdenDelLote` (T7.15) |
| QP-7.4 | E7.4 `setFiltersPorSesion` | disjunción por sesión + recalificación | [L] | TRES clientes JDK simultáneos; lotes de los proyectos 7 y 8 a mano | 3 | lote 7 → frame SOLO a c1 (c2 nada, **c3 SIN set_filters NADA** — S7-10/§2.A-7); lote 8 → SOLO c2; c2 recalifica a `project_id=7` en caliente → siguiente lote 7 llega a c1 Y c2; el server NO responde nada al `set_filters` (§2.C-4) | `StreamingWsClientTest` · `setFiltersPorSesion` (T7.15) |
| QP-7.5 | E7.5 `batchGrandeUnSoloFrameOrdenado` | batch de 3 → 1 frame ordenado | [S] | `POST /ingest/batch` real; needles `smoke-c7-<TS>-a/b/c`; psql para el RETURNING | 2 y 8 | EXACTAMENTE 1 frame con 3 logs EN ORDEN del lote (ids CRECIENTES == orden del RETURNING, psql); `batch_id` ≠ NINGUNO previo (unicidad); shape §3.5; delta BD = 3 | `qa_c7_smoke.sh`: case `E7.5` |
| QP-7.6 | E7.6 `setFiltersRecalificadoEnPila` | filtros recalificados con cliente vivo | [S] | cliente vivo tras E7.1; ingestas reales con needles; psql confirma filas | 3 y 9 | `level=ERROR` → ingesta needle WARN → NO llega frame (psql CONFIRMA la fila — descartada solo del stream); recalifica `level=WARN` → siguiente lote WARN SÍ llega; recalifica a otro project_id → NADA; matching case-sensitive: `ERROR` no matchea `WARN` | `qa_c7_smoke.sh`: case `E7.6` |
| QP-7.7 | E7.7 `handshake401PreUpgradeTresCausas` | inversa ×3 (token) | [L] | (a) sin `?token`; (b) expirado (`new TokenService(<mismo secreto>, -8)`); (c) inválido (basura/otra clave) | 4 | `WebSocketHandshakeException` con status **401 EN LAS TRES** (§2.B-1: SIN oráculo de causa), NUNCA 101, sin recursos del handler, sin cuerpo JSON, SIN `invalid_token` | `StreamingWsClientTest` · `handshake401PreUpgradeTresCausas` (T7.15) |
| QP-7.8 | E7.8 `originValidada` | inversa + happy + ausente (Origin) | [L] | (a) `Origin: http://evil.example`; (b) Origin == frontend-origin de test (por properties); (c) SIN header | 5 | (a) → **403** pre-upgrade; (b) → 101 + ciclo `set_filters`→`batch` NORMAL; (c) → **101** (cliente no-browser — S7-12); una Origin arbitraria NUNCA pasa (jamás `"*"` — §2.B-2) | `StreamingWsClientTest` · `originValidada` (T7.15) |
| QP-7.9 | E7.9 `handshake401YOriginPorProxy` | la pareja de QP-7.7/QP-7.8 contra pila | [S] | por nginx; heredoc Python; línea de status del proxy parseada | 4 y 5 | sin token → línea de status **401** (el proxy NO la reescribe — §2.B-3); `Origin: http://evil.example` con token → **403**; sin Origin con token → **101** + Accept correcto | `qa_c7_smoke.sh`: case `E7.9` |
| QP-7.10 | E7.10 `pingControlCada30s` | heartbeat de control con DEFAULT | [S] | la sesión de E7.1 PERMANECE abierta ≥2 heartbeats (~65 s de guion, solapado con E7.5–E7.15 — precedente C1: 75 s); DEFAULT 30 s (R7.29) | 6 (↔E7.12a) y 10 | frames opcode **0x9**, el cliente responde **0xA** (std RFC), intervalos ≈30 s (±5 s); aserto de AUSENCIA: NINGÚN mensaje JSON distinto de `batch` llegó (§2.A-6); guard R-7.j aplica | `qa_c7_smoke.sh`: case `E7.10` |
| QP-7.11 | E7.11 `reconexionLimpiaSinReplay` | caída limpio + hueco + reconexión | [S] | close limpio (0x8); psql cuenta filas del gap; nuevo handshake completo | 6 (↔E7.12c) | psql: N filas insertadas durante la caída; NUEVO handshake + `set_filters` de NUEVO (obligación del cliente) → próximo lote SÍ llega; aserto de AUSENCIA: NINGÚN frame re-emitido del gap (sin replay — R7.21); gap cubierto por keyset C6 (CITADO — E6.16/E6.23 lo certifican, no se re-prueba) | `qa_c7_smoke.sh`: case `E7.11` |
| QP-7.12 | E7.12 `cierrePorLatidoYLimpieza` | latido + limpieza + reconexión (props 1 s/2 s) | [L] | heartbeat 1 s / idle 2 s por properties; sesión MUDA = mock de `WebSocketSession` registrado en el mapa REAL (el cliente JDK responde pong AUTOMÁTICAMENTE — no puede simular mudez) | 6 | (a) el scheduler cierra la muda con **1001 GOING_AWAY** (§2.C-2) y la LIMPIA del registro; (b) cliente JDK real PERMANECE tras varios ciclos (aserto de NO-cierre); (c) tras close limpio, una NUEVA conexión funciona de CERO (handshake + `set_filters` + frame — reconexión S7-11) | `LatidoYBackpressureTest` · `cierrePorLatidoYLimpieza` (T7.16) |
| QP-7.13 | E7.13 `cierre1013AmbosLimites` | doble barrera (props 1 ms / 1 byte) | [L] | send-time-limit 1 ms / buffer-size 1 byte por properties; sesión lenta = mock con `sendMessage` bloqueado (latch) envuelto en el DECORADOR REAL | 7 | (a) vence sendTimeLimit → `SessionLimitExceededException` → close **1013** + limpieza; (b) desborda bufferSizeLimit → TAMBIÉN **1013** (AMBOS vencimientos — §2.C-1); (c) tras el 1013 de la lenta, un broadcast posterior llega INTACTO a las otras sesiones y `onLotePersistido` retorna limpio (R7.23) | `LatidoYBackpressureTest` · `cierre1013AmbosLimites` (T7.16) |
| QP-7.14 | E7.14 `backpressure1013EnPila` | 1013 en pila — tolerancia R-7.k | [S] | cliente NO consumidor (set_filters matcheando) + sesiones sanas + ingestas repetidas de 32 KiB (máximo por campo — §2.7); defaults pila (512 KiB / 2000 ms) | 7 | (a) las 202 NUNCA bloquean > ~2 s (R-7.a observable); (b) las sesiones sanas SIGUEN recibiendo; (c) 1013 del lento: si los defaults NO lo materializan en guion → **PASS CON NOTA** «1013 no materializado en pila — cubierto por pareja [L] E7.13» (patrón tolerancia Q23 de C3 — criterio: el pin 1013-dual vive SIEMPRE en QP-7.13; la ausencia de materialización en pila NO es FAIL del feature) | `qa_c7_smoke.sh`: case `E7.14` |
| QP-7.15 | E7.15 `archivadoDejaDeEmitir` | derivado flujo 7 paso 4 | [S] | PATCH `{"status":"ARCHIVED"}` → ingest → desarchivar | — | ingest archivado → **403 CUERPO EXACTO `{"code":"project_archived"}`** + SIN frame en ventana (psql: SIN fila nueva — no hay COMMIT ⇒ sin seam ⇒ sin frame); desarchivar → ingest → frame SÍ llega | `qa_c7_smoke.sh`: case `E7.15` |
| QP-7.16 | E7.16 `mensajesNoContratoIgnoradosYsinReplay` | tolerancia del handler + sin replay | [L] | cliente JDK conectado y calificado; frames adversos | 10 | (a) texto plano → sesión SIGUE, nada llega; (b) `{"type":"ping"}` → idem; (c) frame **BINARIO** → idem (R7.12); (d) tras un `set_filters` VÁLIDO uno INVÁLIDO (`level:"error"`, `project_id:"abc"`, clave extra `limit`, JSON malformado, valores `""`) → sesión sigue, SIN cierre y CONSERVA vigentes (siguiente lote según el VIGENTE — §2.C-4); (e) sin replay: lote emitido con el cliente desconectado NO aparece al reconectar — la nueva conexión no recibe nada hasta un lote NUEVO | `StreamingWsClientTest` · `mensajesNoContratoIgnoradosYsinReplay` (T7.15) |
| QP-7.17 | E7.17 `soloHandshakeProxyVerde` | permanente del proxy con el handler REAL | [S] | `scripts/ws_echo_test.py --solo-handshake --token <JWT>` TAL CUAL (artefacto C1 SIN edición) | — | 101 + Accept correcto + timeouts 3600 s del proxy (desambiguación S7-7: ESTO es el Done "echo test del proxy verde con el handler real" — la eco/protocolo vive SOLO en el smoke C7); misma forma en Q20 de qa_c2 (R-7.f) | `qa_c7_smoke.sh`: case `E7.17` + Q20 de `qa_c2_smoke.sh` (re-corrida T7.22) |
| QP-7.18 | E7.18 `regresionSuite146Verde` | regresión pura completa | [L] | `mvn test` completo en builder | — | **146 heredados + N de C7 VERDE** (R7.36); los E6.x del controller BYTE-COMPATIBLES tras extraer el parser (NINGÚN aserto editado — R7.28); pins de C5/C6 re-corridos intocados; qa_c5/qa_c6 smokes SIN cambios | suite completa (gates T7.17 pre-smoke y T7.23 post-smoke; cifra final en tests-written) |
| QP-7.19 | E7.19 `pinSqlPorIdsYOrden` | pin SQL + reorden en Java | [L] | MISMO paquete que el repo (package-private visible, patrón `LogsRepoSqlTest`); Mockito-JdbcTemplate | 8 | pin del LITERAL `SELECT_POR_IDS` (R7.25): 11 columnas EN ESE orden, `WHERE id = ANY (?)`, **1 placeholder**, SIN la palabra ORDER BY; firma `porIds(List<Long>`/record `FilaBatch(LogDto, OffsetDateTime received_at)`; filas mock DESORDENADAS + ids [3,1,2] → salida **[3,1,2]**; faltantes IGNORADOS sin error ([3,9,1] → [3,1]); el MISMO `FILA_DETALLE` sirve a ambos estamentos (pin del delegante `FILA_BATCH`); guard `porIds(null)`/vacía → lista vacía SIN tocar la BD | `PorIdsSqlPinTest` (T7.5) |
| QP-7.20 | E7.20 `pinParserExtraidoSemanticaADR7` | pin parser ÚNICO (checks 2–7) | [L] | unit puro sin Spring/BD de `FiltrosConsulta.parsear` | 9 | los 6 checks EN ORDEN (project_id → environment → level → from → to → from>to), primer-fallo-gana, con `FiltroInvalido.code/message/field` VERBATIM de la tabla R6.22 (literales byte-idénticos a los E6.x; from>to SIN field); caso feliz tipado completo (`patronTexto` `%timeout%` Y `texto` CRUDO SEPARADOS); aguja `_` → el patrón lleva `\_` y el crudo NO (D-7.13 — el stream matchea el crudo); `text=""` → patron `%%` + crudo `""`; `to` null → null; `patronIlike` re-corrida BYTE-A-BYTE (matriz `\`→`%`→`_`, sin tocar su test original C6) | `FiltrosConsultaParseTest` (T7.4) |

Cuenta: **20 casos — 11 [L]** (QP-7.2, QP-7.3, QP-7.4, QP-7.7, QP-7.8,
QP-7.12, QP-7.13, QP-7.16, QP-7.18, QP-7.19, QP-7.20) **+ 9 [S]** (QP-7.1,
QP-7.5, QP-7.6, QP-7.9, QP-7.10, QP-7.11, QP-7.14, QP-7.15, QP-7.17);
**10 parejas cubiertas** (con las absorciones declaradas en §1; E7.15 y
E7.17 sin pareja — derivado y permanente).

## 4. Transversales de la suite

- **Higiene de secretos**: 0 hits de `plk_`/`eyJ` en TODA la salida de los
  smokes y de mvn (grep post-ejecución — R7.35); JWT y API key SOLO en
  tmpdir 700 con trap; NINGÚN JWT en attributes de sesión (R7.7); los
  loggers de streaming jamás contienen mensajes/filtros/contenido de frame
  (R7.12: log DEBUG sin contenido; R7.7: WARN única línea genérica).
- **mvn completo**: `mvn -f backend/pom.xml test` — **146 heredados (C6) +
  N de C7** (≈ 25–30 métodos; cifra final en tests-written) = **146 + N**
  verde (R7.36). Se corre DOS veces: gate T7.17 (MANDATORIO pre-smoke) y
  T7.23 (post-smoke, solo-regresión).
- **Orden de ejecución**: (1) suite [L] completa verde — QP-7.2…QP-7.20 [L]
  (T7.17); (2) re-corrida `qa_c2_smoke.sh` con su Q20 en la nueva forma
  `--solo-handshake --token` + guards (edit T7.19, re-corrida en T7.22.4 —
  R-7.f); (3) recomposición + `qa_c7_smoke.sh` (script T7.21, ejecución
  T7.22.3, `bash -n` como smoke-of-the-smoke); (4) mvn final (T7.23);
  (5) re-corrida `qa_c3_smoke.sh` (T7.24, opcional/recomendada — E3.24 de
  intocables dif-0, los edits de C7 ya están en HEAD del corte; nota
  operativa: exige pila con `FRONTEND_ORIGIN` — R-7.j); (6) frontera
  (T7.25). El smoke NUNCA antes del mvn verde completo.
- **FAIL con pin [L] verde** (o viceversa): se REPORTA tal cual — no
  auto-arreglo fuera del guion (regla heredada de canon C5/C6).
- **Criterios de BLOQUEADO (exit 2)**: (a) `FRONTEND_ORIGIN` ausente en
  `.env` → guard R-7.j: los escenarios WS (y el Q20 de qa_c2) salen
  BLOQUEADO con nota «sin FRONTEND_ORIGIN el handshake C7 cierra (S7-12)» y
  **exit 2 global** — jamás se mitiga con `"*"` ni tocando código o
  `.env.example` (R-7.j: NO es cambio de código); (b) recomposición o
  healthy 3× falla; (c) falta JWT/key de la pre-fase (`! -s "$F_TOKEN"` /
  `CON_ENV != 1`). **exit 1** = cualquier FAIL de un E7.n. **exit 0** = los
  9 [S] PASS con E7.14 tolerado-con-nota si los defaults de pila no
  materializan el 1013 (R-7.k, §2.C del plan).
- **Tolerancia R-7.k (criterio anotado)**: el 1013 de pila (QP-7.14c) es
  sensible a los defaults (512 KiB / 2000 ms): PASS CON NOTA si no
  materializa — el pin CONTRACTUAL del 1013-dual vive en QP-7.13 [L] y
  NINGÚN corte se declara en riesgo por la no-materialización en guion.

## 5. Fuera del alcance de este plan («NO cubierto» — frontera para verify)

Para que `verify` posea la frontera, el plan de QA de C7 NO cubre:

1. **`ws-console.html` (R7.30–R7.32)**: la consola es ENSEÑANZA MANUAL al
   cerrar el corte (verify/archive cita la URL `http://<host>:8080/
   ws-console.html` + captura del ciclo `set_filters`→`batch`) — NO tiene
   E7.x, NO es dependencia del smoke (el smoke usa el cliente Python), NO
   añade nada al build del SPA y su contenido NO es contrato (P-1/S7-1).
2. **Del lado CLIENTE (C8)**: backoff 1→30 s, dedup por `id`, banda
   «● En vivo», banner «Sesión expirada»/«Streaming inactivo», estados
   ●/○, reenvío de `set_filters` tras reconectar (es OBLIGACIÓN del
   cliente — S7-11). C7 solo certifica las garantías del SERVIDOR
   (entrega única por sesión viva, sin replay, gap por keyset C6).
3. **token-en-primer-frame**: FUTURO documentado (TRD §2.8 — v1
   contractual = query param; riesgo R-7.d ACEPTADO por docs). La higiene
   de secretos se verifica en este plan por grep post-ejecución (§4), no
   por escenario dedicado.
4. **Deudas RETENIDAS**: 401 ISO-8859-1 → C8/C9 (NUNCA afecta al 401 SIN
   cuerpo del handshake — superficies distintas, S7-3);
   `client_max_body_size` → C9; smoke canónico `docker compose` → C9. Sin
   escenario nuevo en este corte.
5. **Replay/SSE/STOMP/SockJS (ADR-5)/push externo/alertas/métricas/rate
   limiting**: fuera de alcance v1 — sin escenario.
6. **Testcontainers**: P-2 CERRADA definitivamente — el plan NO la
   reevalúa; la sustitución (cliente WS JDK + RANDOM_PORT + pila viva) se
   verifica como CONSECUENCIA de E7.2–E7.16/E7.18, no hay caso propio.
7. **Migraciones/dependencias/env obligatorias**: NINGUNA (R7.2/R7.29) —
   sin escenario (cero `pom.xml`, cero `V1__init.sql`, `.env.example`/
   `compose.yaml` SIN edición; los umbrales de test viajan SOLO por
   properties — jamás tocando `application-test.yml`).
8. **`GET /logs`**: NO cambia un byte — se verifica por RE-CORRIDA
   (E7.18 byte-compat de los E6.x + `qa_c6_smoke.sh` sin cambios), no por
   casos nuevos del plan.
9. **Oráculo de causa en 401/403/1013/1001**: la familia responde un único
   status/código (§2.B/§2.C); la causa se deduce SOLO del estímulo del
   setup. Anti-oráculos verificados como PARTE de QP-7.7/QP-7.8/QP-7.13 —
   no como superficie de API distinta.
10. **Rendimiento más allá de E7.14a** (202 ≤ ~2 s, R-7.a observable): sin
    benchmark de latencia del broadcast ni de cola del executor («v1
    monousuario, tasa baja» es LITERAL del gate P-6 — no se mide).

## 6. Riesgos del plan

| Riesgo | Manejo |
|---|---|
| Los defaults de pila (512 KiB / 2000 ms) no materializan el 1013 en guion (R-7.k) | Tolerancia ESCRITA: QP-7.14 PASS CON NOTA «cubierto por pareja [L] E7.13» (patrón Q23 de C3); el pin 1013-dual vive SIEMPRE en [L]; un FAIL por materialización en pila con [L] verde no bloquea el corte |
| `.env` sin `FRONTEND_ORIGIN` mata TODO handshake de pila (S7-12 cerrado por defecto — R-7.j) | Guard en pre-fase y en qa_c2 (CON_ORIGIN): BLOQUEADO con nota + exit 2 — jamás mitigación `"*"`, jamás default inventado en código, jamás edición de `.env.example` |
| La ventana ~65 s del E7.10 alarga o inestabiliza el guion | Solapado con E7.5–E7.15 (design §5.3 — precedente C1: 75 s); el caso pinnea el DEFAULT 30 s (R7.29) — si no se logran ≥2 heartbeats limpios se anota y la pareja [L] QP-7.12a sostiene el pin de latido |
| Confusión 10-vs-11 campos en el frame (D-C7-1) | El aserto ANCLA las 11 claves DESDE el LITERAL «LogDto completo» del spec (§2.A-3); un FAIL de cuenta se señala a QP-7.2/QP-7.1, jamás al ejemplo abreviado de §3.5 |
| Comparar `received_at` del frame contra el instante crudo de psql (D-C7-2) | El aserto compara contra el PSQL TRUNCADO A SEGUNDOS (§2.A-5); un FAIL por micros ausentes sería error del PLAN, no del feature |
| El 401 pre-upgrade se confunde con el 401 REST del panel | §2.B-1: el aserto excluye EXPLÍCITAMENTE cuerpo JSON/`invalid_token`/entry point — superficies distintas (S7-3); un 401 CON cuerpo sería FAIL de QP-7.7/QP-7.9 |
| Smoke contra pila NO levantada | Pre-fase: recomposición/health falla → `E7.1..E7.17: BLOQUEADO (pila)` con exit 2, nunca falso PASS |
| El cliente JDK responde pong AUTOMÁTICAMENTE (no puede simular mudez/lenchura) | Las sesiones muda y lenta viven en MOCKS de `WebSocketSession` registrados en el mapa REAL (QP-7.12/QP-7.13) — la imposibilidad del JDK está cubierta por diseño (R7.33) |