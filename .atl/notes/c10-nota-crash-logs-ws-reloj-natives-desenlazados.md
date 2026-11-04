---
title: "Nota C10 (T10.33): /logs crashea entera — el Reloj por defecto de ClienteWsLogs pasa setTimeout/clearTimeout NATIVOS DESENLAZADOS y el nuevo armado del timer CONNECTING en abrir() (D-C10-7) lanza TypeError: Illegal invocation en la 1.ª conexión WS"
type: hallazgo
date: 2026-11-04
what: >
  T10.32 (corrida 1 del arnés visual C10, ts 20261104155255) terminó 11
  marcas · 6 FAIL · guard 33 falla · exit 1. El diagnóstico (T10.33,
  diagnosticador /tmp con creds en memoria) clavó la causa: la pantalla
  /logs se blanca COMPLETA (main×0, pageerror). Stack del bundle
  index-JKFaOz3p.js (col ~37016): `Bb.abrir → TypeError: Illegal
  invocation` en `this.reloj.setTimeout(...)` — el armado del timer de
  CONNECTING nuevo de T10.20/D-C10-7, PRIMERA sentencia que usa el Reloj en
  la vía feliz. El constructor (client.ts L105, PREVIO a C10) declara
  `reloj: Reloj = { setTimeout, clearTimeout }` — natives del scope global,
  SIN receiver. En Chromium (playwright 1.64.1, chromium-1248 — mismo motor
  que Chrome estable) la llamada desenlazada de ese native lanza
  `TypeError: Illegal invocation` (verificado en vivo con evaluación en
  página: `{setTimeout}` desestructurado + invocación ⇒ THROWS; con
  receiver ⇒ ok; WebSocket ⇒ ok). Antes de C10 el patrón era LATENTE: el
  único uso era `reconectar()` (backoff, línea ~240), jamá alcanzado en
  corridas visuales previas (E9.11 de C9 moría antes, H-C9-WS-COLGADO).
  C10 arma el timer en TODO `abrir()` ⇒ cada montaje de /logs crashea la
  raíz React (sin ErrorBoundary) ⇒ /logs inoperable para usuarios reales;
  además queda un WebSocket creado sin gestión (new WebSocket ocurre antes
  del throw). Efecto en arnés: E9.1/E9.7/E9.10/E9.12/E9.15 timeouts
  (todas esperan contenido de /logs), excepción global en e10
  (Locator.click), L/b7 saltados, 28 checks jamá marcados. Vitest 80/80 no
  lo detecta porque TODOS los tests de ws/client.ts inyectan un Reloj faké
  (la rama del default NUNCA se ejercita con natives reales).
why: >
  Es un defecto de PRODUCTO (no del arnés): el usuario que abre /logs ve
  una página en blanco. La corrección es de una línea en
  frontend/src/wss→ws/client.ts (ej.: arrow wrapper `(fn, ms) =>
  setTimeout(fn, ms)` / bind a window / cancelarTimerApertura ya existente
  comparte el mismo Reloj), pero frontend/src es INTOCABLE para la
  instancia qa del bucle (restricción T10.33) ⇒ FAIL honesto + ESCALADO al
  orquestador. Nada en el arnés maquilla o pasa por alto el crash (guard
  de 33 falla honesto, exit 1).
where: >
  frontend/src/ws/client.ts (L105 default del constructor + L172 armado
  nuevo del timer CONNECTING en abrir(); mesmo padrão en L240 backoff y
  L215 clearTimeout); build servido en
  frontend/dist → bundle http://localhost:8080/assets/index-JKFaOz3p.js
  ({setTimeout,clearTimeout} en col ~36225, crash en col ~37016).
  Reproducción: cualquier navegación a /logs con sesión; diagnosticador
  /tmp/diag_c10.py (creds solo en memoria).
learned: >
  (1) Un Reloj inyectable en producción convive con natives desenlazados:
  en Chromium moderno, extraer setTimeout/clearTimeout del scope global y
  llamarlos sin receiver lanza Illegal invocation — el default del
  constructor debe ENVOLVER (arrow) o BIND, nunca pasar el native tal
  cual. (2) Los tests con reloj faké cubren la semántica pero NO el
  integración del default: una rama constructor-default jamás ejercitada
  es un ciego de tests — el arnés visual es el que lo pilla (valor del
  arnés C9->C10). (3) El defecto era LATENTE desde C8 (Reloj default sin
  uso en vía feliz); C10 lo ACTIVIZA al armar un timer en abrir() — los
  fixes de robustez pueden activar latentes; el re-run honesto del arnés
  existió para pillarlo y lo pilló. (4) El informe b8 del arnés imprime el
  residuo CON texto hardcodeado («PURGADO (E10.10) y ELIMINADO (E10.13)»)
  aunque esos E10.x no corrieron — defecto menor DEL ARNÉS registrado para
  el bucle (no maquilló veredictos: el guard de 33 falló y exit fue 1).
## Resolución (C10, ratificada)

Fix de producto aplicado SOLO en el default del `Reloj` del constructor de
`frontend/src/ws/client.ts` (los natives del scope se ENVUELVEN en arrows
para fijar el receiver — cambia nada del tipo `Reloj` ni de los tests):

```ts
// ANTES
constructor(callbacks: WsCallbacks, reloj: Reloj = { setTimeout, clearTimeout }) {
// DESPUÉS
constructor(
  callbacks: WsCallbacks,
  reloj: Reloj = {
    setTimeout: (fn, ms) => setTimeout(fn, ms),
    clearTimeout: (t) => clearTimeout(t),
  },
) {
```

- Cubre setTimeout Y clearTimeout con el mismo Reloj compartido
  (`cancelarTimerApertura()` usa ese `clearTimeout` — mismo fix por
  partida doble). Comentario en español sobre el constructor.
- **Pin de test** (`client.test.ts`, 81.º test): la rama del default se
  ejercita ahora — instanciar sin `reloj` + `setTimeout(() => {}, 0)` /
  `clearTimeout(id)` con natives reales bajo `.not.toThrow()`.
- **Verificación**: tsc 0 errores; vitest **81/81** (80 previos sin
  regresión + pin); rebuild imagen frontend (`podman compose build
  frontend`) + recreate SOLO frontend (`up -d --force-recreate frontend`)
  → pila healthy (postgres, backend, frontend) y bundle servido nuevo
  **index-DS0Nr1vD.js** (antes index-JKFaOz3p.js) con la firma envuelta
  `setTimeout:(o,m)=>setTimeout(o,m),clearTimeout:o=>c…` verificada por
  grep del bundle.
- Estado posterior: navegación a /logs ya no crashea — el armado del timer
  CONNECTING pasa por la envoltura con receiver fijo. Sin commits ni push
  (pendiente del orquestador).
