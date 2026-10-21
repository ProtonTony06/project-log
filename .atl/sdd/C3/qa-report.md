---
change: "C3"
artifact: qa-report
last_updated: 2026-10-21
results: "32/33 PASS · 1 BLOQUEADO (Q23)"
commit: d9853a0e950a32fab1dfd9a67838e3fcb7d3b5e4
log: qa-log.txt
---

# QA Report — C3 · Proyectos: CRUD + archivado

> Fase sdd-qa (delegación C: qa-run). Veredictos interpretados DESDE
> `qa-plan.md` (§1 casos, §2 [B], §3 re-ataques, §7 criterio). Los veredictos
> [S] son **del corte, no del script** (§7): el script de la delegación B
> no llegó a ejecutar ningún caso (3 corridas bloqueadas por bugs del
> PROPIO script — abajo); el plan §7 + §5 sanciona reconfirmar los casos
> "a mano con los curl de §6" sobre la misma pila, que es lo hecho
> (qa-log.txt §6–§9).

## Estado

- **[U]/[M] Q26–Q29**: `mvn test` completo → **60/60 verdes, 0 fallos**
  (incluye `ProjectDtoSerializationTest` 2, `ProjectsControllerMvcTest` 25,
  `ProjectsUpdateSqlTest` 7 y las 26 de C2 sin regresión).
  **Contingencia de la delegación B resuelta: la mitad (ii) de RA-1 EXISTE**
  (test `patchChoqueDeUniqueResponde409NameDuplicate`: mock del repo lanza
  `DuplicateKeyException` → 409 `name_duplicate` con cuerpo estándar).
- **[S]/[D] Q1–Q22, Q24, Q25**: reconfirmados a mano (§6 del plan) con TS
  fresco `20261021214801`, pila recreada disciplinadamente
  (`env -i … up -d --build --force-recreate`, healthy) → **todos PASS**.
- **[B] Q23**: **BLOQUEADO (sin navegador)** — evidencia mínima headless
  capturada (capturas de `/mockups/04` y `/05?id=`: login inline renderizado,
  sin panel roto — paso 1 de §2); pasos 2–8 (ratón) sin driver posible
  (no hay geckodriver/chromedriver/xdotool y NO se instalan dependencias).
  Conforme a §7: no invalida el Done del gate G5; queda **nota obligatoria
  al verify**.

## Tabla por ID

| ID | Veredicto | Evidencia breve |
|---|---|---|
| Q1 | PASS | 201; 8 claves exactas sin `summary`; `ACTIVE`; `api_key_prefix:null`; `has_key:false`; fechas `Z`; eco; psql status/has_key |
| Q2 | PASS | 201 sin description → `description:null`; psql `description IS NULL` |
| Q3 (RA-1 i-a) | PASS | 409 `name_duplicate` (POST inmediato); psql COUNT==1 (constraint BD, no check-then-insert) |
| Q4 | PASS | `{}` y `{"name":""}` → 400 `field_required(name)`; psql sin fila nueva |
| Q5 | PASS | name 201 chars → 400 `field_too_long(name)`; sin fila nueva |
| Q6 | PASS | description 2001 chars → 400 `field_too_long(description)`; sin fila nueva |
| Q7 | PASS | array plano, 8 claves por elemento, membresía del TS (membresía, no exclusividad) |
| Q8 | PASS | `?status=ACTIVE` contiene `-b` y no `-a2`; `ARCHIVED` contiene `-a2` y no `-b`; sin query ambos (orden crear→parchear→listar) |
| Q9 | PASS | `active`/`ARCHIVE`/vacío → 400 `filter_invalid_value(status)` — fail-fast, nunca 200/500 (CD-3.11 incluido) |
| Q10 | PASS | ficha `-a` 200, DTO completo, id coherente, datos parcheados |
| Q11 | PASS | `/999999` → 404 JSON `not_found` (no vacío, AppFlow §5) |
| Q12 | PASS | PATCH name → description/status INTACTOS (G2), `created_at` igual, `updated_at` estrictamente posterior; psql coherente |
| Q13 | PASS | texto→cambia; `null`→limpia (columna NULL real en psql) y refresca |
| Q14 | PASS | archivar→persistencia+intact (regla 3)→lista ARCHIVED→desarchivar→2× re-ARCHIVE idempotente→reversible (§4.2); key/prefix intactos (regla 11) |
| Q15 (RA-3b) | PASS | PATCH `{}` → 200 con `updated_at` INTACTO (igualdad ESTRICTA tras `sleep 2`) — no hay UPDATE indebido (R3.6/G2) |
| Q16 | PASS | `{"name":null}`/`{"status":null}` → 400 `field_required`; fila intocada (psql) |
| Q17 | PASS | `ACTIVO`/`active`/`ARCHIVE` → 400 `filter_invalid_value` antes de BD (nunca 500, R-2); fila intocada |
| Q18 (RA-1 i-b) | PASS | PATCH name de B → 409 `name_duplicate`; re-GET de B intacto |
| Q19 | PASS | PATCH `/999999` → 404 `not_found` (antecede al update, CD-3.8) |
| Q20 (RA-3) | PASS | GETs×3 (≥1 s) con `updated_at` INVARIANTE; PATCH con campo lo refresca estrictamente; `created_at` invariable; psql coherente |
| Q21 | PASS | 401 sin JWT en las 4 rutas (status; body tolerante — literal `invalid_token` presente) |
| Q22 | PASS | `/mockups/04` y `/05?id=` → 200 `text/html` con marcas de fetch (`/auth/login`, `/projects`, `?id=`); `/docs/mockups/04` NO expone el original del repo |
| Q23 | BLOQUEADO | (sin navegador/driver de ratón) — evidencia mínima headless: capturas 04/05 (login inline, paso 1 §2); pasos 2–8 requieren driver; HTTP-only: Q22. **Nota al verify**: ejecutar §2 a mano o automatizar con driver; NUNCA PASS sin los pasos |
| Q24 (RA-4) | PASS | login 200 `{token,username,expires_at Z}`; 401 `invalid_credentials` ×3 (tolerante); `/me` y `/panel/projects` sin JWT → 401 `invalid_token`; preflight PATCH → 200 con ACAO exacta y PATCH en Allow-Methods |
| Q25 | PASS | intocables (worktree+index) **diff 0**; cero `V[2-9]__*`; `flyway_schema_history` solo versión 1; pila healthy al cierre |
| Q26 | PASS | `mvn test`: serialización con las 8 claves §3.3 sin `summary`, `description:null`, literal `ACTIVE`, regex `Z` (2 tests) |
| Q27 | PASS | `mvn test`: matriz R3.15 completa (bordes 200 chars/201, 2000/2001, `verifyNoInteractions`, `never().update`, 409, 404, DTO sin summary) — 25 tests |
| Q28 | PASS | `mvn test`: 7 SET literales con `updated_at = now()`, cero columnas de key, prefijo/sufijo `UPDATE … WHERE id = ? RETURNING …`, combo vacío → `IllegalArgumentException` (7 tests) |
| Q29 | PASS | `mvn test`: 401 en las 4 rutas dentro del slice (`sinJwtLasCuatroRutasResponde401InvalidToken`) |
| RA-1 | PASS | MITAD (i): end-to-end 409 real (Q3 POST + psql COUNT==1; Q18 PATCH + B intacto) — constraint BD; MITAD (ii): test [M] **EXISTE** y verde (contingencia B resuelta: no hay hallazgo FAIL). Límite N9 declarado (concurrencia real diferida, C5) |
| RA-2 | PASS | (1)/(3) vía Q15/Q16; (2) paso2 limpieza real en `-c`; (4) ciclo ARCHIVED×2→ACTIVE→ARCHIVED; (5) campos desconocidos ignorados + re-GET (CD-3.9); (6) no-JSON/sin body → 400 `field_required` SIN `field` (CD-3.9); (7) conservación psql del TS |
| RA-3 | PASS | (a) GETs no modifican; (b) `{}` no ejecuta UPDATE; (c) PATCH real refresca; `created_at` invariable en todo; sin trigger (V1 intacto) |
| RA-4 | PASS | Q21 + Q24 + pila healthy en apertura y cierre + seed C2 idempotente tras recomposición (login OK) |

**Total: 32/33 PASS · 1 BLOQUEADO (Q23) · 0 FAIL**

## Bloqueantes Done-07 (plan §7)

- ✅ «CRUD+archivado con smoke de estados (409 y 400 incluidos)»: Q1–Q21 PASS.
- ✅ «maquetas 04/05 servibles contra esta API»: Q22 PASS (obligatorio) y
  Q23 **BLOQUEADO (sin navegador)** — válido según §7: NO invalida el Done
  del gate G5, pero queda como nota obligatoria (ver fila Q23).
- ✅ RA-1..RA-4 (bloqueantes): los cuatro PASS; contingencia RA-1(ii)
  resuelta (el test [M] EXISTE).
- ✅ No-regresión R3.20: Q24 y Q25 PASS.
- ✅ Bloqueantes de tests: Q26, Q27 PASS.
- No bloqueantes (defensivos): Q28, Q29 PASS (nota positiva).

## Interpretación desde el plan

- Todo FAIL habría nombrado un caso del plan; **no hay ningún FAIL**.
- `BLOQUEADO` único: Q23, causa EXCLUSIVAMENTE de entorno (sin driver de
  navegador; no se instalan dependencias — plan §2/§6.6), NO del código.
- La pila NO fue causa de bloqueo: `healthy` (backend+db) en cada recreación
  disciplinada (`--force-recreate`, anti-veneno env UNSET); sin `down -v`
  ni `restart` en todo el proceso; `.env` NO tocado (CD-3.14: sin mutación).

## Comandos exactos ejecutados (resumen; detalles crudos en qa-log.txt)

1. `mvn -f backend/pom.xml test` (host) → falla PRE-test del entorno (JRE 25
   sin `javac`, "release version 21 not supported"); sin resultado de tests.
2. Corrida válida de tests (mismo builder del proyecto): `podman run --rm
   -v <backend>:/ws:Z -v ~/.m2:/root/.m2:Z -w /ws
   docker.io/library/maven:3.9-eclipse-temurin-21 mvn test` → 60/60.
3. `env -i HOME=… PATH=… podman-compose up -d --build --force-recreate`
   (3x para smoke + 1x diagnóstico) + `podman inspect Health.Status` (healthy).
4. `bash scripts/qa_c3_qa_smoke.sh` ×3 (original + 2 re-ejecuciones
   documentadas) — todas BLOQUEO GLOBAL por bugs del PROPIO script (abajo).
5. Confirmación manual §5/§6/§7 del plan (tool propio `/tmp/qa_c3_manual_qa.sh`,
   corrida válida TS=20261021214801 + suplemento RA-2-paso7/Q23) — 29/30 PASS.
6. Capturas headless Q23: `firefox --headless … --screenshot /tmp/q23_04.png`
   (y 05) — evidencia de paso 1 §2.

## Hallazgos sobre el SCRIPT de QA (no del corte) — para el verify

El script `scripts/qa_c3_qa_smoke.sh` (delegación B) tiene **8 bugs propios
documentados** (cabecera del script + qa-log.txt §3–§6), por los que sus 3
corridas terminaron en BLOQUEO GLOBAL espurio:

1. `wait_healthy` usa `$2` (nombre del contenedor DB) como timeout y llama
   `salud "$1" "$2"` → error `[ -lt ]` no entero y BLOQUEO GLOBAL falso.
2. `TOKEN` se extraía de la variable inexistente `$L` (unbound).
3. `curl_get` no enviaba `Authorization` → todo GET autenticado daría 401.
4. Las marcas `RA-n: INFO` sumaban al contador BLOQUEADO (exit 2 espurio).
5. `ra2_paso7` comparaba el `created_at` de `-a3` con el epoch de `-c`.
6. Backticks sin escapar en el `echo` de `bloqueo_global` ejecutaban `mvn test`.
7. Credenciales leídas como `ADMIN_USERNAME/PASSWORD` pero `.env`/compose
   usan `ADMIN_INITIAL_USERNAME/ADMIN_INITIAL_PASSWORD`.
8. **NO corregido** por límite de re-ejecuciones: `curl_post` está
   hardcodeado a `/api/v1/panel/projects` y el LOGIN se hace con él → el
   login del script SIEMPRE daría 401 (payload va a la ruta de proyectos
   con `Bearer pendiente`). Fix trivial (usar `/auth/login` en el login) +
   además: en las comparaciones contra psql, `EXTRACT(...)::bigint`
   **redondea** y el formatter del DTO trunca → las igualdades estrictas
   de `updated_at`/`created_at` deben usar `FLOOR(EXTRACT(EPOCH …))::bigint`.

Ninguno de los 8 afecta a los veredictos del corte (los casos se ejecutaron
con la disciplina del plan por la vía manual §7). Los 1–6 y 7 quedaron
corregidos EN el script (documentados en su cabecera); el 8 y el FLOOR
quedan SIN corregir (tope de 2 re-ejecuciones agotado: "la última manda" —
su corrida es BLOQUEADO GLOBAL por bug del script, no FAIL del corte).

## Desviaciones de entorno declaradas

- `mvn` en host sin JDK 21 → la corrida de tests se ejecutó dentro de la
  MISMA imagen builder del proyecto (`maven:3.9-eclipse-temurin-21`, tag
  literal de `docker/Dockerfile`, D1/RG-1), con el código y la caché `~/.m2`
  montados; sin `BD` ni contenedores de la pila en los tests (G4 respetado).
- `podman-compose up` no es modificable; en el diagnóstico anti-raza se
  repitió la recreación disciplinada (sancionada por §6.2) — nunca
  `down -v` ni `restart`.