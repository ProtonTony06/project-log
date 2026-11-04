// Sesión del panel (D4 — R8.7–R8.12 / D-C8-15): ÚNICA fuente de sesión de la
// app y ÚNICO módulo que toca sessionStorage (R8.7 — greps de higiene §9).
// Token + expires_at en sessionStorage con try/catch SIEMPRE (storage
// bloqueado ⇒ fallback a MEMORIA de la pestaña); JAMÁS localStorage ni cookies.
// expires_at es INFORMATIVA (S8-1/R8.9): la validez SIEMPRE la dice GET /me.
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import { asignarBearer, registrarInterceptor401 } from "../api/client";
import { login as loginApi, me } from "../api/panel";
import type { LoginResponse } from "../api/types";

export type EstadoSesion = "comprobando" | "anonimo" | "autenticado";

const CLAVE_TOKEN = "projectlog.panel.token";
const CLAVE_EXPIRA = "projectlog.panel.expires_at";
const CLAVE_DESTINO = "projectlog.panel.destino";

// Fallback a memoria de la pestaña (storage bloqueado/deshabilitado — D4).
const memoria: Record<string, string> = {};

function storageGet(clave: string): string | null {
  try {
    const v = window.sessionStorage.getItem(clave);
    return v ?? memoria[clave] ?? null;
  } catch {
    return memoria[clave] ?? null;
  }
}

function storageSet(clave: string, valor: string): void {
  memoria[clave] = valor;
  try {
    window.sessionStorage.setItem(clave, valor);
  } catch {
    // storage bloqueado: la sesión vive en memoria de la pestaña (E8.10).
  }
}

function storageRemove(clave: string): void {
  delete memoria[clave];
  try {
    window.sessionStorage.removeItem(clave);
  } catch {
    // idem
  }
}

function leerToken(): string | null {
  return storageGet(CLAVE_TOKEN);
}

/** Lectura del token SOLO para el handshake del WS (S8-5): la sesión sigue
 * siendo propiedad de este módulo (R8.7 — sessionStorage ÚNICO aquí). */
export function leerTokenSesion(): string | null {
  return leerToken();
}

/** Guarda token + expires_at (D4) tras un login 200 — INFORMATIVO expires_at. */
export function guardarSesion(r: LoginResponse): void {
  storageSet(CLAVE_TOKEN, r.token);
  storageSet(CLAVE_EXPIRA, r.expires_at);
  asignarBearer(r.token);
}

/** Descarta la sesión LOCAL (R8.10/R8.16): borra storage, Bearer a null,
 * CERO red. El WS se cierra por el desmontaje de /logs + guarda del hook. */
export function descartarSesionLocal(): void {
  storageRemove(CLAVE_TOKEN);
  storageRemove(CLAVE_EXPIRA);
  asignarBearer(null);
}

export function guardarDestino(destino: string): void {
  storageSet(CLAVE_DESTINO, destino);
}

export function obtenerDestino(): string | null {
  return storageGet(CLAVE_DESTINO);
}

export function borrarDestino(): void {
  storageRemove(CLAVE_DESTINO);
}

// ── D-C10-11 (fix H-C9-E9.2): helpers PUROS del arranque post-login ─────
// Extraídos del render de Login para el Vitest SIN DOM (REQ-C10-E92-1/2).
// El `borrarDestino()` de dentro de sesion.login() NO toca la variable ya
// leída (se lee de storage ANTES del await — explore B.4).

/** Destino tras un login exitoso: el destino guardado viaja TAL CUAL,
 * incluida su query (`/proyectos/99999999?text=…` — R8.5/E8.3/R9.2.2);
 * sin destino ⇒ /logs (REQ-C10-E92-2, sin doble salto). */
export function destinoTrasLogin(destinoGuardado: string | null): string {
  return destinoGuardado ?? "/logs";
}

/** Predicate EXACTO del render de Login: el `<Navigate to="/" replace />`
 * SOLO gana en LLEGADAS ya autenticadas (restore/me, back sobre /login —
 * R8.3/E8.4) y NUNCA en la transición propia del login (el envío ya navega
 * a su propio destino con la ref `destinoPropio` — una sola navegación,
 * REQ-C10-E92-1: el arribo post-login PERDURANDO es el destino guardado). */
export function elNavigateGana(
  estado: EstadoSesion,
  transicionPropia: boolean,
): boolean {
  return estado === "autenticado" && !transicionPropia;
}

export interface ValorSesion {
  estado: EstadoSesion;
  username: string | null;
  /** INFORMATIVA (S8-1): JAMÁS decide sesión (la validez la dice /me). */
  expiresAt: string | null;
  /** POST /auth/login sin Bearer (fuera del interceptor): 200 ⇒ guarda y
   * autenticado; 401 ⇒ ApiError inline para la página (E8.8). */
  login(username: string, password: string): Promise<void>;
  /** Logout local (R8.10): sin red; el redirect lo hace el componente. */
  logout(): void;
}

/** Contexto de sesión — el provider JSX vive en App.tsx (D-C8-2/D-C8-15). */
export const AuthContext = createContext<ValorSesion | null>(null);

export function useAuth(): ValorSesion {
  const valor = useContext(AuthContext);
  if (valor === null) {
    throw new Error("useAuth fuera de AuthProvider");
  }
  return valor;
}

/** TODA la lógica de sesión (restore /me antes de renderizar — E8.10,
 * interceptor 401, login, logout). Usada por App.tsx para el provider. */
export function useLogicaSesion(): ValorSesion {
  const [estado, setEstado] = useState<EstadoSesion>("comprobando");
  const [username, setUsername] = useState<string | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);

  // Restauración al montar (E8.10): token en storage ⇒ GET /me ANTES de
  // renderizar nada protegido (el guard no deja pasar con "comprobando").
  useEffect(() => {
    const token = leerToken();
    asignarBearer(token);
    if (token === null) {
      setEstado("anonimo");
      return;
    }
    let vigente = true;
    // me() con 401 pasa por el interceptor (App.tsx) — igual regla R8.14.
    me()
      .then((r) => {
        if (!vigente) return;
        setUsername(r.username);
        setExpiresAt(r.expires_at);
        setEstado("autenticado");
      })
      .catch(() => {
        if (!vigente) return;
        setEstado("anonimo");
      });
    return () => {
      vigente = false;
    };
  }, []);

  const login = useCallback(async (u: string, p: string) => {
    const r = await loginApi(u, p);
    guardarSesion(r);
    setUsername(r.username);
    setExpiresAt(r.expires_at);
    // asignarBearer ya lo hizo panel.login/guardarSesion (D4/D-C8-3).
    borrarDestino();
    setEstado("autenticado");
  }, []);

  const logout = useCallback(() => {
    descartarSesionLocal();
    setUsername(null);
    setExpiresAt(null);
    setEstado("anonimo");
  }, []);

  return { estado, username, expiresAt, login, logout };
}

/** Registro ÚNICO del interceptor 401 (R8.16): descarta la sesión local y
 * deja la redirección al componente (App: navigate("/login", {state:
 * {motivo:"expirada"}}) — D-C8-15). Idempotente por repetición (R8.16). */
export function interceptar401(navegar: () => void): void {
  registrarInterceptor401(() => {
    descartarSesionLocal();
    navegar();
  });
}