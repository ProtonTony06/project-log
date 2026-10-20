---
change: "C2"
artifact: explore
project: "Project_Log"
last_updated: 2026-10-20
status: done
---

# Explore — C2 · Seguridad base + auth del panel

> Exploración del punto de partida del repo tras C1 (verify PASS-with-notes)
> frente a los docs de definición. Solo lectura: nada del repo se ha
> modificado. Artículo único de esta fase.

## 1. Decisiones YA cerradas (con ancla exacta)

Lo siguiente no se reabre en la propuesta; se cita como contrato:

### 1.1 Endpoints y contratos de auth (Schema §3.2 / §3.3)

- Rutas exactas: `POST /api/v1/panel/auth/login` (pública) y
  `GET /api/v1/panel/me` (JWT). El resto de `/api/v1/panel/**` queda fuera de
  C2 (C3–C6) y ya nace protegido.
- **LoginRequest**: `{username: string, password: string}`.
- **LoginResponse** 200: `{token, username, expires_at}` — `expires_at`
  ISO-8601 UTC con sufijo `Z` (Schema §3.1); TTL 8 h.
- **401 login** → `{"code": "invalid_credentials", …}` (literal §3.3; cuerpo
  de error estándar `{code, message, field?}` de §3.1). 401 se aplica a
  usuario inexistente, password mal y genérico (no filtrar 401 distinto para
  usuario inexistente es decisión de hardening razonable; el literal no
  distingue).
- **GET /me** 200 → `{username: string, role: string ("ADMIN"), expires_at:
  string}` (§3.3; rol literal `"ADMIN"`). 401 sin/expirado/inválido JWT.
  Objetivo del flujo AppFlow flujo 1: restaurar sesión al arrancar el panel.
- Sin logout ni refresh tokens (Schema §5): el cierre de sesión es local
  (válido hasta `expires_at`).

### 1.2 JWT (TRD ADR-13, Schema §5)

- HS256; secreto `JWT_SECRET` ≥ 32 bytes aleatorios por env, jamás en código;
  cambiarlo invalida los tokens (re-login, aceptable con admin único).
- TTL **8 h** confirmado (GATE 2026-10-19), configurable vía `JWT_TTL_HOURS`
  (defecto 8, TRD §6.3).
- **Claims literales (Schema §5): `sub` (username), `role`, `iat`, `exp`.
  Es el conjunto cerrado — la propuesta no añade claims.**
- Sin refresh; sin endpoint de logout; firmado solo por el backend.

### 1.3 Contraseñas (TRD ADR-3, TRD §5, Schema §5 / §2 `users`)

- **Argon2id, parámetros OWASP 2024, "vía Spring Security", formato PHC en
  BBDD** (TRD §5). Login ~500 ms asumible (1 admin). La ingesta NO pasa por
  Argon2 (SHA-256 rápido es ADR-4, asunto de C4/C5).
- `users.password_hash TEXT NOT NULL` — "Hash **Argon2id** (formato PHC), jamás
  texto plano" (comentario literal de `V1__init.sql` línea 6).
- Parámetros OWASP 2024 (Password Storage Cheat Sheet) para Argon2id:
  `m=19456 KiB, t=2 iterations, p=1` (salt 16 bytes, hash 32 bytes). Ver §2.1.

### 1.4 users / roles del V1__init.sql (aplicado en C1, E3)

```sql
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,                 -- Argon2id (formato PHC)
    role          TEXT        NOT NULL DEFAULT 'ADMIN', -- sin uso real en v1 (P2)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_username_unique UNIQUE (username)
);
```

- Unicidad por `users_username_unique` (índice implícito, Schema §2). No hay
  índice adicional para users.
- `role`: presente sin uso real en v1 (ADR-14); autorización = "todo lo
  autenticado por JWT está autorizado" (Schema §5); sin checks por recurso.
- Sin registro público ni auto-alta; un único usuario.

### 1.5 Env relevante (TRD §6.3, tabla = autoridad de nombres)

| Variable | Papel en C2 | Nota |
|---|---|---|
| `JWT_SECRET` | Firma/verificación HS256 | Ya exigida y validada (≥32 bytes UTF-8) por el validador de C1 |
| `JWT_TTL_HOURS` | Expiración (defecto 8) | Con default → NO la exige el validador (`REQUERIDAS` de C1 lista 6 variables: datasource ×3, JWT_SECRET, ADMIN_×2) |
| `ADMIN_INITIAL_USERNAME` / `ADMIN_INITIAL_PASSWORD` | Seed idempotente | Sin default en producción; sin credenciales por defecto (TRD §5) |
| `FRONTEND_ORIGIN` | Origen CORS (dev) | Con default ausente del validador; comentario de `application.yml` C1: «CORS es C2» (ver §2.4) |

- `.env.example` de C1 ya documenta todas con comentarios correctos (incluido
  «Generar: openssl rand -base64 48» para JWT_SECRET), alineado con la tabla
  del §6.3.

### 1.6 Qué cae bajo `/api/v1/panel/**` y el proxy

- TRD §2.5: base `/api/v1/panel`, `Authorization: Bearer <jwt>` en todas las
  rutas salvo `POST /auth/login`. La tabla §3.2 del Schema es la fuente de
  rutas; en C2 solo existen login y me (C3–C6 añaden el resto).
- Nginx enruta `/api/v1/` completo al backend (proxy_pass sin URI final,
  E8) → todo `/api/v1/panel/**` e `/api/v1/ingest/**` llegan al
  SecurityFilterChain del backend. `/ws/logs` también llega al backend pero es
  handshake de C7 (ver §2.3).
- El healthcheck del backend (`GET /actuator/health` 200 cada 30 s, compose)
  NO puede romperse al añadir spring-security (riesgo R2 en §3).

### 1.7 Interacción fail-fast ↔ seed (StartupSecretsValidator de C1)

- El validador (EnvironmentPostProcessor, `META-INF/spring.factories`) corre
  en el punto MÁS TEMPRANO del arranque, antes del refresh: DataSource,
  Flyway, Tomcat y beans aún no existen. Su javadoc dice literalmente:
  «firma JWT, seed del admin y SecurityFilterChain son C2 y NO se anticipan
  aquí» — es decir, C2 es el corte previsto para esta pieza, sin tocar el
  validador (regresión E4 prohibida).
- Orden resultante: (1) EnvironmentPostProcessor valida presencia/no-blanco de
  las 6 variables + ≥32 bytes de JWT_SECRET → (2) refresh del contexto:
  DataSource + Flyway aplica V1 → (3) ApplicationRunner del seed: lee env (ya
  garantizada presente y no-blanca) y verifica `users`. Consecuencia: el seed
  NO necesita re-validar presencia; puede limitarse a leer valor y hashear.
  «Sin `ADMIN_INITIAL_PASSWORD` → no arranca» ya lo cumple E4.

## 2. Puntos de diseño ABIERTOS (los decide la propuesta)

### 2.1 Librería/mecanismo Argon2id en Spring Boot 3.5.16 / Java 21 (riesgo #1 del 06)

Estado real (verificado contra docs/emisores oficiales, fuentes al final):

- **El "hallazgo C1" es preciso pero matizable**: spring-security-crypto SÍ
  trae `org.springframework.security.crypto.argon2.Argon2PasswordEncoder`
  desde 5.x, y `PasswordEncoderFactories.createDelegatingPasswordEncoder()`
  SÍ registra `argon2` → `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_2()`.
  Lo que NO es nativo de Spring Security es el algoritmo: el encoder delega en
  **Bouncy Castle** (`Argon2BytesGenerator`), que es dependencia **optional** de
  spring-security-crypto → `NoClassDefFoundError` en runtime si no se añade
  `org.bouncycastle:bcprov-*` explícito (issue #8842 de spring-security,
  cerrado como "invalid" — comportamiento por diseño).
- La JVM/Java 21 NO trae Argon2 en JCA (solo PBKDF2; ni siquiera scrypt).
  Toda implementación viene de librería.

Opciones concretas:

| Opción | Pros | Cons | Complejidad |
|---|---|---|---|
| **A. Argon2PasswordEncoder (spring-security-crypto) + `org.bouncycastle:bcprov-jdk18on` fijado exacto** | Es la vía "vía Spring Security" que ADR-3 nombra; pure-Java (sin JNI → funciona en cualquier runtime/container, incl. eclipse-temurin:21-jre); produce PHC estándar `$argon2id$v=19$m=…,t=…,p=…$…`; parámetros configurables por constructor → OWASP 2024 (m=19456, t=2, p=1, salt 16, hash 32) sin código propio | BC es una dependencia nueva grande (~5-6 MB) que hay que fijar exacta; defaults de fábrica (v5_8: m=16384, t=2, p=1) NO son los OWASP 2024 → pasarlos explícitos | Baja |
| **B. `de.mkammerer:argon2-jvm` (JNI, nativos empaquetados) envuelto en un PasswordEncoder propio** | La impl. de referencia C de Argon2; también PHC | JNI = fragilidad de runtime (glibc/musl, arch); hay que escribir y mantener el wrapper; más lejos de lo que ADR-3 dice | Media |
| **C. Password4j u otra librería (la vía que Spring Security 7.0 adopta como alternativa)** | PHC, parámetros OWASP fáciles | Dep. nueva no estándar en esta pila; ADR-3 ya cerró "vía Spring Security" | Media |

**Recomendación de exploración**: Opción A. El formato PHC queda intacto
(request del slicing plan: «si falla: librería madura, formato PHC intacto» —
A es "librería madura" BC empaquetada dentro de spring-security-crypto).
La prueba de que el encoder existe y cumple (encode→matches→formato PHC
literal) es un smoke del corte C2 (Done), así el riesgo queda probado aquí,
no al final.

### 2.2 Estructura del JWT y manejo de expiración

- Claims cerrados (§1.2): `sub`, `role`, `iat`, `exp`. Sign + verify HS256
  con la clave de `JWT_SECRET` (bytes UTF-8 crudos — la validación de C1 es
  sobre bytes UTF-8, no Base64: aclarar en la propuesta cómo deriva la clave
  HMAC del value del env; lo simple y consistente con el validador es usar los
  bytes UTF-8 tal cual).
- `exp = iat + TTL` (`JWT_TTL_HOURS` default 8); `expires_at` de las
  respuestas = `exp` serializada ISO-8601 UTC `Z`.
- Verificación por tiempo del servidor (reloj NTP del host, TRD §9); sin
  clock-skew configurado necesario en red interna (propuesta lo decide si
  introduce margen).
- Librería:

| Opción | Pros | Cons |
|---|---|---|
| **jjwt (jjwt-api + jjwt-impl + jjwt-jackson)** | API mínima idiomática para sign/parse HS256 puro; sin semántica OAuth; uso extendido | No gestionada por el BOM de Boot → fijar versión exacta |
| **nimbus-jose-jwt** (directamente o vía spring-security `oauth2-jose`) | La usa Spring Security internamente | El BOM de Boot 3.5.x dejó de gestionar nimbus (issue #46478): su versión llega transitiva de spring-security-bom solo si se añade spring-security-oauth2-jose (peso OAuth innecesario); usada sola hay que fijar versión exacta |

Sugerencia: jjwt con versión fijada exacta (coherente con la política de
versiones exactas del POM).

### 2.3 SecurityFilterChain con dos cadenas

Forma esperada (la propone C2, esto es el espacio de decisiones):

- **Cadena 1 (`@Order(1)`, matcher `/api/v1/ingest/**`)** — la de ingesta
  `X-API-Key`: se DECLARA en C2, se USA en C5. Sin mecanismo real, y con
  `anyRequest().authenticated()` (sin permitAll) → toda petición a
  `/api/v1/ingest/**` responde 401 en la capa de seguridad (los filtros de
  seguridad corren antes del DispatcherServlet, por lo que ni siquiera será
  404 aunque no exista controller). Alternativa: no registrar la cadena y
  dejarlo para C5 — pero el slicing plan dice "la de ingesta se declara, se
  usa en C5"; declararla como 401-until-C5 es coherente y seguro
  (deny-by-default, ADR-15).
- **Cadena 2 (`@Order(2)`, matcher `/api/v1/panel/**`)** — panel JWT:
  `permitAll` solo para `POST /api/v1/panel/auth/login` (match exacto método+
  ruta) + filtro OncePerRequestFilter de Bearer JWT que: extrae header,
  firma/verifica, frega `Authentication` con `sub`+`role`, y si falta/mal/
  expirado → **401 con cuerpo estándar `(code, message)`** — decidir el
  literal del `code` del 401 de filtro (p. ej. `invalid_token` /
  `token_required`; el Schema fija literal solo para login:
  `invalid_credentials`, §3.3). Stateless (SessionCreationPolicy.STATELESS),
  CSRF off (API token-based, no hay form login), sin httpBasic/formLogin.
- **Cadena 3 (resto)** — decide el destino de lo NO cubierto:
  `/actuator/health` (y `/actuator/info`) **DEBEN quedar permitAll** o rompe
  el healthcheck (regresión E1/E10); `/ws/logs`: el Echo de C1 no valida
  tokens y el smoke (`scripts/ws_echo_test.py`, reutilizable en C7) espera
  handshake libre → recomendación: dejar `/ws/logs` SIN autenticar en C2 y
  añadir la autenticación por query-param `?token=` en C7 (que es cuando se
  necesita; ADR-6). Y `anyRequest()` → denyAll (no permitAll) para superficie
  mínima.
- Consecuencia inevitable de añadir spring-boot-starter-security: Boot
  protege TODO por defecto y genera user aleatorio — con nuestras 3 cadenas
  el usuario in-memory queda inerte; aún así la propuesta debe explicitar
  como NO regresión E10 (`/actuator/env` sigue 404).

### 2.4 CORS en C2 (hueco de alcance detectado)

- El `application.yml` de C1 etiqueta CORS como C2 («sin FRONTEND_ORIGIN
  (CORS es C2)»). TRD §5: en producción el panel es mismo-origen vía proxy →
  CORS «prácticamente no entra en juego»; en desarrollo, solo el origen del
  dev server (`FRONTEND_ORIGIN`); la API de ingesta sin CORS abierto.
- El alcance de C2 del 07 NO menciona CORS explícitamente. Opciones: (i)
  incluir config mínima CORS ahora (permitir `FRONTEND_ORIGIN` solo en dev y
  sobre /api/v1/panel y /ws/logs), o (ii) diferir a C8 (cuando el frontend
  dev la necesita). Pregunta de gate (§5).

### 2.5 Seed idempotente del admin

- Dónde: feature `auth` (paquete ya creado en C1 con package-info). Un
  `ApplicationRunner` (tras refresh, después de Flyway — ver §1.7) con
  JdbcTemplate (spring-boot-starter-jdbc ya está por D4).
- Idempotencia: Schema §6.3 dice «si no existe **ningún** `users`» → crea el
  admin con username/env y hash Argon2id del password/env. TRD §5 dice «solo
  si no existe **el usuario**». Diferencia sutil (tabla vacía vs username
  existente): la propuesta debe elegir una semántica (recomendado: la literal
  del Schema §6.3 — consultar solo si la tabla está vacía — y NO tocar nada
  si hay usuarios, aunque el username del env difiera), documentarla y no
  reabrir el resto.
- Nunca hashear en SQL/Flyway (Schema §6.3: «no en SQL de Flyway porque el
  hash Argon2id depende de configuración») → solo código.
- `role='ADMIN'` explícito al insertar (DEFAULT 'ADMIN' del esquema lo cubre;
  fijarlo explícito es trivial y no cambia contrato).
- Verificación Done: reinicio NO duplica (idempotente), y sin password sigue
  sin arrancar por E4 ya cubierto (el validador).

### 2.6 Dónde vive qué (encoder vs validador de login)

- `PasswordEncoder` (bean de opción A con parámetros OWASP): paquete `auth`.
  El login con seed YA hasheado hace `encoder.matches(raw, password_hash)` —
  el hash guardado en el seed se crea con `encoder.encode(ADMIN_INITIAL_PASSWORD)`
  en el mismo arranque; PHC autodescribe sus parámetros, así el `matches`
  funcione aunque luego se ajusten defaults.
- Filtro JWT + servicio token (sign/parse): paquete `auth`.
- Error 401 login: el controller/servicio de login produce
  `{"code":"invalid_credentials", …}` (§3.3); el 401 de filtro lo produce el
  filtro (code a fijar, §2.3).
- GET /me: `sub`/`role` del token + `expires_at` = `exp` del token (no nueva
  consulta para existir; opción: revalidar contra DB — decisión menor para la
  propuesta).

### 2.7 Tests/smoke del corte

- El POM de C1 omite spring-boot-starter-test «porque los tests llegan en
  C2» (comentario literal). La propuesta debe decidir: añadirlo y escribir
  tests de integración (¿con qué container engine? podman en el host) o
  limitar el Done a smokes curl contra pila podman como en C1
  (login OK, login 401, seed idempotente al reinicio, `/api/v1/panel/**`
  → 401 sin JWT). El Done del 07 habla de «smoke» explícitamente — probable
  suficiente, pero el comentario del POM sugiere añadir starter-test.

## 3. Riesgos aplicables a C2

1. **Argon2id no nativo en Spring Security** (riesgo #1 del 06, probado aquí):
   mitigación ya en §2.1 (BC + encoder de Spring Security; PHC intacto;
   smoke encode/matches en el corte).
2. **Regresión del healthcheck al añadir spring-boot-starter-security**:
   Boot securiza todo por defecto; sin `/actuator/health` permitAll la pila
   entra en bucle `restart: unless-stopped` (RG-6). Mitigación: cadena 3 con
   permit explícito (§2.3).
3. **Regresión del fail-fast E4**: el validador no se toca (su javadoc ya
   reserva JWT/seed/CORS para C2); el C2 no debe relajar ninguna variable
   exigida.
4. **Regresión del Echo WS de C1** si `/ws/logs` se metera en la cadena JWT
   del panel — por eso la recomendación §2.3 es dejarlo libre hasta C7.
5. **Dependencias nuevas (bcprov, jjwt) fijas exactas** — política RG-1 de
   versiones exactas del POM (ya aplicada en C1); versiones flotantes
   prohibidas.
6. **Nota heredada S2 de C1 (para C8, NO para C2)**: fijar versiones de
   `frontend/package.json` sin prefijos `^`/`~` cuando se toquen dependencias
   frontend. C2 no toca el frontend — se registra como nota de heredados.
7. **JWT en logs — no aplica aún como riesgo de C7, pero sí como precaución**:
   el filtro del panel nunca debe loguear el header Authorization ni el token.
8. **Semántica del seed (tabla-vacía vs username)**: divergencia menor
   Schema §6.3 vs TRD §5 (§2.5) — la propuesta fija una, documentada.

## 4. Downstream ligado

- **C5** — consume la cadena 1 declarada aquí: el mecanismo real
  `X-API-Key` (hash SHA-256, índice O(1), comparación constante) es C4+C5.
- **C7** — mismo JWT (HS256, mismo secreto/TTL) vía query param `?token=` en
  el handshake `/ws/logs`; reemplaza el Echo de C1; reutiliza
  `scripts/ws_echo_test.py --solo-handshake/--token` sin reescrituras (acción
  heredada de C1).
- **C8** — login desde el frontend (AppFlow flujo 1: `POST /auth/login` →
  `{token, username, expires_at}`), y `GET /me` como router guard para
  restaurar sesión (401 → redirige a `/login`). Si CORS se difiere, C8 lo
  necesita en dev.
- **C3/C4/C6** — heredan directamente el 401 de panel sin JWT al añadir sus
  endpoints bajo `/api/v1/panel/**`.

## 5. Preguntas para el gate de propuesta (máx. 5)

1. **CORS**: ¿incluye C2 la config CORS mínima (solo `FRONTEND_ORIGIN`, dev,
   sobre panel + ws) tal como etiqueta el `application.yml` de C1, o se
   difiere íntegramente a C8? (Recomendado: mínimo viable en C2, ya que C1
   lo asignó a C2 y el costo es una línea de config.)
2. **`/ws/logs` en C2**: ¿queda libre hasta C7 (recomendado, no romper el
   Echo/smoke de C1) o se le añade ya el filtro de JWT por query param?
3. **Tests**: ¿C2 añade spring-boot-starter-test con tests de integración
   (el comentario del POM lo sugiere) o el Done se satisface solo con smokes
   curl contra la pila podman como el Done literal del 07 indica?
4. **Seed**: si la tabla `users` NO está vacía pero el `ADMIN_INITIAL_USERNAME`
   configurado no existe, ¿el seed no hace nada (idempotencia estricta,
   Schema §6.3 literal) o crea al nuevo? (Recomendado: primera — tabla vacía
   o nada; 1 admin en v1 lo hace irrelevante en la práctica.)
5. — (sin quinta: el resto de decisiones abiertas son técnicas y las decide
   la propuesta.)

## 6. Nota explícita — decisión D7 de C1 (no perder de vista)

El TRD §6.3 contiene la divergencia conocida: su **TABLA** enumera
`SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`, pero su **bloque
de ejemplo** NO las incluye. C1 ya las usa (fix D7 en `e861d7b`, `.env.example`
con 4 líneas adicionales y comentario citando la tabla) y el validador las
exige — **NO cambiar nada**. Registro explícito para la próxima validación de
docs que toque el TRD: añadir las 2 líneas al bloque de ejemplo del §6.3
(ecos pendiente, menor; recomendación S3-a del verify-report de C1). No
re-validar docs solo por esto.

---

### Fuentes externas (verificación Argon2id/JWT, consultadas 2026-10-20)

- [spring-security issue #8842 — "The usage of Argon2PasswordEncoder requires additional dependencies" (BC es optional por diseño)](https://github.com/spring-projects/spring-security/issues/8842)
- [Spring Security — Password Storage (referencia oficial, fábrica de PasswordEncoders)](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [Argon2PasswordEncoder javadoc (spring-security 7.0 API)](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/crypto/argon2/Argon2PasswordEncoder.html)
- [PasswordEncoderFactories.java (fuente, mapping `argon2`)](https://github.com/spring-projects/spring-security/blob/master/crypto/src/main/java/org/springframework/security/crypto/factory/PasswordEncoderFactories.java)
- [spring-boot issue #46478 — Boot 3.5.x ya no gestiona nimbus-jose-jwt (viene de spring-security-bom)](https://github.com/spring-projects/spring-boot/issues/46478)