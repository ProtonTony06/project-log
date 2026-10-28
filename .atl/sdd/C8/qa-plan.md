---
project: Project_Log
change: C8
artifact: qa-plan
last_updated: 2026-10-28
status: draft
---

# QA Plan — C8 · Frontend del panel según maquetas (React + TS SPA + fix D1)

> Plan de QA del corte C8 derivado EXCLUSIVAMENTE de `spec.md` (R8.1–R8.59,
> cierres S8-1…S8-10, decisiones de gate D1–D4, escenarios E8.1–E8.59, mapa
> de parejas §3) y `design.md` (D-C8-1…D-C8-17, frontera de ficheros §4,
> Testing Strategy §6). Aislamiento de autoría: el plan NO se apoya en el
> código implementado ni en los tests escritos — a un test FAIL debe poder
> señalársele SU caso del PLAN (QP-8.x), no al revés. Los literales de
> contrato (DTOs, frames, códigos de 400, sentencias V11/V10, charset D1) se
> CITAN del spec/design; el plan NUNCA redacta literales nuevos.

## 1. Estrategia y cobertura

- **59 casos QP-8.x** (1:1 con E8.1–E8.59), con TRES niveles:
  - **[L]** — suite sin pila: **Vitest de lógica pura** (`npx vitest run`
    desde `frontend/`, entorno `node`, CERO jsdom/testing-library — D3/R8.56)
    y **mvn** (`mvn -f backend/pom.xml test`; el único test nuevo backend es
    `EntryPointCharsetTest` — C39/R8.48).
  - **[S]** — smoke canónico **autocontenido** `scripts/qa_c8_smoke.sh`
    (patrón qa_c2–qa_c7): bash/curl contra `BASE_URL=http://127.0.0.1:8080`
    por el ÚNICO puerto (nginx), veredictos `E8.n: PASS | FAIL |
    BLOQUEADO (detalle)` y **exit 0/1/2**.
  - **[MANUAL-Q23]** — lo que exige ojo humano o navegador real: visual de
    las 6 pantallas contra `docs/mockups/`, navegación del guard, estados §4.
    En el qa-report quedan **BLOQUEADO (Q23)** con instrucción para el
    **demo C9** — NO fallan exit (E8.58: sin E2E navegado, PROHIBIDO
    Playwright/Cypress/drivers).
  - Subtipo **[REVIEW]** (no-Q23, manual de verify): revisión de diff
    (lockfile — QP-8.54) y revisión de política (sin E2E — QP-8.58).

- **Cobertura de las 6 parejas mock↔pila del spec §3** (col. «Pareja» de la
  tabla §3). Absorciones DECLARADAS:
  - **QP-8.3 absorbe la pareja E8.6↔E8.1/E8.5 en su mitad [L]** (la query
    serializada que el retorno debe conservar es el mismo mapeo
    bidireccional que pinnea `useLogFilters.test.ts`); la mitad navegada de
    E8.1/E8.5 queda MANUAL-Q23 propia.
  - **QP-8.11 absorbe el pin 401-SOLO-status de QP-8.15** ([L]; MISMO caso de
    `api/client.test.ts` — un único punto de paso, D-C8-3) y **lleva la
    mitad manual** de su pareja E8.8–E8.10 (banner/destino/re-logueo).
  - **QP-8.22 absorbe el shape `set_filters` de QP-8.27 ([L]) y el pin
    cursor-reiniciado de QP-8.24**; su parte propia (orquestación de la
    página: esqueleto + refetch + reenvío en un solo gesto) queda
    MANUAL-Q23 — hueco declarado en §5.
  - **QP-8.26 absorbe el caso 400-con-`code`/`field` de QP-8.23** ([S]; el
    mismo case `E8-API` del smoke produce el banner-material: from>to →
    `filter_from_to_inverted` SIN field); la visualización del banner queda
    MANUAL-Q23.
  - **QP-8.31 absorbe los 401/403 pre-upgrade de QP-8.53/qa_c7** a nivel pila
    (MISMO backend, MISMA superficie `/ws/logs`); el distingue caminos del
    hook (`alCierreInaugural` → `me()`) vive en [L] `ws/client.test.ts`.
  - **Los E8.9, E8.10, E8.12, E8.13, E8.29, E8.32, E8.35, E8.40 NO tienen
    canal [L] materializable** dentro de la frontera de los 6 ficheros
    Vitest (D-C8-11/D3 prohíben jsdom; E8.58 prohíbe E2E navegado): sus
    Verificación «Vitest (hooks con mocks)» del spec §6-map se degradan
    EXPLÍCITAMENTE a **[MANUAL-Q23]** con pareja pila ([S]) donde existe —
    huecos enumerados en §5. Ningún caso queda sin responsable.

- **Smoke C8 AUTOCONTENIDO (Q13/E2.7)**: pre-fase `podman compose down -v &&
  podman compose up -d --build` (COMPOSE_BIN autodetectado). Implicaciones:
  1. es el ÚNICO comandante de `down -v` del corte: borra pgdata — las
     filas de smokes C5–C7 DESAPARECEN; el estado de BD con el que arrancan
     las re-corridas posteriores (qa_c3/qa_c7) es el SEMBRADO por el smoke
     C8 (sus needles `smoke-c8-<TS>-*` + proyectos de su E8-API) — los
     conteos relativos de los demás smokes siguen siendo válidos porque
     todos usan DELTAS, jamás conteos absolutos.
  2. el `--build` es LA GUARDIA DE COMPILACIÓN del corte (stage 1 del
     Dockerfile: `tsc --noEmit` strict + `vite build` — QP-8.55): un error
     de tipos DEBE matar la pre-fase con exit 2 (jamás falso PASS).
  3. el rollout NORMAL del corte (que NO borra datos) es distinto: plain
     `up -d --build`. El `down -v` es privilegio del smoke.
- **Orden de ejecución formal** (RAW a capturar en el qa-report, EN ESTE
  ORDEN):
  1. `mvn -f backend/pom.xml test` — gate MANDATORIO pre-smoke: **177/177**
     (174 heredados de C7 + 3 de C39) — el smoke NUNCA antes del mvn verde.
  2. `scripts/qa_c8_smoke.sh` — autocontenido (down -v + up -d --build), exit
     0 esperado (los BLOQUEADO-Q23 visuales NO cuentan para exit).
  3. `scripts/qa_c3_smoke.sh` — re-corrida de regresión, **SIN `down -v`**
     (la pila queda levantada por qa_c8; bd = siembra del smoke C8).
  4. `scripts/qa_c7_smoke.sh` — re-corrida de regresión WS, **SIN `down -v`**
     (su propio guard de recomposición debe detectar la pila ya healthy y
     continuar; handshake + frame contra el backend D1 intacto).
  - **qa_c2: NO se requiere** (decisión del plan, en la MAY de R8.59): sus Q
    de login/`/me` quedan re-cubiertos por E8-auth/E8-charset del smoke C8
    (mismos endpoints, mismo proxy) y su Q20 solo-handshake por qa_c7
    (E7.17) — correr qa_c2 AÑADIRÍA una recomposición sin valor nuevo. Se
    reserva como diagnóstico opcional si E8-auth suscitara dudas.
  - mvn post-smoke (solo-regresión, recomendada): re-corrida final para
    certificar E8.50/E8.59 una vez la pila pasó por los smokes.

## 2. Regla de asertos (transversal a los casos)

### 2.A Vitest [L] — lógica pura, literales SIN DOM

1. **DTOs literales por CAMPO-PRESENCIA y AUSENCIA, no por reorden en
   runtime**: la partida es el TIPO (R8.19): `ProjectDto` de **8 campos** —
   el `summary` de §3.3 NUNCA se declara ni viaja (S8-6); `LogListItemDto`
   de **9** (SIN exception/metadata — V6); `LogDto` de **11** (los 9 +
   `exception` + `metadata`); `LoginResponse {token, username, expires_at}`;
   `ApiKeyResponse {key, prefix, created_at}`; `LogsPagina {items,
   next_cursor}` con null explícito. El ORDEN literal de claves se exige en
   la SALIDA JSON que el client construye (`stringify` de `set_filters`) y
   en pila ([S]); para respuestas parseadas el aserto es de campos
   presentes/ausentes (la erasure de TS no da orden en runtime). Un FAIL de
   orden real de respuesta se señala al caso [S] correspondiente.
2. **`set_filters` (S8-8)**: el stringify pinneado en `ws/client.test.ts`
   produce EXACTAMENTE 6 claves EN ORDEN (`project_id, environment, level,
   from, to, text`), `null` donde no aplica, `project_id` number cuando
   hay valor, jamás `cursor`/`limit`.
3. **`fusionarEnVivo` (D-C8-4)**: pinnes de dedup por `id`, comparador
   global `received_at DESC, id DESC` CON MICROS (ISO-8601 string con `Z`),
   grupo del lote coherente, NO-mutación de entradas, idempotencia.
4. **`retrasoReconexion` (D-C8-5.4)**: secuencia de retrasos con
   `aleatorio` INYECTADO — 1 s → 2 s → 4 s … cap 30_000 ms; SIEMPRE en
   `[800…30_000]` con jitter multiplicativo ±20 %.
5. **Comparador de modales (D-C8-9)**: `coincideLiteralConTrim` — trim
   inicial/final, coincidencia LITERAL exacta; texto incompleto/distinto →
   false.
6. **Tolerancias del client (D-C8-3)**: mock fetch que **CRASHEA** si
   `json()`/`text()` se invocan en el camino 401 (aserto de NO-invocación);
   no-200 con `text()`+`JSON.parse` try/catch; fallback `"HTTP <status>"`;
   red caída → `status: 0`. Mapeo de errores (R8.17): literales citados,
   jamás reescritos.
7. CERO DOM: ningún caso [L] importa componentes que toquen `document`
   (jsdom PROHIBIDO — E8.58); los hooks de React sin extracción pura NO se
   testean (queda MANUAL-Q23, §5).

### 2.B Smoke [S] — pila real por :8080

1. **Needles sufijados** `smoke-c8-<TS>-*` por ejecución (logs, textos de
   proyecto — jamás colisionan con siembras de otro corte; la `-` es aguja,
   comparadas por `=`/`position()` en psql si hiciera falta).
2. **Conteos RELATIVOS**: psql-snapshot antes → delta después; jamás
   conteos absolutos (la bd arranca vacía por el `down -v`, pero la regla
   es canon y la re-corrida tras qa_c8 hereda la siembra del smoke).
3. **Órdenes literales de claves** en JSON crudo: login 200 →
   `{token, username, expires_at}`; regenerate **201 {key, prefix,
   created_at}** — verificado por posición en la cadena (grep del inicio
   de cada clave), no por parseteo re-serializado.
4. **Estados de BD cuando aplique**: `has_key = false` y `api_key_prefix IS
   NULL` tras revoke (psql) + ingesta con esa key → **401**; el proyecto
   archivado → ingesta **403 `project_archived`** (efecto derivado — sin
   frame en ventana) y desarchivado restaura; filas del keyset contadas por
   psql (delta) contra `next_cursor` recibidos.
5. **`batch_id` único (E8.27)**: matchea `^b_[0-9a-f]{8}-[0-9a-f]{4}-4…`
   (UUID **v4** minúsculas tras `b_`, 36 chars) y es DISTINTO en cada
   frame del guion (colección del tmpdir); frame batch byte-exacto §3.5 —
   5 claves top-level EN ORDEN y LogDto completo de 11 por elemento.
6. **401/405/404 de los DELETE C10-negativos (`X-Confirm-*`)**: el smoke
   ENVÍA la cabecera YA FORMADA (`X-Confirm-Log-Id: <id>` /
   `X-Confirm-Name: <name>` — S8-2) y pina el resultado TAL CUAL:
   **405 ó 404** (el OR exacto es el contrato hoy — el endpoint no existe),
   jamás 200/202, con el parseo tolerante aplicable (R8.15: el 405 de
   Spring puede venir en HTML — el aserto es de STATUS, no de cuerpo JSON).
   También `?include=summary` aceptado SIN `summary` en el DTO.
7. **Charset D1 (E8.48/E8.49)**: `Content-Type: application/json;
   charset=UTF-8` + bytes del cuerpo verificados por `xxd`/`od`:
   la `ó` presente como `0xC3 0xB3` y **`0xF3` aislado AUSENTE**; un
   `JSON.parse` del cuerpo no produce `U+FFFD`.
8. **WS por proxy**: handshake 101 + `Sec-WebSocket-Accept` correcto;
   401/403 pre-upgrade con **cuerpo VACÍO** (superficie distinta del 401
   REST — el fix D1 NO lo toca, R8.47); frame MASKED del cliente; NO-2xx
   del `set_filters` nunca ocurre (el server no responde nada — aserto de
   ausencia).
9. **Servido (E8.6/E8.51)**: `curl /` y `/logs/123` → `index.html`,
   `Content-Type: text/html` (SPA fallback — nunca 404 de servidor);
   assets ABSOLUTOS (`/assets/…`) en el HTML; NINGÚN asset bajo ruta
   anidada; `/ws-console.html` y `/mockups/…` caen al fallback (E8.52).
10. Higiene (S16): 0 hits `plk_/eyJ` en la salida + tmpdir 700 con trap —
    §6.

## 3. Tabla de casos QP-8.1…QP-8.59

Parejas del spec §3: 1 E8.6↔E8.1/E8.5 · 2 E8.7/E8.18↔E8.8/E8.9/E8.10 ·
3 E8.27/E8.30/E8.31↔E8.29/E8.32 · 4 E8.48/E8.49↔— · 5 E8.38/E8.47↔E8.37 ·
6 E8.51–E8.55↔R8.52. Nivel: [L], [S], [L]+[S] (dual) y [MANUAL-Q23];
[REVIEW] cuando el canal es la revisión de diff/política en verify.

| QP | E8.x | Caso | Nivel | Asertos obligatorios | Dónde (suite prevista / smoke) |
|---|---|---|---|---|---|
| QP-8.1 | E8.1 `nueveRutasRegistradas1a1` | 9 rutas AppFlow §1 1:1 + `/`→`/logs` | [MANUAL-Q23] | navegación de las 9 rutas sin 404 de servidor; `/` sin pantalla propia; `*` pinta 404 del panel | hueco declarado (§5): la «tabla de rutas» [L] del spec NO materializable en los 6 ficheros Vitest (jsdom/E2E prohibidos); pareja: smoke E8-servido (QP-8.6) |
| QP-8.2 | E8.2 `guardSinTokenRedirigeConDestino` | guard `GET /me` → `/login` con destino | [MANUAL-Q23] | sin render protegido durante la resolución; destino recordado (pathname+search) | hueco: hook-guard sin fichero Vitest frontera; mitad REST (`/me` sin token → 401) ABSORBIDA por QP-8.15/QP-8.48 ([S]) |
| QP-8.3 | E8.3 `retornoTrasLoginConservaFiltros` | volver a la vista destino con query intacta | [L] | bidireccional filtros⇄query IDA-Y-VUELTA idempotente (`/logs?level=ERROR&text=timeout` idéntica tras ida y vuelta); destino por defecto `/`→`/logs` | `hooks/useLogFilters.test.ts` (parte navegada → pareja MANUAL E8.1/E8.5) |
| QP-8.4 | E8.4 `loginConSesionRedirige` | `/login` con sesión válida redirige | [MANUAL-Q23] | con `/me` 200 → NO se muestra el formulario (redirect a `/logs`); con token y `/me` 401 → token descartado + formulario normal | hueco (hook); mitad REST ABSORBIDA por QP-8.7/QP-8.15 ([S] E8-auth: `/me` con/sin token) |
| QP-8.5 | E8.5 `rutaDesconocida404Pantalla` | `*` → 404 del panel (o redirect a login sin sesión) | [MANUAL-Q23] | nunca vacío ni redirect silencioso con sesión; sin sesión → `/login` con destino | hueco (misma familia QP-8.1); pareja QP-8.6 |
| QP-8.6 | E8.6 `spaFallbackYAssetsAbsolutos` [S] | SPA fallback + assets absolutos en servido | [S] | `curl /` y `/logs/123` → 200 `text/html` (nunca 404 de servidor); HTML con assets ABSOLUTOS (`src="/assets/`/`href="/assets/`); `curl -sI /logs/assets/*` jamás ocurre (§2.B-9) | `qa_c8_smoke.sh`: case `E8-servido` |
| QP-8.7 | E8.7 `loginFelizOrdenLiteral` [S] | login 200 orden literal `{token, username, expires_at}` | [S] | 200; orden de claves literal por posición en el JSON crudo; `expires_at` ISO-8601 con `Z` (§2.B-3) | `qa_c8_smoke.sh`: case `E8-auth` (persistencia D4 en storage → pareja MANUAL QP-8.13) |
| QP-8.8 | E8.8 `loginCredencialesInvalidasInline` | 401 login → inline, sin cerrar vista | [MANUAL-Q23] | mensaje literal exacto bajo el formulario; SIN cerrar vista, SIN revelar campo, SIN redirect (única excepción al interceptor) | MAY smoke: 401 `invalid_credentials` REST en case `E8-auth`; visual → demo C9 |
| QP-8.9 | E8.9 `dobleSubmitBloqueado` | botón "Enviando…" deshabilitado, 1 solo POST | [MANUAL-Q23] | UN solo `POST /auth/login` por submit (anti-doble) | hueco: «Vitest mock fetch contador» del spec no materializable (estado de componente); demo C9 |
| QP-8.10 | E8.10 `restauroSesionAntesDeRenderizar` | F5 → `/me` ANTES de render; storage bloqueado → memoria | [MANUAL-Q23] | sin flash de login; username en la barra post-restauro; con storage bloqueado todas las llamadas Bearer | hueco (hook); mitad REST del restauro ABSORBIDA por QP-8.7 ([S]) |
| QP-8.11 | E8.11 `caducidadCalienteInterceptor` | 401 → interceptor: descarta token, cierra WS, redirect, banner | [L]+[MANUAL-Q23] | camino 401 con SOLO `response.status` (pin de QP-8.15); callback descarta token + redirect a `/login` con destino; banner literal "Tu sesión ha expirado (8 h)…" | [L] `api/client.test.ts` (ABSORCIÓN QP-8.15); banner/re-logueo con filtros → demo C9 |
| QP-8.12 | E8.12 `logoutLocalSinRed` | Cerrar sesión = limpiar storage + cerrar WS + `/login`, cero red | [MANUAL-Q23] | NINGUNA llamada de red de logout (no existe endpoint); token fuera del storage tras logout | hueco (hook); demo C9 |
| QP-8.13 | E8.13 `jwtSoloSessionStorage` | token+`expires_at` SOLO sessionStorage (D4) | [MANUAL-Q23] | `localStorage` y cookies NUNCA contienen JWT; claves `projectlog.panel.token`/`.expires_at`; higiene S16 de la SPA (R8.12) | demo C9 (devtools) + grep de higiene en apply/verify (§5) |
| QP-8.14 | E8.14 `bearerEnTodoLoginExceptuado` [S] | Bearer en todo, login excluido y fuera del interceptor | [L]+[S] | todo request de panel lleva `Authorization: Bearer <jwt>`; login (`bearer:false`) NO lo lleva y su 401 cae inline | [L] `api/client.test.ts` (header inyectado); [S] case `E8-auth` (todas las llamadas de pila 200 con token — el header del NAVIGADOR no es observable por curl: nota) |
| QP-8.15 | E8.15 `cuarentaYUnoNuncaParseaBody` | 401: decisión SOLO por status, jamás leer body | [L]+[S] | mock fetch que CRASHEA si `json()`/`text()` en el camino 401 (§2.A-6); interceptor invocado; ninguna `U+FFFD` por el camino 401 | [L] `api/client.test.ts`; [S] case `E8-auth` (401 status) |
| QP-8.16 | E8.16 `parseoToleranteNoDoscientos` | no-200 no-JSON → fallback `"HTTP <status>"` | [L] | 405 HTML, 502 texto plano, cuerpo vacío → `text()`+`JSON.parse` try/catch → fallback (nunca throw UI); 404 JSON `{"code":"not_found",…}` parseado (code/message/field) | `api/client.test.ts` (mocks de Response) |
| QP-8.17 | E8.17 `vocabularioErroresALaUI` | vocabulario §3.3/§3.4 sin reescritura | [L]+[MANUAL-Q23] | mapeo literal de codes/families (400 filtros, 404, 409, 403 genérico, 500/502, red status 0 — R8.17); jamás `project_archived` llega al panel | [L] `api/client.test.ts` (mapeo); banners visuales → demo C9 |
| QP-8.18 | E8.18 `reintentarTras500` | banner 500/502 + Reintentar re-emite la MISMA llamada | [S] (MAY) | re-emisión idéntica; 502→200 recupera estado sin recarga | MAY en case `E8-API`/`E8-WS` (502 con backend parado — nota de orden: restaurar backend); patrón tolerancia R-7.k/Q23: si no se materializa el 502 en guion, PASS-CON-NOTA; visual del banner → demo C9 |
| QP-8.19 | E8.19 `sinCorsRutasRelativas` [S] | rutas relativas mismo origen, CERO CORS | [S] | todo el tráfico del panel por `127.0.0.1:8080` a `/api/v1/**` relativas; sin preflight OPTIONS en el tráfico replicado | `qa_c8_smoke.sh`: transversal de case `E8-auth`/`E8-API` + review del client (rutas relativas) |
| QP-8.20 | E8.20 `primeraCarga50ReceivedDesc` [S] | primera carga sin filtros + selector de proyectos | [S]+[MANUAL-Q23] | `GET /panel/logs` sin parámetros (defecto 50 — S8-7) shape `{items, next_cursor}`; `GET /panel/projects` SIN `include=summary` (S8-6); orden `received_at DESC` (psql); delta = siembra del smoke | [S] case `E8-API`; tabla 8 columnas sin exception/metadata → demo C9 |
| QP-8.21 | E8.21 `filtrosEnQueryStringPersisten` | estado de filtros EN la query string | [L]+[MANUAL-Q23] | ida↔vuelta idempotente (`filtrosDesdeSearch∘searchDesdeFiltros`); solo claves con valor, orden estable; desde/hasta offset local (§2.A) | [L] `useLogFilters.test.ts`; F5/pestaña compartida → demo C9 |
| QP-8.22 | E8.22 `cambioFiltroReiniciaPaginaYsetFilters` | cambio de filtro: cursor a 1ª página + refetch + set_filters | [MANUAL-Q23] | orquestación de página (esqueleto + sustitución + reenvío) en un solo gesto; Reiniciar ⇒ query vacía y `GET /logs` sin parámetros | hueco (orquestación `Logs.tsx`); pines ABSORBIDOS: shape set_filters (QP-8.27 [L]), cursor (QP-8.24); gesto completo → demo C9 |
| QP-8.23 | E8.23 `rangoDesdeHastaOffset` | `datetime-local` → ISO-8601 CON offset local; from==to legal | [L]+[S] | `datetimeLocalAIsoConOffset` viaja `2026-10-26T10:30:00+02:00` (offset INYECTADO, TZ fija del test); `from==to` legal; `from>to` → 400 `filter_from_to_inverted` SIN field | [L] `useLogFilters.test.ts`; [S] case `E8-API` (400 code/field del literal) |
| QP-8.24 | E8.24 `keysetCargarMasFinDeResultados` [S] | keyset: acumulación + fin de resultados | [L]+[S] | `cursor=<next_cursor>` pedida y ACUMULADA sin recargar (sin duplicados); `next_cursor: null` ⇒ fin ("fin de resultados"); vacío legítimo `{items:[],next_cursor:null}` ≠ error; delta psql = filas pedidas | [S] case `E8-API` (keyset con psql); [L] `usePagination.test.ts` |
| QP-8.25 | E8.25 `keysetEstableAnteLlegadasEnVivo` [S] | llegadas en vivo no invalidan el keyset | [L]+[S] | fila nueva insertada al comienzo por dedup; cursor ya obtenido NO invalidado; "Cargar más" posterior no duplica ni salta | [L] `ws/dedup.test.ts`; [S] case `E8-WS` (secuencia ingesta→frame→Cargar más con psql) |
| QP-8.26 | E8.26 `banner400CampoCulpable` | banner rojo con code/message; field marca culpable; lista conservada | [MANUAL-Q23] | `filter_invalid_value` con `field: "level"` → marca del campo; `filter_from_to_inverted` SIN marca (no field); lista previa pintada | ABSORCIÓN del 400 code/field en QP-8.23 ([S]); banner visual → demo C9 |
| QP-8.27 | E8.27 `bandaEnVivoGrupoCoherente` [S] | frame batch byte-exacto + grupo al comienzo | [L]+[S] | UN frame tras ingest 202 `{"accepted":3}`; 5 claves top-level EN ORDEN `{type, batch_id, project_id, received_at, logs}`; `batch_id` regex `b_`+UUIDv4 (§2.B-5) y ÚNICO en el guion; LogDto 11 por elemento; set_filters 6 claves aceptado (sin respuesta del server); grupo en orden del lote sin refresco | [S] case `E8-WS` (byte-exacto); [L] `ws/client.test.ts` (shape set_filters + parser); banda visual → demo C9 |
| QP-8.28 | E8.28 `dedupPorIdOrdenGlobal` | dedup por id + orden global `received_at DESC, id DESC` con micros | [L] | id repetido en acumulado → IGNORADO; lote desordenado → sort global; empate de micros resuelto por `id` DESC; entradas NO mutadas | `ws/dedup.test.ts` (unit puro — D-C8-4) |
| QP-8.29 | E8.29 `indicadorSoloEnLogsEstados` | indicador SOLO en `/logs` con estados ●/○/Reconectando | [MANUAL-Q23] | "● Conectado" tras 101+set_filters; "○ Desconectado" + banner "Streaming inactivo…" con lista por REST; "Reconectando (intento N)…" creciente; fuera de `/logs` NADA (S8-4/decisión C) | hueco (estados del hook — no fichero Vitest frontera); demo C9 |
| QP-8.30 | E8.30 `backoffYReconexionRefresco` [S] | backoff 1 s→30 s con jitter + refresco | [L]+[S] | secuencia de retrasos con `aleatorio` inyectado 1 s→2 s→4 s…cap 30_000, SIEMPRE en `[800…30_000]` (§2.A-4); tras reconectar: set_filters REENVIADO + refetch 1ª página (lo perdido aparece por REST — sin replay) | [L] `ws/client.test.ts`; [S] case `E8-WS` (reconexión limpia — pareja E7.11 de qa_c7: CITADA, no re-probada) |
| QP-8.31 | E8.31 `ws401PreUpgradeRedirige` [S] | 401/403 pre-upgrade del handshake | [S]+[L] | handshake sin token válido → **401 pre-upgrade con cuerpo VACÍO**; Origin ajena a `FRONTEND_ORIGIN` → **403 pre-upgrade con cuerpo vacío** (R-1, nota de despliegue — jamás mitigado); el hook distingue vía `alCierreInaugural`→`me()`: 401 → interceptor/`/login`; 200 → backoff normal (D-C8-5.6) | [S] case `E8-WS` (401/403 con cuerpo vacío); [L] `ws/client.test.ts` (rama inaugural — si el módulo la extrae pura); banner/redirect → demo C9 |
| QP-8.32 | E8.32 `estadosDiferenciadosNoVaciosPorError` | estados §4 de `/logs` diferenciados | [MANUAL-Q23] | cargando (esqueleto) ≠ vacío por filtro (con la sugerencia literal) ≠ vacío legítimo ≠ error (banner con lista conservada); NUNCA 404/error mostrado como vacío | hueco; demo C9 |
| QP-8.33 | E8.33 `frameNoBatchIgnorado` | frame no-batch ignorado defensivamente | [L] | no-JSON, JSON ajeno y shape roto (≠5 claves) → IGNORADOS sin throw ni cierre; lista intacta | `ws/client.test.ts` (parser — defensiva R8.27) |
| QP-8.34 | E8.34 `fichaDetalle11CamposBloquesCondicional` [S] | detalle LogDto 11 campos + bloques condicionales | [S]+[MANUAL-Q23] | `GET /panel/logs/{id}` → 11 campos EN ORDEN del DTO literal (los 9 + `exception` + `metadata`); con sembrado con exception/metadata y otro nulos | [S] case `E8-API` (11 campos + 404 n.a.); bloques `<pre>` mono + Copiar, zonas ocultas si null → demo C9 |
| QP-8.35 | E8.35 `volverConservaFiltrosYPagina` | Volver del detalle conserva query y acumulado | [MANUAL-Q23] | misma query string + mismas filas acumuladas al volver (ruta anidada/Outlet — D-C8-7); sin refetch desde el principio | hueco (composición del router — no materializable en los 6 ficheros); demo C9 |
| QP-8.36 | E8.36 `cuarentaYcuatroDetalleNoVacío` [S] | 404 del detalle → mensaje + Volver, jamás vacío | [S]+[MANUAL-Q23] | `GET /panel/logs/99999999` → 404 `{"code":"not_found"}` literal | [S] case `E8-API` (404 literal); mensaje "Este registro no existe (o fue eliminado)" + Volver → demo C9 |
| QP-8.37 | E8.37 `modal8bSentenciaLiteralTrimBotonRojo` | modal 8b V11: sentencia literal + trim + botón rojo | [L]+[MANUAL-Q23] | `coincideLiteralConTrim("DELETE /logs/<id> · header X-Confirm-Log-Id: <id>", "  …  ")` TRUE (trim inicio/final); texto incompleto/distinto → false; habilitación SOLO con coincidencia | [L] `components/Modal.test.ts` (D-C8-9); bg-red-300→bg-red-700 y sentencia con el id real → demo C9 |
| QP-8.38 | E8.38 `deleteHoy405404SinFingirExito` [S] | `DELETE /panel/logs/{id}` con `X-Confirm-Log-Id` → 405/404 | [S] | cabecera YA FORMADA enviada (S8-2); respuesta **405 ó 404** TAL CUAL (§2.B-6) — jamás 200/202; parseo tolerante aplicado (el 405 puede venir HTML) | `qa_c8_smoke.sh`: case `E8-C10-negativo`; camino de error del modal sin fingir éxito → demo C9 |
| QP-8.39 | E8.39 `listaProyectosMaqueta04` [S] | listado con STATUS/prefix/has_key, SIN resumen P1 | [S]+[MANUAL-Q23] | `ProjectDto` SIN `summary` (8 claves — §2.A-1; `?include=summary` jamás invocado en C8 — R8.18); archivados visibles | [S] case `E8-API` + case `E8-C10-negativo` (include); maqueta 04 (tarjetas, orden, toggle) → demo C9 |
| QP-8.40 | E8.40 `estadoVacioLegitimoConCTA` | "No hay proyectos todavía" + CTA Nuevo proyecto | [MANUAL-Q23] | estado vacío legítimo ≠ error; CTA literal | hueco; demo C9 |
| QP-8.41 | E8.41 `archivarDesarchivarConfirmación` [S] | PATCH status ARCHIVED/ACTIVE (flujo 7) | [S] | `PATCH {"status":"ARCHIVED"}` → 200, etiqueta material (label en DTO) y desarchivar → 200 ACTIVE; ingesta sobre archivado → **403 `{"code":"project_archived"}`** (efecto derivado, SIN frame — absorción del flujo 7 paso C7); jamás DELETE para archivar | `qa_c8_smoke.sh`: case `E8-API` (nota de ORDEN: desarchivar ANTES de los demás casos que usan la key del proyecto) |
| QP-8.42 | E8.42 `fichaMaqueta05ConBloqueKey` [S] | ficha con bloque "API key de ingesta" | [S]+[MANUAL-Q23] | `GET /panel/projects/{id}` → prefix (`plk_…`), `has_key`, estado; 404 proyecto → `{"code":"not_found"}` | [S] case `E8-API`; maqueta 05 visual → demo C9 |
| QP-8.43 | E8.43 `regenerate201RevelacionUnica` [S] | regenerate 201 orden literal + revelación única V8 | [S] | **201 {key, prefix, created_at}** orden literal por posición (§2.B-3); key matchea `plk_<43 chars Base64url>`; tras el modal SOLO queda `prefix` + `has_key: true` (ficha refrescada); la key COMPLETA jamás en otra respuesta posterior | `qa_c8_smoke.sh`: case `E8-API`; higiene: key solo en tmpdir (§6); «la key no se persiste fuera del modal» (Vitest intención) → hueco declarado + review de código; visual del modal V8 → demo C9 |
| QP-8.44 | E8.44 `revocar204Has_keyFalse` [S] | revoke → 204, `has_key:false`, sin prefix | [S] | `DELETE .../api-key` → **204**; psql después: `has_key=false` AND `api_key_prefix IS NULL` (§2.B-4); ingesta con la key revocada → **401** | `qa_c8_smoke.sh`: case `E8-API` (+ E8-C10-negativo si separado) |
| QP-8.45 | E8.45 `nuevoProyecto201Y409` [S] | crear 201 / 409 / 400 por campo | [S]+[MANUAL-Q23] | 201 ProjectDto con `api_key_prefix` vacío y `has_key:false`; 409 `name_duplicate`; 400 con errores POR CAMPO (name vacío/>200); needle `smoke-c8-<TS>-e45` | [S] case `E8-API`; formulario flujo 2 (sin maqueta) → demo C9 |
| QP-8.46 | E8.46 `editarProyectoGuardadoY404` [S] | PATCH solo-modificados 200 / 409-400 / 404 | [S] | `PATCH {"description":…}` (SOLO modificados — el body jamás lleva `status` ni `name` si no cambian) → 200 con `updated_at` refrescado; 404 id inexistente (caso simulado — no hay DELETE real hoy) | `qa_c8_smoke.sh`: case `E8-API` |
| QP-8.47 | E8.47 `modales8a8cConstruidosNoOperativos` [S] | 8a/8c exigencia de nombre + DELETE → 405/404 | [L]+[S] | comparador de NOMBRE EXACTO con trim (mismo `coincideLiteralConTrim` — [L]); `DELETE /panel/projects/{id}` y `/panel/projects/{id}/logs` con `X-Confirm-Name: <name>` → **405/404** tal cual; `?include=summary` aceptado SIN summary | [S] case `E8-C10-negativo`; [L] `components/Modal.test.ts`; zona destructiva visual → demo C9 |
| QP-8.48 | E8.48 `cuarentaYUnoPanelUtf8` [S] | 401 panel UTF-8 byte-exacto (D1) | [S]+[L] | `Content-Type: application/json;charset=UTF-8`; bytes `0xC3 0xB3` PRESENTES y `0xF3` aislado AUSENTE en el cuerpo (§2.B-7); `Content-Type` de `getContentType()` EXACTO; sin `U+FFFD` tras relectura UTF-8 | [S] case `E8-charset` (xxd/od); [L] `EntryPointCharsetTest` (@Nested `Panel401` — C39) |
| QP-8.49 | E8.49 `simetriaIngesta401Utf8` [S] | 401 ingesta UTF-8 (simetría D1) | [S]+[L] | `POST /api/v1/ingest` sin `X-API-Key` → 401 con `charset=UTF-8` + bytes UTF-8 correctos (misma rúbrica §2.B-7) | [S] case `E8-charset`; [L] `EntryPointCharsetTest` (@Nested `Ingesta401` — C39) |
| QP-8.50 | E8.50 `suiteBackendIntactaMasD1` [L] | `mvn test` completo: 174 heredados + tests D1 | [L]+[REVIEW] | **177/177** (R8.59/R8.48); NOTA matizada del spec (desviación ratificada orquestador 2026-10-27): 3 aserciones `contentType(MediaType.APPLICATION_JSON)` de `PanelAuthMvcTest` pasan a `contentTypeCompatibleWith(...)` (intención conservada) — única edición de aserciones previa, RASTREADA; diff del backend = SOLO 2 entry points (+1 import c/u) + C39 | `mvn -f backend/pom.xml test` (gate pre-smoke MANDATORIO) + review del diff (verify) |
| QP-8.51 | E8.51 `baseAbsolutaAssets` | base `/` + assets absolutos + Tailwind compilado | [S] | assets `/assets/…` en `dist/index.html`; ninguna petición `/logs/assets/*` en servido (complemento de QP-8.6); CSS de producción compilado (sin CDN de Tailwind) | `qa_c8_smoke.sh`: case `E8-servido` (el HTML servido ya es el `dist/` del stage 2) + grep del build |
| QP-8.52 | E8.52 `retiroMockupsYConsola` [S] | ws-console.html y mockups retirados (D2) | [S]+[REVIEW] | `curl -sI /ws-console.html` y `/mockups/04-proyectos.html` → caen al SPA fallback (`text/html`, no la página con su JS); review: `docs/mockups/` INTOCABLE (6 maquetas sin cambios) y sin strings huérfanas de las copias | `qa_c8_smoke.sh`: case `E8-servido`; git status (verify) |
| QP-8.53 | E8.53 `nginxIntocableProxyVerde` [S] | nginx C1 sin cambio: ws_echo + qa_c3 verdes | [S] | `scripts/ws_echo_test.py --solo-handshake --token <JWT>` SIN edición → 101 + Accept + timeouts 3600 s; `qa_c3` exit 0 (proxy REST intacto); `qa_c7` exit 0 (handshake WS intacto — superficie D1 no tocada: 401/403 pre-upgrade cuerpo vacío) | orden formal §1 (comandos 3-4) + `ws_echo_test.py` (artefacto C1 sin edición) |
| QP-8.54 | E8.54 `depsPinnadasLockfile` | 4 deps pinnadas SIN rango; lockfile solo esas | [REVIEW] | `package.json`: EXACTAMENTE 4 líneas (`7.18.4`, `4.3.3`, `4.3.3`, vitest pin), sin `^`/`~`; diff del lockfile = esas 4 + transitivas (D-C8-12/D-C8-11: vitest 5.0.5, fallback documentado 4.1.12); NINGUNA otra dep | review del diff en apply/verify (no automatizable sin leer diff — §5) |
| QP-8.55 | E8.55 `buildVerde` [S] | `tsc --noEmit` + `vite build` verdes | [S] | pre-fase del smoke: si TS no compila, `up -d --build` FALLA → exit 2 (guardia de compilación — stage 1 Dockerfile); nunca falso PASS de pila semi-levantada | pre-fase de `qa_c8_smoke.sh` (Q13) [+ `npm run build` local opcional] |
| QP-8.56 | E8.56 `vitestLogicaPuraVerde` [L] | Vitest 55 tests / 6 ficheros verdes | [L] | 4 módulos puros: dedup+orden global; keyset acumulado+fin; comparador 8b con trim; mapeo filtros⇄query ida y vuelta; tolerancias del client; CERO dep de DOM | `npx vitest run` desde `frontend/` (sin config, sin script en package.json — D-C8-11) |
| QP-8.57 | E8.57 `smokeCanonicoAutocontenidoGreen` [S] | meta-caso: la corrida propia del smoke | [S] | recomposición `down -v`+`up -d --build` (§1); los 32 veredictos E8 [S] en PASS; visuales BLOQUEADO-Q23 anotados (no fallan exit); SIN secretos en salida; **exit 0** | `qa_c8_smoke.sh` (la captura RAW del qa-report es SU evidencia) |
| QP-8.58 | E8.58 `sinE2E_navegado` | sin Playwright/Cypress/drivers | [REVIEW] | ninguna dep de E2E en `package.json`/lockfile; los visuales quedan BLOQUEADO+MANUAL (demo C9) — patrón Q23 | review de la política en qa-plan/qa-report + package diff (§5) |
| QP-8.59 | E8.59 `regresionVerdeAlCierre` [S] | re-corridas al cierre + diff lockfile | [S]+[REVIEW] | `qa_c3` exit 0, `qa_c7` exit 0, `mvn` 177/177 (pos-smoke recomendada); qa_c2 NO requerido (decisión §1 con su rationale); diff de lockfile revisado | orden formal §1 (RAW de las 4 corridas) + verify |

Cuenta: **59 casos — 21 con pin [L]** (QP-8.3, 8.11, 8.14, 8.15, 8.16, 8.17,
8.21, 8.23, 8.24, 8.25, 8.27, 8.28, 8.30, 8.31, 8.33, 8.37, 8.47, 8.48,
8.49, 8.50, 8.56 — de los cuales **11 son duales [L]+[S]**) **+ 32 con
veredicto [S]** (21 solo-[S] + los 11 duales) **+ 15 [MANUAL-Q23]** (QP-8.1,
8.2, 8.4, 8.5, 8.8, 8.9, 8.10, 8.12, 8.13, 8.22, 8.26, 8.29, 8.32, 8.35,
8.40) **+ 2 [REVIEW]** (QP-8.54, QP-8.58; las mitades [REVIEW] de
QP-8.50/8.52 se cuentan dentro de sus duales). **Las 6 parejas del spec §3
cubiertas** con las absorciones declaradas en §1.

## 4. Cifras esperadas para qa-run

| Canal | Cifra esperada | Fuente |
|---|---|---|
| **mvn** (`mvn -f backend/pom.xml test`) | **177/177** = 174 heredados de C7 + **3 tests de C39** (`EntryPointCharsetTest` — `@Nested` `Panel401`/`Ingesta401`) | enunciado del corte C8; design D-C8-14 contaba «2 escenarios byte-exacto» (Panel401/Ingesta401) — la reconciliación de la 3ª prueba (tercer método: higiene 0xF3/U+FFFD o simetría adicional) la certifica la cifra RAW del qa-report; si C39 aportase 2 métodos, la cifra esperada pasa a 176 y se DECLARA en el qa-report (discrepancia abierta del plan, §5) |
| **Vitest** (`npx vitest run`) | **55 tests / 6 ficheros** | reparto previsto: `ws/dedup.test.ts` ≈8 · `ws/client.test.ts` ≈10 · `usePagination.test.ts` ≈7 · `useLogFilters.test.ts` ≈12 · `api/client.test.ts` ≈10 · `Modal.test.ts` ≈8 (el método-a-método exacto lo anota el qa-report) |
| **smoke C8** | **32 veredictos `E8.n` [S] esperados en PASS** (lista de §3 col. [S]: E8.6, 7, 14, 15, 18, 19, 20, 23, 24, 25, 27, 30, 31, 34, 36, 38, 39, 41, 42, 43, 44, 45, 46, 47, 48, 49, 51, 52, 53, 55, 57, 59) + 1 pre-fase de build + los **BLOQUEADO-Q23 visuales anotados** (NO cuentan para exit) | spec mapa-qa §6 |
| **exit del smoke** | **0** (falso-PASS jamás: FAIL de un E8.n → 1; guard/pre-fase/secretos → 2) | §6 |
| **qa_c3 / qa_c7** | exit 0 ambos (sin `down -v` — pila heredada del smoke C8) | §1 |
| **qa_c2** | NO requerido (decision §1); recomendable SOLO como diagnóstico opcional | §1 |
| **Higiene** | 0 hits `plk_`/`eyJ` en la salida de TODOS los smokes y mvn | §6 |

## 5. Alcance NOT cubierta por este plan («NO cubierto» — frontera para verify)

Para que `verify` posea la frontera, el QA automatizable de C8 NO cubre:

1. **Render visual/navegado del panel** (Q23 — parejas del spec §3): el
   contrato visual 1:1 R8.52 (paleta, FATAL invertido, banda "● En vivo",
   tabla 8 columnas, indicador SOLO en `/logs`, revelación única, modales
   V10/V11, estados §4) y la navegación real (E8.1/E8.5, guard, retorno del
   detalle — E8.35). Verificación MANUAL del usuario en el **demo C9**
   (E8.58: ningún E2E de navegador — Playwright/Cypress/drivers PROHIBIDOS).
2. **Huecos [L] declarados (degradados a MANUAL-Q23 por frontera)**: la
   intención «Vitest» de los escenarios E8.1 (tabla de rutas), E8.2 (guard),
   E8.4 (login-con-sesión), E8.5, E8.9 (doble-submit), E8.10 (restauro),
   E8.13 (claves de storage), E8.22 (orquestación de página), E8.29
   (estados del hook), E8.32, E8.35 (Outlet), E8.43 (key no persistida
   fuera del modal) NO se materializa en los 6 ficheros Vitest de la
   frontera (jsdom/testing-library PROHIBIDOS por D3/R8.56; E2E PROHIBIDO
   por E8.58). El plan los deja en MANUAL-Q23 con su pareja [S] y lo
   declara como límite consciente de cobertura — NO como deuda implícita.
3. **`E8.54` (lockfile) y `E8.58` (política sin-E2E)**: verificación por
   REVISIÓN de diff/política en apply/verify — sin corrida automatizada.
4. **Higiene de secretos como greps post-ejecución** (§6): la ausencia de
   `plk_`/`eyJ`/`console.log(token)`/`localStorage` en la SPA es live-evidence
   de grep (R8.12), no un escenario con veredicto dedicado.
5. **C10**: los 3 DELETE reales (`X-Confirm-*` efectivos), `include=summary`
   + `GET /projects/{id}/summary` (resumen P1) — hoy el plan solo pina el
   NEGATIVO (405/404 tal cual, S8-2). Sin escenario positivo.
6. **FUTURO**: token WS en primer frame `{"type":"auth",…}` (S8-5 — v1
   contractual = query param). Sin escenario.
7. **Favicon cosmético** (`/favicon.ico` → SPA fallback `text/html`) —
   aceptado por design D-C8-17, sin escenario.
8. **Sin pantallas diseñadas**: `403 project_archived`/`413`/`429` en el
   PANEL (jamás llegan), dark mode (V1), rate limiting, gestión de
   usuarios, pantallas de ingesta (flujo 10) — sin escenario (spec §5).
9. **Rendimiento**: sin benchmark (latencias de UI/backoff más allá del
   rango acotado de E8.30 — «tasa baja» del gate, no se mide).
10. **Migraciones/env nuevas**: NINGUNA (R8.47/R8.53: nginx, Dockerfile,
    compose, `.env*`, tsconfig, docs/** intocables) — se certifica por
    review del diff (checklist §9 del design), no por escenario.

## 6. Transversales de la suite

- **S16 — Higiene de secretos**: JWT y API key SOLO en tmpdir
  (`mktemp -d` + `chmod 700` + `trap EXIT`) — JAMÁS impresos en la salida
  del smoke ni de mvn; grep post-ejecución de TODAS las salidas capturadas:
  **0 hits `plk_`/`eyJ`**. En la SPA: ningún `console.log`/`localStorage`/
  cookie con token (R8.12/R8.13-d5 — grep de apply, §5). La key del
  regenerate vive EXACTAMENTE una vez en tmpdir y la revelación completa
  queda en su ventana (R8.40).
- **Orden de comandos** (RAW capturado en el qa-report, §1): (1) mvn 177
  verde — GATE MANDATORIO pre-smoke; (2) `qa_c8_smoke.sh` (exit 0 con
  BLOQUEADO-Q23 anotados); (3) `qa_c3` (SIN down -v — pila heredada); (4)
  `qa_c7` (SIN down -v); (5) mvn pos-smoke (recomendado — regresión pura
  E8.50/E8.59); (6) frontera (§5). El smoke NUNCA antes del mvn verde.
- **Criterios de BLOQUEADO (exit 2)**: (a) pre-fase falla — `down -v`/`up
  -d --build` (incl. el error de compilación TS: QP-8.55) o 3×healthy no
  alcanzada ⇒ TODOS los E8.n: BLOQUEADO (pila/build) + exit 2, jamás falso
  PASS; (b) falta de credenciales del seed de la pre-fase; (c) higiene
  fallada (`plk_`/`eyJ` en salida) ⇒ BLOQUEADO + exit 2 (S16 duro). **exit
  1** = cualquier FAIL de un E8.n (incluidas las partes [S] de duales).
  **exit 0** = los 32 veredictos [S] PASS con los BLOQUEADO-Q23 visuales
  anotados (y el MAY de QP-8.18 tolerado-con-nota si no materializa el 502).
- **FAIL → loop-back**: ante cualquier FAIL con su pin [L] verde (o
  viceversa), se REPORTA tal cual en el qa-report, se señala el QP-8.x
  AFECTADO y se devuelve al apply por ENCIMA del qa-report (jamás
  auto-arreglo dentro del guion — regla heredada del canon C5/C6/C7). Los
  MAY tolerados nunca son FAIL.
- **Riesgos del plan** (canon §6):
  | Riesgo | Manejo |
  |---|---|
  | La 3ª prueba de C39 vs los «2 escenarios» de D-C8-14 (cifra 177 vs 176) | Cifra anclada a 177 per enunciado; la RAW del qa-report reconcilia y DECLARA la discrepancia si apareciera (§4) |
  | El `down -v` del smoke destruye datos de los cortes previos (Q13) | Los demás smokes usan DELTAS (jamás conteos absolutos — §2.B-2): corren DESPUÉS sobre la siembra del smoke C8 sin verse alterados; el down -v NUNCA sale del smoke |
  | El 405 de Spring puede responder HTML (no JSON) | §2.B-6: el aserto de los DELETE C10-negativos es de STATUS (405 ó 404), no de forma del cuerpo — jamás se pina cuerpo JSON que no existe |
  | El header `Authorization` del NAVIGADOR no es observable por curl | QP-8.14: el pin del Bearer es [L] (mock fetch — único punto de paso D-C8-3); el smoke solo certifica que las llamadas con token responden 200 |
  | Orden del archivo E8-API: archivar deshabilita el key de ingesta | Nota de ORDEN en QP-8.41: el smoke desarchiva ANTES de los casos que usan la key; needles propios por caso evitan dependencia cruzada |
  | Smoke contra pila NO levantada u orientación de `BASE_URL` errónea | Pre-fase con guard: exit 2 con todos E8.n BLOQUEADO (§6-criterios) — nunca falso PASS |
  | Confusión del 401 REST (D1, cuerpo JSON) con el 401 pre-upgrade WS (cuerpo vacío) | Superficies DISTINTAS (R8.47): el charset se pina SOLO en `E8-charset` (QP-8.48/8.49); el cuerpo VACÍO del handshake en `E8-WS` (QP-8.31) — jamás se mezclan asertos |