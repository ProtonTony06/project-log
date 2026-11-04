---
project: Project_Log
artifact: design
last_updated: 2026-10-29
status: complete
change: C10 — Fase 2 (3 DELETEs con doble confirmación, resumen por proyecto, CORS multi-origen latente, doc 08, fixes H-C9-* y arnés visual C10)
base_head: 27b62cb
insights: .atl/sdd/C10/spec.md · .atl/sdd/C10/proposal.md §4 · .atl/sdd/C10/explore.md (A.1–A.6, B.1–B.6, C.1–C.3, D, gaps 1–10)
contrato: docs/05 §3.2/§3.3/§4 (r.2/r.3/r.12) · docs/03 flujos 8/9 + §4/§5 · docs/04 V10/V11 + mockups 04/05 · docs/02 §2.3/§2.5/§6.8 · docs/06–07 (Fase 2, done-when C10)
---

# C10 · Design — borrados definitivos, resumen por proyecto, acceso remoto y fixes de C9

Design de implementación. Los literales de API/UI **vienen citados del spec/proposal** (05 §3.2/§3.3, AppFlow flujo 8) y NO se redefinen: este documento cierra las decisiones que el spec dejó abiertas (M9, M10, F2, purga, CORS, UI, arnés, doc) con fragmentos EXACTOS de código y pines de test. Sin capa de servicio; controller FINO como toda C3; SQL explícito inline package-private (patrón D-9); Vitest sin DOM (D-C8-11).

## 1. Arquitectura de la pieza

Package-by-feature respetado al 100 % — ni un fichero fuera de su paquete:

**Backend `backend/src/main/java/com/projectlog/`** (cadena 2 `/api/v1/panel/**` existente; SecurityConfig/JwtAuthFilter SIN tocar):
```
projects/controller/ProjectsController.java   ← EXTENDE: DELETE /{id}, DELETE /{id}/logs, GET /{id}/summary, include= en listar
projects/dto/ProjectDto.java                  ← EXTENDE: 9.º componente summary + @JsonInclude(NON_NULL) + ctor comodín 8-arg
projects/dto/SummaryDto.java                  ← NUEVO: record del §3.3 (by_level/by_environment/total/last_log_at)
projects/repository/ProjectsRepository.java   ← EXTENDE: SQL_BORRAR + borrar(id): boolean (mono-sentencia, CASCADE del motor)
query/controller/LogsController.java          ← EXTENDE: DELETE /{id} (X-Confirm-Log-Id, fail-fast sin BD)
query/repository/LogsQueryRepository.java     ← EXTENDE: 3 constants SELECT_RESUMEN_* + resumen(id): SummaryDto
query/repository/LogsBorradoRepository.java   ← NUEVO: SQL_DELETE_POR_ID / SQL_DELETE_POR_PROYECTO + borrarPorId/purgarPorProyecto
config/CorsConfig.java                        ← EXTENDE: split por coma de FRONTEND_ORIGIN (G1-a latente)
```

**Backend tests `backend/src/test/java/com/projectlog/`**: `projects/ProjectsControllerMvcTest` (EXTENDE, ~14 @Test nuevos) · `query/controller/LogsControllerMvcTest` (EXTENDE, ~7 @Test nuevos) · `projects/dto/ProjectDtoSerializationTest` (EXTENDE) · `projects/repository/ProjectsBorradoSqlTest` (NUEVO, pin D-9) · `query/repository/LogsBorradoSqlTest` (NUEVO, pin D-9) · `query/repository/LogsQuerySqlTest` (EXTENDE pins de resumen) · `config/CorsConfigTest` (EXTENDE).

**Frontend `frontend/src/`** (Vitest sin DOM; nada de DOM en tests):
```
api/types.ts            ← EXTENDE: SummaryDto + ProjectDto.summary? + Record<Nivel/Entorno, number>
api/panel.ts            ← EXTENDE: retornos void/{deleted:N} + listaProyectosConResumen() + resumenProyecto(id)
ws/client.ts            ← EXTENDE: timerApertura T=10 s en abrir() (D-C10-7); nada más del ciclo
hooks/useLogsStream.ts  ← EXTENDE: guard de cambio por clave (defensa D-C10-12) + helper exportado
hooks/useAuth.ts        ← EXTENDE: destinosPostLogin() + elNavigateGana() (helpers puros de D-C10-11)
pages/Login.tsx         ← F8 (Navigate condicional — carrera E9.2)
pages/Logs.tsx          ← F7 (¿? doble) + F9 (useMemo de toSetFilters)
pages/LogDetalle.tsx    ← F7 (2 enlaces «Volver») + flujo post-8b (alExito)
pages/Proyectos.tsx     ← include=summary + banner de aviso + botón «Actualizar»
pages/ProyectoDetalle.tsx ← F2 (X-Confirm-Name = texto tecleado) + post-8a/8c + bloque resumen
components/TarjetaProyecto.tsx ← franja de resumen (maqueta 04) como texto plano del <button>
components/ModalBorrado8b.tsx  ← prop alExito (post-204) sin tocar su guard de error
components/ResumenLogs.tsx     ← NUEVO: bandas de badges + estado vacío (compartido tarjeta/ficha)
ws/client.test.ts / hooks / api/client.test.ts ← Vitest nuevos (§5)
```

**Docs y scripts**: `docs/08_ACCESO_REMOTO.md` (NUEVO, §D-C10-14) · `scripts/qa_vis_c10.py` + `scripts/qa_vis_c10.sh` (NUEVOS, import limpio de `qa_vis_c9.py`) · `scripts/qa_c10_smoke.sh` (NUEVO, patrón qa_c9_smoke.sh — ejecución REAL de SQL). `qa_vis_c9.py` queda bit a bit (G5/M6). VM 108 y `docs/README.md` INTOCABLES.

## 2. Decisiones D-C10-n (cerradas aquí, con racional)

### D-C10-1 — M10: orden fail-fast 400-confirmación vs 404 en los 3 DELETE

**Orden fijado: los 400 de confirmación ANTECEDEN SIEMPRE al 404 de existencia; en 8a/8c el `confirm_mismatch` necesita la fila (compara contra el nombre en BD), así que ahí el 404 gana SOLO cuando el recurso ha desaparecido ANTES de poder comparar.**

- **8a `DELETE /projects/{id}` y 8c `DELETE /projects/{id}/logs`** (mismo orden exacto):
  1. cabecera `X-Confirm-Name` ausente → **400 `confirm_required`** SIN tocar BD;
  2. `findById` → vacío → **404 `not_found`** (E3.19: el DELETE jamás va sobre id inexistente; el mensaje es el MSG_NOT_FOUND C3);
  3. `!fila.name().equals(confirmacion)` (equals EXACTO String, sin trim, diferenciando mayúsculas y espacios) → **400 `confirm_mismatch`**;
  4. DELETE → 204 / 200 según endpoint.
- **8b `DELETE /logs/{id}`** — comparación contra el PROPIO path (`String.valueOf(id)`), sin BD:
  1. cabecera `X-Confirm-Log-Id` ausente → **400 `confirm_required`**;
  2. valor ≠ `String.valueOf(id)` EXACTO → **400 `confirm_mismatch`** (con id inexistente Y cabecera desigual el 400 gana: la comparación no toca BD);
  3. `borrarPorId` → `false` → **404**; `true` → 204.

**Racional**: (i) AppFlow flujo 8 literal: «La doble confirmación es estructura obligatoria del sistema … Sin ambas, la API rechaza con 400» ⇒ una petición malconfirmada se rechaza por estructura antes de interrogar recursos; (ii) disciplina C3 fail-fast más-barato-primero: el `confirm_required` se resuelve con cero E/S; (iii) menor oráculo: peticiones sin confirmación correcta no distinguen existente/inexistente; (iv) 8b puede validar TODO sin BD porque el id de confirmación viaja en la ruta ⇒ su 400 con recurso ya borrado es determinista y testeable.

**Pines de test (MvcTest)**: 8a/8c — id=999999 SIN cabecera → 400 `confirm_required` (NO 404); id=999999 con cabecera ERRÓNEA → 404 (la existencia gana porque el target de comparación ya no existe); id existente con cabecera errónea (mayúsculas/espacio interior) → 400 `confirm_mismatch` y repo.delete NUNCA invocado (`verifyNoInteractions`). 8b — id=999999 + `X-Confirm-Log-Id: 999999` → 404; id=999999 + `X-Confirm-Log-Id: 999998` → 400 `confirm_mismatch`; id existente + id desigual → 400 sin tocar BD. Carrera documentada: si la fila desaparece entre `findById` y el DELETE, `borrar()` devuelve `false` → 404 (patrón C4 empty-RETURNING de `ApiKeyController.regenerar`).

**Cuerpos de error**: helper `error()` local (patrón C3 varargs) con constantes nuevas `CODE_CONFIRM_REQUIRED = "confirm_required"` / `CODE_CONFIRM_MISMATCH = "confirm_mismatch"`, mensajes `MSG_CONFIRM_MISMATCH = "La confirmación no coincide"` (literal de AppFlow flujo 8 — es el texto que la UI pinta, REQ-C10-UI-2) y `MSG_CONFIRM_REQUIRED = "La cabecera X-Confirm-Name es obligatoria"` (o `… X-Confirm-Log-Id …` en 8b) — SIN `field` (patrón ApiKeyController: los errores de confirmación no llevan field; el cuerpo §3.1 queda `{code, message}`; pínese la ausencia).

### D-C10-2 — Purga: N del retorno de `update`, mono-sentencia, y desambiguación del N=0

**SQL (única fuente, en el NUEVO `query/repository/LogsBorradoRepository`)**:
```sql
DELETE FROM logs WHERE id = ?            -- SQL_DELETE_POR_ID  (8b)
DELETE FROM logs WHERE project_id = ?    -- SQL_DELETE_POR_PROYECTO (8c)
```
Ambos mono-sentencia con `@Transactional // defensa documentada (R3.2): mono-sentencia`. El **primer** `int` de retorno de `JdbcTemplate.update(...)` del proyecto ES el `deleted: N` (gap 6/propuesta R1: primer DELETE con cuerpo de respuesta; la forma exacta es **200** con cuerpo `{"deleted": N}` serializado con el `ObjectMapper` del controller — LinkedHashMap por estabilidad). El índice `logs_dashboard_idx (project_id, …)` sirve a la purga por su prefijo (V1, sin migración).

**Desambiguación N=0 vs proyecto-desaparecido (M4 + REQ-C10-PURGA-4)**: tras `int n = purgarPorProyecto(id)`, si `n == 0` el controller re-verifica `findById` — vacío ⇒ **404** («nunca 200 con `deleted: 0` sobre proyecto inexistente», cubre también la carrera del CASCADE); presente ⇒ **200 `{"deleted": 0}`** (estado vacío informativo, M4). Coste: 1 SELECT extra SOLO en la rama N=0 (raras y pequeñas). Los caminos N>0 nunca pagan el re-check. Pines: N=0 con proyecto existente → 200 `{"deleted": 0}`; `purgar`→0 + `findById`→vacío → 404 (mock — carrera).

**8a borrado de proyecto**: `ProjectsRepository.borrar(id)` — `static final String SQL_BORRAR = "DELETE FROM projects WHERE id = ?"` mono-sentencia; PostgreSQL purga los logs por la FK `logs_project_fk ON DELETE CASCADE` de V1 en la MISMA transacción del motor (regla 3 del §4: sin huérfanos, sin pasos intermedios) y la API key muere con la fila (columnas dentro de `projects`, §2 — nada que purgar aparte; REQ-C10-DEL-5 por smoke + MvcTest de interacciones). Retorno `boolean` (`update == 1`) → `false` ⇒ 404 de carrera.

**Purga / borrado ↔ streaming (seam C7, decidido y PINADO con AppFlow)**: una purga (o borrado) con sesión WS abierta **NO emite frame alguno** — LiveBroadcaster solo emite desde ingesta (explore gap 10, sin acoplamiento WS↔DELETE) y el cliente NO se cierra ni se resetea. Consecuencia literal de flujo 8c: «la próxima consulta de logs del proyecto arranca vacía» ⇒ el panel sigue mostrando las filas ya pintadas hasta su PRÓXIMO refetch propio (cambio de filtro, reconexión → refetch página 1 de E8.30, «Actualizar», navegación); el resumen tampoco se recalcula en vivo (flujo 9 paso 5). Se pina con check del arnés E10.x: tras purga con `/logs` abierto, delta de frames del WsContador = 0 y delta de handshakes = 0, y un `logs()` posterior (refetch) llega vacío.

### D-C10-3 — F2: SQL exacto del resumen y dónde vive

**3 estamentos literales en `LogsQueryRepository`** (todos sobre `logs`, resueltos por el prefijo `project_id` de `logs_dashboard_idx`; `COALESCE`/agregación JSON descartadas — ver racional):
```sql
SELECT_RESUMEN_NIVELES:
  SELECT level, COUNT(*) FROM logs WHERE project_id = ? GROUP BY level
   ORDER BY CASE level WHEN 'FATAL' THEN 1 WHEN 'ERROR' THEN 2 WHEN 'WARN' THEN 3
                       WHEN 'INFO' THEN 4 WHEN 'DEBUG' THEN 5 ELSE 6 END
SELECT_RESUMEN_ENTORNOS:
  SELECT environment, COUNT(*) FROM logs WHERE project_id = ? GROUP BY environment ORDER BY environment
SELECT_RESUMEN_TOTALES:
  SELECT COUNT(*), MAX(received_at) FROM logs WHERE project_id = ?
```
**Firma**: `public SummaryDto resumen(long projectId)` — 3 llamadas `jdbcTemplate.query(...)` por proyecto; los mapas se construyen en `LinkedHashMap` sobre las filas (solo claves PRESENTES, REQ-C10-RESUMEN-4) y el orden maqueta-04 («FATAL 1 · ERROR 148 · WARN 63 …») sale del `CASE` en SQL — determinista también para MvcTest; entornos alfabéticos. `last_log_at`: `OffsetDateTime` → `.format(LoginController.EXPIRES_AT_FORMATTER)` del C2 (patrón RowMapper de `ProjectsRepository`), `null` si no hay logs. Proyecto sin logs → mapas vacíos, `total: 0`, `last_log_at: null` (una sola fila del TOTALES con COUNT=0 y MAX=NULL ⇒ nunca `queryForObject` sobre ausencia real).

**Racional «3 estamentos vs 1 con `jsonb_object_agg`»**: con 1 estamento por proyecto sería imposible devolver mapas por nivel y por entorno sin añadir jsonb + parseo en Java (más superficie, misma E/S); con 3 estamentos todo es SQL plano pinado y mapas Java naturales. Coste de lista con include: **3N idas** (N proyectos) — aceptable por contrato: el panel del MVP es de decenas de proyectos (maqueta 04: «5 proyectos»), cada ida es index-scan por prefijo y NO hay SQL dinámico ni `IN`-lists (frontera E1/C6: cero SQL dinámico). Reversible: condensar a `jsonb_object_agg` es un refactor local del método `resumen`.

**Ubicación** (decidida): los SELECT de lectura del resumen viven en `query/repository/LogsQueryRepository` — co-locación con `SELECT_LISTADO`/`SELECT_POR_ID` (recomendación del explore B/§4.2: agregados sobre los mismos datos). Los 2 DELETE sobre `logs` NO entran allí (su javadoc declara «ÚNICA fuente de SQL de LECTURA» y `ingest/LogsRepository` es INSERT-only por frontera C5) → **NUEVO `query/repository/LogsBorradoRepository`**: única morada honesta para las mutaciones del panel sobre `logs`, con los mismos patrones (constants package-private para pin D-9, `@Transactional` mono-sentencia). Dependencia nueva DECLARADA: `query.repository → projects.dto` para `SummaryDto` (mismo patrón ya existente `query → auth` por el formatter del C2; el resumen ES parte del contrato del proyecto).

### D-C10-4 — Modelo: SummaryDto record, campo `summary` NON_NULL en ProjectDto, formas EXACTAS

```java
// projects/dto/SummaryDto.java — NUEVO (G4-a)
public record SummaryDto(Map<String, Long> by_level,
                         Map<String, Long> by_environment,
                         long total,
                         String last_log_at) { }
```
`ProjectDto` gana el 9.º componente **`@JsonInclude(JsonInclude.Include.NON_NULL) SummaryDto summary`** (anotación de Jackson sobre el componente del record — propagada al accessor en Boot 3.5) + **constructor comodín de 8 args** que delega `this(..., null)`, para que `ProjectsRepository.FILA`, tests de C3 y cualquier constructor previo quede intacto. Con `summary == null` la clave **AUSENTE** del JSON (no `null`): `GET /projects` sin include, `GET /projects/{id}`, POST 201 y PATCH 200 quedan byte-idénticos a C8 (REQ-C10-RESUMEN-2). Con include: el controller compone con el accessor `conResumen(SummaryDto)` (`new ProjectDto(p.id(), …, p.has_key(), resumen)`).

`SummaryDto` NO lleva `NON_NULL`: sus 4 claves viajan SIEMPRE — con proyecto sin logs la serialización EXACTA es `{"by_level": {}, "by_environment": {}, "total": 0, "last_log_at": null}` (null EXPLÍCITO de `last_log_at`, literal §3.3/flujo 9). Pines: `ProjectDtoSerializationTest` — sin summary `doesNotContain("summary")`; con summary el objeto completo; SummaryDto vacío con las 4 claves y `last_log_at` `isNull()`; `last_log_at` con dato → regex ISO-Z.

**Endpoints**: `GET /projects` gana `@RequestParam(required = false) String include` validado fail-fast ANTES de BD (ver D-C10-5) y compone `findAll(status)` + `resumen(id)` por proyecto. `GET /projects/{id}/summary` (ProjectsController, mapping natural `/{id}/summary`): `findById` → 404 → `resumen(id)` → 200 (E3.19: 404 sin consultar logs; nulo-imposible después). Archivados cuentan igual (M5/flujo 9 paso 3: el archivado solo afecta a la ingesta, no a consultas — el resumen ignora `status` por diseño).

### D-C10-5 — Política de `include` inválido (REQ-C10-RESUMEN-6)

`include` presente ≠ `summary` → **400 `filter_invalid_value`** con mensaje `"Valor de include no válido; use summary"` y **`field: "include"`**, en el orden de checks: **(1) `status`, (2) `include`** (el check existente de status queda byte-idéntico y PRIMERO — orden additivo determinista; `?include=summary&status=xxx` sigue validando ambos antes de BD). Racional: `filter_invalid_value` es el code del vocabulario para valores de query inválidos (C3/C6/C7) — no se inventa `include_invalid` (literal nuevo sin ancla); el `field` señala el parámetro culpable como hacen status/limit/level. Único valor válido: `summary` (enumeración literal de §3.2). Pin: MvcTest `?include=summary` → 200 con summary; `?include=otracosa` → 400 con field=include y BD sin invocar.

### D-C10-6 — CORS G1-a latente: fragmento EXACTO de CorsConfig

Sustitución EXACTA del bloque actual (líneas 37–44) — methods/headers/credentials/maxAge y el registro `/**` SIN tocar:
```java
// G1-a (latente): FRONTEND_ORIGIN acepta lista separada por coma. Sin
// "*", sin setAllowedOriginPatterns (G1-C2): reflejo EXACTO de cada origin.
List<String> origins = Arrays.stream(frontendOrigin.split(","))
        .map(String::trim)
        .filter(o -> !o.isEmpty())
        .toList();
if (origins.isEmpty()) {
    // S26/E2.18: cero configuraciones ⇒ CORS cerrado por defecto.
    return source;
}
CorsConfiguration config = new CorsConfiguration();
config.setAllowedOrigins(origins);
```
Cambios mínimos: el guard `isBlank()` se sustituye por `origins.isEmpty()` (una `","` solitaria ⇒ cerrado por defecto, pínese); **una sola origin produce una lista de 1 elemento — lista de igual contenido; el comportamiento C9 queda idéntico** (REQ-C10-CORS-2, test verde con el mismo literal). `setAllowedMethods` ya incluye DELETE (REQ-C10-CORS-3: nada que añadir; `X-Confirm-*` cubre las cabeceras nuevas). VM/.env intocables (REQ-C10-CORS-5). `CorsConfigTest` EXTENDE: (1) una origin → lista `[localhost]` idéntica al C9; (2) `a,b` → dos origins; (3) `a, b , ,c` → trim y filtrado de vacíos; (4) `","` / vacío → cero registros; (5) rechazo de Origin no listada sin cabeceras CORS (REQ-C10-CORS-4); prohibiciones G1-C2 re-pinnadas (`getAllowedOriginPatterns` vacío/nulo). Arnés C10 lo constata como «sin cambio observable» (check E10.x).

### D-C10-7 — M9/G3-a: timeout CONNECTING T=10 s en `ws/client.ts` — mecanismo elegido

**Decisión: timer dedicado de apertura (`timerApertura`), guard `readyState === CONNECTING`, y DELEGACIÓN al enrutado EXISTENTE por `abiertoAlgunaVez` — NO se crea flag nuevo de reintentos ni se manipula `intento` desde el timeout.** El mecanismo «close-durante-handshake» de la lista del GATE.

- **Constante exportada** `export const T_TIMEOUT_CONNECTING_MS = 10_000;` (pin del Vitest: el delay capturado del Reloj fake es EXACTAMENTE 10000; heartbeat del servidor es 30 s y el backoff mínimo 800 ms — 10 s corta un hang eterno sin rozar ninguno de los dos).
- **En `abrir()`, inmediatamente tras `this.socket = socket`**:
```ts
this.timerApertura = this.reloj.setTimeout(() => {
  this.timerApertura = null;
  // Doble-close inofensivo: si el socket ya abrió o ya cerró, NO está en
  // CONNECTING ⇒ no-op (el onclose real ya enrutó por alCierre).
  if (socket.readyState === WebSocket.CONNECTING) {
    socket.close();
  }
}, T_TIMEOUT_CONNECTING_MS);
```
  El socket va CAPTURADO en la closure (no `this.socket`): una carrera de reconexión no dispara close sobre el socket equivocado.
- **`socket.onopen`**: primero `cancelarTimerApertura()` (clearTimeout + `timerApertura = null`), después lo ya existente (`abiertoAlgunaVez/intento=0/alEstado("conectado")/enviarFiltros()`) — el timer se cancela ANTES de `enviarFiltros()` (REQ-C10-WS-1, sin cierres espurios sobre sockets sanos).
- **`alCierre()`**: primera línea nueva `cancelarTimerApertura()` — si un close REAL llega con el timer aún corriendo, el timer queda anulado y (por el guard) su disparo tardío sería no-op: **doble-close inofensivo por partida doble**.
- **`cerrar()`**: también cancela `timerApertura` (desmonte durante CONNECTING sin timers colgando; mismo patrón del `timer` de backoff).
- **Campo nuevo** `private timerApertura: ReturnType<typeof setTimeout> | null = null;` — DISTINTO del `timer` de backoff: `reconectar()` (`timer !== null` guard) no se confunde y el backoff 1 s→30 s con jitter queda intacto (interacción R8.30/R8.31: el timeout NO compite con el backoff; el timeout solo convierte «sin nunca-close» en «un-close-más»).

**Semántica del intento (REQ-C10-WS-2, la distinción EXACTA exigida por M9)**: el `socket.close()` manual dispara `onclose` → `alCierre()` EXISTENTE, cuyo branch `if (!this.abiertoAlgunaVez)` ES ya la distinción requerida: `abiertoAlgunaVez` es de INSTANCIA y se pone `true` en el primer `onopen` sin volver a `false` — así que *socket inaugural de la sesión* ⇔ `abiertoAlgunaVez === false` ⇔ `alCierreInaugural()` → `me()` → **`intento` NO crece**; *socket de la cadena* (cualquier socket posterior, aunque muera en CONNECTING sin abrir jamás) ⇔ `abiertoAlgunaVez === true` ⇒ `intento += 1` → backoff → **banner «Reconectando (intento N+1)…» N creciente** (R9.2.11-b). El timeout simplemente introduce el close que faltaba; nada del contador ni del enrutado cambia (el resto de la maquinaria — dedup D-C8, primer-frame-JWT, set_filters S8-8 — intacta, REQ-C10-WS-3). Pines de Vitest: vía cadena (socket 1 abre, se cierra, socket 2 cuelga en CONNECTING → timeout → close() sobre el socket 2 → `alEstado("reconectando", 2)` creciente) y vía inaugural (PRIMER socket cuelga → timeout → close → `alCierreInaugural()` llamado y `alEstado("desconectado", 0)` — intento NO crece).

### D-C10-8 — UI: cableado real de la cabecera (G2-a/M1) y flujo post-204/200

**8a/8c — el input va al header**: `ModalPurga8c` y `ModalEliminar8a` cambian su prop de `alConfirmar: () => void` a `alConfirmar: (texto: string) => void` y llaman `alConfirmar(texto)` con el TEXTO TECLEADO tal cual (sin re-trim en el cliente). El trim sigue siendo SOLO el gate del botón (`coincideLiteralConTrim`, §4 de 04_UIUX): con un texto trim-igual-pero-no-exacto (p. ej. espacio final) el servidor responde 400 `confirm_mismatch` y el usuario ve el error literal dentro del modal (REQ-C10-UI-1/2 — la comparación EXACTA es del servidor). `ejecutarConfirmado(texto: string)` pasa `purgarLogs(dto.id, texto)` y `eliminarProyecto(dto.id, texto)` — **adiós `dto.name`** (M1 cerrado). `ModalBorrado8b` ya manda `eliminarLog(id, id)` correcto: solo gana prop **`alExito: () => void`** invocada tras el 204 (su guard interno `enviando/error` intacto).

**Post-8a (REQ-C10-UI-3)**: 204 → `setConfirmacion(null)` → `navegar("/proyectos", { state: { aviso: "Proyecto y sus logs eliminados definitivamente" } })` — `Proyectos.tsx` gana el banner de aviso leyendo `location.state.aviso` (patrón ya usado por `ProyectoDetalle`). **404** en 8a → modal cerrado → mismo `navegar("/proyectos", { state: { aviso: "No existe (quizás fue eliminado)" } })` (§5 + retorno a la lista, literal del flujo). **400** → el modal permanece abierto, error literal dentro (`errorAccion` ya se pinta), `coincide` sigue evaluando el texto (reintento directo) y **NO hay logout** (el 400 no toca el interceptor R8.14 — G6 verificado con check E10.x).

**Post-8c (REQ-C10-UI-4)**: 200 → `const r = await purgarLogs(...)` → `setConfirmacion(null)` → banner DE ESTADO local en la ficha (nuevo state `avisoAccion: string | null`, render como `Banner variante="info"`): **`${r.deleted} logs eliminados; el proyecto y su key siguen intactos`** con el N REAL del cuerpo (no estimado) → `cargar()` refresca DTO **y resumen** (el bloque pasa a «sin logs todavía»). **404** → banner rojo §5 de la ficha (patrón `errorAccion` ya existente).

**Post-8b (REQ-C10-UI-5)**: `LogDetalle` pasa `alExito={() => { setModalAbierto(false); ... navegar("/logs" + location.search) }}` tras poner el aviso en el state de navegación (`{ state: { aviso: "Registro eliminado" } }`) — regreso a `/logs` **conservando la query** (coherente con D-C10-10; `Logs.tsx` gana lectura de `aviso` en su banner de estado). **404** → error literal dentro del modal (ya existe), sin cierre.

### D-C10-9 — UI resumen: franja en `<button>` (maqueta 04) y bloque en ficha (maqueta 05)

**Componente NUEVO `components/ResumenLogs.tsx`**, compartido por tarjeta y ficha, texto plano puro (R7 del proposal — la `TarjetaProyecto` es un `<button>` íntegro y la navegación por clic NO se rompe):
- Prop `resumen: SummaryDto` + `variante: "franja" | "bloque"`. En `franja` (tarjeta): bandas de badges de severidad (`FATAL 1 · ERROR 148 · …` con spans `lv-*` — clases existentes de `index.css`/`BadgeSeveridad`, WARN/ERROR/FATAL destacados según paleta 04_UIUX §2), bandas `env-*` (`production 23 988 · staging 1 342`), y franja inferior tras `border-t`: `**25 330** logs · último: <mono>2026-10-19T21:47:58Z</mono>`. En `bloque` (ficha): total grande + «logs totales · último …», mismas bandas.
- Estado vacío (total 0): «**sin logs todavía**» en texto informativo (M4: no error) — en tarjeta y ficha.
- Archivado: `opacity-80` del botón ya existente cubre la franja — resumen INTACTO, sin rama especial.
- Sin sub-elementos interactivos: los badges son `<span>` (los existentes `BadgeSeveridad`/`PildoraEntorno` se REUTILIZAN por referencia o sus clases; nada clicable nuevo dentro del botón).

**`/proyectos`**: `listaProyectosConResumen()` (`GET /projects?include=summary` — función NUEVA de `panel.ts`; `listaProyectos()` sin include queda para los mapas de nombres de `Logs.tsx`, sin payload extra); cabecera añade el literal `GET /projects?include=summary` + botón **«Actualizar»** que re-invoca `cargar()` (flujo 9 paso 5). **Ficha**: `cargar()` añade en paralelo `resumenProyecto(idNum)` → state `resumen`; sección «Resumen de logs» bajo «Datos del proyecto» (maqueta 05). Degradación honesta: si el resumen falla con error no-404, el bloque se OMITE sin romper la ficha (complementario, no blocker) — nota visible en el head del bloque. Los comentarios «SIN resumen P1 (R8.18)» de `Proyectos.tsx`/`TarjetaProyecto.tsx` se REESCRIBEN con el literal nuevo (`include=summary`, §3.2).

### D-C10-10 — F7/H-C9-E9.13: la query viaja tal cual (3 puntos textuales)

`location.search` YA trae su `?` — se ELIMINA el `?${...}` interpolado:
1. `Logs.tsx::alVerDetalle`: `navegar(\`/logs/${id}${location.search}\`);`
2. `LogDetalle.tsx` — AMBOS «Volver a logs» (~L113 y ~L139): `to={\`/logs${location.search}\`}`
3. El `guardarListado(location.search, …)` de `Logs.tsx` NO cambia ni el store cambia: `consumirListado` compara `tomada.search === searchActual` con igualdad EXACTA — con la search viajando correcta el round-trip E8.35 (query, nº filas y 1.ª fila intactas, CERO refetch) se recupera. Pin de Vitest en `storeListado.test.ts`: contrato `guardarListado("?text=qa-c10")` + `consumirListado("?text=qa-c10")` → snapshot; `consumirListado("??text=qa-c10")` → null (la search con doble `?` NUNCA coincide — pina la forma que las pages deben enviar). La certificación real es el re-run honesto E9.13 (NEEDLE_E35).

### D-C10-11 — F8/H-C9-E9.2: el Navigate de Login deja de pisar el destino

**Vía elegida (opción «condicionar» del proposal): el `<Navigate to="/" replace />` solo actúa en LLEGADAS ya autenticadas (restore/me, back-button), y NO en la transición propia del login.** En `Login.tsx`:
```ts
const destinoPropio = useRef<string | null>(null);   // transición iniciada por enviar()
// en enviar(), ANTES del await (leído de storage, borrarDestino() de login() no lo toca):
destinoPropio.current = destino ?? "/logs";
...
await sesion.login(usuario, password);
navegar(destinoPropio.current, { replace: true });
// en finally/catch de errores: destinoPropio.current = null (higiene)
// render:
if (sesion.estado === "autenticado" && destinoPropio.current === null) {
  return <Navigate to="/" replace />;
}
```
Durante el hueco entre `setEstado("autenticado")` (dentro de `login()`) y `navegar(destino)` el re-render ve `destinoPropio.current !== null` ⇒ el `Navigate` NO se renderiza ⇒ **UNA sola navegación** (REQ-C10-E92-1): la que el propio `enviar()` hace hacia el destino guardado (incluida query, `/proyectos/99999999` — R8.5/E8.3/R9.2.2). Llegada SIN destino: `destino ?? "/logs"` + `replace` (REQ-C10-E92-2, sin doble salto; el `borrarDestino()` dentro de `login()` no rompe la variable local — ya leída antes del await). Llegada YA autenticada (restore, back sobre /login) ⇒ Navigate a `/` intacto (R8.3/E8.4). **Para el Vitest sin DOM**, la decisión se extrae a helpers PUROS en `hooks/useAuth.ts`: `destinoTrasLogin(destinoGuardado: string | null): string` (null → `"/logs"`; `'\/proyectos\/99999999'` → intacto) y `elNavigateGana(estado: EstadoSesion, transicionPropia: boolean): boolean` → `estado === "autenticado" && !transicionPropia` (el predicate EXACTO del render). `Login.tsx` los consume (sin lógica duplicada). La certificación END-TO-END es el re-run E9.2 endurecido (URL PERDURANDO ~700 ms, learned 1).

### D-C10-12 — F9/H-C9-E9.10: UN `set_filters` por cambio real — doble defensa testeable

1. **Primaria (la del literal del spec)**: `Logs.tsx` memoiza — `const filtrosSet = useMemo(() => toSetFilters(filtros), [filtros]);` (useMemo ya importado, L9) y `useLogsStream({…, filtros: filtrosSet, …})` — identidad estable por render porque `filtros` de `useFiltrosLogs` ya es `useMemo(…, [searchParams])` (useLogFilters L112–115). El refetch de página 1 (`[…, filtros]` L123) NO se toca.
2. **Defensa por contenido en `useLogsStream`** (protege también renders no memoizados futuros): el efecto `[p.filtros]` compara la clave serializada y SOLO envía si cambió:
```ts
const claveFiltros = useRef<string | null>(null);
useEffect(() => {
  const clave = JSON.stringify(p.filtros);
  if (clave === claveFiltros.current) return;   // render ajeno ⇒ delta 0
  claveFiltros.current = clave;
  clienteRef.current?.aplicarFiltros(p.filtros);
}, [p.filtros]);
```
   (JSON de un literal de 6 claves con orden fijo ⇒ clave estable; el reenvío tras reconnect lo hace `onopen → enviarFiltros`, no este efecto — intacto.) El helper puro se extrae como `debeReenviarFiltros(clavePrevia: string | null, filtros: SetFilters): boolean` exportado del propio módulo para el Vitest (REQ-C10-QA-2: «delta de llamadas a aplicarFiltros»): primera vez → `true`; misma clase → `false`; cambios → `true`. La certificación real es el re-run E9.10 con el WsContador (delta 1 por evento, 0 en renders ajenos).

### D-C10-13 — Arnés visual C10 (G5-a): import limpio + rebaso de 3 globals, checks al final

`scripts/qa_vis_c10.py` NUEVO. **Decisión: import limpio** — `qa_vis_c9.py` tiene `if __name__ == "__main__":` (L2592) y su top-level son SOLO constantes/definiciones sin efectos: `import qa_vis_c9 as c9` ejecuta definiciones y expone la maquinaria (pareja/passO/fail/marca/vista/shot/sellarE911/salirPorVeredictos/cargarEnvProceso/verificarPila/verificarEntorno/detectarComposeBin/loginPorUI/contextoLimpio/interceptar/ingestar/Contadores/WsContador/pararBackend/levantarBackend/bucleHealth/b1–b8). **Rebaso declarado en el head** (3 globals del módulo importado): `c9.DIR_QA = RAIZ/".atl/sdd/C10"`, `c9.DIR_EVID = c9.DIR_QA/"evidencia"` (NO commitear; sha256 en qa-report — M6), `c9.TS = time.strftime(...)` (sellos/fotos con sello propio). La **siembra efímera C10 usa prefix propio** `qa-c10-<ts>-` con `needle()` LOCAL (jamás la global `qa-c9-`), y sus literales rojas/credenciales propias. Si en apply el import resultara sucio (cualquier side-effect no constatado), el fallback es **copia controlada función-a-función** con línea a línea citada en el head — decisión binaria registrada en el head del script (G5).

**Checklist de contenido** (guard propio de b8 con el nº real de checks C10; `qa_vis_c9.py` queda bit a bit):
- Re-runs honestos, invocados como funciones del módulo C9: **E9.2** (b1, arreglo D-C10-11 — URL PERDURANDO), **E9.10** (b3, WsContador delta), **E9.13** (b6, NEEDLE_E35), **E9.11/E9.26** (b6 a/c + b7, ventana de kill `stop backend` + `up -d backend` ya maquinaria C9 — AL FINAL de la corrida).
- Checks NUEVOS E10.x (parejas `pareja("E10.x")` en fase nueva): resumen — tarjeta con badges `lv-*`/`env-*` + franja totals en `/proyectos`, archivado resumen-intacto, estado vacío «sin logs todavía», bloque de ficha con total grande, «Actualizar» refresca; doble confirmación — 8c/8a con texto trim-igual-malo → `confirm_mismatch` DENTRO del modal, **nada borrado** (filas siguen), **sesión intacta** (no logout — G6/REQ-C10-UX401-2), botón `bg-red-300` deshabilitado; 8b sentencia literal; estados flujo 8 — 8c → banner con N real + resumen a cero, 8b → «Registro eliminado» + regreso a /logs con query, 8a → banner «Proyecto y sus logs eliminados definitivamente» + tarjeta ausente en `/proyectos`; post-estados — refetch de /logs vacío tras purga, ingesta posterior 202 (key sobrevive), 404 repetido → §5; WsContador delta 0 tras purga con /logs abierto (`set_filters` reenviado intacto); CORS latente — preflight OPTIONS DELETE con `X-Confirm-Name` reflejado, sin cambio observable.
- **Destructivos AL FINAL y SOLO contra siembra efímera propia** (REQ-C10-QA-5): el proyecto propio del arnés C10 se purga (8c) y elimina (8a) al final de la fase de checks nuevos y ANTES de la ventana de kill de E9.11/E9.26. **Limpieza del residuo C9**: fase final enumera `GET /projects` y aplica purga+delete SOLO a proyectos cuyo `name` matchee `^qa-c9-|^qa-c10-` (datos del arnés, jamás del usuario) — si algún proyecto no matchea el patrón se REPORTA y se salta con guard duro. Orden total B0 (pila/env) → re-runs suaves (E9.2/E9.10/E9.13) → checks E10.x (lectura, luego mismatch, luego 8b/8c/8a destructivos propios) → limpieza de residuo → kill-window (E9.11/E9.26) → informe (`b8` del módulo C9 rebasado, exit canon 0/1/2 igual).
- **Wrapper `scripts/qa_vis_c10.sh`**: gemelo de `qa_vis_c9.sh` — RAW teed a `.atl/sdd/C10/qa-log.txt`, grep S16 de `plk_`/`eyJ` = 0 hits, passthrough.

### D-C10-14 — `docs/08_ACCESO_REMOTO.md`: esqueleto (DOC-1..4, D1 ratificada)

Doc nuevo siguiendo numeración/estilo de cabecera de 01–07 (mismo frontmatter de proyecto y numeración de secciones); `docs/README.md` INTACTO (disyuntiva «en /docs **o** README operativo» del done-when satisfecha por la primera rama). Secciones:
1. **Objetivo y alcance** (P1 Tailscale operativa, P2 TLS documentado sin implementar — TRD §6.8 citado).
2. **Estado de partida (C9)**: VM 108 «projectlog» (Debian 12.15, eth0 192.168.1.58/24 — FB-0), compose 3/3 healthy por 8080, firewall nftables GATE #11 (solo SSH-LAN + 8080), Tailscale SSH ya operativo como ruta de gestión (`root@100.64.3.17` vía ProxyJump).
3. **Acceso remoto Tailscale (P1 del PRD)**: instalación de Tailscale en la VM (apt + `tailscale up`), IP de tailnet, SSH de gestión, **acceso al panel por el MISMO puerto 8080 con cero cambios en la Compose** (literal §6.8), nota de firewall: la regla 8080-solo-LAT NO bastaría desde la tailnet para rutas no-tunnelizadas — Tailscale SSH/tunnel opera a nivel de kernel fuera de nftables-LAT según lo observado en FB-0; regla 8080 por tailnet SOLO si el usuario la pide.
4. **CORS-LAN — condicionada (a+), pendiente de RATIFICACIÓN del usuario**: qué se verá hoy (403 en `/ws` y API desde `192.168.1.58:8080`), que el backend YA soporta lista por coma en `FRONTEND_ORIGIN` (C10, latente), y el paso de deploy futuro EXACTO (añadir `http://192.168.1.58:8080` al `.env` de la VM + `docker compose up -d backend`) marcado **«pendiente de ratificación»** — NO como hecho (REQ-C10-CORS-5/DOC-3).
5. **Proxy inverso con HTTPS (P2) — SIN implementar**: estructura ya prevista (1 proxy nginx delante + servicios internos + `/ws/logs` detrás con upgrade headers y `proxy_read/send_timeout 3600s` de `frontend/nginx.conf`), Let's Encrypt delante o TLS en el propio nginx, «nada del MVP se rompe» (literal).
6. **Referencias**: docs/02 §2.3/§2.5/§6.8, compose.yaml, frontend/nginx.conf, state.md C9 FB-0, GATE #11.

## 3. Secuencias críticas

### 3.1 Borrado de proyecto end-to-end (flujo 8a, G2 + G6)

```
Ficha /proyectos/:id → «Eliminar definitivamente»
 → modal 8a (V10): input obligatorio; coincideLiteralConTrim(dto.name, texto) — texto TAL CUAL viaja al click
 → alConfirmar(texto) → eliminarProyecto(id, texto)
 → GET sesion Bearer + header X-Confirm-Name: <texto>
 → cadena 2 autentica (401 solo si sesión expirada — interceptor desloguea, correcto)
 → Controller: (1) ¿X-Confirm-Name presente? — 400 confirm_required sin BD
              (2) findById — vacío ⇒ 404 not_found «No existe (quizás fue eliminado)»
              (3) name EXACTO? — no ⇒ 400 confirm_mismatch «La confirmación no coincide» (modal abierto, reintento)
               (4) DELETE FROM projects WHERE id = ? → PG borra logs por logs_project_fk CASCADE
                   (misma transacción, key muere con la fila) → update==0 ⇒ 404 de carrera
 → 204 SIN cuerpo → panel.ts Promise<void> → setConfirmacion(null)
 → navegar("/proyectos", {state:{aviso:"Proyecto y sus logs eliminados definitivamente"}})
 → /proyectos: banner + GET /projects?include=summary → la tarjeta ya no existe
 → /logs en el próximo refetch: sin sus logs (flujo 8a paso 3); WS: sin frames (D-C10-2)
```

### 3.2 Purga con N (flujo 8c, M4 + desambiguación)

```
modal 8c → alConfirmar(texto) → purgarLogs(id, texto) → Controller (orden D-C10-1)
 → DELETE FROM logs WHERE project_id = ? → n = retorno de filas (JdbcTemplate.update)
 → n > 0 ⇒ 200 {"deleted": n}  (LinkedHashMap, primer cuerpo de un DELETE del proyecto)
 → n == 0 ⇒ re-verificar findById: presente ⇒ 200 {"deleted": 0} (M4, estado vacío); vacío ⇒ 404 (nunca 200 sobre inexistente)
 → ficha: banner "{n} logs eliminados; el proyecto y su key siguen intactos" (N REAL del cuerpo)
 → cargar() refresca DTO + resumen → bloque "sin logs todavía" (total 0, last_log_at null)
 → WS abierto: delta frames 0 (LiveBroadcaster solo emite con ingesta) — la próxima consulta arranca vacía
 → ingesta posterior con la MISMA key ⇒ 202 {accepted: N} (key sobrevive, literal §3.3)
```

### 3.3 Timeout CONNECTING del WS (M9/G3-a)

```
abrir() → new WebSocket(url) → this.socket = socket
 → reloj.setTimeout(apertura, T_TIMEOUT_CONNECTING_MS=10_000)   [socket capturado en la closure]
 CAMINO SANO:  onopen → clearTimeout(apertura) → abiertoAlgunaVez=true; intento=0
                       → alEstado("conectado", 0) → enviarFiltros()
 CAMINO HANG (socket de la cadena, intento N≥1): a los 10 s, readyState===CONNECTING
                       → socket.close() → onclose → alCierre():
                         cancelarTimerApertura(); abiertoAlgunaVez==true
                         → intento+=1 → alEstado("desconectado", N+1) → ("reconectando", N+1)
                         → timer backoff (1 s→30 s+jitter) → abrir() …
                         [restauración del backend ⇒ onopen ⇒ banner retirado, set_filters reenviado, refetch pág. 1]
 CAMINO HANG (socket INAUGURAL de la sesión): onclose → alCierre(): abiertoAlgunaVez==false
                         → alCierreInaugural() → me() → 200: reconectar() / 401: interceptor
                         → intento NO crece  [distinción EXACTA de M9]
 DOBLE-CLOSE: close real con timer corriendo ⇒ alCierre anula timerApertura;
              si el disparo tardío ocurre, readyState≠CONNECTING ⇒ no-op (inofensivo ×2)
 cerrar() (desmonte) ⇒ detenido=true + clearTimeout(apertura) + clearTimeout(timer) + socket.close(1000)
```

## 4. File Changes (exhaustivo)

| # | Fichero (absoluto) | Acción | Contenido |
|---|---|---|---|
| 1 | `backend/src/main/java/com/projectlog/projects/controller/ProjectsController.java` | EXTENDE | D-C10-1: `DELETE /{id}`, `DELETE /{id}/logs`, `GET /{id}/summary`; `include` en listar + D-C10-5; constants confirm_required/confirm_mismatch + mensajes; javadoc vocabulario actualizado |
| 2 | `backend/src/main/java/com/projectlog/projects/dto/SummaryDto.java` | NUEVO | record §3.3 (D-C10-4) |
| 3 | `backend/src/main/java/com/projectlog/projects/dto/ProjectDto.java` | EXTENDE | 9.º componente `summary` con `@JsonInclude(NON_NULL)` + ctor 8-arg + `conResumen(...)` (D-C10-4); javadoc C10 |
| 4 | `backend/src/main/java/com/projectlog/projects/repository/ProjectsRepository.java` | EXTENDE | `SQL_BORRAR` package-private + `borrar(long): boolean` (D-C10-2) |
| 5 | `backend/src/main/java/com/projectlog/query/repository/LogsBorradoRepository.java` | NUEVO | `SQL_DELETE_POR_ID`/`SQL_DELETE_POR_PROYECTO` + `borrarPorId/purgarPorProyecto` (D-C10-2) |
| 6 | `backend/src/main/java/com/projectlog/query/repository/LogsQueryRepository.java` | EXTENDE | 3 `SELECT_RESUMEN_*` + `resumen(long): SummaryDto` (D-C10-3) |
| 7 | `backend/src/main/java/com/projectlog/query/controller/LogsController.java` | EXTENDE | `DELETE /{id}` con fail-fast sin BD (D-C10-1, orden 8b) |
| 8 | `backend/src/main/java/com/projectlog/config/CorsConfig.java` | EXTENDE | split por coma + trim + filtrado (D-C10-6); comentarios G1-C2 intactos |
| 9 | `backend/src/test/java/com/projectlog/projects/ProjectsControllerMvcTest.java` | EXTENDE | ~14 @Test nuevos (§5) |
| 10 | `backend/src/test/java/com/projectlog/query/controller/LogsControllerMvcTest.java` | EXTENDE | ~7 @Test nuevos (§5) |
| 11 | `backend/src/test/java/com/projectlog/projects/dto/ProjectDtoSerializationTest.java` | EXTENDE | summary ausente/presente + SummaryDto exacto; comentar la línea «sin summary (C10)» obsoleta |
| 12 | `backend/src/test/java/com/projectlog/projects/repository/ProjectsBorradoSqlTest.java` | NUEVO | pin D-9 de `SQL_BORRAR` |
| 13 | `backend/src/test/java/com/projectlog/query/repository/LogsBorradoSqlTest.java` | NUEVO | pin D-9 de los 2 DELETE |
| 14 | `backend/src/test/java/com/projectlog/query/repository/LogsQuerySqlTest.java` | EXTENDE | pins de los 3 `SELECT_RESUMEN_*` (GROUP BY, CASE de orden, MAX) |
| 15 | `backend/src/test/java/com/projectlog/config/CorsConfigTest.java` | EXTENDE | 5 casos de D-C10-6 |
| 16 | `frontend/src/api/types.ts` | EXTENDE | `SummaryDto`, `ProjectDto.summary?`, mapa tipado `Partial<Record<Nivel/Entorno, number>>`; comentario R8.18 actualizado |
| 17 | `frontend/src/api/panel.ts` | EXTENDE | retornos `Promise<void>` (8a/8b) y `Promise<{deleted:number}>` (8c); `listaProyectosConResumen()`; `resumenProyecto(id)` |
| 18 | `frontend/src/ws/client.ts` | EXTENDE | `T_TIMEOUT_CONNECTING_MS` + `timerApertura` + guard/cancelaciones (D-C10-7); resto del ciclo intacto |
| 19 | `frontend/src/hooks/useLogsStream.ts` | EXTENDE | guard `debeReenviarFiltros` por clave (D-C10-12) |
| 20 | `frontend/src/hooks/useAuth.ts` | EXTENDE | `destinoTrasLogin` + `elNavigateGana` helpers puros (D-C10-11) |
| 21 | `frontend/src/pages/Login.tsx` | EXTENDE | `destinoPropio` ref + render condicional via `elNavigateGana` (D-C10-11) |
| 22 | `frontend/src/pages/Logs.tsx` | EXTENDE | fix doble «?» en `alVerDetalle` (D-C10-10); `useMemo` de `toSetFilters` (D-C10-12); banner `aviso` de state para el regreso de 8b |
| 23 | `frontend/src/pages/LogDetalle.tsx` | EXTENDE | fix doble «?» en los 2 «Volver» (D-C10-10); prop `alExito` → aviso + `navegar("/logs"+search)` (D-C10-8) |
| 24 | `frontend/src/components/ModalBorrado8b.tsx` | EXTENDE | prop opcional `alExito: () => void` tras 204; guard de error intacto |
| 25 | `frontend/src/pages/ProyectoDetalle.tsx` | EXTENDE | D-C10-8: `alConfirmar(texto)`, post-8a/8c/404, state `avisoAccion`, resumen en `cargar()`; comentarios «hoy 405/404» reescritos |
| 26 | `frontend/src/pages/Proyectos.tsx` | EXTENDE | `listaProyectosConResumen`, banner `aviso`, botón «Actualizar», literal de cabecera |
| 27 | `frontend/src/components/TarjetaProyecto.tsx` | EXTENDE | franja de resumen (variante "franja") solo con `summary` presente; comentario R8.18 reescrito |
| 28 | `frontend/src/components/ResumenLogs.tsx` | NUEVO | bandas badges + totals + estado vacío (D-C10-9), compartido |
| 29 | `frontend/src/ws/client.test.ts` | EXTENDE | 6+ tests del timeout CONNECTING y semántica M9 (§5) |
| 30 | `frontend/src/hooks/storeListado.test.ts` | EXTENDE | pin de contrato de search EXACTA con `?` inicial (D-C10-10) |
| 31 | `frontend/src/hooks/useLogFilters.test.ts` o nuevo `hooks/useLogsStream.helpers.test.ts` | EXTENDE/NUEVO | tests de `debeReenviarFiltros` (D-C10-12) |
| 32 | `frontend/src/hooks/useAuth.test.ts` | NUEVO | tests de `destinoTrasLogin` + `elNavigateGana` (D-C10-11) |
| 33 | `frontend/src/api/client.test.ts` | EXTENDE | parser: 200 `{deleted:N}` en DELETE y 204 → void |
| 34 | `docs/08_ACCESO_REMOTO.md` | NUEVO | esqueleto D-C10-14 |
| 35 | `scripts/qa_vis_c10.py` | NUEVO | arnés C10 (D-C10-13) |
| 36 | `scripts/qa_vis_c10.sh` | NUEVO | wrapper RAW + grep S16 |
| 37 | `scripts/qa_c10_smoke.sh` | NUEVO | smoke SQL REAL: purga con N, CASCADE verificado (count logs → delete project → count 0), key muere (`api_key_invalid`), ingesta 202 post-purga, resumen con datos reales |

**NO se tocan**: `SecurityConfig`, `JwtAuthFilter`, `client.ts` (frontend), interceptor 401 (G6), `ingest/**`, `streaming/**`, `frontend/nginx.conf`, `compose.yaml`, `docs/README.md`, VM 108 y su `.env`, `scripts/qa_vis_c9.py` (bit a bit), `scripts/qa_c*_smoke.sh` previos, `storeListado.ts`, `App.tsx`.

## 5. Plan de tests (REQ → cobertura)

| REQ | Test | Qué pina |
|---|---|---|
| DEL-1 | `ProjectsControllerMvcTest` | 204 sin cuerpo + `verify(borrar)`, id correcto |
| DEL-2/3 (8a) | MvcTest | 400 `confirm_required`/`confirm_mismatch`, cuerpo `{code,message}` sin field, repo sin invocar en el 400 |
| DEL-4 + M10 | MvcTest | id inexistente sin cabecera → 400; con cabecera errónea → 404; con cabecera correcta → 404 (pin completo del orden D-C10-1) |
| DEL-5 | `ProjectsBorradoSqlTest` + `qa_c10_smoke.sh` | literal `DELETE FROM projects WHERE id = ?` + CASCADE real (logs→0, key muere: ingesta 401 `api_key_invalid`) |
| DEL-6 | MvcTest | ARCHIVED → 204 (sin rama de status, M5) |
| DEL-7 | MvcTest | 401 sin JWT en las 3 DELETE (cadena 2 del slice, SecurityConfig intocada) |
| LOGDEL-1..4 | `LogsControllerMvcTest` | 204; 400 required/mismatch (mismatch SIN BD — `verifyNoInteractions`); fail-fast 8b (id inexistente + desigual → 400); 404; solo `borrarPorId` tocado (LOGDEL-4) |
| PURGA-1 | MvcTest | 200 cuerpo EXACTO `{"deleted":55}` (Jackson) |
| PURGA-3/4 | MvcTest | 400 con ambos codes; 404 en inexistente (también vía carrera: purgar→0 + findById vacío → 404) |
| PURGA-5 | MvcTest | N=0 con proyecto presente → 200 `{"deleted":0}`; `LogsBorradoSqlTest` pin del literal |
| PURGA-2 / RESUMEN-7 | `qa_c10_smoke.sh` + arnés E10.x | key sobrevive (202), resumen a cero, delta WS 0 tras purga |
| RESUMEN-1/2 | MvcTest + `ProjectDtoSerializationTest` | include → summary en TODOS los elementos; sin include/POST/PATCH/GET id → clave AUSENTE |
| RESUMEN-3/4 | MvcTest + `LogsQuerySqlTest` | forma EXACTA 4 claves; solo presentes; total/last_log_at; 404; 401 |
| RESUMEN-5 | MvcTest | archivado con resumen idéntico |
| RESUMEN-6 | MvcTest | `include=otracosa` → 400 `filter_invalid_value` field=include, BD sin invocar |
| CORS-1..4 | `CorsConfigTest` | lista por coma, trim, una-origin-idéntica, cierre, rechazo, prohibiciones |
| WS-1/2/3 | `ws/client.test.ts` | T=10000 capturado del Reloj fake; vía cadena → `("reconectando", N creciente)`; vía inaugural → `alCierreInaugural` + intento 0; cancelación en onopen ANTES de enviarFiltros; doble-close no-op; cerrar() limpia timerApertura; retrasoReconexion/serializar/dedup intactos |
| E913-1/2 | `storeListado.test.ts` + re-run E9.13 | contrato search-exacta (`?` inicial; `??` → null); round-trip E8.35 real |
| E92-1/2 | `useAuth.test.ts` + re-run E9.2 | `destinoTrasLogin` y `elNavigateGana`; URL PERDURANDO en arnés |
| E910-1 | test helper D-C10-12 + re-run E9.10 | delta `aplicarFiltros` 1/0 por render |
| UI-1..8 / UX401-2/3 | arnés `qa_vis_c10.py` (E10.x) | doble confirmación UI+cabecera, mismatch no-borra/no-desloguea, banners con literales, resumen tarjeta/ficha, estado vacío, Actualizar, 400 sin logout, 401 real sí |
| QA-3 | suites completas | 177 Java + Vitest creciendo; pauta E3.22/pre-fase = N/A-post-D2; Q23 tolerado; smokes canónicos sin editar |
| QA-4/5 | `qa_vis_c10.py` | re-runs 4 FAIL honestos + checks nuevos; destructivos al final, siembra efímera propia, guard patrón `qa-c*` para limpieza de residuo |

## 6. Riesgos con reversibilidad

| # | Riesgo | Mitigación | Reversibilidad |
|---|---|---|---|
| R1 | Primer `200` con cuerpo en un DELETE y primer retorno de `JdbcTemplate.update` (gap 6) | pin D-9 + MvcTest del cuerpo EXACTO + smoke con N real (D-C10-2) | los 3 endpoints son aditivos: quitarlos no deja efectos laterales (el 401-falso de antes era el estado previo) |
| R2 | `ProjectDto` de 9 componentes rompe constructores/tests de C3–C8 | ctor comodín 8-arg delegando `null`; solo `FILA` podría usarlo — cero cambios en llamadas existentes; `@JsonInclude` es por-propiedad | 1 anotación + 1 componente: revertir = borrar el componente y la anotación |
| R3 | `summary` presente por accidente en POST/PATCH | NON_NULL por-propiedad + test `doesNotContain("summary")` en cada forma | idem |
| R4 | Semántica del intento en timeout (M9) mal distinguible | mecanismo delega en el branch existente `abiertoAlgunaVez` (sin flag nuevo); 2 Vías pinadas en Vitest (cadena crece / inaugural no) | fix aislado en `abrir()`/`alCierre()`: 1 campo + 1 constante, revertible sin tocar el backoff |
| R5 | CORS multi-origen activa de más (G1-C2) | split+trim+filtrado sin `*`/patterns; `CorsConfigTest` con los 5 casos; VM/.env intocables (latente) | 1 bloque de 6 líneas: revertir = restaurar `List.of(frontendOrigin)` |
| R6 | Checks destructivos destruyen siembra del arnés/usuario | destructivos AL FINAL, siembra efímera propia con prefix `qa-c10-`, limpieza de residuo con guard patrón `qa-c9-/qa-c10-` y skip-and-report si no matchea | re-siembra a pedido (state.md); jamás toca datos del usuario |
| R7 | `TarjetaProyecto` `<button>` íntegro | resumen = texto plano/spans sin interactividad (D-C10-9; R7 del proposal) | la franja es un componente con props opcionales: quitar → tarjeta C8 |
| R8 | denyAll-en-/error residual (DEV-C8-4/M7) | ratificado G6: desaparece la RAÍZ para los DELETE; el residual queda para rutas ajenas — sin toque | n/a (sin código) |
| R9 | Doble navegación de Login tiene más vías (index-redirect de App) | fix en la única carrera instrumentada (Login.tsx vs `navegar(destino)`); E9.2 endurecido observando la URL PERDURANDO | helpers puros + 1 ref: revertir = restaurar el render condicional |
| R10 | Guard de `useLogsStream` enmascara reenvíos legítimos | la clave compara CONTENIDO (6 claves fijas en orden literal) — cualquier cambio real produce clase distinta; el reenvío de reconexión vive en `onopen`, fuera del guard | 6 líneas en un efecto; revertir = quitar el guard |
| R11 | Import de `qa_vis_c9.py` resultara sucio en apply | decisión binaria en head del script: import+rebaso o copia controlada citada; `qa_vis_c9.py` no se toca en ningún caso | el fallback copia-controlada está definido (G5) |
| R12 | Scope creep (backups/retención/TLS real/Fase 3) | TLS P2 SOLO documentado; VM intocable; retención/particionado fuera (TRD) | n/a |