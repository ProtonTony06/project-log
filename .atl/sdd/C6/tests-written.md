---
project: Project_Log
change: C6
artifact: tests-written
last_updated: 2026-10-25
---

# Tests-written — C6 · Consulta keyset + filtros + detalle

> Auditoría-documental del test-side C6 contra `qa-plan.md` (QP-6.1…QP-6.23,
> asertos [M], transversales 2.A–2.D). Aislamiento CONTEXT-0: NO se leyó el
> código implementado (`backend/src/main/java/com/projectlog/query/**`),
> ni diffs, ni `apply-progress.md`. Fuente de veredictos: SOLO el plan
> (qa-plan §2–§4) ↔ ficheros de test — `backend/src/test/java/com/projectlog/query/{controller/LogsControllerMvcTest, dto/DtosLogsPinTest, CursorCodecTest, FiltrosConsultaTest, repository/LogsQuerySqlTest}.java` +
> `scripts/qa_c6_smoke.sh`.
>
> Nadie de test-side parcheó nada: este artefacto DOCUMENTA, el bucle
> apply/QA resuelve los hallazgos.

## 1. Inventario auditado (completo — R6.4 verificado)

| Fichero | Escenarios del plan |
|---|---|
| `backend/src/test/java/com/projectlog/query/controller/LogsControllerMvcTest.java` | E6.1, E6.3–E6.10, E6.14, E6.15 (→ QP-6.1, 6.3–6.10, 6.14, 6.15) |
| `backend/src/test/java/com/projectlog/query/dto/DtosLogsPinTest.java` | E6.2 (→ QP-6.2) |
| `backend/src/test/java/com/projectlog/query/CursorCodecTest.java` | E6.12 (→ QP-6.12) |
| `backend/src/test/java/com/projectlog/query/FiltrosConsultaTest.java` | E6.13 (→ QP-6.13) |
| `backend/src/test/java/com/projectlog/query/repository/LogsQuerySqlTest.java` | E6.11 (→ QP-6.11) |
| `scripts/qa_c6_smoke.sh` | E6.16–E6.23 + pre-fase (→ QP-6.16–6.23, pareja 12) |

Inventario EXACTO a R6.4 (5 ficheros Maven + 1 script, sin extras ni
ficheros de higiene nuevos — R6.26 respetado). Cuenta global: 23 casos
plan, todos con test-método o fase asignada.

## 2. Mapa por caso — bloque [L] (QP-6.1…QP-6.15)

### QP-6.1 — E6.1 `listadoShapeExactoItemsYNextCursor` — **CUBIERTO**
`LogsControllerMvcTest.listadoShapeExactoItemsYNextCursor` +
`listadoSinFilaExtraDejaNextCursorNullExplicito` +
`listadoVacioSinCursorEs200EstadoLegitimo`.
- «200; shape EXACTO {items:[9 campos], next_cursor}» — asertos [M]: 200 ✓;
  `clavesDe(items[0]).containsExactly(...9 literales..., R6.9)` ✓. Nota
  menor: la claves del wrapper top-level (`items,next_cursor` SIN extras)
  se pinan en el smoke (E6.16, `claves_json`), no en el happy [L] — el
  [L] pina `nodo.has("items")` + el item completo.
- «decode-back == (r,i) de la fila 50 — la ÚLTIMA ENTREGADA, jamás la
  extra» ✓: `CursorCodec.decodificar(next)` contra `filas.get(49)` (la
  fila 51 extra del mock queda descartada — D-S6-b/D-6.11, sin
  mockStatic, sin ArgumentCaptor del codec, sin reflection — verificado
  en el imports del test: el captor solo se usa sobre el BEAN repo).
- «next_cursor: null EXPLÍCITO (la CLAVE aparece)» ✓:
  `cuerpo.contains("\"next_cursor\":null")` + `nodo.get(...).isNull()`.
- «items sin exception ni metadata» ✓ (implícito en containsExactly de
  9 claves).

### QP-6.2 — E6.2 `pinDeLosDtos` — **CUBIERTO**
`DtosLogsPinTest` (4 métodos): 9 componentes literales en orden exacto ✓;
fuga estructural `doesNotContain("exception","metadata")` ✓; LogDto 11 ✓
con pin del TIPO `metadata → JsonNode.class` (índice 8, cierre D-S6-c) y
`exception → String.class` (índice 7) ✓; serialización con claves
literales sin `@JsonProperty` ✓ + nulls explícitos (`"exception":null`,
`"metadata":null`) ✓ + metadata objeto no-textual ✓. Extra: pin del
wrapper `LogsPaginaResponse` (items + next_cursor null EXPLÍCITO, R6.11)
✓ ancla el aserto de wrapper del QP-6.1.

### QP-6.3 — E6.3 `detalleResponde200…` — **PARCIAL**
`LogsControllerMvcTest.detalleResponde200ConLos11CamposYMetadataNodoExacto`.
- 200 con las 11 claves EXACTAS en orden ✓ (`containsExactly` los 11);
  `exception` String tal cual ✓; `metadata` JsonNode OBJETO EXACTO
  (`isObject()` + span/attempt — claves del jsonb intactas, nunca Map ni
  String crudo — R6.10) ✓; nulls EXPLÍCITOS de exception/metadata ✓
  (`contains("\"exception\":null")`/`"metadata":null`, segunda petición
  /43).
- HUECO (H3): «fechas timestamp/received_at render C2 truncado a
  SEGUNDOS (R-6.i)» NO asertado en el detalle — el mock ya transporta
  Strings formateados (el repo está mockeado: el formatter del
  RowMapper es intesteable aquí) y el smoke E6.20 pina las 11 claves
  PERO no el formato de las fechas. En la PAREJA, el render queda
  pinado solo en el LISTADO ([L] E6.1: `received_at == "...:05Z"`
  truncado). Ver plan R6.13→E6.1/E6.3/E6.20.

### QP-6.4 — E6.4 `detalleIdInexistente` — **CUBIERTO**
`LogsControllerMvcTest.detalleIdInexistenteResponde404NotFound`: 404 ✓;
cuerpo EXACTO `{code:"not_found", message:"No existe (quizás fue
eliminado)"}` ✓; `$.field` doesNotExist ✓; `Optional.empty()` del mock
✓. Nota: «NUNCA queryForObject» (R6.18-a) no es asertable por pin de SQL
ni por mock — queda como revisión de código (el pin E6.11 pina la
SELECT, no el método de lectura).

### QP-6.5 — E6.5 enums fuera — **CUBIERTO**
`LogsControllerMvcTest.environmentYLevelFueraDeEnumResponde400FilterInvalidValue`:
`environment=qa` y `level=error` (minúscula, sin normalización) y
`environment=""` y `level=""` → 400 `filter_invalid_value` con field
culpable y los 2 messages VERBATIM (environment y level) ✓;
`verifyNoInteractions(repo)` tras cada bloque ✓ (2.C).

### QP-6.6 — E6.6 `fromMayorTo` — **CUBIERTO**
`fromMayorToResponde400FromToInverted`: 400 `filter_from_to_inverted`,
message VERBATIM, field doesNotExist ✓, verifyNoInteractions ✓;
`fronteraFromIgualToEsLegalYLlegaAlRepo`: from==to → 200 y verify de la
llamada al repo (`Mockito.eq(50)`) ✓.

### QP-6.7 — E6.7 `limitFueraDeRangoONaN` — **CUBIERTO**
`limitFueraDeRangoONaNResponde400FilterLimitInvalid`: bucle sobre
`{0, 201, abc, -5, ""}` (plan pide 0/201/abc/""; `-5` es EXTRA del writer,
cubierto encima) → EL MISMO cuerpo (anti-oráculo S6-6: mismo
code/field/message en el bucle) ✓; verifyNoInteractions dentro del bucle
✓; fronteras legales: `limit=1` → 200 aquí ✓ y `limit=200` en el test
E6.14 ✓.

### QP-6.8 — E6.8 id/instantes no parseables — **CUBIERTO**
`projectIdFromToNoParseablesResponde400FilterInvalidValue`:
`project_id=abc` (msg «El filtro project_id debe ser un número») ✓;
`from=no-fecha` / `to=no-fecha` con messages VERBATIM propios ✓; naive
`2026-10-16T22:31:04` → el MISMO 400 de `from` (S5-5) ✓;
verifyNoInteractions ×4 ✓.

### QP-6.9 — E6.9 `cursorIlegible` — **PARCIAL**
`LogsControllerMvcTest.cursorIlegibleResponde400FilterCursorInvalid`:
11 tokens adversos (a) `ñ%20%2B` y `a+b/cd`, (b) padding `=`, (c) no-JSON
`hello`, (d) sin-r y sin-i, (e) `r` no-parseable y `+02:00`, (f) claves
extras `x:true`, (g) `i` 1.5 y `-1` → todos EL MISMO 400
`filter_cursor_invalid` (code/field/message — anti-oráculo P1 pinado por
iteración con cuerpos idénticos) ✓; verifyNoInteractions por token ✓;
cursor AUSENTE → flujo legal del 200 ✓.
- HUECO (H1, = 2.A-4): «`r` FUTURO válido → 200 `{"items":[],
  "next_cursor":null}` (keyset legítimo, sin 400)» — sin ancla en NINGÚN
  test [L] ni fase [S] (ver §Hallazgos). Detalle abajo.

### QP-6.10 — E6.10 `ordenFailFast` — **PARCIAL**
`LogsControllerMvcTest.ordenFailFastDe8ChecksEsDeterminista` — 4
sub-casos presentes: (1) TODOS mal → check 1 (`filter_limit_invalid`,
field limit) ✓; (2) limit OK + project_id+level mal → SIEMPRE
project_id ✓; (3) from naive solo → field `from` ✓; (4) from/to
PARSEADOS invertidos + cursor basura → SIEMPRE `filter_from_to_inverted`
(check 7 ANTES del 8) ✓; verifyNoInteractions en los 4 ✓.
- HUECO (H2): el sub-caso del plan «to no-fecha + restantes mal → SIEMPRE
  `to` (check 6 ANTES del 7)» NO existe (el guion del plan lo lista; el
  test lo sustituyó por el (3) from-naive-solo, que ya vive en QP-6.8).

### QP-6.11 — E6.11 pin SQL dual — **CUBIERTO**
`LogsQuerySqlTest` (4 métodos, MISMO paquete `query/repository` — sin
reflection):
- Literal EXACTO de `SELECT_LISTADO` (`isEqualTo` byte-a-byte del §1.G) ✓
  — pin más fuerte que los 9 sub-asertos del plan.
- **17 placeholders contados DESDE EL LITERAL** (D-S6-a/D-R6.1: 12 de los
  6 filtros + cursorR×3 + cursorI×1 + limit×1) ✓ — `.chars().filter(c ->
  c=='?').count() == 17`; el Javadoc del test declara explícitamente que
  NO cuenta la lista de 16 de R6.17.
- 7 CASTs: 1×`bigint`, 3×`text` (environment/level/ILIKE), 3×
  `timestamptz` (from/to/guard) ✓ — el orden EXACTO lo fija el
  `isEqualTo` del literal (los conteos por tipo suman 7 ✓).
- Guard keyset `(CAST(? AS timestamptz) IS NULL OR (received_at < ? OR
  (received_at = ? AND id < ?)))` con AMBAS comparaciones + `id <` ✓.
- `LIMIT ? + 1` VERBATIM (`endsWith`) ✓; `ORDER BY received_at DESC, id
  DESC` ✓; las 6 igualdades de filtro ✓.
- NO-`ESCAPE` (`doesNotContain("ESCAPE")`) ✓; listado sin
  exception/metadata (`doesNotContain` ×2) ✓; 9 columnas listado en orden
  (`startsWith`) ✓.
- `SELECT_POR_ID`: literal EXACTO con 11 columnas (exception y metadata
  SÍ) + `WHERE id = ?` + 1 placeholder ✓.
- 2.B-9 (verifyNoInteractions ante TODO 400): está en el MVC (ver 2.C).

### QP-6.12 — E6.12 `codecRoundTrip` — **CUBIERTO**
`CursorCodecTest` (6 métodos, unit puro sin Spring/BD): (a) round-trip
`2026-10-16T22:31:04.123456Z` micros EXACTOS (2.A-1) ✓; (b) pin P-1
encode byte-exacto `eyJyIjoi…MX0` + decode → (2026-10-16T22:31:04Z,
12301) (2.A-5) ✓; (c) sin fracciones (y `token doesNotContain "="` —
withoutPadding SIEMPRE) y con nanos `000000007Z` ✓; (d) anti-truncado:
truncar al segundo → token DIFERENTE + round-trip NO semánticamente
neutro ✓.
- DUDA/NOTA PARA SPEC (H8, no xfail): el test
  `offsetNoUtcEsRechazadoAunQueInstantParseLoAceptaria` documenta con
  comentario («Sonda P-3 empírica de C6») que la PREMISA del R6.15-b —
  «`Instant.parse` rechaza `+02:00`» — es FALSA en JDK moderno (ISO_INSTANT
  normaliza offsets); el test pina el RESULTADO contractual (rechazo →
  `CursorInvalidoException`), que exige que el codec valide la `Z`
  EXPLÍCITAMENTE. Si el codec NO la validara, este test FAILS: es el
  detector del desajuste premise/contrato. El archive de C6 debería citar
  la matización (el literal R6.15-b describe el resultado correcto con
  una causa empíricamente incorrecta).

### QP-6.13 — E6.13 `escapeIlike` — **CUBIERTO**
`FiltrosConsultaTest` (6 métodos): `a_b` → `%a\_b%` ✓; `100%` → `%100\%%`
✓; `a\b` → `%a\\b%` ✓; MATRIZ de orden `_`+`\` juntos (`a_b\c` →
`%a\_b\\c%`; y `\_%` → `%\\_\%%`) — solo `\`-primero correcto (S6-5) ✓;
contra-caso de aguja: `patronIlike("smoke_c6_X")` == `%smoke\_c6\_X%`, ≠
patrón del needle sin `_`, contiene `\_`, no contiene `_` crudo (regex
lookbehind) ✓; `""` → `%%` sin case especial (S6-2, EXTRA del writer,
ancla S6-2) ✓.

### QP-6.14 — E6.14 `limitDefecto50YLegal200AlRepo` — **CUBIERTO**
`limitDefecto50YLegal200AlRepo` (ArgumentCaptor SOBRE EL BEAN repo →
`50` sin param) ✓; `limit200LegalAlRepoYSinLimitElDefectoManda` (captor →
`200` con `?limit=200`) ✓; la rama del trim con limit+1 filas y la extra
descartada vive en E6.1 (51 filas → 50 items + decode-back de la fila 49,
no la 51) ✓. Nota test-side: sin cap de `text`/`cursor` (D-6.10/S6-10) es
un NO-check — no asertable positivamente (un check-de-más SOLO sería
detectable con un `text` largo → 400 esperable? NO: ausencia de 400 ante
text largo no está pinada en ningún test; hueco CERO por diseño del plan
— fuera de alcance §5.8).

### QP-6.15 — E6.15 `ambosEndpointsNacenTras401` — **CUBIERTO**
`LogsControllerMvcTest.ambosEndpointsNacenTras401InvalidToken`: sin JWT
(listado Y detalle) → 401 `invalid_token` ✓; con `X-API-Key` y sin JWT →
TAMBIÉN 401 `invalid_token` (ambos endpoints) ✓;
`conJwtValidoLlegaAlControllerFirmaDelFlujoDel200` ✓;
`verifyNoInteractions` ✓. Pareja 12 [S]: pre-fase del smoke (ver §3,
«pre-fase»).

## 3. Mapa por caso — bloque [S] (QP-6.16…QP-6.23, smoke)

Estructura del smoke: recomposición ÚNICA
`podman compose up -d --build --force-recreate` (NUNCA `down -v`; el
`--build` es decisión test-side — la imagen debe CONTENER las clases C6,
documentado en el header del script, líneas 310–313) →
`esperar_healthy` (sondeo `GET /me` sin token → 401 `invalid_token`,
pareja 12) → `pila_saludable` 3×healthy por podman (nunca /actuator) →
sondeo extra `GET /me` CON `X-API-Key` SIN JWT → 401 `invalid_token`
(E6.15(b) en pila) → login 200 (JWT a tmpdir) → pre-fase: POST project
201 (`smoke-c6-<TS>-a`, id capturado INCONDICIONALMENTE) + regenerate
201 (key → tmpdir). tmpdir 700 + trap (JWT/key jamás a consola —
KEEP_TMP solo depuración, documentado). Exit 0/1/2 ✓; línea
`E6.n: PASS | FAIL | BLOQUEADO (detalle)` por ID (sort -V) ✓. Filtros de
credenciales: `unset` de las 14 vars + lectura `.env` evaluada ✓
(precondiciones 1/1/1 → si no, 8× BLOQUEADO exit 2).

### QP-6.16 — E6.16 filtros combinables — **CUBIERTO**
`case_e6_16`: batch de agujas k1/k2 production+ERROR, k3
production+WARN (distinguidos, T_STAMP común del lote) ✓; delta = base+3
✓; `GET ?project_id=<a>&environment=production&level=ERROR` → 200 ✓;
psql TWIN con la MISMA cláusula WHERE combinada (`=` estricto — R6.20-a)
→ ids IDENTICOS (`items_ids` == string_agg de psql) ✓;
anti-falso-pass: `vacio-inesperado` si el set viniera vacío ✓; k3 (WARN)
NO está en la página ✓; página SIN filtros → 200 + 50 primeras +
`claves_json == "items,next_cursor"` (shape top-level EXACTO — heredado del [L]
del QP-6.1, HUECO del [L] cubierto aquí) ✓ + token `next_cursor`
presente ✓. Nota: «ORDER BY efectivo» del plan no está en ESTA fase
(está pinado en E6.17-twin y cubierto por el walk de E6.23) — nota, sin
hallazgo (la cobertura transversal existe).

### QP-6.17 — E6.17 keyset ESTABLE (Done literal) — **PARCIAL**
`case_e6_17`: batch1 de 5 logs en UN SOLO batch (received_at COMÚN, regla
9 — decisión test-side, ver §5) → página 1 `limit=3` →
`last_id = item 3.º` → psql `resto = count(id < last_id AND
received_at = rec)`; si el corte NO cae dentro del mismo instante,
REINTENTO con `limite=2` (segunda pasada, recálculo) y el FAIL si tampoco
→ materializa el guion de re-intento del plan (riesgo §6 fila 1) ✓.
- (a) «r == received_at a MICROSEGUNDOS (no truncado)»: psql `rec_micro`
  (to_char UTC `.US` — 6 dígitos SIEMPRE) vs `norm_micro(dec.r())`:
  captura la detección de truncado (a segundos `.000000` ≠ `.724014`; a
  millis `.123000` ≠ `.123456`) ✓ — pareja [S] del E6.12a ✓.
- (b) «sin duplicados ni saltos, primera fila pag2 = SIGUIENTE en keyset»:
  `pag1-id-repetido` ×N ✓; `batch2-id-en-pag2` ×N (los ids > maxprev2 del
  batch nuevo NUNCA en pag2) ✓; TWIN psql con la SEMÁNTICA EXACTA del
  estamento (`received_at < r OR (= r AND id < last_id)`, ORDER
  `received_at DESC, id DESC`, LIMIT $limite) == `items_ids_orden` (el
  ORDEN COMPLETO de entrega, no solo el set) ✓; y `pag2-primera-no-mismo-
  instante`: la PRIMERA de pag2 comparte received_at a MICROSEGUNDOS con la
  última entregada de pag1 ✓.
- (c) «sin cursor truncado las filas del mismo instante saltarían» —
  materializado POR el aserto (b) (twin+adyacencia) ✓.
- HUECO (H4): sub-aserto BD «delta = batch K / sin DELETE-UPDATE» sin
  snapshot base + delta explícito en ESTA fase (batch1 se ingesta y NO se
  re-cuenta; batch2 tampoco) — hay evidencia INDIRECTA (twin/ids contra
  BD live) pero el conteo relativo R-6.h no se aplica aquí. Ver
  §Hallazgos, gravitación baja.

### QP-6.18 — E6.18 aguja `_` (cierre S-3) — **CUBIERTO**
`case_e6_18`: ingesta INDIVIDUAL (URL_INGEST — patrón C5) de la aguja
`smoke_c6_<TS>_timeout` (con `_` literal) + contraguja
`smoke-c6-<TS>-sin-guion-bajo` (mismo prefijo, sin `_`) ✓; delta = base+2
✓; `GET ?project_id=<a>&text=smoke_c6_<TS>_timeout` → 200 con conteo==1
IGUALDAD ESTRICTA ✓; el ÚNICO item es la aguja: id REST == id psql (por
`=` IGUALDAD — R6.20-a, NUNCA LIKE crudo) ✓; contraguja EXISTE en BD
(count==1 por `=`) pero NO matcheada (verificado por el conteo==1 +
id==aguja) ✓.

### QP-6.19 — E6.19 rango from/to inclusive — **CUBIERTO**
`case_e6_19`: aguja e619 con frontera `from == received_at == to`
(rango de punto) ✓; delta = base+1 ✓; query `from=<t>+02:00&to=<t>+02:00`
(offset NO-UTC, `+` URL-encodado como %2B — decisión test-side) → 200 ✓;
MISMO resultado que el equivalente `${rec}Z` (`ids_off == ids_z`) ✓;
TWIN psql `>= AND <=` == ids REST (la NORMALIZACIÓN a UTC es equivalente
en ambas caras) ✓; EXTREMOS INCLUSIVE: `id_frontera` PRESENTE en ids REST
✓ (un `>` o `<` EXCLUSIVO produciría vacío y FAIL — la igualdad
rest==psql pina la inclusividad de AMBOS extremos con UNA frontera de
punto). Nota test-side: el plan literal «los 2 ids presentes» (un log de
from y otro de to separados) se materializa como frontera DE PUNTO con
1 id + twin — la inclusividad es equivalente; sin hallazgo.

### QP-6.20 — E6.20 detalle en pila — **PARCIAL**
`case_e6_20`: ingesta individual con `exception` no-nula + `metadata`
objeto real `{"span":"checkout","attempt":2}` ✓; id capturado por
correlation_id ✓; `GET /logs/{id}` → 200 ✓; las 11 CLAVES EXACTAS
(`claves_json` ordenadas == las 11) ✓; `exception` == texto COMPLETO
✓; `metadata` == OBJETO EXACTO (`isinstance dict` + igualdad con las
claves — NADA de string JSON entrecomillado) ✓; (b) `GET /logs/999999999`
→ 404 + `code == not_found` + `message == "No existe (quizás fue
eliminado)"` + field AUSENTE (`clave_existe field` → e=`field-inesperado`)
✓.
- HUECOS (H5): (i) sub-aserto BD «delta = 1 aguja» sin snapshot+delta en
  ESTA fase (la ingesta NO se re-cuenta) — gravedad baja; (ii) «texto
  COMPLETO (hasta 128 KiB solo en detalle)»: la aguja lleva un texto
  corto — el límite 128 KiB no se ejercita (el 413 de ingesta es C5 y
  queda FUERA de este plan §5; nota, sin hallazgo de plan).

### QP-6.21 — E6.21 codes 400 reales ×5 — **PARCIAL**
`case_e6_21`: las 5 variantes con needles del CUERPO EXACTO — (a)
from>to→`filter_from_to_inverted` + message verbatim + field AUSENTE ✓;
(b) `level=error`→`filter_invalid_value`/level + message verbatim ✓;
(c) `limit=0`→`filter_limit_invalid`/limit + message verbatim ✓;
(d) `cursor=basura`→`filter_cursor_invalid`/cursor + message verbatim ✓;
(e) `project_id=abc`→`filter_invalid_value`/project_id + message
verbatim ✓.
- HUECO (H6): sub-aserto BD «los 400 NO insertan ni modifican nada (delta
  0)» sin snapshot+delta en ESTA fase — la fase es 100% GET y el riesgo
  real es nulo, PERO el plan lo declara [M] y el smoke lo omite. Ver
  §Hallazgos.

### QP-6.22 — E6.22 limit frontera en pila — **PARCIAL**
`case_e6_22`: pre-condición (conteo global ≥201, BLOQUEADO honesto si
no) ✓; `?limit=200` → items==200 EXACTOS + `next_cursor` PRESENTE
(«si hay fila 201» — la pila global la garantiza por la pre-condición) ✓
+ token decodificable `cursor_r_i` ✓; `?limit=201` → 400
`filter_limit_invalid` ✓ + field `limit` ✓; SIN limit → items==50 ✓
(pareja del E6.14).
- HUECO (H7): la variante 201 pine code+field PERO NO el message
  verbatim — la regla global §1 del plan («TODO [M] pina HTTP +
  CUERPO code/message/field») queda incompleta SOLO aquí (las otras 400
  de smoke la pinan). Gravedad baja.

### QP-6.23 — E6.23 fin de resultados + recorrido completo — **CUBIERTO**
`case_e6_23`: batch de 5 agujas (UN SOLO batch → received_at COMÚN,
regla 9) — aserto PRE: `count(DISTINCT received_at) == 1` para ids >
maxprev (el batch COMPARTIÓ el instante — precondición del Done) ✓;
recorrido COMPLETO encadenando `cursor=next_cursor` (limit=2) hasta
`next_cursor: null` EXPLÍCITO — `next_cursor_tok` DISTINGUE null de
clave-ausente (exit 3 → `pag.n-cursor-ausente`) — el aserto «null
EXPLÍCITO» (R6.11) en pila ✓; páginas intermedias EXACTAMENTE 2 items ✓;
`ids-duplicados` (uniq -d) ✓; COBERTURA COMPLETA: walked == psql_ids del
PROYECTO COMPLETO (no solo del batch — iguales, sin saltos globales)
✓; los 5 ids del batch PRESENTES (`batch-id-faltante` ×N) ✓; fin de
resultados sin fila extra ✓. Antibucle de while infinito (límite `paginas < 60`) ✓
— nota test-side. «Estable en cada punto» (AppFlow paso 4) queda
materializado por el set de cobertura + adyacencia ya pinada en E6.17.

### Pre-fase (pareja 12 — smoke-of-the-smoke) — **CUBIERTO**
`precondiciones` + `pre_fase` (descritos arriba en la estructura):
recomposición ÚNICA — down-v JAMÁS (no hay `down` en el script; verificado
grep: el único `down` del case es el nombre del case wrapper `compose_ down`
de timeout-no-usado) ✓; sondeo `/me` sin token → 401 invalid_token ✓;
`/me` CON `X-API-Key` (`plk_basura_de_smoke`) → 401 `invalid_token` ✓
(E6.15(b) en pila); login+proyecto+key ✓; needles sufijados `<TS>` ✓.

## 4. Transversales 2.A–2.D — dónde están anclados

| Transversal | [L] | [S] | Veredicto |
|---|---|---|---|
| **2.A-1** round-trip micros | `CursorCodecTest.roundTripConMicrosPreservaElInstanteExacto` (+ nanos en roundTripSinFraccionesYConNanos) | E6.17(a) `norm_micro`/`rec_micro` a 6 dígitos (pareja QP-6.17) | CUBIERTO |
| **2.A-2** corte DENTRO del mismo instante | — (no es [L]-asertable sin BD) | E6.17: batch1 propio 5-filas + `resto = count(... id < last_id AND received_at = rec)` + re-intento `limite=2` + twin keyset-ESTRICTO + `pag2-primera-no-mismo-instante` | CUBIERTO |
| **2.A-3** tolerancia ESTRECHA decode (anti-oráculo P1) | E6.9: 11 tokens → EL MISMO cuerpo iterado (sin causa) | E6.21(d) `cursor=basura` (body exacto) | CUBIERTO |
| **2.A-4** sin oráculo de BD — `r` futuro → 200 vacío | **NINGUNO** | **NINGUNA fase** | **NO CUBIERTO — Hallazgo H1 (QP-6.9)** |
| **2.A-5** pin byte-exacto P-1 | `CursorCodecTest.pinP1EncodeByteExacto…` + `pinP1DecodeDevuelveElParExacto…` | (es un pin [L] por diseño — no requiere [S]) | CUBIERTO |
| **2.B** pin SQL (D-S6-a) | `LogsQuerySqlTest`: **17 placeholders DESDE EL LITERAL** (no los 16 de R6.17 — D-R6.1), 7 CASTs (1/3/3) con orden pinado por `isEqualTo` byte-a-byte, guard keyset, `LIMIT ? + 1` endsWith, ORDER BY, `SELECT_POR_ID` 11+1, NO-ESCAPE, 9 cols, sin exception/metadata | ejecución REAL de los CASTs contra PG17 vía las 8 fases de smoke (el estamento se ejecuta en TODAS las fases con filtros) | CUBIERTO (9/9 sub-asertos — 2.B-9 vía MVC) |
| **2.C** fail-fast 8 checks | `ordenFailFastDe8ChecksEsDeterminista` (4 sub-casos — hueco check-6-vs-7, H2) + `verifyNoInteractions(repo)` EN CADA caso 400 del MVC (E6.5 ×3 bloques, E6.6, E6.7-por-item, E6.8 ×4, E6.9-por-token, E6.10 ×4) | los 400 reales en E6.21 ×5 (sin delta-0, H6) | PARCIAL (H2, H6) |
| **2.D** orden del escape + contra-caso aguja | `FiltrosConsultaTest.elOrdenGuionMasBarraJuntos…` (matriz) + `contracasoDeLaAgujaElGuionBajoNoEsComodin` | E6.18 (needle psql por `=`/`=` count — NUNCA LIKE crudo — verificado: NO hay `LIKE` en ningún SQL del smoke, solo `=`/ILIKE REST) | CUBIERTO |

**Cuenta de 17 placeholders desde el LITERAL R6.16 (D-S6-a)**: ANCLADA —
`LogsQuerySqlTest` líneas 41–45 (`assertThat(sql.chars().filter(c -> c ==
'?').count()).isEqualTo(17)`) y el `isEqualTo` byte-a-byte del literal
(líneas 23–38) la refuerza; Javadoc del test declara la 17-vs-16
(D-R6.1/plan riesgo §6 fila 3).

**Decode-back de E6.1 (D-S6-b)**: ANCLADO —
`LogsControllerMvcTest.listadoShapeExactoItemsYNextCursor` (líneas
195–200): `CursorCodec.decodificar(next)` vs `(r, i)` de
`filas.get(49)`; el Javadoc del test y el set de imports confirman: SIN
mockStatic, SIN ArgumentCaptor del codec (captor solo sobre el BEAN
repo — E6.14), SIN reflection. Igual en `CursorCodecTest` para la misma
aserción (pin decode P-1).

## 5. Decisiones test-side (del auditor)

1. **Ventana de E6.17 sembrada con batch PROPIO** (decisión del writer,
   RATIFICADA por este auditor): en vez del recorrido sobre las 1877
   filas heredadas (sonda P-0 del plan probabilística), `case_e6_17`
   SIEMBRA SU batch1 de 5 logs (regla 9: received_at COMÚN garantizado
   por el propio ingest) y pregunta `limit=3` → el corte DENTRO del
   mismo instante es DETERMINISTA (resto=2); el re-intento `limite=2`
   queda como salvaguarda del guion del plan. La sonda P-0 queda como
   racional documentado, no como fixture. Mejora el determinismo sin
   apartarse del plan (el plan permite «el límite mínimo que la
   produzca»).
2. **Sufijos de needles**: `smoke-c6-<TS>-k1..k3` (combinado, niveles/environment
   distinguidos), `smoke_c6_<TS>_timeout` (aguja `_`), `smoke-c6-<TS>-
   sin-guion-bajo` (contra), correlation_id `e616-k1..e623-*` como llaves
   de captura psql — respetan la regla R6.20-a: en psql, `_` SOLO entra
   por `=` / `count(*)` (verificado: CERO `LIKE` de psql con `_` crudo).
3. **`items_ids` ASC-normaliza** los ids (`sorted`) para igualdad de
   SETS con `string_agg(ORDER BY id)`; `items_ids_orden` preserva el
   ORDEN DE ENTREGA para la comparación adyacente de E6.17 — separación
   limpia de «igualdad de sets» vs «igualdad de secuencia keyset».
4. **`norm_micro`** (fracción rellena/de-truncada a 6): la comparación
   de la `r` del token contra el to_char de psql sigue DETECTANDO
   truncamiento (a segundos → `.000000`; a milisegundos → `.123000`) aunque
   `Instant.toString()` omita fracciones nulas — cierre del aserto
   2.A-1 en pila sin falsos-negativos.
5. **`+02:00` percent-encodado `%2B`** en la query de E6.19 (+ crudo es
   espacio en query-string) — correcto; nota del auditor.
6. **Recomposición con `--build`** (además de `--force-recreate`):
   decisión del writer JUSTIFICADA — sin rebuild los contenedores
   seguirían en la imagen vieja sin los endpoints `/logs` — documento
   header del smoke; el plan §1 solo menciona `--force-recreate`; sin
   hallazgo (diferencia segura, la pila no se destruye).
7. **El smoke NO invoca scripts previos** (el header declara por qué: el
   helper de down de C5 usa `-v` y destruiría pgdata) — esqueleto PROPIO
   réplica; coherente con R6.27/R6.28 y el plan («down -v NUNCA»).
8. **Sondeo `/me` en 2 puntos**: como smoke-of-the-smoke EN
   `esperar_healthy` (loop) y como pareja-12 en `precondiciones` (sin
   token y con `X-API-Key`) — duplicación INTENCIONAL (el plan pide
   ambos usos).
9. **Error interno del script** (`ejecutar` wrapper): cualquier crash de
   una fase → `FAIL (error interno)` — JAMÁS silencio; el
   `set -euo pipefail` está bien combinado con los `|| true` locales.

## 6. Dudas y comportamientos ambiguos (nota para spec / archive)

- **H8 — premise del R6.15-b (Instant.parse vs `+02:00`)**: el literal
  del spec dice «`Instant.parse` (ISO-8601 estricto: solo instante UTC
  con `Z`; un `+02:00` NO es instante-parseable y la rechaza)». EMPÍRIC
  EN JDK ≥12: `Instant.parse("...+02:00")` SÍ parsea (ISO_INSTANT
  normaliza). El test `offsetNoUtcEsRechazadoAunQueInstantParseLoAceptaria`
  (comentario «Sonda P-3 empírica de C6») pina el RESULTADO
  contractual (`CursorInvalidoException`), lo que exige que el codec
  lleve una validación de `Z` propia (el «paso 5c del codec» citado). Para el
  spec/archive: el RESULTADO del contrato se MANTIENE (tolerancia
  estrecha, 400); la CAUSA documentada en R6.15-b está desfasada. El
  E6.9(g/E) y el 400 `filter_cursor_invalid` de la respuesta siguen
  cubiertos.
- **E6.18 no discrimina ILIKE-escapado VS igualdad exacta CON ESTA
  CONTRAGUJA (H9-DUDA, severidad media — ver Hallazgos)**: la
  contraguja `smoke-c6-<TS>-sin-guion-bajo` NO contiene el sufijo
  «timeout», así que TAMBIÉN fallaría a matchear si `_` fuese comodín
  (el patrón `smoke?c6?TS?timeout` no cabe en «-sin-guion-bajo») → el
  conteo==1 NO distingue una implementación con escape correcto de una
  que usa el texto crudo sin escapar. El [L] E6.13 pina la FUNCIÓN
  (a_b→%a\_b%) pero NINGÚN test pine que EL CONTROLLER LA USE (no hay
  captor del arg `patronTexto` en el MVC test). Este nombre de
  contraguja viene DEL PROPIO PLAN (QP-6.18: «contraguja smoke-c6-<TS>-
  sin-guion-bajo») — es una debilidad del PLAN/spec, no del writer.
  Nota para spec: una contraguja de la MISMA LONGITUD con el MISMO
  sufijo final (`smoke-c6-<TS>-timeout`, sin `_`) SÍ discriminaría (con
  escape NO matchea; con `_` comodín SÍ matchearía).
- **xfail**: NO se detectó NINGÚN `@Disabled`/`@ExpectedException`-style
  xfail en los 5 ficheros; el único «comportamiento ratificado sin
  toque» (`/logs/abc`, R6.6) está CORRECTAMENTE fuera de los tests (§5.9
  del plan: sin escenario).

## 7. Hallazgos para apply/QA (bucle apply — NO los resuelve test-side)

| # | QP-x / transversal | Hallazgo | Severidad |
|---|---|---|---|
| **H1** | QP-6.9 / 2.A-4 | **NO CUBIERTO**: «`r` FUTURO válido → 200 `{"items":[],"next_cursor":null}` (keyset legítimo, SIN 400)» — sin ancla en `CursorCodecTest` (no pina semántica de BD), sin caso MVC (faltaría un stub de `listar` con cursor parseado → lista vacía + aserto de NO-400) y sin fase smoke. Es el ÚNICO aserto del plan sin test de ninguna clase | ALTA (única NO-CUBIERTA) |
| **H2** | QP-6.10 / 2.C | Sub-caso «to no-fecha + restantes mal → SIEMPRE `to` (check 6 ANTES del 7)» ausente de `ordenFailFastDe8ChecksEsDeterminista` (se sustituyó por from-naive-solo, ya cubierto en E6.8). Riesgo: un reorden check-7-vs-6 pasaría los 4 sub-asertos presentes | MEDIA |
| **H3** | QP-6.3 / R6.13 | Render C2 truncado a segundos (R-6.i) NO asertado para el DETALLE: [L] usa mock ya formateado (el formatter del RowMapper es intesteable con repo mockeado), E6.20 [S] pine claves+metadata+exception pero NO el formato de `timestamp`/`received_at`. Pinado solo para el LISTADO (E6.1 [L]) | BAJA |
| **H4** | QP-6.17 | Sub-aserto BD «delta = batch K (sin DELETE/UPDATE)» SIN conteo relativo base→after en `case_e6_17` (batch1/batch2 ingesta sin re-cuenta) — evidencia indirecta (twin/walk) | BAJA |
| **H5** | QP-6.20 | Sub-aserto BD «delta = 1 aguja» SIN conteo relativo en `case_e6_20` (aguja ingesta sin re-cuenta) | BAJA |
| **H6** | QP-6.21 / 2.C | Sub-aserto BD «los 400 NO insertan ni modifican nada (delta 0)» SIN snapshot+delta en `case_e6_21` (la fase es 100% GET; el plan lo declara [M]) | BAJA |
| **H7** | QP-6.22 | Variante `?limit=201` pine code+field pero NO el message verbatim (`El filtro limit debe ser un número entre 1 y 200`) — regla global §1 del plan (todo [M] = HTTP+cuerpo) incompleta SOLO aquí | BAJA |
| **H8** | QP-6.12 / R6.15-b |La premisa del spec DESFASADA (ver §6): `Instant.parse` en JDK moderno SÍ tolera offsets; el test pina el resultado contractual y FALLARÍA (correctamente) si el codec no validase la Z — citar la matización en el archive/spec | INFO (spec-side) |
| **H9** | QP-6.18 / 2.D / S-3 | La contraguja del PLAN (`smoke-c6-<TS>-sin-guion-bajo`, SIN «timeout») NO DISCRIMINA: con `_` comodín el patrón tampoco la matchearía (no contiene «timeout») → el conteo==1 pasaría TAMBIÉN con escape AUSENTE. Además el MVC test no pina el USO de `patronIlike` en el controller (sin captor del arg `patronTexto`). El cerrado S-3 queda citable solo por E6.13 (función) + conteo-estricto (texto-ignorado SÍ detecta: proyecto-a tiene ≥10 filas al llegar a E6.18), pero NO por el «contra-caso de comodín». El nombre viene del plan mismo — es un hueco del PLAN, no del writer; aplicar una contraguja-con-«timeout» requiere decisión spec (fuera de este artefacto) | MEDIA |

Resumen: sin `xfail`; ningún test parcheado; los 3 veredictos PARCIAL
del bloque [L] y 4 del [S] quedan trazados con QP-6.x exacto arriba.

## 8. Cuenta final de veredictos

| Bloque | CUBIERTO | PARCIAL | NO CUBIERTO | DUDA |
|---|---|---|---|---|
| [L] QP-6.1–6.15 | 12 (6.1, 6.2, 6.4–6.8, 6.11–6.15) | 3 (6.3, 6.9, 6.10) | 0 | — |
| [S] QP-6.16–6.23 | 4 (6.16, 6.18, 6.19, 6.23) | 4 (6.17, 6.20, 6.21, 6.22) | 0 | — |
| Transversales 2.A–2.D | 2.A (4/5), 2.B ✓, 2.D ✓ | 2.C | 2.A-4 | H8/H9 |

**Total: 16 CUBIERTO · 7 PARCIAL · 1 NO CUBIERTO (2.A-4/QP-6.9) · 2
notas-de-spec (H8, H9)** — de 23 casos + 4 transversales.