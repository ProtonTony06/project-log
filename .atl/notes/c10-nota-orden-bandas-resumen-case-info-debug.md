---
title: "Nota C10 (resumen por proyecto): el CASE del SQL ordena INFO antes que DEBUG, mientras la literal de maqueta-04/REQ-C10-UI-6 muestra «DEBUG 1 000 · INFO 24 118» (DEBUG antes)"
type: hallazgo
date: 2026-10-29
what: >
  Al implementar ResumenLogs (T10.16, D-C10-9) se detectó una divergencia
  de orden entre dos piezas del contrato que el design D-C10-3 trata como
  equivalentes: (a) el SQL CASE del resumen — FATAL=1, ERROR=2, WARN=3,
  INFO=4, DEBUG=5, ELSE=6 — produce la banda de severidad en el orden
  «FATAL · ERROR · WARN · INFO · DEBUG · TRACE»; (b) la literal de
  maqueta-04 y REQ-C10-UI-6 cita «FATAL 1 · ERROR 148 · WARN 63 · DEBUG
  1 000 · INFO 24 118» — con DEBUG ANTES de INFO. La UI (frontend) resuelve
  tomando UNA sola fuente del orden: el JSON del backend (LinkedHashMap del
  CASE) — el frontend JAMÁS reordena, para no crear una segunda fuente de
  verdad divergente.
why: >
  Si el arnés E10.x (o una revisión visual) compara la banda de la tarjeta
  contra la literal EXACTA de maqueta-04/REQ-C10-UI-6, con datos que tengan
  DEBUG e INFO simultáneos el orden observado será «… WARN · INFO · DEBUG»
  (vía CASE) y NO el de la maqueta («… WARN · DEBUG · INFO»). Solo difiere
  la posición relativa de DEBUG/INFO (y TRACE queda al final por ELSE=6);
  FATAL/ERROR/WARN — los niveles destacados que la maqueta pide ver de un
  vistazo — están en la MISMA posición en ambas fuentes, que es lo que el
  diseño quiere («el orden maqueta-04 sale del CASE en SQL»).
where: >
  design.md D-C10-3 (SELECT_RESUMEN_NIVELES, CASE) vs spec.md REQ-C10-UI-6
  (literal de maqueta-04) y docs/mockups/04-proyectos.html L92–96;
  frontend/src/components/ResumenLogs.tsx (comentario de cabecera del
  componente documenta la decisión de orden).
learned: >
  Decisión de apply (CLOSA del orquestador 2026-10-29 — T10.6 backend):
  MANDA la literal de maqueta-04/REQ-C10-UI-6 — el CASE del backend usa
  DEBUG=4 e INFO=5 (FATAL=1, ERROR=2, WARN=3, DEBUG=4, INFO=5, ELSE=6),
  de modo que la banda observada sea «FATAL · ERROR · WARN · DEBUG · INFO»
  tal cual la maqueta. Corrección aplicada EN EL CASE de
  LogsQueryRepository.SELECT_RESUMEN_NIVELES (main); pin actualizado en
  LogsQuerySqlTest (literal byte-a-byte + asserts WHEN 'DEBUG' THEN 4 /
  WHEN 'INFO' THEN 5); el frontend (ResumenLogs.tsx) NO cambia nada —
  renderiza el orden del JSON (LinkedHashMap del CASE), que ahora sale en
  orden maqueta-04. Divergencia spec-vs-design (transcripción de D-C10-3
  con INFO=4/DEBUG=5) resuelta a favor de spec+maqueta por decisión del
  orquestador; mvn 223/223 verde tras la corrección.
---