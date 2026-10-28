---
project: Project_Log
change: C8
artifact: archive-report
last_updated: 2026-10-28
status: closed
commits:
  - 1987754 fix(config): 401 del panel en UTF-8 — setCharacterEncoding antes de getWriter (+ simetría ingest), test del header y del ó (C8)
  - ea62874 feat(frontend): panel React según maquetas — 6 pantallas + flujo 2, guard GET /me, cliente API tolerante, WS en /logs con dedup y backoff, modal 8b V11, base absolute (C8)
  - d88d715 test(smoke): scripts/qa_c8_smoke.sh — servido SPA/base absoluta, auth y charset del panel, CRUD + keyset + regenerate, negativos C10 (405/404), WS batch byte-exacto y 403 Origin (C8)
  - cbffdd1 test(smoke): bucle acotado de hallazgos QA ronda 1 — semillas con clave "logs" (canon C5 QA y backend CAMPO_LOGS), guards || true de set -uo pipefail (C8)
  - dd26fa9 test(smoke): bucle acotado ronda 2 — 401 real de C10/post-me (cadena-3 denyAll en /error), lookup del detalle por needle, campo_json_nul canon C3, 403 evil canon E7.9, E8.27 URL_BATCH y detalle SIN summary (C8)
  - docs(SDD): commit de cierre que incluye este informe + ficha C8 hecha en 07_SLICING_PLAN (hash en `git log` a partir de dd26fa9)
---

# Archive Report — C8 · Frontend del panel según maquetas

Cierre del corte C8 (modo `atl`; audit trail en `.atl/sdd/C8/`). Veredicto
global: **PASS-with-notes** — 8/8 checks A–H OK, **0 CRITICAL / 2 WARNING /
4 SUGGESTION** (verify 2026-10-28). Base: HEAD `dd26fa9` + cadena de commits
`1987754 → ea62874 → d88d715 → cbffdd1 → dd26fa9` + el commit docs(SDD) de
este cierre. Frontera R8.3 verificada dif-0 por el verify (check D): la
pila de cambios contra `5df6409` es **41 A + 3 D + 8 M exactos**, 1:1 con la
tabla File Changes del design §4; todos los intocables (backend/main salvo
2 entrypoints de D1, docs/mockups, smokes qa_c2–qa_c7 + ws_echo_test.py)
en null-diff; higiene S16 por grep limpia (0 secretos impreso en capturas).

## Qué quedó implementado

- Panel React + TypeScript en `frontend/` — 6 pantallas según maquetas
  (login 01, logs 02 con filtros combinables/keyset/banda en vivo, detalle
  03 con stacktrace mono + modal 8b V11 sentencia-literal, proyectos 04/05
  con bloque API key + revelación única, nuevo/editar SIN maqueta por
  AppFlow flujo 2), router guard vía `GET /me` (R8.6), base absoluta
  (`/` y `/api/v1` — R8.7), cliente API tolerante por status (§5: parseo
  tolerante, JAMÁS fingir éxito, 401→interceptor logout+redirect sin
  bucle), WS en `/logs` con dedup por id y backoff, errores globales §5
  y banner 400 con campo culpable.
- Backend ÚNICAMENTE los 2 entrypoints de D1 (RATIFICADO): charset UTF-8
  en el 401 del panel (`RestAuthenticationEntryPoint` —
  `setCharacterEncoding` ANTES de `getWriter`) + simetría en ingest;
  `EntryPointCharsetTest` 3 @Test (2×Panel401 + 1×Ingesta401 — cifra
  R8.50 reconciliada: la T8.62 decía «+2», era un error de transcripción
  corregido en tasks).
- 3 DELETEs de docs/mockups retirados (D2/D-C8-17 aprobada):
  `/mockups/04`, `/mockups/05`, `/ws-console.html` (copias dentro del
  frontend; maqueta 04/05 canónica sigue servida por nginx desde
  docs/mockups — verify §B).
- Smoke propio: `scripts/qa_c8_smoke.sh` — autocontenido (Q13: `down -v`
  + `up -d --build` — ÚNICO guion autorizado a destruir volúmenes),
  E8.1–E8.59 [S] con marca() E8.n y tmpdir 700 + trap.
- Tests [L] Vitest: 55/55 en 6 ficheros (reparto real 7/15/5/9/11/8 —
  difiere del §4 previsto, matiz anclado en tests-written) +
  `npm run build` verde (tsc strict + vite, 68 módulos).

## Bucle acotado de hallazgos (T8.64) — CERRADO en 2 rondas

- Corrida 1 (HEAD d88d715): exit 1 — 13 FAIL / 10 PASS / 1 BLOQUEADO.
  **CERO bugs de backend** (mvn 177/177 paralelo + sondas REST lo
  descartan): todos los FAIL eran del guion o de predicciones del spec
  sin respaldo en canon.
- Ronda 1 → `cbffdd1`: H-C8-B1 (semillas `{"batch":…}` → `{"logs":…}`,
  canon C5/CAMPO_LOGS) + guards `|| true` de `set -uo pipefail`.
- Ronda 2 → `dd26fa9`: H-C8-B2 (405/404 del spec FALSIFICADO — la ruta
  no mapeada despacha a `/error` y reentra en la cadena-3 denyAll de
  C2-S4 → 401 real {invalid_token}; POST /panel/me ídem), H-C8-B3
  (filtro C6 `text` matchea SOLO MESSAGE — lookup del detalle por
  needle del message con correlation_id de la fila verificado), H-C8-B4
  (`campo_json_nul` canon C3 para `next_cursor`/`api_key_prefix`
  explicit-null), H-C8-B5 (403 evil = canon E7.9 SOLO-status; el cuerpo
  es «Invalid CORS request» 26 bytes, NO vacío) + E8.27 URL_BATCH +
  detalle SIN summary (canon C3, 8 claves exactas).
- Re-corrida (parches aplicados sin commit, declarado en qa-report):
  **exit 0 — 23 PASS / 0 FAIL / 1 BLOQUEADO (Q23**, demo visual C9).
- Todo anclado en `.atl/notes/c8-smoke-401-denyall-error-dispatch-campojson.md`.

## Matices / desviaciones ratificadas (para el spec, si se re-emite)

1. **DEV-C8-4 (WARNING, ratificada por el orquestador siguiendo el
   precedente DEV-C8-1)** — la predicción «los DELETE de C10 (hoy
   inexistentes) responden 405/404 TAL CUAL» (S8-2/R8.36/R8.44/E8.38/
   E8.47, 5 lugares) es FALSIFICADA por el sistema real: error dispatch
   a `/error` reentra cadena-3 denyAll → **401**. El contrato del
   cliente §5 es agnóstico del status y queda ÍNTEGRO. Consecuencia UX
   anclada: hasta C10, confirmar un modal 8a/8b/8c recibe 401 y el
   interceptor dispara logout+redirect a `/login` (en vez del banner §5
   dentro del modal). C10 reemplazará por los status del DELETE real.
2. **DEV-C8-1** — PanelAuthMvcTest: 3 asertos de cadena JSP →
   `contentTypeCompatibleWith` (gate de apply, ratificada 2026-10-28).
3. **DEV-C8-3 / DEV-C8-5 / DEV-C8-6 / DEV-C8-7** — hallazgos
   guion-side de las 2 rondas (ver arriba, todos con parche commiteado).
4. **Errata documental del qa-report corregida en archive**: la prosa
   de la cuenta global decía «41 PASS / 2 huecos / 15 MANUAL»; la
   partición correcta según su propia tabla QP es **37 PASS planos +
   5 CON-MATIZ + 2 PARCIAL (QP-8.42, QP-8.45) + 1 HUECO declarado
   (QP-8.46) + 14 MANUAL (Q23) = 59** (la tabla es el ancla; patrón del
   desliz H12↔H13 de C7). Idem corregido en tasks T8.68.

## Deudas HEREDADAS que pasan a C9 / C10 (sin acción en C8)

- → **C9** (demo visual): las 14 parejas MANUAL-Q23 (estados de
  componente, banners dentro de pantallas, flujos E2E navegados,
  formularios de flujo 2) — E8.58: cero E2E navegado por diseño.
- → **C9**: smoke de docker compose canónico (el smoke sigue con podman
  `--build --force-recreate` + Q13 autocontenido) — heredada de C7/C5.
- → **C9**: `nginx client_max_body_size` — heredada de C5/C7.
- → **C10**: los 3 DELETEs operativos (proyecto, ficha API key,
  log por detalle vía modal V11) — hoy NO existen; cualquier confirmación
  de modal recibe **401** por cadena-3 denyAll en `/error` (DEV-C8-4) y
  el interceptor desloguea. C10 implementará los endpoints y los status
  del DELETE real con el comparador V11 ya [L]-pinned (QP-8.47).
- → C9 (pauta para re-corridas futuras de regresiones): en qa_c3,
  marcar **E3.22 como N/A-post-D2** (D2 borró `/mockups/04|05` del
  runtime del frontend); en qa_c7, la **pre-fase** que exige
  `/ws-console.html` publicado es igualmente N/A-post-D2 (D-C8-17).
  El resto de ambas suites: 23/23 y 9/9 PASS.
- Observación a la ficha de C7 (sin acción): su nota «403 de Origin
  ajena también con cuerpo vacío» solo vale para la 401 del handshake;
  la 403 real lleva «Invalid CORS request» (DEV-C8-7, canon E7.9).

## Cierre

Ficha C8 en `docs/07_SLICING_PLAN.md` actualizada a
`estado: hecho 2026-10-28`; commits del corte 1987754 (fix D1) →
ea62874 (feat) → d88d715 (smoke) → cbffdd1 + dd26fa9 (bucle acotado,
2 rondas) → commit docs de cierre. Veredicto de cierre: **C8 CERRADO** —
PASS-with-notes con 0 CRITICAL; DEV-C8-4 ratificada y anclada en 3
anclas (qa-report §DEV-C8-4 + note + ficha), bucle cerrado con
re-corrida verde, huecos QP-8.9/8.29/8.35/8.40/8.46 declarados (ni
materializables dentro de la frontera sin fichero nuevo fuera de la
tabla), deudas a C9/C10 documentadas arriba. **NO push** (manual del
usuario). C9 y C10 NO se inician sin orden del usuario.