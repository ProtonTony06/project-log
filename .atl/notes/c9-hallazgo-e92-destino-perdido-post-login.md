---
title: "H-C9-E9.2 — el destino recordado por el guard se PIERDE tras el login (aterriza siempre en /logs)"
type: bugfix
what: >
  R9.2.2 ("login → regresa a /proyectos/:id — destino cumplido") NO se cumple
  en el producto: tras el login iniciado desde el guard, la app aterriza
  SIEMPRE en /logs, no en el destino recordado. Confirmado determinista
  (repro x3 + instrumentación de history en corrida 6). En la corrida 6
  E9.2 quedó PASS-CON-NOTA porque el arnés validó un TRANSITORIO
  (wait_for_url pilla /proyectos/99999999 unos ms) — corregir la
  observación y el veredicto queda FAIL honesto de PRODUCTO.
why: >
  Doble navegación en Login.tsx que pisa el destino: enviar() llama
  navegar(destino ?? "/logs", {replace:true}) tras sesion.login(), PERO el
  flip a `autenticado` re-renderiza Login ANTES de desmontarse y su
  render condicional `if (sesion.estado === "autenticado") return
  <Navigate to="/" replace />` (líneas 31-33) dispara una SEGUNDA
  navegación 1 ms después. Traza instrumentada (history.replaceState
  envuelto, repro /tmp/qa_c9/repro_e92c.py):
    rep '/proyectos/99999999' @200ms  ← navegar(destino) de enviar()
    rep '/'                   @201ms  ← Navigate to="/" de Login (gana)
    rep '/logs'               @203ms  ← index-redirect <Navigate to="/logs"/>
  El destino solo sobrevive en sessionStorage (borrarDestino() corre
  después); el ARRIBO real es /logs en 3/3 repros y en las corridas 4, 5 y 6
  ([pantalla] E9.2 · http://localhost:8080/logs en todas).
where: >
  frontend/src/pages/Login.tsx (líneas 31-33 el Navigate condicional que
  gana; navegar(destino) en enviar() es código muerto en la práctica),
  frontend/src/App.tsx (index redirect "/": Navigate to="/logs"),
  frontend/src/hooks/useAuth.ts (login(): setEstado("autenticado") antes de
  que Login se desmonte). Contrato violado: R8.5/E8.3 (destino con query
  intacta) y R9.2.2 (regreso al destino). Los tests unitarios E8.x de C8
  usan useNavigate MOCKEADO — no ven esta carrera de navegación real.
learned: >
  (1) Un wait_for_url de Playwright valida TRANSITORIOS: en flujos con doble
  navegación encadenada el arribo se afirma con la URL PERDURANDO (poll
  tras ~700 ms). (2) El patrón «render condicional que navega» + «navigate
  imperativo en el submit» en el MISMO componente es una carrera de
  navegación: gana el render condicional (mismo batch, orden de React).
  (3) Corrección arnés acotada pendiente en E9.2 (observar el arribo
  enduredido); la corrección de PRODUCTO es del orquestador/C10 — p. ej.
  eliminar el Navigate condicional de Login (el envío ya navega) o
  memorizar el render.
---