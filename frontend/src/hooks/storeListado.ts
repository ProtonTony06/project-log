// Store EN MEMORIA del acumulado keyset de /logs (E8.35 post-parche).
//
// Parche post-archive de C8 (matización Q23 de la demo del usuario,
// 2026-10-28, OPCIÓN 2 ratificada): el detalle de log pasa a RUTA HERMANA
// `/logs/:id` (D-C8-8) y /logs SE DESMONTA al abrirlo — ya no hay <Outlet/>.
// Para conservar el requisito E8.35 ("Volver a /logs conserva filtros y
// acumulado") la manera barata y honesta es dividir el estado:
// - Los FILTROS no necesitan store: viven en la query string (D-C8-6, la
//   única fuente) y viajan en la URL del detalle y del retorno. Solo si la
//   entrada al detalle fue directa (sin query) el retorno arranca limpio.
// - El ACUMULADO (items ya cargados + next_cursor) sí se pierde con el
//   desmontaje: este módulo lo TRANSPORTA entre el desmontaje de /logs y
//   el re-montaje del retorno. Es memoria de módulo — una recarga dura
//   (F5) lo borra y /logs arranca limpio reconstruyendo con "Cargar más".
// - Hygiene R8.7/higiene §9: sessionStorage/localStorage siguen siendo
//   propiedad ÚNICA de useAuth — este módulo JAMÁS toca el storage.
import type { Acumulado } from "./usePagination";
import type { LogListItemDto } from "../api/types";

export interface InstantaneaListado {
  /** Query string de /logs al salir hacia el detalle ("" sin filtros). */
  search: string;
  /** Items acumulados + cursor, tal como estaban al pulsar "Detalle". */
  acumulado: Acumulado<LogListItemDto>;
}

let instantanea: InstantaneaListado | null = null;

/** Guarda (o sustituye) la instantánea. Se llama justo antes de navegar a
 * /logs/:id desde el listado. */
export function guardarListado(
  search: string,
  acumulado: Acumulado<LogListItemDto>,
): void {
  instantanea = { search, acumulado };
}

/** Lee SIN consumir (para pruebas). */
export function leerListado(): InstantaneaListado | null {
  return instantanea;
}

/** Consume la instantánea al montar /logs — SOLO si la query string actual
 * coincide con la guardada (retorno legítimo del detalle con filtros). Si
 * no hay instantánea o no coincide (carga directa, TopBar, otra ruta),
 * devuelve null y /logs arranca limpio como siempre (R8.20). */
export function consumirListado(searchActual: string): InstantaneaListado | null {
  const tomada = instantanea;
  instantanea = null;
  return tomada !== null && tomada.search === searchActual ? tomada : null;
}