---
change: "C3"
artifact: qa-plan
last_updated: 2026-10-21
status: done
---

# QA Plan — C3 · Proyectos: CRUD + archivado

> Plan de pruebas de la fase sdd-qa (delegación A: qa-plan) para C3.
> Escrito EXCLUSIVAMENTE desde `spec.md` (R3.1–R3.20, E3.1–E3.26, gates
> G1–G5) y `design.md` (CD-3.1…CD-3.14, R-1…R-6) del corte C3. Regla de
> señalización: todo FAIL futuro se formula **contra un caso de ESTE plan
> (ID Q/RA) y su ancla del spec/design** — nunca como observación genérica.
> El plan no reutiliza `scripts/qa_c2_smoke.sh` (NO invocarlo jamás: su Q13
> ejecuta `down -v` y destruiría el volumen con datos del smoke).

## 0. Precondiciones globales (comunes a todos los casos [S]/[B])

| Precondición | Cómo se garantiza/verifica |
|---|---|
| Pila podman-compose sana | `podman-compose up -d --build --force-recreate` (el `--force-recreate` es OBLIGATORIO: sin él corre la imagen vieja — lección R-nuevo-3 de C2, heredada). Salud por `podman inspect --format '{{.State.Health.Status}}' <contenedor_backend|db>` == `healthy` y por `podman ps`. NUNCA `restart` para recomponer; NUNCA `down -v`. |
| Anti-veneno de env | Cualquier recomposición se ejecuta con las variables del proceso UNSET (`env -i HOME=… PATH=…`) y `.env` cargada de forma robusta (leída por compose, no exportada a ciegas del shell). El smoke C3 **no necesita mutar `.env`** (CD-3.14: no hay variable de C3 en compose); si una re-ataque exigiera tocarlo, vale la secuencia backup/restore de §6.5. |
| JWT del admin (fixture) | `POST /api/v1/panel/auth/login` con las credenciales dev del `.env` (seed idempotente de C2). Devuelve `{token, username, expires_at}` con `expires_at` = `iat + JWT_TTL_HOURS`, formato `Z`. Header en todas las llamadas: `Authorization: Bearer $TOKEN`. Refresh del token si expira durante la sesión de QA (TTL 8 h). |
| Idempotencia de datos (fixture) | Sufijo por ejecución `TS="$(date +%Y%m%d%H%M%S)"`; todos los proyectos del smoke se llaman `smoke-c3-${TS}-{a,b,c,unk,…}`. Repetible sin limpiar volumen y SIN depender de borrado (DELETE es C10): las aserciones de lista son de **membresía** (no exclusividad) salvo el filtro de estado, que se juzga sobre los miembros del TS propio. |
| 401 sin parse estricto | El 401 del entry point sale ISO-8859-1 (heredado C2): los casos verifican **status y, tolerantemente, el código**; nunca parse JSON estricto del body de un 401. |
| Estado BD observable | `psql` vía el servicio BD de compose contra la tabla `projects` de `V1__init.sql`: filas, `status`, `description IS NULL`, `EXTRACT(EPOCH FROM updated_at)`, `has_key` DERIVADO como `(api_key_hash IS NOT NULL)` — nunca leer una columna `has_key` que no existe (R3.7). |

Orden de ejecución del smoke de estados (heredado del design CD-3.14):
**crear → parchear → listar** (el `-a` queda ARCHIVED antes del bloque de
filtros de lista, de modo que `?status=ARCHIVED` tenga un miembro del TS).

## 1. Tabla de casos Q1..Q29

Tipos: **[U]** unitario JUnit puro (sin Spring, sin BD) · **[M]** MockMvc
`@WebMvcTest` con repo mockeado · **[S]** smoke curl sobre la pila podman ·
**[B]** verificación de navegador · **[D]** verificación estática (diff/git).
Cada caso tiene su veredicto propio en el informe de qa (§7).

### 1.A Creación — POST (E3.1–E3.6)

| ID | Escenario | Tipo | Técnica | Precondiciones | Aserciones obligatorias |
|---|---|---|---|---|---|
| Q1 | E3.1 — POST 201 con name+description | [S] | curl POST `/api/v1/panel/projects` con `{"name":"smoke-c3-${TS}-a","description":"<texto ≤2000>"}` + Bearer | Pila healthy, JWT admin, TS fresco | **201**; JSON con EXACTAMENTE las claves `id, name, description, status, created_at, updated_at, api_key_prefix, has_key` (verificar por conjunto: sin `summary`, sin extras); `status:"ACTIVE"`; `api_key_prefix:null`; `has_key:false`; `created_at`/`updated_at` casan `^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$`; `description` ecoada. psql: fila existe, `status='ACTIVE'`, `(api_key_hash IS NOT NULL)=false` |
| Q2 | E3.2 — POST 201 sin description | [S] | curl POST con solo `{"name":"smoke-c3-${TS}-b"}` | Q1 previo (pila y JWT) | **201**; `description:null`; resto idéntico a Q1 (8 claves, `status:"ACTIVE"`, `has_key:false`, `api_key_prefix:null`, fechas `Z`). psql: `description IS NULL` |
| Q3 | E3.3 — POST 409 name_duplicate | [S] | POST **inmediato** (ida y vuelta) con el MISMO `name` `smoke-c3-${TS}-a` | El proyecto de Q1 existe | **409** con cuerpo estándar `{"code":"name_duplicate","message":"Ya existe un proyecto con ese nombre","field":"name"}`. psql: COUNT de filas con ese name == **1** (no se insertó segunda fila; datos conservados) |
| Q4 | E3.4 — POST 400 name ausente/vacío | [S] | POST con `{}` y con `{"name":""}` | Ídem | **400** en ambos con `{"code":"field_required","field":"name"}` + `message` descriptivo en español. psql: ninguna fila nueva |
| Q5 | E3.5 — POST 400 name >200 | [S] | POST con `name` de **201** caracteres (`printf 'x%.0s' $(seq 1 201)`), description válida o ausente | Ídem | **400** `{"code":"field_too_long","field":"name"}`. (Borde 200 válido se fija en Q27 [M]) |
| Q6 | E3.6 — POST 400 description >2000 | [S] | POST con `name` válido y `description` de **2001** caracteres | Ídem | **400** `{"code":"field_too_long","field":"description"}`. psql: fila no creada. (Borde 2000 válido se fija en Q27) |

### 1.B Listado y ficha — GET (E3.7–E3.11)

| ID | Escenario | Tipo | Técnica | Precondiciones | Aserciones obligatorias |
|---|---|---|---|---|---|
| Q7 | E3.7 — GET lista 200 | [S] | curl GET `/api/v1/panel/projects` + Bearer | ≥1 proyecto del TS creado (Q1/Q2) | **200**; el body es un **array plano** (`jq 'type=="array"'`), NO objeto con `items`/`next_cursor`; cada elemento tiene las 8 claves del DTO; contiene los miembros `smoke-c3-${TS}-*` creados (membresía, no exclusividad — tolera sobras de corridas previas) |
| Q8 | E3.8 — GET lista con `?status=` | [S] | GET `?status=ACTIVE` y `?status=ARCHIVED` (tras archivar `-a` en Q14) | Orden: crear→parchear→listar | **200** ambos; `?status=ACTIVE` contiene `-b` (ACTIVE del TS) y NO contiene `-a` (ARCHIVED del TS); `?status=ARCHIVED` contiene `-a` y NO los ACTIVE del TS; sin query → ambos presentes. Si es la primera corrida del volumen, `?status=ARCHIVED` previo al archivado devuelve **200 `[]`** (vacío legítimo, AppFlow §4) — aserción condicional: si hay sobras archivadas de TS previos, se verifica sólo forma `[]`/array |
| Q9 | E3.9 — GET filtro inválido → 400 | [S] | GET `?status=active` y `?status=ARCHIVE` y `?status=` (vacío — CD-3.11) | Ídem | **400** en los tres con `{"code":"filter_invalid_value","field":"status"}` — fail-fast, nunca filtro silenciado; nunca 200 con filtrado silencioso ni 500 |
| Q10 | E3.10 — GET ficha existente 200 | [S] | GET `/api/v1/panel/projects/{id}` de `-a` | `-a` existe | **200** con el DTO completo, mismas 8 claves exactas que Q1, `id` coincide, datos coherentes con lo parcheado hasta el momento |
| Q11 | E3.11 — GET ficha inexistente 404 | [S] | GET `/api/v1/panel/projects/999999` + Bearer | Ídem | **404** con cuerpo JSON estándar `{"code":"not_found", "message":…}` (nunca cuerpo vacío ni lista vacía — AppFlow §5) |

### 1.C Edición y archivado — PATCH (E3.12–E3.20)

| ID | Escenario | Tipo | Técnica | Precondiciones | Aserciones obligatorias |
|---|---|---|---|---|---|
| Q12 | E3.12 — PATCH solo name | [S] | `sleep 2`; PATCH `/{idA}` con `{"name":"smoke-c3-${TS}-a2"}` | `-a` existe con su `updated_at` capturado | **200**; `name` cambiado; `description` y `status` INTACTOS (ausente = sin cambio, G2); `created_at` SIN cambiar; `updated_at` estrictamente posterior al previo (epoch `<`, resolución 1 s — el `sleep 2` lo hace observable). psql: `name` nuevo, resto de columnas intactas |
| Q13 | E3.13 — PATCH description texto y `null` limpia | [S] | PATCH `{"description":"texto nuevo"}` → luego `sleep 2` y PATCH `{"description":null}` | Ídem | Ambos **200**; tras el primero la descripción cambia (`updated_at` refrescado); tras el segundo `description:null` con `name`/`status` intactos y `updated_at` refrescado de nuevo. psql tras el segundo: `description IS NULL` (columna NULL real, explore §1.4) |
| Q14 | E3.14 — Archivar / desarchivar | [S] | PATCH `{"status":"ARCHIVED"}` sobre `-a`; luego re-GET ficha + lista `?status=ARCHIVED`; luego PATCH `{"status":"ACTIVE"}` | Ídem | **200** con `status:"ARCHIVED"`; el dato persistido e INTACTO al releer (mismo `name`/`description` — «Archivar nunca borra nada», regla 3); la lista ARCHIVED lo muestra; **200** de vuelta con `status:"ACTIVE"` (reversible, §4.2). Ni la key ni los logs se tocan (aquí: `api_key_prefix:null`, `has_key:false` en ambos relleos — regla 11) |
| Q15 | E3.15 — PATCH `{}` idempotente | [S] | `sleep 2`; PATCH `/{id}` con cuerpo `{}` | `updated_at` previo capturado (epoch BD y del DTO) | **200** con el DTO actual SIN cambios y `updated_at` INTACTO (igualdad ESTRICTA de epoch tras `sleep 2`: si hubiera UPDATE indebido, `now()` divergiría ≥2 s > resolución de 1 s del formatter — G2/R3.6). [L] lo corrobora: sin llamada a `update` (Q27) |
| Q16 | E3.16 — PATCH name/status null → 400 | [S] | PATCH con `{"name":null}` y con `{"status":null}` | Ídem | **400** `{"code":"field_required","field":"name"}` en el primero y **400** `{"code":"field_required","field":"status"}` en el segundo (G2/G3); fila intocada (psql: status/name/description/updated_at sin cambiar) |
| Q17 | E3.17 — PATCH status fuera de enum → 400 antes de BD | [S] | PATCH `{"status":"ACTIVO"}`, `{"status":"active"}`, `{"status":"ARCHIVE"}` (×3) | Ídem | **400** en los tres con `{"code":"filter_invalid_value","field":"status"}`; NUNCA **500** (R-2: si escapara, `projects_status_check` daría 500) ni mutación de la fila (psql: `status` sin cambiar, `updated_at` sin cambiar) |
| Q18 | E3.18 — PATCH choque de name → 409 | [S] | PATCH `/{idA}` con `{"name":"<name de B>"}` | A y B del TS existen; `sleep 2` previo | **409** `{"code":"name_duplicate", …,"field":"name"}`; re-GET de B: intacto (name/status/description propios) |
| Q19 | E3.19 — PATCH id inexistente → 404 | [S] | PATCH `/999999` con `{"description":"x"}` (campo válido) | Ídem | **404** `{"code":"not_found"}`; sin UPDATE (el 404 antecede al update — CD-3.8) |
| Q20 | E3.20 — `updated_at` cambia con PATCH y NO con GET | [S] | Crear `-c`; capturar; GET ficha ×3 separados ≥1 s; `sleep 2`; PATCH `{"description":"touch"}`; re-GET; comparativas de epoch (DTO y psql) | `-c` recién creado | GETs SIN PATCH devuelven el MISMO `updated_at` (leer NUNCA modifica); tras el PATCH con ≥1 campo (`sleep 2` previo), `updated_at` estrictamente postero (epoch `<`); `created_at` invariable durante todo el caso |

### 1.D Autenticación heredada (E3.21)

| ID | Escenario | Tipo | Técnica | Precondiciones | Aserciones obligatorias |
|---|---|---|---|---|---|
| Q21 | E3.21 — 401 sin JWT en las 4 rutas | [S] | GET lista, POST, GET `/{id}`, PATCH — las 4 SIN header `Authorization` | Pila healthy | **401** en las 4 (S19: nacen protegidos por la cadena 2, diff 0 exigido); código/estado verificados con decodificación TOLERANTE del body (ISO-8859-1 del entry point — sin parse JSON estricto, R3.19). Nada más de seguridad que verificar (R3.20) |

### 1.E Maquetas 04/05 servibles (E3.22, gate G5) — dos casos: [S] curl y [B] navegador

| ID | Escenario | Tipo | Técnica | Precondiciones | Aserciones obligatorias |
|---|---|---|---|---|---|
| Q22 | E3.22 — copias servidas con fetch real (curl) | [S] | curl `GET :8080/mockups/04-proyectos.html` y `GET :8080/mockups/05-proyecto-detail.html`; y `GET :8080/docs/mockups/04-proyectos.html` | Imagen frontend reconstruida con disciplina anti-veneno (up -d --build --force-recreate) | Ambas copias → **200** `text/html`; el HTML contiene las marcas de fetch `/api/v1/panel/auth/login` y `/api/v1/panel/projects` (04) y `/api/v1/panel/projects` + uso de `?id=` (05). `GET /docs/mockups/04-proyectos.html` NO devuelve el HTML original del repo (el nginx de la pila no sirve `/docs/**`: el body NO coincide con el fichero del repo; se permite el fallback a `index.html` del SPA o 404). `/docs/mockups/**` queda intacto en disco (lo cubre Q25, diff 0) |
| Q23 | E3.22 — verificación de ratón sobre las maquetas | [B] | Navegador (automatizado si existe driver; si no, MANUAL documentado §2 abajo) sobre `http://localhost:8080/mockups/04-proyectos.html` y `…/05-proyecto-detail.html` | Q22 verde; JWT emitible con credenciales dev del `.env`; ≥2 proyectos del TS en la BD (uno ACTIVE, uno ARCHIVED tras Q14) | Login inline → lista pinta los proyectos REALES; toggle del filtro cambia lo visible según `?status=`; clic **Archivar** (04 o 05) ejecuta un **PATCH `{status}` REAL** (se verifica por BD: psql muestra `status='ARCHIVED'` y/API re-GET) y la UI refleja la etiqueta «Archivado» + el filtro ARCHIVED lo incluye; clic **Desarchivar** lo revierte (`status='ACTIVE'` en BD y en UI). Resultado posible: `PASS` (automatizado), `PASSED-MANUAL` (procedimiento §2 seguido) o `BLOQUEADO (sin navegador)` — NUNCA inventado |

### 1.F No-regresiones C2 (E3.23/E3.24 — obligatorias, R3.20)

| ID | Escenario | Tipo | Técnica | Precondiciones | Aserciones obligatorias |
|---|---|---|---|---|---|
| Q24 | E3.23 — cadenas auth de C2 intactas | [S] | Re-ejecución local (NO delegada, NO script de C2): login OK; 401 `invalid_credentials` ×3 (contraseña mala, usuario mal, cuerpo roto); 401 `invalid_token` en `/me`; 401 `invalid_token` en `GET /api/v1/panel/projects` SIN JWT (S19 con controller real); preflight CORS `OPTIONS /api/v1/panel/projects` con `Origin: $FRONTEND_ORIGIN` + `Access-Control-Request-Method: PATCH` | Pila healthy | Login → **200** `{token, username, expires_at}` con `expires_at` formato `Z`; los 401 de login → **401** `{"code":"invalid_credentials"}` idéntico en las 3 ramas; `/me` sin JWT → **401** `invalid_token` (código tolerante); `/panel/projects` sin JWT → **401** (con controller ya presente); preflight → **20[46]** (200 o 204, divergencia ya registrada en C2) con ACAO reflejada exacta y PATCH presente en `Access-Control-Allow-Methods`. Sin tocar `CorsConfig` (Q25) |
| Q25 | E3.24 — diff 0 en intocables + zero-migraciones | [D] (+psql de soporte) | `git diff --exit-code` (o equivalente estado repo) sobre: `SecurityConfig`, `CorsConfig`, `RestAuthenticationEntryPoint`, `JwtAuthFilter`, `TokenService`, `AdminSeeder`, `StartupSecretsValidator`, `META-INF/spring.factories`, `application.yml`, `application-test.yml`, `pom.xml`, `compose.yaml`, `frontend/nginx.conf`, `frontend/Dockerfile`, `frontend/vite.config.ts`, `docs/mockups/**` (6 HTML), `scripts/qa_c2_smoke.sh`, `scripts/ws_echo_test.py`, `V1__init.sql`; y `ls backend/src/main/resources/db/migration/` | Repo en estado post-apply C3 | **0 líneas de diff** en TODOS; cero ficheros `V[2-9]__*` en `db/migration/`; psql: `SELECT version FROM flyway_schema_history` → solo `1` (Flyway "up to date", E3 de C1). Cierre del smoke: pila `healthy` |

### 1.G Verificación ligera sin contenedor (E3.25/E3.26 + defensas del design)

| ID | Escenario | Tipo | Técnica | Precondiciones | Aserciones obligatorias |
|---|---|---|---|---|---|
| Q26 | E3.25 — ProjectDto serialization | [U] | `ProjectDtoSerializationTest`: JUnit puro + `ObjectMapper` de Boot, sin Spring context ni BD | `application-test.yml` INTOCABLE (n/a aquí: no hay context) | JSON con EXACTAMENTE las 8 claves §3.3 **sin `summary`**; `description:null` admitido; `status()` serializa el literal `"ACTIVE"`; `created_at`/`updated_at` terminan en `Z` (formatter patrón C2 — sin segunda convención, R3.8) |
| Q27 | E3.26 — matriz de validación y mapeo de errores | [M] | `ProjectsControllerMvcTest`: `@WebMvcTest(ProjectsController)` + cadena importada (fixture réplica de `PanelAuthMvcTest`), `@MockitoBean ProjectsRepository`, `@ActiveProfiles("test")` | `application-test.yml` INTOCABLE (S29); JWT de prueba del fixture | Matriz COMPLETA R3.15/CD-3.6: `name` vacío → 400 `field_required(name)`; `name` **200 chars válido → 201** / **201 chars → 400 `field_too_long(name)`**; `description` **2000 válido / 2001 → 400 `field_too_long(description)`**; `?status=` fuera de enum → 400 `filter_invalid_value(status)` con **`verifyNoInteractions(repo)`** (fail-fast demostrado); `status` de PATCH fuera de enum → 400 ANTES de tocar el repo; `{"name":null}`/`{"status":null}` → 400 `field_required(name|status)`; `{}` → **200 sin invocar `update`** (`verify(repo, never()).update(...)`); `{"description":null}` → llamada a `update` con combo `{description}` correcto; `DuplicateKeyException` lanzada por el mock → **409 `name_duplicate`** con cuerpo estándar; `Optional.empty()` → **404 `not_found`**; DTO de salida con claves exactas sin `summary` |
| Q28 | Defensa CD-3.5 — construcción del SET del UPDATE | [U] | `ProjectsUpdateSqlTest`: JUnit puro sobre la función `conjuntoSet(Set<String>)` — NO ejecuta SQL (R3.18: el unit no ejercita el SQL; la ejecución va en smoke/review) | Ninguna (sin BD) | Los **7 combos** producen el SET literal esperado; TODOS incluyen `updated_at = now()`; NINGUNA rama menciona `api_key_hash`/`api_key_prefix`/`key_created_at` (R-4); prefijo `UPDATE projects SET ` y sufijo ` WHERE id = ? RETURNING …` presentes; combo vacío → `IllegalArgumentException` (defensa) |
| Q29 | Defensa S19 — 401 sin JWT en las 4 rutas dentro del slice | [M] | Caso dentro de `ProjectsControllerMvcTest` (barato, no exigido por E3.x): las 4 rutas sin header Bearer | Fixture del Q27 | **401** en las 4 (corrobora en [L] que el código nuevo nace tras el filtro de la cadena 2) |

## 2. Caso [B] — instrucciones reproducibles de verificación de navegador (Q23)

**Preparación**: pila healthy tras `up -d --build --force-recreate` (anti-veneno,
R-nuevo-3); credenciales dev = las del `.env` (`ADMIN_USERNAME`/`ADMIN_PASSWORD`
— nombres literales según compose); datos en BD: ≥1 proyecto del TS ACTIVE y
≥1 ARCHIVED (tras ejecutar Q14).

**URL base**: `http://localhost:8080/mockups/04-proyectos.html`

Pasos esperados (cada uno con su observación):

1. **Abrir 04** → aparece el login inline (form); NO un panel vacío con error JS.
2. **Loguear con credenciales dev** → el login llama `POST /api/v1/panel/auth/login`
   (mismo origen, sin CORS visible en consola) y guarda el token
   (`sessionStorage["mockups_token"]`); el panel sustituye al form.
3. **Lista real** → se pintan tarjetas con `name`, `description`, etiqueta de
   estado (`Activo`/`Archivado`), línea `api_key_prefix … has_key`
   (`sin key · has_key: false` para proyectos del smoke), `created_at` formato
   `Z` — corresponden a los datos REALES de la pila (fetched de
   `GET /api/v1/panel/projects`).
4. **Filtro** → toggle Todos/Activos/Archivados cambia lo visible según
   `?status=` (coherente con Q8); estado vacío de un filtro → «No hay proyectos
   todavía».
5. **Archivar desde 04** → clic en botón `Archivar` de una tarjeta ACTIVE →
   ejecuta PATCH `{"status":"ARCHIVED"}` real (verificar: `location.reload()`
   tras el PATCH; luego psql —`status='ARCHIVED'`— y/o re-GET por API) y la UI
   muestra la etiqueta «Archivado» + el botón pasa a `Desarchivar`.
6. **Abrir 05** → desde la tarjeta (enlace `<a>` relativo con `?id=…`) → la
   ficha pinta name, status, description, api_key_prefix/has_key y
   created_at/updated_at desde `GET /api/v1/panel/projects/{id}`.
7. **Archivar/Desarchivar desde 05** → clic en el botón → PATCH `{status}`
   real → verificar el estado cambiado en BD y en la ficha re-cargada.
8. **Desarchivar** (ciclo completo) → de vuelta a `status='ACTIVE'` en BD y «Activo» en la UI.
9. **Originales intactos** → `/docs/mockups/04-proyectos.html` NO es lo que se
   acaba de operar (la URL operada es `/mockups/…`, servida de `public/`); los
   ficheros originales no cambian (lo certifica Q25, diff 0).

**Regla de resultado (obligatoria)**: si hay navegador automatizable
disponible en el entorno (driver ya presente; NO se instalan dependencias para
esto), se automatiza y el veredicto es `PASS`; si NO lo hay, el paso anterior
se ejecuta como **procedimiento MANUAL paso a paso** (los 9 pasos de arriba)
y el veredicto es `PASSED-MANUAL` — o `BLOQUEADO (sin navegador)` si ni
siquiera puede ejecutarse el manual (p. ej. entorno sin display y sin driver).
**Nunca se inventa un resultado**: un [B] sin evidencia de los pasos es
BLOQUEADO, no PASS.

## 3. Re-ataques independientes (exigidos, RA-1..RA-4)

Re-ataques pensados DESDE el plan (atacan lo que el diseño afirma, no solo
lo que el spec lista). Los cuatro son **bloqueantes** (§7).

### RA-1 — R-1: 409 por carrera del `name_duplicate` [bloqueante]

La carrera temporal NO es reproducible en C3 (sin Testcontainers — G4; y [L]
no usa hilos). El plan EXIGE la demo en DOS mitades, ambas obligatorias:

- **Mitad (i) — DUPLICIDAD simple, [S]** (Q3 + Q18): el smoke debe demostrar
  409 real END-TO-END contra Postgres 17: (a) segundo POST inmediato con el
  mismo `name` → 409 `name_duplicate` (constraint `projects_name_unique`
  disparada por la BD, no por un check-then-insert — verificar psql COUNT==1);
  (b) PATCH que cambia `name` al de otro proyecto → 409 (mismo garante, vía
  UPDATE). Si el 409 viniera de un pre-chequeo SELECT en el código en lugar
  de la constraint, es hallazgo contra CD-3.4/R3.5 (evidencia: revisión del
  SQL en review — el unit no ejecuta SQL).
- **Mitad (ii) — TRADUCCIÓN de la excepción, [M]** (dentro de Q27): el mock
  del repo lanza `DuplicateKeyException` y el `@ExceptionHandler` del
  controller produce **409 `name_duplicate`** con cuerpo estándar
  `{"code","message","field":"name"}`. Esto materializa la mitad del mecanismo
  que el smoke no puede aislar.
- **Contingencia EXPLÍCITA para el qa-run**: si al ejecutar/inspeccionar la
  suite no existe ese test [M] de traducción (`DuplicateKeyException` → 409)
  en el corte C3, es **hallazgo FAIL de RA-1** aunque el smoke (i) pase —
  porque la mitad de traducción quedaría sin demostración y R3.5 la exige
  ("la misma técnica DEBE aplicarse al PATCH").
- Cobertura estructural explícita como NO verificada temporalmente → N9 (§4).

### RA-2 — Matriz adversarial del PATCH (y cuerpos del POST) [bloqueante]

Secuencia sobre el proyecto `-a` (o `-c`) del TS; cada paso con captura
previa/posterior (DTO + psql):

1. `{}` → 200 idempotente, `updated_at` intacto (compartido con RA-3).
2. `{"description":null}` sobre un proyecto CON description → 200, limpieza
   real (`description IS NULL` en psql; DTO `description:null`).
3. `{"name":null}` → 400 `field_required(name)`; re-GET posterior: fila
   idéntica a la previa (nada mutado).
4. Ciclo de estado completo: `{"status":"ARCHIVED"}` → 200; **re-archivar**
   (segundo `{"status":"ARCHIVED"}` seguido) → 200 de nuevo (idempotente del
   archivado); `{"status":"ACTIVE"}` → 200 (desarchivar); **re-archivar una
   tercera vez** → 200. En todo el ciclo: `name`/`description` intactos,
   `has_key:false`, `api_key_prefix:null`.
5. **Campos desconocidos ignorados**: POST `{"name":"smoke-c3-${TS}-unk","nmae":"x"}`
   → 201 con DTO correcto; PATCH con `{"descripton":"hack","status":"ACTIVE","name":"<nuevo>"}`
   → 200 aplica `status` y `name`, IGNORA la desconocida, y el DTO devuelto
   CONSERVA los datos reales (re-GET confirma). NOTA DE SEÑALIZACIÓN: el
   trato de campos desconocidos es decisión menor **CD-3.9** (spec §1.D lo
   dejó sin gate) — un FAIL aquí se ancla al plan + CD-3.9, no a un E3.x.
6. **Cuerpos no-JSON → 400**: POST/PATCH con body `not-json{{{` y PATCH sin
   body → **400 `field_required` SIN `field`** (CD-3.9: "El cuerpo de la
   petición debe ser JSON válido"; sin inventar `body_invalid`). Igual que en
   5: FAIL se ancla al plan + CD-3.9. El PATCH con JSON válido `{}` NO cae
   aquí (va en el paso 1).
7. **Conservación de datos**: tras la matriz completa, psql del TS: los
   `name`/`status`/`description` finales son EXACTAMENTE los esperados y
   `updated_at` solo cambió en los PATCH que trajeron campos (cruce con RA-3).

### RA-3 — Semántica de `updated_at` [bloqueante]

Todas las comparaciones en **epoch** (DTO + psql `EXTRACT(EPOCH FROM
updated_at)`), con **`sleep 2`** entre observaciones (resolución del
formatter = segundos; el `sleep` hace fiables ambas direcciones):

- (a) Crear `-c`, capturar `updated_at`; repetir GET ficha ≥2 veces separadas
  ≥1 s → igualdad ESTRICTA: **GET no actualiza** (leer nunca modifica).
- (b) PATCH `{}` → 200; capturar → igualdad ESTRICA con el valor previo tras
  `sleep 2`: **PATCH sin campos no ejecuta UPDATE** (G2/R3.6 — un UPDATE
  indebido con `now()` divergiría ≥2 s y sería detectable pese al truncado a
  segundos).
- (c) `sleep 2`; PATCH `{"name":…}` (o cualquier campo presente) →
  `updated_at` estrictamente POSTERIOR al previo (epoch `<`): **PATCH real sí
  actualiza**. Además `created_at` invariable en (a)–(c) y en toda la RA-2,
  y cada rama con campos refresca (cruce con Q12/Q13/Q14).
- Anclaje BD: `updated_at` nunca debe depender de un trigger (no existe en
  V1) — si un PATCH sin campos altera `updated_at`, FAIL contra R3.6/G2.

### RA-4 — No-regresiones auth C2 [bloqueante — re-ejecutadas aquí, NO delegadas]

Se re-ejecutan en esta qa (materializadas en Q24 y la parte de pila de Q25):

- Login 200 `{token, username, expires_at}` con forma exacta (Q24).
- 401 `invalid_credentials` en las 3 ramas, literal idéntico (Q24).
- 401 `invalid_token` en `/me` y, con controller ya presente, en
  `GET /api/v1/panel/projects` sin JWT (S19) (Q21 + Q24).
- Preflight CORS con `PATCH` en ruta nueva → **20[46]** con ACAO reflejada y
  PATCH en Allow-Methods (Q24).
- Salud de la pila: `podman inspect .State.Health.Status` == `healthy` en
  backend y db al abrir y al cerrar (Q25/Q21 precondición).
- Seed idempotente C2 intacto: el login del admin funciona tras la recomposición
  (S21–S24) — sin semillas nuevas (Q25: flyway_history solo versión 1).

## 4. NOT cubierta (para verify — límites declarados del plan)

| Nº | Fuera del alcance de este plan | Por qué |
|---|---|---|
| N1 | Rendimiento/carga (latencias, concurrencia de tráfico) | No hay umbral normativo en spec/Schema; fuera del patrón C2 |
| N2 | Orden de la lista MÁS allá del determinismo | E3.7/E3.8 verifican membresía por diseño (CD-3.10/riesgo ORDER BY: timestamps del mismo TS pueden empatar); el criterio `ORDER BY created_at DESC, id DESC` se fija por review del SQL, no por aserción en smoke |
| N3 | `DELETE /projects/{id}` + `X-Confirm-Name` (CASCADE) | Es C10 (Schema §3.2 P1); C3 no debe tener DELETE — solo se verifica su AUSENCIA incidental (rutas exactas de R3.9) |
| N4 | API keys: regenerate/DELETE, prefijo/hash con valores reales | Es C4; C3 solo garantiza `api_key_prefix:null` + `has_key:false` (R3.7) y que el UPDATE no toque columnas de key (Q28) |
| N5 | Regla 403 `project_archived` en la ingesta | Es C5 (nota de cruce del 07): en C3 existe solo el DATO `status` |
| N6 | `summary` / `include=summary` / `GET …/{id}/summary` | Es C10; el plan verifica solo que `summary` esté AUSENTE del DTO (Q1/Q26) |
| N7 | Keyset pagination `{items, next_cursor}` | Exclusivo de LOGS (C6); el plan verifica lo contrario (array plano, Q7) |
| N8 | Panel React real `/proyectos` + estados AppFlow §4 completos + formularios nuevo/editar | Es C8; el [B] de Q23 cubre solo el mínimo del gate G5 (puente desechable) |
| N9 | Concurrencia REAL de escritura (hilos/ventanas de carrera temporales) | No reproducible sin Testcontainers (diferidos a C5 por G4); R-1 queda cubierto ESTRUCTURALMENTE (constraint BD [S] + traducción [M]) — RA-1 declara este límite |
| N10 | Echo WS `/ws/logs` | Se ejercitará en el qa de C7; aquí solo diff 0 (Q25) |
| N11 | Parse estricto del body de los 401 / fix UTF-8 | Warning heredado de C2; fix en C8/C9 — el plan aplica disciplina de no-parse, no lo corrige |
| N12 | Trimming/normalización de `name`/`description` | CD-3.6: sin normalización («errores nunca silenciosos»); solo se prueba el borde blank→`field_required`; un name con espacios se acepta literal — sin ancla para otra cosa |
| N13 | RBAC/multiusuario | Solo existe el admin seed; no hay más roles en v1 hasta su corte |
| N14 | Rate limiting/429 y TLS/dominio | No existen en v1 (Schema §3.1; ADR-23) |

## 5. Ejecución por tipo (resumen)

- **[U]/[M]** (Q26–Q29): `mvn test` — arrancan en segundos, sin contenedor ni
  BD; respetan `application-test.yml` (INTOCABLE, S29).
- **[S]** (Q1–Q22, Q24): `scripts/qa_c3_smoke.sh` (design CD-3.14, patrón
  C2 replicado SIN `source` del script de C2) + los re-ataques RA-1..RA-4
  (los pasos adicionales pueden ejecutarse como curl puntuales sobre la misma
  pila con el mismo `TS`).
- **[B]** (Q23): §2 de este plan.
- **[D]** (Q25): git/diff + `ls db/migration` + psql Flyway.

## 6. Comandos

### 6.0 Convenciones

```bash
cd /home/acuelop369/Escritorio/Proyectos/Project_Log
TS="$(date +%Y%m%d%H%M%S)"
BIN=podman-compose                     # podman 5.8.7
# Nombres de servicio según compose.yaml (resolver al ejecutar):
SVC_BACKEND=<svc_backend>  SVC_DB=<svc_db>
HOST=8080                                # nginx: API y mockups por el proxy C1
AUTH="Authorization: Bearer $TOKEN"
```

### 6.1 Unit/MockMvc [U]/[M]

```bash
mvn -q -f backend/pom.xml test    # o ./mvnw si existe wrapper — ejecuta Q26–Q29
```

### 6.2 Flujo podman (anti-veneno R-nuevo-3 — OBLIGATORIO `--force-recreate`)

```bash
# Variables del proceso UNSET; .env la lee compose; recreate NUNCA restart
env -i HOME="$HOME" PATH="$PATH" $BIN up -d --build --force-recreate
podman ps --format '{{.Names}}\t{{.Status}}'
podman inspect --format '{{.State.Health.Status}}' <ctr_backend> <ctr_db>   # healthy
# PROHIBIDO: $BIN down -v  ·  $BIN restart (no sustituye recreate)
```

### 6.3 JWT del admin (fixture) y curl exactos por caso

```bash
TOKEN=$(curl -s -X POST "http://localhost:$HOST/api/v1/panel/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r '.token')

# Q1: 201 con name+description (claves exactas, Z, has_key false)
curl -s -o /tmp/q1.json -w '%{http_code}\n' -X POST "http://localhost:$HOST/api/v1/panel/projects" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"name\":\"smoke-c3-${TS}-a\",\"description\":\"Proyecto del smoke C3\"}"
jq -e 'keys == ["id","name","description","status","created_at","updated_at","api_key_prefix","has_key"] or ((keys|sort) == (["api_key_prefix","created_at","description","has_key","id","name","status","updated_at"]|sort))' /tmp/q1.json
jq -e '.status=="ACTIVE" and .api_key_prefix==null and .has_key==false and (.created_at|test("^[0-9-]{10}T[0-9:]{8}Z$")) and (.updated_at|test("^[0-9-]{10}T[0-9:]{8}Z$"))' /tmp/q1.json

# Q3: 409 duplicado inmediato (mismo name de Q1)
curl -s -w '\n%{http_code}\n' -X POST "http://localhost:$HOST/api/v1/panel/projects" \
  -H "$AUTH" -H 'Content-Type: application/json' -d "{\"name\":\"smoke-c3-${TS}-a\"}"
#    esperar: 409 + {"code":"name_duplicate","message":…,"field":"name"}

# Q4–Q6: 400s de POST
L201=$(printf 'x%.0s' $(seq 1 201))
curl -s -w '\n%{http_code}\n' -X POST "http://localhost:$HOST/api/v1/panel/projects" -H "$AUTH" \
  -H 'Content-Type: application/json' -d "{\"name\":\"$L201\"}"            # 400 field_too_long/name
D2001=$(printf 'y%.0s' $(seq 1 2001))
curl -s -w '\n%{http_code}\n' -X POST "http://localhost:$HOST/api/v1/panel/projects" -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"smoke-c3-${TS}-d\",\"description\":\"$D2001\"}"          # 400 field_too_long/description

# Q7–Q9: lista y filtro
curl -s -H "$AUTH" "http://localhost:$HOST/api/v1/panel/projects" | jq 'type=="array"'
curl -s -H "$AUTH" "http://localhost:$HOST/api/v1/panel/projects?status=ACTIVE"    | jq .
curl -s -H "$AUTH" "http://localhost:$HOST/api/v1/panel/projects?status=ARCHIVED"  | jq .
curl -s -o /dev/null -w '%{http_code}\n' -H "$AUTH" "http://localhost:$HOST/api/v1/panel/projects?status=active"   # 400
curl -s -o /dev/null -w '%{http_code}\n' -H "$AUTH" "http://localhost:$HOST/api/v1/panel/projects?status=&"       # 400 (CD-3.11)

# Q10–Q11: ficha y 404
curl -s -H "$AUTH" "http://localhost:$HOST/api/v1/panel/projects/$ID_A"            # 200 + 8 claves
curl -s -w '\n%{http_code}\n' -H "$AUTH" "http://localhost:$HOST/api/v1/panel/projects/999999"  # 404 not_found

# Q12–Q19: matriz PATCH (sleep 2 antes de cada PATCH que deba refrescar updated_at)
curl -s -w '\n%{http_code}\n' -X PATCH "http://localhost:$HOST/api/v1/panel/projects/$ID_A" \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"name":"smoke-c3-'"${TS}"'-a2"}'      # 200 (Q12)
curl -s -X PATCH "http://localhost:$HOST/api/v1/panel/projects/$ID_A" -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"description":null}'                              # 200 limpia (Q13)
curl -s -X PATCH "http://localhost:$HOST/api/v1/panel/projects/$ID_A" -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"status":"ARCHIVED"}'                             # 200 (Q14)
curl -s -X PATCH "http://localhost:$HOST/api/v1/panel/projects/$ID_A" -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{}'                                                # 200 idempotente (Q15/RA-3b)
curl -s -X PATCH "http://localhost:$HOST/api/v1/panel/projects/$ID_A" -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"name":null}'                                     # 400 field_required/name (Q16)
curl -s -X PATCH "http://localhost:$HOST/api/v1/panel/projects/$ID_A" -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"status":"ACTIVO"}'                               # 400 filter_invalid_value (Q17)
curl -s -X PATCH "http://localhost:$HOST/api/v1/panel/projects/$ID_A" -H "$AUTH" \
  -H 'Content-Type: application/json' -d "{\"name\":\"smoke-c3-${TS}-b\"}"                   # 409 (Q18)
curl -s -X PATCH "http://localhost:$HOST/api/v1/panel/projects/999999" -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"description":"x"}'                               # 404 (Q19)

# RA-2 pasos 5–6 (campos desconocidos y cuerpos no-JSON)
curl -s -X POST "http://localhost:$HOST/api/v1/panel/projects" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"name\":\"smoke-c3-${TS}-unk\",\"nmae\":\"x\"}"                                      # 201 ignora "nmae" (CD-3.9)
curl -s -w '\n%{http_code}\n' -X PATCH "http://localhost:$HOST/api/v1/panel/projects/$ID_A" \
  -H "$AUTH" -H 'Content-Type: application/json' -d 'not-json{{{'                            # 400 field_required sin field (CD-3.9)

# Q21: 401 sin JWT en las 4 rutas (sin parse estricto del body)
for m in "GET /api/v1/panel/projects" ; do :; done
curl -s -o /dev/null -w '%{http_code}\n' "http://localhost:$HOST/api/v1/panel/projects"                 # 401
curl -s -o /dev/null -w '%{http_code}\n' -X POST "http://localhost:$HOST/api/v1/panel/projects" \
  -H 'Content-Type: application/json' -d '{"name":"x"}'                                                 # 401
curl -s -o /dev/null -w '%{http_code}\n' "http://localhost:$HOST/api/v1/panel/projects/$ID_A"            # 401
curl -s -o /dev/null -w '%{http_code}\n' -X PATCH "http://localhost:$HOST/api/v1/panel/projects/$ID_A" \
  -H 'Content-Type: application/json' -d '{}'                                                            # 401

# Q24: preflight CORS con PATCH (nuevo verbo en ruta nueva) — 20[46]
curl -s -o /dev/null -w '%{http_code}\n' -X OPTIONS "http://localhost:$HOST/api/v1/panel/projects" \
  -H "Origin: $FRONTEND_ORIGIN" -H 'Access-Control-Request-Method: PATCH' -D - | \
  grep -Ei 'access-control-allow-(origin|methods)'     # ACAO reflejada + PATCH en Allow-Methods
```

### 6.4 psql — estado de `projects` (filas, status, updated_at, has_key DERIVADO)

```bash
$BIN exec -T $SVC_DB psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
SELECT id, name, status, description IS NULL AS desc_null,
       (api_key_hash IS NOT NULL) AS has_key, api_key_prefix,
       EXTRACT(EPOCH FROM created_at) AS created_ep,
       EXTRACT(EPOCH FROM updated_at) AS updated_ep
FROM projects WHERE name LIKE 'smoke-c3-${TS}-%' ORDER BY id;"
# Flyway: cero migraciones nuevas (Q25)
$BIN exec -T $SVC_DB psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version FROM flyway_schema_history;"
```

### 6.5 Mutaciones de `.env` (solo si un paso lo exigiera — C3 NO las necesita, CD-3.14)

```bash
# Back-up → cambio mínimo → UNSET + recreate → aplicar → restaurar (trap)
cp .env ".env.bak-${TS}"
# … editar solo la variable mínima necesaria …
env -i HOME="$HOME" PATH="$PATH" $BIN up -d --build --force-recreate
# … verificar salud y ejecutar el caso …
mv ".env.bak-${TS}" .env
env -i HOME="$HOME" PATH="$PATH" $BIN up -d --build --force-recreate   # volver al estado base, SIEMPRE --force-recreate
```

Reglas: sufijos timestamp en todo artefacto temporal; el `.env` restaurado
antes de cerrar; jamás dejar la pila recreada con env mutado; jamás
`down -v` (heredado C2 — destruiría el volumen del smoke).

### 6.6 Verificación de navegador [B] (Q23)

Automatizada SOLO si existe driver en el entorno (no se instalan dependencias).
Si no: ejecutar el procedimiento manual de §2 paso a paso con las credenciales
dev del `.env` desde `http://localhost:8080/mockups/04-proyectos.html` y
registrar `PASSED-MANUAL` (o `BLOQUEADO (sin navegador)`) con la evidencia de
cada paso (captura o transcripción de lo observado + verificación BD del PATCH).

## 7. Criterio de veredicto

- **Veredicto por-ID**: cada caso (Q1–Q29, RA-1–RA-4) recibe uno de: `PASS`,
  `FAIL`, `BLOQUEADO`, `XFAIL`, y `PASSED-MANUAL`/`BLOQUEADO (sin navegador)`
  (solo para Q23 [B] — §2). No hay veredicto global sin desglose por ID.
- **El veredicto de un [S] es del corte, no del script**: un FAIL en un caso
  se formula contra ESTE plan (ID Q/RA + ancla E3.x/CD del spec/design). Un
  script que falle por razones propias (p. ej. bug del script) NO es FAIL del
  corte hasta reconfirmar el caso a mano con los curl de §6.
- **Bloqueantes (defecto de Done)**:
  - Done-07 literal «CRUD+archivado con smoke de estados (409 y 400 incluidos)»:
    Q1–Q21 (incluidos los 409 de Q3/Q18 y los 400 de Q4–Q6/Q9/Q16/Q17).
  - Done-07 literal «maquetas 04/05 servibles contra esta API»: Q22 (obligatorio)
    y Q23 en cualquiera de sus resultados válidos (`PASS`/`PASSED-MANUAL`;
    `BLOQUEADO (sin navegador)` NO invalida el Done del gate G5, pero queda
    como nota obligatoria del informe — nunca se sustituye por un PASS invención).
  - Re-ataques RA-1..RA-4 (los cuatro bloqueantes; la contingencia de RA-1 —
    ausencia del test [M] `DuplicateKeyException`→409 — es hallazgo FAIL por sí misma).
  - No-regresión obligatoria R3.20: Q24 y Q25 bloqueantes.
- **No bloqueantes (defensivos/recomendados)**: Q28 y Q29 (cortesía del design
  CD-3.13/CD-3.5). Su fallo se informa como nota, no como Done fallido —
  salvo que delaten el defecto que un bloqueante cubre.
- **Pila no sana**: si tras la recomposición disciplinada (§6.2, con
  `--force-recreate` y anti-veneno) la pila no alcanza `healthy`, TODOS los
  casos [S]/[B] se marcan `BLOQUEADO` (no FAIL) y el informe lo distingue del
  defecto de código — BLOQUEADO no es FAIL, salvo que la causa demostrada esté
  en código C3.
- **XFAIL**: solo con nota a decisión de spec/design explícita (candidatos
  declarados: trato de campos desconocidos y cuerpos no-JSON — CD-3.9; el
  orden de lista N2). Un XFAIL sin ancla escrita no es admisible.
- **Prohibiciones heredadas que invalidan la corrida**: usar
  `scripts/qa_c2_smoke.sh` (su Q13 hace `down -v`); recomponer sin
  `--force-recreate` (imagen vieja — R-nuevo-3); parse JSON estricto de un
  401; borrar volumen para "limpiar" entre corridas (la idempotencia por TS
  es el mecanismo).

## 8. Resumen operativo

| Tipo | Casos |
|---|---|
| [S] smoke pila podman (23) | Q1–Q22, Q24 |
| [U] unitario sin Spring (2) | Q26, Q28 |
| [M] MockMvc (2) | Q27, Q29 |
| [B] navegador (1) | Q23 (§2; PASSED-MANUAL/BLOQUEADO si no hay navegador) |
| [D] estática (1) | Q25 |
| Re-ataques (4, todos bloqueantes) | RA-1, RA-2, RA-3, RA-4 |
| Bloqueantes | Q1–Q22, Q24, Q25, Q26, Q27, RA-1..RA-4 |
| No bloqueantes (defensivos) | Q28, Q29 |
| NOT cubierta | N1–N14 (§4) |