---
project: Project_Log
change: C8
artifact: proposal
last_updated: 2026-10-26
status: approved
gate:
  verdict: approved
  date: 2026-10-26
  notes: >
    Aprobado TAL CUAL por el usuario, sin matices. Las 4 decisiones quedan
    como recomendaba el proposal: D1 fix 401 UTF-8 ENTRA en C8
    (RestAuthenticationEntryPoint + simetría IngestAuthenticationEntryPoint
    + test), D2 retirar frontend/public/mockups/** y ws-console.html SÍ,
    D3 dependencias nuevas SÍ con versiones pinnadas (react-router-dom,
    tailwindcss + @tailwindcss/vite, Vitest), D4 JWT en sessionStorage con
    fallback a memoria (nunca localStorage/cookies).
---

# Proposal — C8: Frontend del panel según maquetas (React + TypeScript SPA)

> Base: `.atl/sdd/C8/explore.md` (completa, HEAD `5df6409`). Afirmaciones
> críticas re-verificadas por cuenta propia de cara a este proposal:
> `frontend/package.json` (deps = react/react-dom SOLO; sin test runner NI
> eslint), `frontend/vite.config.ts` (`base: "./"` presente, Riesgo R-6 real),
> `backend/.../config/RestAuthenticationEntryPoint.java` (método `escribir401`
> líneas 62–67: `getWriter()` SIN `setCharacterEncoding` — deuda ISO-8859-1
> confirmada), `docs/03_APPFLOW.md` §4 (tabla de estados) y §5 (errores
> globales) leídos línea a línea, `docs/04_UIUX.md` V1–V11 (tabla §8) y la
> sentencia literal del modal 8b en `docs/mockups/03-log-detail.html`
> (`DELETE /logs/48211 · header X-Confirm-Log-Id: 48211`, comparación literal
> con trim, `bg-red-300` deshabilitado → `bg-red-700`), y sección C8 de
> `docs/07_SLICING_PLAN.md` (línea 143 ss.).

## 1. Motivo y encaje en el plan

- **Por qué ahora:** C2–C7 están cerrados (`estado: hecho` en el slicing
  plan) y el backend vivo completa la fase panel-visible: auth (`mvn` heredado
  174/174 tras C7), proyectos/API-keys, ingesta 202, consulta keyset con
  filtros y cursor `r,id`, y WebSocket §3.5 con broadcast ordenado. El único
  frontend existente es el **placeholder "pila OK" de C1**
  (`frontend/src/App.tsx`, comentario literal: "se sustituye por el panel real
  en C8"), más las copias de maqueta del puente C3/C4.5 y `ws-console.html`
  (C7). C8 es el corte que convierte la SPA en el panel real, el primero que
  consume las 17 rutas `/api/v1/**` construidas.
- **Story del PRD:** cubre la story de "panel de administración" (consultar,
  filtrar, paginar, detallar, archivar, gestionar keys y streaming vivo de
  logs — PRD §3/§4, trazadas en AppFlow §3 flujos 1–7).
- **Encaje (`docs/07_SLICING_PLAN.md` §C8, línea 143):** alcance literal —
  `/login` (01), `/logs` (02) con keyset + banda "● En vivo" + dedup +
  banner 400 con campo culpable + estado conectado/reconectando SOLO aquí,
  `/logs/:id` (03) con modal 8b V11 (sentencia literal + trim + botón rojo
  deshabilitado; la petición dará 405/404 hasta C10 y la UI lo muestra como
  AppFlow §5), `/proyectos` (04) y `/proyectos/:id` (05) con bloque API key y
  revelación única; `/proyectos/nuevo` y `/proyectos/:id/editar` SIN maqueta
  (AppFlow flujo 2); router guard vía `GET /me`. Deuda heredada a resolver por
  D1: el 401 ISO-8859-1 señalado en R-nuevo-4 de C2 y heredado "→ C8/C9" por
  C2–C7.

## 2. Alcance del corte

**Entra (UI):**

1. **`/login`** (maqueta 01): tarjeta login, `POST /api/v1/panel/auth/login` →
   200 `{token, username, expires_at}`; 401 = mensaje inline "Usuario o
   contraseña incorrectos" sin revelar campo (única excepción al interceptor
   global, AppFlow §5); botón "Enviando…" anti-doble-submit; con sesión →
   redirige a `/`.
2. **`/logs`** (maqueta 02): filtros combinables en grid (proyecto/env/level/
   desde-hasta/reiniciar/texto), **estado de filtros en la query string** (F5
   conserva, retorno desde detalle intacto — AppFlow flujo 4), paginación
   keyset "Cargar más" con deshabilitado "fin de resultados" cuando
   `next_cursor: null`, estado vacío "No hay logs que cumplan los filtros"
   (≠ vacío legítimo ≠ eliminado), banner 400 con `code` + campo culpable
   (`filter_from_to_inverted` SIN field; `filter_invalid_value` con
   `field=`), tabla densa 8 columnas SIN exception/metadata, badges
   severidad V3 (FATAL invertido), píldoras entorno V4, **banda "● En vivo"**
   (V5, borde discontinuo verde), **indicador de conexión persistente SOLO en
   `/logs`**: "● Conectado" / "○ Desconectado" / "Reconectando (intento N)…".
   Cliente WS completo: handshake `wss|ws + /ws/logs?token=` (patrón
   `ws-console.html`), frame batch 5 claves **dedup por `id`** insertando al
   comienzo respetando `received_at DESC, id DESC`, `set_filters` (6 claves
   ESTRECHAS) justo tras conectar, tras cada cambio de filtro y tras cada
   reconexión, backoff exponencial con jitter 1 s → 30 s, y al reconectar
   refresco de primera página. La banda "● En vivo" recoge los batches.
3. **`/logs/:id`** (maqueta 03): ficha completa, LogDto 11 campos,
   exception `<pre>` mono + Copiar y metadata JSON `<pre>` + Copiar solo si
   no nulos, correlación mono + select-all, 404 → "Este registro no existe
   (o fue eliminado)" + Volver que conserva filtros/página,
   **modal 8b V11**: exigencia de escribir la sentencia literal completa
   (`DELETE /logs/<id> · header X-Confirm-Log-Id: <id>`), comparación literal
   **con trim**, botón rojo deshabilitado `bg-red-300` → habilitado
   `bg-red-700`. Al pulsar, la llamada irá a un DELETE que **hoy no existe**:
   405/404 se muestran como AppFlow §5 (banner "No existe (quizás fue
   eliminado)"), **sin fingir éxito** — la integración real llega en C10 con
   `X-Confirm-Log-Id`.
4. **`/proyectos`** (maqueta 04): tarjetas con STATUS Activo/Archivado,
   prefix + `has_key`, estado vacío legítimo + CTA "Nuevo proyecto", toggle
   archivados visibles (archivado = PATCH `status`, nunca DELETE).
5. **`/proyectos/:id`** (maqueta 05): ficha + bloque API key
   (generar/regenerar = POST `.../api-key/regenerate` → **201
   `{key, prefix, created_at}`**) con **modal de revelación única**
   ("No se volverá a mostrar; cópiala ahora" + Copiar; al cerrar queda solo
   el `prefix`), revocar = DELETE `.../api-key` → 204, archivar/desarchivar,
   editar (a flujo 2). Resumen P1 (`include=summary`) **NO entra**: es C10 y
   el endpoint no existe (la tarjeta muestra lo que viaja en ProjectDto, sin
   `summary`).
6. **`/proyectos/nuevo`** y **`/proyectos/:id/editar`** (SIN maqueta, AppFlow
   flujo 2): formulario name 1..200 + description ≤2000, errores por campo
   (400), 409 `name_duplicate` inline "Ya existe un proyecto con ese nombre",
   datos conservados; nuevo → 201 y redirección a ficha con aviso de generar
   key; editar → 200 y confirmación "Cambios guardados".
7. **`*` (404 del panel):** pantalla 404 con sesión; sin sesión → `/login` con
   destino guardado. En SPA nunca hay 404 de servidor (SPA fallback nginx).
8. **Guard de rutas vía `GET /me`:** vista protegida sin JWT → guard consulta
   `GET /me` → 401 → redirige a `/login` **recordando la vista destino**
   para volver tras login; restauro de sesión al recargar con `GET /me`
   antes de renderizar.
9. **Cliente API centralizado** (módulo único `api.ts`): Bearer,
   tolerancias heredadas (§4 de este documento), interceptor global 401
   (descarta token, cierra WS, redirige con destino), banner global 500/502 +
   Reintentar, red caída.
10. **Archivado de proyectos** vía PATCH `{status: "ACTIVE"|"ARCHIVED"}` con
    confirmación estándar (no hay UI de `project_archived` 403: es solo de
    ingesta).

**NO entra:**
- Flujos 8 API/9 y los tres DELETE (`X-Confirm-Name`, `X-Confirm-Log-Id`,
  `X-Confirm-Project-Id`) y `include=summary` → **C10** (la UI de los modales
  sí se construye — ver apartado 3; las peticiones darán 405/404 y se
  muestran como §5 sin fingir éxito).
- Borrados definitivos de registros/proyectos/logs purgados → C10.
- **Token en primer frame del WS** (`{"type":"auth","token":…}`) → FUTURO;
  el token va en query del handshake como hoy (§3.5 vivo).

## 3. Arquitectura de frontend propuesta

**Stack sobre lo existente (C1, no se reimponen herramientas):**
React 19 + TypeScript 5.9 estricto + Vite 7, con:

- **`react-router-dom` v7** (ecosistema hoy: 7.18.4, sin CVEs abiertos en
  rango 7.18.x — el spec pina la versión exacta). Data mínima: `BrowserRouter`
  + `Routes` con las 9 rutas de AppFlow §1 1:1; query string de `/logs`
  gestionado con `useSearchParams` (R-7 no es opcional).
- **Tailwind CSS v4 con `@tailwindcss/vite`** (ecosistema hoy: 4.3.3 ambos;
  el spec pina). Sin CDN en producción (las maquetas usaban
  `cdn.tailwindcss.com` solo por ser estáticas). Las clases de las maquetas
  aprobadas son el contrato visual y se traducen 1:1; los tokens de
  `docs/04_UIUX.md` (stone/teal/green/severidades/entornos/destructivo) viven
  en `frontend/src/index.css` vía `@theme` (v4), y las utilidades específicas
  (badges `lv-*`, píldoras `env-*`) como clases de componente locales.

**Estructura de directorios** (bajo `frontend/src/`):

```
src/
  main.tsx                    # edit: monta router
  App.tsx                     # reescrito: layout + guard + routes (adiós placeholder C1)
  index.css                   # @import "tailwindcss" + @theme tokens 04_UIUX
  api/
    client.ts                 # fetch único: Bearer, tolerancia 401 solo-status,
                              # text()+JSON.parse tolerante en no-200, interceptor global
    types.ts                  # ProjectDto, LogListItemDto (9), LogDto (11),
                              # LogsPagina{items,next_cursor}, LoginResponse, ApiKeyResponse, ErrorBody
    panel.ts                  # funciones por endpoint (login, me, projects CRUD,
                              # regenerate, revoke, logs, logs/:id)
  ws/
    client.ts                 # WebSocket /ws/logs?token=, set_filters 6 claves,
                              # backoff 1→30 s con jitter, listener de frames batch
    dedup.ts                  # acumulador puro: dedup por id + inserción received_at DESC, id DESC
  hooks/
    useAuth.ts                # token + expires_at, GET /me, interceptor y logout local
    useLogFilters.ts          # URLSearchParams ⇄ filtros (mapeo y serialización)
    useLogsStream.ts          # página /logs: conecta SOLO aquí, refresco de página 1 al reconectar
    usePagination.ts          # cursor keyset acumulado (puro, sin DOM)
  components/
    TopBar.tsx EstadoConexion.tsx BandaEnVivo.tsx BadgeSeveridad.tsx
    PildoraEntorno.tsx Banner.tsx Modal.tsx ModalBorrado8b.tsx
    ModalRevelacionKey.tsx TarjetaProyecto.tsx FiltrosLogs.tsx
    TablaLogs.tsx Esqueleto.tsx
  pages/
    Login.tsx Logs.tsx LogDetalle.tsx Proyectos.tsx
    ProyectoDetalle.tsx ProyectoNuevo.tsx ProyectoEditar.tsx NotFound.tsx
```

**Rutas y CORS:** rutas relativas `/api/v1/**` y `/ws/logs` con mismo origen —
el proxy nginx de C1 (`proxy_pass http://backend:8080` SIN URI final) sigue
intacto; cero CORS en producción. El CORS REST de dev y la Origin EXACTA del
WS siguen dependiendo de `FRONTEND_ORIGIN` (`.env`, ver Riesgo R-1).

**Estado:** local por página (sin librería de estado global; react no trae
nada que exigirlo). El token vive en el hook `useAuth` (única fuente), los
filtros de `/logs` en la **query string** (persistente ante F5 y navegable),
el acumulado keyset + stream en la página `/logs`, la conexión y dedup en
`useLogsStream`/`ws/`. El indicador de conexión y el WS se montan SOLO dentro
de `/logs` y se cierran al salir de la vista y en logout/401 (AppFlow §5).

**Fix `vite.config` (R-6, necesario):** `base: "./"` → `base: "/"`.
Con rutas SPA anidadas (`/logs/:id`), el `./assets/…` del `index.html`
resuelve relativo a la ruta y `try_files` sirve `index.html` bajo
`/logs/assets/…` con MIME roto. Base absoluta = el único cambio de
configuración de C1 que C8 hace conscientemente (edita `vite.config.ts` y
añade `@tailwindcss/vite` a `plugins`).

## 4. REGLA de tolerancias heredadas en el cliente API

Norma del módulo `api/client.ts` (patrón normativo ya validado en
`frontend/public/mockups/04+05.html`, puente C3/C4.5):

- **Ante 401 SOLO se mira `response.status` y NUNCA se parsea el body.** Ni
  `r.json()`, ni `r.text()` en el camino 401: el 401 sale hoy de
  `RestAuthenticationEntryPoint` en **ISO-8859-1** (la `ó` de
  "Autenticación requerida" = byte `0xF3`; verificado en líneas 62–67), y
  `Response.json()` decodifica SIEMPRE como UTF-8 → mojibake `U+FFFD`. El
  interceptor global reacciona solo con el status: descarta token, cierra WS,
  redirige a `/login` con destino.
- **Resto de no-200 (400/403/404/409/500/502/405):** `r.text()` (que SÍ
  respeta el `charset` del header) + `JSON.parse` en try/catch con fallback
  `"HTTP <status>"`. Nunca un parse estricto que rompa la UI ante un cuerpo
  inesperado (405 hoy puede venir de Spring en HTML).
- **Con el fix del charset de D1 aprobado**, el 401 pasa a salir UTF-8 con
  `Content-Type: application/json;charset=UTF-8` y `.json()` es seguro;
  la tolerancia de arriba se MANTIENE igual, porque la regla es heredada y
  no se acopla a una corrección de backend.
- El login (única llamada sin Bearer) trata su 401 como inline
  "Usuario o contraseña incorrectos", fuera del interceptor global (AppFlow §5).

## 5. Decisiones abiertas para el GATE del usuario

**D1 — ¿El fix del 401 ISO-8859-1 en backend entra en C8 o se difiere a C9?**
> **Recomendación: entra en C8.** Motivo: (a) es el único punto donde la UI
> que C8 construye muestra esos `message` en banners — y el demo de C9 no
> debería enseñar mojibake `U+FFFD`; (b) es literalmente **una línea**
> (`response.setCharacterEncoding("UTF-8")` antes de `getWriter()`) +
> aserción de test y header `charset=UTF-8` en mvn, en el spec; (c) cuatro
> archive-reports (C2 R-nuevo-4 y heredados C3–C7) la marcan "→ C8/C9" y C9
> es el corte de demo/deploy, no el lugar natural para tocar backend.
> Simetría: corregir también `IngestAuthenticationEntryPoint` (mismo patrón,
> líneas 72–74): no afecta a la UI del panel, pero es el mismo bug de una
> línea y corregirlo solo en el del panel dejaría un duplicado con deuda
> conocida — recomendado incluido. Alcance backend EXACTO: solo esas dos
> clases + su test; nada más del backend se toca.

**D2 — ¿Se retiran `frontend/public/mockups/**` y `ws-console.html` en C8?**
> **Recomendación: sí, se retiran en C8** (dentro del corte que los
> sustituye). Motivo: el retiro está **pactado** en el archive-report de C3
> ("acción C8: maquetas React REALES + retiro de las copias") y
> `ws-console.html` se declara a sí mismo "se reemplaza en C8"; dejarlas
> serviría UI vieja en `:8080/mockups/**` y `:8080/ws-console.html` que
> contradice el panel nuevo y mantiene un segundo patrón de cliente API. El
> retiro es `docs(SDD)`-safe: **`docs/mockups/` (congelado) NO se toca** —
> siguen siendo el contrato visual que las pantallas replican.

**D3 — ¿Se aprueban las dependencias nuevas?**
> **Recomendación: sí** — `react-router-dom@7` (no hay rutas 9 sin router,
> y reinventarlo es coste + riesgo), `tailwindcss@4` + `@tailwindcss/vite`
> (las maquetas aprobadas están ESCRITAS en clases Tailwind: traducirlas a
> CSS propio reintroduce desvío visual contra un GATE ya cerrado), y
> **Vitest sí, incluido** para lógica pura (ver §6): es la única QA
> automatizable sin navegador en un corte de frontend, y sin él la QA de las
> 6 pantallas queda reducida a servido + endpoints vía curl, sin cubrir
> dedup/comparadores. NINGUNA otra dep (sin estado global, sin librerías de
> componentes, sin testing-library/jsdom — la lógica pura corre en node).
> Serán las primeras deps nuevas desde C1; el spec pina versiones exactas
> (`react-router-dom` 7.18.x, `tailwindcss`/`@tailwindcss/vite` 4.3.x,
> `vitest` pin) y el cambio de `package-lock.json` queda anotado.

**D4 — ¿Dónde vive el JWT del panel: sessionStorage o solo memoria?**
> **Recomendación: `sessionStorage`** (patrón del puente C3, con try/catch
> por storage bloqueado y fallback a memoria en la pestaña). Motivo: el
> AppFlow exige **restaurar la sesión al recargar con `GET /me` antes de
> renderizar** (§1/flujo 1) — el modo solo-memoria (higiene de
> `ws-console.html`) obliga a re-loguear en cada F5 y contradice ese literal;
> `sessionStorage` es un storage efímero por pestaña (no compartido, muere al
> cerrarla), nunca `localStorage` ni cookies, y el token ya es corto (8 h).
> Se guarda token + `expires_at` y cualquier 401 lo descarta (§4/§5). La
> opción memoria-pura queda registrada como alternativa más dura pero
> inconsistente con el AppFlow aprobado.

## 6. Estrategia de QA

- **`scripts/qa_c8_smoke.sh` canónico** (misma plantilla que `qa_c2`–`qa_c7`):
  bash/curl contra `BASE_URL=http://127.0.0.1:8080` (todo por el proxy nginx
  del único puerto), `COMPOSE_BIN` autodetectado, tmpdir `mktemp -d` con
  `chmod 700` + `trap EXIT`, cero secretos impresos (JWT solo en ficheros del
  tmpdir, S16), helper `marca()` con veredictos "Qn: PASS|FAIL|BLOQUEADO" y
  exit 0/1/2. **Autocontenido: `down -v` + `up -d --build` inicial** (regla
  Q13/E2.7 — ningún smoke asume datos previos; el `--build` además es la
  guardia de compilación `tsc --noEmit` + `vite build`, porque el stage 1 del
  Dockerfile falla si TypeScript no compila). Casos previstos:
  - E8-servido: `/` y rutas anidadas (`/logs/123`) devuelven `index.html`
    `text/html` (SPA fallback + base absoluta, R-6) — nunca 404 de servidor.
  - E8-auth panel: login 200 con orden literal `{token, username, expires_at}`;
    `GET /me` con y sin token; 401 sin token (status, sin parsear body).
  - E8-charset (condicional D1): header `charset=UTF-8` y `ó` correcta en el
    body del 401 (y del 400 si aplica).
  - E8-API panel: `GET/POST/PATCH /panel/projects`, 409 `name_duplicate`,
    regenerate 201 `{key, prefix, created_at}`, revoke 204,
    `GET /panel/logs` `{items, next_cursor}` con keyset,
    `GET /panel/logs/{id}` 11 campos, 400 de filtros con `code`/`field`.
  - E8-C10-negativo: `DELETE /panel/logs/{id}` y `DELETE /panel/projects/{id}`
    → 405/404 tal cual (la UI debe sobrevivir a esto, R-3);
    `?include=summary` aceptado pero sin `summary` en el DTO.
  - E8-WS: login → WS con token → ingest real (202) → **frame batch
    byte-exacto §3.5** recibido; `set_filters` acepta la shape de 6 claves;
    401/403 pre-upgrade con cuerpo vacío (token malo / Origin mala).
  - E8-visual-bloqueado: los renders con navegador quedan BLOQUEADOS como Q23
    (C3), con instrucción manual de comprobación visual al usuario para el
    cierre (la demo de C9).
- **Guardia de compilación:** `npm run build` (`tsc --noEmit` estricto +
  `vite build`) — verificado como parte del smoke vía build Docker, y
  repetible en local.
- **Vitest (si D3 lo aprueba, SOLO lógica pura, sin DOM):** `ws/dedup.ts`
  (dedup por `id` + orden `received_at DESC, id DESC` con micros),
  `usePagination`/cursor keyset (acumulado + fin con `next_cursor: null`),
  comparador trim del modal 8b (coincidencia literal, espacios al final/inicio),
  y mapeo filtros ⇄ query string. Sin jsdom, sin testing-library.
- **SIN E2E de navegador** (precedente Q23 de C3: BLOQUEADO en este entorno).
- **Re-corrida de regression al final si el patrón del corte lo pide**
  (como C6/C7 re-corrieron `qa_c2`): mínimo re-corrida de `qa_c3` (endpoints
  panel que la UI consume) y `qa_c7` (handshake WS), más `qa_c2` si el spec
  lo dicta; `mvn test` completo (174/174 → +tests de D1 si entra).

## 7. Riesgos y mitigaciones

- **R-1 · Origin del WS / `FRONTEND_ORIGIN`** (403 pre-upgrade cuerpo vacío
  si el origen del navegador no casa con `FRONTEND_ORIGIN`; afecta igual al
  CORS REST de dev). **Mitigación:** caso de smoke E8-WS con Origin mala
  esperando 403; `.env.example` ya lo documenta — nota de despliegue en el
  spec (localhost:8080 en dev).
- **R-2 · Deuda 401 ISO-8859-1.** **Mitigación:** tolerancia obligatoria e
  incondicional en `api/client.ts` (§4); fix de 1 línea + test si D1 entra.
- **R-3 · API incompleta (C10).** Los 3 DELETE y el summary no existen. La
  UI de modales 8a/8b/8c se construye igual y muestra 405/404 como AppFlow
  §5, sin fingir éxito; el cliente API tolera cuerpos no-JSON (405 de Spring
  es HTML). Smoke E8-C10-negativo la protege.
- **R-4 · Desvío del contrato visual = reabrir el GATE.** Las maquetas
  aprobadas son contrato: paleta exacta, FATAL invertido, banda "● En vivo",
  sentencia literal del modal, indicador SOLO en `/logs`, revelación única de
  key, tabla SIN exception/metadata. **Mitigación:** traducción 1:1 de clases
  Tailwind de las maquetas + tokens `@theme` desde `04_UIUX.md` (§3), y la
  comprobación visual manual del cierre (Q23).
- **R-5 · WS en `/logs` SOLO + ciclo de vida.** Montaje dentro de la página,
  cierre al navegar fuera, al logout y ante 401 (AppFlow §5).
- **R-6 · `base: "./"` con rutas anidadas.** Fix a `base: "/"` consciente en
  C8 + caso E8-servido que re-ejecuta el fallo real (`/logs/assets/…`).
- **R-7 · Estado de filtros en query string obligatorio.** `useSearchParams`
  es el único camino: F5, retorno desde detalle y vista compartible (AppFlow
  flujo 4/§5 convenciones).
- **R-8 · Almacenamiento del JWT.** Decidido vía D4 (recomendado
  sessionStorage); el spec lo fija y el código centraliza en `useAuth`.
- **R-9 · Sin E2E de navegador.** Precedente Q23; mitigado con smoke de
  servidor + Vitest de lógica pura + comprobación visual manual planificada
  de cara al demo C9.
- **R-10 · Lockfile.** Primeras deps nuevas desde C1: versiones pinadas en el
  spec, diff de `package-lock.json` revisado, cero deps no aprobadas en D3.

## 8. Criterios de aceptación (done cuando)

1. Las 6 pantallas con maqueta (`/login`, `/logs`, `/logs/:id`, `/proyectos`,
   `/proyectos/:id` + revelación única de key y modales) y las 2 de flujo 2
   (`/proyectos/nuevo`, `/proyectos/:id/editar`) **renderizan contra la API
   real** con sus estados de AppFlow §4: esqueleto de cargando, estados vacío
   diferenciados (vacío ≠ sin-resultados-por-filtro ≠ eliminado), error según
   §5, éxito estable.
2. Errores globales §5 operativos: 401 → interceptor (logout local + WS
   cerrado + `/login` con destino y banner de expiración; login con 401
   inline); 404 "quizás fue eliminado" con Volver; 400 con `code` + campo
   culpable y lista conservada (filtros) o dentro del modal (confirmaciones);
   409 en línea; 500/502 + Reintentar; red caída con banner + indicador WS
   desconectado. Convenciones cerradas: nunca 404 como vacío, nunca perder
   filtros/página al ir-volver, nunca la key fuera de su ventana.
3. Streaming vivo en `/logs`: "● Conectado", banda "● En vivo" con batches
   dedup por `id` y orden correcto, `set_filters` tras conectar/cambiar/
   reconectar, backoff 1→30 s con "Reconectando (intento N)…", refresco de
   primera página al reconectar; nada de WS fuera de `/logs`.
4. Modal 8b V11 con sentencia literal + trim + `bg-red-300` deshabilitado →
   `bg-red-700`; y las peticiones DELETE (hoy 405/404) mostradas como §5 sin
   fingir éxito.
5. Guard de rutas vía `GET /me`: protegidas → `/login` recordando destino y
   retorno tras login con filtros intactos (van en la URL); `/` redirige a
   `/logs`; `*` → 404 del panel.
6. `scripts/qa_c8_smoke.sh` **verde** (autocontenido con `down -v`, sin
   secretos, BLOQUEADO solo lo inherente a navegador);
   `tsc --noEmit` + `vite build` verdes; `mvn` íntegro (174/174; +tests si
   D1 entra), re-corridas de regresión del §6 en verde.
7. Retiro D2 ejecutado si se aprueba (mockups copias + ws-console fuera;
   `docs/mockups/` intacto) — sin strings de maqueta huérfanas servidas.

## 9. Plan de commits previsto (pauta fija de cortes previos, SIN push)

1. `feat(frontend): panel React según maquetas — 6 pantallas + flujo 2, guard
   GET /me, cliente API tolerante, WS en /logs con dedup y backoff, modal 8b
   V11, base absolute (C8)`
   — si D1 entra, primero: `fix(config): 401 del panel en UTF-8 —
   setCharacterEncoding antes de getWriter (+ simetría ingest), test del
   header y del ó (C8)`.
2. `test(smoke): scripts/qa_c8_smoke.sh — servido SPA/base absoluta, auth y
   charset del panel, CRUD + keyset + regenerate, negativos C10 (405/404),
   WS batch byte-exacto y 403 Origin (C8)`
3. `docs(SDD): corte C8 — artefactos`, con `.atl/sdd/C8/*` (proposal, spec,
   tasks, qa, verify, archive) versionados en repo.
- Si el bucle de hallazgos de QA lo pide, commit acotado adicional como C5–C7
  (`+ronda de hallazgos`). Nada de push; C9 hará demo/deploy/firewall.

## 10. Estimación de tamaño

- **Creados ≈ 30–34 ficheros** bajo `frontend/src/` (8 pages + ~13 components
  + ~4 api/ws + ~4 hooks + `index.css`), más `scripts/qa_c8_smoke.sh`,
  ~2–4 ficheros Vitest (`src/**/*.test.ts`) si D3 incluye Vitest, y el artefacto
  `spec.md` + `tasks.md` + informes de las fases.
- **Modificados ≈ 5–6:** `frontend/src/main.tsx`, `App.tsx` (reescrito),
  `frontend/vite.config.ts` (base + plugin Tailwind), `frontend/package.json`
  + `package-lock.json` (deps D3), y si D1: 2 clases Java del entry point +
  su test (+mvn). Borrados si D2: `frontend/public/mockups/**` (2 ficheros) y
  `frontend/public/ws-console.html`; regenerado `dist/` por build.
- Dimensión apply: corte comparable a C6/C7 (25–30 tareas), concentrado en
  frontend; sin migraciones ni cambios de contrato de API.