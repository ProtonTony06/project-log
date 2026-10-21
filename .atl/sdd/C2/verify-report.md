---
change: "C2"
artifact: verify-report
last_updated: 2026-10-21
status: done
resultado: PASS-with-notes
---

# Verify report — C2 · Seguridad base + auth del panel

> Fase sdd-verify. Fuentes: `spec.md` (S1–S32, E2.1–E2.22), `tasks.md`
> (Done), `apply-progress.md` (status done; re-apply Q24-fixed), `qa-report.md`
> (22/27; FAIL Q5/Q18/Q19/Q24; BLOQUEADO Q21), `qa-plan.md` (§2/§5) y
> `tests-written.md`. Re-capturas propias del verify: ejecución real de
> `mvn test` (contenedor `maven:3.9-eclipse-temurin-21`), doble lectura de
> healthchecks podman (t1/t2, >70 s aparte), sondeos en-red (`podman exec …
> curl`) y por proxy (8080), smoke curl enmascarado y diffs git
> `fa42751…HEAD`. NINGÚN fichero de código modificado salvo
> `LogHygieneTest.java` (fix de Q5, test-side); sin commits.

**Veredicto: PASS-with-notes** — los 22 escenarios E2.1–E2.22 confirmados;
los 4 hallazgos de QA quedan cerrados (Q24 FAIL-real resuelto en re-apply con
entorno, Q5 corregido test-side por verify, Q18/Q19 re-capturados con técnica
y resueltos como divergencia plan-vs-routing, Q21 RESUELTO diff-vacío); las
notas son no-bloqueantes (§8).

---

## 1. Veredicto por escenario E2.1–E2.22

| Escenario | Veredicto | Evidencia (apply · QA · verify) |
|---|---|---|
| E2.1 login correcto [S] | **confirmado** | apply 5.2 (200, JWT 3 seg., `alg:HS256`, exp−iat=28800) · QA Q8 · verify re-ejecuta: 200, claves `{token,username,expires_at}`, claims `{exp,iat,role,sub}` (conjunto cerrado), exp−iat=28800 s, expires `…T02:39:37Z` |
| E2.2 login 401 ×1 literal [S] | **confirmado** | apply 5.2 · QA Q9 · verify: password incorrecto → 401 `invalid_credentials`; usuario inexistente y cuerpo malformado por LoginCredentialsTest (Q7) + QA — misma constante del controller |
| E2.3 panel 401 sin JWT [S] | **confirmado** | apply 5.3 · QA Q10 · verify: `/me` sin Authorization → 401 `invalid_token` (JSON) |
| E2.4 firma alterada/expirado [L+S] | **confirmado** | PanelAuthMvcTest + QA Q4/Q12 (firma alterada, payload alterado, otro secreto F5) · apply 5.3 smoke token manipulado → 401 |
| E2.5 /me con JWT [S] | **confirmado** | apply 5.4 · QA Q11 · verify: 200 `{username:'admin', role:'ADMIN', expires_at:'…Z'}` desde claims, sin BBDD |
| E2.6 /me sin JWT [L] | **confirmado** | PanelAuthMvcTest (401 JSON, no HTML, Content-Type JSON) · QA Q3 |
| E2.7 seed arranque limpio [S] | **confirmado** | apply 5.5 (volumen limpio → 1 fila, PHC `$argon2id$v=19$m=19456,t=2,p=1$`, role='ADMIN', login OK) · QA Q13 (BD-A) |
| E2.8 reinicio no duplica [S] | **confirmado** | apply 5.5 (restart → 1 fila, mismo id=1, log "Seed omitido") · QA Q14 (recomposición full, fila bit-a-bit idéntica = no re-hashing) |
| E2.9 tabla no vacía + usern. inexistente → no-acción [S] | **confirmado** | apply 5.5 · QA Q15 (G4 completo: fila original intacta, login original 200, login-alt 401) |
| E2.10 sin password no arranca [S] | **confirmado** | apply 5.6 (`Exited (1)`, mensaje único del validador: "faltan 1 de las 6 variables exigidas… ADMIN_INITIAL_PASSWORD") · QA Q16 · seed no reintenta (S23) |
| E2.11 healthcheck sin bucle [S] | **confirmado (re-captura verify)** | verify Q18: t1 y t2 (>70 s = ≥2 healthchecks) los 3 contenedores `health=healthy, FailingStreak=0, RestartCount=0`; en-red `/actuator/health` → 200 `{"status":"UP"}` y `/actuator/info` → 200. Véase §4: por proxy `/actuator/*` NO se enruta (SPA) — aserción por-proxy era incompatible con el routing C1, criterio DURO E1/RG-6 cumple |
| E2.12 /actuator/env sin contenido [S] | **confirmado con nota (re-captura verify)** | verify Q19: en-red → **401** JSON del entry point (nunca contenido de configuración); por proxy → 200 index SPA (322 B) y `grep` anti-leak (JWT_SECRET/FRONTEND_ORIGIN/ADMIN_INITIAL/datasource/logging) sin match. Nota: el literal "404" del spec describe el mecanismo de C1 (sin exposición ⇒ 404 de actuator); con la cadena 3 el rechazo se manifiesta 401 — criterio DURO E10 "nunca contenido" cumple (tasks 5.7 ya lo registró; qa-plan Q19 admite 401/404) |
| E2.13 /ws/logs Echo + idle 75 s [S] | **confirmado** | apply 5.7 + re-apply: `python3 -I scripts/ws_echo_test.py` → exit 0, handshake 101, eco full-duplex, sobrevive >75 s (E5–E7; G2/S31) · QA Q20 |
| E2.14 fail-fast + intocables [L+S] | **confirmado (`Q21 RESUELTO`)** | QA Q17 a/b/c PASS (mensaje agregado del validador C1, sin olor a seeder; secreto corto rechazado; secreto válido green) · **verify Q21**: `git diff fa42751…HEAD` sobre `StartupSecretsValidator.java` + `META-INF/spring.factories` = **0 líneas** y `sha256sum` actual = `df1f031a…`/`d1db6b0f…` (los mismos valores capturados y congelados por QA — son la línea base C1) · diff del resto de intocables (§5) = vacío |
| E2.15 proxy y ruta única [S] | **confirmado** | apply 5.7 · QA Q22 · verify: único `ports:` host (8080→80, FRONTEND_BIND opcional), index 200 por proxy, `/api/v1/**` llega al backend en JSON (401, no index SPA), ruta fuera de matchers → respuesta del denyAll sin contenido de negocio |
| E2.16 ingesta deny-by-default [S] | **confirmado** | apply 5.3 · QA Q23 (y preflight a ingesta SIN cabeceras CORS — S26) · verify: `/ingest` → 401, `/ingest/batch` → 401, con y sin `X-API-Key`, nunca 404/2xx |
| E2.17 CORS con FRONTEND_ORIGIN [S] | **confirmado (re-captura verify)** | re-apply Q24-fixed (3 sub-casos VERDE tras recreate con env correcto) · verify: preflight `OPTIONS /auth/login` → **200** sin auth con ACAO `http://192.168.42.101:8080` exacto (nunca `*`), `Allow-Methods: GET,POST,PATCH,DELETE,OPTIONS`, `Allow-Headers: authorization, content-type` (+ `X-Confirm-*` configurado, reservado C3), `Max-Age: 3600`, SIN `Allow-Credentials`; `/me` con Bearer+Origin → 200 ACAO exacto; Q25 evil.example → 403 sin ACAO. Divergencia literal 204-vs-200: §4 |
| E2.18 CORS cerrado sin variable [L] | **confirmado** | CorsConfigTest (origen vacío ⇒ 0 configuraciones) = 4/4 verde en `mvn test` del verify · QA Q6/Q26 (`recompose` sin error, cero cabeceras `Access-Control-*`, same-origin operativo) |
| E2.19 token nunca en logs [L] | **confirmado** | **Q5 corregido por verify** (fix test-side, §3): `LogHygieneTest` verde; ninguna línea de los 3 loggers contiene `Authorization` / `Bearer ` / tokens / fragmentos / contraseña de test. Estructura: cero llamadas a logger en filtro/entry point/TokenService (grep verify). Greps de pila de apply/QA Q8/Q13 sin `Authorization`/`Bearer`/`argon2id` ni "Using generated security password" |
| E2.20 PHC y Argon2id [L] | **confirmado** | Argon2PasswordTest (PHC exacto, salts distintos, matches true/false, BC en runtime OK — issue #8842 desactivado) · QA Q1/Q2 |
| E2.21 TokenService roundtrip [L] | **confirmado** | TokenServiceTest (claims exactos {sub,role,iat,exp} y SOLO esos, TTL 8 h y alterno, exp=iat+TTL, firma alterada y expirado rechazados) · QA Q3/Q4 · verify smoke: claims reales `{exp,iat,role,sub}` sin extras |
| E2.22 semilla respeta esquema [S] | **confirmado** | apply 5.5 (constraint `users_username_unique` existe; duplicado rechazado en pila; Flyway "Schema up to date" en frío) · QA Q27 (information_schema + otras tablas vacías, S24) |

**Resumen: 22 confirmados · 0 pendientes · 0 no aplicables** (2 con nota de
divergencia literal resuelta: E2.11/E2.12 por-proxy vs routing C1 — §4).

## 2. Cobertura S1–S32

Compacta (referencias: spec §1 → fuentes de verificación):

- **S1–S5** (3 cadenas, deny-by-default, login exacto, cadena-3 permitAll,
  filtro/entry point): estructura verficada en revisión de código del verify
  (qa-plan §5.1 la reserva a verify): `SecurityConfig` con EXACTAMENTE 3
  beans `SecurityFilterChain` `@Order(1)/(2)/(3)`, matchers literales,
  `permitAll` del login con `HttpMethod.POST` + ruta exacta,
  `/ws/logs`+/actuator en la cadena 3, `denyAll` en 1 y 3, CERO
  `httpBasic(`/`formLogin(`, STATELESS + CSRF disabled (común a las 3
  cadenas). Comportamiento: E2.3/E2.4/E2.6/E2.15/E2.16.
- **S6–S9** (Argon2 OWASP 2024, bcprov, PHC, plan B no activado):
  `PasswordConfig` = `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)`, sin
  defaults v5_8/v5_5; PHC en BBDD verificado en pila (E2.7/E2.22); `matches`
  contra PHC autodescrito (E2.20). Plan B S9 no activado (no se necesitó).
- **S10–S16** (jjwt 0.13.0, HS256, claims cerrados, TTL 8 h, sin skew, sin
  logout, sin token en logs): E2.20/E2.21 + estructura (imports solo
  `io.jsonwebtoken` raíz, `SecretKeySpec(getBytes(UTF_8))`, sin
  `clockSkewSeconds`, sin logger en TokenService) + E2.19.
- **S17–S20** (endpoints/literales Schema, /me sin BBDD, panel nace
  protegido, sin logout/refresh/roles): E2.1–E2.5, E2.15/Q22 rutas futuras
  401; `MeController` sin `JdbcTemplate` (grep); constante única
  `invalid_credentials`; `expires_at` formatter UTC
  `yyyy-MM-dd'T'HH:mm:ss'Z'` compartido (código del verify).
- **S21–S24** (seed): E2.7–E2.10, E2.22; COUNT tabla-vacía (G4),
  `ON CONFLICT ON CONSTRAINT users_username_unique DO NOTHING`, `role`
  explícito, `db/migration/` = solo `V1__init.sql`.
- **S25–S26** (CORS mínimo, cerrado por defecto): E2.17/E2.18; `CorsConfig`
  con `@Value("${projectlog.security.frontend-origin:}")`, `setAllowedOrigins(List.of(origin))`
  exacto (grep: cero `*` como origen, cero `allowedOriginPatterns`),
  `allowCredentials(false)`, `maxAge(3600)`, registro sobre `"/**"` acotado
  por el matcher de la cadena 2; sin `cors()` en cadena 1.
- **S27–S28** (POM y colocación): `spring-boot-starter-security` sin versión
  (BOM), `spring-boot-starter-test` scope test; cero rangos/RELEASE/LATEST
  en `<version>` (grep); paquetes `config`/`auth` 1:1 con el árbol del
  design §1.1 (git log fa42751..HEAD: 13 nuevos en src/main + 5 test +
  application-test.yml + pom/yml/compose — nada fuera).
- **S29–S32** (intocables): `git diff fa42751…HEAD` sobre la lista completa
  de §0.3 (validador, spring.factories, V1, WebSocketConfig, EchoSocketHandler,
  nginx.conf, Dockerfiles, .env.example, Application.java,
  ws_echo_test.py) = **0 líneas** (E2.14).

## 3. Cierre de los FAIL de QA

- **Q24 (FAIL-real → RESUELTO en re-apply, 2026-10-21).** Root-cause de
  ENTORNO en dos capas, sin defecto de wiring: (1) el contenedor del cierre
  QA se creó con `FRONTEND_ORIGIN=` vacío — la precedencia
  env-del-proceso > `.env` en la interpolación de compose (mecanismo que el
  propio QA documentó en COMANDO 2) dejó el allowlist vacío tras el
  restore-restore del Q26; `restart` no re-lee env. (2) El `.env` de F1
  tenía el valor en forma frágil (`http://<ip-vm>:8080  # comentario`) que
  `source .env` en bash TRUNCA (bash lee `<ip-vm>` como redirección). El
  re-apply recreó el contenedor con las var UNSET y el `.env` con el valor
  real con forma limpia → Q24a/b/c, Q25, Q26, E2.11, E2.13, E2.14, E2.18 =
  8/8 VERDE sin tocar `backend/src/main`, sin hash y sin commit. Código OK.
  **Lección adoptada como patrón**: disciplina anti-veneno de env en
  mutaciones (recomposición siempre con el env del proceso UNSET y `.env`
  en forma robusta — sin placeholders `<…>`, sin comentarios en línea) —
  véase acción heredada §9.
- **Q5 (FAIL test-side → CORREGIDO por verify).** El aserto antivacío del
  ListAppender presumía eventos; las 3 clases, correctamente, no loguean
  nada (precisamente lo que S16 pide). Fix: eliminado el aserto
  `isNotEmpty()` de `LogHygieneTest.java` y añadida la contraseña de test
  (`qa-c2-contra-f3`) a los literales prohibidos — la aserción queda como el
  plan pide (NINGUNA línea de los 3 loggers contiene
  `Authorization`/`Bearer `/token/fragmentos/contraseña, lista vacía ⇒
  vacuamente verde, sin fabricar eventos). Ejecutado:
  `-Dtest=LogHygieneTest` verde (1/1) y **`mvn test` completo verde: 26
  tests, 0 fallos, 0 errores** (Argon2 4 · Login 4 · PanelAuthMvc 6 ·
  TokenService 7 · CorsConfig 4 · LogHygiene 1).
- **Q18/Q19 (FAIL incompatibilidad plan-vs-routing → RE-CAPTURADOS por
  verify, RESUELTOS).** Técnica correcta usada (ajuste del corredor, SIN
  reescritura de tests-writte ni del script):
  `podman inspect --format '{{.State.Health.Status}}'` para estabilidad y
  `RestartCount`/`FailingStreak`; sondeos `/actuator/*` EN-RED (el routing
  del nginx C1 solo enruta `/api/v1/**` + `/ws/logs`; `podman ps` vacío del
  script era una ventana post-`up`). Lectura 1 (t1) y lectura 2 (t2, >70 s =
  ≥2 healthchecks): 3× `healthy`, FailingStreak=0, RestartCount=0 en ambas —
  estabilidad confirmada. En-red: health 200 `{"status":"UP"}`, info 200,
  env 401 sin contenido de config (leak=NO también por proxy). Registado
  como aclaración: las aserciones de `/actuator/*` viven EN-RED; por el
  proxy `/actuator/**` = index del SPA (comportamiento documentado de C1,
  sin regresión).
- **Q21 (BLOQUEADO → RESUELTO por verify).** La referencia existía como
  captura congelada (qa-report: `df1f031a…` / `d1db6b0f…`). Dos pruebas:
  (a) `sha256sum` actual de ambos ficheros = EXACTAMENTE esas capturas ⇒ el
  código observado por QA es el mismo que sigue en HEAD; (b) prueba más
  fuerte que el hash: `git diff fa42751 (último commit C1) → HEAD` sobre
  ambos = **0 líneas** ⇒ intocables desde pre-C2 con javadoc incluido (S29).
  Veredicto Q21: **PASS**. (La comparación contra commits C1 anteriores
  — `521582b` — arroja el mismo diff vacío.)

## 4. Divergencias literales evaluadas

- **Preflight 200 vs 204 (qa-plan Q24/§2.7).** El spec E2.17 pide solo "2xx
  sin autenticación" y el qa-plan escribe «204»; Spring
  (`DefaultCorsProcessor`/`CorsFilter`, servlet 6 en Boot 3.5) ha rendido
  200 con `Content-Length: 0` (comportamiento estable del producto y ya
  observado VERDE por el apply). Es un literal TEST-SIDE del plan: **sí se
  sugiere corregir el script para corte futuros** (asertar `20[46]`, o
  `2xx` + cabeceras) — NO ejecutado aquí (no se toca el QA script).
  El qa-plan §7 permite este trato (XFAIL con nota / decisión de spec); en
  este corte el caso quedó VERDE por el re-apply con el literal del spec
  (2xx), así que no afecta al veredicto.
- **ACAH refleja los headers pedidos vs `X-Confirm-*`.** Spring procesa
  `Access-Control-Allow-Headers` respondiendo con los request-headers
  permitidos (en la prueba: `authorization, content-type`), no con el
  patrón configurado literal; `X-Confirm-*` ESTÁ configurado en
  `CorsConfig` (reservado C3/P1). Decisión: **coherente con el
  funcionamiento estándar de CORS** (lo relevante es que un preflight con
  `Authorization`/`Content-Type` sale permitido, y `X-Confirm-*` lo estará
  en C3). No exige eco documental; nota de redacción para la próxima
  validación del qa-plan si se reutiliza el caso.
- **E2.15 /actuator por proxy → index SPA.** Coherente con el routing C1
  (TRD §9/nginx solo `/api/v1/**` + `/ws/logs`): C1 ya documentó ese
  comportamiento (desde host, `/actuator/health` = SPA). El literal "por el
  proxy" del qa-plan Q18 era incompatible de fábrica con ese routing;
  resuelto con sondeos en-red registrados en §1 (E2.11/E2.12). Sin
  regresión: `/api/v1/**` sigue llegando al backend en JSON (401 real).

## 5. Spot-checks de contratos

- **login 200 `POST /api/v1/panel/auth/login`**: 200 con claves EXACTAS
  `{token, username, expires_at}`; 401 con
  `{"code":"invalid_credentials","message":"Credenciales inválidas"}` —
  constante única `CODE_INVALID_CREDENTIALS` en las 3 ramas + handler de
  `HttpMessageNotReadableException` → 401 (no 400). ✔
- **/me 200 `{username, role:"ADMIN", expires_at}`** desde claims
  (`principal.role()`, formatter compartido
  `yyyy-MM-dd'T'HH:mm:ss'Z'` UTC), sin BBDD. ✔
- **claims §5**: header del token decodificado `alg: HS256`; payload
  `{exp, iat, role, sub}` — conjunto cerrado sin extras (S12). ✔
- **TTL 8 h**: `exp−iat = 28800 s`; `application.yml`
  `${JWT_TTL_HOURS:8}`. ✔
- **Argon2 PHC**: `$argon2id$v=19$m=19456,t=2,p=1$` en BBDD y en tests;
  paramétros `(16,32,1,19456,2)` OWASP 2024 en el código. ✔
- **bcprov 1.86 + jjwt 0.13.0 sin rangos**: pom con versiones exactas y
  scopes literales (BC runtime, impl/jackson runtime, api compile, test
  scope BOM); grep de rangos/RELEASE/LATEST/SNAPSHOT en `<version>`: 0. ✔
- **Seed §6.3**: `COUNT(*) FROM users` tabla-vacía (G4), INSERT con
  `role='ADMIN'` explícito + `ON CONFLICT ON CONSTRAINT
  users_username_unique DO NOTHING`; `db/migration/` = solo V1 (S24, g4
  no-acción verificado E2.9/Q15). ✔
- **CORS**: `FRONTEND_ORIGIN` con passthrough en compose
  (`FRONTEND_ORIGIN: ${FRONTEND_ORIGIN}`), sin wildcard, cerrado sin la
  var (E2.18), `allowCredentials(false)`, maxAge 3600, headers
  `Authorization/Content-Type/X-Confirm-*`, sin CORS en la cadena 1
  (preflight a ingesta sin cabeceras — QA Q23). ✔
- **3 cadenas con matchers exactos + entry point compartido**; `/ws/logs`
  permitAll (cadena 3), `/actuator/health,/info` permitAll (en-red 200),
  `denyAll` en 1 y 3; login público solo método+ruta exactos (GET sobre la
  ruta → 401, QA Q22). ✔
- **Token no en logs**: cero llamadas a logger en
  JwtAuthFilter/RestAuthenticationEntryPoint/TokenService (estructura) +
  E2.19 corregido y verde + greps de pila. ✔
- **Diff vacío de intangibles de C1**: validador + spring.factories +
  V1 + WS config + nginx + Dockerfiles + .env.example + Application +
  ws_echo_test.py — `git diff fa42751…HEAD` = 0 líneas. ✔
- `.env` NO versionado (`git ls-files` sin `.env`); `git grep "JWT_SECRET="`
  sin valores en ficheros versionados. ✔

## 6. Desviaciones y eco documental

Regla: ningún doc valido se modifica sin nueva validación. Evaluación por
desviación:

1. **Entry point con `AccessDeniedHandler` (implements).** Coherente con el
   design: CD-1 pide "el mismo entry point como `accessDeniedHandler` —
   defensa en profundidad que garantiza nunca 403 HTML" (design.md líneas
   ~157–162). El desvío era solo la mecánica Java (implementar la interfaz
   para que el DSL lo acepte), registrado en apply-progress §Desviaciones
   (3). **Eco: NO** (el design ya lo decía; nada que trasladar).
2. **`application-test.yml` en `src/test/resources`.** Solución EXPLÍCITAMENTE
   prevista por CD-9 ("si un placeholder resoluble chocara, la solución
   prevista es un application-test.yml mínimo, decidido al escribir el
   test"). El validador de C1 (intocable) corre en cada slice. **Eco: NO**
   (design CD-9; desvío registrado en apply y qa).
3. **Fix `829cc70` (CD-8a: usuario in-memory inerte).** El design S3/CD-8a
   exige inercia; el mecanismo
   `spring.autoconfigure.exclude=UserDetailsServiceAutoConfiguration` en
   `application.yml` (fichero editable de C2) cumple el objetivo SIN beans
   ni `UserDetailsService` propio. Verificado en pila real (sin "Using
   generated security password"). **Eco: NO** — es implementación de CD-8a,
   no cambio de diseño. Si la próxima validación de docs toca el TRD, la
   nota útil (texto sugerido, una línea en §6.3/TRD o el ADR correspondiente):
   «La inercia del usuario in-memory de Boot se asegura excluyendo
   UserDetailsServiceAutoConfiguration (Boot imprime "Using generated
   security password" incluso con chains propias)».
4. **Corrección del `.env` de F1 NO commiteada.** Decisión: registrar en el
   acta del corte (ya está en apply-progress/Q24) **y** en las acciones
   heredadas de C9/C10: el `.env` NO es versionado (`.gitignore` de C1
   intacto), así que "no commit" es el estado natural — lo que hay que
   hacer es dejar el patrón documentado: en C9/C10 el `.env` real de la VM
   debe escribirse en forma robusta (valor real, sin placeholders `<…>` y
   sin comentarios en línea) y los scripts QA futuros deben recomponer con
   el env del proceso UNSET. Backup del original: `/tmp/project_log_env_backup_q24`.

Ninguna desviación toca `/docs`; nada que revalidar en esta fase. Los
textos sugeridos (3) solo si la próxima validación toca esos puntos.

## 7. Laguna S15 (rotación de JWT_SECRET)

El plan la señaló como sin escenario E2.x (§5.4). Evaluación:

- La propiedad S15 ("cambiar `JWT_SECRET` invalida los tokens emitidos con
  el secreto anterior") se cumple **por construcción y está demostrada de
  forma indirecta**: `TokenService` deriva la clave del valor del env EN el
  arranque (`SecretKeySpec(@Value JWT_SECRET)`) y QA Q12c demuestra que un
  token firmado con OTRO secreto (`JWT_SECRET_ALT`) es rechazado — mismo
  mecanismo que una rotación con reinicio. Sin margen de skew (S14) ni
  cacheo de claves.
- La pieza que falta es solo el E2.x de humo de rotación en la pila
  (emitir → reiniciar con nuevo secreto → reusar → 401).

**Decisión recomendada (no bloqueante)**: registrarla como decisión de
spec pendiente para la **siguiente ronda de validación de docs** (v2/C9),
con esta forma: "Añadir a la siguiente validación un escenario de
rotación de JWT_SECRET (suficiente: reinicio con nuevo secreto y reuso de
token → 401 `invalid_token`) o documentar en TRD/Schema que S15 se
satisface por construcción (Q12c-like)". Alternativa operativa ligera:
incluir ese único caso en el smoke canónico de C9. No retrasa el archive
de C2: la propiedad no puede romperse sin cambiar `TokenService`
(intocable en comportamiento contractual) o el mecanismo de arranque.

## 8. Hallazgos

- **WARNING — Cuerpo 401 del entry point codificado en ISO-8859-1.**
  Observado en la pila: `Content-Type: application/json;charset=ISO-8859-1`
  y `Autenticación requerida` con `0xF3` (Latin-1) en el body — Tomcat
  (default de Boot) decodifica `getWriter()` con ISO-8859-1. HTTP-válido
  (el charset va declarado) pero NO es JSON UTF-8: clientes que decodifican
  el body como UTF-8 (p. ej. `fetch().json()` del frontend de C8) fallan al
  parsear el mensaje (el `code` sigue siendo legible por header/grep lenient;
  el impacto es de parseo estricto del JSON). Afecta al 401 del panel y a
  los 401 de cadenas 1/3. Recomendación (sin tocar main en verify): fix
  puntual en C8 o commit de fix —
  `response.setCharacterEncoding("UTF-8")` antes de `getWriter()` (o
  `ObjectMapper.writeValue(response.getOutputStream(), …)`), más un caso de
  higiene en el siguiente QA si se desea. No bloquea C2 (el status/literal
  del `code` cumple y ningún E2.x pedía el byte-level encoding).
- **WARNING — Veneno de env proceso-vs-`.env` en compose (root-cause Q24).**
  La precedencia del env del proceso sobre `.env` en la interpolación
  `${VAR}` de compose.yaml + `restart` que no re-lee env pueden dejar la
  pila con variables vacías "fantasma" tras un restore. Se fija como
  **patrón para scripts futuros** (disciplina anti-veneno: recomponer con
  el env del proceso UNSET, `.env` en forma robusta, recreate — nunca
  restart — cuando cambia el env). Ver §9 acción para C9.
- **SUGGESTION — Literal 204 del preflight en qa-plan**: cambiar a
  `20[46]`/2xx + cabeceras en los scripts QA futuros (futuros cortes; no
  ejecutado aquí por restricción de fase).
- **SUGGESTION — Redacción del ACAH en qa-plan**: aceptar el reflejo de
  request-headers (X-Confirm-* configurado en el bean, no literal en la
  respuesta) — nota para la próxima validación del plan.
- **SUGGESTION — Typo en qa-report Q3**: dice "JWT HS256 con jti" — el
  claims set es cerrado {sub, role, iat, exp} sin `jti` (verificado en pila
  y por TokenServiceTest); corregir en la próxima edición del informe si
  se re-edíta.
- **SUGGESTION — S15 sin escenario E2.x**: véase §7 (decisión recomendada,
  no bloqueante).

**CRITICAL: ninguno.**

## 9. Recomendación final

**Procede archive** (PASS-with-notes). Estado al cierre: `mvn test` completo
verde (26/26, incluye LogHygieneTest corregido), pila podman green (3×
healthy, RestartCount=0), HEAD = `829cc70`, working tree sin ficheros de
código pendientes (solo artefactos `.atl` y QA sin versionar, para
sdd-archive como en C1), intocables de C1 con diff vacío, sin secretos
versionados.

Acciones heredadas (cortas, para el orquestador):

- **C8**: el login del frontend debe tratar las respuestas no-200 sin
  confiar en el parseo estricto del body (el 401 del entry point va
  ISO-8859-1 — hallazgo WARNING 1); CORS dev ya cubierto por G1, no hace
  falta config adicional.
- **C9**: smoke canónico que incluya (a) recomposición con el env del
  proceso UNSET + `.env` de la VM real sin placeholders ni comentarios en
  línea (disciplina anti-veneno — WARNING 2), y (b) opcionalmente el caso
  único de rotación de JWT_SECRET (§7).
- **Futuros scripts QA**: asertar preflight CORS como `20[46]`/2xx + cabeceras
  (no 204 literal), y sondeos de salud vía `podman inspect
  .State.Health.Status`/en-red cuando el routing del proxy no cubra la ruta
  (técnica Q18/Q19 del verify).
- **Próxima validación de docs**: registrar la decisión S15 (§7), la nota
  CD-8a (§6.3) y, si se re-edíta el qa-plan, los dos SUGGESTION de
  redacción. Sin ecos obligatorios de esta fase.