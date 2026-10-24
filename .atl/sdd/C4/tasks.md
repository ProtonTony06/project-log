---
change: "C4"
artifact: tasks
last_updated: 2026-10-24
status: draft
---

# Tasks — C4 · API keys de ingesta

> Desglose fino y secuencial del corte C4 a partir del spec closed
> (E4.1–E4.29, R4.1–R4.16) y del design (micro-decisiones D-1..D-6).
> Cada tarea lista ficheros, criterio de cierre VERIFICABLE (test/comando),
> dependencias y trazabilidad a escenarios E4.x. Sin front (Decisión 4),
> sin migraciones (V1 intocable), sin SecurityConfig/CorsConfig/pom/docs.

## Notas transversales (aplican a TODAS las tareas de código)

- **D-3 (divergencia preservada, NO corregir hacia el spec)**: las
  constantes `UPDATE_REGENERAR_KEY` / `UPDATE_REVOCAR_KEY` son `static final
  String` **package-private** en `com.projectlog.projects.repository` (lo que
  R4.3 llama `private`), y el pin E4.13 vive en ese MISMO paquete (patrón
  `ProjectsUpdateSqlTest`). PROHIBIDO: marcarlas `private`, colocar el pin en
  otro paquete, o usar reflection (`getDeclaredField` + `setAccessible`).
- **D-2 (divergencia preservada)**: el 204 se construye con
  `ResponseEntity.status(HttpStatus.NO_CONTENT).build()` — NO con el snippet
  `ResponseEntity.noContent().build()` de R4.7 (ilustrativo; el
  `HeadersBuilder` produciría `ResponseEntity<Void>` no asignable a
  `ResponseEntity<Object>` y NO compila).
- **D-1**: el chequeo ARCHIVED del POST es `findById(id)` de C3 comparando
  `"ARCHIVED".equals(...)` — cero SQL de lectura nuevo, sin
  `existsStatus`/`WHERE status <> 'ARCHIVED'` en el UPDATE.
- **D-4/D-5/D-6**: helper `error()` private de 3 parámetros exactos (sin
  varargs `field`), cuerpos de error como `LinkedHashMap` ordenado
  `code → message`, y cero Javadoc de C5 más allá de los anclas del spec.
- **Intocables** (R4.16c — `git diff --exit-code` debe dar 0 en cada uno al
  cerrar el corte): `config/SecurityConfig.java`, `config/CorsConfig.java`,
  `config/RestAuthenticationEntryPoint.java`, `config/StartupSecretsValidator.java`,
  `db/migration/V1__init.sql`, `application.yml`, `application-test.yml`,
  `backend/pom.xml`, `compose.yaml`, `docs/**`,
  `frontend/public/mockups/**`, `auth/*` de C2 (incl. `LoginController` — su
  `EXPIRES_AT_FORMATTER` se usa POR REFERENCIA desde el repo).
- **Entorno heredado C3 (R4.15/R-7)** para las tareas 5.x: re-ejecución de
  `mvn test` DENTRO de la imagen builder `maven:3.9-eclipse-temurin-21`
  (backend + `~/.m2` montados, sin BD); recomposición de pila SOLO con
  `podman compose up -d --build --force-recreate`; psql `-qAt` con epoch
  `FLOOR(EXTRACT(EPOCH …))::bigint` donde toque y booleanos `t/f` (psql) ≠
  `true/false` (JSON); nombres sufijados por ejecución; **smoke-of-the-smoke**
  antes de la corrida canónica. Sin push a remoto; commits locales por fase
  (patrón C3), con la atribución de la sesión.

---

## 1. Service crypto + tests unitarios (primitivas ADR-4)

- [x] **T4.1 — Crear `keys/ApiKeyService.java`** — 4 primitivas, frontera crypto pura.
  - Fichero: `backend/src/main/java/com/projectlog/keys/ApiKeyService.java` (NUEVO).
  - Contenido (R4.11, design §1.2): `@Service`; `private static final
    SecureRandom ALEATORIO` + `private static final String PREFIJO = "plk_"`;
    `newKey()` = `PREFIJO + Base64.getUrlEncoder().withoutPadding()
    .encodeToString(<32 bytes>)` → 47 chars, UNA generación por llamada;
    `byte[] sha256(String key)` = `MessageDigest.getInstance("SHA-256")
    .digest(key.getBytes(StandardCharsets.UTF_8))` del String COMPLETO,
    MessageDigest NUEVO por llamada; `String prefixDe(String key)` =
    `key.substring(0, 10)`; `boolean tiempoConstanteIgual(byte[] a, byte[] b)`
    = wrapper de `MessageDigest.isEqual`. SIN loggers, SIN BD, SIN estado de
    instancia (D-R3).
  - PROHIBIDO: Argon2id/BCrypt (ADR-4), hex, salt, trim de `plk_` en sha256.
  - Cierre: compila en el builder (`mvn -q test-compile` dentro de la imagen
    `maven:3.9-eclipse-temurin-21`) sin tocar pom.xml.
  - Deps: ninguna. Cubre habilitación de: E4.2, E4.3, E4.11, E4.12 (y E4.14).

- [x] **T4.2 — Test unitario `ApiKeyServiceTest`** — Done 5 (primitiva) + Done 1 parcial.
  - Fichero: `backend/src/test/java/com/projectlog/keys/ApiKeyServiceTest.java` (NUEVO).
  - Casos (spec §2.2, puro, sin BD ni Spring):
    - E4.3 `newKeyGenera47CharsConEntropiaEnMuestra` — 100 llamadas: TODAS
      casan `^plk_[A-Za-z0-9_-]{43}$` y TODAS distintas.
    - E4.11 `sha256EsDelStringCompletoYParejaCorrectaDaTrue` —
      `tiempoConstanteIgual(sha256(K), sha256(K)) == true` y vector fijo
      `sha256("abc")` == hex
      `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`.
    - E4.12 `tiempoConstanteIgualConHashAnteriorDaFalse` —
      `tiempoConstanteIgual(sha256(K_anterior), sha256(K_nueva)) == false`.
  - Cierre: `mvn test` (builder) con esta clase verde. Este test es el Done 5
    de la proposal y parte del Done 1 reformulado (primitiva).
  - Deps: T4.1. Cubre: E4.3, E4.11, E4.12.

- [x] **T4.3 — Crear `keys/ApiKeyResponse.java`** — record de salida de la 201.
  - Fichero: `backend/src/main/java/com/projectlog/keys/ApiKeyResponse.java` (NUEVO).
  - Contenido (R4.7, design §1.3): `public record ApiKeyResponse(String key,
    String prefix, String created_at) {}` — accessors snake_case serializados
    por Jackson SIN `@JsonProperty` (patrón `ProjectDto`/`LoginResponse` de
    C2); el ORDEN de componentes fija el orden de las claves JSON del
    contrato. Único lugar donde la key completa vive como valor transportado.
  - Cierre: compila en el builder (sin test propio — se prueba vía MVC E4.1).
  - Deps: ninguna (paralelizable con T4.1/T4.2). Habilita: E4.1, E4.2, E4.4.

---

## 2. Estamentos SQL aditivos en ProjectsRepository + pin E4.13

- [x] **T4.4 — Extensión ADITIVA de `ProjectsRepository`** — 2 estamentos + 2 métodos.
  - Fichero (ÚNICO de cortes anteriores que se toca en C4):
    `backend/src/main/java/com/projectlog/projects/repository/ProjectsRepository.java`.
  - Cambio PURAMENTE ADITIVO al final de la clase, tras `conjuntoSet`
    (R4.2–R4.4, design §3):
    - Constantes **package-private** (¡D-3!, NO `private`)
      `UPDATE_REGENERAR_KEY` y `UPDATE_REVOCAR_KEY` con los literales EXACTOS
      del R4.3: (1) `SET api_key_hash = ?, api_key_prefix = ?,
      key_created_at = now(), updated_at = now() WHERE id = ? RETURNING
      key_created_at`; (2) `SET api_key_hash = NULL, api_key_prefix = NULL,
      key_created_at = NULL, updated_at = now() WHERE id = ? RETURNING id` —
      el WHERE de (2) es `id = ?` A SECAS (cierre S1: SIN
      `api_key_hash IS NOT NULL`).
    - `public Optional<String> regenerateKey(long id, byte[] hash, String p)`
      con `@Transactional`: `jdbcTemplate.query(...)` sobre `List` (NUNCA
      `queryForObject`), rowmapper `getObject("key_created_at",
      OffsetDateTime.class)`, binding en orden `hash, p, id`; 0 filas →
      `empty()`, 1 fila → `Optional.of(...format(
      LoginController.EXPIRES_AT_FORMATTER))` (cierre S4: formateo EN el repo).
    - `public boolean revokeKey(long id)` con `@Transactional`: same-List
      patrón, `rs.getLong("id")`, `!filas.isEmpty()`.
    - Cero pre-SELECT (404 por empty-RETURNING, una ida). Javadoc de clase
      AMPLIADO aditivamente (C4 añadido a consumidores).
  - VERIFICACIÓN DE FRONTERA antes de cerrar: `git diff` sobre el fichero
    muestra SOLO adiciones — el UPDATE existente de C3
    (`SQL_UPDATE_PREFIX`/`conjuntoSet`/`SQL_UPDATE_SUFFIX`) y las 4
    operaciones de C3 sin NI UNA línea cambiada; `conjuntoSet` sigue sin
    mencionar `api_key_*`/`key_created_at` (frontera R4.16a/R-4 de C3).
  - Cierre: compila en builder; `git diff` del fichero revisado como arriba.
  - Deps: ninguna (paralelizable con la Fase 1). Habilita: E4.13, y vía
    controller E4.5–E4.10; en pila E4.19–E4.22, E4.24, E4.26.

- [x] **T4.5 — Test pin SQL `ProjectsKeySqlTest`** (E4.13).
  - Fichero: `backend/src/test/java/com/projectlog/projects/repository/ProjectsKeySqlTest.java`
    (NUEVO — MISMO paquete que el repo, patrón `ProjectsUpdateSqlTest`).
  - Contenido (E4.13, cierre S1):
    - `UPDATE_REGENERAR_KEY` contiene literalmente `SET api_key_hash = ?,
      api_key_prefix = ?, key_created_at = now(), updated_at = now()` +
      `WHERE id = ?` + `RETURNING key_created_at`.
    - `UPDATE_REVOCAR_KEY` contiene literalmente `SET api_key_hash = NULL,
      api_key_prefix = NULL, key_created_at = NULL, updated_at = now()` +
      `WHERE id = ? RETURNING id` + ASSERT de AUSENCIA de
      `api_key_hash IS NOT NULL` en el WHERE (S1) y de CUALQUIER otra columna.
    - Re-verificación: `conjuntoSet` NO menciona columnas de key
      (frontera R4.16a, pin heredado).
  - Sin BD (pin de literales, patrón C3). Cierre: `mvn test` con esta clase
    verde en builder.
  - Deps: T4.4. Cubre: E4.13 (y re-ancla la frontera R4.16a).

---

## 3. ApiKeyController + ApiKeyResponse wiring + tests MVC

- [x] **T4.6 — Crear `keys/ApiKeyController.java`** — controller FINO, 2 mappings, ramas R4.8/R4.9.
  - Fichero: `backend/src/main/java/com/projectlog/keys/ApiKeyController.java` (NUEVO).
  - Contenido (R4.6–R4.10, design §1.1/§2/§4):
    - Constantes (cierre S5 — DUPLICADAS con Javadoc de ancla, nunca se
      edita C3): `CODE_NOT_FOUND`, `MSG_NOT_FOUND`,
      `CODE_PROJECT_ARCHIVED = "project_archived"`, `MSG_PROJECT_ARCHIVED`
      (literal S3 exacto del spec).
    - Constructor `ApiKeyController(ProjectsRepository, ApiKeyService,
      ObjectMapper)`; SIN `@Valid`, SIN `@RequestBody`, SIN
      `@ExceptionHandler`.
    - `@PostMapping("/api/v1/panel/projects/{id}/api-key/regenerate")` con
      ramas R4.8 EXACTAS: findById vacío → 404; `"ARCHIVED".equals(status)` →
      409; solo después `newKey()` → `prefixDe` → `sha256` →
      `regenerateKey(id, hash, prefix)`; `empty()` del RETURNING → 404; else
      201 `new ApiKeyResponse(key, prefix, ts)`.
    - `@DeleteMapping("/api/v1/panel/projects/{id}/api-key")` con ramas R4.9:
      `revokeKey(id)` → true/`ResponseEntity.status(HttpStatus.NO_CONTENT)
      .build()` (¡D-2, NO `noContent().build()`!) / false → 404; SIN chequeo
      de status (Decisión 3 revisada: permitido en ARCHIVADO).
    - Helper `error(HttpStatus, String, String)` **private**, 3 parámetros
      exactos (D-4: sin varargs `field`), `LinkedHashMap` ordenado
      `code → message` + `ObjectMapper.valueToTree` +
      `MediaType.APPLICATION_JSON` (D-5).
    - `DuplicateKeyException` NO se captura (cierre S6, riesgo R-4 → 500).
  - Cierre: compila en builder; los tests de T4.7/T4.8 pasan contra este
    controller (ninguno mockea más allá de repo/service).
  - Deps: T4.1, T4.3, T4.4. Habilita: E4.1, E4.2, E4.4–E4.10; en pila
    E4.18, E4.20, E4.21, E4.25, E4.26, E4.27.

- [x] **T4.7 — Test MVC `ApiKeyControllerMvcTest`** (E4.1, E4.2, E4.4–E4.10).
  - Fichero: `backend/src/test/java/com/projectlog/keys/ApiKeyControllerMvcTest.java` (NUEVO).
  - Patrón (suite [L], design §10): réplica de `PanelAuthMvcTest` de C2 —
    `@WebMvcTest(ApiKeyController.class)` + `@Import(SecurityConfig,
    JwtAuthFilter, RestAuthenticationEntryPoint, CorsConfig)` +
    `@ActiveProfiles("test")` + `@MockitoBean` de `ProjectsRepository` y
    `ApiKeyService`; `application-test.yml` intocable.
  - Casos (spec §2.1):
    - E4.1 `regenerateSobreProyectoActivoResponde201ConClavesExactas` — 201
      con claves JSON EXACTAS `key, prefix, created_at` en ese orden (el mock
      del repo devuelve `Optional.of(<ts formateado>)` y el service devuelve
      key/prefix/hash del mock).
    - E4.2 `regenerateKeyNaceCasandoLiteralDeFormatoPlk` — key del mock casa
      `^plk_[A-Za-z0-9_-]{43}$` y `prefix == key.substring(0, 10)`.
    - E4.4 `created_atConFormatoFormatterDeC2` — casa
      `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$` (cierre S4; valor real en
      psql = E4.19).
    - E4.5 `regenerateProjectoInexistenteResponde404CuerpoEstandar` — 404
      `{code: "not_found", message: "No existe (quizás fue eliminado)"}` sin
      `field`; `verifyNoInteractions(apiKeyService)` y del vía escritura
      (`regenerateKey` NUNCA invocado — orden R4.8).
    - E4.6 `regenerateSobreArchivadoResponde409ProjectArchived` — 409
      `{code: "project_archived", message: <literal S3>}` sin `field`;
      `verifyNoInteractions(apiKeyService)` (el 409 ANTECEDE a la
      generación — cierre S2).
    - E4.7 `deleteSobreArchivadoPermitidoResponde204` — `revokeKey` mock →
      true con proyecto ARCHIVED → 204 SIN cuerpo (sin ninguna aserción de
      status previo: no hay rama).
    - E4.8 `deleteProyectoInexistenteResponde404` — `revokeKey` → false →
      404 cuerpo estándar.
    - E4.9 `sinJwtAmbasRutasResponde401InvalidToken` — 401 `invalid_token`
      del entry point en las 2 rutas (cadena 2); `verifyNoInteractions` del
      repo (patrón `PanelAuthMvcTest`).
    - E4.10 `deleteExitosoResponde204SinCuerpo` — true → 204 y cuerpo HTTP
      VACÍO (Done 2 vía mock; idempotencia Decisión 2).
  - Cierre: `mvn test` con esta clase verde en builder.
  - Deps: T4.6. Cubre: E4.1, E4.2, E4.4–E4.10.

- [x] **T4.8 — Test de higiene `ApiKeyLogHygieneTest`** (E4.14, Done 6).
  - Fichero: `backend/src/test/java/com/projectlog/keys/ApiKeyLogHygieneTest.java` (NUEVO).
  - Contenido (R4.13): réplica del patrón `LogHygieneTest` de C2
    (`backend/src/test/java/com/projectlog/auth/LogHygieneTest.java`) —
    `ListAppender` de logback sobre los loggers de TODAS las clases de
    `com.projectlog.keys`; ejecutar un regenerate (201) y un delete (204)
    con mocks (mismo armado que T4.7) con nivel DEBUG activado para esos
    loggers; NINGÚN evento contiene `plk_`, la key del test ni fragmentos
    suyos.
  - Cierre: `mvn test` con esta clase verde en builder.
  - Deps: T4.6. Cubre: E4.14.

---

## 4. Smoke script `scripts/qa_c4_smoke.sh`

- [x] **T4.9 — Escribir `scripts/qa_c4_smoke.sh`** (E4.15–E4.29, Done 8).
  - Fichero: `scripts/qa_c4_smoke.sh` (NUEVO).
  - Patrón: réplica PROPIA del esqueleto de `scripts/qa_c3_smoke.sh` (helpers
    propios: anti-veneno de env, carga `.env` línea a línea sin exportar,
    `compose_` con timeouts, `pila_saludable`, `esperar_healthy` vía
    `GET /me` → 401, `psql_q -qAt`, `req` curl, `campo_json`/
    `campo_json_nul`/`claves_json`, `capturar_id` INCONDICIONAL, `bearer_hdr`,
    `iso_a_epoch`). NUNCA invoca el script de C2 ni C3 (down con `-v`
    destruiría el volumen).
  - Higiene runtime (R4.13/nota E4.15): la key revelada de cada 201 va a
    tmpdir 700 (`chmod 700`, trap de limpieza) y JAMÁS a consola; tampoco el
    JWT. En psql la key solo pasa como parámetro para computar
    `sha256('<key>'::bytea)` (leerla del tmpdir, escapar comilla simple
    `'` → `''`).
  - Pasos codificados (spec §2.5/§2.6, un case/función por E4.n, salida
    `E4.n: PASS | FAIL | BLOQUEADO (detalle)` por ID, exit 0/1/2):
    - E4.15 precondición: recomposición ÚNICA `podman compose up -d --build
      --force-recreate`, 3×healthy por podman, sondeo auténtico
      `GET /me` → 401 `invalid_token`.
    - E4.16 login → 200 `{token, username, expires_at}`; JWT al tmpdir.
    - E4.17 `POST /projects` con `smoke-c4-<TS>-a` → 201; captura de id
      incondicional (lección E3.1); ficha `has_key: false`.
    - E4.18 1er `POST …/api-key/regenerate` → 201; key casa
      `^plk_[A-Za-z0-9_-]{43}$`; `prefix == key.substring(0,10)`;
      `created_at` casa la regex ISO-Z.
    - E4.19 psql (Done 4): `api_key_hash = sha256('<key>'::bytea)` → `t`
      (PG17); `api_key_prefix` == 10 chars; `name/description/
      api_key_prefix` SIN aparición de la key en claro.
    - E4.20 2º regenerate → 201 key DISTINTA (Done 3); psql: hash ==
      `sha256(key_nueva)` → t Y `<> sha256(key_anterior)` → t.
    - E4.21 `DELETE …/api-key` → 204 SIN cuerpo.
    - E4.22 psql (Done 2): los TRES a NULL juntos → t.
    - E4.23 `GET /projects/{id}` → `has_key: false`, `api_key_prefix: null`
      (derivados C3 sin diff).
    - E4.24 DELETE de nuevo (sin key) → 204; psql: tres siguen a NULL
      (nota: `updated_at` avanza — cierre S1 — NO se aserta).
    - E4.25 `PATCH {"status":"ARCHIVED"}` (C3, 200) → regenerate → 409
      `{code: "project_archived", …}`; psql: los tres SIGUEN a NULL (el 409
      NO escribió nada).
    - E4.26 DELETE sobre ARCHIVADO → 204 (psql: tres NULL; `updated_at`
      avanzó — solo informativo).
    - E4.27 regenerate sobre id inexistente (999999) con JWT → 404
      `not_found` estándar.
    - E4.28 401 sin JWT en AMBAS rutas (`invalid_token`, parse tolerante
      ISO-8859-1 — patrón heredado de E3.21).
    - E4.29 no-regresión C3 ligera: `GET /projects` 200 y MEMBRESÍA del
      proyecto del smoke en la lista (DELETE de proyectos es C10).
  - Heredar también: `grep` intocables (como E3.24, lista ampliada si se
    desea) NO es obligatorio aquí — la verificación formal de no-regresión
    intocables es T4.10; E4.29 es la check ligera de runtime.
  - Cierre (mientras la pila no esté levantada o los endpoints no existan,
    se valida sintaxis): `bash -n scripts/qa_c4_smoke.sh` (parse OK) y
    shellcheck si está disponible; la validación funcional es T4.11.
  - Deps: T4.6 + T4.4 (endpoints existentes en la imagen que se reconstruye).
    Cubre: E4.15–E4.29.

---

## 5. Verificación de no-regresión y corrida canónica

- [x] **T4.10 — `mvn test` COMPLETO en el builder + diff-0 de intocables**.
  - Comando: `mvn test` DENTRO de la imagen builder
    `maven:3.9-eclipse-temurin-21` (backend + `~/.m2` montados, sin BD) —
    suite COMPLETA: C1 (validadores), C2 (auth), C3 (panel) y los 4 test
    nuevos de `com.projectlog.keys` + `ProjectsKeySqlTest` — TODO verde
    (R4.16, R4.15).
  - Complemento: `git diff --exit-code` sobre la lista de intocables de la
    sección "Notas transversales" (SecurityConfig, CorsConfig, entry point,
    V1__init.sql, application-test.yml, pom.xml, docs/**,
    frontend/public/mockups, …) → 0 en cada uno (R4.16c/S7/D4); y
    `ls backend/src/main/resources/db/migration/` == solo V1 (cero
    migraciones); y ausencia de `findByApiKeyHash` en `ProjectsRepository`
    (R4.5 — review, Decisión 5).
  - Cierre: salida `BUILD SUCCESS` del `mvn test` completo en builder +
    diffs 0. ESTE es el bloqueo para declarar la corrida canónica del smoke.
  - Deps: T4.2, T4.5, T4.7, T4.8 (todas las suites [L]). Cubre: R4.16 (con
    soporte de E4.29 en pila), R4.5, R4.14 (review de ausencia).

- [x] **T4.11 — Smoke-of-the-smoke + CORRIDA CANÓNICA `qa_c4_smoke.sh`**.
  - Entorno levantado por el PROPIO script (precondición E4.15):
    recomposición única con `--force-recreate`.
  - Pasos: (a) ejecutar `bash scripts/qa_c4_smoke.sh` en modo
    smoke-of-the-smoke (corrida de validación del script, patrón C3) —
    depurar falsos rojos de entorno (epoch, booleanos `t/f` vs `true/false`,
    ISO-8859-1 del 401, recrear-vs-restart) hasta consistencia; (b) corrida
    CANÓNICA final → E4.15–E4.29 TODOS PASS, exit 0; (c) pila 3×healthy al
    cierre y volumen con datos intacto (NUNCA `down -v`).
  - Cierre: veredicto canónico `exit 0` con 15/15 PASS (E4.15–E4.29) en el
    log de la corrida canónica.
  - Deps: T4.9 + T4.10. Cubre: E4.15–E4.29 (incl. 409 real E4.25 y los Done
    2/3/4 en pila).

---

## Trazabilidad tarea → escenarios

| Tarea | Escenarios que cubre (directa) |
|---|---|
| T4.1 (service crypto) | habilita E4.2, E4.3, E4.11, E4.12, E4.14 |
| T4.2 (ApiKeyServiceTest) | E4.3, E4.11, E4.12 |
| T4.3 (ApiKeyResponse) | habilita E4.1, E4.2, E4.4 |
| T4.4 (repo aditivo) | habilita E4.13; en pila E4.19–E4.22, E4.24, E4.26 |
| T4.5 (pin SQL) | E4.13 (+ frontera R4.16a) |
| T4.6 (controller) | habilita E4.1, E4.2, E4.4–E4.10; pila E4.18, E4.20, E4.21, E4.25, E4.26, E4.27 |
| T4.7 (MVC tests) | E4.1, E4.2, E4.4, E4.5, E4.6, E4.7, E4.8, E4.9, E4.10 |
| T4.8 (higiene ListAppender) | E4.14 |
| T4.9 (smoke script) | E4.15–E4.29 (codificación) |
| T4.10 (mvn test + intocables) | R4.16; R4.5, R4.14 (reviews de ausencia) |
| T4.11 (corrida canónica) | E4.15–E4.29 (veredicto definitivo) |

Cobertura de los 29 escenarios: E4.1–E4.2, E4.4–E4.10 → T4.7; E4.3, E4.11,
E4.12 → T4.2; E4.13 → T4.5; E4.14 → T4.8; E4.15–E4.29 → T4.9/T4.11.
Escenarios sin test dedicado (por diseño del spec): R4.5 y R4.14 → review de
ausencia en T4.10.

## Done = (proposal §5 + 409 de la Decisión 3 revisada)

| # | Criterio Done | Cubierto por |
|---|---|---|
| 1 | POST regenerate → 201 `{key, prefix, created_at}` (47 chars, regex, prefix=10, ISO-Z) + 404 estándar + test unitario primitiva (Decisión 1 reformulada) | T4.6+T4.7 (E4.1, E4.2, E4.4, E4.5), T4.2 (E4.3, E4.11, E4.12), T4.9/T4.11 (E4.18) |
| 2 | DELETE → 204 sin cuerpo idempotente, TRES a NULL juntos, ficha `has_key: false` / `api_key_prefix: null` sin cambios en C3 | T4.7 (E4.10, E4.7), T4.9/T4.11 (E4.21, E4.22, E4.23, E4.24, E4.26) |
| 3 | Regeneración: hash anterior ≠ nuevo en psql (invalidación al instante, sin ingesta) | T4.9/T4.11 (E4.20) |
| 4 | Persistencia SOLO por hash: `api_key_hash` BYTEA == sha256(key full UTF-8); key en claro en NINGÚN campo | T4.9/T4.11 (E4.19) |
| 5 | Test unitario `tiempoConstanteIgual` true/false (pareja correcta vs hash anterior) | T4.2 (E4.11, E4.12) |
| 6 | Higiene: ListAppender — ningún log de `keys` contiene `plk_`/la key | T4.8 (E4.14); higiene runtime del script (nota E4.15) |
| 7 | Tests MVC: 201, 204, 404, 401 sin JWT (cadena 2) | T4.7 (E4.1, E4.7–E4.10) |
| 8 | Smoke de pila (curl + psql) con patrones R-7 (force-recreate, builder mvn, epoch/booleanos, smoke-of-the-smoke, sufijos por ejecución) | T4.9 + T4.11 |
| 9 | NO pasa: ingesta/401-403 de C5, front (C8), migraciones, SecurityConfig, CORS, starter-validation, `findByApiKeyHash`, captura de DIVE | T4.10 (reviews de ausencia) + fronteras del spec §4 |
| +409 | POST regenerate sobre ARCHIVADO → 409 `project_archived` (Decisión 3 REVISADA; DELETE permitido sin rama) | [L] T4.7 (E4.6) + [S] T4.11 (E4.25); permitido DELETE en archivado: T4.7 (E4.7) + T4.11 (E4.26) |

Corte C4 TERMINADO cuando: T4.1–T4.9 marcadas `- [x]`, T4.10 con
`BUILD SUCCESS` + diffs-0, y T4.11 con corrida canónica `exit 0`
(15/15 PASS). Sin push a remoto; commits locales por fase.