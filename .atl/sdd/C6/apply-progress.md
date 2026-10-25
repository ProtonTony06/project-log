---
project: Project_Log
change: C6
artifact: apply-progress
last_updated: 2026-10-25 (loop-back post-auditoría test-side)
status: draft
---

# Apply progress — C6 · Consulta keyset + filtros + detalle

Estado por tarea (T6.1–T6.25). Este fichero se mantiene durante el apply.
Frontera (R6.3): toda pieza nueva en `com.projectlog.query` (+ tests) y
`scripts/qa_c6_smoke.sh`; CERO ficheros existentes modificados.

| Tarea | Estado | Notas |
|---|---|---|
| T6.1 | **hecho** | LogListItemDto (9 componentes literales, sin exception/metadata) |
| T6.2 | **hecho** | LogDto (11 componentes; metadata como JsonNode — cierre D-S6-c) |
| T6.3 | **hecho** | LogsPaginaResponse {items, next_cursor} |
| T6.4 | **hecho** | CursorCodec: encode composición manual + Base64url sin padding; decode 6 pasos estrechos → CursorInvalidoException única |
| T6.5 | **hecho** | FiltrosConsulta.patronIlike (orden \→%→\_ sin case especial) |
| T6.6 | **hecho** | SELECT_LISTADO / SELECT_POR_ID constants package-private (D-C6-1: 7 CASTs + guard; citado en Javadoc) |
| T6.7 | **hecho** | métodos listar/porId + binding 17 `?` (D-S6-a) + RowMappers 2 separados |
| T6.8 | **hecho** | LogsController: 2 mappings, constants CODE_*/MSG_*, error() réplica C3 |
| T6.9 | **hecho** | 8 checks fail-fast inline en orden (limit String; firma del design §4 cerrada a String) |
| T6.10 | **hecho** | flujo listado: patrón → 1 llamada → trim+next_cursor (raw micros) o null explícito |
| T6.11 | **hecho** | detalle 200/404 not_found sin field; /logs/abc ratificado sin handler |
| T6.12 | **hecho** | DtosLogsPinTest (E6.2) |
| T6.13 | **hecho** | CursorCodecTest (E6.12, pin P-1 byte-exacto) |
| T6.14 | **hecho** | FiltrosConsultaTest (E6.13, orden + contra-caso aguja) |
| T6.15 | **hecho** | LogsQuerySqlTest (E6.11: literal byte-a-byte, 17 `?`, 7 CASTs, guard, no-ESCAPE, no fuga) |
| T6.16 | **hecho** | LogsControllerMvcTest fixture + E6.15 (401 invalid_token ambas rutas; X-API-Key no autentica panel) |
| T6.17 | **hecho** | E6.1 (decode-back del next_cursor, D-S6-b) + E6.14 (limit 50/200 al repo) |
| T6.18 | **hecho** | E6.3/E6.4 (detalle 11 campos, metadata JsonNode exacto; 404 verbatim) |
| T6.19 | **hecho** | E6.5–E6.9 (cuerpos 400 tal cual R6.21/R6.22; 7 cursor-variants) |
| T6.20 | **hecho** | E6.10 (orden fail-fast + verifyNoInteractions en todo 400) |
| T6.21 | **hecho** | mvn test completo pre-smoke en builder: 111 heredados + 34 nuevos de C6 = 145/145 VERDES (Tests run: 145, Failures: 0, Errors: 0; exit 0) — log `/tmp/mvn_c6_pre_smoke.log` |
| T6.22 | **hecho** | scripts/qa_c6_smoke.sh (pre-fase + E6.16–E6.23; patrón qa_c5_smoke; tmpdir 700 + trap [KEEP_TMP opcionales para debug]; NO revokes keys; sin rutas directas — C6 no tiene cuerpos >1 MiB) |
| T6.23 | **hecho** | smoke contra pila viva (4 corridas — 2 bugs del PROPIO script corregidos, cero cambios de backend): run 4 canónica = **E6.16–E6.23 8/8 PASS, exit 0**; pila 3×healthy; pgdata intocado (sin `down -v`; `--force-recreate` con `--build`); log `/tmp/qa_c6_run4.log` |
| T6.24 | **hecho** | mvn test post-smoke (solo-regresión): **145/145 VERDES, exit 0** de nuevo — log `/tmp/mvn_c6_post_smoke.log` |
| T6.25 | **hecho** | frontera: `git status --porcelain` = CERO ficheros modificados/borrados (solo `??`: query/** main (8) + tests (5) + smoke + .atl/sdd/C6); cobertura E6.1–E6.23 completa (15 [L]) + 8 [S] PASS y 12 parejas §2.6 cubiertas |

## Log

- 2026-10-25: arranque del apply. Lecturas obligatorias hechas (tasks/spec/design
  + código de referencia C3/C5). Pila viva UP 3×healthy (pgdata heredado).
- 2026-10-25: bloques 1–7 escritos. Micro-decisiones documentadas (ninguna
  bloqueante, ancladas abajo en "Notas de divergencia / micro-decisiones"):
  - **text AUSENTE → patron null (no filter)**: `patronIlike` no recibe null
    (precondición Javadoc); el controller convierte ausente → patron null
    DIRECTO al repo — la rama `CAST(? AS text) IS NULL OR message ILIKE ?`
    del estamento único literal lo absorbe, coherente con "todos
    combinables" y sin inventar case. Con `text` PRESENTE (incl. `""`),
    patronIlike produce `%%` natural (fase sin case especial).
  - `FilaConsulta.received_at`/`id` (RAW) → `codificar` en el controller
    (R6.15-c); los DTOs formatean con `EXPIRES_AT_FORMATTER` por referencia.
  - `metadataComoNodo`: `rs.getString("metadata")` → null si NULL;
    readTree malformado (imposible por construcción) → IllegalStateException
    con causa (RuntimeException no capturada → 500 honesto; jamás silencio).
- 2026-10-25: T6.21 PRIMERA corrida: 2 ERRORES de compilación en
  CursorCodec — corregidos y re-corrida VERDE:
  - `JsonNode.longValueExact()` no existe → `i.bigIntegerValue()
    .longValueExact()` (overflow → ArithmeticException atrapada como
    RuntimeException → tolerancia única, sin oráculo de causa).
  - `catch (IllegalArgumentException | RuntimeException)` ilegal (IAE es
    subclase de RuntimeException: "Alternatives ... cannot be related by
    subclassing") → un solo `catch (RuntimeException)` con el comentario.
  - Segunda corrida: 1 FAILURE en E6.9(e): `Instant.parse` moderno ACEPTA
    `2026-10-16T22:31:04+02:00` y lo NORMALIZA a UTC (el supuesto del spec
    R6.15-b — "ISO-8601 estricto rechaza +02:00" — es falso en JDK 21;
    sonda P-3 empírica reproducida con jshell). FIX en el codec propio:
    NUEVO paso 5c — guard `!'r'.endsWith("Z") → CursorInvalidoException`
    ANTES del parse (el codec propio jamás emite un 'r' sin Z). Test nuevo
    en CursorCodecTest (offset-no-utc-rechazado). SIN tocar el vocabulario
    de errores ni ficheros fuera de frontera.
  - Re-corrida FINAL pre-smoke: **145/145 VERDES, exit 0** (111 heredados
    + 34 C6: Dtos×5, Codec×6, Filtros×6, SqlTest×4, MVC×13).
- 2026-10-25: T6.22 smoke escrito; `bash -n` OK; self-review previo arregló
  5 bugs de higiene del script (comillas de literal SQL con
  '::timestamptz', iteración de listas por coma en for, un `fi` huérfano,
  '+' crudo en query %2B, frontera contenida con regex ,^,$).
- 2026-10-25: T6.23 — CORRIDAS del smoke (cada corrida crea su proyecto
  propio `smoke-c6-<TS>-a`; las correcciones fueron TODAS del script, el
  backend NUNCA cambió durante el smoke):
  - **Run 1** (`/tmp/qa_c6_smoke_run.log`): E6.16/E6.17/E6.23 FAIL
    (ingesta batch = 400) y E6.22 FAIL (decode del cursor) — E6.18–E6.21
    PASS. Causa raíz 1: los heredocs python leían `t = sys.argv[1]`
    (¡la RUTA del payload!) como timestamp → 400 del guard de ingesta en
    TODO batch @fichero (los individuales inline pasaban). Causa raíz 2:
    `cursor_r_i` usaba `f"{d[\"r\"]}"` — backslash dentro de la EXPRESIÓN
    del f-string es SyntaxError en Python <3.12 → ALWAYS fail (el endpoint
    estaba BIEN). La trap borró el tmpdir de debug → fijé la opción
    KEEP_TMP=1 (tmpdir 700 conservado, borrado a mano al acabar — hecho).
    CORRECCIÓN: `t = sys.argv[2]` (y E6.16 usa argv[3] para el sufijo) +
    cursor_r_i sin backslash en f-string.
  - Run 2: detenida a mitad de compose al ver la causa raíz (evitar
    corrida inútil); su tmpdir borrado a mano.
  - **Run 3** (`/tmp/qa_c6_run3.log`): 6/8 PASS (E6.18–E6.23 PASS menos
    E6.17/E6.23). Dos bugs PROPIOS: (a) el twin psql agregaba
    `ORDER BY received_at` FUERA de la subconsulta (columna no visible →
    twin="err"); (b) `${batch_ids//,/}` BORRA las comas en vez de
    dividirlas (los id-checks iteraban 1 palabra gigante). El aserto de
    "primera fila adjacente" usaba ids ordenados ASC en vez del ORDEN DE
    ENTREGA. Reconstituido a mano contra psql: REST page2
    {1886,1885,1884} era CORRECTA — keyset perfecto de fábrica. CORRECCIÓN:
    sub-select expone received_at, `${var//,/ }` (coma→espacio), helper
    `items_ids_orden` (orden de entrega) + twin con igualdad de ORDEN.
  - **Run 4 canónica** (`/tmp/qa_c6_run4.log`): E6.16–E6.23 **8/8 PASS,
    exit 0**. tmpdirs de debug borrados a mano (higiene completa).
- 2026-10-25: T6.24 re-corrida post-smoke **145/145 VERDES, exit 0**;
  T6.25 frontera limpia (solo `??`, cero modificados/borrados).

## Loop-back post-auditoría test-side (2026-10-25)

Auditoría: `tests-written.md` §7 (hallazgos H1–H9 contra `qa-plan.md`).
Regla: cerrar al 100% del plan como asserts, SIN degradar asertos ni
reescribir el plan; frontera dif-0 mantenida (solo tests C6 + smoke).

| # | Hallazgo | Resolución (fichero + método/aserto) | Evidencia |
|---|---|---|---|
| **H1** ALTA (QP-6.9 / 2.A-4) | `r` FUTURO → 200 `{"items":[],"next_cursor":null}` sin ancla | **NUEVO test [L]** `LogsControllerMvcTest.cursorConRFuturoResponde200ListaVaciaYNextCursorNullExplicito`: token válido `{"r":"2030-01-01T00:00:00Z","i":1}` → 200 + cuerpo COMPLETO verbatim `{"items":[],"next_cursor":null}` (JAMÁS `filter_cursor_invalid`; S6-3). Único método añadido | mvn: MVC 16→17 métodos, suite 145→146 VERDES |
| **H2** MEDIA (QP-6.10 / 2.C) | sub-caso «to no-fecha + restantes mal → SIEMPRE to (check 6 antes del 7)» ausente | **NUEVO sub-caso (5)** dentro de `ordenFailFastDe8ChecksEsDeterminista`: `from` Z válida + `to=no-fecha` + `cursor=basura` → 400 `filter_invalid_value` field `to` + message VERBATIM («El filtro to debe ser una fecha ISO-8601 con zona horaria») + `verifyNoInteractions(repo)` (con `to` imparsable, `hasta=null` — un reorden 7-vs-6 ya no puede robar el ganador) | mismo método MVC (17 asertos; suite VERDE) |
| **H9** MEDIA (QP-6.18 / 2.D / S-3) | contraguja del plan NO discrimina escape real (sin «timeout») | **CONTRAGUJA DISCRIMINANTE AÑADIDA** en `case_e6_18` del smoke: nueva `CONTRA_TIMEOUT="smoke-c6-<TS>-timeout"` (MISMA LONGITUD + mismo sufijo `-timeout`, sin `_`), correlation `e618c`; ingesta 202 + conteo BD `=` = 1 + su id JAMÁS en la página (`grep -qx` sobre `items_ids`): con escape correcto conteo==1; con `_` comodín crudo matchearía → conteo==2/id-en-página → FAIL. Matización: el aserto del plan «delta = 2 agujas» pasa a +3 en el smoke (tercera aguja del propio smoke; conteo RELATIVO R-6.h intacto, filas heredadas intocadas) — hueco del PLAN, decisión del writer RATIFICADA por orchestrator en el encargo del loop-back | smoke canón. run: E6.18 PASS |
| **H3** BAJA (QP-6.3 / R-6.i) | render C2 de fechas sin asertar en el DETALLE | **NUEVOS asertos** en `case_e6_20` del smoke, pinnados sobre la fila SEMBRADA por el propio test: `timestamp == T_STAMP` (el sembrado) y `received_at` regex `^…T…:…:..Z$` + igualdad contra `to_char(psql … 'HH24:MI:SS\"Z\"')` de ESA fila (un render con milis/micros no encajaría) | smoke canón.: E6.20 PASS |
| **H4** BAJA (QP-6.17) | delta del batch sin conteo relativo en `case_e6_17` | **deltas R-6.h añadidos**: snapshot `base` antes de batch1 → tras 202 delta==base+5; tras batch2 delta==base+8 (5+3) | smoke canón.: E6.17 PASS |
| **H5** BAJA (QP-6.20) | delta de la aguja sin conteo en `case_e6_20` | **delta añadido**: snapshot → tras ingesta delta==base+1 | smoke canón.: E6.20 PASS |
| **H6** BAJA (QP-6.21 / 2.C) | «los 400 NO modifican nada (delta 0)» sin snapshot | **delta-0 añadido** en `case_e6_21` (fase 100% GET): snapshot antes de las 5 variantes → tras elles `delta_cuenta == base` | smoke canón.: E6.21 PASS |
| **H7** BAJA (QP-6.22) | `limit=201` sin message verbatim | **aserto message VERBATIM añadido** en `case_e6_22`: «El filtro limit debe ser un número entre 1 y 200» | smoke canón.: E6.22 PASS |
| **H8** INFO (R6.15-b) | premise desfasada de `Instant.parse` | **SIN CAMBIO — ya citada clara**: el comentario de `CursorCodecTest.offsetNoUtcEsRechazadoAunQueInstantParseLoAceptaria` ya declara la Sonda P-3 (Instant.parse moderno ACEPTA `+02:00`; el paso 5c del codec exige `Z` y pina el RESULTADO contractual, no la causa desfasada) | lectura del test (sin tocar) |

Verificación del loop-back:
- **mvn test COMPLETO** (builder `maven:3.9-eclipse-temurin-21` vía podman):
  **146/146 VERDES, exit 0** — log `/tmp/mvn_c6_loopback.log`
  (LogsControllerMvcTest: Tests run 17, Failures 0).
- **Smoke canónico NUEVO** (`bash scripts/qa_c6_smoke.sh`, pila viva,
  recomposición ÚNICA `up -d --build --force-recreate`, NUNCA `down -v`;
  la contraguja H9 y los deltas H4/H5/H6/H9 en la MISMA corrida):
  **E6.16–E6.23 8/8 PASS, exit 0** — log `/tmp/qa_c6_loopback_smoke.log`.
- **T6.25-frontera re-verificada**: `git status --porcelain` SIN ficheros
  M/D (solo los `??` preexistentes de C6); 2 ficheros de test escritos
  dentro de los directorios `??` de la frontera C6 y el smoke propio del
  corte — cero toques a C1–C5, Security/CorsConfig, yml, V1, pom, docs/, front.
- Higiene: KEYS/JWT solo en tmpdir 700 con trap (tmpdir de la corrida
  borrado por la trap al exit 0).