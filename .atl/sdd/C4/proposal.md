---
change: "C4"
artifact: proposal
last_updated: 2026-10-24
status: approved
gate: "APROBADO por el usuario 2026-10-24 (4 de los 5 puntos como estaban; Decisión 3 REVISADA: en ARCHIVADO solo DELETE permitido, regenerate rechazado con 409 project_archived — a cerrar en spec)"
---

# Proposal — C4 · API keys de ingesta

> Fase sdd-propose. Se presenta al usuario como GATE (aprobación de los 5
> puntos de decisión de la §4) antes de pasar a spec/design/tasks/apply.
> Sin este aprobado no se escribe código de C4. Insumos: `explore.md` de C4
> (fuentes congeladas: TRD §2.5/§2.6 + ADR-4, Schema §3.2/§3.3/§3.4/§2/§4/§6.3,
> AppFlow flujo 3 + §4/§5, 07 §C4, cierre de C3).

## 1. Problema y motivación

El panel (C3) ya expone la ficha del proyecto con `has_key` y
`api_key_prefix` **derivados en SQL**, pero no existe ningún mecanismo para
crear una key: todo proyecto nace y muere con `has_key: false`, y la ingesta
de C5 (que requiere `X-API-Key`) no tendría contra qué autenticar. El 07
§C4 define el corte: **generación/regeneración/revocación de la API key de
ingesta por proyecto**, con las garantías de seguridad ancladas en TRD
§2.6/ADR-4 y Schema §4.2:

- key `plk_<43 chars Base64url>` de **32 bytes SecureRandom** (256 bits;
  total 47 chars);
- guardada **SOLO por hash SHA-256** (BYTEA; "NUNCA texto plano");
- `api_key_prefix` = primeros 10 chars, visible sin revelar la key;
- `key_created_at`;
- comparación en **tiempo constante** (`MessageDigest.isEqual`) — primitiva
  que nace aquí y C5 reutiliza en el hot-path;
- la key completa se revela **UNA única vez** (respuesta 201 del POST).

Por qué es el siguiente natural tras C3: la ficha de proyecto ya materializa
el estado de la key (`has_key`, `api_key_prefix`) que C4 va a escribir; el
paquete ancla `com.projectlog.keys` (package-info de C1) queda sin habitantes
y esperando esta feature; y C5 no puede construirse sin este almacén de
hashes. C4 es la pieza de gestión (panel) de la feature; la de consumo
(ingesta) es otra story en otro corte.

## 2. Alcance propuesto

### 2.1 Endpoints (literales CITADOS del Schema §3.2 / AppFlow flujo 3)

1. **`POST /api/v1/panel/projects/{id}/api-key/regenerate`** — un único
   endpoint para generar Y regenerar (AppFlow flujo 3: "Generar API key
   (sin key previa, o tras revocar) o Regenerar").
   - Auth: JWT (cadena 2 de SecurityConfig, ya en pie — nada que tocar).
   - **201** `{key, prefix, created_at}`:
     - `key`, p. ej. `plk_Ab3x7kQ9mT4vR2pW8yZcLd5NhJfE6sUuG1iXoBe0qAr`
       (43 chars tras `plk_`; regex `^plk_[A-Za-z0-9_-]{43}$`) — revelación
       única;
     - `prefix`, p. ej. `plk_Ab3xQw` (= `key.substring(0, 10)`);
     - `created_at` = `key_created_at` recién escrito, formato
       `yyyy-MM-dd'T'HH:mm:ss'Z'` UTC (formatter de C2,
       `LoginController.EXPIRES_AT_FORMATTER`, ya reutilizado por
       `ProjectsRepository`).
   - 404 (proyecto inexistente) con cuerpo estándar
     `{"code":"not_found", "message":"No existe (quizás fue eliminado)", …}`
     — mismos literales que C3. **409 `project_archived`** si el proyecto
     está ARCHIVADO (Decisión 3 revisada en gate; literal exacto a fijar
     en spec).
   - 401 sin JWT (automático, cadena 2).
   - Sin cuerpo de request (Schema §3.2: entrada "—"), sin 400/409.
2. **`DELETE /api/v1/panel/projects/{id}/api-key`** — revocar.
   - **204 SIN cuerpo**; escribe `api_key_hash`, `api_key_prefix`,
     `key_created_at` a **NULL conjuntamente** (invariante Schema §2).
   - 404 (inexistente) / 401, igual que arriba.
   - Sobre proyecto **sin key activa** → 204 idempotente (decisión de gate,
     §4.2).

Semántica de estado (Schema §4 regla 11, se citerá en spec): la key anterior
**deja de autenticar en el instante** de regenerar (sobrescritura del hash en
la misma fila); archivar NO toca la key (ver §4.3); tras revocar, GET
ficha/lista de C3 devuelven automáticamente `has_key: false` /
`api_key_prefix: null` (derivado, cero cambios en C3).

### 2.2 Qué NO hace C4

- **No hay ingesta**: ni `POST /api/v1/ingest` ni el filtro/auth de
  `X-API-Key` (C5). La cadena 1 de `SecurityConfig` sigue en `denyAll()`
  respondiendo 401 `invalid_token` a todo `/api/v1/ingest/**`.
- **No hay paneles React ni modales** (C8): la maqueta 05 y sus modales son
  referencia visual. La "revelación única" es a nivel API.
- **No se toca el front** (decisión de gate §4.4): la copia servible de la
  maqueta 05 queda con su nota "C4: sin regenerate/revocar".
- **No hay migración:** columnas + índice parcial único ya existen en
  `V1__init.sql` (C1); el fichero queda intocable.
- No archivado (C3), no `DELETE /projects/{id}` CASCADE (C10/P1), no summary
  (C10/P1), no streaming (C7).
- No se implementa el lookup `findByApiKeyHash` (se decide en gate §4.5).

## 3. Enfoque técnico recomendado (de explore §4, ratificado)

- **Paquete**: TODO en `com.projectlog.keys` — ancla ya declarada en el
  package-info de C1 ("Feature de API keys (C4): regeneración/revocación
  única, formato plk_*, hash SHA-256") y Regla package-by-feature (TRD §2.1).
  En `keys` se crean: `ApiKeyController`, `ApiKeyService`, `ApiKeyResponse`
  record (`(String key, String prefix, String created_at)`, snake_case, patrón
  auth de C2 sin subpaquete dto).
- **Controller fino**, sin `@Valid` (starter-validation no está en el pom, y
  aquí no hay cuerpo de request): 2 mappings con rutas completas — sin
  colisión con los de C3 (Spring resuelve por path exacto). Nace bajo la
  cadena 2 → 401 sin JWT sin tocar `SecurityConfig`.
- **Service** como frontera crypto: `newKey()` (`SecureRandom` 32 bytes →
  `Base64.getUrlEncoder().withoutPadding().encodeToString(...)` con prefijo
  `plk_`), `sha256(String)` → `byte[]` (del String COMPLETO de la key, bytes
  UTF-8 — decisión anclada en spec para que C5 compute
  `sha256(header_X_API_Key)` y compare directo), `prefixDe(String)` → 10
  chars, `tiempoConstanteIgual(byte[], byte[])` (wrapper de
  `MessageDigest.isEqual`, primitiva C5). Instancia `SecureRandom` estática
  del servicio (thread-safe, una generación por llamada). NO Argon2id/BCrypt
  (descartado por ADR-4: la key no es contraseña humana; el KDF lento
  destruiría el lookup O(1) por índice del Schema §4.2).
- **SQL**: dos estamentos NUEVOS en `ProjectsRepository` (dueño único del SQL
  de `projects`, según su propio Javadoc de C3 — se añade, no se reescribe;
  el UPDATE existente de C3 JAMÁS toca columnas de key):
  - `regenerateKey(long id, byte[] hash, String prefix)` → `UPDATE projects
    SET api_key_hash = ?, api_key_prefix = ?, key_created_at = now(),
    updated_at = now() WHERE id = ? RETURNING key_created_at;`
    (mono-sentencia; row 0 → 404, seguro ante carreras).
  - `revokeKey(long id)` → `UPDATE projects SET api_key_hash = NULL,
    api_key_prefix = NULL, key_created_at = NULL, updated_at = now()
    WHERE id = ? RETURNING id;` (row 0 → 404; si el id no existe, el UPDATE
    es no-op y no escribe — patrón E3.19 de C3 en versión de una ida).
  - El 404 sale del empty-RETURNING, sin pre-SELECT: mejor que el patrón C3
    (una ida menos, sin carrera).
- **Seguridad**: `SecurityConfig` intocable (3 cadenas); `V1__init.sql`
  intocable; higiene de logs estilo S16 de C2 (test ListAppender: ningún
  evento de las clases de `keys` contiene `plk_` ni la key del test) —
  `ApiKeyResponse.key` vive en memoria una única vez.
- **Colisión de hash en el índice parcial único** (R-4 astronómica): NO se
  captura `DuplicateKeyException` (no hay literal de error; coherente con C3
  que no mapea DIVE genérico). Documentado como riesgo teórico.

Alternativas descartadas en explore §4 (UUID+prefijo, alfabeto custom, KDF
lento, cifrado de la key, digest hex, `KeysRepository` propio, controller
dentro de `projects`) quedan recogidas como alternativas rechazadas de las
decisiones del gate donde aplica.

## 4. Decisiones/riesgos para el GATE — 5 puntos

> Los 5 numerados deben aprobarse (aprobación 5/5, como C1–C3). Se marca
> cada uno si es una decisión de fondo del usuario o si va **de paquete**
> (recomendación sólida; solo se aprueba en bloque, sin debate esperado).

### Decisión 1 (DE GATE, la más importante) — Resolución del hazard del Done del 07

**Enunciado.** El Done del 07 §C4 dice "regenerar → revelar una vez →
revocar → **403 al ingestar** (smoke curl)". Pero: (a) el único literal
anclado para key revocada en ingesta es **401 `api_key_invalid`** (Schema
§3.4; el 403 `project_archived` es otra condición — proyecto ARCHIVADO con
key válida), y AppFlow flujo 3 paso 6 dice igualmente "401"; (b) en C4 la
cadena 1 responde **401 `invalid_token` a CUALQUIER intento de ingestar**
(denyAll + entry point), con o sin key, así que un smoke curl C4 no puede
distinguir key válida de revocada de ningún tipo; (c) la regla del flujo
prohíbe mezclar stories de cortes distintos, y adelantar media C5 para
"hacer el 403/401 real" rompería la frontera.

**Recomendación (de paquete fuerte, pero por ser un Done del plan se somete
al gate).** Aprobar la **reformulación honesta del Done de C4**:

> "smoke por transiciones de estado + test unitario de la primitiva de
> comparación": (1) `POST regenerate` → 201 con key que casa la regex
> `^plk_[A-Za-z0-9_-]{43}$` y prefix = 10 chars → psql: `api_key_hash` (BYTEA)
> = SHA-256 de esa key y `api_key_prefix` correcto; (2) regenerar de nuevo →
> 201 con key distinta → psql: hash ANTERIOR ≠ nuevo (invalidación al
> instante, verificable sin ingesta); (3) `DELETE` → 204 → psql: tres campos
> a NULL juntos + `GET /projects/{id}` de C3 → `has_key: false`,
> `api_key_prefix: null`. Y test unitario: `tiempoConstanteIgual(hash del
> estado previo, sha256(key nueva)) == false` y `== true` para la pareja
> correcta (primitiva que C5 usará en el hot-path). La verificación **real
> de "la key ya no autentica al ingestar" (401 `api_key_invalid` con
> X-API-Key, y 403 `project_archived` con key válida) queda como Done del
> smoke de C5**, donde existen los 401/403 anclados y el mecanismo real.

Queda así corregida la incoherencia interna del Done (403→401
`api_key_invalid`) por coherencia con Schema §3.4 y AppFlow flujo 3: los
literales del Schema/APFLOW son contrato; el Done del slicing se interpreta,
nunca se reancla contra ellos.

**Alternativa rechazada**: demo del rechazo YA en C4 (esbozar el auth de
ingesta) — contra la frontera del corte 07 y duplicaría el trabajo de C5.

### Decisión 2 (de paquete, con anclaje explícito) — Idempotencia de `DELETE` sin key activa

**Enunciado.** `DELETE /api-key` sobre un proyecto EXISTENTE y sin key
activa no tiene ancla (la fila Schema §3.2 solo lista 401·404; no hay 400/409
para este par de endpoints).

**Recomendación (de paquete).** **204 idempotente**: el estado final "sin
key activa" ya es el objetivo; devolver 404/409 sería un "failure espurio"
(del estilo anti-patrón del AppFlow §4), y es la semántica natural de
DELETE. El estamento `revokeKey` da filas=1 o 0 por inexistencia del
proyecto; si el proyecto existe, la fila se toca (updated_at avanza) y
devuelve 204 esté o no la key ya a NULL. Nota para spec: si se prefiriera
que revokeKey sobre sin-key no toque `updated_at`, la condición
`WHERE id = ? AND api_key_hash IS NOT NULL` lo consigue sin cambiar el
código de resultado (204 igualmente). El spec lo fija con literales.

**Alternativa rechazada**: 404/409 si no hay key — introduce un código sin
ancla y convierte un no-op en error.

### Decisión 3 (REVISADA EN GATE 2026-10-24) — Regenerar/revocar sobre proyecto ARCHIVADO

**Enunciado.** La fila Schema §3.2 no ancla 403 para estos endpoints; la
regla 11 dice que archivar NO toca la key y al desarchivar "la misma key
vuelve a aceptarse". ¿Se permiten regenerate/revocar en ARCHIVADO?

**Decisión del usuario (corrección al gate).** **ASIMÉTRICO**:
- **DELETE (revocar) sobre ARCHIVADO: PERMITIDO** (el estado archivado
  condiciona la ingesta, no la gestión de key; sin rama extra en revokeKey).
- **POST regenerate sobre ARCHIVADO: RECHAZADO.** No se genera/regenera key
  sobre un proyecto archivado. Esto añade UNA rama de error solo en el POST:
  **409** con cuerpo estándar C3 y `code: "project_archived"` (literal ya
  contrato del Schema §3.4) — el coordinator sugiere 409 y lo remite al spec
  para cerrarlo junto con los 7 cierres del §7 (el spec fija el literal
  exacto del cuerpo, el mensaje y el Javadoc; el design documenta la rama
  temprana en el service/controller y el orden respecto al 404).

**Alternativa original rechazada en gate**: permitir ambos (sin rama extra)
— el usuario prefirió blindar la superficie de generación.

**Alternativa rechazada (de la revisión)**: 403 `project_archived` para el
par de endpoints — el 403 con ese literal es la condición de INGESTA en C5
(proyecto archivado CON key válida); usarlo en el panel mezclaría semánticas.

### Decisión 4 (DE GATE, porque el encargo dice "referencia UI") — ¿Wiring de la copia servible de la maqueta 05?

**Enunciado.** ¿Conectar los botones Regenerar/Revocar (+ modal de
revelación) en `frontend/public/mockups/05-proyecto-detail.html`, como se
hizo con el panel de archivado en C3, o dejar el front para C8?

**Recomendación (DE GATE, es la única decisión con afectación a ficheros
fuera del backend).** **NO tocar front en C4**: (a) el Done de C4 NO lo
exige (a diferencia de C3, cuyo Done sí pedía la copia servible); (b) el
encargo define la maqueta como "referencia UI, no implementar paneles aún"
(hasta C8); (c) el smoke "revelar una vez" es puro curl. La copia servible
de C3 queda con su nota "C4: sin regenerate/revocar", que sigue siendo
verdadera al cerrar C4. Si al usuario le gusta ver la UI, puede optar aquí
por wiring ligero (fetch a los 2 endpoints + modal de revelación), pero NO
es la recomendación: retrasa el corte y adelanta trabajo de C8 sin Done que
lo pida.

**Alternativa**: wiring ligero en la copia servible — solo si el gate lo
activa explícitamente (ampliaría las estimaciones de la §6 con ~1 fichero y
su riesgo de divergencia con la maqueta canónica).

### Decisión 5 (DE PAquete) — Primitivas crypto en C4 y `findByApiKeyHash` diferido

**Enunciado.** ¿Se construyen en C4 las primitivas `sha256` /
`tiempoConstanteIgual` (unitariamente probadas, aunque solo las use C5) y se
deja el lookup `ProjectsRepository.findByApiKeyHash(byte[])` para C5, o se
construye ya también el método del repo?

**Recomendación (de paquete).** **Primitivas en C4 SÍ (lo exige el Done de
la feature: "comparación en tiempo constante" es del alcance), lookup NO**:
(a) es literal del 07 §C4; (b) el test unitario de la Decisión 1 da prueba
sin BD; (c) el índice parcial único ya existe, así que el método de C5 será
un `SELECT id FROM projects WHERE api_key_hash = ?` trivial — adelantarlo
no ahorra nada y su spec (hot-path C5, cadena 1) NO es lo que la story C4
cubre. C5 firma así: llega al repo ya probado y solo añade el lookup.

**Alternativa rechazada**: incluir `findByApiKeyHash` ya — mezclaría la
story de gestión con la de ingesta y el 07 lo separa.

### Otros riesgos (no de gate, gestionados en spec/design/tasks)

- **R-2b**: higiene de la key en logs (R-5 de explore) — test replicado del
  patrón `LogHygieneTest` de C2; la key jamás en logback.
- **R-8**: literales EXACTOS de respuesta (201/204/404, mensaje 404 C3,
  formato ISO-Z) — patrón heredado C2/C3; se citan, no se redefinen.
- **R-7**: entorno heredado de C3 — recomposición con `--force-recreate`,
  `mvn test` en imagen builder, psql con epoch/booleanos contextuales,
  "smoke of the smoke" antes de la canónica, nombres con sufijo por
  ejecución. Sin cambios respecto a C3.
- **Intocables (R-6)**: `SecurityConfig`, `V1__init.sql`, entry point,
  `CorsConfig`, `application-test.yml`, `docs/**`,
  `frontend/public/mockups` (salvo Decisión 4 en contra). Prohibido añadir
  starter-validation u extras de spring-security. Nota CORS para spec: el
  header `X-API-Key` no se usa en el panel y `/api/v1/ingest/**` no pasa por
  `cors()` (sin navegador), verificar en el spec que no hace falta tocar
  `CorsConfig`.

## 5. Criterios de Done de C4 (propuesta, coherente con la Decisión 1)

1. `POST /api/v1/panel/projects/{id}/api-key/regenerate` devuelve **201**
   `{key, prefix, created_at}`: key de 47 chars casando
   `^plk_[A-Za-z0-9_-]{43}$`, prefix = `key.substring(0,10)`, `created_at`
   ISO-Z UTC. Solo JWT; 404 ante proyecto inexistente (cuerpo
   estándar C3); 409 `project_archived` ante archivado (Decisión 3 revisada).
2. `DELETE /api/v1/panel/projects/{id}/api-key` devuelve **204 sin cuerpo**
   (idempotente según Decisión 2) y deja en psql los TRES campos a NULL
   juntos; `GET /projects/{id}` refleja `has_key: false` /
   `api_key_prefix: null` sin cambios en C3.
3. Regeneración = sobrescritura del hash en la misma fila: psql hash
   anterior ≠ nuevo tras el segundo POST (invalidación al instante,
   verificable sin ingesta — Decisión 1).
4. Persistencia SOLO por hash: en psql, `api_key_hash` (BYTEA) ==
   SHA-256 (UTF-8) de la key revelada 201, y NINGÚN campo de la tabla
   contiene la key en claro.
5. Test unitario de la primitiva: `tiempoConstanteIgual(a, b)` true/false
   correctos (pareja correcta vs hash de key distinta).
6. Higiene: test ListAppender — ningún evento de log de `com.projectlog.keys`
   contenga `plk_` ni la key del test.
7. Tests MVC: 201 (literales), 204, 404, 401 sin JWT (cadena 2).
8. Smoke de pila (curl + psql) según el guion de la Decisión 1, con los
   patrones de entorno heredados de C3 (R-7).
9. **NO pasa**: verificación real de 401 `api_key_invalid` / 403
   `project_archived` al ingestar (Done de C5), front (C8), ingesta,
   migraciones, `SecurityConfig`.

## 6. Estimación de alcance

**Ficheros a crear (backend, en `com.projectlog.keys`):**

| Fichero | Contenido |
|---|---|
| `keys/ApiKeyController.java` | 2 mappings, 201/204/404, handler de 404 (patrón C3/C2) |
| `keys/ApiKeyService.java` | newKey, sha256, prefixDe, tiempoConstanteIgual; SecureRandom estático |
| `keys/ApiKeyResponse.java` | record `(key, prefix, created_at)` |

**Ficheros a MODIFICAR (aditivos, sin reescritura):**

| Fichero | Cambio |
|---|---|
| `projects/repository/ProjectsRepository.java` | +2 estamentos (`regenerateKey`, `revokeKey`, UPDATE…RETURNING) + Javadoc |
| Tests (nuevos, `backend/src/test/java/com/projectlog/keys/`) | test de formato/entropía, test unitario de la primitiva, test ListAppender de higiene, tests MVC (o ampliar el patrón `PanelAuthMvcTest` con los mappings de `keys`) |
| `scripts/` | +1 smoke curl/psql de C4 (patrón `qa_c3_smoke.sh`) |

**Migraciones: NO.** `V1__init.sql` intocable (columnas e índice parcial
único ya en C1). **Front: NO** (salvo Decisión 4 en contra). **Docs: NO.**

**Riesgos de regreción sobre C1–C3:**

- `ProjectsRepository` es el único fichero de backend de cortes anteriores
  que se EDITA — cambio puramente aditivo (2 métodos nuevos); el UPDATE
  existente de C3 jamás toca columnas de key (frontera R-4 de C3
  preservada). Riesgo bajo.
- `GET /projects/{id}` y listado de C3 reflejarán sin cambios los nuevos
  estados (derivados SQL ya en `COLS`) — verificación en el smoke, sin diff.
- Sin tocar `SecurityConfig`/`V1__init.sql`/`CorsConfig`/`application-test.yml`
  → los validadores de C1 corren igual en cada contexto de test.
- Higiene heredada de C3 en entorno de smoke (R-7) — sin novedad.

## 7. Preguntas para sdd-spec (huecos que el spec de C4 deve cerrar)

1. Literales EXACTOS de los 2 estamentos SQL (con o sin la condición
   `api_key_hash IS NOT NULL` en el WHERE de `revokeKey` — afecta
   `updated_at`, pero no el código HTTP; Decisión 2).
2. Confirmación de que el hash es del **String COMPLETO** de la key
   (`plk_` + 43, UTF-8) — punto ya fijado aquí, el spec lo reafirma con la
   cita a Schema §4.2.
3. Anclaje Javadoc de las constantes `CODE_NOT_FOUND`/`MSG_NOT_FOUND`
   duplicadas en `keys` (mismo patrón que C3 al replicar cuerpos de C2) y
   del helper `error()` — verificar contra `ProjectsController` que la
   visibilidad package-private no obliga a más duplicación de lo previsto.
4. Confirmación de que CORS no requiere entrada para `X-API-Key` (el panel
   no lo usa; el ingest no pasa por `cors()`).
5. Desglose exacto de tests (número/nombre) y del guion del smoke (pasos
   curl + psql con los patrones de booleanos/epoch del R-7), incluyendo el
   test de que el formato de `created_at` es el formatter C2.
6. Frontera C5 exacta que queda abierta: firma futura de
   `findByApiKeyHash` y su uso en el filtro de `X-API-Key` de la cadena 1
   (solo documentar, no construir).
7. Nota de spec para C5: la respuesta 401 `invalid_token` actual de la
   cadena 1 (todo el `/api/v1/ingest/**`) es temporal hasta C5 — el spec de
   C4 solo la menciona como firma del límite, sin cambiarla.