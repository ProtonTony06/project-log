// TopBar (R8.6): marca "Project_Log", nav Logs/Proyectos (NavLink activo con
// bg-stone-100, maqueta 02), slot del indicador de conexión SOLO en /logs
// (R8.29 — C12 recibido por props, nunca aquí el estado), nombre de usuario
// y Cerrar sesión → logout LOCAL (R8.16 sin red — el redirect lo hace App).
// Ancho según página: /logs max-w-7xl; fichas max-w-6xl.
import { NavLink } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";

const linkBase =
  "px-3 py-1.5 rounded-lg text-sm ";
const linkInactivo = "text-stone-500 hover:text-stone-900";
const linkActivo = "font-medium bg-stone-100 text-stone-900";

export function TopBar({
  alCerrarSesion,
  ancho = "max-w-7xl",
}: {
  /** Ancla para el indicador de conexión (portal de /logs — S8-4/R8.28:
   * el span es VACÍO fuera de /logs; EstadoConexion solo se monta allí). */
  alCerrarSesion: () => void;
  ancho?: "max-w-7xl" | "max-w-6xl";
}) {
  const sesion = useAuth();
  return (
    <header className="bg-white border-b border-stone-200 sticky top-0 z-40">
      <div className={`${ancho} mx-auto px-4 h-14 flex items-center gap-6`}>
        <div className="flex items-center gap-2 shrink-0">
          <span className="w-7 h-7 rounded-lg bg-teal-700 text-white hidden sm:flex items-center justify-center font-bold text-sm">
            L
          </span>
          <span className="font-semibold tracking-tight hidden sm:inline">
            Project_Log
          </span>
        </div>
        <nav className="flex items-center gap-1 text-sm">
          <NavLink
            to="/logs"
            className={({ isActive }) =>
              linkBase + (isActive ? linkActivo : linkInactivo)
            }
          >
            Logs
          </NavLink>
          <NavLink
            to="/proyectos"
            className={({ isActive }) =>
              linkBase + (isActive ? linkActivo : linkInactivo)
            }
          >
            Proyectos
          </NavLink>
        </nav>
        <div className="ml-auto flex items-center gap-4 text-sm">
          <span id="indicador-conexion" data-testid="slot-indicador" />
          <span className="text-stone-500 hidden sm:inline">
            {sesion.username ?? ""}
          </span>
          <button
            type="button"
            onClick={alCerrarSesion}
            className="text-stone-500 hover:text-stone-900"
          >
            Cerrar sesión
          </button>
        </div>
      </div>
    </header>
  );
}