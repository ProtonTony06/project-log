---
change: "C4"
artifact: qa-plan
last_updated: 2026-10-24
status: draft
---

# QA Plan — C4 · API keys de ingesta

> Fase sdd-qa (planificación). Fuente ÚNICA de diseño de tests: `.atl/sdd/C4/spec.md`
> (S1–S7, R4.1–R4.16, E4.1–E4.29) y `.atl/sdd/C4/design.md` (D-1..D-6, riesgos
> D-R1..D-R6). AISLAMIENTO: este plan se escribió SIN leer el código de la
> feature (`backend/src/main/java/com/projectlog/keys/**`), el diff de
> `ProjectsRepository`, `scripts/qa_c4_smoke.sh` ni los tests existentes de
> `keys`. Un fallo futuro de un test aquí especificado es, por construcción,
> señal de bug de la IMPLEMENTACIÓN (o una divergencia spec↔implementación),
> no del test. Patrones de entorno tomados del corte anterior (permitido):
> `scripts/qa_c3_smoke.sh` y su spec/design de C3.

Stack real: Spring Boot 3 / Java 21, spring-jdbc (`JdbcTemplate`), PostgreSQL
17, podman/docker compose en repo. Smoke con curl + psql. Recomposición de la
pila con `--force-recreate` (NUNCA `down -v`: el volumen con datos del smoke
se conserva).

## 1. Tabla por-caso de tests

Tipos: **[L]** = test de suite (sin contenedor; `@WebMvcTest` + `@MockitoBean`
o unitario puro; `@ActiveProfiles("test")`), **[S]** = paso del smoke en pila
real (`scripts/qa_c4_smoke.sh`, curl + psql).

| ID | E4.x | Qué se prueba | Tipo | Fichero propuesto | Fixtures |
|---|---|---|---|---|---|
| QP-01 | E4.1 | POST regenerate con JWT sobre ACTIVE: 201 con claves JSON EXACTAS `key, prefix, created_at` en ESE orden, sin claves extra ni `field`; valores = los del mock | [L] MVC | `backend/src/test/java/com/projectlog/keys/ApiKeyControllerMvcTest.java` | `@WebMvcTest` + `@Import(SecurityConfig, JwtAuthFilter, RestAuthenticationEntryPoint, CorsConfig)`; `@MockitoBean` ProjectsRepository + ApiKeyService; `ProjectDto` mock con `status()="ACTIVE"`; repo.regenerateKey→`Optional.of("2026-10-24T10:00:00Z")`; JWT de prueba (patrón `PanelAuthMvcTest` de C2) |
| QP-02 | E4.2 | La `key` de la 201 casa `^plk_[A-Za-z0-9_-]{43}$` (47 chars) y `prefix == key.substring(0, 10)` (`plk_` + 6) | [L] MVC | mismo fichero | mismos mocks de QP-01 |
| QP-03 | E4.3 | 100 llamadas a `newKey()`: TODAS casan la regex plk y TODAS distintas (Done 1) | [L] unitario puro | `backend/src/test/java/com/projectlog/keys/ApiKeyServiceTest.java` | instancia real de `ApiKeyService` (sin BD) |
| QP-04 | E4.4 | `created_at` de la 201 casa `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$` (formatter C2; cierre S4) | [L] MVC | ApiKeyControllerMvcTest | mock repo.regenerateKey→`Optional.of(...)` ya formateado |
| QP-05 | E4.5 | regenerate con `findById` vacío → 404 cuerpo estándar C3; `verifyNoInteractions` de la vía de escritura (regenerateKey NUNCA invocado con id inexistente — orden R4.8) | [L] MVC | ApiKeyControllerMvcTest | mock findById→`Optional.empty()`; verify(repo, never()).regenerateKey + verifyNoInteractions(apiKeyService) |
| QP-06 | E4.6 | regenerate con fila `status()=="ARCHIVED"` → 409 `{code:"project_archived", message: <literal S3>}` sin `field`; generación JAMÁS ocurre (409 ANTECEDE a crypto — cierre S2) | [L] MVC | ApiKeyControllerMvcTest | mock findById→ProjectDto ARCHIVED; `verifyNoInteractions(apiKeyService)` + verify(repo, never()).regenerateKey |
| QP-07 | E4.7 | DELETE sobre ARCHIVED → `revokeKey` true → 204 sin cuerpo (Decisión 3 REVISADA: sin rama de status) | [L] MVC | ApiKeyControllerMvcTest | mock revokeKey→`true` (con fila ARCHIVED solo ilustrativo: NO hay chequeo); verify(repo, never()).findById si el design lo declara sin pre-SELECT |
| QP-08 | E4.8 | DELETE con `revokeKey`→false (empty-RETURNING) → 404 cuerpo estándar | [L] MVC | ApiKeyControllerMvcTest | mock revokeKey→`false` |
| QP-09 | E4.9 | Sin JWT en AMBAS rutas → 401 `invalid_token` (entry point C2); `verifyNoInteractions` de repo y service (nunca llegan al controller) | [L] MVC | ApiKeyControllerMvcTest | request SIN header Authorization; cadena 2 vía `@Import(...)` |
| QP-10 | E4.10 | DELETE con revokeKey→true → 204 y cuerpo HTTP VACÍO (Done 2; el "sin key previa" también es true → 204 idempotente vía mock) | [L] MVC | ApiKeyControllerMvcTest | mock revokeKey→`true`; assert body="" y 204 |
| QP-11 | E4.11 | Primitiva: `tiempoConstanteIgual(sha256(K), sha256(K)) == true`; vector fijo `sha256("abc")` == bytes hex `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad` (ancla UTF-8/32 B) | [L] unitario | ApiKeyServiceTest | ApiKeyService real; K = key plk de prueba |
| QP-12 | E4.12 | `tiempoConstanteIgual(sha256(K_anterior), sha256(K_nueva)) == false` (exactamente el hot-path de C5 contra el hash ya sobrescrito — Done 5) | [L] unitario | ApiKeyServiceTest | dos keys K1≠K2 generadas por `newKey()` |
| QP-13 | E4.13 | Pin de literales SQL: `UPDATE_REGENERAR_KEY` contiene `SET api_key_hash = ?, api_key_prefix = ?, key_created_at = now(), updated_at = now()` + `WHERE id = ?` + `RETURNING key_created_at`; `UPDATE_REVOCAR_KEY` contiene `SET api_key_hash = NULL, api_key_prefix = NULL, key_created_at = NULL, updated_at = now()` + `WHERE id = ? RETURNING id` y SIN `api_key_hash IS NOT NULL` (cierre S1); y `conjuntoSet` SIGUE sin mencionar columnas de key (frontera R4.16a) | [L] pin de literales | `backend/src/test/java/com/projectlog/projects/repository/ProjectsKeySqlTest.java` (patrón `ProjectsUpdateSqlTest` de C3; DEBE vivir en el MISMO paquete — see riesgo TR-7) | acceso package-private a las 2 constantes (divergencia D-3) y a `conjuntoSet` |
| QP-14 | E4.14 | Higiene de logs: `ListAppender` de logback sobre los loggers de las clases de `com.projectlog.keys` (DEBUG incluido) mientras corre regenerate 201 y delete 204 con mocks; NINGÚN evento contiene `plk_`, la key del test ni fragmentos suyos | [L] higiene | `backend/src/test/java/com/projectlog/keys/ApiKeyLogHygieneTest.java` (patrón `LogHygieneTest` de C2) | ListAppender adjuntado a los loggers de keys; mocks como QP-01/QP-10 |
| QP-15 | E4.15 | smoke-of-the-smoke + precondición: recomposición ÚNICA `up -d --build --force-recreate`; pila 3×healthy por podman (backend/frontend/postgres); sondeo auténtico `GET /me` → 401 `invalid_token` | [S] | `scripts/qa_c4_smoke.sh` (fase 0; corrida de VALIDACIÓN antes de la canónica) | `.env` F1; compose.yaml; helpers de C3 (compose wrapper con timeout, `pila_saludable`, `esperar_healthy` tolerante) |
| QP-16 | E4.16 | login → 200 `{token, username, expires_at}`; JWT al tmpdir 700 (nunca en consola) | [S] | mismo script | credenciales ADMIN_INITIAL_* del .env |
| QP-17 | E4.17 | POST /projects 201 con name `smoke-c4-<TS>-a`; **captura de id INCONDICIONAL** (lección C3); ficha `has_key: false`, `api_key_prefix: null` | [S] | mismo script | tmpdir 700 (F_KEY_1/F_KEY_2/F_ID/F_TOKEN); helper `capturar_id` |
| QP-18 | E4.18 | 1.ª POST regenerate (generar) → 201; key casa regex plk; `prefix == key.substring(0,10)`; created_at casa regex ISO-Z — **INSUFICIENTE SOLO: requiere QP-19 en psql (pareja obligatoria)** | [S] | mismo script | key_1 → archivo del tmpdir (JAMÁS consola) |
| QP-19 | E4.19 | **DB-state**: psql `api_key_hash` (BYTEA) == `sha256('<key_1>'::bytea)` → `t` (PG17); `api_key_prefix == substring(key_1,1,10)`; `key_created_at IS NOT NULL` → `t`; NINGÚN campo de la fila contiene la KEY COMPLETA en claro (ver matiz TR-11: el prefix CONTIENE por contrato un fragmento de 10) | [S] psql | mismo script | psql `-qAt` vía compose exec; key interpolada desde tmpdir |
| QP-20 | E4.20 | 2.º POST regenerate → 201 con key_2 ≠ key_1; **DB-state**: `api_key_hash == sha256(key_2::bytea)` → `t` Y `api_key_hash <> sha256(key_1::bytea)` → `t` (invalidación al instante, Done 3) | [S] + psql | mismo script | key_1 y key_2 en tmpdir; comparación ANTES del DELETE de E4.21 (tras revocar el hash es NULL y la aserción ya es imposible) |
| QP-21 | E4.21 | DELETE /projects/{id}/api-key → 204 SIN cuerpo | [S] | mismo script | — |
| QP-22 | E4.22 | **DB-state**: psql `api_key_hash IS NULL AND api_key_prefix IS NULL AND key_created_at IS NULL` → `t` (los TRES a NULL JUNTOS, Done 2) | [S] psql | mismo script | — |
| QP-23 | E4.23 | GET /projects/{id} (C3 SIN cambios): `has_key: false`, `api_key_prefix: null` (derivados SQL de `COLS`) | [S] | mismo script | — |
| QP-24 | E4.24 | Idempotencia en pila (Decisión 2): DELETE de nuevo (fila ya sin key) → 204; psql: los tres SIGUEN a NULL. **NOTA: `updated_at` avanza por cierre S1 y NO se aserta (D-R5)** — un test que aserte updated_at intacto aquí daría FALSO ROJO por diseño | [S] + psql | mismo script | — |
| QP-25 | E4.25 | PATCH `{"status":"ARCHIVED"}` → 200 (C3); POST regenerate sobre ARCHIVED → 409 `{code:"project_archived", …}`; **DB-state**: los tres SIGUEN a NULL (el 409 NO escribió nada — nace del orden S2) | [S] + psql | mismo script | sleep 2 antes del PATCH comparativo si se compara updated_at (resolución de segundos, patrón C3) |
| QP-26 | E4.26 | ARCHIVADO + DELETE api-key → 204 (Decisión 3 revisada: PERMITIDO); **DB-state**: tres NULL + `updated_at` AVANZÓ (epoch: `FLOOR(EXTRACT(EPOCH key_created_at…))` n/a aquí; comparación con `FLOOR(EXTRACT(EPOCH updated_at))::bigint` mayor que el del momento del archivado) | [S] + psql | mismo script | updated_at capturado tras el PATCH de E4.25; sleep 2 antes de comparar |
| QP-27 | E4.27 | POST regenerate sobre id INEXISTENTE (999999…) → 404 `not_found` cuerpo estándar (curl con JWT) | [S] | mismo script | — |
| QP-28 | E4.28 | 401 sin JWT en AMBAS rutas → 401 `invalid_token` (parse tolerante del body ISO-8859-1 del entry point — patrón heredado, sin parse JSON estricto) | [S] | mismo script | sin header Authorization |
| QP-29 | E4.29 | No-regresión C3 ligera: GET /projects → 200 (membresía: el proyecto del smoke sigue en la lista); la ficha de QP-23 ya cubre los derivados. El resto de R4.16 NO sale de aquí (ver §4) | [S] | mismo script | — |
| QP-30 (opcional) | sin E4.x | Rama (6) del R4.8: fila ACTIVE presente + `regenerateKey` → `Optional.empty()` → 404 (segunda barrera del empty-RETURNING; el spec no le asigna ID de escenario — ver hueco G-1) | [L] MVC | ApiKeyControllerMvcTest | mock findById→ACTIVE, mock regenerateKey→`Optional.empty()` |

Cobertura: los 29 E4.x quedan mapeados 1:1 (E4.15–E4.29 → QP-15..QP-29;
E4.1–E4.14 → QP-01..QP-14). QP-30 es un complemento fuera-de-spec marcado
como OPCIONAL.

## 2. Asertos OBLIGATORIOS por caso

Claves: **[M]** mandatorio (sin él el caso es INSUFICIENTE y se pide
complemento), **[O]** opcional/recomendado. Un caso que solo comprueba HTTP
status sin estado queda explícitamente marcado INSUFICIENTE al pie.

### 2.1 MVC controller [L] (QP-01, QP-02, QP-04..QP-10)

- QP-01: [M] 201; [M] conjunto EXACTO y ORDEN de claves JSON `key, prefix,
  created_at` (el orden lo fija el record `ApiKeyResponse` — R4.7; un JSON
  con las tres claves en otro orden o con claves extra FAILA); [M] valores =
  los devueltos por los mocks (la 201 transporta la key que produce
  `newKey()` mockeado y el `created_at` del `Optional` del repo). [O]
  Content-Type application/json.
- QP-02: [M] regex `^plk_[A-Za-z0-9_-]{43}$` sobre `key`; [M] `prefix ==
  key.substring(0, 10)`. [O] `key.length() == 47` (implícito en la regex,
  pero explícito por legibilidad).
- QP-04: [M] regex `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$` sobre `created_at`
  (cierre S4: el repo devuelve el String YA formateado con
  `EXPIRES_AT_FORMATTER`). NOTA: el valor CONCRETO en producción se valida en
  pila (QP-19), no aquí.
- QP-05: [M] 404; [M] `code == "not_found"`; [M] `message ==
  "No existe (quizás fue eliminado)"`; [M] sin clave `field` en el cuerpo;
  [M] ninguna interacción con la escritura (`verifyNoInteractions` del
  `ApiKeyService` — la generación NO ocurre para un id inexistente; R4.8).
  [O] verify(repo, never()).regenerateKey.
- QP-06: [M] 409; [M] `code == "project_archived"`; [M] `message` == literal
  EXACTO del cierre S3 ("El proyecto está archivado: desarchívalo antes de
  generar o regenerar su API key"); [M] sin `field`; [M]
  `verifyNoInteractions(apiKeyService)` + `verify(never()).regenerateKey` —
  el 409 ANTECEDE a la generación (cierre S2: jamás se genera y descarta un
  secret).
- QP-07: [M] 204; [M] cuerpo vacío; [M] mock de `revokeKey` invocado una vez
  con el id (la escritura SÍ ocurre en ARCHIVED — Decisión 3 revisada). [O]
  verificar que NO se llama a `findById` antes (R4.9 sin chequeo de status:
  si aparece un `findById` pre-DELETE, es una rama extra prohibida del
  R4.9 — aserto [M] si así lo decide qa en el momento, pues detecta
  violación del cierre).
- QP-08: [M] 404; [M] cuerpo estándar C3 completo (code/message/sin field).
- QP-09: [M] 401 en POST regenerate Y en DELETE (ambas rutas); [M] body
  contiene el literal `invalid_token` (entry point C2); [M]
  `verifyNoInteractions(projectsRepository)` Y de `apiKeyService` (la cadena
  2 resuelve ANTES del controller). Nota: el 401 de producción es el del
  entry point; C4 nunca lo genera (R4.7).
- QP-10: [M] 204; [M] cuerpo HTTP VACÍO (no `"null"`, no `{}`, vacío de
  verdad). [O] Content-Length 0/ausente.

**Parejas obligatorias de cobertura** (un caso aislado es INSUFICIENTE):

- QP-01/QP-02/QP-04 (201 vía mock) **SIN** QP-19 (hash real en psql) =
  INSUFICIENTE: el mock no prueba que el hash escrito en BD sea sha256 de la
  key ni que los TRES campos se escriban juntos. La pareja [L]+[S] es
  inseparable para el Done 4.
- QP-07/QP-10 (204 vía mock) **SIN** QP-22 (tres NULLs juntos en psql) =
  INSUFICIENTE para el Done 2.
- QP-06 (409 vía mock) **SIN** QP-25 (409 + tres NULLs en pila) =
  INSUFICIENTE para la Decisión 3 revisada.
- QP-05/QP-08/QP-27 (404 en mock y en pila) se cubren mutuamente: los tres
  deberían existir (el 404 de [S] E4.27 y el de [L] E4.5/E4.8).

### 2.2 Primitivas crypto [L] (QP-03, QP-11, QP-12)

- QP-03: [M] 100 keys, todas matching regex plk, y `Set` de tamaño 100
  (todas distintas). [O] cada una length 47.
- QP-11: [M] `tiempoConstanteIgual(sha256(K), sha256(K))` → true; [M] vector
  fijo del digest de "abc" (ancla el UTF-8 y los 32 bytes — imposible
  falsear con hex, salt o Argon2id). [O] `sha256(K).length == 32`.
- QP-12: [M] `tiempoConstanteIgual(sha256(K1), sha256(K2))` → false. Este y
  QP-11 son los ÚNICOS asertos del primitivo exigidos aquí; el smoke E4.20
  (QP-20) replica el caso de uso real en pila (hash anterior ≠ nuevo en BD).

### 2.3 Pin SQL [L] (QP-13)

- QP-13: [M] los DOS literales SQL contienen los fragmentos exactos de
  R4.3 (SET/WHERE/RETURNING); [M] `UPDATE_REVOCAR_KEY` NO contiene
  `api_key_hash IS NOT NULL` (cierre S1); [M] NINGUNA otra columna aparece
  en el SET; [M] `conjuntoSet` (y sus ramas) no mencionan
  `api_key_hash`/`api_key_prefix`/`key_created_at` (frontera R4.16a). [O]
  Javadoc de las constantes (review, no test).

### 2.4 Higiene [L] (QP-14)

- QP-14: [M] cero eventos de log de las clases de `com.projectlog.keys` con
  `plk_`, la key del test o fragmento suyo, a INFO y DEBUG, durante la
  ejecución de una 201 y una 204 completas. [O] cero EVENTOS en absoluto
  (el design declara "sin loggers en el paquete": si aparece un evento
  cualquiera, revisar su contenido antes de pasar el caso).

### 2.5 Smoke [S] (QP-15..QP-29)

- QP-15: [M] recomposición ÚNICA con `--force-recreate` (un `up -d --build`
  a secas deja contenedores contra imagen vieja — lección C3, falso rojo
  E4.1/E3.22); [M] pila 3×healthy por podman (NUNCA curl a /actuator como
  proxy); [M] sondeo auténtico GET /me → 401 con `invalid_token`.
- QP-16: [M] 200; [M] claves `expires_at,token,username`; [M] token SOLO en
  tmpdir 700.
- QP-17: [M] 201; [M] `has_key: false`, `api_key_prefix: null` en ficha; [M]
  id capturado AUN SI la aserción falla (captura incondicional; sin ella
  cascada de BLOQUEADOs).
- QP-18: [M] 201; [M] regex plk sobre key_1; [M] prefix == substring 10;
  [M] regex ISO-Z sobre created_at. **INSUFICIENCIA DECLARADA**: con solo
  el HTTP el caso NO cubre el Done 4 — es la pareja con QP-19.
- QP-19: [M] `SELECT sha256('<key_1>'::bytea) = api_key_hash FROM projects
  WHERE id = ?` → `t` (pincha el hash del String COMPLETO, con `plk_`
  incluido, sin trim ni hex); [M] `api_key_prefix == substring(key_1,
  1, 10)`; [M] `key_created_at IS NOT NULL` → `t`; [M] la KEY COMPLETA no
  aparece en `name`/`description` (los únicos 2 campos de texto libre
  besides prefix; VER MATIZ TR-11 sobre api_key_prefix). [O]
  `to_char(key_created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  == created_at de la 201` (cross-check del formatter S4; tolera el
  truncado a segundos en la captura del timestamp — leer created_at de la
  201 inmediatamente tras el 201 en QP-18, no tras operaciones previas).
- QP-20: [M] 201; [M] key_2 != key_1; [M] psql: `sha256(key_2::bytea) =
  api_key_hash` → `t`; [M] psql: `sha256(key_1::bytea) <> api_key_hash` →
  `t` — ESTE es el aserto de estado del Done 3 (invalidación AL INSTANTE,
  sobrescritura en la misma fila). OJO de orden: correr las psql ANTES de
  E4.21 (tras revoke el hash es NULL: la aserción `< >` ya no es posible).
- QP-21: [M] 204; [M] cuerpo vacío.
- QP-22: [M] psql booleano conjunto `api_key_hash IS NULL AND
  api_key_prefix IS NULL AND key_created_at IS NULL` → `t` (los TRES a la
  vez, invariante Schema §2; NO tres consultas separadas que podrían ocultar
  un estado híbrido — aunque tres separadas también son aceptables como
  complemento, la única `t` conjunta es la aserción del invariante).
- QP-23: [M] 200; [M] `has_key: false`; [M] `api_key_prefix: null`
  (distinguir JSON null de ausencia — helper `campo_json_nul`, patrón C3).
- QP-24: [M] 204 (2.ª vez); [M] psql tres siguen a NULL. [**NO ASERTAR**]
  updated_at aquí (cierre S1/D-R5: avanza por diseño; una aserción de
  invarianza daría falso rojo, una de avance está cubierta en QP-26 donde
  sí es semánticamente exigida por el spec).
- QP-25: [M] PATCH archivar → 200 y `status: ARCHIVED`; [M] regenerate →
  409; [M] `code == "project_archived"` (y [O] message == literal S3); [M]
  psql: los tres SIGUEN a NULL (el 409 no escribió NADA — aserto de estado
  que SOLO puede salir de pila, imposible de simular con mock).
- QP-26: [M] 204 en ARCHIVED; [M] psql tres NULL; [M] `updated_at` avanzó
  con `FLOOR(EXTRACT(EPOCH updated_at))::bigint` > epoch del updated_at
  capturado tras el archivado de QP-25 (cierre S1: la fila SÍ se reescribe
  incluso sin key previa activa; pattern C3 iso_a_epoch + sleep 2 para la
  resolución de segundos).
- QP-27: [M] 404; [M] code `not_found`; [O] message estándar.
- QP-28: [M] 401 en AMBAS rutas; [M] literal `invalid_token` con grep
  tolerante (ISO-8859-1 del entry point; NUNCA parse JSON estricto del body
  del 401 — deuda heredada C8/C9). [O] verificar contraste: las mismas
  rutas CON el JWT de QP-16 respondieron 201/204 arriba (la correlación
  prueba que el 401 es por JWT ausente y no por otra causa).
- QP-29: [M] 200; [M] membresía del proyecto del smoke. [O] diff-0 sobre
  la lista de intocables (estilo E3.24, adaptado: SecurityConfig,
  CorsConfig, V1__init.sql, pom.xml, application-test.yml, docs/**,
  mockups) — de NO incluirse en el smoke, es obligación explícita del
  sdd-verify (§4).

### 2.6 Higiene del smoke (transversal, R4.15/§2.6 nota)

La key revelada (key_1, key_2) va al tmpdir 700 y NUNCA se imprime a
consola; el JWT igual. Cualquier `echo`/`set -x` que las toque es un fallo
del propio smoke, independiente del veredicto E4.n. [O] grep final del
stderr capturado por `plk_` como auto-cheque.

## 3. Riesgos de test

| ID | Riesgo | Impacto / señal de falso rojo | Mitigación |
|---|---|---|---|
| TR-1 | El test-writer copia patrones de C2/C3 de memoria SIN releer el spec C4. Errores típicos: (a) esperar 400 por cuerpo de request con `@ExceptionHandler`/`HttpMessageNotReadable` — en C4 NO hay `@RequestBody` ni handlers; (b) esperar **403** `project_archived` al regenerar sobre archivado (versión PREVIA de la Decisión 3) — el cierre REVISADO es **409** en panel (el 403 es de INGESTA, C5); (c) esperar 400/409 al DELETE de un proyecto sin key — es **204 idempotente** (Decisión 2); (d) escribir asserts con `field` en los cuerpos de error — en C4 van SIEMPRE sin `field`; (e) firmar el helper de error con varargs | Falsos ROJOS en verde o, peor, falsos VERDES que ratifican una implementación divergente | Tests escritos SOLO desde spec+design (este doc + E4.x); toda expectativa citada de las tablas R4.7–R4.10 |
| TR-2 | Smoke rojo por imagen vieja: `up -d --build` sin `--force-recreate` reconstruye la imagen pero DEJA los contenedores viejos (lección C3 E3.1/E3.22) | 201 sin las rutas nuevas (404 del Spring por no existir el mapping) — se lee como bug | QP-15 marca la recomposición ÚNICA con `--force-recreate` como MANDATORIA |
| TR-3 | Parse JSON estricto del body del 401: el entry point responde ISO-8859-1 (deuda C8/C9); un `json.loads` estricto explota | Falso rojo en QP-09/QP-28 aunque la pila esté bien | Grep tolerante del literal `invalid_token` (patrón C3), nunca JSON.parse del 401 |
| TR-4 | Psql y formatos: (a) computar SHA-256 sobre text vs bytea — la función `sha256()` de PG17 sobre `bytea` es el análogo correcto de los bytes UTF-8 del String; un `sha256(key::text)` produce LO MISMO en PG pero conviene fijar `::bytea` del spec; (b) updated_at con epoch (`FLOOR(EXTRACT(EPOCH…))::bigint`) y la resolución de segundos del formatter — comparar epoch de timestamps a 1 s de diferencia sin sleep 2 da falso rojo (patrón C3); (c) booleanos `t/f` de psql vs `true/false` de JSON — mezclarlos en un parser | Falsos rojos de entorno leídos como bugs de la feature | Helpers heredados de C3 (`psql_q`, `iso_a_epoch`, `campo_json_nul`); sleep 2 antes de comparaciones de updated_at (QP-26) |
| TR-5 | Fuga del secret: imprimir la key_1/key_2 o el JWT a consola (y en [L], un mensaje de failure de JUnit que incluya la key) | Violación de R4.13/§2.6 aunque los veredictos den PASS; el smoke queda inútil como evidencia | key/JWT SOLO en tmpdir 700; en [L] nunca interpolar la key en mensajes de assert. [O] mitigo psql: pasar la key con `-v` de psql en lugar de interpolarla en la SQL (queda en argv del proceso — patrón C3 la tolera, la mitigación es opcional) |
| TR-6 | Captura de id solo en rama feliz de E4.17 (lección C3: captura incondicional) | Si la ficha falla, cascada BLOQUEADOs de E4.18–E4.27 = un fallo contado como quince y salida ambigua | QP-17 exige capturar el id SIEMPRE |
| TR-7 | Visibilidad D-3: `UPDATE_REGENERAR_KEY`/`UPDATE_REVOCAR_KEY` son package-private (divergencia menor D-3 del design). Un `ProjectsKeySqlTest` en OTRO paquete (p. ej. en `keys`) no compilaría; y si la implementación las dejó `private` como decía el literal R4.3, el pin no puede leerlas | Error de compilación del pin o tentación de reflection frágil | El pin vive en `com.projectlog.projects.repository` (patrón `ProjectsUpdateSqlTest`). Si salen `private`: NO arreglar con reflection sin decidir; marcar el caso FAIL y ESCALAR la divergencia D-3/D-R6 a quien corresponda (es exactamente el fallo-divergencia que este plan aislado debe Detectar) |
| TR-8 | Asertar updated_at en el revoke IDEMPOTENTE (QP-24): por cierre S1 avanza por diseño (D-R5) | Falso rojo (aserción de invarianza) o aserción redundante que confunde | QP-24: updated_at NO se aserta; el avance se aserta DONDE el spec lo pide (QP-26) |
| TR-11 | Aserción IMPOSIBLE del check de plaintext: `api_key_prefix` contiene por CONTRATO `key.substring(0,10)` (Schema §2) — un test que pida "`api_key_prefix` sin aparición de la key" con la key completa como aguja lo pasaría; uno con "fragmento de la key" como aguja SIEMPRE fallaría (el prefix es un fragmento de la key, por diseño) | Falso rojo estructural o check absurdo | El check correcto de QP-19: la KEY COMPLETA (47 chars) no aparece en NINGÚN campo; y `api_key_prefix == substring(key, 1, 10)` como aserción SEPARADA de conformidad |
| TR-9 | QP-03 (entropía en muestra): colisión de 2 SHA-256 en 100 muestras ~ imposible; falso rojo solo si el RNG está roto | Prácticamente nulo | Aceptado; si pasa, indicio real de bug |
| TR-10 | Orden de la corrida en pila: E4.20 (comparación `hash <> sha256(key_1)`) DEBE ejecutarse antes del DELETE E4.21 | Tras revoke el hash es NULL y la comparación daría falso rojo (NULL <> … → NULL/t según cómo se escriba) | Orden del guion §2.6 respetado; cada psql de estado se corre inmediatamente tras su llamada HTTP |
| TR-12 | Cross-check created_at↔key_created_at (QP-19 [O]): el formatter trunca a segundos y `now()` puede cruzar el segundo entre el 201 y el psql | Falso rojo de 1 s | Capturar created_at de la 201 en QP-18 psql inmediato; en caso de divergencia de 1 s tratar como ruido, no como bug |

## 4. NO cubierta (scope explícito para sdd-verify)

Los siguientes puntos quedan FUERA de la cobertura de tests de C4 (ni [L]
ni [S]); el sdd-verify debe tratarlos como review/ausencia o delegated a
cortes futuros:

1. **Ingesta real con X-API-Key (C5)**: los 401 `api_key_invalid` y 403
   `project_archived` del endpoint de ingesta NO se prueban aquí (Decisión 1
   del gate: la validación REAL "la key ya no autentica al ingestar" es Done
   del smoke de C5). En C4 la cadena 1 sigue `denyAll()`: TODO
   `/api/v1/ingest/**` responde 401 `invalid_token` con o sin `X-API-Key`,
   y el humo E4.15 la usa como SONDEO de precondición, no como contrato
   asertado de la feature.
2. **Front (C8)**: ni wiring de la maqueta 05 ni modales (Decisión 4). La
   nota "C4: sin regenerate/revocar" en la copia de C3 debe seguir siendo
   verdadera al cerrar C4 (check de review, no de test).
3. **`findByApiKeyHash` (C5)**: NO existe en C4 (R4.5, Decisión 5).
   Verificación por review de AUSENCIA, no por test (señalar si aparece).
4. **Comportamiento de `CorsConfig` con `X-API-Key` (cierre S7)**: sin
   escenario E4.x. Solo review de fichero INTOCADO (sin `X-API-Key` en
   `allowedHeaders`).
5. **`DuplicateKeyException` NO capturado (R-4 → 500, cierre S6 / D-R1)**:
   sin test (la colisión de hash es astronómica; un test que la forzaría
   mockeando el UPDATE es trivial y NO añade señal — si el equipo la
   quisiera sería opcional, no mandatoria).
6. **`updated_at` en el revoke idempotente (D-R5, QP-24)**: NO se aserta ni
   su avance ni su invarianza en E4.24; el avance está asertado SOLO en
   E4.26 (QP-26) donde el spec lo exige.
7. **Ventana TOCTOU D-R2** (regenerar sobre ARCHIVADO con un PATCH
   archivador concurrente): documentada como aceptada; no testable
   determinísticamente y sin escenario.
8. **Rama (6) del R4.8 en PILA** (404 por empty-RETURNING de la 2.ª
   barrera): inalcanzable hoy (DELETE de proyectos es C10); solo cubrible
   vía mock (QP-30, OPCIONAL). Hueco detectado G-1: el spec no le asigna
   escenario E4.x — se recomienda a sdd-tasks incluir el QP-30 como caso
   adicional.
9. **Threading/estrés** (`SecureRandom` estático y `MessageDigest` por
   llamada bajo request concurrentes): sin escenario de carga.
10. **No-regresiones C3 completas (R4.16)**: E4.29 es la versión LIGERA
    (GET /projects + derivados). La lista completa (suite [L] de C3
    re-corrida verde en la imagen builder, diff-0 de intocables, `conjuntoSet`
    sin columnas de key) reparte: `conjuntoSet`→QP-13, suite C3→criterio de
    PASS global (§5), diff-0 intocables→[O] de QP-29 u obligación del
    sdd-verify.
11. **Semántica "archivar NO toca la key / misma key vuelve al
    desarchivar" (R4.12, regla 11)**: no hay escenario E4.x dedicado a
    desarchivar y comprobar que el hash persiste intacto (E4.25 solo prueba
    que el 409 no escribe). Hueco menor G-2 — el smoke podría añadirlo como
    paso opcional (PATCH ACTIVE + psql hash == hash previo), pero NO es
    mandatorio en C4; el comportamiento real (la key autentica de nuevo) es
    observabilidad de C5.

## 5. Criterios de PASS por caso

### 5.1 Suite [L] (QP-01..QP-14, QP-30)

- Cada caso QP-x PASS = todos sus asertos [M] pasan en una única ejecución
  de JUnit (JUnit 5: un aserto [M] fallido = FAIL del caso, y el caso
  reporta qué asertó mediante mensajes descriptivos). Un aserto [O] fallido
  NO bloquea el PASS del caso, pero DEBE figurar en el reporte de qa como
  nota (los [O] "fuertemente recomendados" — QP-19 cross-check de fechas,
  QP-29 diff intocables — se reportan siempre).
- Ejecución: `mvn test` DENTRO de la imagen builder
  `maven:3.9-eclipse-temurin-21` (backend + `~/.m2` montados, sin BD), con
  `application-test.yml` INTOCABLE. PASS de la suite [L] = exit 0 de `mvn
  test` con TODOS los tests de C4 (y los de C2/C3 re-corridos) en verde.
- Un FAIL de cualquier [M] de QP-01..QP-14 con el código conforme a este
  plan = bug de la IMPLEMENTACIÓN (aislamiento de autoría): NO se "ajusta"
  el test para hacerlo pasar; se reporta la divergencia spec↔código.

### 5.2 Smoke [S] (QP-15..QP-29)

- Salida: una línea `E4.n: PASS | FAIL | BLOQUEADO (detalle)` por ID (como
  C3); exit **0** = todos PASS; exit **1** = algún FAIL; exit **2** = solo
  BLOQUEADO (p. ej. `.env` sin credenciales/precondiciones fallidas —
  mapear TODAS las líneas a BLOQUEADO, nunca mezclar FAIL con entorno roto).
- `BLOQUEADO` está reservado a ENTORNO (pila no compone, healthy falla, .env
  incompleto, id no capturable por fallo previo encadenado). `FAIL` es
  SIEMPRE una aserción sobre el comportamiento del sistema en pila que
  obtuvo respuesta evaluable.
- PASS de QP-15 = recomposición con `--force-recreate` completada + 3×
  healthy por podman + sondeo /me 401 `invalid_token`. PASS de la corrida
  canónica requiere también el smoke-of-the-smoke previo (corrida de
  validación del script antes de la canónica — R4.15).
- psql diffs: cada chequeo de hash/NULL/updated_at es una comparación cuyo
  único output admisible en `-qAt` es `t` (o el string exacto esperado).
  Un output vacío, `f`, `NULL` o error de ON_ERROR_STOP = FAIL de la línea.
  Ejemplos concretos: `SELECT sha256('<key>'::bytea) = api_key_hash FROM
  projects WHERE id = ?` → `t`; `SELECT api_key_hash IS NULL AND
  api_key_prefix IS NULL AND key_created_at IS NULL FROM projects WHERE id
  = ?` → `t`; comparaciones de epoch de `updated_at` (QP-26) con
  `FLOOR(EXTRACT(EPOCH …))::bigint` + sleep 2.
- Higiene de runtime (no negociable): key_1/key_2 y JWT nunca en consola
  (tmpdir 700); los veredictos no dependen de ello, pero el smoke con
  secretos impresos no es evidencia válida.

### 5.3 PASS GLOBAL del corte C4

PASS = (a) suite [L] exit 0 (incluida la suite de C2/C3 re-corrida en el
builder, R4.16); (b) smoke `qa_c4_smoke.sh` exit 0 con las 15 líneas
E4.15–E4.29 en PASS; (c) E4.14 (higiene) y E4.13 (pin SQL) PASS
explícitamente citados en el reporte; (d) cero asertos [O] fallidos sin
nota. El FAIL de pareja obligatoria (ej.: E4.18 PASS pero E4.19 FAIL) deja
la pareja en FAIL: los 9 Done no se consideran cubiertos con la mitad de
una pareja.