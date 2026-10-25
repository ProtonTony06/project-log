---
change: "C5"
artifact: qa-report
last_updated: 2026-10-25
results: "37/37 pass (0 fail) · 13/13 smoke · QP-EX-2/EX-3 [O] sin cobertura"
commit: "6cc609f1994d536a95db198bb4408b932115062f"
log: qa-log.txt
---

# QA report — C5 · Ingesta REST

> **2.ª emisión (post-remediación)**: evidencia VIGENTE = corrida tras el
> loop-back de remedios de apply (higiene + cobertura). Corridas:
> 1.ª canónica 10:45 (107/107 + 13/13, hallazgos de review);
> remediación de apply (qa_c5_smoke.sh sin dumps fuera de tmpdir 700,
> 8 keys de smoke revocadas con trío NULL verificado, QP-EX-1 [L],
> QP-12 verify del sha256 del header completo, messages de field_too_long);
> 2.ª canónica (ESTA, 10:57) = mvn 108/108 + smoke 13/13 (log: qa-log.txt).

## (i) Tabla de veredictos (+ QP-16b y EX)

| QP | Veredicto | Pareja | Método / evidencia |
|---|---|---|---|
| QP-1 | PASS | 1 ✓ | `ingestIndividualResponde202Accepted1Exacto` — `{"accepted":1}` exacto + `verify(insert(7L,…))` |
| QP-2 | PASS | 2 ✓ | `ingestBatchResponde202AcceptedNExacto` — `{"accepted":3}` (N≥2) |
| QP-3 | PASS | 6 ✓ | `environmentAusenteOInvalidoResponde400EnvironmentRequired` — 5 variantes + cuerpo EXACTO sin `index` |
| QP-4 | PASS | 6 ✓ | `timestampNaiveONoParseableResponde400TimestampWithoutTimezone` — naive/no-textual + validas Z/+02:00→202 |
| QP-5 | PASS | — | `levelFueraDeEnumResponde400LevelInvalid` — sin normalización (`"error"`, `"TRACE "`, ausente, null → 400) |
| QP-6 | PASS | — | `messageVacioResponde400MessageRequired` — null/`""`/`"   "` |
| QP-7 | **PASS (FULL)** | 7 ✓ | `messageBytes32769Responde400FieldTooLongFrontera` — frontera multibyte + **message compuesto EXACTO ("…32768 bytes") [post-remediación]** |
| QP-8 | PASS | 7 ✓ | `metadataArrayOEscalarResponde400MetadataNotObject` — array/escalar→400, `{}`→202 |
| QP-9 | **PASS (FULL)** | 7 ✓ | `fieldTooLongDeExceptionMetadataSourceYCorrelationId` — fronteras ×4 + **messages compuestos EXACTOS (131072/65536/200/200) [post-remediación]** |
| QP-10 | PASS | 8 ✓ | `batchVacioONoArrayY201Responde400FieldLogs` — batch_empty/batch_too_large (message límite exacto) + `index:0` solo batch + `never insert` |
| QP-11 | PASS | 3 ✓ | `batchRegistroIntermedioLlevaIndexYFieldPrimerFalloGana` — AMBOS sentidos de `index` |
| QP-12 | **PASS (FULL)** | 4 ✓ | `sinHeaderMalformadaONoMatchResponde401ApiKeyInvalid` — cuerpo byte-a-byte único + **`Mockito.verify(apiKeyService).sha256(KEY_FIXTURE)` [post-remediación]** (digest→repo implícito strict-stubbing, see (iv)-1') |
| QP-13 | PASS | 5 ✓ | `proyectoArchivadoResponde403ProjectArchivedAntesDeValidar` — 403 válido E inválido, body exact con acento, sin field, repo never |
| QP-14 | PASS | 9 ✓ | `ordenFailFastDeCamposEsDeterminista` |
| QP-15 | PASS | 10 ✓ | `LogsRepoSqlTest` ×4 (CABECERA/FILA-with-cast/RETORNO) + `ProjectsIngestSqlTest` ×3 (SELECT exacto/COLS/lectura) |
| QP-16 | PASS | 11 ✓ | `jwtNuncaAutenticaIngestaYCadena1SinInvalidToken` — 401 `api_key_invalid` + `doesNotContain("invalid_token")` (anti-oráculo) |
| QP-16b | PASS | 11 ✓ | re-corrida heredados C1–C4 (77) en la misma corrida mvn (108 total, PanelAuthMvcTest 6/6) |
| QP-17 | PASS (parcial en [M]) | — | `ningunLogFiltraLaKeyNiSuFragmentoNiElHeaderNiElHash` — ListAppender DEBUG sobre `com.projectlog` 202+401; literal del hash = `"api_key_hash"` (no el hex del digest — ver (iv)-3') |
| QP-18 | PASS | — | `timestampParseAceptaZyOffsetsYRechazaNaive` |
| QP-19 | PASS | — | `received_atFijadoUnaVezComunAlLote` — times(1) + captor OffsetDateTime + igualdad captor-seam |
| QP-20 | PASS | — | `seamIngestListenerSoloTrasCommitConArgsExactos` — (a) batch ids EN ORDER de VALUES; (b) tamaño 1; (c1)/(c2) verifyNoInteractions + 400 `index:1` 0-based + insert que lanza propagado |
| QP-EX-1 | **PASS (FULL, [L] nuevo)** | — | `camposDeConfianzaFalsificadosPorElClienteIgnorados` (surefire, NUEVO) — [post-remediación]: record SOLO 8 componentes (reflection), JSON de 6 falsificados + variantes camelCase ignorados + captor `insert` con `project_id=7` del principal (jamás `999`) + `received_at` server-side (jamás `1999`) |
| QP-EX-2 | SIN COBERTURA ([O]) | — | Sin test de `shouldNotFilter` fuera de `/api/v1/ingest/**` |
| QP-EX-3 | SIN COBERTURA ([O]) | — | Sin test de opcionales vacíos capturados como `""` |
| QP-21 | PASS | 1 ✓ 10 ✓ | smoke E5.21 — 202 + delta=1 + fila con igualdades EXACTAS (`t`) |
| QP-22 | PASS | 2 ✓ 10 ✓ | smoke E5.22 — delta=200, count(DISTINCT received_at)=1 sobre ids nuevos, ROW_NUMBER id↔correlation_id |
| QP-23 | PASS | 3 ✓ | smoke E5.23 — 400 index:2 **Y delta=0** (all-or-nothing, ambas mitades) |
| QP-24 | PASS | 4 ✓ | smoke E5.24 — 3×401 mismo cuerpo (jamás `invalid_token`), DELETE→204 + trío NULL, key2 201 |
| QP-25 | PASS | 5 ✓ | smoke E5.25 — 403 literal de INGESTA exacto + delta=0 |
| QP-26 | PASS | 6 ✓ | smoke E5.26 — dos 400s + delta=0 |
| QP-27 | PASS | — | smoke E5.27 — 1048576 (inclusivo)→202 delta=1; 1048577 RUTA DIRECTA→413 delta=0; sin reset → plan B R-12 NO activado |
| QP-28 | PASS | — | smoke E5.28 — CHUNKED directo (sin Content-Length)→413, delta=0 |
| QP-29 | PASS | — | smoke E5.29 — `timestamp = '…Z'::timestamptz` por instants + `received_at` ≥ T1 (FLOOR 1 s tolerado) |
| QP-30 | PASS | 7 ✓ | smoke E5.30 — JSONB `metadata->>'span'` + 65536/65537 + 32768/32769 |
| QP-31 | PASS | — | smoke E5.31 (G-2) — PATCH ACTIVE + MISMA key2 → 202 |
| QP-32 | PASS | 8 ✓ | smoke E5.32 — []→batch_empty / 201→batch_too_large + delta=0 |
| QP-33 | PASS | 9 ✓ | smoke E5.33 — >1 MiB+key-basura→413 (NO 401) directo; ≤1 MiB→401 |

Resumen: **37/37 PASS, 0 FAIL** (31 tests C5 verdes en surefire: 16 MVC + 3
guard + 1 higiene + **4** service + 4 LogsRepo + 3 ProjectsIngest; heredados
77/77 → total mvn **108/108**) + smoke **13/13 exit 0**. QP-EX-2/EX-3 son
[O] del plan (sin cobertura, no gate). **QP-17 queda como único [M] con
matiz** (literal del hex del digest — detalle (iv)-3').

## (ii) Interpretación desde el plan

NINGÚN FAIL en ninguna de las dos corridas (pre y post remediación). El
1.er hallazgo (dump de keys fuera de tmpdir del smoke) fue REMEDIADO por
apply y CONSTANTADO en la 2.ª corrida: `/tmp/qa_c5_req_debug.log` y
`/tmp/qa_c5_curl_err.log` NO se crean (el script remedio ya NO los copia —
ver la nota del propio script, "hallazgo de qa-loop-back 2026-10-25 (higiene
1a)"). Las 2 aserciones de contenido añadidas por la remediación (captor/
verify QP-12 + messages QP-7/9 + QP-EX-1) pasaron a FULL en verde.

Precondiciones del §6: recomposición única `--build --force-recreate` (el
pgdata sobrevive — delta relativos sobre base capturada), smoke-of-the-smoke
(ruta directa a `backend:8080` para los 3 cuerpos >1 MiB por nginx default,
desviación E2E documentada del propio script).

## (iii) Comandos exactos ejecutados (2.ª corrida)

1. `podman compose ps` — 3×healthy (recién re-creada por la re-corrida de apply).
2. `podman run --rm -v /home/acuelop369/Escritorio/Proyectos/Project_Log/backend:/ws:Z -v /home/acuelop369/.m2:/root/.m2:Z -w /ws docker.io/library/maven:3.9-eclipse-temurin-21 mvn test` → **Tests run: 108, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS, EXIT=0**.
3. `bash /home/acuelop369/Escritorio/Proyectos/Project_Log/scripts/qa_c5_smoke.sh > /tmp/qa_c5_canonico_post.txt 2>&1` → **13/13 PASS, SMOKE_EXIT=0**; `curl_err.log` interno VACÍO (sin connection reset → plan B R-12 NO activado); salida con **0 coincidencias `plk_`/JWT** (verificación `grep -cE 'plk_|eyJ'` = 0 en ambas salidas).
4. Post-corrida (higiene, verbatim en qa-log §C): `/tmp/qa_c5_req_debug.log` y `/tmp/qa_c5_curl_err.log` **NO existen**; estado de keys de smoke en psql = 8 proyectos con trío NULL excepto la key viva del ciclo propio (proyecto 50) → revocada post-corrida vía psql (UPDATE trío NULL, verificado t) — **0 keys de smoke vivas al final**; pila 3×healthy.

## (iv) Review de asertos [M] per-QP (fuentes leídos ENTEROS, 2 corridas)

Confirmados [M] claves (sin cambios): 202 cuerpos EXACTOS string; 401 = un
ÚNICO cuerpo byte-a-byte (nodo de 2 claves, sin `field`), `invalid_token`
JAMÁS en la superficie de ingesta (asertado en QP-16 [L] y con needles en
3 escenarios [S]); 403 body exact con acento leído UTF-8 desde bytes; 413
`payload_too_large`; `index` 0-based en los DOS sentidos; all-or-nothing
con DOS mitades (HTTP + delta=0); conteo RELATIVO y `count(DISTINCT
received_at)=1` SOBRE los ids del lote; normalización UTC por instants +
`received_at` ≥ T1; orden de VALUES vía ROW_NUMBER; E5.31 (G-2, MISMA
key2); orden fail-fast determinista; ilegibles→400 (JAMÁS 415).

Estado [M] TRAS la remediación de apply:

1'. **QP-12 — verify del sha256 del header COMPLETO: AÑADIDO
   [post-remediación]**: `Mockito.verify(apiKeyService).sha256(KEY_FIXTURE)`
   dentro del test happy (QP-1/12). El captor del `byte[]` hacia
   `findByApiKeyHash` sigue siendo de facto (strict-stubbing: el stub solo
   matchea `HASH_FIXTURE`) — nota de matiz, no rojo: el B1 completo queda
   E2E certificado en pila (E5.21 match→202, E5.24 no-match→401).
2'. **QP-7/QP-9 — messages compuestos EXACTOS: AÑADIDOS [post-remediación]**
   en 5 fronteras (`message` 32768, `exception` 131072, `metadata` 65536,
   `source` 200, `correlation_id` 200 — BYTES vs CARACTERES en el literal,
   verbatim del plan §2.2).
3'. **QP-17 — literal del hash: sigue PARCIAL**: pin de `"api_key_hash"`
   (columna) y de `X-API-Key`, key completa y fragmento `plk_ingestaC5Higiene`
   (no vacuoso); el **hex del digest** (`010203` del HASH_FIXTURE) no va
   en la lista de needles. Sin re-mediación pedida (señal débil — el
   needle del hex sería vacuoso sin sha256 real del fixture).
4'. **QP-EX-1 — CONFIRMADO EXISTENTE [post-remediación]** (surefire:
   `camposDeConfianzaFalsificadosPorElClienteIgnorados`): aserto EXACTO del
   plan — record de SOLO 8 componentes (reflection), JSON con
   `project_id/received_at/id` + camelCase ignorados, captor del `insert`
   con `projectId = 7L` (jamás 999) y `received_at` server-side (jamás el
   1999 falsificado). Hueco S5-2 CERRADO.
5'. Higiene del smoke: **RE-SOLVED/REMEDIADO** — el script remedio NO copia
   dumps fuera del tmpdir 700 (comentado in-situ: "hallazgo de qa-loop-back
   2026-10-25 (higiene 1a)") y las 2 salidas canónicas de esta corrida
   tienen 0 `plk_`/JWT; 0 keys de smoke vivas al final (8/8 trío NULL).
6'. Mapeo por-surefire: 4 métodos en `IngestServiceTest` (con el nuevo
   EX-1), 16+3+1+4+4+3 = 31 tests C5, 108 total — coincide con el anunciado
   por la remediación.

## (v) NO cubierta (actualizada)

- Todo §5 del qa-plan vigente: streaming (C7), consulta (C6), front (C8), Testcontainers, **nginx `client_max_body_size` en la puerta 8080 (deuda C9 — el 413 [S] se certifica por ruta directa)**, DIVE, 429, G-1 (cerrado), S-3 (convención), ObjectProvider/D-14, D-15, D-12, 405, 404.
- Vigente (no gate): QP-EX-2/EX-3 [O] sin cobertura; **QP-17 sin needle del hex del digest** (único [M] con matiz).
- Cerrada en esta remediación: QP-EX-1 (hueco S5-2), messages de field_too_long, verify del sha256 del header, higiene de dumps del smoke.

## (vi) Estado

**QA de C5: PASS sin FAIL — 2.ª corrida post-remediación (108/108 [L] + 13/13 [S])**. Verbatims completos en `qa-log.txt` (evidencia vigente = esta corrida).