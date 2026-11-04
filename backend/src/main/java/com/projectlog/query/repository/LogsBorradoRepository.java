package com.projectlog.query.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * C10 (D-C10-2/D-C10-3): ÚNICA morada de las MUTACIONES del panel sobre
 * {@code logs} — el DELETE 8b ({@code /logs/{id}}) con
 * {@code SQL_DELETE_POR_ID} y la purga 8c ({@code /projects/{id}/logs})
 * con {@code SQL_DELETE_POR_PROYECTO}.
 *
 * <p>NO viven en {@code LogsQueryRepository} (su javadoc declara «ÚNICA
 * fuente de SQL de LECTURA» — frontera C6, R6.18e) NI en
 * {@code ingest.LogsRepository} (INSERT-only por frontera C5); este repo
 * NUEVO aplica los MISMOS patrones: constants package-private para el pin
 * D-9 (el test vive en este MISMO paquete — sin reflection), SQL inline
 * mono-sentencia, {@code @Transactional} como defensa documentada (R3.2).
 * El índice {@code logs_dashboard_idx (project_id, …)} de V1 sirve a la
 * purga por su prefijo — sin migración.</p>
 *
 * <p>El repo NO valida NADA (el orden fail-fast de confirmación vive en el
 * controller — D-C10-1); el retorno {@code int} es el número de filas
 * borradas: el {@code deleted: N} del cuerpo de la purga (primer retorno
 * de {@code JdbcTemplate.update} del proyecto — gap 6) y el discriminante
 * 204/404 del 8b (0 filas ⇒ 404).</p>
 */
@Repository
public class LogsBorradoRepository {

    /** 8b (literal EXACTO del spec, D-C10-2). */
    static final String SQL_DELETE_POR_ID =
            "DELETE FROM logs WHERE id = ?";

    /** 8c (literal EXACTO del spec, D-C10-2). */
    static final String SQL_DELETE_POR_PROYECTO =
            "DELETE FROM logs WHERE project_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public LogsBorradoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 8b — borrado de UN log por id. {@code 0} filas ⇒ 404 del controller
     * (el log ya no existía); {@code ≥ 1} ⇒ 204. El borrado de un log NUNCA
     * toca el proyecto ni su key (REQ-C10-LOGDEL-4).
     */
    @Transactional // defensa documentada (R3.2): mono-sentencia
    public int borrarPorId(long id) {
        return jdbcTemplate.update(SQL_DELETE_POR_ID, id);
    }

    /**
     * 8c — purga de TODOS los logs de un proyecto. CONSERVA el proyecto y
     * su API key (fila de {@code projects} intocada — REQ-C10-PURGA-2); el
     * valor de retorno ES el {@code deleted: N} del cuerpo 200 (determinista
     * ante N=0: el controller re-verifica {@code findById} para
     * desambiguar estado-vacío vs proyecto-inexistente, M4/D-C10-2).
     */
    @Transactional // defensa documentada (R3.2): mono-sentencia
    public int purgarPorProyecto(long projectId) {
        return jdbcTemplate.update(SQL_DELETE_POR_PROYECTO, projectId);
    }
}