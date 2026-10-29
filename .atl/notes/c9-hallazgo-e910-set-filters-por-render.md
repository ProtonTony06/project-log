---
title: "Hallazgo C9 (E9.10): la app reenvía set_filters 3 veces por cambio de filtro (identidad de objeto en el efecto del hook, no en el cliente WS)"
type: bugfix
date: 2026-10-29
what: >
  Corrida 3 y repro A aislado del arnés visual C9: UN cambio de filtro en
  /logs produce delta de frames `set_filters` = 3 (identicos, misma
  longitud) en lugar del UNO que pide R9.2.10; el resto de los sub-astos
  (1 GET con el needle, forma 6 claves sin cursor, URL rehidratada, Reiniciar
  ⇒ URL VACÍA y GET sin parámetros) son conformes. La causa está en
  PRODUCTO, no en el WS: Logs.tsx interpola `filtros: toSetFilters(filtros)`
  en línea (objeto NUEVO en cada render) y useLogsStream dispara
  `aplicarFiltros` en el efecto [p.filtros] — que por IDENTIDAD de objetor
  se re-ejecuta en CADA render, no sólo al cambiar el filtro (el cliente
  ClienteWsLogs envía SIEMPRE que se le llama; el repro 4 mido 3 frames
  deterministas por evento — cambio y Reiniciar — con delta de handshakes 0
  y 1 socket vivo).
why: >
  Los frames redundantes son idempotentes en el backend (filter set igual)
  y no provocan replay de logs, pero contradicen el literal R9.2.10 («UN
  set_filters nuevo») y QP-8.22/E8.22; cada render del panel cuesta un
  frame al servidor (escala con re-renders del listado — N puede crecer).
  El arnés NO puede fingir PASS (regla D-C9-1/R9.6.4): E9.10 queda FAIL
  honesto con este hallazgo, y el resto de sub-astos sí verificados
  (nota en la corrida). La reparación de producto (memo de toSetFilters o
  dependencia del SEARCH string, no del objeto) toca C8 (useLogsStream /
  Logs.tsx) — decisión del ORQUESTADOR; el arnés NO parcha producto.
where: >
  frontend/src/pages/Logs.tsx (paso en línea de toSetFilters(filtros) al
  hook), frontend/src/hooks/useLogsStream.ts (useEffect [p.filtros] +
  aplicarFiltros), frontend/src/ws/client.ts (aplicarFiltros — fiel a S8-8),
  scripts/qa_vis_c9.py (E9.10, veredicto FAIL + nota),
  .atl/sdd/C9/qa-log.txt. Producto SIN cambios en C9 (dif-0, T9.19).
learned: >
  Un efecto con dependencia de OBJETO construido en el render del llamador
  se ejecuta en cada render, no en «cambio de valor»: la identidad gana.
  Los QA visuales que cuentan frames por evento son el detector natural —
  el test unitario del cliente (un llamado ⇒ un frame) no puede verlo.
---