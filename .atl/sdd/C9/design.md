---
project: Project_Log
change: C9
artifact: design
last_updated: 2026-10-29
status: complete
---

# Design — C9 · Arnés QA visual Playwright + deploy/firewall (OPS)

> Fase sdd-design del corte C9. Este documento **IMPLEMENTA
> arquitectónicamente** el spec (`spec.md`, status done-gate — G1–G4 APROBADOS
> 2026-10-29) y NO lo renegocia: **NO añade ni quita checks** (límite EXACTO:
> **14 parejas + 9 extras G2 + 3 criterios del 06 = 26 checks**; QP-8.37 FUERA
> — decisión del orquestador, se sigue el set del gate). Fuentes normativas:
> spec C9 (R9.x, R9-B1…B7, escenarios E9.1–E9.26 / E9-B1…B6, §4 cifras, §5
> manuales, done-when) + explore C9 §B–§F (técnicas por pareja, higiene,
> semillas, clíaves de `.env`, patrón del repro `/tmp/qa_c9/repro_detalle.py`)
> + canon de smokes de C8 (`.atl/sdd/C8/qa-plan.md` §6/qa-log commiteado,
> `marca()`, exit 0/1/2) + `docs/02_TRD.md` §2.7/§6.7 + `compose.yaml` /
> `frontend/nginx.conf` verificados en HEAD `c144f3d`. Literales de UI/API =
> contrato CITADO (spec C9 §1, maquetas congeladas `docs/mockups/`), nunca
> redefinido. **Cero ficheros de producto en Fase A** (R9.1.3 —
> `frontend/package.json` y lockfile dif-0, QP-8.54 intacto).

## 0. Resumen del diseño

```
 corrida ÚNICA (localhost:8080, pila ya arriba — NO se recompila, se verifica)
 ─────────────────────────────────────────────────────────────────────────────
 scripts/qa_vis_c9.sh  (wrapper MAY — guarda de entorno + RAW + grep S16)
   └─ scripts/qa_vis_c9.py  (Playwright sync 1.64.1, chromium headless, pip)
        ├─ infra: dotenv-parser EN PROCESO (sin eco, R9.1.2) · login por UI
        │   (inputs + Entrar + wait_for_url "**/logs" — jamás token a mano,
        │   salvo storage basura de E9.3/E9.7-B) · fábrica de contextos
        │   (principal / limpio / storage-bloqueado) · contadores request/
        │   response (panel/me|logs|projects|auth|ingest) · ws.on("framesent")
        │   (counts por type/length — S16) · INTERCEPTS de red (route)
        │   ├─ snapshot semántico: get_by_text/inner_text/roles/getComputedStyle
        │   ├─ screenshots E9n_*.png  → .atl/sdd/C9/evidencia/
        │   ├─ veredicto marca() estilo C8: E9.n PASS/PASS-CON-NOTA/FAIL/
        │   │   BLOQUEADO (detalle) — NINGÚN PASS sin pantalla real (R9.1.4)
        │   └─ exit 0/1/2 (canon smokes; 2 = higiene / PILOTO CAÍDO / pila caída)
        ├─ ingestas de semilla REST (X-API-Key en MEMORIA) — needles qa-c9-<ts>
        ├─ REGLA destructiva (ÚLTIMA, fase aparte): stop backend → up -d
        │   backend en try/finally + health 180 s → E9.11-b/c + E9.26 (D-C9-5)
        │   JAMÁS down -v / postgres / volúmenes (R9.1.5)
        └─ cierre: informe resumido (26 veredictos) + residuo (R9.5.5) + grep
            S16 (0 hits plk_/eyJ) → exit
```

**Fase B (separada, R9-B1…B7)**: SSH check → rsync sin secretos → `.env`
generado EN la VM → `docker compose up -d --build` en destino → firewall
GATE #11 (SSH-LAN PRIMERO + consola de escape ANTES de 8080) →
`client_max_body_size 5m` en el nginx del destino → smoke `docker compose`
verde → frío/reinicio → ingesta grande sin 413 → reporte redactado. Si SSH
falla ⇒ **BLOQUEADA-ACCESO, CERO cambios** (R9-B3).

---

## 1. Arquitectura del arnés `scripts/qa_vis_c9.py` — secciones internas

Un solo guion Python (R9.1.1), **secciones** (comentarios de cabecera, no
paquetes — es un guion de QA, NO código de producto):

| Sección | Helpers | Qué fija |
|---|---|---|
| **(a) infra/env** | `cargarEnvProceso()` — parser dotenv mínimo en proceso (`ADMIN_INITIAL_USERNAME`/`ADMIN_INITIAL_PASSWORD` y claves del compose SOLO a variables internas); `verificarPila()` — `GET /actuator/health` + `GET /` 200 al arranque; si falla ⇒ **exit 2** (pila/entorno no disponible — R9.6.2, jamás falso PASS) | Secretos jamás en `os.environ` exportado, en argv, ni impresos |
| **(b) navegación/login** | `sesionPrincipal()` — login por UI (fill `input[type=password]` + click botón Entrar + `wait_for_url("**/logs")`); `contextoLimpio()` (guard/404 sin sesión — E9.2/E9.4-b/E9.3-variante); `contextoStorageBloqueado()` (`add_init_script` que LANZA sobre `window.sessionStorage` — E9.7-B) | Token jamás seteado a mano salvo variantes (string basura, jamás JWT real) |
| **(c) observación (contadores)** | `registroRed(page)` — listeners `request`/`response` con contadores por patrón (`panel/logs`, `panel/projects`, `panel/me`, `auth/login`, `ingest/batch`); `ws.framesent` y `ws.close`/`open` con contador por `type`/longitud del frame (S16: jamás contenido) | Toda la asertación temporal (orden, 0/1/2 peticiones) nace de contadores, no de "esperar y mirar" |
| **(d) snapshot semántico** | `textoVisible(page, literal)`, `rolPagina(...)`, `claseComputada(locator)` (`getComputedStyle` de badges/indicador/botón rojo: FATAL `#7f1d1d`-familia, verde Conectado `#16a34a`-familia, `bg-red-300`→`bg-red-700`), `filasTabla()` (ids de la tabla DOM) | D-C9-2: literales + roles + clases; píxeles NO (R9.5.3) |
| **(e) evidencia** | `shot(n, etiqueta)` — screenshot a `.atl/sdd/C9/evidencia/E9n_<etiqueta>.png`; `shotRedactado(n, locator)` — inyecta overlay opaco sobre la key JUSTO para el shot y lo retira después (E9.23; R9.5.2); login shots con campos vacíos (vaciar o capturar el estado de error) | ≈25–35 PNG; naming D-C9-7 |
| **(f) veredicto** | `marca(id, veredicto, nota=None)` — lista `RESULTADOS` estilo canon C8 (`E9.n: PASS …`); helpers `passO(nota)`, `bloqueado(detalle)`, `fail()`; al cierre: tabla ordenada + `sys.exit(0|1|2)` | R9.6.1/R9.6.2; FAIL nunca auto-arreglado (R9.6.3) |
| **(g) intercepts de red** | `interceptar(page, patrón_globe, respuesta, retardo=None)` — envoltura de `page.route` usada por: E9.6 (delay 600 ms del POST login + contador), E9.12-a (delay 1.ª `**/panel/logs`), E9.14 (`**/panel/projects` → `{"items":[],"next_cursor":null}`) y E9.15 (variante rehidratada de URL, sin intercept — el 400 lo emite el backend real) | D-C9-3: el intercept simula DATOS, jamás sustituye a la pantalla |
| **(h) semillas/ingesta** | `ingestar(logs[], key)` — `POST /api/v1/ingest/batch` con `X-API-Key` (202 `{"accepted":N}` asertado); needle `qa-c9-<ts>-<caso>` en MESSAGE; `timestamp` en pasado reciente | R9.5.4; key de `POST /panel/projects/{id}/api-key/regenerate` SOLO en memoria |
| **(i) fase destructiva** | `pararBackend()` / `levantarBackend()` — `docker compose stop backend` / `docker compose up -d backend` vía `subprocess` (SOLO servicio `backend`, contenedor `project_log_backend…`), `bucleHealth(180 s)` en `finally`; restauración fallida ⇒ **exit 2 "PILOTO CAÍDO"** | R9.1.5; UNA ventana de kill por corrida (D-C9-5) |

Guardas transversales del guion: (i) **regla dura estructural** — el helper de
veredicto PASS exige que la pareja haya pasado por `navegar()` + snapshot +
`shot()` (flag interno `pantalla_vista` que el propio helper de check setea;
si un check intenta `passO` sin él, el guion se auto-marca FAIL — falso PASS ⇒
defecto del arnés, R9.1.4); (ii) **grep S16 post-corrida** sobre el qa-log RAW:
`grep -c "plk_\|eyJ"` == 0, fallo ⇒ exit 2 (R9.5.2); (iii) **higiene de
returns de `evaluate`**: SOLO booleanos/keys/conteos (D-C9-6).

## 2. Orden de corrida por bloques (R9.1.1 — con las dependencias de datos resueltas)

El spec §1 fija la secuencia (1)…(5)+destructivo. Dos precondiciones de DATOS
la matizan (decisión **D-C9-9**, trazada): (A) la ingesta de semillas y los
checks que la consumen exigen la **API key del proyecto C9**, que nace de
E9.21 (creación por UI) + E9.23 (regenerate) en el bloque de proyectos; (B)
E9.13 exige ≥60 logs del needle. Por eso los checks dependientes de semilla se
corren en su bloque nativo de `/logs`/detalle PERO **después** del bloque de
proyectos; el resto respeta el orden del spec 1:1. El destructivo va SIEMPRE
AL FINAL (última fase, sin re-corrida). El logout (E9.8) destruye la sesión ⇒
va al final de SU bloque con re-login inmediato por UI (D-C9-10).

| # | Bloque | Checks | Por qué aquí |
|---|---|---|---|
| **B0** | Pre-comprobaciones | — (sin veredicto) | `verificarPila()` (health + `/` 200), playwright instalado (si no ⇒ exit 2), `.env` cargado en proceso, `mkdir evidencia/`, ts del needle |
| **B1** | Login + routing/guard/404 | **E9.1** (9 rutas secuencial), **E9.2** (ctx limpio: guard→401 `/me`→`/login?destino`→regreso), **E9.3** (con sesión `/login`→`/logs`; variante token-basura en ctx limpio), **E9.4** (404 panel con/sin sesión), **E9.5** (401 inline), **E9.6** (doble submit, route delay), **E9.7-A** (restauro proxy temporal), **E9.7-B** (storage bloqueado — BLOQUEADO honesto si la emulación no resulta) | R9.1.1-bloque (1); E9.2 y variantes usan contexto NUEVO (storage limpio) ⇒ no contaminan la sesión principal |
| **B2** | Storage/hibernación | **E9.9** (JWT solo sessionStorage — booleanos), **E9.8** (logout sin red — AL FINAL del bloque; tras él **re-login por UI**, D-C9-10) | R9.1.1-bloque (2); el logout debe ir tras todo lo que usa su sesión y antes de los bloques de datos |
| **B3** | `/logs` estados sin semilla | **E9.10** (set_filters 6 claves contado por framesent + Reiniciar; el needle del filtro NO requiere resultados — las aserciones son de conteos/URL), **E9.12** (esqueleto con route delay / vacío por filtro / 400 from>to con lista conservada), **E9.15** (banner 400: `filter_from_to_inverted` sin field; `filter_invalid_value` field=`level` vía `?level=PANICA`) | R9.1.1-bloque (3) — solo sus checks no dependientes de semilla |
| **B4** | Proyectos (crea la semilla) | **E9.21** (PRIMERO — flujo 2 UI: 201 `qa-c9-<ts>-e45` + aviso literal; 409 inline; 400 por campo — el 201 crea el **proyecto C9**), **E9.19** (maqueta 04 — el needle del proyecto C9 es visible entre tarjetas), **E9.20** (maqueta 05 + variante 404), **E9.22** (modales 8a/8c — habilitación por trim, SIN petición DELETE), **E9.23** (regenerate del proyecto C9 → revelación única redactada → key SOLO en memoria), **E9.14** (route-interception vacío + CTA navegada — PASS-CON-NOTA G1, AL FINAL del bloque: la CTA navega real al formulario sin depender del estado simulado) | R9.1.1-bloque (4) con el orden interno EXACTO del spec (E9.21 primero); E9.19/E9.20/E9.22/E9.23 consumen el proyecto creado; **RE-ORDENACIÓN D-C9-8**: E9.21 (QP-8.45) precede a E9.23 (QP-8.43) — inverso del orden numérico del qa-plan, exigido porque la key del proyecto C9 debe regenerarse ANTES de las ingestas |
| **B5** | Semillas/ingesta | — (sin veredicto propio) | Con la key ya en memoria: batch ≥60 needle E9.13 (1 request, ≤200, `timestamp` pasado reciente), 1 ERROR production con exception+metadata (E9.17/E9.25), 1 log SIN exception/metadata (E9.17-b), correlation needles |
| **B6** | `/logs` y detalle CON datos | **E9.13** (Volver conserva query+filas+1.ª fila, sin refetch, dedup; F5 ⇒ acumulado limpio), **E9.16** (banda En vivo, lote de 3 en orden, batch_id, 0 refetch), **E9.24** (CRITERIO 1 del 06 — 2.ª ventana de lote de 3, idéntica técnica; screenshots antes/después), **E9.11-a** (indicador "● Conectado" en la barra), **E9.17** (bloques detalle CON/SIN exception+metadata + cabecera maqueta 03), **E9.25** (CRITERIO 2 — filtros level ERROR+production+text needle → detalle → `<pre>` del stacktrace con cronómetro < 120 s) | Datos de semilla listos; E9.16 y E9.24 comparten técnica (batch) pero corren como checks INDEPENDIENTES con ventanas separadas; E9.11-a y E9.11-c se observan aquí (c) antes de la fase destructiva |
| **E9.11-c** | Navegación fuera de /logs | (parte de R9.2.11) goto `/proyectos` ⇒ indicador y banner AUSENTES (S8-4) | No destructivo — cierre del bloque no-destructivo antes del kill |
| **B7** | **FASE DESTRUCTIVA (ÚLTIMA — ventana única de kill)** | snapshot pre-caída (id-set de la tabla en `/logs` vivo), **E9.26** (b)+**E9.11-b** (misma ventana caída): `stop backend` → "○ Desconectado" + banner literal + "Reconectando (intento N)…" con N creciente (polling) → `up -d backend` en `finally` + bucle health **180 s** → **E9.26** (post): "● Conectado", banner RETIRADO, `set_filters` REENVIADO (2.º frame), **1 refetch** `GET /panel/logs`, ids ÚNICOS + **delta 0** contra el id-set pre-caída | **D-C9-5**: la MISMA ventana cosecha E9.11-b/c y E9.26 (R9.1.5 — no se para dos veces; 2.º ciclo solo si un sub-estado lo exige, documentado). Durante la caída NO hay ingesta (el backend también sirve REST) ⇒ "sin replay" se pina por AUSENCIA |
| **B8** | Cierre | — | Informe resumido con los 26 veredictos + cifras RAW + residuo (R9.5.5) + grep S16 del RAW (0 hits `plk_`/`eyJ`) → exit |

Confirmación pedida por el gate: **NINGÚN check del set ratificado destruye la
sesión antes de tiempo** — los destructores de sesión están acotados y
ordenados: logout (E9.8) al final de B2 con re-login inmediato; token-basura
(E9.3-variante) y storage-bloqueado (E9.7-B) en contexto NUEVO aparte; el
401→interceptor→`/login` NO se provoca en la sesión principal (QP-8.11/8.31/
8.38 FUERA — G2); el kill es la última fase. NINGÚN check interrumpe con
anterioridad la corrida: un FAIL de un check se MARCA y la corrida continúa
(con la sesiones/contextos que necesita), salvo guardas de entorno (exit 2) o
la fase destructiva (que va al final — si la corrida aborta ANTES por
excepción, el backend nunca se ha parado).

## 3. Decisiones de arquitectura

### D-C9-1 · Regla dura estructurada — el arnés no puede "presuponer"
**Decisión**: cada check tiene forma `navegar/abrir → asertar(over DOM vivo) →
shot()`; el helper de veredicto PASS exige la flag `pantalla_vista` de la
pareja; un `passO()` sin esa flag se transforma en FAIL del propio check con
la nota "falso PASS bloqueado por el arnés" (defecto del arnés — R9.1.4).
**Alternativa**: disciplina por convención de código — descartada: la regla
dura es BINDING y debe cumplirse por estructura, no por prudencia del autor.
**Rationale**: es el pin A1 del done-when; la única forma de hacerlo
verificable en la re-corrida es que el arnés se autovetee.

### D-C9-2 · Snapshot SEMÁNTICO (roles/textos/clases computadas) — píxeles NO
**Decisión**: las aserciones visuales usan `get_by_text`/`inner_text` sobre
literales LITERALES de contrato (el REAL renderizado se pina en vivo cuando la
maqueta no lo describe, p. ej. la 404 del panel — R9.2.4), roles/estructura y
`getComputedStyle` de los elementos coloreados (badges FATAL, indicador
verde/rojo, botón 8b `bg-red-300`→`bg-red-700`). **NUNCA** diff de píxeles
contra las maquetas (R9.5.3: usan CDN de Tailwind y datos de muestra — el
contrato es de literales/estructura). La RATIFICACIÓN estética final es
MANUAL A6 sobre los PNG.
**Alternativa**: pixel-diff o screenshot-diff — descartada de raíz (R9.5.3,
explícito en §0.2 NO-alcance).
**Rationale**: el humano ratifica el PNG; el arnés pina lo verificable por
máquina: literales/estructura/colores computados.

### D-C9-3 · Red de intercepts — solo DATOS simulados, jamás pantallas falsas
**Decisión**: `page.route` se usa en E9.6 (delay del POST, no sustituye la
respuesta real — `route.continue_()` retrasado), E9.12-a (delay de la 1.ª
`**/panel/logs` — la pantalla de esqueleto es real) y E9.14 (payload
`{"items":[],"next_cursor":null}` — pantalla real, datos simulados, nota
OBLIGATORIA en el veredicto). Fuera de esos tres, TODO lo demás pasa por la
pila real. El resto de errores (400, 404, 401) los emite el backend real.
**Alternativa**: interceptar también WS o 401s para acelerar la corrida —
descartada: violaría la regla dura (pantalla sin pila real) y el matiz G1 se
acota EXACTO al listado.
**Rationale**: G1 ratificó EXACTAMENTE la opción (i) para E9.14; el patrón se
extiende solo a los dos casos de delay que el spec/explora ya fijan.

### D-C9-4 · WS `framesent` para `set_filters` — con PLAN B documentado
**Decisión**: contador de frames cliente→servidor por `type` (logging SOLO por
`type`/`length` — S16): al abrir `/logs`, cambiar filtro y tras reconexión el
frame `set_filters` debe verse (6 claves con nulls, SIN `cursor` — shape
asertado por parseo del frame JSON en el contador, sin imprimir contenido).
El nombre EXACTO del evento se comprueba en el arranque de la corrida contra
la 1.64.1 instalada (`websocket.on("framesent")`). **PLAN B** (R9.2.10/§5): si
el evento no resultara observable, ESE sub-aserto se degrada a
**BLOQUEADO-anotado** con respaldo de conteo de handshakes WS/reconexiones —
jamás falso PASS ni FAIL injusto (R9.6.4).
**Alternativa**: inspección del DOM del indicador como proxy de "set_filters
enviado" — descartada: el sub-aserto del spec es del FRAME, no del indicador.
**Rationale**: explore §E.8 fijó el riesgo y el PLAN B; el degradado es
localizado (sub-aserto, no check entero).

### D-C9-5 · Kill ÚNICO cosechando doble veredicto (E9.11-b/c + E9.26)
**Decisión**: UNA ventana de kill/restauración al final de la corrida; en ella
se corren en orden: (1) snapshot id-set + indicador pre-caída (E9.26-pre);
(2) `docker compose stop backend` (SOLO servicio `backend`); (3) asertos de
E9.11-b = E9.26-en-caída ("○ Desconectado" + banner literal + "Reconectando
(intento N)…" con N creciente, lista sigue por REST); (4) `docker compose up
-d backend` en el `finally` + bucle health `GET /actuator/health` hasta 200,
timeout 180 s (start_period 60 s ⇒ ventana 180 s); (5) asertos de
E9.26-post (set_filters 2.º, 1 refetch, ids únicos, **delta 0**) — E9.11-c
(indicador/banner ausentes en `/proyectos`) se corren en B6, antes del kill,
porque la navegación cierra el WS. Restauración fallida ⇒ **exit 2 "PILOTO
CAÍDO"**. Un 2.º ciclo MAY solo si un sub-estado exigido no fuera observable,
documentado en el qa-log (R9.1.5).
**Alternativa**: dos kills (uno por pareja E9.11, otro por E9.26) — descartada:
R9.1.5 manda cosechar de la MISMA ventana y minimiza el riesgo del piloto.
**Rationale**: mismo backend, mismos estados de hook — la única diferencia es
el sub-aserto (E9.11 no exige delta del id-set); compartir ventana reduce el
riesgo destructivo sin perder nada de contrato.

### D-C9-6 · Higiene S16 visual — evaluaciones booleanas, shots redactados, grep canon
**Decisión**: (a) TODO `page.evaluate` que toca storage/DOM devuelve SOLO
booleanos/keys/conteos — el valor del token/key JAMÁS cruza la frontera del
navegador (E9.9/E9.23 matchean internamente `^eyJ`/`^plk_[A-Za-z0-9_-]{43}` y
devuelven bool); (b) shots de login SIN campos rellenados (vaciar antes del
shot o capturar el estado de error con campos limpios); (c) shot del modal de
revelación E9.23 con la línea de la key **REDACTADA** por overlay opaco
inyectado JUSTO para el shot; (d) frames WS al log solo por `type`/`length`;
(e) **grep post-corrida** sobre el qa-log RAW: 0 hits `plk_`/`eyJ` — fallo ⇒
exit 2 (S16 duro, R9.5.2); (f) el wrapper NUNCA exporta `.env` al shell (los
secretos viven en el proceso Python — D-C9-11).
**Alternativa**: redacción posterior del PNG con un editor — descartada: el
overlay inyectado ANTES del `screenshot()` garantiza que la key jamás exista
en el fichero.
**Rationale**: es el canon §6 de C8 aplicado a evidencias visuales; cada punto
cubre una fuga concreta del arnés (evaluate, stdout, PNG, frames, shell).

### D-C9-7 · Evidencia — naming `E9n_*.png` en `evidencia/` + informe resumido
**Decisión**: screenshots con patrón `E9n_<estado>.png` (p. ej.
`E9.13_03_volver_query_intacta.png`, `E9.26_02_banner_caída.png`) en el
subdirectorio `.atl/sdd/C9/evidencia/`; el qa-log RAW a
`.atl/sdd/C9/qa-log.txt` (stdout del guion verbatim); el informe resumido
(los 26 veredictos, cifras, orden, residuo) vive en el qa-report de C9
(R9.5.6/R9.6.5). **MATIZ RATIFICABLE**: R9.5.1 ejemplifica el prefijo
`e8-xx-estado.png` — se usa `E9n` porque los IDs DEL ARNÉS C9 son E9.n (§
escenarios C9): así cada PNG es trazable 1:1 a su check sin mirar el log.
El patrón es de naming, NO de contenido ni de cuenta (≈25–35 shots, R9.5.1
intacto).
**Alternativa**: prefijo `e8-xx` literal del ejemplo del spec — descartada:
los checks C9 tienen IDs E9.n; un prefijo E8 colaría con la evidencia de C8.
**Rationale**: trazabilidad inconfundible del artefacto de evidencia.

### D-C9-8 · Re-ordenación de la trazabilidad — tabla propia del design (§2)
**Decisión**: el design reemite la **tabla de trazabilidad del spec §3**
(check visual → QP-8.x → pareja de stack C8) con la AÑADIDURA del orden de
corrida real (columna "bloque" de §2 de este design): ahí se ve SIN AMBIGÜEDAD
que E9.21 (QP-8.45) precede a E9.23 (QP-8.43) y que E9.13/E9.16/E9.24 corren
tras la semilla — el orden numérico de IDs NO es el orden de corrida y NUNCA
se leerá como tal. (spec §1: dominios S9-1…S9-6 con sus R9.x.y; spec §3: tabla
de parejas de stack ya verdes, CITADAS, no re-pruebadas aparte de la semilla.)
**Alternativa**: reordenar los IDs del spec (E9.23→antes que E9.21) —
descartada: los IDs tienen su criterio en el spec DONE; renumerar rompería la
trazabilidad con el qa-plan (QP-8.x) y con los done-when (A1–A6).
**Rationale**: la re-ordenación vive en el ORDEN de corrida (una propiedad de
la ejecución) y se documenta ahí, no redefiniendo IDs.

### D-C9-9 · Reordenación por DEPENDENCIA DE DATOS (corrección a R9.1.1 literal)
**Decisión**: en §2 de este design, E9.13/E9.16/E9.24 (consumidores de semilla
y de la key del proyecto C9) corren en B5/B6 — DESPUÉS de E9.21+E9.23 — porque
la key de ingesta solo existe tras regenerate (R9.5.3: jamás regenerar keys de
proyectos preexistentes ⇒ no hay otra fuente). El resto de la secuencia es la
del spec 1:1, y la fase destructiva conserva su posición final.
**Alternativa**: crear el proyecto/la key por REST al principio — descartada:
R9.5.4 fija que el proyecto C9 nace por UI en E9.21 (flujo 2 DOBLE como
semilla); adelantarlo duplicaría el flujo o desplazaría su criterio visual.
**Rationale**: dependencias de datos > orden numérico; corrección LOCAL
(movimiento dentro del mismo corte, sin tocar contratos), documentada aquí
para que tasks/apply/qa no se pregunten.

### D-C9-10 · Logout E9.8 — al final de SU bloque, con re-login por UI
**Decisión**: E9.8 (logout sin red) corre al final de B2 destruyendo la sesión
principal (los evaluables previos de B1/B2 ya corrieron); tras el shot del
login final, el guion **re-loguea por UI** (2.º login — documentado en el
qa-log) para que B3–B7 tengan sesión. "UN SOLO login de sesión" de R9.1.1 se
implementa como "una sesión lógica por bloque de trabajo": el 2.º login es
efecto obligado de un check del set (el logout DEBE probarse con una sesión
REAL que destruir); jamás se crea una sesión de más ni con el token a mano.
**Alternativa**: ejecutar el logout en un contexto ESPEJO con su propio login —
descartada: el logout es un comportamiento del panel REAL de la sesión
principal (el hook limpia storage + cierra WS + `<Navigate>`; en un espejo el
aserto sería equivalente pero el costo de un 3.er contexto+login es mayor que
el de re-loguear); además el logout al final de B2 deja el storage limpio, que
es justamente lo que el flujo posterior necesita.
**Rationale**: el orden pedido por el gate ("el logout debe ir con SESIÓN NUEVA
o al final del bloque de navegación") se satisface por la 2.ª vía, la más
fiel al panel real.

### D-C9-11 · Wrapper `scripts/qa_vis_c9.sh` — SÍ (A): guarda de entorno y RAW, SIN exportar secretos
**Decisión**: el wrapper HACE: (1) comprobaciones de entorno baratas (pila
healthy con curl — si no, exit 2 antes de abrir el navegador); (2)
`python3 scripts/qa_vis_c9.py 2>&1 | tee .atl/sdd/C9/qa-log.txt` (RAW canon);
(3) grep S16 post-corrida sobre el RAW (0 hits `plk_`/`eyJ` — si falla,
reescribe el exit a 2 con guardia visible); (4) pass-through del exit del
guion. El wrapper NO hace: `set -a; source .env` — a diferencia del default
sugerido, **el `.env` NO se exporta al shell** (los secretos irían al environ
y a los subprocess de docker); el parser dotenv EN PROCESO (R9.1.2) es el
único camino de los secretos; los comandos `docker compose stop/up backend`
leen su `.env` por sí mismos (interpolación de compose, canon C1+).
**Alternativa**: sin wrapper (solo .py) — descartada MAY: el RAW + el grep
post-corrida + la guarda previa de entorno ya viven cómodos en shell y el
costo es 40 líneas; la alternativa contraria (wrapper que exporta .env) —
descartada: superficie de secreto innecesaria.
**Rationale**: canon C8 de smokes (RAW + higiene + exit) con la frontera S16
más estricta al no haber shell con secretos.

### D-C9-12 · Evidencia commiteada vs en disco — qa-log RAW SÍ; PNGs NO
**Decisión**: **`qa-log.txt` RAW se commitea** (canon C8 — .atl/sdd/C8/
qa-log.txt vive en el repo) y **los PNGs NO se commitean**: quedan en disco en
`.atl/sdd/C9/evidencia/` para la ratificación visual A6 del usuario (los PNG
de una BD dev real contienen datos del usuario — nombres de proyectos, logs;
el principio de canon es no meter binarios de evidencia con datos vivos al
repo). El check de verify registra la LISTA de shots generados (nombre+sha256,
texto corto) en el informe, sin el binario. **NINGÚN `.gitignore` nuevo**: el
`git add` del corte es selectivo (canon de apply del repo).
**Alternativa**: commitear los PNGs (ya higiénicos — login sin campos, key
redactada) — descartada: el canon C8 es explícito (qa-log RAW commiteado,
shots a disco); un `.gitignore` nuevo también sería fichero en el diff.
**Rationale**: el qa-log RAW es el registro verificable y re-visible; los PNG
son para el OJO humano de esta corrida (A6), no artefacto de repo.

### D-C9-13 · `client_max_body_size 5m` en el nginx del DESTINO (Fase B)
**Decisión**: el valor fijado en el nginx del frontend del **destino** es
**`5m`**, con la pareja de verificación: (1) ingesta VÁLIDA ~0,9 MiB → **202**
(el proxy no interviene); (2) ingesta con schema válido y cuerpo ~1,2 MiB →
**413 JSON `{code:"payload_too_large"}` del BACKEND** (IngestGuardFilter,
barrera 1 — NO el 413 HTML del proxy): la pareja demuestra que el cuerpo
"mayor que el defecto de nginx" ALCANZÓ al backend ⇒ `client_max_body_size`
venció al default 1 MiB; el aserto de R9-B5 (CERO-413 del proxy) se cumple en
ambas. **Fundamento cifrado**: TRD §2.7 fija `INGEST_MAX_REQUEST_BYTES = 1 MiB`
— igual al default de nginx ⇒ un payload en el borde (≤1 MiB válido) podía
caer en el HTML-413 del proxy ANTES de ser evaluado por el backend; `5m` da
≥5 veces el límite de producto. **NO se toca `INGEST_MAX_REQUEST_BYTES`** (es
config de producto — fuera de Fase B, R9-B5 solo exige el CERO-413 del proxy).
TRD §6.7 no fija valor numérico ("el detalle de implementación va con el
despliegue") — este valor queda **ANOTADO en el reporte** (R9-B5).
**Alternativa**: `1m` (default igual al límite de producto) — descartada: no
resuelve la deuda (es exactamente lo que causa el 413 del proxy en el borde);
`20m`/`100m` — descartadas: holgura innecesaria en una pila de 1 MiB por
petición.
**Rationale**: el requerimiento es el CERO-413 operativo; 5 MiB cubre el
cuerpo máximo legal (1 MiB) con margen de cabeceras/form y deja el límite de
producto como única frontera de tamaño.

### D-C9-14 · El cambio de nginx se aplica EN el destino (repo dif-0) — protección del rsync
**Decisión**: R9-B5 fija el nginx del **destino**: la edición de
`client_max_body_size 5m;` (en el bloque `server` del `frontend/nginx.conf`)
se hace en la COPIA de la VM (`/opt/project_log/frontend/nginx.conf`) seguida
de `docker compose up -d --build frontend` allí; el repo NO cambia (Frontera
de Fase A/B: jamás lógica de producto — y el diff del repo es exactamente el
del corte). **Protección**: los rsync POSTERIORES al cambio excluyen
`frontend/nginx.conf` (lista de excludes anotada en el reporte); una
re-deploy-a-limpio del repo requiere re-aplicar la edición — anotado como
paso del runbook de Fase B (R-D-C9-9).
**Alternativa**: bind-mount de un conf extra en `compose.yaml` del destino —
descartada como principal (es una modificación de compose del destino
equivalente y menos legible; se anota como fallback si la re-edición
del conf resultara frágil en apply).
**Rationale**: la frontera del spec es EXPLÍCITA ("en el nginx del frontend
DEL DESTINO"); el repo limpio es además parte del done-when A4 (diff solo de
atréfa SDD).

### D-C9-15 · Playwright 1.64.1 por pip — verificado en arranque, jamás en producto
**Decisión**: la corrida arranca verificando la importación de
`playwright.sync_api` y el binario chromium-headless-shell (`~/.cache/
ms-playwright`) — si no están, **exit 2** con instrucción de instalación por
pip (jamás instala solo: decisión explícita del entorno — la instalación es
del usuario). El guion jamás se lanza desde `frontend/` (frontera Vitest de
C8) y vive en `scripts/` (fuera de cualquier glob de test de producto, QP-8.54
intacto — R9.1.3). El nombre EXACTO de evento WS se comprueba en arranque
contra la versión instalada (D-C9-4).
**Alternativa**: dependencia de producto/npx playwright — PROHIBIDA (R9.1.3,
dif-0 en `frontend/package.json`+lockfile).
**Rationale**: la instalación pip está hecha en esta máquina (repro funcional
de `explore.md`); el arranque lo CERTIFICA para re-corridas futuras sin falso
PASS por entorno incompleto.

## 4. File Changes — tabla COMPLETA y cerrada

**CREATE (Fase A):**

| # | Ruta exacta | Tipo | Nota |
|---|---|---|---|
| A1 | `scripts/qa_vis_c9.py` | NUEVO | Arnés único (§1) — sync API 1.64.1, 26 checks, exit 0/1/2 (R9.1.1) |
| A2 | `scripts/qa_vis_c9.sh` | NUEVO | Wrapper MAY → decido SÍ (D-C9-11): guarda de entorno + RAW + grep S16 |
| A3 | `.atl/sdd/C9/qa-log.txt` | NUEVO (generado) | RAW del stdout — **COMMITEADO** (canon C8, D-C9-12) |
| A4 | `.atl/sdd/C9/evidencia/E9n_*.png` | NUEVO (generados) | ≈25–35 — **EN DISCO, NO commitear** (D-C9-12; ratificación A6) |
| A5 | `.atl/sdd/C9/design.md` (ESTE) | artefacto SDD | commit docs |
| A6 | `.atl/sdd/C9/tasks.md`, `qa-plan.md`, `qa-report.md`, `verify-report.md`… | artefactos SDD | se crean en SUS fases (fuera de apply) |

**CREATE (Fase B — en la VM, NO commiteados jamás):**

| # | Ruta (destino) | Nota |
|---|---|---|
| B1 | `/opt/project_log/**` | copia del repo vía `rsync` con excludes (sin `.env`, sin `.git`, sin `.atl`, sin `node_modules`) |
| B2 | `/opt/project_log/.env` | **generado EN la VM** (S16, chmod 600) — R9-B7/D-C9-13 |
| B3 | `/opt/project_log/frontend/nginx.conf` (copia destino) | `client_max_body_size 5m;` — R9-B5/D-C9-13/D-C9-14 |

**NINGÚN fichero de producto** — verificación en el cierre de Fase A:

- [ ] `git status` post-Fase-A dif-0 en: `frontend/package.json`,
      `frontend/package-lock.json` (**R9.1.3/QP-8.54**), TODO `frontend/**`
      y `backend/**` (la pila NO se recompila — qa_c8_smoke SOLO verifica),
      `compose.yaml`, `.env*`, `docs/**` (incl. maquetas congeladas), smokes
      previos, tests Java y Vitest.
- [ ] El diff del corte = A1+A2 (+A3 al final de qa) + artefactos SDD.

## 5. Interfaces / Contracts del arnés (firmas clave)

```python
# (f) veredicto — estilo canon C8 (qa_c8_smoke.sh usa RESULTADOS+=("E8.n: ..."))
RESULTADOS: list[str]
def marca(id_check: str, veredicto: str, nota: str | None = None) -> None   # "E9.n: PASS[ ...]"
def passO(nota: str | None = None)            # PASS / PASS-CON-NOTA — exige pantalla_vista
def bloqueado(detalle: str)                   # BLOQUEADO honesto (R9.6.4)
def fail(id_check: str, motivo: str)          # FAIL → reportado (loop-back, R9.6.3)

# (d) snapshot semántico
def textVisible(p, literal: str) -> bool                 # inner_text/get_by_text sobre DOM vivo
def claseComputada(p, loc) -> str                        # getComputedStyle → color/clase resuelta
def filasTabla(p) -> list[str]                           # ids de filas → dedup/delta (E9.13/E9.26)

# (b) navegación
def loginPorUI(ctx) -> Page                              # inputs + Entrar + wait_for_url("**/logs")
def contextoSesion(nueva: bool) -> BrowserContext        # principal | limpio | storage-bloqueado

# (c) observación
class Contadores:  # request/response por patrón: logs/me/projects/auth/ingest
def wsContador(ctx) -> {set_filters: int, handshakes: int, cerrados: int}   # solo type/length

# (h) semillas
def ingestar(logs: list[dict], key: bytes) -> dict       # 202 {"accepted":N}
def needle(caso: str) -> str                             # f"qa-c9-{ts}-{caso}"

# (i) destructiva
def ventanaKill(probar: Callable[[], None]) -> None      # stop → probar() → up -d (finally)
                                                          #  + bucleHealth(180) → exit 2 si falla
```

Contrato de literales (CITADOS del spec C9 §1 — jamás redefinidos aquí):
los del bloque destructivo ("Streaming inactivo: los datos en pantalla no se
actualizan en vivo", "● Conectado"/"○ Desconectado", "Reconectando (intento
N)…"), los de estados de `/logs` ("No hay logs que cumplan los filtros",
"Prueba a reiniciar filtros o amplía el rango temporal"), 401 inline ("Usuario
o contraseña incorrectos"), flujo 2 ("Proyecto creado; genera su API key para
que las apps puedan ingestar", "Ya existe un proyecto con ese nombre"),
revelación ("No se volverá a mostrar; cópiala ahora"), 404 ("Este registro no
existe (o fue eliminado)", "Este proyecto no existe (o fue eliminado)") y el
de QP-8.34 ("Exception y stacktrace" / "Metadata (jsonb)").

## 6. Fase B — design de OPS (acotado; R9-B1…B7)

> Requerimientos de OPERACIÓN (resultados verificables), con comandos
> CONCRETOS parametrizados como runbook. `<vm>` = IP LAN de la VM Proxmox,
> `<lan>` = CIDR de la LAN, `<repo>` = raíz del repo. TODA la evidencia se
> captura ya REDACTADA (R9-B7). S16: valores de secretos jamás impresos ni
> copiados al repo.

**FB-0 · Guardia de acceso (R9-B3 — PRIMERO, sin tocar nada):**
```
ssh -o BatchMode=yes -o ConnectTimeout=10 <usuario>@<vm> 'echo ACCESO-OK && hostname'
```
Fallo (credenciales/clave/red) ⇒ **BLOQUEADA-ACCESO**: reporte con el motivo,
CERO cambios de firewall/deploy/reinicio, estado de la VM EXACTAMENTE como se
encontró; re-corridas re-intentan DESDE CERO de Fase B.

**FB-1 · Copia del repo al destino (la MISMA compose/stack):**
```
rsync -a --delete \
  --exclude '.git' --exclude '.atl' --exclude '.env' --exclude 'node_modules' \
  --exclude 'dist' --exclude 'target' \
  <repo>/ <usuario>@<vm>:/opt/project_log/
```
(sin secretos ⇒ nada que filtrar en S16; el `.env` del dev jamás sale del dev).

**FB-2 · `.env` del destino creado EN la VM (S16 — R9-B7):** NUNCA `rsync` del
`.env` local con secretos reales (decisión: los valores se generan en sitio).
```
ssh <usuario>@<vm> 'cd /opt/project_log && test -f .env || \
  (umask 077; {
     echo "POSTGRES_DB=project_log"; echo "POSTGRES_USER=project_log";
     echo "POSTGRES_PASSWORD=$(openssl rand -hex 16)";
     echo "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/project_log";
     echo "SPRING_DATASOURCE_USERNAME=project_log";
     echo "SPRING_DATASOURCE_PASSWORD=$(openssl rand -hex 16)";
     echo "JWT_SECRET=$(openssl rand -hex 32)";
     echo "JWT_TTL_HOURS=8";
     printf "ADMIN_INITIAL_USERNAME=%s\n" "<usuario-admin-de-acuerdo-en-sitio>";
     printf "ADMIN_INITIAL_PASSWORD=%s\n" "<contraseña-de-acuerdo-en-sitio>";
     echo "INGEST_MAX_REQUEST_BYTES=1048576"; echo "INGEST_MAX_BATCH_SIZE=200";
  } > .env && chmod 600 .env)'
```
Si `test -f .env` existe ⇒ se usa el existente (idempotente, jamás re-print).
Nombres de variables citables en el reporte (como en C1+); valores JAMÁS.

**FB-3 · Despliegue (binario del destino ANOTADO — R9-B6):**
```
ssh <usuario>@<vm> 'cd /opt/project_log && \
  { docker compose up -d --build || podman compose up -d --build; } && \
  docker compose ps   # (o podman compose ps) — 3 contenedores healthy, binario anotado'
```
Nota anotada (R9-B6): se usa `docker compose` si el destino lo tiene; `podman
compose` es el fallback con MISMOS resultados — el binario queda por escrito
en el reporte del entorno.

**FB-4 · Firewall GATE #11 (R9-B2 — ORDEN OPERATIVO DURA):**
1. **ANTES de tocar el cierre**: abrir la CONSOLA de la VM en Proxmox (escape
   de GATE #11 — si el firewall cortara la sesión SSH activa, la consola es el
   camino de vuelta).
2. **Regla SSH-LAN PRIMERO** (el orden es parte del requerimiento):
   `firewall-cmd --permanent --add-rich-rule='rule family=ipv4 source address=<lan> port port=22 protocol=tcp accept'` (o `nft` equivalente: `nft add rule inet filter input ip saddr <lan> tcp dport 22 accept`); `--reload` y **verificar `ssh` vivo** en una CONEXIÓN NUEVA desde la LAN.
3. **Reglas de 8080 DESPUÉS**: `firewall-cmd --permanent --add-port=8080/tcp`
   (o `nft`); `--reload`.
4. **Cierre final** según GATE #11 (solo SSH-LAN + 8080) — jamás un flush que
   deje fuera la regla SSH del paso 2 (la regla ya está PERMANENTE antes de
   cerrar).
5. **Verificación DESDE la LAN en vivo antes del cierre** (R9-B2): `ssh` OK +
   `curl -fsS http://<vm>:8080/ | grep -c '<html'` + login por UI posible.
   El mecanismo concreto (firewalld/nft/ufw) se pina en apply según el SO de
   la VM; lo contractual es el ORDEN (SSH-LAN antes del cierre) y la
   verificación en vivo.

**FB-5 · Smoke canónico `docker compose` (R9-B6):**
```
docker compose ps   # 3 healthy
curl -fsS http://127.0.0.1:8080/ | head -1        # HTML del panel
curl -s -X POST http://127.0.0.1:8080/api/v1/panel/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"<u>","password":"<p>"}' | head -c 40   # 200 con token → NO IMPRIMIDO (cortar a los primeros bytes no-token)
curl -fsS http://127.0.0.1:8080/api/v1/panel/me -H "Authorization: Bearer $TOKEN"   # 200 humo
```
(El `$TOKEN` vive en la shell efímera de la VM; la salida del comando se
recorta a lo no-secreto — higiene R9-B7. Con la pila recién levantada puede
exigirse UNA espera del healthcheck del compose — bucle de health como R9.1.5.)

**FB-6 · `client_max_body_size 5m` (R9-B5/D-C9-13/D-C9-14):**
```
ssh <usuario>@<vm> 'cd /opt/project_log && \
  grep -q client_max_body_size frontend/nginx.conf || \
  sed -i "/server_name _;/a\\    client_max_body_size 5m;" frontend/nginx.conf && \
  docker compose up -d --build frontend'
# pareja de verificación (desde la LAN):
#   (1) POST /api/v1/ingest/batch con cuerpo VÁLIDO ~0,9 MiB  → 202 {"accepted":N}
#   (2) POST /api/v1/ingest/batch con schema válido ~1,2 MiB  → 413 JSON del BACKEND
#       {"code:" payload_too_large"} (IngestGuardFilter barrera 1 — NO es el 413
#       HTML del proxy) ⇒ el cuerpo superó el defecto de nginx y llegó al backend.
```
El valor `5m` queda anotado en el reporte (R9-B5); `INGEST_MAX_REQUEST_BYTES`
sin tocar (D-C9-13).

**FB-7 · Frío y reinicio (R9-B4):**
```
ssh <usuario>@<vm> 'sudo poweroff'   # o desde Proxmox
# encender → esperar →
ssh <usuario>@<vm> 'cd /opt/project_log && docker compose ps'   # 3 healthy SOLAS
curl -fsS http://<vm>:8080/ >/dev/null && echo FRIO-OK
# reinicio: ssh 'sudo reboot' → idem → REINICIO-OK   (evidencia mínima redactada)
```
La verificación es de ARRIBA (healthy + 8080), no de contenido (R9-B4); el
volumen pgdata sobrevive (compose igual). **Nota**: el primer arranque en frío
DESPUÉS del deploy reutiliza el pgdata creado en FB-3 — los needles del
usuario/semillas siguen consultables si el volumen persiste (no se fuerza).

**FB-8 · Cierre Fase B:** reporte con: binario anotado (FB-3), mecanismo de
firewall y reglas (FB-4, redactado), valor `client_max_body_size: 5m` (FB-6),
salidas de FB-6/FB-7 (redactadas), grep S16 (0 valores de `.env` en repo/
evidencias — R9-B7). Si SSH falla en CUALQUIER paso PREVIO a haber tocado
nada ⇒ BLOQUEADA-ACCESO; los pasos que ya tocaron la VM solo ocurren CON SSH
vivo.

## 7. Verificación / cifras (cruzadas con spec §4)

| Canal | Cifra del design | Coincide con spec §4 |
|---|---|---|
| Checks del arnés | **26** = 14 (E9.1–E9.14) + 9 extras (E9.15–E9.23) + 3 (E9.24–E9.26) | ✓ (límite EXACTO G2; QP-8.37 FUERA) |
| Screenshots | ≈25–35 a `evidencia/`, naming E9n (D-C9-7) | ✓ |
| Vitest | **60 tests / 6 ficheros** — SIN CAMBIO (Playwright fuera de la frontera) | ✓ |
| mvn | **177/177** — SIN CAMBIO (Fase A no toca backend) | ✓ |
| Smoke C8 | re-corrida al cierre: **32 veredictos [S] PASS + exit 0** (R9.5.6) | ✓ |
| qa-log RAW | 1 fichero commiteado; grep post-corrida 0 hits `plk_`/`eyJ` | ✓ |
| Exit del arnés | 0 = 26 PASS (con notas ratificadas G1/E9.7-A); 1 = algún FAIL; 2 = higiene/PILOTO CAÍDO/pila no disponible | ✓ (R9.6.2) |
| Fase B | 6 dominios, 7 requerimientos R9-B1…B7 + 6 pasos FB-0…FB-8 | ✓ |

## 8. Riesgos del diseño R-D-C9-*

| ID | Riesgo | Mitigación / plan |
|---|---|---|
| R-D-C9-1 | Fragilidad de selectores (cambia un literal/estructura ⇒ FAIL de arnés sin bug de producto) | Snapshot semántico por roles/textos del contrato; literales no descritos por maqueta se cosechan EN VIVO y se pinan, no se inventan (R9.2.4); FAIL → loop-back a apply/design (R9.6.3, jamás auto-arreglo) |
| R-D-C9-2 | Evento WS `framesent` no observable en la 1.64.1 instalada | PLAN B (D-C9-4): conteo de handshakes/reconexiones + sub-aserto degradado BLOQUEADO-anotado (jamás falso PASS — R9.6.4) |
| R-D-C9-3 | Emulación de storage bloqueado (E9.7-B) frágil | BLOQUEADO honesto de ESA sub-prueba (§5 del spec) — el resto de E9.7 (variante A) no depende |
| R-D-C9-4 | La restauración del backend falla (health 180 s) | `try/finally` + `up -d backend` + bucle health; falla ⇒ **exit 2 "PILOTO CAÍDO"** (R9.1.5); jamás `down -v`/postgres |
| R-D-C9-5 | Timings (600 ms delay, intento N de backoff 1 s×2^(N-1), esqueleto) frágiles en máquina lenta | Retardo suficientemente holgado (600 ms > doble-click humano; polling del texto con timeout generoso dentro de la ventana de 180 s); N creciente se pina por polling, no por timing exacto |
| R-D-C9-6 | Contaminación de datos entre re-corridas (proyectos acumulan, E9.19 ve múltiples qa-c9-*) | Needles sufijados por ts (idempotentes — R9.5.4); E9.19/E9.20 asertan PRESENCIA del needle, no exclusividad; residuo NOTIFICADO (R9.5.5) y barido real = los `down -v` de los smokes futuros / C10 |
| R-D-C9-7 | Fuga de secretos (JWT/key) por stdout, PNG o evaluate | D-C9-6 completa (evaluate booleano, overlay redactado, login shots vacíos, frames por type/length, grep post-corrida 0 hits + exit 2) |
| R-D-C9-8 | LOCKOUT por firewall en Fase B | Orden dura SSH-LAN PRIMERO + consola Proxmox abierta ANTES del cierre (FB-4); verificación en vivo desde la LAN antes del cierre; si SSH muere a mitad, consola restaura la regla |

## 9. Riesgos del diseño R-D-C9-* (Fase B, continuidad)

| ID | Riesgo | Mitigación / plan |
|---|---|---|
| R-D-C9-9 | Un rsync posterior a FB-6 sobreescribe el `frontend/nginx.conf` editado en destino | Excludes del rsync incluyen `frontend/nginx.conf` después del 1.er cambio (anotado en el runbook); re-deploy-a-limpio exige re-aplicar FB-6 (D-C9-14) |
| R-D-C9-10 | SSH se cae EN MEDIO del cambio de firewall | Consola de escape (FB-4.1) ya está abierta; la regla SSH es PERMANENTE antes de las reglas de cierre — el riesgo remanente es operativo, no de diseño |
| R-D-C9-11 | El binario del destino no es `docker compose` (podman) | FB-3/FB-5 con fallback y NOTA del binario en el reporte (R9-B6); los resultados exigidos son idénticos |
| R-D-C9-12 | `.env` del destino ya existente con secretos antiguos | FB-2 idempotente (`test -f .env`): se usa el existente, jamás re-impreso ni sobreescrito; si el usuario quiere rotación, se re-crea manualmente en la VM (fuera del arnés) |
| R-D-C9-13 | Volumen `pgdata` del destino vacío en un arranque frío ANTES del 1.er deploy | FB-7 corre SIEMPRE tras FB-3 (la pila ya se desplegó y su pgdata existe) — el frío/reinicio JAMÁS es el primer paso de Fase B |

## 10. Open questions (NO bloqueantes — default para tasks)

1. **`client_max_body_size 5m`** (D-C9-13): valor propuesto con fundamento
   (TRD §2.7: 1 MiB + margen ≥5×). TRD §6.7 no fija cifra; si el orquestador
   prefiriera otro valor (p. ej. `2m`), el cambio es UN literal de FB-6 — el
   aserto contractual (CERO-413 del proxy) es independiente del valor elegido.
2. **Commits de PNGs** (D-C9-12): default = NO commitear (en disco, sha256
   listado en el informe); si el usuario quisera la evidencia binaria en el
   repo (shots ya higiénicos), es un `git add` selectivo de la carpeta
   `evidencia/` — sin código.

## Done-when (del design, espejo de A1–A6 + B del spec)

- **A1 [L]** — 26 checks con veredicto individual y regla dura estructural
  (D-C9-1); E9.14 PASS-CON-NOTA con su nota G1; E9.7-A con su matiz proxy.
- **A2 [L]** — E9.25 con cronómetro `< 120 s` (`time.monotonic()`); E9.26 con
  kill stop/`up -d` + health 180 s + ids únicos + delta 0.
- **A3 [L]** — 9 extras observados; 8.11/8.31/8.38/8.37 NO existen como checks.
- **A4 [S]** — qa-log RAW commiteado + PNGs en `evidencia/` (D-C9-12),
  higiene S16 verificada (grep 0 hits, login limpio, key redactada), exit
  0/1/2 canon.
- **A5 [R]** — Manuales/PASS-CON-NOTA §5 del spec tal cual en el qa-report;
  `qa_c8_smoke.sh` verde post-Fase A (R9.5.6).
- **A6 [MANUAL]** — Ratificación visual humana de los PNG (contraste/estética,
  FATAL invertido).
- **B** — FB-0…FB-8 ejecutados con sus verificaciones (o **BLOQUEADA-ACCESO**
  reportado sin tocar nada — R9-B3); binario anotado, `5m` anotado, `.env`
  jamás al repo/impreso; ficha C9 del 07 PARTIDA si B cerrara bloqueada.

## 11. Checklist de frontera final (dif-0 fuera de tabla)

- [ ] `git status` post-Fase-A NO muestra cambios en `frontend/**`, `backend/**`,
      `compose.yaml`, `.env*`, `docs/**`, smokes previos, tests (R9.1.3,
      QP-8.54 — cifras §7 SIN CAMBIO).
- [ ] NINGÚN `plk_`/`eyJ` imprimido en qa-log (grep == 0 — S16 duro).
- [ ] NINGÚN check fuera del límite EXACTO de 26 (G2) — verificado contra la
      tabla de trazabilidad del spec §3.
- [ ] El arnés jamás ejecuta `down -v`, SQL directo, ni toca postgres/volúmenes.