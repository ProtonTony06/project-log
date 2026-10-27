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