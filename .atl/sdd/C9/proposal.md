---
project: Project_Log
change: C9
artifact: proposal
last_updated: 2026-10-29
status: pending-gate
---

# Proposal — C9: QA visual automático (ARNÉS Playwright) + deploy y firewall (PENDIENTE-DE-APROBACIÓN)

> Base: `.atl/sdd/C9/explore.md` (completa, HEAD `c144f3d` — parche post-Q23 del
> detalle RUTA HERMANA aplicado). Afirmaciones críticas re-verificadas de cara a
> este proposal: ficha C9 de `docs/07_SLICING_PLAN.md` (líneas 162–167), los 3
> criterios de la demo de aceptación Fase 1 de `docs/06_IMPLEMENTATION_PLAN.md`
> (líneas 90–98), TRD §6.7 (firewall de la VM Proxmox, GATE 2026-10-19: reglas
> SSH desde LAN + 8080, "el detalle de implementación va con el despliegue"),
> `.atl/sdd/C8/qa-plan.md` §3/§6 y `.atl/sdd/C8/qa-report.md` (los 14
> MANUAL-PENDIENTE: QP-8.1/2/4/5/8/9/10/12/13/22/29/32/35/40), y las deudas
> heredadas declaradas en el estado `hecho` de C1/C4/C5/C6/C7/C8 del slicing
> plan (nginx `client_max_body_size`, smoke canónico `docker compose`, demo
> visual Q23 ×14; "401 ISO-8859-1 → C8/C9" CERRADA en C8 vía D1).

## 1. Motivo y encaje en el plan — Y REORDENACIÓN RATIFICADA DEL USUARIO

- **Por qué ahora:** C1–C8 cerrados. El panel real existe contra la API real
  (smoke C8 23/23 + Vitest 55/55), pero **catorce parejas quedaron
  MANUAL-PENDIENTE** (Q23: "sin E2E de navegador" era la regla interna de C8)
  y ni un solo ojo humano ha ratificado aún las pantallas en vivo. Además la
  pila solo ha vivido en localhost: la ficha C9 (subida a VM, firewall, frío/
  reinicio, demo con app real) pide deploy.
- **REORDENACIÓN del usuario (ratificada 2026-10-29, prioridad explícita):**
  lo PRIMERO de C9 es **qa automatizado del visual** ("los ojos"); el
  deploy/firewall viene DESPUÉS. Q23 se resuelve con Playwright headless
  (ratificado: vive por `pip` fuera de `frontend/package.json`/lockfile —
  QP-8.54 intacto; scripts en `scripts/`, fuera de la frontera Vitest de C8).
- **Encaje:** C9 cierra Q23 ×14 y, si se ratifica Fase B, las deudas C1/C4–C8
  de deploy. La demo "con app real del homelab" se demuestra con ingesta REST
  programática desde el propio arnés — misma superficie `POST /ingest/batch`
  (`X-API-Key`), literal canónico del criterio 1.

## 2. Alcance del corte

### Fase A — QA visual automático, "ojos" (lo primero y obligatorio)

1. **ARNÉS único `scripts/qa_vis_c9.py`** (+ wrapper `.sh` si hace falta para
   env/jobs), estilo "patrón del vídeo": un guion que ejecuta las 14 parejas
   en orden con **un solo login de sesión** y orden sin contaminación
   (explore §C: 1. routing/guard/404 → 2. storage/hibernación → 3. `/logs`
   estados + regalos → 4. proyectos (crea proyecto C9 + key) → 5. detalle →
   AL FINAL el caso destructivo-de-sesión; kill del backend en fase aparte).
2. **REGLA DURA (decisión de diseño a anclar en spec/design): NINGÚN PASS
   declarado sin haber abierto y observado la pantalla real** — navegación
   chromium headless + login por UI (nunca token seteado a mano salvo en las
   variantes que lo exigen) + asertos de snapshot SEMÁNTICO sobre el DOM vivo
   (textos LITERALES del contrato + roles + clases computadas) + **screenshot
   de evidencia** por pareja a `.atl/sdd/C9/`.
3. **Las 14 parejas MANUAL-Q23** con condición de aceptación LITERAL del
   qa-plan §3 y técnica concreta por pareja del explore §B/§C (cada una en la
   tabla): QP-8.1 (9 rutas 1:1), 8.2 (guard con destino), 8.4 (login→redirect
   con token / 401 descarta), 8.5 (404 del panel), 8.8 (401 inline
   "Usuario o contraseña incorrectos"), 8.9 (doble submit "Enviando…" — 1 solo
   POST, `route.continue_()` retrasado), 8.10 (restauro pre-render; variante B
   storage bloqueado), 8.12 (logout sin red), 8.13 (JWT solo sessionStorage,
   asertos BOOLEANOS), 8.22 (set_filters 6 claves + cursor reiniciado),
   8.29 (indicador/banner/reconexión + indicador ausente fuera de `/logs`),
   8.32 (esqueleto/vacío/error 400 con lista conservada), 8.35 (Volver
   conserva acumulado y query — post-parche D-C8-8), 8.40 (vacío legítimo +
   CTA — ver GATE G1).
4. **Los 3 criterios demo del 06, AUTOMATIZADOS:**
   - C1: batch de 3 → grupo en banda "● En vivo" sin refresco (0 refetch de
     `GET /panel/logs` en la ventana).
   - C2: de filtros ERROR+production a ver el stacktrace del detalle,
     **cronometrado < 120 s** (`time.monotonic()`).
   - C3: **kill del backend destructivo LOCAL** — `stop` del servicio
     `backend` y `up -d backend` con `try/finally` + bucle de health
     (timeout 180 s; si la restauración falla, exit 2 "PILOTO CAÍDO");
     **JAMÁS `down -v`** ni tocar postgres/volúmenes. Reconexión con N
     creciente, banner de streaming inactivo, sin duplicados (set de ids
     único) ni replay (delta 0 contra el id-set pre-caída).
5. **EXTRAS RATIFICABLES (GATE G2) — mitades VISUALES de duales** (explore
   §0-bis; el arnés las observa de regalo, sin infra nueva). **Límite EXACTO:
   lo ratificado — sin inventar checks**. Propuesta de reparto:
   - **ENTRAN en la v1 (recomendados, sin efecto destructivo):** QP-8.26
     (banner 400 `code`/`field`), QP-8.27 (banda "● En vivo" en orden del
     lote), QP-8.34 (bloques "Exception y stacktrace"/"Metadata (jsonb)"
     condicionales, cabecera 03), QP-8.36 (404 detalle "Este registro no
     existe (o fue eliminado)"), QP-8.39/8.42 (maquetas 04/05), QP-8.45
     (flujo 2 vía UI — DOBLE como semilla del proyecto C9), QP-8.47 (modales
     8a/8c, aserto de disabled/enabled — SIN petición), QP-8.43 (revelación
     única — SOLO del proyecto qa-c9 creado por el arnés; shot con la key
     REDACTADA, valor jamás impreso).
   - **OPT-IN aparte (el usuario decide):** QP-8.11 (banner expiración 8 h
     con token tamperado en proceso), QP-8.31 (variante 401 pre-upgrade WS —
     ya cubierta REST/WS por el smoke C7), QP-8.38/DEV-C8-4 (confirm 8b →
     **401 real** → logout; comportamiento REAL documentado; ordenado AL
     FINAL por destruir la sesión).
6. **Evidencia e higiene (S16 aplicado a lo visual):** `qa-log.txt` RAW a
   `.atl/sdd/C9/`, veredicto por ID `E8.n: PASS | PASS-CON-NOTA (motivo) |
   FAIL | BLOQUEADO (detalle) | Manual`, **exit 0/1/2** como canon C8; grep
   post-ejecución de 0 hits `plk_`/`eyJ`; JAMÁS capturar el login con campos
   rellenados; jamás screenshot/echo del JWT ni de la key sin redactar.
   Snapshot semántico + screenshots — **NO diff de píxeles contra las
   maquetas** (el contrato es literales/estructura; la ratificación final de
   cada shot es del usuario al revisar el qa-artefacto).

**Entregables Fase A:** `scripts/qa_vis_c9.py` ejecutable (log a stdout),
evidencia por pareja + informe resumido (tabla de veredictos al final del
qa-report de C9), y el artefacto de QA versionado.

### Lo que QUEDA manual / PASS-CON-NOTA — declaración explícita (RATIFICAR en el GATE)

- **E8.40 / QP-8.40 (G1):** imposible con datos vivos (los DELETE de C10 no
  existen; el estado "sin proyectos" es inalcanzable). Opciones: (i) **route-
  interception** `**/panel/projects` → `{items:[],next_cursor:null}` y se
  observa la PANTALLA REAL con payload simulado ⇒ **PASS-CON-NOTA** (la CTA
  "Nuevo proyecto" SÍ se navega 100% real) — recomendado; (ii) queda MANUAL.
- **Percepción humana de contraste/estética (manual SIEMPRE):** mitigado con
  `getComputedStyle` de badges/indicadores/botón rojo + screenshot; la lectura
  subjetiva la ratifica el usuario sobre los shots.
- **Flash de login E8.10 (PASS-CON-NOTA):** aserto por proxy temporal
  (`/me` antes del primer render protegido + polls sin formulario) — no hay
  captura por-frame de un flash real.
- **Storage bloqueado E8.10-B:** emulación por `add_init_script` frágil ⇒
  **BLOQUEADO honesto** (Q23) si la emulación no resulta; jamás falso PASS.
- **Clipboard/a11y (G3, opcional):** los botones Copiar se asertan por
  presencia/habilitación; verificar el portapapeles real requiere
  `grant_permissions(["clipboard-read","clipboard-write"])` — se hace SOLO si
  se ratifica (key/stacktrace redactados igualmente).

### Fase B — Deploy + firewall (`PENDIENTE-DE-APROBACIÓN` — se DES-ACTIVA si el gate no la aprueba)

DESCRITA sin detalles de implementación (se explorará/especificará SOLO si
esta fase se ratifica en el gate — G4):

1. Subida de la pila a la VM Proxmox (la misma que ya corre en localhost).
2. Firewall de la VM con reglas NUEVAS (GATE #11, TRD §6.7): SSH de gestión
   desde la LAN + 8080 del frontend. Regla operativa dura: la regla de SSH
   entra ANTES que cualquier cierre, para no perder el acceso.
3. Arranque en frío y reinicio de la VM verificados (pila arriba sola).
4. `nginx client_max_body_size` (deuda heredada C5→C6→C7→C8).
5. Smoke canónico `docker compose` (deuda heredada C1/C4/C5/C6).
6. Secretos `.env` del destino: creados EN la VM, **JAMÁS subidos al repo ni
   impresos** (S16).

**NO entra (explícito):** borrados (3 DELETE) y resumen `include=summary` →
C10; TLS/dominio (fuera de alcance del MVP, TRD §23); rate limiting por
ventana; ningún check visual más allá de lo ratificado en G2.

## 3. File-changes previstos

| Area | Impacto | Descripción |
|---|---|---|
| `scripts/qa_vis_c9.py` | Nuevo | Arnés Playwright headless Python (sync API, 1.64.1) — Fase A |
| `scripts/qa_vis_c9.sh` | Nuevo (si pide wrapper) | env/exit del guion; sin dependencias nuevas de repo |
| `.atl/sdd/C9/qa-log.txt` + `.png` | Nuevos | RAW + screenshots de evidencia (higiene S16) |
| `.atl/sdd/C9/spec.md`, `design.md` (si aplica), `tasks.md`, qa/verify/archive | Nuevos | artefactos SDD del corte |
| `frontend/**` | **SIN cambios** | C9 no toca código de producto en Fase A |
| VM Proxmox / repo de deploy | **SOLO si G4 aprueba Fase B** | se especificará aparte |

Playwright NO entra en `frontend/package.json` ni en el lockfile (QP-8.54
intacto); las deps viven en `~/.local/lib/python3.14/site-packages` +
`~/.cache/ms-playwright` (ya presentes).

## 4. Deudas del proyecto — cuáles cierra C9 y a quién pasan

- **CIERRA C9 (Fase A):** demo visual MANUAL **Q23 ×14** (heredada de C8;
  las "→ demo C9" de los duales quedan cubiertas según lo ratificado en G2).
- **CIERRA C9 (Fase B, si se ratifica):** nginx `client_max_body_size`
  (C5→C9) y smoke canónico `docker compose` (C1→C9, heredada por C4/C5/C6).
- **NOTA de deuda YA CERRADA:** "401 ISO-8859-1 → C8/C9" quedó **CERRADA en
  C8 vía D1** (RestAuthenticationEntryPoint/Ingest + `EntrypointCharsetTest`,
  mvn 177/177). No es deuda de C9.
- **PASAN a C10:** los 3 DELETE operativos y el reemplazo de la UX
  401-logout de los modales por los status del DELETE real (pauta de
  regresiones del estado `hecho` de C8); `include=summary`. Además, el
  residuo local de Fase A (proyecto `qa-c9-<ts>-*` + sus logs con needles y
  la key activa del proyecto C9) se NOTA en el qa-report — la limpieza real
  llega con los DELETE de C10 (jamás SQL directo, jamás `down -v` desde C9).
- **NINGUNA deuda nueva no documentada** — todo lo anterior cita su destino.

## 5. Riesgos

- **R-C9-1 · Destructivo por diseño:** dos casos paran el backend real
  (QP-8.29-c y criterio 3). Mitigación: fase de kill aparte, `try/finally` +
  `up -d backend` + bucle de health con timeout (start_period 60 s ⇒ ventana
  180 s); restauración fallida = exit 2 con "PILOTO CAÍDO" visible; JAMÁS
  `down -v`/postgres/volúmenes; no correr durante otro smoke.
- **R-C9-2 · Falso PASS visual:** la regla dura (ningún PASS sin pantalla
  abierta + aserto semántico + shot) la bloquea; snapshot semántico ≠ diff de
  píxeles es una decisión INTENCIONADA (maquetas con CDN y datos de muestra).
- **R-C9-3 · Contaminación de sesión/BD:** login por UI único + orden del
  explore §C; el caso de sesión-destructiva va AL FINAL; semillas con needle
  `qa-c9-<ts>-<caso>` (no colisionan); NUNCA regenerar keys de proyectos
  preexistentes del usuario.
- **R-C9-4 · Fragilidad de emulación headless:** eventos de frames WS
  (`framesent`/`framereceived`) según versión; PLAN B por conteo de
  handshakes/reconexiones con sub-aserto a BLOQUEADO-anotado; storage
  bloqueado honesto (ver arriba).
- **R-C9-5 · Secretos en evidencia:** S16 en runtime (dotenv en proceso,
  booleanos por evaluate), redacción del shot de la key, grep post-corrida;
  en Fase B los `.env` de la VM jamás al repo.
- **R-C9-6 · Firewall de Fase B (si se ratifica):** regla SSH de LAN en
  primer lugar (evita lockout); especificación aparte tras el gate.

## 6. Criterios de aceptación (done cuando)

Leyenda: [L] = automático en local (arnés); [S] = semi (automático con
matiz/PASS-CON-NOTA ratificable); [R] = residual/regresión; [MANUAL] = ojo
humano.

- **Fase A (obligatoria):**
  - **A1 [L]:** las 14 parejas tienen veredicto individual con snapshot
    semántico sobre el DOM vivo — **cero PASS sin pantalla real observada**
    (regla dura anclada).
  - **A2 [L]:** los 3 criterios demo del 06 pasan automatizados (C2 con
    cronómetro < 120 s; C3 con kill local stop/up y sin duplicados/replay).
  - **A3 [L]:** los extras visuales ratificados en G2 observados por el mismo
    arnés con sus literales; lo NO ratificado no existe como check.
  - **A4 [S]:** evidencia completa en `.atl/sdd/C9/` (qa-log RAW +
    screenshots) con higiene S16 verificada (grep 0 hits `plk_`/`eyJ`; login
    sin campos rellenados; key redactada) y exit 0/1/2 canónico; informe
    resumido en el qa-report.
  - **A5 [R]:** los manuales/PASS-CON-NOTA declarados en §2 quedan escritos
    tal cual en el qa-report (sin silencios); re-corrida de regresión mínima:
    `qa_c8_smoke.sh` verde tras la Fase A (la pila no cambia, solo se
    verifica).
  - **A6 [MANUAL]:** ratificación visual humana de los screenshots por el
    usuario al revisar el qa-artefacto (percepción de contraste/estética).
- **Fase B (SOLO si G4 la ratifica):** pila arriba en la VM; firewall SSH
  LAN + 8080 activo y verificado desde la LAN; frío y reinicio verificados;
  `client_max_body_size` fijado; smoke `docker compose` verde. Si NO se
  ratifica: C9 cierra con Fase A y la ficha C9 del 07 queda partida
  (Fase A hecha; deploy/firewall documentados como pendientes para un corte
  siguiente).

## 7. Decisiones para el GATE (lo que el usuario debe ratificar)

- **G1 — E8.40:** (i) route-interception ⇒ PASS-CON-NOTA (recomendado —
  pantalla real, payload simulado, CTA navegada real) vs (ii) MANUAL puro.
- **G2 — Extras visuales de duales que entran en la v1:** fijar el set
  EXACTO. Recomendado: entran los 8 no-destructivos (8.26, 8.27, 8.34, 8.36,
  8.39, 8.42, 8.45, 8.47) + 8.43 con redacción; opt-in aparte: 8.11 (tamper
  de token), 8.31 (redundante con smoke C7), 8.38 (destructivo-de-sesión, al
  final). Sin inventar checks fuera de este listado.
- **G3 — Clipboard real:** `grant_permissions` sí/no (recomendado: NO en v1;
  Copiar asertado por presencia/habilitación).
- **G4 — Fase B:** ¿aprobada o diferida? Si se aprueba, se abre
  explore/spec acotado SOLO de deploy+firewall antes de implementar. El
  proposal NO avanza a spec/design de Fase B sin este visto bueno.

## 8. Plan de commits previsto (pauta de cortes previos, SIN push)

1. `test(qa): scripts/qa_vis_c9.py — arnés visual headless de las 14
   MANUAL-Q23 + criterios demo del 06 automatizados, snapshot semántico +
   screenshots con higiene S16 (C9)`.
2. `docs(SDD): artefactos del corte C9` — `.atl/sdd/C9/*` (proposal, spec,
   tareas, qa/verify/archive).
- Bucles de hallazgos: commit acotado adicional por ronda (patrón C5–C8).
- Fase B (solo si G4): commits `ops(deploy)/fix(nginx) + docs(SDD)` propios,
  tras su especificación. Nada de push.

## 9. Estimación de tamaño

- ~1 guion nuevo de ~600–900 líneas (14 parejas + extras + fase de kill),
  ~25–35 screenshots, qa-log y artefactos SDD. **Cero ficheros de producto
  modificados en Fase A** — el corte más contenido desde C2 en repo de
  código, y el primero que levanta la regla interna "sin E2E navegado"
  (E8.58), por decisión ratificada del usuario.
- Fase B (si G4): se dimensionará en su propia especificación; cero líneas de
  producto estimadas desde aquí — solo config de VM/firewall/nginx.