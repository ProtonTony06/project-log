---
project: Project_Log
change: C7
artifact: verify-report
last_updated: 2026-10-26
verdict: PASS-with-notes
status: pass-with-notes
---

# Verify Report — C7 · Streaming WebSocket en tiempo real

Verificación INDEPENDIENTE del corte C7 sobre el espécimen real (árbol: HEAD
`a6b1962` + commits `ce0ddcb`/`a6b1962` + 3 ficheros M SIN COMMIT del bucle
acotado + sin trackear `.atl/notes/**`/`.atl/sdd/C7/**`). La evidencia formal
de test-run (mvn 172/172 pre-bucle → **174/174** post-bucle, smoke C7 9/9 =
E7.1–E7.17 [S] PASS en AMBAS corridas, qa_c3 24/24, qa_c2 exit 1 por deudas
AJENAS con Q20 PASS) NO se repite — se consume como dato de `qa-log.txt`
(§1–§4 pre-bucle, §5–§6 post-bucle, cifras re-chequeadas por grep) — pero
TODO el contenido contractual del corte se re-lee y se pina desde el código
real contra `spec.md` (R7.1–R7.37, S7-1…S7-12, D-C7-1/D-C7-2, E7.1–E7.20),
`design.md` (D-7.1…D-7.15, R-7.a…R-7.k) y `tasks.md` (T7.1–T7.25, frontera).

**Veredicto: PASS-with-notes** — 9/9 checks A–I OK, 0 CRITICAL, 2
desviaciones ANCLADAS (WebSocketConfig 3.ª edición funcional; 1013 cerrado
al DELEGADO) + 1 desliz documental de verify (WARNING: qa-report permuta la
atribución H12↔H13 en "Dónde se parcheó"; los diffs reales y las cifras son
correctos) + los 9 BAJOS H2/H3/H6/H7/H8/H10/H11/H15/H16 retenidos tal cual.
No hay arreglos pendientes en el código.

## Check A — Frontera R7.3/R7.4: **OK** (CRITICAL — cumplido)

- `git diff 89b5d60..a6b1962 --name-status` (corte commiteado): EXACTAMENTE
  16 ficheros = 6 M (`WebSocketConfig`, `FiltrosConsulta`, `LogsController`,
  `LogsQueryRepository`, `application.yml`, `qa_c2_smoke.sh`) + 1 D
  (`streaming/EchoSocketHandler.java`) + 9 A (3 main streaming:
  `JwtHandshakeInterceptor`/`LiveBroadcaster`/`LogSocketHandler`; 4 tests;
  `frontend/public/ws-console.html`; `scripts/qa_c7_smoke.sh`) — 1:1 con la
  lista CERRADA de tasks.md (E1–E7 + piezas NUEVAS).
- `git diff` (working tree): EXACTAMENTE los 3 M del bucle documentados
  (`FiltrosConsultaParseTest`, `StreamingWsClientTest`, `qa_c7_smoke.sh`).
  Backend MAIN sin diff post-bucle — el re-chequeo qa_c3/qa_c2 omitido
  post-bucle permanece VÁLIDO (evidencia pre-bucle §3/§4 no desfasa).
- INTOCABLES con diff NULO en AMBOS rangos (commited + working): pom.xml,
  V1__init.sql, `application-test.yml`, compose.yaml, .env.example,
  nginx.conf, frontend/Dockerfile, vite.config.ts, ws_echo_test.py (byte-a-byte),
  qa_c5/c6/c3/c3_qa/c4_smoke.sh — **qa_c3_smoke.sh dif-0 CONFIRMADO** (D-7.15).
  El resto (ingest/**, SecurityConfig, CorsConfig, auth/**, projects/**,
  keys/**, query/{CursorCodec,PatronIlike,package-info}, query/dto/**,
  streaming/package-info, common/**, frontend/src/** — ni una línea,
  docs/**, TODOS los tests C1–C6) queda sin diff POR CONSTRUCCIÓN: no
  aparece en ninguna de las dos listas name-status, que son exhaustivas.

## Check B — Cobertura E7.1–E7.20 (11 [L] + 9 [S]) y parejas §2.5: **OK**

- qa-plan (§1/§3: QP-7.1…QP-7.20 con fijación de fixtures/asertos) ↔
  tests-written (§1 inventario 4 ficheros Maven + smoke + Q20; §6 hallazgos
  H1–H16) ↔ qa-report (tabla con veredicto y método por QP): **20/20 QP
  verdes**, re-certificados post-bucle. Sin hueco: ningún QP sin test o
  live-evidence asignada; E7.18 vive en los gates T7.17/T7.23 + re-corrida
  post-bucle (172→174, cifras reales en qa-log EXIT=0 líneas 12 y 1402).
- **Las 10 parejas mock↔pila del §2.5, 1:1 verificadas** en tests-written §4
  con las absorciones EXACTAS que el plan declaró (E7.9→parejas 4+5;
  E7.5→2+8; E7.6→3+9; E7.10/E7.11→6; E7.10→10 parcial; E7.13↔E7.14 con
  tolerancia anotada); E7.15/E7.17 sin pareja como el plan exime (derivado
  flujo 7 paso 4 y permanente S7-7).
- **H13 cerrado con el método de matching R7.16**: el nuevo test
  `r716MatchingFromToInclusivoYTextoCaseInsensitive` SÍ está en el diff
  working tree de `StreamingWsClientTest.java` (+65 líneas: fronteras
  INCLUSIVAS ==from y ==to, exclusiones <from/>to, texto
  mayúsculas-en-filtro/minúsculas-en-mensaje → EXACTAMENTE 1 frame y casos
  inversos → CERO). La NOTA DE COBERTURA está en qa-report (fila QP-7.20).

## Check C — Literales y estamentos (contrato verbatim): **OK** (CRITICAL — cumplido)

- **SELECT_POR_IDS**: comparado contra el literal R7.25 del spec —
  byte-exacto (`SELECT id, project_id, level, environment, timestamp,
  received_at, message, exception, metadata, source, correlation_id FROM
  logs WHERE id = ANY (?)`); SIN ORDER BY, SIN array_position; 11 columnas
  en ESE orden. Pina `PorIdsSqlPinTest` (isEqualTo byte-a-byte + cuenta de
  placeholders + doesNotContain ORDER BY/array_position — 7/7). Reorden en
  Java por entrada (LinkedHashMap por id → salida en orden de la LISTA),
  faltantes ignorados, guard null/vacía sin tocar BD, bind
  `createArrayOf("bigint", …)`; `FILA_BATCH` DELEGANTE reutiliza
  `FILA_DETALLE.mapRow` (R7.26 — reuso in-situ, sin fichero nuevo; pin DEL
  COMPORTAMIENTO, matiz H11 retenido).
- **Frame §3.5**: `LiveBroadcaster.renderFrame` = LinkedHashMap con
  EXACTAMENTE 5 claves EN ORDEN (`type`,`batch_id`,`project_id`,
  `received_at`,`logs`); `batch_id = "b_" + UUID.randomUUID().toString()`
  (v4); `received_at` = `receivedAt.format(LoginController.EXPIRES_AT_
  FORMATTER)` (formatter C2 por REFERENCIA — **D-C7-2 truncado-a-segundos**,
  implementado, NO corregido silenciosamente); `logs` = DTOs completos del
  lote (objectMapper del BEAN — D-3). LogDto de 11 componentes proviene
  INTACTO de `query/dto/LogDto` (nativo, dif-0) — **D-C7-1 (11 claves vs
  ejemplo §3.5) implementada por el LITERAL**, pineada por E7.2 en [L] y
  `verificar_frame` (object_pairs_hook=list) en [S].
- **set_filters 6 claves**: `LogSocketHandler.CLAVES_FILTERS = Set.of(...)` —
  conjunto cerrado (project_id/environment/level/from/to/text), loop de
  fieldNames con extra→no-contrato (JAMÁS cursor/limit); `project_id`
  admite número JSON normalizado a String; `""` PRESENTE viaja al parser
  (inválido en env/level — S6-2); parseo ÚNICO vía
  `FiltrosConsulta.parsear`; INVÁLIDO conserva vigentes sin cerrar (R-7.g);
  el servidor JAMÁS contesta (no hay envío fuera del broadcast).
- **401/403 pre-upgrade**: `JwtHandshakeInterceptor.beforeHandshake` en
  ORDEN Origin→token; comparación EXACTA (`frontendOrigin.equals(origins.
  get(0))`), sin normalizar, jamás `"*"`; propiedad vacía → 403 a TODO;
  Origin AUSENTE → continúa (S7-12); 401 en las 3 causas de token sin
  oráculo; `rechazar()` solo `setStatusCode` — **cuerpo NUNCA escrito**
  (cuerpo vacío del 401 ahora PINADO en [L] por el parche H5 del bucle —
  sub-caso (a) de e77 acepta String-en-blanco o null como vacío, matiz de
  API del JDK documentado in-line).
- **1013 dual / 1001**: `ConcurrentWebSocketSessionDecorator(session,
  sendTimeLimitMs, bufferSizeBytes)` con 2000 ms / 524288 de config; en
  `SessionLimitExceededException` → `cerrarPorLentitud` → **close(1013)**
  por el DELEGADO (desviación anclada, ver §Desviaciones 2) + WARN sin
  contenido; latido GC **1001 GOING_AWAY** desde el último pong (umbral
  desde conexión vía nanoTime inicial); scheduler PROPIO daemon +
  ping `PingMessage` (0x9 — NUNCA JSON). **H9 endurecido**: el guion
  post-bucle hace `close ≠ 1013 ⇒ FAIL 'cierre-no-1013=<code>'` —
  verificado en el diff working tree (la nota R-7.k solo cubre la SESIÓN
  ABIERTA al final de la ventana).
- **Seam sin diff en ingest/**: `LiveBroadcaster implements IngestListener`
  (PRIMER bean del seam) — ingest/** dif-0 confirmado (Check A).
- **Códigos 400 con el parser mudado**: los CODE_*/MSG_* de checks 2–7
  MUDAN a `FiltrosConsulta` con LITERALES byte-idénticos (comparados
  carácter a carácter contra el diff C6 del controller — ningún literal
  alterado en la mudanza); `LogsController` conserva limit (check 1) ANTES
  y cursor (check 8) DESPUÉS del `parsear(...)` — orden global 2..7
  primer-fallo-gana preservado y AHORA PINADO por el parche H12
  (`ordenGlobalChecks2a7PrimerFalloGana`, sub-casos EN CADENA 2>3>4>5>6>7).
  El helper `error(estado, codigo, mensaje, campo...)` omite la clave
  `field` cuando es null (`campo[0] != null`), así que el cuerpo del check 7
  (field null) es byte-idéntico al C6 — E7.18 174/174 lo corona.

## Check D — Divergencias CITADAS trazables: **OK** (ninguna corregida en silencio)

- **D-C7-1** (LogDto 11 vs ejemplo 10): IMPLEMENTADA por el literal — el
  frame emite 11 campos incl. `project_id` (`FilaBatch::log` completo);
  pina E7.2/E7.1. Citada en spec §0.3, tasks (notas), apply-progress.
- **D-C7-2** (truncado-a-segundos): IMPLEMENTADA — formatter C2 por
  referencia en el render del frame; el matching from/to usa el instante
  RAW (`FilaBatch.received_at`/parametro del seam); en [S] la comparación es
  contra el **psql truncado** (`ra_frame == "${ra_psql%%.*}Z"`). NO se
  "corrigió" a micros en ninguna parte (grep de micros en render: solo el
  RAW interno).
- **D-7.15** (qa_c3 dif-0): CONFIRMADA — `git diff` sobre
  `scripts/qa_c3_smoke.sh` = 0 en ambos rangos; re-corrida pre-bucle 24/24
  PASS con E3.24 (intocables dif-0) PASS.
- **R-7.j** (guards FRONTEND_ORIGIN): en `qa_c7_smoke.sh` (CON_ORIGIN +
  `bloqueo_origin` + exit 2, línea 587) y en `qa_c2_smoke.sh` case_q20
  (CON_ORIGIN≠1 → BLOQUEADO con nota S7-12) — ambos en el diff del corte;
  ningún `"*"` en código ni en smokes (grep limpio en los 3 ficheros main
  y en el guard); evaluado, NO activado ( `.env` con FRONTEND_ORIGIN)
  — coherente con qa-report.
- **R-7.k** (E7.14 PASS-con-nota; pin contractual en QP-7.13 [L]): la nota
  emitida por el guion es EXACTamente «1013 no materializado en pila —
  cubierto por pareja [L] E7.13 (R-7.k; code=)» (qa-log líneas 448/1840);
  el pin 1013-dual vive en `LatidoYBackpressureTest.e13Cierre…` (2/2 VERDE
  ambas corridas); qa-plan §6 lo declara como criterio anotado. NO se
  extendió la tolerancia a otros códigos: el parche H9 del bucle cierra
  exactamente el hueco H9 de tests-written (cualquier close ≠ 1013 → FAIL).
- Matices menores ANCLADOS (no divergencias): `FILA_BATCH` de INSTANCIA vs
  `static` del boceto de design §4.1 — declarado en el Javadoc del fichero
  (motivo: `FILA_DETALLE` es instancia); la línea ancla de
  `WebSocketConfig` se RESUELVE con el comentario literal prescrito por
  T7.11 («C7 ancla resuelta… frase conservada» — la mitad
  `sin .addInterceptors` desaparece como el tasks ordena).

## Check E — Estado QA: **OK** (coherente con qa-log)

- Cifras re-chequeadas en `qa-log.txt` (sin leer los RAW): §1 mvn
  `Tests run: 172, Failures: 0, Errors: 0` EXIT=0; §2 smoke C7 `9/9 PASS`
  EXIT=0 (E7.1/5/6/9/10/11/14/15/17 — E7.14 PASS con nota R-7.k, línea 448);
  §3 qa_c3 `24/24 PASS` EXIT=0 (E3.24 dif-0 LIMPIO); §4 qa_c2 `EXIT=1` con
  `Q20: PASS` (línea 1372) — deudas Q18/Q19/Q24 FAIL + Q21 BLOQUEADO
  AJENAS documentadas (C7 no toca healthchecks/actuator/CORS; la
  clasificación exacta es del orquestador); §5/§6 post-bucle: mvn
  **174/174** EXIT=0 (FiltrosConsultaParseTest 11, Latido 2,
  StreamingWsClientTest 8 — desglose visible en la misma captura) y smoke
  `9/9 PASS` EXIT=0 (E7.14 nota con `(R-7.k; code=)`, línea 1840).
- Orden prescrito respetado (mvn → qa_c7 → qa_c3 → qa_c2 última por su
  down -v); post-bucle SOLO los 2 comandos canónicos — la omisión de
  qa_c3/qa_c2 post-bucle está JUSTIFICADA en qa-report (backend main sin
  diff entre corridas — verificado Check A; el Q13 haría down -v).
- Higiene de secretos: las únicas 3 líneas de qa-log que contienen
  `plk_`/`eyJ` SON las declaraciones del propio grep (0 hits reales
  declarados en §4 y §6, acumulado 0/0 en todas las capturas); JWT/key
  SOLO en tmpdirs 0700 con trap (nombres de tmpdir impresos).
- NOTA (WARNING — desliz documental hallazgo de verify): en la sección
  «Cierre del bucle acotado» del qa-report la atribución por fichero de los
  parches está PERMUTADA: dice que StreamingWsClientTest suma el pin de
  CUERPO VACÍO (H5→8/8) y que FiltrosConsultaParseTest suma el pin de
  matching R7.16 (H13→11/11), cuando los diffs reales muestran la inversa:
  H13 (matching) es el test NUEVO de StreamingWsClientTest (7→8) y H5 solo
  REFUERZA in-situ e77 (sub-caso (a) cuerpo vacío, sin test nuevo), mientras
  que el test NUEVO de FiltrosConsultaParseTest (10→11) es el pin de ORDEN
  GLOBAL H12. `apply-progress.md` (tabla del bucle) lo tiene CORRECTO y es
  el ancla; las cifras 8/8 y 11/11 y la suficiencia de ambos pins son
  correctas en ambos documentos — el desliz es de PROSA del qa-report (y se
  arrastra a las filas QP-7.7/QP-7.20 de la tabla). Sin impacto en
  veredictos del QA; para el archive basta corregir la atribución.

## Check F — Bucle acotado resuelto: **OK**

- **6 MEDIA cerrados con parche, SIN COMMIT sobre a6b1962** — verificados
  patch-a-patch contra `git diff` working tree:
  H1 (case_e7_1: `snapshot_bd="$(delta_cuenta)"` ANTES de la ingesta;
  delta==snapshot+1 — ya no usa `base=frames_count` como base de BD),
  H4 (case_e7_5: snapshot psql ANTES del batch + delta==3 EXPLÍCITO +
  unicidad de batch_id contra TODOS los previos del registro `$F_BIDS`,
  con helper `bid_de` — elimina el check vacuo del base==0),
  H9 (case_e7_14: exclusividad 1013 — close ≠ 1013 → FAIL
  `cierre-no-1013=<code>`; la nota R-7.k SOLO para sesión abierta), en el
  guion; H5 (e77 sub-caso (a) cuerpo vacío del 401), H12
  (`ordenGlobalChecks2a7PrimerFalloGana`, con el sub-caso infeasible
  'to + from>to' sustituido honestamente por 'from>to con 2–6 ausentes →
  field sin nulo'), H13 (`r716Matching…`), en tests.
- **9 BAJOS retenidos como matices documentales** (H2/H3/H6/H7/H8/H10/H11/
  H15/H16): registrados EXPLÍCITAMENTE en qa-report («BAJOS RETENIDOS para
  verify») y apply-progress (cierre del bucle) — ningún Bajo aplicado,
  ninguno silenciado.
- Re-certificación real: mvn 174/174 + smoke run 8 9/9 (run 7 intermedio
  8/9 — E7.5 FAIL por colocación del snapshot, CORREGIDO y veredicto
  registrado) — la historia del bucle es auditable en qa-log §5/§6 y
  apply-progress; spec/design/tasks INTACTOS (mtimes 2026-10-25) —
  NINGUNA divergencia fue "arreglada" tocando docs.

## Check G — Consola y enseñanza R7.30–R7.32: **OK**

- `frontend/public/ws-console.html` existe (307 líneas, ÚNICO fichero de
  front del corte; `frontend/src/**` dif-0 por doble rango — el build del
  SPA sin tocar); banner VISIBLE + comentario de cabecera «CONSOLA
  TEMPORAL C7 — no es parte del panel; se reemplaza en C8» (R7.32); login
  §3.2 (`POST /api/v1/panel/auth/login`), URL de conexión
  `/ws/logs?token=` con encodeURIComponent, badge/eventos con `event.code`,
  reconexión MANUAL (no backoff — R7.31); el smoke la PUBLICA y la coteja
  en pre-fase sin usarla como dependencia (cliente Python — R7.32).
- **Nota para el archive**: URL de enseñanza al cerrar =
  `http://<host>:8080/ws-console.html` (captura del ciclo
  set_filters→batch). NO es aserto E7.x (R7.30–7.32 quedan FUERA del plan
  de QA, §5.1 — verificación HERE por revisión de ficheros, como el spec
  §5 prescribe: "manual + review").

## Check H — Deudas pendientes citadas, INTACTAS; 2 CERRADAS: **OK**

- Vivas y SIN toque (dif-0 verificado Check A): smoke canónico docker
  compose → C9 (el smoke sigue con podman `--build --force-recreate`,
  NUNCA `down -v` — grep: cero `down -v` real en qa_c7_smoke.sh);
  401 ISO-8859-1 → C8/C9 (el 401 SIN cuerpo del handshake es OTRA
  superficie — S7-3); nginx client_max_body_size → C9; token-en-primer-frame
  → FUTURO (v1 = query param — el interceptor lo implementó EXACTO).
- CERRADAS y confirmadas: **Testcontainers (P-2)** — cero diff en pom.xml,
  cero dependencias nuevas; la sustitución (cliente WS JDK `java.net.http`
  + RANDOM_PORT + smoke de pila viva) es REAL en los 4 ficheros de test y
  en el smoke. **Hueco "seam sin beans" de C6** — el PRIMER bean
  `LiveBroadcaster implements IngestListener` está en main y su dif-0 en
  ingest/** confirma que el seam C5 se activa sin editar nada.

## Check I — Higiene de logging (S16/R7.7/R7.35): **OK** (revisión de código)

- Los 3 ficheros main de streaming + consola, TODAS las sentencias log
  (17 en main): cada mensaje es UNA línea genérica sin interpolación de
  datos — «handshake WS rechazado» (sin causa/token/query),
  «mensaje no-contrato ignorado» ×6, «set_filters inválido: filtros
  vigentes conservados» (sin el filtro), «sesión cerrada por lentitud
  (1013)», «latido:…» ×3, «lote no encolado para broadcast», «broadcast:
  fallo materializando el lote» / «envío fallido a una sesión» — cero
  contenido de mensajes, filtros, tokens, JWT o frames. `tokenService.verify`
  es la ÚNICA superficie que toca el token (por referencia — R7.8); el
  token NO se guarda en attributes (el interceptor jamás escribe en el
  mapa). Consola: token SOLO en variable en memoria («JAMÁS localStorage ni
  cookies»), sin console.log del token ni de frames con credenciales.

## Desviaciones ANCLADAS (ninguna abierta)

1. **WebSocketConfig — la 3.ª edición funcional** (WARNING): el spec R7.3.1
   y tasks T7.11 prescribían EXACTAMENTE 2 ediciones (swap de handler +
   `.addInterceptors`); el diff real añade inyección de `frontend-origin`
   + `registro.setAllowedOrigins(frontendOrigin)` CONDICIONAL (solo con la
   propiedad non-blank). CAUSA: hallazgo de la pila — spring-websocket
   6.2.19 AÑADE SIEMPRE un `OriginHandshakeInterceptor` por defecto que con
   la lista VACÍA rechaza TODO Origin cross-origin ANTES de los
   interceptores propios (el 403 del smoke E7.9 con token VÁLIDO no
   venía del corte). ANCLAS: `.atl/notes/
   origin-handshake-interceptor-default-spring-websocket.md` (discovery,
   con diagnóstico por capas y verificación de bytecode), Javadoc del
   propio fichero, apply-progress T7.11. La edición es COHERENTE con el
   spec sustantivo: orígen EXACTO (jamás `"*"` ni patterns — G1), con la
   propiedad vacía (S26) el dueño del reject SIGUE siendo el
   JwtHandshakeInterceptor (403 primero; el default queda inerte). Matiz
   spec-side para el archive: R7.3.1 «SOLO 2 ediciones» si el spec se
   re-emite. CERRADA por decisión de apply con evidencia; NO bloqueante.
2. **`cerrarPorLentitud` cierra al DELEGADO (1013)** (BAJO): R7.22 no
   prescribía a quién `close`; cerrando POR EL DECORADOR spring reescribe
   1013→1011 (`SESSION_NOT_RELIABLE`) y el cliente jamás vería el código
   contractual. ANCLAS: `.atl/notes/close-1013-vs-decorador-1011-bugfix.md`,
   Javadoc del método, apply-progress T7.16 (D-7.15-adjunto, «nota
   bugfix»), y el pin [L] E7.13 (verify close 1013 VERDE). FORTALECE el
   contrato (materializa el ÚNICO código pineado por lentitud).

## Matices / hallazgos de verify (nuevos, no del bucle)

- **WARNING — atribución H12↔H13 permutada en qa-report** (Check E): los
  parches reales y las cifras están bien; la PROSA del qa-report
  («Dónde se parcheó» + filas QP-7.7/QP-7.20) cruza qué pin cayó en qué
  fichero. El ancla correcta es apply-progress. Corregirla en archive
  (o dejar constancia de este reporte como ancla).
- **SUGGESTION — tests-written §7 dice «5 MEDIA»** y lista 6 (H1, H4, H5,
  H9, H12, H13). Errata de conteo sin efecto (qa-report/apply-progress
  cuentan 6). Matizar en archive.
- **BAJOS H2/H3/H6/H7/H8/H10/H11/H15/H16 retenidos** ( esperados): matices
  documentales de asertos del plan vs tests — H2 (regex batch_id solo [L]),
  H3 (Accept verificado en E7.9c transversal), H6 (SIN Origin→101 por
  absorción), H7 (no-respuesta a set_filters indirecta), H8 (ventana ping
  ±6 s / pong sin pin directo / ~30 KiB vs 32), H10 (cuerpo 403 solo
  `code`), H11 (pin del delegante de comportamiento), H15 (barrera BUFFER
  vía decorador del test), H16 (mock de E7.3 con filas en orden —
  reorden pinado por E7.19). NINGUNO invalida cobertura; van al archive
  tal cual.

## Veredicto global

**PASS-with-notes** — 0 CRITICAL. Todo el contenido contractual del corte
está implementado, pineado y verificado contra el código real; los 6 MEDIA
del bucle están cerrados con parche (sin commit, backend main sin diff) y
re-certificados; las desviaciones están ANCLADAS y documentadas; las deudas
vivas quedan intactas y las CERRADAS quedan acreditadas. Notas para el
archive: (a) corregir la atribución H12/H13 del qa-report; (b) errata «5
MEDIA»; (c) matiz spec-side de R7.3.1 (3.ª edición de WebSocketConfig,
anclada en .atl/notes); (d) recoger los 9 BAJOS retenidos, la deuda ajena
de qa_c2 (Q18/Q19/Q24/Q21, clasificación del orquestador) y las deudas
vivas heredadas (docker compose → C9; 401 ISO-8859-1 → C8/C9;
client_max_body_size → C9; token-en-primer-frame → futuro); (e) cita de
Testcontainers CERRADA (P-2) y del hueco "seam sin beans" CERRADO con
`LiveBroadcaster`; (f) URL de enseñanza `http://<host>:8080/ws-console.html`
al cerrar el corte. Los 3 ficheros M del bucle quedan SIN COMMIT por
decisión del orquestador (patrón de cortes previos: archive genera los
commits).