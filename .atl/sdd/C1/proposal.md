---
change: "C1"
artifact: proposal
last_updated: 2026-10-20
status: approved
gate: >
  Aprobado por el usuario 2026-10-20 en los 5 puntos del GATE:
  (1) versiones Java 21 LTS Temurin + SB 3.5.x + Maven 3.9 + Node 22 LTS +
  Vite 7 + React 19 + TS 5.x, tags exactos, actualización manual
  (rechazada Java 25/Node 24); (2) echo test en /ws/logs tal cual;
  (3) esqueleto 6 paquetes + config/common como package-info.java tal cual;
  (4) index.html placeholder del build Vite tal cual; (5) validación local
  en C1, VM + firewall en C9.
---

# Proposal — C1 · Fase 0: Setup de pila y repo

> Fase sdd-propose del flujo SDD para el corte C1 (07, tabla y detalle C1).
> Resuelve las decisiones abiertas D1–D10 de `explore.md` §3, propone el plan
> de trabajo y deja listas las preguntas del gate. Los literales de los docs
> se **citan, nunca se redefinen** (regla del 07). Nada de esto se ejecuta
> hasta que el usuario apruebe en gate: no se toca código, no se hace
> `git init`, no se crean otros ficheros.

## 1. Objetivo y alcance

**C1**: crear el repo git y levantar la pila vacía de Project_Log de punta a
punta, sin valor de usuario (06 Fase 0; 07 C1 `estado: pendiente`), cumpliendo
el Done del 06: `docker compose up -d` levanta los 3 servicios con healthchecks
green (postgres → backend → frontend), el único puerto del host (8080)
responde `index.html` de prueba, Flyway aplica el esquema `V1` en frío y el
backend hace fail-fast sin secretos del `.env`.

Entregables concretos:

1. **Repo git inicial** con monorepo: backend Spring Boot monomódulo
   package-by-feature (TRD §1, §2.1, ADR-1) + frontend Vite/React/TS (TRD §1);
   `.gitignore` y política de `.env` desde el primer commit (TRD §6.3, D7).
2. **Backend mínimo**: Maven web+actuator+websocket+flyway; actuator expuesto
   solo `health,info` (TRD §6.5/§9); fail-fast de secretos (D4); handler
   temporal del echo test WS (D2).
3. **`V1__init.sql`**: copia literal del Schema §6.1 en
   `src/main/resources/db/migration` (Schema §6), con la trigram comentada y
   reservada a `V2__add_message_trigram.sql` (Schema §6.2 — C1 no la ejecuta).
4. **`.env.example`** en el formato literal del TRD §6.3 (nombres y valores de
   ejemplo cerrados), documentando cómo generar los secretos.
5. **`frontend/Dockerfile`** multi-stage (Node build → nginx estáticos) y
   **`frontend/nginx.conf`** con los 5 puntos del TRD §6.2 (SPA fallback,
   proxy `/api/v1/` → `http://backend:8080`, bloque `/ws/logs` con Upgrade y
   timeouts 3600 s, cabeceras `Host`/`X-Real-IP`, sin TLS).
6. **`docker/Dockerfile`** backend multi-stage (build Maven → JRE final) —
   nombre fijado por el 06, ítem 3.
7. **`compose.yaml`** en la raíz: `frontend(8080:80)` / `backend` /
   `postgres:17-alpine` (tag menor fijo, GATE #8), red interna única
   `project_log_net`, volumen nombrado `pgdata`, healthchecks exactos del
   TRD §6.5 encadenados con `depends_on: service_healthy`,
   `restart: unless-stopped`, sin `ports:` en backend/BBDD, sin `mem_limit`
   (ADR-27), sin `version:` (D10).
8. **`index.html` de prueba** servido por el pipeline Vite→nginx (D5).
9. **Extra obligatorio (07 C1; riesgo #1 del 06): echo test del WebSocket
   tras nginx** (D2), automatizado como smoke repetible.
10. **Flyway en frío verificado** (D9) y **fail-fast verificado** (Done).

## 2. Resolución propuesta de D1–D10

### D1 — Versiones de stack (**decisión que el usuario puede cambiar en el gate**)

Combinación propuesta a 2026-10-20:

| Componente | Propuesta | Rol |
|---|---|---|
| Java | **21 LTS** (Eclipse Temurin) | Runtime y compilación del backend |
| Spring Boot | **3.5.x** (último patch estable, fijado en el POM) | Framework único de backend |
| Maven | **3.9.x** (imágenes oficiales `maven:3.9-eclipse-temurin-21`) | Build tool (cerrado en 06) |
| Node | **22 LTS** (`node:22-alpine`) | Solo builder del frontend |
| Vite | **7.x** estable | Bundler/dev server |
| React | **19.x** estable | (su consumo real llega en C8) |
| TypeScript | **5.x** (menor estable del momento) | — |

Razonamiento de estabilidad:
- **Java 21 LTS** frente a 17: LTS con soporte amplio, base recomendada de la
  línea Spring Boot 3.5.x, virtual threads disponibles y máximo soporte de
  librerías de terceros (relevante para C2: Argon2id en Spring Security y
  librerías PHC del plan B del 06). Java 25 (LTS más reciente) se descarta por
  ecosistema más joven sin beneficio en un monolito de homelab; Java 17, por
  ventana de soporte más corta que 21.
- **Spring Boot 3.5.x**: línea estable de referencia con la ventana de soporte
  más larga de las 3.x activas; las previas (3.3/3.4) agotaron soporte OSS.
  La mayor siguiente se descarta por excesiva novedad para infra crítica. La
  versión nativa de WebSocket (`WebSocketConfigurer` + handler) y todos los
  mecanismos que C2–C7 necesitan existen intactos en 3.5.
- **Node 22 LTS**: LTS consolidado; el builder solo compila estáticos, así que
  la carga de versiones recae en Vite, no en Node. Alternativa razonable: la
  LTS activa siguiente (24) si el usuario prefiere el borde; descartada por
  ser más joven sin ganancia para compilación de Vite.
- Vite 7 / React 19 / TS 5.x: versiones estables maduras entre sí; el panel
  real no se consume hasta C8, así que aquí solo deben producir un build
  limpio determinista.
- Política de versiones en imágenes Docker (cierra D10 parcialmente): **tags
  exactos, no `latest`**, en todos los Dockerfiles y en el POM; la
  actualización es manual con `docker compose build --pull` (TRD §6.4), como
  ya hace `postgres:17-alpine` (GATE #8). nginx final: `nginx:stable-alpine`.

Alternativa descartada: versión "latest" o flotantes en imágenes/POM — rompe
reproducibilidad del build y contradice la política ya cerrada para postgres.

### D2 — Echo test del WebSocket tras nginx (riesgo #1 del 06)

**Opción recomendada: handler temporal de echo montado en la ruta definitiva
`/ws/logs`, con la misma API de registro que usará C7** (WebSocket nativo:
`WebSocketConfigurer` + `TextWebSocketHandler` que devuelve cada frame
recibido, sin interceptor ni auth — no existen todavía). Verificación con
cliente CLI automatizable (detalle en §3).

Justificación: el proxy enruta **solo** `/api/v1/**` + `/ws/logs` (TRD §9,
cerrado). Una ruta temporal `/ws/echo` obligaría a añadir un `location` extra
al nginx que viola ese contrato (o a probar sin pasar por nginx: inútil). Con
el echo en `/ws/logs`, la prueba ejercita el bloque literal de producción
(cabeceras de upgrade, timeouts 3600 s, cabeceras Host/X-Real-IP), y el
criterio de C7 ("el echo test del proxy WS de C1 siga verde con el handler
real", 07 C7) queda literal: en C7 solo cambia el registro del handler
(EchoSocketHandler → LogSocketHandler + HandshakeInterceptor), el nginx no se
toca y el mismo smoke se re-ejecuta (en su modo handshake, ya con token).
Alternativa descartada: ruta `/ws/echo` con `location` propio en nginx
(contravine TRD §9) o handler echo mantenido para siempre (feature fantasma
sin traza → scope creep).

### D3 — Estructura del repo

Layout raíz propuesto (monorepo; la compose en la raíz porque orquesta todo):

```
Project_Log/
├── compose.yaml                  # orquesta los 3 servicios
├── .env.example                  # formato exacto del TRD §6.3
├── .gitignore                    # D7 — primer commit junto a .env.example
├── README.md                     # mínimo operativo: cp .env.example .env, chmod 600, compose up
├── backend/                      # proyecto Maven monomódulo
│   ├── pom.xml                   # groupId com.projectlog, artifactId project-log-backend
│   └── src/main/.../db/migration/V1__init.sql   # copia literal Schema §6.1
├── docker/Dockerfile             # backend multi-stage — nombre del 06, ítem 3
├── frontend/
│   ├── Dockerfile                # multi-stage Node → nginx (06, ítem 4)
│   ├── nginx.conf                # los 5 puntos del TRD §6.2
│   └── (proyecto Vite scaffold)
└── scripts/ws_echo_test.py       # smoke del echo test (D2), stdlib Python
```

Detalles: `groupId` `com.projectlog`, `artifactId` `project-log-backend`,
raíz de paquetes Java `com.projectlog.<feature>`. `docker/Dockerfile` se
construye con `build.context: ./backend` + `dockerfile: ../docker/Dockerfile`
en la compose (respeta el nombre del 06 sin copiar la configuración Maven
fuera de `backend/`). Alternativa descartada: mover el Dockerfile dentro de
`backend/` (rompe el nombre literal del 06 e ítem 3).

### D4 — Fail-fast sin secretos

Implementación: **validador de arranque en `config/`** que se ejecuta lo antes
posible en el arranque del backend (antes de que el puerto reciba tráfico) y
aborta el arranque con **un único error claro que liste todos los secretos
ausentes** de golpe. En C1, sin lógica de negocio, exige ya las variables cuyo
consumo llega en cortes posteriores pero cuyo requisito ya es contrato:

- Exigidas en C1: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
  `SPRING_DATASOURCE_PASSWORD` (Flyway necesita conectar), `JWT_SECRET`
  **con chequeo de ≥32 bytes** (TRD §5), `ADMIN_INITIAL_USERNAME` y
  `ADMIN_INITIAL_PASSWORD` (la semilla de C2 no debe arrancar sin ellos —
  TRD §5, Schema §6.3; el chequeo es config, no adelanta código de C2).
- Con default, no exigidas: `JWT_TTL_HOURS` (8), `INGEST_MAX_*`,
  `WS_HEARTBEAT_SECONDS`, `LOGGING_LEVEL`, `SPRING_PROFILES_ACTIVE`, `TZ`,
  `FRONTEND_ORIGIN` (C1 no la consume — TRD §6.3, explore §5), `FRONTEND_BIND`.
- La propia pila añade una red de seguridad: si falta `POSTGRES_PASSWORD` en
  el `.env`, el contenedor postgres no se levanta healthy y el backend nunca
  arranca (`depends_on: service_healthy`).

Alternativa descartada: `ApplicationRunner` (comprueba demasiado tarde, cuando
el healthcheck puede dar un verde fugaz) y validaciones sueltas por `@Value`
(mensajes de error pobres, uno por variable).

### D5 — `index.html` de prueba

**Placeholder generada por el proyecto Vite real** (React vacío: "Project_Log
— pila OK", versión y enlaces a los checks), servida por el pipeline completo
Vite → Dockerfile multi-stage → nginx → puerto 8080 del host. Se elige la
página del build real (no un HTML estático suelto) porque el Done exige
validar el pipeline completo desde la Fase 0, que es el mismo que servirá el
panel en C8; se marca claramente como temporal en su contenido para no
confundirla con la UI final (que la sustituye en C8). Las rutas relativas del
TRD §6.2.2 ya quedan ejercitadas: la página no carga nada por `/api/v1`
(no hay endpoints aún hasta C2 — ver §4 paso 8 para la comprobación del proxy
API con curl).

Alternativa descartada: `index.html` estático colocado a mano en la imagen
nginx — no valida el build Vite ni el multi-stage (06 ítem 4).

### D6 — Esqueleto de paquetes

**Recomendación: crear ya los 6 paquetes de feature** (`auth`, `projects`,
`keys`, `ingest`, `query`, `streaming`) **+ `config` y `common`**
(nombres cerrados en TRD §2.1), como `package-info.java` documentando cada
feature; `config` y `streaming` ya llevan el contenido real de C1
(`ConfigChecker`/configuración de flyway/actuator y `EchoSocketHandler`
respectivamente; el configurer del registro WS en `config`). Fija la
convención package-by-feature antes de que C2 escriba línea alguna (explore
§5: renombrar después cuesta) y hace verificable la estructura desde C1.
Alternativa descartada: dejar los paquetes para cada corte (la convención se
descubre en C2 y un desvío se contagiaria a C2–C7).

### D7 — `.gitignore` y generación de secretos

`README.md` + `.env.example` entran en el **primer commit** junto al
`.gitignore`; el `.env` nunca está en git (TRD §6.3, P0-10). Propuesta:

- `.gitignore` raíz (monorepo): `.env` y `.env.*` excepto `.env.example`,
  `backend/target/`, `frontend/node_modules/`, `frontend/dist/`,
  `.vite/`, IDE (`*.iml`, `.idea/`, `.vscode/`), OS (`DS_Store`).
- Procedimiento documentado en README y en comentarios del `.env.example`:
  `cp .env.example .env && chmod 600 .env` y generación con
  `openssl rand -base64 48` para `JWT_SECRET` y `openssl rand -base64 24`
  para `POSTGRES_PASSWORD` (ambas sin default: fail-fast D4).
- El `.env.example` es **copia textual del bloque del TRD §6.3** (formato
  cerrado), con comentarios de cómo generar cada secreto.

### D8 — Healthcheck del frontend

Imagen final del frontend: `nginx:stable-alpine` (alpine con busybox wget
incluido). Check trivial sobre su propio :80: `wget --spider -q http://127.0.0.1/ || exit 1`
cada ~10–30 s; observabilidad básica, no bloquea a nadie (TRD §6.5). Si la
imagen elegida careciera de wget (no es el caso en alpine), el fallback sería
una petición con `nginx -q -s reload`-style imposible — se documenta esta
elección y no se cambia en medio del corte.

### D9 — Comprobación de Flyway en frío

Procedimiento (comprobación, no diseño) tras el primer `docker compose up`:

1. Pila en frío real: `docker compose down` (sin `-v`) +
   borrado del volumen local `project_log_pgdata` **solo en local, donde en
   C1 no hay nada que perder** (en la VM nunca: `-v` prohibido salvo desastre
   deliberado, TRD §6.4); `docker compose up -d` de nuevo.
2. Postgres healthy → backend arranca → logs del backend muestran Flyway
   ejecutando `V1` con éxito (`Successfully applied 1 migration`).
3. Verificación desde contenedor efímero de `postgres:17-alpine` sobre la red
   `project_log_net` con `psql`: `\dt` muestra `users`, `projects`, `logs`;
   `select * from flyway_schema_history` muestra V1 en `success` y los
   4 índices de §6.1 existen (incluido `projects_api_key_hash_idx` único
   parcial; la trigram NO existe).

### D10 — Compose sin `version:`

`compose.yaml` moderno del **Compose Spec (v2)**, sin clave `version:`
(obsoleta y solo genera warning). Requisito de entorno documentado en el
README: Docker Engine con Compose v2 (plugin compose). La versión de Docker
Engine de la VM es decisión/detalle de C9 (deploy), pero el README de C1 fija
el mínimo probado en local.

## 3. Echo test del WebSocket tras nginx — diseño (extra obligatorio)

Objetivo ligado a C7: el 07 exige en C7 que **"el echo test del proxy WS de
C1 siga verde con el handler real"** — el test debe ser repetible y su verde
debe depender solo del proxy (nginx + upgrade), no del handler conectado.

### Handler temporal en `/ws/logs`

- `streaming/EchoSocketHandler` (TRD §2.1: el futuro handler también vive en
  `streaming`): `TextWebSocketHandler` que responde cada mensaje recibido tal
  cual (eco, frame a frame), registrado por un `config.WebSocketConfig` con
  la misma mecánica nativa que usará C7 (`registerWebSocketHandlers` /
  `addHandler(handler, "/ws/logs")`) — STOMP y SockJS quedan descartados ya
  por ADR-5.
- Sin `HandshakeInterceptor`: C1 no tiene JWT ni seguridad (C2/C7); así el
  proxy se prueba "en limpio", sin mezclar auth con infraestructura. Ninguna
  ruta nueva, ningún cambio de nginx en C7.

### Comprobaciones del test (3 niveles)

1. **Handshake (101) a través del nginx** — con `curl -i` contra
   `http://<host>:8080/ws/logs` con cabeceras de upgrade
   (`Connection: Upgrade`, `Upgrade: websocket`, `Sec-WebSocket-Key`...):
   se espera `101 Switching Protocols` + `Sec-WebSocket-Accept` correcto.
   Prueba el `location /ws/logs` del nginx (§6.2.3) y la negociación backend.
2. **Frame de eco full-duplex** — script `scripts/ws_echo_test.py` (Python
   stdlib, sin dependencias: handshake + frames RFC 6455 a pelo, ~80 líneas)
   enviado a través del puerto publicado: envía un texto, exige recibir el
   eco idéntico y cierra limpio. Prueba el camino completo
   cliente → nginx → backend → nginx → cliente. Alternativa descartada:
   `websocat` (binario externo a instalar) o script Node con `ws` (necesita
   `npm install` de un módulo solo para el smoke).
3. **Timeouts 3600 s del bloque WS** — dos comprobaciones:
   - estática: el `nginx.conf` lleva `proxy_read_timeout`/`proxy_send_timeout`
     `3600s` + `proxy_http_version 1.1` + cabeceras Upgrade/Connection (revisión
     del diff, literal §6.2.3);
   - dinámica parcial: conexión sostenida **con inactividad > 75 s** exige
     seguir viva — con el default de 60 s del proxy la conexión moriría; así
     se valida que el timeout no es el default ni nada menor (un test de los
     3600 s completos sería impracticable y no aporta).

### Modo de ejecución y reutilización en C7

El smoke vive como comando documentado en el README del corte
(`python3 scripts/ws_echo_test.py [--solo-handshake]`); en C7 se re-ejecuta
**en modo handshake** (con token en el handshake del handler real) y con los
frames se demuestra la cadena completa — por eso el test se diseña desde
hoy con dos modos y no se reescribe en C7. Verificación siempre **contra la
pila completa (`docker compose up` con el nginx delante)**, nunca en dev (el
riesgo #1 del 06 es precisamente el "conectado en dev, muerto en prod").

## 4. Plan de trabajo por pasos (orden de construcción)

Sin entrar al detalle de tasks.md; cada paso termina en una verificación
observable. Los pasos 1–7 construyen, el 8 cierra el Done.

| # | Paso | Qué se verifica |
|---|---|---|
| 1 | `git init` + primer commit: `.gitignore`, `.env.example` (formato TRD §6.3), README mínimo | `.env` (creado localmente 600) no aparece en `git status`; `.env.example` sin secretos; convención del repo fijada |
| 2 | Proyecto Maven (`backend/`): POM (web, actuator, websocket, flyway, driver postgres), paquete raíz `com.projectlog`, esqueleto de los 8 paquetes (D6), `application.yml` (actuator solo `health,info`; flyway on) | `mvn -q -DskipTests package` compila; árbol de paquetes = TRD §2.1; ninguna dependencia sobrante |
| 3 | `streaming/EchoSocketHandler` + `config.WebSocketConfig` (ruta `/ws/logs`) + validador fail-fast (D4) con su lista de secretos | Contexto Spring arranca con todos los secretos y aborta con error único y claro si falta alguno (primera prueba local) |
| 4 | `src/main/resources/db/migration/V1__init.sql` — copia literal del Schema §6.1 (cuidado: copiar tal cual, con el comentario de trigram) | Diff contra el doc = cero caracteres de diferencia; la trigram solo comentada |
| 5 | `frontend/`: scaffold Vite + React + TS, placeholder `index.html` (D5); `frontend/Dockerfile` multi-stage y `frontend/nginx.conf` con los 5 puntos del TRD §6.2 (incl. bloque `/ws/logs` completo) | `yarn`/`npm run build` genera `dist/`; docker build de la imagen frontend llega hasta nginx con los estáticos; revisión literal del bloque WS (§6.2.3) |
| 6 | `docker/Dockerfile` backend (build Maven 3.9/temurin-21 → runtime temurin-21-jre) + `compose.yaml` completo (topología, healthchecks, red, volumen, depends_on, sin ports backend/BBDD, sin mem_limit, sin `version:`) | `docker compose build` builda las 2 imágenes; `docker compose config` valida; nombres de servicio = los que exigen `SPRING_DATASOURCE_URL` y `proxy_pass` (TRD §6.3/§6.2.2) |
| 7 | `docker compose up -d`: pila arriba | healthchecks green en orden postgres → backend → frontend; `curl :8080` devuelve el `index.html` de prueba (Done del 06) |
| 8 | Cierre del Done: (a) Flyway en frío (D9); (b) fail-fast verificado (retirar un secreto del `.env` → `up` → el backend aborta con el error del D4; restaurar → green de nuevo); (c) echo test WS completo (§3); (d) proxy API en limpio: `curl :8080/api/v1/nonexistent` — una respuesta **404 JSON de Spring** (no el HTML de nginx) demuestra que el proxy llega al backend sin endpoints aún | Los 4 Done del 06 + echo test: todos registrados con evidencia para verify/archive |

Criterios del "Done cuando" del 06, todos cubiertos: pila arriba con
healthchecks green (paso 7), único puerto 8080 con `index.html` (7), Flyway V1
en frío (8a), fail-fast verificado (8b); extra obligatorio echo test (8c).

## 5. Preguntas para el gate del usuario

Máx. 5 (refinadas de explore §6). Recomendación marcada en cada una.

1. **Versiones de stack (D1)** — ¿aprobar la combinación **Java 21 LTS +
   Spring Boot 3.5.x + Maven 3.9 + Node 22 LTS + Vite 7 + React 19 +
   TypeScript 5.x** con tags exactos y actualización manual `--pull`?
   *(Recomendación: sí, tal cual. Alternativa aceptable: Java 25 LTS y/o Node
   24 LTS si se prefiere el borde más reciente.)*
2. **Echo test (D2)** — ¿handler temporal de echo **en la ruta definitiva
   `/ws/logs`** (sin ruta `/ws/echo`, para respetar TRD §9: el proxy solo
   enruta `/api/v1/**` + `/ws/logs`), verificado con `curl` (101) + script
   Python stdlib (frames) + conexión inactiva > 75 s, con modos reutilizables
   en C7? *(Recomendación: sí. Alternativa descartada: ruta `/ws/echo` con
   location adicional — viola el contrato de rutas del proxy.)*
3. **Esqueleto de paquetes (D6)** — ¿crear ya los 6 paquetes de feature +
   `config`/`common` (TRD §2.1) como `package-info.java` en C1, con
   `streaming` ya llevando el handler del echo? *(Recomendación: sí, fija la
   convención antes de C2. Alternativa: nacer por corte, descubre la
   convención C2.)*
4. **`index.html` de prueba (D5)** — ¿placeholder generada por el build Vite
   real (pipeline completo validado, sustituida en C8) o página estática
   "status" colocada a mano? *(Recomendación: la del build Vite real. La
   estática no valida el multi-stage del Dockerfile frontend.)*
5. **Dónde se valida primero la pila (D9/deploy)** — el Done del 06 dice
   "en la VM" pero el 07 pone el deploy en C9: ¿aceptas el criterio de C1 =
   **validar la pila completa en local (host de desarrollo)** con el
   procedimiento en frío de D9, dejando la subida a la VM + firewall para C9
   (que a su vez re-verifica arranque en frío en la VM)? *(Recomendación:
   sí — C1 termina verde en local; la VM no debe ser requisito de un corte de
   setup. Si prefieres otra cosa, decimos cómo: la pila es idéntica.)*

## 6. Riesgos y rollback

| Riesgo / pieza | Si falla al subir | Actuación |
|---|---|---|
| `postgres` no llega healthy (secreto/volumen/tag) | `docker compose logs postgres` | Corregir `.env`/tag; si el volumen quedó a medias en un intento local fallido: `docker compose down` + `docker volume rm project_log_pgdata` **solo en local** (nada que perder en C1; en la VM nunca) |
| Flyway falla aplicando V1 (driver/JSONB/TIMESTAMPTZ — riesgo #4 del 06) | logs del backend | V1 es literal del Schema §6.1: primera sospecha = difusión entre copia y doc; comprobar `docker compose exec postgres psql …` y reintentar; **nunca editar V1 tras aplicarlo** (checksum) — corrección solo si la copia difiere del doc |
| Backend aborta por fail-fast en falso | logs del arranque | Ajustar D4 (lista de exigidas) — la corrección es config, no código de lógica. |
| WebSocket no negocia 101 tras nginx (riesgo #1 del 06) | curl -i / script D2 | Verificar el bloque `/ws/logs` punto por punto contra TRD §6.2.3 (http_version 1.1, Upgrade, Connection, timeouts); nunca probar solo en dev |
| Build Vite o Dockerfile frontend | `docker compose build frontend` | Revisar multi-stage (node build → nginx); la imagen nginx estable y el pattern no cambian entre cortes |
| Elección de versiones (D1) desfasa | — | El punto 1 del gate es la decisión cambiante: si el usuario cambia algo, afecta POM + 2 Dockerfiles **antes** de la primera tarea; el coste de cambio es puntual en C1 y no arrastra a C2+ (los componentes del stack consumen APIs estables) |
| `docker compose down -v` por error en la VM | — | Procedimiento del TRD §6.4: `-v` prohibido salvo desastre deliberado; en C1 local el volumen es desechable, en la VM jamás |

**Qué se toca de los docs: nada.** Todos los literales se citan (TRD §6.1–6.5,
§9; Schema §6.1–§6.3; 06 Fase 0; GATE §8). Si surgiera una discrepancia entre
la implementación y un doc durante apply, se propaga PRD → TRD → resto según
la regla del 06 (docs vivos), nunca se parchea solo en código.

## 7. Fuera de alcance explícito

- Nada de **auth/Argon2/JWT** (C2: SecurityFilterChain, seed del admin,
  `/auth/login`, `/me`); C1 no consume `FRONTEND_ORIGIN` ni firma nada.
- Nada de **CRUD de proyectos** (C3), **API keys** (C4), **ingesta** (C5),
  **consulta keyset** (C6), **handler real de WS con HandshakeInterceptor y
  emisión tras COMMIT** (C7) — el handler/echo es **infraestructura de prueba
  del proxy, no una feature**, y desaparece/sustituye en C7.
- Nada del **panel real** (C8: maquetas 01–05), del **deploy a la VM +
  firewall** (C9) ni de borrados/resumen (C10).
- Nada de v2: sin rate limiting (ADR-26), sin backups (§6.6), sin `mem_limit`
  (ADR-27), sin retención, sin TLS (ADR-23), sin trigram en V1 (Schema §6.2).

## 8. Siguiente paso recomendado

**`next_recommended`: gate del usuario sobre esta propuesta (puntos 1–5 de
§8).** Con la aprobación (y los ajustes que el usuario pida), el siguiente
paso del flujo SDD para C1 es `sdd-spec`.

---

## GATE: puntos a aprobar por el usuario

1. **Versiones de stack**: Java 21 LTS + Spring Boot 3.5.x + Maven 3.9 + Node
   22 LTS + Vite 7 + React 19 + TS 5.x, tags exactos en POM e imágenes.
2. **Echo test WS**: handler temporal de echo en la ruta definitiva
   `/ws/logs` (sin ruta nueva), smoke automatizable con `curl` + script
   Python stdlib + conexión inactiva > 75 s, reutilizable en C7.
3. **Esqueleto de paquetes**: los 6 paquetes de feature + `config`/`common`
   (TRD §2.1) creados ya en C1 (`package-info.java`), con `streaming`
   llevando el handler del echo.
4. **`index.html` de prueba**: placeholder generada por el build Vite real
   (valida pipeline completo), temporal hasta C8.
5. **Criterio de validación**: pila completa validada en local dentro de C1
   (con Flyway en frío y fail-fast verificados); subida a la VM + firewall
   queda en C9.