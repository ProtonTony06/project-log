---
project: Project_Log
change: C8
artifact: tests-written
last_updated: 2026-10-28
---

# Tests-written — C8 · Frontend del panel según maquetas (React + TS SPA + fix D1)

> Auditoría-documental del test-side C8 contra `qa-plan.md` (QP-8.1…QP-8.59,
> asertos §2.A/§2.B, parejas del spec §3, cifras §4). Aislamiento CONTEXT-0:
> NO se leyó el código implementado (NINGÚN fichero de
> `backend/src/main/java/**`, `frontend/src/main/**` o de producción —
> `frontend/src/ws/{dedup,client}.ts`, `frontend/src/api/*.ts`,
> `frontend/src/hooks/*.ts`, `frontend/src/components/*.tsx`,
> `frontend/src/pages/**`), ni diffs de git, ni `apply-progress.md`, ni
> `.atl/notes/**`, ni `state.md`, ni config de builds. Fuente de veredictos:
> SOLO el plan (qa-plan §1–§6) ↔ ficheros de test — los 6 ficheros Vitest
> (`ws/dedup.test.ts`, `ws/client.test.ts`, `hooks/usePagination.test.ts`,
> `hooks/useLogFilters.test.ts`, `api/client.test.ts`,
> `components/Modal.test.ts`), `backend/src/test/java/com/projectlog/config/
> EntryPointCharsetTest.java` y `scripts/qa_c8_smoke.sh` (el guion es fichero
> de test: sus marcas `E8.n` son el objeto de auditoría, incl. el guion que
> ya trae las correcciones de la nota de humo — comentarios H-C8-B2/B3
> leídos DEL PROPIO guion).
>
> NO se ejecutó NINGÚN test (corridas en curso): todas las cifras de esta
> auditoría son CONTADAS del código de test (@Test / `it(` / marcas), no de
> ejecuciones. Nadie de test-side parcheó nada; los hallazgos van al bucle
> apply/QA.

## §0 Encabezado de aislamiento (CONTEXT-0)

- **Leído y permitido**: `qa-plan.md`, `C7/tests-written.md` (formato), los 6
  `*.test.ts`, `EntryPointCharsetTest.java`, `qa_c8_smoke.sh`.
- **NO leído (prohibido)**: todo lo de producción, diffs, notes/, state,
  apply-progress. Por tanto los FAIL del qa-run deben poder señalarse al caso
  del PLAN (QP-8.x) — este artefacto documenta qué QP tienen test responsable
  y con qué fuerza, para que el loop-back del plan §6 funcione.
- Veredictos CUBIERTO / CUBIERTO-CON-MATIZ / HUECO se justifican SIEMPRE
  citando aserto del plan; ningún hueco se cubre con suposiciones.

## 1. Inventario auditado (completo)

| Fichero | Escenarios del plan (E8.x) | QP-8.x |
|---|---|---|
| `frontend/src/ws/dedup.test.ts` (7 `it`) | E8.28 (dedup por id, orden global, no-mutación), E8.25 ([L]: grupo coherente / insertado al comienzo) | QP-8.28, QP-8.25 ([L]) |
| `frontend/src/ws/client.test.ts` (15 `it`) | E8.30 ([L]: backoff secuencial/bounds), E8.27 ([L]: shape `set_filters` + parser), E8.33 (parser defensivo), E8.31 ([L] — NO materializada, ver §2) | QP-8.30, QP-8.27 ([L]), QP-8.33, QP-8.31 ([L] hueco parcial) |
| `frontend/src/hooks/usePagination.test.ts` (5 `it`) | E8.24 ([L]: acumulado keyset, fin, vacío legítimo) | QP-8.24 ([L]) |
| `frontend/src/hooks/useLogFilters.test.ts` (9 `it`) | E8.21 (bidireccional idempotente), E8.23 ([L]: offset inyectado), E8.3 ([L]: ida-y-vuelta del par E8.6↔E8.1/E8.5) | QP-8.21, QP-8.23 ([L]), QP-8.3 |
| `frontend/src/api/client.test.ts` (11 `it`) | E8.14/E8.15 ([L]: Bearer + 401 solo-status), E8.16 (tolerante no-200), E8.17 ([L]: captura de code/message/field), red caída status 0 | QP-8.14, QP-8.15, QP-8.16, QP-8.17 ([L] parcial) |
| `frontend/src/components/Modal.test.ts` (8 `it`) | E8.37 / E8.47c (comparador trim literal) | QP-8.37 ([L]), QP-8.47 ([L]) |
| `backend/src/test/java/com/projectlog/config/EntryPointCharsetTest.java` (3 `@Test`: 2 en `@Nested Panel401` + 1 en `@Nested Ingesta401`) | E8.48, E8.49 (D1charset byte-exacto C39), 3.º método = live-evidence que reconcilia la cifra de QP-8.50 | QP-8.48 ([L]), QP-8.49 ([L]), QP-8.50 ([L]) |
| `scripts/qa_c8_smoke.sh` (22 marcas `E8.n` [S] + 1 `Q23` BLOQUEADO) | MARCADAS: E8.6, E8.51, E8.7, E8.19, E8.14, E8.15, E8.48, E8.49, E8.20, E8.24, E8.23, E8.26, E8.39, E8.43, E8.44, E8.34, E8.36, E8.41, E8.38, E8.47, E8.27, E8.31 — cases: `case_e8_6` (E8.6+E8.51), `case_e8_7` (E8.7+E8.19), `case_e8_14` (E8.14+E8.15), `case_e8_48` (E8.48+E8.49), `case_e8_crud` (E8.17), `case_e8_20` (E8.20+E8.24), `case_e8_23` (E8.23+E8.26), `case_e8_39`, `case_e8_43` (E8.43+E8.44), `case_e8_34` (E8.34+E8.36), `case_e8_41`, `case_e8_38` (E8.38+E8.47), `case_e8_31`, `case_e8_27` | QP-8.6, 8.51, 8.7, 8.14, 8.15, 8.19*, 8.20, 8.23, 8.24, 8.26, 8.27, 8.31, 8.34, 8.36, 8.38, 8.39, 8.41, 8.43, 8.44, 8.47, 8.48, 8.49 — *ojo: los IDs `E8.19` y `E8.17` que MARCA el guion NO corresponden a los casos del plan con esos números (ver H-A1/H-A2) |
| (live-evidence de gates, sin fichero propio) `mvn -f backend/pom.xml test` | E8.50 regresión completa | QP-8.50 ([L]), QP-8.59 (pos-smoke) |
| (live-evidence por orden formal §1) `qa_c3` / `qa_c7` / `ws_echo_test.py` sin edición | E8.53 | QP-8.53 (comandos 3–4 del orden formal; fuera del guion C8) |
| pre-fase `down -v` + `up -d --build` del guion | E8.55 (guardia de compilación TS/Dockerfile), E8.57 (meta-caso), E8.59 (re-corridas) | QP-8.55, QP-8.57, QP-8.59 — con la desviación de exit-codes de H-A3 |

- **QP sin cobertura [L]/[S] materializada** (más allá de su [MANUAL-Q23] o
  [REVIEW] DECLARADOS por el plan — que no son huecos):
  - **QP-8.19** (`sinCorsRutasRelativas` [S]): el guion NO emite veredicto
    para el sin-CORS/rutas-relativas del plan; la marca `E8.19` del guion es
    «/me sin token → 401» (H-A1).
  - **QP-8.42** (ficha 05 + 404 proyecto): sin veredicto `E8.42` en el guion
    (el GET detail 200 existe implícito en `case_e8_crud`/`case_e8_43`, pero
    el 404 del proyecto no se prueba y el ID no se marca) — HUECO (plan
    esperaba `case E8-API`).
  - **QP-8.45** (201 con `api_key_prefix` vacío + `has_key:false`; form.
    flujo 2): sin veredicto `E8.45`; sus negativos (409/400) los porta el
    case marcado **E8.17** (H-A2) y el 201 SIN asertos de prefix/has_key
    ocurre solo en pre-fase. HUECO parcial (el guion lo reasigna a Q23 en su
    nota final, pero el plan §3/[S]-cifra lo esperaba con veredicto).
  - **QP-8.46** (PATCH solo-modificados + 404): SIN ningún case ni veredicto
    — HUECO (B-media).
  - **QP-8.52** (`/ws-console.html` y `/mockups/…` al fallback): sin aserto
    ni veredicto — HUECO (plan lo asignaba a `case E8-servido`).
  - **QP-8.18** MAY (502→Reintentar): no materializado en el guion (sin
    veredicto E8.18) — el plan ya lo preveía tolerado-con-nota (R-7.k/Q23
    patrón); queda a nota del qa-report.
  - **QP-8.25 mitad [S]** (ingesta→frame→Cargar más con psql): sin case ni
    veredicto — HUECO de la mitad [S] (la mitad [L] sí en dedup.test.ts).
  - **QP-8.31 mitad [L]** (rama `alCierreInaugural`→`me()`): NO
    materializada — el plan la condicionó («si el módulo la extrae pura»);
    los tres puntos de entrada extraídos que testeó el guion Vitest son
    `retrasoReconexion`/`serializarSetFilters`/`parsearFrame`, y ningún test
    dispara el cierre inaugural del `ClienteWsLogs` (los callbacks
    `alCierreInaugural: () => {}` pasan como no-ops sin aserto). HUECO
    declarado-condicional del plan; sin cobertura real.
  - **QP-8.30 mitad [S]**: por diseño del plan va CITADA (pareja E7.11 de
    qa_c7, «CITADA, no re-probada») — no es hueco, es delegación declarada;
  - las [MANUAL-Q23] (QP-8.1, 8.2, 8.4, 8.5, 8.8, 8.9, 8.10, 8.12, 8.13,
    8.22, 8.26-visual, 8.29, 8.32, 8.35, 8.40) y [REVIEW] (8.54, 8.58 +
    mitades de 8.50/8.52) están asignadas por el plan a demo C9/verify — NO
    huecos (el MAY de QP-8.8: «401 invalid_credentials REST en case
    E8-auth» tampoco se materializa — solo se prueba login FELIZ; matiz
    BAJA dentro del Q23).

## 2. Auditoría por aserto — bloque [L] (Vitest + C39)

### QP-8.28 — E8.28 `dedupPorIdOrdenGlobal` — **CUBIERTO** (1 matiz)
`dedup.test.ts`: `comparadorRecibidoDesc` —
- «id repetido en acumulado → IGNORADO» ✓: `it("id ya presente (llegó por Cargar más o refetch) se IGNORA")` — lote con id ya presente ⇒ `resultado).toBe(acumulado)` (identidad conservada, sin cambios) + ids `[10, 5]`.
- «lote desordenado → sort global» ✓: `it("lote desordenado vs orden global…")` — [100 | lote 2,3,1] → `[1, 3, 2, 100]` (más reciente primero, grupo del lote por id DESC, antiguo del keyset al final).
- «empate de micros resuelto por `id` DESC» ✓: `it("empate con micros…")` — tres logs con `received_at` IDÉNTICO al micro (`.204123Z`) → `[7, 6, 5]`.
- «entradas NO mutadas» ✓: `it("NUNCA muta los arrays de entrada")` — `toEqual(copias)` + `resultado).not.toBe(acumulado)`.
- «comparador global `received_at DESC, id DESC` CON MICROS» ✓ dirección DESC (`comparador(a=b viejo, b=nuevo) > 0` / inverso `< 0`) — MATIZ (BAJA): NINGÚN par del fichero difiere SOLO en micros a partir de `.000`/`.100` (los pares usan millis o el MISMO string completo al micro); la resolución-a-micros queda inferida por la comparación de string ISO sin aserto dedicado que distinga `.204123` vs `.204124`.

### QP-8.25 — E8.25 mitad [L] — **CUBIERTO CON MATIZ** (mitad [S] HUECO)
- «fila nueva insertada al comienzo por dedup» ✓: `it("el lote entra como GRUPO coherente en el empate de received_at")` — [50 | lote 11,12,13 empate al micro] → `[13, 12, 11, 50]` (grupo completo por delante del acumulado, orden del lote sin refresco).
- «cursor ya obtenido NO invalidado» ✓ (vía `usePagination.test.ts`): `it("acumula páginas y guarda el nuevo cursor (E8.24)")` pinna `nextCursor` conservado tras acumular — el pin del cursor vive en QP-8.24 [L] (absorción declarada del plan).
- «Cargar más posterior no duplica ni salta» ✓ materializado en [L] como dedup-id (id repetido ignorado, identidad conservada) — la forma integrada (frame + clic + petición con cursor) era la mitad [S] del plan y NO tiene case en el guion (HUECO, ver §6).

### QP-8.27 — E8.27 mitad [L] — **CUBIERTO CON MATIZ** («EN ORDEN»)
`client.test.ts` — `serializarSetFilters` (S8-8):
- «EXACTAMENTE 6 claves, `null` donde no aplica, jamás `cursor`/`limit`» ✓: `it("shape byte-exacto: 6 claves SIEMPRE, nulls, sin claves extra")` — `JSON.parse(s).filters` `toEqual` de las 6 + `it("con valores…")` con `Object.keys(filters).length).toBe(6)` y `project_id` number, `to` null.
- MATIZ (MEDIA — H-C8-1): el plan §2.A-2 exige el stringify «con EXACTAMENTE 6 claves **EN ORDEN** (`project_id, environment, level, from, to, text`)» y §2.A-1 remite el orden a «la SALIDA JSON que el client construye». Los asertos Vitest usan `toEqual`/`Object.keys(…).length` (insensibles al ORDEN de claves JSON) y `useLogFilters.test.ts` además ordena con `.sort()` — el pin de ORDEN del `set_filters` NO existe ni en [L] ni en [S] (en el smoke, el `set_filters` que cruza el cable lo COMPONE el propio guion con `printf`, no el cliente serializado — `case_e8_27`). La forma contractual (6 claves + nulls) sí está; el orden literal queda sin pin.
- Parser ([L] del frame): `parsearFrame` — «frame batch válido pasa al callback completo» pinna type/batch_id/logs[0].id sobre el fixture de 11 campos. (La cobertura defensiva es el QP-8.33, abajo.)

### QP-8.30 — E8.30 [L] — **CUBIERTO CON MATIZ** (secuencia pinada por extremos)
`client.test.ts` — `retrasoReconexion` (D-C8-5.4), 5 `it`:
- «secuencia 1 s → 2 s → 4 s … cap 30_000» ✓ PERO vía los dos EXTREMOS del jitter: `it("secuencia exponencial con jitter neutro (aleatorio=0)…")` espera `800/1600/3200/6400` (×0.8) y `it("jitter máximo (aleatorio≈1) ×1.2…")` espera `~1200/4800` (×1.2) — la base 1000/2000/4000/8000 queda inferida de ambos; NO hay test con jitter neutro ×1.0 que pinne los literales 1000/2000/4000 directamente. El plan escribe ambos números juntas (secuencia y ±20 %); la fuerza equivale de conjunto, forma distinta.
- «SIEMPRE en `[800…30_000]`» ✓: `it("todo resultado dentro de los límites [800, 30000] para intentos 1..20")` (bucle 1..20 con `aleatorio` fijo) + `it("…800 ms mínimo (E8.30 límite)")` (mínimo del intento 1 = 800) — el extremo inferior y el rangolote pinnados.
- «cap 30_000» ✓: `it("CAP 30 s alcanzado: base ya en cap (intento ≥ 6)")` — 30000 exacto para intentos 6 y 7.
- Mitades [S]: «tras reconectar set_filters REENVIADO + refetch 1ª página» — CITADA por el plan (pareja E7.11 qa_c7, «no re-probada»); en [L] el reenvío de `set_filters` tras cambio/apertura sí está pinado (`it("set_filters al abrir y tras cada cambio de filtro; reconexión reusa la ruta")` — `envios.length` 1→2). ✓
- «Reconectando (intento N)…» / refetch visual → MANUAL-Q23 (declarado).

### QP-8.33 — E8.33 `frameNoBatchIgnorado` — **CUBIERTO CON MATIZ** (wiring)
`client.test.ts` — `parsearFrame` (defensiva R8.27):
- «no-JSON, JSON ajeno y shape roto → IGNORADOS sin throw» ✓: `it("no-JSON, JSON ajeno o shape roto ⇒ null SIN lanzar (ignorado)")` — 10 entradas: `"no es json"`, `"[1,2,3]"`, `{"type":"ping"}`, `{"type":"batch"}` (sin claves), batch_id no-string, project_id no-string, `logs` no-array, `null`, `undefined`, `""` — todas `toBeNull()` (sin lanzar). Más exhaustiva por tipos que el plan («≠5 claves»): pin extra de TIPOS por campo.
- «logs vacíos ES frame válido» ✓: `it("logs vacíos (batch sin novedades) ES frame válido")` — pin fuera de la letra del plan (positivo).
- MATIZ (BAJA — H-C8-2): «ignorados **sin throw ni CIERRE**; lista intacta» — el «lista intacta» y el «sin cierre» SOLO se pinan en la función pura (`parsearFrame` → `null`); NINGÚN test entrega un frame malformado por `stub.onmessage` del `ClienteWsLogs` para pinar que la sesión viva cierra el envío defectuoso sin cerrar el WebSocket. La pureza cubre el «no throw»; el «sin cierre en la sesión» no queda ejercitado (el plan no exigía explícitamente el wiring, pero el aserto del caso lo formula en términos de la sesión).

### QP-8.14 — E8.14 `bearerEnTodoLoginExceptuado` [L] — **CUBIERTO**
`api/client.test.ts`:
- «todo request lleva `Authorization: Bearer <jwt>`» ✓: `it("Bearer en todo MENOS login (bearer false)")` — `headers` `toMatchObject({Authorization: "Bearer secreto"})`.
- «login (`bearer:false`) NO lo lleva» ✓: `headersLogin["Authorization"]).toBeUndefined()` + `Content-Type: application/json` (extra: el POST sí viaja JSON).
- «su 401 cae inline (sin interceptor)» ✓: `it("con bearer:false (login): NO pasa por el interceptor (única excepción R8.8)")` — `llamados` vacío.
- Mitad [S]: `case_e8_14` marca E8.14 con la nota del plan §6 («el Bearer del navegador no es observable por curl»; el smoke certifica status íntegros con token) ✓.

### QP-8.15 — E8.15 `cuarentaYUnoNuncaParseaBody` — **CUBIERTO**
- «mock fetch que CRASHEA si `json()`/`text()` se invocan en el camino 401 (§2.A-6)» ✓ EXACTO: `resp401Trampa()` — ambas funciones marcian y dejan bandera; `it("con bearer y interceptor registrado…")` pinea `resp.__leido()).toBe(false)` (aserto de NO-invocación) + `ApiError` con `status 401` / `message "HTTP 401"` + interceptor invocado + `fetch` una sola vez.
- «decisión SOLO por status» ✓: ninguna lectura del cuerpo en el camino 401 (bandera).
- «ninguna `U+FFFD` por el camino 401» ✓ por construcción del trampa (el body jamás se decodifica).

### QP-8.16 — E8.16 `parseoToleranteNoDoscientos` — **CUBIERTO**
`describe("no-200 tolerantes")`:
- «405 HTML → fallback `"HTTP 405"`» ✓: `it("405 en HTML (Spring): fallback 'HTTP 405'")` — Whitelabel HTML → `code` undefined, `message "HTTP 405"`.
- «502 texto plano» ✓: `it("502 en texto plano de proxy: fallback 'HTTP 502'")`.
- «cuerpo vacío → fallback» ✓: `it("cuerpo VACÍO (300/409 sin body): fallback 'HTTP <status>'")` (pinnado con 409 — el plan decía 300/409 en §3.4; la forma es la misma).
- «404 JSON parseado (code/message/field)» ✓: `it("404 JSON: code/message/field PARSEADOS")` — `not_found` / «El log no existe» / `field: "id"` conservados (passthrough literal, sin reescritura — R8.17 de captura).
- «red caída → `status: 0`» ✓: `it("fetch rechaza ⇒ ApiError status 0 message 'HTTP 0'")` (QP-8.17/§2.A-6).

### QP-8.17 — E8.17 `vocabularioErroresALaUI` [L] — **CUBIERTO CON MATIZ**
- Lo pinable en lógica pura: la CAPTURA de `code`/`message`/`field` SIN reescritura y el fallback `"HTTP <status>"` + `status 0` ✓ (los `it` del párrafo anterior). El mapeo hacia los LITERALES de UI (banner 400/404/409/403, sin `project_archived` al panel) toca DOM → [MANUAL-Q23] como el plan degrada. MATIZ documentado en el plan (col. «banners → demo C9»), no hueco.
- EXTRA del guion: `case_e8_crud` marca un veredicto `E8.17` [S] (409 name_duplicate / 400 field_required) — ver H-A2: ese ID del guion NO es el E8.17 del plan (vocabulario) sino el material de QP-8.45.

### QP-8.21 — E8.21 `filtrosEnQueryStringPersisten` [L] — **CUBIERTO**
`useLogFilters.test.ts`:
- «ida↔vuelta idempotente» ✓: `it("ida y vuelta IDEMPOTENTE — parse(search(search(f))) === f")` — con `from`/`to` offset ±05:00/+02:00, las 6 claves + 2.ª vuelta estable.
- «solo claves con valor, orden estable» ✓: `it("solo claves con valor, orden estable…")` — igualdad EXACTA del string decodificado `project_id=3\nenvironment=production\nlevel=ERROR\nfrom=…\ntext=upstream` (el ORDEN de la query SÍ pinnado aquí; el hueco de orden es del `set_filters`, no de la query).
- «defensiva: query vacía ⇒ nulls; valor inválido ⇒ null» ✓: `it("query VACÍA ⇒ filtros nulls; id/valor inválido ⇒ null")` — `project_id=12ab`/`level=CRITICAL` → null (pin fuera de la letra del plan, positivo).
- «from==to legal» ✓: `it("from == to es legal (rango de un punto)")`.
- Parte visual (F5/compartir pestaña) → MANUAL-Q23; el destino por defecto `/`→`/logs` de QP-8.3 es mitad navegada (MANUAL, declarado por el plan).

### QP-8.23 — E8.23 `rangoDesdeHastaOffset` — **CUBIERTO** (1 matiz menor)
- «`datetimeLocalAIsoConOffset` viaja ISO-8601 CON offset local (offset INYECTADO, TZ fija del test)» ✓: 4 `it` de `datetimeLocalAIsoConOffset` — `("2026-10-19T10:30", -120) → "2026-10-19T10:30:00+02:00"`, offset -05:00/+00:00, «IDEMPOTENTE: un ISO ya con offset viaja TAL CUAL (sin doble offset)» (también `Z` intacto), «minuto-only añade `:00`; cadena ajena viaja sin romper». MATIZ nomenclatura: el plan ejemplifica con `2026-10-26T10:30:00+02:00`; el test usa la MISMA forma con 2026-10-19 — equivalente de fuerza.
- «from==to legal» ✓ (useLogFilters, arriba) y «from>to → 400 `filter_from_to_inverted` SIN field» ✓ [S]: `case_e8_23` — 400 + `code=filter_from_to_inverted` + **AUSENCIA de `field` asertada positivamente** (`campo_json field → e="field-PRESENTE"`). Offset −03:00 aceptado 200 ✓.

### QP-8.24 — E8.24 `keysetCargarMasFinDeResultados` [L] — **CUBIERTO**
`usePagination.test.ts`:
- «acumulación sin recargar / ACUMULADA» ✓: `it("acumula páginas y guarda el nuevo cursor (E8.24)")` — `[10,9]` + `[8,7]` → `[10,9,8,7]` con cursor avanzado (R8.23 citado en el comentario del test).
- «NUNCA muta» ✓: `it("NUNCA muta el acumulado previo")`.
- «`next_cursor: null` ⇒ fin» ✓: `it("next_cursor null ⇒ fin de resultados (tras ≥1 página)")`.
- «vacío legítimo ≠ error» ✓: `it("vacío legítimo: {items: [], nextCursor: null} (estado ≠ 404 ≠ error)")` — MATIZ menor: `esFinDeResultados(paginaInicial)=true` con la página SIN pedir (el test lo declara «null ⇒ fin (sin páginas aún)»); la semántica fin-tras-≥1-página y la fin-tras-inicio unificadas.
- «primeraPagina reemplaza TODO» (refetch reconexión) ✓: `it("primeraPagina reemplaza TODO (cambio de filtros / refetch reconexión)")`.
- Mitad [S]: `case_e8_20` — 50 items, cursor 2.ª página disyunta (set-intersección vacía), `next_cursor` null por `campo_json_nul` EXPLÍCITO ✓.

### QP-8.37 / QP-8.47([L]) — comparador trim — **CUBIERTO**
`Modal.test.ts` (8 `it`, `coincideLiteralConTrim` — D-C8-9):
- «`coincideLiteralConTrim("DELETE /logs/<id> · header X-Confirm-Log-Id: <id>", "  …  ")` TRUE (trim inicio/final)» ✓ — DOS `it`: `it("espacios al INICIO y al FINAL se recortan (trim) → true")` (espacios y `\t`/`\n`) y `it("sentencia real de 8b con espacios en los extremos → true (E8.38 flujo)")` con la sentencia LITERAL de 8b del plan.
- «texto incompleto/distinto → false» ✓: `it("texto incompleto → false")` (sentencia sin el `: <id>` final) + `it("texto distinto (una letra de diferencia) → false")`.
- EXTRA del writer (positivos, dentro del aserto «LITERAL exacto»): `it("case-sensitivity: NO se normaliza capitalización")` (`Payments-Service` → false), `it("espacios INTERNOS NO se normalizan")` (doble espacio → false — pin fuerte que distingue trim de normalización), `it("cadena vacía contra esperado no vacío → false")`.
- «habilitación SOLO con coincidencia» / botón rojo → demo C9 (declarado).

### QP-8.48 / QP-8.49 — E8.48/E8.49 charset C39 ([L]) — **CUBIERTO** (+ el 3.er método que reconcilia la cifra)
`EntryPointCharsetTest` (unit puro, MockHttpServletResponse, ObjectMapper real, handlers con `(null, resp, null)`):
- `Panel401.el401SaleEnUtf8ConHeaderCharsetYLaOCorrecta` — «Content-Type EXACTO» ✓ (`isEqualTo("application/json;charset=UTF-8")`) + `getCharacterEncoding()` UTF-8; «bytes `0xC3 0xB3` PRESENTES y `0xF3` aislado AUSENTE» ✓ (`hex.contains("c3b3")` / `doesNotContain("f3")`); «sin `U+FFFD` tras relectura UTF-8» ✓ (`doesNotContain("�")`) + cuerpos verbatim («Autenticación requerida», `"code":"invalid_token"`) — los (a)/(b)/(c) del plan §2.B-7 byte-exactos.
- `Panel401.accessDeniedHandlerMismoCaminoMismoCharset` — TERCER método (EXTRA del writer): pinna que el `handle(...)` del `denyAll()` de las cadenas 1/3 (CD-1) sale por el MISMO camino/charset (mismo cuerpo + `c3b3`, sin `f3`) — es la 3.ª prueba que el plan §4 dejó «a reconciliación»: responde al modelo D-C8-14 de «2 escenarios» sumando la higiene del camino AccessDeniedHandler.
- `Ingesta401.el401DeIngestaSaleEnUtf8ConLaACorrecta` — simetría D1 ✓: status 401 + Content-Type exacto + `c3a1` presente / `f3` ausente / `e1` ausente + sin U+FFFD + «API key de ingesta ausente o inválida» + `"code":"api_key_invalid"`.

### QP-8.3 — E8.3 [L] — **CUBIERTO** (mitad navegada MANUAL declarada)
- «bidireccional filtros⇄query IDA-Y-VUELTA idempotente» ✓ (los 2 primeros `it` de useLogFilters arriba; `/logs?level=ERROR&text=timeout` forma cubierta por la ida-exacta y la vuelta).
- «destino por defecto `/`→`/logs`» — mitad navegada → pareja MANUAL QP-8.1/QP-8.5 (declarada por el plan §1).

### QP-8.11 — E8.11 [L] — **CUBIERTO** (mitad manual declarada)
- «camino 401 con SOLO `response.status`» ✓ (absorción QP-8.15 — `resp401Trampa` + `__leido()==false`, MISMO caso `api/client.test.ts`).
- «callback descarta token + redirect … banner literal» — el CALLBACK invocado ✓ (`llamados == ["cb"]` y repetición por 401 en `it("repite el callback en cada 401 (idempotencia por repetición, R8.16)")`); lo que hace el callback (descarte/redirect/banner) es mitad [MANUAL-Q23] declarada por el plan.

### QP-8.31 — E8.31 [L] — **HUECO declarado-condicional** (la rama pura no se extrajo/testeó)
El plan asignaba «[L] `ws/client.test.ts `(rama inaugural — **si el módulo la extrae pura**)». `client.test.ts` cubre handshake-literal, https, cerrar-idempotente, `aplicarFiltros`-pre-conectar y reenvío — NINGÚN test ejercita el cierre inaugural → `alCierreInaugural` → `me()` → 401-vs-200 (D-C8-5.6): los callbacks pasan como `() => {}` sin aserto. Queda **sin cobertura [L]** — a nota del qa-report (la mitad [S] sí está, ver §3).

### QP-8.33-adjacente y restos [L]
- `ws/client.test.ts` extras fuera de la letra del plan (positivos): handshake literal `ws://host/ws/logs?token=<encodeURIComponent>` (S8-5 v1 query-param), `wss://` en https, `cerrar()` idempotente close(1000), `aplicarFiltros` antes de conectar no lanza ni envía (readyState ≠ OPEN).
- `api/client.test.ts` extras (positivos): `it("204 ⇒ undefined SIN parsear body (revocar key)")` con `text()` que RECHAZA si se lee (no-invocación sobre 204 — materializa el patrón R8.14 extendido), `it("headers extra viajan (X-Confirm-Log-Id — S8-2)")`.

## 3. Auditoría por aserto — bloque [S] (`scripts/qa_c8_smoke.sh`)

Estructura (coherente con el plan §1/§2.B): autocontenida `down -v` +
`up -d --build` (Q13 — ÚNICO comandante de `down -v`) → `esperar_healthy`
(sondeo `/me` → 401 `invalid_token`; 480 s) → `pila_saludable` 3×healthy por
podman (NUNCA /actuator) → smoke-of-the-smoke + login admin → pre-fase (POST
project 201 + regenerate + capture id/key) → siembra (65 logs keyset en 2
lotes + 1 FATAL con exception/metadata) → cases REST → (si `CON_ORIGIN=1`)
cases WS → veredictos + Q23 BLOQUEADO. Guards honestos: sin `.env`/credenciales
→ TODOS `BLOQUEADO` (bloqueo_env); pila no levanta → TODOS `BLOQUEADO`
(bloqueo_pila); sin `FRONTEND_ORIGIN` → WS `BLOQUEADO` con nota R-1 (patrón
R-7.j), REST corre. Higiene S16: tmpdir `mktemp -d` + `chmod 700` + trap EXIT,
`F_TOKEN`/`F_KEY` sin consola, keys impresas solo en comentarios de tamaño
(`[pre] … $TMPDIR_QA` — ruta, no valor) ✓. Needles `smoke-c8-<TS>-*` ✓. Cliente
WS `ws8_client.py` heredado (stdlib: handshake + Accept verificado matemáticamente,
frames MASKED, responde 0xA a 0x9, DRENA el cuerpo post-handshake para
contarlo).

**Marcas emitidas**: 22 `E8.n` [S] (6, 51, 7, 19, 14, 15, 48, 49, 20, 24, 23,
26, 39, 43, 44, 34, 36, 41, 38, 47, 27, 31) + 1 `Q23` BLOQUEADO — el plan §4
esperaba «32 veredictos E8 [S] en PASS»: ver H-A3/H-A4.

### QP-8.6 / QP-8.51 — E8.6/E8.51 servido — **CUBIERTO**
`case_e8_6`: `/` y `/logs/123` → 200 + `content-type: text/html` ✓; assets
ABSOLUTOS (`(src|href)="/assets/`) en AMBOS ✓; ausencia de `/logs/assets/`
asertada positivamente en ambos ✓ — los asertos de §2.B-9 completos. (El
`text/html` va por `grep -qi` — case-insensitive, tolerable.)

### QP-8.7 — E8.7 login orden literal — **CUBIERTO CON MATIZ**
`case_e8_7`: 200 ✓; **orden literal `{token,username,expires_at}`** ✓ por
`json_claves_orden` (grep exacto `token|username|expires_at` — §2.B-3 por
posición, no re-serializado); `/me` 200 + role ADMIN + username ✓; `/me` sin
token → 401 + `invalid_token` ✓ (E8.19-marca, H-A1). MATIZ (BAJA — H-C8-3):
«`expires_at` ISO-8601 con `Z` (§2.B-3)» del aserto NO se pinna (el script
verifica el ORDEN pero no el formato del valor `expires_at`).

### QP-8.14 / QP-8.15 — E8.14/E8.15 [S] — **CUBIERTO CON MATIZ** (H-C8-B2 documentado en el guion)
`case_e8_14`: POST `/me` con Bearer → **401 TAL CUAL** (el status del error de
método viaja ÍNTEGRO por el proxy — aserto de decisión-por-status) ✓; DELETE
proyecto → status `(405|401|404)` tolerado ✓. Nota del propio guion
(H-C8-B2): el «405 HTML» del camino E8.15 no es alcanzable por `/api/v1` (el
dispatch de `/error` reentra en la cadena 3 `denyAll` → 401 real) — el 405
HTML queda cubierto por el mock Vitest de `api/client.test.ts` (405 HTML), de
forma que el par mock↔pila se reparte: mock cubre la FORMA del fallback, pila
cubre el STATUS tal cual. MATIZ aceptable, DOCUMENTADO en el comentario del
case (no silenciado). EXTRA menor: variable local `tolerante` (HTML/JSON)
calculada sin usarse en ningún aserto — código muerto, hygiene baja.

### QP-8.48 / QP-8.49 — E8.48/E8.49 charset [S] — **CUBIERTO**
`case_e8_48`: panel `GET /me` sin token → header grep exacto
`application/json;charset=UTF-8` ✓ + Python byte-exacto (0xC3 0xB3 presente,
**0xF3 aislado AUSENTE** contado con `raw.count(...) == 0`, extras 0xE1/0xD3
ausentes — beyond el plan, positivo) ✓; ingest `POST /ingest` sin
`X-API-Key` → 401 + charset + `api_key_invalid` + 0xC3 0xA1 presente /
0xF3-0xE1 ausentes ✓; la relectura UTF-8 (decode estricto + asserts de bytes)
materializa «no `U+FFFD`» ✓ — §2.B-7 completo por vía `xxd`/`od`-equivalente
(python sobre bytes crudos). El header se compone en el grep case-insensitive
(`-qi`) — MATIZ menor frente al `isEqualTo` del [L], que es el exacto.

### QP-8.20 / QP-8.24 — E8.20/E8.24 [S] — **CUBIERTO CON MATIZ**
`case_e8_20`: `GET /logs` sin parámetros → **50 items** ✓ (`ids_de_ultima_respuesta | wc -l`), `next_cursor` NON-null ✓ (`case "$cursor" in null|-|"") e=""` → error) — shape `{items, next_cursor}` implícito por campo; 2.ª página por `?cursor=<urlenc>` → ≥1 items + `next_cursor` **null EXPLÍCITO** vía `campo_json_nul` ✓; **DISYUNCIÓN de keys por set-intersección** (keyset ⇒ progresión sin duplicados) ✓. MATIZ (MEDIA — H-C8-5): «orden `received_at DESC` (psql)» y «delta = siembra del smoke» del aserto E8.20 NO se pinnan (el guion NO ejecuta psql — decisión H-C8-B3: todo por API del panel; la forma `id_por_correlacion` declara que el filtro C6 `text` NO matchea `correlation_id` crudo). El DESC queda implícito por el diseño del endpoint C6 y la cifra de la siembra, sin aserto.

### QP-8.23 / QP-8.26 — E8.23/E8.26 [S] — **CUBIERTO** (con la desviación del ID E8.26 — H-A2-bis)
`case_e8_23`: from −03:00 → 200 ✓; from>to → 400 + `filter_from_to_inverted` + **`field` AUSENTE positivo** ✓; `level=ALGO` → 400 + `filter_invalid_value` + `field=level` ✓. Marca extra `E8.26` (el plan asigna ese veredicto a MANUAL-Q23 con absorción del 400): el guion materializa la mitad REST y da veredicto [S] PROPIO — más fuerte que el plan, sin aserto perdido.

### QP-8.39 — E8.39 — **CUBIERTO CON MATIZ**
`case_e8_39`: `GET /projects?include=summary` → listado donde **`"summary" not in p`** (aserto de AUSENCIA con include ENVIADO — exactamente R8.18) ✓ + presencia de `id, name, status, api_key_prefix, has_key` ✓. MATIZ (BAJA): el «ProjectDto de 8 claves» del §2.A-1 (que NINGÚN summary llegue y que sean EXACTAMENTE 8) queda como presencia de 5 + ausencia de summary, sin conteo de claves 8.

### QP-8.43 / QP-8.44 — E8.43/E8.44 — **CUBIERTO CON MATIZ** (2)
`case_e8_43`: regenerate → **201 + orden literal `key|prefix|created_at`** por `json_claves_orden` ✓ (§2.B-3); ficha refrescada `has_key=true` ✓; revoke → **204** ✓; `has_key=false` + `api_key_prefix` **null EXPLÍCITO** (`campo_json_nul` — el guion usa la variante null-aware exactamente para esto) ✓; re-generate para el WS ✓. MATIZ-1 (BAJA — H-C8-4): «key matchea `plk_<43 chars Base64url>`» NO se pinna en ningún punto (pre-fase ni case) — la key solo se captura. MATIZ-2 (MEDIA — H-C8-5): «ingesta con la key revocada → **401**» del aserto QP-8.44 NO se materializa (tras el revoke se regenera la key de inmediato para el WS; NINGÚN case intenta una ingesta con la key revocada). El 401-de-ingesta SIN key sí existe (case_e8_48), pero el pin del REVOKED-401 falta.

### QP-8.34 / QP-8.36 — E8.34/E8.36 — **CUBIERTO CON MATIZ**
`case_e8_34`: fila exacta por needle+correlation (vía API — H-C8-B3) → GET detalle 200 → los **11 campos** del LogDto (los 9 + exception + metadata) POR PRESENCIA + `exception`/`metadata` NON-null ✓ (sembrado con exception/metadata, §2.B). MATIZ (BAJA — H-C8-6): «11 campos **EN ORDEN** del DTO literal» — el Python verifica membresía (`faltan`), NO el orden del DTO en el JSON crudo (y el [L] tampoco pinna orden de claves de respuesta — coherente con §2.A-1: «para respuestas parseadas el aserto es de campos presentes/ausentes… un FAIL de orden real de respuesta se señala al caso [S]»; AQUÍ ni ese [S] existe — el pin de orden queda en ninguna parte). `GET /logs/99999999` → 404 + `code=not_found` ✓ (E8.36 completo).

### QP-8.41 — E8.41 — **CUBIERTO CON MATIZ** (H-C8-7)
`case_e8_41`: PATCH `{"status":"ARCHIVED"}` → 200 + label en la respuesta ✓; ingesta sobre archivado → **403** + presencia de `project_archived` ✓; PATCH ACTIVE → 200 ✓ (NOTA de ORDEN del plan respetada: el patch queda ANTES de los delete-negativos que usan el id/key). MATIZ (BAJA): «403 `{"code":"project_archived"}`» — el script pina la PRESENCIA de `project_archived` (grep), NO la EXACTITUD del cuerpo (mismo patrón que el H10 de C7). «jamás DELETE para archivar» ✓ implícito (solo PATCH en el case; el DELETE va como negativo en case_e8_38 con el proyecto sobreviviente).

### QP-8.38 / QP-8.47 — E8.38/E8.47 — **CUBIERTO CON DESVIACIÓN PINNADA (H-C8-B2)**
`case_e8_38`: los 3 DELETE (`/panel/logs/{id}` con `X-Confirm-Log-Id`, `/panel/projects/{id}` y `/panel/projects/{id}/logs` con `X-Confirm-Name`) con las cabeceras **YA FORMADAS** (S8-2) ✓ + **el proyecto y el log SOBREVIVEN** (extra de «sin fingir éxito», §5) ✓. **DESVIACIÓN del aserto del plan**: el plan dice «405 ó 404 TAL CUAL (§2.B-6), jamás 200/202»; el guion tolera **401 ADEMÁS** de 405/404 (`grep -qE '^(405|404|401)$'`) y documenta EN EL GUION el porqué (H-C8-B2: C10 no existe y el dispatcher `/error` reentra en la cadena 3 `denyAll` → 401 real; 405/404 no alcanzables vía `/api/v1`). La dirección del aserto es más laxa que el plan — es un hallazgo para el qa-report: si la pila respondiera 401 por el denyAll, el veredicto PASSará igualmente; el aserto del plan («405/404 OR exacto») y el del guion («405/404/401 OR») difieren. La nota de desviación es honesta y el status real queda CAPTURADO en el veredicto (`$r_log`, `$r_proj`, `$r_purga` se imprimen), de modo que el qa-report puede certificar cuál se materializó. No falso-PASS de status oculto, pero sí un cambio de contrato del PLAN al GUION.
- QP-8.47 [L] (comparador con trim) ✓ vía Modal.test.ts (§2); «`?include=summary` aceptado SIN summary» ✓ vía case_e8_39.

### QP-8.31 — E8.31 [S] — **CUBIERTO CON MATIZ** (cuerpo del 403)
`case_e8_31`: sin token → `HS 401 BODY 0` ✓ (**cuerpo VACÍO asertado CON DRENAJE post-handshake** — más fuerte que solo status; materializa §2.B-8 en la 401); Origin `http://evil.example` → `HS 403` ✓ (status tal cual, canon E7.9). MATIZ (MEDIA — H-C8-8): el plan §2.B-8 y la fila QP-8.31 pegan «401/403 pre-upgrade **con cuerpo VACÍO**» para AMBOS; el guion SOLO aserta el cuerpo de la 401 y su comentario DECLARA que la 403 de Origin ajena lleva el cuerpo estándar «Invalid CORS request» (DefaultCorsProcessor — R8.47 nota de despliegue) que NO llega a estar vacío. El aserto del plan es más fuerte que la realidad de la pila en la rama 403 — el guion lo dejo sin aserto (solo status) y lo documenta; para el qa-report la discrepancia del plan debe declararse.

### QP-8.27 — E8.27 [S] — **CUBIERTO CON MATIZ** (2)
`case_e8_27`: cliente long → `hs-ok` + `sf-sent` (set_filters de 6 claves compuesto literal por el guion, §2.B-8 «NO-2xx del `set_filters` nunca ocurre» materializado como aserto de AUSENCIA de frames extra) ✓; ingesta 202 → **EXACTAMENTE 1 frame** (`nf -eq 1` con drenaje de 25 s — aserto de unicidad) ✓; frame byte-shape §3.5: 5 claves top-level EXACTAS ✓ (`set(frame.keys()) == {"type","batch_id","project_id","received_at","logs"}`), `batch_id` regex ✓, `project_id` == id del proyecto ✓, LogDto 11 por elemento ✓ + exception/metadata null ✓ + needle/level/source/correlation exactos ✓; `batch_id` ÚNICO (set de batch_ids == número de frames) ✓.
- MATIZ-1 (H-C8-9): «**EN ORDEN** {type, batch_id, …}» del §2.B-5 — el Python usa `set(...) ==` (SIN orden); el «byte-exacto» de orden queda sin pin, igual que en [L] (H-C8-1). El CONTENIDO de las 5 claves sí es exacto.
- MATIZ-2 (H-C8-10): regex del `batch_id` `^b_[0-9a-fA-F-]{36}$` es MÁS LAXA que la del plan §2.B-5 (`^b_[0-9a-f]{8}-[0-9a-f]{4}-4…` UUID **v4** minúsculas): NO pinna el dígito de versión v4 ni las minúsculas. El [L] fija `b_1a9f4c2e` sin formato UUID. El pin de «UUID v4 minúsculas» NO queda cubierto en [L] ni [S].

## 4. Transversales §2.A/§2.B y parejas del spec §3

| Transversal / pareja | [L] | [S] | Veredicto |
|---|---|---|---|
| **2.A-1** DTOs por presencia/ausencia (8/9/11 claves) | api + smoke E8.34 (presencia 11), E8.39 (5 + ausencia summary), `case_e8_crud` (detalle SIN summary) | conteo exacto 8/11 NO pinado (presencia/ausencia sí) | CUBIERTO-CON-MATIZ (H-C8-3/H-C8-6) |
| **2.A-2 + S8-8** `set_filters` 6 claves/orden/nulls | dedup/client (6 + nulls + sin extras) | case E8.27 (aceptado sin eco) | PARCIAL (H-C8-1 — orden sin pin en NINGÚN canal) |
| **2.A-3** `fusionarEnVivo` D-C8-4 | dedup.test.ts (dedup/desc/empate/grupo/no-mut) | E8.27 (frame) | CUBIERTO |
| **2.A-4** `retrasoReconexion` D-C8-5.4 [800..30_000] | client.test.ts (extremo+bounds+cap 1..20) | CITADA (E7.11 qa_c7) | CUBIERTO (secuencia por extremos de jitter) |
| **2.A-5** `coincideLiteralConTrim` D-C8-9 | Modal.test.ts | — (Zona destructiva visual → C9) | CUBIERTO |
| **2.A-6** tolerancias fetch (401 NO-body / no-200 / fallback / status 0) | api/client.test.ts | case E8.14 (status íntegro por proxy), E8.48 (bytes crudos) | CUBIERTO (405 HTML solo en mock — H-C8-B2 del guion) |
| **2.B-3** órdenes literales de claves (login 200 / regenerate 201) | — | `json_claves_orden` en case_e8_7 / case_e8_43 | CUBIERTO (expires_at-Z sin pin — H-C8-3) |
| **2.B-5** batch_id v4 + único + frame §3.5 | client.test.ts (parser) | case_e8_27 | PARCIAL (H-C8-9/H-C8-10 — orden por SET, regex v4 laxa) |
| **2.B-6** 405/404 DELETE C10-negativos | — | case_e8_38 tolera 401 (H-C8-B2) | CUBIERTO-CON-DESVIACIÓN PINNADA |
| **2.B-7** charset D1 bytes | EntryPointCharsetTest | case_e8_48 (0xC3 0xB3 / 0xC3 0xA1, cero latin-1 aislado) | CUBIERTO |
| **2.B-8** 401/403 pre-upgrade cuerpo vacío | client.test.ts (handshake-literal [L], rama inaugural NO) | case_e8_31 (401 BODY 0 CON drenaje; 403 solo status) | PARCIAL (H-C8-8 — 403 con cuerpo «Invalid CORS request» según el guion; rama [L] HUECO) |
| **2.B-9** Servido / base absoluta | — | case_e8_6 | CUBIERTO (E8.52 mockups/ws-console SIN case — HUECO) |
| **S16 higiene** (tmpdir 700/trap/0 hits plk_eyJ) | — | tmpdir+trap ✓; grep plk_/eyJ post-ejecución como el plan §5.4 lo declara (fuera del guion) | CUBIERTO |

Parejas del spec §3 (mapa del plan §1): pareja 1 (E8.6↔E8.1/E8.5 — [L] en useLogFilters + [S] case_e8_6) ✓; pareja 2 (E8.7/E8.18↔E8.8–10 — login [S] case_e8_7; E8.18 MAY sin materializar, tolerado) ✓; pareja 3 (E8.27/E8.30/E8.31↔E8.29/E8.32 — [L]+[S] ambas mitades de E8.27 y E8.31) ✓ con los matices H-C8-1/8/9/10; pareja 4 (E8.48/E8.49↔—) ✓; pareja 5 (E8.38/E8.47↔E8.37 — [L] Modal + [S] case_e8_38 con desviación 401) ✓; pareja 6 (E8.51–E8.55↔R8.52 — E8.52 sin case, HUECO) parcial.

## 5. Cifras para el qa-run (contadas del código, NO ejecutadas)

| Canal | Contado por este auditor | Plan | Concurso |
|---|---|---|---|
| **mvn** | `EntryPointCharsetTest` = **3 `@Test`** contados (2 en `@Nested Panel401` + 1 en `@Nested Ingesta401`) ⇒ **177/177 esperado** = 174 heredados C7 + 3 de C39 | 177 (con la discrepancia abierta 176-vs-177 si C39 aportara 2) | **RECONCILIADO**: la 3.ª prueba EXISTE y es `Panel401.accessDeniedHandlerMismoCaminoMismoCharset` (higiene del camino `denyAll`/`handle` — CD-1). La discrepancia del plan §4 se CIERRA sin desviación; el qa-report debe certificar 177 RAW y NOMINAR el 3.er método. |
| **Vitest** (`npx vitest run`) | **55 `it(`** contados: `dedup.test.ts` 7 · `client.test.ts` 15 · `usePagination.test.ts` 5 · `useLogFilters.test.ts` 9 · `api/client.test.ts` 11 · `Modal.test.ts` 8 → **55 real = 55 previsto** | 55 / 6 ficheros | **CONCIDE en el TOTAL** (55) y en el nº de ficheros (6); el reparto por fichero difiere del previsto (§4 del plan): dedup 7 (≈8), client 15 (≈10), usePagination 5 (≈7), useLogFilters 9 (≈12), api 11 (≈10), Modal 8 (=8) — MATIZ de estimación, no de cifra. CERO `test(` en los 6 ficheros (solo `it(`). |
| **smoke** | **22 veredictos `E8.n` [S]** + 1 `Q23` BLOQUEADO | 32 `E8.n` [S] en PASS | **DISCREPANCIA (H-A4)**: el guion marca 20 IDs REST + 2 WS; le FALTAN para la cifra del plan E8.18, E8.25([S]), E8.30([S]-citada), E8.42, E8.45, E8.46, E8.52, E8.53, E8.55, E8.57, E8.59 — de los cuales E8.53/E8.55/E8.57/E8.59 viven (per plan) en el orden formal/pre-fase fuera de los marks, pero **E8.42, E8.45, E8.46, E8.52 y las mitades [S] de E8.18/E8.25 son CASES SIN veredicto o SIN case** (ver §6). El guion ADEMÁS marca `E8.26` (extra) y reasigna Q23 los E8.45/E8.46/… en su nota final. |
| **exit** | `EXIT 0/1` SOLO (header del guion); BLOQUEADO (env/pila/origin) NO altera el exit | exit 0/1/2 (plan §1/§6: pre-fase falla ⇒ exit 2, «jamás falso PASS»; higiene ⇒ exit 2) | **DESVIACIÓN (H-A3)**: pre-fase fallada ⇒ 22 BLOQUEADO + `Q23` ⇒ `fails==0` ⇒ **exit 0**. El detalle queda VISIBLE en los veredictos (nada falso-PASS de asertos), pero el gate de exit 2 del plan no existe; QP-8.55/QP-8.57 (y el criterio de BLOQUEADO exit 2, §6-c) NO se materializan como Código de salida. |

## §Final. Hallazgos y dudas para el qa-report (bucle apply/QA — NO los resuelvo)

| # | QP-x / transversal | Hallazgo | Severidad |
|---|---|---|---|
| **H-A1** | QP-8.19 vs marca `E8.19` del guion | El veredicto `E8.19` del guion = «/me sin token → 401» (mitad [S] ABSORBIDA de QP-8.2/8.14 por el plan), NO el caso `sinCorsRutasRelativas` del plan (CERO OPTIONS / rutas relativas). El caso del plan NO tiene veredicto; el ID reutilizado con otra semántica — el qa-report debe señalar el QP-8.19 como SIN materializar y el QP-8.2/8.14 como receptor de la marca | MEDIA (mapeo de IDs) |
| **H-A2** | QP-8.17/8.45 vs marca `E8.17` del guion | El veredicto `E8.17` del guion = 409 name_duplicate / 400 field_required (material de QP-8.45), NO el «vocabularioErroresALaUI» del plan (cuya mitad [L] está en api/client.test.ts). El guion deja a QP-8.45 sin su propio veredicto (y SIN el aserto 201-prefix/has_key). QP-8.45 queda parcialmente SIN cobertura [S] con veredicto | MEDIA (mapeo de IDs) |
| **H-A3** | QP-8.55 / QP-8.57 / §6 exit-codes | El guion SOLO sale 0/1 (header «Exit: 0 = sin FAILs»); el exit 2 del plan (pre-fase fallida / credenciales / higiene) NO existe — pre-fase fallando ⇒ 22 BLOQUEADO + exit 0. El QA del criterio «jamás falso PASS» decompone: los asertos NO pasan de falso (BLOQUEADO visible), PERO el gate binario del plan (exit 2) no se materializa | MEDIA |
| **H-A4** | Cifra §4 | 22 veredictos [S] vs 32 previstos: sin case/veredicto E8.42, E8.46, E8.52 (y E8.18 MAY, E8.25-[S], E8.45-parcial). El guion declara en su nota Q23 que cubre `E8.45/E8.46/E8.55` por demo C9 — la REASIGNACIÓN no figura en el plan §3 (debe declararse en el qa-report) | MEDIA |
| **H-A5** | QP-8.31 [L] | La rama `alCierreInaugural`→`me()` (401-vs-200, D-C8-5.6) SIN ningún test — el plan la condicionó («si el módulo la extrae pura») y NO se extrajo ni se testeó: ninguna cobertura [L] | MEDIA (hueco declarado-condicional) |
| **H-C8-1** | QP-8.27 / §2.A-2 | Orden de las 6 claves del `set_filters` SIN pin (toEqual + keys-length en [L]; en [S] el set_filters lo compone el guion, no el cliente) y orden de las 5 claves del frame por `set` (case E8.27) — el «EN ORDEN» de S8-8/§3.5 queda sin ancla en NINGÚN canal (el [S] de login/regenerate SÍ tiene pin de orden vía `json_claves_orden`) | MEDIA |
| **H-C8-2** | QP-8.33 | El wiring «frame malformado en sesión viva SIN cerrar» no ejercitado (solo la función pura `parsearFrame`) | BAJA |
| **H-C8-3** | QP-8.7 | «`expires_at` ISO-8601 con `Z`» sin aserto (solo el orden literal) | BAJA |
| **H-C8-4** | QP-8.43 | «key matchea `plk_<43 chars Base64url>`» sin aserto en NINGÚN punto | BAJA |
| **H-C8-5** | QP-8.44 | «ingesta con la key revocada → 401» SIN materializar (tras revoke se regenera inmediatamente p/ WS) | MEDIA |
| **H-C8-6** | QP-8.34 | «11 campos EN ORDEN» → presencia (membresía) sin orden; idem ProjectDto-8-claves en E8.39 (presencia 5 + ausencia summary, sin conteo 8) | BAJA |
| **H-C8-7** | QP-8.41 | `{"code":"project_archived"}` pinnado por PRESENCIA de `project_archived` (grep), sin exactitud del cuerpo (idem H10 de C7) | BAJA |
| **H-C8-8** | QP-8.31 [S] / §2.B-8 | El plan pega «401/**403** pre-upgrade con cuerpo VACÍO»; el guion aserta BODY 0 SOLO en la 401 y DECLARA que la 403 lleva «Invalid CORS request» (DefaultCorsProcessor) — el aserto del plan sobrecarga la 403 | MEDIA |
| **H-C8-9** | QP-8.27 | Orden top-level por `set(...)` en Python (§2.B-5 «EN ORDEN») — sin pin de orden | BAJA (repetición del H-C8-1 en [S]) |
| **H-C8-10** | QP-8.27 / §2.B-5 | Regex del `batch_id` `^b_[0-9a-fA-F-]{36}$` más laxa que la del plan (UUID v4 minúsculas con dígito-4 sin pin) | BAJA |
| **H-C8-11** | QP-8.20 | «orden `received_at DESC` (psql)» y «delta = siembra» sin aserto (el guion NO ejecuta psql — decisión H-C8-B3 documentada: API-only; el conteo de 50 y la disyunción de keys SÍ pinnados) | MEDIA |
| **H-C8-12** | QP-8.28 | NINGÚN par del fichero difiere SOLO en micros más allá de millis — la resolución-a-micros del comparador queda inferida (comparación de string ISO), sin aserto dedicado `.204123` vs `.204124` | BAJA |
| **N-A1** | QP-8.18 / QP-8.8 | Los dos MAY del plan (502→Reintentar; login 401 `invalid_credentials` REST) SIN materializar — el plan los preveía tolerados (R-7.k/Q23); quedan a nota del qa-report | INFO |
| **N-A2** | QP-8.50 [REVIEW] / 8.52 [REVIEW] / 8.54 / 8.58 / higiene-grep | Las mitades [REVIEW] (diff lockfile, política sin-E2E, docs/mockups intocable, 0-hits plk_/eyJ post-ejecución) viven en verify — SIN fichero de test, tal como el plan §5 lo declara | INFO |

- **Aislamiento de autoría (loop-back)**: ante un FAIL, cada aserto citado
  arriba es trazable a su QP-8.x por el nombre del `it`/método/case que
  materializa (prefijos `E8.nn` en los `describe`/comentarios de los `.test.ts`
  y cases `case_e8_NN` del guion). La UNICA zona de colisión de IDs es la
  marcada en H-A1/H-A2 (`E8.19` y `E8.17` con semántica distinta a la del plan
  §3): ahí el FAIL/loop-back debe citarse como «el QP del plan que PORTA el
  aserto», según la tabla de §1.
- Resumen de cobertura: de los 59 QP, **21 con pin [L] materializados** (8.3,
  8.11, 8.14, 8.15, 8.16, 8.17, 8.21, 8.23, 8.24, 8.25, 8.27, 8.28, 8.30,
  8.33, 8.37, 8.47, 8.48, 8.49, 8.50-live, 8.56, 8.31-[S]…) — en detalle,
  los 21 [L] del plan §3 tienen su pin EXCEPTO la rama inaugural de QP-8.31
  (H-A5) y el vocabulario-UI de QP-8.17 (mática declarada); **32 veredictos
  [S] del plan → 22 marcados por el guion** (matiz H-A4); las [MANUAL-Q23]
  y [REVIEW] quedan asignadas como el plan las dejó (demo C9/verify).
- Todo esto es AUDITORÍA: los matices y huecos van al bucle apply/QA; este
  artefacto NO parchea nada y NO ejecuta nada.