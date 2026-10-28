---
project: Project_Log
change: C8
artifact: explore
last_updated: 2026-10-26
status: complete
---

# Explore — C8: Frontend del panel según maquetas (React + TypeScript SPA)

> Exploración de código real (solo lectura). Fuentes citadas fichero a fichero.
> Nada de lo que sigue es inventado: cada sección referencia el fichero real
> donde se verificó. Hoy = 2026-10-26, base HEAD `5df6409` (C7 cerrado).

## A. Setup frontend existente (de C1) — NO reimponer tooling

### A.1 Estructura y tooling (`frontend/`)

Verificado en `frontend/package.json`:

- **dependencies**: `react ^19.3.1`, `react-dom ^19.3.1`. NADA más.
- **devDependencies**: `@types/react ^19.3.0`, `@types/react-dom ^19.3.0`,
  `@vitejs/plugin-react ^5.2.0`, `typescript ~5.9.3`, `vite ^7.3.7`.
- **scripts**: `dev` (vite), `build` (`tsc --noEmit && vite build`),
  `preview`. El build ya corre TypeScript estricto.
- **NO existe ningún test runner** en `package.json` NI en
  `frontend/package-lock.json` (grep `vitest|jest|playwright|cypress|@testing-library`
  → 0 coincidencias). Tampoco hay eslint configurado. Toda la QA de C1–C7
  fue smoke bash (`scripts/qa_c*.sh`) + `mvn test` — el repo no trae ninguna
  herramienta de test JS.
- `frontend/vite.config.ts`: `plugins: [react()]` y **`base: "./"`**. Ver
  Riesgo R-6 (base relativa vs rutas SPA anidadas).
- `frontend/tsconfig.json`: `strict: true`, `noUnusedLocals`,
  `noUnusedParameters`, `noFallthroughCasesInSwitch`, `verbatimModuleSyntax`,
  `jsx: react-jsx`, `moduleResolution: bundler`, `include: ["src"]`.
- **Código React real existente**: SOLO el placeholder. `frontend/src/main.tsx`
  monta `App` de `frontend/src/App.tsx`, que es literalmente la pantalla de
  "pila OK" del corte C1 (comentario propio: "PLACEHOLDER TEMPORAL del corte
  C1 (se sustituye por el panel real en C8). NO llama a nada por /api/v1").
  C8 es, por tanto, el PRIMER panel real de la SPA: no hay librería de router,
  ni cliente HTTP, ni estado de sesión previo que preservar.
- `frontend/index.html`: título `Project_Log`, `<div id="root">`.
- `frontend/dist/`: build del placeholder C1 + `mockups/04 y 05` copiadas
  (se regenera con el build de C8).

### A.2 Servido por nginx (`frontend/nginx.conf`, `frontend/Dockerfile`, `compose.yaml`)

- Dockerfile multi-stage confirmado: stage 1 `node:22-alpine` con `npm ci`
  determinista + `npm run build`; stage 2 `nginx:stable-alpine` copiando
  `dist/` a `/usr/share/nginx/html`. Sin TLS, sin HEALTHCHECK en el
  Dockerfile (los healthchecks viven en compose, DD-5).
- `nginx.conf` (5 puntos literales de TRD §6.2/§9, R9):
  1. SPA fallback: `location / { try_files $uri /index.html; }` — el refresh
     de navegador en cualquier ruta SPA nunca da 404 de servidor.
  2. `location /api/v1/ { proxy_pass http://backend:8080; }` SIN URI final:
     el prefijo `/api/v1/` llega INTACTO al backend. **Rutas relativas OK**:
     el frontend puede llamar a `/api/v1/...` con mismo origen, cero CORS.
  3. `location /ws/logs` con `proxy_http_version 1.1`, headers de upgrade
     (`Upgrade $http_upgrade`, `Connection "upgrade"`) y
     `proxy_read_timeout 3600s` / `proxy_send_timeout 3600s` (E5/E7).
- `compose.yaml`: el frontend es el ÚNICO puerto publicado (`8080:80`,
  `${FRONTEND_BIND:-0.0.0.0}`), `depends_on backend healthy`; sin secretos
  en el fichero, todo interpola de `.env` (chmod 600, fuera de git).
  `FRONTEND_ORIGIN` viaja al backend (CORS REST de dev en
  `backend/.../config/CorsConfig.java` y Origin EXACTA del WS en
  `backend/.../config/WebSocketConfig.java`).
- `.env.example` línea 17: `FRONTEND_ORIGIN=http://<ip-vm>:8080` — lección
  heredada: debe ser el origen REAL desde el que se usa el panel
  (localhost:8080 en dev); si no casa, el WS muere con 403 (ver Riesgo R-1).

### A.3 Lo que ya vive en `frontend/public/`

- **`ws-console.html`** (306 líneas, C7): consola temporal del stream —
  comentario propio: "no es parte del panel; **se reemplaza en C8**". Es la
  referencia práctica más directa del cliente WS: login con
  `fetch("/api/v1/panel/auth/login")` → token **solo en memoria** ("JAMÁS
  localStorage ni cookies", higiene C7), `new WebSocket((location.protocol
  === "https:" ? "wss" : "ws") + "://" + location.host +
  "/ws/logs?token=" + encodeURIComponent(token))`, listeners open/close
  (code)/error/message; cada texto recibido se parsea como frame batch
  (el servidor nunca envía otro JSON distinto de batch — R7.11/R7.19).
- **`public/mockups/04-proyectos.html` y `05-proyecto-detail.html`**: COPIAS
  adaptadas del puente C3/C4.5 (`docs/mockups/` queda congelado intocable).
  El wiring de 04 (líneas ~130–260) es el patrón de tolerancia ya acordado:
  helper `api()` que **ante 401 SOLO mira `response.status` y NUNCA parsea el
  body** ("sale ISO-8859-1 del entry point" — comentario literal); para el
  resto usa `r.text()` + `JSON.parse` en try/catch con fallback
  `"HTTP <status>"`. Login inline con `POST /api/v1/panel/auth/login` →
  `sessionStorage` (copias de puente; el panel real decide su propio
  almacenamiento — ver Riesgo/decisión R-8 sobre sessionStorage vs memoria).
  04 llama a `GET /api/v1/panel/projects?status=` (toggle
  Todos/Activos/Archivados); 05 pre-carga ficha por `?id=` en querystring.
- **Retiro pactado**: `.atl/sdd/C3/archive-report.md` (acción C8):
  "Maquetas React REALES + retiro de las copias `frontend/public/mockups/**`
  de `:8080/mockups/**`". El placeholder de `App.tsx` también se sustituye.

## B. Contrato visual y de rutas (docs congelados)

### B.1 `docs/03_APPFLOW.md` (v1.0, gate-ok: si)

- **§1 — 9 rutas SPA**: `/login` (única sin JWT; con sesión → redirige a `/`),
  `/` (redirige transparente a `/logs`, no se renderiza), `/logs` (dashboard
  con filtros combinables + keyset + streaming vivo + indicador),
  `/logs/:id` (detalle completo + borrado registro a registro),
  `/proyectos` (lista; crear; archivados visibles), `/proyectos/nuevo`
  (formulario `POST /projects`), `/proyectos/:id` (ficha: key, editar,
  archivar, resumen, eliminación), `/proyectos/:id/editar`
  (`PATCH {name?, description?}`), `*` (404 del panel; con sesión → pantalla
  404, sin sesión → `/login`). Rutas NO-SPA: `/api/v1/ingest*` y `/ws/logs`.
- Route guard (§1 y flujo 1): vista protegida sin JWT → el guard consulta
  `GET /me` → 401 → redirige a `/login` **recordando la vista destino** para
  volver tras el login. Restauro de sesión al recargar: `GET /me` antes de
  renderizar.
- **10 flujos** (§3): 1 login/logout local (sin endpoint logout; el JWT sigue
  válido hasta `expires_at`), 2 crear/editar proyecto, 3 generar/regenerar/
  revocar key con **revelación única** (201 `{key, prefix, created_at}`,
  aviso "No se volverá a mostrar; cópiala ahora"), 4 consulta/filtrado/
  paginación keyset (filtros combinables; **el estado de filtros vive en la
  query string de `/logs`** — F5 conserva contexto; 400 → banner con code +
  campo culpable + última lista válida conservada), 5 detalle (exception y
  metadata solo si no-null; Volver conserva filtros y página), 6 streaming
  (a: handshake → "● Conectado" / 401 → login / 502-fallo de red → "Sin
  conexión en vivo"; `set_filters` justo tras conectar y tras cada cambio y
  reconexión; b: frame batch por lote tras COMMIT, dedup por `id` insertando
  al comienzo respetando `received_at DESC, id DESC`; c: backoff 1 s → 30 s
  con jitter, "Reconectando (intento N)…", al reconectar reenvía
  `set_filters` y refresca la primera página de `GET /logs`), 7 archivar/
  desarchivar (confirmación; efecto 403 `project_archived` solo en ingesta),
  8a/8b/8c DELETE con doble confirmación, 9 resumen por proyecto
  (`include=summary`), 10 exclusiones.
- **§4 — estados por pantalla**: tabla completa (cargando = esqueleto;
  vacío ≠ "sin resultados por filtro" ≠ eliminado; `/login` con botón
  "Enviando…" anti-doble-submit; `/proyectos` con estado vacío legítimo + CTA
  "Nuevo proyecto"; revelación de key; modales 8a/8b/8c con error 400 dentro
  del modal sin cerrarlo, reintento directo).
- **§5 — errores globales**: 401 → interceptor global: descarta token, cierra
  WebSocket si abierto, redirige a `/login` con destino guardada y banner
  "Tu sesión ha expirado (8 h)". **Única excepción**: el propio
  `POST /auth/login` con 401 = mensaje inline "Usuario o contraseña
  incorrectos", sin revelar campo. 404 → "No existe (quizás fue eliminado)"+
  Volver; nunca 404 de servidor en SPA. 400 → (a) filtros: banner con el
  `code` + campo culpable, conserva lista; (b) confirmaciones: dentro del
  modal sin cerrar; (c) formularios: mensajes por campo, datos conservados.
  409 → en línea "Ya existe un proyecto con ese nombre". 500/502 → banner +
  Reintentar. Red caída → banner REST + indicador WS desconectado.
  Convención transversal: nunca traducir 404 como vacío; nunca perder el
  estado de filtros/página al ir al detalle y volver; nunca mostrar la key
  fuera de su ventana de revelación.
- **Fuera de alcance deliberado en C8** (`docs/07_SLICING_PLAN.md` §C8):
  `/proyectos/nuevo` y `/proyectos/:id/editar` **SIN maqueta** (decisión del
  GATE visual; se implementan desde AppFlow flujo 2); summary/flujos 9 y los
  tres DELETE-8 son **C10** (la UI del modal 8b sí se construye en C8: hasta
  que la API DELETE llegue, la petición devolverá 405/404 y la UI lo muestra
  como AppFlow §5).

### B.2 `docs/04_UIUX.md` (v1.0, gate-ok: si)

Paleta exacta (tokens literales; las maquetas los repiten en `:root`):

| Uso | Hex |
|---|---|
| Fondo página stone-100 | `#f5f5f4` |
| Panel/superficie blanco | `#ffffff` |
| Línea stone-200 | `#e7e5e4` |
| Texto principal stone-900 | `#1c1917` |
| Texto muted stone-500 | `#78716c` |
| Acento/CTA teal-700 (acciones) | `#0f766e` |
| Conexión en vivo green-600/700 (estado) | `#16a34a` / `#15803d` |
| Severidad TRACE | text `#a8a29e` sobre claro |
| DEBUG | bg `#f1f5f9` / text `#64748b` |
| INFO | bg `#e0f2fe` / text `#075985` |
| WARN | bg `#fef3c7` / text `#92400e` |
| ERROR | bg `#fee2e2` / text `#991b1b` |
| FATAL | sólido invertido bg `#7f1d1d` + texto blanco + borde izq. de fila |
| Entorno production | píldora OScura bg `#1c1917` / blanco (SIEMPRE) |
| staging | bg `#ede9fe` / text `#5b21b6` |
| development | bg `#f5f5f4` / text `#57534e` |
| Destructivo (solo zonas marcadas) | bg `#fef2f2`, borde `#fecaca`, botón `#b91c1c`/`#991b1b` |

Semánticas V2 (teal=acciones ≠ verde=estado), V4 (production siempre oscura),
V3 (jerarquía TRACE→FATAL por distancia al rojo, color+texto nunca solo
color), V7 (rojo solo irreversible en zona explicada). Tema claro único V1.
Tipografía: `system-ui` general + `ui-monospace/SFMono-Regular/"JetBrains
Mono"/Menlo` para timestamps UTC, id, correlation_id, key, JSON, stacktraces.
Escalas: título ~18px semibold, cuerpo 14px, badges 11–13px, notas 11px.
Componentes base (§4): botones (primario teal, secundario, ghost,
destructivo solo en zona marcada, deshabilitado rojo apagado `bg-red-300` en
modales hasta validar), input/select (default/error borde rojo + mensaje por
campo/disabled), badge severidad, píldora entorno, card de proyecto (STATUS +
resumen P1 destacando WARN/ERROR/FATAL + prefix + has_key), modal (estándar;
revelación única de key; borrado con exigencia de texto), banner (info/ámbar/
rojo con `code`/neutral de conexión, con Reintentar), **indicador de
conexión "● Conectado" — persistente SOLO en `/logs`** (V2/decisión), banda
"● En vivo" (borde discontinuo verde sobre la tabla, V5), tabla densa (sm +
py-2, mensajes truncados, overflow-x-auto; SINV exception/metadata, V6).
Layout: barra superior fija con marca+nav Logs/Proyectos+indicador(en /logs)+
usuario+Cerrar sesión; `/logs` grid de filtros 1→2→6 columnas; móvil: filtros
apilados, tabla con scroll horizontal controlado, sin hamburguesa;
`max-w-7xl` (dashboard) / `max-w-6xl` (fichas 03 y 05).
Accesibilidad (§6): contraste AA, doble vía de codificación, focus ring
nativo, áreas ≥32px, select-all para lo copiable.

**Decisiones V1–V11** (§8, registro): V1 tema claro único; V2 teal≠verde;
V3 badges severidad FATAL invertido; V4 production oscura; V5 banda "● En
vivo" separada; V6 tabla densa sin exception/metadata en lista; V7 rojo
reservado; V8 monoespaciado + key nunca fuera de su ventana; V9 estados
alternos representados en las maquetas (aprobados por el GATE — la app debe
renderizarlos); V10 borrado de proyecto = **escribir el nombre exacto**;
**V11 borrado de registro = escribir la sentencia literal completa**
(`DELETE /logs/48211 · header X-Confirm-Log-Id: 48211`) en un input,
**comparación literal con trim**, botón rojo deshabilitado hasta coincidir
(bg-red-300 → bg-red-700). Las maquetas aprobadas son **contrato visual**:
cambios visuales = nueva revisión con el usuario.

### B.3 Maquetas (`docs/mockups/` + copias `frontend/public/mockups/`)

- 6 navegables en `docs/mockups/` (congeladas): `index.html` (índice +
  muestrario de la paleta), `01-login.html` (tarjeta login + estado 401
  representado; nota "Sesión con JWT · expira a las 8 h"), `02-logs.html`
  (340 líneas: nav superior con "● Conectado", filtros en grid 6 col
  (proyecto/env/level/desde-hasta datetime-local/reiniciar/texto), banda
  "● En vivo" con `batch_id b_1a9f4c2e · 3 logs`, tabla densa 8 columnas con
  badges/píldoras/borde izquierdo por severidad, paginación "Cargar más" +
  variante deshabilitada "fin de resultados", y `<details>` con los 3
  alternos: vacío por filtros, socket desconectado con "Reconectando (intento
  3)…", banner 400 `filter_from_to_inverted · campo: "from"`),
  `03-log-detail.html` (dl de cabecera con los 8 campos, message, exception
  `<pre>` mono con Copiar, metadata JSON `<pre>` con Copiar, zona
  destructiva, modal 8b completo con la sentencia literal + trim + botón
  deshabilitado, y `<details>` de 404), `04-proyectos.html` (tarjetas con
  resumen P1, etiqueta "Activo/Archivado", estado con "ruido" FATAL visible),
  `05-proyecto-detail.html` (ficha + bloque API key + modal revelación única
  `POST /api/v1/ingest` visible como texto documental; borrados).
- Cómo navegan: entre sí por nav `mockup-nav` (01→02→03→04→05 + índice);
  links "Detalle" de tabla 02 → 03; fila de 04 → 05.
- **Qué endpoints invocan**: las maquetas de `docs/` (congeladas) son
  estáticas y NO invocan nada. Las copias de `frontend/public/` (puente C3/
  C4.5) sí: login + `GET /panel/projects(?status=)` / `GET /panel/projects/{id}`
  + PATCH/archivado + regenerate de key (ver A.3). Son la referencia de cómo
  se consume la API con tolerancia 401.
- Estilos: todas usan `<script src="https://cdn.tailwindcss.com">` + tokens
  `:root` propios (`--accent:#0f766e` etc.) y clases utilitarias de Tailwind
  (`max-w-7xl`, `lv lv-ERROR`, `env env-production`, `rounded-xl`, ...) —
  **las clases de las maquetas son el contrato visual**, no necesariamente
  el mecanismo CSS de la app real (ver Decisión D-visual en Approaches).

## C. Contrato de API/DTOs (`docs/05_BACKEND_SCHEMA.md` §3, confirmado en código)

Endpoints `/api/v1/panel/**` con Bearer JWT (salvo login). Fechas ISO-8601
UTC con `Z`. Error estándar `{"code", "message", "field"?}`.

| Endpoint | Confirmado en código | Shape |
|---|---|---|
| `POST /panel/auth/login` | `auth/LoginController.java`, `LoginResponse.java` | `{username, password}` → **200 `{token, username, expires_at}`** (orden literal del record LoginResponse; `expires_at` con `Z`) · 401 `invalid_credentials` |
| `GET /panel/me` | `auth/MeController.java`, `MeResponse.java` | 200 `{username, role:"ADMIN", expires_at}` · 401 |
| `GET /panel/projects` | `projects/controller/ProjectsController.java` (`@RequestMapping("/api/v1/panel/projects")`) | query `status?`, `include=summary?` → `[ProjectDto]`; **sin DELETE, sin `summary`, sin `/summary` en el código (C10)**; el javadoc de `ProjectDto` pinza "Sin summary de ninguna manera (es C10 — R3.9)" |
| `POST /panel/projects` | idem | `{name 1..200, description? ≤2000}` → 201 ProjectDto · 400 · 409 `name_duplicate` |
| `GET/PATCH /panel/projects/{id}` | idem (`@GetMapping/{id}`, `@PatchMapping/{id}`) | PATCH `{name?, description?, status? "ACTIVE"|"ARCHIVED"}` (archivado = PATCH, nunca DELETE) · 200/400/404/409 |
| `DELETE /panel/projects/{id}` | **NO existe (C10)**; `X-Confirm-Name` ya reservada en `CorsConfig.java` (setAllowedHeaders incluye `X-Confirm-*`) | hoy: 405/404 |
| `POST /panel/projects/{id}/api-key/regenerate` | `keys/ApiKeyController.java` `@PostMapping(...regenerate)` | → **201 `{key, prefix, created_at}`** (record `ApiKeyResponse` con ESE orden; key completa UNA única vez) · 401 · 404 |
| `DELETE /panel/projects/{id}/api-key` | `@DeleteMapping` | → 204 (revoca: hash/prefix/created_at a NULL) |
| `GET /panel/projects/{id}/summary` | **NO existe (C10)** | `{by_level, by_environment, total, last_log_at}` |
| `GET /panel/logs` | `query/controller/LogsController.java` `@GetMapping` | query `project_id?, environment?, level?, from?, to?, text?, cursor?, limit?` (defecto 50, máx 200) → 200 `{items, next_cursor?}` con **null explícito** (`LogsPaginaResponse`; estado vacío legítimo `{"items": [], "next_cursor": null}`, R6.12) · 400/401 |
| `GET /panel/logs/{id}` | `@GetMapping("/{id}")` | LogDto **11 campos** (los 9 del listado + `exception` + `metadata`) · 404 `not_found` |
| `DELETE /panel/logs/{id}`, `DELETE /panel/projects/{id}/logs` | **NO existen (C10)** | hoy: 405/404; modal 8b de C8 debe mostrar esto como §5 |
| `POST /ingest`, `POST /ingest/batch` | `ingest/IngestController.java` | `X-API-Key` → **202 `{"accepted": N}`**; SIN UI (flujo 10) |

- LogListItemDto (lista): 9 campos `{id, project_id, level, environment,
  timestamp, received_at, message, source, correlation_id}` — SIN
  exception/metadata (no se transportan). El nombre del proyecto no viaja en
  la fila: el panel mapea `project_id` con lo cargado de `GET /projects`.
- Cursor opaco keyset `(received_at, id)` Base64url; `next_cursor: null` =
  fin de resultados; nunca se recarga la tabla desde el principio.
- Códigos 400 del vocabulario de filtros: `filter_from_to_inverted` (SIN
  `field`), `filter_invalid_value` (project_id/environment/level/from/to,
  cada uno con su `field`), `filter_limit_invalid`, `filter_cursor_invalid`.
- 401 del panel: cuerpo `{"code": "invalid_token", "message": "Autenticación
  requerida"}` — ver deuda ISO-8859-1 (sección D).
- 403 `project_archived` / 401 `api_key_invalid` / 413 `payload_too_large`:
  SOLO ingesta, sin UI de panel (flujo 10).

**Contrato WS §3.5** (confirmado en `streaming/`): `GET /ws/logs?token=<JWT>`
(o `wss`); handshake con `JwtHandshakeInterceptor`: JWT+Origin validados
**pre-upgrade**, rechazo 401/403 con **cuerpo VACÍO** (`response.setStatusCode`
sin escribir body — comentario literal "NUNCA se escribe body"); Origin EXACTA
de `FRONTEND_ORIGIN` (nunca `"*"` — nota `.atl/notes/origin-handshake-
interceptor-default-spring-websocket.md`: el `OriginHandshakeInterceptor`
POR DEFECTO de spring-websocket 6.2.19 rechaza CUALQUIER Origin con lista
vacía). Frame servidor: **1 frame JSON por batch**, 5 claves
`{type, batch_id, project_id, received_at, logs[]}` con `batch_id = "b_"+UUID
v4` y logs con **LogDto 11 claves**. Cliente → servidor:
`{"type":"set_filters","filters":{project_id, environment, level, from, to,
text}}` — shape ESTRECHO, 6 claves, misma semántica que `GET /logs` (sin
limit/cursor), reenviado a cada cambio y tras cada reconexión. Heartbeat del
servidor cada 30 s; cierres 4013/1013 (backpressure; nota
`close-1013-vs-decorador-1011-bugfix.md` es de servidor, irrelevante para el
cliente, que solo debe tratar códigos de close) y latido con GC 1001. Sin
replay: dedup del cliente por `id`. Reconexión cliente: backoff exponencial
con jitter 1 s → máx 30 s. **Token en primer frame = FUTURO, fuera de C8**.

## D. Backend vivo comprobable (`src/main/java/com/projectlog/…`)

### D.1 FiltrosConsulta (parser extraído en C7)

`backend/src/main/java/com/projectlog/query/FiltrosConsulta.java` — función
pura, sin BD ni Spring. **Orden global de parsing 2..7 (primer-fallo-gana,
pinado):** check 2 `project_id` numérico → check 3 `environment` enum →
check 4 `level` enum → check 5 `from` ISO-8601 CON offset → check 6 `to`
idem → check 7 `from > to` (sobre Instants; `from == to` legal). Misma fuente
para `GET /logs` (checks 2–7) y para `set_filters` del stream (única fuente
ADR-7). `patronIlike`: `\` → `\\` PRIMERO, `%` → `\%`, `_` → `\_`, luego
envolver `%...%`; texto NUNCA null (ausente = filtro ausente), vacío legal →
`"%%"`, texto no trim. Excepción `FiltroInvalido` con `code/message/field`
(tabla R6.22; `filter_from_to_inverted` SIN field). Para C8 importa: los
mismos valores/errores pueden llegar por query string y por `set_filters`,
y la UI puede pre-validar o mostrar el `field` culpable que devuelve la API.

### D.2 Deuda: 401 en ISO-8859-1 (decidir C8 vs C9 con datos)

Código exacto: `backend/src/main/java/com/projectlog/config/
RestAuthenticationEntryPoint.java` (método `escribir401`, lines 62–67):

```java
response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
response.setContentType(MediaType.APPLICATION_JSON_VALUE);
objectMapper.writeValue(response.getWriter(),
        Map.of("code", CODE_INVALID_TOKEN, "message", MENSAJE));
```

`MENSAJE = "Autenticación requerida"` contiene una `ó`. Con `getWriter()`
sin `setCharacterEncoding(...)`, Tomcat fija el encoding por defecto
**ISO-8859-1**: la respuesta sale con
`Content-Type: application/json;charset=ISO-8859-1` y el cuerpo serializado
byte-a-byte en ISO-8859-1 (la `ó` = byte `0xF3`). Mismo patrón en
`ingest/IngestAuthenticationEntryPoint.java` (líneas 72–74; sin impacto de
panel). El `JwtHandshakeInterceptor` del WS (401/403 pre-upgrade) escribe
**cuerpo vacío** — no afectado.

- **Efecto sobre un cliente HTTP/JS (fetch)**: `Response.json()`
  DECODIFICA SIEMPRE como UTF-8 (independiente del charset del header) → el
  `0xF3` se vuelve `U+FFFD` (mojibake en `message`). `Response.text()`
  respeta el charset del header (`ISO-8859-1`) y decodificaría bien. Por
  eso el puente de C3 (`frontend/public/mockups/04-proyectos.html`) ante
  401 **no parsea el body para nada** (solo `r.status`) y para otros errores
  usa `r.text()` + `JSON.parse` tolerante con fallback `"HTTP <status>"`.
- **Notas heredadas (las cuatro fuentes)**:
  - `.atl/sdd/C2/archive-report.md` **R-nuevo-4** (WARNING): "el 401 del
    entry point sale `charset=ISO-8859-1` … el frontend de C8 DEBE ser
    tolerante (no parseo estricto del body en no-200) o el fix es de una
    línea (`setCharacterEncoding("UTF-8")`) en C8/C9".
  - `.atl/sdd/C3/archive-report.md` (acción C8): "tolerancia 401 ISO-8859-1
    en helpers fetch (fix UTF-8 del entry point en C8/C9 — N11; en C3 la
    no-parse funcionó)".
  - `.atl/sdd/C4/C5/C6/C7/archive-report.md`: la heredan vivas ("→ C8/C9").
  - `.atl/notes/`: no hay nota específica del 401 (las dos notas existentes
    son de WS: 1013-vs-1011 y OriginHandshakeInterceptor); la evidencia está
    en los archive-reports citados.
- **Lectura para decidir**: la tolerancia en el helper fetch es obligatoria
  en C8 pase lo que pase (nunca parsear estrictamente un no-200 con
  `.json()`). El fix del servidor es literalmente UNA línea
  (`response.setCharacterEncoding("UTF-8")` antes de `getWriter()`) + una
  aserción de test, y afecta directamente a la UI que C8 construye
  (los `message` de los 400/401 del panel se muestran en banners). Encajarlo
  en C8 (corte de frontend, con backend congelado solo para esa línea +
  mvn) o dejarlo a C9 es decisión del proposal/spec; el slicing plan lo deja
  abierto ("C8/C9") en C2/C4/C5/C6/C7.

### D.3 JWT y sesión

`auth/TokenService.java`: HS256, clave = bytes UTF-8 CRUDOS de `JWT_SECRET`
(sin Base64/HKDF); claims `{sub, role, iat, exp}` (sin `jti`, sin `iss`);
`exp = iat + TTL` con TTL `projectlog.security.jwt-ttl-hours` — **default 8
horas** (S13), `JWT_TTL_HOURS` por env en compose. `LoginResponse(token,
username, expires_at)` con `expires_at` ISO-8601 UTC con `Z`. Sin refresh y
sin logout → el logout de la UI es LOCAL (borrar token + cerrar WS +
redirect /login); el JWT sigue válido hasta `expires_at` (aceptado en v1).
Validación falla sin distinguir causa (S5/CD-4): un único 401
`invalid_token`. Para la UI: guardar token + expires_at, restaurar con
`GET /me`, expiración en caliente = 401 de cualquier llamada → interceptor
global.

## E. QA y patrones de cortes previos

### E.1 Patrón smoke canónico (`scripts/qa_c2_smoke.sh` y homólogos)

`scripts/` trae `qa_c2_smoke.sh` (27 casos), `qa_c3_smoke.sh`,
`qa_c3_qa_smoke.sh`, `qa_c4_smoke.sh`, `qa_c5_smoke.sh`, `qa_c6_smoke.sh`,
`qa_c7_smoke.sh` (`--solo-handshake`, `--token`) y `ws_echo_test.py`. La
plantilla común (`qa_c2`): `BASE_URL=http://127.0.0.1:8080` (TODO pasa por el
proxy nginx del único puerto); `TMPDIR_QA=$(mktemp -d ...)` con `chmod 700` +
`trap EXIT` que restaura `.env` y borra el tmpdir; **cero secretos impresos**
(JWT solo en ficheros del tmpdir, S16); `COMPOSE_BIN` autodetectado
(podman compose / podman-compose) con `compose_()` que acota `up` (620 s) y
`down` (120 s) con timeout y purga huérfanes; helper `marca()` acumula
veredictos y salen "Qn: PASS|FAIL|BLOQUEADO (detalle)" por ID al final;
exit 0 = todo PASS, 1 = algún FAIL, 2 = BLOQUEADO sin FAIL.
**Q13 (E2.7, BD-A): `compose down -v` + `up -d --build`** — borrar `pgdata`
y resiembra desde cero: **regla establecida: ningún smoke posterior asume
datos previos a esa resiembra; cada `qa_cN` es autocontenido** (C7 re-corrió
`qa_c2` con Q20, `qa_c6` y `qa_c3`; `qa_c7` añadió E7.1–E7.17). Precedente
crítico de QA con navegador: **Q23 de C3 quedó BLOQUEADO** ("navegador
BLOQUEADO sin driver"), tolerado por §7 con instrucción manual al usuario —
el E2E con navegador real NO ha funcionado en este entorno hasta ahora.

### E.2 Tooling de test posible para el frontend (basado en lo real)

- **Ya disponible hoy, sin tocar lockfile**: `npm run build` (que ejecuta
  `tsc --noEmit` con `strict`) = verificación de tipos completa; smokes bash
  al estilo `qa_cN` contra `http://127.0.0.1:8080` (páginas servidas, login,
  fetch del panel, WS) — es el patrón que ha funcionado 7 cortes.
- **Añadir Vitest (+ jsdom + @testing-library/react si se quiere DOM)**:
  devDependencies nuevas, cambio de `package-lock.json` — viable y barato
  para lógica PURA del frontend (dedup por `id`, acumulado keyset, comparador
  trim del modal 8b, mapeo filtros↔query-string, reducer de estado de WS).
- **Playwright/Cypress E2E**: NO está en el repo, exige instalar navegador
  en el entorno; el precedente Q23 (C3) lo marcó BLOQUEADO. Opción honesta
  pero con coste/riesgo de entorno; la demo de C9 es el momento natural
  para validar a mano.

## Approaches (bosquejo para proposal/spec)

1. **Panel React + Router + Tailwind compilado** (recomendado como base):
   añadir `react-router-dom` (rutas 1:1 con AppFlow §1) y `tailwindcss` +
   `@tailwindcss/vite` (v4, sin CDN en producción) como devDependency;
   reescribir `App.tsx` con layout (barra superior), guard por `GET /me`,
   las 6 vistas con maqueta + `nuevo`/`editar` desde flujo 2. Pros: contrato
   visual traducido casi 1:1 desde las clases de las maquetas; router
   estándar con query-state para `/logs`. Cons: 2 dependencias nuevas
   (lockfile). Effort: medio.
2. **CSS propio con tokens `:root`** (alternativa): replicar la paleta con
   variables CSS sin Tailwind. Pros: cero dependencias. Cons: las maquetas
   aprobadas (contrato visual) están escritas EN clases Tailwind — traducción
   manual propensa a desvío visual; el GATE aprobó ese aspecto. Effort: alto
   en fidelidad.
3. **Cliente API único + tolerancias heredadas**: un módulo `api.ts` (fetch
   con Bearer, 401 = solo status y logout global, `text()`+parse tolerante
   para el resto — patrón del puente C3) + módulo WS (patrón `ws-console.html`
   exacto: `encodeURIComponent`, wss/ws, listeners, `set_filters`, backoff
   1→30 s con jitter, dedup por `id`). Pros: encapsula la deuda ISO-8859-1 y
   el contrato §3.5 una sola vez. Effort: bajo-medio.
4. **QA**: `tsc` del build + `qa_c8_smoke.sh` (bash/curl canónico: pila
   sana → login → cada vista servida → endpoints del panel → WS) + Vitest
   para lógica pura (OPCIONAL: 2–3 módulos testables sin DOM). Sin E2E de
   navegador (precedente Q23). Effort: medio.
5. **Deuda 401**: tolerancia SIEMPRE en el helper; opcionalmente el fix de
   una línea `setCharacterEncoding("UTF-8")` en `RestAuthenticationEntryPoint`
   (+ `IngestAuthenticationEntryPoint` por simetría) con aserción en mvn —
   decidir en proposal si entra en C8 o se difiere a C9.

## Recommendation (borrador para el proposal)

Enfoque 1 + 3 + 4: React + `react-router-dom` + Tailwind compilado, cliente
API/WS central con las tolerancias heredadas, y QA tipo smoke + tsc (Vitest
solo si el spec pide tests unitarios de lógica pura). Resolver en proposal:
(a) fix 401 UTF-8 en C8 vs C9; (b) retiro de `frontend/public/mockups/**`
y `ws-console.html`; (c) `vite.config` `base: "./"` → `"../"`o `"/"` para
rutas anidadas (Riesgo R-6); (d) dependencias nuevas (router+Tailwind, y
Vitest si aplica).

## Risks

- **R-1 · Origin del WS exacta**: si el panel se sirve/usa desde un origen
  distinto de `FRONTEND_ORIGIN` (p. ej. IP de VM vs localhost), la conexión
  WS muere con 403 pre-upgrade, cuerpo vacío (nota
  `origin-handshake-interceptor-default-spring-websocket.md`). Afecta también
  al CORS REST de dev. Documentado en `.env.example` pero fácil de pisar.
- **R-2 · Deuda 401 ISO-8859-1**: cualquier `r.json()` estricto sobre no-200
  mojibakea (`U+FFFD`) el `message`. Tolerancia obligatoria; fix de 1 línea
  pendiente de decidir C8/C9.
- **R-3 · API incompleta en C8**: los 3 DELETE (`X-Confirm-*`) y el summary
  no existen (C10). La UI del modal 8b y de borrados debe sobrevivir a
  405/404 del backend actual y mostrarlos como AppFlow §5 (sin fingir éxito).
- **R-4 · Contrato visual = maquetas aprobadas**: cualquier desvío (colores,
  FATAL invertido, sentencia literal del modal, indicador SOLO en `/logs`,
  banda "● En vivo", revelación única de key) reabre el GATE. La banda
  "En vivo" y el modal V11 tienen literales exactos.
- **R-5 · WS en /logs SOLO**: el indicador de conexión es persistente SOLO
  en `/logs` (decisión del slicing C8); cerrar WS al salir de la vista o al
  logout (AppFlow §5: 401 cierra WS).
- **R-6 · `vite.config` `base: "./"` con rutas anidadas**: con SPA fallback y
  rutas tipo `/logs/:id`, el `<script src="./assets/…">` del `index.html`
  resuelve relativo a la ruta anidada → request `/logs/assets/…` que
  `try_files` sirve con `index.html` (MIME roto). Hay que usar base absoluta
  (`"/"`) o construir con hashing absoluto — es un cambio de config de C1
  que C8 debe hacer conscientemente.
- **R-7 · Estado de filtros en query string**: AppFlow lo exige (F5 conserva,
  vista compartible, retorno desde detalle intacto) — no es opcional.
- **R-8 · Almacenamiento del JWT**: el puente C3/C4.5 usó `sessionStorage`
  (con try/catch por storage bloqueado); `ws-console.html` (C7) fijó el
  estándar de higiene "solo en memoria". El spec debe decidir explícitamente.
- **R-9 · Sin E2E de navegador**: precedente Q23 BLOQUEADO; la demo visual
  final quedará para C9/manual.
- **R-10 · Lockfile**: añadir router/Tailwind/(Vitest) modifica
  `package-lock.json` — sin prohibición, pero es la primera dependencia nueva
  desde C1 y debe quedar en el spec con versiones pinadas.

## Ready for Proposal

Sí. La exploración cubre: tooling existente (no reimponer), contrato visual
V1–V11 con literales exactos, 9 rutas + 10 flujos + §4/§5, 17 endpoints con
DTOs confirmados en código (y los 4 que faltan: DELETE×3 + summary, C10),
contrato WS exacto, deuda 401 localizada a método y línea con sus 4 notas
heredadas, y el patrón de QA con Q13. El orquestador debe preguntar al
usuario: (1) ¿el fix del 401 UTF-8 entra en C8 o se difiere a C9? (2) ¿se
retiran en C8 las copias `frontend/public/mockups/**` y `ws-console.html`?
(3) ¿se aprueban las dependencias nuevas (react-router-dom, tailwindcss,
opcional Vitest y jsdom)? (4) ¿dónde vive el JWT del panel: sessionStorage o
memoria?