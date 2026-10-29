---
title: "Hallazgo C9 (E9.15): el banner 400 con field=level no es alcanzable por UI — D-C8-6 normaliza inválidos a null antes de fetch"
type: discovery
date: 2026-10-29
what: >
  El arnés visual C9 (corrida 2) fracasó en la variante (b) de E9.15 tal y
  como estaba implementada: navegar a /logs?level=PANICA y esperar en
  pantalla el banner 400 con code=filter_invalid_value Y «Campo culpable:
  level». Verificación en vivo: (1) el BACKEND sí emite el 400 correcto
  (curl: {code:filter_invalid_value, field:level} — literal intacto, R9.3.1
  / E8.26); (2) el FRONTEND JAMÁS envía ese valor al backend: la hidratación
  de la query `filtrosDesdeSearch` (useLogFilters.ts, contrato D-C8-6
  «query como fuente única, inválidos ⇒ null») normaliza level=PANICA a
  null ANTES del fetch, de modo que el montaje por URL aterraza en el
  listado normal SIN banner y SIN crash. Consecuencia: el sub-aserto de
  PANTALLA «banner con campo culpable=level» no es observables tal cual el
  literal R9.3.1 («variante filter_invalid_value CON field:'level'») lo
  sugiere, porque su superficie ratificada en C8 (qa-plan QP-8.26/E8.26,
  case_e8_23 del smoke) fue la REST DIRECTA (GET /panel/logs?level=ALGO →
  400+field), no la UI. La superficie sí produce EN PANTALLA banner 400 es
  la del filtro invertido from>to (E9.12-c: filter_from_to_inverted, SIN
  field) porque el form sí manda from/to crudos.
why: >
  No es un bug de producto: la normalización de inválidos es el contrato C8
  ratificado (D-C8-6) y ningún check del smoke/E8 la falsificó; es una
  CONFLICTO de superficie entre el literal de R9.3.1 (escrito en C9 con
  forma de URL) y la hidratación de C8. El arnés NO puede fingir PASS ni
  parchar producto; la resolución de apply (acotada, sin tocar producto) es
  OBSERVACIÓN DUAL: (b-API) fetch DENTRO del navegador con el Bearer de la
  sesión → 400 + code + field=level confirmados (superficie C8 exacta,
  evaluate solo booleanos — S16); (b-UI) navegación real por
  /logs?level=PANICA → banner AUSENTE y panel vivo (sin crash). El
  veredicto resultante es PASS-CON-NOTA con el sub-aserto de pantalla
  field=level BLOQUEADO-anotado (R9.6.4) y la nota impresa en qa-log. EL
  ORQUESTADOR decide si (futuro corte) el literal R9.3.1 se matiza a
  superficie dual como este hallazgo — el spec C9 no se edita.
where: >
  frontend/src/hooks/useLogFilters.ts (filtrosDesdeSearch — LECTURA),
  scripts/qa_vis_c9.py (sección E9.15, observación dual), .atl/sdd/C9/
  qa-log.txt (veredicto E9.15), .atl/notes/ (esta). Producto SIN cambios
  (dif-0 verificado en T9.19).
learned: >
  Al traducir una pareja del qa-plan a un arnés visual, pinar la SUPERFICIE
  en la que la pareja fue asertada en su corte original (aquí REST,
  E8.26) antes de elegir una superficie UI no ratificada: una aserción de
  pantalla que el diseño ratificado hace inalcanzable no es un defecto de
  producto ni un falso FAIL — es una decisión de observación que hay que
  documentar (dual surface + BLOQUEADO-anotado del sub-aserto).