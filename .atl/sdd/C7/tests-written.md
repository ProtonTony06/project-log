---
project: Project_Log
change: C7
artifact: tests-written
last_updated: 2026-10-26
---

# Tests-written — C7 · Streaming WebSocket en tiempo real

> Auditoría-documental del test-side C7 contra `qa-plan.md` (QP-7.1…QP-7.20,
> asertos §2–§3, transversales 2.A–2.D, parejas §2.5 del spec). Aislamiento
> CONTEXT-0: NO se leyó el código implementado (NINGÚN fichero de
> `backend/src/main/java/**`), ni `application.yml`, ni `WebSocketConfig` de
> main, ni diffs de git, ni `apply-progress.md`, ni `.atl/notes/**`. Fuente
> de veredictos: SOLO el plan (qa-plan §2–§4) ↔ ficheros de test —
> `backend/src/test/java/com/projectlog/streaming/{StreamingWsClientTest,
> LatidoYBackpressureTest}.java`, `query/FiltrosConsultaParseTest.java`,
> `query/repository/PorIdsSqlPinTest.java` + `scripts/qa_c7_smoke.sh` (+ Q20
> de `scripts/qa_c2_smoke.sh`, editado por C7 — D-7.14/R-7.f).
>
> Nadie de test-side parcheó nada: este artefacto DOCUMENTA, el bucle
> apply/QA resuelve los hallazgos.

## 1. Inventario auditado (completo)

| Fichero | Escenarios del plan | QP-7.x |
|---|---|---|
| `backend/src/test/java/com/projectlog/streaming/StreamingWsClientTest.java` | E7.2, E7.3, E7.4, E7.7, E7.8, E7.16 (a–d) + E7.16 (e) | QP-7.2, 7.3, 7.4, 7.7, 7.8, 7.16 |
| `backend/src/test/java/com/projectlog/streaming/LatidoYBackpressureTest.java` | E7.12 (a/b/c), E7.13 (a/b/c) | QP-7.12, 7.13 |
| `backend/src/test/java/com/projectlog/query/FiltrosConsultaParseTest.java` | E7.20 (checks 2–7 + caso feliz + aguja `_` + `""` + to-null + re-corrida `patronIlike`) | QP-7.20 |
| `backend/src/test/java/com/projectlog/query/repository/PorIdsSqlPinTest.java` | E7.19 (pin SQL + firma + reorden + faltantes + delegante + guard + binder) | QP-7.19 |
| `scripts/qa_c7_smoke.sh` | E7.9, E7.17, E7.1, E7.5, E7.6, E7.15, E7.10, E7.11, E7.14 + pre-fase (→ QP-7.1) | QP-7.1, 7.5, 7.6, 7.9, 7.10, 7.11, 7.14, 7.15, 7.17 |
| `scripts/qa_c2_smoke.sh` §`case_q20` (edit T7.19) | E7.17 en forma permanente Q20 (Pila de C2) | QP-7.17 (misma forma, R-7.f) |
| (live-evidence de gates, sin fichero propio) `mvn test` ×2 | E7.18 regresión completa | QP-7.18 |

- Sin ficheros de higiene nuevos, sin extras: el inventario coincide con el plan §1 (4 ficheros Maven + 1 smoke nuevo + el Q20 editado, sin tocar `application-test.yml` — los umbrales viajan SOLO por `@SpringBootTest(properties=…)` ✓).
- **Todos los QP-7.1…QP-7.20 tienen test o live-evidence asignada**; E7.1–E7.20 quedan los 20 representados (11 [L] + 9 [S]).
- Nota de nombres: los métodos difieren de la col. «método previsto» del plan (T7.15/T7.16: `frameTrasCommitFormaExacta§3.5` / `batchDeUnoYOrdenDelLote` / …). Los reales son `e72FrameTrasLoteFormaExacta35`, `e73BatchDeUnoOrdenDelLoteYIdsVacioSinFrame`, `e74SetFiltersPorSesionTresClientes`, `e77Handshake401PreUpgradeTresCausasSinOraculo`, `e78OriginValidadaExactaYJamasComodin`, `e76NoContratoIgnoradoSinCierreYVigentesConservados` + `e76eSinReplayTrasDesconexion` (QP-7.16 repartido en DOS métodos — (a–d) y (e)), `e12LatidoCierraMuda1001LimpiaYElRealPermaneceYReconexion`, `e13Cierre1013AmbosLimitesYLosDemasIntactos`. Los prefijos `e72/e73/…/e76` mantienen la trazabilidad 1:1 E7.x — MATIZ de nomenclatura, no de cobertura. El plan anunciaba `PorIdsSqlPinTest` en T7.5 y `FiltrosConsultaParseTest` en T7.4 — coinciden ✓.
- Cuenta de métodos [L]: 26 `@Test` (7 StreamingWsClientTest + 2 LatidoYBackpressureTest + 10 FiltrosConsultaParseTest + 7 PorIdsSqlPinTest) — dentro del estimado 25–30 del plan §4; **cifra esperada para T7.23 / QP-7.18: 146 heredados (C6) + 26 = 172 verde**, PENDIENTE de confirmar en ejecución (este auditor no corre mvn). Regresión E7.18 byte-compat de los E6.x: NO verificable por este auditor (leyer diffs está prohibido por el aislamiento) — queda como gate de qa-run.

## 2. Auditoría por aserto — bloque [L]

### QP-7.2 — E7.2 `frameTrasCommitFormaExacta§3.5` — **CUBIERTO** (con 2 matices)
`StreamingWsClientTest.e72FrameTrasLoteFormaExacta35`.
- «EXACTAMENTE UN TextMessage por lote» §2.A-6 ✓: 2.º `pollTexto(NADA_MS=500)` `isNull()`.
- «5 claves top-level EN ORDEN» (§2.A-1) ✓: `containsExactly("type","batch_id","project_id","received_at","logs")` — verificación de orden por `fieldNames()` (LinkedHashMap, D-7.3).
- «batch_id matchea regex UUID v4» ✓ (`matches("^b_[0-9a-f]{8}-…-[89ab]…$")` — el 4 de versión y el 89ab de variante pinnados).
- «project_id == 7» ✓ como `asLong() == 71` — MATIZ nomenclatura: project id ÚNICO por test para aislar de sesiones moribundas (documentado en el Javadoc de `esperarFiltros`); el plan usa 7 como ejemplo, el aserto Long-igualdad es equivalente.
- «received_at == render del común (segundos)» §2.A-5 ✓ contra `t.format(LoginController.EXPIRES_AT_FORMATTER)` con `t = ahora().withNano(0)`. MATIZ FUERTE (positivo): el stub `FilaBatch` lleva `received_at` = instante FIJO `.123456Z` ≠ `t` — el aserto pinna que el top-level sale del parámetro COMÚN del común del lote (seam), NO del instante del `FilaBatch` (si la implementación renderizara el instante de la fila, el frame sería `2026-10-25…` y FAILearía). Más fuerte que la igualdad trivial; sin riesgo de falso-PASS por el truncado (t ya es a segundos).
- «CADA elemento con 11 claves del LogDto EN ORDEN, nulls incluidos» (§2.A-3 / D-C7-1) ✓: `containsExactlyElementsOf(claves11)` en bucle sobre todos los elementos + `metadata`/`exception` `isNull()` presentes.
- «orden [12301, 12302]» ✓ (→ [711, 712]): pinado por `logs[0].message == "primer log"` (único de 711) + `logs[1].id == 712` — MATIZ: la lista COMPLETA de ids EN ORDEN no se hace containsExactly sobre los dos ids (el par (mensaje-0, id-1) basta para distinguir las dos permutaciones posibles de un lote de 2, así que la fuerza es equivalente AQUÍ; en lotes mayores la pinna E7.3/E7.19).

### QP-7.3 — E7.3 `batchDeUnoYOrdenDelLote` — **CUBIERTO** (1 matiz)
`e73BatchDeUnoOrdenDelLoteYIdsVacioSinFrame`.
- «lote [501] → frame de 1 log» ✓ (`logs` hasSize(1)).
- «lote [5,2,9,1,7] → frame PRESERVA el ORDEN DE ENTRADA» (§2.A-4) ✓: `containsExactly(5,2,9,1,7)`.
- «batch_id DISTINTOS entre frames» ✓ (`isNotEqualTo(batchIdUno)`).
- «ids vacío → CERO frames (R7.17)» ✓: `pollTexto(NADA_MS)` null + `verify(porIds, times(2))` — el mock acumula SOLO las 2 invocaciones legítimas; la vacía retorna ANTES de llegar a `porIds`. Aserto extra FUERTE (el plan no pedía el verify) — positivo, pinna la distribución interna.
- MATIZ (H16): el plan pedía «mock devuelve filas DESORDENADAS» en E7.3; aquí el stub de 5 filas lleva las filas EN ORDEN de ids (el orden del mock ≡ orden de entrada). El REORDEN-EN-JAVA con filas desordenadas lo pinna E7.19 [L] (mock 2,3,1 → salida [3,1,2]) — absorción documentada, no hueco del aserto de orden (el frame-preserva-entrada queda pinado).

### QP-7.4 — E7.4 `setFiltersPorSesion` — **CUBIERTO** (1 matiz)
`e74SetFiltersPorSesionTresClientes`.
- «lote 7 → frame SOLO a c1; c2 nada; c3 SIN set_filters NADA» (§2.A-7 / S7-10) ✓: tres clientes simultáneos, `awaitFrame(e1)` + `pollTexto(e2/e3, NADA_MS)` null.
- «lote 8 → SOLO c2» ✓ (lote de 74).
- «c2 recalifica a project_id=7 en caliente → siguiente lote 7 llega a c1 Y c2» ✓ (`esperarFiltros(73, 2)` + ambos `awaitFrame`).
- «el server NO responde nada al set_filters» (§2.C-4) — MATIZ (H7): sin aserto DEDICADO de ausencia tras `sendText(set_filters)`; la pinna indirecta: `awaitFrame(eX)` pide `contains("batch")`, así que una eco/respuesta del server al set_filters contaminaría el primer frame esperado de un lote → FAIL; las ventanas NADA_MS tras cada lote acotan. Indirecta, no directa.
- MATIZ nomenclatura: proyectos 7/8 del plan → 73/74/75/76/78 en el test (proyecto ÚNICO por test — misma decisión que QP-7.2, coherente en la suite).

### QP-7.7 — E7.7 `handshake401PreUpgradeTresCausas` — **PARCIAL** (H5)
`e77Handshake401PreUpgradeTresCausasSinOraculo`.
- Las TRES causas con estímulos del plan ✓: (a) sin `?token`, (b) expirado `new TokenService(jwtSecret, -8)` (patrón TokenServiceTest, misma firma del contexto), (c) inválido basura — MATIZ: el plan daba «basura/otra clave»; se pinna SOLO basura (un token de otra firma no se prueba; equivalente de fuerza, sin aserto perdido para el status).
- «status 401 EN LAS TRES, NUNCA 101» ✓ sin oráculo de causa (§2.B-1): el MISMO aserto (`WebSocketHandshakeException → getResponse().statusCode() == 401`) iterado sobre los 3 estímulos — jamás se distingue la causa por la respuesta (anti-oráculo R-7.d ✓); NUNCA-101 queda pinado por excepción (un upgrade consumido no lanzaría `WebSocketHandshakeException`).
- HUECO (H5, MEDIA): «SIN cuerpo JSON, SIN `invalid_token`, jamás el entry point REST, sin recursos del handler» — NO pinado en [L] (el aserto del plan lo declara; el JDK no expone el body del fallo de upgrade fácilmente, pero el aserto tampoco se intenta) NI en [S] (E7.9 lee la LÍNEA DE STATUS y headers, nunca el cuerpo). La anti-confusión con el 401 REST del panel queda a revisión de código. Ver §6.

### QP-7.8 — E7.8 `originValidada` — **CUBIERTO** (1 matiz)
`e78OriginValidadaExactaYJamasComodin`.
- (a) `Origin: http://evil.example` → **403** pre-upgrade ✓ (comparación EXACTA del literal del header, sin normalizar).
- (b) Origin == frontend-origin (por properties `projectlog.security.frontend-origin=http://consola-test.example`, R7.29) → 101 + ciclo `set_filters`→`batch` NORMAL ✓ (conecta CON Origin, set_filters 77, frame recibido, `batch_id` regex ✓).
- (c) SIN header Origin → 101 — MATIZ (H6): NO hay conexión dedicada sin Origin DENTRO de este método; el aserto se cumple POR ABSORCIÓN — todas las demás conexiones de la suite [L] (`conectar(...)`) van sin Origin y completan upgrade + frames (el aserto `handler.sesionesVivas() isNotEmpty` pinea que el upgrade ocurrió). La evidencia existe en la MISMA suite, pero el sub-caso no es autónomo.
- «jamás `"*"`» ✓ implicitamente por (a): una aceptación comodín haría pasar evil → FAIL.

### QP-7.12 — E7.12 `cierrePorLatidoYLimpieza` — **CUBIERTO**
`LatidoYBackpressureTest.e12LatidoCierraMuda1001LimpiaYElRealPermaneceYReconexion` (heartbeat 1 s / idle 2 s por properties — R7.29, `application-test.yml` untouched ✓).
- (a) MUDA = mock de `WebSocketSession` registrado en el mapa REAL vía `handler.afterConnectionEstablished(mock)` (exactamente el patrón del plan — el JDK no puede simular mudez; el Javadoc del test deja constancia) ✓; el scheduler REAL cierra con `verify(muda, timeout(9000)).close(CloseStatus.GOING_AWAY)` → **1001** (§2.C-2) ✓.
- Limpieza (R-7.c) — MATIZ documentado: `afterConnectionClosed` (ÚNICO punto de remove) es disparado por el CONTENEDOR; para una mock no hay contenedor, así que el test SIMULA el dispatch (`handler.afterConnectionClosed(muda, GOING_AWAY)` a mano) y pinea el REMOVE con `esperarFueronDelRegistro`. El Javadoc del test lo declara explícitamente: el close 1001 REAL sí es ejecutado por el scheduler real y pinado arriba. Honesto, no oculto — veredicto matiz, no brecha.
- (b) cliente JDK REAL PERMANECE tras varios ciclos ✓: `sleep(4200)` (≥4 ticks con heartbeat 1 s) + `isInputClosed/isOutputClosed == false` + `cerrado` no completado — aserto de NO-cierre (la API `java.net.http.WebSocket` no tiene `isOpen`; sustitución documentada).
- (c) cierre limpio → el remove lo hace el CONTENEDOR real (sin simulación: espera que ninguna `StandardWebSocketSession` quede en el registro) + NUEVA conexión de CERO completa handshake + set_filters + frame (project 812, `type == "batch"`) — S7-11 ✓.

### QP-7.13 — E7.13 `cierre1013AmbosLimites` — **CUBIERTO** (1 matiz)
`e13Cierre1013AmbosLimitesYLosDemasIntactos` (send-time 1 ms / buffer 1 byte por properties).
- (a) sesio LENTA = mock con `sendMessage` bloqueado SOLO en `TextMessage` (latch — los pings 0x9 pasan, el scheduler monohilo jamás se bloquea — decisión fina y documentada), envuelta en el DECORADOR REAL de la pila (`envueltaDeId` sobre `handler.sesionesVivas()`): `esperarInFlight` (sendStartTime > 0) → tick del scheduler → `SessionLimitExceededException` (en el hilo del scheduler) → verify `close(1013)` ✓ + aserto NEGATIVO extra `never().close(GOING_AWAY)` (el latido no GC-a a la lenta — cada tick muere antes por sendTime; pin fuera del plan, positivo).
- Limpieza R-7.c: dispatch SIMULADO como E7.12a (documentado en Javadoc) + `esperarFueronDelRegistro`.
- (b) bufferSizeLimit — MATIZ (H15): NO sobre el decorador del contexto (con send-time 1 ms la barrera de TIEMPO dispara siempre primero por construcción — el comentario lo justifica); el test construye un DECORADOR REAL de la MISMA clase `ConcurrentWebSocketSessionDecorator(lentaB, 30_000, 1)` con el buffer límite del CONTEXTO (1 byte), pina `SessionLimitExceededException` POR BUFFER en el hilo llamador, y dispara el cierre por el MISMO camino del broadcaster `handler.cerrarPorLentitud(envueltaB)` → verify `close(1013)`. La barrera dual queda pinada, pero el camino broadcaster→decorador-del-contexto para el caso BUFFER no (el caso TIEMPO sí va por el camino real íntegro).
- (c) tras el 1013 de la lenta: broadcast posterior → la SANA recibe frame INTACTO (`type == "batch"`, project 901, `received_at == t2.format(formatter)` — §2.A-5 ✓) y `onLotePersistido` retorna LIMPIO en <500 ms (R7.23 — nunca bloquea ni propaga) ✓. Detalle de higiene de test: la sana YA recibió el frame del lote anterior y el test la DRENA antes del aserto (evita falso-positivo leyendo el frame viejo) ✓.

### QP-7.16 — E7.16 `mensajesNoContratoIgnoradosYsinReplay` — **CUBIERTO**
`e76NoContratoIgnoradoSinCierreYVigentesConservados` + `e76eSinReplayTrasDesconexion` (QP-7.16 repartido en 2 métodos — absorción interna del QP, cobertura completa).
- (a) texto plano → nada + sesión sigue ✓ (después el lote llega).
- (b) `{"type":"ping"}` → nada ✓.
- (c) frame BINARIO (R7.12) → nada ✓ (`sendBinary(8 bytes)`).
- (d) inválidos tras un set_filters VÁLIDO → sesión sigue, SIN cierre, CONSERVA vigentes (§2.C-4): los 5 del plan (`project_id:"abc"`, `level:"error"`, clave extra `limit`, JSON malformado `{`, valores `""` → `environment:""`) + EXTRA del writer (`environment:true` booleano — no String ni null) — tras cada inválido, un lote llega según el VIGENTE ✓ (bucle con `awaitFrame(...) contains("batch")`).
- (e) sin replay (R7.21): `sendClose` limpio → lote emitido SIN oyentes → NUEVA conexión + set_filters de nuevo (project ÚNICO 78 para no heredar la sesión moribunda del 76) → NADA de replay (`pollTexto(NADA_MS)` null) → lote NUEVO SÍ llega ✓ (pareja E7.11 [S]).

### QP-7.18 — E7.18 `regresionSuite146Verde` — **EVIDENCE-ASSIGNED (pendiente de ejecución)**
Sin fichero propio (por diseño del plan): la certificación vive en los gates T7.17 (pre-smoke MANDATORIO) y T7.23 (post-smoke). Espera auditora: **146 + 26 = 172 verde** (26 métodos C7 nuevos, sección §1) y NINGÚN aserto E6.x editado (inverificable aquí por aislamiento — es el gate de qa-run). Los E6.x del controller byte-compatibles tras extraer el parser (R7.28) dependen de que `FiltrosConsultaParseTest` + los E6.x sin edición convivan — no hay conflicto de paquetes (parser en `query/`, tests E6.x del controller intactos por hipótesis de qa-run).

### QP-7.19 — E7.19 `pinSqlPorIdsYOrden` — **CUBIERTO** (1 matiz)
`PorIdsSqlPinTest` (7 métodos, MISMO paquete `query/repository`, JUnit+Mockito puro sin Spring/BD — patrón `LogsRepoSqlTest`):
- Literal EXACTO de `SELECT_POR_IDS` (R7.25) ✓ — `isEqualTo` byte-a-byte + `startsWith` de las 11 columnas en orden + `contains("FROM logs WHERE id = ANY (?)")` + `doesNotContain("ORDER BY")` + `doesNotContain("array_position")` + **1 placeholder** contado desde el literal (.chars() filter '?') — todos los sub-asertos del plan ✓.
- Firma `porIds(List<Long>)` ✓ por uso compilado; record `FilaBatch(LogDto, OffsetDateTime received_at)` ✓ por reflexión SÓLO de nombres/tipos de los record components (`log`, `received_at`) — MATIZ: reflexión mínima (nombres/tipos, sin instanciar), aceptable dentro del patrón (los pins SQL de C6 se hacían sin reflection; aquí es sobre la FIRMA, no sobre SQL).
- Reorden en Java ✓: filas DESORDENADAS 2,3,1 del stub JdbcTemplate → `porIds(List.of(3,1,2))` → salida [3,1,2] ✓ + mensaje por fila (mapping delegante por fila correcto).
- Faltantes IGNORADOS sin error ✓: `[3,9,1] → [3,1]`.
- El MISMO `FILA_DETALLE` sirve al delegante — MATIZ (H11): el pin es DEL COMPORTAMIENTO (el mapper ejecutado sobre la fila mock renderiza C2 truncado `2026-10-16T22:31:05Z` + el instante RAW `.123456` en `FilaBatch.received_at` + LogDto completo de 11) — NO pinna la identidad de constants (`FILA_BATCH == FILA_DETALLE` con `isSameAs`, que sería el pin de LITERAL del delegante). Cualquier mapper duplicado con el mismo comportamiento pasaría.
- Guard `porIds(null)`/vacía → vacío + `verify(..., never()).query(...)` sin tocar la BD ✓.
- EXTRA del writer: `binderUsaCreateArrayOfBigintConElArrayCompleto` — captura del `PreparedStatementSetter` y pin del bind `createArrayOf("bigint", …)` + `setArray(1, array)` (R7.25 menciona el bind; test extra POSITIVO, no pina cosas fuera del plan).

### QP-7.20 — E7.20 `pinParserExtraidoSemanticaADR7` — **PARCIAL** (H12)
`FiltrosConsultaParseTest` (10 métodos, unit puro sin Spring/BD):
- Los 6 checks con `FiltroInvalido.code/message/field` VERBATIM ✓: check 2 (`filter_invalid_value` / «El filtro project_id debe ser un número» / field project_id), check 3 (environment, message verbatim de la lista de enums), check 4 (level, verbatim), checks 5/6 (from/to naive sin offset — patrón S5-5, «El filtro from/to debe ser una fecha ISO-8601 con zona horaria»), check 7 (`filter_from_to_inverted`, message verbatim, **`field isNull()`** — R6.22 ✓) + frontera legal `from == to` no lanza ✓.
- HUECO (H12, MEDIA): «los 6 checks EN ORDEN, primer-fallo-gana» — el orden se pinna SOLO con el par 2-vs-3 (`check2ProjectIdNoNumericoPrimeroEnOrden`: project_id Y environment ambos inválidos → gana project_id). Los pares 3-vs-4, 4-vs-5, 5-vs-6 NO están pinados: un desorden interno de los checks 4↔5 o 5↔6 pasaría la suite (cada check individual es correcto). Los literales byte-idénticos están; el ORDEN GLOBAL no. Ver §6.
- «caso feliz tipado completo, patronTexto Y texto CRUDO SEPARADOS» (D-7.13) ✓: `Filtros` tipado — projectId Long 7, enums, `OffsetDateTime` de from/to, `texto()=="timeout"` Y `patronTexto()=="%timeout%"` SEPARADOS.
- Aguja `_` ✓: patron `%a\_b%` (escape) vs `texto()=="a_b"` (crudo SIN escape) — el stream matchea el crudo (S7-10).
- `text=""` → patron `%%` + crudo vacío SIN case especial (S6-2) ✓.
- `to` null → null ✓ (+ EXTRA: TODO el resto de campos null cuando no hay filtros — patronTexto null incluido).
- «patronIlike re-corrida BYTE-A-BYTE (matriz, sin tocar su test original C6)» ✓: `a_b→%a\_b%`, `100%→%100\%%`, `a\b→%a\\b%`, `a_b\c→%a\_b\\c%` (solo `\`-primero), `\_%→%\\_\%%`, `""→%%` — la matriz equivale a las de C6; el test original `FiltrosConsultaTest` queda INTOCABLE ✓ (verificado solo por nombre/ausencia de edición en este auditor — la NO-edición es gate de qa-run).

## 3. Auditoría por aserto — bloque [S] (`scripts/qa_c7_smoke.sh`)

Estructura del smoke (coherente con el plan §1/§4): recomposición ÚNICA
`compose_ up -d --build --force-recreate` (NUNCA `down -v` — verificado: el
`down` del wrapper es SOLO la rama de timeout sin uso) → `esperar_healthy`
(sondeo `GET /me` → 401 `invalid_token`) → `pila_saludable` 3×healthy por
podman (NUNCA /actuator) → smoke-of-the-smoke `/me` sin token → login (JWT →
tmpdir 700) → pre-fase: POST project 201 + regenerate + **R7.30 ws-console.html
publicado** (GET 200 + 'CONSOLA') → 9 cases en orden cronológico §5.3 (E7.9 →
E7.17 → E7.1 → E7.5 → E7.6 → E7.15 → E7.10 → E7.11 → E7.14) → veredictos
`E7.n: PASS | FAIL | BLOQUEADO (detalle)` ordenados `sort -V` → exit 0/1/2 ✓
(FAIL manda sobre BLOQUEADO). Guard R-7.j en pre-fase ✓ (CON_ORIGIN=0 →
recomposición corre, WS BLOQUEADO con nota S7-12, `ejecutar_pre=0`, exit 2);
CERO mitigation `"*"` en todo el script ✓. Cliente RFC stdlib `ws7_client.py`
en heredoc (handshake con `Sec-WebSocket-Key`, Accept verificado SOLO en modo
hs, frames MASKED cliente→servidor, responde 0xA a 0x9, lee la línea de
status del proxy sin reescribirla). T_STAMP común de los ingestas (regla 9).
Needles sufijados `smoke-c7-<TS>-*` ✓.

### QP-7.1 — E7.1 — **PARCIAL** (H1, H2, H3)
`case_e7_1` + `verificar_frame`:
- 101 ✓ (gate `hs-ok` — el cliente long solo sigue tras status 101).
- H3 (BAJA): «Sec-WebSocket-Accept correcto (SHA-1+base64)» asertado en el plan PARA ESTE CASO — el modo `long` NO verifica el Accept (solo el modo `hs` de E7.9(c) lo verifica, en la MISMA pila/ruta minutos antes). Cubierto transversalmente EN LA MISMA corrida, no dentro del caso.
- Shape §3.5 (§2.A-1/3) ✓ FUERTE: `verificar_frame` parsea con `object_pairs_hook=list` y pina `top == [type,batch_id,project_id,received_at,logs]` EN ORDEN + 11 claves de CADA log EN ORDEN (las de D-C7-1, con `project_id`) — el error imprime la lista real.
- «logs.0.message == needle» ✓.
- H2 (BAJA): «batch_id v4» NO verificado en [S] — `verificar_frame` mira la clave por orden pero ni su formato ni su presencia semántica (regex). Vive en [L] (QP-7.2).
- «received_at == truncado-a-segundos del psql» §2.A-5 / D-C7-2 ✓ EXPLÍCITO: `rec_micro` (to_char UTC con `.US`) recortado a segundos y comparado contra el frame (`ra_frame == "${ra_psql%%.*}Z"`); FAIL honesto si alguno «err».
- H1 (MEDIA): «BD: delta = la aguja» implementado como `delta_cuenta == base+1` PERO `base` es **`frames_count`** (número de frames recibidos por la sesión), NO un snapshot `delta_cuenta` pre-ingesta. Sound SOLO porque el proyecto del smoke nace vacío y la sesión aún sin frames (ambos 0 a la entrada). Un falso-PASS sería posible con un proyecto pre-poblado o frames asimétricos a filas (1 frame por lote vs N filas). El patrón canon (snapshot psql antes → delta después) NO se aplica en ESTA fase. Ver §6.

### QP-7.5 — E7.5 — **PARCIAL** (H4)
`case_e7_5`:
- 202 + `accepted == 3` ✓; EXACTAMENTE 1 frame ✓ (delta frames == +1, extra FAIL si >1).
- «3 logs EN ORDEN del lote (ids CRECIENTES == orden del RETURNING, psql)» ✓: `frame_ids == string_agg(id ORDER BY id ASC)` de psql + `msgs == "$NEEDLE_B,$NEEDLE_B2,$NEEDLE_C"` — el orden de ENTREGA del frame igualado al ASC de la BD (SERIAL garantiza el RETURNING; comparado contra BD, nocontrafe).
- «batch_id ≠ NINGUNO previo» — MATIZ: comparado SOLO contra el ÚLTIMO frame previo (índice base), con OMISIÓN VACUA si `base == 0` (primer frame del fichero — el check se salta). En la cronología del smoke E7.1 ya produjo un frame (base ≥ 1), así que la omisión no ocurre en la corrida canónica; sigue siendo una debilidad del guion.
- «shape §3.5» — MATIZ: E7.5 NO invoca `verificar_frame`; pina batch_id-distinto, ids/msgs orden y (implícitamente) parse JSON. El shape completo de ESTE frame vive en E7.1 (misma sesión, `verificar_frame`) — absorción de caso, gravedad baja.
- H4 (MEDIA): «delta BD = 3» SIN snapshot+delta (case_e7_5 NO re-cuenta con `delta_cuenta`); evidencia indirecta (los 3 ids REALES en psql por correlation). Ver §6.

### QP-7.6 — E7.6 — **CUBIERTO**
`case_e7_6` (parejas 3 y 9):
- (a) `level=ERROR` → ingesta needle WARN → NO llega ✓ (frames_count == base) + **psql CONFIRMA la fila** (`id_por_corr != err` — descartada solo del stream) + ventana de AUSENCIA extra de 5 s (frame-tardío) ✓ — el servidor JAMÁS contesta (R7.11).
- (b) recalifica `level=WARN` → siguiente lote WARN SÍ llega ✓ (frame con `logs[0].message == needle`).
- (c) recalifica a OTRO project (999999) → NADA ✓ (+ ventana 3 s).
- «matching case-sensitive: ERROR no matchea WARN» ✓ — en el plan era el sentido filtro(UPPER) vs fila(WARN): materializado por (a) (el filtro ERROR no atrapa la fila WARN); la dirección minúscula-filtro(`error`) vs fila-ERROR del ESTRICTO case-sensitive NO se ejercita en ESTE caso ([S]) — QP-7.6 del plan literal «ERROR no matchea WARN» queda pinado; el aserto §2.A-7 «estrictos case-sensitive» en la dirección opuesta queda SIN pila (ver §6, H13).
- Limpieza restauradora: vuelve al project del smoke para E7.15/E7.10/E7.11 ✓ (higiene del guion).

### QP-7.9 — E7.9 — **CUBIERTO**
`case_e7_9` (parejas 4 y 5):
- (a) sin token → línea de status **401** tal cual del proxy (§2.B-3 — el cliente la lee parseada, sin reescritura) ✓.
- (b) `Origin: http://evil.example` con token → **403** ✓.
- (c) sin Origin con token → **101** + `Sec-WebSocket-Accept` VERIFICADO matemáticamente (SHA-1(key+GUID)+base64 en `hs_solo`) ✓ — aquí sí pinna el Accept (compensa el H3 de QP-7.1 en la misma corrida).
- Nota §2.B-1: el cuerpo del 401 no se mira (también H5 para la fila [S]).

### QP-7.10 — E7.10 — **CUBIERTO** (2 matizes, H8)
`case_e7_10` (parejas 6a y 10):
- «frames opcode 0x9» ✓: el cliente imprime `ping <t>` SOLO ante opcode 0x9; `pings_count ≥ 2` con espera honesta (FAIL `pings<2`).
- «el cliente responde 0xA» — MATIZ: la respuesta está en el COMPORTAMIENTO del cliente (`send_frame(sock, 0xA, payload, 5)`), sin evidencia propia en el fichero de eventos; la evidencia INDIRECTA es la supervivencia de la sesión ≥65 s con pings repetidos (un servidor que no recibiera el pong mataría a 1001 y los cases siguientes FAILearían). Sin pin directo.
- «intervalos ≈30 s (±5 s)» — MATIZ (H8): la ventana implementada es **24..36 s (±6)** — 1 s más laxa que el plan; el DEFAULT 30 s (R7.29) queda igualmente pinado por el centro de la ventana.
- «NINGÚN mensaje JSON distinto de batch» (§2.A-6, aserto de AUSENCIA) ✓ FUERTE: recorre TODAS las líneas `frame ` y FALLA ante cualquier JSON de type ≠ batch O no-JSON-parseable (ambos marcados).
- DEFAULT y guard R-7.j ✓ (heredados del bloque de pre-fase).

### QP-7.11 — E7.11 — **CUBIERTO**
`case_e7_11` (pareja 6c):
- Close limpio del cliente (comando CLOSE → frame 0x8 código 1000; el comentario deja constancia de que el «cerrado-por-comando» se imprime ANTES de esperar el eco — race fix documentado del run 3) ✓; gate de espera real no-cursor (`cerrado-por-comando`).
- «psql cuenta filas del gap» ✓ — delta relativo: `base_gap` antes de la ingesta del gap → `+1` EXACTO (Honesto: aquí SÍ hay snapshot+delta — a diferencia de H1/H4).
- «NUEVO handshake + set_filters DE NUEVO → próximo lote SÍ llega» ✓ (`iniciar_long2` con `sf_json` inicial + ingesta `NEEDLE_RECON` + espera del frame).
- «NINGÚN frame re-emitido del gap (sin replay)» ✓ — `sleep 5` + `frames_count == 0` en la reconexión — aserto de AUSENCIA con ventana honesta.
- «gap cubierto por keyset C6 (CITADO)» ✓ — coherente: NO se re-prueba (E6.16/E6.23).

### QP-7.14 — E7.14 — **CUBIERTO con tolerancia ampliada (H9)**
`case_e7_14` (pareja 7, tolerancia R-7.k):
- Cliente NO consumidor con set_filters matcheando ✓ (modo `lento`: set_filters, luego dormir 70 s SIN leer).
- (a) «202 NUNCA bloquean > ~2 s» ✓: 20 ingestas de ~30 KiB con gate `dt ≤ 2500 ms` por ingesta — H8-menor: 2.5 s es la lectura razonable de «~2 s» (más laxa que 2.0 exactos; el plan escribe «~»).
- H8-menor (BAJA): tamaño del mensaje ~30.1 KiB (needle + 30000 'x') — el plan citaba «32 KiB (máximo por campo, §2.7)»: no llega al tope exacto; suficiente para el modelo de backpressure, MATIZ documentado.
- (b) «las sesiones sanas SIGUEN recibiendo» ✓: la reconexión de E7.11 (`F_LONG2`) recibe ≥10 frames de las 20 ingestas — umbral 10/20 decisión del writer (razonable; no es la literalidad «SIGUEN» absoluta, pero es un pin concreto y suficientemente fuerte).
- (c) 1013 del lento: TRES ramas — (i) `close 1013` → PASS; (ii) SIN close/fin sin código → PASS CON NOTA EXACTA del plan «1013 no materializado en pila — cubierto por pareja [L] E7.13 (R-7.k)»; (iii) close con OTRO código (p. ej. 1001 por latido del lento sin leer pings durante 70 s) → PASS CON NOTA «cubierto por [L] E7.13». MATIZ (H9, MEDIA): la rama (iii) EXTENDE R-7.k a ANY código ≠ 1013 — el plan solo toleraba «los defaults NO lo materializan» (ausencia); un 1011/1006/1009 distinto de 1001/1013 también caería en PASS-con-nota. La nota al menos NOMINA el código observado (no silencia el valor). (a)/(b) NO se silencian por la tolerancia (comentario explícito del script) ✓.

### QP-7.15 — E7.15 — **PARCIAL** (H10)
`case_e7_15` (sin pareja — derivado del flujo 7 paso 4, como el plan declara):
- PATCH `{"status":"ARCHIVED"}` → 200 gate ✓; ingest → **403** ✓.
- H10 (BAJA): «CUERPO EXACTO `{"code":"project_archived"}`» — el script pina SOLO la PRESENCIA de `"code":"project_archived"` (grep); NO la EXACTITUD del cuerpo (un 403 con `message` añadido u otra clave pasaría). En C2/Q19-herencia la familia project_archived tiene cuerpo único pero el aserto exacto del plan no se materializa al completo.
- «psql SIN fila nueva (no hay COMMIT ⇒ sin seam ⇒ sin frame)» ✓ — delta relativo con snapshot ✓ (`base = delta_cuenta` antes; igualdad tras la ingesta 403).
- «SIN frame en ventana» ✓: `sleep 4` + `frames_count == base` (ventana de AUSENCIA completa del registro).
- «desarchivar → ingest → frame SÍ llega» ✓: PATCH ACTIVE 200 + 202 + frame con espera honesta de 20 s.

### QP-7.17 — E7.17 — **CUBIERTO**
`case_e7_17` + Q20 de `qa_c2_smoke.sh`:
- Invoca `python3 scripts/ws_echo_test.py --solo-handshake --token "$(cat "$F_TOKEN")"` TAL CUAL — SIN edición del artefacto C1 ✓ (timeout 200, log al tmpdir, PASS solo exit 0 — los asertos internos de 101/Accept/timeouts 3600 viven en el artefacto C1, forma aceptada por el plan).
- BLOQUEADO honesto si el artefacto no existe ✓.
- Q20 editado (R-7.f / D-7.14): misma forma + guards del plan ✓ — `CON_ENV` → BLOQUEADO; `asegurar_healthy` → BLOQUEADO; `! -s "$F_TOKEN"` → BLOQUEADO («el handshake C7 responde 401»); **`CON_ORIGIN != 1` → BLOQUEADO «sin FRONTEND_ORIGIN el handshake C7 cierra (S7-12)»** — los 4 criterios del plan §4 aplicados ✓.

## 4. Transversales 2.A–2.D y parejas del spec §2.5

| Transversal / pareja | [L] | [S] | Veredicto |
|---|---|---|---|
| **2.A-1** 5 claves EN ORDEN | E7.2 (`containsExactly`) | `verificar_frame` (object_pairs_hook=list, igualdad de listas) | CUBIERTO |
| **2.A-2** batch_id v4 + unicidad | regex E7.2/E7.8(b); unicidad E7.3 (`isNotEqualTo`) | regex NO (H2); unicidad vs ÚLTIMO previo con omisión vacua si base==0 (E7.5) | PARCIAL (H2) |
| **2.A-3** 11 claves + nulls incluidos | E7.2 (bucle containsExactly + isNull ×2) | `verificar_frame` (11 en orden por log) | CUBIERTO |
| **2.A-4** orden del lote = entrada | E7.3 (5,2,9,1,7), E7.19 (reorden con filas 2,3,1 → [3,1,2]) | E7.5 (frame == ASC del RETURNING psql + msgs en orden del payload) | CUBIERTO |
| **2.A-5** truncado a segundos (D-C7-2) | E7.2 (render del común), E7.13(c) (`t2.format`) | E7.1 (`rec_micro` recortado == frame; comparado contra PSQL TRUNCADO, nunca crudo) + E7.13 en sana (no [S] — es [L]) | CUBIERTO |
| **2.A-6** UN TextMessage / jamás otro JSON | E7.2 (2.º poll null), E7.16 (no-contrato) | E7.10 (no-batch sobre TODOS los frames) | CUBIERTO |
| **2.A-7** matching en servidor | E7.4 (conjunción project_id + sin set_filters), E7.16 (vigentes) | E7.6 (level STRICTO: ERROR vs WARN) | PARCIAL (H13 — from/to/text del MATCHING sin ancla; ver §6) |
| **2.B** anti-oráculo de causa (handshake) | E7.7 (3 causas, mismo status sin oráculo) | E7.9 (a/b/c con la línea del proxy sin reescribir) | PARCIAL (H5 — sin cuerpo JSON/invalid_token/recursos sin pin) |
| **2.C** códigos de cierre (1013/1001) + limpieza + sigue-viva | E7.12 (1001 + limpieza), E7.13 (1013 a/b + intactos), E7.16 (sigue viva) | E7.11 (0x8 1000 + gap), E7.14 (1013-R-7.k) | CUBIERTO |
| **2.D** conteos RELATIVOS | — | E7.11 (snapshot+delta ✓), E7.15 (snapshot+delta ✓) | PARCIAL (H1/H4 — E7.1 usa base=frames; E7.5 sin delta) |
| **2.D** needles sufijados + `_` por `=`/`position()` | E7.20 (aguja en parser) | TODOS los needles `smoke-c7-<TS>-*`; en psql los `_` van SOLO por `correlation_id = '…'` (sin LIKE con `_` crudo — verificado: CERO `LIKE` en el SQL del smoke) | CUBIERTO |
| **§4 Higiene** tmpdir 700 + trap + JWT/key no consola | — | `chmod 700` + trap EXIT + PY_TOKEN/PY_KEY por env del proceso + F_TOKEN/F_KEY1 del tmpdir | CUBIERTO (el grep post-ejecución plk_/eyJ NO vive en el script — H14) |

**Parejas mock↔pila (col. «Pareja» del plan vs casos reales) — 10/10 cubiert
as**, con las absorciones EXACTAS que el plan §1 declaró, verificadas 1:1:

| # | [L] | [S] real | Veredicto |
|---|---|---|---|
| 1 | E7.2 (frame §3.5) | E7.1 (`case_e7_1`/`verificar_frame`) | ✓ (Accept por E7.9c, H3) |
| 2 | E7.3 (batch de 1 / orden) | E7.5 (`case_e7_5`) | ✓ |
| 3 | E7.4 (set_filters por sesión) | E7.6 (`case_e7_6`) | ✓ |
| 4 | E7.7 (401 ×3) | E7.9(a) | ✓ (cuerpo ausente — H5) |
| 5 | E7.8 (Origin) | E7.9(b)(c) | ✓ |
| 6 | E7.12 (latido) | E7.10 (↔E7.12a pings) + E7.11 (↔E7.12c reconexión) | ✓ (reparto declarado respetado) |
| 7 | E7.13 (1013 dual) | E7.14 (tolerancia R-7.k) | ✓ con MATIZ H9 |
| 8 | E7.19 (pin porIds) | E7.5 (ids reales/orden) | ✓ |
| 9 | E7.20 (parser ADR-7) | E7.6 (level STRICTO en pila) | ✓ |
| 10 | E7.16 (tolerancia/sin replay) | E7.10 (no-JSON literal — parcial OK) | ✓ (sin replay también ↔E7.11 según el spec — las dos filas [S] presentes) |

E7.15 y E7.17 sin pareja ✓ — implementadas como derivado (403 pre-COMMIT ⇒
sin seam ⇒ sin frame + psql SIN fila) y permanente (`ws_echo_test.py`
involucrado SIN edición), tal como el plan §1 los exime.

## 5. Decisiones test-side (del auditor)

1. **Cliente JDK real contra Tomcat REAL con el BD desactivado por properties**
   (`spring.autoconfigure.exclude` DataSource/JdbcTemplate/Flyway + `@MockitoBean`
   de `JdbcTemplate`/`AdminSeeder`/`LogsQueryRepository`) — el patrón exacto
   del plan (R7.33): upgrade real, handler real (`LogSocketHandler`), interceptor
   real, `ObjectMapper` REAL, JWT del `TokenService` del contexto, y el lote
   disparado INVOCANDO el bean real `LiveBroadcaster.onLotePersistido(...)`
   (broadcast a mano, sin BD). `application-test.yml` INTOCABLE ✓ — todo via
   `@SpringBootTest(properties=…)`.
2. **`esperarFiltros` — sondeo determinista por el seam de paquete**
   (`handler.sesionesVivas()` + ATTR_FILTROS bajo `synchronized`) en lugar de
   sleeps nominales; proyecto ÚNICO por test para aislar de sesiones
   moribundas. Los sets de filtros incluyen las 6 claves con nulls (forma
   contractual completa).
3. **Mocks mudo/lento sobre `WebSocketSession` registrados en el mapa REAL**
   (exactamente lo que el plan exige — la imposibilidad del JDK está cubierta
   por diseño R7.33): la muda sin stub de pong; la lenta con `sendMessage`
   bloqueado SOLO en TextMessage (latch) para que el scheduler monohilo no
   quede tomado. El dispatch de `afterConnectionClosed` para mocks se SIMULA
   (el contenedor no existe para una mock) — documentado en el Javadoc de
   LatidoYBackpressureTest; el close real (1001/1013) es ejecutado por el
   scheduler/handler REALES y es lo pinado con `Mockito.verify(timeout)`.
4. **Barrera (b) del 1013 con decorador construido por el test** de la MISMA
   clase real (`ConcurrentWebSocketSessionDecorator(lentaB, 30_000, 1)`) +
   `handler.cerrarPorLentitud` a mano — justificado: con send-time 1 ms la
   barrera de tiempo dispara SIEMPRE primero en el decorador del contexto
   (checkSessionLimits evalúa tiempo primero). Racionalizable, pero deja el
   camino completo broadcaster→decorador-de-contexto→buffer sin cubrir (H15).
5. **Cliente WS Python heredoc dentro del propio smoke** (`ws7_client.py`,
   stdlib RFC 6455, frames MASKED, responde 0xA a 0x9, lee status del proxy)
   + modos `hs`/`long`/`lento` + FIFO para set_filters en caliente y CLOSE
   (E7.6/E7.11). Race fix documentado (el print de `cerrado-por-comando`
   ANTES de esperar el eco del close — run 3) y fix documentado de
   `frame_campo` requiriendo reenvío del nombre de clave (run 5), y de la
   interpolación de `F_LONG` en los `bash -c` (run 5).
6. **Needles y llaves de captura**: mensajes `smoke-c7-<TS>-{a,b,b2,c,e6w1,
   e6w2,e6p,ar1,ar2,gap,recon,bp}` + `correlation_id` `e7xx-*` DEDICADOS a
   captura psql por `=` (los `_` jamás viajan por LIKE — CERO LIKE en el SQL
   del smoke). `T_STAMP` común a todos los ingestas del guion (regla 9) para
   que los lotes compartan received_at.
7. **Truncado-a-segundos comparado CONTRA PSQL** (D-C7-2): `rec_micro`
   (to_char UTC `.US`) recortado a segundos y comparado contra el frame —
   jamás contra el instante crudo (la precisión completa NO viaja en el
   frame): exactamente la regla §2.A-5 del plan (un FAIL por micros sería
   error del PLAN, no del feature).
8. **Tolerancia R-7.k codificada como ramas** del E7.14(c) — PASS CON NOTA
   verbatim del plan en la rama «sin materializar» + rama EXTRA para otros
   códigos de close, siempre NOMBRANDO el valor observado; (a)/(b) jamás se
   silencian por la tolerancia (comentario explícito).
9. **Guard R-7.j doble** (pre-fase del smoke C7 y Q20 de qa_c2): recomposición
   CORRE aun sin `FRONTEND_ORIGIN` (la pila REST de C2/C6 sigue
   certificable), los escenarios WS van BLOQUEADO con nota S7-12 y exit 2 —
   jamás `"*"`, jamás default inventado (la recomposición en ausencia de
   credenciales se salta con `ejecutar_pre=0` y BLOQUEADO honesto).
10. **Sin Awaitility ni dependencias nuevas** (P-2 cerrado): sondeos con
    `esperar_que`/`esperar(ms, BooleanSupplier)` + queues JDK; `@AfterEach`
    con `sendClose(NORMAL_CLOSURE)` tolerante.
11. **XFAIL**: CERO `@Disabled`/assumptions/xfail en los 4 ficheros; el
    BLOQUEADO-con-nota y el PASS-CON-NOTA son las únicas vías de
    «casi-fail» y ambas están documentadas.
12. **Dudas abiertas para spec (no silenciadas) — resumen**: H9 (la rama
    no-1013 del E7.14(c) extiende la tolerancia), H12 (orden global de los
    checks del parser con solo el par 2-vs-3), H13 (matching de from/to/text
    del broadcast sin test), H1/H4 (conteos relativos con base errónea o
    ausente), H5 (cuerpo del 401 nunca mirado), H11 (pin del delegante
    DEL COMPORTAMIENTO, no de identidad de constants), H16 (mock sin filas
    desordenadas en E7.3 — absorbido por E7.19). NINGUNA exige XFAIL: todas
    son asertos débiles/ausentes, no pines que FAILearían por razón de
    entorno.

## 6. Hallazgos para apply/QA (bucle apply — NO los resuelve test-side)

| # | QP-x / transversal | Hallazgo | Severidad |
|---|---|---|---|
| **H1** | QP-7.1 / 2.D | `case_e7_1` usa `base="$(frames_count ...)"` como «antes» del delta de BD (`[ "$(delta_cuenta)" = "$(( base + 1 ))" ]`) — SNAPSHOT ERRÓNEO (frames, no filas de psql). Sound solo por proyecto-vacío-al-entrar; el patrón canon (snapshot `delta_cuenta` antes de la ingesta) falta | MEDIA |
| **H2** | QP-7.1 / 2.A-2 | Regex de `batch_id` (UUID v4) NO verificada en `verificar_frame` [S] — vive solo en [L] (E7.2/E7.8) | BAJA |
| **H3** | QP-7.1 | «Sec-WebSocket-Accept correcto» del aserto de E7.1: el modo `long` no verifica el Accept; verificado solo en E7.9(c) de la misma corrida (transversal, no dentro del caso) | BAJA |
| **H4** | QP-7.5 / 2.D | «delta BD = 3» SIN snapshot+delta en `case_e7_5` (no re-cuenta; evidencia indirecta por ids psql) — y unicidad de batch_id omitida si `base == 0` (check vacuo del primer frame) | MEDIA |
| **H5** | QP-7.7 + QP-7.9 / §2.B-1 | «SIN cuerpo JSON / SIN `invalid_token` / sin recursos del handler» NO pinado en [L] (solo status) NI en [S] (solo línea de status + headers) — la anti-confusión 401-REST queda a revisión de código | MEDIA (anti-oráculo declarado [M] por el plan) |
| **H6** | QP-7.8(c) | Sub-caso «SIN Origin → 101» sin conexión dedicada en `e78...` — por absorción de las demás conexiones de la suite (sin Origin + frames) y el aserto `sesionesVivas() notEmpty` | BAJA |
| **H7** | QP-7.4 / §2.C-4 | «el server NO responde nada al `set_filters`» sin aserto directo (indirecta: `awaitFrame` exige `contains("batch")`; una eco contaminaría) | BAJA |
| **H8** | QP-7.10 / QP-7.14a | Ventana de intervalos de ping 24–36 s (±6) vs plan ±5 s; respuesta 0xA sin evidencia directa (solo supervivencia de la sesión); mensaje de backpressure ~30 KiB vs «32 KiB» del plan | BAJA |
| **H9** | QP-7.14(c) / R-7.k | La rama de tolerancia acepta CUALQUIER close ≠ 1013 (1001/1006/1011/…) como PASS-CON-NOTA «cubierto por [L]» — R-7.k del plan solo toleraba la AUSENCIA de materialización (1013 no ocurrido); la extensión se documenta con el código observado, pero el plan la contempla | MEDIA |
| **H10** | QP-7.15 | «CUERPO EXACTO `{"code":"project_archived"}`» sin pin de exactitud (grep solo `code`) — un cuerpo con `message` extra pasaría | BAJA |
| **H11** | QP-7.19 | Pin del delegante `FILA_BATCH` DEL COMPORTAMIENTO (mapper ejecutado) — la identidad/simplicidad del LITERAL `FILA_BATCH` (reuso in-situ de `FILA_DETALLE`, R7.26) no es asertada por pin de constants | BAJA |
| **H12** | QP-7.20 | «los 6 checks EN ORDEN, primer-fallo-gana»: SOLO el par 2-vs-3 pinado — un desorden 3↔4, 4↔5 o 5↔6 pasaría la suite (los literales individuales sí están byte-idénticos) | MEDIA |
| **H13** | 2.A-7 / R7.16 / *gap del PLAN* | El MATCHING del stream para `from`/`to` (rango INCLUSIVE sobre el instante RAW, `from == to` LEGAL) y `text` (contains case-insensitive del texto CRUDO, aguja `_` no-escapada en matching) NO tiene ancla en NINGÚN test [L] ni [S] — los casos del plan solo ejercitan project_id (E7.4/E7.6) y level (E7.6). La aguja `_` del MATCHING sí está en E7.20 (patrón vs crudo SEPARADOS) pero la demostración de que el broadcast USA el crudo no existe. La semántica queda delegada en la igualdad-de-fuente (parsear pin + E6.x del SQL); un hueco del PLAN (ningún QP lo pide), no del writer — para spec/archive | MEDIA (hueco de plan) |
| **H14** | §4 Higiene | El grep «0 hits plk_/eyJ» NO está implementado en `qa_c7_smoke.sh` (la higiene estructural sí: tmpdir 700 + trap + env-vars del proceso) — queda como verificación post-ejecución de qa-run, tal como el plan §4 lo describe | INFO |
| **H15** | QP-7.13(b) | Barrera por buffer fuera del camino real del broadcaster (decorador del test + `cerrarPorLentitud` a mano) — la dualidad del 1013 queda pinada como SEMÁNTICA, no como integración | BAJA |
| **H16** | QP-7.3 | El mock del lote de 5 NO lleva filas desordenadas (plan: «mock devuelve filas DESORDENADAS») — el reorden-e-orden-pin queda absorbido por E7.19 (filas 2,3,1 → [3,1,2]) y el pin del frame-preserva-entrada es correcto | BAJA |

## 7. Veredicto

- **Cuenta**: 4 ficheros Maven + 1 smoke nuevo (+ Q20 de qa_c2 editado) —
  **9 cases [S]** en `qa_c7_smoke.sh` (E7.1, E7.5, E7.6, E7.9, E7.10, E7.11,
  E7.14, E7.15, E7.17) y **26 métodos [L]** (7 + 2 + 10 + 7) — **11 QP [L]
  cubiertos + 9 QP [S] cubiertos = 20/20 con evidencia asignada**.
- **Huecos de cobertura**: **CERO** casos QP sin test/live-evidence; las
  10 parejas del spec §2.5 verificación 1:1 contra los casos reales del smoke
  (§4), con E7.15/E7.17 sin pareja como el plan declara. Las absorciones
  del plan (E7.9→parejas 4+5; E7.5→2+8; E7.6→3+9; E7.10/E7.11→6; E7.10→10
  parcial) están TODAS presentes tal cual.
- **Hallazgos**: 16 (H1–H16: 6 MEDIA — H1, H4, H5, H9, H12, H13; el resto
  baja/INFO) — NINGUNO invalida la cobertura; los MEDIA son asertos más
  débiles que el plan (no pines erróneos) y H13 es un hueco del PLAN que
  pide decisión spec. Nada xfail; nada silenciado.
- **Cifra de regresión para QP-7.18**: esperada **146 + 26 = 172 verde**
  (T7.17/T7.23) — pendiente de la ejecución de qa-run.
- **Estado para la ejecución de qa-run**: LISTO — el orden de ejecución del
  plan §4 (mvn verde completo → re-corrida qa_c2 con Q20 → recomposición +
  qa_c7_smoke → mvn final → qa_c3 opcional → frontera) es ejecutable tal
  cual con este inventario; la comprobación higiene plk_/eyJ (H14) hay que
  hacerla post-ejecución como procedimiento de qa-run.