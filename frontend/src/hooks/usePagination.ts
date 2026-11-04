// Acumulado keyset PURA (D-C8-7/E8.24): el listado ACUMULA páginas con el
// cursor de la última recibida; `next_cursor: null` = fin de resultados.
// Sin DOM; las entradas NUNCA se mutan (React depende de identidad).

export interface Acumulado<T> {
  items: T[];
  nextCursor: string | null;
}

/** Página inicial vacía (estado legítimo sin filtros ≠ 404 ≠ error). */
export function paginaInicial<T>(): Acumulado<T> {
  return { items: [], nextCursor: null };
}

/** Acumula una página: items += página.items (sin recargar desde el
 * principio, R8.23) y guarda el nuevo cursor. */
export function acumular<T>(
  a: Acumulado<T>,
  pagina: { items: T[]; next_cursor: string | null },
): Acumulado<T> {
  return {
    items: [...a.items, ...pagina.items],
    nextCursor: pagina.next_cursor,
  };
}

/** Sustituye el acumulado por la primera página (cambio de filtros /
 * refetch de página 1 al reconectar, D-C8-7b). */
export function primeraPagina<T>(
  pagina: { items: T[]; next_cursor: string | null },
): Acumulado<T> {
  return { items: [...pagina.items], nextCursor: pagina.next_cursor };
}

/** Fin de resultados: next_cursor === null (tras ≥1 página — E8.24). */
export function esFinDeResultados<T>(a: Acumulado<T>): boolean {
  return a.nextCursor === null;
}

/** Expulsa por id la fila borrada (flujo 8b — fix C10 ratificado por el
 * dueño): tras el 204 del DELETE el detalle regresa a /logs con el id en
 * location.state (junto al aviso «Registro eliminado») y esta función lo
 * quita del acumulado restaurado — SIN refetch, coherente con el skip del
 * snapshot E8.35 y con el «delta 0» del WS para borrados (LiveBroadcaster
 * sólo emite con ingesta: no hay canal de DELETE).
 * El cursor keyset NO se toca: codifica received_at/id del último item de
 * la página recibida y la expulsión de una fila no invalida los restantes.
 * Id inexistente en el acumulado ⇒ contenido equivalente (no-op puro).
 * NUNCA muta la entrada (React depende de identidad). */
export function expulsarPorId<T extends { id: number }>(
  a: Acumulado<T>,
  id: number,
): Acumulado<T> {
  return {
    items: a.items.filter((item) => item.id !== id),
    nextCursor: a.nextCursor,
  };
}