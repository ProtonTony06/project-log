/**
 * Panel Project_Log (C8 — D-C8-2/D-C8-15): AuthProvider + TopBar + las 9
 * rutas 1:1 (R8.1). El provider JSX vive aquí con el valor de useLogicaSesion;
 * el guard RequireAuth aplica E8.2/E8.3 (destino guardado con query intacta).
 * /login es ruta hermana (mayor especificidad que el *) — nunca entra al guard.
 */
import { useEffect } from "react";
import type { ReactNode } from "react";
import {
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  AuthContext,
  guardarDestino,
  interceptar401,
  useLogicaSesion,
} from "./hooks/useAuth";
import { TopBar } from "./components/TopBar";
import { useAuth } from "./hooks/useAuth";
import Login from "./pages/Login";
import Logs from "./pages/Logs";
import LogDetalle from "./pages/LogDetalle";
import Proyectos from "./pages/Proyectos";
import ProyectoDetalle from "./pages/ProyectoDetalle";
import ProyectoNuevo from "./pages/ProyectoNuevo";
import ProyectoEditar from "./pages/ProyectoEditar";
import NotFound from "./pages/NotFound";

/** Guard D-C8-15 (R8.2/E8.2/E8.3): comprobando → SIN render protegido;
 * anonimo → guarda destino (path+search) y /login; autenticado → children. */
function RequireAuth({ children }: { children: ReactNode }) {
  const sesion = useAuth();
  const navegar = useNavigate();
  const location = useLocation();

  // Interceptor 401 GLOBAL ÚNICO (R8.16): descarta sesión local; el redirect
  // aquí, con el destino cubierto por el guard al montar /login.
  useEffect(() => {
    interceptar401(() =>
      navegar("/login", { state: { motivo: "expirada" }, replace: true }),
    );
  }, [navegar]);

  useEffect(() => {
    if (sesion.estado === "anonimo") {
      guardarDestino(location.pathname + location.search);
      navegar("/login", { replace: true });
    }
  }, [sesion.estado, location.pathname, location.search, navegar]);

  if (sesion.estado === "comprobando") {
    return (
      <main className="max-w-6xl mx-auto px-4 py-6 text-sm text-stone-500">
        Comprobando sesión…
      </main>
    );
  }
  if (sesion.estado === "anonimo") return null;
  return <>{children}</>;
}

/** Layout protegido: TopBar + <Outlet/> de la ruta hija casada (R8.4 — TODO
 * lo no conocido, incluido el 404 del panel, vive bajo el guard). */
function Protegidas() {
  const location = useLocation();
  const navegar = useNavigate();
  const sesion = useAuth();

  const esListado =
    location.pathname === "/logs" ||
    location.pathname === "/proyectos" ||
    location.pathname === "/proyectos/nuevo";

  return (
    <div className="min-h-screen bg-stone-50">
      <TopBar
        ancho={esListado ? "max-w-7xl" : "max-w-6xl"}
        alCerrarSesion={() => {
          sesion.logout();
          navegar("/login");
        }}
      />
      <Outlet />
    </div>
  );
}

export default function App() {
  const valor = useLogicaSesion();
  return (
    <AuthContext.Provider value={valor}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<RequireAuth><Protegidas /></RequireAuth>}>
          <Route index element={<Navigate to="/logs" replace />} />
          {/* Ruta ANIDADA (D-C8-7a/E8.35): /logs NO se desmonta al abrir el
              detalle — LogDetalle entra por <Outlet/> y el acumulado queda. */}
          <Route path="logs" element={<Logs />}>
            <Route path=":id" element={<LogDetalle />} />
          </Route>
          {/* /proyectos/nuevo ANTES de /:id (convención de orden de rutas). */}
          <Route path="proyectos" element={<Proyectos />} />
          <Route path="proyectos/nuevo" element={<ProyectoNuevo />} />
          <Route path="proyectos/:id" element={<ProyectoDetalle />} />
          <Route path="proyectos/:id/editar" element={<ProyectoEditar />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </AuthContext.Provider>
  );
}