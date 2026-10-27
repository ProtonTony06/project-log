// NotFound del PANEL (R8.4/E8.5): 404 real, nunca vacía, nunca redirect
// silencioso. Sin sesión la cubre el guard (RequireAuth desmonta antes).
import { Link } from "react-router-dom";

export default function NotFound() {
  return (
    <main className="max-w-6xl mx-auto px-4 py-6" role="alert">
      <div className="bg-white border border-stone-200 rounded-xl p-8 text-center">
        <p className="mono text-xs text-stone-400 mb-2">HTTP 404</p>
        <h1 className="text-lg font-semibold tracking-tight mb-2">
          404 · Página no encontrada
        </h1>
        <p className="text-sm text-stone-500 mb-5">
          La dirección que has abierto no existe en el panel (o fue retirada).
        </p>
        <Link
          to="/logs"
          className="inline-block bg-teal-700 hover:bg-teal-800 text-white text-sm font-medium rounded-lg px-4 py-2"
        >
          Volver a logs
        </Link>
      </div>
    </main>
  );
}