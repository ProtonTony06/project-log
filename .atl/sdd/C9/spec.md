---
project: Project_Log
change: C9
artifact: spec
last_updated: 2026-10-29
status: done-gate
gate: G1-G4 APROBADOS por el usuario 2026-10-29 (G1 PASS-CON-NOTA route-interception; G2 extras ENTRAN según listado EXACTO; G3 clipboard fuera; G4 Fase B APROBADA)
---

# Spec — C9 · QA visual automático (ARNÉS Playwright) + deploy y firewall

> Especificación normativa del corte C9 para la implementación/operación
> (sdd-design → tasks → apply → qa → verify). Fuentes CITADAS, nunca
> redefinidas: `proposal.md` C9 (GATE RATIFICADO 2026-10-29, frontmatter
> `gate:` con veredicto), `explore.md` C9 (técnicas por pareja §B/§C/§F,
> higiene §F, semillas, los 3 criterios del 06 §D), `.atl/sdd/C8/qa-plan.md`
> (filas QP-8.x — literales de contrato) y `.atl/sdd/C8/spec.md` (SOLO los
> E8.n del set ratificado), `docs/mockups/02-logs.html` y
> `docs/mockups/03-log-detail.html` (CONGELADOS — literal UI). Base HEAD
> `c144f3d` (parche post-Q23 del detalle RUTA HERMANA D-C8-8 aplicado).
>
> **C9 NO es código de producto**: Fase A es un corte de QA — los
> "requerimientos" son requerimientos DEL ARNÉS (`scripts/qa_vis_c9.py`) y de
> sus OPS de evidencia. Fase B es un corte de OPS (deploy/firewall) con
> requerimientos de OPERACIÓN, NO de implementación (esos detalles van a
> design/tasks de Fase B). Cero ficheros de producto modificados en Fase A.

## 0. Decisiones de gate ya aprobadas (resumen vinculante 2026-10-29)

| Gate | Decisión aprobada |
|---|---|
| **G1** | **E8.40 → PASS-CON-NOTA con route-interception** (opción (i)): pantalla REAL observada con payload simulado `{"items":[],"next_cursor":null}` interceptando `**/panel/projects`; la CTA "Nuevo proyecto" se navega 100% real (→ formulario). Queda anclada en R9.2.14 (E9.14) con su nota OBLIGATORIA en el veredicto. Queda descartada la opción (ii) MANUAL puro |
| **G2** | **EXTRAS RECOMENDADOS ENTRAN — límite EXACTO**: QP-8.26, 8.27, 8.34, 8.36, 8.39, 8.42, 8.45, 8.47 + 8.43 (con la key REDACTADA en el shot). **OPT-INS FUERA — CERO checks de ellos**: QP-8.11, QP-8.31, QP-8.38/DEV-C8-4. NINGÚN check fuera de este listado (§0 no-alcance: el límite es EXACTO, no se inventa) |
| **G3** | **Clipboard `grant_permissions` NO** (fuera de alcance): los botones Copiar se asertan por PRESENCIA/HABILITACIÓN, jamás por lectura del portapapeles |
| **G4** | **Fase B APROBADA dentro de C9**: deploy VM + firewall SSH LAN+8080 (GATE #11) + frío/reinicio + nginx `client_max_body_size` + smoke `docker compose` + secretos `.env` destino. Especificada como OPS acotada en §2 (§2 dominios S9-B1…S9-B6) con la restricción de lockout EXPLÍCITA (R9-B3) |

Ancladas además (reglas duras, ya ratificadas en el proposal):

- **REGLA DURA del arnés** (R9.1.4): NINGÚN PASS sin haber abierto y
  observado la pantalla real (navegación chromium headless + login por UI +
  snapshot semántico por roles/textos/clases + screenshot de evidencia por
  pareja). Jamás falso PASS.
- **Higiene S16 visual** (R9.5.2): JWT/keys NUNCA impresos ni en PNGs; login
  shots SIN campos rellenados; key de revelación V8 REDACTADA con overlay en
  el shot de QP-8.43; grep post-corrida de **0 hits `plk_`/`eyJ`** sobre el
  qa-log RAW.
- **Destructivo acotado** (R9.1.5): JAMÁS `down -v` ni tocar
  postgres/volúmenes; kill del backend SOLO con `stop`/`up -d` del servicio
  `backend` dentro de `try/finally` + bucle de health (180 s) — fase aparte.
- **Playwright FUERA de `frontend/package.json` y del lockfile** (QP-8.54 intacto): vive por `pip` (`~/.local/lib/python3.14/site-packages` +
  `~/.cache/ms-playwright`, chromium-headless-shell); solo guion Python
  local en `scripts/`.

## 0.2 Alcance y NO-alcance (frontera para design/verify)

**ES C9 (Fase A + Fase B):**

1. Arnés único `scripts/qa_vis_c9.py` (+ wrapper `.sh` MAY) que ejecuta las
   **14 parejas MANUAL-Q23** (QP-8.1/2/4/5/8/9/10/12/13/22/29/32/35/40) con
   veredicto individual y evidencia.
2. Los **3 criterios demo de la Fase 1 del 06** automatizados (E9.24–E9.26).
3. Los **9 extras visuales ratificados en G2** (E9.15–E9.23).
4. Evidencia e higiene (qa-log RAW + screenshots en `.atl/sdd/C9/`, S16,
   exit 0/1/2 canon C8) y el informe resumido en el qa-report de C9.
5. **Fase B (G4)**: deploy de la pila a la VM Proxmox, firewall GATE #11
   (SSH LAN + 8080), arranque en frío y reinicio, `client_max_body_size`,
   smoke canónico `docker compose`, secretos `.env` del destino.

**NO es C9 (explícito):**

- **Código de producto**: CERO cambios en `frontend/**` NI
  `backend/**` en Fase A (la pila no se recompila; qa_c8_smoke verde
  post-Fase A SOLO verifica). Fase B toca SOLO config de VM/firewall/nginx
  del destino — jamás lógica de producto.
- **C10**: los 3 DELETE reales (`X-Confirm-*` efectivos), `include=summary`
  y el resumen P1 — el residuo de Fase A se NOTIFICA y su limpieza real es
  C10 (jamás SQL directo desde C9).
- **Checks fuera del límite EXACTO de G2**: **cero checks** de QP-8.11
  (banner expiración 8 h), QP-8.31 (401 pre-upgrade WS — ya cubierta
  REST/WS por el smoke C7), QP-8.38/DEV-C8-4 (confirm modal 8b → 401 real →
  logout). **Tampoco QP-8.37** (modal 8b visual — no está en el listado
  G2): su capa [L] del comparador ya venció en C8. Cualquier otro QP-8.x
  fuera del listado: NO existe como check del arnés.
- **Clipboard real** (G3), dark mode (V1), rate limiting, TLS/dominio
  (TRD §23), gestión de usuarios, pantallas de ingesta (flujo 10).
- **Diff de píxeles contra las maquetas**: INTENCIONALMENTE NO (R9.5.3).
- **E2E de navegador generalizado**: el arnés es UN guion acotado al set
  ratificado de 26 checks; NO abre la puerta a suites E2E de producto
  (E8.58 sigue siendo la regla de las SUITES de C8; solo este arnés la
  levanta, por decisión explícita del usuario).

## 1. Fase A — "Ojos": arnés visual Playwright (OBLIGATORIA — lo PRIMERO)

### DOMINIO `arnes/infra` (S9-1)

- **R9.1.1** — Arnés ÚNICO `scripts/qa_vis_c9.py` (Playwright **sync API
  1.64.1**, chromium headless; patrón del repro `/tmp/qa_c9/repro_detalle.py`)
  que ejecuta el set COMPLETO en UNA corrida con **UN SOLO login de sesión**
  por UI y ORDEN SIN CONTAMINACIÓN: (1) `login` + routing/guard/404 (E9.1–E9.7);
  (2) storage/hibernación (E9.8, E9.9); (3) `/logs` estados (E9.10,
  E9.12, E9.13 + regalo E9.15/E9.16); semillas e ingesta entre (3) y (4);
  (4) proyectos, que crea el proyecto C9 y su key (E9.21 primero — flujo 2
  DOBLE como semilla —, luego E9.19/E9.20/E9.23/E9.14); (5) detalle (E9.17,
  E9.25) y **AL FINAL** la fase destructiva; kill del backend (E9.11-b/c y
  E9.26, R9.1.5) en **FASE APARTE** AL FINAL con restauración garantizada.
  El guion es idempotente por corrida (needles sufijados — R9.5.4) y
  re-corrible.
- **R9.1.2** — Login REALES por UI (inputs del formulario + botón Entrar +
  `wait_for_url("**/logs")`); el token JAMÁS se setea a mano SALVO en las
  variantes que lo exigen (E9.3/E9.7 con storage basura corrupto — NUNCA un
  JWT real de nadie ni impreso). Los secretos (`ADMIN_INITIAL_USERNAME`/
  `ADMIN_INITIAL_PASSWORD`) se leen de `.env` en runtime con parser dotenv
  en proceso — SIN ECO; la key de ingesta SOLO en memoria del proceso
  (S16/R9.5.2).
- **R9.1.3** — **Playwright FUERA del repo de producto**: nada en
  `frontend/package.json` ni lockfile (QP-8.54 intacto, `git status` de Fase
  A lo certifica); deps por pip + `~/.cache/ms-playwright`; guion en
  `scripts/` (fuera de la frontera Vitest de C8); wrapper `.sh`
  (`scripts/qa_vis_c9.sh`) MAY para env/exit.
- **R9.1.4** — **REGLA DURA (binding)**: NINGÚN veredicto PASS se declara
  sin haber (a) navegado/abierto la PANTALLA REAL con el navegador
  headless; (b) asertado snapshot SEMÁNTICO sobre el DOM vivo (textos
  LITERALES del contrato + roles + estructura, `get_by_text`/`inner_text`/
  clases computadas); (c) capturado screenshot de evidencia de esa pareja a
  `.atl/sdd/C9/`. El PASS SOLO POR `page.route` sin pantalla (o viceversa)
  es PROHIBIDO (falso PASS ⇒ defecto del arnés, FAIL/exit 2).
- **R9.1.5** — Fase destructiva acotada: el backend se para SOLO con
  `stop` del servicio `backend` (contenedor `project_log_backend…`) y se
  restaura con `up -d backend` DENTRO de `try/finally` + **bucle de health
  `/actuator/health` hasta 200 con timeout 180 s** (start_period 60 s ⇒
  ventana 180 s). `down -v` y cualquier acceso a postgres/volúmenes:
  PROHIBIDO desde C9. Restauración fallida ⇒ **exit 2 con
  "PILOTO CAÍDO"** en el log. No correr durante otro smoke en curso.
  La caída del backend se ejecuta UNA sola vez por corrida (de la MISMA
  ventana caída/restaurada se cosechan los sub-asertos de E9.11-b/c Y el
  veredicto E9.26 — no se para dos veces por defecto; un 2.º ciclo MAY SOLO
  si la observación de un sub-estado lo exige, documentado en el qa-log).

### DOMINIO `qa-visual/parejas-q23` (S9-2) — las 14 parejas 1:1, criterio y técnica

Cada fila es un requerimiento idempotente y trazable (R9.2.n ↔ QP-8.x ↔
E8.x ↔ E9.n de §escenarios). Criterio LITERAL del qa-plan §3; técnica del
explore §C (MATIZACIÓN citada cuando aplica).

- **R9.2.1 ↔ QP-8.1 / E8.1 `nueveRutasRegistradas1a1` (→ E9.1)** — navegación
  secuencial a las 9 rutas de AppFlow §1 (`/login`(→redirect), `/`(→`/logs`
  SIN pantalla propia), `/logs`, `/logs/:id`, `/proyectos`,
  `/proyectos/nuevo`, `/proyectos/:id`, `/proyectos/:id/editar`, ruta
  inexistente): cada una renderiza SU pantalla, sin 404 de servidor (solo el
  `*` del cliente pinta 404 del panel). Técnica: aserto de respuestas de
  navegación = `index.html` (SPA fallback) + pantallas por texto
  distintivo; `/` ⇒ `wait_for_url("**/logs")` + h1 "Logs". MATIZ
  post-parche: `/logs/:id` es **RUTA HERMANA** bajo el mismo padre Protegidas
  (D-C8-8): TopBar+guard PRESENTES (a citar en la ficha del 07, no
  reescribir). Screenshot por pantalla.
- **R9.2.2 ↔ QP-8.2 / E8.2 `guardSinTokenRedirigeConDestino` (→ E9.2)** — sin
  JWT (contexto NUEVO con storage limpio), abrir `/proyectos/:id` → el guard
  consulta `GET /panel/me` (401 — listener de response) y aterriza en
  `/login` con la vista destino RECORDADA (pathname+search — pinar el
  parámetro EXACTO cosechado en runtime); durante la resolución NO se
  renderiza la vista protegida; login → regresa a `/proyectos/:id` (destino
  cumplido).
- **R9.2.3 ↔ QP-8.4 / E8.4 `loginConSesionRedirige` (→ E9.3)** — con sesión:
  `/login` ⇒ `GET /me` 200 ⇒ redirige a `/logs`, formulario AUSENTE.
  Variante: token BASURA en storage (string corrupto, jamás JWT real) +
  `/login` ⇒ formulario normal y token DESCARTADO (evaluate devuelve
  BOOLEANO de la clave vacía — valor jamás extraído).
- **R9.2.4 ↔ QP-8.5 / E8.5 `rutaDesconocida404Pantalla` (→ E9.4)** — con
  sesión: `/noexiste` ⇒ pantalla 404 del PANEL (literal REAL cosechado en
  vivo — la maqueta no lo describe; se pina lo renderizado, no se inventa),
  URL permanece, navegación 200 (fallback), contenido no vacío (longitud de
  `main` + texto 404). Sin sesión (contexto nuevo) ⇒ `/login` con destino.
- **R9.2.5 ↔ QP-8.8 / E8.8 `loginCredencialesInvalidasInline` (→ E9.5)** —
  401 `invalid_credentials` (credenciales MALAS fijas no-secretas) ⇒ mensaje
  inline EXACTO **"Usuario o contraseña incorrectos"** bajo el formulario;
  `page.url` sigue en `/login`; vista NO cerrada, campo culpable NO
  revelado, SIN redirect. Screenshot del banner SIN campos rellenados
  (R9.5.2).
- **R9.2.6 ↔ QP-8.9 / E8.9 `dobleSubmitBloqueado` (→ E9.6)** — `page.route`
  de `**/panel/auth/login` con `route.continue_()` RETRASADO (~600 ms) +
  contador de POST en el handler; doble `click()` rápido → botón
  **"Enviando…"** y deshabilitado (`expect(to_be_disabled)`), texto EXACTO;
  contador final de POST == 1.
- **R9.2.7 ↔ QP-8.10 / E8.10 `restauroSesionAntesDeRenderizar` (→ E9.7)** —
  variante A: con sesión, nueva página del mismo contexto (`goto /logs`,
  equivalente a recarga) ⇒ `/panel/me` ANTES del primer render protegido
  (t_me <= t_render del primer `table`) + username en la barra + formulario
  login JAMÁS visible (polls cortos `count()==0`). MATIZ (PASS-CON-NOTA
  declarada — §5): el aserto es proxy temporal, NO captura por-frame de un
  flash. Variante B (storage bloqueado): `add_init_script` que LANZA sobre
  `window.sessionStorage` ⇒ panel funcional con `/me` 200 y Bearer viajando.
  Si la emulación no resulta: sub-veredicto **BLOQUEADO honesto** — jamás
  falso PASS (§5).
- **R9.2.8 ↔ QP-8.12 / E8.12 `logoutLocalSinRed` (→ E9.8)** — en `/logs`,
  click "Cerrar sesión" (registro de TODOS los requests) ⇒ URL→`/login`;
  evaluate BOOLEANO: sin `projectlog.panel.token`/`expires_at` en
  sessionStorage NI localStorage; NINGÚN request patrón logout en el
  registro; NINGÚN handshake WS post-click. Screenshot del login final.
- **R9.2.9 ↔ QP-8.13 / E8.13 `jwtSoloSessionStorage` (→ E9.9)** — tras login,
  evaluate que INSPECCIONA y devuelve SOLO BOOLEANOS/KEYS (valores jamás a
  log): sessionStorage contiene `projectlog.panel.token` +
  `projectlog.panel.expires_at` (el token matchea internamente `^eyJ` sin
  imprimirse); `Object.keys(localStorage)` SIN claves JWT; `document.cookie`
  sin `projectlog.panel` ni `eyJ`. Evidencia = salida estructurada booleana
  (sin screenshot de valores).
- **R9.2.10 ↔ QP-8.22 / E8.22 `cambioFiltroReiniciaPaginaYsetFilters` (→
  E9.10)** — vista viva con cursor avanzado: cambiar filtro
  `text=<needle>` ⇒ URL refleja el filtro; UN `GET /panel/logs` nuevo con el
  needle; UN `set_filters` nuevo con **6 claves** con nulls (S8-8, contado
  por `framesent` — SIN `cursor`); Reiniciar ⇒ query de URL VACÍA y
  `GET /panel/logs` sin parámetros. Sin recarga de página (`page.url`
  estable). **MATIZ** (explore §E.8/PLAN-B): el nombre EXACTO del evento de
  frames se verifica en 1.64.1 (`framesent`/`framereceived`); si el evento
  no resultara observable, PLAN B por conteo de handshakes/reconexiones con
  ese sub-aserto degradado a **BLOQUEADO-anotado** (jamás falso PASS). El
  logging de frames se limita a `type`/longitud (S16).
- **R9.2.11 ↔ QP-8.29 / E8.29 `indicadorSoloEnLogsEstados` (→ E9.11)** —
  (a) `/logs` con WS abierto: indicador verde **"● Conectado"** visible en
  la barra — se pina el LITERAL REAL (maqueta: "Conectado"; spec:
  "● Conectado"); (b) socket cerrado en caliente (fase de kill, R9.1.5):
  **"○ Desconectado"** + banner EXACTO **"Streaming inactivo: los datos en
  pantalla no se actualizan en vivo"** + **"Reconectando (intento N)…"**
  con N creciente (polling del texto) + lista sigue por REST; (c) navegar a
  `/proyectos` ⇒ indicador AUSENTE y banner AUSENTE (S8-4/decisión C).
  Destructivo controlado — la (b) comparte la ventana de kill con E9.26.
- **R9.2.12 ↔ QP-8.32 / E8.32 `estadosDiferenciadosNoVaciosPorError` (→
  E9.12)** — (a) esqueleto: `route` retrasa la 1.ª petición de `**/panel/logs`
  ⇒ elemento/animación cosechado en vivo + screenshot; (b) vacío por
  filtro (`text=qa-c9-<ts>-vacio-noexiste`): **"No hay logs que cumplan los
  filtros"** + **"Prueba a reiniciar filtros o amplía el rango temporal"**;
  (c) error 400: `goto /logs?from=…&to=…` con from>to (D-C8-6: la query es
  fuente única) ⇒ banner con `code: filter_from_to_inverted` visible +
  LISTA PREVIA CONSERVADA (filas por id antes/después) + esqueleto/vacío
  AUSENTES mientras el banner está. NUNCA error mostrado como vacío ni
  viceversa (§4/§5).
- **R9.2.13 ↔ QP-8.35 / E8.35 `volverConservaFiltrosYPagina` (→ E9.13)** —
  con acumulado sembrado (≥60 logs del needle; 50 + "Cargar más" ≥10):
  capturar nº filas, id de 1.ª fila y query; click Detalle del needle ⇒ URL
  `/logs/<id>?…` con la query INTACTA en el detalle; click **Volver** ⇒
  query idéntica + nº filas idéntico + id de 1.ª fila idéntico + `page.on
  ("response")` demuestra que NO hubo `GET /panel/logs` nuevo al montar
  (skip del 1.er fetch del store en memoria) + dedup sin duplicados.
  Recarga F5 en `/logs` ⇒ acumulado LIMPIO (arranca 50, next_cursor
  no-null). **CONTRA EL POST-PARCHE D-C8-8** (RUTA HERMANA + store
  `storeListado.ts` en memoria, consumo único al montar si la query
  coincide): la maqueta 03 con Outlet está SUPERADA — el aserto pina el
  comportamiento post-parche (5 tests Vitest + qa-log del commit `f3c7480`
  son los pines [L] previos; aquí se pinnea la mitad NAVEGADA).
- **R9.2.14 ↔ QP-8.40 / E8.40 `estadoVacioLegitimoConCTA` (→ E9.14)** —
  **DECISIÓN G1**: `page.route("**/panel/projects")` →
  `{"items":[],"next_cursor":null}` y se OBSERVA la pantalla real ⇒
  **PASS-CON-NOTA** con la nota OBLIGATORIA en el veredicto ("pantalla real,
  datos simulados — estado 'primer uso' inalcanzable con datos vivos: los
  DELETE de C10 no existen"). La CTA **"Nuevo proyecto"** se navega REAL
  (→ formulario — 100% real). NUNCA un PASS limpio sin nota; la opción
  MANUAL puro quedó descartada en G1.

### DOMINIO `qa-visual/extras-g2` (S9-3) — los 9 ratificados (límite EXACTO de G2)

Mitades VISUALES de duales que el arnés observa con el mismo login/semilla,
SIN infra nueva. **Cualquier otro QP-8.x NO existe como check** (G2).

- **R9.3.1 ↔ QP-8.26 / E8.26 `banner400CampoCulpable` (→ E9.15)** — banner
  rojo del 400 con `code` literal: `filter_from_to_inverted` SIN field (sin
  marca de campo) y variante `filter_invalid_value` CON `field: "level"`
  (URL `?level=PANICA` rehidratada — campo culpable MARCADO); lista previa
  conservada.
- **R9.3.2 ↔ QP-8.27 / E8.27 `bandaEnVivoGrupoCoherente` (→ E9.16)** —
  ingesta REST de UN batch de 3 con `/logs` abierto ⇒ banda **"● En vivo"**
  con las 3 filas EN EL ORDEN DEL LOTE, `batch_id` `b_+uuidv4` visible,
  tabla histórica sin alterarse y **0 peticiones `GET /panel/logs`** en la
  ventana (sin refresco — response listener).
- **R9.3.3 ↔ QP-8.34 / E8.34 (→ E9.17)** — detalle del log sembrado CON
  exception+metadata (§semillas): bloques literales **"Exception y
  stacktrace"** (`<pre>` mono + Copiar) y **"Metadata (jsonb)"** (JSON
  formateado + Copiar) PRESENTES; log SIN ambos ⇒ bloques AUSENTES (zonas
  ocultas, no vacías); cabecera con los dt literales de la maqueta 03
  (Level/Environment/Proyecto/Source/Timestamp…/Received_at…/Id/
  Correlation_id copiable) + message.
- **R9.3.4 ↔ QP-8.36 / E8.36 `cuarentaYcuatroDetalleNoVacío` (→ E9.18)** —
  `goto /logs/99999999` ⇒ literal **"Este registro no existe (o fue
  eliminado)"** + botón Volver; jamás vacío/esqueleto infinito (aserto de
  contenido).
- **R9.3.5 ↔ QP-8.39 / E8.39 `listaProyectosMaqueta04` (→ E9.19)** —
  `/proyectos` = maqueta 04: tarjetas con STATUS **"Activo"|"Archivado"**,
  name (needle del proyecto C9), description, `api_key_prefix` (JAMÁS la
  key completa) + `has_key` sí/no, SIN bloque de resumen P1.
- **R9.3.6 ↔ QP-8.42 / E8.42 `fichaMaqueta05ConBloqueKey` (→ E9.20)** —
  ficha `/proyectos/:id` = maqueta 05: bloque **"API key de ingesta"** con
  prefix mono `plk_…` (truncado, jamás completo), `has_key`, fecha;
  variante 404 ⇒ "Este proyecto no existe (o fue eliminado)" + Volver.
- **R9.3.7 ↔ QP-8.45 / E8.45 `nuevoProyecto201Y409` (→ E9.21)** — formulario
  flujo 2 COMPLETO vía UI: crea con name `qa-c9-<ts>-e45` (**DOBLE como
  semilla del proyecto C9** — R9.5.4) ⇒ 201 → redirect a ficha + aviso
  EXACTO **"Proyecto creado; genera su API key para que las apps puedan
  ingestar"**; 2.º submit con el MISMO nombre ⇒ 409 ⇒ inline **"Ya existe
  un proyecto con ese nombre"** con datos conservados; name vacío ⇒ 400 con
  errores POR CAMPO.
- **R9.3.8 ↔ QP-8.47 / E8.47 `modales8a8cConstruidosNoOperativos` (→ E9.22)** —
  zona destructiva de la ficha: modales **8a** ("Eliminar definitivamente"
  — exige el NOMBRE EXACTO, V10) y **8c** ("Purgar logs" — proyecto+key
  sobreviven, exige el nombre): botón deshabilitado hasta coincidencia con
  trim (clase computada + `is_disabled()`), asertos de habilitación — **SIN
  petición DELETE** (los DELETE no se envían desde el arnés; el comparador
  [L] ya venció en C8).
- **R9.3.9 ↔ QP-8.43 / E8.43 `regenerate201RevelacionUnica` (→ E9.23)** —
  Generar/Regenerar API key **SOLO del proyecto qa-c9 creado por el arnés**
  (NUNCA regenerar keys de proyectos preexistentes del usuario — R9.5.4):
  confirmación de consecuencia → modal de REVELACIÓN ÚNICA con la key
  matcheando internamente `^plk_[A-Za-z0-9_-]{43}$` (evaluate BOOLEANO —
  valor jamás extraído/imprimido) + aviso EXACTO **"No se volverá a
  mostrar; cópiala ahora"** + Copiar (presencia/habilitación — G3).
  **Screenshot SOLO con la key REDACTADA** (overlay/blur inyectado por el
  guion justo para el shot — R9.5.2). Tras cerrar: ficha refrescada con
  prefix nuevo + `has_key: true` y la key completa AUSENTE del DOM
  (`document.body.innerText.includes("plk_")` → solo el prefix).

### DOMINIO `qa-visual/demo-06` (S9-4) — los 3 criterios de la demo de aceptación Fase 1

- **R9.4.1 (→ E9.24) — Criterio 1** (docs/06 Fase 1 / métrica 1 PRD): con
  `/logs` abierto (indicador ● Conectado, `set_filters` ya enviado), UNA
  ingesta REST `POST /api/v1/ingest/batch` de 3 logs con la key del proyecto
  C9 (cabecera `X-API-Key` — respuesta 202 `{"accepted":3}`) ⇒ **la app real
  del homelab se SUSTITUYE por ingesta programática desde el propio arnés**
  (misma superficie `POST /ingest/batch`, literal canon del criterio) ⇒ las
  3 filas aparecen como GRUPO en la banda "● En vivo" en el orden del lote
  SIN refresco (0 refetch de `GET /panel/logs` en la ventana) e indicador
  sigue Conectado. Screenshots antes/después.
- **R9.4.2 (→ E9.25) — Criterio 2**: de filtros a detalle < 120 s: sembrar de
  antemano ≥1 log ERROR de production con `exception` (stacktrace con needle
  interno) + `metadata` + correlation needle; en UI: seleccionar level
  ERROR y environment production (selects de `/logs`) + acotar con
  `text=<needle>` si hiciera falta → click Detalle de esa fila ⇒ URL
  `/logs/<id>`, bloque "Exception y stacktrace" con el contenido EXACTO
  sembrado (needle del stacktrace), `correlation_id` mono copiable;
  **CRONÓMETRO** `time.monotonic()` desde el submit del filtro hasta la
  visibilidad del `<pre>` del stacktrace ⇒ aserto **< 120 s** (quedará
  ~segundos). Screenshot full-page del detalle.
- **R9.4.3 (→ E9.26) — Criterio 3**: kill del backend destructivo LOCAL
  (comparte la fase de kill, R9.1.5): `stop` del servicio `backend` ⇒
  banner + "○ Desconectado"/"Reconectando (intento N)…" (N creciente);
  restauración `up -d backend` + bucle health (180 s) ⇒ indicador "●
  Conectado", banner RETIRADO, `set_filters` REENVIADO (2.º frame contado),
  UN refetch de 1.ª página (`GET /panel/logs` contado), ids en el DOM
  **ÚNICOS** (`len(set)==len(lista)`) e id-set idéntico al pre-caída
  (**delta 0** — sin duplicados ni replay; durante la caída no hay ingesta
  posible: el backend también sirve REST — "sin replay" se pina por
  AUSENCIA). Screenshot de cada estado. Si la restauración falla:
  **exit 2 "PILOTO CAÍDO"** (R9.1.5).

### DOMINIO `qa-visual/evidencia-higiene` (S9-5)

- **R9.5.1** — Evidencia por pareja: `qa-log.txt` RAW a `.atl/sdd/C9/`
  (stdout del guion) + **un screenshot por pareja** (≈25–35, prefijados
  `e8-xx-estado.png`) + el set de sembrados reportado. Snapshot semántico =
  asertos sobre el DOM vivo; las screenshots son EVIDENCIA para el ojo
  humano.
- **R9.5.2** — **Higiene S16 visual (hard)**: JWT y key NUNCA impresos
  (stdout/qa-log/PNG) ni en atributos de DOM capturados fuera de su
  ventana; screenshots de login SIEMPRE sin campos rellenados (vaciar antes
  o capturar estado de error con campos limpios); shot del modal de
  revelación QP-8.43 con la línea de la key **REDACTADA** por overlay
  inyectado para el shot; frames WS logueados solo por `type`/longitud;
  `document.body.innerText.includes("plk_")` tras cerrar el modal ⇒ solo el
  prefix. **Grep post-corrida sobre el RAW: 0 hits `plk_`/`eyJ`** (canón
  §6 de C8) — fallo de higiene ⇒ exit 2 (S16 duro).
- **R9.5.3** — **Snapshot SEMÁNTICO ≠ diff de píxeles** (decisión
  INTENCIONADA): el PASS visual nace de textos literales + roles + clases
  computadas (`getComputedStyle` de badges/indicador/botón rojo — p. ej.
  FATAL `#7f1d1d`, verde Conectado `#16a34a`, `bg-red-300`→`bg-red-700`);
  NO hay comparación de render contra las maquetas (usan CDN de Tailwind y
  datos de muestra — el contrato es de literales/estructura). La
  RATIFICACIÓN final de cada screenshot es del USUARIO (A6 — §5/done-when).
- **R9.5.4** — Semillas idempotentes: needle `qa-c9-<ts>-<caso>` en MESSAGE
  (el filtro `text` matchea SOLO message — lección DEV-C8-3) — no colisionan
  con nada; sembrado por ingesta REST: 3 logs ERROR/WARN/INFO (criterio 1);
  ≥1 ERROR production con exception+metadata (criterio 2/QP-8.34); 1 log SIN
  exception ni metadata (QP-8.34-b); ≥60 logs del needle de E9.13 (batch ≤200
  ⇒ 1 request única; `timestamp` del payload en el pasado reciente para caer
  arriba). El proyecto C9 se crea por UI en E9.21 (`qa-c9-<ts>-e45`) y su key
  por `POST /panel/projects/{id}/api-key/regenerate` (key SOLO en memoria).
  NUNCA regenerar keys de proyectos preexistentes del usuario.
- **R9.5.5** — **Residuo notificado** (no limpiado): NO hay limpieza posible
  (los DELETE de C10 no existen) y NO se intenta: jamás SQL directo, jamás
  `down -v` desde C9. El qa-run REPORTA el residuo: proyecto
  `qa-c9-<ts>-*` + sus logs con needles + la key activa del proyecto C9
  (nota de higiene local del entorno dev de una sola VM — aceptable, citada
  en el qa-report).
- **R9.5.6** — Regresión mínima al cierre de Fase A: `scripts/qa_c8_smoke.sh`
  en VERDE tras la Fase A (la pila NO cambia — la corrida solo la
  verifica; su `down -v` es SU privilegio canónico y barre además la siembra
  de C9, igual que la de C8). Cero cambios de suite/líneas de producto que
  verificar.

### DOMINIO `qa-visual/veredicto` (S9-6)

- **R9.6.1** — Veredicto por ID con el formato canón de C8:
  `E8.n: PASS | PASS-CON-NOTA (motivo) | FAIL | BLOQUEADO (detalle) |
  Manual` — individual por pareja y por criterio demo; **cero PASS sin
  pantalla real observada** (R9.1.4 — un PASS sin pantalla es un DEFECTO
  del arnés).
- **R9.6.2** — **Exit codes canon de smokes**: **0** = todo PASS (las
  PASS-CON-NOTA ratificadas — G1, E9.7 variante A — cuentan como PASS con
  su nota impresa); **1** = cualquier FAIL de un ID; **2** = guard de
  higiene S16 fallada, restauración del backend fallida ("PILOTO CAÍDO"),
  o pila/entorno no disponible al arranque (jamás falso PASS).
- **R9.6.3** — **FAIL → loop-back** (canón C5–C8): ante cualquier FAIL se
  REPORTA el QP-8.x afectado en el qa-report y se devuelve a
  apply/design POR ENCIMA del qa-report; JAMÁS auto-arreglo/maquillaje
  dentro del guion. Los PASS-CON-NOTA y BLOQUEADO-anotados nunca son FAIL.
- **R9.6.4** — **BLOQUEADO honesto**: cualquier emulación que no resulte
  (storage bloqueado E9.7-B, evento `framesent` de WS) produce
  BLOQUEADO (detalle) — jamás falso PASS ni FAIL injusto (criterios de
  BLOQUEADO de qa-plan §6 aplicados).
- **R9.6.5** — Informe resumido: tabla de veredictos de los 26 checks al
  final del qa-report de C9 (+ cifras RAW de la corrida, orden de ejecución
  y el residuo de R9.5.5); los manuales/PASS-CON-NOTA de §5 quedan escritos
  tal cual, sin silencios.

### Escenarios de aceptación de Fase A (registro E9.n)

Formato GIVEN/WHEN/THEN — REGISTRO COMPACTO: el criterio completo y la
técnica de cada E9.n viven en su R9.x.y del dominio correspondiente (esta
tabla NO añade checks nuevos — el límite EXACTO de G2 ya está fijado).

- **E9.1** — GIVEN sesión válida (login por UI), WHEN navegación secuencial
  a las 9 rutas, THEN cada una renderiza su pantalla SIN 404 de servidor y
  `/` aterriza en `/logs` SIN pantalla propia (R9.2.1).
- **E9.2** — GIVEN contexto sin JWT, WHEN se abre `/proyectos/:id`, THEN
  401 de `/me` capturado + aterrizaje en `/login?…` con la vista destino
  recordada y la vista protegida NUNCA renderizada; tras login, regreso al
  destino (R9.2.2).
- **E9.3** — GIVEN token vigente, WHEN se visita `/login`, THEN redirect a
  `/logs` con formulario ausente; GIVEN token basura + `/me` 401, THEN
  token descartado y formulario normal (R9.2.3).
- **E9.4** — GIVEN con sesión, WHEN `/noexiste`, THEN pantalla 404 del
  panel (contenido no vacío, URL estable); GIVEN sin sesión, THEN `/login`
  con destino guardado (R9.2.4).
- **E9.5** — GIVEN credenciales malas, WHEN submit con 401, THEN mensaje
  inline "Usuario o contraseña incorrectos", vista íntegra, sin redirect
  (R9.2.5).
- **E9.6** — GIVEN el POST de login retrasado por route, WHEN doble click
  en Entrar, THEN botón "Enviando…" deshabilitado y contador de POST == 1
  (R9.2.6).
- **E9.7** — GIVEN sesión activa, WHEN `goto /logs` en página nueva, THEN
  `/me` ANTES del primer render protegido + username en la barra +
  formulario de login jamás visible (PASS-CON-NOTA: proxy temporal); GIVEN
  storage bloqueado, THEN panel funcional con Bearer (o BLOQUEADO honesto)
  (R9.2.7).
- **E9.8** — GIVEN en `/logs`, WHEN "Cerrar sesión", THEN `/login` + storage
  limpio + CERO requests de logout + sin handshake WS posterior (R9.2.8).
- **E9.9** — GIVEN sesión activa, WHEN inspección booleana del navegador,
  THEN token+`expires_at` SOLO en sessionStorage (claves del panel) y
  localStorage/cookies limpios (R9.2.9).
- **E9.10** — GIVEN cursor avanzado, WHEN cambio de filtro, THEN 1
  `GET /panel/logs` con el needle + 1 `set_filters` (6 claves, nulls, SIN
  cursor) + URL rehidratada; WHEN Reiniciar, THEN query vacía y `GET /logs`
  sin parámetros (R9.2.10).
- **E9.11** — GIVEN `/logs` con WS abierto, THEN "● Conectado" en la barra;
  GIVEN backend parado (fase de kill), THEN "○ Desconectado" + banner
  "Streaming inactivo: los datos en pantalla no se actualizan en vivo" +
  "Reconectando (intento N)…" con N creciente; GIVEN navegación a
  `/proyectos`, THEN indicador y banner AUSENTES (R9.2.11).
- **E9.12** — GIVEN 1.ª petición retrasada, THEN esqueleto; GIVEN filtro sin
  resultados, THEN sus 2 literales; GIVEN `from>to` por URL, THEN banner con
  `code` + lista conservada (jamás vacío por error ni viceversa) (R9.2.12).
- **E9.13** — GIVEN acumulado ≥60 con query en URL, WHEN Detalle → Volver,
  THEN query, nº filas e id de 1.ª fila idénticos + SIN `GET /panel/logs`
  nuevo al montar; GIVEN F5, THEN acumulado limpio (50 + next_cursor
  no-null) — CONTRA el post-parche D-C8-8 (R9.2.13).
- **E9.14 (G1)** — GIVEN `page.route("**/panel/projects")` devolviendo
  `{items:[],next_cursor:null}`, WHEN se abre `/proyectos`, THEN pantalla
  REAL con "No hay proyectos todavía" + CTA "Nuevo proyecto" navegada 100%
  real ⇒ PASS-CON-NOTA (R9.2.14).
- **E9.15** — GIVEN 400 de filtros (`filter_from_to_inverted` /
  `filter_invalid_value`) por URL, THEN banner rojo con el `code` (y el
  `field` marcado en la variante) + lista previa conservada (R9.3.1).
- **E9.16** — GIVEN ingesta de un batch de 3 con `/logs` abierto, THEN
  banda "● En vivo" con las 3 filas EN EL ORDEN del lote, `batch_id`
  visible y 0 refetch de `GET /panel/logs` (R9.3.2).
- **E9.17** — GIVEN detalle de un log CON y otro SIN exception+metadata,
  THEN bloques literales presentes / ocultos según nulos + cabecera de la
  maqueta 03 (R9.3.3).
- **E9.18** — GIVEN `/logs/99999999`, THEN "Este registro no existe (o fue
  eliminado)" + Volver, jamás vacío/esqueleto infinito (R9.3.4).
- **E9.19** — GIVEN `/proyectos`, THEN tarjetas de la maqueta 04 con STATUS
  "Activo"|"Archivado", name/description/prefix/has_key y SIN resumen P1
  (R9.3.5).
- **E9.20** — GIVEN ficha `/proyectos/:id`, THEN bloque "API key de
  ingesta" con prefix mono truncado + `has_key`; GIVEN id inexistente, THEN
  "Este proyecto no existe (o fue eliminado)" + Volver (R9.3.6).
- **E9.21** — GIVEN `/proyectos/nuevo`, WHEN crea `qa-c9-<ts>-e45`, THEN
  201 → ficha con el aviso literal; GIVEN 2.º submit con el MISMO nombre,
  THEN 409 inline con datos conservados; GIVEN name vacío, THEN 400 por
  campo (R9.3.7).
- **E9.22** — GIVEN zona destructiva de la ficha, THEN modales 8a/8c con
  botón habilitado SOLO por coincidencia exacta (trim) y SIN petición
  DELETE (R9.3.8).
- **E9.23** — GIVEN Regenerar API key del proyecto C9, THEN modal de
  revelación única con aviso "No se volverá a mostrar; cópiala ahora" y key
  matcheada internamente (REDACTADA en el shot); al cerrar, SOLO prefix +
  `has_key: true` (R9.3.9).
- **E9.24** — GIVEN `/logs` abierto con WS conectado, WHEN ingesta REST de
  un batch de 3 (`X-API-Key`), THEN grupo en banda "● En vivo" sin refresco
  (0 refetch) e indicador sigue Conectado (R9.4.1).
- **E9.25** — GIVEN semillas ERROR/production con stacktrace, WHEN filtros
  desde `/logs` hasta el `<pre>` del detalle, THEN contenido EXACTO visible
  con cronómetro `time.monotonic()` < 120 s (R9.4.2).
- **E9.26** — GIVEN `/logs` vivo con id-set capturado, WHEN kill
  `stop`/`up -d backend` con bucle de health, THEN estados/banner durante
  la caída y, tras restaurar: "● Conectado", banner retirado,
  `set_filters` REENVIADO, 1 refetch de 1.ª página e ids ÚNICOS con
  **delta 0** contra el id-set pre-caída (R9.4.3).

## 2. Fase B — Deploy + firewall (APROBADA en G4 — requisito de OPERACIÓN)

> Especificación ACOTADA de OPS: describe los RESULTADOS operativos que se
> deben alcanzar y verificar, NO los detalles de implementación de la VM
> (esos se cierran en design/tasks de Fase B, que la proposal reserva). La
> misma pila que ya corre en localhost, LLEVADA a la VM Proxmox. TRD §6.7
> (GATE #11): "el detalle de implementación va con el despliegue".

### DOMINIO `ops/deploy-vm` (S9-B1)

- **R9-B1** — La pila completa (frontend+backend+postgres, LA MISMA compose
  con sus secretos) DEBE quedar desplegada en la VM Proxmox y healthy, accesible por el ÚNICO puerto 8080 (proxy nginx — mismo patrón
  de C1) desde la LAN. Verificación: los contenedores healthy + panel
  accesible desde la LAN.
  **E9-B1** — GIVEN la VM accesible por SSH, WHEN la pila se despliega,
  THEN los 3 contenedores healthy y `http://<vm>:8080` sirve el panel desde
  la LAN (login por UI posible; mismo origen, cero CORS).

### DOMINIO `ops/firewall-gate11` (S9-B2)

- **R9-B2** — Firewall de la VM con reglas NUEVAS (GATE #11, TRD §6.7):
  **SSH de gestión desde la LAN** y **8080 (frontend)**. Regla operativa
  DURA: la regla de SSH ENTRA **SIEMPRE ANTES** que cualquier regla de
  cierre — el orden es parte del requerimiento (evita lockout). Todo
  cambio de firewall DEBE verificarse desde la LAN antes de aplicarse el
  cierre (jamás "aplicar y probar").
- **R9-B3** — **CONDICIÓN DE BLOQUEO-ACCESO (explícita)**: SI el acceso SSH a
  la VM falla (credenciales/clave/red — motivo el que sea), **Fase B queda
  BLOQUEADA-ACCESO y se REPORTA sin tocar NADA**: SIN cambios de firewall,
  SIN deploy parcial, SIN reinicios — el estado de la VM queda EXACTAMENTE
  como se encontró. El report declara el motivo y las re-corridas
  posteriores re-intentan DESDE CERO de Fase B. Jamás un avance parcial
  "a ciegas" sin SSH.
- **E9-B2** — GIVEN SSH operativa, WHEN el firewall se aplica, THEN SSH
  desde la LAN sigue operativa (jamás cortada) Y el puerto 8080 responde
  desde la LAN Y el resto del tráfico queda según GATE #11;
  verificado DESDE la LAN en vivo antes del cierre.
- **E9-B3** — GIVEN SSH no accesible, WHEN arranca Fase B, THEN
  BLOQUEADA-ACCESO con reporte y CERO cambios en la VM (R9-B3).

### DOMINIO `ops/frio-reinicio` (S9-B3)

- **R9-B4** — Arranque EN FRÍO y REINICIO de la VM verificados: tras un
  arranque frío (VM apagada → encendida) y tras un reinicio, la PILA DEBE
  quedar ARRIBA SOLA (sin intervención): contenedores healthy, panel en
  8080, sin pérdida de datos visible (los needles de C8/C9 siguen
  consultables si su volumen sobrevive — la verificación es de ARRIBA, no
  de contenido). **E9-B4** — GIVEN la VM apagada, WHEN se enciende, THEN la
  pila termina healthy sin intervención y el panel responde en 8080; GIVEN
  reinicio, THEN idem (2 estados verificados con evidencia mínima en el reporte).

### DOMINIO `ops/nginx-body-size` (S9-B4)

- **R9-B5** — `nginx client_max_body_size` FIJADO en el nginx del frontend
  del DESTINO (deuda heredada C5→C6→C7→C8): una ingesta grande por el proxy
  NO DEBE ser rechazada con 413 por nginx (la superficie REST/WS intACTA —
  R8.53/R8.47). Verificación operativa: ingesta de carga por encima del
  límite defecto DEBE completarse sin 413 a través del único puerto
  (el valor exacto lo fija design/tasks; el requerimiento es el CERO-413 —
  y el mismo valor se deja anotado en el reporte).
  **E9-B5** — GIVEN el nginx del destino con el límite fijado, WHEN una
  app ingesta un batch mayor que el defecto de nginx, THEN NO llega 413 del
  proxy y la ingesta procede (202/400 según validez del payload — el aserto
  es de AUSENCIA de 413).

### DOMINIO `ops/smoke-compose` (S9-B5)

- **R9-B6** — **Smoke canónico con `docker compose`** en el destino (deuda
  heredada C1/C4/C5/C6): la recomposición/verificación del despliegue
  DEBE correr bajo `docker compose` (el binario del destino) en verde — la pila
  DEBE recomponerse con sus imágenes/secretos del destino y quedar healthy con
  los casos mínimos del panel accesibles (login y `GET /me` de humo por el
  8080). El smoke DEJA ANOTADO qué binario (docker compose vs podman
  compose) se usó por entorno.
  **E9-B6** — GIVEN el destino con la pila desplegada, WHEN el smoke
  canónico corre con `docker compose`, THEN termina verde (pila healthy, humo
  del panel OK) y anota el binario usado en el reporte.

### DOMINIO `ops/secretos-destino` (S9-B6)

- **R9-B7** — Los `.env` del destino se CREAN **EN la VM** y **JAMÁS** se
  suben al repo, se copian en evidencias ni se IMPRIMEN (S16 — canon): los
  nombres de variables se pueden citar en el reporte (como hicieron C1+),
  los valores JAMÁS; ningún fichero de secretos del destino entra en el
  diff del repo. La evidencia de Fase B (comandos/salidas) se captura ya
  redactada.
  **E9-B7** — GIVEN Fase B ejecutada, WHEN se revisa el repo y las
  evidencias, THEN ningún valor de `.env` aparece (grep de S16) y el diff
  contiene solo config no-secreta (firewall/nginx/compose de referencia sin
  valores).

**NO entra en Fase B (explícito — proposal §2):** TLS/dominio (fuera del MVP —
TRD §23), rate limiting por ventana, los 3 DELETE y `include=summary`
(C10), y ningún check visual más allá de lo ratificado en G2.

**Frontera entre fases:** Fase A NO requiere la VM (corre contra
localhost:8080); Fase B NO re-valida los 26 checks (solo arranca la pila
idéntica); si Fase B queda BLOQUEADA-ACCESO, C9 CIERRA con Fase A y la
ficha C9 del 07 queda PARTIDA (A hecha; deploy/firewall pendientes para un
corte siguiente — done-when).

## 3. Parejas de evidencia — qué [S] REST respalda cada check visual

Cada check visual del arnés respaldado por la pareja de stack ya VERDE en
C8 (mismo backend, misma superficie; se CITAN, no se re-prueban salvo lo
que la pareja visual exige de semilla: el arnés ingesta sus needles
propios):

| Check visual (E9-n) | QP-8.x | Respaldado [S]/[L] por (C8, ya verde) |
|---|---|---|
| E9.1 (rutas 9) | QP-8.1 | E8.6/E8.51 (SPA fallback + assets) |
| E9.2 (guard+destino) | QP-8.2 | E8.19/E8.48 (`/me` sin token → 401 ABSORBIDO) |
| E9.3 (login redirect) | QP-8.4 | E8.7/E8.18 (auth REST) |
| E9.4 (404 panel) | QP-8.5 | E8.6 (fallback, jamás 404 de servidor) |
| E9.5 (401 inline) | QP-8.8 | smoke E8-auth (401 `invalid_credentials`) |
| E9.6 (doble submit) | QP-8.9 | E8.7 (endpoint 200; el pin "1 POST" es del arnés) |
| E9.7 (restauro + storage-b) | QP-8.10 | E8.7 (restauro REST absorbido) |
| E9.8 (logout sin red) | QP-8.12 | — (el sin-endpoint es el pin del arnés) |
| E9.9 (JWT storage) | QP-8.13 | T8.67/grep de higiene C8 |
| E9.10 (filtros+set_filters) | QP-8.22 | QP-8.27 [L] (shape 6 claves) + QP-8.24 (cursor) |
| E9.11 (estados + banner) | QP-8.29 | E8.30/E8.31 [S] (reconexión/401 por pila) |
| E9.12 (estados /logs) | QP-8.32 | smoke E8-API (400 code/field del literal) |
| E9.13 (Volver conserva) | QP-8.35 | 5 tests Vitest E8.35 (`f3c7480`) — aquí la MITAD NAVEGADA |
| E9.14 (vacío + CTA, G1) | QP-8.40 | smoke E8-API (shape `{items, next_cursor}`) |
| E9.15 (banner 400) | QP-8.26 | smoke E8-API (captura 400 code/field) |
| E9.16 (banda En vivo) | QP-8.27 | smoke E8-WS byte-exacto (`b_`+UUIDv4) |
| E9.17 (bloques detalle) | QP-8.34 | smoke E8-API (11 campos del DETALLE) |
| E9.18 (404 detalle) | QP-8.36 | smoke E8-API (`{"code":"not_found"}`) |
| E9.19/E9.20 (maquetas 04/05) | QP-8.39/8.42 | smoke E8-API (ProjectDto sin summary / prefix+has_key) |
| E9.21 (flujo 2 UI) | QP-8.45 | smoke E8-API (201/409/400 del CRUD) |
| E9.22 (modales 8a/8c SIN petición) | QP-8.47 | [L] comparador `coincideLiteralConTrim`; smokes C10-negativos (405/404) CITADOS |
| E9.23 (revelación única) | QP-8.43 | smoke E8-API (201 `{key, prefix, created_at}`) |
| E9.24 (criterio 1) | 06-C1 | E8.27 smoke + Vitest dedup |
| E9.25 (criterio 2 <120 s) | 06-C2 | smoke E8-API (detalle 11 campos + needle) |
| E9.26 (criterio 3 kill) | 06-C3 | QP-8.29/E8.30 (reconexión — pareja E7.11 de qa_c7 CITADA) |

**Needles**: `qa-c9-<ts>-<caso>` (proyectos: `qa-c9-<ts>-e45`; logs por
caso; stacktrace con needle interno para E9.25). **Residuo**:
proyecto qa-c9 + sus logs + key activa — NOTIFICADO en el qa-report
(R9.5.5); limpieza real con los DELETE de C10.

## 4. Cifras de gates previstas

| Canal | Cifra esperada | Nota |
|---|---|---|
| **Checks del arnés** | **26** = **14** parejas Q23 (E9.1–E9.14) + **9** extras G2 (E9.15–E9.23) + **3** criterios del 06 (E9.24–E9.26) — límite EXACTO de G2, sin inventar | exit 0 esperado: PASS + PASS-CON-NOTA (G1/E9.14 y el matiz proxy de E9.7-A) |
| **Vitest** (`npx vitest run`) | **60 tests / 6 ficheros** (55 + 5 de E8.35 — commit `f3c7480`) | **SIN CAMBIO** — Fase A no añade ninguno (Playwright vive fuera de la frontera) |
| **mvn** (`mvn -f backend/pom.xml test`) | **177/177** | **SIN CAMBIO** — Fase A no toca backend (D1 ya cerrado en C8; la deuda "401 ISO-8859-1" NO es de C9) |
| **Smoke C8** (`qa_c8_smoke.sh`) | **32 veredictos [S] PASS** + exit 0 | re-corrida de regresión al cierre (R9.5.6); su `down -v` barre la siembra de C9 |
| **Screenshots** | ≈**25–35** | uno por pareja (+ estados múltiples de E9.24/E9.26) con higiene R9.5.2 |
| **qa-log RAW** | 1 fichero | grep post-corrida: 0 hits `plk_`/`eyJ` |
| **Fase B** | 6 dominios (R9-B1…R9-B7) — cifras propias en su design/tasks | si BLOQUEADA-ACCESO: reporte + ficha del 07 partida |

Total de escenarios del corte: **26 (Fase A) + 7 (Fase B) + los
transversales de higiene/guardia (R9.5.2/R9.1.5, sin veredicto propio)**.

## 5. Huecos y MANUALES declarados (sin silencios — tal cual al qa-report)

- **Percepción humana A6 (manual SIEMPRE)**: contraste/estética/jerarquía
   visual real (legibilidad de la paleta en vivo, FATAL invertido) es ojo
   del usuario sobre los screenshots — MITIGADA con asertos de
   `getComputedStyle` de badges/indicador/botón rojo; la lectura subjetiva
   NO es automatizable.
- **E9.7-A flash de login (PASS-CON-NOTA)**: el aserto es proxy temporal
  (`/me` antes del primer render protegido + polls sin formulario) — NO hay
  captura por-frame de un flash real (residual manual si el usuario exigiera
  seguridad por-frame; propongo: proxy suficiente + ratificación visual A6).
- **E9.7-B storage bloqueado (BLOQUEADO honesto)**: la emulación por
  `add_init_script` que LANZA es frágil; si no resulta ⇒ BLOQUEADO (Q23)
  de esa sub-prueba — jamás falso PASS.
- **E9.10 sub-aserto `framesent` (PLAN B)**: si el evento de frames enviados
  no resultara observable en 1.64.1, se degrada a BLOQUEADO-anotado con
  conteo de handshakes/reconexiones como respaldo (R9.2.10).
- **Residuo de BD de Fase A (no limpiado)**: proyecto C9 + logs + key
  activa — NOTIFICADO (R9.5.5); limpieza real = C10. Aceptado: entorno dev
  de una sola VM.
- **E9.14 "pantalla real, datos simulados"**: la NOTA de G1 queda impresa
  en el veredicto (no PASS limpio).
- **Clipboard (G3)**: Copiar asertado por presencia/habilitación — el
  portapapeles real queda FUERA de alcance (sin `grant_permissions`).
- **Fase B**: si el SSH falla ⇒ BLOQUEADA-ACCESO y reporte sin tocar nada
  (R9-B3); TLS/dominio y rate limiting NO entran (proposal/§2).

## Done-when (final)

- **A1 [L]** — Las 14 parejas tienen veredicto individual con snapshot
  semántico sobre el DOM vivo: **cero PASS sin pantalla real observada**
  (regla dura anclada — R9.1.4/R9.6.1).
- **A2 [L]** — Los 3 criterios demo del 06 pasan automatizados (E9.25 con
  cronómetro < 120 s; E9.26 con kill local stop/up + bucle health y sin
  duplicados/replay del id-set pre-caída).
- **A3 [L]** — Los 9 extras ratificados en G2 observados por el mismo arnés
  con sus literales; lo NO ratificado (8.11/8.31/8.38/8.37) no existe como
  check.
- **A4 [S]** — Evidencia completa en `.atl/sdd/C9/` (qa-log RAW +
  screenshots) con higiene S16 verificada (grep 0 hits `plk_`/`eyJ`; login
  sin campos rellenados; key de E9.23 REDACTADA en shot) y exit 0/1/2
  canónico; informe resumido en el qa-report.
- **A5 [R]** — Los manuales/PASS-CON-NOTA de §5 escritos tal cual en el
  qa-report (sin silencios); re-corrida de regresión mínima:
  `qa_c8_smoke.sh` verde tras la Fase A (la pila no cambia — solo se
  verifica).
- **A6 [MANUAL]** — Ratificación visual humana de los screenshots por el
  usuario al revisar el qa-artefacto (percepción de contraste/estética).
- **B (si accesible — G4 aprobada)** — Pila arriba en la VM (R9-B1);
  firewall SSH LAN + 8080 activo y verificado desde la LAN (R9-B2); frío y
  reinicio verificados (R9-B4); `client_max_body_size` fijado con 413
  ausente (R9-B5); smoke `docker compose` verde con su binario anotado
  (R9-B6); secretos `.env` creados EN la VM, jamás al repo/imprimidos
  (R9-B7). **Si el acceso SSH falla ⇒ BLOQUEADA-ACCESO: se reporta sin
  tocar nada y C9 cierra con Fase A** (ficha C9 del 07 partida:
  deploy/firewall documentados como pendientes para un corte siguiente).