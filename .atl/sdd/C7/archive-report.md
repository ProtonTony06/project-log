---
project: Project_Log
change: C7
artifact: archive-report
last_updated: 2026-10-26
status: closed
commits:
  - ce0ddcb feat(streaming): WebSocket en tiempo real — handshake JWT+Origin 401/403 pre-upgrade, set_filters 6 claves, broadcast ordenado tras COMMIT vía seam IngestListener, backpressure 1013 + latido 1001, parser extraído a FiltrosConsulta + porIds byte-compat E6 (C7)
  - a6b1962 test(smoke): scripts/qa_c7_smoke.sh codificando E7.1–E7.17 [S] + Q20 de qa_c2 → --solo-handshake --token con guards (C7)
  - 0372381 test(streaming): bucle acotado de hallazgos QA — deltas canon E7.1/E7.5 en smoke con unicidad de batch_id, tolerancia E7.14(c) exclusiva de 1013, pin cuerpo vacío de 401, orden global 2..7 del parser y matching R7.16 (C7)
  - docs(SDD): commit de cierre que incluye este informe + ficha C7 hecha en 07_SLICING_PLAN (hash en `git log` a partir de 0372381)
---

# Archive Report — C7 · Streaming WebSocket en tiempo real

Cierre del corte C7 (modo `atl`; audit trail en `.atl/sdd/C7/`). Veredicto
global: **PASS-with-notes** — 9/9 checks A–I OK, 0 CRITICAL, 2 desviaciones
ANCLADAS (WebSocketConfig 3.ª edición funcional; 1013 cerrado al DELEGADO)
+ 1 desliz documental de verify (permutación H12↔H13 en la prosa del
qa-report — ya corregida por el orquestador; errata «5 MEDIA»→6 de
tests-written también corregida) + los 9 BAJOS H2/H3/H6/H7/H8/H10/H11/H15/
H16 retenidos tal cual. Base: HEAD `a6b1962` + commits `ce0ddcb`/`a6b1962`,
con el bucle acotado commiteado por este archive (backend MAIN sin diff entre
corridas — la evidencia qa_c3/qa_c2 pre-bucle permanece válida). Frontera
R7.3: 16 ficheros en el corte commiteado (6 M + 1 D + 9 A, 1:1 con tasks.md)
+ 3 M del bucle.

## Qué quedó implementado

- Paquete `com.projectlog.streaming` (3 ficheros main + edits en
  `WebSocketConfig`, `FiltrosConsulta`, `LogsController`, `LogsQueryRepository`,
  `application.yml`, `qa_c2_smoke.sh`; REMOVE `EchoSocketHandler`):
  `JwtHandshakeInterceptor` (401/403 pre-upupgrade: Origin EXACTA
  `frontendOrigin.equals(origins.get(0))` — jamás `"*"` — en ORDEN
  Origin→token, 401 ×3 sin oráculo, cuerpo NUNCA escrito, propiedad vacía →
  403 a TODO, Origin AUSENTE → continúa S7-12), `LiveBroadcaster`
  (implements `IngestListener` — PRIMER bean del seam C5, sin tocar
  `ingest/**`: broadcast ordenado tras COMMIT vía `porIds` con reorden en
  Java, sin replay, archivado deja de emitir), `LogSocketHandler`
  (set_filters 6 claves con recalificación en caliente — no-contrato
  ignorado, inválidos conservan vigentes sin cerrar, el servidor JAMÁS
  contesta; backpressure `ConcurrentWebSocketSessionDecorator` 2000 ms /
  512 KiB → close 1013 por el DELEGADO; latido GC 1001 desde el último
  pong con scheduler propio daemon; ping 0x9, NUNCA JSON).
- `WebSocketConfig` — 3.ª edición funcional (ver desviación 1): swap de
  handler + `registro.setAllowedOrigins(frontendOrigin)` condicional a la
  propiedad non-blank.
- Frame §3.5 byte-exacto: `renderFrame` LinkedHashMap 5 claves EN ORDEN
  (`type`,`batch_id`,`project_id`,`received_at`,`logs`), `batch_id = "b_" +
  UUID v4`, `received_at` con formatter C2 por referencia (D-C7-2
  truncado-a-segundos), LogDto de 11 claves desde `query/dto/LogDto` nativo
  DIF-0 (D-C7-1, literal prevalece).
- Parser extraído a `FiltrosConsulta.parsear` (mudanza byte-compatible de
  literales, orden global de checks 2..7 primer-fallo-gana PINADO post-bucle)
  + `SELECT_POR_IDS` (literal R7.25 byte-a-byte, pin `PorIdsSqlPinTest` 7/7,
  `FILA_BATCH` DELEGANTE reutiliza `FILA_DETALLE.mapRow`).
- Tests [L]: 28 de C7 en 4 suites (`StreamingWsClientTest` 8,
  `LatidoYBackpressureTest` 2, `PorIdsSqlPinTest` 7,
  `FiltrosConsultaParseTest` 11) + 146 heredados C1–C6.
- Smoke: `scripts/qa_c7_smoke.sh` (E7.1–E7.17 [S], 9/9 PASS, exit 0) —
  recomposición única `up -d --build --force-recreate`, NUNCA `down -v`,
  guard R-7.j de `FRONTEND_ORIGIN`, higiene tmpdir 0700 con trap.
- Consola temporal `frontend/public/ws-console.html` (307 líneas, único
  fichero de front del corte; banner R7.32; login §3.2, `/ws/logs?token=`
  con encodeURIComponent, `event.code`, reconexión MANUAL, token JAMÁS en
  localStorage). **Enseñanza al cerrar el corte: abrir
  `http://<host>:8080/ws-console.html`**, iniciar sesión y observar el
  ciclo set_filters→batch (no es aserto E7.x: R7.30–R7.32 quedan fuera del
  plan de QA; verificación manual + review).

## Evidencia QA

- Dos corridas canónicas de `mvn test` (builder maven:3.9-eclipse-temurin-21,
  patrón C1–C6): pre-bucle **172/172** (Failures 0, Errors 0, Skipped 0)
  y post-bucle **174/174** BUILD SUCCESS = 28 C7 (+H12/H13) + 146 heredados.
- Smoke canónico: **E7.1–E7.17 [S] 9/9 PASS, exit 0 en AMBAS corridas**
  (runs 6 y 8; E7.14 PASS-con-nota R-7.k, tolerance exclusive-1013 del H9
  no disparada). qa-log.txt §1–§4 pre-bucle, §5–§6 post-bucle (RAW verbatim).
- qa_c3 re-corrida **24/24 PASS** con E3.24 (intocables dif-0) LIMPIO
  desde HEAD del corte (D-7.15 confirmada).
- Re-corrida qa_c2: **Q20 PASS** (modo nuevo `--solo-handshake --token` de
  T7.19, R-7.f cumplido); Q18/Q19/Q24 FAIL y Q21 BLOQUEADO son deudas
  AJENAS a C7 (healthchecks/actuator/CORS — clasificación del orquestador);
  qa_c6 re-corrida en fase apply **8/8 PASS**. qa_c3/qa_c2 NO re-corridas
  post-bucle: backend MAIN sin diff entre corridas (justificado en
  qa-report).
- Higiene de secretos: 0 hits reales `plk_`/`eyJ` en TODAS las capturas;
  JWT/key solo en tmpdir 0700 con trap.
- QA 20/20 QP-7.x verdes, re-certificados post-bucle; las 10 parejas
  mock↔pila del §2.5 1:1 (E7.9→4+5, E7.5→2+8, E7.6→3+9, E7.10/11→6,
  E7.10→10 parcial, E7.13↔E7.14 con tolerancia anotada); Higiene de
  logging R7.7/R7.35/S16: 17 sentencias log en main sin datos (revisión
  de código, Check I).

## Bucle acotado de hallazgos MEDIA (H1/H4/H5/H9/H12/H13) — CERRADO

6 parches SIN COMMIT sobre a6b1962 (commiteados por este archive como
0372381; backend MAIN sin diff), re-certificados con mvn 174/174 + smoke
run 8 9/9 (run 7 intermedio 8/9: E7.5 FAIL por colocación del snapshot,
corregido):

- **H1** — `qa_c7_smoke.sh` case_e7_1: delta canon (snapshot psql del
  proyecto ANTES de la ingesta; delta==snapshot+1; ya no usa
  `base=frames_count`).
- **H4** — `qa_c7_smoke.sh` case_e7_5: snapshot psql ANTES del batch +
  delta==3 EXPLÍCITO + unicidad de batch_id contra TODOS los previos
  (registro `$F_BIDS`, helper `bid_de`).
- **H9** — `qa_c7_smoke.sh` case_e7_14(c): exclusividad 1013 —
  close ≠ 1013 ⇒ FAIL `cierre-no-1013=<code>`; la nota R-7.k SOLO cubre
  la sesión abierta al final de la ventana.
- **H5** — `StreamingWsClientTest` e77 sub-caso (a): pin del CUERPO VACÍO
  del 401 (String-en-blanco o null = vacío, matiz de API del JDK
  documentado in-line) — aserto in-situ, sin test nuevo (7→8 por H13).
- **H12** — `FiltrosConsultaParseTest` +`ordenGlobalChecks2a7PrimerFalloGana`
  (orden global 2..7 con sub-casos EN CADENA; el sub-caso «to + from>to»
  es INFACTIBLE por diseño del parser — from>to exige to parseado —
  implementado honestamente como «from>to con 2–6 ausentes → field sin
  nulo», cierre-de-orden). Clase 10→11.
- **H13** — `StreamingWsClientTest` +`r716MatchingFromToInclusivoYTextoCaseInsensitive`
  (matching R7.16: fronteras INCLUSIVAS ==from/==to, exclusiones
  <from/>to, texto case-insensitive → EXACTAMENTE 1 frame; inversos →
  CERO). Clase 7→8.

NOTA de verify (desliz ya corregido por el orquestador): el qa-report
tenía la atribución por fichero PERMUTADA (decía H5→StreamingWsClientTest
y H13→FiltrosConsultaParseTest); los diffs reales muestran la inversa —
H13 es el test NUEVO de StreamingWsClientTest y H12 el de
FiltrosConsultaParseTest. Las cifras 8/8 y 11/11 y la suficiencia de ambos
pins eran correctas. El ancla correcta es apply-progress. Asimismo,
tests-written §7 decía «5 MEDIA» listando 6 — errata de conteo corregida.

## Matices / desviaciones ratificadas (para el spec, si se re-emite)

1. **WebSocketConfig — 3.ª edición funcional** (WARNING): R7.3.1/T7.11
   prescribían EXACTAMENTE 2 ediciones; el diff añade inyección de
   `frontend-origin` + `registro.setAllowedOrigins(frontendOrigin)`
   condicional. CAUSA anclada en `.atl/notes/origin-handshake-interceptor-default-spring-websocket.md`
   (bytecode verificado con javap sobre spring-websocket-6.2.19.jar):
   spring-websocket 6.2.19 AÑADE SIEMPRE un `OriginHandshakeInterceptor`
   por defecto que con la lista VACÍA rechaza TODO Origin cross-origin
   ANTES de los interceptores propios (el 403 de E7.9 con token VÁLIDO no
   era del corte). COHERENTE con el espíritu del spec: Origen EXACTO,
   jamás `"*"` ni patterns; con la propiedad vacía (S26) el dueño del
   reject sigue siendo el JwtHandshakeInterceptor (403 primero; default
   inerte). Matiz spec-side: R7.3.1 «SOLO 2 ediciones» si el spec se
   re-emite. Javadoc del fichero + apply-progress T7.11.
2. **`cerrarPorLentitud` cierra al DELEGADO (1013)** (BAJO): cerrando por
   el DECORADOR, spring reescribe 1013→1011 (SESSION_NOT_RELIABLE) y el
   cliente jamás vería el código contractual. ANCLADA en
   `.atl/notes/close-1013-vs-decorador-1011-bugfix.md` + Javadoc del
   método + apply-progress T7.16 + pin [L] E7.13 VERDE. FORTALECE el
   contrato (R7.22 no prescribía a quién close).
3. **Divergencias CITADAS, ninguna corregida en silencio**: D-C7-1 (frame
   con 11 claves vs ejemplo §3.5 — implemented por el LITERAL), D-C7-2
   (received_at truncado-a-segundos en frame vs micros en BD — formatter
   C2 por referencia; matching from/to usa el instante RAW), D-7.15
   (qa_c3.smoke.sh dif-0 CONFIRMADA), R-7.j (guard FRONTEND_ORIGIN en
   ambos smokes; evaluado NO activado, `.env` NON-EMPTY), R-7.k (E7.14
   PASS-con-nota: el 1013 no se materializa en pila con los defaults —
   defaults absorben; el pin 1013-dual vive en QP-7.13 [L]). Matiz menor
   anclado: `FILA_BATCH` de INSTANCIA vs `static` del boceto (Javadoc).
4. **H12 sub-caso «to + from>to»** — INFACTIBLE por diseño del parser
   (documentado arriba); constancia para re-auditorías.

## Hallazgos BAJOS retenidos (sin parche, matices documentales)

H2 (regex batch_id solo [L], H3 (Accept verificado transversal en E7.9c),
H6 (SIN Origin→101 por absorción), H7 (no-respuesta indirecta), H8
(ventana ping ±6 s / ~30 KiB vs 32), H10 (cuerpo 403 solo `code`), H11
(pin del delegante de comportamiento), H15 (barrera BUFFER vía decorador
del test), H16 (mock E7.3 con filas en orden — reorden pinado por E7.19).
NINGUNO invalida cobertura; van tal cual en tests-written §6 / qa-report.

## Deudas RETENIDAS / heredadas (sin acción en C7)

- Smoke de docker compose canónico → **C9** (el smoke sigue con podman
  `--build --force-recreate`, NUNCA `down -v`).
- `nginx client_max_body_size` → **C9** (heredada de C5).
- 401 ISO-8859-1 → **C8/C9** (heredada de C2; el 401 SIN cuerpo del
  handshake es OTRA superficie — S7-3).
- Token-en-primer-frame → FUTURO (v1 = query param, como el spec).
- Deuda ajena de qa_c2: Q18/Q19/Q24/Q21 (healthchecks/actuator/CORS —
  flake/absorción, clasificación del orquestador).

## Deudas CERRADAS en C7

- **Testcontainers (P-2)**: cero diff en pom.xml, cero dependencias
  nuevas; sustitución REAL por cliente WS JDK `java.net.http` +
  RANDOM_PORT + smoke de pila viva (4 ficheros de test + smoke).
- **Hueco "seam sin beans" de C5/C6**: PRIMER bean del seam
  `LiveBroadcaster implements IngestListener` en main con ingest/**
  dif-0 — el seam C5 se activa sin editar nada.

## Deudas NUEVAS del corte

- NINGUNA.

## Cierre

Ficha C7 en `docs/07_SLICING_PLAN.md` actualizada a
`estado: hecho 2026-10-26`; commits del corte ce0ddcb (feat) → a6b1962
(test/smoke) → 0372381 (bucle acotado) → commit docs de cierre. Veredicto
de cierre: **C7 CERRADO** — PASS-with-notes con 0 CRITICAL; desviaciones
ancladas, bucle cerrado con re-certificación, BAJOS documentados, deudas
vivas intactas y las dos CERRADAS acreditadas. NO push (manual del
usuario). C8 NO se inicia sin orden del usuario.