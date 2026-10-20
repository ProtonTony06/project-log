---
change: "C1"
artifact: apply-progress
last_updated: 2026-10-20
status: done
---

# Apply — C1 · Fase 0: Setup de pila y repo

> Registro de ejecución de sdd-apply. Contexto: repo greenfield, gate de la
> propuesta APROBADO (2026-10-20, 5/5). 26 tasks ejecutadas; 12 escenarios
> E1–E12 verificados con evidencia real. Estado final: **done** (con
> desviaciones de entorno documentadas en §2).

## 0. Estado del entorno detectado

- `docker` / `docker compose` **NO instalados** en el host Fedora 44
  (intento exacto: `docker --version` → `bash: docker: instrucción no encontrada`;
  `podman-compose` ausente — su instalación fue denegada por permisos del
  entorno; `systemctl is-active docker` → inactive). Sin sudo. → Orquestación
  equivalente con **podman 5.8.7 rootless** (herramienta nativa del host).
- Maven descargado a `~/.local/opt/apache-maven-3.9.16`; el JDK local es solo
  un **JRE 25 sin javac**, así que TODOS los builds Java se ejecutaron dentro
  de `maven:3.9-eclipse-temurin-21` (OpenJDK **21.0.12**), o sea el propio
  stage 1 del Dockerfile. Jar de 21 bits verificado en la imagen runtime.
- Peculiaridad de red: la JVM (TLS) dentro de contenedores recibe
  «Connection reset» contra Maven Central aunque curl funciona. Builds Maven
  en contenedor ejecutados MONTANDO `~/.m2` pre-poblada (por `mvn` del host)
  con `-o`. El POM y el Dockerfile no cambian por esto.
- Resolución short-name de podman (sin TTY) exige pull previo de
  `eclipse-temurin:21-jre` — particularidad de podman, no del Dockerfile.

## 1. Progreso por tasks (26/26)

### Fase F0 — Repo git y raíz
- [x] 1.1 `git init -b main` + `.gitignore` con regla negada `!.env.example`
      (R13) + dirs backend/frontend/docker/scripts.
- [x] 1.2 `.env.example` literal TRD §6.3 + comentarios de generación
      (`openssl rand -base64 48`/`24`), `ADMIN_INITIAL_PASSWORD` sin default,
      `FRONTEND_BIND` comentada. Grep: solo nombres del §6.3, sin secretos.
- [x] 1.3 README operativo (R14 completo). **COMMIT #1 `bd60b0d`** — exactamente
      `.gitignore` + `.env.example` + `README.md` (`git log --stat` verificado).

### Fase F1 — Backend Maven
- [x] 2.1 `backend/pom.xml`: parent **3.5.16** (último patch exacto de la
      línea 3.5.x vigente en Central; consultado, no inventado);
      `java.version=21` + `maven.compiler.release=21`; deps: web, actuator,
      websocket, **jdbc** (ver desviación D4), flyway-core +
      flyway-database-postgresql **11.7.2** (RG-3), postgresql runtime 42.7.11.
      `dependency:tree`: cero starter-test, cero deps de negocio.
- [x] 2.2 `Application.java` + 8 `package-info.java` con nombres L5 exactos
      (`auth projects keys ingest query streaming config common`); el de
      streaming marca EchoSocketHandler como infraestructura a sustituir por
      LogSocketHandler en C7; el de config anota contenido real C1.
- [x] 2.3 `application.yml` (DD-8 completo) + `META-INF/spring.factories`
      con la clave exacta `org.springframework.boot.env.EnvironmentPostProcessor`.
- [x] 2.4 `EchoSocketHandler` (eco del payload, sin estado) + `WebSocketConfig`
      (`@EnableWebSocket` + `registry.addHandler(echoSocketHandler, "/ws/logs")`
      — un único addHandler en el fuente, ruta carácter a carácter; sin
      SockJS ni interceptors).
- [x] 2.5 `StartupSecretsValidator`: EnvironmentPostProcessor, las 6 exigidas
      acumuladas sin cortar, blanco = fallo, JWT_SECRET ≥ 32 bytes UTF-8, UN
      único IllegalStateException. Grep: solo las 6; las 8 con default NO
      aparecen en el código (verificado).
- [x] 2.6 `V1__init.sql`: `diff` contra el bloque de Schema §6.1 = **0
      diferencias** (copia literal completa; trigram solo comentada).
- [x] 2.7 Build limpio del jar **dentro de maven:3.9-eclipse-temurin-21**
      (Java 21.0.12): `backend/target/project-log-backend-0.1.0.jar` (27.7 MB
      tras el fix D4); jar inspeccionado (spring.factories + V1 + classes +
      starter-jdbc/HikariCP + flyway 11.7.2). `backend/target/` no versionado.
      **COMMIT #2 `521582b`** (16 ficheros [5]–[20]).

### Fase F2 — Frontend Vite/React/TS
- [x] 3.1 Scaffold real con versiones fijadas consultadas a npm al aplicar:
      **Vite 7.3.7 · React 19.3.1 · TS 5.9.3 · plugin-react 5.2.0** +
      @types react 19.3.0. Placeholder "Project_Log — pila OK" con versión y
      enlaces a checks; cero llamadas a `/api/v1`.
- [x] 3.2 `npm install` → `package-lock.json` (lockfile v3); `npm ci &&
      npm run build` → exit 0 y `dist/index.html`. dist/ y node_modules/
      ignorados. **COMMIT #3 `c331148`** (9 ficheros, con el lock).

### Fase F3 — Docker
- [x] 4.1 `docker/Dockerfile` multi-stage igual al DD-4 (go-offline como capa,
      curl con comentario RG-4, sin HEALTHCHECK, EXPOSE documental).
- [x] 4.2 `frontend/Dockerfile` multi-stage (npm ci determinista). RG-4
      verificado ANTES de commit #4: `podman run --rm
      docker.io/library/nginx:stable-alpine which wget` → `/usr/bin/wget`
      (busybox 1.37). Fallback no necesario.
- [x] 4.3 `frontend/nginx.conf`: 5 puntos literales; greps: 2× `3600s`,
      `proxy_http_version 1.1`, Upgrade/Connection, `try_files $uri
      /index.html`, 2 proxy_pass SIN URI final, CERO `listen 443`/ssl/tls.
- [x] 4.4 `compose.yaml`: sin `version:`, sin `mem_limit`, un único `ports:`,
      healthchecks literales (pg_isready 10s/5s/5 · curl health 30s/5s/5 +
      start_period 60s · wget 15s/3s/3), depends_on service_healthy
      encadenado, `restart: unless-stopped` ×3, red `project_log_net`,
      volumen `pgdata` en `/var/lib/postgresql/data`. YAML asertado.
- [x] 4.5 `scripts/ws_echo_test.py`: stdlib solo (argparse/base64/hashlib/os/
      socket/struct/sys/time), handshake con validación EXACTA
      del Accept RFC 6455, eco mascarado byte a byte, close 0x8, inactividad
      `--idle-segundos 75` con contador, `--solo-handshake` y `--token` para
      C7. `chmod +x`, `--help` OK, `ast.parse` OK.
- [x] 4.6 Builds de imágenes con podman (equivalente a `docker compose build`;
      ver D3): `project-log-backend:c1` y `project-log-frontend:c1` OK.
      Tags exactos (grep: cero `latest`/flotantes). **COMMIT #4 `e861d7b`**
      ([21], [30], [31], [1], [32] + retoque [2]).

### Fase F4 — Verificación end-to-end (E1–E12)
- [x] 5.1 `.env` real: `cp .env.example .env && chmod 600 .env`, secretos con
      `openssl rand` (JWT 64 bytes ≥ 32; POSTGRES 24+ bytes de base64),
      `SPRING_DATASOURCE_USERNAME/PASSWORD` = `POSTGRES_USER/PASSWORD`.
      `stat -c %a .env` → **600**; `git status` no lo lista (check-ignore OK).
- [x] 5.2 **E1**: pila green en orden forzado postgres → backend → frontend
      (encadenado service_healthy simulado con monitores de healthchecks).
      Estado final `podman ps`: los 3 `Up ... (healthy)` estables, sin bucles.
- [x] 5.3 **E2**: `curl http://localhost:8080/` → **200 text/html** con el
      placeholder (título `Project_Log`, texto pila OK). `podman port backend`
      y `postgres` → vacíos (publican nada); único `ports:` en compose.
- [x] 5.4 **E3**: procedimiento DD-6 en frío (bajar pila + `podman volume rm
      project_log_pgdata` + cadena arriba). Logs: `Migrating schema "public"
      to version "1 - init"` y **`Successfully applied 1 migration`**.
      psql efímero sobre `project_log_net`: `\dt` → `users, projects, logs`
      (+ flyway_schema_history); fila V1 `success=t`;
      índices presentes `logs_dashboard_idx`, `logs_received_idx`,
      `logs_correlation_idx`, `projects_api_key_hash_idx`; **NO existe**
      `logs_message_trgm_idx`. Segundo arranque: `Schema "public" is up to
      date. No migration necessary.` (idempotencia).
- [x] 5.5 **E4**: quitados `JWT_SECRET` + `ADMIN_INITIAL_PASSWORD` del .env y
      backend recreado → **UN único** `IllegalStateException: ... faltan 2 de
      las 6 variables exigidas` que lista los DOS de golpe, antes de Tomcat
      (sin puerto abierto); health nunca llegó a green durante el fallo;
      bucle de reinicio `unless-stopped` (RG-6, esperado). Restaurado el
      `.env` (600) → backend vuelve a healthy (verificado por monitor).
      Además: arrancar la imagen SIN env produce el mismo error agregado con
      las 6 ausentes (evidencia extra, citada en el reporte).
- [x] 5.6 **E5**: `curl -i` con upgrade headers → `HTTP/1.1 101`,
      `Server: nginx/1.30.6`, `Sec-WebSocket-Accept:
      s3pPLMBiTxaQ9kYGzzhZRbK+xOo=` (exactamente el esperado para la key de
      ejemplo RFC 6455) → negociado por el bloque `/ws/logs`.
      **E6 + E7**: `python3 scripts/ws_echo_test.py` canónico completo →
      handshake 101 + Accept válido · eco full-duplex idéntico byte a byte ·
      cierre limpio 0x8 · **inactividad >75 s con la conexión viva** (con el
      default 60 s habría muerto). Modo `--sin-inactividad` también usado en
      iteraciones. RC=0 en ambos.
- [x] 5.7 **E8**: `curl -H "Accept: application/json"
      http://localhost:8080/api/v1/nonexistent` → **404 application/json**
      `{"timestamp","status":404,"error":"Not Found","path":...}` (JSON de
      Spring: el proxy alcanza al backend, no hay HTML de nginx).
      **E9**: `curl http://localhost:8080/ruta/de/panel/inexistente` →
      **200 text/html** con el index del placeholder (SPA fallback).
      **E10**: dentro de la red (exec): `GET /actuator/health` → **200**;
      `/actuator/info` → **200**; `/actuator/env` (no expuesto) → **404**.
      Desde el host `curl http://localhost:8080/actuator/health` → devuelve
      el index.html del SPA (el nginx NO enruta `/actuator/**`, L6).
- [x] 5.8 **E11**: `git status` limpio; `.env` (600) ignorado con
      `.gitignore:7:.env`; `.env.example` sí versionado; sin secretos reales
      en él (grep). **E12**: `mvn package` limpio dentro de temurin-21 (Java
      21.0.12, Maven 3.9.16); `npm ci && npm run build` limpio; builds podman
      de 2 imágenes con tags exactos; árbol de paquetes = TRD §2.1 (8
      package-info + Application). Cierre: `git ls-files` = **32 ficheros**
      que coinciden 1:1 con la lista numerada [1]–[32] del design §1.1 (ver
      desviación D6 sobre el «31» del design). `git status` limpio.

## 2. Desviaciones documentadas

- **D1 (`.gitignore`)**: reglas extra para `/docs/`, `/mockups/`, `.atl/`
  (artefactos pre-existentes al corte, ajenos al árbol de design §1.1) para
  que E11 dé árbol limpio; versionarlos queda como decisión del usuario.
- **D2 (build local)**: sin `mvn` ni `javac` en el host, todo build de
  verificación corre dentro de `maven:3.9-eclipse-temurin-21` (entorno del
  propio Dockerfile) montando `~/.m2` con `-o` por el problema de red JVM.
- **D3 (orquestación)**: sin Docker/Compose v2 (no instalados; instalación
  de podman-compose denegada por el entorno), la pila se levanta con `podman
  run` manual replicando EXACTAMENTE la topología del compose.yaml: red
  bridge `project_log_net`, aliases DNS de servicio (`postgres`, `backend`,
  `frontend`), healthchecks literales con mismas cadencias, encadenado
  postgres healthy → backend → frontend, volumen `project_log_pgdata`,
  restart unless-stopped. El `docker compose up` canónico queda pendiente del
  entorno del usuario (requisito del README).
- **D4 (POM — fix `2394c4c`)**: el design/spec omitían que Boot 3.5 NO
  autoconfigura el DataSource sin spring-jdbc+Hikari; sin él el arranque
  subía Tomcat **sin ejecutar Flyway** (E3 rojo). Se añade
  `spring-boot-starter-jdbc` (infra mínima, cero lógica de negocio). NOTA
  para sdd-verify: `spring-boot-starter-flyway` ya NO existe en 3.5.x (es de
  Boot 4; su POM da 404 en Central).
- **D5 (`frontend/src/index.css` — fix `26236f8`)**: había quedado un fichero
  fuera del árbol de design §1.1; eliminado (sin el import) y `npm run build`
  re-verificado. Árbol = design 1:1.
- **D6 (aritmética del design)**: design §1.1 afirma «31 ficheros» pero su
  propia lista numerada [1]–[32] contiene 32 items; con el lock comprometido
  (como exige el spec) el árbol real es 32. La lista de ficheros es la
  autoridad; el desglose del design no se reescribe.
- **D7 (`.env.example`)**: se añaden `SPRING_DATASOURCE_USERNAME` /
  `SPRING_DATASOURCE_PASSWORD` (la tabla §6.3 los exige y el fail-fast los
  valida, pero el bloque literal del ejemplo los omitía); valores por defecto
  coherentes con `POSTGRES_*`.

## 3. Commits (ramal `main`, sin remote, sin push)

| # | hash | mensaje |
|---|---|---|
| 1 | bd60b0d | chore: init repo — .gitignore, .env.example y README operativo |
| 2 | 521582b | feat(backend): esqueleto Maven — echo WS /ws/logs, fail-fast de secretos y V1 de Flyway |
| 3 | c331148 | feat(frontend): scaffold Vite/React/TS con placeholder de pila OK |
| 4 | e861d7b | feat(docker): pila compose — Dockerfiles multi-stage, nginx y echo test WS |
| fix | 2394c4c | fix(backend): starter-jdbc — DataSource mínimo para el autoarranque de Flyway (fallo E3) |
| fix | 26236f8 | fix(frontend): árbol 1:1 con design §1.1 — sin index.css fuera del árbol |

Los 6 llevan el trailer `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
Identidad repo-local provisional (`acuelop369 <acuelop369@homelab.local>`) al
no existir config global de git en el host — el usuario puede reescribirla.

## 4. Cómo se verificó cada E (evidencia)

| E | Resultado | Evidencia citada (salida real) |
|---|---|---|
| E1 | VERDE | `podman ps` → postgres/backend/frontend `Up (healthy)` en orden encadenado; monitores de healthcheck transicionaron `starting → healthy` |
| E2 | VERDE | `curl -w` → `200 text/html`; título placeholder; `podman port` vacío en backend/postgres |
| E3 | VERDE | log: `Successfully applied 1 migration` (frío, tras rm del volumen); psql: 3 tablas + fila V1 `success=t` + 4 índices + sin trigram |
| E4 | VERDE | UN `IllegalStateException` con «faltan 2 de las 6» + lista completa, antes de Tomcat; sin verde fugaz (health en `starting`); restaurado → healthy |
| E5 | VERDE | `HTTP/1.1 101` + `Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=` vía nginx/1.30.6 |
| E6 | VERDE | smoke: «Eco full-duplex: 3 frames ... idéntico byte a byte» + «Cierre limpio ... close frame 0x8 de vuelta» |
| E7 | VERDE | smoke completo: «Inactividad > 75 s: la conexión SIGUE viva» (RC=0); greps del nginx.conf confirman 2×3600s + http 1.1 + Upgrade/Connection |
| E8 | VERDE | `404 application/json` con cuerpo de error de Spring en `/api/v1/nonexistent` |
| E9 | VERDE | `200 text/html` con el index placeholder en ruta inexistente |
| E10 | VERDE | en red: health `200`, info `200`, env `404`; desde host `/actuator/health` sirve el SPA (proxy no lo enruta) |
| E11 | VERDE | `git status` limpio con `.env` 600; `.gitignore:7:.env · .env`; `!.env.example` presente; `.env.example` sin secretos |
| E12 | VERDE | jar 21 bits (Temurin 21.0.12) + `npm ci && npm run build` exit 0 + 2 imágenes reconstruidas con tags exactos; árbol = design 1:1 (32 ficheros, D6) |

## 5. Estado final de la pila (al cerrar esta fase)

- Pila green en local con podman: `project_log_postgres`, `project_log_backend`,
  `project_log_frontend` en `project_log_net` (volumen `project_log_pgdata`).
- El `.env` real sigue en la raíz con permisos 600 (ignorado por git).
- Pendiente de entorno del usuario: instalar Docker Engine + Compose v2 y
  ejecutar el flujo canónico del README (`docker compose up -d` + smoke);
  la configuración ya está probada equivalente con podman.