package com.projectlog.ingest;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * C5 (R5.11-b/c/d, D-4, R-4): ÚNICA fuente de SQL sobre {@code logs} para
 * INSERTS (las LECTURAS nacen en C6 en su corte). {@code @Repository} en el
 * paquete {@code ingest}; patrón de estamentos de piezas de
 * {@code ProjectsRepository} (C3/C4), SIN SQL de lectura, sin paginación y
 * sin {@code PGobject} manual (el {@code CAST(? AS jsonb)} de la V1 hace el
 * trabajo — R-4: un String → jsonb sin cast falla).
 *
 * <p>D-4: la ÚNICA anotación transaccional del corte vive AQUÍ
 * ({@code insert}); el {@code IngestService} NO es transaccional, así que el
 * COMMIT ocurre al RETORNAR {@link #insert} y el seam
 * {@code IngestListener} queda estructuralmente tras-COMMIT (R5.13/S5-10).</p>
 */
@Repository
public class LogsRepository {

    static final String INSERT_LOGS_CABECERA =
            "INSERT INTO logs (project_id, level, environment, timestamp,"
                    + " received_at, message, exception, metadata, source,"
                    + " correlation_id) VALUES ";

    static final String INSERT_LOGS_FILA =
            "(?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)";

    static final String INSERT_LOGS_RETORNO = " RETURNING id";

    private final JdbcTemplate jdbcTemplate;

    public LogsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * C5: INSERT multi-VALUES + RETURNING id (ids en ORDEN de VALUES —
     * moneda del frame batch de C7). metadata con CAST(? AS jsonb);
     * timestamp/received_at viajan como OffsetDateTime (PG driver los envía
     * como TIMESTAMPTZ y PG los normaliza a UTC al guardar — TRD §2.6); los
     * opcionales nulos van como parámetro NULL en los varargs (el driver lo
     * infiere — patrón C3 del description). @Transactional defensa documentada:
     * UNA sentencia = UNA transacción (regla 6 / ADR-11).
     */
    @Transactional
    public List<Long> insert(long projectId, List<LogValidado> filas,
            OffsetDateTime receivedAt) {
        StringBuilder sql = new StringBuilder(INSERT_LOGS_CABECERA);
        for (int i = 0; i < filas.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(INSERT_LOGS_FILA);
        }
        sql.append(INSERT_LOGS_RETORNO);

        Object[] parametros = new Object[filas.size() * 10];
        int destino = 0;
        for (LogValidado fila : filas) {
            // orden EXACTO de columnas / de ? por fila (R5.11-d)
            parametros[destino++] = projectId;
            parametros[destino++] = fila.level();
            parametros[destino++] = fila.environment();
            parametros[destino++] = fila.timestamp();
            parametros[destino++] = receivedAt;
            parametros[destino++] = fila.message();
            parametros[destino++] = fila.exception();
            parametros[destino++] = fila.metadataJson();
            parametros[destino++] = fila.source();
            parametros[destino++] = fila.correlationId();
        }
        return jdbcTemplate.query(sql.toString(),
                (rs, numeroFila) -> rs.getLong("id"), parametros);
    }
}