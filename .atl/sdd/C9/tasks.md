---
project: Project_Log
change: C9
artifact: tasks
last_updated: 2026-10-29
status: active
---

# Tasks — C9 · QA visual automático (ARNÉS Playwright, 26 checks) + deploy y firewall (Fase B)

> Fase sdd-tasks del corte C9. Fuentes normativas: `spec.md` C9 (R9.x de
> Fase A en 6 dominios + R9-B1…R9-B7 de Fase B, escenarios E9.1–E9.26 /
> E9-B1…B6, §4 cifras, §5 manuales, done-when A1–A6+B — status done-gate,
> G1–G4 APROBADOS 2026-10-29) y `design.md` C9 (secciones (a)–(i) §1, orden
> de corrida B0→B8 §2, decisiones D-C9-1…D-C9-15, File Changes §4 COMPLETA
> y CERRADA, runbook FB-0…FB-8 §6, riesgos R-D-C9-1…13, checklist §11).
> El spec y el design NO se editan. C9 NO es código de producto: Fase A es
> QA (requerimientos DEL ARNÉS + OPS de evidencia), Fase B es OPS de
> deploy/firewall. Literales de UI/API se CITAN (spec §1, maquetas
> congeladas `docs/mockups/`) — jamás redefinidos. Base HEAD `c144f3d`.

## Frontera (vale para TODAS las tareas — tabla §4 del design, CERRADA)

**Únicamente estas rutas pueden crearse/editarse — nada fuera de la tabla
llega a apply:**

- **CREATE (Fase A)**: `scripts/qa_vis_c9.py` (arnés único, R9.1.1) y
  `scripts/qa_vis_c9.sh` (wrapper MAY → design D-C9-11: SÍ). **Generados en
  corrida**: `.atl/sdd/C9/qa-log.txt` (RAW, **COMMITEADO** — canon C8,
  D-C9-12) y `.atl/sdd/C9/evidencia/E9n_*.png` (≈25–35, **EN DISCO, NO
  commitear** — D-C9-12, ratificación A6). Artefactos SDD C9 en SUS fases.
- **CREATE (Fase B — en la VM, JAMÁS commiteados, S16)**: `/opt/project_log/**`
  (rsync con excludes), `/opt/project_log/.env` (generado EN la VM, chmod
  600), `/opt/project_log/frontend/nginx.conf` (copia del DESTINO —
  `client_max_body_size 5m`; el **repo** dif-0, D-C9-14).
- **INTOCABLES (Fase A — dif-0 obligado)**: TODO `frontend/**` INCLUIDO
  `frontend/package.json` + `package-lock.json` (R9.1.3/QP-8.54 — Playwright
  por pip, JAMÁS dependencia de producto), TODO `backend/**`, `compose.yaml`,
  `.env*`, `docs/**` (incl. maquetas congeladas), smokes previos
  (`qa_c2`–`qa_c8`, `ws_echo_test.py`), tests Vitest y Java. El diff del
  corte = A1+A2 (+A3 al final de qa) + artefactos SDD. **NINGÚN `.gitignore`
  nuevo** (git add selectivo).

## Orden de dependencias

**Bloque 1** (arnés por secciones + wrapper + commit test(qa)) →
**Bloque 2** (primera corrida + bucle de hallazgos acotado hasta verde: 26
veredictos) → **Bloque 3** (evidencia/higiene + confirmación de regresión
dif-0 → estado FINAL de Fase A) → **Bloque 4** (Fase B: runbook FB-0→FB-8
en orden; si FB-0 falla ⇒ BLOQUEADA-ACCESO y saltar DIRECTO a Bloque 5 sin
tocar nada — R9-B3) → **Bloque 5** (cierre del corte: re-corrida
qa_c8_smoke AL FINAL de TODO tras Fase B → re-siembra condicional →
qa-report → verify → state → ficha 07 → commit docs(SDD)). El arnés DEBE
estar completo ANTES de la 1.ª corrida (los checks se implementan POR
SECCIÓN pero la secuencia B0→B8 es UNA sola: nunca una corrida parcial
"verificando" checks por bloques).

## Reglas transversales de apply

- **Higiene S16 visual (duro — R9.5.2/D-C9-6)**: JWT y key NUNCA impresos
  (stdout/qa-log/PNG/argv/os.environ); TODO `page.evaluate` devuelve SOLO
  booleanos/keys/conteos (matcheo `^eyJ`/`^plk_[A-Za-z0-9_-]{43}` INTERNAMENTE
  en el navegador); frames WS al log SOLO por `type`/`length`; shots de login
  SIEMPRE sin campos rellenados; shot de E9.23 con overlay REDACTADO
  inyectado ANTES del `screenshot()`; el wrapper JAMÁS exporta `.env` al
  shell (`set -a; source .env` PROHIBIDO — D-C9-11).
- **Regla dura NO fingir PASS (R9.1.4/D-C9-1)**: NINGÚN veredicto PASS sin
  `pantalla_vista` (navegar + snapshot semántico sobre DOM vivo + shot). Un
  `passO` sin la flag ⇒ auto-FAIL del check con nota "falso PASS bloqueado
  por el arnés" (= defecto DEL ARNÉS, no del producto). Intercept de red =
  solo DATOS simulados (D-C9-3), jamás sustituto de pantalla.
- **Destructivo acotado (R9.1.5/D-C9-5)**: UNA ventana de kill por corrida,
  SIEMPRE última fase (B7); SOLO `docker compose stop backend` / `up -d
  backend` dentro de `try/finally` + bucle health 180 s; **JAMÁS `down -v`,
  SQL directo ni acceso a postgres/volúmenes**; restauración fallida ⇒
  **exit 2 "PILOTO CAÍDO"**. No correr durante otro smoke en curso.
- **Veredictos canon C8 (R9.6.x)**: `E9.n: PASS | PASS-CON-NOTA (motivo) |
  FAIL | BLOQUEADO (detalle) | Manual`; exit **0** = todo PASS (la
  PASS-CON-NOTA de G1/E9.14 y el matiz proxy de E9.7-A cuentan como PASS con
  nota impresa), **1** = algún FAIL, **2** = higiene S16 fallada /
  PILOTO CAÍDO / pila no disponible. FAIL → loop-back a apply/design por
  encima del qa-report (R9.6.3 — JAMÁS auto-arreglo o maquillaje dentro del
  guion). BLOQUEADO honesto para emulación que no resulte (R9.6.4).
- **Límite EXACTO de checks (G2/D-C9 checklist)**: EXACTAMENTE los 26
  (E9.1–E9.26); QP-8.11/8.31/8.38/8.37 NO EXISTEN como check. Clipboard
  (G3): Copiar asertado SOLO por presencia/habilitación.
- **Needles idempotentes (R9.5.4)**: `qa-c9-<ts>-<caso>` en MESSAGE (el
  filtro `text` matchea SOLO message); proyecto C9 `qa-c9-<ts>-e45` nace por
  UI en E9.21; su key por regenerate SOLO en memoria; **NUNCA regenerar
  keys de proyectos preexistentes**; residuo NOTIFICADO (R9.5.5), NO limpiado.
- **Pauta de commits (sin push)**: 1× `test(qa)` del arnés (o 2 si el
  wrapper se commitea aparte) + commits acotados propios de hallazgos
  (guion) + `docs(SDD)` final. Todo con `Co-Authored-By: Claude Code
  <noreply@anthropic.com>`.
- **NO tocar código, NO re-ejecutar suites de producto** salvo excepción
  justificada por dif real (las cifras Vitest/mvn son SIN CAMBIO — §4).

---

## Bloque 1 — Fase A: arnés `scripts/qa_vis_c9.py` por secciones + wrapper

> Una tarea por sección §1 del design ((a)–(i)) + composición del main
> (orden de corrida B0→B8 §2) + wrapper (D-C9-11) + commit. Fase A NO
> requiere la VM: TODO contra `http://127.0.0.1:8080`/`localhost:8080` con
> la pila ya arriba (NO se recompila — se verifica).

- [x] T9.1 — CREAR sección (a) infra/env del arnés `scripts/qa_vis_c9.py`
      (Playwright **sync API 1.64.1**, chromium headless — patrón del repro
      `/tmp/qa_c9/repro_detalle.py`): `cargarEnvProceso()` — parser dotenv
      mínimo EN PROCESO (`ADMIN_INITIAL_USERNAME`/`ADMIN_INITIAL_PASSWORD` y
      claves del compose SOLO a variables internas; jamás a `os.environ`
      exportado, a argv ni impresos); `verificarPila()` — `GET
      /actuator/health` + `GET /` 200 al arranque, fallo ⇒ **exit 2** (pila/
      entorno no disponible, R9.6.2 — jamás falso PASS); verificación de
      arranque D-C9-15: import de `playwright.sync_api` + binario
      chromium-headless-shell en `~/.cache/ms-playwright` (si faltan ⇒
      exit 2 con instrucción de instalado por pip — **el guion jamás
      instala solo**) y comprobación del nombre EXACTO del evento WS
      (`websocket.on("framesent")` — D-C9-4) contra la versión instalada.
      Evidencia: B0 corre sin veredicto. Criterio hecha: R9.1.2/R9.1.5/
      D-C9-15 literales; secretos jamás fuera del proceso Python.
      (nota sdd-apply: HECHA — sección (a) en qa_vis_c9.py; B0 corre en vivo
      sin veredicto: GET / 200 + GET /actuator/health 200 (anotada como
      trivial SPA-fallback, H-C9-SALUD) + señal real GET /panel/me == 401;
      verificarEntorno: playwright 1.64.1 + binarios chromium-1248 y
      chromium_headless_shell-1248 en ms-playwright + evento WS "framesent"
      confirmado contra la versión instalada; parser dotenv en proceso, sin
      export/os.environ/print; faltantes ⇒ exit 2, jamás pip.)
- [x] T9.2 — CREAR sección (b) navegación/login: `sesionPrincipal()` —
      login REAL por UI (fill `input[type=password]` + click botón Entrar +
      `wait_for_url("**/logs")` — token JAMÁS seteado a mano SALVO las
      variantes que lo exigen: E9.3/E9.7-B con string BASURA, nunca un JWT
      real); `contextoLimpio()` (guard/404/variante token-basura con storage
      limpio — E9.2/E9.3-v/E9.4-b); `contextoStorageBloqueado()`
      (`add_init_script` que LANZA sobre `window.sessionStorage` — E9.7-B,
      BLOQUEADO honesto si la emulación no resulta). Criterio hecha:
      R9.1.2/R9.2.2-var/R9.2.7-B.
      (nota sdd-apply: HECHA — loginPorUI fill #user/#pass + wait_for_url
      "**/logs" (Login.tsx literals verificados en fuente); token jamás
      seteado a mano salvo basura en E9.3/E9.7-B; contextoLimpio con
      pre-sembrado `qa-c9-<ts>-token-basura` en sessionStorage;
      contextoStorageBloqueado add_init_script que Lanza sobre
      sessionStorage; E9.7-B sin viabilidad ⇒ BLOQUEADO honesto y FALLBACK A
      (proxy temporal) con nota.)
- [x] T9.3 — CREAR sección (c) observación: `registroRed(page)` — listeners
      `request`/`response` con contadores por patrón (`panel/logs`,
      `panel/projects`, `panel/me`, `auth/login`, `ingest/batch`) — TODA la
      asertación temporal (orden, 0/1/2 peticiones) nace de contadores, no
      de "esperar y mirar"; `wsContador` — `page.on("websocket")` +
      `ws.on("framesent")` con contador por `type`/longitud (S16: jamás
      contenido del frame al log) + `ws.open/close`; parseo interno del
      shape `set_filters` (6 claves con nulls, SIN `cursor`) sin imprimir
      contenido. PLAN B D-C9-4 disponible (conteo de
      handshakes/reconexiones). Criterio hecha: R9.2.10-matiz/D-C9-4/§(c).
      (nota sdd-apply: HECHA — clase Contadores con page.on request/response
      y 5 patrones (logs/projects/me/auth/ingest) + reqCon/respCuenta/marca
      índice; clase WsContador con websocket→framesent/framereceived/close
      logueando SOLO type/longitud (S16), parseo interno de set_filters
      (6 claves, sin cursor) a contadores, sin contenido al log; PLAN B D-C9-4
      disponible vía handshakes/cierres + flag PLAN B.)
- [x] T9.4 — CREAR sección (d) snapshot semántico: `textVisible(p, literal)`
      (`get_by_text`/`inner_text` sobre lits LITERALES del contrato — el
      literal no descrito por maqueta se cosecha EN VIVO y se pina, R9.2.4),
      `rolPagina(...)`, `claseComputada(p, loc)` — `getComputedStyle` de
      badges/indicador/botones coloreados (FATAL familia `#7f1d1d`, verde
      Conectado `#16a34a`, `bg-red-300`→`bg-red-700`), `filasTabla(p)` (ids
      de la tabla DOM → dedup/delta de E9.13/E9.26). **NUNCA diff de
      píxeles contra maquetas** (R9.5.3/D-C9-2). Criterio hecha: D-C9-2
      (roles/textos/clases computadas; píxeles NO).
      (nota sdd-apply: HECHA — textoVisible sobre literales del contrato
      (Login.tsx/NotFound/Logs.tsx/LogDetalle/BandaEnVivo verificados en
      fuente); filasTabla (claves timestamp|mensaje de la tabla viva para
      dedup/delta E9.13/E9.26); claseComputada evaluate getComputedStyle
      (rgb(185,28,28) botón Eliminar en E9.22); sin píxeles maqueta R9.5.3.)
- [x] T9.5 — CREAR sección (e) evidencia: `shot(n, etiqueta)` — screenshot a
      `.atl/sdd/C9/evidencia/E9n_<etiqueta>.png` (naming D-C9-7, p. ej.
      `E9.13_03_volver_query_intacta.png`, `E9.26_02_banner_caída.png` —
      trazable 1:1 al E9.n); `shotRedactado(n, locator)` — overlay opaco
      sobre la key JUSTO para el shot, retirado después (E9.23; la key
      jamás existe en el fichero); login shots con campos VACÍOS (vaciar
      antes o capturar el estado de error). Criterio hecha: R9.5.1 (≈25–35
      PNG) / R9.5.2 / D-C9-7. `mkdir .atl/sdd/C9/evidencia/` en B0.
      (nota sdd-apply: HECHA — shot(n, etiqueta) → evidencia/E9n_NN_<…>.png
      (presupuesto ≈34 PNG, dentro de R9.5.1); shotRedactado con overlay
      "REDACTADO (S16)" añadido/solo para el shot y retirado en finally
      (E9.23); vaciarInputsLogin antes de todo shot de login; DIR_EVID.mkdir
      en B0; directorio creado vacío y comprometido vacío.)
- [x] T9.6 — CREAR sección (f) veredicto: `RESULTADOS: list[str]` estilo
      canon C8 + `marca(id, veredicto, nota=None)` (`"E9.n: PASS …"`) +
      `passO(nota=None)`/**bloqueado(detalle)**/`fail(id, motivo)`; al
      cierre tabla ordenada + `sys.exit(0|1|2)` (R9.6.1/R9.6.2). **Regla
      dura ESTRUCTURAL (D-C9-1)**: `passO` exige la flag `pantalla_vista`
      que el helper de check setea al navegar+screenshot (snapshot sobre
      DOM vivo); un `passO` sin flag ⇒ auto-FAIL del propio check con nota
      "falso PASS bloqueado por el arnés" (R9.1.4 — jamás falso PASS). El
      PASS-CON-NOTA NO es FAIL y cuenta como PASS en exit (R9.6.2). FAIL
      nunca auto-arreglado (R9.6.3); corrida continúa tras un FAIL salvo
      guardas de entorno/exit 2 (design §2 confirmación del gate).
      (nota sdd-apply: HECHA — (f) canon C8: RESULTADOS una única línea
      `E9.N: …` por check, marca/passO/fail/bloqueado IDEMPOTENTES per-pareja
      (fail gana: conservador); REGLA DURA D-C9-1/R9.1.4 ESTRUCTURAL en
      passO — sin `pantalla_vista` real ⇒ auto-FAIL "falso PASS bloqueado por
      el arnés"; salirPorVeredictos con guard EXACTO 26/26 (len(RESULTADOS)
      y len(MARCADOS)) y exit 0/1; PASS-CON-NOTA cuenta como PASS.)
- [x] T9.7 — CREAR sección (g) intercepts: `interceptar(patrón_glob,
      respuesta, retardo)` envoltura de `page.route` usada EXACTAMENTE por:
      **E9.6** (delay ~600 ms de `**/panel/auth/login` con
      `route.continue_()` + contador de POST — no sustituye la respuesta
      real), **E9.12-a** (delay de la 1.ª `**/panel/logs` — el esqueleto es
      pantalla real) y **E9.14** (`**/panel/projects` →
      `{"items":[],"next_cursor":null}` — pantalla REAL, datos simulados,
      nota OBLIGATORIA G1). Fuera de esos tres: TODO pasa por la pila real
      (400/404/401 los emite el backend real). Criterio hecha: D-C9-3
      literal (intercept = DATOS, jamás pantallas falsas).
      (nota sdd-apply: HECHA — interceptar(patrón, respuesta=None,
      retardo=0, limite=1) único punto de page.route; EXACTAMENTE 3 usos:
      E9.6 delay 600 ms POST login con continue_ (respuesta REAL) + cont2
      cuenta n_posts==1; E9.12-a retardo 1.4 s 1.ª /panel/logs (esqueleto
      real); E9.14 fulfill {"items":[],"next_cursor":null} + nota G1
      OBLIGATORIA + unroute en finally. Todo lo demás pila real.)
- [x] T9.8 — CREAR sección (h) semillas/ingesta: `ingestar(logs[], key)` —
      `POST /api/v1/ingest/batch` con cabecera `X-API-Key` (202
      `{"accepted":N}` asertado); `needle(caso)` → `qa-c9-<ts>-<caso>` en
      MESSAGE; `timestamp` del payload en pasado reciente (cae arriba);
      batches: ≥60 logs del needle de E9.13 (≤200 ⇒ 1 request única), 1
      ERROR production con `exception` (stacktrace con needle interno) +
      `metadata` + correlation needle (E9.17/E9.25), 1 log SIN
      exception/metadata (E9.17-b), lotes de 3 para E9.16/E9.24; la key
      SOLO en memoria del proceso (de `POST
      /panel/projects/{id}/api-key/regenerate` — **NUNCA regenerar keys de
      proyectos preexistentes**, R9.5.4). Criterio hecha: R9.5.4/§(h).
      (nota sdd-apply: HECHA — ingestar POST /api/v1/ingest/batch X-API-Key
      con aserto de 202 {"accepted":N} y `accepted` logueado sin la key;
      needles `qa-c9-<ts>-<caso>`; lote35 de 60 INFO (1 request ≤200), e25
      ERROR production con stack needle interno + metadata + correlation,
      e17b limpio sin exception/metadata, lotes de 3 para E9.16/E9.24;
      KEY_MEMORIA = dict en proceso alimentado POR el 201 del regenerate de
      E9.23; jamás se regenera key de proyecto preexistente (R9.5.4); guard
      en main: sin key en memoria ⇒ se salta B5 con print (sin veredictos).)
- [x] T9.9 — CREAR sección (i) fase destructiva: `pararBackend()` /
      `levantarBackend()` vía `subprocess` — `docker compose stop backend` /
      `docker compose up -d backend` (SOLO el servicio `backend`,
      contenedor `project_log_backend…`), `bucleHealth(180 s)` con `GET
      /actuator/health` hasta 200 en el `finally` (start_period 60 s ⇒
      ventana 180 s); restauración fallida ⇒ **exit 2 con "PILOTO CAÍDO"**
      en el log; `down -v`/postgres/volúmenes PROHIBIDOS; UNA ventana de
      kill por corrida (un 2.º ciclo MAY solo si un sub-estado lo exige,
      documentado en qa-log). Criterio hecha: R9.1.5/D-C9-5/R-D-C9-4.
      (nota sdd-apply: HECHA — pararBackend/levantanBackend via subprocess
      sobre `podman compose stop|up -d backend` (H-C9: COMPOSE_BIN
      autodetectado docker compose | podman compose | podman-compose; el
      host es podman 5.8.9 + podman-compose 1.6.0); bucleHealth(180 s) con
      GET /api/v1/panel/me == 401 como señal REAL de backend arriba (H-C9-
      SALUD: /actuator/health es SPA-fallback 200 trivial por nginx); en
      try/finally UNA ventana de kill en B7, restauración fallida ⇒ print
      "PILOTO CAÍDO" + exit 2; cero `down -v`, cero postgres/volúmenes.)
- [x] T9.10 — COMPOSICIÓN del main: orden de corrida B0→B8 EXACTO del
      design §2 con las re-ordenaciones ancladas: **B0** pre-comprobaciones
      (T9.1, sin veredicto); **B1** login+routing/guard/404 (E9.1 → E9.2 →
      E9.3+v → E9.4 → E9.5 → E9.6 → E9.7-A → E9.7-B; variantes en contexto
      NUEVO); **B2** E9.9 (booleanos) y **E9.8 logout AL FINAL del bloque
      con re-login inmediato por UI** (D-C9-10 — 2.º login documentado en
      el qa-log); **B3** `/logs` estados sin semilla (E9.10 contado por
      framesent → E9.12 → E9.15); **B4** proyectos con orden interno EXACTO
      **E9.21 PRIMERO** (crea el proyecto C9 + 201/409/400) → E9.19 → E9.20
      → E9.22 (modales 8a/8c SIN petición DELETE) → E9.23 (regenerate,
      **precede por D-C9-8**) → E9.14 AL FINAL del bloque (PASS-CON-NOTA
      G1); **B5** semillas/ingesta con la key ya en memoria (D-C9-9);
      **B6** con datos (E9.13 → E9.16 → E9.24 → E9.11-a indicador "●
      Conectado" → E9.17 → E9.25 cronómetro `<120 s`) + **E9.11-c**
      (`goto /proyectos` — indicador y banner AUSENTES, antes del kill);
      **B7** FASE DESTRUCTIVA ÚLTIMA (E9.26-E9.11-b cosechados de la MISMA
      ventana: `stop` → banner/`○ Desconectado`/`Reconectando (intento N)…`
      con N creciente → `up -d` en finally + health 180 s → E9.26-post:
      "● Conectado", banner RETIRADO, set_filters REENVIADO, **1 refetch**,
      ids ÚNICOS con **delta 0**); **B8** cierre (informe de 26 veredictos +
      cifras RAW + residuo R9.5.5). El guion es idempotente/re-corrible por
      needles ts (R9.5.4). Criterio hecha: R9.1.1/D-C9-8/D-C9-9/D-C9-10.
      (nota sdd-apply: HECHA — main() B0→B8 verbatim del §2 (B4 con orden
      interno E9.21→E9.19→E9.20→E9.22→E9.23→E9.14; B6 E9.13→E9.16→E9.24→
      E9.11-a→E9.17→E9.25 + E9.11-c; B7 ventana de kill ÚNICA con
      E9.26+E9.11-b y sellarE911 al cierre — H-C9: E9.11 es 3 partes deferred
      con sellado único al final de B7; E9.18 404-detalle slotted al final de
      B6 — ausente de la tabla de §2 por omisión del design). b5 condicional
      (se salta si no hay key en memoria); except global hace sellarE911 +
      cifras; veredictos 26/26 en B8.)
- [x] T9.11 — CREATE wrapper `scripts/qa_vis_c9.sh` (D-C9-11): (1) guarda de
      entorno barata (curl health del `127.0.0.1:8080` — si falla **exit 2**
      ANTES de abrir el navegador); (2) `python3 scripts/qa_vis_c9.py 2>&1 |
      tee .atl/sdd/C9/qa-log.txt` (RAW canon verbatim); (3) grep S16
      post-corrida sobre el RAW (`grep -c "plk_\|eyJ"` == 0 — fallo ⇒
      reescribe el exit a 2 con guardia visible); (4) pass-through del exit
      del guion. PROHIBIDO `set -a; source .env` — los `docker compose
      stop/up backend` leen su `.env` por sí mismos (interpolación compose).
      Criterio hecha: D-C9-11 (40 líneas aprox); `bash -n` verde.
      (nota sdd-apply: HECHA — 61 líneas; guardas de ficheros + import
      playwright ANTES de abrir el navegador (exit 2); tee RAW completo a
      .atl/sdd/C9/qa-log.txt con PIPESTATUS[0] como exit passthrough canon;
      grep S16 post-corrida de prefijos de credencial/token == 0 hits sino
      exit 2; sin `set -a` ni `source .env` — jamás lee VALORES;
      `bash -n` verde.)
- [x] T9.12 — Verificación de frontera del arnés + COMMIT 1 de la pauta: (a)
      Playwright FUERA de producto — NO hay entrada nueva en
      `frontend/package.json`/`package-lock.json` (R9.1.3/QP-8.54, dif-0);
      el guion vive en `scripts/` (fuera de la frontera Vitest de C8, jamás
      bajo `frontend/`); (b) el arnés jamás contiene `down -v`, SQL directo
      ni acceso a postgres (checklist §11). COMMIT (sin push): `test(qa):
      arnés visual Playwright scripts/qa_vis_c9.py + wrapper — 26 checks
      E9.n, snapshot semántico, S16, exit 0/1/2 (C9)` — py+sh juntos (o 2
      commits `test(qa)` si el wrapper se aparta — pauta lo permite), +`Co-
      Authored-By`. Evidencia: `git status` = solo A1+A2.
      (nota sdd-apply: HECHA — py_compile verde + bash -n verde + `git
      status` = solo scripts/qa_vis_c9.py + scripts/qa_vis_c9.sh añadidos
      (.atl/ permanece untracked: tasks.md se edita en disco pero NO se
      commitea en este bloque — Bloque 5); sin `down -v`, sin SQL, sin
      postgres en el arnés; sin playwright en frontend/package.json
      (dif-0 verificado). Mensaje del commit ratificado en instrucción.)

## Bloque 2 — Corrida y bucle de hallazgos (regla: el guion NO puede fingir PASS)

- [x] T9.13 — PRIMERA corrida completa en la pila real:
      `bash scripts/qa_vis_c9.sh` (wrapper — genera qa-log.txt RAW +
      evidencia/) → registrar los **26 veredictos por ID** (E9.1–E9.26, uno
      por pareja/criterio; E9.19/E9.20 y E9.15 por sub-variantes según su
      R9.x.y). Esperado: **exit 0** con PASS + PASS-CON-NOTA ratificadas
      (nota G1 de E9.14 impresa OBLIGATORIA en su veredicto; matiz proxy
      impreso en E9.7-A) — R9.6.2/§4. Cualquier FAIL se ANOTA TAL CUAL, sin
      auto-arreglo dentro del guion (R9.6.3). NO recompilar la pila.
- [x] T9.14 — BUCLE de hallazgos acotado ante cualquier FAIL (patrón
      C5–C8): clasificar (a) **defecto DEL ARNÉS** (selector frágil,
      timing, `passO` sin pantalla, `framesent` no observable con PLAN B no
      aplicado, emulación E9.7-B que degrada) → parche ACOTADO al guion
      (`scripts/qa_vis_c9.py`/`qa_vis_c9.sh`) + re-corrida; (b) **hallazgo
      real del PRODUCTO** la pantalla/endpoint no muestra lo del contrato
      → NO parchear el guion para fingir PASS: nota en `.atl/notes/`
      (patrón C8 H-C9-*) + veredicto FAIL/BLOQUEADO según corresponda +
      **decisión del ORQUESTADOR** (jamás auto-arreglo, jamás SQL directo).
      REGLA DURA: el guion NO puede fingir PASS — **un PASS sin pantalla =
      defecto del arnés = FAIL** (R9.1.4); BLOQUEADO honesto cuando una
      emulación no resulte (E9.7-B, sub-aserto framesent de E9.10 —
      R9.6.4). Re-corrida hasta verde (26 veredictos, exit 0).
      *(Nota de estado: EJECUTADO — 6 corridas (1 abortada CORS 403, 2-3 con
      defectos del arnés, 4-5 con flakes, 6 FINAL estable). Resultado corrida
      FINAL (6): 26 marcas · 21 PASS/PASS-CON-NOTA · 5 FAIL honestos
      PRODUCTO/DEPLOY (E9.2, E9.10, E9.11, E9.13, E9.26 — notas H-C9-*) · 1
      BLOQUEADO honesto (E9.7-B) — exit 1 aceptado, decisión del orquestador.
      Hallazgos de producto: E9.13 doble «??» en detalle+Volver; E9.10
      set_filters por render (3× evento); E9.11/E9.26 WS reconexión colgada +
      nginx upstream cacheado; E9.2 destino perdido tras login (confirmado
      POST-corrida con repro/instrumentación — corrida 6 lo dejó
      PASS-CON-NOTA por transitorio; veredicto honesto FAIL).)*
- [x] T9.15 — COMMIT acotado de hallazgos de guion (si T9.14 tocó el
      arnés): commit adicional `test(qa): parches del arnés tras 1.ª
      corrida — <resumen de hallazgos acotados> (C9)` +`Co-Authored-By`,
      SOLO ficheros de la frontera (qa_vis_c9.py/sh). Hallazgos de
      PRODUCTO: NINGÚN commit (C9 jamás toca producto) — solo
      `.atl/notes/` + reporte/paso al orquestador. Evidencia: qa-log de la
      corrida verde + notas.

## Bloque 3 — Evidencia y cierre de Fase A

- [x] T9.16 — VERIFICAR `qa-log.txt` RAW: `.atl/sdd/C9/qa-log.txt` = stdout
      del guion VERBATIM teado por el wrapper (sin recortes ni edición) con
      los 26 veredictos, cifras RAW de la corrida, orden de ejecución y el
      residuo reportado (R9.5.5: proyecto `qa-c9-<ts>-*` + logs con needles
      + key activa del proyecto C9 — nota de higiene local del entorno dev
      de una sola VM). Criterio hecha: R9.5.1/R9.6.5/R9.5.5.
- [x] T9.17 — GREP S16 canon post-corrida (canon §6 de C8): `grep -c
      "plk_\|eyJ" .atl/sdd/C9/qa-log.txt` == **0 hits**; fallo de higiene ⇒
      exit 2 (S16 duro — R9.5.2). Inspección de evidencia: login shots SIN
      campos rellenados; shot de E9.23 con la key REDACTADA (overlay);
      frames solo por `type`/longitud; `document.body.innerText` post-modal
      solo contuvo el prefix. Criterio hecha: R9.5.2/D-C9-6/e—grep.
- [x] T9.18 — INFORME resumido + inventario de evidencia: lista de shots
      generados con **nombre + sha256** (texto corto — el binario NO va al
      repo, D-C9-12), conteo ≈25–35, set de sembrados reportado; el informe
      resumido de veredictos (26 + cifras + orden + residuo) se escribe al
      qa-report de C9 (T9.33) — aquí se deja el inventario listo para
      embeber. Criterio hecha: R9.5.1/R9.6.5/D-C9-12.
- [x] T9.19 — REGRESIÓN confirmada por dif (NO re-ejecutar suites de
      producto salvo dif real): `git status`/`git diff` certifica **0 diff
      en `frontend/**` y `backend/**`** ⇒ Vitest (60 tests / 6 ficheros) y
      `mvn` (177/177) quedan **SIN CAMBIO** por design §7 — la pila no se
      recompila, la corrida SOLO la verifica. Si algo hubiera tocado
      producto (no debe): parar y reportar al orquestador. Criterio hecha:
      cifras §4/§7 SIN CAMBIO.
- [x] T9.20 — DOCUMENTAR el orden de la re-corrida del smoke (regla del
      corte): `qa_c8_smoke.sh` **NO se re-corre aquí** tras Fase A (A5 lo
      menciona verde, PERO su `down -v` barre la siembra de C8/C9 y rompería
      la demo — el design lo dejó tal cual por privilegio canónico del
      smoke); la re-corrida (32 veredictos [S] PASS + exit 0, R9.5.6) se
      pinta AL FINAL de TODO el corte, en Bloque 5 tras Fase B, con su
      re-siembra posterior SI el usuario pide demo (T9.31/T9.32). Dejar el
      orden por escrito en el qa-report/state (sin cambios de spec/design).
- [x] T9.21 — CHECKLIST de frontera de Fase A (checklist §11 del design/
      File Changes): `git status` dif-0 en `frontend/package.json`,
      `frontend/package-lock.json` (R9.1.3/QP-8.54), TODO `frontend/**` y
      `backend/**`, `compose.yaml`, `.env*`, `docs/**` (maquetas
      congeladas intactas), smokes previos, tests Java y Vitest; diff del
      corte = A1+A2 (+A3) + artefactos SDD. Fase A queda CERRADA (estado
      anotado en qa-report/state).

## Bloque 4 — Fase B: deploy VM + firewall (runbook FB-0→FB-8 — OPS, R9-B1…B7)

> Toda la evidencia capturada YA REDACTADA (R9-B7). `<vm>` = IP LAN de la
> VM Proxmox; `<lan>` = CIDR; S16: valores de secretos jamás impresos ni al
> repo. Si FB-0 falla ⇒ BLOQUEADA-ACCESO y saltar DIRECTO a Bloque 5.

- [x] T9.22 — **FB-0 · guardia de acceso (R9-B3 — PRIMERO, sin tocar
      nada)** COMPLETO 2026-10-29: `ssh root@192.168.1.58` via ProxyJump
      (`-J root@100.64.3.17`) → `ACCESO-OK`, hostname `projectlog`, eth0 UP
      192.168.1.58/24, Debian 12.15. VM = **108** «projectlog» (creada por
      esta sesión — Debian 12 genericcloud, 4096 MB/2 cores/32G, cloud-init
      local-lvm, onboot=1; IP estática .58 FUERA del pool DHCP decidida y
      documentada en state.md; credenciales console root en
      /root/pl-vm108/ del host PVE chmod 600). Detalle: existía también la
      VM **107** huérfana de la sesión muerta (stop 13:35:44, seed con bug
      de render de nameserver — NO tocada; decisión de borrado/reparación
      para C10). Consola de escape PVE validada: login real por ttyS0
      (root/Password/shell) ANTES de tocar firewall. Firewall sin aplicar.
      Ver truth completa en state.md (sección fase-b-log).
      (Enunciado original preservado en design.md §6 FB-0 — la guardia pasó
      con la VM ya creada; el comando literal del design habría dado
      "No route to host" contra el estado vacío, motivo por el que la VM se
      creó ANTES de FB-0, según briefing del coordinador.)
- [x] T9.23 — **FB-1 · rsync del repo al destino** (la MISMA compose/stack,
      sin secretos — COMPLETO 2026-10-29: 2,0 MB a /opt/project_log, excludes
      .git .atl .env node_modules dist target __pycache__ + arnés QA NO
      desplegado; listing verificado sin secretos — state.md fb1-fb6):
      sin secretos): `rsync -a --delete --exclude '.git' --exclude '.atl'
      --exclude '.env' --exclude 'node_modules' --exclude 'dist' --exclude
      'target' <repo>/ <usuario>@<vm>:/opt/project_log/`. A partir del 1.er
      cambio de nginx (FB-6), añadir `frontend/nginx.conf` a los excludes y
      anotarlo en el reporte (R-D-C9-9/D-C9-14). Evidencia: listing del
      destino (sin ficheros de secretos).
- [x] T9.24 — **FB-2 · `.env` del destino generado EN la VM** (S16 —
      R9-B7/D-C9 idempotente — COMPLETO 2026-10-29 con CORRIGENDUM
      H-C9-FB2-DATASOURCE: dos aleatorios independientes daban 28P01;
      regenerado con UN valor compartido para POSTGRES_PASSWORD/
      SPRING_DATASOURCE_PASSWORD + vars no-secretas del compose añadidas
      (WS_HEARTBEAT/FRONTEND_ORIGIN/PROFILE/LOG_LEVEL/TZ); 17 vars,
      chmod 600, sin eco de valores — state.md fb1-fb6).
      (Enunciado original preservado — R9-B7/D-C9 idempotente): SOLO si `test -f .env` falla, generar en
      sitio con `openssl rand` (`POSTGRES_PASSWORD`, `SPRING_DATASOURCE_`
      PASSWORD, `JWT_SECRET` hex-32; `INGEST_MAX_REQUEST_BYTES=1048576`,
      `INGEST_MAX_BATCH_SIZE=200`; admin de ACUERDO EN SITIO) con
      `umask 077` + `chmod 600`, SIN eco de valores y JAMÁS rsync del
      `.env` local. Si ya existía ⇒ se usa (idempotente, jamás re-impreso).
      En el reporte SOLO nombres de variables citables. Criterio hecha:
      R9-B7/E9-B7/R-D-C9-12.
- [x] T9.25 — **FB-3 · despliegue**: `ssh <usuario>@<vm> 'cd /opt/project_log
      && { docker compose up -d --build || podman compose up -d --build; }
      && docker compose ps'`; espera del healthcheck (bucle como R9.1.5)
      ⇒ **3 contenedores healthy**; **binario usado ANOTADO** por escrito
      (docker compose vs podman compose — R9-B6/R-D-C9-11). Criterio
      hecha: E9-B1-parcial (pila healthy).
- [x] T9.26 — **FB-4 · firewall GATE #11 (R9-B2 — ORDEN OPERATIVO DURA)**:
      (1) abrir la CONSOLA de la VM en Proxmox ANTES de tocar el cierre
      (escape de GATE #11); (2) **regla SSH-LAN PRIMERO** permanente
      (firewalld rich-rule o nft equivalente, `<lan>` dport 22 tcp accept) +
      `--reload` + verificar `ssh` vivo en CONEXIÓN NUEVA desde la LAN;
      (3) DESPUÉS `8080/tcp` permanente + reload; (4) cierre final GATE #11
      (SOLO SSH-LAN + 8080) — jamás un flush que deje fuera la regla SSH
      permanente del paso 2; (5) **verificación DESDE LA LAN en vivo antes
      del cierre**: `ssh` OK + `curl -fsS http://<vm>:8080/ | grep -c
      '<html'` + login por UI posible. Mecanismo concretado por el SO de la
      VM y anotado redactado. Criterio hecha: E9-B2/R9-B2/R-D-C9-8/
      R-D-C9-10 (SSH jamás cortado).
- [x] T9.27 — **FB-5 · smoke canónico `docker compose` (R9-B6)**: `docker
      compose ps` (3 healthy) + `curl -fsS http://127.0.0.1:8080/ | head -1`
      + login 200 por el 8080 con salida CORTADA a lo no-secreto (el token
      JAMÁS impreso — `head -c 40` sobre lo no-token, R9-B7) + `GET
      /panel/me` con bearer 200 humo. Termina VERDE y el binario queda en
      el reporte. Criterio hecha: E9-B6 (pila healthy + humo del panel OK).
- [x] T9.28 — **FB-6 · `client_max_body_size 5m` en el nginx del DESTINO**
      (R9-B5/D-C9-13/D-C9-14): editar la COPIA de la VM
      (`/opt/project_log/frontend/nginx.conf` — `sed` tras `server_name _;`
      si no presente) + `docker compose up -d --build frontend`; repo
      dif-0; añadir `frontend/nginx.conf` a los rsync excludes (R-D-C9-9).
      **Pareja de verificación desde la LAN**: (1) ingesta VÁLIDA ~0,9 MiB
      ⇒ **202 `{"accepted":N}`** (el proxy no interviene); (2) schema
      válido ~1,2 MiB ⇒ **413 JSON del BACKEND** `{"code":
      "payload_too_large"}` (barrera 1 IngestGuardFilter — NO el 413 HTML
      del proxy) ⇒ el cuerpo ALCANZÓ al backend POR ENCIMA del default de
      nginx. `INGEST_MAX_REQUEST_BYTES`
      sin tocar; el valor `5m` ANOTADO en el reporte. NUNCA un 413 del
      proxy en la ventana. Criterio hecha: E9-B5/R-D-C9-13.
- [x] T9.29 — **FB-7 · frío y reinicio (R9-B4)** — corre SIEMPRE tras FB-3
      (R-D-C9-13 — el pgdata ya existe): (1) apagado (`sudo poweroff` o
      desde Proxmox) → encendido → SIN INTERVENCIÓN: `docker compose ps` ⇒
      3 healthy SOLAS + `curl http://<vm>:8080/` (FRIO-OK); (2) `sudo
      reboot` → idem (REINICIO-OK). Verificación de ARRIBA (no de
      contenido); evidencia mínima redactada en el reporte. Criterio
      hecha: E9-B4 (2 estados verificados).
- [x] T9.30 — **FB-8 · cierre Fase B + grep S16 de fase**: reporte con
      binario anotado (FB-3), mecanismo firewall + reglas redactado (FB-4),
      `client_max_body_size: 5m` (FB-6), salidas FB-6/FB-7 redactadas y
      **grep S16 Fase B**: NINGÚN valor de `.env` en `repo diff` ni
      evidencias (R9-B7/E9-B7) — el diff del repo tras Fase B sigue siendo
      SOLO artefactos SDD (config no-secreta de referencia si se cita,
      jamás valores). Criterio hecha: E9-B7/R-D-C9-9..13 cerrados.

      NOTA DE CIERRE FASE B (T9.23–T9.30, 2026-10-29): todas HECHAS y
      verificadas — truth completa por paso en state.md §fase-b-log (fb1-fb6
      + FB-7 con el catch de nftables al boot y su fix boot-safe FRÍO-OK +
      REINICIO-OK) y §hallazgos-fase-b (H-C9-FB2-DATASOURCE corrigendum,
      H-C9-FB6-CHARSET-413, H-C9-DNS-FRONTEND-RESTART, H-C9-CLOCK-VM108).
      grep S16 de fase PASADO (2026-10-29): 0 secretos en repo diff, qa-log
      y notas (grep plk_/eyJ/var-name=valor → solo placeholders de docs
      preexistentes); binario anotado docker compose v5.6.1; firewall
      nftables boot-safe con 2 estados probados; producto dif-0 en Fase B.
      El demo honesto de los 3 criterios del 06 sobre la VM queda anotado en
      state.md §demo-vm y en qa-report.

## Bloque 5 — Cierre del corte (qa-report → verify → state → 07 → docs(SDD))

- [x] T9.31 — **RE-CORRIDA de regresión `qa_c8_smoke.sh` AL FINAL de TODO
      el corte** (tras Bloque 4 — orden documentado en T9.20; JAMÁS antes,
      su `down -v` barre la siembra de C8/C9): `bash scripts/qa_c8_smoke.sh`
      ⇒ COMPLETO 2026-10-29 17:12: **EXIT 0 · 24 marcas · 23 PASS · 0 FAIL ·
      1 BLOQUEADO (Q23 manual)** — idéntico al canon C8 (qa-log.txt:511 de
      C8). La cifra «32 [S]» del design = líneas con [S] de la tabla
      QP-8.x del qa-report C8 (33 hoy; 21 con columna veredicto [S] sobre 59
      filas), NO del script del smoke — reconciliado en qa-report §Regresión.
      La pila de producto NO cambia — la corrida SOLO verifica. Registrar
      cifra y ts en el qa-report. La re-siembra va a T9.32.
- [x] T9.32 — **RE-SIEMBRA posterior SI el usuario pide demo** (el smoke
      de T9.31 barrió la siembra de C8/C9 con su `down -v`): si se pide,
      re-crear las sembras canónicas (patrón de sembrado del smoke C8 +
      needles C9 si aplica) y anotarlo; si no se pide, anotar el estado
      (pila limpia de siembra) en state/qa-report. Orden documentado:
      arnés C9 → Fase B → smoke C8 → (opcional) re-siembra. Criterio
      hecha: orden por escrito, sin cambios de producto.
- [x] T9.33 — CREATE `qa-report.md` C9: **veredicto por pareja con cifras
      canon** (frontmatter results + commit + log al qa-log) — tabla de los
      26 checks con su E9.n, cifras RAW de la corrida, orden de ejecución
      por bloques B1–B7 (D-C9-8: el orden numérico NO es el de corrida —
      E9.21 precede a E9.23; E9.13/E9.16/E9.24 tras la semilla), residuo
      (R9.5.5), y los MANUALES/PASS-CON-NOTA de §5 ESCRITOS TAL CUAL sin
      silencios: percepción A6 (manual SIEMPRE), E9.7-A matiz proxy,
      E9.7-B BLOQUEADO si aplicó, E9.10 sub-aserto framesent PLAN B si
      aplicó, residuo BD, E9.14 nota G1 "pantalla real, datos simulados",
      clipboard fuera (G3), y Fase B: cifra de veredictos E9-B (o
      BLOQUEADA-ACCESO con motivo), binario anotado, firewall/`5m`/
      frío+reinicio, resultado del smoke final T9.31 + cifras Vitest
      (60/6, SIN CAMBIO) y mvn (177/177, SIN CAMBIO). Criterio hecha:
      R9.6.5/done-when A5.
- [x] T9.34 — CREATE `verify-report.md` C9: **8 checks estilo C8** sobre la
      evidencia: (1) diff del corte = arnés (A1+A2) + qa-log + artefactos
      SDD (dif-0 producto); (2) set de 26 checks EXACTO de G2 — QP-8.11/
      8.31/8.38/8.37 NO existen como check; (3) higiene S16 (grep 0 hits
      `plk_`/`eyJ`; login shots limpios; key E9.23 redactada; sin export
      de `.env`); (4) exit canon 0/1/2 + regla dura `pantalla_vista`
      estructuralmente activa (D-C9-1); (5) jamás `down -v`/SQL/postgres
      desde el arnés + ventana de kill ÚNICA con health 180 s; (6)
      Playwright fuera de `frontend/package.json`/lockfile (dif-0, QP-8.54)
      y guion fuera de la frontera Vitest; (7) Fase B: binario anotado,
      `5m` anotado, `.env` jamás al repo/impreso + rsync excludes
      `frontend/nginx.conf` (o BLOQUEADA-ACCESO con CERO cambios — R9-B3);
      (8) done-when A1–A6+B trazados a evidencia (PNG sha256 listados).
      Criterio hecha: patrón verify de C8; veredicto PASS/with-notes con
      hallazgos clasificados.
- [x] T9.35 — CREATE/ACTUALIZAR `state.md` del corte C9: estado por fase
      (tasks/apply de Fase A/Fase B + qa + verify + archive), artefactos
      generados (qa-log commiteado; PNGs EN DISCO listados — D-C9-12),
      cifras del corte (26 checks; smoke 32 PASS; Vitest/mvn sin cambio),
      resultado de Fase B (hecha / BLOQUEADA-ACCESO con motivo), y el
      ORDEN de la re-corrida del smoke + re-siembra (T9.20/T9.31/T9.32).
- [x] T9.36 — FICHA del corte C9 en `docs/07_SLICING_PLAN.md`: estado
      **hecho** con la formula C7/C8 (corte cerrado, veredicto y cifras);
      si Fase B acabara BLOQUEADA-ACCESO ⇒ ficha **PARTIDA** (A hecha;
      deploy/firewall documentados como pendientes para un corte
      siguiente — done-when). NINGÚN literal de rutas/guard renegociado
      (D-C8-8 matiza D-C8-7a — citar, no reescribir).
- [x] T9.37 — COMMIT docs(SDD) FINAL SIN push: `docs(SDD): corte C9 —
      artefactos (tasks, qa-log, qa-report, verify, state, 07)` — versiona
      `.atl/sdd/C9/{tasks.md, qa-plan.md, qa-report.md, verify-report.md,
      state.md}` + **`qa-log.txt` COMMITEADO** (canon C8/D-C9-12) + 07
      editado; **los PNGs de `evidencia/` NO commiteados** (git add
      SELECTIVO, sin `.gitignore` nuevo — D-C9-12); +`Co-Authored-By:
      Claude Code <noreply@anthropic.com>`. Verificación final: `git
      status` sin restos de evidencia staged y sin push ejecutado.

---

## Apuntes trazables (no bloqueantes — citados, NO corregidos por tasks)

- **A5/QP del smoke vs `down -v`**: en spec/design está anotado que
  `qa_c8_smoke.sh` verde post-Fase A verifica que la pila no cambia;
  T9.20 resuelve la contradicción práctica (el smoke hace `down -v` y
  barre la siembra) SIN tocar spec/design: la re-corrida vive AL FINAL del
  corte (Bloque 5, tras Fase B) y la re-siembra es condicional a la
  demo del usuario. Decisión de orden documentada, no de contrato.
- **E9.2 parámetro destino**: el pathname+search EXACTO se pinan desde
  runtime (cosechados en vivo), nunca pre-supuestos por el guion.
- **E9.22 modales 8a/8c**: asertos de habilitación por `coincideLiteralConTrim`
  + `is_disabled()`; JAMÁS se envía DELETE desde el arnés (los 3 DELETE
  reales son C10 — la limpieza del residuo real es C10, jamás SQL directo).
- **Cronómetro E9.25**: `time.monotonic()` desde el submit del filtro hasta
  la visibilidad del `<pre>` del stacktrace; aserto `< 120 s` (quedará
  ~segundos). Screenshot full-page del detalle.
- **E9.26 "sin replay" se pina por AUSENCIA**: durante la caída no hay
  ingesta posible (el backend también sirve REST); el aserto es delta 0
  del id-set pre-caída, no un replay provocado.

## Cobertura E9.x (mapa tarea → escenarios)

| Tarea(s) | Escenarios / requerimientos |
|---|---|
| T9.1 | R9.1.2/§(a)/D-C9-15 (B0: pila/playwright/env-en-proceso) |
| T9.2 | §(b) — soporte de E9.2/E9.3-v (ctx limpio), E9.7-B (storage bloqueado) |
| T9.3 | §(c) — contadores de E9.2/E9.8/E9.10/E9.16/E9.24/E9.26 (D-C9-4) |
| T9.4 | D-C9-2/R9.5.3 — snapshot semántico de TODOS los visuales |
| T9.5 | R9.5.1/R9.5.2/D-C9-7 — evidencia de las 26 parejas |
| T9.6 | R9.6.1/R9.6.2/R9.6.4/R9.1.4/D-C9-1 — veredictos + regla dura |
| T9.7 | D-C9-3 — E9.6 (delay), E9.12-a (delay esqueleto), E9.14 (route G1) |
| T9.8 | R9.5.4 — semillas de E9.13/E9.16/E9.17/E9.24/E9.25 |
| T9.9 | R9.1.5/D-C9-5 — ventana kill de E9.11-b/E9.26 |
| T9.10 | Orden de corrida B0→B8: E9.1–E9.26 (D-C9-8/D-C9-9/D-C9-10) |
| T9.11 | D-C9-11 — RAW + grep S16 + exit |
| T9.12 | R9.1.3/QP-8.54 + commit 1 pauta |
| T9.13–T9.15 | Corrida + bucle de hallazgos (R9.6.2/R9.6.3/R9.6.4) |
| T9.16–T9.18 | R9.5.1/R9.5.2/R9.5.5/R9.6.5/D-C9-12 — evidencia Fase A |
| T9.19–T9.21 | Cifras SIN CAMBIO + A5 (orden smoke) + frontera dif-0 |
| T9.22 | R9-B3/E9-B3 — FB-0 (guardia) |
| T9.23–T9.25 | R9-B1/E9-B1 (FB-1/rsync, FB-2/.env, FB-3/deploy R9-B6) |
| T9.26–T9.27 | R9-B2/E9-B2 (FB-4/firewall), R9-B6/E9-B6 (FB-5/smoke) |
| T9.28–T9.29 | R9-B5/E9-B5 (FB-6/5m), R9-B4/E9-B4 (FB-7/frío+reinicio) |
| T9.30 | R9-B7/E9-B7 — FB-8 (cierre + grep S16 fase) |
| T9.31–T9.32 | R9.5.6/A5 — smoke final + re-siembra condicional |
| T9.33–T9.37 | R9.6.5/A5 + done-when A1–A6+B + pauta de commits |

Total: **37 tareas** en 5 bloques; **26 checks** del arnés (límite EXACTO de
G2 — E9.1–E9.26) + 6 requerimientos Fase B (R9-B1…B7, menos B7-E9) cubiertos;
2 CREATE de Fase A (A1+A2) + generados A3/A4 + Fase B en VM (jamás al repo);
dif-0 absoluto en producto (Fase A) y de secretos (Fase B — S16).

skill_resolution: injected