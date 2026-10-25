---
change: C5
artifact: tasks
last_updated: 2026-10-25
status: draft
---

# Tasks — C5 · Ingesta REST

> Desglose fino y SECENCIAL del corte C5 a partir del spec (E5.1–E5.34,
> R5.1–R5.24, S5-1…S5-15, D-C5-1) y del design (D-1…D-12). Cada tarea lista
> ficheros concretos, criterio de cierre VERIFICABLE (test/comando),
> dependencias y trazabilidad. Sin front (C8), sin consulta (C6), sin
> streaming real (C7 — solo el seam), sin migraciones (V1 intocable).
>
> **Base de rutas**: todos los paths son relativos a la raíz del repo
> `/home/acuelop369/Escritorio/Proyectos/Project_Log`; el apply resuelve
> cada uno como ruta absoluta.

## Notas transversales (aplican a TODAS las tareas)

- **Entorno R-7 heredado C3/C4**: `mvn test` DENTRO de la imagen builder
  `maven:3.9-eclipse-temurin-21` (backend + `~/.m2` montados, sin BD);
  recomposición de pila SOLO con `podman compose up -d --build
  --force-recreate` (NUNCA `down -v` — el volumen `pgdata` conserva los
  proyectos de C1–C4); psql `-qAt`; sufijos por ejecución
  (`smoke-c5-<TS>-*`); **conteos RELATIVOS** (`count(*)` antes vs después —
  R-15); needles psql de `plk_*` con escape `\_` o `position()`/`=` (S-3);
  epoch contextual `FLOOR(EXTRACT(EPOCH …))::bigint`; booleanos `t/f` (psql)
  ≠ `true/false` (JSON).
- **Sin push; sin commits** (los hace sdd-apply/archive).
- **`pom.xml` CONGELADO** (R5.2): cero dependencias nuevas (sin
  starter-validation, sin Testcontainers, sin commons-io).
- **Higiene R5.15**: las clases nuevas de `com.projectlog.ingest` nacen SIN
  loggers; jamás loguear header `X-API-Key`, key (`plk_`) ni hash.
- **R-12 (413 sin drenar)**: si E5.27 manifestase conexión reiniciada sin
  cuerpo, el apply activa el plan B del spec §2.6 (drenar ACOTADO hasta
  `limite+1` antes de responder) SIN cambiar contrato (code y cuerpo
  idénticos) — decisión del smoke real, T5.18.

---

## DECISIÓN T5-1 — Ratificación de la divergencia D-7 (colocación de los constants de los 400)

R5.10/S5-13 agrupan "403/400 en `IngestController`", pero la VALIDACIÓN vive
en `IngestService` (R5.12, dueño del orden primer-fallo-gana R-9) y el gate
de lote en el CONTROLLER (R5.24-c, sobre el `JsonNode`). Mover un constants
de sitio respecto al design NO procede: **se RATIFICA el reparto del design
D-7 por PROPIETARIO del gate** (los 11 literales quedan BYTE-A-BYTE
idénticos a R5.10 — el spec no contradice en literales, solo en lugar de
residencia):

| Owner | Constants (+ sus `MSG_*`) |
|---|---|
| **`IngestService`** (los 6 de 400 por campo — es el escritor del CONTENIDO; el controller solo MATERIALIZA el `ErrorValidacion`) | `CODE_ENVIRONMENT_REQUIRED`, `CODE_TIMESTAMP_WITHOUT_TIMEZONE`, `CODE_LEVEL_INVALID`, `CODE_MESSAGE_REQUIRED`, `CODE_FIELD_TOO_LONG` (+ límites 32768/131072/65536/200 como `static final`), `CODE_METADATA_NOT_OBJECT` |
| **`IngestController`** (los 3 de SUS gates) | `CODE_PROJECT_ARCHIVED`, `CODE_BATCH_EMPTY`, `CODE_BATCH_TOO_LARGE` (mensaje compuesto con el límite efectivo) |
| `IngestAuthenticationEntryPoint` | `CODE_API_KEY_INVALID` (package-private — el controller lo REUTILIZA para su 401-honesto, D-2) |
| `IngestGuardFilter` | `CODE_PAYLOAD_TOO_LARGE` |

El service JAMÁS referencia `IngestController.CODE_*` (sería dependencia
service→controller, inversa a capas — eso es exactamente lo que D-7 evita).
Trazabilidad: R5.10, S5-13, D-7, R5.12, R5.24-c. Aplica en T5.12 y T5.13.

---

## Fase 1 — Beans de la cadena 1 (filtro + entry point + principal) y SecurityConfig: PRIMERA EDICIÓN desde C1 con red anti-contaminación corriendo YA

> La Fase 1 deja la cadena 1 viva (413→401 determinista) con el panel y las
> cadenas 2/3 demostrablemente intactos ANTES de escribir una sola línea de
> ingesta de datos. `IngestPrincipal` y el lookup B1 (T5.1) van aquí por
> COMPILABILIDAD: el guard (D-1) los referencia; el pin SQL del lookup es
> T5.6 (Fase 2) y el resto de la validación, T5.7 en adelante.

- [x] **T5.1 — `ProjectsRepository.findByApiKeyHash` (firma B1) — lookup aditivo.**
  ADELANTADA de la Fase 2 por compilabilidad del guard (dependencia D-1).
  - Fichero (`TOCADO`, PURAMENTE ADITIVO): `backend/src/main/java/com/projectlog/projects/repository/ProjectsRepository.java`.
  - Contenido (R5.11, R5.11-a, gate Punto 1): 1 constant
    `private static final String SELECT_POR_API_KEY_HASH = "SELECT " + COLS
    + " FROM projects WHERE api_key_hash = ?";` (reutiliza `COLS`/`FILA` —
    CD-3.2, UNA sola fuente; `NUNCA queryForObject` — patrón C4) + el
    método EXACTO del R5.11-a con `@Transactional` (defensa documentada,
    patrón R3.2): `public Optional<ProjectDto> findByApiKeyHash(byte[] hash)`
    → `jdbcTemplate.query(SELECT_POR_API_KEY_HASH, FILA, hash)`;
    `isEmpty() ? Optional.empty() : Optional.of(filas.get(0))`. Binding
    `byte[]` → varargs → driver PG `setBytes` → **bytea** (dirección de
    LECTURA R-2, certificada E2E por E5.21/E5.24).
  - Javadoc EXACTO del R5.11-a: cita del ancla C4 spec §7 y **declaración
    D-C5-1** (R5.16): la igualdad la resuelve el `WHERE` por igualdad — SIN
    `tiempoConstanteIgual` post-lookup (el `ProjectDto`/`COLS` no porta el
    hash; divergencia declarada y ratificada en gate 2026-10-25).
  - PROHIBIDO: tocar una línea de C3/C4 (los 2 UPDATE de C4 y los 4
    métodos de C3 byte-a-byte); `queryForObject`; DTO estrecho; re-hasheo.
  - Cierre: compila en builder (`mvn -q test-compile`, imagen
    `maven:3.9-eclipse-temurin-21`); `git diff` del fichero = SOLO
    adiciones; `ProjectsKeySqlTest` verde (pin heredado, R5.19a).
  - Deps: ninguna. Habilita: T5.4, T5.6; en pila E5.21, E5.24. Traz:
    R5.11, R5.11-a, R5.16 (D-C5-1), gate Punto 1.

- [x] **T5.2 — `ingest/IngestPrincipal.java` (record) — el proyecto viaja como `Authentication`.**
  - Fichero (NUEVO): `backend/src/main/java/com/projectlog/ingest/IngestPrincipal.java`.
  - Contenido (R5.6-a, D-2, B1 del gate Punto 1): `public record
    IngestPrincipal(long projectId, String status) {}` — análogo a
    `JwtPrincipal`; NADA más en el record (JAMÁS el hash, JAMÁS la key).
    El `status` es el dato del gate 3 (403).
  - Cierre: compila en builder. Deps: ninguna. Traz: R5.6-a, D-2.
    Habilita: T5.4, T5.13.

- [x] **T5.3 — `ingest/IngestAuthenticationEntryPoint.java` — ÚNICO escritor del 401 de ingesta.**
  - Fichero (NUEVO): `backend/src/main/java/com/projectlog/ingest/IngestAuthenticationEntryPoint.java`.
  - Contenido (R5.5, S5-11, S5-13, D-3): `@Component` que
    `implements AuthenticationEntryPoint, AccessDeniedHandler` — réplica
    LITERAL de `config/RestAuthenticationEntryPoint.java` (ObjectMapper
    bean por constructor, D-3; SIN logger; SIN leer el header).
    Constants (Decisión T5-1): `static final String CODE_API_KEY_INVALID =
    "api_key_invalid";` (package-private — visible al paquete `ingest` para
    el 401-honesto del controller, D-2) + `private static final String
    MSG_API_KEY_INVALID = "API key de ingesta ausente o inválida";`.
    Helper privado `escribir401(HttpServletResponse)` → `setStatus(401)` +
    `Content-Type: application/json` + `objectMapper.writeValue(getWriter(),
    Map.of("code", CODE_API_KEY_INVALID, "message", MSG_API_KEY_INVALID))`.
    `commence(...)` y `handle(...)` llaman al MISMO helper — SIN varianza
    por causa (anti-oráculo): un ÚNICO cuerpo para header ausente /
    malformado / sin match / revocada (E5.12/E5.24). El 403
    `project_archived` JAMÁS sale por aquí (sería defecto, no contrato).
  - PROHIBIDO: tocar `RestAuthenticationEntryPoint` del panel (`invalid_token`,
    CD-4 — intocable, R5.19c).
  - Cierre: compila en builder; comportamiento probado en T5.14 (E5.12).
  - Deps: ninguna (paralelizable con T5.1/T5.2). Traz: R5.5, S5-11, S5-13.
    Habilita: E5.12, E5.16, E5.24.

- [x] **T5.4 — `ingest/IngestGuardFilter.java` — orden 413→401 en UNA clase (doble barrera + auth).**
  - Fichero (NUEVO): `backend/src/main/java/com/projectlog/ingest/IngestGuardFilter.java`.
  - Contenido (R5.4, S5-12, S5-9, D-1/D-5/D-6/D-8): `@Component extends
    OncePerRequestFilter`, SIN loggers (R5.15), Javadoc cita D-C5-1 (R5.16).
    Constructor: `ApiKeyService` + `ProjectsRepository` + `ObjectMapper` +
    `@Value("${projectlog.ingest.max-request-bytes:1MiB}") String
    limiteTexto` convertido UNA VEZ a `long` (fail-fast de arranque).
    Orden interno por petición:
    1. `shouldNotFilter(...)` (D-8) → `true` (no filtrar) salvo URI
       `/api/v1/ingest` o que empiece por `/api/v1/ingest/` — acota la
       auto-registración global de Boot (D-R2; S5-12a).
    2. **Barrera 1 (tamaño)**: `request.getContentLengthLong()`; si `≥ 0 &&
       > limite` → escribir 413 (constants `CODE_PAYLOAD_TOO_LARGE` /
       `MSG_PAYLOAD_TOO_LARGE` vía ObjectMapper) y `return` SIN tocar el
       cuerpo ni continuar la cadena (R-12: no drena).
    3. **Barrera 2 (chunked)**: SIEMPRE continuar con un
       `HttpServletRequestWrapper` cuyo `getInputStream()` devuelve un
       `ServletInputStream` decorador anidado que cuenta bytes; al cruzar
       `limite` (frontera EXACTA: `limite` bytes LEGAL, `limite+1` → 413)
       lanza la nested `CuerpoDemasiadoGrandeException extends
       RuntimeException` (D-5 — NUNCA `IOException`: Jackson la envolvería
       en `HttpMessageNotReadableException` → 400 degradando el 413).
       `getReader()` cubierto con un solo conteo posible (por diseño).
    4. **Auth**: header `X-API-Key` → `apiKeyService.sha256(header)` (String
       COMPLETO — sin trim de `plk_`, sin hex, sin salt) →
       `projectsRepository.findByApiKeyHash(hash)`; con match →
       `setAuthentication(new UsernamePasswordAuthenticationToken(new
       IngestPrincipal(dto.id(), dto.status()), null, List.of()))`; sin
       header / malformada / sin match / revocada → NO escribir respuesta,
       NO setear contexto: continuar (la regla `authenticated()` fallará y
       el entry point escribirá el 401).
    5. `try { filterChain.doFilter(wrapped, response); } catch
       (CuerpoDemasiadoGrandeException ex) { escribir413 }` — catch SOLO ese
       tipo exacto, solo si `!response.isCommitted()`, sin rethrow.
  - PROHIBIDO: leer/loguear header/key/hash; consumir el cuerpo en el
    filtro; `catch (RuntimeException)` genérico.
  - Cierre: compila en builder. Deps: T5.1, T5.2. Traz: R5.4, S5-12, S5-9
    (D-6), D-C5-1, R5.15. Habilita: E5.27, E5.28, E5.33 (pila), E5.12 (MVC).
    Unit del normalizador en T5.16 (E5.34).

- [x] **T5.5 — `SecurityConfig.ingestChain` — PRIMERA EDICIÓN del fichero desde C1 + RED ANTI-CONTAMINACIÓN INMEDIATA (OBLIGATORIA antes de continuar).**
  (**hecho** 2026-10-25 — con 2 RATIFICACIONES de gate, ver design.md D-10: (1) los 2 beans de
  ingesta entran a `ingestChain` como `ObjectProvider` resuelto LAZY al request (lambdas delegantes:
  `@WebMvcTest` incluye `Filter` pero no `AuthenticationEntryPoint` en su escaneo, y un `getObject()`
  a build-time estallaba en 46 slices de C1–C4); (2) el ctor de `IngestGuardFilter` lleva
  `ObjectProvider<ApiKeyService>`/`ObjectProvider<ProjectsRepository>` resueltos lazy en
  `doFilterInternal`. Suite C1–C4: 77/77 VERDES SIN TOCAR NINGÚN assert; `mvn` completo final:
  **Tests run: 107, Failures: 0, Errors: 0 — BUILD SUCCESS** (log `/tmp/mvn_c5_full5.log`).
  Además: bean-higiene `filtroGuardSoloCadena1` (FilterRegistrationBean setEnabled(false) — hallazgo
  E5.28) y en `IngestGuardFilter` los 3 catches desenvolvedores de `FrameworkServlet` (hallazgo
  E5.28). Cadenas 2/3 y constructor byte-a-byte ✓.)
  - Fichero (`TOCADO`): `backend/src/main/java/com/projectlog/config/SecurityConfig.java`.
  - Edición (R5.6, R5.3.1): SOLO el bean `ingestChain` — cuerpo EXACTO al
    bloque Java del R5.6 (`common(http)` →
    `.securityMatcher("/api/v1/ingest/**")` → SIN `cors()` (TRD §5,
    comentario conservado) → `.addFilterBefore(ingestGuardFilter,
    UsernamePasswordAuthenticationFilter.class)` (el MISMO ancla literal del
    `panelChain`) → `.authorizeHttpRequests(auth ->
    auth.anyRequest().authenticated())` → `.exceptionHandling(eh ->
    eh.authenticationEntryPoint(entryPointIngesta).accessDeniedHandler(entryPointIngesta))`
    → `.build()`) + Javadoc del bean + las 2 líneas del Javadoc de clase.
    Los 2 beans nuevos entran como PARÁMETROS del método `@Bean` — el
    CONSTRUCTOR y sus 2 campos quedan byte-a-byte; `panelChain`,
    `defaultChain` y `common` byte-a-byte (R5.19b).
  - Semántica: `denyAll()` TEMPORAL → `authenticated()`; el 401 de la
    cadena pasa de `invalid_token` (firma temporal C4, REEMPLAZADA) a
    `api_key_invalid` (Schema §3.4) — la frontera temporal queda consumida.
  - **OBLIGATORIO INMEDIATO tras el edit, ANTES de la tarea T5.6** (red
    anti-contaminación R-3): `mvn test` COMPLETO (suite C1–C4 = **77/77**,
    sin verde no se continúa) en la imagen builder — con foco en
    `backend/src/test/java/com/projectlog/auth/PanelAuthMvcTest.java`
    (5 tests que esperan `invalid_token`: si la cadena del panel viese el
    entry point de ingesta o el guard, ROMPEN al instante — fuga del
    literal `api_key_invalid` al panel = rojo instantáneo). Si algo rompe:
    REVERTIR el edit y depurar el guard/entry point — NUNCA relajar un
    assert de C1–C4.
  - Cierre: `mvn test` completo en builder = 77/77 BUILD SUCCESS +
    `git diff` de `SecurityConfig.java` muestra SOLO `ingestChain` +
    Javadocs (constructor y cadenas 2/3 sin una línea cambiada).
  - Deps: T5.3, T5.4 (los 2 beans referenciados). Cubre: R5.6, R5.3.1,
    R5.19(b)(c), E5.16-b (con soporte de T5.19). Habilita: TODO el resto
    del corte (la cadena 1 está viva).

---

## Fase 2 — Pin SQL del lookup B1 (E5.15, parte SELECT)

- [x] **T5.6 — Test pin `ProjectsIngestSqlTest` — pin del `SELECT_POR_API_KEY_HASH` (E5.15/SELECT).**
  - Fichero (NUEVO): `backend/src/test/java/com/projectlog/projects/repository/ProjectsIngestSqlTest.java`
    (JUNTO a `ProjectsKeySqlTest`, MISMO paquete — D-9: el constant es
    `private` y su pin DEBE vivir en el paquete `projects.repository`;
    NUNCA reflection, NUNCA `public`).
  - Contenido (patrón `ProjectsKeySqlTest`, JUnit puro sin BD/Spring):
    pin de `SELECT_POR_API_KEY_HASH` == `"SELECT " + COLS + " FROM projects
    WHERE api_key_hash = ?"` — asertar columna a columna contra `COLS`
    (la proyección EXACTA con `has_key` derivado), la tabla `projects` y el
    `WHERE api_key_hash = ?`; sin columnas extra (sin `api_key_hash` en el
    SELECT — D-C5-1) y sin `queryForObject` en el método (pin de
    ausencia vía revisión del código en T5.19).
  - Cierre: `mvn test` con esta clase verde en builder.
  - Deps: T5.1. Cubre: E5.15 (SELECT), R5.11-a, R5.19a.

---

## Fase 3 — Records de entrada/salida y `LogsRepository` (INSERT all-or-nothing + pin)

- [x] **T5.7 — `ingest/LogIngestRequest.java` — record de entrada + factoría `desdeJson`.**
  - Fichero (NUEVO): `backend/src/main/java/com/projectlog/ingest/LogIngestRequest.java`.
  - Contenido (R5.24-a/b, S5-1, S5-2, S5-15): `public record
    LogIngestRequest(String level, String timestamp, String message, String
    exception, JsonNode metadata, String environment, String source, String
    correlationId)` — orden literal del Schema §3.4; la CLAVE JSON es
    `correlation_id` (la columna V1); `metadata` se guarda como `JsonNode`
    CRUDO (nada lo serializa aquí — UNA serialización en el service, S5-8).
    Factoría `public static LogIngestRequest desdeJson(JsonNode nodo)` —
    patrón LITERAL `UpdateProjectRequest.desdeJson` (`backend/src/main/java/com/projectlog/projects/dto/UpdateProjectRequest.java`):
    helper `valor(nodo, campo)` → `null` si null/`isNull()`, si no
    `asText()` (coerción Jackson determinista: escalar → forma texto,
    contenedor → ""); SOLO los 8 nombres literales se leen — campos
    desconocidos (incl. `project_id`/`received_at` falsificados del
    cliente) JAMÁS se leen ni persisten (S5-2); SIN normalización
    case/trim/empty→NULL: cadena vacía presente es VÁLIDA (R5.24-a).
  - Cierre: compila en builder; comportamiento probado en T5.14.
  - Deps: ninguna (paralelizable). Traz: R5.24-a/b, S5-1/S5-2, S5-15.
    Habilita: E5.1–E5.14, E5.24-c.

- [x] **T5.8 — `ingest/LogValidado.java` — record moneda del INSERT.**
  - Fichero (NUEVO): `backend/src/main/java/com/projectlog/ingest/LogValidado.java`.
  - Contenido (S5-15, S5-8, R5.11-d): `public record LogValidado(String
    level, String environment, OffsetDateTime timestamp, String message,
    String exception, String metadataJson, String source, String
    correlationId)` — nace en el service tras validar todo el registro;
    `timestamp` ya parseado, `metadataJson` ya serializado (o `null`). Es
    lo ÚNICO que `LogsRepository` recibe (el repo no valida, no parsea, no
    serializa).
  - Cierre: compila en builder. Deps: ninguna. Traz: S5-15, S5-8, R5.11-d.
    Habilita: T5.9, T5.12.

- [x] **T5.9 — `ingest/LogsRepository.java` (NUEVO) — ÚNICA fuente SQL de INSERT sobre `logs`.**
  - Fichero (NUEVO): `backend/src/main/java/com/projectlog/ingest/LogsRepository.java`.
  - Contenido (R5.11-b/c/d, D-4, R-4): `@Repository`; 3 constants
    package-private `static final` EXACTAS al R5.11-b:
    `INSERT_LOGS_CABECERA = "INSERT INTO logs (project_id, level,
    environment, timestamp, received_at, message, exception, metadata,
    source, correlation_id) VALUES "`, `INSERT_LOGS_FILA = "(?, ?, ?, ?, ?,
    ?, ?, CAST(? AS jsonb), ?, ?)"` (el `CAST` en la 8.ª posición es
    OBLIGATORIO — R-4), `INSERT_LOGS_RETORNO = " RETURNING id"` (existe
    desde C5 aunque la 202 no exponga ids — moneda del frame batch de C7).
    Método EXACTO R5.11-c: `@Transactional` (defensa documentada: UNA
    sentencia = UNA transacción — regla 6/ADR-11; la ÚNICA anotación
    transaccional del corte) `public List<Long> insert(long projectId,
    List<LogValidado> filas, OffsetDateTime receivedAt)`. SQL por
    `StringBuilder` (CABECERA + FILA N veces separadas por `", "` +
    RETORNO; individual = N=1; N ≤ 200 garantizado por el `limiteLote` del
    controller — el nº de filas NO proviene del cliente como dato libre).
    Varargs en el orden EXACTO de columnas por fila: `projectId, level,
    environment, timestamp (OffsetDateTime), receivedAt (OffsetDateTime),
    message, exception, metadataJson (String → CAST), source,
    correlationId`; opcional nulo = parámetro `null` (patrón C3 del
    `description`); el driver manda `timestamptz` y PG normaliza a UTC
    (TRD §2.6). Extractor `(rs, numeroFila) -> rs.getLong("id")` →
    `List<Long>` en ORDEN de VALUES. SIN captura de
    `DataIntegrityViolationException`/`DuplicateKeyException` (R5.12-b:
    500 honesto; el rollback del proxy cubre).
  - PROHIBIDO: SQL de lectura (C6), paginación, PGobject manual.
  - Cierre: compila en builder + T5.10 verde. Deps: T5.8. Traz:
    R5.11-b/c/d, D-4, R-4, R5.12. Habilita: E5.19 (mock), E5.22/E5.23 (pila).

- [x] **T5.10 — Test pin `LogsRepoSqlTest` — pins de los 3 constants (E5.15/INSERT).**
  - Fichero (NUEVO): `backend/src/test/java/com/projectlog/ingest/LogsRepoSqlTest.java`
    (mismo paquete que los constants package-private — D-9; patrón
    `ProjectsKeySqlTest`, JUnit puro sin BD).
  - Contenido (E5.15): pin de `INSERT_LOGS_CABECERA` (columnas literales
    `INSERT INTO logs (project_id, level, environment, timestamp,
    received_at, message, exception, metadata, source, correlation_id)
    VALUES `), pin de `INSERT_LOGS_FILA` == `(?, ?, ?, ?, ?, ?, ?,
    CAST(? AS jsonb), ?, ?)` (asertar posición 8 = `CAST(? AS jsonb)`, 10
    placeholders), pin de `INSERT_LOGS_RETORNO` == `" RETURNING id"`.
  - Cierre: `mvn test` con esta clase verde en builder.
  - Deps: T5.9. Cubre: E5.15 (INSERT), R-4.

---

## Fase 4 — Seam C7 + `IngestService` (validación) + `IngestController` + tests unitarios MVC

> La interfaz `IngestListener` (T5.11) nace aquí por COMPILABILIDAD: el
> constructor del `IngestService` inyecta `List<IngestListener>` (D-4/D-7
> del flujo); su VERIFICACIÓN de invocación tras-COMMIT es T5.15 (Fase 5,
> E5.20/E5.33).

- [x] **T5.11 — `ingest/IngestListener.java` (seam C7, Punto 5 del gate).**
  - Fichero (NUEVO): `backend/src/main/java/com/projectlog/ingest/IngestListener.java`.
  - Contenido (R5.13, S5-10, D-12): interfaz pública con ÚNICO método
    `void onLotePersistido(long projectId, OffsetDateTime receivedAt,
    List<Long> ids);` + Javadoc EXACTO del design §10 (invocado
    EXCLUSIVAMENTE tras el COMMIT — estructura D-4; JAMÁS ante
    401/403/400/parse ni rollback; C5 NO implementa streaming; C5 no
    captura excepciones del suscriptor — at-least-once, D-12).
  - PROHIBIDO: `TransactionSynchronizationManager`,
    `@TransactionalEventListener`, frames, handler, `LiveBroadcaster` (E3
    del explore descartado en gate).
  - Cierre: compila en builder. Deps: ninguna. Traz: R5.13, S5-10,
    gate Punto 5. Habilita: T5.12, E5.20.

- [x] **T5.12 — `ingest/IngestService.java` — validación completa fail-fast + received_at UNA vez + seam (constants D-7).**
  - Fichero (NUEVO): `backend/src/main/java/com/projectlog/ingest/IngestService.java`.
  - Contenido (R5.12, R5.24, S5-7/S5-8, D-7, D-11, Decisión T5-1): `@Service`
    con Javadoc de clase ANOTANDO la ratificación de D-7 (los 6 constants
    de 400 por campo viven AQUÍ — propietario del check de validación; los
    literales byte-a-byte a R5.10/S5-13). Constructor: `LogsRepository` +
    `List<IngestListener> oyentes` (Spring inyecta lista VACÍA si no hay
    beans — cero clientes en C5; NUNCA `Optional<IngestListener>`). Nested
    `public record ErrorValidacion(String code, String message, String
    field, Integer index)` (index 0-based en batch, `null` en individual —
    S5-3). Constants `static final`: `ENTORNOS = Set.of("development",
    "staging", "production")` y `NIVELES = Set.of("TRACE","DEBUG","INFO",
    "WARN","ERROR","FATAL")` (case-sensitive EXACTO, sin normalización);
    los 6 `CODE_*` + `MSG_*` del R5.10 + límites `32768/131072/65536/200`.
    Helper `static package-private OffsetDateTime parsearTimestamp(String)`
    (D-11: `OffsetDateTime.parse` sin formatter custom — ISO del JDK
    acepta `Z` y `±hh:mm`, RECHAZA naive; testable en puro, E5.18).
    Método público ÚNICO `public Optional<ErrorValidacion> registrar(long
    projectId, List<LogIngestRequest> registros, boolean lote)` — NO
    transaccional (D-4). Flujo EXACTO:
    1. Validación COMPLETA antes de tocar la BD (regla 6), loop por índice
       creciente, POR REGISTRO en el orden determinista R5.24
       (primer-fallo-gana, R-9): environment (S5-4: 1 code para las 4
       variantes) → timestamp (S5-5) → level (S5-6) → message required →
       message > 32768 bytes → exception > 131072 → metadata
       `!isObject()` o > 65536 bytes serializados (`{}` VÁLIDO) → source >
       200 chars → correlation_id > 200 chars. Bytes UTF-8 via
       `getBytes(StandardCharsets.UTF_8).length` UNA sola obtención por
       campo (S5-8); `metadata`: UNA `writeValueAsString` cuyo String
       alimenta medida Y parámetro (R-D3); chars (`String.length()`) para
       source/correlation_id. En batch `index` = posición 0-based del
       culpable; en individual `index = null`.
    2. `OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC)` —
       UNA llamada, tras TODA validación, común a todo el lote, pasada
       como parámetro al repo (S5-7; en un 400 nunca se calcula).
    3. `List<Long> ids = logsRepository.insert(projectId, filas, receivedAt)`
       (el `@Transactional` del repo = COMMIT al retornar).
    4. Seam tras-COMMIT ESTRUCTURAL: `oyentes.forEach(o ->
       o.onLotePersistido(projectId, receivedAt, ids))` — sentencia
       siguiente, fuera de todo scope transaccional; TAMBIÉN en ingesta
       individual (ids de 1 — frame batch de C7 = batch de 1, Schema §3.5).
    5. `return Optional.empty()` → el controller emite la 202.
  - SIN captura de DIVE (R5.12-b: 500 honesto — política S6 de C4). El
    service JAMÁS toca HTTP (ni `ResponseEntity`, `HttpServletResponse`).
  - Cierre: compila en builder; su conducta verificada por T5.15 y T5.14.
  - Deps: T5.9, T5.10, T5.11, T5.7, T5.8. Traz: R5.12, R5.24, S5-3…S5-8,
    D-7 (Decisión T5-1), R5.13, gate Punto 6. Habilita: E5.3–E5.11, E5.14,
    E5.19, E5.20; en pila E5.23, E5.26, E5.30.

- [x] **T5.13 — `ingest/IngestController.java` — 2 mappings, gates 3 y de lote, handler de ilegibilidad, 202.**
  - Fichero (NUEVO): `backend/src/main/java/com/projectlog/ingest/IngestController.java`.
  - Contenido (R5.7–R5.10, S5-1, S5-3, D-2/D-3/D-7/D-10, Decisión T5-1):
    `@RestController @RequestMapping("/api/v1/ingest")`; exactamente 2
    mappings `@PostMapping` y `@PostMapping("/batch")` (S5-12e: GET/PUT →
    405 por defecto de Spring, fuera de contrato); entrada
    `@RequestBody JsonNode` (S5-1). Constructor: `IngestService` +
    `ObjectMapper` (D-3) + `@Value("${projectlog.ingest.max-batch-size:200}")
    int limiteLote` (bind int nativo, conversión UNA vez, R5.14).
    Constants D-7 (propietario de SUS 3 gates): `CODE_PROJECT_ARCHIVED` /
    `MSG_PROJECT_ARCHIVED = "El proyecto está archivado: no acepta logs"`,
    `CODE_BATCH_EMPTY` / `MSG_BATCH_EMPTY = "El campo logs debe contener
    al menos 1 registro"`, `CODE_BATCH_TOO_LARGE` / mensaje compuesto
    `"El campo logs no puede contener más de <n> registros"`. Ambos
    endpoints unificados en un método privado
    `procesar(JsonNode cuerpo, boolean lote)` con la tabla de gates EXACTA
    del design §6:
    - Gate 0 (D-2, defecto estructural): principal no es `IngestPrincipal`
      → 401 honesto REUTILIZANDO `CODE_API_KEY_INVALID` package-private
      del entry point (jamás 500 enmascarado).
    - Gate 3 (403, key VÁLIDA): `principal.status()` == `"ARCHIVED"`
      (comparación exacta) ANTES de la validación de campos (R5.8 — un
      pedido con cuerpo inválido a archivado responde 403) → 403 con el
      literal, SIN `field`, SIN `index`.
    - Gate de lote (solo batch, S5-8/R5.24-c): sobre el `JsonNode` ANTES de
      parsear elemento alguno — `path("logs")` no array o array de 0 →
      400 `batch_empty` (`field: "logs"`); `size() > limiteLote` →
      `batch_too_large`. NADIE re-especializa el 413 en 400 (R-13).
    - Parse por registro en orden del array (`desdeJson`; individual: 1
      llamada sobre el propio cuerpo) → `ingestService.registrar(...)`;
      si `Optional<ErrorValidacion>` → helper `error(...)` materializando
      `code/message/field` y `index` SOLO si `lote` && `index != null`
      (S5-3: en individual JAMÁS `index`, aunque valdría 0).
    - Sin error → `ResponseEntity.ok(objectMapper.valueToTree(Map.of(
      "accepted", registros.size())))` con `.contentType(MediaType.
      APPLICATION_JSON)` — JSON SIN espacios: `{"accepted":1}` literal
      (R5.9/R5.10-a); 202 SOLO tras el COMMIT (R5.12-a).
    - Handler de ilegibilidad (S5-1/D-10): `@ExceptionHandler({
      HttpMessageNotReadableException.class,
      HttpMediaTypeNotSupportedException.class})` → 400 por URI:
      `request.getRequestURI().endsWith("/batch")` — individual →
      `environment_required` (`field: "environment"`); batch →
      `batch_empty` (`field: "logs"`). Nunca 415 fuera del contrato §3.1.
    - Helper `error(HttpStatus, String code, String message, String
      fieldOrNull, Integer indexOrNull)` private — réplica LITERAL de C3
      (`LinkedHashMap` orden `code, message, field, index`; claves solo si
      aplican; `valueToTree`; `MediaType.APPLICATION_JSON`). Un solo
      método cubre 403, 400 de lote y 400 de `ErrorValidacion`.
  - Cierre: compila en builder; conducta verificada por T5.14.
  - Deps: T5.3 (constant package-private), T5.12, T5.7. Traz: R5.7, R5.8,
    R5.9, R5.10, R5.10-a, R5.24-c, S5-1, S5-3, S5-12, D-7 (Decisión T5-1).
    Habilita: E5.1–E5.14, E5.24-c; en pila E5.21–E5.26, E5.30–E5.32.

- [x] **T5.14 — Test MVC `IngestControllerMvcTest` — 202/400 de campo/lote/401/403 + ilegibilidad + orden fail-fast (E5.1–E5.16 MVC).**
  - Fichero (NUEVO): `backend/src/test/java/com/projectlog/ingest/IngestControllerMvcTest.java`.
  - Patrón (design §13, suite [L]): `@ActiveProfiles("test")` +
    `@WebMvcTest(IngestController.class)` + `@Import({SecurityConfig.class,
    IngestGuardFilter.class, IngestAuthenticationEntryPoint.class})` +
    `@MockitoBean` de `ProjectsRepository`, `ApiKeyService`,
    `LogsRepository` + `@Import(IngestService.class)` (service REAL con el
    repo de logs mock) — `application-test.yml` INTOCABLE. El mock del
    filtro arma el principal (ACTIVE/ARCHIVED) vía `SecurityContext`.
  - Casos (spec §2.1–§2.3):
    - E5.1 `ingestIndividualResponde202Accepted1Exacto` — 202 y cuerpo
      serializado EXACTO `{"accepted":1}` (sin espacios, sin más campos).
    - E5.2 `ingestBatchResponde202AcceptedNExacto` — batch N ≥ 2 →
      `{"accepted":N}`.
    - E5.3 `environmentAusenteOInvalidoResponde400EnvironmentRequired` —
      las 4 variantes del S5-4 → 400 con code/field y message EXACTO; sin
      `index` en individual.
    - E5.4 `timestampNaiveONoParseableResponde400TimestampWithoutTimezone`.
    - E5.5 `levelFueraDeEnumResponde400LevelInvalid` — `"error"` (minúscula,
      sin normalización), `"TRACE "` y ausente.
    - E5.6 `messageVacioResponde400MessageRequired` — ausente, "" y "   ".
    - E5.7 `messageBytes32769Responde400FieldTooLongFrontera` — message de
      32768 bytes (con carácter multibyte: bytes ≠ chars) → 202; 32769 →
      400 `field_too_long` `field: "message"`.
    - E5.8 `metadataArrayOEscalarResponde400MetadataNotObject` — array y
      escalar → 400; `{}` → 202.
    - E5.9 `fieldTooLongDeExceptionMetadataSourceYCorrelationId` —
      131073/65537/201/201 → 400 con el `field` del culpable; fronteras
      legales 131072/65536/200/200 → 202.
    - E5.10 `batchVacioONoArrayY201Responde400FieldLogs` — `{"logs": []}` y
      logs ausente/no-array → `batch_empty`; 201 registros →
      `batch_too_large`; ambos `field: "logs"`.
    - E5.11 `batchRegistroIntermedioLlevaIndexYFieldPrimerFalloGana` —
      batch de 5 con el registro 2 inválido en `level` y el 4 en `message`
      → 400 con `index: 2`, `field: "level"`; en individual el mismo fallo
      SIN `index` (S5-3).
    - E5.14 `ordenFailFastDeCamposEsDeterminista` — registro con TODOS los
      campos mal → SIEMPRE `environment` (check 1); registro con
      `timestamp`+`level` mal → `timestamp` (check 2).
    - E5.24-c — cuerpo "no-json" en individual → 400 `environment_required`;
      en batch → 400 `batch_empty`; `Content-Type: text/plain` → EL MISMO
      400 (nunca 415, D-10).
    - E5.12 `sinHeaderMalformadaONoMatchResponde401ApiKeyInvalid` — sin
      header, header basura no-plk, key válida-forma sin match → 401 con
      el EXACTO cuerpo de R5.9 (UNO solo para las 3 — anti-oráculo);
      `verifyNoInteractions` del service y de `LogsRepository` (el 401
      jamás toca la BD de logs).
    - E5.13 `proyectoArchivadoResponde403ProjectArchivedAntesDeValidar` —
      principal ARCHIVED + cuerpo inválido → 403 (gate 3 ANTES de la
      validación, R5.8); literal EXACTO sin field; el repo de logs NUNCA
      se llama.
    - E5.16 `jwtJamásAutenticaIngestaYCadena1SinInvalidToken` —
      `Authorization: Bearer <jwt>` y SIN `X-API-Key` → 401
      `api_key_invalid` (NUNCA `invalid_token` — Schema §5 verbatim); la
      respuesta de ingesta jamás contiene el literal del panel (las
      cadenas 2/3 byte-a-byte se certifican con la re-corrida C1–C4,
      E5.16-b: T5.5/T5.19).
  - Cierre: `mvn test` con esta clase verde en builder.
  - Deps: T5.13, T5.3, T5.4, T5.5, T5.7–T5.9, T5.11, T5.12.
    Cubre: E5.1–E5.14, E5.16, E5.24-c (parejas mock de R5.18).
  - Nota remediación QA (loop-back 2026-10-25): QP-12 COMPLETO — en
    `ingestIndividualResponde202Accepted1Exacto` se añadió
    `verify(apiKeyService).sha256(KEY_FIXTURE)` (el digest SIEMPRE del
    header COMPLETO, con prefijo, sin recortes). Y QP-7/QP-9 COMPLETOS:
    asertos del mensaje compuesto de `field_too_long` en las 5 fronteras
    400 (message 32768 bytes; exception 131072; metadata 65536; source y
    correlation_id 200 caracteres — literales exactos de `msgLimite`).

---

## Fase 5 — Seam: prueba de invocación tras COMMIT + units del service/bind + higiene

- [x] **T5.15 — Test unitario `IngestServiceTest` — seam tras-COMMIT, received_at común, parse de timestamp (E5.18/E5.19/E5.20).**
  - Fichero (NUEVO): `backend/src/test/java/com/projectlog/ingest/IngestServiceTest.java`.
    Nota remediación QA (loop-back 2026-10-25): QP-EX-1 (hueco S5-2) COMPLETO —
    método NUEVO `camposDeConfianzaFalsificadosPorElClienteIgnorados`: JSON con
    `id`/`project_id`/`received_at` falsificados (y variantes camelCase) +
    campos válidos → `LogIngestRequest.desdeJson` → asertos de que el record
    porta SOLO sus 8 componentes literales e `isEqualTo` el registro válido;
    captor del `insert` probando `project_id` == el del parámetro/principal
    (jamás el 999 falsificado) y `received_at` side-server (jamás el
    `1999-01-01T…` falsificado) y fila igual a `filaDe(leido)`. Suite: 108/0/0.
  - Contenido (unit puro Mockito SIN Spring, design §13): repo mock +
    `List<IngestListener>` con UN mock.
    - E5.18 `timestampParseAceptaZyOffsetsYRechazaNaive` (con D-11) —
      `2026-10-16T22:31:04Z`, `2026-10-16T22:31:04+02:00`,
      `2026-10-16T22:31:04.123+05:30` parsean; `2026-10-16T22:31:04`
      (naive) lanza `DateTimeParseException` → 400
      `timestamp_without_timezone`.
    - E5.19 `received_atFijadoUnaVezComunAlLote` (S5-7) — `ArgumentCaptor`
      del `insert`: UNA invocación, UN `OffsetDateTime` común a TODAS las
      filas (lote), y el MISMO valor portado al seam.
    - E5.20 `seamIngestListenerSoloTrasCommitConArgsExactos` (S5-10/D-12) —
      (a) batch válido → `onLotePersistido(projectId, receivedAt, ids)`
      invocado UNA VEZ con los ids del mock en ORDEN de VALUES; (b)
      individual → invocado con lista de 1; (c) ante 400 de validación o
      `insert` que lanza (rollback) → `verifyNoInteractions(listener)` —
      invocado jamás antes del COMMIT (E5.33 pila complementa en T5.20).
  - Cierre: `mvn test` con esta clase verde en builder.
  - Deps: T5.12, T5.13. Cubre: E5.18, E5.19, E5.20 (R5.13, R5.7/S5-7).

- [x] **T5.16 — Test unitario `IngestGuardLimitTest` — bind R-6 del límite (E5.34, OBLIGATORIO).**
  - Fichero (NUEVO): `backend/src/test/java/com/projectlog/ingest/IngestGuardLimitTest.java`.
  - Contenido (S5-9/D-6, unit sin Spring): `limiteBytes("1MiB")` →
    1048576; `"1KB"`/`"1KiB"` → 1024; `"1048576"` → 1048576; `"1MB"` →
    1048576 (equivalencia binaria de Spring, evidencia spring-core
    6.2.19); `"abc"` → excepción (fail-fast de arranque); y el default del
    `@Value("${projectlog.ingest.max-request-bytes:1MiB}")` del filtro es
    EXACTAMENTE `"1MiB"` (assert sobre la anotación vía reflection del
    constructor o pin del literal — design §13; sin duplicación frágil).
  - Cierre: `mvn test` con esta clase verde en builder.
  - Deps: T5.4. Cubre: E5.34, R5.14, R-6.

- [x] **T5.17 — Test de higiene `IngestLogHygieneTest` (E5.17).**
  - Fichero (NUEVO): `backend/src/test/java/com/projectlog/ingest/IngestLogHygieneTest.java`.
  - Contenido (R5.15): réplica del patrón `LogHygieneTest` de C2
    (`backend/src/test/java/com/projectlog/auth/LogHygieneTest.java`) —
    `ListAppender` de logback sobre el logger `com.projectlog` a DEBUG;
    con el mismo armado de T5.14, ejecutar un ingest 202 (key real de
    test) y un 401: NINGÚN evento de `com.projectlog.ingest` ni de
    `com.projectlog.keys` contiene la key, un fragmento, el header
    `X-API-Key` ni el hash.
  - Cierre: `mvn test` con esta clase verde en builder.
  - Deps: T5.14. Cubre: E5.17, R5.15.

---

## Fase 6 — Smoke script (tarea ÚNICA: codificación E5.21–E5.33 + smoke-of-the-smoke)

- [x] **T5.18 — Escribir `scripts/qa_c5_smoke.sh` + smoke-of-the-smoke (E5.21–E5.33, incl. E5.31/G-2).**
  (**hecho** 2026-10-25 — script + corrida canónica 13/13 PASS exit 0; `bash -n` OK; shellcheck NO
  disponible en el host; desviaciones del harness [E2E] documentadas en apply-progress: nginx 1MiB
  de referencia sin `client_max_body_size` → 3 cuerpos >1048576 via ruta directa backend, bug del
  `@` de `req()` corregido; hallazgo E5.28 real = envoltorio de `FrameworkServlet` — ver apply-progress)
  - Fichero (NUEVO): `scripts/qa_c5_smoke.sh` (patrón `scripts/qa_c4_smoke.sh`
    — no se invoca NINGÚN script previo: `down -v` destruiría el volumen).
  - Helpers propios heredados de C4: anti-veneno de env, carga `.env` sin
    exportar, recomposición ÚNICA (`podman compose up -d --build
    --force-recreate`, timeout acotado), `pila_saludable`/`esperar_healthy`,
    `psql_q -qAt`, `req` curl, `campo_json`, `capturar_id` INCONDICIONAL,
    `bearer_hdr`, `curl_ingest KEY` (`-H "X-API-Key: $KEY"`) — NUEVO de C5;
    key y JWT SOLO en tmpdir 700 con trap de limpieza (JAMÁS a consola);
    conteos RELATIVOS antes/después de cada escenario (R-15); sufijos
    `smoke-c5-<TS>-*`; needles `plk_*` con escape `\_` o `position()` (S-3).
  - Pre-fase (smoke-of-the-smoke, sin ID propio — parte de E5.21):
    recomposición ÚNICA + 3×healthy + sondeo `GET /me` sin token → 401
    `invalid_token` (cadena 2 intacta — no-regresión light) + login 200
    (JWT al tmpdir) + `POST /projects` → 201 `smoke-c5-<TS>-a` (id
    capturado INCONDICIONAL) + `POST …/api-key/regenerate` → 201 (key al
    tmpdir).
  - Pasos (spec §2.5, una función por E5.n, salida `E5.n: PASS | FAIL |
    BLOQUEADO (detalle)`, exit 0/1/2 como C3/C4):
    - E5.21 happy individual: `POST /api/v1/ingest` con la key y payload
      completo (`correlation_id: "e521"`, `metadata: {"span": "checkout"}`)
      → 202 `{"accepted":1}` EXACTO; psql: delta `count(*)` = 1 y la fila
      nueva con `project_id` correcto, level/env/correlation_id/source
      EXACTOS.
    - E5.22 batch de 200 (payload generado, `correlation_id: e522-<nnn>`,
      level/env rotando) → 202 `{"accepted":200}`; psql: delta = 200,
      `count(DISTINCT received_at) = 1` SOBRE los 200 ids nuevos (regla 9,
      igualdad EXACTA) y ids en ORDEN de VALUES (correlación id ASC ↔
      `correlation_id` ASC con ROW_NUMBER por JOIN sobre los ids > máximo
      previo).
    - E5.23 all-or-nothing: batch con registro inválido INTERMEDIO
      (`level: "XX"` en índice 2) → 400 `level_invalid` `field: "level"`
      `index: 2`; psql: `count(*)` delta = 0 (NADA se persiste — regla 6).
    - E5.24 401 en 3 variantes: (a) sin header; (b)
      `X-API-Key: plk_basura_no_existente`; (c) revocar (`DELETE …/api-key`
      con JWT → 204; tres a NULL en psql — regla 11) → ingestar con la
      MISMA key → 401. Las 3 con el MISMO cuerpo (needle `api_key_invalid`,
      jamás `invalid_token`). Luego `POST regenerate` → 201 con key2.
    - E5.25 403: proyecto ACTIVE + key2 → `PATCH {"status":"ARCHIVED"}`
      (C3, 200) → ingestar con key2 → 403 `project_archived` literal; psql
      delta = 0.
    - E5.26 400 reales (ACTIVE + key2): `environment` ausente →
      `environment_required`; `timestamp` naive →
      `timestamp_without_timezone`; psql delta = 0 en ambos.
    - E5.27 413 frontera (curl `--data-binary`): 1048576 bytes EXACTOS →
      202 (barrera inclusiva); 1048577 → 413 `payload_too_large` (needle
      del cuerpo; psql delta = 0). Plan B R-12 del spec §2.6 si curl
      manifestase conexión reiniciada sin cuerpo.
    - E5.28 413 chunked: payload ≥ 1048577 SIN `Content-Length`
      (`--header "Transfer-Encoding: chunked"`) → 413 (segunda barrera).
    - E5.29 confianza timestamp: `+02:00` → 202; psql: `timestamp`
      almacenado == el instant UTC esperado (`t` de
      `timestamp = '<utc>'::timestamptz`) y `received_at` ≥ el T1 del
      request.
    - E5.30 JSONB + fronteras: `metadata->>'span' = checkout` tras 202;
      metadata array → 400 `metadata_not_object`; frontera metadata
      65536/65537 serializados → 202/400; frontera message
      32768/32769 → 202/400 (pareja [S] de E5.7).
    - E5.31 (G-2, cierre ACTIVO — R5.16): proyecto aún ARCHIVED (de
      E5.25) → `PATCH {"status":"ACTIVE"}` → ingesta con la MISMA key2
      (JAMÁS re-generada) → 202 (reglas 11+12 juntas). El archive de C5
      citará este ID.
    - E5.32 fronteras de lote: `{"logs": []}` → `batch_empty`;
      `{"logs": [201...]}` → `batch_too_large` (`field: "logs"`); psql
      delta = 0.
    - E5.33 orden de gates en pila: >1 MiB con `X-API-Key: plk_basura` →
      413 (NO 401 — gate 1 antecede al 2); y ≤1 MiB + key inválida → 401.
  - Higiene: la verificación formal de intocables es T5.19 — aquí solo la
    check light de runtime (sondeo `/me` de la pre-fase).
  - Cierre en ESTA tarea (la corrida CANÓNICA es T5.20): `bash -n
    scripts/qa_c5_smoke.sh` (parse OK) + shellcheck si disponible + UNA
    corrida smoke-of-the-smoke sobre la pila `--force-recreate` para
    depurar falsos rojos de entorno (`t/f` vs `true/false`, ISO-8859-1 del
    401 de `/me`, needles S-3, fronteras de bytes de E5.27/E5.30) hasta
    consistencia mecánica.
  - Deps: T5.14, T5.17 (todas las [L] de la superficie codificadas; la
    imagen reconstruida debe contener TODO el código de C5).
    Cubre: E5.21–E5.33 (codificación + G-2 E5.31).

---

## Fase 7 — No-regresión completa, intocables y corrida canónica (finalDone)

- [x] **T5.19 — `mvn test` COMPLETO en builder + `git diff --exit-code` de intocables + revisión de ausencias.**
  (**hecho** 2026-10-25, tras las 2 ratificaciones de gate: **Tests run: 107, Failures: 0,
  Errors: 0, BUILD SUCCESS** = 77 C1–C4 + 30 C5, TODOS verdes sin tocar ningún assert
  [log `/tmp/mvn_c5_full5.log`]. Intocables verificados con diff-0: JwtAuthFilter,
  RestAuthenticationEntryPoint, CorsConfig, StartupSecretsValidator, WebSocketConfig, package-info,
  pom, compose.yaml, application-test.yml, .env.example, V1__init.sql, streaming/**, auth/**,
  keys/**, common/**, docs/**, mockups/**; ProjectsRepository = 30 adiciones/0 borrados
  (regenerateKey/EMPTY-RETURNING intactos). SecurityConfig: difs SOLO en ingestChain + Javadocs +
  bean-higiene `filtroGuardSoloCadena1` (D-10 ratificada). Ausencias ✓ (sin starter-validation/
  Testcontainers; sin IngestBatchRequest/IngestAcceptedResponse; DataIntegrityViolation solo como
  mención de AUSENCIA en Javadoc; 0 loggers en ingest; sin codes nuevos; sin toLowerCase/
  toUpperCase).)
  - `mvn test` completo en la imagen builder `maven:3.9-eclipse-temurin-21`
    (backend + `~/.m2` montados, sin BD): suite C1–C4 (77/77) + las 6
    clases nuevas (`IngestControllerMvcTest`, `IngestServiceTest`,
    `IngestGuardLimitTest`, `IngestLogHygieneTest`, `LogsRepoSqlTest`,
    `ProjectsIngestSqlTest`) → BUILD SUCCESS (R5.19b, E5.16-b).
  - Intocables diff-0 (`git diff --exit-code` == 0 en cada uno, R5.19/R5.3):
    `backend/src/main/java/com/projectlog/config/JwtAuthFilter.java`,
    `backend/src/main/java/com/projectlog/config/RestAuthenticationEntryPoint.java`
    (panel, CD-4), `config/CorsConfig.java` (sin `X-API-Key` — cierre S7
    de C4), `config/StartupSecretsValidator.java`,
    `config/WebSocketConfig.java`, `streaming/**`, `auth/**`, `keys/**`
    (incl. `tiempoConstanteIgual` intocada — D-C5-1), `common/**`,
    `db/migration/V1__init.sql`, `application-test.yml`, `.env.example`,
    `compose.yaml`, `backend/pom.xml`, `docs/**`,
    `frontend/public/mockups/**`,
    `backend/src/main/java/com/projectlog/ingest/package-info.java` (rol
    marcador — R5.1), tests y smoke de C1–C4. Para `SecurityConfig.java`:
    el diff SOLO en el bean `ingestChain` + Javadocs (constructor y
    cadenas 2/3 sin una línea).
  - Revisión de ausencias (grep/review): sin `starter-validation` ni
    Testcontainers en pom (R5.2/Punto 4); SIN ficheros
    `IngestBatchRequest.java`/`IngestAcceptedResponse.java` (S5-15); sin
    captura de `DataIntegrityViolationException` (R5.12-b); sin loggers en
    `com.projectlog.ingest` (R5.15); NINGÚN code nuevo fuera de los 11 de
    R5.10 (R5.19g); sin normalización case de enums; sin 429 (ADR-26); sin
    404 en ingesta; sin generación server-side de `correlation_id`; sin
    validación de Content-Type (S5-12); **G-1 NO reabierta** —
    `regenerateKey`/rama empty-RETURNING sin diff (R5.16); la rama del
    `E5.33`/413 no tocada si el plan B R-12 no se activó (o activada solo
    con drenaje acotado si E5.27 lo exigió — documentado).
  - Cierre: BUILD SUCCESS + diffs 0 + ausencias verificadas. ESTE es el
    bloqueo para declarar la corrida canónica.
  - Deps: T5.6, T5.10, T5.14–T5.17 (todas las [L]). Cubre: R5.19 (a–h),
    R5.1, R5.2, R5.16 (G-1), S5-15, E5.16-b, Done 10 de la proposal §6.

- [x] **T5.20 — CORRIDA CANÓNICA `qa_c5_smoke.sh` (tarea FINAL del corte).**
  (**hecho** 2026-10-25 — corrida canónica DEFINITIVA (run 5, con las 2 ratificaciones de gate
  aplicadas): 13/13 PASS (E5.21–E5.33), exit 0, 0 FAIL/BLOQUEADO, pila 3×healthy tras recomposición
  ÚNICA `--build --force-recreate`, volumen pgdata intacto [sin `down -v`]; log
  `/tmp/qa_c5_run5.log`. Una 1.ª canónica previa (run 4, pre-ratificación) también 13/13:
  `/tmp/qa_c5_run4.log`. E5.28 pasó tras el hallazgo del envoltorio de FrameworkServlet en el guard.)
  - Pasos: (a) ejecutar `bash scripts/qa_c5_smoke.sh` — pre-fase
    (recomposición ÚNICA `--force-recreate`, 3×healthy, sondeo `/me` → 401
    `invalid_token`) + E5.21–E5.33; (b) el veredicto CANÓNICO exige
    **13/13 PASS** (E5.21–E5.33) y `exit 0` en el log de esta corrida; (c)
    pila 3×healthy al cierre y volumen `pgdata` intacto (NUNCA `down -v`).
    Incluye el cierre ACTIVO de G-2 (E5.31: desarchivar → MISMA key →
    202) y el orden de gates en pila (E5.33).
  - Cierre: veredicto canónico `exit 0` con 13/13 PASS en el log. ESTA es
    la tarea finalDone: con ella, los criterios de Done de la proposal §6
    quedan todos cubiertos (tabla "Criterios Done de C5" abajo).
  - Deps: T5.18 + T5.19. Cubre: E5.21–E5.33 (veredicto definitivo), G-2
    (E5.31), R5.18 (parejas mock↔psql como mínimo 401/403/400×2/413 +
    all-or-nothing).

---

## Trazabilidad tarea → escenarios

| Tarea | Escenarios / requisitos que cubre (directa) |
|---|---|
| T5.1 (lookup B1) | habilita E5.15 (SELECT), E5.21, E5.24; R5.11-a, D-C5-1 |
| T5.2 (principal) | habilita E5.12, E5.13; R5.6-a |
| T5.3 (entry point) | habilita E5.12, E5.16, E5.24; R5.5, S5-11 |
| T5.4 (guard) | habilita E5.27, E5.28, E5.33, E5.12; R5.4, S5-9/S5-12, D-C5-1 (Javadoc) |
| T5.5 (SecurityConfig + red anti-contaminación) | R5.6, R5.3.1, R5.19(b)(c), E5.16-b (con T5.19) |
| T5.6 (pin SELECT) | E5.15 (SELECT), R5.19a |
| T5.7 (LogIngestRequest) | habilita E5.1–E5.14, E5.24-c; S5-1, S5-2, R5.24-a/b |
| T5.8 (LogValidado) | habilita E5.19–E5.23; S5-15, S5-8 |
| T5.9 (LogsRepository) | habilita E5.19, E5.22, E5.23; R5.11-b/c/d, D-4, R-4 |
| T5.10 (pin INSERT) | E5.15 (INSERT), R-4 |
| T5.11 (IngestListener) | habilita E5.20; R5.13, S5-10 |
| T5.12 (IngestService) | habilita E5.3–E5.11, E5.14, E5.19, E5.20, E5.23, E5.26, E5.30; R5.12, R5.24, S5-3…S5-8, D-7 |
| T5.13 (IngestController) | habilita E5.1–E5.14, E5.24-c y pila E5.21–E5.26, E5.30–E5.32; R5.7–R5.10, S5-1/S5-3, D-10 |
| T5.14 (MVC tests) | E5.1–E5.14, E5.16, E5.24-c |
| T5.15 (unit service/seam) | E5.18, E5.19, E5.20 |
| T5.16 (unit bind) | E5.34, R-6, S5-9 |
| T5.17 (higiene) | E5.17, R5.15 |
| T5.18 (smoke script + smoke-of-the-smoke) | E5.21–E5.33 (codificación), G-2 (E5.31), R5.17 |
| T5.19 (mvn completo + intocables + ausencias) | R5.19 (a–h), R5.1, R5.2, R5.16 (G-1), S5-15, E5.16-b, Done 10 |
| T5.20 (corrida canónica) | E5.21–E5.33 (veredicto definitivo), G-2 (E5.31), R5.18 |

**Cobertura de los 34 escenarios:** E5.1–E5.11, E5.14, E5.16, E5.24-c →
T5.14; E5.12, E5.13 → T5.14 (habilitados por T5.3/T5.4/T5.5); E5.15 →
T5.6 + T5.10; E5.17 → T5.17; E5.18–E5.20 → T5.15; E5.34 → T5.16;
E5.21–E5.33 → T5.18 (codificación) + T5.20 (veredicto). Sin escenarios
huérfanos: E5.16-b y los requisitos sin escenario (R5.19, R5.2, G-1) se
cierran por re-corrida + review de ausencias en T5.19 (patrón C4).

## Criterios Done de C5 (proposal §6, trazados a tareas)

| # | Criterio Done | Cubierto por |
|---|---|---|
| 1 | POST individual con key real: 202 `{"accepted":1}` EXACTO + fila psql completa (project_id deducido, level/environment, timestamp UTC, received_at servidor, correlation_id, metadata JSONB) | T5.14 (E5.1) + T5.18/T5.20 (E5.21, E5.29, E5.30) |
| 2 | Batch de 200: 202 `{"accepted":200}`; received_at EXACTO común (regla 9); ids en orden de VALUES | T5.14 (E5.2) + T5.18/T5.20 (E5.22) |
| 3 | All-or-nothing: 400 con `index`/`field` del culpable + `count(*)` invariante | T5.14 (E5.11) + T5.18/T5.20 (E5.23) |
| 4 | Los 5 errores de la ficha 07 contra la pila + orden fail-fast 413→401→403→400 | T5.18/T5.20 (E5.24, E5.25, E5.26, E5.27; orden E5.33) |
| 5 | G-2: desarchivar → MISMA key → 202 (reglas 11+12) | T5.18/T5.20 (E5.31) |
| 6 | Fronteras de batch y 413: 0/201 → 400; 1 MiB exacto vs +1 → 413; 413 chunked | T5.14 (E5.10) + T5.18/T5.20 (E5.32, E5.27, E5.28) |
| 7 | Validación por campo con fronteras de BYTES | T5.14 (E5.5–E5.9) + T5.18/T5.20 (E5.30) |
| 8 | Tests [L] MVC/unit: literales, 401 nuevo, 403, 400 code/field/index, pins SQL, SecurityConfig byte-a-byte, higiene, unit parse + received_at | T5.14, T5.6 + T5.10 (E5.15), T5.5/T5.19 (E5.16-b), T5.17, T5.15, T5.16 |
| 9 | Seam: IngestListener UNA vez tras COMMIT, args exactos, no-op con lista vacía | T5.15 (E5.20), T5.11 |
| 10 | NO pasa: fronteras de la proposal §3 (streaming C7 real, consulta C6, front, migraciones, panel tocado, CORS, starter-validation, Testcontainers, DIVE, G-1 reabierta) | T5.19 (diff-0 + revisión de ausencias) |

**Corte C5 TERMINADO cuando:** T5.1–T5.19 marcadas `- [x]` (T5.5 con la
re-corrida 77/77 INMEDIATA ya ejecutada; T5.19 con `BUILD SUCCESS` +
diffs-0 + ausencias verificadas) y T5.20 con corrida canónica `exit 0`
(13/13 PASS, E5.21–E5.33, incl. E5.31/G-2). Sin push a remoto; sin commits
(los hace sdd-apply/archive).