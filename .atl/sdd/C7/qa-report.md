---
project: Project_Log
change: C7
artifact: qa-report
last_updated: 2026-10-26
status: done
results: mvn 174/174 PASS exit 0 (146 heredados + 28 de C7 = 26 pre-bucle + H12 + H13: StreamingWsClientTest 8/8, FiltrosConsultaParseTest 11/11) + smoke C7 post-bucle: 9/9 PASS exit 0 (E7.14 con nota R-7.k; H9: close≠1013 → FAIL, no ocurrió) + corrida canónica pre-bucle: mvn 172/172 + smoke 9/9 + qa_c3 24/24 PASS exit 0 (E3.24 intocables diff-0 LIMPIO) + qa_c2 pre-bucle 20/24 (Q20 PASS crítico; Q18/Q19/Q24 FAIL ajenos a C7; Q21 BLOQUEADO crónico)
commit: ce0ddcb (feat del corte; HEAD a6b1962 = feat + test(smoke) del propio corte) · corridas canónicas pre- y post-bucle sobre HEAD a6b1962; post-bucle con parches del bucle APLICADOS SIN COMMIT (git diff --stat: solo scripts/qa_c7_smoke.sh + StreamingWsClientTest.java + FiltrosConsultaParseTest.java — backend MAIN sin diff)
log: qa-log.txt
---

# QA Report — C7 · Streaming WebSocket en tiempo real

Veredicto por EJECUCIÓN FORMAL: corrida canónica PRE-BUCLE + corrida
canónica POST-BUCLE (tras el loop-back de hallazgos MEDIA), RAW verbatim en
`qa-log.txt` (§1–§4 pre-bucle, §5–§6 post-bucle). Orden prescrito respetado:
mvn → qa_c7 → qa_c3 → qa_c2 (última, por su `down -v` de Q13). Post-bucle:
solo los DOS comandos de la corrida canónica (mvn + smoke C7). qa_c3/qa_c2
NO re-corridas post-bucle — su evidencia pre-bucle (§3/§4) sigue VÁLIDA:
entre ambos puntos solo cambió el guion de smoke y dos ficheros de TEST
(cero cambios en backend main), y el Q13 de qa_c2 haría `down -v`. qa_c6:
NINGUNA (ya re-corrida válida en la fase apply, 8/8 PASS).

## Comandos ejecutados (corrida canónica PRE-BUCLE)

1. `podman run --rm -v /home/acuelop369/Proyectos/Project_Log/backend:/ws:Z`
   `-v /home/acuelop369/.m2:/root/.m2:Z -w /ws`
   `docker.io/library/maven:3.9-eclipse-temurin-21 mvn test`
   → exit 0, `Tests run: 172, Failures: 0, Errors: 0, Skipped: 0`,
   BUILD SUCCESS. C7 en la suite: `StreamingWsClientTest` 7/7,
   `LatidoYBackpressureTest` 2/2, `PorIdsSqlPinTest` 7/7,
   `FiltrosConsultaParseTest` 10/10 (= 26 nuevos; 146 heredados intactos).
2. `bash scripts/qa_c7_smoke.sh` → exit 0, 9/9 PASS (recomposición única
   `up -d --build --force-recreate`, pila 3×healthy, guard R-7.j previo OK:
   `FRONTEND_ORIGIN` presente y NON-EMPTY en `.env`).
3. `bash scripts/qa_c3_smoke.sh` → exit 0, 24/24 PASS.
4. `bash scripts/qa_c2_smoke.sh` → exit 1, 20/24 PASS (Q13 ejecutó su
   `down -v` canónico — BD wipeada y re-siembrada por el propio script;
   pila restaurada 3×healthy al cierre).

## Comandos ejecutados (corrida canónica POST-BUCLE — tras loop-back MEDIA)

5. `podman run --rm -v /home/acuelop369/Proyectos/Project_Log/backend:/ws:Z`
   `-v /home/acuelop369/.m2:/root/.m2:Z -w /ws`
   `docker.io/library/maven:3.9-eclipse-temurin-21 mvn test`
   → exit 0, `Tests run: 174, Failures: 0, Errors: 0, Skipped: 0`,
   BUILD SUCCESS. Desglose C7: 26 pre-bucle + 2 nuevos (H12/H13) =
   `StreamingWsClientTest` **8/8** (era 7/7) y `FiltrosConsultaParseTest`
   **11/11** (era 10/10); 146 heredados intactos.
6. `bash scripts/qa_c7_smoke.sh` → exit 0, 9/9 PASS (E7.14 con nota
   R-7.k; H9: el guion endurecido hace FAIL si algún close ≠ 1013 — no
   ocurrió). Recomposición única `up -d --build --force-recreate`, pila
   3×healthy. Sobre HEAD a6b1962 con los parches del bucle SIN commit.

## Tabla de veredictos QP-7.1…QP-7.20

| QP | E7.x | Caso | Veredicto | Método / evidencia (qa-log.txt) |
|---|---|---|---|---|
| QP-7.1 | E7.1 | ciclo end-to-end por nginx → frame | **PASS** | smoke §2 (pre-bucle) y §6/RAW (post-bucle, guion con delta canon) — E7.1: PASS en ambas |
| QP-7.2 | E7.2 | frame tras commit forma exacta §3.5 | **PASS** | mvn §1 (7/7) y §5 post-bucle (8/8) — e72FrameTrasLoteFormaExacta35 en la clase VERDE ambas corridas |
| QP-7.3 | E7.3 | batch de 1, orden del lote, ids vacío sin frame | **PASS** | mvn §1 — e73BatchDeUnoOrdenDelLoteYIdsVacioSinFrame |
| QP-7.4 | E7.4 | set_filters por sesión (3 clientes) | **PASS** | mvn §1 — e74SetFiltersPorSesionTresClientes |
| QP-7.5 | E7.5 | batch de 3 → 1 frame ordenado | **PASS** | smoke §2 (pre-bucle) y §6 (post-bucle, guion con delta canon) — E7.5: PASS en ambas |
| QP-7.6 | E7.6 | recalificación set_filters en pila | **PASS** | smoke §2 — E7.6: PASS |
| QP-7.7 | E7.7 | 401 pre-upgrade ×3 sin oráculo | **PASS** | mvn §1 — e77Handshake401PreUpgradeTresCausasSinOraculo; post-bucle §5: pin añadido de CUERPO VACÍO en la familia 401 (8/8) |
| QP-7.8 | E7.8 | Origin validada, jamás `*`, ausente→101 | **PASS** | mvn §1 — e78OriginValidadaExactaYJamasComodin |
| QP-7.9 | E7.9 | handshake por proxy (401/403/101) | **PASS** | smoke §2 — E7.9: PASS; post-bucle §6: orden global del guion (2..7) re-escenario y PASS |
| QP-7.10 | E7.10 | ping 0x9/0xA ~30 s, ningún JSON ≠ batch | **PASS** | smoke §2 — E7.10: PASS (≥2 heartbeats, ~65 s solapados) |
| QP-7.11 | E7.11 | reconexión limpia SIN replay | **PASS** | smoke §2 — E7.11: PASS |
| QP-7.12 | E7.12 | latido cierra muda 1001 + limpieza + reconexión | **PASS** | mvn §1 — e12LatidoCierraMuda1001LimpiaYElRealPermaneceYReconexion (LatidoYBackpressureTest 2/2) |
| QP-7.13 | E7.13 | cierre 1013 por AMBOS límites | **PASS** | mvn §1 — e13Cierre1013AmbosLimitesYLosDemasIntactos |
| QP-7.14 | E7.14 | backpressure en pila (tolerancia R-7.k) | **PASS con nota R-7.k** | smoke §2 y §6 — «1013 no materializado en pila — cubierto por pareja [L] E7.13 (R-7.k; code=)»; post-bucle el guion hace la tolerancia EXCLUSIVA-1013 (FAIL si algún close ≠ 1013 — H9, no ocurrió); el pin contractual vive en QP-7.13 [L] VERDE |
| QP-7.15 | E7.15 | archivado deja de emitir | **PASS** | smoke §2 — E7.15: PASS |
| QP-7.16 | E7.16 | no-contrato ignorado, vigentes conservados, sin replay | **PASS** | mvn §1 — e76NoContratoIgnoradoSinCierreYVigentesConservados + e76eSinReplayTrasDesconexion |
| QP-7.17 | E7.17 | ws_echo_test.py --solo-handshake SIN edición | **PASS** | smoke §2 — E7.17: PASS (**+** re-corrida qa_c2 §4: **Q20 PASS** — misma forma R-7.f) |
| QP-7.18 | E7.18 | regresión 146 + N verde | **PASS** | mvn §1 pre-bucle 172/172 (146 + 26) y §5 post-bucle **174/174** (146 + 28, con H12/H13); NINGÚN test heredado editado |
| QP-7.19 | E7.19 | pin SQL porIds + reorden en Java | **PASS** | mvn §1 — PorIdsSqlPinTest 7/7 |
| QP-7.20 | E7.20 | pin parser extraído (semántica ADR-7) | **PASS** | mvn §1 — FiltrosConsultaParseTest 10/10; post-bucle §5: 11/11 — NOTA DE COBERTURA: pin añadido de matching R7.16 (H13) |

**20/20 QP verdes** (uno de ellos con la nota tolerada R-7.k), re-certificados
en la corrida POST-BUCLE. Higiene de secretos: 0 hits `plk_`/`eyJ` en TODAS
las capturas (4 pre-bucle + 2 post-bucle); JWT/key solo en tmpdir 0700 con
trap (per plan §4, R7.35).

## Interpretación escrita por QA (basada en el plan)

- **Ningún caso QP-7.x FAIL.** Un FAIL de [L] con método `e7x*` o de [S]
  con `E7.n: FAIL` habría nombrado su QP; no ocurrió. La nota de E7.14 no
  es FAIL: es la tolerancia ESCRITA del plan §4/R-7.k (criterio Q23 de C3).
- **QP-7.17 doble evidencia**: E7.17 PASS en el smoke canónico Y Q20 PASS
  en la re-corrida de qa_c2 con el modo nuevo `--solo-handshake --token`
  (401→token→101) — el edit T7.22.4 de qa_c2_smoke.sh está en HEAD y
  funciona (R-7.f cumplido).
- **qa_c3 re-corrida viable y verde**: el diff de intocables de E3.24 sale
  LIMPIO desde HEAD (los edits de qa_c2_smoke.sh/application.yml son parte
  del HEAD del corte). Los 23 casos restantes de C3 también PASS.
- **FAIL en qa_c2 AJENOS a C7** (el smoke sale exit 1 por ellos): Q18
  (healthchecks no-healthy en muestreo t1/t2 tras la restauración F1 —
  flake análogo al Q18 de C2 ya histórico; C7 no toca healthchecks),
  Q19 (`/actuator/env` → 200 esperado 404; el cuerpo SIN contenido de
  configuración sigue OK — C7 no toca actuator) y Q24 (preflight CORS →
  200/headers sin X-Confirm-* — C7 no toca CORS). Clasificación exacta
  (flake vs absorción de cortes posteriores) es decisión del orquestador;
  para C7 lo que importa (Q20, R-7.f) está PASS. Q21 BLOQUEADO es crónico
  (sin hash de referencia del plan, igual que en C2).

## Cierre del bucle acotado (H1/H4/H5/H9/H12/H13)

**Dónde se parcheó** (sdd-apply, SIN commit sobre a6b1962 — confirmado con
`git diff --stat`: 3 ficheros, backend MAIN sin diff):
- `scripts/qa_c7_smoke.sh` — guion endurecido: delta canon para E7.1/E7.5
  (snapshot psql del proyecto ANTES de la ingesta → delta tras la 202) y
  unicidad de batch_id EXPLÍCITA contra los batch_id previos ($F_BIDS +
  helper `bid_de()`), tolerancia del E7.14 hecha exclusiva-1013
  (H9: close ≠ 1013 ⇒ FAIL), conservando `up -d --build --force-recreate`
  y NUNCA `down -v`. (Corrección de atribución verify: el pin del ORDEN
  GLOBAL 2→7 NO vive en el guion — vive en FiltrosConsultaParseTest.)
- `StreamingWsClientTest.java` — clase 7/7 → **8/8** POR H13: +1 test [L]
  NUEVO, matching R7.16 (`r716MatchingFromToInclusivoYTextoCaseInsensitive`,
  fronteras == from / == to INCLUSIVAS, texto case-insensitive; cobertura
  adicional junto a QP-7.20). El H5 (pin cuerpo vacío 401) es un ASERTO
  añadido IN-SITU al test existente de E7.7, no un test nuevo.
- `FiltrosConsultaParseTest.java` — +1 test [L]: pin del ORDEN GLOBAL 2→7
  (`ordenGlobalChecks2a7PrimerFalloGana`, sub-casos combinados en cadena,
  literales R6.22 verbatim); clase 10/10 → **11/11**.

**Cifras de la corrida formal post-bucle** (RAW en qa-log.txt §5–§6):
`mvn test` **174/174 PASS exit 0** (146 heredados + 28 de C7); smoke C7
**9/9 PASS exit 0** (E7.14 con nota R-7.k). Higiene 0/0.

**BAJOS RETENIDOS para verify** (no aplicados en este bucle): H2, H3, H6,
H7, H8, H10, H11, H15, H16.

## Riesgos / notas

- **R-7.k**: el 1013 de pila no se materializa con los defaults (512 KiB /
  2000 ms) — PASS con nota; el pin 1013-dual vive en QP-7.13 [L] VERDE.
- **R-7.j**: guard evaluado, no activado — `FRONTEND_ORIGIN` NON-EMPTY en
  `.env`; ningún escenario BLOQUEADO por ello, jamás `"*"`.
- **mvn corrió DOS veces canónicas** (T7.17 pre-smoke + re-corrida
  post-bucle tras aplicar los parches del loop-back; T7.23 post-smoke
  solo-regresión de la misma suite cubierta por la corrida post-bucle —
  172/172 y 174/174 ambas verde).
- **Los parches del bucle están SIN COMMIT en el árbol** (3 ficheros M;
  backend main sin diff): la decisión de commit es del orquestador; los
  artefactos QA reflejan el estado efectivo de las corridas.
- **Q13 down -v (canon C2)**: la BD del punto 4 queda WIPEADA y re-siembrada
  por el propio script qa_c2; la pila queda restaurada y 3×healthy. Los
  datos de smokes futuros (C5/C6/C7 patterns) se re-siembra en sus propias
  pre-fases — ninguna otra corrida debe asumir datos tras esta re-corrida.
- **E7.14 nota verbatim** incluye `(R-7.k; code=)` tal cual la emite el
  smoke (sin detalle del code de cierre — el código contractual no se
  observa en pila con los defaults, consistente con la nota R-7.k).

## Veredicto global

**PASS** — corrida canónica pre-bucle: 172/172 [L] + 9/9 [S] + qa_c3 24/24
(E3.24 limpio) + Q20 de qa_c2 PASS; corrida canónica post-bucle (parches
H1/H4/H5/H9/H12/H13 aplicados): **174/174 [L] + 9/9 [S] del corte** (E7.14
con nota tolerada R-7.k; H9 verificado). Sin loop-back pendiente que
bloquee: seguir a verify (con los BAJOS retenidos H2/H3/H6/H7/H8/H10/H11/
H15/H16 anotados para esa fase).