---
title: "Parche post-Q23 de C8: detalle de log como pantalla dedicada /logs/:id"
type: decision
date: 2026-10-28
what: >
  Parche acotado post-archive del corte C8 (hallazgo Q23 documentado): la
  vista de detalle deja de ser RUTA ANIDADA bajo /logs y pasa a RUTA HERMANA
  /logs/:id (LogDetalle se monta sola, /logs se desmonta al abrirla). Nueva
  decisión D-C8-8, ratificada por el USUARIO en su demo manual (2026-10-28,
  OPCIÓN 2) y que MATIZA D-C8-7a (listado montado bajo el detalle con
  Outlet): el usuario clickaba "Detalle" y la ficha pintaba DEBAJO de la
  tabla, fuera de la vista inicial — parecía que "no llevaba a ningún
  sitio"; lo mismo con el 404 de ids borrados.
why: >
  E8.35 («Volver a /logs conserva filtros y acumulado») se conserva con la
  manera barata y honesta elegida (OPCIÓN del plan): los FILTROS no
  necesitan store — viven en la query string (D-C8-6, única fuente) y viajan
  en la URL del detalle y del retorno (Logs añade location.search al navegar
  al detalle; LogDetalle enlaza Volver con esa search). El ACUMULADO
  keyset (items + next_cursor) sí se perdería con el desmontaje: viaja en
  un store EN MEMORIA de módulo (frontend/src/hooks/storeListado.ts) — se
  guarda justo antes de navegar al detalle y se CONSUME una única vez al
  montar /logs, SOLO si la query string coincide (retorno legítimo del
  detalle); carga directa, TopBar u otra ruta ⇒ /logs arranca limpio
  (R8.20 — la query es la única fuente). El primer refetch de página 1 del
  efecto [filtros] se salta en ese caso (flag ref) para no machacar el
  acumulado; el acumulado entra además en el useState INICIAL (evita la
  carrera con el primer frame WS). Recarga dura (F5): store borrado →
  arranca limpio y el usuario reconstruye con "Cargar más". Higiene: el
  store NO toca sessionStorage/localStorage (siguen siendo propiedad única
  de useAuth — R8.7/greps §9); memoria de módulo basta (no sobrevive a
  recarga por diseño).
where: >
  frontend/src/App.tsx (rutas hermanas logs + logs/:id bajo el mismo padre
  Protegidas — el guard RequireAuth SIGUE cubriendo /logs/:id y el TopBar
  sigue presente); frontend/src/pages/Logs.tsx (fuera Outlet/; guardar al
  ir al detalle + skip del primer fetch + acumulado inicial); 
  frontend/src/pages/LogDetalle.tsx (Links Volver con search); 
  frontend/src/hooks/storeListado.ts (nuevo) + storeListado.test.ts (5 tests).
learned: >
  WS/streaming (decisión C): el hook useLogsStream SOLO vive en /logs — al
  abrir el detalle la vista se desmonta y el WS se CIERRA en cleanup (no
  sigue vivo debajo); al volver a /logs se reconecta y ya existe el refetch
  de página 1 al reconectar (E8.30, dedup) — coherente con S8-4/D-C8-5.5
  sin cambios. Vitest 60/60 (55 heredados + 5 del store) y npm run build
  verde (69 módulos). El smoke qa_c8_smoke.sh no se afecta: su única
  interacción con la ruta es el fallback SPA /logs/123 (index.html + assets
  absolutos), idéntico con ruta anidada o hermana; mvn no se toca (cero
  cambios backend). Para la demo C9 (ficha del 07 CONGELADA — anotar como
  matiz visible en la demo, sin reescribir la ficha): el detalle es ahora
  una pantalla dedicada; D-C8-7a queda ratificado a matizado por el
  usuario.