---
project: Project_Log
change: C7
artifact: tasks
last_updated: 2026-10-25
status: draft
---

# Tasks — C7 · Streaming WebSocket en vivo

> Fase sdd-tasks del corte C7. Fuente normativa: `spec.md` C7 (R7.1–R7.37,
> cierres S7-1…S7-12, divergencias D-C7-1/D-C7-2, E7.1–E7.20) y `design.md` C7
> (D-7.1…D-7.15, riesgos R-7.a…R-7.k). Ningún literal de API/frames se
> redefinirá en apply: el frame §3.5, `set_filters`, el 401/403 pre-upgrade y
> los estamentos SQL se copian VERBATIM. El spec y el design NO se editan.
> Anclas HEAD verificadas en 89b5d60 (por sdd-tasks, 2026-10-25):
> `WebSocketConfig` ancla `// C1: sin .withSockJS() — ADR-5; sin
> .addInterceptors(...) — C7` (L28), `IngestService` L126
> (`oyentes.forEach(o -> o.onLotePersistido(...))`), `qa_c2_smoke.sh` Q20
> (L864–870, eco completo SIN `--token`), `qa_c3_smoke.sh` L810 (SOLO lista
> intocables de E3.24 — ver T7.19), `.env` con
> `FRONTEND_ORIGIN=http://localhost:8080`.

## Frontera (vale para TODAS las tareas — R7.3/R7.4)

**DIFIERENCIA CLAVE CON C6: en C7 SÍ se editan ficheros existentes de
código.** La regla del anti-contaminación ("mvn inmediato tras cada tocada de
fichero existente") VUELVE — en C6 se sustituyó por corridas pre/post-smoke;
aquí rige por tarea (T7.1, T7.2, T7.3, T7.7, T7.11, T7.13) además de los
gates de fase.

**Ficheros EXISTENTES tocados por C7 — lista CERRADA y EXACTA (7):**

| # | Fichero | Operación | Tarea |
|---|---|---|---|
| E1 | `config/WebSocketConfig.java` | EDITA — SOLO 2 ediciones | T7.11 |
| E2 | `backend/src/main/resources/application.yml` | EDITA — AÑADE bloque `projectlog.streaming` + retira reserva cabecera | T7.13 |
| E3 | `query/controller/LogsController.java` | EDITA — delegación checks 2–7 | T7.2 |
| E4 | `query/FiltrosConsulta.java` | EDITA — añade `parsear`/`Filtros`/`FiltroInvalido` + mudanza constants | T7.1 |
| E5 | `query/repository/LogsQueryRepository.java` | EDITA — añade `SELECT_POR_IDS`/`porIds`/`FilaBatch`/`FILA_BATCH` | T7.3 |
| E6 | `streaming/EchoSocketHandler.java` | **REMOVE** (P-3) | T7.12 |
| E7 | `scripts/qa_c2_smoke.sh` | EDITA — Q20 → `--solo-handshake --token` + guards (D-7.14) | T7.19 |

`scripts/qa_c3_smoke.sh` = **dif-0 efectivo** (D-7.15 verificado por grep:
NO invoca el eco; solo lista `ws_echo_test.py` y aplicará `git diff
--exit-code` sobre los intocables de E3.24 — NO se edita, ver T7.20 y
T7.24).

**INTOCABLES (jamás diff)**: `backend/pom.xml` (cero deps — P-2 CERRADO),
`V1__init.sql`, `postgres/**`, `ingest/**` (el bean nuevo activa el seam SIN
diff — lista inyectada), `SecurityConfig`, `CorsConfig`, `auth/**`
(`LoginController.EXPIRES_AT_FORMATTER` y `TokenService` REUTILIZADOS por
referencia), `projects/**`, `keys/**`, `query/{CursorCodec,PatronIlike,
package-info}`, `query/dto/**` (LogDto/LogListItemDto tal cual),
`streaming/package-info.java`, `common/**`, `frontend/nginx.conf`,
`frontend/src/**` (ni una línea del SPA), `frontend/Dockerfile`,
`frontend/vite.config.ts`, `compose.yaml`, `.env.example`,
`application-test.yml`, `scripts/ws_echo_test.py` (byte-a-byte),
`scripts/qa_c5_smoke.sh`, `scripts/qa_c6_smoke.sh`, `scripts/qa_c3_qa_smoke.sh`,
`scripts/qa_c4_smoke.sh`, `docs/**`, `.atl/sdd/C6/**`, TODOS los tests de
C1–C6, y los 7 ficheros DIFERENTES de la lista cerrada de arriba.

**Piezas NUEVAS main** (`backend/src/main/java/com/projectlog/streaming/`):
`JwtHandshakeInterceptor.java`, `LogSocketHandler.java`,
`LiveBroadcaster.java`; NUEVO `frontend/public/ws-console.html`; NUEVO
`scripts/qa_c7_smoke.sh`; NUEVOs tests: `streaming/StreamingWsClientTest.java`,
`streaming/LatidoYBackpressureTest.java`, `query/repository/PorIdsSqlPinTest.java`,
`query/FiltrosConsultaParseTest.java`.

**Riesgo de reordenar fases (anti-contaminación estructural)**: si apply
escribe `streaming/` ANTES de cerrar la fase 1 (`query/`), el bean nuevo
`LiveBroadcaster implements IngestListener` ACTIVA el seam C5 sin diff en
`ingest/**` mientras `query/` está a medio mudar (constants movidas pero
controller sin delegar, o `porIds` inexistente): la ingesta 202 quedaría
compilando o corriendo sobre query roto y la regresión E6.x/146 perdería su
punto de comparación. Las dependencias ordenadas aquí (query PRIMERO, y
Fases 1→2→3 en secuencia estricta) lo prevenirn; apply NO DEBE saltar la
fase 1.

## Orden de dependencias

**Fase 1** (query/, con mvn tras cada edit + gate antes de streaming):
T7.1 → T7.2 → T7.3 → T7.4 → T7.5 → **T7.6 (GATE de fase: E7.18/E7.19/E7.20
verdes)** → **Fase 2** (streaming/): T7.7 → T7.8 → T7.9 → T7.10 (GATE) →
**Fase 3** (swap + config + consola): T7.11 → T7.12 → T7.13 → T7.14 →
**Fase 4** (tests [L]): T7.15 → T7.16 → T7.17 (E7.18 completo) → **Fase 5**
(smokes heredados): T7.19 → T7.20 → **Fase 6**: T7.21 (smoke C7 script) →
**Fase 7** (pila viva): T7.22 → T7.23 → T7.24 → **Fase 8**: T7.25 (frontera).

## Reglas transversales de apply

- Cada gate "mvn" = `mvn -f backend/pom.xml test` completo y VERDE
  (heredados + los de C7 escritos hasta ese punto). Si rota algo heredado,
  parar y arreglar ANTES de continuar — jamás continuar con rojo.
- Regla anti-contaminación INMEDIATA: tras CADA edit de un fichero EXISTENTE
  de código backend (T7.1, T7.2, T7.3, T7.7-n/a — es nuevo, T7.12 n/a, T7.13)
  corre su mvn marcado en la tarea antes de escribir el siguiente fichero.
- Los editores de SCRIPTS (`qa_c2_smoke.sh` T7.19) no corren mvn (no tocan
  código backend): su verificación es `bash -n scripts/qa_c2_smoke.sh`
  (sintaxis) + re-corrida en T7.22.
- Higiene S16 en TODO fichero nuevo: NINGÚN logueo de token/JWT/fragmentos
  ni de contenido de mensajes/filtros (R7.7/R7.12/R7.35).

---

## Bloque 1 — Fase 1: `query/` (parser extraído + porIds + delegación — P-8)

### T7.1 — EDITAR `query/FiltrosConsulta.java` (parsear + Filtros + FiltroInvalido + mudanza constants)

- **Fichero**: EXISTENTE (E4) `backend/src/main/java/com/projectlog/query/FiltrosConsulta.java`.
- **NUEVO vs EXISTENTE**: edita un existente. `patronIlike` y su javadoc
  quedan BYTE-A-BYTE (R7.3.4).
- **Qué añade** (R7.27, firmas EXACTAS — verbatim del spec §1.H-2):
  - `public static Filtros parsear(String projectId, String environment,
    String level, String from, String to, String text)` (throws
    `FiltroInvalido` runtime): los checks 2→7 del controller MUDAN aquí con
    MISMO orden, primer-fallo-gana, MISMAS parejas code/message/field de la
    tabla R6.22 de C6 (project_id Long.parse → environment enum
    case-sensitive con `""` inválido → level enum idem → from
    `OffsetDateTime.parse` naive rechazado → to idem → from>to sobre
    Instants, `from == to` LEGAL). El `text`: != null → `texto = text` y
    `patronTexto = patronIlike(text)`; null → ambos null. El texto NO se
    trimma.
  - `public record Filtros(Long projectId, String environment, String
    level, OffsetDateTime from, OffsetDateTime to, String texto, String
    patronTexto) {}`.
  - `public static final class FiltroInvalido extends RuntimeException` con
    `public final String code; public final String message; public final
    String field;` (null en `filter_from_to_inverted`).
  - **Mudanza de constants**: `CODE_FILTER_INVALID_VALUE`,
    `CODE_FILTER_FROM_TO_INVERTED` y sus `MSG_*` (5 messages) pasan a ser
    `static final` package-private de `FiltrosConsulta` con NOMBRES IGUALES
    y LITERALES byte-idénticos (única fuente ADR-7). QUITARlos de
    `LogsController` ocurre en T7.2 (no aquí: T7.1 debe compilar solo —
    mientras el controller también las declare, son dos ficheros con
    literales idénticos; el mvn de T7.1 valida verde igualmente).

- **mvn obligatorio INMEDIATO tras este edit**: `mvn -f backend/pom.xml test`
  verde = 146/146 heredados (el controller aún no usa `parsear`; compila
  igual).
- **Criterio de hecho**: mvn verde; firmas y constants idénticas a las del
  spec; `patronIlike` sin diff.
- **Escenarios**: base de E7.20 (pin en T7.4); base de E7.18 (en T7.2).

### T7.2 — EDITAR `query/controller/LogsController.java` (delegación checks 2–7 → parsear)

- **Fichero**: EXISTENTE (E3) `backend/src/main/java/com/projectlog/query/controller/LogsController.java`.
- **NUEVO vs EXISTENTE**: edita un existente. Cambio MÍNIMO
  comportamiento-PRESERVADO (R7.28).
- **Qué hace**: conserva check 1 (limit) y check 8 (cursor) EN SUS
  POSICIONES; conserva `CODE_FILTER_LIMIT_INVALID`/`CODE_FILTER_CURSOR_INVALID`/
  `CODE_NOT_FOUND` y sus `MSG_*` (no aplican al stream), el helper
  `error(...)` byte-a-byte y el código de listado/detalle; RETIRA del
  fichero las constants mudadas a `FiltrosConsulta` (T7.1) y SUSTITUYE el
  bloque de checks 2–7 por `final FiltrosConsulta.Filtros filtros; try {
  filtros = FiltrosConsulta.parsear(projectId, environment, level, from, to,
  text); } catch (FiltrosConsulta.FiltroInvalido ex) { return
  error(HttpStatus.BAD_REQUEST, ex.code, ex.message, ex.field); }`. Orden
  resultante byte-compat: limit → parsear (2..7) → cursor. El resto del
  método consume `filtros.projectId()/.environment()/.level()/.from()/.to()/
  .patronTexto()` (el `patron = text == null ? null : ...` inline
  desaparece). Import de `Filtros`/`FiltroInvalido`.
- **mvn obligatorio INMEDIATO tras este edit**: verde y ESTA VEZ los E6.x
  del controller (E6.1–E6.10, E6.14–E6.23) VERDES SIN EDICIÓN de ningún
  aserto (los cuerpos 400 salen idénticos de las constants mudadas — pin
  E7.18 parcial).
- **Criterio de hecho**: mvn verde con los E6.x intactos; respuesta de
  `GET /logs` byte-idéntica.
- **Escenarios**: E7.18 (parcial — regresión parser), soporte de E7.6/E7.20.

### T7.3 — EDITAR `query/repository/LogsQueryRepository.java` (porIds + SELECT_POR_IDS + FilaBatch)

- **Fichero**: EXISTENTE (E5) `backend/src/main/java/com/projectlog/query/repository/LogsQueryRepository.java`.
- **NUEVO vs EXISTENTE**: edita un existente. `SELECT_LISTADO`/`SELECT_POR_ID`/
  `listar`/`porId`/`FilaConsulta`/`FILA_LISTADO`/`FILA_DETALLE` BYTE-A-BYTE
  (R7.3.5).
- **Qué añade** (R7.24–R7.26, D-7.11 — VERBATIM del spec):
  - `static final String SELECT_POR_IDS` con el LITERAL EXACTO: 11 columnas
    EN ESE orden + `FROM logs WHERE id = ANY (?)` — SIN ORDER BY, 1
    placeholder (package-private para el pin E7.19, patrón `LogsRepoSqlTest`).
  - `@Transactional public List<FilaBatch> porIds(List<Long> ids)` (javadoc
    del spec VERBATIM: orden de entrada, faltantes IGNORADOS, UNA select
    por lote); `public record FilaBatch(LogDto log, OffsetDateTime
    received_at) {}`.
  - `FILA_BATCH` RowMapper anidado delegante que ROBA el instante RAW de la
    MISMA fila: `(rs, n) -> new FilaBatch(FILA_DETALLE.mapRow(rs, n),
    rs.getObject("received_at", OffsetDateTime.class))` — SIN duplicar la
    lambda de `FILA_DETALLE`, SIN fichero nuevo.
  - Binding: `jdbcTemplate.query(SELECT_POR_IDS, psSetter, FILA_BATCH)` con
    `ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids.toArray(new
    Long[0])))` (driver PG17); guard defensivo: `ids` null/vacía → lista
    vacía sin tocar BD. **Reorden en Java**: `LinkedHashMap<Long, FilaBatch>`
    por `log.id()` y salida iterando `ids` EN ORDEN de entrada (faltantes →
    skip, sin error); NADA de `array_position`.

- **mvn obligatorio INMEDIATO tras este edit**: verde (regresión entera;
  `porIds` aún sin llamador).
- **Criterio de hecho**: mvn verde; literal SQL idéntico al spec; el resto
  del fichero sin diff.
- **Escenarios**: E7.19 (pin en T7.5), E7.5 (pila, en T7.21).

### T7.4 — NUEVO test `query/FiltrosConsultaParseTest.java` (E7.20)

- **Fichero**: NUEVO `backend/src/test/java/com/projectlog/query/FiltrosConsultaParseTest.java`.
- **Qué hace** (design §7): unit puro SIN Spring/BD de `FiltrosConsulta.parsear`:
  (a) los 6 checks compartidos EN ORDEN (2→7), primer-fallo-gana, con
  `FiltroInvalido.code/message/field` EXACTOS, literales byte-idénticos a los
  E6.x de C5/C6 (project_id "abc", environment "qa"/"" , level "error"/"",
  from naive `2026-10-16T22:31:04`, to no-parseable, from>to SIN field);
  (b) caso feliz tipado completo: `projectId` Long, `patronTexto`
  `%timeout%` Y `texto` crudo SEPARADOS; (c) aguja `_`:
  `patronIlike`-derivado `patronTexto` lleva `\_` y el crudo NO (S7-10/D-7.13);
  (d) `text=""` → `patronTexto` `%%` + crudo `""` (S6-2); (e) `to` null
  (ausente) → `to` null; (f) `patronIlike` re-corrida byte-a-byte (matriz de
  escapes `\`→`%`→`_` + aguja S-3 de C4 — sin tocar su test original C6).
- **Criterio de hecho**: unit verde sin contexto Spring.
- **Escenarios**: E7.20 (pareja [S]: E7.6).

### T7.5 — NUEVO test `query/repository/PorIdsSqlPinTest.java` (E7.19)

- **Fichero**: NUEVO `backend/src/test/java/com/projectlog/query/repository/PorIdsSqlPinTest.java`
  (MISMO paquete que el repo — constants package-private visibles sin
  reflection, patrón `LogsRepoSqlTest` de C5).
- **Qué hace**: (a) pin del LITERAL EXACTO de `SELECT_POR_IDS` (R7.25): 11
  columnas EN ESE orden, `WHERE id = ANY (?)`, 1 placeholder, SIN la palabra
  ORDER BY; (b) pin de la firma `porIds(List<Long>)` + del record
  `FilaBatch(LogDto, OffsetDateTime received_at)`; (c) Mockito-JdbcTemplate
  con filas DESORDENADAS del mock e ids de entrada [3,1,2] → salida
  [3,1,2] (reorden en Java); (d) faltantes IGNORADOS sin error (ids [3,9,1]
  con el mock sin la 9 → salida [3,1]); (e) el MISMO `FILA_DETALLE` sirve a
  ambos estamentos (pin del delegante `FILA_BATCH`: LogDto completo de 11 +
  `received_at` RAW de la misma fila); (f) guard: `porIds(null)`/vacía →
  lista vacía, verify zero query (defensiva D-7.11).
- **Criterio de hecho**: pin verde sin BD.
- **Escenarios**: E7.19 (pareja [S]: E7.5).

### T7.6 — GATE de Fase 1: regresión query verde antes de streaming

- **Fichero**: NINGUNO (ejecución).
- **Qué hace**: `mvn -f backend/pom.xml test` completo: 146 heredados + los
  pins de T7.4/T7.5 — EXIGIR E7.18-parcial (E6.x byte-compatibles sin
  asertos editados), E7.19 y E7.20 VERDES antes de escribir UNA sola línea
  de `streaming/`. **Este gate es la defensa contra el riesgo de
  reordenación**: con query cerrado y verde, la activación del seam por el
  bean nuevo (Fase 2) es segura.
- **Criterio de hecho**: exit 0; los E6.x del controller todos verdes.
- **Escenarios**: E7.18 (parcial), E7.19, E7.20.
- **MANDATARIO: SÍ** — bloquea el paso a Fase 2.

## Bloque 2 — Fase 2: `streaming/` (paquete — P-7; SIN edit de SecurityConfig)

> Orden T7.7 (interceptor) → T7.8 (broadcaster) → T7.9 (handler) según
> encargo; NOTA de compilación: `LiveBroadcaster` inyecta `LogSocketHandler`
> (constructor del design §1.2), así que T7.8 se escribe contra la API del
> design y el mvn de la fase corre SOLO después de T7.9 — el tree puede
> quedar no-compilable entre T7.8 y T7.9, JAMÁS correr mvn ahí. `SecurityConfig`
> NO se toca (R7.8: cadena 3 `permitAll()` de `/ws/logs` queda tal cual; el
> auth vive SOLO en el interceptor).

### T7.7 — NUEVO `streaming/JwtHandshakeInterceptor.java`

- **Fichero**: NUEVO `backend/src/main/java/com/projectlog/streaming/JwtHandshakeInterceptor.java`.
- **Qué hace** (R7.5–R7.8, D-7.9 — orden del reject VINCULANTE):
  `@Component implements HandshakeInterceptor`; inyecta `TokenService` (por
  referencia, SOLO `verify`) y `@Value("${projectlog.security.
  frontend-origin:}")`.
  - **Origin PRIMERO**: propiedad vacía → RECHAZO de TODO handshake (S26
    extendido — NUNCA `"*"`) con 403; header `Origin` PRESENTE y ≠ propiedad
    (comparación EXACTA sin normalizar) → 403; header AUSENTE → continúa
    (S7-12: clientes RFC no-browser legítimos).
  - **token LUEGO**: query param EXACTO `token` desde
    `UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams()
    .getFirst("token")`; ausente o vacío → 401;
    `tokenService.verify(token).isEmpty()` → 401 (Optional sin distinguir
    causa — R-7.d). Si no → `return true`.
  - Rechazo SIEMPRE: `response.setStatusCode(...)` + `return false` —
    pre-upgrade, cuerpo VACÍO (NUNCA se escribe body), NUNCA el
    `RestAuthenticationEntryPoint` ni `invalid_token` (S7-3: otra
    superficie). NUNCA escribe attributes (NINGÚN JWT en sesión). `afterHandshake`
    no-op.
  - Log: UNA línea WARN genérica `"handshake WS rechazado"` — sin token, sin
    causa, sin cadena de query (R7.7, patrón anti-oráculo CD-4).
- **mvn**: tras T7.9 (compila solo, pero el gate de fase agrupa — el
  interceptor no depende de otros ficheros nuevos; si se quiere gate
  intermedio aquí, correrlo y volver a correr tras T7.9).
- **Criterio de hecho**: compila; el orden Origin→token y los status 403/401
  quedan pinnados por T7.15.
- **Escenarios**: E7.7, E7.8 (parejas [S]: E7.9).

### T7.8 — NUEVO `streaming/LiveBroadcaster.java` (nombre CONTRACTUAL)

- **Fichero**: NUEVO `backend/src/main/java/com/projectlog/streaming/LiveBroadcaster.java`.
- **Qué hace** (R7.17/R7.18, D-7.2/D-7.4 — PRIMER bean del seam C5, SIN
  tocar `ingest/**`):
  - `@Component implements com.projectlog.ingest.IngestListener`; inyecta
    `LogSocketHandler`, `LogsQueryRepository`, `ObjectMapper` (bean ÚNICO de
    Boot — D-3, NUNCA instancia suelta) y crea en constructor
    `Executors.newSingleThreadExecutor(ThreadFactory daemon "streaming-broadcast")`
    (FIFO — orden de lotes preservado; cola sin límite declarado — R-7.a
    cerrado).
  - `@Override onLotePersistido(long projectId, OffsetDateTime receivedAt,
    List<Long> ids)` corre en el HILO TOMCAT de la 202 y SOLO ENCOLA:
    `try { broadcast.execute(() -> distribuir(...)); } catch
    (RejectedExecutionException | RuntimeException ex) { log WARN sin
    contenido; }` — JAMÁS propaga una excepción al seam (R7.18; D-12 de C5
    deja las excepciones sin capturar — el catch vive AQUÍ).
  - `@PreDestroy apagar()`: `shutdown()` → `awaitTermination(5, SECONDS)`
    (los lotes encolados se INTENTAN emitir) → `shutdownNow()` (descarte —
    coherente con "sin replay").
  - `distribuir` (tarea del executor, en orden EXACTO — R7.17): (1) ids
    null/vacío → return; (2) `porIds(ids)` — lista vacía → return; (3)
    `batch_id = "b_" + UUID.randomUUID().toString()` (v4 minúsculas, UNA vez
    por lote — D-7.4); (4) por cada sesión del snapshot
    `handler.sesionesVivas()` (copia — sin iterar el mapa vivo): match
    (§2.3) → `envuelta.sendMessage(new TextMessage(frame))`; (5) política de
    excepciones: por SESIÓN → `SessionLimitExceededException` →
    `handler.cerrarPorLentitud(...)` (1013), cualquier otra `IOException` →
    swallow + log WARN genérico (un cliente malo no tumba a los demás); del
    LOTE (p. ej. porIds) → swallow + log ERROR — NO re-emite, NO reintenta
    (at-least-once YA garantizado por el COMMIT del seam — R-7.i).
  - Frame (R7.14/R7.15, D-7.3 — LinkedHashMap sobre record): EXACTAMENTE 5
    claves EN ORDEN — `type`="batch"; `batch_id`; `project_id` (Long del
    seam); `received_at` = `receivedAt.format(LoginController.EXPIRES_AT_FORMATTER)`
    (formatter C2 reutilizado por referencia — truncado a segundos, D-C7-2);
    `logs` = `filas.stream().map(FilaBatch::log).toList()` (LogDto COMPLETO
    de 11 campos, nulls incluidos, en orden del lote — D-C7-1). Frame UNA
    `TextMessage` — JAMÁS fragmentado, JAMÁS varios frames, JAMÁS un JSON
    del servidor distinto de `batch`.
  - Matching por sesión EN SERVIDOR (R7.16/D-7.6/D-7.7 — lote matchea por
    CONJUNCIÓN de filtros activos; environment/level/text matchean si ALGÚN
    registro cumple; from/to INCLUSIVE sobre el instante RAW
    `FilaBatch.received_at`; texto crudo con `toLowerCase(Locale.ROOT)`
    contains; `from == to` LEGAL; `ATTR_FILTROS == null` ⇒ sin calificar ⇒
    sin frames).
- **mvn**: después de T7.9 (compilation dependency — ver nota de fase).
- **Criterio de hecho**: compila y corre en suite T7.10.
- **Escenarios**: E7.2, E7.3, E7.4, E7.13c, E7.14a (pila), E7.5.

### T7.9 — NUEVO `streaming/LogSocketHandler.java` (decorador + scheduler)

- **Fichero**: NUEVO `backend/src/main/java/com/projectlog/streaming/LogSocketHandler.java`.
- **Qué hace** (R7.9–R7.13/R7.19–R7.23, D-7.1/D-7.10/D-7.2/D-7.8):
  - `@Component extends TextWebSocketHandler`; **dueño del registro**:
    `ConcurrentHashMap<String, ConcurrentWebSocketSessionDecorator>
    registradas` DENTRO del handler (NO bean separado; la mini-clase
    `SesionesActivas` NO se separa — 3 ficheros main). Attributes por
    sesión con `static final` (`streaming.filtros`, `streaming.ultimo-pong`)
    y acceso SIEMPRE bajo `synchronized (sesion.getAttributes())`.
  - `@Value` constructor de las 4 properties (`heartbeat-seconds`,
    `idle-timeout-seconds`, `send-time-limit-ms`, `buffer-size-bytes` —
    R7.29).
  - `afterConnectionEstablished`: `new ConcurrentWebSocketSessionDecorator(
    sesion, sendTimeLimitMs, bufferSizeBytes)` + registro + si nunca
    respondió, umbral desde ahora (D-7.10).
  - `handleTextMessage` (R7.10/R7.11, D-7.8): `objectMapper.readTree`;
    contrato SOLO objeto con `size == 2`, `type` EXACTO `"set_filters"`,
    `filters` objeto cuyas claves ENTRAN al conjunto cerrado
    `{project_id, environment, level, from, to, text}` (sin extras — JAMÁS
    `cursor`/`limit`); clave ausente o valor null = filtro sin aplicar
    (ejemplo §3.5 lleva `"to": null`); PRESENTE no-null debe ser String
    JSON salvo `project_id` que admite también número JSON (normalizado a
    String antes del parser); booleano/array/objeto → INVÁLIDO. Los 6
    valores van a `FiltrosConsulta.parsear` (`""` presente inválido en
    environment/level/from/to; `text=""` LEGAL): OK → `attributes.put`
    bajo lock + RETURN (el servidor JAMÁS responde nada — R7.11);
    `FiltroInvalido` → NO aplica el cambio, conserva vigentes, NO cierra,
    log WARN sin contenido (R-7.g). MENSAJE NO-CONTRATO (R7.12: texto que
    no sea el shape EXACTO, JSON malformado, texto plano, `{"type":"ping"}`)
    y frame BINARIO (`handleBinaryMessage` override): IGNORAR sin cerrar,
    log DEBUG sin contenido. La sesión cierra SOLO por 1013/latido/close.
  - `handlePongMessage` (R7.19): actualiza último pong bajo lock.
  - **Scheduler PROPIO** (D-7.10): `ScheduledExecutorService` single-thread
    daemon `"streaming-heartbeat"`, tick cada `heartbeat-seconds`: (a)PING
    a cada sesión vía el decorador — `org.springframework.web.socket.PingMessage`
    RFC 0x9, NUNCA JSON; (b) GC: `System.nanoTime() - ultimoPong >
    idle-timeout-seconds` → close `CloseStatus.GOING_AWAY` (1001 — decisión
    anclada S7-9; el ÚNICO código pineado por lentitud es 1013) + log WARN
    genérico; la limpieza la hace `afterConnectionClosed`.
  - `afterConnectionClosed` (cualquier código): `registradas.remove(sessionId)`
    — **ÚNICO punto de remove** del sistema (1013 y GC cierran y CONÍAN en
    él — R-7.c sin leak).
  - `cerrarPorLentitud(sesion)` (D-7.2/R7.22): close `CloseStatus.
    TRY_AGAIN_LATER` (1013) + log WARN ÚNICA línea sin contenido.
  - `sesionesVivas()`: snapshot (lista) para el broadcaster. `@PreDestroy
    apagar()` para el scheduler (patrón igual al broadcaster).
- **mvn obligatorio tras T7.9** (gate de Fase 2 — T7.10). VERDE 146 + pins
  C7 de Fase 1.
- **Criterio de hecho**: mvn verde; el paquete queda en EXACTAMENTE 3
  ficheros main NUEVOS y `EchoSocketHandler` aún presente (su REMOVE es
  Fase 3 — en este momento coexisten sin conflicto, el Echo sigue
  registrado en la ruta: la coexistencia es legal porque el swap es Fase 3).
- **Escenarios**: E7.4, E7.12, E7.13, E7.16 (parejas [S]: E7.6/E7.10–E7.14).

### T7.10 — GATE de Fase 2

- **Fichero**: NINGUNO. **mvn completo verde; MANDATORIO antes de Fase 3.**
- **Escenarios**: soporte de E7.2–E7.16 [L]; regresión entera.

## Bloque 3 — Fase 3: swap WebSocketConfig + REMOVE Echo + application.yml + consola

### T7.11 — EDITAR `config/WebSocketConfig.java` — EXACTAMENTE 2 ediciones

- **Fichero**: EXISTENTE (E1) `backend/src/main/java/com/projectlog/config/WebSocketConfig.java`.
- **NUEVO vs EXISTENTE**: edita un existente. Ruta `/ws/logs` y
  `@EnableWebSocket` BYTE-A-BYTE; nada más en el fichero (R7.3.1, design
  §1.4).
- **Qué hace**: (a) el handler del registro pasa de `EchoSocketHandler` a
  `LogSocketHandler` (campo + constructor + argumento — mismo patrón);
  (b) la línea ancla L28 `// C1: sin .withSockJS() — ADR-5; sin
  .addInterceptors(...) — C7` se RESUELVE con `.addInterceptors(
  jwtHandshakeInterceptor)` y el comentario nuevo del design:
  `// C7 ancla resuelta: .addInterceptors(jwtHandshakeInterceptor) — JWT+Origin
  pre-upgrade (spec §1.B); sin .withSockJS() — ADR-5 (frase conservada)`.
  La mitad `sin .addInterceptors(...) — C7` desaparece sustituida por la
  realidad; la frase `sin .withSockJS() — ADR-5` se CONSERVA.

### T7.12 — REMOVE `streaming/EchoSocketHandler.java` (P-3)

- **Fichero**: EXISTENTE (E6) `backend/src/main/java/com/projectlog/streaming/EchoSocketHandler.java` — **SE BORRA**.
- **Qué hace**: eliminación exacta (design §5: su sustitución estaba
  anunciada en su propio javadoc; sin registro en ruta queda código muerto
  y los congelados no conservan muertos). `streaming/package-info.java` y
  `scripts/ws_echo_test.py` BYTE-A-BYTE (intocables). "Verde con el handler
  real" = 101+Accept por nginx + ciclo set_filters→batch del smoke C7 (S7-7
  desambiguación) — no eco.
- **mvn obligatorio INMEDIATO tras T7.11+T7.12 (se contabilizan como UNA
  tocada conjunta: el edit sin el remove no compila y viceversa)**: verde.
- **Criterio de hecho**: mvn verde; `EchoSocketHandler` desaparece del tree;
  WebSocketConfig diff = solo lo anterior.
- **Escenarios**: R7.3.1/R7.3.6 (verify); soporte E7.1–E7.17.

### T7.13 — EDITAR `application.yml` (bloque `projectlog.streaming`)

- **Fichero**: EXISTENTE (E2) `backend/src/main/resources/application.yml`.
- **Qué hace** (R7.29 — VERBATIM): AÑADE tras el bloque `ingest:` el
  bloque `projectlog.streaming` con las 4 properties EXACTAS del spec
  (`heartbeat-seconds: ${WS_HEARTBEAT_SECONDS:30}`,
  `idle-timeout-seconds: ${WS_IDLE_TIMEOUT_SECONDS:75}`,
  `send-time-limit-ms: ${WS_SEND_TIME_LIMIT_MS:2000}`,
  `buffer-size-bytes: ${WS_BUFFER_SIZE_BYTES:524288}` + comentario
  `# C7 (R7.29): defaults del spec`), y RETIRA de la cabecera (L2: "sin
  WS_HEARTBEAT_SECONDS (heartbeat es C7)") la reserva. **0 env
  obligatorias nuevas**; `.env.example`/`compose.yaml` SIN edición; la
  suite [L] sobreescribe SOLO via `@SpringBootTest(properties=…)` — JAMÁS
  tocando `application-test.yml`.
- **mvn obligatorio INMEDIATO tras este edit**: verde (T7.14 agrupa).
- **Criterio de hecho**: mvn verde; bloque byte-idéntico al spec; únicos
  diffs del fichero = bloque añadido + comentario retirado.
- **Escenarios**: R7.29 (soporte de E7.12/E7.13 por props y E7.10 default 30 s).

### T7.14 — NUEVO `frontend/public/ws-console.html` (consola temporal — NO contrato)

- **Fichero**: NUEVO `frontend/public/ws-console.html` (el ÚNICO fichero de
  frontend de C7; el SPA NO se toca ni se importa la consola).
- **Qué hace** (R7.30–R7.32, design §6): página estática ÚNICO fichero,
  HTML+CSS+JS vanilla SIN framework NI build (estilo maquetas C3/C4 llanas,
  fondo claro, cero dependencias externas). Contenido mínimo: (1) cabecera
  con comentario HTML `"CONSOLA TEMPORAL C7 — no es parte del panel; se
  reemplaza en C8"` + banner VISIBLE con el mismo literal (R7.32); (2)
  login → `fetch POST /api/v1/panel/auth/login` → `{token, username,
  expires_at}` EN MEMORIA (variable JS — JAMÁS localStorage); (3) botón
  Conectar → `new WebSocket((location.protocol==='https:'?'wss':'ws') +
  '://'+location.host+'/ws/logs?token='+encodeURIComponent(token))` + botón
  Desconectar + reconexión MANUAL por botón (sin backoff — es utilidad);
  badge Conectado/Desconectado; feed de eventos open/close con `event.code`/
  error; (4) sección filtros §3.3 (`project_id` number, `environment` y
  `level` selects con vacío=null, `from`/`to` text ISO-8601 con zona,
  `text`) + botón "Aplicar set_filters" que construye el JSON EXACTO de
  §3.5 (`{"type":"set_filters","filters":{…}}` con las 6 claves SIEMPRE
  presentes, null por defecto, `project_id` como número cuando parsea); (5)
  lista de frames `batch` con JSON pretty (últimos 50, `slice(-50)`),
  contador total, `batch_id` resaltado. NO entra al smoke como dependencia
  (el smoke usa el cliente Python); NOTA: nginx lo sirve en la recomposición
  (se enseña al cerrar el corte — verify/archive cita URL + captura).
- **Sin mvn** (no toca código backend). Verificación: revisión de fichero
  (no build del SPA tocado).
- **Criterio de hecho**: fichero existe; ni una línea de `frontend/src` con
  diff; banner temporal presente.
- **Escenarios**: R7.30–R7.32 (enseñanza manual al cerrar — no E7.x).

### GATE de Fase 3 (dentro de T7.14's mvn en T7.13/T7.14 — explícito)

`mvn -f backend/pom.xml test` verde tras T7.13 — con `EchoSocketHandler`
removido y el swap hecho, la suite completa debe seguir 146 + Fase 1
VERDE. MANDATORIO antes de la Fase 4.

## Bloque 4 — Fase 4: tests [L] nuevos (design §7) + regresión completa

### T7.15 — NUEVO `streaming/StreamingWsClientTest.java` (E7.2, E7.3, E7.4, E7.7, E7.8, E7.16)

- **Fichero**: NUEVO `backend/src/test/java/com/projectlog/streaming/StreamingWsClientTest.java`.
- **Qué hace** (design §7, mecánica exacta): `@SpringBootTest(webEnvironment
  = RANDOM_PORT)` + cliente WebSocket REAL del JDK
  (`HttpClient.newHttpClient().newWebSocketBuilder()` contra Tomcat REAL —
  upgrade real, handler real, interceptor real) con `LogsQueryRepository`
  `@MockitoBean` (FilaBatch fabricados) y `ObjectMapper` REAL del contexto;
  el lote se dispara INVOCANDO el bean real `LiveBroadcaster.
  onLotePersistido(...)` (broadcast a mano, sin BD); JWT emitido por el
  `TokenService` del contexto (patrón `PanelAuthMvcTest`); token expirado
  con `new TokenService(<mismo secreto>, -8)` (TTL negativo — patrón
  `TokenServiceTest`); `frontend-origin` de test por properties;
  umbrales/límites por properties (R7.29 — nunca tocando
  `application-test.yml`).
  - **E7.2** frame tras commit forma EXACTA §3.5: set_filters calificando +
    `onLotePersistido(7, t, [12301, 12302])` → EXACTAMENTE UN TextMessage
    con 5 claves top-level EN ORDEN, `type=="batch"`, `batch_id` matchea
    `^b_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`
    (UUID v4), `project_id==7`, `received_at` == render del común, cada
    elemento con EXACTAMENTE las 11 claves del LogDto (nulls incluidos —
    D-C7-1).
  - **E7.3** lote de UNO [501] → frame de 1 log; lote [5,2,9,1,7] con el
    mock devolviendo filas DESORDENADAS → frame PRESERVA el orden de
    entrada; batch_id distintos entre frames; ids vacío → NINGÚN frame.
  - **E7.4** TRES clientes simultáneos: c1 `project_id=7`, c2 `project_id=8`,
    c3 SIN set_filters → lote del 7: SÓLO c1 (c2/c3 nada — sin calificar no
    recibe); lote del 8: sólo c2; c2 recalifica a 7 en caliente →
    siguiente lote del 7 llega a c1 Y c2.
  - **E7.7** handshake 401 ×3 (sin `?token`, token expirado, token
    inválido) → `WebSocketHandshakeException` con status 401 EN LAS TRES
    (sin oráculo de causa), NUNCA 101, sin recursos del handler, sin cuerpo
    JSON.
  - **E7.8** Origin: (a) `Origin: http://evil.example` → 403; (b) Origin ==
    frontend-origin de test → 101 + ciclo normal; (c) SIN header Origin →
    101; jamás `"*"`.
  - **E7.16** no-contrato/sin replay: (a) texto plano → sesión sigue;
    (b) `{"type":"ping"}` → idem; (c) frame BINARIO → idem; (d) tras un
    set_filters VÁLIDO uno INVÁLIDO (level "error", project_id "abc", clave
    extra `limit`, JSON malformado, valores "") → conserva vigentes (el
    siguiente lote llega según el VIGENTE); (e) sin replay: un lote emitido
    mientras el cliente está desconectado NO aparece al reconectar.
- **Criterio de hecho**: los 6 escenarios verdes.
- **Escenarios**: E7.2, E7.3, E7.4, E7.7, E7.8, E7.16.

### T7.16 — NUEVO `streaming/LatidoYBackpressureTest.java` (E7.12, E7.13)

- **Fichero**: NUEVO `backend/src/test/java/com/projectlog/streaming/LatidoYBackpressureTest.java`.
- **Qué hace**: properties encogidas (heartbeat 1 s / idle 2 s;
  send-time-limit 1 ms / buffer 1 byte). Sesión muda y sesión lenta = mocks
  de `WebSocketSession` registrados en el mapa REAL vía
  `handler.afterConnectionEstablished(mock)` (el cliente JDK responde pong
  AUTOMÁTICAMENTE — no puede simular mudez):
  - **E7.12** (a) la sesión mock SIN pong es cerrada por el scheduler con
    GOING_AWAY (1001) y LIMPIADA del registro; (b) el cliente JDK real
    PERMANECE tras varios ciclos (NO-cierre); (c) tras cerrar limpiamente
    una conexión real, una NUEVA conexión completa funciona de cero
    (handshake + set_filters + frame — reconexión/S7-11).
  - **E7.13** mock cuyo `sendMessage` bloquea (latch) envuelto en el
    DECORADOR REAL: (a) vence sendTimeLimit → `SessionLimitExceededException`
    → close 1013 + limpieza; (b) desborda bufferSizeLimit → idem 1013;
    (c) tras el 1013 de la lenta, un broadcast posterior llega INTACTO a
    las otras sesiones y `onLotePersistido` retorna limpio (R7.23).
- **Criterio de hecho**: los 2 escenarios verdes.
- **Escenarios**: E7.12, E7.13 (parejas [S]: E7.10/E7.11 y E7.14).

### T7.17 — GATE: `mvn test` completo (E7.18 — regresión pura)

- **Fichero**: NINGUNO. **mvn completo: 146 heredados (C6 vigentes) + N de
  C7 — VERDE. Los E6.x del controller byte-compatibles tras extraer el
  parser (NINGÚN aserto editado); los pins de C5/C6 re-corridos intocados;**
  los pins C7 (E7.19/E7.20) incluidos. MANDATORIO antes de los smokes.
- **Escenarios**: E7.18.

## Bloque 5 — Fase 5: smokes heredados ajustados (P-3/D-7.14/D-7.15)

### T7.19 — EDITAR `scripts/qa_c2_smoke.sh` — Q20 → `--solo-handshake --token` + guards (D-7.14)

- **Fichero**: EXISTENTE (E7) `scripts/qa_c2_smoke.sh`.
- **Qué hace**: Q20 (L864–870 de HEAD) pasa de la invocación eco COMPLETA
  (`timeout 200 python3 scripts/ws_echo_test.py`, que MUERE con el handler
  real — S7-7) a `python3 scripts/ws_echo_test.py --solo-handshake --token
  <JWT>` con el token de Q8 (`F_TOKEN` — sin token el handshake responde
  401). Guards añadidos (patrón canon, `return 0`, BLOQUEADO): el
  `CON_ENV != 1` ya existe; AÑADIR `! -s "$F_TOKEN"` → BLOQUEADO "sin token
  de Q8"; AÑADIR **`CON_ORIGIN != 1`** → BLOQUEADO "sin FRONTEND_ORIGIN el
  handshake C7 cierra (S7-12)" (R-7.j — en HEAD `CON_ORIGIN` ya se setea en
  la carga de `.env` L384; falta el guard). El comentario del case y el
  texto del mensaje se ajustan al nuevo modo. NADA más del fichero.
- **Sin mvn** (script). Verificación: `bash -n` + re-corrida en T7.22.
- **Criterio de hecho**: Q20 con la nueva forma; guards presentes; el resto
  del fichero sin diff.
- **Escenarios**: E7.17 (misma forma), soporte R-7.f.

### T7.20 — `scripts/qa_c3_smoke.sh` — VERIFICADO dif-0, NO se toca (D-7.15)

- **Fichero**: NINGUNO (verificación + decisión).
- **Qué hace**: grep de HEAD CONFIRMADO (por sdd-tasks): `qa_c3_smoke.sh`
  NO invoca `ws_echo_test.py` — su ÚNICA referencia es L810, dentro del
  array `intocables` del E3.24 (junto a `qa_c2_smoke.sh`,
  `application.yml`, `nginx.conf`, `compose.yaml`, `docs/mockups`), con
  `git diff --exit-code`. Conclusión D-7.15 ratificada: la edición
  declarada en R7.3.7 para qa_c3 NO tiene punto de anclaje real — el corte
  NO lo edita (dif-0 efectivo).
- **NOTA OPERATIVA para la re-corrida (T7.24)**: E3.24 coteja
  `git diff --exit-code` sobre `qa_c2_smoke.sh` y `application.yml`, que C7
  SÍ edita — la verificación pasa SOLO con los commits de C7 HECHOS
  (diff vs HEAD, no vs C1). Aplicarla antes del commit de la Fase 3/5
  fallaría falsamente. Si al aplicar se detectara cualquier invocación al
  eco (re-grep obligatorio), aplicar el patrón D-7.14.
- **Criterio de hecho**: dif de qa_c3 = 0 al final del corte.
- **Escenarios**: R7.3.7 (dif-0); E3.24 re-corrida (T7.24).

## Bloque 6 — Fase 6: `scripts/qa_c7_smoke.sh` NUEVO (guion cronológico — design §5.3)

### T7.21 — NUEVO `scripts/qa_c7_smoke.sh` completo (patrón canon C5/C6)

- **Fichero**: NUEVO `scripts/qa_c7_smoke.sh`.
- **Qué hace** (R7.34, guion EXACTO del design §5.3 — salida
  `E7.n: PASS | FAIL | BLOQUEADO (detalle)`, exit 0/1/2; needles sufijados
  `smoke-c7-<TS>-*`; JWT y key SOLO en tmpdir 700 con trap; conteos
  RELATIVOS; cliente WS Python INLINE en heredoc — RFC 6455 stdlib:
  socket a `ws://…/ws/logs?token=…` SIN header `Origin` (S7-12), frames
  MASKED (obligatorio cliente→server), parse del status HTTP del handshake +
  cálculo del `Sec-WebSocket-Accept` esperado (SHA-1+base64), lector de
  frames con opcodes (0x1 texto / 0x9 ping → responde 0xA / 0x8 close),
  timeouts duros por caso; cero dependencias nuevas):
  - **Pre-fase** (sin ID, parte de E7.1): guard R-7.j — `FRONTEND_ORIGIN`
    ausente en `.env` → escenarios WS BLOQUEADO con nota, exit 2 global
    (jamás `"*"`); recomposición ÚNICA `docker compose -f compose.yaml up -d
    --build --force-recreate` (NUNCA `down -v` — pgdata intocado; la
    recomposición TAMBIÉN publica `ws-console.html`); pila 3×healthy;
    smoke-of-the-smoke (GET /me sin token → 401 `invalid_token`); login
    (JWT → tmpdir); POST /projects (id → tmpdir); regenerate (key →
    tmpdir).
  - **E7.9** handshake por nginx: sin token → línea de status **401** (el
    proxy NO reescribe); `Origin: http://evil.example` con token → **403**;
    sin Origin con token → **101** + Accept correcto.
  - **E7.17** solo-handshake permanente: `scripts/ws_echo_test.py
    --solo-handshake --token <JWT>` TAL CUAL (artefacto C1 SIN edición) →
    101 + Accept + timeouts 3600 s del proxy. (Desambiguación S7-7: ESTO es
    el Done "echo test del proxy verde con el handler real".)
  - **E7.1** ingesta → frame por nginx: handshake WS con JWT real → 101;
    envía `set_filters` EXACTO (project_id del proyecto smoke); `POST
    /api/v1/ingest` REAL (key real, needle `smoke-c7-<TS>-a`) → EXACTAMENTE
    1 frame `batch`: shape §3.5 + 11 campos + `received_at` == truncado-
    a-segundos del psql de la fila (D-C7-2) + `logs.0.message == needle`.
  - **E7.5** batch de 3 → 1 frame: `POST /api/v1/ingest/batch` (needles
    a/b/c) → EXACTAMENTE 1 frame con 3 logs EN ORDEN del lote (ids
    CRECIENTES == orden del RETURNING, verificado por psql) + `batch_id` ≠
    ninguno previo.
  - **E7.6** set_filters recalificado: cliente vivo `level=ERROR` → ingesta
    needle WARN → NO llega frame (psql confirma la fila); recalifica
    `level=WARN` → siguiente lote WARN SÍ llega; recalifica a otro
    project_id → nada.
  - **E7.15** archivado deja de emitir: `PATCH /api/v1/panel/projects/{id}`
    `{"status":"ARCHIVED"}` → `POST /api/v1/ingest` → **403
    `{"code":"project_archived"}`** → sin frame en ventana (psql: sin fila
    nueva — no hay COMMIT); desarchivar → ingest → frame SÍ llega.
  - **E7.10** ping de control 30 s: la sesión del E7.1 PERMANECE abierta
    ≥2 heartbeats (~65 s de guion SOLAPADOS con E7.5–E7.15 — precedente C1:
    75 s): observa opcode **0x9**, responde 0xA, mide intervalos ≈30 s
    (±5 s) y CONFIRMA que NINGÚN mensaje JSON distinto de `batch` llegó
    (literal §3.5). Guard R-7.j aplica.
  - **E7.11** reconexión limpia sin replay: cierra limpio (0x8), psql cuenta
    filas insertadas durante la caída, NUEVO handshake completo +
    `set_filters` de nuevo → próximo lote SÍ llega; aserto de AUSENCIA:
    ningún frame re-emitido del gap (el fallback C6 se CITA — E6.16/E6.23
    lo certifican, no se re-prueba).
  - **E7.14** backpressure 1013 (tolerancia R-7.k): cliente NO consumidor
    (set_filters matcheando) + sesiones consumidoras sanas + ingestas
    repetidas con mensajes de 32 KiB (máximo por campo — límite §2.7): (a)
    las 202 NUNCA bloquean más de ~2 s (R-7.a observable), (b) las sesiones
    sanas siguen recibiendo, (c) el 1013 del lento: si los defaults de pila
    (512 KiB / 2000 ms) no lo materializan en el guion, el smoke lo ANOTA
    ("1013 no materializado en pila — cubierto por pareja [L] E7.13") y
    PASS con nota (patrón tolerancia Q23 de C3).
- **Sin mvn** (script). Verificación: `bash -n scripts/qa_c7_smoke.sh`
  (smoke-of-the-smoke del script) + ejecución real en T7.22.
- **Criterio de hecho**: script completo con los 9 E7.x codificados +
  guard R-7.j + tolerancia R-7.k.
- **Escenarios**: E7.1, E7.5, E7.6, E7.9, E7.10, E7.11, E7.14, E7.15, E7.17.

## Bloque 7 — Fase 7: ejecución contra pila viva

### T7.22 — Recomposición + smoke C7 + smoke C2 re-corrido

- **Fichero**: NINGUNO (ejecución).
- **Qué hace**: (1) `docker compose -f compose.yaml up -d --build
  --force-recreate` para desplegar TODO C7 (backend con streaming, nginx con
  la consola); **NUNCA `down -v` — pgdata INTOCADO** (las filas heredadas de
  C5/C6 quedan); check `FRONTEND_ORIGIN` en `.env` ANTES de arrancar (R-7.j
  — en esta VM ya está: `http://localhost:8080`). (2) pila 3×healthy. (3)
  `bash scripts/qa_c7_smoke.sh` → registrar salida `E7.n` por ID. (4)
  Re-corrida `bash scripts/qa_c2_smoke.sh` → Q20 con el nuevo modo
  `--solo-handshake --token` VERDE (R-7.f). (5) (opcional/recomendado)
  `bash scripts/qa_c6_smoke.sh` SIN cambios (no toca WS) → verde.
- **Criterio de hecho**: E7.1, E7.5, E7.6, E7.9, E7.10, E7.11, E7.14 (con
  tolerancia si aplica), E7.15, E7.17 PASS; Q20 PASS. FAIL con pin [L]
  verde → reportar tal cual (no auto-arreglar fuera del guion).
- **Escenarios**: E7.1, E7.5, E7.6, E7.9, E7.10, E7.11, E7.14, E7.15, E7.17.

### T7.23 — `mvn test` final completo (solo-regresión post-smoke)

- **Fichero**: NINGUNO. Re-correr `mvn -f backend/pom.xml test` COMPLETO
  tras el smoke: 146 heredados + N de C7 (= 146 + N; cifra final en
  tests-written) VERDE (R7.36). MANDATORIO.
- **Escenarios**: E7.18 (completo).

### T7.24 — qa_c3 re-corrida (dif-0 de intocables) — OPCIONAL pero recomendada

- **Fichero**: NINGUNO. Con los commits de C7 ya hechos (nota de T7.20),
  re-correr `bash scripts/qa_c3_smoke.sh` (o al menos su E3.24): la lista
  intocables debe salir VERDE con el working tree limpio (los edits de C7
  a `qa_c2_smoke.sh`/`application.yml` son parte del HEAD nuevo, no diff).
- **Criterio de hecho**: E3.24 PASS o nota de por qué se difiere (p. ej.
  pila ocupada por el smoke C7).
- **Escenarios**: R7.36 (regresión smokes), soporte R-7.f.

## Bloque 8 — Fase 8: verificación final de frontera

### T7.25 — Verificación final de frontera + cobertura

- **Fichero**: NINGUNO (git diff / revisión).
- **Qué hace**:
  1. `git status` + diff: los ficheros existentes M/D deben ser EXACTOS —
     y NADA MÁS: `config/WebSocketConfig.java` (M), `application.yml` (M),
     `query/controller/LogsController.java` (M), `query/FiltrosConsulta.java`
     (M), `query/repository/LogsQueryRepository.java` (M),
     `streaming/EchoSocketHandler.java` (D/removido),
     `scripts/qa_c2_smoke.sh` (M) — **7 existentes, ni uno más; y
     `scripts/qa_c3_smoke.sh` dif-0**. Todos los INTOCABLES de la sección
     Frontera con diff nulo (`ingest/**`, `SecurityConfig`, `auth/**`,
     `pom.xml`, `application-test.yml`, `compose.yaml`, `.env.example`,
     `frontend/src/**`, `scripts/ws_echo_test.py`, `qa_c5/c6_smoke.sh`,
     docs, TODOS los tests C1–C6).
  2. NUEVOS esperados: 3 main streaming + `ws-console.html` +
     `qa_c7_smoke.sh` + 4 tests (T7.4/T7.5/T7.15/T7.16) + artefactos
     `.atl/sdd/C7/`.
  3. Checklist de cobertura E7.1–E7.20 COMPLETA (11 [L] + 9 [S]) y las 10
     parejas mock↔pila de §2.5 del spec cubiertas (E7.2↔E7.1, E7.3↔E7.5,
     E7.4↔E7.6, E7.7↔E7.9, E7.8↔E7.9, E7.12↔E7.10/E7.11, E7.13↔E7.14 con
     tolerancia anotada si aplica, E7.19↔E7.5, E7.20↔E7.6, E7.16↔E7.10
     parcial OK).
  4. Divergencias CITADAS trazables (no corregidas): **D-C7-1** (frame con
     LogDto completo de 11 campos aunque el ejemplo §3.5 muestre 10 —
     pinnada por E7.2), **D-C7-2** (fechas del frame truncadas a segundos
     con el formatter C2 — pinnada por E7.1/E7.2 y verificada en pila),
     **D-7.15** (qa_c3 dif-0 — evidencia del grep de esta tasks),
     **R-7.j** (guard CON_ORIGIN en qa_c2 y qa_c7; `.env` local ya cumple),
     **R-7.k** (tolerancia E7.14 PASS CON NOTA si los defaults no
     materializan 1013). Deudas retenidas sin toque: smoke `docker
     compose` → C9; 401 ISO-8859-1 → C8/C9; nginx `client_max_body_size` →
     C9. Deuda CERRADA en archive: Testcontainers (P-2 — sustitución
     documentada); hueco "seam sin beans" de C6 (cerrado con
     `LiveBroadcaster`).
  5. La consola `ws-console.html` queda lista para su ENSEÑANZA al cerrar
     el corte (verify/archive cita la URL `http://<host>:8080/ws-console.html`
     + captura del ciclo set_filters→batch — R7.32).
- **Criterio de hecho**: diff con EXACTAMENTE los 7 existentes tocados
  (6 M + 1 D) + los NUEVOS esperados; checklist completa sin hueco.
- **Escenarios**: cierre de R7.3/R7.4/R7.36/R7.37; trazabilidad §5 del spec.

---

## Puntos mvn obligatorios intermedios (resumen — regla anti-contaminación VUELVE)

| Tarea | Fichero existente tocado | mvn |
|---|---|---|
| T7.1 | FiltrosConsulta.java (M) | INMEDIATO tras el edit |
| T7.2 | LogsController.java (M) | INMEDIATO tras el edit |
| T7.3 | LogsQueryRepository.java (M) | INMEDIATO tras el edit |
| T7.6 | — | GATE Fase 1 (bloquea streaming) |
| T7.10 | — | GATE Fase 2 |
| T7.11+T7.12 | WebSocketConfig.java (M) + EchoSocketHandler.java (REMOVE) | INMEDIATO (conjunta — no compilan por separado) |
| T7.13 | application.yml (M) | INMEDIATO tras el edit (también GATE Fase 3) |
| T7.17 | — | GATE tests [L] completos / pre-smoke |
| T7.23 | — | mvn final completo |
| T7.19/T7.21 | scripts (M/N) | NO mvn — `bash -n` + re-corrida |

**Riesgo si apply salta la Fase 1 antes de escribir streaming**: el bean
`LiveBroadcaster` nuevo activa el seam C5 sin diff en `ingest/**`
(`oyentes.forEach(...)`) mientras `query/` está a medio mudar → query roto
bajo el hilo caliente de la ingesta + regresión E6.x sin punto de
comparación. La secuencia Fase 1 → gate → Fase 2 lo previene.

## Notas de divergencia (citadas, NO corregidas — ninguna bloqueante)

- **D-C7-1** (contra el congelado — resuelta por el spec): el LITERAL "los
  elementos de `logs` usan el LogDto completo" (11 campos, incl.
  `project_id`) prevalece sobre el ejemplo §3.5 que muestra 10 claves.
  T7.8 la IMPLEMENTA y E7.2 (T7.15) la pina (11 claves por elemento).
- **D-C7-2** (hereda R-6.i de C6): fechas del frame render truncadas a
  segundos con `LoginController.EXPIRES_AT_FORMATTER` (reutilizado por
  referencia); el matching from/to usa el instante RAW interno
  (`FilaBatch.received_at`). T7.8 la IMPLEMENTA; E7.1/E7.5 la cotejan en
  pila (psql del truncado).
- **D-7.15** (verificación de design, RATIFICADA por grep de esta tasks):
  `qa_c3_smoke.sh` NO invoca el modo eco — dif-0, NO se edita. T7.20.
- **R-7.j** (operativo, nuevo en design): `.env` sin `FRONTEND_ORIGIN` ⇒
  todo handshake rechazado (403) — requirement del spec (S26 extendido),
  no decisión propia. Guards en qa_c2 (T7.19) y qa_c7 (T7.21); en esta VM
  `.env` ya trae `FRONTEND_ORIGIN=http://localhost:8080`. NUNCA `"*"` y
  NUNCA default `"*"` en código.
- **R-7.k** (tolerancia): E7.14 en pila con defaults quizá no materializa
  el 1013 en guion → PASS CON NOTA "cubierto por pareja [L] E7.13" (patrón
  Q23 de C3). Codificado en T7.21.
- **S7-7 desambiguación** (T7.20/T7.21): "verde con el handler real" =
  101+Accept por nginx + ciclo set_filters→batch del smoke C7 — NO eco.
- **Nota de orden Fase 2** (T7.8/T7.9): el orden encargo
  interceptor→broadcaster→handler deja el tree no-compilable entre T7.8 y
  T7.9 (broadcaster inyecta handler); el mvn de la fase corre tras T7.9.
  Sin divergencia real de diseño — orden de escritura únicamente.

## Cobertura E7.x (mapa tarea → escenarios)

| Tarea | E7.x |
|---|---|
| T7.1 / T7.2 | base E7.20 / E7.18-parcial |
| T7.3 / T7.5 | E7.19 (pila: E7.5) |
| T7.4 | E7.20 |
| T7.6 | gate E7.18-parcial + E7.19 + E7.20 |
| T7.7 | E7.7, E7.8 |
| T7.8 | E7.2, E7.3, E7.4 (pila E7.1/E7.5; R-7.a/E-7.14a) |
| T7.9 | E7.4, E7.12, E7.13, E7.16 (pila E7.6/E7.10–E7.14) |
| T7.10 | gate de fase |
| T7.11 / T7.12 | frontera R7.3.1/R7.3.6 (swap + remove) |
| T7.13 | R7.29 (soporte E7.10/E7.12/E7.13) |
| T7.14 | consola (R7.30–R7.32 — enseñanza al cerrar) |
| T7.15 | E7.2, E7.3, E7.4, E7.7, E7.8, E7.16 |
| T7.16 | E7.12, E7.13 |
| T7.17 / T7.23 | E7.18 |
| T7.19 | E7.17 (misma forma), R-7.f |
| T7.20 | D-7.15 (dif-0 qa_c3) |
| T7.21 / T7.22 | E7.1, E7.5, E7.6, E7.9, E7.10, E7.11, E7.14, E7.15, E7.17 |
| T7.24 | regresión smokes (E3.24) |
| T7.25 | cierre de frontera (R7.3/R7.4/R7.36/R7.37) |

Total: **25 tareas**; 20 escenarios cubiertos (11 [L] + 9 [S]) + consola
(no-E7.x) y dif-0 qa_c3; ningún escenario queda sin tarea; 7 ficheros
existentes tocados (6 M + 1 REMOVE) + qa_c3 dif-0 verificado.