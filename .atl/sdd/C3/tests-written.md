---
change: "C3"
artifact: tests-written
last_updated: 2026-10-21
status: done
---

# tests-written — C3 · fase sdd-qa (delegación B: test-writer)

> Registro de la producción de tests [U]/[M] (Q26–Q29) y del script de
> smokes [S]/[B]/[D] (Q1–Q24, Q25) según `qa-plan.md` (§1, §2, §6, §7),
> `spec.md` (R3.1–R3.20, E3.1–E3.26) y `design.md` (CD-3.1…CD-3.14, R-1…R-6).
> Prohibiciones respetadas: sin leer código del feature (`backend/src/**`),
> `pom.xml`, diffs, `apply-progress.md` ni `tasks.md`; sin ejecutar tests ni
> hacer commits; intocables (`main`, pom, compose, nginx, /docs, scripts
> existentes) sin tocar.

## 1. Artefactos producidos

| Ruta (absoluta) | Qué es | Casos |
|---|---|---|
| `/home/acuelop369/Escritorio/Proyectos/Project_Log/scripts/qa_c3_qa_smoke.sh` | NUEVO script de smokes de QA, independiente (NO invoca ni replica el flujo de `qa_c2_smoke.sh` ni de `qa_c3_smoke.sh`). `bash -n` OK, `chmod +x` aplicado. exit 0/1/2. | Q1–Q22, Q24 ([S]) · Q23 ([B]) · Q25 ([D]) + RA-1..RA-4 |

### Nada de Java [U]/[M] nuevo

Los tres ficheros de test que el plan/design exigen para Q26–Q29
**YA EXISTEN** (listado por nombre, contenido NO leído):

```
backend/src/test/java/com/projectlog/projects/dto/ProjectDtoSerializationTest.java
backend/src/test/java/com/projectlog/projects/ProjectsControllerMvcTest.java
backend/src/test/java/com/projectlog/projects/repository/ProjectsUpdateSqlTest.java
```

Regla aplicada del encargo: si existe, NO se duplica ni reescribe — quedan
marcados como «existente, validado por la delegación C con `mvn test`».

## 2. Casos: cubiertos vs existentes vs XFAIL/bloqueados

| Caso | Estado | Dónde |
|---|---|---|
| Q1–Q6 (E3.1–E3.6) | Escrito (script) | `qa_c3_qa_smoke.sh` — 201 con/sin description (claves exactas §3.3 sin `summary`, `status:"ACTIVE"`, `has_key:false` por psql `(api_key_hash IS NOT NULL)`, fechas `Z`), 409 `name_duplicate` inmediato + psql COUNT==1, 400 `field_required`/`field_too_long` (201/2001 chars) con «ninguna fila nueva» por psql |
| Q7 (E3.7) | Escrito | Array plano (`jq type=="array"`), 8 claves por elemento, membresía del TS |
| Q8 (E3.8) | Escrito | Filtros ACTIVE/ARCHIVED y sin query; juzgado sobre los miembros del TS propio (membresía; el `-a` queda ARCHIVED antes del bloque lista: orden crear→parchear→listar) |
| Q9 (E3.9) | Escrito | `?status=active|ARCHIVE|=∅` → 400 `filter_invalid_value(status)` (CD-3.11 incluido) |
| Q10/Q11 (E3.10/11) | Escrito | Ficha 200 (8 claves, id/nombre coherentes con lo parcheado); 999999 → 404 `not_found` |
| Q12–Q20 (E3.12–E3.20) | Escrito | Matriz PATCH completa con captura previa/posterior (DTO + psql epoch), `sleep 2` antes de cada comparación (`updated_at` estricto `<` en PATCH con campos; igualdad ESTRICTA en `{}`); fila intocada en Q16/Q17 |
| Q14 | Escrito EN 2 PARTES (decisión) | Parte 1 tras Q13 (archivar + persistencia + lista ARCHIVED); la reversión a ACTIVE vive en RA-2 paso 4 — motivo: el orden CD-3.14 (crear→parchear→listar) exige un ARCHIVED del TS antes de Q8/Q10; el veredicto `Q14:` une ambas partes |
| Q21 (E3.21) | Escrito | Las 4 rutas SIN JWT → 401; status obligatorio + código TOLERANTE (`grep -a invalid_token`), NUNCA parse JSON estricto del body 401 |
| Q22 (E3.22) | Escrito | 200 `text/html` de `/mockups/04|05`; marcas de fetch `/auth/login` + `/projects` (04), `/projects` + uso `?id=` (05); `/docs/mockups/04-proyectos.html` NO coincide byte a byte con el original del repo (cmp) |
| Q23 (E3.22 [B]) | Escrito CON navegador detectado | En la VM existe `/usr/bin/firefox`. Comportamiento: evidencia MÍNIMA headless (screenshot de 04 y 05?id=) + impresión del procedimiento manual de los 9 pasos (§2 del plan). Veredicto: `BLOQUEADO` por defecto (sin driver de ratón: un [B] sin evidencia de los pasos es BLOQUEADO, no PASS — plan §2); `PASSED-MANUAL` solo con el flag `QA_C3_Q23_PASSED_MANUAL=1` (declaración del operador tras ejecutar §2). El verdicto está marcado «tolerado»: no fuerza exit 2 (plan §7: no invalida el Done de G5) |
| Q24 (E3.23) | Escrito | Login 200 forma exacta (`expires_at` Z); 401 `invalid_credentials` ×3 (tolerante); 401 `/me` y `/panel/projects` sin JWT (tolerante); preflight `OPTIONS` con `Origin: $FRONTEND_ORIGIN` + PATCH → 20[46] + ACAO exacta + PATCH en Allow-Methods |
| Q25 (E3.24) | Escrito | `git diff --quiet` + `git diff --cached --quiet` sobre la lista literal de intocables (pathspecs); `ls` sin `V[2-9]__*.sql`; psql `flyway_schema_history` == solo versión 1; cierre con pila healthy |
| RA-1 | Escrito (mitad i) | Mitad (i) [S]: Q3 (409 POST, COUNT==1) + Q18 (409 PATCH, B intacto). Mitad (ii) [M]: el `@ExceptionHandler` del `DuplicateKeyException`→409 vive dentro de `ProjectsControllerMvcTest` (existente) — la delegación C lo valida con `mvn test` |
| RA-2 | Escrito | Puntos 1/3 compartidos con Q15/Q16; punto 2 (description null sobre `-c` CON descripción, psql NULL); punto 4 (ciclo ARCHIVED×2→ACTIVE→ARCHIVED, key/logs intactos); punto 5 (POST `-unk` con `nmae` → 201; PATCH con `descripton`+status+name → aplica/ignora, re-GET confirma — ancla CD-3.9); punto 6 (not-json + PATCH sin body → 400 `field_required` SIN `field` — CD-3.9); punto 7 (psql de conservación del TS: names/status finales + created_at invariante) |
| RA-3 | Escrito (integrado) | (a) en Q20 (GET×3 separados ≥1 s, igualdad estricta), (b) en Q15 (`{}` igualdad estricta tras sleep 2), (c) en Q12/Q20 (posterior estricto); `created_at` invariable |
| RA-4 | Escrito (integrado) | Materializado en Q21 + Q24 (más el `--force-recreate` + healthy de precondición y de cierre) |
| Q26 (E3.25) | EXISTENTE, no leído | `ProjectDtoSerializationTest.java` — delegación C con `mvn test` |
| Q27 (E3.26) | EXISTENTE, no leído | `ProjectsControllerMvcTest.java` (matriz R3.15, bordes 200/201·2000/2001, `verifyNoInteractions`, `never().update`, 409, 404, DTO sin `summary`) — delegación C |
| Q28 (CD-3.5) | EXISTENTE, no leído | `ProjectsUpdateSqlTest.java` (7 SET literales, `updated_at = now()`, cero columnas de key, `IllegalArgumentException` en combo vacío) — delegación C |
| Q29 (S19 en slice) | EXISTENTE, no leído | Caso dentro de `ProjectsControllerMvcTest.java` (401 ×4 sin JWT) — delegación C |

## 3. Decisiones de test-side (con anclaje al plan/design)

1. **Orden de ejecución del script** (qa-plan §0, CD-3.14): crear (Q1–Q6) → Q7 →
   parchear (Q12–Q20 + Q14 parte 1) → listar/ficha (Q8–Q11 con `-a` ya
   ARCHIVED) → RA-2/RA-3 → Q21 → Q24 → Q22 → Q23 → Q25. Es la que hace que
   `?status=ARCHIVED` tenga miembro del TS (Q8) y que Q10 sea coherente con lo
   parcheado.
2. **Q14 dividida en dos partes** (ver tabla): reversión a ACTIVE dentro del
   ciclo RA-2 paso 4; el plan ordena crear→parchear→listar y Q14 termina
   desarchivado, lo que rompería la aserción «ARCHIVED contiene `-a`» de Q8.
3. **`has_key` siempre DERIVADO en psql** (`(api_key_hash IS NOT NULL)` y
   comparación booleana `false`/`t`), nunca columna `has_key` (R3.7; q-plan §0).
4. **401 sin parse estricto** (R3.19): status obligatorio + detección tolerante
   del literal con `grep -a` sobre el body crudo (ISO-8859-1), nunca `jq`.
5. **`.env` con lista blanca** (selección `ADMIN_USERNAME`, `ADMIN_PASSWORD`,
   `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `FRONTEND_ORIGIN`):
   sin `set -a` global ni exportación a ciegas (anti-veneno, qa-plan §6.2).
6. **Recomposición SIEMPRE `env -i … up -d --build --force-recreate`**;
   `down -v` y `restart` ausentes por construcción; el veredicto tras un `up`
   con error lo decide la salud (`podman inspect Health.Status`), y una pila
   no sana marca TODO bloqueado (§7: BLOQUEADO, no FAIL).
7. **Resolución de contenedores dinámica** (`podman ps` grep backend/db) para
   no acoplar el script a nombres de servicio; psql vía `podman exec` al
   contenedor de BD con `POSTGRES_USER/DB` de `.env` (defaults `postgres`).
8. **Campos desconocidos y cuerpos no-JSON (RA-2 puntos 5–6)** etiquetados con
   ancla **CD-3.9** (spec §1.D nota: decisión menor sin gate E3.x) — un FAIL
   ahí se formula contra plan + CD-3.9, no contra un E3.x (regla §7 del plan).
9. **Exit codes**: 0 = sin FAIL y sin BLOQUEADO (Q23 BLOQUEADO tolerado); 1 =
   algún FAIL; 2 = algún BLOQUEADO de pila/dependencias/git. Los `RA-n` con
   veredicto INFO no cuentan (las mitades ya cuentan vía sus casos Q).
10. **Q25 con pathspecs**: `git diff --quiet` (worktree) + `git diff --cached
    --quiet` (index) sobre la lista literal §5; queda anotado que un cambio
    ya COMMITTEADO no es visible para `git diff` — el design lo completa con
    inspección en review; el script imprime la nota.

## 4. Existentes que NO se leyeron (respetando el encargo)

- `backend/src/test/java/com/projectlog/projects/ProjectsControllerMvcTest.java`
- `backend/src/test/java/com/projectlog/projects/dto/ProjectDtoSerializationTest.java`
- `backend/src/test/java/com/projectlog/projects/repository/ProjectsUpdateSqlTest.java`
- `backend/src/test/java/com/projectlog/auth/**`, `config/CorsConfigTest.java`
  (solo listados por nombre para verificar existencia)
- `scripts/qa_c2_smoke.sh`, `scripts/qa_c3_smoke.sh`, `backend/src/main/**`,
  `backend/pom.xml`, `compose.yaml` — NO leídos.

## 5. XFAIL / bloqueados declarados aquí

- **Q23**: `BLOQUEADO` por defecto en el script (navegador `firefox` existe
  pero no hay driver de automatización de ratón; la evidencia mínima headless
  no sustituye los 9 pasos §2). `PASSED-MANUAL` solo si el operador ejecuta
  §2 y lanza con `QA_C3_Q23_PASSED_MANUAL=1`. Plan §7: `BLOQUEADO (sin
  navegador)` no invalida el Done del gate G5, pero queda como nota obligatoria
  del informe.
- **Sin XFAIL de código desde test-side** (no hay aserciones escritas que
  anticipen fallo). Los candidatos del plan (§7): campos desconocidos y cuerpos
  no-JSON → ancla **CD-3.9**; orden de lista → **N2** — ambos se testean aquí
  como PASS esperado según la decisión del design; un FAIL se ancla a plan+CD.
- **RA-1** se declara estructuralmente cubierto (N9: concurrencia real NO
  verificada temporalmente, diferida a C5 por G4).

## 6. Dudas al spec (para verify)

1. **Contingencia RA-1 (bloqueante si se cumple)**: la mitad (ii) de RA-1
   (test [M] `DuplicateKeyException` → 409 dentro de `ProjectsControllerMvcTest`)
   no es verificable desde test-side (el contenido de la clase no se leía).
   La delegación C DEBE confirmar que el caso existe; si no existe, es hallazgo
   **FAIL de RA-1** aunque el smoke (i) pase (qa-plan §3, R3.5).
2. **Igual en Q27/Q28/Q29**: solo se certificó EXISTENCIA de las clases por
   nombre; que cada aserción EXACTA del plan (matriz R3.15 completa con
   `verifyNoInteractions`, `verify(repo, never()).update(...)`, 7 SET literales,
   401×4 en el slice) esté presente y con nombres de clases/beans como los
   declara el design (`PanelAuthMvcTest` fixture réplica, `@MockitoBean
   ProjectsRepository`, `LoginController.EXPIRES_AT_FORMATTER`) debe
   verificarla la delegación C con `mvn test`.
3. **Q25 y commits**: `git diff --exit-code` (plan literal) no ve cambios ya
   committeados en los intocables. Si apply hizo commits que tocan algún
   intocable, el plan pide «equivalente estado repo» — propuesta para verify:
   confrontar la lista de ficheros tocados por los commits de C3 (sin tocar
   `/docs` ni reescribir historial).
4. **`POSTGRES_PASSWORD`**: el uso que hace el script (`podman exec` psql como
   `POSTGRES_USER` de `.env`) no necesita la password (unix socket del
   contenedor), pero la lee por lista blanca para robustez; si el `.env` usara
   un nombre distinto de variable, el fallback es `postgres`.
5. **`FRONTEND_ORIGIN` si no estuviera en `.env`**: el preflight de Q24 usa
   fallback `http://localhost:8080` con nota; el plan asume que existe
   (heredado de C2).