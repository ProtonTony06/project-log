---
change: "C4"
artifact: apply-progress
last_updated: 2026-10-24
status: done
---

# Apply-progress — C4 · API keys de ingesta

> Bitácora de sdd-apply. Por-tarea estado, ficheros (rutas absolutas base:
> `/home/acuelop369/Escritorio/Proyectos/Project_Log`), comandos y resultados,
> desviaciones y notas para QA. Sin commits ni push (working tree).

## Estado por tarea

| Tarea | Estado | Notas |
|---|---|---|
| T4.1 ApiKeyService | CERRADA | 4 primitivas (D-R3: sin loggers) |
| T4.2 ApiKeyServiceTest | CERRADA | E4.3/E4.11/E4.12 verdes |
| T4.3 ApiKeyResponse | CERRADA | record snake_case |
| T4.4 ProjectsRepository aditivo | CERRADA | diff SOLO adiciones (+1 línea Javadoc reescrita de la clase — permitido "AMPLÍA aditivamente") |
| T4.5 ProjectsKeySqlTest | CERRADA | E4.13 verde (4 tests) |
| T4.6 ApiKeyController | CERRADA | 2 mappings, ramas R4.8/R4.9, D-2/D-4 |
| T4.7 ApiKeyControllerMvcTest | CERRADA | 9 tests verdes (E4.1–E4.10) |
| T4.8 ApiKeyLogHygieneTest | CERRADA | E4.14 verde (ListAppender sobre 3 loggers de keys) |
| T4.9 qa_c4_smoke.sh | CERRADA (codificación) | `bash -n` OK; validación funcional = T4.11 |
| T4.10 mvn test completo + intocables | CERRADA | 77/77 BUILD SUCCESS; diff-0; solo V1; sin findByApiKeyHash |
| T4.11 smoke-of-the-smoke + canónica | CERRADA | corrida canónica 15/15 PASS exit 0 |

## Comandos ejecutados y resultados

1. `podman run --rm -v backend:/ws:Z -v ~/.m2:/root/.m2:Z -w /ws
   docker.io/library/maven:3.9-eclipse-temurin-21 mvn -q test-compile` →
   1ª pasada: 2 errores de compilación en ApiKeyControllerMvcTest
   (`JsonPathResultMatchers.matches(String)` NO existe en MockMvc 3.5) →
   reescritas como `MvcResult` + `assertThat(...matches(...))` → 2ª pasada:
   1 fallo de aserción en ProjectsKeySqlTest (`key_created_at` contiene el
   substring `created_at`) → aserción reescrita (`" created_at"` con espacio)
   → corrida completa `mvn test` (builder): **Tests run: 77, Failures: 0,
   Errors: 0, Skipped: 0 — BUILD SUCCESS** (incluye las 4 suites [L] de C4 +
   ProjectsKeySqlTest + todo C1/C2/C3 re-corrido).
2. Intocables (T4.10): `git diff --exit-code` → 0 en SecurityConfig,
   CorsConfig, RestAuthenticationEntryPoint, StartupSecretsValidator,
   V1__init.sql, application.yml, application-test.yml, pom.xml, compose.yaml,
   auth/* (LoginController/JwtAuthFilter/TokenService), docs/** y
   frontend/public/mockups/**; `ls db/migration` == solo `V1__init.sql`;
   `grep -c findByApiKeyHash ProjectsRepository` == 0; `grep -c
   starter-validation pom.xml` == 0.
3. `bash -n scripts/qa_c4_smoke.sh` → SYNTAX_OK (shellcheck no instalado).
4. T4.11 — C3 corridas del smoke (`scripts/qa_c4_smoke.sh`, la pila la levanta
   el propio script con `up -d --build --force-recreate`; jamás `down -v`):
   - **Corrida 1 (smoke-of-the-smoke, TS 20261024213700 aprox)** → exit 0 con
     6 FAIL: E4.19/E4.20/E4.22/E4.24/E4.25/E4.26 — causa 100 % del SCRIPT
     (no del corte): los `::text` sobre booleans en PG17 devuelven
     `true/false` (no `t/f`) y la query (c) de E4.19 mezclaba
     `boolean OR text` (error de tipado SQL). El psql jamás escribió (todas
     las verificaciones eran SELECT). CORREGIDO: sin `::text` (el boolean
     crudo con `-qAt` sale `t/f`) y la query (c) toda como UN boolean.
   - **Corrida 2 (validación, post-fix)** → **15/15 PASS, exit 0**.
   - **Corrida 3 = CORRIDA CANÓNICA** → **15/15 PASS (E4.15–E4.29), exit 0**
     (log: /tmp/qa_c4_run3_canonico.log). Al cierre: pila 3×healthy por
     podman (postgres/backend/frontend), volumen `project_log_pgdata` con
     los datos intactos (`SELECT count(*) FROM projects` = 30; sin `down -v`).

## Desviaciones / notas para QA

- Ninguna respecto a spec/design/tasks. D-2 (204 con
  `ResponseEntity.status(NO_CONTENT).build()`) y D-3 (constantes SQL
  package-private + pin en el mismo paquete sin reflection) preservadas tal
  como manda design.
- Nota de test-side: en E4.2/E4.4 la aserción de regex se hace con
  `assertThat(valor).matches(...)` sobre el cuerpo JSON (MockMvc de esta
  versión no expone `jsonPath(...).matches(String)`).
- Pila ARRANCADA SOLO por el propio smoke (precondición E4.15) con
  `--force-recreate`; PROHIBIDO `down -v` (volumen pgdata sagrado).

## Loop-back QP-07/QP-26 (2026-10-24)

Complemento de los 2 asertos [M] ausentes señalados en qa-report §1/§4
(29/30, ambos PARCIAL). Mínimo cambio posible, sin reordenar escenarios
ni tocar qa-report/qa-log/qa-plan.

1. **QP-07** — `backend/src/test/java/com/projectlog/keys/
   ApiKeyControllerMvcTest.java`, método
   `deleteSobreArchivadoPermitidoResponde204`: añadido
   `Mockito.verify(projectsRepository).revokeKey(7L)` tras los asertos
   ya presentes (204 + cuerpo vacío + never findById). El literal `7L`
   es el MISMO id que consume el controller bajo test
   (`URL_REVOCAR = …/projects/7/api-key` y stub
   `revokeKey(7L)`); `verify(...)` sin times() declara EXACTAMENTE una
   invocación. Import de `org.mockito.Mockito` ya existía (no tocado).
   El resto del test NO se tocó.
2. **QP-26** — `scripts/qa_c4_smoke.sh`:
   - E4.25 (`case_e4_25`): tras el PATCH 200 del archivado, captura del
     epoch de `updated_at` con `psql_q "SELECT
     FLOOR(EXTRACT(EPOCH FROM updated_at))::bigint FROM projects WHERE
     id = $id_a"` (patrón `psql_q` C3, salida `-qAt`) guardada en
     `$TMPDIR_QA/upd_e425.txt` (`|| true`: si falla la captura, E4.26
     lo reporta en su FAIL, no rompe `set -e`). El 409 posterior y el
     GET no escriben la fila, por lo que la referencia queda válida.
   - E4.26 (`case_e4_26`): `sleep 2` ANTES del DELETE (separar
     timestamps en segundos; `updated_at = now()` del UPDATE de
     revoke, literal pinneado en ProjectsKeySqlTest); tras el 204 y el
     aserto tres-NULL ya existentes, NUEVA condición PASS/FAIL: se
     recalcula el epoch y se exige `-gt` respecto a la referencia de
     E4.25; si la referencia está vacía o el epoch da `err` →
     `updated_at-epoch-indisponible` (FAIL explícito, nada silencioso).
     El comentario antiguo "solo informativo, NO se aserta" queda
     sustituido: aquí el spec SÍ exige el avance (QP-24 sigue sin
     asertarlo, sin cambios).
   - `bash -n` → SYNTAX_OK. Sin otros cambios en el script.

Resultados de validación:
- `mvn test` (builder podman, comando canónico) → **Tests run: 77,
  Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS, exit 0**
  (ApiKeyControllerMvcTest 9/9 incluye el nuevo verify).
- Corrida canónica NUEVA del smoke (pila ya 3×healthy por `podman
  compose ps`, sin recomposición) → `bash scripts/qa_c4_smoke.sh >
  /tmp/qa_c4_smoke_raw_loopback.txt 2>&1` → **15/15 PASS
  (E4.15–E4.29), exit 0**, incluido E4.26 con el aserto de epoch
  `upd > upd_ref` verde. RAW guardado en /tmp, NO integrado en
  qa-log.txt (lo re-corre qa-run para la evidencia formal).

Desviaciones: NINGUNA. Ambos complementos cerrados según qa-plan §2
(QP-07 línea ~100: "mock de revokeKey invocado una vez"; QP-26 línea
~201: `updated_at` avanzó con epoch > capturado tras el archivado +
sleep 2). Status del frontmatter: se MANTIENE `done` (ambas validaciones
cierran).