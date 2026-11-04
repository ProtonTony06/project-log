---
project: Project_Log
change: C10
artifact: apply-progress
last_updated: 2026-10-29
status: active
---

# Apply-progress — C10 · Bloques 1–2 backend (instancia backend-specialist, re-anclaje y cierre)

> Fase apply de C10, alcance de esta instancia: **Bloque 1 completo
> (T10.1–T10.10) + Bloque 2 (T10.11)**. Sin commits, sin push. Frontera
> respetada: solo ficheros backend main/test de la tabla §4 del design.

## Estado por tarea (Bloques 1–2)

| Tarea | Veredicto | Detalle |
|---|---|---|
| T10.1 | hecho-ya (verificado) | `SummaryDto` record 4 claves snake_case; `ProjectDto` 9.º componente `@JsonInclude(NON_NULL) summary` + ctor comodín 8-arg delegando `this(..., null)` + `conResumen(SummaryDto)`. Conforme a D-C10-4. |
| T10.2 | hecho-ahora (completado) | Faltaban 3 pins: con `conResumen` objeto completo (8 claves + summary; SummaryDto 4 claves literales), SummaryDto de proyecto sin logs → `{}`/`{}`/`0` con `last_log_at` `isNull()` EXPLÍCITO + forma EXACTA String, `last_log_at` con dato → regex ISO-Z. Javadoc «sin summary (C10)» reescrita (obsoleta). + pin de AUSENCIA en PATCH 200 (añadido al test `patchDescriptionNullLimpiaYActualizaConElComboCorrecto` — estaba ausente; las otras 3 formas ya pinaban: POST 201, GET /{id}, lista sin include). |
| T10.3 | hecho-ya (verificado) | `SQL_BORRAR` package-private literal `"DELETE FROM projects WHERE id = ?"` + `borrar(long): boolean` (`update==1`) con `@Transactional // defensa documentada (R3.2): mono-sentencia — CASCADE del motor`. |
| T10.4 | hecho-ahora | `ProjectsBorradoSqlTest` NUEVO (patrón D-9): literal EXACTO byte-a-byte, 1 placeholder, sin `;`/sub-SELECT/RETURNING/`logs`, delegación con mock (`update==1→true`, `update==0→false` — 404 de carrera), `verifyNoMoreInteractions` (mono-sentencia), `@Transactional` presente por reflection. |
| T10.5 | hecho-ya + pin hecho-ahora | `LogsBorradoRepository` conforme (javadoc «ÚNICA morada de las MUTACIONES del panel sobre logs», constants package-private, `@Transactional` en ambos). Faltaba el pin: `LogsBorradoSqlTest` NUEVO — literales EXACTOS de `SQL_DELETE_POR_ID`/`SQL_DELETE_POR_PROYECTO`, 1 placeholder c/u, retorno int (`deleted:N`; 0⇒404), mono-sentencia, `@Transactional` en ambos métodos. |
| T10.6 | hecho-ya + pins hecho-ahora (+ corrección orden CASE) | Repo conforme (3 constants, `resumen()` con LinkedHashMap solo-claves-presentes, formatter C2, null si sin logs). Pins añadidos a `LogsQuerySqlTest` (3 @Test nuevos). **Corrección del orquestador aplicada**: CASE con DEBUG=4 e INFO=5 (mandan spec REQ-C10-UI-6 + maqueta 04 — «FATAL · ERROR · WARN · DEBUG · INFO»; divulgación spec-vs-design en D-C10-3). Repo, pin y javadocs actualizados. |
| T10.7 | hecho-ya (verificado) | DELETE /{id} y DELETE /{id}/logs con orden fail-fast EXACTO D-C10-1 (8a/8c): (1) `confirm_required` sin BD, (2) 404, (3) `confirm_mismatch` equals EXACTO sin `field`, (4) borrado; purga `{"deleted": N}` LinkedHashMap + desambiguación N=0 re-findById (M4); `GET /{id}/summary` 404-antes-de-log; `include` checks en orden (1) status byte-idéntico (2) include → 400 `filter_invalid_value` field=include ANTES de BD (D-C10-5); constants + helper error() patrón C3 + javadoc vocabulario. |
| T10.8 | hecho-ya (verificado) | DELETE /logs/{id} orden 8b: `confirm_required` → mismatch contra `String.valueOf(id)` SIN BD (M10) → `borrarPorId` 0⇒404/≥1⇒204. |
| T10.9 | hecho-ya (verificado) | Los 5 escenarios M10 en 8a/8c (id inexistente sin cabecera → 400; id inexistente con cabecera errónea → 404; existente+errónea [mayúsculas/espacio interior] → 400 + sin borrar; existente+correcta → 204 + verify id; carrera purgar→0+findById→vacío → 404), `{"deleted":55}`/`{"deleted":0}` cuerpos EXACTOS Jackson, ARCHIVED→204 sin rama (M5), 401 sin JWT (8a/8c/summary), resumen forma EXACTA 4 claves, include en todos, clave AUSENTE sin include, archivado resumen-intacto (RESUMEN-5), `include=otracosa` → 400 con field + `verifyNoInteractions` (incl. orden status-primero). |
| T10.10 | hecho-ya (verificado) | 6 @Test nuevos de 8b: 204 feliz + verify(borrarPorId) id correcto; `confirm_required` sin BD; mismatch con `verifyNoInteractions` (incl. id inexistente + desigual → 400, fail-fast M10); 404 con cabecera correcta sobre inexistente; log-del-401; `verifyNoInteractions(logsQueryRepository)` en el feliz (LOGDEL-4 — el controller de query no tiene dependency del repo de projects, nada del proyecto es tocable). |
| T10.11 | hecho-ahora | `CorsConfig` con el fragmento EXACTO de D-C10-6 (split por coma + trim + filtrado sobre la MISMA `FRONTEND_ORIGIN`; guard `origins.isEmpty()` — incluida `","` solitaria → cero registros; sin `*`, sin `setAllowedOriginPatterns`, sin env nueva; methods/headers/credentials/maxAge y registro `/**` SIN tocar). `CorsConfigTest` con los 5 casos nuevos: una-origin idéntica al canon C9 (REQ-C10-CORS-2 «sin cambio observable»), `a,b` → dos origins, `a, b , ,c` → trim+filtrado, `","`/`" "`/`""` → cero records + null en preflight, Origin no listada sin permitir (checkOrigin null). Prohibiciones G1-C2 re-pinadas en los casos nuevos (`getAllowedOriginPatterns` isNullOrEmpty). |

Veredicto global: Bloque 1 y Bloque 2 COMPLETOS y verdes; T10.12+ (Bloques 3–6) fuera del alcance de esta fase.

---

# Apply-progress — C10 · Bloque 5 (cierre — instancia qa-test-engineer)

> Alcance de esta instancia: cierre del Bloque 5 (T10.27 veredicto,
> T10.28 primera corrida del smoke, T10.29 CREATE `qa_vis_c10.sh` +
> completar/auditar `qa_vis_c10.py`) y rebuild de la pila con código C10.
> T10.26 (doc 08) HECHA en fase anterior. Sin commits, sin push. La corrida
> completa del arnés visual queda para T10.32 (fase posterior), conforme a
> la orden del orquestador: NO se ejecuta `qa_vis_c10.py` en corrida propia.

## Estado por tarea (Bloque 5)

| Tarea | Veredicto | Detalle |
|---|---|---|
| T10.26 | hecho-ya (fase anterior) | `docs/08_ACCESO_REMOTO.md` existía en disco al re-anclar; fuera del alcance de esta instancia. |
| T10.27 | hecho (auditado + 2 parches de guion) | `scripts/qa_c10_smoke.sh` (604 líneas, `bash -n` verde) CONFORME contra T10.27/D-C10-13 en estructura (10 checks S1–S10, supera el mínimo S1–S7; siembra efímera propia `qa-c10-<ts>-`; sin `down -v`; S16 tmpdir 700 con trap EXIT; gestión de pila propia con `up -d --build` y pila ARRIBA al término). 2 defectos DEL GUION detectados en T10.28 y parcheados acotados (ver hallazgos 1–2). |
| T10.29 | hecho (wrapper) + arnés COMPLETADO | `scripts/qa_vis_c10.sh` NUEVO (FC#36): gemelo estructural de `qa_vis_c9.sh` — mismo contrato (RAW teed a `.atl/sdd/C10/qa-log.txt`, grep S16 `plk_`/`eyJ` == 0, passthrough exit canon 0/1/2, guardas de entorno exit 2). `bash -n` verde; diff estructural vs C9 = SOLO comentarios (33 vs 26 checks, referencias de corte D-C10-13, nota de destructividad C10). `qa_vis_c10.py` (FC#35): la instancia anterior murió a mitad de fichero — quedaban 15/15 checks E10.x escritos pero SIN la mitad operativa del guion. Completado (ver hallazgo 3): fase L (limpieza con guard `^qa-c9-|^qa-c10-` skip-and-report), informe b8-rebasado con guard PROPIO de 33 checks (`ESPERA_CHECKS`), `main()` con el orden D-C10-13 (B0 → P0 aprovisionamiento REST con siembra → re-runs suaves c9.b1/b3/b6 → e10 (destructivos al final) → limpieza → c9.b7 (kill-window ÚLTIMA, sella E9.11) → informe) y `__main__` con `--check`. `py_compile` verde; `--check` verificado (import limpio de `qa_vis_c9` reconstatado). NO corrida (queda T10.32). |
| rebuild pila | hecho | Pila estaba CAÍDA (3 contenedores Exited hace 5 días). Reconstrucción canónica: `podman compose build` (exit 0, imágenes backend+frontend recreadas con el código C10: DELETEs, resumen, CORS latente, fixes H-C9) + `podman compose up -d --force-recreate` (SIN `down -v`, volumen pgdata intacto, `.env`/VM intocadas). Estado final: **3/3 healthy** (postgres, backend, frontend — `podman ps`), sondeo por el único puerto: `GET /api/v1/panel/me` SIN token → 401 `invalid_token` (señal H-C9-SALUD). |
| T10.28 | hecho (3 corridas: 2 rojas por defecto de guion → verde) | Corrida 1 (15:48:42+0100): **10 FAIL / 0 BLOQUEADO** (exit 1) — defecto de guion E1 (hallazgo 1). Corrida 2 de diagnóstico (15:49:18+0100): idéntico, con `KEEP_TMP=1` para forense. Corrida 3 (15:50:09+0100): 9 PASS · 1 FAIL (S1 `forma-datos`) — defecto de guion E2 (hallazgo 2). **Corrida FINAL (15:51:05–15:51:20+0100): 10 marcas · 10 PASS · 0 FAIL · 0 BLOQUEADO · exit 0.** Veredicto canónico para el qa-report: C10-S1..S10 PASS. |

## Hallazgos (todos defectos DEL GUION — parche acotado, patrón T10.33; cero defectos de producto)

1. **E1 — fichero de id de proyecto escrito a ruta anidada**: `crear_proyecto()` hacía `printf '%s' "$id" > "$TMPDIR_QA/$2_id.txt"`, pero `$2` (`F_KEY1`) es ya una ruta ABSOLUTA (`$TMPDIR_QA/key1.txt`) ⇒ el fichero caía en `$TMPDIR_QA//tmp/…/key1.txt_id.txt` (directorio inexistente, printf falla silenciosamente sin `set -e` en contexto condicional) y el lector (`"$TMPDIR_QA/$(basename "$F_KEY1")_id.txt"`) miraba otro sitio. Efecto: `$ID1/$ID2` vacíos en TODOS los URLs (`/projects//summary`, `/projects/`) → 400/401 en masa + efecto cascadado en S7/S8/S10 (el DELETE del par nunca ocurría ⇒ la «key muerta» de S8 seguía viva ⇒ 202). Forense con `req_debug.log` del tmpdir conservado: los URLs válidos con el MISMO bearer respondían 200/204/404 — el backend estaba BIEN. Fix: `> "$2_id.txt"` + comentario del contrato.
2. **E2 — siembra S1 inconsistente con su propia distribución documentada**: la cabecera, el assert y el veredicto del smoke declaran/expectan «production 35 · staging 10» pero el plan sembraba production 40 (FATAL 1 + ERROR 5 + DEBUG 4 + INFO 30) y staging 5 (WARN 5) ⇒ el assert `by_environment` de C10-S1 fallaba (`forma-datos`). Fix: plan INFO repartido 25/5 → production 35 · staging 10, mismo total 45 y mismas cifras by_level.
3. **E3 — `qa_vis_c10.py` INCOMPLETO (instancia anterior interrumpida)**: el fichero terminaba a mitad de la función `e10()` (L1045, tras E10.15): faltaban por completo fase L de limpieza, informe b8-rebasado, `main()` (B0/P0/invocación de b1-b3-b6/b7) y `__main__`. Completado conforme a D-C10-13 (import limpio constatado en cabecera, rebaso de 3 globals ya presente, guard propio de 33 checks = 18 re-runs + 15 E10.x) replicando la estructura del canon `qa_vis_c9.py` (b8/salirPorVeredictos verificados como referencia). Además, 3 sub-fixes acotados del propio E10: (a) `c9.cerrarSiAbierta(page)` → `cerrarSiAbierta(page)` LOCAL — esa función NO existe en el módulo C9 y crashearía TODOS los excepts de E10 con AttributeError; (b) `resto_ingesta()` INVOCADA por E10.12 pero nunca definida — creada (POST /ingest/batch con la key superviviente de A y needle propio `POST_P`, S16 en memoria); (c) `m0` de la ventana WS de la purga inicializado a `None` para que E10.11 reporte «encadenamiento roto» honesto en vez de NameError.
4. **Residuo arnés en BD (reportado, NO limpiado aquí)**: las 2 corridas fallidas dejaron 4 proyectos `qa-c10-20261104154842-{purga,par}` y `qa-c10-20261104154918-{purga,par}` (ids 3–6, con semillas). El guard de limpieza `^qa-c9-|^qa-c10-` de la fase L del arnés (T10.32) los barre; el smoke final limpió SOLO su propio `<ts>`. Datos del usuario (1 proyecto) intocados — verificado por sondeo REST.
5. Entorno: build canónico con `podman compose` (podman-compose provider), sin `down -v` en ninguna operación; tmpdirs de diagnóstico con JWT borrados tras la forense.

## Ficheros tocados (todos dentro de la tabla §4 del design)

- `scripts/qa_vis_c10.sh` — NUEVO (FC#36, T10.29): wrapper gemelo (bash -n OK).
- `scripts/qa_vis_c10.py` — COMPLETADO (FC#35, T10.29): cola faltante (limpieza/informe/main/__main__) + 3 sub-fixes (py_compile OK, `--check` OK). NO corrida — T10.32.
- `scripts/qa_c10_smoke.sh` — EDITADO (FC#37, T10.27, 2 parches acotados T10.33): fichero de id + plan de siembra 35/10 (bash -n OK, corrida final exit 0).
- `.atl/sdd/C10/apply-progress.md` — este apéndice.

Frontera respetada: `qa_vis_c9.py`/`.sh` y smokes previos dif-0 (verificado con `git diff`), compose.yaml dif-0, sin toques a SecurityConfig/JwtAuthFilter/ingest/streaming/client.ts/nginx/VM — sin commits, sin push, sin secretos impresos (S16).

## Cifras mvn (builder canónico podman C4/C7/C8, imagen maven:3.9-eclipse-temurin-21)

| Corrida | Momento | Resultado |
|---|---|---|
| baseline | al re-anclar (tras arreglar import content faltante de la instancia anterior en `LogsControllerMvcTest`) | **203/203** |
| T10.2 (pins serialización) | tras completar ProjectDtoSerializationTest | **206/206** |
| T10.4 (pin SQL_BORRAR) | tras crear ProjectsBorradoSqlTest | **210/210** |
| T10.5 (pin DELETEs logs) | tras crear LogsBorradoSqlTest | **215/215** |
| T10.6 (pins resumen) | tras extender LogsQuerySqlTest | **218/218** |
| T10.11 (CORS) | tras CorsConfig + CorsConfigTest | **223/223** |
| cierre (PATCH-pin + corrección CASE DEBUG/INFO) | final de fase | **223/223, BUILD SUCCESS** |

**Cifra final: mvn 223/223, Failures 0, Errors 0** (177 heredados previos a C10 SIN
edición ni relajación de asertos + 46 nuevos de C10; desglose: +26 MvcTest de
bloque 1 de la instancia anterior +3 serialización T10.2 +4 pin SQL_BORRAR
T10.4 +5 pin DELETEs T10.5 +3 pins resumen T10.6 +5 CORS T10.11).

## Ficheros tocados (todos dentro de la tabla §4 del design)

- Backend main (5/8): `ProjectsController.java` (hecho-ya, verificado),
  `SummaryDto.java` (hecho-ya, verificado), `ProjectDto.java` (hecho-ya,
  verificado), `ProjectsRepository.java` (hecho-ya, verificado),
  `LogsBorradoRepository.java` (hecho-ya, verificado),
  `LogsQueryRepository.java` (hecho-ya + EDITADO ahora: CASE DEBUG=4/INFO=5 +
  javadocs), `LogsController.java` (hecho-ya, verificado) — 7/8 revisados;
  `config/CorsConfig.java` (EDITADO ahora: D-C10-6) — 8/8.
- Backend test (7/7): `ProjectsControllerMvcTest` (hecho-ya + EDITADO ahora:
  pin ausencia-summary en PATCH 200), `LogsControllerMvcTest` (hecho-ya +
  EDITADO ahora: import `content` faltante — compile-fix de la instancia
  interrumpida), `ProjectDtoSerializationTest` (EDITADO ahora: 3 pins nuevos +
  javadoc + import Map), `ProjectsBorradoSqlTest` (NUEVO),
  `LogsBorradoSqlTest` (NUEVO), `LogsQuerySqlTest` (EDITADO ahora: 3 pins +
  corrección CASE), `config/CorsConfigTest` (EDITADO ahora: 5 casos nuevos).

## Desviaciones / hallazgos

1. **Compile-fix heredado (instancia anterior interrumpida)**:
   `LogsControllerMvcTest` importaba `content()` sin el static import
   correspondiente — el bloque 1 anterior quedó con el test-suite en rojo de
   compilación. Arreglado ANTES de añadir nada (regla: jamás seguir con rojo).
2. **T10.2 incompleta en el re-anclaje**: los 3 pins de SummaryDto
   (4-claves-literales / estado-vacío con null explícito / regex ISO-Z) y el
   pin de ausencia en PATCH 200 no estaban; completados.
3. **Divergencia spec-vs-design — orden de bandas del resumen (CASE)**:
   D-C10-3 transcribió el CASE con INFO=4/DEBUG=5 pero la maqueta 04/REQ-C10-
   UI-6 pide DEBUG ANTES de INFO. Decisión del orquestador: manda spec+maqueta
   — CASE del repo con DEBUG=4/INFO=5, pin ajustado, frontend sin tocar.
   Registrada en `.atl/notes/c10-nota-orden-bandas-resumen-case-info-debug.md`
   (nota actualizada con la decisión final).
4. **Entorno**: `mvn` no está en PATH del host (ni maven nativo compatible:
   el JDK del host es 25 y el pom exige release 21) — la corrida canónica de
   este corte es por builder podman, MISMO comando de C4/C7/C8:
   `podman run --rm -v /home/acuelop369/Proyectos/Project_Log/backend:/ws:Z -v /home/acuelop369/.m2:/root/.m2:Z -w /ws docker.io/library/maven:3.9-eclipse-temurin-21 mvn test`.
5. Frontera respetada: sin toques a `SecurityConfig`, `JwtAuthFilter`,
   `ingest/**`, `streaming/**`, `compose.yaml`, `frontend/**`, `docs/**`,
   smokes previos ni VM. Sin commits, sin push, sin secretos impresos (S16).

## Corridas canónicas T10.30/T10.31 (instancia qa, paralela al Bloque 5)

Ejecutadas 2026-11-04 por la instancia qa-test-engineer, en paralelo al Bloque 5.
Solo verificación y registro: **cero toques a código ni a tests**.

### T10.30 — `mvn test` completo (builder canónico podman)

Comando ejecutado (MISMO de C4/C7/C8, imagen `maven:3.9-eclipse-temurin-21`):

```
podman run --rm -v /home/acuelop369/Proyectos/Project_Log/backend:/ws:Z \
  -v /home/acuelop369/.m2:/root/.m2:Z -w /ws \
  docker.io/library/maven:3.9-eclipse-temurin-21 mvn test
```

Resultado: **BUILD SUCCESS** (exit 0, Total time 26.089 s).

| Métrica | Valor | Esperado (state) |
|---|---|---|
| Tests | **223** | 223 |
| Failures | **0** | 0 |
| Errors | **0** | 0 |
| Skipped | 0 | 0 |

Desglose por suite de los C10 (surefire, todos verdes): 401-ingesta 1, 401-panel 2,
CorsConfigTest 9, ProjectDtoSerializationTest 5, ProjectsBorradoSqlTest 4,
LogsBorradoSqlTest 5, LogsQuerySqlTest 7 — resto heredado verde (177).

RAW: `.atl/sdd/C10/qa-log-mvn-raw.txt` (348 líneas; escaneado `plk_`/`eyJ`:
sin secretos).

### T10.31 — frontend (`npx tsc --noEmit` + `npx vitest run`)

1. `npx tsc --noEmit` → **sin errores** (exit 0, sin salida).
2. `npx vitest run` (vitest v5.0.5, 606 ms) → **BUILD verde**, exit 0.

| Métrica | Valor | Esperado (state) |
|---|---|---|
| Test Files | **9 passed (9)** | — |
| Tests | **80 passed (80)** | 80 |
| Failures / Errors | **0 / 0** | 0 |

RAW: `.atl/sdd/C10/qa-log-vitest-raw.txt` (9 líneas; escaneado `plk_`/`eyJ`:
sin secretos).

### Veredicto de la instancia qa

T10.30 y T10.31 **CIERRES: cifras canónicas reproducidas exactas** (223/223 mvn,
0/0; tsc limpio; 80/80 vitest) sobre el árbol de trabajo actual. Sin hallazgos
ni tests en rojo. No se tocó `.atl/sdd/C10/qa-log.txt` (lo escribe otra fase).
---

# Apply-progress — C10 · Bloque 6 (corridas ejecutables — instancia qa-test-engineer)

> Alcance de esta instancia: T10.32 (corrida completa del arnés), T10.33
> (bucle de hallazgos) y registro. **T10.34 (re-corridas de regresión) NO
> EJECUTADA** — la instancia DETUVO el bucle ante un hallazgo de PRODUCTO
> (pauta T10.33: hallazgo de producto ⇒ nota + escalado al orquestador,
> jamás forzar verde). Sin commits, sin push.

## T10.32 — corrida 1 del arnés visual C10 (ts 20261104155255)

- Comando: `bash scripts/qa_vis_c10.sh` (pila 3/3 healthy verificada antes).
- Resultado: **11 marcas · 6 FAIL · 0 BLOQUEADO · exit 1** (canon honesto).
  Guard de cierre falla: límite EXACTO 33 checks, marcados 11 — defecto del
  flujo: la corrida murió antes de E10.x y de la ventana de kill.
- Cifras del informe: 6 FAIL (E9.1, E9.7, E9.10, E9.11-sin-sello,
  E9.12, E9.15 — todos TIME-OUTS esperando contenido de /logs) + 5
  PASS-CON-NOTA (E9.2 destino recordado URL PERDURANDO **— re-run del
  fix D-C10-11 VERDE en vivo**, E9.3, E9.4, E9.5, E9.6).
- Grep S16 post-corrida: **0 hits** (`plk_`/`eyJ` — verificado a mano sobre
  el RAW y por el propio wrapper).
- PNGs en disco (5): E9.2_01_01_retorno_destino.png, E9.3_01_02_token_
  basura_formulario.png, E9.4_01_01_404_panel.png,
  E9.5_01_01_401_inline_limpio.png, E9.6_01_01_enviando_deshabilitado.png
  (sha256 en ficha: aa6a0dfc…, 066f49c4…, 1b16fa1d…, 51712faa…,
  d9240e2f… — NO commitear, D-C9-12/C10).

## T10.33 — bucle de hallazgos: 1 corrida ⇒ hallazgo de PRODUCTO ⇒ DETENTE

1 corrida del arnés. El diagnóstico acotado (diagnosticador Playwright en
/tmp, creds SOLO en memoria — S16) REPROUJO y CLAVÓ la causa en vivo:

- **La pantalla /logs se BLANQUEA entera** (main×0, pageerror
  `TypeError: Illegal invocation`); TODAS las demás pantallas cargan
  (E9.2–E9.6 verdes; /proyectos renderiza el header C10 «GET
  /projects?include=summary · 7 proyectos» — el resumen está vivo).
- **Defecto de PRODUCTO** (NO del arnés — no hay parche de guion posible
  ni admisible): `frontend/src/ws/client.ts` L105 — constructor con
  `reloj: Reloj = { setTimeout, clearTimeout }` (natives DESENLAZADOS) +
  T10.20/D-C10-7 arma en TODO `abrir()` el timer CONNECTING con
  `this.reloj.setTimeout(...)` (L172) ⇒ primera llamada del Reloj en la
  vía feliz ⇒ Chromium lanza `TypeError: Illegal invocation` (nativo
  invocado sin receiver) ⇒ crashea la raíz React de /logs SIN
  ErrorBoundary. Latente desde el Reloj default de C8 (el único uso
  previo era backoff, jamá alcanzado en visuales: E9.11-C9 moría antes);
  C10 lo ACTIVA. Verificado: stack del bundle servido
  (index-JKFaOz3p.js, col ~37016 = la línea del armado; `{setTimeout,
  clearTimeout}` en col 36225) + evaluación en página (desestructurado ⇒
  THROWS; con receiver ⇒ ok).
- Vitest 80/80 NO lo pilla: todos los tests de ws/client.ts inyectan
  Reloj faké — la rama del default jamá se ejercita.
- Nota completa escalada al orquestador:
  `.atl/notes/c10-nota-crash-logs-ws-reloj-natives-desenlazados.md`
  (frontmatter title/type/what/why/where/learned). Fix propuesto (UNA
  línea, decidirlo y delegarlo el orquestador — frontend/src intocable
  para esta instancia): envolver/bindear los natives en el default del
  Reloj (ej. arrow), mismo patrón en el clearTimeout compartido.
- Defecto MENOR del ARNÉS también registrado (NO parcheado — la corrida
  está detenida por producto): el informe b8 imprime la sección RESIDUO
  con texto hardcodeado «PURGADO (E10.10) y ELIMINADO (E10.13 — 8a
  real)» aunque esos E10.x NO corrieron (no maquilla veredictos: el
  guard de 33 falló y exit fue 1). Parche acotado pendiente para cuando
  el bucle re-arranque.

## Estado de siembra / residuo (sondeo REST 2026-11-04 ~15:56+0100)

- BD: 7 proyectos — 6 residuo `qa-c10-*` (4 de las corridas fallidas del
  smoke T10.28: `qa-c10-20261104154842-{par,purga}`,
  `qa-c10-20261104154918-{par,purga}`; 2 de ESTA corrida 1:
  `qa-c10-20261104155255-{par-b,purga-8c}`, con las 63 semillas del arnés)
  + **1 proyecto del usuario, INTOCADO**. Todo el residuo matchea el guard
  `^qa-c9-|^qa-c10-` ⇒ fase L del arnés lo barre en la próxima corrida
  (la de esta corrida NO llegó a correr — murió en b1).
- Pila al terminar: 3/3 healthy (postgres, backend, frontend) — la
  kill-window (stop/up backend) NO se ejecutó.

## T10.34 — re-corridas de regresión: NO EJECUTADA (pendiente del orquestador)

Canonical anotado para cuando toque: C3 32/33 PASS · 1 BLOQUEADO (Q23)
pauta E3.22 post-D2; C7 9/9 PASS exit 0 (pre-fase N/A-post-D2, Q23
tolerado); C8 24 marcas · 23 PASS · 0 FAIL · 1 BLOQUEADO (Q23) exit 0. La
pauta del bucle (DETENTE ante hallazgo de producto) y el orden destructivo
(AL FINAL tras arnés verde) impiden correrlas ahora sin decisión del
orquestador.

## Ficheros tocados por esta instancia

- `.atl/sdd/C10/qa-log.txt` — escrito SOLO por el wrapper (intocable a mano).
- `.atl/sdd/C10/evidencia/*.png` (5) — generados por la corrida (NO en git).
- `.atl/notes/c10-nota-crash-logs-ws-reloj-natives-desenlazados.md` — NUEVO.
- `.atl/sdd/C10/apply-progress.md` — este apéndice.
- /tmp/diag_c10.py — diagnosticador efímero (fuera del repo).
- Frontera respetada: `qa_vis_c9.*` y smokes previos sin tocar, frontend/ y
  backend/ sin tocar, sin commits, sin push, sin secretos impresos (S16).

### Parche acotado del arnés (decisión ratificada del orquestador, 2026-11-04)

- **Objeto**: informe b8 — sección RESIDUO en `scripts/qa_vis_c10.py`
  (defecto menor del arnés registrado en T10.33). El texto
  «PURGADO (E10.10) y ELIMINADO (E10.13 — 8a real)» era hardcodeado aunque
  esos checks no hubieran corrido.
- **Parche**: el estado del residuo ahora se DERIVA de `c9.RESULTADOS`
  (veredicto real por ID, con helper `veredictoCheck`) — tres estados
  honestos por destino: «EJECUTADO y VERDE» / «corrió PERO veredicto FAIL —
  estado en BD NO confirmado (jamá fingido)» / «NO EJECUTADO (no corrió —
  estado en BD INVERIFICADO)». También condicionado: línea del proyecto B
  (veredicto de E10.4 o «no corrió») y línea de las semillas congeladas
  (CASCADE solo con purga GREEN). **Guard de 33 y exit canon intactos**
  (ninguna lógica de salirPorVeredictos10 tocada).
- **Verificación**: `py_compile` OK, `python3 scripts/qa_vis_c10.py
  --check` OK (import C9 limpio), `bash -n` del wrapper OK, y prueba
  sintética in-memoria de la lógica con 3 escenarios (sin E10.x → None;
  verde → PASS; FAIL → FAIL) — LÓGICA_OK.
- **NO re-corrida del arnés** (orden del orquestador: primero fix de
  producto ratificado en frontend + rebuild imagen).

## Fix producto ratificado — Reloj desenlazado (ws/client.ts L105)

- **Objeto**: `frontend/src/ws/client.ts` — default del `Reloj` del
  constructor (`{ setTimeout, clearTimeout }`, natives del scope global SIN
  receiver). En Chromium, invocarlos desenlazados lanza
  `TypeError: Illegal invocation`; el timer CONNECTING nuevo de C10
  (D-C10-7, armado en `abrir()`) usa el Reloj en la vía feliz de TODA
  conexión ⇒ cualquier navegación a /logs crasheaba la raíz React.
- **Diff resumido** (constructor, única zona tocada del fichero):

  ```ts
  // ANTES
  constructor(callbacks: WsCallbacks, reloj: Reloj = { setTimeout, clearTimeout }) {
  // DESPUÉS
  constructor(
    callbacks: WsCallbacks,
    reloj: Reloj = {
      setTimeout: (fn, ms) => setTimeout(fn, ms),
      clearTimeout: (t) => clearTimeout(t),
    },
  ) {
  ```

  Comentario español añadido sobre el constructor explicando la envoltura
  (receiver fijo). NADA más en el fichero: TipoReloj, armado del timer,
  cancelarTimerApertura, ciclo WS INTACTOS. Contrato de los tests con Reloj
  fake sin cambios.
- **Pin añadido** (sí): 1 test en `client.test.ts` (describe nuevo
  «ClienteWsLogs — default del Reloj (fix C10)») — instancia la clase SIN
  `reloj` (rama del default, jamás ejercitada) y ejecuta
  `reloj.setTimeout(() => {}, 0)` + `clearTimeout(id)` con natives reales
  bajo `.not.toThrow()`, más un assert de que el default expone funciones
  invocables. Sin acoplarse a `conectar()` ni a WebSocket stub (no los
  usa): 81.º test, riesgo cero para los 80 previos.
- **tsc**: `npx tsc --noEmit` desde `frontend/` → 0 errores.
- **vitest**: `npx vitest run` desde `frontend/` → **81/81 (9 ficheros)**
  SIN regresión (80 previos + 1 pin).
- **Rebuild/recreate**: `podman compose build frontend` (imagen
  `localhost/project_log_frontend:latest` — servicio build según
  compose.yaml, context ./frontend) → `podman compose up -d --force-recreate
  frontend` (SOLO frontend; postgres y backend SIN recrear — depende_on
  no desencadenó recreate ajeno).
- **Estado pila tras el recreate**: postgres_1 healthy (up ~1 min),
  backend_1 healthy, frontend_1 healthy (wget :80 GREEN). Bundle servido:
  `curl -s http://localhost:8080/` sirve **index-DS0Nr1vD.js** (antes
  index-JKFaOz3p.js); grep del bundle confirma la firma nueva
  `setTimeout:(o,m)=>setTimeout(o,m),clearTimeout:o=>c…
  ` — default envuelto, no natives crudos.
- **Ficheros tocados**: `frontend/src/ws/client.ts` (fix), 
  `frontend/src/ws/client.test.ts` (pin, SOLO el test añadido), este
  apéndice y la nota del hallazgo (sección Resolución). Resto de frontend/,
  backend/, scripts/, compose.yaml, .env, VM: SIN tocar.
- **Sin commits, sin push** (pendiente de orden del orquestador).

## Parte 3 (aviso del orquestador, 2026-11-04 ~16:00) — re-corrida 2 + bucle T10.33

## Re-corrida 2 del arnés visual C10 (ts 20261104160223)

- Contexto: fix de producto aplicado por el orquestador (Reloj envuelto en
  `ws/client.ts` L105 + pin de test, rebuild frontend, bundle
  index-DS0Nr1vD.js, Vitest 81/81).
- Comando: `bash scripts/qa_vis_c10.sh` — resultado: **33 marcas · 14 FAIL ·
  1 BLOQUEADO · exit 1**. Parche del informe b8 VERIFICADO en vivo: la
  sección RESIDUO imprimió estados REALES derivados de `c9.RESULTADOS`
  (sin texto harcodado).
- **Re-runs E9 verdes o honestos**: E9.1–E9.6, E9.10–E9.12, E9.15–E9.18,
  E9.24–E9.26 PASS-CON-NOTA; **la kill-window E9.11/E9.26 SÍ completó** (stop
  backend + up + health + sellado); E9.7 variante B BLOQUEADO honesto (el
  mismo de su canon C9). **Única excepción del re-run: E9.13 FAIL** (ver
  clasificación abajo).
- Fase L corrió: «eliminados 7 de arné · saltados 1 · fallos 0» — barrió el
  residuo `qa-c10-*` de las corridas anteriores; el proyecto del usuario
  quedó SALVADO por el guard.
- Grep S16 post-corrida: limpio (el wrapper hubiese salido 2; salió 1 —
  veredictos, no fuga).

## T10.33 — clasificación completa de los 14 FAIL (evidencia empírica, repros en /tmp)

| Check | Clasificación | Causa y evidencia |
|---|---|---|
| E10.15 | **PRODUCTO** (CORS) | Preflight vivo con `ACR-Headers: x-confirm-name` → **403 «Invalid CORS request»**; con `authorization` → 200 reflejado EXACTO. `DefaultCorsProcessor` de Spring NO pattern-machea `X-Confirm-*` (solo igualdad o `*` completo). `CorsConfigTest` pina la LISTA de config pero nunca ejecuta el preflight real — el pin de config no alcanzó la capa HTTP. Latente en UI (same-origin por proxy nginx: el browser no preflightea los DELETE) pero ROMPE los 8a/8b/8c desde el 2.º origin «latente» G1-a. Design L146 («X-Confirm-* cubre las cabeceras nuevas») no se cumple a nivel HTTP. Nota: `.atl/notes/c10-nota-cors-preflight-x-confirm-wildcard-no-matcheado.md` (type: bugfix). Fix de producto a ratificar por el orquestador: headers EXACTOS `X-Confirm-Name`/`X-Confirm-Log-Id` en `setAllowedHeaders` + pin de profundiad MockMvc con ACR-Headers. |
| E10.1, E10.2, E10.5(ok_antes) | GUION — expectativa fantasma | Cifras hardcodeadas de la siembra propia (SOPORTES_RES: «9 logs», «3 logs totales», FATAL 1/ERROR 2/…) en un proyecto A que ACUMULA toda la siembra de la corrida (73 en P0 + 6 del re-run de b6 = 79 reales). El producto renderiza CORRECTO los datos reales (E10.2: banda=True, req=True; solo falla `by_level` contra el fantasma). **E10.3 pasó COINCIDENTALMENTE** («9 logs» ⊂ «79 logs»; FATAL 1 real). E10.5 falló solo `ok_antes` («3 logs totales» en B, que acumula residuo de corridas anteriores). Parche a ratificar: re-anclar las afirmaciones al CUERPO REST VIVO (`GET /projects/{id}/summary` dentro del check; badges/orden/franja contra el JSON real con el mismo formatear()) — la cabecera del propio fichero (L130-134) ya describe ese intento. |
| E10.6, E10.7 | GUION — premisa IMPOSIBLE por transporte | El fill con espacios extremos NO produce 400: el transporte HTTP recorta los OWS del field-value (RFC 9110) ⇒ el servidor recibe el NOMBRE EXACTO y su comparación EXACTA ADMITE ⇒ **EJECUTA** la acción (repro: 8c → 200 purga REAL; 8a → 204 delete REAL). El producto es COMPLIANT (texto viaja tal cual — G2-a/M1; server compare EXATO pinned con espacios INTERIORES → 400). La ejecución accidental DESTRUYÓ los datos de los checks siguientes → **cascada E10.8–E10.14** (e8b 0 filas, encadenamiento roto, summary 404, 401 key muerta, botón/404-ui inexistente). Parche a ratificar: vía **NBSP** (`U+00A0`) — VALIDADA empíricamente en repro: botón HABILITADO (trim() de JS lo quita) → click → **400 `confirm_mismatch` DENTRO del modal**, sesión intacta, NADA borrado (total==3 comprobado por REST antes/después), autolimpieza 200/204. Nota: `.atl/notes/c10-nota-http-recorta-ows-confirm-trim-malo-ejecuta-nbsp-alternativa.md` (type: discovery). |
| E10.8–E10.14 | GUION — cascada de E10.6/E10.7 | Ver fila anterior. Ningún defecto de producto observado en su código: cada fallo es el estado de BD dejado por las ejecuciones accidentales (repro /tmp/repro_c10_flow.py con proyecto efímero propio confirmó REQ/RES reales). |
| E9.13 | GUION (c9.b6, INTOCABLE) — artefacto de VENTANA | El store SÍ salta el primer fetch al volver (validado): repro con orden canónico + 600 ms tras `wait_for_url` → **0 GETs en la ventana**, query/65 filas intactas. El check de c9 (L2035) toma `reqs_antes` INMEDIATAMENTE tras `wait_for_url`, SIN esperar el fetch propio del DETALLE — micro-repro con el orden exacto de b6, 3/3 corridas → n_refetch=1 y el GET de la ventana es **`GET /panel/logs/{id}`** (el del detalle). El retorno real hace 0 GETs a /panel/logs. Defecto de la VENTANA de medición del guion C9 — INTOCABLE para esta instancia; requiere ratificación del orquestador (apretón del `reqs_antes` o espera del estabilizado del detalle en c9.b6). |

- Bucle DETENIDO aquí (pauta: hallazgo de PRODUCTO E10.15 ⇒ nota +
  escalado; NO maquillar ni forzar verde). Los parches del arnés
  (E10.1/2/3/5 re-anclo, E10.6/7 NBSP) quedan DISEÑADOS y VALIDADOS
  (repro empíricos) PERO NO aplicados — pendientes de ratificación, mismo
  patrón que el parche del informe b8.

## Estado del residuo al cerrar esta instancia (2026-11-04)

- Limpieza por guard de nombre (REST, X-Confirm-Name con N real): los 5
  proyectos repro `qa-c10-repro-*` (ids 16–20) purgados+eliminados (204);
  proyecto del usuario `smoke-c8-20261029171145-proyecto` (id=1) SALVADO.
- Pila: **3/3 healthy** (postgres_1, backend_1, frontend_1 healthy;
  `pp-pg16-dev` — contenedor ajeno de este arnés, sin tocar).
- Repros efímeros de esta parte en /tmp (fuera del repo):
  repro_c10_flow.py, repro_e913_c10.py, repro_e913_micro.py,
  repro_nbsp_c10.py — creds SOLO en memoria (S16).

## Parte 4 — parches ratificados aplicados (2026-11-04 ~17:05)

### Parche 1 — re-anclaje al cuerpo REST vivo (E10.1/E10.2/E10.3/E10.5)
- **Objeto**: `scripts/qa_vis_c10.py` — las cifras fantasma («9 logs»,
  «3 logs totales», FATAL 1/ERROR 2/… de `SOPORTES_RES`) causaban FAILs de
  guion: A acumula TODA la siembra de la corrida (79 reales en la
  re-corrida 2) y B conserva residuo de corridas previas; el producto
  renderiza bien.
- **Parche**: helpers NUEVOS — `fmt10()` (el formatear() EXACTO de
  ResumenLogs: separador de miles con espacio), `resumenVivo(pid)` (guard:
  summary REST con by_level, sin éxito fingido) y `badgesEnOrden()` (find
  ADVANCIENDO — jamá una substring de otro badge). Los 4 checks ahora
  asertan que la UI renderiza FIELMENTE el JSON vivo: nº de badges == nº
  de claves, textos `NIVEL CNT` con fmt10, ORDEN == orden del JSON
  (badgesEnOrden), franja/bloque con el total formateado (texto exacto
  del span grande + «logs totales»), `last_log_at` del cuerpo visible
  (E10.2), archivado == cuerpo REST idéntico antes/después (E10.5).
  **SOPORTES_RES ELIMINADO** (sin usos). Guard de 33 y exit canon sin
  tocar.
- **Verificación**: `py_compile` OK + `python3 scripts/qa_vis_c10.py
  --check` OK (import C9 limpio). Validación empírica del enfoque en los
  repros de la parte 3 (el REST vivo siempre retornó cuerpos coherentes).

### Parche 2 — NBSP extremo en los trim-igual-malo (E10.6/E10.7)
- **Objeto**: los fills con espacios extremos EJECUTABAN el destructive
  (el transporte HTTP recorta los OWS del field-value — RFC 9110; repro
  200-purga/204-elimina de la parte 3).
- **Parche**: fill de E10.6 y del segundo fill de E10.7 ahora es
  `f"{NOMBRE_A} "` (NBSP extrema). Primer fill de E10.7 (variante
  gate-desactivado « NOMBRE_Axxx») INTACTO. Asserts sin cambios
  (gate/400/modal/sesión/intacto). Nota `conNota` actualizada.
- **Verificación**: la vía NBSP fue VALIDADA empíricamente (repro
  /tmp/repro_nbsp_c10.py, parte 3): botón HABILITADO + 400 confirm_mismatch
  DENTRO del modal + nada borrado (total==3) + autolimpieza 200/204.
  Nota documental:
  `.atl/notes/c10-nota-http-recorta-ows-confirm-trim-malo-ejecuta-nbsp-alternativa.md`
  (type: discovery).

### Desviación D-C10-16 — excepción ÚNICA al intocable qa_vis_c9.py
- **Objeto**: bloque b6 (check E9.13) — artefacto de VENTANA de muestreo:
  `reqs_antes` se tomaba inmediatamente tras `wait_for_url` y el fetch
  PROPIO del detalle (`GET /panel/logs/{id}`) caía DENTRO de la ventana
  del retorno → pseudo-FAIL permanente («1 GET al montar») con producto
  CORRECTO (repro canónico 0 GETs; micro-repro 3/3 con el orden exacto
  capturando sólo ese GET propio).
- **Hunk** (el diff de `scripts/qa_vis_c9.py` contiene únicamente ESTO —
  verificado con `git diff`: 7 inserciones, 1 hunk; grep S16 del fichero
  = 0): `page.wait_for_timeout(600)` tras
  `page.wait_for_url("**/logs/*?*")` y ANTES de
  `url_detalle = urlSegura(page.url)`, con comentario español que cita la
  evidencia.
- **Alcance del registro**: **C9 queda TOCADO EN SU GUION POR C10** — el
  qa-report/verify de C10 lo reflejarán (la frontera intocable se rompió
  con ratificación EXPLÍCITA del orquestador para evitar el pseudo-FAIL
  permanente en lo canónico). `py_compile` de c9 OK tras el hunk.

## Fix producto ratificado — CORS setAllowedHeaders (D-C10-15)

- **Origen**: hallazgo E10.15 del arnés visual (nota
  `.atl/notes/c10-nota-cors-preflight-x-confirm-wildcard-no-matcheado.md`) —
  el preflight REAL con `Access-Control-Request-Headers: x-confirm-name`
  devolvía 403 «Invalid CORS request»: el `DefaultCorsProcessor` de Spring
  matchea los headers pedidos por IGUALDAD (solo el `*` COMPLETO es
  wildcard), así que el literal `X-Confirm-*` de `setAllowedHeaders` era
  decorativo a nivel HTTP.
- **Fix** (`backend/src/main/java/com/projectlog/config/CorsConfig.java`,
  bloque allowedHeaders):
  `List.of("Authorization", "Content-Type", "X-Confirm-*")` →
  `List.of("Authorization", "Content-Type", "X-Confirm-Name",
  "X-Confirm-Log-Id")` + comentario del bloque reescrito en español
  (matcheo por igualdad, E10.15, REQ-C10-CORS-3 COMPORTAMIENTO). Se
  MANTIENEN Authorization y Content-Type; prohibidos respetados: sin `*`
  completo, sin `setAllowedOriginPatterns` (G1-C2 vigente).
- **Test** (`backend/src/test/java/com/projectlog/config/CorsConfigTest.java`):
  el pin de la lista fija ahora los 4 literales exactos (único aserto
  heredado retocado — fijaba el literal sustituido) y se AÑADE un slice
  `@Nested` de PROFUNDIDAD (comportamiento) con dos tests: preflight REAL de
  extremo a extremo por la cadena 2 (`@WebMvcTest(ProjectsController.class)`
  + `SecurityConfig/JwtAuthFilter/RestAuthenticationEntryPoint/CorsConfig`,
  `projectlog.security.frontend-origin=http://localhost:8080`) —
  `OPTIONS /api/v1/panel/projects` con ACR-Method DELETE + ACR-Headers
  x-confirm-name ⇒ 200 + ACAO reflejada + ACAM con DELETE + ACAH con
  x-confirm-name; y la pieza espejo (origin ajena ⇒ 403 sin cabeceras).
  Los demás asertos del corte quedan intactos.
- **Verificación mvn** (canon, builder podman
  maven:3.9-eclipse-temurin-21): BUILD SUCCESS — **Tests run: 225,
  Failures: 0, Errors: 0, Skipped: 0** (223 previos VERDES + 2 pins nuevos
  del preflight real).
- **Deploy a la pila local**: `podman compose build backend` (rebuild de
  src, capa mvn package nueva: 06b23b9a4e48 → imagen
  b5908c377828) + `podman compose up -d --force-recreate backend`.
  **Desviación menor**: compose arrastró a los dependientes y postgres y
  frontend también se rec-recrearon (uptime ~1 min en el ps); pgdata es
  volumen NAMED — INTACTO (4 tablas en public verificados via psql), sin
  `down -v`, sin puertos nuevos, resto del stack sin tocar. Salud final:
  **3/3 containers healthy** + backend `{"status":"UP"}` (actuator/health,
  canon TRD §6.5) + wget del frontend GREEN.
- **Verificación del preflight vivo** (el mismo sondeo REST de E10.15, sin
  JWT — el CORS se procesa antes de la cadena de seguridad, canon E7.9):
  - `OPTIONS /api/v1/panel/projects` + `Origin: http://localhost:8080` +
    ACR-Method DELETE + ACR-Headers `x-confirm-name` ⇒ **HTTP 200** con
    `Access-Control-Allow-Origin: http://localhost:8080`,
    `Access-Control-Allow-Methods: GET,POST,PATCH,DELETE,OPTIONS` y
    `Access-Control-Allow-Headers: x-confirm-name` (+ Max-Age 3600).
  - La misma petición con `Origin: http://atacante.example` ⇒ **HTTP 403**
    «Invalid CORS request», SIN cabeceras CORS.
  - E10.15 queda desbloqueado por producto (re-corrida del arnés la hará
    la instancia qa).

## Bloque 6 — corrida final + regresiones (2026-11-04, cierre T10.32/T10.33/T10.34)

### T10.32 — corrida FINAL canónica del arnés visual C10

- **Resumen: 33 marcas · 0 FAIL · 1 BLOQUEADO · exit 1** (ts de siembra
  20261104165000). Canónico: el único BLOQUEADO es E9.7 variante B (canon
  H-C7/H-C9-E9.7 — emulación de storage bloqueado no resultó; cubierto por
  la parte A), declarado honesto (R9.6.4). **CERO hallazgos de producto
  nuevos**: los E10.x del módulo C8b/C8c/CORS y los re-anclajes del parche 1
  (E10.1/2/3/5) y el parche 2 (E10.6/7 NBSP) y los re-parches del bucle
  (E10.8 orden modal-primero; E10.14 shape `{items,next_cursor}`; E10.9
  re-ancado a vuelta+banner+query con la fila stale NOTADA) pasan todos
  PASS-CON-NOTA con su evidencia.
- Nº de re-corridas totales del arnés C10 en esta instancia: 8 (corrida 1
  inicial + 6 del bucle T10.33 + corrida final canónica). Corridas
  SERIALIZADAS en su tramo final (lección del exit 2 espurio por overlap).
- **S16 sobre el qa-log RAW COMPLETO**: `grep -cE "plk_|eyJ"` → **0 hits**.
- **Evidencia**: 39 PNGs en `.atl/sdd/C10/evidencia/` (3,3 MB), inventario
  sha256 en `MANIFEST-sha256.txt` del mismo dir (NO en git).

### T10.34 — regresiones en orden y serial (smokes SIN editar)

| smoke | canon (docs de su corte) | OBTENIDO | causa de lo divergente |
|---|---|---|---|
| `qa_c3_smoke.sh` | post-C8: 23 PASS · 1 FAIL E3.22 · exit 1 (E3.22 AJENO-CLASIFICADO N/A-post-D2) | **22 PASS · 2 FAIL (E3.22 + E3.24) · exit 1** | E3.22 = la N/A-post-D2 ya ratificada (mockups 04/05 borrados por D2; pauta «leer E3.22 como N/A-post-D2» documentada en C8 qa-report §4). **E3.24 NUEVO**: `git diff --exit-code` sobre INTOCABLE de C8 `backend/…/CorsConfig.java` != 0 — modificado por el fix CORS de producto RATIFICADO de este corte D-C10-15 (sin commit, pendiente del orquestador): la pre-fase de C8 no puede verlo verde hasta que el fix se comitee. NO es regresión de comportamiento; los 22 checks de endpoints: PASS. |
| `qa_c7_smoke.sh` | su corte: 9/9 PASS · exit 0; post-C8 (D2): 9/9 PASS · exit 2 (pre-fase doble BLOQUEADO+PASS) | **9/9 PASS (E7.14 con nota R-7.k canon) · exit 2** — pre-fase: `/ws-console.html` no publicado (C8-D2) | idéntico a la regresión documentada en C8 qa-report §4.5; N/A-post-D2, sin regresión real. |
| `qa_c8_smoke.sh` | su corte: 23 PASS · 0 FAIL · 1 BLOQUEADO (Q23) · exit 0 | **17 PASS · 6 FAIL · 1 BLOQUEADO (Q23) · exit 1** | 6 FAIL = **premisas de C8 invalidadas por diseño C10 RATIFICADO** (ninguna es un defecto de producto ni del smoke en sí): (1) E8.38/E8.47 — el smoke ejecuta los destructivos esperando 401 (H-C8-B2, escrito cuando los DELETE de C10 NO existían) y ahora son REALES: 204 logs / 204 8a / 204 8c, proyecto + log ya NO sobreviven → premisa muerta; (2) E8.14/E8.15 — sub-aserto DELETE /projects/99999999 esperaba 405/401/404 y hoy es 400 (ruta mapeada, confirm falla por defecto → 400 confirm_mismatch, diseño C10); (3) E8.39 — «DTO SIN summary aunque viaje ?include=summary»: C10 AÑADIÓ summary (REQ-C10-RESUMEN, validado VERDE por E10.1/E10.2 del arnés); (4) E8.27 — COLATERAL: la key de la siembra murió con la fila que E8.38 borró de verdad (coherente con E10.14: key en columnas de projects) → ingest 401 + frames 0. El 5 PASS del mismo smoke y todo lo no-tocado-por-C10 sigue VERDE. |

- **Nota para el dueño (pauta futura, espejo de la E3.22)**: hasta decisión
  del orquestador, las re-corridas de `qa_c8_smoke.sh` deben leer sus
  negativos C10 (E8.14/15 DELETE, E8.38, E8.47, E8.27-colateral) y E8.39
  como **N/A-post-C10** (superadas por el comportamiento real del corte C10,
  validado verde por el arnés E10.x). Los smokes quedan INTOCABLES y SIN
  editar (frontera de la instancia).
- **down -v observado**: SOLO la pre-fase Q13 autocontenida de `qa_c8_smoke.sh`
  hizo `compose down -v || up -d --build` (volumen `project_log_pgdata`
  recreado); C3 y C7 usaron SOLO `up -d --build --force-recreate` (pgdata
  intacto en esas dos). Consecuencia: el residuo histórico
  `smoke-c8-20261029171145-proyecto` (id 1) desapareció con el volumen.

### Estado final al cerrar el bloque 6

- **Pila**: 3/3 containers healthy (`postgres`, `backend`, `frontend`),
  `GET :8080/` → 200, flyway_schema_history = **1** (canon E3.24).
- **BD**: tras el `down -v` de C8 queda SIN proyectos y SIN logs
  (proys=0, logs=0, users=1 — solo el admin de siembra); el arnés C10
  hizo su fase L limpiando su propia siembra, y los destructivos que el
  smoke C8 ejecutó de verdad (E8.38) fueron sobre SUS datos de siembra.
  Residuo del residuo-expected (`smoke-c8-…-proyecto` id 1) purgado por
  el down -v: limpio.
- Los repros efímeros de /tmp con autolimpieza REST terminaron sin
  residuo; credenciales jamá impresas (S16: 0 hits en los 4 logs).
