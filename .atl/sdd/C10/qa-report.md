---
project: Project_Log
change: C10
artifact: qa-report
last_updated: 2026-11-04
status: complete
results: "32/33 PASS · 1 BLOQUEADO (E9.7 variante B — su canon honesto) · corrida final ts siembra 20261104165000"
commit: 27b62cb (base_head del corte; SIN commits de C10 a esta fecha — solo working tree)
log: qa-log.txt
---

# QA-report — C10 · corridas canónicas finales

Truth completa en `qa-log.txt` (RAW teed por el wrapper, jamás editado a mano).
Desglose de 33 marcas: 18 re-runs honestos (b1 E9.2 / b3 E9.10 / b6 E9.13 del
módulo C9) + 15 checks E10.x. Evidencia: 39 PNGs en `evidencia/` (NO commiteados)
con `evidencia/MANIFEST-sha256.txt` (`sha256sum -c` VERIFICADO).

## Suites (cifras oficiales)

| Suite | Comando | Resultado | Observación |
|---|---|---|---|
| mvn | builder podman (maven:3.9-eclipse-temurin-21) `mvn test` | **225/225 · BUILD SUCCESS** (Failures 0, Errors 0) | 223 de C10 (177 heredados sin tocar + 46) + 2 pins NUEVOS de preflight real (D-C10-15); RAW `qa-log-mvn-raw.txt` |
| Vitest | `npx vitest run` + `npx tsc --noEmit` | **81/81** (9 files) · tsc 0 errores | 60/55 previos sin relajar + 20 C10 + pin del default del Reloj (fix ratificado); RAW `qa-log-vitest-raw.txt` |
| Smoke qa_c10 | `bash scripts/qa_c10_smoke.sh` | **10/10 PASS · 0 FAIL · exit 0** (ts 15:51) | SQL REAL: purga N (`{"deleted":45}`/`0`), CASCADE, key muere (`401 api_key_invalid`), key sobrevive a purga (202), resumen vivo |
| Arnés visual | `bash scripts/qa_vis_c10.sh` | **33 marcas · 0 FAIL · 1 BLOQUEADO (E9.7 variante B) · exit 1 = CANÓNICO del wrapper** (solo 33/0/0 sería 0) | 8 re-corridas hasta convergir (11·6 → 33·14 → 3 → 1 → 0); S16 RAW: **0 hits**; kill-window E9.11/E9.26 corrió; fase L siembra propia: proys=0 · logs=0 · users=1 · flyway=1 (canon C3) |
| Regresión qa_c3 | `bash scripts/qa_c3_smoke.sh` | **22 PASS · 2 FAIL · exit 1** (serial, sin `down -v`) | E3.22 = N/A-post-D2 (ratificada); **E3.24 = ARTEFACTO, no regresión** (ver abajo) |
| Regresión qa_c7 | `bash scripts/qa_c7_smoke.sh` | **9/9 PASS · exit 2** | patrón ya documentado en qa-report C8 §4.5; el «exit 0» del canon de chat es ERRATA — manda el documento (reconciliado); sin down -v |
| Regresión qa_c8 | `bash scripts/qa_c8_smoke.sh` | **17 PASS · 6 FAIL · 1 BLOQUEADO (Q23)** | su Q13 hizo `down -v` (pgdata recreado — el proyecto del smoke C8 id=1 desapareció con el VOLUMEN, no por el arnés); RAW en qa-log.txt |
| Corrida 1 | `bash scripts/qa_vis_c10.sh` (ts 20261104155255) | 11 · 6 FAIL · exit 1 | hallazgo PRODUCTO #1 (Reloj) |
| Corrida 2 | `bash scripts/qa_vis_c10.sh` (ts 20261104160223) | 33 marcas · 14 FAIL · 1 BLOQUEADO · exit 1 | hallazgo PRODUCTO #2 (CORS) + guion |

## Veredictos — arnés C10 (detalle en qa-log.txt)

**Re-runs honestos (18)** E9.1–E9.18: todos PASS/PASS-CON-NOTA (E9.2 VERDE en
vivo con URL perdurando — D-C10-11; E9.10 WsContador delta 1 — D-C10-12; E9.13
NEEDLE_E35 con la ventana establecida — D-C10-16). **E10.x (15)**: E10.1–E10.15
todos PASS/PASS-CON-NOTA: resumen tarjeta/ficha fiel al REST vivo con el orden
del CASE (nota orden bandas), «Actualizar» refresca, «sin logs todavía» (M4),
archivado resumen-intacto, NBSP extremo → 400 `confirm_mismatch` dentro del
modal sin logout ni borrado (G6), sentencia literal V11, post-204/200 de flujo 8
con banners literales, delta WS 0 tras purga (LiveBroadcaster solo emite con
ingesta) + refetch vacío, key sobrevive a purga (202 vivo), key muerta con la
fila (8a → 401), **E10.15 preflight CORS real: ACR-Headers x-confirm-name →
ACAO reflejada EXACTA + ACAM DELETE (post-D-C10-15)**.

**1 BLOQUEADO**: E9.7 variante B — su canon honesto del guion (no defecto).

## Interpretación (FAILs y pautas)

- **qa_c3 22·2·exit 1**: E3.22 = N/A-post-D2 (**ratificada**). **E3.24 nueva =
  ARTEFACTO, no regresión**: su guard de git-diff no verdea porque
  `CorsConfig.java` lleva el fix D-C10-15 SIN COMMIT todavía — **condición
  «verde tras commit»** (el QA nunca commitea). PENDIENTE-DEL-DUEÑO el commit.
- **qa_c7 9/9 · exit 2**: patrón «/ws-console.html no publicado» ya documentado
  post-D2 (qa-report C8 §4.5) — pauta **pre-fase = N/A-post-D2**; sin
  diferencia con el canon-documento.
- **qa_c8 17·6·exit 1**: **raíz ÚNICA de los 6 FAIL = premisas C8 invalidadas
  POR EL DISEÑO C10 RATIFICADO — CERO defectos de producto**: E8.38/E8.47 son
  destructivos REALES hoy; E8.14/15 esperaban 405/401/404 y reciben 400
  `confirm_mismatch` (estructura obligatoria de doble confirmación); E8.39
  esperaba «sin summary» y C10 añadió el summary; colateral: E8.27 — la key
  muerta por el DELETE real de E8.38. **LECTURA RECOMENDADA: N/A-post-C10**
  (espejo de la pauta E3.22) — **RECOMENDACIÓN PENDIENTE-DE-RATIFICAR-POR-EL-
  DUEÑO, no bloqueante del corte**.
- **Q23**: manual tolerado (1 BLOQUEADO en qa_c8 y en el arnés si aplica) — canon.

## Desviaciones del corte

- **D-C10-15** (producto → fix RATIFICADO por el orquestador): CORS
  `setAllowedHeaders` por literales exactos `X-Confirm-Name` /
  `X-Confirm-Log-Id`; la ortografía `X-Confirm-*` del spec/design no era
  materializable en `DefaultCorsProcessor` (match por igualdad, solo el `*`
  completo; nota + Resolución en `.atl/notes/c10-nota-cors-preflight-x-confirm-wildcard-no-matcheado.md`).
- **D-C10-16** (excepción PUNTUAL ratificada a la frontera): UNA edición en
  `scripts/qa_vis_c9.py` — bloque b6, `wait_for_timeout(600)` tras
  `wait_for_url` antes de muestrear `reqs_antes` (diff verificado: 1 hunk de 7
  inserciones, NADA más; S16 0) — **C9 queda «tocado en su guion por C10»**;
  justificación: artefacto de ventana de muestreo del guion (producto correcto).
- **Product-fix #1 ratificado**: default del `Reloj` de `ws/client.ts` envuelve
  natives (`Illegal invocation` — crash REAL de /logs desde C10, latente desde
  C8; nota + Resolución en `.atl/notes/c10-nota-crash-logs-ws-reloj-natives-desenlazados.md`).
- **Operativa menor** (documentada, sin efecto en datos): un recreate de
  backend arrastró a postgres/frontend (sin `down -v`, pgdata intacto); y el
  `down -v` del propio Q13 de qa_c8 es canon de ese smoke.
- **Previas del corte**: orden de bandas DEBUG=4/INFO=5 (nota
  `c10-nota-orden-bandas-resumen-case-info-debug.md` — CASE corregido en backend).

## Hallazgos guion vs producto (del bucle T10.33)

- **GUION** (parcheados acotadamente + re-corrida): cifras fantasma E10.1/2/3/5
  (re-anclaje al REST vivo), NBSP E10.6/7, RESIDUO hardcodeado del informe b8,
  compile-fix/import de la instancia posterior, ruta-anidada del fichero de id
  y distribución de siembra S1 del smoke.
- **PRODUCTO** (2, ambos remediados RATIFICADOS por el orquestador):
  Reloj desenlazado (crash /logs) y CORS allowHeaders (403 preflight en
  cliente cross-origin).

## PENDIENTES-DEL-DUEÑO (no bloqueantes del corte, NO ratificados)

1. Commit del fix CORS (el guard E3.24 de qa_c3 verdea después del commit).
2. Ratificar la lectura **N/A-post-C10** de los 6 FAIL E8.x de la regresión qa_c8.
3. Fila stale tras borrado 8b en listados — opción abierta (expulsión por id /
   refetch con aviso / aceptar-documentar), sin pin del arnés.
4. Canon de chat vs documentos: reconciliado AL DOCUMENTO (exit 2 de qa_c7).

## Higiene

S16: grep `plk_`/`eyJ` == 0 hits en TODOS los RAW (mvn, vitest, qa-log.txt).
Evidencia PNG: `.atl/sdd/C10/evidencia/` NO commiteada (sha256 en MANIFEST).
`scripts/__pycache__/` fuera de cualquier commit. Sin commits, sin push.

## Comandos exactos ejecutados

```
podman run --rm -v /home/acuelop369/Proyectos/Project_Log/backend:/ws:Z \
  -v /home/acuelop369/.m2:/root/.m2:Z -w /ws \
  docker.io/library/maven:3.9-eclipse-temurin-21 mvn test
npx tsc --noEmit && npx vitest run        # desde frontend/
bash scripts/qa_c10_smoke.sh
bash scripts/qa_vis_c10.sh
bash scripts/qa_c3_smoke.sh && bash scripts/qa_c7_smoke.sh && bash scripts/qa_c8_smoke.sh
sha256sum .atl/sdd/C10/evidencia/*.png && sha256sum -c .atl/sdd/C10/evidencia/MANIFEST-sha256.txt
```