---
change: "C2"
artifact: tasks
last_updated: 2026-10-20
status: done
---

# Tasks — C2 · Seguridad base + auth del panel

> Desglose mecánico para `sdd-apply` con **contexto fresco**. Cada task es
> autosuficiente: contiene los ficheros que toca (rutas del árbol [N1]–[N13]
> de `design.md` §1.1), las referencias exactas (S-n / CD-n / E2.n / G-n y §
> de los docs) y un criterio "Done" observable. Este artefacto NO reescribe
> literales; apunta a la sección exacta del doc donde se **copian**.
>
> Contexto del arranque: el repo ya contiene **C1 completo** (31 ficheros,
> 4 commits, pila green en podman). C2 toca **exactamente 3 ficheros de C1**
> (`backend/pom.xml`, `backend/src/main/resources/application.yml`,
> `compose.yaml`), añade **13 ficheros nuevos en `src/main`** y **5 de
> tests** — nada más. Todo lo de C1 que no está en la lista de 1.2 de
> `design.md` es INTOCABLE (§0.3).
>
> Verificación SIEMPRE contra la pila completa con **podman**:
> `podman compose up -d --build` — `docker compose` NO está disponible en
> este entorno (RG-6/C1; advertencia §4.11).

---

## 0. Recordatorios de contrato (leer ANTES de escribir nada)

### 0.1 Literales que se CITAN de los docs, nunca se redefinen

| # | Literal | Fuente exacta |
|---|---|---|
| L1 | Endpoints y cuerpos de auth: `POST /api/v1/panel/auth/login` entrada `{username, password}` → **200** `{token, username, expires_at}` · **401** `{"code": "invalid_credentials", …}` · `GET /api/v1/panel/me` → **200** `{username, role: "ADMIN", expires_at}` · **401** | `/home/acuelop369/Escritorio/Proyectos/Project_Log/docs/05_BACKEND_SCHEMA.md` §3.2/§3.3; `docs/02_TRD.md` §2.5 |
| L2 | Cuerpo de error estándar `{code, message, field?}`; fechas ISO-8601 UTC con sufijo `Z`; 429 no existe | `docs/05_BACKEND_SCHEMA.md` §3.1 |
| L3 | JWT: algoritmo **HS256**; claims conjunto CERRADO `{sub, role, iat, exp}` (sin `jti`/`iss`); `JWT_SECRET` ≥32 bytes UTF-8 aleatorios; TTL 8 h (`JWT_TTL_HOURS`, defecto 8, GATE 2026-10-19); sin logout ni refresh; 401 con cuerpo estándar | `docs/05_BACKEND_SCHEMA.md` §5; `docs/02_TRD.md` ADR-13, §5, §6.3 |
| L4 | Env: nombres CERRADOS `JWT_SECRET`, `JWT_TTL_HOURS`, `ADMIN_INITIAL_USERNAME`, `ADMIN_INITIAL_PASSWORD`, `FRONTEND_ORIGIN` (tabla = autoridad; `.env.example` NO se toca) | `docs/02_TRD.md` §6.3 |
| L5 | Seed literal: "si no existe ningún `users`, crea el admin con `username` = `ADMIN_INITIAL_USERNAME` y `password_hash` = Argon2id(`ADMIN_INITIAL_PASSWORD`)"; idempotente; **por código, no en SQL de Flyway**; sin otras semillas; sin credenciales por defecto | `docs/05_BACKEND_SCHEMA.md` §6.3 (gate G4 resuelve la divergencia con TRD §5 a favor del Schema) |
| L6 | Tabla `users` (columnas y constraint literales): `id BIGINT IDENTITY PK`; `username` TEXT NOT NULL UNIQUE (`users_username_unique`); `password_hash` TEXT NOT NULL — "Hash Argon2id (formato PHC)"; `role` TEXT NOT NULL DEFAULT 'ADMIN'; `created_at`/`updated_at` TIMESTAMPTZ NOT NULL | Fichero ya en repo: `backend/src/main/resources/db/migration/V1__init.sql` (C1) + `docs/05_BACKEND_SCHEMA.md` §2/§6.1 |
| L7 | Contraseñas: Argon2id, parámetros **OWASP 2024** (spec S6 fija el constructor: 16, 32, 1, 19456, 2), vía Spring Security, formato PHC en BBDD | `docs/02_TRD.md` ADR-3, §5; `docs/05_BACKEND_SCHEMA.md` §5 |
| L8 | CORS: restricción estricta al origen del frontend, **nunca wildcard**, nunca en ingesta; en prod mismo-origen por proxy | `docs/02_TRD.md` §5, §6.2; `docs/05_BACKEND_SCHEMA.md` §5 |
| L9 | Superficie enrutable del proxy: SOLO `/api/v1/**` + `/ws/logs`; `Authorization: Bearer` en panel; ingesta por `X-API-Key` (sin usar hasta C5); reloj host NTP = base temporal del JWT | `docs/02_TRD.md` §9, §2.5, §2.8 |

### 0.2 Decisiones del gate (5/5 aprobadas 2026-10-20) — VINCULANTES

- **G1 CORS**: config mínima en C2 — solo `FRONTEND_ORIGIN`, sobre `/api/v1/panel/**` (vía cadena 2), sin wildcard; nunca en ingesta (S25/S26).
- **G2 `/ws/logs`**: libre (sin auth) hasta C7 — el Echo de C1 NO se rompe (S31, E2.13).
- **G3 Tests**: `spring-boot-starter-test` (scope test, versión del BOM) + tests [L] sin contenedor ni BBDD + smokes [S] curl contra la pila podman (§6 del spec: sin Testcontainers — diferido a C3).
- **G4 Seed**: semántica **tabla-vacía** (COUNT): tabla no vacía y `ADMIN_INITIAL_USERNAME` inexistente → **no hacer nada** (S22, E2.9).
- **G5 Versiones**: `org.bouncycastle:bcprov-jdk18on:1.86` + `io.jsonwebtoken:jjwt-api/impl/jackson:0.13.0` — **literales exactos, sin rangos** (RG-1).
- **Code del 401 del filtro**: `invalid_token`, único para ausente/malformado/inválido/expirado, sin subtipos — fijado para este corte (S5; cambiable solo en el futuro afectando al frontend de C8).

### 0.3 Intocables de C1 (diff vacío exigido — E2.14 y §1.3 del design)

- `backend/src/main/java/com/projectlog/config/StartupSecretsValidator.java` + `backend/src/main/resources/META-INF/spring.factories` — **javadoc incluido** (S29): C2 no añade ni relaja variables.
- `backend/src/main/resources/db/migration/V1__init.sql` — cero migraciones nuevas (S24, E3/E2.22).
- `backend/src/main/java/com/projectlog/config/WebSocketConfig.java` y `backend/src/main/java/com/projectlog/streaming/EchoSocketHandler.java` (S31).
- `frontend/nginx.conf`, `docker/Dockerfile`, `frontend/Dockerfile` (S32, E2/E8/E9).
- `.env.example` — TRD §6.3 ya lo documenta; G1 no cambia nombres (design §1.3).
- `backend/src/main/java/com/projectlog/Application.java` + `package-info.java` — el escaneo ya cubre los beans nuevos.
- `scripts/ws_echo_test.py` — intocable y **re-ejecutado** en F4 (E2.13).
- `logging.level` de C1 en `application.yml` queda intacto (S16).

---

## 1. Grafo de dependencias (orden de ejecución)

```
1.1 pom.xml ── 1.2 application.yml ── 1.3 compose.yaml ── 1.4 build + COMMIT #1
2.1 RestAuthenticationEntryPoint ──┐
2.2 CorsConfig ────────────────────┤── 2.5 SecurityConfig ── 2.6 build + COMMIT #2
2.3 TokenService ── 2.4 JwtPrincipal + JwtAuthFilter ──┘
3.1 PasswordConfig ── 3.2 AdminSeeder (dep. 3.1)
 ├── 3.3 LoginController + LoginRequest/LoginResponse
 └── 3.4 MeController + MeResponse          ── 3.5 build + COMMIT #3
4.1 Argon2PasswordTest; 4.2 TokenServiceTest; 4.3 PanelAuthMvcTest (dep. 4.1-4.2 verdes);
 4.4 CorsConfigTest; 4.5 LoginCredentialsTest  ── 4.6 mvn test + COMMIT #4
5.1–5.9 verificación smokes podman (sin commit; fixes → commits de fix)
```

- **Nota de orden (única desviación respecto al mero agrupado por fase)**:
  `TokenService` (2.3) vive en el paquete `auth` pero se ejecuta **dentro de
  F1**: `JwtAuthFilter` (2.4) y `SecurityConfig` (2.5) lo necesitan para
  compilar. El fichero y su contrato son EXACTAMENTE los del design
  [N2]/CD-3 — solo se adelanta su ejecución.
- Bloqueos clave: 2.4 bloquea 2.5; 3.1 bloquea 3.2; F3 completo bloquea F4;
  F4 bloquea el "Done" del corte (§5).

---

## 2. Tasks

### Fase F0 — Dependencias y configuración base (bloquea F1)

#### 1.1 · Añadir las 6 dependencias a `backend/pom.xml`

- **Ficheros**: `backend/pom.xml` (modificado).
- **Cubre**: S7, S10, S27; CD-8 (design §1.4); G5/RG-1.
- **Contenido** (design §1.4, literal; se añaden tras las existentes de C1,
  **sin reordenar**): `spring-boot-starter-security` (sin versión — BOM),
  `org.bouncycastle:bcprov-jdk18on:1.86` (**scope runtime** — issue
  spring-security #8842, advertencia §4.1), `io.jsonwebtoken:jjwt-api:0.13.0`
  (compile, default), `io.jsonwebtoken:jjwt-impl:0.13.0` y
  `io.jsonwebtoken:jjwt-jackson:0.13.0` (**scope runtime**),
  `spring-boot-starter-test` (**scope test**, sin versión — BOM). Actualizar
  el comentario del bloque `<dependencies>` para eliminar la frase
  "Deliberadamente SIN spring-boot-starter-test (los tests llegan en C2)",
  que deja de ser verdad. `spring-boot-starter-jdbc` ya está (D4 de C1):
  `JdbcTemplate` no añade dependencia.
- **Depende de**: estado C1.
- **Done**: `mvn -q -f backend/pom.xml dependency:resolve` limpio;
  `mvn -f backend/pom.xml dependency:tree` muestra `bcprov-jdk18on` en
  runtime, `jjwt-impl`/`jjwt-jackson` en runtime, `jjwt-api` en compile,
  `spring-boot-starter-test` solo bajo `maven.test` scope; `grep` del pom
  confirma `1.86` y `0.13.0` literales y CERO rangos/`latest` (RG-1).

#### 1.2 · Actualizar `backend/src/main/resources/application.yml`

- **Ficheros**: `backend/src/main/resources/application.yml` (modificado).
- **Cubre**: S13, S25, S26; CD-7 (bloque `projectlog.security`).
- **Contenido**: (a) actualizar el comentario de cabecera de C1 (la frase
  "sin FRONTEND_ORIGIN (CORS es C2)" ya no es cierta); (b) añadir el bloque
  EXACTO del design CD-7:

  ```yaml
  projectlog:
    security:
      jwt-ttl-hours: ${JWT_TTL_HOURS:8}        # defecto 8 (TRD §6.3, S13)
      frontend-origin: ${FRONTEND_ORIGIN:}     # opcional: falta ⇒ CORS cerrado (S26)
  ```

  **NO** añadir `jwtSecret` al yml (el design lo prohíbe: se lee con
  `@Value("${JWT_SECRET}")` — jamás default-izado en un fichero versionado).
  TODO lo demás de C1 (datasource, Flyway, exposure `health,info`,
  `logging.level`) queda intacto (L7 de C1, S16).
- **Depende de**: 1.1 (mismo commit, orden libre).
- **Done**: el YAML carga sin error; el bloque `projectlog.security` existe
  con los defaults `${JWT_TTL_HOURS:8}` y `${FRONTEND_ORIGIN:}` visibles;
  `git diff` sobre el fichero muestra SOLO el comentario y el bloque nuevo;
  `exposure.include: health,info` sigue literal.

#### 1.3 · Añadir `FRONTEND_ORIGIN` al `compose.yaml`

- **Ficheros**: `compose.yaml` (modificado).
- **Cubre**: S25, E2.17; design §1.2 (hallazgo: C1 no pasa la variable al
  contenedor — sin esta línea S25/E2.17 son imposibles).
- **Contenido**: **+1 línea** en `backend.environment`:
  `FRONTEND_ORIGIN: ${FRONTEND_ORIGIN}`. NADA más: puertos, red, volumen,
  healthchecks y `restart` sin cambios (E2, L8 de C1).
- **Depende de**: 1.2.
- **Done**: `podman compose config` valida sin errores; `git diff` sobre
  `compose.yaml` muestra exactamente esa línea y nada más; interpolación con
  la variable ausente del `.env` no rompe el `config` (cadena vacía = S26).

#### 1.4 · Verificar build y COMMIT #1

- **Ficheros**: ninguno nuevo (verificación).
- **Cubre**: S7/S10/S27 (compilan), RG-1.
- **Acción**: `mvn -q -DskipTests -f backend/pom.xml package` → retorno 0
  (las deps nuevas aún sin uso no rompen el build).
- **Depende de**: 1.1–1.3.
- **Commit #1**: `feat(security): dependencias y configuración base para la seguridad de C2` + trailer §3.
- **Done**: build limpio; el commit contiene EXACTAMENTE `backend/pom.xml`,
  `backend/src/main/resources/application.yml` y `compose.yaml` (los 3
  ficheros modificados de C1 — ni uno más).

---

### Fase F1 — Seguridad: cadenas, filtro JWT y CORS

#### 2.1 · Crear `config/RestAuthenticationEntryPoint.java` [N12]

- **Ficheros**: `backend/src/main/java/com/projectlog/config/RestAuthenticationEntryPoint.java`.
- **Cubre**: S2, S5 (escritor único del 401); CD-4.
- **Contenido** (CD-4): `@Component` que implementa
  `AuthenticationEntryPoint`; `commence()` escribe con `ObjectMapper` —
  status **401**, `Content-Type: application/json`, cuerpo
  `{"code": "invalid_token", "message": "Autenticación requerida"}` (mismo
  formato `{code, message}` que el 401 de login, L1/L2). Mensaje genérico
  IDÉNTICO para los cuatro casos (ausente/malformado/inválido/expirado) —
  sin subtipos ni oráculo (S5). NO lee NI loguea el header `Authorization`
  (S16, advertencia §4.8). Reutilizado por las TRES cadenas (CD-1).
- **Depende de**: 1.4.
- **Done**: `mvn -q -DskipTests compile` limpio; grep no encuentra ninguna
  llamada a logger ni referencia al header en el fuente.

#### 2.2 · Crear `config/CorsConfig.java` [N13]

- **Ficheros**: `backend/src/main/java/com/projectlog/config/CorsConfig.java`.
- **Cubre**: S25, S26; CD-7; G1.
- **Contenido** (CD-7): `@Configuration` que expone `@Bean
  CorsConfigurationSource`; inyecta `frontendOrigin` (string desde
  `projectlog.security.frontend-origin`, **vacío si falta**); usa
  `UrlBasedCorsConfigurationSource`; si el origen NO está en blanco,
  registra config para `"/**"` (el `securityMatcher` de la cadena 2 limita
  el efecto a `/api/v1/panel/**`):
  `setAllowedOrigins(List.of(origin))` — **NUNCA `*`, NUNCA
  `allowedOriginPatterns`** (G1); métodos `GET, POST, PATCH, DELETE,
  OPTIONS`; headers `Authorization`, `Content-Type`, `X-Confirm-*`
  (reservados C3/P1); **`setAllowCredentials(false)`** (JWT por header, no
  cookies); `setMaxAge(3600L)`. Si `FRONTEND_ORIGIN` falta ⇒ se registra
  **CERO** configuraciones — CORS cerrado por defecto (S26, E2.18). Sin
  `cors()` en la cadena 1 (ingesta, TRD §5).
- **Depende de**: 1.4 (bloque yml de 1.2).
- **Done**: compila; grep confirma CERO apariciones de `"*"` como origen ni
  `allowedOriginPatterns` en el fuente; la rama de origen vacío no registra
  nada.

#### 2.3 · Crear `auth/TokenService.java` [N2]

- **Ficheros**: `backend/src/main/java/com/projectlog/auth/TokenService.java`.
- **Cubre**: S10–S16; CD-3. *(Nota de orden §1: fichero del paquete `auth`,
  ejecutado aquí para que 2.4/2.5 compilen.)*
- **Contenido** (CD-3): `@Component`; constructor recibe `jwtSecret`
  (`@Value("${JWT_SECRET}")` — obligatorio, sin default) y `ttlHours`
  (desde `projectlog.security.jwt-ttl-hours`, defecto 8). Clave HMAC:
  `new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8),
  "HmacSHA256")` — **bytes UTF-8 crudos**, la MISMA convención que mide el
  validador de C1; SIN Base64/HKDF (S11).
  - `issue(username, role)`: `Jwts.builder().subject(...).claim("role", ...)
    .issuedAt(...).expiration(iat.plus(ttl))` con
    `signWith(secretKey, Jwts.SIG.HS256)`; claims EXACTOS
    `{sub, role, iat, exp}` — conjunto cerrado, sin añadir ninguno (S12/L3).
    Devuelve token **y** el `Instant exp` juntos (CD-6: `expires_at` sin
    re-derivar).
  - `verify(token)`: `Jwts.parser().verifyWith(secretKey).build()
    .parseSignedClaims(token)` envuelto en try/catch de `JwtException` e
    `IllegalArgumentException` → `Optional<JwtPrincipal>` vacío ante
    cualquier fallo, sin distinguir causa (S5).
  - **Sin clock-skew**: NO invocar `clockSkewSeconds(...)` — comentario que
    documente la decisión (reloj host NTP, S14).
  - **API 0.13.0 exclusivamente no deprecada**: `Jwts.SIG.HS256`,
    `parser().verifyWith(...)`, `parseSignedClaims(...)` — **ningún import
    de `io.jsonwebtoken.impl.*`** (S10, advertencia §4.2).
  - TTL expira = `iat + TTL` (S13). El servicio NO loguea nada (S16).
- **Depende de**: 1.4 (deps jjwt de 1.1).
- **Done**: compila; los imports de jjwt provienen SOLO de `io.jsonwebtoken`
  raíz y `io.jsonwebtoken.security`/`Jwts.SIG` (grep: cero `impl.*`); el
  literal de derivación es `getBytes(StandardCharsets.UTF_8)`; no existe
  ninguna llamada a logger en la clase.

#### 2.4 · Crear `auth/JwtPrincipal.java` [N4] y `auth/JwtAuthFilter.java` [N3]

- **Ficheros**: `backend/src/main/java/com/projectlog/auth/JwtPrincipal.java`;
  `backend/src/main/java/com/projectlog/auth/JwtAuthFilter.java`.
- **Cubre**: S5, S18 (puente), S20; CD-4.
- **Contenido**: `JwtPrincipal` = record `{username, role, expiresAt:
  Instant}`. `JwtAuthFilter` = `@Component OncePerRequestFilter` registrado
  SOLO en la cadena 2 (2.5) — no es bean global (CD-4). Flujo: (1) header
  `Authorization` con prefijo exacto `"Bearer "` → extraer token crudo →
  `tokenService.verify(token)`; (2) válido →
  `UsernamePasswordAuthenticationToken(principal=new
  JwtPrincipal(sub, role, exp), credentials=null, authorities=List.of())` en
  el `SecurityContextHolder` y continuar (autoridades vacías: sin checks por
  rol, S20/ADR-14); (3) ausente/malformado/inválido/expirado → el filtro
  **NO escribe respuesta** y **NO setea contexto**: continúa la cadena
  vacía; la regla `authenticated()` falla y el **entry point** (2.1) escribe
  el 401 — UN solo punto escritor (CD-4). Sin logging del token/header
  (S16).
- **Depende de**: 2.1 (conceptual), 2.3 (compilación).
- **Done**: compila; el filtro NO contiene ningún objeto `Response` ni
  escritura de status (grep: sin `response.setStatus` ni `getWriter`); no
  hay ninguna referencia a `Authorization` en logs.

#### 2.5 · Crear `config/SecurityConfig.java` [N11] (3 SecurityFilterChain)

- **Ficheros**: `backend/src/main/java/com/projectlog/config/SecurityConfig.java`.
- **Cubre**: S1–S4, S19, S30, S31; CD-1; G2.
- **Contenido** (CD-1, tabla literal):

  | Cadena | Matcher | Reglas |
  |---|---|---|
  | 1 `@Order(1)` | `securityMatcher("/api/v1/ingest/**")` | `anyRequest().denyAll()` + entry point JSON 401 + mismo entry point como `accessDeniedHandler` (nunca 403 HTML). **Sin** `cors()`. Mecanismo `X-API-Key`: NO se implementa (C5) — S2. |
  | 2 `@Order(2)` | `securityMatcher("/api/v1/panel/**")` | `csrf(disabled)`, `sessionManagement(STATELESS)`, `cors(withDefaults())` (fuente 2.2), `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`, **sin** `httpBasic` ni `formLogin`; `requestMatchers(HttpMethod.POST, "/api/v1/panel/auth/login").permitAll()` (método+ruta EXACTOS) y `anyRequest().authenticated()` (no `hasRole` — ADR-14, S20); entry point JSON 401. |
  | 3 `@Order(3)` | sin matcher (resto) | `requestMatchers("/actuator/health", "/actuator/info", "/ws/logs").permitAll()` + `anyRequest().denyAll()` + entry point JSON 401. |

  Las tres cadenas reciben el entry point por constructor (comparten el
  escritor único). `/ws/logs` permitAll **obligatorio** (G2 — sin él se
  rompe el Echo, advertencia §4.6). El usuario in-memory de Boot queda
  inerte: sin `UserDetailsService` propio ni `httpBasic`/`formLogin` (S3).
- **Depende de**: 2.1, 2.2, 2.4 (y 2.3 vía 2.4).
- **Done**: compila; grep confirma EXACTAMENTE 3 beans `SecurityFilterChain`
  con `@Order(1)/(2)/(3)`, `permitAll` del login con `HttpMethod.POST` +
  ruta literal, `/ws/logs` y `/actuator/health`+`/actuator/info` en la
  cadena 3, `denyAll` en cadenas 1 y 3, y CERO `httpBasic(`/`formLogin(`.

#### 2.6 · Verificar compilación y COMMIT #2

- **Ficheros**: ninguno nuevo (verificación).
- **Acción**: `mvn -q -DskipTests -f backend/pom.xml package` → retorno 0.
- **Depende de**: 2.1–2.5.
- **Commit #2**: `feat(security): tres cadenas SecurityFilterChain, filtro JWT HS256 y CORS mínimo` + trailer §3. Incluye [N11]–[N13] y [N2]–[N4]
  (TokenService entra aquí por la dependencia de compilación de 2.4 — nota §1).
- **Done**: build limpio; el commit contiene los 6 ficheros [N2], [N3],
  [N4], [N11], [N12], [N13]; `git diff HEAD~1` NO toca ningún fichero de la
  lista intocable §0.3.

---

### Fase F2 — Auth: Argon2id, seed y endpoints

#### 3.1 · Crear `auth/PasswordConfig.java` [N1]

- **Ficheros**: `backend/src/main/java/com/projectlog/auth/PasswordConfig.java`.
- **Cubre**: S6–S9, S28; CD-5; E2.20.
- **Contenido** (CD-5, literal): `@Configuration` con `@Bean PasswordEncoder
  passwordEncoder()` → `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)`
  (salt 16, hash 32, `p=1`, `m=19456` KiB, `t=2` — OWASP 2024). **NO**
  usar `defaultsForSpringSecurity_v5_8` (m=16384 ≠ OWASP 2024 — S6).
  Produce PHC `$argon2id$v=19$m=19456,t=2,p=1$…` = lo que espera el
  comentario literal de `V1__init.sql` (S8/L6). Comparación SIEMPRE
  `encoder.matches(raw, password_hash)` — nunca hashear y comparar strings
  (S8). El PHC autodescribe sus parámetros: si el bean evolucionara,
  `matches` sigue OK (S8).
- **Depende de**: COMMIT #2.
- **Done**: compila; el constructor es el literal `(16, 32, 1, 19456, 2)` con
  comentario de fuente OWASP 2024; **no** aparece
  `defaultsForSpringSecurity_v5_8` ni `defaultsForSpringSecurity_v5_5`.
  Si un uso posterior del encoder lanzara `NoClassDefFoundError`, revisar el
  scope `runtime` de `bcprov-jdk18on` en 1.1 ANTES de continuar (issue
  spring-security #8842 — advertencia §4.1).

#### 3.2 · Crear `auth/AdminSeeder.java` [N5]

- **Ficheros**: `backend/src/main/java/com/projectlog/auth/AdminSeeder.java`.
- **Cubre**: S21–S24; CD-2; G4; E2.7–E2.10, E2.22.
- **Contenido** (CD-2, pseudocódigo literal): `@Component` que implementa
  `ApplicationRunner` en `auth`; acceso a datos vía `JdbcTemplate`
  (ya presente por C1). Sin `@Order` ni dependencia explícita de Flyway:
  Boot garantiza validador (EnvironmentPostProcessor, pre-refresh) → refresh
  (Flyway V1) → runners. Lógica:

  ```
  run():
    long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    if (n > 0) { log.info("Seed omitido: users no está vacía..."); return; }   // G4 / S22
    String hash = passwordEncoder.encode(env.getProperty("ADMIN_INITIAL_PASSWORD"));
    jdbcTemplate.update("""
        INSERT INTO users (username, password_hash, role)
        VALUES (?, ?, 'ADMIN')
        ON CONFLICT ON CONSTRAINT users_username_unique DO NOTHING
        """, env.getProperty("ADMIN_INITIAL_USERNAME"), hash);
    log.info("Admin inicial sembrado (users vacía).");   // sin valores, sin hash
  ```

  - COUNT **tabla-vacía global**, no por-username (G4/L5): cambiar
    `ADMIN_INITIAL_USERNAME` tras el primer seed NO crea un segundo admin
    (E2.9).
  - `ON CONFLICT ... users_username_unique DO NOTHING` = segunda barrera de
    idempotencia (nombre del constraint LITERAL de `V1__init.sql`); con 1
    admin ambas caídas dan exactamente 1 fila (E2.8).
  - `role='ADMIN'` EXPLÍCITO en el INSERT (S21, E2.22). Nunca se hashea en
    SQL ni en Flyway — el hash depende de configuración (L5, S24: V1 intacto).
  - **No re-valida presencia** de credenciales (S23): sin
    `ADMIN_INITIAL_PASSWORD` el proceso no llega aquí (E4 falla antes —
    validador C1 intocable). Coste: COUNT + 0 hashes en reinicio normal
    (el COUNT evita re-hashing — advertencia §4.5).
  - Logs sin valores: jamás username ni hash (misma higiene S16/E2.19).
- **Depende de**: 3.1 (encoder).
- **Done**: compila; el SQL contiene `COUNT(*) FROM users`,
  `users_username_unique` y `role` explícito `'ADMIN'`; grep confirma que
  ningún log imprime el username o el hash; NO existen otras semillas ni
  migraciones nuevas (`ls backend/src/main/resources/db/migration/` =
  solo `V1__init.sql` — S24).

#### 3.3 · Crear `auth/LoginRequest.java` [N8], `auth/LoginResponse.java` [N9] y `auth/LoginController.java` [N6]

- **Ficheros**: `backend/src/main/java/com/projectlog/auth/LoginRequest.java`,
  `.../auth/LoginResponse.java`, `.../auth/LoginController.java`.
- **Cubre**: S17, S28; CD-6; L1/L2; E2.1, E2.2.
- **Contenido** (CD-6): `LoginRequest` record `{username, password}` — SIN
  anotaciones de validación (la semántica de error es siempre el MISMO 401).
  `LoginController`:
  1. `JdbcTemplate`: `SELECT password_hash FROM users WHERE username = ?`
     (limitada por `users_username_unique`).
  2. `encoder.matches(password, hash)`; usuario inexistente o match fallido
     → MISMO camino: **401** `{"code": "invalid_credentials", "message":
     "…"}` — literal idéntico para usuario inexistente, password incorrecto
     y cuerpo malformado (anti user-enumeration, S17/L1). Hardening de
     tiempo: ante usuario inexistente, `matches` contra un hash dummy
     constante de la clase (misma latencia).
  3. OK → `tokenService.issue(username, "ADMIN")`; `expires_at` = `exp`
     devuelto por `issue`, formateado con `DateTimeFormatter` en
     `ZoneOffset.UTC` y patrón `yyyy-MM-dd'T'HH:mm:ss'Z'` — determinista,
     SIEMPRE con `Z` y precisión de segundos (CD-6, riesgo 7 del design;
     L2). → **200** `LoginResponse{token, username, expires_at}` (L1).
  4. **Cuerpo malformado** (JSON no parseable
     `HttpMessageNotReadableException` o campos nulos) → `@ExceptionHandler`
     en el PROPIO controller → **401** `invalid_credentials` (NO el 400 por
     defecto de Boot — S17 exige el mismo par). Sin infraestructura
     genérica anticipada en `common` (S28).
  Sin logging de credenciales ni token (S16).
- **Depende de**: 3.1, 2.3 (TokenService), estado C1 de BBDD.
- **Done**: compila; grep confirma un único literal `invalid_credentials`
  usado en las 3 ramas de fallo; el formatter es
  `yyyy-MM-dd'T'HH:mm:ss'Z'` en UTC compartido conceptualmente con /me; el
  handler de `HttpMessageNotReadableException` responde 401 (no 400).

#### 3.4 · Crear `auth/MeController.java` [N7] y `auth/MeResponse.java` [N10]

- **Ficheros**: `backend/src/main/java/com/projectlog/auth/MeController.java`,
  `.../auth/MeResponse.java`.
- **Cubre**: S18, S28; CD-6; L1; E2.5, E2.6.
- **Contenido** (CD-6): `MeResponse` record `{username, role,
  expires_at}`; `MeController` lee la `Authentication` del `SecurityContext`,
  castea el principal a `JwtPrincipal` y responde **200**
  `MeResponse{username, role: "ADMIN", expiresAt}` — `role` y `expires_at`
  salen de los claims del token, **SIN consulta BBDD** (S18). `expires_at`
  con el MISMO formatter UTC del login (idéntico formato `Z`, E2.5). Sin
  sesión y sin usuario de DB: cambiar password no revoca tokens activos
  hasta `exp` (Schema §5, cierre local — S20).
- **Depende de**: 2.4 (`JwtPrincipal`), 3.3 (formatter compartido).
- **Done**: compila; el controller NO inyecta `JdbcTemplate` (sin BBDD en
  el caso normal, S18); el rol sale del claim `role` (no hardcodeado salvo
  el emisor "ADMIN" de 3.3).

#### 3.5 · Verificar compilación y COMMIT #3

- **Ficheros**: ninguno nuevo (verificación).
- **Acción**: `mvn -q -DskipTests -f backend/pom.xml package` → retorno 0.
- **Depende de**: 3.1–3.4.
- **Commit #3**: `feat(auth): login y /me con Argon2id y seed idempotente del admin` + trailer §3.
- **Done**: build limpio; el commit contiene [N1], [N5]–[N10] (7 ficheros);
  con este commit el árbol de `src/main` coincide 1:1 con design §1.1
  (13 ficheros nuevos + los 3 modificados de F0); nada de §0.3 tocado.

---

### Fase F3 — Tests ligeros [L] (G3, sin contenedor ni BBDD — CD-9)

> Regla de fase (CD-9 + §6 del spec): los tests [L] usan SOLO
> `spring-boot-starter-test` (JUnit 5 + AssertJ + MockMvc + Mockito con
> `@MockitoBean` — reemplazo de `@MockBean` en Boot 3.5) y arrancan en
> segundos. **No** se usan `@SpringBootTest` (exigiría DataSource/Flyway/admin
> ⇒ terreno de los smokes [S] y, con BBDD real, de C3). **No** hay test [L]
> de seed: los escenarios E2.7/E2.8/E2.9/E2.22 necesitan PostgreSQL real y el
> mapa del spec §6 los mapea 100% como [S] — NO se mockea el datasource para
> simular BD y NO se introducen H2 ni Testcontainers (dependencias nuevas no
> propuestas; Testcontainers diferido a C3). Si un placeholder de propiedad
> chocara en un slice, la salida prevista es un `application-test.yml` mínimo,
> decidido al escribir el test (CD-9) — no se anticipa aquí.

#### 4.1 · Crear `backend/src/test/java/com/projectlog/auth/Argon2PasswordTest.java` [T1]

- **Ficheros**: ese único (`.../backend/src/test/java/com/projectlog/auth/Argon2PasswordTest.java`).
- **Cubre**: E2.20 [L]; CD-9.
- **Contenido**: unit puro SIN Spring: instancia
  `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)` y aserciona: (a) el PHC
  producido empieza por `$argon2id$v=19$m=19456,t=2,p=1$`; (b) dos
  `encode()` del mismo raw producen hashes DIFERENTES (salt aleatorio);
  (c) `matches(raw, hash)` true con el raw correcto y false con otro;
  (d) `matches` funciona contra un hash generado con los parámetros
  autodescritos del PHC (S8).
- **Depende de**: 3.1, 1.1 (bcprov en runtime está en el classpath de test).
- **Done**: `mvn -q -f backend/pom.xml test -Dtest=Argon2PasswordTest` verde
  — si aquí apareciera `NoClassDefFoundError` de BC, es el issue #8842:
  corregir el scope de 1.1 antes de seguir (advertencia §4.1).

#### 4.2 · Crear `backend/src/test/java/com/projectlog/auth/TokenServiceTest.java` [T2]

- **Ficheros**: `.../backend/src/test/java/com/projectlog/auth/TokenServiceTest.java`.
- **Cubre**: E2.21 [L]; CD-9.
- **Contenido**: unit puro: `new TokenService(SECRETO_TEST_≥32_BYTES_UTF8,
  ttl)`; roundtrip sign→parse; claims EXACTOS `{sub, role, iat, exp}` y SOLO
  esos (rechazar claims extra, S12); `exp = iat + TTL` con default 8 y con
  TTL alterno (p. ej. 1 h); token con firma ALTERADA y token EXPIRADO se
  rechazan en `verify` (Optional vacío, sin distinguir causa — S5).
- **Depende de**: 2.3.
- **Done**: `mvn -q -f backend/pom.xml test -Dtest=TokenServiceTest` verde.

#### 4.3 · Crear `backend/src/test/java/com/projectlog/auth/PanelAuthMvcTest.java` [T3]

- **Ficheros**: `.../backend/src/test/java/com/projectlog/auth/PanelAuthMvcTest.java`.
- **Cubre**: E2.4 [L], E2.6, E2.19 [L]; CD-9 (E2.19 vive DENTRO de esta
  clase como appender ListAppender de logback sobre los loggers de
  JwtAuthFilter/RestAuthenticationEntryPoint/TokenService — 5 ficheros de
  test en total, design §1.1).
- **Contenido** (CD-9): `@WebMvcTest(MeController.class)` +
  `@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class,
  CorsConfig.class})` + `@MockitoBean TokenService` (el conjunto exacto de
  `@Import` — p. ej. añadir `JwtAuthFilter.class` si `SecurityConfig` exige
  su bean en el slice — se decide al escribir el test, CD-9). MockMvc:
  (a) sin header `Authorization` → **401** JSON
  `{code:"invalid_token"}` cuerpo estándar, NO HTML (E2.6);
  (b) `Bearer` con firma alterada (verify mockeado → vacío) → **401**
  `invalid_token` (E2.4 [L]); (c) token expirado (verify mockeado → vacío) →
  **401** `invalid_token`; (d) el 401 nace del entry point con
  `Content-Type: application/json` (CD-4); (e) **E2.19**: con ListAppender
  adjunto, tras las peticiones anteriores NINGUNA línea de log contiene
  `Authorization`, `Bearer` ni el JWT.
- **Depende de**: 4.1, 4.2 (verdes primero — aislar fallos), 2.4–2.5.
- **Done**: `mvn -q -f backend/pom.xml test -Dtest=PanelAuthMvcTest` verde;
  la aserción de higiene de logs es explícita en el test (E2.19).

#### 4.4 · Crear `backend/src/test/java/com/projectlog/config/CorsConfigTest.java` [T4]

- **Ficheros**: `.../backend/src/test/java/com/projectlog/config/CorsConfigTest.java`.
- **Cubre**: E2.18 [L]; CD-9.
- **Contenido**: unit sobre `CorsConfig`: con origen VACÍO →
  `UrlBasedCorsConfigurationSource` sin configuraciones registradas
  (`getCorsConfiguration(req)` nulo para cualquier origin — CORS cerrado,
  S26); con origen seteado → origen REFLEJADO exacto (sin `*`, sin
  `allowedOriginPatterns`), `allowCredentials` false, headers
  `Authorization/Content-Type/X-Confirm-*`, métodos
  GET/POST/PATCH/DELETE/OPTIONS (S25).
- **Depende de**: 2.2.
- **Done**: `mvn -q -f backend/pom.xml test -Dtest=CorsConfigTest` verde.

#### 4.5 · Crear `backend/src/test/java/com/projectlog/auth/LoginCredentialsTest.java` [T5]

- **Ficheros**: `.../backend/src/test/java/com/projectlog/auth/LoginCredentialsTest.java`.
- **Cubre**: E2.2 [componente L]; CD-9 (E2.2 [S] lo confirma en la pila).
- **Contenido** (CD-9): `@WebMvcTest(LoginController.class)` +
  `@MockitoBean` de `JdbcTemplate`/`PasswordEncoder`/`TokenService`:
  (a) usuario inexistente (query sin filas), (b) password incorrecto
  (`matches` false), (c) cuerpo malformado (JSON no parseable) → las tres
  **401** con el MISMO literal `invalid_credentials` (anti
  user-enumeration verificado en [L]).
- **Depende de**: 3.3.
- **Done**: `mvn -q -f backend/pom.xml test -Dtest=LoginCredentialsTest`
  verde; el literal es idéntico en los 3 casos (una sola constante).

#### 4.6 · `mvn test` completo y COMMIT #4

- **Ficheros**: ninguno nuevo (verificación).
- **Acción**: `mvn -q -f backend/pom.xml test` → todas las suites verdes
  (las 5 clases [T1]–[T5]).
- **Depende de**: 4.1–4.5.
- **Commit #4**: `test(backend): tests ligeros de C2 — Argon2, JWT, 401 del panel, CORS e higiene de logs` + trailer §3.
- **Done**: `mvn -q -f backend/pom.xml test` retorno 0; el commit contiene
  exactamente los 5 ficheros de test; `src/test/java` contiene SOLO esas 5
  clases (sin tests de seed, sin Testcontainers, sin H2 — G3); nada de
  §0.3 tocado.

---

### Fase F4 — Verificación contra la pila podman (sin commit; fixes → commits de fix)

> Regla de fase: si un escenario sale rojo, volver a apply con el fallo
> concreto (qué task, qué literal se desvió), corregir y RE-ejecutar desde
> el escenario afectado hacia adelante. Órdenes con PODMAN:
> `podman compose up -d --build` / `podman compose restart backend` —
> `docker compose` NO está disponible. Los Done del 07 obligatorios son
> E2.1, E2.2, E2.3, E2.8 + las no-regresiones E2.11/E2.13/E2.14 (§6 del spec).

#### 5.1 · Rebuild backend y pila arriba con el env nuevo

- **Acción**: `podman compose build backend` && `podman compose up -d`
  (recrea el contenedor para que reciba la nueva variable); esperar a que
  los 3 servicios estén `Up (healthy)`. Confirmar el passthrough:
  `podman compose exec backend printenv FRONTEND_ORIGIN` → el valor del
  `.env` (CRÍTICO para S25/E2.17 — advertencia §4.4).
- **Cubre**: S25, E2.11 (primera observación); design §1.2.
- **Depende de**: COMMIT #4.
- **Done**: 3 contenedores healthy; `printenv` muestra `FRONTEND_ORIGIN`
  con el valor del `.env` (o vacío si no está definida en el `.env` — S26).

#### 5.2 · Smokes de login — Done del 07 (E2.1, E2.2)

- **Acción** (curl contra `http://localhost:8080`, credenciales del `.env`,
  nunca impresas en la terminal/commit):
  - (E2.1) `POST /api/v1/panel/auth/login` con credenciales correctas →
    **200** `{token, username, expires_at}`; `token` = JWT de 3 segmentos y
    su header decodificado (primer segmento base64) declara
    `"alg":"HS256"`; `expires_at` matchea
    `^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$` y representa
    iat+TTL (≈8 h o `JWT_TTL_HOURS`).
  - (E2.2) tres peticiones: password incorrecto; username inexistente;
    cuerpo malformado (`-d 'no-es-json'`) → cada una **401** con
    `{"code": "invalid_credentials", …}` — literal IDÉNTICO en las tres
    (sin user-enumeration).
- **Cubre**: S17, E2.1, E2.2 (smoke obligatorio del 07).
- **Depende de**: 5.1 (admin sembrado — ver 5.5 si el volumen fuera nuevo).
- **Done**: 200 con formato exacto en E2.1; 3× 401 con el mismo `code` en
  E2.2. Revisar `podman compose logs backend`: NO debe aparecer "Using
  generated security password" (CD-8a — señal de config mal conectada).

#### 5.3 · Panel protegido e ingesta declarada (E2.3, E2.4 [S], E2.16)

- **Acción**:
  - (E2.3) `GET /api/v1/panel/me` SIN `Authorization`; y `POST`/`GET`
    contra una ruta INEXISTENTE de `/api/v1/panel/**` sin token → **401**
    `{"code": "invalid_token", …}` en todos (S19: el panel nace protegido;
    por el proxy llega JSON, no el index del SPA — E8 intacto).
  - (E2.4 [S]) con el token de 5.2, alterar UN carácter del tercer segmento
    → **401** `invalid_token`.
  - (E2.16) `POST /api/v1/ingest` y `POST /api/v1/ingest/batch` con y sin
    header `X-API-Key` → **401** en la capa de seguridad (nunca 404 ni 2xx
    — cadena 1 declarada deny-by-default; C5 la usará).
- **Cubre**: S3, S5, S19, S2, E2.3, E2.4 [S], E2.16 (E2.3 = smoke obligatorio del 07).
- **Depende de**: 5.2.
- **Done**: todos los casos 401 con cuerpo JSON `invalid_token` /
  código 401 en ingesta; NINGÚN 403 HTML ni 404 en las rutas declaradas.

#### 5.4 · GET /me con JWT válido (E2.5)

- **Acción**: `GET /api/v1/panel/me` con `Authorization: Bearer <jwt de 5.2>`
  → **200** `{username, role: "ADMIN", expires_at}` con los valores del
  token; `expires_at` mismo formato ISO-8601 `Z` (idéntico formato a 5.2).
- **Cubre**: S18, E2.5.
- **Depende de**: 5.2.
- **Done**: 200 con `role` literal `"ADMIN"` y `expires_at` con sufijo `Z`.

#### 5.5 · Seed idempotente en pila real (E2.7, E2.8, E2.9, E2.22)

- **Acción**:
  - (E2.7) si el volumen `pgdata` ya tiene el admin de C2/C1, validar aquí;
    en un volumen limpio (SOLO en local:
    `podman compose down` + `podman volume rm project_log_pgdata`,
    nunca en la VM) arrancar y verificar tras el arranque:
    `podman compose exec postgres psql -U <user> -d <db> -c "SELECT id,
    username, role, left(password_hash, 30) FROM users;"` → **1 fila** con
    `username` = env, `password_hash` empezando por
    `$argon2id$v=19$m=19456,t=2,p=1$` y `role='ADMIN'`; login de 5.2 OK.
  - (E2.8 — smoke obligatorio del 07) `podman compose restart backend`
    (o down/up sin `-v`), esperar healthy → la tabla sigue con
    **exactamente 1 fila**, MISMO `id` (idempotente; el COUNT evitó el
    re-hash — advertencia §4.5).
  - (E2.9) cambiar temporalmente `ADMIN_INITIAL_USERNAME` en el `.env`,
    `podman compose restart backend`, esperar healthy → NO se crea ninguna
    fila nueva (sigue 1 fila, G4), sin error de arranque, fila original
    intacta; **RESTAURAR** el `.env` y reiniciar de nuevo.
  - (E2.22) la fila cumple `V1__init.sql` aplicado: constraint
    `users_username_unique` presente (duplicado → error de constraint, no
    doble fila), timestamps NOT NULL por default de esquema; en frío los
    logs de Flyway dicen "Schema up to date" (E3 — cero migraciones, S24).
- **Cubre**: S21–S24, G4, E2.7, E2.8, E2.9, E2.22.
- **Depende de**: 5.1.
- **Done**: 1 fila estable con PHC y `role='ADMIN'`; reinicio no duplica
  (mismo `id`); username alterado → no-acción sin error; Flyway "Schema up
  to date" en frío.

#### 5.6 · Fail-fast intacto (E2.10, E2.14)

- **Acción**:
  - (E2.14 [L]) `git diff` sobre
    `config/StartupSecretsValidator.java` y `META-INF/spring.factories`
    → **VACÍO** (javadoc incluido); si C1 dejó tests del validador,
    siguen verdes en `mvn test` (hecho en 4.6).
  - (E2.10 [S]) comentar/quitar `ADMIN_INITIAL_PASSWORD` del `.env`,
    `podman compose up -d backend` → el backend NO arranca:
    `podman compose logs backend` muestra el ÚNICO error agregado del
    validador (misma forma que E4 de C1); el seed NO reintenta ni re-valida
    presencia; **RESTAURAR** el `.env` y verificar que la pila vuelve a
    green.
- **Cubre**: S23, S29, E2.10, E2.14 (obligatorio).
- **Depende de**: 5.5.
- **Done**: diff vacío sobre el validador; el fail-fast muestra el mismo
  mensaje agregado y comportamiento que C1 (el bucle `unless-stopped`
  durante el fallo es esperado — RG-6 de C1); restaurado el `.env`, healthy.

#### 5.7 · No-regresiones C1 (E2.11, E2.12, E2.13, E2.15)

- **Acción**:
  - (E2.11) observar los 3 contenedores a lo largo de varios ciclos de
    healthcheck (30 s): `Up (healthy)` estable, sin bucle de
    `restart: unless-stopped`; `GET /actuator/health` y `/actuator/info` →
    **200**; `/actuator/health` sigue siendo el healthcheck de compose que
    pasa (curl 127.0.0.1:8080 dentro del contenedor).
  - (E2.12) `actuator/env` JAMÁS devuelve contenido de configuración:
    dentro de la red,
    `podman compose exec backend curl -i http://127.0.0.1:8080/actuator/env`
    → el endpoint no expone configuración (la cadena 3 lo rechaza; se
    registra el código observado 401/404 como evidencia — ver nota); desde
    el host por el proxy la ruta tampoco enruta al backend (L9: nginx solo
    `/api/v1/**` + `/ws/logs`). *Nota (coherencia CD-1/E2.15): el "404"
    literal del spec describe el mecanismo de C1; con la cadena 3 +
    entry point el rechazo puede manifestarse como 401 JSON — el criterio
    DURO e innegociable es "nunca contenido de configuración" (E10).*
    `exposure.include: health,info` sigue literal en el yml.
  - (E2.13 — obligatorio) `python3 scripts/ws_echo_test.py` COMPLETO contra
    `http://localhost:8080/ws/logs`, incluida la fase de idle 75 s
    (`--idle-segundos 75` default) → handshake libre, eco idéntico, sin
    cierre por timeout (E5–E7): `/ws/logs` no cayó en la cadena 2 (G2,
    S31).
  - (E2.15) único puerto host 8080 (`podman compose config`: un solo
    `ports:`); `curl http://localhost:8080/` → index placeholder de nginx;
    `curl -H "Accept: application/json"
    http://localhost:8080/api/v1/nonexistent` (RG-8 de C1) → JSON del
    backend; `/actuator/**` invisible desde el host; una ruta no cubierta
    por las 3 cadenas responde según `denyAll` (401 JSON del entry point /
    403, sin contenido — E2.15 admite ambos).
- **Cubre**: S30–S32, E2.11, E2.12, E2.13, E2.15 (+E2/E8/E10 heredados).
- **Depende de**: 5.1 (re-ejecutable tras cualquier fix de F1/F2).
- **Done**: healthchecks estables sin bucle; `env` sin contenido
  sensible; echo WS completo con idle 75 s verde; puerto único e index
  placeholder intactos.

#### 5.8 · CORS con FRONTEND_ORIGIN (E2.17 — E2.18 ya es [L] en 4.4)

- **Acción** (con `FRONTEND_ORIGIN` definida en el `.env`, p. ej.
  `http://<ip>:8080`, y token válido de 5.2):
  - `GET /api/v1/panel/me` con `Origin: <FRONTEND_ORIGIN>` y Bearer → la
    respuesta refleja EXACTAMENTE ese origen en
    `Access-Control-Allow-Origin` (sin `*`), con
    `Access-Control-Allow-Headers` incluyendo `Authorization` y
    `allowCredentials` no activo.
  - Preflight `OPTIONS /api/v1/panel/auth/login` con `Origin` +
    `Access-Control-Request-Method: POST` → **2xx SIN autenticación**
    (queda cubierto por el matcher de la cadena 2).
  - `Origin: http://otro-no-autorizado` → SIN cabeceras CORS de permiso
    (no se refleja el origen no autorizado).
- **Cubre**: S25, E2.17; G1.
- **Depende de**: 5.1 (con `printenv FRONTEND_ORIGIN` confirmado) y 5.2.
- **Done**: origen reflejado exacto solo para `FRONTEND_ORIGIN`; preflight
  2xx sin auth; origen extraño sin cabeceras de permiso.

#### 5.9 · Higiene de cierre y estado del árbol

- **Acción**:
  - `git ls-files` (y grep) → NINGÚN secreto versionado: `.env` NO está en
    el índice (`.gitignore` de C1 intacto); ninguno de los ficheros nuevos
    contiene `JWT_SECRET` con valor, passwords ni tokens (S16/E2.19
    complementario: revisar `podman compose logs backend` de los pasos
    5.2–5.3 — ninguna línea con `Authorization`, `Bearer` ni fragmento de
    JWT).
  - Arbolado: 13 ficheros nuevos en `src/main` ([N1]–[N13]) + 5 de test
    ([T1]–[T5]) + los 3 ficheros modificados de C1; NADA fuera del árbol
    del design §1.1; `git status` limpio tras el último commit.
  - `podman compose logs backend` (nivel INFO de C1) sin "Using generated
    security password" (CD-8a).
- **Cubre**: S16, S27, S28, E2.19 (observación de pila), RG-1.
- **Depende de**: todos los anteriores.
- **Done**: `git ls-files` sin secretos; árbol = design §1.1 1:1; status
  limpio; logs sin token ni usuario aleatorio de Boot.

---

## 3. Commits sugeridos (atómicos, por fase)

| # | Tras task | Mensaje + trailer |
|---|---|---|
| 1 | 1.4 | `feat(security): dependencias y configuración base para la seguridad de C2` |
| 2 | 2.6 | `feat(security): tres cadenas SecurityFilterChain, filtro JWT HS256 y CORS mínimo` |
| 3 | 3.5 | `feat(auth): login y /me con Argon2id y seed idempotente del admin` |
| 4 | 4.6 | `test(backend): tests ligeros de C2 — Argon2, JWT, 401 del panel, CORS e higiene de logs` |
| — | 5.x | F4 no genera commit; fixes → `fix(<área>): <fallo de E2.n>` |

Trailer en TODOS:
`Co-Authored-By: Claude Code <noreply@anthropic.com>`.

## 4. Advertencias para apply (del design §5 y CD-1…CD-9, memorizar antes de escribir)

1. **BC issue spring-security #8842 (crítico, F2/F3)**:
   `Argon2PasswordEncoder` delega en Bouncy Castle, dependencia *optional*
   de spring-security-crypto → sin `bcprov-jdk18on:1.86` en **scope
   runtime** el primer `encode()` en runtime lanza `NoClassDefFoundError`.
   El riesgo se desactiva TEMPRANO: correr 4.1 (E2.20) en cuanto exista
   3.1 (con `-Dtest=Argon2PasswordTest`) antes de seguir con el resto.
   Plan B acotado si E2.20 fallara (S9): `de.mkammerer:argon2-jvm`
   envuelto en un `PasswordEncoder` propio manteniendo el PHC de
   `users.password_hash` IDÉNTICO — la BBDD no cambia en ningún plan.
2. **jjwt 0.13.0: API distinta de 0.12.x** (CD-3): SOLO superficie no
   deprecada — `Jwts.SIG.HS256` para el algoritmo,
   `parser().verifyWith(secretKey).build().parseSignedClaims(token)` para
   verificar, `signWith(secretKey, Jwts.SIG.HS256)` para firmar. **Ningún
   import de `io.jsonwebtoken.impl.*`** en fuentes (S10): la superficie
   compilable es solo `jjwt-api` (impl/jackson en runtime). Cualquier
   desvío de API se detecta en COMPILACIÓN, no en la pila.
3. **Un solo escritor del 401 (CD-4)**: el 401 del panel lo escribe
   EXCLUSIVAMENTE `RestAuthenticationEntryPoint` (literal único
   `invalid_token`, mensaje genérico idéntico); `JwtAuthFilter` NO escribe
   respuesta — deja fallar la regla `authenticated()`. El 401 de login lo
   escribe el controller (`invalid_credentials`). NUNCA un 403 HTML en la
   superficie declarada (entry point también como `accessDeniedHandler` en
   la cadena 1).
4. **`FRONTEND_ORIGIN` en compose es imprescindible para S25/E2.17**
   (hallazgo del design): C1 no pasaba la variable al contenedor; sin la
   línea de 1.3 el env del backend estará siempre vacío ⇒ CORS siempre
   cerrado y E2.17 NUNCA verde aunque `CorsConfig` esté perfecto. Verificar
   con `podman compose exec backend printenv FRONTEND_ORIGIN` (5.1).
5. **COUNT antes de hashear (CD-2)**: el seed lee `SELECT COUNT(*) FROM
   users` y SOLO hashea si la tabla está vacía — reinicio normal = coste 0
   (COUNT + 0 hashes, ~100 ms solo en el primer arranque limpio). La
   segunda barrera es `ON CONFLICT ON CONSTRAINT users_username_unique DO
   NOTHING`. Semántica G4 literal: tabla no vacía → no-acción también si
   `ADMIN_INITIAL_USERNAME` configurado no existe (E2.9).
6. **`/ws/logs` permitAll en la cadena 3 es OBLIGATORIO** (G2): sin esa
   regla explícita (evaluada ANTES del `anyRequest().denyAll()` dentro de
   la MISMA chain), el Echo de C1 moriría con 401 y E2.13 sería rojo. El
   handshake sigue llegando por el nginx intacto (S31).
7. **`anyRequest().denyAll()`** en las cadenas 1 y 3 (CD-1): el estado
   "protegido por defecto" es deliberado — C3–C6 nacen behind 401 (S19).
   NO relajar la cadena 3 para "arreglar" una ruta olvidada; los futuros
   cortes amplían SU cadena.
8. **Token/credenciales JAMÁS en logs (S16/E2.19)**: ni filtro, ni entry
   point, ni `TokenService`, ni controllers loguean el header
   `Authorization`, el token, fragmentos, ni el username/hash del seed.
   El `logging.level` de C1 queda intacto.
9. **INTOCABLES (S29/S31/S32, E2.14)**: `StartupSecretsValidator` +
   `spring.factories` (javadoc incluido), `V1__init.sql`,
   `WebSocketConfig`, `EchoSocketHandler`, `frontend/nginx.conf`, ambos
   Dockerfiles, `.env.example`, `Application.java`, `ws_echo_test.py`.
   Diff vacío exigido; C2 añade CERO migraciones (E3).
10. **`expires_at` determinista (riesgo 7)**: NO usar `Instant.toString()`
    (elide precisión); formatter explícito UTC
    `yyyy-MM-dd'T'HH:mm:ss'Z'` compartido por login y /me (CD-6) — salida
    SIEMPRE con `Z` y precisión de segundos (L2).
11. **Verificación con PODMAN, nunca `docker compose`** (no disponible):
    `podman compose build` / `podman compose up -d --build` /
    `podman compose exec ...`. Sin healthcheck-atajos ni dev: siempre la
    pila completa. Tras cambiar el `compose.yaml`, recrear el contenedor
    (no basta `restart`) para que el env nuevo entre.
12. **Señal de config mal conectada (CD-8a)**: el mensaje "Using generated
    security password" de Boot NO debe aparecer en los logs del backend —
    si aparece, faltan las reglas de 2.5 (sin `httpBasic`/`formLogin` y sin
    `UserDetailsService`, el usuario in-memory queda inerte).
13. **No anticipar C3–C8**: sin controllers de negocio (proyectos/keys/logs
    — nacen 401 bajo la cadena 2, S19), sin mecanismo `X-API-Key` (C5),
    sin auth WS ni heartbeat (C7), sin frontend (C8), sin logout/refresh,
    sin checks por rol (ADR-14), sin rate limiting (429 no existe), sin
    Testcontainers/H2 (C3), sin TLS (ADR-23). El literal `invalid_token`
    queda fijado para este corte (0.2).

## 5. Criterios de salida de fase

- F3 solo está "Done" con `mvn -q -f backend/pom.xml test` verde completo
  (las 5 clases [T1]–[T5]).
- La fase de verificación (F4) SOLO puede declararse "Done" si:
  - los smokes **obligatorios del Done del 07** pasan: E2.1 (login OK),
    E2.2 (login 401 ×3 con literal idéntico), E2.3 (panel 401 sin JWT),
    E2.8 (seed idempotente en reinicio);
  - pasan las **no-regresiones obligatorias**: E2.11 (healthcheck sin
    bucle + health/info 200), E2.13 (`ws_echo_test.py` completo + idle
    75 s), E2.14 (diff vacío del validador + E2.10 fail-fast idéntico);
  - pasan el RESTO de escenarios [S] del mapa del spec §6 en su tarea
    correspondiente: E2.4 [S], E2.5, E2.7, E2.9, E2.12, E2.15, E2.16,
    E2.17, E2.22;
  - y cada uno con la evidencia concreta que lista su sección (código HTTP,
    fila de `users` con PHC, salida del script WS, `printenv`, `git
    ls-files`).
- Cualquier escenario rojo = volver a apply con el fallo concreto (qué task
  lo produce, qué literal se desvió), corregir con un commit de fix mínimo
  (`fix(<área>): <fallo de E2.n>` + trailer §3) y re-ejecutar desde el
  escenario afectado hasta el final de F4.
- Estado final esperado al cerrar: repo con 4 commits de fase (+ fixes si
  los hubo), árbol = 13 ficheros nuevos `src/main` + 5 test + 3 modificados
  de C1, `mvn test` verde, pila podman green con auth activa y `/ws/logs` +
  healthchecks de C1 intactos, NINGÚN fichero fuera del árbol del design
  y NINGÚN secreto versionado.