---
change: "C5"
artifact: spec
last_updated: 2026-10-25
status: draft
---

# Spec — C5 · Ingesta REST

> Especificación normativa del corte C5 para la implementación (sdd-design →
> tasks → apply → qa → verify). Fuente: `proposal.md` C5 (status **approved**
> — GATE aprobado por el usuario 2026-10-25, 6/6 puntos tal cual; el
> all-or-nothing del batch se aclaró al usuario y se aceptó: "perfecto, me
> parece bien") y anclas de `explore.md` C5. Los literales de API/BD/errores
> se **citan** del Backend Schema (`docs/05_BACKEND_SCHEMA.md`, valido v1.0),
> del TRD (`docs/02_TRD.md`, valido v1.1), del AppFlow (`docs/03_APPFLOW.md`,
> valido v1.0), del PRD (`docs/01_PRD.md`, valido v1.2) y de la ficha C5 del
> `07_SLICING_PLAN.md` — nunca se redefinen (regla del 07). Lo heredado de
> C1–C4 queda intocable donde se indica. Código verificado en HEAD 6cc609f
> (2026-10-25).

## 0. Decisiones de gate ya aprobadas (resumen vinculante)

| Gate | Decisión aprobada (2026-10-25) |
|---|---|
| Punto 1 (DE GATE) | **B1**: firma EXACTA del ancla C4 spec §7 — `Optional<ProjectDto> findByApiKeyHash(byte[] hash)` con `SELECT <COLS> … WHERE api_key_hash = ?`. La igualdad la resuelve el `WHERE` por igualdad sobre el índice parcial único; la comparación extra `tiempoConstanteIgual` post-lookup NO cabe (el `ProjectDto` no porta el hash) y se declara como divergencia **D-C5-1** formal (§1.F). Sin DTO estrecho, sin re-hasheo, sin migración |
| Punto 2 (de paquete) | **UNIDADES**: bytes UTF-8 para `message` (32768) / `exception` (131072) / `metadata` (65536 bytes del JSON serializado); **caracteres** (`String.length()`) para `source` y `correlation_id` (200). **413 con doble barrera**: `Content-Length` primera (sin leer el cuerpo) + stream de cuerpo limitado a `max+1` bytes segunda (chunked incluido) |
| Punto 3 (de paquete) | **G-1 (QP-30 de C4): NO se reabre** — queda cerrado como hueco aceptado del archive de C4 (S-1); C5 no toca `regenerateKey` ni le asigna escenario. **G-2: cierre ACTIVO** en el smoke de C5 — E5.31 (desarchivar → la MISMA key → 202) corrobora las reglas 11 y 12 juntas; el archive de C5 lo citará |
| Punto 4 (de paquete) | **Sin Testcontainers en C5** (reevaluar en C6/C7 solo si hace falta): el patrón `[L]` MVC-mocks + smoke `[S]` contra la pila real certifica bytea/JSONB/TIMESTAMPTZ/all-or-nothing con conteos psql y `--force-recreate` |
| Punto 5 (DE GATE) | **Seam C7 EN C5**: interfaz `IngestListener` con el evento de lote persistido, invocada tras COMMIT, SIN suscriptores en C5 (lista inyectada vacía); sin frames ni handler ni `LiveBroadcaster` |
| Punto 6 (de paquete) | Literales de `message` de 401/403/413 de INGESTA **ratificados tal cual** (los 3 del §0.2/S5-13 abajo); cuerpo estándar `{code, message, field?}` (TRD §2.6); sin varianza por causa (anti-oráculo). El `message` de los 400 lo fija este spec (los docs fijan `code` y `field`) |

## 0.2 Decisiones de SPEC (cierres de los huecos del §8 de la proposal)

Cierres que la proposal remitió a este spec (§8.1–§8.13). Son VINCULANTES
para design/tasks.

| # | Hueco | Cierre del spec | Justificación |
|---|---|---|---|
| S5-1 | §8.8 — cuerpo no-JSON / tipos imposibles (R-14) | Los 2 endpoints leen el cuerpo como **`@RequestBody JsonNode`** y lo extraen con la factoría `LogIngestRequest.desdeJson(JsonNode)` — patrón LITERAL del PATCH de C3 (`UpdateProjectRequest.desdeJson`, backend/src/main/java/com/projectlog/projects/dto/UpdateProjectRequest.java). Un cuerpo no parseable (no-JSON, vacío) → `HttpMessageNotReadableException` → **@ExceptionHandler del IngestController → 400 con el code del primer requerido de cada endpoint**: individual → `environment_required` (`field: "environment"`); batch → `batch_empty` (field "logs"). TAMBIÉN se mapea `HttpMediaTypeNotSupportedException` (Content-Type sin converter → sería 415, fuera del contrato cerrado de §3.1) al MISMO 400 de ilegibilidad | Cierre S5-2 de C3 ("NO se inventa `body_invalid`" / CD-3.9: sin codes sin ancla). Un cuerpo ilegible en individual responde exactamente lo mismo que un cuerpo `{}` (la primera exigencia — environment — no llega), y en batch lo mismo que un lote vacío: mismo code, sin oráculo nuevo, sin 415 fuera de contrato |
| S5-2 | §8.8 — campos desconocidos del JSON | **IGNORADOS por construcción**: con la lectura `JsonNode` solo se accede a los 8 nombres literales del Schema §3.4; cualquier clave extra (incl. un `project_id`/`received_at` falsificado por el cliente) JAMÁS se lee ni persiste (patrón CD-3.9 de C3: "cualquier otra se IGNORA — sin rechazar campos desconocidos: exigiría un literal sin ancla"). E5.10-fam lo pincha con `project_id` falso | Anti-oráculo (no hay code de "campo desconocido" en el enum §3.4) + campos de confianza falsificables jamás llegan a la BD (Schema §4 regla 5) |
| S5-3 | index del 400 de batch | El 400 de un registro del lote lleva **`index` como entero 0-based** (posición en el JSON array, `Integer`) ADEMÁS de `field`, SOLO en batch — los 400 del endpoint individual JAMÁS llevan `index` (aunque su valor sería 0). Cita: Schema §4 regla 6 ("400 con `index`/`field` del culpable") y §3.4 ("Batch vacío o > 200 → 400 … con `field: "logs"`) | "posición en el array" del regla 6 = índice del array JSON tal cual (0-based, como indexan JS/Java). En individual no hay posición que reportar (el cuerpo no es un array) |
| S5-4 | `environment` ausente/vacío/inválido | UN SOLO code para las 4 variantes: ausente, presente no textual, cadena vacía (vacía o solo blancos) y valor fuera del enum → **400 `environment_required`, `field: "environment"`**. SIN normalización case (patrón C3: "Minúsculas no se normalizan (CHECK literal)") | El Schema no fija un code para valor inválido (solo define `environment_required` para "falta", regla 7) y NO hay `environment_invalid` en el enum §3.4: un valor que no satisface la exigencia del enum cerrado (regla 10 + ADR-25) incumple TAMBIÉN el "obligatorio bien formado" — un único code evita inventar + anti-oráculo |
| S5-5 | `timestamp` ausente/malformado/naive | UN SOLO code: ausente, presente no textual, no parseable (malformado) y parseable SIN offset (naive) → **400 `timestamp_without_timezone`, `field: "timestamp"`**. Parse = `OffsetDateTime.parse(valor)` (ISO_OFFSET_DATE_TIME por defecto del JDK: acepta `Z` y `±hh:mm`, RECHAZA el naive — verificado: `OffsetDateTime.parse("2026-10-16T22:31:04")` lanza `DateTimeParseException`). El `OffsetDateTime` parseado viaja al INSERT TAL CUAL (sin conversión extra): PG lo normaliza a UTC al guardar (`TIMESTAMPTZ`; TRD §2.6: "siempre se normaliza a UTC al guardar") | El Schema solo define este code de timestamp (§3.4, campo "timestamp": "ISO-8601 con offset; sin offset → 400"); sin 400 genérico "required" anclado. La normalización es de la BD — el humo la certifica leyendo UTC en psql (E5.29) |
| S5-6 | `level` ausente/inválido | UN SOLO code: ausente, no textual o fuera de los 6 literales del enum → **400 `level_invalid`, `field: "level"`**. Enum `TRACE/DEBUG/INFO/WARN/ERROR/FATAL` en `Set` de constantes, **case-sensitive EXACTO** (un `"error"` minúscula → `level_invalid`, sin normalización silenciosa: el CHECK de V1 solo acepta el literal exacto y normalizar cambiaría el valor persistido) | Enum cerrado del Schema §3.4/§4.1 regla 10; sin code "level_missing" separado (§3.4 solo define `level_invalid`) |
| S5-7 | received_at: ¿antes o tras validar? | Se calcula **UNA VEZ en Java, TRAS pasar TODA la validación, inmediatamente ANTES del INSERT**: `OffsetDateTime.now(ZoneOffset.UTC)` de UNA sola llamada, común a todo el lote, y se pasa al repo como parámetro (parámetro, no `now()` SQL — literal regla 9: "fijado en el momento de la transacción"). En un 400 el valor no llega a calcularse — y en cualquier caso NADA se persiste (validación completa antes de tocar la BD, regla 6) | Regla 9 del Schema §4 citada verbatim ("Todos los logs de un mismo batch comparten un mismo `received_at` (fijado en el momento de la transacción)"); recomputar tras validar también cumple la regla, y es el punto EXPLÍCITO del programa (el valor existe un momento antes de insertar). Determinismo de test: el mock del repo captura el valor único (E5.19) |
| S5-8 |Cómo se miden los tamaños en Java | **UNA sola obtención de bytes por campo**: el tamaño se mide con `String.getBytes(StandardCharsets.UTF_8).length` calculado una única vez por campo (el array de bytes reutilizado luego como parámetro si procede, sin segundo `getBytes`); para `metadata`, UNA única serialización con `objectMapper.writeValueAsString(...)` cuyo String alimenta la medida (`getBytes(UTF_8).length`, misma convención exacta que el conteo del 1 MiB) Y el parámetro `CAST(? AS jsonb)` del INSERT (R5.8). `source`/`correlation_id`: `String.length()` (caracteres, patrón C3). El límite del batch = `logs.size()` (nº de elementos del array JSON), comprobado en el borde del controller SOBRE el `JsonNode` (¡`.isArray()` + `.size()`) ANTES de parsear elemento alguno | Fiel al literal: KiB ⇒ bytes UTF-8 (Schema §3.4/§4.1, TRD §2.7); "≤ 200 caracteres" explícito para source/correlation_id (Schema §2, tabla `logs`); sin coste doble; R-9 determinista |
| S5-9 | §8.9 — bind de `INGEST_MAX_REQUEST_BYTES=1MiB` (R-6) | Propiedad como **String + normalizador de sufijo + `DataSize.parse`**, NUNCA `DataSize` directo: **VERIFICADO contra spring-core 6.2.19 del `~/.m2` de este repo** — `DataSize.parse("1MiB")` lanza `IllegalArgumentException ('1MiB' is not a valid data size)`; `DataSize.parse("1MB")` → **1048576** (los KB/MB/GB de Spring SON binarios, 1024-base); `DataSize.parse("1048576")` → 1048576 (sin sufijo = bytes). Mecanismo: el sufijo `iB` se normaliza quitando la "i" (`1MiB`→`1MB`, `1KiB`→`1KB`, `1GiB`→`1GB`, `1TiB`→`1TB`; dígitos puros y `…B` quedan) y se resuelve con `DataSize.parse(...).toBytes()` UNA VEZ en el constructor del filtro (fail-fast al arrancar si el literal no casa). `@Value("${projectlog.ingest.max-request-bytes:1MiB}")` (default literal = el de `.env.example`/TRD §6.3, JAMÁS reescrito — el default 1MiB también hace funcionar los [L] con `application-test.yml` INTOCABLE). Test unitario del bind OBLIGATORIO: E5.34 | Evidencia local (no suposición): el R-6 decía "el soporte de MiB no está verificado" — verificado NEGATIVO. `.env.example` NO se toca (literal TRD §6.3); plan B (1048576 en `.env` real del usuario) descartado: innecesario con el normalizador (~4 líneas, primitiva de plataforma) |
| S5-10 | §8.4 — firma y punto del seam | Firma EXACTA: **`void onLotePersistido(long projectId, OffsetDateTime receivedAt, List<Long> ids)`** en la interfaz `com.projectlog.ingest.IngestListener` (método único). Suscripción: **lista inyectada vacía** — el `IngestService` recibe `List<IngestListener> oyentes` por constructor (Spring inyecta lista VACÍA si no hay beans: cero clientes en C5, no-op verificable; `Optional<IngestListener>` descartado: no modela "0..n"). Invocación: el service la hace **DESPUÉS de que el método transaccional del repo retorne** (fuera de CUALQUIER scope `@Transactional`: la única anotación del corte es en `LogsRepository.insert`, la llamada al seam es la sentencia siguiente en un método de servicio NO transaccional) — tras-COMMIT ESTRICTO de la estructura, sin `TransactionSynchronizationManager` ni `@TransactionalEventListener` (E3 del explore, descartado en gate: maquinaria mayor para un consumidor futuro). El seam se invoca TAMBIÉN en la ingesta individual (con `ids` de 1 elemento: el frame `batch` de C7 para un log individual es batch de 1 — Schema §3.5). JAMÁS se invoca ante 401/403/400 ni ante un INSERT que falle (la emisión queda estructuralmente detrás del COMMIT) | Punto 5 del gate aprobado; firma propuesta en la proposal ratificada tal cual; patrón E1; ADR-9 ("emisión solo tras COMMIT") y Schema §3.5 |
| S5-11 | §8.13 — nombre del entry point | **`IngestAuthenticationEntryPoint`** (el nombre de la proposal §7, confirmado): `@Component` en `com.projectlog.ingest`, `implements AuthenticationEntryPoint, AccessDeniedHandler` — réplica LITERAL del patrón `RestAuthenticationEntryPoint` (ObjectMapper + `Map.of("code", …, "message", …)`, SIN logger, con su constante `CODE_API_KEY_INVALID` package-private visible al resto del paquete `ingest`). Se registra como entry point Y accessDeniedHandler de la cadena 1 | Nombres finales los fija el spec (§8.13); el patrón C1/C2 es el probado (un solo escritor del 401 por superficie; nunca 403 HTML) |
| S5-12 | §8.10 — alcance del 413, Content-Type, respuesta | (a) El límite de 1 MiB aplica a **toda la sub-superficie `/api/v1/ingest/**`** (el matcher de la cadena 1 — defensa uniforme sin acoplarse a la lista de rutas; hoy solo existen los 2 mappings). (b) **NO se aplica a la respuesta** (el Schema lo fija sobre "Petición HTTP de ingesta"). (c) **NO se exige/valida Content-Type** (sin ancla en docs): un JSON con Content-Type ajeno caería en el 415 del conector — se MAPEA al 400 de ilegibilidad (S5-1) para no salir del contrato de códigos de §3.1. (d) Un `Content-Length` no numérico es mensaje HTTP roto: lo rechaza el conector (400 Tomcat) antes de la cadena — fuera del contrato. (e) Rutas ancladas SOLO POST: un GET/PUT ante `/api/v1/ingest` responde el 405 por defecto de Spring (método no anclado, fuera del contrato — no se inventa code) | Sin códigos sin ancla; el enum §3.4 solo cubre 400 de validación/parse + 401/403/413 (y §3.1 cierra el conjunto de códigos del API) |
| S5-13 | §8.6 — mensajes de los códigos de INGESTA | Tabla EXACTA en R5.5 (401/403/413 sin `field`; 400 con `field`, `index` en batch): constants `CODE_*`/`MSG_*` en la clase del componente escritor (401 en `IngestAuthenticationEntryPoint`, 413 en `IngestGuardFilter`, 403/400 en `IngestController`), patrón `CODE_NOT_FOUND`/`MSG_NOT_FOUND` de C3. Los 3 literales 401/403/413 son EXACTAMENTE los ratificados en el Punto 6 del gate. Los `message` de los 400 componen el límite (patrón C3 R3.13) | Envelope `{code, message, field?}` (TRD §2.6 verbatim); el `message` de ingesta NO estaba fijado en ningún doc (C4 spec §7) — aquí se cierra con el patrón C3 |
| S5-14 | §8.5 — cierres de huecos heredados | **G-1**: NO reabrible (sin escenario). **G-2**: cierre ACTIVO con E5.31 (ver gate Punto 3). Ambos se declaran en §2.4 y los cita el archive de C5 | Gate aprobado 2026-10-25 |
| S5-15 | ficheros de la proposal — recorte por la lectura JsonNode | Se CONFIRMAN la mayoría de los nombres de la proposal §7 y se **ELIMINAN 2 ficheros**: `IngestBatchRequest.java` (el wrapper `{logs}` se lee directo del `JsonNode`: `path("logs")` — array, tamaño y elementos) y `IngestAcceptedResponse.java` (la 202 se emite con `objectMapper.valueToTree(Map.of("accepted", n))` — 1 sola clave, patrón del entry point; JSON sin espacios). Nace 1 fichero nuevo de paso del spec: **`LogValidado.java`** (record de salida de la validación, §1.F) | Spec dueño del naming (§8.13); con `JsonNode` ya no hay binding tipado que necesite los wrappers — 2 clases menos, mismo contrato; la fila validada es la moneda única del INSERT (necesita `timestamp` ya parseado y `metadata` ya serializado) |

### D-C5-1 — Declaración FORMAL de la divergencia con el C4 spec §7 (aprobada en gate, Punto 1)

El C4 spec §7 (frontera, verbatim) documentó el uso del lookup como:
"`sha256(header)` (String completo, este mismo contrato) → lookup →
`tiempoConstanteIgual` como comparación final". Con la firma B1 aprobada
(`Optional<ProjectDto> findByApiKeyHash(byte[] hash)` + `SELECT <COLS>`), el
hash almacenado NO viaja (`ProjectDto`/`COLS` no contienen `api_key_hash`),
así que la comparación extra `tiempoConstanteIgual` post-lookup es
**estructuralmente imposible en este diseño** y NO se realiza. La
divergencia queda DECLARADA y CERRADA con esta ratificación del gate:

- La igualdad la garantiza el `WHERE api_key_hash = ?` del SELECT por
  igualdad (B-tree del índice parcial único `projects_api_key_hash_idx`,
  V1 líneas 54–55): comparación binaria completa de 32 bytes, exacta, sin
  timing-oracle observable útil (256 bits de entropía — ADR-4: "la key no es
  atacable por diccionario… hash rápido permite localizar el proyecto por
  índice en O(1)").
- `ApiKeyService.tiempoConstanteIgual` SIGUE existiendo (primitiva C4,
  R4.11, testeada) — C5 no la usa en el hot-path y no la elimina ni la
  modifica (`keys/**` intocable).
- El Javadoc del método del repo Y del filtro DEBE citar esta declaración
  (D-C5-1, gate 2026-10-25) para que la divergencia sea visible en review.
- Riesgo residual certificado por E2E: E5.21 (key real → 202) y E5.24
  (falsificada/revocada → 401).

## 1. Requisitos normativos (RFC 2119)

En este documento **MUST/DEBE**, **MUST NOT/NO DEBE**, **SHOULD** y **MAY**
se interpretan según RFC 2119. Cada requisito es verificable por al menos un
escenario de la sección 2.

### 1.A Estructura y dependencias

- **R5.1** — TODO el código nuevo del corte DEBE vivir en el paquete
  `com.projectlog.ingest` (package-by-feature; el `package-info.java` de C1
  ya declara: "REST individual y batch con X-API-Key, límites INGEST_MAX_*,
  413/403/400, all-or-nothing" — NO DEBE perder su rol marcador y NO DEBE
  tocarse). Piezas EXACTAS (nombres finales, cierra §8.13 de la proposal):
  `ingest/IngestGuardFilter.java`, `ingest/IngestAuthenticationEntryPoint.java`,
  `ingest/IngestPrincipal.java` (record), `ingest/IngestController.java`,
  `ingest/IngestService.java`, `ingest/LogsRepository.java`,
  `ingest/LogIngestRequest.java`, `ingest/LogValidado.java` (record, cierre
  S5-15) e `ingest/IngestListener.java` (seam C7). NO crean fichero propio
  `IngestBatchRequest` ni `IngestAcceptedResponse` (cierre S5-15).
- **R5.2** — Dependencias de features (rígidas): `ingest` → `keys`
  (`ApiKeyService.sha256`, primitiva existente SIN cambios — jamás un método
  nuevo ni un acoplamiento inverso) y `ingest` → `projects`
  (`ProjectsRepository.findByApiKeyHash`). `keys` y `projects` NO ganan
  dependencias hacia `ingest`. `streaming/**` INTOCABLE. **Cero cambios en
  `pom.xml`**: NO se añade `spring-boot-starter-validation` (la validación es
  MANUAL, patrón C3) ni Testcontainers (Punto 4 del gate) NI NINGUNA otra
  dependencia (ni commons-io: el stream limitado de la segunda barrera del
  413 es un decorador propio mínimo, S5-12/R5.4). **Cero migraciones**:
  `V1__init.sql` INTOCABLE — la tabla `logs` está completa (level/environment
  CHECK, `received_at`, `metadata JSONB`, `correlation_id`) y el índice
  parcial único `projects_api_key_hash_idx` ya existe de C1.
- **R5.3** — Ficheros de cortes anteriores TOCADOS en C5 (únicamente, SIEMPRE
  aditivos o acotados al cuerpo indicado):
  1. `config/SecurityConfig.java` — SOLO el método `ingestChain` (cuerpo +
     Javadoc del bean + Javadoc de clase actualizado a la evolución;
     §1.B/R5.6). El constructor Y los beans `panelChain`/`defaultChain`
     quedan BYTE-A-BYTE iguales (sin diff en sus líneas).
  2. `projects/repository/ProjectsRepository.java` — 1 método +
     1 constante de SQL + Javadoc, puramente aditivos (§1.D/R5.7); jamás
     toca una línea de C3/C4 (los pins de `ProjectsKeySqlTest` siguen).
  3. `application.yml` — +2 propiedades bajo `projectlog:` (§1.H/R5.12) y la
     actualización del comentario de cabecera que hoy dice "sin límites de
     ingesta (C5)" (el cambio lo vuelve falso: se corrige la línea de
     comentario, NADA de semántica).
  4. `scripts/qa_c5_smoke.sh` — NUEVO (patrón `qa_c4_smoke.sh`).
  Todo lo demás intocable (§1.L/R5.15).

### 1.B La cadena 1: guard, entry point y principal

- **R5.4** — `IngestGuardFilter` DEBE ser un `OncePerRequestFilter` `@Component`
  (patrón LITERAL `auth/JwtAuthFilter`: sin match útil → contexto VACÍO y
  `filterChain.doFilter`; sin loggers; jamás lee la respuesta; jamás loguea
  el header, la key, o el hash — higiene R5.14). Constructor: `ApiKeyService`
  + `ProjectsRepository` + `ObjectMapper` + el límite de bytes
  (`@Value("${projectlog.ingest.max-request-bytes:1MiB}") String`, convertido
  UNA VEZ a `long` con el normalizador del cierre S5-9). Orden interno
  OBLIGATORIO por petición (contrato de orden del Schema §3.4):

  1. **Barrera 1 — TAMAÑO (413)**: `request.getContentLengthLong()`; si es
     ≥ 0 y > límite → escribir el 413 (R5.5, constante `payload_too_large`)
     y RETORNAR sin tocar el cuerpo ni continuar la cadena. Si es −1
     (chunked/ausente) o ≤ límite → continuar.
  2. **Envoltorio del cuerpo (barrera 2 — 413 chunked)**: SIEMPRE se
     continúa con un `HttpServletRequestWrapper` cuyo `getInputStream()`
     expone un `ServletInputStream` decorador propio que cuenta bytes y, al
     cruzar `límite+1`, lanza la excepción runtime propia del paquete
     (`IngestCuerpoDemasiadoGrandeException` o equivalente — nombre final
     de design); el filtro DEBE capturarla alrededor de `chain.doFilter` y
     escribir el MISMO 413. Frontera EXACTA: un cuerpo de `límite` bytes
     (1048576) es LEGAL; `límite+1` → 413. (commons-io NO está en el
     classpath — pom verificado; por eso el decorador es propio.)
  3. **Autenticación (401)**: header `X-API-Key` → `apiKeyService.sha256(header)`
     (String COMPLETO: ni trim de `plk_`, ni hex, ni salt — contrato C4
     spec §0.2) → `projectsRepository.findByApiKeyHash(hash)`. Con match →
     `IngestPrincipal(projectId, status)` en el `SecurityContext` como
     `UsernamePasswordAuthenticationToken(principal, null, List.of())`
     (patrón JwtAuthFilter). Sin header / malformado / sin match / revocada
     → NO escribir respuesta y NO setear contexto: continuar; la regla
     `authenticated()` fallará y el entry point escribirá el 401.
  4. `chain.doFilter(wrapped, response)` (siempre con el envoltorio).

- **R5.5** — `IngestAuthenticationEntryPoint` DEBE: implementar
  `AuthenticationEntryPoint, AccessDeniedHandler` (réplica
  `RestAuthenticationEntryPoint`); `commence` → escribir
  `401 {"code": "api_key_invalid", "message": "API key de ingesta ausente o
  inválida"}` (Content-Type JSON, ObjectMapper, SIN `field`, SIN varianza por
  causa — anti-oráculo C2/S5); `handle` (access denied) → escribir el MISMO
  cuerpo `api_key_invalid` (patrón literal del `RestAuthenticationEntryPoint.handle`
  → `escribir401`), registrado como accessDeniedHandler de la cadena 1 para
  que JAMÁS exista un 403 HTML en la superficie. **El 403
  `project_archived` JAMÁS sale por aquí**: es del borde de ingesta con key
  VÁLIDA (R5.9, gate 3 del §1.C) — una `AccessDeniedException` en la cadena 1
  sería un defecto, no contrato. Sin logger.
- **R5.6** — `SecurityConfig.ingestChain` DEBE quedar EXACTAMENTE así (solo
  este método cambia; el constructor y las cadenas 2/3 byte-a-byte):

```java
/** Cadena 1 (C5): ingesta autenticada por X-API-Key — guard + entry
 * point PROPIOS (api_key_invalid, Schema §3.4); JAMÁS el entry point del
 * panel (invalid_token, CD-4, intocable). Sin cors() (TRD §5). */
@Bean
@Order(1)
public SecurityFilterChain ingestChain(HttpSecurity http,
        IngestGuardFilter ingestGuardFilter,
        IngestAuthenticationEntryPoint entryPointIngesta) throws Exception {
    return common(http)
            .securityMatcher("/api/v1/ingest/**")
            // Sin cors() (TRD §5: los clientes de ingesta son servicios).
            .addFilterBefore(ingestGuardFilter,
                    UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .exceptionHandling(eh -> eh
                    .authenticationEntryPoint(entryPointIngesta)
                    .accessDeniedHandler(entryPointIngesta)) // nunca 403 HTML
            .build();
}
```

  - Los 2 beans nuevos entran como **parámetros del método `@Bean`** (inyección
    de Spring) — el constructor de la clase NO cambia (diff mínimo sobre
    fichero cerrado C1–C4).
  - El ancla del `addFilterBefore` es **`UsernamePasswordAuthenticationFilter.class`**
    (el MISMO ancla literal del `panelChain` existente — patrón único de la
    casa; garantiza que el guard corre antes del filtro de autorización que
    evalúa `authenticated()`; respecto al `JwtAuthFilter` no hay relación de
    orden que gestionar: ese filtro SOLO está registrado en la cadena 2).
  - **NO `cors()`** (TRD §5 verbatim: "La API de ingesta sin CORS abierto:
    sus clientes son servicios"; `CorsConfig` intocable — su `allowedHeaders`
    sigue SIN `X-API-Key`, cierre S7 de C4).
  - **`denyAll()` TEMPORAL → `anyRequest().authenticated()`**; el 401
    `invalid_token` que la cadena respondía a todo (firma temporal del
    límite C4, verbatim en C4 spec §4/§7) queda **REEMPLAZADO** por el
    `api_key_invalid` (Schema §3.4) — la frontera temporal queda consumida.
- **R5.6-a** — `IngestPrincipal`: **record `(long projectId, String status)`**
  (análogo a `JwtPrincipal`; el `status` es el dato que el gate 3 necesita
  — B1 del gate Punto 1). NADA más en el principal (jamás el hash, jamás la
  key). El controller lo lee del `SecurityContext` (`getAuthentication().getPrincipal()`);
  si por defecto no estuviera (defecto estructural, nunca alcanzable con
  `authenticated()`): respuesta 401 `api_key_invalid` honesta (mismos
  constants del entry point, package-private en el mismo paquete).

### 1.C Endpoints, orden de gates y literales de respuesta EXACTOS

- **R5.7** — `IngestController` DEBE exponer EXACTAMENTE 2 mappings
  (clase con `@RequestMapping("/api/v1/ingest")`, patrón C3):

```java
@PostMapping                     // POST /api/v1/ingest — log individual
@PostMapping("/batch")           // POST /api/v1/ingest/batch — batch 1…200
```

  Ambos nacen bajo la cadena 1 evolucionada. Entrada: `@RequestBody JsonNode`
  (cierre S5-1). Los errores de parse → `@ExceptionHandler` del propio
  controller (S5-1).

- **R5.8** — ORDEN DE GATES del request (lista contractual, Schema §3.4
  verbatim: "tamaño (413, descartado antes de parsear) → key (401) →
  archivado (403) → validación (400). Todos **fail-fast antes de tocar la
  BBDD** (§4)"; R5.22 lo deja determinista con E5.33):

| Gate | Componente que responde | Condición → respuesta |
|---|---|---|
| 1. Tamaño | `IngestGuardFilter` (barrera 1 y 2) | cuerpo (Content-Length o stream) > 1048576 bytes → **413** `payload_too_large` — ANTES de leer/parsear el cuerpo |
| 2. Credencial | `IngestGuardFilter` (auth) + `IngestAuthenticationEntryPoint` (escritor) | header ausente / malformado / hash sin matchea / revocada → **401** `api_key_invalid` |
| 3. Estado del proyecto | `IngestController` (borde de ingesta, con key VÁLIDA) | `principal.status() == "ARCHIVED"` → **403** `project_archived` (ANTES de validar campos: un pedido con cuerpo inválido a proyecto archivado responde 403) |
| 4. Validación | `IngestService` (fail-fast, primer-fallo-gana) | primer campo invalido del primer registro culpable → **400** con `code`/`message`/`field` (+`index` en batch); SI TODO pasa → INSERT único → tras COMMIT → seam → **202** |

  Caso de borde documentado (cierre S5-1): con cuerpo NO parseable, el 400 de
  ilegibilidad antecede mecánicamente al gate 3 (Jackson parsea el cuerpo en
  la resolución del argumento, antes del código del controller) — el orden
  413→401 se conserva EXACTO (ambos en el filtro, previos a todo), y el orden
  403→400 se conserva EXACTO para cuerpos parseables (que es lo que evalúa la
  tabla "archivado → validación" del Schema). Sin ventana de orálogo nueva:
  la respuesta está determinista por (cuerpo, proyecto).
- **R5.9** — Literales de respuesta EXACTOS (JSON sin espacios; los 3
  primeros ratificados en el gate Punto 6 tal cual):

| Caso | HTTP | Cuerpo EXACTO | Escritor |
|---|---|---|---|
| Éxito individual | **202** | `{"accepted":1}` — la clave se emite con `objectMapper.valueToTree(Map.of("accepted", 1))`, SIN espacios y SIN más campos (ni ids ni received_at: no es contrato) | `IngestController` |
| Éxito batch | **202** | `{"accepted":200}` con N = nº de registros del lote (misma emisión, `Map.of("accepted", N)`) — 202 SOLO tras el COMMIT (ADR-11; regla 6) | `IngestController` |
| Key ausente/inválida/revocada | **401** | `{"code": "api_key_invalid", "message": "API key de ingesta ausente o inválida"}` — sin `field`, un ÚNICO cuerpo para las 3 causas | `IngestAuthenticationEntryPoint` |
| Proyecto archivado (key VÁLIDA) | **403** | `{"code": "project_archived", "message": "El proyecto está archivado: no acepta logs"}` — sin `field`; message DISTINCTO del panel (mismo `code`, otra superficie — Punto 6 del gate: el del panel es "…desarchívalo antes de generar o regenerar su API key") | `IngestController` |
| Petición > 1 MiB | **413** | `{"code": "payload_too_large", "message": "La petición supera el límite de 1 MiB"}` — sin `field`; literal FIJO (no compone el valor configurado) | `IngestGuardFilter` |
| Cuerpo no parseable / sin Content-Type mapeable | **400** | individual → `{"code": "environment_required", "message": "El campo environment es obligatorio (development, staging o production)", "field": "environment"}`; batch → `{"code": "batch_empty", "message": "El campo logs debe contener al menos 1 registro", "field": "logs"}` (cierre S5-1; sin codes nuevos) | `IngestController` |
| 400 de campo (individual) | **400** | el code correspondiente (§1.C/R5.11) con `field` — JAMÁS `index` | `IngestController` |
| 400 de registro de lote | **400** | el code correspondiente con `field` Y `index` (0-based, S5-3); el body lleva `index` en su posición tras `field` (patrón de error() con `LinkedHashMap`: `code`, `message`, `field`, `index`) | `IngestController` |

  Nota sobre la lectura del literal "único cuerpo `{"code": "project_archived"}`"
  (Schema §3.4): se ratifica la interpretación de la proposal (Punto 6) — un
  ÚNICO cuerpo SIN varianza por condición, en el envelope `{code, message,
  field?}` del TRD §2.6 citado por el mismo doc; C5 lo emite con su `message`,
  en español, patrón C2/C3/C4.
- **R5.10** — Constantes EXACTAS de literales (patrón C3
  `CODE_*`/`MSG_*`; `static final` package-private o private según clase):

```java
// IngestAuthenticationEntryPoint
static final String CODE_API_KEY_INVALID = "api_key_invalid";
private static final String MSG_API_KEY_INVALID =
        "API key de ingesta ausente o inválida";

// IngestGuardFilter
private static final String CODE_PAYLOAD_TOO_LARGE = "payload_too_large";
private static final String MSG_PAYLOAD_TOO_LARGE =
        "La petición supera el límite de 1 MiB";

// IngestController
static final String CODE_PROJECT_ARCHIVED = "project_archived";
private static final String MSG_PROJECT_ARCHIVED =
        "El proyecto está archivado: no acepta logs";
static final String CODE_ENVIRONMENT_REQUIRED = "environment_required";
private static final String MSG_ENVIRONMENT_REQUIRED =
        "El campo environment es obligatorio (development, staging o"
                + " production)";
static final String CODE_TIMESTAMP_WITHOUT_TIMEZONE =
        "timestamp_without_timezone";
private static final String MSG_TIMESTAMP_WITHOUT_TIMEZONE =
        "El campo timestamp debe ser ISO-8601 con zona horaria";
static final String CODE_LEVEL_INVALID = "level_invalid";
private static final String MSG_LEVEL_INVALID =
        "El campo level no es un nivel válido (TRACE, DEBUG, INFO, WARN,"
                + " ERROR o FATAL)";
static final String CODE_MESSAGE_REQUIRED = "message_required";
private static final String MSG_MESSAGE_REQUIRED =
        "El campo message es obligatorio";
static final String CODE_FIELD_TOO_LONG = "field_too_long";
// message(msg, límite): "El campo <f> no puede superar <límite> bytes"
// (message/exception/metadata) o "… <límite> caracteres"
// (source/correlation_id) — compuesto, patrón C3 R3.13.
static final String CODE_METADATA_NOT_OBJECT = "metadata_not_object";
private static final String MSG_METADATA_NOT_OBJECT =
        "El campo metadata debe ser un objeto JSON";
static final String CODE_BATCH_EMPTY = "batch_empty";
private static final String MSG_BATCH_EMPTY =
        "El campo logs debe contener al menos 1 registro";
static final String CODE_BATCH_TOO_LARGE = "batch_too_large";
// mensaje compuesto con el límite efectivo: "El campo logs no puede"
//     + " contener más de <n> registros"
```

  Helper `error(...)` private en el controller — réplica del de C3
  (`LinkedHashMap`: `code`, `message`, `field` solo si aplica, `index` solo
  si aplica; `ObjectMapper.valueToTree`; `MediaType.APPLICATION_JSON`).
- **R5.10-a** — El 202 se emite con la MISMA convención (Map de UNA clave con
  `valueToTree`) — Jackson lo serializa SIN espacios: `{"accepted":1}`.

### 1.D Estamentos SQL EXACTOS (ProjectsRepository aditivo)

- **R5.11** — `ProjectsRepository` DEBE incorporar EXACTAMENTE el siguiente
  estamento (constante `private static final String`, réplica del estilo de
  `SELECT_POR_ID`; reutiliza `COLS` y `FILA` — CD-3.2, una sola fuente de
  columnas/mapeo):

```sql
-- (NUEVO, C5) lookup del hot-path de ingesta por índice parcial único
SELECT id, name, description, status, created_at, updated_at, api_key_prefix,
       (api_key_hash IS NOT NULL) AS has_key
  FROM projects
 WHERE api_key_hash = ?
```

  (en código: `private static final String SELECT_POR_API_KEY_HASH = "SELECT "
  + COLS + " FROM projects WHERE api_key_hash = ?";`). El `ProjectDto`
  completo va GRATIS (el 403 necesita `status`, el service necesita
  `project_id` — Punto 1 del gate). Las filas con hash NULL (revocadas)
  quedan excluidas por el `WHERE` (el hash entrante nunca es `null`:
  `sha256` de un String no devuelve null — regla 11). El Javadoc del método
  DEBE citar el ancla C4 spec §7 y la D-C5-1.
- **R5.11-a** — Firma EXACTA y uso (B1 del gate):

```java
/** C5 (frontera C4 spec §7): lookup O(1) del hot-path de ingesta por el
 *  índice parcial único projects_api_key_hash_idx. Con lista → principal
 *  con project_id+status; filas 0 (ausente/revocada) → Optional.empty() →
 *  401 api_key_invalid del entry point de la cadena 1.
 *  D-C5-1 (gate 2026-10-25): la igualdad la resuelve el WHERE por igualdad
 *  del SELECT — SIN tiempoConstanteIgual post-lookup (ProjectDto/COLS no
 *  porta el hash; divergencia declarada y ratificada en gate). */
@Transactional // defensa documentada (patrón R3.2): mono-sentencia
public Optional<ProjectDto> findByApiKeyHash(byte[] hash) {
    List<ProjectDto> filas = jdbcTemplate.query(
            SELECT_POR_API_KEY_HASH, FILA, hash);
    return filas.isEmpty() ? Optional.empty() : Optional.of(filas.get(0));
}
```

  Binding del `byte[]`: varargs de `JdbcTemplate` → driver PG
  (`setBytes`) → **bytea** — el MISMO mecanismo probado en la dirección de
  ESCRITURA por C4 (E4.19/E4.20: `api_key_hash = sha256('<key>'::bytea)`);
  la dirección de LECTURA (R-2) queda certificada E2E por E5.21 (match real)
  y E5.24 (401). El `FILA` formatea 2 fechas (`created_at`/`updated_at`) que
  la ingesta no usa: coste ínfimo, se paga por CD-3.2 (una sola fuente).
  `NUNCA queryForObject` (patrón C4: la fila-0 es el caso legal del 401).
- **R5.11-b** — `LogsRepository` NUEVO (la ÚNICA fuente del SQL de INSERT
  sobre `logs`; C6 creará sus lecturas en su corte): 3 constantes `static
  final` (patrón de piezas del `UPDATE` de C4 en `ProjectsRepository`):

```java
static final String INSERT_LOGS_CABECERA =
        "INSERT INTO logs (project_id, level, environment, timestamp,"
                + " received_at, message, exception, metadata, source,"
                + " correlation_id) VALUES ";
static final String INSERT_LOGS_FILA =
        "(?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)";
static final String INSERT_LOGS_RETORNO = " RETURNING id";
```

  El estamento para un lote de N registros se construye como
  `INSERT_LOGS_CABECERA` + `INSERT_LOGS_FILA` N veces separadas por
  `", "` + `INSERT_LOGS_RETORNO` (el individual es el MISMO estamento con
  N=1). El `RETURNING id` existe DESDE C5 aunque la 202 no los exponga (la
  moneda del frame `batch` de C7, coste nulo — proposal §2.3). El orden de
  los `?` por fila REPLICA el de las columnas: `project_id, level,
  environment, timestamp, received_at, message, exception, metadata, source,
  correlation_id`. El `CAST(? AS jsonb)` es OBLIGATORIO en la 8.ª posición
  (R-4: un String → jsonb sin cast falla: "column "metadata" is of type
  jsonb but expression is of type character varying").
- **R5.11-c** — Firma y binding del INSERT:

```java
/** C5: INSERT multi-VALUES + RETURNING id (ids en ORDEN de VALUES —
 *  moneda del frame batch de C7). metadata con CAST(? AS jsonb);
 *  timestamp/received_at viajan como OffsetDateTime (PG driver los envía
 *  como TIMESTAMPTZ y PG los normaliza a UTC al guardar — TRD §2.6); los
 *  opcionales nulos van como parámetro NULL en los varargs (el driver lo
 *  infiere — patrón C3 del description). @Transactional defensa documentada:
 *  UNA sentencia = UNA transacción (regla 6 / ADR-11). */
@Transactional
public List<Long> insert(long projectId, List<LogValidado> filas,
        OffsetDateTime receivedAt)
```

  con extractor de fila `(rs, numeroFila) -> rs.getLong("id")` → `List<Long>`
  (en el orden de los VALUES).
- **R5.11-d** — El binding por fila (orden EXACTO de los 10 parámetros:
  `projectId, level, environment, timestamp (OffsetDateTime), receivedAt
  (OffsetDateTime), message, exception, metadataJson (String), source,
  correlation_id`); `timestamp`/`received_at` como `OffsetDateTime`
  (driver → `timestamptz`, normalización UTC de PG — TRD §2.6: "siempre se
  normaliza a UTC al guardar"); la `metadata` va como el String serializado
  con el `CAST` (R5.11-b) — UNA única serialización por registro (S5-8).

### 1.E Validación — orden contractual de comprobaciones por registro

- **R5.24** — La validación completa del lote DEBE ejecutarse ANTES de abrir
  la transacción y por orden DETERMINISTA (primer-fallo-gana, R-9; patrón
  C3 CD-3.6). En batch, el orden de registros es el del array (índices
  crecientes) y el primer registro culpable decide (su `index`). Orden de
  checks POR REGISTRO (cada check → su pareja code/field del R5.9/R5.10):

  1. `environment` — ausente (null) / cadena vacía o solo blancos / no
     textual / fuera del enum `development|staging|production` (case-sensitive,
     cierre S5-4; `ENTORNOS = Set.of(...)`) → 400 `environment_required`,
     `field: "environment"`.
  2. `timestamp` — parse `OffsetDateTime.parse` (cierre S5-5: ausente / no
     textual / no parseable / naive sin offset → el MISMO code) → 400
     `timestamp_without_timezone`, `field: "timestamp"`.
  3. `level` — ausente / no textual / fuera del enum cerrado
     `TRACE, DEBUG, INFO, WARN, ERROR, FATAL` (case-sensitive EXACTO, cierre
     S5-6; `NIVELES = Set.of(...)`) → 400 `level_invalid`, `field: "level"`.
  4. `message` — ausente / cadena vacía o solo blancos (`isBlank()`, patrón
     C3) → 400 `message_required`, `field: "message"`.
  5. `message` — > 32768 **bytes UTF-8** → 400 `field_too_long`,
     `field: "message"` (mensaje compuesto con el límite en bytes).
  6. `exception` (opcional) — > 131072 bytes UTF-8 → 400 `field_too_long`,
     `field: "exception"`.
  7. `metadata` (opcional) — presente NO objeto (`!isObject()`; array o
     escalar) → 400 `metadata_not_object`, `field: "metadata"`; su JSON
     serializado > 65536 bytes → 400 `field_too_long`, `field: "metadata"`.
     `{}` (objeto vacío) es VÁLIDO.
  8. `source` (opcional) — `length()` > 200 **caracteres** → 400
     `field_too_long`, `field: "source"` (mensaje en caracteres).
  9. `correlation_id` (opcional) — `length()` > 200 caracteres → 400
     `field_too_long`, `field: "correlation_id"`.

  Justificación del orden interno (los docs fijan el orden ENTRE componentes
  413→401→403→400, no entre campos): los requeridos primero (satisfacibilidad
  antes que la validez de lo opcional), luego parseo (timestamp), luego enum
  (level), luego message, luego tamaños de opcionales — igual que C3 fija
  su orden "primer fallo gana" cuando el doc no lo da. SIN normalización
  case en NINGÚN enum (patrón C3: "Minúsculas no se normalizan (CHECK
  literal)"): un valor fuera del literal no se corrige silenciosamente —
  cambia el valor persistido.
- **R5.24-a** — Los campos de texto opcionales (`exception`, `source`,
  `correlation_id`) sin normalización (ni trim ni vacío→NULL): cadena vacía
  presente es VÁLIDA y se persiste vacía (patrón C3: `""` es legal para
  `description`); `null`/ausente → columna NULL. La lectura de valores
  (`desdeJson`) usa el patrón C3 literal (`valor()` con `asText()` —
  UpdateProjectRequest.java): la coerción de Jackson es la convención de la
  casa y queda determinista (escalar → su forma texto; contenedor → "").
- **R5.24-b** — Validación MANUAL (sin `@Valid`, sin starter-validation —
  pom congelado; patrón C3). `LogIngestRequest.desdeJson(JsonNode)` (cierre
  S5-1/S5-2) lee SOLO los 8 tipos literales del Schema §3.4
  (`level, timestamp, message, exception, metadata, environment, source,
  correlation_id`) — campos desconocidos ignorados.
- **R5.24-c** — El endpoint batch controla el límite del lote SOBRE el
  `JsonNode` y ANTES de parsear elemento alguno (cierre S5-8):
  `path("logs")` — si no es array (ausente, no-array) → 400 `batch_empty`
  (`field: "logs"`); array de 0 → `batch_empty`; array de > 200 →
  `batch_too_large` (límite de la propiedad, §1.H); luego y solo luego se
  parsea registro a registro (`desdeJson`) en orden. El límite de batch es
  nº de ELEMENTOS, independiente del límite de bytes (R-13: un lote de 200
  registros con 32 KiB de message cadauno superaría 6 MiB — el 413 del
  1 MiB lo corta ANTES, y esa es la respuesta correcta: el tamaño es la
  primera barrera contractual, NO se "re-especializa" en 400).

### 1.F All-or-nothing, transacción y received_at (reglas 6 y 9)

- **R5.12** — Flujo del `IngestService` (método público NO transaccional —
  es el úNICO propietario del orden): recibe `projectId`, los registros
  parseados y el flag `lote` (individual vs batch — decide si los 400 llevan
  `index`) → (1) valida COMPLETO en orden (§1.E/R5.24) → con fallo: devuelve
  el error (sin tocar la BD — regla 6 verbatim: "El lote se valida **completo
  antes de tocar la BBDD** … si un registro falla → 400 con `index`/`field`
  del culpable y **nada se persiste**") → (2) `received_at = OffsetDateTime
  .now(ZoneOffset.UTC)` UNA vez (cierre S5-7) → (3) `logsRepository.insert(…)`
  (el `@Transactional` del repo = UNA sentencia multi-VALUES = UNA
  transacción; primera multi-fila del codebase — el Javadoc de
  `ProjectsRepository` lo anticipaba: "lo multi-sentencia (batch
  all-or-nothing…) llega en C5") → (4) tras el RETORNO del insert (COMMIT
  estructural — el service NO es transaccional) invoca el seam (§1.G) →
  (5) sin excepción: el controller emite el 202 (`Map.of("accepted", N)`,
  N = nº de registros del lote; individual = 1).
- **R5.12-a** — 202 SOLO tras el COMMIT (ADR-11 verbatim via regla 6: "la
  inserción del lote es **una única transacción**; 202 solo tras el COMMIT").
  Nada del lote fallido cruza el 202 (ni ids ni received_at: la 202 es
  `{"accepted":N}` sola).
- **R5.12-b** — SIN captura de `DataIntegrityViolationException` ni
  `DuplicateKeyException` (en `logs` no hay UNIQUE aparte de la PK): los
  fallos reales de INSERT son teóricos (FK `project_id` si el proyecto
  desaparece en carrera → **500 honesto**, política S6 de C4); el rollback
  de la transacción cubre el resto. El seam NO se invoca ante un insert que
  falla (la emisión queda estructuralmente tras el COMMIT — ADR-9).

### 1.G Seam para C7 (Punto 5 del gate, aprobado)

- **R5.13** — `com.projectlog.ingest.IngestListener`: interfaz con UN ÚNICO
  método, firma EXACTA (cierre S5-10):

```java
void onLotePersistido(long projectId, OffsetDateTime receivedAt,
        List<Long> ids);
```

  Suscripción: el `IngestService` recibe `List<IngestListener> oyentes` por
  constructor — **lista VACÍA en C5** (cero clientes: no-op verificable;
  `Optional<IngestListener>` descartado, S5-10). Invocación (§1.F/R5.12):
  DESPUÉS del retorno del insert (tras-COMMIT estructural, fuera de todo
  scope transaccional), con los args EXACTOS: el `projectId` del principal,
  el `received_at` común fijado (el MISMO valor persistido), los `ids` en
  orden de VALUES. TAMBIÉN en la ingesta individual (lista de 1 id — el
  frame `batch` de C7 para un log individual es batch de 1, Schema §3.5).
  JAMÁS ante 401/403/400/parse ni ante rollback. C5 NO implementa streaming
  (ni frames ni handler ni `LiveBroadcaster`): solo este seam.

### 1.H Propiedades y bind (R-6)

- **R5.14** — `application.yml` DEBE quedar con (patrón
  `projectlog.security.jwt-ttl-hours: ${JWT_TTL_HOURS:8}`):

```yaml
projectlog:
  security:
    jwt-ttl-hours: ${JWT_TTL_HOURS:8}
    frontend-origin: ${FRONTEND_ORIGIN:}
  ingest:
    max-request-bytes: ${INGEST_MAX_REQUEST_BYTES:1MiB}   # literal TRD §6.3
    max-batch-size: ${INGEST_MAX_BATCH_SIZE:200}
```

  Los defaults son EXACTAMENTE los literales de TRD §6.3/`.env.example`
  (1 MiB / 200) — `.env.example` INTOCABLE. El bind del límite de bytes es
  String→normalizador→`DataSize.parse` (cierre S5-9, con la evidencia
  local: `DataSize.parse("1MiB")` FALLA; `DataSize.parse("1MB")` →
  1048576); el bind del batch es int directo. Los 2 valores se convierten
  UNA VEZ en el constructor de su consumidor (filtro / service) y el
  literal no convertible DEBE abortar el arranque (fail-fast). El unit del
  bind es OBLIGATORIO (E5.34). `application-test.yml` INTOCABLE (los [L]
  corren con los defaults).

### 1.I Higiene de logs

- **R5.15** — Las clases nuevas del paquete `ingest` DEBEN nacer SIN loggers
  (patrón literal JwtAuthFilter/RestAuthenticationEntryPoint de C2): NINGUNA
  clase loguea la key, un fragmento, el header `X-API-Key`, ni el hash.
  La higiene DEBE probarse con el patrón `LogHygieneTest` de C2
  (`ListAppender`): escenario E5.17.

### 1.J Divergencia D-C5-1 y cierres heredados G-1/G-2

- **R5.16** — La D-C5-1 (§0.2) DEBE quedar documentada en el Javadoc del
  método `findByApiKeyHash` y del `IngestGuardFilter` (cita literal de la
  declaración). **G-1** (QP-30 de C4: rama empty-RETURNING→404 en
  `regenerateKey`): CERRADO como hueco aceptado — C5 NO reabre ese método ni
  le asigna escenario (Punto 3 del gate). **G-2** (regla 11, "al desarchivar,
  la misma key vuelve a aceptarse"): cierre ACTIVO con E5.31 (desarchivar →
  MISMA key → 202); el archive de C5 lo citará.

### 1.K Tests y smoke

- **R5.17** — Suite [L] (patrón C2/C3/C4: `@WebMvcTest(IngestController)` +
  `@Import(SecurityConfig, IngestGuardFilter, IngestAuthenticationEntryPoint)`
  + `@MockitoBean` de `ProjectsRepository` y de `ApiKeyService` +
  `@ActiveProfiles("test")` con `application-test.yml` INTOCABLE) +
  unitarios puros (normalizador de límite, seam) + pin SQL (patrón
  `ProjectsKeySqlTest` → `LogsRepoSqlTest`) + smoke [S] en pila real
  (`scripts/qa_c5_smoke.sh`, NUEVO, patrón `qa_c4_smoke.sh`) con el desglose
  numerado y nominado de §2.3 y el guion de §2.5. Patrones de entorno
  HEREDADOS de C3/C4 (R4.15): recomposición ÚNICA con `--force-recreate`
  (`podman compose`, timeout acotado), `mvn test` DENTRO de la imagen
  builder `maven:3.9-eclipse-temurin-21` (backend + `~/.m2` montados), psql
  `-qAt`, epoch contextual `FLOOR(EXTRACT(EPOCH …))::bigint` donde toque,
  booleanos `t/f` (psql) ≠ `true/false` (JSON), **smoke-of-the-smoke** antes
  de la canónica, nombres sufijados por ejecución (`smoke-c5-<TS>-*`), keys
  y JWT solo en tmpdir 700 (jamás a consola), **conteos RELATIVOS**
  (`count(*)` antes vs después de cada escenario — R-15: el volumen `pgdata`
  conserva datos de C4), y needles psql de `plk_*` con escape `\_` o
  `position()`/`=` (S-3 heredado de C4).
- **R5.18** — Parejas OBLIGATORIAS mock↔psql (la lista de §2.3): todo
  code/field/index literal certificado en [L] DEBE tener su paso [S] contra
  la pila para los Done del 07 (401, 403, 400×2, 413 y all-or-nothing como
  MÍNIMO — Done 4); los demás en [L] puro. El 4.º Done del 07 ("batch
  intermedio inválido persiste NADA") se verifica con conteo psql absoluto
  RELATIVO (E5.23).

### 1.L No-regresiones C1–C4 — bloqueos heredados

- **R5.19** — C5 NO DEBE romper NADA de C1–C4: (a) el Javadoc/SQL de C3/C4 en
  `ProjectsRepository` sin diff (los 2 UPDATE de C4 y los 4 métodos/estamentos
  de C3 quedan byte-a-byte — pin de `ProjectsKeySqlTest` re-corrido); (b)
  `panelChain`/`defaultChain` byte-a-byte (el pin E5.16-b se certifica con la
  RE-CORRIDA completa de la suite de C1–C4 en imagen builder: 77/77); (c)
  `RestAuthenticationEntryPoint` (panel, CD-4: `invalid_token`) INTACTO —
  `PanelAuthMvcTest` re-corrido sigue verde; (d) `CorsConfig` sin
  `X-API-Key` (cierre S7 de C4 mantenido); (e) `streaming/**` (Echo +
  WebSocketConfig) sin diff; (f) `V1__init.sql` sin diff; (g) sin nuevos
  codes fuera de los de R5.10 (reutilizados: `field_too_long`.
  NINGÚN code nuevo de C5: los 11 literales del R5.10 son todos del enum
  §3.4/§4.1); (h) sin starter-validation, sin Testcontainers, sin DIVE
  capturado, sin rate limiting (ADR-26).

## 2. Escenarios de aceptación

Cada escenario es verificable en al menos un test [L] (§2.3) o paso del
smoke [S] (§2.5). Los IDs E5.x son estables para qa/verify y REPLICAN LOS
DATOS de la proposal §6 (1–33) con el +1 del cierre S5-9 (E5.34, el bind del
R-6 — añadido sin renumerar nada: los Done de la proposal citan E5.20–E5.33
tal cual).

### 2.1 MVC [L] — literales de respuesta (202)

- **E5.1** `ingestIndividualResponde202Accepted1Exacto` [L] — con key mock
  válida (ACTIVE): POST `/api/v1/ingest` válido → **202** y el cuerpo
  serializado EXACTO `{"accepted":1}` (sin espacios, sin más campos).
- **E5.2** `ingestBatchResponde202AcceptedNExacto` [L] — batch de N (≥2) →
  202 `{"accepted":N}`.

### 2.2 MVC [L] — 400 de campo y de lote

- **E5.3** `environmentAusenteOInvalidoResponde400EnvironmentRequired` [L] —
  las 4 variantes del cierre S5-4 (ausente, "", no textual, fuera del enum
  p.ej. `"qa"`): 400 `{code: environment_required, field: environment}` y
  el message EXACTO del R5.10; sin `index` (individual).
- **E5.4** `timestampNaiveONoParseableResponde400TimestampWithoutTimezone` [L]
  — `"2026-10-16T22:31:04"` (naive) y no-textual → 400
  `timestamp_without_timezone`, `field: "timestamp"`.
- **E5.5** `levelFueraDeEnumResponde400LevelInvalid` [L] — `"error"`
  (minúscula, sin normalización), `"TRACE "` y ausente → 400
  `level_invalid`, `field: "level"`.
- **E5.6** `messageVacioResponde400MessageRequired` [L] — ausente, "" y "   "
  → 400 `message_required`, `field: "message"`.
- **E5.7** `messageBytes32769Responde400FieldTooLongFrontera` [L] — frontera
  de BYTES reales: un message de 32768 bytes UTF-8 (con carácter multibyte
  para certificar la convención bytes≠chars) → 202; 32769 bytes → 400
  `field_too_long` `field: "message"` con message "...32768 bytes".
- **E5.8** `metadataArrayOEscalarResponde400MetadataNotObject` [L] — array y
  escalar → 400 `metadata_not_object` `field: "metadata"`; `{}` → 202 (válido).
- **E5.9** `fieldTooLongDeExceptionMetadataSourceYCorrelationId` [L] —
  exception 131073 bytes → 400 `field_too_long` (exception); metadata
  serializada 65537 bytes → 400 (metadata); source 201 chars → 400 (source);
  correlation_id 201 chars → 400 (correlation_id); cada frontera legal
  (131072 / 65536 / 200 / 200) → 202.
- **E5.10** `batchVacioONoArrayY201Responde400FieldLogs` [L] — `{"logs": []}`
  y logs ausente / no array → 400 `batch_empty` `field: "logs"`;
  `{"logs": [201 registros]}` → 400 `batch_too_large` `field: "logs"`.
- **E5.11** `batchRegistroIntermedioLlevaIndexYFieldPrimerFalloGana` [L] —
  batch de 5 con el registro 2 (0-based) inválido en `level` Y el 4
  inválido en `message` → 400 del culpable con `index: 2`, `field: "level"`
  (primer-fallo-gana en orden del array — R-9); y en individual el mismo
  fallo SIN `index` (cierre S5-3).
- **E5.14** `ordenFailFastDeCamposEsDeterminista` [L] — registro con TODOS
  los campos mal → el 400 es SIEMPRE el de `environment` (check 1 del
  R5.24); otro registro con solo `timestamp`+`level` mal → el de timestamp
  (check 2) — orden fijo de la §1.E.
- **E5.24-c** — sin nombre: el 400 de CUERPO ILEGIBLE (S5-1): cuerpo
  "no-json" en individual → 400 `environment_required`; en batch → 400
  `batch_empty`; y con Content-Type `text/plain` → el MISMO 400 (el
  HttpMediaTypeNotSupportedException se mapea, no 415).

### 2.3 MVC/unit [L] — 401/403, pins, higiene, servicio y seam

- **E5.12** `sinHeaderMalformadaONoMatchResponde401ApiKeyInvalid` [L] —
  cadena 1: sin header `X-API-Key`, header basura no plk, y key válida-forma
  con hash sin matchea → 401 con el cuerpo EXACTO del R5.9 (mismo body para
  las 3 — anti-oráculo); `verifyNoInteractions` con el service (el 401 jamás
  toca la BD de logs).
- **E5.13** `proyectoArchivadoResponde403ProjectArchivedAntesDeValidar` [L] —
  principal ARCHIVED + cuerpo inválido → 403 (gate 3 ANTES de la validación,
  orden del R5.8); cuerpo `{code: project_archived, message: <literal>}` sin
  field; el repo de logs NUNCA se llama.
- **E5.16** `jwtJamásAutenticaIngestaYCadena1SinInvalidToken` [L] — request
  con `Authorization: Bearer <jwt>` válido y SIN `X-API-Key` → 401
  `api_key_invalid` (NUNCA `invalid_token` — Schema §5: "Un JWT jamás
  autentica la ingesta"); y la respuesta de ingesta jamás contiene el
  literal del panel. (Las cadenas 2/3 byte-a-byte se certifican con la
  re-corrida de la suite C1–C4 — `PanelAuthMvcTest` espera `invalid_token`.)
- **E5.15** `insertLogsPinenLiteralesExactos` [L] — patrón
  `ProjectsKeySqlTest`: pin de `INSERT_LOGS_CABECERA`
  (`INSERT INTO logs (project_id, level, environment, timestamp, received_at,
  message, exception, metadata, source, correlation_id) VALUES `),
  `INSERT_LOGS_FILA` (`(?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)`),
  `INSERT_LOGS_RETORNO` (` RETURNING id`), y el pin del SELECT:
  `SELECT_POR_API_KEY_HASH` = `SELECT ` + `COLS` + ` FROM projects WHERE
  api_key_hash = ?` (sin columnas extra). Fichero:
  `backend/src/test/java/com/projectlog/projects/repository/` (pin junto a
  `ProjectsKeySqlTest`) o en `ingest` — design fija.
- **E5.16-b** `securityConfigCadenas2y3ByteABByte` [L] — cubierto por la
  re-corrida de `PanelAuthMvcTest` + suite C1–C4 (R5.19b); sin test nuevo de
  C4 reescrito.
- **E5.17** `ningúnLogDeIngestContieneKeyHeaderOHash` [L] — patrón
  `LogHygieneTest` (ListAppender): mientras corre un ingest (202) y un 401,
  NINGÚN evento de las clases de `com.projectlog.ingest` ni de
  `com.projectlog.keys` contiene la key, un fragmento, el header `X-API-Key`
  ni el hash.
- **E5.18** `timestampParseAceptaZyOffsetsYRechazaNaive` [L] (unit del parse,
  cierre S5-5) — `2026-10-16T22:31:04Z`, `2026-10-16T22:31:04+02:00`,
  `2026-10-16T22:31:04.123+05:30` parsean; `2026-10-16T22:31:04` (naive) lanza
  `DateTimeParseException` → 400 `timestamp_without_timezone`.
- **E5.19** `received_atFijadoUnaVezComunAlLote` [L] (cierre S5-7) —
  service con repo mock: una sola llamada a `insert` con el MISMO
  `OffsetDateTime` para TODAS las filas del lote (ArgumentCaptor: una
  invocación, un único receivedAt) y el mismo valor portado al seam.
- **E5.20** `seamIngestListenerSoloTrasCommitConArgsExactos` [L] (cierre
  S5-10) — service con `List<IngestListener>` de un mock: (a) batch válido →
  `onLotePersistido(projectId, receivedAt, ids)` invocado UNA VEZ con los
  ids del mock en ORDEN de VALUES; (b) individual → invocado con lista de 1;
  (c) ante 403/400/parse (en el controller) o rollback del insert
  (`insert` lanza) → `verifyNoInteractions` del listener.
- **E5.34** `elLiteralMiBSeBindeaASusBytes` [L] (cierre S5-9, OBLIGATORIO) —
  unit del normalizador: `"1MiB"` → 1048576; `"1KB"`/`"1KiB"` → 1024;
  `"1048576"` → 1048576; `"1MB"` → 1048576 (la equivalencia binaria de
  Spring); literal no parseable (`"abc"`) → excepción (fail-fast de
  arranque); y el default del `@Value` es EXACTAMENTE `"1MiB"`.

### 2.4 Cierre declarado (sin escenario)

- **G-1** — SIN escenario (cierre S5-14): la rama empty-RETURNING→404 de
  `regenerateKey` queda CERRADA (hueco aceptado del archive de C4); C5 no la
  toca. Cualquier escenario que la reabra ensancharía falsamente este corte.
- **G-2** — cierre ACTIVO: **E5.31** (§2.5).

### 2.5 Smoke [S] — `scripts/qa_c5_smoke.sh` (guion exacto, patrón C4)

> Pre-fase (smoke-of-the-smoke, sin ID propio — parte del E5.21):
> recomposición ÚNICA `podman compose up -d --build --force-recreate` +
> pila healthy + sondeo heredado `GET /me` sin token → 401 `invalid_token`
> (cadena 2, certifica que el cambio NO rompió el panel) + login → 200
> (JWT al tmpdir) + `POST /projects` → 201 `smoke-c5-<TS>-a` (captura
> incondicional del id) + `POST /projects/{id}/api-key/regenerate` → 201
> (key → tmpdir, jamás a consola). Helper `curl_ingest KEY …` con
> `-H "X-API-Key: $KEY"`.

- **E5.21** happy individual [S] — `POST /api/v1/ingest` con la key +
  `{"level":"ERROR","timestamp":"<T>","message":"E5.21","environment":
  "production","correlation_id":"e521","source":"smoke","metadata":{"span":
  "checkout"}}` → **202** y el cuerpo EXACTO `{"accepted":1}`; psql: delta
  count(*) = 1 y la fila nueva con `project_id = <id>` (psql: el último id
  del proyecto), level/env/correlation_id/source EXACTOS.
- **E5.22** batch de 200 [S] — payload generado (200 registros, cada uno con
  `correlation_id` `e522-<nnn>` y level/env rotando) → **202**
  `{"accepted":200}`; psql: delta = 200, `count(DISTINCT received_at) = 1`
  SOBRE los 200 ids nuevos (regla 9 — igualdad EXACTA), y los ids crecen en
  orden de VALUES (correlación id ASC ↔ correlation_id ASC vía JOIN sobre
  los ids > máximo previo — aserto con ROW_NUMBER).
- **E5.23** all-or-nothing [S] — batch con un registro inválido INTERMEDIO
  (p. ej. índice 2 con `level: "XX"`) → **400** `level_invalid` con
  `field: "level"` **y `index: 2`**; psql: `count(*)` delta = 0 (NADA se
  persiste — regla 6).
- **E5.24** 401 [S] — 3 variantes contra la pila: (a) sin header → 401;
  (b) `X-API-Key: plk_basura_no_existente` → 401; (c) revocar la key (DELETE
  con JWT → 204; los tres a NULL en psql, regla 11) → ingestar con la MISMA
  key → 401. Las 3 con el MISMO cuerpo (needle `api_key_invalid`, y jamás
  `invalid_token`). Luego `POST regenerate` → 201 con key2 (seguir).
- **E5.25** 403 [S] — proyecto ACTIVE + key2 → `PATCH {"status":
  "ARCHIVED"}` (C3, 200) → ingestar con key2 → **403**
  `{code: project_archived, message: <literal>}`; psql: count(*) delta = 0.
- **E5.26** 400 reales [S] — (proyecto ACTIVE, key2) `environment` ausente →
  400 `environment_required`; `timestamp` naive (sin offset) → 400
  `timestamp_without_timezone`; en ambos psql delta = 0.
- **E5.27** 413 frontera [S] — cuerpo con padding JSON legal: 1048576 bytes
  EXACTOS (`Content-Length` presente, curl `--data-binary`) → **202**
  (la barrera es inclusiva en el límite); 1048577 bytes → **413**
  `payload_too_large` (needle del cuerpo, psql delta = 0). (Si curl
  manifestase la conexión reiniciada sin cuerpo — R-12 — se activa el plan B
  documentado en §2.6.)
- **E5.28** 413 chunked [S] — mismo payload de 1048577+ bytes SIN
  `Content-Length` (`--header "Transfer-Encoding: chunked"`) → **413**
  `payload_too_large` (segunda barrera del filtro — la primera no dispara:
  no hay header).
- **E5.29** confianza timestamp [S] — ingest con `timestamp` `+02:00` → 202;
  psql: `timestamp` almacenado == el instant UTC esperado (`t/f` del
  `timestamp = '<utc>'::timestamptz`) y `received_at` ≥ el T1 del request
  (impuesto por el servidor — el humo NO lo controla).
- **E5.30** JSONB + fronteras de bytes [S] — `metadata` `{"span":"checkout"}`
  → 202; psql: `metadata->>'span' = checkout`; metadata array → 400
  `metadata_not_object`; frontera metadata 65536/65537 bytes serializados →
  202/400; y frontera message 32768/32769 bytes → 202/400 (pareja [S] de
  E5.7).
- **E5.31** G-2 [S] — cierre ACTIVO del hueco heredado: proyecto aún
  ARCHIVED (de E5.25) → `PATCH {"status": "ACTIVE"}` (desarchivar) →
  ingesta con la MISMA key2 (jamás re-generada) → **202** (reglas 11 + 12
  juntas). El archive de C5 cita este ID.
- **E5.32** fronteras de lote [S] — `{"logs": []}` → 400 `batch_empty`;
  `{"logs": [201...]}` → 400 `batch_too_large` (ambas `field: "logs"`; psql
  delta = 0) — pareja [S] de E5.10.
- **E5.33** orden de gates en pila [S] — petición de >1 MiB con
  `X-API-Key: plk_basura` (key inválida) → **413** (no 401): el gate 1
  antecede al 2 literalmente en la pila; y con cuerpo ≤1 MiB + key inválida
  → 401 (el mismo flujo controlado).

Salida: una línea `E5.n: PASS | FAIL | BLOQUEADO (detalle)` por ID;
exit 0/1/2 como C3/C4.

### 2.6 Nota sobre R-12 (413 sin leer el cuerpo)

La barrera 1 escribe el 413 y NO drena el cuerpo (fail-fast; drenar 1 MiB+
contra el propósito). Tomcat puede manifestar una conexión reiniciada con
algún cliente si el restado del cuerpo aborta la lectura de la respuesta —
si E5.27 lo mostrara, el apply activa el plan B (drenar ACOTADO hasta
`límite+1` antes de responder) SIN cambiar el contrato (el cuerpo y el code
del 413 son los mismos). La segunda barrera (chunked) no está expuesta a
esto (el cuerpo ya se está leyendo stream-a-stream).

## 3. Contratos citados (fuente única de verdad)

| Literal | Fuente (congelada) |
|---|---|
| Rutas `POST /api/v1/ingest` / `/ingest/batch` con `X-API-Key`; entradas `LogIngestRequest` / `{logs: [LogIngestRequest]}` 1…200 | Schema §3.2 (filas POST ingest) + §3.4; TRD §2.6; AppFlow flujo 10 |
| 202 `{"accepted": 1}` / `{"accepted": N}` (decisión cerrada: no es 200) | Schema §3.4 ("**Respuestas**"); 07 ficha C5 |
| 401 `api_key_invalid` (key ausente/inválida/revocada — un único body); 403 `project_archived` con key VÁLIDA; 400 con `field` (e `index` en lote); 413 `payload_too_large` — **los 4 con el orden 413→401→403→400, todo fail-fast antes de la BBDD** | Schema §3.4 (tabla de errores de ingesta + párrafo "Orden de evaluación…") |
| Códigos 400: `environment_required`, `timestamp_without_timezone`, `level_invalid`, `message_required`, `metadata_not_object`, `field_too_long`, `batch_empty`, `batch_too_large` | Schema §3.4 (enum) + §4.1 (filas por límite) |
| Cuerpo de error estándar `{code, message, field?}`; fechas ISO-8601 UTC; códigos usados (200/201/202/204, 400, 401, 403 solo en ingesta, 404, 409, 413; sin 429) | Schema §3.1; TRD §2.6 ("Errores explícitos siempre, con cuerpo `{code, message, field?}` — nunca silenciosos") |
| `project_id` deducido de la key (el cliente no lo manda); `received_at` impuesto por el servidor en UTC; `id` lo genera la BD | Schema §4 regla 5 + §3.4; PRD §6 ("El backend valida y establece los campos de confianza"); AppFlow |
| Batch all-or-nothing: lote validado COMPLETO antes de la BBDD, NADA se persiste a medias, UNA transacción, 202 solo tras el COMMIT | Schema §4 regla 6 (verbatim) + ADR-11 |
| Todos los logs de un batch comparten un MISMO `received_at` (fijado en el momento de la transacción) | Schema §4 regla 9 (verbatim) |
| Enums cerrados: level `TRACE/DEBUG/INFO/WARN/ERROR/FATAL`, environment `development|staging|production`, forzados por CHECK **y** validación en el borde | Schema §4 regla 10 + §2 (tabla `logs`) + `V1__init.sql` líneas 39–40 |
| `environment` obligatorio sin default (400 si falta) | Schema §4 regla 7 + ADR-25 + §2 fila environment |
| `timestamp` ISO-8601 CON offset (400 sin offset); normalizado a UTC al guardar | Schema §2 fila timestamp + §3.4; TRD §2.6 ("siempre se normaliza a UTC al guardar") |
| `correlation_id` opcional del cliente (columna ÚNICA unificada, sin generación server-side) | Schema §4 regla 8 + ADR-28 |
| `metadata` solo objeto JSON; tamaños máximos por campo | Schema §4 regla 14 |
| Límites: petición 1 MiB (`INGEST_MAX_REQUEST_BYTES`) → 413 `payload_too_large`; batch 1…200 (`INGEST_MAX_BATCH_SIZE`); message ≤ 32 KiB (32768 B); exception ≤ 128 KiB (131072 B); metadata ≤ 64 KiB serializado (65536 B); source/correlation_id ≤ 200 caracteres | Schema §4.1 (filas verbatim) + §3.4; TRD §2.7 (tabla); Schema §2 (200, "concreción") |
| "Estos límites no son rate limiting" (429 no existe) | Schema §4.1 (párrafo) + ADR-26 |
| Ingesta solo sobre proyectos ACTIVE (ARCHIVED → 403 para toda ingesta; sus logs siguen consultables) | Schema §4 regla 12 (verbatim) |
| Key: hash SHA-256 BYTEA, índice parcial único O(1); revocada = tres NULL → 401; regenerar invalida al instante; archivar no toca la key; misma key al desarchivar | Schema §2 (fila api_key_hash) + §4 regla 11 + §5 (bloque API keys); TRD §2.6 + ADR-4 |
| Un JWT jamás autentica la ingesta; una API key jamás autentica panel ni socket | Schema §5 (tabla de superficies, verbatim) + TRD ADR-15 |
| Ingesta sin CORS (clientes servicios) | TRD §5 + §2.1; SecurityConfig cadena 1 (TRD §5) |
| Errores de ingesta sin UI (los reciben los scripts tal cual) | AppFlow flujo 10 (verbatim); AppFlow §5 nota de apertura |
| Env: `INGEST_MAX_REQUEST_BYTES` / `INGEST_MAX_BATCH_SIZE` — 1 MiB / 200 | TRD §6.3 (tabla + bloque de `.env.example`, literal `1MiB`/`200`) |
| Emisión tras COMMIT del frame batch (C7): proyecto, received_at e ids son del lote; emisión jamás de lo fallido | Schema §3.5 + TRD §2.8/§2.9 + ADR-8/9 |
| Estamentos SQL y patrón COLS/FILA (una sola fuente) | `V1__init.sql` (tabla logs líneas 29–43, índice 54–55); `ProjectsRepository` (COLS, FILA, patrón C2/C3) |
| Firma del lookup + contrato del hash (String completo, UTF-8, digest binario) | C4 spec §7 (frontera, verbatim) + §0.2 |

## 4. Fuera de alcance (explícito — NO cubierto por C5)

- **Streaming real (C7)**: sin frames, sin handler real de `/ws/logs`, sin
  `LiveBroadcaster` — SOLO el seam `IngestListener` (§1.G) con lista vacía.
- **Consulta (C6)**: ni `GET /panel/logs` ni `GET /panel/logs/{id}`, sin
  LogDto, sin cursor, sin filtros; el repo de LECTURA de logs NO se anticipa
  (los INSERTs de `logs` viven en `LogsRepository`; las lecturas las crea C6
  en su corte).
- **Frontend (C8)**: los errores de ingesta no tienen UI (AppFlow flujo 10).
- **Migraciones**: `V1__init.sql` INTOCABLE; sin `V2` (trigram).
- **Panel/cadenas 2 y 3**: sin diff (`panelChain`/`defaultChain` byte-a-byte);
  `RestAuthenticationEntryPoint` (panel, `invalid_token`, CD-4) INTACTO —
  jamás produce un 401 de ingesta.
- **NO reabre G-1** (QP-30 de C4, cerrado como hueco aceptado — §1.J); G-2
  cierra ACTIVO con E5.31.
- **No rate limiting 429** (ADR-26), **no retención**, **no borrados de logs
  ni summary** (C10), **sin Testcontainers** (Punto 4 del gate), **sin
  starter-validation**, **sin captura de DIVE** (500 honesto, R5.12-b), **sin
  normalización case de enums**, **sin generación server-side de
  correlation_id** (el cliente la aporta; regla 8/ADR-28), **sin validación
  de Content-Type** (S5-12), **sin 404 en ingesta** (la key deduce el
  proyecto; "no existe" es 401 para no dar oráculo), **sin docs ni front
  tocados**, **sin oráculo de causa del 401** (un único body).
- **Nota heredada S-3 (C4)**: los needles psql de literales con underscore de
  `plk_` usan escape `\_` o `position()`/`=` (R5.17) — deuda de patrón, no de
  código.

## 5. Trazabilidad

| Requisito | Escenarios | Verificado por |
|---|---|---|
| R5.1 (paquete, pom, sin migración) | E5.1–E5.20, E5.34 | review de ficheros + suite [L] |
| R5.2/R5.3 (aditividad, frontera C4) | E5.15, E5.16-b, E5.24-c | pin SQL + re-corrida |
| R5.4 (guard: 413 doble barrera + auth) | E5.27, E5.28, E5.33 (y E5.12) | smoke + MVC |
| R5.5/R5.6 (entry point NUEVO + chain 1) | E5.12, E5.16 | MVC |
| R5.7/R5.8 (rutas + orden de gates) | E5.1, E5.2, E5.12, E5.13, E5.27, E5.33 | MVC + smoke |
| R5.9/R5.10 (literales y constants) | E5.1–E5.14, E5.21–E5.33 | MVC + smoke |
| R5.11 (estamentos SQL + firmas) | E5.15, E5.21, E5.22 | pin + smoke |
| R5.24 (validación y orden) | E5.3–E5.11, E5.14, E5.23 | MVC + smoke |
| R5.12 (all-or-nothing, 202 tras COMMIT) | E5.2, E5.11, E5.22, E5.23 | smoke + MVC |
| R5.13 (seam C7) | E5.20 | unit |
| R5.14 (propiedades y bind R-6) | E5.34 | unit |
| R5.15 (higiene) | E5.17 | ListAppender |
| R5.16 (D-C5-1, G-1, G-2) | — / E5.31 / E5.24 | review + smoke |
| R5.17/R5.18 (suite, smoke y parejas) | E5.1–E5.34 | suite + smoke |
| R5.19 (no-regresiones C1–C4) | suite C1–C4 re-corrida (77/77) | mvn + psql |

Criterios Done = los 10 de la proposal §6, trazados a escenarios:
1 (E5.21, E5.29, E5.30), 2 (E5.22), 3 (E5.23, E5.11), 4 (E5.24, E5.25,
E5.26, E5.27; orden pila E5.33), 5 (E5.31), 6 (E5.32, E5.27, E5.28), 7
(E5.5–E5.9, E5.30), 8 (E5.1–E5.20, E5.34), 9 (E5.20), 10 (§4).

## 6. Mapa de verificación (resumen qa)

| Familia | IDs | Tipo |
|---|---|---|
| MVC 202/400/401/403 | E5.1–E5.16, E5.24-c | [L] `@WebMvcTest` (patrón ApiKeyControllerMvcTest) |
| Unit servicio/parse/seam/bind | E5.17? no — E5.18, E5.19, E5.20, E5.34 | [L] puro |
| Pin SQL | E5.15 | [L] patrón ProjectsKeySqlTest |
| Higiene logs | E5.17 | [L] ListAppender |
| Pila real | E5.21–E5.33 | [S] curl+psql, patrón C3/C4 |

Total: **34 escenarios — 21 [L] + 13 [S]** (los 33 de la proposal + el
E5.34 del cierre S5-9). Parejas mock↔psql OBLIGATORIAS (R5.18):
E5.1↔E5.21, E5.2↔E5.22, E5.11↔E5.23, E5.12↔E5.24, E5.13↔E5.25,
E5.3↔E5.26 (y E5.4↔E5.26), E5.7↔E5.30, E5.10↔E5.32, E5.14↔E5.33,
E5.15↔(psql E5.21), E5.16/E5.16-b↔(sondeo de pre-fase + re-corrida C1–C4).