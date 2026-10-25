---
project: Project_Log
change: C6
artifact: archive-report
last_updated: 2026-10-25
status: closed
commits:
  - 78544b2 feat(query): consulta GET /logs con keyset + filtros combinables + GET /logs/{id} detalle (cursor r,id con micros preservados, LIMIT ? + 1, escape ILIKE, codes 400 del vocabulario) (C6)
  - 2b2f1d8 test(smoke): scripts/qa_c6_smoke.sh codificando E6.16–E6.23 [S] (C6)
  - docs(SDD): commit de cierre que incluye este informe + ficha C6 hecha + S-3 cerrada en 07_SLICING_PLAN (hash en `git log` a partir de 2b2f1d8)
---

# Archive Report — C6 · Consulta keyset + filtros + detalle

Cierre del corte C6 (modo `atl`; audit trail en `.atl/sdd/C6/`). Veredicto
global: **PASS-with-notes** — 7/7 checks A–G OK, 0 desviaciones NO ancladas,
2 desviaciones ANCLADAS + 2 matices ratificados. Base: HEAD `43ea041` +
código C6 commiteado por este archive. Cero ficheros M/D sobre C1–C5
(frontier diff-0: todo el corte son añadidos `??`).

## Qué quedó implementado

- Paquete `com.projectlog.query` (8 ficheros; `package-info.java` marcador
  preexistente intocado): `LogsController` (GET `/logs` y `/logs/{id}`),
  `LogsQueryRepository` (SELECT_LISTADO byte-exacto R6.16 — 17 `?`, 7
  CASTs, guard keyset `(received_at < ? OR (received_at = ? AND id < ?))`,
  `ORDER BY received_at DESC, id DESC`, `LIMIT ? + 1` — y SELECT_POR_ID de
  11 columnas), `CursorCodec` (token `{r,id}` con micros preservados —
  paso 5c guard-Z incluido), `FiltrosConsulta` (escape ILIKE: `\` primero,
  matriz `_`+`\`, `""` → `%%`), DTOs `LogListItemDto` (9 componentes, sin
  exception/metadata), `LogDto` (11) y `LogsPaginaResponse`
  (`{items, next_cursor}` con null EXPLÍCITO).
- Tests [L]: 38 nuevos en 5 suites (`LogsControllerMvcTest` 17,
  `DtosLogsPinTest` 5, `LogsQuerySqlTest` 4 — pin cuantitativo 17/1/3/3,
  `CursorCodecTest` 6 — pin P-1 byte-exacto + anti-truncado,
  `FiltrosConsultaTest` 6) + 108 heredados C1–C5 re-corridos.
- Smoke: `scripts/qa_c6_smoke.sh` (E6.16–E6.23, 8/8 PASS, exit 0) —
  recomposición única `--build --force-recreate`, NUNCA `down -v`, volumen
  intacto (conteos relativos, deltas R-6.h).
- Deuda **S-3 de C4 CERRADA**: aguja `_` no-comodín E6.18 (conteo==1 con
  contraguja misma-length discriminante) + E6.13/`FiltrosConsultaTest`
  (función); anotada en la ficha C4 de 07_SLICING_PLAN.

## Evidencia QA

- `mvn test` en builder (maven:3.9-eclipse-temurin-21, patrón C1–C5):
  **146/146** (Failures 0, Errors 0, skipped 0, BUILD SUCCESS) = 38 C6 +
  108 heredados.
- Smoke canónico: **E6.16–E6.23 8/8 PASS, exit 0** (qa-log.txt §2; higiene
  de secretos 0 hits reales de `plk_`/`eyJ`; pila UP limpia tras la
  corrida).
- QA: tabla QP-6.1–6.23 **23/23 PASS** (15 [L] + 8 [S]) + pre-fase/pareja
  12 PASS — sin huecos, sin FAIL, sin BLOQUEADO.
- Loop-back H1–H9 CERRADO por apply y VISIBLE en la corrida: H1 (r futuro
  → 200 vacío), H2 (to-check-6-vs-7 en el fail-fast), H7 (message
  verbatim E6.22), deltas H4–H6, H9 (contraguja misma-length E6.18).

## Matices / desviaciones ratificadas (para el spec, si se re-emite)

1. **H9** — contraguja E6.18 misma-length `smoke-c6-<TS>-timeout`
   RATIFICADA (la del plan, sin `timeout`, no discriminaba; delta del plan
   2 → +3 en el smoke).
2. **H8 / P-3** — premisa R6.15-b («`Instant.parse` rechaza `+02:00`»)
   EMPÍRICAMENTE FALSA en JDK moderno (≥12 `ISO_INSTANT` normaliza
   offsets); el contrato se mantiene 400 `filter_cursor_invalid` vía
   guard-Z del paso 5c, pinado por
   `offsetNoUtcEsRechazadoAunQueInstantParseLoAceptaria`. INFO spec-side.
3. **D-S6-a / D-R6.1** — inconsistencia interna del spec §1.G: R6.17 y
   E6.11 dicen «16 placeholders» pero el LITERAL R6.16 y su prosa dan
   **17** (cursorR en 3 posiciones). Implementa el literal; ANCLA:
   design D-S6-a/D-R6.1.
4. **Smoke `--build` vs R6.27** — la recomposición usa
   `up -d --build --force-recreate` (necesario: sin rebuild los
   contenedores quedan con la imagen de C5 sin /logs). Fortalece el
   smoke; NUNCA `down -v` se mantiene.

## Deudas RETENIDAS / heredadas (sin acción en C6)

- `nginx client_max_body_size` → **C9** (heredada de C5).
- Smoke de docker compose canónico → **C9** (heredada de C3/C4).
- 401 ISO-8859-1 → **C8/C9** (heredada de C2).
- Cortesía documentada: R-6.i divergencia de render (fechas del DTO a
  segundos por formatter C2 vigente; el cursor preserva micros — P1),
  cubierta por E6.20.

## Deudas NUEVAS del corte

- NINGUNA.

## Cierre

Ficha C6 en `docs/07_SLICING_PLAN.md` actualizada a
`estado: hecho 2026-10-25`; S-3 de C4 cerrada en su ficha. Push de `main`
a `origin/main` ejecutado por este archive (incluye cortes acumulados si
los hubiera).