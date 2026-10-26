---
project: Project_Log
change: C7
artifact: proposal
last_updated: 2026-10-25
status: approved
gate: >-
  APROBADO 2026-10-25 por el usuario: 9/9 puntos tal cual (P-1 consola HTML
  temporal SÍ, formato a nuestro criterio, se diseña en spec y se enseña al
  cerrar; P-2 Testcontainers NO definitivo, deuda CERRADA; P-3 eliminar
  EchoSocketHandler + --solo-handshake permanente + migración de tests;
  P-4 75 s por defecto configurable; P-5 doble barrera 512 KiB + 2000 ms via
  env; P-6 executor single-threaded; P-7 paquete streaming 3–4 ficheros con
  LiveBroadcaster; P-8 porIds en repo C6 + parser extraído a query/ única
  fuente ADR-7; P-9 ignorar mensajes no-contrato + 401 handshake sin cuerpo).
  Recaídas: regresión 146/146 + suites C7 completas en verify; consola HTML
  simple estilo maquetas C3/C4 (no contrato, se reemplaza en C8); deudas en
  archive: nginx client_max_body_size (C9), smoke docker compose (C9),
  ISO-401 (C8/C9).
---

# Proposal — C7 · Streaming WebSocket en vivo

> Fase sdd-propose. Se presenta al usuario como GATE (aprobación de los 9
> puntos de decisión de la §4) antes de pasar a spec/design/tasks/apply. Sin
> este aprobado no se escribe código de C7. Insumos: `explore.md` de C7
> (fuentes congeladas: Schema `docs/05_BACKEND_SCHEMA.md` §3.5/§3.3/§5,
> TRD `docs/02_TRD.md` §2.8/§2.9/§9/§6.2.3, AppFlow `docs/03_APPFLOW.md`
> flujos 6/6b/7, PRD `docs/01_PRD.md` §6, `07_SLICING_PLAN.md` ficha C7 y
> tabla de riesgos), frontera C5 (seam `IngestListener` tras COMMIT),
> frontera C6 (`query/` dueño de las lecturas), y el código real en HEAD
> 89b5d60 (C1 puso la infraestructura WS: `WebSocketConfig`, `EchoSocketHandler`,
> nginx `/ws/logs`, `ws_echo_test.py`, `WS_HEARTBEAT_SECONDS` en compose).
>
> Regla del corte: lo YA CONTRATUAL en los docs congelados NO se re-pregunta
> — se CITA (frame Schema §3.5 literal; heartbeat ping del servidor 30 s vía
> `WS_HEARTBEAT_SECONDS` como frame de control RFC 0x9/0xA, NO JSON;
> `set_filters` con "exactamente la misma semántica" que `GET /logs` —
> ADR-7; JWT en query param v1; dedup por `id` del cliente en C8; archivado
> deja de emitir — derivado del 403 `project_archived` pre-COMMIT). Los
> puntos de gate de la §4 son SOLO los abiertos del explore (P-1…P-9),
> refinados con Recomendación cada uno.

## 1. Alcance y objetivos

### 1.1 Problema

C5 cerró la ingesta REAL y C6 cerró la consulta paginada, pero **el flujo
central del producto — ver los logs llegar EN VIVO sin recargar — no
existe todavía**. El seam `IngestListener.onLotePersistido(long, OffsetDateTime,
List<Long>)` quedó instalado tras COMMIT en C5 **sin ningún bean suscripto**
(lista inyectada vacía — C6 lo dejó deliberado: "C7 decide"). El panel de
C8 necesita que el servidor empuje cada lote persistido como un frame
WebSocket `batch` (Schema §3.5) para la banda "● En vivo" y para el flujo de
demo de C9 ("batch de 3 en vivo sin refresco" — criterio de aceptación de
Fase 1). C7 es **el último corte de backend de Fase 1**: sin él, C8 solo
tendría el listado estático de C6.

La ficha C7 del 07 lo define literalmente: "`set_filters` por sesión (misma
semántica que `GET /logs`), frame único `batch` en orden del lote (`b_`+UUID),
emisión **tras COMMIT** (LiveBroadcaster), heartbeat 30 s → cierre 60–90 s;
archivado deja de emitir; sin replay".

### 1.2 Lo que C1 ya dejó a favor (no es verde-de-cero)

El explore lo verifica: **C7 no toca infraestructura de proxy ni de
dependencias** — la sustitución está ANUNCIADA en el propio código desde C1:

- `backend/pom.xml` YA tiene `spring-boot-starter-websocket` (deps exactas
  del spec R2, congelado a edición): **cero `mvn` nuevo, cero dependencia
  nueva** (y Testcontainers NO entra — P-2).
- `config/WebSocketConfig` (`@Configuration @EnableWebSocket`) registra el
  handler en la ruta DEFINITIVA `/ws/logs` sin SockJS (ADR-5). Su javadoc
  anuncia el cambio EXACTO ("allí solo cambia el handler →
  `LogSocketHandler` + `HandshakeInterceptor` JWT; ni el nginx ni el smoke
  cambian") y su javadoc/inline deja la ancla literal `// C7: sin
  .withSockJS() — ADR-5; sin .addInterceptors(...) — C7`.
- `streaming/EchoSocketHandler` (eco temporal) se declara en su propio
  javadoc "el ÚNICO punto de sustitución del corte C7";
  `streaming/package-info.java` fija el plano (LogSocketHandler +
  HandshakeInterceptor JWT 401 pre-upgrade + emisión tras COMMIT +
  heartbeat).
- `SecurityConfig` cadena 3 (`@Order(3)`) ya `permitAll()` sobre `/ws/logs`
  (S31, gate G2 de C2) — el auth NO va en esta cadena sino en el
  HandshakeInterceptor, ANTES del upgrade. `TokenService.verify(token)` →
  `Optional<JwtPrincipal>` es el verificador reutilizable por referencia.
- `frontend/nginx.conf` / `compose.yaml` / `.env.example`: el bloque
  `/ws/logs` (upgrade 1.1, timeouts 3600 s) es literal del TRD §6.2.3 y
  `WS_HEARTBEAT_SECONDS` ya llega al backend — los tres ficheros SIN
  cambios previstos (solo se AÑADE el mapeo en `application.yml`, que hoy
  lo reserva con comentario "heartbeat es C7").
- `scripts/ws_echo_test.py` ya trae `--token` "(solo C7) JWT en ?token=…
  del handshake" y `handshake()` rellena el query param — cliente RFC 6455
  stdlib Python reutilizable (destino en P-3).

### 1.3 En alcance

1. **Handler real** `LogSocketHandler` (`TextWebSocketHandler`) en `/ws/logs`:
   registro/deregistro de sesión, única recepción cliente→servidor
   `{"type":"set_filters","filters":{…}}` con la misma semántica del
   `GET /logs` de C6 (sin `cursor`/`limit` — no hay paginación en el stream),
   ping/pong RFC (heartbeat), limpieza total de sesión.
2. **HandshakeInterceptor JWT + Origin**: 401 pre-upgrade con token
   ausente/expirado/inválido (verificado con `TokenService.verify`) y
   validación del header `Origin` contra `projectlog.security.frontend-origin`
   (S26: falta ⇒ cerrar la conexión; NUNCA `"*"`) — ficha C7 literal.
3. **`LiveBroadcaster`** — primer bean del seam C5: implementa
   `IngestListener`, genera `batch_id` (`b_`+UUIDv4), materializa las filas
   por ids, filtra por sesión y emite UN frame JSON por batch con el
   `LogDto` completo (11 campos, serializador del Bean único de Jackson —
   D-3), SOLO tras COMMIT (el seam lo garantiza — emisión gratis por
   posición estructural).
4. **Heartbeat del servidor**: ping 0x9 cada `WS_HEARTBEAT_SECONDS`
   (defecto 30, ya env-able vía compose) sobre un scheduler propio; sin pong
   dentro del umbral del P-4 → cierre + limpieza (reconexión y backoff 1→30 s
   son del CLIENTE, C8 — C7 solo cierra).
5. **Backpressure**: envoltorio `ConcurrentWebSocketSessionDecorator` por
   sesión con límites del P-5; vencido el límite → cierre con
   `CloseStatus.TRY_AGAIN_LATER` (1013, código del contrato del corte).
6. **Lectura por ids** en `query/LogsQueryRepository` (único hogar de las
   lecturas — frontera C5 intacta) + reutilización del parseo de filtros de
   C6 (P-8) — prevente la divergencia ADR-7.
7. **Config**: bloque nuevo `projectlog.streaming` en `application.yml`
   (heartbeat-seconds ← `WS_HEARTBEAT_SECONDS`; límites del P-5 si van env).
8. **Tests [L]** con `@SpringBootTest(RANDOM_PORT)` + cliente WebSocket REAL
   `java.net.http.HttpClient.newWebSocketBuilder()` (JDK 21 stdlib, cero deps
   — P-2) + pin SQL del SELECT por ids (patrón `LogsRepoSqlTest`).
9. **Smoke [S]** `scripts/qa_c7_smoke.sh` NUEVO (patrón canon C5/C6) con la
   cadena completa ingesta→COMMIT→frame→cliente WS por nginx, + destino del
   echo test de C1 (P-3).

### 1.4 Fuera de alcance (NO-alcanza)

- **Frontend (C8)**: banda "● En vivo", dedup por `id`, reconexión con
  backoff 1→30 s, banner de filtros 400, estado conectado/reconectando —
  C7 **solo expone el servidor**; ni una línea de `frontend/` (salvo NADA —
  ni siquiera la consola de prueba toca el SPA real; P-1).
- **Replay**: literal de los docs — "lo entrado durante una caída no se
  reemite"; el fallback tras reconectar es el listado keyset de C6 + dedup
  del cliente. Sin buffer de replay, sin estado de cliente en el servidor.
- **SSE**, STOMP/SockJS (ADR-5 cerrado), push externo, alertas, métricas.
- **API key en el socket**: JAMÁS (PRD §6, Schema §5) — solo JWT del admin.
- **Token en primer frame / cookie / header**: explícitamente FUTURO (riesgo
  C7 del 06; v1 contractual = query param, mitigación documentada R-7.d).
- Archivado: no se toca código — "archivado deja de emitir" es DERIVADO
  (403 `project_archived` bloquea la ingesta ANTES de persistir ⇒ no hay
  COMMIT ⇒ no hay seam ⇒ no hay frame) y se PINA por smoke (E7.x de prueba).
- Deudas heredadas NO mueven su corte: 401 ISO-8859-1 (C8/C9), smoke
  `docker compose` canónico (C9), nginx `client_max_body_size` (C9),
  `V1__init.sql` INTOCABLE, `postgres/**` INTOCABLE.

## 2. Descripción de la solución propuesta

### 2.1 Arquitectura: pipeline de emisión (seam → grupo → frame)

```
POST /api/v1/ingest[/batch]                    (C5, intocable)
  └─ validación completa → INSERT multi-VALUES + RETURNING     [@Transactional]
  └─ COMMIT (al retornar insert) ── sentencia siguiente:
     oyentes.forEach(o -> o.onLotePersistido(projectId, receivedAt, ids))
            │
            ▼  hilo Tomcat del request (R-7.a — P-6 decide el hilo)
     LiveBroadcaster implements IngestListener              [NUEVO, streaming/]
            ├─ batch_id = "b_" + UUID v4
            ├─ query/LogsQueryRepository.porIds(ids)         [NUEVO en query/]
            │    → List<LogDto> en ORDEN del lote (los ids llegan ordenados
            │      por el RETURNING — moneda del frame; el SELECT reordena)
            ├─ para cada sesión VIVA registrada:
            │    ├─ ¿filtros de la sesión matchean el lote? (EN SERVIDOR)
            │    └─ frame JSON único {type,batch_id,project_id,received_at,logs[]}
            │         → sendMessage vía ConcurrentWebSocketSessionDecorator
            │           (límites P-5; vencido → 1013 Try Again Later)
            ▼
     HeartbeatScheduler (P-4, P-5)   ← ping 0x9 cada WS_HEARTBEAT_SECONDS;
                                        sin pong → close + limpieza del registro
```

- **Emisión por sesión en SERVIDOR**: cada sesión guarda sus filtros
  parseados en los attributes de la `WebSocketSession`; el broadcast solo
  entrega el lote a las sesiones cuyo filtro matchea (patrón del contrato:
  "filtros por sesión" — nunca filtra el cliente). Un log individual es
  batch de 1 (el seam ya lo llama así en ingesta individual).
- **Frame EXACTO del §3.5** (CONTRATO, no se rediseña): top-level
  `{type:"batch", batch_id, project_id, received_at, logs:[…]}` con `logs`
  = LogDto completo (11 campos, nulls incluidos, patrón C6) y `received_at`
  = el común del lote; serialización con el `ObjectMapper` del contexto
  Boot (D-3, nunca instancia suelta). El ejemplo del congelado muestra las
  fechas truncadas a segundos — coherente con reutilizar el LogDto ya
  serializado con el formatter vigente (divergencia documentada de C6, no
  introduce nada nuevo).
- **Thread-safety por sesión**: el decorador ya serializa los `sendMessage`
  concurrentes entre broadcast y heartbeat (explore §2.b).

### 2.2 Piezas nuevas del paquete `com.projectlog.streaming`

| Pieza (nombres propuestos — final en spec) | Papel | Origen |
|---|---|---|
| `LogSocketHandler` | `TextWebSocketHandler`: established → registrar; `handleTextMessage` → SOLO `set_filters` válido; `handlePongMessage` → feed del latido; closed → limpiar registro y timers | NUEVO (sustituye al Echo — P-3 decide su destino) |
| `LiveBroadcaster` (o integrado con el handler — P-7) | `implements ingest.IngestListener` — el PRIMER bean del seam: genera `batch_id`, consulta por ids, filtra por sesión, emite | NUEVO |
| `JwtHandshakeInterceptor` | `beforeHandshake`: `?token=` → `TokenService.verify` → 401 pre-upgrade sin upgrade ni recursos; `Origin` contra `frontend-origin` — nunca `"*"`; sin loguear token (S16) | NUEVO (hogar en P-7) |
| Registro de sesiones + heartbeat | Mapa thread-safe de sesiones activas + scheduler de pings y cierre por umbral | NUEVO (dueño en P-7) |
| Envoltorio por sesión | `ConcurrentWebSocketSessionDecorator(session, sendTimeLimit, bufferSizeLimit)` → 1013 | NUEVO (números en P-5) |
| `query/…` soporte | `LogsQueryRepository.porIds(ids)` + parseo/validación de filtros reutilizable del `set_filters` (P-8) | NUEVO en `query/` (fichero existente, frontera respetada) |
| `scripts/qa_c7_smoke.sh` + cliente WS ampliado | smoke canon + modo WS de C7 sobre pila viva | NUEVO (P-1/P-3) |

### 2.3 Frontera dif-0 — ediciones a ficheros EXISTENTES (declaradas, mínimas)

El diseño previo de C1 dejó el cambio anunciado; C7 toca lo MÍNIMO:

1. **`config/WebSocketConfig`** — las DOS ediciones previstas por el propio
   javadoc de C1: (a) el handler del registro pasa de `EchoSocketHandler`
   a `LogSocketHandler`; (b) la línea anclada `// C7: sin .withSockJS() —
   ADR-5; sin .addInterceptors(...) — C7` recibe el
   `.addInterceptors(jwtHandshakeInterceptor)`. Nada más en el fichero
   (ruta `/ws/logs` y `@EnableWebSocket` byte-a-byte).
2. **`application.yml`** — se AÑADE el bloque `projectlog.streaming`
   (heartbeat-seconds ← `WS_HEARTBEAT_SECONDS`; límites de P-5 si van env).
   Se retira el comentario reservado "sin WS_HEARTBEAT_SECONDS (heartbeat
   es C7)".
3. **`query/LogsQueryRepository`** — AÑADIR el método de lectura por ids
   (P-8); extraer el parseo de filtros de `LogsController` a lógica de
   paquete REUTILIZABLE (refactor comportamiento-preservado: `GET /logs`
   y su smoke de C6 deben quedar byte-compatibles — E7.x de regresión).
4. **`streaming/EchoSocketHandler`** — destino en P-3 (borrar o desregistrar).
5. **`scripts/ws_echo_test.py`** — destino del modo eco en P-3 (el
   `--solo-handshake` SE QUEDA como prueba permanente del proxy).

**NO tocan NADA**: `ingest/**` (el seam ya queda activo al existir el bean —
la lista inyectada entrega el suscriptor solo), `SecurityConfig` (la cadena
3 permitAll ya cubre; el auth vive en el interceptor — solo se REVISARÁ el
matiz del cuerpo del 401 propio del handshake contra el entry point
compartido del REST, P-9), `nginx`, `compose.yaml`, `.env.example`,
`postgres/**`, `V1__init.sql`, `backend/pom.xml` (CERO dependencias nuevas —
P-2), `frontend/**`, test de C1–C6 (re-corrida 146/146 sigue verde — los
smokes `qa_c2/c3` que invocan el eco quedan en el plan del P-3).

### 2.4 Estrategia de tests (donde cada cosa vive)

| Escenario | Dónde | Patrón |
|---|---|---|
| Handshake 401 pre-upgrade (sin token / expirado / inválido) + Origin no permitida | [L] `@SpringBootTest` RANDOM_PORT + `newWebSocketBuilder()` del JDK | Tomcat REAL + upgrade REAL; connectBlocking falla con HttpResponse 401 |
| `set_filters` válido/inválido; mensajes no-contrato (P-9); frame `batch` ordenado por sesión; batch de 1 | [L] ídem; el lote se dispara llamando al seam vía bean test o fake listener (sin BD) | broadcast simulado a mano |
| Backpressure 1013 y cierre por latido | [L] ídem; umbrales encogidos por property de test | decorador real |
| SQL por ids + RowMapper del frame (order por `array_position`/reorden en Java) | [L] pin del SQL literal — patrón `LogsRepoSqlTest` de C5 | sin BD |
| Regresión GET /logs tras extraer el parseo (P-8) | [L] re-corrida de los E6.x del controller | 146/146 heredados verdes |
| E2E: ingesta real → COMMIT → frame por nginx con JWT real; `set_filters` recalificado tras reconectar; archivado no emite; heartbeat 30 s; 1013 | [S] `qa_c7_smoke.sh` sobre pila viva 3×healthy (patrón canon C5/C6: recomposición `--build --force-recreate`, NUNCA `down -v`, conteos relativos, needles sufijados, tmpdir 700, exit 0/1/2) | E7.x [S] |

La deuda de Testcontainers (P-2) vive en esta tabla EXACTA: cada hueco que
TC quería cerrar tiene hogar con grado de realidad igual o mayor y CERO
dependencias nuevas.

## 3. Alternativas consideradas y descartadas

| Alternativa | Veredicto | Motivo (ancla) |
|---|---|---|
| **SSE (Server-Sent Events)** en vez de WS | DESCARTADA — por docs | El congelado fija WS nativo de Spring en `/ws/logs` (ADR-5, TRD §9); el coordinador lo hereda cerrado. Además los pings 0x9/0xA y la reconexión del AppFlow flujo 6 asumen WS |
| **Broadcast síncrono sin executor** (P-6 opción a) | VIABLE pero recomendado NO puro | El `onLotePersistido` corre en el HILO TOMCAT de la 202: un cliente lento bloquearía la ingesta si no hay sendTimeLimit; la variante recomendada (b) — o a con límites acotados — se decide en P-6 |
| **Executor propio de broadcast** (P-6 opción b) | RECOMENDADA (ver P-6) | Desacopla la 202 de todo envío; una cola monohilo preserva el orden de lotes; coste: 1 pieza más |
| **Replay de lo entrado durante caída** | DESCARTADA — por docs | Literal: "lo entrado durante una caída no se reemite"; el fallback es C6 (listado keyset + dedup cliente del flujo 6b). El servidor no guarda estado de cliente |
| **Varios handlers/rutas (`/ws/logs` + eco temporal)** | DESCARTADA | La ruta `/ws/logs` es ÚNICA (TRD §9) — no caben 2 handlers; el destino del Echo es P-3 |
| **Filtrado en el cliente** | DESCARTADA — por doc/contrato | "filtros por sesión" aplicados EN SERVIDOR (contrato del corte); además evita empujar TODO a todos tras cualquier set_filters |
| **STOMP/SockJS** | DESCARTADA — ADR-5 | WS nativo sin subprotocolos; C1 ya lo fijó en el registro |
| **Testcontainers en `mvn test`** (P-2 opción adoptar) | NO recomendada (ver P-2) | 2 deps nuevas en pom congelado-a-edición, docker-en-builder NO demostrado (R-nuevo-6), y el hueco se cierra igual con `java.net.http` WS client + Tomcat RANDOM_PORT + smoke de pila viva |
| **N `porId(id)` en vez de lectura por ids** (P-8 opción 2) | Descartada como principal | Hasta 200 round-trips al PG por lote justo en el hilo caliente de la ingesta; la SELECT `IN/ANY` única preserva el orden y respeta la frontera |

## 4. Decisiones para el GATE — 9 puntos (P-1…P-9)

> Lo contractual se CITA, no se pregunta (ver cabecera). Cada punto lleva
> Contexto / Opciones / **Recomendación** / Impacto en fichaje o esfuerzo.

### P-1 (DE GATE) — Consola de prueba temporal: sí/no, dónde vive y formato

- **Contexto.** Para probar el stream antes de que el panel real exista
  (C8), algo tiene que "mirar" el socket. El corte heredó de C1 un cliente
  RFC 6455 Python stdlib (`scripts/ws_echo_test.py`) con `--token` ya
  preparado, y el 07 exige el Done "echo test del proxy WS de C1 sigue
  verde con el handler real".
- **Opciones.** (a) Página estática mínima servida por nginx
  (`frontend/public` — el SPA real llega en C8; no tocaría ninguno de sus
  ficheros hoy, solo AÑADE un html de prueba) con JS nativo
  `new WebSocket(…)`; (b) script Python del cliente RFC extendido a
  consumir frames (como el resto del canon de smokes del repo); (c) nada —
  solo el smoke.
- **Recomendación.** (b) como prueba AUTOMATIZABLE del corte (cliente WS
  Python ampliado dentro del estilo del canon — extender `ws_echo_test.py`
  o cliente nuevo a decidir en P-3) y (a) como consola VISUAL mínima,
  opcional, en `frontend/public` (se sirve por nginx sin entrar ni una vez
  al SPA de C8; se retira o se queda como utilidad — el spec decide). La
  consola es temporal por contrato; NO es parte del panel.
- **Impacto.** (a) ~1 fichero `.html` trivial + 0 de backend; (b) horas
  dentro del smoke ya previsto. Sin ninguna de las dos el Done de C9
  ("batch en vivo sin refresco") quedaría sin demo manual posible hasta C8.

### P-2 (DE GATE) — Testcontainers: ratificar NO y cerrar la deuda C5/C6

- **Contexto.** C5/C6 dejaron la deuda "reevaluar en C7" — el corte donde
  el socket real más apetece. Es la ÚLTIMA reevaluación pactada: aquí se
  cierra, en un sentido u otro.
- **Opciones.** (a) NO-TC + cliente WS `java.net.http` sobre
  `@SpringBootTest(RANDOM_PORT)` (Tomcat real, upgrade real, handler real,
  repo mockeado) + smoke [S] para la cadena completa con BD real;
  (b) adoptar TC solo en C7 (WS real + PG real en `mvn test`).
- **Recomendación.** (a) — ratificar NO y DEPORTAR la deuda como CERRADA.
  El cliente stdlib JDK 21 da el mismo grado de realidad para TODA la parte
  WS (401 pre-upgrade, set_filters, frame, 1013, latido) con CERO
  dependencias nuevas (pom congelado, RG-1); TC solo añadiría PG real bajo
  `mvn`, que el patrón canon ya prueba en el smoke sobre pila viva — y
  exigiría demostrar docker-in-build en el builder `maven:3.9-…` (R-nuevo-6:
  no demostrado en esta VM). El cierre se documenta en el archive de C7.
- **Impacto.** (a) 0 deps, 0 pom; (b) 2 deps test-nuevas + probe de
  docker-in-mvn + contexto Spring más pesado. La suite [L] gana los tests
  de socket real en AMBOS caminos; (a) los obtiene gratis.

### P-3 (DE GATE) — Destino de `EchoSocketHandler` y del echo test de C1

- **Contexto.** La ruta `/ws/logs` es ÚNICA: no caben Echo y handler real a
  la vez. El package-info llama al Echo "temporal de prueba"; los smokes
  de C2/C3 dependen del modo COMPLETO (eco), que MORIRÁ con el handler real
  (un texto genérico no es `set_filters`). El Done del 07 exige el proxy
  "verde con el handler real" — hay que desambiguar qué es verde.
- **Opciones.** (a) ELIMINAR el fichero del Echo y dejar el `--solo-handshake`
  de `ws_echo_test.py` como prueba PERMANENTE del proxy (upgrade + 101 +
  Accept + timeouts nginx), moviendo la prueba del protocolo de C7 al smoke
  nuevo (`qa_c7_smoke.sh`) — el modo eco desaparece de la suite C2/C3 o se
  reducen esos invocadores al `--solo-handshake`; (b) conservar el fichero
  sin registrar en ruta; (c) tocar `ws_echo_test.py` vs cliente NUEVO para C7.
- **Recomendación.** (a) con estas tres piezas juntas: el Echo se ELIMINA
  (su sustitución estaba anunciada en su propio javadoc; sin registro en
  ruta queda código muerto y los congelados no conservan muertos),
  `--solo-handshake` pasa a ser LA prueba permanente del proxy en
  `qa_c2_smoke.sh`/`qa_c3_smoke.sh` y la ECO se sustituye en el smoke de C7
  por la prueba del handshake autenticado + ciclo `set_filters`→`batch`
  (eso es lo "verde" con el handler real: el PROXY sigue igual y el
  protocolo se comprueba en su corte). Si tocar `ws_echo_test.py` (artefacto
  C1) incomoda, el spec decide entre extenderlo (reusa su handshake RFC —
  recomendado) o un cliente NUEVO bajo el mismo estilo; el humo no cambia.
- **Impacto.** (a) −1 fichero backend, 2 smokes heredados ajustados a su
  flag permanente, 1 smoke nuevo; riesgo de regresión silenciosa del proxy
  queda cubierto por R-7.f (el `--solo-handshake` corre en cada smoke de
  recomposición).

### P-4 (DE GATE) — Umbral EXACTO del cierre por latido (60–90 s)

- **Contexto.** Los docs dan un RANGO ("sin respuesta en 60–90 s → cierre
  y limpieza"); no pinan el número. El heartbeat son pings 0x9 cada
  `WS_HEARTBEAT_SECONDS` (30 por defecto, ya env). La reconexión es del
  cliente (C8) — C7 solo cierra; el valor afecta cuánto tarda el banner
  "reconectando" del panel en aparecer ante un backend zombi.
- **Opciones.** (a) fijo 60 s (2 latidos perdidos — cierra antes, el
  cliente reconecta antes); (b) fijo 90 s; (c) configurable
  `projectlog.streaming.idle-timeout-seconds` (env-able) con defecto 75
  (punto medio del rango — el mismo 75 s que C1 ya probó como inactividad
  contra los 3600 s del nginx); (d) defecto 60 sin configurar.
- **Recomendación.** (c) — un valor del rango contractual, env-able por
  coherencia con `WS_HEARTBEAT_SECONDS` (que ya es env), default **75 s**:
  tolera un ping perdido completo y parte de un segundo (no cierra por un
  único descarte de red), y sigue bajo el margen del rango de los docs.
  El spec ancla el default y la propiedad; el smoke prueba el cierre con
  el umbral encogido a segundos vía property (patrón de P-5).
- **Impacto.** (c) 1 propiedad más en el bloque `projectlog.streaming`
  (P-5 define el bloque); coste equivalente en las tres opciones.

### P-5 (DE GATE) — Política 1013: valores, simultaneidad y fuente (env vs constantes)

- **Contexto.** El contrato fija "buffer límite + cerrar sesiones lentas con
  código 1013". El congelado NO fija N de buffer ni tiempo de envío.
  `ConcurrentWebSocketSessionDecorator(session, sendTimeLimit,
  bufferSizeLimit)` exige DOS números (ms y bytes) y cierra con
  `SessionLimitExceededException` → `CloseStatus.TRY_AGAIN_LATER` (1013).
- **Opciones.** (a) solo bufferSizeLimit (envío bloqueante esperando drenaje
  — puede atar el broadcast); (b) solo sendTimeLimit; (c) AMBOS
  simultáneos (decorador estándar de Spring); (d) fuente: env en
  `projectlog.streaming.*` vs constantes del paquete.
- **Recomendación.** (c) ambos + (d) env en `projectlog.streaming` con
  defaults razonables: `buffer-size-bytes` = 512 KiB (≈ margen para varias
  páginas de `LogDto` grandes con exception de 128 KiB, sin techo de
  memoria para un cliente zombi) y `send-time-limit` = 2000 ms (un write
  sano al socket local responde en ms — 2 s ya es lentitud real). El 1013
  aplica a AMBOS vencimientos (es el ÚNICO código de cierre por lentitud
  del contrato). El smoke [L] prueba el cierre con límites encogidos vía
  properties de test (números de prod verdes en humo, números de test
  rápidos en CI).
- **Impacto.** (c)+(d) son el diseño natural del decorador (cero piezas
  extra); 3 props nuevas junto a las de P-4.

### P-6 (DE GATE) — Hilo de emisión: ¿bloqueo acotado en el hilo Tomcat o executor de broadcast?

- **Contexto.** `onLotePersistido` llega en el hilo Tomcat del request de
  ingesta: si el broadcast SÍNCRONO bloquea una sesión lenta, se bloquea la
  202 de la ingesta (R-7.a). El contrato fija buffer+1013, pero la
  estrategia de hilo es del spec.
- **Opciones.** (a) síncrono con el decorador (`sendTimeLimit` acota el
  bloqueo a ms por sesión — simple, cero piezas); (b) executor de
  broadcast de UN solo hilo con cola interna (el hilo Tomcat solo encola y
  retorna; el orden de lotes se preserva por monohilo).
- **Recomendación.** (b) — el executor de un solo hilo: la 202 de una
  ingesta NUNCA queda a merced del peor cliente (el sendTimeLimit acota,
  pero con N sesiones lentas el síncrono suma N×límite en el peor caso y
  multiplica la latencia de TODA ingesta); la cola monohilo preserva
  trivialmente el orden de emisión de lotes (contrato: orden del lote) y
  el shutdown se ancla al ciclo del bean. El síncrono (a) queda como
  fallback documentado si el spec ve sobre-ingeniería — decisión final en
  spec con el riesgo R-7.a como criterio.
- **Impacto.** (b) +1 pieza (executor + lifecycle), ~mitad de hora extra;
  (a) cero piezas. Ambas compatibles con el mismo handler/broadcast.

### P-7 (de paquete, con cierre para spec) — Forma del paquete `streaming`: quién registra el bean, dónde vive el interceptor

- **Contexto.** TRD §2.9/Schema §3.5 nombran el `LiveBroadcaster` ("el
  `LiveBroadcaster` publica lotes ya persistidos") — el bean que implementa
  `IngestListener` debería llevar ese nombre. Queda abierto cuántos beans
  son y dónde vive el interceptor del handshake.
- **Opciones.** (a) 1 bean `LiveBroadcaster` que implementa
  `IngestListener` Y dueño del registro de sesiones (handler delgado
  delegando) — 2 ficheros (interceptor + broadcaster+registro);
  (b) separación completa handler (dueño del registro)/broadcaster
  (`IngestListener` puro) — 3 ficheros; (c) interceptor en `config/` junto
  a `WebSocketConfig` vs en `streaming/`.
- **Recomendación.** (b) con el nombre contractual reservado al
  broadcaster: `LiveBroadcaster` (solo `IngestListener`: generar
  `batch_id`, consultar, filtrar, emitir), `LogSocketHandler` (dueño del
  registro + heartbeat + latido + cierre 1013), `JwtHandshakeInterceptor`
  en `streaming/` (opción c: junto a su feature — inyecta `TokenService` y
  `projectlog.security.frontend-origin`; `config/WebSocketConfig` solo lo
  registrará por la ancla `// C7`). Registro de sesiones como mini-clase
  del paquete si el spec la separa del handler. package-by-feature: TODO
  bajo `com.projectlog.streaming`.
- **Impacto.** (b) 3–4 ficheros NUEVOS; (a) −1. Diferencia puramente de
  estructura — ambas emiten idéntico.

### P-8 (DE GATE) — Lectura por ids para el frame y hogar del parseo de `set_filters`

- **Contexto.** El seam solo entrega `(projectId, receivedAt, ids)`; el
  frame §3.5 lleva el LogDto completo de cada fila (hasta 200 por lote).
  Y `set_filters` exige "exactamente la misma semántica" que `GET /logs`
  (ADR-7) — pero los 8 checks de C6 viven PRIVADOS en `LogsController`:
  hoy no hay lógica de paquete reutilizable (C6 explore lo dejó anotado:
  REUTILIZAR, no replicar).
- **Opciones.** Lectura: (a) método nuevo `porIds(ids)` en
  `query/LogsQueryRepository` — una SELECT `= ANY(?)`/`IN (…)`
  reordenada en Java según el orden del `RETURNING` (los ids del seam ya
  llegan en orden de VALUES — la moneda del frame); (b) `porId(id)` × N.
  Parseo: (i) extraer la cadena de validación de filtros a lógica de
  paquete en `query/` (devuelve filtros tipados o el 400-equivalente) y
  `set_filters` la invoca; (ii) replicar el parseo en `streaming/`.
- **Recomendación.** (a) + (i): una única SELECT por lote (la variante (b)
  son hasta 200 round-trips en el hilo caliente de la ingesta) y el parser
  extraído a `query/` como ÚNICA fuente de la semántica ADR-7 (la variante
  (ii) duplicaría la regla y garantiza divergencia futura — justo lo que
  el riesgo R-7.e pinó). El refactor es comportamiento-preservado para
  `GET /logs`: la re-corrida de los E6.x la pina. El RowMapper
  `FILA_DETALLE` del detalle se reutiliza (extraído a mapper compartido del
  paquete `query` — decisión de spec, sin tocar su semántica por-columna).
- **Impacto.** +1 método en fichero EXISTENTE (+1 pin SQL, patrón
  `LogsRepoSqlTest`) y 1 extracción de lógica a clase de paquete en
  `query/` — la mitad de la pieza ya existe escrita: se MUEVE, no se
  reescribe.

### P-9 (DE GATE) — Mensajes no-contrato del cliente y cuerpo del 401 del handshake

- **Contexto.** Schema §3.5: "el cliente solo envía `set_filters`" — pero
  ¿qué hace el servidor ante un frame de texto que no lo sea (bug de
  cliente, eco del Echo histórico, sonda)? Y el 401 pre-upgrade: los docs
  pinan el CÓDIGO HTTP, no la FAMILIA ni el cuerpo (el literal
  `invalid_token` es del entry point REST del PANEL — otra familia).
- **Opciones.** Mensaje extraño: (a) IGNORAR sin cerrar (tolerante — un
  cliente malportado no cae el socket); (b) cerrar con `CloseStatus.POLICY_VIOLATION`
  (1008) u otro código (3008/4003 — reservados a usos específicos de la RFC);
  401 del handshake: (c) 401 SIN cuerpo; (d) 401 con cuerpo `{code: invalid_token, message}`
  reutilizando el vocabulario existente.
- **Recomendación.** (a) ignorar + logueo a nivel debug SIN el contenido
  (higiene S16; tolerancia v1 — el servidor jamás cierra por un mensaje que
  no entiende salvo los caminos ya contratados: 1013/lidado por latido),
  con la validación estrecha del shape de `set_filters` (P-8/R-7.g: keys
  exactas, sin `cursor`/`limit`) que ante un `set_filters` INvalido
  simplemente NO aplica el cambio (conserva los filtros vigentes;
  comportamiento equivalente a "400 → conserva la última lista válida" del
  panel). (c) 401 sin cuerpo: los docs solo fijan el código; el handshake
  es pre-recursos y su consumidor es el cliente de C8 (que trata 401 como
  "re-login") — una familia NUEVA aquí crearía un vocabulario sin ancla
  (precedente C5/C6: no se inventan codes sin literal). El spec lo
  documenta como decisión anclada "401 sin cuerpo — sin ancla de cuerpo".
- **Impacto.** Ambas recomendaciones REDUCEN código (cero mensajes de error
  nuevos); (b)/(d) añadirían constantes+y tests+vocabulario sin ancla.

## 5. Riesgos del corte

| ID | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| **R-7.a** | Broadcast en el hilo de ingesta bloquea la 202 ante cliente(s) lento(s) | ALTO | P-6: executor monohilo recomendado; fallback síncrono solo con sendTimeLimit; 1013 cierra al lento (contrato) |
| **R-7.b** | Buffer del decorador sin techo (memoria) ante cliente zombi | ALTO | bufferSizeLimit EXPLÍCITO (P-5, 512 KiB default) + cierre 1013 + env-able; test [L] del desborde |
| **R-7.c** | Sesiones zombis acumuladas en el registro (leak del mapa) | MEDIO | afterConnectionClosed + latido como GC del mapa (P-4 cierra el mudo) + ConcurrentHashMap; test [L] de limpieza |
| **R-7.d** | JWT en query param queda en logs del nginx — riesgo CONOCIDO del 06, v1 ACEPTADA | MEDIO | Documentado (TRD §2.8; mitigación futura "token en primer frame" — EXPLÍCITAMENTE fuera de C7); higiene: backend JAMÁS loguea token/fragmentos (S16); Tailscale/logs como mitigación asumida |
| **R-7.e** | Divergencia de filtros stream/listado (dos parseos) | ALTO | P-8: parser ÚNICO extraído a `query/` (lógica de paquete); re-corrida E6.x + pin de semántica compartida ADR-7 |
| **R-7.f** | El smoke de eco de C1 deja de pasar → regresión silenciosa del proxy | MEDIO | P-3: `--solo-handshake` permanente en la suite de recomposición + smoke C7 con handler real; "verde" desambiguado en la proposal |
| **R-7.g** | `set_filters` con campos fuera del §3.3 (cursor/limit) o `metadata` del frame distinta de LogDto | MEDIO | Validación estrecha del shape del mensaje (keys exactas — P-9); frame pinado por test [L] (11 campos Literales, nulls incluidos) |
| **R-7.h** | El frame materializa hasta 200 LogDto completos (message ≤32 KiB, exception ≤128 KiB) por lote y por sesiones que matcheen | MEDIO | La lectura por ids es 1 SELECT (P-8-a); backpressure 1013 acota el acumulado por cliente lento; el filtrado por sesión reduce destinatarios; v1 monousuario — sin N alto de clientes previsto |
| **R-7.i** | Fallo de un listener del seam propaga al hilo de ingesta (D-12: NO se capturan) | BAJO | Con la fila YA en COMMIT el at-least-once queda garantizado, pero un broadcast que reviente podría cortar un 202 válido — P-6: el executor monohilo YA desacopla las excepciones del hilo Tomcat; el spec decidirá la política de catch del broadcaster (swallow + log por sesión — patrón "un cliente malo no tumba a los demás") |

## 6. Done criteria de C7 (de la ficha del 07, trazados a E7.x)

1. **Frame tras COMMIT (lit. Done)**: un `POST /ingest|/ingest/batch` real
   (1 y N) produce EXACTAMENTE un frame `batch` por lote con
   `{type:"batch", batch_id:"b_"+uuid4, project_id, received_at, logs:[…]}`
   — logs en ORDEN del lote, `LogDto` completo (11 campos), batch de 1 para
   individual, top-level `received_at` = el común (regla 9). — **E7.1, E7.5 [S]; E7.2, E7.3 [L]**
2. **`set_filters` en servidor por sesión**: tras conectar, el cliente envía
   `{"type":"set_filters","filters":{…}}` (misma semántica que `GET /logs` —
   ADR-7: reuso del parseo P-8) y el servidor SÓLO emite lotes que matchean;
   a cada cambio y tras reconectar se recalifica. — **E7.4 [L], E7.6 [S]**
3. **Handshake autenticado y Origin**: sin token / expirado / inválido →
   **401 PRE-upgrade** (sin recursos); `Origin` no permitida → rechazo;
   nunca `"*"`; `Origin` válida/token OK → 101 upgrade. — **E7.7, E7.8 [L]; E7.9 [S]**
4. **Heartbeat del servidor Y reconexión (lit. Done "heartbeat/reconexión
   de servidor probados")**: ping 0x9 cada 30 s (`WS_HEARTBEAT_SECONDS`) —
   NO JSON (literal §3.5: "ningún mensaje del servidor distinto de `batch`
   está previsto en v1"); sin pong dentro del umbral P-4 → cierre y
   limpieza de sesión; reconexión/backoff son del CLIENTE (C8) — C7 prueba
   que tras cerrar LIMPIAMENTE una nueva conexión (con `set_filters` de
   nuevo) funciona de cero. — **E7.10, E7.11 [S]; E7.12 [L]**
5. **Backpressure 1013 (contrato del corte)**: cliente lento con los límites
   del P-5 → cierre `Try Again Later` (1013) + limpieza; el resto de sesiones
   y las ingestas siguen. — **E7.13 [L], E7.14 [S]**
6. **Archivado deja de emitir (derivado, pina en pila)**: `PATCH /projects/{id}`
   a ARCHIVED → ingesta siguiente → 403 `project_archived` SIN frame (no
   hay COMMIT); desarchivar → vuelve a emitir. — **E7.15 [S]**
7. **Sin replay (lit.)**: nada se re-emite tras caída — el fallback es C6
   (listado); `set_filters` inválido NO cambia los filtros vigentes (P-9).
   — **E7.16 [L]**
8. **Echo del proxy de C1 sigue VERDE con el handler real (lit. Done —
   P-3)**: `--solo-handshake` permanente sobre pila 101+Accept+timeout
   3600 s; la eco/protocolo se sustituye por el smoke C7. — **E7.17 [S]**
9. **Regresión cero C1–C6**: suite heredada 146/146 (incluido el extraído
   del parseo P-8 — comportamiento-preservado) y los smokes C2/C3 ajustados
   (P-3). — **E7.18 [L-regresión]**

### Tabla de escenarios E7.x propuesta (para el spec/qa-plan)

| Familia | IDs | Tipo / cobertura |
|---|---|---|
| Client WS [L] (RANDOM_PORT + `java.net.http`) | E7.2 (handshake 101 + frame batch tras fake listener — forma EXACTA §3.5), E7.3 (batch de 1 / orden del lote / `b_`+UUID), E7.4 (set_filters por sesión: 2 sesiones, filtros distintos, entregas disjuntas), E7.7 (401 pre-upgrade ×3: ausente/expirado/inválido — connectBlocking falla 401), E7.8 (Origin no permitida → rechazo; permitida → 101) | [L] |
| Ciclo de vida [L] | E7.12 (cierre por latido con umbral encogido + limpieza del registro; nueva conexión funciona de cero), E7.13 (1013 por sendTimeLimit y por bufferSizeLimit — límites encogidos), E7.16 (mensajes no-contrato ignorados; set_filters inválido conserva los vigentes; sin replay) | [L] |
| Pins [L] | E7.18-regresión (re-corrida heredada) + pin del SQL `porIds` (orden preservado, patrón `LogsRepoSqlTest`) + pin del parser extraído (los 8 checks intactos) | [L] |
| Pila viva [S] | E7.1 (ingesta real → frame por nginx con JWT real), E7.5 (batch grande N→1 frame ordenado), E7.6 (set_filters recalificado en pila), E7.9 (handshake 401/Origin por proxy), E7.10 (ping 0x9 cada 30 s), E7.11 (reconexión limpia con `set_filters`), E7.14 (1013 real), E7.15 (archivado: sin frame), E7.17 (`--solo-handshake` del proxy) | [S] |

> Total ≈ 20 escenarios (~10 [L] + 9 [S] + regresión); el spec refina con
> sus parejas mock↔psql y define el guion del smoke exacto (patrón canon
> C5/C6).

## 7. Cierres pendientes si aplica

- **Deuda "Testcontainers re-evaluar en C6/C7" (C5→C6→C7)** — SE CIERRA AQUÍ
  con la ratificación del P-2 (NO-TC definitivo; el archive de C7 la cita
  como cerrada con la sustitución documentada: cliente WS JDK + Tomcat
  RANDOM_PORT + smoke de pila viva). Es la ÚLTIMA apuntada por esta línea.
- **Deuda P-3 de C6 ("el seam queda sin beans — C7 decide")** — cerrada en
  esta proposal: C7 APORTA el primer bean (`LiveBroadcaster`).
- **Nada nuevo se hereda de C7 a C8/C9 a priori**; los riesgos abiertos de
  este corte (P-4/P-5 números, P-6 executor) se anclan en spec y pina verify.
  Deudas VIVAS heredadas que NO tocan C7 (en su corte): 401 ISO-8859-1
  (C8/C9 — y NUNCA afecta al 401 del handshake: el P-9 lo deja SIN cuerpo),
  smoke `docker compose` canónico (C9), nginx `client_max_body_size` (C9).
- **Consola temporal (P-1)** si se aprueba: el spec decide su retirada (o
  permanencia como utilidad) en el archive de C8 — queda documentado en el
  propio fichero que ES TEMPORAL.

### Rollback (plan de reversión)

El corte es ADITIVO con una única edición estructural registrada: revertir
los commits de C7 (los ficheros NUEVos del paquete `streaming` desaparecen,
`WebSocketConfig` vuelve a `EchoSocketHandler` con la ancla `// C7`
intacta, `application.yml` pierde el bloque `projectlog.streaming`) deja
la pila en el estado C6 EXACTO (seam sin beans — ingest/consultas
intactas). Sin migraciones y sin dependencias nuevas, no hay reversión de
esquema ni de artefactos de build que gestionar.

---

## 8. Estimación de alcance

**Backend — ficheros NUEVOS** (`com.projectlog.streaming` + soporte; naming
final en spec tras P-7):

| Fichero | Contenido |
|---|---|
| `streaming/LogSocketHandler.java` | handler real: registro, `set_filters`, pong→latido, cierre/limpieza |
| `streaming/LiveBroadcaster.java` | `IngestListener` → batch_id → porIds → filtrar por sesión → emitir |
| `streaming/JwtHandshakeInterceptor.java` | token + Origin; 401 pre-upgrade (P-9 sin cuerpo) |
| `streaming/` (helper de registro/latido si el spec lo separa — P-7) | mapa thread-safe + scheduler de pings/cierre |
| `query/repository/LogsQueryRepository.java` (+ `query/…` parser extraído — P-8) | EDITAR (método `porIds` + extracción del parseo a lógica de paquete) |
| `config/WebSocketConfig.java` | EDITAR — 2 líneas previstas por el javadoc de C1 (handler + `.addInterceptors`) |
| `application.yml` | EDITAR — bloque `projectlog.streaming` (heartbeat, umbral P-4, límites P-5) |

**Sin diff declarado**: `ingest/**`, `SecurityConfig`, `auth/**`,
`nginx.conf`, `compose.yaml`, `.env.example`, `pom.xml` (CERO deps),
`V1__init.sql`, `frontend/**` (salvo la consola P-1 si se aprueba — fichero
nuevo AÑADIDO, nunca edición del SPA), test heredado (re-corrida).

**Scripts**: `scripts/qa_c7_smoke.sh` NUEVO; `ws_echo_test.py` / Echo —
según P-3 (el spec cierra la mecánica exacta).

**Migraciones: NO. Dependencias: NO. Front: NO (salvo consola temporal
P-1, opcional). Env vars: 0 nuevas obligatorias** (todo lo nuevo tiene
default; solo se AÑADE el mapeo de la YA-EXISTENTE `WS_HEARTBEAT_SECONDS`
más las properties opt-in del P-4/P-5).

---

> **STOP del flujo:** este proposal se presenta COMO GATE. La siguiente fase
> (sdd-spec) NO arranca hasta la aprobación explícita del usuario de los 9
> puntos (P-1…P-9) — patrón C4/C5/C6 (aprobación con las matizaciones que
> el usuario quiera anotar). Los puntos marcados "de paquete" (P-7) se
> informatan igual: el usuario puede dejarlos pasar por defecto.