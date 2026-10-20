---
change: "C1"
artifact: verify-report
project: "Project_Log"
last_updated: 2026-10-20
status: done
---

# Verify — C1 · Fase 0: Setup de pila y repo

> Verificación de sdd-verify sobre spec.md (R1–R15, E1–E12), tasks.md (26
> tasks) y apply-progress.md (evidencia del apply). Se hicieron spot-checks
> estáticos del repo real (git, pom.xml, Java, SQL, nginx, compose, Dockerfiles,
> scripts, docs). **No existe qa-report.md** (por decisión del coordinador la
> verificación de C1 se apoya en apply-progress.md y en los smoke del corte):
> se registra como limitación de evidencia, NO como fallo.

**resultado: PASS-with-notes**

Sin hallazgos CRITICAL. Los escenarios E1–E12 están todos VERDES con
evidencia real del apply; los spot-checks estáticos del repo confirman los
contratos citados (V1__init.sql literal, nginx, compose, healthchecks,
env §6.3, paquetes §2.1, versiones). Las notas son: (a) la pila nunca se
levantó con `docker compose` canónico — equivalencia verificada con podman
rootless (R-nuevo-2), con verificación canónica pendiente para C9; y (b) dos
ecos menores recomendados en docs (D7) / notas de archive (D4), ninguno
bloqueante.

---

## 1. Verdict por escenario E1–E12

| E | Estado | Evidencia de apply-progress (comando/salida esencial) | Spot-check del repo |
|---|---|---|---|
| E1 | **CONFIRMADO** | `podman ps` → postgres/backend/frontend `Up (healthy)` estables, sin bucles; transición `starting → healthy` monitoreada en orden encadenado | `compose.yaml`: `depends_on: service_healthy` encadenado postgres → backend → frontend, `restart: unless-stopped` ×3 (§6.5/ADR-21) — coincide |
| E2 | **CONFIRMADO** | `curl http://localhost:8080/` → `200 text/html` con placeholder "pila OK"; `podman port backend/postgres` → vacíos | `compose.yaml`: único bloque `ports:` (`${FRONTEND_BIND:-0.0.0.0}:8080:80`); nginx `listen 80` con `root /usr/share/nginx/html` |
| E3 | **CONFIRMADO** | En frío (bajada + `podman volume rm project_log_pgdata`): log `Successfully applied 1 migration`; psql efímero: `\dt` → `users, projects, logs` + fila V1 `success=t`; 4 índices presentes incl. `projects_api_key_hash_idx` único parcial; **sin** `logs_message_trgm_idx`; segundo arranque: `Schema "public" is up to date` (idempotencia) | `diff` de `backend/src/main/resources/db/migration/V1__init.sql` vs bloque literal de `docs/05_BACKEND_SCHEMA.md` §6.1 → **IDENTICAL** (trigram solo comentada) — R6 alineado al carácter |
| E4 | **CONFIRMADO** | Quitados `JWT_SECRET` + `ADMIN_INITIAL_PASSWORD` → **UN único** `IllegalStateException: faltan 2 de las 6 variables exigidas` con lista completa, antes de Tomcat; health sin verde fugaz; bucle `unless-stopped` (RG-6, esperado); restaurado → healthy; extra: sin env, error con las 6 | `StartupSecretsValidator`: `EnvironmentPostProcessor`, 6 exigidas en `REQUERIDAS`, acumulación sin cortar, blanco=fallo, `JWT_SECRET ≥ 32 bytes UTF-8`, único `IllegalStateException`; `spring.factories` con la clave exacta DD-3; las 8 con default ausentes del código |
| E5 | **CONFIRMADO** | `curl -i` con upgrade headers → `HTTP/1.1 101`, `Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=` (el exacto para la key de ejemplo RFC 6455) vía `Server: nginx/1.30.6` — negociado por el bloque `/ws/logs` | `nginx.conf`: `location /ws/logs` con `proxy_http_version 1.1`, Upgrade/Connection, `proxy_pass http://backend:8080` sin URI final; `WebSocketConfig`: un único `addHandler(…, "/ws/logs")`, sin SockJS ni interceptors |
| E6 | **CONFIRMADO** | `python3 scripts/ws_echo_test.py` completo: «Eco full-duplex: 3 frames idéntico byte a byte» + «Cierre limpio … close frame 0x8 de vuelta», RC=0 | Script: stdlib solo (argparse/base64/hashlib/os/socket/struct/sys/time), frames mascarados, `WS_GUID` RFC 6455, chmod +x |
| E7 | **CONFIRMADO** | Smoke: «Inactividad > 75 s: la conexión SIGUE viva» (RC=0); greps del conf: 2×`3600s` + `proxy_http_version 1.1` + Upgrade/Connection | Literales verificados en `frontend/nginx.conf` carácter a carácter (TRD §6.2.3/ADR-18) |
| E8 | **CONFIRMADO** | `curl -H "Accept: application/json" http://localhost:8080/api/v1/nonexistent` → `404 application/json` con cuerpo JSON de Spring (`{"timestamp","status":404,"error":"Not Found","path":…}`) → el proxy alcanza al backend, no hay HTML de nginx | `nginx.conf`: `location /api/v1/ { proxy_pass http://backend:8080; }` sin URI final (DD-2/DD-4); README documenta RG-8 |
| E9 | **CONFIRMADO** | `curl http://localhost:8080/ruta/de/panel/inexistente` → `200 text/html` con el index placeholder (SPA fallback, sin 404 de nginx) | `location / { try_files $uri /index.html; }` literal §6.2.1 |
| E10 | **CONFIRMADO** | En red (exec): `/actuator/health` → **200**; `/actuator/info` → **200**; `/actuator/env` → **404**; desde el host `/actuator/health` devuelve el index.html del SPA (el nginx NO enruta `/actuator/**`) | `application.yml`: `exposure.include: health,info` literal (R12/L7); nginx solo enruta `/api/v1/**` + `/ws/logs` (TRD §9/ADR-5) |
| E11 | **CONFIRMADO** | `stat -c %a .env` → **600**; `git status` no lista `.env`; `git check-ignore`: `.gitignore:7:.env` y `!.env.example` presente; `.env.example` sin secretos reales | Re-echo propio: `git check-ignore -v .env` → `.gitignore:7:.env`; `git ls-files` lista `.env.example`; `.env` real = 600; `.env.example` = 644, solo placeholders `<generar-…>` |
| E12 | **CONFIRMADO** | Jar 21 bits compilado en `maven:3.9-eclipse-temurin-21` (Java 21.0.12, Maven 3.9.16); `npm ci && npm run build` exit 0 con `dist/index.html`; 2 imágenes reconstruidas con tags exactos; árbol 1:1 con design §1.1 (32 ficheros, D6); `git status` limpio | `git ls-files` = **32** ficheros = lista [1]–[32] del design (verificado 1:1); 8 paquetes + `Application` en `com.projectlog` (TRD §2.1/L5); jar de commit #2 = 27.7 MB con spring.factories + V1 + classes |

Limitación de evidencia (transversal): **no existe qa-report.md** y la
evidencia dinámica proviene del apply (podman, sin Docker/Compose v2 en el
host). No hay verificación independiente re-ejecutada por verify contra la
pila; lo estático (contratos literales) sí se re-verificó directamente sobre
el repo. Asumido por decisión del coordinador para este corte.

---

## 2. Cobertura de requisitos R1–R15

| Req | Verificado por | Estado | Soporte |
|---|---|---|---|
| R1 (repo git, layout, groupId/artifactId, .env fuera de git) | E11, E12 | **OK** | 6 commits en `main`; `git ls-files` = 32 = árbol design 1:1; `.env` ign |
| R2 (stack y versiones exactas) | E12 | **OK** (ver nota S2 sobre rangos en package.json) | POM parent 3.5.16; `maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre`, `node:22-alpine`, `nginx:stable-alpine`, `postgres:17-alpine`; cero `latest` (grep rc=1); lock resuelve Vite 7.3.7 · React 19.3.1 · TS 5.9.3 |
| R3 (8 paquetes + Echo/WSConfig/validador) | E12 | **OK** | ls-files: `auth, projects, keys, ingest, query, streaming (Echo), config (WSConfig+Validator), common` + `Application` |
| R4 (echo en /ws/logs, sin rutas nuevas, smoke) | E5, E6, E7 | **OK** | Un único `addHandler`; `/ws/echo` inexistente; smoke stdlib con `--solo-handshake`/`--idle-segundos 75`/`--token` |
| R5 (fail-fast de secretos) | E4 | **OK** | Validator (DD-3) como arriba |
| R6 (V1 literal + Flyway auto + inmutable) | E3 | **OK** | diff vs Schema §6.1 = IDENTICAL; ddl-auto: none |
| R7 (scaffold Vite real, placeholder, sin /api/v1) | E2, E10, E12 | **OK** | `npm ci && build` OK; `App.tsx` placeholder marcado temporal, texto "Project_Log — pila OK", sin llamadas a /api/v1 |
| R8 (Dockerfiles multi-stage) | E12, E2 | **OK** | Ambos multi-stage, contextos correctos (`./backend` + `../docker/Dockerfile`), sin `HEALTHCHECK` (DD-5) |
| R9 (nginx 5 puntos) | E5, E7, E8, E9, E10 | **OK** | Los 5 puntos literales §6.2 confirmados, sin TLS, sin URI final en proxy_pass |
| R10 (compose) | E1, E2, E10 | **OK** | Sin `version:`, 1 `ports:`, red `project_log_net`, volumen `pgdata` en `/var/lib/postgresql/data`, healthchecks literales §6.5, encadenado, restart ×3, sin `mem_limit`, sin secretos |
| R11 (.env.example según §6.3) | E11 | **OK con D7** | Nombres 1:1 con la **tabla** §6.3 (ver D7: la tabla enumera SPRING_DATASOURCE_USERNAME/PASSWORD aunque el bloque literal de ejemplo del doc los omitía); comentarios openssl 48/24; FRONTEND_BIND comentada |
| R12 (actuator health,info) | E10 | **OK** | application.yml literal |
| R13 (.gitignore) | E11 | **OK** | `.env`, `.env.*` + `!.env.example`, target/, node_modules/, dist/, .vite/, IDE, OS (+ reglas extra D1 documentadas, fuera del árbol) |
| R14 (README operativo) | E12/cierre | **OK** | Los 4 puntos (cp/chmod 600, up, smoke, Flyway en frío) + Compose v2 + diagnóstico RG-6 + curl RG-8; sin secretos |
| R15 (prohibiciones) | transversal | **OK** | Sin endpoints/negocio/seed/CORS/trigram/mem_limit/TLS/rate-limits; sin starter-test; un único addHandler; V1 sin editar post-aplicación; .env fuera del índice |

15/15 requisitos cubiertos. Ningún requisito queda sin escenario o sin
evidencia. Cobertura E ↔ R completa (según las cabeceras del propio spec §2).

---

## 3. Desviaciones y su evaluación

### D4 — `spring-boot-starter-jdbc` añadido al POM (fix `2394c4c`, R-nuevo-1)

**Qué pasó**: el diseño/spec omitían que Boot 3.5.x NO autoconfigura el
`DataSource` sin spring-jdbc+HikariCP; sin la dependencia el arranque subía
Tomcat **sin ejecutar Flyway** (E3 habría quedado rojo de forma silenciosa).
Se añadió `spring-boot-starter-jdbc` al POM. Nota también verificada:
`spring-boot-starter-flyway` (de Boot 4) no existe en la línea 3.5.x.

**Evaluación**:
- **Coherencia técnica (DD-1, R6)**: SÍ. R6 exige «Flyway DEBE aplicarla
  automáticamente al arrancar el backend» y E3/06 lo hacen criterio Done;
  sin spring-jdbc ese requisito es físicamente inalcanzable en Boot 3.5.
  La dependencia no es «sobrante» (R2: «ninguna sobrante» se refiere a
  dependencias de negocio): es la infra mínima que el propio R6 requiere.
  El spec R2 enuncia el mínimo de la Fase 0 sin cerrar el detalle de
  autoconfiguración — el fix corrige un hueco del design, no una
  contradicción de spec.
- **Afectación**: ninguna — starter-jdbc no añade beans de negocio, ni
  JPA (no entra `spring-boot-starter-data-jpa`), ni `ddl-auto` (sigue
  `none` en application.yml, DD-6), ni SecurityFilterChain.
- **¿Eco en docs?** **NO** (recomendación). Ningún doc en `/docs` con
  estado `valido` enumera dependencias Maven: `grep spring-boot-starter`
  sobre `02_TRD.md` y `06_IMPLEMENTATION_PLAN.md` = cero resultados. Es un
  ajuste de implementación, no contractual (TRD §6 habla de topología,
  healthchecks y env; el Schema solo del SQL — ambos intactos). Basta
  registrar R-nuevo-1 en las notas del sdd-archive; **no se requiere nueva
  validación de ningún doc del paquete**.

### D7 — `SPRING_DATASOURCE_USERNAME`/`_PASSWORD` añadidas a `.env.example`

> **Corrección de atribución**: el fix de `.env.example` (+4 líneas) está
> dentro del **commit #4 `e861d7b`** (retoque [2] del apply), NO en
> `26236f8` — `26236f8` es el fix D5 (eliminación de `frontend/src/index.css`
> fuera del árbol). Aplicar esta corrección a cualquier registro que cite
> los hashes.

**Qué pasó**: el bloque literal «Formato propuesto de `.env.example`» de
`02_TRD.md` §6.3 omitía las dos variables, aunque **la propia tabla del §6.3
las enumera** como variables backend («`SPRING_DATASOURCE_USERNAME` /
`SPRING_DATASOURCE_PASSWORD` — credenciales BBDD (las de postgres)»), y R5
obliga al validador a exigirlas. Sin ellas el `.env.example` era internalmente
inconsistente con R5: el fail-fast habría abortado con un `.env` copiado
"completo" según el ejemplo del doc.

**Evaluación y recomendación explícita**:
- **¿Es contractual?** Es un **hueco del DOC, no del contrato**. El §6.3
  dice textualmente: «Los nombres finales se alinean con la configuración
  real del backend al montarlo (**la tabla de aquí fija el conjunto
  mínimo**)». La tabla es la autoridad de nombres y YA incluye ambas
  variables; el bloque de ejemplo es ilustrativo y quedó desincronizado de
  su propia tabla. Por tanto el fix **no altera ningún nombre contractual**
  (ninguna variable nueva se inventa; las two añadidas ya eran contrato) y
  **no requiere re-validación de contrato**.
- **Sin el fix habría sido un bug de usabilidad** (fail-fast E4 contra un
  `.env` "fiel al doc"), así que la desviación es correcta y la menos
  invasiva posible (2 variables con comentario que cita la tabla del §6.3).
- **Eco recomendado**: SÍ, **bajo y no bloqueante** — al próximo paso de
  validación de docs que toque el TRD, añadir las 2 líneas al bloque de
  ejemplo del §6.3 para que la tabla y el ejemplo dejen de divergir. NO
  justifica por sí sola una re-validación de docs ahora (no cambia el
  conjunto de nombres, solo sincroniza el ejemplo). Registrar en
  sdd-archive como "eco pendiente, menor".

### Entorno (R-nuevo-2) — sin Docker/Compose v2; verificación con podman 5.8.7 rootless

**Qué pasó**: `docker` y `docker compose` no están instalados en el host
(verify re-confirma: `docker --version` → «instrucción no encontrada»; la
instalación de podman-compose fue denegada por el entorno). La pila se
levantó con `podman run` manual replicando la topología del compose.yaml
(D3).

**Grado de equivalencia — alto en lo observable, parcial en lo mecánico**:

| Aspecto | Equivalencia |
|---|---|
| Red bridge + DNS por nombre de servicio (`postgres`, `backend` resolvibles) | **Total** (netavark/aardvark vs DNS embebido de Docker; semántica igual para este caso) |
| Healthchecks (CMD-SHELL, cadencias 10s/30s/15s, start_period) | **Total** (mismos literales y cadencias del TRD §6.5, ejecutados de verdad) |
| Encadenado de arranque (`depends_on: service_healthy`) | **Emulado** con monitores externos de transición — el orden se verificó, pero el mecanismo del compose NO fue parseado/ejecutado por la herramienta canónica |
| Volumen nombrado (`project_log_pgdata`), restart `unless-stopped` | **Total** |
| Build de imágenes | **Alta** (Dockerfile plano, sin syntax oddities; Docker construirá sus propias capas en C9; los tags coinciden) |
| **Validación sintáctica de compose.yaml** (`docker compose config`) | **NO EJECUTADA con la herramienta canónica** (se asertó el YAML por otros medios) |

**Riesgo residual**: **medio-bajo, contenido y localizado en C9** (deploy en
VM con Docker, GATE #11). Los puntos donde el `docker compose` canónico
podría divergir del podman-manual son: (1) interpretación del
`depends_on: service_healthy` real, (2) interpolación
`${FRONTEND_BIND:-0.0.0.0}:8080:80` por el parser Compose, (3) errores de
esquema que `docker compose config` detectaría (p. ej. claves mal anidadas).
Ninguno de estos es plausible de haber sido introducido (el fichero es
simple y sigue DD-5 al pie), pero **ninguno quedó demostrado con la
herramienta canónica**: E1/E2/E10 son "equivalentes verificados", no
"canónicos". **Mitigación obligatoria en C9**: primera acción del corte =
`docker compose config` (sin errores) + `docker compose up -d` en la VM +
re-ejecución del smoke canónico (`python3 scripts/ws_echo_test.py` completo)
contra la pila Docker real. No bloquea el archive de C1.

---

## 4. Spot-checks de contratos (literal ↔ repo)

| Contrato | Fuente | Resultado del spot-check |
|---|---|---|
| `V1__init.sql` literal completo | 05_BACKEND_SCHEMA §6.1 | **diff = IDENTICAL** (3 tablas + constraints + 4 índices; trigram solo comentada, reservada a V2 §6.2) |
| Bloque nginx `/ws/logs` | 02_TRD §6.2.3 / ADR-18 | **Coincide al carácter**: `proxy_http_version 1.1`, `Upgrade $http_upgrade`, `Connection "upgrade"`, `proxy_read_timeout 3600s`, `proxy_send_timeout 3600s` |
| Healthchecks y cadencias | 02_TRD §6.5 / ADR-21 | **Literales en compose.yaml** (pg_isready 10s/5s/5 · curl `/actuator/health` 30s/5s/5 + `start_period: 60s` · wget 15s/3s/3); cero `HEALTHCHECK` en Dockerfiles (DD-5); `curl` instalado en la imagen runtime del backend con comentario RG-4 |
| Nombres env | 02_TRD §6.3 (tabla) | **1:1** (ver D7: tabla sí enumera SPRING_DATASOURCE_*, el bloque de ejemplo del doc no — fix alineado con la tabla) |
| Paquetes | 02_TRD §2.1 | **Exactos**: `auth projects keys ingest query streaming config common` + `Application` en `com.projectlog` |
| Actuator `health,info` | TRD §6.5/§9 | `management.endpoints.web.exposure.include: health,info` literal |
| Versiones aprobadas (gate) | tasks §0.2 | POM parent **3.5.16** (patch exacto); Java 21 Temurin (compilado en 21.0.12); Maven 3.9 (`maven:3.9-eclipse-temurin-21`); `eclipse-temurin:21-jre`; `node:22-alpine`; lock → **Vite 7.3.7 · React 19.3.1 · TS 5.9.3** · plugin-react 5.2.0; `nginx:stable-alpine`; `postgres:17-alpine` |
| RG-1 (sin tags flotantes / latest) | tasks §0.2 | grep `latest` en compose/Dockerfiles/pom → **cero** matches |
| proxy_pass sin URI final | DD-2/DD-4 | Ambos `proxy_pass http://backend:8080;` sin URI |
| Rutas del proxy | TRD §9 / ADR-5 | Solo `/api/v1/` + `/ws/logs`; cero `listen 443`/ssl/tls |
| POM deps | R2/DD-1 + D4 | web, actuator, websocket, **jdbc (D4)**, flyway-core + flyway-database-postgresql, postgresql runtime; **sin starter-test** |

---

## 5. Hallazgos

**CRITICAL — ninguno.** No hay ninguna task que reabrir.

**WARNING — ninguno bloqueante.**

- **W1** (registrado, no bloqueante): la composición canónica nunca se
  ejecutó (`docker compose config` / `up` — R-nuevo-2/D3). E1/E2/E10 quedan
  verificados por equivalencia, no canónicos. Acción heredada a C9 (ver §7).
- **W2**: `.env.example` en `644` y `.env` real en `600` — correcto; el
  README ya ordena `chmod 600`. Sin acción.

**SUGGESTION**

- **S1 — atribución de hash de D7**: el fix de `.env.example` está en
  `e861d7b`, no en `26236f8` (que es D5/index.css). Corregir en los registros
  que lo citen; apply-progress §3 ya es correcto (lista ambos commits sin
  cruzarlos).
- **S2 — rangos semver en `frontend/package.json`**: `^19.3.1`, `^7.3.7`,
  `~5.9.3` son rangos (los tasks pedían "fix exacto en package.json"). El
  RG-1 nominal cubre imágenes y POM (ambos exactos, verificados), y el
  `package-lock.json` comprometido fija la resolución exacta (7.3.7/19.3.1/
  5.9.3), así que `npm ci` es determinista y el requisito de fondo (DD-4,
  reproducibilidad) se cumple. Sugerencia menor: fijar sin prefijo (`19.3.1`,
  etc.) en C2 cuando se toquen dependencias, por uniformidad con la política
  RG-1. No bloquea.
- **S3 — ecos de docs recomendados (no ahora)**: (a) 2 líneas al bloque de
  ejemplo del TRD §6.3 (D7, ver arriba); (b) nota R-nuevo-1 (starter-jdbc)
  en las notas de archive. Ninguno exige re-validación de docs con estado
  `valido` en este corte.

---

## 6. Comprobación del repo

- **6 commits en `main`** desde `bd60b0d` a `e861d7b` + 2 fixes, en orden:
  `bd60b0d` (init: .gitignore + .env.example + README — exactamente 3
  ficheros, R1) · `521582b` (backend, 16 ficheros [5]–[20]) · `c331148`
  (frontend, 9 ficheros con lock) · `e861d7b` (docker + retoque .env.example) ·
  `2394c4c` (D4: starter-jdbc) · `26236f8` (D5: sin index.css).
- **32 ficheros versionados** (`git ls-files | wc -l` = 32), coincidencia 1:1
  con la lista [1]–[32] del design §1.1 (D6: la lista es la autoridad sobre
  el «31» del design).
- **Rama**: solo `main`; sin remote, sin push. `git status` limpio.
- **`.env` fuera del índice (E11)**: `git check-ignore -v .env` →
  `.gitignore:7:.env`; `.env` real existe en la raíz con permisos **600**;
  `.env.example` versionado y sin secretos reales (solo placeholders y
  `<generar-…>`).
- Todos los commits llevan el trailer `Co-Authored-By: Claude Code
  <noreply@anthropic.com>` (según apply-progress §3; el mensaje está en los
  propios commits del historial verificado).
- Identidad repo-local provisional (`acuelop369 <acuelop369@homelab.local>`)
  documentada; el usuario puede reescribirla (no bloquea).

## 7. Recomendación final

**PROCEDE a sdd-archive** con resultado PASS-with-notes. Los 26 tasks están
hechos con evidencia, E1–E12 verdes con salida real, R1–R15 cubiertos, los
contratos literales se confirman contra el repo, y las dos desviaciones
sustantivas (D4, D7) están bien resueltas y correctamente documentadas.

**Acciones heredadas para cortes siguientes** (registrar en archive):

1. **C9 (obligatorio)**: primera acción del corte en la VM = `docker compose
   config` (sin errores ni warnings graves) → `docker compose up -d` →
   smoke canónico completo (`python3 scripts/ws_echo_test.py`, incluyendo
   inactividad) contra la pila Docker real. Todo E1/E2/E10 de C1 es
   equivalencia-podman hasta que eso pase (W1/R-nuevo-2).
2. **C7**: reutilizar `scripts/ws_echo_test.py --solo-handshake` y el
   parámetro `--token` tal como están (sin reescritura, R4); sustituir
   Echo por LogSocketHandler cambiando solo el registro en
   `WebSocketConfig`.
3. **C2**: al tocar dependencias frontend, fijar versiones sin `^`/`~` (S2).
4. **Próxima validación de docs**: sincronizar el bloque de ejemplo del
   TRD §6.3 con su propia tabla (2 líneas, D7). No re-validar docs solo por
   esto.
5. **Archive**: registrar R-nuevo-1 (starter-jdbc) y R-nuevo-2 (podman) como
   requisitos añadidos en apply, y la corrección de hash de D7 (S1).