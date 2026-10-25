package com.projectlog.ingest;

/**
 * C5 (R5.6-a, D-2, B1 del gate Punto 1): el proyecto deducido por la key
 * viaja en el {@code SecurityContext} como principal del
 * {@code UsernamePasswordAuthenticationToken(principal, null, List.of())}
 * que arma {@code IngestGuardFilter} — análogo a {@code JwtPrincipal} del
 * panel. El {@code status} es el dato del gate 3 (403 project_archived del
 * controller, comparación exacta).
 *
 * <p>NADA MÁS en el record: JAMÁS el hash ni la key ({@code ApiKeyService}
 * escribe el hash en BD y lo descarta; el filtro no lo retiene ni lo
 * loguea — higiene R5.15).</p>
 */
public record IngestPrincipal(long projectId, String status) {
}