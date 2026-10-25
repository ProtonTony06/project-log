---
change: C5
artifact: design
last_updated: 2026-10-25
status: draft
---

# Design — C5 · Ingesta REST

> Fase sdd-design del corte C5. Este documento **IMPLEMENTA
> arquitectónicamente** el spec (`spec.md`, status draft) — no lo renegocia.
> Fuentes normativas: spec C5 (R5.1–R5.19, cierres S5-1…S5-15, D-C5-1,
> escenarios E5.1–E5.34) + proposal C5 (status approved) + código real
> verificado en HEAD `6cc609f` (2026-10-25): `SecurityConfig`,
> `JwtAuthFilter`, `RestAuthenticationEntryPoint`, `ApiKeyService`,
> `ProjectsRepository`, `ProjectsController`, `UpdateProjectRequest`,
> `V1__init.sql`, `pom.xml`, `application.yml`, `.env.example`,
> `PanelAuthMvcTest`, `ProjectsKeySqlTest`, `scripts/qa_c4_smoke.sh`.
> Stack: Spring Boot 3.5.16 / Java 21 / PG17 — **cero cambios de pom,
> cero migraciones, sin starter-validation, sin Testcontainers** (R5.2).

## 0. Resumen del diseño

La cadena 1 (`/api/v1/ingest/**`) pasa de `denyAll()` TEMPORAL a superficie
real: un **guard único** (`IngestGuardFilter`, réplica del patrón
`JwtAuthFilter`) aplica el orden contractual 413→401 en una sola clase
(doble barrera de tamaño + auth SHA-256), un **entry point propio**
(`IngestAuthenticationEntryPoint`, réplica de `RestAuthenticationEntryPoint`)
escribe el 401 `api_key_invalid` (el del panel, `invalid_token`, queda
intacto), y el controller gatea 403 (archivado, con key VÁLIDA) antes de
delegar en `IngestService` la validación completa fail-fast y el INSERT
multi-VALUES all-or-nothing. El seam `IngestListener` (C7) nace con lista
vacía y se invoca estructuralmente tras el COMMIT (el service NO es
transaccional; la única `@Transactional` del corte está en
`LogsRepository.insert`).

## 1. Mapa del paquete `com.projectlog.ingest`

R5.1 cierra la lista EXACTA de ficheros (S5-15 eliminó
`IngestBatchRequest`/`IngestAcceptedResponse`; nace `LogValidado`). El
`package-info.java` de C1 queda INTOCABLE (su rol marcador se cumple).

| Fichero (NUEVO salvo indicación) | Rol | Trazabilidad |
|---|---|---|
| `ingest/IngestGuardFilter.java` | `OncePerRequestFilter` `@Component` — orden 413→401 en el filtro (S5-12, R5.4 / D-1/D-5/D-6/D-8) | R5.4, escritor del 413 (R5.9), S5-12, S5-9 |
| `ingest/IngestAuthenticationEntryPoint.java` | `@Component implements AuthenticationEntryPoint, AccessDeniedHandler` — ÚNICO escritor del 401 de ingesta | R5.5, S5-11, R5.10 |
| `ingest/IngestPrincipal.java` | record `(long projectId, String status)` — análogo a `JwtPrincipal` | R5.6-a, B1 del gate Punto 1 |
| `ingest/IngestController.java` | 2 mappings (`POST`, `POST /batch`), `@RequestBody JsonNode`, gates 403 y de lote, handler de ilegibilidad, 202 | R5.7, R5.8, R5.9, R5.10, S5-1, S5-3, R5.24-c |
| `ingest/IngestService.java` | validación completa fail-fast → `received_at` UNA vez → insert → seam. Contiene el record anidado `ErrorValidacion` y los constants de los 400 de validación (D-7) | R5.12, R5.24, S5-7, S5-8, D-7 |
| `ingest/LogsRepository.java` | ÚNICA fuente SQL de INSERT sobre `logs`; `@Transactional` del corte (D-4) | R5.11-b/c/d, R5.12 |
| `ingest/LogIngestRequest.java` | record de los 8 campos literales del Schema §3.4 (`metadata` como `JsonNode` crudo) + factoría `desdeJson(JsonNode)` (patrón literal `UpdateProjectRequest.desdeJson`) | R5.24-b, S5-1, S5-2, R5.24-a |
| `ingest/LogValidado.java` | record moneda del INSERT — `timestamp` ya parseado (`OffsetDateTime`), `metadata` ya serializado (String) | S5-15, S5-8, R5.11-d |
| `ingest/IngestListener.java` | seam C7 — interfaz, 1 método, lista de suscriptores VACÍA | R5.13, S5-10 |
| (EXTRA en ficheros cerrados) | `config/SecurityConfig.java` (solo `ingestChain`), `projects/repository/ProjectsRepository.java` (+1 método aditivo), `application.yml` (+2 propiedades + comentario), `scripts/qa_c5_smoke.sh` (NUEVO) | R5.3, R5.6, R5.11, R5.14, R5.17 |

Piezas anidadas SIN fichero propio (la lista R5.1 no las da de alta):
`IngestGuardFilter.CuerpoDemasiadoGrandeException` (excepción estática
anidada, D-5) y `IngestService.ErrorValidacion` (record estático anidado,
D-7).

**Dependencias (R5.2, rígidas):** `ingest` → `keys` (SOLO
`ApiKeyService.sha256(String)` — primitiva existente, visibilidad `public`,
sin cambios; `tiempoConstanteIgual` NO se usa en el hot-path, D-C5-1) e
`ingest` → `projects` (`ProjectsRepository.findByApiKeyHash`, R5.11-a).
`keys` y `projects` no ganan dependencias hacia `ingest`; `streaming/**`
intocable. El `ObjectMapper` lo aporta el autoconfig de Boot (un solo bean,
D-3).

## 2. Micro-decisiones numeradas (D-1…D-12)

- **D-1 — El lookup va en el FILTRO, no en el service.** R5.4.3 lo pone en
  el filtro y lo ratifico: el orden contractual 413→401 debe resolverse
  ANTES de cualquier código MVC (el controller llega tarde: su parse del
  body ya habría pasado el gate 2). Un filtro delegante (el servicio hace el
  lookup) exigiría llamar al servicio desde el filtro — inversión de
  capas — o duplicar la credencial a medio transporte. El guard es
  autónomo: `ApiKeyService.sha256(header)` →
  `projectsRepository.findByApiKeyHash(hash)` → `IngestPrincipal` en el
  `SecurityContext` (o continuar sin contexto). El service NO re-hace el
  lookup nunca (una sola ida por request — proposal §2.2); recibe solo el
  `projectId` ya deducido.
- **D-2 — El proyecto viaja como `Authentication` en el `SecurityContext`**
  (no request-attribute, no re-lookup): réplica literal del patrón
  `JwtAuthFilter` (`UsernamePasswordAuthenticationToken(principal, null,
  List.of())`), R5.6-a verbatim. El controller lo lee con
  `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` y
  el 403 necesita solo `principal.status()` (el `status` viaja en el
  principal por B1: `ProjectDto.status` — `FILA` lo trae de `COLS`). Si
  `principal` no es `IngestPrincipal` (defecto estructural, inalcanzable
  con `authenticated()`): el controller responde 401 `api_key_invalid`
  HONESTO reutilizando las constants package-private del entry point
  (`CODE_API_KEY_INVALID`, D-3: mismas classes, mismo paquete) — jamás un
  500 enmascarado.
- **D-3 — UN solo `ObjectMapper`: el bean autoconfigurado de Boot**,
  inyectado por constructor en los 3 escritores JSON de la superficie
  (entry point, guard, controller) — patrón literal de
  `RestAuthenticationEntryPoint`/`ProjectsController` (inyección, NO
  instancia estática, NO `new ObjectMapper()` suelto: una única convención
  de serialización, CD-2/CD-3 de C2/C3). Para el entry point de ingesta el
  `ObjectMapper` es el mismo bean que usa el panel — la DIVERGENCIA solo
  existe en el LITERAL, no en el escritor técnico, y ese literal vive en
  constants propias de cada clase (C0 de `RestAuthenticationEntryPoint`
  intocado).
- **D-4 — La ÚNICA `@Transactional` del corte va en
  `LogsRepository.insert`.** R5.11-c/R5.12 son literales del spec y los
  sigo: `@Transactional` en el MÉTODO DEL SERVICIO (wording de proposal
  §2.5) arrastraría el seam (invocado tras el insert) a DENTRO del scope
  transaccional — el "tras-COMMIT" quedaría solo por convención, no por
  estructura. Con la anotación en el repo, el `IngestService.registrar`
  es un método público PLANO (sin proxy transaccional): el COMMIT ocurre
  al RETORNAR `insert` y el seam es la sentencia siguiente — la emisión
  queda estructuralmente tras el COMMIT (R5.13/S5-10/E5.20c). Un INSERT
  multi-VALUES es una sola sentencia = UNA transacción (regla 6/ADR-11);
  el `@Transactional` documentado en el repo es defensa (patrón R3.2 heredado
  — el Javadoc de `ProjectsRepository` ya anticipaba "lo multi-sentencia
  llega en C5": se cumple ahí, no en un scope mayor). Nota para spec/tasks:
  la proposal decía "en el método del servicio"; el spec (§1.F/R5.11-c)
  la refina al repo — el design sigue al spec (sin divergencia: es cierre
  S5-10 de propuesta→spec).
- **D-5 — La excepción de la barrera 2 DEBE ser `RuntimeException`**
  (clase estática anidada en el filtro,
  `IngestGuardFilter.CuerpoDemasiadoGrandeException extends
  RuntimeException` — nombre final de design; la lista R5.1 no permite
  fichero propio). RAZÓN crítica: si fuese `IOException`, Jackson la
  envolvería en `HttpMessageNotReadableException` y el handler de
  ilegibilidad del controller respondería **400** — rompería el orden
  (413 NUNCA se degrada a 400). Una `RuntimeException` atraviesa Jackson y
  el `DispatcherServlet` SIN envoltorio y baja por la filter chain hasta el
  `catch` del propio guard (colocado alrededor de `chain.doFilter`), que
  escribe el MISMO 413 (R5.4.2). Filtra además: el catch SOLO captura ese
  tipo exacto (nunca enmascara otras excepciones) y solo escribe si
  `!response.isCommitted()`.
- **D-6 — Normalizador del límite (S5-9):** método `static`
  package-private `long limiteBytes(String)` en `IngestGuardFilter` —
  unit-testable SIN Spring (E5.34): regla = si el literal acaba en `iB`
  quitar la `i` (`1MiB`→`1MB`, `1KiB`→`1KB`, `1GiB`→`1GB`, `1TiB`→`1TB`;
  dígitos puros y `…B` quedan) y resolver con `DataSize.parse(...).toBytes()`
  (spring-core 6.2.19: `parse("1MB")` → 1048576, KB/MB/GB SON binarios
  1024-base — verificado localmente, cierre S5-9). El `@Value` es String
  (`"projectlog.ingest.max-request-bytes:1MiB"`, default literal =
  `.env.example`/TRD §6.3) y el constructor llama al normalizador UNA vez
  (guarda el `long` en un campo `final`): fail-fast al arrancar si el
  literal no casa. JAMÁS `DataSize` directo en `@Value` (fallaría con
  `1MiB`).
- **D-7 — Colocación de los constants de los 400 (refinamiento de R5.10,
  literales IDÉNTICOS).** R5.10 agrupa "403/400 en IngestController", pero
  R5.12 pone la VALIDACIÓN en `IngestService` (dueño del orden
  primer-fallo-gana, R-9) y R5.24-c pone el gate de lote (batch_empty/
  batch_too_large) en el CONTROLLER sobre el `JsonNode`. Sin duplicar
  literales, el design reparte por PROPIETARIO del check: en
  `IngestController` los 3 constants de SUS gates (`CODE_PROJECT_ARCHIVED`,
  `CODE_BATCH_EMPTY`, `CODE_BATCH_TOO_LARGE` + sus `MSG_*`); en
  `IngestService` los 6 constants de los 400 por campo
  (`CODE_ENVIRONMENT_REQUIRED`, `CODE_TIMESTAMP_WITHOUT_TIMEZONE`,
  `CODE_LEVEL_INVALID`, `CODE_MESSAGE_REQUIRED`, `CODE_FIELD_TOO_LONG`,
  `CODE_METADATA_NOT_OBJECT` + `MSG_*` y los límites 32768/131072/65536/200)
  — es el componente escritor del CONTENIDO del error; el controller solo
  MATERIALIZA el `ErrorValidacion` devuelto. Los 11 literales son EXACTOS
  a R5.10 (ninguno cambia); solo su fichero de residencia queda documentado
  aquí (ver §9 Divergencias, para ratificación en tasks).
- **D-8 — Alcance del guard: `shouldNotFilter(HttpServletRequest)`.** Los
  beans `Filter` son auto-registrados por Boot en la servlet chain GLOBAL
  (así funciona hoy `JwtAuthFilter`, benigno por su no-op); para el guard
  eso sería una FUGA real (un 413 sobre cuerpos grandes fuera de ingesta y
  una lectura de `X-API-Key`+lookup en panel/ws). Cierre mínimo sin fichero
  nuevo ni bean extra (R5.1): el filtro sobrecarga `shouldNotFilter(...)` →
  `true` (no filtrar) salvo URI igual a `/api/v1/ingest` o que empiece por
  `/api/v1/ingest/` (rango del matcher de la cadena 1, S5-12a). Con la
  `OncePerRequestFilter` el marcaje de "ya filtrado" evita doble ejecución
  (auto-registro vs cadena de seguridad): la barrera corre UNA vez por
  request, antes del security chain si Boot la registra primero — el orden
  413→401 se conserva EXACTO en ambas colocaciones.
- **D-9 — Ficheros de los pins SQL (E5.15, "design fija")**: el pin de los
  3 constants del INSERT (`INSERT_LOGS_CABECERA`, `INSERT_LOGS_FILA`,
  `INSERT_LOGS_RETORNO`) va en
  `backend/src/test/java/com/projectlog/ingest/LogsRepoSqlTest.java`
  (mismo paquete que los constants package-private — patrón
  `ProjectsKeySqlTest`); el pin de `SELECT_POR_API_KEY_HASH` (constant
  package-private `static final` en `ProjectsRepository`) va en
  `backend/src/test/java/com/projectlog/projects/repository/ProjectsIngestSqlTest.java`
  (JUNTO a `ProjectsKeySqlTest` — el constant no es visible desde `ingest`;
  el alternative "todo en ingest" obligaría a `public` y ensancharía la
  visibilidad del repo del panel). Dos ficheros de test NUEVOS, cero tests
  de C1–C4 tocados.
- **D-10 — El handler de ilegibilidad distingue endpoint por URI**: un solo
  `@ExceptionHandler({HttpMessageNotReadableException.class,
  HttpMediaTypeNotSupportedException.class})` en el controller que decide
  individual-vs-batch con `request.getRequestURI().endsWith("/batch")` (los
  2 mappings son rutas literales — determinista, sin atributos de request
  ni interceptores). Individual → 400 `environment_required`
  (`field: "environment"`); batch → 400 `batch_empty` (`field: "logs"`)
  (cierre S5-1 exacto).
- **D-11 — El parse de `timestamp` es un helper `static` package-private**
  (`OffsetDateTime parsearTimestamp(String)`) en `IngestService`:
  `OffsetDateTime.parse` sin formatter custom (ISO_OFFSET_DATE_TIME del
  JDK: acepta `Z` y `±hh:mm`, RECHAZA el naive — cierre S5-5). Testable en
  puro (E5.18) sin levantar el servicio completo.
- **D-12 — Excepciones de los listeners del seam: NO se capturan.** Sin
  ancla contractual en C5 (cero clientes); si el futuro suscriptor lanza,
  el request acabaría en 500 DESPUÉS del COMMIT (la fila YA está
  persistida — semántica at-least-once). C7 definirá su propio contrato de
  fallo al implementar el suscriptor; C5 solo documenta esta expectativa en
  el Javadoc del seam.

### 2.1 Ratificaciones de gate 2026-10-25 (cierre del bloqueo T5.5 / hallazgo E5.28)

- **D-13 (RATIFICADA en gate)** — bean-higiene `filtroGuardSoloCadena1`
  (`FilterRegistrationBean<IngestGuardFilter>` con `setEnabled(false)`) en
  `SecurityConfig`: el guard queda miembro EXCLUSIVO de la cadena 1
  (patrón del `JwtAuthFilter` de C2). Desviación del alcance literal «diff
  solo en ingestChain + Javadocs», justificada por el hallazgo E2E E5.28.
- **D-14 (RATIFICADA en gate)** — `ObjectProvider` lazy para cerrar el
  bloqueo T5.5: en el ctor de `IngestGuardFilter`
  (`ObjectProvider<ApiKeyService>`/`<ProjectsRepository>`, resueltos
  LAZY en `doFilterInternal`) y en los 2 parámetros de
  `SecurityConfig.ingestChain` (delegantes lazy por lambda: `@WebMvcTest`
  incluye `Filter` pero NO `AuthenticationEntryPoint`, así que el
  `getObject()` a build-time estallaba en 46 slices). Cero asserts de
  C1–C4 tocados; suite final 107/107 BUILD SUCCESS.
- **D-15 (RATIFICADA en gate)** — hallazgo E5.28 del smoke: `FrameworkServlet.
  processRequest` (6.2.19) envuelve cualquier RuntimeException del cuerpo en
  `jakarta.servlet.ServletException("Request processing failed: …", causa)`
  antes de regresar al guard; los 3 catches desenvolvedores en
  `IngestGuardFilter` (con `contieneBarrera2`, re-lanzando intacto lo demás)
  quedan como solución final. El TIPO de D-5 se mantiene (RuntimeException).

## 3. `SecurityConfig` — diff conceptual EXACTO (R5.3.1 / R5.6)

**Archivo:** `backend/src/main/java/com/projectlog/config/SecurityConfig.java`.
**Diff: SOLO el bean `ingestChain`** (cuerpo + Javadoc del bean + 2 líneas
del Javadoc de clase: la lista de la cadena 1 deja de decir
"deny-by-default (S2)... entry point compartido" y describe el guard + entry
point PROPIOS de ingesta). El **constructor y sus 2 campos quedan
byte-a-byte**; `panelChain`/`defaultChain`/`common` quedan byte-a-byte
(R5.19b). Los 2 beans nuevos entran como **parámetros del método `@Bean`**
(inyección por método de Spring — el constructor NO cambia; diff mínimo
sobre fichero cerrado C1–C4):

| Parámetro nuevo | Bean origen | Uso en la cadena |
|---|---|---|
| `IngestGuardFilter ingestGuardFilter` | `@Component` del paquete `ingest` | `addFilterBefore(ingestGuardFilter, UsernamePasswordAuthenticationFilter.class)` — el MISMO ancla literal del `panelChain` (patrón único de la casa); corre antes del filtro de autorización que evalúa `authenticated()`. Respecto al `JwtAuthFilter` NO hay relación de orden que gestionar (ese filtro SOLO está registrado en la cadena 2) |
| `IngestAuthenticationEntryPoint entryPointIngesta` | `@Component` del paquete `ingest` | `exceptionHandling(eh -> eh.authenticationEntryPoint(entryPointIngesta).accessDeniedHandler(entryPointIngesta))` — JAMÁS 403 HTML en la superficie (R5.5) |

**Cuerpo del bean EXACTO a R5.6** (el spec ya lo da como bloque Java; el
apply lo copia tal cual): `common(http)` → `.securityMatcher("/api/v1/ingest/**")` → `addFilterBefore` →
`.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())` →
`exceptionHandling` con el entry point de ingesta en ambos papeles →
`.build()`. En concreto vs el bean actual: `denyAll()` TEMPORAL →
`authenticated()`; el 401 de la cadena pasa de `invalid_token` (entry point
compartido) a `api_key_invalid` (entry point PROPIO); **sin `cors()`** (se
conserva el comentario — TRD §5; `CorsConfig` intocable, `allowedHeaders`
sigue SIN `X-API-Key`, cierre S7 de C4).

**Garantía de no-contaminación de C1–C4 (R-3 del spec):** el pin E5.16-b de
R5.19 (c) es la RED ya existente — `PanelAuthMvcTest`
(`backend/src/test/java/com/projectlog/auth/PanelAuthMvcTest.java`, test
`meSinHeaderResponde401JsonInvalidToken` y el resto: cada uno esperando
`"invalid_token"` con `@Import({SecurityConfig, JwtAuthFilter,
RestAuthenticationEntryPoint, CorsConfig})`). Si la cadena del panel
viese el entry point de ingesta, estos tests ROMPEN al instante
(`jsonPath("$.code").value("invalid_token")`), lo que hace imposible la
fuga del literal `api_key_invalid` al panel. La RE-CORRIDA completa de la
suite C1–C4 (77/77, R5.19b) certifica `panelChain`/`defaultChain`
byte-a-byte. Además el `RestAuthenticationEntryPoint` del panel NO recibe
import nuevo ni toca diff (su constant `CODE_INVALID_TOKEN` queda
package-private en su paquete, sin cambios).

## 4. `IngestGuardFilter` — orden interno por petición (R5.4)

`@Component`, `OncePerRequestFilter` en `com.projectlog.ingest`, SIN
loggers (R5.15), jamás lee la respuesta salvo para escribir su 413, jamás
loguea header/key/hash. Constructor: `ApiKeyService`, `ProjectsRepository`,
`ObjectMapper`, y `@Value("${projectlog.ingest.max-request-bytes:1MiB}")
String limiteTexto` → `long limite` UNA vez con el normalizador D-6
(fail-fast al arrancar). D-C5-1 citada en el Javadoc (R5.16).

| Paso | Mecánica | Respuesta | Trazabilidad |
|---|---|---|---|
| 0. Enrutado | `shouldNotFilter` → fuera de `/api/v1/ingest(/**)` continua sin envoltorio ni nada (D-8) | — | S5-12(a) |
| 1. Barrera 1 — TAMAÑO | `request.getContentLengthLong()`; si `≥ 0 && > limite` → escribir 413 (constantes `CODE_PAYLOAD_TOO_LARGE`/`MSG_PAYLOAD_TOO_LARGE` del filtro vía `ObjectMapper`) y `return` SIN tocar el cuerpo ni continuar la cadena | 413 `payload_too_large` | R5.4.1, R5.9, S5-12, E5.27, E5.33 |
| 2. Envoltorio (barrera 2 — chunked) | SIEMPRE continuar con un `HttpServletRequestWrapper` cuyo `getInputStream()` devuelve un `ServletInputStream` decorador PROPIO (clase anidada): cuenta bytes leídos; al leer el byte que supera `limite` (frontera EXACTA: cuerpo de `limite` bytes LEGAL, el `limite+1` NO) lanza la `CuerpoDemasiadoGrandeException` (nested `RuntimeException`, D-5) | (nada aún) | R5.4.2, S5-9, E5.27/E5.28 |
| 3. Autenticación | `request.getHeader("X-API-Key")`: ausente → sin contexto, seguir (patrón JwtAuthFilter). Presente → `apiKeyService.sha256(header)` (String COMPLETO — ni trim de `plk_`, ni hex, ni salt; contrato C4 §0.2) → `projectsRepository.findByApiKeyHash(hash)` → si hay match: `SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new IngestPrincipal(dto.id(), dto.status()), null, List.of()))`; sin match / revocada (fila excluida por el `WHERE` — hash NULL nunca matchea): NO escribir respuesta, NO setear contexto, seguir — la regla `authenticated()` falla y el entry point escribe el 401 | 401 lo escribe el entry point | R5.4.3, R5.6-a, D-1, D-C5-1, E5.12/E5.16/E5.24 |
| 4. Continuar | `try { filterChain.doFilter(wrappedRequest, response); } catch (CuerpoDemasiadoGrandeException ex) { escribir413(response); }` — el envoltorio SIEMPRE viaja (R5.4.4); el catch solo ese tipo y solo si `!response.isCommitted()`; sin rethrow | 413 (barrera 2) | D-5, E5.28 |

Detalles del envoltorio (clase anidada):
- Implementa `ServletInputStream` delegando `read()` por byte (y `read`
sobre `byte[]` acumulando el conteo) al stream original; NO hay caché ni
doble lectura (el body se lee UNA vez por el parser de Jackson; el filtro
nunca lo consume). `isFinished`/`isReady` delegan; `isAvailable` devuelva
lo legible; `setReadListener` lanza `UnsupportedOperationException`
(no hay async en esta superficie).
- El conteo es DEL CÓDIGO DEL FILTRO, determinista: el decorador es el único
camino de lectura del cuerpo (el wrapper reemplaza
`getInputStream()`; también cubre `getReader()` devolviendo
`new BufferedReader(new InputStreamReader(getInputStream(), UTF_8))` por si
un conversor lo usare — un solo conteo posible por diseño).

**413 sin leer el cuerpo (R-12 / spec §2.6):** la barrera 1 escribe y NO
drena el cuerpo (fail-fast). Si en E5.27 curl manifestase "connection
reset", el apply activa el plan B documentado (drenar ACOTADO hasta
`limite+1` antes de responder) SIN cambiar el contrato (cuerpo y `code`
iguales) — la decisión queda en manos del smoke real, no del diseño.

## 5. `IngestAuthenticationEntryPoint` (R5.5 / S5-11)

`@Component implements AuthenticationEntryPoint, AccessDeniedHandler` —
réplica LITERAL de `RestAuthenticationEntryPoint`
(`backend/src/main/java/com/projectlog/config/RestAuthenticationEntryPoint.java`):
`ObjectMapper` por constructor (D-3), SIN logger, SIN lectura del header.
`commence(...)` y `handle(...)` llaman al MISMO helper privado
`escribir401(HttpServletResponse)` → `setStatus(401)` + `Content-Type:
application/json` + `objectMapper.writeValue(getWriter(), Map.of("code",
CODE_API_KEY_INVALID, "message", MSG_API_KEY_INVALID))`. Constants: `static
final String CODE_API_KEY_INVALID` package-private (visible al paquete
`ingest` — la usan el `IngestController` para su 401-honesto defecto, D-2) y
`private static final String MSG_API_KEY_INVALID = "API key de ingesta
ausente o inválida"`. Sin varianza por causa (sin header / malformado /
sin match / revocada → EL MISMO cuerpo, E5.12; jamás un 403 HTML — la
`AccessDeniedException` que llegara aquí sería un defecto: el 403 real es
del controller con key VÁLIDA, R5.9).

## 6. `IngestController` (R5.7–R5.10, S5-1, S5-3, R5.24-c)

`@RestController` + `@RequestMapping("/api/v1/ingest")` (patrón C3);
constructor: `IngestService`, `ObjectMapper`, y
`@Value("${projectlog.ingest.max-batch-size:200}") int limiteLote` (bind int
nativo de Spring — fallo de arranque si no parsea; conversión UNA vez, R5.14).
Sin DTO wrapper de batch (S5-15); ambos endpoints se unifican en un método
privado `procesar(JsonNode cuerpo, boolean lote)`:

| # | Check (componente EXACTO del lugar) | Fallo → respuesta |
|---|---|---|
| 0 | `getAuthentication().getPrincipal()` → si NO es `IngestPrincipal` (defecto estructural, D-2) | 401 `api_key_invalid` (constants del entry point, package-private) |
| 1 | **Gate 3 — archivado (con key VÁLIDA)**: `principal.status()` == `"ARCHIVED"` (comparación exacta, sin normalización — el `status` llega del `ProjectDto`) — ANTES de construir/parsear la validación de campos (R5.8 verbatim: "un pedido con cuerpo inválido a proyecto archivado responde 403") | 403 `{"code": "project_archived", "message": "El proyecto está archivado: no acepta logs"}` — sin `field` (constants del controller, D-7) — E5.13/E5.25 |
| 2 | **Gate de lote (solo batch, S5-3/R5.24-c)** sobre el `JsonNode`, ANTES de parsear elemento alguno: `path("logs")` no array (ausente, no-array) o array de 0 → 400 `batch_empty` `field: "logs"`; array > `limiteLote` → 400 `batch_too_large` `field: "logs"` (mensaje compuesto con el límite efectivo, R5.10). NADIE re-especializa el 413 en 400 (R-13: lote de 200 × 32 KiB > 1 MiB lo corta el 413 ANTES, en el filtro — primera barrera contractual) | 400 con `field: "logs"` — E5.10/E5.32 |
| 3 | Parse por registro en orden del array: `LogIngestRequest.desdeJson(JsonNode)` (individual: 1 llamada sobre el propio cuerpo; batch: loop con `index` correlativo 0-based) | (nada; los 400 de campo salen del service) |
| 4 | `ingestService.registrar(projectId, registros, lote)` → `Optional<ErrorValidacion>`; si hay error → 400 materializado (helper `error(...)`) con `code/message/field` y `index` SOLO si `lote` y `index != null` (S5-3: en individual JAMÁS `index`, aunque valdría 0) | 400 — E5.3–E5.11, E5.14, E5.23, E5.26 |
| 5 | Sin error → `ResponseEntity.ok(objectMapper.valueToTree(Map.of("accepted", registros.size())))` (individual SIEMPRE 1 por la barrera del lote); con `.contentType(MediaType.APPLICATION_JSON)` — Jackson serializa Map sin espacios: `{"accepted":1}` literal R5.9/R5.10-a | 202 — E5.1/E5.2/E5.21/E5.22 |

- **Handler de ilegibilidad (S5-1, D-10)** — solo en este controller:
`@ExceptionHandler({HttpMessageNotReadableException.class,
HttpMediaTypeNotSupportedException.class})` → 400 con code/field del SEGUNDO
endpoint (individual → `environment_required`, batch → `batch_empty` por
URI, D-10). Con esto un cuerpo "no-json", vacío o con Content-Type ajeno
(sin converter) entra por la MISMA puerta que `{}` — sin 415 fuera del
contrato §3.1, sin `body_invalid` nuevo (CD-3.9).
- Helper privado `error(HttpStatus, String code, String message, String
fieldOrNull, Integer indexOrNull)` — réplica LITERAL del helper `error` de
C3 (`LinkedHashMap`: `code`, `message`, `field` solo si aplica, `index` SOLO
si aplica — claves en ese orden; `objectMapper.valueToTree`;
`MediaType.APPLICATION_JSON`). Un solo método cubre los 3 usos (403, 400 de
lote, 400 de ErrorValidacion).
- El service JAMÁS toca HTTP (sin `ResponseEntity` ni
`HttpServletResponse`: la moneda entre controller y service es
`LogIngestRequest` de ida y `Optional<ErrorValidacion>` de vuelta; el
seam/recibido_at nunca cruzan HTTP).

## 7. `LogIngestRequest` y `LogValidado` (R5.24-a/b, S5-1/S5-2, S5-15)

```java
public record LogIngestRequest(        // entrada cruda en el orden literal del Schema §3.4
        String level, String timestamp, String message, String exception,
        com.fasterxml.jackson.databind.JsonNode metadata,   // crudo: se serializa UNA vez (S5-8)
        String environment, String source, String correlationId) {

    public static LogIngestRequest desdeJson(JsonNode nodo) { … }   // patrón LITERAL UpdateProjectRequest.desdeJson
}
```

- `desdeJson`: patrón C3 `valor(nodo, campo)` — `null` si el nodo es
`null`/`isNull()`, si no `asText()` (coerción Jackson: escalar → forma
texto; contenedor → `""` — determinista, R5.24-a; la coerción NO toca los
enums: esos checks se hacen DESPUÉS sobre el String, con el caso
"no textual" ya cubierto por el mismo check). `metadata` se guarda como
`JsonNode` CRUDO (presente o no) — NADA lo serializa todavía (S5-8: UNA
serialización, en el service). Campos NO leídos (los 8 nombres literales
solo): IGNORADOS por construcción — un `project_id`/`received_at`
falsificado por el cliente JAMÁS llega a la BD (S5-2, E5.10-fam).
- `correlationId` (camelCase Java → la CLAVE JSON es `correlation_id`
literal); sin normalización en ningún campo textual opcional: vacío
presente es VÁLIDO y se persiste vacío; `null`/ausente → columna NULL.

```java
public record LogValidado(           // moneda del INSERT (salida de la validación)
        String level, String environment, OffsetDateTime timestamp,
        String message, String exception, String metadataJson,
        String source, String correlationId) { }
```

Nace en el service tras validar el registro completo; `timestamp` ya
parseado, `metadataJson` ya serializado (o `null`). Es lo ÚNICO que el repo
recibe (contrato de frontera: el repo no valida, no parsea, no serializa).

## 8. `IngestService` (R5.12, R5.24, S5-7/S5-8, R5.13)

`@Service` (bean mockeable en los MVC tests). Constructor: `LogsRepository`
+ `List<IngestListener> oyentes` (Spring inyecta lista VACÍA si no hay
beans — cierre S5-10; `Optional<IngestListener>` descartado: no modela
0..n). Método público ÚNICO (NO transaccional — D-4):

```java
public Optional<ErrorValidacion> registrar(
        long projectId, List<LogIngestRequest> registros, boolean lote)
```

donde `ErrorValidacion` es record anidado `(String code, String message,
String field, Integer index)` (`index` = 0-based en batch, `null` en
individual — S5-3). Flujo EXACTO:

1. **Validación COMPLETA** (regla 6 verbatim) — loop de registros en orden
del array (`index` creciente), por registro en orden DETERMINISTA R5.24
(primer-fallo-gana, R-9); al primer fallo `return Optional.of(...)` —
NADA tocó la BD. Por registro (check → pareja code/field — constants del
service, D-7):
    1. `environment`: null / no textual / `isBlank()` / fuera de
       `ENTORNOS = Set.of("development","staging","production")`
       (case-sensitive EXACTO, sin normalización) → 400
       `environment_required` / `environment` (S5-4: UN code para las 4
       variantes).
    2. `timestamp`: parse con D-11 (`OffsetDateTime.parse`) — null / no
       textual / no parseable / naive → 400
       `timestamp_without_timezone` / `timestamp` (S5-5). El
       `OffsetDateTime` parseado viaja al INSERT TAL CUAL (sin conversión:
       PG normaliza a UTC al guardar — TRD §2.6).
    3. `level`: null / no textual / fuera de
       `NIVELES = Set.of("TRACE","DEBUG","INFO","WARN","ERROR","FATAL")`
       case-sensitive → 400 `level_invalid` / `level` (S5-6).
    4. `message`: null / `isBlank()` → 400 `message_required` / `message`.
    5. `message`: `getBytes(UTF_8).length` > 32768 → 400 `field_too_long`
       / `message`, mensaje compuesto "…32768 bytes" (R5.10, patrón C3
       R3.13; una sola obtención de bytes, S5-8 — el String sigue a la
       fila, el array de medida se descarta).
    6. `exception` opcional: > 131072 bytes → `field_too_long` /
       `exception`.
    7. `metadata` opcional: `JsonNode` presente y `!isObject()` (array o
       escalar) → 400 `metadata_not_object` / `metadata`; si objeto → UNA
       `objectMapper.writeValueAsString(...)` → String; si
       `getBytes(UTF_8).length` > 65536 → 400 `field_too_long` /
       `metadata`. `{}` es VÁLIDO (E5.8). EL String serializado alimenta
       tanto la medida COMO el parámetro `CAST(? AS jsonb)` de la fila
       (UNA serialización, R5.8/S5-8).
    8. `source` opcional: `length()` > 200 caracteres → `field_too_long`
       / `source` (mensaje en "caracteres").
    9. `correlation_id` opcional: > 200 caracteres → `field_too_long` /
       `correlation_id`.

   En el pass completo, cada registro deja su `LogValidado` (fila 5.ª/6.ª/7.ª
   ya midieron sin segundo `getBytes`; `metadata` ya serializado).

2. **`OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC)`** —
UNA llamada (cierre S5-7): tras TODA la validación, inmediatamente ANTES
del insert; común a todo el lote; es parámetro (no `now()` SQL); en un 400
no llegó a calcularse y en cualquier caso nada se persiste (E5.19: el mock
del repo captura el valor único).

3. **`List<Long> ids = logsRepository.insert(projectId, filas, receivedAt)`**
(§9) — `@Transactional` del repo; UNA sentencia = UNA transacción = COMMIT
al retornar (regla 6/ADR-11/AE-9).

4. **Seam tras-COMMIT ESTRUCTURAL** (fuera de cualquier scope
transaccional — la sentencia siguiente del método): `oyentes.forEach(o ->
o.onLotePersistido(projectId, receivedAt, ids))` — args EXACTOS (mismo
`receivedAt` persistido, ids en orden de VALUES). TAMBIÉN en ingesta
individual (lista de 1 — frame batch de C7 = batch de 1, Schema §3.5).
JAMÁS ante 401/403/400/parse/rollback: todos los returns anteriores la
dejan estructuralmente detrás del COMMIT (E5.20, R5.13, D-12).

5. **`return Optional.empty()`** → el controller emite la 202 `{"accepted":
N}` (N = `registros.size()`; individual siempre 1).

- **Sin captura de `DataIntegrityViolationException`/`DuplicateKeyException`**
(R5.12-b): en `logs` no hay UNIQUE fuera de la PK; fallo real teórico (FK de
`project_id` en carrera) → **500 honesto** (política S6 de C4); el rollback
de la transacción (por el `@Transactional` del repo, D-4) cubre el resto.
El `try/catch` del guard NUNCA ve esta excepción (nació dentro del chain
tras autocompletar la auth) — y si el insert falla, el seam NO se invocó
(sentencia 4 nunca alcanzada).

**Threading / stateless:** el service es stateless (sus únicos campos son
dependencias `final` y las constantes `static final`); `ENTORNOS`/`NIVELES`
son `Set.of` inmutables; el `ObjectMapper` bean es thread-safe (contrato
Jackson); ninguna variable compartida mutable; el `receivedAt` vive solo en
el stack de la invocación. Nada de `SecureRandom` en ingest (nada genera
keys — eso es `keys.newKey()`, intocable).

## 9. SQL y repos (R5.11, R5.11-a/b/c/d)

### 9.1 `ProjectsRepository` — ADITIVO (R5.11-a)

`backend/src/main/java/com/projectlog/projects/repository/ProjectsRepository.java`:
+1 constant (`private static final String SELECT_POR_API_KEY_HASH = "SELECT " + COLS + " FROM projects WHERE api_key_hash = ?"`) + 1 método con el
Javadoc EXACTO de R5.11 (cita del ancla C4 spec §7 y de D-C5-1 — R5.16).
Firma EXACTA: `@Transactional public Optional<ProjectDto>
findByApiKeyHash(byte[] hash)` — `jdbcTemplate.query(SELECT_POR_API_KEY_HASH,
FILA, hash)`; `isEmpty() ? Optional.empty() : Optional.of(get(0))`. `NUNCA
queryForObject` (la fila-0 es el caso legal del 401 — patrón C4). El
binding `byte[]` → varargs → driver PG `setBytes` → **bytea** (el mismo
mecanismo probado en ESCRITURA por C4 E4.19/E4.20; la LECTURA R-2 queda E2E
con E5.21/E5.24). `FILA` reutilizada SIN cambios (CD-3.2: una sola fuente de
columnas/mapeo; formatear 2 fechas extra en el path de ingesta es coste
ínfimo aceptado por el propio spec R5.11-a). El `SELECT` completo va GRATIS
(el 403 necesita `status`, el servicio el `project_id` — Punto 1); las filas
con hash NULL (revocadas) quedan excluidas por el `WHERE` (regla 11). Pins
de `ProjectsKeySqlTest` de C3/C4: SIN diff en sus estamentos (R5.19a).

### 9.2 `LogsRepository` — NUEVO (R5.11-b/c/d, D-4)

`@Repository` en `com.projectlog.ingest`, ÚNICA fuente SQL de INSERT sobre
`logs` (C6 creará sus lecturas en su corte). 3 constants `static final`
EXACTAS (R5.11-b literales):

```java
static final String INSERT_LOGS_CABECERA =
        "INSERT INTO logs (project_id, level, environment, timestamp,"
                + " received_at, message, exception, metadata, source,"
                + " correlation_id) VALUES ";
static final String INSERT_LOGS_FILA =
        "(?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)";
static final String INSERT_LOGS_RETORNO = " RETURNING id";
```

El estamento se construye por invocación: `CABECERA` + `FILA` N veces
separadas por `", "` + `RETORNO` (individual = MISMO estamento con N=1).
El `CAST(? AS jsonb)` en la 8.ª posición es OBLIGATORIO (R-4: un String →
jsonb sin cast falla); el `RETORNO` existe desde C5 aunque la 202 no
exponga ids (moneda del frame batch de C7, propuesta §2.3).

```java
@Transactional   // defensa documentada: UNA sentencia = UNA transacción (D-4, R5.11-c)
public List<Long> insert(long projectId, List<LogValidado> filas,
        OffsetDateTime receivedAt)
```

- Construcción del SQL: `StringBuilder` (un bucle append de
`INSERT_LOGS_FILA` con `", "` entre iteraciones) — patrón C3 de `conjuntoSet`
(sin SQL dinámico por filtros: el número de filas es el único parámetro
estructural y NO proviene del cliente — el `limiteLote` del controller
garantiza N ≤ 200).
- Parámetros (varargs en el orden EXACTO de columnas / de `?` por fila):
`projectId, level, environment, timestamp (OffsetDateTime), receivedAt
(OffsetDateTime), message, exception, metadataJson (String → CAST), source,
correlation_id`. Los opcionales nulos van como parámetro `null` en los
varargs (el driver lo infiere — patrón C3 del `description`). `timestamp`/
`received_at` como `OffsetDateTime` → driver los manda como `timestamptz` y
PG normaliza a UTC (TRD §2.6). La `metadata` viaja como el MISMO String que
se midió (S5-8).
- Extractor de fila: `(rs, numeroFila) -> rs.getLong("id")` → `List<Long>` en
ORDEN de VALUES (ids ASC ↔ orden del array — E5.22 con ROW_NUMBER).
- Rollback exacto (D-4): si el INSERT lanza (única causa real: FK de
`project_id` — 500 honesto), el proxy transaccional del repo hace rollback
de su ÚNICA sentencia; no hay estado previo que revertir (la validación ya
salió; ninguna fila se persistió). El service NO anida transacciones ni
declara propagaciones.

## 10. Seam `IngestListener` (R5.13, S5-10, Punto 5 del gate)

```java
package com.projectlog.ingest;

/** Seam C7 (frame batch): invocado EXCLUSIVAMENTE tras el COMMIT del
 *  lote persistido (estructura: la única @Transactional del corte está en
 *  LogsRepository.insert; este hook es la sentencia siguiente de un
 *  método de servicio NO transaccional — D-4). JAMÁS invocado ante
 *  401/403/400/parse ni rollback. C5 NO implementa streaming (sin frames,
 *  sin handler, sin LiveBroadcaster). C5 no captura excepciones del
 *  suscriptor: la fila ya está en COMMIT — at-least-once (D-12). */
public interface IngestListener {
    void onLotePersistido(long projectId, OffsetDateTime receivedAt,
            List<Long> ids);
}
```

Suscripción: `List<IngestListener>` por constructor del service — lista
VACÍA en C5 (cero beans suscriptores; no-op verificable por E5.20).
Sin `TransactionSynchronizationManager` ni `@TransactionalEventListener`
(E3 del explore, descartado en gate — S5-10 verbatim).

## 11. Propiedades y application.yml (R5.14, S5-9)

`backend/src/main/resources/application.yml` — ÚNICO diff (aditivo + 1
línea de comentario):

```yaml
projectlog:
  security:
    jwt-ttl-hours: ${JWT_TTL_HOURS:8}
    frontend-origin: ${FRONTEND_ORIGIN:}
  ingest:                                     # NUEVO (C5)
    max-request-bytes: ${INGEST_MAX_REQUEST_BYTES:1MiB}   # default literal TRD §6.3
    max-batch-size:    ${INGEST_MAX_BATCH_SIZE:200}       # default literal TRD §6.3
```

- El comentario de cabecera "sin límites de ingesta (C5)" se corrige
(NADA de semántica — R5.3.3).
- `.env.example` INTOCABLE (literal del TRD §6.3 — el normalizador D-6
absorbe el sufijo `iB`); `compose.yaml` ya interpola las 2 env (grep
verificado en `compose.yaml:39-40`) — NADA que tocar; `application-test.yml`
INTOCABLE (los [L] corren con los defaults — E5.34 pina su default).
- Consumidores: el `long` del filtro (D-6) y el `int` del controller (§6) —
ambos convertidos UNA VEZ en su constructor, fail-fast de arranque.

## 12. Errores — mapa completo de los 11 codes (R5.5/R5.9/R5.10, S5-13)

NINGÚN code nuevo (R5.19g: los 11 son del enum §3.4/§4.1; `field_too_long`
se REUTILIZA). Cuerpo estándar `{code, message, field?}` + `index` solo en
batch (S5-3); JSON sin espacios (`Map.of`/`LinkedHashMap` +
`valueToTree`).

| HTTP | `code` | `message` (EXACTO) | `field` | `index` | Escritor (constants) |
|---|---|---|---|---|---|
| 401 | `api_key_invalid` | `API key de ingesta ausente o inválida` | — | — | `IngestAuthenticationEntryPoint` (S5-11; defecto-401 del controller reusa la constant, D-2) |
| 403 | `project_archived` | `El proyecto está archivado: no acepta logs` | — | — | `IngestController` |
| 413 | `payload_too_large` | `La petición supera el límite de 1 MiB` (FIJO — no compone el valor configurado) | — | — | `IngestGuardFilter` |
| 400 | `environment_required` | `El campo environment es obligatorio (development, staging o production)` | `environment` (no en batch de lote-vacío) | (batch: sí) | `IngestService` (constants) / controller materializa |
| 400 | `timestamp_without_timezone` | `El campo timestamp debe ser ISO-8601 con zona horaria` | `timestamp` | (batch) | idem |
| 400 | `level_invalid` | `El campo level no es un nivel válido (TRACE, DEBUG, INFO, WARN, ERROR o FATAL)` | `level` | (batch) | idem |
| 400 | `message_required` | `El campo message es obligatorio` | `message` | (batch) | idem |
| 400 | `field_too_long` | `El campo <f> no puede superar <límite> bytes` (message/exception/metadata) o `… <límite> caracteres` (source/correlation_id) — compuesto, límites 32768/131072/65536/200 | `<f>` (message/exception/metadata/source/correlation_id) | (batch) | idem |
| 400 | `metadata_not_object` | `El campo metadata debe ser un objeto JSON` | `metadata` | (batch) | idem |
| 400 | `batch_empty` | `El campo logs debe contener al menos 1 registro` | `logs` | — | `IngestController` (gate de lote; también el 400 de ilegibilidad en batch, D-10) |
| 400 | `batch_too_large` | `El campo logs no puede contener más de <n> registros` (compuesto con el límite efectivo) | `logs` | — | `IngestController` |

Orden de orden de claves del body de error (`LinkedHashMap`): `code`,
`message`, `field` (si aplica), `index` tras `field` (R5.9). El 202:
`{"accepted":N}` (Map de 1 clave — R5.10-a).

## 13. Tests [L] — ficheros NUEVOS (R5.17, R5.1: sin tocar un test de C1–C4)

| Fichero test (NUEVO) | Contenido / patrón | Escenarios |
|---|---|---|
| `ingest/IngestControllerMvcTest.java` | `@ActiveProfiles("test")` + `@WebMvcTest(IngestController.class)` + `@Import({SecurityConfig.class, IngestGuardFilter.class, IngestAuthenticationEntryPoint.class})` + `@MockitoBean ProjectsRepository, ApiKeyService, LogsRepository` + `@Import(IngestService.class)` (service REAL con repo de logs mock) | E5.1–E5.14, E5.24-c, E5.16 (parte MVC), E5.12 (3 variantes del 401 con `verifyNoInteractions` del service y de `LogsRepository`) |
| `ingest/IngestLogHygieneTest.java` | Patrón `LogHygieneTest` de C2 / `PanelAuthMvcTest.hygieneDeLogs…` (`ListAppender` sobre el logger `com.projectlog` a DEBUG): un 202 y un 401 con key real de test — NINGÚN evento contiene la key, un fragmento, el header `X-API-Key` ni el hash (R5.15) | E5.17 |
| `ingest/IngestServiceTest.java` | Unit puro (Mockito, sin Spring): repo mock + `List<IngestListener>` de un mock; `ArgumentCaptor` del `insert` → UNA invocación, UN `receivedAt` común, ids del mock; `verifyNoInteractions` del listener ante error/rollback; parse de timestamp | E5.18 (con D-11), E5.19, E5.20 |
| `ingest/IngestGuardLimitTest.java` | Unit del normalizador D-6 SIN Spring: `"1MiB"`→1048576, `"1KB"`/`"1KiB"`→1024, `"1048576"`→1048576, `"1MB"`→1048576 (equivalencia binaria de Spring), no parseable → excepción; el default del `@Value` es EXACTAMENTE `"1MiB"` (assert sobre la anotación vía reflection o pin del literal en el código del filtro — sin duplicación frágil) | E5.34 |
| `ingest/LogsRepoSqlTest.java` | Pin de los 3 constants del INSERT (patrón `ProjectsKeySqlTest`, JUnit puro sin Spring/BD) | E5.15 (INSERT) |
| `projects/repository/ProjectsIngestSqlTest.java` | Pin de `SELECT_POR_API_KEY_HASH` = `"SELECT " + COLS + " FROM projects WHERE api_key_hash = ?"` (packages-private accesible SOLO en el mismo paquete — D-9) | E5.15 (SELECT) |

Pin de `SecurityConfig` (cadenas 2/3 byte-a-byte, E5.16-b): SIN test nuevo —
RE-CORRIDA de `PanelAuthMvcTest` + suite C1–C4 completa (77/77) en imagen
builder (R5.19b, R5.16). Smoke [S]: `scripts/qa_c5_smoke.sh` NUEVO (patrón
`qa_c4_smoke.sh`: recomposición ÚNICA `--force-recreate`, `psql -qAt`,
tmpdir 700 para key/JWT, conteos RELATIVOS, sufijos por ejecución,
needle `plk_` con escape `\_`/`position()`, `curl_ingest` helper) —
codifica E5.21–E5.33 exactos al guion del spec §2.5 (salida `E5.n: PASS |
FAIL | BLOQUEADO`, exit 0/1/2).

## 14. Divergencias con spec.md

1. **D-7 (colocación de constants de los 400 de validación).** R5.10/S5-13
asignan TODOS los constants "403/400" a `IngestController`, pero la
VALIDACIÓN vive en `IngestService` (R5.12) y el gate de lote en el
controller (R5.24-c): si el service no puede ver los constants del
controller sin una referencia paquete-privada inversa
(service → controller), la dependencia queda al revés de la
arquitectura de capas. Resolución del design: constants de los 400 por
campo en `IngestService`, los de `batch_empty`/`batch_too_large`/
`project_archived` en `IngestController` (propietario de cada gate). Los
11 literales son BYTE-A-BYTE los de R5.10 (nada de contrato cambia):
**se pide a spec/tasks ratificar la colocación** (o, si prefiere el
design literal de R5.10, el service referenciaría
`IngestController.CODE_*` — legal pero inverso).
2. Ninguna otra: el resto del spec es implementable literalmente — D-C5-1
(firma B1), S5-1…S5-15, los literales de R5.6/R5.9/R5.10/R5.11 y el
guion §2.5 se toman tal cual (el design NO renegocia ninguna decisão de
gate).
3. **Añadido por el apply, RATIFICADO en gate 2026-10-25 (ver §2.1
D-13/D-14/D-15)**: bean-higiene `filtroGuardSoloCadena1` en SecurityConfig
(fuera del alcance literal del diff), solución `ObjectProvider` lazy del
bloqueo T5.5 (sin tocar asserts de C1–C4) y los 3 catches
desenvolvedores de `FrameworkServlet` en el guard (hallazgo E2E E5.28,
D-5 se mantiene RuntimeException).

## 15. Riesgos del diseño (D-Rx — los heredados del spec + los propios)

| ID | Riesgo | Mitigación (dónde vive) |
|---|---|---|
| D-R1 (NUEVO) | La excepción de la barrera 2 cruza JACKSON/DispatcherServlet: si fuera `IOException` Jackson la envolvería en `HttpMessageNotReadableException` → el handler del controller respondería **400** degradando el 413 | D-5: clase `RuntimeException` anidada; el catch del guard es el ÚNICO capturador (especificidad de tipo, jamás `catch (RuntimeException)`); E5.28 lo certifica chunked y E5.33 el orden total |
| D-R2 (NUEVO) | Doble registro del guard (bean `@Component` = auto-registro Boot global + cadena 1): fugas a panel/ws (413 sobre cuerpos grandes ajenos; lectura de `X-API-Key` fuera de ingesta) | D-8: `shouldNotFilter` acota el filtro a `/api/v1/ingest(/**)` — la
super-superficie del matcher de la cadena 1, S5-12(a); `OncePerRequestFilter` evita duplicidad; y el `JwtAuthFilter` preexistente no se toca (benigno) |
| D-R3 (NUEVO) | `metadata` serializado dos veces (medida + insert) divergiendo, o medido en unidades distintas (bytes ≠ chars) | S5-8 EXACTO: UNA `writeValueAsString` cuyo String alimenta la medida (`getBytes(UTF_8).length`) Y el parámetro del INSERT; `LogValidado.metadataJson` es la moneda única (no hay segunda serialización); fronteras de BYTES en E5.7/E5.9/E5.30 |
| D-R4 | `received_at` no común (R-5) o distinto del valor del seam | D-4 + cierre S5-7: UNA `OffsetDateTime.now(UTC)` pasada como parámetro al repo y al seam — E5.19/E5.22 (igualdad EXACTA entre las N filas) |
| D-R5 | All-or-nothing perforado: validación parcial + escritura parcial (R heredado spec: "all-or-nothing con la transacción — dónde pone el rollback") | D-4: la ÚNICA transacción es el INSERT multi-VALUES del repo (rollback del proxy al lanzar); la validación corre PRE-transacción (R5.24) — nada de mitades estructuralmente posible; E5.23 con conteo psql relativo antes/después |
| D-R6 | R-12 heredado (spec §2.6): el cliente ve "connection reset" en vez del 413 (cuerpo no drenado) | plan B documentado (drenar ACOTADO hasta `limite+1`) activado por E5.27 solo si curl lo manifiesta; cuerpo y `code` del 413 idénticos — sin cambio de contrato |
| D-R7 | R-2 heredado: bytea en la LECTURA nunca probado E2E | `setBytes` del driver (native bytea) + E5.21 (match real → 202) y E5.24 (falsificada → 401) lo cierran en pila; la D-C5-1 ya declara que la igualdad la resuelve el `WHERE` |
| D-R8 | R-3 heredado: contaminación del panel (`invalid_token` → `api_key_invalid`) | entry point nuevo SOLO en `ingestChain`; constructor/panelChain/defaultChain byte-a-byte; pin ROJO-INSTANTÁNEO: `PanelAuthMvcTest` espera `invalid_token` en 5 tests (§3); re-corrida 77/77 |
| D-R9 | Bind R-6 heredado: sufijo `1MiB` no parseable por `DataSize` directo | D-6/S5-9: normalizador (+prueba unitaria E5.34) con la evidencia local spring-core 6.2.19; `.env.example` intocable; fail-fast de arranque si el literal no casa |
| D-R10 | R-9/R-13/R-14 heredados: orden no determinista de 400 / 413 vs 400 de lote / cuerpos ilegibles | orden R5.24 fijo con primer-fallo-gana (§8 paso 1); gate de lote sobre `JsonNode` ANTES de parsear elementos (S5-8/R5.24-c); 413 NUNCA se re-especializa en 400 (R-13, tabla §6 pasos 1-2); ilegible → misma puerta que `{}` (S5-1, D-10) — E5.14/E5.11/E5.23/E5.33 y E5.24-c los cierran |

## 16. Trazabilidad design → spec

| Elemento de design | Cubre (spec C5) |
|---|---|
| §1 mapa + R5.1, piezas anidadas (D-5, D-7) | R5.1, S5-15 |
| D-1/D-2/D-8, §4 filtro (barrera 1 + envoltorio + auth + catch) | R5.4, S5-12, S5-9, D-C5-1 (Javadoc), R5.16 |
| §5 entry point | R5.5, S5-11, S5-13 (literales 401) |
| §3 SecurityConfig (bean params, sin cors, `authenticated`) | R5.3.1, R5.6, R5.6-a, R5.19(b)(c)(d) |
| §6 controller (gates 3 y de lote, ilegibilidad, 202, helper error) | R5.7, R5.8, R5.9, R5.10, R5.10-a, S5-1, S5-3, R5.24-c, S5-12(c)(e) |
| §7 `LogIngestRequest`/`LogValidado` | R5.24-b, R5.24-a, S5-1, S5-2, S5-15 |
| §8 `IngestService` (orden de checks, received_at, unidades, seam) | R5.12, R5.12-a/b, R5.24, S5-3…S5-8, R5.13 |
| §9 repos (`findByApiKeyHash`, `LogsRepository`) | R5.11, R5.11-a/b/c/d, R5.3.2, R5.19(a) |
| §10 `IngestListener` | R5.13, S5-10, gate Punto 5 |
| §11 propiedades | R5.14, S5-9, R5.3.3 |
| §12 mapa de errores | R5.5/R5.9/R5.10, S5-13, R5.19(g) |
| §13 tests | R5.17, R5.18, E5.1–E5.34, R5.16 (G-1/G-2), R5.19 |
| §15 riesgos | R-12/R-14/R-2 del spec + D-R1/D-R2/D-R3/D-R5 propios |

## 17. Fuera de alcance (heredado del spec §4 — el design NO lo expande)

Streaming real (C7 — solo el seam, §10), consulta (C6), frontend (C8),
migraciones, panel/cadenas 2–3 (byte-a-byte), restos de entry point del
panel, G-1 (NO reabrible — el Javadoc de C5 lo cita, no lo toca), G-2
(cierre ACTIVO en el smoke, E5.31), 429 (ADR-26), retención, borrados,
summary, Content-Type validation (S5-12c), 404 en ingesta, oráculo de causa
del 401, normalización case, generación server-side de `correlation_id`.