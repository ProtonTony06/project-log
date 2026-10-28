---
project: Project_Log
change: C8
artifact: design
last_updated: 2026-10-26
status: complete
---

# Design — C8 · Frontend del panel según maquetas (React + TypeScript SPA)

> Fase sdd-design del corte C8. Este documento **IMPLEMENTA
> arquitectónicamente** el spec (`spec.md`, status complete) — no lo
> renegocia. Fuentes normativas: spec C8 (R8.1–R8.59, cierres S8-1…S8-10,
> decisiones de gate D1–D4, escenarios E8.1–E8.59) + proposal C8 (status
> **approved** — GATE APROBADO TAL CUAL 2026-10-26) + código real verificado
> en HEAD `5df6409` (2026-10-26): `frontend/package.json` (deps =
> react/react-dom SOLO; sin runner de test; lockfile v3 con 119 paquetes),
> `frontend/vite.config.ts` (`base: "./"` presente — Riesgo R-6 real),
> `frontend/tsconfig.json` (strict + noUnusedLocals/Parameters, `types:
> ["vite/client"]`, include `src`),
> `frontend/src/main.tsx`/`App.tsx` (placeholder C1 reescrito),
> `frontend/nginx.conf` (SPA fallback + proxy `/api/v1/` SIN URI final + WS
> upgrade), `frontend/Dockerfile` (stage 1 `npm ci` + `npm run build`),
> `compose.yaml` (único puerto 8080), `frontend/public/mockups/*.html` (copias
> C3/C4.5 con el patrón fetch tolerante) y `frontend/public/ws-console.html`
> (cliente WS normativo), `backend/.../config/RestAuthenticationEntryPoint.java`
> (`escribir401` L62–67: `setContentType` + `getWriter()` SIN
> `setCharacterEncoding` — deuda ISO-8859-1 confirmada),
> `backend/.../ingest/IngestAuthenticationEntryPoint.java` (mismo patrón,
> L71–76), tests mvn existentes (`PanelAuthMvcTest`, `IngestControllerMvcTest`),
> smokes `qa_c2`–`qa_c7` (convención real: veredictos `E<corte>.n` con
> `marca()` y bloque Q23 visual). Literales de DTO/WS **CITADOS** de
> `docs/05_BACKEND_SCHEMA.md` §3.2/§3.3/§3.5 y de `docs/04_UIUX.md` §2/§4/§8 —
> nunca redefinidos. Pins confirmados contra el registro npm HOY
> (2026-10-26): `react-router-dom@7.18.4`, `tailwindcss@4.3.3` +
> `@tailwindcss/vite@4.3.3` (peer `vite ^5.2.0 || ^6 || ^7 || ^8` — casa con
> `vite@7.3.7` del lockfile), `vitest` pin fijado en D-C8-11 (5.0.5, ver
> rationale).

## 0. Resumen del diseño

```
 navegador (SPA React 19 + TS 5.9 + Vite 7 + Tailwind v4, servida por
 nginx C1 en el ÚNICO puerto :8080)
 ─────────────────────────────────────────────────────────────────────
 main.tsx (EDIT: importa index.css + monta <BrowserRouter>)
   └─ App.tsx (REESCRITO: <AuthProvider> + <TopBar/> + <Routes>)
        ├─ useAuth (ÚNICA fuente de sesión: sessionStorage + fallback memoria,
        │    GET /me al montar, interceptor 401, logout local)
        ├─ <RequireAuth> guard: comprobando → no renderiza; anonimo →
        │    /login con destino; autenticado → children
        ├─ Rutas (9, AppFlow §1 1:1):
        │    /login → pages/Login            (fuera del guard)
        │    /      → <Navigate to="/logs"/> (sin pantalla propia)
        │    /logs  → pages/Logs ────────┬─ hooks/useLogFilters (URLSearchParams)
        │    /logs/:id → pages/LogDetalle│         ⇄ FiltrosLogs (grid maqueta 02)
        │    /proyectos → pages/Proyectos│         ⇄ hooks/useLogsStream ──► ws/client
        │    /proyectos/nuevo … /proyectos/:id/editar → flujo 2    │
        │    * → pages/NotFound (404 del panel)                     ▼
        │                                              dedup.ts (puro) + usePagination
        ▼
   api/client.ts  (ÚNICO fetch de la app: Bearer salvo login; 401 ⇒ SOLO
   │                status; resto no-200 ⇒ text()+JSON.parse con fallback)
   └─ api/panel.ts (una función por endpoint; usa types.ts)
        │  rutas RELATIVAS /api/v1/** — mismo origen, CERO CORS
        ▼
 nginx C1 INTOCABLE: try_files $uri /index.html · proxy_pass /api/v1/ sin
        URI final · /ws/logs upgrade 1.1 + read 3600 s
        ▼
 Spring Boot :8080 (backend INTACTO salvo D1: 2 entry points + test)
        ├─ REST panel: auth/me, projects CRUD, api-key regenerate/revoke
        ├─ query: GET /logs (keyset) · GET /logs/{id} (LogDto 11)
        ├─ WS /ws/logs (C7): set_filters 6 claves · ONE frame batch por lote
        └─ 401 REST: entry point (D1 = UTF-8 byte-exacto) — el camino 401 del
           client NUNCA lee el body (regla heredada, válida antes/después)
```

Regla de flechas (frontera de dependencias de módulos):

- `pages/` y `components/` → `hooks/` → `api/` , `ws/` , `lib` de sus propios
  hooks. JAMÁS al revés: `api/` y `ws/` no importan de `hooks/` ni de
  `components/` (un módulo de infraestructura sin React en su interior salvo
  tipos).
- `hooks/useAuth.ts` (contexto) es lo ÚNICO que `main.tsx`/`App.tsx`
  consumen para sesión; NINGÚN fichero lee `sessionStorage` por su cuenta
  fuera de `useAuth.ts` (R8.7).
- NINGÚN fichero de la app llama a `fetch` fuera de `api/client.ts` (R8.13)
  ni construye `new WebSocket` fuera de `ws/client.ts` (R8.27).

---

## 1. Mapa del árbol `frontend/src/`

La partición interna es del design (§0.3 del spec); los NOMBRES de fichero son
los contractuales del proposal §3 (32 ficheros src, dentro del presupuesto
30–34):

| Fichero | Estado | Papel (una línea) |
|---|---|---|
| `index.css` | NUEVO | `@import "tailwindcss"` + `@theme` semántico + clases componente `.lv-*`/`.env-*` 1:1 (D-C8-8) |
| `api/types.ts` | NUEVO | DTOs literales §3.2/§3.3 (D-C8-6; Interfaces §5) |
| `api/client.ts` | NUEVO | fetch único tolerante + registro del interceptor 401 (D-C8-3) |
| `api/panel.ts` | NUEVO | una función por endpoint; DELETEs con `X-Confirm-*` (S8-2) |
| `ws/client.ts` | NUEVO | clase `ClienteWsLogs`: handshake §3.5, set_filters, backoff con jitter (D-C8-5) |
| `ws/dedup.ts` | NUEVO | fusión pura del frame batch en el acumulado (D-C8-4) |
| `hooks/useAuth.ts` | NUEVO | contexto + storage D4 + `/me` + interceptor + logout (D-C8-2) |
| `hooks/useLogFilters.ts` | NUEVO | filtros ⇄ query string + serialization fechas con offset (S8-9) |
| `hooks/useLogsStream.ts` | NUEVO | SOLO en `/logs`: ciclo de vida WS + refresco de página 1 (S8-4) |
| `hooks/usePagination.ts` | NUEVO | acumulado keyset puro + `esFin` (E8.24) |
| `components/TopBar.tsx` | NUEVO | barra superior maqueta 04 (marca, nav, usuario, cerrar sesión) |
| `components/EstadoConexion.tsx` | NUEVO | "● Conectado"/"○ Desconectado"/"Reconectando (intento N)…" SOLO en `/logs` (V2) |
| `components/BandaEnVivo.tsx` | NUEVO | banda borde discontinuo verde (V5) sobre la tabla |
| `components/BadgeSeveridad.tsx` | NUEVO | badge V3, FATAL invertido (clase `.lv-FATAL` |
| `components/PildoraEntorno.tsx` | NUEVO | píldora V4, production oscura (`.env-*`) |
| `components/Banner.tsx` | NUEVO | banner info/ámbar/rojo/neutral con Reintentar opcional |
| `components/Modal.tsx` | NUEVO | esqueleto genérico + comparador `coincideLiteralConTrim` (D-C8-9) |
| `components/ModalBorrado8b.tsx` | NUEVO | sentencia literal V11 + botón `bg-red-300`→`bg-red-700` |
| `components/ModalRevelacionKey.tsx` | NUEVO | revelación única V8 (key + Copiar + aviso literal) |
| `components/TarjetaProyecto.tsx` | NUEVO | tarjeta maqueta 04 (STATUS, prefix, has_key, archivar) |
| `components/FiltrosLogs.tsx` | NUEVO | grid de filtros maqueta 02 (1→2→6 columnas) |
| `components/TablaLogs.tsx` | NUEVO | tabla densa 8 columnas SIN exception/metadata (V6) |
| `components/Esqueleto.tsx` | NUEVO | esqueletos de tabla y de tarjetas (estado §4) |
| `pages/Login.tsx` | NUEVO | maqueta 01; único camino sin Bearer; 401 inline |
| `pages/Logs.tsx` | NUEVO | maqueta 02 completa: filtros+keyset+en vivo+indicador |
| `pages/LogDetalle.tsx` | NUEVO | maqueta 03: 11 campos, bloques condicionales, modal 8b, Volber |
| `pages/Proyectos.tsx` | NUEVO | maqueta 04: tarjetas + toggle archivados + estado vacío |
| `pages/ProyectoDetalle.tsx` | NUEVO | maqueta 05: ficha + bloque key + modales 8a/8c |
| `pages/ProyectoNuevo.tsx` | NUEVO | flujo 2 (SIN maqueta): POST + 409 inline |
| `pages/ProyectoEditar.tsx` | NUEVO | flujo 2 (SIN maqueta): PATCH de modificados |
| `pages/NotFound.tsx` | NUEVO | 404 del panel con sesión |
| `*.test.ts` (6) | NUEVO | Vitest lógica pura (§6): `ws/dedup.test.ts`, `ws/client.test.ts`, `hooks/usePagination.test.ts`, `hooks/useLogFilters.test.ts`, `api/client.test.ts`, `components/Modal.test.ts` |

Nota sobre `hooks/useAuth.ts` (extensión `.ts`, no `.tsx`): el fichero
contractual es sin JSX → exporta `AuthContext`, `useAuth()`, las utilidades
de storage y la lógica (`AuthProvider` como componente JSX se define en
`App.tsx`, que se reescribe de todas formas y queda dentro de la frontera).
El provider sólo hace `<AuthContext.Provider value={…}>` con el valor
producido por la lógica de `useAuth.ts` — toda la regla de sesión sigue en
UN solo módulo.

## 2. Decisiones de arquitectura (cada una con alternativas y rationale)

### D-C8-1 · Estructura de carpetas proposal §3, sin librerías de componentes
**Decisión**: el árbol de §1 tal cual (pages/components/hooks/api/ws), 32
ficheros, sin carpeta extra.
**Alternativas**: (a) carpeta `features/` por dominio (logs/, projects/) —
descartada: 8 páginas pequeñas, duplicaría imports cruzados (TablaLogs la usa
Logs y la banda; Banner lo usan todas) y rompe los nombres contractuales del
proposal; (b) todo plano en `src/` — descartado: 32 ficheros planos es
ilegible.
**Rationale**: separación neta infraestructura (api/ws, sin React) ← lógica de
sesión/datos (hooks, React sin JSX salvo useAuth) ← vista (components/pages).
Vitest de lógica pura importa módulos sin DOM gracias a esta partición (E8.56).

### D-C8-2 · Estado global: contexto React propio, CERO librería de estado
**Decisión**: `AuthContext` (creado en `hooks/useAuth.ts`, proveído en
`App.tsx`). Único estado "global" de la app. El resto es local por página:
`/logs` guarda en su estado local el acumulado keyset + el mapa
`project_id→name` + el estado del stream; filtros en la query string (D-C8-7)
— recarga exacta garantizada por la URL, no por estado global.
**Alternativas**: Zustand/Jotai/Redux — descartadas de raíz: D3 prohíbe
"NINGUNA otra dep" y no hay problema que las necesite (1 admin, sesión única,
sin hidrataciones cruzadas); contexto React está en la stdlib.
**Rationale**: la session es la única pieza compartida por TODAS las vistas
(guard + TopBar + interceptor); su tamaño (token, username, expires_at,
target, flags de banner) cabe holgadamente en un contexto; las vistas se
recargan navegando, así que sus datos locales no necesitan sobrevivir entre
rutas salvo `/logs` al volver del detalle — resuelto vía acumulado mantenido
en `Logs.tsx` montado con `keepPrevious`… ver D-C8-7b.

### D-C8-3 · Cliente API central (`api/client.ts`) y sus tolerancias
**Decisiones** (norma normativa R8.13–R8.15, trasladadas a la firma de §5):

1. **401 SOLO status**: en el camino `status === 401` el código ejecuta
   `interceptor401()` y `return` INMEDIATAMENTE — una sola línea de decisión,
   sin `r.json()` ni `r.text()`. Test E8.15 lo demuestra con un mock que
   CRASHEARÍA si el body se leyera (aserto de no-invocación sobre el spy).
   La regla queda Igual ANTES y DESPUÉS del fix D1 (no se acopla al backend).
2. **Resto de no-200**: `await r.text()` + `JSON.parse` en `try/catch`; si el
   parse falla (405 de Spring en HTML, 502 de nginx en texto plano, cuerpo
   vacío) se usa `{ status, code: undefined, message: "HTTP <status>" }`.
   NUNCA `r.json()` sobre un estado no-200 (E8.16).
3. **Red caída**: `catch` de `fetch` → `ApiError` con `status: 0` → banner
   "Sin conexión con el servidor" (R8.17). La navegación entre vistas ya
   renderizadas no la bloquea (error es de la vista, no del router).
4. **Interceptor registrable**: `client.ts` no importea React; expone
   `registrarInterceptor401(cb: () => void)` y `App.tsx` lo registra una vez
   con el callback de `useAuth` (descarta token + redirige). El 401 de
   `POST /auth/login` NO pasa por aquí: `panel.ts` llama a
   `requestLogin<T>()` (variante sin Bearer y con `tratar401: false`). La
   idempotencia de R8.16 es trivial: el callback solo borra token+redirect,
   repetido es no-op.
5. **Higiene**: NINGÚN `console.log` con token (R8.12); el client no loguea
   absolutamente nada; error messages en pantalla vienen de `code/message`
   del body o del literal de fallback.
**Alternativas**: axios/interceptor de librería — descartado (dep nueva,
prohibida); envolver `fetch` en cada página — descartado: duplicaría las
tolerancias 10 veces y haría la regla del 401 no verificable en un único
punto.
**Rationale**: la regla del 401 es CONTRACTUAL y su verificación (E8.15) exige
un único punto de paso; la tolerancia de parseo es la que ya demostró
`mockups/04+05.html` (r.text()+JSON.parse try/catch: patrón literal copiado).

### D-C8-4 · Dedup e inserción (módulo puro `ws/dedup.ts`)
**Decisión**: función pura exportada:

```ts
export function fusionarEnVivo(
  acumulado: LogListItemDto[],
  lote: LogListItemDto[],            // logs del frame batch, EN ORDEN DEL LOTE
): LogListItemDto[]
```

Reglas (R8.25/E8.28, verbatin del Schema §3.5 "Deduplicación en el cliente"):
1. se descartan del lote los ids YA PRESENTES en `acumulado` (Set<number>
   construido 1 vez);
2. los NO presentes se fusionan con el acumulado reordenando TODO el
   resultado por el comparador global `comparadorRecibidoDesc`
   (`received_at` DESC — comparación de string ISO-8601 UTC con `Z`, estable
   porque el backend emite el mismo formato y los micros viajan en el string;
   empate → `id` DESC numérico).
   NOTA de justificación del reorden completo vs "insertar al comienzo":
   el spec pide "insertar AL COMIENZO … respetando el orden global"; una
   insersión literal al comienzo sería INCORRECTA cuando el lote contiene
   timestamps anteriores a la primera fila acumulada (p. ej. refetch de
   reconexión o ingesta retrasada de otro host); el sort total con
   comparador determinista es la única implementación que garantiza AMBAS
   literales (grupo ordenado del lote y orden global) y es idempotente. El
   orden del lote se respeta en el empate de `received_at` (los logs de un
   batch COMPARTEN `received_at` — regla 9 del Schema §4): para IDs del MISMO
   lote el desempate por `id` DESC NO altera el grupo (ids de un batch son
   consecutivos; su orden visual por id DESC es el orden canónico del SQL
   `ORDER BY received_at DESC, id DESC` — igual criterio que el listado).
3. NUNCA muta el array de entrada (React depende de identidad).
**Alternativas**: dedup con `Map<id, dto>` que reordena al render — descartado:
pierde la identidad de array y complica el "Cargar más"; dedup en el
componente — descartado: la lógica es la única de QA automática del stream
(E8.25/E8.28) y debe ser unitaria sin DOM.
**Tests** (`ws/dedup.test.ts`): duplicado ignorado; lote desordenado frente al
global; empate con micros (`2026-10-26T10:30:00.123456Z`); grupo intacto;
entradas no mutadas.

### D-C8-5 · Cliente WS (`ws/client.ts`) + backoff puro
**Decisiones**:
1. **Handshake literal** del patrón `ws-console.html` (normativo): `new
   WebSocket((location.protocol === "https:" ? "wss" : "ws") + "://" +
   location.host + "/ws/logs?token=" + encodeURIComponent(token))` — token
   SOLO en query (S8-5). NINGÚN `Authorization` header en WS (no existe tal
   API en el navegador).
2. **`set_filters` EXACTO** (S8-8): `JSON.stringify({ type: "set_filters",
   filters: { project_id, environment, level, from, to, text } })` — SIEMPRE
   6 claves presentes, `null` donde no aplica, `project_id` como `number`
   cuando hay valor (igual que la consola C7). Envío: al abrir `open`, tras
   CADA cambio de filtro de `/logs` y tras CADA reconexión exitosa.
3. **Frames entrantes**: `message` → `JSON.parse` en try/catch; sólo
   `parsedObj.type === "batch"` y shape de las 5 claves (`type, batch_id,
   project_id, received_at, logs` array) pasa al callback; cualquier otra
   cosa (no-JSON, JSON ajeno, shape rota) se IGNORA sin crashear
   (defensiva R8.27/E8.33 — el servidor C7 jamás manda otro JSON, pero el
   cliente no confía). Los logs del frame son LogDto 11 — la página
   les aplica `fusionarEnVivo` CON los 11 campos pero renderiza la tabla
   SOLO con los 9 del listado (V6: la columna no existe en la tabla; el
   campo extra no se toca).
4. **Backoff puro** exportado del mismo fichero:

```ts
export function retrasoReconexion(intento: number /*1-based*/,
                                  aleatorio: () => number = Math.random): number
```
   `base = Math.min(30_000, 1000 * 2**(intento - 1))`; `jitter` factor
   uniforme en `[0.8, 1.2)` → `retraso = Math.min(30_000, base * (0.8 +
   0.4 * aleatorio()))`. Resultado en ms, SIEMPRE en `[800 … 30_000]`.
   Rationale: "backoff exponencial con jitter 1 s → máx 30 s" literal §3.5;
   el jitter multiplicativo (±20 %) evita sincronización de reintentos y
   mantiene el rango observable acotado — test con `aleatorio` inyectado
   demuestra la secuencia (1 s → 2 s → 4 s … cap 30 s) y los límites
   (E8.30).
5. **Ciclo de vida** (S8-4): la clase NO se registra global; el HOOK
   `hooks/useLogsStream.ts` (usado SOLO en `pages/Logs.tsx`) crea la
   instancia en un `useEffect` de montaje y la cierra en el cleanup →
   al desmontar la vista (navegación), al logout y ante 401 (el redirect
   del interceptor desmonta la página; además el hook mira
   `auth.estado !== "autenticado"` como condición de cierre — doble seguro).
   NUNCA coexisten dos sockets: el effect guarda la instancia en el estado
   local del hook y el guard `if (socketRef.current) return` hace idemponte
   el efecto — también bajo StrictMode de dev, que invoca los efectos
   doble (mount→cleanup→mount) pero con el cleanup correcto eso produce
   exactamente una conexión viva y un socket cerrado; en producción React
   no doble-invoca.
6. **Detección del 401 pre-upgrade** (E8.31): el navegador NO expone el
   status HTTP de un upgrade fallido — un 401 pre-upgrade llega como
   `close` SIN `open` previo (código 1006 en Chrome/Firefox). Diseño: si el
   socket cierra ANTES del primer `open`, el client emite
   `alCierreInaugural()`; el hook lanza UNA verificación `me()`:
   - `me()` → 401 ⇒ interceptor global (redirect `/login` con banner de
     expiración, indicador queda ○) — camino E8.31a;
   - `me()` → 200 ⇒ era caída/Origin 403 (R-1): estado ○ + backoff normal
     (la conexión se reintenta con los mismos filtros) — camino E8.31b.
   Así un token expirado a medio uso no loopa el backoff contra 401.
**Alternativas**:
(a) token en primer frame `{"type":"auth"}` — PROHIBIDO en C8 (FUTURO, S8-5);
(b) EventSource/SockJS — no existe por servidor (S spring nativo, ADR-5);
(c) detectar 401 por código de close — no existe tal API (1006 es genérico);
(d) reconexión fija sin jitter — viola §3.5 literal; (e) un socket global en
App — viola S8-4 ("SOLO /logs") y deja sockets abiertos navegando.

### D-C8-6 · Filtros de `/logs` EN LA QUERY STRING (`useLogFilters.ts`)
**Decisión**: `useSearchParams` de react-router-dom v7 como ÚNICA fuente del
estado de filtros (R8.22/R-7). El hook mapea ambos sentidos con funciones
PURAS exportadas (testables sin DOM):

```ts
export interface FiltrosLogs {
  project_id: number | null; environment: Entorno | null;
  level: Nivel | null; from: string | null; to: string | null;
  text: string | null;
}
export function filtrosDesdeSearch(sp: URLSearchParams): FiltrosLogs;
export function searchDesdeFiltros(f: FiltrosLogs): string;   // sin nulls
export function toSetFilters(f: FiltrosLogs): SetFilters;     // 6 claves+nulls
export function datetimeLocalAIsoConOffset(v: string, zona?: Intl.ZoneOffsetHelper): string;
```
- Serialización a URL: SOLO claves con valor (`?level=ERROR&text=timeout`),
  en orden estable; `from`/`to` viajan ISO-8601 CON OFFSET local (S8-9:
  entrada `datetime-local` → `datetimeLocalAIsoConOffset` — construida con
  `getTimezoneOffset()` del navegador, p. ej. `2026-10-26T10:30:00+02:00`;
  el backend normaliza a UTC). El offset se deriva a partir del Date
  interpretado LOCAL del navegador (no se usa `toISOString()` porque
  destruiría el offset).
- Cambiar un filtro ⇒ `setSearchParams(nuevo)` ⇒ efecto en `Logs.tsx`:
  cursor reiniciado a `null` + refetch + `set_filters` reenviado (R8.22);
  **Reiniciar** ⇒ `setSearchParams({})` y `GET /logs` sin parámetros.
**Alternativas**: estado local + sync manual con history — descartado: es
exactamente el bug que R8.22 proscribe (F5/retorno pierden estado: R-7);
zustand + persist — dep prohibida y estado en memoria no sobrevive F5.
**Rationale**: `useSearchParams` es literal en el proposal §3; la
bidireccionalidad pura es la que E8.21 verifica (ida y vuelta idempotente).

### D-C8-7 · Keyset acumulado (`usePagination.ts`) y retorno del detalle
**Decisión**: funciones puras exportadas:

```ts
export interface Acumulado<T> { items: T[]; nextCursor: string | null; }
export function paginaInicial<T>(): Acumulado<T>;                    // vacío
export function acumular<T>(a: Acumulado<T>, pagina: { items: T[]; next_cursor: string | null }): Acumulado<T>;
export function esFinDeResultados<T>(a: Acumulado<T>): boolean;      // nextCursor === null tras ≥1 página
```
`Logs.tsx` guarda `Acumulado<LogListItemDto>` en su estado local (NO en el
router): al navegar a `/logs/:id` y VOLVER, el componente se remonta… —
**aquí está la decisión real de conservar el acumulado (E8.35/R8.33)**: el
listado NO se descarta al navegar; `Logs.tsx` se mantiene montado bajo
`/logs` y el detalle NO es su hijo. Opciones evaluadas:
(a) **ruta anidada** `<Route path="/logs" element={<Logs/>}><Route
path=":id" element={<LogDetalle/>}/></Route>` — `Logs.tsx` NUNCA se
desmonta al ir al detalle: acumulado y scroll intactos, refetch NULO al
volver (convención §5 "nunca perder el estado"). El detalle se renderiza
en `<Outlet/>` — **ELEGIDA**.
(b) ruta plana con estado global de logs — viola "sin librería de estado" y
duplicaría el acumulado.
(c) cachear la respuesta en sessionStorage — no: los logs cambian en vivo y
la higiene de storage no debe crecer.
**Rationale**: (a) es la única que garantiza `Volver` sin refetch y con
filtros/página intactos SIN estado global; el router v7 lo soporta nativo
(`Outlet`), y `*`/guard siguen intactos porque el layout de rutas es solo
un detalle de composición. La query string sigue siendo de `/logs` y
el detalle no la toca.

### D-C8-7b · Refresco por cambio de filtros vs acumulado
`Logs.tsx` separa DOS fuentes: `acumulado` (keyset, crece con "Cargar más" y
con frames en vivo) y `recargoEsqueleto` (bool): al cambiar filtros se setea
esqueleto + se sustituye el acumulado por la primera página; al "Cargar más"
o a un frame se fusiona SIN esqueleto (página/paneles estables). Al
reconectar el WS: refetch de la primera página con los filtros actuales
(R8.29) fusionándose por dedup (D-C8-4) — el cursor YA obtenido NO se
invalida (el cursor es opaco `(received_at, id)`; el filtro sigue siendo el
mismo; un "Cargar más" posterior continúa desde el ÚLTIMO cursor recibido,
que sigue siendo válido para el mismo orden — E8.25).

### D-C8-8 · Contrato visual: Tailwind v4 con paleta por DEFECTO + `@theme` semántico + clases componente 1:1
**Decisión** (R8.51/R8.52 — el mapa clase-de-maqueta→Tailwind):
1. **Las maquetas usan la paleta POR DEFECTO de Tailwind** — verificadas una
   a una contra `docs/04_UIUX.md` §2: `stone-100 #f5f5f4` ✓,
   `stone-200/500/600/900` ✓, `teal-700 #0f766e` ✓, `green-600 #16a34a` ✓
   (`green-700 #15803d` ✓), `slate-500 #64748b` ✓, `sky-800 #075985` ✓ con
   `sky-100 #e0f2fe` ✓, `amber-100 #fef3c7`/`amber-800 #92400e` ✓,
   `red-100 #fee2e2`/`red-800 #991b1b`/`red-900 #7f1d1d` ✓,
   `violet-100 #ede9fe`/`violet-800 #5b21b6` ✓, `red-300 #fca5a5` ✓,
   `red-700 #b91c1c` ✓. → **Las clases del HTML de las maquetas se copian
   1:1 y el `@theme` NO redefine ningún color de paleta** (redefinirlos sería
   la vía al desvío de R-4). Utilidades como `bg-teal-700`, `text-stone-500`,
   `border-stone-200` resuelven idénticas en v4 sin configuración.
2. **`@theme` SEMÁNTICO mínimo** en `index.css` — alias de intención, no de
   color nuevo: `--color-superficie: #ffffff`, `--color-acento: var del
   teal-700 ya existente`… Simplificado: el bloque `@theme` define SOLO los
   tokens de intención usados con nombre propio (`--font-mono` ya existe en
   v4; NO se toca) más los colores de severidad/entorno NO nombrados por
   Tailwind (p. ej. `--color-live: #16a34a` para el punto "●" del indicador y
   banda, si una utilidad con nombre semántico lo necesita). NINGÚN color
   distinto del §2 de 04_UIUX entra.
3. **Clases componente `.lv-DEBUG`…`.lv-FATAL` y `.env-development`……
   `.env-production`**: se copian VERBATIM del bloque `<style>` de la maqueta
   04 (mismos hex, radios, paddings) a `index.css` como clases CSS planas
   (v4 las acepta junto a las utilidades; ninguna dependencia de JS de
   configuración). `BadgeSeveridad`/`PildoraEntorno` sólo eligen la clase
   (`"lv-" + level`, `"env-" + environment`) y añaden SIEMPRE `color + texto`
   (V3). FATAL añade el borde izquierdo de fila a nivel de fila en
   `TablaLogs` (clase de la fila, no del badge).
4. **`.mono`** → clase componente en `index.css` mirroring ui-monospace del
   §3 (timestamps, id, correlation_id, key, JSON, stacktrace — V8).
5. **Sin CDN**: `cdn.tailwindcss.com` era sólo de las maquetas estáticas; el
   CSS de producción sale de `@tailwindcss/vite` compilado (E8.51).
**Alternativas**: reescribir tokens propios desde cero — reintroduce desvío
contra el GATE cerrado (R-4); CSS modules por componente — rompe el mapa 1:1
y multiplica ficheros fuera de la frontera.
**Rationale**: el GATE visual está cerrado sobre clases concretas; el camino
mínimo de fidelidad es NO traducir nada: copiar.

### D-C8-9 · Comparador de confirmaciones con trim — en `components/Modal.tsx`
**Decisión**: `components/Modal.tsx` exporta el esqueleto genérico y
`export function coincideLiteralConTrim(esperado: string, texto: string):
boolean` = `texto.trim() === esperado.trim()` (coincidencia LITERAL con trim;
V11/V10; E8.37). Lo usan el modal 8b (sentencia completa) y los 8a/8c
(nombre exacto). Los tests lo importan de ahí (`Modal.tsx` compila en Vitest
con plugin react — es un módulo sin llamadas a DOM al importarlo).
**Alternativa**: duplicar el comparador en cada componente — rompería la
única unidad de prueba del patrón; colocar en `lib/` — fichero fuera de la
lista cerrada de §0.3 (los nombres son contractuales).
**Rationale**: los TRES modales comparten el MISMO patrón interacción de
confirmación-texto (UIUX §4 modal de borrado con exigencia); un punto único
lo hace verificable (E8.56/E8.37) y evita tres implementaciones.

### D-C8-10 · Sentencias de los modales y cabeceras `X-Confirm-*` (S8-2/S8-3)
- **8b** (desde `/logs/:id`): sentencia MOSTRADA literal de la maqueta 03:
  `` `DELETE /logs/${id} · header X-Confirm-Log-Id: ${id}` `` — es el TEXTO
  VISUAL del V11, con el id real; la PETICIÓN que envía `panel.ts` es
  `DELETE /api/v1/panel/logs/{id}` con header `X-Confirm-Log-Id: <id>`
  (ruta panel real ≠ literal del texto del modal — igual que la maqueta
  muestra `DELETE /logs/48211` con el endpoint de panel; distinción cerrada
  en S8-2).
- **8a**: exige el nombre EXACTO del proyecto (comparador con trim), envía
  `DELETE /panel/projects/{id}` con `X-Confirm-Name: <name>`.
- **8c**: mismo patrón sobre `DELETE /panel/projects/{id}/logs`.
Hoy los tres reciben 405/404 → camino de error §5 sin fingir éxito
(R8.36/R8.44); C10 no tocará UI.

### D-C8-11 · Vitest: pin y configuración (S8-10)
**Decisión**: **`vitest@5.0.5`** (pin exacto, SIN rango).
**Evidencia del registro (2026-10-26)**: última estable publicada de la línea
5.x (5.0.5, 2026-10-15; 5 parches desde 5.0.0 del 2026-09-03); peer
`vite: ^6.4.0 || ^7.0.0 || ^8.0.0` — compatible con `vite@7.3.7` del
lockfile; engines `node: ^22.12.0 || ^24.0.0 || >=26.0.0` — compatible con
`node:22-alpine` del Dockerfile (22.x ≥ 22.12) y con el node local
22.23.1.
**Alternativa registrada**: `vitest@4.1.12` (misma fecha de parche, línea
madura, engines 20/22/24, peer vite 6/7/8). Descartada como pin: estar en la
línea MAYOR vigente evita la migración inmediata 4→5 y ambos soportan lo
mismo aquí; el coste/beneficio favorece 5.x con el alcance mínimo de este
corte (sólo lógica pura). Si en apply apareciera un bloqueo de 5.0.5, el
fallback documentado es exactamente `4.1.12` (cambiaría el diff del lockfile,
NADA más — CERO APIs de test usadas que no existan en ambas: `describe/it/
expect/vi`).
**Configuración mínima: CERO fichero de config de Vitest.** Los defaults de
5.x ya son exactamente lo pedido: `environment: "node"` (sin jsdom), include
`**/*.{test,spec}.?(c|m)[jt]s?(x)` (cubre `src/**/*.test.ts`), globals OFF
(los tests importan `describe/it/expect/vi` explícitamente — compatible con
`tsconfig` strict y `types: ["vite/client"]` sin añadir `vitest/globals`).
Vitest lee `vite.config.ts` (plugin react activo transforma los .test.ts
que importan `.tsx`); `base: "/"` no afecta a tests. NINGÚN cambio en
`tsconfig.json` (frontera §0.3: no está en la lista de editados) y NINGÚN
script `test` en `package.json` (§0.3 dice "solo deps D3"): la corrida es
`npx vitest run` desde `frontend/`.
**Alternativas**: jsdom+testing-library — PROHIBIDAS por D3/R8.56 (y las
comprobaciones de UI quedan MANUAL Q23); Jest — dep de build distinta
(ts-jest/babel) y sin integración con el pipeline de Vite; archivo
`vitest.config.ts` — sólo si el default include fallara en apply (queda
anotado como fallback, no como plan).

### D-C8-12 · Pin exacto final de las 4 deps (S8-10/E8.54)

| dep | pin | Verificado npm 2026-10-26 |
|---|---|---|
| `react-router-dom` | **7.18.4** | existe; `dependencies` de la v7: `react-router` interno |
| `tailwindcss` | **4.3.3** | existe; peer `vite ^5.2.0 || ^6 || ^7 || ^8` |
| `@tailwindcss/vite` | **4.3.3** | existe; mismo peer — casa con `vite@7.3.7` |
| `vitest` | **5.0.5** | última estable (D-C8-11) |

En `package.json` SIN `^`/`~` (cuatro líneas exactas). Se sitúan:
`react-router-dom` en `dependencies` (bundle en runtime);
`tailwindcss`, `@tailwindcss/vite`, `vitest` en `devDependencies`
(sólo build/testing). NINGUNA otra dep (E8.54: el diff del lockfile contiene
esas 4 + sus transitivas, NADA más).

### D-C8-13 · Fix de `vite.config.ts` (R-6/R8.49) — único cambio de config C1
```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";           // NUEVO (R8.49)

export default defineConfig({
  plugins: [react(), tailwindcss()],                    // plugin añadido
  base: "/",                                            // "./" → "/" (R-6)
});
```
**Por qué es necesario** (probado en el fallo real): con `base: "./"`, una
ruta anidada `/logs/123` servida por `try_files` carga `index.html` cuyo
`<script src="./assets/…">` resuelve a `/logs/assets/…` → nginx hace SPA
fallback con el HTML y el MIME rompe la carga. Con `base: "/"` los assets
salen absolutos (`/assets/…`) y el fallo desaparece (E8.6/E8.51). ÉSTA es la
ÚNICA edición consciente de config C1; `nginx.conf`, `Dockerfile`,
`compose.yaml` NO se tocan.

### D-C8-14 · D1 — diseño exacto del fix y de su test (backend)

> **DESVIACIÓN RATIFICADA ORQUESTADOR 2026-10-27 (opción A, anclada):** las 3
> aserciones exactas heredadas de `PanelAuthMvcTest` (:88/:98/:128,
> `content().contentType(MediaType.APPLICATION_JSON)` — igualdad estricta de
> MediaType con parámetros) son EXCLUYENTES con el header
> `application/json;charset=UTF-8` que `setCharacterEncoding("UTF-8")`
> produce: `MockHttpServletResponse` recompon el Content-Type siempre que el
> charset se fija explícitamente (verificado en ambas variantes de orden).
> Ratificado: pasar esas 3 líneas a
> `content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)` (intento
> conservado: cuerpo JSON, nunca HTML); E8.50 queda MATIZADO como "174
> heredadas verdes + 1 desviación anclada de 3 líneas de aserción". El resto
> del alcance D1 (2 entry points + EntryPointCharsetTest) SIN cambios.
**Fix** (la línea completa, en AMBOS entry points — R8.45/R8.46):

```java
import java.nio.charset.StandardCharsets;               // NUEVO import

private void escribir401(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());  // ← D1: ANTES de getWriter()
    objectMapper.writeValue(response.getWriter(), …);              // resto IDÉNTICO
}
```
- `setContentType("application/json")` queda IGUAL; con
  `setCharacterEncoding("UTF-8")` PREVIO a `getWriter()`, el contenedor
  (Tomcat) emite `Content-Type: application/json;charset=UTF-8` y serializa
  la `ó` como `0xC3 0xB3` (ISO-8859-1 la emitía como `0xF3`).
- Import añadido: `java.nio.charset.StandardCharsets` (alternativa literal
  `"UTF-8"` string: descartada — charset nombrado por constante es lo
  verificable por el compilador).
- ALCANCE (R8.47): SÓLO esas 2 clases + su test. El handshake WS de C7
  (401/403 pre-upgrade con cuerpo VACÍO) NO pasa por estos entry points y
  NO se toca. La tolerancia del client (D-C8-3) no cambia.

**Test nuevo** (UN fichero, ambos entry points — R8.48/E8.48/E8.49):

`backend/src/test/java/com/projectlog/config/EntryPointCharsetTest.java`
- Test unitario PURO sin Spring context: construye ambos entry points con
  un `ObjectMapper` REAL y llama directamente a su API pública
  `commence(null, mockResponse, null)` / `handle(null, mockResponse, null)`
  — ambos ignoran la request y la exception (verificado en el código
  fuente: sólo usan `response`).
- `HttpServletResponse` mockeado con `MockHttpServletResponse`
  (spring-test, ya en classpath de test — lo usa MockMvc).
- Aserciones byte-exactas por clase:
  (a) `content().getContentType()` (o `resp.getContentType()`) ES
  `"application/json;charset=UTF-8"`;
  (b) `resp.getContentAsByteArray()` contiene la secuencia
  `{ (byte) 0xC3, (byte) 0xB3 }` ("ó" UTF-8 de "Autenticación requerida" /
  mensaje ingest) y NO contiene `0xF3` aislado;
  (c) un `JSON.parse`/relectura con UTF-8 del body NO produce `U+FFFD`
  (`new String(bytes, UTF_8)` contains "ó").
- Patrón y paquete: en `config/` (el entry point del panel); importa la
  clase de `ingest/` (los tests ya importan entre paquetes —
  `IngestControllerMvcTest` importa las de `config/`). Dos clases de test en
  UN fichero con `@Nested` (`Panel401`, `Ingesta401`) mantienen el diff en
  1 fichero nuevo como dice E8.50 ("SOLO las 2 clases del entry point + su
  test" — singular).
- NO se edita ninguna aserción previa (E8.50: 174/174 se mantienen).

### D-C8-15 · Guard de rutas y restauración de sesión (`useAuth`/`RequireAuth`)
Estados de sesión: `"comprobando" | "anonimo" | "autenticado"`.
- Al montar el provider: lee token+`expires_at` de `sessionStorage`
  (claves `projectlog.panel.token` / `projectlog.panel.expires_at`; SIEMPRE
  try/catch ⇒ fallback a memoria de la pestaña — D4/R8.7). Si hay token:
  `GET /me` ANTES de resolver el guard (R8.2/E8.10 — `RequireAuth` muestra
  "Comprobando sesión…" y NO renderiza nada protegido mientras). `/me`
  200 → `autenticado` con `username`; 401 → token descartado + `anonimo`
  (el camino 401 es el del interceptor — igual regla, sin parsear body).
- `RequireAuth`: `comprobando` → estado de carga; `anonimo` → guarda
  `location.pathname + location.search` como VISTA DESTINO
  (`sessionStorage key projectlog.panel.destino`, try/catch + memoria) y
  `<Navigate to="/login"/>`; `autenticado` → children (R8.2/E8.2).
- `/login` con `autenticado` → `<Navigate to="/"/>` (R8.3/E8.4); tras login
  OK → guarda token+`expires_at` → `autenticado` → navega al DESTINO
  guardado (por defecto `/` → `/logs`) con su query intacta (R8.5/E8.3).
- Logout (R8.10): borra claves del storage, `anonimo`, cierra WS (vía
  desmontaje de `/logs` + chequeo de estado en el hook), `<Navigate
  to="/login">`; CERO red.
- Caducidad caliente (R8.11/S8-1): SIN temporizador; el 401 de cualquier
  REST/WS-handshake dispara el interceptor (banner "Tu sesión ha expirado
  (8 h): inicia sesión de nuevo" — el banner se muestra en `/login` vía
  parámetro de estado `?expirada=1`… **decisión fina**: el aviso se pasa
  como estado de navegación (`navigate("/login", { state: { motivo:
  "expirada" } })`) para NO ensuciar la query del destino (la query debe
  quedarse limpia para el retorno a filtros). `expires_at` guardado es
  INFORMATIVO (nota de la maqueta 01; JAMÁS decide sesión — S8-1).

### D-C8-16 · Mapeo `project_id → name` en `/logs` (S8-6)
`Logs.tsx` carga `GET /panel/projects` UNA vez al montar (sin
`include=summary`) y mantiene `Map<number, string>` local; `TablaLogs`
recibe el mapa y pinta `name` o el id CRUDO si no está (futuro DELETE;
nunca vacío ni error). Recambio: la lista de proyectos no re-fetchea al
cambiar filtros (sólo al montar y al reconectar se mantiene la misma).

### D-C8-17 · D2 — retiro de estáticos; qué queda en `public/`
Se ELIMINAN `frontend/public/mockups/04-proyectos.html`,
`frontend/public/mockups/05-proyecto-detail.html` y
`frontend/public/ws-console.html` (lo que los sustituye, D2/R8.54); el
directorio `mockups/` vacío desaparece solo (git no trackea directorios).
**`public/` queda VACÍO** (no hay favicon ni assets: `index.html` no
referencia ninguno; el title va en el HTML). El fallo cosmético conocido
(browsers piden `/favicon.ico` → SPA fallback devuelve `index.html`
`text/html`) se acepta — añadir un favicon sería un fichero FUERA de la
frontera. `docs/mockups/` (congelado, contrato visual) INTOCABLE — las
6 maquetas ahí siguen siendo la referencia de E8.52 (git status debe
mostrarlas sin cambios).

---

## 3. Data Flow (los 5 recorridos concretos)

### 3.1 Login → guard → vista destino
```
F5 en /logs?level=ERROR
  main.tsx → App: AuthProvider.monta → sessionStorage(try/catch) → token
  → RequireAuth: comprobando (sin render protegido) → GET /me (Bearer)
      ├─ 200 → autenticado(username) → Logs.tsx renderiza; FiltrosLogs
      │         hidrata filtros desde la query (useLogFilters);
      │         GET /logs?level=ERROR (+ GET /projects para nombres)
      │         + useLogsStream conecta WS + set_filters
      └─ 401 → interceptor: descarta token (regla SOLO-status) → anonimo
              → RequireAuth guarda destino /logs?level=ERROR → /login
Login submit → POST /auth/login {username,password} (requestLogin, sin
  Bearer, FUERA del interceptor)
      ├─ 200 {token, username, expires_at} → guarda (D4) → autenticado
      │     → <Navigate al destino> → /logs?level=ERROR (filtros INTACTOS)
      ├─ 401 → inline "Usuario o contraseña incorrectos" (única excepción)
      └─ red → banner "No se puede conectar con el servidor"
```

### 3.2 Consulta keyset con cursor
```
Cargar más → GET /api/v1/panel/logs?<filtros>&cursor=<next_cursor>
  → 200 { items[9 campos], next_cursor } → acumular(): items += page.items
  (sin esqueleto); esFin ⇒ next_cursor === null → botón "fin de resultados"
  deshabilitado
Cambio de filtro → setSearchParams → cursor=null + esqueleto + refetch
  página 1 + socket.set_filters(toSetFilters(filtros)) [6 claves]
400 de filtros → camión tolerante code/message/field → Banner rojo con
  `code` literal y campo culpable marcado; la última lista válida NO se toca
```

### 3.3 Frame batch → dedup → render
```
ingesta 202 (otra app) → broadcast C7 → frame texto
  ws/client: JSON.parse → type==="batch" && 5 claves → alFrame(frame)
  Logs.tsx: fusionarEnVivo(acumulado.items, frame.logs)
     ├─ ids ya presentes → ignorados (dedup, Cargar más/refetch no duplican)
     └─ nuevos → orden global received_at DESC, id DESC (micros) → setItems
  → banda "● En vivo" marca el grupo recién llegado (borde discontinuo verde)
  → render tabla 8 columnas (los 11 del frame se transportan, 9 se pintan)
```

### 3.4 set_filters y reconexión
```
cambio filtro / abrir /logs ⇒ set_filters (6 claves, nulls)
onclose (cualquier causa — 1013, 1001, red: SIN oráculo) → ○ Desconectado
  + banner "Streaming inactivo…" + programar retrasoReconexion(intento++)
  (jitter, 1 s → cap 30 s) → reintentos crecientes "Reconectando (intento N)…"
  close SIN open previo ⇒ alCierreInaugural → me():
     ├─ 401 → interceptor → /login (banner expiración)
     └─ 200 → backoff normal
onopen ⇒ ● Conectado + set_filters REENVIADO + refetch página 1
  (fusionarEnVivo evita duplicar; sin replay de lo perdido — §3.5)
desmontar /logs o logout o 401 ⇒ client.cerrar() (S8-4: un solo socket)
```

### 3.5 Errores → banners (mapa único R8.17)
| status | Camino | UI |
|---|---|---|
| 401 (REST) | interceptor SOLO-status → logout local + cerrar WS | redirect `/login` + destino + banner expiración |
| 401 (login inline) | fuera del interceptor | inline "Usuario o contraseña incorrectos" |
| 400 filtros | client tolerante (text+parse) | banner rojo `code`+`message`; `field` marca el input; lista conservada |
| 400 formulario | idem | error POR CAMPO en el form; datos conservados |
| 404 entidad | idem `not_found` | "No existe (quizás fue eliminado)" / "Este registro no existe (o fue eliminado)" + Volver |
| 409 | `name_duplicate` | en línea "Ya existe un proyecto con ese nombre" |
| 403 | genérico | banner "No tienes permiso para esta operación" (panel jamás recibe `project_archived`) |
| 405/404 DELETEs | idem | §5 sin éxito fingido (S8-2/C10) |
| 500/502 | idem | banner "Error del servidor; inténtalo de nuevo" + Reintentar (misma llamada re-emite, E8.18) |
| fetch sin respuesta | `status: 0` | banner "Sin conexión con el servidor" |

---

## 4. File Changes — tabla COMPLETA y cerrada (casar == frontera §0.3)

**CREATE (34 ficheros)** — 32 de `frontend/src/` (árbol de §1, contando
`index.css` y los 6 `*.test.ts` como parte del mismo bloque):

| # | Ruta exacta | Tipo |
|---|---|---|
| C1 | `frontend/src/index.css` | React/CSS: tokens Tailwind + clases .lv/.env/.mono |
| C2 | `frontend/src/api/types.ts` | DTOs + tipos FiltrosLogs/SetFilters/FrameBatch |
| C3 | `frontend/src/api/client.ts` | fetch único + interceptor registrable |
| C4 | `frontend/src/api/panel.ts` | funciones por endpoint + DELETEs cabeceras |
| C5 | `frontend/src/ws/dedup.ts` | `fusionarEnVivo` + comparador global |
| C6 | `frontend/src/ws/client.ts` | `ClienteWsLogs` + `retrasoReconexion` |
| C7 | `frontend/src/hooks/useAuth.ts` | contexto sesión (provider JSX en App.tsx) |
| C8 | `frontend/src/hooks/useLogFilters.ts` | filtros ⇄ query string + offset fechas |
| C9 | `frontend/src/hooks/useLogsStream.ts` | ciclo de vida WS de `/logs` |
| C10 | `frontend/src/hooks/usePagination.ts` | acumulado keyset puro |
| C11 | `frontend/src/components/TopBar.tsx` | barra superior |
| C12 | `frontend/src/components/EstadoConexion.tsx` | indicador ●/○/Reconectando |
| C13 | `frontend/src/components/BandaEnVivo.tsx` | banda V5 |
| C14 | `frontend/src/components/BadgeSeveridad.tsx` | badge V3 |
| C15 | `frontend/src/components/PildoraEntorno.tsx` | píldora V4 |
| C16 | `frontend/src/components/Banner.tsx` | banner + Reintentar |
| C17 | `frontend/src/components/Modal.tsx` | modal genérico + coincideLiteralConTrim |
| C18 | `frontend/src/components/ModalBorrado8b.tsx` | modal 8b V11 |
| C19 | `frontend/src/components/ModalRevelacionKey.tsx` | revelación única |
| C20 | `frontend/src/components/TarjetaProyecto.tsx` | tarjeta maqueta 04 |
| C21 | `frontend/src/components/FiltrosLogs.tsx` | grid filtros maqueta 02 |
| C22 | `frontend/src/components/TablaLogs.tsx` | tabla densa 8 col |
| C23 | `frontend/src/components/Esqueleto.tsx` | esqueletos §4 |
| C24 | `frontend/src/pages/Login.tsx` | maqueta 01 |
| C25 | `frontend/src/pages/Logs.tsx` | maqueta 02 |
| C26 | `frontend/src/pages/LogDetalle.tsx` | maqueta 03 |
| C27 | `frontend/src/pages/Proyectos.tsx` | maqueta 04 |
| C28 | `frontend/src/pages/ProyectoDetalle.tsx` | maqueta 05 (8a/8c) |
| C29 | `frontend/src/pages/ProyectoNuevo.tsx` | flujo 2 |
| C30 | `frontend/src/pages/ProyectoEditar.tsx` | flujo 2 |
| C31 | `frontend/src/pages/NotFound.tsx` | 404 del panel |
| C32 | `frontend/src/ws/dedup.test.ts` | Vitest dedup |
| C33 | `frontend/src/ws/client.test.ts` | Vitest backoff+set_filters |
| C34 | `frontend/src/hooks/usePagination.test.ts` | Vitest keyset |
| C35 | `frontend/src/hooks/useLogFilters.test.ts` | Vitest mapeo+offset |
| C36 | `frontend/src/api/client.test.ts` | Vitest tolerancias client |
| C37 | `frontend/src/components/Modal.test.ts` | Vitest comparador trim |
| C38 | `scripts/qa_c8_smoke.sh` | smoke canónico R8.57 |
| C39 | `backend/src/test/java/com/projectlog/config/EntryPointCharsetTest.java` | test D1 (R8.48) |

(NOTA: filas C32–C37 son los 6 `*.test.ts` — el bloque "CREATE" total es
39 ficheros: 32 src + 6 tests + smoke + test mvn.)

**MODIFY (8 ficheros)** — todos dentro de la lista EXACTA de §0.3:

| # | Ruta exacta | Cambio (y SÓLO eso) |
|---|---|---|
| M1 | `frontend/src/main.tsx` | `import "./index.css"` + monta `<BrowserRouter>` alrededor de `App` (StrictMode se conserva) |
| M2 | `frontend/src/App.tsx` | REESCRITO: placeholder C1 fuera; `AuthProvider` + `TopBar` + `Routes` (9 rutas, guard `RequireAuth`, `Outlet` para detalle) |
| M3 | `frontend/vite.config.ts` | `base: "./"`→`"/"` + `@tailwindcss/vite` en `plugins` (D-C8-13) |
| M4 | `frontend/package.json` | 4 deps pinnadas (D-C8-12); NINGÚN script ni campo más |
| M5 | `frontend/package-lock.json` | regenerado por `npm install` (diff solo esas 4 + transitivas) |
| M6 | `backend/.../config/RestAuthenticationEntryPoint.java` | +1 import `StandardCharsets` +1 línea `setCharacterEncoding` antes de `getWriter()` |
| M7 | `backend/.../ingest/IngestAuthenticationEntryPoint.java` | idem M6 (simetría, R8.46) |
| M8 | — | (ningún otro; `frontend/src/vite-env.d.ts` INTOCADO aunque existe) |

**DELETE (3 ficheros + directorios vacíos implícitos)** — D2:

| # | Ruta exacta |
|---|---|
| D-1 | `frontend/public/mockups/04-proyectos.html` |
| D-2 | `frontend/public/mockups/05-proyecto-detail.html` |
| D-3 | `frontend/public/ws-console.html` |

- `frontend/public/mockups/` (directorio) desaparece al quedar vacío (git no
  trackea directorios — no es acción extra).
- **INTOCABLES y verificados**: `docs/mockups/` COMPLETO (01, 02, 03, 04, 05,
  index — el contrato visual que las pantallas replican; NINGUNA de estas
  se borra ni se edita). `frontend/nginx.conf`, `frontend/Dockerfile`,
  `compose.yaml`, `.env`, `.env.example`, `frontend/src/vite-env.d.ts`,
  `frontend/index.html` (ya correcto: title + lang="es"), TODO backend salvo
  M6/M7, smokes `qa_c2`–`qa_c7` + `ws_echo_test.py`, tests Java C1–C7,
  `frontend/tsconfig.json`. NADA fuera de esta tabla llega a apply.

---

## 5. Interfaces / Contracts

### 5.1 Tipos (`api/types.ts`) — DTOs LITERALES del Schema §3.2/§3.3 (R8.19)

```ts
export type Nivel = "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR" | "FATAL";
export type Entorno = "development" | "staging" | "production";
export type EstadoProyecto = "ACTIVE" | "ARCHIVED";

// §3.2 login — orden literal {token, username, expires_at}
export interface LoginResponse { token: string; username: string; expires_at: string; }
export interface MeResponse   { username: string; role: "ADMIN"; expires_at: string; }

// §3.3 — `summary` NUNCA viaja en C8 (ni se declara): el Tipo NO lo incluye
export interface ProjectDto {
  id: number; name: string; description: string | null;
  status: EstadoProyecto; created_at: string; updated_at: string;
  api_key_prefix: string | null; has_key: boolean;
}   // description null posible; api_key_prefix null + has_key false sin key

// orden literal {key, prefix, created_at} (regenerate 201)
export interface ApiKeyResponse { key: string; prefix: string; created_at: string; }

// Listado — LogListItemDto de 9, SIN exception ni metadata (V6)
export interface LogListItemDto {
  id: number; project_id: number; level: Nivel; environment: Entorno;
  timestamp: string; received_at: string; message: string;
  source: string | null; correlation_id: string | null;
}
// Detalle — LogDto de 11 (los 9 + estas 2)
export interface LogDto extends LogListItemDto {
  exception: string | null;
  metadata: Record<string, unknown> | null;
}

// GET /logs — cursor opaco; next_cursor null = fin (explícito)
export interface LogsPagina { items: LogListItemDto[]; next_cursor: string | null; }

// 400/403/404/409 — {code, message, field?}
export interface ErrorBody { code: string; message: string; field?: string; }
```

WS (mismo fichero o `ws/client.ts` — decisión de colocación en apply; mismo
material):

```ts
export interface SetFilters {                    // S8-8: 6 claves SIEMPRE, nulls
  project_id: number | null; environment: Entorno | null; level: Nivel | null;
  from: string | null; to: string | null; text: string | null;
}
export interface FrameBatch {                    // §3.5 — 5 claves; validador de shape
  type: "batch"; batch_id: string; project_id: number;
  received_at: string; logs: LogDto[];           // logs usan LogDto COMPLETO
}
```

### 5.2 Firma del api-client (`api/client.ts`)

```ts
export class ApiError extends Error {
  status: number;                       // 0 = red caída; 401 NO se construye Aquí
  code?: string; message: string; field?: string;
}
export function registrarInterceptor401(cb: () => void): void;
export interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  body?: unknown;                        // se serializa JSON si presente
  headers?: Record<string, string>;      // ej. X-Confirm-Log-Id
  bearer?: boolean;                      // DEFAULT true; false SOLO para login
}
export async function request<T>(path: string, opciones?: RequestOptions): Promise<T>;
```
Comportamiento: rutas SIEMPRE relativas (`/api/v1/…`); `Authorization:
Bearer` si `bearer !== false` (y SOLO si hay token — login con bearer:false
no lo mira); 200/201/204 → JSON (204 → `undefined` como T);
**401 → `interceptor401()` y THROW `ApiError{status:401}` sin tocar body**
(regla R8.14; el interceptor NO devuelve la respuesta — el flujo termina ahí
para panel; el login usa `bearer:false` y su 401 cae en la rama genérica
de ApiError sin interceptor — ver 5.3); otro no-200 → `text()` + `JSON.parse`
try/catch → `ApiError{status, code?, message, field?}` con fallback
`message: "HTTP <status>"`; catch de red → `ApiError{status:0}`.

### 5.3 Firma de `api/panel.ts` — una función por endpoint

```ts
login(u: string, p: string): Promise<LoginResponse>            // bearer:false — su 401 → ApiError inline
me(): Promise<MeResponse>
listaProyectos(): Promise<ProjectDto[]>
proyecto(id: number): Promise<ProjectDto>
crearProyecto(d: {name: string; description?: string}): Promise<ProjectDto>   // 201
editarProyecto(id: number, d: {name?: string; description?: string}): Promise<ProjectDto>  // PATCH solo modificados
cambiarEstado(id: number, status: EstadoProyecto): Promise<ProjectDto>        // PATCH flujo 7
regenerarApiKey(id: number): Promise<ApiKeyResponse>                          // 201
revocarApiKey(id: number):Promise<void>                                       // 204
eliminarProyecto(id: number, nombreConfirmado: string): Promise<never>        // hoy 405/404 (C10)
purgarLogs(id: number, nombreConfirmado: string): Promise<never>              // hoy 405/404 (C10)
logs(f: FiltrosLogs, cursor: string | null): Promise<LogsPagina>              // Serializa nulls fuera
log(id: number | string): Promise<LogDto>
eliminarLog(id: number, confirmacionId: number): Promise<never>               // hoy 405/404 (C10)
```
`Promise<never>` en los DELETE = el tipo DOCUMENTA que hoy NUNCA hay happy
path (405/404) sin fingir éxito — C10 los cambiará a su shape real cuando
exista. Las cabeceras `X-Confirm-*` ya se envían formadas (S8-2):
`X-Confirm-Log-Id: String(id)` / `X-Confirm-Name: nombreConfirmado`.
Nota: la variante de login maneja su 401 con la MISMA ApiError (sin
interceptor: `registrarInterceptor401` se salta cuando el request lleva
`bearer: false` — el único flag que lo evita).

### 5.4 Firma del cliente WS (`ws/client.ts`) — ver D-C8-5 completo

```ts
export type EstadoWs = "conectado" | "desconectado" | "reconectando";
export interface WsCallbacks {
  alFrame(batch: FrameBatch): void;
  alEstado(estado: EstadoWs, intento: number): void;
  alCierreInaugural(): void;                      // close sin open → verificar /me
  // NOTA: el 401 del handshake NO se diagnostica dentro del client (no hay
  // status); el hook lo resuelve con me() — D-C8-5.6
}
export class ClienteWsLogs {
  constructor(cb: WsCallbacks, reloj: { setTimeout: typeof setTimeout; clearTimeout: typeof clearTimeout } = ...);  // inyectable para tests
  conectar(token: string, filtros: SetFilters): void;
  aplicarFiltros(f: SetFilters): void;            // set_filters SIEMPRE 6 claves
  cerrar(): void;                                 // SIN reconexión ulterior
}
export function retrasoReconexion(intento: number, aleatorio?: () => number): number;
```

### 5.5 Diff exacto del fix D1 (M6/M7) — ver D-C8-14 (bloque de código exacto
+ test byte-exacto con `MockHttpServletResponse`: (a) `Content-Type:
application/json;charset=UTF-8`; (b) bytes `0xC3 0xB3` presentes, `0xF3`
aislado ausente; (c) relectura UTF-8 SIN `U+FFFD`).

### 5.6 Smoke: convención de fichero
`scripts/qa_c8_smoke.sh` con IDs de veredicto `E8.x` (patrón real de qa_c2…_
qa_c7 que usan `E2.x`/`E7.x`, no "Qn" — observado en HEAD; los smokes de este
repo MARCAN por escenario) + bloque visual `Q23` BLOQUEADO como los previos.
`marca() { RESULTADOS+=("$1: $2${3:+ ($3)}"); }`, exit 0/1/2, tmpdir
`mktemp -d`+`chmod 700`+`trap EXIT`, JWT/key en ficheros del tmpdir únicamente,
`down -v` + `up -d --build` inicial (Q13/E2.7). Cliente WS RFC 6455 mínimo
embebido en el tmpdir (heredando el patrón `ws7_client.py` de qa_c7 — stdlib,
no dep).

---

## 6. Testing Strategy

### 6.1 Vitest (R8.56/E8.56 — `npx vitest run`, cero config, entorno node)

| Fichero | Cubre (E8) | Qué aserta |
|---|---|---|
| `src/ws/dedup.test.ts` | E8.25/E8.28 | dedup por id; orden global con micros; grupo coherente; no-mutación |
| `src/ws/client.test.ts` | E8.30/parte E8.27 | secuencia de `retrasoReconexion` con `aleatorio` inyectado (1s→2s→4s…cap 30s; límites jitter); shape byte-exacto de `set_filters` (6 claves, nulls); parser ignora frames no-batch sin lanzar |
| `src/hooks/usePagination.test.ts` | E8.24 | acumular páginas; fin con `next_cursor: null`; vacío legítimo |
| `src/hooks/useLogFilters.test.ts` | E8.21/E8.23 | ida y vuelta filtros⇄query idempotente; ISO-8601 CON offset (`datetimeLocalAIsoConOffset` con offset inyectado — TZ fija del test); from==to legal; toSetFilters 6 claves |
| `src/api/client.test.ts` | E8.15/E8.16/E8.17/E8.14 | 401 SIN leer body (fetch mock que CRASHEA si `json()/text()` se invocan); no-200 tolerantes (405 HTML, 502 texto, cuerpo vacío → "HTTP <status>"); 404 JSON parseado (code/message/field); Bearer en todo menos login; red caída → status 0 |
| `src/components/Modal.test.ts` | E8.37/E8.47(c) | coincidencia literal con trim (espacios inicio/final); texto incompleto/distinto falso |

Hooks de React (guard, login, logout, doble-submit, dedup en página) NO se
testean renderizando DOM (prohibido jsdom): se cubren por smoke de servidor +
MANUAL Q23 (parejas del spec §3). La prueba mock-based de client.ts es la QA
real del contrato de tolerancias.

### 6.2 Smoke `scripts/qa_c8_smoke.sh` (R8.57/E8.57) — casos y mapeo

| Grupo | Escenarios | Qué hace |
|---|---|---|
| pre-fase | — | `down -v` + `up -d --build` (guardia de compilación tsc+vite vía stage 1 Docker) |
| E8-servido | E8.6/E8.51 | `curl /` y `/logs/123` → 200 `text/html`; assets ABSOLUTOS en el HTML (grep `src="/assets/`\|`href="/assets/`) y NIGNÚN asset bajo ruta anidada |
| E8-auth | E8.7/E8.14/E8.15/E8.18/E8.19 | login 200 con ORDEN de claves literal; `GET /me` con/sin token; 401 SIN token → status; todas llamadas con Bearer en el header; por `127.0.0.1:8080` (mismo origen) |
| E8-charset | E8.48/E8.49 | 401 panel sin token: `Content-Type: …charset=UTF-8` + bytes `0xC3 0xB3` (xxd) y SIN `0xF3` aislado; 401 ingesta sin `X-API-Key` idem |
| E8-API | E8.20/E8.23–E8.27/E8.34/E8.36 (REST)/E8.39/E8.41/E8.42/E8.43/E8.44/E8.45/E8.46 | CRUD proyectos (201/200/409/400), regenerate 201 `{key,prefix,created_at}` orden literal, revoke 204, GET /logs keyset (`{items,next_cursor}`), 400 con `code`/`field` (from>to → `filter_from_to_inverted` SIN field), detalle 11 campos, ProjectDto SIN summary |
| E8-C10-negativo | E8.38/E8.47 | `DELETE /panel/logs/{id}` y `DELETE /panel/projects/{id}` → 405/404 TAL CUAL; `?include=summary` aceptado SIN `summary` en el DTO |
| E8-WS | E8.27/E8.30/E8.31 | handshake 101 con token; tras ingest 202 → UN frame batch (5 claves, `b_`+UUID, LogDto completo) byte-exacto; `set_filters` 6 claves aceptado (eco de estado OK — el servidor no responde con error); 401/403 pre-upgrade con cuerpo VACÍO (py client heredado de qa_c7) |
| E8-visual | Q23 | renders con navegador BLOQUEADO + instrucción manual (parejas §3 del spec) |

### 6.3 mvn (R8.48/E8.50)
`mvn test` completo: 174/174 heredados VERDES + `EntryPointCharsetTest`
(2 escenarios byte-exacto). Ninguna aserción previa editada.

### 6.4 MANUAL (Q23 — parejas del spec §3)
Visual/navegación de las 6 pantallas contra las maquetas (`docs/mockups/`),
estados §4 (esqueleto/vacíos/banners/modales), contrato visual 1:1 (paleta,
FATAL invertido, banda, indicador SOLO en `/logs`, revelación única),
navegación real del guard y el retorno con filtros. Instruición documentada
en el qa-report para el cierre/demo C9. **SIN E2E navegador** (E8.58 —
prohibido Playwright/Cypress/drivers).

### 6.5 Regresión (E8.59)
Re-corridas al cierre: `qa_c3` (endpoints que la UI consume) + `qa_c7`
(handshake WS) verdes; `mvn test` completo; `qa_c2` MAY; diff del lockfile
revisado.

---

## 7. Migration / Rollout

- **Sin migración de datos** (cero SQL, cero flyway, cero env nuevas).
- **Lockfile**: primera modificación desde C1 — `npm install` con las 4 deps
  pinnadas regenera `package-lock.json`; el diff esperado contiene SOLO:
  `react-router-dom@7.18.4` (y su interna `react-router@7.18.x`),
  `tailwindcss@4.3.3` (+ `@tailwindcss/oxide`, `@tailwindcss/node`,
  `source-map-js` y transitivas del build engine v4),
  `@tailwindcss/vite@4.3.3`, `vitest@5.0.5` (+ `@vitest/runner`,
  `@vitest/snapshot`, `@vitest/expect`, `@vitest/utils`, `vite-node`,
  `tinybench`, `tinypool`, `pathe`, `std-env` y transitivas propias) —
  verificable en apply/verify (E8.54). NINGÚN cambio de `package.json`
  fuera de esas 4 líneas.
- **Rollout de despliegue**: `docker compose up -d --build` (corta habitual —
  el smoke lo hace con `down -v` primero para ser autocontenido, PERO el
  rollout NORMAL del corte NO borra datos: sólo `--build` del frontend y del
  backend con la línea D1; el volumen pgdata persiste). El proxy nginx y el
  Dockerfile no cambian; el stage 1 recompila TS/Vite como guardia.
- **Retiro de estáticos (D2)**: efectivo al primer `--build` posterior (los
  ficheros borrados dejan de entrar en `dist/`; `:8080/mockups/**` y
  `/ws-console.html` caen al SPA fallback → `text/html` — E8.52).
- **Sin roll-back especial**: volver a HEAD~ revierte ficheros; el lockfile
  anterior queda en git.

## 8. Open Questions

**Ninguna bloqueante.** Dos puntos NO bloqueantes ya decididos con default
para tasks (registrados para trazabilidad):

1. **`vitest@5.0.5` vs `4.1.12`** — decidido en D-C8-11: pin 5.0.5 (última
   estable, peer vite 7, engines node 22.12+). Fallback documentado a
   `4.1.12` SOLO si apply detectara incompatibilidad real (no se espera:
   mismo plugin react, mismas APIs base usadas). El diff del lockfile
   registra el pin final.
2. **Detección del 401 pre-upgrade del WS** — el navegador no expone el
   status; decidido en D-C8-5.6: `close` sin `open` previo → verificación
   `me()` por el hook (401 → interceptor; 200 → backoff). Cualquier caso
   borde (p. ej. 403 por Origin que también cierra sin open) cae en la rama
   "200 de me() → backoff", que es el comportamiento exigido por E8.31b.

---

## 9. Checklist de frontera final (dif-0 fuera de tabla)

- [ ] `git status` post-apply NO muestra cambios en: `frontend/nginx.conf`,
      `frontend/Dockerfile`, `compose.yaml`, `.env*`, `frontend/tsconfig.json`,
      `frontend/index.html`, `frontend/src/vite-env.d.ts`, `docs/**`
      (incl. `docs/mockups/` — 6 maquetas intactas), smokes previos, tests
      Java C1–C7, y CUALQUIER fichero backend distinto de M6/M7/C39.
- [ ] El diff de `frontend/package.json` es EXACTAMENTE 4 líneas de deps.
- [ ] El diff del backend es EXACTAMENTE: 2 clases (+1 import c/u),
      1 fichero de test nuevo.
- [ ] NINGÚN `localStorage`, `document.cookie`, ni `console.log(token)` en
      la SPA (grep de apply).
- [ ] NINGÚN `fetch(` fuera de `api/client.ts` ni `new WebSocket` fuera de
      `ws/client.ts` (grep de apply).
- [ ] `npm run build` verde; smoke `qa_c8_smoke.sh` exit 0 (visuales
      BLOQUEADO-Q23 anotados).