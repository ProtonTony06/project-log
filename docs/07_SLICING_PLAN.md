---
status: activo
updated: 2026-10-20
fuera-del-paquete: si
---

# Slicing Plan: cambios SDD de Project_Log

> v1.0 · 2026-10-20 · **Documento de planificación de trabajo, NO del paquete
> de definición** (los 7 docs de `/docs` están congelados; este fichero no
> redefine alcance: cada corte traza al Implementation Plan 06 y a las stories
> del PRD §3). Es el puente entre el 06 (fases → stories) y el flujo
> SDD de `sdd-orchestrator`: **cada corte = una ejecución completa del flujo
> SDD** (init→explore→propose→spec→design→tasks→apply→verify→archive),
> con artefactos en `.atl/sdd/{id}/`.

## Reglas de slicing

- **Un cambio SDD por fila de la tabla.** No se mezclan historias de cortes
  distintos ni se adelantan dependencias.
- En **cada corte**, `sdd-propose` se presenta al usuario antes de escribir
  código de la pieza nueva (gate de alcance fino); el 06 fija el "Done
  cuando" y este doc no lo altera.
- Los **literales de API/UI son contrato** (Schema/TRD/AppFlow/UIUX): los
  specs de cada corte los citan, nunca los redefinen.
- Estado por corte llevado en un campo `estado:` (pendiente / en curso /
  hecho) — se actualiza al cerrar cada `sdd-archive`.
- Fase 3 (P2, sin fecha) **no se slicea**: se abrirá un corte solo si el
  usuario la activa; su orden propuesto vive en el 06.

## Tabla de cortes

| # | Corte | Fase (06) | Stories PRD §3 | Dependencias |
|---|---|---|---|---|
| C1 | Fase 0 — Setup de pila y repo | 0 | — (sin valor de usuario) | — |
| C2 | Seguridad base + auth del panel | 1 | 1 | C1 |
| C3 | Proyectos: CRUD + archivado | 1 | 2, 13, 14 | C2 |
| C4 | API keys de ingesta | 1 | 3, 4 | C3 |
| C5 | Ingesta REST (single + batch) | 1 | 5, 6, 7 | C4 |
| C6 | Consulta keyset + filtros + detalle | 1 | 8, 9, 10 | C5 |
| C7 | Streaming WebSocket en vivo | 1 | 11, 12 | C5 (+C2 para el JWT) |
| C8 | Frontend del panel según maquetas | 1 | — (consume las APIs de C2–C7) | C2–C7 |
| C9 | Demo de aceptación + deploy + firewall | 1 | cierre de Fase 1 | C1–C8 |
| C10 | Fase 2 — borrados definitivos, resumen, acceso remoto | 2 | 15, 16, 17 (& backup movido a v2) | C9 |

Nada se slicea en Fase 3 (P2). Las 14 stories P0 del PRD §3 quedan cubiertas:
C2→(1), C3→(2,13,14), C4→(3,4), C5→(5,6,7), C6→(8,9,10), C7→(11,12).

## Detalle por corte

### C1 — Fase 0: Setup pila y repo (`estado: hecho` 2026-10-20 · verify PASS-with-notes ×E1–E12 con evidencia en .atl/sdd/C1/; nota: verificado con podman rootless — smoke canónico `docker compose` heredado a C9)

- **Alcance (del 06):** repo monomódulo Spring Boot package-by-feature
  (`auth, projects, keys, ingest, query, streaming` + `config/common`) +
  frontend Vite/React/TS; `.env.example` sin secretos (nombres TRD §6.3);
  Dockerfiles multi-stage; `nginx.conf` con SPA fallback + proxy `/api/v1` +
  bloque `/ws/logs` (Upgrade, timeouts 3600 s); `compose.yaml`
  (`postgres:17-alpine`, healthchecks encadenados, sin mem_limit — ADR-27,
  único puerto 8080); Flyway `V1__init.sql` literal del Schema §6.1; actuator
  solo `health,info`.
- **Done cuando (del 06):** `docker compose up -d` en la VM levanta 3
  servicios con healthchecks green, el 8080 del host responde `index.html`
  de prueba, Flyway aplica el esquema `V1` en frío y el backend hace
  fail-fast sin secretos del `.env`.
- **Extra obligatorio (riesgo #1 del 06, probado aquí y no al final):** un
  **echo test del WebSocket tras nginx** desde la Fase 0 (upgrade/timeout
  reales en el proxy), además del "index.html de prueba".
- **Insumos:** TRD §6 (deploy/env), Schema §6.1 (V1__init.sql), Riesgos del 06.

### C2 — Seguridad base + auth del panel (`estado: hecho` 2026-10-21 · QA 22/27 con cierre posterior: Q24-CORS fixed (entorno), Q5/Q18/Q21 resueltos; verify PASS-with-notes ×E2.1–E2.22, suite 26/26; heredados en archive-report: R-nuevo-3 anti-veneno de env, R-nuevo-4 401 ISO-8859-1 → C8/C9)

- **Alcance:** SecurityFilterChain con dos cadenas (panel JWT / ingesta
  `X-API-Key` — la de ingesta se declara, se usa en C5); Argon2id (ADR-3);
  JWT HS256 + filtro (TTL 8 h, ADR-13); seed idempotente del admin (sin
  password → no arranca); `POST /auth/login` (200 `{token, username,
  expires_at}` · 401) y `GET /me`.
- **Done cuando:** login OK y 401 probados como smoke, seed idempotente
  verificado en reinicio, y `/api/v1/panel/**` protegido (401 sin JWT).
- **Insumos:** TRD §2.5 (auth) y §2.1 (paquetes), Schema §3.2/§3.3 y §5.

### C3 — Proyectos: CRUD + archivado (`estado: hecho` 2026-10-21 · apply DONE 24/24 smokes + mvn 60/60, 8 commits a97de25→d9853a0; QA 32/33 — Q23 [B] navegador BLOQUEADO sin driver, tolerado por §7 con instrucción manual ~2 min al usuario; verify PASS-with-notes ×25/26; heredados en archive-report: R-nuevo-5 podman-compose requiere `--force-recreate`, R-nuevo-6 mvn test en imagen builder maven:3.9-eclipse-temurin-21, smoke-of-the-smoke previo a la corrida canónica en C4+; maquetas 04/05 servibles en :8080/mockups con login inline + PATCH {status})

- **Alcance:** `GET /projects` (query `status?`), `POST /projects` (201 ·
  400 · 409 `name_duplicate`), `GET/PATCH /projects/{id}` (incluye
  archivar/desarchivar vía `status`); archivado conserva logs consultables;
  409/400 con los literales del Schema.
- **Nota de cruce:** el 403 `project_archived` del C5 es consecuencia de
  este estado; aquí existe el dato (status), en C5 la regla.
- **Done cuando:** CRUD+archivado con smoke de estados (409 y 400 incluidos);
  maqueta 04/05 servibles contra esta API (los paneles aún no existen).
- **Insumos:** Schema §3.2/§3.3, AppFlow flujos 2 y 7, TRD §2.3 (entidades).

### C4 — API keys de ingesta (`estado: hecho` 2026-10-24 · apply DONE 11/11 (+loop-back QP-07/QP-26), mvn 77/77, smoke 15/15 canónico, 3 commits 8437bdd→8cf0b9f (+ docs(SDD) de cierre); QA PASS global 30/30 (QP-30 opcional N-A); verify PASS-with-notes ×29/29 E4, 0 CRITICAL/3 W/3 S — huecos G-1/G-2 y LIKE-underscore anotados en archive-report; deudas retenidas de C3: smoke docker compose a C9 y 401 ISO-8859-1 a C8/C9, NINGUNA nueva) · deuda S-3 (LIKE-underscore) **CERRADA en C6** (E6.18 con contraguja misma-length + E6.13 `FiltrosConsultaTest`; matización H9 ratificada: la contraguja del plan no discriminaba, la versión misma-length sí)

- **Alcance:** `POST /projects/{id}/api-key/regenerate` (**201 una única
  vez**, key `plk_`+43 Base64url; `{key, prefix, created_at}`);
  `DELETE /projects/{id}/api-key` (revoca: hash/prefix/key_created_at →
  NULL); guardado solo por hash SHA-256; índice único parcial
  `api_key_hash`; comparación en tiempo constante; regeneración invalida la
  anterior al instante (PRD).
- **Done cuando:** regenerar→revelar una vez→revocar→403 al ingestar
  (smoke con cliente curl; ingesta real llega en C5).
- **Insumos:** TRD §2.6 (formato y hash), Schema §2 (`projects`), AppFlow
  flujo 3, maqueta 05 (modal de revelación única).

### C5 — Ingesta REST (`estado: hecho` 2026-10-25 · apply DONE 20/20 (+ratificaciones de gate D-13/D-14/D-15: bean-higiene del guard, ObjectProvider lazy y unwrap de FrameworkServlet), mvn 108/108 [31 C5 + 77 heredados], smoke 13/13 canónico run 6, QA 37/37 (EX-1/E5.34 in) + higiene REMEDIADA en loop-back, verify PASS-with-notes 0 CRITICAL/6 W [ratificados]/7 S, commits 3f707ba→213735c (+docs): cadena 1 REAL de ingesta — denyAll reemplazado, 202 {accepted:N}, all-or-nothing, seam IngestListener tras COMMIT para C7; hueco G-2 CERRADO con E5.31; deudas: nginx client_max_body_size NUEVA → C9, smoke docker compose → C9 y 401 ISO-8859-1 → C8/C9 heredadas)

- **Alcance:** `POST /api/v1/ingest` y `/ingest/batch` (1…200); campos de
  confianza del servidor (project_id deducido de la key, received_at UTC;
  timestamp normalizado a UTC); validación fail-fast con los literales
  (`environment_required`, `timestamp_without_timezone`, `field` en 400);
  límites (1 MiB → 413 `INGEST_MAX_REQUEST_BYTES`; tamaños ≤ 32/128/64 KiB);
  403 `project_archived`; all-or-nothing en UNA transacción; respuesta
  **`202 {accepted: N}`** (decisión cerrada: no es 200).
- **Done cuando:** los 5 smoke de error (401/403/400×2/413) + happy path
  individual y batch de 200 pasan; batch intermedio inválido persiste
  NADA (all-or-nothing probado).
- **Insumos:** Schema §3.4 (LogIngestRequest) y §4.1, TRD §2.7, PRD §6.

### C6 — Consulta keyset + filtros + detalle (`estado: hecho` 2026-10-25 · apply 25/25 (+loop-back H1–H9 cerrados por apply), mvn 146/146 [38 C6 + 108 heredados], smoke 8/8 canónico E6.16–E6.23, QA 23/23 QP-6.x PASS + pre-fase, verify PASS-with-notes 7/7 checks A–G OK — 2 desviaciones ANCLADAS (D-S6-a spec 16-vs-17 placeholders, smoke `--build` vs R6.27) + 2 matices ratificados (H9 contraguja misma-length, H8/P-3 guard-Z), commits 78544b2→2b2f1d8 (+docs): paquete query — SQL literal R6.16 byte-a-byte (17 `?`/7 CASTs pinados), cursor keyset r,id con micros preservados, `{items, next_cursor?}` con null explícito, filtros combinables con escape ILIKE, detalle 11 campos, 404 y 401 cadena 2; deuda S-3 de C4 CERRADA con E6.18/E6.13; deudas heredadas → C9 (nginx client_max_body_size, smoke docker compose) y C8/C9 (401 ISO-8859-1), NINGUNA nueva)

- **Alcance:** `GET /logs` con filtros combinables (SQL literal del Schema
  §3.3, cursor keyset, LIMIT+1 → `{items, next_cursor?}`), `GET /logs/{id}`
  (LogDto completo con exception/metadata; la lista va SIN esos dos campos);
  400 `filter_from_to_inverted` y 404 según AppFlow §5.
- **Done cuando:** keyset paginado estable (insertar logs nuevos entre
  páginas NO duplica ni salta), filtros combinados y detalle con smoke.
- **Insumos:** Schema §3.3, AppFlow flujo 4/5 y flujo 6b (dedup ligada), TRD §2.4.

### C7 — Streaming WebSocket en vivo (`estado: hecho` 2026-10-26 · apply 25/25 T7.x (+bucle acotado de hallazgos MEDIA H1/H4/H5/H9/H12/H13 cerrado con re-certificación), mvn 174/174 [28 C7 + 146 heredados] (dos corridas canónicas 172→174), smoke C7 9/9 canónico (E7.14 PASS-con-nota R-7.k), QA 20/20 QP-7.x PASS + qa_c3 24/24 con E3.24 dif-0 + re-corrida qa_c2 con Q20 PASS (deuda ajena Q18/Q19/Q24/Q21 clasificada) + qa_c6 8/8, verify PASS-with-notes 9/9 checks A–I OK — 0 CRITICAL, 2 W ratificados (3.ª edición funcional de WebSocketConfig FORZADA por el OriginHandshakeInterceptor por defecto de spring-websocket 6.2.19 con Origen EXACTO sin "*" — anclada en note; permutación H12/H13 en qa-report ya corregida) + 1 S corregida, commits ce0ddcb→a6b1962→(+bucle)→(+docs): paquete com.projectlog.streaming WebSocket en tiempo real — handshake JWT+Origin 401/403 pre-upgrade (401 ×3 sin oráculo, Origin EXACTA, cuerpo vacío), frame §3.5 byte-exacto (5 claves en orden, batch_id b_+uuid4, LogDto 11 claves D-C7-1, truncado-a-segundos D-C7-2), set_filters 6 claves con recalificación, broadcast ordenado tras COMMIT vía seam IngestListener SIN diff (hueco "seam sin beans" de C5 CERRADO con LiveBroadcaster), backpressure buffer límite + 1013 dual (H9 exclusiva-1013) y latido con GC 1001, sin replay, archivado deja de emitir, parser extraído a FiltrosConsulta (orden global 2..7 pinado) + porIds para el broadcast; consola temporal `ws-console.html` (enseñanza: `http://<host>:8080/ws-console.html`); deuda Testcontainers P-2 CERRADA; deudas vivas heredadas → C9 (smoke docker compose, nginx client_max_body_size) y C8/C9 (401 ISO-8859-1) — NINGUNA nueva)

- **Alcance:** handler nativo `/ws/logs?token=<JWT>` con
  HandshakeInterceptor (401 pre-upgrade, Origin validada — nunca `"*"`),
  `set_filters` por sesión (misma semántica que `GET /logs`), frame único
  `batch` en orden del lote (`b_`+UUID), emisión **tras COMMIT**
  (LiveBroadcaster), heartbeat 30 s → cierre 60–90 s; archivado deja de
  emitir; sin replay.
- **Done cuando:** batch de C5 aparece como frame único ordenado tras
  COMMIT; heartbeat/reconexión de servidor probados; echo test del proxy WS
  de C1 sigue verde con el handler real.
- **Insumos:** Schema §3.5 (contrato WS), TRD §2.8, AppFlow flujo 6.

### C8 — Frontend del panel según maquetas (`estado: hecho` 2026-10-28 · apply 68/69 T8.x (T8.69 = commit docs de cierre; bucle acotado de hallazgos 2 rondas H-C8-B1..B5 con parches commited cbffdd1/dd26fa9), mvn 177/177 [3 D1 EntrypointCharsetTest + 174 heredados; cifra reconciliada — era «+2» transcripción], smoke C8 23/23 PASS + 1 BLOQUEADO Q23 (demo C9) exit 0 en re-corrida; corrida 1 exit 1 con 13 FAIL = CERO bugs backend, todo guion-side + 1 matización de literales del spec, Vitest 55/55 + npm build verde, regresiones qa_c3 23/24 y qa_c7 9/9 casos PASS (exit≠0 SOLO por premisas invalidadas por D2 — E3.22 y pre-fase = N/A-post-D2) + ws_echo 101 sin edición, verify PASS-with-notes 8/8 checks A–H OK — 0 CRITICAL / 2 W (DEV-C8-4 ratificada; errata prosa qa-report corregida en archive) / 4 S, commits 1987754→ea62874→d88d715→cbffdd1→dd26fa9→(+docs): panel React+TypeScript según maquetas — /login 401 sin cerrar vista, /logs con filtros combinables, keyset "Cargar más", banda ● En vivo con dedup por id y banner 400 con campo culpable (reconexión SOLO aquí — decisión C), /logs/:id con stacktrace mono + modal 8b V11 sentencia-literal + trim deshabilitado hasta coincidir, /proyectos 04/05 con bloque API key y revelación única, nuevo/editar SIN maqueta por AppFlow flujo 2, router guard GET /me, base absoluta y cliente API tolerante por status (§5, 401→interceptor logout sin bucle), WS /logs con dedup y backoff; backend ÚNICAMENTE D1 (charset UTF-8 en el 401 del panel — setCharacterEncoding antes de getWriter + simetría ingest); MATIZACIÓN RATIFICADA DEV-C8-4: los DELETE de C10 (hoy inexistentes) NO responden 405/404 — el dispatch a /error reentra cadena-3 denyAll (C2-S4) → 401 real {invalid_token}; contrato del cliente §5 íntegro (agnóstico del status), PERO hasta C10 confirmar un modal recibe 401 y el interceptor desloguea a /login; deudas heredadas → C9 (nginx client_max_body_size, smoke docker compose, demo visual MANUAL Q23 ×14) y → C10 (3 DELETEs operativos + reemplazo de la UX 401-logout por los status del DELETE real; pauta regresiones: E3.22 y pre-fase qa_c7 = N/A-post-D2) — NINGUNA deuda nueva no documentada)

- **Alcance:** `/login` (01) con 401 sin cerrar la vista; `/logs` (02):
  filtros combinables, keyset "Cargar más", banda "● En vivo" con
  dedup por id, banner 400 con campo culpable, estado
  conectado/reconectando SOLO aquí (decisión C); `/logs/:id` (03):
  stacktrace mono, metadata JSON, correlation copiable y **modal 8b con
  sentencia literal + trim, botón rojo deshabilitado hasta coincidir**
  (V11 — la API DELETE llega en C10: hasta entonces la petición devolverá
  405/404 y la UI lo muestra como el AppFlow §5); `/proyectos` (04) y
  `/proyectos/:id` (05) con bloque API key y modal de revelación única;
  `/proyectos/nuevo` y `/proyectos/:id/editar` SIN maqueta (de AppFlow
  flujo 2); router guard vía `GET /me`.
- **Done cuando:** cada pantalla renderiza contra la API real con sus
  estados (AppFlow §4) y los errores globales funcionan (§5, 401→login con
  retorno a la vista destino).
- **Insumos:** UIUX v1.0 (V1–V11), maquetas = contrato visual, AppFlow §4/§5
  y flujos 2–5, Schema §3.3 (DTOs literales).

### C9 — Demo + deploy + firewall (`estado: hecho — Fase A+ B cerradas 2026-10-29; hallazgos → C10`)

- **Alcance:** subida de la pila a la VM; firewall Proxmox con reglas
  nuevas (SSH desde LAN + 8080 — GATE #11); arranque en frío y reinicio de
  la VM verificados; demo de aceptación Fase 1 con app real del homelab.
- **Hecho (2026-10-29):** Fase A — arnés Playwright headless
  `scripts/qa_vis_c9.{py,sh}` con las 26 parejas MANUAL-Q23 + extras G2 +
  3 criterios demo del 06 automatizados contra la PILA REAL (regla dura:
  cero PASS sin pantalla observada): 21 PASS/PASS-CON-NOTA · 5 FAIL
  honestos de producto (H-C9-WS-COLGADO, H-C9-E9.13 doble «?», H-C9-E9.2
  guard-pierde-destino, H-C9-E9.10 frames set_filters — fichas en
  `.atl/notes/`, parches en C10); Fase B — VM 108 «projectlog»
  (192.168.1.58, IP estática fuera del pool DHCP), pila docker compose
  v5.6.1 3/3 healthy, firewall nftables GATE #11 (SSH-LAN + 8080, orden
  SSH-primero, boot-safe probado en frío y reinicio), `client_max_body_size
  5m` con pareja CERO-413 (202 válido / 413 JSON del backend), smoke
  canónico verde, S16 de fase limpio. Los 3 criterios demo: niv. navegador
  en Fase A (1/2 PASS, 3 con el WS colgado FAIL honesto) + nivel REST/deploy
  en la VM (verde, con el mismo límite WS anotado).
- **Done cuando (del 06):** los 3 criterios de la demo pasan manualmente
  (batch de 3 en vivo sin refresco; ERROR/production→stacktrace en <2 min;
  kill del backend→banner+reconexión con backoff, sin duplicados ni replay)
  y los smoke de backend y panel quedan automatizados. ← demo automatizada
  y ejecutada; la reconexión WS post-restauración queda FAIL-documentada
  (H-C9-WS-COLGADO → C10).
- **Residuo C10:** 4 hallazgos de producto + H-C9-NGINX-UPSTREAM, UTF-8 del
  413 (Latin-1), acotado arnés (arribo E9.2), VM 107 huérfana, re-siembra
  opcional para demo del usuario.
- **Insumos:** 06 (demo y riesgos), TRD §6.7 (firewall), AppFlow flujo 6.

### C10 — Fase 2: borrados, resumen, acceso remoto (`estado: hecho` 2026-11-04 · apply 36/36 tareas T10.1–T10.36 en 6 bloques (0 toques a intocables; única excepción RATIFICADA D-C10-16: 1 hunk de 7 inserciones en `qa_vis_c9.py` b6 — «C9 tocado en su guion por C10»), mvn 225/225 [48 C10 + 177 heredados sin relajar; builder podman canon, incluidos 2 pins de preflight real D-C10-15], Vitest 81/81 [60 previos sin relajar + 20 C10 + pin del default del Reloj del fix ratificado] con tsc 0, smoke `qa_c10` 10/10 PASS exit 0 (SQL real: purga `{"deleted":N}`/`{"deleted":0}`, CASCADE, key muere `401 api_key_invalid`, key sobrevive a la purga 202), arnés visual `qa_vis_c10.{py,sh}` 33 marcas · 0 FAIL · 1 BLOQUEADO (E9.7 variante B — su canon honesto) exit 1 canónico con 8 re-corridas hasta convergir (2 hallazgos de PRODUCTO remediados RATIFICADOS: Reloj natives desenlazados → `/logs` en blanco [nota+Resolución]; CORS `X-Confirm-*` no materializable en DefaultCorsProcessor → literales exactos X-Confirm-Name/X-Confirm-Log-Id [nota+Resolución]), kill-window E9.11/E9.26 corrió, fase L con guard `^qa-c9-|^qa-c10-` (1 proyecto del usuario INTOCADO), 39 PNGs en `.atl/sdd/C10/evidencia/` con MANIFEST-sha256 `sha256sum -c` 39/39, S16 0 hits en todos los RAW, regresiones seriales AL FINAL: qa_c3 22 PASS · 2 FAIL (E3.22 = N/A-post-D2 ratificada; E3.24 = ARTEFACTO del guard git-diff sin el commit pendiente — «verde tras commit») · qa_c7 9/9 PASS exit 2 (canon-documento C8 §4.5; «exit 0» de chat = errata reconciliada) · qa_c8 17 PASS · 6 FAIL · 1 BLOQUEADO Q23 (raíz única: premisas C8 invalidadas POR EL DISEÑO C10 ratificado — CERO defectos de producto; lectura recomendada N/A-post-C10 PENDIENTE-DE-RATIFICAR-POR-EL-DUEÑO), verify PASS-with-notes 0 CRITICAL / 2 W (RAWs desfasados pre-fix en disco) / 5 S, commit docs(SDD) SELECTIVO PREPARADO SIN EJECUTAR — espera permiso del dueño)

- **Hecho (2026-11-04):** 3 DELETE con doble confirmación (UI texto tecleado
  G2-a/M1 + cabeceras `X-Confirm-Name`/`X-Confirm-Log-Id`) con el orden
  fail-fast D-C10-1/M10 (400 `confirm_required` SIN BD → 404 → 400
  `confirm_mismatch` equals EXACTO sin field → borrado; desambiguación N=0
  vía re-`findById` para la purga — carrera CASCADE incluida), CASCADE del
  motor en el proyecto (key muere con la fila — smoke + arnés); resumen
  `include=summary` + `GET /projects/{id}/summary` (`SummaryDto` 4 claves
  siempre, `SummaryDto` NON_NULL en `ProjectDto` absent-without — ctor
  comodín 8-arg), tarjeta/ficha (maquetas 04/05) con bandas
  `lv-*`/`env-*` del orden CASE (nota orden bandas: DEBUG=4/INFO=5 manda
  spec+maqueta), «Actualizar», estado vacío «sin logs todavía» (M4),
  archivado resumen-intacto; CORS G1-a LATENTE multi-origen por coma
  (split+trim+filtrado + guard cierre; sin `*`, sin patterns;
  literales exactos D-C10-15 — la variante (a+) del `.env` de la VM queda
  PENDIENTE-DE-CONFIRMACIÓN-DEL-USUARIO); `docs/08_ACCESO_REMOTO.md`
  (Tailscale P1, TLS P2 documentado-SIN-implementar, README índice dif-0);
  fixes H-C9 de primera clase con re-runs honestos EN VIVO: WS-COLGADO
  (T_TIMEOUT_CONNECTING_MS=10 s + distinción cadena/inaugural M9),
  E9.13 (query tal cual — 3 puntos), E9.2 (una sola navegación — helpers
  puros + ref), E9.10 (UN set_filters por cambio real — memo + guard); UX
  401-logout cerrado (status reales del DELETE; DEV-C8-4 ratificada); pines
  backend Vitest sin DOM (D-C8-11) y SQL pines D-9; post-C8 de la pila
  reconstruida 3/3 healthy sin `down -v` salvo canon propio de los smokes.
- **Done cuando (del 06):** los 3 DELETE exigen doble confirmación (UI +
  cabecera) y devuelven los estados del AppFlow flujo 8 ← verificado con
  checks E10.x en vivo (mismatch dentro del modal sin logout ni borrado;
  banners con N real; WS delta 0 en purgas); el resumen aparece en
  lista/ficha ← E10.1–E10.5; el doc está en /docs ← `docs/08` (rama /docs,
  `docs/README.md` dif-0).
- **Residuo (pendientes del dueño, no bloqueantes):** commit del fix CORS
  (E3.24 verdea después), ratificar lectura N/A-post-C10 de los 6 FAIL
  E8.x de qa_c8, fila stale tras borrado 8b (opción abierta sin pin del
  arnés), variante CORS-LAN (a+) en la VM.
- **Insumos:** Schema §3.2/§3.3, AppFlow flujo 8/9, maquetas 03/04/05, TRD §2.3.

## Vigilancia de riesgos entre cortes (del 06)

| Riesgo | Corte donde se ataca |
|---|---|
| Proxy WS tras nginx (el más alto) | C1 (echo test desde el principio) y C7 (handler real) |
| Argon2id no nativo en Spring Security | C2 (si falla: librería madura, formato PHC intacto) |
| JWT en query param → logs de nginx | C7 (mitigación ya diseñada: token en primer frame, TRD §2.8) |
| Flyway + JSONB + TIMESTAMPTZ | C1 (migraciones aplican antes que ningún feature) |
| Ingesta sin rate limit bajo cliente ruidoso | C5 (límites por petición bastan en v1; tormenta → C-Fase3) |
| Scope creep post-1-usuario | Todos: toda añadidura = nueva versión del PRD, nunca aquí |