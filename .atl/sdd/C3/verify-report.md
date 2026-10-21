---
change: "C3"
artifact: verify-report
last_updated: 2026-10-21
status: done
resultado: "PASS-with-notes"
---

# Verify Report — C3 · Proyectos: CRUD + archivado

> Fase sdd-verify del corte C3 (HEAD = `d9853a0`, 7 commits `a97de25`→`d9853a0`
> sobre el cierre C2 `d4768a6`). Verificación contra: `spec.md` (R3.1–R3.20,
> E3.1–E3.26), `tasks.md` (Done por fase), `apply-progress.md` (24/24 smokes,
> 3 fixes de script), `qa-plan.md` (Q1–Q29 + RA-1..RA-4, N1–N14),
> `qa-report.md` (32/33, Q23 BLOQUEADO), `qa-log.txt`, `tests-written.md`
> (dudas 1–5) — **más la re-ejecución real de este verify**: script de QA
> corregido + corridas canónicas propias, spot-check `mvn test` y re-capturas
> HTTP/headless. Sin tocar main, tests existentes ni `/docs`.

## 0. Veredicto

**PASS-with-notes** — Todo el alcance funcional C3 está confirmado con
triple fuente (smoke apply + QA manual + corrida canónica de verify);
`mvn test` 60/60; intocables diff-0 verificados sobre el rango completo de
commits. Notas (no bloqueantes): (1) Q23 [B] queda **BLOQUEADO (sin driver
de navegador)** — tolerado por plan §7, con los pasos HTTP-verificables
cubiertos con evidencia y una instrucción manual de ~2 min para el usuario;
(2) el artefacto QA `scripts/qa_c3_qa_smoke.sh` llegó con 11 bugs propios
(bug #8 incluido) — **corregidos y re-ejecutados en verify hasta corrida
limpia exit 0 / FAIL=0**; son defecto del ARTEFACTO, nunca del corte.

---

## 1. Veredicto por escenario (E3.1–E3.26)

Cruce de evidencia: **[A]** = smoke de apply (corrida 3, `bash
scripts/qa_c3_smoke.sh` 24/24 PASS, apply-progress §Evidencia [S]);
**[Q]** = QA manual del qa-run (TS `20261021214801`, qa-log §8+§9 — vía §7
del plan, porque el script de la delegación B no llegó a correr); **[V]** =
corrida canónica del script `qa_c3_qa_smoke.sh` CORREGIDO en verify (TS
`20261021215729`, **exit 0, FAIL=0 BLOQUEADO=0**, log `/tmp/verify_qa_rerun3.log`);
**[V-sp]** = spot-check propio de verify (código/contracts/HTTP puntuales).

| Escenario | Veredicto | Evidencia cruzada |
|---|---|---|
| E3.1 POST 201 con name+description, claves §3.3 exactas sin `summary`, `ACTIVE`, `api_key_prefix:null`, `has_key:false`, fechas `Z` | **Confirmado** | [A] 24/24 · [Q] Q1 (psql status/has_key derivado) · [V] Q1 PASS con `claves_exactas` + psql · [V-sp] POST manual → 201 8 claves exactas (`{"id":9,…,"has_key":false}`) |
| E3.2 POST 201 sin description → `description: null` | **Confirmado** | [A] · [Q] Q2 (`psql description IS NULL`) · [V] Q2 PASS |
| E3.3 POST 409 `name_duplicate` inmediato + fila única (constraint BD, no check-then-insert) | **Confirmado** | [A] · [Q] Q3 + psql COUNT==1 · [V] Q3 PASS (409+COUNT==1) · [V-sp] 409 con cuerpo `{"code":"name_duplicate","message":"Ya existe un proyecto con ese nombre","field":"name"}` |
| E3.4 POST `{}` y `{"name":""}` → 400 `field_required(field:name)` | **Confirmado** | [A] · [Q] Q4 (sin fila nueva en psql) · [V] Q4 PASS |
| E3.5 POST name 201 chars → 400 `field_too_long(name)` | **Confirmado** | [A] · [Q] Q5 · [V] Q5 PASS · [Q27] borde 200 válido/201 rechazado en [L] |
| E3.6 POST description 2001 chars → 400 `field_too_long(description)` | **Confirmado** | [A] · [Q] Q6 · [V] Q6 PASS |
| E3.7 GET lista → 200 array plano `[ProjectDto]` sin paginación | **Confirmado** | [A] · [Q] Q7 · [V] Q7 (`type==array`, 8 claves × elemento, membresía TS) · [V-sp] GET → 200 array plano de 15 elementos, sin `{items,next_cursor}` |
| E3.8 `?status=ACTIVE`/`ARCHIVED` filtra; vacío → 200 `[]` | **Confirmado** | [A] (orden crear→parchear→listar) · [Q] Q8 (membresía del TS: `-a2` solo en ARCHIVED, `-b` solo en ACTIVE) · [V] Q8 PASS |
| E3.9 `?status=active`/`ARCHIVE`/vacío → 400 `filter_invalid_value(field:status)` fail-fast | **Confirmado** | [A] · [Q] Q9 (nunca 200/500; CD-3.11 incluido) · [V] Q9 PASS · [V-sp] PATCH `{"status":"ACTIVO"}` → 400 con literal y `field:"status"` |
| E3.10 GET ficha existente 200, DTO completo | **Confirmado** | [A] · [Q] Q10 · [V] Q10 PASS (nota: el script falló 2 corridas por jq propio — bug #10, corregido) |
| E3.11 GET `/999999` → 404 `not_found` con cuerpo (nunca vacío) | **Confirmado** | [A] · [Q] Q11 · [V] Q11 PASS |
| E3.12 PATCH solo name → resto intacto, `created_at` igual, `updated_at` estrictamente posterior | **Confirmado** | [A] (`sleep 2`) · [Q] Q12 (psql coherente) · [V] Q12 PASS (epoch DTO↔psql con FLOOR) |
| E3.13 PATCH description texto y `{"description":null}` limpia (columna NULL real) | **Confirmado** | [A] · [Q] Q13 (psql `description IS NULL`) · [V] Q13 PASS |
| E3.14 Archivar `{status:ARCHIVED}` → dato persistido/intacto + reversible a ACTIVE; key/logs intactos (reglas 3/11) | **Confirmado** | [A] · [Q] Q14 (en 2 partes: parte 1 tras Q13, ciclo completo en RA-2-paso4) · [V] RA-2-paso4 PASS (ARCHIVED×2→ACTIVE→ARCHIVED idempotente, `api_key_prefix:null`/`has_key:false` en todo el ciclo) + Q14 PASS |
| E3.15 PATCH `{}` → 200 idempotente, `updated_at` INTACTO (igualdad ESTRICTA tras `sleep 2`), sin UPDATE | **Confirmado** | [A] · [Q] Q15 + [L] `verify(never()).update` (Q27) · [V] Q15 PASS |
| E3.16 PATCH `name:null`/`status:null` → 400 `field_required(field:name\|status)`; fila intocada | **Confirmado** | [A] · [Q] Q16 (psql de fila sin cambiar) · [V] Q16 PASS |
| E3.17 PATCH status fuera de enum ×3 (`ACTIVO`/`active`/`ARCHIVE`) → 400 antes de BD, nunca 500 | **Confirmado** | [A] · [Q] Q17 (fila intocada por psql) · [V] Q17 PASS · [V-sp] ejemplo con literal exacto |
| E3.18 PATCH name que choca → 409 `name_duplicate`; B intacto | **Confirmado** | [A] · [Q] Q18 + re-GET de B · [V] Q18 PASS |
| E3.19 PATCH id inexistente → 404 `not_found` (404 antecede al UPDATE) | **Confirmado** | [A] · [Q] Q19 + [L] `never().update` · [V] Q19 PASS |
| E3.20 `updated_at`: GETs repetidos NO modifican; PATCH con campo sí; `created_at` invariable | **Confirmado** | [A] (`sleep 2`) · [Q] Q20 (GETs×3 ≥1 s, epoch DTO+psql con FLOOR) · [V] Q20 PASS · [V-sp] dos GET consecutivos → mismo `updated_at` (`2026-10-21T19:26:45Z` ×2) |
| E3.21 401 sin JWT en las 4 rutas (`invalid_token` del entry point; sin parse estricto del body ISO-8859-1) | **Confirmado** | [A] E3.21 · [Q] Q21 (4 rutas, código tolerante) · [V] Q21 PASS · [L] Q29 (401×4 dentro del slice `ProjectsControllerMvcTest`) |
| E3.22 Maquetas servibles con fetch real (200 `text/html`, marcas login/`panel/projects`, `?id=`; `/docs/mockups` NO expone el original) | **Confirmado** (cota curl, Q22) | [A] E3.22 · [Q] Q22 (cmp contra el original: no coincide; fallback SPA permitido) · [V] Q22 PASS · [V-sp] `:8080/mockups/04` y `/05?id=9` → 200 `text/html`, marcas `/api/v1/panel/auth/login` ×4, `/api/v1/panel/projects` ×4 (04) y ×5 (05), `URLSearchParams` (05); `:8080/docs/mockups/04` → 200 que **no coincide** con el HTML del repo (fallback, permitido) |
| E3.22 [B] Q23 — operación real de ratón (login, filtro, Archivar/Desarchivar) | **BLOQUEADO (sin driver de navegador)** — tolerado por plan §7; subpasos HTTP-verificables cubiertos (§3 de este informe) | [Q] capturas headless de paso 1 (qa-log §9) · [V-sp] re-capturas propias `/tmp/q23_verify/q23_04.png` y `q23_05.png` + login/ficha/PATCH demostrados por curl desde el punto de vista del mockup |
| E3.23 Cadenas auth C2 intactas (login, `invalid_credentials`×3, `invalid_token` en `/me` y `/panel/projects`, preflight `20[46]`) | **Confirmado** | [A] E3.23 · [Q] Q24 · [V] Q24 PASS (login forma exacta con `expires_at Z`, ×3 idéntico tolerante, preflight ACAO reflejada + PATCH en Allow-Methods) · [V-sp] login inline con credenciales `.env` → 200 token |
| E3.24 Diff 0 intocables + cero migraciones + Flyway solo v1 | **Confirmado** | [A] E3.24 · [Q] Q25 (`git diff --quiet` + `--cached` sobre pathspecs; sin `V[2-9]__*`) · [V] Q25 PASS (psql `flyway_schema_history` = solo `1`) · [V-sp] `git diff --name-only d4768a6..d9853a0` = EXACTAMENTE los 11 ficheros nuevos de C3 (ningún intocable tocado ni en commits) |
| E3.25 [L] Serialización ProjectDto: 8 claves exactas sin `summary`, `description:null`, literal `ACTIVE`, fechas `Z` | **Confirmado** | [Q] Q26 (mvn 60/60) · [V-sp] `mvn test` spot-check propio en imagen builder → **exit 0, 60/60** (`ProjectDtoSerializationTest` = 2 tests); el record sin anotaciones y las claves salen del nombre de los componentes (`ProjectDto.java`, verificado por lectura) |
| E3.26 [L] `@WebMvcTest` matriz R3.15 completa + fail-fast + catch UNIQUE → 409 + 404 | **Confirmado** | [A] 25 tests · [Q] Q27 · [V-sp] lectura del test: `verifyNoInteractions` en los 400 de borde, `verify(never()).update(anyLong(), any())` en `{}` y 404, `DuplicateKeyException` del mock → 409 `name_duplicate`, claves §3.3 con `doesNotContain("summary")` |

Resultado: **25/26 escenarios confirmados con triple fuente; 1 BLOQUEADO
de entorno (Q23/E3.22-[B]) con subevidencia completa de los pasos
HTTP-verificables** — contingencia contemplada por plan §2/§7.

## 2. Cobertura R3.1–R3.20 (compacto)

| Req | Cobertura | Confianza |
|---|---|---|
| R3.1 (paquete, sin pom, sin migraciones) | árbol = 8 ficheros `com.projectlog.projects` + 2 mockups + script QA; `pom.xml`/`V1__init.sql` ausentes del diff de commits C3; `package-info.java` intacto | diff + lectura |
| R3.2 (sin capa de servicio; repo centraliza) | controller fino → repo; 4 op mono-sentencia RETURNING | lectura (repository/controller) |
| R3.3 (validación manual, sin `@Valid`) | constantes `CODE_*` + helper `error` con `ObjectMapper`; cero Bean Validation | lectura + smokes de literales |
| R3.4 (findAll/findById/insert/update; WHERE solo con status; array plano ORDER BY `created_at DESC, id DESC`) | `ProjectsRepository` (Lectura) + smokes E3.7/E3.8 | lectura + [S] |
| R3.5 (INSERT directo sin SELECT previo; UNIQUE→409; misma técnica en PATCH) | `insert` sin pre-check; handler ÚNICO `DuplicateKeyException` → 409 sirve POST y PATCH; [S] E3.3/E3.18 + [L] traducción | lectura + [S]+[L] |
| R3.6 (SET acotado, nunca key, `updated_at=now()` SIEMPRE, `{}` sin UPDATE) | 7 ramas de `conjuntoSet`; `[L]` SqlTest (7 tests) + smoke Q15/Q20 | lectura + [L]+[S] |
| R3.7 (`has_key` derivado `api_key_hash IS NOT NULL`; creación sin key) | constantes COLS + psql derivado en todos los smokes | lectura + [S] |
| R3.8 (fechas ISO-8601 UTC `Z` con formatter C2 por referencia) | RowMapper usa `LoginController.EXPIRES_AT_FORMATTER` (literal `yyyy-MM-dd'T'HH:mm:ss'Z'` UTC en `LoginController.java:39`); cero segunda convención en el paquete (solo la import; 3 usos) | lectura + regex `Z` en smokes |
| R3.9 (exactamente 4 endpoints; sin DELETE/summary/`include=summary`) | 4 mappings en el controller; smokes 404/409/400; claves sin `summary` verificado en 201/ficha/[L]/[S] | lectura + [S]+[L] |
| R3.10 (query `status` case-sensitive; inválido → 400 fail-fast) | E3.9 ×3 valores; fail-fast demostrado en [L] con `verifyNoInteractions` | [S]+[L] |
| R3.11 (404 `not_found`, nunca vacío/l lista vacía) | E3.11/E3.19; literal + mensaje AppFlow §5 | [S]+lectura |
| R3.12 (vocabulario aprobado; sin literales nuevos; 401 exclusivo entry point) | los 5 `CODE_*` literal; HttpMessageNotReadable → 400 `field_required` SIN `field` (RA-2-paso6 PASS) | lectura + [S] |
| R3.13 (límites name 1..200 / desc ≤2000 / enum cerrado; borde check antes de BD) | E3.5/E3.6/E3.17 + bordes válidos 200/2000 en [L] | [S]+[L] |
| R3.14 (POST crea fila → 201 DTO completo con defaults V1) | E3.1/E3.2 + psql | [S] |
| R3.15 (tabla G2 completa del PATCH) | matriz E3.12–E3.17 + combinados + campos desconocidos (RA-2-paso5 PASS, CD-3.9) | [S]+[L] |
| R3.16 (archivado SOLO PATCH `status`; nunca DELETE; no toca key/logs; sin 403 `project_archived` en C3) | E3.14 + ciclo RA-2-paso4 (archivar idempotente ×3); cero DELETE en el controller; no hay endpoint de ingesta | [S]+lectura |
| R3.17 (copias 04/05 en `frontend/public/mockups/`, `/docs/mockups` congelado) | commit `723a8c1` solo los 2 HTML; diff `docs/mockups` = 0; servidas por nginx `:8080/mockups/…` | diff + [S] |
| R3.18 (patrón C2: [L] sin contenedor + smokes pila real; SIN Testcontainers/H2) | 3 clases test (unit/Mvc slice), `mvn test` 60/60 en segundos sin BD; smokes contra podman | [L]+[S] |
| R3.19 (disciplina smokes: anti-veneno env, idempotencia `smoke-c3-<ts>`, 401 ISO-8859-1 sin parse estricto) | los dos scripts (apply y QA) la codifican; corridas verify con `env -i` + `--force-recreate`; nunca `down -v`/`restart` | [S] (log de corridas) |
| R3.20 (C3 solo añade; no-regresiones C2) | diff de commits = 11 ficheros exactos; Q21/Q24/Q25 verdes; Flyway v1; Echo WS diff-0 | diff + [S] |

## 3. Cierre de los dos abiertos por el qa

### 3.1 Q23 (BLOQUEADO) — veredicto verify: **BLOQUEADO (sin driver de navegador)**, con subpasos cubiertos

**Decisión** per plan §2 (regla dura: «un [B] sin evidencia de los 9 pasos
es BLOQUEADO, no PASS»): NO hay driver de automatización de ratón
(`geckodriver`/`chromedriver`/`xdotool` ausentes — `command -v` vacío; solo el
binario `firefox`). NO se instalan dependencias (regla de la fase). Por
tanto el veredicto honesto es BLOQUEADO **con** la evidencia de los pasos
HTTP-verificables ya ejecutados (nada de los 9 pasos queda sin cubrir en la
parte observable sin ratón):

1. **Paso 1 (04/05 renderizan login inline, no panel roto)** — capturas
   headless (sin instalar nada): propias de verify en
   `/tmp/q23_verify/q23_04.png` y `/tmp/q23_verify/q23_05.png` (login
   «Acceso al panel» + banda «COPIA C3 · puente desechable hasta C8 · no es
   la implementación», enlaces 04↔05), coincidentes con las del qa (paso 1,
   qa-log §9). No requieren driver: `firefox --headless --screenshot`.
2. **Assets que consume el mockup → 200** — `/mockups/04-proyectos.html` y
   `/mockups/05-proyecto-detail.html?id=…` → **200 `text/html`** con las
   marcas de fetch (`/api/v1/panel/auth/login`, `/api/v1/panel/projects`,
   `URLSearchParams` para `?id=`).
3. **Las rutas que el mockup consume responden** — con JWT real del admin:
   `GET /api/v1/panel/projects` → 200 (15 elementos), `GET
   /api/v1/panel/projects/{id}` → 200 ficha con las 8 claves del mockup
   (`name/status/has_key:false/api_key_prefix:null`), y `POST
   /api/v1/panel/auth/login` con credenciales del `.env` → **200 token** —
   es EXACTAMENTE la llamada que hace el login inline del mockup (mismo
   origen, sin CORS posible), así que el paso 2 del manual está demostrado
   HTTP-level; el PATCH `{status}` que ejecutan los botones es la misma
   API cubierta por E3.14/Q14 end-to-end.
4. **Originales intactos / no expuestos** — `docs/mockups/` diff-0 (Q25) y
   `:8080/docs/mockups/04…` NO sirve el HTML del repo (fallback); paso 9
   cubierto.

**Lo que queda sin evidencia literal**: los clics 3–8 (render de tarjetas,
toggle, etiqueta «Archivado» tras reload) requieren ratón.

**Instrucción concreta al usuario (verificación manual de ~2 min, única vía
restante)**: con la pila levantada (`podman ps`: 3 contenedores healthy),
abrir `http://localhost:8080/mockups/04-proyectos.html`; (1) loguear con
usuario/contraseña de `ADMIN_INITIAL_USERNAME/PASSWORD` en `.env`; (2)
comprobar que aparecen los proyectos del smoke con etiqueta Activo/Archivado
y la línea `sin key · has_key: false`; (3) pulsar el toggle Archivados y
comprobar el filtro; (4) pulsar Archivar en una tarjeta Activa → pantalla
recarga con «Archivado»; verificar en BD si se desea: `podman exec
project_log_postgres_1 psql -U $POSTGRES_USER -d $POSTGRES_DB -tAc "SELECT
status FROM projects WHERE name LIKE 'smoke-c3-20261021215729-%'"` (debe
mostrar ARCHIVED); (5) entrar a la ficha desde la tarjeta → 05 pinta los
datos; Archivar/Desarchivar desde 05 invierte el estado. Si todo sale,
decláralo y el veredicto se actualiza a **PASSED-MANUAL** (flag del script
`QA_C3_Q23_PASSED_MANUAL=1`, previsto en §2 del plan).

**Impacto en el Done del 07 G5**: NINGUNO que bloquee — Q22 (obligatorio,
la cota curl del mismo Done) es PASS con triple fuente; el plan §7 sanciona
explícitamente que `BLOQUEADO (sin navegador)` no invalide el Done de G5
con nota obligatoria. La nota queda aquí permanente. El panel React real
con estados completos es C8 (N8 declarado).

### 3.2 Bug #8 del script QA (delegación B) — fix + delta

**Defecto del artefacto** (nunca del sistema): `curl_post` estaba
hardcodeado a `/api/v1/panel/projects` y el LOGIN se hacía con él → el
payload de login iba a la ruta de proyectos con `Bearer pendiente` → 401
garantizado espurio (3 BLOQUEOS GLOBALES del qa-log, secciones 4–5).
**Corrección aplicada en verify** (`scripts/qa_c3_qa_smoke.sh`, script
nuevo no versionado, untracked): helper `curl_post` con ruta como
parámetro opcional (default `/api/v1/panel/projects`) + helper nuevo
`curl_post_login` hacia `/api/v1/panel/auth/login` SIN header
`Authorization`; los 5 call-sites de login actualizados (login inicial +
Q24: login OK, `invalid_credentials`×3, cuerpo roto). El filtro JWT de C2
no se interfiere en rutas permitidas (`JwtAuthFilter.java` leído: token
inválido deja contexto vacío y continúa), así que la ruta `permitAll` del
chain 2 procesa el login.

**Ajustes menores fijados en la misma pasada** (lecciones del qa-run):
- `FLOOR(EXTRACT(EPOCH FROM …))::bigint` en TODAS las comparaciones
  contra psql (7 sitos): `::bigint` sin FLOOR redondea `.5+` mientras el
  formatter del DTO trunca a segundos — igualdades estrictas falsas
  (`updated_at`/`created_at` en Q12/Q15/Q16/Q17/RA-2-paso7).
- **booleano psql (lección exacta)**: en CONCATENACIÓN de texto
  (`…||bool||…`) psql imprime `f`/`t`; como COLUMNA separada imprime
  `false`/`true` — el ra2_paso7 matchea `|ACTIVE|f|` (salida de la
  concatenación de la suya); el tool manual del qa lo mostraba `false`
  porque lo extraía como columna. Documentado en el script (nota
  sdd-verify).
- **bugs #10/#11 adicionales hallados en la 1ª corrida de verify y
  corregidos**: (10) `caso_q10` usaba `$s` en el filtro jq sin `--arg s`
  → error de compilación jq ⇒ FAIL espurio de la ficha; (11)
  `ra2_paso7` grep básico con `|` que alterna + `\;` escapado ⇒
  multi-match/warning; ahora `grep -qF` con literales.

**Delta de resultados tras corrección** (3 corridas del script corregido;
la 3ª es la canónica):

| Corrida | Resultado | Causa de los FAIL/BLOQ |
|---|---|---|
| verify-rerun 1 (bug #8+FLOOR fijo) | 31 PASS, **2 FAIL** (Q10, RA-2-paso7), exit 1 | bugs #10/#11 del script (jq `--arg` y grep `f/false`) |
| verify-rerun 2 (+#10 fijo) | 32 PASS, **1 FAIL** (RA-2-paso7), exit 1 | bug #11 (`f` vs `false` de psql) |
| **verify-rerun 3 (canónica, todo fijo)** | **33 elementos PASS + Q23 BLOQUEADO (tolerado), FAIL=0, exit 0** | — |

La salida canónica queda transcrita en §1 de este informe ([V]); el log
crudo en `/tmp/verify_qa_rerun3.log` (TS `20261021215729`, pila recreada
con disciplina anti-veneno: `env -i` + `up -d --build --force-recreate`,
healthy en cierre; nunca `down -v` ni `restart`). NO se reescribió la
narrativa del `qa-report.md` (intacto): su vía manual (§7 del plan) sigue
siendo válida y este delta es adicional.

## 4. Divergencias / desviaciones del apply y del QA

1. **7 commits en vez de 5** (`71c3e3b` script de smoke + fixes `4939d04`
   y `d9853a0`): los 2 fixes y el script son TODO ARTEFACTO de smoke
   (cero código de producto); el producto no requirió fix ninguno
   (corrida 3 del smoke verde al primer contacto directo con código C3).
   Encaja con la regla de tasks §5 («no commit salvo fix»). **Eco
   documental: NO** — nada que tocar en `/docs`.
2. **`--force-recreate` en la recomposición** (`4939d04`): `podman compose
   up -d --build` reconstruye la imagen pero NO recrea el contenedor
   (siguió el ID de imagen viejo). Es la actualización del patrón
   R-nuevo-3 ya heredada (qa-plan §0 la codifica como OBLIGATORIO).
   **Registrar como actualización del patrón**: toda recomposición
   podman-compose futura DEBE llevar `--force-recreate` (y `restart`
   sigue prohibido para recomponer). **Eco documental: NO en `/docs`** —
   norma de scripts, ya fijada en plan/QA de C3 y heredable (queda
   sembrada en `qa-plan.md` C3 §0 y en la cabecera de ambos scripts C3).
3. **QA con 8+3 bugs del PROPIO artefacto** (`scripts/qa_c3_qa_smoke.sh`,
   #1–#7 corregidos en qa; #8/#10/#11 en verify): el test-writer no pudo
   validar la mecánica de su script porque prohibidad del encargo le
   impedía tocar el sistema/ejecutar nada. **Recomendación de flujo
   futuro**: añadir una pasada barata «smoke of the smoke» previa a la
   corrida canónica (p. ej. `bash -n` +dry-run sintáctico de helpers +
   login de fumo + una llamada autenticada) o que el corredor (qa-run)
   haga una revisión mecánica del script ANTES de la primera corrida.
   **Eco documental: NO** (norma de flujo; se hereda a plan QA de C4+).
4. **Corrida de tests en imagen builder** (`maven:3.9-eclipse-temurin-21`,
   mismo tag D1/RG-1 de la etapa 1 del Dockerfile): el host no tiene JDK
   21 (JRE 25 sin javac — qa-log §1). Patrón confirmado y **registrado
   para C5+**: `podman run --rm -v backend:/ws:Z -v ~/.m2:/root/.m2:Z -w
   /ws docker.io/library/maven:3.9-eclipse-temurin-21 mvn test` (G4 sin
   BD respetado: sin contenedores de la pila). **Eco documental: NO**
   (patrón de entorno, se replica en los planes siguientes).
5. FLOOR/epoch y booleanos psql: ajustes fijados en el script de verify
   (§3.2); nota documentada para futuros scripts (extraer booleans de
   psql conscientes del contexto texto/columna). **Eco documental: NO**.
6. G4 aplicado — tests [L] sin BD (ninguno monta datasource; slices
   rápidos), `application-test.yml` intocable, sin Testcontainers/H2
   (S29/S26 respetados). Confirmado con la suite en el builder.
7. Q23 tolerado (§3.1 arriba). La operativa restante (3–8) para el
   usuario/PASSED-MANUAL declarativo o C8 (panel React real).

## 5. Spot-checks de contratos (verify directo sobre código y estado)

- **Rutas/códigos/cuerpos** (Schema §3.2/§3.3/§4.1/§4.2): los 4 mappings
  literales `/api/v1/panel/projects` (+`/{id}`) en `ProjectsController.java`
  (única clase con la ruta — grep); respuestas con cuerpos §3.1
  `{code,message,field?}` — 409 `name_duplicate` con mensaje de AppFlow
  flujo 2 literal, 404 `not_found` con mensaje AppFlow §5 literal, 400
  `field_required`/`field_too_long` (`field` correcto en cada caso) /
  `filter_invalid_value`; el cuerpo ilegible sin `field` (CD-3.9);
  `cuerpo ilegible` handler separado del 409.
- **ProjectDto SIN `summary`** — record de 8 componentes snake_case literales
  (`ProjectDto.java`); `summary` solo aparece en NEGACIONES (grep de tests:
  `doesNotContain("summary")`, `$.summary doesNotExist`); smokes con
  `claves_exactas` de 8 claves exactas × elemento en lista y ficha.
- **`has_key` derivado** — `COLS` = `…, (api_key_hash IS NOT NULL) AS
  has_key` en la ÚNICA fuente de columnas del repo; psql lo verificó
  derivado en cada corrida (nunca leer la columna inexistente).
- **CHECK/UNIQUE de V1** — `projects_name_unique UNIQUE (name)` y
  `projects_status_check CHECK (status IN ('ACTIVE','ARCHIVED'))` en
  `V1__init.sql` (líneas 23–24); el 409 y el 400 de enum salieron ANTES de
  que el CHECK pudiera actuar (500 cero en toda corrida = R-2).
- **ORDER BY determinista** — literal `ORDER BY created_at DESC, id DESC`
  en ambas ramas de `findAll` (constantes `SELECT_TODAS`/`SELECT_FILTRADAS`
  compartiendo `COLS`).
- **Formatter C2 por referencia** — `LoginController.EXPIRES_AT_FORMATTER`
  importado en el repo (cero literal `…'Z'` duplicado en
  `com.projectlog.projects`); confirmado en grep (3 usos, cero segunda
  definición).
- **Maquetas copias** — commits `723a8c1` tocan SOLO
  `frontend/public/mockups/04|05-proyectos.html`; `git diff
  --name-only d4768a6..d9853a0` NO incluye `/docs/**`; `git diff
  --exit-code -- docs/mockups/` vacío; build Vite verificado en apply
  (`dist/mockups/` con los 2 HTML).
- **Tests patrón C2** — sin H2/Testcontainers (ninguna dependencia nueva:
  `pom.xml` no está en el diff); 3 clases (Mvc 25/Serialization 2/Sql 7) +
  las 6 de C2 heredadas verdes; `application-test.yml` intacto.
- **Intangibles diff-0 (lista design §5)** — `git diff --name-only` del
  rango C3 completo = 11 ficheros AÑADIDOS de la lista del design
  §1.1 exacta (5 main + 3 test + 2 mockups + 1 script); NINGÚN fichero
  intocable en ningún commit; `flyway_schema_history` = solo `1`;
  `db/migration/` con un único `V1__init.sql`; `package-info.java` intacto.
- **No-regresión auth (RA-4/Q24/E3.23)** — login/`invalid_credentials`×3/
  `invalid_token` en `/me` y en `/panel/projects`/preflight `20[46]` →
  PASS en [Q] y [V] tras los commits de C3, con los ficheros de
  seguridad y entry point verbatim.

## 6. Hallazgos

| Severidad | Hallazgo |
|---|---|
| **CRITICAL** | Ninguno. |
| **WARNING 1** | Q23 [B] queda BLOQUEADO por entorno (sin driver de ratón): la operativa de clics de las maquetas 04/05 depende de verificación manual del usuario (~2 min, instrucción en §3.1) o de la retirada del puente en C8 (panel React real, N8). NOTA: NO invalida el Done de G5 (plan §7; Q22 PASS obligatorio cubierto). |
| **WARNING 2** | El artefacto QA llegó con 11 bugs propios: 3 corridas espurias BLOQUEO GLOBAL del qa + 2 FAILs espurios en la 1ª corrida de verify. Todos corregidos y corrida canónica limpia (exit 0). Ningún veredicto del corte cambió jamás por defecto de script; causa raíz de proceso en §4.3 (recomendación adoptada para C4+: pasada «smoke of the smoke» o review del corredor antes de la 1ª corrida canónica). |
| **SUGGESTION 1** | `podman-compose up` SIN `--force-recreate` deja contenedores con imagen vieja tras rebuild — ya heredado como OBLIGATORIO en qa-plan C3 §0 y en las cabeceras de ambos scripts: mantener en todos los planes/scripts siguientes (patrón R-nuevo-3 actualizado). |
| **SUGGESTION 2** | Compara de epoch en psql SIEMPRE con `FLOOR(EXTRACT(EPOCH …))::bigint` (el cast desnudo REDONDEA) y ten presente el contexto del booleano psql (`f/t` en concat de texto vs `false/true` como columna). Fijado en el script de QA; semilla para scripts C4+ (en C6 los keyset epochs ya heredan). |
| **SUGGESTION 3** | Retirar el puente de maquetas en C8 junto a la maqueta React real; además tolerar los 401 del entry point (ISO-8859-1) en el helper fetch del mockup ya está hecho por diseño del puente (`response.status` solo). El fix UTF-8 real del entry point sigue en C8/C9 (N11), NO en C3 — correcto. |

### Eco documental por desviación (regla: ninguna modificación de `/docs` sin nueva validación)

| Desviación | ¿Requiere tocar `/docs`? |
|---|---|
| Literales/cuerpos/códigos G1–G3 del controlador | **SÍ están ya validados en el gate de C3 y citados en `spec.md` C3 — NO** (regla del encargo; los docs aprobados no cambian) |
| PATCH semántica G2 (nulls/descripción/null/idempotente/combos) | **NO** — ya en gate y spec |
| `--force-recreate` como patrón de recomposición | **NO** — norma de scripts (heredada en qa-plan C3 §0 + cabeceras de scripts); no es contrato del Schema/AppFlow |
| Tests en imagen builder (host sin JDK 21) | **NO** — patrón de entorno; se hereda a C5+ |
| FLOOR/epoch y booleanos psql | **NO** — detalle de scripts ya fijado |
| Q23 BLOQUEADO | **NO** — nota de QA (plan §7 ya la sanciona); el panel real completo es C8 (07 lo dice) |
| Cero migraciones / schema intacto / keys / DELETE / summary fuera de alcance | **NO** — es el estado contractual ya documentado (Schema §4/§6; 07) |

Ninguna desviación desestabiliza contrato de `/docs`: sin eco nuevo.

## 7. Recomendación final y acciones heredadas

**¿Procede archive? SÍ — proceed.** El corte C3 cumple su Done literal del
07 («CRUD+archivado con smoke de estados (409 y 400 incluidos); maquetas
04/05 servibles contra esta API») con las dos mitades cubiertas:
smoke de estados PASS en triple fuente, Q22 PASS y Q23 en resultado válido
(BLOQUEADO tolerado con nota obligatoria — si el usuario ejecuta la
verificación manual de §3.1, se actualiza a PASSED-MANUAL sin otros
cambios). `mvn test` 60/60 (última corrida de verify, exit 0), intocables
diff-0, HEAD `d9853a0` con 7 commits limpios (producto = 5 de fase;
resto = script de smoke + 3 fixes 100% artefacto), pila 3× healthy.

**Acciones heredadas cortas (ya registradas para los cortes siguientes)**:
- **C5**: reevaluar Testcontainers con la inyección real (batch all-or-nothing
  / CASCADE — G4 diferido; concurrencia real de escritura, N9).
- **C8**: maquetas React REALES y retiro de las copias `:8080/mockups/**`, +
  tolerancia 401 ISO-8859-1 en los helpers fetch (fix UTF-8 del entry point
  C8/C9 — N11; en C3 la disciplina de no-parse funcionó).
- **C9**: smoke canónico `docker compose` (hoy el entorno solo tiene
  `podman-compose`; C1/C2/C3 corrieron con podman — re-verificar la
  equivalencia del compose).
- **Scripts futuros (C4+)**: pasada barata «smoke of the smoke» (bash -n +
  helpers de fumo + login + una llamada autenticada) o review del corredor
  ANTES de la corrida canónica; patrón `--force-recreate` OBLIGATORIO; epoch
  con `FLOOR(EXTRACT(...))`; booleano psql consciente de contexto; Q23-like
  [B] con driver (si existe) o el manual de 2 min con flag declarativo
  (`QA_Cn_Q*_PASSED_MANUAL=1`).
- Verificación manual pendiente del usuario (única deuda viva, ~2 min):
  procedimiento de §3.1 para cerrar Q23 como PASSED-MANUAL si se desea.