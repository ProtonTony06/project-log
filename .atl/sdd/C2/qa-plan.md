---
change: "C2"
artifact: qa-plan
last_updated: 2026-10-20
status: done
---

# QA Plan — C2 · Seguridad base + auth del panel

> Plan de QA de la fase sdd-qa (delegación A: qa-plan) para C2. Escrito
> **desde el spec** (`spec.md`, E2.1–E2.22) y el design (`design.md`,
> CD-1–CD-9): un FAIL futuro de un caso se señala contra el ID de este PLAN
> (Qn), nunca contra el código. Literales del Schema/TRD **citados, nunca
> redefinidos** (regla del 07).

## 0. Convenciones del plan

- **Tipos de caso**: `[U]` = unitario sin Spring (ni contexto, ni BBDD) ·
  `[M]` = integración MockMvc (`spring-boot-starter-test`, sin contenedor ni
  BBDD, G3) · `[S]` = smoke curl/python contra la pila podman completa ·
  `[D]` = verificación estática de ficheros (diff/hash, no es JUnit).
- **Estados de BD de referencia** (precondiciones de los smokes):
  - **BD-A** — `users` VACÍA: pila parada con `podman compose down -v`
    (volumen `pgdata` borrado) y arrancada de nuevo → el seed tiene que
    crear 1 fila.
  - **BD-B** — `users` con EXACTAMENTE 1 fila (el admin de BD-A) → el seed
    se omite.
- **Fixtures globales**:
  - **F1** — `.env` real del despliegue con `JWT_SECRET` (≥32 bytes UTF-8),
    `ADMIN_INITIAL_USERNAME` / `ADMIN_INITIAL_PASSWORD` de valor CONOCIDO
    para QA, `JWT_TTL_HOURS` (8 o el que figure) y `FRONTEND_ORIGIN`.
    Antes de cualquier mutación de env: `cp .env .env.qa-backup` y restaurar
    al terminar (obligatorio en Q15–Q17, Q25–Q26).
  - **F2** — `JWT_SECRET` de test fijo ≥32 bytes UTF-8 para tests [L]
    (valor literal fijo en el test, p. ej. `qa-c2-secret-0123456789abcdef-0123456789`).
  - **F3** — Contraseña cruda de test fija para el unit de Argon2 (Q1).
  - **F4** — Token JWT "de ejemplo manipulado" (un carácter cambiado en el
    signature y otra variante con payload alterado) para Q12.
  - **F5** — Un segundo `JWT_SECRET` válido ≥32 B distinto del de F1
    (para firmar Q12b y para Q17c).
- Reglas comunes de aserción [S]: status HTTP + **cuerpo estándar**
  `{code, message[, field?]}` (Schema §3.1) + `Content-Type: application/json`
  cuando el cuerpo es JSON; nunca se acepta 2xx donde el spec pide 401, ni
  404 donde el spec pide 401.

---

## 1. Tabla de casos de test

### 1.1 Casos ligeros sin contenedor ([U]/[M]/[D] — corren con `mvn test` en segundos, o son estáticos)

| ID | Cubre | Tipo / Técnica | Fixtures / datos | Precond. BD | Aserciones obligatorias |
|---|---|---|---|---|---|
| **Q1** | E2.20 | `[U]` unit puro (sin Spring): instancia directa `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)` (S6) | F3; sin BD | ninguna (sin BD) | doble `encode(raw)`: PHC producida empieza por `$argon2id$v=19$m=19456,t=2,p=1$`; dos `encode()` del mismo raw → hashes **distintos** (salt aleatorio); `matches(raw, hash)` → true; `matches(otra, hash)` → false |
| **Q2** | E2.21 | `[U]` unit puro: `new TokenService(F2, ttl)` → sign + parse/verify roundtrip HS256 (S11–S15) | F2 (≥32 B UTF-8); TTL probado con default 8 h y valor alterno (p. ej. 1 h) | ninguna | roundtrip OK; claims del token = exactamente `{sub, role, iat, exp}` (ninguno más — S12); `exp == iat + TTL` en ambas configuraciones; header `alg` = HS256; token con firma alterada → `verify` vacío; token expirado (por construcción) → `verify` vacío |
| **Q3** | E2.6 | `[M]` `@WebMvcTest(MeController)` + `@Import(SecurityConfig, RestAuthenticationEntryPoint, CorsConfig)` + `@MockitoBean TokenService` (CD-9) | F2; sin BD | ninguna (slice sin DataSource) | `GET /api/v1/panel/me` sin header → **401**; cuerpo JSON `{code: "invalid_token", message: …}` (mensaje no vacío, genérico); `Content-Type: application/json`; **no HTML** |
| **Q4** | E2.4 [L] | `[M]` mismo slice de Q3; mock de `TokenService.verify` → vacío | token con firma alterada y token expirado por construcción | ninguna | `GET /me` con `Authorization: Bearer <firma-alterada>` → 401 `{code:"invalid_token"}`; mismo resultado con token expirado; también con header `Authorization` PRESENTE pero sin prefijo `Bearer ` (malformado) → 401 mismo literal — un solo literal para los 3 supuestos (S5) |
| **Q5** | E2.19 | `[M]` `ListAppender` de logback acoplado a los loggers de JwtAuthFilter / RestAuthenticationEntryPoint / TokenService mientras se ejecutan las peticiones de Q3/Q4 | token inválido y login (mock) | ninguna | tras las peticiones: NINGUNA línea de log contiene `Authorization`, `Bearer ` ni el JWT literal/fragmento del mismo (S16); el nivel de logging de C1 no se altera |
| **Q6** | E2.18 | `[U]` unit puro: `CorsConfig` con `frontendOrigin` vacío (S26) | sin `FRONTEND_ORIGIN` | ninguna | la `CorsConfigurationSource` NO registra ninguna configuración: `getCorsConfiguration(request)` → nulo para cualquier origin/path/method; por tanto ninguna respuesta lleva cabeceras `Access-Control-*` (CORS cerrado por defecto) |
| **Q7** | E2.2 [L] | `[M]` `@WebMvcTest(LoginController)` + `@MockitoBean JdbcTemplate/PasswordEncoder/TokenService` | 3 ramas: query sin filas (usuario inexistente), `matches` false (password mal), JSON no parseable (cuerpo malformado) | ninguna (JdbcTemplate mockeado) | las 3 ramas responden **401 con el MISMO literal** `{"code":"invalid_credentials", …}` (literal idéntico — sin user-enumeration, S17); sin `field` que distinga ramas; el cuerpo malformado NO produce 400 por defecto de Boot |
| **Q21** | E2.14 [L] | `[D]` estática: SHA-256 (o `git diff` si hubiese repo) de `StartupSecretsValidator.java` + `META-INF/spring.factories` vs captura de C1 | captura de hashes de C1 (pre-C2) | ninguna | hashes idénticos (diff vacío, javadoc incluido — S29); C2 no añade ni relaja variables requeridas; la contraparte [S] es Q16/Q17 |

### 1.2 Casos smoke pila podman ([S] — flujo canónico de C1) y anclas del Done

| ID | Cubre | Precond. BD | Fixtures / mutación de env | Aserciones obligatorias |
|---|---|---|---|---|
| **Q13** | E2.7 | **BD-A** (volumen limpio: `down -v` + `up -d --build`) | F1 | arranque en orden validador → Flyway V1 → seed; `SELECT COUNT(*) FROM users` = **1**; fila: `username` = `ADMIN_INITIAL_USERNAME`, `role` = `'ADMIN'`, `password_hash` empieza por `$argon2id$v=19$m=19456,t=2,p=1$`; `created_at`/`updated_at` no nulos; log de Flyway "Schema up to date" (E3, cero migraciones nuevas — S24); login posterior con esas credenciales → 200 (reusa el curl de Q8). Si la pila aún no arranca en BD-A, es SEÑAL contra Q13 |
| **Q8** | E2.1 | BD-B (admin de Q13) | F1 | curl `POST /api/v1/panel/auth/login` `Content-Type: application/json` con `{username, password}` correctos → **200**; cuerpo con EXACTAMENTE las claves `{token, username, expires_at}`; `token` = JWT de 3 segmentos con `alg: HS256` (decodificar header base64url); `username` = env; `expires_at` cumple `^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$` (ISO-8601 UTC con `Z`, sin milisegundos) y representa iat+TTL (≈ valor de `JWT_TTL_HOURS`, ventanas 7–9 h si 8); extra (CD-8): los logs del backend NO contienen `Using generated security password` (usuario in-memory inerte, S3) |
| **Q9** | E2.2 [S] | BD-B | F1 | tres curls de login: (a) password incorrecto, (b) username inexistente, (c) cuerpo malformado (JSON roto) → cada uno **401** con `{"code":"invalid_credentials", …}`; literal `code` **idéntico en los 3**; mensaje genérico, sin subtipos ni `field` diferenciador (S17) |
| **Q10** | E2.3 · **Done 07** | BD-B | F1 | `GET /api/v1/panel/me` SIN `Authorization` → 401 `{code:"invalid_token"}`; `GET` y `POST` contra `/api/v1/panel/projects` (ruta futura C3, sin controller) sin token → 401 `invalid_token` — **nunca 404**: el panel nace protegido (S19) |
| **Q11** | E2.5 | BD-B | token del login de Q8 | `GET /api/v1/panel/me` con `Authorization: Bearer <jwt>` → **200**; cuerpo `{username, role, expires_at}` exactamente esas claves; `role` = literal `"ADMIN"` (S3.3/§18, no `ROLE_ADMIN` ni vacío); `username` = `sub` del token; `expires_at` == `exp` del token en formato ISO-8601 `Z`, **idéntico** al `expires_at` devuelto por el login del mismo token; sin consulta BBDD observable (S18) |
| **Q12** | E2.4 [S] | BD-B | F4: token manipulado (firma alterada) y variante de Q12b con payload alterado; y Q12c: token firmado con OTRO secreto (F5) | `GET /me` con cada token inválido → **401** `{code:"invalid_token"}` en todos los casos, mismo literal que Q3/Q10 (S5: un solo `code`) |
| **Q14** | E2.8 · **Done 07** | **BD-B** al finalizar | F1 · RECOMPOSICIÓN (no vale `restart`): `podman compose down` (SIN `-v`) + `up -d --build` (o `--force-recreate`/`recreate`), esperar healthy | `COUNT(*)` sigue = **1**; mismo `id` que antes del down (no 2 filas, no fila nueva); MISMOS `username` y `password_hash` que antes del reinicio (idempotencia fuerte = no re-hash — el hash Argon2 con salt es distinto en cada `encode()`, así que `password_hash` idéntico demuestra que NO se re-seedó); log del runner: "Seed omitido", sin error; login con las credenciales originales sigue OK |
| **Q15** | E2.9 · **Re-ataque A** | **BD-B** (admin A ya sembrado) | F1 mutado: `ADMIN_INITIAL_USERNAME` DISTINTO en el env (backup+restaurar F1) | recomponer pila (down+up, sin `-v`) → sin error de arranque; `COUNT(*)` sigue = **1** (gate G4: tabla no vacía ⇒ NO acción, aunque el username del env no exista); fila original intacta (mismo id/username/`password_hash`); login con credenciales ORIGINALES → 200; login con el NUEVO username (password nueva del env mutado) → 401 `invalid_credentials`; runner no crea segundo admin aunque `ADMIN_INITIAL_PASSWORD` del env también cambie |
| **Q16** | E2.10 | cualquiera (no llega a BD) | F1 mutado: SIN `ADMIN_INITIAL_PASSWORD` (backup+restaurar) | arranque de la pila → el contenedor backend NO arranca (fail-fast); el mensaje proviene del validador de C1 (intacto, S29), NO del seeder (S23): el seed no reintenta ni re-valida presencia; mensaje y código de salida/salida de logs **idénticos a C1** (E4) |
| **Q17** | E2.14 [S] · **Re-ataque B** | cualquiera | F1 mutado en 3 sub-casos (backup+restaurar después): **Q17a** SIN `JWT_SECRET`; **Q17b** `JWT_SECRET` CORTO (<32 bytes, p. ej. 20 chars); **Q17c** `JWT_SECRET` válido ≥32 B (F5 o el de F1) | **a** y **b**: el backend sigue fallando al arrancar con el error ÚNICO agregado del validador C1, que lista los secretos requeridos (el conjunto de los 6) — no-regresión E4, S29 intocable; el mensaje NO proviene de TokenService ni del seeder (el fallo es previo al refresh); **c**: arranque green, `podman compose ps` → backend `Up (healthy)`, y login de smoke OK (pila funcional con secreto válido) |
| **Q18** | E2.11 | BD-B | F1 | tras recomposición (o tras Q14): los **3** contenedores (backend, frontend, postgres) `Up (healthy)` estables durante ≥2 healthchecks (60–90 s), sin entradas de restart en bucle (`RestartCount` no crece — E1/RG-6, S30); `GET /actuator/health` → **200** (con `"status":"UP"`) y `GET /actuator/info` → **200**, ambos POR EL PROXY (puerto host 8080) — permitAll intacto (S4) |
| **Q19** | E2.12 | BD-B | — | `GET /actuator/env` por el proxy → **404** (E10); el cuerpo NUNCA contiene contenido de configuración (sin nombres de variables ni valores — S30/E10; puede ser el JSON del entry point o 404, pero jamás JSON de actuator con propiedades) |
| **Q20** | E2.13 | BD-B | — | `python3 scripts/ws_echo_test.py` (misma invocación del corte C1) contra `ws://127.0.0.1:8080/ws/logs` completo, INCLUIDA la fase idle 75 s → exit 0: handshake libre sin JWT (G2), echo OK, sin cierre por timeout (E5–E7, S31) — `/ws/logs` NO cayó dentro de la cadena 2 (cadena 3 lo sirve, CD-1) |
| **Q22** | E2.15 | BD-B | F1 | `podman compose ps` → único puerto host publicado: 8080 (E2); `GET http://127.0.0.1:8080/` → 200 HTML index placeholder de nginx; `GET /api/v1/panel/auth/login` (método GET, NO el POST público — S3 método+ruta exactos) sin token → 401 JSON `invalid_token` (no el index del SPA: el proxy sigue enrutando `/api/v1/**` al backend, E8/S32); ruta inexistente `/api/v1/panel/noexiste` sin token → 401 `invalid_token`; ruta FUERA de los matchers (p. ej. `/api/v1/otra`) → la cadena 3 responde por `denyAll` 401 (o 403) JSON sin contenido de negocio — NUNCA el index del SPA ni 2xx (S4/S2) |
| **Q23** | E2.16 | BD-B | — | `POST /api/v1/ingest` con cuerpo JSON cualquiera: sin header y CON header `X-API-Key: <valor cualquiera>` → ambos **401** en la capa de seguridad (nunca 404 ni 2xx — cadena 1 declarada deny-by-default, C5 llega después, S2); ídem `POST /api/v1/ingest/batch`; cuerpo estándar `{code, message}`; extra S26: un preflight `OPTIONS /api/v1/ingest` con cualquier `Origin` → SIN cabeceras `Access-Control-*` (la ingesta NO tiene CORS abierto, TRD §5) |
| **Q24** | E2.17 · **Re-ataque C1** | BD-B | F1 CON `FRONTEND_ORIGIN` (p. ej. `http://<ip>:8080`); token válido de Q8 | preflight `OPTIONS /api/v1/panel/auth/login` con `Origin: <FRONTEND_ORIGIN>`, `Access-Control-Request-Method: POST`, `Access-Control-Request-Headers: authorization,content-type` → **204** sin autenticación; `Access-Control-Allow-Origin` = el origen configurado LITERAL (nunca `*`, nunca `allowedOriginPatterns`); `Access-Control-Allow-Methods` incluye `POST` y `OPTIONS` (y GET/PATCH/DELETE de S25); `Access-Control-Allow-Headers` incluye `authorization`, `content-type` y los reservados `X-Confirm-*`; SIN `Access-Control-Allow-Credentials: true` (S25); `GET /me` con token válido y `Origin: <FRONTEND_ORIGIN>` → respuesta con ACAO reflejada exacta |
| **Q25** | E2.17 · **Re-ataque C2** | BD-B | F1 CON `FRONTEND_ORIGIN`; `Origin: http://evil.example` (NO listado) | preflight `OPTIONS /api/v1/panel/auth/login` y `GET /me` con ese Origin → respuesta SIN `Access-Control-Allow-Origin` y SIN cabeceras de permiso CORS; el origen no autorizado NUNCA se refleja; (el estado observado se registra: el rechazo de CORS de Spring — 403 sin cabeceras — o respuesta sin cabeceras; la condición de FAIL es ver ACAO/permisos sobre el origen no listado) |
| **Q26** | E2.18 [S] · **Re-ataque C3** | BD-B | F1 mutado: SIN `FRONTEND_ORIGIN` (backup+restaurar; recomponer pila con la variable ausente) | recomposición + preflight `OPTIONS /api/v1/panel/auth/login` y `GET /me` con `Origin:` cualquier valor → SIN cabeceras `Access-Control-*` (cero orígenes registrados — S26); la misma petición same-origin sin `Origin: ` sigue funcionando normalmente (login → 401 esperado; no se rompe nada por el CORS cerrado — CD-7) |
| **Q27** | E2.22 | BD-B (fila de Q13) | — | inspección del esquema/`users` (information_schema + `\d users`): `id` entero PK IDENTITY; `username` TEXT NOT NULL con índice UNIQUE `users_username_unique`; `password_hash` TEXT NOT NULL con PHC de Q13; `role` TEXT NOT NULL (DEFAULT 'ADMIN' del esquema y valor 'ADMIN' fijado en el INSERT — S21/E2.22); `created_at`/`updated_at` TIMESTAMPTZ NOT NULL no nulos en la fila; sin OTRAS filas en ninguna otra tabla semilla: `projects`/`logs` (o sus equivalentes V1) VACÍAS — S24 (ni proyectos ni logs sembrados) |

### 1.3 Orden de ejecución recomendado (los smokes mutan estado y env)

1. `[L]/[U]/[M]`: `mvn test` con Q1–Q7 (rápido; desactiva el riesgo #1 — Argon2/BC — de forma temprana).
2. `[S]` en esta secuencia para minimizar recomposiciones: **Q13** (BD-A) →
   **Q8** → **Q9** → **Q10** → **Q11** → **Q12** (todos BD-B, sin mutaciones)
   → **Q14** (recomposición BD-B) → **Q17** (mutación, restaurar) →
   **Q15** (mutación, restaurar) → **Q16** (mutación, restaurar) →
   pila sana de nuevo con F1 → **Q18**, **Q19**, **Q20**, **Q22**, **Q23** →
   **Q24**, **Q25** (misma pila) → **Q26** (mutación, restaurar) →
   **Q27** (BD final) → **Q21** (estática, en cualquier momento, idealmente
   al final para comparar contra C1).
3. Toda mutación de env hace backup previo de `.env` y lo restaura antes de
   continuar; ningún caso smoke deja la pila en estado distinto de F1 al
   cerrar QA.

---

## 2. Aserciones obligatorias de ESTADO (más allá del status HTTP)

1. **BD — `users` (Q13, Q14, Q15, Q27)**: `COUNT(*)` exacto (1); `username`
   = valor del env correspondiente; `role` = `ADMIN`; `password_hash`
   empieza por `$argon2id$v=19$m=19456,t=2,p=1$`; `id` estable entre
   reinicios (mismo id en Q14); **idempotencia** = misma fila (id, username,
   `password_hash` idénticos) tras recomposición completa, sin duplicados.
2. **BD — ausencia de semillas extra (Q27, S24)**: ninguna otra tabla con
   filas sembradas; cero migraciones nuevas de Flyway (log "Schema up to
   date", E3) en Q13/Q14.
3. **Contenido de respuesta — login (Q8/Q9)**: 200 con claves EXACTAS
   `{token, username, expires_at}`; 401 con `{code, message}` y literal
   `invalid_credentials` idéntico en las 3 ramas (sin user-enumeration).
4. **Contenido de respuesta — /me (Q11)**: claves exactas `{username, role,
   expires_at}` con `role` literal `"ADMIN"` y `expires_at` = `exp` del
   token en ISO-8601 UTC con sufijo `Z` (patrón `…THH:mm:ssZ`, sin
   milisegundos).
5. **Contenido de respuesta — 401 del filtro (Q3, Q4, Q10, Q12, Q22)**:
   `{code: "invalid_token", message}` — un único literal para
   ausente/malformado/inválido/expirado (S5), `Content-Type: application/json`,
   nunca HTML.
6. **Logs — higiene (Q5, y grep en Q8/Q16)**: el header `Authorization`, el
   token y fragmentos del mismo NO aparecen en ninguna línea del
   filtro/entry point/TokenService/seeder (S16); tampoco debe aparecer
   `Using generated security password` (S3/CD-8) en la pila (Q8).
7. **Cabeceras CORS (Q24/Q25/Q26, y contra-ingesta en Q23)**:
   `Access-Control-Allow-Origin` exacto con `FRONTEND_ORIGIN` configurada;
   preflight 204 sin auth; métodos/headers exactos (Authorization,
   Content-Type, `X-Confirm-*`), sin `*`, sin `allowedOriginPatterns`, sin
   credentials; origen no listado y variable ausente → cero cabeceras de
   permiso; ingesta sin CORS (S26).
8. **Estabilidad de pila (Q18, Q14)**: 3 × `Up (healthy)` sin bucles de
   `restart: unless-stopped` (E1/RG-6); único puerto host 8080 (E2).

---

## 3. Cobertura del Done del 07 (cada objetivo con su caso propio)

| Done del 07 | Caso del plan |
|---|---|
| "login OK y 401 probados como smoke" | **Q8** (login OK 200) y **Q9** (login 401 ×3) — ambos [S] obligatorios |
| "seed idempotente verificado en reinicio" | **Q13** (seed en BD-A) + **Q14** (reinicio con RECOMPOSICIÓN full `down`+`up --build`, no `restart`: el requisito del coordinador es más estricto que el `restart` opcional de E2.8 — se cumple Q14 con down/up sin `-v` o recreate) + **Q15** (variante E2.9) |
| "`/api/v1/panel/**` protegido (401 sin JWT)" | **Q10** (`/me` sin JWT + rutas futuras 401) + **Q22** (rutas inexistentes del panel y denyAll) + **Q23** (ingesta 401 declarada) |
| "no-regresión de C1 (E1/E5–E7/E8/E10 re-ejecutables aquí)" | **Q18** (E1+health/info), **Q19** (E10), **Q20** (E5–E7), **Q22** (E2/E8), **Q16/Q17** (E4) |

---

## 4. Re-ataque independiente (pensado desde el plan, no desde el código)

- **Q15 (Re-ataque A — familia E2.9, seed idempotente con política G4)**:
  reinicio COMPLETO (down+up, no restart) con la tabla NO vacía y un
  `ADMIN_INITIAL_USERNAME` distinto del sembrado: el seed debe NO hacer
  nada (1 fila, la original intacta, sin error) aunque el env traiga
  username y password nuevos. Verifica la política completa
  "tabla-vacía + username inexistente ⇒ no acción" (G4/S22) y que ni el
  COUNT ni el `ON CONFLICT DO NOTHING` dejan pasar una segunda fila.
- **Q17 (Re-ataque B — familia E2.10/E2.14, fail-fast del validador)**:
  tres sub-casos: **Q17a** `JWT_SECRET` ausente → arranque falla con el
  mensaje ÚNICO agregado del validador C1 que lista los secretos requeridos
  (no-regresión E4, S29): el validador no se relajó ni se duplicó el
  mensaje; **Q17b** `JWT_SECRET` ≥ pero corto (<32 bytes UTF-8, p. ej. 20) →
  arranque sigue fallando por longitud, sin que TokenService/seeder lleguen
  a correr; **Q17c** `JWT_SECRET` válido ≥32 B → arranque green (pila
  healthy + login smoke OK): ni un secreto válido sobre-restringido ni uno
  débil aceptado.
- **Q24/Q25/Q26 (Re-ataque C — familia E2.17/E2.18, CORS de G1)**:
  **Q24** con `FRONTEND_ORIGIN` configurada: preflight `OPTIONS
  /api/v1/panel/auth/login` → 204 sin autenticación, `Access-Control-Allow-Origin`
  EXACTO (literal del env, nunca `*`), métodos y headers exactos
  (authorization/content-type/`X-Confirm-*`), sin credentials — y GET
  autenticado con ese Origin refleja el origen; **Q25** con un Origin NO
  listado → negación: sin ACAO, el origen nunca se refleja (si aparece,
  FAIL contra Q25); **Q26** sin la variable → CORS cerrado: cero
  configuraciones registradas, ninguna cabecera `Access-Control-*` en
  ningún Origin — y la API same-origin sigue operando (CD-7).

---

## 5. NOT cubierta por este plan de QA (queda bajo responsabilidad de verify)

Decisión sobre las no-regresiones de C1: **NINGUNA se delega** en los smokes
C1 ya ejecutados — E1, E2, E4, E5–E7, E8, E10 se RE-ejecutan aquí (Q18, Q22,
Q16/Q17, Q20, Q22, Q19) porque la capa de seguridad toca exactamente esas
superficies (chains, entry points, filter). Lo que sí queda fuera del plan,
explícitamente:

1. **S1–S4 como estructura de código (no comportamiento)**: existencia y
   orden de las 3 `SecurityFilterChain` (`@Order` 1/2/3, matchers exactos,
   cadena 3 sin matcher previo), `STATELESS`, CSRF deshabilitado, ausencia
   de `httpBasic`/`formLogin` y usuario in-memory inerte. El comportamiento
   se prueba (Q3/Q4/Q10/Q22/Q23) pero NO las anotaciones/estructuras; la
   verificación de estructura es revisión de código en verify (no hay
   escenario E2.x que la exija como test).
2. **S7/S10/S27 — literales del POM**: versiones exactas
   `bcprov-jdk18on:1.86` y `jjwt-*:0.13.0`, scopes (runtime/test), ausencia
   de rangos (RG-1) y ausencia de imports de `io.jsonwebtoken.impl.*`
   (S10). Sin test automatizado: verificación del pom.xml/fuentes en
   verify (los test que podrían romper por API errónea sí existen: Q2/Q3/Q4).
3. **Anti user-enumeration de TIEMPO (latencia igualada, CD-6)**: Q7/Q9
   aserten igualdad de status+literal entre las 3 ramas, pero NO se mide
   que la rama de usuario inexistente tarde igual que la correcta (medición
   de latencia en pila homelab — frágil y ruidosa, no exigida por E2.2).
4. **S15 — rotación de `JWT_SECRET` invalida tokens previos**: no hay
   escenario E2.x (laguna del spec); este plan no monta una pila con secreto
   cambiado para reusar un token viejo. Solo queda cubierto de forma
   indirecta (la clave se reconstruye del env en cada arranque — diseño)
   y por Q17c/Q9.
5. **S9 — plan B de Argon2 (`de.mkammerer:argon2-jvm`)**: sin test: solo se
   activa si Q1 falla, y ES una decisión de spec/apply, no de QA. Si
   ocurriera, el PHC de BBDD no cambia (la aserción de Q1 sobre el PHC
   seguiría siendo la frontera).
6. **Higiene de logs automatizada sobre la pila [S]**: la aserción
   automatizada de higiene es Q5 [L] sobre logback; el grep manual en logs
   del contenedor (Q8/Q16) es secundario y no un caso propio (un token
   impreso en la pila pasaría al informe de verify, no reduplica el caso).
7. **CORS sobre `/ws/logs`**: decidido en C7/C8 (S25) — fuera de alcance de
   C2; Q20 solo comprueba que el Echo no se rompió (no cabeceras CORS del
   socket).
8. **C5 (mecanismo `X-API-Key` real), C3/C4/C6 (endpoints), C7 (auth WS),
   C8 (frontend/login), logout/refresh, autorización por recurso, rate
   limiting, TLS/dominio, Testcontainers/BBDD en tests**: fuera de alcance
   del spec de C2 (§4) — no es deuda de QA de este corte.
9. **No hay ningún literal del spec sin caso**: E2.1–E2.22 quedan todos
   cubiertos por Q1–Q27 (mapa en §1; la única pieza del spec con caso
   parcialmente manual es la parte [L] de E2.14, que es verificación
   estática — Q21).

---

## 6. Comandos (independientes del código actual: el plan no ve el diff)

### 6.1 Tests ligeros [L] (Q1–Q7)

```bash
cd /home/acuelop369/Escritorio/Proyectos/Project_Log/backend
mvn test        # G3: spring-boot-starter-test, sin contenedor ni BBDD; segundos
```
Nota: no requieren pila ni PostgreSQL levantados; si algún test los pidiera,
es señal contra el caso (G3/D-CD-9 prohíben Testcontainers en C2).

### 6.2 Pila podman (smokes [S])

```bash
cd /home/acuelop369/Escritorio/Proyectos/Project_Log
podman compose up -d --build          # levantar / recrear con build
podman compose ps                      # esperar: 3 × "Up (healthy)"
podman compose down                    # parar (conserva pgdata) — Q14
podman compose down -v                 # parar + BORRAR pgdata (BD-A) — Q13
# (si 'podman compose' no subcomanda en este entorno, usar podman-compose
#  con los flags equivalentes — el entorno ejecuta con podman-compose)
```
Espera activa a healthy antes de cualquier aserción:
```bash
until curl -fsS -o /dev/null http://127.0.0.1:8080/actuator/health; do sleep 2; done
```

### 6.3 Estado de BD (Q13/Q14/Q15/Q27)

```bash
cd /home/acuelop369/Escritorio/Proyectos/Project_Log && . ./.env
podman compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  'SELECT id, username, role, left(password_hash, 32) AS phc_prefijo, created_at, updated_at FROM users;'
podman compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'SELECT COUNT(*) FROM users;'
# idempotencia (Q14): COUNT = 1, mismo id y mismo password_hash que la captura previa
# esquema (Q27): \d users o information_schema para constraint users_username_unique / NOT NULLs
```

### 6.4 Curles exactos por Schema §3.2/§3.3 (rutas y literales)

```bash
cd /home/acuelop369/Escritorio/Proyectos/Project_Log && . ./.env

# Q8 — login OK (200 {token, username, expires_at})
curl -isS -X POST http://127.0.0.1:8080/api/v1/panel/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_INITIAL_USERNAME\",\"password\":\"$ADMIN_INITIAL_PASSWORD\"}"

# Q9a/b/c — login 401 (password mal / usuario inexistente / cuerpo malformado)
curl -isS -X POST http://127.0.0.1:8080/api/v1/panel/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_INITIAL_USERNAME\",\"password\":\"no-es-la-contraseña\"}"
curl -isS -X POST http://127.0.0.1:8080/api/v1/panel/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"no-existe","password":"lo-que-sea"}'
curl -isS -X POST http://127.0.0.1:8080/api/v1/panel/auth/login \
  -H 'Content-Type: application/json' -d '{ "username": '

# Q11 — /me con JWT (extraer token de la respuesta de Q8; higiene: no pegarlo en informes)
TOKEN='…'
curl -isS http://127.0.0.1:8080/api/v1/panel/me -H "Authorization: Bearer $TOKEN"   # → 200
curl -isS http://127.0.0.1:8080/api/v1/panel/me                                      # → 401 (Q10)
curl -isS -X POST http://127.0.0.1:8080/api/v1/panel/projects                        # → 401 (Q10)

# Q12 — token manipulado (firma alterada / payload alterado / otra firma)
curl -isS http://127.0.0.1:8080/api/v1/panel/me -H "Authorization: Bearer <token_f4>"

# Q22 — proxy / método+ruta exactos / denyAll
curl -isS http://127.0.0.1:8080/                                 # index placeholder
curl -isS http://127.0.0.1:8080/api/v1/panel/auth/login          # GET ≠ público → 401
curl -isS http://127.0.0.1:8080/api/v1/panel/noexiste            # 401
curl -isS http://127.0.0.1:8080/api/v1/otra                      # denyAll → 401/403

# Q23 — ingesta declarada (deny-by-default)
curl -isS -X POST http://127.0.0.1:8080/api/v1/ingest -H 'Content-Type: application/json' -d '{"x":1}'
curl -isS -X POST http://127.0.0.1:8080/api/v1/ingest -H 'Content-Type: application/json' \
  -H 'X-API-Key: anything' -d '{"x":1}'
curl -isS -X POST http://127.0.0.1:8080/api/v1/ingest/batch -H 'Content-Type: application/json' -d '[]'

# Q24 — preflight CORS con FRONTEND_ORIGIN (204 + Allowed-Origin exacto)
curl -isS -X OPTIONS http://127.0.0.1:8080/api/v1/panel/auth/login \
  -H "Origin: $FRONTEND_ORIGIN" \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: authorization, content-type'     # → 204 + cabeceras
# Q25 — origen NO listado
curl -isS -X OPTIONS http://127.0.0.1:8080/api/v1/panel/auth/login \
  -H 'Origin: http://evil.example' \
  -H 'Access-Control-Request-Method: POST'                              # sin ACAO

# Q19 / Q18 — actuator por el proxy
curl -isS http://127.0.0.1:8080/actuator/health     # 200
curl -isS http://127.0.0.1:8080/actuator/info       # 200
curl -isS http://127.0.0.1:8080/actuator/env        # 404, nunca config
```

### 6.5 Logs, WS y mutaciones de env

```bash
# Higiene y señal manual (Q8 extra / Q16 / Q17): NO imprimir el valor de $TOKEN en informes
podman compose logs backend 2>&1 | grep -F 'Using generated security password'   # esperado: sin salida
podman compose logs backend 2>&1 | grep -iE 'argon2|seed'                        # "sembrado"/"omitido", sin username ni hash
podman compose logs backend 2>&1 | grep -i 'schema up to date'                   # E3 — Q13/Q14

# Q20 — WS Echo de C1 (misma invocación del corte C1)
python3 scripts/ws_echo_test.py     # incluye idle 75 s; exit 0

# Q15/Q16/Q17/Q26 — mutaciones de env (SIEMPRE con restauración)
cp .env .env.qa-backup
#   … editar/comentar la variable bajo prueba (ADMIN_INITIAL_USERNAME/PASSWORD,
#     JWT_SECRET, FRONTEND_ORIGIN) …
podman compose up -d --build        # o 'podman compose up backend' en fg para capturar el fail-fast
podman compose logs backend | tail -n 50      # capturar el mensaje del validador
mv .env.qa-backup .env                        # restaurar antes de continuar
sha256sum backend/src/main/java/com/projectlog/config/StartupSecretsValidator.java \
          backend/src/main/resources/META-INF/spring.factories   # Q21, comparar con captura C1
```

---

## 7. Criterio de veredicto

- Veredicto **por ID** (Q1…Q27, con sub-casos letra en Q17a/b/c y Q12a/b/c):
  cada ID queda verde o rojo por sí mismo; un FAIL se reporta como
  `Qn (E2.m) — aserción <x> incumplida`, contra el caso de ESTE plan, no
  contra el código.
- NINGÚN verde global parcial: el corte no puede proclamarse Done mientras
  Q8, Q9, Q10, Q13, Q14 (Done del 07) o cualquiera de los re-ataques Q15,
  Q17, Q24–Q26 estén rojos.
- Si la pila no llega a healthy, los smokes dependientes se marcan
  BLOQUEADOS (no FAIL) y se señala el caso que causa el bloqueo (p. ej.
  Q13 si el seed no levanta en BD-A).
- **XFAIL/KNOWN-FAIL** solo admisible si lleva nota EXPLÍCITA que identifique
  el escenario E2.x afectado, la razón, y la escala a una decisión de spec
  (p. ej. activar plan B S9 si Q1 falla, o revisar el literal del preflight
  si Q24 devolviera la cabecera correcta con status ≠ 204). Un XFAIL sin
  nota se cuenta como rojo.
- Cierre de QA: pila restaurada a F1 (sin mutaciones de env), todos los
  IDs verdes con su verdicto listado, y NOT-cubierto (§5) trasladado como
  checklist de revisión a verify.