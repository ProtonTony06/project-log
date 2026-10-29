---
project: Project_Log
change: C9
artifact: apply-progress
last_updated: 2026-10-29
status: in-progress
---

# Apply progress — C9 (log único e idempotente, se reescribe por lote)

- Entradas leídas antes de escribir: `proposal.md` (approved G1–G4),
  `spec.md` (E9.1–E9.26, set EXACTO del gate), `design.md` (9 secciones §1,
  B0→B8 §2, D-C9-1..15), `tasks.md` (T9.1–T9.37, 5 bloques).
- HEAD de partida: `c144f3d` (C8, main). Este lote (Bloque 1) produce
  UN commit: py + sh (sin push; tasks.md se edita en disco pero NO se
  commitea — pertenece al cierre del corte, Bloque 5).

## Bloque 1 — Fase A: arnés + wrapper + commit

- [x] T9.1..T9.12 — COMPLETADOS con evidencia en línea en `tasks.md`.
- `scripts/qa_vis_c9.py` — 2434 líneas; `python3 -m py_compile` verde;
  `python3 scripts/qa_vis_c9.py --check` ⇒ "arnés cargado — no corrida".
- `scripts/qa_vis_c9.sh` — 60 líneas; `bash -n` verde.
- `.atl/sdd/C9/evidencia/` — creado VACÍO (los PNG van a disco, D-C9-12).
- B0 verificado EN VIVO (única ejecución permitida antes de la corrida de
  Bloque 2): `verificarPila()` — GET / 200; GET /actuator/health 200
  (trivial; H-C9-SALUD); GET /api/v1/panel/me == 401 (backend vivo);
  `verificarEntorno()` — playwright 1.64.1 importado sin pip; binarios
  chromium-1248 y chromium_headless_shell-1248 en ms-playwright; evento WS
  "framesent" confirmado contra la versión instalada (D-C9-4).
- Frontera (T9.12): sin playwright en `frontend/package.json` (0 hits) —
  los 2 hits de `frontend/package-lock.json` son `@vitest/browser-playwright`
  de C8 (pre-existente, sin dif); `git status` = solo los 2 scripts. Sin
  `down -v`, sin SQL, sin postgres en el arnés (grep 0/3 hits en código).

## Decisiones y H-C9-matizaciones de sdd-apply

- **H-C9-SALUD**: `/actuator/health` es SPA-fallback 200 trivial (nginx
  no proxyea actuator) — la señal REAL de backend vivo en `bucleHealth`
  y en `verificarPila` es `GET /api/v1/panel/me == 401` (SALUD_BACKEND).
- **H-C9-COMPOSE**: el host es podman 5.8.9 + podman-compose 1.6.0 —
  `detectarComposeBin()` prueba `docker compose` | `podman compose` |
  `podman-compose` (resuelve `podman compose` en vivo en B0).
- **H-C9-E9.11**: E9.11 es de 3 partes (a: Conectado en B6; b: banner
  "Streaming inactivo…" + Reconectando (intento N) durante la caída de B7;
  c: /proyectos sin indicador ni banner) con acuñación única diferida en
  `E911_STATE` y sellado por `sellarE911()` al cierre de B7 (FAIL honesto si
  la parte (b) nunca se observó; guard anti-doble-sello).
- **H-C9-E9.18**: el 404 del detalle (`/logs/99999999`) no estaba en la
  tabla de §2 por omisión del design; slotted al final de B6.
- **H-C9-S16-literales**: los literales `plk_` y `eyJ` aparecen SOLO en el
  docstring/comentarios con `plk_` y `eyJ` SIN ninguna continuación de
  caracteres de credencial — el grep del wrapper
  (`plk_[A-Za-z0-9_-]+|eyJ[A-Za-z0-9_-]{10,}`) da 0 hits. Los evaluates del
  navegador devuelven SOLO booleanos/números; en ninguna línea de log
  impresa aparece ninguno de los dos prefijos.
- **H-C9-E9.21-400**: la variante 400 por campo usa `maxLength=200` del
  input + submit disabled con nombre vacío (gate cliente, no hallazgo de
  producto; matiz de E8.17 citado en la nota del check).
- **H-C9-E9.16/E9.24-orden**: el orden del lote de 3 es por keyset
  (received_at DESC, id DESC) y puede llegar invertido — los asertos son
  set-equality de los 3 mensajes + orden observado impreso en la nota.
- **H-C9-idempotente**: `passO`/`fail` por pareja con flag `pantalla_vista`
  y `p.marcada` — una sola línea `E9.N: …` por ID; FAIL gana (conservador);
  regla dura D-C9-1/R9.1.4 ESTRUCTURAL (PASS sin pantalla observada ⇒
  auto-FAIL "falso PASS bloqueado por el arnés").
- **H-C9-B5-condicional**: B5 (semillas) se salta con print `[guard]` si la
  key no está en memoria (no contamina RESULTADOS — el guard de 26 es
  EXACTO).
- **H-C9-residuo**: R9.5.5 — los needles sembrados quedan en el piloto,
  NOTIFICADOS al final (b8), sin limpieza destructiva del arnés.

## Bloques 2-3 — Corrida real + bucle de hallazgos + cierre Fase A (EJECUTADO)

**Corridas: 7 totales** — C1 (abortada CORS 403 con credenciales del .env,
evidencia `.atl/sdd/C9/qa-log-corrida1-cors403.txt.bak`), C2 (10 FAIL — 6
causas-raíz del ARNÉS), C3 (8 FAIL + PILOTO CAÍDO — restaurado con
`up -d backend` + `--force-recreate frontend`), C4 (5 FAIL), C5 (6 FAIL —
2 flakes nuevos), C6 **FINAL estable** (exit 1: 4 FAIL producto/deploy), y
hallazgo H-C9-E9.2 confirmado POST-corrida (repro×3 + trazas de history) —
corrida 6 lo dejó PASS-CON-NOTA por un transitorio; veredicto honesto FAIL.

Causas del arnés parcheadas en el bucle (commits `975637f`, `b50ab4b`,
`6367443`): CORS/proxy del arranque, ventana de requests vs responses
(`marcaIndiceRequests`), colores OKLCH de Tailwind v4 (modales 8a/8c),
regex de la key (plk_=10+43), shape desnudo de `/panel/projects`, reinicio
F5 para no contaminar checks, case-insensitive «● EN VIVO» (CSS uppercase),
recreación del proxy frontend en `bucleHealth` (upstream nginx cacheado),
`esperarQue` exception-safe + timeouts cortos en E9.6 (botón desmontado
post-POST), polling de lecturas en E9.14 (chunk lazy). Pendiente acotado
(documentado en la nota H-C9-E9.2): observación de ARRIBO en E9.2 (poll de
la URL perdurando ~700 ms tras el transitorio) + mover el shot de E9.1 al
paso real de /logs + retardo 2.5 s del esqueleto de E9.12 (los shots de
E9.1/E9.12 de la corrida 6 fueron infieles a su etiqueta — veredictos
incambiables, la pantalla SÍ fue observada).

**Veredictos FINALES de la corrida 6** (26 marcas · 5 FAIL honestos
PRODUCTO/DEPLOY · 1 BLOQUEADO honesto · exit 1):

| ID | Veredicto corrida 6 | 1 línea |
|----|--------------------|---------|
| E9.1 | PASS-CON-NOTA | 9 rutas 1:1 sin 404 de servidor; / y /login con sesión a /logs (shot mal etiquetado — arnés, pantalla SÍ observada) |
| E9.2 | PASS-CON-NOTA → **FAIL PRODUCTO** (H-C9-E9.2) | guard + destino pinedo OK, PERO el arribo real tras login es /logs SIEMPRE: `<Navigate to="/"/>` de Login pisa `navegar(destino)` a los 1 ms (instrumentado) |
| E9.3 | PASS-CON-NOTA | /login con sesión redirige + token basura descartado sin crash |
| E9.4 | PASS-CON-NOTA | 404 panel cosechado en vivo, URL estable |
| E9.5 | PASS-CON-NOTA | 401 inline, sin revelar campo, URL estable |
| E9.6 | PASS-CON-NOTA | POST retrasado 600 ms real; «Enviando…» deshabilitado; n_posts=1 |
| E9.7 | PASS-CON-NOTA (**+ BLOQUEADO-B honesto**) | A recarga: t(/me)<=t(render protegido); B storage bloqueado no emulable — jamá fingido |
| E9.8 | PASS-CON-NOTA | logout local 0 requests/0 handshakes; 2.º login por UI |
| E9.9 | PASS-CON-NOTA | JWT solo en sessionStorage (booleans internos) |
| E9.10 | **FAIL PRODUCTO** (H-C9-E9.10) | delta=3 frames set_filters POR EVENTO — useLogsStream dispara por identidad del objeto (toSetFilters inline) — contradice R9.2.10 «UN set_filters» |
| E9.11 | **FAIL PRODUCTO** (H-C9-WS-COLGADO) | intentos=[1]: backoff close-driven sin timeout de CONNECTING — cadena muerta con upstream envejecido |
| E9.12 | PASS-CON-NOTA | esqueleto/vacío/400-«lista conservada» diferenciados (shot mal etiquetado — arnés, aserto SÍ observado) |
| E9.13 | **FAIL PRODUCTO** (H-C9-E9.13) | `/logs/388??text=…` doble «?» (alVerDetalle+Volver de LogDetalle) — rompe el round-trip keyset E8.35 |
| E9.14 | PASS-CON-NOTA | vacío legítimo con datos simulados [] + CTA real (nota G1 obligatoria) |
| E9.15 | PASS-CON-NOTA | superficie DUAL: 400 field=level por API; en pantalla normalizado por diseño (D-C8-6) — sub-aserto BLOQUEADO-anotado |
| E9.16 | PASS-CON-NOTA | banda «● EN VIVO» batch_id + 3 logs (orden n03/n02/n01) sin refetch |
| E9.17 | PASS-CON-NOTA | bloques exception/metadata/correlation CON/SIN cosechados en vivo |
| E9.18 | PASS | 404 del detalle: main nunca vacío + Volver |
| E9.19 | PASS-CON-NOTA | tarjeta maqueta 04: STATUS Activo + api_y_prefijo visible + has_key |
| E9.20 | PASS-CON-NOTA | ficha maqueta 05 + bloque API key + variante 404 |
| E9.21 | PASS-CON-NOTA | 201 → ficha (id 13) + aviso literal; proyecto C9 por UI |
| E9.22 | PASS-CON-NOTA | modales 8a/8c color dual OKLCH/rgb; CERO DELETE |
| E9.23 | PASS-CON-NOTA | regenerate revelación única + shot REDACTADO; key solo memoria |
| E9.24 | PASS-CON-NOTA | CRITERIO 1: 2.ª ventana ve el lote sin refetch (before/after) |
| E9.25 | PASS-CON-NOTA | CRITERIO 2: stack del needle visible en 0.6 s (<120 s) |
| E9.26 | **FAIL PRODUCTO/DEPLOY** (H-C9-WS-COLGADO + NGINX) | post-restauración delta 0 — sin recuperación del WS (cuerdas: E9.11) |

**Residuo de siembra (R9.5.5 — NOTIFICADO, NO limpiado; la limpieza son los
DELETE de C10, jamá SQL)**: proyecto `qa-c9-20261029125116-e45` (id 13, por
UI) + residuos de corridas previas (`qa-c9-20261029124016-e45` id 12/11 +
needles e35×60, e25, e16×3, e24×3, e17b por corrida) + keys de ingesta
ACTIVAS en BD (solo prefijo en memoria del proceso — jamá impresa).

**Cierre Fase A (T9.16–T9.21)**: qa-log.txt = RAW verbatim de la corrida 6
(23.745 B; residuos de corridas 3/4/5 con nombres .bak/.txt aparte); S16
grep `plk_|eyJ` = 0 hits (canon §6 C8); inventario PNG 30 ficheros NO
commiteados (D-C9-12) con nombre+sha256 en el reporte de apply (lista
embebida en el handback); T9.19 dif `4e2b559..HEAD` = SOLO
`scripts/qa_vis_c9.py` + `.atl/notes/*` (466+/-80) — **0 diff en
frontend/**, backend/**, compose.yaml, .env*, docs/****: 60 Vitest /
177 Java SIN CAMBIO; smoke C8 NO re-corrido (su `down -v` barrería la
siembra — re-corrida en Bloque 5, T9.20/T9.31/T9.32).

## Set de los 26 checks implementados (una línea por ID)

E9.1 routing 1:1 de las 7 rutas protegidas + 404 panel + raíz.
E9.2 guard /panel/me 401 + destino /proyectos/99999999 + retorno al login.
E9.3 sesión viva + token basura descartado (redirect a /login + token null).
E9.4 404 panel distinta de pantallas válidas, con y sin sesión.
E9.5 401 inline /login con campos vacíos y URL estable.
E9.6 doble submit bloqueado (delay + n_posts==1).
E9.7 restauración de sesión: A proxy temporal / B storage bloqueado.
E9.9 JWT SOLO en sessionStorage (evaluate booleanos).
E9.8 logout local, 0 requests y 0 handshakes, re-login por UI (D-C9-10).
E9.10 filtro por texto reinicia keyset + set_filters 6 claves sin cursor.
E9.12 cargando (esqueleto, delay 1.4 s) / vacío 2 literales / 400 lista conservada.
E9.15 banner 400 con code + "Campo culpable" + "level".
E9.13 acumulado keyset: query intacta al volver + dedup + 0 refetch + F5.
E9.16 banda "● En vivo" lote 3 + batch_id + sin refetch.
E9.24 CRITERIO 1: 2.ª ventana ve el lote de 3 sin refetch.
E9.11 indicador (3 partes: a / b-caída / c) sellado al final de B7.
E9.17 bloques exception+metadata+correlation CON/SIN.
E9.18 404 del detalle: main nunca vacío + Volver.
E9.25 CRITERIO 2: detección de stack < 120 s (cronómetro).
E9.21 flujo 2 (proyecto C9 creado por el arnés por UI; 201/409).
E9.19 ficha tarjeta (Activo / has_key / sin resumen) — maqueta 04.
E9.20 ficha proyecto + variante 404 — maqueta 05.
E9.22 modales 8a/8c construidos SIN DELETE (color botón + gates).
E9.23 regenerate: revelación única + shot REDACTADO + key SOLO en memoria.
E9.14 vacío legítimo con datos simulados + CTA (nota G1 OBLIGATORIA).
E9.26 CRITERIO 3: post-restauración con delta 0 + exactamente 1 refetch.

## Próximos pasos (Bloque 2 — NO en este lote)

CERRADO (los bloques 2-3 se ejecutaron — ver la sección anterior). Restan
los bloques 4 (Fase B deploy VM) y 5 (smoke final) del orquestador; además
pendiente del orquestador/C10: la corrección del defecto H-C9-E9.2 en
producto (Login.tsx), el acotado arnés de la observación de arribo de E9.2
y el commit de la nota (hallazgos de producto NUNCA tocan código).
## Bloque 4 — Fase B (2026-10-29) — COMPLETO

FB-0..FB-7 todas hechas y verificadas en la VM 108 «projectlog»
(192.168.1.58, IP estática fuera del pool DHCP), con truth completa en
state.md §fase-b-log (incluye el catch de nftables.service FAILED al boot
frío — `flush table` sobre tablas vacías —, su fix boot-safe y las verifi-
caciones FRÍO-OK + REINICIO-OK con 3/3 healthy autónomos y firewall
persistente). Producto dif-0 en Fase B. Binario anotado: docker compose
v5.6.1. Firewall GATE #11: nftables table inet pl_vm — SOLO SSH-LAN (22) +
8080, orden SSH-primero respetada, consola de escape validada ANTES.
`client_max_body_size 5m` en la copia nginx del DESTINO (repo dif-0).
Corrida pendiente en closure: demo honesta de los 3 criterios del 06 sobre
la VM (nivel REST/deploy + hallazgo H-C9-WS-COLGADO anotado, no simulado).

## Próximos pasos (Bloque 5 — cierre del corte)

- [ ] T9.31 smoke qa_c8_smoke.sh (corre al cierre — su down -v barre la siembra)
- [ ] T9.32 re-siembra condicional / anotación del estado limpio
- [ ] T9.33 qa-report.md · T9.34 verify-report.md · T9.35 state final
- [ ] T9.36 ficha C9 en docs/07 · T9.37 commit docs(SDD) (sin PNGs ni __pycache__, sin push)
