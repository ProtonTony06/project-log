package com.projectlog.query.repository;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectlog.auth.LoginController;
import com.projectlog.query.dto.LogDto;
import com.projectlog.query.dto.LogListItemDto;

/**
 * C6 (R6.16–R6.19): ÚNICA fuente de SQL de LECTURA sobre {@code logs}
 * (frontera C5: NEVER añade métodos a {@code ingest.LogsRepository} —
 * INSERT-only; R6.3/R6.18e). Patrón EXACTO {@code ProjectsRepository}
 * (C3–C5): {@code @Repository} + {@code JdbcTemplate}/{@code ObjectMapper}
 * por constructor, estamentos inline de constants
 * (package-private para el pin E6.11 en este MISMO paquete — patrón
 * {@code LogsRepoSqlTest}/D-9), {@code @Transactional} como defensa
 * documentada de mono-sentencia (patrón R3.2).
 *
 * <p>El repo NO valida NADA (los 8 checks viven en el controller — frontera
 * de capas, R6.18b); NINGÚN método toca la tabla {@code projects} (FK
 * CASCADE regla 3); NUNCA {@code queryForObject} en las 2 lecturas (fila-0
 * es el caso LEGAL del 404 — R6.18a). CERO SQL dinámico (E1): el estamento
 * de listado es ÚNICO para TODAS las combinaciones de filtros; la primera
 * página usa EL MISMO estamento con los 3 params del keyset {@code null}.</p>
 */
@Repository
public class LogsQueryRepository {

    /**
     * (C6) Estamento ÚNICO del listado keyset — SQL literal del §1.G
     * (R6.16) con las DOS divergencias mínimas DECLARADAS en
     * <b>D-C6-1</b> del spec (§0.3), CITADAS aquí para el pin E6.11:
     * <ol>
     *   <li>los <b>7 CASTs específicos</b> en las ocurrencias IS-NULL
     *       ({@code bigint}, {@code text}, {@code text}, {@code timestamptz},
     *       {@code timestamptz}, {@code text}, {@code timestamptz}) —
     *       necesarios: PG17 rechaza la inferencia del placeholder sin tipo
     *       en {@code ? IS NULL} ("could not determine data type of
     *       parameter $1", sonda P-2; precedente R-4 de C5: el CAST ENTRA al
     *       literal pinado);</li>
     *   <li>la <b>GUARDA del keyset</b> con el MISMO patrón
     *       {@code (:x IS NULL OR ...)} del congelado — la rama desnuda
     *       evaluaría NULL ante params NULL (primera página, sin cursor) y
     *       EXCLUIRÍA TODAS las filas (sonda P-0; AppFlow flujo 4 paso 1 la
     *       exige existente).</li>
     * </ol>
     * Índices: {@code logs_dashboard_idx} es el cascarón EXACTO de la ruta
     * con project_id (+environment/level) y del orden keyset (índice-scan
     * descendiente SIN sort — V1 líneas 45–47, "el índice maestro del
     * dashboard"); sin proyecto, {@code logs_received_idx} (V1 línea 49)
     * resuelve el orden y la degradación "logs_received_idx + scan
     * filtrado" es "aceptable en v1" POR LITERAL del Schema (§2 índices) —
     * ni migración ni índice nuevo. El trigram GIN de message (V2) queda
     * RESERVADO y comentado en V1: NO se activa en C6. SIN la palabra
     * ESCAPE (S6-5 — el \ es el escape default de PG). 17 placeholders
     * (D-S6-a): 12 de los 6 filtros + cursorR×3 + cursorI×1 + limit×1.
     */
    static final String SELECT_LISTADO =
            "SELECT id, project_id, level, environment, timestamp, received_at,"
                    + " message, source, correlation_id"
                    + " FROM logs"
                    + " WHERE (CAST(? AS bigint) IS NULL OR project_id = ?)"
                    + " AND (CAST(? AS text) IS NULL OR environment = ?)"
                    + " AND (CAST(? AS text) IS NULL OR level = ?)"
                    + " AND (CAST(? AS timestamptz) IS NULL OR received_at >= ?)"
                    + " AND (CAST(? AS timestamptz) IS NULL OR received_at <= ?)"
                    + " AND (CAST(? AS text) IS NULL OR message ILIKE ?)"
                    + " AND (CAST(? AS timestamptz) IS NULL"
                    + " OR (received_at < ? OR (received_at = ? AND id < ?)))"
                    + " ORDER BY received_at DESC, id DESC"
                    + " LIMIT ? + 1";

    /**
     * (C6) Detalle: 11 columnas (las 9 del listado + exception + metadata).
     * 404 por fila-0 → {@code Optional.empty()} del controller; NUNCA
     * {@code queryForObject} aquí (la fila-0 es el caso legal del 404 —
     * patrón C4).
     */
    static final String SELECT_POR_ID =
            "SELECT id, project_id, level, environment, timestamp, received_at,"
                    + " message, exception, metadata, source, correlation_id"
                    + " FROM logs WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;

    public LogsQueryRepository(JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Moneda del listado para el next_cursor (R6.17): log item + instante
     * RAW del received_at (precisión COMPLETA para el cursor — R6.15-c,
     * JAMÁS pasa por el formatter antes del codec) + id.
     */
    public record FilaConsulta(LogListItemDto item, OffsetDateTime received_at,
            long id) {
    }

    /**
     * Listado keyset (R6.17): devuelve la Lista CRUDA de hasta
     * {@code limit + 1} filas (el recorte al {@code limit} y el
     * {@code next_cursor} viven en el controller — D-6.8/R6.18c). Binding
     * POSICIONAL con {@code Object[]} en el orden EXACTO de los **17 `?`**
     * (D-S6-a, contados del literal R6.16): los 6 valores de filtro
     * duplicados en sus 2 posiciones (IS-NULL-CAST / igualdad), luego
     * {@code patronTexto} (los 2 del ILIKE), {@code cursorR} en sus 3
     * posiciones (guard IS NULL + {@code <} + {@code =}),
     * {@code cursorI} en 1 ({@code id < ?} — el contexto de la columna
     * {@code id} infiere el tipo) y {@code limit} al FINAL (el
     * {@code LIMIT ? + 1} es VERBATIM del congelado — sonda P-5; el Java NO
     * suma +1: el {@code + 1} vive EN el literal). El patrón llega YA
     * escapado+envuelto ({@code %needle%} — S6-5). El repo NO traduce
     * errores (patrón R-1).
     */
    @Transactional // defensa documentada (patrón R3.2): mono-sentencia
    public List<FilaConsulta> listar(Long projectId, String environment,
            String level, OffsetDateTime from, OffsetDateTime to,
            String patronTexto, OffsetDateTime cursorR, Long cursorI,
            int limit) {
        Object[] parametros =
                {projectId, projectId, environment, environment, level, level,
                        from, from, to, to, patronTexto, patronTexto, cursorR,
                        cursorR, cursorR, cursorI, limit};
        return jdbcTemplate.query(SELECT_LISTADO, FILA_LISTADO, parametros);
    }

    /**
     * Detalle por id (R6.17): {@code Optional.empty()} → 404 del controller
     * (R6.18c). NUNCA {@code queryForObject} (la fila-0 es el caso LEGAL
     * del 404).
     */
    @Transactional // defensa documentada (patrón R3.2): mono-sentencia
    public Optional<LogDto> porId(long id) {
        List<LogDto> filas = jdbcTemplate.query(SELECT_POR_ID, FILA_DETALLE, id);
        return filas.isEmpty() ? Optional.empty() : Optional.of(filas.get(0));
    }

    /**
     * RowMapper del LISTADO (R6.19 — 2 SEPARADOS, jamás un mapper recortado
     * compartido, R6.14): las 2 fechas del item con el formatter de C2
     * ({@code LoginController.EXPIRES_AT_FORMATTER}, truncado a segundos —
     * reutilizado por REFERENCIA, jamás editado); el received_at RAW (sin
     * formatear) viaja en la FilaConsulta SOLO para el codec.
     */
    private static final RowMapper<FilaConsulta> FILA_LISTADO =
            (rs, numeroFila) -> {
                OffsetDateTime timestamp =
                        rs.getObject("timestamp", OffsetDateTime.class);
                OffsetDateTime receivedAt =
                        rs.getObject("received_at", OffsetDateTime.class);
                return new FilaConsulta(new LogListItemDto(
                        rs.getLong("id"),
                        rs.getLong("project_id"),
                        rs.getString("level"),
                        rs.getString("environment"),
                        timestamp.format(LoginController.EXPIRES_AT_FORMATTER),
                        receivedAt.format(LoginController.EXPIRES_AT_FORMATTER),
                        rs.getString("message"),
                        rs.getString("source"),
                        rs.getString("correlation_id")),
                        receivedAt,
                        rs.getLong("id"));
            };

    /**
     * RowMapper del DETALLE (R6.19): los 9 del listado +
     * {@code rs.getString("exception")} (nullable) + metadata:
     * {@code rs.getString("metadata")} (pgjdbc devuelve el TEXTO del jsonb —
     * cierre R6.10) → {@code null} si la columna es NULL; no-null →
     * {@code objectMapper.readTree(texto)} → JsonNode objeto EXACTO
     * (D-6.5: NUNCA el String crudo, NUNCA {@code readValue(Map.class)}).
     * Un texto JSON malformado es imposible por construcción (la columna
     * jsonb de PG solo guarda JSON válido); de suceder, la RuntimeException
     * sin capturar produce el 500 honesto (política S6 de C4 — nunca se
     * silencia).
     */
    private final RowMapper<LogDto> FILA_DETALLE = (rs, numeroFila) -> new LogDto(
            rs.getLong("id"),
            rs.getLong("project_id"),
            rs.getString("level"),
            rs.getString("environment"),
            rs.getObject("timestamp", OffsetDateTime.class)
                    .format(LoginController.EXPIRES_AT_FORMATTER),
            rs.getObject("received_at", OffsetDateTime.class)
                    .format(LoginController.EXPIRES_AT_FORMATTER),
            rs.getString("message"),
            rs.getString("exception"),
            metadataComoNodo(rs.getString("metadata")),
            rs.getString("source"),
            rs.getString("correlation_id"));

    /** La columna jsonb llega como texto → JsonNode (null si la columna es NULL). */
    private JsonNode metadataComoNodo(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return objectMapper.readTree(texto);
        } catch (IOException e) {
            // Imposible por construcción (jsonb solo guarda JSON válido) —
            // 500 honesto (política S6 de C4): nunca silenciado.
            throw new IllegalStateException("metadata jsonb no parseable", e);
        }
    }
}