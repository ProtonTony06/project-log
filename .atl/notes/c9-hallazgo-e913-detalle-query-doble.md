---
title: "Hallazgo C9 (E9.13): la navegación a /logs/:id rehidrata la query DOBLE («??text=…») — rompe el round-trip guardar/consumir listado"
type: bugfix
date: 2026-10-29
what: >
  Corrida 3 del arnés visual C9: al clickar «Detalle» en una fila de /logs
  con query activa, la URL aterriza en /logs/<id>??text=qa-c9-… (DOBLE
  «?»), y el E9.13 fracasa en su aserto de «query del detalle intacta».
  Causa real de PRODUCTO, localizada: en Logs.tsx el handler de detalle
  construye `/logs/${id}${location.search ? \`?${location.search}\` : ""}`
  y location.search YA trae su «?» («?text=…») — se lo antepone UNO MÁS.
  Misma fórmula en el botón Volver de LogDetalle.tsx
  (`to={\`/logs${location.search ? \`?${location.search}\` : ""}\`}`).
why: >
  El doble «?» corrompe el contrato E8.35/D-C8-8 (acumulado keyset entre
  detalle ⇆ retorno): storeListado.gListado(searchActual) compara la search
  TOMADA con la actual; la guardia consume con «??text=x» una search
  guardada como «?text=x» ⇒ MISMATCH ⇒ el acumulado no se consume (se
  refetcha página 1 al volver) y la invariante de E9.13 (query, nº filas,
  1.ª fila intactas, CERO refetch) queda rota también para el usuario del
  app (el acumulado de «Cargar más» se pierde al entrar/salir del detalle
  con filtros). No es defecto del arnés: la URL doble se observó en la
  corrida REAL (qa-log corrida 3, E9.13).
where: >
  frontend/src/pages/Logs.tsx (alVerDetalle → navegar), frontend/src/pages/
  LogDetalle.tsx (botón Volver), frontend/src/hooks/storeListado.ts
  (consume-listado). Producto SIN cambios en C9 (dif-0, T9.19): la
  reparación (usar location.search directamente SIN «?» extra, o
  useSearchParams) toca el corte E8 — decisión del ORQUESTADOR.
learned: >
  Cuando una URL destino debe conservar la query, NO interpolar
  manualmente «?» sobre location.search: ya lo trae. La invariante
  E8.35/round-trip del acumulado es el detector natural de este bug —
  un test de integración sobre el detalle + query la habría cazado.
---