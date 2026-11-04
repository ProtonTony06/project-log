---
project: Project_Log
artifact: proposal
last_updated: 2026-10-29
status: pending-gate
change: C10 — Fase 2 (borrados con doble confirmación, resumen, doc acceso remoto, fixes ratificados de C9)
base_head: 27b62cb
insights: .atl/sdd/C10/explore.md (A.1–A.6, B.1–B.6, C.1–C.3, D, gaps 1–10)
---

# C10 · Proposal — borrados definitivos, resumen por proyecto, acceso remoto y fixes de C9

## 1. Problema y valor

**Problema.** El panel ya cubre la ingesta y el diagnóstico, pero tiene tres carencias
de Fase 2 [stories P1 del PRD §3]:

1. **Sin borrado:** los datos se acumulan sin forma de eliminarse. Archivar NO borra
   (R3.16: archivar va SOLO vía PATCH `status`); la regla 4 del Schema §4 declare que
   las ÚNICAS operaciones destructivas son los 3 DELETE con `X-Confirm-*` — y ninguno
   existe aún (grep: 0 métodos `delete` en repos, 0 DELETE mapeados más allá de la
   revocación de API key). Peor: como las rutas `DELETE /panel/projects/{id}` etc. no
   están mapeadas, la petición cae a `/error`, la cadena 3 denyAll responde
   401 `invalid_token` (DEV-C8-4) y el interceptor **desloguea al admin** al intentar
   cualquier borrado desde la UI (UX-401-logout).
2. **Sin resumen:** para "encontrar un error en menos de 2 minutos" (métrica 1 del
   PRD) el admin debe abrir cada ficha y consultar `/logs` por proyecto. El agregado
   `by_level / by_environment / total / last_log_at` (flujo 9) es el que sitúa
   "dónde hay ruido" y qué proyecto está "muerto" de un vistazo. La UI ya está
   preparada para recibirlo: el comentario literal de `/proyectos` dice
   «SIN resumen P1 (R8.18)» — fue excluido expresamente en C8 (R8.18: «sin
   ?include=summary en C8»), no es una laguna.
3. **Sin acceso remoto operativo:** Tailscale SSH ya funciona como ruta de gestión a
   la VM 108 (FB-0 de C9), pero no existe el doc operativo que el done-when del 06
   exige («el doc de acceso remoto está listo en /docs o README operativo»).
   Adicionalmente, la CORS actual de una sola origin hace que acceder por
   `192.168.1.58:8080` desde la LAN dé 403 en `/ws` y API — el doc de acceso remoto
   no puede ser verdad sin resolver eso (G1).

Además, C9 fichó **4 hallazgos de producto ratificados en memoria** que este corte
repara (H-C9-WS-COLGADO, H-C9-E9.13, H-C9-E9.2, H-C9-E9.10) y el arnés visual C9
queda con **4 checks FAIL honestos** (E9.11/E9.26, E9.13, E9.2, E9.10) que re-run
una vez parcheados.

**Valor.** Se completa la historia P1 "eliminación definitiva + cascada" y "resumen
por proyecto" (las dos últimas features del MVP funcional), el doc de acceso remoto
cierra la historia P1 "accesos remotos/preparación TLS", y el indicador de conexión
vuelve a ser confiable frente a un backend caído. Con esto el corte v2 del 07
(Fase 2) queda `hecho`.

## 2. Alcance

### Fuera del corte (ratificado por el explore / cortes previos — explícito)

- **Nada de 409/422 en los DELETE.** El texto de alcance de Fase 2 decía «200/409/422»
  para el borrado de proyecto; el CONTRATO (Schema §3.2/§3.3 + AppFlow flujo 8) dice
  **204** para proyecto y log, **200 `{deleted: N}`** para la purga, y **400**
  `confirm_required` / `confirm_mismatch` para la confirmación. 409 solo existe en
  `name_duplicate` (POST/PATCH) y `project_archived` (regen key); 422 NO existe en
  ninguna parte del Schema. **El proposal ratifica 204/200/400 y marca la divergencia
  del enunciado como errata** — los estados del AppFlow flujo 8 mandan.
- `/ws/logs` no emite frames por borrado (LiveBroadcaster solo emite desde ingesta);
  la lista refresca por refetch propio («dejan de aparecer en /logs en el próximo
  refetch», flujo 8a paso 3). Sin acoplamiento WS↔DELETE que resolver (gap 10).
- Retención/particionado, backups, rate limiting, TLS en sí: P2/Fase 3 (TRD §6.8:
  «documentado, SIN implementar en el MVP»). El proxy HTTPS se **documienta**, no se
  instala.
- VM 108: NO se toca este corte (ni CORS ni .env de la VM) — ver G1(a+).
- VM 107 huérfana: decisión del usuario que sigue PENDIENTE de C10 (state.md C9),
  no es alcance del proposal.
- El interceptor 401 (`client.ts`) NO se toca: ver G6.

### En el corte

**Backend** (todo bajo la cadena 2 `/api/v1/panel/**` existente — sin tocar
SecurityConfig, explore A.5):

| # | Pieza | Detalle |
|---|---|---|
| B1 | `DELETE /projects/{id}` | header `X-Confirm-Name: <name>` (nombre EXACTO) → 204 sin cuerpo. CASCADE por `ON DELETE CASCADE` existente en `V1__init.sql` (UNA sentencia, transaccional por el motor). 400 `confirm_required`/`confirm_mismatch` · 404 · 401. |
| B2 | `DELETE /logs/{id}` | header `X-Confirm-Log-Id: <id>` → 204. 400 · 404 · 401. |
| B3 | `DELETE /projects/{id}/logs` | header `X-Confirm-Name: <name>` → **200 `{deleted: N}`** (primer DELETE con cuerpo de respuesta del proyecto; N del retorno de filas de `JdbcTemplate.update`). Conserva proyecto y API key (sigue aceptando ingesta). |
| B4 | Resumen | `include=summary` en `GET /projects` + nuevo `GET /projects/{id}/summary` → 200 `{by_level, by_environment, total, last_log_at}`. Proyecto sin logs → mapas vacíos, `total: 0`, `last_log_at: null` (estado vacío informativo, no error). |

**Frontend:**

| # | Pieza | Detalle |
|---|---|---|
| F1 | `panel.ts` | las 3 funciones ya forman cabeceras; cambia el tipo de retorno: `void` para los 204 (hoy `Promise<never>`) y `{deleted: number}` para la purga (el parser tolerante de `finalizar()` ya devuelve el JSON en 200/201). |
| F2 | Ficha — cablear la confirmación real (G2) | `ProyectoDetalle.ejecutarConfirmado()` hoy envía `dto.name` (nombre real de BD); con C10 eso hace `confirm_mismatch` inalcanzable desde la UI y el guard del modal queda decorativo. Cablear el **texto tecleado** del modal (coincideLiteralConTrim ya lo valida) al header. |
| F3 | Ficha — post-borrado | 8a: 204 → redirigir a `/proyectos` con banner «Proyecto y sus logs eliminados definitivamente». 8c: 200 → banner «N logs eliminados; el proyecto y su key siguen intactos» (con N real). 400 → error dentro del modal sin cerrarlo (la UI ya lo pinta); 404 → §5 y retorno a lista. |
| F4 | Detalle de log — 8b | tras 204 de `eliminarLog»: banner «Registro eliminado» y regreso a `/logs` (hoy `Promise<never>` nunca resuelve). El modal V11 completo ya existe (maqueta 03). |
| F5 | Resumen en UI | tarjeta en `/proyectos` (bloque de badges `lv-*`/`env-*` + totals, maqueta 04) pidiendo `GET /projects?include=summary`; bloque «Resumen de logs» en la ficha (maqueta 05) vía `GET /projects/{id}/summary`. `api/types.ts`: declarar `SummaryDto` y el campo opcional. Estado vacío: «sin logs todavía» (flujo 9 paso 4). |
| F6 | Fix WS CONNECTING (H-C9-WS-COLGADO, G3) | timeout en `abrir()` de `ClienteWsLogs` — semántica del intento en design. |
| F7 | Fix doble «?» (H-C9-E9.13) | textual: usar `location.search` sin el «?» extra en `Logs.tsx::alVerDetalle` y en los DOS «Volver a logs» de `LogDetalle.tsx`. El store no se toca. |
| F8 | Fix destino post-login (H-C9-E9.2) | resolver la carrera del `Navigate` condicional de `Login.tsx` (opciones en design). |
| F9 | Fix set_filters ×3 (H-C9-E9.10) | `useMemo` para `toSetFilters(filtros)` en `Logs.tsx` (ya importado) — UN set_filters por cambio de filtro (R9.2.10). |
| F10 | Verificación UX-401-logout (G6) | sin cambio de código más allá de F1: con los DELETE reales el 401-falso desaparece; verificar que los 400 caen en el catch del modal y el 401 real (sesión expirada) sigue deslogueando. |

**Doc y QA:**

| # | Pieza | Detalle |
|---|---|---|
| D1 | Doc operativo | `docs/08_ACCESO_REMOTO.md` — Tailscale P1 (instalar en VM, acceso al mismo puerto 8080 por IP de tailnet, cero cambios en Compose, TRD §6.8) + preparación TLS P2 (estructura ya prevista: 1 proxy + servicios internos + WS detrás). Motivo de la elección en GATE implícito: `docs/README.md` es el índice del paquete de definición, no operatoria — un doc nuevo sigue la numeración 01–07 sin ensuciar el índice congelado. |
| Q1 | Arnés visual (G5) | checks de C10: re-run honestos de los 4 FAIL + checks nuevos de los 3 DELETE (doble confirmación UI+cabecera, estados del flujo 8) y del resumen (tarjeta + ficha). Evidencia en `.atl/sdd/C10/evidencia/` NO commitear (sha256 en qa-report). Destructivos al final. |
| T1 | Tests backend | MvcTest de las 3 DELETE + summary (401 de cadena en slice, 400 con los 2 codes de confirmación, 404, 204/200, orden fail-fast) + pins SQL de los literales DELETE/COUNT + test de serialización del SummaryDto. |
| T2 | Tests frontend | Vitest: doble-? (round-trip storeListado), memo de filtros (useLogsStream/useLogFilters), timeout CONNECTING en `ws/client.test.ts` (stub con readyState controlable), Navigate de Login (a nivel función/hook). |

## 3. Literales de CONTRATO citados (se citan, nunca se redefinen)

### 3.1 Tabla de endpoints — docs/05 §3.2 (transcripción tal cual)

| Método | Ruta | Request → Response (literal) |
|---|---|---|
| DELETE | `/api/v1/panel/projects/{id}` | «Eliminar proyecto **y sus logs** (CASCADE) — P1 · header `X-Confirm-Name: <name>` → **204 · 400 · 401 · 404**» |
| DELETE | `/api/v1/panel/logs/{id}` | «Eliminar un registro — P1 · header `X-Confirm-Log-Id: <id>` → **204 · 400 · 401 · 404**» |
| DELETE | `/api/v1/panel/projects/{id}/logs` | «Purgar todos los logs del proyecto (el proyecto sobrevive) — P1 · header `X-Confirm-Name: <name>` → **200 `{deleted: N}` · 400 · 401 · 404**» |
| GET | `/api/v1/panel/projects` | «query `status?`, `include=summary?` → 200 `[ProjectDto]` · 401» |
| GET | `/api/v1/panel/projects/{id}/summary` | «Resumen por proyecto — P1 · → 200 `{by_level, by_environment, total, last_log_at}` · 401 · 404» |

### 3.2 Doble confirmación — docs/05 §3.3 (transcripción tal cual)

> - Cabecera ausente → 400 `{"code": "confirm_required"}`; valor incorrecto →
>   400 `{"code": "confirm_mismatch"}`; nada se borra. 404 si ya no existía.
> - `DELETE /projects/{id}` con `X-Confirm-Name: <name>` (nombre EXACTO) → 204.
> - `DELETE /logs/{id}` con `X-Confirm-Log-Id: <id>` → 204.
> - `DELETE /projects/{id}/logs` con `X-Confirm-Name: <name>` → 200
>   `{deleted: N}`; **conserva el proyecto y su API key**, que sigue aceptando ingesta.

Y de §3.1/§4: «`DELETE /projects/{id}` es la única forma de borrar definitivamente
(archivar va SOLO por PATCH status, R3.16)»; regla 3: «FK única CASCADE, sin
huérfanos»; regla 4: «log inmutable … Únicas operaciones destructivas: los 3 DELETE
con `X-Confirm-*`». AppFlow flujo 8: «La doble confirmación es estructura
obligatoria del sistema: (1) confirmación explícita en la UI y (2) dato de
confirmación **en el propio request** vía cabeceras `X-Confirm-*`. Sin ambas, la API
rechaza con 400».

### 3.3 SummaryDto — docs/05 §3.3 (transcripción tal cual)

> **SummaryDto**: `{"by_level": {"ERROR": 12, "WARN": 3}, "by_environment":
> {"production": 40}, "total": 55, "last_log_at": "2026-10-16T22:31:04Z"}` —
> mapas con recuento por cada nivel/entorno presente; `total` sumar de logs,
> `last_log_at` = máximo `received_at` (`null` si no hay logs; AppFlow flujo 9).
> Proyecto sin logs → total 0 y `last_log_at: null` (estado vacío informativo, no
> error).

> El campo `summary` está **ausente** del DTO salvo en `GET /projects` con
> `include=summary` (donde siempre viaja, nunca vacío por defecto: con un proyecto
> sin logs sus mapas quedan vacíos y `total: 0`, `last_log_at: null`).

### 3.4 Estados UI del flujo 8 — docs/03 (transcripción tal cual)

- **8a:** «204 → **resultado**: redirige a `/proyectos` con banner "Proyecto y sus
  logs eliminados definitivamente"; el proyecto desaparece de la lista y sus logs
  dejan de aparecer en `/logs` en el próximo refetch. 400 confirmación (cabecera
  ausente o incorrecta) → error "La confirmación no coincide"; nada se borra.
  404 → el proyecto ya no existía: mensaje de §5 y retorno a la lista.»
- **8b:** «204 → confirmación "Registro eliminado" y regreso a `/logs` (o el propio
  detalle); la fila desaparece en la próxima vista del listado. 400 → confirmación
  no valida; 404 → el registro ya no existe (§5).»
- **8c:** «200 `{deleted: N}` → **resultado**: banner "N logs eliminados; el proyecto
  y su key siguen intactos"; el resumen (flujo 9) pasa a recuento cero; la próxima
  consulta de logs del proyecto arranca vacía. 400 / 404 → §5.»

### 3.5 Flujo 9 (resumen) — docs/03, pasos 2–5 (transcripción tal cual)

> 3. **Resultado visible**: en cada tarjeta/fila de proyecto, los recuentos por
>    nivel (destacando WARN/ERROR/FATAL para situar "dónde hay ruido"), el recuento
>    por entorno, el total y la fecha del último log (`last_log_at`, útil para ver
>    proyectos "muertos"). Los archivados siguen mostrando el mismo resumen (su
>    histórico es consultable).
> 4. Un proyecto sin logs → recuento cero y `last_log_at` nulo, presentado como
>    estado vacío informativo ("sin logs todavía"), no como error.
> 5. …no se recalcula en tiempo real por el WebSocket; se refresca al recargar o
>    con **Actualizar**.

### 3.6 Contrato visual — docs/04 + maquetas

- **V10** (04_UIUX §8): «Confirmación de borrado por proyecto: escribir el nombre
  exacto (mapea 1:1 con `X-Confirm-Name`)» — input con trim al validar (§4, Input).
- **V11** (04_UIUX §8): «Confirmación de borrado registro a registro: **escribir la
  sentencia literal completa** (`DELETE /logs/48211 · header X-Confirm-Log-Id:
  48211`) en un input, comparación literal con trim, botón rojo deshabilitado hasta
  coincidir» — el modal de la maqueta 03 (detalle con `select-all`, error literal
  dentro del modal).
- Botón destructivo deshabilitado: rojo apagado `bg-red-300` en modales hasta validar
  texto (04_UIUX §4); el destructivo jamás fuera de una zona destructiva explicada
  (V7).
- **Maqueta 04** (`/proyectos`): tarjeta con badges de severidad
  (`FATAL 1 · ERROR 148 · WARN 63 · DEBUG 1 000 · INFO 24 118`), badges de entorno
  (`production 23 988 · staging 1 342 · development 0`), franja inferior tras
  `border-t` con «**25 330** logs» y «último: 2026-10-19T21:47:58Z»; archivado con
  `opacity-80` y resumen INTACTO. Cabecera: «GET /projects?include=summary · 5
  proyectos · 1 archivado» + botón «Actualizar».
- **Maqueta 05** (ficha): sección «Resumen de logs» con total grande («25 330» +
  «logs totales · último <mono>…»), mismas bandas de badges y entornos; Zona
  destructiva P1 con las sentencias mono pintadas (`DELETE /projects/3/logs ·
  X-Confirm-Name: payments-service` / `DELETE /projects/3 · …`).
- **NOTA de estructura (explore B.6):** `TarjetaProyecto` es un `<button>` íntegro —
  los números del resumen son texto plano, cabe sin conflicto; nada interactivo nuevo
  dentro de la tarjeta.
- Paleta de badges severidad/entorno: la de 04_UIUX §2 (ya implementada en la tabla /
  banda En vivo; el resumen la reutiliza).

### 3.7 Acceso remoto — docs/02 §6.8 (transcripción tal cual)

> - **Acceso remoto Tailscale** (P1 del PRD): instalar Tailscale en la VM y acceder
>   al mismo puerto del frontend por la IP de la tailnet; cero cambios en la Compose.
> - **Proxy inverso con HTTPS** (P2): si hay dominio futuro, se añade el proxy con
>   Let's Encrypt delante (o se configura TLS en el propio nginx) — la estructura
>   actual (1 proxy + servicios internos + WS detrás) ya es la del diseño inverso;
>   nada del MVP se rompe.

Estado de partida: FB-0 de C9 ya certificó Tailscale SSH a la VM
(`root@100.64.3.17` vía ProxyJump); falta la OPERATORIA documentada del acceso al
panel por tailnet y las notas TLS-P2.

## 4. Estrategia por pieza

### 4.1 Backend — 3 DELETEs (`ProjectsController`, `LogsController`, repos)

- **Estilo y lugar:** controller FINO como toda C3 (sin capa de servicio, validación
  manual fail-fast, helper `error()` local `{code, message, field?}`). El hueco
  natural de `DELETE /projects/{id}` y `DELETE /projects/{id}/logs` es
  `ProjectsController` (mismo `/api/v1/panel/projects` mapping); `DELETE /logs/{id}`
  junto a los GET de `query/controller/LogsController`. El patrón material 204/404
  ya existe en `ApiKeyController` (explore A.1): revocar key = DELETE con 204 sin
  cuerpo — replicar.
- **CASCADE:** `logs.project_id` YA es `ON DELETE CASCADE` en `V1__init.sql`
  (explore A.2): el borrado de proyecto es UNA sentencia
  `DELETE FROM projects WHERE id = ?`, PG purga los logs en la misma transacción,
  sin pasos intermedios (flujos 8a paso 4). `@Transactional // defensa documentada
  (R3.2): mono-sentencia`, patrón del repo de C5. La API key NO es tabla aparte: al
  borrar la fila desaparece y la ingesta queda en 401 `api_key_invalid`
  automáticamente.
- **SQL nuevo** (no hay NINGÚN delete en repos hoy):
  - `DELETE FROM projects WHERE id = ?` — borrado definitivo.
  - `DELETE FROM logs WHERE id = ?` — log aislado.
  - `DELETE FROM logs WHERE project_id = ?` — purga; el int de retorno de
    `JdbcTemplate.update(...)` ES el `deleted: N` (primer uso del retorno de filas
    en el proyecto — todo lo previo es query/RETURNING; patrón nuevo sin precedente,
    pin SQL D-9). El índice `logs_dashboard_idx (project_id, …)` sirve a la purga
    por su prefijo.
- **Orden de validación fail-fast (a decidir en design):** 404 vs 400 de
  confirmación; disciplina análoga a C3 (null→enum→longitudes) — el proposal deja la
  secuencia exacta para el spec/design, con los DOS codes de confirmación
  incondicionales: cabecera ausente → `confirm_required`, valor incorrecto →
  `confirm_mismatch` (comparación EXACTA: el trim con comparación del flujo 8b es
  del INPUT de la UI; la cabecera viaja tal cual).
  La 404 solo existe de verdad («ya no existía», flujo 8a paso 3).
- **Tests:** MvcTest en `ProjectsControllerMvcTest` (sitio natural, ~25 @Test de C3):
  401 de la cadena vía slice, 400 con los dos codes, 404, 204/200, orden fail-fast;
  pins SQL de los 3 literales DELETE; smokes de SQL con la pila como siempre
  (`scripts/qa_c*_smoke.sh`).

### 4.2 Backend — resumen (include=summary + GET /{id}/summary)

- **`include=summary`** (G4): parámetro aditivo fail-fast ANTES de tocar BD,
  disciplina C7 (`@RequestParam(required=false) String include`; R8.18 lo excluyó
  expresamente de C8 — entra ahora por contrato §3.2). Valor válido: `summary`
  (única enumeración en §3.2; la política con otra cosa — p. ej. 400
  `filter_invalid_value` — la fija el design, coherente con el resto de filtros).
- **SummaryDto:** JSON `{by_level, by_environment, total, last_log_at}` — mapas SOLO
  con niveles/entornos PRESENTES (`MAX`/GROUP BY; proyecto sin logs → mapas vacíos,
  `total: 0`, `last_log_at: null`). `last_log_at` formateada con el formatter C2
  compartido (`LoginController.EXPIRES_AT_FORMATTER`, patrón del RowMapper de
  `ProjectsRepository`).
- **SQL agregado nuevo** (nada existe aún, explore A.3): JdbcTemplate con **un
  estamento por proyecto** (log_count con SUMs condicionales por level y by
  environment, o 2 GROUP BY separados — el nº de estamentos exacto lo decide el
  design sobre el índice maestro; ambas vías son mono-idempotentes sobre
  `logs_dashboard_idx`). `MAX(received_at)` para `last_log_at`.
- **Modelo Java** (recomendación G4-a): `ProjectDto` es record de 8 componentes y no
  expresa campo opcional limpio; opción **(a)** — campo `summary` con
  `@JsonInclude(NON_NULL)` sobre el componente (mínimo, reversible), + endpoint
  dedicado con el MISMO SummaryDto. Alternativa (b), DTO envoltorio, descartada como
  recomendación (más superficie, mismo fin). `api/types.ts` declara
  `SummaryDto` + campo opcional.
- **Dónde vive el repo:** decide el design (candidatos: `query/` junto al resto del
  SQL de consulta, o `projects/`); la recomendación es `query/repository/` por
  co-locación con `LogsQueryRepository` (agregados sobre los mismos datos).

### 4.3 Frontend — UI de borrado y resumen

- **`panel.ts` (F1):** solo tipos de retorno; el parser tolerante de `finalizar()`
  ya devuelve el JSON del 200.
- **Ficha — cableado real de la confirmación (F2, G2-a):** `ModalEliminar8a` y
  `ModalPurga8c` ya exigen el nombre exacto (`coincideLiteralConTrim`) y pintan el
  error dentro del modal sin cerrarlo; el cambio es pasar el TEXTO VALIDADO al header
  (elevar el input o cambiar la firma de confirmación — el design elige la vía
  mínima). Con esto `X-Confirm-Name: {dto.name}` → `X-Confirm-Name: {texto}` y el
  doble confirm deja de ser decorativo. `ModalBorrado8b` ya manda el id correcto.
- **Ficha — post-borrado (F3/F4):** banners con los literales exactos de flujo 8;
  204/200 se consumen de `panel.ts`; 400 → catch del modal (sin cerrar); 404 → §5.
- **Resumen en UI (F5):** `/proyectos` pasa a pedir `GET /projects?include=summary`;
  `TarjetaProyecto` añade bandas de badges + franja `border-t` (maqueta 04); la ficha
  añade la sección «Resumen de logs» vía `GET /projects/{id}/summary` en su
  `cargar()` (maqueta 05; §3.2 asigna el include SOLO a la lista, así que la ficha
  usa el endpoint dedicado). Énfasis WARN/ERROR/FATAL como pinta la maqueta
  (badges `lv-*`, FATAL primero).
- **Fixes F6–F9:** terreno exacto en explore B.2–B.5. F6 usa el Reloj ya inyectado
  en `ClienteWsLogs`; F7 es textual (2 ficheros, 3 puntos); F8 toca el par
  `Login.tsx`/`useAuth.ts` (opciones: navegar antes del flip de estado, condicionar
  el Navigate a «sin destino pendiente», o llevar el destino al state del Navigate —
  el design decide y lo testea a nivel función/hook); F9 es el `useMemo` con
  dependencia `filtros` (identidad estable por `useLogFilters`).

### 4.4 Doc de acceso remoto (D1)

`docs/08_ACCESO_REMOTO.md`, con la operativa real observada en C9 (Tailscale SSH
vía ProxyJump, nftables GATE #11 con solo SSH-LAN + 8080 — añadir la regla 8080 por
tailnet SI el usuario la pide, docker compose, `client_max_body_size`), el acceso al
panel por IP de tailnet al mismo puerto de Compose (TRD §6.8: «cero cambios en la
Compose») y la sección TLS-P2 (estructura ya prevista, Let's Encrypt delante o TLS
en nginx, se documenta SIN instalar). El doc marca explícitamente la cuestión CORS
LAN como condicionada a G1.

### 4.5 Arnés y QA (Q1, G5)

- **Re-run honestos** de los 4 FAIL una vez parcheados: E9.11/E9.26 (WS
  CONNECTING — necesita matar el servicio backend como hoy), E9.13, E9.2, E9.10.
- **Checks nuevos C10:** doble confirmación en los 3 DELETE (UI + cabecera;
  `confirm_mismatch` alcanzable desde la UI gracias a F2 — chequeo con texto mal
  tecleado que NO borra y pinta error dentro del modal), estados del flujo 8
  (204→banner→/proyectos; 200→banner con N; 8b→«Registro eliminado»), resumen
  (tarjeta con badges + franja de totals en `/proyectos`; bloque en ficha; estado
  vacío «sin logs todavía»).
- **Orden:** checks destructivos AL FINAL (matan la siembra que E9.x necesita);
  la limpieza real del residuo C9 son precisamente los DELETE de C10.
- **Evidencia:** PNGs en `.atl/sdd/C10/evidencia/` (NO commitear, sha256 en
  qa-report; NOTA: `qa_vis_c9.py` tiene DIR_EVID hardcodeada a C9 — la fase nueva
  redirige a C10).

## 5. Riesgos y reversibilidad

| # | Riesgo | Mitigación / reversibilidad |
|---|---|---|
| R1 | Primer `200` con cuerpo en un DELETE y primer uso del retorno de `JdbcTemplate.update` (gap 6) | Pin SQL + MvcTest del 200 `{deleted: N}`; patrón ApiKeyController 204 como referencia; reversible (los endpoints se quitan sin efectos laterales). |
| R2 | `confirm_mismatch` inalcanzable desde la UI si F2 no entra (gap 2) | G2-a lo cierra; el MvcTest cubre el 400 por API. |
| R3 | `summary` ausente vs record Java (gap 3) | G4-a `@JsonInclude(NON_NULL)` — 1 anotación, reversible; test de serialización verifica «ausente sin include, presente con include». |
| R4 | Semántica del intento en el timeout CONNECTING (gap 5) | G3-a con la distinción explícita (`abiertoAlgunaVez` / close-durante-handshake) en design + test del ciclo stubbed. Un fix del cliente es reversible puro frontend. |
| R5 | CORS multi-origen toca `CorsConfig` + `CorsConfigTest` (gap 7) | G1: una división por coma de la MISMA env var, sin `*` ni patterns (G1-C2 vigente); test actualizado; 100 % reversible. La VM se toca SOLO si el usuario ratifica (a+); sin tocarla, el doc documenta el workaround. |
| R6 | Checks destructivos destruyen la siembra de E9.x si corren antes | Orden: destructivos al final; re-siembra disponible a pedido (state.md). |
| R7 | `TarjetaProyecto` es `<button>` íntegro | El resumen es texto plano/badges: sin sub-estructura interactiva nueva. |
| R8 | denyAll-en-/error residual (gap 8) | Ratificado DEV-C8-4 en C8; desaparece para los DELETE al implementarlos; queda para rutas inexistentes ajenas — NO es alcance. |
| R9 | Doble navegación de Login puede tener más vías (index-redirect) | Fix acotado a la carrera señalada (Login.tsx 31–33 vs enviar()); E9.2 endurecido observando la URL PERDURANDO (hallazgo learned 1). |
| R10 | Scope creep | Toda añadidura nueva = nueva versión del PRD (vigilancia del 07); nada de Fase 3 entra (backups, retención, TLS real). |

## 6. GATE — decisiones para el usuario (cada una: opciones cerradas + recomendación + reversibilidad)

### G1 — Origen de navegación LAN (CORS multi-origen) · **PENDIENTE-DE-CONFIRMACIÓN-DEL-USUARIO** (pregunta cerrada)

Hoy `FRONTEND_ORIGIN=http://localhost:8080` en la VM y `CorsConfig` refleja UNA sola
origin (`setAllowedOrigins(List.of(frontendOrigin))`; `"*"` y patterns prohíbidos por
G1-C2). Acceder por `192.168.1.58:8080` da 403 en `/ws` y API.

Opciones:
- **(a) Lista multi-origen configurable** — `FRONTEND_ORIGIN` acepta valores
  separados por coma; `CorsConfig` los divide en la lista de
  `setAllowedOrigins` (1 línea + test). No reemplaza la idea original: mantiene una
  sola env var con semántica ampliada.
- **(b) Dominio/nombre fijo** en `/etc/hosts` o DNS local de la LAN (una origin
  canónica para todos los clientes).
- **(c) Dejar como está** y documentar el trabajo alrededor (solo localhost, o túnel
  Tailscale hacia un origin ya permitido) en `docs/08_ACCESO_REMOTO.md`.

**RECOMENDACIÓN: (a)** — el mínimo que hace verdad el doc de acceso remoto,
reversible (1 línea de split + test), y sin tocar la VM en este corte.
**Variante (a+), sujeta a ratificación del usuario:** añadir
`http://192.168.1.58:8080` AL `.env` de la VM como segundo origin CUANDO el usuario
lo ratifique — en este corte NO se toca la VM 108: se deja preparado en código y
anotado aquí (y en el doc, marcado como paso de deploy futuro).

### G2 — `X-Confirm-Name` desde la UI: ¿cablear el texto tecleado?

- **(a) Cablear el input** del modal (8a/8c) a la cabecera — es el contrato del
  modal V10 y el doble-confirm real; hoy `ProyectoDetalle` envía `dto.name`, con lo
  que `confirm_mismatch` sería inalcanzable desde la UI y el guard decorativo.
- **(b) Dejar como está** y que el mismatch exista solo por API/tests.

**RECOMENDACIÓN: (a).** Reversible (signatura/coincidencia local al componente).

### G3 — Semántica del timeout CONNECTING del WS (fix H-C9-WS-COLGADO)

- **(a) Timer en `abrir()`** (por el Reloj ya inyectado): si tras T ms el
  `readyState` sigue `CONNECTING` ⇒ `close()` manual, y ese cierre cuenta como
  intento (`intento+1` en la cadena `alCierre`). **Cuidado señalizado:** la vía
  `close-sin-open` enruta a `alCierreInaugural` (`me()`) y NO incrementa `intento` —
  el design debe decidir cómo distinguir (p. ej. flag «abriendo por intento N>1» o
  distinguir close-durante-handshake) para que N crezca como pide R9.2.11-b.
- **(b) Polling por `me()`** para re-anclar la cadena (reconexión periódica
  independiente del close).

**RECOMENDACIÓN: (a)** con la distinción explícita en el design; **T = 10 s**
(heartbeat 30 s; backoff existente ya opera por encima; un hang eterno se corta a
los 10 s). Reversible (código del cliente + stub listo en `ws/client.test.ts`).

### G4 — Resumen (`summary`) y modelo Java

- **(a) Campo `summary` con `@JsonInclude(NON_NULL)` sobre el componente del record**
  (mínimo; literal §3.3 «ausente salvo con include» respetado) + **endpoint
  dedicado** `GET /projects/{id}/summary`, ambos con el mismo SummaryDto.
- **(b) DTO envoltorio** (shape separado para la lista con include).

**RECOMENDACIÓN: (a) + endpoint dedicado.** SQL: JdbcTemplate, un estamento por
proyecto (by_level, by_environment, total, last_log_at con el formatter C2); purga
con `DELETE ... WHERE project_id = ?` y `deleted: N` del `getUpdateCount`/retorno
del `update`. La forma EXACTA del SummaryDto es la §3.3 citada arriba (3.3).
Reversible.

### G5 — Arnés visual C10

- **(a) Extender el arnés existente** creando `scripts/qa_vis_c10.py` que **importe
  la maquinaria de `qa_vis_c9.py` si el import es limpio**; si no lo es, copia
  función-a-función CONTROLADA (cada función copiada queda identificada en el
  fichero) — NO duplicar los 26 checks; los re-runs de los 4 FAIL se invocan desde
  la fase C10.
- **(b) Parchear qa_vis_c9.py in place** (renumerar guard de 26, tocar DIR_EVID).

**RECOMENDACIÓN: (a)** — fichero nuevo con reuso máx; deja al C9 canon intacto
(bit a bit, como quedó en T9.31) y aísla el riesgo del monolito. Evidencia en
`.atl/sdd/C10/evidencia/` NO commiteada (sha256 en qa-report), destructivos al final.
La opción elegida (import limpio vs copia controlada) queda constatada en el head del
script.

### G6 — UX-401-logout (ratificar DEV-C8-4)

Ratificar la decisión DEV-C8-4 de C8: el 401 del denyAll ante rutas inexistentes
era comportamiento CORRECTO; **NO se toca código del interceptor**. Con los DELETE
reales el 401-falso deja de ocurrir para ellos (desaparece la raíz, no el
síntoma) y la UI consume los status reales: los 400 caen en el catch del modal, el
204/200 resuelven, y un 401 REAL (sesión expirada) sigue deslogueando por AppFlow §5.
Verificación incluida en los checks de borrado del arnés (chequeo explícito de que
el modal NO se cierra y NO desloguea en un 400). El explore no halló nada contrario
(B.1: «no se detectó ningún toque adicional requerido en client.ts»).

## 7. Done-when del corte (citado del 06/07 — NO redecible)

De `docs/06_IMPLEMENTATION_PLAN.md` (Fase 2 — v1 [stories P1]), fiel:

> **Done cuando:** las 3 operaciones destructivas exigen doble confirmación
> (UI + cabecera) y devuelven los estados del AppFlow flujo 8; el resumen aparece
> en lista/ficha; el doc de acceso remoto está listo en /docs o README operativo.

De `docs/07_SLICING_PLAN.md` §C10 (fiel):

> - **Done cuando (del 06):** los 3 DELETE exigen doble confirmación (UI +
>   cabecera) y devuelven los estados del AppFlow flujo 8; el resumen aparece
>   en lista/ficha; el doc está en /docs o README operativo.
> - **Insumos:** Schema §3.2/§3.3, AppFlow flujo 8/9, maquetas 03/04/05, TRD §2.3.

**Mapeo done-when ↔ alcance de este proposal:** doble confirmación UI+cabecera →
F2/G2-a + B1–B3; estados flujo 8 → literales 3.4 + F3/F4 + checks del arnés;
resumen en lista/ficha → B4 + F5 + maquetas 04/05; doc operativo → D1
(`docs/08_ACCESO_REMOTO.md`, en /docs).
Los fixes H-C9-* no forman parte del done-when literal del 07, pero son
asignación ratificada del orquestador (GATE de cierre C9: «parches de producto …
C10») y quedan aquí de primera clase (F6–F9 + re-runs honestos).