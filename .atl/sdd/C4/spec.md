---
change: "C4"
artifact: spec
last_updated: 2026-10-24
status: draft
---

# Spec — C4 · API keys de ingesta

> Especificación normativa del corte C4 para la implementación (sdd-design →
> tasks → apply → qa → verify). Fuente: `proposal.md` C4 (status **approved**
> — gate aprobado por el usuario 2026-10-24, con la **Decisión 3 REVISADA**:
> en ARCHIVADO solo DELETE permitido, regenerate rechazado con 409
> `project_archived`) y anclas de `explore.md` C4. Los literales de API/BD/
> errores se **citan** del Backend Schema (`docs/05_BACKEND_SCHEMA.md`, valido
> v1.0), del AppFlow (`docs/03_APPFLOW.md`, valido v1.0), del TRD
> (`docs/02_TRD.md`, valido v1.1: §2.6 + ADR-4) y del `V1__init.sql` aplicado
> en C1 — nunca se redefinen (regla del 07). Lo heredado de C1–C3 es
> intocable donde se indica.

## 0. Decisiones de gate ya aprobadas (resumen vinculante)

| Gate | Decisión aprobada (2026-10-24) |
|---|---|
| D1 Done reformulado | El Done del 07 se interpreta como **smoke por transiciones de estado** (regenerar → 201 + psql hash; regenerar de nuevo → hash anterior ≠ nuevo = invalidación al instante; revocar → 204 + tres NULLs + ficha `has_key: false`) **+ test unitario de la primitiva** `tiempoConstanteIgual`. La verificación REAL "la key ya no autentica al ingestar" (401 `api_key_invalid` / 403 `project_archived` con `X-API-Key`) es Done del smoke de C5 |
| D2 DELETE sin key activa | **204 idempotente** (sin ancla de 400/409 en la fila Schema §3.2; el estado "sin key" ya es el objetivo) |
| D3 **REVISADA** | **ASIMÉTRICO en ARCHIVADO**: `DELETE …/api-key` → PERMITIDO (sin rama extra); `POST …/api-key/regenerate` → RECHAZADO con **409** cuerpo estándar C3 y `code: "project_archived"` (literal ya contrato del Schema §3.4). Alternativa rechazada en gate: 403 `project_archived` para el par (el 403 con ese literal es la condición de INGESTA en C5 — mezclaría semánticas) |
| D4 Front | **NO tocar front en C4** (Done no lo exige; maqueta = referencia UI hasta C8). La copia servible de C3 queda con su nota "C4: sin regenerate/revocar" — sigue siendo verdadera al cerrar C4 |
| D5 Primitivas y lookup | Primitivas crypto en C4 **SÍ** (unitarias, lo exige el 07 §C4), `findByApiKeyHash` **NO** (solo se documenta su firma para C5, §7 de este spec) |

Y, de la propuesta (no gate, ratificado de `explore.md` §4): paquete
`com.projectlog.keys` (ancla del package-info de C1), controller fino sin
`@Valid` (starter-validation NO está en el pom — verificado contra
`backend/pom.xml`), `SecureRandom` 32 B + Base64url unpadded, SHA-256 rápido +
`MessageDigest.isEqual` (ADR-4: NO Argon2id para la key), estamentos NUEVOS
aditivos en `ProjectsRepository` — su Javadoc de C3 ya lo declara "pieza
compartida downstream".

## 0.2 Decisiones de SPEC (cierre de los 7 huecos del §7 + Decisión 3)

Cierres que el gate remitió a este spec. Son VINCULANTES para design/tasks.

| # | Hueco | Cierre del spec | Justificación |
|---|---|---|---|
| S1 | `revokeKey`: ¿WHERE con `api_key_hash IS NOT NULL`? | **SIN la condición** (`WHERE id = ?` a secas). El DELETE sobre proyecto sin key SÍ toca la fila (`updated_at` avanza) y devuelve 204 igualmente. La variante con condición queda RECHAZADA: con ella, 0 filas devueltas sería AMBIGUO (id inexistente → 404, vs. id existente sin key → 204) y obligaría a un segundo `findById` para desempatar — dos idas donde una bastan, sin cambiar el código HTTP (el 204 es idempotente en ambos diseños, Decisión 2) | La condición solo cambia `updated_at`, no el contrato HTTP; la ambigüedad del desempate es peor que el avance de `updated_at` (que es literalmente cierto: la fila SÍ se reescribe — `updated_at` es "última escritura sobre la fila") |
| S2 | Rama de ARCHIVADO en regenerate: ¿antes o tras el UPDATE? | **Status check ANTES de generar la key**, en el controller: `findById` (C3) → 404 si vacío → 409 si `status == "ARCHIVED"` → solo entonces `newKey()` + `sha256` + `regenerateKey`. Una key generada JAMÁS llega a escribirse sobre un proyecto archivado (no se genera y descarta un hash) | El 404 ANTECEDE al 409 (¿cómo decidir 409 solo sabiendo el estado?) y el 409 antecede a la generación: generar y tirar el secret consumiría entropía y pondría el secret (efímero) en memoria para nada; además el coste del error falla antes de tocar crypto |
| S3 | Mensaje del 409 panel | `MSG_PROJECT_ARCHIVED = "El proyecto está archivado: desarchívalo antes de generar o regenerar su API key"` — SIN `field` | Los docs fijan el **code** `project_archived` (contrato, Schema §3.4 + §2.6 del TRD) pero NINGÚN literal de `message` para él ("único cuerpo `{"code": "project_archived"}`"); el patrón C3 exige mensajes descriptivos en español en el panel. La coherencia con C5: C5 reutilizará el MISMO `code`; su `message` de ingesta puede diferir (otra superficie) — se deja anotado en §7 |
| S4 | Formato de `created_at` de la 201 | `key_created_at` recién escrito, devuelto por el `RETURNING` y formateado **en el repo** con `LoginController.EXPIRES_AT_FORMATTER` (`yyyy-MM-dd'T'HH:mm:ss'Z'` UTC, pública de C2 ya reutilizada por `ProjectsRepository`) — el repo devuelve `Optional<String>` ya formateado | Misma responsabilidad que el `FILA` de C3 (el repo formatea las fechas; el DTO de salida viaja como `String` ISO-Z, patrón `ProjectDto`) |
| S5 | Visibilidad de constantes/helper de C3 | **Verificado contra `ProjectsController.java`**: `CODE_NOT_FOUND` y `MSG_NOT_FOUND` son `static final` **package-private** y `error()` es **private** — `ApiKeyController` en `com.projectlog.keys` NO puede reutilizar NINGUNO. Se DUPLICAN en `keys` (2 constantes 404 + helper `error()` mini) con Javadoc de ancla, patrón que C3 ya usó al replicar cuerpos de C2 (cero diffs sobre ficheros de cortes anteriores). Nota: `EXPIRES_AT_FORMATTER` SÍ es `public` → reutilizada por referencia, no duplicada | Duplicar < editar la visibilidad de C3 (mínimo diff posible sobre corte cerrado) |
| S6 | Colisión de hash (índice parcial único) | **NO se captura `DuplicateKeyException`** (ni ningún `DataIntegrityViolationException`): sube a 500. Documentado como riesgo teórico R-4 (astronómico) | Sin literal de error para ello en los docs; coherente con C3, que tampoco mapea el DIVE genérico (el handler único de C3 es class-scoped y no aplicaría al controller de `keys` de todos modos) |
| S7 | CORS | **Cero cambios en `CorsConfig`** — verificado: sus `allowedHeaders` son `Authorization, Content-Type, X-Confirm-*` y NO incluye `X-API-Key`, ni lo necesita: (a) el panel nunca envía `X-API-Key` (JWT por `Authorization`; Schema §5: una API key jamás autentica el panel); (b) la cadena 1 de ingesta NO tiene `cors()` (SecurityConfig, TRD §5: clientes servicios, no navegador) | El fichero queda en la lista de INTOCABLES |

### Cita del contrato del hash (hueco §7.2 — REAFIRMACIÓN)

**El hash es SHA-256 del String COMPLETO de la key — `plk_` + 43 chars
Base64url (47 chars), bytes UTF-8 del String íntegro (`key.getBytes
(StandardCharsets.UTF_8)`), digest binario de 32 bytes (BYTEA).** Anclas
textuales:

- Schema §2, fila `api_key_hash`: "**Hash SHA-256** de la key; `NULL` = sin
  key activa (revocada); índice único parcial (solo filas no nulas) para
  localizar el proyecto en O(1) desde la ingesta".
- Schema §5 ("API keys", ancla TRD ADR-4): "se guarda **solo el hash
  SHA-256** (`api_key_hash`) … permite localizar el proyecto por índice en
  O(1) (`projects_api_key_hash_idx`, §2) y comparar en tiempo constante
  (`MessageDigest.isEqual`)".
- TRD §2.6: "Se guarda **solo el hash SHA-256** … comparación final en tiempo
  constante (`MessageDigest.isEqual`)".
- TRD ADR-4: "API key: `SecureRandom` 256 bits, guardada por hash SHA-256 +
  comparación en tiempo constante" (alternativa descartada: "Argon2id para la
  key" — la key no es contraseña humana; el KDF lento destruiría el lookup
  O(1)).

Corrección de cita respecto a la proposal: el ancla en el doc validado es
**Schema §5** (bloque "API keys") y §2 (coluna), no "§4.2" (la §4.2 del doc es
"Soft-delete vs hard-delete"); el contenido citado es idéntico.

**Contrato para C5 (se reafirma aquí como frontera)**: C5 calculará
`sha256(valor_completo_del_header_X_API_Key)` y comparará BINARIO contra
`api_key_hash` — ni trimming de `plk_`, ni hex, ni salt. El presente corte
deja la primitiva `sha256(String)` con esa semántica exacta.

## 1. Requisitos normativos (RFC 2119)

En este documento **MUST/DEBE**, **MUST NOT/NO DEBE**, **SHOULD** y **MAY**
se interpretan según RFC 2119. Cada requisito es verificable por al menos un
escenario de la sección 2.

### 1.A Estructura y dependencias

- **R4.1** — TODO el código nuevo del corte DEBE vivir en el paquete
  `com.projectlog.keys` (package-by-feature, TRD §2.1; el `package-info.java`
  de C1 ya declara: "Feature de API keys (C4): regeneración/revocación única,
  formato plk_*, hash SHA-256" — NO DEBE perder su rol marcador). Piezas:
  `keys/ApiKeyController.java`, `keys/ApiKeyService.java` y
  `keys/ApiKeyResponse.java` (record, patrón auth de C2 SIN subpaquete dto).
  Única dependencia NEW-feature→C3 permitida: `com.projectlog.keys` →
  `ProjectsRepository` y → `LoginController.EXPIRES_AT_FORMATTER` (pública).
  **Cero cambios en `pom.xml`**: NO se añade `spring-boot-starter-validation`
  (verificado: no está, R4.6 no necesita `@Valid` — sin cuerpo de request) ni
  ninguna dependencia extra. **Cero migraciones**: `V1__init.sql` INTOCABLE
  (columnas `api_key_hash BYTEA` / `api_key_prefix TEXT` /
  `key_created_at TIMESTAMPTZ` y el índice parcial único
  `projects_api_key_hash_idx … WHERE api_key_hash IS NOT NULL` ya existen de
  C1).

- **R4.2** — Ficheros de cortes anteriores TOCADOS en C4 (único):
  `backend/src/main/java/com/projectlog/projects/repository/ProjectsRepository.java`,
  en cambio **puramente aditiva** (2 métodos + 2 constantes de SQL + Javadoc;
  jamás se reescribe código de C3). `ProjectsRepository` DEBE seguir siendo
  el dueño único del SQL de `projects`; el UPDATE existente de C3 (via
  `SQL_UPDATE_PREFIX` + `conjuntoSet` + `SQL_UPDATE_SUFFIX`)
  **NO DEBE tocar jamás columnas de key** (`api_key_hash`, `api_key_prefix`,
  `key_created_at` siguen AUSENTES en `conjuntoSet` — frontera R-4 de C3
  preservada) y NO DEBE cambiar una línea.

### 1.B Repositorio — los 2 estamentos NUEVOS (literales EXACTOS)

- **R4.3** — `ProjectsRepository` DEBE incorporar EXACTAMENTE estos dos
  estamentos (constantes `private static final String`, SQL inline del mismo
  estilo que `INSERT_CON_RETORNO`):

```sql
-- (1) generar/regenerar (mono-sentencia; devuelve key_created_at para la 201)
UPDATE projects
   SET api_key_hash = ?, api_key_prefix = ?, key_created_at = now(),
       updated_at = now()
 WHERE id = ?
RETURNING key_created_at
```

```sql
-- (2) revocar (los tres a NULL JUNTOS — invariante Schema §2; fila 0 → 404)
UPDATE projects
   SET api_key_hash = NULL, api_key_prefix = NULL, key_created_at = NULL,
       updated_at = now()
 WHERE id = ?
RETURNING id
```

  Nombres de constantes: `UPDATE_REGENERAR_KEY` y `UPDATE_REVOCAR_KEY`.

- **R4.4** — Firmas DEBE ser:
  - `public Optional<String> regenerateKey(long id, byte[] hash, String p)`
    — ejecuta (1); con `List` (NUNCA `queryForObject`: la fila-0 es un caso
    legal de 404, no una excepción); 0 filas → `Optional.empty()`; 1 fila →
    `Optional.of(key_created_at formateado con
    `LoginController.EXPIRES_AT_FORMATTER)`.
  - `public boolean revokeKey(long id)` — ejecuta (2); 0 filas → `false`
    (→ 404 del controller); 1 fila → `true` (→ 204).
  Ambos DEBEN llevar `@Transactional` (defensa documentada, mismo patrón que
  los 4 métodos de C3: mono-sentencia, el autocommit de JdbcTemplate basta).
  Ambos DEBEN producir su 404 por empty-RETURNING — **sin pre-SELECT** (mejor
  que el patrón de C3: una ida menos, y seguro ante carreras: si el id no
  existe el UPDATE es no-op y no escribe). El `updated_at = now()` es del
  SET en ambos (V1 sin trigger: el DEFAULT solo cubre INSERT).
  **El WHERE de `revokeKey` DEBE ser `id = ?` a secas** (cierre S1: sin la
  condición `api_key_hash IS NOT NULL`). En (1), el `WHERE` lleva el hash de
  la key COMPLETA (cierre de §0.2, cita del contrato) — es una escritura
  ciega del digest, no una búsqueda.

- **R4.5** — `findByApiKeyHash` **NO DEBE existir en C4** (firmada y
  documentada en §7 para C5; Decisión 5 del gate).

### 1.C Endpoints, respuestas y literales (contrato)

- **R4.6** — `ApiKeyController` DEBE exponer EXACTAMENTE 2 mappings, con
  rutas COMPLETAS (sin colisión con los de C3 — Spring resuelve por path
  exacto), SIN cuerpo de request (Schema §3.2: entrada "—"; no hay
  `@RequestBody`, no puede haber `HttpMessageNotReadable`, no hay 400) y SIN
  `@Valid`:

```java
@PostMapping("/api/v1/panel/projects/{id}/api-key/regenerate")
@DeleteMapping("/api/v1/panel/projects/{id}/api-key")
```

  Ambos nacen bajo la **cadena 2** de `SecurityConfig`
  (`/api/v1/panel/**` → `anyRequest().authenticated()`): 401 sin JWT
  AUTOMÁTICO, cero líneas de seguridad tocadas. `SecurityConfig`
  INTOCABLE.

- **R4.7** — Literales de respuesta EXACTOS (R-8):

| Caso | HTTP | Cuerpo |
|---|---|---|
| Regenerar ok | **201** | `{"key": "plk_<43 chars>", "prefix": "plk_<10 chars>", "created_at": "yyyy-MM-dd'T'HH:mm:ss'Z'"}` — claves EXACTAS `key`, `prefix`, `created_at`, en ese orden (record `ApiKeyResponse(String key, String prefix, String created_at)` — Jackson serializa los accessors snake_case sin `@JsonProperty`, patrón `ProjectDto`/`LoginResponse` de C2) |
| Revocar ok | **204** | **SIN cuerpo** (`ResponseEntity.noContent().build()`) |
| Proyecto inexistente (ambos) | **404** | Cuerpo estándar C3: `{"code": "not_found", "message": "No existe (quizás fue eliminado)"}` — SIN `field` |
| Regenerar sobre ARCHIVADO | **409** | `{"code": "project_archived", "message": "El proyecto está archivado: desarchívalo antes de generar o regenerar su API key"}` — SIN `field` (cuerpo estándar §3.1 `{code, message, field?}`; cierres S3) |
| Sin JWT (ambos) | **401** | El del entry point de C2 (`invalid_token`) — exclusivo del entry point, NUNCA generado por código de C4 |

- **R4.8** — Orden de ramas de `POST …/regenerate` (cierre S2) — DETERMINISTA:

```text
(1) 401 sin JWT .................. cadena 2 (automático, previo al controller)
(2) 404 no existe ................ findById(id) de C3 vacío → cuerpo estándar
(3) 409 ARCHIVADO ................ fila.status().equals("ARCHIVED") → 409
(4) generar ...................... newKey() → sha256(key) → prefixDe(key)
(5) escribir ..................... regenerateKey(id, hash, prefix)
(6) 404 (carrera, nunca hoy) ..... Optional.empty() del RETURNING → 404
(7) 201 .......................... ApiKeyResponse(key, prefix, created_at)
```

  El 404 ANTECEDE al 409 (no se puede decidir "archivado" de una fila que no
  existe) y el 409 ANTECEDE a la generación (una key jamás se genera y
  descarta sobre un proyecto archivado). El paso (6) es la segunda barrera
  del empty-RETURNING, gratis por diseño.

- **R4.9** — Orden de ramas de `DELETE …/api-key` — SIN chequeo de status
  (Decisión 3 revisada: permiso en ARCHIVADO, sin rama extra):

```text
(1) 401 sin JWT .................. cadena 2 (automático)
(2) escribir ..................... revokeKey(id) → true | false
(3) false (→ 404) ................ cuerpo estándar C3
(4) true (→ 204) ................. SIN cuerpo (idempotente: también cuando
                                   el proyecto ya estaba sin key — Decisión 2)
```

- **R4.10** — Constantes y helper de `ApiKeyController` (cierre S5 —
  verificado contra `ProjectsController.java`):

```java
/** Duplicados de C3 con ancla: en ProjectsController son package-private
 *  (CODE_NOT_FOUND/MSG_NOT_FOUND) y error() es private — invisibles para el
 *  paquete keys. Literales citados, no redefinidos. */
static final String CODE_NOT_FOUND = "not_found";
static final String MSG_NOT_FOUND  = "No existe (quizás fue eliminado)";
/** Anclado: code=contrato del Schema §3.4 (403 en INGESTA, C5); en el panel
 *  C4 lo porta un 409 — Decisión 3 REVISADA del gate 2026-10-24. El message
 *  es literal de este spec (cierre S3). */
static final String CODE_PROJECT_ARCHIVED = "project_archived";
static final String MSG_PROJECT_ARCHIVED =
        "El proyecto está archivado: desarchívalo antes de generar o"
                + " regenerar su API key";
```

  Y un helper `error(...)` **private** réplica del de C3 (cuerpo estándar
  §3.1 `{code, message, field?}` con `ObjectMapper.valueToTree` +
  `MediaType.APPLICATION_JSON`; en C4 solo se usa sin `field`). NINGÚN
  `@ExceptionHandler` en C4: no hay cuerpo de request (no aplica
  `HttpMessageNotReadable`) y la `DuplicateKeyException` NO se captura
  (cierre S6, riesgo R-4 teórico → 500).

### 1.D Servicio crypto — frontera de la feature

- **R4.11** — `ApiKeyService` (`@Service`) DEBE exponer:
  - `String newKey()` → `plk_` + `Base64.getUrlEncoder().withoutPadding()
    .encodeToString(32 bytes de SecureRandom)` = 47 chars;
    instancia `SecureRandom` **`private static final`** del servicio
    (thread-safe; una generación por llamada).
  - `byte[] sha256(String key)` → `MessageDigest.getInstance("SHA-256")
    .digest(key.getBytes(StandardCharsets.UTF_8))` — del String COMPLETO
    (contrato de §0.2, reafirmado para C5). NUEVO `MessageDigest` POR
    llamada (no es thread-safe como estado compartido).
  - `String prefixDe(String key)` → `key.substring(0, 10)` (Schema §2:
    "primeros 10 caracteres de la key").
  - `boolean tiempoConstanteIgual(byte[] a, byte[] b)` → wrapper de
    `MessageDigest.isEqual(a, b)` — primitiva que C5 usará en el hot-path.
  **NO Argon2id/BCrypt** (ADR-4, rechazado: hash rápido = lookup O(1)).

- **R4.12** — Semántica de estado (Schema §4, reglas 11 y 12 — CITADAS):
  - **Regenerar** sobrescribe `api_key_hash`/`api_key_prefix`/
    `key_created_at` sin tocar logs — **la key anterior DEJA DE AUTENTICAR
    EN EL INSTANTE** (sobrescritura del hash en la MISMA fila; el
    `updated_at` avanza con la escritura).
  - **Revocar** pone los tres a NULL conjuntamente (invariante Schema §2:
    los tres van SIEMPRE juntos) — futuro 401 `api_key_invalid` en ingesta
    (C5, Schema §3.4).
  - **Archivar NO toca la key** (regla 11; ya garantizado: la rama del
    `status` en `conjuntoSet` jamás menciona columnas de key) — y al
    desarchivar, la MISMA key vuelve a aceptarse.
  - **Derivados de C3 SIN cambios**: tras revocar, `GET /projects/{id}` y el
    listado devuelven automáticamente `has_key: false` /
    `api_key_prefix: null` (derivados SQL ya en `COLS` de C3) — cero diff en
    C3; verificación en smoke (E4.9), no en código.

### 1.E Higiene de logs (R-2b / S16 replicado)

- **R4.13** — La key COMPLETA (`plk_*` de 47) DEBE existir UNA única vez: en
  el `ApiKeyResponse` de la 201 (respuesta en curso). NINGUNA clase de
  `com.projectlog.keys` DEBE loggear `plk_`, la key, ni un fragmento suyo —
  ni a INFO ni a DEBUG. La higiene DEBE probarse con el patrón
  `LogHygieneTest` de C2 (`ListAppender` de logback): escenario E4.14.

### 1.F CORS (cierre S7)

- **R4.14** — `CorsConfig` INTOCABLE. Verificado (config/CorsConfig.java):
  `allowedHeaders = [Authorization, Content-Type, X-Confirm-*]` sin
  `X-API-Key`, y no falta: el panel manda JWT por `Authorization` y JAMÁS
  `X-API-Key` (Schema §5), y la cadena 1 de ingesta no tiene `cors()`
  (SecurityConfig; TRD §5). Cero entradas CORS en C4.

### 1.G Tests y smoke

- **R4.15** — Suite [L] (sin contenedor, patrón C2/C3: `@WebMvcTest` +
  `@MockitoBean` + `@ActiveProfiles("test")` con `application-test.yml`
  INTOCABLE) + smoke [S] en pila real (`scripts/qa_c4_smoke.sh`), con el
  desglose numerado y nominado de la §2.5 y el guion de la §2.6. Patrones de
  entorno HEREDADOS de C3 (R-7, archive-report): recomposición con
  `--force-recreate` OBLIGATORIA (`up -d --build` sin recrear
  contenedor = smokes rojos contra imagen vieja), `mvn test` DENTRO de la
  imagen builder `maven:3.9-eclipse-temurin-21` (backend + `~/.m2` montados,
  sin BD), psql `-qAt` con epoch `FLOOR(EXTRACT(EPOCH …))::bigint` donde
  toque y booleanos `t/f` (psql) vs `true/false` (JSON) según el contexto,
  **smoke-of-the-smoke** (corrida de validación del script ANTES de la
  canónica), nombres con sufijo por ejecución (`smoke-c4-<TS>-*`).

### 1.H No-regresiones C3 — bloqueos heredados

- **R4.16** — C4 NO DEBE romper NADA de C3: (a) el UPDATE existente jamás
  toca columnas de key (frontera R-4/E3.19); (b) `GET /projects` y
  `GET /projects/{id}` responden igual (los derivados ya existen en `COLS`);
  (c) `SecurityConfig`, `V1__init.sql`, entry point, `CorsConfig`,
  `application-test.yml`, `docs/**` y `frontend/public/mockups` INTOCABLES
  (validadores de C1 corren igual en cada contexto de test); (d) sin nuevos
  `code` de error fuera de los de R4.10 (reutilizados `not_found` y
  `project_archived`).

## 2. Escenarios de aceptación

Cada escenario es verificable en al menos un test [L] (§2.5) o paso del
smoke [S] (§2.6). Los IDs E4.x son estables para qa/verify.

### 2.1 Regenerar (POST) — generar y regenerar son el MISMO endpoint

- **E4.1** `regenerateSobreProyectoActivoResponde201ConClavesExactas` [L] —
  con JWT y proyecto ACTIVE (mock repo): 201 con claves JSON EXACTAS
  `key, prefix, created_at` (en ese orden) y valores del mock (patrón C2/C3).
- **E4.2** `regenerateKeyNaceCasandoLiteralDeFormatoPlk` [L] — la `key` del
  mock casa `^plk_[A-Za-z0-9_-]{43}$` (47 chars totales) y
  `prefix == key.substring(0, 10)` (10 chars, `plk_` + 6).
- **E4.3** `newKeyGenera47CharsConEntropiaEnMuestra` [L] — 100 llamadas a
  `newKey()`: TODAS casan la regex de E4.2 y TODAS son distintas (entropía
  256 bits: cero colisiones esperables en muestra, Done 1).
- **E4.4** `created_atConFormatoFormatterDeC2` [L] — el `created_at` de la
  201 casa `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$` (formato C2
  `EXPIRES_AT_FORMATTER`, cierre S4 — el valor concreto del formatter en
  producción se comprueba en psql, E4.6).
- **E4.5** `regenerateProjectoInexistenteResponde404CuerpoEstandar` [L] —
  404 `{code: "not_found", message: "No existe (quizás fue eliminado)"}` sin
  `field`; `verifyNoInteractions` de la vía de escritura (regenerateKey
  NUNCA invocado por un id inexistente — orden R4.8).
- **E4.6** `regenerateSobreArchivadoResponde409ProjectArchived` [L] —
  `findById` devuelve ARCHIVED → 409
  `{code: "project_archived", message: <literal S3>}` sin `field`; y
  `tiempoConstanteIgual`/generación jamás ocurren (verifyNoInteractions del
  service de escritura: el 409 ANTECEDE a la generación — cierre S2).
- **E4.7** `deleteSobreArchivadoPermitidoResponde204` [L] — `revokeKey`
  sobre ARCHIVED → `true` del mock → 204 SIN cuerpo (Decisión 3 revisada:
  DELETE permitido en archivado, sin rama de status).
- **E4.8** `deleteProyectoInexistenteResponde404` [L] — `revokeKey` →
  `false` (empty-RETURNING del mock) → 404 cuerpo estándar.
- **E4.9** `sinJwtAmbasRutasResponde401InvalidToken` [L] — cadena 2 en las 2
  rutas: 401 `invalid_token` del entry point (literal de C2,
  `PanelAuthMvcTest` réplica); `verifyNoInteractions` del repo.
- **E4.10** `deleteExitosoResponde204SinCuerpo` [L] — `revokeKey` → `true` →
  204 y el cuerpo HTTP VACÍO (Done 2, idempotencia de Decisión 2 en la vía
  del mock — el "proyecto sin key previa" también es `true`).

### 2.2 Primitivas crypto (unitarias, sin BD — Done 5)

- **E4.11** `sha256EsDelStringCompletoYParejaCorrectaDaTrue` [L] —
  `tiempoConstanteIgual(sha256(K), sha256(K)) == true` (pareja correcta) con
  una key de prueba K; y vector fijo: `sha256("abc")` ==
  bytes hex `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`
  (ancla el UTF-8/digest de 32 B de la primitiva).
- **E4.12** `tiempoConstanteIgualConHashAnteriorDaFalse` [L] —
  `tiempoConstanteIgual(sha256(K_anterior), sha256(K_nueva)) == false`
  (exactamente lo que C5 hará en el hot-path contra el hash ya
  sobrescrito — Done 5).

### 2.3 SQL del repo (pin de literales, sin BD — patrón `ProjectsUpdateSqlTest`)

- **E4.13** `estamentosDeKeyPinenLiteralesExactos` [L] —
  `UPDATE_REGENERAR_KEY` contiene literalmente
  `SET api_key_hash = ?, api_key_prefix = ?, key_created_at = now(),
  updated_at = now()` + `WHERE id = ?` + `RETURNING key_created_at`;
  `UPDATE_REVOCAR_KEY` contiene literalmente
  `SET api_key_hash = NULL, api_key_prefix = NULL, key_created_at = NULL,
  updated_at = now()` + `WHERE id = ? RETURNING id` (cierre S1: SIN
  `api_key_hash IS NOT NULL` en el WHERE) + NINGUNA otra columna; y
  `conjuntoSet` SIGUE sin mencionar columnas de key (frontera R4.16a).

### 2.4 Higiene de logs

- **E4.14** `ningunLogDeKeysContieneLaKey` [L] — patrón `LogHygieneTest` de
  C2: `ListAppender` sobre los loggers de las clases de `com.projectlog.keys`
  mientras se ejecuta regenerate (201) y delete (204) con mock; NINGÚN
  evento contiene `plk_` ni la key del test ni fragmentos suyos (Done 6).

### 2.5 Smoke [S] — `scripts/qa_c4_smoke.sh` (guion exacto)

> Patrones de entorno heredados de C3 (R4.15/R-7). Higiene en runtime: la
> key revelada va al tmpdir 700 y JAMÁS se imprime a consola (la única
> "revelación" es interna al script; en psql se usa vía variable del
> tmpdir).

- **E4.15** precondición (smoke-of-the-smoke previo a la corrida canónica) —
  recomposición ÚNICA inicial `podman compose up -d --build
  --force-recreate` (R-nuevo-5) + pila healthy por podman (backend, frontend,
  postgres) + sondeo auténtico `GET /me` → 401 `invalid_token`.
- **E4.16** login `POST /auth/login` → 200 `{token, username, expires_at}`;
  JWT al tmpdir (nunca en consola).
- **E4.17** `POST /projects` → 201 con nombre sufijado
  `smoke-c4-<TS>-a`; anotar `id` (captura incondicional, lección C3);
  ficha `has_key: false`.
- **E4.18** `POST /projects/{id}/api-key/regenerate` (1.ª vez, "generar")
  → **201**; `key` casa `^plk_[A-Za-z0-9_-]{43}$`; `prefix ==
  key.substring(0, 10)`; `created_at` casa la regex ISO-Z de E4.4.
- **E4.19** psql (Done 4): `api_key_hash` (BYTEA) ==
  `sha256('<key>'::bytea)` → `t` (PG17 tiene `sha256(bytea)` — así se
  pincha el hash del String COMPLETO en pila); `api_key_prefix` ==
  `substring` de 10; y NINGÚN campo de la fila contiene la key en claro
  (`name/description/api_key_prefix` sin aparición de la key).
- **E4.20** 2.º `POST regenerate` → 201 con key DISTINTA a la primera
  (validación al instante, Done 3); psql: `api_key_hash ==
  sha256(key_nueva)` → `t` Y `api_key_hash <> sha256(key_anterior)` → `t`.
- **E4.21** `DELETE /projects/{id}/api-key` → **204 SIN cuerpo**.
- **E4.22** psql (Done 2): los TRES a NULL juntos
  (`api_key_hash IS NULL AND api_key_prefix IS NULL AND key_created_at IS
  NULL` → `t`).
- **E4.23** `GET /projects/{id}` (C3 SIN cambios): `has_key: false`,
  `api_key_prefix: null` (Done 2/derivados).
- **E4.24** idempotencia en pila (Decisión 2): `DELETE` de nuevo (proyecto
  sin key) → **204** otra vez; psql: los tres SIGUEN a NULL. (Nota
  documentada: `updated_at` avanza — cierre S1 — no se aserta.)
- **E4.25** archivar vía `PATCH {"status": "ARCHIVED"}` (200, C3) →
  `POST regenerate` → **409** `{code: "project_archived", …}`; psql: la
  hash NO cambió (igual a la 2.ª key de E4.20... que fue revocada — check:
  los tres SIGUEN a NULL, es decir, el 409 NO escribió nada).
- **E4.26** proyecto ARCHIVADO + `DELETE /api-key` → **204** (Decisión 3
  revisada: permitido; psql: tres NULL, `updated_at` avanzó).
- **E4.27** `POST regenerate` sobre id INEXISTENTE (999999…) → 404
  `not_found` cuerpo estándar (curl con JWT).
- **E4.28** 401 sin JWT en AMBAS rutas: 401 `invalid_token` (parse
  tolerante del body ISO-8859-1 del entry point — patrón heredado).
- **E4.29** no-regresión C3 ligera: `GET /projects` sigue 200 (membresía:
  el proyecto del smoke sigue en la lista; DELETE de proyectos es C10) y la
  ficha de E4.23 ya cubre los derivados.

Salida: una línea `E4.n: PASS | FAIL | BLOQUEADO (detalle)` por ID;
exit 0/1/2 como C3.

## 3. Contratos citados (fuente única de verdad)

| Literal | Fuente (congelada) |
|---|---|
| Rutas, auth, 201 `{key, prefix, created_at}` ("key completa, una única vez"), 204, 401·404 | Schema §3.2 (filas POST `…/api-key/regenerate` y DELETE `…/api-key`) |
| Formato key `plk_<43 chars Base64url>`, 32 B `SecureRandom` ≈256 bits | TRD §2.6; Schema §5; ADR-4 |
| Guardado SOLO por hash SHA-256 (BYTEA, `NULL` = revocada), lookup O(1), `MessageDigest.isEqual` | Schema §2 (fila `api_key_hash`) + §5; TRD §2.6 + ADR-4; `V1__init.sql` (líneas 18–20, 54–55, intocable) |
| `api_key_prefix` = primeros 10 chars, `NULL` junto al hash al revocar | Schema §2 (fila `api_key_prefix`) |
| Invariante tres-a-la-vez; `has_key` derivado | Schema §2 ("Invariantes") |
| Regenerar invalida al instante; revocar → 401 en ingesta; archivar NO toca la key; misma key al desarchivar | Schema §4 regla 11; AppFlow flujo 3 (pasos 2–7) |
| 401 `api_key_invalid` / 403 `project_archived` (INGESTA, C5) | Schema §3.4 (tabla de errores) |
| Cuerpo estándar de error `{code, message, field?}`; fechas ISO-8601 UTC `Z` | Schema §3.1 |
| Mensaje 404 "No existe (quizás fue eliminado)" | AppFlow §5 (fila 404) + C3 (`MSG_NOT_FOUND`) |
| `key_created_at` = fecha de generación/regeneración, `NULL` si revocada | Schema §2 (fila `key_created_at`) |
| Formatter `EXPIRES_AT_FORMATTER` (`yyyy-MM-dd'T'HH:mm:ss'Z'` UTC) | `auth/LoginController.java` (pública, reutilizada por referencia) |
| Ingesta solo sobre proyectos ACTIVE (403 en C5) | Schema §4 regla 12 |

## 4. Fuera de alcance (explícito)

- **NO ingesta**: ni `POST /api/v1/ingest` ni filtro/auth `X-API-Key` (C5);
  la cadena 1 sigue `denyAll()` respondiendo **401 `invalid_token` a TODO
  `/api/v1/ingest/**`** — es la firma TEMPORAL del límite C4/C5, se menciona,
  NO se cambia (ver §7).
- **NO lookup `findByApiKeyHash`** (C5; firma documentada en §7, Decisión 5).
- **NO front**: ni wiring de la maqueta 05 ni modales (C8; Decisión 4).
- **NO migraciones** (V1 intocable), **NO docs** (`docs/**` congelado),
  **NO `SecurityConfig`/`CorsConfig`/entry point/`application-test.yml`**.
- **NO Argon2id/BCrypt** para la key (ADR-4), **NO starter-validation**
  (pom congelado), **NO extras de spring-security**.
- **NO captura de `DuplicateKeyException`** (R-4 teórico; cierre S6).
- **NO** archivado (hecho en C3), `DELETE /projects/{id}` (C10/P1), summary
  (C10/P1), streaming (C7), rate limiting (ADR-26).

## 5. Trazabilidad

| Requisito | Escenarios | Verificado por |
|---|---|---|
| R4.1 (paquete, pom, sin migración) | E4.1–E4.14 | review de ficheros + suite [L] |
| R4.2 (repo aditivo, frontera R-4) | E4.13, E4.29 | test SQL + smoke |
| R4.3/R4.4 (estamentos + firmas) | E4.13, E4.21–E4.22, E4.26 | test SQL + psql |
| R4.5 (lookup NO) | — | review (ausencia) |
| R4.6/R4.7 (rutas y literales) | E4.1, E4.10, E4.18, E4.21 | MVC + smoke |
| R4.8/R4.9 (órdenes de rama) | E4.5, E4.6, E4.7, E4.8 | MVC (verifyNoInteractions) |
| R4.10 (constantes/helper) | E4.5, E4.6 | MVC |
| R4.11 (servicio crypto) | E4.3, E4.11, E4.12 | unitarios |
| R4.12 (semántica de estado) | E4.20, E4.22, E4.23, E4.29 | psql + smoke |
| R4.13 (higiene) | E4.14 | ListAppender |
| R4.14 (CORS) | — | review (fichero intocado) |
| R4.15 (tests/smoke + entorno) | E4.15–E4.29 | smoke |
| R4.16 (no-regresiones) | E4.29 + suite [L] de C3 re-corrida en imagen builder | smoke + mvn |

Criterios Done = los 9 de la proposal §5 (con Decisión 1 reformulada)
**+ el 409 `project_archived` de la Decisión 3 revisada** → cubiertos:
Done 1 (E4.1–E4.4), 2 (E4.10, E4.21–E4.23), 3 (E4.20), 4 (E4.19), 5
(E4.11–E4.12), 6 (E4.14), 7 (E4.1/E4.8/E4.9/E4.10), 8 (E4.15–E4.27),
9 (§4) y 409 (E4.6 [L] + E4.25 [S]).

## 6. Mapa de verificación (resumen qa)

| Familia | IDs | Tipo |
|---|---|---|
| MVC controller | E4.1, E4.2, E4.4–E4.10 | [L] `@WebMvcTest` |
| Servicio crypto | E4.3, E4.11, E4.12 | [L] puro |
| SQL repo | E4.13 | [L] pin de literales |
| Higiene logs | E4.14 | [L] ListAppender |
| Pila real | E4.15–E4.29 | [S] curl+psql, patrón C3 |

## 7. Notas para C5 (frontera EXACTA abierta — SOLO documentar, no construir)

- **Firma futura del lookup** (C5 añade el ÚNICO método nuevo del repo
  pendiente): `Optional<ProjectDto> findByApiKeyHash(byte[] hash)` sobre
  `SELECT <COLS> FROM projects WHERE api_key_hash = ?` — el índice parcial
  único `projects_api_key_hash_idx` ya lo hace O(1). Su uso: en el filtro/
  auth de `X-API-Key` de la **cadena 1** (SecurityConfig, hoy `denyAll()`),
  `sha256(header)` (String completo, este mismo contrato) → lookup →
  `tiempoConstanteIgual` como comparación final. C4 NO construye NADA de
  esto (Decisión 5).
- **Firma temporal del límite**: en C4, TODO intento de ingestar recibe
  **401 `{"code": "invalid_token", …}`** del entry point (cadena 1
  `denyAll()`), CON o SIN `X-API-Key` — es lo esperado y NO se cambia; el
  humo E4.15 lo usa como sondeo. Los 401 `api_key_invalid` (key
  ausente/inválida/revocada) y 403 `project_archived` (proyecto archivado
  CON key válida) de Schema §3.4 nacen en C5.
- **Coherencia del `project_archived`**: en C4 el code viste HTTP **409 en
  el panel** (Decisión 3 revisada del gate); en C5 vestirá **403 en
  ingesta**. El `message` del panel es el literal S3 de este spec; el de
  ingesta lo fijará el spec de C5 (los docs no fijan `message` para ese
  code, solo el `code`).
- **Los hash de C4 son los que C5 autentica**: nada de re-hash ni
  migración; C5 llega al repo ya probado y solo añade el lookup.