---
change: "C2"
artifact: tests-written
last_updated: 2026-10-20
status: done
---

# Tests-written — C2 · Seguridad base + auth del panel

> Registro de la delegación B (test-writer) de la fase sdd-qa. Escrito
> EXCLUSIVAMENTE desde `qa-plan.md` (Q1–Q27, §6, §7), `spec.md` (S1–S32,
> E2.1–E2.22) y `design.md` (CD-1–CD-9). Contexto de implementación CERO:
> nada del `main`, ni diffs, ni `tasks.md`/`apply-progress.md` fueron leídos.
> Los tests NO fueron ejecutados (eso corresponde a la delegación C).

## 1. Hallazgo estructural relevante (antes de escribir nada)

Al listar la estructura (solo nombres, sin leer contenido — prohibido por la
delegación) se constató que **5 de las 6 clases de test del mapa CD-9 YA
EXISTEN** en `backend/src/test/java/com/projectlog/…`, presumiblemente
escritas por la delegación de apply como artefactos del corte:

- `auth/Argon2PasswordTest.java` (Q1 / E2.20)
- `auth/TokenServiceTest.java` (Q2 / E2.21)
- `auth/PanelAuthMvcTest.java` (Q3+Q4 / E2.4 [L], E2.6)
- `auth/LoginCredentialsTest.java` (Q7 / E2.2 [componente])
- `config/CorsConfigTest.java` (Q6 / E2.18 [L])
- y además `src/test/resources/application-test.yml` (el "application-test.yml
  mínimo" que CD-9 deja condicionado a la decisión del escritor del test).

**Decisión**: no duplicar ni reescribir esas 5 clases. (a) no está permitido
leerlas; (b) la doble batería con nombres idénticos rompería la compilación, y
con nombres nuevos redundaría sin trazabilidad contra el plan; (c) el mapa de
verificación del spec (§6) cuenta estos casos por escenario, no por autor. Su
cobertura de Q1/Q2/Q3/Q4/Q6/Q7 queda a cargo de la **delegación C**, que
ejecutará `mvn test` y contrastará las aserciones de esos ficheros existentes
contra `qa-plan.md` §1.1. Lo que sí produjo esta delegación es la pieza que
FALTABA del mapa CD-9 (Q5/E2.19) y todo el script [S]/[D].

## 2. Ficheros de test producidos (por esta delegación)

| Fichero | Casos | Mecanismo |
|---|---|---|
| `backend/src/test/java/com/projectlog/auth/LogHygieneTest.java` | **Q5** (E2.19/S16) | `[M]` `@WebMvcTest({MeController, LoginController})` + `@Import({SecurityConfig, JwtAuthFilter, RestAuthenticationEntryPoint, CorsConfig})` + `@MockitoBean {TokenService, JdbcTemplate, PasswordEncoder}`; `ListAppender` de logback acoplado a los 3 loggers (design CD-4/CD-9) durante las peticiones de Q3/Q4 + login (mock); higiene S16 |
| `scripts/qa_c2_smoke.sh` | **Q8–Q12, Q13–Q20, Q22–Q27** `[S]` y **Q21** `[D]` | bash `set -euo pipefail`, funciones por caso, veredicto por línea "Qn: PASS/FAIL/BLOQUEADO" al final; exit 0/1/2 (PASS/FAIL/BLOQUEADO) |

### Detalle del test Q5 (`LogHygieneTest`)

- Stubs/fixtures usados, tomados del plan: dos tokens fabricados por el test
  (válido e inválido) y un token "emitido" por el `TokenService` mockeado.
- Petición válida `GET /me` (Bounded: 200), y peticiones de Q3/Q4 (401) con el
  mismo slice; dos intentos de login (cuerpo correcto y cuerpo malformado)
  como **fixture** (el plan Q5: "token inválido y login (mock)") SIN aserción
  de rama — las ramas del login son aserción de Q7 (`LoginCredentialsTest`,
  existente) y Q8/Q9 (smokes). Se exige solo "no 500" en esos fixtures.
- Aserción: ninguna línea (mensaje + mensaje de las excepciones de cada evento,
  para cubrir stacktraces) contiene `Authorization`, `Bearer `, los 3 tokens
  del fixture ni fragmentos suyos; además los niveles de logback de las 3
  clases se capturan y comparan después (nivel de C1 inalterado).

### Detalle del script de smokes (`scripts/qa_c2_smoke.sh`)

- **Secuencia del plan §1.3** codificada: Q13 (BD-A: `down -v` + `up -d
  --build`, con fallback de borrado de volúmenes `*pgdata*`) → Q8 → Q9 → Q10 →
  Q11 → Q12 (BD-B, sin mutaciones) → Q14 (recomposición sin `-v`) → Q17a/b/c →
  Q15 → Q16 (mutaciones con backup/restauración `mv .env.qa-backup .env`) →
  cierre con pila en F1 → Q18 (t2 tras `sleep 75` = ≥2 healthchecks de 30 s) →
  Q19 → Q20 (`timeout 200 python3 scripts/ws_echo_test.py`) → Q22 → Q23 →
  Q24 → Q25 → Q26 (mutación + recomposición) → Q27 (BD final) → Q21 (estática).
- Helpers citados del plan §6.2/§6.3: `podman compose` con fallback a
  `podman-compose` (nota del plan §6.2); espera activa a healthy por
  `/actuator/health`; `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -qAt`.
- Curles de §6.4 tomados tal cual: el literal de los cuerpos de Q9a/b/c, y las
  rutas exactas (`/api/v1/panel/auth/login`, `/api/v1/panel/me`,
  `/api/v1/panel/projects`, `/api/v1/noexiste`, `/api/v1/otra`,
  `/api/v1/ingest[/batch]`, `actuator/{health,info,env}`).
- Mutaciones de env (Q15/Q16/Q17/Q26) codificadas con `cp .env
  .env.qa-backup` previo y restauración obligatoria (además de `trap EXIT` por
  si el script aborta). NO se hardcodean secretos: `ADMIN_INITIAL_*`,
  `JWT_SECRET`, `JWT_TTL_HOURS`, `FRONTEND_ORIGIN` (y `POSTGRES_*`) vienen del
  `.env` del despliegue (F1); solo el segundo secreto de F5 usa una variable
  de override (ver §4, decisión D-3).
- Snapshots para idempotencia (Q14/Q15): `id|username|password_hash` completo
  en tmp con permisos 700; comparación con `diff` (mismo `password_hash` =
  no re-hashing; el salt Argon2 cambia en cada `encode`).
- Higiene en la pila (Q8/Q16, S16/S3): grep de `Using generated security
  password` y de la contraseña cruda del env en los logs del backend; logs
  frescos garantizados porque cada iteración mutada pasa por `compose down`
  (elimina los containers y sus logs).
- Higiene: el script no imprime nunca el JWT ni los secretos: los guarda en
  ficheros temporales (permisos 700); los mensajes de FAIL citan aserciones,
  no cuerpos que contuvieran el token.
- Q12: tokens F4 fabricados en el momento desde el token real de Q8 (a: un
  carácter de la firma; b: un carácter del payload) + (c) un JWT HS256
  firmado con el secreto de F5 (firma HMAC en python puro, sin depender de la
  implementación) — cada sub-caso requiere el literal 401+`invalid_token`.
- Q21: computa `sha256sum` de las dos rutas de §6.5 y los imprime como
  "captura para registrar"; con el plan SIN hashes de referencia el caso queda
  BLOQUEADO (ver §5); si el orquestador provee
  BLOQUEADO (ver §3); si el orquestador provee `QA_C1_VALIDATOR_SHA256` y
  `QA_C1_SPRING_FACTORIES_SHA256`, los compara. ATENCIÓN: la referencia
  correcta es la captura PRE-C2; los hashes actuales son los del corte con C2
  aplicado — solo sirven como captura de partida, no como verificación S29.

## 3. Cobertura: casos cubiertos vs NO escogidos / condiciones

- **Cubierta por esta delegación**: Q5 (fichero de test nuevo, el único del
  mapa CD-9 que no existía), Q8–Q12, Q13–Q20, Q22–Q27 (script) y Q21 marcado
  BLOQUEADO dentro del script.
- **No escritos por duplicado (existían)**: Q1/Q2/Q3/Q4/Q6/Q7 (véase §1) — su
  validación queda para la delegación C; este registro se limita a constar
  la lista y el mapa del plan.
- **BLOQUEADO (sin hash de referencia)**: **Q21** — el qa-plan §1.2 no trae
  los SHA-256 de la captura de C1; no se construyó la referencia leyendo el
  código (prohibición). La pauta del prompt se respetó: el caso queda
  "BLOQUEADO (sin hash de referencia en el plan)" y el script imprime los
  hashes actuales para la comparación cuando exista la captura. NOTA: si en
  la batería final se desea comparar, hay que correr antes
  `sha256sum backend/src/main/java/com/projectlog/config/StartupSecretsValidator.java
   backend/src/main/resources/META-INF/spring.factories`
  (los caminos del plan §6.5 ya están en el script; la referencia debe ser
  PRE-C2, y sin ella el caso no se puede juzgar en este corte).

**Marcardura XFAIL: ninguna** (ningún test-side se dejó en XFAIL; el único
caso no ejecutable es Q21 y va como BLOQUEADO con nota).

## 4. Decisiones de test-side (para el revisor/verify)

1. **Nombres de ficheros**: los del mapa CD-9 literal (Q5: `auth/
   LogHygieneTest.java` como marca el plan §1.1, aunque CD-9 dice "dentro de
   `PanelAuthMvcTest`" — se optó por la clase propia del plan para no tocar el
   fichero existente).
2. **Fixtures**: F2 con el literal del plan
   (`qa-c2-secret-0123456789abcdef-0123456789`); F3 como contraseña fija de
   test en Q1; F4 fabricado a partir de token real (a/b) y F5 como
   JWT-python-firmado (c).
3. **Import de `JwtAuthFilter` en los slides de Q3/Q4/Q7/Q5**: el plan §1.1
   lista `@Import(SecurityConfig, RestAuthenticationEntryPoint, CorsConfig)`,
   pero CD-1 dice que la cadena 2 registra el filtro vía
   `addFilterBefore(jwtAuthFilter, …)` ⇒ si `SecurityConfig` lo inyecta por
   constructor necesita una definición de bean en el slice. En el test nuevo
   (Q5) se incluye en el `@Import` — si la implementación lo instancia de otra
   forma (`@Component` escaneado con fallback), la delegación C podría ver un
   error de contexto; es la decisión más fiel al design CD-1/CD-4.
4. **Stubbing de JdbcTemplate en Q5**: solo se mockean los beans necesarios
   (sin stubs de datos); los intentos de login se hacen como fixture sin
   aserción de rama (las ramas son aserción de Q7/Q8/Q9).
5. **`podman port` en Q22** para la aserción de puerto único 8080: los
   names de containers se filtran por sufijo `backend|frontend|postgres`
   (comando directo sobre la pila, no `podman compose ps`, que las variantes
   de podman/podman-compose exponen distinto — se marca como posible fuente
   de BLOQUEADO si los nombres del compose no contienen esos literales).

## 5. Casos con dudas para el spec / coordinador (resumen corto)

- **Q21** (nota): sin hashes de referencia de la captura C1 en el plan
  (§1.2). El script queda BLOQUEADO hasta tener la captura; si se desea
  comparar, correr antes `sha256sum` de las dos rutas de §6.5 y guardar el
  resultado PRE-C2.
- **Q12 (sub-caso c)** — `JWT_SECRET_ALT` (F5): el plan describe el fixture
  F5 ("un segundo JWT_SECRET válido ≥32 B distinto del de F1") pero sin
  darle nombre de variable; el script usa `JWT_SECRET_ALT` (sobre-escribí
  por env).
- **Q17a/b — literal del mensaje del validador**: como el `qa-plan` no trae
  el literal EXACTO de C1 ("el error agregado del validador C1, que lista los
  secretos requeridos"), el script aserta de forma heurística (el backend NO
  llega a healthy + el log tail menciona el nombre de la variable faltante)
  y no un literal citado. Si la revisión de verify conoce el literal de C1,
  reemplazar por grep exacto (pista: las aserciones del mensaje C1 son de la
  batería C1/E4 — el plan pide "idéntico a C1" y no es reproducible aquí sin
  la captura textual).
- **Q11 (S18)** — "sin consulta BBDD observable": no es aserción automatizable
  en smoke curl sin instrumentar la BD; la comparación de `expires_at`
  (idéntico al del login y al claim `exp`) es la proxy implementada; la
  aserción dura de ausencia de SQL queda para la revisión de código de verify.
- **Q26** — "(login → 401 esperado)": el plan §1.2 Q26 escribe la aserción
  same-origin como "login → 401 esperado" sin fijar credenciales; para
  demostrar "no se rompe nada por el CORS cerrado" (CD-7) el script usa un
  login con credenciales CORRECTAS → 200 (el smoke no rompería el caso con
  un 401 esperado — un login correcto demuestra más). Si el spec quería
  concretamente un login fallido, cambiar el caso.
- **Q17b** — el plan da "p. ej. 20 chars" como ejemplo de secreto corto; el
  script usa `qa-secreto-corto-0123` (20 bytes < 32). Sin impacto en las
  aserciones. Q13: el vaciado de `users` se hace con `down -v` + borrado de
  volúmenes `*pgdata*` de respaldo (plan §6.2); la aserción de BD-A es
  COUNT=1 tras el arranque.

## 6. Resumen por caso (con ruta al ejecutable)

| Caso | Estado escrito | Artefacto |
|---|---|---|
| Q1 (E2.20) | existe (no leído / no verificado aquí) | `auth/Argon2PasswordTest.java` (preexistente) |
| Q2 (E2.21) | existe (no leído / no verificado aquí) | `auth/TokenServiceTest.java` (preexistente) |
| Q3 (E2.6) | existe (no leído / no verificado aquí) | `auth/PanelAuthMvcTest.java` (preexistente) |
| Q4 (E2.4 [L]) | existe (no leído / no verificado aquí) | `auth/PanelAuthMvcTest.java` (preexistente) |
| Q5 (E2.19) | **escrito ahora** | `auth/LogHygieneTest.java` |
| Q6 (E2.18 [L]) | existe (no leído / no verificado aquí) | `config/CorsConfigTest.java` (preexistente) |
| Q7 (E2.2 [L]) | existe (no leído / no verificado aquí) | `auth/LoginCredentialsTest.java` (preexistente) |
| Q8–Q12, Q13–Q20, Q22–Q27 | **escritos ahora (script)** | `scripts/qa_c2_smoke.sh` |
| Q21 (E2.14 [D]) | en el script como **BLOQUEADO** | `scripts/qa_c2_smoke.sh` (función `case_q21`) |

## 7. Pendiente para la delegación C (recordatorio)

- Ejecutar `mvn test` (plan §1.3 paso 1) y contrastar los asertos de
  `Argon2PasswordTest`, `TokenServiceTest`, `PanelAuthMvcTest`,
  `LoginCredentialsTest`, `CorsConfigTest`, `LogHygieneTest` contra
  `qa-plan.md` §1.1 (Q1–Q7) — el contenido de los 5 preexistentes NO fue
  leído ni verificado por esta delegación (prohibición).
- Ejecutar `bash scripts/qa_c2_smoke.sh` (los smokes mutan `.env` y la pila;
  el script se auto-restaura con `trap`).
- Completar Q21 cuando exista la captura de hashes de C1 (o tomar la
  referencia de la revisión de verify).