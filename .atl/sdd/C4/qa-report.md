---
change: C4
artifact: qa-report
last_updated: 2026-10-24
results: "30/30 pass (QP-30 N-A: opcional fuera-de-spec, hueco G-1 documentado)"
commit: a6298a85b80294bff5138a932ddb6aebecee720a
log: qa-log.txt
---

# QA Report — C4 · API keys de ingesta

Evidencia RAW vigente: `.atl/sdd/C4/qa-log.txt` (salida verbatim de la
corrida canónica de re-corrida 2026-10-24 TRAS el loop-back de apply de los
complementos QP-07/QP-26 + resultado etiquetado aparte de `mvn test`).

## 1. Veredicto por ID (QP-01..QP-30)

| ID | Tipo | Veredicto | Pareja obligatoria / nota |
|---|---|---|---|
| QP-01 (E4.1) | [L] | PASS | pareja con QP-19 ✓ (ambos PASS) |
| QP-02 (E4.2) | [L] | PASS | |
| QP-03 (E4.3) | [L] | PASS | |
| QP-04 (E4.4) | [L] | PASS | |
| QP-05 (E4.5) | [L] | PASS | trío 404 con QP-08/QP-27 ✓ |
| QP-06 (E4.6) | [L] | PASS | pareja 409 con QP-25 ✓ |
| QP-07 (E4.7) | [L] | PASS | pareja 204↔E4.22 ✓; complemento apply verificado en re-corrida (verify revokeKey(7L)) |
| QP-08 (E4.8) | [L] | PASS | |
| QP-09 (E4.9) | [L] | PASS | |
| QP-10 (E4.10) | [L] | PASS | pareja 204↔E4.22 ✓ (ambos PASS) |
| QP-11 (E4.11) | [L] | PASS | vector fijo sha256("abc") presente |
| QP-12 (E4.12) | [L] | PASS | |
| QP-13 (E4.13) | [L] | PASS | pin SQL verde — **citado explícito (criterio 5.3c)** |
| QP-14 (E4.14) | [L] | PASS | higiene verde — **citado explícito (criterio 5.3c)** |
| QP-15 (E4.15) | [S] | PASS | recomposición única `--force-recreate` + 3×healthy podman + /me 401 `invalid_token` |
| QP-16 (E4.16) | [S] | PASS | claves `expires_at,token,username`; JWT solo tmpdir |
| QP-17 (E4.17) | [S] | PASS | captura de id incondicional |
| QP-18 (E4.18) | [S] | PASS | **pareja obligatoria con QP-19: PASS/PASS** |
| QP-19 (E4.19) | [S] | PASS | `sha256(key_1::bytea)=api_key_hash` → t; TR-11 respetado (aguja = key COMPLETA) |
| QP-20 (E4.20) | [S] | PASS | hash = sha256(k2) t Y <> sha256(k1) t, ANTES del delete (TR-10) |
| QP-21 (E4.21) | [S] | PASS | |
| QP-22 (E4.22) | [S] | PASS | tres NULL juntos, boolean conjunto → t |
| QP-23 (E4.23) | [S] | PASS | `has_key: false`, `api_key_prefix: null` (campo_json_nul) |
| QP-24 (E4.24) | [S] | PASS | updated_at NO asertado (respeta D-R5/TR-8) |
| QP-25 (E4.25) | [S] | PASS | **pareja 409 con QP-06: PASS/PASS**; el 409 NO escribió (tres NULL → t) |
| QP-26 (E4.26) | [S] | PASS | pareja con QP-07 ✓; complemento apply verificado en re-corrida: E4.26 PASS incluye el aserto [M] updated_at avanzó (epoch > ref de E4.25) |
| QP-27 (E4.27) | [S] | PASS | |
| QP-28 (E4.28) | [S] | PASS | 401 en ambas rutas, grep tolerante `invalid_token` |
| QP-29 (E4.29) | [S] | PASS | [O] diff-0 intocables NO ejecutado aquí → obligación de sdd-verify (§5) |
| QP-30 | [L] | N-A | OPCIONAL sin cobertura: hueco G-1 sigue abierto (rama (6) de R4.8, empty-RETURNING→404, sin método en ApiKeyControllerMvcTest) |

Parejas obligatorias (plan §2.1): todas PRESENTES y en PASS —
QP-18↔QP-19, QP-10(mock)↔QP-22(psql), QP-06(mock)↔QP-25(psql),
QP-05/QP-08/QP-27 mutuamente cubiertos, QP-01/02/04(mock)↔QP-19(psql).
Ninguna queda en FAIL por tener solo la mitad.

## 2. Interpretación desde el plan

**No hay ningún FAIL y no queda ningún aserto [M] ausente.** La corrida
canónica de re-corrida (2026-10-24, TRAS el loop-back de apply que añadió
los complementos QP-07/QP-26) dio exit 0 con las 15 líneas E4.15–E4.29 en
PASS (qa-log.txt, bloque "=== VEREDICTOS QA C4 ==="), incluida la línea
`E4.26: PASS` que ya transporta el aserto nuevo [M] "updated_at avanzó".
La suite [L] re-corrida dio `Tests run: 77, Failures: 0, Errors: 0` +
`BUILD SUCCESS` (los 9 métodos de ApiKeyControllerMvcTest — ahora con el
`Mockito.verify(projectsRepository).revokeKey(7L)` añadido en
`deleteSobreArchivadoPermitidoResponde204` —, 1 de ApiKeyLogHygieneTest, 3 de
ApiKeyServiceTest y 4 de ProjectsKeySqlTest; C2/C3 re-corridos verdes).

Los dos asertos [M] que quedaban ausentes en la 1.ª corrida (QP-07 y
QP-26) fueron complementados por apply y verificados en la re-corrida de
hoy (detalle en §4, nota de re-corrida). BLOQUEADO: cero (precondiciones OK,
pila 3×healthy verificada antes de lanzar).

Criterio de PASS global (§5.3): (a) suite exit 0 ✓; (b) smoke exit 0, 15/15 ✓;
(c) E4.13 y E4.14 PASS citados aquí ✓; (d) [O] fallidos: ninguno FALLO, hay
[O] no ejecutados (notados en §5). **PASS GLOBAL del corte C4** (QP-30 queda
N-A por ser OPCIONAL fuera-de-spec, hueco G-1 documentado en §5).

## 3. Comandos exactos

- Corrida canónica VIGENTE (re-corrida 2026-10-24 tras loop-back de apply,
  UNA sola, stdout+stderr completos):
  `bash /home/acuelop369/Escritorio/Proyectos/Project_Log/scripts/qa_c4_smoke.sh > /tmp/qa_c4_smoke_raw_loopback.txt 2>&1` → exit 0.
  Pre-vuelo manual previo: `podman compose ps` → 3/3 healthy (la fase de
  precondiciones del script recompone con `up -d --build --force-recreate` y
  sondea `/me` → 401 `invalid_token` antes de los veredictos, que cumple el
  papel del smoke-of-the-smoke de QP-15).
- Suite [L] (re-corrida 2026-10-24):
  `podman run --rm -v /home/acuelop369/Escritorio/Proyectos/Project_Log/backend:/ws:Z -v /home/acuelop369/.m2:/root/.m2:Z -w /ws docker.io/library/maven:3.9-eclipse-temurin-21 mvn test`
  → exit 0, `Tests run: 77, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS).

## 4. Review de asertos [M] del qa-plan §2 (per-QP, verbatims)

### ApiKeyControllerMvcTest.java (9 métodos)

- **QP-01** → `regenerateSobreProyectoActivoResponde201ConClavesExactas`:
  asertado [M] 201 (`status().isCreated()`), valores con mocks
  (`jsonPath("$.key").value(KEY_FIXTURE)`, `$.prefix` = substring(0,10),
  `$.created_at` = "2026-10-24T12:34:56Z") y conjunto/orden EXACTO:
  `assertThat(clavesDe(...)).containsExactly("key", "prefix", "created_at")`.
  [O] Content-Type application/json: AUSENTE en este método (sí en QP-05/06).
- **QP-02** → `regenerateKeyNaceCasandoLiteralDeFormatoPlk`: asertado [M]
  `matches("^plk_[A-Za-z0-9_-]{43}$")` y `prefix.isEqualTo(KEY_FIXTURE.substring(0, 10))`
  (la key ES la del mock, por lo que equivale a `key.substring(0, 10)`). [O]
  `length()==47`: implícito en la regex.
- **QP-04** → `created_atConFormatoFormatterDeC2`: asertado [M]
  `matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")`.
- **QP-05** → `regenerateProjectoInexistenteResponde404CuerpoEstandar`:
  [M] 404, `code=="not_found"`, `message=="No existe (quizás fue eliminado)"`,
  `$.field.doesNotExist()`, `Mockito.verifyNoInteractions(apiKeyService)` y
  `verify(never()).regenerateKey(...)`. Completo.
- **QP-06** → `regenerateSobreArchivadoResponde409ProjectArchived`: [M] 409,
  `code=="project_archived"`, message == literal S3 EXACTO (concatenado
  "…desarchívalo antes" + " de generar o regenerar su API key"), sin `field`,
  `verifyNoInteractions(apiKeyService)` + `verify(never()).regenerateKey`
  (cierre S2). Completo.
- **QP-07** → `deleteSobreArchivadoPermitidoResponde204`: [M] 204 ✓, [M]
  cuerpo vacío ✓ (`content().string("")`), [M] `verify(never()).findById(...)` ✓
  (sin pre-SELECT, Decisión 3 revisada) — pero **AUSENTE el [M] "mock de
  revokeKey invocado una vez con el id"**: no hay
  `Mockito.verify(projectsRepository).revokeKey(7L)`. El 204 pasa aunque la
  implementación no invocara revokeKey. **INSUFICIENTE: pedir complemento.**
  (El [M]-aserto en pila lo compensa E4.22/QP-22, donde el 204 REAL sí reescribió
  la fila.) Nota: sin fila ARCHIVED en el mock — coherente con "sin chequeo de
  status" (la fila era "solo ilustrativa" en el plan).
  **Nota complemento QP-07 (re-corrida 2026-10-24):** apply añadió
  `Mockito.verify(projectsRepository).revokeKey(7L)` como última línea del
  método (verify sin times() = exactamente una vez, con el id 7L que consume
  el controller bajo test) — presente y verde en la re-corrida
  (`Tests run: 9, Failures: 0` en ApiKeyControllerMvcTest).
- **QP-08** → `deleteProyectoInexistenteResponde404`: [M] 404 + cuerpo
  estándar C3 completo (code/message/sin field). Completo.
- **QP-09** → `sinJwtAmbasRutasResponde401InvalidToken`: [M] 401 en POST y
  DELETE, `jsonPath("$.code").value("invalid_token")`,
  `verifyNoInteractions(projectsRepository)` + `verifyNoInteractions(apiKeyService)`.
  Completo.
- **QP-10** → `deleteExitosoResponde204SinCuerpo`: [M] 204 (`isNoContent`) y
  cuerpo HTTP VACÍO (`content().string("")`). [O] Content-Length 0: AUSENTE.

### ApiKeyServiceTest.java (3 métodos)

- **QP-03** → `newKeyGenera47CharsConEntropiaEnMuestra`: [M] 100 iteraciones
  con `assertThat(key).matches(REGEX_PLK_47)` + `assertThat(claves).hasSize(100)`
  (Set). [O] length 47: implícito en la regex.
- **QP-11** → `sha256EsDelStringCompletoYParejaCorrectaDaTrue`: [M]
  `assertThat(servicio.tiempoConstanteIgual(hashK, servicio.sha256(k))).isTrue()`
  y vector fijo `hex(servicio.sha256("abc")) == "ba7816bf8f01cfea…5ad"`. [O]
  length 32: implícito en el vector fijo igualado. Completo.
- **QP-12** → `tiempoConstanteIgualConHashAnteriorDaFalse`: [M]
  `tiempoConstanteIgual(sha256(kAnterior), sha256(kNueva)) .isFalse()` con dos
  generaciones independientes (`isNotEqualTo` previo). Completo.

### ProjectsKeySqlTest.java (4 métodos, paquete correcto — TR-7 OK, D-3 sin reflection)

- **QP-13** → `updateRegenerarKeyPinaElLiteralExactoDeR43`: [M]
  `UPDATE_REGENERAR_KEY` pin exacto `isEqualTo("UPDATE projects SET api_key_hash = ?, api_key_prefix = ?, key_created_at = now(), updated_at = now() WHERE id = ? RETURNING key_created_at")` + los 3 sub-literales por separado.
- → `updateRevocarKeyPinaElLiteralExactoYElWhereASecas`: [M] pin exacto de
  `UPDATE_REVOCAR_KEY` (`... api_key_hash = NULL, ... updated_at = now() WHERE id = ? RETURNING id`),
  [M] `doesNotContain("IS NOT NULL")` (cierre S1) y [M]-aprox NINGUNA otra
  columna en el SET (`doesNotContain` de name/description/status/" created_at").
- → `conjuntoSetSigueSinMencionarColumnasDeKey`: [M] frontera R4.16a — las 7
  ramas de `conjuntoSet` sin `api_key_hash`/`api_key_prefix`/`key_created_at`.
- → `elUpdateDeC3SigueSiendoElLiteralDelCorteAnterior`: re-anclado R4.2 (bonus).

### ApiKeyLogHygieneTest.java (1 método)

- **QP-14** → `ningunLogDeKeysContieneLaKey`: [M] ListAppender (logback) sobre
  los loggers de `ApiKeyController`/`ApiKeyService`/`ApiKeyResponse` a
  `Level.DEBUG` durante una 201 (`post(URL_REGEN)` → isCreated) y una 204
  (`delete(URL_REVOCAR)` → isNoContent) completas; [M] aserción de
  `LITERALES_PROHIBIDOS = ["plk_", KEY_FIXTURE, KEY_FIXTURE.substring(0, 10),
  KEY_FIXTURE.substring(10, 22)]` con `noneMatch(mensaje.contains)`, sobre
  mensajes formateados y throwable proxies. Completo (los fragmentos van solo
  contra LOGS, no contra campos de BD — no es el check absurdo de TR-11).

### Smoke — nota de re-corrida (complemento QP-26, apply 2026-10-24)

- **QP-26** → `case_e4_26` de `scripts/qa_c4_smoke.sh`: AÑADIDO el aserto
  [M] "updated_at avanzó" — E4.25 captura la referencia con
  `SELECT FLOOR(EXTRACT(EPOCH FROM updated_at))::bigint FROM projects WHERE id = $id_a`
  (a `$TMPDIR_QA/upd_e425.txt`), E4.26 corre `sleep 2` antes del DELETE
  (resolución de segundos, patrón C3/TR-4) y aserta
  `upd > upd_ref` con FAIL explícito `updated_at-epoch-indisponible` /
  `updated_at-no-avanzo` cuando no sale. En la re-corrida la línea
  `E4.26: PASS` de qa-log.txt incluye este aserto [M] (ya no queda solo
  informativo). `sleep 2` está en el código del script (`sleep 2  # updated_at = now() del UPDATE`).

### Foco TR-11

**Conforme.** Ningún test [L] ni [S] aserta que `api_key_prefix` NO contenga
fragmentos de la key. El check de plaintext de E4.19 (c) usa como aguja la
KEY COMPLETA de 47 chars y la contrasta contra `name`, `description` y
(`api_key_prefix = '<key completa>'`); la conformidad del prefix
(10 chars) va como aserción SEPARADA de igualdad en E4.19 (b) y QP-02. En el
test [L] de higiene, `substring(0,10)` y `substring(10,22)` se usan como
agujas contra EVENTOS DE LOG (superficie donde por diseño no debe aparecer
nada del secret), lo cual es correcto y NO cae en TR-11.

## 5. NO cubierta (actualizado desde qa-plan §4)

1. Ingesta con `X-API-Key` (401 `api_key_invalid` / 403 ingesta) → C5 (el 401
   de E4.15 es SONDEO, no contrato). 2. Front/C8 ni maqueta 05 (review de
   fichero). 3. `findByApiKeyHash` → C5 (review de AUSENCIA). 4. `CorsConfig`
   con `X-API-Key` (review INTOCADO). 5. `DuplicateKeyException` → 500: sin
   test, deliberado. 6. `updated_at` en revoke idempotente (QP-24): sin
   asertar, correcto por D-R5. 7. TOCTOU D-R2: aceptado, sin test. 8.
   **QP-30/G-1: sigue SIN cobertura** — no existe método que mockee
   `findById`→ACTIVE + `regenerateKey`→`Optional.empty()`→404; el spec no le
   asigna E4.x. 9. Threading/estrés: sin escenario. 10. No-regresión C3
   completa: suite de C2/C3 re-corrida verde (mvn, este reporte); [O] diff-0
   de intocables NO ejecutado (obligación de sdd-verify). 11. Semántica
   R4.12 (hash persiste al desarchivar): hueco G-2 sin escenario (opcional).

[O] sin ejecutar y notados: QP-01 Content-Type, QP-10 Content-Length, QP-19
cross-check `created_at`↔`key_created_at`, QP-29 diff-0 intocables.