---
title: "Hallazgo C9 (E9.11-b/E9.26): tras la caída del backend, el cliente WS muestra «Reconectando (intento 1)» y NO se recupera solo tras restaurar — el intento 2 queda colgado sin close en el handshake"
type: bugfix
date: 2026-10-29
what: >
  Corridas 3 y 4 del arnés visual C9 (fase destructiva B7, IDENTICO en
  ambas): la señal de caída es correcta (banner «Streaming inactivo…»
  visible, lista pintada persiste), PERO (1) el contador de reconexión se
  QUEDA en «Reconectando (intento 1)…» durante TODO el muestreo de 26 s
  (intentos=[1] en las DOS corridas — nunca N creciente) y (2) tras la
  restauración (backend up + proxy recreado, /api/v1/panel/me 401 vía
  8080), el indicador JAMÁS volvió a «Conectado» en 90 s y NO llegó a haber
  ni UN handshake, ni UN set_filters, ni UN refetch post-restauración
  (deltas 0 en E9.26, corrida 4). Lectura de producto (client.ts): la
  cadena backoff es SELF-sustaining POR CIERRES (alCierre ⇒ intento+1 ⇒
  timer ⇒ abrir); el primer close (intento 1) ocurre, pero el INTENTO 2 se
  queda PENDIENTE en el handshake: en esta pila (podman), `stop backend` +
  `up -d backend` recreó el contenedor con IP NUEVA (10.89.1.x) y el proxy
  nginx envejecido (proxy_pass SIN resolver — ver hallazgo
  c9-hallazgo-nginx-upstream-cacheado) deja los SYN sin respuesta/black-hole
  — el WebSocket del intento 2 jamá recibe su onclose (no hay timeout de
  handshake en el cliente: abrir() no protege la fase CONNECTING) ⇒ sin
  close NO hay intento 3, y muerta la cadena, la restauración del backend
  (aún con el proxy recompueto) jamá re-detonla: el recovery queda
  dependiente de un socket muerto-hanging.
why: >
  El contrato R9.2.11-b («Reconectando (intento N)… con N creciente») y el
  E9.26 (recuperación automática: banner RETIRADO, set_filters reenviado,
  refetch de 1) se diseñaron sobre el backoff por CIERRES de client.ts
  (R8.30/R8.31), que sí existe; el caso de esta pila — handshake que se
  cuelga ANTES de morir — rompe AMBOS: sin close no crece N y sin close no
  hay reintento post-restauración. En un despliegue real con IP estable o
  502-rápido (refuse), la cadena seguiría creciendo; el hang es propio del
  upsteam envejecido de podman. El arnés NO parcha producto: correcciones
  posibles (decisión del ORQUESTADOR, corte futuro): (a) timeout de
  handshake en ClienteWsLogs.abrir (ej. si tras T ms readyState sigue
  CONNECTING ⇒ close() manual ⇒ alCierre ⇒ intento+1); (b) nginx con
  `resolver` o IP fija para evitar el upstream envejecido; (c) el arnés
  con proxy/recreo como ya está en bucleHealth. Veredictos honestos de las
  corridas: E9.11 FAIL (N creciente no observado) y E9.26 FAIL (sin
  recuperación automática) — jamá fingidos (R9.6.4).
where: >
  frontend/src/ws/client.ts (abrir()/alCierre — cadena por cierres, SIN
  timeout de CONNECTING), frontend/src/hooks/useLogsStream.ts
  (alCierreInaugural — sólo el 1.º intento vía me()), frontend/nginx.conf
  (upstream resuelto al arranque), compose.yaml/podman (IP nueva tras
  recreate), scripts/qa_vis_c9.py (b7/bucleHealth — restauración),
  .atl/sdd/C9/qa-log.txt (corridas 3 y 4: caída banner=True intentos=[1];
  post conectado=False deltas 0). Producto SIN cambios en C9 (dif-0).
learned: >
  Un backoff sobre onclose muere en silencio si el handshake nunca llega a
  cerrarse: toda cadena de reconexión necesita UN timeout para la fase
  CONNECTING. Y el health de una pila con proxy NO equivale a la salud del
  path end-to-end que usa el navegador (el socket puede quedar colgado de
  un IP muerto aunque /api/v1 ya responda).
---