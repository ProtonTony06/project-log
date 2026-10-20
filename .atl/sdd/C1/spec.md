---
change: "C1"
artifact: spec
project: "Project_Log"
last_updated: 2026-10-20
status: done
---

# Spec — C1 · Fase 0: Setup de pila y repo

> Fuente: `proposal.md` (aprobado en gate 2026-10-20). Los literales de los
> docs de definición se **citan, nunca se redefinen** (regla del 07).
> C1 describe el estado NUEVO que C1 añade (no hay spec previa que modifiques).

## 1. Alcance — requisitos normativos (RFC 2119)

### R1 · Repo git y layout monorepo — MUST
El repositorio: DEBE nacer con `git init` y un primer commit que incluya `.gitignore`, `.env.example` y `README.md`. DEBE usar el layout raíz: `compose.yaml` (raíz), `.env.example`, `.gitignore`, `README.md`, `backend/` (Maven monomódulo), `docker/Dockerfile`, `frontend/` (Dockerfile + nginx.conf + scaffold Vite), `scripts/ws_echo_test.py`. El `.env` real (permisos 600) NO DEBE estar nunca en git. `groupId` = `com.projectlog`, `artifactId` = `project-log-backend`.

### R2 · Stack y versiones — MUST
DEBE usar Java 21 LTS (Eclipse Temurin) + Spring Boot 3.5.x (patch estable fijado en el POM) + Maven 3.9.x (`maven:3.9-eclipse-temurin-21`) + Node 22 LTS (`node:22-alpine`, solo builder) + Vite 7.x + React 19.x + TypeScript 5.x. TODOS los tags de imágenes y versiones del POM DEBEN ser exactos: prohibido `latest` o flotantes. Dependencias Maven mínimas: web, actuator, websocket, flyway, driver PostgreSQL — ninguna sobrante. La actualización de versiones DEBE ser manual (`docker compose build --pull`).

### R3 · Esqueleto de paquetes — MUST
DEBE crear los 8 paquetes de la convención package-by-feature como `package-info.java` documentativo: `auth`, `projects`, `keys`, `ingest`, `query`, `streaming`, `config`, `common` (nombres del TRD §2.1 — contrato). `streaming` DEBE llevar ya `EchoSocketHandler`; `config` DEBE llevar `WebSocketConfig`, el validador fail-fast y la configuración de Flyway/actuator.

### R4 · Echo test del WebSocket tras nginx — MUST
DEBE montar un handler temporal de echo (`TextWebSocketHandler` que devuelve cada frame) en la ruta definitiva `/ws/logs`, registrado con la misma mecánica nativa de C7 (`registerWebSocketHandlers` / `addHandler`, sin STOMP ni SockJS) y SIN `HandshakeInterceptor` ni auth. NO DEBE crear rutas nuevas (`/ws/echo` prohibido: viola TRD §9). El smoke `scripts/ws_echo_test.py` (Python stdlib, sin dependencias) DEBE verificar: (a) 101 vía nginx con `curl -i` y cabeceras de upgrade; (b) eco full-duplex de un frame; (c) conexión viva con inactividad > 75 s; y DEBE tener un modo `--solo-handshake` reutilizable en C7 sin reescritura. Se ejecuta SIEMPRE contra la pila completa (`docker compose up`), nunca en dev.

### R5 · Fail-fast de secretos — MUST
Un validador de arranque en `config/` DEBE abortar el arranque del backend (antes de que el puerto reciba tráfico) con UN único error claro que liste TODOS los secretos ausentes. En C1 DEBE exigir: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET` (con chequeo ≥ 32 bytes), `ADMIN_INITIAL_USERNAME`, `ADMIN_INITIAL_PASSWORD`. NO DEBE exigir las de default: `JWT_TTL_HOURS` (8), `INGEST_MAX_*`, `WS_HEARTBEAT_SECONDS`, `LOGGING_LEVEL`, `SPRING_PROFILES_ACTIVE`, `TZ`, `FRONTEND_ORIGIN`, `FRONTEND_BIND`.

### R6 · Migración V1 — MUST
`backend/src/main/resources/db/migration/V1__init.sql` DEBE ser copia literal y completa del Schema §6.1 (3 tablas + constraints + 4 índices), con la trigram comentada y reservada a V2. Flyway DEBE aplicarla automáticamente al arrancar el backend, con postgres healthy antes. Tras aplicarse, el fichero NO DEBE editarse jamás (checksum; cambios futuros = V2+).

### R7 · Frontend Vite/React/TS — MUST
DEBE generar un scaffold Vite + React + TS real (build `npm run build` → `dist/`) con un `index.html` placeholder marcado como temporal ("Project_Log — pila OK", con versión y enlaces a los checks), que la pila serve por el pipeline completo Vite → Dockerfile multi-stage → nginx → 8080. El placeholder DEBE sustituirse en C8 y no DEBE cargar nada por `/api/v1`.

### R8 · Dockerfiles multi-stage — MUST
`docker/Dockerfile` (nombre literal del 06) DEBE ser multi-stage: build con `maven:3.9-eclipse-temurin-21` → runtime `temurin-21-jre`; se construye con `build.context: ./backend` + `dockerfile: ../docker/Dockerfile`. `frontend/Dockerfile` DEBE ser multi-stage: build con `node:22-alpine` → imagen final `nginx:stable-alpine` con los estáticos.

### R9 · nginx.conf — MUST
DEBE contener los 5 puntos del TRD §6.2, literales: (1) SPA fallback `try_files … /index.html`; (2) `location /api/v1/ { proxy_pass http://backend:8080; }` — rutas relativas, cero CORS; (3) bloque `location /ws/logs` con `proxy_http_version 1.1`, `proxy_set_header Upgrade $http_upgrade`, `proxy_set_header Connection "upgrade"`, `proxy_read_timeout`/`proxy_send_timeout` 3600 s; (4) cabeceras `Host`/`X-Real-IP` en ambos proxys; (5) sin TLS.

### R10 · compose.yaml — MUST
DEBE declarar: `frontend` (`8080:80`, ÚNICO puerto del host), `backend`, `postgres` (`postgres:17-alpine`); Compose Spec sin clave `version:`; red interna única `project_log_net`; volumen nombrado `pgdata` en `/var/lib/postgresql/data`; healthchecks exactos del TRD §6.5 (`pg_isready` cada 10 s; `GET /actuator/health` → 200 cada 30 s; wget trivial sobre :80); `depends_on: service_healthy` encadenado postgres → backend → frontend; `restart: unless-stopped` en los 3; SIN `ports:` en backend/postgres; SIN `mem_limit` (ADR-27); sin secretos en el fichero.

### R11 · `.env.example` — MUST
DEBE ser copia textual del formato del TRD §6.3 (nombres cerrados), sin secretos reales, con comentarios que documenten la generación: `openssl rand -base64 48` para `JWT_SECRET` y `openssl rand -base64 24` para `POSTGRES_PASSWORD`; `ADMIN_INITIAL_PASSWORD` sin default. `FRONTEND_BIND` quedará comentada (opcional, default `0.0.0.0`).

### R12 · Actuator — MUST
`application.yml` DEBE exponer actuator solo `health,info` (contrato del healthcheck del TRD §6.5/§9).

### R13 · `.gitignore` — MUST
Raíz, monorepo: DEBE ignorar `.env` (y `.env.*` excepto `.env.example`), `backend/target/`, `frontend/node_modules/`, `frontend/dist/`, `.vite/`, IDE (`*.iml`, `.idea/`, `.vscode/`) y OS (`DS_Store`).

### R14 · `README.md` operativo — SHOULD
DEBE documentar: `cp .env.example .env && chmod 600 .env`, `docker compose up -d`, requisito Compose v2 (Docker Engine con plugin), cómo ejecutar el echo test y el procedimiento Flyway en frío. SHOULD evitar cualquier secreto en su texto.

### R15 · Prohibiciones del corte — MUST NOT
C1 NO DEBE: endpoints de negocio ni lógica (C2–C7), semilla del admin (C2 — Schema §6.3), consumir `FRONTEND_ORIGIN`, ejecutar la trigram, `mem_limit`, TLS, rate limit, backups, `latest` en imágenes/pomas, rutas WS o proxy nuevas, tocar el `V1__init.sql` tras aplicarlo, versionar `.env`.

## 2. Escenarios de aceptación (Given/When/Then)

### E1 · Pila arriba con healthchecks green (Done 06) — cubre R10
- GIVEN `.env` completo generado desde `.env.example`
- WHEN `docker compose up -d`
- THEN los 3 servicios llegan a healthy en orden postgres → backend → frontend y no se reinician en bucle.

### E2 · Ún puerto 8080 responde el placeholder (Done 06) — cubre R7, R8, R9, R10
- GIVEN pila verde del E1
- WHEN `curl http://localhost:8080/`
- THEN 200 con el `index.html` placeholder del build Vite; y `docker compose port backend/cualquier-puerto` no existe: backend y postgres no publican nada.

### E3 · Flyway V1 en frío (Done 06) — cubre R6
- GIVEN `docker compose down` + borrado del volumen `project_log_pgdata` (solo local) y `up` de nuevo
- THEN los logs del backend muestran Flyway aplicando V1 con éxito (`Successfully applied 1 migration`); desde un `psql` efímero sobre `project_log_net`: `\dt` muestra `users`, `projects`, `logs`, `flyway_schema_history` lista V1 en `success`, existen los 4 índices incluido `projects_api_key_hash_idx` único parcial y NO existe el índice trigram.

### E4 · Fail-fast verificado (Done 06) — cubre R5
- GIVEN pila arriba y un secreto exigido retirado del `.env` (p. ej. `JWT_SECRET`)
- WHEN `docker compose up -d` con la pila regenerada
- THEN el backend aborta el arranque con un único error que lista todos los secretos ausentes (ningún verde fugaz) y sin levantarse; si faltan varios, el error los lista todos de golpe; RESTORED el `.env` original, la pila vuelve a green.

### E5 · Handshake WS 101 a través de nginx — cubre R4, R9
- GIVEN pila verde
- WHEN `curl -i http://localhost:8080/ws/logs` con cabeceras de upgrade (`Connection: Upgrade`, `Upgrade: websocket`, `Sec-WebSocket-Key`)
- THEN respuesta `101 Switching Protocols` con `Sec-WebSocket-Accept` correcto (negociada por el bloque `/ws/logs` del nginx, no por desvío del proxy).

### E6 · Echo full-duplex — cubre R4
- GIVEN pila verde
- WHEN `python3 scripts/ws_echo_test.py` completa la cadena cliente → nginx → backend
- THEN recibe el eco idéntico de cada frame enviado y cierra limpio.

### E7 · Inactividad > 75 s — cubre R9
- GIVEN conexión WS abierta por E6 sin enviar frames 75 s
- THEN la conexión sigue viva (con el default de 60 s del proxy habría muerto); la revisión del `nginx.conf` confirma además los literales 3600 s + `proxy_http_version 1.1` + cabeceras Upgrade/Connection.

### E8 · Proxy `/api/v1` sin endpoints (comportamiento esperado) — cubre R9, R15
- GIVEN pila verde (C1 no tiene endpoints)
- WHEN `curl http://localhost:8080/api/v1/nonexistent`
- THEN un 404 con cuerpo JSON de Spring (no HTML de nginx): demuestra que el proxy alcanza al backend.

### E9 · SPA fallback — cubre R9
- GIVEN pila verde
- WHEN `curl http://localhost:8080/ruta/de/panel/inexistente`
- THEN 200 devolviendo `index.html` (el placeholder), sin 404 de nginx.

### E10 · Actuator solo `health,info` — cubre R12
- GIVEN el healthcheck interno del compose
- THEN `GET http://backend:8080/actuator/health` dentro de `project_log_net` devuelve 200, cualquier otro endpoint de actuator devuelve 404, y `/actuator/**` no es alcanzable desde el host (el proxy no lo enruta).

### E11 · `.env` fuera de git — cubre R1, R13
- GIVEN `cp .env.example .env && chmod 600 .env`
- WHEN `git status` e inspección de `.gitignore`
- THEN `.env` no aparece, `.env.example` sí, y `.env.example` no contiene ningún secreto real.

### E12 · Estructura y build verificables — cubre R2, R3
- WHEN `mvn -q -DskipTests package` y `npm run build`
- THEN ambos terminan limpios; el árbol de paquetes coincide con TRD §2.1 (8 paquetes) y `docker compose build` genera las 2 imágenes con tags exactos.

## 3. Contratos que este spec CITA (no redefine)

| Contrato | Fuente | Uso en C1 |
|---|---|---|
| `V1__init.sql` literal (3 tablas, constraints, 4 índices, trigram comentada) | 05_BACKEND_SCHEMA §6.1 (§6.2 para V2) | R6, E3 |
| Nombres y formato del `.env.example` | 02_TRD §6.3 | R5, R11, E4 |
| Nombres de paquetes (`auth…streaming` + `config`/`common`, capas web/service/repository) | 02_TRD §2.1 | R3, E12 |
| Bloque nginx `/ws/logs` (upgrade, timeouts 3600 s) | 02_TRD §6.2.3, ADR-18 | R9, E5, E7 |
| Rutas que el proxy enruta: solo `/api/v1/**` + `/ws/logs` | 02_TRD §9, ADR-5 | R4, R9, R15, E8, E10 |
| Healthchecks exactos y encadenamiento | 02_TRD §6.5, ADR-21 | R10, E1, E10 |
| Topología: único 8080, red `project_log_net`, volumen `pgdata`, `restart: unless-stopped`, sin `mem_limit` | 02_TRD §6.1, §6.4, ADR-16/19/24/27 | R10, E2 |

## 4. Fuera de alcance

- **C2**: SecurityFilterChain, Argon2id, JWT/filtro, seed del admin (Schema §6.3), `/auth/login`, `/me`, consumo de `FRONTEND_ORIGIN` (CORS).
- **C3/C4**: CRUD de proyectos + archivado; API keys (regeneración/revocación, formato `plk_*`).
- **C5/C6**: ingesta REST + batch (413/403/400, all-or-nothing, `202 {accepted: N}`); consulta keyset y detalle.
- **C7**: streaming real — `LogSocketHandler` + `HandshakeInterceptor` (JWT 401 pre-upgrade, set_filters, emisión tras COMMIT, heartbeat). El `EchoSocketHandler` de C1 es infraestructura de prueba del proxy, marcada para ser sustituido en C7 (que solo cambia el registro del handler y re-ejecuta el smoke en modo handshake).
- **C8**: panel real según maquetas (sustituye el placeholder del E2).
- **C9**: subida a la VM Proxmox + reglas de firewall (GATE #11) + verificación de arranque en frío en la VM; C1 valida en local por decisión del gate #5.
- **C10 / v2**: borrados definitivos, resumen, Tailscale; backups (§6.6), rate limiting (ADR-26), `mem_limit` (ADR-27), TLS (ADR-23), índice trigram (`V2__add_message_trigram.sql`, Schema §6.2 — no se ejecuta en C1).

## 5. Trazabilidad

| De | Ref | Hacia este spec |
|---|---|---|
| 06_IMPLEMENTATION_PLAN | "Done cuando" de Fase 0 (pila green en orden, 8080 con `index.html`, V1 en frío, fail-fast) | E1, E2, E3, E4 |
| 06_IMPLEMENTATION_PLAN | Ítems 1–8 de Fase 0 (repo, .env.example, docker/Dockerfile, frontend multi-stage, nginx.conf, compose, V1, actuator) | R1–R13 |
| 06_IMPLEMENTATION_PLAN | Riesgos #1 (WS tras nginx) y #2 (Flyway/JSONB/TIMESTAMPTZ) atacados en Fase 0 | R4, R6, E3–E7 |
| 07_SLICING_PLAN | Tabla C1 (Fase 0, sin stories, dependencias: ninguna) + detalle C1 (extra obligatoria: echo test WS) | §1/§2 completos |
| Gate de propuesta (2026-10-20) | Punto 1: versiones D1 tal cual; Punto 2: echo en `/ws/logs` sin ruta nueva; Punto 3: esqueleto de 8 paquetes; Punto 4: placeholder del build Vite; Punto 5: validación local + VM/firewall en C9 | R2, R4, R3, R7, §4 (C9) |