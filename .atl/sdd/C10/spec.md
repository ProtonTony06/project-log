---
project: Project_Log
artifact: spec
last_updated: 2026-10-29
status: complete
change: C10 — Fase 2 (borrados con doble confirmación, resumen, doc acceso remoto, fixes ratificados de C9)
base_head: 27b62cb
insights: .atl/sdd/C10/proposal.md · .atl/sdd/C10/explore.md (A.1–A.6, B.1–B.6, C.1–C.3, D, gaps 1–10)
contrato: docs/05 §3.2/§3.3/§4 · docs/03 flujos 8/9 + §4/§5 · docs/04 V10/V11 + mockups 04/05 · docs/02 §2.3/§2.5/§6.8 · docs/06 (Fase 2 done-when) · docs/07 §C10
---

# C10 · Spec — borrados definitivos, resumen por proyecto, acceso remoto y fixes de C9

Spec de comportamiento. Los literales de API/UI se **CITAN tal cual** del contrato (doc 05/03/04/02); este spec no redefine ninguno. Escenarios = estado + entrada + salida esperada con literales; cada REQ es verificable con un test automatizado (MvcTest / pin SQL / Vitest / check del arnés visual C10).

## §0. Decisiones GATE ancladas (ratificadas por el orquestador — NO redecibles en spec/design/apply)

| GATE | Decisión | Consecuencia en este spec |
|---|---|---|
| G1=(a) latente | CORS multi-origen: `FRONTEND_ORIGIN` acepta lista separada por coma; `CorsConfig` divide y llena `setAllowedOrigins`. **ADOPTADA LATENTE**: el código entra en este corte, pero NO se toca la VM 108 ni su `.env` — la activación del segundo origin (`192.168.1.58:8080`) es un paso de deploy futuro PENDIENTE DE RATIFICACIÓN del usuario. `*` y `setAllowedOriginPatterns` SIGUEN prohibidos (G1-C2 vigente). | Área 5. El doc de acceso remoto (Área 12) documenta la variante (a+) como paso pendiente, no como hecho. |
| G2=(a) | `X-Confirm-Name` desde la UI viaja con el **texto tecleado** del modal (no `dto.name`). Cierra el matiz M1. | Áreas 1/3/10. |
| G3=(a) | Timeout CONNECTING **T = 10 s** en `abrir()` del WS; close manual ⇒ cuenta como cierre con `intento+1`. La vía `close-sin-open`/`alCierreInaugural` (`me()`) NO incrementa el contador — distinción explícita requerida (M9). | Área 6. |
| G4=(a) | Campo `summary` con `@JsonInclude(NON_NULL)` sobre el componente del record `ProjectDto` + **endpoint dedicado** `GET /projects/{id}/summary`, ambos con el MISMO `SummaryDto` (§3.3). | Área 4. |
| G5=(a) | Arnés visual: `scripts/qa_vis_c10.py` NUEVO reutilizando la maquinaria de `qa_vis_c9.py` (import limpio o copia controlada constatada en el head del script); los 26 checks C9 NO se duplican; los 4 re-run honestos se invocan desde C10. `qa_vis_c9.py` queda bit a bit como el canon de C9. | Área 13. |
| G6 | Ratificar DEV-C8-4 (C8): el interceptor 401 (§5 de AppFlow / `client.ts` R8.14) **NO se toca**. La raíz del 401-falso desaparece al implementar los DELETE. | Área 11. |

**Decisión D1 (doc operativo):** `docs/08_ACCESO_REMOTO.md` NUEVO, siguiendo numeración y estilo de cabecera de 01–07. Se decide **NO alterar `docs/README.md`** (índice congelado del paquete de definición — no operatoria): el done-when del 06 dice «el doc de acceso remoto está listo **en /docs** o README operativo», disyuntiva que /docs satisface sin ensuciar el índice. Justificación recogida aquí para no re-decidirse en design.

### Matices del estado real (contrato vs código hoy — anclados con número, se citan en las áreas)

- **M1.** `ProyectoDetalle.ejecutarConfirmado()` envía hoy `purgarLogs(dto.id, dto.name)` y `eliminarProyecto(dto.id, dto.name)` (frontend/src/pages/ProyectoDetalle.tsx:99/104): el header lleva el nombre REAL de BD, con lo que `confirm_mismatch` sería **inalcanzable desde la UI** y el guard del modal (V10) decorativo. Lo repara G2-a (Área 10, REQ-C10-UI-1).
- **M2.** Errata ratificada por el proposal: el enunciado de Fase 2 decía «200/409/422» para borrado; el CONTRATO manda **204** (proyecto y log), **200 `{deleted: N}`** (purga) y **400** `confirm_required`/`confirm_mismatch`. 409 solo existe en `name_duplicate` (POST/PATCH) y `project_archived` (ingesta); 422 NO existe en el Schema. Los estados de AppFlow flujo 8 mandan.
- **M3.** El prompt de elaboración mencionaba `GET /projects/{id}?include=summary`; el contrato §3.2 asigna `include=summary?` **SOLO a la lista** (`GET /projects`) y la ficha consume el **endpoint dedicado** `GET /projects/{id}/summary` (explore B.6, proposal §4.2). El mecanismo NON_NULL de G4-a garantiza `summary` ausente en `GET /projects/{id}` sin include y en toda respuesta salvo la lista pedida con include.
- **M4.** Purga sobre proyecto sin logs: el Schema no enumera un estado de error para `N=0` (solo `200 {deleted: N} · 400 · 401 · 404`); «nada se borra» ≠ error y el retorno de filas de `DELETE FROM logs WHERE project_id = ?` es 0. Se ratifica: **`200 {"deleted": 0}`**, no error. El estado vacío post-purga sí es literal: flujo 8c («la próxima consulta de logs del proyecto arranca vacía») y flujo 9 paso 4 (`total: 0`, `last_log_at: null`, «sin logs todavía»).
- **M5.** Archivado NO bloquea los DELETE: §3.1 limita el 403 «(`project_archived` solo en ingesta)», §4 regla 12 («Proyecto ARCHIVED → 403 … para toda **ingesta**; sus logs siguen consultables»), AppFlow §5 («el panel no lo recibe nunca») y las filas §3.2 de los DELETE listan `204 · 400 · 401 · 404` SIN 403. Regla 3 (§4): «Archivar **nunca** borra nada: solo cambia `status`». Los DELETE sobre un proyecto ARCHIVED responden como cualquier otro.
- **M6.** `qa_vis_c9.py` tiene `DIR_EVID` hardcodeada a `.atl/sdd/C9/evidencia/` y guard de 26 checks — C10 NO lo parchea (canon intacto); `qa_vis_c10.py` redirige evidencia a `.atl/sdd/C10/evidencia/` (NO commitear; sha256 en qa-report).
- **M7.** DEV-C8-4 residual: al implementar los DELETE desaparece el 401-falso para ellos, pero el denyAll-en-/error queda para cualquier otra ruta/método no mapeado — NO es alcance (ratificado C8).
- **M8.** El arnés C9 no parchea producto (dif-0, regla D-C9-1/R9.6.4): los fixes de producto de los 4 hallazgos (F6–F9) son de primera clase en C10 y sus checks re-run honestos una vez parcheados.
- **M9.** Semántica del intento en el timeout CONNECTING (gap 5 de explore): el close generado POR el timeout en un socket de la cadena (intento N≥1) DEBE producir `intento+1`; el close de un socket INAUGURAL (primera conexión de la sesión, que enruta a `alCierreInaugural()` → `me()`) NO incrementa `intento`. La distinción concreta (flag «abriendo por intento N>1» vs «close-durante-handshake») la fija design; el comportamiento observable exigido es el de Área 6.
- **M10.** Orden de validación fail-fast 400-vs-404 con recurso inexistente (proposal §4.1 lo deja a design): este spec exige solo estados conformes al contrato en los escenarios dados; la secuencia exacta con recurso ya inexistente Y cabecera incorrecta simultáneos se fija en design y queda cubierta por MvcTest.

---

## Área 1 — `DELETE /api/v1/panel/projects/{id}` (8a)

Literal §3.2: «Eliminar proyecto **y sus logs** (CASCADE) — P1 · header `X-Confirm-Name: <name>` → **204 · 400 · 401 · 404**».
Literal §3.3: «`DELETE /projects/{id}` con `X-Confirm-Name: <name>` (nombre exacto del proyecto) → 204; borra proyecto **y logs** por CASCADE en la misma transacción.» · «Cabecera ausente → 400 `{"code": "confirm_required"}`; valor incorrecto → 400 `{"code": "confirm_mismatch"}`; nada se borra. 404 si ya no existía.»
Literal §4 regla 3: «Borrado definitivo del proyecto purga sus logs en la misma transacción; sin huérfanos ni pasos intermedios».

### REQ-C10-DEL-1 — Borrado feliz 204 sin cuerpo
- GIVEN sesión JWT válida y proyecto `id=3` named `payments-service` con N logs
- WHEN `DELETE /api/v1/panel/projects/3` con cabecera `X-Confirm-Name: payments-service` (comparación EXACTA en el servidor; el trim es solo del INPUT de la UI — la cabecera viaja tal cual)
- THEN respuesta **204 sin cuerpo**
- AND `GET /projects` ya no lista el proyecto y `GET /projects/3` → 404 `not_found`

### REQ-C10-DEL-2 — 400 `confirm_required` (cabecera ausente)
- GIVEN sesión válida y proyecto existente
- WHEN `DELETE /api/v1/panel/projects/3` SIN `X-Confirm-Name`
- THEN **400** con cuerpo `{"code": "confirm_required", ...}` (forma §3.1 `{code, message, field?}`)
- AND nada se borra (el proyecto sigue en `GET /projects/3` → 200)

### REQ-C10-DEL-3 — 400 `confirm_mismatch` (valor desigual)
- GIVEN proyecto existente named `payments-service`
- WHEN `DELETE` con `X-Confirm-Name: payments-servic` (o cualquier valor ≠ name EXACTO; también diferencia por mayúsculas/espacios interiores)
- THEN **400** `{"code": "confirm_mismatch", ...}` y nada se borra

### REQ-C10-DEL-4 — 404 inexistente
- GIVEN sesión válida y `id=999999` que ya no existe («borrada en otra pestaña o sesión», AppFlow §5 fila 404)
- WHEN `DELETE /api/v1/panel/projects/999999` (con o sin cabecera, según M10)
- THEN **404** `{"code": "not_found", ...}` con el mensaje del vocabulario C3 («No existe (quizás fue eliminado)»)

### REQ-C10-DEL-5 — CASCADE transaccional y muerte de la API key
- GIVEN proyecto con logs y API key activa (`api_key_hash`, `api_key_prefix`, `key_created_at` DENTRO de la fila `projects` — explore A.2)
- WHEN `DELETE` con confirmación correcta
- THEN UNA sentencia `DELETE FROM projects WHERE id = ?` y PostgreSQL purga los logs por `ON DELETE CASCADE` de `logs_project_fk` en la MISMA transacción (V1__init.sql literal explore A.2)
- AND 0 logs huérfanos: `GET /logs?project_id=3` → lista vacía
- AND la key muere con la fila: ingesta con esa key → **401 `api_key_invalid`** (§4 regla 2/columnas de `projects`; nada que purgar aparte)

### REQ-C10-DEL-6 — Archivado NO bloquea (M5)
- GIVEN proyecto `status=ARCHIVED`
- WHEN `DELETE` con confirmación correcta
- THEN **204** igual que un ACTIVE (el 403 `project_archived` es exclusivo de la API de ingesta — literales M5)

### REQ-C10-DEL-7 — 401 de la cadena sin tocar SecurityConfig
- GIVEN sin token o token inválido
- WHEN `DELETE /api/v1/panel/projects/3`
- THEN **401** `invalid_token` por la cadena 2 (`/api/v1/panel/**` + `anyRequest().authenticated()`), que YA cubre las 3 DELETE nuevas sin añadir antMatcher (explore A.5)

---

## Área 2 — `DELETE /api/v1/panel/logs/{id}` (8b)

Literal §3.2: «Eliminar un registro — P1 · header `X-Confirm-Log-Id: <id>` → **204 · 400 · 401 · 404**».
Literal §3.3: «`DELETE /logs/{id}` con `X-Confirm-Log-Id: <id>` (el id del registro) → 204.»

### REQ-C10-LOGDEL-1 — Borrado feliz 204
- GIVEN log `id=48211` existente
- WHEN `DELETE /api/v1/panel/logs/48211` con `X-Confirm-Log-Id: 48211`
- THEN **204** sin cuerpo; el log desaparece del listado en el próximo refetch y `GET /logs/48211` → 404

### REQ-C10-LOGDEL-2 — 400 confirm_required / confirm_mismatch
- GIVEN log existente
- WHEN `DELETE /api/v1/panel/logs/48211` sin cabecera → THEN **400** `{"code": "confirm_required", ...}`
- WHEN con `X-Confirm-Log-Id: 48212` (id desigual) → THEN **400** `{"code": "confirm_mismatch", ...}`
- AND en ambos, nada se borra

### REQ-C10-LOGDEL-3 — 404 inexistente
- GIVEN log `id=999999` inexistente
- WHEN `DELETE` con `X-Confirm-Log-Id: 999999`
- THEN **404** `not_found`

### REQ-C10-LOGDEL-4 — el borrado de un log NO toca el proyecto
- GIVEN log del proyecto 3 (ACTIVE, con key)
- WHEN `DELETE /logs/{id}` correcto
- THEN 204; el proyecto 3 sobrevive intacto y su key sigue aceptando ingesta (la única vía de borrar un proyecto es REQ-C10-DEL-1)

---

## Área 3 — `DELETE /api/v1/panel/projects/{id}/logs` (Purgar, 8c)

Literal §3.2: «Purgar todos los logs del proyecto (el proyecto sobrevive) — P1 · header `X-Confirm-Name: <name>` → **200 `{deleted: N}` · 400 · 401 · 404**».
Literal §3.3: «`DELETE /projects/{id}/logs` con `X-Confirm-Name: <name>` → 200 `{deleted: N}`; **conserva el proyecto y su API key**, que sigue aceptando ingesta.»

### REQ-C10-PURGA-1 — Purga feliz 200 `{deleted: N}` (literal EXACTO de forma)
- GIVEN proyecto 3 named `payments-service` con 55 logs
- WHEN `DELETE /api/v1/panel/projects/3/logs` con `X-Confirm-Name: payments-service`
- THEN **200** con cuerpo `{"deleted": 55}` — `N` ES el retorno de filas del estamento `DELETE FROM logs WHERE project_id = ?` (primer uso del retorno de `JdbcTemplate.update`; explore A.2/gap 6)

### REQ-C10-PURGA-2 — el proyecto y su key sobreviven
- GIVEN purga 200 feliz
- THEN `GET /projects/3` → 200 con el `ProjectDto` intacto (mismo `name`, `status`, `api_key_prefix`, `has_key: true`)
- AND ingesta posterior con esa key → **202 `{accepted: N}`** (la key «sigue aceptando ingesta», literal)
- AND `GET /projects/3/summary` → `{"by_level": {}, "by_environment": {}, "total": 0, "last_log_at": null}` (flujo 8c: «el resumen pasa a recuento cero»)

### REQ-C10-PURGA-3 — 400 confirm_required / confirm_mismatch con `X-Confirm-Name`
- WHEN purga sin cabecera → **400** `{"code": "confirm_required", ...}`)
- WHEN purga con nombre desigual al EXACTO → **400** `{"code": "confirm_mismatch", ...}`)
- AND en ambos, nada se borra (los 55 logs siguen en el listado)

### REQ-C10-PURGA-4 — 404 proyecto inexistente
- WHEN `DELETE /api/v1/panel/projects/999999/logs`
- THEN **404** `not_found` (nunca 200 con `deleted: 0` sobre proyecto inexistente)

### REQ-C10-PURGA-5 — N=0 → 200 (M4)
- GIVEN proyecto existente sin logs
- WHEN purga con confirmación correcta
- THEN **200** `{"deleted": 0}` (estado vacío informativo, no error — derivación de M4)

---

## Área 4 — Resumen por proyecto (flujo 9)

Literales §3.2: «GET `/api/v1/panel/projects` — query `status?`, `include=summary?` → 200 `[ProjectDto]` · 401» y «GET `/api/v1/panel/projects/{id}/summary` — Resumen por proyecto — P1 · → 200 `{by_level, by_environment, total, last_log_at}` · 401 · 404».
Literal §3.3 SummaryDto: `{"by_level": {"ERROR": 12, "WARN": 3}, "by_environment": {"production": 40}, "total": 55, "last_log_at": "2026-10-16T22:31:04Z"}` — «mapas con recuento por cada nivel/entorno presente; `total` sumar de logs, `last_log_at` = máximo `received_at` (`null` si no hay logs; AppFlow flujo 9). Proyecto sin logs → total 0 y `last_log_at: null` (estado vacío informativo, no error).»
Literal §3.3 ProjectDto: «El campo `summary` está **ausente** del DTO salvo en `GET /projects` con `include=summary` (donde siempre viaja, nunca vacío por defecto: con un proyecto sin logs sus mapas quedan vacíos y `total: 0`, `last_log_at: null`).»
Literal flujo 9 paso 3: «Los archivados siguen mostrando el mismo resumen (su histórico es consultable).»

### REQ-C10-RESUMEN-1 — `include=summary` en la lista
- GIVEN sesión válida, ≥2 proyectos (uno con logs, otro sin)
- WHEN `GET /api/v1/panel/projects?include=summary`
- THEN 200 `[ProjectDto]` donde **cada** elemento lleva `summary` presente (nunca vacío por defecto: el sin logs lleva `{"by_level": {}, "by_environment": {}, "total": 0, "last_log_at": null}`)

### REQ-C10-RESUMEN-2 — `summary` AUSENTE sin include (G4-a / M3)
- WHEN `GET /api/v1/panel/projects` (sin include) o `GET /api/v1/panel/projects/3`
- THEN 200 con `ProjectDto` SIN la clave `summary` (ausencia, no `null` — verificado por test de serialización)
- AND el resto de endpoints que sirven `ProjectDto` (POST 201, PATCH 200) también omiten `summary`

### REQ-C10-RESUMEN-3 — Endpoint dedicado de ficha
- WHEN `GET /api/v1/panel/projects/3/summary`
- THEN 200 con el `SummaryDto` EXACTO de §3.3 (4 claves literales: `by_level`, `by_environment`, `total`, `last_log_at`)
- WHEN proyecto 999999 inexistente → THEN **404** `not_found`
- WHEN sin token → THEN **401** (cadena 2)

### REQ-C10-RESUMEN-4 — Semántica de los campos
- GIVEN proyecto con logs de varios levels/environments
- THEN `by_level`/`by_environment` contienen SOLO los niveles/entornos PRESENTES (mapas con recuento por cada uno presente; sin claves a 0)
- AND `total` = suma de los logs del proyecto (independiente de filtros y de paginación)
- AND `last_log_at` = máximo `received_at` formateado con el formatter compartido de C2 (`LoginController.EXPIRES_AT_FORMATTER`, patrón del RowMapper de `ProjectsRepository`) en ISO-8601 UTC con sufijo `Z` (§3.1: «Todas las fechas en JSON van en ISO-8601 UTC con sufijo `Z`»)

### REQ-C10-RESUMEN-5 — Archivados cuentan (flujo 9 paso 3)
- GIVEN proyecto ARCHIVED con histórico
- THEN su `summary` refleja el mismo agregado que un ACTIVE (el archivado «afecta al flujo, no a la consulta», §4 regla 12)

### REQ-C10-RESUMEN-6 — `include` fail-fast
- WHEN `GET /api/v1/panel/projects?include=otracosa`
- THEN **400** sin tocar BD (disciplina C7): code `filter_invalid_value` recomendado (política exacta la fija design); único valor válido: `summary`

### REQ-C10-RESUMEN-7 — No recalcula por WebSocket (flujo 9 paso 5)
- THEN el resumen «se refresca al recargar o con **Actualizar**» — LiveBroadcaster NO emite frames por borrado ni el resumen se actualiza en vivo (explore gap 10: sin acoplamiento WS↔DELETE)

---

## Área 5 — CORS multi-origen latente (G1-a)

Estado exacto hoy (explore A.4): `config.setAllowedOrigins(List.of(frontendOrigin));` con «Nunca `"*"`, nunca `setAllowedOriginPatterns` (G1): reflejo EXACTO del origen.» Cierre CORS por defecto si la env está vacía (S26/E2.18).

### REQ-C10-CORS-1 — Lista por coma de la MISMA env var
- WHEN `FRONTEND_ORIGIN=http://localhost:8080,http://192.168.1.58:8080`
- THEN `CorsConfig` divide la env en la lista de `setAllowedOrigins` (ambas origins reflejadas EXACTAS; sin trim raro ni re-declarar la semántica de origin)

### REQ-C10-CORS-2 — Sin segunda origin no cambia nada
- WHEN `FRONTEND_ORIGIN=http://localhost:8080` (estado actual de la VM — LATENTE)
- THEN comportamiento idéntico al de C9 (una sola origin; `CorsConfigTest` verde con el mismo literal)

### REQ-C10-CORS-3 — Prohibiciones G1-C2 vigentes
- THEN NUNCA `*` como origin, NUNCA `setAllowedOriginPatterns`, NUNCA una env var nueva (misma `FRONTEND_ORIGIN` con semántica ampliada); `setAllowedMethods` (ya incluye DELETE) y `setAllowedHeaders` (`Authorization`, `Content-Type`, `X-Confirm-*`) sin cambios

### REQ-C10-CORS-4 — Origin no listada → rechazo
- GIVEN lista solo con `localhost`
- WHEN petición con `Origin: http://otra-cosa:8080`
- THEN sin cabeceras CORS (403 de Spring ante Origin no permitida), incluida la upgrade de `/ws/logs`

### REQ-C10-CORS-5 — VM intocable (G1 latente)
- THEN este corte NO modifica la VM 108 ni su `.env`; la adición de `http://192.168.1.58:8080` al `.env` queda documentada como paso de deploy futuro pendiente de ratificación (a+) — ver también Área 12

---

## Área 6 — Fix H-C9-WS-COLGADO: timeout CONNECTING (G3-a)

Hallazgo (ficha `c9-hallazgo-ws-reconexion-colgada.md`): «toda cadena de reconexión necesita UN timeout para la fase CONNECTING»; la cadena backoff es self-sustaining POR CIERRES (`alCierre` ⇒ `intento+1` ⇒ timer ⇒ `abrir`); con el socket colgado en CONNECTING «jamás recibe su onclose (no hay timeout de handshake en el cliente) ⇒ sin close NO hay intento 3» y la restauración del backend queda sin efecto (E9.11/E9.26 FAIL honestos). Contrato violado: R9.2.11-b («Reconectando (intento N)… con N creciente», backoff 1 s → 30 s) y R8.30/R8.31 (reconexión close-driven).

### REQ-C10-WS-1 — Timer de CONNECTING T=10 s en `abrir()`
- GIVEN socket creado cuyo handshake no completa
- WHEN transcurren **10 s** sin `onopen` (Reloj ya inyectado en `ClienteWsLogs`)
- THEN el cliente close() manual el socket ⇒ `onclose` dispara ⇒ la cadena `alCierre()` retoma el backoff normal ⇒ **recuperación automática post-restauración** del backend sin intervención (E9.26: banner retirado, `set_filters` reenviado, refetch página 1)
- AND al abrir (`onopen`) el timer se cancela ANTES de `enviarFiltros()` (sin cierres espurios sobre sockets sanos)

### REQ-C10-WS-2 — Semántica del intento (M9)
- GIVEN intento N≥1 de la cadena que muere por el timeout
- THEN ese cierre cuenta como cierre normal: `intento+1` ⇒ «Reconectando (intento N+1)…» — **N creciente** en el banner (R9.2.11-b)
- AND la vía close-sin-open/`alCierreInaugural` (`me()`, primer intento inaugural de la sesión) NO incrementa `intento` — se distingue explícitamente (flag de «abriendo por intento N>1» o close-durante-handshake según design) para que el contador no crezca por la vía inaugural

### REQ-C10-WS-3 — Sin cambios de contrato WS
- THEN el resto de la maquinaria intacta: dedup por id (D-C8), backoff 1 s → 30 s con Jitter del canon C9, primer-frame-JWT (§2.8), `set_filters` S8-8 — el fix es exclusivo del ciclo de conexión del cliente

**Test base:** `ws/client.test.ts` ya tiene el ciclo stubbed (WebSocket con `readyState` controlable + Reloj fake) para simular CONNECTING eterno y asertar `alEstado("reconectando", n)` (explore B.2).

---

## Área 7 — Fix H-C9-E9.13: doble «?» en la query del detalle

Hallazgo (ficha `c9-hallazgo-e913-detalle-query-doble.md`): `location.search` YA trae su «?» — interpolar otro genera `/logs/388??text=…`; el consumo del snapshot guarda igualdad EXACTA de la search ⇒ MISMATCH ⇒ se pierde el acumulado keyset (contrato E8.35/D-C8-8).

### REQ-C10-E913-1 — URL sin doble «?»
- THEN en `Logs.tsx` (`alVerDetalle`) y en los **DOS** botones «Volver a logs» de `LogDetalle.tsx`, la query viaja tal cual (`location.search` sin «?» extra): `/logs/388?text=qa-c9-…`, nunca `??text=`
- AND el store `storeListado` NO se toca (compara la search completa, que ahora viaja correcta)

### REQ-C10-E913-2 — Round-trip E8.35 intacto
- GIVEN listado con filtros + acumulado de «Cargar más» (keyset)
- WHEN entrar al detalle y volver
- THEN query, nº filas y 1ª fila intactas y **CERO refetch** (el acumulado se consume; invariante E9.13 con `NEEDLE_E35`)

---

## Área 8 — Fix H-C9-E9.2: destino post-login se pierde

Hallazgo (ficha `c9-hallazgo-e92-destino-perdido-post-login.md`): el render condicional `if (sesion.estado === "autenticado") return <Navigate to="/" replace />` de `Login.tsx` (líneas 31–33) dispara una SEGUNDA navegación que pisa `navegar(destino ?? "/logs")` de `enviar()` — traza instrumentada: `rep '/proyectos/99999999' @200ms ← navegar(destino); rep '/' @201ms ← Navigate de Login (gana); rep '/logs' @203ms ← index redirect`. Contrato violado: R8.5/E8.3 (destino con query intacta) y R9.2.2 («login → regresa a /proyectos/:id — destino cumplido»).

### REQ-C10-E92-1 — UNA sola navegación post-login
- THEN `Login.tsx` deja de navegar doble: se elimina el `Navigate` condicional (el envío ya navega) o se memoiza/condiciona para que no pise `navegar(destino)` — la vía exacta la fija design (proposal F8), el comportamiento exigido es único: **el arribo POST-login PERDURANDO (poll ~700 ms, learned 1 de la ficha) es el destino guardado**

### REQ-C10-E92-2 — Destino sobrevive y sin-destino cae en /logs
- GIVEN guard de sesión con vista destino guardada (incluida con query: `/proyectos/99999999`)
- WHEN login exitoso → THEN llegada al destino guardado (R8.5/E8.3/R9.2.2), no a `/`
- WHEN login directo SIN destino pendiente → THEN llegada a `/logs` (vía index redirect de App.tsx, sin doble salto observable)
- AND el `borrarDestino()` dentro de `sesion.login()` no rompe la variable local `destino` ya leída (explore B.4)

---

## Área 9 — Fix H-C9-E9.10: UN set_filters por cambio

Hallazgo (ficha `c9-hallazgo-e910-set-filters-por-render.md`): `Logs.tsx` interpola `filtros: toSetFilters(filtros)` en línea (objeto NUEVO por render) y `useLogsStream` tiene `useEffect(…, [p.filtros])` ⇒ identity-change dispara `aplicarFiltros` en CADA render ⇒ delta 3 frames idénticos por evento (contradice R9.2.10 «UN set_filters» y QP-8.22/E8.22).

### REQ-C10-E910-1 — UN solo frame por cambio de filtro
- THEN `toSetFilters(filtros)` se memoiza (`useMemo`, ya importado en `Logs.tsx`) o la dependencia pasa a la search string — UN frame `set_filters` por cambio real de filtro (R9.2.10)
- WHEN UN cambio de filtro o Reiniciar → THEN delta de frames `set_filters` = **1** (WsContador del arnés), delta de handshakes = 0, 1 socket vivo
- AND renders ajenos (nuevos batches en tabla, banners, toggles) → delta **0**
- AND el refetch de página 1 ante cambio de filtros (efecto `[..., filtros]` de Logs.tsx) NO queda afectado (mismo objeto memoizado; identidad estable vía `useFiltrosLogs` `useMemo(…, [searchParams])`)

---

## Área 10 — UI de borrado y purga (consume los status reales)

Contrato visual: V10 (04_UIUX §8): «Confirmación de borrado por proyecto: escribir el nombre exacto (mapea 1:1 con `X-Confirm-Name`)»; V11: «Confirmación de borrado registro a registro: **escribir la sentencia literal completa** (`DELETE /logs/48211 · header X-Confirm-Log-Id: 48211`) en un input, comparación literal con trim, botón rojo deshabilitado hasta coincidir»; estados de modales (AppFlow §4): «400 "La confirmación no coincide" dentro del modal, sin cerrarlo (reintento directo); 404 → banner en la pantalla de origen». Literales de flujo 8 en §3.4 del proposal (transcritos de docs/03).

### REQ-C10-UI-1 — Cableado real de la confirmación (G2-a, cierra M1)
- THEN el header de 8a/8c viaja con el **TEXTO TECLEADO** validado del modal (`coincideLiteralConTrim`), NO con `dto.name`: `X-Confirm-Name: {texto}` — el doble confirm deja de ser decorativo y `confirm_mismatch` es alcanzable desde la UI tecleando mal
- AND 8b (`ModalBorrado8b`) sigue mandando el id correcto (`X-Confirm-Log-Id: {id}` — ya existe desde C8)

### REQ-C10-UI-2 — Modal NO cierra ni desloguea en 400 (G6)
- GIVEN modal 8a/8b/8c abierto con texto tecleado
- WHEN la API responde **400** `confirm_required`/`confirm_mismatch`
- THEN el error literal «La confirmación no coincide» aparece DENTRO del modal, el modal permanece abierto, reintentable, y **NO hay logout** (el interceptor no entra: el 400 no es 401)
- AND botón deshabilitado `bg-red-300` hasta validar el texto (04_UIUX §4); botón «Eliminando…» durante la petición

### REQ-C10-UI-3 — Post-8a: 204 → /proyectos con banner
- WHEN 204 de `eliminarProyecto` (`navegar` desde ficha)
- THEN redirige a `/proyectos` con banner **«Proyecto y sus logs eliminados definitivamente»**
- WHEN 404 → mensaje §5 («No existe (quizás fue eliminado)») y retorno a la lista

### REQ-C10-UI-4 — Post-8c: 200 → banner con N real y ficha refrescada
- WHEN 200 `{"deleted": N}` de `purgarLogs`
- THEN banner **«N logs eliminados; el proyecto y su key siguen intactos»** con el N REAL del cuerpo (no estimado) y la ficha refrescada: bloque resumen a cero («sin logs todavía», flujo 9 paso 4)
- WHEN 404 → §5 en la ficha

### REQ-C10-UI-5 — Post-8b: 204 → «Registro eliminado» y regreso a /logs
- WHEN 204 de `eliminarLog` (hoy `Promise<never>` nunca resuelve — explore B.6)
- THEN confirmación «Registro eliminado» y regreso a `/logs` (conservando la query si el detalle la trajo — coherencia con Área 7); 404 → §5 de detalle

### REQ-C10-UI-6 — Tarjeta de resumen en `/proyectos` (maqueta 04)
- THEN `/proyectos` pide `GET /projects?include=summary`; cada `TarjetaProyecto` muestra (texto plano dentro del `<button>` íntegro — explore B.6/nota de estructura): badges de severidad (`lv-*`, FATAL primero: «FATAL 1 · ERROR 148 · WARN 63 · DEBUG 1 000 · INFO 24 118») con WARN/ERROR/FATAL destacados, badges de entorno (`env-*`: «production 23 988 · staging 1 342 · development 0»), franja inferior tras `border-t` con totals («**25 330** logs») y «último: 2026-10-19T21:47:58Z»; paleta de badges = 04_UIUX §2 (reutilizada, no nueva)
- AND archivado con `opacity-80` y **resumen INTACTO** (maqueta 04); proyecto sin logs → estado vacío informativo «sin logs todavía», no error
- AND cabecera con botón «Actualizar» refresca los resúmenes (flujo 9 paso 5)

### REQ-C10-UI-7 — Bloque resumen + zona destructiva en ficha (maqueta 05)
- THEN ficha `/proyectos/:id` añade sección «Resumen de logs» vía `GET /projects/{id}/summary` (M3: el include de §3.2 solo existe en la lista) dentro del `cargar()` existente: total grande («25 330» + «logs totales · último <mono>…»), mismas bandas de badges y entornos
- AND zona destructiva P1 existente con sentencias mono pintadas (`DELETE /projects/3/logs · X-Confirm-Name: payments-service` / `DELETE /projects/3 · …`) opera con los status reales (REQ-C10-UI-3/4)

### REQ-C10-UI-8 — Tipos del cliente (F1)
- THEN `panel.ts`: retornos `Promise<void>` para 8a/8b (204 sin cuerpo) y `Promise<{deleted: number}>` para 8c; `api/types.ts` declara `SummaryDto` y el campo opcional `summary` (tierra nueva — explore B.6). El interceptor y el resto de `client.ts` sin cambios (B.1: «no se detectó ningún toque adicional requerido»)

---

## Área 11 — UX-401-logout: ratificación G6 sin cambio de interceptor

### REQ-C10-UX401-1 — Interceptor intacto
- THEN `client.ts` (regla 401 SOLO por status, R8.14) y AppFlow §5 SIN TOCAR: la raíz del 401-falso (DELETE no mapeado → /error → cadena-3 denyAll → 401 `invalid_token` → deslogueo) desaparece al implementar los endpoints (explore A.5). El residual denyAll-en-/error queda para rutas inexistentes ajenas (M7) — NO es alcance.

### REQ-C10-UX401-2 — 400 real NO desloguea
- GIVEN sesión válida y confirmación rechazada por la API REAL
- WHEN 400 `confirm_mismatch`
- THEN NO hay deslogueo, NO hay redirección a `/login`, NO se descarta el token: error dentro del modal (REQ-C10-UI-2) — check explícito del arnés

### REQ-C10-UX401-3 — 401 REAL sí desloguea (comportamiento existente correcto)
- GIVEN token inválido/expirado (TTL 8 h)
- WHEN cualquier DELETE o REST
- THEN interceptor desloguea: descarta sesión, redirige a `/login` con «Tu sesión ha expirado (8 h): inicia sesión de nuevo» + vista destino guardada (fila 401 de AppFlow §5)

---

## Área 12 — Doc operativo de acceso remoto (`docs/08_ACCESO_REMOTO.md`)

Literal TRD §6.8: «**Acceso remoto Tailscale** (P1 del PRD): instalar Tailscale en la VM y acceder al mismo puerto del frontend por la IP de la tailnet; cero cambios en la Compose.» · «**Proxy inverso con HTTPS** (P2): si hay dominio futuro, se añade el proxy con Let's Encrypt delante (o se configura TLS en el propio nginx) — la estructura actual (1 proxy + servicios internos + WS detrás) ya es la del diseño inverso; nada del MVP se rompe.» Literal TRD §2.3: `logs.project_id … ON DELETE CASCADE` (insumo del done-when).

### REQ-C10-DOC-1 — Operativa Tailscale P1 documentada
- THEN el doc cubre: instalación de Tailscale en la VM «projectlog» (Debian 12.15, eth0 192.168.1.58/24 — estado FB-0 de C9), IP de tailnet (ruta SSH observada `root@100.64.3.17` vía ProxyJump), acceso al panel al **mismo puerto 8080** por la IP de tailnet con **cero cambios en la Compose** (literal §6.8), SSH de gestión a la VM, y el contexto de firewall nftables GATE #11 (solo SSH-LAN + 8080; regla 8080 por tailnet SOLO si el usuario la pide)

### REQ-C10-DOC-2 — TLS P2 documentado SIN implementar
- THEN sección TLS-P2 con la estructura ya prevista (1 proxy nginx + servicios internos + `/ws/logs` detrás con upgrade y timeouts 3600 s; Let's Encrypt delante o TLS en el propio nginx), marcada SIN implementar en el MVP

### REQ-C10-DOC-3 — Verdad condicionada y paso pendiente
- THEN el doc marca la cuestión CORS-LAN como condicionada a la activación (a+) de G1 (pendiente de ratificación del usuario): accediendo por `192.168.1.58:8080` hoy da 403 en `/ws` y API — la variante (a+) se documenta como paso de deploy futuro, NO como hecho consumado (M de Área 5/REQ-C10-CORS-5)

### REQ-C10-DOC-4 — README índice intacto (D1)
- THEN `docs/README.md` (índice congelado del paquete de definición) NO se altera; el doc nuevo sigue la numeración 01–07 en `docs/` — el done-when («en /docs **o** README operativo») queda satisfecho por la primera rama

---

## Área 13 — Regresión y QA

Canon de partida: **177 Java** (mvn) · **55 Vitest** · 23/24 qa_c3 y 9/9 qa_c7 con pauta E3.22/pre-fase = N/A-post-D2 · smoke C8 23/23 + 1 BLOQUEADO Q23 tolerado · arnés visual C9: 26 checks con 4 FAIL honestos.

### REQ-C10-QA-1 — Suites backend
- THEN MvcTest (`ProjectsControllerMvcTest`, patrón ~25 @Test de C3) de las 3 DELETE + summary: 401 de cadena vía slice, 400 con los DOS codes (`confirm_required`/`confirm_mismatch`), 404, 204/200 (y `{"deleted": 0}`), orden fail-fast según M10; pin SQL puro (patrón D-9) de los literales `DELETE FROM projects WHERE id = ?`, `DELETE FROM logs WHERE id = ?`, `DELETE FROM logs WHERE project_id = ?` (+ agregados del resumen); test de serialización del `SummaryDto`/ProjectDto (ausente sin include, presente con include); smokes canónicos `scripts/qa_c*_smoke.sh` con la pila para la ejecución real de SQL (MvcTest no ejecuta BD)

### REQ-C10-QA-2 — Suites frontend
- THEN Vitest crece sin DOM (D-C8-11): round-trip storeListado (Área 7), memo de filtros (Área 9, delta de llamadas a `aplicarFiltros`), timeout CONNECTING en `ws/client.test.ts` (stub readyState controlable, Área 6 incl. semántica M9), Navigate de Login a nivel función/hook (Área 8). Los 55 existentes sin regresión salvo los que el fix re-especifica (siempre citando literal, nunca relajando)

### REQ-C10-QA-3 — Pauta de regresión heredada
- THEN regresiones qa_c3/qa_c7 con la pauta ratificada en C8: **E3.22 y pre-fase qa_c7 = N/A-post-D2** (premisas invalidadas por D1 del charset, no bugs); **Q23 tolerado** (demo visual MANUAL); smokes canónicos sin editar

### REQ-C10-QA-4 — Arnés visual C10 (G5-a)
- THEN `scripts/qa_vis_c10.py` NUEVO reutilizando la maquinaria de `qa_vis_c9.py` (import limpio o copia controlada identificada función-a-función en el head; NO duplicar los 26 checks): **re-run honestos** de los 4 FAIL post-fix — E9.11/E9.26 (WS, con la ventana de kill del servicio `backend` como hoy), E9.13, E9.2 (arribo endurecido: URL PERDURANDO, learned 1), E9.10 — y **checks nuevos**: doble confirmación UI+cabecera en los 3 DELETE (incl. `confirm_mismatch` alcanzable con texto mal tecleado que NO borra y NO desloguea — REQ-C10-UI-2 con G6), estados del flujo 8 (204→banner→/proyectos; 200→banner con N real; 8b→«Registro eliminado»), resumen (tarjeta con badges+franja en `/proyectos`, bloque en ficha, estado vacío «sin logs todavía»), CORS (lista latente con un solo origin: sin cambio observable)

### REQ-C10-QA-5 — Destructivos al final, en siembra efímera
- THEN los checks destructivos (borrados/purgas) corren **AL FINAL** y SOLO contra la siembra efímera del propio arnés (residuo C9: «la limpieza real del residuo C9 son precisamente los DELETE de C10») — **jamás contra los datos actuales del usuario**; la ventana de kill del backend ya la cubre C9 (E9.11/E9.26) y NO se replica en C10 salvo re-run
- AND evidencia en `.atl/sdd/C10/evidencia/` NO commitear (sha256 en qa-report; `DIR_EVID` del arnés C10 apunta a C10 — M6); `qa_vis_c9.py` queda bit a bit

---

## Done-when (del 06/07 — citado, NO redecido)

> «las 3 operaciones destructivas exigen doble confirmación (UI + cabecera) y devuelven los estados del AppFlow flujo 8; el resumen aparece en lista/ficha; el doc de acceso remoto está listo en /docs o README operativo.»

Mapeo: doble confirmación → REQ-C10-UI-1 + REQ-C10-DEL-2/3 + REQ-C10-LOGDEL-2 + REQ-C10-PURGA-3; estados flujo 8 → REQ-C10-DEL-1/4, REQ-C10-LOGDEL-1/3, REQ-C10-PURGA-1/4 + REQ-C10-UI-3/4/5; resumen en lista/ficha → REQ-C10-RESUMEN-1..7 + REQ-C10-UI-6/7; doc → REQ-C10-DOC-1..4. Los fixes H-C9-* (Áreas 6–9) no forman parte del done-when literal del 07 pero son asignación ratificada del orquestador (cierre C9) y quedan de primera clase.

## Trazabilidad resumida

| Área | REQ | Insumo contractual | Verificación principal |
|---|---|---|---|
| 1 | DEL-1..7 | 05 §3.2/§3.3/§4 r3/r12 · 03 flujo 8a · 02 §2.3/§2.5 | MvcTest + pin SQL + smoke |
| 2 | LOGDEL-1..4 | 05 §3.2/§3.3 · 03 flujo 8b | MvcTest |
| 3 | PURGA-1..5 | 05 §3.2/§3.3 · 03 flujo 8c · M4 | MvcTest + pin SQL (`update`→N) |
| 4 | RESUMEN-1..7 | 05 §3.2/§3.3 · 03 flujo 9 · M3/M5 | MvcTest + serialización + smoke |
| 5 | CORS-1..5 | explore A.4 · G1-C2 · proposal G1 | `CorsConfigTest` actualizado |
| 6 | WS-1..3 | ficha ws-reconexion · R9.2.11-b · R8.30/31 | Vitest ciclo stubbed + re-run E9.11/E9.26 |
| 7 | E913-1..2 | ficha e913 · E8.35/D-C8-8 | Vitest round-trip + re-run E9.13 |
| 8 | E92-1..2 | ficha e92 · R8.5/E8.3/R9.2.2 | Vitest función/hook + re-run E9.2 |
| 9 | E910-1 | ficha e910 · R9.2.10/QP-8.22 | Vitest memo + re-run E9.10 |
| 10 | UI-1..8 | 04 V10/V11 + mockups 03/04/05 · 03 flujo 8 + §4 | Arnés C10 (checks nuevos) |
| 11 | UX401-1..3 | DEV-C8-4 · AppFlow §5 · explore B.1 | Arnés C10 (400-no-desloguea) |
| 12 | DOC-1..4 | 02 §6.8/§2.3 · state.md C9 FB-0 · G1(a+) | Revisión del doc (done-when) |
| 13 | QA-1..5 | explore C.1–C.3 · state.md · 07 pautas | mvn/vitest/scripts/qa_vis_c10 |