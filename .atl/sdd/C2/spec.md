---
change: "C2"
artifact: spec
last_updated: 2026-10-20
status: done
---

# Spec — C2 · Seguridad base + auth del panel

> Especificación normativa del corte C2 para la implementación
> (sdd-design → tasks → apply → qa → verify). Fuente: `proposal.md` (status
> **approved** — gate aprobado por el usuario 2026-10-20, 5/5). Los
> literales de API/BD/errores se **citan** del Backend Schema y del TRD,
> nunca se redefinen (regla del 07). Los heredados de C1 son
> intocables donde se indica.

## 0. Decisiones de gate ya aprobadas (resumen vinculante)

| Gate | Decisión aprobada |
|---|---|
| G1 CORS | Config CORS mínima en C2: solo `FRONTEND_ORIGIN`, sobre `/api/v1/panel/**`, sin wildcard; nunca en ingesta |
| G2 `/ws/logs` | Libre (sin auth) hasta C7; el Echo de C1 no se rompe |
| G3 Tests | `spring-boot-starter-test` (scope test, versión del BOM) + tests ligeros sin contenedor + smokes curl contra la pila podman |
| G4 Seed | Semántica literal Schema §6.3: **tabla-vacía**; tabla no vacía y `ADMIN_INITIAL_USERNAME` inexistente → no hacer nada |
| G5 Versiones | `org.bouncycastle:bcprov-jdk18on:1.86` + `io.jsonwebtoken:jjwt-api/impl/jackson:0.13.0`, sin rangos (RG-1) |

Y, implícita en el gate (aprobada con recomendación): el literal del `code`
del 401 del filtro JWT es **`invalid_token`** (único para
ausente/expirado/inválido; cambiable en el futuro solo afectando al
frontend de C8).

## 1. Requisitos normativos (RFC 2119)

En este documento **MUST/DEBE**, **MUST NOT/NO DEBE**, **SHOULD** y **MAY**
se interpretan según RFC 2119. Cada requisito es verificable por al menos
un escenario de la sección 2.

### 1.A Seguridad base — SecurityFilterChain (3 cadenas)

- **S1** — El backend DEBE definir exactamente **tres** `SecurityFilterChain`
  ordenadas: cadena 1 `@Order(1)` con matcher `/api/v1/ingest/**`; cadena 2
  `@Order(2)` con matcher `/api/v1/panel/**`; cadena 3 (resto, sin matcher
  previo) como última. (Propuesta §1.2; 07 C2 declara "dos cadenas" de
  negocio — panel/ingesta — y la propuesta las materializa en 3, con el
  resto en `denyAll`. Fuente de verdad: esta spec.)

- **S2** — La cadena 1 (ingesta) DEBE aplicar **deny-by-default**: toda
  petición a `/api/v1/ingest/**` DEBE responder **401** en la capa de
  seguridad, sin controller, cuerpo estándar y sin exponer detalles. El
  mecanismo real `X-API-Key` (hash SHA-256, comparación en tiempo
  constante — ADR-4) NO se implementa aquí: la cadena se **declara en C2 y
  se usa en C5** (07 C2). El cuerpo de 401 de esta cadena DEBE seguir el
  formato estándar `{code, message}` (Schema §3.1) y no DEBE distinguir
  subtipos.

- **S3** — La cadena 2 (panel) DEBE dejar público **solo** el login exacto
  `POST /api/v1/panel/auth/login` (método+ruta exactos) y DEBE requerir JWT
  Bearer para **todo** el resto de `/api/v1/panel/**`. Stateless
  (`SessionCreationPolicy.STATELESS`), CSRF deshabilitado (API
  token-based), **sin** `httpBasic` ni `formLogin`: el usuario in-memory
  aleatorio que genera Boot DEBE quedar inerte.

- **S4** — La cadena 3 DEBE dejar `permitAll` únicamente:
  `/actuator/health` e `/actuator/info` (no-regresión E1/E10) y `/ws/logs`
  (sin auth hasta C7, gate G2); y DEBE aplicar `anyRequest().denyAll()`
  para todo lo demás (superficie mínima). En consecuencia
  `/actuator/env` DEBE seguir respondiendo **404** (E10).

- **S5** — El filtro de autenticación del panel (`JwtAuthFilter`,
  `OncePerRequestFilter`) DEBE: extraer el header `Authorization: Bearer
  <jwt>`, verificar firma y vigencia con `TokenService`, poblar el
  `SecurityContext` con `sub` + `role` en el caso válido, y responder
  **401 con cuerpo estándar** `{"code": "invalid_token", "message": "…"}`
  cuando el token esté **ausente, malformado, inválido (firma) o
  expirado** — un único literal `invalid_token` para los tres casos, sin
  subtipos (TRD §2.5 agrupa "401 (ausente/expirado/inválido)" como un solo
  error; distinguir con literales distintos no se recomienda: ayuda a la
  enumeración). Este literal es de la propuesta (Schema §5 exige "401 con
  cuerpo estándar" sin fijar el `code`).

### 1.B Contraseñas — Argon2id (ADR-3)

- **S6** — El hash de contraseñas DEBE usar
  `org.springframework.security.crypto.argon2.Argon2PasswordEncoder`
  (spring-security-crypto) instanciado con **constructor explícito**
  `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)` → salt 16 bytes, hash
  32 bytes, paralelismo `p=1`, memoria `m=19456 KiB`, iteraciones `t=2`
  (OWASP 2024, Password Storage Cheat Sheet). Los defaults de la fábrica
  (`defaultsForSpringSecurity_v5_8`: m=16384) NO son OWASP 2024 y NO DEBEN
  usarse.

- **S7** — El POM DEBE declarar `org.bouncycastle:bcprov-jdk18on:1.86`
  (versión exacta, sin rangos — RG-1). BC es dependencia *optional* de
  spring-security-crypto: sin ella, `NoClassDefFoundError` en runtime
  (issue spring-security #8842).

- **S8** — El hash almacenado en `users.password_hash` DEBE ser el PHC
  estándar `$argon2id$v=19$m=19456,t=2,p=1$…`, que es lo que el comentario
  literal de `V1__init.sql` ("Hash Argon2id (formato PHC)") espera. El PHC
  autodescribe sus parámetros: `matches(raw, hash)` DEBE funcionar
  aunque los defaults del bean cambien en el futuro. La comparación en
  login DEBE hacerse con `encoder.matches(raw, password_hash)` — nunca
  hasheando y comparando cadenas.

- **S9** — Si Opción A fallara en runtime (riesgo #1 del 06), el plan B
  DEBE mantener el **formato PHC intacto** en `users.password_hash`
  (p. ej. `de.mkammerer:argon2-jvm` envuelto en un `PasswordEncoder`
  propio). El PHC en BBDD no cambia en ningún plan.

### 1.C JWT (ADR-13, Schema §5)

- **S10** — El POM DEBE declarar jjwt con versión exacta **0.13.0** en las
  tres piezas: `io.jsonwebtoken:jjwt-api:0.13.0` (compile),
  `jjwt-impl:0.13.0` y `jjwt-jackson:0.13.0` (runtime). Sin rangos (RG-1).
  La superficie compilable DEBE ser solo `jjwt-api`; la API interna de
  `jjwt-impl` NO DEBE usarse (regla de la librería).

- **S11** — El algoritmo DEBE ser **HS256**. La clave HMAC DEBE derivarse
  de los **bytes UTF-8 crudos** del valor de `JWT_SECRET` — tal cual los
  mide el validador de C1 (≥32 bytes UTF-8, no Base64):
  `SecretKeySpec(jwtSecret.getBytes(UTF_8), "HmacSHA256")`. NO se
  introduce una segunda convención de derivación (Base64, HKDF, etc.).

- **S12** — El conjunto de claims DEBE ser el **cerrado del Schema §5**,
  sin añadir ninguno: `sub` (username), `role`, `iat`, `exp`.

- **S13** — `exp` DEBE ser `iat + TTL`, con TTL leído de `JWT_TTL_HOURS`
  (default **8** cuando la variable no está; TRD §6.3). El `expires_at`
  de las respuestas DEBE ser `exp` serializada **ISO-8601 UTC con sufijo
  `Z`** (Schema §3.1).

- **S14** — La validación temporal DEBE usar el reloj del servidor
  (reloj JVM del host NTP-sincronizado, TRD §9) **sin margen de
  clock-skew**: red interna homelab con un único firmante. Decisión
  documentada de la propuesta; no reabre ADR-13.

- **S15** — `TokenService` (paquete `auth`) DEBE encapsular sign (login) y
  parse/verify (filtro). Cambiar `JWT_SECRET` DEBE invalidar los tokens
  emitidos con el secreto anterior (re-login; Schema §5, aceptado en v1).

- **S16** — Ni el filtro, ni el entry point de la cadena 2, ni
  `TokenService` DEBEN loguear el header `Authorization`, el token ni
  ningún fragmento del mismo (precaución riesgo 7 de la exploración). El
  `logging.level` de C1 queda intacto.

### 1.D Endpoints de auth — literales del Schema (§3.2/§3.3)

- **S17** — `POST /api/v1/panel/auth/login`, pública. Entrada
  `{username: string, password: string}`; salida **200**
  `{token: string, username: string, expires_at: string}` (`expires_at`
  ISO-8601 UTC con `Z`; TTL 8 h). **401 →
  `{"code": "invalid_credentials", …}`** con el cuerpo estándar
  `{code, message, field?}` (Schema §3.1). El mismo par 401/literal DEBE
  usarse para: usuario inexistente, password incorrecto y cuerpo
  malformado — **sin** filtrar un 401 distinto para usuario inexistente
  (hardening anti user-enumeration, Schema §3.3/§5).

- **S18** — `GET /api/v1/panel/me`, protegida por JWT. Salida **200**
  `{username: string, role: string ("ADMIN"), expires_at: string}` — rol
  literal `"ADMIN"` (Schema §3.3). **401** sin/expirado/inválido JWT
  (cuerpo de filtro, S5). `role` y `expires_at` DEBEN salir de los claims
  del token (`role`, `exp` → ISO-8601 `Z`); NO DEBE requerir consulta a
  BBDD en el caso normal (decisión menor de la propuesta §2.6: revalidar
  contra DB no está exigido).

- **S19** — Resto de rutas de `/api/v1/panel/**` (que en C2 aún no existen
  como controllers: proyectos, keys, logs — C3–C6) DEBEN responder 401
  desde la cadena 2 ante peticiones sin JWT: el panel **nace protegido**.

- **S20** — NO DEBE existir endpoint de logout, refresh tokens, ni
  checks de autorización por recurso: el cierre de sesión es local (el
  JWT sigue válido hasta `expires_at`, Schema §5). Todo lo autenticado
  por JWT está autorizado (único rol `ADMIN` en v1; ADR-14).

### 1.E Seed idempotente del admin (Schema §6.3)

- **S21** — Un `ApplicationRunner` en el paquete `auth` (orden de arranque:
  validador de secretos → refresh → Flyway aplica V1 → runner) DEBE crear
  el admin **solo si la tabla `users` está vacía**: `username` =
  `ADMIN_INITIAL_USERNAME`, `password_hash` = `encoder.encode(
  ADMIN_INITIAL_PASSWORD)` (Argon2id PHC, S6–S8), `role='ADMIN'`
  explícito en el INSERT (el DEFAULT 'ADMIN' del esquema lo cubre;
  fijarlo evita dependencia del default). Nunca se hashea en SQL ni en
  Flyway (Schema §6.3: "no en SQL de Flyway porque el hash Argon2id
  depende de configuración").

- **S22** — Si la tabla `users` NO está vacía, el runner NO DEBE hacer
  nada — tampoco cuando `ADMIN_INITIAL_USERNAME` configurado no exista
  entre los usuarios (gate G4, literal Schema §6.3: "si no existe ningún
  `users`"). Consecuencia documentada: si un despliegue cambia
  `ADMIN_INITIAL_USERNAME` tras tener el admin sembrado, no se crea un
  segundo admin. Divergencia Schema §6.3 ("ningún `users`") vs TRD §5
  ("el usuario") registrada: se toma el literal del Schema.

- **S23** — El runner NO DEBE re-validar presencia de credenciales (el
  validador de C1 ya lo garantiza antes del refresh — E4): se limita a
  leer valor y hashear. "Sin `ADMIN_INITIAL_PASSWORD` → no arranca" lo
  sigue cumpliendo E4 con el `StartupSecretsValidator` **intocable**.
  Acceso a datos vía `JdbcTemplate` (spring-boot-starter-jdbc ya presente
  por D4 de C1).

- **S24** — No hay otras semillas: ni proyectos ni logs iniciales
  (Schema §6.3). C2 NO DEBE añadir ninguna migración de Flyway (V1 queda
  intacto, esquema de C1).

### 1.F CORS (gate G1)

- **S25** — Debe existir un bean `CorsConfigurationSource` en `config`
  que registre **exclusivamente** el origen con valor de `FRONTEND_ORIGIN`
  (nunca `*`, nunca `allowedOriginPatterns` con comodín), aplicado en la
  cadena 2 sobre `/api/v1/panel/**`; métodos GET/POST/PATCH/DELETE/OPTIONS;
  headers permitidos `Authorization`, `Content-Type` y `X-Confirm-*`
  (reservados para C3 — P1); `allowCredentials(false)` (el JWT va por
  header, no cookies). El preflight queda cubierto porque el matcher de la
  cadena 2 lo alcanza. `/ws/logs` se decide en C7/C8.

- **S26** — Si `FRONTEND_ORIGIN` no está en el env, NO DEBE registrarse
  ningún origen: **CORS cerrado por defecto** (en producción mismo-origen
  vía proxy hace la config inerte — TRD §6.2). La API de ingesta NO DEBE
  tener CORS abierto (TRD §5: "sus clientes son servicios").

### 1.G Dependencias y paquetes

- **S27** — El POM DEBE añadir, además de S7/S10:
  `spring-boot-starter-security` y `spring-boot-starter-test` (scope
  test) sin versión explícita — las gestiona el BOM de Boot 3.5.16, como
  el resto de starters de C1. Ninguna versión nueva con rango o flotante
  (RG-1).

- **S28** — Colocación de beans (TRD §2.1, package-by-feature):
  `com.projectlog.config` → `SecurityConfig` (3 cadenas + entry points) y
  `CorsConfig` (S25); `com.projectlog.auth` → bean `PasswordEncoder` (S6),
  `TokenService` (S15), `JwtAuthFilter` (S5), `AdminSeeder` (S21),
  `LoginController`/`MeController` + DTOs record (`LoginRequest`,
  `LoginResponse`, `MeResponse`). El cuerpo 401 de filtro lo escribe el
  entry point de la cadena 2; el 401 de login, el controller/servicio
  (S17) — sin infraestructura genérica anticipada en `common`.

### 1.H Heredados de C1 — no-regresiones obligatorias

- **S29** — El `StartupSecretsValidator` (EnvironmentPostProcessor,
  `META-INF/spring.factories`) DEBE quedar **intocable** (javadoc
  incluido): regresión E4 prohibida. C2 no añade ni relaja variables
  requeridas.
- **S30** — `/actuator/health` e `/info` DEBEN seguir `permitAll` y la
  pila DEBE seguir estable sin bucles de `restart: unless-stopped`
  (regresión E1 prohibida, RG-6). Un único puerto host 8080 (E2).
- **S31** — `/ws/logs` DEBE seguir sirviendo el Echo de C1
  (`scripts/ws_echo_test.py`) hasta C7: idle 75 s OK (E5–E7). El
  handshake con query-param `?token=` es C7 (ADR-6/TRD §2.8).
- **S32** — El proxy nginx DEBE seguir enrutando `/api/v1/` al backend en
  JSON (E8) y `/actuator/env` DEBE seguir 404 (E10). En frío, Flyway
  sigue "Schema up to date" (E3) — C2 añade cero migraciones.

## 2. Escenarios de aceptación

Numeración E2.x (corte C2, escenario x). Cada uno indica su tipo de
verificación: **[L]** = test ligero sin contenedor (JUnit +
spring-boot-starter-test, sin BBDD, segundos) · **[S]** = smoke curl
contra la pila podman completa (mismo método canónico de C1).

### Autenticación del panel

- **E2.1 — Login correcto [S]** — Given: pila levantada con `.env` real
  del despliegue y admin sembrado. When: `POST /api/v1/panel/auth/login`
  con las credenciales correctas. Then: **200** con
  `{token, username, expires_at}`; `token` es un JWT de 3 segmentos con
  HS256; `expires_at` coincide con formato ISO-8601 UTC con sufijo `Z` y
  representa iat+TTL (≈8 h o el valor de `JWT_TTL_HOURS`).

- **E2.2 — Login 401 [S]** — Given: mismo estado. When: login con
  password incorrecto; con username inexistente; y con cuerpo malformado
  (tres peticiones). Then: cada una responde **401** con
  `{"code": "invalid_credentials", …}` — literal idéntico en los tres
  casos (sin user-enumeration).

- **E2.3 — Panel protegido sin JWT [S]** — Given: pila levantada. When:
  `GET /api/v1/panel/me` sin header `Authorization`; y `POST`/`GET`
  contra otra ruta cualquiera de `/api/v1/panel/**` sin token. Then:
  **401** con `{"code": "invalid_token", …}` en todos los casos.

- **E2.4 — 401 con token inválido/expirado [L+S]** — When/L: `MockMvc`
  (`@WebMvcTest` con `TokenService` mockeado): header Bearer con firma
  alterada → 401 `{code: "invalid_token"}`; token expirado (generado con
  TTL forzado bajo/expirado por construcción) → 401
  `{code: "invalid_token"}`. Then/S: confirmar contra la pila con un
  token firmado de ejemplo manipulado → 401.

- **E2.5 — GET /me con JWT válido [S]** — Given: token emitido por
  E2.1. When: `GET /api/v1/panel/me` con `Authorization: Bearer <jwt>`.
  Then: **200** `{username, role: "ADMIN", expires_at}` con los valores
  del token; `expires_at` idéntico formato ISO-8601 `Z`.

- **E2.6 — GET /me sin JWT [L]** — When: `MockMvc` sin header. Then:
  **401** `{code: "invalid_token"}` (cuerpo JSON estándar, no HTML).

### Seed idempotente

- **E2.7 — Seed en arranque limpio [S]** — Given: volumen `pgdata`
  limpio (sin filas en `users`) y `.env` con `ADMIN_INITIAL_*`. When:
  primer arranque de la pila (validador → Flyway V1 → runner). Then: la
  tabla `users` contiene **1 fila** con `username` = env,
  `password_hash` que empieza por `$argon2id$v=19$m=19456,t=2,p=1$…` y
  `role='ADMIN'`. Login (E2.1) con esas credenciales funciona.

- **E2.8 — Reinicio no duplica [S]** — Given: E2.7 ejecutado. When:
  `podman compose restart` (o down/up sin `-v`) y espera a healthy.
  Then: la tabla `users` sigue teniendo **exactamente 1 fila**, mismo
  `id` — idempotente ante reinicios y repeticiones.

- **E2.9 — Tabla no vacía + username del env inexistente → no hace
  nada [S]** — Given: la tabla `users` tiene el admin de E2.7, y se
  reinicia la pila con un `ADMIN_INITIAL_USERNAME` distinto en el env.
  Then: NO se crea ninguna fila nueva (sigue 1 fila, gate G4), no hay
  error de arranque y la fila original queda intacta.

- **E2.10 — Sin password sigue sin arrancar [S]** — Given: `.env` sin
  `ADMIN_INITIAL_PASSWORD` (u otra requerida). When: arranque de la
  pila. Then: fail-fast **idéntico a C1** (E4): el backend no arranca y
  el mensaje proviene del validador intacto — el seed NO reintenta ni
  re-valida presencia.

### No-regresiones sobre C1

- **E2.11 — Healthcheck sin bucle [S]** — Given: pila levantada con la
  security añadida. When: observar los 3 contenedores durante el
  intervalo de varios healthchecks (30 s cada uno). Then: E1 — los 3
  contenedores `Up (healthy)` estables, sin entradas en bucle de
  `restart: unless-stopped`; `GET /actuator/health` → 200 y
  `/actuator/info` → 200 a través del proxy.

- **E2.12 — /actuator/env sigue 404 [S]** — When: `GET /actuator/env`
  por el proxy. Then: **404** (E10), nunca contenido de configuración;
  `exposure.include: health,info` intacto.

- **E2.13 — /ws/logs sigue sirviendo el Echo [S]** — When:
  `scripts/ws_echo_test.py` completo contra `/ws/logs`, incluida la fase
  de idle 75 s. Then: E5–E7 verdes (handshake libre, echo, sin cierre
  por timeout) — `/ws/logs` no quedó dentro de la cadena 2 del panel.

- **E2.14 — Fail-fast intacto [L+S]** — Given: arranque sin los secretos
  requeridos. Then/L: la battery de tests de C1 del validador (si la
  hay) sigue en verde y su javadoc/firma no cambió (diff vacío sobre el
  validador). Then/S: E2.10 observa el mismo mensaje y código de salida
  que en C1.

- **E2.15 — Proxy y ruta única de host [S]** — Then: E2/E8 — único
  puerto host 8080 con el index placeholder servido por nginx;
  `/api/v1/**` llega al backend en JSON; una ruta inexistente bajo
  `/api/v1/panel/` responde 401 JSON del filtro (no el index del SPA,
  porque va bajo `/api/v1/`); una ruta no cubierta por las 3 cadenas
  responde según `denyAll` (401/403, sin contenido).

### Ingesta declarada (deny-by-default)

- **E2.16 — Ingesta 401 hasta C5 [S]** — When: `POST /api/v1/ingest` y
  `/api/v1/ingest/batch` con cualquier cuerpo, con y sin header
  `X-API-Key`. Then: **401** en la capa de seguridad (nunca 404 ni 2xx):
  la cadena 1 está declarada deny-by-default; el mecanismo real de
  ingesta llega con C5.

### CORS (gate G1)

- **E2.17 — CORS con FRONTEND_ORIGIN configurado [S]** — Given: env con
  `FRONTEND_ORIGIN=http://<ip>:8080`. When: `GET /api/v1/panel/me` con
  `Origin: <FRONTEND_ORIGIN>` (con token válido) y
  `OPTIONS /api/v1/panel/auth/login` preflight. Then: la respuesta
  refleja el origen configurado y las cabeceras CORS correctas (incl.
  `Access-Control-Allow-Headers` para `Authorization`); el preflight
  responde 2xx **sin autenticación**; con un `Origin` distinto → sin
  cabeceras CORS de permiso (no refleja el origen no autorizado).

- **E2.18 — CORS cerrado sin FRONTEND_ORIGIN [L]** — Given: contexto sin
  la variable. When: preflight/GET con cualquier `Origin`. Then: no se
  registra origen alguno (CORS cerrado por defecto, S26); la petición
  same-origin sigue funcionando sin cabeceras CORS.

### Higiene y calidad

- **E2.19 — Token nunca en logs [L]** — Given: una petición de panel con
  token inválido y una de login. When: revisar la salida de log del
  backend (nivel INFO de C1) producida por filtro/entry
  point/TokenService. Then: ninguna línea contiene el header
  `Authorization`, el JWT ni un fragmento del mismo.

- **E2.20 — PHC y Argon2id correctos [L]** — When: `encoder.encode(raw)`
  (doble encoding) y `encoder.matches(raw, hash)` / `matches(otra, hash)`.
  Then: el PHC producido es `$argon2id$v=19$m=19456,t=2,p=1$…`; dos
  `encode()` del mismo raw producen hashes **diferentes** (salt
  aleatorio); `matches` → true con el raw correcto y false con uno
  incorrecto. Plan B (S9) solo si esto falla → el PHC se mantiene igual.

- **E2.21 — TokenService roundtrip y claims [L]** — When: sign con
  secreto de test fijo (≥32 bytes UTF-8) → parse/verify. Then: claims
  exactos `{sub, role, iat, exp}` y **solo** esos; `exp = iat +
  TTL` (con `JWT_TTL_HOURS` default 8 y con un TTL configurado
  alterno); un token con firma alterada y otro expirado se rechazan en
  el parse.

- **E2.22 — Semilla respeta el esquema [S]** — Given: fila de E2.7.
  When: inspeccionar `users`. Then: cumple `V1__init.sql` aplicado
  (`username` TEXT NOT NULL UNIQUE —
  `users_username_unique`; `password_hash` TEXT NOT NULL PHC;
  `role` NOT NULL DEFAULT 'ADMIN' — fijado explícito en el INSERT;
  `created_at`/`updated_at` NOT NULL por default) y no existen más
  semillas (S24).

## 3. Contratos citados (fuente única de verdad)

| Contrato | Contenido citado | Fuente |
|---|---|---|
| Rutas de auth | `POST /api/v1/panel/auth/login` (pública) · `GET /api/v1/panel/me` (JWT) — únicos endpoints de panel en C2 | Schema §3.2; TRD §2.5 |
| Cuerpos login | entrada `{username, password}` → 200 `{token, username, expires_at}` · 401 `{"code": "invalid_credentials", …}` | Schema §3.3; TRD §2.5 |
| Cuerpo /me | 200 `{username, role: "ADMIN", expires_at}` · 401 | Schema §3.3; TRD §2.5 |
| Errores | cuerpo estándar `{code, message, field?}`; fechas ISO-8601 UTC con `Z`; 429 no existe | Schema §3.1 |
| JWT | HS256; `JWT_SECRET` ≥32 bytes aleatorios por env; TTL 8 h (`JWT_TTL_HOURS`, GATE 2026-10-19); claims `sub, role, iat, exp` (conjunto cerrado); sin refresh/logout; filtro en todas las rutas del panel salvo `/auth/login`; 401 con cuerpo estándar | Schema §5; TRD ADR-13, §5, §6.3 |
| Credenciales por superficie | panel/socket = `Bearer <jwt>`; ingesta = `X-API-Key`; jamás cruzadas | Schema §5; TRD ADR-15 |
| Contraseñas | Argon2id, parámetros OWASP 2024, vía Spring Security, formato PHC en BBDD | TRD ADR-3, §5; Schema §5 |
| Tabla `users` | `id BIGINT IDENTITY PK`; `username` TEXT NOT NULL UNIQUE (`users_username_unique`); `password_hash` TEXT NOT NULL — "Hash Argon2id (formato PHC)"; `role` TEXT NOT NULL DEFAULT 'ADMIN' (sin uso real en v1, ADR-14); `created_at`/`updated_at` TIMESTAMPTZ NOT NULL | `V1__init.sql` (aplicada en C1, E3) + Schema §2/§6.1 |
| Seed | "si no existe ningún `users`, crea el admin con `username` = `ADMIN_INITIAL_USERNAME` y `password_hash` = Argon2id(`ADMIN_INITIAL_PASSWORD`)"; idempotente; por código, no en SQL de Flyway; sin otras semillas; sin credenciales por defecto | Schema §6.3 (divergencia con TRD §5 "el usuario" — resuelta por gate G4 a favor del Schema) |
| Env | `JWT_SECRET`, `JWT_TTL_HOURS` (defecto 8), `ADMIN_INITIAL_USERNAME`/`ADMIN_INITIAL_PASSWORD`, `FRONTEND_ORIGIN` — nombres sin cambios en C2 | TRD §6.3 (tabla = autoridad) |
| Rutas/protección | base `/api/v1/panel`; `Authorization: Bearer` salvo `POST /auth/login`; 401 (ausente/expirado/inválido) como un solo error; proxy enruta `/api/v1/**` + `/ws/logs`; healthcheck `/actuator/health`; reloj host NTP = base del tiempo del JWT | TRD §2.5, §9 |
| Paquetes | package-by-feature: `auth`, … + `config` (seguridad, CORS, …); capas web/service/repository | TRD §2.1 |
| ADRs | ADR-3 (Argon2id), ADR-13 (JWT HS256/TTL 8 h/sin refresh), ADR-14 (role sin uso real), ADR-15 (ingesta por `X-API-Key`) | TRD §3 |
| CORS | restricción estricta al origen del frontend; en prod mismo-origen por proxy; ingesta sin CORS abierto | TRD §5; Schema §5 |

## 4. Fuera de alcance (explícito)

- **C3/C4/C6** — endpoints de proyectos, API keys y consulta: nacen como
  401 bajo la cadena 2 sin controllers; no se implementan aquí.
- **C5** — el mecanismo real de ingesta `X-API-Key` (hash SHA-256, índice
  `projects_api_key_hash_idx` O(1), comparación en tiempo constante
  `MessageDigest.isEqual`, 400/403/413/202): C2 solo declara la cadena
  deny-by-default (S2/E2.16).
- **C7** — auth del WebSocket (`/ws/logs?token=<JWT>` en el handshake,
  ADR-6/TRD §2.8) y rechazo 401 pre-upgrade: `/ws/logs` queda libre
  (S31/E2.13).
- **C8** — login desde el frontend, router guard con `/me`, y — solo si
  se difiriera CORS — config CORS completa en dev (con G1 aprobado, no
  hace falta en C8).
- **Logout y refresh tokens** — no existen (Schema §5 literal: el JWT
  sigue válido hasta `expires_at`; cierre de sesión local).
- **Autorización por recurso** — sin checks por rol (ADR-14; todo lo
  autenticado por JWT está autorizado).
- **Rate limiting / límites por ventana** — no en v1 (429 no existe,
  Schema §3.1); los límites por petición de ingesta son de C5.
- **Migraciones** — cero: V1 queda intacto; ninguna otra pieza de BD.
- **TLS / dominio** — HTTP plano en LAN (ADR-23); no en C2.
- **Frontend** — C2 no toca `frontend/`; la nota heredada S2 de C1 sobre
  versiones exactas de `package.json` aplica a C8, no aquí.
- **Testcontainers/BBDD en tests** — diferidos a C3 (primera vez que un
  test necesite PostgreSQL; allí se decide engine/estructura una vez).

## 5. Trazabilidad

| Fuente | Qué cubre en este spec |
|---|---|
| **07 C2** (alcance) | Dos cadenas de negocio (panel JWT / ingesta declarada sin usar hasta C5) → S1–S3, S22, E2.16; Argon2id (ADR-3) → S6–S9; JWT HS256 + filtro TTL 8 h (ADR-13) → S10–S16; seed idempotente → S21–S24; `POST /auth/login` y `GET /me` → S17–S18 |
| **07 C2** (Done cuando) | "login OK y 401 probados como smoke" → E2.1/E2.2; "seed idempotente verificado en reinicio" → E2.7/E2.8/E2.9; "`/api/v1/panel/**` protegido (401 sin JWT)" → E2.3/E2.16/E2.19 |
| **Gate 2026-10-20 (5/5)** | G1 CORS → S25/S26, E2.17/E2.18 · G2 `/ws/logs` libre → S31, E2.13 · G3 tests → §6 (mapa de verificación) · G4 seed tabla-vacía → S22, E2.9 · G5 versiones → S7/S10/S27 |
| **Heredados C1** | `StartupSecretsValidator` intacto (E4) → S29/E2.10/E2.14 · healthcheck+stability (E1, RG-6) → S30/E2.11 · Echo WS + idle 75 s (E5–E7) → S31/E2.13 · proxy `/api/v1` sin URI final (E8) → S32/E2.15 · versiones exactas RG-1 → S7/S10/S27 · `/actuator/env` 404 (E10) → S4/E2.12 · Flyway "Schema up to date" (E3) → S24/E2.22 |
| **06 (riesgos)** | Riesgo #1 "Argon2id no nativo en Spring Security — C2 (si falla: librería madura, formato PHC intacto)": probado EN este corte con E2.20 (PHC+matches), plan B S9 (`de.mkammerer:argon2-jvm`) manteniendo el PHC de `users.password_hash` idéntico |
| **Riesgo 8 explore / divergencia docs** | Seed Schema §6.3 vs TRD §5 → resuelto por gate G4 (Schema), registrado para la próxima validación de docs que toque el TRD |

## 6. Mapa de verificación

| Escenario | Verificación | Tipo |
|---|---|---|
| E2.1 | Smoke curl: login OK → 200 con token/expires_at | [S] podman |
| E2.2 | Smoke curl: login 401 ×3 (password mal, usuario inexistente, cuerpo mal) | [S] podman |
| E2.3 | Smoke curl: `/api/v1/panel/**` sin JWT → 401 `invalid_token` | [S] podman |
| E2.4 | `@WebMvcTest`/MockMvc con `TokenService` mockeado: firma alterada + expirado → 401; confirmación smoke | [L]+[S] |
| E2.5 | Smoke curl: `/me` con Bearer → 200 `{username, role:"ADMIN", expires_at}` | [S] podman |
| E2.6 | MockMvc: `/me` sin header → 401 JSON estándar | [L] |
| E2.7 | Smoke: arranque limpio → 1 fila `users` con PHC `argon2id$` y `role=ADMIN`; login funciona | [S] podman |
| E2.8 | Smoke: `compose restart` → sigue 1 fila, mismo `id` | [S] podman |
| E2.9 | Smoke: restart con `ADMIN_INITIAL_USERNAME` distinto → sin fila nueva, sin error | [S] podman |
| E2.10 | Smoke: `.env` sin password → fail-fast idéntico C1 (validador intacto) | [S] podman |
| E2.11 | Smoke: 3 contenedores `Up (healthy)` estables; health/info 200 por proxy | [S] podman |
| E2.12 | Smoke: `/actuator/env` → 404 | [S] podman |
| E2.13 | Smoke: `ws_echo_test.py` completo + idle 75 s | [S] podman |
| E2.14 | Ligero: diff vacío del validador + (si existe) test de C1 en verde; smoke E2.10 | [L]+[S] |
| E2.15 | Smoke: puerto único 8080, index placeholder, `/api/v1/**` JSON en proxy, denyAll | [S] podman |
| E2.16 | Smoke: `POST /api/v1/ingest[/**]` con y sin `X-API-Key` → 401 | [S] podman |
| E2.17 | Smoke: curl con `Origin: FRONTEND_ORIGIN` sobre `/me` y preflight `OPTIONS /auth/login` | [S] podman |
| E2.18 | Ligero: contexto sin `FRONTEND_ORIGIN` → sin origen registrado (CORS cerrado) | [L] |
| E2.19 | Ligero: aserción sobre salida de log del filtro/entry point/TokenService (sin `Authorization` ni JWT) | [L] |
| E2.20 | Unit: PHC `$argon2id$v=19$m=19456,t=2,p=1$…`, `matches` true/false, salts distintos | [L] |
| E2.21 | Unit: roundtrip sign→parse HS256; claims exactos `{sub, role, iat, exp}`; exp=iat+TTL (default 8 y TTL alterno); rechazo firma alterada y expirado | [L] |
| E2.22 | Smoke: inspección de `users` vs `V1__init.sql` aplicado | [S] podman |

Reglas del mapa: los tests [L] usan `spring-boot-starter-test` (scope
test, BOM) **sin contenedor ni BBDD** (arrancan en segundos; sin
Testcontainers — diferido a C3). Los smokes [S] se corren contra la pila
podman completa (`podman compose up -d --build` o el flujo canónico de
C1), con el index placeholder de nginx. Los Done literales del 07 (E2.1,
E2.2, E2.8, E2.3) son smokes obligatorios; E2.11/E2.13/E2.14 son pasos
de no-regresión obligatorios en cada paso relevante del plan.