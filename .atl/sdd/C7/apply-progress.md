---
project: Project_Log
change: C7
artifact: apply-progress
last_updated: 2026-10-26
status: draft
---

# Apply progress — C7 · Streaming WebSocket en vivo

Estado por tarea (T7.1–T7.25). Frontera (R7.3): 6 M + 1 D existentes EXACTOS
(qa_c3 dif-0) + piezas NUEVAS según spec/design. No commit (lo hace archive).

| Tarea | Estado | Notas |
|---|---|---|
| T7.1 | hecho | FiltrosConsulta: parsear/Filtros/FiltroInvalido + mudanza constants (patronIlike BYTE-A-BYTE) |
| T7.2 | hecho | LogsController delega checks 2–7 (byte-compatible E6.x) |
| T7.3 | hecho | LogsQueryRepository: SELECT_POR_IDS/porIds/FilaBatch/FILA_BATCH (reorden en Java) |
| T7.4 | hecho | FiltrosConsultaParseTest (E7.20, 9 checks) |
| T7.5 | hecho | PorIdsSqlPinTest (E7.19, pin SELECT_POR_IDS + reorden [3,1,2]) |
| T7.6 | hecho | GATE Fase 1 mvn VERDE |
| T7.7 | hecho | JwtHandshakeInterceptor (401/403 D-7.9) |
| T7.8 | hecho | LiveBroadcaster (porIds reordena; sin replay) |
| T7.9 | hecho | LogSocketHandler (set_filters 6 claves, latido, backpressure) |
| T7.10 | hecho | GATE Fase 2 mvn VERDE |
| T7.11 | hecho | WebSocketConfig swap + setAllowedOrigins(frontend-origin EXACTO — hallazgo OriginHandshakeInterceptor default de spring-websocket 6.2.19) |
| T7.12 | hecho | REMOVE EchoSocketHandler (D) |
| T7.13 | hecho | application.yml bloque streaming |
| T7.14 | hecho | ws-console.html (público por nginx, R7.30) |
| T7.15 | hecho | StreamingWsClientTest (E7.2/3/4/7/8/16, 7 tests) |
| T7.16 | hecho | LatidoYBackpressureTest (E7.12/13, doble barrera; 1013 por el DELEGADO — nota bugfix) |
| T7.17 | hecho | GATE mvn pre-smoke VERDE: 172 tests, 0 fallos, 0 errores — BUILD SUCCESS (146 heredados + 26 C7, sin el DiagHandshakeTest temporal borrado tras diagnóstico) |
| T7.19 | hecho | qa_c2 Q20 → --solo-handshake --token + guards (bash -n OK) |
| T7.20 | hecho | qa_c3 dif-0 verificado (git diff vacío intocables) |
| T7.21 | hecho | qa_c7_smoke.sh completo (bash -n OK; heredocs py OK; fix estructura case_e7_5 de post-summary) |
| T7.22 | hecho | smoke C7 VERDE run 6 (exit 0): E7.1/E7.5/E7.6/E7.9/E7.10/E7.11/E7.14/E7.15/E7.17 PASS (9/9; E7.14 PASS con nota R-7.k "1013 no materializado — cubierto [L] E7.13"); Q20 (qa_c2 re-corrida) PASS; qa_c6 re-corrida 8/8 PASS (tras down -v accidental de Q13 de qa_c2: DB resiementada con 250 filas). Fixes del guion run 4→6: payload FLAT individual (400 environment_required), try-sin-except ×4 (frame_campo/N.º de frame E7.5/6), carrera cerrado-por-comando, sf inicial COMPUESTO en iniciar_long/2 (shape 6-claves raíz = no-contrato ignorado → sesiones sin filtros → 0 frames E7.11/E7.14), F_LONG no-exportada en bash -c de E7.10 (splice), ingesta_simple con LEVEL param (E7.6 WARN real) |
| T7.23 | hecho | GATE mvn FINAL post-smoke: **172 tests, 0 fallos, 0 errores — BUILD SUCCESS** (exit 0; 146 heredados + 26 C7: 7 PorIdsSqlPinTest + 10 FiltrosConsultaParseTest + 2 LatidoYBackpressureTest + 7 StreamingWsClientTest) |
| T7.24 | hecho | qa_c3 re-corrida: 23/24 PASS; E3.24 FAIL solo por application.yml + qa_c2_smoke.sh (CONTRACTUAL de C7: T7.13/T7.19) — DIFERIDO con nota (sin commits permitidos) |
| T7.25 | hecho | frontera final verificada (git status EXACTO; checklist abajo) |

## Fronto — T7.25 (frontera de working tree + checklist E7.1–E7.20)

git status al cierre (SIN commits — lo hace archive): 6 M (WebSocketConfig,
FiltrosConsulta, LogsController, LogsQueryRepository, application.yml,
qa_c2_smoke.sh) + 1 D (streaming/EchoSocketHandler.java) + NUEVOS:
JwtHandshakeInterceptor / LiveBroadcaster / LogSocketHandler (main),
FiltrosConsultaParseTest / PorIdsSqlPinTest / streaming/{StreamingWsClientTest,
LatidoYBackpressureTest} (test), frontend/public/ws-console.html,
scripts/qa_c7_smoke.sh, .atl/notes/{close-1013-vs-decorador-1011-bugfix,
origin-handshake-interceptor-default-spring-websocket}.md, .atl/sdd/C7/**.
qa_c3_smoke.sh dif-0 (T7.20). Intocables TODOS sin diff (compose/.env.example,
frontend/src/**, pom.xml, V1__init.sql, ingest/**, SecurityConfig, auth/**,
application-test.yml, ws_echo_test.py, docs/**).

Checklist E7.1–E7.20 — 11 [L] VERDE en mvn (172; Latido 2 · Streaming 7 · pins
17) + 9 [S] VERDE en smoke run 6:

| ID | Caso | Tipo | Veredicto |
|---|---|---|---|
| E7.1 | ciclo end-to-end por nginx (1 frame shape §3.5) | [S] | PASS (run 6) |
| E7.2 | frame tras COMMIT forma §3.5 | [L] | PASS (StreamingWsClientTest) |
| E7.3 | batch de 1 + orden del lote | [L] | PASS (StreamingWsClientTest) |
| E7.4 | set_filters por sesión (3 clientes; c3 sin sf NADA) | [L] | PASS (StreamingWsClientTest) |
| E7.5 | batch 3 → 1 frame ordenado + RETURNING | [S] | PASS (run 6) |
| E7.6 | recalificada en caliente (ERROR/WARN/otro project) | [S] | PASS (run 6) |
| E7.7 | handshake 401 pre-upgrade ×3 | [L] | PASS (StreamingWsClientTest) |
| E7.8 | Origin (403/101/ausente) | [L] | PASS (StreamingWsClientTest) |
| E7.9 | 401/403/101 por proxy | [S] | PASS (run 6) |
| E7.10 | ping 0x9/0xA ~30 s + cero JSON != batch | [S] | PASS (run 6) |
| E7.11 | reconexión limpia sin replay | [S] | PASS (run 6) |
| E7.12 | cierre por latido 1001 + limpieza + reconexión | [L] | PASS (LatidoYBackpressureTest) |
| E7.13 | backpressure 1013 (doble barrera) | [L] | PASS (LatidoYBackpressureTest) |
| E7.14 | backpressure en pila (202 ≤ 2 s; sanos ≥ 10) | [S] | PASS (run 6, nota R-7.k) |
| E7.15 | archivado deja de emitir (403/ACTIVE) | [S] | PASS (run 6) |
| E7.16 | límite de mensaje 64 KiB | [L] | PASS (StreamingWsClientTest) |
| E7.17 | ws_echo_test --solo-handshake --token (S7-7) | [S] | PASS (run 6) |
| E7.18 | regresión mvn completo | [L] | PASS (172) |
| E7.19 | pin SELECT_POR_IDS + reorden | [L] | PASS (PorIdsSqlPinTest) |
| E7.20 | parsear/FiltrosConsulta (9 checks) | [L] | PASS (FiltrosConsultaParseTest — 10) |

Divergencias registradas: D-C7-1 (LogDto 11 campos — literal prevalece),
D-C7-2 (received_at truncado-a-segundos en frame vs micros en BD),
D-7.15 (1013 por getDelegate() — nota bugfix), R-7.j (guard de Origin en
precondiciones del smoke), R-7.k (E7.14 tolera no-1013 en pila — defaults
absorben ~900 KiB; la pareja [L] E7.13 pinea el 1013).

## Bucle acotado de hallazgos MEDIA (H1/H4/H5/H9/H12/H13)

Audiencia de qa-run VERDE (20/20) + auditoría tests-written.md §6 → 6
parches aplicados en orden, SIN commits, sin tocar spec/design ni artefactos
de QA:

| Parche | Fichero | Qué |
|---|---|---|
| H1 | scripts/qa_c7_smoke.sh · case_e7_1 | delta CANON snapshot-delta: SNAPSHOT psql del proyecto ANTES de la ingesta (antes comparaba delta_cuenta contra la base de FRAMES); tras la 202 exige delta == 1 + frame + needle (lo demás intacto) |
| H4 | scripts/qa_c7_smoke.sh · case_e7_5 (+F_BIDS, +helper bid_de, +E7.1 guarda su bid) | delta de BD == 3 con snapshot psql ANTES del batch (1ª colocación fue POST — run 7 '4!=7' corregido) y unicidad del batch_id EXPLÍCITA contra TODOS los previamente vistos (registro $F_BIDS; E7.1 y E7.5 apuntan); antes era vacuo (solo el último frame) |
| H9 | scripts/qa_c7_smoke.sh · case_e7_14 (c) | SOLO un close 1013 cuenta como cierre por backpressure; sesión sigue abierta → PASS con nota R-7.k; código ≠ 1013 → FAIL 'cierre-no-1013=<code>' (JAMÁS tolerable) |
| H5 | StreamingWsClientTest · e77 (sub-caso a) | Cuerpo VACÍO del 401 pre-upgrade: HttpResponse.body() del WebSocketHandshakeException; la CLASE del cuerpo no está especificada por la API pública del JDK — se ACEPTE String en blanco o null como 'vacío'; una clase distinta sería una LIMITACIÓN TÉCNICA documentada (comentario in-line) — matiz anotado para verify |
| H12 | FiltrosConsultaParseTest · +ordenGlobalChecks2a7PrimerFalloGana | pin del ORDEN GLOBAL 2→7 con sub-casos EN CADENA (pares inválidos simultáneos): 2→project_id + env → project_id; 3→env + level → env; 4→level + from naive → level; 5→from naive + to no-fecha → from; 7→from>to cierra con field sin nulo. Nota: el sub-caso 'to + from>to' de la auditoría es infactible (from>to exige to parseado); se implementó 'from>to con 2–6 ausentes → field sin nulo' |
| H13 | StreamingWsClientTest · +r716MatchingFromToInclusivoYTextoCaseInsensitive | vacío de matching R7.16: lote-filtros {project_id, from, to, text} → lote en el rango INCLUSIVO (== desde y == hasta probados) + texto mayúsculas-en-filtro/minúsculas-en-mensaje → EXACTAMENTE un frame; INVERSO: < desde, > hasta y texto no contenido → CERO frames (mecánica E7.2) |

Re-certificación: `bash -n` OK · **mvn test completo = 174, 0 fallos,
0 errores, BUILD SUCCESS** (172 + 2: FiltrosConsultaParseTest 10→11,
StreamingWsClientTest 7→8) · **smoke re-corrido (run 8) = 9/9 PASS, exit 0**
(E7.14 con nota R-7.k '1013 no materializado'; code= — el H9 no se disparó:
la sesión lenta quedó abierta, no cerró con otro código). Run 7
intermedio: 8/9 (solo E7.5 FAIL por la colocación del snapshot — corregida
en run 8; veredicto 'delta-bd!=3(4!=7)').

Hallazgos BAJOS retenidos (matices documentales — para verify, SIN parche):
H2/H3/H6/H7/H8/H10/H11/H15/H16 (auditoría tests-written.md §6).