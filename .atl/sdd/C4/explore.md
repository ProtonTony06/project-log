---
change: "C4"
artifact: explore
last_updated: 2026-10-21
status: done
---

# Explore — C4 · API keys de ingesta

> Fase sdd-explore. Fuentes (solo lectura, congeladas): TRD §2.5/§2.6 y ADR-4
> (`docs/02_TRD.md`, valido v1.1), Schema §3.2/§3.3/§3.4/§4/§6.3
> (`docs/05_BACKEND_SCHEMA.md`, valido v1.0), AppFlow flujo 3 + §4/§5
> (`docs/03_APPFLOW.md`, valido v1.0), 04_UIUX.md (modales V8) y maqueta 05
> (`docs/mockups/05-proyecto-detail.html` — referencia UI, NO implementar
> paneles), 07_SLICING_PLAN.md (C4) y el cierre heredado de C3
> (`.atl/sdd/C3/archive-report.md`). Código real inspeccionado SIN modificar
> nada: `backend/src/main/java/com/projectlog/{projects,auth,config,keys}`,
> `backend/src/main/resources/db/migration/V1__init.sql`,
> `frontend/public/mockups/05-proyecto-detail.html`, `scripts/`.

## 1. Qué es C4 y su frontera

C4 crea la **feature de API keys de ingesta** del panel: dos endpoints sobre
proyectos existentes.

- **`POST /api/v1/panel/projects/{id}/api-key/regenerate`** (duplica como
  "generar": sirve con y sin key previa — AppFlow flujo 3) → 201
  `{key, prefix, created_at}` con la key completa visible **UNA única vez**.
- **`DELETE /api/v1/panel/projects/{id}/api-key`** (revocar) → 204, con
  `api_key_hash` / `api_key_prefix` / `key_created_at` → NULL.

Bajo el capó: generación cripto-aleatoria, guardado **solo por hash SHA-256**,
`api_key_prefix` identificadora, y primitivas de comparación en tiempo
constante que C5 reutilizará en el hot-path de ingesta.

**Frontera (lo que C4 NO hace):**

- **No hay ingesta**: `POST /api/v1/ingest` y `/ingest/batch` son C5. La
  cadena 1 de `SecurityConfig` sigue en `denyAll()` + 401 del entry point.
- **No hay paneles React ni implementación de modales UI** (C8): la maqueta 05
  y sus modales son SOLO referencia visual. La "revelación única" del Done es
  a nivel API (respuesta 201), no de pantalla.
- **No hay migración nueva**: columnas e índice parcial ya existen en V1
  (C1). `V1__init.sql` queda intocable.
- **No toca archivado** (C3, ya cerrado), ni `DELETE /projects/{id}` con
  CASCADE (C10/P1), ni summary (C10/P1), ni streaming (C7).
- **Deja preparado pero no implementa** el lookup `findByApiKeyHash` para C5
  (punto abierto §6.4).

## 2. Literales extraídos de los insumos (contrato, no se redefinen)

### 2.1 Formato de la key (TRD §2.6 y ADR-4; Schema §4.2)

> "Formato de la key: `plk_<43 chars Base64url>` de 32 bytes aleatorios
> (`SecureRandom`, ≈256 bits)." (TRD §2.6)

- **`plk_` + 43 chars Base64url → 47 chars totales**. Los 43 son la codificación
  Base64url SIN padding de 32 bytes: 32 bytes = 256 bits de entropía exactos
  (codificación unpadded de un grupo múltiplo de 3 nunca añade relleno:
  32·4/3 → ⌈⌉ = 44 con padding `=`, 43 sin él).
- Alfabeto Base64url: `[A-Za-z0-9_-]` (incluye `-` y `_`; el mockup solo
  muestra alfanuméricos por azar del ejemplo).
- Ancla triple: TRD §2.6, TRD ADR-4 ("API key: `SecureRandom` 256 bits,
  guardada por **hash SHA-256** + comparación en tiempo constante") y Schema
  §4.2 ("header `X-API-Key` (formato `plk_<43 chars Base64url>`, 32 bytes
  `SecureRandom`, ≈256 bits)").
- Ejemplo del mockup 05 (`docs/mockups/05-proyecto-detail.html`, modal de
  revelación): `plk_Ab3x7kQ9mT4vR2pW8yZcLd5NhJfE6sUuG1iXoBe0qAr` — 43 chars
  verificados tras `plk_`.

### 2.2 Hash y comparación (TRD §2.6; Schema §4.2)

> "Se guarda **solo el hash SHA-256** … el hash rápido permite **localizar el
> proyecto por índice en O(1)** … comparación final en tiempo constante
> (`MessageDigest.isEqual`)" (TRD §2.6 / Schema §4.2 API keys).

- Se guarda el **digest** SHA-256 (32 bytes) en la columna `api_key_hash`
  *(BYTEA: bytes binarios, no hex)*.
- La comparación en tiempo constante (`MessageDigest.isEqual`) es requisito
  del alcance C4 (07 §C4) aunque **solo la usa** el ingest de C5: la primitiva
  nace aquí, probada unitariamente.
- El hash es de la **key completa tal como viaja en el header**
  (`plk_` + 43, bytes UTF-8 del String completo): nada de los docs fija un
  hashing alternativo, pero C5 comparará contra lo que llegue en `X-API-Key`
  ⇒ debe fijarse en spec: hash del String íntegro de la key presentada.

### 2.3 `api_key_prefix` (Schema §2, tabla `projects`)

> "P. ej. `plk_Ab3xQw`: **primeros 10 caracteres de la key** (concreción del
> ejemplo del TRD §2.3); se muestra en el panel **sin revelar** la key;
> `NULL` junto al hash al revocar." (Schema §2)

- Prefijo = `key.substring(0, 10)` = `plk_` + primeros 6 chars codificados.
- Ejemplos literales que usan los docs: `plk_Ab3xQw` (Schema, AppFlow) y
  `plk_Ab3x…` (mockup) — son EJEMPLOS, no valores del sistema.

### 2.4 Endpoints y respuestas (Schema §3.2 filas; TRD §2.5; AppFlow flujo 3; Schema §3.3)

| Método | Ruta literal | Auth | → Respuesta | Códigos |
|---|---|---|---|---|
| POST | `/api/v1/panel/projects/{id}/api-key/regenerate` | JWT | **201 `{key, prefix, created_at}`** — "la key completa, una única vez" | 401 · **404** |
| DELETE | `/api/v1/panel/projects/{id}/api-key` | JWT | **204** "(revoca: hash, prefix y `key_created_at` a NULL; la ingesta con esa key pasa a responder 401)" | 401 · **404** |

- **Ni 400 ni 409 anclados para estos dos endpoints** (a diferencia del CRUD
  de C3): los literales del contrato devuelven solo 401 (superficie panel,
  sin JWT) y 404 (proyecto inexistente). La pregunta del brief
  "400/404 vs literales del Schema" se resuelve así: **404 únicamente**.
- La ruta es `POST …regenerate` para AMBOS casos (generar sin key previa, o
  regenerar con key activa): AppFlow flujo 3 — "el usuario pulsa **Generar
  API key** (sin key activa, o tras revocar) o **Regenerar** → … llama
  `POST /projects/{id}/api-key/regenerate`".
- `created_at` de la respuesta = `key_created_at` recién escrito
  ("Fecha de generación/regeneración", Schema §2). Formato de fechas del
  contrato §3.1: ISO-8601 UTC con sufijo `Z` (formatter C2 reutilizable,
  `LoginController.EXPIRES_AT_FORMATTER`).
- `DELETE` → 204 **sin cuerpo**.

### 2.5 Semántica de estado (Schema §4 regla 11; PRD §3/P0-3, P0-4; §6.3)

> "Regenerar sobrescribe `api_key_hash`/`api_key_prefix`/`key_created_at` sin
> tocar logs (la key anterior **deja de autenticar en el instante**); revocar
> pone los tres a NULL → ingesta 401. `has_key` es derivado. **Archivar no
> toca la key** ni los logs; al desarchivar, la misma key vuelve a aceptarse"
> (Schema §4 regla 11).

- Regenerar invalida la anterior al instante — sobrescritura del hash en la
  MISMA fila (PRD §4: "Regenerar invalida la anterior").
- Revocar: los tres campos → NULL conjuntamente; ingesta futura con la
  revoked key → **401 `api_key_invalid`** (Schema §3.4 tabla).
- Invariantes (Schema §2): los tres campos de la key van SIEMPRE juntos
  (tres a NULL = sin key activa; tres informados = key activa); `has_key`
  derivado (`api_key_hash IS NOT NULL`) — el DTO de C3 ya lo materializa, no
  se toca.
- Sin endpoint de "generar" y otro de "regenerar": UNO solo (el POST
  `regenerate`); AppFlow flujo 3 paso 7: sin key activa el proyecto "no
  puede recibir ingesta"; ARCHIVED además → 403 `project_archived` en
  ingesta (regla de C5, dato ya existente).
- Seed (Schema §6.3): "Sin otras semillas" — sin keys seed; proyecto
  recién creado nace `has_key: false` / `api_key_prefix: null` (semántica
  ya implementada en C3).

### 2.6 Referencia UI (04_UIUX.md V8 + maqueta 05 — NO implementar)

- Modal de revelación única (flujo 3 paso 5): key completa en mono + aviso
  "No se volverá a mostrar; cópiala ahora. La key anterior ya está invalidada
  para todas las apps de este proyecto" + botón **Copiar** + "He copiado la
  key; cerrar"; al cerrar solo queda `prefix` y `has_key: true`.
- Confirmación previa a regenerar (flujo 3 paso 2): "La key actual queda
  invalidada al instante para todas las apps de este proyecto".
- V8 (04_UIUX): "key nunca fuera de su ventana única de revelación
  (AppFlow flujo 3)".
- La copia servible de C3 (`frontend/public/mockups/05-proyecto-detail.html`,
  línea 37) declara: "SIN key (C4: sin regenerate/revocar)" — sin wiring de
  estas acciones hoy.

### 2.7 Done cuando (07_SLICING_PLAN C4)

> "regenerar → revelar una vez → revocar → **403 al ingestar** (smoke con
> cliente curl; ingesta real llega en C5)".

⚠ **Incoherencia interna detectada** (desarrollada en §5 R-1): el Done del
slicing dice 403, pero el único literal anclado para ingesta con key
ausente/inválida/**revocada** es **401 `api_key_invalid`** (Schema §3.4; y
AppFlow flujo 3 paso 6 dice "401 (TRD §2.6)"); el 403 `project_archived` es
el rechazo de un proyecto ARCHIVADO con key válida. Además, en C4 la cadena 1
de `SecurityConfig` responde **401 `invalid_token`** a TODO el `/api/v1/ingest/**`
(denyAll + entry point), con o sin key, porque el mecanismo real llega en C5.
El gate debe fijar la interpretación del Done (§6.1).

## 3. Estado del código base hoy

### 3.1 Lo que YA existe (reutilizable)

| Pieza | Fichero (absoluto) | Qué aporta a C4 |
|---|---|---|
| Tabla + índice key | `backend/src/main/resources/db/migration/V1__init.sql` (líneas 18–20, 54–55) | `api_key_hash BYTEA` (comentario: "SHA-256; NUNCA texto plano. NULL = revocada"), `api_key_prefix TEXT`, `key_created_at TIMESTAMPTZ`, índice **parcial único** `projects_api_key_hash_idx ON projects (api_key_hash) WHERE api_key_hash IS NOT NULL` — TODO ya aplicado en C1. **C4 no migra nada.** |
| `ProjectsController` | `backend/src/main/java/com/projectlog/projects/controller/ProjectsController.java` | Patrón controller fino sin `@Valid` (starter-validation NO está en el pom), validación manual, helper `error()` (cuerpo §3.1 `{code, message, field?}`), constantes `CODE_NOT_FOUND`/`MSG_NOT_FOUND` (package-private), `@ExceptionHandler` de `DuplicateKeyException` en 409. Mapeado en `/api/v1/panel/projects`. |
| `ProjectsRepository` | `backend/src/main/java/com/projectlog/projects/repository/ProjectsRepository.java` | JdbcTemplate + SQL inline; `COLS` con `has_key` **derivado** en SQL (`(api_key_hash IS NOT NULL) AS has_key`); `findById`, `insert`, `update` (el UPDATE jamás toca columnas de la key — R-4 de C3 mantiene esa frontera); `@Transactional` documentada; su Javadoc declara: "**C4 usa findById**; piezas compartidas downstream". |
| `ProjectDto` | `backend/src/main/java/com/projectlog/projects/dto/ProjectDto.java` | Record snake_case con `api_key_prefix` y `has_key` — tras revocar, GET ficha/lista devolverán automáticamente `api_key_prefix: null` + `has_key: false` sin tocar C3 (derivado). |
| Cadenas de seguridad | `backend/src/main/java/com/projectlog/config/SecurityConfig.java` | **Cero líneas a tocar**: los 2 endpoints caen bajo `/api/v1/panel/**` (cadena 2, `anyRequest().authenticated()`) → nacen 401 sin JWT. Cadena 1 (`/api/v1/ingest/**`) sigue denyAll → 401 `invalid_token` a todo intento de ingestar (firma del límite C4/C5). |
| Patrón fechas | `backend/src/main/java/com/projectlog/auth/LoginController.java` | `EXPIRES_AT_FORMATTER` (`yyyy-MM-dd'T'HH:mm:ss'Z'` UTC) — ya reutilizado por `ProjectsRepository` (FILA); C4 lo reutiliza para `created_at`. |
| Paquete `keys` | `backend/src/main/java/com/projectlog/keys/package-info.java` | Marcador C1: "Feature de API keys (C4): regeneración/revocación única, formato plk_*, hash SHA-256" — ancla del placement de la feature. |
| Tests | `backend/src/test/java/com/projectlog/auth/LogHygieneTest.java`, `PanelAuthMvcTest`, etc. | Patrón `@WebMvcTest` + `@MockitoBean` del repositorio (mockeable), `ListAppender` logback para higiene (S16: el token no sale en logs — replicable para la key), `application-test.yml` INTOCABLE (validador de C1 corre en cada contexto). |
| Entorno/smoke | `.atl/sdd/C3/archive-report.md`; `scripts/qa_c3_smoke.sh` | R-nuevo-5 (`up -d --build` exige `--force-recreate`); R-nuevo-6 (`mvn test` dentro de la imagen builder `maven:3.9-eclipse-temurin-21`, backend + `~/.m2` montados, sin BD); psql con `FLOOR(EXTRACT(EPOCH…))::bigint` y booleanos conscientes de contexto; "smoke of the smoke" pre-canónica; nombres con sufijo por ejecución para idempotencia. |

### 3.2 Lo que falta (todo lo de C4)

1. Generación segura de la key (SecureRandom 32 B → Base64url unpadded 43
   chars) — no existe aún en el repo.
2. Hashing SHA-256 + comparación en tiempo constante — no existe (Argon2id
   de `auth/PasswordConfig` es para contraseñas humanas; NO sirve aquí).
3. Endpoints `POST …/api-key/regenerate` y `DELETE …/api-key` — no existen.
4. Statements SQL sobre proyectos (`UPDATE … SET api_key_…` con RETURNING,
   y el futuro lookup por hash para C5) — `ProjectsRepository` solo tiene
   CRUD puro; `conjuntoSet` jamás menciona columnas de key (por diseño).
5. DTO de respuesta `{key, prefix, created_at}` — no existe (auth usa records
   directos en el paquete: `LoginResponse`, `MeResponse`).
6. Higiene de la key en logs (nunca loggearla) — patrón de C2 replicable.

## 4. Comparación de enfoques y recomendación técnica

### 4.1 Generación de la key

| Opción | Análisis |
|---|---|
| **A. `SecureRandom` 32 B + `Base64.getUrlEncoder().withoutPadding()`** (RECOMENDADA) | Literales exactos del contrato: 32 bytes = 256 bits (TRD ADR-4, Schema §4.2); Base64url → 43 chars verificables regex `^plk_[A-Za-z0-9_-]{43}$` (longitud total 47). Cero dependencias (`java.security` + `java.util.Base64`). |
| B. UUID + prefijo | 128 bits y guiones — NO cumple el literal (43/Base64url/256 bits). Descartada. |
| C. Alfabeto muestreado char a char (hex o custom) | Fuente de bugs de entropía/bias; obliga a reinventar lo que `Base64` da gratis + el literal pide Base64url. Descartada. |

Generación puntual que se hace UNA vez por llamada ("one-shot"): `new
SecureRandom()` por instancia o singleton bean — irrelevante para la
seguridad aquí (recomendación: instancia estática del servicio; las llamadas
concurrentes a `SecureRandom` nativo son thread-safe).

### 4.2 Hashing y comparación

| Opción | Análisis |
|---|---|
| **A. SHA-256 (`MessageDigest`) + comparación `MessageDigest.isEqual`** (RECOMENDADA) | Literal TRD ADR-4 y Schema §4.2. Hash rápido ⇒ lookup por índice O(1) viable (con hash lento+sal, imposible por definición). 256 bits de entropía ⇒ no es atacable por diccionario ⇒ KDF lento añade coste sin seguridad (latencia ×1000 en el hot-path de la ingesta). `MessageDigest.isEqual` compara en tiempo constante. |
| B. BCrypt/Argon2id para la key | **Descartada** por el propio Schema §4.2 y TRD ADR-4 ("Argon2id para la key" figura en la columna de alternativas NO elegidas del ADR-4): una key de 256 bits no es contraseña humana; además el salt destruye la localización por índice O(1). Se mantendría solo para contraseñas (ya está en C2, `PasswordConfig`). |
| C. Key cifrada (guardar texto + AES) | **Descartada**: contradice PRD §4 ("La API key se guarda como hash: es imposible mostrarla después") y TRD ("NUNCA texto plano"). Requeriría gestionar KEK, y "cifrado es reversible" — el PRD quiere irreversibilidad práctica + revelación única por regeneración. |
| D. Digest como hex en `TEXT` | **Descartada**: la columna es `BYTEA` literal en V1/Schema; guardarlo hex desdobla la convención para C5. |
| E. SHA-512 u otro | Descartada: SHA-256 es el literal (`MessageDigest.getInstance("SHA-256")`), y 32 B de digest cuadra con BYTEA del ejemplo. |

**Decisión a anclar en spec**: el hash es del **String completo de la key**
(`plk_` + 43 chars, bytes UTF-8) — así C5 puede computar `sha256(header_X_API_Key)`
y comparar directo. (Si se hash-eara solo el tramo de 43, C5 debería
recortar antes: innecesario y frágil.)

### 4.3 Store y SQL (repo)

| Enfoque | Análisis |
|---|---|
| **A. Estamentos nuevos en `ProjectsRepository`** (RECOMENDADA) | El Javadoc de C3 ya la declara "pieza compartida downstream" y dueño ÚNICO del SQL de `projects`; evita dos mapeadores de la misma tabla; `COLS` reutilizable para devolver `key_created_at`. Dos métodos: `regenerateKey(long id, byte[] hash, String prefix)` y `revokeKey(long id)`, ambos `UPDATE projects SET … WHERE id = ? RETURNING key_created_at` (mono-sentencia, autocommit; la invalidación del instante es la atomicidad natural del UPDATE). |
| B. `KeysRepository` propio en `com.projectlog.keys` | Cohesión de feature máxima, pero segunda clase mapeando la MISMA tabla y duplicación del RowMapper/formatter; C3 dejó el patrón contrario (repo centraliza). Solo si el gate quiere separación estricta paquete↔tabla. |

Detalle SQL (recomendación; el spec lo fija con literales):

```sql
-- regenerar (una sola ida; devuelve key_created_at para la respuesta)
UPDATE projects
   SET api_key_hash = ?, api_key_prefix = ?, key_created_at = now(),
       updated_at = now()
 WHERE id = ?
RETURNING key_created_at;

-- revocar (los tres juntos, invariante del Schema §2)
UPDATE projects
   SET api_key_hash = NULL, api_key_prefix = NULL, key_created_at = NULL,
       updated_at = now()
 WHERE id = ?
RETURNING id;   -- filas = 0 → 404 (sin pre-SELECT: si el id no existe,
                -- el UPDATE es no-op y no escribe — 404 antecede a la
                -- escrita por construcción, patrón E3.19 de C3 en versión
                -- de una sola ida, y seguro ante carreras)
```

- El **404 sale gratis del empty-RETURNING** (mejor que el pre-SELECT de C3:
  sin carrera y sin segunda ida). No hay 400/409: nada más que validar.
- `has_key` tras revocar: `false` automáticamente (derivado en `COLS` de C3,
  sin tocar nada de C3). Verificación psql: los tres campos NULL juntos.

### 4.4 Endpoints (placement)

| Enfoque | Análisis |
|---|---|
| **A. `ApiKeyController` NUEVO en `com.projectlog.keys`** (RECOMENDADA) | Ancla del paquete `keys` en su `package-info` C1 ("Feature de API keys (C4)"); feature completa en un solo paquete (regla package-by-feature, TRD §2.1); `ProjectsController` no crece. Los mappings van con rutas completas (`@PostMapping("/api/v1/panel/projects/{id}/api-key/regenerate")`, `@DeleteMapping("/api/v1/panel/projects/{id}/api-key")`) — sin colisión con los mappings de C3: Spring resuelve por path exacto. Seguridades: nace bajo la cadena 2 SIN tocar `SecurityConfig`. |
| B. Dos métodos dentro de `ProjectsController` | Reutiliza `error()` y las constantes 404 package-private SIN editar C3; pero engorda el controller fino de C3 y el paquete `keys` muere vacío contra su declaración C1. |
| C. `ApiKeyController` en `projects` | Hybrid incoherente: ni el paquete de la feature ni el controller del listado. Descartada. |

Coste de A: el helper `error()` y las constantes de 404 de C3 son
package-private (visibles solo dentro de `projects.controller`) — el nuevo
controller NO puede reutilizarlos. Opciones (gate §6.3): duplicar las 2
constantes + helper mini en `keys` (con Javadoc de ancla — mismo patrón que
ya usó C3 al replicar cuerpos de C2, cero diffs sobre ficheros de cortes
anteriores), o hacer públicas las constantes de `ProjectsController`
(edita 1 fichero de C3: solo visibilidad, sin comportamiento).

Los endpoints **no tienen cuerpo de request** (Schema §3.2: la entrada es
"—"): no hay `@RequestBody`, el caso `HttpMessageNotReadable` no aplica, y
el único camino de error es 404 (o 401 de la cadena, ya cubierto).

### 4.5 DTO y paquete de respuesta

`ApiKeyResponse` record en `com.projectlog.keys` directo (patrón auth C2,
sin subpaquete dto), snake_case literal:

```java
public record ApiKeyResponse(String key, String prefix, String created_at) { }
```

- `key` SOLO existe aquí, en memoria, una vez: jamás almacenada ni loggeada.
- `prefix` = `key.substring(0, 10)` (literal Schema §2 §2.3).
- `created_at` = el `RETURNING` de key_created_at, formateado con
  `EXPIRES_AT_FORMATTER` (convención §3.1 `{yyyy-MM-dd'T'HH:mm:ss'Z'}`).

### 4.6 Caja a caja — piezas NUEVAS propuestas (para el spec/tasks)

| Pieza | Paquete |
|---|---|
| `ApiKeyService` (o `ApiKeyCrypto`): `newKey()` (genera), `sha256(String)` → `byte[]`, `prefixDe(String)` → 10 chars, `tiempoConstanteIgual(byte[], byte[])` (wrapper de `MessageDigest.isEqual` — primitiva C5) | `com.projectlog.keys` |
| `ApiKeyController`: 2 mappings, 404 (empty-RETURNING) → cuerpo estándar, 201 `{key, prefix, created_at}`, 204 | `com.projectlog.keys` |
| `ApiKeyResponse(key, prefix, created_at)` | `com.projectlog.keys` |
| `ProjectsRepository.regenerateKey / revokeKey / (+ opcional findByApiKeyHash para C5 — §6.4)` | `com.projectlog.projects.repository` |
| Tests MVC (404/201/204, literales), test de formato/entropía de key, test de higiene de logs (la key nunca en logs), test unitario de la primitiva de comparación | `backend/src/test/java/...` |
| Smoke curl en pila: login → POST regenerate (201, formato, prefix = 10 chars) → psql (hash BYTEA distinto tras regenerar) → DELETE (204) → psql (3 NULLs) → GET ficha (`has_key: false`) | `scripts/` |

## 5. Riesgos y puntos de decisión para el propuesta

- **R-1 · Incoherencia del Done "403 al ingestar" (ALTA, gate)** — el literal
  del Done del slicing contradice los anclados: Schema §3.4 (401
  `api_key_invalid` para key revocada; 403 `project_archived` es otra cosa) y
  AppFlow flujo 3 paso 6 ("401"). Encima, en C4 la cadena 1 responde **401
  `invalid_token`** a CUALQUIER intento de ingestar (denyAll), así que el
  smoke curl no puede distinguir key válida/revocada. Propuesta
  (interpretación honesta del Done): el smoke de C4 verifica las tres
  transiciones de ESTADO — (1) regenerate → 201 con la key revelada una vez →
  psql contiene hash de esa key y `api_key_prefix` (=10 chars); (2) revocar →
  204 → psql tres NULLs + GET ficha `has_key: false, api_key_prefix: null`;
  (3) "regeneración invalida al instante" → psql hash ANTERIOR ≠ nuevo + (en
  test unitario) que la primitiva `tiempoConstanteIgual(hash_guardado_antiguo,
  sha256(key_nueva))` da false — la prueba end-to-end de autenticación real
  queda como Done de C5 (que trae el mismo smoke con `X-API-Key` y sus
  401/403 con literales). ALTERNATIVA si el gate quiere literalidad: añadir
  en C4 un esbozo del auth de ingesta — **contra la frontera del corte** (no
  se mezclan stories de cortes distintos, regla del 07). Decisión de gate.
- **R-2 · Revocar cuando ya no hay key (sin ancla)**: DELETE sobre un
  proyecto sin key activa. No hay 400/409 anclado; la fila Schema §3.2 solo
  dice 404 (proyecto inexistente). Propuesta: **204 idempotente** (semántica
  DELETE de HTTP; el estado final "sin key" ya es el objetivo — error sería
  un "failure espurio" anti-patrón del AppFlow). Gate.
- **R-3 · Regenerar sobre proyecto ARCHIVADO (sin ancla de 403)**: la fila
  Schema §3.2 solo lista 401·404; regla 11 dice que archivar no toca la key y
  al desarchivar "la misma key vuelve a aceptarse". Propuesta: **permitir
  regenerate/revocar en ARCHIVADO** (es estado de ingesta, no de gestión de
  key; el panel de la maqueta muestra las acciones según estado pero el
  bloque de key no está condicionado por `status` en el flujo 3). Gate.
- **R-4 · Colisión de hash en el índice parcial único (probabilidad
  astronómica)**: si el digest de la nueva key coincidiera con otra fila
  activa, `DuplicateKeyException` subiría a 500. Propuesta: NO capturar (no
  hay literal de error para ello; documentar el riesgo como teórico: colisión
  de SHA-256 en 2^(1/2·256) generaciones). Coherente con la decisión de C3
  de no mapear `DataIntegrityViolationException` genérico.
- **R-5 · Higiene de la key en logs (S16 replicado)**: la key 201-viva no
  debe aparecer en logback (ni debug). El patrón `LogHygieneTest` de C2 se
  replica: ListAppender sobre las clases de keys + aserción de que NINGÚN
  evento contiene `plk_` o la key del test. Recordatorio: el smoke debe
  validar formato y descartar la key (no la reutiliza — no puede, no hay
  ingesta aún), evitando fugarla a consola salvo en la única "revelación"
  del paso 1 del Done.
- **R-6 · Intocables**: `SecurityConfig` (3 cadenas — el endpoint nuevo
  nace 401-sin-JWT sin tocarlas), `V1__init.sql` (índice parcial ÚNICO
  parcial YA existe), entry point, `CorsConfig` (nota: `X-API-Key` ya debe
  estar en la config CORS de C1/C3? — se verificará en spec: los clientes de
  ingesta no son navegador, y CORS no aplica a `/api/v1/ingest/**` por
  diseño (chain sin `cors()`); para el panel el header `X-API-Key` no se
  usa), `docs/**`, `frontend/public/mockups` (a menos que el gate apruebe
  el wiring, §6.4). Prohibido añadir starter-validation o spring-security
  extras.
- **R-7 · Entorno (heredado de C3)**: toda recomposición pila con
  `--force-recreate` (R-nuevo-5); `mvn test` en imagen builder (R-nuevo-6,
  patrón `podman run --rm -v backend:/ws:Z -v ~/.m2:/root/.m2:Z -w /ws mvn
  test`, sin BD); psql epoch con `FLOOR(EXTRACT(EPOCH …))::bigint` y
  booleanos `f/t` vs `false/true` según contexto; "smoke of the smoke" ANTES
  de la corrida canónica; nombres con sufijo de ejecución para idempotencia.
- **R-8 · Respuestas y literales EXACTOS (patrón heredado del 07 y C2/C3)**:
  201 `{"key": "plk_<43>", "prefix": "plk_<10>", "created_at": "…Z"}`,
  404 cuerpo estándar `{"code":"not_found", …}` con el mismo mensaje de C3
  ("No existe (quizás fue eliminado)" — AppFlow §5), 204 SIN cuerpo. Sin
  nuevos códigos sin ancla (solo `not_found` reutilizado).

## 6. Preguntas abiertas para sdd-propose (gate del usuario)

1. **Interpretación del Done "403 al ingestar" (R-1)**: ¿se aprueba la
   lectura por estado (smoke transiciones psql + has_key + test unitario de
   la primitiva; la prueba "la key ya no autentica" con `X-API-Key` real
   queda en el Done de C5, donde SÍ existen 401/403 anclados)? ¿O el gate
   quiere una demo del rechazo YA (exigiría adelantar C5)?
2. **Idempotencia de revocar (R-2)**: ¿`DELETE` sobre proyecto sin key
   activa → 204 idempotente (propuesta) u otro código?
3. **Placement y reutilización de literales (§4.4)**: ¿`ApiKeyController`
   NUEVO en `com.projectlog.keys` (propuesta en sintonía con el
   `package-info` de C1), duplicando las constantes de error 404 con ancla
   Javadoc para no editar ficheros de C3 — o se hacen públicas las de
   `ProjectsController`?
4. **¿Wiring de la copia servible de la maqueta 05?** (los botones
   Regenerar/Revocar sobre `frontend/public/mockups/05-proyecto-detail.html`,
   con modal de revelación): el Done de C4 NO la exige (a diferencia de C3)
   y la tarea del encargo dice "referencia UI, no implementar paneles aún" —
   propuesta por defecto: NO tocar front en C4 (queda para C8); el smoke
   "revelar una vez" es puro curl.
5. **Preconstrucción para C5 (§4.6)**: ¿se dejan en C4 las primitivas
   crypto/comparación (`sha256`, `tiempoConstanteIgual`) como clases
   unitariamente probadas en `com.projectlog.keys`, dejando el lookup
   `ProjectsRepository.findByApiKeyHash(byte[] hash)` para C5 (propuesta),
   ¿o se incluye ya el método del repo con su índice parcial?