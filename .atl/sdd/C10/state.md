---
project: Project_Log
change: C10
artifact: state
last_updated: 2026-11-04
status: complete
phase: CIERRE DEL CORTE — apply COMPLETO (36/36 T10.1–T10.36, 6 bloques; T10.36 = commit PREPARADO SIN EJECUTAR esperando permiso del dueño) tras 2 re-anclajes (2 muertes de instancia, todo recuperado desde disco): Bloques 1–2 backend (mvn 223/223 → 225/225 con los 2 pins de preflight de D-C10-15), Bloques 3–4 frontend (tsc 0, Vitest 80→81 con pin del default del Reloj), Bloque 5 (qa_c10_smoke.sh 10/10 PASS exit 0; qa_vis_c10.py COMPLETADO — la 1.ª instancia lo dejó truncado a mitad de fichero — + wrapper qa_vis_c10.sh), Bloque 6 corridas canónicas (ver cifras abajo), verify PASS-with-notes (0 CRITICAL / 2 W / 5 S), ficha docs/07 «estado: hecho» 2026-11-04, archive-report en disco. Dos hallazgos de PRODUCTO remediados RATIFICADOS por el orquestador + una excepción ratificada a la frontera (D-C10-16) + parche del arnés b8: ver desviaciones.
  tasks: complete (36 tareas, 6 bloques; frontera = 37 ficheros FC#1–37 del design; intocables verificados dif-0 por verify: SecurityConfig/JwtAuthFilter/client.ts frontend/ingest/streaming/compose/VM/docs README/qa_vis_c9 (excepto hunk D-C10-16)/storeListado/App.tsx; commit docs(SDD) SELECTIVO preparado SIN ejecutar)
  design: complete (14 decisiones D-C10-1..14 + desviaciones D-C10-15/16 en runtime; M9 vía timerApertura T=10s + branch abiertoAlgunaVez; M10 400-antes-de-404; resumen 3 estamentos + SummaryDto record + ProjectDto 9.º componente NON_NULL; purga LogsBorradoRepository con re-findById solo si N==0; WS-cerrado delta frames 0; arnés qa_vis_c10.py import limpio de qa_vis_c9; File Changes 37 ficheros)
  spec: complete (13 áreas, 52 REQ-C10-*, ~70 escenarios; 10 matices M1–M10; GATE G1–G6 cerrado-en-mode-recomendado; el spec MANDA sobre design donde divergen — CASE orden bandas DEBUG=4/INFO=5 aplicado en backend/arnés/smoke)
qa: complete — cifras oficiales finales (qa-report.md; truth en qa-log.txt RAW):
  - mvn (builder podman maven:3.9-eclipse-temurin-21): 225/225 BUILD SUCCESS (Failures 0, Errors 0) — 177 heredados sin editar + 48 de C10 (incl. 2 pins MockMvc preflight real D-C10-15)
  - Vitest: 81/81 (9 files) · tsc --noEmit 0 errores (60 previos + 20 C10 + pin default Reloj)
  - smoke qa_c10: 10/10 PASS · 0 FAIL · exit 0 (ts 15:51; SQL real con siembra propia, JAMÁS down -v)
  - arnés visual qa_vis_c10: corrida FINAL 33 marcas · 0 FAIL · 1 BLOQUEADO (E9.7 variante B — canon honesto) · exit 1 = CANÓNICO del wrapper; 8 re-corridas hasta convergir (11·6 → 33·14 → 3 → 1 → 0); kill-window E9.11/E9.26 corrió; fase L siembra propia proys=0·logs=0·users=1·flyway=1 (1 proyecto del usuario INTOCADO); S16 RAW 0 hits; 39 PNGs evidencia/ con MANIFEST sha256 39/39 OK (NO commitear)
  - regresiones AL FINAL seriales, smokes sin editar: qa_c3 22 PASS · 2 FAIL · exit 1 (E3.22 N/A-post-D2 RATIFICADA; E3.24 = ARTEFACTO guard git-diff «verde tras commit» del fix CORS) · qa_c7 9/9 PASS · exit 2 (canon-documento C8 §4.5; «exit 0» de chat = errata reconciliada) · qa_c8 17 PASS · 6 FAIL · 1 BLOQUEADO Q23 (raíz ÚNICA: premisas C8 inválidas por diseño C10 ratificado, cero defectos producto; lectura recomendada N/A-post-C10 PENDIENTE-DE-RATIFICAR-POR-EL-DUEÑO)
verify: complete — PASS-with-notes 0 CRITICAL / 2 WARNING (W-1 RAWs de mvn/vitest desfasados pre-fix en disco — las 225/81 actuales descritas sin RAW propio; W-2 qa-log.txt conserva solo la corrida final) / 5 SUGGESTION; compliance 13/13 áreas, 56/56 REQ-comportamiento CON EVIDENCIA de ejecución
archive: complete (archive-report.md en disco; NO commitea)
commits: [0c7c320 feat(C10) producto — 33 ficheros · c6f3270 test(qa) arnés+smoke+RAW+notas — 11 ficheros · e7b192e docs(SDD) artefactos+ficha 07+doc 08 — 14 ficheros] — EJECUTADOS con permiso del usuario («Autorizar los 3», 2026-11-04); S16 staged 0 hits en los 3; SIN push; working tree residual solo con .atl/sdd/C10/evidencia/ · .atl/sdd/C9/evidencia/ · scripts/__pycache__/ (excluidos por diseño). Consecuencia: el guard git-diff E3.24 de qa_c3 verdea a partir de aquí (artefacto resuelto).
gate: GATE cerrado-en-modo-recomendado (usuario ratificó arranque C10 el 2026-10-29): G1-a CORS LATENTE ADOPTADA (CorsConfig lista por coma; SIN cambio de .env no cambia runtime) — variante (a+) añadir http://192.168.1.58:8080 al .env de la VM → PENDIENTE-DE-CONFIRMACIÓN-DEL-USUARIO (VM NUNCA tocada). G2-a texto tecleado en X-Confirm-*. G3-a timeout CONNECTING T=10s. G4-a summary NON_NULL + endpoint. G5-a arnés nuevo reutilizando maquinaria C9 (import LIMPIO constatado). G6 DEV-C8-4 ratificada (interceptor 401 intacto).
base_head: 27b62cb
artifacts:
  explore: complete
  proposal: complete
  spec: complete
  design: complete
  tasks: complete
  apply-progress: complete (Bloques 1–6 + fixes ratificados + corridas)
  qa-log-mvn-raw: qa-log-mvn-raw.txt (corrida 223 pre-D-C10-15 — ver W-1 del verify)
  qa-log-vitest-raw: qa-log-vitest-raw.txt (corrida 80 pre-pin Reloj — ver W-1 del verify)
  qa-log: qa-log.txt (RAW corrida FINAL del arnés; escrito SOLO por el wrapper)
  evidencia: evidencia/ — 39 PNGs + MANIFEST-sha256.txt (sha256sum -c 39/39 OK); NO commitear
  qa-report: complete (qa-report.md — cifras oficiales arriba + PENDIENTES-DEL-DUEÑO)
  tests-written: (C10 no usó el mini-qa-chain separado — QA integrado en Bloques 5–6 del tasks; la separación de autoriedad se mantuvo por instancias distintas apply/qa)
  verify-report: complete (PASS-with-notes; 0 CRITICAL/2 W/5 S)
  archive-report: complete
  notes: .atl/notes/c10-nota-orden-bandas-resumen-case-info-debug.md · c10-nota-crash-logs-ws-reloj-natives-desenlazados.md (+Resolución) · c10-nota-cors-preflight-x-confirm-wildcard-no-matcheado.md (+Resolución D-C10-15)
pendientes-del-dueño (no bloqueantes):
  1. PERMISO para los commits preparados (producto + qa + docs(SDD); git add -f para docs/08 y docs/07 — SIN tocar .gitignore)
  2. Ratificar lectura N/A-post-C10 de los 6 FAIL E8.x de la regresión qa_c8
  3. Fila stale tras borrado 8b en listados — opción abierta (expulsión por id / refetch con aviso / aceptar-documentar), sin pin del arnés
  4. Variante CORS-LAN (a+): añadir http://192.168.1.58:8080 al .env de la VM (VM intocada)
  5. E3.24 de qa_c3 verdea tras el commit del fix CORS (artefacto del guard git-diff)
desviaciones:
  - "D-C10-1..14: del design (aplicadas; D-C10-3 CASE corregido por spec+maqueta — nota orden bandas)"
  - "D-C10-15 (RATIFICADA orquestador): CORS setAllowedHeaders por LITERALES EXACTOS X-Confirm-Name/X-Confirm-Log-Id — DefaultCorsProcessor matchea por igualdad, la ortografía X-Confirm-* del spec no era materializable; REQ-C10-CORS-3/design L146 exigen el COMPORTAMIENTO (2 pins MockMvc de preflight real + verify en vivo 200/403)"
  - "D-C10-16 (RATIFICADA orquestador, excepción ÚNICA a frontera): 1 hunk de 7 inserciones en scripts/qa_vis_c9.py b6 (wait_for_timeout(600) tras wait_for_url antes de muestrear reqs_antes) — C9 «tocado en su guion por C10»; artefacto de ventana de muestreo, producto correcto"
  - "Fix-producto #1 (RATIFICADO): default del Reloj de ws/client.ts envolve natives (Illegal invocation — crash REAL de /logs, latente desde C8, activado por D-C10-7); Vitest 81/81 con pin"
  - "Operativa menor documentada (sin efectos en datos): un recreate de backend arrastró postgres/frontend (sin down -v, pgdata intacto — 4 tablas); down -v de Q13 en qa_c8 es canon del propio smoke (pgdata recreado; proyecto smoke-c8 id=1 desapareció con el VOLUMEN, no por el arnés)"
alcance: (entregado)
  - DELETE /projects/{id} (X-Confirm-Name, CASCADE transaccional) · DELETE /logs/{id} (X-Confirm-Log-Id) · DELETE /projects/{id}/logs (200 {deleted: N})
  - Resumen include=summary + GET /projects/{id}/summary (SummaryDto — tarjeta /proyectos y bloque ficha)
  - docs/08_ACCESO_REMOTO.md (Tailscale P1; TLS P2 documentado sin implementar)
  - Fixes H-C9-* (WS-COLGADO/E9.13/E9.2/E9.10) + UX-401-logout (status reales DELETE) + CorsConfig multi-origin latente
notes: >
  C10 cierra la Fase 1+2 (07 líneas 192–204) — ficha «estado: hecho» 2026-11-04.
  El QA del arnés pilló 2 defectos de producto que el suite no veía (rama
  constructor-default jamás ejercitada; test de CONFIGURACIÓN ≠ test de
  COMPORTAMIENTO en CORS). Próximo ciclo: Fase 3 (P2/TLS real, backups,
  retención) según 07 — sin renegociar literales. SI OTRA INSTANCIA DEBE
  REANUDAR: solo queda PENDIENTE-EJECUTAR el commit (pedir permiso al usuario)
  — todo lo demás está en disco y cerrado.