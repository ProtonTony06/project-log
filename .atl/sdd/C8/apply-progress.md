---
project: Project_Log
change: C8
artifact: apply-progress
last_updated: 2026-10-27
status: in-progress
---

# Apply progress — C8 (log único e idempotente, se reescribe por lote)

- Entradas leídas antes de escribir: `proposal.md` (approved), `spec.md`,
  `design.md` (17 D-C8 + tabla §4 + §9), `tasks.md` (69 tareas, 11 fases).
- HEAD de partida: `5df6409` (C7). Commits: `1987754` (fix/config) →
  `ea62874` (feat/frontend) → `d88d715` (test/smoke).

## Progreso por fase

### Fase 0 — fix D1 (backend): CERRADA (con desviación anclada ratificada)

**Bloqueo H-C8-B1 → RESUELTO (opción A, ratificada por el orquestador
2026-10-27)**. Resumen: las 3 aserciones exactas heredadas de
`PanelAuthMvcTest` (:88/:98/:128, `content().contentType(APPLICATION_JSON)` —
igualdad ESTRICTA de MediaType, `ContentResultMatchers` :86
`AssertionErrors.assertEquals`) eran excluyentes con el header
`application/json;charset=UTF-8` que `setCharacterEncoding("UTF-8")` produce
en `MockHttpServletResponse`. Verificado empíricamente en DOS variantes de
orden (la del design D-C8-14 `setContentType→setCharacterEncoding` y la
inversa) — mismos 3 fallos: `Tests run: 174, Failures: 3` cada corrida. El
propio aserto (a) de D-C8-14 (header con `charset=UTF-8` en MockMvc) era
mutuamente excluyente con `PanelAuthMvcTest` sobre el mismo mock. Ratificado:
excepción trazable de 3 líneas de aserción (intento conservado: cuerpo JSON,
nunca HTML). Registrado en: nota `DESVIACIÓN RATIFICADA ORQUESTADOR
2026-10-27` en `design.md` bajo D-C8-14; `.atl/notes/
panelmvauthtest-content-type-charset-desviacion-r845.md`; E8.50 matizado.

- [x] T8.1 — M6 `config/RestAuthenticationEntryPoint.java` + M7
  `ingest/IngestAuthenticationEntryPoint.java`: +`import
  java.nio.charset.StandardCharsets;` y
  `response.setCharacterEncoding(StandardCharsets.UTF_8.name());` antes de
  `getWriter()` en `escribir401(...)` (con comentario trazable; `setContentType`
  queda tras el setCharacterEncoding — irrelevante en Tomcat real).
- [x] T8.2 — mvn tras tocadas: primera variante ROJO 174/3 (evidencia del
  bloqueo); tras ratificar opción A: **174/174 Failures 0** (desviación) y
  tras T8.3: **177/177** (ver Comandos y gates).
- [x] T8.3 — C39 `backend/.../config/EntryPointCharsetTest.java` (UN fichero,
  `@Nested` `Panel401` (2 tests: commence + handle/denyAll) + `Ingesta401`
  (1 test)): unit puro sin Spring context, `commence(null, resp, null)` /
  `handle(null, resp, null)` con `MockHttpServletResponse` + ObjectMapper
  real. Asertos: (a) `getContentType() == "application/json;charset=UTF-8"`;
  (b) hex `c3b3` (ó panel) / `c3a1` (á ingesta) presentes y `f3` AUSENTE;
  (c) relectura UTF-8 sin `U+FFFD` con code/message literales. Nota: la `á` de
  ingesta sustituye a la `ó` inexistente en "API key de ingesta ausente o
  inválida" (el espíritu del aserto (b): acento UTF-8 multibyte, no Latin-1).
- [x] T8.4 — GATE mvn: **Tests run: 177, Failures: 0, Errors: 0** (174
  heredados + 3 de C39; el XML de surefire confirma 3 testcases 2×Panel401 +
  1×Ingesta401). tasks.md T8.62 anticipaba "+2 de C39" pero EntryPointCharsetTest
  tiene 3 tests — se registra 177 tal cual (E8.50 re-verificado).
  `git diff` = M6/M7 (+import y +línea c/u) + PanelAuthMvcTest (3 líneas,
  desviación) + C39 nuevo. NINGUNA otra aserción editada.
- [x] T8.5 — COMMIT 1: **`1987754`** `fix(config): 401 del panel en UTF-8 —
  setCharacterEncoding antes de getWriter (+ simetría ingest), test del
  header y del ó (C8)` (incluye la desviación explicada en el cuerpo del
  mensaje; sin push).

### Fase 1 — fundación frontend: CERRADA
- T8.6 (deps D3 — M4 package.json solo 4 líneas nuevas: react-router-dom
  7.18.4; devDeps @tailwindcss/vite 4.3.3, tailwindcss 4.3.3, vitest 5.0.5;
  M5 lockfile regenerado, 34 paquetes, sin jsdom/testing-library — E8.54),
  T8.7 (M3 vite.config tailwindcss()+base "/"), T8.8 (index.css: @import
  tailwindcss + @theme tokens + .lv-*/.env-* literal de maqueta), T8.9–T8.19
  (api/types.ts DTOs literales, api/client.ts tolerante con interceptor
  401-only-status, api/panel.ts función por endpoint con los 3 DELETE
  Promise<never>, ws/dedup.ts comparadorRecibidoDesc+fusionarEnVivo,
  ws/client.ts único new WebSocket con backoff jitter + parser defensivo,
  hooks/useAuth.ts sessionStorage+fallback memoria+interceptor,
  hooks/useLogFilters.ts query string + datetimeLocalAIsoConOffset
  idempotente, hooks/usePagination.ts keyset puro, hooks/useLogsStream.ts
  ciclo de vida S8-4 + alCierreInaugural con me()): TODAS [x]. Gate
  `npx tsc --noEmit` VERDE.

### Fase 3 — componentes compartidos: CERRADA
- T8.20–T8.32 [x]: Banner (4 variantes + Reintentar), Esqueleto
  (tabla+tarjetas), BadgeSeveridad/PildoraEntorno (clases literales lv/env),
  EstadoConexion (3 estados; en TopBar entra por PORTAL al span
  #indicador-conexion — solo se MONTA en /logs, S8-4/R8.28), BandaEnVivo
  (borde discontinuo + batch_id·N·hora), Modal (genérico + export
  coincideLiteralConTrim trim===trim), TopBar (NavLink activos maqueta, slot
  indicador, logout local, anchos 7xl/6xl), TarjetaProyecto (Activo|Archivado
  literal, SIN summary, clic→ficha), FiltrosLogs (grid 1→2→6 maqueta 02,
  texto con debounce 400 ms, from/to sin re-offset), TablaLogs (8 columnas
  literal, sin exception/metadata, borde izq. FATAL/ERROR/WARN, nombre desde
  Map si no id crudo), ModalBorrado8b (sentencia `DELETE /logs/${id} · header
  X-Confirm-Log-Id: ${id}`, red-300→red-700, error §5 en modal),
  ModalRevelacionKey (aviso literal "No se volverá a mostrar; cópiala ahora."
  + Copiar + descarte al cerrar). T8.33: `npx tsc --noEmit` VERDE.
- Nota de wiring: el indicador viaja por createPortal de Logs.tsx al hueco del
  TopBar (decisión propia documentada aquí — sin portal, el estado del WS
  tendría que subir a App y la conexión viviría fuera de /logs, violando
  S8-4/R8.28).

### Fase 4 — páginas + rutas: CERRADA
- T8.34 (main.tsx: import index.css + BrowserRouter, StrictMode intacto),
  T8.35 (App.tsx REESCRITO: placeholder C1 fuera; /AuthProvider + Routes única
  — /login hermana con mayor especificidad que el *; 9 rutas + RequireAuth con
  destino path+search y interceptor 401; /logs RUTA ANIDADA con /logs/:id en
  Outlet — listado no se desmonta E8.35; /proyectos/nuevo antes de /:id),
  T8.36 Login (bearer:false; 401 inline; red → banner + Reintentar; Enviando…
  deshabilitado; con sesión Navigate; banner motivo expirada), T8.37 Logs
  (keyset acumulado + refetch en cambio de URL + Cargar más + fin de
  resultados + estados §4 diferenciados + banner 400 con campo culpable +
  banda en vivo + fusionarEnVivo + refetch página 1 al reconectar fusionado
  + indicador por portal), T8.38 LogDetalle (11 campos, bloques condicionales
  con Copiar, 404 literal R8.34, modal 8b), T8.39 Proyectos (tarjetas, toggle
  archivados, vacío ≠ error, esqueleto, Reintentar), T8.40 ProyectoDetalle
  (ficha maqueta 05, Archivar/Desarchivar SIEMPRE PATCH {status} E8.41,
  generar/regenerar→201→revelación única→refresco E8.43, revocar→204 E8.44,
  modales 8a/8c nombre EXACTO+trim con DELETE+X-Confirm-Name → 405/404 §5
  E8.47, 404 propio), T8.41 ProyectoNuevo (409 inline datos conservados, 400
  por campo, Creando…), T8.42 ProyectoEditar (PATCH solo campos modificados,
  "Cambios guardados" en la ficha vía location.state, 404 → retorno),
  T8.43 NotFound (404 del panel literal). T8.44: `npm run build` VERDE
  (tsc strict + vite build; base absoluta "/"; 68 módulos, JS 328.8 kB).

### Fase 5 — tests Vitest: CERRADA
- T8.45–T8.50 [x]: `ws/dedup.test.ts` (fusionarEnVivo dedup por id +
  comparadorRecibidoDesc received_at DESC/id DESC, sin mutar acumulado, identidad
  con lote vacío), `hooks/usePagination.test.ts` (keyset acumular/paginaInicial/
  esFinDeResultados), `api/client.test.ts` (resp401Trampa: 401 sin leer body,
  intercepción solo con bearer, repetición por llamada, 405 HTML/502 plano/409
  vacío → "HTTP <n>", 404 JSON parseado, bearer:false en login, headers
  X-Confirm-Log-Id, 204 → undefined, red caída status 0), hooks/useLogFilters
  (round-trip idempotente, ISO con offset), ws/client (retrasoReconexion
  jitter 800–30000 cap 30 s, set_filters 6 claves byte-exacto, parsearFrame
  defensiva no lanza, URL ws:// con token encodeURIComponent, close 1000
  idempotente, WsStub + stubGlobal location), components/Modal.test.ts
  (coincideLiteralConTrim 8 casos). T8.51 gate `npx vitest run`: **VERDE —
  55 tests, 6 ficheros, 0 fallos** (node env, globals off, sin DOM).

### Fase 6 — revisión e higiene: CERRADA
- T8.52 higiene §9 VERIFICADA con greps: sin localStorage/cookies (solo
  comentarios), sin console.log(token), `fetch(` solo en api/client.ts,
  `new WebSocket` solo en ws/client.ts, sessionStorage solo en
  hooks/useAuth.ts.
- T8.53 huérfanos: sin referencias vivas a public/mockups (los DELETEs D2
  quitaron public/ entero; solo comentarios que citan las maquetas congeladas
  en docs/) — OK.

### Fase 7 — COMMIT 2: CERRADA
- T8.54 COMMIT 2 **`ea62874`**: 45 ficheros (frontend/src completo + package.json
  + lockfile + vite.config + 3 DELETEs de public/), 5936(+)/959(−). Orden de
  commits verificado: 1987754 → ea62874.

### Fase 8 — smoke qa_c8: CERRADA
- T8.55–T8.58 [x]: `scripts/qa_c8_smoke.sh` (canon qa_c2–qa_c7) — BASE_URL
  127.0.0.1:8080 único puerto; tmpdir 700 + trap EXIT (JWT/key SOLO allí);
  pre-fase AUTOCONTENIDA Q13 `down -v` + `up -d --build` (guardia tsc+vite) +
  smoke-of-the-smoke (GET /me sin token → 401 invalid_token); guard
  FRONTEND_ORIGIN → casos WS BLOQUEADO (patrón R-7.j); cliente WS RFC 6455
  embebido (ws8_client.py stdlib, modos hs/long). Casos: E8.6/E8.51 (SPA
  servida, assets absolutos, fallback /logs/123, sin /logs/assets/),
  E8.7/E8.19 (login orden literal {token,username,expires_at}; /me ADMIN; 401
  invalid_token), E8.14/E8.15 (status íntegro; 405 HTML tolerado), E8.48/E8.49
  (charset=UTF-8 + bytes c3b3/c3a1, cero byte latin-1 aislado), E8.17 (CRUD
  negativos 409 name_duplicate / 400 field_required field=name; detalle 200
  con summary), E8.20/E8.24 (defecto 50; keyset cursor, fin null, páginas
  disyuntas), E8.23/E8.26 (offset −03:00 OK; from>to → filter_from_to_inverted
  SIN field; level inválido → filter_invalid_value CON field=level), E8.39
  (listado sin summary), E8.43/E8.44 (regenerate 201 orden literal; revoke
  204 has_key=false), E8.34/E8.36 (detalle 11 campos; 404 not_found), E8.41
  (PATCH ARCHIVED/ACTIVE + 403 project_archived en ingest), E8.38/E8.47
  (DELETEs panel con X-Confirm-* formadas → 405/404 tal cual, fichas
  sobreviven), E8.31 (WS 401/403 pre-upgrade cuerpo VACÍO), E8.27 (1 frame
  batch byte-exacto §3.5: 5 claves, LogDto 11, b_+uuid único), Q23
  BLOQUEADO → MANUAL demo C9. CEROS secretos impresos.
- T8.59 [x]: `bash -n` VERDE + COMMIT 3 **`d88d715`** `test(smoke):
  scripts/qa_c8_smoke.sh — servido SPA/base absoluta, auth y charset del
  panel, CRUD + keyset + regenerate, negativos C10 (405/404), WS batch
  byte-exacto y 403 Origin (C8)` (además TRACKED por primera vez
  `.atl/sdd/C8/tasks.md` con marcas [x]).

## Comandos y gates

- `mvn test` (builder `docker.io/library/maven:3.9-eclipse-temurin-21`,
  patrón C4/C7 `podman run ... -v /home/acuelop369/.m2:/root/.m2:Z`):
  - variante 1 (orden design): 174 run, 3 Failures (PanelAuthMvcTest ×3).
  - variante 2 (orden inverso): 174 run, 3 Failures (idénticas).
  - tras desviación: **174/174 FAILURES 0**.
  - tras T8.3: **177/177 FAILURES 0** — GATE FASE 0 VERDE.
- `npx tsc --noEmit`: VERDE tras Fase 1, Fase 3 (T8.33) y Fase 4 (T8.44).
- `npm run build` (T8.44): VERDE — `tsc --noEmit` strict + `vite build`
  (base "/", 68 módulos; dist/assets JS 328.8 kB gzip 98.4 kB).
- `npx vitest run` (T8.51): VERDE — 55 tests / 6 ficheros.
- `bash -n scripts/qa_c8_smoke.sh` (T8.59): VERDE.

## Desviaciones / hallazgos

- **DEV-C8-1 (ANCLADA, ratificada orquestador 2026-10-27)** — excepción
  PanelAuthMvcTest :88/:98/:128 → `contentTypeCompatibleWith`; ver Fase 0.
  Commiteada dentro del COMMIT 1 con nota explícita; E8.50 matizado en
  design.md. Diff backend total: 2 clases principal + 3 líneas de test + C39.
- **DEV-C8-2 (propia, documentada)** — indicador de conexión: `createPortal`
  de Logs.tsx hacia el slot del TopBar en vez de levantar el estado del WS a
  App (mantiene S8-4/R8.28: EstadoConexion solo vive bajo /logs). Sin diff-0.

### Fase 9 — QA contra pila viva: CERRADA (2026-10-28)

- **Corrida 1** (T8.60, HEAD d88d715): exit 1 — 13 FAIL / 10 PASS /
  1 BLOQUEADO (Q23). **Cero bugs de backend** (sondas REST en vivo + mvn
  177/177 en paralelo): los 13 FAILs se atribuyeron al guion del smoke o a
  predicciones sin respaldo canon (H-C8-B1..B5, abajo).
- **Commit acotado H-C8-B1** (T8.64, pre-corrida): `cbffdd1` — semillas
  `{"batch"→"logs"}` (canon C5/backend) + guards `|| true`.
- **Parches del bucle** (H-C8-B2..B5, aplicados y commited como ronda 2):
  401-real de los DELETE C10 y POST /me (desviación DEL LITERAL del spec
  falsificado — ver DEV-C8-4 abajo y nota), lookup del detalle por needle +
  correlation verificado, `campo_json_nul` (canon C3) para los null
  explícitos, 403 evil canon E7.9 (solo status), E8.27 al URL_BATCH, E8.17
  SIN summary (canon C3).
- **Re-corrida 2** (T8.60): **exit 0 — 23 PASS / 0 FAIL / 1 BLOQUEADO (Q23)**.
- **T8.61**: qa_c3 23/24 (E3.22 AJENO-CLASIFICADO: exige las copias
  `/mockups/04|05` borradas por D2 aprobada; endpoints UI 23/23) + qa_c7
  9 casos PASS (E7.14 nota R-7.k; pre-fase AJENO-CLASIFICADA: exige
  `/ws-console.html` publicado, retirado por D2) + ws_echo_test.py SIN
  edición → 101 (E8.53).
- **T8.62**: mvn → **177/177** (la cifra "+2" de T8.62 era error de libros:
  EntryPointCharsetTest = 3 @Test — reconciliado por el auditor context-0).
- **T8.63**: Vitest **55/55** (6 ficheros) + `npm run build` VERDE.
- **Evidencia RAW**: `.atl/sdd/C8/qa-log.txt` (6 secciones verbatim);
  veredictos por QP en `qa-report.md` (59/59: 41 PASS, 2 huecos declarados,
  15 MANUAL-Q23 demo C9, 0 CRITICAL), formato canon C7.

### Fase 10 — frontera final: CERRADA (2026-10-28)

- T8.65: diff 5df6409..HEAD = 41 A + 3 D + 8 M — EXACTAMENTE M1–M7 (+
  PanelAuthMvcTest de la desviación DEV-C8-1) + C1–C39 + 3 DELETEs D2;
  intocables SIN diff (nginx.conf, Dockerfile, compose.yaml, .env*,
  tsconfig.json, index.html, vite-env.d.ts, docs/** incl. 6 maquetas,
  smokes qa_c2–qa_c7, ws_echo_test.py, tests C1–C7, backend salvo D1).
- T8.66: package.json = EXACTAMENTE las 4 deps pinnadas; backend = 2 clases
  (+1 import y +1 línea c/u) + C39 + 3 líneas de aserción (desviación).
- T8.67: greps higiene — 0 localStorage/document.cookie/console.log;
  `fetch(` SOLO en api/client.ts; `new WebSocket` SOLO en ws/client.ts;
  sessionStorage SOLO en hooks/useAuth.ts.

## DEV-C8-4 (matización de literales del spec, RATIFICADA por orquestador 2026-10-28)

La predicción «DELETE de C10 hoy → 405/404 TAL CUAL» (S8-2/R8.36/R8.44/
E8.38/E8.47) está FALSIFICADA por el sistema real: el dispatcher de error
de las rutas no mapeadas reentra por la cadena 3 de SecurityConfig
(denyAll, C2-S4, intocable) → **401 `{invalid_token}`** con charset de D1.
El contrato del CLIENTE §5 (status, parseo tolerante, sin fingir éxito)
queda íntegro. Consecuencia UX anclada: hasta C10, confirmar un modal
8a/8b/8c dispara el interceptor 401 (logout+redirect) en vez del banner §5
in-modal — C10 lo reemplazará al implementar los DELETE. Detalle:
`.atl/notes/c8-smoke-401-denyall-error-dispatch-campojson.md`.

## Notas

- `mvn -q test` no imprime resumen en éxito — usar sin `-q` con grep para
  registrar "Tests run".
- Fichero único de progreso — se reescribe completo tras cada lote.
- APPLY DE C8 COMPLETO (T8.1–T8.68); falta T8.69 (commit docs(SDD) del
  cierre + ficha del 07) tras verify/archive.