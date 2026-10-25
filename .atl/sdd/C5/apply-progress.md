---
change: C5
artifact: apply-progress
last_updated: 2026-10-25
status: done — T5.5 cerrado con ratificaciones de gate (D-13/D-14/D-15)
---

# Apply progress — C5 · Ingesta REST

Estado por tarea (T5.1–T5.20). Este fichero se mantiene durante el apply.

| Tarea | Estado | Notas |
|---|---|---|
| T5.1 | **hecho** | findByApiKeyHash aditivo (+28 líneas; mvn test-compile OK en builder podman; git diff = solo adiciones) |
| T5.2 | **hecho** | IngestPrincipal creado |
| T5.3 | **hecho** | IngestAuthenticationEntryPoint creado (réplica RestAuthenticationEntryPoint) |
| T5.4 | **hecho** | IngestGuardFilter (barreras 1+2, auth, D-5 RuntimeException anidada, D-6 normalizador, D-8 shouldNotFilter); compila en builder |
| T5.5 | **hecho (cerrado con RATIFICACIÓN de gate)** | suite C1–C4 77/77 VERDES sin tocar un assert (log `/tmp/mvn_c5_full5.log`): 2 ratificaciones D-14/D-15 en design §2.1 — los 2 beans de ingesta entran a `ingestChain` como `ObjectProvider` (delegantes LAZY por lambda: `@WebMvcTest` incluye Filter pero NO AuthenticationEntryPoint) y el ctor del guard lleva `ObjectProvider<ApiKeyService>`/`<ProjectsRepository>` resueltos en `doFilterInternal` |
| T5.6 | **hecho** | ProjectsIngestSqlTest 3/3 verde (SELECT re-pineado package-private a ratificar; D-9) |
| T5.7 | **hecho** | LogIngestRequest (8 campos, desdeJson patrón C3) |
| T5.8 | **hecho** | LogValidado (moneda del INSERT) |
| T5.9 | **hecho** | LogsRepository (3 constants pinned + @Transactional única; compila) |
| T5.10 | **hecho** | LogsRepoSqlTest 4/4 verde (corregido assert substr "id,"→" id": "project_id," contenía "id,") |
| T5.11 | **hecho** | IngestListener (Javadoc design §10) |
| T5.12 | **hecho** | IngestService (una pasada validar+materializar: UN parse, UNA serialización S5-8; constants D-7 Decisión T5-1; compila; verificado por T5.15/T5.14) |
| T5.13 | **hecho** | IngestController (2 mappings, gates 0/3/lote, handler ilegibilidad por URI, 202; compila; verificado por T5.14) |
| T5.14 | **hecho** | IngestControllerMvcTest 16/16 verde (frontera metadata corregida: serializado = 12+len; ver § Log) |
| T5.15 | **hecho** | IngestServiceTest 3/3 verde (E5.18/E5.19/E5.20) |
| T5.16 | **hecho** | IngestGuardLimitTest 3/3 verde (E5.34 + default @Value por reflection — param types actualizados a ObjectProvider, gate D-14) |
| T5.17 | **hecho** | IngestLogHygieneTest 1/1 verde (ListAppender com.projectlog DEBUG) |
| T5.18 | **hecho** | qa_c5_smoke.sh (13 escenarios E5.21–E5.33); `bash -n` OK, shellcheck NO disponible; corrida canónica en T5.20. Desviaciones E2E (nginx 1MiB → ruta directa; bug `@` de req() corregido) en el Log |
| T5.19 | **hecho** | `mvn test` completo en builder: **Tests run: 107, Failures: 0, Errors: 0 — BUILD SUCCESS** (77 C1–C4 + 30 C5; log `/tmp/mvn_c5_full5.log`); intocables diff-0 ✓; ausencias ✓ |
| T5.20 | **hecho (corrida CANÓNICA run 5)** | E5.21–E5.33 **13/13 PASS**, exit 0, 0 FAIL/BLOQUEADO, pila 3×healthy, pgdata intacto (sin `down -v`) — log `/tmp/qa_c5_run5.log` [run 4 previo también 13/13] |

## Log

- 2026-10-25: arranque del apply. Lecturas obligatorias hechas (tasks/spec/design + código de referencia). Pila UP 3×healthy (snapshot inicial), `.env` presente.
- 2026-10-25: T5.12–T5.17 escritas y verdes en builder. Desviaciones menores documentadas:
  - `SELECT_POR_API_KEY_HASH` re-declarada **package-private** (el pin E5.15-b vive en el mismo paquete; D-9 sin reflection — el literal del spec/tasks decía `private`).
  - `MSG_API_KEY_INVALID` re-declarada **package-private** (el 401-honesto D-2 del controller REUTILIZA el literal sin duplicarlo; el spec/tasks decía `private`).
  - T5.13: la 202 se emite con `ResponseEntity.status(HttpStatus.ACCEPTED)` — el snippet del design §6/T5.13 decía `ResponseEntity.ok(...)` que es 200 y contradice R5.9/E5.1/E5.21 (202). Prevalece el contrato 202.
  - T5.12: el flujo quedó en UNA pasada por registro (`validarYMaterializar`): el timestamp se parsea una vez (check 2) y la metadata se serializa una vez (check 7) — el String alimentando medida y parámetro (S5-8/R-D3 exacto).
  - T5.14/armado: además de lo declarado en design §13 hace falta `@Import(JwtAuthFilter, RestAuthenticationEntryPoint, CorsConfig)` + `@MockitoBean TokenService` (el constructor de SecurityConfig los exige); el 401 se aserta leyendo los bytes de la respuesta como UTF-8 (acentos) y comparando cuerpo byte-a-byte entre las 3 variantes (Map.of no tiene orden contractual).
  - T5.14/E5.9: frontera metadata = 12 + len del valor (no 8): `{"clave":"…"}` serializado.
  - T5.9/T5.10: assert substr corregido `"id,"` → `" id"`.
- 2026-10-25: T5.18 script escrito (`scripts/qa_c5_smoke.sh`) — smoke-of-the-smoke y corrida canónica en T5.20.
- 2026-10-25: T5.19 (mvn completo, builder, offline): `Tests run: 107, Failures: 0, Errors: 46` — los 46 errors son EXACTAMENTE las 6 clases documentadas del bloqueo T5.5 (PanelAuthMvcTest 6, ProjectsControllerMvcTest 25, ApiKeyControllerMvcTest 9, LoginCredentialsTest 4, LogHygieneTest 1, ApiKeyLogHygieneTest 1), assert-level 0; las 30 clases nuevas de C5 (16+3+3+1+4+3+... incl. Pins SQL) van verdes dentro de la misma corrida (107 = 77 + 30). El BUILD SUCCESS pleno exige la solución ObjectProvider a ratificar en gate.
- 2026-10-25: depuración del smoke (runs 1–2, 6–8 FAILs). Causa raíz REAL del run 1: bug del PROPIO harness — `req()` quitaba el `@` a `--data-binary "@file"` y curl enviaba la RUTA como cuerpo (~26 bytes) → todos los cuerpos @file (E5.22/23/27/30/32) llegaban ilegibles al backend → `batch_empty` / `environment_required` (los repros "manuales" pasaban porque usaban @). El backend C5 estaba BIEN. Corregido en req() (el @ llega a curl).
- 2026-10-25: desviación E2E documentada: `frontend/nginx.conf` (INTOCABLE) no define client_max_body_size → default nginx 1 MiB: todo cuerpo >1048576 muere en nginx con 413 HTML ANTES del backend. Los 3 cuerpos que exceden (E5.27 grande 1048577, E5.28 chunked, E5.33 grande) se envían DIRECTO al contenedor backend vía `req_directo` (curl efímero `curlimages/curl:8.11.0 --user 0:0` en la red de la pila, `http://backend:8080`, key via `-H @fichero` en tmpdir 700 — nunca en argv). El resto de escenarios sigue por nginx:8080 (la puerta real). E5.27 legal (1048576, barrera INCLUSIVA) SÍ pasa por nginx (nginx rechaza solo > límite).
- 2026-10-25: hallazgo del harness en pila (entrada 08:41 del jar, SIN catch) — ***REFUTADA como causa
  de E5.28***: `javap` de `target/classes` y del jar desplegado probó que el catch de barrera 2 SÍ está
  en ambos binarios. La entrada se retiene aquí como cronología del debug; la causa real está en las
  3 siguientes.
- 2026-10-25: **hallazgo E5.28 — CAUSA RAÍZ DEFINITIVA (bytecode + traza Tomcat)**: `FrameworkServlet.
  processRequest` (spring-webmvc 6.2.19; bytecode: tabla de excepciones `67–73 → target 123, Throwable`)
  ENVUELVE cualquier Throwable que no es ServletException/IOException en
  `jakarta.servlet.ServletException("Request processing failed: …", causa)` ANTES de que la pila
  regrese al guard. La `CuerpoDemasiadoGrandeException` (RuntimeException, D-5) lanzada durante el
  parse de Jackson NO cruza el catch de tipo exacto del guard: cruza su ENVOLTURA → el catch no
  dispara → escape hasta Tomcat → error dispatch `/error` → cadena 3 (`anyRequest().denyAll()`,
  unauthenticated) → 401 `invalid_token` en vez de 413. La doble registración global del guard (bean
  auto-registrado + miembro de la cadena 1) era real pero NO operativa en el escape (sidecar con el
  fix de registración ÚNICO seguía fallando con la misma traza). El mecanismo era INVISIBLE al
  deploy de CL (barrera 1 escribe el 413 ANTES de continuar) y a los mocks (`@WebMvcTest` standalone
  no pasa por `FrameworkServlet.processRequest` real) — solo E5.28 (chunked &gt;1MiB) lo expone.
- 2026-10-25: **fix E5.28** (2 piezas, SOLO código C5): (a) `SecurityConfig` — bean
  `FilterRegistrationBean<IngestGuardFilter>` con `setEnabled(false)` (Javadoc nuevo; higiene: el
  guard queda miembro EXCLUSIVO de la cadena 1, patrón `JwtAuthFilter` de C2 intacto y sin tocar);
  (b) `IngestGuardFilter` — los 3 catches nuevos (`ServletException` / `IOException` /
  `RuntimeException`) con `contieneBarrera2(ex)` (búsqueda en cadena de causas, máx. 8 niveles) que
  escriben el MISMO 413 `{payload_too_large}` vía `escribir413SiToca` y, sin barrera 2 en la causa,
  re-lanzan la excepción INTACTA (cero enmascaramiento). El tipo de la excepción NO cambia
  (sigue RuntimeException, D-5); el catch directo se conserva (vía MockMvc/invocación directa).
  PROBADO en sidecar: CL small 202, CL grande 413, chunked small 202, **chunked 1048577 → 413
  payload_too_large** y 0 error dispatch («Securing POST /error» = 0). Tests C5: 30/30 verde.
- 2026-10-25: **T5.19 re-corrida** tras el fix: `Tests run: 107, Failures: 0, Errors: 46` — idéntica
  al estado documentado (46 = las 6 clases del bloqueo T5.5, assert-level 0). Intocables + ausencias
  verificados (detalle en tasks.md T5.19). Desviación a ratificar en gate: el bean-higiene
  `filtroGuardSoloCadena1` en SecurityConfig (fuera del alcance literal «diff solo en ingestChain +
  Javadocs») — es la solución mínima del hallazgo E2E E5.28.
- 2026-10-25: **T5.20 corrida CANÓNICA (run 4)**: `bash scripts/qa_c5_smoke.sh` exit 0 —
  **E5.21–E5.33 = 13/13 PASS** (incl. E5.21 fila exacta, E5.22 batch 200 + received_at único en orden,
  E5.23 all-or-nothing, E5.27 frontera inclusiva 1048576→202 / 1048577→413, **E5.28 chunked→413**,
  E5.31/G-2 cierre activo, E5.33 orden 413&gt;401). Pila 3×healthy al cierre, pgdata intacto.
  El corte C5 queda APLICADO con: T5.1–T5.4/T5.6–T5.18/T5.20 hechos; T5.5 y el BUILD SUCCESS pleno de
  T5.19 en decisión de gate (Solution ObjectProvider propuesta y a ratificar).
- 2026-10-25: **RATIFICACIÓN DE GATE recibida (orchestrator) — T5.5 CERRADO.** Se aplicó la
  solución propuesta: (1) `ObjectProvider<ApiKeyService>`/`<ProjectsRepository>` en el ctor de
  `IngestGuardFilter` (resolución LAZY en `doFilterInternal`, solo al request) y los 2 parámetros
  de `ingestChain` como `ObjectProvider` con delegantes LAZY por lambda (hallazgo de la 1.ª
  aplicación: `@WebMvcTest` incluye los beans `Filter` en su escaneo, pero NO los
  `AuthenticationEntryPoint`, así que `getObject()` a build-time estallaba en los slices —
  evidencia `NoSuchBeanDefinitionException: IngestAuthenticationEntryPoint` en `/tmp/mvn_c5_full3.log`);
  (2) bean-higiene `filtroGuardSoloCadena1` RATIFICADO (anotado como D-13 en design.md §2.1,
  juntamente con D-14=ObjectProvider y D-15=unwrap de FrameworkServlet). El 4.º test de
  `IngestGuardLimitTest` actualizado a los tipos ObjectProvider (assert del `@Value` idéntico).
- 2026-10-25: **T5.19 CERRADO — `mvn test` completo en builder: `Tests run: 107, Failures: 0,
  Errors: 0, BUILD SUCCESS`** (77 C1–C4 + 30 C5; log `/tmp/mvn_c5_full5.log`; la progresión
  intermedia fue 107/0/46 con `getObject()` a build → 107/0/1 con el test de reflection viejo →
  107/0/0 definitivo). Intocables diff-0 + ausencias ✓ (detalle en tasks.md T5.19).
- 2026-10-25: **T5.20 CERRADO — corrida CANÓNICA DEFINITIVA (run 5, ya con las ratificaciones
  aplicadas)**: `bash scripts/qa_c5_smoke.sh` exit 0, **E5.21–E5.33 = 13/13 PASS, 0 FAIL,
  0 BLOQUEADO**, pila 3×healthy tras recomposición ÚNICA `--build --force-recreate`, pgdata
  intacto (log `/tmp/qa_c5_run5.log`; run 4 pre-ratificación también 13/13). **Corte C5 APLICADO
  COMPLETO: T5.1–T5.20 todos [x]; status done.**

## RESUELTO T5.5 — conflicto estructural con los slices @WebMvcTest de C1–C4 (CERRADO con ratificación de gate 2026-10-25: ver D-13/D-14/D-15 en design.md §2.1)

La edición literal R5.6 se APLICÓ (cuerpo `ingestChain` EXACTO, constructor y
cadenas 2/3 byte-a-byte), pero la re-corrida anti-contaminación da **46
errores de carga de contexto** (no aserts rojos — assert-level: 0):

- `mvn test` tras el edit: `Tests run: 77, Failures: 0, Errors: 46` —
  fallan los slices `PanelAuthMvcTest` (6), `ProjectsControllerMvcTest` (25),
  `ApiKeyControllerMvcTest` (9), `LoginCredentialsTest` (4),
  `LogHygieneTest` (1), `ApiKeyLogHygieneTest` (1).
- **Causa 1 (con config revertida TAMBIÉN reproduce)**: `@WebMvcTest` NO
  excluye las clases `jakarta.servlet.Filter` de su escaneo (keep-filter
  propio del slice WebMvcTypeExcludeFilter) → `IngestGuardFilter` (@Component,
  implements Filter vía OncePerRequestFilter) es REGISTRADO y CONSTRUIDO en
  TODOS los slices @WebMvcTest del proyecto → su constructor exige
  `ApiKeyService`/`ProjectsRepository` → `NoSuchBeanDefinitionException:
  No qualifying bean of type 'com.projectlog.keys.ApiKeyService'`. En C1–C4
  esto no pasaba porque `JwtAuthFilter` depende solo de `TokenService`, que
  TODOS los slices de C1–C4 mockean. Evidencia: con `SecurityConfig`
  REVERTIDO al estado C1–C4, `mvn test -Dtest=PanelAuthMvcTest` sigue rojo
  con EXACTAMENTE ese error (la cadena editada NO interviene).
- **Causa 2 (con el edit R5.6 intacto)**: los 2 beans nuevos entran como
  PARÁMETROS del método `@Bean ingestChain` → todo contexto que importe
  `SecurityConfig` sin tener esos beans (todos los slices C1–C4 salvo los
  que mockean ApiKeyService+ProjectsRepository) falla en
  `IngestAuthenticationEntryPoint`: `No qualifying bean of type
  'com.projectlog.ingest.IngestAuthenticationEntryPoint'` → error 9 de
  ApiKeyControllerMvcTest (esa clase SÍ mockea ApiKeyService/ProjectsRepository).

**Por qué no lo resuelvo yo**: sin tocar tests C1–C4 (intocables, R5.19) las
únicas salidas son reescribir firmas de contrato
(`ObjectProvider<...>` en el ctor del guard y/o en los 2 parámetros del bean
`ingestChain`, o `@Nullable`) — eso es decisión del gate, no del apply.

**Estado del árbol**: el edit R5.6 LITERAL PERMANECE (necesario para T5.14 y
el smoke E2E; es el end-state del spec). Los errores C1–C4 quedan documentados
como bloqueo, no como asserts relajados. NOTA: la solución de menor
invasión (a ratificar en gate) = `ObjectProvider<ApiKeyService>` /
`ObjectProvider<ProjectsRepository>` en el ctor del guard +
`ObjectProvider<IngestGuardFilter>` / `ObjectProvider<IngestAuthenticationEntryPoint>`
en los 2 parámetros de `ingestChain` (resolución VACÍA en los slices de test,
idéntica en prod; solo cambian los TIPOS de los parámetros, el cuerpo/salida
es literal). Complemento equivalente: `@MockitoBean` extra en los slices
C1–C4 (tocar tests de C1–C4 — el otro extremo).

## REMEDIACIÓN QA (loop-back 2026-10-25) — 3 grupos CERRADOS

Entrada: loop-back de qa-run al agente sdd-apply (3 grupos de remediación
ANTES de la verify). Estado: TODOS ejecutados y en verde.

### Grupo 1 — Higiene del smoke (hecho)
- 1a: `scripts/qa_c5_smoke.sh` remediado — eliminadas las líneas que
  copiaban `req_debug.log`/`curl_err.log` fuera del tmpdir 700 (antes del
  bloque «VEREDICTOS QA C5»); ahora un comentario docuenta que NINGÚN dump
  de request sale del tmpdir (`bash -n` PARSE-OK). No se tocó nada más del
  script (los cuerpos/veredictos byte-a-byte intactos).
- 1b: purgadas las copias con secretos: `/tmp/qa_c5_req_debug.log`
  (JWTs de login + keys de `/regenerate` de runs previos),
  `/tmp/qa_c5_curl_err.log`, `/tmp/dbgkey.txt`. Verificado con grep que
  los logs de evidencia (`/tmp/qa_c5_run4/5/6.log`, `/tmp/sidecar*.log`)
  NO contienen `plk_` ni `eyJ` (0 hits; los tmpdirs del smoke viven y
  mueren en 700 con trap).
- 1c: keys vivas del smoke REVOCADAS por el DELETE canónico
  (login `/api/v1/panel/auth/login` + JWT en tmpdir 700 + `DELETE
  /api/v1/panel/projects/<id>/api-key`): proyectos 42, 44, 45, 46, 47, 48
  (del smoke) → TODAS 204; postgres verifica el trío NULL
  (`api_key_hash IS NULL AND api_key_prefix IS NULL AND key_created_at IS
  NULL` = `t` — regla 11). Revocación VIABLE, sin nota para C9. Tras el
  run 6 se revocaron las 2 keys que quedaron vivas: proyecto 49 (nuevo
  smoke) y 43 (`debug-c5-manual`, legado) → ambas 204 y `t` en psql.

### Grupo 2 — Cobertura [L] barata (hecho: QP-EX-1 COMPLETO, QP-12 COMPLETO, QP-7/9 COMPLETOS)
- QP-EX-1 (hueco S5-2): método NUEVO
  `camposDeConfianzaFalsificadosPorElClienteIgnorados` en
  `IngestServiceTest` — JSON con `id`/`project_id`/`received_at`
  falsificados (999 / `1999-01-01T00:00:00Z`) + variantes camelCase →
  `LogIngestRequest.desdeJson` → asertos: el record porta SOLO sus 8
  componentes literales (pin por reflexión de `getRecordComponents`),
  `isEqualTo` el registro válido; captor del `insert` probando que
  `project_id` == el del parámetro/principal (7, JAMÁS el 999) y que
  `received_at` es side-server (isAfter 2026-01-01, JAMÁS el falsificado
  de 1999) y la fila == `filaDe(leido)`. IngestServiceTest: 4/4 verde.
- QP-12: COMPLETO (era trivial) — en
  `ingestIndividualResponde202Accepted1Exacto` se añadió
  `verify(apiKeyService).sha256(KEY_FIXTURE)`: el digest SIEMPRE del
  header COMPLETO del test (con prefijo, sin recortes).
- QP-7/QP-9: COMPLETOS (eran triviales) — asertos del mensaje compuesto
  de `field_too_long` en las 5 fronteras 400 del
  `IngestControllerMvcTest`: «El campo message no puede superar 32768
  bytes», exception 131072 bytes, metadata 65536 bytes, source y
  correlation_id 200 caracteres (los literales exactos de `msgLimite`).
  MVC: 16/16 verde.

### Grupo 3 — Validación final (hecho)
- Suite completa en builder (comando canónico, `mvn -o test`):
  `Tests run: 108, Failures: 0, Errors: 0, Skipped: 0` — **BUILD
  SUCCESS** (107 → 108 por el método QP-EX-1). Evidencia RAW:
  `/tmp/mvn_c5_full6.log` (MVN6_EXIT=0). Desglose: MVC 16, GuardLimit 3,
  LogHygiene 1, Service 4 (EX-1 incluido), LogsRepoSql 4,
  ProjectsIngestSql 3 → C5 = 31 [L] nuevos; heredados C1–C4 = 77/77.
- Corrida canónica NUEVA del smoke con el script remediado (run 6):
  `bash scripts/qa_c5_smoke.sh` exit 0 — **E5.21–E5.33 = 13/13 PASS, 0
  FAIL, 0 BLOQUEADO**, pila 3×healthy tras recomposición
  `--build --force-recreate`, pgdata intacto. Evidencia RAW:
  `/tmp/qa_c5_run6.log` (RUN6_EXIT=0). Sin dumps fuera del tmpdir
  (verificado).
- Artefactos actualizados: tasks.md (notas QP-EX-1/QP-12/QP-7/9 en
  T5.15/T5.14), tests-written.md (mapeo: IngestServiceTest 4 tests con
  método propio para QP-EX-1; flags §5.1 marcados resueltos),
  design/tasks/apply-progress ya anotaban las ratificaciones de gate.
- NO tocados: qa-log.txt, qa-report.md, spec.md, design.md (salvo la
  sección de ratificaciones ya cerrada), código de feature (grupo 2 fue
  SOLO tests del propio corte).

### Próximo paso recomendado
Re-corrida FORMAL de qa-run (qp → qa-log/qa-report) → verify del corte
C5 con los artefactos actualizados.