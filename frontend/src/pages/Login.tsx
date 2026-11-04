// Login (R8.8–R8.9/E8.7–E8.9, maqueta 01): POST /auth/login SIN Bearer FUERA
// del interceptor (opción bearer:false, D-C8-3); 200 ⇒ guarda token+expires_at
// y navega al DESTINO guardado con query intacta (R8.5/E8.3 — C10 D-C10-11:
// `destinoPropio` se lee ANTES del await y `navegar(destinoPropio, replace)`
// es la ÚNICA navegación post-login — el `<Navigate to="/">` SOLO gana en
// llegadas YA autenticadas via elNavigateGana, fix H-C9-E9.2/REQ-C10-E92-1);
// error: destinoPropio a null; 401 ⇒ inline "✕ Usuario o contraseña
// incorrectos" (sin revelar campo, sin redirect, E8.8); fallo de red ⇒ banner
// "No se puede conectar con el servidor"; botón deshabilitado "Enviando…"
// (anti-doble-submit, E8.9).
import { useRef, useState } from "react";
import type { FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { destinoTrasLogin, borrarDestino, elNavigateGana, obtenerDestino } from "../hooks/useAuth";
import { ApiError } from "../api/client";
import { Banner } from "../components/Banner";

export default function Login() {
  const sesion = useAuth();
  const navegar = useNavigate();
  const location = useLocation();

  const [usuario, setUsuario] = useState("");
  const [password, setPassword] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [credencialesInvalidas, setCredencialesInvalidas] = useState(false);
  const [falloRed, setFalloRed] = useState<string | null>(null);

  // D-C10-11: transición iniciada por enviar() — leída de storage ANTES del
  // await (borrarDestino() de dentro de login() no la toca). En errores se
  // pone a null; tras el éxito el componente se desmonta con la navegación.
  const destinoPropio = useRef<string | null>(null);

  const motivoExpirada =
    (location.state as { motivo?: string } | null)?.motivo === "expirada";

  // R8.3/E8.4: con sesión activa el formulario no se muestra — SOLO en
  // llegadas ya autenticadas (restore/back); la transición propia del login
  // tiene destinoPropio ≠ null ⇒ elNavigateGana false ⇒ UNA sola navegación
  // (la de enviar(), hacia el destino guardado — REQ-C10-E92-1).
  if (elNavigateGana(sesion.estado, destinoPropio.current !== null)) {
    return <Navigate to="/" replace />;
  }

  const enviar = async (e?: FormEvent) => {
    e?.preventDefault();
    if (enviando) return;
    setEnviando(true);
    setCredencialesInvalidas(false);
    setFalloRed(null);
    // El destino lo guardó RequireAuth al desviar (path+query intactos, E8.3);
    // se lee y se fija ANTES del await (D-C10-11/explore B.4).
    const destino = obtenerDestino();
    destinoPropio.current = destinoTrasLogin(destino);
    try {
      await sesion.login(usuario, password);
      navegar(destinoPropio.current, { replace: true });
    } catch (err) {
      destinoPropio.current = null; // higiene: el reintento re-lee el destino
      if (err instanceof ApiError && err.status === 401) {
        // E8.8: mensaje inline; NUNCA se revela qué campo falló (S8-1).
        setCredencialesInvalidas(true);
      } else if (err instanceof ApiError && err.status === 0) {
        setFalloRed("No se puede conectar con el servidor");
      } else {
        setFalloRed(
          err instanceof Error
            ? err.message
            : "No se puede conectar con el servidor",
        );
      }
    } finally {
      setEnviando(false);
      borrarDestino();
    }
  };

  return (
    <main className="min-h-[70vh] flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="bg-white border border-stone-200 rounded-xl shadow-sm p-6">
          <h1 className="text-base font-semibold mb-1">Iniciar sesión</h1>
          <p className="text-sm text-stone-500 mb-5">
            Único administrador (seed inicial). Sin registro público.
          </p>

          {motivoExpirada && (
            <div className="mb-4">
              <Banner variante="neutral" message="Tu sesión ha expirado; vuelve a iniciar sesión." />
            </div>
          )}

          {credencialesInvalidas && (
            <p
              role="alert"
              className="flex items-center gap-2 text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2 mb-4"
            >
              <span aria-hidden="true">✕</span> Usuario o contraseña incorrectos
            </p>
          )}

          {falloRed !== null && (
            <div className="mb-4">
              <Banner
                variante="ambar"
                message={falloRed}
                alReintentar={() => void enviar()}
              />
            </div>
          )}

          <form onSubmit={enviar}>
            <label className="block text-sm font-medium mb-1" htmlFor="user">
              Usuario
            </label>
            <input
              id="user"
              type="text"
              value={usuario}
              autoComplete="username"
              onChange={(e) => setUsuario(e.target.value)}
              className="w-full border border-stone-300 rounded-lg px-3 py-2 text-sm mb-4 focus:outline-none focus:ring-2 focus:ring-teal-600 focus:border-teal-600"
            />

            <label className="block text-sm font-medium mb-1" htmlFor="pass">
              Contraseña
            </label>
            <input
              id="pass"
              type="password"
              value={password}
              placeholder="••••••••••••"
              autoComplete="current-password"
              onChange={(e) => setPassword(e.target.value)}
              className="w-full border border-stone-300 rounded-lg px-3 py-2 text-sm mb-5 focus:outline-none focus:ring-2 focus:ring-teal-600 focus:border-teal-600"
            />

            <button
              type="submit"
              disabled={enviando || usuario === "" || password === ""}
              title="Se habilita con usuario y contraseña"
              className={
                "w-full text-white text-sm font-medium rounded-lg py-2.5 transition-colors " +
                (enviando || usuario === "" || password === ""
                  ? "bg-teal-400 cursor-not-allowed"
                  : "bg-teal-700 hover:bg-teal-800")
              }
            >
              {enviando ? "Enviando…" : "Entrar"}
            </button>
          </form>

          <p className="text-xs text-stone-400 mt-4">
            Sesión con JWT · expira a las 8 h · los intentos no revelan qué campo falló
          </p>
        </div>
      </div>
    </main>
  );
}