---
change: C5
artifact: archive-report
last_updated: 2026-10-25
status: closed
commits:
  - 3f707ba feat(ingest): cadena 1 real — filtro de API key + POST /ingest y /ingest/batch (1..200, all-or-nothing, 202 accepted:N) (C5)
  - 213735c test(smoke): scripts/qa_c5_smoke.sh codificando E5.21–E5.33 [S] (C5)
  - docs(SDD): commit de cierre que incluye este informe (hash en `git log` a partir de 213735c)
---

# Archive Report — C5 · Ingesta REST

Cierre del corte C5 (modo `atl`; audit trail en `.atl/sdd/C5/`). Veredicto:
**PASS-with-notes** — 0 CRITICAL / 6 W / 7 S. Base: HEAD 6cc609f + código C5
commiteado por este archive.

## Qué quedó implementado

- Paquete `com.projectlog.ingest` (10 ficheros: el `package-info.java`
  marcador R5.1 preexistente + 9 nuevos): `IngestController` (POST
  `/api/v1/ingest` y `/ingest/batch`, gates 0/3/lote, 202
  `{accepted: N}`), `IngestGuardFilter` (barrera 1 Content-Length, barrera 2
  streaming con frontera exacta, sha256 de la key en el filtro + findByApiKeyHash,
  unwrap de FrameworkServlet), `IngestAuthenticationEntryPoint` (401 honesto
  `api_key_invalid`), `IngestService` (validación fail-fast completa ANTES de
  received_at/insert, UNA pasada con parse+serialización única), `LogsRepository`
  (INSERT multi-VALUES + RETURNING id, `CAST(? AS jsonb)`), `IngestListener`
  (seam void onLotePersistido, llamado tras el RETORNO del insert en método no
  transaccional — COMMIT estructural, listo para streaming C7), `IngestPrincipal`,
  `LogIngestRequest` (JsonNode, sin DTOs de lote) y el record `LogValidado`
  (moneda del INSERT, 8 componentes).
- `ProjectsRepository`: `findByApiKeyHash(byte[] hash)` ADITIVO (+1 constant
  `SELECT_POR_API_KEY_HASH` package-private + `@Transactional` de defensa,
  R5.11-a); G-1 de C4 NO reabierta (regenerate/RETURNING sin diff).
- `SecurityConfig`: cadena 1 REAL DE INGESTA (`.addFilterBefore` del guard,
  `authenticated()`, entry point propio en ambos papeles, sin `cors()`);
  `denyAll` temporal REEMPLAZADO; cadenas 2/3 y panel byte-a-byte intactos
  (el constructor NO entra en ningún hunk del diff).
- `application.yml`: `ingest.max-request-bytes` (default `1MiB`, D-6
  normalizador) y `max-batch-size` (default 200), R5.14.
- Tests [L]: 31 nuevos en 6 suites (`IngestControllerMvcTest` 16,
  `IngestGuardLimitTest` 3, `IngestLogHygieneTest` 1, `IngestServiceTest` 4 —
  QP-EX-1 incluido, `LogsRepoSqlTest` 4, `ProjectsIngestSqlTest` 3) + 77
  heredados C1–C4 re-corridos sin tocar un assert.
- Smoke: `scripts/qa_c5_smoke.sh` (E5.21–E5.33, 13/13). Hueco G-2 de C4
  **CERRADO ACTIVAMENTE** con E5.31 (PATCH ACTIVE + la MISMA key2 sin
  regenerate intermedio → 202).

## Evidencia QA

- QA global **37/37 PASS, 0 FAIL** (2.ª emisión post-remediación;
  qa-log.txt vigente; QP-EX-1/E5.34 IN; QP-16b heredados en la misma corrida).
- `mvn test` en builder: **108/108** (Failures 0, Errors 0, BUILD SUCCESS)
  = 31 C5 + 77 C1–C4.
- Smoke canónico: **13/13 PASS (E5.21–E5.33), exit 0** (run 6), recomposición
  única `--build --force-recreate`, sin `down -v` (pgdata intacto).
- Higiene **REMEDIADO** (loop-back apply, constatado en la corrida): dumps
  fuera del tmpdir 700 eliminados (`/tmp/qa_c5_req_debug.log` y
  `/tmp/qa_c5_curl_err.log` NO existen tras la corrida), **0 secretos en
  outputs** (grep `plk_`/`eyJ` = 0 en ambas salidas canónicas), keys de
  smoke revocadas post-corrida (trío NULL verificado con RETURNING + SELECT,
  0 keys vivas al final).

## Checks del verify (A–G): todos PASS

A diff-0 de intocables (ÚNICA modificación de config permitida: SecurityConfig
con EXACTAMENTE 2 hunks — imports/Javadoc de clase + bean `ingestChain` y
bean-higiene `filtroGuardSoloCadena1`; constructor byte-a-byte, panelChain/
defaultChain/common FUERA de cualquier hunk; ProjectsRepository aditivo puro
30+/0−; resto CLEAN, `git diff --exit-code` en cada conjunto); B CorsConfig sin
`X-API-Key` (cierre S7 de C4 mantenido) y cadena 1 sin `cors()`; C ausencias
(sin DTOs JsonNode-extra, sin deps nuevas, sin migraciones, sin loggers,
exactamente los 11 codes de R5.10, sin DIVE, G-1 no reabierta); D semántica
spot-check 9/9 (orden 413→401→403→400, sha256 en el filtro con cero JWT,
received_at UNA vez común, multi-VALUES+RETURNING, all-or-nothing, 202 tras
COMMIT con seam tras el retorno, bind D-6, shouldNotFilter D-8, unwrap D-15);
E trazado de los 10 Done de proposal §6 evidencia a ID; F QA como evidencia
sin manipulación; G higiene de secretos (0 `plk_`/`eyJ` reales en qa-log).

## Hallazgos (copiados del verify-report)

- **W-1/W-2 (D-14, RATIFICADA en gate 2026-10-25)** — los 2 beans de ingesta
  entran a `ingestChain` como `ObjectProvider` con lambdas delegantes lazy, y
  el ctor del guard resuelve `ApiKeyService`/`ProjectsRepository` lazy en
  `doFilterInternal`; sin ello los slices `@WebMvcTest` de C1–C4 estallaban
  (46 errores de carga, assert-level 0). Semántica R5.6 conservada.
- **W-3 (D-13, RATIFICADA)** — bean-higiene `filtroGuardSoloCadena1`
  (`FilterRegistrationBean…setEnabled(false)`) fuera del alcance literal
  «diff solo ingestChain + Javadocs»: solución mínima del hallazgo E2E E5.28
  (la passe global consumía el marker de OncePerRequestFilter).
- **W-4 — Código sin commit RESUELTO**: los commits de este archive (feat,
  smoke, docs SDD) dejan el working tree limpio.
- **W-5 — Package-private**: `MSG_API_KEY_INVALID` y
  `SELECT_POR_API_KEY_HASH` son package-private donde R5.10/R5.11 decían
  `private` (pin D-9 y 401-honesto D-2 sin reflection; inconsistencia
  tipográfica del spec, resuelta por design D-9/S5-11).
- **N-4 — 2 `@Transactional` en el diff** (`LogsRepository.insert` +
  `findByApiKeyHash` defensiva, PREVISTA por R5.11-a): NINGUNA envuelve al
  seam ni al service — el punto de fondo de D-4 (seam tras-COMMIT
  ESTRUCTURAL) se cumple; "única" de R5.12 se refiere al scope del lote.
- **S-1 — NUEVA deuda única del corte**: nginx `client_max_body_size` NO
  definido en el frontend (default 1 MiB); los 3 cuerpos >1 MiB del smoke
  (E5.27/E5.28/E5.33) viajan por ruta directa al backend (desviación E2E
  documentada del script) → deuda **C9**.
- **S-2 (D-14)** — ObjectProvider es degradación honesta: patrón a recordar
  si C6/C7 añaden beans de seguridad a la cadena 1.
- **S-3** — ciclo de keys del smoke: la revocación post-corrida fue MANUAL;
  incluir la revocación final EN el script en C6/C7 (0-manual).
- **S-4** — S-3 heredada de C4 (needles LIKE con `\_`/`position()`):
  vigente como convención.
- **S-5** — 401 ISO-8859-1 (deuda HEREDADA de C2/C4) sigue abierta para
  C8/C9; NO aplicable a la superficie de ingesta de C5 (needles ASCII-safe).
- **S-6** — typo cosmético "ObjecProvider" en comentario del filtro (L82):
  a corregir en touch-up oportunista de un corte posterior.
- **S-7** — QP-EX-2/EX-3 [O] sin cobertura (shouldNotFilter fuera de
  `/api/v1/ingest/**`; opcionales `""` capturados como `""`), plan aceptado.

## Deudas RETENIDAS (heredadas C3/C4, solo se anotan)

- Smoke `docker compose` completo a re-verificar en C9 (heredado de C3/C4).
- 401 ISO-8859-1 en C8/C9 (heredado de C2).
- Hueco G-1 de C4 (rama RETURNING vacío→404 sin test): NO reabrible, vigente
  como opcional; G-2 CERRADO ACTIVAMENTE con E5.31 en este corte.

## Deudas NUEVAS del corte

- Nginx `client_max_body_size` (S-1) → C9. NINGUNA otra.