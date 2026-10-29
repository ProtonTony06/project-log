---
title: "Hallazgo C9 (E9.26/E9.11-b): nginx resuelve el upstream backend UNA vez al arranque — recrear sólo el backend deja /api/v1 y /ws/logs en 502"
type: discovery
date: 2026-10-29
what: >
  Corrida 3 del arnés visual C9, fase destructiva B7: tras la ventana ÚNICA
  (compose stop backend + up -d backend en finally), el Spring arriba
  correctamente, PERO todo /api/v1 vía 8080 siguió en 502 durante TODO el
  bucle health de 180 s (exit 2, PILOTO CAÍDO). Causa: nginx
  (`proxy_pass http://backend:8080` SIN `resolver`) resuelve el hostname
  del upstream UNA VEZ al arrancar el contenedor y se queda con SU IP;
  la recreación del backend le da IP NUEVA y el proxy envejecido intenta
  alcanzar una IP sin listener → 502, aunque el backend esté vivo. La
  pila se restauró end-to-end (me=401) recreando SOLO el proxy:
  `podman compose up -d --force-recreate frontend`.
why: >
  Afecta a la VENTANA destructiva del diseño (R9.1.5/D-C9-5): con
  stop+up del backend (el único kill permitido), la restauración vía
  8080 es imposible sin tocar el frontend — el bucle health del arnés
  no puede distinguir «backend caído» de «proxy envejecido» midiendo por
  el único puerto del host (ADR-24). Remedio de RESTAURACIÓN del arnés
  (ya parcheado en scripts/qa_vis_c9.py, H-C9-NGINX-UPSTREAM): si tras
  20 s el health vía 8080 no converge, `up -d --force-recreate frontend`
  UNA vez (jamon down, jamón postgres/volúmenes) y continuar el bucle
  180 s. No es bug del app React ni del backend: es un límite de
  despliegue del proxy de este compose (sin docker-DNS-per-request).
where: >
  frontend/nginx.conf (location /api/v1/ y /ws/logs — proxy_pass sin
  resolver), compose.yaml, scripts/qa_vis_c9.py (bucleHealth + finally de
  b7), .atl/notes/ (esta). Producto/app SIN cambios. Alternativa de
  PRODUCTO para un corte futuro (decisión del ORQUESTADOR): resolver
  el upstream dinamicamente (resolver 127.0.0.11 valid=10s… — DNS de
  docker/podman) o fijar IP del backend en la rede del compose.
learned: >
  En una ventana destructiva que reinicia UN servicio, medir el health por
  el CONTENEDOR-proxy no basta: si el proxy cachea su upstream, hay que
  contemplar la recreación del proxy como parte de la restauración. En
  nginx, proxy_pass con hostname estático resuelve UNA vez.
---