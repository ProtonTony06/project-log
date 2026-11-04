---
project: Project_Log
change: C10
artifact: archive-report
last_updated: 2026-11-04
status: complete
---

# Archive-report — C10 · Borrados definitivos, resumen por proyecto, acceso remoto, fixes H-C9

Cierre del ciclo SDD del corte C10. El corte vive EN WORKING TREE sobre la
base `27b62cb` (base_head); **el archive NO commitea** — la fase archive solo
escribe este informe (mode `atl`: no hay `openspec/` que sincronizar ni mover;
el informe `.atl/` es la pista de auditoría y registra las rutas de los
artefactos).

## 1. Cierre del corte C10

### Alcance entregado (todo verificado por verify-report — PASS-with-notes)

- **3 DELETE con doble confirmación** (flujo 8 del AppFlow), con el orden
  fail-fast EXACTO D-C10-1/M10: (1) 400 `confirm_required` SIN tocar BD →
  (2) 404 `not_found` (vocabulario C3) → (3) 400 `confirm_mismatch`
  (equals String EXACTO, sin trim, sin `field`) → (4) borrado. UI manda el
  TEXTO TECLEADO en `X-Confirm-Name` / `X-Confirm-Log-Id` (G2-a — M1
  cerrado: `confirm_mismatch` alcanzable desde la UI). Fail-fast 8b SIN BD
  (comparación contra `String.valueOf(id)` de la propia ruta) — M10.
- **Purga con N real**: `DELETE /projects/{id}/logs` → 200
  `{"deleted": N}` con el N REAL del retorno de `JdbcTemplate.update`;
  desambiguación N=0 D-C10-2/M4 vía re-`findById` (nunca 200 `deleted:0`
  sobre inexistente, incluida la carrera del CASCADE — pineada).
- **Resumen por proyecto (flujo 9)**: `include=summary` en `GET /projects`
  con fail-fast D-C10-5 (status byte-idéntico → include → 400
  `filter_invalid_value` ANTES de BD) + **endpoint dedicado**
  `GET /projects/{id}/summary` (404-antes-de-log). `SummaryDto` record
  nuevo (4 claves snake_case SIEMPRE, `{"by_level": {}, "by_environment":
  {}, "total": 0, "last_log_at": null}` literal con proyecto vacío);
  `ProjectDto` 9.º componente `@JsonInclude(NON_NULL)` con ctor comodín
  8-arg (cero cambios en llamadas); orden de bandas por CASE en el SQL —
  **DEBUG=4/INFO=5 manda el spec REQ-C10-UI-6 + maqueta-04** sobre la
  transcripción de D-C10-3 (desviación ratificada, nota
  `c10-nota-orden-bandas-resumen-case-info-debug.md`). Resumen en tarjeta
  (`ResumenLogs variante="franja"`) y ficha (`variante="bloque"`,
  maquetas 04/05), reutilizando `BadgeSeveridad`/`PildoraEntorno` sin
  color nuevo; archivado con resumen INTACTO (RESUMEN-5); jamás reordena
  (una sola fuente: el CASE); sin recálculo por WS (RESUMEN-7, delta 0).
- **CORS multi-origen LATENTE (G1-a)**: `CorsConfig` acepta
  `FRONTEND_ORIGIN` como lista separada por coma (split + trim + filtrado,
  guard `origins.isEmpty()` → cero registros = canon C9); sin `*`
  completo, sin `setAllowedOriginPatterns`, sin env nueva — G1-C2
  re-pinado. Runtime sin cambio observable (CORS-2) hasta que el
  `.env` cambie. Desviación RATIFICADA **D-C10-15**: `setAllowedHeaders`
  con los **4 literales exactos** (`Authorization`, `Content-Type`,
  `X-Confirm-Name`, `X-Confirm-Log-Id`) porque `DefaultCorsProcessor`
  matchea por IGUALDAD y el literal `X-Confirm-*` del spec/design no era
  materializable (preflight real 403 → hallazgo de producto E10.15
  remediado; nota + Resolución en
  `c10-nota-cors-preflight-x-confirm-wildcard-no-matcheado.md`) + 2 pins
  NUEVOS de preflight REAL por la cadena 2 (200 reflejado / 403 ajena) y
  curl vivo en la pila. La variante (a+) del `.env` de la VM queda
  PENDIENTE-DE-CONFIRMACIÓN-DEL-USUARIO (CORS-5 — VM intocada).
- **Fixes H-C9 de primera clase** (áreas 6–9, re-runs honestos EN VIVO):
  - **H-C9-WS-COLGADO** (D-C10-7/G3-a): timeout CONNECTING T=10 s con
    `timerApertura` distinto del timer de backoff, cancelación en onopen
    ANTES de `enviarFiltros()`, semántica del intento delegada en el
    branch `abiertoAlguaVez` (M9 — cadena crece / inaugural no). Kill
    window E9.11/E9.26: banner «Reconectando (intento N)» con intentos
    [1, 2, 3] post-caída y restauración automática. + fix de producto
    RATIFICADO del RELÓ: default del constructor envuelto por receiver
    (`(fn, ms) => setTimeout(fn, ms)`) — los natives desenlazados
    crasheaban `/logs` entera con `TypeError: Illegal invocation`
    (latente desde C8, activado por C10; nota + Resolución en
    `c10-nota-crash-logs-ws-reloj-natives-desenlazados.md`; pin
    81.º test).
  - **H-C9-E9.13** (D-C10-10): query tal cual en `/logs/${id}` y en los
    2 «Volver» de `LogDetalle` — 3 puntos textuales; guard del store sin
    tocar (pin NUEVO en `storeListado.test.ts`, doble «?» → null NUNCA).
    Re-run NEEDLE_E35 verde (query + 60 filas + 1.ª fila intactas).
  - **H-C9-E9.2** (D-C10-11): una sola navegación post-login — helpers
    puros `destinoTrasLogin`/`elNavigateGana` en `useAuth.ts` + ref en
    `Login.tsx` (destino leído de storage ANTES del await); re-run E9.2
    VERDE con URL perdurando.
  - **H-C9-E9.10** (D-C10-12): UN `set_filters` por cambio real —
    `useMemo` en `Logs.tsx` + `debeReenviarFiltros` (guard por contenido,
    helper puro exportado en `useLogsStream.ts`); reconexión sigue
    reenviando en onopen (fuera del guard). Re-run delta 1.
  - **UX-401-logout → status reales de DELETE** (DEV-C8-4 ratificada,
    G6): 400 dentro del modal PERMANECE abierto/reintentable SIN logout
    (interceptor 401-only, `client.ts` dif-0); 401 real sí desloguea.
    Vía NBSP ratificada para el arnés (el transporte RFC 9110 recorta los
    OWS — el producto es COMPLIANT; nota OWS/NBSP).
- **Doc 08**: `docs/08_ACCESO_REMOTO.md` NUEVO (6 secciones del esqueleto
  D-C10-14): Tailscale P1, TLS P2 **documentado SIN implementar**, estado
  de partida C9 (VM 108, nftables GATE #11), CORS-LAN condicionada (a+)
  documentada como paso de deploy PENDIENTE (no como hecho); frontmatter
  lo declara «Documento operativo, NO del paquete de definición». Doc-4
  (D1): `docs/README.md` intocado; el doc queda gitignored por `/docs/*`
  ⇒ el commit T10.36 exige `git add -f` SIN tocar `.gitignore` (S-2).
- **Arnés propio (G5-a)**: `scripts/qa_vis_c10.{py,sh}` — import LIMPIO
  de `qa_vis_c9` + rebaso de 3 globals (DIR_QA C10, DIR_EVID no-comitear,
  TS propio) + needle prefix `qa-c10-<ts>-` + guard PROPIO de 33 checks +
  orden D-C10-13 con destructivos AL FINAL + limpieza guard
  `^qa-c9-|^qa-c10-` (el proyecto del usuario queda SALVADO, verificado)
  + kill-window ÚLTIMA. Smokes previos C2–C9 intocables salvo la única
  excepción RATIFICADA **D-C10-16** (1 hunk de 7 inserciones en
  `qa_vis_c9.py` bloque b6 — espera 600 ms tras `wait_for_url` para
  excluir el fetch propio del detalle de la ventana de muestreo;
  «C9 tocado en su guion por C10», registrado en qa-report/verify).

### Done-when del 06/07 verificado

Criterios de los docs `06`/`07` (ficha C10) contrastados con las **cifras
oficiales del qa-report** y el veredicto del verify:

| Suite | Resultado oficial | Fuente |
|---|---|---|
| mvn (builder podman) | **225/225 · BUILD SUCCESS** (223 de C10 + 2 pins de preflight real D-C10-15; 177 heredados sin relajar) | `qa-log-mvn-raw.txt` (223) + prose QA-2 |
| Vitest + tsc | **81/81 (9 files) · tsc 0 errores** (60 previos + 20 C10 + pin Reloj) | `qa-log-vitest-raw.txt` (80) + prose QA-2 |
| Smoke qa_c10 | **10/10 PASS · 0 FAIL · exit 0** (SQL REAL: purga `{"deleted":45}`/`{"deleted":0}`, CASCADE, key muere 401, key sobrevive 202) | `qa-log.txt` |
| Arnés visual | **33 marcas · 0 FAIL · 1 BLOQUEADO (E9.7 variante B, canon honesto) · exit 1 canónico** | `qa-log.txt` |
| Regresión qa_c3 | **22 PASS · 2 FAIL · exit 1** (E3.22 = N/A-post-D2 ratificada; E3.24 = ARTEFACTO del guard git-diff — verdea tras el commit) | qa-report |
| Regresión qa_c7 | **9/9 PASS · exit 2** (patrón canon-documento C8 §4.5) | qa-report |
| Regresión qa_c8 | **17 PASS · 6 FAIL · 1 BLOQUEADO (Q23)** (premisas C8 invalidadas POR EL DISEÑO C10 — CERO defectos de producto; lectura recomendada N/A-post-C10) | qa-report |

**Veredicto del verify: PASS-with-notes — 0 CRITICAL / 2 WARNING / 5
SUGGESTION.** 13/13 áreas del spec OK, **56/56 REQ-comportamiento
COMPLIANT** (la «52 REQ» heredada de tasks es errata documental, S-1).
Los 2 WARNING son de REGISTRO (W-1: RAWs de mvn/vitest en disco quedan con
las cifras 223/80 PRE-fix de D-C10-15/pre-pin-Reloj, desfase frente a las
225/81 oficiales descritas en prosa; W-2: qa-log.txt solo contiene la
corrida FINAL del arnés — los RAW de smoke y regresiones viven de prosa),
no de comportamiento; accionables antes del commit docs(SDD) — W-1 admite
o anotar el desfase o RE-EJECUTAR las suites para refrescar RAW (jamás
editar a mano un RAW). Los intocables con dif-0 confirmado por git
(Check A del verify); frontera dif-0 salvo D-C10-16 ratificada.

## 2. Estado final de artefactos

Rutas de auditoría (todas bajo `.atl/sdd/C10/`):

| Artefacto | Ruta | Estado |
|---|---|---|
| Explore | `explore.md` | complete (A backend / B frontend / C arnés+tests / D acceso remoto) |
| Proposal | `proposal.md` | complete (458 líneas; GATE G1–G6 cerrado-en-modo-recomendado 2026-10-29) |
| Spec | `spec.md` | complete (13 áreas, 56 REQ-C10-* enumerados; 10 matices M1–M10) |
| Design | `design.md` | complete (14 decisiones D-C10-1..14; 37 ficheros FC#1–37) |
| Tasks | `tasks.md` | complete (36 tareas T10.1–T10.36, 6 bloques) |
| Apply-progress | `apply-progress.md` | complete (veredicto por bloque; fixes ratificados y desviaciones D-C10-15/16) |
| State | `state.md` | cortes: complete/active — cierra con este archive |
| QA-log | `qa-log.txt` | RAW del arnés FINAL canónico (teed por el wrapper, jamás editado a mano) |
| QA-log RAW unitarias | `qa-log-mvn-raw.txt` (223), `qa-log-vitest-raw.txt` (80) | en disco, DESFASEADOS vs cifras oficiales 225/81 — WARNING W-1 (pendiente del dueño: anotar o re-ejecutar) |
| QA-report | `qa-report.md` | complete (cifras oficiales arriba; desviaciones y pendientes del dueño) |
| Verify-report | `verify-report.md` | PASS-with-notes (0 C / 2 W / 5 S) |
| **Archive-report** | `archive-report.md` | **complete — este fichero** |
| Evidencia | `evidencia/` | **NO commiteada** — 39 PNGs (3,3 MB) + `MANIFEST-sha256.txt` con `sha256sum -c` verificado 39/39 OK; solo los sha256 viven en git vía MANIFEST |
| Notas | `../../notes/c10-nota-*.md` (5) | orden de bandas; Reloj natives (con Resolución); CORS wildcard (con Resolución); OWS/NBSP; fila stale 8b — todas con frontmatter title/type/what/why/where/learned |

## 3. Commits / pendiente de permiso

- El corte vive EN **WORKING TREE** sobre base `27b62cb` — **SIN ningún
  commit de C10 a la fecha de cierre**; la cadena de evidencia (qa-log,
  verify, checks A del verify por `git status/diff`) se construyó sobre
  ese estado.
- El commit **docs(SDD) SELECTIVO + producto + qa queda PREPARADO SIN
  EJECUTAR, esperando permiso del orquestador/dueño** (la fase archive NO
  commitea; patrón de cortes previos — T10.36 declarado no ejecutado a
  este fin).
- Recordatorios para la orden de commit (SUGGESTIONS del verify, aquí
  replicados):
  1. `git add` **SELECTIVO** — jamás `git add -A` (excluiría
     `scripts/__pycache__/`, que NO se commitea jamás);
  2. `git add -f docs/08_ACCESO_REMOTO.md` (gitignored por `/docs/*`,
     regla D1-de-C1 intangible — NO tocar `.gitignore`);
  3. Evidencia PNG NO commitear (solo el MANIFEST-sha256);
  4. grep S16 de staged = 0 antes de commitear; sin push.
- Tras commit: el guard E3.24 de qa_c3 verdea (W-1/W-2 y PENDIENTES-DEL-
  DUEÑO 1 del qa-report/verify).

## 4. Heredadas a cortes futuros (NINGUNA deuda nueva no documentada)

Pendientes del dueño (del qa-report y del verify, NO bloqueantes):

1. **Commit del fix CORS (D-C10-15)** — el guard `git diff` de E3.24
   (qa_c3) verdea después del commit (ver §3).
2. **Ratificar la lectura N/A-post-C10** de los 6 FAIL E8.x de la
   regresión qa_c8 (premisas invalidadas por el diseño C10 — espejo de la
   pauta E3.22 N/A-post-D2).
3. **Fila stale tras borrado 8b** en listados — opción abierta
   (expulsión por id / refetch con aviso / aceptar-documentar), sin pin
   del arnés (nota `c10-nota-8b-fila-stale-snapshot-sin-refetch.md`);
   hoy el comportamiento implementado es el del spec (E8.35).
4. **Variante CORS-LAN (a+)** — agregar `http://192.168.1.58:8080` al
   `.env` de la VM: PENDIENTE-DE-CONFIRMACIÓN-DEL-USUARIO (paso de deploy
   futuro, la VM NO se tocó en este corte; documentado así en doc 08 §4).
5. **Canon de chat vs documentos** — reconciliado AL DOCUMENTO (qa_c7
   exit 2 es canon; el «exit 0» del chat era errata). Cerrada, solo
   registro.
6. **W-1** — decidir si se REFRESCAN los RAW mvn/vitest (re-ejecutando
   suites) antes del commit o se deja el desfase documentado.

Lecciones heredadas para el código futuro:

- **Reloj injectable**: en TODO punto nuevo donde se capturen natives
  (`setTimeout`/`clearTimeout`), preferir envoltura por receiver o
  `setTimeout.bind(globalThis)` — la lección `learned` de la nota
  `c10-nota-crash-logs-ws-reloj-natives-desenlazados.md` (S-5 del verify);
  los defaults de constructor con helpers nativos DESENLAZADOS petan en
  Chromium con `Illegal invocation` y los tests con fake-reloj jamás
  ejercitan la rama default — pinarla cuando se toque.

Deudas de corte explícitas heredadas de la ficha C10 / verify: ninguna
nueva no documentada — las 4 pautas-futuro arriba y la N/A-post-C10
cubren todo lo abierto.

## 5. Fase 1+2 completa

- **C10 cierra la Fase 2**: la ficha C10 de `docs/07_SLICING_PLAN.md`
  queda «`estado: hecho` 2026-11-04» (T10.35 — apply 36/36 T10.1–T10.36
  en 6 bloques, con la síntesis del corte y los pendientes del dueño
  listados). Las 14 stories P0 del PRD §3 quedan cubiertas
  (C2→(1), C3→(2,13,14), C4→(3,4), C5→(5,6,7), C6→(8,9,10), C7→(11,12))
  y C8/C9/C10 cierran el panel, el deploy y la fase 2.
- **Fase 3 (P2)** — no sliceada en este ciclo y NO renegociada: queda
  para el siguiente ciclo según 07 §Fase 3 (líneas 192–204) y doc 08:
  **TLS real (proxy P2: upgrade headers, timeouts 3600 s de
  nginx.conf)**, backups y retención — con la activación CORS-LAN (a+)
  y el ratificado N/A-post-C10 arriba como primeros ítems operativos al
  arrancar el ciclo. Sin renegociar literales: el alcance vivo es el de
  los docs 06/07/08 tal como están en disco.

## Verificación del archive (mode atl)

- [x] Todos los artefactos presentes bajo `.atl/sdd/C10/` (explorar:
      `ls` del corte — explore/proposal/spec/design/tasks/apply-progress/
      qa-log(+RAW)/qa-report/verify-report/evidencia+MANIFEST).
- [x] Rutas registradas en este informe (§2).
- [x] Sin `openspec/` en el proyecto — nada que sincronizar ni mover
      (mode atl: el informe es la pista de auditoría).
- [x] state.md de C10 queda en `active`/pendiente-deriva — este informe
      NO edita ficheros ajenos; el orquestador decide la desactivación
      con el commit.