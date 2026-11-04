---
project: Project_Log
artifact: explore
last_updated: 2026-10-29
status: complete
change: C10 — Fase 2 (borrados, resumen, acceso remoto, fixes C9)
base_head: 27b62cb
---

# C10 · Explore — mapa de terreno (solo READS; cero código tocado)

## A) Backend

### A.1 ProjectsController y estilo actual de C3

`backend/src/main/java/com/projectlog/projects/controller/ProjectsController.java`:
`@RequestMapping("/api/v1/panel/projects")`, controller FINO sin capa de
servicio. Endpoints existentes: `GET ""` (con `status?` validado fail-fast
contra ACTIVE/ARCHIVED), `GET /{id}` (404 `not_found` + MSG_NOT_FOUND),
`POST` (validación manual de límites literales §3.3: name 1..200,
description ≤2000; 201 + DTO del RETURNING) y `PATCH /{id}` (semántica
parcial con `UpdateProjectRequest.desdeJson`, orden determinista
null→enum→longitudes, `{}`→200 idempotente).

El vocabulario de errores vive como constants del controller:
`field_required` · `field_too_long` · `filter_invalid_value` ·
`name_duplicate` (409, mensaje «Ya existe un proyecto con ese nombre») ·
`not_found` (404, mensaje «No existe (quizás fue eliminado)»).

**NO existe @ControllerAdvice en todo el proyecto** (grep: 0 hits). La
traducción de excepciones es por `@ExceptionHandler` LOCAL a cada controller:
ProjectsController tiene 2 (DuplicateKeyException → 409 `name_duplicate`
serviendo a POST y PATCH juntos; HttpMessageNotReadableException → 400
`field_required` sin field). Patrones a replicar: el cuerpo estándar §3.1
`{code, message, field?}` lo emite el helper `error()` privado de cada
controller (`LinkedHashMap` + `objectMapper.valueToTree` +
`MediaType.APPLICATION_JSON`). Tres variantes del helper ya existen: con
varargs `field` (C3/C6), sin field con 3 params exactos (C4, ApiKeyController).

El DELETE de proyecto tiene hueco natural en `ProjectsController`
(`@DeleteMapping("/{id}")`); el patrón DELETE ya existe en
`keys/ApiKeyController.java` (`DELETE /api/v1/panel/projects/{id}/api-key` →
204 SIN cuerpo · `false` → 404 cuerpo estándar) — ese es el estilo material.

### A.2 Esquema, FK CASCADE y tablas que referencian projects

`backend/src/main/resources/db/migration/V1__init.sql`:
- `logs.project_id` → `CONSTRAINT logs_project_fk FOREIGN KEY (project_id)
  REFERENCES projects(id) ON DELETE CASCADE` — **el CASCADE ya existe en
  esquema**; el borrado de proyecto es UNA sentencia
  `DELETE FROM projects WHERE id = ?` y PG purga sus logs en la misma
  transacción (no hay pasos intermedios: AppFlow flujo 8a paso 4).
- La ÚNICA tabla que referencia projects es `logs`. `users` no tiene FK.
  La API key NO es tabla aparte: columnas `api_key_hash` (BYTEA),
  `api_key_prefix`, `key_created_at` DENTRO de `projects` (invariante §2),
  con índice parcial único `projects_api_key_hash_idx` — al borrar la fila
  la key desaparece con ella (la ingesta pasa a 401 api_key_invalid
  automáticamente; nada que purgar).
- Índice que sirve a la purga: `logs_dashboard_idx (project_id,
  environment, level, received_at DESC, id DESC)` — un
  `DELETE FROM logs WHERE project_id = ?` lo recorre por su prefijo.

Patrón de escritura transaccional de C5 (`ingest/IngestService.java` +
`ingest/LogsRepository.java`): la ÚNICA `@Transactional` de escritura del
corte vive en `LogsRepository.insert` (mono-batch, COMMIT al retornar); el
service NO es transaccional (D-4) y valida TODO antes de tocar la BD. Para
C10: el DELETE de proyecto es mono-sentencia (el CASCADE es del motor) y la
purga también; ambas encajan en el patrón `@Transactional // defensa
documentada (R3.2): mono-sentencia` de `ProjectsRepository`. NO existe hoy
NINGÚN método delete en ningún repo (grep `delete`/`DELETE` en
ingest/LogsRepository: solo comentarios), ni borrado en lote, ni
delete-by-project — todo es SQL nuevo. El count de la purga sale del int de
retorno de `JdbcTemplate.update(...)` (patrón nuevo, sin precedente).

### A.3 GET /logs, include= y base para el SummaryDto

`query/controller/LogsController.java`: `GET /api/v1/panel/logs` toma los 8
params como `String` y los parsea en cadena de 8 checks fail-fast delegando
los 6 filtros a `FiltrosConsulta.parsear` (C7, única fuente ADR-7). **NO
existe hoy NINGÚN parámetro `include=` en ningún endpoint** (grep: 0 en
backend y en `api/panel.ts`; R8.18 de C8 lo dejó expresamente fuera: "sin
?include=summary en C8"). Añadirlo es aditivo: `@RequestParam(required=false)
String include` antes de tocar BD, con la misma disciplina fail-fast.

Dos shapes ya separados por diseño: `query/dto/LogDto.java` (11 componentes,
exception `String` + metadata `JsonNode` nullables SIEMPRE serializados) vs
`query/dto/LogListItemDto.java` (9, sin exception/metadata — imposibilidad
estructural de fuga). Para el resumen no existe ningún SQL agregado aún:
`query/repository/LogsQueryRepository.java` solo tiene SELECT_LISTADO (17
placeholders keyset) y SELECT_POR_IDS. El SummaryDto
`{"by_level": {...}, "by_environment": {...}, "total": N, "last_log_at": …}`
requiere SQL nuevo con GROUP BY sobre level/environment + `MAX(received_at)`
(sugerencia de terreno, no decisión: 2 GROUP BY separados o 2 estamentos;
`last_log_at` formateada con `LoginController.EXPIRES_AT_FORMATTER`,
patrón del RowMapper de ProjectsRepository). Proyecto sin logs → mapas
vacíos, total 0, `last_log_at: null` (estado vacío informativo, §3.3).

### A.4 CORS actual (fragmento EXACTO)

`config/CorsConfig.java`, `corsConfigurationSource()`:

```java
if (frontendOrigin == null || frontendOrigin.isBlank()) {
    // S26/E2.18: cero configuraciones registradas ⇒ ninguna respuesta
    // lleva cabeceras CORS ⇒ CORS cerrado por defecto.
    return source;
}
CorsConfiguration config = new CorsConfiguration();
// Nunca "*", nunca setAllowedOriginPatterns (G1): reflejo EXACTO del origen.
config.setAllowedOrigins(List.of(frontendOrigin));
config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
// X-Confirm-* reservado a C3 (P1); Authorization/Content-Type para el panel.
config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Confirm-*"));
config.setAllowCredentials(false);
config.setMaxAge(3600L);
```

Fuente del valor: `@Value("${projectlog.security.frontend-origin:}")` ←
`application.yml` línea 39 `frontend-origin: ${FRONTEND_ORIGIN:}` ←
`compose.yaml` línea 42 `FRONTEND_ORIGIN: ${FRONTEND_ORIGIN}`.
**UNA sola origin, reflejo EXACTO, sin patterns ni wildcard** (prohibidos por
G1). Ya existe `CorsConfigTest` (test del literal). La decisión LAN
multi-origen del GATE tocaría exactamente esa línea `setAllowedOrigins`
(p. ej. Set de valores parseados; NOTA: hay `DELETE` ya en allowedMethods —
nada que añadir).

### A.5 SecurityFilterChain y las DELETE bajo el panel

`config/SecurityConfig.java`: cadena 2 (`@Order(2)`) con
`securityMatcher("/api/v1/panel/**")` + ` JwtAuthFilter` +
`anyRequest().authenticated()` (sin hasRole, ADR-14; login público EXACTO
POST /auth/login). Las 3 DELETE nuevas viven bajo `/api/v1/panel/...`, así
que quedan cubiertas por la cadena 2 **sin tocar SecurityConfig ni añadir
antMatcher** (rutas: `DELETE /api/v1/panel/projects/{id}`,
`DELETE /api/v1/panel/logs/{id}`, `DELETE /api/v1/panel/projects/{id}/logs`).

Cadena 3 (`@Order(3)`, sin matcher): `permitAll` solo health/info/+`/ws/logs`,
`anyRequest().denyAll()` con `accessDeniedHandler(entryPoint)` — ese es el
DEV-C8-4: una ruta inexistente/método no mapeado cae a `/error` del
dispatcher y la cadena 3 responde 401 `invalid_token`. HOY los DELETE de C10
(405 de Spring en la ruta existente con otro método) terminan en 401
falso → el frontend desloguea (UX-401-logout). Al implementar los endpoints
el 401-falso desaparece para ellos; el comportamiento denyAll-en-/error
permanece para el resto (ratificado en C8, no es alcance salvo decisión).

### A.6 Literales CONTRACTO (transcripción tal cual — docs/05 §3.2/§3.3 + docs/03 flujo 8)

De §3.2 (tabla de endpoints):

| Método | Ruta | Request → Response (literal) |
|---|---|---|
| DELETE | `/api/v1/panel/projects/{id}` | header `X-Confirm-Name: <name>` → **204 · 400 · 401 · 404** |
| DELETE | `/api/v1/panel/logs/{id}` | header `X-Confirm-Log-Id: <id>` → **204 · 400 · 401 · 404** |
| DELETE | `/api/v1/panel/projects/{id}/logs` | header `X-Confirm-Name: <name>` → **200 `{deleted: N}` · 400 · 401 · 404** |
| GET | `/api/v1/panel/projects` | query `status?`, `include=summary?` → 200 `[ProjectDto]` |
| GET | `/api/v1/panel/projects/{id}/summary` | → 200 `{by_level, by_environment, total, last_log_at}` · 401 · 404 |

De §3.3: **SummaryDto**: `{"by_level": {"ERROR": 12, "WARN": 3},
"by_environment": {"production": 40}, "total": 55,
"last_log_at": "2026-10-16T22:31:04Z"}` — mapas con recuento por cada
nivel/entorno PRESENTE; `total` suma logs; `last_log_at` = máximo
`received_at` (`null` si no hay). Proyecto sin logs → total 0 y
`last_log_at: null`, no error. «El campo `summary` está **ausente** del DTO
salvo en `GET /projects` con `include=summary` (donde siempre viaja, nunca
vacío por defecto…)».

De §3.3 (doble confirmación, P1):

- Cabecera ausente → 400 `{"code": "confirm_required"}`; valor incorrecto →
  400 `{"code": "confirm_mismatch"}`; nada se borra. 404 si ya no existía.
- `DELETE /projects/{id}` con `X-Confirm-Name: <name>` (nombre EXACTO) → 204.
- `DELETE /logs/{id}` con `X-Confirm-Log-Id: <id>` → 204.
- `DELETE /projects/{id}/logs` con `X-Confirm-Name: <name>` → 200
  `{deleted: N}`; **conserva el proyecto y su API key**, que sigue aceptando ingesta.

De §3.1/§4 reglas: `DELETE /projects/{id}` es la única forma de borrar
definitivamente (archivar va SOLO por PATCH status, R3.16); regla 3 (FK
única CASCADE, sin huérfanos); regla 4 (log inmutable: los únicos
operaciones destructivas son los 3 DELETE con `X-Confirm-*`).

De AppFlow flujo 8 (UI, sin cambiar): 8a modal severo exige **escribir el
nombre exacto**; 204 → redirige a `/proyectos` con banner «Proyecto y sus
logs eliminados definitivamente»; 400 → «La confirmación no coincide» dentro
del modal sin cerrarlo; 404 → mensaje §5 y retorno a lista. 8b: sentencia
literal `DELETE /logs/<id> · header X-Confirm-Log-Id: <id>` (id REAL),
comparación con trim; 204 → «Registro eliminado» y regreso a `/logs`. 8c:
exige escribir el nombre; 200 `{deleted: N}` → banner «N logs eliminados; el
proyecto y su key siguen intactos»; 400/404 → §5.

## B) Frontend

### B.1 api/client.ts + api/panel.ts — DELETE con cabecera custom y UX-401

`client.ts` es el ÚNICO fetch (R8.13). `RequestOptions` ya soporta
`headers?: Record<string, string>` (comentario literal: «p. ej.
X-Confirm-Log-Id / X-Confirm-Name — S8-2») y método `DELETE`. Regla 401
(R8.14): el 401 **decide SOLO con status** y llama `interceptor401()`
(registrado por App.tsx vía `interceptar401` → descarta sesión + navega
/login con `state {motivo: "expirada"}`); única excepción login (bearer:false).

`panel.ts` ya trae las 3 funciones de C10 con cabeceras FORMADAS pero
`Promise<never>` (documentando que no hay happy path): `eliminarProyecto(id,
nombreConfirmado)` con `{ "X-Confirm-Name": nombreConfirmado }`,
`purgarLogs(id, nombreConfirmado)` idem, `eliminarLog(id, confirmacionId)`
con `{ "X-Confirm-Log-Id": String(confirmacionId) }`. Para C10 solo cambia
el tipo de retorno (`void` para los 204; `{deleted: number}` para la purga —
el parser tolerante de `finalizar()` ya devuelve el JSON en 200/201).

UX-401-logout (DEV-C8-4): HOY cada DELETE llega al modal como 401-falso
(405 → /error → denyAll → `invalid_token`) y el interceptor desloguea
ANTES de que el catch del modal lo vea. **La raíz se elimina implementando
los endpoints** (A.5); el interceptor queda CORRECTO para el 401 real de
sesión expirada (AppFlow §5). No se detectó ningún toque adicional
requerido en client.ts; verificación propuesta: los 400 de los DELETE deben
caer en el catch del modal y mostrarse SIN cerrarlo (la UI ya lo hace).

### B.2 ws/client.ts — timeout de fase CONNECTING (H-C9-WS-COLGADO)

`ClienteWsLogs.abrir()` no tiene NINGÚN timeout en CONNECTING: crea el
socket, asigna `onopen/onmessage/onclose/onerror` y espera; la reconexión es
100 % close-driven (`alCierre()` → `intento += 1` →
`retrasoReconexion(intento)` → timer → `abrir()`). Con el socket colgado en
CONNECTING (upstream envejecido) la cadena muere: nada dispara `onclose`.
**Punto de inserción exacto**: en `abrir()`, inmediatamente tras
`this.socket = socket` (línea ~152), programar `this.timer` con el Reloj
inyectado (ya existe `this.reloj`);
al disparar: si `socket.readyState === WebSocket.CONNECTING` ⇒
`socket.close()` ⇒ dispara `onclose` ⇒ entra por `alCierre()` ⇒ si
`abiertoAlgunaVez` ⇒ `intento += 1` y backoff normal. En `onopen` hay que
cancelar ese timer antes de `enviarFiltros()`. DISEÑO A DECIDIR: si el
socket jamás abrió (`abiertoAlguaVez === false`), `alCierre()` enruta a
`alCierreInaugural()` → `me()` → `reconectar()` (o muere con 401) — el
intento NO crece por esa vía; decidir si el timeout CONNECTING debe
producir `intento += 1` explícito o dejar la vía inaugural. Prueba del
intento N creciente: `ws/client.test.ts` ya tiene el describe «ClienteWsLogs
— ciclo sin red (WebSocket y location stubbed)» con WebSocket stub y Reloj
fake (`retrasoReconexion` además es función pura testable) — patrón listo
para simular CONNECTING eterno y asertar `alEstado("reconectando", n)`.

### B.3 E9.13 — doble «?» (3 puntos de código)

1. `pages/Logs.tsx` ~línea 322 (`alVerDetalle` en TablaLogs):
   `navegar(\`/logs/${id}${location.search ? \`?${location.search}\` : ""}\`)`
   — `location.search` YA incluye el «?» inicial ⇒ se genera
   `/logs/388??text=…`.
2. `pages/LogDetalle.tsx` líneas ~113 y ~139 (los DOS «Volver a logs»): mismo
   patrón `` `to={`/logs${location.search ? `?${location.search}` : ""}`}` ``.
3. `hooks/storeListado.ts::consumirListado(searchActual)`: compara
   `tomada.search === searchActual` (igualdad EXACTA de la query string
   guardada) ⇒ con «??» nunca coincide y la instantánea E8.35 se descarta.
   El fix es puramente textual en 1 y 2 (usar `location.search` sin el «?»
   extra); el store no necesita tocar (compara la search completa que
   viajará correcta). Tests: `hooks/storeListado.test.ts` y un test de
   round-trip en Vitest.

### B.4 E9.2 — Navigate condicional de Login pisa el destino

`pages/Login.tsx` líneas 31–33: `if (sesion.estado === "autenticado") {
return <Navigate to="/" replace />; }` — al resolver `sesion.login(...)`
(`hooks/useAuth.ts::login`, líneas 147–155: llama `borrarDestino()` y
`setEstado("autenticado")` ANTES de que el `await` retorne), el re-render
ejecuta ese `Navigate to="/"` y pisa el `navegar(destino ?? "/logs")` de la
línea 45 (App.tsx línea 102: index redirect `Navigate to="/logs"`). El
resultado: arribo real SIEMPRE `/logs` con destino guardado perdido
(repro×3 C9). Terreno de fix en proposal: opciones sobre esa rama
condicional (por p. ej. navegar ANTES de que el estado cambie el render, o
condicionar a «sin destino pendiente», o llevar el destino al state del
Navigate). Los módulos implicados y sus interacciones: Login.tsx,
useAuth.ts (borrarDestino() además corre antes de navegar — otro punto del
mismo flujo: la línea 45 ya leyó el destino en la variable `destino`, así
que borrarDestino() dentro de login() no rompe la variable local), App.tsx
(index redirect). Arnés: check E9.2 está en b1 del `qa_vis_c9.py`
(línea ~991: guard SIN token → /login con destino → arribo).

### B.5 E9.10 — toSetFilters interpolado en línea

`pages/Logs.tsx` línea 144: `filtros: toSetFilters(filtros)` llega a
`useLogsStream({…})` como objeto NUEVO por render (toSetFilters crea
objeto literal, `useLogFilters.ts` líneas 54–63). En `useLogsStream.ts`
líneas 89–93: `useEffect(…, [p.filtros])` llama
`clienteRef.current?.aplicarFiltros(p.filtros)` — con identidad cambiada en
cada render dispara `send(set_filters)` repetido ⇒ delta 3 frames por
evento (contradice R9.2.10 «UN set_filters»). **useMemo ya está importado
en Logs.tsx** (línea 9). Fix natural: `const filtrosSet = useMemo(() =>
toSetFilters(filtros), [filtros])` y pasar ese `filtrosSet` — la dependencia
correcta es `filtros`, que en `useFiltrosLogs` es ya `useMemo(…) [searchParams]`
(useLogFilters.ts líneas 112–115) con identidad estable entre renders. El
refetch de página 1 (Logs.tsx efecto `[..., filtros]` línea 123) NO se ve
afectado (mismo objeto memoizado).

### B.6 UI de borrados y resumen — lo que ya existe y lo que falta

- **Ficha `/proyectos/:id`** (`pages/ProyectoDetalle.tsx`): zona destructiva
  P1 COMPLETA (botones «Purgar logs» y «Eliminar definitivamente», con la
  sentencia mono `DELETE /projects/{id}/logs · X-Confirm-Name: {name}` pintada).
  Modal 8c (ModalPurga8c) y 8a (ModalEliminar8a) ya exigen el nombre EXACTO
  (`coincideLiteralConTrim` de `components/Modal.tsx`) y muestran el error
  dentro del modal sin cerrarlo.
  **GAP funcional**: `ejecutarConfirmado()` llama
  `purgarLogs(dto.id, dto.name)` y `eliminarProyecto(dto.id, dto.name)`
  (líneas 99/104) — envía al header el nombre REAL de la BD, no el texto
  tecleado: con C10, `confirm_mismatch` sería inalcanzable desde la UI y el
  guard del modal queda decorativo. Decisión del proposal: cablear `texto`
  de cada modal al header (cambiar la firma por confirmación, o elevar el
  input).
- **Detalle `/logs/:id`** (`pages/LogDetalle.tsx` + `components/ModalBorrado8b.tsx`):
  modal V11 completo con sentencia literal, trim, botón rojo condicionado y
  error literal dentro del modal; llama `eliminarLog(id, id)` — tras 204 el
  flujo de regreso a /logs es el punto a añadir (hoy `Promise<never>` nunca
  resuelve).
- **`/proyectos`** (`pages/Proyectos.tsx` + `components/TarjetaProyecto.tsx`):
  tarjetas con name/description/status/prefix/has_key; comentario literal
  «SIN resumen P1 (R8.18)». Insertar el block de SummaryDto (flujo 9):
  bloque inferior de la tarjeta tras la línea `border-t` (donde hoy van `#id`
  y `updated_at`) o en su propia franja; los recuentos WARN/ERROR/FATAL
  destacados (AppFlow Flujo 9 paso 3). NOTA CONTRATO: la tarjeta es un
  `<button>` íntegro — cualquier sub-estructura interactiva habría que
  repensarla (los números del resumen son texto plano: cabe sin conflicto).
- **Ficha**: bloque de resumen candidato en la columna lateral `aside` (tras
  «Estado de ingesta») o sección nueva bajo los datos del proyecto. La ficha
  llama `proyectoApi(idNum)` en `cargar()` (líneas 50–71) — punto para la
  llamada adicional `GET /projects/{id}/summary` (o para leer `summary` del
  DTO si se pide con include en ficha; §3.2 asigna el include SOLO a GET
  /projects lista).
- `api/types.ts` no declara `summary` («§3.3 — ProjectDto SIN summary (no
  declarado en C8)») — añadir SummaryDto y el campo opcional es tierra nueva.

## C) Arnés y tests

### C.1 Arnés visual C9

`scripts/qa_vis_c9.py` (2603 líneas): monolítico por funciones, Playwright
sync + chromium headless. Fases B0→B8 (`verificarPila`,
`verificarEntorno`, `detectarComposeBin` → main). **26 checks** numerados
E9.1–E9.26 con celdas `pareja(id)`/`passO(nota)`/`fail()`/`marca()` +
`sellarE911()` (veredicto cruzado); la fase B8 (`b8()`) imprime informe,
cifras RAW (Contadores/WsContador de peticiones/WS observadas por
interceptación de páginas) y el RESIDUO de siembra; `salirPorVeredictos()`
devuelve el exit canon (0 = 26 PASS, 1 = algún FAIL/BLOQUEADO, 2 = pila).
Lanzamiento: wrapper `scripts/qa_vis_c9.sh` (RAW teed a
`.atl/sdd/C9/qa-log.txt`, grep S16 de prefijos `plk_`/`eyJ` = 0 hits,
salida passthrough). Los 4 FAIL a re-run viven en:
- E9.2 → b1 (línea ~991, guard sin token → destino).
- E9.10 → b3 (línea ~1400, delta de set_filters con WsContador).
- E9.13 → b6 (línea ~2002, acumulado ≥60 con NEEDLE_E35).
- E9.11/E9.26 → repartidos b6 (a/c) y b7 (b), sello en `sellarE911()`
  (líneas 201–215) y ventana de kill del servicio `backend` (compose stop/up
  destructiva SOLO de ese servicio).
Para C10 (state.md: «ampliar qa_vis_c9.py, NO duplicar; evidencia en
`.atl/sdd/C10/evidencia/`», NO commitear): añadir checks = nuevas parejas
`pareja("E10.x")` + funciones/bloques b9; revisar el guard de 26 de b8 y la
nota de DIR_EVID (`.atl/sdd/C9/evidencia/` hardcodeada → C10). Los checks
destructivos de DELETE alteran la siembra: ordenar la fase nueva AL FINAL
(o tras re-siembra) para no matar los needles de E9.x si se re-run.

### C.2 Testing backend

Build: `backend/pom.xml` (Maven, Spring Boot parent 3.5.16, Java 21,
starter-test). Tests de C3: `backend/src/test/java/com/projectlog/projects/
ProjectsControllerMvcTest.java` (@WebMvcTest con MockMvc, ~25 @Test — sitio
natural para los tests de los 3 DELETE: 401 de la cadena vía slice, 400
confirm_required/confirm_mismatch, 404, 204/200, orden fail-fast). Tests de
SQL: pines JUnit PURO sin Spring ni BD
(`projects/repository/ProjectsIngestSqlTest.java` etc.: `assertThat(
CONSTANTE).isEqualTo(...)`, constants package-private — patrón D-9 para
pinear los literales DELETE FROM). No hay Testcontainers ni H2 ejecutable:
`application-test.yml` solo alimenta a StartupSecretsValidator de los
slices; la EJECUCIÓN real de SQL se certifica con los smokes canónicos
`scripts/qa_c*_smoke.sh` (patrón:MvcTest para HTTP + smoke para SQL con
pila). El corte AÑADIRÁ tests: MvcTest de las 3 DELETE + summary, pin SQL de
los DELETE/COUNT, quizá un test de `SummaryDto` serialización al lado de
`ProjectDtoSerializationTest`.

### C.3 Testing frontend

Vitest puro sin DOM (D-C8-11). 7 ficheros: `api/client.test.ts` (tolerancias
+ aserto de que el mock CRASHEA si se lee el body en 401), `ws/client.test.ts`
(retrasoReconexion, serializarSetFilters, parsearFrame, ciclo ClienteWsLogs
stubbed), `ws/dedup.test.ts`, `hooks/storeListado.test.ts`,
`hooks/useLogFilters.test.ts`, `hooks/usePagination.test.ts`,
`components/Modal.test.ts`. Canon hoy: 55 Vitest / 177 Java (state.md C10).
Tests de los fixes: doble-? en storeListado/useLogsStream (B.3/B.5),
Navigate de Login (necesita render — hoy los tests son lógicos sin DOM; el
test de E9.2 puede ir a nivel función/hook), CONNECTING timeout en
ws/client.test.ts (stub de WebSocket con readyState controlable).

## D) Acceso remoto — doc Tailscale + TLS (TRD §6.8)

TRD §6.2/§6.8 (literal): «**Acceso remoto Tailscale** (P1 del PRD): instalar
Tailscale en la VM y acceder al mismo puerto del frontend por la IP de la
tailnet; cero cambios en la Compose.» y «**Proxy inverso con HTTPS** (P2):
si hay dominio futuro, se añade el proxy con Let's Encrypt delante (o se
configura TLS en el propio nginx) — la estructura actual (1 proxy + servicios
internos + WS detrás) ya es la del diseño inverso; nada del MVP se rompe.»
`frontend/nginx.conf`: listen 80 sin TLS (ADR-23), SPA fallback + proxy
`/api/v1/` y `/ws/logs` con upgrade headers y read/send timeout 3600s.

Estado C9 de partida (.atl/sdd/C9/state.md): FB-0 PASS — «ssh
root@192.168.1.58 via ProxyJump (root@100.64.3.17, Tailscale SSH) →
ACCESO-OK, hostname=projectlog, eth0 UP 192.168.1.58/24, Debian 12.15»;
deploy vm108 3/3 healthy por 8080 desde la LAN; firewall nftables GATE #11.
Tailscale SSH ya opera como ruta de acceso a la VM; falta el doc OPERATIVO
(instalación/uso en la tailnet, acceso al puerto 8080/frontend por IP de
tailnet, notas TLS-P2).

**NO existe README operativo**: `docs/README.md` es el índice del paquete
de definición (estado del paquete + cómo navegar), no operatoria. Hueco
natural según elDone-when del slicing («el doc está en /docs o README
operativo»): un doc nuevo `docs/08_ACCESO_REMOTO.md` (seguiría la numeración
y el estilo de cabecera de 01–07) o una sección operativa nueva en
docs/README.md; propuesta debe elegir (recomendable doc separado por no
ensuciar el índice congelado del paquete).

## Gaps y riesgos (para proposal)

1. **Estados: 204, NO 200; 400, NO 409/422.** El alcance redacta «DELETE
   /projects/{id} → 200» y menciona «200/404/409/422», pero el CONTRATO
   (§3.2, §3.3, flujo 8) dice: DELETE proyecto → **204**; DELETE log →
   **204**; purga → **200 `{deleted: N}`**; confirmación ausente/incorrecta
   → **400** `confirm_required`/`confirm_mismatch`. 409 solo existe en
   name_duplicate (POST/PATCH) y project_archived (regen key); 422 NO
   existe en ningún lugar del Schema. El proposal debe ratificar 204/400 y
   señalar explícitamente la divergencia.
2. **Header con el nombre REAL, no el tecleado** (B.6): ProyectoDetalle pasa
   `dto.name` como `nombreConfirmado`; con C10 el `confirm_mismatch` sería
   imposible desde la UI → cablear el input del modal o ratificar la
   divergencia (el guard UI ya bloquea el botón).
3. **`summary` ausente vs record de Java**: §3.3 exige campo AUSENTE del
   ProjectDto salvo `include=summary` — el record `ProjectDto` fijo de 8
   componentes no lo puede expresar sin `@JsonInclude(NON_NULL)` sobre el
   componente o DTO/shape alternativo; decisión de diseño (afecta también a
   `api/types.ts` y TarjetaProyecto).
4. **SQL agregado nuevo**: no existe nada para by_level/by_environment/total/
   last_log_at (A.3); decidir nº de estamentos, dónde vive el repo (query vs
   projects) y el formateo de `last_log_at` (formatter C2 compartido).
5. **WS: intento por timeout CONNECTING**: la vía close-sin-open enrutaría a
   `alCierreInaugural()` (me()-driven) y no incrementaría `intento` (B.2) —
   definir semántica antes de parchear y su test.
6. **Purga `{deleted: N}`**: primer uso de `JdbcTemplate.update(...)` con
   retorno de filas en el proyecto (todo lo previo es query/RETURNING); el
   200 con cuerpo es también el primer DELETE con body de respuesta.
7. **CORS LAN**: una sola origin con reflejo exacto (G1 prohíbe `*` y
   patterns); la decisión multi-origen del GATE toca una línea de
   CorsConfig + CorsConfigTest (A.4), con la pregunta cerrada ya preparada
   en state.md C10.
8. **DEV-C8-4 residual**: implementar los DELETE elimina el 401-falso para
   ellos, pero el denyAll-en-/error queda para cualquier otro método no
   mapeado (no es alcance salvo decisión explícita).
9. **Arnés**: ampliar qa_vis_c9.py implica renumerar el guard de 26 checks
   y redirigir evidencia a `.atl/sdd/C10/evidencia/`; los checks destructivos
   deben correr al final para no destruir la siembra que E9.x necesita
   (residuo C9 conscientemente no limpiado — «la limpieza real son los
   DELETE de C10»: esa es exactamente la oportunidad).
10. **Streaming y borrados**: LiveBroadcaster solo emite desde ingesta; un
    log borrado no genera frame; la tabla/lista de la UI refresca por
    refetch propio (flujo 8a paso 3: «dejan de aparecer en /logs en el
    próximo refetch») — no se detectó acoplamiento WS↔DELETE a resolver.