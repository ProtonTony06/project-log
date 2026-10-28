---
project: Project_Log
change: C8
artifact: verify-report
last_updated: 2026-10-28
verdict: PASS-with-notes
status: pass-with-notes
---

# Verify Report — C8 · Frontend del panel según maquetas (React + TS SPA + fix D1)

Verificación INDEPENDIENTE del corte C8 sobre el árbol real (HEAD `dd26fa9`;
diff `5df6409..HEAD` = 41 A + 3 D + 8 M con todos los commits del corte:
`1987754` fix/config → `ea62874` feat/frontend → `d88d715` test/smoke →
`cbffdd1` + `dd26fa9` hallazgos ronda 1/2, SOLO `scripts/qa_c8_smoke.sh`;
working tree limpio salvo `.atl/sdd/C8/tasks.md` — flips de checkboxes + cifras
inline, bookkeeping legítimo). La evidencia formal de test-run (qa-log.txt RAW:
corrida 1 13 FAIL/10 PASS/1 BLOQUEADO, mvn 177/177 exit 0, re-corrida smoke
23 PASS/0 FAIL/1 BLOQUEADO exit 0, qa_c3 23/24 con E3.22 AJENO, qa_c7 9/9 casos
PASS exit 2 pre-fase AJENA, ws_echo EXIT=0; más Vitest 55/55 y build VERDE
certificados por tests-written/apply-progress) NO se re-ejecuta — se consume
como dato de `qa-log.txt` (6 secciones verbatim, cifras re-chequeadas por
grep/lectura) — pero TODO el contenido contractual se re-lee del código real
contra `spec.md` (R8.1–R8.59, E8.1–E8.59, S8-1…S8-10, D1–D4), `design.md`
(D-C8-1…D-C8-17, tabla §4) y `tasks.md` (T8.1–T8.69, frontera).

**Veredicto: PASS-with-notes** — 8/8 checks A–H OK, **0 CRITICAL**, 1
matización RATIFICADA de literales del espec (DEV-C8-4: los DELETE de C10 hoy
responden **401 real** — error dispatch `/error` reentra en la cadena 3
`denyAll` de C2-S4 — y no el 405/404 predicho; el contrato del CLIENTE §5/R8.15
queda ÍNTEGRO), 2 huecos declarados RATIFICADOS (QP-8.31-rama [L],
QP-8.46-case), 1 errata de conteo documental en la prosa de la cuenta del
qa-report (la tabla fila-a-fila es la ancla correcta), 1 deuda de captura RAW
(Vitest/claim de build sin sección en qa-log) y los matices B-A1…B-A4 de
auditoría retenidos. No hay arreglos pendientes en el código; T8.69 (commits
docs(SDD) + ficha del 07) queda pautado como patrón de cortes previos.

## Check A — Frontera dif-0 (tabla §4 del design): **OK** (CRITICAL — cumplido)

- `git diff --name-status 5df6409..HEAD` (corte completo, 5 commits): EXACTAMENTE
  el casar de la tabla §4 del design — **8 M**: M1 `main.tsx`, M2 `App.tsx`,
  M3 `vite.config.ts`, M4 `package.json`, M5 `package-lock.json`, M6/M7
  (Rest/IngestAuthenticationEntryPoint) + `PanelAuthMvcTest` (desviación
  DEV-C8-1 RATIFICADA orquestador 2026-10-27: 3 aserciones a
  `contentTypeCompatibleWith` — anclada en design D-C8-14 y nota
  `.atl/notes/panelmvauthtest-content-type-charset-desviacion-r845.md`,
  commiteada dentro del COMMIT 1); **3 D** (D2: mockups 04/05 +
  ws-console.html); **41 A** (C1–C39: 32 src frontend + 6 `*.test.ts` Vitest +
  `EntryPointCharsetTest` C39, C38 `qa_c8_smoke.sh`) + el A documental de la
  nota DEV-C8-1 y `.atl/sdd/C8/tasks.md`.
- **Los 2 commits de hallazgos (`cbffdd1`, `dd26fa9`) tocan EXACTAMENTE 1
  fichero: `scripts/qa_c8_smoke.sh` (C38 de la tabla) — verificado con
  `git show --name-status` en ambos.** El bucle T8.64 no sacó NADA de la
  frontera.
- INTOCABLES con diff NULO confirmado por `git diff 5df6409..HEAD --stat` (vacío
  sobre cada uno): `frontend/nginx.conf`, `frontend/Dockerfile`, `compose.yaml`,
  `.env.example`, `frontend/tsconfig.json`, `frontend/index.html`,
  `frontend/src/vite-env.d.ts`, `docs/**` (incl. `docs/mockups/` — 6 maquetas,
  E8.52), smokes `qa_c2`–`qa_c7` + `ws_echo_test.py`, `backend/pom.xml` (sin
  Testcontainers, sin deps nuevas), tests Java C1–C7 y TODO backend salvo
  M6/M7 + PanelAuthMvcTest/C39.
- Diffs mínimos verificados byte-a-byte: M6/M7 = EXACTAMENTE
  `+import java.nio.charset.StandardCharsets;` + `setCharacterEncoding(
  StandardCharsets.UTF_8.name())` antes de `getWriter()` (comentario trazable
  D1) y NADA más; M4 package.json = EXACTAMENTE las 4 deps Pinnadas sin
  `^`/`~` (`react-router-dom 7.18.4` dependencies; devDeps
  `@tailwindcss/vite 4.3.3`, `tailwindcss 4.3.3`, `vitest 5.0.5` — S8-10/E8.54)
  + nada más (los scripts/campos sin touch); lockfile contiene las 4 con esas
  versiones y CERO jsdom/@testing-library/playwright/cypress (E8.54/E8.58).
- `spec.md`/`design.md`/`qa-plan.md`/`qa-report.md` SIN edición: spec/design
  intocados (untracked, mtime 2026-10-27; la desviación DEV-C8-1 viva como NOTA
  en D-C8-14, no como parche del contenido); tasks.md working-tree diff = solo
  flips [ ]→[x] + cifras inline — NINGUNA divergencia "arreglada" tocando docs
  (los literales del espec con la matización DEV-C8-4 se conservan SIN EDITAR,
  anclados en notes/qa-report/apply-progress/verify).

## Check B — Trazabilidad R8.x ↔ E8.x ↔ QP-8.x y 59/59: **OK**

- **59/59 filas QP-8.1…QP-8.59 presente en la tabla del qa-report**, 1:1 con
  E8.1–E8.59 y con el mapa §6 del espec (R8.1–R8.59 agrupados en 9 dominios,
  cada uno con su(s) escenario(s) y su canal). Sin QP huérfano ni QP
  duplicado: conteo fila-a-fila 59 = 14 MANUAL-PENDIENTE (8.1, 8.2, 8.4, 8.5,
  8.8, 8.9, 8.10, 8.12, 8.13, 8.22, 8.29, 8.32, 8.35, 8.40 — Q23, demo C9) +
  1 hueco puro (8.46) + 5 PASS-CON-MATIZ (8.18 nota R-7.k; 8.31 matiz H-C8-B5;
  8.38 y 8.47 matiz DEV-C8-4; 8.59 causas D2 clasificadas) + 2 PARCIAL
  absorbidas (8.42, 8.45 — declarados, con su fuente porta citada) + 37 PASS
  planos. Sin hueco silencioso: ningún QP sin test o absorción/pareja/manual
  asignada.
- **Los 5 PASS-CON-MATIZ y los 2 huecos declarados NO silenciados** (Check D
  y Check E los ratifican uno a uno — NINGUNO CRITICAL):
  - QP-8.31-rama [L] (`alCierreInaugural`→`me()` no extraída pura): HUECO
    RATIFICADO como **WARNING** (ver Check E.3) — la condición del plan
    («si el módulo la extrae pura», qa-plan §3) NO se cumplió; los callbacks
    van como no-ops en `ws/client.test.ts` (tests-written H-A5). Causa: límite
    D3/R8.56 (sin DOM/hooks → la rama vive en `hooks/useLogsStream.ts`:61-66 y
    está cubierta [S] por case_e8_31 de pila + REVIEW de código — verify
    CONFIRMA la rama en el árbol: `me()` → 401 interceptor / 200 backoff).
  - QP-8.46-case (`PATCH` solo-modificados + `updated_at` sin case): HUECO
    RATIFICADO como **WARNING** (ver Check E.4) — declarado explícito en
    qa-report, no materializable sin fichero/case fuera de lo sembrado; la
    superficie REST equivalente (PATCH `{status}` de E8.41 + CRUD 200/400/409
    de E8.17) está VERDE en el mismo smoke y el flujo de ficha → demo C9.
- Los mapeos de ID del guion H-A1 (marca `E8.19` = mitad REST de QP-8.2/8.14) y
  H-A2 (marca `E8.17` = CRUD/409/400 de QP-8.45) están DOCUMENTADOS en
  qa-report §Interpretación («sugerencia S») y en tests-written — sin falso
  mapeo en la tabla QP (las filas declaran su absorción real).
- mvn 177/177 COHERENTE con el reconciliado de C39: qa-log §2 = 3 @Test de
  `EntryPointCharsetTest` (2 Panel401 + 1 Ingesta401 — la 3.ª prueba
  `accessDeniedHandlerMismoCaminoMismoCharset` NOMINADA, discrepancia
  176-vs-177 del plan §4 CERRADA sin desviación); Vitest 55/55 concide con el
  conteo del auditor (55 `it`, 6 ficheros; reparto por fichero difiere de la
  estimación del plan — matiz declarado).

## Check C — Contrato implementado (re-lee y pina desde el código real): **OK**

- **Rutas/guard (R8.1–R8.5)**: `App.tsx` reescrito — 9 rutas 1:1 AppFlow §1
  (`/login`, `/` → `<Navigate to="/logs"/>` con index route, `/logs` anidada
  con `logs/:id` en Outlet — D-C8-7a, acumulado intacto al volver E8.35,
  `/proyectos/nuevo` ANTES de `:id`, `/proyectos/:id`,
  `/proyectos/:id/editar`, `*`→NotFound), guard `RequireAuth` con `comprobando`
  sin render protegido + destino path+search (R8.2/E8.2/E8.3).
- **D4/`useAuth` (R8.7–R8.12)**: sessionStorage con try/catch SIEMPRE + fallback
  memoria; claves `projectlog.panel.*`; interceptor 401 → logout local +
  `navigate("/login")` (R8.11/R8.16); `expires_at` INFORMATIVA (R8.9); logout
  sin red (R8.10); cierre WS (R8.16). ÚNICO módulo que toca el storage (grep
  verificado — Check G).
- **Client tolerante (R8.13–R8.18)**: `api/client.ts` fichero ÚNICO con
  `fetch`; camino 401 con `resp.status === 401` y retorno/interceptor SIN leer
  el body (ni `json()` ni `text()` en ese camino — literal del fichero, pineado
  por `resp401Trampa` de client.test.ts, E8.15); resto no-200 `text()` +
  `JSON.parse` try/catch con fallback `"HTTP <status>"` (R8.16 de E8.16: 405
  HTML/502 plano/cuerpo vacío mockeados — el único 405-materializable en pila
  es mock por DEV-C8-4, ver Check D); `status: 0` red caída; `panel.ts` con
  los 3 DELETE como `Promise<never>` y cabeceras `X-Confirm-Name`/
  `X-Confirm-Log-Id` YA FORMADAS (S8-2/R8.18, literal verificado: los comments
  de panel.ts citan exactamente esa consecuencia "hoy 405/404… sin fingir
  éxito"); SIN `?include=summary` invocado por la app (el guion solo lo prueba
  negativamente — R8.39/C10).
- **`/logs` (R8.20–R8.31)**: `ws/dedup.ts` con `comparadorRecibidoDesc`
  (`received_at` DESC con micros string ISO, empate `id` DESC) +
  `fusionarEnVivo` (dedup por `id`, grupo del lote coherente, NUNCA muta —
  D-C8-4); `ws/client.ts` ÚNICO `new WebSocket` con handshake literal
  `ws(s)://host/ws/logs?token=` + `encodeURIComponent` (S8-5),
  `set_filters` 6 claves SIEMPRE + nulls al abrir/cada cambio/cada reconexión
  (S8-8), parser defensivo `parsearFrame` (no-batch ⇒ null sin lanzar —
  E8.33), `retrasoReconexion` backoff jitter [800..30 000] (E8.30),
  `alCierreInaugural` (E8.31); `useLogsStream` SOLO en `pages/Logs.tsx` con
  `cerrar()` en cleanup (S8-4 — y el wiring `createPortal` del indicador hacia
  el slot del TopBar es la DEV-C8-2 declarada, mantiene S8-4/R8.28 sin subir
  estado a App); keyset `usePagination` (`next_cursor: null` ⇒ fin — E8.24);
  filtros EN la query string por `useSearchParamS`⇄funcs puras idempotentes
  (R8.22/E8.21).
- **Detalle + modales 8a/8b/8c (R8.32–R8.36/R8.44)**: LogDto 11 campos +
  bloques condicionales SOLO no-nulos con Copiar (R8.32); 404 literal
  "Este registro no existe (o fue eliminado)" (R8.34); modal 8b con sentencia
  literal `DELETE /logs/${id} · header X-Confirm-Log-Id: ${id}` +
  `coincideLiteralConTrim` (compartido con 8a/8c) y `bg-red-300`→`bg-red-700`
  (R8.35/V11 — pineado [L] por Modal.test.ts 8 casos); 8a/8c construidos NO
  operativos con DELETE + `X-Confirm-Name` (R8.44/S8-3).
- **Proyectos + key (R8.37–R8.43)**: tarjetas con STATUS literal sin resumen
  P1 (R8.37); archivar/desarchivar SIEMPRE PATCH `{status}` NUNCA DELETE
  (R8.39/E8.41); revelación única 201 `{key, prefix, created_at}` con Copiar +
  aviso literal y key JAMÁS persistida fuera del modal (R8.40/V8 —
  `ModalRevelacionKey` descarta al cerrar); revoke 204 → `has_key: false`
  (R8.41); flujo 2 sin maqueta coherente (R8.42/R8.43).
- **D1/charset (R8.45–R8.48)**: la 1 línea EXACTA + import en AMBOS entry
  points antes de `getWriter()` (diff verificado Check A — el comentario
  trazable cita el motivo y convivencia MockMvc); test C39 con header exacto
  `application/json;charset=UTF-8` + bytes `c3b3`/`c3a1` presentes y
  `f3`/latin-1 aislado AUSENTE + relectura sin U+FFFD + la 3.ª prueba del
  camino `handle`/`denyAll` (E8.48/E8.49/E8.50) — mvn 177/177 RAW (Check B).
- **Build/servido (R8.49–R8.55)**: `vite.config.ts` = base absoluta + plugin
  (M3); smoke case_e8_6: `/` y `/logs/123` → index.html `text/html` con assets
  ABSOLUTOS y cero `/logs/assets/` (E8.6/E8.51); retiro D2 por DELETEs en git
  (qa-report E8.52 con evidencia REAL = git + qa_c7 pre-fase que constata la
  ausencia); `npm run build`/stage 1 Dockerfile verdes (E8.55).

## Check D — Matización DEV-C8-4 (405/404 → 401 real): **OK** (WARNING RATIFICADO)

El espec prescribía «hoy 405/404» en 5 lugares (S8-2/R8.36/R8.44/E8.38/E8.47)
y el sistema real responde **401 `{invalid_token}`**: la ruta no mapeada
produce 405 y su error-page dispatch a `/error` reentra por la cadena 3 de
`SecurityConfig` (`anyRequest().denyAll()`, C2-S4, backend intocable) cuyo
accessDeniedHandler es el `RestAuthenticationEntryPoint`. **Veredicto: WARNING
RATIFICADO — no CRITICAL**, porque se cumplen TODAS las condiciones del canon:

1. El backend es INTOCABLE (R8.47) y NINGÚN smoke de C2–C7 asertó jamás un 405
   (grep vacío declarado; las cadenas `denyAll` de C2 son preexistentes) — el
   comportamiento es PREEXistente e íntegro, no una regresión introducida por
   C8.
2. **El CONTRATO DEL CLIENTE §5 (R8.14/R8.15) queda ÍNTEGRO**: la regla 401
   decide SOLO por `response.status` sin parsear el cuerpo (pin [L]
   `resp401Trampa` — un 401 pasa por el MISMO camino que un 405/404/500 por
   status) y la regla resto-de-no-200 con parseo tolerante y fallback
   "HTTP `<status>`" es agnóstica del status; «SIN fingir éxito» se cumple
   (los veredictos del smoke case_e8_38 muestran 401 tal cual + **fichas
   sobreviven**, sin marcar eliminado). El camino de error del modal queda
   pineado [L] por el mock 405-HTML de client.test.ts (E8.16).
3. **La consecuencia está DOCUMENTADA en los 3 anclas**: qa-report §Matizaciones
   (DEV-C8-4: «hasta que C10 implemente los DELETE, confirmar un modal
   8a/8b/8c recibe 401 y el interceptor del client (R8.14) dispara
   logout+redirect a /login en vez del banner §5 dentro del modal»),
   `.atl/notes/c8-smoke-401-denyall-error-dispatch-campojson.md` (what/why/
   where/learned completos) y apply-progress §DEV-C8-4 — C10 lo reemplazará
   por los 404/405 del DELETE real. El destino del redirect y el re-logueo por
   el interceptor (canales R8.14/R8.16/R8.5) están pineados [L]
   (api/client.test.ts, interceptor invocado en cada 401 — idempotencia por
   repetición).
4. Los literales del espec se CONSERVAN SIN EDITAR y la matización queda
   anclada (qa-report + notes + ficha del 07) — patrón DEV-C7 (1013) respetado:
   nada corregido en silencio, nada de backend tocado.
5. Los asertos del guion se RELAJARON con la dirección CORRECTA: case_e8_38
   pinna el OR `(405|404|401)` con el detalle del status capturado en el
   veredicto (`r_log/r_proj/r_purga` impresos) y el comentario H-C8-B2 DENTRO
   del guion — no falso-PASS de status oculto; el 405/404 no es alcanzable vía
   `/api/v1` (dispatch `/error`), declarado en la nota.

## Check E — Matizaciones, huecos y notas del bucle, uno a uno (ningún silencio)

1. **DEV-C8-3/H-C8-B1 (+ B4, B5)**: semillas `{"logs"}` (canon C5/backend
   CAMPO_LOGS — commited `cbffdd1` antes de la corrida), `campo_json_nul` canon
   C3 para nulls explícitos (next_cursor/api_key_prefix), 403 evil canon E7.9
   solo-status (cuerpo "Invalid CORS request" de DefaultCorsProcessor —
   preexistente, el aserto más duro del 1.er guion era un desvío propio, y la
   401 pre-upgrade SÍ BODY 0 re-pinada en la re-corrida). Todos en la nota
   ancla + qa-report + apply-progress. OK — WARNING ratificado (cambian ASERTOS
   DEL GUION, cero código de producción).
2. **5 PASS-CON-MATIZ**: QP-8.18 (502 no materializado — tolerancia R-7.k
   canon C7, la mitad [L] del camino 502→200 sí pinada), QP-8.31 (matiz
   H-C8-B5 — arriba), QP-8.38/8.47 (matiz DEV-C8-4 — Check D), QP-8.59
   (regresiones con premisas D2 invalidadas, causas CLASIFICADAS — no bugs).
   Ninguno silencioso; todos con su causa citada en la tabla QP.
3. **QP-8.31-rama [L] — HUECO WARNADO**: la rama `alCierreInaugural`→`me()`
   no tiene pin Vitest (callbacks no-ops); el plan la dejó condicional y
   tests-written (H-A5) la reporta. RATIFICADO NO-CRITICAL: la causa es el
   límite D3/R8.56 (sin DOM/no-hooks) ya declarado en design (D-C8-5.6/Open Q2
   — «el hook resuelve con `me()`») y se cierra el hueco con la evidencia [S]
   case_e8_31 (401 pre-upgrade BODY 0 → estado ○) + la REVIEW de verify (la
   rama está en el árbol y wired a `me()`); la mitad manual (banner/redirect)
   va a demo C9.
4. **QP-8.46-case — HUECO WARNADO**: `PATCH` solo-modificados + `updated_at`
   refrescado sin case propio en el guion (H-A4/tests-written: «HUECO
   B-media»). RATIFICADO NO-CRITICAL: hueco DECLARADO en qa-report (no
   materializable sin tocar la semántica del case E8.41/E8.17 que ya pasan);
   la superficie PATCH (200 por `status` en E8.41 + PATCH del CRUD en E8.17)
   está verde y el flujo de ficha (precarga → Guardar → "Cambios guardados")
   es visual → demo C9. Anotar en la ficha del 07 como deuda de test-side
   (caso E8.46 [S] sin veredicto propio).
5. **qa_c3/qa_c7 AJENO-CLASIFICADO por D2**: E3.22 exige las copias
   `/mockups/04|05` SERVIDAS (BORRADAS por D2/R8.54 aprobada) y la pre-fase de
   qa_c7 exige `/ws-console.html` publicado (retirado por D2) — premisas
   invalidadas POR DESIGN, no por bug; el resto 23/23 y 9/9 PASS. Los smokes
   previos quedan INTOCABLES (Check A) y la pauta para la ficha del 07 queda
   anclada (Check H).
6. **NOTA de captura**: la re-corrida del smoke (qa-log §3) corrió con los
   parches B2..B5 APLICADOS SIN COMMIT y el commit ronda 2 (`dd26fa9`) es
   POSTERIOR a la corrida (qa-report frontmatter lo declara: «commit acotado
   posterior en la ronda 2») — contenido-equivalencia asumida y declarada
   (solo `qa_c8_smoke.sh` difiere entre `cbffdd1` y `dd26fa9`, y el guion que
   corrió es el parcheado); micro-matiz: la nota ancla dice "26 bytes" para el
   cuerpo del 403 y el log midió `BODY 31` (marco RFC + línea en blanco
   incluida) — sin efecto contractual (solo-status, canon E7.9).

## Check F — Coherencia interna del qa-report (cifras ⇄ §Comandos ⇄ tabla QP): **OK con 1 WARNING documental**

- Re-chequeado contra qa-log.txt (sin leer RAW de más): frontmatter ⇄ §Comandos
  ⇄ qa-log SECTIONS — smoke re-corrida exit 0 / «24 marcas · 23 PASS · 0 FAIL ·
  1 BLOQUEADO» (23 E8.n + Q23 = 24 marcas ✓); mvn «Tests run: 177, Failures: 0,
  Errors: 0, Skipped: 0» exit 0 ✓ (3 de C39 visibles en las filas de surefire,
  con el nombre de display de los @Nested); qa_c3 exit 1 / 23 PASS / 1 FAIL
  E3.22 ✓; qa_c7 exit 2 / 9 casos PASS con doble marca (BLOQUEADO pre-fase +
  PASS por caso) ✓; ws_echo EXIT=0 ✓; corrida 1 exit 1 / 13 FAIL / 10 PASS ✓ —
  **CERO bugs de backend** documentado con la vía de descarte (mvn paralelo +
  sondas REST).
- **WARNING — errata de CONTEO en la prosa de la cuenta del qa-report** (y
  repetida en T8.68): dice «41 PASS (…; de esos, 5 PASS-CON-MATIZ), 2 huecos
  declarados, 15 MANUAL-PENDIENTE, 1 parcial» — la partición correcta conforme
  a SU PROPIA tabla fila-a-fila es **37 PASS planos + 5 PASS-CON-MATIZ +
  2 PARCIAL (8.42, 8.45) + 1 hueco puro (8.46) + 14 MANUAL-PENDIENTE = 59**
  (la prosa suma 59 en aritmética pero cruza las etiquetas: 14 filas
  MANUAL-PENDIENTE reales, no 15; y los PARCIAL son 2, no 1). El patrón es el
  mismo desliz documental de C7 (prosa vs tabla); la tabla QP es la ancla
  correcta y las cifras RAW están bien. Para el archive basta matizar la
  prosa (o dejar constancia de este reporte como ancla). NO afecta a
  veredictos: 59/59 cubiertos, 0 CRITICAL — ni un caso perdido ni doble-contado
  en la tabla.
- SUGGESTION — claims de Vitest 55/55 y `npm run build` VERDE SIN sección RAW en
  qa-log.txt (6 secciones: smoke×2/mvn/qa_c3/qa_c7/ws_echo; el header del
  qa-log dice «CUATRO comandos» — errata de prosa, hay 5 bloques + ws_echo).
  La cifra 55 queda CORROBORADA por el conteo del auditor context-0 (55 `it`,
  6 ficheros) y el build verde por el stage 1 Dockerfile del smoke (con cache
  `RUN npm run build` visible en qa-log §3/§4/§5) — NO CRITICAL (los comandos
  §6/§7 de qa-report son certificación del apply, patrón admitido), pero la
  captura RAW de Vitest/build no existe en qa-log y así queda en la ficha.
- SUGGESTION — tests-written §1/§3 cuenta «22 marcas E8.n» y la re-corrida
  (y la corrida 1) EMITE 23 (le falta `E8.17`, que sí pasa por case_e8_crud);
  errata de conteo del auditor sin efecto en ningún QP (H-A4 ya declara el
  resto de la brecha 32-previstos/23-emitidos con sus absorciones).
- Higiene de secretos: **0 hits `plk_`/`eyJ` CONFIRMADO por grep propio sobre
  qa-log.txt completo** (tmpdirs 0700 impresos por nombre, no valores; el JWT
  del ws_echo va en shell efímero — S16 ✓) — el requisito §6 higiene del plan
  queda acreditado en RAW.

## Check G — Higiene (R8.12/S16 — greps re-conforme verify): **OK**

Re-confirmado sobre `frontend/src` (greps propios): `localStorage` cero
(solo comentario en useAuth.ts que la NOMINA como prohibida — la prohibición
del comentario no es código), `document.cookie` cero, `console.*` cero en
código (un único comentario en ws/client.ts cita "console.log" como
prohibición), `fetch(` SOLO en `api/client.ts` (R8.13 — ni tests ni páginas),
`new WebSocket` SOLO en `ws/client.ts` (R8.27), lectura de `sessionStorage`
SOLO en `hooks/useAuth.ts` (R8.7 — los 3 accesos window.sessionStorage en ese
fichero). Tests Vitest: cero DOM/jsdom/testing-library (los `it` corren en
node); cero `vi.fn` con token/key impreso. La key revelada vive SOLO en
`ModalRevelacionKey` y se descarta al cerrar (R8.40 — apply-progress + review
del fichero).

## Check H — Deudas heredadas, cerradas y del 07-ficha (para el archive): **OK**

- **Deuda CERRADA por C8**: 401 ISO-8859-1 → D1 (panel + simetría ingest + test
  byte-exacto + smoke E8-charset) — la deuda C7 «401 ISO-8859-1 → C8/C9» queda
  cerrada (quedaba solo la superficie pre-upgrade WS, que sí es distinta y no
  pasa por entry points — R8.47, canon).
- **INTACTAS y heredadas**: nginx `client_max_body_size` → C9 (nginx.conf
  dif-0 confirmado); smoke canónico con `docker compose` → C9 (los smokes
  siguen con COMPOSE_BIN autodetect/podman; compose.yaml dif-0); token WS en
  primer frame `{"type":"auth",…}` → FUTURO (S8-5, v1 = query param —
  implementado EXACTO); favicon cosmético → aceptado (D-C8-17).
- **NUEVAS para C9**: (a) MANUAL-Q23 visual/navegación — 14 QPs
  MANUAL-PENDIENTE (contrato visual 1:1 R8.52, guard, modales V10/V11/botón
  rojo, estados §4, indicador/banda) → demo C9 con instrucción anclada en
  qa-log/qa-report (E8.58: cero E2E navegado); (b) captura RAW de Vitest/build
  en la suite formal si el patrón lo vuelve a pedir (Check F).
- **Para C10**: los 3 DELETE operativos + `include=summary`/`GET
  /projects/{id}/summary` (la UI y las cabeceras `X-Confirm-*` YA formadas
  quedan listas — panel.ts `Promise<never>`); MATIZ DEV-C8-4 para la ficha:
  hasta el corte de C10 el confirm de un modal 8a/8b/8c dispara
  interceptor-401 (logout+redirect a `/login`) en vez del banner §5 in-modal —
  comportamiento real documentado que C10 reemplazará.
- **Pautas para la ficha del 07 y cortes futuros**: qa_c3 E3.22 = N/A-post-D2
  (las copias `/mockups/04|05` ya no existen en runtime — decisión del
  orquestador pendiente sobre el caso: actualizar aserto de qa_c3 en un corte
  que lo TOQUE, NUNCA editar el smoke de C3 en frontera); qa_c7 pre-fase =
  N/A-post-D2 idem (`/ws-console.html` retirado — mismo patrón; los 9 casos
  E7.n quedan citables). Regresiones futuras: leer ambas como AJENAS con causa
  D2 hasta decisión.
- **Observación sobre la ficha C7**: el «cuerpo NUNCA escrito / cuerpo vacío»
  que C7 pineó como D5/ Check C es VÁLIDO acotado al 401 de su interceptor
  (`rechazar()` solo-status) y C8 lo RE-PINA en pila (`HS 401 BODY 0`, ambas
  corridas). NO generaliza al 403 de Origin ajena: en la pila real ese 403
  sale de `DefaultCorsProcessor` (via el setAllowedOrigins de la 3.ª edición
  C7) y LLEVA cuerpo «Invalid CORS request» — el canon E7.9 de C7 solo asertó
  status y sigue correcto; matizar la ficha C7 para que su afirmación se lea
  acotada a la 401 (y citar DEV-C8-7/H-C8-B5 como el matiz de la 403).

## Veredicto global

**PASS-with-notes** — **0 CRITICAL / 2 WARNING / 4 SUGGESTION**. Todo el
contenido contractual del corte (SPA de 6 pantallas + flujo 2, guard con
`GET /me` + retorno con filtros, client tolerante R8.14/R8.15, keyset+dedup+
backoff+ciclo-WS S8-4, modales 8a/8b/8c con cabeceras formadas, revelación
única de key, D1 UTF-8 con simetría, base absoluta + retiro D2 + smokes previos
intactos) está implementado, pineado ([L] 21 + [S] 23 marcas + mvn 177/177) y
certificado en RAW; la única matización de literales del espec (DEV-C8-4,
405/404 → 401 real por denyAll en `/error`) está RATIFICADA con el contrato del
cliente §5 íntegro y la consecuencia documentada para C10. Notas para el
archive (ficha del 07): (a) corregir la prosa de la cuenta del qa-report
(37/5/2/1/14, no 41/2/15/1) — la tabla QP es la ancla; (b) los 2 huecos
declarados (QP-8.31-rama, QP-8.46-case) van a la ficha como deudas test-side
ratificadas con sus causas (frontera Vitest D3 / case ausente con la superficie
REST verde); (c) pauta qa_c3-E3.22 y qa_c7-pre-fase = N/A-post-D2; (d) la
afirmación «cuerpo vacío» de la ficha C7 se lee acotada a la 401 (la 403 de
Origin ajena lleva «Invalid CORS request» — DEV-C8-7); (e) las cifras RAW
(Vitest 55/55, build VERDE) no tienen sección en qa-log — corroboradas por el
conteo del auditor y el stage 1 Dockerfile; (f) deudas vivas: nginx
client_max_body_size → C9, smoke docker compose → C9, MANUAL-Q23 → demo C9, los
3 DELETE + include=summary → C10 (con la consecuencia UX del interceptor-401
documentada), token-en-primer-frame → FUTURO; (g) T8.69 (commits docs(SDD) +
ficha del 07) queda pautado — patrón de cortes previos: archive genera los
commits.