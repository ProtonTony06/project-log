// Esqueletos de carga (§4 estados — R8.21 tabla de /logs, R8.37 tarjetas de
// /proyectos): pulsos neutros SIN texto que prometa datos; JAMÁS simulan
// resultado (E8.20).
export function EsqueletoTabla({ filas = 8 }: { filas?: number }) {
  return (
    <div className="bg-white border border-stone-200 rounded-xl overflow-hidden">
      <div className="h-10 bg-stone-50 border-b border-stone-200" />
      <div className="divide-y divide-stone-100" aria-hidden="true">
        {Array.from({ length: filas }, (_, i) => (
          <div key={i} className="flex items-center gap-3 px-4 py-3">
            <div className="h-4 w-16 rounded bg-stone-200 animate-pulse" />
            <div className="h-3 w-40 rounded bg-stone-200 animate-pulse" />
            <div className="h-3 w-24 rounded bg-stone-100 animate-pulse" />
            <div className="h-3 flex-1 rounded bg-stone-100 animate-pulse" />
          </div>
        ))}
      </div>
    </div>
  );
}

export function EsqueletoTarjetas({ tarjetas = 6 }: { tarjetas?: number }) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
      {Array.from({ length: tarjetas }, (_, i) => (
        <div
          key={i}
          aria-hidden="true"
          className="bg-white border border-stone-200 rounded-xl p-4"
        >
          <div className="h-4 w-2/3 rounded bg-stone-200 animate-pulse mb-2" />
          <div className="h-3 w-1/2 rounded bg-stone-100 animate-pulse mb-3" />
          <div className="h-3 w-full rounded bg-stone-100 animate-pulse mb-2" />
          <div className="h-3 w-3/4 rounded bg-stone-100 animate-pulse" />
        </div>
      ))}
    </div>
  );
}