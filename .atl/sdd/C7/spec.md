---
change: C7
artifact: spec
project: Project_Log
last_updated: 2026-10-25
status: draft
---

# Spec — C7 · Streaming WebSocket en vivo

> Especificación normativa del corte C7 para la implementación
> (sdd-design → tasks → apply → qa → verify). Fuente: `proposal.md` C7
> (status **approved** — GATE APROBADO por el usuario 2026-10-25, 9/9 puntos
> tal cual, con nota de gate; P-2 cierra la deuda Testcontainers
> definitivamente) y anclas de `explore.md` C7. Los literales de API/WS se
> **citan** del Backend Schema (`docs/05_BACKEND_SCHEMA.md`, §3.5/§3.3/§3.2/§5),
> del TRD (`docs/02_TRD.md`, §2.8/§2.9/§5/§6.2.3/§9), del AppFlow
> (`docs/03_APPFLOW.md`, flujos 6/6b/7, §5), del PRD §6 y de la ficha C7 del
> `07_SLICING_PLAN.md` — nunca se redefinen. Código verificado en HEAD
> 89b5d60 (todo pushed; pila APAGADA limpia — el smoke se corre al aplicar/qa
> tras recomponer).

## 0. Decisiones de gate ya aprobadas (resumen vinculante 2026-10-25)

| Gate | Decisión aprobada |
|---|---|
| P-1 | Consola HTML temporal SÍ — página estática simple estilo maquetas C3/C4, NO contrato, se diseña AQUÍ y se enseña al cerrar el corte |
| P-2 | Testcontainers NO definitivo — deuda CERRADA; cliente WS JDK (`java.net.http`) sobre Tomcat RANDOM_PORT + smoke de pila viva |
| P-3 | Eliminar EchoSocketHandler; `--solo-handshake` permanente; tests de protocolo MIGRADOS al smoke nuevo |
| P-4 | Umbral de cierre por latido **75 s** por defecto, configurable |
| P-5 | Doble barrera 1013: bufferSizeLimit **512 KiB** + sendTimeLimit **2000 ms**, ambas env |
| P-6 | **Executor single-threaded** para la emisión |
| P-7 | Paquete `streaming` 3–4 ficheros con `LiveBroadcaster` |
| P-8 | `porIds` en LogsQueryRepository (C6) + parser de filtros EXTRAÍDO a `query/` — única fuente ADR-7 |
| P-9 | Mensajes no-contrato se IGNORAN + 401 pre-upgrade SIN cuerpo |

Recaídas del gate: regresión 146/146 + suites C7 completas en verify; consola
se reemplaza en C8; deudas en archive: nginx `client_max_body_size` (C9),
smoke `docker compose` (C9), 401 ISO-8859-1 (C8/C9).

## 0.2 Decisiones de SPEC — cierre de los 12 huecos (S7-1…S7-12)

VINCULANTES para design/tasks. Resuelven los 11 puntos del encargo + la
frontera de ediciones.

| # | Hueco | Cierre del spec |
|---|---|---|
| S7-1 | DISEÑO consola HTML (P-1) | **§1.J** — `frontend/public/ws-console.html`, NUEVO ÚNICO fichero frontend, NO contrato, TEMPORAL declarado en el propio fichero |
| S7-2 | Barreras: valores + mapeo env (P-4/P-5) | **§1.I** — bloque `projectlog.streaming` con 4 properties (heartbeat heredada + 3 opt-in nuevas, defaults 30/75/2000/524288); **0 env obligatorias; `.env.example` y `compose.yaml` SIN edición** |
| S7-3 | 401 pre-upgrade literal | **§1.B** — Status **401**, cuerpo VACÍO (sin JSON, sin `invalid_token` — esa familia es del entry point REST del panel, otra superficie); log WARN único sin token ni causa |
| S7-4 | `porIds` + parser extraído (P-8) | **§1.H** — firmas EXACTAS; orden PRESERVADO de la lista de entrada; faltantes IGNORADOS; parser `FiltrosConsulta.parsear` (checks 2–7 compartidos, 1º y 8º quedan en el controller) |
| S7-5 | Executor (P-6, R-7.a/i) | **§1.E** — monohilo daemon `streaming-broadcast`; el hilo Tomcat SOLO encola; shutdown graceful 5 s (§1.E); excepciones per-sesión swallow + log (un cliente malo no tumba a los demás); fallo de `porIds` NO re-emitido (at-least-once ya garantizado por COMMIT) |
| S7-6 | No-contrato (P-9, R-7.g) | **§1.C** — "no-contrato" = TEXTO que no sea `{"type":"set_filters","filters":{…}}` con shape EXACTO (6 claves de filtros §3.3, sin `cursor`/`limit`, sin claves extra), o frame BINARIO; NO cierra la sesión (solo los caminos contratados cierran: 1013 / latido); log debug sin contenido; `set_filters` INVÁLIDO conserva los vigentes |
| S7-7 | Echo verde (P-3, R-7.f) | `--solo-handshake` (SIEMPRE con `--token <JWT>` desde C7 — sin token el handshake responde 401) es LA prueba permanente del proxy en qa_c2/qa_c3/qa_c7; la eco/protocolo vive SOLO en el smoke C7; `EchoSocketHandler` y el modo eco completo ELIMINADOS — **desambiguación: "verde con el handler real" = 101+Accept por nginx + ciclo `set_filters`→`batch` del smoke C7, no eco** |
| S7-8 | Frontera de ediciones | **§1.A** — 5 ficheros existentes editados (EXACTOS), 1 REMOVIDO, resto INCOGNITO (lista completa cerrada) |
| S7-9 | Frame byte-exacto §3.5 + heartbeat control | **§1.D/§1.F** — claves top-level EN ORDEN; `batch_id` = `"b_"`+`UUID.randomUUID().toString()` (v4 minúsculas con guiones); LogDto 11 campos tal cual; heartbeat = ping RFC `0x9` (Spring `PingMessage`), NUNCA JSON; cierre por latido con `CloseStatus.GOING_AWAY` (1001) — sin ancla de código, decisión anclada aquí (solo 1013 está pineado como código de cierre) |
| S7-10 | Match de filtros en servidor | **§1.C/§1.D** — evaluación EN SERVIDOR contra el lote materializado: igualdades estrictas (project_id numérico, environment/level case-sensitive), from/to INCLUSIVE sobre el instante RAW (FilaBatch.received_at — no el render), texto = contains case-insensitive (Locale.ROOT) del texto CRUDO — equivalente EXACTO al ILIKE %text% (el escape es solo transporte SQL); `from == to` LEGAL; sesión sin `set_filters` NO recibe frames |
| S7-11 | Reconexión / fallback | **§1.F (R7.21)** — garantías del servidor: cada lote matcheado se entrega UNA vez por sesión viva (sin frames duplicados); ante caída NO hay replay (literal); el gap se resuelve con el cursor C6 (`next_cursor`) + dedup cliente; reenvío de `set_filters` tras reconectar es OBLIGACIÓN del cliente |
| S7-12 | Origen de sesión con Origin | **§1.B** — orden del reject: Origin PRIMERO (403), token LUEGO (401); `Origin` PRESENTE y ≠ `frontend-origin` → 403 sin cuerpo; `Origin` AUSENTE → continúa (clientes RFC no-browser legítimos: el JDK del test y el Python del smoke no envían Origin; el navegador SIEMPRE la envía — queda validado); propiedad `frontend-origin` vacía → rechazo de TODO handshake (S26: falta ⇒ cerrado; NUNCA `"*"`) |

## 0.3 Divergencias declaradas (D-C7-x — sin corregir docs)

- **D-C7-1 — `project_id` ausente del ejemplo de los elementos `logs` de
  §3.5.** El literal dice "los elementos de `logs` usan **el LogDto
  completo**" y el LogDto de §3.3 tiene **11 campos** (incl. `project_id`),
  pero el ejemplo JSON de §3.5 muestra SOLO 10 claves por elemento (omite
  `project_id`). **Resolución para el pin**: prevalece el LITERAL — el frame
  emite el LogDto completo de 11 campos (incl. `project_id`); el ejemplo es
  abreviado (además el `project_id` top-level ya lo da el contexto de la
  clave de ingesta). Riesgo BAJO (over-inclusión coherente con la dedup por
  `id` y con el ejemplo del TRD §2.8, que tampoco lo omite estructuralmente).
- **D-C7-2 — fechas del frame truncadas a segundos (hereda R-6.i de C6).**
  El ejemplo §3.5 muestra `2026-10-16T22:31:04Z` (segundos) mientras PG guarda
  micros (regla 9). **Resolución**: el `received_at` top-level y el de cada
  LogDto se RENDER truncado a segundos con el formatter C2 reutilizado por
  referencia (`LoginController.EXPIRES_AT_FORMATTER`) — coherente con el
  ejemplo y con la convención de casa; la precisión COMPLETA NO viaja en el
  frame (la dedup del cliente es por `id`, no necesita micros; el matching
  from/to del servidor usa el instante RAW interno, §1.D). Ninguna novedad
  respecto de C6 — se declara aquí porque el frame es una superficie nueva.
- **Sin divergencia**: umbral 75 s DENTRO del rango 60–90 s literal; 1013
  SOLO por lentitud; batch de 1 para ingestas individuales (literal §3.5);
  archivado derivado del 403 `project_archived` pre-COMMIT (flujo 7 paso 4).

## 1. Requisitos normativos (RFC 2119)

MUST/DEBE, MUST NOT/NO DEBE, SHOULD, MAY según RFC 2119. Cada requisito es
verificable por ≥1 escenario de §2.

### 1.A Estructura y frontera de ediciones (S7-8)

- **R7.1** — PIEZAS NUEVAS main, todas en `com.projectlog.streaming`
  (package-by-feature; el package se llama streaming desde C1):
  1. `streaming/LogSocketHandler.java` — `TextWebSocketHandler` REAL en
     `/ws/logs`: dueño del REGISTRO de sesiones (mapa thread-safe interno —
     ConcurrentHashMap; NO bean separado; la mini-clase `SesionesActivas` del
     paquete es optativa del design, P-7 permite 3–4 ficheros), del
     `set_filters` (§1.C), del latido (scheduler propio, §1.F) y de la
     limpieza total en `afterConnectionClosed`.
  2. `streaming/LiveBroadcaster.java` — `@Component implements
     ingest.IngestListener` (PRIMER bean del seam C5): genera `batch_id`,
     consulta `porIds`, evalúa filtros por sesión (§1.C/§1.D) y emite vía el
     executor (§1.E). El nombre es CONTRACTUAL (TRD §2.9 / Schema §3.5:
     "el `LiveBroadcaster` publica lotes ya persistidos").
  3. `streaming/JwtHandshakeInterceptor.java` — `HandshakeInterceptor`:
     token + Origin (§1.B). Vive en `streaming/` junto a su feature (P-7c);
     inyecta `TokenService` y `@Value("${projectlog.security.frontend-origin:}")`.
- **R7.2** — CIERO dependencias y migraciones: `pom.xml` INTOCABLE (zero
  `mvn` nuevo — spring-boot-starter-websocket ya viaja desde C1; NO
  Testcontainers, P-2 CERRADO); `V1__init.sql` y `postgres/**` INTOCABLES.
- **R7.3** — Ficheros EXISTENTES editados por C7 — lista CERRADA y EXACTA:
  1. `config/WebSocketConfig.java` — SOLO 2 ediciones (las anunciadas en su
     javadoc de C1): (a) el handler del registro pasa de `EchoSocketHandler`
     a `LogSocketHandler`; (b) en la línea ancla `// C1: sin .withSockJS() —
     ADR-5; sin .addInterceptors(...) — C7` se añade
     `.addInterceptors(jwtHandshakeInterceptor)`. Nada más: ruta `/ws/logs`
     y `@EnableWebSocket` byte-a-byte.
  2. `backend/src/main/resources/application.yml` — se AÑADE el bloque
     `projectlog.streaming` (§1.I) y se RETIRA del encabezado la reserva
     "sin WS_HEARTBEAT_SECONDS (heartbeat es C7)".
  3. `query/controller/LogsController.java` — SOLO extracción de checks
     §1.H-2 (delegar en `FiltrosConsulta.parsear`,
     comportamiento-preservado). Respuesta byte-idéntica (E7.18).
  4. `query/FiltrosConsulta.java` — se AÑADEN `parsear`/`Filtros`/
     `FiltroInvalido` (§1.H-2); `patronIlike` BYTE-A-BYTE intacto.
  5. `query/repository/LogsQueryRepository.java` — se AÑADEN
     `SELECT_POR_IDS` + `porIds` + `FilaBatch` (§1.H-1);
     `SELECT_LISTADO`/`SELECT_POR_ID`/`listar`/`porId` byte-a-byte.
  6. REMOVIDO — `streaming/EchoSocketHandler.java` (P-3: su sustitución
     estaba anunciada en su propio javadoc; sin registro en ruta queda
     código muerto y los congelados no conservan muertos).
  7. `scripts/qa_c2_smoke.sh` y `scripts/qa_c3_smoke.sh` — SUS invocaciones
     del modo eco se reducen a `--solo-handshake --token <JWT>` (S7-7).
- **R7.4** — INTOCABLES además: `ingest/**` (el bean nuevo activa el seam
  sin diff: lista inyectada), `SecurityConfig`, `CorsConfig`, `auth/**`
  (formatter y TokenService REUTILIZADOS por referencia — jamás editados),
  `projects/**`, `keys/**`, `query/{CursorCodec,FiltrosConsulta→ patronIlike,
  package-info}`, `query/dto/**` (LogDto/LogListItemDto reutilizados tal
  cual), `streaming/package-info.java` (su texto YA declara el plano C7),
  `frontend/nginx.conf`, `compose.yaml`, `.env.example`,
  `application-test.yml`, `frontend/src/**` (ni una línea del SPA),
  `scripts/ws_echo_test.py` (byte-a-byte — su `--token` ya funciona),
  `docs/**` y TODOS los tests de C1–C6. Ficheros NUEVOS de test en
  `backend/src/test/java/com/projectlog/`:
  `streaming/StreamingWsClientTest.java` (E7.2–E7.4, E7.7, E7.8, E7.16),
  `streaming/LatidoYBackpressureTest.java` (E7.12, E7.13),
  `query/repository/PorIdsSqlPinTest.java` (E7.19, patrón `LogsRepoSqlTest`
  — mismo paquete que el repo), `query/FiltrosConsultaParseTest.java`
  (E7.20) + `scripts/qa_c7_smoke.sh` NUEVO (patrón canon C5/C6).

### 1.B Handshake — JWT y Origin (401 pre-upgrade sin cuerpo — S7-3/S7-12)

- **R7.5** — `JwtHandshakeInterceptor.beforeHandshake` DEBE ejecutar EN
  ORDEN: (1) **Origin**: propiedad `frontend-origin` vacía → RECHAZO
  (cierre S26 extendido al handshake — NUNCA `"*"`); header `Origin`
  PRESENTE y ≠ propiedad (comparación EXACTA, sin normalizar) → RECHAZO;
  header AUSENTE → continúa (S7-12); (2) **token**: query param EXACTO
  `token` ausente o vacío → RECHAZO 401; `TokenService.verify(token)` (por
  referencia, C2) `isEmpty()` (expirado/inválido) → RECHAZO 401.
- **R7.6** — RECHAZO pre-upgrade (literal §3.5: "conexión rechazada con 401
  antes de asignar recursos"): Status **401** para token y **403** para
  Origin (decisión anclada — el congelado solo exige el rechazo de Origin,
  ficha C7, sin pinar status), cuerpo VACÍO, SIN upgrade, SIN tocar recursos
  del handler y SIN crear sesión. El 401 NO DEBE pasar por el
  `RestAuthenticationEntryPoint` ni llevar `{"code":"invalid_token",…}` —
  el literal `invalid_token` es de la familia REST del PANEL (otra
  superficie, CD-4 intocable); el handshake es pre-recursos y su consumidor
  (panel C8) trata 401 como re-login (AppFlow flujo 6a paso 2).
- **R7.7** — Higiene (S16/R-7.d): JAMÁS loguear el token ni fragmentos ni la
  cadena de query; el rechazo se loguea a WARN como ÚNICA línea sin causa
  distinguible ("handshake WS rechazado" — anti-oráculo, patrón CD-4);
  NINGÚN JWT DEBE quedar en los attributes de sesión.
- **R7.8** — Seguridad NINGÚN diff: la cadena 3 `permitAll()` de
  `/ws/logs` (G2 de C2) queda tal cual — el auth vive SOLO en el
  interceptor; una API key JAMÁS autentica el socket (PRD §6 / Schema §5).

### 1.C Sesión — registro, `set_filters` y no-contrato (S7-6/S7-10)

- **R7.9** — `afterConnectionEstablished`: la sesión DEBE registrarse
  envuelta en `ConcurrentWebSocketSessionDecorator(session, sendTimeLimit,
  bufferSizeLimit)` (§1.G) con su estado (filtros parseados + último pong)
  en los attributes de sesión; sin `set_filters` aún la sesión NO recibe
  frames (S7-10: sin calificar ⇒ sin emisión).
- **R7.10** — `handleTextMessage` — shape ESTRECHO de `set_filters`: SOLO es
  contrato el objeto `{"type":"set_filters","filters":{…}}` con type EXACTO
  y `filters` con EXACTAMENTE las claves `project_id, environment, level,
  from, to, text` (§3.3), sin claves extra (JAMÁS `cursor`/`limit` — no hay
  paginación en el stream, literal §3.5). Valores por clave: `null` o clave
  ausente = filtro sin aplicar (el ejemplo §3.5 lleva `"to": null`);
  PRESENTE debe ser String JSON (salvo `project_id`, que admite también
  número JSON — ejemplo §3.5 `"project_id": 7` — normalizado a String antes
  del parser). Los strings viajan al parser con la MISMA semántica que los
  query params de `GET /logs` (presente-y-vacío "" es INVÁLIDO en
  environment/level — patrón C6 S6-2).
- **R7.11** — `set_filters` VÁLIDO: se PARSEA vía
  `FiltrosConsulta.parsear` (§1.H-2, única fuente ADR-7) y se almacena el
  `Filtros` resultante en los attributes de la sesión (recalificación
  inmediato para los siguientes lotes — el frame NO responde nada: el
  servidor JAMÁS envía un mensaje JSON distinto de `batch`, literal §3.5).
  `set_filters` INVÁLIDO (`FiltroInvalido` por checks 2–7, o fallo de
  shape): NO aplica el cambio — la sesión CONSERVA sus filtros vigentes
  (equivalencia del "400 → panel conserva la última lista válida" del
  AppFlow flujo 4), NO cierra (R-7.g cerrado con tolerancia).
- **R7.12** — MENSAJE NO-CONTRATO (S7-6): CUALQUIER texto que no sea el
  shape EXACTO de R7.10 (incl. `{"type":"ping"}` u otro type, JSON
  malformado, texto plano, keys extra) y CUALQUIER frame BINARIO: se IGNORA
  sin cerrar la sesión, con log a DEBUG SIN el contenido del mensaje
  (higiene; un cliente malportado NUNCA cae el socket). La sesión cierra
  SOLO por los caminos ya contratados: 1013 (§1.G), latido vencido (§1.F) o
  close del cliente.
- **R7.13** — Pings/pongs RFC: los 0x9/0xA son frames de CONTROL del
  contenedor — el server ping es del scheduler (§1.F); el pong del cliente
  llega a `handlePongMessage` y alimenta el latido (S7-9). El close del
  cliente (`afterConnectionClosed`, cualquier código) DEBE limpiar TODO el
  registro (R-7.c: sin leak del mapa).

### 1.D Frame `batch` — forma byte-exacta del Schema §3.5 (CONTRATO)

- **R7.14** — UN frame JSON de texto POR LOTE, top-level con EXACTAMENTE 5
  claves EN ESTE ORDEN (literal §3.5 verbatim, D-3: ObjectMapper del BEAN
  único de Boot, NUNCA instancia suelta):

```json
{"type":"batch","batch_id":"b_3f2b9c7a-…","project_id":7,
 "received_at":"2026-10-16T22:31:04Z","logs":[ … LogDto × N, orden del lote … ]}
```

  - `type` = `"batch"` exacto. `batch_id` = `"b_" + UUID.randomUUID().
    toString()` (UUID **v4**, minúsculas, 36 chars con guiones — el ejemplo
    `b_3f2b9c7a-…`); opaco, único por frame (NUNCA se reutiliza).
  - `project_id` = Long del lote (el seam lo entrega).
  - `received_at` = el COMÚN del lote (regla 9), RENDER truncado a segundos
    con el formatter C2 reutilizado (D-C7-2).
  - `logs` = `List<LogDto>` COMPLETO — 11 campos, claves literales snake_case
    (`id, project_id, level, environment, timestamp, received_at, message,
    exception, metadata, source, correlation_id`), nulls INCLUIDOS, EN EL
    ORDEN DEL LOTE (D-C7-1: el ejemplo omite `project_id` — prevalece el
    literal "LogDto completo", 11 campos). Un ingest individual es batch de
    1 (literal §3.5).
- **R7.15** — Serialización determinista: el frame se construye como record
  o `LinkedHashMap` (claves en el orden de R7.14 — design elige), se
  emite como UN `TextMessage`; JAMÁS fragmentado, JAMÁS varios frames por
  lote, JAMÁS un mensaje JSON del servidor distinto de `batch`.
- **R7.16** — Matching por sesión EN SERVIDOR (S7-10), contra el lote
  materializado: `project_id` igualdad Long; `environment`/`level` igualdad
  estricta case-sensitive; `from`: `!receivedAt.isBefore(from)`, `to`:
  `!receivedAt.isAfter(to)` (INCLUSIVE — igual que >=/<= del SQL de C6) con
  el instante RAW; `text`: `message.toLowerCase(Locale.ROOT).contains(
  texto.toLowerCase(Locale.ROOT))` (el escape ILIKE es SOLO transporte SQL —
  semántica equivalente EXACTA); TODOS los filtros activos deben pasar
  (conjunción; sin filtro ⇒ no restringe).

### 1.E LiveBroadcaster y executor (P-6, P-7 — S7-5/R-7.a/R-7.i)

- **R7.17** — `onLotePersistido(projectId, receivedAt, ids)` en el HILO
  Tomcat de la 202: SOLO DEBE encolar la tarea en su executor y retornar
  (cero BD, cero envíos en caliente — la 202 NUNCA queda a merced del peor
  cliente). Tarea del executor (monohilo, FIFO — orden de lotes preservado
  por construcción): (1) `ids` null/vacío → nada; (2) `porIds(ids)` (§1.H-1);
  lista vacía → nada; (3) `batch_id`; (4) por cada sesión viva: match
  (§1.D) → `sendMessage` por el decorador; (5) excepciones: por SESIÓN →
  swallow + log WARN (sin contenido) y si la sesión cerró, fuera del
  registro; del lote (p. ej. `porIds`) → swallow + log ERROR — NO re-emite,
  NO reintenta (at-least-once YA garantizado por el COMMIT del seam; sin
  replay contractual), la 202 siguiente jamás se afecta (D-12 + R-7.i).
- **R7.18** — Executor CONTRACTO (P-6): `newSingleThreadExecutor` con
  ThreadFactory daemon nombrado `streaming-broadcast`; cola sin límite
  declarado (v1 monousuario, tasa baja — R-7.a queda cerrado: N sesiones
  lentas jamás multiplican la latencia de la 202). Ciclo de vida ANCLADO al
  bean (`@PreDestroy`): `shutdown()` + `awaitTermination(5 s)` (grace: los
  lotes ya encolados se intentan emitir) → `shutdownNow()` (los restantes se
  descartan — sin garantía post-shutdown, coherente con "sin replay");
  JAMÁS propaga exceptions al llamo de `onLotePersistido`.

### 1.F Heartbeat del servidor y cierre por latido (P-4 — S7-9)

- **R7.19** — `LogSocketHandler` DEBE mantener un scheduler PROPIO
  (`ScheduledExecutorService` single-thread daemon) que cada
  `heartbeat-seconds` envía a cada sesión viva un PING como frame de CONTROL
  RFC (`org.springframework.web.socket.PingMessage`, opcode 0x9) — NUNCA
  JSON (literal §3.5: "ningún mensaje del servidor distinto de batch está
  previsto en v1"). `handlePongMessage` actualiza el último pong.
- **R7.20** — Sesión sin pong DENTRO de `idle-timeout-seconds` (default 75,
  punto medio del rango 60–90 s literal): cierre + limpieza de sesión
  (código `CloseStatus.GOING_AWAY`, 1001 — decisión anclada; el ÚNICO código
  pinado por de lentitud es 1013). El umbral se mide desde el ÚLTIMO pong
  (o desde la conexión si nunca respondió); tolera un ping perdido completo
  (75 > 30). Reconexión + backoff 1→30 s y reenvío de `set_filters` son del
  CLIENTE (C8) — C7 solo cierra y queda limpio.
- **R7.21** — Reconexión / fallback (S7-11, DONE literal "heartbeat/
  reconexión de servidor probados" — la reconexión PROBADA es del SERVIDOR
  limpio): tras un cierre (latido/backpressure/cliente), una nueva conexión
  funciona de CERO (handshake + `set_filters` + frames nuevos). Garantías
  del servidor hacia el cliente: (a) cada lote matcheado se entrega UNA vez
  por sesión viva (sin frames duplicados — el executor es FIFO monohilo);
  (b) lo entrado durante una caída NO se re-emite (sin replay, literal);
  (c) el gap lo cubre el listado keyset de C6 con `next_cursor` + dedup por
  `id` del cliente (AppFlow flujo 6b/6c — ya certificado en E6.x); (d) NO
  hay estado de cliente en el servidor (sin buffers, sin replay queue).

### 1.G Backpressure — doble barrera 1013 (P-5 — S7-2/R-7.b)

- **R7.22** — Cada sesión registrada queda envuelta en
  `ConcurrentWebSocketSessionDecorator(session, sendTimeLimit, bufferSizeLimit)`
  con los dos límites de config (§1.I): `send-time-limit-ms` (default 2000)
  y `buffer-size-bytes` (default 524288 = 512 KiB). VENCIDO CUALQUIERA de
  los dos (`SessionLimitExceededException`): cierre con
  `CloseStatus.TRY_AGAIN_LATER` (`1013`, ÚNICO código contractual de cierre
  por lentitud) + log WARN sin contenido + limpieza del registro (vía
  `afterConnectionClosed`). 1013 aplica a AMBOS vencimientos (S7-9).
- **R7.23** — El resto del mundo NUNCA se bloquea: tras el 1013 de una
  sesión lenta, las OTRAS sesiones siguen recibiendo y las ingestas siguen
  respondiendo 202 (el síncrono fue descartado — R-7.a cerrado por R7.17).

### 1.H Soporte en `query/` (P-8 — S7-4, única fuente ADR-7)

#### 1.H-1 `porIds` — lectura por ids para el frame

- **R7.24** — En `query/repository/LogsQueryRepository.java` (fichero
  EXISTENTE, frontera C5 respetada — JAMÁS método en `ingest.LogsRepository`):

```java
/** (C7) Frame batch §3.5: materializa el lote por ids. Los ids llegan EN
 *  ORDEN DEL LOTE (RETURNING del INSERT de C5 — la MONEDA del frame); el
 *  SELECT NO ordena: porIds REORDENA en Java según la lista de ENTRADA.
 *  Faltantes: IGNORADOS (sin error — DELETE no existe en Fase 1 y la fila
 *  ya está en COMMIT; el frame emite solo las encontradas, en orden del
 *  lote). CERO round-trips por elemento: UNA SELECT por lote (P-8). */
@Transactional // defensa documentada mono-sentencia — patrón C6
public List<FilaBatch> porIds(List<Long> ids);

/** Moneda del frame: LogDto completo (render) + instante RAW para el
 *  matching temporal del servidor (§1.D). Record PÚBLICO anidado. */
public record FilaBatch(LogDto log, OffsetDateTime received_at) {}
```

- **R7.25** — Estamento EXACTO (constant `static final String` package-
  private para el pin E7.19 — patrón `LogsRepoSqlTest`; 1 placeholder,
  bind del array con `Connection.createArrayOf("bigint", …)` — driver PG17):

```java
/** (C7) §1.H-1: las MISMA 11 columnas de SELECT_POR_ID en ESE orden.
 *  SIN ORDER BY: el orden del frame lo impone la lista de entrada (Java). */
static final String SELECT_POR_IDS =
        "SELECT id, project_id, level, environment, timestamp, received_at,"
                + " message, exception, metadata, source, correlation_id"
                + " FROM logs WHERE id = ANY (?)";
```

- **R7.26** — El RowMapper DEL DETALLE (`FILA_DETALLE` de C6) se REUTILIZA
  tal cual para `SELECT_POR_IDS` (mismo mapping por-columna: metadata jsonb →
  `readTree`, formatter C2, instante RAW en `FilaBatch.received_at`) — SIN
  duplicar la lambda y SIN extraer a fichero nuevo (menor diff; P-8 dejaba
  la extracción a decisión de spec — CERRADO: reuso in-situ).

#### 1.H-2 Parser extraído de filtros (única fuente ADR-7)

- **R7.27** — En `query/FiltrosConsulta.java` (fichero EXISTENTE puro — su
  javadoc de C6 YA declara "seam C7: la misma semántica de filtros del
  stream §3.5 la reutiliza") se AÑADEN (firma EXACTA):

```java
/** C7 (P-8): ÚNICA fuente de la semántica de filtros ADR-7 — la usan
 *  GET /logs (checks 2–7) y set_filters del stream (SIN limit/cursor:
 *  no hay paginación en el stream). MISMOS checks, MISMO orden (checks
 *  2→7: project_id → environment → level → from → to → from>to),
 *  primer-fallo-gana, MISMAS parejas code/message/field. PatronIlike
 *  (byte-a-byte) genera patronTexto. Devuelve TAMBIÉN el texto CRUDO:
 *  el matching del stream lo necesita sin escape (§1.D). */
public static Filtros parsear(String projectId, String environment,
        String level, String from, String to, String text);
        // throws FiltroInvalido (runtime)

/** Filtros tipados del corte (record PÚBLICO). */
public record Filtros(Long projectId, String environment, String level,
        OffsetDateTime from, OffsetDateTime to, String texto,
        String patronTexto) {}

/** Única excepción: code/message/field EXACTOS de la tabla R6.22 de C6. */
public static final class FiltroInvalido extends RuntimeException {
    public final String code; public final String message;
    public final String field;   // null en filter_from_to_inverted
}
```

  Las constants `CODE_FILTER_INVALID_VALUE`, `CODE_FILTER_FROM_TO_INVERTED`
  y sus `MSG_*` (checks 2–7) MUDAN a `FiltrosConsulta` (única fuente);
  `CODE_FILTER_LIMIT_INVALID`/`CODE_FILTER_CURSOR_INVALID` y sus `MSG_*` +
  `CODE_NOT_FOUND`/`MSG_NOT_FOUND` quedan en `LogsController` (no aplican al
  stream). LITERALES byte-idénticos (pin E7.20 + re-corrida E7.18).

- **R7.28** — `LogsController` — refactor comportamiento-PRESERVADO (cambio
  MÍNIMO): conserva check 1 (limit) y check 8 (cursor) EN SUS POSICIONES;
  sustituye el bloque de checks 2–7 por `parsear(...)` + catch de
  `FiltroInvalido` → los MISMOS cuerpos de error (tabla R6.22 de C6 verbatim).
  Orden resultante byte-compat: limit → parser (2..7) → cursor. Los E6.x del
  controller DEBEN quedar verdes SIN edición de sus asertos (E7.18).

### 1.I Config — bloque `projectlog.streaming` (S7-2)

- **R7.29** — En `application.yml` (ÚNICA edición de config) se AÑADE:

```yaml
projectlog:
  streaming:                                 # C7 (R7.29): defaults del spec
    heartbeat-seconds: ${WS_HEARTBEAT_SECONDS:30}     # ya env (C1) — mapeo
    idle-timeout-seconds: ${WS_IDLE_TIMEOUT_SECONDS:75}    # P-4 (rango 60–90)
    send-time-limit-ms: ${WS_SEND_TIME_LIMIT_MS:2000}      # P-5 barrera 1
    buffer-size-bytes: ${WS_BUFFER_SIZE_BYTES:524288} # P-5 barrera 2 (512 KiB)
```

  0 variables obligatorias nuevas (las 3 opt-in SOLO se necesitan a test;
  documentadas como comentario aquí — `.env.example`/`compose.yaml` SIN
  edición). La suite [L] sobreescribe SOLO via
  `@SpringBootTest(properties={…})` / `@TestPropertySource` — JAMÁS tocando
  `application-test.yml`. Los umbrales de PROD en pila (30/75/2000/524288)
  son EXACTOS estos.

### 1.J Consola HTML temporal (P-1 — S7-1; NO contrato)

- **R7.30** — NUEVO fichero EXACTO `frontend/public/ws-console.html` (el
  ÚNICO fichero de frontend de C7; precede: `frontend/public/mockups` C3 —
  Vite copia `public/` verbatim a `dist/` y nginx lo sirve; el SPA NO se
  toca ni se importa la consola). Página estática ÚNICO fichero, HTML+CSS+JS
  vanilla SIN framework NI build (estilo MAQUETAS C3/C4 llanas, fondo claro,
  sin dependencias externas). La URL servida es `http://<host>:8080/ws-console.html`.
- **R7.31** — CONTENIDO mínimo: (1) formulario de login → `POST
  /api/v1/panel/auth/login` (literal §3.2) con username/password → guarda el
  token EN MEMORIA; (2) botón Conectar →
  `new WebSocket((location.protocol==='https:'?'wss':'ws') +
  '://'+location.host+'/ws/logs?token='+encodeURIComponent(token))`;
  (3) campos de filtros §3.3 (`project_id`, `environment` select,
  `level` select, `from`, `to`, `text`) + botón "Aplicar set_filters"
  (construye el JSON EXACTO de §3.5, con nulls); (4) lista de frames `batch`
  recibidos (JSON pretty, últimos 50, contador total) con badge de estado
  (Conectado/Desconectado) y eventos open/close (con `event.code`) /
  error; (5) reconexión MANUAL por botón (la consola NO implementa backoff
  — es utilidad, no panel).
- **R7.32** — TEMPORAL por contrato: banner VISIBLE "CONSOLA TEMPORAL C7 —
  no es parte del panel; se reemplaza en C8" + comentario HTML equivalente
  en la cabecera del fichero. Se ENSEÑA al usuario al CERRAR el corte
  (verify/archive cita la URL y captura del ciclo set_filters→batch). NO es
  parte del panel, NO entra al smoke como dependencia (el smoke usa cliente
  Python), NO añade nada al build del SPA.

### 1.K Suite [L] y smoke [S]

- **R7.33** — Suite [L]: `@SpringBootTest(webEnvironment = RANDOM_PORT)` +
  cliente WebSocket REAL del JDK
  (`java.net.http.HttpClient.newHttpClient().newWebSocketBuilder()`) contra
  Tomcat REAL (upgrade REAL, handler REAL, interceptor REAL) con
  `LogsQueryRepository` MOCKEADO (`@MockitoBean`) y `ObjectMapper` REAL del
  contexto; el lote se dispara INVOCANDO el bean real `LiveBroadcaster.
  onLotePersistido(...)` (broadcast a mano — sin BD); JWT de test emitido
  por el `TokenService` del contexto (patrón `PanelAuthMvcTest`);
  umbrales/límites de test por properties (R7.29). El caso "sin pong" y la
  sesión lenta se materializan con mocks de `WebSocketSession` registrados
  en el mapa REAL (el cliente JDK responde pong AUTOMÁTICAMENTE — no puede
  simular una sesión muda). Pines unitarios sin BD: E7.19/E7.20.
- **R7.34** — Smoke [S] `scripts/qa_c7_smoke.sh` NUEVO (patrón canon
  C5/C6): recomposición ÚNICA `podman compose up -d --build --force-recreate`
  (NUNCA `down -v` — pgdata; la recomposición TAMBIÉN publica la consola),
  pila 3×healthy, smoke-of-the-smoke (GET /me sin token → 401), login (JWT a
  tmpdir) + proyecto + key (tmpdir 700, needles sufijados `smoke-c7-<TS>-*`,
  conteos relativos, exit 0/1/2, salida `E7.n: PASS | FAIL |
  BLOQUEADO (detalle)`). Cliente WS Python INLINE en el propio script
  (heredoc — RFC 6455 stdlib, frames mascarados, responde pong 0xA a ping
  0x9, lee el status HTTP del handshake) estilo `ws_echo_test.py`; E7.17
  invoca `scripts/ws_echo_test.py --solo-handshake --token <JWT>` tal cual
  (artefacto C1 SIN edición). Los tests de protocolo C1 (eco) quedan
  SUSTITUIDOS aquí (S7-7).
- **R7.35** — Higiene heredada: NINGUNA clase nueva loguea credenciales
  (JWT/token jamás; sin loggers con contenido de mensajes/filtros — R7.7/
  R7.12); los smokes jamás imprimen la key o el JWT (tmpdir).

### 1.L No-regresiones C1–C6 y deudas

- **R7.36** — La suite completa `mvn test` (146 vigentes en C6) re-corrida
  en builder DEBE seguir 146/146 VERDE más los N de C7 (= 146 + N);
  los E6.x del controller byte-compatibles (parseo extraído — R7.28); los
  pins de C5/C6 re-corridos intocados; los smokes C2/C3 con su nueva forma
  `--solo-handshake --token` verdes (R-7.f); el `qa_c5_smoke.sh`/`qa_c6_smoke.sh`
  SIN cambios (no invocan el WS).
- **R7.37** — Deudas: Testcontainers CERRADA definitivamente (P-2 — el
  archive de C7 la cita como CERRADA con la sustitución documentada: WS JDK
  + RANDOM_PORT + smoke de pila viva); el hueco "seam sin beans" de C6
  CERRADO (primer bean `LiveBroadcaster`); NO se tocan las vivas (smoke
  `docker compose` → C9; 401 ISO-8859-1 → C8/C9 — NUNCA afecta al 401 SIN
  cuerpo del handshake; nginx `client_max_body_size` → C9).

## 2. Escenarios de aceptación (E7.1–E7.20)

IDs ESTABLES 1:1 con la tabla de la proposal §6 (E7.19/E7.20 son el
refinamiento de los pins que la proposal dejó sin ID). Cuenta: **20 —
11 [L] + 9 [S]** (≈10 [L] + 9 [S] + pins, como la proposal esperó).

### 2.1 Cliente WS real [L] — frame, orden, filtros

- **E7.2** `frameTrasCommitFormaExacta§3.5` [L] — RANDOM_PORT + cliente JDK
  + JWT válido + `set_filters` calificando → invocación directa del bean
  `LiveBroadcaster.onLotePersistido(7, t, [12301, 12302])` (repo mock:
  2 `FilaBatch`) → el cliente recibe EXACTAMENTE UN `TextMessage` con el
  shape EXACTO de R7.14: 5 claves top-level EN ORDEN, `type=="batch"`,
  `batch_id` matchea `^b_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]
  {3}-[0-9a-f]{12}$` (UUID v4), `project_id==7`, `received_at` == render del
  común, y cada elemento con EXACTAMENTE las 11 claves del LogDto (nulls
  incluidos — D-C7-1). Pareja [S]: E7.1.
- **E7.3** `batchDeUnoYOrdenDelLote` [L] — lote de UNO [501] → frame de 1
  log (individual = batch de 1, literal §3.5); lote [5,2,9,1,7] con el mock
  devolviendo filas en CUALQUIER orden de SELECT → el frame PRESERVA el
  orden de ENTRADA [5,2,9,1,7] (porIds reordena en Java — R7.24); batch_
  id distintos entre frames; `ids` vacío → NINGÚN frame (defensiva R7.17).
  Pareja [S]: E7.5.
- **E7.4** `setFiltersPorSesion` [L] — TRES clientes simultáneos: c1
  `project_id=7`, c2 `project_id=8`, c3 conecta SIN `set_filters` → lote del
  proyecto 7: frame SÓLO a c1 (c2/c3 nada — c3 sin calificar no recibe,
  S7-10); lote del 8: sólo c2; c2 RECALIFICA a `project_id=7` en caliente →
  siguiente lote del 7 llega a c1 Y c2. Pareja [S]: E7.6.
- **E7.7** `handshake401PreUpgradeTresCausas` [L] — `connectBlocking` con
  (a) sin `?token`, (b) token EXPIRADO (TTL encogido de test), (c) token
  inválido (basura/otra clave) → WebSocketHandshakeException con status
  **401 en LAS TRES** (sin oráculo de causa — R-7.d de la familia: un único
  status), NUNCA 101, sin recursos del handler creados, sin cuerpo JSON.
  Pareja [S]: E7.9. DONE 3 [L].
- **E7.8** `originValidada` [L] — (a) `Origin: http://evil.example` ≠
  frontend-origin de test → rechazo pre-upgrade con status **403**; (b)
  `Origin` == frontend-origin → 101 + ciclo set_filters→batch normal; (c)
  SIN header `Origin` → 101 (cliente no-browser — S7-12); y jamás `"*"`
  (una Origin arbitraria nunca pasa). Pareja [S]: E7.9. DONE 3 [L].

### 2.2 Ciclo de vida [L] — latido, 1013, tolerancia

- **E7.12** `cierrePorLatidoYLimpieza` [L] — properties encogidas
  (heartbeat 1 s, idle 2 s): (a) sesión Mock SIN pong registrada en el mapa
  REAL → el scheduler la cierra (GOING_AWAY 1001) y la LIMPIA del registro;
  (b) cliente JDK real (que SIEMPRE responde pong) PERMANECE tras varios
  ciclos (aserto de NO-cierre); (c) tras cerrar limpiamente una conexión
  real, una NUEVA conexión completa funciona de cero (handshake +
  `set_filters` + frame). Parejas [S]: E7.10/E7.11. DONE 4 [L].
- **E7.13** `cierre1013AmbosLimites` [L] — properties encogidas
  (send-time-limit 1 ms, buffer 1 byte): sesión lenta = mock sin drenaje
  envuelta en el DECORADOR REAL: (a) vence sendTimeLimit →
  `SessionLimitExceededException` → close **1013** + limpieza; (b) desborda
  bufferSizeLimit → idem 1013; (c) tras el 1013 de la lenta, un broadcast
  posterior llega INTACTO a las otras sesiones y `onLotePersistido` retorna
  limpio (la 202 jamás se afecta — R7.23). Pareja [S]: E7.14. DONE 5 [L].
- **E7.16** `mensajesNoContratoIgnoradosYsinReplay` [L] — cliente conectado
  envía: (a) texto plano → sesión SIGUE y nada llega; (b)
  `{"type":"ping"}` (JSON no-contrato) → idem; (c) frame BINARIO → idem
  (R7.12); (d) tras un `set_filters` VÁLIDO uno INVÁLIDO (level "error",
  project_id "abc", clave extra `limit`, JSON malformado, valores "") →
  sesión sigue y CONSERVA los vigentes (el siguiente lote llega según el
  VIGENTE); (e) sin replay: un lote emitido mientras el cliente está
  desconectado NO aparece al reconectar — la nueva conexión no recibe nada
  hasta un lote NUEVO (R7.21). DONE 7 [L].
- **E7.18** `regresionSuite146Verde` [L-regresión] — `mvn test` completo:
  146 heredados VERDES + N de C7 (R7.36); los E6.x del controller
  byte-compatibles tras extraer el parser (ningún aserto editado). DONE 9.

### 2.3 Pins [L] — SQL porIds y parser ADR-7

- **E7.19** `pinSqlPorIdsYOrden` [L] — patrón `LogsRepoSqlTest` (mismo
  paquete que el repo): pin de `SELECT_POR_IDS` literal EXACTO (R7.25: 11
  columnas EN ESE orden, `WHERE id = ANY (?)`, 1 placeholder, SIN ORDER BY)
  + pin de la firma `porIds(List<Long>)`/`FilaBatch` + mockito-JdbcTemplate:
  con filas del mock devueltas DESORDENADAS e ids de entrada [3,1,2] →
  salida [3,1,2] (reorden en Java); faltantes IGNORADOS sin error; el MISMO
  `FILA_DETALLE` sirve a ambos estamentos. Pareja [S]: E7.5 (ids reales en
  pila). DONE 1 [L].
- **E7.20** `pinParserExtraidoSemanticaADR7` [L] — unit puro de
  `FiltrosConsulta.parsear`: los 6 checks compartidos EN ORDEN (project_id →
  environment → level → from → to → from>to), primer-fallo-gana, con
  `FiltroInvalido.code/message/field` EXACTOS de la tabla R6.22 (los
  LITERALES byte-idénticos a los E6.x); caso feliz tipado completo (con
  patronTexto `%timeout%` Y texto crudo separado); aguja `_`: patronTexto
  lleva el escape (`_` → `\_`) y el crudo NO (el stream matchea el crudo —
  S7-10);
  `text=""` → patron `%%` + crudo ""; `to: null` (ausente) → null; y
  `patronIlike` byte-a-byte (re-corrida). Pareja [S]: E7.6.

### 2.4 Smoke [S] — `scripts/qa_c7_smoke.sh` (guion cronológico, canon C5/C6)

> Pre-fase (sin ID, parte de E7.1): recomposición ÚNICA `--build
> --force-recreate` + 3×healthy + smoke-of-the-smoke (GET /me sin token →
> 401 cadena 2) + login (JWT a tmpdir) + POST /projects (id capturado) +
> regenerate (key a tmpdir). Consola publicada en la recomposición (R7.30).

- **E7.1** `ingestFramesPorNginx` [S] — heredoc Python RFC: handshake
  `ws://<host>:8080/ws/logs?token=<JWT real>` POR nginx → **101** +
  Sec-WebSocket-Accept correcto → envía `set_filters` EXACTO (project_id del
  proyecto smoke) → `POST /api/v1/ingest` REAL (key real, needle
  `smoke-c7-<TS>-a`) → recibe EXACTAMENTE 1 frame `batch`: shape §3.5 + 11
  campos + `received_at` == truncado-a-segundos del psql de la fila
  (D-C7-2) + `logs.0.message == needle`. Pareja [L]: E7.2. DONE 1 [S].
- **E7.5** `batchGrandeUnSoloFrameOrdenado` [S] — `POST /api/v1/ingest/batch`
  con 3 logs (needles a/c) → EXACTAMENTE 1 frame con 3 logs EN ORDEN del
  lote (ids CRECIENTES == orden del RETURNING verificado por psql) y
  `batch_id` único (≠ ninguno previo). Parejas [L]: E7.3/E7.19. DONE 1.
- **E7.6** `setFiltersRecalificadoEnPila` [S] — cliente vivo: `level=ERROR`
  → ingesta needle WARN → NO llega frame (psql confirma la fila);
  `level=WARN` → siguiente lote WARN SÍ llega; recalifica a otro project_id
  → nada. Parejas [L]: E7.4/E7.20. DONE 2.
- **E7.9** `handshake401YOriginPorProxy` [S] — por nginx: sin token → la
  línea de status del proxy ES **401** (el proxy no la reescribe);
  `Origin: http://evil.example` con token válido → **403**; sin Origin con
  token → 101 (la pareja exacta de E7.7/E7.8). DONE 3.
- **E7.10** `pingControlCada30s` [S] — sesión abierta ≥2 heartbeats con el
  DEFAULT 30 s (~65 s de guion — precedente C1: 75 s): el cliente observa
  frames opcode **0x9**, responde **0xA**, mide intervalos ≈30 s (±5 s) y
  CONFIRMA que NINGÚN mensaje JSON distinto de `batch` llegó (literal §3.5).
  Pareja [L]: E7.12a. DONE 4.
- **E7.11** `reconexionLimpiaSinReplay` [S] — cierra limpio (0x8), psql
  cuenta filas insertadas durante la caída, NUEVO handshake completo +
  `set_filters` de nuevo → próximo lote SÍ llega; NINGÚN frame re-emitido de
  lo entrado durante la caída (aserto de ausencia — sin replay); el gap lo
  cubre el listado keyset C6 (citado, no re-probado — E6.16/E6.23 lo
  certifican). Pareja [L]: E7.12c/E7.16e. DONE 4.
- **E7.14** `backpressure1013EnPila` [S] — cliente NO consumidor (set_
  filters matcheando) + sesiones consumidoras sanas + ingestas repetidas con
  mensajes de 32 KiB (máximo por campo — límite §2.7): (a) las 202 NUNCA
  bloquean más de ~2 s (R-7.a observable), (b) las sesiones sanas siguen
  recibiendo, (c) el 1013 del lento: si los defaults de pila (512 KiB /
  2000 ms) no lo materializan en el guion, el smoke lo ANOTA
  ("1013 no materializado en pila — cubierto por pareja [L] E7.13") y PASS
  con nota (patrón tolerancia Q23 de C3). Pareja [L]: E7.13. DONE 5.
- **E7.15** `archivadoDejaDeEmitir` [S] — `PATCH /api/v1/panel/projects/{id}`
  `{"status":"ARCHIVED"}` → `POST /api/v1/ingest` → **403
  `{"code":"project_archived"}`** (literal §3.4) → cliente filtrado por ese
  proyecto SIN frame en ventana (y psql: sin fila nueva — no hay COMMIT);
  desarchivar → ingest → frame SÍ llega. DONE 6 (derivado — flujo 7 paso 4).
- **E7.17** `soloHandshakeProxyVerde` [S] — `scripts/ws_echo_test.py
  --solo-handshake --token <JWT>` (artefacto C1 SIN edición) → 101 + Accept
  correcto + timeouts 3600 s del proxy; misma forma en los ajustes de
  qa_c2/qa_c3 (R7.3.7). Desambiguación S7-7: ESTO es el Done "echo test del
  proxy WS de C1 sigue verde con el handler real". DONE 8.

### 2.5 Parejas mock↔pila OBLIGATORIAS

| Pareja | [L] | [S] |
|---|---|---|
| 1 | E7.2 (frame forma/§3.5) | E7.1 |
| 2 | E7.3 (batch de 1 / orden) | E7.5 |
| 3 | E7.4 (set_filters por sesión) | E7.6 |
| 4 | E7.7 (401 ×3) | E7.9 |
| 5 | E7.8 (Origin) | E7.9 |
| 6 | E7.12 (latido/reconexión) | E7.10 / E7.11 |
| 7 | E7.13 (1013 dual) | E7.14 (con tolerancia anotada) |
| 8 | E7.19 (pin SQL porIds) | E7.5 (ids reales) |
| 9 | E7.20 (parser ADR-7) | E7.6 |
| 10 | E7.16 (tolerancia/sin replay) | E7.10 (no-JSON literal) — parcial OK |

Criterio: todo Done del 07 certificado en [L] DEBE tener su [S] contra la
pila; E7.16/E7.18 y los pins puros (E7.19/E7.20) cubren lo demás.

## 3. Contratos citados (fuente única de verdad — literales CITADOS)

| Literal | Fuente (congelada) |
|---|---|
| Frame `batch`: `{type:"batch", batch_id:"b_"+uuid4, project_id,
received_at, logs:[]}` UN frame por batch; logs EN ORDEN del lote; batch de
1 para individual; LogDto COMPLETO (11 campos); received_at COMÚN (regla 9) | Schema §3.5 (ejemplo + bullets verbatim) + TRD §2.8 |
| `set_filters` por sesión con EXACTAMENTE la misma semántica que GET /logs
(§3.3); sin cursor/limit; recalificado a cada cambio y tras reconectar | Schema §3.5 + ADR-7 |
| Handshake `GET /ws/logs?token=<JWT>`; token ausente/expirado/inválido →
401 ANTES de asignar recursos; v1 query param (token-in-primer-frame es
FUTURO — riesgo 06, NO C7) | Schema §3.5 + TRD §2.8 |
| Origin validada — NUNCA `"*"` | ficha C7 del slicing (verbatim) |
| Heartbeat: ping del SERVIDOR cada 30 s (`WS_HEARTBEAT_SECONDS`); sin
respuesta en 60–90 s → cierre + limpieza; ping RFC 0x9/0xA, NO JSON ("ningún
mensaje del servidor distinto de batch está previsto en v1; el cliente solo
envía set_filters") | Schema §3.5 + TRD §2.8 + AppFlow 6c |
| Reconexión cliente backoff 1→30 s con reenvío de set_filters; dedup CLIENTE
por id (AppFlow 6b); fallback listado keyset | AppFlow 6b/6c + Schema §3.5 |
| Sin replay: "lo entrado durante una caída no se reemite" | Schema §3.5 (verbatim) |
| Emisión SÓLO tras COMMIT (LiveBroadcaster); api key JAMÁS en el socket;
archivado deja de emitir (403 project_archived pre-COMMIT — flujo 7 paso 4) | Schema §3.5/§5 + TRD §2.8/§2.9 + PRD §6 |
| Seam `IngestListener.onLotePersistido(projectId, receivedAt, ids)` tras
COMMIT — sentencia siguiente de `registrar` (D-12: excepciones sin capturar) | `ingest/IngestListener.java` (C5, byte-a-byte) |
| Backpressure: buffer límite + cerrar sesiones lentas con 1013
(`TRY_AGAIN_LATER`) — valores 512 KiB / 2000 ms del gate P-5 | ficha C7 + gate |
| WS nativo de Spring sin STOMP/SockJS; ruta ÚNICA `/ws/logs`; nginx
`/ws/logs` upgrade 1.1 + timeouts 3600 s (INTOCABLE) | TRD §2.8/§9/§6.2.3 + ADR-5 |
| 413/401/403/400 de ingesta (fail-fast pre-BD); 403 project_archived como
único cuerpo `{"code":"project_archived"}` | Schema §3.4/§4.1 |
| `projectlog.security.frontend-origin` (S26: falta ⇒ cerrado; CORS C2) | CorsConfig (CD-7) |
| Login del panel: `POST /api/v1/panel/auth/login` → `{token, username,
expires_at}` (lo consume la consola) | Schema §3.2/§3.3 |
| Ficha C7 (alcance/Done/Insumos — estado `pendiente` → C8/C9 heredan) | 07_SLICING_PLAN.md (verbatim) |
| Demo Fase 1: "3 logs → aparecen como grupo en ● En vivo sin refresco" | 06 Fase 1 (métricas PRD §7) |

## 4. Fuera de alcance (explícito — NO cubierto por C7)

- **Frontend real (C8)**: banda "● En vivo", dedup por id, backoff 1→30 s,
  banner "Sesión expirada"/"Streaming inactivo", estados ●/○ — C7 SOLO
  expone el servidor + la consola temporal (utilidad, no panel).
- **token-en-primer-frame**: FUTURO documentado (TRD §2.8 — v1 contractual
  = query param, mitigación asumida R-7.d).
- Replay/SSE/STOMP/SockJS (ADR-5)/push externo/alertas/métricas/rate
  limiting.
- Archivado: NO se toca código — DERIVADO (403 pre-COMMIT ⇒ sin seam ⇒ sin
  frame); pina E7.15.
- Migraciones/dependencias/mem limits/env obligatorias: NINGUNA (R7.2/R7.29).
- `GET /logs` NO cambia un byte (el parser se MUEVE, no se reescribe — R7.28).
- Testcontainers (P-2: CERRADO — no se reevalúa más por esta línea).

## 5. Trazabilidad

| Requisito | Escenarios | Verificado por |
|---|---|---|
| R7.1/R7.4 (piezas + tests nuevos) | E7.2–E7.16, E7.19, E7.20 | suite [L] + review de ficheros |
| R7.2/R7.3/R7.37 (frontera, cero deps/migs) | re-corrida + E7.18 | suite 146/146 + smokes C2/C3 |
| R7.5–R7.8 (handshake JWT/Origin/401 sin cuerpo) | E7.7, E7.8, E7.9 | [L] + [S] |
| R7.9–R7.13 (sesión, set_filters, no-contrato) | E7.4, E7.6, E7.16 | [L] + [S] |
| R7.14–R7.16 (frame §3.5 byte-exacto + matching) | E7.2, E7.3, E7.1, E7.5 | pin [L] + pila |
| R7.17/R7.18 (broadcaster + executor + shutdown) | E7.13c, E7.14a, E7.3, E7.19 | [L] + [S] |
| R7.19–R7.21 (heartbeat, latido, reconexión/sin replay) | E7.12, E7.10, E7.11, E7.16e | [L] + [S] |
| R7.22/R7.23 (1013 dual + no-bloqueo) | E7.13, E7.14 | [L] + [S] |
| R7.24–R7.26 (porIds + SQL + mapper reutilizado) | E7.19, E7.5 | pin + pila |
| R7.27/R7.28 (parser ADR-7 + controller compat) | E7.20, E7.18, E7.6 | unit + re-corrida |
| R7.29 (config bloque) | E7.12/E7.13 (props test) + E7.10 (default 30 s) | suite + pila |
| R7.30–R7.32 (consola temporal) | enseñanza al cerrar (verify/archive) | manual + review |
| R7.33–R7.35 (suite + smoke canon + higiene) | todos | qa |

Criterios Done de la ficha (proposal §6), trazados: 1 (E7.1, E7.2, E7.3,
E7.5, E7.19), 2 (E7.4, E7.6, E7.20), 3 (E7.7, E7.8, E7.9), 4 (E7.10, E7.11,
E7.12), 5 (E7.13, E7.14), 6 (E7.15), 7 (E7.16), 8 (E7.17), 9 (E7.18).

Mapa qa:

| Familia | IDs | Tipo |
|---|---|---|
| Cliente WS real (RANDOM_PORT + JDK) | E7.2, E7.3, E7.4, E7.7, E7.8, E7.16 | [L] |
| Ciclo de vida (latido/1013 con mocks) | E7.12, E7.13 | [L] |
| Pins puros | E7.19, E7.20 | [L] sin BD |
| Regresión | E7.18 | [L] suite completa |
| Pila viva | E7.1, E7.5, E7.6, E7.9, E7.10, E7.11, E7.14, E7.15, E7.17 | [S] |

Total: **20 escenarios — 11 [L] + 9 [S]**. QP para qa-plan.