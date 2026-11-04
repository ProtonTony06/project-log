---
title: "Nota C10 (T10.33): tras el 8b real la VUELTA a /logs restaura el acumulado del snapshot y la FILA BORRADA queda VISIBLE (stale) hasta el próximo refetch — banner «Registro eliminado» + fila viva contradictorios; sin refetch ni canal WS de borrados"
type: discovery
date: 2026-11-04
what: >-
  E10.9 del arnés visual C10 (re-corrida final ts del 2026-11-04,
  33 marcas · 3 FAIL): tras el 204 REAL de eliminarLog (modal 8b), el
  retorno a /logs?text=<needle> deja la FILA BORRADA EN PANTALLA
  (vacio=False — 1 fila — y el literal de estado vacío invisible).
  Veredicto del check (banner + vuelta + query intacta) PASS-CON-NOTA tras
  re-anchado al design; la fila stale quedó NOTADA, jamá pinada. Mecánica:
  al pulsar «Detalle» el listado guarda el acumulado en el store
  (storeListado.ts, módulo en memoria — guardarListado en Logs.tsx L~336);
  el delete ocurre con el store CONGELADO en el snapshot; al montar el
  retorno E8.35/D-C10-10 CONSUME el snapshot y SALTA el primer refetch
  (por design: el requisito es conservar acumulado y no parpadear); el
  cliente WS NO recibe nada del borrado — LiveBroadcaster sólo emite con
  INGESTA (pinned por E10.11 de ESTA corrida: deltas 0) — no hay canal de
  DELETE ⇒ la fila borrada permanece hasta el PRÓXIMO refetch que el
  usuario provoque (Actualizar, cambiar filtro, Reiniciar…). En pantalla:
  banner «Registro eliminado» + la fila aún presente = contradictorio.
why: >-
  El E10.9 original pedía «el listado del needle queda vacío con su
  estado vacío» — sobre-especificación DEL GUION: el design D-C10-8 pina
  vuelta + banner + query conservada, y NADA del contenido del listado;
  el vacío sólo es posible vía refetch (incompatible con el skip del
  snapshot E8.35 que otra norma del mismo corte pina). Elevación POR la
  pauta: es un hueco de producto para DECISIÓN del dueño (no está
  cubierto ni prohibido por el canon): opciones — (a) montar con
  location.state.aviso == "Registro eliminado" ⇒ expulsar la fila por
  id del acumulado restaurado (quirúrgico, 0 requests — coherente con el
  skip); (b) refetch de página 1 SÓLO en el retorno con state.aviso;
  (c) aceptar la fila stale (documentarla en UX). El arnés NO pinna
  ninguna opción: queda en conNota el nº de filas observado.
where: >-
  frontend/src/pages/Logs.tsx (L49-59 restaurada/consumirListado; L100-107
  salto del 1.er fetch; L254-257 banner aviso) ·
  frontend/src/hooks/storeListado.ts (snapshot módulo-en-memoria) ·
  frontend/src/pages/LogDetalle.tsx (alExito → navegar con state.aviso) ·
  componente front: ModalBorrado8b.tsx (alExito tras 204) · observación
  empírica: re-corrida final C10 (E10.9 con n_filas anotado). PATCH del
  arnés aplicado en scripts/qa_vis_c10.py E10.9 (re-ancado a vuelta+
  banner+query; filas observadas van en conNota).
learned: >-
  (1) «Conserva el acumulado» (C8) + «el WS no emite borrados» (C10) = la
  fila borrada es stale por CONSTRUCCIÓN — dos decisiones sanas combinadas
  producen un UX contradictorio que ninguna de las dos normas contempla:
  los huecos ENTRE decisiones pinnables son los que el check de
  extremo-a-extremo debe buscar. (2) Un check puede fallar por pedir de
  MÁS que el design (sobre-especificación) — la distinción guion/producto
  exigió re-leer el design letra por letra y no asumir el literal del
  arnés. (3) El estado de navegación (location.state) es un canal ya
  usado por el producto para C10 — es la herramienta barata para un (a)
  quirúrgico sin sacrificar el skip.