---
change: "C1"
artifact: explore
last_updated: 2026-10-20
status: done
---

# Explore — C1 · Fase 0: Setup de pila y repo

> Fase sdd-explore del flujo SDD para el corte C1 (07_SLICING_PLAN.md).
> Solo investigación de los docs de definición validados: decisiones ya
> cerradas con su ancla exacta, puntos de diseño abiertos para la propuesta,
> riesgos del 06 aplicables, consecuencias downstream y preguntas de gate.
> No se propone implementación.

## 1. Alcance del corte (trazado del 06 y del 07)

Fase 0 del Implementation Plan (06, §Fase 0) sin valor de usuario (07, tabla C1,
`estado: pendiente`, sin stories PRD). Entregables enumerados en el 06:

1. Repo monomódulo Spring Boot package-by-feature (TRD §2.1) + frontend Vite/React/TS.
2. `.env.example` versionado sin secretos + `.env` local 600 fuera de git (TRD §6.3).
3. `docker/Dockerfile` backend multi-stage (build Maven → JRE final) — 06, ítem 3.
4. `frontend/Dockerfile` multi-stage (Node build → nginx con estáticos) — 06, ítem 4.
5. `frontend/nginx.conf`: SPA fallback + proxy `/api/v1` → backend:8080 + bloque
   `/ws/logs` con Upgrade y timeouts 3600 s (TRD §6.2).
6. `compose.yaml`: `frontend(8080:80)` / `backend` / `postgres:17-alpine`, volumen
   nombrado `pgdata`, red interna única, healthchecks encadenados, `depends_on:
   service_healthy`, `restart: unless-stopped`, sin `ports:` en backend/BBDD,
   sin `mem_limit` (ADR-27) — TRD §6.1/§6.5.
7. Flyway `V1__init.sql` literal del Schema §6.1 (3 tablas + 4 índices). Nota del
   06: "deshabilitar la trigram (opcional, V2)" — ya está resuelto en el literal
   del Schema §6.1, donde el índice trigram va comentado y la extensión se deja
   para `V2__add_message_trigram.sql`; C1 no la ejecuta.
8. Actuator expuesto solo `health,info` (TRD §6.5).
9. **Extra obligatorio del corte** (07, detalle C1; riesgo #1 del 06): echo test
   del WebSocket tras nginx desde la Fase 0, no al final.

**Done cuando (06):** `docker compose up -d` levanta los 3 servicios con
healthchecks green (postgres → backend → frontend), el único puerto del host
(8080) responde `index.html` de prueba, Flyway crea el esquema V1 en frío, y el
backend rechaza arrancar si falta un secreto del `.env` (fail-fast verificado).

**Fuera de alcance de C1** (queda para otros cortes): toda la lógica (C2–C7), el
panel real (C8), deploy a la VM + firewall (C9 — el C1 se construye pero el 06
solo exige levantar la pila; el deploy a la VM Proxmox y las reglas del firewall
GATE #11 son de C9), borrados/resumen (C10), backups/rate limiting/TLS/retención
(v2 — TRD §6.6, ADR-26, §6.7/§6.8, Schema §7).

## 2. Decisiones ya cerradas que afectan a C1

### 2.1 Stack y arquitectura

| Decisión | Valor cerrado | Fuente |
|---|---|---|
| Backend: monolito Spring Boot único (JVM), package-by-feature, monomódulo | Sí | TRD §1, §2.1, ADR-1 |
| Paquetes: `auth, projects, keys, ingest, query, streaming` + `config` / `common`, con capas `web`/`service`/`repository` | Sí (nombres) | TRD §2.1 |
| Build tool backend: Maven (no Gradle) | Maven (build multi-stage) | 06 Fase 0 ítem 3 |
| Frontend: React + TypeScript con Vite; build estático servido por nginx | Sí | TRD §1, §6.2; 06 ítem 4 |
| DB: PostgreSQL 17, imagen **tag menor fijo `postgres:17-alpine`** | Sí | TRD §6.4, GATE §8 #8; Schema nota inicial |
| Streaming futuro: WebSocket nativo (sin STOMP/SockJS), ruta única `/ws/logs` | Sí | TRD §2.8, ADR-5/§9 |
| Sin integraciones externas, sin colas, sin Redis, sin multi-instancia | Sí | TRD §4, §7 |
| Sin rate limiting (ADR-26) y sin límites de memoria de contenedor ni JVM (ADR-27) en la compose | Sí | TRD GATE §8 #1/#10, §7 |
| Backups: sin automatismo en v1 (pg_dump+cron movido a v2) | Sí | TRD §6.4, §6.6, GATE §8 #9; PRD v1.2 |
| Sin TLS en MVP (HTTP plano en LAN); proxy listo para ADR futura | Sí | TRD ADR-23, §6.2.5, §6.8 |
| Caching de imágenes: sin registry, build local | Sí | TRD §6.1 |

### 2.2 Topología, compose y red

| Decisión | Valor cerrado | Fuente |
|---|---|---|
| Único punto de entrada: **solo el frontend publica puerto**; backend y postgres sin `ports:` | Sí | TRD ADR-16, §6.1, §6.7 |
| Puerto del host: **8080** (frontend `8080:80`); backend interno 8080; postgres interno 5432 | Sí (GATE #7) | TRD ADR-24, §6.5, GATE §8 #7 |
| Red interna única `project_log_net` (bridge) | Sí | TRD §6.1 |
| Volumen **nombrado** `pgdata` en `/var/lib/postgresql/data` (no bind mount) | Sí | TRD ADR-19, §6.4 |
| `restart: unless-stopped` en los 3 servicios | Sí | TRD §6.1 |
| Healthcheck postgres: `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB` cada 10 s | Sí | TRD §6.5 |
| Healthcheck backend: `GET /actuator/health` → 200 cada 30 s, actuator solo `health,info` | Sí | TRD §6.5, §9 |
| Healthcheck frontend: wget trivial sobre su propio :80, no bloquea a nadie | Sí | TRD §6.5 |
| `depends_on: service_healthy` encadenado (postgres → backend → frontend) | Sí | TRD ADR-21, §6.5; 06 Fase 0 |
| Flyway aplica migraciones **automáticamente al arrancar el backend**, con postgres healthy antes | Sí | TRD §6.5, §9; Schema §6 |
| `docker compose down` **nunca con `-v`** (salvo desastre deliberado); actualizar con `build --pull` | Sí | TRD §6.4 |
| Sin `mem_limit` en contenedores en v1 | Sí (ADR-27) | TRD GATE §8 #10; 07 C1 |

### 2.3 nginx (frontend/Dockerfile + nginx.conf)

| Decisión | Valor cerrado | Fuente |
|---|---|---|
| Multi-stage: Node compila → imagen final nginx con estáticos | Sí | TRD §6.2; 06 ítem 4 |
| 1. SPA fallback `try_files … /index.html` | Sí | TRD §6.2.1 |
| 2. Proxy `location /api/v1/ { proxy_pass http://backend:8080; }`; el frontend usa solo rutas relativas (cero CORS, cero URL absoluta en el build) | Sí | TRD §6.2.2 |
| 3. Bloque `location /ws/logs` con `proxy_http_version 1.1`, `Upgrade $http_upgrade`, `Connection "upgrade"`, `proxy_read_timeout`/`proxy_send_timeout` **3600 s** | Sí | TRD §6.2.3, ADR-18; 07 C1 |
| 4. Cabeceras `Host`/`X-Real-IP` en ambos proxys | Sí | TRD §6.2.4 |
| 5. Sin TLS; si llega, se añade en el propio nginx | Sí | TRD §6.2.5, §6.8 |
| Rutas que el proxy enruta: **solo** `/api/v1/**` + `/ws/logs` | Sí | TRD §9 |

### 2.4 Entorno y configuración (`.env` / `.env.example`)

- Filosofía: `.env` real fuera de git desde el primer commit (permisos 600),
  `.env.example` versionado y sin secretos; **faltando un secreto la pila no
  arranca** (fail-fast) — TRD §6.3.
- **El conjunto y los nombres de variables están cerrados** en TRD §6.3, con el
  formato propuesto del `.env.example` ya escrito ahí. Nombres: `POSTGRES_DB`,
  `POSTGRES_USER`, `POSTGRES_PASSWORD`, `SPRING_DATASOURCE_URL`,
  `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`,
  `JWT_TTL_HOURS` (defecto 8), `ADMIN_INITIAL_USERNAME`/`ADMIN_INITIAL_PASSWORD`,
  `FRONTEND_ORIGIN`, `INGEST_MAX_REQUEST_BYTES`, `INGEST_MAX_BATCH_SIZE`,
  `WS_HEARTBEAT_SECONDS`, `LOGGING_LEVEL`, `SPRING_PROFILES_ACTIVE`, `TZ`,
  y la opcional de host `FRONTEND_BIND` (defecto `0.0.0.0`).
- Condiciones cerradas sobre el uso: JWT HS256 con secreto ≥32 bytes aleatorios
  (TRD §5); seed del admin idempotente sin default en producción, si falta el
  password no arranca (TRD §5; Schema §5/§6.3); el frontend no lleva ninguna
  variable en runtime (TRD §6.3).
- Los valores literales del `.env.example` (incluidos `INGEST_MAX_REQUEST_BYTES=1MiB`,
  `INGEST_MAX_BATCH_SIZE=200`, `WS_HEARTBEAT_SECONDS=30`, `TZ=UTC` — TRD §6.3,
  §2.7) son también referencia cerrada.

### 2.5 Esquema SQL (migración V1)

- `V1__init.sql` existe **literal, completo y ejecutable** en Schema §6.1: las
  3 tablas (`users`, `projects`, `logs`), sus constraints (`users_username_unique`,
  `projects_name_unique`, `projects_status_check`, `logs_level_check`,
  `logs_environment_check`, FK `logs_project_fk … ON DELETE CASCADE`) y los 4
  índices (`logs_dashboard_idx`, `logs_received_idx`, `logs_correlation_idx`
  parcial, `projects_api_key_hash_idx` único parcial). La trigram va comentada y
  queda reservada a `V2__add_message_trigram.sql` (Schema §6.2).
- Ubicación de los scripts cerrada: `src/main/resources/db/migration` (Schema §6).
- C1 **no** semilla el admin por SQL: la semilla es por código (Argon2id depende
  de configuración) y es trabajo de C2 (Schema §6.3). C1 solo deja el esquema V1
  aplicado y el fail-fast de secretos verificado.
- `TIMESTAMPTZ` en UTC en todas las columnas temporales; `BIGINT GENERATED
  ALWAYS AS IDENTITY`; `log` inmutable; `metadata` JSONB solo objeto — Schema §1.
- El backend no necesita acceso a Internet; reloj del host NTP-sync (TRD §9) —
  requisito del entorno VM, no del corte.

## 3. Puntos de diseño abiertos que la propuesta de C1 debe resolver

Estos no están decididos en ningún doc; la propuesta (fase siguiente) los
concreta y el usuario los aprueba en el gate fino de alcance (07, reglas).

- **D1 - Versiones de stack (la única familia sin cerrar).** Los docs fijan
  tecnologías pero ninguna versión concreta: de Java (¿17? ¿21 LTS?),
  Spring Boot (¿3.3.x/3.4.x/3.5.x?), Vite, React, TypeScript y Node (¿20/22 LTS?)
  para el builder del Dockerfile. Solo Maven está nombrado como build tool (06).
  Nota: la imagen de la base es el caso contrario ya cerrado (`postgres:17-alpine`,
  tag menor fijo, GATE #8). La propuesta debe elegir y justificar versiones LTS
  actuales coherentes entre sí (y qué política de versión usa el resto de Dockerfiles).
- **D2 - Echo test del WebSocket tras nginx (riesgo #1 del 06; extra obligatorio
  del corte).** Preguntas que la propuesta debe responder:
  - ¿Endpoint temporal (p. ej. handler mínimo `/ws/echo`) o se replica la futura
    ruta `/ws/logs` vacía? Ojo: `/ws/logs` sin HandshakeInterceptor (que llega en
    C2/C7) permite probar el proxy en limpio; pero crear ya la ruta definitiva
    podría interferir con el interceptor real de C7. Alternativa: nombre de ruta
    temporal distinta (`/ws/echo`) que se elimina al entrar C7 (o se mantiene
    como smoke de infraestructura).
  - ¿Cliente del test? Puedes probarlo sin frontend real: `curl` con cabeceras de
    upgrade y `--include` (comprueba el 101 + upgrade), o `websocat`, o un script
    Node mínimo con `ws`. Debe poder automatizarse como smoke repetible (C7
    reutiliza la misma comprobación: el 07 exige en C7 que "el echo test del
    proxy WS de C1 siga verde con el handler real").
  - ¿Qué verifica exactamente? Como mínimo: upgrade negociado a través de nginx,
    ping/echo de frame, y que la conexión sobrevive más allá del default de 60 s
    del proxy (valida el timeout 3600 s al menos con observación parcial: la
    configuración del bloque + una conexión sostenida breve).
- **D3 - Estructura exacta del repo.** Los datos cerrados: monorepo con backend
  Spring Boot monomódulo package-by-feature y frontend Vite (07; nombres de
  paquetes TRD §2.1). Abiertos para la propuesta: layout de carpetas raíz
  (`backend/` + `frontend/` + `docker/` — el 06 ya sugiere `docker/Dockerfile`
  y `frontend/Dockerfile`; ¿la compose en la raíz?), nombre del artefacto/proyecto
  Maven, `groupId`/`artifactId`, ubicación de `nginx.conf` (el 06 fija
  `frontend/nginx.conf`), y si `docker/Dockerfile` del backend vive en raíz o dentro de `backend/` (el 06 lo llama `docker/Dockerfile`: seguirlo).
- **D4 - Fail-fast sin secretos: cómo se implementa.** El TRD exige que sin
  un secreto del `.env` la pila no arranque (§6.3) y el 06 lo pide verificado.
  Abierto: ¿validación en el arranque de Spring (p. ej. un `ConfigChecker`/
  `@PostConstruct` o `EnvironmentPostProcessor` que abra el contexto y liste los
  secretos ausentes)? ¿A qué variables aplica exactamente el fail-fast (los
  secretos: `POSTGRES_PASSWORD`, `JWT_SECRET`, `ADMIN_INITIAL_PASSWORD`…) frente
  a las que tienen default (`JWT_TTL_HOURS=8`, límites de ingesta, heartbeat)?
  Nota: el seed del admin ya fallará al no existir password en C2, pero en C1
  aún no existe ese código — la comprobación de C1 debe cubrir la pila vacía.
  En C1 no existe lógica de Spring: si acaso, el arranque del backend vacío
  igualmente necesita `JWT_SECRET` per TRD; la propuesta debería hacer explícito
  qué chequeo corresponde a C1 sin adelantar código de C2.
- **D5 - `index.html` de prueba: qué es y qué contiene.** "responde index.html
  de prueba" (06). C1 no construye el panel real (C8). Debe decidirse: ¿un
  `index.html` mínimo temporal (p. ej. "Project_Log — pila OK" con enlaces a
  healthchecks) generado por el build Vite vacío, o página placeholder estática
  que el build real sustituirá en C8? Debe ser lo bastante real para validar el
  pipeline Vite→nginx→puerto 8080 completo, y no confundirse con la UI final.
- **D6 - Qué contiene el backend de C1.** Un Spring Boot mínimo: sin endpoints
  de negocio (llegan en C2+), pero con: actuator solo `health,info` (cerrado),
  Flyway con `V1__init.sql` (cerrado) y la ruta WS del echo test (D2) necesaria
  para el extra obligatorio. Abierto: ¿se crean ya los paquetes vacíos
  (`auth`, `projects`… los 6 de TRD §2.1 + `config`/`common`) o solo `config`?
  Crear el esqueleto de paquetes ahora obliga a poco y fija la convención para
  C2–C7; no hacerlo deja la comprobación del package-by-feature a C2.
- **D7 - Política de gestión del `.env` fuera de git.** Debe concretarse en la
  propuesta: qué entra exactamente al primer `.gitignore` (`.env`, `target/`,
  `node_modules/`, `dist/`…) y si el `.env.example` se copia o se documenta cómo
  generarlo (incluido cómo generar un secreto fuerte para `JWT_SECRET`, p. ej.
  `openssl rand -base64 48`). El requisito "fuera de git desde el primer commit"
  (TRD §6.3) convierte esto en la primera tarea de repo.
- **D8 - Healthcheck del frontend con wget.** Cerrado que es un check trivial
  wget contra :80 (TRD §6.5); abierto solo el detalle de la imagen nginx final
  (¿alpine con busybox wget? — imagen elegida según la decisión D1/D3 de
  Dockerfile).
- **D9 - Cómo se "prueba" Flyway en frío.** El Done dice "Flyway aplica el
  esquema V1 en frío": la propuesta debe definir la comprobación (p. ej. desde
  un contenedor efímero `psql` con `flyway_schema_history` presente y `\d logs`
  mostrando la tabla, tras un `down` + `up` limpio en la VM o en local antes de
  subir). Es comprobación, no diseño.
- **D10 - Docker Compose versión/esquema.** Sin versiones de stack fijadas
  también en la compose (`compose.yaml` moderno sin `version:` es lo habitual);
  menor, pero conviene fijarlo en la propuesta para no sorprender con el Docker
  Engine de la VM.

## 4. Riesgos del 06 aplicables a C1 y cómo se comprueban aquí

| Riesgo (06, "Riesgos de ejecución") | Aplica en C1 | Cómo se comprueba en C1 |
|---|---|---|
| **Proxy WS mal configurado (upgrade/timeout) — el más alto** ("conectado en dev, muerto en prod") | Sí — riesgo #1, plan B dice "Probar WS tras nginx desde Fase 0 (echo test), no al final; timeouts 3600 s ya en el .conf" | Echo test del D2: upgrade 101 negociado a través del nginx, frame echo, y bloque `/ws/logs` del `nginx.conf` con las 4 cabeceras/timeouts del TRD §6.2.3, verificado en la pila completa (`docker compose up`), no en dev. En C7 se repite con el handler real |
| **Flyway + JSONB + TIMESTAMPTZ: sorpresas de driver** | Sí — plan B: "V1__init.sql probado antes del panel (Fase 0 arranca con migraciones)" | Migración V1 literal aplicada en frío (D9): postgres healthy → backend arranca → Flyway ejecuta V1 → tablas/índices visibles y `flyway_schema_history` con V1 success |
| JWT en query param → logs de nginx | No en C1 (el handshake JWT llega en C7; 07 lo asigna a C7) | Fuera de C1; pero el bloque WS del nginx debe quedar ya como en TRD §6.2.3 para no tocarlo después |
| Argon2id no nativo | No en C1 (C2) | Fuera de C1 |
| Ingesta bajo presión sin rate limit | No en C1 (C5) | Fuera de C1; los límites solo como variables del `.env.example` (cerrado §2.4) |
| Scope creep post-1-usuario | Sí (transversal) | C1 no añade feature alguno: pila vacía + prueba. Toda necesidad nueva = nueva versión del PRD |

Riesgo adicional propio de C1 (no en la tabla del 06, surge de su Done): **el
fail-fast de secretos** debe comprobarse (levantar sin secreto → pila no arranca
o el backend aborta con error claro; levantar con todos → green). Es "Done
cuando", no riesgo de diseño, pero la propuesta debe definir cómo se demuestra.

## 5. Consecuencias downstream: qué decisiones de C1 ligan a C2–C8

- **Nombres de paquetes** (`auth, projects, keys, ingest, query, streaming`,
  `config`/`common`, capas web/service/repository): TRD §2.1 — se fijan en C1
  y C2–C7 los rellenan; renombrar después cuesta. (Si D6 decide crear esqueleto,
  esta es la ligadura principal.)
- **`V1__init.sql` no se toca después**: es el esquema canónico del Schema §6.1;
  cambios futuros = migraciones nuevas (V2+), nunca editar V1 (Flyway checksum;
  y el literal es "contrato") — Schema §6, 07 reglas de slicing ("literales son
  contrato"). La trigram queda reservada a `V2__add_message_trigram.sql`
  (Schema §6.2; también Fase 3 del 06).
- **`nginx.conf` y su bloque `/ws/logs`**: C7 exige "el echo test del proxy WS
  de C1 siga verde con el handler real" — el handler de C7 debe casar con la
  ruta (`/ws/logs`) y el upgrade que este nginx ya proxya; la ruta del echo test
  (D2) se elige para no condicionar la definitiva.
- **`compose.yaml`, nombres de servicio y red**: los nombres `frontend`,
  `backend`, `postgres` (y la red/volumen `pgdata`) son los que usan
  `SPRING_DATASOURCE_URL` (`jdbc:postgresql://postgres:5432/…`, TRD §6.3) y el
  `proxy_pass http://backend:8080` (TRD §6.2.2), y los que C9 usará para subir
  la pila a la VM; cambiarlos después rompe env/nginx.
- **`.env.example` y sus nombres**: contrato de configuración para todo el
  sistema (C2 los consume: `JWT_SECRET`, `JWT_TTL_HOURS`,
  `ADMIN_INITIAL_*`; C5: `INGEST_MAX_*`; C7: `WS_HEARTBEAT_SECONDS`). Cambiar
  un nombre tras C2 implica tocar cada corte afectado + TRD §6.3.
- **Puerto 8080 del host** (`FRONTEND_BIND` opcional): fija cómo el admin y las
  apps de ingesta alcanzarán la pila y la config CORS (C2) y la doc de Tailscale
  (C10).
- **`FRONTEND_ORIGIN`** (TRD §6.3): en C1 solo existe como variable; su uso real
  (CORS del dev server) llega en C2/C8, así que C1 debe dejarla con el valor de
  ejemplo del TRD (comment "solo para desarrollo/CORS") sin consumirla.
- **Estructura de Dockerfiles multi-stage**: el patrón (build en builder →
  runtime mínimo) es el que C8/C9 reutilizarán al añadir el panel real; un
  Dockerfile mal separado arrastraría coste de build a cada corte.
- **`.gitignore` y política de secretos**: C2 añade código que usa secretos; el
  hábito nacido en C1 (nada secreto en git) es la garantía continua (TRD §6.3,
  ADR-22).

## 6. Preguntas para el gate de propuesta (si solo el usuario puede decidir)

Máx. 5, ordenadas por orden probable de bloqueo:

1. **Versiones de stack (D1)**: ¿Java 21 LTS + Spring Boot 3.5.x actual, y Node
   22 LTS + React/Vite/TS más recientes estables como base de los Dockerfiles?
   (Es la única familia de decisiones sin cerrar; si el usuario prefiere otra,
   afecta a todos los Dockerfiles y al POM desde la primera tarea.)
2. **Echo test (D2)**: ¿ruta temporal `/ws/echo` que se elimina al llegar C7, o
   mismo handler de prueba en `/ws/logs` sin auth que se sustituye? Y ¿aceptas
   que el test se ejecute con un cliente CLI (`curl`/`websocat`/script Node)
   automatizable como smoke?
3. **Esqueleto de paquetes (D6)**: ¿creamos ya los 6 paquetes de feature +
   `config`/`common` vacíos en C1 (fija convención antes de C2), o C1 deja solo
   `config` con lo mínimo y los paquetes nacen en su corte?
4. **`index.html` de prueba (D5)**: ¿placeholder mínimo temporal (sustituido por
   el build real de C8) o página "status" sencilla que quede como página raíz de
   cortes intermedios?
5. **Donde se valida primero la pila (D9/deploy)**: ¿se levanta y prueba
   localmente en el host de desarrollo y luego se sube a la VM en C9, o
   `docker compose up -d` en la VM (Done literal del 06) ya forma parte del
   cierre de C1? (El 06 pone el deploy final en C9 pero el Done de Fase 0 dice
   "en la VM"; conviene fijar el criterio de aceptación.)

## 7. Resumen de decisiones cerradas vs. abiertas

- **Cerradas** (con ancla): stack tecnológico y patrón arquitectónico (TRD §1/§2.1,
  ADR-1), Maven (06), paquetes (TRD §2.1), postgres 17 + tag fijo (GATE #8),
  topología/puertos/red/volumen (ADR-16/19/21/24, §6.1/§6.4/§6.5), healthchecks
  exactos (§6.5), nginx con los 5 puntos (§6.2, ADR-18), conjunto y nombres de
  variables (§6.3, formato ya escrito), fail-fast de secretos como requisito
  (§6.3, §5), `V1__init.sql` literal y ubicación (Schema §6.1, §6), actuator
  `health,info` (§6.5/§9), sin mem_limit/backups/rate limit/TLS (ADR-26/27/23,
  §6.6), rutas del proxy (§9), semilla del admin NO en C1 (Schema §6.3 → C2).
- **Abiertas para la propuesta** (D1–D10, §3): versiones de Java/SB/Vite/
  React/TS/Node; mecánica y alcance del echo test WS; layout raíz del repo y
  detalle de Dockerfiles; implementación del fail-fast de secretos en C1;
  contenido del `index.html` de prueba; esqueleto de paquetes; `.gitignore`
  base y generación de secretos; detalle del healthcheck wget; procedimiento de
  verificación de V1 en frío; dónde se valida primero (host vs VM).

Listo para pasar a `sdd-propose` con las preguntas de §6 si el gate las requiere.