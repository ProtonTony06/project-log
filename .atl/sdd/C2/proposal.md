---
change: "C2"
artifact: proposal
last_updated: 2026-10-20
status: approved
gate: >
  Aprobado por el usuario 2026-10-20 en los 5 puntos del GATE (con
  recomendaciones): (1) CORS mínimo en C2 — FRONTEND_ORIGIN sobre
  /api/v1/panel/**, sin wildcard; (2) /ws/logs libre/sin auth hasta C7; (3)
  tests spring-boot-starter-test + tests ligeros sin contenedor + smokes
  curl; (4) seed literal Schema §6.3 — tabla no vacía y username del env
  inexistente → no hacer nada; (5) bcprov-jdk18on 1.86 + jjwt 0.13.0, sin
  rangos; literal 401 "invalid_token" cambiable.
---

# Proposal — C2 · Seguridad base + auth del panel

> Fase sdd-propose del flujo SDD para el corte C2 (07, detalle C2).
> Resuelve las decisiones abiertas de `explore.md` §2, propone el plan de
> trabajo y deja listas las preguntas del gate. Los literales de los docs
> se **citan, nunca se redefinen** (regla del 07). Nada de esto se ejecuta
> hasta que el usuario apruebe el gate: no se toca código, no se hacen
> commits, no se crean otros ficheros.
>
> Heredados de C1 (archivado, verify PASS-with-notes): versiones exactas
> SIN prefijos `^`/`~` (RG-1); fail-fast del `StartupSecretsValidator` NO
> se toca (regresión E4 prohibida); `/actuator/health` e `/info` deben
> seguir permitAll (regresión E1/E10 prohibida); `/ws/logs` debe poder
> seguir sirviendo el Echo de C1 hasta C7 (regresión E5–E7 prohibida).

## 1. Objetivo y alcance

**C2**: instalar la capa de seguridad base del monolito y la autenticación
completa del panel, cerrando el estado "sin seguridad" de C1. Done del 07:
login OK y 401 probados como smoke, seed idempotente verificado en reinicio,
y `/api/v1/panel/**` protegido (401 sin JWT).

Entregables concretos:

1. **Dependencias de seguridad en el POM**, versiones exactas sin prefijos
   (RG-1): `spring-boot-starter-security` (versión gestionada por el BOM de
   Boot 3.5.16, como el resto de starters de C1), `org.bouncycastle:
   bcprov-jdk18on:1.86` y jjwt (`jjwt-api` + `jjwt-impl` + `jjwt-jackson`,
   mismo número exacto — ver §2.2), más `spring-boot-starter-test` (scope
   test, si el gate aprueba la opción recomendada de §2.6).
2. **SecurityFilterChain con tres cadenas** (`config`):
   - Cadena 1 `@Order(1)`, matcher `/api/v1/ingest/**` — la de ingesta
     `X-API-Key`: se DECLARA deny-by-default (401) y se USA en C5 (07 C2).
   - Cadena 2 `@Order(2)`, matcher `/api/v1/panel/**` — panel JWT con el
     filtro Bearer y `permitAll` solo en el login exacto (método+ruta).
   - Cadena 3 (resto) — `/actuator/health` e `/actuator/info` permitAll
     (no-regresión E1/E10), `/ws/logs` sin autenticar hasta C7, y
     `anyRequest().denyAll()` (superficie mínima; /actuator/env sigue 404).
   Stateless, CSRF off, sin httpBasic ni formLogin (no aplica a API
   token-based); el user aleatorio que genera Boot queda inerte.
3. **Argon2id para contraseñas** (ADR-3): bean `PasswordEncoder` con
   `Argon2PasswordEncoder` (spring-security-crypto) + Bouncy Castle
   explícito, parámetros OWASP 2024, formato PHC en BBDD (ver §2.1).
4. **JWT HS256 + filtro de validación** (ADR-13): `TokenService` (sign en
   login, parse/verify en el filtro), claims cerrados del Schema §5 (`sub`,
   `role`, `iat`, `exp`), TTL 8 h por `JWT_TTL_HOURS` (defecto 8),
   `expires_at` ISO-8601 UTC con sufijo `Z` (Schema §3.1).
5. **Endpoints de auth, literales del Schema** (§3.2/§3.3, no redefinidos):
   `POST /api/v1/panel/auth/login` `{username, password}` → 200
   `{token, username, expires_at}` · 401 `{"code": "invalid_credentials", …}`;
   `GET /api/v1/panel/me` → 200 `{username, role, expires_at}` · 401.
6. **Seed idempotente del admin**: `ApplicationRunner` que crea el admin
   solo si procede (semántica en §2.4), con `password_hash` = Argon2id del
   `ADMIN_INITIAL_PASSWORD` y `role='ADMIN'`; sin password no arranca
   (lo sigue garantizando el validador de C1, E4, sin tocarlo).
7. **CORS mínimo**, si el gate lo aprueba en C2 (recomendado, ver §2.5):
   solo origen `FRONTEND_ORIGIN`, sobre panel (+ `/ws/logs`), nunca en
   ingesta.

Fuera de alcance explícito (queda para C3–C8):

- **C3/C4/C5/C6**: endpoints de proyectos, API keys, ingesta y consulta —
  en C2 esas rutas nacen protegidas detrás de la cadena correspondiente.
- **C5**: el mecanismo real `X-API-Key` (hash SHA-256, índice O(1),
  comparación en tiempo constante — ADR-4); C2 solo declara la cadena.
- **C7**: auth del WebSocket (`/ws/logs?token=<JWT>` en el handshake,
  ADR-6/TRD §2.8); en C2 `/ws/logs` sigue libre para el Echo de C1.
- **C8**: login frontend (AppFlow flujo 1) y, si se difiere CORS, la config
  CORS completa en dev.
- **Logout y refresh tokens**: NO existen (Schema §5 literal: "Sin refresh
  tokens y sin endpoint de logout: el cierre de sesión es local (el JWT
  sigue válido hasta `expires_at`, aceptado en v1)").
- **Autorización por recurso**: no hay checks por rol (ADR-14; Schema §5:
  "todo lo autenticado por JWT está autorizado").
- **Rate limiting, límites de ingesta, migraciones nuevas**: C2 añade
  **cero migraciones** de Flyway (V1 queda intacto) y ninguna otra pieza.
- **Frontend**: C2 no toca `frontend/` (nota heredada S2 de C1 sobre
  versiones exactas de `package.json` aplica a C8, no aquí).

## 2. Resolución de las decisiones abiertas (explore §2)

### 2.1 Librería Argon2id y versiones exactas

Resuelto: **Opción A de la exploración** — `Argon2PasswordEncoder` de
spring-security-crypto + `org.bouncycastle:bcprov-jdk18on:1.86` explícito.

- Es la vía que ADR-3 nombra ("parámetros OWASP 2024, vía Spring Security").
- BC es dependencia *optional* de spring-security-crypto (issue
  spring-security #8842): sin ella, `NoClassDefFoundError` en runtime — por
  eso se declara en el POM con versión exacta.
- El encoder delega en `Argon2BytesGenerator` de BC, pure-Java sin JNI →
  funciona en `eclipse-temurin:21-jre` (imagen backend de C1) sin
  fragilidad de runtime.
- Los defaults de la fábrica (`defaultsForSpringSecurity_v5_8`: m=16384)
  NO son los OWASP 2024 → se instancia el encoder con constructor explícito:

  `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)`
  → salt 16 bytes, hash 32 bytes, paralelismo p=1, memoria m=19456 KiB,
  iteraciones t=2 (Password Storage Cheat Sheet, OWASP 2024 — como citó la
  exploración). Produce PHC estándar `$argon2id$v=19$m=19456,t=2,p=1$…`,
  que el comentario literal de `users.password_hash` en `V1__init.sql`
  ("Hash Argon2id (formato PHC)") espera.
- El PHC autodescribe sus parámetros: `matches(raw, hash)` seguirá
  funcionando aunque los defaults del bean se ajusten en el futuro.
- Plan B (si algo falla): librería madura alternativa envuelta en un
  `PasswordEncoder` propio manteniendo el formato PHC intacto (ver §5).

Versión verificada en Maven Central (metadatos oficiales, consulta
2026-10-20): última de `bcprov-jdk18on` = **1.86**.

### 2.2 Librería JWT y versión exacta

Resuelto: **jjwt** con versión fija exacta —
`io.jsonwebtoken:jjwt-api:0.13.0` (compile) + `jjwt-impl:0.13.0` y
`jjwt-jackson:0.13.0` (runtime). Última publicada en Maven Central
(metadatos consultados 2026-10-20: lastUpdated 2025-08-20), sin sorpresas:
más de un año de madurez. Coherente con la política de versiones exactas
del POM (el BOM de Boot no gestiona jjwt ni nimbus; nimbus requeriría
arrastrar oauth2-jose — peso OAuth innecesario, descartado en la
exploración).

Detalles operativos decididos aquí:

- **Derivación de la clave HMAC**: los bytes **UTF-8 crudos** del valor de
  `JWT_SECRET`, tal cual los mide la validación de C1 (`≥32 bytes UTF-8`,
  no Base64) → `SecretKeySpec(jwtSecret.getBytes(UTF_8), "HmacSHA256")`.
  Simple y consistente con el validador existente (no se introduce una
  segunda convención).
- **Claims**: exactamente `sub` (username), `role`, `iat`, `exp` — conjunto
  cerrado del Schema §5; la propuesta no añade claims.
- **`exp = iat + TTL`** con TTL leído de `JWT_TTL_HOURS` (default 8);
  `expires_at` de las respuestas = `exp` serializada ISO-8601 UTC `Z`.
- **Verificación por tiempo del servidor** (reloj de la JVM/NTP del host,
  TRD §9). Sin margen de clock-skew: red interna homelab con un solo
  firmante; añadir skew solo amplifica la ventana de validez. Decisión
  documentada; no reabre ADR-13.
- El filtro **nunca loguea** el header `Authorization` ni el token
  (precaución del riesgo 7 de la exploración).
- `jjwt-impl` se declara `runtime` y su API interna no se usa (regla de la
  librería), de modo que la superficie compilable es solo `jjwt-api`.

### 2.3 Literal del `code` del 401 del filtro JWT — DECISIÓN CAMBIABLE

El Schema fija el literal solo para el login (`invalid_credentials`, §3.3)
y exige 401 "con cuerpo estándar" en el filtro (§5), sin fijar su `code`.

**Propuesta: `invalid_token`** — un único literal para los tres casos
(ausente, expirado, inválido), cuerpo estándar `{"code": "invalid_token",
"message": "…"}`. Coherente con el estilo snake_case del Schema
(`invalid_credentials`, `name_duplicate`, `environment_required`,
`timestamp_without_timezone`) y con que TRD §2.5 agrupa "401
(ausente/expirado/inválido)" como un solo error sin subtipos.

⚠ **Este literal es una decisión de esta propuesta que el usuario puede
cambiar en el gate** sin afectar a nada downstream salvo al frontend de C8
(el AppFlow solo reacciona al 401, no al `code`). Alternativas registradas:
`token_required` (solo cuando falta el header) o `unauthorized` (genérico).
Distinguir ausente/malformado/expirado con literales distintos **no se
recomienda**: ayuda a la enumeración.

### 2.4 Semántica del seed idempotente

Resuelto: **el literal del Schema §6.3** — sembrar **solo si la tabla
`users` está vacía**. Si existe cualquier usuario, no se toca nada,
aunque `ADMIN_INITIAL_USERNAME` configurado difiera de los existentes.

- Cuerpo del Schema §6.3 (citado, no redefinido): "si no existe ningún
  `users`, crea el admin con `username` = `ADMIN_INITIAL_USERNAME` y
  `password_hash` = Argon2id(`ADMIN_INITIAL_PASSWORD`)".
- TRD §5 dice "solo si no existe **el usuario**" — divergencia menor ya
  detectada en la exploración (riesgo 8). Con **1 admin en v1** la
  diferencia es irrelevante en la práctica; se toma el literal del Schema
  (condición global sobre la tabla) y se registra la divergencia.
- Consecuencia práctica aceptada: si un despliegue cambia
  `ADMIN_INITIAL_USERNAME` tras tener ya el admin sembrado, el seed no hace
  nada (no crea un segundo admin). Es el comportamiento seguro (sin
  usuarios fantasma), documentado aquí.
- La presencia de credenciales la sigue garantizando E4: el validador de C1
  corre antes del refresh (EnvironmentPostProcessor) y aborta si falta
  `ADMIN_INITIAL_PASSWORD` u otra de las 6 requeridas; el seed NO re-valida
  presencia (solo lee valor y hashea), tal como fija la exploración §1.7.
- Implementación: `ApplicationRunner` en `auth`, después de Flyway
  (orden validador → refresh → Flyway V1 → runner, §1.7 de la exploración),
  con `JdbcTemplate` (spring-boot-starter-jdbc ya presente por D4).
  `INSERT` con `role='ADMIN'` explícito (el DEFAULT del esquema lo cubre;
  fijarlo evita dependencia del default). **Nunca** se hashea en SQL ni en
  Flyway (Schema §6.3 literal: el hash depende de configuración).
- Verificación Done: reinicio de la pila NO duplica ni toca la fila;
  sin password sigue sin arrancar (E4 ya cubierta, no se toca el validador).

### 2.5 CORS — decisión

**Incluir la config CORS mínima en C2** (recomendación adoptada; queda
como pregunta de gate G1 con opción de diferir a C8).

Justificación: el `application.yml` de C1 lo asigna literalmente (comentario
"sin FRONTEND_ORIGIN (CORS es C2)"); TRD §6.3/`.env.example` ya documentan
`FRONTEND_ORIGIN`; el costo es un bean pequeño; y diferirlo obligaría a
retocar la SecurityFilterChain de nuevo en C8 (más riesgo de regresión que
hacerlo ahora con los tests verdes recién puestos).

Forma (si se aprueba): bean de `CorsConfigurationSource` en `config` con
origen único = valor de `FRONTEND_ORIGIN` (nunca `*`, nunca
`allowedOriginPatterns` con comodín), registrado en la cadena del panel
sobre `/api/v1/panel/**` (+ `/ws/logs` si se decide en C7/C8), métodos
GET/POST/PATCH/DELETE/OPTIONS, headers permitidos `Authorization`,
`Content-Type` y `X-Confirm-*` (los últimos ya se reservan para C3 — P1),
con `allowCredentials(false)` (el JWT va por header, no por cookies). Preflight
queda cubierto porque el matcher de la cadena 2 lo alcanza. **La API de
ingesta sin CORS abierto** (TRD §5: "sus clientes son servicios"). Si
`FRONTEND_ORIGIN` no está en el env, no se registra ningún origen (CORS
cerrado por defecto; en producción mismo-origen vía proxy hace la config
inerte, TRD §6.2).

### 2.6 Estrategia de verificación

**Smokes curl-style contra la pila podman** (canon del Done del 07) +
**`spring-boot-starter-test` con tests de integración ligeros** (recomendado
— el comentario literal del POM de C1 dice "los tests llegan en C2"):

- Unit/ligeros sin contenedor ni BBDD (arrancan en segundos, sin Testcontainers):
  1. `TokenService`: sign→parse roundtrip con secreto de test fijo; claims
     exactos (`sub`, `role`, `iat`, `exp`); `exp = iat + TTL`; rechazo de
     firma alterada y de token expirado.
  2. `PasswordEncoder`: `encode()` produce PHC `$argon2id$v=19$m=19456,t=2,p=1$…`;
     `matches()` true/false; salt distinto en dos `encode()` del mismo raw
     (hashes nunca idénticos).
  3. `JwtAuthFilter` vía `@WebMvcTest`/`MockMvc` con la security config
     mockeando el service: 401 con cuerpo `{code: invalid_token}` en los
     tres casos; 200 `/me` con token válido (controller aislado con mock).
- **Los smokes del Done** (curl contra la pila podman levantada, mismo
  método que C1) validan el flujo completo con BBDD real: login OK, login
  401, `/me`, seed idempotente al reinicio, 401 sin JWT, no-regresiones.
- Los tests de integración **con PostgreSQL real** (Testcontainers sobre
  podman) quedan diferidos a C3: primera vez que se necesite BBDD en un
  test; se decide allí el engine/estructura una vez, no en C2.
- `spring-boot-starter-test` se añade con scope `test`, versión del BOM.

### 2.7 Ubicación de beans (paquetes)

Según TRD §2.1 ("`config` (seguridad, CORS, límites, broadcaster)" y
package-by-feature):

- `com.projectlog.config`: `SecurityConfig` (las 3 SecurityFilterChain +
  entry points), `CorsConfig` (si G1 aprueba CORS en C2).
- `com.projectlog.auth`: `PasswordEncoder` (bean con parámetros OWASP),
  `TokenService` (sign/parse jjwt), `JwtAuthFilter`
  (OncePerRequestFilter), `AdminSeeder` (ApplicationRunner),
  `LoginController` + `MeController` + DTOs record
  (`LoginRequest`, `LoginResponse`, `MeResponse`).
- `com.projectlog.common`: si C1 dejó mapeo de errores reutilizable se usa
  (C1 dejó `common` como package-info; en la práctica el cuerpo estándar
  lo escriben el entry point del filtro y el advice del login — pieza
  mínima, sin infraestructura genérica anticipada).
- El 401 de login sale del controller/servicio (literal
  `invalid_credentials`); el 401 del filtro sale del entry point de la
  cadena 2 (literal propuesto `invalid_token`, §2.3).

### 2.8 `/ws/logs` — libre hasta C7

Resuelto: `/ws/logs` queda SIN autenticar en C2 (permitAll en la cadena 3).
El handshake con query-param `?token=` es C7 (ADR-6/TRD §2.8); meterlo ahora
rompería el Echo y el smoke de C1 (`scripts/ws_echo_test.py`, E5–E7) sin
necesidad. Gate G2 formaliza la elección.

## 3. Plan de trabajo por pasos (con verificación observable)

Orden de construcción; cada paso tiene comprobación observable. Los smokes
se corren contra la pila podman completa (`podman compose up -d --build` o
el flujo canónico de C1), con el index placeholder servido por nginx.

1. **POM**: añadir `spring-boot-starter-security`, `bcprov-jdk18on:1.86`,
   jjwt `0.13.0` ×3, `spring-boot-starter-test`. Verificación: `mvn
   -q -f backend/pom.xml dependency:list | grep -E 'bcprov|jjwt|security'`
   muestra versiones exactas y sin rangos; imagen backend se reconstruye OK
   (`mvn package` dentro del build C1, Java 21).
2. **Filtro + SecurityConfig (3 cadenas, sin login aún)**: `JwtAuthFilter`,
   `TokenService`, entry point `{code: invalid_token}`, `denyAll` en el
   resto; `/actuator/health|/info` y `/ws/logs` permitAll; login todavía
   401. Verificación: pila healthy (E1 verde — healthcheck no entra en
   bucle), `/actuator/health` 200 por el proxy, `echo` WS smoke de C1
   OK (E5–E7), `/api/v1/panel/me` → 401, `/api/v1/ingest/lo-que-sea` → 401
   (deny-by-default de la cadena declarada), `/actuator/env` sigue 404
   (E10).
3. **Argon2id**: bean encoder con parámetros OWASP. Verificación: test
   unitario de PHC format + matches (§2.6).
4. **Seed idempotente**: `AdminSeeder` con `JdbcTemplate`, cond. tabla
   vacía. Verificación: primer arranque con `.env` real del despliegue →
   fila en `users` con `password_hash` `argon2id$` y `role=ADMIN`;
   reinicio (compose restart) → 1 fila (no duplicado, mismo id); arranque
   con `.env` sin `ADMIN_INITIAL_PASSWORD` → sigue fallando igual que C1
   (E4, sin tocar el validador).
5. **Login + /me**: `LoginController`/`Servicios` con el encoder del paso 4,
   `TokenService`, errores estándar. Verificación: `POST /auth/login`
   credenciales correctas → 200 `{token, username, expires_at}` con
   `expires_at` ISO-8601 Z; credenciales malas → 401 con
   `{"code":"invalid_credentials",…}` (mismo literal para usuario
   inexistente, password mal y cuerpo malformado — sin filtrar 401
   distinto por usuario inexistente, §1.1 de la exploración); `GET /me`
   con Bearer → 200 `{username, role:"ADMIN", expires_at}`; `GET /me` sin
   token/expirado/inválido → 401 `invalid_token`; cualquier otra ruta de
   `/api/v1/panel/**` → 401 (Done del 07: panel protegido).
6. **CORS** (si G1 = sí): bean CorsConfig + matcher. Verificación: curl con
   `Origin: http://<FRONTEND_ORIGIN>` sobre `/api/v1/panel/me` refleja el
   origen y `Access-Control-Allow-Auth…`/headers correctos; con otro origen
   → sin cabeceras CORS; `OPTIONS /auth/login` preflight → 2xx sin auth.
7. **Verificación final Done + no-regresiones** (checklist de gate):
   - **Done C2 (07)**: login OK y 401 como smoke; seed idempotente en
     reinicio; `/api/v1/panel/**` protegido (401 sin JWT).
   - **E1**: 3 contenedores Up (healthy) estables, sin bucles de
     `restart: unless-stopped` al añadir spring-security.
   - **E2**: único puerto host 8080, index placeholder OK.
   - **E3** (+ E4): en frío Flyway sigue "Schema up to date"; sin
     password/env, fail-fast idéntico (javadoc del validador intacto).
   - **E5–E7**: `ws_echo_test.py` completo + idle 75 s OK (`/ws/logs` sin
     auth según G2).
   - **E8/E9/E10**: proxy `/api/v1` sigue llegando al backend en JSON;
     `/actuator/health` 200, `/info` 200, `/env` 404; `/api/v1/panel/*`
     sin auth → 401 JSON (no el index del SPA, porque va bajo `/api/v1/`).
   - Tests unitarios/ligeros del §2.6 en verde.

## 4. Preguntas para el gate del usuario (máx. 5)

1. **CORS en C2** (explore §2.4 / recomendación §2.5 de esta propuesta):
   (a) incluir la config mínima ahora — solo `FRONTEND_ORIGIN`, sobre
   `/api/v1/panel/**`, sin CORS en ingesta — **recomendado** (el
   `application.yml` de C1 lo asigna a C2 y evita retocar la
   SecurityFilterChain en C8); o (b) diferir íntegramente a C8 (C2 queda
   mismo-origen puro, dev sin CORS disponible hasta C8).
2. **`/ws/logs` en C2** (explore §2.3 / §2.8): (a) dejarlo libre hasta C7 —
   **recomendado** (no romper Echo/smoke E5–E7 de C1; autenticación por
   query-param `?token=` llega con C7 cuando hace falta); o (b) meter ya el
   filtro JWT por query-param (rompería el Echo y adelantaría trabajo de
   C7 sin need).
3. **Tests** (explore §2.7 / §2.6): (a) añadir `spring-boot-starter-test`
   (scope test, versión del BOM) con tests ligeros sin contenedor
   (TokenService roundtrip, Argon2 PHC/matches, filtro 401 con MockMvc) +
   smokes curl para el Done — **recomendado** (el comentario del POM de C1
   dice "los tests llegan en C2" y estos tests no exigen infra extra); o
   (b) solo smokes curl contra la pila podman, dejando starter-test para C3.
4. **Seed con `ADMIN_INITIAL_USERNAME` que no existe pero tabla NO vacía**
   (explore §2.5 / §2.4): (a) no hacer nada (semántica literal
   "tabla-vacía", Schema §6.3) — **recomendado** (1 admin en v1 lo hace
   irrelevante; es el comportamiento seguro); o (b) crear al usuario si su
   username no existe (semántica "por-usuario", TRD §5 — crearía un 2.º
   admin si cambia el env).
5. **Versiones exactas de dependencias nuevas** (§2.1/§2.2, verificadas en
   Maven Central 2026-10-20): (a) `bcprov-jdk18on:1.86` (última estable) +
   jjwt `0.13.0` (última, +1 año de madurez) — **recomendado**; o
   (b) jjwt `0.12.7` (penúltima, la 0.13.0 reorganiza algunos paquetes
   internos si se quiere api aún más conservadora) y/o fijar BC distinto.
   Cualquier opción se fija sin rangos por RG-1.

## 5. Riesgos y rollback

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Argon2id no nativo en Spring Security** (riesgo #1 del 06) — BC es optional y su ausencia rompe en runtime | Opción A lo añade explícito y se prueba en este corte con smoke/test (PHC + matches), no al final. **Plan B**: librería madura alternativa envuelta en un `PasswordEncoder` propio manteniendo el formato PHC (p. ej. `de.mkammerer:argon2-jvm` con JNI empaquetados). El PHC en BBDD no cambia en ningún plan |
| 2 | **Regresión del healthcheck** al añadir spring-security (Boot protege TODO por defecto) → bucle `restart: unless-stopped` (RG-6) | Cadena 3 con `permitAll` explícito para `/actuator/health` e `/actuator/info`; la verificación E1 es paso obligatorio del plan (§3.2) |
| 3 | **Regresión del fail-fast E4** | `StartupSecretsValidator` NO se toca (su javadoc reserva JWT/seed/CORS para C2); C2 no añade ni relaja variables requeridas |
| 4 | **Regresión E5–E7 del Echo WS** | `/ws/logs` permitAll hasta C7 (G2); `ws_echo_test.py` re-ejecutado en cada paso relevante |
| 5 | **`/actuator/env` deja de ser 404** (exposición accidental por defecto de spring-security) | Explicitar como no-regresión E10; `anyRequest().denyAll()` en la cadena 3 y `exposure.include: health,info` intacto |
| 6 | **Dependencias nuevas con versión flotante** (bcprov, jjwt) rompiendo la política RG-1 | Fijadas exactas: `bcprov-jdk18on:1.86`, `jjwt-*:0.13.0`; solo `spring-boot-starter-security/test` sin versión porque el BOM de Boot 3.5.16 ya las fija (mismo patrón que C1) |
| 7 | **Token JWT filtrado en logs** | El filtro/entry point/TokenService nunca loguean `Authorization` ni el token; `logging.level` de C1 intacto |
| 8 | **Divulgación de usuarios** (user enumeration) | 401 idéntico (`invalid_credentials`) para usuario inexistente, password mal y cuerpo malformado; sin filtrar 401 distinto para usuario inexistente (§1.1 de la exploración) |
| 9 | **Versión BC/jjwt con CVE futuro** | Nota: se fijan las vigentes a 2026-10-20; la política de actualización manual de C1 aplica en próximos cortes |
| 10 | **Divergencia semilla Schema §6.3 vs TRD §5** (riesgo 8 de la exploración) | Fijada aquí en §2.4 (literal Schema); no reabre nada más. Registro para una próxima validación de docs que toque el TRD |

**Rollback**: C2 **no añade migraciones** (V1 intacto, esquema de C1) → un
`revert` del/los commits del corte restaura la pila de C1 sin tocar datos ni
volumen (`pgdata` mantiene la fila del admin sembrada — inofensiva para
C1 o para re-aplicar C2, el seed es idempotente). El POM vuelve sin los
starters y el `application.yml` vuelve a su estado C1. No hay dependencia
de datos de C2 para C3+ (C3 solo necesita que el panel esté autenticado).

## 6. Trazabilidad

| Fuente | Qué cubre |
|---|---|
| **07 C2** (alcance/Done/Insumos) | Dos cadenas (panel JWT / ingesta declarada-sin-usar C5); Argon2id (ADR-3); JWT HS256 + filtro TTL 8 h (ADR-13); seed idempotente; `POST /auth/login` (200 `{token, username, expires_at}` · 401) y `GET /me`; Done: login OK + 401 smoke, seed idempotente en reinicio, `/api/v1/panel/**` protegido (401 sin JWT) |
| **TRD §2.1** | Package-by-feature; `config` (seguridad, CORS); capas web/service |
| **TRD §2.5** | Base `/api/v1/panel`; `Authorization: Bearer` en todas las rutas salvo `POST /auth/login`; `POST /auth/login` `{username, password}` → `{token, username, expires_at}` · 401; `GET /me` `{username, role, expires_at}` · 401; errores 401 ausente/expirado/inválido |
| **TRD §3 (ADRs)** | ADR-3 Argon2id vía Spring Security; ADR-13 HS256 + secret env + TTL 8 h + sin refresh; ADR-14 role sin uso real; ADR-15 ingesta por `X-API-Key` |
| **TRD §5** (Seguridad) | Argon2id parámetros OWASP 2024/PHC; JWT HS256 + `JWT_SECRET` ≥32 bytes + TTL 8 h cerrado GATE 2026-10-19 + claims; seed sin credenciales por defecto; CORS restringido al origen del frontend y NUNCA abierto en ingesta |
| **TRD §6.3** | Nombres/roles de `JWT_SECRET`, `JWT_TTL_HOURS` (defecto 8), `ADMIN_INITIAL_*`, `FRONTEND_ORIGIN` — sin cambios de nombres en C2 |
| **TRD §9** | Reloj host NTP-sincronizado = base del tiempo del JWT; rutas proxy; `/actuator/health` |
| **Schema §3.1** | Fechas ISO-8601 UTC con sufijo `Z`; cuerpo de error estándar `{code, message, field?}`; 429 no existe |
| **Schema §3.2/§3.3** | Rutas literales `POST /api/v1/panel/auth/login`, `GET /api/v1/panel/me`; DTOs literales; `401 → {"code": "invalid_credentials", …}`; `role` → `"ADMIN"` |
| **Schema §5** | Credenciales mutuamente exclusivas por superficie; claims JWT cerrados; sin logout/refresh; filtro en todas las rutas del panel salvo login con 401 cuerpo estándar; autorización "todo lo autenticado por JWT está autorizado"; CORS solo dev origin |
| **Schema §6.3** | Seed en código, idempotente, no en SQL de Flyway; solo si no existe ningún `users`; sin otras semillas |
| **Schema §2 `users`** | Unicidad `username` implícita; `password_hash` TEXT NOT NULL PHC; `role` default `ADMIN` |
| **Heredados C1** | `StartupSecretsValidator` intacto (E4); compose healthcheck + `depends_on: service_healthy` + `restart: unless-stopped` (E1, RG-6); Echo WS `/ws/logs` + `ws_echo_test.py` reutilizable en C7 (E5–E7); nginx proxy `/api/v1/` sin URI final (E8); versions exactas RG-1; `.env.example` ya documenta todas las vars de C2 (no se toca salvo, opcionalmente, comentario si CORS cambia de corte) |
| **06 (riesgos)** | Riesgo #1 Argon2id no nativo → plan B en §5; dependencias nuevas fijadas sin rangos |

## GATE: puntos a aprobar por el usuario

1. **CORS**: mínimo en C2 (recomendado — el `application.yml` de C1 lo
   asigna a C2; solo `FRONTEND_ORIGIN`, sobre panel; nunca en ingesta) vs
   diferir a C8.
2. **`/ws/logs`**: libre hasta C7 (recomendado — no romper Echo/smoke de
   C1) vs auth por query-param ya.
3. **Tests**: `spring-boot-starter-test` + tests ligeros sin contenedor
   (TokenService/Argon2/filtro-MockMvc) + smokes curl (recomendado) vs
   solo smokes curl y starter-test en C3.
4. **Seed cuando la tabla NO está vacía y el `ADMIN_INITIAL_USERNAME`
   configurado no existe**: no hacer nada (recomendado, literal
   Schema §6.3) vs crear por-username (TRD §5).
5. **Versiones de dependencias nuevas**: `bcprov-jdk18on:1.86` +
   `jjwt-api/impl/jackson:0.13.0` (recomendado) vs alternativa jjwt
   `0.12.7` / BC distinto (siempre sin rangos).
6. — (implícito de 3): **literal del `code` del 401 del filtro JWT**:
   `invalid_token` único para ausente/expirado/inválido (propuesto en
   §2.3, **cambiable sin coste** salvo compromiso C8 con el frontend).

---

### Fuentes externas (versiones exactas, consultadas 2026-10-20)

- Maven Central `org.bouncycastle:bcprov-jdk18on` — maven-metadata.xml:
  últimas en orden: 1.86, 1.85.2, 1.85, 1.84, 1.83.
- Maven Central `io.jsonwebtoken:jjwt-api` — maven-metadata.xml:
  última `0.13.0` (lastUpdated 2025-08-20); anteriores 0.12.7, 0.12.6, 0.12.5, 0.12.4.
- (De la exploración, aún válidas: spring-security issue #8842 — BC optional;
  Password Storage reference; PasswordEncoderFactories.java; issue #46478 Boot-nimbus.)