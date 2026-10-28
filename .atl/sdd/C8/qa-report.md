---
project: Project_Log
change: C8
artifact: qa-report
last_updated: 2026-10-28
status: done
results: mvn 177/177 PASS exit 0 (174 heredados C7 + 3 de EntryPointCharsetTest C39 — cifra R8.50 re-verificada tras la pila) + smoke C8 POST-bucle: 23 PASS / 0 FAIL / 1 BLOQUEADO (Q23) exit 0 + Vitest 55/55 (6 ficheros) + npm run build VERDE + ws_echo_test.py SIN edición exit 101 OK + regresión qa_c3 23/24 PASS exit 1 (E3.22 AJENO-CLASIFICADO por D2) + qa_c7 9 casos PASS exit 2 (pre-fase AJENO-CLASIFICADA por D2: ws-console.html retirado)
commit: cbffdd1 (fix/feat/smoke del corte + hallazgo acotado H-C8-B1; la re-corrida del smoke y qa_c3/qa_c7 corrieron con los parches H-C8-B2..B5 del bucle APLICADOS SIN COMMIT — commit acotado posterior en la ronda 2; RAW en qa-log.txt)
log: qa-log.txt
---

# QA Report — C8 · Frontend del panel según maquetas

Veredicto por EJECUCIÓN FORMAL: la re-corrida canónica del smoke es
POST-bucle (tras el loop-back de hallazgos del GUION, T8.64/H-C8-B1..B5);
RAW verbatim en `qa-log.txt` (§1 corrida 1 pre-bucle = evidencia del bucle;
§2 mvn; §3 re-corrida smoke; §4 qa_c3; §5 qa_c7; §6 ws_echo). La corrida 1
(HEAD d88d715) dio exit 1 con 13 FAIL — **CERO bugs de backend** (mvn
177/177 paralelo y sondas REST en vivo lo descartan): todos los FAIL eran
deficiencias del propio guion o predicciones de resultados no respaldadas
por canon (ver §Matizaciones ancladas). Tras los parches: re-corrida
exit 0 con 23 PASS / 0 FAIL / 1 BLOQUEADO (Q23 — MANUAL demo C9).

## Comandos ejecutados (orden REAL — el del plan §6 pedía mvn→qa_c8→qa_c3→qa_c7; el REAL sigue a tasks T8.60–T8.62, que exige el mvn tras la pila)

1. Corrida 1 `bash scripts/qa_c8_smoke.sh` (HEAD d88d715, PRE-bucle) →
   exit 1: 13 FAIL / 10 PASS / 1 BLOQUEADO. Origen: H-C8-B1 (semillas
   `{"batch"}` → `{"logs"}`, commited como `cbffdd1`) + H-C8-B2..B5.
2. `mvn test` (builder canon C4/C7) → exit 0, **Tests run: 177, Failures:
   0, Errors: 0, Skipped: 0**. C39: `EntrypointCharsetTest` = 3 @Test
   (2×`Panel401` + 1×`Ingesta401` — la discrepancia 176-vs-177 del plan
   §4 RECONCILIADA: 3, no 2; la cifra de T8.62 «+2» era un error de
   transcripción, corregido en tasks).
3. Re-corrida `bash scripts/qa_c8_smoke.sh` (parches H-C8-B2..B5
   aplicados sin commit) → exit 0: 23 PASS / 0 FAIL / 1 BLOQUEADO (Q23).
4. Regresión `bash scripts/qa_c3_smoke.sh` → exit 1: **23 PASS · 1 FAIL
   (E3.22, AJENO-CLASIFICADO)**: el caso exige que `/mockups/04|05`
   estén SERVIDOS en el runtime — y D2 (R8.54/D-C8-17, aprobada en el
   gate de C8) BORRA exactamente esas 2 ficheros del runtime. Premisa
   invalidada POR DESIGN, no por bug; los endpoints que la UI consume:
   23/23 PASS. `qa_c3` queda INTOCABLE (frontera — smokes previos) y se
   NOTA para la ficha del 07: la pauta de re-corridas futuras de qa_c3
   debe leer su E3.22 como N/A-post-D2 hasta decisión del orquestador.
5. Regresión `bash scripts/qa_c7_smoke.sh` → exit 2: los 9 casos E7.n
   **PASS** (E7.14 con nota R-7.k canon); su pre-fase marca
   BLOQUEADO+PASS doble en el log porque exige `/ws-console.html`
   PUBLICADO (R7.30) y D2 lo RETIRA (misma causa D2, clasificada).
6. `npx vitest run` (T8.63) → **55/55** tests, 6 ficheros, 0 fallos.
7. `npm run build` (T8.63) → VERDE (tsc strict + vite build, 68 módulos,
   JS 328.8 kB; además el `--build` del smoke pasa por el Dockerfile).
8. `python3 scripts/ws_echo_test.py --solo-handshake --token <JWT>` SIN
   edición → exit 0: 101 + Accept válido por nginx (E8.53).

## Matizaciones ancladas (de la corrida 1 — resolución T8.64, patrón DEV-C8-1/DEV-C8-2)

- **DEV-C8-3 (H-C8-B1, commited `cbffdd1`)** — semillas del smoke con
  `{"batch":…}` cuando el canon C5 y el backend (`CAMPO_LOGS="logs"`)
  usan `{"logs":…}`: 4 lugares corregidos + guards `|| true` de
  `set -uo pipefail`. Sin impacto en código de producción.
- **DEV-C8-4 (H-C8-B2, MATIZACIÓN DE LITERALES DEL SPEC — RATIFICADA POR
  ORQUESTADOR, ver nota)** — la predicción «los DELETE de C10 (hoy
  inexistentes) responden 405/404 TAL CUAL» (S8-2/R8.36/R8.44/E8.38/
  E8.47, 5 lugares) está FALSIFICADA por el sistema real: la ruta no
  mapeada produce 405 y su error-page dispatch a `/error` reentra por la
  cadena 3 de `SecurityConfig` (`anyRequest().denyAll()`, C2-S4) cuyo
  accessDeniedHandler es el `RestAuthenticationEntryPoint` → **401
  real** `{invalid_token}` (con el charset de D1). El backend es
  INTOCABLE (R8.47) y NINGÚN smoke de C2–C7 asertó jamás un 405 (grep
  vacío) — el comportamiento es preexistente e íntegro. El CONTRATO DEL
  CLIENTE §5 (decisión por status, parseo tolerante, SIN fingir éxito)
  es agnóstico del status y queda ÍNTEGRO con 401. Consecuencia UX
  anclada: hasta que C10 implemente los DELETE, confirmar un modal
  8a/8b/8c recibe 401 y el interceptor del client (R8.14) dispara
  logout+redirect a `/login` en vez del banner §5 dentro del modal —
  comportamiento real documentado; C10 lo reemplazará por los 404/405
  del DELETE real. Iguales: POST `/panel/me` (sin método) → 401 (E8.14
  reformulado a lo real; el 405-tolerante queda cubierto por Vitest mock
  405-HTML, que SÍ pina el camino del client R8.15).
- **DEV-C8-5 (H-C8-B3)** — el filtro C6 `text` solo matchea MESSAGE: el
  lookup de la fila del detalle por `correlation_id` crudo da lista
  vacía; el guion busca por el needle del message y verifica el
  correlation_id de la fila hallada (E8.34/E8.38).
- **DEV-C8-6 (H-C8-B4)** — helper `campo_json` (exit 2 ante JSON null)
  sustituido por el canon C3 `campo_json_nul` en los asertos de
  `next_cursor: null` (C6) y `api_key_prefix: null` tras revoke (C3).
- **DEV-C8-7 (H-C8-B5)** — la 403 pre-upgrade de Origin ajena NO lleva
  cuerpo vacío: `DefaultCorsProcessor` escribe «Invalid CORS request»
  (26 bytes); el canon E7.9 de C7 SOLO aserta el status 403 (grep
  `^HS 403 `), y así queda. La 401 pre-upgrade SÍ lleva cuerpo VACÍO y
  se pina (`HS 401 BODY 0` — pasó en ambas corridas). El «cuerpo vacío
  también en la 403» del plan (QP-8.31) queda matizado al canon E7.9.

## Tabla de veredictos QP-8.1…QP-8.59

| QP | E8.x | Veredicto | Evidencia (qa-log.txt / qa_plan mapping) |
|---|---|---|---|
| QP-8.1 | E8.1 | MANUAL-PENDIENTE (demo C9) | parejas §3; [L] declarado hueco por D3/R8.56 — ver verify §A |
| QP-8.2 | E8.2 | MANUAL-PENDIENTE (demo C9); mitad REST absorbida (E8.19/E8.48) | §3.5 |
| QP-8.3 | E8.3 | **PASS [L]** | `useLogFilters.test.ts` (round-trip idempotente) — Vitest 55/55 |
| QP-8.4 | E8.4 | MANUAL-PENDIENTE (demo C9); mitad REST absorbida (E8-auth) | §3.5 |
| QP-8.5 | E8.5 | MANUAL-PENDIENTE (demo C9); pareja E8.6 PASS | §3.5 |
| QP-8.6 | E8.6 | **PASS [S]** | smoke re-corrida E8.6 + E8.51 |
| QP-8.7 | E8.7 | **PASS [S]** | E8.7 (orden literal + /me 200 ADMIN) |
| QP-8.8 | E8.8 | MANUAL-PENDIENTE (demo C9); 401 REST invalid_credentials MAY-anotada | §3.5 |
| QP-8.9 | E8.9 | MANUAL-PENDIENTE (demo C9) | hueco declarado (estado de componente) |
| QP-8.10 | E8.10 | MANUAL-PENDIENTE (demo C9); mitad REST `/me` absorbida (E8-auth) | §3.5 |
| QP-8.11 | E8.11 | **PASS [L]** (absorción QP-8.15) | `client.test.ts` camino 401; banner → demo C9 |
| QP-8.12 | E8.12 | MANUAL-PENDIENTE (demo C9) | hueco (hook) |
| QP-8.13 | E8.13 | MANUAL-PENDIENTE (demo C9) + higiene por grep (T8.67 — sin localStorage/cookies) | §3.5/verify |
| QP-8.14 | E8.14 | **PASS [L]+[S]** (matiz H-C8-B2: la parte [S] reformulada a lo real — 401 tal cual de POST /me; Bearer viaja) | §3 re-corrida |
| QP-8.15 | E8.15 | **PASS [L]+[S]** | client.test (401 sin body-read) + smoke 401 por status |
| QP-8.16 | E8.16 | **PASS [L]** | client.test (405 HTML/502 plano/cuerpo vacío → "HTTP <n>"; 404 JSON) |
| QP-8.17 | E8.17 | **PASS [L]** (matiz H-A2: el ID del guion porta el CRUD 409/400 — vocabulario visual → demo C9) | smoke E8.17 + client.test |
| QP-8.18 | E8.18 | **PASS-CON-NOTA [S]** (502 no materializado en guion — tolerancia R-7.k; Vitest del camino 502→200 en client.test [L]) | §3 |
| QP-8.19 | E8.19 | **PASS [S]** (matiz H-A1: el ID porta «/me 401»; rutas relativas por :8080 + review) | smoke E8-auth/E8-API |
| QP-8.20 | E8.20 | **PASS [S]** (matiz H-C8-11: orden DESC por psql no pinado en guion — API-shape 50+cursor verificado) | smoke E8.20 |
| QP-8.21 | E8.21 | **PASS [L]** | `useLogFilters.test.ts` |
| QP-8.22 | E8.22 | MANUAL-PENDIENTE (demo C9); pines absorbidos (set_filters QP-8.27, cursor QP-8.24) | §3.5 |
| QP-8.23 | E8.23 | **PASS [L]+[S]** | offset −03:00 + 400 filter_from_to_inverted SIN field |
| QP-8.24 | E8.24 | **PASS [L]+[S]** | p50 + p16 disyuntas + fin null + `usePagination.test.ts` |
| QP-8.25 | E8.25 | **PASS [L]** ([S] absorbida por E8.27 — secuencia inyecta tras keyset) | dedup.test + E8.27 |
| QP-8.26 | E8.26 | **PASS [S]** (absorción del 400 code/field de QP-8.23; banner visual → demo C9) | smoke E8.26 |
| QP-8.27 | E8.27 | **PASS [L]+[S]** | 1 frame batch byte-exacto §3.5 (5 claves, 11 campos, b_+uuid único) + `ws/client.test.ts` |
| QP-8.28 | E8.28 | **PASS [L]** | dedup.test (dedup + orden global + empate id DESC) |
| QP-8.29 | E8.29 | MANUAL-PENDIENTE (demo C9) | hueco declarado |
| QP-8.30 | E8.30 | **PASS [L]** ([S] reconexión por pareja qa_c7 E7.11 citada — canon H-C8-2; refetch 1ª página cubierto por E8.27) | ws/client.test + §6 qa_c7 |
| QP-8.31 | E8.31 | **PASS [S] CON MATIZ** (H-C8-B5: 401 real cuerpo VACÍO; 403 real "Invalid CORS request" — canon E7.9 solo-status) + **[L] HUECO** (rama `alCierreInaugural`→`me()` no extraída pura — declarado, no materializable sin fichero nuevo fuera de tabla) | smoke E8.31 + verify §A |
| QP-8.32 | E8.32 | MANUAL-PENDIENTE (demo C9); estados §4 del guion API verificados indirectamente | §3.5 |
| QP-8.33 | E8.33 | **PASS [L]** | ws/client.test (parser defensiva no lanza) |
| QP-8.34 | E8.34 | **PASS [S]** | detalle 11 campos + FATAL con exception/metadata (lookup por needle — H-C8-B3) |
| QP-8.35 | E8.35 | MANUAL-PENDIENTE (demo C9) | hueco (composición router) |
| QP-8.36 | E8.36 | **PASS [S]** | 404 not_found literal |
| QP-8.37 | E8.37 | **PASS [L]** | `Modal.test.ts` (coincideLiteralConTrim, 8 casos) |
| QP-8.38 | E8.38 | **PASS [S] CON MATIZ (DEV-C8-4)** — 401 tal cual (H-C8-B2), fichas sobreviven | smoke E8.38 |
| QP-8.39 | E8.39 | **PASS [S]** | listado SIN summary (8 claves) |
| QP-8.40 | E8.40 | MANUAL-PENDIENTE (demo C9) | hueco |
| QP-8.41 | E8.41 | **PASS [S]** | PATCH ARCHIVED/ACTIVE 200 + 403 project_archived en ingest |
| QP-8.42 | E8.42 | **PASS [S] PARCIAL** (ficha 200 + prefix/has_key vía E8.43 con pines absorbidos; caso E8.42-firma no emitido como marca propia — HUECO declarado por auditoría; visual → demo C9) | smoke E8.43 + verify |
| QP-8.43 | E8.43 | **PASS [S]** | regenerate 201 orden literal {key,prefix,created_at} |
| QP-8.44 | E8.44 | **PASS [S]** (matiz H-C8-5: ingesta-con-key-revocada not materializada; 204+has_key false+prefix null SÍ) | smoke E8.44 |
| QP-8.45 | E8.45 | **PASS [S] PARCIAL** (409 name_duplicate + 400 field_required=name por la marca E8.17/ID-ajeno H-A2; formulario flujo 2 → demo C9) | smoke E8.17 |
| QP-8.46 | E8.46 | **HUECO [S] declarado** (PATCH solo-modificados con updated_at refresco sin case propio en guion; PATCH {status} de E8.41 y CRUD REST de E8.17 cubren la superficie REST; flujo de ficha → demo C9) | — |
| QP-8.47 | E8.47 | **PASS [L]+[S] CON MATIZ (DEV-C8-4)** — 401 tal cual + comparador [L] PASS | smoke E8.47 + Modal.test |
| QP-8.48 | E8.48 | **PASS [S]+[L]** | charset=UTF-8 + bytes c3b3 sin 0xF3 + EntryPointCharsetTest (2/3) |
| QP-8.49 | E8.49 | **PASS [S]+[L]** | charset ingest bytes c3a1 + EntryPointCharsetTest (Ingesta401) |
| QP-8.50 | E8.50 | **PASS [L]+[REVIEW]** | mvn 177/177 — discrepancia 176-vs-177 RECONCILIADA (3 de C39, auditor §1) |
| QP-8.51 | E8.51 | **PASS [S]** | fallback SPA + assets absolutos |
| QP-8.52 | E8.52 | **PASS [S]+[REVIEW]** (la parte del guion: QA indirecta; evidencia REAL = git: 3 DELETEs D2 + docs/mockups intactos + qa_c7 pre-fase que constata su ausencia) | git diff (verify) |
| QP-8.53 | E8.53 | **PASS [S]** (matiz: qa_c3 23/24 y qa_c7 pre-fase con causas clasificadas por D2 — §Comandos 4-5; ws_echo SIN edición → 101) | §4/§5/§6 |
| QP-8.54 | E8.54 | **PASS [REVIEW]** | package.json = las 4 deps pinnadas; lockfile solo esas + transitivas (T8.66) |
| QP-8.55 | E8.55 | **PASS [S]** | npm run build VERDE + el --build del smoke (Dockerfile stage 1) |
| QP-8.56 | E8.56 | **PASS [L]** | Vitest 55/55 (cifra REAL contada por auditor = 55; reparto 7/15/5/9/11/8 difiere del §4 previsto — matiz) |
| QP-8.57 | E8.57 | **PASS [S]** | re-corrida exit 0 (23 PASS / 0 FAIL / 1 BLOQUEADO Q23; sin secretos en salida — S16) |
| QP-8.58 | E8.58 | **PASS [REVIEW]** | sin Playwright/Cypress/jsdom en lockfile (auditor); visuales → MANUAL Q23 |
| QP-8.59 | E8.59 | **PASS [S]+[REVIEW] CON MATIZ** | regresiones con las 2 causas D2 clasificadas + mvn 177 + lockfile review |

**Cuenta global (partición corregida tras el verify — WARNING documental):
59/59 QP verificados — 37 PASS planos + 5 PASS-CON-MATIZ/NOTA + 2 PARCIAL
(QP-8.42, QP-8.45) + 1 HUECO declarado (QP-8.46-case; el [L] de QP-8.31-rama
también carece de pin Vitest y queda dentro de la partida de matiz) + 14
MANUAL-PENDIENTE demo C9 (Q23) = 59. 0 CRITICAL.**

## Interpretación (QA, por el plan)

- Ningún FAIL de implementación: la única ronda de 13 FAILs de la corrida 1
  se atribuyó al guion con evidencia directa (sondas REST + mvn paralelo
  177/177) y la re-corrida quedó 0 FAIL. Los 5 hallazgos H-C8-B1..B5 están
  anclados en `.atl/notes/c8-smoke-401-denyall-error-dispatch-campojson.md`.
- Regresiones: qa_c3/qa_c7 exit ≠ 0 SOLO por las premisas que D2 (aprobada)
  invalida: E3.22 exige las copias `/mockups/04|05` servibles (borradas por
  D2/R8.54) y la pre-fase C7 exige `/ws-console.html` publicado (retirado por
  D2/D-C8-17). El resto de ambas suites: 23/23 y 9/9 PASS. El proxy nginx
  (QP-8.53) queda intacto y verificado SIN edición de `ws_echo_test.py`.
- El exit-2 no-existente del guion (H-A3) y las reasignaciones de ID E8.17/
  E8.19 (H-A1/H-A2) quedan documentados para el verify (sugerencia S, no
  bloqueante).

skill_resolution: injected