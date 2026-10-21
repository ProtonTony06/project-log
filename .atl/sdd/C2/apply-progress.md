---
change: "C2"
artifact: apply-progress
last_updated: 2026-10-21
status: done
re-apply: Q24-fixed
---

# Apply progress — C2 · Seguridad base + auth del panel

## Entorno y desviaciones de entorno (no de diseño)

- **Maven en contenedor**: no hay JDK en el host (solo JRE 25, sin `javac`),
  así que TODO `mvn` se ejecuta vía podman con la imagen
  `docker.io/library/maven:3.9-eclipse-temurin-21` (la del Dockerfile de C1),
  montando `backend/` y `~/.m2/repository`. Comando: wrapper `/tmp/pmvn.sh`.
- **Compose provider**: `podman compose` inicialmente fallaba
  ("looking up compose provider failed" — no había docker-compose ni
  podman-compose en el sistema, contrario a lo asumido por RG-6). Resuelto
  instalando `podman-compose 1.6.0` vía pip de usuario
  (`python3 -m ensurepip --user` + `pip install --user podman-compose`);
  verificado `podman compose config` OK. Cambio de ENTORNO, no de docs.

## Fase F0 — dependencias y config base

- [x] 1.1 pom.xml: starter-security (BOM), bcprov-jdk18on:1.86 runtime,
  jjwt-api 0.13.0 compile + impl/jackson runtime, starter-test test
  (G5/RG-1). `dependency:tree`: BC/jjwt en runtime, jjwt-api compile,
  starter-test en `maven.test` — evidencia en sesión. Cero rangos (el único
  match de "LATEST" es el comentario de C1 "nunca 3.5 flotante ni LATEST").
- [x] 1.2 application.yml: comentario de cabecera actualizado + bloque
  `projectlog.security` con `${JWT_TTL_HOURS:8}` y `${FRONTEND_ORIGIN:}`;
  `exposure.include: health,info` intacto.
- [x] 1.3 compose.yaml: +1 línea `FRONTEND_ORIGIN: ${FRONTEND_ORIGIN}` en
  backend.environment; `podman compose config` sin errores con la variable.
- [x] 1.4 build `mvn -q -DskipTests package` → EXIT 0.
- **Commit #1**: `3c60e86` feat(security): dependencias y configuración base
  para la seguridad de C2 — 3 ficheros exactos (pom, yml, compose).

## Fase F1 — cadenas, filtro JWT y CORS

- [x] 2.1 RestAuthenticationEntryPoint [N12]: único escritor 401 JSON
  `{code: invalid_token, message: Autenticación requerida}`, ObjectMapper,
  sin logger, no lee Authorization. **Fix en build**: también implementa
  `AccessDeniedHandler` (CD-1 pedía accessDeniedHandler=entry point y Spring
  exige el tipo en el DSL) — mensaje/cuerpo idénticos en ambos caminos.
- [x] 2.2 CorsConfig [N13]: origen único reflect exacto, sin `*` ni
  `allowedOriginPatterns`, sin credenciales, headers
  Authorization/Content-Type/X-Confirm-*, maxAge 3600; origen vacío ⇒ cero
  configuraciones registradas.
- [x] 2.3 TokenService [N2] (adelantado a F1 por dependencia de compilación
  de 2.4 — nota de orden §1 del tasks): SecretKeySpec sobre
  `getBytes(StandardCharsets.UTF_8)`; `issue()` devuelve token + exp juntos
  (CD-6); `verify()` atrapa `JwtException|IllegalArgumentException` →
  Optional vacío sin causa; sin `clockSkewSeconds` (comentario S14); API
  0.13.0 no deprecada (`Jwts.SIG.HS256`, `parser().verifyWith`,
  `parseSignedClaims`), cero imports `io.jsonwebtoken.impl.*`.
- [x] 2.4 JwtPrincipal [N4] + JwtAuthFilter [N3]: filtro sin escritura de
  respuesta (grep sin `response.setStatus`/`getWriter`), contexto solo con
  token válido, autoridades vacías.
- [x] 2.5 SecurityConfig [N11]: 3 beans SecurityFilterChain @Order(1)/(2)/(3)
  con matchers literales; login `POST /api/v1/panel/auth/login` permitAll;
  /actuator/health, /actuator/info y /ws/logs permitAll en la 3; denyAll en
  1 y 3; cero `httpBasic(` / `formLogin(` en código (solo javadoc).
- [x] 2.6 build EXIT 0.
- **Commit #2**: `c051047` feat(security): tres cadenas SecurityFilterChain,
  filtro JWT HS256 y CORS mínimo — 6 ficheros [N2][N3][N4][N11][N12][N13].

## Fase F2 — auth

- [x] 3.1 PasswordConfig [N1]: `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)`
  con comentario OWASP 2024; sin defaults v5_8/v5_5.
- [x] 3.2 AdminSeeder [N5]: COUNT tabla-vacía (G4) + INSERT con `role='ADMIN'`
  explícito y `ON CONFLICT ON CONSTRAINT users_username_unique DO NOTHING`;
  logs sin username/hash. `db/migration/` sigue = solo V1. (Desvío menor
  de estilo: el log de omisión reporta el número de filas ya presentes
  — sin valores sensibles.)
- [x] 3.3 LoginRequest/LoginResponse/LoginController [N8][N9][N6]: misma
  constante `CODE_INVALID_CREDENTIALS` en las 3 ramas de fallo; hardening de
  tiempo con hash dummy generado por el propio encoder en el constructor
  (garantiza PHC parseable — el desvío sobre el literal "hash dummy constante
  de la clase" es solo el MECANISMO: constante durante la vida del proceso);
  formatter `yyyy-MM-dd'T'HH:mm:ss'Z'` UTC compartido con /me; handler de
  `HttpMessageNotReadableException` → 401 (no 400).
- [x] 3.4 MeController/MeResponse [N7][N10]: sin JdbcTemplate, rol y
  expires_at de los claims.
- [x] 3.5 build EXIT 0; `ls db/migration/` = solo V1__init.sql.
- **Commit #3**: `f69830c` feat(auth): login y /me con Argon2id y seed
  idempotente del admin — 7 ficheros [N1][N5]–[N10].

## Fase F3 — tests [L]

- [x] 4.1 Argon2PasswordTest [T1] **ejecutado temprano** (advertencia §4.1):
  verde — `NoClassDefFoundError` de BC descartado (issue #8842 cubierto).
- [x] 4.2 TokenServiceTest [T2]: verde tras ajustar la aserción de
  precisión (jjwt serializa claims de fecha a segundos: exp del parse =
  issued truncado a segundo; el expires_at de la API sale del formatter,
  CD-6).
- [x] 4.3 PanelAuthMvcTest [T3]: verde — 401 JSON invalid_token para
  ausente/malformado/firma alterada/expirado, content-type JSON, y aserción
  explícita E2.19 (ListAppender sobre logger com.projectlog, sin
  Authorization/Bearer/JWT ni fragmentos).
- [x] 4.4 CorsConfigTest [T4]: verde — origen vacío ⇒ sin configuraciones;
  con origen ⇒ reflejo exacto, credenciales false, headers/métodos S25.
- [x] 4.5 LoginCredentialsTest [T5]: verde — 3 ramas 401 con la MISMA
  constante; el slice importa la seguridad real (sin ello, Boot aplicaba su
  config default → 403 y CSRF, falsando S17).
- [x] 4.6 `mvn test` completo → **Tests run: 25, Failures: 0, Errors: 0**.
- **Commit #4**: `bdbb258` test(backend): tests ligeros de C2 — 5 clases
  [T1]–[T5] + `src/test/resources/application-test.yml`.
- **Commit de fix**: `829cc70` fix(security): CD-8a — inerte el usuario
  in-memory de Boot excluyendo UserDetailsServiceAutoConfiguration. Fallo
  observable en F3: con starter-security, Boot imprime "Using generated
  security password" incluso con las 3 cadenas propias (evidencia: logs del
  slice LoginCredentialsTest). application.yml gana
  `spring.autoconfigure.exclude` — S3 por literal (usuario inerte), sin
  beans de seguridad añadidos.
- **Desvío CD-9 registrado**: +1 fichero de recursos de test
  (`backend/src/test/resources/application-test.yml`, dummy values de las 6
  vars) — la solución prevista por CD-9: el StartupSecretsValidator de C1
  (intocable) corre en cada contexto y abortaba los slices. El árbol de
  `src/test/java` sigue siendo SOLO las 5 clases.

## Fase F4 — verificación podman

Contexto del arranque de F4 (desviación de entorno, no de diseño): la pila
"green" heredada de C1 eran contenedores SIN labels de compose (creados al
margen de podman-compose con el mismo volumen `project_log_pgdata`) — al
montar los nuevos de C2 ambos postgres convivieron sobre el MISMO volumen,
con riesgo de estado inconsistente (la primera fila seedada con E2.7 no era
verificable: COUNT=0 al inspeccionar). Resolución: `podman compose down` +
`podman volume rm project_log_pgdata` **solo en local** (tal como prevé el
paso 5.5) → arranque limpio → re-ejecución completa de smokes.

- [x] 5.1 rebuild backend (imagen `localhost/project_log_backend:latest`
  construida con las deps de C2; 2 stages OK) + `up -d` recreation:
  3 contenedores `Up (healthy)`; `podman exec project_log_backend_1
  printenv FRONTEND_ORIGIN` → valor del `.env` (passthrough CRÍTICO OK,
  advertencia §4.4); `printenv JWT_TTL_HOURS` → 8.
- [x] 5.2 E2.1 login correcto [S/obligatorio]: **200**
  `{token, username, expires_at}`; token de 3 segmentos, header decodificado
  `{"alg":"HS256"}`; claims `exp,iat,role,sub`; `exp−iat = 28800 s = 8 h`
  (TTL por defecto); `expires_at=2026-10-21T17:51:51Z` (formato
  `^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$`). Logs SIN
  "Using generated security password" (CD-8a, commit fix 829cc70
  verificado en pila real).
- [x] 5.2 E2.2 login 401 [S/obligatorio]: los 3 casos — password
  incorrecto, username inexistente, cuerpo malformado (`no-es-json`) →
  **401** con el cuerpo IDÉNTICO
  `{"code":"invalid_credentials","message":"Credenciales inválidas"}` en
  los tres (sin user-enumeration, S17). (Nota: el primer intento de script
  falló por interpolación bash sin escapar el JSON de la contraseña con
  caracteres especiales; resuelto construyendo el body con python — no fue
  un fallo del backend.)
- [x] 5.3 E2.3 panel protegido sin JWT [S/obligatorio]: `GET /me` sin
  Authorization, `POST /api/v1/panel/proyectos` (ruta inexistente) y
  `GET /api/v1/panel/keys/none` sin token → **401** JSON
  `{"code":"invalid_token","message":"Autenticación requerida"}` en los
  tres (S19: el panel nace protegido; por el proxy llega JSON, no el index
  del SPA).
- [x] 5.3 E2.4 [S]: token válido con UN carácter del 3.er segmento alterado
  → **401** `invalid_token`.
- [x] 5.3 E2.16 ingesta deny-by-default [S]: `POST /api/v1/ingest` sin
  `X-API-Key` y `POST /api/v1/ingest/batch` con `X-API-Key: cualquier-cosa`
  → **401** JSON en ambos (nunca 404 ni 2xx; cadena 1 declarada, C5 la usará).
  (El 401 de estas cadenas lleva el literal `invalid_token` del entry point
  compartido — CD-1; C5 puede revisarlo.)
- [x] 5.4 E2.5 /me con JWT válido [S]: **200**
  `{username, role: "ADMIN", expires_at}` con los valores del token;
  `expires_at` mismo formato ISO-8601 con Z que el login (mismo
  `expires_at=...17:51:51Z` que el token de E2.1).
- [x] 5.5 E2.7 seed en arranque limpio [S]: tras down + volume rm + up:
  `SELECT id, username, role, substr(password_hash,1,30), length(...) FROM
  users;` → **1 fila**: `id=1`, `username=admin` (= env), PHC exacto
  `$argon2id$v=19$m=19456,t=2,p=1`, `role='ADMIN'`; login posterior 200.
- [x] 5.5 E2.8 reinicio no duplica [S/obligatorio]: `podman compose restart
  backend` + espera healthy → **1 fila, mismo id=1**; logs del reinicio:
  `Seed omitido: la tabla users ya tiene 1 fila(s).` (COUNT evitó re-hash, §4.5).
- [x] 5.5 E2.9 tabla no vacía + username inexistente [S]: `.env` con
  `ADMIN_INITIAL_USERNAME=otro-admin-inexistente` + recreación → SIN fila
  nueva (sigue `1|admin`), sin error de arranque (`Seed omitido`), y tras
  RESTAURAR el `.env` la pila vuelve a green con la fila original intacta.
- [x] 5.5 E2.22 semilla respeta el esquema [S]: constraint
  `users_username_unique` EXISTE; `INSERT ... VALUES ('admin', ...)` de
  prueba → `ERROR: duplicate key value violates unique constraint
  "users_username_unique"` (nunca doble fila, COUNT=1 tras el intento);
  Flyway en reinicio: `Schema "public" is up to date. No migration
  necessary.` (E3, cero migraciones); `created_at/updated_at` cubiertos por
  los defaults de V1 (fila seedada sin valores para ellos).
- [x] 5.6 E2.14 fail-fast intacto [L+S]: `git diff` C1→HEAD sobre
  `StartupSecretsValidator.java`, `META-INF/spring.factories`,
  `V1__init.sql`, `WebSocketConfig.java`, `EchoSocketHandler.java`,
  `frontend/`, `docker/`, `.env.example`, `scripts/`, `Application.java` →
  **VACÍO (0 líneas)**.
- [x] 5.6 E2.10 sin password no arranca [S]: `.env` con
  `ADMIN_INITIAL_PASSWORD=` vacío + recreación → contenedor `Exited (1)` en
  bucle esperado (`restart: unless-stopped`, RG-6) y logs: EXACTAMENTE el
  mensaje del validador de C1 — `Arranque del backend abortado (fail-fast
  de secretos): faltan 1 de las 6 variables exigidas... - Falta la variable
  de configuración ADMIN_INITIAL_PASSWORD (...)`. El seed NO reintenta ni
  revalida. RESTAURADO el `.env` → pila green (verificado login 200).
  (Incidente de herramienta resuelto: un `pkill` con patrón coincidente
  con la propia shell abortó el primer restore; re-hecho limpio.)
- [x] 5.7 E2.11 healthcheck sin bucle [S/obligatorio]: los 3 contenedores
  `Up (healthy)` estables a lo largo de ≥3 ciclos de healthcheck (30 s) y
  ~2 min de observación, `RestartCount=0` en backend; healthcheck del
  compose (curl 127.0.0.1 en contenedor) pasa: `/actuator/health` **200**
  en red (y `/actuator/info` **200** en red). Por el proxy el nginx sirve
  el index (NO enruta `/actuator/**`, L9) — IDENTICO al comportamiento
  documentado de C1 ("desde host /actuator/health sirve el SPA"), sin
  regresión.
- [x] 5.7 E2.12 /actuator/env sin contenido [S]: en red
  `curl http://127.0.0.1:8080/actuator/env` → **401** JSON (cadena 3
  denyAll + entry point — nota CD-1/E2.15 de 5.7: el 404 de C1 era el
  mecanismo sin seguridad; el criterio DURO "nunca contenido de
  configuración" se cumple: cuerpo `{"code":"invalid_token",...}`, sin
  ninguna propiedad). Por el proxy: SPA index (no enruta). `exposure
.include: health,info` sigue literal.
- [x] 5.7 E2.13 /ws/logs sigue sirviendo el Echo [S/obligatorio]:
  `python3 scripts/ws_echo_test.py --host localhost --puerto 8080`
  (intocable, re-ejecutado) — SMOKE COMPLETO VERDE: handshake 101, eco
  full-duplex, `Inactividad > 75 s: la conexión SIGUE viva` (E5–E7; /ws/logs
  NO cayó en la cadena 2 — G2/S31).
- [x] 5.7 E2.15 proxy y ruta única [S]: `podman compose config` → un solo
  bloque `ports:` (8080 host); `curl http://localhost:8080/` → **200** index
  placeholder; `/api/v1/nonexistent` con `Accept: application/json` →
  **401** JSON del backend (llega en JSON, no el index del SPA — la ruta no
  cubierta por cadenas 1/2 responde según denyAll de la 3; C1 era 404 JSON
  porque entonces todas las rutas caían "resto" sin seguridad); ruta fuera
  de /api/v1 → 200 index (E9 SPA fallback intacto).
- [x] 5.8 E2.17 CORS con FRONTEND_ORIGIN [S]: con `.env` temporal
  `FRONTEND_ORIGIN=http://127.0.0.1:8080` (passthrough confirmado con
  printenv) y token válido de E2.1:
  `GET /me` con `Origin` autorizado → **200** con
  `Access-Control-Allow-Origin: http://127.0.0.1:8080` (reflejo EXACTO, sin
  `*`), `allowCredentials` fuera (JWT por header);
  preflight `OPTIONS /api/v1/panel/auth/login` → **200 SIN autenticación**
  con `Access-Control-Allow-Methods: GET,POST,PATCH,DELETE,OPTIONS`,
  `Access-Control-Allow-Headers: authorization, content-type`
  (`X-Confirm-*` configurado — reservado C3), `Access-Control-Max-Age: 3600`;
  `Origin: http://otro-no-autorizado` → **403** SIN cabeceras de permiso.
  `.env` RESTAURADO (= valor original del despliegue) y pila recreada green.
- [x] 5.9 higiene y árbol: `git ls-files` SIN `.env`; `git grep
  "JWT_SECRET="` en backend/compose/scripts/README → 0 coincidencias (el
  único match del repo es el placeholder documental de `.env.example`
  heredado de C1, intocable); logs del backend revisados (pasos 5.2–5.9):
  **cero** líneas con `Authorization`/`Bearer`/`argon2id`, sin "Using
  generated security password"; árbol = 13 nuevos `src/main` ([N1]–[N13]) +
  5 tests ([T1]–[T5]) + `application-test.yml` (desvío CD-9 documentado) +
  3 ficheros modificados de C1; `git status` sin ficheros de código
  pendientes (solo los artefactos .atl/sdd/C2/ pendientes de archive).

### Resumen de commits (6)

| Hash | Mensaje |
|---|---|
| 3c60e86 | feat(security): dependencias y configuración base para la seguridad de C2 |
| c051047 | feat(security): tres cadenas SecurityFilterChain, filtro JWT HS256 y CORS mínimo |
| f69830c | feat(auth): login y /me con Argon2id y seed idempotente del admin |
| bdbb258 | test(backend): tests ligeros de C2 — Argon2, JWT, 401 del panel, CORS e higiene de logs |
| 829cc70 | fix(security): CD-8a — inerte el usuario in-memory de Boot excluyendo UserDetailsServiceAutoConfiguration |

### Mapa E2.1–E2.22 (resultado)

| Escenario | Resultado | Evidencia |
|---|---|---|
| E2.1 [S/obligatorio] | VERDE | 200, JWT 3 seg. `{"alg":"HS256"}`, exp−iat=28800s, expires_at `...Z` |
| E2.2 [S/obligatorio] | VERDE | 3×401 literal idéntico `invalid_credentials` |
| E2.3 [S/obligatorio] | VERDE | 3×401 `invalid_token` (me y rutas inexistentes del panel) |
| E2.4 [L]+[S] | VERDE | PanelAuthMvcTest (firma alterada/expirado) + smoke token manipulado 401 |
| E2.5 [S] | VERDE | 200 `{username, role:"ADMIN", expires_at}` desde claims |
| E2.6 [L] | VERDE | MockMvc sin header → 401 JSON (no HTML) |
| E2.7 [S] | VERDE | volumen limpio → 1 fila PHC argon2id + role ADMIN; login OK |
| E2.8 [S/obligatorio] | VERDE | restart → 1 fila mismo id=1; "Seed omitido" |
| E2.9 [S] | VERDE | username alterado → sin fila nueva, sin error; fila original intacta |
| E2.10 [S] | VERDE | fail-fast único del validador (1 de 6) idéntico C1; bucle RG-6 |
| E2.11 [S/obligatorio] | VERDE | healthy estable 3+ ciclos, RestartCount=0, health/info 200 en red |
| E2.12 [S] | VERDE | env en red → 401 JSON sin contenido; por proxy SPA; exposure intacto |
| E2.13 [S/obligatorio] | VERDE | ws_echo_test.py completo + idle 75 s OK |
| E2.14 [L]+[S] | VERDE | diff intocables VACÍO (0 líneas) + E2.10 |
| E2.15 [S] | VERDE | puerto único 8080, index 200, /api/v1 → 401 JSON backend, E9 intacto |
| E2.16 [S] | VERDE | ingest y ingest/batch → 401 con y sin X-API-Key |
| E2.17 [S] | VERDE | reflejo exacto, preflight 200 sin auth, origen extraño sin permiso |
| E2.18 [L] | VERDE | CorsConfigTest: origen vacío ⇒ cero configuraciones |
| E2.19 [L] | VERDE | ListAppender: cero Authorization/Bearer/JWT en logs (test explícito) |
| E2.20 [L] | VERDE | PHC `$argon2id$v=19$m=19456,t=2,p=1`, salts distintos, matches (BC runtime OK) |
| E2.21 [L] | VERDE | roundtrip HS256, 4 claims exactos, TTL 8h/1h, alterado+expirado rechazados |
| E2.22 [S] | VERDE | constraint existe, duplicado rechazado, Schema up to date |

`mvn test` (contenedor maven:3.9-eclipse-temurin-21): **Tests run: 25,
Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** (Argon2PasswordTest 4 ·
LoginCredentialsTest 4 · PanelAuthMvcTest 6 · TokenServiceTest 7 ·
CorsConfigTest 4).

## Desviaciones del tasks/design (todas registradas con su causa)

1. **`application-test.yml` en `src/test/resources`** (solución prevista
   CD-9, no anticipable antes): el `StartupSecretsValidator` de C1
   (intocable, S29) corre en cada contexto Spring y exigía las 6 variables
   en los slices. `src/test/java` sigue siendo EXACTAMENTE las 5 clases.
2. **Commit de fix `829cc70`** (CD-8a): Boot imprime "Using generated
   security password" incluso con las 3 cadenas propias — evidencia en los
   logs del slice durante F3. Resuelto en `application.yml`
   (`spring.autoconfigure.exclude` de `UserDetailsServiceAutoConfiguration`),
   fichero editable de C2; sin beans de seguridad añadidos ni
   `UserDetailsService` propio (S3 al literal).
3. **`RestAuthenticationEntryPoint` implementa también
   `AccessDeniedHandler`** (corrección de compilación de 2.5): CD-1 pide "el
   mismo entry point como accessDeniedHandler" y el DSL exige el tipo;
   cuerpos idénticos en ambos caminos (nunca 403 HTML, CD-4).
4. **Hardening dummy-hash del login**: generado con el propio encoder en el
   constructor en vez de un literal estático (garantiza PHC parseable —
   mismo coste, S17).
5. **Log del AdminSeeder** reporta `{n}` filas ya presentes cuando omite
   (sin valores sensibles) — no cambia ninguno de los literales exigidos
   ("Administrador inicial sembrado..." / "Seed omitido...").
6. **Entorno**: maven solo ejecutable en contenedor (no hay JDK en host);
   se requería instalar `podman-compose 1.6.0` (pip --user) porque el
   sistema no tenía proveedor de compose. La pila heredada de C1 carecía de
   labels de compose — se eliminaron (los datos viven en el volumen) y F4
   arrancó con `down` + `volume rm` (permiso explícito "solo en local" del
   paso 5.5) para el estado limpio de E2.7.

## Riesgos nuevos

- `podman-compose 1.6.0` en lugar del proveedor usado en C1: el flujo
  canónico de up/down/build funciona, pero los contenedores de C1
  "huérfanos" (sin labels) conviven mal con un `up` nuevo (duplicados con
  sufijo `_1`, colisión de puerto). Para cortes futuros (C3+): o bien
  documentar el estado "contenedores gestionados por podman-compose" en el
  corte, o eliminar el set huérfano antes de `up`. Sin impacto en código.
- La primera fila del seed se generó durante la convivencia de dos
  postgres sobre el mismo volumen; el estado actual fue re-sembrado desde
  un volumen LIMPIO y re-verificado completo (ningún resto del estado
  dudoso sobrevive: la fila actual es de E2.7 limpio con login OK).

## Estado final

- status: done
- working tree: sin ficheros de código pendientes de commit
  (los artefactos .atl/sdd/C2/{spec,design,tasks,apply-progress}.md quedan
  sin versionar hasta la fase sdd-archive, igual que la C1).

## Re-apply Q24 CORS (2026-10-21) — status: fixed

### Causa raíz (SIN defecto del wiring de código; defecto de ENTORNO en dos capas)

El allowlist de CORS en runtime quedó VACÍO porque el contenedor
`project_log_backend_1` del cierre QA se CREÓ con `FRONTEND_ORIGIN=` vacío
(evidencia: `podman inspect .Config.Env` congelado → `FRONTEND_ORIGIN=`;
es el estado «sin variable» de S26: config null ⇒ preflight rechazado
`Invalid CORS request` 403 sin cabeceras, simple request con Origin → pasa sin ACAO).
El wiring de código (`CorsConfig` bean + `.cors()` con `CorsConfigurationSource`
en la cadena 2 + mapping `${FRONTEND_ORIGIN:}` en application.yml) es CORRECTO
y quedó demostrado al recrear el contenedor con el env correcto (Q24 → VERDE
sin tocar main). Dos capas del defecto de entorno:

1. **El contenedor final del cierre QA se creó con la variable vacía.**
   Mecanismo ya documentado por el propio QA en qa-log.txt (COMANDO 2
   1ª ejecución, hallazgo (a)): el env del proceso que invoca
   podman-compose PRECEDE al `.env` en la interpolación `${VAR}` de
   compose.yaml. Tras el flujo Q26 (pila recomuesta SIN la variable), la
   restauración final (Q27, contenedor creado 2026-10-21 20:14:54) leyó
   `FRONTEND_ORIGIN` vacío del proceso — el `.env` estaba restaurado en el
   disco (mtime 20:14:33) pero el proceso de compose ya no lo interpoló.
   `restart` NO re-lee env: el estado vacío persistió hasta el recreate.
2. **El `.env` de F1 tenía el valor en forma frágil**: 
   `FRONTEND_ORIGIN=http://<ip-vm>:8080  # solo para desarrollo/CORS` —
   placeholder `<ip-vm>` SIN rellenar más comentario en línea. (a)
   Compose lo interpola bien, pero un `source .env` en bash trunca el
   valor (bash interpreta `<ip-vm>` como redirección: «No existe el fichero
   o el directorio» ⇒ el test lee `http://`), lo que reproduce EXACTAMENTE
   el patrón observado en el qa (preflight y /me con Origin → 403
   «Invalid CORS request», PASS de Q25/Q26). (b) Mismo mecanismo en la 1ª
   ejecución QA documentada. La reproducción del qa-report usaba el valor
   truncado del shell, no el del contenedor.

### Por qué CorsConfigTest NO detectó el wiring «roto» (y por qué el wiring no estaba roto)

`CorsConfigTest` NO monta el wiring de runtime: instancia `new CorsConfig("<literal>")`
directamente sobre el constructor (sin contexto Spring, sin el mapping
application.yml `${FRONTEND_ORIGIN:}`, sin `SecurityFilterChain`, sin
SecurityFilterChain/container env) y assertiona las propiedades del bean.
Con el bean aislado y un literal correcto, el test es verde aunque el
contenedor no reciba la variable — el eslabón roto (env del contenedor →
application.yml → @Value) vive FUERA de la unidad probada, y es el eslabón
que E2.17 [S] cubre en red (printenv + sondeo por proxy). Señal para
verify: no reescribir el test; la cobertura de extremo a extremo es
E2.17/Q24, como de hecho clasifica el plan.

### Cambio realizado

- **backend/src/main: CERO cambios** (dif vs HEAD 829cc70 en
  `backend/src/main`/`scripts`/`docs` = vacío). No hay commit de código:
  el fix es de ENTORNO (recompose del contenedor con env limpio), y no se
  justifica commit vacío `fix(security): …`; quien hace verify decidirá si
  quiere fijar la lección (guard del smoke script contra la precedencia
  proceso-vs-.env) como artefacto QA test-side.
- **`.env` de F1 corregido de forma (NO commiteado; backup del original en
  `/tmp/project_log_env_backup_q24`)**: línea del valor con placeholder
  `<ip-vm>` sin rellenar + comentario en línea →
  `FRONTEND_ORIGIN=http://192.168.42.101:8080` (IP real de la VM, valor
  sin `<>` ni comentario en línea; comentario movido a su propia línea).
  El valor del contenedor ahora coincide EXACTO con lo que el test lee por
  `source .env` (sin truncado bash).
- Recomposición (NO restart): `podman-compose up -d --no-deps
  --force-recreate backend` tres veces (antes/corregida/Q26-restore),
  SIEMPRE desde un shell con las 18 variables de compose UNSET, para que
  la interpolación venga del `.env` y no de restos del env del proceso
  (disciplina anti-veneno de la capa 1). Nota: podman-compose 1.6.0
  recreó también el frontend (sin impacto; quedó healthy).

### Re-ejecución mínima — resultados (todos VERDE)

- **Q24/E2.17 (3 sub-casos, por proxy 8080, Origin = `printenv
  FRONTEND_ORIGIN` del contenedor = `http://192.168.42.101:8080`)**:
  - preflight `OPTIONS /api/v1/panel/auth/login` + AC-Request-Method/Headers →
    **HTTP 200** (sin autenticación, `Content-Length: 0`) CON
    `Access-Control-Allow-Origin: http://192.168.42.101:8080` (reflejo
    EXACTO, nunca `*`), `Allow-Methods: GET,POST,PATCH,DELETE,OPTIONS`,
    `Allow-Headers: authorization, content-type`, `Max-Age: 3600`, SIN
    `Allow-Credentials`. **Divergencia literal para verify** (misma clase que
    Q18/Q19, NO nueva): el qa-plan escribe «204» pero Spring
    (DefaultCorsProcessor/CorsFilter) ha rendido SIEMPRE **200** — el
    E2.17 del propio apply quedó «preflight **200** sin auth» (línea 236
    de este fichero) y su fila VERDE (línea 282). El script QA
    asertará 204 ⇒ falla literal test-side; y el ACAH refleja los headers
    PEDIDOS (literal `X-Confirm-*` no sale reflejado: Spring responde con
    los request-headers permitidos; el apply ya observó lo mismo en VERDE).
  - `GET /me` con `Bearer` válido + `Origin` → **200** con ACAO reflejado
    exacto (ANTES: 403 «Invalid CORS request»).
  - `GET /me` con `Bearer`, sin `Origin` → **200** (ANTES: 200; intacto).
- **Q25 (origen `http://evil.example`)**: preflight → **403 «Invalid CORS
  request» SIN cabeceras de permiso**; `GET /me` con ese Origin → **403**
  sin ACAO (el origen nunca se refleja). PASS, sin regresión (la condición
  FAIL es ver ACAO sobre el no listado).
- **Q26 (`.env` SIN la variable; backup+restore, recomposición con env
  limpio)**: `FRONTEND_ORIGIN` vacía en el contenedor (estado S26); el
  preflight con `Origin: cualquiera` → 403 y el `GET /me` con Origin → 200
  AMBOS SIN cabeceras `Access-Control-Allow/Expose/Max-Age` (cero
  permisos); login same-origin sin Origin → 401 esperado. PASS, sin
  regresión. `.env` restaurado tras el caso (md5 = pre-mutación) y pila
  recompuesta con valor correcto.
- **E2.11 (healthcheck green, sin bucle)**: los 3 contenedores
  `health=healthy`, `RestartCount=0`, `FailingStreak=0` (postgres/backend/
  frontend; backend/frontend Up tras el recreate final).
- **E2.13 (actuator env sin contenido de config)**: por el proxy 8080 →
  **200 index del SPA (322 B)** y `grep` de secretos/config SIN match
  (`leak=NO` — el estado routing heredado de C1 documentado en
  qa-report Q18/Q19, sin regresión); en-red
  `podman exec … curl /actuator/env` → **401**.
- **E2.14 (WS echo + idle)** — `python3 -I scripts/ws_echo_test.py` →
  exit 0: «handshake 101 · eco full-duplex · inactividad > 75 s — pila WS
  verde» (cierre limpio 0x8 y la conexión sobrevive >75 s).
- **E2.18 (CorsConfigTest, solo esa clase)** — `mvn test
  -Dtest=CorsConfigTest` (Maven 3.9.9 + Temurin 21.0.12.1 portables): 
  `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`.
  El test sigue PASS (el wiring del bean era correcto y no ha cambiado
  nada: sin literales que rehacer, sin cambios).

### Estado final de la re-aplicación

- status: fixed (Q24 vuelve a verde en runtime; el conjunto re-ejecutado:
  8/8 VERDE — Q24a/b/c, Q25, Q26, E2.11, E2.13, E2.14, E2.18).
- working tree: SIN cambios de código main (HEAD = 829cc70, ni una línea
  modificada); sin commits en este re-apply (no procede: cero diff).
- Pila F1 al cierre: 3 contenedores healthy, `.env` con
  `FRONTEND_ORIGIN=http://192.168.42.101:8080` (estado F1 corregido de
  forma; sin marcas QA; valor del contenedor verificado con printenv).