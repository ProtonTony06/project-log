---
change: C7
artifact: design
project: Project_Log
last_updated: 2026-10-25
status: draft
---

# Design — C7 · Streaming WebSocket en vivo

> Fase sdd-design del corte C7. Este documento **IMPLEMENTA
> arquitectónicamente** el spec (`spec.md`, status draft) — no lo renegocia.
> Fuentes normativas: spec C7 (R7.1–R7.37, cierres S7-1…S7-12,
> divergencias D-C7-1/D-C7-2, escenarios E7.1–E7.20) + proposal C7 (status
> **approved** — gate 9/9 del 2026-10-25) + código real verificado en HEAD
> `89b5d60` (2026-10-25): `config/WebSocketConfig` (ancla literal
> `// C1: sin .withSockJS() — ADR-5; sin .addInterceptors(...) — C7`),
> `streaming/EchoSocketHandler` (javadoc "el ÚNICO punto de sustitución"),
> `streaming/package-info.java` (INTOCABLE), `ingest/IngestListener`
> (seam `onLotePersistido(long, OffsetDateTime, List<Long>)`, byte-a-byte),
> `ingest/IngestService` (L126: `oyentes.forEach(o ->
> o.onLotePersistido(...))` — lista inyectada, CERO diff en ingest/**),
> `query/LogsController` (8 checks en orden verificado),
> `query/FiltrosConsulta` (seam C7 declarado en su javadoc),
> `query/LogsQueryRepository` (`FILA_DETALLE`, `SELECT_POR_ID`),
> `auth/TokenService.verify → Optional<JwtPrincipal>` (L74),
> `auth/LoginController.EXPIRES_AT_FORMATTER` (C2, truncado a segundos),
> `TokenServiceTest` (TTL negativo ⇒ token expirado — patrón E7.7b),
> `application.yml` (reserva "sin WS_HEARTBEAT_SECONDS (heartbeat es C7)"),
> `scripts/qa_c2_smoke.sh` (Q20 es LA invocación del eco),
> `scripts/qa_c3_smoke.sh` (ws_echo SOLO como intocable de E3.24 — ver
> D-7.15). Stack: Spring Boot 3.5.16 / Java 21 / PG17 — **cero cambios de
> pom, cero migraciones** (R7.2: la deuda Testcontainers CIERRA con la
> sustitución declarada: cliente WS JDK + RANDOM_PORT + smoke de pila viva).
> Nombres de piezas CONTRACTUALES del spec (R7.1): `LogSocketHandler`,
> `LiveBroadcaster`, `JwtHandshakeInterceptor`.

## 0. Resumen del diseño

```
POST /api/v1/ingest[/batch]                  (C5, INTOCABLE — dif 0)
  └─ validar → INSERT multi-VALUES + RETURNING → COMMIT
  └─ sentencia siguiente (IngestService L126, byte-a-byte):
       oyentes.forEach(o -> o.onLotePersistido(projectId, receivedAt, ids))
            │  ← el bean de C7 activa el seam SIN ningún diff en ingest/**
            ▼ hilo Tomcat de la 202 (SOLO encola — R7.17/D-7.2)
     LiveBroadcaster implements IngestListener      [NUEVO streaming/]
            ├─ broadcast.execute(() -> distribuir(...))   executor monohilo
            │    ├─ (1) ids null/vacío → nada
            │    ├─ (2) porIds(ids) → List<FilaBatch>    [query/, UNA SELECT]
            │    ├─ (3) batch_id = "b_" + UUID v4
            │    ├─ (4) por cada sesión registrada (snapshot del mapa del
            │    │       HANDLER): match §1.D → sendMessage (decorador)
            │    └─ (5) excepciones: por-sesión swallow+WARN; del lote
            │            swallow+ERROR — NUNCA al hilo Tomcat (D-7.2)
            ▼
     LogSocketHandler (TextWebSocketHandler, dueño del registro + latido)
            ├─ afterConnectionEstablished: envolver en
            │    ConcurrentWebSocketSessionDecorator(session, sendTimeLimit,
            │    bufferSizeLimit) + registrar (ConcurrentHashMap)
            ├─ handleTextMessage: SOLO set_filters (shape §3.5) →
            │    FiltrosConsulta.parsear (única fuente ADR-7) → attributes
            ├─ handlePongMessage: último pong (latido)
            ├─ scheduler propio: ping 0x9 cada 30 s + GC a 75 s (1001)
            └─ afterConnectionClosed: limpieza total del registro (R-7.c)
     JwtHandshakeInterceptor (HandshakeInterceptor): Origin PRIMERO (403) →
       token LUEGO (401) — pre-upgrade SIN cuerpo; log WARN única línea.

config/WebSocketConfig — 2 ediciones anunciadas por su propio javadoc C1
application.yml — el bloque projectlog.streaming (R7.29) + cabecera
```

---

## 1. Mapa del paquete `com.projectlog.streaming`

Package-by-feature (P-7 opción b): **3 ficheros main NUEVOS** — la
mini-clase `SesionesActivas` que el spec dejaba optativa NO se separa
(D-7.1) y `EchoSocketHandler` se REMUEVE (§5). El `package-info.java`
existente queda byte-a-byte (R7.4); su texto histórico ("el registro en
/ws/logs y el smoke … no cambian") queda como plano declarado de C1 — intocable.

### 1.1 `streaming/LogSocketHandler.java` — NUEVO

Dueño del registro, del `set_filters`, del latido y de la limpieza (R7.1).

```java
@Component
public class LogSocketHandler extends TextWebSocketHandler {

    // — Estado (R7.9 + D-7.1) —
    private final ConcurrentHashMap<String, ConcurrentWebSocketSessionDecorator>
            registradas = new ConcurrentHashMap<>();   // sessionId → envoltura
    static final String ATTR_FILTROS     = "streaming.filtros";      // Filtros | null
    static final String ATTR_ULTIMO_PONG = "streaming.ultimo-pong";  // Long (nanoTime)

    // — Scheduler PROPIO del latido (R7.19/D-7.10) —
    private final ScheduledExecutorService latido = Executors
            .newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "streaming-heartbeat");
                t.setDaemon(true);
                return t;
            });

    // R7.29 — @Value de las 4 properties (heartbeat-seconds, idle-timeout-
    // seconds, send-time-limit-ms, buffer-size-bytes) por constructor.

    @Override public void afterConnectionEstablished(WebSocketSession sesion);
    @Override protected void handleTextMessage(WebSocketSession sesion,
            TextMessage mensaje);
    @Override protected void handlePongMessage(WebSocketSession sesion,
            PongMessage mensaje);
    @Override public void afterConnectionClosed(WebSocketSession sesion,
            CloseStatus estado);
    @PreDestroy void apagar();                       // shutdown + shutdownNow
    List<ConcurrentWebSocketSessionDecorator> sesionesVivas();  // snapshot
    void cerrarPorLentitud(WebSocketSession sesion); // 1013 (§1.G)
}
```

**D-7.1 — Registro y estado.** El mapa `registradas` vive DENTRO del
handler (NO bean separado — R7.1). Valor = la `ConcurrentWebSocketSessionDecorator`
envolvente (R7.9 MUST); la sesión RAW queda accesible con
`decorador.getDelegate()` cuando hace falta cerrar/leer attributes. El
estado por sesión (filtros parseados + último pong) vive EN LOS ATTRIBUTES
de sesión (R7.9 literal), con claves `static final` de arriba y acceso
SIEMPRE bajo `synchronized (sesion.getAttributes())` (patrón oficial de
Spring WebSocket para estado por sesión — el mapa de attributes es fijo del
handshake y su mutación concurrente se serializa con este lock). El
broadcaster lee los filtros vía el decorador (delega `getAttributes`). Sin
mini-clase de paquete ⇒ 3 ficheros main.

**D-7.10 — Latido (R7.19/R7.20).** Scheduler PROPIO single-thread daemon
`streaming-heartbeat`, tick cada `heartbeat-seconds`: (a) envía a CADA
sesión registrada un `org.springframework.web.socket.PingMessage()` (frame
de CONTROL RFC 0x9 — NUNCA JSON) vía el decorador; (b) GC del mapa: si
`System.nanoTime() - ultimoPong > idle-timeout-seconds` →
`cerrar(sesion, CloseStatus.GOING_AWAY)` (1001 — decisión anclada S7-9;
el ÚNICO código pineado por lentitud es 1013) vía `cerrarPorLentitud(...)`
(no — por latido NO es 1013: close directo con 1001 + log WARN línea
genérica; el afterConnectionClosed limpia). El umbral corre desde el ÚLTIMO
pong o desde `afterConnectionEstablished` si nunca respondió; 75 > 30
tolera un ping perdido entero (S7-9). `handlePongMessage` actualiza
`ATTR_ULTIMO_PONG` bajo lock. `afterConnectionClosed` elimina del mapa con
CUALQUIER código de cierre (R7.13/R-7.c).

**D-7.2 — 1013 helper (R7.22).** `cerrarPorLentitud` cierra la sesión con
`CloseStatus.TRY_AGAIN_LATER` (1013) y log WARN ÚNICA línea sin contenido;
la limpieza la hace `afterConnectionClosed` (single source of truth del
remove — jamás un segundo remove paralelo).

### 1.2 `streaming/LiveBroadcaster.java` — NUEVO (nombre CONTRACTUAL)

```java
@Component
public class LiveBroadcaster
        implements com.projectlog.ingest.IngestListener {

    private final LogSocketHandler handler;
    private final LogsQueryRepository logsQueryRepository;
    private final ObjectMapper objectMapper;   // bean ÚNICO de Boot (D-3)
    private final ExecutorService broadcast;   // D-7.2: monohilo FIFO daemon
                                               // "streaming-broadcast"

    @Override public void onLotePersistido(long projectId,
            OffsetDateTime receivedAt, List<Long> ids);
    @PreDestroy void apagar();  // shutdown() → awaitTermination(5 s) → shutdownNow()
    private void distribuir(long projectId, OffsetDateTime receivedAt,
            List<Long> ids);    // la tarea del executor
}
```

**D-7.2 — Executor y ciclo de vida (P-6, R7.17/R7.18, S7-5).** Campo final
creado en el constructor: `Executors.newSingleThreadExecutor(ThreadFactory`
`daemon "streaming-broadcast")` — FIFO por construcción ⇒ orden de lotes
preservado; cola sin límite declarado (v1 monousuario — R-7.a cerrado).
`onLotePersistido` corre en el HILO TOMCAT de la 202 y SOLO DEBE encolar:
todo su cuerpo va bajo `try { broadcast.execute(() -> distribuir(...)); }`
con el `catch (RejectedExecutionException | RuntimeException ex)` → log
WARN sin contenido y return — **JAMÁS propaga una excepción al seam**
(R7.18; D-12 de C5 deja las excepciones del listener sin capturar — el
catch vive AQUÍ). `@PreDestroy`: `shutdown()` + `awaitTermination(5,`
`SECONDS)` (los lotes ya encolados se INTENTAN emitir) → `shutdownNow()`
(los restantes se descartan — sin garantía post-shutdown, coherente con
"sin replay"). El handler tiene el MISMO patrón de `@PreDestroy` para su
scheduler de latido.

### 1.3 `streaming/JwtHandshakeInterceptor.java` — NUEVO

```java
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final TokenService tokenService;   // por referencia (C2) — SOLO verify
    private final String frontendOrigin;       // @Value("${projectlog.security.
                                               //           frontend-origin:}")

    @Override public boolean beforeHandshake(ServerHttpRequest request,
            ServerHttpResponse response, WebSocketHandler wsHandler,
            Map<String, Object> attributes);   // NUNCA escribe attributes
    @Override public void afterHandshake(ServerHttpRequest request,
            ServerHttpResponse response, WebSocketHandler wsHandler,
            Exception exception);              // no-op
}
```

**D-7.9 — Orden del reject (S7-12/R7.5).** `beforeHandshake`:

1. **Origin PRIMERO**: `frontendOrigin.isEmpty()` → rechazo de TODO
   handshake (S26 extendido — NUNCA `"*"`), status **403** (familia del
   check que falla; anti-oráculo: un solo status para la familia Origin);
   header `Origin` PRESENTE y `!frontendOrigin.equals(origin)` (comparación
   EXACTA, sin normalizar) → **403**; header AUSENTE → continúa (S7-12:
   clientes RFC no-browser legítimos — el JDK del test y el Python del
   smoke no lo envían).
2. **token LUEGO**: query param EXACTO `token` extraído del
   `request.getURI()` (`UriComponentsBuilder.fromUri(request.getURI())`
   `.build().getQueryParams().getFirst("token")`); ausente o vacío →
   **401**; `tokenService.verify(token).isEmpty()` (expirado/inválido —
   Optional SIN distinguir causa, S5/R-7.d) → **401**; si no → `true`
   (upgrade procede; una API key JAMÁS autentica el socket — R7.8: la
   cadena 3 `permitAll()` de `/ws/logs` queda tal cual, CERO diff en
   `SecurityConfig`).

El rechazo es SIEMPRE: `response.setStatusCode(...)` + `return false` —
pre-upgrade, cuerpo VACÍO (NUNCA se escribe body), NUNCA el
`RestAuthenticationEntryPoint` ni `invalid_token` (familia REST del PANEL,
otra superficie — S7-3). Log: UNA línea WARN genérica `"handshake WS`
`rechazado"` (R7.7 — sin token, sin fragmentos, sin causa distinguible,
sin la cadena de query). NINGÚN JWT DEBE quedar en los attributes (R7.7).

### 1.4 `config/WebSocketConfig.java` — EXACTAMENTE 2 ediciones (R7.3.1)

(a) El handler del registro pasa de `EchoSocketHandler` a
`LogSocketHandler` (campo+constructor+argumento — mismo patrón);
(b) la línea ancla se RESUELVE:

```java
        registry.addHandler(logSocketHandler, "/ws/logs")
                .addInterceptors(jwtHandshakeInterceptor);
        // C7 ancla resuelta: .addInterceptors(jwtHandshakeInterceptor) — JWT+Origin
        // pre-upgrade (spec §1.B); sin .withSockJS() — ADR-5 (frase conservada)
```

La frase `sin .withSockJS() — ADR-5` del ancla se CONSERVA; la mitad
`sin .addInterceptors(...) — C7` queda sustituida por la realidad (el
comentario nuevo DOCUMENTA en vez de reservar). Ruta `/ws/logs` y
`@EnableWebSocket` byte-a-byte. Nada más en el fichero. (El prompt del
encargo nombraba "config bean de registro vía el ancla // C7" — es ESTA
pieza aquí; los nombres "StreamingSesion/handler" y
"StreamingHandlerInterceptor" del encargo quedan sustituidos por los
CONTRACTUALES del spec: `LogSocketHandler` / `JwtHandshakeInterceptor`.)

---

## 2. La cadena completa: del COMMIT al byte en el socket

### 2.1 Encolado (hilo Tomcat)

`onLotePersistido` solo `execute()` (§1.2). El hilo de la 202 no toca BD ni
socket — R-7.a cerrado estructuralmente.

### 2.2 `distribuir` — tarea del executor (R7.17)

En orden EXACTO: (1) `ids == null || ids.isEmpty()` → return; (2)
`logsQueryRepository.porIds(ids)` (§4) — lista vacía → return; (3)
`batch_id` (D-7.4); (4) por cada sesión del snapshot
`handler.sesionesVivas()` (copia — sin iteración sobre el mapa vivo):
matchea (§2.3) → `envuelta.sendMessage(new TextMessage(frame))`; (5)
política de excepciones: **por SESIÓN** → `SessionLimitExceededException`
→ `handler.cerrarPorLentitud(...)` (1013) y si la sesión cerró → fuera del
registro (afterConnectionClosed); cualquier otra `IOException` → swallow +
log WARN línea genérica; **del LOTE** (p. ej. porIds) → swallow + log
ERROR — NO re-emite, NO reintenta (at-least-once YA garantizado por el
COMMIT del seam; sin replay contractual; la 202 siguiente jamás se afecta
— D-12 + R-7.i).

**D-7.4 — `batch_id`.** `"b_" + UUID.randomUUID().toString()` (v4, minúsculas,
36 chars con guiones — el literal `b_3f2b9c7a-…` de §3.5); opaco, distinto
por frame (NUNCA reutilizado); generado UNA vez por lote en el executor,
no por sesión.

### 2.3 Matching por sesión EN SERVIDOR (S7-10/R7.16)

**D-7.6 — Matching a nivel LOTE, frame SIEMPRE ÍNTEGRO.** La unidad de
qualificación es el LOTE (contrato: "el servidor SÓLO emite **lotes** que
matchean"; "un frame por lote"; "3 logs → aparecen como **grupo** en ● En
vivo" — métricas PRD §7). Con la sesión calificada (D-7.7), el lote
matchea si TODOS los filtros activos de la sesión pasan (conjunción):

| Filtro | Evaluación contra el lote |
|---|---|
| `project_id` | igualdad `Long` contra el `projectId` del seam (top-level) |
| `environment` / `level` | igualdad estricta case-sensitive contra los registros: **el lote matchea si ALGÚN registro cumple** (un batch puede llevar niveles/environment distintos por registro — C5 valida por-registro) |
| `from` / `to` | `!receivedAt.isBefore(from)` / `!receivedAt.isAfter(to)` (INCLUSIVE — igual que >=/<= del SQL de C6) sobre el instante RAW del común; `from == to` LEGAL (rango de punto) |
| `text` | `message.toLowerCase(Locale.ROOT).contains(texto.toLowerCase(Locale.ROOT))` — **el lote matchea si ALGÚN registro cumple**; texto CRUDO (el escape ILIKE es SOLO transporte SQL — semántica equivalente EXACTA); nunca `patronTexto` |
| (sin filtro) | no restringe — campo `null` en `Filtros` = filtro sin aplicar |

El frame que va a la sesión es el LOTE ÍNTEGRO (`List<FilaBatch>`), jamás
recortado por log (D-C7-1: over-inclusión coherente con la dedup por `id`
del cliente en C8). Alternativa DESCARTADA: recortar el frame a los logs
que matchean — contradice el literal "logs EN ORDEN DEL LOTE" + LogDto
completo del §3.5 y rompe la demo del grupo.

**D-7.7 — Sesión sin calificar.** `ATTR_FILTROS == null` ⇒ NO matchea ⇒
sin frames (S7-10). `set_filters` VÁLIDO aplica inmediatamente para los
SIGUIENTES lotes y el servidor NO responde nada (R7.11: JAMÁS un JSON
distinto de `batch`); INVÁLIDO conserva los vigentes (nada se muta — R-7.g
cerrado con tolerancia).

### 2.4 Frame byte-exacto (R7.14/R7.15 — contratos §3.5)

**D-7.3 — LinkedHashMap sobre record.** El top-level se construye como
`LinkedHashMap<String,Object>` con EXACTAMENTE 5 claves EN ESTE ORDEN
(elegido sobre un record de DTO: cero fichero nuevo y orden literal verbatim
garantizado — Jackson serializa LinkedHashMap en orden de inserción):

```java
LinkedHashMap<String, Object> frame = new LinkedHashMap<>();
frame.put("type", "batch");
frame.put("batch_id", batchId);                                    // "b_" + UUID v4
frame.put("project_id", projectId);                                // Long del seam
frame.put("received_at", receivedAt.format(LoginController.EXPIRES_AT_FORMATTER));
frame.put("logs", filas.stream().map(FilaBatch::log).toList());    // LogDto × N
String payload = objectMapper.writeValueAsString(frame);           // bean Boot (D-3)
```

- `received_at` top-level (D-7.5/D-C7-2): render del común del seam con el
  formatter C2 REUTILIZADO por referencia (`LoginController.EXPIRES_AT_FORMATTER`,
  truncado a segundos) — la precisión completa NO viaja en el frame; el
  matching from/to del servidor usa el instante RAW interno (§2.3/§4).
- `logs`: el LogDto COMPLETO de 11 campos (`id, project_id, level,`
  `environment, timestamp, received_at, message, exception, metadata,`
  `source, correlation_id`), claves snake_case, nulls INCLUIDOS (la suite
  no configura nada de Jackson — verificado en C6), EN EL ORDEN DEL LOTE
  (reorden de porIds, §4.1); el `project_id` de cada elemento SÍ viaja
  (D-C7-1 prevalece el literal). Ingesta individual = batch de 1 (literal
  §3.5 — el seam ya llama con ids de 1).
- Se emite como UN `TextMessage` único: JAMÁS fragmentado, JAMÁS varios
  frames por lote, JAMÁS un JSON del servidor distinto de `batch`.

### 2.5 `sendMessage` y doble barrera (R7.22/R7.23)

Toda sesión registrada está envuelta en
`ConcurrentWebSocketSessionDecorator(sesion, sendTimeLimitMs,`
`bufferSizeBytes)` con los valores de config (§1.I); vencido CUALQUIERA
(send 2000 ms / buffer 524288 = 512 KiB en prod) el envío lanza
`SessionLimitExceededException` → `handler.cerrarPorLentitud` → close 1013
(`CloseStatus.TRY_AGAIN_LATER`) + limpieza vía `afterConnectionClosed`.
Las OTRAS sesiones siguen recibiendo y la 202 sigue respondiendo (el
síncrono fue descartado — D-7.2). El `sendMessage` de los pings también va
por el decorador (misma barrera, mensajes de 2 bytes — irrelevante en
práctica).

---

## 3. Ciclo de sesión

### 3.1 Handshake (D-7.9 arriba + §1.3) — 401/403 SIN cuerpo

Resumen normativo: Origin PRIMERO (propiedad vacía → 403 a TODO; Origin ≠
propiedad → 403; ausente → continúa), token LUEGO (ausente/vacío/verify
empty → 401). El proxy nginx reenvía la línea de status SIN reescribirla —
E7.9 lo pina por pila. `--solo-handshake` SIEMPRE con `--token <JWT>`
(S7-7: sin token ahora responde 401). **Requisito operativo**: la pila de
smoke necesita `.env` con `FRONTEND_ORIGIN` — ver R-7.j (§8).

### 3.2 `set_filters` — shape exacto (R7.10/R7.11 — D-7.8)

`handleTextMessage`: `objectMapper.readTree(payload)` (bean Boot):

- `root` debe ser objeto con `size == 2`, `root.get("type")` textual
  EXACTO `"set_filters"` y `root.get("filters")` objeto — cualquier desvío
  (JSON malformado, otro `type`, `size` distinto, `filters` no-objeto) →
  no-contrato (§3.3).
- `filters`: **TODA su clave DEBE pertenecer al conjunto cerrado**
  `{project_id, environment, level, from, to, text}` — cualquier clave
  extra (JAMÁS `cursor`/`limit`: no hay paginación en el stream) →
  set_filters INVÁLIDO (conserva vigentes). Clave AUSENTE o valor `null` =
  filtro sin aplicar (el ejemplo §3.5 lleva `"to": null`); PRESENTE no-null
  debe ser String JSON — salvo `project_id`, que admite TAMBIÉN número JSON
  (`"project_id": 7` del ejemplo — normalizado a String con
  `String.valueOf(node.asText())`/`asLongText` antes del parser). Booleano,
  array u objeto por clave → INVÁLIDO.
- Los 6 valores van a `FiltrosConsulta.parsear(projectId, environment,`
  `level, from, to, text)` (D-7.12 — ÚNICA fuente ADR-7; null = filtro
  ausente; el String vacío PRESENTE en environment/level/from/to es
  INVÁLIDO — patrón C6 S6-2; `text=""` es LEGAL, patrón `%%`):
  - `FiltroInvalido` → NO aplica el cambio (la sesión CONSERVA sus filtros
    vigentes — equivalencia del "400 → conserva la última lista válida"
    del AppFlow flujo 4), NO cierra la sesión, log WARN sin contenido.
  - OK → `attributes.put(ATTR_FILTROS, filtros)` bajo lock (recalificación
    inmediato) y RETURN — el servidor JAMÁS contesta nada (no hay
    `ack` contratado: "ningún mensaje del servidor distinto de batch está
    previsto en v1").

**D-7.13 — texto crudo vs patrón.** El matching del stream usa
`Filtros.texto` (crudo); `patronTexto` (el escape ILIKE) lo genera el
parser para `GET /logs` pero el stream NO lo toca — la aguja `_` →
`patronTexto` lleva `\_` y el crudo NO (pin E7.20).

### 3.3 mensajes no-contrato (R7.12 — D-7.8 cierre)

CUALQUIER texto que no sea el shape exacto (incl. `{"type":"ping"}`, JSON
malformado, texto plano, keys extra) y CUALQUIER frame BINARIO (override
de `handleBinaryMessage`): se IGNORA sin cerrar, con log a DEBUG SIN el
contenido del mensaje. La sesión SOLO cierra por: 1013 (§2.5), latido
vencido (§1.1/D-7.10) o close del cliente. Los pings/pongs RFC 0x9/0xA son
frames de CONTROL del contenedor — no pasan por `handleTextMessage`.

### 3.4 Limpieza (R7.13/R-7.c)

`afterConnectionClosed` (cualquier código) → `registradas.remove(sesionId)`
— ÚNICO punto de remove (el GC del latido cierra con close y CONFÍA en el
afterConnectionClosed; el 1013 idem). Sin leak del mapa: el cliente que
muere sin close lo recoge el latido a 75 s (1001) → afterConnectionClosed.

---

## 4. El cambio DELIVERY en `query/` (los 3 existentes que toca C7)

### 4.1 `query/repository/LogsQueryRepository.java` — AÑADIR (R7.24–R7.26)

Constant SQL VERBATIM del spec (package-private `static final String` —
pin E7.19, patrón `LogsRepoSqlTest`, 1 placeholder, SIN ORDER BY):

```java
/** (C7) §1.H-1: las MISMA 11 columnas de SELECT_POR_ID en ESE orden.
 *  SIN ORDER BY: el orden del frame lo impone la lista de entrada (Java). */
static final String SELECT_POR_IDS =
        "SELECT id, project_id, level, environment, timestamp, received_at,"
                + " message, exception, metadata, source, correlation_id"
                + " FROM logs WHERE id = ANY (?)";
```

Firmas EXACTAS (verbatim del spec):

```java
@Transactional // defensa documentada mono-sentencia — patrón C6
public List<FilaBatch> porIds(List<Long> ids);

public record FilaBatch(LogDto log, OffsetDateTime received_at) {}
```

**D-7.11 — Implementación del `porIds`.**

- Binding del array: `jdbcTemplate.query(SELECT_POR_IDS, psSetter,`
  `FILA_BATCH)` con `PreparedStatementSetter` que hace
  `ps.setArray(1, ps.getConnection().createArrayOf("bigint",`
  `ids.toArray(new Long[0])))` (driver PG17; UNA select por lote — P-8-a);
  guard defensivo: `ids` null/vacía → lista vacía sin tocar la BD.
- **Mapper**: NO se duplica la lambda de `FILA_DETALLE` ni se extrae a
  fichero (cierre de P-8: reuso in-situ) — se añade una RowMapper anidada
  delegante que ROBA el instante RAW de la MISMA fila:

```java
private static final RowMapper<FilaBatch> FILA_BATCH =
        (rs, numeroFila) -> new FilaBatch(
                FILA_DETALLE.mapRow(rs, numeroFila),
                rs.getObject("received_at", OffsetDateTime.class));
```

(same-row re-read de `received_at`: legal — el mapping por columna de
`FILA_DETALLE` queda byte-idéntico y el RAW alimenta el matching §2.3).
- **Reorden en Java**: `LinkedHashMap<Long, FilaBatch>` por `log.id()` y
  salida iterando `ids` EN ORDEN DE ENTRADA (la MONEDA del frame —
  RETURNING del INSERT de C5); faltantes IGNORADOS sin error (`get` null →
  skip). El SELECT NO ordena; nada de `array_position` (descartada por el
  spec R7.24: "porIds REORDENA en Java").
- `SELECT_LISTADO`/`SELECT_POR_ID`/`listar`/`porId`/`FilaConsulta`/
  `FILA_LISTADO` byte-a-byte.

### 4.2 `query/FiltrosConsulta.java` — AÑADIR (R7.27 — firma EXACTA)

```java
public static Filtros parsear(String projectId, String environment,
        String level, String from, String to, String text);   // throws FiltroInvalido (runtime)

public record Filtros(Long projectId, String environment, String level,
        OffsetDateTime from, OffsetDateTime to, String texto,
        String patronTexto) {}

public static final class FiltroInvalido extends RuntimeException {
    public final String code; public final String message;
    public final String field;   // null en filter_from_to_inverted
}
```

**D-7.12 — Mudanza de constants y checks.** Los checks 2–7 del controller
(seen in HEAD: project_id Long.parse → environment enum → level enum →
from parse → to parse → from>to sobre Instants, primer-fallo-gana) MUDAN
a `parsear` con los LITERALES byte-idénticos de la tabla R6.22 de C6;
`CODE_FILTER_INVALID_VALUE`, `CODE_FILTER_FROM_TO_INVERTED` y sus
`MSG_*` (5 messages) se MUDAN a `FiltrosConsulta` como
`static final` package-private con NOMBRES IGUALES (única fuente). El
`text`: != null → `texto = text` y `patronTexto = patronIlike(text)`
(byte-a-byte); null → ambos null (el controller y el stream reciben
`Filtros` con texto null = filtro ausente). `patronIlike` y su javadoc
BYTE-A-BYTE intactos (R7.3.4).

### 4.3 `query/controller/LogsController.java` — DELEGA (R7.28 — E7.18)

Cambio MÍNIMO comportamiento-preservado:

- **Conserva**: check 1 (limit) y check 8 (cursor) EN SUS POSICIONES; las
  constants `CODE_FILTER_LIMIT_INVALID`/`CODE_FILTER_CURSOR_INVALID`/
  `CODE_NOT_FOUND` y sus `MSG_*` (no aplican al stream); el helper
  `error(...)` byte-a-byte; el código de listado/detalle tras los checks.
- **Sustituye**: el bloque de checks 2–7 por:

```java
final FiltrosConsulta.Filtros filtros;
try {
    filtros = FiltrosConsulta.parsear(projectId, environment, level,
            from, to, text);
} catch (FiltrosConsulta.FiltroInvalido ex) {
    return error(HttpStatus.BAD_REQUEST, ex.code, ex.message, ex.field);
    // el helper ya NO emite field cuando campo[0] == null (código actual —
    // filter_from_to_inverted sin field queda byte-compat)
}
```

- **Orden resultante byte-compat**: limit → parsear (2..7) → cursor. El
  resto del método consume `filtros.projectId()/.environment()/.level()`
  `/.from()/.to()/.patronTexto()` (`patron = text == null ? null :`
  `...` desaparece — `patronTexto` YA viene null-si-ausente). Los mensajes
  de error resultantes son IDÉNTICOS (constants mudadas, literales iguales)
  ⇒ los E6.x del controller quedan verdes SIN editar un aserto (E7.18).
- Import nuevo: `com.projectlog.query.FiltrosConsulta.Filtros/FiltroInvalido`.

---

## 5. `EchoSocketHandler`: eliminación exacta y qué queda (P-3/S7-7)

- **REMOVED**: `backend/src/main/java/com/projectlog/streaming/EchoSocketHandler.java`
  se BORRA (sin registro en ruta queda código muerto; los congelados no
  conservan muertos). `streaming/package-info.java` INTOCABLE;
  `scripts/ws_echo_test.py` byte-a-byte (R7.4) — su `--token` ya funciona.
- **WebSocketConfig**: el swap de §1.4 (las 2 líneas).
- **"Verde con el handler real" (S7-7 desambiguación)**: 101+Accept por
  nginx + ciclo `set_filters`→`batch` del smoke C7 — no eco.

### 5.1 `scripts/qa_c2_smoke.sh` — ajuste de Q20 (D-7.14)

Q20 ("WS Echo de C1 completo (idle 75 s)") pasa de
`python3 scripts/ws_echo_test.py` (eco completo — muere con el handler
real) a `python3 scripts/ws_echo_test.py --solo-handshake --token <JWT>`:

```bash
  # (D-7.14) el eco completo MUERE con el handler real de C7 (S7-7): lo que
  # queda permanente del artefacto C1 es --solo-handshake (101+Accept+timeouts
  # nginx), SIEMPRE con --token (sin token el handshake responde 401 — S7-3).
```

Guardias añadidos (patrón canon, return 0): `CON_ENV != 1` → BLOQUEADO
(ya existe); `! -s "$F_TOKEN"` → BLOQUEADO "sin token de Q8"; **`CON_ORIGIN`
`!= 1` → BLOQUEADO "sin FRONTEND_ORIGIN el handshake C7 cierra (S7-12)"**
(en HEAD qa_c2 ya levanta CON_ORIGIN en la carga de `.env` — solo falta el
guard). El comentario del case y el texto del mensaje se ajustan al nuevo
modo; nada más del fichero.

### 5.2 `scripts/qa_c3_smoke.sh` — verificado: SIN invocación (D-7.15)

Grep de HEAD: `qa_c3_smoke.sh` NO invoca `ws_echo_test.py` — su única
referencia es la lista INTOCABLES del E3.24 (`git diff --exit-code`), que
incluye `scripts/qa_c2_smoke.sh`, `scripts/ws_echo_test.py`,
`application.yml`, `nginx.conf`, `compose.yaml`, `docs/mockups`. Tras el
COMMIT de C7 esa verificación sigue VERDE (los ficheros quedan como quedan
en HEAD del corte). Conclusión: la edición declarada en R7.3.7 para qa_c3
no tiene punto de anclaje real — el corte NO lo edita (dif-0 efectivo).
Si tasks detecta cualquier invocación al eco (re-grep obligatorio), aplica
el patrón D-7.14. NOTA OPERATIVA para la re-corrida: la invocación
`--solo-handshake` de D-7.14 exige pila con `FRONTEND_ORIGIN` (R-7.j).

### 5.3 `scripts/qa_c7_smoke.sh` — NUEVO (R7.34, canon C5/C6)

Guion cronológico (salida `E7.n: PASS | FAIL | BLOQUEADO (detalle)`,
exit 0/1/2; needles sufijados `smoke-c7-<TS>-*`; JWT y key SOLO en tmpdir
700; conteos relativos; recomposición ÚNICA `podman compose up -d --build`
`--force-recreate` — NUNCA `down -v`):

| # | Paso | Detalle |
|---|---|---|
| pre | Recomposición + healthy | 3×healthy por podman; smoke-of-the-smoke (GET /me sin token → 401 invalid_token); login (JWT → tmpdir); POST /projects (id → tmpdir); regenerate (key → tmpdir); la recomposición TAMBIÉN publica `ws-console.html` (R7.30); **guard**: FRONTEND_ORIGIN ausente en `.env` → escenarios WS BLOQUEADO (nota R-7.j, exit 2 global) |
| E7.9 | Handshake por nginx | sin token → línea de status HTTP **401** (el proxy NO la reescribe); `Origin: http://evil.example` con token → **403**; sin Origin con token → **101** + Sec-WebSocket-Accept correcto |
| E7.17 | Solo-handshake permanente | `scripts/ws_echo_test.py --solo-handshake --token <JWT>` tal cual (artefacto C1 SIN edición) → 101 + Accept + timeouts 3600 s del proxy |
| E7.1 | Ingesta → frame por nginx | heredoc Python RFC 6455 (inline en el script): handshake `ws://<host>:8080/ws/logs?token=<JWT real>` → 101 + Accept; envía `set_filters` EXACTO (project_id del proyecto smoke) → `POST /api/v1/ingest` real con la key (needle `smoke-c7-<TS>-a`) → recibe EXACTAMENTE 1 frame `batch`: shape §3.5, 11 campos, `received_at` == truncado-a-segundos del psql de la fila (D-C7-2), `logs.0.message == needle` |
| E7.5 | Batch de 3 → 1 frame | `POST /api/v1/ingest/batch` (needles a/b/c) → EXACTAMENTE 1 frame con 3 logs EN ORDEN (ids CRECIENTES == orden del RETURNING, verificado por psql) + `batch_id` ≠ ninguno previo |
| E7.6 | set_filters recalificado | cliente vivo: `level=ERROR` → ingesta needle WARN → NO llega (psql confirma la fila); recalifica `level=WARN` → siguiente lote WARN SÍ llega; recalifica a otro project_id → nada |
| E7.15 | Archivado deja de emitir | `PATCH /panel/projects/{id}` ARCHIVED → ingest → **403 `{"code":"project_archived"}`** → sin frame en ventana (y psql: sin fila nueva); desarchivar → ingest → frame SÍ llega |
| E7.10 | Ping de control 30 s | la sesión del E7.1 PERMANECE abierta ≥2 heartbeats (~65 s de guion, solapado con E7.5–E7.15 — precedente C1: 75 s): el cliente observa opcode **0x9**, responde **0xA**, intervalos ≈30 s (±5 s) y CONFIRMA que NINGÚN JSON distinto de `batch` llegó |
| E7.11 | Reconexión limpia, sin replay | cierra limpio (0x8), psql cuenta filas entradas durante la caída, NUEVO handshake + `set_filters` de nuevo → próximo lote SÍ llega; aserto de AUSENCIA: ningún frame re-emitido del gap (el fallback C6 se CITA — E6.16/E6.23) |
| E7.14 | Backpressure 1013 | cliente NO consumidor (set_filters matcheando) + consumidores sanos + ingestas repetidas de 32 KiB por mensaje (límite §2.7): (a) 202 responden ≤ ~2 s, (b) sanos siguen recibiendo, (c) si los defaults de pila (512 KiB / 2000 ms) no materializan el 1013 en guion → **PASS CON NOTA** "1013 no materializado en pila — cubierto por pareja [L] E7.13" (patrón tolerancia Q23 de C3) |

Cliente WS Python INLINE (heredoc en el propio script, estilo
`ws_echo_test.py`): socket stdlib a `ws://…/ws/logs?token=…` SIN header
`Origin` (S7-12), frames MASKED (cliente→server obligatorio RFC 6455),
parse del status HTTP del handshake + cálculo del `Sec-WebSocket-Accept`
esperado (SHA-1+base64), lector de frames con opcodes (0x1 texto / 0x9
ping → responde 0xA / 0x8 close), timeouts duros por caso. Cero
dependencias nuevas.

---

## 6. `frontend/public/ws-console.html` — NUEVO ÚNICO fichero de front (S7-1, NO contrato)

Boceto del diseño (el implementador tiene libertad de estilo dentro de
"maquetas C3/C4 llanas: HTML+CSS+JS vanilla, fondo claro, cero
dependencias externas, cero build"):

- **Cabecera**: comentario HTML inicial "CONSOLA TEMPORAL C7 — no es parte
  del panel; se reemplaza en C8" + banner VISIBLE con el mismo literal
  (R7.32) al tope de la página. Título simple; URL servida
  `http://<host>:8080/ws-console.html` (Vite copia `public/` verbatim;
  nginx lo sirve; el SPA NO se toca ni importa la consola).
- **Sección login (§3.2)**: username/password → `fetch POST
  /api/v1/panel/auth/login` → guarda `{token, username, expires_at}` EN
  MEMORIA (variable JS — JAMÁS localStorage); muestra username+expires_at.
- **Sección conexión**: botón Conectar → `new WebSocket((location.protocol===`
  `'https:'?'wss':'ws') + '://' + location.host + '/ws/logs?token=' +`
  `encodeURIComponent(token))`; botón Desconectar; **reconexión MANUAL por
  botón** (sin backoff — es utilidad); badge de estado Conectado/
  Desconectado; log de eventos con `event.code` en close, error visible.
- **Sección filtros (§3.3)**: `project_id` (input number — null si vacío),
  `environment` select (`development/staging/production` + vacío=null),
  `level` select (`TRACE..FATAL` + vacío=null), `from`/`to` inputs text
  ISO-8601 con zona (placeholder `2026-10-25T10:00:00Z` — el navegador no
  normaliza), `text` input. Botón "Aplicar set_filters" construye el JSON
  EXACTO de §3.5 con las 6 claves SIEMPRE presentes (null por defecto) y
  `project_id` como número cuando el input parsea:
  `{"type":"set_filters","filters":{...}}`.
- **Sección frames**: lista de frames `batch` recibidos con JSON pretty
  (últimos 50 conservados, `slice(-50)`), contador total, timestamps del
  lado cliente, campo `batch_id` resaltado; eventos open/close/error
  intercalados en un feed de eventos.
- NO entra al smoke como dependencia (el smoke usa el cliente Python);
  NO añade nada al build del SPA (ni una línea de `frontend/src`).

El corte la ENSEÑA al cerrar (verify/archive cita la URL + captura del
ciclo set_filters→batch).

---

## 7. Estrategia de tests [L] (R7.33) y parejas [S] (§2.5 del spec)

Ficheros NUEVOS de test (R7.4 — lista cerrada):

| Fichero | Escenarios | Mecánica |
|---|---|---|
| `streaming/StreamingWsClientTest.java` | E7.2, E7.3, E7.4, E7.7, E7.8, E7.16 | `@SpringBootTest(webEnvironment=RANDOM_PORT)` + cliente REAL `HttpClient.newHttpClient().newWebSocketBuilder()` (header `Origin` vía `builder.header(...)`; URL con `?token=`): upgrade real, handler real, interceptor real; `LogsQueryRepository` con `@MockitoBean` (FilaBatch fabricados) y `ObjectMapper` REAL del contexto; el lote se dispara INVOCANDO el bean real `LiveBroadcaster.onLotePersistido(...)` (broadcast a mano, sin BD); JWT emitido por el `TokenService` del contexto (patrón `PanelAuthMvcTest`); token expirado con `new TokenService(<mismo secreto del contexto>, -8)` (TTL negativo — patrón `TokenServiceTest`); `frontend-origin` de test por properties. Umbrales por properties (R7.29) |
| `streaming/LatidoYBackpressureTest.java` | E7.12, E7.13 | properties encogidas (heartbeat 1 s / idle 2 s; send-time-limit 1 ms / buffer 1 byte). Sesión muda y sesión lenta = mocks de `WebSocketSession` registrados en el mapa REAL vía `handler.afterConnectionEstablished(mock)` (el cliente JDK responde pong AUTOMÁTICAMENTE — no puede simular mudez): (a) el scheduler cierra la muda con 1001 y la limpia del registry; (b) el JDK real PERMANECE tras varios ciclos; (c) reconexión de cero. 1013: mock cuyo `sendMessage` bloquea (latch) sobre el DECORADOR REAL → `SessionLimitExceededException` por sendTime y por buffer → close 1013 + limpieza + broadcast posterior intacto a los demás |
| `query/repository/PorIdsSqlPinTest.java` | E7.19 | MISMO paquete que el repo (package-private visible — patrón `LogsRepoSqlTest`): pin del literal `SELECT_POR_IDS` (11 columnas EN ESE orden, `WHERE id = ANY (?)`, 1 placeholder, SIN ORDER BY), pin de las firmas `porIds(List<Long>)`/`FilaBatch`; Mockito-JdbcTemplate con filas DESORDENADAS e ids [3,1,2] → salida [3,1,2]; faltantes IGNORADOS; el MISMO `FILA_DETALLE` sirve a ambos estamentos (pin del delegante `FILA_BATCH`) |
| `query/FiltrosConsultaParseTest.java` | E7.20 | unit puro: los 6 checks compartidos EN ORDEN (2→7), primer-fallo-gana, `code/message/field` EXACTOS (literales byte-idénticos a los E6.x); caso feliz tipado completo (`patronTexto` `%timeout%` Y `texto` crudo separado); aguja `_` (patrón lleva `\_`, crudo NO); `text=""` → `%%` + crudo ""; `to: null` → null; `patronIlike` byte-a-byte (re-corrida) |

**Regresión pura (E7.18)**: `mvn test` completo — 146 heredados VERDE + N
de C7 (≈ 25–30 métodos: cifra final en tasks/tests-written); los E6.x del
controller byte-compatibles tras extraer el parser (NINGÚN aserto editado);
los pins de C5/C6 re-corridos intocados. Ningún otro escenario es de
regresión pura: E7.2–E7.17 prueban funcionalidad NUEVA o su pareja [S].

Parejas OBLIGATORIAS (tabla §2.5 del spec): 1 E7.2↔E7.1; 2 E7.3↔E7.5;
3 E7.4↔E7.6; 4 E7.7↔E7.9; 5 E7.8↔E7.9; 6 E7.12↔E7.10/E7.11; 7
E7.13↔E7.14 (con tolerancia anotada); 8 E7.19↔E7.5; 9 E7.20↔E7.6; 10
E7.16↔E7.10 (parcial OK). Los pins puros (E7.19/E7.20) cubren su Done sin
[1:1] de pila (E7.5 los lleva).

Quién pinnea qué: `SELECT_POR_IDS` → `PorIdsSqlPinTest` (E7.19); el parser
→ `FiltrosConsultaParseTest` (E7.20); el frame → `StreamingWsClientTest`
(E7.2, shape byte-exacto + regex UUID v4); el handshake 401/403 →
`StreamingWsClientTest` (E7.7×3 / E7.8×3 — status de la
`WebSocketHandshakeException`, NUNCA 101, sin oráculo de causa); el
matching → E7.4 (disjunción por sesión) + E7.16d (inválidos); 1013 dual →
`LatidoYBackpressureTest` (send + buffer, ambos 1013); latido 30/75 →
E7.12 (props 1/2 s) + E7.10 (default 30 s en pila).

---

## 8. Manejo de riesgos R-7 — decisión de implementación

| ID | Riesgo | Decisión de implementación |
|---|---|---|
| R-7.a | Broadcast bloquea la 202 | CERRADO estructuralmente: executor monohilo (D-7.2); `onLotePersistido` solo encola; cola sin límite declarado (v1 monousuario); N sesiones lentas jamás multiplican la latencia de la 202 |
| R-7.b | Buffer sin techo ante zombi | bufferSizeLimit EXPLÍCITO 524288 + sendTimeLimit 2000 ms por env-able (R7.29); vencido → 1013 + limpieza; test E7.13 pinnea el desborde por AMBOS límites |
| R-7.c | Leak del mapa de sesiones | `afterConnectionClosed` ÚNICO punto de remove + GC del latido (75 s → 1001) + 1013 → close → afterConnectionClosed; ConcurrentHashMap; test E7.12a pinaea el remove |
| R-7.d | JWT en query param en logs | v1 ACEPTADA (docs); higiene implementada: WARN única línea genérica `"handshake WS rechazado"` sin causa/token/query (D-7.9); NINGÚN JWT en attributes; el broadcast jamás loguea filtros ni mensajes (R7.35) |
| R-7.e | Divergencia de filtros stream/listado | Parser ÚNICO extraído (D-7.12): `GET /logs` y `set_filters` llaman el MISMO `FiltrosConsulta.parsear`; pin E7.20 (semántica ADR-7) + E7.18 (controller byte-compat) |
| R-7.f | El smoke de eco de C1 deja de pasar | `--solo-handshake --token` permanente (D-7.14) en qa_c2; smoke C7 (E7.17) prueba el proxy con el handler real; desambiguación S7-7 documentada |
| R-7.g | Campos fuera de §3.3 en set_filters / metadata distinta | Shape EXACTO de 6 claves sin extras (D-7.8); inválido conserva vigentes; frame pinado con las 11 claves del LogDto (E7.2) |
| R-7.h | Frame materializa 200 LogDto grandes | UNA select por lote (D-7.11); 1013 acota el acumulado por cliente lento; matching por sesión reduce destinatarios; monousuario |
| R-7.i | Fallo del listener corta un 202 | Catch dobles (D-7.2): por-sesión swallow+WARN; del lote swallow+ERROR; `RejectedExecutionException` capturado; NADA propaga al hilo Tomcat; NO re-emite |
| **R-7.j** (NUEVO operativo, NUEVO en design) | Pila `.env` sin `FRONTEND_ORIGIN` ⇒ per S7-12/D-7.9 el handshake RECHAZA TODO (403) — los smokes C2/C7 y la consola quedan sin stream | REQUISITO OPERATIVO documentado (S26 extendido al WS es spec R7.5, no decisión propia): el despliegue de prueba DEBE definir `FRONTEND_ORIGIN` (p. ej. `http://localhost:8080`); los smokes lo guardan con CON_ORIGIN → BLOQUEADO con nota (patrón canon); NUNCA `"*"` y NUNCA default `"*"` en el código. NO es un cambio de código ni de `.env.example` |
| R-7.k (tolerancia) | E7.14 en pila con defaults (512 KiB / 2000 ms) quizá no materializa 1013 en guion | PASS CON NOTA "cubierto por pareja [L] E7.13" (patrón tolerancia Q23 de C3) — ya codificado en la fila E7.14 del guion |

Divergencias del spec NO corregidas (se IMPLEMENTAN tal como el spec las
resolvió): **D-C7-1** — el frame emite el LogDto completo de 11 campos
(incl. `project_id` por elemento) aunque el ejemplo de §3.5 muestre 10
(prevalece el literal); **D-C7-2** — fechas del frame render truncadas a
segundos con el formatter C2 (precisión completa NO viaja; matching con el
instante RAW interno).

---

## 9. Frontera de ediciones final (checklist dif-0 — R7.3/R7.4)

| Fichero | Operación | Ancla |
|---|---|---|
| `config/WebSocketConfig.java` | EDITA (2 ediciones) | handler → `LogSocketHandler` + `.addInterceptors(jwtHandshakeInterceptor)` sobre la línea ancla `// C1: sin .withSockJS() — ADR-5; sin .addInterceptors(...) — C7` (§1.4) |
| `backend/src/main/resources/application.yml` | EDITA | AÑADE bloque `projectlog.streaming` (R7.29 VERBATIM) tras el bloque `ingest:`; RETIRA de la cabecera "sin WS_HEARTBEAT_SECONDS (heartbeat es C7)" |
| `query/repository/LogsQueryRepository.java` | EDITA (solo añade) | `SELECT_POR_IDS` + `porIds` + `FilaBatch` + `FILA_BATCH` delegante (§4.1); el resto byte-a-byte |
| `query/FiltrosConsulta.java` | EDITA (solo añade + mudanza) | `parsear`/`Filtros`/`FiltroInvalido` + constants 2–7 mudadas (§4.2); `patronIlike` byte-a-byte |
| `query/controller/LogsController.java` | EDITA | checks 2–7 → delegación (§4.3); limit/cursor/not_found byte-compat |
| `streaming/EchoSocketHandler.java` | REMUEVE | — (P-3) |
| `frontend/public/ws-console.html` | NUEVO | §6 (NO contrato) |
| `streaming/LogSocketHandler.java` / `LiveBroadcaster.java` / `JwtHandshakeInterceptor.java` | NUEVO | §1 (3 ficheros) |
| `scripts/qa_c2_smoke.sh` | EDITA | Q20 → `--solo-handshake --token` + guardías (D-7.14) |
| `scripts/qa_c3_smoke.sh` | DIF-0 efectivo | sin invocación al eco en HEAD (D-7.15) |
| `scripts/qa_c7_smoke.sh` | NUEVO | §5.3 |
| tests NUEVOS (`streaming/StreamingWsClientTest`, `streaming/LatidoYBackpressureTest`, `query/repository/PorIdsSqlPinTest`, `query/FiltrosConsultaParseTest`) | NUEVO | §7 |
| **INTOCABLES** (jamás diff) | — | `pom.xml`, `V1__init.sql`, `postgres/**`, `ingest/**`, `SecurityConfig`, `CorsConfig`, `auth/**`, `keys/**`, `projects/**`, `query/{CursorCodec, package-info}`, `query/dto/**`, `streaming/package-info.java`, `frontend/nginx.conf`, `frontend/src/**` (SPA), `compose.yaml`, `.env.example`, `application-test.yml`, `scripts/ws_echo_test.py`, `qa_c5_smoke.sh`, `qa_c6_smoke.sh`, `qa_c3_qa_smoke.sh`, `qa_c4_smoke.sh`, `docs/**`, TODOS los tests C1–C6 |

**Rollback** (proposal §7): revertir los commits de C7 — los NUEVOS
desaparecen, `WebSocketConfig` vuelve a `EchoSocketHandler` con el ancla
`// C7` intacta, `application.yml` pierde el bloque `projectlog.streaming`
— y la pila queda en C6 EXACTO (seam sin beans). Sin migraciones ni deps,
nada más que gestionar.