---
project: Project_Log
change: C8
artifact: tasks
last_updated: 2026-10-27
status: active
---

# Tasks — C8 · Frontend del panel según maquetas (React + TS SPA + fix D1)

> Fase sdd-tasks del corte C8. Fuentes normativas: `spec.md` C8 (R8.1–R8.59,
> E8.1–E8.59, S8-1…S8-10, gate D1–D4 aprobado 2026-10-26) y `design.md` C8
> (D-C8-1…D-C8-17, tabla §4 File Changes COMPLETA y CERRADA, checklist §9).
> El spec y el design NO se editan. Ningún literal de DTO/WS/paleta se
> redefinirá en apply: se CITAN del Schema §3.2/§3.3/§3.5, AppFlow §1/§4/§5,
> UIUX §2/V1–V11 y las maquetas congeladas `docs/mockups/`.

## Frontera (vale para TODAS las tareas — tabla §4 del design, CERRADA)

**Únicamente estas rutas pueden crearse/editarse/borrarse — nada fuera de la
tabla llega a apply:**

- **CREATE (38 src/scripts/tests)**: `frontend/src/` → `index.css`;
  `api/{types,client,panel}.ts`; `ws/{client,dedup}.ts`;
  `hooks/{useAuth,useLogFilters,useLogsStream,usePagination}.ts`;
  `components/{TopBar,EstadoConexion,BandaEnVivo,BadgeSeveridad,PildoraEntorno,
  Banner,Modal,ModalBorrado8b,ModalRevelacionKey,TarjetaProyecto,FiltrosLogs,
  TablaLogs,Esqueleto}.tsx`; `pages/{Login,Logs,LogDetalle,Proyectos,
  ProyectoDetalle,ProyectoNuevo,ProyectoEditar,NotFound}.tsx`; 6 tests Vitest
  (`ws/dedup.test.ts`, `ws/client.test.ts`, `hooks/usePagination.test.ts`,
  `hooks/useLogFilters.test.ts`, `api/client.test.ts`,
  `components/Modal.test.ts`); `scripts/qa_c8_smoke.sh`; y el test backend
  `backend/src/test/java/com/projectlog/config/EntryPointCharsetTest.java`.
- **MODIFY (7)**: `frontend/src/main.tsx` (solo: import index.css +
  BrowserRouter), `frontend/src/App.tsx` (REESCRITO — placeholder C1 fuera),
  `frontend/vite.config.ts` (SOLO `base "./"`→`"/"` + plugin
  `@tailwindcss/vite`), `frontend/package.json` (SOLO 4 deps pinnadas),
  `frontend/package-lock.json` (regenerado por npm install),
  `backend/.../config/RestAuthenticationEntryPoint.java` e
  `backend/.../ingest/IngestAuthenticationEntryPoint.java` (SOLO +1 import
  `StandardCharsets` +1 línea `setCharacterEncoding` c/u).
- **DELETE (3)**: `frontend/public/mockups/04-proyectos.html`,
  `frontend/public/mockups/05-proyecto-detail.html`,
  `frontend/public/ws-console.html` (D2).
- **INTOCABLES**: `frontend/nginx.conf`, `frontend/Dockerfile`,
  `frontend/index.html`, `frontend/tsconfig.json`,
  `frontend/src/vite-env.d.ts`, `compose.yaml`, `.env`, `.env.example`,
  TODO backend salvo las 2 clases de D1 (incl. SecurityConfig/CorsConfig/
  streaming/**/tests C1–C7), `docs/**` INCLUYENDO `docs/mockups/` (6 maquetas,
  contrato visual), smokes `qa_c2`–`qa_c7` + `ws_echo_test.py`.

## Orden de dependencias

**Fase 0** (D1 backend — commit 1 independiente) → **Fase 1** (fundación:
deps D3 → vite.config → tokens → tipos → client/panel → ws) → **Fase 2**
(hooks) → **Fase 3** (componentes) → **Fase 4** (páginas + rutas + gate
build) → **Fase 5** (Vitest) → **Fase 6** (retiro D2) → **Fase 7** (COMMIT 2
feat) → **Fase 8** (smoke script + COMMIT 3) → **Fase 9** (QA contra pila) →
**Fase 10** (frontera final). El fix D1 va PRIMERO y como primer commit: es
autocontenido (mvn verde por separado), el smoke E8-charset lo presupone y
deja el backend congelado para el resto del corte (R8.47).

## Reglas transversales de apply

- Regla anti-contaminación (solo aplica a Fase 0, los únicos edits de código
  existente backend): mvn INMEDIATO tras cada tocada; JAMÁS continuar con rojo.
- En el frontend: `npx tsc --noEmit` barato tras cada fase de módulos; el
  gate completo es `npm run build` (T8.43). Sin fichero de config Vitest y
  sin script `test` en `package.json` (D-C8-11: corrida = `npx vitest run`
  desde `frontend/`, environment node, globals OFF).
- Higiene S16/CD-4 en TODO fichero nuevo: NINGÚN `console.log` con JWT/token
  WS/API key (R8.12); la key completa vive SOLO dentro del modal E8.43.
- Reglas de dependencia de módulos (D-C8-1): `pages/` y `components/` →
  `hooks/` → `api/`+`ws/`; JAMÁS al revés. NINGÚN `fetch(` fuera de
  `api/client.ts` (R8.13), NINGÚN `new WebSocket` fuera de `ws/client.ts`
  (R8.27), NINGUNA lectura de `sessionStorage` fuera de `hooks/useAuth.ts`
  (R8.7).
- Los veredictos del smoke usan IDs `E8.x` (patrón real qa_c2–qa_c7) y el
  bloque visual Q23 BLOQUEADO.

---

## Bloque 1 — Fase 0: fix D1 (backend — ÚNICO alcance: 2 clases + 1 test, E8.48–E8.50)

> **DESVIACIÓN ANCLADA RATIFICADA (orquestador 2026-10-27, opción A)**: el
> header `charset=UTF-8` del fix D1 recomponía el Content-Type de
> `MockHttpServletResponse`, rompiendo las 3 aserciones EXACTAS heredadas de
> `PanelAuthMvcTest` (:88/:98/:128). Ratificado: esas 3 líneas a
> `contentTypeCompatibleWith` (intento conservado: JSON, nunca HTML);
> E8.50 matizado. Detalle: `apply-progress.md` + nota en design D-C8-14 +
> `.atl/notes/panelmvauthtest-content-type-charset-desviacion-r845.md`.

- [x] T8.1 — EDITAR M6+M7 (tocada CONJUNTA): `backend/src/main/java/com/projectlog/config/RestAuthenticationEntryPoint.java` y `.../ingest/IngestAuthenticationEntryPoint.java`. En el método de escritura del 401 de CADA uno: añadir `import java.nio.charset.StandardCharsets;` y `response.setCharacterEncoding(StandardCharsets.UTF_8.name());` ANTES de `getWriter()` (D-C8-14/R8.45/R8.46). NADA más cambia: mismo JSON `{"code":"invalid_token","message":"Autenticación requerida"}` (el handshake WS pre-upgrade escribe cuerpo vacío y NO pasa por aquí).
- [x] T8.2 — mvn INMEDIATO tras T8.1: `mvn -f backend/pom.xml test` → 174/174 heredados VERDES sin ningún aserto editado (la 1 línea es comportamiento-mínimo). *(Hecho con la desviación anclada ratificada: 174/174 tras pasar las 3 aserciones a `contentTypeCompatibleWith` — ver nota del bloque.)*
- [x] T8.3 — CREATE C39 `backend/src/test/java/com/projectlog/config/EntryPointCharsetTest.java` (UN fichero, `@Nested` `Panel401`+`Ingesta401`): unit puro sin Spring — llama `commence(null, resp, null)`/`handle(null, resp, null)` con `MockHttpServletResponse` (spring-test ya en classpath) + ObjectMapper real; aserta (a) `Content-Type: application/json;charset=UTF-8`, (b) bytes `0xC3 0xB3` presentes y `0xF3` aislado ausente, (c) relectura UTF-8 SIN `U+FFFD` (R8.48/E8.48/E8.49).
- [x] T8.4 — GATE mvn completo (E8.50): 174 heredados + 2 de C39 VERDE; `git diff` backend = EXACTAMENTE M6/M7 (+1 import y +1 línea c/u) + C39.
- [x] T8.5 — COMMIT 1 (sin push): `fix(config): 401 del panel en UTF-8 — setCharacterEncoding antes de getWriter (+ simetría ingest), test del header y del ó (C8)` — SOLO M6/M7/C39.

## Bloque 2 — Fase 1: fundación frontend (deps D3 → config → tokens → api/ws)

- [x] T8.6 — EDITAR M4 `frontend/package.json`: EXACTAMENTE 4 líneas pinnadas SIN `^`/`~` (D-C8-12/R8.50): `react-router-dom: "7.18.4"` en dependencies; `tailwindcss: "4.3.3"`, `@tailwindcss/vite: "4.3.3"`, `vitest: "5.0.5"` en devDependencies; NINGÚN script ni campo más. Luego `npm install` (M5 lockfile regenerado) y verificar el diff de M5 = solo esas 4 + transitivas esperadas (react-router, @tailwindcss/*, vitest/* — E8.54).
- [x] T8.7 — EDITAR M3 `frontend/vite.config.ts` (D-C8-13/R8.49): `import tailwindcss from "@tailwindcss/vite";` + `plugins: [react(), tailwindcss()]` + `base: "/"`. Es el ÚNICO cambio de config C1 del corte.
- [x] T8.8 — CREATE C1 `frontend/src/index.css` (D-C8-8/R8.51): `@import "tailwindcss";` + `@theme` SEMÁNTICO mínimo (ningún color de paleta redefinido — las clases de maqueta ya resuelven) + clases componente VERBATIM de la maqueta 04: `.lv-TRACE…-FATAL`, `.env-development/staging/production`, `.mono`. Sin CDN (R8.50).
- [x] T8.9 — CREATE C2 `frontend/src/api/types.ts` (R8.19/§5.1, DTOs LITERALES): `Nivel/Entorno/EstadoProyecto`, `LoginResponse{token,username,expires_at}`, `MeResponse`, `ProjectDto` SIN `summary`, `ApiKeyResponse{key,prefix,created_at}`, `LogListItemDto` (9), `LogDto` (11, extiende), `LogsPagina{items,next_cursor}`, `ErrorBody`, `SetFilters` (6 claves+nulls — S8-8), `FrameBatch` (5 claves §3.5).
- [x] T8.10 — CREATE C3 `frontend/src/api/client.ts` (R8.13–R8.17/D-C8-3/§5.2): fichero ÚNICO con `fetch` de la app. `request<T>(path, RequestOptions{method,body,headers,bearer})` con `ApiError{status,code?,message,field?}`; Bearer salvo `bearer:false` (solo login); **401 ⇒ SOLO `response.status`, interceptor registrable (`registrarInterceptor401`) y THROW sin `json()` ni `text()` (R8.14); resto no-200 ⇒ `text()` + `JSON.parse` en try/catch con fallback `"HTTP <status>"` (R8.15)**; red caída ⇒ `status: 0` (E8.14–E8.17). Cero logging (R8.12).
- [x] T8.11 — CREATE C4 `frontend/src/api/panel.ts` (§5.3): una función por endpoint (`login` bearer:false, `me`, `listaProyectos`, `proyecto`, `crearProyecto`, `editarProyecto` PATCH solo-modificados, `cambiarEstado`, `regenerarApiKey`, `revocarApiKey`, `logs(f,cursor)`, `log(id)`); los 3 DELETE de C10 (`eliminarProyecto`, `purgarLogs`, `eliminarLog`) devuelven `Promise<never>` con cabeceras `X-Confirm-Name`/`X-Confirm-Log-Id` YA FORMADAS (S8-2/D-C8-10). Sin `?include=summary` (R8.18).
- [x] T8.12 — CREATE C5 `frontend/src/ws/dedup.ts` (D-C8-4): función PURA `fusionarEnVivo(acumulado, lote)` — descarta ids ya presentes (dedup E8.28) y reordena TODO por `comparadorRecibidoDesc` (`received_at` DESC con micros, empate `id` DESC), grupo del lote coherente (comparte `received_at`), NUNCA muta entradas (R8.25/E8.28/E8.25).
- [x] T8.13 — CREATE C6 `frontend/src/ws/client.ts` (D-C8-5/§5.4): clase `ClienteWsLogs` con handshake literal `(wss|ws)://host/ws/logs?token=encodeURIComponent(token)` (S8-5); `set_filters` EXACTO 6 claves con nulls al abrir/cada cambio/cada reconexión (S8-8); parser defensivo: solo `type==="batch"` + 5 claves pasa, resto IGNORADO (E8.33); `retrasoReconexion(intento, aleatorio?)` exportada (backoff 1 s → cap 30 s, jitter ×[0.8,1.2), E8.30); `alCierreInaugural()` si close sin open (E8.31 — el hook resuelve con `me()`). NADA fuera de este fichero construye `new WebSocket`.
- [x] T8.14 — Gate de fase: `npx tsc --noEmit` (desde `frontend/`) VERDE con los 8 módulos nuevos + M3/M4 (los placeholders C1 aún intactos).

## Bloque 3 — Fase 2: hooks (sesión D4, filtros, keyset, ciclo WS)

- [x] T8.15 — CREATE C7 `frontend/src/hooks/useAuth.ts` (D-C8-15/R8.7–R8.12): exporta `AuthContext`, `useAuth()`, lógica de sesión y utilidades de storage (provider JSX en App.tsx). Claves `projectlog.panel.token`/`.expires_at` + destino (`projectlog.panel.destino`) en sessionStorage SIEMPRE try/catch con fallback a memoria (D4/R8.7 — JAMÁS localStorage/cookies); estados `comprobando|anonimo|autenticado`; restauración vía `GET /me` ANTES de renderizar protegidas (R8.2/E8.10); registro del interceptor 401 → logout local + banner "Tu sesión ha expirado (8 h)…" vía `navigate("/login",{state:{motivo:"expirada"}})` (S8-1/R8.11); logout local sin red + cerrar WS (R8.10); `expires_at` INFORMATIVA jamás decide sesión (S8-1/R8.9).
- [x] T8.16 — CREATE C8 `frontend/src/hooks/useLogFilters.ts` (D-C8-6): exporta las funciones PURAS `filtrosDesdeSearch`, `searchDesdeFiltros` (solo claves con valor, orden estable), `toSetFilters` (6 claves+nulls) y `datetimeLocalAIsoConOffset` (offset local del navegador, nunca `toISOString` — S8-9/E8.23); `useSearchParams` como única fuente de filtros (R8.22/R-7).
- [x] T8.17 — CREATE C10 `frontend/src/hooks/usePagination.ts` (D-C8-7): funcs puras `Acumulado<T>`, `paginaInicial`, `acumular` (acumula sin recargar, E8.24), `esFinDeResultados` (`next_cursor === null`).
- [x] T8.18 — CREATE C9 `frontend/src/hooks/useLogsStream.ts` (D-C8-5.5/S8-4/R8.28–R8.31): hook USADO SOLO en `pages/Logs.tsx` — instancia única en `useEffect` + `cerrar()` en cleanup (desmontar/logout/401: idempotente bajo StrictMode); estadosWS → "● Conectado"/"○ Desconectado"/"Reconectando (intento N)…"; al cierre inaugural llama `me()` → 401: interceptor redirect + banner / 200: backoff normal (E8.31); al reconectar reenvía `set_filters` y refresca página 1 fusionando por dedup (E8.30). NUNCA fuera de `/logs`.
- [x] T8.19 — Gate de fase: `npx tsc --noEmit` verde (hooks autocontenidos, sin render). VERDE.

## Bloque 4 — Fase 3: componentes compartidos (13 ficheros)

- [x] T8.20 — CREATE C16 `components/Banner.tsx` (R8.17): variantes info/ámbar/rojo/neutral; `code`+`message` literales; soporte Reintentar (500/502/502-proxy — E8.17/E8.18).
- [x] T8.21 — CREATE C23 `components/Esqueleto.tsx`: esqueleto de tabla y de tarjetas (estados §4 — R8.21/R8.37).
- [x] T8.22 — CREATE C14 `components/BadgeSeveridad.tsx` (V3): `"lv-" + level`; color+texto SIEMPRE; FATAL invertido (R8.20), borde izq. de fila a nivel de `TablaLogs`.
- [x] T8.23 — CREATE C15 `components/PildoraEntorno.tsx` (V4): `"env-" + environment`; production SIEMPRE oscura (R8.20).
- [x] T8.24 — CREATE C12 `components/EstadoConexion.tsx` (V2/R8.28): "● Conectado"/"○ Desconectado"/"Reconectando (intento N)…" — SOLO renderizable en `/logs`.
- [x] T8.25 — CREATE C13 `components/BandaEnVivo.tsx` (V5/R8.25): banda borde discontinuo verde sobre la tabla; marca el grupo recién llegado.
- [x] T8.26 — CREATE C17 `components/Modal.tsx` (D-C8-9): esqueleto genérico + `export function coincideLiteralConTrim(esperado, texto): boolean` (`trim()===trim()`, V10/V11 — E8.37/E8.47c), compartido por 8a/8b/8c.
- [x] T8.27 — CREATE C11 `components/TopBar.tsx` (R8.6): marca "Project_Log", nav Logs/Proyectos, indicador conexión (SOLO `/logs`), nombre usuario, Cerrar sesión → logout local (R8.10). Anchos: `/logs` `max-w-7xl`, fichas `max-w-6xl`.
- [x] T8.28 — CREATE C20 `components/TarjetaProyecto.tsx` (R8.37/E8.39): STATUS literal "Activo"|"Archivado", name, description, `api_key_prefix`+`has_key`, SIN resumen P1; clic → `/proyectos/:id`.
- [x] T8.29 — CREATE C21 `components/FiltrosLogs.tsx` (R8.20/E8.21): grid 1→2→6 columnas según maqueta 02 (proyecto selector, environment, level, desde/hasta `datetime-local`, Reiniciar, texto); cambia filtro ⇒ `setSearchParams` + reinicio cursor + `set_filters` (E8.22); Reiniciar ⇒ query vacía.
- [x] T8.30 — CREATE C22 `components/TablaLogs.tsx` (R8.20/V6): tabla densa 8 columnas (`overflow-x-auto`), SIN exception/metadata; badges/píldoras; FATAL borde izq. de fila; timestamps UTC mono junto a received (S8-9); nombre de proyecto desde Map (S8-6: id CRUDO si desconocido, nunca vacío).
- [x] T8.31 — CREATE C18 `components/ModalBorrado8b.tsx` (R8.35–R8.36/D-C8-10): sentencia EXACTA mostrada `` `DELETE /logs/${id} · header X-Confirm-Log-Id: ${id}` `` (id real), input con `coincideLiteralConTrim`, botón `bg-red-300` deshabilitado → `bg-red-700` al coincidir; al confirmar llama `eliminarLog` (405/404 ⇒ §5 SIN fingir éxito, E8.38).
- [x] T8.32 — CREATE C19 `components/ModalRevelacionKey.tsx` (R8.40/V8/E8.43): key completa `plk_…` + Copiar + aviso literal "No se volverá a mostrar; cópiala ahora"; al cerrar SOLO queda `prefix`; la key JAMÁS se guarda en estado del app más allá del modal.
- [x] T8.33 — Gate de fase: `npx tsc --noEmit` verde.

## Bloque 5 — Fase 4: páginas + rutas (wiring del app, D-C8-7a/D-C8-15)

- [x] T8.34 — EDITAR M1 `frontend/src/main.tsx`: AÑADIR `import "./index.css";` y envolver `App` en `<BrowserRouter>` (StrictMode se conserva; nada más).
- [x] T8.35 — REESCRIBIR M2 `frontend/src/App.tsx` (D-C8-2/D-C8-15): placeholder C1 fuera; `AuthProvider` (provider JSX con el valor de useAuth) + `TopBar` + `Routes` — las 9 rutas 1:1 (R8.1): `/login`; `/` → `<Navigate to="/logs"/>` sin pantalla; `/proyectos/nuevo`, `/` → RUTA ANIDADA `/logs` (`<Route path="/logs" element={<Logs/>}>` + `/logs/:id` → LogDetalle en `<Outlet/>` — D-C8-7a: el listado NO se desmonta al ir al detalle, acumulado intacto — E8.35/R8.33), `/proyectos/nuevo` ANTES de `/:id`, `/proyectos/:id`, `/proyectos/:id/editar`, `*` → NotFound (R8.4). Guard `RequireAuth`: `comprobando` → carga sin render protegido; `anonimo` → guarda destino (path+search) y `<Navigate to="/login"/>`; `autenticado` → children (R8.2/E8.2).
- [x] T8.36 — CREATE C24 `pages/Login.tsx` (R8.8–R8.9/E8.7–E8.9): maqueta 01; `requestLogin` sin Bearer FUERA del interceptor; 200 → guarda token+expires_at → navega al DESTINO guardado con query intacta (R8.5/E8.3); 401 → inline "Usuario o contraseña incorrectos" sin revelar campo ni redirect (E8.8); red → banner "No se puede conectar con el servidor"; botón "Enviando…" deshabilitado (anti-doble-submit, E8.9); con sesión → `<Navigate to="/"/>` (R8.3/E8.4); nota "Sesión con JWT · expira a las 8 h" informativa (R8.9); banner de expiración si `state.motivo==="expirada"` (S8-1).
- [x] T8.37 — CREATE C25 `pages/Logs.tsx` (R8.20–R8.27/D-C8-7b/D-C8-16): maqueta 02 completa — `useLogFilters` (URL) + `useLogsStream` (S8-4); primera carga `GET /logs` sin filtros (defecto 50 — S8-7) + `GET /projects` para Map nombre (S8-6, SIN summary); esqueleto mientras; "Cargar más" con `cursor` acumulando y "fin de resultados" deshabilitado con `next_cursor: null` (E8.24); estados §4 diferenciados (vacío por filtro con sugerencia ≠ vacío legítimo ≠ error con lista conservada — E8.32); banner 400 con `code`+campo culpable marcado si `field` (E8.26/R8.26); banda "● En vivo" + `fusionarEnVivo` de frames (E8.27/E8.28); al reconectar refetch página 1 fusionando (E8.30); 400 ⇒ última lista válida conservada.
- [x] T8.38 — CREATE C26 `pages/LogDetalle.tsx` (R8.32–R8.36/E8.34–E8.38): maqueta 03; LogDto 11 campos; bloques "Excepción y stacktrace" y "Metadata" (JSON `<pre>` mono) SOLO si no nulos, con Copiar; `correlation_id` mono copiable; 404 → "Este registro no existe (o fue eliminado)" + Volver (E8.36); modal 8b (T8.31); DELETE 405/404 → §5 sin fingir éxito (E8.38); Volver conserva filtros/página acumulada (ruta anidada).
- [x] T8.39 — CREATE C27 `pages/Proyectos.tsx` (R8.37/E8.39–E8.40): tarjetas + archivados visibles según toggle; estado vacío "No hay proyectos todavía" + CTA "Nuevo proyecto" (≠ error); esqueleto; error → banner + Reintentar.
- [x] T8.40 — CREATE C28 `pages/ProyectoDetalle.tsx` (R8.38–R8.41/R8.44/E8.41–E8.44+E8.47): maqueta 05; ficha + bloque "API key de ingesta" (prefix mono, `has_key`); Archivar/Desarchivar = PATCH `{status}` con confirmación estándar explicando "NO se borra nada" (NUNCA DELETE — E8.41); Generar/Regenerar → confirmación de consecuencia → 201 `{key,prefix,created_at}` → revelación única → ficha refresca prefix+has_key (E8.43); Revocar → confirmación → 204 → `has_key: false` (E8.44); modales 8a/8c construidos NO operativos (nombre EXACTO + trim, DELETE+`X-Confirm-Name` → 405/404 §5 sin fingir éxito — E8.47); 404 → "Este proyecto no existe…"+Volver.
- [x] T8.41 — CREATE C29 `pages/ProyectoNuevo.tsx` (R8.42/E8.45 — flujo 2 SIN maqueta, coherente con el sistema visual): name 1..200 + description ≤2000; 201 → redirect a `/proyectos/{id}` con aviso "Proyecto creado; genera su API key para que las apps puedan ingestar"; 409 `name_duplicate` → inline "Ya existe un proyecto con ese nombre", datos conservados; 400 → errores POR CAMPO; "Creando…" deshabilitado.
- [x] T8.42 — CREATE C30 `pages/ProyectoEditar.tsx` (R8.43/E8.46 — flujo 2): precarga con `GET /projects/{id}`; `PATCH` con SOLO campos modificados → 200 → ficha con confirmación "Cambios guardados" y `updated_at` refrescado; 409/400 errors por campo; 404 → §5 y retorno a `/proyectos`; JAMÁS control de status aquí.
- [x] T8.43 — CREATE C31 `pages/NotFound.tsx` (R8.4/E8.5): 404 DEL PANEL (nunca vacía, nunca redirect silencioso); sin sesión la cubre el guard.
- [x] T8.44 — GATE de fase (E8.55): `npm run build` VERDE (`tsc --noEmit` strict + `vite build`; base absoluta — el stage 1 del Dockerfile reproducirá esto como guardia del smoke).

## Bloque 6 — Fase 5: Vitest (6 ficheros, lógica pura SIN DOM — D-C8-11/R8.56)

- [x] T8.45 — CREATE `frontend/src/ws/dedup.test.ts` (E8.25/E8.28): duplicado ignorado; lote desordenado vs global; empate con micros (`…123456Z`); grupo del lote coherente; entradas NO mutadas.
- [x] T8.46 — CREATE `frontend/src/hooks/usePagination.test.ts` (E8.24): acumular páginas; fin con `next_cursor: null` (`esFinDeResultados`); vacío legítimo.
- [x] T8.47 — CREATE `frontend/src/api/client.test.ts` (E8.14–E8.17): 401 SIN leer body (fetch mock que CRASHEA si `json()/text()` se invocan — aserto de no-invocación); no-200 tolerantes (405 HTML / 502 texto / cuerpo vacío → `"HTTP <status>"`); 404 JSON parseado code/message/field; Bearer en todo menos login; red caída → status 0.
- [x] T8.48 — CREATE `frontend/src/hooks/useLogFilters.test.ts` (E8.21/E8.23): ida y vuelta filtros⇄query idempotente; `datetimeLocalAIsoConOffset` con offset inyectado (TZ fija del test) viaja ISO-8601 CON offset; `from==to` legal; `toSetFilters` con las 6 claves y nulls.
- [x] T8.49 — CREATE `frontend/src/ws/client.test.ts` (E8.30 + parte de E8.27): secuencia de `retrasoReconexion` con `aleatorio` inyectado (1 s → 2 s → 4 s … cap 30 s; límites jitter [800..30000]); shape byte-exacto de `set_filters` (6 claves, nulls, sin extras); parser ignora frames no-batch sin lanzar (E8.33).
- [x] T8.50 — CREATE `frontend/src/components/Modal.test.ts` (E8.37/E8.47c): `coincideLiteralConTrim` — coincidencia exacta, espacios inicio/final trimmeados, texto incompleto/distinto → false.
- [x] T8.51 — GATE: `npx vitest run` (desde `frontend/`) TODO VERDE en node, cero DOM (E8.56); sin jsdom/testing-library/Playwright en lockfile (E8.58).

## Bloque 7 — Fase 6: retiro D2 + higiene

- [x] T8.52 — DELETE D-1/D-2/D-3: `frontend/public/mockups/04-proyectos.html`, `frontend/public/mockups/05-proyecto-detail.html`, `frontend/public/ws-console.html` (R8.54/D-C8-17). `docs/mockups/` INTOCABLE (git status debe mostrarlo sin cambios). Tras el retiro: `public/` queda vacío.
- [x] T8.53 — Verificación de limpieza: grep de strings huérfanas de las copias retiradas en `frontend/**` (el patrón "fetch tolerante" vive ahora solo en `api/client.ts`); `/favicon.ico` faltante aceptado (D-C8-17, no añadir fichero fuera de frontera).

## Bloque 8 — Fase 7: COMMIT 2 (feat frontend)

- [x] T8.54 — COMMIT 2 (sin push): `feat(frontend): panel React según maquetas — 6 pantallas + flujo 2, guard GET /me, cliente API tolerante, WS en /logs con dedup y backoff, modal 8b V11, base absolute (C8)` — incluye D3 (M4/M5), M1–M3, C1–C31 (32 src), 6 tests Vitest, DELETEs D2. Mensaje con `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

## Bloque 9 — Fase 8: smoke script `scripts/qa_c8_smoke.sh` (C38)

^- [x] T8.55 — CREATE `scripts/qa_c8_smoke.sh` — esqueleto canónico (R8.57/D-C8-§5.6): plantilla qa_c2–qa_c7; `BASE_URL=http://127.0.0.1:8080` (ÚNICO puerto, todo por el proxy nginx); `COMPOSE_BIN` autodetectado; tmpdir `mktemp -d`+`chmod 700`+`trap EXIT`; helper `marca()` con `E8.n: PASS|FAIL|BLOQUEADO` y exit 0/1/2; CERO secretos impresos (JWT y key SOLO en ficheros del tmpdir — S16); **pre-fase autocontenida Q13/E2.7: `down -v` + `up -d --build`** (el `--build` es la guardia tsc+vite vía Dockerfile); guard `FRONTEND_ORIGIN` ausente ⇒ casos WS BLOQUEADO con nota (patrón R-7.j); smoke-of-the-smoke (`GET /me` sin token → 401). Cliente WS RFC 6455 mínimo embebido en el tmpdir (patrón `ws7_client.py` de qa_c7, stdlib).
^- [x] T8.56 — Casos E8-servido + E8-auth + E8-charset: (a) `curl /` y `/logs/123` → 200 `text/html`, assets ABSOLUTOS (`src="/assets/`|`href="/assets/` en el HTML) y NINGÚN `/logs/assets/…` (E8.6/E8.51); (b) login 200 con ORDEN literal `{token,username,expires_at}` (E8.7), `GET /me` con/sin token, 401 sin token decidido por status sin parsear (E8.15/E8.14/E8.19 — todo por `127.0.0.1:8080`); (c) 401 panel e ingest sin credencial: `Content-Type: …charset=UTF-8` + bytes `0xC3 0xB3` (xxd) SIN `0xF3` aislado (E8.48/E8.49).
^- [x] T8.57 — Casos E8-API + E8-C10-negativo: login+regenerate siembra proyecto+key; siembra ≥60 logs vía `POST /ingest/batch` (needle `smoke-c8-<TS>-*`); CRUD proyectos 201/200/409 `name_duplicate`/400 por campo; `?include=summary` aceptado SIN `summary` en el DTO (E8.39); regenerate **201** orden literal `{key,prefix,created_at}` (E8.43); revoke 204 (E8.44); `GET /logs` `{items,next_cursor}` + keyset con cursor acumulando (E8.20/E8.24); 400 filtros: from>to → `filter_from_to_inverted` SIN field, valor inválido → `filter_invalid_value` CON field (E8.23/E8.26); detalle 11 campos (E8.34) y 404 `not_found` de id inexistente (E8.36); PATCH status ARCHIVED/ACTIVE 200 (E8.41); `DELETE /panel/logs/{id}` con `X-Confirm-Log-Id` y `DELETE /panel/projects/{id}` y `/logs` con `X-Confirm-Name` → **405/404 TAL CUAL** (E8.38/E8.47).
^- [x] T8.58 — Casos E8-WS + visual Q23: handshake con token → 101; `set_filters` 6 claves aceptado (sin error de vuelta); ingest 202 → EXACTAMENTE 1 frame batch byte-exacto §3.5 (`b_`+UUID, 5 claves, LogDto 11, batch_id único) (E8.27); 401/403 pre-upgrade (token malo/Origin `http://evil.example`) con cuerpo VACÍO (E8.31); bloque final E8-visual: renders con navegador **BLOQUEADO** como Q23 con instrucción manual para la demo C9 (parejas §3 del spec).
^- [x] T8.59 — Verificación `bash -n scripts/qa_c8_smoke.sh` (sintaxis) + COMMIT 3: `test(smoke): scripts/qa_c8_smoke.sh — servido SPA/base absoluta, auth y charset del panel, CRUD + keyset + regenerate, negativos C10 (405/404), WS batch byte-exacto y 403 Origin (C8)` (+ `Co-Authored-By`).

## Bloque 10 — Fase 9: QA contra pila viva (regla Q13 — sin commits de hallazgos salvo el acotado)

- [x] T8.60 — Recomposición autocontenida del smoke: `bash scripts/qa_c8_smoke.sh` (él solo hace `down -v` + `up -d --build` — NO reusar datos previos) → registrar veredictos E8.n por ID; esperado exit 0 con visuales BLOQUEADO-Q23 anotados (E8.57). Cualquier FAIL con pin [L] verde → anotar tal cual, NO auto-arreglar fuera del guion. *(Corrida 1 (2026-10-28, HEAD d88d715): exit 1 — 13 FAIL / 10 PASS / 1 BLOQUEADO; TODOS guion-side, ver T8.64. Re-corrida 2 (HEAD cbffdd1 + parches): **exit 0 — 23 PASS / 0 FAIL / 1 BLOQUEADO (Q23)**.)*
- [x] T8.61 — Re-corridas de regresión (E8.59): `bash scripts/qa_c3_smoke.sh` (endpoints que la UI consume) VERDE + `bash scripts/qa_c7_smoke.sh` (handshake WS) VERDE; `qa_c2` MAY re-correrse si el patrón del corte lo pide. *(Cifras en qa-report.)*
- [x] T8.62 — mvn final completo: `mvn -f backend/pom.xml test` → 174 heredados + 3 de C39 (=177) VERDE (E8.50 re-verificado tras la pila; la cifra "+2" de esta línea era un error de books — EntryPointCharsetTest tiene 3 @Test). *(Cifra exacta en qa-report.)*
- [x] T8.63 — QA frontend local re-verificada: `npx vitest run` verde (E8.56) + `npm run build` verde (E8.55, además cubierto por el `--build` del smoke). *(55/55 Vitest; build verde — 68 módulos, JS 328.8 kB.)*
- [x] T8.64 — Bucle de hallazgos (patrón C5–C7): si el smoke/Vitest/mvn revelan fallos, corregir acotado a la tabla §4 y COMMIT acotado adicional (`feat(spa)`/`test(streaming)`/`+ronda de hallazgos`, +Co-Authored-By); re-correr el caso en rojo hasta PASS. Si el hallazgo exigiera tocar ficheros FUERA de la tabla §4 → parar y reportar al orquestador (NO resolver). *(H-C8-B1 commit acotado `cbffdd1` pre-corrida: `{"batch"→"logs"}` + guards `|| true`; re-corrida: H-C8-B2/B3/B4/B5 parches del guion (401-real denyAll, lookup por needle, campo_json_nul canon C3, 403 canon E7.9) SIN commit todavía — anclados en `.atl/notes/c8-smoke-401-denyall-error-dispatch-campojson.md`; commit acotado pending antes del docs(SDD). NADA fuera de la tabla §4.)*

## Bloque 11 — Fase 10: verificación final de frontera (checklist §9 del design, dif-0)

- [x] T8.65 — `git status` + diffs: los ficheros M/D son EXACTAMENTE M1–M7 + los 3 DELETE + los CREATE C1–C39; NINGÚN cambio en intocables: `frontend/nginx.conf`, `frontend/Dockerfile`, `compose.yaml`, `.env*`, `frontend/tsconfig.json`, `frontend/index.html`, `frontend/src/vite-env.d.ts`, `docs/**` (incl. `docs/mockups/` — 6 maquetas intactas, E8.52), smokes previos y `ws_echo_test.py`, tests Java C1–C7, TODO backend distinto de M6/M7/C39.
- [x] T8.66 — Diff de `frontend/package.json` = EXACTAMENTE 4 líneas de deps pinnadas; diff de `package-lock.json` = solo esas 4 + transitivas (E8.54). Diff del backend = EXACTAMENTE 2 clases (+1 import c/u) + 1 fichero de test nuevo. *(Verificado 2026-10-28: diff 5df6409..HEAD = 41 A + 3 D + 8 M; M-files = M1–M7 + PanelAuthMvcTest (desviación DEV-C8-1 ratificada); package.json = exactamente las 4 deps pinnadas; intocables (nginx.conf, Dockerfile, compose.yaml, .env*, tsconfig, index.html, vite-env.d.ts, docs/**, streaming tests, ws_echo_test.py) SIN diff.)*
- [x] T8.67 — Greps de higiene en `frontend/src/**` (R8.12/checklist §9): NINGÚN `localStorage` ni `document.cookie` ni `console.log(token…)`; NINGÚN `fetch(` fuera de `api/client.ts`; NINGÚN `new WebSocket` fuera de `ws/client.ts`; NINGUNA lectura de sessionStorage fuera de `useAuth.ts`.
- [x] T8.68 — Cobertura E8.1–E8.59 COMPLETA: cada escenario trazado a su tarea (Vitest → T8.45–T8.50; smoke → T8.56–T8.58; mvn → T8.3/T8.4; MANUAL-Q23 parejas §3 anotadas para demo C9 en el qa-report — E8.58: cero E2E navegado). *(qa-report 59/59 QP — partición corregida tras el verify: 37 PASS planos + 5 CON-MATIZ + 2 PARCIAL + 1 HUECO declarado + 14 MANUAL-Q23 (demo C9); trazabilidad QP↔E8↔test en qa-plan §3 + tests-written §1.)*
- [x] T8.69 — COMMITS en orden verificado (pauta §8 del proposal, SIN push): 1º `fix(config)` (T8.5) → 2º `feat(frontend)` (T8.54) → 3º `test(smoke)` (T8.59) → [acotado de hallazgos si T8.64] → 4º pendiente de cierre: `docs(SDD): corte C8 — artefactos` (versiona `.atl/sdd/C8/{proposal,spec,design,tasks,qa,verify,archive}.md` en las fases siguientes; NO en apply). Racional del orden: D1 primero es autocontenido (mvn verde aislado), deja el backend congelado y el smoke E8-charset lo presupone; el feat agrupa todo el frontend (incl. deps D3 y los 3 DELETEs D2) porque el script smoke va en su propio commit test. *(EJECUTADO 2026-10-28: 1987754 → ea62874 → d88d715 → cbffdd1 → dd26fa9 → commit docs(SDD) de cierre — veredicto verify PASS-with-notes 0 CRITICAL/2W/4S, archivo archive-report.md; SIN push.)*

---

## Apuntes trazables (no bloqueantes — citados, NO corregidos por tasks)

- **D-C8-4 vs literal "insertar al comienzo" de R8.25**: el design justifica el
  reorden TOTAL con `comparadorRecibidoDesc` como la única implementación que
  garantiza AMBAS literales (grupo coherente del lote + orden global
  `received_at DESC, id DESC`) — la inserción literal rompe el orden cuando el
  lote trae timestamps anteriores al acumulado. T8.12 la IMPLEMENTA tal cual;
  E8.28/E8.25 quedan cubiertos por T8.45.
- **Escenarios de hooks del mapa-qa del spec** (fila "E8.2–E8.5/E8.7–E8.13
  hooks con mocks"): la prohibición jsdom/testing-library de D3/R8.56 impide
  Vitest de hooks renderizando; el design §6.1/§3 lo deriva a smoke + MANUAL
  Q23 (parejas del spec) y a Vitest de la lógica PURA extraíble. En C8 quedan
  Vitest puro: E8.15/16/17 (client), E8.21/23 (filtros), E8.24 (keyset),
  E8.25/28/33 (dedup/parser), E8.30 (backoff), E8.37 (comparador); los
  comportamientos de guard/login/logout/indicador (E8.2–E8.5, E8.7–E8.13,
  E8.22, E8.29, E8.31-parcial, E8.32, E8.35, E8.36-parcial) quedan cubiertos
  por smoke REST/WS de servidor + comprobación MANUAL Q23 (demo C9) tal como
  fija la tabla de parejas del spec §3. La desviación de la fila del mapa-qa
  es consecuencia directa de D3 — no resuelta aquí, ya cerrada en design.
- **Fallback Vitest** (D-C8-11/Open Question 1): pin 5.0.5; si apply
  detectara incompatibilidad real, documentar fallback EXACTO `vitest@4.1.12`
  y registrar el cambio del lockfile (NADA más cambia — cero APIs de test
  distintas de `describe/it/expect/vi`).
- **401 pre-upgrade del WS** (D-C8-5.6/Open Question 2): el client no
  diagnostica el status del handshake (no existe tal API); el hook resuelve
  con `me()` (401 → interceptor; 200 → backoff) — T8.13/T8.18.

## Cobertura E8.x (mapa tarea → escenarios)

| Tarea(s) | Escenarios |
|---|---|
| T8.1–T8.4 | E8.48, E8.49, E8.50 (D1) |
| T8.6 | E8.54 |
| T8.7 | R8.49 (servido en E8.6/E8.51) |
| T8.8 | R8.51 (contrato visual — MANUAL R8.52) |
| T8.9 | R8.19 (base de E8.7/E8.19/E8.34/E8.39) |
| T8.10 | E8.14, E8.15, E8.16, E8.17, E8.18 |
| T8.11 | E8.38, E8.47 (S8-2), E8.43, E8.44 |
| T8.12 / T8.45 | E8.25, E8.28 (pila E8.27) |
| T8.13 / T8.49 | E8.30, E8.33, parte E8.27 (pila E8.30/E8.31) |
| T8.15 | R8.7–R8.12 (E8.7/E8.10/E8.11/E8.12/E8.13 smoke+MANUAL) |
| T8.16 / T8.48 | E8.21, E8.23 |
| T8.17 / T8.46 | E8.24 (pila keyset) |
| T8.18 | E8.29, E8.30, E8.31 (parcial Vitest; resto MANUAL/smoke) |
| T8.26 / T8.50 | E8.37, E8.47(c) |
| T8.29 | E8.21/E8.22/E8.23 (UI) |
| T8.31 / T8.38 | E8.34–E8.38 |
| T8.32 / T8.40 | E8.41–E8.44, E8.47 |
| T8.35 | E8.1–E8.5 (rutas/guard — Vitest de tabla n/a; smoke+MANUAL) |
| T8.36 | E8.7–E8.9 (formularios — MANUAL Q23) |
| T8.37 | E8.20–E8.27, E8.32 (estados — Vitest puro + MANUAL) |
| T8.41 / T8.42 | E8.45, E8.46 |
| T8.44 / T8.63 | E8.55 |
| T8.55–T8.58 | E8.6, E8.7, E8.14, E8.15, E8.19, E8.48, E8.49, E8.20, E8.23, E8.24, E8.36, E8.38, E8.39, E8.41–E8.44, E8.47, E8.27, E8.30, E8.31 |
| T8.51 | E8.56, E8.58 |
| T8.60 | E8.57 |
| T8.61 | E8.59, E8.53 |
| T8.62 | E8.50 (re-verificado) |
| T8.65–T8.68 | cierre de frontera (R8.47/D2/E8.52/E8.54/E8.58) + trazabilidad |

Total: **69 tareas** en 11 fases; 59 escenarios E8.1–E8.59 cubiertos
(~35 automatizables y ~24 con componente MANUAL-Q23 → demo C9); 39 ficheros
CREATE + 7 MODIFY + 3 DELETE (tabla §4 del design — frontera CERRADA, dif-0
fuera de ella).

skill_resolution: injected