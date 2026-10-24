---
change: "C4"
artifact: design
last_updated: 2026-10-24
status: draft
---

# Design — C4 · API keys de ingesta

> Fase sdd-design. Implementa ARQUITECTÓNICAMENTE el spec closed de C4
> (decisiones S1–S7 del §0.2 y requisitos R4.1–R4.16): NO renegocia NINGUNA
> decisión — los literales de API/SQL/errores son los CITADOS del spec y este
> documento los materializa en firmas, ficheros y flujos de rama. Stack real:
> Spring Boot 3 / Java 21, spring-jdbc (`JdbcTemplate`), PostgreSQL 17,
> Docker Compose en repo. `SecurityConfig`, `V1__init.sql`, `CorsConfig`,
> `application-test.yml`, `pom.xml`, `docs/**` y
> `frontend/public/mockups` INTOCABLES (§6).

## 0. Resumen arquitectónico

```text
HTTP ──► SecurityConfig cadena 2 (/api/v1/panel/**) ── 401 sin JWT (entry point C2)
        │
        ▼
ApiKeyController (FINO: orquesta ramas, sin @Valid, sin @ExceptionHandler)
   ├─► ProjectsRepository.findById(id)  [C3, existente — lectura de status]
   ├─► ApiKeyService  (FRONTERA CRYPTO: newKey/sha256/prefixDe/tiempoConstanteIgual)
   │      └─ SecureRandom estático + MessageDigest.isEqual (primitivas ADR-4)
   └─► ProjectsRepository.regenerateKey(id, hash, prefix) / revokeKey(id)
          └─ 2 estamientos NUEVOS aditivos (UPDATE…RETURNING — 404 por empty)
```

Tres ficheros NUEVOS en `com.projectlog.keys` (ancla del `package-info` de
C1), un fichero de C3 EDITADO en modo puramente aditivo
(`ProjectsRepository`: 2 métodos + 2 constantes + Javadoc), cero cambios en
config/migraciones/pom/front/docs. La orquestación de ramas (404→409→generar)
vive en el controller, como en C3; el service NO orquesta: es la frontera
crypto pura y sin estado.

## 1. Mapa del paquete `com.projectlog.keys`

### 1.1 `keys/ApiKeyController.java` — `@RestController`, controller FINO

Responsabilidad: los 2 mappings del Schema §3.2 + la orquestación de ramas
de error (R4.8/R4.9) + la construcción de cuerpos de error (§3.1) vía
`ObjectMapper`. SIN `@Valid` (no hay cuerpo de request), SIN
`@ExceptionHandler` (S6: `DuplicateKeyException` NO se captura; no aplica
`HttpMessageNotReadable` sin `@RequestBody`; además los handlers de C3 son
class-scoped y nunca habrían aplicado a este controller).

```java
@RestController
public class ApiKeyController {

    /** Duplicados de C3 con ancla (cierre S5) — literales citados, no redefinidos. */
    static final String CODE_NOT_FOUND = "not_found";
    static final String MSG_NOT_FOUND  = "No existe (quizás fue eliminado)";
    /** Anclado: code = contrato del Schema §3.4; en el panel C4 lo porta un
     *  409 (Decisión 3 REVISADA, gate 2026-10-24); message = cierre S3. */
    static final String CODE_PROJECT_ARCHIVED = "project_archived";
    static final String MSG_PROJECT_ARCHIVED =
            "El proyecto está archivado: desarchívalo antes de generar o"
                    + " regenerar su API key";

    ApiKeyController(ProjectsRepository projectsRepository,
            ApiKeyService apiKeyService, ObjectMapper objectMapper)

    @PostMapping("/api/v1/panel/projects/{id}/api-key/regenerate")
    public ResponseEntity<Object> regenerar(@PathVariable long id)

    @DeleteMapping("/api/v1/panel/projects/{id}/api-key")
    public ResponseEntity<Object> revocar(@PathVariable long id)

    /** Cuerpo estándar §3.1 {code, message} — SIN field (véase §4). */
    private ResponseEntity<Object> error(HttpStatus estado, String codigo,
            String mensaje)
}
```

Rutas COMPLETAS en los mappings literales (R4.6), sin `@RequestMapping` de
clase: el literal de Schema queda visible verbatim en la anotación y no hay
nada que colisione con los 4 mappings de C3 (Spring resuelve por path
exacto). Respuestas: `ResponseEntity<Object>` en ambos, patrón C3.

### 1.2 `keys/ApiKeyService.java` — `@Service`, frontera crypto

Responsabilidad: las ÚNICAS 4 primitivas de la feature; SIN estado de
instancia, SIN BD, SIN logging. `@Service` (y no utilidad 100 % estática)
porque los tests MVC de C3/C4 anclan el comportamiento del controller vía
bean mockeado (`@MockitoBean` → `verifyNoInteractions` en E4.5/E4.6 — eso
exige un bean inyectable).

```java
@Service
public class ApiKeyService {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String PREFIJO = "plk_";

    public String newKey()        // "plk_" + Base64url UNPADDED de 32 B → 47 chars
    public byte[] sha256(String key)      // digest del String COMPLETO, UTF-8
    public String prefixDe(String key)    // key.substring(0, 10)
    public boolean tiempoConstanteIgual(byte[] a, byte[] b) // MessageDigest.isEqual
}
```

- `newKey()`: `PREFIJO + Base64.getUrlEncoder().withoutPadding()
  .encodeToString(bytes32De(ALEATORIO))`. La generación es UNA por llamada
  (nada de buffers reutilizados).
- `sha256(String)`: `MessageDigest.getInstance("SHA-256").digest(
  key.getBytes(StandardCharsets.UTF_8))` — NUEVO `MessageDigest` POR llamada
  (no es thread-safe como estado compartido). Semántica CONTRACTUAL para C5:
  `sha256(header_X_API_Key)` comparable en binario contra `api_key_hash` —
  ni trim de `plk_`, ni hex, ni salt (spec §0.2).
- `tiempoConstanteIgual(byte[], byte[])`: wrapper de
  `MessageDigest.isEqual(a, b)` — primitiva nacida aquí que C5 reutiliza en
  el hot-path (Decisión 5: primitivas en C4 SÍ, lookup NO).
- NO Argon2id/BCrypt (ADR-4): hash rápido = lookup O(1) sobre el índice
  parcial único; el KDF lento lo destruiría.

### 1.3 `keys/ApiKeyResponse.java` — record de salida (solo 201)

```java
public record ApiKeyResponse(String key, String prefix, String created_at) {}
```

Componentes snake_case → Jackson serializa los accessors con los nombres
EXACTOS `key`, `prefix`, `created_at` SIN `@JsonProperty` (patrón
`ProjectDto`/`LoginResponse` de C2, citado en R4.7). El ORDEN de los
componentes fija el orden de las claves JSON del contrato. Este record es el
ÚNICO lugar donde la key completa vive como valor transportado (§5).

### 1.4 Grafo de dependencias (frontera R4.1)

```text
com.projectlog.keys  ──►  com.projectlog.projects.repository  (ProjectsRepository)
                     ──►  com.projectlog.projects.dto         (ProjectDto, retorno de findById)
keys ► LoginController.EXPIRES_AT_FORMATTER  → INDIRECTA: el formatter se usa DENTRO del repo (cierre S4), keys NUNCA lo importa
```

- El PAQUETE-edge `keys → projects` es uno: `ProjectsRepository` (los 3
  métodos usados) + su DTO de retorno `ProjectDto` (solo lectura de
  `status()` en la rama del 409). Es consecuencia estructural de usar
  `findById` (§2.3) — dentro de la dependencia permitida por R4.1, más
  estrecha de lo que R4.1 llega a exigir: `LoginController` NO aparece en
  imports de `keys` (el repo ya formatea; S4).
- `keys` NO importa nada de `config`, `auth` ni de otros paquetes de
  features. Cero dependencias nuevas en `pom.xml`.

## 2. Flujo de ramas de los endpoints (EXACTO según spec)

### 2.1 `POST /api/v1/panel/projects/{id}/api-key/regenerate` — R4.8

```text
(1) 401 sin JWT ................ cadena 2 (automático, previo al controller)
(2) 404 no existe .............. findById(id) vacío → error(404, not_found, MSG_NOT_FOUND)
(3) 409 ARCHIVED ............... fila.status().equals("ARCHIVED") → error(409, project_archived, MSG_...)
(4) generar .................... key  = apiKeyService.newKey()
                                 prefix = apiKeyService.prefixDe(key)
                                 hash  = apiKeyService.sha256(key)
(5) escribir ................... regenerateKey(id, hash, prefix)
(6) 404 (carrera) .............. Optional.empty() del RETURNING → error(404, …)
(7) 201 ........................ ApiKeyResponse(key, prefix, created_at)
```

Esqueleto del cuerpo (el orden de (4) es newKey → prefix/sha256; el hash
SALE del String completo recién generado — nunca recíproco):

```java
Optional<ProjectDto> fila = projectsRepository.findById(id);
if (fila.isEmpty()) {
    return error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, MSG_NOT_FOUND);
}
if ("ARCHIVED".equals(fila.get().status())) {           // (2) ANTES de (3)
    return error(HttpStatus.CONFLICT, CODE_PROJECT_ARCHIVED,
            MSG_PROJECT_ARCHIVED);
}
String key = apiKeyService.newKey();                     // (3) ANTES de (4)
String prefix = apiKeyService.prefixDe(key);
byte[] hash = apiKeyService.sha256(key);
Optional<String> creado = projectsRepository.regenerateKey(id, hash, prefix);
return creado.<ResponseEntity<Object>>map(ts -> ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiKeyResponse(key, prefix, ts)))     // (7)
        .orElseGet(() -> error(HttpStatus.NOT_FOUND,
                CODE_NOT_FOUND, MSG_NOT_FOUND));               // (6)
```

Orden GARANTIZADO por construcción: el 404 precede al 409 (no se puede
decidir "archivado" de una fila que no existe) y el 409 precede a la
generación (una key JAMÁS se genera y descarta sobre un archivado — cierra
S2; el fail-falso barato — error de estado — falla ANTES de tocar crypto y
sin consumir entropía). La rama (6) es la segunda barrera del
empty-RETURNING, gratis por diseño (R4.4); en C4 es inalcanzable (DELETE de
proyectos es C10) pero existe como blindaje de carrera por si C10 llega.

### 2.2 `DELETE /api/v1/panel/projects/{id}/api-key` — R4.9

```text
(1) 401 sin JWT ................ cadena 2 (automático)
(2) escribir ................... revokeKey(id) → true | false
(3) false (→ 404) .............. error(404, not_found, MSG_NOT_FOUND)
(4) true (→ 204) ............... ResponseEntity.status(NO_CONTENT).build() SIN cuerpo
```

```java
return projectsRepository.revokeKey(id)
        ? ResponseEntity.status(HttpStatus.NO_CONTENT).build()
        : error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, MSG_NOT_FOUND);
```

SIN chequeo de `status` (Decisión 3 REVISADA: revocar sobre ARCHIVADO es
PERMITIDO, sin rama extra — E4.7/E4.26). Con el `WHERE id = ?` a secas del
cierre S1, `true` llega tanto para fila con key como para fila sin key
(204 idempotente, Decisión 2): la "escritura" de tres NULLs sobre una fila
que ya está en NULL es un no-op lógico pero una escritura real
(`updated_at` avanza) y NO cambia el código HTTP. Nota de tipado (micro-decisión
D-2, §7): `ResponseEntity.status(HttpStatus.NO_CONTENT).build()` en lugar del
ilustrativo `ResponseEntity.noContent().build()` del spec — el `BodyBuilder`
genérico infiere `ResponseEntity<Object>` y encaja con la firma del mapping;
semántica HTTP idéntica (204 sin cuerpo).

### 2.3 El chequeo de ARCHIVED antes de generar — decisión (D-1)

**Elección: reutilizar `ProjectsRepository.findById(id)` (C3, existente) en
el controller.** Es lo más simple y limpio, y lo que el cierre S2 ya fija:

- `ProjectDto.status()` existe y es `String` del enum del CHECK — el
  controller compara contra el LITERAL `"ARCHIVED"` con
  `"ARCHIVED".equals(...)` (null-safe, patrón C3 en `listar`/`editar`).
- CERO SQL nuevo de lectura: ni un `existsStatus`, ni un
  `SELECT status FROM …` adicional — el design no añade estamentos al repo
  más allá de los 2 UPDATE aditivos del spec (R4.3), preservando el Javadoc
  de C3 ("pieza compartida downstream: C4 usa `findById`").
- Alternativas RECHAZADAS:
  - `existsStatus(long id)`/`findStatusById(...)` en el repo — un 3er
    estamiento nuevo donde `findById` ya sirve; duplicaría lecturas y rompería
    la frontera "solo 2 estamientos" de R4.3.
  - Guard de estado en el SQL del UPDATE (p. ej. `WHERE id = ? AND status <>
    'ARCHIVED'` o CTE) — ver Riesgo D-R2 (§8): divergiría del literal
    pinado en E4.13 y haría el empty-RETURNING ambiguo (0 filas ¿id
    inexistente o archivado? → el 404/409 del paso (6) perdería
    determinismo). El chequeo lee el estado ANTES, el write es ciego.
  - Chequeo en el service (orchestración del flujo allí) — el spec R4.8 lo
    sitúa en el controller y C3 ya fijó el patrón "service no existe /
    controller orquesta" para el panel; mantener la simetría.

Nota TOCTOU residual aceptada (se amplía en Riesgo D-R2 §8): entre (2) y (5)
un PATCH de archivado concurrente podría colar una key sobre un ARCHIVADO;
se documenta, no se blinda con SQL.

## 3. Repositorio — los 2 estamientos NUEVOS (aditivos, R4.2–R4.4)

`ProjectsRepository` recibe, AL FINAL de la clase, tras `conjuntoSet`:

```java
/** (1) generar/regenerar — mono-sentencia; RETURNING key_created_at para la 201. */
static final String UPDATE_REGENERAR_KEY =
        "UPDATE projects"
                + " SET api_key_hash = ?, api_key_prefix = ?,"
                + " key_created_at = now(), updated_at = now()"
                + " WHERE id = ?"
                + " RETURNING key_created_at";

/** (2) revocar — los tres a NULL JUNTOS (invariante Schema §2); fila 0 → false → 404. */
static final String UPDATE_REVOCAR_KEY =
        "UPDATE projects"
                + " SET api_key_hash = NULL, api_key_prefix = NULL,"
                + " key_created_at = NULL, updated_at = now()"
                + " WHERE id = ?"
                + " RETURNING id";
```

```java
@Transactional // defensa documentada (misma que los 4 métodos de C3): mono-sentencia
public Optional<String> regenerateKey(long id, byte[] hash, String p) {
    List<OffsetDateTime> filas = jdbcTemplate.query(UPDATE_REGENERAR_KEY,
            (rs, n) -> rs.getObject("key_created_at", OffsetDateTime.class),
            hash, p, id);
    return filas.isEmpty() ? Optional.empty()
            : Optional.of(filas.get(0).format(LoginController.EXPIRES_AT_FORMATTER));
}

@Transactional
public boolean revokeKey(long id) {
    List<Long> filas = jdbcTemplate.query(UPDATE_REVOCAR_KEY,
            (rs, n) -> rs.getLong("id"), id);
    return !filas.isEmpty();
}
```

Puntos FIRME del design (todos heredados del spec):

- **`List` + empty-check, NUNCA `queryForObject`**: la fila-0 es un caso
  legal de 404, no una excepción (R4.4) — patrón `findById` de C3 en
  versión de una ida (sin pre-SELECT; el UPDATE no-op ante id inexistente
  NO escribe: seguro ante carreras).
- **El 404 sale del empty-RETURNING**: una única ida por endpoint de
  escritura; mejor que el patrón E3.19 de C3 (dos idas) — y sin pre-SELECT
  no hay hueco de carrera sobre la inexistencia.
- **Formateo de `key_created_at` EN el repo** (cierre S4): el método
  devuelve `Optional<String>` ya ISO-Z UTC con
  `LoginController.EXPIRES_AT_FORMATTER` (pública de C2, ya reutilizada por
  el `FILA` de C3 — misma responsabilidad, cero segunda convención de
  formateo). El controller NO toca fechas.
- **El WHERE de `revokeKey` es `id = ?` a secas** (cierre S1: la variante
  `AND api_key_hash IS NOT NULL` queda RECHAZADA — 0 filas sería ambiguo
  entre "id inexistente → 404" y "id existente sin key → 204" y obligaría a
  un segundo `findById` para desempatar).
- **Binding de parámetros**: en (1), el orden es exactamente el de los `?`:
  `hash` (byte[] → BYTEA del driver), `p` (prefix TEXT), `id`. El hash viaja
  ya como digest binario — el repo NO calcula ni re-hashea nada (escritura
  ciega; R4.4).
- **Intocable**: el UPDATE existente de C3 (`SQL_UPDATE_PREFIX` +
  `conjuntoSet` + `SQL_UPDATE_SUFFIX`) y las 4 operaciones de C3 no cambian
  NI UNA línea; ninguna rama de `conjuntoSet` menciona columnas de key
  (frontera R-4/E3.19, verificada por el pin E4.13). El Javadoc de clase del
  repo se AMPLÍA aditivamente (C4 añadido a la lista de consumidores).
- **Visibilidad de las 2 constantes** (micro-decisión D-3 con nota de
  divergencia — §7 y Riesgo D-R5): `static final` PACKAGE-PRIVATE (no
  `private` como dice literalmente R4.3) para que el pin E4.13 pueda
  leerlas; ver justificación en §7.

## 4. Errores — constantes duplicadas y helper (R4.10 / cierre S5)

- **Duplicación justificada y verificada contra el código real de C3**: en
  `ProjectsController`, `CODE_NOT_FOUND` y `MSG_NOT_FOUND` son
  `static final` package-private y `error()` es `private` — INVISIBLES para
  el paquete `keys`. Duplicar (con Javadoc de ancla que cita su origen) es
  MENOR diff que editar la visibilidad de un corte cerrado (patrón que C3
  ya usó al replicar cuerpos de C2 — cero diffs sobre ficheros anteriores).
- `MSG_PROJECT_ARCHIVED`/`CODE_PROJECT_ARCHIVED`: el `code` es CONTRATO del
  Schema §3.4; el `message` es el literal del cierre S3 (los docs no fijan
  message para ese code; coherencia C5 anotada en spec §7: mismo `code`,
  message de ingesta distinto).
- **`error()` PRIVATE y SIN varargs**: en C3 la firma es
  `error(HttpStatus, String, String, String... campo)`; en C4 NINGÚN error
  lleva `field` (404 y 409 van sin `field`, R4.7) — el helper replica el
  cuerpo de C3 (`LinkedHashMap` ordenado `code` → `message`,
  `objectMapper.valueToTree`, `MediaType.APPLICATION_JSON`) pero se estrecha
  a exactamente 3 parámetros para que la rama `field` sea físicamente
  imposible (micro-decisión D-4, §7).
- **Cero `@ExceptionHandler` en C4** (R4.10): no hay `@RequestBody` (no
  aplica `HttpMessageNotReadable`); NO se captura
  `DuplicateKeyException`/`DataIntegrityViolationException` (cierre S6:
  colisión astronómica R-4 → 500 sin mapeo; además ningún handler de C3
  habrían aplicado por ser class-scoped).
- NINGÚN `code` de error nuevo fuera de los 4 de R4.10 (reutilizados
  `not_found` y `project_archived`; R4.16d).

## 5. Threading, estado e higiene del secret

- **`SecureRandom` `private static final` del service**: thread-safe por
  contrato del JDK (una generación por llamada, sin estado compartido
  mutable) — seguro bajo Tomcat (múltiples requests concurrentes).
- **`MessageDigest` NUEVO por llamada** en `sha256` (no thread-safe como
  estado compartido — R4.11).
- **El service no tiene instancia-estado**: las únicas cosas estáticas son
  `ALEATORIO` y `PREFIJO`; todo lo demás son parámetros/retornos locales.
- **Ciclo de vida de la key completa (47 chars)**: nace en `newKey()` →
  vive como variable locales de `regenerar` (`key`, y sus derivados
  `hash`/`prefix`) → viaja UNA única vez en el cuerpo de la 201
  (`ApiKeyResponse.key`) → muere al terminar el request. `hash` es el único
  derivado que toca BD; `prefix` (10 chars, NO secreto) es el único
  derivado persistido en claro.
- **Higiene de logs (R-2b/R4.13)**: NINGUNA clase de `com.projectlog.keys`
  declara logger ni emite evento — la key no puede filtrarse porque no hay
  statement de log. La garantía NO se delega en esa ausencia: se PRUEBA con
  el patrón `LogHygieneTest` de C2 (`ListAppender` de logback sobre los
  loggers de las clases de `keys`, DEBUG incluido, escenario E4.14) y en el
  smoke la key revelada va a un tmpdir 700 y JAMÁS a consola (E4.15 nota).
- **En psql** la key solo existe como parámetro del tmpdir para computar
  `sha256('<key>'::bytea)` (E4.19) — nunca impresa.

## 6. Qué NO toca este design (frontera con C3/C5/C8 y los intocables)

- **SecurityConfig** (3 cadenas intocables): los 2 mappings nuevos NACEN
  bajo la cadena 2 (`/api/v1/panel/**` → `authenticated()`, matcher por
  prefijo) → 401 sin JWT automático, cero líneas de seguridad.
- **`V1__init.sql`**: columnas `api_key_hash BYTEA`/`api_key_prefix TEXT`/
  `key_created_at TIMESTAMPTZ` + índice parcial único
  `projects_api_key_hash_idx … WHERE api_key_hash IS NOT NULL` ya existen
  de C1; cero migraciones.
- **CorsConfig** (cierre S7): sin `X-API-Key` en `allowedHeaders` y NO
  falta — el panel manda JWT por `Authorization` (Schema §5) y la cadena 1
  de ingesta no tiene `cors()`.
- **`application-test.yml`, pom.xml** (sin starter-validation — no hay
  cuerpo de request y `@Valid` sería no-op), entry point, `docs/**`,
  `frontend/public/mockups` (Decisión 4: sin wiring; la nota "C4: sin
  regenerate/revocar" sigue siendo verdadera al cerrar C4).
- **C5** (solo frontera documentada en spec §7, NADA construido): ni
  `findByApiKeyHash` ni filtro `X-API-Key` ni los 401
  `api_key_invalid`/403 `project_archived` de INGESTA; la cadena 1 sigue
  `denyAll()` con 401 `invalid_token` a todo `/api/v1/ingest/**` (firma
  temporal del límite; el humo E4.15 la usa como sondeo).
- **La ingesta del 07**: no se adelanta media C5 para demostrar "la key ya
  no autentica" (Decisión 1) — el smoke de C4 demuestra la invalidación por
  transición de estado (hash anterior ≠ nuevo en psql).

## 7. Micro-decisiones de design y notas de divergencia con el spec

| # | Decisión | Fundamento | Relación con el spec |
|---|---|---|---|
| D-1 | Chequeo de ARCHIVED vía `findById` (C3) en el controller, sin SQL de lectura nuevo | Cierre S2 ya lo sitúa así; `ProjectDto.status()` existe; `existsStatus` sería un 3er estamiento | CONFORME a S2/R4.8 |
| D-2 | 204 construido con `ResponseEntity.status(HttpStatus.NO_CONTENT).build()` (y no `ResponseEntity.noContent().build()`) | `HeadersBuilder.build()` devuelve `ResponseEntity<Void>` (no asignable a `ResponseEntity<Object>`); el `BodyBuilder` genérico infiere `T = Object`. Semántica HTTP idéntica | El literal de R4.7 es ilustrativo del CONTRATO (204 sin cuerpo); anotado para que tasks no copie el snippet al pie de la letra y tropiece en compilación |
| D-3 | `UPDATE_REGENERAR_KEY`/`UPDATE_REVOCAR_KEY` package-private (`static final String`), y el pin E4.13 en `com.projectlog.projects.repository` (mismo paquete, patrón `ProjectsUpdateSqlTest`) | R4.3 dice `private static final`, pero E4.13 exige PINEAR su contenido: un test en otro paquete no puede leer un `private`. La casa ya tiene el patrón: `COLS`/`SQL_UPDATE_PREFIX`/`SQL_UPDATE_SUFFIX` y `conjuntoSet` son package-private justamente para eso (C3). Alternativa descartada: reflejar con `getDeclaredField` + `setAccessible` (frágil, sin precedente en el repo) | **DIVERGENCIA MENOR con el literal "private" de R4.3** — no cambia SQL, visibilidad externa ni contrato (package-private ≠ API pública; nadie fuera del paquete `projects.repository` lo consume). Anotada aquí y en el reporte; NO se corrige el spec desde el design |
| D-4 | `error()` private con firma EXACTA de 3 parámetros (sin varargs `field`) | En C4 ningún error lleva `field` (R4.7); la rama `field` queda físicamente ausente | CUMPLE R4.10 ("replica del de C3; en C4 solo se usa sin field") estrechándolo — decisión dentro del margen del spec |
| D-5 | Cuerpo del 404/409 como `LinkedHashMap` ordenado `code` → `message` (sin `field`) | Cuerpo estándar §3.1; el orden de las claves es el de C3 (`code` primero, `message` después, `field` solo cuando aplica) | CONFORME a R4.7/R4.10 |
| D-6 | Sin Javadoc de mención a C5 en las primitivas más allá del ancla "primitiva C5" ya fijada por el spec | La frontera C5 vive en spec §7 (fuente única); duplicarla aquí sería segunda fuente | CONFORME a R4.5/§7 |

## 8. Riesgos de diseño y mitigaciones

| ID | Riesgo | Impacto | Mitigación / aceptación |
|---|---|---|---|
| D-R1 | **Colisión de hash en el índice parcial único** (R-4, cierre S6): 2ª key con el mismo SHA-256 → `DuplicateKeyException` → 500 sin mapeo | Teórico: 256 bits, índice parcial solo sobre filas no nulas; astronómico | ACEPTADO documentado: NO se captura DIVE (sin literal de error en los docs; coherente con C3, que tampoco mapea el DIVE genérico). Si apareciera, 500 honesto > 409 inventado sin ancla |
| D-R2 | **Ventana TOCTOU entre el chequeo (2)/(3) y la escritura (5)**: un `PATCH {"status": "ARCHIVED"}` concurrente (C3) en el lapso ms escribiría una key sobre un ARCHIVADO sin 409 | Bajo: panel mono-usuario (sin `hasRole`, ADR-14), ventana de milisegundos, y el estado final ("key sobre archivado") es el mismo que la decisión alternativa "permitir" toleraría; la ingesta de C5 solo mira `status` de la fila, no el historial, y la key es revocable | ACEPTADO documentado: blindearlo con `AND status <> 'ARCHIVED'` divergiría del literal pinado E4.13 y haría ambiguo el empty-RETURNING (¿404 por id inexistente o por archivado?) — ver §2.3. El orden del cierre S2 ya elimina la versión COSTOSA del problema (generar-y-descartar un hash nunca ocurre) |
| D-R3 | **Higiene del secret en logs** (R-2b/R4.13): un log futuro (o una excepción que imprima argumentos) filtraría la key | Alto si ocurriera (credencial filtrada), improbable en C4 (cero statements de log en `keys`) | Triple: (a) el design prohíbe loggers en el paquete; (b) test ListAppender E4.14 (patrón `LogHygieneTest` de C2, DEBUG incluido); (c) en smoke, key solo en tmpdir 700. La primitiva `tiempoConstanteIgual` y el hash NO imprimen nada |
| D-R4 | **Entorno heredado de C3** (R-7/R4.15): smokes rojos contra imagen vieja, `updated_at` con epoch, booleanos `t/f` vs `true/false`, ISO-8859-1 del 401 | Medio (falsos rojos) | Heredado tal cual y OBLIGATORIO en `qa_c4_smoke.sh`: recomposición única con `--force-recreate`, `mvn test` dentro del builder `maven:3.9-eclipse-temurin-21`, psql `-qAt` con `FLOOR(EXTRACT(EPOCH …))::bigint` donde toque, smoke-of-the-smoke antes de la canónica, nombres sufijados por ejecución. Sin novedad de diseño |
| D-R5 | **`updated_at` avanza en el revoke idempotente sin key** (cierre S1): consumidor que lea `updated_at` como "cambio de contenido" verá avance en un 204 "no-op" | Cosmético, un solo operador | La semántica fijada es "última ESCRITURA sobre la fila" (literalmente cierto: la fila se reescribe); el smoke E4.24 lo anota y NO lo aserta. Si algún día molesta, la variante `AND api_key_hash IS NOT NULL` del cierre S1 sigue disponible sin cambiar el contrato HTTP |
| D-R6 | **Divergencia menor D-3** (visibilidad de las 2 constantes SQL: package-private vs `private` de R4.3) | Nulo en runtime (nadie fuera del paquete las consume) | Declarada AQUÍ (no corregida en el spec — regla del design): si el auditor del spec la rechaza, el fallback es reflection en E4.13, con coste de fragilidad. Decidir en review de tasks/qa |

## 9. Trazabilidad design → spec (S1–S7, R4.x, E4.x)

| Elemento de design | Decisiones que cubre | Escenarios de verificación |
|---|---|---|
| Paquete `com.projectlog.keys` (3 ficheros, package-info intacto, sin subpaquete dto) | R4.1, D5 del gate | E4.1–E4.14 (review de ficheros + suite [L]) |
| 2 mappings de ruta completa, sin `@Valid`, sin `@RequestBody`, sin `@ExceptionHandler` | R4.6, R4.10, S6 | E4.1, E4.10, E4.18, E4.21, E4.5/E4.8 |
| Rama de `regenerar`: 404 → 409 → generar → escribir → 404-carrera → 201 | R4.8, S2, D3 del gate (REVISADA) | E4.1, E4.4–E4.6 [L]; E4.18, E4.25, E4.27 [S] |
| Rama de `revocar`: sin chequeo de status, revokeKey true→204 / false→404, 204 idempotente | R4.9, S1, D2/D3 del gate | E4.7, E4.8, E4.10 [L]; E4.21, E4.24, E4.26 [S] |
| Chequeo ARCHIVED vía `findById` (D-1) | R4.8, S2 | E4.5 (404 sin tocar escritura), E4.6 (409 sin tocar generación) |
| `regenerateKey`/`revokeKey` (List+RETURNING, `@Transactional`, sin pre-SELECT, WHERE a secas en revoke, formateo en repo) | R4.2–R4.4, S1, S4 | E4.13 [L] (pin de literales); E4.19–E4.22, E4.24, E4.26 [S] (pila) |
| `ApiKeyService` (newKey/sha256/prefixDe/tiempoConstanteIgual, SecureRandom estático, MessageDigest por llamada) | R4.11, D5, §0.2 (contrato hash) | E4.2, E4.3, E4.11, E4.12; higiene E4.14 |
| `ApiKeyResponse` record snake_case (orden de claves) | R4.7 (201), patrón C2 | E4.1, E4.2, E4.4, E4.18 |
| Constantes duplicadas + `error()` private sin field | R4.10, S5, D3 | E4.5, E4.6 (cuerpos exactos) |
| Semántica de estado (sobrescritura al instante, tres NULLs juntos, derivados C3 sin diff) | R4.12, regla 11 Schema | E4.20, E4.22, E4.23, E4.29 [S] |
| Higiene: sin loggers en `keys`, ListAppender, key solo en la 201/tmpdir | R4.13, R-2b | E4.14 [L]; nota E4.15 [S] |
| Seguridad intocable (cadena 2, SecurityConfig, V1, CorsConfig, pom, front, docs) | R4.14, R4.16, S7, D4 | E4.9, E4.28 (401); reviews de ausencia |
| Frontera C5 (solo documentada) | R4.5, D5, S3 (coherencia `project_archived`) | spec §7 (review de ausencia) |

## 10. Entregables que este design habilita (resumen para sdd-tasks)

- **Nuevos**: `keys/ApiKeyController.java`, `keys/ApiKeyService.java`,
  `keys/ApiKeyResponse.java`; tests en
  `backend/src/test/java/com/projectlog/keys/` — `ApiKeyControllerMvcTest`
  (réplica `PanelAuthMvcTest`: `@WebMvcTest` + `@Import(SecurityConfig,
  JwtAuthFilter, RestAuthenticationEntryPoint, CorsConfig)` +
  `@MockitoBean` repo/service; E4.1, E4.2, E4.4–E4.10), `ApiKeyServiceTest`
  (E4.3, E4.11, E4.12), `ApiKeyLogHygieneTest` (E4.14, réplica
  `LogHygieneTest`).
- **Editado (aditivo)**: `ProjectsRepository.java` (+2 constantes
  package-private + 2 métodos `@Transactional` + Javadoc) y el pin SQL
  `ProjectsKeySqlTest` EN `.../projects/repository/` (mismo paquete,
  patrón `ProjectsUpdateSqlTest`; E4.13, incluye re-verificar que
  `conjuntoSet` no menciona columnas de key).
- **Smoke**: `scripts/qa_c4_smoke.sh` con el guion EXACTO E4.15–E4.29 del
  spec §2.6 (higiene: key → tmpdir 700, jamás consola).
- Desglose fino de tests/pasos y su orden: tarea de sdd-tasks sobre la §2
  del spec — este design no los re-escribe.