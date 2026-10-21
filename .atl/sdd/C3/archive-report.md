---
change: "C3"
artifact: archive-report
last_updated: 2026-10-21
status: archived
---

# Archive report — C3 · Proyectos: CRUD + archivado

## 1. Resumen de cierre

C3 entregó el CRUD + archivado del panel sobre `projects` sin capa de servicio (repo centraliza) en `/api/v1/panel/projects*`, exactamente 4 endpoints: GET lista con `?status=` case-sensitive y fail-fast 400 `filter_invalid_value`, POST 201 con defaults V1 (400 `field_required`/`field_too_long` literales; 409 `name_duplicate` vía handler ÚNICO de `DuplicateKeyException` — constraint BD, no check-then-insert), GET/PATCH `{id}` con PATCH parcial G2 (nulls → enum antes de BD → longitudes; `{}` → 200 idempotente sin UPDATE; 404 antecede al UPDATE) y archivar/desarchivar SOLO por PATCH `status` (nunca DELETE; key/logs intactos). DTO de 8 claves exactas sin `summary`, `has_key` derivado (`api_key_hash IS NOT NULL`), fechas `Z` con formatter C2 por referencia. Maquetas 04/05 como copias servibles en `:8080/mockups/` con login inline y fetch real (`docs/mockups/` congelado). Flujo completo: explore → proposal gate **5/5** (2026-10-21) → spec 20R/26E → design CD-3.1…14 → tasks 21 → apply DONE 24/24 → qa 32/33 (Q23 BLOQUEADO) → verify **PASS-with-notes**. Estado final: HEAD `d9853a0`, **8 commits** `a97de25`→`d9853a0` (5 de fase + `71c3e3b` smoke + fixes `4939d04`/`d9853a0` — los 3 extras solo script de smoke, cero código de producto; verify enuncia «7» pero `git rev-list d4768a6..d9853a0` cuenta 8), 0 ficheros de C1/C2 modificados (diff del rango = exactamente los 11 ficheros nuevos del design §1.1), `mvn test` 60/60 en imagen builder, pila podman 3× healthy, working tree limpio salvo artefactos `.atl` (los commitea el orquestador).

## 2. Registros nuevos

- **R-nuevo-5 — podman-compose `up` tras rebuild requiere `--force-recreate`** (regla de scripts; actualiza R-nuevo-3): `up -d --build` reconstruye la imagen pero NO recrea el contenedor (sirvió la imagen vieja → smokes rojos). Toda recomposición futura DEBE llevar `--force-recreate`; `restart` sigue prohibido para recomponer.
- **R-nuevo-6 — host sin JDK 21**: `mvn test` corre dentro de la imagen builder `maven:3.9-eclipse-temurin-21` (mismo tag D1/RG-1) con backend y `~/.m2` montados (`podman run --rm -v backend:/ws:Z -v ~/.m2:/root/.m2:Z -w /ws … mvn test`), sin BD ni contenedores de la pila (G4). Patrón para C5+.
- **Lección de proceso**: el test-writer sin acceso al sistema no puede validar la mecánica de su script (11 bugs del artefacto QA — 3 BLOQUEOS GLOBALES espurios del qa-run + 2 FAILs en la 1ª verify; 0 defectos de corte). Para C4+: pasada barata «smoke of the smoke» (o repaso mecánico del corredor) ANTES de la corrida canónica, más `FLOOR(EXTRACT(EPOCH…))::bigint` y booleano psql consciente de contexto (`f/t` en concat vs `false/true` como columna).

## 3. Q23 (estado final)

**BLOQUEADO por entorno** (sin driver de navegador; `geckodriver`/`chromedriver`/`xdotool` ausentes y no se instalan dependencias) — tolerado por qa-plan §7. Evidencia cubierta: pasos HTTP-verificables (capturas headless del paso 1, assets 200 `text/html`, login inline/ficha/PATCH demostrados por curl con JWT real, originales de `docs/mockups` no expuestos). Instrucción manual de ~2 min al usuario en verify-report §3.1 (flag `QA_C3_Q23_PASSED_MANUAL=1` del script actualiza el veredicto a PASSED-MANUAL). **NO invalida el Done del G5**: Q22 (cota curl obligatoria del mismo Done) es PASS con triple fuente. El panel React real con estados completos es C8.

## 4. Acciones heredadas

| Destino | Acción |
|---|---|
| C5 | Reevaluar Testcontainers con la inyección real (batch all-or-nothing / CASCADE, concurrencia de escritura N9); patrón builder-image (R-nuevo-6) |
| C8 | Maquetas React REALES + retiro de las copias `frontend/public/mockups/**` de `:8080/mockups/**`; tolerancia 401 ISO-8859-1 en helpers fetch (fix UTF-8 del entry point en C8/C9 — N11; en C3 la no-parse funcionó) |
| C9 | Smoke canónico `docker compose` (entorno solo tiene podman-compose) sin `down -v`; disciplina anti-veneno + `--force-recreate`; `.env` real de VM sin placeholders |
| Scripts C4+ | Q23-like [B] con driver si existe o manual declarativo (`QA_Cn_Q*_PASSED_MANUAL=1`); `--force-recreate` OBLIGATORIO; epoch con FLOOR; booleanos psql por contexto; «smoke of the smoke» pre-canónica |
| Próx. validación docs | Ningún eco OBLIGATORIO de C3: todas las divergencias son normas de scripts/patrón de entorno (eco = NO según verify §4/§6) |

## 5. Estado de artefactos y lo que NO se hizo

| Artefacto | Status |
|---|---|
| explore / proposal / spec / design / tasks | done (proposal approved 5/5, 2026-10-21) |
| apply-progress | done — DONE 24/24, `mvn` 60/60, intocables diff-0 |
| qa-plan (29+4) / qa-log.txt (referencial) / tests-written / qa-report | done — referencia (32/33, Q23 BLOQUEADO) |
| verify-report | done — **PASS-with-notes** (2026-10-21) |
| archive-report | archived (este fichero) |

NO se hizo: commits de verify (la corrida canónica no cambió código; fixes del artefacto QA quedan en su script); `scripts/qa_c3_qa_smoke.sh` **untracked** pende del commit de pauta del orquestador; `/docs` intacto; `/07` sin tocar (estado lo actualiza el orquestador); sin push; sin migraciones nuevas (`flyway_schema_history` = solo v1); sin secretos ni JWT a stdout ni versionados.