---
change: C4
artifact: verify-report
last_updated: 2026-10-24
verdict: PASS-with-notes
status: draft
---

# Verify Report — C4 · API keys de ingesta

Verificación independiente de sdd-verify CONTRA `spec.md` (R4.1–R4.16,
S1–S7, E4.1–E4.29), `tasks.md` (T4.1–T4.11), `proposal.md` §5 (9 Done + 409)
y `qa-report.md`/`qa-log.txt` (complementos aplicados: QP-07/QP-26).
Evidencia propia ejecutada HOY 2026-10-24 sobre el árbol de trabajo (HEAD
a6298a8 + el código C4 sin commit): greps, `git diff` por fichero y lectura
directa de `ApiKeyController`, `ApiKeyService`, `ProjectsRepository` (diff),
`CorsConfig`, `SecurityConfig`, `ProjectsKeySqlTest`, `ApiKeyControllerMvcTest`,
`ApiKeyLogHygieneTest` y `scripts/qa_c4_smoke.sh`. El código C4 vive en el
árbol de trabajo SIN commitear (véase W-3 en Hallazgos).

## Completitud de tareas (tasks.md)

11/11 `- [x]`. Sin tareas incompletas.

## Checks A–G (tal como los pide el orquestador)

### A — diff-0 de intocables [O] QP-29 · PASS (cerrado por verify)

`git status --porcelain`: SOLO `ProjectsRepository.java` (M — cambio previsto
y auditado abajo) + ficheros NUEVOS de C4 (untracked: `keys/*.java`, 3 tests
de `keys/`, `ProjectsKeySqlTest`, `scripts/qa_c4_smoke.sh`, `.atl/sdd/C4/`).
`git diff --exit-code` por fichero, TODO clean (estado vs HEAD a6298a8):

| Fichero | Estado |
|---|---|
| `backend/src/main/java/com/projectlog/config/SecurityConfig.java` | CLEAN |
| `backend/src/main/java/com/projectlog/config/CorsConfig.java` | CLEAN |
| `backend/src/main/java/com/projectlog/config/RestAuthenticationEntryPoint.java` | CLEAN |
| `backend/src/main/java/com/projectlog/config/StartupSecretsValidator.java` | CLEAN |
| `backend/src/main/java/com/projectlog/auth/JwtAuthFilter.java` | CLEAN |
| `backend/src/main/java/com/projectlog/auth/LoginController.java` | CLEAN |
| `backend/src/main/resources/db/migration/V1__init.sql` | CLEAN |
| `backend/pom.xml` | CLEAN |
| `backend/src/main/resources/application.yml` | CLEAN |
| `backend/src/main/resources/application-test.yml` | CLEAN |
| `compose.yaml` | CLEAN |
| `docs/**` y `frontend/public/mockups/**` | CLEAN (0 entradas en status) |

`ls backend/src/main/resources/db/migration/` == solo `V1__init.sql` (cero
migraciones, R4.1).

Cambio en `ProjectsRepository.java` (el ÚNICO fichero tocado): 75 inserciones
+ 1 línea — la única línea NO aditiva es su propia entrada de Javadoc de
clase ("pieza compartida downstream…" ampliada para C4), PREVISTA por
R4.2/T4.4 ("Javadoc de clase AMPLIADO aditivamente"). NINGUNA línea de código
de C3 tocada: `SQL_UPDATE_PREFIX`/`conjuntoSet`/`SQL_UPDATE_SUFFIX` y los 4
métodos de C3 intactos; lo pinchan los tests `conjuntoSetSigueSinMencionarColumnasDeKey`
y `elUpdateDeC3SigueSiendoElLiteralDelCorteAnterior` de `ProjectsKeySqlTest`.

**QP-29 queda CERRADO**: el [O] "diff-0 intocables" que QA declaró suyo (§5.10
del qa-report) está ejecutado aquí con resultado 0 en todos los ficheros.

### B — CorsConfig sin X-API-Key (cierre S7) · PASS

`CorsConfig.java`, línea 47 (leído hoy, literal exacto):

```java
config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Confirm-*"));
```

SIN `X-API-Key`; `allowCredentials(false)`; origen único reflejado. La cadena
1 de `SecurityConfig` NO tiene `cors()`: líneas 66–68 —
`securityMatcher("/api/v1/ingest/**")` → `anyRequest().denyAll()` con el
comentario "// Sin cors() (TRD §5…)"; el `cors()` solo existe en la cadena 2
(`/api/v1/panel/**`, línea 81). Cierre S7 confirmado: cero entradas CORS en
C4; el header `X-API-Key` no entra en la superficie CORS (se introduce en C5;
C4 no lo toca — R4.14).

### C — ausencia de findByApiKeyHash (R4.5 / Decisión 5) · PASS

`grep -c "findByApiKeyHash" ProjectsRepository.java` → **0**; `grep -rn` en
todo `backend/src/` → 0 apariciones. Frontera C5 documentada SOLO en spec
§7 (firma `Optional<ProjectDto> findByApiKeyHash(byte[])` + uso en cadena 1);
nada construido, correcto.

### D — nota maqueta 05 (Decisión 4 / R4.16c) · PASS

`frontend/public/mockups/05-proyecto-detail.html`, línea 37, sigue conteniendo
la nota "(cuerpo estándar §3.1). SIN key (C4: sin regenerate/revocar), SIN
summary". Sin wiring: grep de `api-key|regenerate|revocar` en el fichero →
NADA fuera de esa nota. Cerrado C4, la nota SIGUE siendo verdadera (no hay
llamadas desde el front a los 2 endpoints de C4) — exactamente lo que fija
la Decisión 4. Fichero CLEAN contra HEAD.

### E — cobertura de Done (proposal §5, 9 criterios + 409) · PASS

| # | Criterio Done | Evidencia (ID de qa, todos PASS en qa-log) |
|---|---|---|
| 1 | POST regenerate → 201 `{key, prefix, created_at}` (regex 47, prefix=10, ISO-Z) + 404 + test primitiva (D-1 reformulado) | QP-01 (201 + orden exacto `containsExactly("key","prefix","created_at")`), QP-02 (regex `^plk_[A-Za-z0-9_-]{43}$` + prefix), QP-04 (regex ISO-Z), QP-03 (entropía 100/100, Done 1 parcial), QP-05 (404), QP-18/QP-19 pareja en pila; unit primitiva QP-11/QP-12 |
| 2 | DELETE → 204 sin cuerpo idempotente; TRES a NULL juntos; ficha `has_key:false`/`api_key_prefix:null` sin diff C3 | QP-10 (204 vacío en mock), QP-07 (204 + verify revokeKey(7L) — complemento apply APLICADO, línea 235 del MvcTest), QP-21/QP-22 (204 + tres NULL → t en psql), QP-23 (derivados), QP-24 (204 + tres NULL re-DELETE, updated_at no asertado — S1), QP-26 (204 sobre ARCHIVADO + epoch avanzó, sleep 2 — complemento apply APLICADO en script) |
| 3 | Hash anterior ≠ nuevo tras 2.º POST (invalidación al instante) | QP-20: `hash = sha256(k2)` → t Y `<> sha256(k1)` → t, antes del delete |
| 4 | Persistencia SOLO por hash: `api_key_hash` BYTEA == sha256(key UTF-8); key en claro en NINGÚN campo | QP-19: `api_key_hash = sha256('<key>'::bytea)` → t (aguja = key COMPLETA, TR-11 conforme), prefix == 10, plaintext check → `f` |
| 5 | Test unitario `tiempoConstanteIgual` true/false | QP-11 (pareja correcta true + vector fijo `sha256("abc")` == `ba7816bf…15ad`), QP-12 (hash anterior → false) |
| 6 | Higiene ListAppender — ningún log de `keys` con `plk_`/key | QP-14: `ApiKeyLogHygieneTest` (ListAppender, `Level.DEBUG` sobre los 3 loggers, `LITERALES_PROHIBIDOS` con `plk_`, key completa y 2 fragmentos, `noneMatch(contains)`) — 1/1 PASS |
| 7 | Tests MVC 201/204/404/401 sin JWT (cadena 2) | QP-01, QP-07/QP-10 (204), QP-05/QP-08 (404), QP-09 (401 `invalid_token` en ambas rutas + `verifyNoInteractions` repo/service); `SecurityConfig/JwtAuthFilter/entry point intocables` (check A) |
| 8 | Smoke de pila según guion D-1 con patrones R-7 | QP-15 a QP-29: 15/15 PASS, exit 0, corrida canónica con `--force-recreate` + smoke-of-the-smoke (precondiciones del script) + nombres sufijados (qa-log.txt) |
| 9 | NO pasa (fronteras): ingesta C5, front, migraciones, SecurityConfig, CORS, starter-validation, `findByApiKeyHash`, captura DIVE | Checks de review de ESTE verify: A (A/dirs/yaml/pom/compose/docs clean), C (`findByApiKeyHash` = 0), B (CorsConfig sin X-API-Key), F4 (sin `@ExceptionHandler` ni captura de `DuplicateKeyException`), pom sin starter-validation (check A), `V1` única migración |
| +409 | POST regenerate sobre ARCHIVADO → 409 `project_archived`; DELETE en ARCHIVADO permitido (Decisión 3 revisada) | [L] QP-06 (409, literal S3 exacto, sin `field`, `verifyNoInteractions(apiKeyService)` — S2) + [S] QP-25 (409 real en pila; psql: tres SIGUEN a NULL = el 409 NO escribió); DELETE permitido: QP-07 (mock, sin pre-SELECT `verify(never()).findById`) + QP-26 (psql real) |

Parejas obligatorias del plan (QP-18↔QP-19, QP-10↔QP-22, QP-06↔QP-25,
QP-05/08/27, QP-01/02/04↔QP-19): todas PRESENTES y PASS en qa-report §1.

Complementos QP-07 (Mockito.verify revokeKey(7L)) y QP-26 (epoch updated_at,
sleep 2): VERIFICADOS en código por este verify —
`ApiKeyControllerMvcTest.java:235` `Mockito.verify(projectsRepository).revokeKey(7L)`
y `qa_c4_smoke.sh` líneas 480 (`upd_e425`), 505 (`sleep 2`) y 515–519 (aserto
`upd > upd_ref` con FAILs explícitos). Ambos presentes y PASS en la corrida
canónica (qa-log.txt, 77/77 + 15/15).

### F — semántica contra el código real (spot-check) · PASS (7/7)

Lectura directa de `keys/ApiKeyController.java`, `keys/ApiKeyService.java`,
diff de `ProjectsRepository.java` y `ProjectsKeySqlTest.java`:

1. **Rama 409 ARCHIVED ANTES de generar (D-1/S2)** — en el código:
   línea 72 `findById(id)`, 73–75 404 si vacío, 76–79 409 si
   `"ARCHIVED".equals(status)`, LÍNEA 80 `apiKeyService.newKey()` — el 404
   antecede al 409 y el 409 antecede a la generación (no se gasta entropía;
   la key nunca nace sobre un archivado). Confirmado además por QP-06
   (`verifyNoInteractions(apiKeyService)`).
2. **Revoke SIN chequeo de status (Decisión 3 revisada)** — `revocar()`
   (líneas 98–103): directo `revokeKey(id)` → `true` → 204 /
   `false` → 404; cero lecturas de status (y QP-07: `verify(never()).findById`).
3. **sha256 del String COMPLETO UTF-8** — `ApiKeyService.sha256(String)`
   (líneas 67–75): `MessageDigest.getInstance("SHA-256")
   .digest(key.getBytes(StandardCharsets.UTF_8))`; sin trim de `plk_`, sin
   hex, sin salt; MessageDigest nuevo por llamada. Pin del contrato: vector
   fijo `sha256("abc")` (QP-11) y psql E4.19 (`sha256('<key>'::bytea) == hash`).
4. **Sin `@ExceptionHandler` / sin captura de DIVE (S6)** — grep en
   `com.projectlog.keys`: cero apariciones de `@ExceptionHandler`,
   `DuplicateKeyException`, `DataIntegrityViolation…` en código (solo en
   Javadoc que DOCUMENTA su ausencia). R-4 teórico → 500 honesto.
5. **SecureRandom estático** — `ApiKeyService` líneas 45–47:
   `private static final SecureRandom ALEATORIO = new SecureRandom();` (única
   instancia, thread-safe, una generación por llamada). Base64url unpadded
   de 32 B (línea 58) → 47 chars.
6. **Sin loggers en `keys`** — grep `Logger|log.|logger` en el paquete →
   solo coincidencias en package names/imports/Javadoc; CERO logback/slf4j.
   Reforzado por QP-14 (ListAppender PASS).
7. **Estamientos aditivos + UPDATE de C3 sin columnas de key (frontera
   R4.16a)** — diff muestra SOLO adiciones tras `conjuntoSet` (más la línea
   de Javadoc descrita en A); pin en `ProjectsKeySqlTest` (MISMO paquete,
   sin reflection — D-3): método `conjuntoSetSigueSinMencionarColumnasDeKey`
   (7 ramas × `doesNotContain` de `api_key_hash`/`api_key_prefix`/
   `key_created_at`) y `elUpdateDeC3SigueSiendoElLiteralDelCorteAnterior`
   (literal C3 exacto + `SQL_UPDATE_PREFIX.doesNotContain("api_key",
   "key_created_at")`). WHERE de revoke `id = ?` A SECAS (S1:
   `doesNotContain("IS NOT NULL")`).

Extra (S5/Divergencias D-2/D-3/D-4/D-5): `CODE_NOT_FOUND`/`MSG_NOT_FOUND` en
`ProjectsController` son `static final` package-private y `error()` private
(líneas 56/60 confirmadas) → duplicación justificada;
`EXPIRES_AT_FORMATTER` SÍ `public static final` en `LoginController` (línea
39) → reutilizada por referencia desde el repo (cierre S4: formateo EN el
repo, `Optional<String>` ya formateado); 204 vía
`ResponseEntity.status(HttpStatus.NO_CONTENT).build()` (D-2, línea 101);
helper `error` private de 3 parámetros + `LinkedHashMap` (D-4/D-5). Ninguna
divergencia ilegítima: las 6 preservadas (D-1..D-6) están en el código tal
como los tasks las declaran.

### G — QA como evidencia (sin manipulación) · PASS

Cero ediciones a ficheros QA por este verify. Confirmado por lectura de
`.atl/sdd/C4/qa-log.txt`: bloque "=== VEREDICTOS QA C4 (por ID del spec) ==="
con **E4.15–E4.29 = 15 líneas, todas PASS**; bloque aparte "RESULTADO APARTE:
suite [L]" con `Tests run: 77, Failures: 0, Errors: 0, Skipped: 0` +
`BUILD SUCCESS` (mvn en builder, re-corrida 2026-10-24T21:34:53Z tras el
loop-back de apply de QP-07/QP-26). `qa-report.md`: `results: 30/30 pass`
(29 E4.x + QP-30 N-A opcional), commit a6298a8, complementos QP-07/QP-26
documentados en §4. Consistente: 9 (MvcTest) + 1 (Hygiene) + 3 (ServiceTest)
+ 4 (KeySqlTest) = 17 tests C4 de los 77 totales.

## Matriz escenarios → evidencia (resumen)

E4.1–E4.14 (14 [L]): todos PASS vía mvn 77/77 (qa-log) con los métodos
nombrados del qa-report §4. E4.15–E4.29 (15 [S]): todos PASS vía smoke
canónico exit 0 (qa-log). 29/29 escenarios COMPLIANT con test corrido;
QP-30 N-A (opcional, véase SUGGESTION S-1). R4.5 y R4.14 → review por
ausencia, cerrados aquí (checks C y B). R4.16 → checks A + E4.29 + suite
integral verde.

## Hallazgos

### CRITICAL

Ninguno.

### WARNING

- W-1 — Desviaciones PRESERVADAS documentadas (D-1..D-6 de tasks.md "Notas
  transversales"), NO defectos: el 204 por `status(NO_CONTENT).build()` (D-2,
  el snippet de R4.7 no compila contra `ResponseEntity<Object>`), constantes
  SQL package-private con el pin en el mismo paquete (D-3), chequeo ARCHIVED
  por `"ARCHIVED".equals(...)` sin `WHERE status <> 'ARCHIVED'` (D-1), helper
  `error()` de 3 parámetros sin
  varargs (D-4), `LinkedHashMap` ordenado (D-5), sin Javadoc extra de C5 (D-6).
  Verificadas en el código: conformes a los tasks; quedará anotado en
  archive que D-2/D-3 divergen del texto literal de R4.7/R4.3 por necesidad
  técnica.
- W-2 — `[O]` no ejecutados en QA, cerrados o quedados como notas: QP-29
  CERRADO por este verify (cheque A, PASS); QP-01 Content-Type (el 201 del
  MOCK no aserta `content().contentType(APPLICATION_JSON)` — solo lo hacen
  los 404/409 vía helper y el smoke real via curl); QP-10 Content-Length 0
  (aserta cuerpo vacío, no el header); QP-19 cross-check `created_at`↔`key_created_at`
  (el formato ISO-Z va E4.4/E4.18 y el valor psql vía RETURNING — el
  cross-check exacto de igualdad entre ambas superficies no está asertado).
  Ninguno bloquea: son observaciones [O] del qa-plan, deliberadas y notadas.
- W-3 — El código C4 está SIN commitear (working tree vs a6298a8: 1 fichero
  modificado + los NUEVOS untracked). Consistente con "commits locales por
  fase (patrón C3 de la sesión)" y con
  las 26+ commits sin push heredadas de la pausa, pero sdd-archive
  DEBE crear los commits de C4 (código + tests + smoke + .atl)
  antes de cerrar; la sesión del usuario decidió mantener la pila apagada y
  sin push.

### SUGGESTION

- S-1 — Hueco G-1/QP-30 (rama (6) de R4.8): no hay método en
  `ApiKeyControllerMvcTest` que mockee `findById`→ACTIVE +
  `regenerateKey`→`Optional.empty()`→404 (segunda barrera del
  empty-RETURNING). QP-30 N-A: el spec no le asigna E4.x. Ruta difícil de
  alcanzar hoy en test (el id fue leído con findById exitoso justo antes);
  si C5 lo considera relevante por carreras, plantear ahí el test de la rama (6).
- S-2 — Hueco G-2 (semántica R4.12 "hash persiste al desarchivar"): sin
  escenario dedicado; E4.22/E4.25/E4.26 cubren que archivar NO toca la key y
  el 409/DELETE en archivado, pero el ciclo completo "key ACTIVA + archivar +
  desarchivar = misma key aceptada" no se corre en el smoke (opcional para C5).
- S-3 — LIKE-underscore (para cortes futuros): en el smoke C4 solo hay UN
  LIKE (E4.19 c, línea 368 de `qa_c4_smoke.sh`, sobre name/description con
  aguja = la key completa). Como el charset `plk_*` PUEDE contener `_`, un
  `_` en la aguja actúa como comodín de un carácter en LIKE — el patrón es
  LIGERAMENTE más permiso de lo literal. En este caso NO produce falso
  PASS/FAIL relevante: el check exige resultado "f" (sin plaintext), y el
  comodín solo haría el patrón más fácil de matchear, es decir, más
  conservador en la dirección correcta; el riesgo real del comodín aparecerá
  en futuras queries de smoke/proyectos que usen needles literales con `_`
  (p.ej. nombres de proyecto sufijados) para aserciones de pertenencia —
  anotarlo para C5+. La membresía de
  E4.29 usa parseo JSON (`lista_contiene`), no LIKE — sin riesgo.

## Veredicto

**PASS with notes** — el corte C4 está completo y conforme al spec: 11/11
tasks, 29/29 escenarios con test corrido y PASS (77/77 mvn + 15/15 smoke,
exit 0 ambos), los 9 criterios Done de la proposal §5 + el 409 cubiertos con
evidencia, los 7 cierres S1–S7 verificados contra el código real, la frontera
C5/R4.5/R4.14/documentación de §7 respetada y el [O] QP-29 (diff-0 de
intocables) cerrado por verify con 0 en TODOS los ficheros. Ningún CRITICAL.
Los warnings son desviaciones preservadas documentadas (D-1..D-6, [O]
restantes) y el estado sin commitear del corte (para archive). Los 3
suggestions (G-1, G-2, LIKE-underscore) son huecos menores documentados que
no bloquean.