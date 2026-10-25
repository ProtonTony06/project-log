---
project: Project_Log
change: C6
artifact: qa-report
last_updated: 2026-10-25
results: 146/146 pass-fail (146 PASS, 0 FAIL, 0 ERROR, 0 skipped) + smoke: 8/8 pass-fail (E6.16–E6.23 8/8 PASS + pre-fase PASS, exit 0)
commit: 78544b229c34c366f14945c656fb9fbcca8b977b (feat del corte C6, archive; base 43ea041f02452df43e5f2620eab44fef060f6a8d)
log: qa-log.txt
---

# QA Report — C6 · Consulta keyset + filtros + detalle

> Ejecución FORMAL de la suite tras el loop-back de cierre (H1–H9 cerrados
> por apply). EJECUCIÓN, no parcheo: `mvn test` completo + UNA corrida
> canónica del smoke. La verdad RAW vive en `qa-log.txt` (captura verbatim,
> higiene de secretos verificada: 0 hits reales de `plk_`/`eyJ`).
> Veredicto global: **PASS — suite verde completa (146/146 + 8/8, exit 0)**.

## 1. Comandos exactos ejecutados

1. `podman run --rm -v /home/acuelop369/Escritorio/Proyectos/Project_Log/backend:/ws:Z -v /home/acuelop369/.m2:/root/.m2:Z -w /ws docker.io/library/maven:3.9-eclipse-temurin-21 mvn test` → **Tests run: 146, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS, EXIT=0** (qa-log.txt §1; LogsControllerMvcTest 17, DtosLogsPinTest 5, LogsQuerySqlTest 4, CursorCodecTest 6, FiltrosConsultaTest 6 [C6] + 108 heredados = 146).
2. `bash scripts/qa_c6_smoke.sh` → **E6.16–E6.23: 8/8 PASS, EXIT=0** (qa-log.txt §2; recomposición ÚNICA `up -d --build --force-recreate`, NUNCA `down -v`; volumen intacto).

## 2. Tabla por QP-6.x (veredicto + dónde vive la evidencia)

| ID | QP / Caso | Veredicto | Evidencia (método / fase) |
|---|---|---|---|
| QP-6.1 | E6.1 listado happy + frontera (trim) | **PASS** | `LogsControllerMvcTest.listadoShapeExactoItemsYNextCursor` + `listadoSinFilaExtraDejaNextCursorNullExplicito` + `listadoVacioSinCursorEs200EstadoLegitimo` (shape 9 claves, decode-back == fila 49 [la ÚLTIMA], next_cursor null EXPLÍCITO); shape top-level también en smoke E6.16 (`claves_json`) |
| QP-6.2 | E6.2 pin de DTOs | **PASS** | `DtosLogsPinTest` (5 métodos: 9 componentes, `metadata JsonNode` — D-S6-c, serialización claves literales, wrapper `items,next_cursor`) |
| QP-6.3 | E6.3 detalle 200 + nulls | **PASS** | `detalleResponde200ConLos11CamposYMetadataNodoExacto` (11 claves, metadata isObject exacto, nulls EXPLÍCITOS); cierre H3 (formato fechas) ratificado en smoke E6.20 |
| QP-6.4 | E6.4 404 no-existente | **PASS** | `detalleIdInexistenteResponde404NotFound` (cuerpo EXACTO, sin field); pareja [S] en smoke E6.20(b) |
| QP-6.5 | E6.5 enums fuera | **PASS** | `environmentYLevelFueraDeEnumResponde400FilterInvalidValue` (qa/error/vacío, msgs VERBATIM, verifyNoInteractions) + smoke E6.21(b) |
| QP-6.6 | E6.6 from>to | **PASS** | `fromMayorToResponde400FromToInverted` + `fronteraFromIgualToEsLegalYLlegaAlRepo` (field AUSENT, msg verbatim) + smoke E6.21(a) |
| QP-6.7 | E6.7 limit rango/NaN | **PASS** | `limitFueraDeRangoONaNResponde400FilterLimitInvalid` (mismo cuerpo — anti-oráculo S6-6) + smoke E6.21(c)(d) / E6.22 |
| QP-6.8 | E6.8 no-parseables ×3 | **PASS** | `projectIdFromToNoParseablesResponde400FilterInvalidValue` + smoke E6.21(e) |
| QP-6.9 | E6.9 cursor ilegible ×7 | **PASS** | `cursorIlegibleResponde400FilterCursorInvalid` (11 tokens, MISMO cuerpo — anti-oráculo P1) + cierre H1: `cursorConRFuturoResponde200ListaVaciaYNextCursorNullExplicito` (r futuro → 200 vacío, 2.A-4) + smoke E6.21(d) |
| QP-6.10 | E6.10 orden fail-fast | **PASS** | `ordenFailFastDe8ChecksEsDeterminista` (5 sub-casos tras cierre H2: check-1, project_id, from-naive, to-no-fecha [6 antes de 7], from/to invertidos [7 antes de 8]); verifyNoInteractions en TODO 400 |
| QP-6.11 | E6.11 pin SQL dual | **PASS** | `LogsQuerySqlTest` (4 métodos: literal EXACTO R6.16 con D-C6-1, **17 placeholders** — D-S6-a, guard keyset, LIMIT ? +1, NO-ESCAPE, 9/11 cols) |
| QP-6.12 | E6.12 codec round-trip | **PASS** | `CursorCodecTest` (micros EXACTOS 2.A-1, pin P-1 byte-exacto 2.A-5, sin fracciones/nanos, anti-truncado) + pareja smoke E6.17(a) |
| QP-6.13 | E6.13 escape ILIKE | **PASS** | `FiltrosConsultaTest` (orden `\` primero, matriz `_`+`\`, contra-caso aguja, `""` → `%%`) + pareja smoke E6.18 |
| QP-6.14 | E6.14 limit defecto 50 / 200 | **PASS** | `limitDefecto50YLegal200AlRepo` + `limit200LegalAlRepoYSinLimitElDefectoManda` (captor sobre el BEAN repo) + pareja smoke E6.22 (defecto→50 en pila) |
| QP-6.15 | E6.15 401 sin JWT | **PASS** | `ambosEndpointsNacenTras401InvalidToken` (sin JWT y con X-API-Key → 401 `invalid_token`) + `conJwtValidoLlegaAlControllerFirmaDelFlujoDel200`; pareja 12 [S]: pre-fase smoke (sdeo `/me` sin token y CON api-key basura) |
| QP-6.16 | E6.16 filtros combinables | **PASS** | smoke `case_e6_16` (twin psql ids IDENTICOS, delta base+3, k3 WARN fuera, shape) |
| QP-6.17 | E6.17 keyset ESTABLE | **PASS** | smoke `case_e6_17` (corte dentro del mismo instante con `r` a MICROSEGUNDOS, twin keyset-ESTRICTO, sin duplicados/saltos, batch entre páginas, delta H4 base+5/+8) |
| QP-6.18 | E6.18 aguja `_` (S-3) | **PASS** | smoke `case_e6_18` (conteo==1 IGUALDAD ESTRICTA, psql por `=`; contraguja H9 misma-length `-timeout` añadida y ratificada — ver §4a) |
| QP-6.19 | E6.19 rango from/to | **PASS** | smoke `case_e6_19` (`+02:00` ≡ `Z` (ids_off==ids_z), extremos INCLUSIVE con frontera de punto, twin psql, delta base+1) |
| QP-6.20 | E6.20 detalle en pila | **PASS** | smoke `case_e6_20` (exception texto COMPLETO + metadata OBJETO EXACTO (span/attempt), 404 con cuerpo exacto, delta H5 base+1) |
| QP-6.21 | E6.21 codes 400 ×5 | **PASS** | smoke `case_e6_21` (5 needles de cuerpo EXACTO de R6.22, delta-0 H6 — snapshot → igualdad relativa) |
| QP-6.22 | E6.22 limit frontera | **PASS** | smoke `case_e6_22` (limit=200 → 200 items + cursor presente y decodificable; limit=201 → 400 con code+field+**message VERBATIM** [cierre H7]; sin limit → 50) |
| QP-6.23 | E6.23 fin de resultados / walk | **PASS** | smoke `case_e6_23` (recorrido encadenado hasta `next_cursor: null` EXPLÍCITO, sin duplicados ni saltos globales, cobertura == psql del proyecto, batch único con pre-aserto de regla 9) |

**Cuenta: 23/23 PASS** (15 [L] + 8 [S]) + pre-fase/pareja 12 PASS.

## 3. Interpretación por bloque

- **[L] QP-6.1–6.15 (15/15)**: los pins estructurales (DTOs, SQL literal byte-a-byte,
  codec P-1, anti-truncado, escape-order) están todos VERDES y los inversos
  (400s, 404, orden fail-fast, 401) llegan SIEMPRE con cuerpo verbatim de
  R6.22/CD-4 y el repo INTACTO (`verifyNoInteractions`). Los 4 cierres del
  loop-back visibles en la ejecución: H1 (`cursorConRFuturo...`),
  H2 (sub-caso to-check-6-vs-7 en el fail-fast), H7 (message verbatim — lado
  smoke) y los deltas H4–H6. **Ningún FAIL ni ERROR**.
- **[S] QP-6.16–6.23 (8/8, exit 0)**: la pila viva certificó las 12 parejas
  mock↔psql del spec §2.6: filtros combinables twin-exactos, keyset ESTABLE
  con corte dentro de un mismo instante (regla 9), aguja `_` no-comodín,
  rango offset-inclusivo, detalle 200/404, 5×400 reales con delta-0,
  frontera limit 200/201/defecto, y el RECORRIDO COMPLETO hasta fin de
  resultados sin duplicados ni saltos. La recomposición única mantuvo el
  volumen: los conteos de BD son RELATIVOS (deltas) — filas heredadas
  intocadas (R6.28/R-6.h).
- **Transversales 2.A–2.D**: 2.A-1/2.A-2/2.A-3/2.A-5 y 2.B y 2.D CUBIERTOS y
  VERDES en esta corrida; 2.A-4 (`r` futuro, H1) CUBIERTO tras el loop-back;
  2.C (fail-fast) CUBIERTO tras H2 (los 5 sub-casos de orden presentes).

## 4. Matizaciones que van al archive (citadas por el coordinador)

### (a) H9 — contraguja E6.18 misma-length "timeout" — **RATIFICADA**
La contraguja del PLAN (`smoke-c6-<TS>-sin-guion-bajo`, SIN «timeout») no
discriminaba: con `_` comodín el patrón `smoke?c6?TS?timeout` tampoco la
matchearía (no contiene «timeout») → el `conteo==1` pasaría TAMBIÉN con
escape AUSENTE (delta plan=2 → el conteo no distinguía). En la corrida
formal entra la contraguja CORREGIDA de apply: `smoke-c6-<TS>-timeout`
(MISMA longitud, mismo sufijo `-timeout`, sin `_`). Smoke actual: delta
base+3 (aguja + contras 2, matrización H9 del plan) → la aguja matchea SOLO
ella (==1) y la contraguja existe (por `=`) pero NO matchea — con `_`
comodín SÍ habría matcheado un 2.º ⇒ el conteo estricto HOY DISCRIMINA
escape-OK de escape-AUSENTE. Cierre S-3 queda citable por E6.13 (función)
+ esta contraguja (uso en pila).

### (b) H8 / P-3 — premisa R6.15-b falsa en JDK moderno — **resuelta con paso 5c guard-Z**
El literal del spec R6.15-b («`Instant.parse` rechaza `+02:00` por ISO-8601
estricto») describe una causa EMPÍRICAMENTE FALSA: en JDK moderno
(≥12) `ISO_INSTANT` NORMALIZA offsets y `Instant.parse("...+02:00")` SÍ
parsea. El contrato queda respetado por el paso **5c del codec** (guard-Z:
validación EXPLÍCITA de que `r` termina en `Z`), y el resultado contractual
400 `filter_cursor_invalid` sigue vigente y VERDE en esta corrida —
pinado por el test `CursorCodecTest.offsetNoUtcEsRechazadoAunQueInstantParseLoAceptaria`
(test pinnado SIN cambios, que FALLARÍA si el guard-Z desapareciese). H8
es INFO/spec-side: el RESULTADO del contrato se MANTIENE; la causa
documentada en R6.15-b está desfasada.

## 5. Estado de la pila (podman ps tras las corridas)

Los contenedores quedaron RECREADOS por la recomposición del smoke (salida
RAW en qa-log.txt §2 — `up -d --build --force-recreate`):

```
project_log_postgres_1    Up (healthy)
project_log_backend_1     Up (healthy)
project_log_frontend_1    Up (healthy)
```

Volumen INTACTO (NUNCA `down -v`): las 1877 filas heredadas + las agujas de
ESTA corrida (deltas R-6.h) permanecen — estado «pila UP limpia» de C6.
tmpdir 700 con trap: JWT y API key solo en tmpdir, jamás a consola (los logs
publicables pasaron el grep de higiene: 0 hits reales).

## 6. Resumen ejecutivo

Suite completa VERDE en la 1.ª ejecución formal tras el loop-back: 146/146
(en builder maven:3.9-eclipse-temurin-21, patrón C1–C5) + smoke canónico
E6.16–E6.23 8/8 con pre-fase y exit 0 — sin un solo FAIL, sin BLOQUEADO.
Los 9 hallazgos H1–H9 quedaron CERRADOS por apply (no parcheados por este
agente) y su cierre es VISIBLE en la evidencia de esta corrida. QA de C6
termina sin huecos de plan ni deudas nuevas.