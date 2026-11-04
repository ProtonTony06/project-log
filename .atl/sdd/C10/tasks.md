---
project: Project_Log
change: C10
artifact: tasks
last_updated: 2026-10-29
status: active
---

# Tasks — C10 · Borrados definitivos con doble confirmación + resumen por proyecto + CORS latente + doc 08 + fixes H-C9

> Fase sdd-tasks del corte C10 (base HEAD 27b62cb). Fuentes normativas:
> `spec.md` C10 (REQ-C10-DEL-1..7, LOGDEL-1..4, PURGA-1..5, RESUMEN-1..7,
> CORS-1..5, WS-1..3, E913-1..2, E92-1..2, E910-1, UI-1..8, UX401-1..3,
> DOC-1..4, QA-1..5; matices M1–M10; GATEs G1=(a) latente, G2=(a), G3=(a),
> G4=(a), G5=(a), G6 — APROBADOS, NO re-decidibles) y `design.md` C10
> (decisiones D-C10-1..14, File Changes §4 — 37 ficheros, CERRADA, plan de
> tests §5, riesgos R1–R12). El spec y el design NO se editan. Los literales
> de API/UI (docs/05 §3.2/§3.3, AppFlow flujo 8/9 §4/§5, 04 V10/V11,
> mockups 04/05) se CITAN — jamás redefinidos. Máx ~2 líneas de contexto por
> tarea: el detalle normativo completo está en spec/design y aquí se cita.

## Frontera (vale para TODAS las tareas — tabla §4 del design, CERRADA)

**Únicamente estos 37 ficheros (crear/editar según la tabla) + artefactos SDD
C10 — nada fuera de la tabla llega a apply:**

- **Backend main (8)**: `projects/controller/ProjectsController` (1, EXTENDE),
  `projects/dto/SummaryDto` (2, NUEVO), `projects/dto/ProjectDto` (3,
  EXTENDE), `projects/repository/ProjectsRepository` (4, EXTENDE),
  `query/repository/LogsBorradoRepository` (5, NUEVO),
  `query/repository/LogsQueryRepository` (6, EXTENDE),
  `query/controller/LogsController` (7, EXTENDE),
  `config/CorsConfig` (8, EXTENDE).
- **Backend test (7)**: `ProjectsControllerMvcTest` (9, EXTENDE),
  `LogsControllerMvcTest` (10, EXTENDE), `ProjectDtoSerializationTest`
  (11, EXTENDE), `ProjectsBorradoSqlTest` (12, NUEVO),
  `LogsBorradoSqlTest` (13, NUEVO), `LogsQuerySqlTest` (14, EXTENDE),
  `config/CorsConfigTest` (15, EXTENDE).
- **Frontend src (18)**: `api/types.ts` (16), `api/panel.ts` (17),
  `ws/client.ts` (18), `hooks/useLogsStream.ts` (19), `hooks/useAuth.ts`
  (20), `pages/Login.tsx` (21), `pages/Logs.tsx` (22), `pages/LogDetalle.tsx`
  (23), `components/ModalBorrado8b.tsx` (24), `pages/ProyectoDetalle.tsx`
  (25), `pages/Proyectos.tsx` (26), `components/TarjetaProyecto.tsx` (27),
  `components/ResumenLogs.tsx` (28, NUEVO), tests: `ws/client.test.ts` (29),
  `hooks/storeListado.test.ts` (30), `hooks/useLogFilters.test.ts` O nuevo
  `hooks/useLogsStream.helpers.test.ts` (31), `hooks/useAuth.test.ts` (32,
  NUEVO), `api/client.test.ts` (33).
- **Docs/scripts (4)**: `docs/08_ACCESO_REMOTO.md` (34, NUEVO),
  `scripts/qa_vis_c10.py` (35, NUEVO), `scripts/qa_vis_c10.sh` (36, NUEVO),
  `scripts/qa_c10_smoke.sh` (37, NUEVO). **Generado en corrida**:
  `.atl/sdd/C10/evidencia/` (PNGs, **EN DISCO, NO commitear** — sha256 al
  qa-report, M6) y `.atl/sdd/C10/qa-log.txt` (RAW teado).
- **INTOCABLES (dif-0)**: `SecurityConfig`, `JwtAuthFilter`, `ingest/**`,
  `streaming/**`, `frontend/src/api/client.ts` (interceptor 401 G6),
  `frontend/nginx.conf`, `compose.yaml`, `frontend/package.json`/lockfile,
  `storeListado.ts`, `App.tsx`, `docs/README.md` (D1), smokes previos
  `qa_c2`–`qa_c9_smoke` + `qa_vis_c9.py`/`.sh` (bit a bit — canon C9),
  **VM 108 y su `.env`** (G1 latente, REQ-C10-CORS-5), `docs/**` salvo doc
  08 nuevo. **NINGÚN `.gitignore` nuevo** (git add SELECTIVO;
  `scripts/__pycache__/` JAMÁS commiteado).

## Orden de dependencias

**Bloque 1** (backend borrados+resumen, DTOs→repos→controllers→tests, mvn
tras cada tocada) → **Bloque 2** (CORS G1-a latente, backend autónomo) →
**Bloque 3** (frontend consumos, tsc + vitest parciales) → **Bloque 4**
(fixes H-C9: WS/E9.13/E9.2/E9.10 — E9.13 toca Logs.tsx/LogDetalle ya
editados en Bloque 3, SECUENCIAL) → **Bloque 5** (doc 08 + smoke SQL +
arnés C10 [destructivos AL FINAL]) → **Bloque 6** (corridas canónicas:
mvn → Vitest → arnés C10 → smokes de regresión AL FINAL → ficha 07 +
commits). Los checks destructivos viven AL FINAL del guion del arnés
(D-C10-13) y las re-corridas de smokes canónicos AL FINAL del corte
(barren siembra con `down -v` — patrón T9.20/T9.31 de C9).

## Reglas transversales de apply

- **Anti-contaminación backend**: mvn INMEDIATO tras cada tocada de
  `backend/**` — JAMÁS continuar con rojo (patrón C8).
- **Frontend**: `npx tsc --noEmit` tras cada fase de módulos; Vitest SIN DOM
  (D-C8-11), corrida `npx vitest run` desde `frontend/`.
- **Regla dura NO fingir PASS**: en arnés y smokes, veredictos canon
  (`PASS | PASS-CON-NOTA | FAIL | BLOQUEADO | Manual`); FAIL honesto → nota
  en `.atl/notes/` + loop-back a apply — jamás parche que maquille (R9.6.3).
- **Higiene S16**: JWT/key/secretos jamás impresos (qa-log/PNG/argv); el
  wrapper JAMÁS exporta `.env` al shell; grep post-corrida `plk_|eyJ` == 0.
- **Destructivo acotado**: DELETEs reales SOLO contra siembra efímera propia
  (`qa-c10-<ts>-…`, needle() local JAMÁS la global `qa-c9-`) y limpieza de
  residuo con guard patrón `^qa-c9-|^qa-c10-` + skip-and-report — JAMÁS
  datos del usuario (REQ-C10-QA-5/D-C10-13); jamás SQL directo ni `down -v`
  desde el arnés.
- **Pauta de commits (sin push)**: commits de producto por bloque
  (`feat(backend)`, `feat(frontend)`, `fix(config)`…) + `test(qa)` para
  arnés/wrapper + `docs(SDD)` final SELECTIVO; todo con `Co-Authored-By:
  Claude Code <noreply@anthropic.com>`. El commit final solo en T10.36.
- **Scope**: nada de Fase 3 (retención, backups, TLS real, VM 107), VM
  intocable, `docs/README.md` dif-0 (R12 del design).

---

## Bloque 1 — Backend borrados + resumen (DTOs → repos → controllers → tests)

- T10.1 — CREATE `backend/.../projects/dto/SummaryDto.java` (FC#2) +
  EDITAR `ProjectDto.java` (FC#3): record `SummaryDto(Map<String,Long>
  by_level, Map<String,Long> by_environment, long total, String
  last_log_at)` (D-C10-4). ProjectDto: 9.º componente `summary` con
  `@JsonInclude(NON_NULL)` + **ctor comodín 8-arg** delegando
  `this(..., null)` (R2: cero cambios en llamadas existentes) + composición
  con `conResumen(SummaryDto)` en el controller (REQ-C10-RESUMEN-2).
  HECHO-CUANDO: `mvn test` compila verde; `ProjectDto` sin summary responde
  SIN la clave `summary` (ausencia, no `null`).
- T10.2 — EDITAR `ProjectDtoSerializationTest.java` (FC#11): summary
  AUSENTE (`doesNotContain("summary")`) en GET lista sin include, GET
  /{id}, POST 201 y PATCH 200 (REQ-C10-RESUMEN-2/R3); con summary → 4
  claves literales; SummaryDto de proyecto sin logs → `{}`/`{}`/`0` con
  `last_log_at` `isNull()` (null EXPLÍCITO — literal §3.3); con dato →
  regex ISO-Z; comentar la línea «sin summary (C10)» obsoleta.
  HECHO-CUANDO: mvn verde con esos pines byte-idénticos a C8 en las formas
  previas.
- T10.3 — EDITAR `ProjectsRepository.java` (FC#4): `SQL_BORRAR`
  package-private literal `"DELETE FROM projects WHERE id = ?"` +
  `borrar(long): boolean` mono-sentencia con `@Transactional // defensa
  documentada (R3.2)`; `update==1` → true, `false` ⇒ 404 de carrera
  (D-C10-2: CASCADE del motor `logs_project_fk`, la key muere con la fila
  — REQ-C10-DEL-1/DEL-5).
  HECHO-CUANDO: mvn compila; `grep` del literal en el repo (pin D-9 pendiente
  de T10.4).
- T10.4 — CREATE `ProjectsBorradoSqlTest.java` (FC#12): pin SQL puro
  patrón D-9 del literal `SQL_BORRAR` (única sentencia, sin pasos
  intermedios — REQ-C10-DEL-5).
  HECHO-CUANDO: mvn verde; el pin falla si el literal cambia.
- T10.5 — CREATE `query/repository/LogsBorradoRepository.java` (FC#5) +
  CREATE `LogsBorradoSqlTest.java` (FC#13): constants package-private
  `SQL_DELETE_POR_ID` = `DELETE FROM logs WHERE id = ?` (8b) y
  `SQL_DELETE_POR_PROYECTO` = `DELETE FROM logs WHERE project_id = ?`
  (8c) + `borrarPorId(id)` / `purgarPorProyecto(id)` con retorno int,
  `@Transactional` mono-sentencia; javadoc «mutaciones del panel sobre
  logs» (D-C10-2/D-C10-3 — NO en LogsQueryRepository, que es SQL de
  LECTURA; REQ-C10-LOGDEL-1, PURGA-1/5).
  HECHO-CUANDO: mvn verde; pin D-9 falla si cualquier literal cambia.
- T10.6 — EDITAR `LogsQueryRepository.java` (FC#6) + EDITAR
  `LogsQuerySqlTest.java` (FC#14): 3 constants literals
  `SELECT_RESUMEN_NIVELES` (GROUP BY + `CASE` FATAL→…→DEBUG/ELSE 6),
  `SELECT_RESUMEN_ENTORNOS` (ORDER BY environment) y
  `SELECT_RESUMEN_TOTALES` (`COUNT(*), MAX(received_at)`) según D-C10-3;
  `resumen(long): SummaryDto` — 3 `query(...)`, mapas en `LinkedHashMap`
  SOLO con claves PRESENTES, `last_log_at` con
  `LoginController.EXPIRES_AT_FORMATTER` (formatter C2), proyecto sin
  logs → mapas vacíos/total 0/`last_log_at` null (REQ-C10-RESUMEN-3/4).
  HECHO-CUANDO: mvn verde con los pins de GROUP BY/CASE/MAX y del orden
  maqueta-04 de las bandas.
- T10.7 — EDITAR `ProjectsController.java` (FC#1): (a) `DELETE /{id}` y
  `DELETE /{id}/logs` con el **orden fail-fast EXACTO de D-C10-1** (8a/8c):
  (1) `X-Confirm-Name` ausente → 400 `confirm_required` SIN tocar BD,
  (2) `findById` vacío → 404 `not_found` (MSG_NOT_FOUND C3), (3) equals
  EXACTO contra el nombre en BD → 400 `confirm_mismatch`
  («La confirmación no coincide», sin `field`), (4) borrado; (b) purga:
  200 con `{"deleted": N}` (ObjectMapper/LinkedHashMap, D-C10-2) y
  desambiguación N=0 → re-check `findById` → presente `{"deleted": 0}`
  (M4) / vacío 404 (carrera CASCADE incluida); (c) `GET /{id}/summary`:
  `findById` → 404 → `resumen(id)` → 200; (d) `include` en listar con
  checks en orden (1) status (byte-idéntico) (2) `include≠summary` → 400
  `filter_invalid_value` field=`include` ANTES de BD (D-C10-5);
  constants `CODE_/MSG_` nuevas + helper `error()` patrón C3 + javadoc
  vocabulario (REQ-C10-DEL-1..4, PURGA-1..5, RESUMEN-1..6).
  HECHO-CUANDO: mvn compila (pin de estados lo toma T10.9); endpoints
  visibles bajo la cadena 2 `/api/v1/panel/**` sin tocar SecurityConfig.
- T10.8 — EDITAR `LogsController.java` (FC#7): `DELETE /{id}` orden 8b de
  D-C10-1 — (1) `X-Confirm-Log-Id` ausente → 400 `confirm_required`;
  (2) valor ≠ `String.valueOf(id)` EXACTO → 400 `confirm_mismatch` SIN
  BD (id inexistente Y cabecera desigual ⇒ 400, M10); (3) `borrarPorId`
  → `false` 404 / `true` 204 (REQ-C10-LOGDEL-1..3).
  HECHO-CUANDO: mvn compila; el orden se pina en T10.10.
- T10.9 — EDITAR `ProjectsControllerMvcTest.java` (FC#9): ~14 @Test
  nuevos con los **5 escenarios M10** de D-C10-1 — 8a/8c: id=999999 SIN
  cabecera → 400 `confirm_required` (NO 404); id=999999 con cabecera
  ERRÓNEA → 404; existente + errónea (mayúsculas/espacio interior) → 400
  `confirm_mismatch` + `verifyNoInteractions(repo)`; existente + correcta
  → 204 sin cuerpo + `verify(borrar)` con id correcto; carrera
  `purgar`→0 + `findById`→vacío → 404. Más: PURGA-1 cuerpo EXACTO
  `{"deleted":55}` (Jackson) y `{"deleted":0}`; DEL-6 ARCHIVED → 204 sin
  rama de status (M5); DEL-7 401 sin JWT; RESUMEN: include → summary en
  TODOS los elementos, sin include/POST/PATCH/GET id clave AUSENTE,
  forma EXACTA 4 claves, solo presentes, 404 y 401 de `/{id}/summary`,
  archivado con resumen idéntico (RESUMEN-5), `include=otracosa` → 400
  con field=include y BD sin invocar (D-C10-5).
  HECHO-CUANDO: mvn verde (MvcTest no ejecuta BD — la CASCADE real va al
  smoke T10.28, REQ-C10-DEL-5).
- T10.10 — EDITAR `LogsControllerMvcTest.java` (FC#10): ~7 @Test de 8b —
  204 feliz (`verify(borrarPorId)` con id correcto); 400
  `confirm_required`; 400 `confirm_mismatch` SIN BD
  (`verifyNoInteractions`); fail-fast M10 de 8b: id inexistente + cabecera
  desigual → 400; 404 con `X-Confirm-Log-Id` correcto sobre inexistente;
  solo `borrarPorId` tocado, nada del proyecto (LOGDEL-4); 401 sin JWT.
  HECHO-CUANDO: **mvn completo verde** — 177 previos + nuevos de Bloque 1,
  cero asertos heredados editados (REQ-C10-QA-1/3).

## Bloque 2 — CORS G1-a latente (backend autónomo)

- T10.11 — EDITAR `config/CorsConfig.java` (FC#8) + EDITAR
  `config/CorsConfigTest.java` (FC#15): sustitución EXACTA del bloque
  actual por el fragmento de D-C10-6 — split por coma + trim + filtrado
  de vacíos sobre la MISMA `FRONTEND_ORIGIN` (sin `*`, sin
  `setAllowedOriginPatterns`, sin env nueva — G1-C2 vigente); guard
  `origins.isEmpty()` (incluida `","` solitaria) → return source = CORS
  cerrado por defecto (S26/E2.18); methods/headers/credentials/maxAge y
  registro `/**` SIN tocar. CorsConfigTest con los **5 casos**: (1) una
  origin → lista idéntica a C9 (REQ-C10-CORS-2, latente); (2) `a,b` →
  dos origins; (3) `a, b , ,c` → trim+filtrado; (4) `","`/vacío → cero
  registros; (5) Origin no listada → sin cabeceras CORS; +
  re-pin de prohibiciones G1-C2 (`getAllowedOriginPatterns` vacío/nulo).
  HECHO-CUANDO: mvn verde; con el `.env` actual de una sola origin el
  comportamiento es byte-idéntico al C9 — «sin cambio observable»
  (check del arnés E10.x).

## Bloque 3 — Frontend consumos (tipos → api → páginas/componentes)

- T10.12 — EDITAR `frontend/src/api/types.ts` (FC#16): `SummaryDto` TS
  (4 claves), `ProjectDto.summary?` y mapas tipados
  `Partial<Record<Nivel|Entorno, number>>`; comentario R8.18 actualizado
  con el literal `GET /projects?include=summary` (REQ-C10-UI-8).
  HECHO-CUANDO: `npx tsc --noEmit` verde; `panel.ts` compila tipos.
- T10.13 — EDITAR `api/panel.ts` (FC#17): retornos reales —
  `eliminarProyecto`/`eliminarLog` → `Promise<void>` (204; hoy
  `Promise<never>` nunca resuelve), `purgarLogs` → `Promise<{deleted:
  number}>` (200 `{deleted:N}`, parser tolerante de `finalizar()`);
  `listaProyectosConResumen()` (GET `/projects?include=summary`) y
  `resumenProyecto(id)` (GET `/{id}/summary`); cabeceras
  `X-Confirm-*` YA formadas ahora reciben el parámetro de texto
  (REQ-C10-UI-8; `client.ts` SIN tocar — G6/B.1).
  HECHO-CUANDO: tsc verde; `listaProyectos()` sin include queda intacta
  para los mapas de nombres de `Logs.tsx`.
- T10.14 — EDITAR `api/client.test.ts` (FC#33): parser — DELETE con 200
  {deleted:N} → objeto; 204 → void (sin `json()` sobre cuerpo vacío)
  (REQ-C10-QA-2). HECHO-CUANDO: vitest de ese fichero verde; 55 previos
  sin regresión.
- T10.15 — EDITAR `pages/ProyectoDetalle.tsx` (FC#25) +
  `components/ModalBorrado8b.tsx` (FC#24): **G2-a cierra M1** — props
  `alConfirmar: (texto: string) => void` en modales 8a/8c y
  `ejecutarConfirmado(texto)` manda `purgarLogs(dto.id, texto)` /
  `eliminarProyecto(dto.id, texto)` — el TEXTO TECLEADO viaja tal cual
  (sin re-trim en cliente; trim solo gate del botón). Post-estados de
  D-C10-8: 8a 204 → `setConfirmacion(null)` + navegar `/proyectos` con
  `state:{aviso:"Proyecto y sus logs eliminados definitivamente"}`; 8a
  404 → banner §5 + retorno a lista; 8c 200 → banner de estado local
  `avisoAccion` (Banner variante="info") literal
  «N logs eliminados; el proyecto y su key siguen intactos» con el N
  REAL del cuerpo → `cargar()` refresca DTO **y resumen** (parallel
  `resumenProyecto`); `ModalBorrado8b` gana prop `alExito` invocada tras
  el 204 con su guard `enviando/error` intacto; bloque «Resumen de logs»
  (maqueta 05) bajo «Datos del proyecto»; comentarios «hoy 405/404»
  reescritos (REQ-C10-UI-1..4, UI-7).
  HECHO-CUANDO: tsc verde; los 400 no tocan el interceptor (G6 — check
  E10.x del arnés).
- T10.16 — CREATE `components/ResumenLogs.tsx` (FC#28) + EDITAR
  `components/TarjetaProyecto.tsx` (FC#27): componente compartido con
  prop `resumen: SummaryDto` + `variante: "franja" | "bloque"` (D-C10-9)
  — bandas de badges `lv-*` (FATAL primero, WARN/ERROR/FATAL destacados,
  paleta 04_UIUX §2 reutilizada), bandas `env-*`, franja totals tras
  `border-t` («25 330 logs · último: <mono>…»), estado vacío
  «sin logs todavía» (M4: no error); SOLO `<span>`/texto plano dentro
  del `<button>` íntegro — nada clicable nuevo; archivado cubierto por
  `opacity-80` existente, sin rama especial. `TarjetaProyecto` renderiza
  la franja solo con `summary` presente; comentarios «SIN resumen P1
  (R8.18)» reescritos con el literal nuevo (REQ-C10-UI-6/7).
  HECHO-CUANDO: tsc verde; navegación por clic de la tarjeta intacta.
- T10.17 — EDITAR `pages/Proyectos.tsx` (FC#26): `listaProyectosConResumen()`,
  banner de `location.state.aviso` (patrón ya usado por ProyectoDetalle),
  botón **«Actualizar»** que re-invoca `cargar()` (flujo 9 paso 5), literal
  de cabecera `GET /projects?include=summary` (maqueta 04).
  HECHO-CUANDO: tsc verde; el resumen llega por include (RESUMEN-1) y
  «Actualizar» refresca (RESUMEN-7 — check E10.x).
- T10.18 — EDITAR `pages/LogDetalle.tsx` (FC#23, parte post-8b) +
  `pages/Logs.tsx` (FC#22, parte banner): `LogDetalle` pasa
  `alExito={() => { setModalAbierto(false); … navegar("/logs" +
  location.search) }}` con `state:{aviso:"Registro eliminado"}` — regreso
  conservando la query (D-C10-8/10); `Logs.tsx` gana lectura del aviso en su
  banner de estado (REQ-C10-UI-5).
  HECHO-CUANDO: tsc verde. *(La query exacta que viaja se pina en T10.22 —
  D-C10-10.)*
- T10.19 — Gate de fase: `npx tsc --noEmit` + `npx vitest run` desde
  `frontend/`. HECHO-CUANDO: tsc verde y los 55 Vitest previos sin
  regresión (los nuevos de Bloque 3 van con T10.14; el gate final con
  T10.32).

## Bloque 4 — Fixes H-C9 (WS timeout · E9.13 · E9.2 · E9.10)

- T10.20 — EDITAR `ws/client.ts` (FC#18) — **D-C10-7 (G3-a/M9), fix
  H-C9-WS-COLGADO**: constant exportada `T_TIMEOUT_CONNECTING_MS =
  10_000`; campo `timerApertura` DISTINTO del `timer` de backoff; en
  `abrir()` inmediatamente tras `this.socket = socket` el setTimeout con
  el **socket capturado en la closure** + guard
  `readyState === WebSocket.CONNECTING → socket.close()`; `onopen`
  cancela el timer ANTES de `enviarFiltros()` (sin cierres espurios);
  `alCierre()` añade `cancelarTimerApertura()` en primera línea;
  `cerrar()` también lo cancela; NADA más del ciclo (dedup,
  primer-frame-JWT, set_filters, backoff — REQ-C10-WS-1..3).
  HECHO-CUANDO: tsc verde; la semántica del intento NO cambia (delegación
  en el branch existente `abiertoAlgunaVez` de `alCierre()` — pines en
  T10.21).
- T10.21 — EDITAR `ws/client.test.ts` (FC#29): 6+ tests del ciclo stubbed
  (readyState controlable + Reloj fake) — delay capturado EXACTAMENTE
  10000; **vía cadena** (socket 1 abre, muere, socket 2 cuelga en
  CONNECTING → timeout → close → `alEstado("reconectando", N creciente)`);
  **vía inaugural** (PRIMER socket cuelga → timeout → close →
  `alCierreInaugural` y `alEstado("desconectado", 0)` — intento NO crece,
  M9); cancelación en onopen ANTES de enviarFiltros; doble-close no-op;
  `cerrar()` limpia timerApertura; `retrasoReconexion`/serializar/dedup
  intactos (REQ-C10-WS-1..3).
  HECHO-CUANDO: vitest verde; los 55 previos sin regresión.
- T10.22 — Fix H-C9-E9.13 (D-C10-10): EDITAR `pages/Logs.tsx`
  (`alVerDetalle` → `navegar(`/logs/${id}${location.search}`)`) y
  `pages/LogDetalle.tsx` (AMBOS «Volver a logs», ~L113 y ~L139 →
  `to={`/logs${location.search}`}`) — **3 puntos textuales**, se ELIMINA
  el «?» interpolado; `storeListado.ts` SIN tocar; +
  EDITAR `hooks/storeListado.test.ts` (FC#30): pin de contrato —
  `guardarListado("?text=qa-c10")` + `consumirListado("?text=qa-c10")` →
  snapshot; `consumirListado("??text=qa-c10")` → null (REQ-C10-E913-1/2).
  HECHO-CUANDO: vitest verde con el pin de search EXACTA; certificación
  real = re-run E9.13 del arnés (NEEDLE_E35).
- T10.23 — Fix H-C9-E9.2 (D-C10-11, vía «condicionar»): EDITAR
  `hooks/useAuth.ts` (FC#20) — helpers PUROS `destinoTrasLogin(destino):
  string` (null → `/logs`; destino con query intacto) y
  `elNavigateGana(estado, transicionPropia)` (predicate EXACTO
  `estado === "autenticado" && !transicionPropia`); EDITAR
  `pages/Login.tsx` (FC#21) — `destinoPropio = useRef<string|null>`
  leído antes del await (borrarDestino() no lo toca), `navegar(
  destinoPropio.current, {replace:true})` tras `sesion.login(...)`,
  ref a null en errores, render: `<Navigate to="/" replace />` SOLO si
  `elNavigateGana(...)` (llegadas ya autenticadas — restore/back);
  CREATE `hooks/useAuth.test.ts` (FC#32) con tests de ambos helpers
  (REQ-C10-E92-1/2).
  HECHO-CUANDO: vitest verde; certificación real = re-run E9.2 endurecido
  (URL PERDURANDO ~700 ms, learned 1).
- T10.24 — Fix H-C9-E9.10 (D-C10-12, doble defensa): EDITAR
  `hooks/useLogsStream.ts` (FC#19) — guard por contenido con
  `claveFiltros = useRef`, `JSON.stringify(p.filtros)` e igualdad EXACTA
  + helper puro exportado `debeReenviarFiltros(clavePrevia, filtros)`; el
  reenvío de reconexión sigue en `onopen` (fuera del guard); EDITAR
  `pages/Logs.tsx` — `const filtrosSet = useMemo(() => toSetFilters(
  filtros), [filtros])` y paso al hook; el refetch de página 1
  (`[…, filtros]`) SIN tocar; CREATE/EDITAR test del helper (FC#31:
  `hooks/useLogsStream.helpers.test.ts` NUEVO o extensión de
  `useLogFilters.test.ts`) — primera vez → true, misma clase → false,
  cambio real → true (REQ-C10-E910-1).
  HECHO-CUANDO: vitest verde; certificación real = re-run E9.10 (delta de
  `aplicarFiltros` 1 por evento, 0 en renders ajenos).
- T10.25 — Gate de fase de fixes: `npx tsc --noEmit` + `npx vitest run`
  desde `frontend/`. HECHO-CUANDO: tsc verde; los 55 Vitest previos +
  los nuevos de Bloques 3–4 VERDES sin regresión; el resto del ciclo WS
  (backoff 1 s→30 s, dedup D-C8, set_filters S8-8, primer-frame-JWT) sin
  cambio observable en los tests que ya lo pinan (REQ-C10-QA-2).

## Bloque 5 — Doc 08 + smoke SQL + arnés visual C10

- T10.26 — CREATE `docs/08_ACCESO_REMOTO.md` (FC#34): doc nuevo con
  numeración/estilo de cabecera de 01–07 y las 6 secciones de D-C10-14 —
  objetivo/alcance (P1 Tailscale, P2 TLS documentado sin implementar);
  estado de partida C9 (VM 108, compose 3/3 healthy, nftables GATE #11,
  Tailscale SSH `root@100.64.3.17` vía ProxyJump — FB-0); Tailscale P1
  (instalación, IP de tailnet, panel por el MISMO puerto 8080 con **cero
  cambios en la Compose**, literal TRD §6.8; nota firewall: regla
  8080-solo-LAT no basta fuera del túnel — regla por tailnet SOLO si el
  usuario la pide); CORS-LAN **CONDICIONADA (a+) pendiente de
  ratificación** con el paso de deploy futuro EXACTO (añadir
  `http://192.168.1.58:8080` al `.env` de la VM + `docker compose up -d
  backend`) como pendiente, NO como hecho; proxy inverso HTTPS P2 SIN
  implementar (upgrade headers, timeouts 3600s de `frontend/nginx.conf`).
  `docs/README.md` dif-0 (D1) (REQ-C10-DOC-1..4).
  HECHO-CUANDO: doc en `/docs` con los 4 REQ trazados; `git diff
  docs/README.md` vacío; NINGÚN valor de `.env`/secreto en el doc (S16).
- T10.27 — CREATE `scripts/qa_c10_smoke.sh` (FC#37): estilo canónico de
  los `qa_c*_smoke` previos (sin editarlos), veredictos marcados, pila
  compose para **ejecución REAL de SQL** — purga con N real (200
  {deleted:N}), CASCADE verificado (count logs → delete project → count
  0), key muere con la fila (ingesta con esa key → 401 `api_key_invalid`),
  ingesta 202 post-purga sobre proyecto purgado (la key sobrevive a la
  PURGA — par de proyectos: uno purgado, otro eliminado), resumen con
  datos reales (REQ-C10-DEL-5, PURGA-1/2, QA-1).
  **[DESTRUCTIVO — SOLO contra su siembra propia `qa-c10-*`]**.
  HECHO-CUANDO: `bash -n` verde; 1.ª corrida (T10.28) termina exit 0 con
  veredictos contados.
- T10.28 — PRIMERA corrida `bash scripts/qa_c10_smoke.sh`: registrar
  cifra de veredictos + ts para el qa-report; cualquier FAIL → nota en
  `.atl/notes/` (jamás parche que maquille).
  **[DESTRUCTIVO — siembra propia]**. HECHO-CUANDO: smoke verde antes de
  seguir; cifra anotada.
- T10.29 — CREATE `scripts/qa_vis_c10.py` (FC#35) + `scripts/qa_vis_c10.sh`
  (FC#36) — **D-C10-13 (G5-a)**: import limpio `import qa_vis_c9 as c9`
  (top-level sin efectos; si el import resultara sucio, fallback copia
  controlada función-a-función — decisión binaria citada EN EL HEAD del
  script); rebaso declarado de los 3 globals (`c9.DIR_QA` →
  `.atl/sdd/C10`, `c9.DIR_EVID` → `.atl/sdd/C10/evidencia/` NO commitear,
  `c9.TS` propio); `needle()` LOCAL con prefix `qa-c10-<ts>-` (JAMÁS la
  global `qa-c9-`) y literales/credenciales propias; checklist E10.x —
  re-runs honestos E9.2/E9.10/E9.13 (b1/b3/b6 del módulo C9) y
  E9.11/E9.26 (kill-window `stop backend` + `up -d backend` + health,
  **AL FINAL de la corrida**); checks nuevos: resumen (tarjeta con badges
  `lv-*`/`env-*` + franja totals, archivado resumen-intacto, estado vacío
  «sin logs todavía», bloque de ficha, «Actualizar» refresca), doble
  confirmación (trim-igual-malo → `confirm_mismatch` DENTRO del modal,
  nada borrado, **sesión intacta sin logout** — G6/UX401-2, botón
  `bg-red-300` deshabilitado, 8b sentencia literal), estados flujo 8
  (8c → banner con N real + resumen a cero; 8b → «Registro eliminado» +
  regreso a /logs con query; 8a → banner + tarjeta ausente),
  post-estados (refetch /logs vacío, ingesta 202 con la key, WsContador
  delta frames 0 y handshakes 0 tras purga con /logs abierto), CORS
  latente (preflight OPTIONS DELETE con X-Confirm-Name reflejado, sin
  cambio observable — E10.x); **destructivos AL FINAL y SOLO contra
  siembra efímera propia** (el proyecto propio se purga 8c y elimina 8a
  ANTES de la kill-window); limpieza de residuo C9: enumera GET
  /projects y purga+delete SOLO con guard `^qa-c9-|^qa-c10-` (skip-and-
  report con guard duro si no matchea); guard propio de b8 con el nº
  real de checks C10; wrapper gemelo de `qa_vis_c9.sh` — RAW teed a
  `.atl/sdd/C10/qa-log.txt`, grep S16 `plk_`/`eyJ` == 0, passthrough
  exit canon 0/1/2. `qa_vis_c9.py`/`.sh` bit a bit (M6/REQ-C10-QA-4/5).
  **[ARNÉS — contiene checks destructivos, AL FINAL de su corrida]**.
  HECHO-CUANDO: `py_compile` + `bash -n` verdes; `git diff
  scripts/qa_vis_c9.*` vacío; el orden B0 → re-runs suaves → E10.x →
  destructivos → limpieza → kill-window → informe queda en el head.

## Bloque 6 — Corridas canónicas y cierre

- T10.30 — mvn completo canónico: `mvn -f backend/pom.xml test` →
  **177 previos + nuevos** VERDES (MvcTests 3 DELETE + summary +
  serialización + pins SQL + CORS), cero asertos heredados editados o
  relajados (REQ-C10-QA-1/QA-3). HECHO-CUANDO: cifra nueva anotada para
  qa-report/state.
- T10.31 — Vitest completo: `npx vitest run` desde `frontend/` →
  **55 previos + nuevos** (client/storeListado/ws/useAuth/useLogsStream
  helpers) VERDES SIN DOM; los que un fix re-especifica citan literal,
  nunca relajan (REQ-C10-QA-2). HECHO-CUANDO: cifra nueva anotada.
- T10.32 — CORRIDA completa del arnés visual C10:
  `bash scripts/qa_vis_c10.sh` (RAW → `.atl/sdd/C10/qa-log.txt` + PNGs
  en `evidencia/` NO commitear; inventario nombres + sha256 para el
  qa-report) — exit canon 0/1/2.
  **[ARNÉS — DESTRUCTIVO: sus DELETEs corren AL FINAL y SOLO contra
  `qa-c10-*`; después limpieza de residuo C9 y kill-window E9.11/E9.26]**.
  HECHO-CUANDO: 4 re-runs honestos post-fix (E9.2 URL PERDURANDO, E9.10
  delta 1, E9.13 NEEDLE_E35, E9.11/E9.26 banner retirado + set_filters
  reenviado + refetch pág. 1) + checks E10.x con guard C10; grep S16
  post-corrida == 0 hits; cifra de veredictos anotada.
- T10.33 — BUCLE de hallazgos del arnés (patrón T9.14/C5–C8): defecto DEL
  ARNÉS → parche acotado a `qa_vis_c10.py`/`.sh` + re-corrida;
  hallazgo de PRODUCTO → FAIL honesto + nota en `.atl/notes/` + decisión
  del orquestador (jamás auto-arreglo ni maquillaje). Re-corrida hasta
  verde. HECHO-CUANDO: corrida final con veredictos estables y notas
  trazables.
- T10.34 — RE-CORRIDAS de regresión de smokes canónicos AL FINAL del
  corte (tras el arnés — su `down -v` barre la siembra, patrón
  T9.20/T9.31): `qa_c3_smoke` con la pauta **E3.22 = ratificado/N/A-post-D2**,
  `qa_c7_smoke` con **pre-fase = N/A-post-D2** y **Q23 tolerado**
  (demo visual MANUAL), `qa_c8_smoke` → **24 marcas · 23 PASS · 0 FAIL ·
  1 BLOQUEADO (Q23)**, exit 0 — sin editar smokes canónicos.
  **[DESTRUCTIVO — `down -v` barre la siembra]**
  HECHO-CUANDO: cifras idénticas al canon anotadas; tras la última
  re-corrida, re-siembra SOLO si el usuario pide demo (nota en
  state/qa-report, patrón T9.32; también re-siembra del residuo del
  arnés C10 si se pedirá, usando el propio DELETEs de C10).
- T10.35 — EDITAR `docs/07_SLICING_PLAN.md` (ficha del corte C10): estado
  **hecho** con la fórmula de cortes anteriores (corte cerrado, veredicto
  del arnés C10 y cifras mvn/Vitest/smoke) SIN renegociar literales de
  rutas/guard de cortes previos. HECHO-CUANDO: ficha al día con cifras
  reales de T10.30–T10.34.
- T10.36 — COMMIT docs(SDD) FINAL SELECTIVO sin push (pauta única del
  corte): `git add` SELECTIVO —
  `.atl/sdd/C10/*.md` (tasks/spec/proposal/design/explore + qa-plan/
  qa-report/verify-report/state cuando existan) +
  `docs/08_ACCESO_REMOTO.md` + `docs/07_SLICING_PLAN.md`; **NO** commitear
  `.atl/sdd/C10/evidencia/` (PNGs — sha256 solo en qa-report),
  **NO** `scripts/__pycache__/`, sin `.gitignore` nuevo, sin push, sin
  secretos (S16); commits de producto/qa previos por bloque según pauta
  transversal (cada uno con `Co-Authored-By: Claude Code
  <noreply@anthropic.com>`).
  HECHO-CUANDO: `git status` limpio (sin restos de evidencia staged, sin
  `__pycache__` staged) y sin push ejecutado.

---

## Cobertura (mapa tarea → REQ/decisión)

| Tarea(s) | Cubre |
|---|---|
| T10.1–T10.2 | RESUMEN-2 (NON_NULL), G4-a, R2/R3 (ctor 8-arg) |
| T10.3–T10.5 | DEL-1/DEL-5, LOGDEL-1, PURGA-1/5 (pines D-9, mono-sentencia) |
| T10.6 | RESUMEN-3/4 (SQL D-C10-3, formatter C2, estado vacío) |
| T10.7–T10.8 | DEL-1..4, PURGA-1..5 (incl. N=0/M4), RESUMEN-6, LOGDEL-1..3 (M10/D-C10-1) |
| T10.9–T10.10 | QA-1 completa (5 escenarios M10, pin 204/200-deleted, 401 cadena 2) |
| T10.11 | CORS-1..4 (G1-a latente, G1-C2 re-pin) |
| T10.12–T10.14 | UI-8 (tipos/retornos, QA-2 parser) |
| T10.15–T10.18 | UI-1..7, UX401-2 (G2-a/M1, post-estados flujo 8, maquetas 04/05) |
| T10.20–T10.21 | WS-1..3 (G3-a/M9, D-C10-7) |
| T10.22 | E913-1/2 (D-C10-10) |
| T10.23 | E92-1/2 (D-C10-11) |
| T10.24 | E910-1 (D-C10-12) |
| T10.25 | QA-2 (gate de fase: 55 previos + sin regresión del ciclo WS) |
| T10.26 | DOC-1..4 (D-C10-14, D1) |
| T10.27–T10.28 | DEL-5/PURGA-2 (SQL real) + QA-3 (humo canónico) |
| T10.29–T10.33 | QA-4/QA-5 (G5-a, D-C10-13; re-runs + E10.x; destructivos al final) |
| T10.34 | QA-3 (pautas E3.22/pre-fase/Q23, smoke C8 24 marcas) |
| T10.35–T10.36 | done-when del 06/07 trazado + higiene de cierre (M6, sin push) |

Total: **36 tareas** en 6 bloques; 37 ficheros de la tabla §4 del design
(+ ficha 07); done-when del corte — 3 DELETE con doble confirmación
(UI+cabecera) con estados del AppFlow flujo 8 (→ T10.7–T10.10 +
T10.15–T10.18 + checks E10.x), resumen en lista/ficha (→ T10.6–T10.9 +
T10.16–T10.17), doc de acceso remoto en /docs (→ T10.26) — y los 4 fixes
H-C9 de primera clase con re-runs honestos (→ Bloque 4 + T10.32).

skill_resolution: injected