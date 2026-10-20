---
change: "C1"
artifact: tasks
last_updated: 2026-10-20
status: done
---

# Tasks — C1 · Fase 0: Setup de pila y repo

> Desglose mecánico para `sdd-apply` con **contexto fresco**. Cada task es
> autosuficiente: contiene los ficheros que toca (rutas del árbol [n] de
> `design.md` §1.1), las referencias exactas (R-n / E-n / DD-n / RG-n y § de
> los docs) y un criterio "Done" observable. Este artefacto NO reescribe
> literales; apunta a la sección exacta del doc donde se **copian**.
>
> Contexto del arranque: repo greenfield, árbol vacío. No hay código previo.
> El flujo es `git init` → backend → frontend → docker → verificación.

---

## 0. Recordatorios de contrato (leer ANTES de escribir nada)

### 0.1 Literales que se COPIAN de los docs, nunca se inventan

| # | Literal | Fuente exacta (ruta absoluta desde la raíz del proyecto) |
|---|---|---|
| L1 | `V1__init.sql` completo (3 tablas + constraints + 4 índices; trigram solo comentada, reservada a V2) | `/home/acuelop369/Escritorio/Proyectos/Project_Log/docs/05_BACKEND_SCHEMA.md` §6.1 |
| L2 | Bloque nginx `location /ws/logs` (Upgrade/Connection, `proxy_http_version 1.1`, `proxy_read_timeout`/`proxy_send_timeout` 3600s) | `/home/acuelop369/Escritorio/Proyectos/Project_Log/docs/02_TRD.md` §6.2.3 (ADR-18) |
| L3 | Healthchecks exactos y encadenamiento (`pg_isready` cada 10s; `curl -fsS http://127.0.0.1:8080/actuator/health` cada 30s; `wget --spider -q http://127.0.0.1/` cada 15s) | `docs/02_TRD.md` §6.5 (ADR-21) |
| L4 | Nombres y formato del `.env.example` (nombres de variables CERRADOS) | `docs/02_TRD.md` §6.3 |
| L5 | Nombres de los 8 paquetes (`auth`, `projects`, `keys`, `ingest`, `query`, `streaming`, `config`, `common`) | `docs/02_TRD.md` §2.1 |
| L6 | Rutas que el proxy enruta: SOLO `/api/v1/**` + `/ws/logs` | `docs/02_TRD.md` §9 (ADR-5) |
| L7 | `management.endpoints.web.exposure.include: health,info` | `docs/02_TRD.md` §6.5/§9 |
| L8 | Topología: único puerto host `8080:80`, red `project_log_net`, volumen `pgdata` en `/var/lib/postgresql/data`, `restart: unless-stopped`, sin `mem_limit` | `docs/02_TRD.md` §6.1, §6.4 (ADR-16/19/24/27) |
| L9 | Los otros 4 puntos del `nginx.conf` (listen/root, SPA `try_files $uri /index.html`, `location /api/v1/` con proxy_pass relativo, cabeceras Host/X-Real-IP/X-Forwarded-For, sin TLS) | `docs/02_TRD.md` §6.2.1–§6.2.5 |

### 0.2 Versiones EXACTAS aprobadas (gate 2026-10-20) — política RG-1

- Java **21 LTS** (Eclipse Temurin); `maven.compiler.release=21`.
- Spring Boot **3.5.x** — fijar el **patch exacto** vigente al aplicar en el
  `<parent><version>` del POM (NUNCA `3.5` flotante ni `LATEST`).
- Maven 3.9: imagen `maven:3.9-eclipse-temurin-21` (tag exacto).
- Runtime backend: `eclipse-temurin:21-jre` (JRE, no JDK).
- Node **22** LTS: `node:22-alpine` (solo builder).
- Vite **7**, React **19**, TypeScript **5.x** (fix exacto en `package.json`
  + `package-lock.json` comprometido → `npm ci`).
- nginx: `nginx:stable-alpine`; PostgreSQL: `postgres:17-alpine`.
- **RG-1**: prohibido `latest` o tags flotantes; no hay auto-update; la
  actualización de versiones es manual con `docker compose build --pull`.
- Dependencias Maven: EXACTAMENTE web, actuator, websocket, flyway-core,
  **flyway-database-postgresql**, postgresql (runtime). **Sin** starter-test.

### 0.3 Puntos del compose/nginx/validador que el spec deja cerrados

- Compose Spec sin clave `version:` (D10).
- `proxy_pass` **sin URI final** (`http://backend:8080;`) en los dos proxys
  (DD-2/DD-4): el prefijo llega intacto al backend.
- Healthchecks **en compose.yaml, nunca `HEALTHCHECK` en los Dockerfiles** (DD-5).
- `FRONTEND_BIND` interpolada como `"${FRONTEND_BIND:-0.0.0.0}:8080:80"` y
  **comentada** en `.env.example` (R11, DD-5).
- Fail-fast = `EnvironmentPostProcessor` + `META-INF/spring.factories`
  (DD-3): **UN único** `IllegalStateException` que agrega los 6 secretos
  ausentes; se recorren las 6 sin cortar en la primera; chequeo `JWT_SECRET`
  ≥ 32 **bytes** UTF-8; nunca lista parcial.
- Fuera de C1 (no anticipar): sin `SecurityFilterChain`, sin seed del admin,
  sin JWT, sin `FRONTEND_ORIGIN` en `application.yml`, sin heartbeat WS,
  sin tests (`spring-boot-starter-test` NO entra), sin endpoints de negocio.
- Trailer obligatorio en cada commit (ver §3): `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

---

## 1. Grafo de dependencias (orden de ejecución)

```
1.1 git init + .gitignore
 ├── 1.2 .env.example   ┐
 └── 1.3 README.md      ┘── COMMIT #1
2.1 pom.xml ── 2.2 Application + package-info ── 2.3 application.yml + spring.factories
 ── 2.4 EchoSocketHandler + WebSocketConfig ─┐
 ── 2.5 StartupSecretsValidator ──────────────┤
 ── 2.6 V1__init.sql (independiente dentro de F1) ── 2.7 mvn package ── COMMIT #2
3.1 scaffold Vite ── 3.2 npm run build + lock ── COMMIT #3
4.1 docker/Dockerfile; 4.3 nginx.conf; 4.4 compose.yaml; 4.5 ws_echo_test.py
 (4.2 frontend/Dockerfile depende de 3.2; 4.1 de 2.7; 4.4 de 4.1-4.3)
 ── 4.6 docker compose build + config ── COMMIT #4
5.1–5.8 verificación E1–E12 (sin commit; fixes → commits de fix)
```

Bloqueos clave: F1 bloquea 4.1 y 2.7 bloquea 4.6; F2 bloquea 4.2; F3 completo
bloquea F4; F4 bloquea el "Done" de toda la fase (§5).

---

## 2. Tasks

### Fase F0 — Repo git y raíz (bloquea todo lo demás)

#### 1.1 · Iniciar repo y crear `.gitignore` raíz

- **Ficheros**: `.gitignore` ([3] del árbol design); directorios vacíos
  `backend/`, `frontend/`, `docker/`, `scripts/` (solo para fijar el layout; se
  rellenan después). Ejecutar `git init` en la raíz del proyecto.
- **Cubre**: R1 (git init + layout raíz), R13, E11.
- **Contenido de `.gitignore`** (reglas de R13, raíz monorepo): `.env`,
  `.env.*` EXCEPTO `.env.example` (patrón negado `!.env.example`),
  `backend/target/`, `frontend/node_modules/`, `frontend/dist/`, `.vite/`,
  IDE: `*.iml`, `.idea/`, `.vscode/`; OS: `.DS_Store` (y `DS_Store`).
- **Done**: `git init` ejecutado; `git status` funciona en la raíz; el
  `.gitignore` contiene la regla negada `!.env.example` (sin ella, el patrón
  `.env.*` la ignoraría y violaría E11).

#### 1.2 · Crear `.env.example` (copia literal del formato TRD §6.3)

- **Ficheros**: `.env.example` ([2]).
- **Cubre**: R11, R5 (nombres de las 6 variables exigidas), E11.
- **Fuente**: L4 — `docs/02_TRD.md` §6.3. Copiar el bloque textual con los
  nombres de variables EXACTOS, sin ningún secreto real, con los comentarios
  de generación: `JWT_SECRET` → `openssl rand -base64 48`;
  `POSTGRES_PASSWORD` → `openssl rand -base64 24`; `ADMIN_INITIAL_PASSWORD`
  sin default; `FRONTEND_BIND` **comentada** (opcional, default `0.0.0.0`);
  con default (no exigidas por el validador): `JWT_TTL_HOURS` (8),
  `INGEST_MAX_*`, `WS_HEARTBEAT_SECONDS`, `LOGGING_LEVEL`,
  `SPRING_PROFILES_ACTIVE`, `TZ`.
- **Depende de**: 1.1.
- **Done**: los nombres de variables coinciden 1:1 con TRD §6.3; `grep` no
  encuentra ningún valor que parezca secreto real; `git status` la lista
  (gracias a `!.env.example`).

#### 1.3 · Crear `README.md` operativo y COMMIT #1

- **Ficheros**: `README.md` ([4]).
- **Cubre**: R14, R1 (incluido en el primer commit), E11; documentar RG-6 y RG-8.
- **Contenido mínimo** (R14): requisito **Compose v2** (plugin de Docker
  Engine); procedimiento: `cp .env.example .env && chmod 600 .env` →
  `docker compose up -d`; cómo ejecutar el echo test
  (`python3 scripts/ws_echo_test.py` contra la pila levantada — nunca en dev,
  R4); procedimiento Flyway en frío (DD-6 / E3: `docker compose down` +
  `docker volume rm project_log_pgdata` SOLO en local, nunca en la VM);
  lectura de diagnóstico del fail-fast: `docker compose logs backend` muestra
  el ÚNICO error agregado (RG-6 — el bucle de reinicio con
  `unless-stopped` es el comportamiento esperado);
  el curl canónico del proxy con `Accept: application/json` (RG-8).
  SIN ningún secreto en el texto (R14/R15).
- **Commit #1** (R1: primer commit = `.gitignore` + `.env.example` + `README.md`):
  `chore: init repo — .gitignore, .env.example y README operativo`
  + trailer §0.3.
- **Depende de**: 1.1, 1.2.
- **Done**: `git log --stat` muestra el commit con exactamente esos 3
  ficheros; el README describe los 4 puntos de R14.

---

### Fase F1 — Backend Maven (bloquea F3 4.1)

#### 2.1 · Crear `backend/pom.xml`

- **Ficheros**: `backend/pom.xml` ([5]).
- **Cubre**: R2, DD-1, E12; RG-1 (patch fijo), RG-3 (flyway-database-postgresql).
- **Contenido**: parent `spring-boot-starter-parent` con versión **patch
  exacta** de la línea 3.5.x vigente (§0.2); propiedades
  `java.version=21` y `maven.compiler.release=21` (si la línea del parent no
  la propaga, fijar `<maven.compiler.release>21</maven.compiler.release>`);
  `groupId=com.projectlog`, `artifactId=project-log-backend` (R1);
  dependencias EXACTAS (§0.2): `spring-boot-starter-web`,
  `spring-boot-starter-actuator`, `spring-boot-starter-websocket`,
  `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`
  (obligatorio en Flyway 11.x/SB 3.5 — RG-3; sin él: "No database found to
  handle jdbc:postgresql://…"), `org.postgresql:postgresql` (scope runtime).
  SIN `spring-boot-starter-test`.
- **Depende de**: 1.1.
- **Done**: `mvn -q -DskipTests -f backend/pom.xml dependency:resolve`
  termina limpio; `mvn dependency:tree` NO muestra starter-test ni ninguna
  dependencia sobrante de negocio; la versión del parent es un patch exacto
  (no flotante).

#### 2.2 · Crear `Application.java` y los 8 `package-info.java`

- **Ficheros**: `backend/src/main/java/com/projectlog/Application.java` ([6]);
  `backend/src/main/java/com/projectlog/{auth,projects,keys,ingest,query,streaming,config,common}/package-info.java`
  ([7]–[14]).
- **Cubre**: R3, DD-1, E12 (árbol = TRD §2.1, L5).
- **Contenido**: `Application` con solo `@SpringBootApplication` en la raíz
  `com.projectlog` (nivel que escanea los 8 paquetes); cada `package-info`
  con Javadoc de 1–3 líneas descriptivo (sin código). Notas obligatorias:
  el package-info de `streaming` anota que `EchoSocketHandler` es
  infraestructura de prueba del proxy **a sustituir por `LogSocketHandler`
  en C7**; el de `config` anota que su contenido real en C1 son
  `WebSocketConfig` + `StartupSecretsValidator` y que la config de
  Flyway/actuator vive en `application.yml` (props, no beans).
- **Depende de**: 2.1 (para que compile desde el primer build).
- **Done**: el paquete raíz contiene exactamente 8 subpaquetes con los
  nombres de L5 y `Application.java`; `mvn -q -DskipTests compile` limpio.

#### 2.3 · Crear `application.yml` y `META-INF/spring.factories`

- **Ficheros**: `backend/src/main/resources/application.yml` ([18]);
  `backend/src/main/resources/META-INF/spring.factories` ([19]).
- **Cubre**: R12, DD-8, DD-3 (registro), E10.
- **Contenido de `application.yml`** (DD-8, forma cerrada): `server.port: 8080`;
  `spring.datasource.url/username/password` desde
  `${SPRING_DATASOURCE_URL}` / `${SPRING_DATASOURCE_USERNAME}` /
  `${SPRING_DATASOURCE_PASSWORD}`; `spring.flyway.enabled: true` (explícito);
  `spring.jpa.hibernate.ddl-auto: none` (el esquema es de V1, nada más lo
  toca — DD-6); `management.endpoints.web.exposure.include: health,info`
  (L7); `logging.level.root: ${LOGGING_LEVEL:INFO}`. **Sin** seguridad, sin
  `FRONTEND_ORIGIN`, sin `WS_HEARTBEAT_SECONDS`, sin límites de ingesta (§6
  del design).
- **Contenido de `spring.factories`**: clave
  `org.springframework.boot.env.EnvironmentPostProcessor` → FQCN de
  `com.projectlog.config.StartupSecretsValidator` (DD-3). Es el mecanismo
  oficial de Boot 3.x; sin este fichero el validador NO corre.
- **Depende de**: 2.1.
- **Done**: el YAML carga sin error; `exposure.include` = `health,info`
  literal; la clave de `spring.factories` es exacta.

#### 2.4 · Crear `EchoSocketHandler` y `WebSocketConfig` (echo en `/ws/logs`)

- **Ficheros**: `backend/src/main/java/com/projectlog/streaming/EchoSocketHandler.java`
  ([15]); `backend/src/main/java/com/projectlog/config/WebSocketConfig.java` ([16]).
- **Cubre**: R4, R15 (sin rutas nuevas), DD-2; E5, E6.
- **Contenido** (DD-2, comportamiento cerrado):
  - `EchoSocketHandler extends TextWebSocketHandler`: solo
    `handleTextMessage` → `session.sendMessage(new TextMessage(message.getPayload()))`.
    Sin broadcast, sin estado, sin `ConcurrentWebSocketSessionDecorator`
    (mecanismo de C7), sin gestionar frames binarios.
  - `WebSocketConfig`: `@Configuration @EnableWebSocket`, implementa
    `WebSocketConfigurer`, en `registerWebSocketHandlers`:
    `registry.addHandler(echoSocketHandler(), "/ws/logs")`. Sin `.withSockJS()`
    (ADR-5), sin `.addInterceptors(...)` (C7). Ruta **definitiva** `/ws/logs`
    carácter a carácter (coincide con el `location` del nginx, L2/L6);
    **prohibido** `/ws/echo` (R4/R15).
- **Depende de**: 2.2 (paquetes), 2.1 (starter-websocket).
- **Done**: `mvn -q -DskipTests compile` limpio; el handler está registrado
  EXACTAMENTE en `/ws/logs` y no existe ninguna otra ruta WS (grep del
  fuente: un único `addHandler`).

#### 2.5 · Crear `StartupSecretsValidator` (fail-fast)

- **Ficheros**: `backend/src/main/java/com/projectlog/config/StartupSecretsValidator.java` ([17]).
- **Cubre**: R5, DD-3, E4.
- **Contenido** (contrato cerrado DD-3): implementa
  `EnvironmentPostProcessor`; lee del `Environment` las **6 exigidas** de R5:
  `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
  `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`,
  `ADMIN_INITIAL_USERNAME`, `ADMIN_INITIAL_PASSWORD`; fallan si ausentes **o
  en blanco** (null/vacío/espacios); chequeo extra: `JWT_SECRET` ≥ **32
  bytes UTF-8**; recorre las 6 **sin cortar en la primera**, acumula, y con
  fallos lanza **UN único `IllegalStateException`** cuyo mensaje lista todas
  las ausentes (nombre corto + qué se esperaba; NUNCA secretos ni lista
  parcial). No valida ni exige ninguna de las 8 con default (R5). NO adelanta
  C2: sin firma JWT, sin seed, sin SecurityFilterChain — solo presencia de
  config. Corre antes del refresh del contexto: nada de beans concretados
  hasta validar (no puede haber verde fugaz).
- **Depende de**: 2.2, 2.3 (registro en spring.factories).
- **Done**: una prueba manual con un secreto retirado arranca el backend y
  aborta con UN único mensaje agregado antes de que ningún bean arranque
  (se verificará de verdad en 5.5 / E4); `grep` confirma que las 6 variables
  exigidas están en el código y las 8 con default NO lo están.

#### 2.6 · Crear `V1__init.sql` (copia literal)

- **Ficheros**: `backend/src/main/resources/db/migration/V1__init.sql` ([20]).
- **Cubre**: R6, DD-6, E3.
- **Fuente**: L1 — `docs/05_BACKEND_SCHEMA.md` §6.1. Copia **literal y
  completa**: 3 tablas (`users`, `projects`, `logs`) + constraints + los 4
  índices (`logs_dashboard_idx`, `logs_received_idx`, `logs_correlation_idx`,
  `projects_api_key_hash_idx` único parcial); la trigram
  (`logs_message_trgm_idx`) SOLO comentada, reservada a V2 (§6.2).
  Ubicación = `classpath:db/migration` (default Flyway, cero config extra).
  Tras aplicarse NO se edita jamás (checksum; cambios futuros = V2+, R6/R15).
- **Depende de**: 2.1 (independiente del resto de F1).
- **Done**: `diff` del fichero contra el bloque del Schema §6.1 = cero
  diferencias (exigido por el paso 4 del plan del 06); el índice trigram
  aparece solo comentado en el fichero; el nombre es exactamente
  `V1__init.sql`.

#### 2.7 · Verificar build local del backend y COMMIT #2

- **Ficheros**: ninguno nuevo (verificación sobre todo F1).
- **Cubre**: E12 (mvn), R2.
- **Acción**: `mvn -q -DskipTests -f backend/pom.xml package` → debe terminar
  limpio y producir el jar en `backend/target/`.
- **Depende de**: 2.1–2.6.
- **Commit #2**: `feat(backend): esqueleto Maven — echo WS /ws/logs, fail-fast de secretos y V1 de Flyway`
  + trailer §0.3.
- **Done**: `mvn -q -DskipTests package` retorno 0; `git status` tras el
  commit no muestra `backend/target/` (lo cubre el `.gitignore` de 1.1);
  `git log` contiene el commit con los ficheros [5]–[20].

---

### Fase F2 — Frontend Vite/React/TS (bloquea F3 4.2)

#### 3.1 · Scaffold Vite + React + TS y placeholder

- **Ficheros**: `frontend/package.json` ([22]); `frontend/tsconfig.json` ([25]);
  `frontend/vite.config.ts` ([24]); `frontend/index.html` ([26]);
  `frontend/src/main.tsx` ([27]); `frontend/src/App.tsx` ([28]);
  `frontend/src/vite-env.d.ts` ([29]); `frontend/package-lock.json` ([23],
  generado por `npm install`, NO a mano).
- **Cubre**: R7, DD-1, E2, E12.
- **Versiones** (§0.2): Vite 7, React 19, TS 5.x — fix exactos en
  `package.json`; el lock comprometido hace `npm ci` determinista (DD-4).
- **Placeholder** (R7): `index.html` placeholder con texto
  **"Project_Log — pila OK"** más versión y enlaces a los checks (marcado
  como temporal; se sustituye en C8); `main.tsx` bootstrap React mínimo;
  `App.tsx` el contenido del placeholder. El placeholder **no carga nada por
  `/api/v1`** (R7/R15).
- **Depende de**: 1.1.
- **Done**: `npm install` genera `package-lock.json` versión coherente con el
  `package.json`; el scaffold es un proyecto Vite real (no un HTML suelto).

#### 3.2 · Verificar `npm run build` y COMMIT #3

- **Ficheros**: ninguno nuevo (verificación).
- **Cubre**: E12 (npm run build), R7 (build → `dist/`).
- **Acción**: `cd frontend && npm ci && npm run build` → `dist/` generado.
- **Depende de**: 3.1.
- **Commit #3**: `feat(frontend): scaffold Vite/React/TS con placeholder de pila OK`
  + trailer §0.3.
- **Done**: `npm run build` retorno 0 y `frontend/dist/index.html` existe;
  `git status` tras el commit no muestra `dist/` ni `node_modules/`
  (.gitignore de 1.1); el commit incluye el `package-lock.json`.

---

### Fase F3 — Docker: Dockerfiles, nginx, compose y echo test (bloquea F4)

#### 4.1 · Crear `docker/Dockerfile` (backend, multi-stage)

- **Ficheros**: `docker/Dockerfile` ([21]) — nombre literal del 06 ítem 3.
- **Cubre**: R8, DD-4, RG-4 (curl).
- **Contenido** (DD-4, literal del design): stage 1 `FROM
  maven:3.9-eclipse-temurin-21 AS build`, `WORKDIR /app`, `COPY pom.xml .`,
  `RUN mvn -q -DskipTests dependency:go-offline` (cache de deps como capa
  propia — builds iterativos de C2–C7 rapidísimos), `COPY src ./src`,
  `RUN mvn -q -DskipTests package`; stage 2 `FROM eclipse-temurin:21-jre`,
  `apt-get` install `curl` **con comentario de que es solo para el
  healthcheck del TRD §6.5** (la imagen Temurin no lo trae — RG-4),
  `COPY --from=build /app/target/*.jar /app/app.jar`, `EXPOSE 8080`
  (documental), `ENTRYPOINT ["java","-jar","/app/app.jar"]`.
  **Sin `HEALTHCHECK`** (viven en compose, DD-5). Se construye con
  `build.context: ./backend` + `dockerfile: ../docker/Dockerfile` (4.4).
- **Depende de**: 2.7 (F1 completo).
- **Done**: `docker build` del stage 1+2 termina limpio; la imagen final NO
  contiene ni maven ni JDK; contiene `curl` (`docker run --rm <img> which curl`).

#### 4.2 · Crear `frontend/Dockerfile` (multi-stage node → nginx)

- **Ficheros**: `frontend/Dockerfile` ([30]).
- **Cubre**: R8, DD-4, RG-4 (wget busybox).
- **Contenido** (DD-4): stage 1 `FROM node:22-alpine AS build`, `WORKDIR
  /app`, `COPY package.json package-lock.json ./`, `RUN npm ci`
  (determinista — jamás `npm install` en la imagen), `COPY . .`,
  `RUN npm run build`; stage 2 `FROM nginx:stable-alpine`, `COPY nginx.conf
  /etc/nginx/conf.d/default.conf`, `COPY --from=build /app/dist
  /usr/share/nginx/html`, `EXPOSE 80`. **Sin `HEALTHCHECK`** (DD-5).
  La imagen final solo tiene nginx + estáticos (sin Node).
- **Depende de**: 3.2 (lock existe y build funciona).
- **Acción de verificación previa (RG-4)**: `docker run --rm
  nginx:stable-alpine which wget` debe devolver ruta (busybox wget de
  serie). Si NO existiera, aplicar el fallback documentado del design
  (instalar curl en el stage nginx y usar curl en el healthcheck) como
  decisión ÚNICA y documentada en el commit — nunca cambiar a mitades del corte.
- **Done**: `docker run --rm nginx:stable-alpine which wget` devuelve una
  ruta; el build multi-stage termina y la imagen final NO tiene node.

#### 4.3 · Crear `frontend/nginx.conf`

- **Ficheros**: `frontend/nginx.conf` ([31]).
- **Cubre**: R9 (los 5 puntos), DD-4; E5, E7, E8, E9; RG-5, RG-8.
- **Contenido**: los 5 puntos LITERALES del TRD §6.2 (L2 + L9), con un
  `server { listen 80; root /usr/share/nginx/html; index index.html; }`:
  1. SPA fallback literal §6.2.1: `location / { try_files $uri /index.html; }` (E9).
  2. Proxy API literal §6.2.2: `location /api/v1/ { proxy_pass
     http://backend:8080; }` — **sin URI final** tras host:puerto (el
     prefijo `/api/v1/` llega INTACTO al backend, DD-2/DD-4; trocearlo
     rompería el mapping de controllers de C2–C6) y rutas relativas, cero CORS (E8).
  3. Bloque WS literal §6.2.3/ADR-18 (L2): `location /ws/logs` con
     `proxy_http_version 1.1`, `proxy_set_header Upgrade $http_upgrade`,
     `proxy_set_header Connection "upgrade"` (fijo, sin `map $http_upgrade`
     — literal del spec; DD-4), `proxy_read_timeout 3600s`,
     `proxy_send_timeout 3600s` (E5/E7; el default 60s de nginx moriría — RG-5).
  4. Cabeceras en AMBOS proxys: `proxy_set_header Host $host;`,
     `proxy_set_header X-Real-IP $remote_addr;` + `X-Forwarded-For`
     (§6.2.4); `X-Forwarded-Proto $scheme` como práctica mínima.
  5. `proxy_pass http://backend:8080;` sin URI final y **sin TLS en todo el
     conf** (§6.2.5 / ADR-23).
  El nginx NO enruta `/actuator/**` (solo `/api/v1/**` + `/ws/logs`, L6) — E10.
- **Depende de**: 1.1 (4.3 junto a 4.2 lo consume).
- **Done**: el fichero contiene los 5 puntos; grep confirma
  `3600s` en ambos timeouts, `proxy_http_version 1.1`, las cabeceras
  Upgrade/Connection, `try_files $uri /index.html` y CERO apariciones de
  `listen 443`/ssl/certificado.

#### 4.4 · Crear `compose.yaml` (raíz)

- **Ficheros**: `compose.yaml` ([1]).
- **Cubre**: R10, DD-5; E1, E2, E10; L3, L4, L8.
- **Contenido** (DD-5, cerrado):
  - **Sin** clave `version:` (D10, Compose Spec v2).
  - `backend`: `build: { context: ./backend, dockerfile: ../docker/Dockerfile }`
    (R8/D3); `depends_on: { postgres: { condition: service_healthy } }`;
    `environment` con las vars del backend del TRD §6.3 (interpoladas del
    `.env`, sin secretos en el fichero); healthcheck LITERAL L3:
    `curl -fsS http://127.0.0.1:8080/actuator/health` con `interval: 30s`,
    `timeout: 5s`, `retries: 5`, `start_period: 60s` (margen JVM+Flyway);
    **sin `ports:`**.
  - `frontend`: `build: { context: ./frontend }`;
    `depends_on: { backend: { condition: service_healthy } }`; ÚNICO
    `ports:` de la pila: `"${FRONTEND_BIND:-0.0.0.0}:8080:80"` (con
    `FRONTEND_BIND` comentada en `.env.example` resuelve exactamente
    `8080:80` — ADR-24, DD-5); healthcheck LITERAL L3:
    `wget --spider -q http://127.0.0.1/` con `interval: 15s`, `timeout: 3s`,
    `retries: 3`.
  - `postgres`: imagen `postgres:17-alpine`; `environment`
    `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD` del `.env`; healthcheck
    LITERAL L3: `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB` con
    `interval: 10s`, `timeout: 5s`, `retries: 5`; **sin `ports:`**.
  - `restart: unless-stopped` en los 3 (§6.1).
  - Red única `project_log_net` (bridge) conectada a los 3 (los nombres
    `postgres`/`backend` resuelven por DNS tal como los exigen
    `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/…` y
    `proxy_pass http://backend:8080`).
  - Volumen nombrado `pgdata` montado en `/var/lib/postgresql/data` (§6.4;
    en local vive como `project_log_pgdata`, E3).
  - **Sin** `mem_limit` (ADR-27), **sin secretos reales** en el fichero,
    **sin `HEALTHCHECK` duplicados** (solo aquí, no en Dockerfiles).
- **Depende de**: 4.1, 4.2, 4.3 (referenciados).
- **Done**: `docker compose config` valida sin errores ni warnings graves;
  `docker compose config --services` devuelve exactamente `postgres`,
  `backend`, `frontend`; grep confirma un único bloque `ports:` en todo el
  fichero (el `8080:80` del frontend) y cero apariciones de `mem_limit` y
  `version:`.

#### 4.5 · Crear `scripts/ws_echo_test.py` (smoke WS stdlib)

- **Ficheros**: `scripts/ws_echo_test.py` ([32]).
- **Cubre**: R4, DD-7; E5, E6, E7; RG-5, RG-7.
- **Contenido** (protocolo cerrado DD-7): Python **stdlib solo**
  (`socket`, `base64`, `hashlib`, `os`, `struct`, `argparse`, `sys`,
  `time`) — sin websocket-client, sin websocat, sin Node. Shebang
  `#!/usr/bin/env python3`, `chmod +x`, no entra en ninguna imagen.
  CLI: `python3 scripts/ws_echo_test.py [--host HOST] [--puerto PUERTO]
  [--solo-handshake] [--sin-inactividad] [--idle-segundos 75]
  [--payload TEXT] [--token TOKEN]`.
  - **Paso 1 · Handshake** (común a ambos modos): envía a pelo
    `GET /ws/logs HTTP/1.1` con `Upgrade: websocket`, `Connection: Upgrade`,
    `Sec-WebSocket-Key` (base64 de 16 bytes aleatorios),
    `Sec-WebSocket-Version: 13`; verifica status `101 Switching Protocols`
    y que `Sec-WebSocket-Accept` sea EXACTAMENTE
    `base64(sha1(Sec-WebSocket-Key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))`
    (GUID fijo RFC 6455); si el Accept no es correcto, aborta. Con
    `--solo-handshake` termina aquí (modo reutilizable en C7 sin
    reescritura — R4). El `--token` existe como CLI pero en C1 NUNCA se
    pasa (en C7 añade `?token=…` al path del handshake).
  - **Paso 2 · Eco full-duplex** (modo completo): N frames de texto
    MASCARADOS (obligación RFC 6455 del cliente; el servidor responde sin
    máscara), opcode 0x1 FIN=1; parsea cada respuesta (opcode, longitud,
    payload) y exige eco idéntico byte a byte y opcode texto; cierra con
    frame close 0x8 y espera el close de vuelta (cierre limpio).
  - **Paso 3 · Inactividad > 75 s** (modo completo): con la conexión abierta
    no envía nada durante `--idle-segundos` (default 75) y luego exige eco
    de un frame de comprobación → discrimina el timeout real del nginx
    (default 60s mataría a los 60 s; con 3600s vive — RG-5). Con
    `--sin-inactividad` se salta (iteraciones rápidas; el smoke canónico del
    gate de archive lo incluye siempre).
  - Sin secretos, sin estado, idempotente ante reejecución (RG-7).
- **Depende de**: 1.1 (dir `scripts/`); se ejecuta en F4.
- **Done**: `python3 -c "import ast; ..."` o un `python3
  scripts/ws_echo_test.py --help` corre sin errores de sintaxis; grep no
  encuentra import de librerías no-stdlib; el fichero es ejecutable
  (`ls -l` muestra la x).

#### 4.6 · Verificar `docker compose build` y COMMIT #4

- **Ficheros**: ninguno nuevo (verificación).
- **Cubre**: E12 (2 imágenes con tags exactos), R2, RG-1.
- **Acción**: `docker compose build` (o `docker compose build backend
  frontend`) → ambas imágenes sin errores.
- **Depende de**: todo F3 (4.1–4.5) y transitivamente F1 (2.7) + F2 (3.2).
- **Commit #4**: `feat(docker): pila compose — Dockerfiles multi-stage, nginx y echo test WS`
  + trailer §0.3.
- **Done**: `docker compose build` retorno 0; `docker compose images` (o
  `docker images`) muestra las 2 imágenes construidas; los tags usados en el
  build son los de §0.2 (grep en los 3 ficheros de docker no encuentra
  `latest` ni tags flotantes cruzando mayor); el commit incluye
  `docker/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`,
  `compose.yaml` y `scripts/ws_echo_test.py`.

---

### Fase F4 — Verificación end-to-end (sin commit; fixes → commits de fix)

> Regla de fase: si un escenario sale rojo, volver a apply con el fallo
> concreto, corregir y RE-ejecutar desde el escenario afectado hacia
> adelante. Ningún "Done" de fase de verificación sin E1–E12 todos verdes (§5).

#### 5.1 · Preparar `.env` real (permisos 600)

- **Acción**: `cp .env.example .env && chmod 600 .env`; generar los valores
  reales (`openssl rand -base64 48` para `JWT_SECRET`; `openssl rand
  -base64 24` para `POSTGRES_PASSWORD`; credenciales admin; la URL
  `jdbc:postgresql://postgres:5432/<db>` con el host `postgres`).
- **Cubre**: R14, R11; E11.
- **Depende de**: COMMIT #4.
- **Done**: `stat -c %a .env` = 600; `git status` NO lista `.env` y SÍ
  lista `.env.example` (E11); ninguna variable con nombre del TRD §6.3 falta.

#### 5.2 · E1 — Pila arriba con healthchecks green

- **Acción**: `docker compose up -d` y vigilar hasta healthy.
- **Cubre**: E1 (R10, DD-5, L3).
- **Done**: los 3 servicios llegan a healthy (con el orden forzado
  postgres → backend → frontend por el encadenado `service_healthy`);
  `docker compose ps` los muestra healthy de forma estable (sin bucle de
  reinicio — salvo el caso deliberado de 5.5).

#### 5.3 · E2 — Único 8080 con placeholder

- **Acción**: `curl http://localhost:8080/`.
- **Cubre**: E2 (R7, R8, R9, R10).
- **Done**: 200 con el `index.html` del placeholder "Project_Log — pila OK"
  (salida del build Vite); backend y postgres no publican puertos
  (nada responde en otras puertas del host; `docker compose config` muestra
  solo un `ports:`).

#### 5.4 · E3 — Flyway V1 en frío

- **Acción** (procedimiento DD-6, SOLO en local): `docker compose down`;
  `docker volume rm project_log_pgdata`; `docker compose up -d`;
  inspección `docker compose logs backend` (buscar
  `Successfully applied 1 migration`); verificación con psql efímero:
  `docker run --rm --network project_log_net postgres:17-alpine psql -h
  postgres -U <user> -d <db> -c '\dt'` y la fila de
  `flyway_schema_history` (`success` = true) y la lista de índices.
- **Cubre**: E3 (R6, DD-6, L1).
- **Done**: la línea de Flyway aparece; `\dt` lista `users`, `projects`,
  `logs`; `flyway_schema_history` tiene V1 en `success`; existen LOS 4
  índices incluido `projects_api_key_hash_idx` (único parcial) y NO existe
  `logs_message_trgm_idx` (trigram no ejecutada).

#### 5.5 · E4 — Fail-fast de secretos

- **Acción** (DD-3/RG-6): con la pila arriba, quitar un secreto exigido del
  `.env` (p. ej. `JWT_SECRET`), recrear el contenedor del backend y leer
  `docker compose logs backend`; después RESTAURAR el `.env` original y
  verificar que la pila vuelve a green.
- **Cubre**: E4 (R5, DD-3).
- **Done**: el arranque aborta con UN único error que lista TODOS los
  secretos ausentes (de golpe, no uno por uno); NUNCA hubo verde fugaz del
  healthcheck del backend durante el fallo; con el `.env` restaurado el
  backend vuelve a healthy (el bucle de reinicio `unless-stopped` durante
  el fallo es comportamiento esperado, no un bug — RG-6).

#### 5.6 · E5, E6 y E7 — WebSocket echo tras nginx

- **Acción**:
  - (`E5`) `curl -i http://localhost:8080/ws/logs` con cabeceras de upgrade
    (`Connection: Upgrade`, `Upgrade: websocket`, `Sec-WebSocket-Key`,
    `Sec-WebSocket-Version: 13`) → verificar 101 + `Sec-WebSocket-Accept`
    correcto.
  - (`E6+7`) `python3 scripts/ws_echo_test.py` (modo completo, pila
    levantada, nunca en dev); con `--sin-inactividad` para iteraciones y el
    modo completo para la pasada canónica.
- **Cubre**: E5 (R4, R9, L2), E6 (R4, DD-2), E7 (R9, DD-7, RG-5).
- **Done**: 101 con Accept correcto y negociación por el bloque `/ws/logs`
  (no por desvío del proxy); eco idéntico byte a byte de cada frame y cierre
  limpio (0x8); la conexión sobrevive > 75 s de inactividad; revisión
  estática complementaria del `nginx.conf` (3600s, `proxy_http_version 1.1`,
  Upgrade/Connection) confirmada en el diff.

#### 5.7 · E8, E9 y E10 — proxy API, SPA fallback y actuator

- **Acción**:
  - (`E8`) `curl -H "Accept: application/json"
    http://localhost:8080/api/v1/nonexistent` (RG-8: sin el Accept podría
    salir HTML y el escenario perdería valor).
  - (`E9`) `curl http://localhost:8080/ruta/de/panel/inexistente`.
  - (`E10`) desde dentro de la red: `docker compose exec backend curl -fsS
    http://127.0.0.1:8080/actuator/health` (200) y
    `.../actuator/info` (404 o restricción según expose — comprobar que
    cualquier ENDPOINT de actuator distinto de health/info responde 404);
    desde el host, `curl -i http://localhost:8080/actuator/health` NO es
    alcanzable (el nginx no enruta `/actuator/**`, L6).
- **Cubre**: E8 (R9, R15, RG-8), E9 (R9, L9 punto 2), E10 (R12, DD-8, L7).
- **Done**: E8 = 404 con cuerpo JSON de Spring (no HTML de nginx) — prueba
  de que el proxy alcanza al backend; E9 = 200 devolviendo el
  `index.html` del placeholder (sin 404 de nginx); E10 = health 200 dentro
  de la red, resto de actuator 404 y `/actuator/**` invisible desde el host.

#### 5.8 · E11, E12 y cierre del corte

- **Acción**:
  - (`E11`) re-verificación con `.env` ya creado en 5.1: `git status` e
    inspección de `.gitignore`; además `grep` en `.env.example` (sin
    secretos reales).
  - (`E12`) `mvn -q -DskipTests -f backend/pom.xml package` y
    `cd frontend && npm ci && npm run build` limpios; el árbol de paquetes
    = TRD §2.1 (8 paquetes, L5); `docker compose build` regenera las 2
    imágenes con tags exactos.
  - Cierre: `git status` limpio (árbol = los 31 ficheros del design §1.1
    incluyendo `package-lock.json`); el árbol coincide 1:1 con design §1.1.
- **Cubre**: E11 (R1, R13), E12 (R2, R3, E12).
- **Done**: E11 y E12 verdes; número de ficheros versionados = 31 (o 30 si
  el lock se genera en apply y se compromete aquí — nota [23] del design);
  ningún fichero del árbol del design falta.
- **Sin commit**: F4 no genera commit; si un escenario obliga a arreglar
  algo, el fix entra como un commit de fix mínimo (mensaje
  `fix(<área>): <fallo de E-n>` + trailer §0.3) y se re-ejecuta la
  verificación desde el escenario afectado.

---

## 3. Commits sugeridos (atómicos, por fase)

| # | Tras task | Mensaje + trailer |
|---|---|---|
| 1 | 1.3 | `chore: init repo — .gitignore, .env.example y README operativo` |
| 2 | 2.7 | `feat(backend): esqueleto Maven — echo WS /ws/logs, fail-fast de secretos y V1 de Flyway` |
| 3 | 3.2 | `feat(frontend): scaffold Vite/React/TS con placeholder de pila OK` |
| 4 | 4.6 | `feat(docker): pila compose — Dockerfiles multi-stage, nginx y echo test WS` |
| — | 5.x | F4 no genera commit; fixes → `fix(<área>): <fallo de E-n>` |

Trailer en TODOS: `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

## 4. Advertencias para apply (del design §5, memorizar antes de escribir)

1. **RG-3 (crítico en F1)**: Flyway 11.x (con SB 3.5) requiere
   `org.flywaydb:flyway-database-postgresql` explícito. Sin él el build es
   verde pero el arranque aborta con "No database found to handle
   jdbc:postgresql://…".
2. **RG-4 (docker)**: `curl` en la imagen RUNTIME del backend (la Temurin
   21-jre no lo trae; el healthcheck del TRD §6.5 lo necesita — el backend
   no publica puertos, no hay alternativa del lado host). `wget` busybox en
   `nginx:stable-alpine` — verificar con
   `docker run --rm nginx:stable-alpine which wget` ANTES de commit #4; si
   faltara, el fallback documentado es curl instalado en el stage nginx
   (decisión única y documentada, no a mitad del corte).
3. **RG-5 (smoke idle)**: el paso 3 del echo test
   (inactividad > 75 s, default `--idle-segundos 75`) es el observador
   dinámico del timeout del nginx: con el default 60s de
   `proxy_read_timeout` la conexión MURIRÍA a los 60 s; solo con los
   literales 3600s + `proxy_http_version 1.1` + Upgrade/Connection pasa.
   Complementar con la revisión estática del diff del `nginx.conf`. El
   heartbeat real (30 s, `WS_HEARTBEAT_SECONDS`) es C7 y NO se anticipa.
4. **RG-8 (E8)**: el curl canónico del proxy usa
   `-H "Accept: application/json"` — sin él, Spring puede responder HTML y
   el escenario no distingue proxy de SPA. Documentado en README (1.3).
5. **RG-6 (E4)**: durante la prueba de fail-fast, con
   `restart: unless-stopped` el backend entra en bucle de reinicio —
   comportamiento esperado; diagnóstico = `docker compose logs backend`
   (único error agregado). NO "arreglar" quitando el restart.
6. **`proxy_pass` sin URI final** (`http://backend:8080;`) en los dos
   proxys: el prefijo (`/api/v1/`, `/ws/logs`) llega INTACTO al handler; con
   URI final nginx trocearía y rompería el emparejamiento con el
   `addHandler` (DD-2/DD-4).
7. **Healthchecks en compose.yaml, nunca `HEALTHCHECK` en los Dockerfiles**
   (DD-5): el encadenado y las cadencias son topología (§6.5), no imagen; el
   `curl` del backend es lo único que vive en el Dockerfile (es un binario
   de imagen).
8. **Fail-fast = `EnvironmentPostProcessor`** (DD-3): registrado en
   `META-INF/spring.factories` con la clave exacta; sin spring.factories el
   validador no corre y E4 no puede pasar. Las alternativas
   (@PostConstruct, ApplicationRunner, @Validated, shell) están descartadas
   por verde fugaz o errores parciales.
9. **No anticipar C2–C7 (§6 del design)**: sin SecurityFilterChain, sin JWT,
   sin seed del admin, sin `FRONTEND_ORIGIN` en `application.yml`, sin
   endpoints de negocio, sin heartbeat, sin `spring-boot-starter-test`, sin
   trigram (V2), sin TLS, sin rate limit, sin backups, sin `mem_limit`,
   sin tocar `V1__init.sql` tras aplicarlo, sin versionar `.env` (R15, bloqueo
   completo del corte).
10. **Verificación SIEMPRE contra la pila completa** con
    `docker compose up` (R4) — nunca contra dev ni con atajos de proxy.

## 5. Criterios de salida de fase

- La fase de verificación (F4) SOLO puede declararse "Done" si los 12
  escenarios E1–E12 del spec (`spec.md` §2) están todos verdes, cada uno
  verificado con la evidencia concreta que su sección lista (línea de
  log de Flyway, HTTP status, salida del smoke, resultado de `git status`).
- Cualquier escenario rojo = volver a apply con el fallo concreto (qué task
  lo produce, qué literal se desvió), corregir y re-ejecutar desde el
  escenario afectado hasta el final de F4.
- Estado final esperado al cerrar: repo git con 4 commits de fase (+ fixes
  si los hubo), los 31 ficheros del árbol del design §1.1 versionados,
  pila green en local, y NINGÚN fichero fuera del árbol del design añadido
  sin motivo documentado.