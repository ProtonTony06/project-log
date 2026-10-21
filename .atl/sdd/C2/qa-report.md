---
results: 22/27 pass
commit: 829cc707571fcc2f0f5f83a1e71f2dd987c26593 (HEAD; ningún fichero de QA está commiteado — qa-plan/qa-log/qa-report/tests-written y los artefactos de QA sin commitear: backend/src/test/java/com/projectlog/auth/LogHygieneTest.java (delegación B) y scripts/qa_c2_smoke.sh (delegación B, con correcciones test-side de la delegación C documentadas en qa-log.txt))
log: qa-log.txt
---

# Reporte QA — C2 (Seguridad base + auth del panel)

Ejecución única de la suite según qa-plan.md §1.3/§6: `mvn test` (COMANDO 1, Q1–Q7) y `bash scripts/qa_c2_smoke.sh` (COMANDO 2, Q8–Q27). La ejecución del veredicto del smoke es la **re-ejecución 2/2** (separador `COMANDO 2c` de qa-log.txt); la 1ª ejecución y la 1ª re-ejecución están documentadas como separadores `COMANDO 2`/`COMANDO 2b` (contaminada por `set -a` / abortada con SIGTERM por el colgado de podman-compose en Q17a). No se hizo ningún commit; no se tocó `backend/src/main/**` (solo inspección de pila) ni `/docs`; `.env` restaurado intacto por el trap (1057 B, sin marcas C2QA) y la pila cerró en F1 healthy (postgres/backend/frontend `Up (healthy)`).

## Tabla por ID (qa-plan §7)

| ID | Veredicto | Evidencia breve |
|----|-----------|-----------------|
| Q1 | PASS | Argon2PasswordTest: hash PHC `$argon2id$v=19$m=19456,t=2,p=1$` y verificación (mvn, 26 tests / 25 ok) |
| Q2 | PASS | Argon2PasswordTest: claves distintas → hashes distintos |
| Q3 | PASS | TokenServiceTest: emite JWT HS256 con jti y exp coherentes (jjwt 0.13.0) |
| Q4 | PASS | TokenServiceTest: valida firma y exp; expira/alterada → rechazo |
| Q5 | FAIL | LogHygieneTest: aserto antivacío del ListAppender sin eventos (appender.list vacío) aun a DEBUG — bug test-side del artefacto QA (delegación B), ver veredictos previos 1–5 en el separador del COMANDO 1 |
| Q6 | PASS | PanelAuthMvcTest: login 200 con credenciales sembradas (mock) |
| Q7 | PASS | LoginCredentialsTest: 401 `invalid_credentials` con credenciales inválidas |
| Q8 | PASS | POST /panel/auth/login admin → 200, token JWT emitido, `expires_in` coherente |
| Q9 | PASS | login 401 ×3 (usuario y contraseña inválidos) `invalid_credentials` |
| Q10 | PASS | /panel/me sin JWT → 401 `invalid_token` (entry point JSON) |
| Q11 | PASS | /me con JWT de Q8 → 200 (sub, role=ADMIN) |
| Q12 | PASS | a: firma alterada→401; b: payload alterado→401; c: otro secreto (ALT)→401 (F5 vía `JWT_SECRET_ALT`) |
| Q13 | PASS | BD-A (down -v + up): 1 fila en users, password_hash PHC Argon2id, role=ADMIN, login OK; sin logs de sembrado, Flyway `is up to date` |
| Q14 | PASS | Recomposición BD-B (down + up SIN -v): fila previa intacta (fila_antes == fila_despues), sin duplicado (COUNT=1), sin logs de sembrado |
| Q15 | PASS | Re-ataque G4: recomposición con ADMIN_INITIAL_* mutados → arranque OK, COUNT=1, fila original intacta, login ORIGINAL→200 y login-alt→401 `invalid_credentials` |
| Q16 | PASS | Re-ataque E4: `.env` sin ADMIN_INITIAL_PASSWORD → backend NO arranca (fail-fast C1: `faltan 1 de las 6 variables exigidas`), log nombra ADMIN_INITIAL_PASSWORD, sin olor a seeder |
| Q17 | PASS | a: sin JWT_SECRET→no arranca; b: JWT_SECRET→60 sin contenido de configuración (nombres ni valores) en el cuerpo; captura Q21 = BLOQUEADO (QA_C1_VALIDATOR_SHA256 / QA_C1_SPRING_FACTORIES_SHA256 ausentes) |
| Q22 | PASS | re-ataque JWT (Q10/Q11 equivalentes vía endpoint re-atacable) |
| Q23 | PASS | validación de contenido/código de la respuesta de error (401 JSON con `code`) |
| Q24 | FAIL | preflight OPTIONS con `Origin: $FRONTEND_ORIGIN` → **403 sin NINGUNA cabecera Access-Control-*** (esperado 204 reflejado); GET /me con token+Origin → 403; sin Origin → 200 (reproducido post-cierre, ver interpretación) |
| Q25 | PASS | Origin http://evil.example (no listado) → 403 sin ACAO (rechazo correcto) |
| Q26 | PASS | `.env` sin FRONTEND_ORIGIN → recomposición SIN error; arranque estable |
| Q27 | PASS | cierre en F1: .env restaurado, pila healthy (postgres/backend/frontend `Up (healthy)`) |
| Q21 | BLOQUEADO | sin hashes de referencia en el plan/entorno (delegación B tests-written.md); captura tomada: SHA-256 StartupSecretsValidator.java = df1f031a1d22d543363dc48f25b898fcfa9d3db2ab3e452cfac73a286dda4c9d, META-INF/spring.factories = d1db6b0f1a857bf87975ed8e0bcebcd2af1ccd67d68b564299d3c361c3254260 |

**Veredicto: 22/27 pass** — FAIL: Q5, Q18, Q19, Q24; BLOQUEADO: Q21. Exit smokes: COMANDO 1 → `mvn test` verde salvo LogHygieneTest (1 de 26); COMANDO 2c → `EXIT_SMOKE=1` (coherente con FAIL presentes).

## Interpretación (desde el plan)

**Obligatorios Done 07 (§7): Q8, Q9, Q10, Q13, Q14 → 5/5 PASS.** Re-ataques: Q15 PASS, Q17 a/b/c PASS, Q25 PASS, Q26 PASS; **Q24 FAIL**; Q21 BLOQUEADO (no obligatorio, es la contraparte [D] de Q16/Q17).

**Q5 — FAIL (artefacto QA test-side, no sistema).** El plan (§6.1) exige que la delegación B escriba LogHygieneTest con aserción de higiene S16 sobre eventos reales del ListAppender. La falla es del ARTEFACTO TEST: el aserto antivacío no recibe ningún evento (appender.list vacío) ni con loggers a DEBUG del test propio (ver re-ejecuciones 1–5 del COMANDO 1, documentadas en qa-log.txt). Nada indica que S16 se haya roto en el sistema (los smokes Q13/Q14 verifican la ausencia de mensajes de sembrado sobre la pila real y dan PASS). Hipótesis: el patrón del test no adjunta el appender a los loggers que la delegación B creyó (o el appender se adjunta en un logger sin eventos a nivel pedido). Señal a verify: corregir/reevaluar el TEST, no el código main.

**Q18 — FAIL (estructura plan-vs-routing; dos componentes).** (1) `health-sin-status-UP`: el plan §6 exige leer `/actuator/health` POR EL PROXY (8080), pero el nginx del frontend (TRD §9, intacto desde C1 y confirmado en el verify de C1) solo enruta `/api/v1/**` y `/ws/logs`: `/actuator/**` cae al fallback del SPA y devuelve 200 con el index (322 B, sin `"status"`) — incompatibilidad del PLAN con el routing heredado de C1, no regresión de C2. Evidencia in-network post-cierre (inspección de pila, estilo verify C1): dentro del contenedor del backend, `GET /actuator/health` → 200 con `"status":"UP"`; el servicio de health está intacto. (2) `backend/frontend/postgres-no-healthy(t1)(t2)`: los snapshots de `podman ps` del script no capturaron `(healthy)` en ninguno de los tres en t1 ni t2 (75 s aparte) y el chequeo de RestartCount pasó vacío (coherente con snapshots vacíos, p. ej. ps sin salida en la ventana inmediatamente posterior al `up` del cierre). La pila sí era funcional (el sondeo real del script por `/me` dio healthy y Q18 continuó a health/info). Señal a verify: re-capturar el sub-caso de estabilidad con un sondeo healthcheck de podman separado (p. ej. `podman inspect .State.Health.Status` con reintento) y decidir en spec dónde viven las aserciones de /actuator (proxy vs en-red).

**Q19 — FAIL (misma causa estructural; la seguridad S10/E10 subyacente está intacta).** El plan (E2.12) espera `/actuator/env` → 404 por el proxy. Por el proxy llega 200 (index del SPA, ~322 B) — y el cuerpo NO contiene ningún contenido de configuración (`grep -qiE 'JWT_SECRET|FRONTEND_ORIGIN|ADMIN_INITIAL|…'` sin match: el anti-leak del propio caso no saltó). En-red (post-cierre): `GET /actuator/env` → 401 (requiere auth; jamás 200 con contenido) — la propiedad E10 "la configuración no se expone" se cumple; lo que falla es la aserción LITERAL 404-por-proxy, imposible con el routing de C1. Escala a decisión de spec/verify (XFAIL con nota o redefinir el caso en red).

**Q24 — FAIL REAL del sistema (re-ataque S25; señal directa a verify/B).** El plan (E2.16/S25) exige preflight `OPTIONS /panel/auth/login` con `Origin: $FRONTEND_ORIGIN` → 204 con ACAO reflejado exacto, lista de métodos POST+OPTIONS+GET+PATCH+DELETE y cabeceras authorization/content-type/X-Confirm-*, sin `Allow-Credentials`. Lo observado (veredicto + reproducción dirigida post-cierre, misma petición por el proxy): preflight → **403 SIN NINGUNA cabecera Access-Control-***; y `GET /me` con JWT válido → 200 SIN Origin pero **403 CON Origin** (la cabecera Origin provoca el rechazo, también con evil.example → 403, que es lo correcto para Q25). Hipótesis (desde el plan, sin diagnóstico de código): el allowlist de orígenes del backend NO incluye el valor de `FRONTEND_ORIGIN` de F1 — el procesador CORS rechaza toda petición cross-origin no listada con 403 "Invalid CORS request" (patrón típico de Spring), p. ej. porque la lista esperada es otra (patrón/lista distinta del valor de F1) o porque la cabecera de origen no llega procesada por el proxy. Es un fracaso de comportamiento real (no del script ni del proxy): se reproduce ahora mismo contra la pila F1. Señal a verify: escalable al fix de CORS/FRONTEND_ORIGIN antes de Done (Q24 está en el set de re-ataques).

**Q21 — BLOQUEADO (pre-anunciado en tests-written.md).** El plan no aporta hashes de referencia (QA_C1_VALIDATOR_SHA256 / QA_C1_SPRING_FACTORIES_SHA256 vacíos); el script imprime la captura pero el veredicto no es emitible. Captura registrada arriba como línea base para verify. Línea literal: `Q21: BLOQUEADO (sin hash de referencia en el plan (comparar contra la captura de C1))`.

## Comandos exactos ejecutados

1. **COMANDO 1 (veredicto Q1–Q7)** — desde la raíz del repo:
   ```
   JAVA_HOME=/tmp/maven-qa/jdk-21.0.12.1+1 PATH=/tmp/maven-qa/apache-maven-3.9.9/bin:/usr/bin:/bin \
     mvn -f backend/pom.xml test
   ```
   (JDK Temurin 21.0.12.1 y Maven 3.9.9 portable: el host solo tiene JRE sin javac. Tras 4 re-ejecuciones previas documentadas en qa-log.txt, todas por entorno o bug test-side del artefacto LogHygieneTest, nunca por el sistema.)
2. **COMANDO 2 (veredicto Q8–Q27)** — desde la raíz del repo:
   ```
   bash scripts/qa_c2_smoke.sh
   ```
   Ejecutado 3 veces documentadas: COMANDO 2 (1ª, contaminada por `set -a` en el source de .env — precedencia del env del proceso sobre `.env` en la interpolación `${VAR}` de compose.yaml), COMANDO 2b (re-ejecución 1/2, abortada con SIGTERM tras 6 h 22 m colgada en el `up` de Q17a: podman-compose espera healthy y el fail-fast + restart:unless-stopped crash-loopea) y **COMANDO 2c (re-ejecución 2/2 — EJECUCIÓN DEL VEREDICTO)** con las correcciones test-side (f)(g)(h) documentadas en qa-log.txt (timeout en up/down, up en 2.º plano con observador para los casos que ESPERAN fail-fast, sondeo real de healthy por `/me` en lugar de /actuator por el proxy). Salida verbatim en `qa-log.txt` (separadores `== COMANDO …`).
3. **Evidencias complementarias post-cierre** (inspección de pila F1, NO veredicto, documentadas en este reporte): captura SHA-256 de los ficheros de Q21 (§6.5); sondeos por el proxy de `/actuator/health|env` (200 index SPA); sondeos en-red vía `podman exec project_log_backend_1 curl` (health → 200 `"status":"UP"`, env → 401); reproducción dirigida de Q24 (login 200 con credenciales de F1; preflight/GET con Origin → 403 sin cabeceras CORS; GET sin Origin → 200). Verdictos por ID: salida `=== VEREDICTOS QA C2 ===` en qa-log.txt líneas 2150–2174 (COMANDO 2c).

## Cierre de fase

- `.env` intacto (1057 B, sin marcas C2QA, JWT_SECRET presente; restaurado por el trap del script).
- Pila en F1: `project_log_{postgres,backend,frontend}_1` → `Up (healthy)`.
- Sin commits; sin cambios en `backend/src/main/**`, `/docs` u otros ficheros fuera de los artefactos QA (qa-log.txt, qa-report.md) y las correcciones test-side documentadas a `scripts/qa_c2_smoke.sh` y `LogHygieneTest.java` (artefactos QA propios de estas delegaciones).