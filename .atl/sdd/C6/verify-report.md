---
project: Project_Log
change: C6
artifact: verify-report
last_updated: 2026-10-25
verdict: PASS-with-notes
status: pass
---

# Verify Report — C6 · Consulta keyset + filtros + detalle

Verificación INDEPENDIENTE del corte C6 sobre el espécimen real (working
tree vs HEAD base `43ea041`). La evidencia formal de test-run (146/146 mvn
en builder + 8/8 smoke E6.16–E6.23, exit 0) NO se repite — se consume como
dato de `qa-log.txt` — pero TODO el contenido contractual del corte se
re-lee y se pina desde el código real, contra `spec.md` (S6-1..S6-10,
E6.1–E6.23, R6.16, R6.22), `design.md` (D-C6-1, D-S6-a/b/c, D-R6.*) y las
matices ratificadas del loop-back (H8, H9).

**Veredicto: PASS-with-notes** — 7/7 checks A–G OK, 0 desviaciones NO
ancladas, 2 desviaciones ANCLADAS (D-S6-a 16-vs-17, smoke `--build` vs
R6.27) + 2 matices ratificados para el archive (H9, H8/P-3). No hay
arreglos pendientes en el código.

## Check A — Frontera dif-0: **OK**

- `git status --porcelain` sobre HEAD `43ea041`: SOLO `??` (añadidos) —
  `backend/src/main/java/com/projectlog/query/**` (8 ficheros: CursorCodec,
  FiltrosConsulta, controller/LogsController, dto/{LogListItemDto,LogDto,
  LogsPaginaResponse}, repository/LogsQueryRepository, package-info YA
  tracked y sin diff), los 5 test (`LogsControllerMvcTest`, `DtosLogsPinTest`,
  `LogsQuerySqlTest`, `CursorCodecTest`, `FiltrosConsultaTest`),
  `scripts/qa_c6_smoke.sh` y `.atl/sdd/C6/*`.
- `git diff HEAD` y `git diff HEAD --name-status`: **VACÍOS** — CERO ficheros
  modificados/borrados (M/D) en C1–C5, `SecurityConfig`, `CorsConfig`,
  `application*.yml`, `V1__init.sql`, `pom.xml`, `docs/` (07_SLICING_PLAN
  queda pendiente de tocar en el archive), front. R6.3 y R6.29 cumplidos
  byte-a-byte; `query/package-info.java` intocado (S6-1).

## Check B — Literales de contrato verbatim: **OK**

- Los 4 codes con ancla §3.3 verbatim: `filter_from_to_inverted`,
  `filter_invalid_value`, `filter_limit_invalid`, `filter_cursor_invalid`
  (`LogsController` CODE_* constants) + `not_found` y `invalid_token`
  reutilizados según spec (R6.21/R6.22: NINGÚN code nuevo fuera de la tabla;
  el 401 NO lo introduce C6 — entry point de cadena 2).
- Messages cortos en español tal cual R6.22 para TODOS los cuerpos (limit,
  project_id, environment, level, from, to, from>to SIN field, cursor);
  `MSG_NOT_FOUND` duplicado como constant propia del paquete query con el
  LITERAL de AppFlow §5 (`"No existe (quizás fue eliminado)"`) — patrón C5.
- Estructura `{code, message, field?}`: helper `error()` réplica EXACTA del
  de C3 (`LinkedHashMap` + `valueToTree` + `MediaType.APPLICATION_JSON`),
  `field` solo si aplica (check 7 y 404 SIN field — R6.22 ✓).
- DTOs snake_case EXACTOS: `LogListItemDto` 9 componentes literales
  (`project_id`, `correlation_id`, SIN exception/metadata — fuga
  estructuralmente imposible, pinado en `DtosLogsPinTest` por reflection);
  `LogDto` 11 (los 9 + `exception: String` + `metadata: JsonNode` — cierre
  D-S6-c); `LogsPaginaResponse` `{items, next_cursor}` con null EXPLÍCITO
  pinado (`"next_cursor":null` en la serialización).

## Check C — Pin del SQL (E6.11/E6.16): **OK**

- `SELECT_LISTADO` en `LogsQueryRepository` comparado CARÁCTER a CARÁCTER
  contra el literal R6.16 del spec (extracción programática, líneas
  stripped): **byte-exacto**. Ídem `SELECT_POR_ID` (11 columnas).
- Cuenta del literal real: **17 `?`** (12 de los 6 filtros duplicados
  IS-NULL-CAST/igualdad + cursorR×3 + cursorI×1 + limit×1 — D-S6-a) y
  **7 CASTs** (1×bigint project_id, 3×text environment/level/patron,
  3×timestamptz from/to/cursorR) — pinado por `LogsQuerySqlTest` con
  `isEqualTo` byte-a-byte + aserto de cuenta 17 + reparto 1/3/3.
- Guard keyset `AND (CAST(? AS timestamptz) IS NULL OR (received_at < ? OR
  (received_at = ? AND id < ?)))`, `ORDER BY received_at DESC, id DESC` y
  `LIMIT ? + 1` VERBATIM (sonda P-5) — todos presentes en literal y test.
- SIN la palabra `ESCAPE` y SIN `exception`/`metadata` en el listado —
  asertados.
- El smoke EJECUTA ese mismo estamento: todo E6.16–E6.23 con GET /logs
  pasa por `SELECT_LISTADO` (E6.20 por `SELECT_POR_ID`); el twin psql de
  E6.17 replica la semántica keyset del estamento con exactitud de ORDEN.

## Check D — Cursor sin pérdida de micros: **OK**

- Trayecto completo sin truncar: `rs.getObject("received_at",
  OffsetDateTime.class)` (pgjdbc RAW, FilaConsulta.received_at) →
  `codificar(...)` en el controller con `receivedAt.toInstant().toString()`
  — JAMÁS el String renderizado del formatter C2 (R6.15-c; el controller
  usa `filas.get(limite - 1).received_at()`, la ÚLTIMA ENTREGADA, no la
  fila extra — R6.24 ✓).
- Pin P-1 byte-exacto en `CursorCodecTest`
  (`pinP1EncodeByteExactoConElEjemploDelSchema`: token del Schema §3.3
  `eyJy…MX0` en y out; y `pinP1DecodeDevuelveElParExactoDelEjemplo`);
  round-trips a micros y nanos + anti-truncado (token distinto al truncar).
- Regla 9 en el estamento (rama `received_at = ? AND id < ?` ✓) y en el
  smoke E6.17: corte DENTRO del mismo instante (limit=3/2 con resto>0
  pre-asertado), `r` del token == `received_at` a micros (norm_micro),
  twin psql de ORDEN, batch entre páginas — sin duplicados ni saltos.

## Check E — QA report coherente: **OK**

- Tabla QP-6.1–6.23 ↔ evidencia SIN huecos: 15 [L] (`mvn` 146/146:
  MVC 17 + DTOs 5 + SQL 4 + Codec 6 + Filtros 6 = 38 C6 + 108 heredados)
  + 8 [S] + pre-fase/pareja 12 — y el loop-back H1/H2/H9/H3–H7 queda
  VISIBLE en la evidencia de la corrida formal (H1 `cursorConRFuturo…`,
  H2 sub-caso check-6-vs-7, H7 message verbatim en E6.22, deltas H4/H5/H6,
  contraguja H9 en E6.18; H8 citada en §4b). Sin FAIL ni BLOQUEADO.
- Las DOS matices para el archive están presentes y ratificadas: (a) **H9**
  — contraguja misma-length `smoke-c6-<TS>-timeout` DISCRIMINANTE RATIFICADA
  (delta del plan 2 → +3 en el smoke, conteo relativo R-6.h intacto);
  (b) **H8/P-3** — premisa R6.15-b (`Instant.parse` rechaza `+02:00`)
  EMPÍRICAMENTE FALSA en JDK moderno; resuelta con paso 5c guard-Z del
  codec, test `offsetNoUtcEsRechazadoAunQueInstantParseLoAceptaria` SIN
  cambios pina el RESULTADO contractual (es INFO spec-side).
- 12 parejas mock↔psql de §2.6 todas cubiertas (1–12: E6.12→E6.17,
  E6.13→E6.18, E6.14/E6.7→E6.22, etc.).

## Check F — Seguridad: **OK**

- Los 2 endpoints `/api/v1/panel/logs[/...]` nacen bajo la cadena 2
  `securityMatcher("/api/v1/panel/**")` `@Order(2)` EXISTENTE —
  `git diff HEAD` vacío confirma CERO toque a `SecurityConfig` (y ni
  `CorsConfig` ni `WebSocketConfig`); SIN `@PreAuthorize` nuevo (grep: solo
  el comentario Javadoc que lo declara) y SIN `cors()` en query/.
- 401 `invalid_token` cuando no hay JWT: pinado en
  `ambosEndpointsNacenTras401InvalidToken` (sin JWT y con `X-API-Key` →
  401 `invalid_token` en AMBOS paths, `verifyNoInteractions(repo)` —
  la API key jamás autentica el panel) + E6.15 pareja [S] en la pre-fase
  del smoke (sondeo `/me` sin token y con API-key basura → 401
  `invalid_token`).

## Check G — Done criteria del spec, uno a uno (ficha C6 de 07): **OK**

1. Shape/paginado `{items, next_cursor?}` (E6.1, E6.2, E6.22, E6.23) —
   VERDE (trim exacto, null explícito, límites 200/201/defecto-50, walk
   completo hasta fin con cobertura == psql).
2. **Keyset paginado estable** con inserción entre páginas sin duplicar ni
   saltar (`E6.17` + `E6.12`) — VERDE (corte intra-instante, regla 9,
   micros preservados).
3. **Filtros combinados** (`E6.16`, `E6.18`, `E6.19`) — VERDE (twin psql
   idénticos, aguja `_` no-comodín, rango offset-inclusivo `+02:00`≡`Z`).
4. **Detalle con smoke** (`E6.3`/`E6.4` + `E6.20`) — VERDE (11 campos,
   exception completo, metadata objeto EXACTO, 404 con cuerpo exacto).
5. 400s del vocabulario (E6.5–E6.10 + E6.21, delta-0) — VERDE.
6. 401 cadena 2 (E6.15 + pre-fase) — VERDE.
7. Fronteras del corte (spec §4) — respetadas: nada de streaming/resumen/
   DELETE/migraciones/Testcontainers; cero SQL dinámico.

**Deuda S-3 de C4: queda CERRADA** por `E6.18` (aguja `_` con contraguja
discriminante misma-length) + `E6.13`/`FiltrosConsultaTest` (función) —
citada para el archive CON la matización H9 (la contraguja del plan de QA
no discriminaba; la versión misma-length sí, ratificada por orchestrator).

## Desviaciones ANCLADAS (ninguna abierta)

1. **D-S6-a / D-R6.1** — inconsistencia INTERNA del spec §1.G: R6.17
   («16 placeholders») y E6.11 («16 placeholders contados») discrepan del
   PROPIO literal pinado R6.16 (17 `?`) y de la propia prosa R6.17
   («cursorR en sus 3 posiciones»). Resolución ratificada en design.md
   (§ D-S6-a: "el design implementa el LITERAL… sdd-tasks llevará la
   cuenta 17 en el pin"), implementada (binding de 17) y pinada por
   `LogsQuerySqlTest` (17 desde el literal). ANCLA: design D-S6-a/D-R6.1;
   qa-plan §2.B y §6-risk; qa-report QP-6.11. Nota spec-side para el
   archive: corregir los «16» de R6.17/E6.11 si el spec se re-emite.
2. **Smoke `--build` vs R6.27** — el spec R6.27 prescribe recomposición
   `podman compose up -d --force-recreate`; el smoke de C6 usa
   `up -d --build --force-recreate` por necesidad (sin rebuild los
   contenedores quedan con la imagen de C5 SIN los endpoints /logs — el
   propio qa-log lo registra). ANCLA: comentario del script (§0) +
   apply-progress T6.23. Fortalece el smoke; no altera lo prescrito
   (se mantiene NUNCA `down -v`).

## Matices ratificados que el archive DEBE recoger (con H9/H8)

- **H9** (contraguja misma-length ratificada; delta plan 2 → +3 en el
  smoke) — tal como arriba.
- **H8/P-3** (premisa R6.15-b falsa en JDK moderno; guard-Z del codec;
  INFO spec-side) — tal como arriba.

## Deudas / observaciones para el archive

1. `nginx client_max_body_size` → C9 (heredada — R6.29/spec §4).
2. Smoke de docker compose canónico → C9 (heredada — R6.29).
3. 401 con charset ISO-8859-1 → C8/C9 (heredada — R6.29).
4. Matizaciones H9 y H8/P-3 → spec/archive (arriba).
5. Nota del smoke con `--build` en la recomposición (arriba, desviación 2).
6. C6 está SIN COMMIT (evidencia del working tree sobre base 43ea041) — el
   archive debe generar los commits (patrón de cortes previos: feat backend
   + test(smoke) + docs(SDD)), actualizar la ficha `estado` de C6 en
   `docs/07_SLICING_PLAN.md` y cerrar S-3 de C4 con sus anclas.
7. Cortesía documentada (sin acción): R-6.i divergencia de render —
   fechas del DTO truncadas a segundos (formatter C2 vigente, ratificado
   de casa) mientras el cursor preserva micros (P1); cubierta por E6.20.