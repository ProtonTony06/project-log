---
change: C6
artifact: spec
last_updated: 2026-10-25
status: draft
---

# Spec — C6 · Consulta keyset + filtros + detalle

> Especificación normativa del corte C6 para la implementación (sdd-design →
> tasks → apply → qa → verify). Fuente: `proposal.md` C6 (status **approved**
> — GATE aprobado por el usuario 2026-10-25, 6/6 puntos tal cual) y anclas
> de `explore.md` C6. Los literales de API/BD/errores se **citan** del
> Backend Schema (`docs/05_BACKEND_SCHEMA.md`, valido v1.0), del TRD
> (`docs/02_TRD.md`, valido v1.1), del AppFlow (`docs/03_APPFLOW.md`,
> valido v1.0), del PRD (`docs/01_PRD.md`, valido v1.2) y de la ficha C6 del
> `07_SLICING_PLAN.md` — nunca se redefinen (regla del 07). Lo heredado de
> C1–C5 queda intocable donde se indica. Código verificado en HEAD 43ea041
> (todo pushed); **sondas de solo-verificación contra la pila VIVA
> 2026-10-25** documentadas en §1.G-2 (la pila estaba UP 3×healthy con las
> 1877 filas heredadas de C5).

## 0. Decisiones de gate ya aprobadas (resumen vinculante)

| Gate | Decisión aprobada (2026-10-25) |
|---|---|
| P1 (DE GATE) | **Codec del cursor**: token Base64url `withoutPadding()` del JSON `{"r","i"}` con `r` = ISO-8601 del instante SIN truncar (micros preservados); tolerancia ESTRECHA en decodificación — cualquier fallo → 400 `filter_cursor_invalid`, sin oráculo de causa. El render del DTO MANTIENE el formatter Z de C2; la precisión completa es SOLO del cursor |
| P2 (DE GATE) | Los 3 codes 400 sin literal textual (`project_id` no-numérico, `from`/`to` no parseable, `limit` no-numérico) se REUTILIZAN del vocabulario EXISTENTE (`filter_invalid_value` / `filter_limit_invalid`, con su `field`) — **ningún code nuevo**; los `message` los fija este spec (herencia C5 Punto 6) |
| P3 (DE GATE) | S-3 de C4 se CIERRA en C6: escape server-side OBLIGATORIO de `%`, `_`, `\` (contrato §3.3, no elección) + aguja con `_` real insertada por ingesta + contra-caso de igualdad estricta |
| P4 (de paquete) | Sin Testcontainers en C6 (reevaluar en C7): la prueba del Done es 100% scriptable en el smoke [S] sobre la pila real |
| P5 (DE GATE) | **D1**: DOS records — `LogListItemDto` (9) y `LogDto` (11): la fuga de `exception`/`metadata` en la lista queda estructuralmente IMPOSIBLE (protección por compilar, no por disciplina) |
| P6 (de paquete) | Orden fail-fast de los 400: la lista EXACTA de 8 checks de §2.4 de la proposal, anclada AQUÍ como contrato del endpoint (§1.D); primer-fallo-gana, todo antes de tocar la BD |

## 0.2 Decisiones de SPEC (cierres de los huecos del §8 de la proposal)

Cierres que la proposal remitió a este spec (§8.1–§8.9). Son VINCULANTES
para design/tasks. Siguientes los IDs del encargo (bloques 1–10).

| # | Hueco | Cierre del spec | Justificación |
|---|---|---|---|
| S6-1 | §8.6 (naming/piezas) | Piezas EXACTAS: en RAÍZ de `com.projectlog.query`: `CursorCodec.java` (codec puro) y `FiltrosConsulta.java` (escape ILIKE, función pura — seam C7 §3.5). Sub-paquetes al patrón `projects`: `query/controller/LogsController.java`, `query/dto/{LogListItemDto,LogDto,LogsPaginaResponse}.java`, `query/repository/LogsQueryRepository.java`. **NO se toca `query/package-info.java`** (su texto YA declara el rol C6 — regla del encargo: NINGÚN fichero existente modificado) | Patrón `projects` (sub-paquetes) ratificado: desambigua `query.repository.LogsQueryRepository` del `ingest.LogsRepository` de escritura; el paquete raíz aloja lo PURO (sin Spring, sin BD) |
| S6-2 | Params exactos y semántica | Tabla de §1.C: nombres/case LITERALES de §3.3; `limit` defecto 50 (AUSENTE) y rango 1..200; rango `from`/`to` INCLUSIVE sobre `received_at` (>= / <=); `environment`/`level` IGUALDAD EXACTA (lista NO: el congelado no declara listas — solo igualdad `environment = :environment` / `level = :level`); `project_id` igualdad numérica; presente-y-vacío ("") en enums → inválido (patrón C3: `?status=` vacío es inválido, sin "sin filtro"); `text` "" → patrón `%%` (compatible con "se busca como `%<text>%`" — sin case especial) | Literal §3.3 verbatim; el SQL solo lleva comparaciones de IGUALDAD — una lista de levels exigiría inventar sintaxis fuera del congelado |
| S6-3 | §8.1/§8.2 (codec y tolerancia) | §1.F COMPLETA: encode con composición manual del JSON (orden determinista `r`,`i` — byte-exacto con el ejemplo del Schema, probe P-1); decode estricto con los 6 pasos (reject de `=`, JSON estricto, exactamente 2 claves, `r` textual con `Instant.parse` — SOLITAISO Z, `i` integral ≥ 1); el instante NO se valida contra BD; cursor con `r` futuro → 200 página vacía (semántica keyset legítima, sin case especial) | P1 aprobado; tolerar variantes falsificaría el contrato "opaco" (§8.2 recomendado "no", ratificado) |
| S6-4 | Estamento SQL ejecutable | §1.G: el literal `(:x IS NULL OR ...)` de §3.3 es INEJECUTABLE tal cual con placeholders posicionales en PG17 (sonda P-2: `could not determine data type of parameter $1`); se RESUELVE con `CAST(? AS tipo)` en la ocurrencia IS-NULL (patrón R-4 de C5: el CAST por necesidad de binding ENTRA al literal pinado) + la guarda del keyset **D-C6-1** (§0.3). Binding POSICIONAL con `JdbcTemplate` (patrón C3/C5), orden de 16 `?` documentado; `LIMIT ? + 1` VERBATIM (sonda P-5: válido) con el Java bindeando `limit`; metadata → `rs.getString("metadata")` → `JsonNode` con `readTree` | Evidencia de pila (sondas §1.G-2); NamedParameterJdbcTemplate descartado (no existe en la casa y sus placeholders van por-ocurrencia igual) |
| S6-5 | §8.3 (escape ILIKE) | Orden OBLIGATORIO `\` PRIMERO, luego `%`, luego `_` (escapar `%`→`\%` antes de `\`→`\\` DOBLARÍA las barras recién insertadas — E6.13 lo pina); SIN `ESCAPE '\'` explícito en el estamento (el `\` es el escape DEFAULT de LIKE/ILIKE en PG — el congelado no lo lleva; el pin NO lo contiene); el parámetro `:text` llega YA envuelto escapado `%<text>%` (recomendación ratificada §8.3); helper `FiltrosConsulta.patronIlike(String)` (§1.H) | Contrato verbatim §3.3 "escapando `%`, `_` y `\`"; el orden es una CORRECCIÓN funcional de la lista del enunciado (que es enumeración de caracteres, no secuencia) |
| S6-6 | §8.5 constants (P2) | §1.I: constants `CODE_*`/`MSG_*` EXACTOS en `LogsController` (patrón C3); los 3 codes propuestos quedan ANCLADOS AQUÍ por spec (cuerpos completos en la tabla §1.I); `filter_from_to_inverted` SIN `field`; mensajes cortos en español, patrón C3 ("corta, español") | Anclados por spec según el encargo; `message` sin ancla doc — lo fija el spec (herencia C5 Punto 6) |
| S6-7 | §8.4 (orden fail-fast) | LA lista de 8 checks de §2.4 de la proposal queda anclada TAL CUAL como contrato (§1.D): `limit → project_id → environment → level → from → to → from>to → cursor`, TODOS antes de tocar la BD, primer-fallo-gana, `verifyNoInteractions` del repo (E6.10/E6.11) | P6 aprobado tal cual; el cursor queda ÚLTIMO (es el único parámetro que además decodifica; determinismo puro) |
| S6-8 | helper de error | `error(...)` private en `LogsController` — réplica EXACTA del de C3 (`LinkedHashMap`: `code`, `message`, `field` solo si aplica; `objectMapper.valueToTree`; `MediaType.APPLICATION_JSON`); constants package-private visibles al pin en el mismo paquete de estamento correspondiente | Patrón probado (ProjectsController/R5.10 de C5) |
| S6-9 | §8.5 (E6.x) | §2: 23 escenarios E6.1–E6.23 (IDs 1:1 con la proposal; el DUAL E6.11 de la proposal se FUNSIONA: pin SQL del estamento + `verifyNoInteractions` — mismo ID, ambos aspectos), ~15 [L] + 8 [S], parejas mock↔psql obligatorias (§2) | Cuenta de la proposal (~23) EXACTA |
| S6-10 | §8.7/§8.9 (sin cap; render truncado) | `text` y `cursor` SIN cap de longitud: el congelado no declara límite — NO se inventa (sin check de `length()`; el valor pasa tal cual al SQL/codec); el `timestamp`/`received_at` del DTO se RENDER truncado a segundos (formatter C2 vigente, ratificado C3 — ningún doc exige micros en la SALIDA; los ejemplos §3.3 van a segundos); la precisión COMPLETA es SOLO del cursor (P1); divergencia de render R-6.i documentada (bajo, ratificado de casa) | §6.4/§2.4 de las propuestas; los ejemplos del congelado son el ancla del render |

### 0.3 D-C6-1 — Declaración FORMAL de divergencia con el SQL literal de §3.3

El estamento VERBATIM de Schema §3.3, evaluado tal cual con parámetros
posicionales y sin cursor, es INEJECUTABLE/INCORRECTO por DOS causas
independientes (ambas verificadas con sondas de solo-lectura contra la pila
viva, §1.G-2). La divergencia queda DECLARADA y CERRADA con esta
ratificación:

1. **Inferencia del placeholder en `... IS NULL`**: un placeholder sin tipo
   que aparece SOLO en la posición de comparación con `IS NULL` NO tiene
   contexto de tipo — PG17 responde `ERROR: could not determine data type
   of parameter $1` (sonda P-2/CASO-A y CASO-B). Los placeholders sin tipo
   SÍ se infieren cuando la MISMA posición está restringida por contexto de
   columna (sonda P-4/CASO-T2 y CASO-T1 para ILIKE, CASO-L2 para LIMIT).
   **Resolución**: `CAST(? AS <tipo>) IS NULL` en la ocurrencia IS-NULL de
   las 7 ramas (el tipo es EXACTAMENTE el de su columna/semántica:
   `bigint` × 2 → project_id y cursor... ver §1.G). Precedente de casa R-4
   (C5): el `CAST(? AS jsonb)` por necesidad de binding ENTRÓ al literal
   pinado de C5 — mismo estatus aquí.
2. **La rama keyset sin guarda NULL**: el congelado lleva `AND
   (received_at < :cursor_r OR (received_at = :cursor_r AND id <
   :cursor_i))` SIN el patrón `(x IS NULL OR ...)`. Con parámetros NULL
   (primera página, sin cursor) las comparaciones evalúan NULL
   (lógica 3-valorada) y el `AND` EXCLUYE TODAS las filas: la primera página
   respondería VACÍA SIEMPRE — contradice el MISMO congelado (flujo 4 paso 1
   del AppFlow: la primera página SIN cursor existe y el flujo la consume) y
   la ratificación propia de la proposal (§2.1: ":cursor_r/:cursor_i NULL →
   la rama del keyset no filtra"). **Resolución**: envolver la rama con la
   MISMA forma del congelado: `(CAST(:cursor_r AS timestamptz) IS NULL OR
   (received_at < :cursor_r OR (received_at = :cursor_r AND id <
   :cursor_i)))`.

La divergencia es MÍNIMA (el cuerpo de la rama queda VERBATIM; la guarda es
el patrón del propio congelado), JAMÁS altera la semántica de la página con
cursor, y DEBE citarse en el Javadoc del estamento y en el pin SQL (E6.11).
El verify de C6 la pina como literal del spec. Alternativa descartada:
sentinela de instante extremo en el binding (fuera del rango de
`TIMESTAMPTZ` de PG o frágil según el driver) y SQL dinámico (convención C3
prohibida — E1 del explore ratificada).

## 1. Requisitos normativos (RFC 2119)

En este documento **MUST/DEBE**, **MUST NOT/NO DEBE**, **SHOULD** y **MAY**
se interpretan según RFC 2119. Cada requisito es verificable por al menos un
escenario de la sección 2.

### 1.A Estructura y dependencias

- **R6.1** — TODO el código nuevo del corte DEBE vivir en el paquete
  `com.projectlog.query` (package-by-feature). Piezas EXACTAS (nombres
  finales, cierre S6-1):
  1. `query/CursorCodec.java` — codec puro Base64url/JSON del token (§1.F).
  2. `query/FiltrosConsulta.java` — `patronIlike` (función pura del escape,
     §1.H; seam de C7: los filtros del stream usan "exactamente la misma
     semántica" — §3.5).
  3. `query/controller/LogsController.java` — LOS 2 mappings y validación
     MANUAL fail-fast.
  4. `query/dto/LogListItemDto.java` — record de los 9 campos (§1.E).
  5. `query/dto/LogDto.java` — record de los 11 campos (§1.E).
  6. `query/dto/LogsPaginaResponse.java` — record wrapper `{items,
     next_cursor}` (§1.E).
  7. `query/repository/LogsQueryRepository.java` — lecturas de `logs` con
     SQL inline pin-testable (§1.G).
- **R6.2** — Cero cambios en `backend/pom.xml`: NO se añade
  `starter-validation` (validación MANUAL, patrón C3), NO Testcontainers
  (P4 del gate), NO `pg_trgm`, NO NINGUNA otra dependencia
  (NamedParameterJdbcTemplate DESCARTADO, S6-4). **Cero migraciones**:
  `V1__init.sql` INTOCABLE (los índices existentes BASTAN, §1.G).
- **R6.3** — Ficheros EXISTENTES modificados por C6: **NINGUNO**. En
  particular: `ingest/**` (incl. `LogsRepository` — INSERT-only + `LogValidado`
  byte-a-byte, SIN métodos de lectura añadidos; `LogValidado` NO se reutiliza
  para leer filas), `config/**` (cadena 2 ya cubre `/api/v1/panel/logs[/...]`
  — "C6 nace behind 401", S19, SIN toque; `CorsConfig`, `WebSocketConfig`
  intactos), `auth/**` (`EXPIRES_AT_FORMATTER` REUTILIZADO por referencia,
  jamás editado), `keys/**`, `projects/**` (solo lectura del patrón),
  `streaming/**`, `common/**`, `resources/db/migration/V1__init.sql`,
  `application.yml` / `application-test.yml` / `.env.example` /
  `compose.yaml` (C6 no introduce env vars), `query/package-info.java`
  (byte-a-byte — su texto YA declara "Feature de consulta (C6)"), y
  TODOS los tests de C1–C5 (sin diff; re-corrida 108/108 → verde).
- **R6.4** — Ficheros NUEVOS de test: en `backend/src/test/java/com/projectlog/query/`
  — `LogsControllerMvcTest.java` (E6.1–E6.10, E6.14, E6.15),
  `repository/LogsQuerySqlTest.java` (E6.11, MISMO paquete que el repo —
  patrón `LogsRepoSqlTest`, sin reflection), `CursorCodecTest.java` (E6.12),
  `FiltrosConsultaTest.java` (E6.13), `dto/DtosLogsPinTest.java` (E6.2) — y
  `scripts/qa_c6_smoke.sh` NUEVO (patrón `qa_c5_smoke.sh`).

### 1.B Endpoints y autenticación (frontera: cadena 2, sin toque)

- **R6.5** — `LogsController` DEBE exponer EXACTAMENTE 2 mappings
  (clase con `@RequestMapping("/api/v1/panel/logs")`, patrón C3):

```java
@GetMapping                      // GET /api/v1/panel/logs — listado keyset
@GetMapping("/{id}")             // GET /api/v1/panel/logs/{id} — detalle
```

  Filas VERBATIM de Schema §3.2 (CONTRATO, cita literal):

  > | GET | `/api/v1/panel/logs` | JWT | Listado keyset con filtros combinables | query (§3.3) → 200 `{items, next_cursor?}` · 400 · 401 |
  > | GET | `/api/v1/panel/logs/{id}` | JWT | Detalle de log | → 200 `LogDto` · 401 · 404 |

  - NUNCA `/api/v1/logs` (la ingesta vive aparte — surface §3.2).
  - Cadena 2 del `SecurityConfig` (`securityMatcher("/api/v1/panel/**")`,
    `@Order(2)`) los cubre SIN editar NADA de seguridad: sin JWT → 401
    `invalid_token` del `RestAuthenticationEntryPoint` (CD-4 de C2,
    intocable). Un JWT jamás es rechazado por la superficie de panel y una
    API key jamás autentica allí (§5 del Schema: "Un JWT jamás autentica la
    ingesta" en la superficie inversa igualmente aplicable por diseño; la API
    key NO está en la cadena 2 → header `X-API-Key` ignorado → 401
    `invalid_token`).
- **R6.6** — El `id` no-numérico en el path (`/logs/abc`): Spring NO mapea la
  variable de ruta y responde su error por defecto de matching/argumento —
  se RATIFICA sin toque (fuera del enum de codes del §3.1; patrón EXACTO de
  `GET /projects/{id}` de C3, ya operado así). NO se le añade handler, NO se
  le asigna escenario (sería un code sin ancla, CD-3.9).

### 1.C Query params — nombres exactos y semántica

- **R6.7** — Los 8 params EXACTOS de Schema §3.3 (verbatim: "`project_id?`,
  `environment?`, `level?`, `from?`, `to?`, `text?` (todos combinables),
  `cursor?`, `limit?` (defecto **50**, máx. **200**, TRD §2.7)"). Los
  nombres son los LITERALES (NUNCA `date_from`/`date_to`/`q` — explore,
  corregido y ratificado). Todos llegan como `String`
  (`@RequestParam(required = false)` — R-6.c: binding directo a `long`
  produciría el 400 genérico de Spring, fuera del contrato), y se PARSEAN
  manualmente en el controller (R6.9). Semántica por param:

| Param | Filtra sobre | Semántica EXACTA |
|---|---|---|
| `project_id` | `project_id` (BIGINT) | Igualdad ESTRICA y numérica: parse manual de `String` → `long` con `Long.parseLong` (el fallo del parse → 400, §1.D check 2, code `filter_invalid_value`, `field` "project_id" — NUNCA el 400 genérico de Spring, cierre R-6.c) |
| `environment` | `environment` | Igualdad EXACTA sobre el enum cerrado `development|staging|production` — case-sensitive, SIN normalizar minúsculas (patrón C3: "Minúsculas no se normalizan (CHECK literal)"); fuera de enum (incl. presente-y-vacío "") → 400 (check 3) |
| `level` | `level` | Igualdad EXACTA sobre `TRACE|DEBUG|INFO|WARN|ERROR|FATAL` — case-sensitive; idem (check 4) |
| `from` | `received_at >= :from` (INCLUSIVE) | ISO-8601 CON offset: parse `OffsetDateTime.parse(valor)` (acepta `Z` y `±hh:mm`, RECHAZA el naive — patrón EXACTO S5-5 de C5, ya verificado); viaja al SQL como `OffsetDateTime` (PG normaliza a UTC al comparar con `received_at` TIMESTAMPTZ — TRD §2.6: "siempre se normaliza a UTC al guardar"); no parseable → 400 (check 5) |
| `to` | `received_at <= :to` (INCLUSIVE) | Ídem, con `to` (check 6); `from > to` (ambos parseados, sobre los Instants) → 400 `filter_from_to_inverted` (check 7); `from == to` LEGAL (rango de punto) |
| `text` | `message ILIKE :text` | Sin comodines del cliente (NUNCA se aceptan): el backend construye `%<text>%` CON escape de contrato (§1.H, S6-5); SIN cap de longitud (S6-10, §4) |
| `cursor` | rama keyset (§1.G) | Token OPACO para el cliente (nunca lo parsea el cliente — literal §3.3); codificado con `CursorCodec` (§1.F); ilegible → 400 (check 8) |
| `limit` | `LIMIT :limit + 1` | Rango 1..200 inclusive; defecto 50 cuando el param está AUSENTE; no-numérico o fuera de rango → 400 (check 1) |

  - Proyectos ARCHIVED: sus logs SIGUEN consultables (§4 regla 12 verbatim:
    "sus logs siguen consultables y filtrables") — NO hay filtro por `status`
    de proyecto en este endpoint.
  - El nombre del proyecto NO viaja en la fila: el panel mapea por `project_id`
    contra `GET /projects` que ya cargó (AppFlow flujo 4 paso 3 — literal del
    congelado, NO decisión de C6).

### 1.D Orden fail-fast de los 400 de GET /logs (contrato, P6)

- **R6.8** — LA lista de checks EXACTA (anclada tal cual de la proposal §2.4;
  el congelado NO fija el orden — patrón C3/C5 "el enum ANTES de la BD —
  regla 10"). TODOS los checks DEBE ejecutarse ANTES de invocar la BD
  (contrato: si alguno falla, el repositorio NUNCA se llama —
  `verifyNoInteractions` del repo, E6.10/E6.11). Primer fallo gana (cada
  check → su pareja code/message/field EXACTA del §1.I):

  1. `limit` — presente NO numérico (incl. "" vacío) O fuera de 1..200 → 400
     `filter_limit_invalid`, `field: "limit"`.
  2. `project_id` — presente NO numérico → 400 `filter_invalid_value`,
     `field: "project_id"`.
  3. `environment` — presente fuera de enum (o "") → 400
     `filter_invalid_value`, `field: "environment"`.
  4. `level` — presente fuera de enum (o "") → 400 `filter_invalid_value`,
     `field: "level"`.
  5. `from` — presente NO parseable (incl. naive sin offset) → 400
     `filter_invalid_value`, `field: "from"`.
  6. `to` — presente NO parseable → 400 `filter_invalid_value`,
     `field: "to"`.
  7. `from > to` (ambos parseados, sobre los Instants) → 400
     `filter_from_to_inverted`, SIN `field`.
  8. `cursor` — presente ILEGIBLE (cualquier fallo de los 6 pasos del
     decode, §1.F) → 400 `filter_cursor_invalid`, `field: "cursor"`.

  Solo tras los 8: `LogsQueryRepository.listar(...)` ejecuta el estamento
  único. Con `text` NO hay check (sin cap — S6-10). El detalle (`/{id}`) no
  lleva checks de query params (solo el 404 de la consulta).

### 1.E DTOs (D1 del gate — fuga estructuralmente imposible, P5)

- **R6.9** — DOS records EXACTOS (literal §3.3 verbatim: "**LogListItemDto**:
  sin `exception` ni `metadata` — **no se transportan en la lista** (TRD
  §2.4)"; "los campos del listado **más** `exception` (string o `null`) y
  `metadata` (objeto JSON o `null)`"):

```java
public record LogListItemDto(
        Long id, Long project_id, String level, String environment,
        String timestamp, String received_at, String message,
        String source, String correlation_id) {
}   // 9 componentes EXACTOS — JAMÁS exception ni metadata (pin E6.2)

public record LogDto(
        Long id, Long project_id, String level, String environment,
        String timestamp, String received_at, String message,
        String exception, Object metadata /*JsonNode — ver R6.10*/,
        String source, String correlation_id) {
}   // 11 componentes EXACTOS; exception y metadata nullables
```

  (los componentes usan los NOMBRES LITERALES de §3.3 — `project_id`,
  `correlation_id` — Jackson serializa el accessor con SU nombre sin
  `@JsonProperty`, patrón `ProjectDto`; el cierre del tipado de `metadata`
  es el R6.10: `com.fasterxml.jackson.databind.JsonNode`).

- **R6.10** — `metadata` en `LogDto` DEBE ser `JsonNode` (Jackson, ya en el
  classpath): el RowMapper del detalle la convierte del jsonb SIN
  re-interpretación (objeto JSON EXACTO serializado tal cual — no Map, no
  string, no re-parseo posterior). Conversión EXACTA (cierre S6-4; §1.G):
  `rs.getString("metadata")` (el driver PG devuelve el TEXTO del jsonb) →
  `null` si la columna es NULL → si no, `objectMapper.readTree(texto)` —
  NUNCA `readValue(Map.class)` (fabricaría re-interpretación) y NUNCA el
  String crudo (fabricaría un string JSON entrecomillado). `exception` es
  `String` nula o el texto tal cual (hasta 128 KiB — solo el detalle la
  transporta).
- **R6.11** — Wrapper de la respuesta del listado:

```java
public record LogsPaginaResponse(List<LogListItemDto> items, String next_cursor) {
}
```

  Emitido con `ResponseEntity.ok(pagina)` (Jackson serializa el record con
  las claves `items`/`next_cursor`, SIN espacios). `next_cursor: null` es la
  marca "fin de resultados" (literales §3.3: "`next_cursor: null` = última
  página"). **La clave DEBE aparecer EXPLÍCITA con `null`** (NO OMITIRSE
  cuando es null): el ejemplo del §3.3 la muestra y el toggle "Cargar más"
  del flujo 4 paso 4 del AppFlow la consume ("si `next_cursor` es `null`, el
  botón queda deshabilitado"); Jackson la emite SIN configuración alguna
  (default: nulls incluidos).
- **R6.12** — El listado VACÍO (sin cursor y sin resultados) es estado
  VACÍO LEGÍTIMO: 200 `{"items": [], "next_cursor": null}` (AppFlow §4:
  "No hay logs que cumplan los filtros") — NUNCA 404 ni 400.
- **R6.13** — Fechas del DTO (render): `timestamp` y `received_at` van como
  `String` YA formateadas con el formatter de C2 REUTILIZADO por referencia
  (`LoginController.EXPIRES_AT_FORMATTER`, `yyyy-MM-dd'T'HH:mm:ss'Z'` —
  truncado a segundos; patrón EXACTO de `ProjectDto`/`FILA` de C3). El
  formatter JAMÁS se toca; la precisión COMPLETA del instante es SOLO del
  cursor (P1/S6-3; divergencia de render R-6.i documentada — BAJO,
  ratificado de casa). El RowMapper del repo formatea; el controller nunca
  toca fechas (patrón C3 literal).
- **R6.14** — Detalle y listado JAMÁS comparten mapper recortado: 2
  RowMappers/2 métodos distintos del repo (protección de la fuga del P5 —
  sin `@JsonInclude` por vistas, sin recortes post-serialización).

### 1.F El codec del cursor (P1) — `query/CursorCodec.java`

- **R6.15** — Pieza PURA: `public final class CursorCodec` con SOLO métodos
  estáticos y NINGUNA dependencia de Spring ni BD (unit-testable sin Spring,
  E6.12). Firma EXACTA (cierre S6-3):

```java
/**
 * C6 (P1 del gate 2026-10-25; Schema §3.3: "next_cursor es un token OPACO
 * para el cliente; internamente codifica (received_at, id) en Base64url".
 * R-6.a (hallazgo CRÍTICO del explore): la clave "r" lleva el instante SIN
 * truncar — el formateador de casa (EXPIRES_AT_FORMATTER) trunca a segundos
 * y PG TIMESTAMP guarda MICROSEGUNDOS (todo batch comparte un solo
 * received_at, regla 9); un cursor truncado SALTARÍA las filas del MISMO
 * instante que la última fila entregada (ni received_at < r ni = r —
 * desaparecerían entre páginas, Done literal roto).
 * Formato EXACTO del token: JSON compacto {"r":"<Instant ISO-8601>",
 * "i":<id>} → Base64url WITHOUT-PADDING (Base64.getUrlEncoder()
 * .withoutPadding()). El cliente lo trata como OPACO (nunca lo parsea —
 * literal §3.3).
 */
public static String codificar(OffsetDateTime receivedAt, long id);
/** Decodificación ESTRECHA (P1): cualquier fallo → CursorInvalidoException;
 *  el controller la traduce al 400 filter_cursor_invalid (§1.D check 8,
 *  SIN oráculo de causa). El instante NUNCA se valida contra la BD. */
public static Cursor decodificar(String token);
/** Moneda del decode: el instante EXACTO + el id. */
public record Cursor(Instant r, long i);
/** Excepción única de tolerancia estrecha. */
public static final class CursorInvalidoException extends RuntimeException {
}
```

- **R6.15-a** — ENCODE, fórmula EXACTA (cierre S6-3): (1) `r` =
  `receivedAt.toInstant().toString()` — ISO-8601 UTC del instante COMPLETO
  (sin fracciones si PG no las trajo, `.123456` a micros si sí — longitud
  variable; NUNCA truncado, NUNCA re-formateado por el formatter de C2);
  (2) JSON por COMPOSICIÓN MANUAL (NO `ObjectMapper` en el encode: un
  `Map.of` no garantiza el orden de claves y el formato es byte-exacto)
  `{\"r\":\"<r>\",\"i\":<id>}` — orden determinista `r`-luego-`i`;
  (3) `Base64.getUrlEncoder().withoutPadding()` sobre los bytes UTF-8 del
  JSON. El instante no puede contener `"` ni `\` (ISO-8601: letras/dígitos/
  `-:.+`), la composición manual es segura. El token de los datos de VIVA
  ejemplifica byte-exacto al del Schema con una página sin fracciones:

  > `codificar(parse("2026-10-16T22:31:04Z"), 12301)` DEBE ser EXACTAMENTE
  > `eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0` (el ejemplo del
  > Schema §3.3 decodificado: `{"r":"2026-10-16T22:31:04Z","i":12301}` —
  > verificado con sonda P-1).

- **R6.15-b** — DECODE, TOLERANCIA ESTRECHA (P1; los 6 pasos EN ORDEN —
  cualquier fallo en CUALQUIER paso → la MISMA `CursorInvalidoException`,
  sin oráculo de causa):
  1. `token == null || token.isEmpty()` → inválido.
  2. Rechazo EXPLÍCITO del padding: el token contiene `'='` → inválido
     (garantía de no-padding INDEPENDIENTE de la laxitud del JDK; y
     `Base64.getUrlDecoder()` rechaza el alfabeto clásico `+`/`/` — los
     caracteres fuera del alfabeto URL-safe → `IllegalArgumentException`
     → inválido).
  3. `Base64.getUrlDecoder().decode(token)` → bytes; fallo → inválido.
  4. `readTree` del JSON con un `ObjectMapper` estático PROPIO del codec; no
     parseable como JSON → inválido.
  5. Estructura ESTRECHA: el nodo raíz debe ser objeto; debe tener `r` e `i`
     (path textual); `r` DEBE ser textual (`isTextual()`) y parseable con
     `Instant.parse` (ISO-8601 estricto: solo instante UTC con `Z`; un
     `+02:00` NO es instante-parseable y la rechaza — el codec propio
     jamás la emite); `r` NUNCA se re-deriva ni trunca (los micros perdidos
     no se recuperan y romperían la igualdad `received_at = :cursor_r`);
     claves EXTRA (≠ 2 claves) → inválido (el token solo puede venir del
     codec propio — tolerancia estrecha sin ancla para variantes);
     `i` DEBE ser `isIntegralNumber()` y convertible a long exacto
     (`longValueExact()`, overflow → inválido) y `>= 1` (los ids BIGINT de
     logs son todos positivos — un `i` ≤ 0 no lo emite el codec propio).
  6. Resultado: `Cursor(r, i)` con el instante EXACTO al micro/nano dado.
  - El `r` decodificado NO DEBE validarse contra la BD (no hay oráculo de
    instante; un cursor "de futuro" — r posterior a cualquier fila — DEBE
    responder 200 con `items: []` y `next_cursor: null`: es una comparación
    keyset legítima, sin semántica especial — cierre §8.2, recomendación
    ratificada).
- **R6.15-c** — Viaje del valor SIN truncar: el `received_at` llega del
  RowMapper como `rs.getObject("received_at", OffsetDateTime.class)`
  (precisión COMPLETA del driver) → a `FilaConsulta.received_at` (§1.G) →
  `codificar(...)`; JAMÁS pasa por el formatter de C2 antes del codec.

### 1.G Estamentos SQL y repository (D-C6-1 aplicado)

- **R6.16** — `query/repository/LogsQueryRepository.java` (`@Repository`,
  `JdbcTemplate` + `ObjectMapper` por constructor, SQL inline de constantes,
  `@Transactional` defensa documentada mono-sentencia — patrón EXACTO
  `ProjectsRepository`) DEBE tener EXACTAMENTE estos 2 estamentos
  (constants `static final String` package-private, para el pin E6.11 en el
  MISMO paquete — patrón `LogsRepoSqlTest`; la correspondencia con el
  literal `:x` del congelado es 1:1 con los CASTs del D-C6-1):

```java
/** (C6) Estamento ÚNICO del listado keyset (SQL literal del Schema §3.3,
 *  con 2 divergencias mínimas DECLARADAS en D-C6-1 del spec: los CASTs de
 *  las 7 ocurrencias IS-NULL (necesarios: PG17 rechaza la inferencia del
 *  placeholder sin tipo en `? IS NULL` — sonda P-2) y la GUARDA del keyset
 *  con el MISMO patrón `(:x IS NULL OR ...)` del congelado (la rama desnuda
 *  evaluaría NULL y EXCLUIRÍA TODAS las filas de la primera página).
 *  Índices: logs_dashboard_idx es el cascarón EXACTO de la ruta con
 *  project_id (+environment/level) y del orden keyset (índice-scan
 *  descendiente SIN sort — V1 líneas 45–47, "el índice maestro del
 *  dashboard"); sin proyecto, logs_received_idx (V1 línea 49) resuelve el
 *  orden y la degradación "logs_received_idx + scan filtrado" es
 *  "aceptable en v1" POR LITERAL del Schema (§2 índices) — ni migración
 *  ni índice nuevo. El trigram GIN de message (V2) queda RESERVADO y
 *  comentado en V1: NO se activa en C6. */
static final String SELECT_LISTADO =
        "SELECT id, project_id, level, environment, timestamp, received_at,"
                + " message, source, correlation_id"
                + " FROM logs"
                + " WHERE (CAST(? AS bigint) IS NULL OR project_id = ?)"
                + " AND (CAST(? AS text) IS NULL OR environment = ?)"
                + " AND (CAST(? AS text) IS NULL OR level = ?)"
                + " AND (CAST(? AS timestamptz) IS NULL OR received_at >= ?)"
                + " AND (CAST(? AS timestamptz) IS NULL OR received_at <= ?)"
                + " AND (CAST(? AS text) IS NULL OR message ILIKE ?)"
                + " AND (CAST(? AS timestamptz) IS NULL"
                + " OR (received_at < ? OR (received_at = ? AND id < ?)))"
                + " ORDER BY received_at DESC, id DESC"
                + " LIMIT ? + 1";

/** (C6) Detalle: 11 columnas (las 9 del listado + exception + metadata).
 *  404 por fila-0 → Optional.empty() del controller; NUNCA
 *  queryForObject aquí (la fila-0 es el caso legal del 404 — patrón C4). */
static final String SELECT_POR_ID =
        "SELECT id, project_id, level, environment, timestamp, received_at,"
                + " message, exception, metadata, source, correlation_id"
                + " FROM logs WHERE id = ?";
```

  CERO SQL dinámico en NINGÚN punto (E1 ratificada): la única construcción
  permitida es NINGUNA (los OR con params NULL son la forma — no hay ramas
  a probar por combinación de filtros); el estamento es ÚNICO para TODAS
  las combinaciones y la primera página (sin cursor) usa EL MISMO estamento
  con los 3 params del keyset NULL.

- **R6.17** — FIRMAS EXACTAS y orden de binding (16 placeholders `?`; el
  binding POSICIONAL con `JdbcTemplate` patrón C3/C5 — `Object[]` en el
  orden EXACTO de aparición):

```java
/** C6 (R6.10/R6.16): estamento ÚNICO del listado (D-C6-1: el SELECT del
 *  keyset necesita el guard — ver §0.3). Fila moneda: log item + instante
 *  RAW del received_at (precisión COMPLETA para el cursor — R6.15-c) + id. */
@Transactional // defensa documentada (patrón R3.2): mono-sentencia
public List<FilaConsulta> listar(Long projectId, String environment,
        String level, OffsetDateTime from, OffsetDateTime to,
        String patronTexto, OffsetDateTime cursorR, Long cursorI,
        int limit);

/** C6: detalle por id — Optional.empty() → 404 del controller (R6.18).
 *  NUNCA queryForObject (la fila-0 es el caso LEGAL del 404). */
@Transactional
public Optional<LogDto> porId(long id);

/** Moneda del listado para el next_cursor (record PÚBLICO anidado). */
public record FilaConsulta(LogListItemDto item, OffsetDateTime received_at,
        long id) {
}
```

  ORDEN EXACTO de los 16 `?` (patrón R5.11-d de C5):
  `projectId, projectId, environment, environment, level, level, from, from,
  to, to, patronTexto, patronTexto, cursorR, cursorR, cursorI, limit` — los
  7 valores de filtro van duplicados en sus 2 posiciones (IS NULL / igualdad:
  placeholders SEPARADOS — son los `?` per-ocurrencia del D-C6-1, y el CAST
  del lado IS-NULL ancla la inferencia), `cursorR` en sus 3 posiciones
  (guard IS NULL + `<` + `=`), `cursorI` en 1 (`id < ?` — el contexto de la
  columna `id` infiere el tipo), y `limit` al FINAL (el `LIMIT ? + 1` es
  VERBATIM del congelado — sonda P-5: `LIMIT $1 + 1` es válido e infiere).
  `projectId`/`cursorI` viajan como `Long` (null cuando no hay filtro),
  `cursorR`/`from`/`to` como `OffsetDateTime` (driver → timestamptz —
  patrón R5.11-d), `patronTexto` como `String` YA envuelta escapada
  (S6-5), `limit` como `int` (el Java NO suma +1: el `+ 1` vive EN el
  literal — cierre S6-4).

- **R6.18** — Reglas del repo: (a) NUNCA `queryForObject` en las 2 lecturas
  (fila-0 es caso legal); (b) el repo NO valida NADA (los 8 checks viven en
  el controller — frontera de capas); (c) `porId`: 404 por
  `Optional.empty()`; el listado devuelve la Lista CRUDA de hasta
  `limit+1` filas (el recorte al `limit` y el next_cursor viven en el
  controller — §1.J); (d) NINGÚN método toca la tabla `projects` (el detalle
  NO pre-consulta el proyecto: no existe caso "log con proyecto borrado" —
  FK CASCADE, regla 3); (e) el repositorio JAMÁS añade métodos al
  `ingest.LogsRepository` (frontera C5: INSERT-only; R6.3).
- **R6.19** — RowMappers (2, separados — R6.14):
  (listado) `FILA_LISTADO`: `new FilaConsulta(new LogListItemDto(...9
  campos...), rs.getObject("received_at", OffsetDateTime.class),
  rs.getLong("id"))` — las 2 fechas del item con el formatter C2
  (`rs.getObject(col, OffsetDateTime.class).format(LoginController
  .EXPIRES_AT_FORMATTER)`), `id`/`project_id` `Long`,
  `source`/`correlation_id` opcionales null.
  (detalle) `FILA_DETALLE`: los 9 + `rs.getString("exception")` (nullable) +
  `metadata`: `rs.getString("metadata")` (pgjdbc devuelve el TEXTO del
  jsonb — cierre R6.10) → `null` si la columna es NULL; no-null →
  `objectMapper.readTree(texto)` (JsonNode objeto EXACTO). Si el texto
  JSON viniera malformado (imposible por construcción: la columna jsonb de
  PG solo guarda JSON válido), el readTree es una RuntimeException no
  capturada → 500 honesto (política S6 de C4; nunca se silencia).

#### 1.G-2 Sondas de verificación en pila (solo-lectura, 2026-10-25 — evidencia del §0.2/§0.3)

| Sonda | Comprobación | Resultado |
|---|---|---|
| P-0 | pila UP 3×healthy; `count(*) = 1877` en `logs` (filas heredadas de C5); fila 1877 con `received_at` `.724014` (MICROS reales en pila — R-6.a materializado); 67 `DISTINCT received_at` / 1877 filas (regla 9: grupos de ~28 por instante — la precisión del cursor es ESENCIAL) | CONFIRMADO |
| P-1 | el token `eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0` del ejemplo del Schema decodifica EXACTO `{"r":"2026-10-16T22:31:04Z","i":12301}` (formato del encode R6.15-a) | CONFIRMADO |
| P-2 | `SELECT id FROM logs WHERE ($1 IS NULL OR project_id = $2) LIMIT 1` con binds sin tipo → `ERROR: could not determine data type of parameter $1` (la rama IS-NULL sin CAST es INEJECUTABLE); ídem con el MISMO `$1` en ambas posiciones | CONFIRMADO (justifica el CAST de D-C6-1) |
| P-3 | `CAST($1 AS bigint) IS NULL OR project_id = $2` → Parse+Describe+plan OK (la validación falló solo por el VALUE del bind — "invalid input syntax", es decir: la INFERENCIA quedó resuelta por el CAST) | CONFIRMADO |
| P-4 | `received_at >= $1` untyped (sin cast) infiere timestamptz del contexto de la columna (OK); `message ILIKE $1` untyped OK; `LIMIT $1 + 1` untyped OK (devuelve limit+1 filas) | CONFIRMADO (closures del estamento) |

Estas sondas son de SOLO-LECTURA (SELECT/psql); no modificaron estado; el
verify de C6 puede REPLICARLAS si necesita evidencia fresca (R6.21).

### 1.H El escape ILIKE (S-3 se cierra aquí — P3)

- **R6.20** — `query/FiltrosConsulta.java` (pieza pura, estática — cierre
  S6-1): clase `public final class FiltrosConsulta` con la firma EXACTA:

```java
/**
 * C6 (S-3 de C4 se CIERRA aquí — P3 del gate; §3.3 verbatim: "«text» se
 * recibe SIN comodines y se busca como %<text>% (escapando %, _ y \)":
 * el cliente NUNCA aporta comodines; el escape es server-side CONTRATO.
 * ORDEN OBLIGATORIO: \ primero, luego %, luego _ (escapar %/_ antes que \
 * DOBLARÍA las barras recién insertadas — E6.13 lo pina). El "\" es el
 * escape DEFAULT de LIKE/ILIKE en PG (el congelado no declara ESCAPE ''));
 * el resultado va YA Envuelve con %...% y el parámetro :text del estamento
 * llega ya escapado+envuelto (S6-5). Función pura: sin BD, sin Spring
 * (unit E6.13). Seam C7: la misma semántica de filtros del stream (§3.5)
 * la reutiliza.
 */
public static String patronIlike(String texto)
```

  COMPORTAMIENTO EXACTO (5 fases): (1) `texto == null || texto.isEmpty()`
  NO se le llega desde el controller CON null (presente-y-vacío es LEGAL y
  llega como `""` — el patrón resultante es `"%%"`: matchea todos los
  messages — legítimo, compatible literal, sin case especial, S6-2);
  (2) `texto.replace("\\", "\\\\")` (primero — cada `\` del cliente pasa a
  doble backslash); (3) `.replace("%", "\\%")`; (4) `.replace("_", "\\_")`;
  (5) devolver `"%" + resultado + "%"`. El texto NO se trimma NI se
  normaliza. Aguja de contrato: un mensaje que contenga `_` literal NO DEBE
  hacer wildcard (contra-caso E6.13/E6.18 — cierre de la S-3 de C4:
  "aguja con `_` opera como comodín en LIKE"; el archive de C6 la citará
  COMO CERRADA si E6.18 pasa).
- **R6.20-a** — En los asertos psql del smoke, las agujas con `_` NUNCA van
  a un `LIKE`/`ILIKE` de psql sin escapar (lección S-3 literal: escapar en
  el script o comparar por `=`/`position()`).

### 1.I Literales de respuesta — codes, messages, constants (S6-6)

- **R6.21** — Constants EXACTAS en `LogsController` (patrón C3
  `CODE_*`/`MSG_*`; package-private para los pins):

```java
static final String CODE_FILTER_FROM_TO_INVERTED = "filter_from_to_inverted";
static final String CODE_FILTER_INVALID_VALUE    = "filter_invalid_value";
static final String CODE_FILTER_LIMIT_INVALID    = "filter_limit_invalid";
static final String CODE_FILTER_CURSOR_INVALID   = "filter_cursor_invalid";
static final String CODE_NOT_FOUND               = "not_found";

private static final String MSG_FROM_TO_INVERTED =
        "El rango temporal no es válido: from es posterior a to";
private static final String MSG_ENVIRONMENT_INVALID =
        "Valor de environment no válido; use development, staging o production";
private static final String MSG_LEVEL_INVALID =
        "Valor de level no válido; use TRACE, DEBUG, INFO, WARN, ERROR o FATAL";
private static final String MSG_PROJECT_ID_INVALID =
        "El filtro project_id debe ser un número";
private static final String MSG_FROM_INVALID =
        "El filtro from debe ser una fecha ISO-8601 con zona horaria";
private static final String MSG_TO_INVALID =
        "El filtro to debe ser una fecha ISO-8601 con zona horaria";
private static final String MSG_LIMIT_INVALID =
        "El filtro limit debe ser un número entre 1 y 200";
private static final String MSG_CURSOR_INVALID =
        "El parámetro cursor no es válido";
/** C3, reutilizado por referencia (MSG_NOT_FOUND de ProjectsController). */
static final String MSG_NOT_FOUND = "No existe (quizás fue eliminado)";
```

  (el literal de MSG_NOT_FOUND se duplica aquí como constante propia del
  paquete query — patrón IngestAuthenticationEntryPoint CODE_* de C5:
  cada escritor con sus constants — para que el paquete query NO dependa
  de projects; el LITERAL es el MISMO, anclado en AppFlow §5 fila 404:
  "No existe (quizás fue eliminado)").
- **R6.22** — Tabla de cuerpos de error EXACTOS (cuerpo estándar §3.1
  `{code, message, field?}` — error() helper, S6-8):

| Caso | HTTP | code | message | field |
|---|---|---|---|---|
| `limit` no-numérico o fuera de 1..200 | 400 | `filter_limit_invalid` | `El filtro limit debe ser un número entre 1 y 200` | `limit` |
| `project_id` presente no-numérico | 400 | `filter_invalid_value` | `El filtro project_id debe ser un número` | `project_id` |
| `environment` fuera de enum (o "") | 400 | `filter_invalid_value` | `Valor de environment no válido; use development, staging o production` | `environment` |
| `level` fuera de enum (o "") | 400 | `filter_invalid_value` | `Valor de level no válido; use TRACE, DEBUG, INFO, WARN, ERROR o FATAL` | `level` |
| `from` presente no parseable | 400 | `filter_invalid_value` | `El filtro from debe ser una fecha ISO-8601 con zona horaria` | `from` |
| `to` presente no parseable | 400 | `filter_invalid_value` | `El filtro to debe ser una fecha ISO-8601 con zona horaria` | `to` |
| `from > to` | 400 | `filter_from_to_inverted` | `El rango temporal no es válido: from es posterior a to` | — (SIN field) |
| `cursor` ilegible (cualquier causa) | 400 | `filter_cursor_invalid` | `El parámetro cursor no es válido` | `cursor` |
| `id` inexistente en detalle | 404 | `not_found` | `No existe (quizás fue eliminado)` | — |

  - Los 4 codes con ancla literal §3.3 (verbatim "400 si: `from > to`
    (`filter_from_to_inverted`), `environment`/`level` fuera de enum
    (`filter_invalid_value`), `limit` fuera de 1..200
    (`filter_limit_invalid`), cursor ilegible (`filter_cursor_invalid`). 400
    → el panel conserva la última lista válida").
  - Los 3 cases sin literal textual ANCLADOS AQUÍ por spec (encargo):
    `project_id` no-numérico y `from`/`to` no-parseable →
    `filter_invalid_value` con su `field` culpable; `limit` no-numérico →
    `filter_limit_invalid` (MISMO code del rango). NINGÚN code nuevo fuera
    de esta tabla (§3.1 cierra el vocabulario del API; CD-3.9/S5-2).
  - `filter_invalid_value` ya EXISTE (C3, `ProjectsController`) — se
    REUTILIZA (su uso en panel es exactamente el previsto: "query fuera de
    enum"); el `not_found` idem.
- **R6.23** — `error(...)` private en `LogsController` — réplica EXACTA del
  helper de C3 (`LinkedHashMap` con `code`, `message`, `field` solo si
  aplica; `objectMapper.valueToTree`; `MediaType.APPLICATION_JSON`).

### 1.J Flujo del listado (LIMIT+1 — trim y next_cursor)

- **R6.24** — El controller, TRAS pasar los 8 checks, DEBE ejecutar en
  ESTE orden exacto: (1) llamada ÚNICA a
  `listar(projectId, environment, level, from, to, patronTexto, cursorR,
  cursorI, limit)`; (2) si la lista viene con `limit + 1` filas → hay
  `next_cursor`, y `items = filas.subList(0, limit)` (RECORTE exacto) y
  `next_cursor = CursorCodec.codificar(filas.get(limit - 1).received_at(),
  filas.get(limit - 1).id())` — la ÚLTIMA fila ENTREGADA (literal §3.3 del
  ejemplo: el cursor codifica el par de la última fila de la página
  ENTREGADA, no la fila extra) — recorte ANTES de construir DTOs ya
  construidos (el RowMapper ya mapeó la extra: descartada, no transportada);
  (3) si la lista NO lleva fila extra → `next_cursor = null` (fin de
  resultados) y `items = filas`; (4) 200 `ResponseEntity.ok(new
  LogsPaginaResponse(items, nextCursor))`.
  `next_cursor` NUNCA presente en la última página vacía del recorrido
  (cierre del R6.12: `null` explícito en la clave).

### 1.K Tests (RL) y smoke ([S])

- **R6.25** — Suite [L] (patrón C2/C3/C5: `@WebMvcTest(LogsController.class)`
  + `@Import(SecurityConfig.class, JwtAuthFilter.class, RestAuthenticationEntryPoint.class)`
  + `@MockitoBean` de `LogsQueryRepository` (+ lo que el padrón
  `PanelAuthMvcTest` use para el JWT — TokenService según ese patrón)
  + `@ActiveProfiles("test")` con `application-test.yml` INTOCABLE) +
  unitarios puros (codec E6.12, escape E6.13, pins SQL E6.11/D-C6-1, pin de
  DTOs E6.2) + smoke [S] `scripts/qa_c6_smoke.sh` NUEVO (patrón
  `qa_c5_smoke.sh`) con el guion de §2.6 y la salida
  `E6.n: PASS | FAIL | BLOQUEADO (detalle)` por ID (exit 0/1/2 como
  C3/C4/C5).
- **R6.26** — Higiene heredada: NINGUNA clase nueva loguea credenciales (no
  hay keys en `query`, pero el padrón se mantiene: sin loggers en las clases
  nuevas — JAMÁS loguear JWT, cursor ni token). Nota: la suite de C6 no
  necesita escenario nuevo de higiene (no hay secretos en las lecturas —
  el smoke de C5 ya cubre la superfluicie de ingesta).
- **R6.27** — Smoke guion-base (de las parejas): recomposición ÚNICA
  `podman compose up -d --force-recreate` (NUNCA `down -v` — pgdata con las
  1877 filas heredadas), `psql -qAt`, conteos RELATIVOS antes/después de
  cada escenario (R-6.h: los absolutos romperían el smoke), nombres de
  needles sufijados por ejecución (`smoke-c6-<TS>-*`), tmpdir 700 con trap
  (JWT y la key de ingesta SOLO a tmpdir — jamás a consola), smoke-of-the-smoke
  antes de la canónica (sondeo `GET /me` sin token → 401 `invalid_token` de
  la cadena 2), agujas `smoke_c6_<TS>` con escape psql (`\_`/`=` —
  R6.20-a), y los escenarios E6.16–E6.23 en orden de ejecución cronológico
  (§2.6).
- **R6.28** — La pila viva con las 1877 filas heredadas NO se restaura ni se
  purga en el smoke de C6 (conteos relativos — R-6.h; el patrón C5 lo
  certificó con `--force-recreate` SIN caída de datos).

### 1.L No-regresiones C1–C5 — y deudas heredadas

- **R6.29** — C6 NO DEBE romper NADA de C1–C5: la suite completa
  `mvn test` (108 test vigentes en C5) re-corrida en builder DEBE seguir
  108/108 VERDE más los N de C6 (= 108 + N); `SecurityConfig.CorsConfig`,
  `V1__init.sql`, `ingest/**`, `streaming/**`, `auth/**`, y los tests
  vigentes SIN diff. Los pins de `LogsRepoSqlTest` (C5, INSERT de logs)
  re-corridos intocados. Deuda A-NOTADA del archive de C6 (SI E6.18 pasa):
  S-3 de C4 queda **CERRADA** (el archivo lo citará con el E6.18 como
  evidencia). NO se toca la deudas RETENIDAS (smoke docker compose canónico
  → C9; 401 ISO-8859-1 → C8/C9; nginx `client_max_body_size` → C9).

## 2. Escenarios de aceptación (E6.1–E6.23)

Cada escenario es verificable en al menos un test [L] y/o paso del smoke
[S]. Los IDs son ESTABLES para qa/verify y REPLICA N 1:1 a la proposal §6
(el E6.11 dual de la proposal se FUNSIONA en un único ID con ambos
aspectos).

### 2.1 MVC [L] — 200 listado y detalle

- **E6.1** `listadoShapeExactoItemsYNextCursor` [L] — con repo mock
  entregando 51 filas y limit 50: GET `/api/v1/panel/logs` válido (JWT) →
  **200** con el shape EXACTO `{"items": [LogListItemDto...],
  "next_cursor": "…"}` — el `next_cursor` del ÚLTIMO entregado (mock: el
  codec recibe el par (r, i) de la fila 50 del mock — ArgumentCaptor del
  codec); y con lista de 50 (sin fila extra) → `next_cursor: null`
  EXPLÍCITO en el JSON (la CLAVE aparece con null, R6.11).
- **E6.2** `pinDeLosDtosLogListItemDto9YLogDto11` [L] — pin de los records:
  `LogListItemDto` con EXACTAMENTE los 9 componentes con nombres literales
  (`id, project_id, level, environment, timestamp, received_at, message,
  source, correlation_id`) — la FUGA de `exception`/`metadata` es IMPOSIBLE
  de compilar (R6.9); `LogDto` con EXACTAMENTE los 11 (los 9 + `exception:
  String` + `metadata: JsonNode` — pin del tipo). Patrón
  `ProjectDtoSerializationTest`.
- **E6.3** `detalleResponde200ConLos11CamposYMetadataNodoExacto` [L] — mock
  con exception no-nula y metadata no-nula: 200 `LogDto` completo
  (exception = el string tal cual; metadata = JsonNode con el objeto
  EXACTO — las claves del jsonb intactas — R6.10); con exception/metadata
  nulas → los 2 campos con `null` EXPLÍCITO (R6.13 del wrapper no aplica:
  en LogDto los nulls incluidos, default Jackson).
- **E6.4** `detalleIdInexistenteResponde404NotFound` [L] — mock vacío → 404
  con el cuerpo EXACTO `{"code":"not_found","message":"No existe (quizás
  fue eliminado)"}` sin `field` (R6.22) — NUNCA lista vacía ni redirección.

### 2.2 MVC [L] — 400 de filtros (anclados §3.3 y los 3 nuevos)

- **E6.5** `environmentYLevelFueraDeEnumResponde400FilterInvalidValue` [L] —
  `?environment=qa` y por otra request `?level=error` (minúscula, sin
  normalización) y `?environment=` (vacío) → 400 `filter_invalid_value` con
  su `field` y el message EXACTO de R6.22 (cada uno).
- **E6.6** `fromMayorToResponde400FromToInverted` [L] —
  `?from=2026-01-02T00:00:00Z&to=2026-01-01T00:00:00Z` → 400
  `filter_from_to_inverted` SIN field, message EXACTO; frontera
  `from == to` → LEGAL (NO 400; llega al repo — verify call).
- **E6.7** `limitFueraDeRangoONaNResponde400FilterLimitInvalid` [L] —
  `?limit=0`, `?limit=201`, `?limit=abc`, `?limit=` → 400
  `filter_limit_invalid`, field `limit`, message EXACTO (el MISMO cuerpo
  para rango y no-numérico — anti-oráculo S6-6); `?limit=1` y
  `?limit=200` → LEGAL.
- **E6.8** `projectIdFromToNoParseablesResponde400FilterInvalidValue` [L]
  (los 3 P2, anclados por SPEC) — `?project_id=abc` → 400
  `filter_invalid_value` field `project_id`; `?from=no-fecha` → campo
  `from`; `?to=no-fecha` → campo `to`; y `?from=2026-10-16T22:31:04` (NAIVE
  sin offset) → el MISMO 400 de `from` (patrón S5-5: el parse SÓLO acepta
  con offset).
- **E6.9** `cursorIlegibleResponde400FilterCursorInvalid` [L] — LOS 4
  sub-casos del §3.3 + los 3 extras del P1 (mismo body EXACTO, anti-oráculo,
  con field `cursor`): (a) no-Base64url (`"ñ%20%2B"` o `+`/`/` clásicos);
  (b) Base64url con padding `=`; (c) decodifica pero NO JSON
  (`"no-json"`); (d) JSON sin `r` o sin `i`; (e) JSON con `r` NO parseable
  (`"no-es-instant"` / con offset `+02:00`); (f) JSON con claves extra
  (tolerancia estrecha — R6.15-b paso 5); (g) `i` no entero / `-1`.
- **E6.10** `ordenFailFastDe8ChecksEsDeterminista` [L] — query con TODOS
  los params mal (limit abc Y project_id abc Y level error Y from naive Y
  to no-fecha Y cursor basura) → el 400 es SIEMPRE el del check 1
  (`filter_limit_invalid`) — no el 2º/3º; con limit OK y 2 fallos
  (project_id + level) → SIEMPRE project_id (check 2); con from naive Y
  from>to-parseado — solo `from` mal → campo `from`; con from/to parseados
  invertidos Y cursor basura → SIEMPRE `filter_from_to_inverted` (check 7
  ANTES del 8); y en TODO 400: `verifyNoInteractions` del repo (los checks
  DEBE corren antes de la BD — R6.8).

### 2.3 Unit/pin [L] — codec, escape, SQL, DTOs

- **E6.11** ` pinDeLos2EstamentosEjecutablesYVerificacionDeFrontera` [L] —
  patrón `LogsRepoSqlTest` (constants package-private en el MISMO paquete
  de test `query/repository`): pin de `SELECT_LISTADO` literal EXACTO del
  §1.G (9 columnas del listado en ese orden; los 7 CASTs ESPECÍFICOS en sus
  posiciones IS-NULL — `bigint`/`text`/`text`/`timestamptz`/`timestamptz`/
  `text`/`timestamptz` — D-C6-1; la guarda del keyset con el patrón `(:x IS
  NULL OR ...)` y AMBAS comparaciones `<` + `=` + `id < `; `ORDER BY
  received_at DESC, id DESC`; `LIMIT ? + 1` VERBATIM; 16 placeholders
  contados), y de `SELECT_POR_ID` (11 columnas exactas — `exception` y
  `metadata` SÍ presentes — y `WHERE id = ?`); pin de NO-`ESCAPE`
  explícito (el estamento NO contiene la palabra ESCAPE — S6-5). Además
  `verifyNoInteractions` (família E6.10) que el repo JAMÁS se llama ante
  cualquier 400.
- **E6.12** `codecRoundTripConMicrosPreservados` [L] (OBLIGATORIO, P1) —
  unit puro del `CursorCodec` SIN Spring ni BD: (a) round-trip completos
  con instante a MICROSECONDS: `2026-10-16T22:31:04.123456Z` → encode →
  decode → MISMO instante exacto (ningún dígito perdido — R-6.a);
  (b) el pin del EJEMPLO del Schema: `codificar(parse("2026-10-16T22:31:04Z"),
  12301)` == `eyJyIjoiMjAyNi0xMC0xNlQyMjozMTowNFoiLCJpIjoxMjMwMX0` (byte-
  exacto — sonda P-1) y `decodificar(token)` == `(2026-10-16T22:31:04Z,
  12301)`; (c) round-trip con token SIN fracciones y CON nanos de
  conveniencia; (d) truncar el instante → se pierden los micros y el
  re-encode produce token DIFERENTE (el round-trip con truncado NO es
  semánticamente neutral — el codec NO trunca nunca).
- **E6.13** `escapeIlikeOrdenYContracasoDeAguja` [L] — pin del `patronIlike`:
  `"a_b"` → `%a\\_b%` (el `_` queda escapado, NO wild-card); `"100%"` →
  `%100\\%%`; `"a\\b"` → `%a\\\\b%`; ORDEN: un texto con `_` y `\` juntos
  produce el ESCAPE CORRECTO solo con `\`-primero (matriz del cierre S6-5);
  y el contra-caso de la AGUJA: `patronIlike("smoke_c6_X")` produce
  `%smoke\_c6\_X%` — un mensaje `needleXc6` (con el `_` sustituido por `X`,
  mismo estilo) NO matchearía ese patrón (cierre S-3 — el mismo patrón se
  usa en E6.18 contra la pila).
- **E6.14** `limitDefecto50YLegal200AlRepo` [L] — sin `limit` → el repo
  recibe `50` (ArgumentCaptor); `?limit=200` → recibe `200`; y la rama
  del trim (E6.1) usa limit+1 filas del mock — el extra se descarta.

### 2.4 MVC [L] — seguridad (frontera cadena 2 intocable)

- **E6.15** `ambosEndpointsNacenTras401InvalidToken` [L] — SIN JWT → 401
  `invalid_token` del entry point del PANEL (RestAuthenticationEntryPoint,
  CD-4 intocable — JAMÁS `api_key_invalid`); idem con `X-API-Key: plk_xxx`
  Y SIN JWT (la API key jamás autentica el panel — la cadena 2 la ignora);
  con JWT válido → pasa al controller (la firma del flujo del 200).
  NO toca NINGUNA línea de seguridad (R6.3).

### 2.5 Smoke [S] — `scripts/qa_c6_smoke.sh` (guion exacto, patrón C5)

> Pre-fase (smoke-of-the-smoke, sin ID propio — parte de E6.16): recomposición
> ÚNICA `podman compose up -d --force-recreate` (NUNCA `down -v` — pgdata) +
> pila healthy + sondeo heredado `GET /me` sin token → 401 `invalid_token`
> (cadena 2 intacta — la pareja [S] de E6.15) + login → 200 (JWT a tmpdir) +
> `POST /projects` → 201 `smoke-c6-<TS>-a` (captura incondicional del id) +
> `POST /projects/{id}/api-key/regenerate` → 201 (key → tmpdir, jamás a
> consola). Helper `curl_ingest KEY …` (patrón C5) y `curl_get JWT …`
> (nuevo, para los GET de panel).

- **E6.16** filtros combinables en pila [S] — ingesta con la key de agujas
  (batch de ingesta NORMAL, patrón C5: mensajes `smoke-c6-<TS>-k1..k3` con
  env/level distinguidos, p. ej. production/ERROR) → `GET /api/v1/panel/logs`
  (JWT) con `project_id=<id>&environment=production&level=ERROR` → **200**
  con los ids de las agujas y SOLO ellas; psql (needle de la lista): la
  MISMA cláusula WHERE combinada sobre psql devuelve los mismos ids
  (comparación REST↔psql de igualdad estricta); también la página SIN
  filtros (página 1 del recorrido) → 200 con 50 primeras y shape
  `{"items":[...],"next_cursor":"…"}` (pareja [S] de E6.1).

- **E6.17** keyset ESTABLE con batch partido (el Done literal) [S] —
  página 1 con `limit=3` (los grupos de la pila comparten received_at —
  sonda P-0: 67 instants/1877 filas — así una página de 3 CORTA DENTRO de
  un mismo instante con altísima probabilidad; el guion VERIFICA la
  división y si no la produjo, re-intenta con el límite mínimo que la
  produzca — el escenario exige que exista una página 1 cuyo ÚLTIMO
  entregado comparte `received_at` (a micros, psql) con las filas NO
  entregadas) → captura `received_at` a MICROSEGUNDOS (psql de la última
  entregada) y el `next_cursor` → (a) psql: el token decodificable es
  `r == received_at` COMPLETO (a micros, NO truncado a segundos —
  pareja [S] del E6.12a); (b) ingesta de UN batch nuevo de K logs entre
  páginas (received_at NUEVO más reciente) → página 2 con el cursor de
  la 1 → asertos EXACTOS: NINGÚN id repetido entre páginas, NINGÚN id de
  la página 1 reaparece, y la PRIMERA fila de la página 2 es la fila
  SIGUIENTE en el orden keyset (id adyacente bajo el MISMO received_at a
  micros — psql); (c) SIN cursor truncado: si el codec truncara, las filas
  del mismo instante que la última entregada saltarían — el aserto de
  (b) lo materializa (el Done literal: "insertar logs nuevos entre páginas
  NO duplica ni salta").

- **E6.18** aguja `_` de ILIKE (cierre S-3) [S] — ingesta individual con
  mensaje `smoke_c6_<TS>_timeout` (aguja con `_` literal) y otra
  `smoke-c6-<TS>-sin-guion-bajo` con el MISMO PREFIJO pero sin `_` (el
  contra-caso: si `_` actuase como comodín, este matchearía) →
  `GET /panel/logs?text=smoke_c6_<TS>_timeout` → 200 con SOLA la aguja
  (igualdad estricta de conteo=1) y el contra-caso NO matcheado; (y el
  needle psql por `=`/`position()` — NUNCA la aguja cruda en un LIKE
  psql — R6.20-a). Pareja [S] del E6.13.

- **E6.19** rango from/to con offset e inclusive [S] — captura de una fila
  real en pila (frontera `from == received_at` de una aguja) →
  `GET /panel/logs?from=<t>+02:00&to=<t>+02:00` (offset NO-UTC) → **200**
  y el MISMO resultado que su equivalente en `Z` (psql idéntico); los
  extremos INCLUSIVE: un log con `received_at == from` (y otra con
  `== to`) salta en la página (psql: los 2 ids presentes) — sonda P-4.

- **E6.20** detalle en pila [S] — ingesta individual con `exception` no
  nula y `metadata` objeto real (`{"span":"checkout","attempt":2}—
  patrón C5) → `GET /panel/logs/{id}` → 200: `exception` = el texto
  COMPLETO (hasta 128 KiB solo en detalle) y `metadata` = el OBJETO EXACTO
  (needle de claves del jsonb legible: `span`/`attempt`); (b) `GET
  /panel/logs/999999999` → **404** con `{code: not_found, message: ...}`.
  Pareja [S] de E6.3/E6.4.

- **E6.21** codes 400 reales en pila [S] — (JWT, pila) 5 variantes con
  needle del cuerpo EXACTO (code Y field/message de R6.22):
  `?from=2026-01-02..&to=2026-01-01..` → `filter_from_to_inverted`;
  `?level=error` → `filter_invalid_value` (level); `?limit=0` →
  `filter_limit_invalid`; `?cursor=basura` → `filter_cursor_invalid`;
  `?project_id=abc` → `filter_invalid_value` (project_id — el propuesto).
  Pareja [S] de E6.5/E6.6/E6.7/E6.8/E6.9.

- **E6.22** limit en frontera en pila [S] — `?limit=200` → la página
  trae 200 (delta/comparativa psql sobre el recorrido) y `next_cursor`
  presente si hay fila 201; `?limit=201` → **400** `filter_limit_invalid`;
  SIN limit → la página trae 50 (pareja [S] de E6.14/E6.7).

- **E6.23** fin de resultados y recorrido completo con cursor [S] —
  ingesta de aguja con `limit` pequeño (p. ej. 2) sobre UN SOLO batch
  (todos comparten received_at — regla 9, sonda P-0) → recorrer TODAS las
  páginas encadenando `cursor=next_cursor` hasta recibir
  `next_cursor: null` EXPLÍCITO → asertos: los ids entregados SIN
  duplicados, SIN saltos (cubre el set completo del batch — la última
  página llega vacía de extras con `next_cursor: null` correcto), y el
  "Cargar más" del panel sería estable en cada punto (AppFlow paso 4).

Salida: una línea `E6.n: PASS | FAIL | BLOQUEADO (detalle)` por ID;
exit 0/1/2 como C3/C4/C5.

### 2.6 Orden de parejas mock↔psql OBLIGATORIAS

| Pareja | [L] | [S] |
|---|---|---|
| 1 | E6.1 (shape/next_cursor trim) | E6.16/E6.23 |
| 2 | E6.2 (pin DTOs) | E6.20 (campos reales en pila) |
| 3 | E6.3 (detalle 11 campos) | E6.20 |
| 4 | E6.4 (404 not_found) | E6.20 |
| 5 | E6.5 (filter_invalid_value) | E6.21 |
| 6 | E6.6 (from_to_inverted) | E6.21 |
| 7 | E6.7 (filter_limit_invalid) | E6.22 |
| 8 | E6.9 (cursor ilegible) | E6.21 |
| 9 | E6.12 (codec micros) | E6.17 (keyset estable real) |
| 10 | E6.13 (escape `_`) | E6.18 (aguja `_` en pila) |
| 11 | E6.14 (limit defecto/200) | E6.22 |
| 12 | E6.15 (401 invalid_token) | pre-fase del smoke (sondeo /me) |

Criterio de pareja (R6.25): todo code/literal de los Done del 07 certificado
en [L] DEBE tener su [S] contra la pila; los demás [L] puro.

## 3. Contratos citados (fuente única de verdad)

| Literal | Fuente (congelada) |
|---|---|
| Paths EXACTOS `GET /api/v1/panel/logs` y `GET /api/v1/panel/logs/{id}` (JWT 401/400/404; NUNCA `/api/v1/logs`) | Schema §3.2 (verbatims del §1.B) |
| Shape del listado: `{items, next_cursor?}`; `next_cursor` opaco, `null` = fin; LogListItemDto SIN exception/metadata (no se transportan — TRD §2.4); nombre del proyecto NO viaja (panel mapea por project_id) | Schema §3.3 (ejemplo JSON + bullets; TRD §2.4; AppFlow flujo 4 paso 3) |
| LogDto del detalle = campos del listado más exception/metadata (objeto JSON y/o null) | Schema §3.3 verbatim |
| SQL literal del listado (keyset con el patrón `(:x IS NULL OR ...)` + orden + LIMIT) — con las 2 divergencias mínimas DECLARADAS en D-C6-1 del spec | Schema §3.3 (cuerpo del SQL, verbatim) + sonda P-2 a P-5 |
| KEYSET: `ORDER BY received_at DESC, id DESC`; offset descartado (ADR-10); coste plano; páginas estables; nunca la tabla entera en memoria (métrica 3) | Schema §4 regla 13 (verbatim) + TRD §2.4 + ADR-10 |
| Cursor: opaco, `(received_at, id)` en Base64url = ejemplo descodificable `eyJy…MX0` → `{"r":"2026-10-16T22:31:04Z","i":12301}` | Schema §3.3 (sonda P-1) |
| Filtros: todos combinables; from/to ISO-8601 con offset normalizado a UTC, INCLUSIVE ambos extremos sobre received_at (regla 9: columna canónica) | Schema §3.3 + §4 regla 9; TRD §2.6 |
| text sin comodines del cliente, `%<text>%` escapado (`%`, `_`, `\`) con ILIKE | Schema §3.3 verbatim |
| 400: `filter_from_to_inverted` (from > to), `filter_invalid_value` (env/level fuera de enum), `filter_limit_invalid` (limit fuera 1..200), `filter_cursor_invalid` (ilegible) — el panel conserva la última lista válida | Schema §3.3 verbatim (párrafo "400 si:") |
| limit: defecto 50, máx. 200 (rango 1..200) | Schema §3.3 + §4.1 (página de consulta) + TRD §2.7 |
| Cuerpo de error estándar `{code, message, field?}`; errores SIEMPRE explícitos (métrica 2); 200/400/401/404 (409/413 no aplican en C6) | Schema §3.1 + TRD §2.6 |
| Enum cerrado con validación en el borde: `level/environment` CHECK y 400 antes de insertar (patrón de la regla 10 aplicado a la lectura: los 400 viven en el controller) | Schema §4 regla 10 + §2 de logs (CHECK V1 líneas 39–40) |
| received_at: columna canónica de orden/rango/keyset; el batch comparte UN solo received_at y el lote queda desempatado por `id` creciente (regla 9 — coherente con leer `id DESC`) | Schema §4 regla 9 verbatim |
| Proyectos ARCHIVED: sus logs SIGUEN consultables y filtrables (sin filtro de status en /logs) | Schema §4 regla 12 verbatim |
| Logs inmutables: C6 NO añade UPDATE ni DELETE; ni resumen ni include=summary | Schema §4 reglas 4 + §3.3 (P1 → C10) |
| Normalización a UTC al guardar; el render del DTO truncado a segundos es la convención vigente de casa | TRD §2.6 verbatim + C2/C3 (formatter EXPIRES_AT_FORMATTER reutilizado) |
| Índices: `logs_dashboard_idx` (maestro del dashboard, cascarón exacto), `logs_received_idx`, degradación level/environment sin proyecto "aceptable en v1", trigram GIN (V2) reservado | Schema §2 índices (verbatim) + V1 líneas 45–59 |
| 404 del detalle: mensaje "Este registro no existe (o fue eliminado)" (flujo) / "No existe (quizás fue eliminado)" (§5) | AppFlow flujo 5 paso 6 + §5 fila 404 (C3) |
| 400 → el panel conserva la última lista válida (C8 consumo) | AppFlow flujo 4 paso 6 + §5 fila 400 |
| Cargar más / fin de resultados / cursor reiniciado con nuevo filtro | AppFlow flujo 4 pasos 2/4 (el cursor NO se rechaza ante filters cambiados — R-6.d documentado) |
| Dedup por id (flujo 6b): respeta orden `received_at DESC, id DESC` (la REST de C6 es su condición) | AppFlow flujo 6b paso 3 |
| P0-6 (consulta desde el panel: nivel/rango/texto/proyecto+entorno combinables; paginado; detalle completo) | PRD §4 P0-6 verbatim |
| Fichas: "SQL literal + cursor keyset + LIMIT+1 → {items, next_cursor?}"; Done: keyset estable, filtros combinados y detalle con smoke | `07_SLICING_PLAN.md` ficha C6 (verbatim) |
| Patrón: controller fino sin @Valid, error() {code,message,field?}, Optional por 404 sin queryForObject, SQL inline de constants, COLS/FILA una fuente | `ProjectsController`/`ProjectsRepository` (C3) + `LogsRepository` (C5) — leídos en HEAD 43ea041 |
| Estamento de INSERT de logs (frontera C5 INSERT-only) | `ingest/LogsRepository.java` (C5, byte-a-byte) |
| Cadena de ingesta X-API-Key para las agujas del smoke | C5 (cadena 1, `IngestGuardFilter`/202 — sin tocar) |

## 4. Fuera de alcance (explícito — NO cubierto por C6)

- **Streaming real (C7)**: ni HandshakeInterceptor, ni `LiveBroadcaster`, ni
  frames `batch`/heartbeat/backoff; `streaming/**` (Echo + WebSocketConfig)
  byte-a-byte. C6 SOLO fija la semántica de filtros compartida (§3.5/ADR-7,
  "exactamente la misma semántica") y deja el seam `IngestListener` de C5
  sin beans suscriptos (C7 decide).
- **Flujo 6b (llegada en vivo del CLIENTE)**: del lado servidor NO hace
  nada — la única ligadura es que el keyset estable del Done es condición
  del dedup por `id`; el resto es C7/C8.
- **Resumen y DELETE (C10)**: ni `include=summary` ni `GET /projects/{id}/summary`;
  NI NINGÚN DELETE — `DELETE /logs/{id}` (flujo 8b), purga
  `/projects/{id}/logs` (8c), CASCADE (8a) y el botón "Eliminar registro"
  del flujo 5 paso 5 son C10. NINGÚN UPDATE (regla 4).
- **Front (C8)**: la tabla, "Cargar más", estados de pantalla §4 del
  AppFlow, query-string compartible — C6 solo expone la REST.
- **Nginx (C9)**: sin cambios en `compose.yaml` ni proxy;
  `client_max_body_size` es deuda de C9 (heredada).
- **Migraciones**: ninguna — V1 INTOCABLE; el trigram GIN (`V2`,
  pg_trgm) queda COMENTADO y reservado (documentado, NO activado).
- **Testcontainers (P4)**: NO en C6 (reevaluación real apuntada a C7).
- **Rate limiting 429 (ADR-26), retención, backups, multiusuario** — fuera
  de alcance v1 (§7 del Schema). **Sin caps de `text`/`cursor`** (S6-10 —
  el congelado no los declara: límites sin ancla NO se inventan).
- **Sin normalización case de enums** (patrón C3); **sin cap de errores**
  ni oráculo de causa en el 400 de cursor (anti-oráculo P1); **sin
  oráculo del 401** (un único body `invalid_token` de la cadena 2).

## 5. Trazabilidad

| Requisito | Escenarios | Verificado por |
|---|---|---|
| R6.1/R6.4 (piezas nuevas, sin edit de package-info) | E6.1–E6.15 | review de ficheros + suite [L] |
| R6.2/R6.3 (frontera, cero diffs) | E6.15 + re-corrida | suite C1–C5 108/108 |
| R6.5/R6.6 (endpoints + cadena 2) | E6.1, E6.4, E6.15 | MVC + smoke |
| R6.7/R6.8 (params + orden fail-fast) | E6.5–E6.10, E6.21, E6.22 | MVC + smoke |
| R6.9/R6.10/R6.11 (DTOs 9/11 y wrapper) | E6.1, E6.2, E6.3 | MVC + pin DTO |
| R6.13 (formatter C2 reutilizado) | E6.1/E6.3 (pin JSON del render) | pin + smoke E6.20 |
| R6.15 (codec P1) | E6.9, E6.12, E6.17 | unit + smoke |
| R6.16/R6.17/R6.19 (SQL + método + mapping) | E6.11 (pin), E6.16/E6.20 (pila) | pin + smoke |
| R6.20 (escape S-3) | E6.13, E6.18 | unit + smoke |
| R6.21/R6.22/R6.24 (códigos y flujo trim) | E6.1, E6.5–E6.10, E6.21–E6.23 | MVC + smoke |
| R6.25–R6.29 (suite, smoke, no-regresiones) | E6.1–E6.23 | suite + smoke |

Criterios Done de la ficha C6 (proposal §6), trazados:
1 (E6.1, E6.2, E6.22, E6.23), 2 (E6.17 + E6.12), 3 (E6.16, E6.18, E6.19),
4 (E6.3/E6.4, E6.20), 5 (E6.5–E6.10, E6.21), 6 (E6.15),
7 (§4 — fronteras del corte).

Mapa de verificación qa:

| Familia | IDs | Tipo |
|---|---|---|
| MVC 200 listado/detalle | E6.1, E6.3, E6.14 | [L] `@WebMvcTest` |
| MVC 400/404/401 | E6.4, E6.5, E6.6, E6.7, E6.8, E6.9, E6.10, E6.15 | [L] `@WebMvcTest` |
| Unit/pin puros | E6.2, E6.11, E6.12, E6.13 | [L] sin Spring |
| Pila real | E6.16, E6.17, E6.18, E6.19, E6.20, E6.21, E6.22, E6.23 | [S] curl+psql |

Total: **23 escenarios — 15 [L] + 8 [S]** (= propuesta §6 con E6.11
fusión). Parejas mock↔psql OBLIGATORIAS: §2.6 (12).