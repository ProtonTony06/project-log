---
title: "Nota C10 (T10.33): el TRANSPORTE HTTP recorta los espacios extremos (OWS) de X-Confirm-Name — el «trim-igual-malo» por UI con espacios EJECUTA el DELETE (no 400); NBSP (U+00A0) es el único texto que da trim-pasa-gate + mismatch-real al servidor (validado)"
type: discovery
date: 2026-11-04
what: >-
  Los checks E10.6/E10.7 del arnés visual C10 (doble confirmación:
  «texto trim-igual-malo → 400 confirm_mismatch DENTRO del modal, nada
  borrado») asumían que fill «<NOMBRE>  » deja llegar un VALOR con espacio
  extremo al servidor y su comparación EXACTA lo rechazaría con 400.
  Empíricamente NO es así (repro con proyecto efímero qa-c10-repro-<ts>,
  traza de Playwright request): el click dispara REQ
  DELETE /api/v1/panel/projects/<id>/logs con X-Confirm-Name==nombre:True
  ==trim:True — y el RES es 200 (purga REAL: el resumen va a 0) en 8c y
  204 (delete REAL: ficha 404) en 8a. Causa: la normalización del VALOR
  de cualquier cabecera durante el transporte HTTP (RFC 9110 §5.1/§5.9
  — Chromium y el stack HTTP/1.1 recortan los OWS leading/trailing del
  field-value ANTES de que el servidor lo lea; el navegador también los
  recorta a su lado). Así que el TEXTO TECLEADO viaja TAL CUAL (el
  cliente lo cumple — G2-a/M1: panel.ts no re-trimea) pero el ESPACIO
  extremo jamá llega. El servidor compara EXACTO contra lo que RECIBE
  (comportamiento pinado por MvcTest/smoke con espacios INTERIORES →
  400) — el producto NO viola su spec: la PREMISA del check es la que no
  existe a nivel transporte. Cascada observada en la re-corrida del
  arnés: la ejecución accidental purgó/borró el proyecto A (e8b
  desaparece → E10.8 0 filas, E10.10 summary 404, E10.12 401 key muerta,
  E10.13 botón inexistente, E10.14 asserts post-borrado).
why: >-
  La doble confirmación tiene DOS comparadores distintos por design
  (gate frontend: coincideLiteralConTrim con trim(); servidor: equals
  EXACTO) — y el hueco observable «trim-pasa-gate PERO distinto al
  servidor» solo existe con caracteres que el trim() de JS quita pero el
  transporte HTTP no recorta. Candidatos del trim de JS: espacio/tab/LF/
  CR (OWS — recortados en transporte), U+000B vertical-tab (byte 0x0B
  prohibido en un header value — el browser aborta), BOM U+200B/U+3000
  etc. (>U+00FF — ByteString conversion throw), y U+00A0 (NBSP) — el
  ÚNICO viable. VALIDADO empíricamente (repro dedicado): 8c y 8a con
  fill «<NOMBRE> » → botón HABILITADO (el trim() del gate lo
  acepta) → click → 400 confirm_mismatch DENTRO del modal (literal
  LIT_MISMATCH + code + HTTP 400 con role=alert), modal abierto, sesión
  intacta y NADA borrado (total==3 antes/después por REST). En la UI
  real un NBSP es alcanzable (pegado de texto, IME...), así que este 400
  es un comportamiento REAL del producto — más valioso que el
  inalcanzable por espacios.
where: >-
  frontend/src/components/Modal.tsx (coincideLiteralConTrim, L9-11 —
  trim() de ambos lados) · frontend/src/pages/ProyectoDetalle.tsx
  (ModalsPurga/Eliminar — header con el texto tal cual) ·
  frontend/src/api/panel.ts (sin re-trim — G2-a) · comparación EXACTA
  pinned en backend MvcTests (interior spaces → 400) · repros
  /tmp/repro_c10_flow.py y /tmp/repro_nbsp_c10.py (creds en memoria S16,
  proyectos efímeros autolimpiados). Parche DEL ARNÉS a ratificar: en
  E10.6/E10.7 sustituir el fill por «NOMBRE + "\\u00a0"» (NBSP extrema, la secuencia de escape en código Python)
  — el resto de los asserts (gate/400/modal/sesión/intacto) queda
  idéntico; las variantes gate-desactivado de E10.7 (« NOMBRExxx») no
  cambian. NO tocar frontend/ ni backend/ para esto.
learned: >-
  (1) Los espacios extremos de un header value JAMÁS llegan al servidor
  (recorte definido en el transporte) — cualquier test que quiera
  «valor con espacio extremo rechazado por igualdad exacta» necesita un
  carácter NO-OWS que JS trim() quite (NBSP). (2) Comparadores
  asimétricos por design (trim vs exacto) = producto correcto con
  premisa de test imposible — el check que falla no siempre apunta al
  producto: aquí el repro de traza (REQ/RES con el valor real de la
  cabecera) distingió guion/producto en una corrida. (3) Los checks
  destructivos (purga/delete) pueden AUTODESTRUIR los datos que sus
  checks siguientes necesitan (E10.8-14 en cascada) — la destrucción
  accidental por premisa equivocada es más peligrosa que el FAIL
  limpio.