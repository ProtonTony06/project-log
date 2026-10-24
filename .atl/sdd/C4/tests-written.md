---
change: "C4"
artifact: tests-written
last_updated: 2026-10-24
status: draft
---

# tests-written — C4 · API keys de ingesta

> Fase sdd-qa (implementación de tests [L]). Escrito por el test-writer FRESH
> con aislamiento de autoría (sin leer código de la feature, diffs,
> `apply-progress.md`, `qa_c4_smoke.sh` ni los ficheros de test existentes de
> `keys`). Fuente de trabajo: `qa-plan.md` + `spec.md` + `design.md`.

## 1. Situación hallada — NINGÚN fichero escrito por este agente

Los CUATRO ficheros de test [L] que este plan asignaba al test-writer YA
EXISTÍAN en disco (sin trackear en git, timestamps del 2026-10-24) al momento
de arrancar esta tarea:

- `backend/src/test/java/com/projectlog/keys/ApiKeyControllerMvcTest.java`
- `backend/src/test/java/com/projectlog/keys/ApiKeyServiceTest.java`
- `backend/src/test/java/com/projectlog/keys/ApiKeyLogHygieneTest.java`
- `backend/src/test/java/com/projectlog/projects/repository/ProjectsKeySqlTest.java`

Siguiendo la instrucción dada ("si un fichero de test de la feature YA
EXISTE: NO lo toques, no lo sobrescribas — deja constancia y pasa"), se
dejan INTACTOS y NO se han leído (ni sus fuentes ni sus diffs: el aislamiento
se mantiene respecto a test-files ajenos igual que respecto al código de la
feature). Por tanto este corte NO tiene tests nuevos de este agente: lo que
sigue es la constancia + la corrida de verificación de los existentes.

Limitación de evidencia resultante: al no poder leer los ficheros, el
mapeo fichero→QP de la §2 se infiere SOLO de los nombres de testcase del
XML de surefire (salida de la corrida, no fuente) cruzados con los nombres
de escenario `E4.x.*` de la §2 del spec. No se puede certificar desde aquí
qué asertos [M] exactos del qa-plan contiene cada método — esa verificación
de contenido la tiene que hacer sdd-verify/qa-run por review (los verdes
pueden estar "probar poco").

## 2. Corrida en builder maven (comando del R4.15)

`mvn test` en `maven:3.9-eclipse-temurin-21` (backend + `~/.m2` montados, sin
BD, `application-test.yml` intocado): **exit 0, BUILD SUCCESS, 77 tests /
0 failures / 0 errors / 0 skipped**. Compilación y suite de C4 verdes de
una sola pasada.

| Clase de C4 | Tests | Resultado |
|---|---|---|
| com.projectlog.keys.ApiKeyControllerMvcTest | 9 | 0F/0E |
| com.projectlog.keys.ApiKeyServiceTest | 3 | 0F/0E |
| com.projectlog.keys.ApiKeyLogHygieneTest | 1 | 0F/0E |
| com.projectlog.projects.repository.ProjectsKeySqlTest | 4 | 0F/0E |
| (resto: suites C2/C3 re-corridas) | 60 | 0F/0E |

## 3. Mapeo QP → testcase existente (por nombre de surefire, no por fuente)

- **QP-01** → `ApiKeyControllerMvcTest.regenerateSobreProyectoActivoResponde201ConClavesExactas` (E4.1)
- **QP-02** → `ApiKeyControllerMvcTest.regenerateKeyNaceCasandoLiteralDeFormatoPlk` (E4.2)
- **QP-03** → `ApiKeyServiceTest.newKeyGenera47CharsConEntropiaEnMuestra` (E4.3)
- **QP-04** → `ApiKeyControllerMvcTest.created_atConFormatoFormatterDeC2` (E4.4)
- **QP-05** → `ApiKeyControllerMvcTest.regenerateProjectoInexistenteResponde404CuerpoEstandar` (E4.5)
- **QP-06** → `ApiKeyControllerMvcTest.regenerateSobreArchivadoResponde409ProjectArchived` (E4.6)
- **QP-07** → `ApiKeyControllerMvcTest.deleteSobreArchivadoPermitidoResponde204` (E4.7)
- **QP-08** → `ApiKeyControllerMvcTest.deleteProyectoInexistenteResponde404` (E4.8)
- **QP-09** → `ApiKeyControllerMvcTest.sinJwtAmbasRutasResponde401InvalidToken` (E4.9)
- **QP-10** → `ApiKeyControllerMvcTest.deleteExitosoResponde204SinCuerpo` (E4.10)
- **QP-11** → `ApiKeyServiceTest.sha256EsDelStringCompletoYParejaCorrectaDaTrue` (E4.11)
- **QP-12** → `ApiKeyServiceTest.tiempoConstanteIgualConHashAnteriorDaFalse` (E4.12)
- **QP-13** → `ProjectsKeySqlTest.updateRegenerarKeyPinaElLiteralExactoDeR43`,
  `updateRevocarKeyPinaElLiteralExactoYElWhereASecas`,
  `conjuntoSetSigueSinMencionarColumnasDeKey` + un 4.º
  `elUpdateDeC3SigueSiendoElLiteralDelCorteAnterior` (no-regresión R4.16a
  del corte C3, de más) (E4.13)
- **QP-14** → `ApiKeyLogHygieneTest.ningunLogDeKeysContieneLaKey` (E4.14)
- **QP-15..QP-29** → [S] del smoke: NO los escribe este agente (`qa_c4_smoke.sh`, qa-run)
- **QP-30** (opcional, rama (6) del R4.8 — hueco G-1) → **SIN test**: ningún
  testcase de empty-RETURNING con fila ACTIVE está entre los nombres
  extraídos. Marca opcional del plan, no bloquea PASS.

Cobertura [L]: **14/14 QP mandatorios mapeados** (QP-01..QP-14) + QP-30
opcional ausente.

## 4. Riesgos del qa-plan — estado tras la corrida

- **TR-7** (constantes SQL `private` → pin no compila): CERRADO A FAVOR de
  D-3. El pin compila y corre verde en el paquete
  `com.projectlog.projects.repository` → las constantes son package-private
  (o con visibilidad legible), no `private`. No fue necesaria reflection.
- **TR-11** (aserción correcta sobre la key COMPLETA vs prefix): no
  verificable desde aquí sin leer el fuente del pin/test MVC; el nombre
  testcase de QP-13 sugiere que el pin es de literales (donde TR-11 no
  aplica); pendiente de review en qa-run.
- **TR-1** (falsos patrones C2/C3): los nombres testcase coinciden EXACTO
  con los nombres de escenario `E4.x` del spec (p. ej. 409 `project_archived`,
  204 idempotente), señal indirecta de que se escribió desde el spec C4 y no
  de memoria de cortes anteriores. Confirmación de aserciones por review.

## 5. Decisiones de lado-test (de este agente: NINGUNA — ficheros ajenos)

Ningún import, fixture ni builder fue decidido aquí: los cuatro ficheros
existentes no fueron leídos ni editados. El @Import literal de C2, los
patrones ListAppender y el pin SQL quedan como estaban en los ficheros.

## 6. Dudas / flags del spec-design detectadas

- **FLAG G-1 (ya documentado en el qa-plan §4.8)**: el spec no asigna
  escenario E4.x a la rama (6) del R4.8 (empty-RETURNING con fila ACTIVE);
  el plan lo cubre con QP-30 opcional y NO existe. Sin impacto Done.
- **Observación de aislar**: la verificación de que los verdes no son
  vacíos (que cada test contiene los asertos [M] del qa-plan §2) NO es
  certificable por un agente aislado que no lea los fuentes. Recomendación
  a qa-run/sdd-verify: review de los 4 ficheros contra la §2 del qa-plan,
  o un pase de mutación ligero en los casos más baratos (QP-06 409,
  QP-11 vector fijo).

## 7. Tests que quedaron ROJOS (en la corrida de este agente)

NINGUNO: 77/77 verdes, exit 0. No hay mensajes de fallo que reportar.
Advertencia de la instrucción mantenida: verdes a la primera NO equivalen
a aserciones fuertes cuando el test no es del autor de este reporte.