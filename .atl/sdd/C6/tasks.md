---
project: Project_Log
change: C6
artifact: tasks
last_updated: 2026-10-25
status: draft
---

# Tasks — C6 · Consulta keyset + filtros + detalle

> Fase sdd-tasks del corte C6. Fuente normativa: `spec.md` C6 (R6.1–R6.29,
> cierres S6-1…S6-10, D-C6-1, E6.1–E6.23) y `design.md` C6 (D-6.1…D-6.11,
> divergencias D-S6-a/b/c). Ningún literal de API se redefinirá en apply:
> los códigos/mensajes/estamentos del spec se copian VERBATIM. El spec y el
> design NO se editan; divergencia pendiente: NINGUNA bloqueante
> (ver "Notas de divergencia" abajo).

## Frontera (vale para TODAS las tareas — R6.3/R6.2)

- **Toda pieza de backend C6 es NUEVA**: nada de C1–C5 se toca. EN
  NINGUNA tarea se edita: `SecurityConfig`, `CorsConfig`, `RestAuthenticationEntryPoint`,
  `application.yml` / `application-test.yml` / `.env.example` / `compose.yaml`,
  `resources/db/migration/V1__init.sql`, `backend/pom.xml`, `query/package-info.java`
  (byte-a-byte), `ingest/**` (incl. `LogsRepository`, `LogValidado`),
  `auth/**` (incl. `LoginController.EXPIRES_AT_FORMATTER` — reutilizado por
  REFERENCIA, jamás editado), `keys/**`, `projects/**` (solo lectura del
  patrón), `streaming/**`, `common/**`, docs/ y front/, y TODOS los tests
  de C1–C5. La verificación formal es T6.25.
- Piezas NUEVAS (paths bajo `backend/src/main/java/com/projectlog/`, cierre
  S6-1): `query/CursorCodec.java`, `query/FiltrosConsulta.java`,
  `query/controller/LogsController.java`, `query/dto/LogListItemDto.java`,
  `query/dto/LogDto.java`, `query/dto/LogsPaginaResponse.java`,
  `query/repository/LogsQueryRepository.java`; tests bajo
  `backend/src/test/java/com/projectlog/query/` y `scripts/qa_c6_smoke.sh`.

## Orden de dependencias

DTOs (T6.1–T6.3) → CursorCodec (T6.4) → FiltrosConsulta (T6.5) →
LogsQueryRepository (T6.6 → T6.7, mismo fichero secuencial) →
LogsController (T6.8 → T6.9 → T6.10 → T6.11, mismo fichero secuencial) →
pin E6.2/E6.12/E6.13 (unit, T6.12–T6.14) → pin SQL E6.11 (T6.15) →
MVC test fixture+401 (T6.16 → T6.17 → T6.18 → T6.19 → T6.20, mismo
fichero secuencial) → `mvn test` completo pre-smoke (T6.21) → smoke
script (T6.22) → ejecución smoke (T6.23) → `mvn test` post-smoke (T6.24) →
verificación final de frontera (T6.25).

---

## Bloque 1 — DTOs (records P5; R6.9–R6.14)

### T6.1 — Crear `dto/LogListItemDto.java` (9 componentes)

- **Fichero**: NUEVO `backend/src/main/java/com/projectlog/query/dto/LogListItemDto.java`.
- **Qué hace**: `public record LogListItemDto(Long id, Long project_id,
  String level, String environment, String timestamp, String received_at,
  String message, String source, String correlation_id)` — EXACTAMENTE 9
  componentes con los NOMBRES LITERALES de Schema §3.3 (`project_id`,
  `correlation_id`): Jackson serializa el accessor con SU nombre sin
  `@JsonProperty` (patrón `ProjectDto`). JAMÁS `exception` ni `metadata`
  (la fuga de la lista queda IMPOSIBLE de compilar — P5/D1). Fechas como
  `String` ya renderizadas (el RowMapper formatea — R6.13).
- **Criterio de hecho**: el fichero compila y el record declara 9 y solo
  9 componentes (lo pina E6.2 en T6.12).
- **Escenarios**: E6.2 (y base de E6.1/E6.16).

### T6.2 — Crear `dto/LogDto.java` (11 componentes)

- **Fichero**: NUEVO `backend/src/main/java/com/projectlog/query/dto/LogDto.java`.
- **Qué hace**: `public record LogDto(Long id, Long project_id, String
  level, String environment, String timestamp, String received_at, String
  message, String exception, com.fasterxml.jackson.databind.JsonNode
  metadata, String source, String correlation_id)` — 11 componentes
  EXACTOS; `exception` (`String`) y `metadata` (`JsonNode`) nullables
  **pero SIEMPRE serializados** (Jackson default: nulls incluidos —
  `application.yml` SIN config Jackson; el objeto EXACTO del jsonb sale
  tal cual). **Nota D-S6-c**: el boceto `Object metadata /*JsonNode…*/`
  de R6.9 se CIERRA como `JsonNode` por R6.10 (D-6.9) — el pin E6.2 pina
  el tipo (no hay divergencia real: es cerrar el hueco del propio spec).
- **Criterio de hecho**: compila; reflection da 11 componentes y el tipo
  de `metadata` es `JsonNode` (pin E6.2).
- **Escenarios**: E6.2, E6.3, E6.20.

### T6.3 — Crear `dto/LogsPaginaResponse.java`

- **Fichero**: NUEVO `backend/src/main/java/com/projectlog/query/dto/LogsPaginaResponse.java`.
- **Qué hace**: `public record LogsPaginaResponse(List<LogListItemDto>
  items, String next_cursor)` — wrapper del listado (R6.11). La clave
  `next_cursor` DEBE aparecer EXPLÍCITA con `null` cuando es fin de
  resultados (NO omitirse): Jackson default la emite (sin config extra,
  D-6/sin toque de `application.yml`) — el toggle "Cargar más" del AppFlow
  flujo 4 paso 4 la consume.
- **Criterio de hecho**: compila; serializa `{items, next_cursor}` con
  null explícito (verificado en T6.17/E6.1).
- **Escenarios**: E6.1, E6.23.

## Bloque 2 — CursorCodec (P1; R6.15–R6.15-c)

### T6.4 — Crear `query/CursorCodec.java`

- **Fichero**: NUEVO `backend/src/main/java/com/projectlog/query/CursorCodec.java`.
- **Qué hace**: `public final class CursorCodec` con SOLO métodos estáticos,
  SIN Spring y SIN BD (unit-testable — E6.12):
  - `public static String codificar(OffsetDateTime receivedAt, long id)` —
    fórmula EXACTA R6.15-a: `r = receivedAt.toInstant().toString()`
    (ISO-8601 del instante COMPLETO, micros preservados — JAMÁS el
    formatter de C2, R6.15-c); JSON por COMPOSICIÓN MANUAL
    `{"r":"<r>","i":<id>}` (orden determinista `r`,`i` — NO `ObjectMapper`
    en encode, NO `Map.of`); `Base64.getUrlEncoder().withoutPadding()` sobre
    bytes UTF-8.
  - `public static Cursor decodificar(String token)` — tolerancia ESTRECHA
    de los 6 pasos EN ORDEN (R6.15-b): (1) `null`/vacío → inválido;
    (2) rechazo EXPLÍCITO del `'='` (padding); (3)
    `Base64.getUrlDecoder().decode` (alfabeto clásico `+//` → fallo);
    (4) `readTree` con un `ObjectMapper` static PROPIO del codec (SOLO
    decode — D-6.3); (5) estructura estrecha: raíz objeto, EXACTAMENTE
    2 claves `r`+`i` (extras → inválido), `r` `isTextual()` + `Instant.parse`
    (SOLO UTC-Z; `+02:00` rechazado; NUNCA re-derivado), `i`
    `isIntegralNumber()` + `longValueExact()` + `>= 1`; (6) `Cursor(r, i)`
    con el instante EXACTO. CUALQUIER fallo → LA MISMA
    `CursorInvalidoException` (sin oráculo de causa — P1). El `r`
    decodificado NUNCA se valida contra BD (cursor de futuro → 200 página
    vacía, cierre §8.2/S6-3).
  - Anidados: `public record Cursor(Instant r, long i)` y
    `public static final class CursorInvalidoException extends RuntimeException {}`.
  - **Pin encode byte-exacto** (sonda P-1): `codificar(OffsetDateTime.parse("2026-10-16T22:31:04Z"), 12301)`
    === `eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0`.
- **Criterio de hecho**: compila sin dependencia de Spring; pin P-1
  verde en T6.13.
- **Escenarios**: E6.12 (base de E6.9 y E6.17).

## Bloque 3 — FiltrosConsulta (S-3 se cierra aquí; R6.20)

### T6.5 — Crear `query/FiltrosConsulta.java` (patronIlike)

- **Fichero**: NUEVO `backend/src/main/java/com/projectlog/query/FiltrosConsulta.java`.
- **Qué hace**: `public final class FiltrosConsulta` estática pura SIN
  Spring/BD, con UNA pieza — `public static String patronIlike(String
  texto)` (NO lleva el parseo: D-6.2). Comportamiento EXACTO (5 fases,
  S6-5): (1) `null`/`""` NO se le llega como null; el vacío LEGAL llega
  como `""` y produce `"%%"` (matchea todos los messages — S6-2, sin case
  especial); (2) `texto.replace("\\", "\\\\")` PRIMERO; (3) luego
  `.replace("%", "\\%")`; (4) luego `.replace("_", "\\_")` — el ORDEN
  OBLIGATORIO `\`→`%`→`_` (escapar `%`/`_` antes de `\` DOBLARÍA las
  barras recién insertadas); (5) devolver `"%" + resultado + "%"` (el
  texto NO se trimma NI normaliza — D-6.10; sin cap de longitud, S6-10).
  El `\` es el escape DEFAULT de LIKE/ILIKE en PG (sin `ESCAPE '\'` en el
  estamento). Javadoc ancla contratos §3.3 + seam C7 §3.5.
- **Criterio de hecho**: compila; orden y contra-caso de aguja verdes en
  T6.14.
- **Escenarios**: E6.13 (y E6.18 vía smoke).

## Bloque 4 — LogsQueryRepository (D-C6-1; R6.16–R6.19)

### T6.6 — Crear `repository/LogsQueryRepository.java` (parte 1: constants SQL package-private)

- **Fichero**: NUEVO `backend/src/main/java/com/projectlog/query/repository/LogsQueryRepository.java`,
  parte 1 (T6.7 completa el mismo fichero).
- **Qué hace**: `@Repository` con `JdbcTemplate` + `ObjectMapper` por
  constructor y constants `static final String` **package-private** (visibles
  al pin E6.11 en el mismo paquete — patrón `LogsRepoSqlTest`/D-9 de C5):
  - `SELECT_LISTADO`: LITERAL EXACTO del §1.G/R6.16 Verbatim — 9 columnas
    del listado en orden; los **7 CASTs ESPECÍFICOS** en las ocurrencias
    IS-NULL (`bigint`, `text`, `text`, `timestamptz`, `timestamptz`,
    `text`, `timestamptz` — D-C6-1 causa 1, sonda P-2; precedente R-4 de
    C5); la **GUARDA del keyset** con el MISMO patrón del congelado
    `(CAST(? AS timestamptz) IS NULL OR (received_at < ? OR (received_at =
    ? AND id < ?)))` (D-C6-1 causa 2: la rama desnuda evaluaría NULL y
    EXCLUIRÍA todas las filas de la primera página); `ORDER BY received_at
    DESC, id DESC`; `LIMIT ? + 1` VERBATIM (sonda P-5). Javadoc del
    estamento CITANDO D-C6-1 explícitamente (el verify C6 la pina como
    literal del spec). **SIN la palabra ESCAPE** (S6-5 — el `\` es el
    escape default de PG; el pin NO lo contiene).
  - `SELECT_POR_ID`: `SELECT id, project_id, level, environment, timestamp,
    received_at, message, exception, metadata, source, correlation_id FROM
    logs WHERE id = ?` — 11 columnas EXACTAS (`exception` y `metadata` SÍ
    presentes).
  - CERO SQL dinámico (E1): el estamento es ÚNICO para todas las
    combinaciones; la primera página usa EL MISMO estamento con los 3
    params del keyset NULL.
- **Criterio de hecho**: el fichero compila y las constants coinciden
  byte-a-byte con el §1.G del spec (pin T6.15).
- **Escenarios**: E6.11.

### T6.7 — Completar `LogsQueryRepository` (parte 2: métodos, binding 17 `?`, RowMappers)

- **Fichero**: EXistente-recién-creado por T6.6 (mismo fichero, parte 2).
- **Qué hace** (R6.17–R6.19):
  - `@Transactional public List<FilaConsulta> listar(Long projectId, String
    environment, String level, OffsetDateTime from, OffsetDateTime to,
    String patronTexto, OffsetDateTime cursorR, Long cursorI, int limit)` y
    `@Transactional public Optional<LogDto> porId(long id)` — ambas
    mono-sentencia, defensa documentada (patrón R3.2); record anidado
    `public record FilaConsulta(LogListItemDto item, OffsetDateTime
    received_at, long id)`.
  - Binding POSICIONAL con `Object[]` de `jdbcTemplate.query(...)` (patrón
    R5.11-d de C5) en el orden EXACTO — **17 placeholders `?`** (D-S6-a):
    la cuenta se toma del LITERAL R6.16 y es `12` (los 6 filtros
    duplicados: projectId, projectId, environment, environment, level,
    level, from, from, to, to, patronTexto, patronTexto) + `cursorR × 3`
    (guard IS NULL + `<` + `=`) + `cursorI × 1` (contexto de columna `id`
    infiere el tipo) + `limit × 1` — **NO la lista de 16 de R6.17** (la
    PROSA de R6.17 dice "cursorR en sus 3 posiciones"; el design
    D-S6-a/D-R6.1 manda anclar 17). Tipos: `projectId`/`cursorI` como
    `Long` (null sin filtro), `from`/`to`/`cursorR` como `OffsetDateTime`
    (driver timestamptz) — la conversión `OffsetDateTime.ofInstant(c.r(),
    ZoneOffset.UTC)` vive en el CONTROLLER (D-6.4), `patronTexto` como
    `String` YA escapada+envuelta (`%needle%` — S6-5), `limit` como `int`
    (el Java NO suma +1: el `+ 1` vive en el literal).
  - Reglas (R6.18): NUNCA `queryForObject` en las 2 lecturas (fila-0 es el
    caso LEGAL del 404 — el listado devuelve la Lista CRUDA de hasta
    limit+1 filas; el trim vive en el controller, D-6.8); el repo NO
    valida NADA (frontera de capas); NINGÚN método toca la tabla
    `projects`; NI UN método nuevo en `ingest.LogsRepository`.
  - RowMappers 2 SEPARADOS (R6.14 — jamás un mapper recortado compartido):
    `FILA_LISTADO` → `FilaConsulta(new LogListItemDto(...9 campos...),
    rs.getObject("received_at", OffsetDateTime.class) /*RAW, R6.15-c*/,
    rs.getLong("id"))` con `timestamp`/`received_at` del item formateados
    con `LoginController.EXPIRES_AT_FORMATTER` (reutilizado por referencia,
    JAMÁS editado; el RAW JAMÁS pasa por el formatter antes del codec);
    `FILA_DETALLE` → `LogDto` de 11 con `rs.getString("exception")`
    (nullable) + `metadata`: `rs.getString("metadata")` → `null` si la
    columna es NULL; si no `objectMapper.readTree(texto)` → `JsonNode`
    objeto EXACTO (NUNCA String crudo, NUNCA `readValue(Map.class)` —
    D-6.5; readTree malformado sería RuntimeException no capturada → 500
    honesto, imposible por construcción).
- **Criterio de hecho**: compila; firma y orden de binding pinnados en
  T6.15; lectura real certificada por T6.23.
- **Escenarios**: E6.11 (pin), E6.16/E6.20 (pila).

## Bloque 5 — LogsController (constants junto al gate-owner; R6.5–R6.8, R6.21–R6.24)

### T6.8 — Crear `controller/LogsController.java` (parte 1: mappings, constants, error())

- **Fichero**: NUEVO `backend/src/main/java/com/projectlog/query/controller/LogsController.java`,
  parte 1 (T6.9–T6.11 completan el mismo fichero).
- **Qué hace**:
  - `@RestController @RequestMapping("/api/v1/panel/logs")` con
    EXACTAMENTE 2 mappings: `@GetMapping` (listado) y
    `@GetMapping("/{id}")` (detalle) — VERBATIM de Schema §3.2: 200
    `{items, next_cursor?}` · 400 · 401 / 200 `LogDto` · 401 · 404. NUNCA
    `/api/v1/logs`. **SIN `@PreAuthorize`** y SIN toque de seguridad
    (D-6.7: la cadena 2 `securityMatcher("/api/v1/panel/**")`, `@Order(2)`,
    los cubre nata — E6.15 lo pina).
  - Constants `CODE_*`/`MSG_*` EXACTAS de R6.21 (patrón C3, package-private
    junto al gate-owner): `CODE_FILTER_FROM_TO_INVERTED`/
    `CODE_FILTER_INVALID_VALUE`/`CODE_FILTER_LIMIT_INVALID`/
    `CODE_FILTER_CURSOR_INVALID`/`CODE_NOT_FOUND` + los 8 `MSG_*` con los
    literales EXACTOS de R6.21/R6.22 (incl. la `MSG_NOT_FOUND`
    `No existe (quizás fue eliminado)` DUPLICADA como constant propia del
    paquete query — mismo LITERAL anclado en AppFlow §5 fila 404, para no
    depender de `projects.*`; patrón del entry point de C5). NINGÚN code
    nuevo fuera de la tabla R6.22 (vocabulario cerrado §3.1; los 3 sin
    literal textual — `project_id` no-numérico, `from`/`to` no-parseable,
    `limit` no-numérico — REUTILIZAN `filter_invalid_value`/
    `filter_limit_invalid` según gate P2).
  - Helper `private Object error(HttpStatus, String codigo, String mensaje,
    String... campo)` — réplica EXACTA del de C3 (D-6.6): `LinkedHashMap`
    con `code`, `message`, `field` SOLO si aplica;
    `objectMapper.valueToTree`; `MediaType.APPLICATION_JSON`.
- **Criterio de hecho**: compila; constants/pins verdes en T6.16–T6.19
  (los cuerpos de E6.5–E6.9/E6.21 se comparan TAL CUAL).
- **Escenarios**: E6.5–E6.9, E6.21, E6.15 ( mappings).

### T6.9 — Completar `LogsController` (parte 2: los 8 checks fail-fast)

- **Fichero**: mismo fichero (parte 2).
- **Qué hace** (R6.8/D-6.2): método listado con los 8 params TODOS como
  `@RequestParam(required = false) String` (project_id, environment, level,
  from, to, text, cursor, limit) — parseo MANUAL inline en `listar` como
  cadena de 8 `if`s de retorno temprano, EN ORDEN EXACTO, primer-fallo-gana,
  TODOS antes de invocar la BD:
  1. `limit` presente no-numérico (incl. `""` vacío) o fuera de 1..200 →
     400 `filter_limit_invalid` / `field: "limit"` / MSG_LIMIT_INVALID
     (MISMO cuerpo para rango y no-numérico — anti-oráculo S6-6).
  2. `project_id` presente no-numérico (`Long.parseLong` en try) → 400
     `filter_invalid_value` / `project_id`.
  3. `environment` fuera de `development|staging|production` (case-sensitive,
     sin normalizar; `""` → 400) → `filter_invalid_value` / `environment`.
  4. `level` fuera de `TRACE|DEBUG|INFO|WARN|ERROR|FATAL` (idem) →
     `filter_invalid_value` / `level`.
  5. `from` presente NO parseable (`OffsetDateTime.parse` — naive sin
     offset RECHAZADO, patrón S5-5 de C5) → `filter_invalid_value` / `from`.
  6. `to` idem → `filter_invalid_value` / `to`.
  7. `from > to` (`from.toInstant().isAfter(to.toInstant())`; `from == to`
     LEGAL, rango de punto) → `filter_from_to_inverted` **SIN `field`**.
  8. `cursor` presente ilegible — `try { CursorCodec.decodificar(t) } catch
     (CursorInvalidoException)` — → `filter_cursor_invalid` / `cursor`
     (sin oráculo de causa).
  `text` NO lleva check (sin cap — D-6.10/S6-10). La verificación del
  orden es T6.20/E6.10 con `verifyNoInteractions` del repo en TODO 400.
  - **Nota (micro-divergencia design/spec citada, NO bloqueante)**: el
    boceto de firma del design §4 escribe `Integer limit` pero SU PROPIO
    comentario ("limit NO binding tipado: check 1 manual") y el spec
    R6.7/R6.8 (check 1 con parse manual y `""` incluido, R-6.c) exigen
    `String limit` — se implementa `String`.
- **Criterio de hecho**: compila; E6.5–E6.10 verdes en T6.19/T6.20.
- **Escenarios**: E6.5–E6.10, E6.21, E6.22.

### T6.10 — Completar `LogsController` (parte 3: flujo del listado, trim y next_cursor)

- **Fichero**: mismo fichero (parte 3).
- **Qué hace** (R6.24/D-6.8) — tras pasar los 8 checks, SOLO si pasan:
  (1) `patron = FiltrosConsulta.patronIlike(text)`; (2) llamada ÚNICA a
  `listar(...)`; (3) recorte si `filas.size() > limit`: `items =
  filas.subList(0, limit)` (RECORTE exacto — `subList` es vista sobre DTOs
  ya mapeados: la extra se descarta, no se transporta) y `next_cursor =
  CursorCodec.codificar(filas.get(limit - 1).received_at(),
  filas.get(limit - 1).id())` — la ÚLTIMA fila ENTREGADA, jamás la extra,
  con el `received_at` RAW (jamás el String renderizado — R6.15-c); (4) si
  no hay fila extra: `next_cursor = null` y `items = filas`; (5) 200
  `ResponseEntity.ok(new LogsPaginaResponse(items, nextCursor))`. El
  listado VACÍO sin cursor es estado LEGÍTIMO: 200
  `{"items": [], "next_cursor": null}` (R6.12 — NUNCA 404/400). El
  estado del recorrido vive ÍNTEGRAMENTE en el token opaco (sin
  `HttpSession` — D-6 / §3 del design).
- **Criterio de hecho**: compila; E6.1/E6.14 verdes en T6.17.
- **Escenarios**: E6.1, E6.14, E6.23.

### T6.11 — Completar `LogsController` (parte 4: detalle 200/404)

- **Fichero**: mismo fichero (parte 4).
- **Qué hace**: `GET /{id}` (R6.6/D-6): `porId(id)` → 200 `LogDto`
  (exception/metadata nulls incluidos — R6.13 no aplica: LogDto lleva
  default Jackson) | `Optional.empty()` → **404** con el cuerpo EXACTO
  `{code: "not_found", message: "No existe (quizás fue eliminado)"}`
  SIN `field` (R6.22) — NUNCA lista vacía ni redirección. El detalle NO
  lleva checks de query params (R6.8). El `/logs/abc` (id no-numérico)
  queda RATIFICADO sin toque: Spring no mapea la variable y responde su
  error de matching/argumento (patrón `GET /projects/{id}` de C3 — sin
  handler, sin escenario, CD-3.9).
- **Criterio de hecho**: compila; E6.3/E6.4 verdes en T6.18.
- **Escenarios**: E6.3, E6.4, E6.20.

## Bloque 6 — Tests [L] unit/pin (E6.2/E6.12/E6.13, E6.11)

### T6.12 — Crear `dto/DtosLogsPinTest.java` (E6.2)

- **Fichero**: NUEVO `backend/src/test/java/com/projectlog/query/dto/DtosLogsPinTest.java`.
- **Qué hace**: patrón `ProjectDtoSerializationTest` (JUnit puro, SIN
  Spring): (a) reflection de los componentes del record `LogListItemDto` —
  EXACTAMENTE 9, en el orden/nombres literales del spec; (b) la FUGA de
  `exception`/`metadata` en la lista es IMPOSIBLE de compilar (el pin lo
  afirma estructuralmente — no existe el campo); (c) `LogDto` con
  EXACTAMENTE 11 — los 9 + `exception: String` + `metadata` DE TIPO
  `JsonNode` (pin del tipo — cierre D-S6-c); (d) serialización: claves
  correctas, y con valores null los campos siguen APARECIENDO en el JSON
  (default Jackson, R6.11/R6.13).
- **Criterio de hecho**: `mvn -f backend/pom.xml test` rojo→verde con esta
  clase; sin tocar NADIE C1–C5.
- **Escenarios**: E6.2.

### T6.13 — Crear `CursorCodecTest.java` (E6.12)

- **Fichero**: NUEVO `backend/src/test/java/com/projectlog/query/CursorCodecTest.java`.
- **Qué hace**: unit puro del codec SIN Spring NI BD: (a) round-trip
  completos con instante a MICROSECONDS
  (`2026-10-16T22:31:04.123456Z` → encode → decode → MISMO instante
  exacto, ningún dígito perdido — R-6.a/D-R6.a); (b) el pin del EJEMPLO
  del Schema (sonda P-1): encode byte-exacto
  `eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0` y decode
  `(2026-10-16T22:31:04Z, 12301)`; (c) round-trip con token SIN fracciones
  y CON nanos de conveniencia; (d) anti-truncado: truncar el instante a
  segundos → re-encode produce token DIFERENTE (el round-trip con
  truncado NO es semánticamente neutro — el codec NUNCA trunca).
- **Criterio de hecho**: unit verde sin contexto Spring.
- **Escenarios**: E6.12 (pareja [S]: E6.17).

### T6.14 — Crear `FiltrosConsultaTest.java` (E6.13)

- **Fichero**: NUEVO `backend/src/test/java/com/projectlog/query/FiltrosConsultaTest.java`.
- **Qué hace**: unit puro del `patronIlike` con literales Java: `"a_b"` →
  `%a\_b%` (escrito en Java `%a\\_b%`) — el `_` queda escapado, NO
  wildcard; `"100%"` → `%100\%%` (Java `%100\\%%`); `"a\b"` → `%a\b%`
  (Java `%a\\b%`); el ORDEN pinado: un texto con `_` y `\` juntos produce
  el escape CORRECTO SOLO con `\`-primero (matriz del cierre S6-5);
  contra-caso de la AGUJA (cierre de la S-3 de C4): `patronIlike("smoke_c6_X")`
  produce `%smoke\_c6\_X%` y un needle con el `_` sustituido por otra
  letra (`needleXc6`, mismo estilo) NO matchearía ese patrón; y `""` →
  `"%%"` (caso legal S6-2). El patrón aquí producido es el MISMO que usa
  E6.18 contra la pila.
- **Criterio de hecho**: unit verde.
- **Escenarios**: E6.13 (pareja [S]: E6.18).

### T6.15 — Crear `repository/LogsQuerySqlTest.java` (E6.11 — pin del SQL, 17 `?`)

- **Fichero**: NUEVO `backend/src/test/java/com/projectlog/query/repository/LogsQuerySqlTest.java`.
- **Qué hace**: patrón `LogsRepoSqlTest`/D-9 (MISMO paquete
  `query/repository` — constants package-private visibles SIN reflection y
  SIN BD): pin del LITERAL EXACTO de `SELECT_LISTADO` (R6.16): (a) las 9
  columnas del listado en orden; (b) los 7 CASTs ESPECÍFICOS en sus
  posiciones IS-NULL (`bigint`/`text`/`text`/`timestamptz`/`timestamptz`/
  `text`/`timestamptz` — D-C6-1); (c) la GUARDA del keyset con el patrón
  `(x IS NULL OR ...)` y AMBAS comparaciones (`<` + `=` + `id < `);
  (d) `ORDER BY received_at DESC, id DESC`; (e) `LIMIT ? + 1` VERBATIM;
  (f) **CUENTA DE PLACEHOLDERS = 17**, tomada del LITERAL de R6.16:
  12 de los 6 filtros (duplicados IS-NULL-CAST/igualdad) + `cursorR` en 3
  posiciones + `cursorI` en 1 + `limit` en 1 — **NO la lista de 16 de
  R6.17** (D-S6-a/D-R6.1: la prosa de R6.17 YA dice "cursorR en sus 3
  posiciones"; implementar 16 con el literal de 17 descolgaría el binding
  con `index out of range`); (g) pin de `SELECT_POR_ID` (11 columnas con
  `exception` y `metadata` SÍ presentes + `WHERE id = ?`); (h) pin de
  NO-"ESCAPE" explícito (el estamento NO contiene la palabra ESCAPE —
  S6-5); (i) pin de que el listado NO menciona `exception`/`metadata`
  (fuga P5). El (f)/(g) cubren el aspecto DUAL de E6.11 fused del spec.
- **Criterio de hecho**: pin verde; literal idéntico byte-a-byte al §1.G
  del spec.
- **Escenarios**: E6.11 (y `verifyNoInteractions`-familia de E6.10 en
  T6.20).

## Bloque 7 — Tests [L] MVC (fixture + E6.15 + E6.1, E6.3–E6.10, E6.14)

### T6.16 — Crear `controller/LogsControllerMvcTest.java` (parte 1: fixture + E6.15)

- **Fichero**: NUEVO `backend/src/test/java/com/projectlog/query/controller/LogsControllerMvcTest.java`,
  parte 1 (T6.17–T6.20 extienden el mismo fichero).
- **Qué hace**: fixture EXACTA de `ProjectsControllerMvcTest` (R6.25):
  `@WebMvcTest(LogsController.class)` + `@Import({SecurityConfig.class,
  JwtAuthFilter.class, RestAuthenticationEntryPoint.class,
  CorsConfig.class})` + `@MockitoBean LogsQueryRepository` + `@MockitoBean
  TokenService` + `@ActiveProfiles("test")` con `application-test.yml`
  INTOCABLE; JWT real emitido con `TokenService(SECRETO_TEST, 8).issue(...)`
  + helper `bearer()` (padrón `PanelAuthMvcTest`). Escenario **E6.15**:
  SIN JWT → 401 `invalid_token` del entry point del PANEL
  (`RestAuthenticationEntryPoint` CD-4, JAMÁS `api_key_invalid`);
  con `X-API-Key: plk_xxx` Y SIN JWT → idem 401 `invalid_token` (la API
  key NO autentica el panel — la cadena 2 la ignora); con JWT válido →
  pasa al controller (firma del flujo 200). NINGUNA línea de seguridad
  tocada (R6.3/D-6.7).
- **Criterio de hecho**: fixture carga el contexto; E6.15 verde.
- **Escenarios**: E6.15 (pareja [S]: pre-fase del smoke — sondeo /me).

### T6.17 — Extender MVC test (parte 2: E6.1 + E6.14)

- **Fichero**: EXistente-recién-creado por T6.16 (parte 2).
- **Qué hace**:
  - **E6.1** `listadoShapeExactoItemsYNextCursor`: con repo mock
    entregando 51 filas y `limit` 50 → 200 con el shape EXACTO
    `{"items": [LogListItemDto…], "next_cursor": "…"}`. La aserción del
    `next_cursor` se materializa como **DECODIFICACIÓN DE VUELTA**
    (**D-6.11 / D-S6-b** — NO `ArgumentCaptor` del codec ni
    `mockito.mockStatic` ni reflection: el codec es static puro y el spec
    fue escrito con "ArgumentCaptor" como intención): se captura el
    `next_cursor` del JSON de respuesta y `CursorCodec.decodificar(token)`
    === `(r, i)` de la fila `limit-1` (fila 50) del mock — MISMA
    equivalencia, determinista. Y con lista de 50 (sin fila extra) →
    `next_cursor: null` EXPLÍCITO en el JSON (la CLAVE aparece con null,
    R6.11).
  - **E6.14** `limitDefecto50YLegal200AlRepo`: sin `limit` → el repo
    recibe `50` (ArgumentCaptor del REPO — sí es capturable, es bean
    mock); `?limit=200` → recibe `200`; y la rama del trim (E6.1) usa
    limit+1 filas del mock — la extra se descarta sin transportarla.
- **Criterio de hecho**: E6.1 y E6.14 verdes.
- **Escenarios**: E6.1, E6.14.

### T6.18 — Extender MVC test (parte 3: E6.3 + E6.4, detalle)

- **Fichero**: mismo fichero (parte 3).
- **Qué hace**:
  - **E6.3** `detalleResponde200ConLos11CamposYMetadataNodoExacto`: mock
    con `exception` no-nula y `metadata` no-nula → 200 `LogDto` completo —
    `exception` = el String tal cual; `metadata` = JsonNode con el objeto
    EXACTO (claves del jsonb intactas — R6.10/D-6.5); con
    exception/metadata nulas → los 2 campos con `null` EXPLÍCITO (default
    Jackson en LogDto).
  - **E6.4** `detalleIdInexistenteResponde404NotFound`: mock vacío → 404
    con el cuerpo EXACTO `{"code":"not_found","message":"No existe (quizás
    fue eliminado)"}` SIN `field` (R6.22) — NUNCA lista vacía ni
    redirección.
- **Criterio de hecho**: E6.3/E6.4 verdes.
- **Escenarios**: E6.3, E6.4 (pareja [S]: E6.20).

### T6.19 — Extender MVC test (parte 4: E6.5–E6.9, cuerpos 400)

- **Fichero**: mismo fichero (parte 4).
- **Qué hace** (cada cuerpo comparado TAL CUAL contra R6.21/R6.22 — code Y
  message Y field):
  - **E6.5**: `?environment=qa`, `?level=error` (minúscula, SIN
    normalización) y `?environment=` (vacío) → 400 `filter_invalid_value`
    con su `field` y message EXACTO.
  - **E6.6**: `?from=2026-01-02T00:00:00Z&to=2026-01-01T00:00:00Z` → 400
    `filter_from_to_inverted` SIN `field`, message EXACTO; frontera
    `from == to` → LEGAL (llega al repo — verify call).
  - **E6.7**: `?limit=0`, `?limit=201`, `?limit=abc`, `?limit=` → 400
    `filter_limit_invalid`, `field: "limit"`, MISMO cuerpo (anti-oráculo);
    `?limit=1` y `?limit=200` → LEGAL.
  - **E6.8** (los 3 de P2 anclados por SPEC): `?project_id=abc` →
    `filter_invalid_value` field `project_id`; `?from=no-fecha` → field
    `from`; `?to=no-fecha` → field `to`; `?from=2026-10-16T22:31:04`
    (NAIVE) → el MISMO 400 de `from` (S5-5).
  - **E6.9** (los 4 del §3.3 + 3 extras del P1, MISMO body, anti-oráculo,
    field `cursor`): (a) no-Base64url (`"ñ%20%2B"` / `+`/`/` clásicos);
    (b) padding `=`; (c) decodifica pero NO JSON; (d) JSON sin `r` o sin
    `i`; (e) `r` no-parseable (`"no-es-instant"` / `+02:00`); (f) claves
    extra (tolerancia estrecha — R6.15-b paso 5); (g) `i` no-entero / `-1`.
- **Criterio de hecho**: E6.5–E6.9 verdes.
- **Escenarios**: E6.5, E6.6, E6.7, E6.8, E6.9 (parejas [S]: E6.21).

### T6.20 — Extender MVC test (parte 5: E6.10, orden fail-fast + frontera repo)

- **Fichero**: mismo fichero (parte 5).
- **Qué hace**: **E6.10** `ordenFailFastDe8ChecksEsDeterminista`: query con
  TODOS los params mal (limit abc Y project_id abc Y level error Y from
  naive Y to no-fecha Y cursor basura) → el 400 es SIEMPRE el del check 1
  (`filter_limit_invalid`) — no el 2º/3º; con limit OK y 2 fallos
  (project_id + level) → SIEMPRE `project_id` (check 2); con SOLO
  `from` naive → `from`; con from/to PARSEADOS invertidos Y cursor basura →
  SIEMPRE `filter_from_to_inverted` (check 7 ANTES del 8); y en TODO 400 →
  `verifyNoInteractions` del repo (los 8 checks corren ANTES de la BD —
  R6.8/P6; el `verifyNoInteractions` es el segundo aspecto del E6.11
  fused — T6.15 pin el estamento, aquí la frontera).
- **Criterio de hecho**: E6.10 verde.
- **Escenarios**: E6.10 (aspecto frontera de E6.11).

## Bloque 8 — Gate de suite [L] pre-smoke

### T6.21 — `mvn test` completo ANTES del smoke

- **Fichero**: NINGUNO (ejecución de verificación).
- **Qué hace**: correr `mvn -f backend/pom.xml test` completo y EXIGIR
  verde 108/108 de C1–C5 + los N de C6, ANTES de bajar al smoke — este es
  el pin del SQL (T6.15, 17 `?`/D-C6-1) y del contrato (constants, checks,
  codec, escape, DTOs) contra el build. **Nota de higiene**: como C6 NO
  edita `SecurityConfig` ni ningún bean existente, NO aplica el
  anti-contaminación post-edit de cortes previos — el equivalente aquí es
  ESTA corrida completa pre-smoke y otra post-smoke (T6.24, solo-regresión).
- **Criterio de hecho**: exit 0, suite verde, cero fallos heredados.
- **Escenarios**: soporte de E6.1–E6.15 (todos los [L] juntos).

## Bloque 9 — Smoke [S] `scripts/qa_c6_smoke.sh` (E6.16–E6.23)

### T6.22 — Crear `scripts/qa_c6_smoke.sh` completo (patrón qa_c5_smoke)

- **Fichero**: NUEVO `scripts/qa_c6_smoke.sh` (NADA más, patrón EXACTO de
  `scripts/qa_c5_smoke.sh` — C5 lo construyó por escenarios E5.21–E5.33 en
  un solo script; idem aquí).
- **Qué hace** (guion EXACTO del spec §2.5/R6.27/R6.28):
  - Pre-fase (smoke-of-the-smoke, parte de E6.16): recomposición ÚNICA
    `podman compose up -d --force-recreate` (NUNCA `down -v` — pgdata con
    las 1877 filas heredadas de C5, R6.28) + pila 3×healthy + psql `-qAt`;
    sondeo heredado `GET /me` SIN token → 401 `invalid_token` (cadena 2
    intacta — pareja [S] de E6.15); login → 200 (JWT a tmpdir) +
    `POST /projects` → 201 `smoke-c6-<TS>-a` (id capturado
    incondicionalmente) + `POST /projects/{id}/api-key/regenerate` → 201
    (key a tmpdir — JAMÁS a consola).
  - Helpers: `curl_ingest KEY …` (patrón C5) y `curl_get JWT …` (NUEVO,
    para los GET de panel). tmpdir con permisos 700 y trap de limpieza.
  - Escenarios E6.16–E6.23 EN orden cronológico, cada uno con salida
    `E6.n: PASS | FAIL | BLOQUEADO (detalle)` y exit 0/1/2 como C3/C4/C5:
    - **E6.16** filtros combinables en pila: ingesta batch `smoke-c6-<TS>-k1..k3`
      (env/level distinguibles, p. ej. production/ERROR) → GET
      `/api/v1/panel/logs` JWT con `project_id=<id>&environment=production&level=ERROR`
      → 200 con SOLO los ids de las agujas; comparación REST↔psql de
      igualdad estricta con la MISMA cláusula WHERE combinada; y página 1
      SIN filtros → 200 con 50 primeras y shape
      `{"items":[…],"next_cursor":"…"}` (pareja [S] de E6.1). **Nota
      D-S6-a**: el estamento que corre en pila es EL MISMO pinado con los
      17 `?` y los 7 CASTs — el smoke ejerce el binding real que el pin
      cuenta estático.
    - **E6.17** keyset ESTABLE con batch partido (Done literal): página 1
      con `limit=3` (67 instants/1877 filas — sonda P-0: el guion VERIFICA
      que la página corte DENTRO de un mismo instante con altísima
      probabilidad y re-intenta con el límite mínimo que la produzca) →
      captura `received_at` a MICROSEGUNDOS (psql de la última entregada)
      y `next_cursor` → (a) psql: el token decodificable tiene `r ==
      received_at` COMPLETO a micros (pareja [S] del E6.12a — sin
      truncado); (b) ingesta de UN batch nuevo de K logs ENTRE páginas →
      página 2 con el cursor: NINGÚN id repetido entre páginas, NINGÚN id
      de la página 1 reaparece, la PRIMERA fila de la página 2 es la
      SIGUIENTE en el orden keyset (id adyacente bajo el MISMO received_at
      a micros — psql).
    - **E6.18** aguja `_` de ILIKE (cierre S-3 de C4): ingesta individual
      de `smoke_c6_<TS>_timeout` (aguja con `_` literal) y contra-caso
      `smoke-c6-<TS>-sin-guion-bajo` con el MISMO prefijo sin `_` → GET
      `?text=smoke_c6_<TS>_timeout` → 200 con SOLA la aguja (conteo=1
      estricto) y el contra-caso NO matcheado; needle psql POR
      `=`/`position()` — NUNCA la aguja cruda en un LIKE psql (R6.20-a).
      Pareja [S] del E6.13.
    - **E6.19** rango from/to con offset e inclusive: frontera
      `from == received_at` de una aguja real → GET
      `?from=<t>+02:00&to=<t>+02:00` (offset NO-UTC) → 200 y MISMO
      resultado que su equivalente `Z` (psql idéntico); extremos
      INCLUSIVE (psql: los 2 ids de frontera presentes).
    - **E6.20** detalle en pila: ingesta individual con `exception` no
      nula y `metadata` objeto real `{"span":"checkout","attempt":2}`
      (patrón C5) → GET `/panel/logs/{id}` → 200: `exception` COMPLETO y
      `metadata` = el OBJETO EXACTO (`span`/`attempt` legibles); y GET
      `/panel/logs/999999999` → 404 `{code: not_found, message: …}`.
      Parejas [S] de E6.3/E6.4 (y [S] del E6.2 en campos reales).
    - **E6.21** codes 400 reales en pila (cuerpo EXACTO de R6.22):
      `?from=2026-01-02..&to=2026-01-01..` → `filter_from_to_inverted`;
      `?level=error` → `filter_invalid_value` (field level); `?limit=0` →
      `filter_limit_invalid`; `?cursor=basura` → `filter_cursor_invalid`;
      `?project_id=abc` → `filter_invalid_value` (field project_id).
      Parejas [S] de E6.5/E6.6/E6.7/E6.8/E6.9.
    - **E6.22** limit en frontera: `?limit=200` → la página trae 200
      (delta/comparativa psql) y `next_cursor` presente si hay fila 201;
      `?limit=201` → 400 `filter_limit_invalid`; SIN limit → la página
      trae 50. Parejas [S] de E6.14/E6.7.
    - **E6.23** fin de resultados y recorrido completo: aguja propia con
      `limit` pequeño (p. ej. 2) sobre UN SOLO batch (todos comparten
      received_at — regla 9/P-0) → recorrer TODAS las páginas encadenando
      `cursor=next_cursor` hasta recibir `next_cursor: null` EXPLÍCITO →
      ids SIN duplicados, SIN saltos (set completo del batch cubierto) —
      el AppFlow paso 4 "Cargar más" estable en cada punto. Pareja [S]
      de E6.1.
  - Conteos RELATIVOS antes/después de cada escenario (R-6.h; los
    absolutos romperían el smoke); needles sufijados por ejecución
    (`smoke-c6-<TS>-*`); agujas `smoke_c6_<TS>` con escape psql
    (R6.20-a).
- **Criterio de hecho**: `bash scripts/qa_c6_smoke.sh` contra la pila viva
  imprime `E6.16…E6.23: PASS` y exit 0 (ejecución en T6.23).
- **Escenarios**: E6.16, E6.17, E6.18, E6.19, E6.20, E6.21, E6.22, E6.23.

### T6.23 — Ejecutar el smoke contra la pila viva

- **Fichero**: NINGUNO (ejecución de T6.22).
- **Qué hace**: con la pila UP (3×healthy, 1877 filas heredadas
  INTOCADAS — sin restauración ni purga, R6.28), correr
  `bash scripts/qa_c6_smoke.sh` y registrar la salida `E6.n` por ID.
- **Criterio de hecho**: los 8 IDs PASS y exit 0. Si la pila está down →
  BLOQUEADO (detalle: recomponer y re-correr); si algún E6.n FAIL con
  pin [L] verde → reportarlo tal cual (no se auto-arregla fuera del
  guion).
- **Escenarios**: E6.16–E6.23.

## Bloque 10 — Cierre de corte

### T6.24 — `mvn test` completo TRAS el smoke (solo-regresión)

- **Fichero**: NINGUNO.
- **Qué hace**: re-correr `mvn -f backend/pom.xml test` completo tras el
  smoke y exigir 108/108 de C1–C5 + N de C6 VERDE (R6.29: los pins de
  `LogsRepoSqlTest` de C5 — INSERT de logs — re-corridos intocados). Sin
  anti-contaminación de beans (C6 no edita SecurityConfig/CorsConfig) —
  este es el segundo gate de higiene de C6.
- **Criterio de hecho**: exit 0 / suite verde.
- **Escenarios**: cumplimiento R6.29 (soporte global).

### T6.25 — Verificación final de frontera + cobertura

- **Fichero**: NINGUNO (git diff / revisión).
- **Qué hace**:
  1. `git status` + diff: **fichero existente modificado: CERO** — solo
     los CREATE del paquete `com.projectlog.query` (7) + sus tests (5) +
     `scripts/qa_c6_smoke.sh` + artefactos `.atl/sdd/C6/`. Confirmar
     espec.: `query/package-info.java`, `V1__init.sql`, `backend/pom.xml`,
     `application.yml`/`application-test.yml`/`.env.example`/
     `compose.yaml`, `SecurityConfig`/`CorsConfig`/`RestAuthenticationEntryPoint`,
     `ingest/**`, `auth/**`, `keys/**`, `projects/**`, `streaming/**`,
     `common/**`, docs/ y TODOS los tests C1–C5 sin diff.
  2. Checklist de cobertura E6.1–E6.23 COMPLETA (15 [L] + 8 [S]) y las 12
     parejas mock↔psql de §2.6 del spec cubiertas (E6.1↔E6.16/E6.23,
     E6.2↔E6.20, E6.3↔E6.20, E6.4↔E6.20, E6.5↔E6.21, E6.6↔E6.21,
     E6.7↔E6.22, E6.9↔E6.21, E6.12↔E6.17, E6.13↔E6.18, E6.14↔E6.22,
     E6.15↔pre-fase del smoke).
  3. Deudas RETENIDAS sin toque (nota para review): smoke docker compose
     canónico → C9; 401 ISO-8859-1 → C8/C9; nginx `client_max_body_size`
     → C9. Y SI E6.18 PASS: la S-3 de C4 queda CERRADA (el archive de C6
     lo citará con E6.18 como evidencia — no acción aquí).
  4. La divergencia D-C6-1 citada en el Javadoc de `SELECT_LISTADO` y
     pinada en E6.11 (literal del pin incluye los 7 CASTs + guard) —
     trazable por el verify.
- **Criterio de hecho**: diff limpio (0 modificados salvo nada) +
  checklist completa sin hueco.
- **Escenarios**: cierre de R6.2/R6.3/R6.29 y trazabilidad §5 del spec.

---

## Notas de divergencia (citadas, NO corregidas — ninguna bloqueante)

- **D-S6-a** (inconsistencia INTERNA del spec §1.G — resuelta por el
  LITERAL): R6.17/E6.11 declaran "**16** placeholders" en su lista, pero
  el LITERAL pinado de R6.16 tiene **17 `?`** y la PROSA de R6.17 dice
  "`cursorR` en sus 3 posiciones". El design (D-S6-a/D-R6.1) manda
  implementar el LITERAL: 17 = 12 (6 filtros duplicados) + cursorR×3 +
  cursorI×1 + limit×1. Efecto en tareas: **T6.7** (binding) y **T6.15**
  (pin) anclan la cuenta a 17; **T6.22/E6.16** la coteja en pila.
- **D-S6-b** (aclaración E6.1): el "ArgumentCaptor del codec" del spec no
  es captorable tal cual (codec static puro — D-6.11). T6.17 lo
  materializa como DECODIFICACIÓN DE VUELTA del token emitido; sin
  mockStatic ni reflection.
- **D-S6-c** (cierre R6.9/R6.10): el boceto `Object metadata` de R6.9 se
  cierra como `JsonNode` por R6.10 (D-6.9) — T6.2/T6.12 pin el tipo.
  Sin divergencia real.
- **Micro-nota firma `listar`** (T6.9): el boceto del design §4 escribe
  `Integer limit` pero su propio comentario ("limit NO binding tipado:
  check 1 manual") y el spec R6.7/R-6.c/R6.8 (check 1 con parse manual,
  `""` incluido) exigen `String limit` — se implementa `String` (lo
  ratifica el propio design).
- **D-C6-1** (contra el CONGELADO — ya DECLARADA y CERRADA EN el spec con
  sondas P-2…P-5): T6.6/T6.7 la IMPLEMENTAN (7 CASTs IS-NULL + guard del
  keyset) y la CITAN en el Javadoc del estamento y del pin E6.11 — no es
  divergencia nueva del design.

## Cobertura E6.x (mapa tarea → escenarios)

| Tarea | E6.x |
|---|---|
| T6.1 / T6.2 / T6.12 | E6.2 (base E6.3) |
| T6.3 | E6.1, E6.23 |
| T6.4 | E6.12 (base E6.9, E6.17) |
| T6.5 | E6.13 (base E6.18) |
| T6.6 / T6.7 / T6.15 | E6.11 (T6.22/E6.16 la ejercita real) |
| T6.8 | mappings+constants (E6.5–E6.9, E6.21, E6.15) |
| T6.9 | E6.5–E6.10, E6.21, E6.22 |
| T6.10 | E6.1, E6.14, E6.23 |
| T6.11 | E6.3, E6.4, E6.20 |
| T6.13 / T6.14 | E6.12 / E6.13 |
| T6.16 | E6.15 |
| T6.17 / T6.18 / T6.19 / T6.20 | E6.1, E6.14 / E6.3, E6.4 / E6.5–E6.9 / E6.10 |
| T6.21 / T6.24 | suite completa (soporte de los [L]) |
| T6.22 / T6.23 | E6.16–E6.23 |
| T6.25 | cierre de frontera (R6.2/R6.3/R6.29) |

Total: 25 tareas; 23 escenarios cubiertos (15 [L] + 8 [S]); ningún
escenario queda sin tarea y ninguna tarea toca fichero existente.