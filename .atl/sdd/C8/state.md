---
project: Project_Log
change: C8
artifact: state
last_updated: 2026-10-28
status: closed
phase: archive
gate: approved 2026-10-26 (sin matices; D1 entra, D2-D3-D4 como recomendadas)
artifacts:
  explore: complete
  proposal: complete + GATE approved (D1 entra; D2/D3/D4 como recomendadas)
  spec: complete (59 req / 59 escenarios, 9 dominios; S8-1..S8-10 cerrados; vitest pin → design)
  design: complete (1020 líneas; 17 decisiones D-C8; tabla File Changes 42 filas, dif-0; pin vitest@5.0.5)
  tasks: complete (69 tareas T8.1–T8.69 TODAS hechas — T8.69 = commit docs(SDD) de cierre)
  apply-progress: complete (fases 0–10; hallazgos H-C8-B1..B5 + DEV-C8-1..7 anclados)
  qa-plan: complete (59 QP-8.x 1:1 con E8.1–E8.59; 2026-10-28)
  tests-written: complete (auditoría context-0; mvn 177 reconciliado; Vitest 55)
  qa-log: complete (qa-log.txt RAW verbatim, 6 secciones)
  qa-report: complete (0 CRITICAL; partición de 59 corregida en archive: 37+5+2+1+14)
  verify-report: complete (PASS-with-notes — 0 CRITICAL / 2 WARNING / 4 SUGGESTION; 2026-10-28)
  archive-report: complete (C8 CLOSED; ficha en 07; deudas → C9/C10 documentadas)
base_head: 5df6409
commits:
  - 1987754 fix(config) D1 UTF-8 (incl. desviación PanelAuthMvcTest ratificada)
  - ea62874 feat(frontend) panel React completo
  - d88d715 test(smoke) qa_c8_smoke.sh
  - cbffdd1 test(smoke) hallazgos ronda 1 (H-C8-B1 {"logs"})
  - dd26fa9 test(smoke) hallazgos ronda 2 (H-C8-B2..B5)
  - docs(SDD) cierre del corte (incluye este state.md + archive-report + ficha del 07)
notes: >
  Corte C8 (panel frontend React+TypeScript según maquetas) CERRADO
  2026-10-28. Gates: smoke 23 PASS/0 FAIL/1 BLOQUEADO-Q23 exit 0 (re-corrida),
  mvn 177/177, Vitest 55/55 + build verde, qa_c3 23/24 (E3.22 ajeno-D2),
  qa_c7 9/9 casos PASS (pre-fase ajena-D2), ws_echo 101 sin edición,
  frontera 41A+3D+8M dif-0 (verify check D), verify PASS-with-notes
  0 CRITICAL/2W/4S. MATIZACIÓN RATIFICADA DEV-C8-4: los DELETE C10 de hoy
  responden 401 real (cadena-3 denyAll en /error), no el 405/404 predicho
  por el spec — contrato del cliente §5 íntegro; UX: modal → 401 → logout
  hasta C10. NO push. C9/C10 se inician solo con orden del usuario.
---