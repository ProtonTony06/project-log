---
change: C5
artifact: explore
last_updated: 2026-10-25
status: draft
---

# Explore — C5 · Ingesta REST

> Fase sdd-explore. Fuentes (solo lectura, congeladas): **Schema**
> `docs/05_BACKEND_SCHEMA.md` §3.4, §4.1, §4 (reglas 5–14), §5 e índices
> (valido v1.0), **TRD** `docs/02_TRD.md` §2.6/§2.7/§2.9, ADR-4, §3.1, §6.3
> (valido v1.1), **AppFlow** `docs/03_APPFLOW.md` flujo 10 + §4/§5 (valido
> v1.0), **PRD** `docs/01_PRD.md` §4 P0-4/§6 (valido v1.2), **07_SLICING_PLAN.md**
> ficha C5 y **06_IMPLEMENTATION_PLAN.md** Fase 1 ("Ingesta"). NOTA DE NOMENCLATURA:
> el enunciado del corte citaba `docs/03_SCHEMA.md`/`docs/04_APPFLOW.md`; los
> ficheros REALES congelados son `docs/05_BACKEND_SCHEMA.md` y
> `docs/03_APPFLOW.md` (número = posición en /docs). Código real inspeccionado
> SIN modificar nada: `backend/src/main/java/com/projectlog/{ingest,keys,
> auth,config,projects,streaming,common}`, `backend/pom.xml`,
> `backend/src/main/resources/application.yml` + `application-test.yml`,
> `compose.yaml`, `.env.example`, `scripts/qa_c4_smoke.sh` (solo lectura),
> `.atl/sdd/C4/{verify,archive}-report.md` y `spec.md` §7 (frontera).
>
> Ancla heredada (C4 spec §7): C5 añade el ÚNICO método nuevo de lookup al
> repo (`findByApiKeyHash`), reemplaza el `denyAll()` TEMPORAL de la cadena 1
> y nace con `sha256(header)` + comparación BINARIA — ni trim de `plk_`, ni
> hex, ni salt; los hashes de C4 son los que C5 autentica (nada de re-hash ni
> migración).

## 1. Resumen del corte

C5 crea la **feature de ingesta**: lo que PRD P0-4/P0-5 (stories 5, 6, 7)
pide a las apps del homelab. Dos endpoints sobre la superficie
`/api/v1/ingest/**` autenticada por `X-API-Key` (TRD ADR-15: un JWT jamás
autentica la ingesta):

- **`POST /api/v1/ingest`** — log individual: `LogIngestRequest` →
  **202 `{"accepted": 1}`** (202 es decisión cerrada del gate de coherencia,
  no 200).
- **`POST /api/v1/ingest/batch`** — `{logs: [LogIngestRequest, …]}` con
  **1…200** registros → **202 `{"accepted": N}`**; batch vacío o >200 → 400
  (`batch_empty` / `batch_too_large`, con `field: "logs"`). All-or-nothing
  (schema §4 regla 6 / ADR-11): validación del lote COMPLETO antes de tocar
  la BBDD y persistencia en UNA única transacción.

**Done cuando (07 ficha C5):** los 5 smokes de error — 401 key inválida /
403 archivado / 400 `environment_required` / 400
`timestamp_without_timezone` / 413 tamaño — + happy path individual + batch
de 200 pasan contra la pila; y batch intermedio inválido persiste NADA.

### Literales de contrato fijados (citan 05/02/03/01; C5 no los redefine)

| Literal | Valor | Fuente |
|---|---|---|
| Éxito individual | 202 `{"accepted": 1}` | Schema §3.4 / 06 Fase 1 |
| Éxito batch | 202 `{"accepted": N}` (N = logs aceptados) | Schema §3.4 |
| 401 | `api_key_invalid` (key ausente/inválida/revocada) | Schema §3.4, TRD §2.6 |
| 403 | `project_archived`, cuerpo único `{"code": "project_archived"}` | Schema §3.4, TRD §2.6, AppFlow flujo 7.4 |
| 413 | `payload_too_large` — NOTA: el enunciado decía "413 `INGEST_MAX_REQUEST_BYTES`": `INGEST_MAX_REQUEST_BYTES` es el nombre del LÍMITE (env, TRD §6.3), el `code` del cuerpo es **`payload_too_large`** | Schema §4.1 §3.2 |
| 400 de campo | `environment_required`, `timestamp_without_timezone`, `level_invalid`, `message_required`, `metadata_not_object`, `field_too_long` — con `field` | Schema §3.4 |
| 400 de lote | `batch_empty` / `batch_too_large`, `field: "logs"`; regla 6: error de registro del lote lleva `index`/`field` del culpable | Schema §3.4, §4 regla 6 |
| Orden fail-fast | **413 (antes de parsear) → 401 → 403 → 400**, todo antes de tocar la BBDD | Schema §3.4 |
| Envíos corporales | level `TRACE/DEBUG/INFO/WARN/ERROR/FATAL` (enum cerrado); environment `development/staging/production` **OBLIGATORIO sin default** (ADR-25); `timestamp` ISO-8601 **con offset**, normalizada a UTC al guardar; `metadata` **solo objeto** JSON | Schema §3.4 §4.1 |
| Tamaños | `message` ≤ **32 KiB**; `exception` ≤ **128 KiB** (NO 32: el enunciado dudaba — el literal real del Schema §3.4/§4.1 y TRD §2.7 es 128 KiB); `metadata` ≤ **64 KiB serializado**; `source` ≤ **200 chars**; `correlation_id` ≤ **200 chars**; batch **1…200** (`INGEST_MAX_BATCH_SIZE`); petición ≤ **1 MiB** (`INGEST_MAX_REQUEST_BYTES`) | Schema §3.4/§4.1, TRD §2.7 |
| Campos de confianza | `project_id` (deducido de la key, el cliente NO lo envía), `received_at` (servidor, UTC); **todos los logs de un batch comparten el MISMO `received_at`**; `id` lo genera la BD | Schema §3.4 §4 reglas 5/9, PRD §6 |
| `correlation_id` | Columna ÚNICA unificada (ADR-28); opcional, la APORTA el cliente (sin generación server-side en ningún doc) | Schema §4 regla 8, TRD ADR-28 |
| Errores sin UI | los scripts de las apps los reciben tal cual (AppFlow flujo 10); sin 429 (ADR-26); sin CORS en ingesta (TRD §5) | AppFlow flujo 10 |

## 2. Mapa del código relevante (estado hoy, HEAD ~0d93a68)

| Pieza | Fichero | Estado |
|---|---|---|
| Package ingesta | `backend/src/main/java/com/projectlog/ingest/` | solo `package-info.java` (esqueleto C1: "REST individual y batch con X-API-Key, límites INGEST_MAX_*, 413/403/400, all-or-nothing") — **C5 lo llena** |
| `C5/controller` | — | **NO existe**: `LogIngestController` (2 mappings literales) |
| `C5/dto` | — | **NO existe**: `LogIngestRequest` (8 campos del Schema §3.4) |
| `C5/servicio` | — | **NO existe**: servicio con validación + transacción + campos de confianza |
| `C5/repo` | — | **NO existe**: repo de INSERT sobre `logs` (C6 creará los de lectura en `query`) |
| Primitivas crypto | `backend/src/main/java/com/projectlog/keys/ApiKeyService.java` | EXISTE: `sha256(String)` (UTF-8 completo, digest binario, MessageDigest nuevo por llamada), `tiempoConstanteIgual(byte[],byte[])` (wrapper `MessageDigest.isEqual`), `newKey()`, `prefixDe(String)`. Bean `@Service` mockeable. Sin estado ni BD |
| Lookup por hash | `backend/src/main/java/com/projectlog/projects/repository/ProjectsRepository.java` | **NO existe** `findByApiKeyHash` (grep = 0 apariciones; frontera documentada C4 spec §7). El repo es `JdbcTemplate` con SQL inline (patrón C2), COLS única fuente, `@Transactional` como defensa documentada |
| Seguridad cadena 1 | `backend/src/main/java/com/projectlog/config/SecurityConfig.java` | EXISTE `ingestChain()` `@Order(1)` con `securityMatcher("/api/v1/ingest/**")` + `.anyRequest().denyAll()` TEMPORAL + entry point (y accessDeniedHandler) = `RestAuthenticationEntryPoint` compartido. **C5 lo evoluciona** (el fichero era intocable en C4 pero la ficha 07 y el C4spec §7 ya asignan esta evolución a C5) |
| Entry point 401 | `backend/src/main/java/com/projectlog/config/RestAuthenticationEntryPoint.java` | EXISTE con literal ÚNICO `invalid_token` (contrato del PANEL; CD-4). El 401 de ingesta es `api_key_invalid` (Schema §3.4) — literal DISTINTO: hay que dar salida sin contaminar el panel |
| Filtro de patrón | `backend/src/main/java/com/projectlog/auth/JwtAuthFilter.java` | EXISTE: `OncePerRequestFilter`, registrado SOLO en su cadena; sin credencial → sigue la cadena vacío y el entry point escribe el 401; sin loggers (higiene S16). Es la PLANTILLA del filtro de `X-API-Key` |
| CORS | `backend/src/main/java/com/projectlog/config/CorsConfig.java` | EXISTE: allowed headers SIN `X-API-Key` (cierre S7 de C4 — deliberado). Cadena 1 SIN `cors()`. C5 no debe abrirlo (TRD §5: clientes de ingesta sin CORS) |
| WS | `backend/src/main/java/com/projectlog/streaming/` | `EchoSocketHandler` (eco) + `WebSocketConfig` con registro en `/ws/logs`. C7 sustituye el handler. C5 NO toca nada de streaming salvo el SEAM de emisión (§3.d) |
| `common` | `backend/src/main/java/com/projectlog/common/` | vacío (solo package-info): **sin** helpers de tamaños, errores ni enums — todo lo reusado vive en `auth` (formatter `EXPIRES_AT_FORMATTER` público de C2) y en constantes de cada corte |
| Propiedades | `backend/src/main/resources/application.yml` | SIN límites de ingesta (comentario C1: "sin límites de ingesta (C5)"). `compose.yaml` ya interpola `INGEST_MAX_REQUEST_BYTES`/`INGEST_MAX_BATCH_SIZE` al backend; `.env.example` ya tiene `INGEST_MAX_REQUEST_BYTES=1MiB` (sufijo "MiB" — ver R-6) |
| BD | `backend/src/main/resources/db/migration/V1__init.sql` | `logs` COMPLETA con `received_at`, `environment`, `correlation_id`, JSONB, CHECKs, índice maestro; índice único parcial `projects_api_key_hash_idx` ya creado. **Sin migraciones nuevas** (R4.1 heredado: repo intocable) |
| pomicio | `backend/pom.xml` | EXISTE: web, jdbc, actuator, websocket, flyway, pg driver, security, bouncycastle, jjwt. **SIN starter-validation** → la validación de C5 es MANUAL como en C3 (`ProjectsController` es el patrón) |
| Tests de patrón | `backend/src/test/java/com/projectlog/keys/ApiKeyControllerMvcTest.java`, `auth/PanelAuthMvcTest.java`, `projects/ProjectsControllerMvcTest.java`, `projects/repository/ProjectsKeySqlTest.java` | Patrón EXACTO: `@ActiveProfiles("test")` + `@WebMvcTest(Controller)` + `@Import(SecurityConfig, JwtAuthFilter, RestAuthenticationEntryPoint, CorsConfig)` + `@MockitoBean` de deps; application-test.yml INTOCABLE; pins SQL en el mismo paquete (constantes package-private + tests de literal) |
| Smoke de patrón | `scripts/qa_c4_smoke.sh`, `qa_c3_smoke.sh` | curl+psql contra la pila real; recomposición ÚNICA `--force-recreate`; patrón a replicar en `scripts/qa_c5_smoke.sh` |

## 3. Análisis de opciones (lo nuevo)

### a) Cadena 1 de seguridad — filtro de API key (reemplazo del denyAll temporal)

El objetivo del Schema: `401 api_key_invalid` en ingesta, `invalid_token`
SÓLO del panel (CD-4 de C2 vive en la cadena 2 y NO se toca).

| Opción | Diseño | Pros | Contras |
|---|---|---|---|
| **A1 (recomendada)** | Filtro `OncePerRequestFilter` en `com.projectlog.ingest` (p. ej. `IngestGuardFilter`/`ApiKeyAuthFilter`), replica del `JwtAuthFilter`: lee `X-API-Key` → `sha256(header)` (String COMPLETO vía `ApiKeyService.sha256`) → lookup en repo → con match: `Authentication` (principal nuevo con `project_id` + `status` deducidos) en el `SecurityContext` → cadena; sin match (header ausente/malformado/hash sin filas/key revocada): contexto VACÍO y `filterChain.doFilter` → la regla `.anyRequest().authenticated()` falla → **entry point NUEVO** de la cadena 1 escribe `401 {"code": "api_key_invalid", ...}` | Copia EXACTA del patrón probado C2 (auth → contexto; entry point único escritor por superficie; sin logger); un solo oráculo (`api_key_invalid` para los 3 casos: ausente/malformado/no-match, anti-enumeración igual que C2/S5); diff de `SecurityConfig` MÍNIMO: quitar `denyAll()` → `authenticated()`, `addFilterBefore(filtro, UsernamePasswordAuthenticationFilter.class)` del bean nuevo, y el entry point propio de la cadena 1 | Nueva clase de entry point (2.ª en el codebase) — pero por SUPERFICIE distinta: el CD-4 "único escritor" era de la API de panel; el literal de ingesta es contrato distinto (Schema §3.4 vs §3.3). `message` del 401 lo fija el spec (los docs no fijan ese literal) |
| A2 descartada | El filtro escribe el 401 él mismo (sin entry point) | Menos clases | Rompe CD-4 (2.º escritor de 401), duplica escritura JSON, pierde el anti-oráculo estructural |
| A3 descartada | Reusar `RestAuthenticationEntryPoint` cambiando el literal | Cero clases nuevas | Rompe CONTRATO del panel (`invalid_token`) y toca fichero congelado C2 — prohibido |

Decisiones ligadas a A1 que debe cerrar el propose/gate:

- **`tiempoConstanteIgual` en el hot-path**: la igualdad binaria la resuelve
  el SELECT por igualdad (índice B-tree de PG). `MessageDigest.isEqual`
  después del lookup es defensa en profundidad (y literal de TRD ADR-4).
  Dos lecturas: (i) seguir el literal C4 spec §7 "lookup → tiempoConstanteIgual
  como comparación final" (requiere recuperar el hash leído, ver b-B2);
  (ii) reconocer que con 256 bits de entropía no hay timing-oracle útil y el
  SELECT basta (B1). Recomendación: si se elige B1, documentar aquí la
  divergencia con el C4 spec; **es decisión de propose, no de explore**.
- **Evolución de SecurityConfig con mínimo riesgo a C2**: la cadena 2
  (`panelChain`) y la 3 quedan byte-a-byte iguales; solo cambia el cuerpo del
  bean `ingestChain`. Los pins de no-regresión (el patrón `ProjectsKeySqlTest`
  de C4 para literales SQL; para SecurityConfig, un test MVC del panel que
  siga devolviendo 401 `invalid_token` — ya existe en `PanelAuthMvcTest`) se
  reaprovechan. El `accessDeniedHandler` de la cadena 1 se MANTIENE (nunca
  403 HTML en la superficie) y el 403 `project_archived` NO viene de
  seguridad sino del servicio (proyecto con key válida y status ARCHIVED).
- **Ubicación del filtro**: `com.projectlog.ingest` (feature owner de la
  ingesta; `keys` es la feature del PANEL para gestionar keys — el filtro es
  infra de la superficie de ingesta). `keys` no gana dependencias hacia la
  ingesta; `ingest` sí importa `keys` (primitivas) y `projects` (repo).
- **El principal**: objeto nuevo (`IngestPrincipal`/record) con `project_id`,
  `status`, (y `api_key_hash` si B2) — el controller/service NUNCA re-hace el
  lookup (una sola ida por request). Análogo a `JwtPrincipal`.

### b) Look-up de key: `findByApiKeyHash` y el mapeo bytea

| Opción | Diseño | Pros | Contras |
|---|---|---|---|
| **B1 (recomendada)** | `Optional<ProjectDto> findByApiKeyHash(byte[] hash)` — EXACTAMENTE la firma documentada por el C4 spec §7: `SELECT <COLS> FROM projects WHERE api_key_hash = ?` | Literal con la frontera C4 (sin gate extra sobre el repo); reusa `COLS`/`FILA` del repo (una sola fuente de columnas, CD-3.2); el principal portará `project_id`+`status` (que el 403 necesita); WHERE por igualdad ya excluye las filas revocadas (`hash` no puede ser `NULL`) | `ProjectDto` no porta `api_key_hash` → la comparación extra `tiempoConstanteIgual` post-lookup no es posible tal cual; el literal del C4spec §7 queda satisfecho a medias (divergencia que el propose debe declarar como D-x) |
| B2 | Query dedicada estrecha: `SELECT id, status, api_key_hash FROM projects WHERE api_key_hash = ?` → record propio (tipo `IngestProjectRef(id, status, hash)`) | Permite la cadena completa sha256→lookup→`tiempoConstanteIgual`; DTO mínimo en el hot-path | Diverge de la firma documentada (mismo camino que C4 con sus D-1..D-6: legal, pero exige cierre S-x + ancla); coloca un DTO de ingesta en el repo del panel o en `ingest` |

**bytea en el driver**: el driver PostgreSQL mapea `byte[]` ↔ `bytea`
nativamente (`setBytes`/`setObject`); es el mismo mecanismo que C4 ya usó en
el UPDATE (`regenerateKey(id, hash, prefix)` con `hash` byte[] probado en pila
por E4.19/E4.20 — `api_key_hash = sha256('<key>'::bytea)`). El índice parcial
único ya está en V1 (sin migración). Riesgo residual bajo pero real (R-2):
verificar la dirección LECTURA (`getObject("api_key_hash", byte[].class)` si
B2, o confiar solo en el WHERE si B1).

**Coste**: O(1) por índice; `projects_api_key_hash_idx` garantiza una fila
por hash. No hay segunda comparación con `MessageDigest` de la CONTRASEÑA
(argon2 solo en login, ADR-4: hash rápido deliberado en el hot-path).

### c) Persistencia del log (tabla `logs` de V1, sin migraciones)

- **Destino**: `LogsRepository` nuevo (SQL inline patrón C2/C3) en
  `com.projectlog.ingest` (package-by-feature: el repo de INSERTA es de la
  feature; C6 creará sus consultas de lectura en su propio corte, sin
  reusar este repo obligatoriamente — la única fuente de columnas de
  `logs` se fija aquí).
- **Columnas a poblar** (todas las de entrada validadas + confianza):
  `project_id` (del principal), `level` (enum), `environment` (enum, NOT
  NULL), `timestamp` (parseada de la entrada), `received_at` (servidor),
  `message`, `exception` (opcional), `metadata` (opcional JSONB),
  `source` (opcional), `correlation_id` (opcional, del cliente).
- **`received_at` server-side y COMÚN POR LOTE** (regla 9): dos caminos —
  (i) `now()` de PostgreSQL: es "transaction timestamp", CONSTANTE dentro de
  una transacción → gratis con un solo `INSERT`; (ii) fijarlo en Java
  (`received_at` calculado UNA vez en el servicio y enviado como parámetro).
  **Recomendación (ii)**: explícito, determinista para tests, y el valor
  exacto que viajará al frame WS de C7 es conocido por el servicio (el seam
  §3.d lo necesita); (i) es menos código pero el servicio no conoce el valor
  que quedó fijado sin releer.
- **`timestamp` del cliente**: parse estricto con `OffsetDateTime.parse` —
  acepta `Z` y offsets (`+02:00`) y RECHAZA el naive sin offset
  (`DateTimeParseException` → 400 `timestamp_without_timezone`);
  normalización a UTC: `TIMESTAMPTZ` en PG ya almacena UTC internamente;
  la salida futura (C6) formatea `Z` con el formatter C2.
- **`correlation_id`**: la APORTA el cliente (opcional, ≤200 chars); ninguna
  generación server-side en ningún doc — NO se genera.
- **`metadata` JSONB**: solo objeto JSON (rechazar array/escalar →
  `metadata_not_object`); al insertar vía `JdbcTemplate`, cast
  `CAST(? AS jsonb)` con el String serializado (o `PGobject`); validación
  del tamaño SOBRE el JSON serializado (§4.1 "serializado").
- **`id`**: `GENERATED ALWAYS AS IDENTITY` — recuperarlos con `RETURNING id`
  (en orden del VALUES) aunque la respuesta de C5 no los use: es la
  moneda del frame `batch` de C7 y cuesta nada (alternativa: insertar a
  secas y re-leer en C7 — peor). Recomendado `RETURNING id` DESDE C5.

### d) Límites: dónde se comprueban y orden

El orden de Schema §3.4 es **tamaño (413, descartado antes de parsear) →
key (401) → archivado (403) → validación (400)**. Consecuencias:

- **413 en el filtro, ANTES del parser JSON** (el controller llega tarde: el
  cuerpo ya se leería). Opciones: (D1) **un único guard de ingesta** (el
  filtro de §a) que primero mira tamaño y después autentica — el orden 413→401
  queda determinista en UNA clase; (D2) dos filtros encadenados
  (`IngestSizeFilter` con orden menor + `ApiKeyAuthFilter`) — separación de
  responsabilidades pero 2 clases y una ordenación frágil por `@Order`.
  **Recomendación D1** (el guard ya lleva el orden contractual; una clase
  menos en `config`).
- **Mecánica del conteo**:
  - Con `Content-Length` presente (curl/HttpClient/fetch lo ponen en JSON
    POST): comparación directa contra el límite → 413 sin leer el cuerpo.
  - **Chunked / sin Content-Length** (el agujero honesto): opciones —
    (i) envolver el `InputStream` en un `BoundedInputStream` (limit = max+1)
    en el propio filtro y que el parser muera al cruzarlo (determinista,
    ~15 líneas); (ii) confiar solo en Content-Length y documentar la
    limitación; (iii) Tomcat (`server.tomcat.max-http-form-post-size` no
    aplica a JSON; `maxSwallowSize` es sobre abortos, no límite de petición).
    **Recomendación (i)** como segunda barrera + Content-Length como
    primera (fail-fast sin leer). El propose decidir alcance: mínimo
    aceptable del Done es (ii)+413 con Content-Length real; (i) es
    refuerzo barato y cierra el caso curl `--header "Transfer-Encoding:
    chunked"` en smoke.
- **Límites por campo y de lote: en el BORDE del controller** (patrón C3,
  manual — SIN starter-validation; añadir `spring-boot-starter-validation`
  sería incoherente con C3/C4 y una dependencia nueva solo por esto):
  batch `1…200` (`batch_empty`/`batch_too_large`, `field: "logs"`), y por
  registro: `environment` requerido (400 `environment_required`, sin default),
  `level` → `level_invalid`, `message` no vacío → `message_required` +
  `field_too_long`, `timestamp_without_timezone`, `metadata_not_object`,
  `field_too_long` para exception/metadata/source/correlation_id con `field`
  (y en batch, `index` del culpable — regla 6).
- **Unidades — CUIDADO (decisión a fijar en propose)**: C3 midió
  `String.length()` (caracteres) para name/description; el Schema habla de
  **KiB** (bytes) para message/exception/metadata y de "caracteres" para
  source/correlation_id ("≤200 caracteres" explícito). Recomendación fiel al
  literal: message/exception/metadata **en bytes UTF-8**
  (32768/131072/65536), source/correlation_id en caracteres (200). El
  propose debe fijarlo (concreción de nivel implementación, como C3).
- **Propiedad de config**: `projectlog.ingest.max-request-bytes` y
  `projectlog.ingest.max-batch-size` en `application.yml` alimentadas por
  `INGEST_MAX_REQUEST_BYTES`/`INGEST_MAX_BATCH_SIZE` (ya interpoladas en
  compose; NADA que tocar ahí). `.env.example` trae `1MiB` — ver R-6.

### e) All-or-nothing — UNA transacción

- **Validación COMPLETA del lote ANTES de abrir la transacción** (regla 6:
  fail-fast; nada llega a BD si un registro falla) — esta es la barrera que
  hace trivial el Done del smoke "batch con intermedio inválido NO persiste
  NADA" (el conteo psql antes/después lo comprueba).
- **Transacción ÚNICA en el servicio** (`@Transactional` multi-sentencia —
  primera vez en el codebase: C3/C4 eran mono-sentencia con autocommit; el
  Javadoc de `ProjectsRepository` ya lo anticipa como "lo multi-sentencia
  (batch all-or-nothing…) llega en C5" — C5 CUMPLE esa nota). Con un solo
  `INSERT multi-VALUES` + `RETURNING id` es en realidad mono-sentencia, pero
  el `@Transactional` queda como defensa y semántica explícita (regla 6).
- **Reacciones a excepciones**: en `logs` NO hay UNIQUE (solo PK identity)
  → `DuplicateKeyException` NO aplica; los fallos de INSERT reales son teóricos
  (FK `project_id` si el proyecto desaparece en carrera → `500` honesto,
  coherente con la política S6 de C4 — sin capturadores de
  `DataIntegrityViolation` enmascarando nada). Rollback automático de la
  transacción cubre el resto.
- **Batch de 200**: un `INSERT ... VALUES (...),(...)...` de 200 filas con
  `RETURNING id` (o `batchUpdate` sin RETURNING — peor para C7). Coste
  despreciable; el orden del lote queda determinado por el orden de VALUES
  (los ids crecen en orden de inserción).

### f) Respuesta 202 `{accepted: N}`

- Literal exacto: `{"accepted": N}`; individual fija `accepted: 1`. Solo
  tras el COMMIT (ADR-9 en espíritu: nada del lote fallido cruza el 202).
- Cuerpo mínimo — SIN campo adicional (ni ids ni received_at: no es
  contracto, y C7 no los necesita desde aquí: el frame WS los lleva el
  propio `LiveBroadcaster` con los datos del seam).
- Content-Type JSON (el 202 sigue siendo respuesta con cuerpo).

### g) Huecos heredados G-1/G-2 de C4 — valoración honesta

- **G-1 (QP-30: rama (6) empty-RETURNING → 404 de `regenerateKey`, carrera
  con DELETE del proyecto entre `findById` y el UPDATE)**: **NO reabrir en
  C5**. La rama es teórica (1 admin, ventana de milisegundos, el id acaba de
  leerse), el C4 verify la clasificó S-1/SUGGESTION y el qa-plan la marcó
  N-A opcional; C5 no toca ese método (solo AÑADE el lookup al repo) y
  obligar a C5 a testear una rama de C4 ensancha falsamente el corte.
  **Recomendación: cerrada como hueco aceptado** — el propose no la lista;
  si algún día revive (P2 multiusuario con concurrencia en el panel), a esa
  versión le toca.
- **G-2 (semántica regla 11: "hash persiste al desarchivar — la misma key
  vuelve a aceptarse")**: **SÍ, se cierra en C5 y casi gratis**. El smoke de
  C5 NECESITA el flujo 403 `project_archived` (E5-x: proyecto ACTIVE con
  key → PATCH `status=ARCHIVED` → ingestar con la MISMA key → 403). Añadir
  un paso final — PATCH `status=ACTIVE` → ingestar → **202** — corrobora
  exactamente la regla 11 + 12 juntas sin escenario nuevo aparte. Coste:
  ~10 líneas de script. **Recomendación: incluirlo en el guion de smoke de
  C5** (el propose lo lista como escenario, el archive de C5 cerrará G-2
  citándolo).
- Quién los pide: ambos quedaron documentados en
  `.atl/sdd/C4/verify-report.md` S-1/S-2 y `archive-report.md` como
  "opcional para C5"; esta explore RECOMIENDA cerrar G-2 (incluido) y
  declarar G-1 cerrado (no reabrible) — decisión final en el gate de C5.

### h) Testcontainers — reevaluación

**Recomendación: NO Testcontainers en C5; reforzar el patrón vigente.**

- La pregunta C3 ("reevalúa en C5") se responde así: el valor teórico de
  Testcontainers aquí es probar JSONB/bytea/TIMESTAMPTZ/all-or-nothing
  contra PostgreSQL real desde `mvn test`. Pero el patrón actual YA
  garantiza eso mediante el smoke [S] contra la PALA REAL (PG 17 en
  contenedor con V1 aplicado, conteos y asertos psql) — C4 lo demostró
  comprobando `api_key_hash` bytea binario, un índice único parcial y
  timestamps `now()`/`updated_at` avanzados con `--force-recreate`.
- Coste de Testcontainers: 2 dependencias nuevas (testcontainers + jdbc),
  pull de imagen en cada entorno, contextos Spring más pesados y lentos,
  y un patrón de suite distinto al del resto (los 77/77 corren en el builder
  de la imagen — añadir docker-in-test dentro de mvn no está probado en esta
  VM).
- Refuerzos concretos al patrón actual que C5 SÍ debe llevar: (1) asertos
  psql de conteo (`count(*)`) antes/después del batch inválido
  (all-or-nothing real); (2) verificación de los valores materializados
  (`timestamp` UTC, `received_at` común al lote, `environment`/`level`
  exactos); (3) el smoke del 413 y del batch de 200 sobre la pila.
- Se reevaluará de nuevo si C6/C7 necesitan iteración rápida con BD real
  fuera de la pila (por ahora la pila es siempre-UP, con recomposición
  `--force-recreate` como paso canónico).

### ¿Dejo punto de emisión preparado para C7 (streaming)?

C7 exige emisión tras COMMIT del lote persistido (ADR-9, frame único
`batch` con ids y `received_at`). C5 NO implementa streaming, pero el
servicio de ingesta es el ÚNICO punto donde existirá ese evento. Opciones:

| Opción | Diseño | Pros | Contras |
|---|---|---|---|
| E1 (recomendada) | Seam mínimo: interfaz `com.projectlog.ingest.IngestListener` (p. ej. `onLotePersistido(lote, projectId, receivedAt, ids)`) + un método nulo default (o sin beans suscriptos) invocado por el servicio DESPUÉS del COMMIT (con `TransactionSynchronization` afterCommit o simplemente por estar fuera del método transaccional) | C7 sustituye/añade un bean sin tocar el core de ingesta; la semántica "tras COMMIT" queda estructurada; el servicio ya calcula `received_at` común (§c-ii) y los ids con RETURNING (todo lo del frame) | Una pieza especulativa mínima: si C7 rediseña, coste de borrar una interfaz ≈ 0. Riesgo de "scope creep" mitigado por el tamaño (1 interfaz + ≤10 líneas) |
| E2 descartada | Nada, C7 refactoriza | Cero especulación | C7 tendría que re-abrir el servicio y la transacción de C5 (más riesgo de regresión que E1) |
| E3 descartada (`ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`) | Idiomática Spring | Formalmente correcta | Maquinaria mayor (events, fase, ordering) para un único consumidor futuro; overkill v1 |

Recomendación de superficie exacta: E1 con el evento SIN clientes en C5
(la llamada a una lista inyectada vacía o `Optional`); decide propose la
firma del listener (debe bastar para construir el frame `batch` de C7:
`batch_id` lo generará C7, no C5).

## 4. Riesgos del corte

| ID | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| R-1 | 413 solo por `Content-Length`: un cliente chunked sin header evade el límite (cliente defectuoso sin rate limit — vigilancia del 06) | Medio | Primera barrera Content-Length + `BoundedInputStream` como cierre (§d); smoke con cuerpos >1 MiB real (truncados vía curl) |
| R-2 | Mapeo bytea en la LECTURA (el lookup): C4 probó la ESCRITURA del hash, no el `WHERE api_key_hash = ?` con byte[] | Bajo-Medio | `setBytes`/`setObject(byte[])` del driver es nativo para `bytea`; smoke lo certifica E2E (key real → 202, key falsificada → 401) |
| R-3 | El nuevo literal `api_key_invalid` en la cadena 1 "contamina" el panel (`invalid_token`) si el entry point se comparte | Alto (rompería C2) | Entry point NUEVO SOLO en `ingestChain`; cadena 2 y 3 byte-a-byte iguales; pin: `PanelAuthMvcTest` sigue esperando `invalid_token` (ya existe) + test MVC de ingesta esperando `api_key_invalid` |
| R-4 | Inserción JSONB vía `JdbcTemplate`: `String` → jsonb sin cast explícito puede fallar (`column "metadata" is of type jsonb but expression is of type character varying`) | Medio | `CAST(? AS jsonb)` con el String serializado (o `PGobject`); smoke aserta lectura (`metadata->>'span'`) |
| R-5 | `received_at` no idéntico en el lote (regla 9) si se fija por-fila con `now()` por sentencia en autocommit | Medio | Fijarlo UNA vez en Java y pasarlo como parámetro (§c-ii); smoke aserta igualdad exacta entre las N filas |
| R-6 | `.env.example` trae `INGEST_MAX_REQUEST_BYTES=1MiB`: el bind a `long` fallaría; el parse de sufijos tipo "MiB" a `DataSize` debe verificarse (Spring Boot suele documentar "MB"; el soporte exacto de "MiB" NO está verificado en este repo) | Bajo | Verificación unitaria de config en apply (bind + `DataSize`); plan B: convertir el valor a bytes en `.env` (`1048576`) — pero `.env.example` es literal del TRD §6.3: si el parse falla, el propose decide entre bindear `DataSize` o documentar; NUNCA tocar el literal sin ancla |
| R-7 | Unidades de campo: "caracteres" (patrón C3 `String.length`) vs "KiB" (bytes) — medir mal rompe el literal del Schema | Medio | Fijar en propose: bytes UTF-8 para message/exception/metadata (fiel a KiB), caracteres para source/correlation_id (el Schema dice "caracteres" ahí); smoke con fronteras exactas (32768/32769 bytes) |
| R-8 | Humo cruzado: el sondeo de C4 E4.15 usa `401 invalid_token` como sondeo de pila (pero sobre `/me`, cadena 2, NO sobre ingesta) | Bajo | Verificado: `qa_c4_smoke.sh` E4.15/E4.28 sondean `/me` y rutas panel (Authorization), NO `/api/v1/ingest`; el smoke C4 NO queda roto por el cambio de la cadena 1 (solo re-ejecutable con pila C5) |
| R-9 | El orden de errores 400 dentro de un registro/lote: si no es determinista, dos ejecuciones devuelven códigos distintos ante el mismo lote (anti-fail-fast) | Bajo | Orden de checks por campo fijo y primer-fallo-gana (idéntico al determinismo de C3 en PATCH); en batch: validar registro por registro en orden del array, primer delincuente con `index` |
| R-10 | `timestamp` con offset pero formato laxo (`OffsetTime`/fechas fuera de rango) | Bajo | `OffsetDateTime.parse` (ISO por defecto, estricto); smoke con offset real `+02:00` y con naive; verificación psql del normalizado a UTC |
| R-11 | Scope creep: la ingesta se infla con validaciones/reintentos/rate-limit (ADR-26 dice NO) o con emisión WS adelantada | Medio | `NO-alcanza` (§5); rate limit = Fase 2/3 solo si el usuario lo activa; el seam E1 acotado a 1 interfaz |

## 5. NO es C5 (fuera de alcance — NO-alcanza)

- **Streaming/emisión WebSocket (C7)**: ni frames ni `LiveBroadcaster`, ni
  el handler real de `/ws/logs`; SOLO el seam E1 mínimo (§3 punto tras el h).
- **Consulta keyset/filtros/detalle (C6)**: `GET /logs`, LogDto, cursor —
  el repo de lectura de logs NO se anticipa.
- **Frontend (C8)**: los errores de ingesta no tienen UI (AppFlow flujo 10).
- **Borrados definitivos y resumen (C10)**, **rate limiting 429** (ADR-26:
  NO en v1), **retención** (P2), **backups** (v2).
- **Panel /api/v1/panel/** y `/ws/logs` (cadena 2/3): byte-a-byte intactos;
  `findByApiKeyHash` es ADITIVO en `ProjectsRepository` (nada de C3/C4 se
  reescribe; pin SQL del patrón ProjectsKeySqlTest).
- **Migraciones**: V1 es la única (los índices y columnas ya existen).

## 6. Preguntas abiertas para sdd-propose C5

1. B1 vs B2 para el lookup (firma documentada de C4 vs DTO estrecho con
   hash) y su impacto en el uso/no-uso de `tiempoConstanteIgual` (si B1,
   declarar la divergencia con el C4 spec §7 como D-x).
2. Unidades de los límites de campo (bytes vs caracteres) — recomendación
   en §d.
3. Mecánica de 413 sin Content-Length: ¿BoundedInputStream (refuerzo) o
   solo Content-Length (mínimo del Done)?
4. Firma del seam E1 para C7 (qué lleva `onLotePersistido`).
5. Confirmar cierre declarado de G-1 (no reabrible) y cierre activo de G-2
   (escenario de smoke con desarchivar → 202 misma key).
6. `message` de los 401/403/413 de ingesta: los docs fijan el `code`; el
   patrón C3/C4 exige mensajes descriptivos — fijar con ancla (el de
   `project_archived` de PANEL ya existe pero su mensaje de INGESTA puede
   diferir; C4spec §7 lo deja al spec de C5).