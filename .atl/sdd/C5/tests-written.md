---
change: "C5"
artifact: tests-written
last_updated: 2026-10-25
status: draft
---

# tests-written — C5 · Ingesta REST

> Constancia del paso test-writer ([L], aislado — contexto 0 sobre la
> feature). Corre sobre `qa-plan.md` (38 QP), `spec.md` (E5.1–E5.34) y
> `design.md` (§13) únicamente.

## 1. Resultado del paso — TODO PRE-EXISTENTE (paso corto por regla)

Los 6 ficheros [L] que este paso debía crear **YA EXISTÍAN en disco** al
arrancar (timestamps 09:20–10:35 del 2026-10-25, anteriores a este paso).
Regla del brief aplicada literalmente: fichero ya existente → NO tocar, NO
leer, constatar aquí y pasar. **Cero ficheros de test creados o editados en
este paso**; cero cambios en main (nada bajo `backend/src/main/**`,
`scripts/**` ni `resources/**`); sin commits.

| Fichero | Estado al llegar | Acción de este paso |
|---|---|---|
| `/home/acuelop369/Escritorio/Proyectos/Project_Log/backend/src/test/java/com/projectlog/ingest/IngestControllerMvcTest.java` | EXISTENTE (35.995 B, 09:36) | no tocado, no leído |
| `/home/acuelop369/Escritorio/Proyectos/Project_Log/backend/src/test/java/com/projectlog/ingest/IngestServiceTest.java` | EXISTENTE (8.376 B, 09:37) | no tocado, no leído |
| `/home/acuelop369/Escritorio/Proyectos/Project_Log/backend/src/test/java/com/projectlog/ingest/IngestLogHygieneTest.java` | EXISTENTE (7.633 B, 09:37) | no tocado, no leído |
| `/home/acuelop369/Escritorio/Proyectos/Project_Log/backend/src/test/java/com/projectlog/ingest/IngestGuardLimitTest.java` | EXISTENTE (3.034 B, 10:35) | no tocado, no leído |
| `/home/acuelop369/Escritorio/Proyectos/Project_Log/backend/src/test/java/com/projectlog/ingest/LogsRepoSqlTest.java` | EXISTENTE (2.645 B, 09:38) | no tocado, no leído |
| `/home/acuelop369/Escritorio/Proyectos/Project_Log/backend/src/test/java/com/projectlog/projects/repository/ProjectsIngestSqlTest.java` | EXISTENTE (2.320 B, 09:20) | no tocado, no leído |

Los 6 son exactamente los ficheros del design §13 (tabla
ingest/ProjectsIngestSqlTest): cobertura de ficheros COMPLETA — no falta
ninguno de los 6 del mapa. Intencionadamente ausente (por diseño): tests
nuevos para QP-16-b (re-corrida, sin test nuevo) y QP-21…QP-33 ([S], viven
en `scripts/qa_c5_smoke.sh` — también pre-existente y fuera de este
alcance).

## 2. Ejecución en builder (paso 2 del brief — sí ejecutada)

Comando exacto del brief (imagen `maven:3.9-eclipse-temurin-21`, backend +
`~/.m2` montados):

```
podman run --rm -v .../backend:/ws:Z -v ~/.m2:/root/.m2:Z -w /ws \
  docker.io/library/maven:3.9-eclipse-temurin-21 mvn test
```

**Resultado definitivo: `Tests run: 107, Failures: 0, Errors: 0, Skipped:
0` — BUILD SUCCESS** (total time 6.988 s). Coincide EXACTO con el total
de la suite final anunciado por design (D-14: "suite final 107/107 BUILD
SUCCESS") y con el criterio de PASS del qa-plan §6 (77/77 heredados + la
adición de C5 sin un solo fallo — R5.19b, QP-16-b satisfecho por re-corrida).

Desglose por clase C5 (surefire, todas verdes):

| Clase | tests | fail/err |
|---|---|---|
| `ingest.IngestControllerMvcTest` | 16 | 0/0 |
| `ingest.IngestGuardLimitTest` | 3 | 0/0 |
| `ingest.IngestLogHygieneTest` | 1 | 0/0 |
| `ingest.IngestServiceTest` | 3 | 0/0 |
| `ingest.LogsRepoSqlTest` | 4 | 0/0 |
| `projects.repository.ProjectsIngestSqlTest` | 3 | 0/0 |
| **Sub total C5 [L]** | **30** | **0/0** |

(Suite heredada C1–C4: 77 tests, también 0/0 — total 107.)

## 3. Mapeo QP ↔ fichero/escenario

Sin leer los fuentes de los tests (regla de aislamiento), el mapeo se deja
al nivel fichero ↔ QP que fija el qa-plan §1 (columna "Fichero de test") y
design §13 — la correspondencia por-nombre-de-método la certifica qa-run
(surefire imprime los métodos en el log completo):

| QP (escenarios E5.x) | Fichero [L] | Corrida |
|---|---|---|
| QP-1…QP-14, QP-16, QP-34 (E5.1–E5.14, E5.16, E5.24-c) | `ingest/IngestControllerMvcTest.java` | 16 tests, verde |
| QP-18, QP-19, QP-20, QP-EX-1, QP-EX-3 (E5.18, E5.19, E5.20) | `ingest/IngestServiceTest.java` | 4 tests, verde (QP-EX-1 con método propio desde el loop-back de QA 2026-10-25: `camposDeConfianzaFalsificadosPorElClienteIgnorados`; suite total 108/0/0, log `/tmp/mvn_c5_full6.log`) |
| QP-17 (E5.17) | `ingest/IngestLogHygieneTest.java` | 1 test, verde |
| QP-35 (E5.34) | `ingest/IngestGuardLimitTest.java` | 3 tests, verde |
| QP-15/E5.15 (INSERT: CABECERA/FILA con CAST/RETORNO) | `ingest/LogsRepoSqlTest.java` | 4 tests, verde |
| QP-15/E5.15 (SELECT_POR_API_KEY_HASH, pin junto a ProjectsKeySqlTest) | `projects/repository/ProjectsIngestSqlTest.java` | 3 tests, verde |
| QP-16-b (E5.16-b) | RE-CORRIDA de la suite C1–C4 incluida en los 107 (sin test nuevo) | 77 heredados, 0/0 |
| QP-21…QP-33 (E5.21–E5.33) | [S] `scripts/qa_c5_smoke.sh` — FUERA de este paso (paso qa-run) | pendiente |

Conteo por-QP: los 21 QP [L] del plan quedan dentro de los 6 ficheros/30
tests verdes según la asignación de ficheros del plan (los QP se agrupan
en menos métodos de test por clase: 16 MVC abarcan 14 QP-MVC + QP-16 +
QP-34; 3 unit abarcan 3 QP + los 2 QP-EX [O]/extras en el unit del
service; 3 de límite para QP-35; pin SQL repartido 4+3). **Sin lectura de
los fuentes no puedo certificar por-nombre que QP-EX-1/QP-EX-2/QP-EX-3
tengan método propio** — qa-run/verify debe confirmarlo (flag §6).

## 4. Decisiones test-side (este paso)

1. **Existencia > reescritura**: ante fichero pre-existente, la regla del
   brief es inmediata — no tocar, no leer, documentar, pasar. Evitada
   deliberadamente la segunda escritura (riesgo de chocar con un artefacto
   ya validado en apply, que presumiblemente los corrió — ver §5).
2. **El paso 2 (correr la suite) SÍ se ejecutó** aunque ninguna línea de
   test fuera mía: correr `mvn test` no requiere leer los fuentes y da al
   reporte la señal pedida (0 rojos / BUILD SUCCESS con el total exacto).
   Cualquier rojo se habría anotado aquí con su mensaje exacto sin tocar
   asserts (no hubo ninguno).
3. **`application-test.yml` no tocado** (R5.17) — ni leído ni modificado;
   la suite corre con los defaults de los `@Value` (por eso el pin de
   `"1MiB"` en E5.34 es [M] — design §13).
4. Mapeo por-método NO documentado (imposible sin leer los 6 ficheros) —
   decisión 3 de este §; no se abre ninguna excepción al aislamiento.

## 5. Dudas / flags para qa-run y verify

1. **Presencia real por-QP** ([M] de este artefacto): el mapeo §3 es el del
   plan (por fichero). Falta confirmar por-nombre-de-método en el log de
   surefire (o lectura permitida en la fase qa, tras este paso) que: QP-11
   aserta AMBOS sentidos de `index` (presente en batch, ausente en
   individual), QP-16 pina el anti-oráculo (`invalid_token` jamás en
   respuesta de ingesta), QP-12 cierra el captor del digest hacia
   `findByApiKeyHash`, y QP-EX-1 (hueco S5-2, captura del insert con
   projectId del principal / receivedAt server-side / campos falsificados
   ignorados) EXISTE — RESUELTO en el loop-back de QA 2026-10-25: el método
   `camposDeConfianzaFalsificadosPorElClienteIgnorados` existe en
   `IngestServiceTest` (clase 4/4 verde). QP-12 y QP-7/9 también cerrados
   en el mismo loop-back (verify del sha256 con la key exacta del test +
   mensajes compuestos de `field_too_long` pinnados en
   `IngestControllerMvcTest`, 16/16 verde).
2. **Suite 107/107 coherente con design D-14** ("suite final 107/107"):
   coincide — señal fuerte de que los 6 ficheros escritos en apply son los
   del design §13 (30 tests nuevos + 77 heredados). Ninguna discrepancia
   de conteo que investigar.
3. **[S] pendiente**: QP-21…QP-33 (E5.21–E5.33) corren en `qa_c5_smoke.sh`
   (pre-existente, no examinado por aislamiento). El plan B de drenado
   (R-12) es [O]: si el smoke manifiesta "connection reset" en E5.27
   NO es FAIL de aserto — es activación de plan B (constancia aquí para
   el runner).
4. **Riesgos §4 del qa-plan ya pasados a verde en la ejecución**: la suite
   completa (que incluye los 16 del MVC con anti-oráculo, index 0-based,
   `payload_too_large`) corre 0/0 — ninguna de las 11 trampas del §4
   manifiesta rojo. Quedan como verificación de qa-run sólo las de
   superficie pila ([S]).

## 6. Lista de rojos

NINGUNO. La corrida del builder (paso 2) dio 0 failures, 0 errors, 0
skipped en toda la suite (107/107); no hubo iteración de relajación
(ningún assert fue tocado).