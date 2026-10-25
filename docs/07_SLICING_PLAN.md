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

### C4 — API keys de ingesta (`estado: hecho` 2026-10-24 · apply DONE 11/11 (+loop-back QP-07/QP-26), mvn 77/77, smoke 15/15 canónico, 3 commits 8437bdd→8cf0b9f (+ docs(SDD) de cierre); QA PASS global 30/30 (QP-30 opcional N-A); verify PASS-with-notes ×29/29 E4, 0 CRITICAL/3 W/3 S — huecos G-1/G-2 y LIKE-underscore anotados en archive-report; deudas retenidas de C3: smoke docker compose a C9 y 401 ISO-8859-1 a C8/C9, NINGUNA nueva)

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

### C6 — Consulta keyset + filtros + detalle (`estado: pendiente`)

- **Alcance:** `GET /logs` con filtros combinables (SQL literal del Schema
  §3.3, cursor keyset, LIMIT+1 → `{items, next_cursor?}`), `GET /logs/{id}`
  (LogDto completo con exception/metadata; la lista va SIN esos dos campos);
  400 `filter_from_to_inverted` y 404 según AppFlow §5.
- **Done cuando:** keyset paginado estable (insertar logs nuevos entre
  páginas NO duplica ni salta), filtros combinados y detalle con smoke.
- **Insumos:** Schema §3.3, AppFlow flujo 4/5 y flujo 6b (dedup ligada), TRD §2.4.

### C7 — Streaming WebSocket en vivo (`estado: pendiente`)

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

### C8 — Frontend del panel según maquetas (`estado: pendiente`)

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

### C9 — Demo + deploy + firewall (`estado: pendiente`)

- **Alcance:** subida de la pila a la VM; firewall Proxmox con reglas
  nuevas (SSH desde LAN + 8080 — GATE #11); arranque en frío y reinicio de
  la VM verificados; demo de aceptación Fase 1 con app real del homelab.
- **Done cuando (del 06):** los 3 criterios de la demo pasan manualmente
  (batch de 3 en vivo sin refresco; ERROR/production→stacktrace en <2 min;
  kill del backend→banner+reconexión con backoff, sin duplicados ni replay)
  y los smoke de backend y panel quedan automatizados.
- **Insumos:** 06 (demo y riesgos), TRD §6.7 (firewall), AppFlow flujo 6.

### C10 — Fase 2: borrados, resumen, acceso remoto (`estado: pendiente`)

- **Alcance:** `DELETE /projects/{id}` (`X-Confirm-Name`, CASCADE
  transaccional), `DELETE /logs/{id}` (`X-Confirm-Log-Id` — la UI del
  modal ya existe desde C8), `DELETE /projects/{id}/logs`
  (`X-Confirm-Name` → 200 `{deleted: N}`); resumen `include=summary` +
  `GET /projects/{id}/summary` (SummaryDto; tarjeta en `/proyectos` y
  bloque en ficha); doc operativo de acceso remoto (Tailscale) y
  preparación TLS (TRD §6.8).
- **Done cuando (del 06):** los 3 DELETE exigen doble confirmación (UI +
  cabecera) y devuelven los estados del AppFlow flujo 8; el resumen aparece
  en lista/ficha; el doc está en /docs o README operativo.
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