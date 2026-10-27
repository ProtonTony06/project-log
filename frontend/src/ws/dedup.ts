// Fusión PURA del frame batch en el acumulado keyset (D-C8-4 / R8.25/E8.28).
// Reglas (verbatim del Schema §3.5 "Deduplicación en el cliente"):
// 1. ids ya presentes en el acumulado (por "Cargar más" o refetch) se IGNORAN;
// 2. los nuevos se fusionan con el acumulado reordenando TODO por el
//    comparador global `received_at DESC, id DESC` — el orden del lote se
//    respeta en el empate de `received_at` (los logs de un batch COMPARTEN
//    received_at; ids consecutivos ⇒ desempate id DESC = orden canónico del
//    SQL ORDER BY received_at DESC, id DESC; D-C8-4);
// 3. NUNCA muta los arrays de entrada (React depende de identidad).
import type { LogListItemDto } from "../api/types";

/** Comparador GLOBAL: received_at DESC (string ISO-8601 UTC con Z y micros —
 * estable porque el backend emite el mismo formato con los micros en el
 * string); empate → id DESC numérico. */
export function comparadorRecibidoDesc(
  a: LogListItemDto,
  b: LogListItemDto,
): number {
  if (a.received_at !== b.received_at) {
    return a.received_at < b.received_at ? 1 : -1;
  }
  return b.id - a.id;
}

export function fusionarEnVivo(
  acumulado: LogListItemDto[],
  lote: LogListItemDto[],
): LogListItemDto[] {
  const presentes = new Set<number>();
  for (const item of acumulado) {
    presentes.add(item.id);
  }

  const nuevos: LogListItemDto[] = [];
  for (const item of lote) {
    if (!presentes.has(item.id)) {
      presentes.add(item.id);
      nuevos.push(item);
    }
  }

  if (nuevos.length === 0) {
    // Dedup total sin cambios: devolvemos el acumulado SIN reordenar
    // (identidad estable; el orden recibido ya era el global).
    return acumulado;
  }

  return [...acumulado, ...nuevos].sort(comparadorRecibidoDesc);
}