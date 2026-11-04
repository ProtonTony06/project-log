---
project: Project_Log
change: C10
artifact: verify-report
last_updated: 2026-11-04
verdict: PASS-with-notes
status: pass-with-notes
---

# Verify Report — C10 · Borrados definitivos con doble confirmación, resumen por proyecto, acceso remoto, fixes de C9

Verificación INDEPENDIENTE del corte C10 sobre el árbol real (HEAD `27b62cb` =
base_head del corte; **SIN commits de C10** — todo el producto vive en el
working tree). Todo el contenido contractual se re-leído del CÓDIGO REAL
contra `spec.md` (13 áreas, REQ-C10-*, matices M1–M10, GATEs G1–G6),
`design.md` (D-C10-1..14 + tabla §4 de 37 ficheros) y `tasks.md` (T10.1–T10.36,
frontera). La evidencia formal de test-run se consume de `qa-report.md`
(mvn 225/225 · Vitest 81/81 · smoke qa_c10 10/10 · arnés 33 marcas · 0 FAIL ·
1 BLOQUEADO canon E9.7-B) — los 4 artefactos de fases anteriores son
`complete`. **El spec es CONTRATO**; donde divergió del design se mandó el
spec (precedente ratificado: orden de bandas DEBUG=4/INFO=5).

**Veredicto: PASS-with-notes** — **0 CRITICAL / 2 WARNING / 5 SUGGESTION**.
Cobertura por área del spec: **13/13 áreas OK** (áreas 1–12 OK; área 13 OK con
1 WARNING documental sobre RAW). Desviaciones ratificadas D-C10-15 (CORS
literales exactos por comportamiento) y D-C10-16 (hunk único en `qa_vis_c9.py`
b6, «C9 tocado en su guion por C10») + 2 fixes de producto RATIFICADOS
(Reloj envuelto, CORS) + desviación operativa menor (recreate arrastró
servicios, pgdata intacto) — ninguna rompe contrato. Pendientes del dueño
(NO bloqueantes) tal como los dejó el qa-report. **Completitud de tareas**:
T10.1–T10.34 verificados a hechos con HECHO-CUANDO concretado; T10.35 (ficha
07 del corte) y T10.36 (commit docs(SDD) selectivo) quedan PAUTADOS y SIN
ejecutar — pauta de los cortes previos (verify no commitea; archive cierra).

## Check A — Frontera dif-0 (tabla §4 del design, 37 ficheros + excepción única): **OK**

- `git status --porcelain` (completo, revisado arriba): los ficheros M/?? que
  tocan `backend/`, `frontend/src/` y `scripts/` son EXACTAMENTE los de la
  tabla §4 — **27 M** (16 src main/test frontend + 11 backend; todos con FC#
  correspondiente: 1,3,4,6,7,8 main · 9,10,11,14,15 test · 16,17,18,19,20,
  21,22,23,24,25,26,27 src · 29,30,33 tests) + **10 ??** de la tabla
  (`SummaryDto` FC#2, `LogsBorradoRepository` FC#5, `ProjectsBorradoSqlTest`
  FC#12, `LogsBorradoSqlTest` FC#13, `ResumenLogs` FC#28,
  `useLogsStream.helpers.test.ts` FC#31-vía-(b), `useAuth.test.ts` FC#32,
  `qa_c10_smoke.sh` FC#37, `qa_vis_c10.py` FC#35, `qa_vis_c10.sh` FC#36) +
  artefactos SDD (`.atl/sdd/C10/`, `.atl/notes/c10-nota-*` ×5) y artefactos
  de corrida (`scripts/__pycache__/`, `.atl/sdd/C9/evidencia/` — SIN tracking,
  ver Check H). El total de diff vs HEAD (28 M + nuevos) cuadra con la stats
  `2026 insertions(+), 122 deletions(-)`.
- **INTOCABLES con ausencia de diff/estado confirmada por git** (nada de ellos
  aparece en `git status` ni en `git diff`): `SecurityConfig`,
  `JwtAuthFilter`, `frontend/src/api/client.ts` (interceptor 401 — G6),
  `ingest/**`, `streaming/**`, `compose.yaml`, `frontend/nginx.conf`,
  `.env` (mtime 2026-10-26 — anterior al corte, intocado),
  `docs/README.md` (gitignored por D1-de-C1 — intocado en disco),
  `storeListado.ts`, `App.tsx`, `useLogFilters.test.ts` (el FC#31 se resolvió
  por la VÍA-(b) `useLogsStream.helpers.test.ts` NUEVO — la opción del design),
  smokes previos `qa_c2`–`qa_c9_smoke`.
- **`scripts/qa_vis_c9.py` — la ÚNICA excepción ratificada, verificada hunk a
  hunk**: `git diff scripts/qa_vis_c9.py` contiene EXACTAMENTE **1 hunk de
  7 inserciones** (verificado con `grep -c '^@@'` = 1; `+7 / -0`) en el bloque
  b6 (~L2026): `page.wait_for_timeout(600)` tras `wait_for_url("**/logs/*?*")`
  y antes de `url_detalle = urlSegura(page.url)`, con comentario español
  que cita la evidencia (fetch propio del detalle dentro de la ventana —
  pseudo-FAIL permanente con producto CORRECTO). Es la **D-C10-16**.
  `qa_vis_c9.sh` bit a bit (no aparece en git status).
- **VM 108 y su `.env`** intocables (REQ-C10-CORS-5): no hay ninguna orden de
  deploy en el árbol ni cambios en `.env`; la desviación operativa de recreación
  fue SOLO pila local (Check G.4).

## Check B — Áreas 1–3: los 3 DELETE con doble confirmación y el orden fail-fast D-C10-1 (contrato flujo 8): **OK**

Verificado DESDE EL CÓDIGO (`ProjectsController.java` + `LogsController.java` —
no desde los informes):

- **Fail-fast D-C10-1 en 8a y 8c (idéntico)** — literal del código:
  (1) `@RequestHeader(name="X-Confirm-Name", required=false)` nulo →
  **400 `confirm_required`** SIN tocar BD; (2) `findById(id).isEmpty()` →
  **404 `not_found`** con `MSG_NOT_FOUND` = «No existe (quizás fue
  eliminado)» (vocabulario C3); (3) `!fila.get().name().equals(confirmacion)`
  — **equals EXACTO String, sin trim** — → **400 `confirm_mismatch`**
  «La confirmación no coincide» SIN `field` (patrón ApiKeyController);
  (4) DELETE → 204 (`noContent()`, sin cuerpo) en 8a / 200 en 8c.
- **8b — mismatch SIN BD (M10)**: comparación contra `String.valueOf(id)` de la
  PROPIA ruta: (1) `X-Confirm-Log-Id` ausente → 400 `confirm_required`;
  (2) valor ≠ id → 400 `confirm_mismatch` sin E/S (id inexistente Y cabecera
  desigual ⇒ 400 determinista); (3) `borrarPorId(id) == 0` → 404 / ≥1 → 204.
- **Desambiguación N=0 (M4/D-C10-2)** en la purga: `int n = purgarPorProyecto(id)`
  — con `n == 0` re-verifica `findById`: vacío ⇒ **404** (nunca 200
  `deleted:0` sobre inexistente, incluida la carrera del CASCADE) / presente
  ⇒ **200 `{"deleted": 0}`**. Comprobar N>0 → 200 `{"deleted": N}` con el N
  REAL del retorno de `JdbcTemplate.update` (cuerpo `LinkedHashMap` +
  `objectMapper.valueToTree`).
- **SQL de los 3 borrados** literales y en su morada: `ProjectsRepository.SQL_BORRAR`
  = `DELETE FROM projects WHERE id = ?` (`borrar(long): boolean`,
  `update==1`, `@Transactional // defensa documentada (R3.2)` — CASCADE del
  MOTOR por `logs_project_fk ON DELETE CASCADE` de V1, la key muere con la fila,
  M5: sin rama de status); `LogsBorradoRepository` NUEVO = única morada de las
  mutaciones del panel sobre `logs`, `SQL_DELETE_POR_ID`/
  `SQL_DELETE_POR_PROYECTO` (+ `@Transactional` en ambos), retorno `int` =
  `deleted: N` (primer uso del retorno de `update` del proyecto — gap 6).
  `LogsQueryRepository` javadoc reescrito: «NINGÚN método ESCRIBE logs»
  (mantiene la frontera LECTURA).
- **Estados del contrato (M2 errata cerrada)**: 204 (8a/8b) · 200
  `{"deleted": N}` (8c) · 400 `confirm_required`/`confirm_mismatch` ·
  404 `not_found` · 401 de la cadena 2 SIN tocar `SecurityConfig`
  (`@DeleteMapping` bajo `/api/v1/panel/**` — ningún antMatcher nuevo).
- **Pines MvcTest re-nombrados y revisados** (`ProjectsControllerMvcTest`
  +20 @Test, `LogsControllerMvcTest` +6): los **5 escenarios M10** completos
  (de 8a/8c: `delete8aSinCabeceraEIdInexistenteResponde400ConfirmRequiredNo404`,
  `delete8aConCabeceraErroneaEIdInexistenteResponde404`,
  `delete8aCabeceraErroneaEnExistenteResponde400MismatchSinBorrar`,
  `delete8aFelizResponde204SinCuerpoYBorraConElIdCorrecto`,
  `delete8aCarreraFilaDesaparecidaResponde404`), ARCHIVED→204 sin rama (M5),
  `{"deleted":55}`/`{"deleted":0}` cuerpo EXACTO Jackson, carrera de purga
  (`purgaCeroConProyectoDesaparecidoCarreraResponde404`), 401 sin JWT en los
  3 endpoints + `sinJwtLasCuatroRutas…`, include; 8b: mismatch sin BD,
  fail-fast id-inexistente+desigual → 400, 404 con cabecera correcta, 401.
  Pins D-9 NUEVOS (`ProjectsBorradoSqlTest` 4, `LogsBorradoSqlTest` 5):
  literales byte-a-byte, 1 placeholder, sin `;`/sub-SELECT, delegación mock,
  `@Transactional` por reflection.
- **SQL real ejecutado** (smoke `qa_c10_smoke.sh` — MvcTest no ejecuta BD):
  10/10 PASS exit 0 — purga N real (`{"deleted":45}`/`{"deleted":0}`),
  CASCADE verificado por count, key muere con la fila (ingesta → 401
  `api_key_invalid`), key sobrevive a la PURGA (ingesta posterior → 202),
  resumen vivo — REQ-C10-DEL-5/DEL-6/DEL-7, PURGA-1/2/4/5, LOGDEL-1/3/4
  cubiertos por REST real sobre siembra propia.
- **Cada REQ de las áreas 1–3**: DEL-1..7 y LOGDEL-1..4 y PURGA-1..5 —
  COMPLIANT (ver matriz al final); la «UI» de sus estados es del Área 10.

## Check C — Área 4: resumen por proyecto (flujo 9) — **OK**

- **`SummaryDto`** NUEVO record de 4 claves snake_case SIN `@JsonInclude` —
  sus 4 claves viajan SIEMPRE; con proyecto sin logs la serialización es
  la literal `{"by_level": {}, "by_environment": {}, "total": 0,
  "last_log_at": null}` (null EXPLÍCITO — pineado:
  `summaryDeProyectoSinLogsEsMapasVaciosTotalCeroYLastLogAtNullExplicito`).
- **`ProjectDto`** 9.º componente `@JsonInclude(NON_NULL) SummaryDto summary`
  + ctor comodín de 8 args delegando `null` (R2 — cero cambios en llamadas
  existentes) + `conResumen(SummaryDto)`. **AUSENTE sin include** pineado:
  `serializaConLasClavesExactasDelSchema33SinSummary` (heredado), +
  `listaSinIncludeMantieneSummaryAusente`, + pin de AUSENCIA en PATCH 200
  (añadido a `patchDescriptionNullLimpiaYActualizaConElComboCorrecto`) —
  POST 201/GET id/lista cubiertos (REQ-C10-RESUMEN-2, R3).
- **Endpoint dedicado** `GET /{id}/summary`: `findById → 404 (sin consultar
  logs: `summaryInexistenteResponde404SinConsultarLogs` — E3.19) →
  `resumen(id)` → 200 forma EXACTA de 4 claves; 401 por cadena 2 en los
  nuevos (`sinJwtLosTresEndpointsNuevosResponde401InvalidToken`).
- **`LogsQueryRepository.resumen(long)` — 3 estamentos pinados D-9** en
  `LogsQuerySqlTest`: NIVELES (`GROUP BY level` + `ORDER BY CASE level WHEN
  'FATAL' THEN 1 WHEN 'ERROR' THEN 2 WHEN 'WARN' THEN 3 WHEN 'DEBUG' THEN 4
  WHEN 'INFO' THEN 5 ELSE 6 END` — **DEBUG=4/INFO=5: el SPEC+maqueta-04 mandan
  sobre la transcripción original de D-C10-3** — desviación ratificada con
  nota `c10-nota-orden-bandas-resumen-case-info-debug.md`), ENTORNOS
  (`ORDER BY environment`), TOTALES (`SELECT COUNT(*), MAX(received_at)` —
  una fila SIEMPRE, nunca `queryForObject` sobre ausencia). Mapas en
  `LinkedHashMap` SOLO claves presentes (RESUMEN-4); `last_log_at` con
  `LoginController.EXPIRES_AT_FORMATTER` (C2 reutilizado por referencia) →
  ISO-Z regex pineado. Dependencia `query.repository → projects.dto` DECLARADA
  en javadoc.
- **`include` fail-fast (D-C10-5)**: orden de checks en el código — (1)
  `status` byte-idéntico PRIMERO, (2) `include != null && ≠ "summary"` → 400
  `filter_invalid_value` field=`include` ANTES de BD (ambos checks antes de
  `findAll`) — `includeInvalidoResponde400FilterInvalidValueConFieldIncludeSinBd`
  + `getStatusInvalido…` (RESUMEN-6).
- **Archivados cuentan** (RESUMEN-5): `listaArchivadaConIncludeMantieneResumenIntacto`
  + smoke/arnés E10.5 (resumen INTACTO en tarjetas archivadas).
- **No recalcula por WS** (RESUMEN-7, D-C10-2 seam): E10.11 del arnés — con
  `/logs` abierto durante purga, delta 0 handshakes/0 set_filters/0 batches;
  refetch posterior vacío; «Actualizar» refresca (E10.3).
- RESUMEN-1: `listaConIncludeSummaryTraeResumenEnTodosLosElementos` + arnés
  E10.1/E10.2 (render fiel = JSON REST vivo — badges/orden/franja).

## Check D — Área 5: CORS G1-a latente — **OK con 1 desviación ratificada (D-C10-15)**

- **Código re-leído (`CorsConfig.java`)**: split por coma de la MISMA
  `FRONTEND_ORIGIN` + `map(String::trim)` + `filter(o → !o.isEmpty())` →
  `List<String> origins`; guard `origins.isEmpty()` → `return source`
  (prohibiciones G1-C2 vigentes); `config.setAllowedOrigins(origins)`;
  methods (ya con DELETE), credenciales false, maxAge 3600 y registro `/**`
  SIN tocar. NADA de `*`, de `setAllowedOriginPatterns` o de env nueva.
- **`CorsConfigTest` (11 @Test)**: 1-origin idéntica al canon C9 (CORS-2),
  2-origins por coma (CORS-1), trim+filtrado de espacios/vacíos intermedios,
  `","`/vacío → cero configuraciones (S26), origin no listada sin cabeceras
  (CORS-4), prohibiciones re-pinadas.
- **Desviación D-C10-15 RATIFICADA (cierre de hallazgo PRODUCTO E10.15)**:
  `setAllowedHeaders` deja el literal `X-Confirm-*` del spec/design y pasa a
  los **4 literales EXACTOS** `Authorization`, `Content-Type`,
  `X-Confirm-Name`, `X-Confirm-Log-Id` — el `DefaultCorsProcessor` de Spring
  matchea los `Access-Control-Request-Headers` por IGUALDAD (solo el `*`
  COMPLETO es wildcard), de modo que el literal con sufijo comodín JAMÁS
  admitía `x-confirm-name` y el preflight real devolvía 403 «Invalid CORS
  request». El design (L146) decía «X-Confirm-* cubre las cabeceras nuevas» a
  nivel de config, pero el COMPORTAMIENTO que exige CORS-3 es el admitido; el
  mando es del spec (comportamiento) sobre el design (literales). Nota con
  Resolución: `c10-nota-cors-preflight-x-confirm-wildcard-no-matcheado.md`.
  **2 pins NUEVOS de profundidad** (`CorsConfigTest @Nested`): preflight REAL
  por la cadena 2 (`OPTIONS /api/v1/panel/projects` + ACR-Method DELETE +
  ACR-Headers `x-confirm-name` → 200 + ACAO reflejada + ACAM DELETE + ACAH
  `x-confirm-name`) y la pieza espejo (origin ajena → 403). Verificado en la
  pila VIVA: curl 200 con reflejo / 403 ajena; arnés E10.15 PASS-CON-NOTA
  post-fix; G1-C2 re-confirmado en este mismo bloque (sin `*` completo, sin
  patterns — los literales son exactos).
- **VM `.env` intocado** (CORS-5): mtime 2026-10-26, anterior al corte; la
  activación del 2.º origin es paso de deploy futuro (a+) — documentado así
  en doc 08 §4 (Check F.3).

## Check E — Áreas 6–9: los 4 fixes H-C9 de primera clase — **OK**

- **Área 6 — WS (D-C10-7/G3-a/M9), en `ws/client.ts`**: código re-leído —
  `export const T_TIMEOUT_CONNECTING_MS = 10_000;`; campo **`timerApertura`
  DISTINTO del `timer` de backoff** (el guard `timer !== null` de
  `reconectar()` no se confunde); en `abrir()`, inmediatamente tras
  `this.socket = socket`, el setTimeout con el **SOCKET CAPTURADO en la
  closure** + guard `readyState === WebSocket.CONNECTING → socket.close()`
  (doble-close inofensivo); **`onopen` cancela el timer ANTES de
  `enviarFiltros()`** (primera línea: `cancelarTimerApertura()` antes de
  `abiertoAlgunaVez/intento=0/alEstado/enviarFiltros`); `alCierre()` añade
  `cancelarTimerApertura()` al inicio; `cerrar()` también lo cancela.
  **Semántica del intento (M9)**: delegación 100 % en el branch EXISTENTE
  `abiertoAlgunaVez` — sin flag nuevo ni manipulación del contador; el close
  del timeout dispara `alCierre()` normal: cadena ⇒ `intento+1` (banner N
  creciente R9.2.11-b) / inaugural ⇒ `alCierreInaugural()` → `me()` — NO
  crece. **7 tests NUEVOS en `ws/client.test.ts`**: delay EXACTAMENTE 10000,
  cancelación en onopen antes de enviarFiltros, vía CADENA
  (`("reconectando", 2)` creciente), vía INAUGURAL (intento NO crece),
  doble-close no-op, `cerrar()` limpia timerApertura. Re-runs honestos del
  arnés: **E9.11/E9.26 PASS-CON-NOTA** con la ventana de kill real (stop
  backend + up + health: banner «Reconectando (intento N)…» con intentos
  [1, 2, 3] crecientes post-caída, restauración automatica — banner retirado,
  set_filters delta 1, refetch pág. 1). WS-3 (resto intacto): dedup/backoff/
  primer-frame-JWT/set_filters sin cambio (los tests que ya los pinan verdes).
- **Área 7 — E9.13 doble «?» (D-C10-10)**: los **3 puntos textuales** en el
  código — `Logs.tsx::alVerDetalle` → `navegar(`/logs/${id}${location.search}`)`;
  los **2** «Volver a logs» de `LogDetalle.tsx` →
  `to={`/logs${location.search}`}` (ambos, el del error y el del flujo
  normal); el guard `storeListado` SIN tocar (untracked en status — dif-0);
  pin de contrato NUEVO en `storeListado.test.ts` (search CON su `?` inicial
  → snapshot consumida; `"​?​?text=qa-c10"` con doble «?» → **null** NUNCA
  coincidiría). **Re-run E9.13 PASS-CON-NOTA** (NEEDLE_E35: query + 60 filas +
  1.ª fila intactas, CERO refetch) — y el hallazgo de su pseudo-FAIL anterior
  se resolvió con la D-C10-16 (Check A) tras repro 3/3. Round-trip E8.35
  intacto (E913-2).
- **Área 8 — E9.2 (D-C10-11)**: helpers PUROS en `useAuth.ts` —
  `destinoTrasLogin(destino)` (null → `/logs`; destino con query intacto) y
  `elNavigateGana(estado, transicionPropia)` = `estado === "autenticado" &&
  !transicionPropia` (predicate EXACTO del render). `Login.tsx`:
  `destinoPropio = useRef<string|null>`, leído de storage ANTES del await
  (el `borrarDestino()` de login() no lo toca), `navegar(destinoPropio.current,
  { replace: true })` tras `sesion.login(...)`, ref a null en el catch
  (higiene de reintentos), y el `<Navigate to="/" replace />` SOLO si
  `elNavigateGana(...)` ⇒ **UNA sola navegación post-login**; llegadas ya
  autenticadas (restore/back) mantienen el Navigate a `/`. 6 tests nuevos en
  `useAuth.test.ts` (destino null/intactos/con query; el predicate). **Re-run
  E9.2 PASS CON URL PERDURANDO** (arribo endurecido, learned 1) — E92-1/E92-2
  COMPLIANT con la certificación end-to-end.
- **Área 9 — E9.10 (D-C10-12, doble defensa)**: `Logs.tsx` —
  `const filtrosSet = useMemo(() => toSetFilters(filtros), [filtros])` (el
  useMemo ya estaba importado) y `useLogsStream({…, filtros: filtrosSet…})`;
  el refetch de página 1 (`[…, filtros]`) SIN tocar. `useLogsStream.ts` —
  helper puro EXPORTADO `debeReenviarFiltros(clavePrevia, filtros)` =
  `JSON.stringify(filtros) !== clavePrevia` + guard por contenido en el efecto
  (`claveFiltros = useRef`); el reenvío de reconexión sigue en `onopen`
  (fuera del guard). Test nuevo `useLogsStream.helpers.test.ts` (3+ casos:
  primera vez true / misma clase —otra identidad, mismo contenido— false /
  cambio real true). **Re-run E9.10 PASS-CON-NOTA (WsContador delta 1)**
  (E910-1).

## Check F — Áreas 10–12: UI de borrado/purga, UX-401 y doc 08 — **OK**

- **UI-1 (G2-a/M1)**: los modales 8a/8c cambian su prop a
  `alConfirmar: (texto: string) => void` y llaman `alConfirmar(texto)` con el
  TEXTO TECLEADO tal cual (sin re-trim en cliente — el trim SOLO gate del
  botón con `coincideLiteralConTrim`); `ejecutarConfirmado(texto: string)`
  manda `purgarLogs(dto.id, texto)` / `eliminarProyecto(dto.id, texto)` —
  **adiós `dto.name`** (el M1 queda cerrado: `confirm_mismatch` alcanzable
  desde la UI). 8b sigue mandando `eliminarLog(id, id)` — id REAL.
- **UI-2 (G6)**: 400 → error literal DENTRO del modal, modal PERMANECE
  abierto/reintentable, **NO logout** (el interceptor solo reacciona con
  status 401 — `client.ts` dif-0). Arnés E10.6/E10.7 con la vía **NBSP
  (U+00A0)** ratificada (extremos-OWS los recorta el TRANSPORTE RFC 9110 —
  el producto es COMPLIANT, comparación EXACTA del servidor con espacios
  INTERIORES queda pinada por MvcTest): 400 `confirm_mismatch` dentro del
  modal, nada borrado (total==3 por REST antes/después), sesión intacta.
  Botón `bg-red-300` deshabilitado → `bg-red-700` (V11 — E10.8 con la
  sentencia literal DENTRO del modal 8b).
- **UI-3**: post-8a 204 → `navegar("/proyectos", {state:{aviso: "Proyecto y
  sus logs eliminados definitivamente"}})`; banner leído de `location.state`
  en `Proyectos.tsx` (E10.13: banner + tarjeta ausente). 404 en 8a → retorno
  a lista con aviso «No existe (quizás fue eliminado)» (rama
  `confirmacion === "eliminar" && fallo.status === 404` en el catch).
- **UI-4**: post-8c 200 → `avisoAccion` con el **N REAL del cuerpo**
  (`` `${r.deleted} logs eliminados; el proyecto y su key siguen intactos` ``)
  + `cargar()` refresca DTO **y resumen** (paralelo
  `resumenProyecto(idNum)`; bloque a «sin logs todavía») — E10.10 con N=78
  real. 404 en 8c → banner rojo §5 de la ficha (errorAccion con modal
  cerrado).
- **UI-5**: `ModalBorrado8b` gana prop opcional **`alExito`** invocada tras el
  204 (guard interno `enviando/error` intacto); `LogDetalle` la pasa con
  `setModalAbierto(false)` + `navegar(`/logs${location.search}`, {state:{
  aviso: "Registro eliminado"}})` — regreso CONSERVANDO la query
  (coherente con Área 7). `Logs.tsx` lee el `aviso` de state en su banner.
  E10.9: modal cerrado + aviso + query intacta — con la **FILA STALE
  NOTADA** (la fila borrada sigue visible hasta el próximo refetch: snapshot
  E8.35 + LiveBroadcaster sin canal de borrados — nota
  `c10-nota-8b-fila-stale-snapshot-sin-refetch.md`, PENDIENTE-DEL-DUEÑO,
  opción abierta, sin pin del arnés).
- **UI-6**: `/proyectos` pide `listaProyectosConResumen()` (literal
  `GET /projects?include=summary` en la cabecera); cada `TarjetaProyecto`
  render `ResumenLogs variante="franja"` SOLO con `summary` presente —
  `BadgeSeveridad`/`PildoraEntorno` REUTILIZADOS (paleta 04_UIUX §2, sin
  color nuevo) como `<span>` puros DENTRO del `<button>` íntegro (nada
  clicable — R7), franja totals tras `border-t` («**79** logs · último:…»
  con el separador de miles con espacio de maqueta-04); archivado con
  `opacity-80` del botón (resumen INTACTO — E10.5, opacity == 0.8 medido y
  resumen vivo); estado vacío «sin logs todavía» italic (M4 — E10.4 en
  tarjeta y ficha). Botón «**Actualizar**» re-invoca `cargar()` (E10.3).
  La UI **jamás reordena** (una sola fuente del orden: el CASE del SQL).
- **UI-7**: sección «Resumen de logs» en ficha (maqueta 05) — total grande +
  «logs totales · último <mono>…» + mismas bandas (`variante="bloque"`)
  dentro del `cargar()` existente; zona destructiva P1 con sentencias mono
  pintadas opera con los status reales (UI-3/UI-4). Degradación honesta:
  fallo no-404 del resumen ⇒ bloque OMITIDO sin romper la ficha
  (D-C10-9, catch vacío documentado).
- **UI-8**: `panel.ts` — retornos `Promise<void>` (8a/8b, 204 sin cuerpo) y
  `Promise<{deleted: number}>` (8c); `listaProyectosConResumen()` +
  `resumenProyecto(id)`; `listaProyectos()` SIN include intacta para los
  mapas de nombres de `Logs.tsx`; `api/types.ts` declara `SummaryDto` (4
  claves, mapas `Partial<Record<Nivel|Entorno, number>>`) y
  `ProjectDto.summary?`; `client.ts` SIN tocar (G6/B.1). Parser pinado por
  `client.test.ts` (2 tests nuevos: 200 `{"deleted":55}` → objeto; 204 →
  void **sin `json()`/`text()` sobre el cuerpo vacío** — el mock THROWS si
  alguien lo leyera).
- **UX401-1**: literal confirmado — `client.ts` ausente del diff; interceptor
  intacto; el 401-falso desapareció AL IMPLEMENTAR los DELETE; residual
  denyAll-en-`/error` queda NO-alcance (M7 ratificado).
- **UX401-2**: E10.6/E10.7 del arnés — 400 real → **sin deslogueo, sin
  /login, token intacto** (sesión viva tras la operación). COMPLIANT.
- **UX401-3**: 401 REAL sí desloguea — comportamiento preexistente CORRECTO
  pinado por las suites C2/C8 (E9.5 del arnés `401 inline limpio` PASS;
  interceptor desloguea con vista destino guardada — canon C8/C9 sin cambios).
- **DOC-1..4 — `docs/08_ACCESO_REMOTO.md` NUEVO (leído)**: 6 secciones del
  esqueleto D-C10-14 — objetivo/alcance con la tabla P1/P2 y SUS estados
  (P1 operativa como ruta de gestión SSH, panel documentado; P2 TLS
  **documentado SIN implementar**); estado de partida C9 (VM 108 «projectlog»,
  Debian 12.15, eth0 192.168.1.58/24 — FB-0; compose 3/3; nftables GATE #11
  solo SSH-LAN+8080; Tailscale SSH `root@100.64.3.17` vía ProxyJump); Tailscale
  P1 (instalación, IP de tailnet, panel por el MISMO puerto 8080 con CERO
  cambios en la Compose — literal §6.8; nota firewall: la regla
  8080-solo-LAN no basta fuera del túnel — regla por tailnet SOLO si el
  usuario la pide); **CORS-LAN CONDICIONADA (a+) PENDIENTE DE RATIFICACIÓN**
  con el paso de deploy futuro EXACTO marcado como pendiente, NO como hecho
  (DOC-3/REQ-C10-CORS-5 — el frontmatter del doc lo declara:
  «Documento operativo, NO del paquete de definición»); proxy P2 SIN
  implementar (upgrade headers, timeouts 3600 s de nginx.conf); referencias.
  **DOC-4 (D1)**: `docs/README.md` sin tocar (gitignored — mtime oct 20, ANTES
  del corte); el doc 08 queda fuera de git por `/docs/*` en `.gitignore`
  (regla D1 de C1: de /docs solo 07 se versiona) ⇒ el commit final T10.36
  necesita `git add -f docs/08_ACCESO_REMOTO.md` SIN tocar `.gitignore`
  (WARNING documental — Check H). Sin secretos/valores de `.env` en el doc
  (S16 — leído).

## Check G — Área 13: QA, cifras canónicas y corridas — **OK con 1 WARNING documental**

1. **mvn**: cifra oficial **225/225, Failures 0, Errors 0, Skipped 0, BUILD
   SUCCESS** (223 de la fase Bloques 1–2 + 2 pins NUEVOS de preflight real de
   D-C10-15). El RAW en disco (`qa-log-mvn-raw.txt`, ts 14:45Z) captura la
   corrida de cierre de Bloques 1–2 (**223/223, exit 0** — verificado al
   final del fichero); la corrida de 225 posterior al fix CORS quedó
   descrita en qa-report/apply-progress PERO su RAW NO está en disco
   (→ WARNING W-1).
2. **Vitest**: oficial **81/81 (9 files) + `npx tsc --noEmit` 0 errores**.
   El RAW en disco (`qa-log-vitest-raw.txt`, 15:44) captura **80/80** — la
   del pin NUEVO del Reloj (81.º test) NO está en disco (→ WARNING W-1, ídem).
   Canon de «55 previos»: el state corrige que eran **60** (errata del canon
   heredado — SUGGESTION).
3. **Smoke qa_c10**: **10/10 PASS · 0 FAIL · exit 0** (ts 15:51) con las 3
   corridas del bucle documentadas (2 FAIL por defectos de guion E1/E2
   parcheados, corrida final verde) — destructivo SOLO contra siembra
   `qa-c10-<ts>-`.
4. **Arnés visual C10** (`scripts/qa_vis_c10.py/.sh`, G5-a — head LEÍDO):
   **import LIMPIO de `qa_vis_c9` constatado** (empíricamente: stdout vacío,
   sin efectos, todas las piezas presentes) + **rebaso de 3 globals**
   (`c9.DIR_QA/.atl/sdd/C10`, `c9.DIR_EVID/evidencia` NO commitear, `c9.TS`
   propio) + **needle() LOCAL prefix `qa-c10-<ts>-`** (jamás la global
   `qa-c9-`) + guard propio de **33 checks (18 re-runs + 15 E10.x)** + orden
   D-C10-13 con **destructivos AL FINAL** + limpieza de residuo con guard
   duro `^qa-c9-|^qa-c10-` (skip-and-report; el proyecto del USUARIO queda
   SALVADO por el guard — verificado en 2 corridas) + kill-window E9.11/E9.26
   ÚLTIMA. **Corrida FINAL canónica: 33 marcas · 0 FAIL · 1 BLOQUEADO
   (E9.7-B canon honesto) · exit 1 (canon del wrapper)** — leído en
   `qa-log.txt` (veredictos reales E9.x y E10.1–E10.15, N real 78, NBSP,
   CORS preflight vivo). 8 re-corridas hasta converger (bucle T10.33:
   hallazgos de GUION parcheados acotados; los 2 de PRODUCTO ratificados y
   remediados). **QA-4/QA-5 COMPLIANT.**
5. **Pautas heredadas (QA-3)**: E3.22 = N/A-post-D2 (ratificada);
   pre-fase qa_c7 = N/A-post-D2 (`/ws-console.html` no publicado);
   **Q23 tolerado** (1 BLOQUEADO en el smoke C8 y en el arnés). Regresiones
   ejecutadas AL FINAL serializadas y sin editar smokes: qa_c3 **22 PASS ·
   2 FAIL · exit 1** (E3.22 la pauta ya ratificada + **E3.24 = ARTEFACTO, no
   regresión**: su guard `git diff` sobre `CorsConfig.java` intocable-de-C8
   no verdea porque el fix D-C10-15 está SIN COMMIT — condición
   «verde-tras-commit», pendiente el commit del dueño); qa_c7 **9/9 PASS ·
   exit 2** (patrón pre-fase ya documentado en qa-report C8 §4.5 — el «exit 0»
   del canon de chat era ERRATA, **reconciliado AL DOCUMENTO**); qa_c8
   **17 PASS · 6 FAIL · 1 BLOQUEADO (Q23) · exit 1** — raíz ÚNICA de los 6
   FAIL: **premisas C8 invalidadas POR EL DISEÑO C10** (destructivos ahora
   REALES 204/204/204; 400 confirm_mismatch donde esperaba 405/401/404;
   summary añadido donde E8.39 esperaba ausencia; colateral E8.27 key muerta
   con la fila borrada de verdad) — CERO defectos de producto; lectura
   recomendada **N/A-post-C10 PENDIENTE-DE-RATIFICAR-POR-EL-DUEÑO**
   (espejo de E3.22).
6. **skill QA del corte (higiene de ejecución)**: pauta E3.22/pre-fase Q23
   aplicada LITERAL como estaba; smokes canónicos SIN editar (ningún diff en
   git); los smokes quedan intocables y las lecturas van por documento.

## Check H — Higiene, hallazgos y pendientes del dueño — **OK con avisos**

1. **S16 — 0 hits CONFIRMADO por grep propio** de `plk_|eyJ` sobre
   `qa-log.txt` (RAW del arnés final), `qa-log-mvn-raw.txt`,
   `qa-log-vitest-raw.txt`: **0 en los 3**. Los hits de grep en los SCRIPTS
   `.py` son comentarios/patrón de regex (S16 los NOMINA — no son secretos).
   El wrapper imprime `[S16] grep del RAW: 0 hits`.
2. **Evidencia**: `evidencia/` = **39 PNGs + `MANIFEST-sha256.txt`** —
   `sha256sum -c` verificado POR verify: **39/39 OK, 0 falidos**. NO
   commitear (untracked ✓ — igual `.atl/sdd/C9/evidencia/`).
3. **`scripts/__pycache__/`** SIN tracking (untracked visible en git
   status) — regla: JAMÁS commiteado, git add SELECTIVO (SUGGESTION:
   considerar regla de `.gitignore` en un corte FUTURO que toque scripts —
   en C10 el design lo prohíbe).
4. **Desviación operativa menor (documentada, sin efecto en datos)**: un
   recreate de backend tras el fix D-C10-15 arrastró a postgres/frontend por
   `depends_on` — **pgdata es volumen NAMED, INTACTO** (4 tablas en public
   verificadas), sin `down -v` en ninguna operación del arnés; el único
   `down -v` que corrió fue el Q13 autocontenido del propio smoke C8
   (canon de ese smoke; pgdata recreado — el proyecto residuo id 1 de
   siembra C8 desapareció con el VOLUMEN, no por el arnés).
5. **Los 5 hallazgos documentales** (`c10-nota-*.md`) existen con
   frontmatter title/type/what/why/where/learned: orden de bandas
   (hallazgo + decisión del orquestador), Reloj desenlazado (hallazgo +
   **Resolución** = fix ratificado), CORS wildcard (hallazgo + **Resolución**
   = D-C10-15), OWS/NBSP (discovery), fila stale 8b (discovery abierta).
6. **Los 4 fixes de producto / desviaciones RATIFICADAS del corte, constatadas
   en el código o el git**:
   - **D-C10-15** (CORS headers exactos) — en el código (Check D).
   - **D-C10-16** (hunk único c9.b6) — en el git (Check A).
   - **fix Reloj envuelto** (`ws/client.ts` constructor default con arrows
     `(fn, ms) => setTimeout(fn, ms)` / `(t) => clearTimeout(t)`) — en el
     código + pin (81.º test).
   - corrección CASE orden bandas (DEBUG=4/INFO=5) — en el SQL (Check C).

## Compliance summary (área → REQ → verdict)

| Área | REQ | Veredicto | Evidencia principal (fuente) |
|---|---|---|---|
| 1 | DEL-1..7 (7) | **OK — COMPLIANT** | MvcTest 8a (`delete8a*` ×6 + 401) + pin D-9 `ProjectsBorradoSqlTest` + smoke S-real |
| 2 | LOGDEL-1..4 (4) | **OK — COMPLIANT** | MvcTest 8b ×6 (mismatch SIN BD, fail-fast, 404 cabecera-correcta) + smoke |
| 3 | PURGA-1..5 (5) | **OK — COMPLIANT** | MvcTest purga ×6 (cuerpos EXACTOS, carrera 404) + smoke N real/CASCADE |
| 4 | RESUMEN-1..7 (7) | **OK — COMPLIANT** | Serialización (5 tests) + `LogsQuerySqlTest` (3 pins) + MvcTest + arnés E10.1/2/3/4/11 |
| 5 | CORS-1..5 (5) | **OK — COMPLIANT (D-C10-15 en CORS-3 como desviación de literales RATIFICADA)** | `CorsConfig` + `CorsConfigTest` 5+2 (preflight REAL) + E10.15 + curl vivo |
| 6 | WS-1..3 (3) | **OK — COMPLIANT** | 7 tests `client.test.ts` + re-run E9.11/E9.26 kill-window |
| 7 | E913-1..2 (2) | **OK — COMPLIANT** | pin `storeListado.test.ts` + 3 puntos textuales + re-run E9.13 (NEEDLE_E35) |
| 8 | E92-1..2 (2) | **OK — COMPLIANT** | helpers `useAuth` + `useAuth.test.ts` + re-run E9.2 URL PERDURANDO |
| 9 | E910-1 (1) | **OK — COMPLIANT** | `debeReenviarFiltros` + Helpers test + re-run E9.10 delta 1 |
| 10 | UI-1..8 (8) | **OK — COMPLIANT** (UI-5 con 1 nota abierta del dueño: fila stale) | E10.1–E10.14 + código de pages/componentes re-leído |
| 11 | UX401-1..3 (3) | **OK — COMPLIANT** | `client.ts` dif-0 + E10.6/E10.7 (sin logout) + E9.5 |
| 12 | DOC-1..4 (4) | **OK — COMPLIANT** | `docs/08_ACCESO_REMOTO.md` (leído; DOC-3 condicionada a+, DOC-4 README intacto) |
| 13 | QA-1..5 (5) | **OK — COMPLIANT** con W-1 (RAW no refrescados) | qa-report (mvn 225/225 · Vitest 81/81 · smoke 10/10 · arnés 33/0/1) |

**Compliance summary: 56/56 REQ-comportamiento C10 COMPLIANT** (el frontmatter
heredado de tasks dice «52 REQ» — la enumeración literal del spec son 56;
errata documental, SUGGESTION). 1 nota de producto abierta (fila stale, 8b)
NO bloqueante: no contradice ningún REQ (el spec no re-escribe la UI tras
borrar un registro si no por REQ-C10-UI-5, que se cumple; la contradicción
banner↔fila es un matiz de UX pendiente de decisión del dueño).

## Issues Found

**CRITICAL**: **None.**

**WARNING** (should fix, NO bloquea el veredicto):

- **W-1 — RAW de las cifras oficiales finales no actualizados en disco**:
  `qa-log-mvn-raw.txt` = **223** (ts 14:45Z, pre-fix D-C10-15) y
  `qa-log-vitest-raw.txt` = **80** (15:44, pre-pin Reloj), mientras las cifras
  OFICIALES del qa-report son **225/225** y **81/81**. La cifra final está
  descrita (apply-progress: «BUILD SUCCESS — Tests run: 225» y «81/81»), pero
  un reproducir-ejecutar la RAW del corte no llega a ellas. Patrón admitido
  en cortes previos (C8 Check F — claims certificados por el apply), pero
  aquí el RAW SÍ existe y queda DESFASEADO. Acción pautada para archive:
  o anotar el desfase en la ficha, o sustituir los RAW antes del
  commit docs(SDD) (SOLO si se re-ejecutan las suites; jamás editar a mano
  un RAW).
- **W-2 — qa-log.txt del corte contiene SOLO la corrida FINAL canónica del
  arnés** (el wrapper teedo sobrescribe): los RAW de smoke qa_c10 (10/10,
  ts 15:51), de las 2 corridas intermedias del arnés y de las regresiones
  qa_c3/qa_c7/qa_c8 NO están en disco — sus cifras viven solo en la prosa de
  qa-report/apply-progress. La cadena de evidencia del arnés final (el
  veredicto del corte) está completa; la de los pasos intermedios es
  de-prosa.

**SUGGESTION**:

- **S-1**: errata documental: tasks/qa-report hablan de «52 REQ» y la
  enumeración del spec son 56; igualmente el canon de «55 Vitest previos»
  eran 60 (state.md ya lo corrige).
- **S-2**: `docs/08_ACCESO_REMOTO.md` queda gitignored por `/docs/*` (regla
  D1-de-C1, intangible): el commit docs(SDD) final (T10.36) DEBE usar
  `git add -f` sobre ese fichero sin tocar `.gitignore` (state.md ya lo
  anota) — riesgo de olvido, repetir en la orden de archive.
- **S-3**: `scripts/__pycache__/` en disco untracked sin regla de ignore —
  mantener la disciplina de `git add` SELECTIVO en T10.36 (regla del corte).
- **S-4**: fila stale post-8b (nota abierta): si el dueño decide
  «expulsión-por-id» o «refetch-con-aviso», el pin del arnés E10.9 y la nota
  se re-ancan en el corte que lo toque; HOY el comportamiento
  implementado (snapshot, sin refetch) es el del spec literal (E8.35).
- **S-5**: el default del `Reloj` del constructor usa arrow envuelta
  (`(fn, ms) => setTimeout(fn, ms)`) — patrón válido; un comentario ya lo
  ancla; para cortes futuros, preferir el helper `setTimeout.bind(globalThis)`
  o envoltura similar en TODO punto nuevo donde se capturen natives (lección
  learned de la nota `c10-nota-crash-logs-ws-reloj-natives-desenlazados.md`).

## PENDIENTES-DEL-DUEÑO (NO bloqueantes del veredicto)

1. **Commit del fix CORS** (D-C10-15): el guard `git diff` de E3.24 (qa_c3)
   verdea tras comitir `CorsConfig.java` — el QA jamás commitea.
2. **Ratificar lectura N/A-post-C10** de los 6 FAIL E8.x de la regresión
   qa_c8 (premisas invalidadas por el diseño C10 — espejo de la pauta E3.22).
3. **Fila stale tras borrado 8b** en listados — opción abierta (expulsión
   por id / refetch con aviso / aceptar-documentar) sin pin del arnés
   (nota `c10-nota-8b-fila-stale-snapshot-sin-refetch.md`).
4. **Canon de chat vs documentos** — reconciliado AL DOCUMENTO (exit 2 de
   qa_c7 es el patrón canónico post-D2; el «exit 0» del chat era errata).
5. *(verificación, de este reporte)* Decidir si al archive se REFRESCAN
   (re-ejecutando las suites) los RAW mvn/vitest para alinear las cifras
   oficiales con la evidencia de disco, o dejar el desfase documentado
   (W-1).

## Tareas (T10.1–T10.36)

- **T10.1–T10.29**: COMPLETAS (verificado contra el código/los scripts uno a
  uno en los Checks B–F; `bash -n` de qa_c10_smoke.sh OK; `py_compile`+`--check`
  del arnés declarados en apply-progress y el RAW final corre el guion
  completo — 33 marcas reales).
- **T10.30–T10.34**: EJECUTADAS con cifras oficiales (Check G); T10.34 con las
  lecturas-pauta de E3.22/pre-fase/Q23 y la recomendación N/A-post-C10
  pendiente del dueño.
- **T10.35** (ficha 07 del corte): SIN ejecutar — `docs/07_SLICING_PLAN.md`
  conserva la ficha C10 «pendiente». No es CRITICAL: el corte NO está
  archivado y la fórmula exige las cifras del qa-report YA disponibles;
  queda pautado para archive tras el veredicto.
- **T10.36** (commit docs(SDD) selectivo): SIN ejecutar — pauta del corte
  (verify no commitea; los commits los genera la fase archive con permiso del
  dueño, patrón de cortes previos).

## Veredicto global

**PASS-with-notes** — **0 CRITICAL / 2 WARNING / 5 SUGGESTION**.

Todo el contenido contractual del corte (12 áreas de producto + el arnés y
smoke propios) está implementado, pineado y certificado: los 3 DELETE con la
doble confirmación y el orden fail-fast EXACTO (D-C10-1/M10), purga con N
REAL y desambiguación N=0, resumen SummaryDto con NON_NULL/endpoint-dedicado/
CASE DEBUG=4-INFO=5 (mandan spec+maqueta), CORS latente por lista sin `*` ni
patterns (con la desviación de literales D-C10-15 ratificada por
comportamiento), fix WS con timer CONNECTING T=10 s y semántica M9 intacta,
los 4 fixes H-C9 certificados con re-runs honestos EN VIVO (E9.2/E9.10/E9.13/
E9.11/E9.26), la UI con el texto tecleado en `X-Confirm-*` (G2-a — M1 cerrado)
y los banners literales del flujo 8, y el doc 08 con la verdad condicionada
(a+) sin hecho-consumado. Frontera dif-0 verificada por git CON la única
excepción ratificada D-C10-16 (1 hunk de 7 inserciones en c9.b6). Las suites:
mvn 225/225, Vitest 81/81, smoke 10/10, arnés 33 marcas/0 FAIL/1 BLOQUEADO
canon. Los 2 Warnings son de REGISTRO (RAW desfasados con las cifras
oficiales; qa-log truncado a la última corrida), no de comportamiento; los 4
pendientes del dueño (commit CORS → E3.24, ratificar N/A-post-C10, fila
stale 8b, canon qa_c7 al documento) y las tareas T10.35/T10.36 quedan a la
fase archive.