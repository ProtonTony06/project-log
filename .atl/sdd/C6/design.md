---
change: C6
artifact: design
project: Project_Log
last_updated: 2026-10-25
status: draft
---

# Design — C6 · Consulta keyset + filtros + detalle

> Fase sdd-design del corte C6. Este documento **IMPLEMENTA
> arquitectónicamente** el spec (`spec.md`, status draft) — no lo renegocia.
> Fuentes normativas: spec C6 (R6.1–R6.29, cierres S6-1…S6-10, D-C6-1,
> escenarios E6.1–E6.23) + proposal C6 (status **approved** — gate 6/6 del
> 2026-10-25) + código real verificado en HEAD `43ea041` (2026-10-25):
> `ProjectsController`, `ProjectsRepository`, `ProjectDto`,
> `LoginController` (EXPIRES_AT_FORMATTER), `ingest/LogsRepository`
> (frontera INSERT-only), `SecurityConfig` (cadena 2), `V1__init.sql`
> (índices), `LogsRepoSqlTest`, `ProjectsKeySqlTest`,
> `ProjectsControllerMvcTest`, `ProjectDtoSerializationTest`,
> `application.yml` (SIN config Jackson → nulls incluidos por defecto).
> Stack: Spring Boot 3.5.16 / Java 21 / PG17 — **cero cambios de pom,
> cero migraciones, NINGÚN fichero existente modificado** (R6.3, ratificado
> abajo §12). Único fichero tocado por C6: este design (documentación).

## 0. Resumen del diseño

```
GET /api/v1/panel/logs[/{id}]          (cadena 2 @Order(2) — SIN toque)
        │  JwtAuthFilter → 401 invalid_token ante sin-JWT (RestAuthenticationEntryPoint, CD-4)
        ▼
LogsController (query/controller/)     ── 8 checks fail-fast inline (D-6.2)
        │   FiltrosConsulta.patronIlike (texto ya escapado+envuelto)
        │   CursorCodec.decodificar → Cursor(Instant r, long i)
        ▼
LogsQueryRepository (query/repository/) ── JdbcTemplate + binding POSICIONAL
        │   SELECT_LISTADO (17 ?, D-C6-1 CASTs) / SELECT_POR_ID
        ▼
FilaConsulta(item, received_at RAW, id) ── trim limit / decidir next_cursor
        ▼
CursorCodec.codificar(received_at real, id) → Base64url JSON {"r","i"} SIN truncar
        ▼
LogsPaginaResponse(items, next_cursor) → 200 {items, next_cursor}
```

Una sola sentencia SQL para TODAS las combinaciones de filtros (E1 del
explore ratificada); cursor opaco sin pérdida de micros (P1); 2 records
DTO (P5); todo el paquete nuevo en `com.projectlog.query` con
sub-paquetes al patrón `projects` (S6-1).

## 1. Mapa del paquete `com.projectlog.query`

Todos los paths bajo `backend/src/main/java/com/projectlog/`. PIEZAS
EXACTAS (cierre S6-1; `query/package-info.java` queda **byte-a-byte** —
R6.3: su texto YA declara el rol C6):

| Pieza (NUEVA) | Contenido | Ancla |
|---|---|---|
| `query/CursorCodec.java` | Clase `public final` con SOLO métodos static: `codificar(OffsetDateTime receivedAt, long id)` → String; `decodificar(String token)` → `Cursor`; anidados `record Cursor(Instant r, long i)` y `static final class CursorInvalidoException extends RuntimeException`. Un `ObjectMapper` static PROPIO del codec (SOLO para decode; el encode es composición manual — D-6.3). Cero Spring, cero BD | R6.15, S6-3 |
| `query/FiltrosConsulta.java` | Clase `public final` static pura: `patronIlike(String texto)` (única pieza — NO lleva el parseo: D-6.2). Seam C7 §3.5 | R6.20, S6-1/S6-5 |
| `query/controller/LogsController.java` | `@RestController @RequestMapping("/api/v1/panel/logs")` con EXACTAMENTE 2 mappings (`@GetMapping`, `@GetMapping("/{id}")`); los 8 checks inline; constants `CODE_*`/`MSG_*`; helper `error(...)` private réplica de C3 | R6.5/R6.7/R6.8/R6.21–R6.23 |
| `query/dto/LogListItemDto.java` | Record de 9 componentes con nombres literales §3.3 (`id, project_id, level, environment, timestamp, received_at, message, source, correlation_id`) | R6.9, P5 |
| `query/dto/LogDto.java` | Record de 11 (los 9 + `exception: String` + `metadata: JsonNode` — D-6.9); `exception`/`metadata` nullables | R6.9/R6.10, P5 |
| `query/dto/LogsPaginaResponse.java` | Record `LogsPaginaResponse(List<LogListItemDto> items, String next_cursor)` | R6.11 |
| `query/repository/LogsQueryRepository.java` | `@Repository` con `JdbcTemplate` (+ `ObjectMapper` por constructor para el readTree del detalle); constants `static final String` **package-private** `SELECT_LISTADO`/`SELECT_POR_ID`; 2 `@Transactional` mono-sentencia; RowMappers `FILA_LISTADO`/`FILA_DETALLE`; record anidado `FilaConsulta(item, received_at, id)` | R6.16–R6.19 |

INTOCABLES (frontera R6.3): `ingest/**` (incl. `LogsRepository` y
`LogValidado` — sin NI UN método de lectura nuevo), `config/**`
(SecurityConfig/CorsConfig/WebSocketConfig/RestAuthenticationEntryPoint),
`auth/**` (`EXPIRES_AT_FORMATTER` reutilizado por REFERENCIA — jamás
editado), `keys/**`, `projects/**` (solo lectura del patrón),
`streaming/**`, `common/**`, `V1__init.sql`, `pom.xml`,
`application*ml`, `compose.yaml` y TODOS los tests de C1–C5.
**`@PreAuthorize` NO se usa** (D-6.7): `grep` en HEAD confirma que la
casa no tiene ni uno; la cadena 2 (`securityMatcher("/api/v1/panel/**")`,
`@Order(2)`, `anyRequest().authenticated()`) cubre ambos paths natos.

## 2. Micro-decisiones numeradas (D-6.1…D-6.11)

| # | Decisión | Alternativa descartada | Rationale |
|---|---|---|---|
| **D-6.1** | Acceso a datos = `JdbcTemplate` con **binding POSICIONAL** (`Object[]` en el orden EXACTO de los `?`, patrón `LogsRepository.insert` R5.11-d). Los 2 estamentos como constants `static final String` **package-private** en `LogsQueryRepository`; el pin `LogsQuerySqlTest` vive en el MISMO paquete de test `query/repository` — sin reflection, sin BD | `NamedParameterJdbcTemplate` (descartado en el spec, S6-4: no existe en la casa); SQL dinámico (E1 ratificada: convención C3 prohibida) | Es EXACTAMENTE lo que YA usan `ProjectsRepository` (C3–C5) y `ingest/LogsRepository` (C5). Ni una librería nueva (R6.2). El patrón pin del mismo paquete es el probado en `LogsRepoSqlTest`/`ProjectsKeySqlTest` (D-9 de C5) |
| **D-6.2** | El parseo de los 8 query params vive **INLINE en `LogsController.listar`** como cadena de 8 `if`s de retorno temprano (patrón EXACTO `ProjectsController.listar`/`crear`: checks manuales con literal). `FiltrosConsulta` NO recibe un `parse()`: queda pieza de UNA función (`patronIlike`) tal como R6.1 la define | Mover el parseo a un `parse()` static de `FiltrosConsulta` que devuelva el params-parsed record | LO MÁS SIMPLE coherente con el patrón de casa: C3/C5 ponen la validación fall-fast en el controller y las constants `CODE_*`/`MSG_*` junto a su gate-owner; un record intermedio de params no aporta (el flujo pasa DIRECTO del parse a la llamada del repo — R6.24) y duplicaría las firmas en test MVC. Si C7 reutiliza el parseo del stream, extraerlo ENTONCES es una línea de refactor (seam real: `patronIlike` YA queda reutilizable hoy) |
| **D-6.3** | `CursorCodec`: firma EXACTA del spec R6.15 (es la ley). `codificar(OffsetDateTime receivedAt, long id)` — NO el orden `(id, instant)`: el par se lee de `FilaConsulta` tal cual (offset de PG por el driver) y el Javadoc se ancla a §3.3 + R-6.a. `decodificar(String token)` con los 6 pasos ESTRECHOS → `Cursor(Instant r, long i)` → `CursorInvalidoException` única, SIN oráculo de causa | Sugerencia del encargo de design con firma `(long id, Instant instant)` — queda sin efecto (el spec la fija al revés); un `ObjectMapper. writeValueAsString(Map)` en el encode | `Map.of` no garantiza el orden `r`,`i` del JSON — la composición manual es BYTE-EXACTA con el ejemplo del Schema (sonda P-1) y determinista. P-1 es el pin de encode de E6.12b |
| **D-6.4** | La `Cursor(Instant r, long i)` se convierte A `OffsetDateTime` al pasar al repo: `OffsetDateTime.ofInstant(c.r(), ZoneOffset.UTC)` (sin pérdida — el Instant lleva los nanos y el offset neutro). El repo firma `cursorR: OffsetDateTime` (patrón del driver) | Cambiar la firma del repo a `Instant` | El driver envía TIMESTAMPTZ al comparar con `received_at` (TRD §2.6); `from`/`to` ya viajan como `OffsetDateTime` (R6.17) — UNA única convención de instante-tipo en el binding. La conversión vive en el controller (el repo NUNCA interpreta tokens) |
| **D-6.5** | El `metadata` del select del detalle ES `rs.getString("metadata")` → `objectMapper.readTree(...)` → `JsonNode` (NUNCA String crudo, NUNCA `readValue(Map.class)`) — R6.10 verbatim. El `readTree` es RuntimeException si el texto viniera malformado (imposible por construcción: jsonb solo guarda JSON válido) → 500 honesto (política S6 de C4) | `PGobject`/`Map` en el record | El record `LogDto` serializa el `JsonNode` tal cual (objeto EXACTO — claves del jsonb intactas). El `CAST(? AS jsonb)` de C5 solo es necesario en INSERT; en SELECT pgjdbc entrega el TEXTO por defecto (sonda y patrón del driver) |
| **D-6.6** | `error(...)` private con firma `error(HttpStatus, String codigo, String mensaje, String... campo)` — réplica EXACTA de C3 (`LinkedHashMap`: `code`, `message`, `field` SOLO si aplica; `objectMapper.valueToTree`; `MediaType.APPLICATION_JSON`). El `MSG_NOT_FOUND` se duplica como constant `static final` del paquete query (el LITERAL es el MISMO — patrón del entry point de C5: cada escritor con sus constants) para que query NO dependa de `projects.*` | Importar `MSG_NOT_FOUND` de ProjectsController (paquete distinto — acoplamiento cross-feature) | R6.21/S6-8 lo fija. Con varargs el call-site del `filter_from_to_inverted` (SIN `field`) y del 404 queda IDÉNTICO (llamada de 3 args) |
| **D-6.7** | Seguridad: NINGÚN `@PreAuthorize`, NINGÚN handler de auth, NINGÚN toque a `SecurityConfig` — la firma del corte es: los endpoints NACEN tras la cadena 2 (E6.15 lo pina en test con `@Import(SecurityConfig, JwtAuthFilter, RestAuthenticationEntryPoint, CorsConfig)`; el smoke lo pina con el sondeo `GET /me` sin token) | Añadir `@PreAuthorize("hasRole(...)")` o `hasRole` en la cadena | La casa usa cadenas de matcher + `anyRequest().authenticated()` SIN roles (ADR-14, S20); C3/C5 nacieron igual. Añadir anotación sería un literal SIN ancla |
| **D-6.8** | El recorte `LIMIT+1` del keyset: el repo devuelva la Lista CRUDA de hasta `limit+1` `FilaConsulta`; trim y `next_cursor` viven EN el controller (R6.24): `items = filas.subList(0, limit)`; `codificar(filas.get(limit-1).received_at(), filas.get(limit-1).id())` — la ÚLTIMA ENTREGADA, jamás la extra | Recorte+cursor dentro del repo | R6.18(c) lo fija (frontera de capas); el controller es el único que decide "fin de resultados". El `subList` es una VISTA sobre DTOs ya mapeados: la fila extra se descarta sin transportarla (R6.24) |
| **D-6.9** | `LogDto.metadata` se declara **`JsonNode`** (R6.10: "DEBE ser JsonNode"). El bloque de código del spec R6.9 lo escribe `Object metadata /*JsonNode*/` como boceto — R6.10 es el cierre de tipado y PREVALECE (el pin E6.2 es del tipo) | Dejarlo `Object` y cast en el mapper | `JsonNode` serializa el objeto EXACTO sin pasos; `Object` + `Map` fabricaría re-interpretación (prohibida). Sin divergencia: es cerrar el hueco `/*…*/` del propio spec |
| **D-6.10** | La validación de `text`/`cursor` NO lleva check de `length()` (sin cap, S6-10 — "límites sin ancla NO se inventa"): el valor pasa TAL CUAL a `patronIlike`/`decodificar`; un `text`="" produce patrón `"%%"` legal (S6-2); el `from` naive (sin offset) lo RECHAZA `OffsetDateTime.parse` (patrón S5-5 — ya operado así en C5) | Cap de longitud o trim/normalización case | Sin ancla en el congelado; cualquier check nuevo sería literal sin ancla (CD-3.9). El smoke lo ejercita con agujas reales |
| **D-6.11** | En test MVC, la aserción del E6.1 sobre el `next_cursor` se materializa como DECODIFICACIÓN DE VUELTA: captura del token del JSON de respuesta + `CursorCodec.decodificar(token)` == `(r, i)` de la fila `limit-1` del mock (el "ArgumentCaptor del codec" del spec no es captorable tal cual — codec static puro; no se usa `mockito.mockStatic` ni reflection) | `mockStatic` de Mockito; hacer el codec instancia-mockeable (rompería R6.15: "SOLO métodos estáticos... puro") | La codificación es DETERMINISTA (byte-exacto, pin P-1): decodificar lo emitido verifica exactamente la MISMA propiedad (que el par de la última fila entregada alimenta el cursor) sin acoplar el test a static-mocking |

## 3. `CursorCodec` — el viaje EXACTO de los micros (P1 / R-6.a)

Flujo del valor SIN truncar (la línea de vida del corte):

```
[PG] received_at TIMESTAMPTZ (micros, regla 9: 1 batch = 1 instante)
  │  pgjdbc preserva los micros
  ▼
RowMapper: rs.getObject("received_at", OffsetDateTime.class)  ← R6.15-c
  │  (JAMÁS pasa por EXPIRES_AT_FORMATTER antes del codec)
  ├──────────────► item.received_at = raw.format(EXPIRES_AT_FORMATTER)
  │                     (render Z truncado a SEGUNDOS — pantalla ratificada, R-6.i)
  ▼
FilaConsulta(item, received_at=RAW, id)     (lista CRUDA de limit+1 del repo)
  │  controller: si hay fila extra y tras el trim a limit:
  ▼
CursorCodec.codificar(filas.get(limit-1).received_at(),  ← el Instant REAL
                      filas.get(limit-1).id())             — jamás el String renderizado
  │  (1) r = receivedAt.toInstant().toString()    ← ISO-8601, micros SI
  │  (2) JSON compuesto manual: {"r":"<r>","i":<id>}  ← orden determinista
  │  (3) Base64.getUrlEncoder().withoutPadding()    ← SIN padding
  ▼
next_cursor (String) → LogsPaginaResponse
```

Invariante ÚNICO (lo que el pin E6.12 y el smoke E6.17 certifican): la
clave `r` llega a la rama keyset de la SIGUIENTE página EXACTA al micro
del `received_at` real de la última fila entregada; el formatter de C2
(truncador) queda EXCLUÍDO del trayecto cursor (D-6.3/R6.15-c). Con
truncado, las filas del MISMO batch dejarían de cumplir
`received_at = :cursor_r` (su instante > truncado) NI
`received_at < :cursor_r` → salto literal del Done.

ENCODE (fórmula cerrada, S6-3/R6.15-a): `Instant.toString()` produce la
longitud VARIABLE correcta (sin fracciones → `2026-10-16T22:31:04Z`;
con micros → `...04.123456Z`) — byte-exacto con el ejemplo del Schema:
`codificar(OffsetDateTime.parse("2026-10-16T22:31:04Z"), 12301)` ==
`eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0`. El instante
ISO-8601 no puede contener `"` ni `\` → la composición manual es segura
(sin escaping).

DECODE (tolerancia ESTRECHA, R6.15-b — 6 pasos EN ORDEN, cualquier fallo
→ LA MISMA `CursorInvalidoException`): (1) `null`/vacío → inválido;
(2) `'='` en el token → inválido (anti-padding EXPLÍCITO; el alfabeto
clásico `+`/`/` lo rechaza el propio `getUrlDecoder()` al decodificar);
(3) `Base64.getUrlDecoder().decode` → fallo → inválido;
(4) `readTree` con el `ObjectMapper` static del codec → no-JSON →
inválido; (5) estructura estrecha: raíz objeto; EXACTAMENTE 2 claves
`r`+`i` (extras → inválido); `r` `isTextual()` parseable con
`Instant.parse` (SOLO UTC-Z: `+02:00` NO lo admite — el codec propio
jamás lo emite); `i` `isIntegralNumber()`, `longValueExact()` (overflow
→ inválido) y `>= 1`; (6) `Cursor(r, i)` sin re-derivación. El instante
decodificado NUNCA se valida contra la BD: `r` en el futuro → 200 con
`items: []` + `next_cursor: null` (keyset legítimo, cierre §8.2).

Threading: `CursorCodec` y `FiltrosConsulta` son static puro SIN estado
compartido mutable (el `ObjectMapper` static es thread-safe — uso
documentado). El controller y el repo son beans sin estado; la
paginación NO guarda estado de sesión (el "estado" del recorrido vive
ÍNTEGRAMENTE en el token opaco del cliente — stateless de la cadena 2,
sin `HttpSession`).

## 4. `LogsController` — fail-fast de 8 checks y flujo R6.24

`listar` (los 8 params todos `@RequestParam(required = false) String`
— binding directo tipado daría el 400 genérico de Spring, R-6.c):

```java
@GetMapping
public ResponseEntity<Object> listar(@RequestParam(required = false) String projectId,
        String environment, String level, String from, String to, String text,
        String cursor, Integer limit)   // limit NO binding tipado: check 1 manual
```

LOS 8 CHECKS EN ORDEN (R6.8 — TODOS antes del repo, primer-fallo-gana,
`verifyNoInteractions` en test E6.10) — cada uno con su
code/message/field de §8 (tabla de R6.22, EXACTA):

| # | check | code | field |
|---|---|---|---|
| 1 | `limit` presente y (no-numérico — LONG.parseLong en try — incl. `""` vacío — u fuera de 1..200) | `filter_limit_invalid` | `limit` |
| 2 | `project_id` presente no-numérico (`Long.parseLong` en try) | `filter_invalid_value` | `project_id` |
| 3 | `environment` fuera de `development|staging|production` (case-sensitive; `""` → 400) | `filter_invalid_value` | `environment` |
| 4 | `level` fuera de `TRACE|DEBUG|INFO|WARN|ERROR|FATAL` (idem) | `filter_invalid_value` | `level` |
| 5 | `from` presente NO parseable (naive incluido) — `OffsetDateTime.parse` | `filter_invalid_value` | `from` |
| 6 | `to` presente NO parseable | `filter_invalid_value` | `to` |
| 7 | `from.toInstant().isAfter(to.toInstant())` (`from == to` LEGAL — punto) | `filter_from_to_inverted` | — SIN field |
| 8 | `cursor` ilegible (CUALQUIER fallo de los 6 pasos) — `try { CursorCodec.decodificar(t) } catch (CursorInvalidoException)` | `filter_cursor_invalid` | `cursor` |

`text` NO lleva check (sin cap — D-6.10). Tras los 8 (D-6.2/D-6.8):
`patron = FiltrosConsulta.patronIlike(text)`; llamada ÚNICA a `listar(...)`;
(`cursor` presente) `offset = OffsetDateTime.ofInstant(cr.r(), ZoneOffset.UTC)`;
si `filas.size() > limit` → trim + `codificar(última entregada)`; si no →
`next_cursor = null` y `items = filas`; `ResponseEntity.ok(pagina)`. El
listado VACÍO sin cursor es `200 {"items": [], "next_cursor": null}`
(R6.12 — NUNCA 404/400).

`/{id}` (detalle, sin checks de query): `porId(id)` → 200 `LogDto`
| `Optional.empty()` → 404 `{code: not_found, message: "No existe
(quizás fue eliminado)"}` sin `field`. El `/logs/abc` (id no-numeric)
queda RATIFICADO sin toque: Spring no mapea la variable y responde su
404/400 de matching — el patrón de `GET /projects/{id}` ya operado así
(R6.6; sin handler, sin escenario).

## 5. `LogsQueryRepository` — estamentos (D-C6-1) y binding

El literal `SELECT_LISTADO` es el del spec R6.16 TAL CUAL (pinnado por
E6.11), con las 2 divergencias mínimas DECLARADAS en D-C6-1 citadas EN
SU JAVADOC: (a) los 7 CASTs en las ocurrencias IS-NULL
(`bigint`/`text`/`text`/`timestamptz`/`timestamptz`/`text`/`timestamptz`
— PG17 rechaza `could not determine data type of parameter $1` sin
CAST, sondas P-2/P-3; precedente de casa R-4 de C5: el CAST ENTRA al
literal pinado); (b) la GUARDA del keyset `(CAST(? AS timestamptz) IS
NULL OR (received_at < ? OR (received_at = ? AND id < ?)))` — la rama
desnuda del congelado evalúa NULL ante params NULL y EXCLUIRÍA TODAS
las filas de la primera página (sondas P-0; AppFlow flujo 4 paso 1 la
exige existente). SIN `ESCAPE '\'` explícito (S6-5 — el `\` es el
escape DEFAULT de PG; el pin NO contiene la palabra ESCAPE). Sin SQL
dinámico en NINGÚN punto: la primera página es EL MISMO estamento con
los params del keyset NULL.

`SELECT_POR_ID`: las 11 columnas EXACTAS (`exception` y `metadata` SÍ
presentes) + `WHERE id = ?`. `NUNCA queryForObject` en las 2 lecturas
(la fila-0 es el caso LEGAL del 404; patrón `findById` de C4).

**ORDEN DE BINDING — 17 `?` (contados del literal R6.16):**
`projectId, projectId, environment, environment, level, level, from,
from, to, to, patronTexto, patronTexto, cursorR, cursorR, cursorR,
cursorI, limit` — los 6 valores de filtro en sus 2 posiciones
(IS-NULL-CAST / igualdad); `cursorR` en sus **3** posiciones (guard IS
NULL + `<` + `=`); `cursorI` en 1 (el contexto de la columna `id`
infere el tipo); `limit` al FINAL (el `LIMIT ? + 1` es VERBATIM —
sonda P-5: válido). `projectId`/`cursorI` = `Long` (null sin
filtro); `from`/`to`/`cursorR` = `OffsetDateTime`; `patronTexto` =
String YA ESCAPADO+ENVUELTO (`%needle%`); `limit` = int (el Java NO
suma +1 — el `+ 1` vive en el literal). El Java del repo es el
`Object[]` varargs de `jdbcTemplate.query(sql, FILA_LISTADO,
parametros)`.

RowMappers (2, SEPARADOS — R6.14; jamás un mapper recortado compartido):
`FILA_LISTADO` → `FilaConsulta(new LogListItemDto(...9...), raw
received_at, id)` con las 2 fechas del item formateadas con
`LoginController.EXPIRES_AT_FORMATTER` (reutilizado por referencia,
jamás editado); `FILA_DETALLE` → `LogDto` de 11 con
`rs.getString("exception")` (nullable) y metadata → `readTree` (D-6.5).
El repo NO valida NADA y NO toca la tabla `projects` (FK CASCADE, regla
3; el nombre del proyecto NO viaja — AppFlow flujo 4 paso 3).

## 6. Errores — tabla code → message (constants, S6-6)

En `LogsController` como `static final String` (package-private para
los pins, patrón C3 `CODE_*`/`MSG_*`): los 5 `CODE_*` y los 7 `MSG_*`
con los LITERALES EXACTOS de la tabla R6.22 (`El rango temporal no es
válido: from es posterior a to`; `Valor de environment no válido; use
development, staging o production`; `Valor de level no válido; use
TRACE, DEBUG, INFO, WARN, ERROR o FATAL`; `El filtro project_id debe
ser un número`; `El filtro from/to debe ser una fecha ISO-8601 con zona
horaria`; `El filtro limit debe ser un número entre 1 y 200`; `El
parámetro cursor no es válido`; `No existe (quizás fue eliminado)`) —
NINGÚN code nuevo fuera de esa tabla (vocabulario cerrado §3.1; los 3
cases de P2 quedan reutilizar-EXISTENTES según gate). Los body de E6.4–
E6.9 y el smoke E6.21 los comparan TAL CUAL.

## 7. Tests [L] — 5 ficheros en `backend/src/test/java/com/projectlog/query/`

| Fichero | Escenarios | Patrón / notas |
|---|---|---|
| `controller/LogsControllerMvcTest.java` | E6.1, E6.3–E6.10, E6.14, E6.15 | `@WebMvcTest(LogsController.class)` + `@Import({SecurityConfig.class, JwtAuthFilter.class, RestAuthenticationEntryPoint.class, CorsConfig.class})` + `@MockitoBean LogsQueryRepository` + `@MockitoBean TokenService` + `@ActiveProfiles("test")` — fixture EXACTO de `ProjectsControllerMvcTest` (JWT real con `TokenService(SECRETO_TEST, 8).issue(...)`, `bearer()`, `verifyNoInteractions`). El "ArgumentCaptor del codec" de E6.1 se materializa COMO DECODIFICACIÓN DE VUELTA (D-6.11): el codec es static (no-Captor-able, no mock sin static-mocking) — se captura el `next_cursor` del JSON de respuesta y `CursorCodec.decodificar(next_cursor)` == (r, i) de la fila 50 del mock; misma aserción de igualdad, determinista |
| `dto/DtosLogsPinTest.java` | E6.2 | Patrón `ProjectDtoSerializationTest` (JUnit puro): 9 / 11 componentes con nombres EXACTOS via reflection de componentes del record + serialización de claves; `metadata` ES `JsonNode` (pin del tipo) |
| `CursorCodecTest.java` | E6.12 | Unit puro SIN Spring/BD: round-trips con micros, pin byte-exacto P-1, sin-fracciones/nanos, y el anti-truncado (recodificar tras truncar → token DIFERENTE) |
| `FiltrosConsultaTest.java` | E6.13 | Unit puro: `"a_b"` → `%a\\_b%` (Java-literal `%a\_b%`), `"100%"` → `%100\%%`, `"a\b"` → `%a\\b%`... — ORDEN `\` PRIMERO pin con texto con `_` y `\` juntos; contra-caso de la aguja (`patronIlike("smoke_c6_X")` ≠ patrón del needle sin `_`) |
| `repository/LogsQuerySqlTest.java` | E6.11 | MISMO paquete que el repo (constants package-private, patrón `LogsRepoSqlTest`/D-9): pin literal EXACTO de los 2 estamentos (los 7 CASTs en sus posiciones, guard keyset completa, ORDER BY, `LIMIT ? + 1` verbatim, count de `?`), pin de NO-"ESCAPE", y pin de que el listado NO menciona `exception`/`metadata` |

NO hay fichero de higiene nuevo (R6.26: no hay secretos en las lecturas
— el smoke de C5 ya cubre la superficie de ingesta; ningún logger en
las clases nuevas). El smoke `scripts/qa_c6_smoke.sh` es NUEVO [S]
(patrón `qa_c5_smoke.sh`): recomposición ÚNICA `podman compose up -d
--force-recreate` (NUNCA `down -v` — pgdata con las 1877 filas
heredadas, R6.28), psql `-qAt`, conteos RELATIVOS antes/después (R-6.h),
needles `smoke-c6-<TS>-*` con escape psql (`\_`, comparación por `=` /
`position()` — R6.20-a), tmpdir 700 con trap (JWT y key SOLO a tmpdir),
smoke-of-the-smoke (sondeo `GET /me` sin token → 401 `invalid_token`
— pareja [S] de E6.15) y los 8 escenarios E6.16–E6.23 del §2.5 del spec
con salida `E6.n: PASS | FAIL | BLOQUEADO (detalle)` y exit 0/1/2.

## 8. Ficheros tocados por C6 (promesa R6.3 confirmada)

| Fichero | Acción |
|---|---|
| `query/CursorCodec.java`, `query/FiltrosConsulta.java`, `query/controller/LogsController.java`, `query/dto/{LogListItemDto,LogDto,LogsPaginaResponse}.java`, `query/repository/LogsQueryRepository.java` | **CREATE** (7) |
| `backend/src/test/java/com/projectlog/query/{controller/LogsControllerMvcTest, dto/DtosLogsPinTest, CursorCodecTest, FiltrosConsultaTest, repository/LogsQuerySqlTest}.java` | **CREATE** (5) |
| `scripts/qa_c6_smoke.sh` | **CREATE** |
| `query/package-info.java` + TODO lo del mapa de §1 bajo "INTOCABLES" | **NINGÚN diff** (0 modificados, 0 borrados) |

## 9. Divergencias con spec.md (declaradas — no corregidas allí)

- **D-S6-a (inconsistencia INTERNA del spec §1.G, resuelta por el literal):** R6.17/E6.11 declaran "**16** placeholders" y enumeran una lista que omite la 3.ª ocurrencia de `cursorR`, pero el PROPIO literal pinado de R6.16 (que el E6.11 pin) tiene **17 `?`** y la PROPIA prosa de R6.17 dice "`cursorR` en sus 3 posiciones". El design implementa el LITERAL (17: los 12 de los 6 filtros + 3 de `cursorR` + 1 de `cursorI` + 1 de `limit`) y el pin de E6.11 contará 17. El texto "los 7 valores de filtro van duplicados" de R6.17 es el mismo desajuste (son 6 filtros duplicados + cursorR triplicado). No altera NINGÚN literal de API ni la semántica; sdd-tasks llevará la cuenta 17 en el pin.
- **D-S6-b (aclaración del E6.1):** el "ArgumentCaptor del codec" del spec no es captorable tal cual (codec static puro — D-6.11). Se materializa como aserción de DECODIFICACIÓN DE VUELTA del `next_cursor` emitido contra el par (r, i) de la fila limit-1 del mock — misma equivalencia verificada, sin mockito-mockStatic ni reflection.
- **D-S6-c (aclaración del R6.9/R6.10):** el boceto `Object metadata` del R6.9 se cierra como `JsonNode` por el PROPIO R6.10 (D-6.9) — el pin E6.2 pina el tipo.
- Contra el CONGELADO existe SÓLO D-C6-1 (ya declarada y cerrada EN el spec con sondas P-2…P-5): el design la IMPLEMENTA (los CASTs + la guarda) y manda citarla en el Javadoc del estamento y en el pin E6.11 — SIN nueva divergencia.

## 10. Riesgos del diseño (D-Rx; los del spec + los propios)

| ID | Riesgo | Impacto | Mitigación en diseño |
|---|---|---|---|
| **D-R6.a** (spec R-6.a) | Cursor truncado a segundos SALTARía filas del mismo instante (regla 9) — Done roto | CRÍTICO | §3: el `r` viaja del pgjdbc → `Instant.toString()` SIN pasar por el formatter (R6.15-c); pin E6.12 byte-exacto; smoke E6.17 con batch que separa páginas |
| **D-R6.b** (spec R-6.b) | Comodines del cliente en ILIKE — R-6.b producto, no solo smoke | ALTO | `patronIlike` SERVER-SIDE OBLIGATORIO, orden `\`→`%`→`_` PINNED (E6.13) + smoke E6.18 con aguja `_` real y contra-caso (cierre S-3 de C4 citado en el archive si E6.18 pasa) |
| **D-R6.g** (spec R-6.g) | Fuga de `exception`/`metadata` en la LISTA | ALTO | D1/P5: 2 records estructurales; el list-item NO LLEVA los campos — imposible de compilar; pin E6.2 lo afirma |
| **D-R6.f** (spec R-6.f) | El estamento real puede fallar aunque todos los mocks pasen | MEDIO | Pin SQL literal (E6.11, 17 `?` counted) + ejecución real certificada por el smoke con los 7 CASTs contra PG17 (siguiendo los P-2…P-5 del spec) |
| **D-R6.1 (nuevo)** | La cuenta de placeholders del spec (16) vs el literal (17) si tasks/copiara la lista de R6.17 tal cual → `index out of range`/binding descolgado | BAJO | Divergencia D-S6-a: el pin E6.11 ANCLA la cuenta a 17 DESDE el literal; tasks lo cita |
| **D-R6.2 (nuevo)** | `ObjectMapper` static dentro del `CursorCodec` en el contexto de test MVC (el bean del contexto es otro) | BAJO | Es el REQUERIMIENTO del spec (R6.15-b paso 4): el codec NO usa el bean (es static puro); no acopla al contexto — testable sin Spring (E6.12) |
| **D-R6.d / R-6.e / R-6.h / R-6.i** | Heredados del spec (cursor con filtros cambiados — NO se rechaza, R-6.d documentado; degradación "logs_received_idx + scan filtrado" aceptada por literal; conteos relativos en smoke; render truncado en pantalla ratificado) | BAJO | Documentados tal cual; C6 NO TOCA índices NI formatter |

## 11. Trazabilidad design → spec

| Elemento del design | Anclas |
|---|---|
| Mapa del paquete (§1) | S6-1, R6.1, R6.2, R6.3, P-x S6-1 |
| `CursorCodec` (§3) | R6.15/R6.15-a/b/c, S6-3, P1, R-6.a, P-1; E6.9, E6.12, E6.17 |
| `FiltrosConsulta` (§1, §4) | R6.20, S6-5, P3; E6.13, E6.18 |
| Fail-fast 8 checks + constants + errores (§4, §6) | R6.7/R6.8/R6.21/R6.22/R6.23, S6-6/S6-7, P2, P6; E6.5–E6.10, E6.21, E6.22 |
| `LogsQueryRepository` + binding (§5) | R6.16/R6.17/R6.18/R6.19, S6-4, D-C6-1; E6.11, E6.16, E6.19, E6.20 |
| DTOs y wrapper (§1) | R6.9–R6.14, P5, S6-10; E6.1–E6.3, E6.14 |
| Trim/next_cursor (§4) | R6.24; E6.1, E6.23 |
| Seguridad sin toque (§1, §2-D-6.7) | R6.5, S19/E6.15, CD-4; cadena 2 de `SecurityConfig` intocada |
| Threading/stateless (§3) | R6.8 (contrato), chain 2 stateless, sin sesión |
| Suite [L] + smoke (§7) | R6.25/R6.26/R6.27/R6.28; E6.1–E6.15, E6.16–E6.23 |
| No-regresiones (§8) | R6.29 (108/108 + N; `LogsRepoSqlTest` re-corrida intocada) |

## 12. Fuera de alcance (heredado del spec §4 — el design NO lo expande)

Streaming C7 (`IngestListener` sin beans suscriptos), flujo 6b del
cliente, resumen/DELETE (C10), front (C8), nginx/compose (C9),
migraciones y pg_trgm (V2 reservado y comentado), Testcontainers (P4 —
reevaluar en C7), rate limiting/retención/multiusuario, normalización
case de enums, caps de `text`/`cursor`. Deudas RETENIDAS sin toque:
smoke docker compose canónico (C9); 401 ISO-8859-1 (C8/C9);
`client_max_body_size` (C9).