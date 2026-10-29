---
project: Project_Log
change: C9
artifact: explore
last_updated: 2026-10-29
status: complete
---

# Explore — C9 Fase A: automatización del QA visual de C8 (Playwright headless Python)

> Exploración de solo lectura (artefactos de C8 + mockups congelados + docs +
> entorno en vivo). Base HEAD `c144f3d` (parche post-Q23 del detalle aplicado).
> Pila REAL arriba en http://localhost:8080 (contenedores
> `project_log_backend/frontend/postgres_1` healthy al 2026-10-29). No se
> tocan código ni repos; solo se escribe este fichero. S16: cero secretos
> impresos aquí — los nombres de variables de `.env` se citan, jamás valores.

## A. Fuentes leídas (verificadas fichero a fichero)

1. `.atl/sdd/C8/qa-plan.md` — tabla QP-8.1…QP-8.59 (§3), absorciones (§1),
   reglas de asertos (§2), higiene S16 y criterios de BLOQUEADO (§6).
2. `.atl/sdd/C8/qa-report.md` — tabla de veredictos: 14 MANUAL-PENDIENTE
   (demo C9) + matizaciones DEV-C8-4…DEV-C8-7.
3. `.atl/sdd/C8/spec.md` — SECCIÓN 2 escenarios E8.1–E8.47 (solo los
   leídos/pertinentes; NO lectura íntegra).
4. `docs/mockups/02-logs.html` y `03-log-detail.html` (CONGELADOS) —
   literales visuales de banner/indexador/banda/estados/modal 8b/404.
5. `docs/06_IMPLEMENTATION_PLAN.md` — Fase 1: «Demo de aceptación Fase 1
   (métricas del PRD §7)» (los 3 criterios) + lista de frontend.
6. `docs/03_APPFLOW.md` — flujo 2 (crear/editar proyecto) + §5 (manejo de
   errores global, tabla de códigos).
7. `.atl/context.md` — convenciones SDD (español, literales de API/UI =
   contrato que se CITA, no redefinen).
8. `/tmp/qa_c9/repro_detalle.py` — repro funcional: login por UI
   (`input[type="password"]` + botón Entrar/wait_for_url `**/logs`) + click
   en `button:has-text("Detalle")`; capturas 01–07 en `/tmp/qa_c9/`.
9. `.atl/notes/c8-parche-post-q23-detalle-ruta-hermana.md` — post-parche:
   detalle = RUTA HERMANA `/logs/:id` (D-C8-8, ratificada usuario), filtros
   viajan en la query string (D-C8-6) y el ACUMULADO keyset viaja en store
   EN MEMORIA `frontend/src/hooks/storeListado.ts` (consumo único al montar
   `/logs` si la query coincide; store no toca storage — R8.7).
10. `compose.yaml` + `podman ps` (nombre del servicio backend y contenedor).
11. `.env`: SOLO nombres de claves leídos (`ADMIN_INITIAL_USERNAME`,
    `ADMIN_INITIAL_PASSWORD`, …) para saber de dónde leer en runtime.

Marco de política: E8.58 («sin E2E navegado») fue la REGLA INTERNA de la
suite de C8 (frontera de 6 ficheros Vitest + smokes curl). C9 es el corte
que la levanta por decisión ratificada del usuario: Playwright vive por
`pip` en `~/.local/lib/python3.14/site-packages` + `~/.cache/ms-playwright`
(chromium-headless-shell) — NO entra en `frontend/package.json` ni en el
lockfile (QP-8.54 queda intacto) y los scripts van en `scripts/`, fuera de
la frontera Vitest de C8. Regla dura ratificada: **el script no declara PASS
sin haber abierto y observado la pantalla real** (navegación + asertos
semánticos sobre el DOM vivo + screenshot de evidencia).

## B. El set EXACTO de las 14 parejas MANUAL-Q23 (fuentes qa-plan §3 + qa-report + spec)

El qa-plan §4 contaba 15 [MANUAL-Q23] (incluía QP-8.26); el qa-report
RECLASIFICA QP-8.26 como PASS [S] por absorción (la captura 400
code/field) y deja su banner visual pendiente. La 2.ª ronda de smoke
(commit `dd26fa9`, E8.27 URL_BATCH, detalle SIN summary, 403 evil y 401
C10-real) ya automatizó pila. **Las 14 MANUAL-PENDIENTE (demo C9) son
QP-8.1, 8.2, 8.4, 8.5, 8.8, 8.9, 8.10, 8.12, 8.13, 8.22, 8.29, 8.32, 8.35,
8.40** (cuenta del qa-report: 59 = 37 PASS + 5 con matiz + 2 parcial + 1
hueco + 14 MANUAL). Además quedan mitades VISUALES de duales
(`→ demo C9` en el qa-report) que el mismo arnés puede observar de regalo
(sin coste extra de infra): ver §0-bis.

| QP | E8.n (nombre spec) | Condición de aceptación LITERAL | Pareja pila ya cubierta |
|---|---|---|---|
| QP-8.1 | E8.1 `nueveRutasRegistradas1a1` | navegación secuencial a `/login`(→redirect), `/`(→`/logs`, SIN pantalla propia), `/logs`, `/logs/:id`, `/proyectos`, `/proyectos/nuevo`, `/proyectos/:id`, `/proyectos/:id/editar` y ruta inexistente: cada una renderiza SU pantalla, sin 404 de servidor (solo `*` del cliente pinta 404 del panel) | E8.6/E8.51 PASS [S] (fallback SPA + assets absolutos) |
| QP-8.2 | E8.2 `guardSinTokenRedirigeConDestino` | sin JWT: abrir `/proyectos/:id` → el guard consulta `GET /panel/me` (401) y aterriza en `/login` con la vista destino RECORDADA (pathname+search); durante la resolución NO se renderiza la vista protegida | `/me` sin token → 401 ABSORBIDO (E8.19/E8.48 PASS [S]) |
| QP-8.4 | E8.4 `loginConSesionRedirige` | con token vigente: visitar `/login` → `GET /me` 200 ⇒ redirige a `/` (→`/logs`), formulario NO se muestra; con token y `/me` 401 ⇒ token DESCARTADO y formulario normal | mitad REST absorbida (E8-auth PASS [S]) |
| QP-8.5 | E8.5 `rutaDesconocida404Pantalla` | con sesión: `/noexiste` → pantalla 404 del PANEL (jamás vacía ni redirect silencioso); sin sesión → `/login` con destino guardado | pareja E8.6 PASS |
| QP-8.8 | E8.8 `loginCredencialesInvalidasInline` | 401 `invalid_credentials` → mensaje INLINE **"Usuario o contraseña incorrectos"** bajo el formulario; la vista NO se cierra, NO se revela el campo culpable, NO hay redirect (única excepción al interceptor §5) | MAY smoke: 401 REST en E8-auth |
| QP-8.9 | E8.9 `dobleSubmitBloqueado` | doble click rápido en Entrar → durante la petición el botón muestra **"Enviando…"** y está deshabilitado; el cliente emite UN SOLO `POST /auth/login` | hueco declarado (estado de componente) |
| QP-8.10 | E8.10 `restauroSesionAntesDeRenderizar` | F5 en `/logs` con token vivo → `GET /me` ANTES de renderizar la vista protegida (SIN flash de login); username en la barra; storage bloqueado → token en memoria + todas las llamadas Bearer, panel funciona (sin F5-persist) | mitad REST del restauro absorbida (E8.7) |
| QP-8.12 | E8.12 `logoutLocalSinRed` | pulsar "Cerrar sesión" → token BORRADO del storage, WS cerrado, redirect a `/login`; NINGUNA llamada de red de logout (no existe endpoint) | hueco (hook) |
| QP-8.13 | E8.13 `jwtSoloSessionStorage` | token+`expires_at` SOLO en sessionStorage del panel, claves `projectlog.panel.token`/`projectlog.panel.expires_at`; `localStorage` y `document.cookie` NO contienen nada del JWT (D4/higiene C7) | higiene por grep (T8.67) |
| QP-8.22 | E8.22 `cambioFiltroReiniciaPaginaYsetFilters` | vista viva con cursor avanzado: cambiar filtro (o Reiniciar) → cursor REINICIADO a 1ª página, refetch `GET /logs` con los nuevos filtros y `set_filters` REENVIADO (6 claves, nulls donde no aplica — S8-8); Reiniciar ⇒ query string VACÍA y `GET /logs` sin parámetros | pines absorbidos: shape set_filters (QP-8.27 [L]), cursor reiniciado (QP-8.24 [L]+[S]) |
| QP-8.29 | E8.29 `indicadorSoloEnLogsEstados` | en `/logs` con WS abierto: **"● Conectado"** (verde) en la barra; socket cerrado en caliente: **"○ Desconectado"** + banner **"Streaming inactivo: los datos en pantalla no se actualizan en vivo"** y la lista sigue por REST; en reconexión: **"Reconectando (intento N)…"** con N creciente; navegando a `/proyectos`: indicador DESAPARECE y el WS se cierra (S8-4/decisión C) | hueco (estados del hook); pareja E8.30/E8.31 [S] |
| QP-8.32 | E8.32 `estadosDiferenciadosNoVaciosPorError` | en `/logs`: cargando→esqueleto; filtro que no casan nada→**"No hay logs que cumplan los filtros"** + sugerencia **"Prueba a reiniciar filtros o amplía el rango temporal"**; error 400/500→banner con el `code` y la LISTA CONSERVADA; NUNCA un 404/error se muestra como vacío ni viceversa (§4/§5) | estados §4 verificados indirectamente por API en el guion |
| QP-8.35 | E8.35 `volverConservaFiltrosYPagina` | `/logs` con filtros en URL y N páginas acumuladas: abrir detalle y pulsar **Volver** → MISMA query string y MISMAS filas acumuladas, sin refetch desde el principio. POST-PARCHE (D-C8-8): detalle = RUTA HERMANA `/logs/:id`; los filtros viajan en la query del enlace al detalle y del Volver; el ACUMULADO viaja en store EN MEMORIA `storeListado.ts` (consume una única vez si la query coincide; carga directa/F5 ⇒ limpia). NOTA: la maqueta 03 con Outlet está SUPERADA por D-C8-8 (matiz a citar en la ficha del 07, no reescribir) | E8.35 acumulado keyset: 5 tests Vitest + qa-log (commit `f3c7480`); ruta: `05dd7df` |
| QP-8.40 | E8.40 `estadoVacioLegitimoConCTA` | primer uso (SIN proyectos) en `/proyectos` → **"No hay proyectos todavía"** + CTA **"Nuevo proyecto"**; estado vacío legítimo ≠ error | hueco |

### §0-bis Mitades VISUALES de duales que el mismo arnés observa de regalo

(fuente qa-report, columna «→ demo C9»; el arnés las captura con el mismo
login/semilla SIN casos nuevos de infraestructura):

- **QP-8.11** banner literal **"Tu sesión ha expirado (8 h): inicia sesión de
  nuevo"** al 401 caliente + re-logueo devuelve a `/logs` con los filtros.
- **QP-8.26** banner rojo del 400 con `code` literal (`filter_from_to_inverted`
  SIN field; `filter_invalid_value` CON field=`level`) y lista conservada.
- **QP-8.27** banda **"● En vivo"** con el grupo del lote al comienzo en el
  orden del lote, sin refresco.
- **QP-8.31** (variante): banner "Sesión expirada: vuelve a iniciar sesión" +
  redirect si el handshake WS es 401 pre-upgrade.
- **QP-8.34** detalle: bloques literales "Exception y stacktrace" (`<pre>` mono
  + Copiar) y "Metadata (jsonb)" (JSON formateado) SOLO si no-nulos; ocultos
  si nulos; cabecera con id mono/level badge/environment/proyecto/timestamp+/
  received_at UTC/source/correlation_id copiable; message.
- **QP-8.36** 404 detalle → **"Este registro no existe (o fue eliminado)"** +
  Volver (jamás vacío/esqueleto infinito).
- **QP-8.37** modal 8b: sentencia EXACTA `DELETE /logs/<id> · header
  X-Confirm-Log-Id: <id>` con el id real; trim inicio/final aceptado; botón
  rojo deshabilitado (bg-red-300) → habilitado (bg-red-700) solo con
  coincidencia.
- **QP-8.38/DEV-C8-4**: confirmar 8b HOY recibe **401 real** (denyAll de `/error`,
  cadena-3) → interceptor → logout + redirect a `/login` (NO banner dentro del
  modal, NO "eliminado" fingido). Es el comportamiento REAL documentado — se
  pina ESTE, no el 405/404 del plan.
- **QP-8.39** `/proyectos` = maqueta 04: tarjetas con STATUS "Activo"|"Archivado",
  name, description, `api_key_prefix` + `has_key`, SIN bloque de resumen.
- **QP-8.42** ficha `/proyectos/:id` = maqueta 05: bloque "API key de ingesta"
  con prefix mono `plk_…`, has_key sí/no; 404 → "Este proyecto no existe (o fue
  eliminado)" + Volver.
- **QP-8.43** regenerate → modal de REVELACIÓN ÚNICA: key `plk_<43 Base64url>`
  + Copiar + aviso literal "No se volverá a mostrar; cópiala ahora"; tras
  cerrar, SOLO prefix + `has_key: true`. (HIGIENE: el screenshot del modal
  abierto debe REDACTAR la key — ver §F.)
- **QP-8.45** formulario flujo 2 completo vía UI: 201 → ficha con aviso
  **"Proyecto creado; genera su API key para que las apps puedan ingestar"**;
  409 → inline **"Ya existe un proyecto con ese nombre"** con datos
  conservados; 400 → errores por campo.
- **QP-8.47** zona destructiva de la ficha: modales 8a ("Eliminar
  definitivamente", exige NOMBRE EXACTO — V10) y 8c ("Purgar logs", exige el
  nombre), botón deshabilitado hasta coincidencia con trim.

## C. Automatización por pareja (Playwright headless Python 1.64.1, sync API)

Arnés común (patrón del repro `/tmp/qa_c9/repro_detalle.py`): `chromium.launch()`
headless → login por UI real (nunca setear token a mano salvo en variantes de
E8.4/E8.11) → utilidades: `page.on("console")`, `page.on("pageerror")`,
`page.on("request")`/`"response"` (contadores/registro por URL,
filtros `panel/logs|projects|me|auth/login`), `page.on("websocket")`
(ws.on("framesent") = cliente→servidor: para pinar `set_filters`; verificar
el nombre EXACTO del evento en la versión instalada — `framesent`/
`framereceived`), `screenshot()` a `.atl/sdd/C9/`, y snapshot semántico =
asertos de TEXTOS LITERALES + roles + estructura sobre el DOM vivo
(`get_by_text`, `locator("...").count()`, `inner_text()` del main). Veredictos
`E8.n: PASS | FAIL | BLOQUEADO (detalle)` con exit 0/1/2 como el canon de
smokes; NINGÚN PASS sin haber abierto la pantalla.

| Pareja | Automatizable | Técnica concreta |
|---|---|---|
| QP-8.1 (E8.1) | SÍ, 100% | login por UI → `goto` a cada ruta; aserto: la respuesta de navegación es el index.html (SPA fallback — el smoke [S] ya pina el 404 de servidor imposible); pantallas por texto distintivo (Logs: "Logs"+filtros; detalle `#id`+FATAL; proyectos "Proyectos"; nuevo/editar: formulario con label name+description; 404 panel). `/` ⇒ `wait_for_url("**/logs")` y h1 "Logs" (sin pantalla propia). Screenshot por pantalla. Rutas = las 8 de AppFlow §1 + `*`. NOTA post-parche: `/logs/:id` es HERMANA bajo el mismo padre Protegidas (TopBar+guard presentes) |
| QP-8.2 (E8.2) | SÍ, 100% | contexto NUEVO (storage limpio — `b.new_context()` recién creado) → `goto /proyectos/99999999` → asertos: (a) listener de response captura `GET /panel/me` con 401; (b) URL final `/login?…` con la vista destino codificada (pathname+search — pinar el parámetro EXACTO que la SPA usa, cosechado en runtime); (c) durante la espera NO aparece el contenido protegido: `expect` del formulario login visible y AUSENCIA del título de proyecto; (d) continuar el flujo: login → aterriza de vuelta en `/proyectos/99999999` (destino cumplido). Screenshot del login con destino |
| QP-8.4 (E8.4) | SÍ, 100% | tras login feliz → `goto /login` ⇒ redirige a `/logs`, formulario ausente. Variante `/me` 401: `page.evaluate` pone en sessionStorage `projectlog.panel.token` BASURA (string corrupto, jamás un JWT real de nadie — y jamás impreso) → `goto /login` → formulario normal + token descartado (clave de storage vacía tras el intento, comprobado por evaluate devolviendo BOOLEANO) |
| QP-8.5 (E8.5) | SÍ, 100% | con sesión: `goto /noexiste` → pantalla 404 del panel (texto/pantalla cosechado en vivo — la maqueta no lo describe; pinar el literal REAL renderizado) + URL permanece; navegación con status 200 (fallback). Sin sesión (contexto nuevo): `goto /noexiste` → `wait_for_url("**/login")` con destino. NUNCA vacío: aserto de longitud de `main` y de que hay texto de 404 |
| QP-8.8 (E8.8) | SÍ, 100% | `/login`, credenciales MALAS (string fijo no-secret, p. ej. `qa-c9-bad-pass`), submit con `expect_response` de `POST /panel/auth/login` (401) → asertos: texto EXACTO "Usuario o contraseña incorrectos" visible bajo el formulario; `page.url` sigue en `/login`; vista no desmontada. Screenshot del banner inline (SIN credenciales visibles — limpiar campos o capturar tras el error) |
| QP-8.9 (E8.9) | SÍ, 100% | `page.route("**/panel/auth/login", ...)` con `route.continue_()` RETRASADO (~600 ms) para dar ventana; contador de requests POST en el handler; doble `click()` rápido sobre Entrar → `expect(button).to_be_disabled()` + texto EXACTO "Enviando…"; tras resolver: contador de POST == 1. Screenshot del estado de carga |
| QP-8.10 (E8.10) | SÍ con matiz | variante A (restauro): sesión activa → nueva página del mismo contexto → `goto /logs` (equivale a recarga con storage vivo) → listener registra que la llamada a `/panel/me` ocurre antes del primer render protegido; capturar el orden temporal: registrar `t_me` (response) y `t_render` (primer `table` detectado con `wait_for_selector`) y asertar `t_me <= t_render` + username en la barra + formulario login JAMÁS visible (poll con `locator(...).count()==0` en intervalos cortos durante la carga). MATIZ: la ausencia de «flash» es un proxy temporal, no una prueba por-frame — residual posible (ver §E). Variante B (storage bloqueado): `page.add_init_script` que sustituye el acceso a `window.sessionStorage` por un objeto que LANZA (try/catch del hook lo absorbe) → aserto: `/logs` funciona, `/me` 200 recogido, tabla con filas (Bearer viaja). Riesgo de fragilidad en la emulación — flag BLOQUEADO-anotada si la emulación no resulta, NO falso PASS |
| QP-8.12 (E8.12) | SÍ, 100% | en `/logs`: registrar TODOS los requests de red; click "Cerrar sesión" → asertos: URL→/login; evaluate booleans: NO existe `projectlog.panel.token`/`expires_at` en sessionStorage NI en localStorage; NINGÚN request con patrón logout (grep del registro capturado); NINGÚN handshake WS post-click (`page.on("websocket")` no dispara de nuevo). Screenshot del login final |
| QP-8.13 (E8.13) | SÍ, 100% | tras login: `page.evaluate` que INSPECCIONA y devuelve solo BOOLEANOS/KEYS (nunca valores): sessionStorage contiene las claves `projectlog.panel.token`+`projectlog.panel.expires_at` (y el valor del token matchea internamente `^eyJ` SIN imprimirse); `Object.keys(localStorage)` no contiene ninguna clave JWT; `document.cookie` no contiene `projectlog.panel` ni `eyJ` (comprobar en evaluate, devolver booleano). Sin screenshot necesario; evidencia = salida estructurada booleana |
| QP-8.22 (E8.22) | SÍ, 100%（ojo WS） | en `/logs` del proyecto C9 con página avanzada (sembrada — §F): listener de websocket → ws.on(framesent) cuenta payload que contiene `set_filters`/`project_id` (S16: los frames van a log con el token… NO — el token va por query del handshake, jamás en frame; los frames son set_filters seguros, pero igualmente el logging de frames se limita a type/longitud); cambiar filtro `text=<needle>` → asertos: URL refleja el filtro; UN `GET /panel/logs` nuevo con el needle (response listener); UN `set_filters` nuevo con 6 claves (framesent), cursor reiniciado (la petición NO lleva `cursor`); Reiniciar ⇒ query vacía y `GET /panel/logs` sin parámetros. Nada de recarga de página (`page.url` estable) |
| QP-8.29 (E8.29) | SÍ, 100% (destructivo controlado) | a) en `/logs`: indicador verde con texto Conectado visible en la barra (literal cosechado en vivo; mockup: "Conectado" + punto; spec: "● Conectado" — pinar ambos, el REAL no el de la maqueta); b) `podman compose stop backend` (subprocess desde el guion, SOLO el servicio `backend`, JAMÁS `down -v`) → `expect` de "Desconectado"/"Reconectando (intento N)…" con N creciente capturado por polling del texto + banner literal "Streaming inactivo: los datos en pantalla no se actualizan en vivo" + screenshot; c) navegar a `/proyectos` → indicador AUSENTE (y sin banner); d) restaurar backend `up -d backend` + `finally` con bucle de health (/actuator/health 200) y timeout de emergencia — SIEMPRE restaurado |
| QP-8.32 (E8.32) | SÍ, 100% | a) esqueleto: `page.route("**/panel/logs")` retrasa la primera petición → expect del esqueleto (elemento/animación cosechado en vivo) + screenshot; b) vacío por filtro: filtro `text=qa-c9-vacio-<ts>-noexiste` → literales "No hay logs que cumplan los filtros" + "Prueba a reiniciar filtros o amplía el rango temporal"; c) error 400: navegar directamente a `/logs?from=…&to=…` con from>to (query manipulada por URL — D-C8-6 la hace fuente única, sin pasar por los selects) → banner con `code: filter_from_to_inverted` visible (literal cosechado) + la lista anterior CONSERVADA (comparar filas de la tabla antes/después por id) + esqueleto/vacío AUSENTES mientras el banner está presente |
| QP-8.35 (E8.35) | SÍ, 100% | con acumulado sembrado (§F: ≥60 logs → 50 + Cargar más ≥10): capturar nº filas, id de la primera fila y query; click en el enlace/botón Detalle del needle → URL `/logs/<id>?…`(query intacta en la URL del detalle — D-C8-8); click Volver → asertos: query idéntica, nº filas idéntico, id 1.ª fila idéntico, `page.on("response")` demuestra que NO hubo `GET /panel/logs` nuevo al montar (skip del 1.er fetch del store), y dedup no duplica; recarga dura F5 en `/logs` ⇒ acumulado limpio (arranca con 50 y next_cursor no-null — comportamiento post-parche, NO lo del spec anidado) |
| QP-8.40 (E8.40) | PARCIAL — ratificar | la BD real NO puede quedar sin proyectos: los DELETE de C10 NO existen todavía (E8.38/E8.47: 401 real) ⇒ el estado "primer uso" es INALCANZABLE con datos reales. Opciones: (i) `page.route("**/panel/projects")` devolviendo `{items:[], next_cursor:null}` y observar la PANTALLA REAL renderizada con datos falsos → PASS-CON-NOTA (screen real, datos simulados — el usuario ratifica si esto valle como PASS o queda manual); (ii) BLOQUEADO-manual. La CTA "Nuevo proyecto" SÍ se navega real (→ formulario) — eso es 100% real. Recomendación de explore: (i) + nota, sin falso PASS |
| — QP-8.27 banda (regalo) | SÍ | ingesta de 3 (X-API-Key, REST) con `/logs` abierto del proyecto C9 → banda "● En vivo": 3 filas en el orden del lote, batch_id visible, tabla histórica sin alterarse, y 0 peticiones `GET /panel/logs` nuevas (sin refresco). Screenshot |
| — QP-8.11 banner 401 caliente (regalo) | SÍ | tamper de token expirado (JWT firmado con secret del .env con `exp` en el pasado — build in-process, jamás impreso) o, más simple, token basura → siguiente llamada REST (detalle o Cargar más) → banner literal "Tu sesión ha expirado (8 h): inicia sesión de nuevo" + redirect a `/login` con destino; WS cerrado (sin frames posteriores) |
| — QP-8.26 banner 400 (regalo) | SÍ | igual que QP-8.32-c; variante `filter_invalid_value` con field: URL `?level=PANICA` (valor inválido rehidratado de la query) → banner con code + campo culpable MARCADO |
| — QP-8.34 detalle bloques (regalo) | SÍ | open detalle del log sembrado CON exception+metadata (§F): bloques literales "Exception y stacktrace"/"Metadata (jsonb)" presentes con `<pre>` mono + botones Copiar; detalle de un log SIN exception/metadata → bloques AUSENTES; cabecera con los dt literales de la maqueta 03 (Level/Environment/Proyecto/Source/Timestamp (evento…)/Received_at…/Id/Correlation_id). Screenshot full-page |
| — QP-8.36 404 detalle (regalo) | SÍ | `goto /logs/99999999` → literal "Este registro no existe (o fue eliminado)" + botón Volver; jamás vacío (aserto de contenido) |
| — QP-8.37 modal 8b (regalo) | SÍ | desde el detalle real (id real del needle): abrir "Eliminar registro" → sentencia EXACTA `DELETE /logs/<id> · header X-Confirm-Log-Id: <id>` (interpolada con el id del URL); texto parcial (`…Log-Id` truncado como en la maqueta) ⇒ botón disabled con clase bg-red-300; texto exacto con espacios delante/detrás ⇒ enabled + bg-red-700 (getBoundingClientRect? no — aserto de clase computada + `is_disabled()`) |
| — QP-8.38/DEV-C8-4 confirm a 401 real (regalo) | SÍ | confirmar el modal con la sentencia correcta → response listener pina 401 (NO 405/404 — comportamiento real) → UI: SIN "eliminado"/mensaje de éxito, SIN lista corrupta; tras la 401 el interceptor REDIRIGE a `/login` (login necesitado después — ORDEN: este caso AL FINAL del arnés, tras todos los de sesión). Screenshot del momento de cierre |
| — QP-8.39/8.42 maquetas 04/05 (regalo) | SÍ | `/proyecto`s: tarjetas con "Activo"/"Archivado", name=needle, prefix (sin `plk_` completo — jamás), `has_key` sí/no visible; ficha: bloque "API key de ingesta" + prefix mono + fecha |
| — QP-8.43 revelación única (regalo) | SÍ con redacción | click Generar/Regenerar API key (SOLO del proyecto C9 — NUNCA regenerar keys de proyectos del usuario) → confirmación de consecuencia → modal V8: aviso literal "No se volverá a mostrar; cópiala ahora" + Copiar; aserto de key: `expect(locator).to_match("^plk_[A-Za-z0-9_-]{43}$")` en evaluate devolviendo BOOLEANO (valor jamás extraído/imprimido); Screenshot SOLO con la key REDACTADA (overlay/blur del tramo — o captura del modal con la línea de la key cubierta por un elemento inyectado); cerrar → ficha refrescada: prefix nuevo + has_key true y la key completa ausente del DOM (evaluate: `document.body.innerText.includes("plk_")` → solo el prefix) |
| — QP-8.45 formulario flujo 2 (regalo) | SÍ | `/proyectos/nuevo`: crear con name `qa-c9-<ts>-e45` (vía UI — doble como semilla): 201 → redirect a ficha + aviso "Proyecto creado; genera su API key para que las apps puedan ingestar"; segundo submit con el MISMO nombre → 409 → inline "Ya existe un proyecto con ese nombre" + datos conservados; name vacío → 400 por campo. Screenshot del aviso/inline |

Orden de ejecución del arnés (evitar contaminación de sesión): (1) login +
routing/guard/404 (E8.1/8.2/8.4/8.5/8.10); (2) storage/hibernación
(8.13/8.12); (3) /logs estados (8.22/8.29/8.32/8.35 — esta última con
acumulado + ingesta) + regalo 8.27/8.26/8.11; (4) proyectos (8.45 crea el
proyecto+key → 8.39/8.42/8.43/8.40); (5) detalle (regalo 8.34/8.36/8.37) y
AL FINAL el 8.38 destructivo-con-logout; el kill del backend (8.29-c y
criterio 3 del 06) en fase aparte con restauración garantizada.

## D. Los 3 criterios de la demo de aceptación Fase 1 (docs/06 §Fase-1, texto textualizado)

1. **App de prueba → POST /ingest/batch con 3 logs → aparecen como grupo en
   "● En vivo" sin refresco (métrica 1 PRD).**
   Técnica: login por UI → `/logs` (indicador ● Conectado) → ingesta REST de
   UN batch de 3 con la key del proyecto C9 (`X-API-Key`, respuesta 202
   `{"accepted":3}`) → asertos: banda "● En vivo" con las 3 filas del lote EN
   ORDEN del lote, batch_id del frame visible, 0 refetch de `GET /panel/logs`
   durante la ventana (response listener), indicador sigue Conectado.
   Screenshots antes/ahora. (La "app real del homelab" se sustituye por
   ingesta REST programática desde el propio arnés — misma superficie de API.)
2. **Del dashboard (filtros ERROR + production) a ver el stacktrace del ERROR
   en el detalle en <2 min.**
   Técnica: sembrar de antemano (ingesta REST) ≥1 log ERROR de production con
   `exception`+stacktrace realista+`metadata`+correlation needle; en UI:
   seleccionar nivel ERROR y environment production (selects de /logs) →
   filtro text=<needle> si hiciera falta para acotar → click en Detalle de esa
   fila → asertos: URL `/logs/<id>`, bloque "Exception y stacktrace" con el
   contenido EXACTO sembrado (needle del stacktrace), correlation_id copiable;
   CRONÓMETRO: `time.monotonic()` desde el submit del filtro hasta la
   visibilidad del `<pre>` del stacktrace — aserto de duración < 120 s
   (quedará ~segundos). Screenshot full-page del detalle.
3. **Kill del backend → banner + reconecta con backoff; al volver, sin
   duplicados y sin replay.**
   Técnica: con `/logs` vivo (set_filters ya enviado — contado) →
   `podman compose stop backend` (¡servicio `backend`, contenedor
   `project_log_backend_1`; NUNCA `down -v`, jamás tocando postgres ni
   volúmenes!) → asertos: indicador a "○ Desconectado"/"Reconectando (intentN)…"
   con N creciente + banner "Streaming inactivo: los datos en pantalla no se
   actualizan en vivo"; (durante el caído no hay ingesta posible — el backend
   también sirve REST — con lo que "sin replay" se pina por AUSENCIA: tras
   `podman compose up -d backend` + bucle `/actuator/health` hasta 200 →
   indicador "● Conectado", banner RETIRADO, set_filters REENVIADO (2.º frame
   set_filters contado), un `GET /panel/logs` de refetch de 1.ª página
   (contado), ids de filas en el DOM ÚNICOS (set de ids: len(set)==len(lista)),
   y id-set idéntico al pre-caída (delta 0 — nada nuevo entró, nada se
   duplicó ni repitió). Screenshot de cada estado.

## E. Limitaciones / riesgos headless (lo que queda 100% manual — el usuario ratifica)

1. **Percepción humana del contraste/estética global** (legibilidad real de la
   paleta, jerarquía FATAL invertido verde/rojo sobre monitor): se MITIGA con
   asertos de `getComputedStyle` (colores computados de badges/indicador/botón
   rojo — p. ej. FATAL `#7f1d1d`, verde Conectado `#16a34a` familia Tailwind)
   y el screenshot como evidencia, pero la percepción subjetiva sigue manual.
2. **Flash de login (E8.10) por frames**: el aserto es un proxy temporal
   (listener /me antes del primer selector protegido + ausencia del formulario
   en polls cortos) — no hay captura por-frame de un flash real. Residual
   manual SOLO si el usuario exige seguridad por-frame (propongo: proxy
   suficiente + demo visual del usuario).
3. **E8.40 estado vacío real**: irrealizable con BD viva (los DELETE de
   proyectos no existen en el backend — C10 pendiente). Propuesta: PASS-CON-
   NOTA con route-interception (pantalla real, payload simulado) — decide el
   usuario en el proposal.
4. **Storage bloqueado (E8.10b)**: la emulación (add_init_script que lanza)
   es frágil; si falla la emulación se anota BLOQUEADO (Q23) en esa submitad,
   jamás falso PASS.
5. **Clipboard/a11y real** (Copiar nativo, orden de foco, lectura de
   pantalla): fuera de alcance; los botones Copiar se asertan por presencia/
   habilitación, no por verificación del portapapeles (aunque
   `grant_permissions(["clipboard-read","clipboard-write"])` en Chromium
   permite, opcionalmente y si el usuario lo ratifica, leer el portapapeles
   del contexto headless — key/stacktrace redactados igualmente).
6. **Snapshot semántico ≠ screenshot-diff de píxeles**: el PASS visual nace de
   textos/roles/clases computadas sobre el DOM real + screenshot de evidencia
   para el ojo humano; NO hay comparación de píxeles contra la maqueta
   (intencionado: las maquetas usan CDN de Tailwind y datos de muestra — el
   contrato es de literales/estructura, no de render idéntico). La RATIFICACIÓN
   final de cada screenshot es del usuario en la revisión del qa-artefacto.
7. **Riesgo destructivo por diseño**: dos casos paran el backend real
   (E8.29-c, criterio 3). Obligación dura del guion: `try/finally` +
   `up -d backend` + bucle de health con timeout (p. ej. 180 s — start_period
   60 s del healthcheck) + si restaura-falla, exit 2 con "PILOTO CAÍDO" bien
   visible. JAMÁS `down -v` ni tocar postgres/volúmenes. No parar en medio de
   otro smoke.
8. **Interacción Playwright-WS**: verificar el nombre de evento exacto de
   frames para contar `set_filters` en la versión instalada (1.64.1: eventos
   de WebSocket `framesent`/`framereceived`/`close`); si el evento de frames
   enviados no resultara observable, PLAN B: contar handshakes WS +
   reconexiones (criterio de set_filters reenviado por el nº de ws abiertos ×
   reconexiones) y degradar ese sub-aserto a BLOQUEADO-anotado.
9. **Nada queda 100% manual por necesidad técnica salvo lo declarado en (1)-(3)**:
   las 14 parejas y los 3 criterios del 06 son automatizables con un residual
   de percepción humana (screenshots para ratificación) — el límite exacto lo
   fija el usuario en el gate de proposal.

## F. Datos/semillas necesarios, JWT y limpieza (higiene incl. S16)

1. **JWT admin**: en runtime leer `.env` (`ADMIN_INITIAL_USERNAME`/
   `ADMIN_INITIAL_PASSWORD`) SIN ECO (parser de dotenv en el guion, valores
   jamás en stdout ni en el fichero de explore) → `POST /api/v1/panel/auth/login`
   → `{token,…}` SOLO en memoria del proceso (o tmpdir `mktemp -d` chmod 700 +
   trap si hace falta entre procesos — canon S16 de C8 §6). Alternativa igual
   de válida: login por UI y capturar desde `sessionStorage` vía evaluate
   (booleano/valor a memoria de proceso, sin imprimir).
2. **Proyecto C9**: creado por el propio arnés en la parte de proyectos
   (E8.45-vía-UI, name `qa-c9-<ts>-e45`) — o por REST (`POST /panel/projects`)
   si el orden exige sembrar antes. NUNCA regenerar keys de proyectos
   PREEXISTENTES del usuario (regenerate invalida la key anterior).
3. **API key de ingesta**: `POST /panel/projects/{id}/api-key/regenerate` con
   el JWT → 201 `{key, prefix, created_at}`; key SOLO en memoria del proceso;
   usada para ingestar (`X-API-Key`), jamás logueada/imprimida (S16).
4. **Semillas de logs** (ingesta `POST /api/v1/ingest/batch`, needle
   `qa-c9-<ts>-<caso>` en MESSAGE — el filtro `text` matchea SOLO message,
   lección DEV-C8-3):
   - 3 logs ERROR/WARN/INFO (criterio 1 / E8.27) con correlation needle.
   - ≥1 ERROR production con `exception` (stacktrace con needle interno) +
     `metadata` JSON (criterio 2 / QP-8.34).
   - 1 log SIN exception ni metadata (bloques ocultos — QP-8.34-b).
   - ≥60 logs del needle E8.35 (50/Cargar más → acumulado ≥60 — batch ≤200
     permite 1 única request; `received_at` server-side, `timestamp` del
     payload en el pasado reciente para que caiga arriba del listado).
   - 1/2 logs para "fin de resultados" (next_cursor null) si se quiere pinar
     la variante deshabilitada.
5. **Datos que DEJA en la BD** (y limpieza): los DELETE de proyectos/logs NO
   existen todavía en el backend (C10) ⇒ NO hay limpieza posible y NO se
   intenta (jamás SQL directo, jamás down -v desde C9). Contaminación =
   un proyecto `qa-c9-<ts>-*` + sus logs con needles sufijados (no colisionan
   con nada; el próximo smoke autocontenido `down -v` los borra — igual que
   los del smoke C8). Conteo/reporte en el qa-run de C9 de lo sembrado.
   La API key activa del proyecto C9 queda activa: nota de higiene local
   (entorno de la propia dev de una sola VM — aceptable; se cita en el report).
6. **Higiene en screenshots** (S16 aplicado a evidencia visual):
   - JAMÁS capturar el formulario de login CON campos rellenados (o vaciar
     antes o capturar pre-fill/estado de error con campos limpios).
   - Modal de revelación de key: capturar con la línea de la key REDACTADA
     (elemento overlay inyectado por el propio guion justo para el shot).
   - Nunca screenshot/echo del token JWT; el nombre de usuario admin en shots
     es irrelevante (no es secreto), el password jamás.
7. **Salidas del guion**: scripts en `scripts/` (p. ej.
   `scripts/qa_c9_visual.py` o por grupos), veredictos `E8.n: PASS|FAIL|
   BLOQUEADO (detalle)` + RAW a `.atl/sdd/C9/qa-log.txt`, screenshots a
   `.atl/sdd/C9/` (prefijados `e8-xx-estado.png`), exit 0/1/2 como el canon
   del smoke C8 (PASS total / FAIL / guard-higiene-restauración). Sin
   secretos en la salida (grep post-ejecución 0 hits `plk_`/`eyJ` sobre
   qa-log, como §6 del plan de C8).