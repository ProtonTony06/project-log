---
change: "C5"
artifact: qa-plan
last_updated: 2026-10-25
status: draft
---

# QA Plan — C5 · Ingesta REST

> Plan de pruebas del corte C5 (fase qa, ANTES del test-writer). Insumos
> ÚNICOS de diseño: `.atl/sdd/C5/spec.md` (R5.1–R5.19, S5-1…S5-15, D-C5-1,
> escenarios E5.1–E5.34) y `.atl/sdd/C5/design.md` (D-1…D-15, test files
> §13). AISLAMIENTO: ni el qa-plan ni los tests que nazca de él leen diffs,
> `apply-progress.md`, código de la feature ni `scripts/qa_c5_smoke.sh` —
> un fallo de test es señal de bug de implementación, NO del test (salvo
> defecto del propio plan). Patrones de entorno heredado se citan del spec
> (R5.17) y del design (§13), nunca de memoria de otra corte.
>
> Convenciones: **[M]** = aserción obligatoria; **[O]** = opcional
> (mejora, no gate). Una aserción HTTP aislada sin su espejo psql (o
> viceversa) es INSUFICIENTE en los casos marcados como pareja (R5.18).
> Tipos: **[L]** unit / **[L MVC]** @WebMvcTest / **[L pin]** SQL-pin /
> **[L higiene]** ListAppender / **[S]** smoke en pila.

## 0. Resumen del plan

- **38 casos QP**: 34 con E5.x 1:1 (E5.1–E5.34, incluido E5.24-c "sin
  nombre" del spec §2.2 y el E5.16-b) + **3 extras de hueco** (QP-EX-1…3).
- **21 [L]** (3 MVC + 1 higiene + 3 unit + 2 pin, según ficheros §13 del
  design) y **13 [S]** (E5.21–E5.33 en `scripts/qa_c5_smoke.sh`).
- **10 parejas mock↔psql OBLIGATORIAS** (R5.18) — sección 3.
- Aserciones **[M] de DB-state psql** añadidas por este plan: normalización
  UTC del `timestamp` (comparación input↔fila), `received_at` común e
  igual-entre-sí (regla 9, igualdad EXACTA sobre los ids del batch),
  `correlation_id`/`metadata`/`exception NULL` por fila, conteos RELATIVOS,
  ids consecutivos (orden de VALUES), all-or-nothing delta=0, y la
  frontera 1048576/1048577 del 413.
- NO cubierto (sección 5): streaming real (C7), consulta (C6), front (C8),
  Testcontainers, nginx, DIVE, 429, S-3, ObjectProvider, D-15.

## 1. Tabla por-caso (esqueleto E5.x + aserciones como valor añadido)

| QP | Escenario | Qué se prueba | Tipo | Fichero de test (design §13) | Fixtures |
|---|---|---|---|---|---|
| QP-1 | E5.1 | 202 individual con cuerpo EXACTO `{"accepted":1}` (sin espacios, sin más campos) | [L MVC] | `ingest/IngestControllerMvcTest.java` | key mock válida, principal ACTIVE con project_id=1; payload válido mínimo (`level/timestamp/message/environment`) |
| QP-2 | E5.2 | 202 batch `{"accepted":N}` con N ≥ 2 (Map de 1 clave + `valueToTree`) | [L MVC] | idem QP-1 | mismo + array `logs` de N≥2 registros válidos |
| QP-3 | E5.3 | Las 4 variantes de `environment` (ausente, `""`, no textual, fuera del enum `"qa"`) → 400 `environment_required` + `field:"environment"` + message EXACTO R5.10; **sin `index` (individual)** | [L MVC] | idem QP-1 | payload con environment mutado por variante |
| QP-4 | E5.4 | `timestamp` naive (`2026-10-16T22:31:04`) y no textual → 400 `timestamp_without_timezone` + `field:"timestamp"` | [L MVC] | idem QP-1 | idem QP-3 |
| QP-5 | E5.5 | `level` fuera de enum: `"error"` minúscula (sin normalización), `"TRACE "` (con espacio — fuera del Set EXACTO del literal), ausente → 400 `level_invalid` + `field:"level"` | [L MVC] | idem QP-1 | idem QP-3 |
| QP-6 | E5.6 | `message` ausente / `""` / `"   "` → 400 `message_required` + `field:"message"` | [L MVC] | idem QP-1 | idem QP-3 |
| QP-7 | E5.7 | Frontera de BYTES UTF-8 de `message`: 32768 bytes (con multibyte: bytes≠chars) → **202**; 32769 → 400 `field_too_long` `field:"message"`, mensaje compuesto "…32768 bytes" | [L MVC] | idem QP-1 | generador de message exacto en bytes (relleno multibyte) |
| QP-8 | E5.8 | `metadata` array y escalar → 400 `metadata_not_object` `field:"metadata"`; `{}` → **202** | [L MVC] | idem QP-1 | idem QP-3 |
| QP-9 | E5.9 | Fronteras superiores +1/legal de opcionales: exception 131073→400 y 131072→202; metadata serializada 65537→400 y 65536→202; source 201→400 y 200→202 (CARACTERES); correlation_id 201→400 y 200→202 | [L MVC] | idem QP-1 | generador por campo; medir en la MISMA convención del spec (S5-8) |
| QP-10 | E5.10 | `{"logs":[]}`, `logs` ausente, `logs` no-array → 400 `batch_empty` `field:"logs"`; 201 registros → 400 `batch_too_large` `field:"logs"` + mensaje compuesto "…200 registros" | [L MVC] | idem QP-1 | arrays vacíos/201 mínimos |
| QP-11 | E5.11 | Batch de 5 con registro 2 inválido en `level` y el 4 en `message` → **400 único del índice 2** (`index:2, field:"level"`, primer-fallo-gana R-9); el MISMO fallo en individual → **SIN `index`** | [L MVC] | idem QP-1 | batch manual de 5; aserto del orden de claves `code,message,field,index` |
| QP-12 | E5.12 | Cadena 1: sin `X-API-Key`, header basura no-`plk`, key bien-formada hash sin-match → **401 con el MISMO cuerpo** (anti-oráculo); `verifyNoInteractions` de `IngestService`/`LogsRepository` | [L MVC] | idem QP-1 | `ApiKeyService` y `ProjectsRepository` `@MockitoBean` |
| QP-13 | E5.13 | Principal `status:"ARCHIVED"` + cuerpo INVÁLIDO → **403** (gate 3 antes de validar); cuerpo `{code, message}` SIN `field`; el repo de logs NUNCA se llama | [L MVC] | idem QP-1 | principal ARCHIVED por `SecurityContext`/mock del lookup |
| QP-14 | E5.14 | Orden fail-fast de campos (R5.24): registro con TODO mal → 400 siempre de `environment`; solo `timestamp`+`level` mal → 400 de `timestamp` | [L MVC] | idem QP-1 | payloads deliberadamente multi-malos |
| QP-15 | E5.15 | Pin de literales SQL: los 3 constants del INSERT + `SELECT_POR_API_KEY_HASH = "SELECT "+COLS+" FROM projects WHERE api_key_hash = ?"` | [L pin] | `ingest/LogsRepoSqlTest.java` + `projects/repository/ProjectsIngestSqlTest.java` (D-9) | ninguno (JUnit puro) |
| QP-16 | E5.16 | JWT válido en `Authorization: Bearer` SIN `X-API-Key` → 401 `api_key_invalid` (NUNCA `invalid_token`); la respuesta de ingesta jamás contiene el literal del panel | [L MVC] | idem QP-1 | JWT válido de test (cadena 2 NO en el slice) |
| QP-16b | E5.16-b | `panelChain`/`defaultChain` byte-a-byte: RE-CORRIDA de `PanelAuthMvcTest` + suite C1–C4 (77/77) — SIN test nuevo | [L] (re-corrida) | `auth/PanelAuthMvcTest.java` (EXISTENTE, intocado) | ninguna |
| QP-17 | E5.17 | Higiene: durante un 202 y un 401, NINGÚN evento log de `com.projectlog.ingest`/`com.projectlog.keys` contiene la key, un fragmento, el header `X-API-Key` ni el hash | [L higiene] | `ingest/IngestLogHygieneTest.java` | ListAppender a DEBUG sobre logger `com.projectlog`; key real de test |
| QP-18 | E5.18 | Unit del parse (D-11): `…Z`, `…+02:00`, `…123+05:30` parsean; naive lanza `DateTimeParseException` → 400 `timestamp_without_timezone` | [L] | `ingest/IngestServiceTest.java` | cadenas literal del spec |
| QP-19 | E5.19 | `received_at` fijado UNA vez (S5-7): UNA invocación de `insert`, UN único `OffsetDateTime` común a TODAS las filas vía captor | [L] | idem QP-18 | repo mock; batch de N≥3 |
| QP-20 | E5.20 | Seam (S5-10): (a) batch → `onLotePersistido(projectId, receivedAt, ids)` UNA vez con ids en ORDEN de VALUES; (b) individual → lista de 1; (c) 403/400/parse/rollback → `verifyNoInteractions` | [L] | idem QP-18 | `List<IngestListener>` con mock; casos negativos incl. insert que lanza |
| QP-21 | E5.21 | Happy individual EN PILA (la pareja [S] de QP-1/QP-15): 202 + `{"accepted":1}` + fila psql (§2 below) | [S] | `scripts/qa_c5_smoke.sh` | proyecto `smoke-c5-<TS>-a` + key regenerada (tmpdir 700) |
| QP-22 | E5.22 | Batch de 200 EN PILA (pareja de QP-2): delta=200, `count(DISTINCT received_at)=1` sobre los 200 nuevos, ids crecen en orden de VALUES (correlación con `correlation_id` vía ROW_NUMBER) | [S] | idem QP-21 | generador de 200 (`e522-<nnn>`, level/env rotando) |
| QP-23 | E5.23 | All-or-nothing (pareja de QP-11): batch con índice 2 inválido (`level:"XX"`) → 400 `level_invalid`+`field:"level"`+`index:2` **Y psql delta = 0 (NADA persiste)** | [S] | idem QP-21 | idem QP-21 |
| QP-24 | E5.24 | 401 EN PILA, 3 variantes: (a) sin header, (b) `plk_basura_no_existente`, (c) REVOCAR (DELETE→204; 3 columnas NULL en psql, regla 11) → ingestar la misma → 401; mismo cuerpo en las 3 (needle `api_key_invalid`, jamás `invalid_token`); luego `regenerate`→201 key2 | [S] | idem QP-21 | JWT en tmpdir; DELETE de api-key |
| QP-25 | E5.25 | 403 EN PILA (pareja de QP-13): proy ACTIVE + key2 → PATCH ARCHIVED (200) → ingestar → 403 `{code,message}`; psql delta=0 | [S] | idem QP-21 | PATCH status (C3) |
| QP-26 | E5.26 | 400 reales EN PILA (pareja de QP-3/QP-4): `environment` ausente → 400 `environment_required`; `timestamp` naive → 400 `timestamp_without_timezone`; psql delta=0 en ambos | [S] | idem QP-21 | proyecto ACTIVE, key2 |
| QP-27 | E5.27 | 413 frontera Content-Length (pareja de QP-10 por barrera): 1048576 EXACTOS `--data-binary` → **202** (límite INCLUSIVO); 1048577 → 413 `payload_too_large` (cuerpo exacto, delta=0) | [S] | idem QP-21 | padding JSON legal; plan B R-12 si curl manifiesta reset (sección 4, [O]) |
| QP-28 | E5.28 | 413 chunked: mismo payload >1 MiB SIN `Content-Length` (`Transfer-Encoding: chunked`) → 413 (2.ª barrera — la 1.ª no dispara) | [S] | idem QP-21 | idem QP-27 |
| QP-29 | E5.29 | Confianza timestamp: ingest con `+02:00` → 202; psql `timestamp` == instant UTC esperado (`= '<utc>'::timestamptz`) y `received_at` ≥ T1 del request | [S] | idem QP-21 | T1 capturado antes del curl |
| QP-30 | E5.30 | JSONB + fronteras bytes EN PILA (pareja de QP-7/QP-8): `metadata->>'span'='checkout'`; metadata array → 400; 65536/65537 → 202/400; message 32768/32769 → 202/400 | [S] | idem QP-21 | idem QP-21 |
| QP-31 | E5.31 | **G-2 (cierre ACTIVO)**: proyecto aún ARCHIVED (de QP-25) → PATCH ACTIVE → ingesta con la MISMA key2 (NUNCA re-generada) → **202** (reglas 11+12 juntas) | [S] | idem QP-21 | el archive de C5 citará E5.31 |
| QP-32 | E5.32 | Fronteras de lote EN PILA (pareja de QP-10): `{"logs":[]}` → 400 `batch_empty`; 201 → 400 `batch_too_large`; psql delta=0 | [S] | idem QP-21 | generador de 201 |
| QP-33 | E5.33 | Orden de gates literal en pila: >1 MiB + key inválida → **413 (NO 401)**; ≤1 MiB + key inválida → 401 | [S] | idem QP-21 | idem QP-28 |
| QP-34 | E5.24-c | Cuerpo ILEGIBLE (S5-1): "no-json" individual → 400 `environment_required`; batch → 400 `batch_empty`; Content-Type `text/plain` → el MISMO 400 (no 415) | [L MVC] | idem QP-1 | cuerpos crudos con Content-Type forzado |
| QP-35 | E5.34 | Bind R-6 (S5-9, OBLIGATORIO): ``"1MiB"``→1048576; `"1KB"`/`"1KiB"`→1024; `"1048576"`→1048576; `"1MB"`→1048576 (binaria de Spring); `"abc"`→excepción (fail-fast); default del `@Value` EXACTAMENTE `"1MiB"` | [L] | `ingest/IngestGuardLimitTest.java` | ninguno (unit sin Spring) |
| QP-EX-1 | EXTRA (hueco S5-2) | Campos de CONFIANZA falsificados por el cliente (`project_id`, `received_at`, `id` en el body) IGNORADOS: `insert` capturado con el `projectId` del principal y `receivedAt` server-side | [L] (+[O] psql) | idem QP-18 | payload con los 3 falsificados |
| QP-EX-2 | EXTRA (D-R2/D-8) | `shouldNotFilter`: el guard NO aplica fuera de `/api/v1/ingest/**` (p. ej. cuerpo grande a panel no recibe 413 del guard) | [L MVC] [O] | idem QP-1 | ruta ajena al slice |
| QP-EX-3 | EXTRA (R5.24-a) | Opcionales NO normalizados: `exception:""`/`source:""`/`correlation_id:""` presentes → VÁLIDOS, se capturan como `""` (no vacío→NULL, no trim) | [L] [O] | idem QP-18 | payload con opcionales vacíos |

Notas de la tabla: E5.24-c no lleva número propio en el spec ("sin
nombre", §2.2) y E5.16-b se cubre por RE-CORRIDA (R5.19b del design —
pin de cadenas 2/3 SIN test nuevo de C4 reescrito). Los QP-EX son huecos
detectados del spec, no escenarios nuevos del corte: QP-EX-1 es hueco real
(S5-2 exige que el falsificado JAMÁS llegue a la BD y ningún E5.x lo pincha
en [L] para el unit), QP-EX-2/3 son [O] (certificación adicional: D-R2 la
mitiga por diseño, R5.24-a la cubre parcialmente en QP-9).

## 2. Aserciones OBLIGATORIAS [M] por caso

### 2.1 DB-state psql para los [S] (patrones RELATIVOS del entorno heredado)

**Conteo base SIEMPRE** (R5.17): `psql -qAt` con `count(*)` de `logs` ANTES
del escenario (o del bloque) y comparación de delta — JAMÁS conteo absoluto
contra una cifra codificada (el volumen `pgdata` conserva logs heredados de
C3/C4). Igual para `projects`.

- **QP-21/E5.21** tras el 202 individual — [M] en psql:
  - delta `count(*)` de `logs` = **1**; y la fila nueva identificada por
    `id = max(id) WHERE project_id = <id>` (o `id > máximo previo`
    capturado antes del escenario).
  - `project_id` = el id del proyecto capturado (JAMÁS el que un body
    falsificado dijera).
  - `level = 'ERROR'`, `environment = 'production'`, `correlation_id =
    'e521'`, `source = 'smoke'` — EXACTOS (igualdad con `=`, needles de
    literal con `\_`/`position()` si llevaran underscore… aquí no llevan).
  - `metadata` es JSONB válido: `metadata->>'span' = 'checkout'` (E5.30 lo
    repite con fronteras).
  - `exception IS NULL` (no vino) y `message = 'E5.21'`.
  - `timestamp` de la fila comparado contra el INSTANT del input: psql
    `timestamp::timestamptz = '<input>'::timestamptz` — [M] comparar el
    timestamp del INPUT vs el de la FILA (igualdad de instants en psql, no
    de Strings — el input trae offset y la fila está en UTC).
  - `received_at IS NOT NULL` — común a todo el conjunto heredado del
    lote; y `received_at` ≥ el T1 previo al request (impuesto por el
    servidor, QP-29).
- **QP-22/E5.22** tras el 202 batch de 200 — [M]:
  - delta `count(*)` = **200** (relativo a la cuenta previa del bloque).
  - `count(DISTINCT received_at) = 1` **SOBRE los 200 ids nuevos** (`WHERE
    id BETWEEN` los ids del lote o `id > máximo previo`): TODAS las filas
    del batch con `received_at` IGUAL ENTRE SÍ — igualdad EXACTA de regla 9
    (D-R4); aserto adicional: `received_at IS NOT NULL` en las 200.
  - ids consecutivos/crecientes en ORDEN de VALUES: JOIN por
    `correlation_id` (`e522-001`…`e522-200`) con ROW_NUMBER sobre `id ASC`
    — aserto de correlación id ASC ↔ correlation_id ASC.
  - level/env rotando EXACTOS por fila (muestreo de primera/última).
- **QP-23/E5.23 — all-or-nothing [M]**: tras el 400, psql delta `count(*)`
  = **0** (¡cero filas nuevas por el batch inválido — regla 6!). La pareja
  del 400 HTTP + delta=0 es INSUFICIENTE si va aislada de cualquiera de sus
  mitades: el 400 solo demuestra la validación; el delta=0 demuestra que
  NADA se persistió a medias. [O]: repetir el delta tras la variante
  `metadata` array.
- **QP-24/E5.24**: revocar → psql `api_key_hash IS NULL AND api_key_prefix
  IS NULL AND api_key_generated_at IS NULL` (regla 11, tres NULL needle
  por igualdad — S-3 heredado para el literal `plk_` con underscore);
  posteriormente delta=0 en las 3 variantes de 401.
- **QP-25/E5.25** tras el 403: delta=0 [M]. **QP-26**: delta=0 en ambos
  400 [M]. **QP-27**: tras el 413, delta=0 [M] (y tras el 202 legal, +1).
  **QP-32**: delta=0 en ambas fronteras del lote [M].
- **QP-29/E5.29 [M]**: con input `2026-10-25T21:00:00+02:00` (por ejemplo),
  psql: `EXTRACT(EPOCH FROM timestamp) = <epoch UTC esperado>` (o `timestamp
  = '<utc>'::timestamptz`) — normalización a UTC por PG (TRD §2.6); y
  `received_at >= '<T1>'::timestamptz` — server-side, no del input (el
  `received_at` JAMÁS puede ser el del body si el client lo falsificó,
  QP-EX-1).

### 2.2 Literales HTTP EXACTOS

- **202** [M]: cuerpo EXACTO, comparado como String sin espacios:
  `{"accepted":1}` (individual), `{"accepted":<N>}` (batch) — SIN espacios,
  sin `ids`, sin `received_at`, sin otro campo (`jsonPath` de igualdad
  cruda o `contentAsString().equals`); status exactamente 202 (no 200 —
  decisión cerrada del Schema §3.4). En todos los QP-1/2/7/8/9 (rutas
  legales), QP-21/22/27/29/30/31.
- **401** [M] (QP-12, QP-16, QP-24, QP-33): status 401; body
  `{code: "api_key_invalid", message: "API key de ingesta ausente o
  inválida"}` — message EXACTO (needle literal), **sin `field`**, sin
  varianza por causa (sin header = malformado = sin-match = revocada →
  EL MISMO body, un ÚNICO cuerpo en los tests); y JAMÁS el literal
  `invalid_token` en la respuesta de ingesta.
- **403** [M] (QP-13, QP-25): status 403; body `{code:
  "project_archived", message: "El proyecto está archivado: no acepta
  logs"}` — message EXACTO y DISTINTO del literal del panel (message del panel:
  "…desarchívalo antes de generar o regenerar su API key"), **sin
  `field`**; JAMÁS 403 HTML (Content-Type JSON).
- **413** [M] (QP-27, QP-28, QP-33): status 413; body `{code:
  "payload_too_large", message: "La petición supera el límite de 1 MiB"}` —
  literal FIJO ("1 MiB" no compone el valor configurado — si el yml
  cambiara, el body NO cambiaría; aserto de literal sin field).
- **400 por code** [M] (QP-3…QP-10, QP-11, QP-14, QP-26, QP-32, QP-34):
  status 400 y el envelope con `code`, `message` EXACTO, `field` EXACTO
  (y `index` en batch). Tabla de parejas obligatorias:
  - `environment_required` / `environment` / "El campo environment es
    obligatorio (development, staging o production)" — sin index en
    individual.
  - `timestamp_without_timezone` / `timestamp` / "El campo timestamp debe
    ser ISO-8601 con zona horaria".
  - `level_invalid` / `level` / "El campo level no es un nivel válido
    (TRACE, DEBUG, INFO, WARN, ERROR o FATAL)".
  - `message_required` / `message` / "El campo message es obligatorio".
  - `field_too_long` / `<field>` con message compuesto EXACTO: "El campo
    message no puede superar 32768 bytes"; exception "…131072 bytes";
    metadata "…65536 bytes"; source "…200 caracteres";
    correlation_id "…200 caracteres" (BYTES vs CARACTERES en el message
    — R5.10/S5-8).
  - `metadata_not_object` / `metadata` / "El campo metadata debe ser un
    objeto JSON".
  - `batch_empty` / `logs` / "El campo logs debe contener al menos 1
    registro".
  - `batch_too_large` / `logs` / "El campo logs no puede contener más de
    200 registros" (compuesto con el límite EFECTIVO de la propiedad).
- **`index` 0-based SOLO en batch** [M] (S5-3): en QP-11, el body lleva
  `index: 2` ENTERO en su posición TRAS `field` (orden de claves
  `code,message,field,index` del LinkedHashMap); en QP-3/QP-4/QP-5/QP-6/
  QP-7/QP-8/QP-9 (individual) el mismo fallo JAMÁS lleva `index` (ni con
  valor 0). [M] en los DOS sentidos (presencia en batch, AUSENCIA en
  individual).
- **Orden de gates 413→401→403→400** [M] en QP-33 (y QP-13 para el
  403→400): 413 con key inválida (gate 1 antecede al 2); y con cuerpo
  INVÁLIDO + proyecto ARCHIVED → 403 (gate 3 antecede a la validación,
  R5.8 verbatim). Pareja del [S] con el [L]: QP-14↔QP-33.
- **Orden de claves JSON de la 202** [O]: `{"accepted":N}` no tiene
  orden observable (1 clave); el [M] es la igualdad exacta del String.

### 2.3 Unit del primitivo/lookup (pareja del B1)

- **QP-12 (mock)** [M]: `apiKeyService.sha256(header)` invocado con el
  String COMPLETO del header (ni trim de `plk_`, ni hex, ni salt —
  contrato C4 spec §0.2) — `verify(keys).sha256("plk_…")`; y
  `projectsRepository.findByApiKeyHash(digest)` invocado con el `byte[]`
  del sha256 (captor) — B1 exacto. La pareja INSUFICIENTE aislada: mockear
  el repo sin verificar el hash pasado (un stub que matchee cualquier cosa
  NO certifica el B1).
- **QP-24 (mock↔pila)** [M]: revocada (hash NULL excluida por el WHERE)
  y no-match → sin contexto → 401 (E5.12-c la variante [L]; E5.24-c la
  real con los tres NULL en psql).
- **QP-17 (higiene)** [M]: ListAppender sobre el logger `com.projectlog`
  a DEBUG durante un 202 y un 401: NINGÚN evento (message ni throwable)
  contiene la key de test, NINGÚN fragmento de ella, el literal
  `X-API-Key`, ni el hash (hex del digest). Cualquier needle que el
  test-writer ponga DEBE aparecer antes en la key de test, o el aserto es
  vacuoso.

### 2.4 Unit de servicio / seam / bind

- **QP-19** [M]: una sola invocación al `insert`; un único
  `OffsetDateTime` (captor) recibido por TODAS las filas; el mismo valor
  recibido por el seam (QP-20a). Pareja INSUFICIENTE aislada: verificar
  "una vez" sin capturar el valor (la regla 9 pide IGUALDAD de valor).
- **QP-20** [M]: (a) batch → `onLotePersistido(projectId, receivedAt, ids)`
  UNA vez, `ids` = la lista del mock EN ORDEN de VALUES; (b) individual →
  lista de tamaño 1; (c) ante 400 de cualquier campo, 403 (en controller —
  cubierto en MVC), parse, o `insert` que LANZA → `verifyNoInteractions`
  del listener [M] (la emisión queda estructuralmente tras el COMMIT).
- **QP-35 / E5.34** [M] (OBLIGATORIO, cierre S5-9): `"1MiB"` → 1048576
  (la evidencia local: `DataSize.parse("1MiB")` FALLA — el normalizador
  quita la `i`); `"1KiB"`/`"1KB"` → 1024; `"1048576"` → 1048576 (bytes
  puros); `"1MB"` → 1048576 (equivalencia BINARIA de Spring, 1024-base);
  `"abc"` → excepción (fail-fast de arranque); y el default del `@Value`
  del filtro es EXACTAMENTE `"1MiB"` (assert vía reflection sobre la
  anotación o pin del literal — sin duplicación frágil). [O]: `"1GiB"`→
  1073741824.

### 2.5 Higiene del pin SQL

- **QP-15 / E5.15** [M]: pin EXACTO (igualdad de String, patrón
  `ProjectsKeySqlTest`) de:
  - `INSERT_LOGS_CABECERA` = `INSERT INTO logs (project_id, level,
    environment, timestamp, received_at, message, exception, metadata,
    source, correlation_id) VALUES ` (espacio final del literal).
  - `INSERT_LOGS_FILA` = `(?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)`
    — el `CAST` en la 8.ª posición ES el pin (R-4 — un String→jsonb sin
    cast falla).
  - `INSERT_LOGS_RETORNO` = ` RETURNING id` (espacio inicial).
  - `SELECT_POR_API_KEY_HASH` = `"SELECT " + COLS + " FROM projects WHERE
    api_key_hash = ?"` y el Javadoc del método CITA la D-C5-1 (aserto
    string-contains del Javadoc o review — [O] el assert de Javadoc, [M]
    el pin del SQL).
  - [O]: construcción del SQL para un batch de N (CABECERA + N×FILA con
    `", "` + RETORNO) en el repo real con N=2/3, verificación de forma
    del String.
  - Ficheros: `LogsRepoSqlTest.java` (INSERT) y
    `ProjectsIngestSqlTest.java` (SELECT, junta a `ProjectsKeySqlTest`)
    — D-9.

## 3. Parejas mock↔psql OBLIGATORIAS (R5.18)

Todo code/field/index literal certificado en [L] TIENE su paso [S] contra
la pila (Done 4 del 07) — en la nomenclatura QP:

| Pareja | [L] (mock) | [S] (pila) | Qué comparten |
|---|---|---|---|
| 1 | QP-1 | QP-21 | 202 `{"accepted":1}` + fila individual |
| 2 | QP-2 | QP-22 | 202 batch + regla 9 (received_at común) + order ids |
| 3 | QP-11 | QP-23 | index 0-based + **all-or-nothing** (delta psql = 0) |
| 4 | QP-12 | QP-24 | 401 `api_key_invalid` un único body (3 causas) |
| 5 | QP-13 | QP-25 | 403 `project_archived` antes de validar, delta=0 |
| 6 | QP-3 (y QP-4) | QP-26 | 400 de campo con field, delta=0 |
| 7 | QP-7 | QP-30 | frontera bytes UTF-8 message 32768/32769 (y metadata) |
| 8 | QP-10 | QP-32 | fronteras del lote (batch_empty / batch_too_large) |
| 9 | QP-14 | QP-33 | orden de gates literal 413→401 |
| 10 | QP-15 | QP-21/22 | pins SQL ↔ los estamentos que de verdad corren |
| 11 | QP-16 / QP-16b | pre-fase (sondeo `/me` 401 `invalid_token`) + re-corrida 77/77 | no-contaminación del panel |

Las demás aserciones [M] del §2 viven SOLO en [L] o SOLO en el [S] (sin
duplicar: p. ej. E5.33 en pila y E5.14 en MVC prueban órdenes distintos
— campos vs componentes — por diseño del spec).

## 4. Riesgos de test (qué escribiría MAL un test-writer que copia de memoria C3/C4)

1. **Heredar el 401 del PANEL**: en rutas de INGESTA el code es
   `api_key_invalid` con su message propio — escribir `invalid_token`
   (o el Javadoc/needle heredado de C2/C4) es ERROR: `RestAuthentication
   EntryPoint` (panel) queda INTACTO y la cadena 1 tiene entry point
   PROPIO (R5.5/R5.6). El 401 de ingesta jamás lleva `field`.
2. **Confusión `index`/`field` entre endpoints (cierre S5-3)**: el 400 de
   un registro de lote lleva `field` Y `index` (0-based, entero, tras
   `field` en el LinkedHashMap); el 400 del endpoint INDIVIDUAL lleva
   `field` pero JAMÁS `index` (incluso cuando "valdría 0"). Un test-writer
   que copie de C4 escribirá `index:0` en individual — ROJO.
3. **El 413 NO es `INGEST_MAX_REQUEST_BYTES` como code**: el code es
   `payload_too_large` (la propiedad se llama max-request-bytes, el CODE
   del body es otro literal — R5.10); tampoco lleva `field`, y su message
   es FIJO ("…1 MiB"), no compone el valor configurado.
4. **Olvidar el conteo RELATIVO**: codificar `count(*) = 201` absoluto
   romperá con el volumen `pgdata` heredado de C3/C4 — SIEMPRE delta
   antes/después (R-15), y con `psql -qAt`.
5. **El [O] del drenado (R-12/§2.6 del spec)**: NO es [M]. El plan B
   (drenar ACOTADO hasta límite+1 antes de responder) solo se ACTIVA si
   E5.27/QP-27 manifiesta "connection reset" en curl; un test-writer que
   exija drenado de antemano escribiría un test que falla sobre la
   implementación CORRECTA de la barrera 1 (escribir 413 SIN drenar).
6. **Comparar `timestamp` como String**: el input lleva offset y la
   fila está normalizada a UTC por PG — comparar Strings daría FALSO
   NEGATIVO/POSITIVO; comparar instants (`::timestamptz`/epoch) [M].
7. **Espacios en las 202**: `{"accepted": 1}` (con espacio) es FALLO del
   aserto [M] de igualdad exacta — la emisión `Map.of` + `valueToTree`
   va sin espacios (R5.10-a).
8. **Normalización case en enums** (S5-4/S5-6): `"error"` y `"TRACE "` son
   400 — un test que espere 202 por "corrigen" el case es ERROR (el CHECK
   de V1 solo acepta el literal exacto).
9. **Igualdad de `received_at` FUERA de su batch**: la regla 9 exige
   igualdad DENTRO de un lote — batches distintos en la misma ejecución
   tienen `received_at` DISTINTOS (test que pida igualdad cross-batch es
   ERROR); la igualdad se aserta SOBRE los ids del lote (delta window).
10. **Booleanos psql**: `t/f` en psql ≠ `true/false` en JSON (R5.17) —
    needles de estado o has_key deben seguir la convención de la fuente
    que las lee.
11. **Cuerpos ilegibles** (QP-34): el 400 de un body no-JSON NO es un code
    nuevo (`body_invalid` NO existe) — es el primer requerido del endpoint
    (individual `environment_required`, batch `batch_empty`), y un
    Content-Type `text/plain` responde el MISMO 400 (JAMÁS 415).

## 5. NO cubierto (scope explícito para sdd-verify)

- **Streaming real (C7)**: solo el seam `IngestListener` con lista VACÍA
  (Punto 5 del gate); sin frames, sin handler de `/ws/logs`, sin
  LiveBroadcaster; el QP-20 no certifica ningún suscriptor real.
- **Consulta/logs (C6)**: ni `GET /panel/logs` ni LogDto ni cursor; la
  lectura de logs NO se anticipa (el repo solo INSERT + RETURNING).
- **Frontend (C8)**: los errores de ingesta no tienen UI (AppFlow flujo
  10) — QP no planea nada de front.
- **Testcontainers** (Punto 4 gate: NO): el patrón MVC-mocks + smoke pila
  real certifica bytea/JSONB/TIMESTAMPTZ/all-or-nothing.
- **nginx `client_max_body_size`**: el 413 se certifica en la puerta 8080
  (ruta directa al backend codificada en el smoke) — la puerta nginx (y su
  propio corte del 413) es deuda documentada (C9), NO un escenario QP.
- **DIVE de logs**: sin captura (R5.12-b) — un failure real de INSERT es
  500 honesto; en `logs` no hay UNIQUE que la dispare (los tests NO deben
  intentar provocarla).
- **429 / rate limiting**: fuera de alcance (ADR-26) — ningún escenario
  de ráfaga.
- **G-1** (QP-30 de C4): CERRADO, NO reabrible — ningún QP lo toca
  (`regenerateKey` intacto; el Javadoc lo cita).
- **Like-underscore heredada (S-3 de C4)**: deuda de patrón de needles
  psql (`\_`/`position()` contra literales `plk_*`), no de código — se
  aplica como convención, sin escenario.
- **`ObjectProvider` lazy (D-14)**: riesgo aceptado — degradación honesta
  ante upgrade de Spring; sin test dedicado (la suite 107/107 es la red).
- **D-15**: dependencia del envoltorio `FrameworkServlet.processRequest`
  (los 3 catches desenvolvedores del guard) — riesgo documentado, sin
  test unitario del interno de Spring; E5.28/QP-28 lo certifica E2E.
- **D-12**: excepciones del suscriptor del seam — sin test (cero clientes);
  C7 definirá su contrato.
- **405** (S5-12(e)): GET/PUT sobre `/ingest` → 405 por defecto de Spring,
  fuera del contrato — sin escenario.
- **404 en ingesta**: NO existe (la key deduce el proyecto; "no existe es
  401") — un test que espere 404 es ERROR.

## 6. Criterios de PASS por familia

- **[L] (unit/MVC/pin/higiene)**: `mvn test` DENTRO de la imagen builder
  `maven:3.9-eclipse-temurin-21` (backend + `~/.m2` montados, R5.17) con
  **exit 0**; la suite completa C1–C4 re-corrida (77/77→suite final
  107/107 por design) SIN un solo fallo — R5.19b es criterio de PASS de
  QP-16b; `application-test.yml` INTOCABLE (los [L] corren con los
  defaults del `@Value` — por eso QP-35 pina el default literal `"1MiB"`).
- **[S] (smoke)**: `scripts/qa_c5_smoke.sh` (NUEVO, patrón
  `qa_c4_smoke.sh`) imprimiendo una línea `E5.n: PASS | FAIL |
  BLOQUEADO (detalle)` por cada ID E5.21–E5.33 y **exit 0** (1/2 como
  C3/C4). Precondiciones de ejecución: **smoke-of-the-smoke** previo
  (recomposición, `GET /me` → 401 `invalid_token`, login 200 con JWT a
  tmpdir, `POST /projects` → 201 capture incondicional del id,
  `regenerate` → 201 key a tmpdir — JAMÁS a consola); recomposición ÚNICA
  con `podman compose up -d --build --force-recreate` (timeout acotado) —
  **NUNCA `down -v`** (el volumen `pgdata` con los datos heredados debe
  sobrevivir); keys y JWT SOLO en tmpdir 700; conteos psql SIEMPRE con
  `psql -qAt` y DELTA relativa antes/después (sección 2.1); needles psql
  de literales `plk_` con escape `\_` o `position()`/`=` (S-3 heredado);
  nombres sufijados por ejecución (`smoke-c5-<TS>-*`).
- **FAIL/BLOQUEADO**: cualquier línea FAIL → FAIL del corte salvo causa
  de entorno (p. ej. plan B R-12 ya descartado, o pila no healthy —
  entonces BLOQUEADO con detalle y exit 2, como C3/C4).