---
change: C5
artifact: verify-report
last_updated: 2026-10-25
verdict: PASS-with-notes
status: draft
---

# Verify Report — C5 · Ingesta REST

Verificación independiente de sdd-verify CONTRA `spec.md` (R5.1–R5.24+S5-x,
D-C5-1, E5.1–E5.34), `tasks.md` (T5.1–T5.20), `proposal.md` §6 (10 Done + los
6 puntos del gate) y `qa-report.md`/`qa-log.txt` (2.ª corrida post-remediación).
Evidencia propia ejecutada HOY 2026-10-25 sobre el árbol de trabajo (HEAD
6cc609f + el código C5 sin commit): `git status --porcelain`, `git diff` por
fichero, greps de ausencias/higiene, y lectura directa de los 9 ficheros
nuevos de `ingest`, el diff de los 3 ficheros tocados, los 6 tests nuevos y
`scripts/qa_c5_smoke.sh` completo, más `qa-log.txt` y los RAW de /tmp.

## Completitud de tareas (tasks.md)

20/20 `- [x]` (T5.1–T5.20, con las notas "hecho" de T5.5/T5.18/T5.19/T5.20
citando sus logs: `/tmp/mvn_c5_full5.log`, `/tmp/qa_c5_run4.log`,
`/tmp/qa_c5_run5.log`). Sin tareas incompletas.

## Checks A–G

### A — diff-0 de intocables · PASS

`git status --porcelain`: SOLO los 3 ficheros modificados QUE EL CORTE
PERMITE (`SecurityConfig`, `ProjectsRepository`, `application.yml`) +
untracked: `.atl/sdd/C5/`, los 9 ficheros de `ingest/` main, 6 tests NUEVOS
(`ingest/` ×5 + `ProjectsIngestSqlTest`) y `scripts/qa_c5_smoke.sh`.
TODO lo demás CLEAN, verificado con `git diff --exit-code` (exit 0 en cada
conjunto):

| Fichero | Estado |
|---|---|
| `config/JwtAuthFilter.java` | CLEAN |
| `config/RestAuthenticationEntryPoint.java` (panel, `invalid_token` CD-4) | CLEAN |
| `config/CorsConfig.java` | CLEAN |
| `config/StartupSecretsValidator.java` | CLEAN |
| `config/WebSocketConfig.java` | CLEAN |
| `backend/pom.xml` | CLEAN |
| `application-test.yml` | CLEAN |
| `.env.example`, `compose.yaml`, `V1__init.sql` (única migración) | CLEAN |
| `docs/**`, `frontend/**`, `auth/**`, `keys/**`, `common/**`, `streaming/**` | CLEAN (0 diffs) |
| `ingest/package-info.java` (rol marcador R5.1) | CLEAN |
| `backend/src/test/**` (TODO test de C1–C4, incl. `PanelAuthMvcTest` y asserts heredados) | CLEAN — `git diff --stat HEAD -- backend/src/test/` VACÍO: cero tests tocados |
| `scripts/qa_c3_smoke.sh`, `scripts/qa_c4_smoke.sh` | CLEAN |

**SecurityConfig** (lo ÚNICO modificado de la config): `git diff` muestra
EXACTAMENTE 2 hunks — (h1) imports (2 de C5) + el ítem "Cadena 1" del Javadoc
de clase (los ítems "Cadena 2 (S3)" y "Cadena 3" quedan SIN diff); (h2) el
Javadoc del bean `ingestChain` + su cuerpo + el bean-higiene `filtroGuardSoloCadena1`
(D-13). El **constructor es byte-a-byte** (el hunk 2 abre en contexto
`this.entryPoint = entryPoint;` — ninguna línea de él ni de los 2 campos
entra en los hunks), y **`panelChain`/`defaultChain`/`common` quedan FUERA de
CUALQUIER hunk** (el diff termina en el contexto `/** Cadena 2: panel JWT —
login público exacto… */ @Bean @Order(2)` — sin una línea cambiada).
Cita del cambio permitido: `.addFilterBefore(...)` + `.anyRequest().authenticated()`
+ `.authenticationEntryPoint((request, response, authException) ->
entryPointIngesta.getObject().commence(...))` + `.accessDeniedHandler((...)
-> ...getObject().handle(...))` y el comentario conservado `// Sin cors()
(TRD §5: los clientes de ingesta son servicios).` — el cuerpo DIFIERE del
bloque verbatim de R5.6 por la RATIFICACIÓN de gate D-14 (ObjectProvider
delegante lazy, ver W-1/W-2).

**ProjectsRepository**: diff PURAMENTE ADITIVO (30 inserciones / 0 borrados):
+1 constant `SELECT_POR_API_KEY_HASH` (package-private, Javadoc que cita CD-3.2,
D-C5-1 y D-9) + 1 método `findByApiKeyHash(byte[] hash)` con el Javadoc
EXACTO del R5.11-a (cita del ancla C4 spec §7 y de la D-C5-1 verbatim) +
`@Transactional` de defensa (R5.11-a). Ningún estamento/método de C3/C4
tocado (los pins `ProjectsKeySqlTest` siguen en su paquete CLEAN).

**application.yml**: `git diff` = (1) corrección del comentario de cabecera
"sin límites de ingesta (C5)" (R5.3.3 — solo comentario, PREVISTA); (2)
`ingest.max-request-bytes: ${INGEST_MAX_REQUEST_BYTES:1MiB}` +
`max-batch-size: ${INGEST_MAX_BATCH_SIZE:200}` (R5.14, defaults EXACTOS TRD
§6.3 — `.env.example` INTOCABLE, lo absorbe el normalizador D-6/S5-9). El
test.yml queda CLEAN (los [L] corren con defaults); E5.34 PINA el default
literal `"1MiB"` vía reflection del constructor (test `elDefaultDelValueEsExactamente1MiB`).

### B — CorsConfig sin X-API-Key y cadena 1 sin cors() · PASS

`grep -n "X-API-Key" config/CorsConfig.java` → 0 coincidencias (cierre S7 de
C4 mantenido). En `SecurityConfig.ingestChain` NO existe llamada `cors()`
(solo el comentario TRD §5 conservado). El smoke lo corrobora: la superficie
de ingesta responde tal cual sin preflight (E5.21–E5.33 todos sin CORS).

### C — Ausencias · PASS

- `IngestBatchRequest`/`IngestAcceptedResponse`: NO creados (find = 0) —
  patrón JsonNode D-spec (cierre S5-15).
- `pom.xml`: sin `testcontainers`, sin `spring-boot-starter-validation`, sin
  `commons-io` (grep = 0; pom CLEAN).
- V1__init.sql: sin diff (cero migraciones — `db/migration/` solo V1).
- Loggers: cero logger en `main/java/com/projectlog/ingest/` (grep
  LoggerFactory/Logger = 0; R5.15) — higiene además probada E5.17
  (`IngestLogHygieneTest`, ListAppender DEBUG).
- Codes: EXACTAMENTE los 11 literales de R5.10 (grep CODE_*/MSG_* por clase:
  401 `api_key_invalid` en el entry point — package-private para el
  401-honesto D-2; 413 en el filtro; `project_archived`/`batch_empty`/
  `batch_too_large` en el controller; los 6 por campo + límites
  32768/131072/65536/200 en el service — Decisión T5-1/D-7). NINGÚN code
  fuera del enum §3.4/§4.1; `field_too_long` REUTILIZADO (R5.19g). Sin 429,
  sin 404 en ingesta, sin generación server-side de `correlation_id`.
- Sin `toLowerCase`/`toUpperCase` en ingest (enums case-sensitive, sin
  normalización).
- Sin captura de `DataIntegrityViolationException`/`DuplicateKeyException`
  en ingest (500 honesto R5.12-b; solo mención de AUSENCIA en Javadocs).
- G-1 NO reabierta: `regenerateKey`/rama empty-RETURNING sin diff
  (ProjectsRepository aditivo puro; R5.16).
- Tests C1–C4 TOCADO-NULO: estado listado en el bloque A (tabla) —
  `PanelAuthMvcTest` y TODOS los asserts heredados sin diff.

### D — Semántica contra el código real (spot-check) · PASS (9/9)

1. **Orden 413→401→403→400**: (413) `IngestGuardFilter.doFilterInternal` —
   barrera 1 `getContentLengthLong() >= 0 && > limite` → `escribir413`+return
   (L136–141) y barrera 2 wrapper `StreamContado` con frontera EXACTA
   `limite` legal / `limite+1` → `CuerpoDemasiadoGrandeException` (L145–157,
   L280–295) + catches del propio guard (L194–216); (401) entry point
   (`escribir401`, ambos papeles) tras `authenticated()` fallido; (403)
   `IngestController.procesar` gate 3 `"ARCHIVED".equals(principal.status())`
   ANTES de la validación (L105–108; E5.13 lo pincha con cuerpo inválido →
   403); (400) service (`registrar`→`validarYMaterializar`) → materializado
   por el controller (L132–134). Fail-fast: nada toca la BD antes del INSERT.
2. **sha256 EN EL FILTRO (D-1), cero JWT**: `apiKeyServiceProvider.getObject()
   .sha256(header)` (String COMPLETO, L163–167) →
   `projectsRepository.findByApiKeyHash(hash)` → `IngestPrincipal(id, status)`
   en el contexto (L170–173). El filter Javadoc y el Javadoc del repo CITAN
   la D-C5-1 (R5.16 ✓). Cero JWT en ingesta: el paquete `ingest` no importa
   `JwtAuthFilter`/`TokenService`; E5.16 [L] (Bearer → 401 `api_key_invalid`,
   `doesNotContain("invalid_token")`) + trío-NULL de E5.24c [S] lo cierran.
3. **received_at UNA VEZ tras validación completa (S5-7)**: `IngestService`
   L106–117 — el loop de validación `validarYMaterializar` corre ANTES (los
   retornos de error salen SIN calcular received_at) y
   `OffsetDateTime.now(ZoneOffset.UTC)` es UNA llamada común pasada como
   parámetro al repo (E5.19: captor, 1 invocación, mismo valor al seam; E5.22
   [S]: `count(DISTINCT received_at)=1` sobre los 200 ids).
4. **INSERT multi-VALUES + RETURNING id + única transacción del insert (D-4)**:
   `LogsRepository` — 3 constants EXACTAS (L25–33; `CAST(? AS jsonb)` 8.ª
   posición, `RETURNING id`), StringBuilder N filas separadas por `", "`,
   varargs en el ORDEN EXACTO R5.11-d (L64–76, con `metadataJson` a la 8.ª
   posición), extractor `(rs, n) -> rs.getLong("id")`, `@Transactional` en
   `insert` (L50). El service NO es transaccional; `findByApiKeyHash` lleva
   su PROPIO `@Transactional` de defensa mono-sentencia PREVISTO por
   R5.11-a ("patrón R3.2") — la "única" de D-4/R5.12 es el SCOPE del lote:
   ninguna anotación envuelve al seam (ver N-4).
5. **Validación COMPLETA pre-transacción + all-or-nothing (regla 6)**: el
   loop valida TODOS los registros antes de `receivedAt`/`insert`
   (`IngestService.registrar` L106–114); al primer fallo `return
   Optional.of(...)` sin tocar la BD — E5.23 [S] (delta=0) y E5.11/[L]
   `batchRegistroIntermedioLlevaIndexYFieldPrimerFalloGana` (index:2,
   `verify never insert`, y en individual `index` JAMÁS) lo cierran en ambos
   sentidos; el pin `LogsRepoSqlTest` 4/4 y `ProjectsIngestSqlTest` 3/3.
6. **202 tras COMMIT + seam tras el RETORNO del insert (S5-10/D-12)**: la
   llamada al seam (L126) es la sentencia SIGUIENTE a `logsRepository.insert`
   (L121) en un método NO transaccional — COMMIT estructural; firma EXACTA
   `void onLotePersistido(long projectId, OffsetDateTime receivedAt,
   List<Long> ids)` en `IngestListener` (método único; sin
   TransactionSynchronizationManager ni @TransactionalEventListener); lista
   inyectada vacía (`List<IngestListener> oyentes`, E5.20 a/b/c: ids en orden
   de VALUES, individual = 1, jamás ante 400/rollback); 202 = `Map.of("accepted", N)`
   con `valueToTree` (R5.10-a, sin espacios, E5.1 `{"accepted":1}` exacto).
7. **Bind D-6 con normalizador + E5.34**: `limiteBytes` package-private
   (quita la `i` de `…iB` → `DataSize.parse().toBytes()`, fail-fast en el
   constructor UNA vez, L102/L116–122); `IngestGuardLimitTest` 3 tests:
   `1MiB`→1048576, `1KiB`/`1KB`→1024, puros, `1MB`→1048576, `abc`→excepción,
   y el default del `@Value` === `"${projectlog.ingest.max-request-bytes:1MiB}"`.
8. **shouldNotFilter D-8**: `uri.equals("/api/v1/ingest") ||
   startsWith("/api/v1/ingest/")` (L126–129) + el bean-higiene D-13
   (`FilterRegistrationBean...setEnabled(false)`) que deja al guard SOLO en
   la cadena 1 (el hallazgo E5.28 real: la passe global consumía el marker de
   OncePerRequestFilter).
9. **Unwrap D-15**: 3 catches (ServletException/IOException/RuntimeException)
   + `contieneBarrera2` (cadena de causas ≤8 niveles, sin reflection) con
   rethrow INTACTO de lo demás y `!response.isCommitted()`; el TIPO de D-5 se
   MANTIENE (`extends RuntimeException`, clase anidada estática).

### E — Trazado de Done de proposal §6 · PASS (10/10)

| Done | Evidencia (verificada por mí) |
|---|---|
| 1 (individual 202 + fila completa) | E5.1 [L] (`content().string("{\"accepted\":1}")` + `verify(insert(eq(7L),…))`) + E5.21/E5.29/E5.30 [S] PASS |
| 2 (batch 200, received_at común, ids en orden) | E5.2 [L] + E5.22 [S] (delta=200, DISTINCT=1, ROW_NUMBER) |
| 3 (all-or-nothing con index/field) | E5.11 [L] + E5.23 [S] (400 index:2 + delta=0) |
| 4 (5 errores vs pila + orden fail-fast) | E5.24/E5.25/E5.26/E5.27 + E5.33 [S] (413 ANTES de 401 con key-basura, y ≤1 MiB → 401) |
| 5 (G-2 misma key) | E5.31 [S]: orden cronológico del script coloca `case_e5_31` INMEDIATAMENTE tras E5.25, `PATCH ACTIVE` + `curl_ingest "$F_KEY2"` (la MISMA key2, sin regenerate intermedio) → 202 — corroborado en qa-log: `E5.31: PASS` |
| 6 (fronteras batch + 413 + chunked) | E5.10/E5.32/E5.27/E5.28 ([L]+[S]) |
| 7 (validación con fronteras de BYTES) | E5.5–E5.9 [L] (multibyte 32768: `ñ`.repeat(16384)→202; +1→400 con messages compuestos) + E5.30 [S] |
| 8 (suite [L]: literales, 401/403/400, pins, byte-a-byte, higiene, unit) | 31 tests C5 verdes (16 MVC + 3 guard + 1 higiene + 4 service + 4 LogsRepo + 3 ProjectsIngest) + 77/77 heredados = mvn 108/0/0 (qa-log §B) |
| 9 (seam UNA vez tras COMMIT, no-op lista vacía) | E5.20 [L] (a/b/c1/c2) + T5.11 |
| 10 (NO pasa — fronteras §3) | T5.19: diffs-0 de §5 + ausencias de §C de este reporte |

Remediación QA constatada en código: (a) método NUEVO
`camposDeConfianzaFalsificadosPorElClienteIgnorados` en
`IngestServiceTest` (QP-EX-1, hueco S5-2 CERRADO: 8 record-components EXACTOS
via reflection, JSON con `id`/`project_id`/`received_at` y camelCase
falsificados ignorados, captor con `projectId=7` jamás 999 y received_at
server-side jamás el 1999, fila == `filaDe(leido)`); (b) aserto NUEVO de
QP-12 en `IngestControllerMvcTest` L153–155:
`Mockito.verify(apiKeyService).sha256(KEY_FIXTURE)`; (c) asertos NUEVOS de
QP-7/QP-9: messages compuestos EXACTOS en las 5 fronteras 400 (L349–434:
"…32768 bytes"/"…131072 bytes"/"…65536 bytes"/"…200 caracteres" ×2).

### F — QA como evidencia · PASS (sin manipulación)

`qa-report.md` (2.ª emisión post-remediación) y `qa-log.txt` VIGENTES y
coherentes: mvn **108/0/0 BUILD SUCCESS, EXIT=0** (§B verbatim), smoke
**13/13 PASS (E5.21–E5.33), SMOKE_EXIT=0** tras recomposición ÚNICA
`--build --force-recreate` (§A verbatim, sin `down -v`), higiene REMEDIADO
constatado en §C: los dumps `/tmp/qa_c5_req_debug.log` y
`/tmp/qa_c5_curl_err.log` NO EXISTEN tras la corrida (ls verbatim), 0 keys de
smoke vivas al final (8/8 proyectos `smoke-c5-*` con trío NULL tras la
revocación de la key viva del ciclo id=50, verificado con RETURNING id=50 +
SELECT posterior), pila 3×healthy. Mi sweep propio de los RAW (/tmp) corrobora.

### G — Higiene de secretos · PASS

- `grep -cE 'plk_|eyJ' .atl/sdd/C5/qa-log.txt` → **1** = el comentario meta de
  la propia cabecera ("0 coincidencias plk_/eyJ…") — NINGÚN secreto real.
- Evidencia RAW de la 2.ª corrida: `/tmp/qa_c5_mvn_test_post.txt` → 0;
  `/tmp/qa_c5_canonico_post.txt` → 0 (grep -c propio de verify).
- Sweep adicional: `/tmp/qa_c5_canonico_qa.txt`, `/tmp/qa_c5_mvn_test.txt` y
  `qa_c5_run1..6.log` → 0/0/0/0/0/0.
- El script mantiene la higiene estructural: keys/JWT SOLO en tmpdir 700 con
  trap; key a psql via `psql_stdin` ( jamás argv); `hdr_directo.txt` 600 en
  tmpdir; salida pública `E5.n: PASS…` sin material.

## Hallazgos

**CRITICAL: 0** (ningún FAIL de checks A–G; nada rompe un Done o un literal).

**WARNING (desviaciones DOCUMENTADAS y RATIFICADAS en gate 2026-10-25 —
citeras, sin cambio de contrato):**

- **W-1 (D-14)** — `ingestChain` difiere del bloque verbatim de R5.6: los 2
  beans entran como `ObjectProvider` con lambdas delegantes resueltas lazy
  (los slices @WebMvcTest de C1–C4 escanean `Filter` pero no
  `AuthenticationEntryPoint`). La SEMÁNTICA del R5.6 se conserva (mismo
  matcher, sin cors(), mismo ancla `UsernamePasswordAuthenticationFilter.class`,
  `authenticated()`, entry point propio en ambos papeles); ratificada en gate
  (T5.5/D-14) y PINADA por `elDefaultDelValueEsExactamente1MiB` (reflection
  del ctor). 
- **W-2 (D-14)** — el ctor de `IngestGuardFilter` usa
  `ObjectProvider<ApiKeyService>`/`<ProjectsRepository>` resueltos lazy en
  `doFilterInternal` (L165–167) en vez de las dependencias directas del R5.4:
  mismo trade-off, mismo gate.
- **W-3 (D-13)** — `filtroGuardSoloCadena1` (`FilterRegistrationBean…setEnabled(false)`)
  está FUERA del alcance literal «diff solo ingestChain + Javadocs» del
  R5.3.1: ratificada en gate tras el hallazgo E2E E5.28 (doble registración
  rompía la barrera 2). Es la razón por la que este verdicto lleva notes.
- **W-4** — El código C5 vive SIN COMMIT en el árbol de trabajo (26 commits
  previos sin push; C5 pendiente de commit por sdd-apply/archive) — mismo
  estado que C4 en su verify (allí W-3); el `git diff` de este corte está
  auditado hunk a hunk.
- **W-5** — `MSG_API_KEY_INVALID` y `SELECT_POR_API_KEY_HASH` son
  **package-private** donde R5.10/R5.11 decían `private`: es la ÚNICA forma
  de que el pin D-9 (`ProjectsIngestSqlTest`) y el 401-honesto D-2 existan sin
  reflection ni visibilidad `public`; el propio spec los refirió así en
  D-9/S5-11 ("package-private") — inconsistencia tipográfica de los
  literales de R5.10/R5.11, resuelta por design y documentada in-situ.
- **N-4** — "Única @Transactional del corte": existen 2 en el diff C5
  (`LogsRepository.insert` + `findByApiKeyHash` con su `@Transactional` de
  defensa PREVISTO por R5.11-a verbatim). NINGUNA de las 2 envuelve al seam
  ni al service: el punto de fondo de D-4 (seam tras-COMMIT ESTRUCTURAL) se
  cumple exactamente; la redacción "única" de R5.12/D-4 se refiere al scope
  del lote. Nota de matiz, sin acción.

**SUGGESTION (deudas/notas para archive y futuros cortes):**

- **S-1** — nginx `client_max_body_size` NO definido en el frontend (default
  1 MiB): los 3 cuerpos >1 MiB del smoke (E5.27/E5.28/E5.33) viajan por ruta
  directa al contenedor backend (desviación E2E documentada del script) —
  deuda C9; documentada en qa-report (v).
- **S-2 (D-14)** — `ObjectProvider` es degradación HONESTA: si en un futuro
  corte los beans faltaran en un slice, la resolución lazy evita el estallido
  a build-time — patrón a recordar si C6/C7 añaden beans de seguridad a la
  cadena 1.
- **S-3** — Ciclo de keys del smoke: cada corrida deja UNA key viva (la del
  `--force-recreate` del ciclo propio) y la política es revocarla después
  (hecho post-corrida con trío NULL verificado, qa-log §C) — patrón a recordar
  en C6/C7: incluir la revocación final EN el script para correrla 0-manual.
- **S-4** — S-3 heredada de C4 (needles psql `plk_` con `\_`/`position()`):
  vigente como convención, no deuda de código.
- **S-5** — ISO-401 (deuda C8/C9): el cuerpo del 401 de INGESTA es ASCII-safe
  salvo el `message` con acentos (los needles [S] usan `api_key_invalid`,
  seguro); el 403 se lee UTF-8-from-bytes en [L]. Recordar al archive que la
  deuda de encoding del 401 [heredada de C2/C4] sigue abierta para C8/C9
  (no aplicable a la superficie de ingesta de C5).
- **S-6** — Typo cosmético en `IngestGuardFilter` L82 ("ObjecProvider") en un
  comentario — sin efecto; corregible en el commit del archive.
- **S-7 (QP-EX-2/QP-EX-3 [O])** — sin cobertura (aceptada como plan): no hay
  test de `shouldNotFilter` fuera de `/api/v1/ingest/**` (su efecto E2E quedó
  demostrado por el E5.28-remediado + la suite heredada 77/77, que ejercita
  rutas ajenas al guard) ni de opcionales `""` capturados como `""`.

## Veredicto

**PASS-with-notes** — Checks A–G 7/7 PASS, 34/34 escenarios con dueño y
evidencia (21 [L] verdes en surefire 108/0/0 + 13 [S] PASS exit 0), higiene
REMEDIADA constatada, 0 keys de smoke vivas, intocables CLEAN, cero codes
nuevos, all-or-nothing y tras-COMMIT ESTRUCTURALES verificados en código. Las
notes W-1..W-5/N-4 son las RATIFICACIONES de gate D-13/D-14/D-15 + el estado
sin-commit: NINGUNA desviación de contrato SIN documentar.