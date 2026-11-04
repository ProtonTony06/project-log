---
title: "Nota C10 (T10.33): el preflight CORS con Access-Control-Request-Headers: x-confirm-name devuelva 403 «Invalid CORS request» — el DefaultCorsProcessor de Spring NO pattern-machea el literal «X-Confirm-*» de setAllowedHeaders (design L146 «cubre las cabeceras nuevas» no se cumple en HTTP real)"
type: bugfix
date: 2026-11-04
what: >-
  E10.15 del arnés visual C10 (re-corrida ts 20261104160223) falló con
  «preflight-reflejado(403)=False · origin-ajena(403)=True ·
  pantalla-sin-cambio=True". Sondeo REST directo contra la pila viva (sin
  JWT — el CORS se procesa ANTES de la cadena de seguridad, canon E7.9):
  OPTIONS /api/v1/panel/projects con Origin=http://localhost:8080 y
  ACR-Method DELETE + ACR-Headers «x-confirm-name' → 403 "Invalid CORS
  request" SIN cabeceras; la MISMA petición con ACR-Headers
  «authorization' → 200 con ACAO reflejada exacta, ACAM con DELETE y
  ACAH «authorization'. Es decir: el DefaultCorsProcessor de Spring
  matchea los headers pedidos por IGUALDAD contra la lista de
  setAllowedHeaders (["Authorization","Content-Type","X-Confirm-*"]) — NO
  soporta pattern-wildcards como «X-Confirm-*' (SOLO el «*» completo).
  La lista configurada viaja en ACAH cuando el preflight pasa, pero
  x-confirm-name JAMÁS pasa el filtro de ADMISSION. Efecto LATENTE en UI:
  el SPA es same-origin (nginx enruta /api al mismo puerto 8080 ⇒ el
  browser NO preflightea jamá los DELETE de la doble confirmación). Pero
  CUALQUIER cliente browser de otro origen (el 2.º origin de la lista
  G1-a «latente», el caso a+ del corte) vería sus 8a/8b/8c BLOQUEADOS por
  el preflight (el browser aborta antes del DELETE). El producto queda
  contra el design: design.md L146 «X-Confirm-* cubre las cabeceras
  nuevas» y §checks (L238) «preflight OPTIONS DELETE con X-Confirm-Name
  reflejado». NADA de la UI (same-origin) lo activa — de ahí que el
  smoke y los MvcTest no lo vean.
why: >-
  El defecto es de PRODUCTO (CorsConfig) y de COBERTURA de test: el
  CorsConfigTest pina la CONFIGURACION (getAllowedHeaders contiene el
  literal «X-Confirm-*' y ACAO refleja/403 ajena en source.getCorsConfiguration)
  pero NUNCA ejecuta un preflight REAL vía DefaultCorsProcessor
  (checkOrigin/checkHeadersValue) — la capa que decide. backend/ es
  INTOCABLE para la instancia qa del bucle T10.33 ⇒ FAIL honesto E10.15 +
  ESCALADO al orquestador (sin auto-fix del producto, sin maquillaje).
  El arnés E10.15 está bien escrito (pide el comportamiento que declara el
  design) — no hay parche de guion posible sin degradar el requisito.
where: >-
  backend/src/main/java/com/projectlog/config/CorsConfig.java (L62
  setAllowedHeaders(List.of("Authorization","Content-Type","X-Confirm-*")));
  capa real: DefaultCorsProcessor de Spring (match por igualdad, no
  pattern); test que NO lo pina:
  backend/src/test/java/com/projectlog/config/CorsConfigTest.java (solo
  asserts de lista/config, sin OPTIONS de extremo a extremo con
  x-confirm-name). Repro: cualquier OPTIONS con
  Access-Control-Request-Headers: x-confirm-name (cuerpo: Invalid CORS
  request). Fix de producto a ratificar (una línea): listar «X-Confirm-Name»,
  «X-Confirm-Log-Id» EXACTOS en setAllowedHeaders (o el «*» completo — pero
  ese amplia el área: EXACTOS es más pegado a G1-C2), + pin de test
  MvcTest DE PROFUNDIDAD: MockMvc con Origin + ACR-Headers y
  assert del 200 + ACAH conteniendo x-confirm-name.
learned: >-
  (1) Spring CorsConfiguration NO pattern-machea allowedHeaders (SOLO
  «*»); un sufijo-wildcard como «X-Confirm-*' es literal decorativo a
  nivel HTTP. (2) Test de CONFIGURACIÓN ≠ test de COMPORTAMIENTO: pin
  una lista de strings no ejercita el procesador que la aplica — el arnés
  visual (REST de extremo a extremo) es el que lo pilla, otra vez el
  blind-spot de la capa de config. (3) El comportamiento es LATENTE por
  arquitectura (same-origin por proxy) — el smoke REST del corte lo
  pasó por alto porque la pauta de E10.15 nació EN ESTE arnés (primera
  vez que se midió el preflight vivo con estas cabeceras).
## Resolución (C10, ratificada — D-C10-15)
- **Decisión** (orquestador, ratificación del fix de producto): REQ-C10-CORS-3 /
  design L146+L238 exigen el COMPORTAMIENTO (el preflight admite y refleja las
  cabeceras x-confirm-*), y la ortografía `X-Confirm-*` del spec/config NO era
  materializable a nivel HTTP: el DefaultCorsProcessor de Spring matchea los
  `Access-Control-Request-Headers` por IGUALDAD (solo el `*` COMPLETO es
  wildcard). Se implementa por LITERALES EXACTOS `X-Confirm-Name` y
  `X-Confirm-Log-Id` en `setAllowedHeaders` (más pegado a G1-C2 que el `*`
  completo). Sin `*` completo y sin `setAllowedOriginPatterns`.
- **Diff producto** (`backend/src/main/java/com/projectlog/config/CorsConfig.java`,
  bloque de allowedHeaders): `List.of("Authorization", "Content-Type",
  "X-Confirm-*")` → `List.of("Authorization", "Content-Type",
  "X-Confirm-Name", "X-Confirm-Log-Id")` + comentario español que explica el
  matcheo por igualdad y cita E10.15/REQ-C10-CORS-3.
- **Diff test** (`backend/src/test/java/com/projectlog/config/CorsConfigTest.java`):
  (1) el pin `conOrigenSeteadoLosHeadersMetodosYCredencialesSonLosDeS25` fija
  ahora los 4 literales exactos (único aserto heredado retocado — fijaba el
  literal `X-Confirm-*`); (2) slice `@Nested` de PROFUNDIDAD NUEVO con dos
  tests de COMPORTAMIENTO de extremo a extremo por la cadena 2 (CorsFilter →
  DefaultCorsProcessor, réplica del fixture de ProjectsControllerMvcTest con
  `projectlog.security.frontend-origin=http://localhost:8080`): preflight REAL
  `OPTIONS /api/v1/panel/projects` con ACR-Method DELETE + ACR-Headers
  x-confirm-name ⇒ 200 + ACAO reflejada + ACAM conteniendo DELETE + ACAH
  conteniendo x-confirm-name; y espejo con Origin ajena ⇒ 403 sin cabeceras
  CORS. Nada relajado (REQ-C10-QA-1/3).
- **Verificación**: `mvn test` (builder podman, canon C10) ⇒ BUILD SUCCESS,
  **Tests run: 225, Failures: 0, Errors: 0, Skipped: 0** — 223 previos VERDES
  + 2 pins nuevos.
- **Deploy**: `podman compose build backend` + `up -d --force-recreate
  backend`. Desviación menor: compose arrastró a los dependientes (postgres y
  frontend rec recreados; uptime ~1 min) — pgdata (volumen) INTACTO, 4 tablas
  public y salud 3/3 (backend `{"status":"UP"}` + wget frontend GREEN).
- **Preflight vivo** (con `curl`, el mismo sondeo de E10.15): OPTIONS
  `/api/v1/panel/projects` + Origin `http://localhost:8080` + ACR-Method
  DELETE + ACR-Headers x-confirm-name ⇒ **200** con ACAO
  `http://localhost:8080`, ACAM `GET,POST,PATCH,DELETE,OPTIONS` y ACAH
  `x-confirm-name`; la misma con Origin `http://atacante.example` ⇒ **403**
  «Invalid CORS request» SIN cabeceras CORS. E10.15 pasa por producto.
