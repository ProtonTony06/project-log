---
project: Project_Log
change: C8
artifact: spec
last_updated: 2026-10-26
status: complete
---

# Spec — C8 · Frontend del panel según maquetas (React + TypeScript SPA)

> Especificación normativa del corte C8 para la implementación
> (sdd-design → tasks → apply → qa → verify). Fuente: `proposal.md` C8
> (status **approved** — GATE APROBADO por el usuario 2026-10-26 TAL CUAL,
> sin matices; frontmatter `gate:` con veredicto) y anclas de `explore.md`
> C8. Los literales de API/DTOs se **citan** del Backend Schema
> (`docs/05_BACKEND_SCHEMA.md` §3.2/§3.3/§3.4/§3.5), de AppFlow
> (`docs/03_APPFLOW.md` §1 rutas / §3 flujos 1–8 / §4 estados / §5 errores),
> de UIUX (`docs/04_UIUX.md` V1–V11 y paleta §2) y de la ficha C8 del
> `07_SLICING_PLAN.md` — nunca se redefinen. Código verificado en HEAD
> `5df6409` (C7 cerrado; backend vivo 174/174, pila completa 17 rutas
> `/api/v1/**` + `/ws/logs`).

## 0. Decisiones de gate ya aprobadas (resumen vinculante 2026-10-26)

| Gate | Decisión aprobada |
|---|---|
| D1 | Fix 401 UTF-8 **ENTRA en C8**: `setCharacterEncoding("UTF-8")` antes de `getWriter()` en `RestAuthenticationEntryPoint` + **simetría** en `IngestAuthenticationEntryPoint` + test del charset. alcance backend EXACTO: solo esas 2 clases + su test; nada más del backend se toca |
| D2 | Retiro SÍ de `frontend/public/mockups/**` y `frontend/public/ws-console.html` en C8 (cortes que los sustituyen). `docs/mockups/` (congelado, contrato visual) NO se toca |
| D3 | Deps nuevas SÍ con versiones PINNADAS: `react-router-dom@7.18.4`, `tailwindcss@4.3.3` + `@tailwindcss/vite@4.3.3`, Vitest para lógica pura. NINGUNA otra dep (sin estado global, sin librerías de componentes, sin testing-library/jsdom) |
| D4 | JWT en **sessionStorage** con fallback a memoria por pestaña (try/catch por storage bloqueado); NUNCA `localStorage` ni cookies. Se guarda `token` + `expires_at` |

Recaídas del gate: indicador de conexión persistente SOLO en `/logs` (decisión
C del slicing); las 2 rutas de flujo 2 (`/proyectos/nuevo`,
`/proyectos/:id/editar`) SIN maqueta — fuera del GATE visual; los 3 DELETE y
`include=summary` son C10 (la UI de los modales sí se construye aquí).

## 0.2 Decisiones de SPEC — cierre de los 10 huecos (S8-1…S8-10)

VINCULANTES para design/tasks. Resuelven los puntos que la proposal dejó
explícitos como "¿comportamiento?" o al margen.

| # | Hueco | Cierre del spec |
|---|---|---|
| S8-1 | Caducidad 8 h / expiración — ¿comportamiento? | **§2** — SIN temporizador proactivo ni countdown en la barra. La expiración se detecta SOLO por el 401 de la siguiente llamada REST o del handshake WS → interceptor global → `/login` con banner "Tu sesión ha expirado (8 h): inicia sesión de nuevo". La restauración tras F5 valida SIEMPRE vía `GET /me` (fuente única); `expires_at` guardado es INFORMATIVA (MAY usarse para mostrar la nota de la maqueta 01 "Sesión con JWT · expira a las 8 h", JAMÁS para decidir sesión por reloj local — el drift de reloj del cliente no es autoridad) |
| S8-2 | DELETE hoy inexistente — ¿se forma la cabecera? | **§5/§6** — SÍ: al confirmar un modal 8, el cliente envía YA el `DELETE` con su cabecera `X-Confirm-*` CORRECTAMENTE FORMADA (`X-Confirm-Log-Id: <id>` / `X-Confirm-Name: <name>`) — la integración de C10 no tocará la UI. Con el backend actual el endpoint no existe → 405/404, que el cliente muestra como AppFlow §5 SIN fingir éxito |
| S8-3 | ¿Se construyen los modales 8a/8c o solo 8b? | **§6** — Se construyen LOS TRES en la ficha `/proyectos/:id` (y 8b también desde `/logs/:id`), según zona destructiva de la maqueta 05: 8a = escribir el NOMBRE EXACTO del proyecto (V10), 8c = idem (proyecto+key sobreviven), 8b = sentencia literal (V11). Sus peticiones 405/404 → §5 sin fingir éxito |
| S8-4 | Ciclo de vida del WebSocket | **§4** — UNA ÚNICA conexión WS por pestaña SIEMPRE: se abre SOLO al entrar en `/logs`, se cierra al salir de la vista, al logout y ante 401 (interceptor). Reentrar a `/logs` puede reabrir; NUNCA deben coexistir dos sockets (guardeado en el hook) |
| S8-5 | Token en el WS | v1 contractual = query param del handshake (`/ws/logs?token=`); token en primer frame `{"type":"auth",…}` = FUTURO, fuera de C8 (literal §3.5) |
| S8-6 | Nombre de proyecto en la fila de `/logs` | El listado no transporta el nombre (LogListItemDto 9 campos): `/logs` carga `GET /projects` (SIN `include=summary` — C10) y mapea `project_id → name`; id sin proyecto conocido (futuro DELETE) se muestra CRUDO (id), nunca como vacío ni error |
| S8-7 | `limit` en la UI | El cliente NO fija `limit` (usa el defecto 50 del backend; la maqueta 02 no expone control). `may` añadirse en C10+; NO en C8 |
| S8-8 | Shape exacto de `set_filters` enviado | SIEMPRE objeto con las **6 claves presentes** (`project_id, environment, level, from, to, text`), con `null` cuando el filtro no está aplicado (ejemplo §3.5 `"to": null`). Claves NUNCA añadidas (`cursor`/`limit` jamás) |
| S8-9 | Fechas: entrada y render | Entrada `from`/`to` = `datetime-local` del navegador → se envía ISO-8601 CON offset local (el backend normaliza a UTC — literal AppFlow flujo 4). Render de `timestamp`/`received_at`/fechas = UTC con sufijo `Z`, monoespaciado, `timestamp` siempre junto a `received_at` (UIUX §3) |
| S8-10 | Pin de Vitest | `package.json` SIN rangos (`^`/`~`) en las 4 deps nuevas D3; la versión EXACTA de `vitest` la fija design (línea estable de las publicadas) y queda registrada en el diff de `package-lock.json` anotado en el apply |

## 0.3 Frontera de ediciones (lista cerrada)

- **Nuevos bajo `frontend/src/`** (≈30–34): `api/client.ts`, `api/types.ts`,
  `api/panel.ts`, `ws/client.ts`, `ws/dedup.ts`, `hooks/useAuth.ts`,
  `hooks/useLogFilters.ts`, `hooks/useLogsStream.ts`, `hooks/usePagination.ts`,
  `components/` (TopBar, EstadoConexion, BandaEnVivo, BadgeSeveridad,
  PildoraEntorno, Banner, Modal, ModalBorrado8b, ModalRevelacionKey,
  TarjetaProyecto, FiltrosLogs, TablaLogs, Esqueleto), `pages/` (Login, Logs,
  LogDetalle, Proyectos, ProyectoDetalle, ProyectoNuevo, ProyectoEditar,
  NotFound), `index.css` (@theme tokens 04_UIUX) + ficheros `*.test.ts` de
  Vitest. Nombres de fichero CONTRACTUALES (proposal §3); la partición interna
  (qué hook extrae qué) es del design, respetando el comportamiento de este
  spec.
- **Editados** (lista EXACTA): `frontend/src/main.tsx` (monta router),
  `frontend/src/App.tsx` (REESCRITO — adiós placeholder C1),
  `frontend/vite.config.ts` (solo: `base: "./"` → `base: "/"` + plugin
  `@tailwindcss/vite` en `plugins`), `frontend/package.json` +
  `frontend/package-lock.json` (solo deps D3), y si D1:
  `backend/src/main/java/com/projectlog/config/RestAuthenticationEntryPoint.java`,
  `backend/src/main/java/com/projectlog/ingest/IngestAuthenticationEntryPoint.java`
  (SOLO la línea de encoding) + su test nuevo.
- **REMOVIDOS (D2)**: `frontend/public/mockups/04-proyectos.html`,
  `frontend/public/mockups/05-proyecto-detail.html`,
  `frontend/public/ws-console.html`.
- **INTOCABLES**: `frontend/nginx.conf`, `frontend/Dockerfile`,
  `compose.yaml`, `.env.example`, `.env`, TODO el backend salvo las 2 clases
  de D1, `docs/**` (congelados, incl. `docs/mockups/`), los smokes
  `qa_c2`–`qa_c7` y `ws_console` scripts existentes, tests Java C1–C7.

## 1. Requisitos normativos (RFC 2119)

MUST/DEBE, MUST NOT/NO DEBE, SHOULD, MAY según RFC 2119. Cada requisito es
verificable por ≥1 escenario de §10. Organizado por dominios.

---

## DOMINIO `frontend-app/routing` (app shell, guard, 9 rutas)

- **R8.1** — El router DEBE registrar las **9 rutas de AppFlow §1** 1:1 con
  react-router-dom 7.18.4 (`BrowserRouter` + `Routes`): `/login`, `/`,
  `/logs`, `/logs/:id`, `/proyectos`, `/proyectos/nuevo`,
  `/proyectos/:id`, `/proyectos/:id/editar`, `*` (fallback 404). `/` DEBE
  redirigir a `/logs` sin renderizar pantalla propia. NINGUNA otra ruta de
  SPA (las rutas `/api/v1/ingest*` y `/ws/logs` son infraestructura, flujo 10,
  nunca navegación).
- **R8.2** — ROUTE GUARD: toda vista protegida sin JWT válido DEBE pasar por
  el guard, que consulta `GET /me` (Bearer) → 401 ⇒ redirige a `/login`
  **recordando la vista destino** (path + query) para volver tras el login.
  Mientras el guard resuelve, DEBE mostrarse estado de carga (sin parpadeo de
  la vista protegida). Restauro de sesión al recargar: `GET /me` DEBE
  resolverse ANTES de renderizar la vista protegida (AppFlow flujo 1 paso 4).
- **R8.3** — `/login` con sesión válida (token presente y `/me` 200) DEBE
  redirigir a `/` (que salta a `/logs`). Con token en storage pero `/me` 401 →
  token descartado y formulario normal.
- **R8.4** — `*` (ruta desconocida): con sesión → pantalla 404 del panel; sin
  sesión → redirige a `/login` con destino guardado. EN SPA nunca hay 404 de
  servidor (SPA fallback de nginx C1): el 404 es SIEMPRE del cliente.
- **R8.5** — Retorno tras login: el login OK DEBE devolver al usuario a la
  vista destino guardada incluyendo su query string (los filtros de `/logs`
  van en la URL — S8/R8.8), por defecto `/` → `/logs`.
- **R8.6** — El layout del panel (App.tsx reescrito) DEBE llevar barra superior
  fija con marca "Project_Log", nav **Logs / Proyectos**, indicador de conexión
  (renderizado SOLO dentro de `/logs` — R8.16), nombre de usuario y
  **Cerrar sesión**. Sin menús por roles (admin único). Máximos de ancho: la
  vista `/logs` `max-w-7xl` centrado; fichas 03/05 `max-w-6xl` (UIUX §5).

## DOMINIO `frontend-app/auth-sesion` (D4 — login, JWT, logout, caducidad)

- **R8.7** — Persistencia del JWT (D4): token + `expires_at` DEBEN guardarse
  en `sessionStorage` bajo claves propias del panel, envueltas SIEMPRE en
  try/catch (storage bloqueado/deshabilitado ⇒ fallback a MEMORIA de la
  pestaña, sesión funcional aunque no sobreviva a F5). MUST NOT usarse
  `localStorage` ni cookies (de ningún tipo). El hook `useAuth` es la ÚNICA
  fuente de sesión de toda la app (sin lecturas dispersas del storage).
- **R8.8** — `/login` según maqueta 01: formulario usuario/contraseña →
  `POST /api/v1/panel/auth/login` con `{username, password}` (ÚNICO endpoint
  sin Bearer y FUERA del interceptor global). 200 → guarda token+`expires_at`,
  redirige a la vista destino. Estados §4: botón **"Enviando…"** deshabilitado
  durante la petición (anti-doble-submit); 401 → mensaje inline
  **"Usuario o contraseña incorrectos"** bajo el formulario, SIN cerrar la
  vista, SIN revelar qué campo falló y SIN redirect (única excepción al
  interceptor — AppFlow §5); error de red → banner
  "No se puede conectar con el servidor" + reintentar.
- **R8.9** — NOTA de sesión (maqueta 01): con sesión activa, la UI puede
  mostrar el aviso "Sesión con JWT · expira a las 8 h" usando `expires_at`
  (informativa — S8-1). MUST NOT deducir "sesión válida" comparando
  `expires_at` con el reloj local: la validez es SIEMPRE lo que diga `GET /me`
  o un endpoint real.
- **R8.10** — Logout: pulsar **Cerrar sesión** (y SOLO eso — no hay endpoint
  logout) DEBE: borrar el token del sessionStorage (o de memoria), cerrar el
  WebSocket si está abierto y redirigir a `/login`. Sin llamada de red. El JWT
  sigue válido hasta `expires_at` (aceptado v1, flujo 1 paso 5).
- **R8.11** — Caducidad en caliente (S8-1): sin temporizador proactivo; el
  401 de CUALQUIER llamada REST o del handshake WS dispara el interceptor
  global → logout local + redirigir a `/login` con destino + banner
  **"Tu sesión ha expirado (8 h): inicia sesión de nuevo"**. Al re-loguearse
  se vuelve a la misma vista con los mismos filtros (van en la URL).
- **R8.12** — Higiene (S16/CD-4): NINGÚN código de la app loguea el JWT, el
  token del WS, ni la API key revelada (jamás en `console.log`, jamás en
  atributos visibles fuera de su ventana — R8.28).

## DOMINIO `frontend-app/api-client` (tolerancias heredadas — NORMATIVO)

- **R8.13** — Módulo ÚNICO `api/client.ts`: TODO fetch `/api/v1/**` del panel
  pasa por él (el resto de la app jamás llama a `fetch` directo). Añade
  `Authorization: Bearer <jwt>` a toda llamada de panel SALVO
  `POST /auth/login`. Rutas RELATIVAS de mismo origen (CORS = 0 en producción;
  el proxy nginx de C1 intacto).
- **R8.14** — REGLA 401 (tolerancia normativa heredada del puente C3/C4.5,
  patrón mockups 04/05 — válida ANTES y DESPUÉS del fix D1): ante una
  respuesta 401, el cliente DEBE decidir SOLO con `response.status` y NUNCA
  parsear cuerpo — ni `r.json()` ni `r.text()` en ese camino. (Motivo
  histórico: el 401 salía ISO-8859-1 y `Response.json()` decodifica siempre
  UTF-8 → mojibake `U+FFFD`; con D1 sale UTF-8, pero la regla no se acopla a
  una corrección de backend).
- **R8.15** — REGLA resto de no-200 (400/403/404/405/409/500/502): leer con
  `r.text()` (respeta el charset del header) y `JSON.parse` DENTRO de
  try/catch; SI el parse falla (405 de Spring puede venir en HTML, cuerpo
  vacío, otro texto) se usa el fallback `"HTTP <status>"`. NUNCA un parseo
  estricto (`r.json()` directo) sobre un no-200 que pueda romper la UI.
- **R8.16** — Interceptor global 401: descarta el token, CIERRA el WebSocket
  si estaba abierto (AppFlow §5), redirige a `/login` con la vista destino
  guardada y banner de sesión expirada (R8.11). EXCEPCIÓN ÚNICA: el 401 de
  `POST /auth/login` NO pasa por el interceptor (R8.8). El login en curso
  durante un 401 de otra llamada no compite (idempotente: el redirect final
  es a `/login` con destino).
- **R8.17** — Vocabulario de errores con literales del Schema (§3.3/§3.4),
  mapeados a UI, NUNCA reescritos:
  - 400 filtros de `GET /logs`: `filter_from_to_inverted` (SIN `field`),
    `filter_invalid_value` (con `field` = `project_id`|`environment`|`level`|
    `from`|`to`), `filter_limit_invalid`, `filter_cursor_invalid` — cuerpo
    `{"code", "message", "field"?}`.
  - 404 entidad: `not_found` → "No existe (quizás fue eliminado)".
  - 409: `name_duplicate` → en línea "Ya existe un proyecto con ese nombre".
  - 403: genérico → banner "No tienes permiso para esta operación" (salvaguarda:
    el panel NUNCA recibe `project_archived` — es SOLO de ingesta, flujo 10;
    `403 project_archived`/`413`/`429` SIN estado de pantalla diseñado).
  - 500 (y 502 de nginx con backend caído) → banner "Error del servidor;
    inténtalo de nuevo" + botón **Reintentar** por pantalla.
  - Red caída (fetch sin respuesta) → banner "Sin conexión con el servidor";
    la navegación por vistas ya renderizadas sigue.
- **R8.18** — Respuestas de éxito fuera del feliz: `202 {"accepted": N}` es
  SOLO de ingesta — SIN UI (flujo 10). Las cabeceras `X-Confirm-*` se ENVÍAN
  ya formadas en los DELETE de los modales (S8-2) pero NUNCA se usan para otra
  cosa en C8 y su integración real es C10. `?include=summary` NO se invoca en
  C8 (aunque el backend lo acepte hoy sin `summary` en el DTO — C10).
- **R8.19** — Tipos (`api/types.ts`) con los DTOs LITERALES §3.3:
  `LoginResponse {token, username, expires_at}`, `MeResponse {username, role,
  expires_at}`, `ProjectDto {id, name, description, status: "ACTIVE"|
  "ARCHIVED", created_at, updated_at, api_key_prefix, has_key}` (el `summary`
  de §3.3 NUNCA viaja en C8), `ApiKeyResponse {key, prefix, created_at}` (ese
  orden), `LogListItemDto` 9 campos `{id, project_id, level, environment,
  timestamp, received_at, message, source, correlation_id}`,
  `LogDto` 11 campos (los 9 + `exception` + `metadata`), `LogsPagina {items,
  next_cursor|null}`, `ErrorBody {code, message, field?}`. Fechas ISO-8601 UTC
  con `Z` (S8-9).

## DOMINIO `frontend-ui/logs` (maqueta 02 — dashboard, keyset, streaming)

- **R8.20** — Pantalla según maqueta 02 (contrato visual 1:1): filtros en grid
  reorganizable (1→2→6 columnas: proyecto selector, environment, level,
  desde/hasta `datetime-local`, Reiniciar, texto), banda **"● En vivo"**
  (borde discontinuo verde sobre la tabla — V5), tabla densa de **8 columnas**
  (nivel badge, timestamp, environment, proyecto, source, mensaje truncado,
  + las de la maqueta) con overflow-x-auto controlado, **SIN columnas
  exception/metadata** (V6 — no se transportan en la lista), paginación
  **"Cargar más"** al final. Badges de severidad V3 (color+texto siempre,
  FATAL invertido sólido con borde izquierdo de fila); píldoras de entorno V4
  (production SIEMPRE oscura). Timestamps UTC mono junto a received (S8-9).
- **R8.21** — Primera carga: `GET /api/v1/panel/logs` sin filtros (defecto 50
  — S8-7) + `GET /api/v1/panel/projects` para el selector (S8-6). Orden
  `received_at DESC`. Esqueleto de tabla mientras (estado §4) — y en cada
  refetch de página 1 por cambio de filtros.
- **R8.22** — **Estado de filtros EN LA QUERY STRING** (obligatorio — AppFlow
  flujo 4/§5, Riesgo R-7): cada aplicación/limpieza de filtros actualiza la
  URL (`useSearchParams`), un F5 conserva el contexto, la vista es
  compartible y el retorno desde el detalle la encuentra intACTA. Cambiar un
  filtro ⇒ vuelve a la primera página (cursor reiniciado) + refetch + reenvío
  de `set_filters` (flujo 6 paso 5). **Reiniciar** ⇒ `GET /logs` sin
  parámetros y primera página.
- **R8.23** — Keyset: "Cargar más" pide `cursor=<next_cursor>` de la última
  página recibida y ACUMULA filas (nunca recarga la tabla desde el principio).
  `next_cursor: null` ⇒ botón deshabilitado con **"fin de resultados"**. El
  acumulado DEBE ser estable ante llegadas en vivo: insertar logs nuevos entre
  páginas no duplica ni salta (dedup por id, R8.25).
- **R8.24** — Estados §4 de `/logs`, diferenciados y en este orden de
  decisión: cargando (esqueleto) ≠ **vacío por filtro** ("No hay logs que
  cumplan los filtros" + sugerencia "Prueba a reiniciar filtros o amplía el
  rango temporal") ≠ vacío legítimo sin filtros ≠ error (banner §5 con la
  lista previa conservada). NUNCA se traduce un 404 como vacío ni un error
  como estado vacío.
- **R8.25** — **Banda "● En vivo" + dedup**: cada log de un frame `batch`
  recibido se deduplica por `id` contra el acumulado (si ya está — por
  "Cargar más" o refetch — se IGNORA); si no, se inserta AL COMIENZO del
  listado respetando el orden del lote y el orden global
  `received_at DESC, id DESC` (con micros). El lote entra como grupo
  coherente; "Vivo, sin novedades" (sin frames que pasen filtros) NO es error.
- **R8.26** — **Banner 400 con campo culpable**: ante 400 de filtros, banner
  rojo con el `code` y el `message` devueltos; si el cuerpo lleva `field`, el
  campo culpable queda marcado/enfatizado (p. ej. `filter_invalid_value` con
  `field: "level"`); `filter_from_to_inverted` viaja SIN field (literal) y se
  muestra sin marca de campo. La última lista válida se CONSERVA pintada
  (flujo 4 paso 6).
- **R8.27** — Cliente WS (`ws/client.ts` + `useLogsStream`) según §3.5 y
  patrón `ws-console.html`: handshake
  `new WebSocket((location.protocol==="https:"?"wss":"ws") + "://" +
  location.host + "/ws/logs?token=" + encodeURIComponent(token))` — token SOLO
  en query (S8-5). Tras conectar envía `set_filters` con shape EXACTO de 6
  claves con nulls (S8-8, ejemplo §3.5); lo reenvía tras CADA cambio de
  filtros y tras CADA reconexión. Escucha frames de texto y los trata como
  `batch` (el servidor jamás manda otro JSON — R7.15 de C7): un frame que no
  casé con el shape de 5 claves se IGNORA sin crashear (defensiva). El socket
  NO envía nada más (solo responde pings — el navegador los responde solo).
- **R8.28** — **Indicador de conexión persistente SOLO en `/logs`** (decisión
  C): "● Conectado" (verde, tras handshake 101 + `set_filters` enviado) /
  "○ Desconectado" (gris) / "Reconectando (intento N)…" durante el backoff.
  Banners ligados: 401 de handshake → estado ○ + banner "Sesión expirada:
  vuelve a iniciar sesión" + redirect `/login` (interceptor); fallo de
  red/backend → "○ Desconectado" + banner "Sin conexión en vivo"; socket
  cerrado en caliente → "○ Desconectado" + banner **"Streaming inactivo: los
  datos en pantalla no se actualizan en vivo"** (la lista sigue por REST —
  fallback del PRD). Fuera de `/logs` NO existe indicador NI socket (R8.2/S8-4).
- **R8.29** — **Reconexión**: backoff exponencial CON jitter de 1 s → máx
  30 s (flujo 6c); al lograr reconexión → indicador vuelve a "● Conectado",
  banner retirado, reenvía `set_filters` (obligación) y REFRESCA la primera
  página de `GET /logs` para ponerse al día (la dedup evita duplicados con lo
  acumulado; sin replay de lo perdido).
- **R8.30** — Cierres del servidor tratados uniformemente: `onclose` por
  CUALQUIER código (1013 backpressure, 1001 latido, red) ⇒ mismo camino:
  estado desconectado + reconexión con backoff (el cliente no distingue
  causa — no hay oráculo). `onerror` idem. El cliente JAMÁS cierra por
  lentitud propio ni limita el buffer.
- **R8.31** — Ciclo de vida (S8-4): una única conexión; abierta al montar
  `/logs` (con sesión), cerrada al desmontar la vista, al logout y ante 401.
  Recargar `/logs` reabre una sola.

## DOMINIO `frontend-ui/log-detail` (maqueta 03 — detalle + modal 8b V11)

- **R8.32** — Pantalla según maqueta 03 sobre `GET /api/v1/panel/logs/{id}`
  con el LogDto COMPLETO de **11 campos**: cabecera (id, level, project
  [mapeo S8-6], environment, `timestamp`, `received_at`, source,
  `correlation_id`), cuerpo del `message`, bloque **"Excepción y stacktrace"**
  solo si `exception` no es null (`<pre>` MONOESPACIADO + botón **Copiar**),
  bloque **"Metadata"** solo si `metadata` no es null (JSON formateado en
  `<pre>` mono + Copiar). Ambos nulos ⇒ esas zonas se OCULTAN, no quedan
  vacías (flujo 5 paso 3). `correlation_id` mono y copiable (select-all o
  botón Copiar — UIUX §6).
- **R8.33** — Volver: regresa a `/logs` con filtros y página acumulada
  INTACTOS (van en la URL + el acumulado de lista se conserva al volver —
  AppFlow flujo 5 paso 4; convención §5 "nunca perder el estado").
- **R8.34** — 404 del detalle: mensaje **"Este registro no existe (o fue
  eliminado)"** + botón Volver a `/logs`. Nunca mostrado como vacío (§4/§5).
- **R8.35** — **Modal 8b V11** (borrado registro a registro, zona destructiva
  marcada — V7): botón **Eliminar registro** separado de la lectura; al abrir,
  el modal muestra el `id` del log y la **SENTENCIA LITERAL que se enviará**:
  `DELETE /logs/<id> · header X-Confirm-Log-Id: <id>` (con el id REAL del
  registro — patrón maqueta 03 con 48211), y un input donde el usuario DEBE
  escribir esa sentencia exacta. Comparación LITERAL con trim (espacios
  iniciales/finales recortados). El botón rojo de confirmación permanece
  **DESHABILADO** (`bg-red-300`) hasta que el texto coincida → entonces
  habilitado (`bg-red-700`).
- **R8.36** — Petición del modal (S8-2): al confirmar (texto correcto),
  envía `DELETE /api/v1/panel/logs/{id}` con cabecera
  `X-Confirm-Log-Id: <id>` CORRECTAMENTE FORMADA (para C10). Con el backend
  actual el endpoint NO existe → el cliente recibe **405/404** y la UI los
  muestra como AppFlow §5 (banner "No existe (quizás fue eliminado)" si 404;
  "HTTP 405" + banner si 405 — con el parseo tolerante de R8.15), SIN fingir
  éxito, SIN marcar el registro como eliminado, modal cerrado con el error
  visible y la ficha intacta. Estados §4 de los modales también aplican: error
  400 dentro del modal sin cerrarlo, botón "Eliminando…" mientras corre.

## DOMINIO `frontend-ui/projects` (maquetas 04/05 + flujo 2 sin maqueta)

- **R8.37** — `/proyectos` según maqueta 04 sobre `GET /api/v1/panel/projects`:
  tarjetas con **STATUS** (etiqueta "Activo"|"Archivado" — literal `status`
  del ProjectDto), `name`, `description` (si existe), `api_key_prefix` +
  `has_key`, SIN resumen P1 (el `summary` no viaja en C8 — S8-6/R8.19; la
  tarjeta muestra solo lo que viaja en ProjectDto). Archivados VISIBLES con su
  estado (toggle por `?status=` o local según maqueta). Estado vacío legítimo:
  **"No hay proyectos todavía"** + CTA **"Nuevo proyecto"**. Esqueleto de
  tarjetas mientras; error → banner + Reintentar.
- **R8.38** — Fila/tarjeta clicada → `/proyectos/:id` (ficha, maqueta 05):
  datos del proyecto, bloque **"API key de ingesta"** (prefix `plk_…` mono,
  `has_key`, fecha de creación de key si la hay), acciones según estado:
  editar, archivar/desarchivar, generar/regenerar/revocar key; modales 8a/8c
  en la zona destructiva marcada (S8-3). 404 → "Este proyecto no existe (o
  fue eliminado)" + Volver a `/proyectos` (§5).
- **R8.39** — Archivar/desarchivar (flujo 7): botón **Archivar** →
  confirmación explicando "Dejará de aceptar ingesta; sus logs se conservan
  consultables; NO se borra nada" (confirmación ESTÁNDAR — sin exigencia de
  texto) → `PATCH /api/v1/panel/projects/{id}` `{status: "ARCHIVED"}` → 200
  ⇒ ficha y listado muestran "Archivado" y el botón pasa a **Desarchivar**.
  Desarchivar ⇒ confirmación → `{status: "ACTIVE"}` → 200. NUNCA DELETE para
  archivar. Sin UI para `403 project_archived` (solo ingesta).
- **R8.40** — **Revelación única de API key** (flujo 3, V8 — regla dura):
  *Generar API key* (sin key o tras revocar) / *Regenerar* → modal de
  confirmación explicando la CONSECUENCIA ("La key actual queda invalidada al
  instante para todas las apps de este proyecto") → al confirmar
  `POST /api/v1/panel/projects/{id}/api-key/regenerate` → **201
  `{key, prefix, created_at}`** → modal de REVELACIÓN ÚNICA con la key
  completa, botón **Copiar** y aviso literal **"No se volverá a mostrar;
  cópiala ahora"**. Al cerrar el modal (o recargar la ficha) SOLO queda el
  nuevo `prefix`; la ficha refresca `has_key: true` + `api_key_prefix`. La
  key completa JAMÁS aparece fuera de esa ventana (ni en listado, ni en
  redirección, ni re-render posterior — puede no persistirse en ningún estado
  de la app más allá del modal).
- **R8.41** — Revocar: botón **Revocar** (con key activa) → confirmación
  ("las apps dejarán de poder ingestar hasta generar una nueva") →
  `DELETE /api/v1/panel/projects/{id}/api-key` → **204** ⇒ ficha muestra
  `has_key: false` y retira el `prefix`.
- **R8.42** — `/proyectos/nuevo` (flujo 2, SIN maqueta — fuera del GATE
  visual): formulario vacío (name obligatorio, description opcional) →
  `POST /api/v1/panel/projects` `{name, description?}` (name 1..200,
  description ≤2000). 201 `ProjectDto` (con `api_key_prefix` vacío y
  `has_key: false`) ⇒ redirige a la ficha nueva `/proyectos/{id}` con aviso
  **"Proyecto creado; genera su API key para que las apps puedan ingestar"**.
  409 `name_duplicate` ⇒ en línea bajo el campo **"Ya existe un proyecto con
  ese nombre"**, datos conservados. 400 validación ⇒ errores POR CAMPO, datos
  conservados. Botón "Creando…" deshabilitado mientras (§4).
- **R8.43** — `/proyectos/:id/editar` (flujo 2, SIN maqueta): pre-carga con
  `name`/`description` de `GET /projects/{id}` → al guardar, `PATCH {name?,
  description?}` SOLO con los campos modificados → 200 `ProjectDto` ⇒ vuelve
  a la ficha con confirmación **"Cambios guardados"** y `updated_at`
  refrescado; 409/400 ⇒ errores en el formulario (idem R8.42); 404 (proyecto
  borrado en otra pestaña) ⇒ mensaje §5 y retorno a `/proyectos`. El `status`
  JAMÁS se edita aquí (va por flujo 7 en la ficha).
- **R8.44** — Modales 8a/8c construidos pero NO operativos (S8-3/S8-2): 8a
  "Eliminar definitivamente" exige escribir el NOMBRE EXACTO del proyecto
  (V10, trim, botón deshabilitado hasta coincidir) y envía
  `DELETE /panel/projects/{id}` con `X-Confirm-Name: <name>`; 8c "Purgar
  logs" explica que el proyecto y su key sobreviven, exige el nombre y envía
  `DELETE /panel/projects/{id}/logs` con `X-Confirm-Name: <name>`. Hoy ambos
  devuelven 405/404 ⇒ mostrados como §5 SIN fingir éxito (ni banner de
  "eliminado"). La zona destructiva, textos y estados (400 "La confirmación
  no coincide" dentro del modal sin cerrarlo — cuando C10 exista) quedan
  listos.

## DOMINIO `backend/401-charset` (D1 — ÚNICO alcance backend)

- **R8.45** — `RestAuthenticationEntryPoint` (panel): en su método de escritura
  del 401, DEBE fijar `response.setCharacterEncoding("UTF-8")` ANTES de
  `getWriter()` (la llamada actual escribe `getWriter()` sin encoding ⇒
  Tomcat ISO-8859-1, la `ó` de "Autenticación requerida" = byte `0xF3`).
  Resultado contractual: `Content-Type: application/json;charset=UTF-8` y el
  cuerpo byte-a-byte UTF-8 (`ó` = `0xC3 0xB3`). Cuerpo JSON igual:
  `{"code":"invalid_token","message":"Autenticación requerida"}`.
- **R8.46** — SIMETRÍA: `IngestAuthenticationEntryPoint` (ingesta, líneas 72–74,
  mismo patrón) recibe la MISMA corrección de una línea (el mismo bug en un
  duplicado no debe quedar con deuda conocida). Sin otra diferencia de
  comportamiento.
- **R8.47** — ALCANCE backend EXACTO (D1): solo esas 2 clases + su test
  nuevo. NADA más del backend se toca: `SecurityConfig`, `CorsConfig`,
  `auth/**`, `streaming/**` (el handshake 401/403 pre-upgrade escribe cuerpo
  VACÍO — no está afectado ni se toca), `query/**`, proyectos, keys, ingest,
  migraciones, config. La tolerancia del cliente (R8.14) se MANTIENE igual
  (la regla es heredada y NO se acopla a este fix).
- **R8.48** — TEST del charset: un test nuevo (patrón mvn de los entry points,
  p. ej. MvcTest/MockHttpServletRequestWriter o el test ya existente del 401
  ampliado) DEBE demostrar: (a) header `Content-Type` con `charset=UTF-8`; y
  (b) el byte/body UTF-8 del mensaje (la `ó` correcta, no `0xF3` ni
  `U+FFFD`). Con la suite completa verde.

## DOMINIO `frontend-build/nginx` (servido, deps, retiro, contrato visual)

- **R8.49** — `vite.config.ts` (Riesgo R-6): `base: "./"` → **`base: "/"`**
  (absoluta) y `@tailwindcss/vite` añadido a `plugins`. Con base relativa, los
  assets de rutas anidadas (`/logs/:id`) pedían `/logs/assets/…` que
  `try_files` servía con `index.html` (MIME roto). ÉSTE es el ÚNICO cambio de
  configuración de C1 que C8 hace conscientemente.
- **R8.50** — Deps pinnadas (D3, S8-10): `react-router-dom@7.18.4`,
  `tailwindcss@4.3.3`, `@tailwindcss/vite@4.3.3` y `vitest` (pin EXACTO de
  design) añadidas a `frontend/package.json` SIN rangos (`^`/`~`), con diff
  de `package-lock.json` revisado y anotado. NINGUNA otra dependencia (sin
  estado global, sin librerías de componentes, sin testing-library/jsdom —
  la lógica pura corre en node). Sin CDN de Tailwind en producción (el
  `cdn.tailwindcss.com` era solo de las maquetas estáticas).
- **R8.51** — Tokens visuales: `frontend/src/index.css` con
  `@import "tailwindcss"` + `@theme` a partir de la paleta de `docs/04_UIUX.md`
  §2 (citada por referencia: fondo stone-100 `#f5f5f4`, superficie blanco,
  acento teal-700 **`#0f766e`**, verde conexión `#16a34a`/`#15803d`,
  severidades TRACE gris `#a8a29e` / DEBUG `#f1f5f9`+`#64748b` / INFO
  `#e0f2fe`+`#075985` / WARN `#fef3c7`+`#92400e` / ERROR `#fee2e2`+`#991b1b`
  / FATAL sólido `#7f1d1d` + texto blanco + borde izq., entornos production
  oscura `#1c1917` / staging `#ede9fe`+`#5b21b6` / development
  `#f5f5f4`+`#57534e`, destructivo `#bef264`… botón `#b91c1c`/`#991b1b` con
  zona `#fef2f2`/borde `#fecaca`). Utilidades de componente locales para
  badges `lv-*` y píldoras `env-*`. Semánticas: teal=acciones ≠ verde=estado
  (V2), rojo SOLO irreversible en zona explicada (V7), tema claro único (V1).
  Monoespaciado para timestamps UTC, id, correlation_id, key, JSON y
  stacktraces (V8).
- **R8.52** — CONTRATO VISUAL 1:1 (Riesgo R-4): las 6 pantallas con maqueta
  (`/login` 01, `/logs` 02, `/logs/:id` 03, `/proyectos` 04, `/proyectos/:id`
  05, incl. estados alternos V9: vacío por filtros, desconectado con
  "Reconectando (intento 3)…", banner 400, 404, modales) replican la paleta,
  la banda "● En vivo", el FATAL invertido, la sentencia del modal 8b, el
  indicador SOLO en `/logs` y la revelación única de key. CUALQUIER desvío
  visual reabre el GATE → prohibido improvisar estilo. Las 2 rutas de flujo 2
  (`/proyectos/nuevo`, `/proyectos/:id/editar`) NO tienen maqueta: se
  implementan coherentes con el sistema visual (flujo 2 del AppFlow), fuera
  del GATE.
- **R8.53** — nginx C1 INTOCABLE: SPA fallback (`try_files $uri /index.html`),
  proxy `/api/v1/` SIN URI final (prefijo intacto), proxy WS `/ws/logs` con
  upgrade 1.1 + timeouts 3600 s. El frontend llama con rutas RELATIVAS
  (mismo origen, cero CORS). `FRONTEND_ORIGIN` sigue siendo el origen REAL de
  uso del panel (localhost:8080 en dev; si no casa: 403 pre-upgrade en WS y
  CORS roto en dev — nota de despliegue R-1 heredada, `.env` no se toca).
- **R8.54** — Retiro D2: `frontend/public/mockups/**` (2 HTML) y
  `frontend/public/ws-console.html` ELIMINADOS en este corte (lo sustituyen).
  Tras el build NO se sirven en `:8080/mockups/**` ni `/ws-console.html` (sin
  strings de maqueta huérfanas servidas). `docs/mockups/` (congelado,
  contrato visual) INTOCABLE. El placeholder "pila OK" de `App.tsx` también
  desaparece (reescrito).
- **R8.55** — Compilación (guardia): `npm run build` DEBE quedar verde
  (`tsc --noEmit` strict con tsconfig C1 intocado + `vite build`); es además
  la guardia del smoke vía el stage 1 del Dockerfile (que falla si TS no
  compila). `dist/` se regenera por build.

## DOMINIO `frontend-qa/tooling` (QA del corte)

- **R8.56** — Vitest SOLO lógica pura, SIN DOM (sin jsdom, sin
  testing-library): (a) `ws/dedup.ts` — dedup por `id` + inserción al comienzo
  con orden `received_at DESC, id DESC` (con micros) y orden del lote;
  (b) acumulación keyset (`usePagination`) — acumulado + fin con
  `next_cursor: null`; (c) comparador del modal 8b — literal con trim
  (espacios al inicio/final; coincidencia exacta); (d) mapeo filtros ⇄
  query string (`useLogFilters`). Verdes sin navegador.
- **R8.57** — Smoke canónico autocontenido `scripts/qa_c8_smoke.sh`
  (plantilla `qa_c2`–`qa_c7`, regla **Q13/E2.7**: `down -v` + `up -d --build`
  inicial — asume cero datos previos; el `--build` es además la guardia de
  compilación): bash/curl contra `BASE_URL=http://127.0.0.1:8080` (todo por
  el proxy nginx del único puerto), `COMPOSE_BIN` autodetectado, tmpdir
  `mktemp -d` + `chmod 700` + `trap EXIT`, CERO secretos impresos (JWT y key
  solo en ficheros del tmpdir — S16), helper `marca()` con veredictos
  "Qn: PASS|FAIL|BLOQUEADO" y exit 0/1/2. Casos mínimos (E8-servido: `/` y
  `/logs/123` devuelven `index.html` `text/html` — nunca 404 de servidor;
  E8-auth: login 200 orden literal `{token, username, expires_at}`, `GET /me`
  con/sin token, 401 sin parsear; E8-charset: `charset=UTF-8` + `ó` correcta
  en el cuerpo del 401; E8-API: CRUD proyectos + 409, regenerate 201 orden
  `{key, prefix, created_at}`, revoke 204, `GET /logs` keyset, detalle 11
  campos, 400 con `code`/`field`; E8-C10-negativo: `DELETE /panel/logs/{id}`
  y `DELETE /panel/projects/{id}` → 405/404 tal cual (la UI sobrevive,
  R-3), `?include=summary` aceptado sin `summary`; E8-WS: handshake con
  token 101, frame batch byte-exacto §3.5 tras ingest 202, `set_filters` 6
  claves aceptado, 401/403 pre-upgrade con cuerpo vacío; E8-visual:
  renders con navegador BLOQUEADOS como Q23 con instrucción manual para el
  cierre).
- **R8.58** — SIN E2E de navegador (Q23/C3: BLOQUEADO en este entorno): no se
  introducen Playwright/Cypress ni drivers. La comprobación visual de las
  pantallas contra las maquetas queda como verificación MANUAL del usuario
  (momento natural: demo C9), anotada en el qa-report.
- **R8.59** — Regresión (cierre del corte): re-corrida de `qa_c3` (endpoints
  panel que la UI consume) e `qa_c7` (handshake WS) en verde + `mvn test`
  completo (174/174 → 174 + tests D1); `qa_c2` MAY re-correrse si el patrón
  del corte lo pide (patrón C6/C7). Diff de lockfile revisado en verify.

## 2. Escenarios de aceptación

ID ESTABLES E8.n. Cada escenario es derivable a test automatizado (Vitest /
smoke bash via curl / mvn) salvo los marcados MANUAL (Q23 — verificación
visual humana). Formato GIVEN/WHEN/THEN.

### 2.1 `frontend-app/routing`

- **E8.1** `nueveRutasRegistradas1a1` — GIVEN sesión válida (login 200),
  WHEN se navega secuencialmente a `/login`→redirect, `/`→`/logs`, `/logs`,
  `/logs/:id`, `/proyectos`, `/proyectos/nuevo`, `/proyectos/:id`,
  `/proyectos/:id/editar` y a una ruta inexistente, THEN cada una renderiza
  su pantalla (sin 404 de servidor — solo el `*` del cliente pinta 404),
  `/` NO renderiza pantalla propia y aterriza en `/logs` (AppFlow §1 1:1).
  Verificación: Vitest de tabla de rutas + (MANUAL) navegación.
- **E8.2** `guardSinTokenRedirigeConDestino` — GIVEN sin JWT (storage limpio),
  WHEN se abre `/proyectos/:id` (protegida), THEN el guard consulta
  `GET /api/v1/panel/me` (401) y la SPA aterriza en `/login` con la vista
  destino (`/proyectos/:id`) recordada; durante la resolución NO se renderiza
  la vista protegida (estado de carga). Verificación: Vitest del hook de
  guard (mock fetch) + smoke (navegación no automatizable — la parte REST sí:
  `/me` sin token → 401, cubierto en E8.18).
- **E8.3** `retornoTrasLoginConservaFiltros` — GIVEN guard activado desde
  `/logs?level=ERROR&text=timeout` (query en la URL), WHEN el usuario hace
  login OK, THEN aterriza de nuevo en `/logs?level=ERROR&text=timeout` con la
  query INTACTA y la lista con esos filtros aplicados (flujo 4/§5 "nunca
  perder estado"). Verificación: Vitest (redirección + serialización query).
- **E8.4** `loginConSesionRedirige` — GIVEN token vigente en storage,
  WHEN se visita `/login`, THEN `GET /me` 200 ⇒ redirige a `/` (→ `/logs`);
  el formulario no se muestra. GIVEN token en storage pero `/me` 401, THEN
  token descartado y el formulario se muestra normal. Verificación: Vitest.
- **E8.5** `rutaDesconocida404Pantalla` — GIVEN con sesión, WHEN ruta
  inexistente (`/noexiste`), THEN pantalla 404 del panel (nunca vacía ni
  redirect silencioso); GIVEN sin sesión, THEN redirige a `/login` con
  destino guardado. Verificación: Vitest. 
- **E8.6** `spaFallbackYAssetsAbsolutos` [S] — GIVEN pila levantada, WHEN
  `curl -s http://127.0.0.1:8080/` y `curl -s http://127.0.0.1:8080/logs/123`
  THEN ambos devuelven el `index.html` con `Content-Type: text/html` (SPA
  fallback + R8.49); WHEN se inspecciona el HTML servido, THEN las rutas de
  assets son ABSOLUTAS (`/assets/…`) y `curl -sI
  http://127.0.0.1:8080/logs/assets/…` NUNCA ocurre (ningún asset bajo rutas
  anidadas). Nunca 404 de servidor para rutas SPA. Verificación: smoke
  E8-servido + grep del HTML.

### 2.2 `frontend-app/auth-sesion`

- **E8.7** `loginFelizOrdenLiteral` [S] — GIVEN credenciales válidas del
  seed, WHEN `POST /api/v1/panel/auth/login` `{"username":…,"password":…}`,
  THEN 200 con claves `{token, username, expires_at}` (orden literal
  LoginResponse; `expires_at` ISO-8601 con `Z`); la UI guarda
  token+`expires_at` en sessionStorage (no localStorage, no cookies) y
  redirige a la vista destino. Verificación: smoke E8-auth (orden y status) +
  Vitest (persistencia D4).
- **E8.8** `loginCredencialesInvalidasInline` — GIVEN credenciales malas,
  WHEN submit con 401 `invalid_credentials`, THEN mensaje inline
  **"Usuario o contraseña incorrectos"** bajo el formulario, la vista NO se
  cierra, NO se revela qué campo falló y NO hay redirect (fuera del
  interceptor global — única excepción §5). Verificación: Vitest + MANUAL.
- **E8.9** `dobleSubmitBloqueado` — GIVEN formulario login, WHEN doble click
  rápido en Entrar, THEN durante la petición el botón muestra
  **"Enviando…"** y está deshabilitado; el cliente emite UN SOLO
  `POST /auth/login`. Verificación: Vitest (mock fetch contador).
- **E8.10** `restauroSesionAntesDeRenderizar` — GIVEN token vivo en
  sessionStorage, WHEN F5 en `/logs`, THEN la app llama `GET /me` ANTES de
  renderizar la vista protegida (sin flash de login), sesión restaurada con
  el nombre de usuario en la barra (flujo 1 paso 4). GIVEN storage bloqueado
  (try/catch), WHEN la pestaña funciona con token en memoria, THEN todas las
  llamadas van Bearer y el panel funciona (sin F5-persist). Verificación:
  Vitest (mock) + MANUAL.
- **E8.11** `caducidadCalienteInterceptor` — GIVEN usuario en `/logs` con
  token vencido (sin temporizador proactivo — S8-1), WHEN la siguiente
  llamada REST responde 401 (cualquiera: refetch, Cargar más, detalle), THEN
  el interceptor: descarta el token, CIERRA el WS abierto, redirige a
  `/login` con destino y muestra banner **"Tu sesión ha expirado (8 h):
  inicia sesión de nuevo"**; al re-loguear vuelve a `/logs` con los mismos
  filtros en la URL (E8.3). Verificación: Vitest (mock 401) + MANUAL.
- **E8.12** `logoutLocalSinRed` — GIVEN con sesión (y WS abierto en `/logs`),
  WHEN pulsar "Cerrar sesión", THEN el token se borra del storage (queda
  limpio), el WS se cierra y hay redirect a `/login`; NINGUNA llamada de red
  de logout existe (flujo 1 paso 5). Verificación: Vitest + MANUAL.
- **E8.13** `jwtSoloSessionStorage` — GIVEN cualquier momento con sesión,
  WHEN se inspecciona el navegador, THEN el token y `expires_at` están SOLO
  en sessionStorage del panel; `localStorage` y `document.cookie` NO contienen
  nada del JWT (D4, higiene C7 heredada). Verificación: Vitest de las claves
  del hook + MANUAL.

### 2.3 `frontend-app/api-client`

- **E8.14** `bearerEnTodoLoginExceptuado` [S] — GIVEN sesión, WHEN cualquier
  endpoint del panel se invoca desde la UI, THEN la petición lleva
  `Authorization: Bearer <jwt>`; WHEN `POST /auth/login` se envía, THEN NO
  lleva Bearer y su tratamiento (401 inline) no pasa por el interceptor.
  Verificación: Vitest (header inyectado) + smoke (login y llamadas 200 con
  token).
- **E8.15** `cuarentaYUnoNuncaParseaBody` — GIVEN un 401 del panel (sin
  token) con cuerpo `{"code":"invalid_token","message":"Autenticación
  requerida"}` (UTF-8 tras D1; y el caso ISO-8859-1 pre-fix cubierto por
  diseño), WHEN el cliente lo procesa, THEN su decisión usa SOLO
  `response.status` (logout+redirect) y el camino 401 NO ejecuta ni `.json()`
  ni `.text()` sobre el cuerpo (test: interceptor con fetch mock que
  CRASHEARÍA si se leyera el body — aserto de no-invocación). Ninguna `ó`
  mojibake (`U+FFFD`) alcanza la UI por el camino 401. Verificación: Vitest
  del client (mock) + smoke E8-auth.
- **E8.16** `parseoToleranteNoDoscientos` — GIVEN respuestas no-200 con cuerpo
  no-JSON: 405 que Spring responde en HTML, un 502 texto plano de nginx y un
  cuerpo vacío, WHEN las procesa el client, THEN se leen con `.text()` +
  `JSON.parse` en try/catch y el resultado usable es
  `"HTTP <status>"` (nunca un throw que rompa la UI); GIVEN un 404 JSON
  `{"code":"not_found",…}` THEN se parsea bien (code/message/field).
  Verificación: Vitest del client con mocks de Response.
- **E8.17** `vocabularioErroresALaUI` — GIVEN respuestas con el vocabulario
  §3.3/§3.4, WHEN llegan a la UI, THEN: 400 filtros → banner con `code` +
  campo culpable si hay `field` (filter_from_to_inverted SIN field → sin
  marca); 404 `not_found` → "No existe (quizás fue eliminado)"+Volver; 409
  `name_duplicate` → inline "Ya existe un proyecto con ese nombre"; 403
  genérico → banner (el panel jamás recibe `project_archived`); 500/502 →
  banner + Reintentar; red caída → banner "Sin conexión con el servidor".
  Literales SIN reescritura. Verificación: Vitest de mapeo + MANUAL.
- **E8.18** `reintentarTras500` — GIVEN un 502 del proxy o 500 del backend,
  WHEN el banner se muestra y el usuario pulsa **Reintentar**, THEN la MISMA
  llamada se re-emite y, si responde 200, el estado se recupera (sin recarga
  de página). Verificación: Vitest (mock secuencial 502→200). Smoke: 502 con
  backend parado, MAY.
- **E8.19** `sinCorsRutasRelativas` [S] — GIVEN la pila por el único puerto
  8080, WHEN la UI hace llamadas, THEN todas van a rutas relativas
  `/api/v1/**` de mismo origen (ninguna petición cross-origin; sin headers
  CORS preflight en el tráfico del panel). Verificación: smoke (curl replica
  las llamadas por 127.0.0.1:8080) + revisión del código.

### 2.4 `frontend-ui/logs`

- **E8.20** `primeraCarga50ReceivedDesc` [S] — GIVEN sesión y proyecto con
  logs (smoke siembra N logs por ingesta), WHEN se abre `/logs` sin filtros,
  THEN `GET /panel/logs` sin parámetros (limit defecto 50 — S8-7) + `GET
  /panel/projects` (S8-6, sin include=summary), primera página en orden
  `received_at DESC`, con esqueleto mientras; filas 8 columnas SIN
  exception/metadata. Verificación: smoke E8-API (shape `{items,
  next_cursor}`) + Vitest render-lógico + MANUAL visual.
- **E8.21** `filtrosEnQueryStringPersisten` — GIVEN filtros aplicados en
  `/logs`, WHEN se inspecciona la URL, THEN refleja los filtros activos
  (p. ej. `?project_id=7&environment=production&level=ERROR&from=…&to=…&text=timeout`);
  GIVEN F5 (recarga), THEN la vista re-hidrata EXACTAMENTE esos filtros y
  refetch con ellos; GIVEN la URL copiada a otra pestaña, THEN la misma vista.
  Verificación: Vitest de `useLogFilters` (mapeo⇄serialización bidireccional,
  ida y vuelta idempotente).
- **E8.22** `cambioFiltroReiniciaPaginaYsetFilters` — GIVEN vista viva con
  cursor avanzado, WHEN se cambia un filtro (o se pulsa Reiniciar), THEN se
  vuelve a la primera página (cursor reiniciado), se refetchea `GET /logs`
  con los nuevos filtros y el cliente REENVÍA `set_filters` con los 6 campos
  (nulls donde no aplica — S8-8); Reiniciar ⇒ query string vacía y `GET /logs`
  sin parámetros. Verificación: Vitest + MANUAL.
- **E8.23** `rangoDesdeHastaOffset` — GIVEN entrada `datetime-local`
  (p. ej. `2026-10-26T10:30`), WHEN se serializa al query/`set_filters`, THEN
  viaja ISO-8601 CON offset local (S8-9) y el backend lo normaliza; GIVEN
  `from == to`, THEN es legal (sin 400); GIVEN `from > to`, THEN backend
  responde 400 `filter_from_to_inverted` (sin field) → banner E8.26 con la
  lista previa conservada. Verificación: Vitest del mapeo (offset) + smoke
  (400 code/field del literal).
- **E8.24** `keysetCargarMasFinDeResultados` [S] — GIVEN ≥ limit+10 logs
  sembrados, WHEN pulsar "Cargar más", THEN se pide `cursor=<next_cursor>`
  y las filas se ACUMULAN sin recargar desde el principio (sin duplicados);
  WHEN la última página, THEN `next_cursor: null` ⇒ botón deshabilitado con
  "fin de resultados". Vacío legítimo: `{"items": [], "next_cursor": null}`
  (R6.12) ⇒ estado vacío correspondiente (no error). Verificación: smoke
  E8-API (keyset con psql) + Vitest `usePagination`.
- **E8.25** `keysetEstableAnteLlegadasEnVivo` [S] — GIVEN página 1+ acumulada
  y una nueva ingesta que llega por el stream, WHEN el frame entra, THEN la
  fila nueva se inserta AL COMIENZO (dedup por id), el cursor YA obtenido no
  se invalida, y un "Cargar más" posterior no duplica ni salta filas
  (estabilidad — criterio Done C6 convivido con el stream). Verificación:
  Vitest `dedup.ts` + smoke E8-WS con pgsel secuencia.
- **E8.26** `banner400CampoCulpable` — GIVEN filtros con rango invertido o
  valor inválido, WHEN backend responde 400, THEN banner rojo con el `code`
  literal (`filter_from_to_inverted` / `filter_invalid_value`) y el `message`;
  con `field` presente el campo culpable queda marcado; SIN la lista se
  borra (conserva lo pintado). Verificación: Vitest + MANUAL (smoke cubre el
  400 con code/field).
- **E8.27** `bandaEnVivoGrupoCoherente` [S] — GIVEN `/logs` abierto con WS
  conectado y `set_filters` enviado, WHEN una app ingesta un batch de 3
  (`POST /api/v1/ingest/batch` → 202 `{"accepted":3}`), THEN llega UN frame
  `batch` (5 claves `{type, batch_id, project_id, received_at, logs}` con
  `batch_id b_+uuidv4`) y los 3 logs aparecen como GRUPO al comienzo de la
  banda "● En vivo" en el orden del lote, SIN refresco (métrica 1 PRD).
  Verificación: smoke E8-WS (byte-exacto) + Vitest dedup + MANUAL banda.
- **E8.28** `dedupPorIdOrdenGlobal` — GIVEN el mismo id ya en el acumulado
  (por "Cargar más" o refetch de reconexión), WHEN el frame con ese id
  llega, THEN se IGNORA (sin duplicado visual); GIVEN un lote desordenado
  frente al global, THEN la inserción respeta `received_at DESC, id DESC`
  (comparación con micros) tras insertar el lote como grupo. Verificación:
  Vitest `dedup.ts` (unit puro).
- **E8.29** `indicadorSoloEnLogsEstados` — GIVEN `/logs` con WS abierto, THEN
  indicador **"● Conectado"** (verde) visible en la barra; GIVEN socket
  cerrado en caliente (backend reiniciado), THEN "○ Desconectado" + banner
  "Streaming inactivo: los datos en pantalla no se actualizan en vivo" y la
  lista sigue por REST; GIVEN en reconexión, THEN "Reconectando (intento N)…"
  con N creciente; GIVEN navegación a `/proyectos`, THEN el indicador
  DESAPARECE y el WS se cierra (S8-4; decisión C). Verificación: Vitest del
  hook (estados) + MANUAL (visual).
- **E8.30** `backoffYReconexionRefresco` [S] — GIVEN WS caído, WHEN el
  cliente reconecta, THEN los intentos siguen backoff exponencial con jitter
  1 s → máx 30 s (retraso observado creciente, acotado); al reconectar THEN
  indicador "● Conectado", banner retirado, `set_filters` REENVIADO y la
  primera página `GET /logs` REFRESCADA: lo entrado durante la caída aparece
  por ese refetch (sin replay del stream), la dedup evita duplicados.
  Verificación: Vitest (secuencia de timers del client) + smoke E8-WS
  (reconexión limpia — pareja E7.11).
- **E8.31** `ws401PreUpgradeRedirige` [S] — GIVEN token inválido/expirado al
  abrir `/logs`, WHEN el handshake es rechazado 401 pre-upgrade (cuerpo
  vacío), THEN indicador "○ Desconectado" + banner "Sesión expirada: vuelve a
  iniciar sesión" + redirect a `/login` (flujo 6a); GIVEN Origin del nav no
  casando `FRONTEND_ORIGIN` → 403 pre-upgrade ⇒ "○ Desconectado" + backoff
  (nota de despliegue R-1). Verificación: smoke E8-WS (401/403 con cuerpo
  vacío) + Vitest + MANUAL.
- **E8.32** `estadosDiferenciadosNoVaciosPorError` — GIVEN pestaña `/logs`:
  cargando → esqueleto; filtros que no casan nada → "No hay logs que cumplan
  los filtros" + sugerencia "Prueba a reiniciar filtros o amplía el rango
  temporal"; error 400/500 → banner con lista conservada; THEN NUNCA un 404
  ni un error se muestran como vacío ni viceversa (§4/§5). Verificación:
  Vitest (render lógico por estado) + MANUAL.
- **E8.33** `frameNoBatchIgnorado` — GIVEN un mensaje WS que no sea un frame
  batch válido (texto/JSON ajeno — defensiva; el servidor de C7 no manda
  otros), WHEN llega, THEN se ignora sin crashear ni descolgar la conexión
  (lista intacta). Verificación: Vitest del parser del client.

### 2.5 `frontend-ui/log-detail`

- **E8.34** `fichaDetalle11CamposBloquesCondicional` [S] — GIVEN un log con
  exception y metadata (y otro sin), WHEN se abre `/logs/:id`, THEN la ficha
  muestra los 11 campos del LogDto: cabecera (id mono, level badge, project
  mapeado, environment píldora, `timestamp` junto a `received_at` en UTC
  mono con Z, source, `correlation_id` mono copiable), `message`, y los
  bloques "Excepción y stacktrace" (`<pre>` mono + Copiar) y "Metadata" (JSON
  formateado `<pre>` mono + Copiar) SOLO con campo no-nulo — si ambos son
  nulos las zonas se OCULTAN. Verificación: smoke E8-API (11 campos del
  detalle) + MANUAL visual.
- **E8.35** `volverConservaFiltrosYPagina` — GIVEN `/logs` con filtros en URL
  y N páginas acumuladas, WHEN abrir un detalle y pulsar **Volver**, THEN
  `/logs` recupera la MISMA query y las MISMAS filas acumuladas (sin refetch
  desde el principio), convención §5. Verificación: Vitest + MANUAL.
- **E8.36** `cuarentaYcuatroDetalleNoVacío` [S] — GIVEN un id inexistente
  (p. ej. `/logs/99999999`), WHEN se abre, THEN `GET /panel/logs/{id}` → 404
  `{"code":"not_found"}` ⇒ mensaje **"Este registro no existe (o fue
  eliminado)"** + botón Volver a `/logs`; JAMÁS presentado como vacío ni
  esqueleto infinito. Verificación: smoke (404 literal) + Vitest + MANUAL.
- **E8.37** `modal8bSentenciaLiteralTrimBotonRojo` — GIVEN `/logs/:id` con
  id real (p. ej. 48211), WHEN abrir "Eliminar registro", THEN el modal
  muestra la sentencia EXACTA `DELETE /logs/48211 · header X-Confirm-Log-Id:
  48211` y el input exige escribirla; GIVEN texto con espacios al inicio/final
  (`  DELETE /logs/48211 · header X-Confirm-Log-Id: 48211  `), THEN el trim
  lo acepta (coincidencia); GIVEN texto incompleto o distinto, THEN el botón
  rojo permanece DESHABILADO (`bg-red-300`) y se habilita (`bg-red-700`) solo
  con la coincidencia. Verificación: Vitest del comparador (unit puro,
  E8.56c) + MANUAL visual.
- **E8.38** `deleteHoy405404SinFingirExito` [S] — GIVEN modal 8b confirmado
  (texto correcto), WHEN se envía `DELETE /api/v1/panel/logs/{id}` con
  `X-Confirm-Log-Id: <id>` (cabecera ya formada — S8-2), THEN el backend
  actual responde **405/404** (endpoint inexistente — smoke E8-C10-negativo)
  y la UI muestra el error como §5 (404 → "No existe (quizás fue eliminado)";
  405 → banner "HTTP 405" con parseo tolerante), SIN marcar éxito, SIN
  redirigir como "eliminado" y SIN corromper la lista. El usuario ve que NO
  funcionó. Verificación: smoke (405/404 reales) + Vitest del camino de error
  del modal + MANUAL.

### 2.6 `frontend-ui/projects`

- **E8.39** `listaProyectosMaqueta04` [S] — GIVEN proyectos activos y
  archivados, WHEN se abre `/proyectos`, THEN tarjetas con STATUS literal
  ("Activo"|"Archivado"), name, description, `api_key_prefix` + `has_key`,
  SIN bloque de resumen P1 (el `summary` no viaja en C8), archivados visibles
  según toggle; el orden y clases replican la maqueta 04 (contrato visual).
  Verificación: smoke E8-API (ProjectDto sin summary) + MANUAL.
- **E8.40** `estadoVacioLegitimoConCTA` — GIVEN primer uso (sin proyectos),
  WHEN se abre la lista, THEN "No hay proyectos todavía" + CTA **"Nuevo
  proyecto"** (≠ estado de error). Verificación: Vitest + MANUAL.
- **E8.41** `archivarDesarchivarConfirmacion` [S] — GIVEN ficha de proyecto
  activo, WHEN Archivar → confirmación ("Dejará de aceptar ingesta…NO se
  borra nada") → `PATCH {status:"ARCHIVED"}` 200, THEN etiqueta "Archivado" y
  botón **Desarchivar**; WHEN Desarchivar → `PATCH {status:"ACTIVE"}` 200,
 THEN vuelve a Activo; archivar NUNCA manda DELETE y jamás toca key ni logs.
  (Efecto derivado en ingesta 403 project_archived es de backend — sin UI.)
  Verificación: smoke E8-API (PATCH status 200 + ingest 403 derivado) +
  MANUAL.
- **E8.42** `fichaMaqueta05ConBloqueKey` [S] — GIVEN ficha de proyecto con
  key y otro sin key, WHEN se abre `/proyectos/:id`, THEN datos + bloque "API
  key de ingesta" con prefix mono (`plk_…`), `has_key` (sí/no), fecha de la
  key cuando existe, acciones habilitadas según estado; 404 ⇒ "Este proyecto
  no existe (o fue eliminado)" + Volver. Verificación: smoke + MANUAL.
- **E8.43** `regenerate201RevelacionUnica` [S] — GIVEN ficha de proyecto,
  WHEN Generar/Regenerar → confirmación de consecuencia → `POST
  .../api-key/regenerate`, THEN **201 `{"key","prefix","created_at"}`** (ese
  orden — ApiKeyResponse) abre el modal de REVELACIÓN ÚNICA con la key
  completa `plk_<43 chars Base64url>` + Copiar + aviso "No se volverá a
  mostrar; cópiala ahora"; GIVEN el modal cerrado (o F5), THEN la ficha solo
  muestra el nuevo `prefix` + `has_key: true` y la key COMPLETA no existe en
  ningún estado/render posterior (V8; la key regenerada invalida la anterior
  — explicado en la confirmación). Verificación: smoke (201 y orden) +
  Vitest (la key no se persiste fuera del modal) + MANUAL.
- **E8.44** `revocar204Has_keyFalse` [S] — GIVEN key activa, WHEN Revocar →
  confirmación ("las apps dejarán de poder ingestar…") → `DELETE
  .../api-key`, THEN 204 ⇒ ficha `has_key: false`, `prefix` retirado; ingesta
  con esa key pasa a 401 (backend). Verificación: smoke E8-API + MANUAL.
- **E8.45** `nuevoProyecto201Y409` [S] — GIVEN `/proyectos/nuevo`, WHEN crea
  con `name` "smoke-c8-e45" → 201 ProjectDto (`api_key_prefix` vacío,
  `has_key: false`) ⇒ redirect a `/proyectos/{id}` con aviso "Proyecto
  creado; genera su API key para que las apps puedan ingestar"; GIVEN un name
  ya existente, THEN 409 `name_duplicate` ⇒ inline "Ya existe un proyecto con
  ese nombre" con los datos conservados; GIVEN name vacío o >200 → 400 con
  errores POR CAMPO; botón "Creando…" deshabilitado durante llamada.
  Verificación: smoke E8-API (201/409/400) + Vitest formulario + MANUAL.
- **E8.46** `editarProyectoGuardadoY404` [S] — GIVEN ficha con datos, WHEN
  Editar → formulario precargado con `GET /projects/{id}` → modificar
  descripción → Guardar → `PATCH {description}` (solo campos modificados)
  200 ⇒ ficha con "Cambios guardados" y `updated_at` refrescado; GIVEN
  registro borrado en otra pestaña (futuro; hoy 404 imposible de GET vivo —
  caso simulado), THEN mensaje §5 y retorno a `/proyectos`; 409/400 idem
  R8.45. GIVEN intento de editar status aquí, THEN no existe tal control (va
  por flujo 7). Verificación: smoke (PATCH 200/400/409/404) + Vitest + MANUAL.
- **E8.47** `modales8a8cConstruidosNoOperativos` [S] — GIVEN ficha con zona
  destructiva, THEN existen los modales 8a ("Eliminar definitivamente",
  exige el NOMBRE EXACTO — V10) y 8c ("Purgar logs", explica que el proyecto
  y su key sobreviven, exige el nombre), con botón deshabilitado hasta
  coincidencia (trim); WHEN confirmados, THEN el `DELETE` correspondiente con
  la cabecera `X-Confirm-Name: <name>` formada responde hoy 405/404 ⇒
  mostrado como §5 SIN fingir éxito (R8.44/S8-2). Verificación: smoke
  (DELETEs 405/404) + Vitest comparadores + MANUAL.

### 2.7 `backend/401-charset` (D1)

- **E8.48** `cuarentaYUnoPanelUtf8` [S] — GIVEN pila reconstruida (fix D1
  aplicado), WHEN `curl -si http://127.0.0.1:8080/api/v1/panel/me` sin token,
  THEN `HTTP/1.1 401`, `Content-Type: application/json;charset=UTF-8` y el
  cuerpo contiene la `ó` de "Autenticación requerida" como bytes UTF-8
  (`0xC3 0xB3` — verificado por `xxd`/grep del byte; JAMÁS `0xF3` aislado);
  un `JSON.parse` del cuerpo decodifica sin `U+FFFD`. Verificación: smoke
  E8-charset + test mvn nuevo (R8.48).
- **E8.49** `simetriaIngesta401Utf8` [S] — GIVEN `POST /api/v1/ingest` sin
  `X-API-Key`, THEN 401 con `charset=UTF-8` y cuerpo UTF-8 válido (misma
  corrección — R8.46). Verificación: smoke E8-charset + test mvn.
- **E8.50** `suiteBackendIntactaMasD1` [L] — GIVEN el fix se aplica, WHEN
  `mvn test` completo, THEN 174/174 heredados VERDES + los tests nuevos de D1
  en verde; el diff del backend contiene SOLO las 2 clases del entry point +
  su test (R8.47); ninguna aserción previa editada. Verificación: mvn +
  review del diff.

### 2.8 `frontend-build/nginx`

- **E8.51** `baseAbsolutaAssets` — GIVEN build con `base: "/"`, WHEN se
  inspecciona `dist/index.html`, THEN los assets se referencian como
  `/assets/…` (absolutos) y ninguna petición `/logs/assets/…` ocurre en
  servido (E8.6); `@tailwindcss/vite` activo (el CSS de producción lleva las
  utilidades del tema compiladas, sin CDN). Verificación: smoke + grep dist.
- **E8.52** `retiroMockupsYConsola` [S] — GIVEN pila reconstruida tras el
  corte, WHEN `curl -sI http://127.0.0.1:8080/ws-console.html` y
  `.../mockups/04-proyectos.html`, THEN NO sirven la consola/maquetas viejas
  (D2; caen al SPA fallback ⇒ `text/html`, no la página con su JS); GIVEN el
  repo, THEN `docs/mockups/` NO cambia (intocable) y no quedan strings
  huérfanas de las copias retiradas. Verificación: smoke + `git status` del
  diff.
- **E8.53** `nginxIntocableProxyVerde` [S] — GIVEN nginx.conf/Dockerfile sin
  cambio, THEN `scripts/ws_echo_test.py --solo-handshake --token <JWT>` sigue
  verde (101 + Accept + timeouts 3600 s) y `qa_c3` verde (proxy REST intacto).
  Verificación: re-corridas de regresión (R8.59).
- **E8.54** `depsPinnadasLockfile` — GIVEN `package.json` editado, THEN las
  4 deps nuevas están SIN rango (`7.18.4`, `4.3.3`, `4.3.3`, vitest pin
  exacto), el diff de `package-lock.json` contiene SOLO esas dependencias (y
  sus transitivas) y ninguna otra dep se añadió (D3). Verificación: review
  del diff en apply/verify.
- **E8.55** `buildVerde` [S] — GIVEN el corte aplicado, WHEN
  `npm run build` (o el stage 1 del Dockerfile en el smoke), THEN `tsc
  --noEmit` strict + `vite build` terminan sin error; cualquier error de
  tipos rompe el build (guardia de compilación del corte). Verificación:
  smoke (--build) + local.

### 2.9 `frontend-qa/tooling`

- **E8.56** `vitestLogicaPuraVerde` — GIVEN los 4 módulos de lógica pura,
  WHEN `npx vitest run`, THEN verdes: dedup por id + orden global con micros;
  acumulación keyset + fin de resultados; comparador 8b con trim; mapeo
  filtros⇄query string (ida y vuelta). CERO dependencias de DOM (corre en
  node). Verificación: Vitest (R8.56).
- **E8.57** `smokeCanonicoAutocontenidoGreen` [S] — GIVEN repo en el estado
  del corte, WHEN `scripts/qa_c8_smoke.sh`, THEN: recomposición `down -v` +
  `up -d --build` (Q13 — autocontenido, sin datos previos), casos
  E8-servido/E8-auth/E8-charset/E8-API/E8-C10-negativo/E8-WS en PASS, la
  parte visual anotada BLOQUEADO con instrucción manual, SIN secretos en la
  salida (JWT y key solo en tmpdir), exit 0. Verificación: corrida del smoke.
- **E8.58** `sinE2E_navegado` — GIVEN el corte, THEN ningún test requiere
  navegador real (sin Playwright/Cypress/driver); toda comprobación visual
  queda BLOQUEADO+MANUAL (patrón Q23) anotada para el cierre/demo C9.
  Verificación: revisión de qa-plan/qa-report.
- **E8.59** `regresionVerdeAlCierre` [S] — GIVEN el corte cerrado, WHEN
  re-corridas, THEN `qa_c3` y `qa_c7` verdes + `mvn test` (174→174+N D1)
  verde; `qa_c2` MAY re-correr; diff de lockfile revisado (R8.59).
  Verificación: qa/verify.

## 3. Parejas [automatizable] ↔ [MANUAL visual] 

| Automático | MANUAL (Q23 — visual del usuario) |
|---|---|
| E8.6 (SPA fallback + assets) | E8.1/E8.5 (navegación), pantallas vs maquetas |
| E8.7/E8.18 (auth/REST de la UI vía curl) | E8.8/E8.9/E8.10 (formularios y estados) |
| E8.27/E8.30/E8.31 (WS por proxy, frame byte-exacto) | E8.29/E8.32 (indicador, banda "● En vivo", banners) |
| E8.48/E8.49 (charset UTF-8 del 401) | — |
| E8.38/E8.47 (DELETEs 405/404 reales) | E8.37 (modal V11 visual), fichas/flujos 2 |
| E8.51–E8.55 (servido, retiro, build) | R8.52 (contrato visual 1:1) |

Criterio: lo observable-en-servidor queda automatizado en [S]/Vitest/mvn; lo
que exige ojo humano (estética V1–V11, estados de pantalla, navegación real)
queda BLOQUEADO-MANUAL con instrucción para el demo C9 — precedentamente
tolerado por §7/Q23.

## 4. Contratos citados (fuente única de verdad — literales CITADOS, nunca redefinidos)

| Literal | Fuente (congelada) |
|---|---|
| 9 rutas SPA + guard con `GET /me` recordando destino + `/`→`/logs` + 404 del panel | AppFlow §1 |
| Login `POST /api/v1/panel/auth/login` `{username,password}` → 200 `{token, username, expires_at}` · 401 `invalid_credentials`; 401 inline "Usuario o contraseña incorrectos" SIN revelar campo (única excepción §5) | Schema §3.2/§3.3 + AppFlow flujo 1 |
| `GET /me` → 200 `{username, role: "ADMIN", expires_at}`; TTL 8 h; sin endpoint logout (logout local) | Schema §3.2/§3.3 + flujo 1 paso 5 |
| ProjectDto `{id, name, description, status, created_at, updated_at, api_key_prefix, has_key, summary?}` — `summary` ausente en C8; `api_key_prefix` null/`has_key: false` sin key; entrada crear `{name 1..200, description? ≤2000}`; PATCH `{name?, description?, status? "ACTIVE"|"ARCHIVED"}` — archivar va por PATCH NUNCA DELETE | Schema §3.3 + flujo 2/7 |
| regenerate → **201 `{key: "plk_<43 chars Base64url>", prefix, created_at}`** — key completa UNA única vez + "No se volverá a mostrar; cópiala ahora"; revoke `DELETE .../api-key` → 204 | Schema §3.3 + flujo 3 |
| `GET /logs` query `project_id?, environment?, level?, from?, to?, text?, cursor?, limit?` (defecto 50, máx 200) → 200 `{items, next_cursor?}` con null explícito; LogListItemDto **9 campos** SIN exception/metadata; nombre del proyecto NO viaja (mapea con `/projects`); cursor opaco keyset `(received_at, id)`; `next_cursor: null` = fin; nunca recarga desde el principio | Schema §3.3 + TRD §2.4 + flujo 4 |
| 400 filtros: `filter_from_to_inverted` (SIN field), `filter_invalid_value` (con field=), `filter_limit_invalid`, `filter_cursor_invalid`; 400 conservan la última lista válida | Schema §3.3 + R6.22/C6 + flujo 4 paso 6 |
| `GET /logs/{id}` → LogDto **11 campos** (los 9 + `exception` + `metadata`) · 404 `{"code":"not_found"}` | Schema §3.3 |
| Errores globales §5: 401 interceptor (descarta token + cierra WS + redirect con destino + banner expiración) salvo login inline; 404 "quizás fue eliminado"+Volver; 409 en línea; 500/502 + Reintentar; red caída doble señal; convenciones: nunca 404 como vacío, nunca perder filtros/página, key jamás fuera de su ventana | AppFlow §5 |
| WS handshake `/ws/logs?token=<JWT>` 401 pre-upgrade; `set_filters` 6 claves misma semántica que `GET /logs` (sin cursor/limit) reenviado a cada cambio y reconexión; frame batch UNO por lote `{type, batch_id, project_id, received_at, logs}` con batch_id `b_`+UUID v4 y LogDto completo 11 campos; heartbeat 30 s; sin replay; dedup CLIENTE por id insertando al comienzo `received_at DESC, id DESC`; backoff exponencial con jitter 1 s → máx 30 s | Schema §3.5 (verbatim) + AppFlow flujo 6 + TRD §2.8 |
| 403 `project_archived` SOLO ingesta (el panel NUNCA lo recibe); 413/429 sin estado de pantalla; `202 {"accepted":N}` sin UI | Schema §3.4 + flujo 10 |
| DELETE con `X-Confirm-*` (8a/8b/8c) y `include=summary`/`GET /projects/{id}/summary` → **C10** (UI de modales sí construida aquí; hoy 405/404) | Schema §3.2/§3.3 + ficha C8 del slicing + flujo 8 |
| Paleta y componentes V1–V11 (teal #0f766e acciones ≠ verde conexión; FATAL invertido; production oscura; banda "● En vivo"; tabla densa sin exception/metadata; monoespaciado dato de máquina; rojo solo irreversible; V11 sentencia literal con trim y botón `bg-red-300`→`bg-red-700`) | 04_UIUX §2/§4/§8 |
| Estados por pantalla §4 (esqueleto; vacío ≠ sin-resultados ≠ eliminado; "Enviando…"/"Creando…"/"Eliminando…"; revelación única; error dentro del modal sin cerrarlo) | AppFlow §4 |
| Maquetas = contrato visual (desvío reabre GATE); flujo 2 SIN maqueta | 04_UIUX §7 + ficha C8 |
| Fix D1: `setCharacterEncoding("UTF-8")` antes de `getWriter()` en ambos entry points + test; resto del backend INTACTO (handshake WS cuerpo vacío no afectado) | Gate D1 + C2 R-nuevo-4 + explore §D.2 |

## 5. Fuera de alcance (explícito — NO cubierto por C8)

- **C10**: los 3 `DELETE` (`X-Confirm-Name`, `X-Confirm-Log-Id`,
  `X-Confirm-Project-Id`) reales, `include=summary` + `GET
  /projects/{id}/summary` (resumen P1), borrados definitivos y purgas.
  Las UI existentes muestran 405/404 como §5, sin éxito fingido.
- **FUTURO**: token WS en primer frame `{"type":"auth",…}` (v1 = query param).
- **Sin E2E de navegador** (Q23); sin dark mode (V1); sin rate limiting 429
  (no existe); sin pantallas de ingesta (flujo 10) ni gestión de usuarios.
- **Backend**: INTACTO salvo la 1 línea de encoding en 2 entry points + su
  test (D1/R8.47). Sin migraciones, sin cambios de contrato API, sin env
  nuevas, sin tocar nginx/compose/`.env`/`docs/**`.
- **Herramientas**: ninguna dep además de las 4 aprobadas en D3 (S8-10).

## 6. Trazabilidad

| Requisito(s) | Escenarios | Verificado por |
|---|---|---|
| R8.1–R8.6 (routing/guard/404/layout) | E8.1–E8.6 | Vitest + smoke E8-servido + MANUAL |
| R8.7–R8.12 (sesión/auth UI, D4) | E8.7–E8.13 | Vitest + smoke E8-auth + MANUAL |
| R8.13–R8.19 (client API tolerante) | E8.14–E8.19 | Vitest del client + smoke + review |
| R8.20–R8.31 (logs: keyset, WS, dedup, banner 400) | E8.20–E8.33 | Vitest (dedup/paginador/mapeo) + smoke E8-API/E8-WS + MANUAL |
| R8.32–R8.38 (detalle + modal 8b V11) | E8.34–E8.38 | Vitest comparador + smoke 404/405 + MANUAL |
| R8.39–R8.44 (proyectos, key, flujo 2) | E8.39–E8.47 | smoke CRUD/regenerar/revocar + Vitest + MANUAL |
| R8.45–R8.48 (D1 charset) | E8.48–E8.50 | smoke E8-charset + mvn |
| R8.49–R8.55 (build/base/deps/retiro/nginx/visual) | E8.51–E8.55 + E8.52 | smoke + review de diff + MANUAL |
| R8.56–R8.59 (QA tooling) | E8.56–E8.59 | corridas de QA |

Criterios Done de la ficha C8 (slicing line 143), trazados: alcance completo
(E8.1–E8.47, E8.51–E8.55); "renderiza contra la API real con sus estados §4
y errores globales §5, 401→login con retorno" (E8.2/E8.3/E8.11/E8.17/
E8.32/E8.36); "modal 8b sentencia literal + trim + botón rojo hasta
coincidir; 405/404 mostrados sin fingir éxito" (E8.37/E8.38/E8.47);
"banda En vivo con dedup por id; banner 400 con campo culpable; estado
conectado/reconectando SOLO en /logs" (E8.25–E8.31); "revelación única de
key" (E8.43).

Mapa qa:

| Familia | IDs | Tipo |
|---|---|---|
| Vitest lógica pura (sin DOM) | E8.24/E8.25/E8.28/E8.37 (comparador), E8.21, E8.15/E8.16/E8.17 (client), E8.2–E8.5/E8.7–E8.13 (hooks con mocks) | unit |
| Smoke pila (bash/curl por :8080) | E8.6, E8.7, E8.14, E8.15, E8.18, E8.19, E8.20, E8.23–E8.27, E8.30, E8.31, E8.34, E8.36, E8.38, E8.39, E8.41–E8.47, E8.48–E8.53, E8.55, E8.57 | [S] |
| mvn (regresión + D1) | E8.50 (+tests D1 de E8.48/E8.49) | [L] |
| MANUAL (Q23 — visual/navegación) | E8.1, E8.5, E8.8–E8.13 parciales, E8.22, E8.29/E8.32, E8.35, E8.37, E8.40, R8.52 | humano/demo C9 |
| NO automatizable de otro modo | E8.58 (sin E2E navegado — prohibido) | política |

Total: **59 escenarios — ~35 automatizables (Vitest + smoke + mvn) y ~24
con componente MANUAL visual (Q23)**. QP para qa-plan.