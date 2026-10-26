---
project: Project_Log
change: C7
artifact: explore
last_updated: 2026-10-25
status: draft
---

# C7 — Streaming WebSocket en vivo · explore

Explora cómo sostener el corte C7 sobre lo ya existente. Fuentes: codebase
actual (commits 89b5d60), docs congelados (02_TRD, 03_APPFLOW,
05_BACKEND_SCHEMA, 07_SLICING_PLAN, 01_PRD) y artefactos SDD de C1, C5 y C6.
Nada en docs ni código se corrige: se cita.

## 1. Qué hay

### 1.a El seam `IngestListener` (C5 — plug-and-play real)

`backend/src/main/java/com/projectlog/ingest/IngestListener.java`:

```java
void onLotePersistido(long projectId, OffsetDateTime receivedAt, List<Long> ids);
```

- **Disparo tras-COMMIT ESTRUCTURAL** (no `TransactionSynchronization`):
  la única `@Transactional` del corte C5 vive en
  `ingest.LogsRepository.insert` (INSERT multi-VALUES + `RETURNING id`); el
  método público `IngestService.registrar` NO es transaccional, así que el
  COMMIT ocurre al RETORNAR `insert` y la llamada al seam es **la sentencia
  siguiente** (`IngestService.java` línea 126:
  `oyentes.forEach(o -> o.onLotePersistido(projectId, receivedAt, ids))`).
  Baja 401/403/400/parse jamás llega (validación completa ANTES de tocar la
  BD; `received_at` se calcula una vez tras validación).
- **Suscripción cero-config en C5**: el servicio inyecta
  `List<IngestListener>` — Spring entrega lista VACÍA si no hay beans (S5-10
  rechazó `Optional`: modela 0..n suscriptores). **C7 aporta el PRIMER bean**
  del paquete `streaming`: `@Component` + `@EnableWebSocket` ya activos, la
  llamada `forEach` se activa sola al registrar el bean — sin tocar C5.
- **Excepciones de suscriptores NO se capturan** (D-12): con la fila ya en
  COMMIT, un fallo del listener propaga — at-least-once; el contrato WS no
  reemite (sin replay, Schema §3.5 literal).
- **El nombre LiveBroadcaster es del congelado** (TRD §2.9/Schema §3.5: "el
  `LiveBroadcaster` publica lotes ya persistidos") — el bean que implementa
  `IngestListener` en C7 debería llevar ese nombre o contenerlo.
- **Límite del seam**: solo entrega `projectId, receivedAt, ids`. El frame
  `batch` de Schema §3.5 lleva cada log como **LogDto completo** (11 campos
  incl. `exception` y `metadata`, `query/dto/LogDto.java`) — el listener
  necesita **una lectura por ids** para materializar el frame (ver §2).

### 1.b WS ya existe como infraestructura desde C1 (no es C7 verde-de-cero)

| Pieza | Estado real |
|---|---|
| Dependencia | `spring-boot-starter-websocket` YA en `backend/pom.xml` (desde C1, deps exactas del spec R2). `pom` es congelado a EDICIÓN, legible: **C7 no necesita ningún `mvn` nuevo** — spring-websocket + spring-messaging ya en classpath |
| Registro | `config/WebSocketConfig` (`@Configuration @EnableWebSocket`) registra el handler en la ruta DEFINITIVA `/ws/logs` (TRD §9/§2.8, ADR-5), SIN SockJS. Su javadoc ANUNCIA la sustitución de C7: "allí solo cambia el handler (`EchoSocketHandler` → `LogSocketHandler` + `HandshakeInterceptor` JWT); ni el nginx ni el smoke cambian" — con línea literal `// C1: sin .withSockJS() — ADR-5; sin .addInterceptors(...) — C7` |
| Handler temporal | `streaming/EchoSocketHandler` (`@Component`, `TextWebSocketHandler`, eco puro) — su javadoc lo declara "el ÚNICO punto de sustitución del corte C7". `streaming/package-info.java` fija el plano C7: `LogSocketHandler` con HandshakeInterceptor (JWT 401 pre-upgrade), emisión tras COMMIT, heartbeat |
| Seguridad | `SecurityConfig` cadena 3 (@Order(3)) ya `permitAll()` `/ws/logs` (S31, gate G2 de C2 — deliberado: sin ella el Echo moría con 401). **El auth de C7 va en el HandshakeInterceptor, NO en esta cadena**: el interceptor valida el JWT ANTES del upgrade; sin SecurityContext necesario. `TokenService.verify(token)` → `Optional<JwtPrincipal>` es el verificador reutilizable (patrón cross-feature por referencia: el formatter de C2 ya se reutiliza así en C6) |
| nginx | `frontend/nginx.conf` `location /ws/logs`: `proxy_http_version 1.1`, `Upgrade`/`Connection "upgrade"`, `Host`/`X-Real-IP`/`X-Forwarded-*`, `proxy_read/send_timeout 3600s` — literales del TRD §6.2.3 (E5/E7 de C1), NO se toca |
| Compose/env | `compose.yaml` YA pasa `WS_HEARTBEAT_SECONDS` al backend; `.env.example` ya lo trae (30). **`application.yml` NO lo mapea todavía** — su comentario lo reserva: "sin WS_HEARTBEAT_SECONDS (heartbeat es C7)" → C7 añade el bloque `projectlog.streaming` |
| Smoke WS cliente | `scripts/ws_echo_test.py` — cliente RFC 6455 Python STDLIB (handshake + Sec-WebSocket-Accept, frames mascarados, eco, cierre 0x8, test de inactividad 75 s contra los timeouts 3600 s del nginx). **`--token` existe como CLI desde C1 con el comentario exacto "(solo C7) JWT en ?token=… del handshake"** y `handshake()` ya rellena `path += f"?token={token}"`; el modo `--solo-handshake` es reutilizable tal cual. Riesgo: el modo COMPLETO (eco idéntico byte a byte) MORIRÁ con el handler real — un frame de texto genérico no es `set_filters` y el servidor solo emite `batch` + ping (§2.d) |

### 1.c Contrato WS ya pinado por los docs CONGELADOS (no re-preguntar)

Schema §3.5 + TRD §2.8/§2.9 + AppFlow flujo 6 + ficha C7 del 07_SLICING_PLAN
(estado `pendiente`):

- Ruta ÚNICA `/ws/logs`; WS nativo de Spring, sin STOMP/SockJS (ADR-5, decisión cerrada).
- Handshake `GET /ws/logs?token=<JWT>`: HandshakeInterceptor valida ANTES de
  abrir el socket; token ausente/expirado/inválido → **401 antes de asignar
  recursos** (pre-upgrade). Token en query param es la v1 CONTRATUAL
  (mitigación "token en primer frame" explícitamente FUTURA — riesgo C7 del
  06 y respuesta del slicing: en query param; Tailscale/logs como mitigación
  asumida).
- `set_filters` por sesión: `{"type":"set_filters","filters":{…}}` tras
  conectar, a cada cambio y tras cada reconexión; los valores con
  **exactamente la misma semántica** que los query params de `GET /logs`
  (§3.3; ADR-7 coherencia vivo/listado). Sin cursor/limit en el frame.
- Frame servidor→cliente: **UN frame JSON por batch** `{type, batch_id,
  project_id, received_at, logs:[…]}`, logs en orden del lote, log individual
  = batch de 1 (el seam ya lo emite así: `IngestService` llama al listener
  también en individual, §1.a); `batch_id` = `b_` + UUID v4 opaco del
  servidor; `logs` usa el **LogDto completo**; top-level `received_at` = el
  común del lote (regla 9). El ejemplo del §3.5 muestra fechas sin micros —
  coherente con reutilizar `LogDto` YA-SERIALIZADO (sus campos
  timestamp/received_at son String con el formatter de C2 truncado a
  segundos, E6.1).
- Emisión SOLO tras COMMIT (ADR-9) — vive gratis en el seam (§1.a).
- Dedup por `id` es del **client** (AppFlow flujo 6b, C8); servidor no guarda estado de cliente.
- Heartbeat: ping del SERVIDOR cada 30 s (`WS_HEARTBEAT_SECONDS`); sin
  respuesta en 60–90 s → cierre y limpieza de sesión. Reconexión y backoff
  1 s→30 s son del CLIENTE (C8) — C7 solo cierra.
- **Sin replay** (literal: "lo entrado durante una caída no se reemite"; el
  fallback es el listado keyset + dedup — C6 ya lo sirve). "Ningún mensaje
  del servidor distinto de `batch` está previsto en v1; el cliente solo envía
  `set_filters`" — **esto PINA el heartbeat como frame de control RFC (ping
  0x9 / pong 0xA), NO como mensaje JSON** (un ping JSON violaría el literal;
  además el browser responde pong a los ping automáticamente).
- API key JAMÁS autentica el socket (PRD §6, Schema §5); archivado deja de
  emitir — **derivado, no comprobado**: la ingesta a proyecto ARCHIVED se
  bloquea con 403 `project_archived` ANTES de persistir
  (`IngestController`), luego no llega COMMIT, no llega seam, no llega frame
  ("sin ingesta no hay flujo en vivo", flujo 7 paso 4). La ficha C7 del
  slicing lo fija exactamente así.
- Origin validada en handshake — NUNCA `"*"` (ficha C7 del slicing, literal).
  La fuente del origen permitido ya existe: `projectlog.security.frontend-origin`
  (CorsConfig, S26: falta ⇒ cerrado).

### 1.d Patrones previos aprovechables (C5/C6)

- Lecturas de `logs` SOLO desde `query/`: `LogsQueryRepository` es la ÚNICA
  fuente de SQL de lectura (frontera C5: nunca añadir métodos a
  `ingest.LogsRepository` — INSERT-only). Hoy expone `listar(…)` (17 `?`
  keyset, el estamento ÚNICO de C6) y `porId(id)` → `LogDto`
  (RowMapper `FILA_DETALLE` con jsonb → JsonNode). `CursorCodec`
  (Base64url opaco, micros preservados) y `FiltrosConsulta.patronIlike`
  (puro, cuyo javadoc ya declara "seam C7: la misma semántica de filtros del
  stream §3.5 la reutiliza").
- Tests: unit puro + pin del SQL literal (`LogsRepoSqlTest` patrón) + Mvc
  sobre controller con repo mockeado + smoke [S] `qa_cN_smoke.sh` sobre la
  pila real 3×healthy (recomposición `--build --force-recreate`, NUNCA
  `down -v`, conteos relativos, needles sufijados, keys/JWT a tmpdir 700).
- C6 cerró explícitamente: "El smoke de C5 dejó el seam tras COMMIT; C6 NO lo
  conecta a nada (el listener queda sin beans suscriptos — **C7 decide**)".
- Deudas VIVAS que NO tocan C7: 401 ISO-8859-1 (C8/C9), smoke `docker
  compose` canónico (C9), nginx `client_max_body_size` (C9), mockups (C3, ya
  servibles en :8080/mockups).

## 2. Qué falta en el backend para C7

### 2.a Piezas NUEVAS (ficheros esperados del paquete `com.projectlog.streaming`)

| Pieza | Papel |
|---|---|
| `LogSocketHandler` | `TextWebSocketHandler`: afterConnectionEstablished (registrar sesión con envoltorio anti-backpressure), `handleTextMessage` que SOLO entiende `set_filters` (validación con semántica GET /logs; los demás mensajes: ¿ignorar/3008? — Schema §3.5 dice el cliente solo envía set_filters), `handlePongMessage` (feed del heartbeat), afterConnectionClosed (limpiar registro y timers). El estado por sesión (filtros parseados + último latido) vive en attributes de la `WebSocketSession` |
| `LiveBroadcaster` (o integrado) | Implementa `ingest.IngestListener` (el primer bean del seam): genera `batch_id` (`b_`+UUIDv4), resuelve las filas por ids (§2.c), filtra por sesión y emite el frame JSON único |
| Handshake interceptor JWT | `HandshakeInterceptor.beforeHandshake` que lee `?token=`, verifica con `TokenService.verify`, rechaza con **HTTP 401 sin upgrade** y valida el header `Origin` contra la fuente `frontend-origin` (nunca `"*"`; sin origen de frontend configurado S26 ⇒ cerrar). Sin token ni loguear fragmentos (patrón higiene S16) |
| Registro de sesiones + heartbeat | Mapa thread-safe de sesiones activas, timer (`ScheduledExecutorService` propio o task) que pinge cada `WS_HEARTBEAT_SECONDS` y cierra lo que lleve sin pong el umbral fijado (60–90 s de los docs — exponer en gate) |
| Envoltorio anti-backpressure | `ConcurrentWebSocketSessionDecorator(session, sendTimeLimit, bufferSizeLimit)` por sesión: acumula el buffer de envíos y cierra con `SessionLimitExceededException` → `CloseStatus.TRY_AGAIN_LATER` (**1013 "Try Again Later"**, el código del contrato). Los doc congelados NO pinan N de buffer ni tiempo de envío — pregunta de gate (§3) |
| Smoke C7 | `scripts/qa_c7_smoke.sh` (patrón canon de C5/C6) + cliente WS ampliado (§2.e) |

Punto de anclaje en ficheros EXISTENTES (la frontera del toque):

- **`config/WebSocketConfig` — la única EDICIÓN prevista por diseño previo**:
  cambiar `EchoSocketHandler` por el handler real en su constructor y añadir
  `.addInterceptors(...)` en la línea que C1 dejó marcada `// C7`. Su
  javadoc ya describe el cambio: "ni el nginx ni el smoke cambian".
- **¿Fate de `EchoSocketHandler`?** La ruta `/ws/logs` es ÚNICA (TRD §9) — no
  puede quedar registrada junto al handler real en la misma ruta. Opciones:
  eliminar el fichero (el package-info ya lo llama "temporal de prueba"),
  o conservarlo sin registrar en ruta alguna. Impacto directo en
  `scripts/ws_echo_test.py` (§2.e) — decisión de gate.
- **`SecurityConfig`: NO debería cambiar** (la cadena 3 ya cubre `/ws/logs`
  con permitAll bajo interceptor propio). PERO se revisa un matiz: la ruta
  NO coincide con cadena 1/2; una revisión de que el interceptor rechaza con
  401 PROPIO y no del entry point compartido (el literal `invalid_token` es
  del panel REST — el 401 del handshake es de otra familia; Schema §5: "el
  handshake se rechaza con 401 antes del upgrade" sin pinar el cuerpo) —
  punto de decisión del spec, no de código ya contratado.
- **`application.yml`: se añade** el bloque `projectlog.streaming`
  (heartbeat-seconds ← `WS_HEARTBEAT_SECONDS`; los límites anti-backpressure
  si van env). `compose.yaml`/`.env.example` ya envían el 30 — sin cambios
  esperados allí.
- **¿`query/` se toca?** Sí, con dos candidatos exactos (§2.c/§2.d): un
  método de lectura por ids en `LogsQueryRepository` — fichero EXISTENTE — y
  un extractor del parseo/validación de filtros para reutilizarlo del
  `set_filters` (los 8 checks del `GET /logs` viven PRIVADOS en
  `LogsController` — la "misma semántica" del §3.5 hoy no está expuesta como
  lógica de paquete). Alternativa sin tocar ficheros existentes: replicar el
  parseo en streaming (riesgo de divergencia — C6 explore lo recomendó
  explícitamente REUTILIZAR como lógica de paquete de query).

El resto de piezas: handler/config son ficheros NUEVOS en `streaming/` (el
package ya se llama streaming desde C1); la anexión del bean al seam no
exige tocar `ingest/` (lista inyectada).

### 2.b Emisión estratégica (qué decide el spec, cuál es el hueco)

- El `onLotePersistido` llega en el HILO Tomcat del request de ingesta: si
  el broadcast sincrónico bloquea una sesión lenta, bloquea la 202. Dos
  rutas: (a) envolve el síncrono en el decorador con `sendTimeLimit`
  (bloqueo acotado, el más simple); (b) un executor de broadcast de un solo
  hilo (cola interna — más piezas, cero acople). El contrato del corte fija
  "buffer límite + 1013", compatible con ambas.
- Thread-safety del frame por sesión: el envuelto
  `ConcurrentWebSocketSessionDecorator` ya hace thread-safe los
  `sendMessage` concurrentes entre broadcast y heartbeat.
- El formato del frame debe salir del serializador del BEAN único de
  Jackson (`ObjectMapper` de Boot, D-3 — nunca instancia suelta) y con
  `LogDto` tal cual (los nulls incluidos, patrón C6).

### 2.c Lectura por ids para materializar el frame (hueco REAL del repo)

El frame necesita las 11 columnas de hasta 200 filas NUEVAS por id. Opciones:

1. **Nuevo método `LogsQueryRepository`** (fichero EXISTENTE, frontera
   respetada): `SELECT … WHERE id IN (…)` / `= ANY(?)` con los ids del seam,
   Order implícito por el RETURNING del C5 (los ids YA llegan en orden de
   VALUES — la moneda del frame; el SELECT debería preservar el orden
   reordenando en Java o con `ORDER BY array_position`).
2. `porId(id)` × N (máx 200 llamadas por lote — simple, N+1 en DB pero
   reutiliza el RowMapper del detalle tal cual).

El RowMapper `FILA_DETALLE` actual mapea por-columna — extraerlo a un
mapper estático compartido o duplicar la lambda sería decisión del design.

### 2.d `set_filters` — reuso del parseo de C6

La "misma semántica" de §3.5 implica re-usable la cadena de 8 checks de
`LogsController` (limit NO aplica al stream — sin paginación). Falta decidir
de dónde sale el validador compartido (extraer a query vs replicar en
streaming) — PREGUNTA DE GATE.

### 2.e Qué pasa con `ws_echo_test.py` (C1)

El smoke de C1 (invocado por `qa_c2_smoke.sh`/`qa_c3_smoke.sh` desde su
fase) DEPENDEN del eco con el handler temporal. Con `LogSocketHandler`
real:

- `--solo-handshake` sigue pasando (101 + Accept) — y con `--token` válido
  (el handshake autenticado) o sin él (si se conserva el permitAll de
  cadena 3, el interceptor decide).
- El modo completo (eco + inactividad) ROMPERÁ: el handler real no ecoa —
  el texto enviado sin ser `set_filters` queda fuera del contrato v1.
- La ficha C7 Done: "echo test del proxy WS de C1 sigue verde con el handler
  real" — interpretación probable: el PARTE del proxy (handshake + timeout
  3600 s) sigue verde; la eco se sustituye por el smoke que C7 añade. A
  resolver en el spec/proposal (P-3) — no está pinado qué pasa con el
  fichero `EchoSocketHandler`.

## 3. Testcontainers — reevaluación EXPLÍCITA (deuda de C5/C6 cerrada)

**Recomendación: NO Testcontainers en C7 — con un escalón NUEVO en medio.**

Qué aportaría TC para streaming (el corte donde el SOCKET real más apetece):

- WS real sobre HTTP upgrade + BD real en `mvn test` (handshake con 401,
  frame tras COMMIT contra PG, filtros por sesión, heartbeat).
- Contra el patrón vigente (C5 explore §h + C6 explore §f, ambos NO-TC):
  (1) 2 dependencias nuevas en un pom congelado-a-edición y specs con
  versiones EXACTAS (RG-1); (2) pull de imagen y contextos Spring más
  pesados; (3) **docker-in-build no demostrado**: la suite 146/146 corre en
  el builder `maven:3.9-eclipse-temurin-21` (R-nuevo-6), no está probado
  docker-in-mvn en esta VM; (4) divergencia de patrón (el repo tiene un
  patrón canónico de smoke [S] sobre pila viva y repetidos).

El hueco que TC quería cerrar se cierra MEJOR con Java stdlib:

- **`java.net.http.HttpClient.newWebSocketBuilder()`** (JDK 11+, ya en el
  classpath de Java 21, CERO dependencias nuevas): cliente WebSocket REAL
  que puede conectarse a `@SpringBootTest(webEnvironment = RANDOM_PORT)` →
  Tomcat REAL → upgrade REAL → handler REAL, con repo mockeado para las
  aserciones del frame/broadcast/1013. Cubre handshake (401 pre-upgrade con
  token inválido), filtros por sesión, frame `batch` y cierre 1013 con un
  grado de realidad idéntico al de TC para la PARTE WS (TC solo añadiría la
  BD real, que el patrón vigente ya prueba en smoke).
- La cadena completa ingesta→COMMIT→frame→PG queda en el smoke [S] de C7
  sobre la pila viva (patrón canon del repo), ampliado con el cliente WS
  stdlib del §2.e.

| Escenario | Dónde vive |
|---|---|
| Handshake 401 / Origin rechazada / token expirado | unit-integración con `@SpringBootTest` RANDOM_PORT + `java.net.http` WebSocket |
| Handler: set_filters inválido, frame ordenado, heartbeat, 1013 | ídem (broadcast simulado a mano vía bean test o fake listener) |
| SQL por ids / RowMapper del frame | pin del SQL literal (patrón `LogsRepoSqlTest`) |
| E2E batch→frame por nginx con token real, recon, fallback REST | smoke [S] `qa_c7_smoke.sh` sobre pila viva |

La deuda de reevaluación (P4 de C6, apuntada a C7) queda ASÍ CONTESTADA: no
se adopta TC ni en C7; el argumento de TC pierde casi todo su valor cuando
existe un cliente WS JDK stdlib contra Tomcat real sin dependencias.

## 4. Preguntas de gate (P-1 … P-9) — para la proposal

Lo ya CONTRACTUAL y NO re-preguntar: ruta única `/ws/logs`, WS nativo sin
SockJS, JWT en query param v1 (mitigación ya diseñada para futuro, NO
ahora), `set_filters` con semántica idéntica a `GET /logs`, forma EXACTA del
frame `batch` (Schema §3.5 literal, LogDto completo, `b_`+UUID), emisión tras
COMMIT, batch de 1 para ingestas individuales, dedup en cliente (C8), sin
replay, heartbeat ping del servidor cada 30 s vía `WS_HEARTBEAT_SECONDS`
(compose ya lo envía), archivado deja de emitir (derivado de 403 en ingesta),
API key jamás en el socket, Origin validada — nunca `"*"` (ficha C7),
backpressure con cierre 1013 (contrato del corte) y fallback al listado
paginado (C6 ya lo sirve).

- **P-1 · Consola de prueba temporal**: ¿sí/no, y formato — página estática
  mínima (¿dónde vive? candidates: `frontend/public` servida por nginx —
  el panel real llega en C8 — o mockups de C3) o script Python stdlib
  extendido del cliente RFC de `ws_echo_test.py`? (La pieza es candidata
  para el gate según el contrato del corte.)
- **P-2 · Testcontainers**: ¿se ratifica NO (recomendación §3: cierre la
  deuda con `java.net.http` WS client + Tomcat RANDOM_PORT + smoke de pila
  viva) o se adopta TC solo para este corte?
- **P-3 · Destino de `EchoSocketHandler` y del echo test de C1**: ¿se elimina
  el fichero temporal (package-info dice "a SUSTITUIR") o queda sin registrar
  en ruta? ¿El modo --solo-handshake de `ws_echo_test.py` se queda como la
  prueba permanente del proxy y la parte de eco/`set_filters` pasa a
  `qa_c7_smoke.sh`? ¿Se toca `ws_echo_test.py` (artefacto C1) o se crea un
  cliente NUEVO para C7? (La ficha Done: "echo test del proxy WS de C1 sigue
  verde con el handler real" — desambiguar qué significa verde.)
- **P-4 · Umbral exacto del cierre por latido**: los docs dan un RANGO
  ("sin respuesta en 60–90 s"); ¿se fija 60 (2 latidos perdidos), 90, o
  configurable `projectlog.streaming.*`? ¿El umbral va env-able o constant?
- **P-5 · Política 1013 (números)**: ¿un único límite o sendTimeLimit +
  bufferSizeLimit simultáneos (`ConcurrentWebSocketSessionDecorator`)? ¿Qué
  N (bytes) y qué tiempo (ms) por defecto, y ¿configurables por env o
  constants del paquete? ¿El cierre 1013 es solo por buffer o también por
  send lento (sendTimeLimit vencido)?
- **P-6 · Hilo de emisión**: broadcast sincrónico en el hilo Tomcat del
  request de ingesta (bloqueo acotado por sendTimeLimit) vs executor propio
  de un solo hilo para el broadcaster (cola). El contrato fija buffer+1013;
  la strategy es del spec.
- **P-7 · Quién registra el bean / forma del paquete**: `LiveBroadcaster`
  como implementación única de `IngestListener` Y dueño del registro de
  sesiones (1 bean) vs separación handler/broadcaster; ¿el
  HandshakeInterceptor vive en `streaming/` inyectando `TokenService` (auth)
  y `frontend-origin` — o en `config/` junto a `WebSocketConfig`?
- **P-8 · Lectura por ids para el frame**: ¿método nuevo en
  `LogsQueryRepository` (una SELECT `IN`/`ANY` con preservación del orden de
  `RETURNING`) vs N×`porId` (hasta 200 llamadas)? ¿Se extrae/replica el
  parseo de filtros de `LogsController` para el `set_filters` (recomendado:
  lógica de paquete en query, prevente divergencia ADR-7)?
- **P-9 · Rechazo de mensajes no-contrato y 401 del handshake**: ¿un frame
  de texto que no sea `set_filters` válido se IGNORA (tolerancia) o se cierra
  la sesión (3008/4003)? ¿El 401 pre-upgrade lleva cuerpo JSON (¿cuál
  literal? los docs solo fijan el código HTTP) o 401 vacío? (El literal
  `invalid_token` del panel es del entry point REST — la familia del 401 del
  handshake es hoy un hueco del spec.)

## 5. Riesgos del corte (borrador para el spec)

| R-ID | Riesgo | Mitigación |
|---|---|---|
| R-7.a | Broadcast en hilo de ingesta bloquea la 202 ante cliente lento | Decorador con sendTimeLimit acotado o executor de broadcast; 1013 cierra |
| R-7.b | Backpressure sin límite: el buffer del decorador crece sin techo (memoria) | bufferSizeLimit EXPLÍCITO + cierre 1013 (contrato); valores a gate |
| R-7.c | Frame emitido ante sesiones muertas/zombies: registro de sesiones con leak | afterConnectionClosed + heartbeat como GC del mapa; Concurrent |
| R-7.d | JWT en query param queda en logs del nginx (riesgo conocido del 06, v1 aceptada) | Documentado TRD §2.8 (mitigación futura token en primer frame); higiene: el backend JAMÁS loguea el token (S16) |
| R-7.e | Filtros del stream divergen del GET /logs (dos parseos) | Extraer/reutilizar parseo de query (P-8); pin de semántica compartida ADR-7 |
| R-7.f | El smoke del eco de C1 deja de pasar → regression silenciosa del proxy | `--solo-handshake` permanente en la suite + smoke C7 con handler real (P-3) |
| R-7.g | `set_filters` con campos NO del §3.3 (cursor/limit) o metadata del frame distinta de LogDto | Validación estrecha de la forma del mensaje del cliente (P-9); frame pinado por test |