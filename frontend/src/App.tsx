/**
 * PLACEHOLDER TEMPORAL del corte C1 (se sustituye por el panel real en C8).
 * NO llama a nada por /api/v1 (R7/R15).
 */
function App() {
  return (
    <main style={{ fontFamily: "sans-serif", margin: "3rem auto", maxWidth: 640 }}>
      <h1>Project_Log — pila OK</h1>
      <p>Corte C1 · Fase 0: setup de pila y repo.</p>
      <p>Versión: 0.1.0</p>
      <p>Links internos (no cargan nada por /api/v1 en este corte):</p>
      <ul>
        <li>
          <a href="/api/v1/nonexistent">/api/v1/nonexistent</a> → 404 JSON de Spring (proxy OK)
        </li>
        <li>WS de prueba: <code>/ws/logs</code> (echo; smoke en scripts/ws_echo_test.py)</li>
      </ul>
    </main>
  );
}

export default App;