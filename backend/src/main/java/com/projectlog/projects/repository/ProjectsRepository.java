package com.projectlog.projects.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.projectlog.auth.LoginController;
import com.projectlog.projects.dto.ProjectDto;
import com.projectlog.projects.dto.UpdateProjectRequest;

/**
 * {@code ProjectsRepository} (R3.2/R3.4, CD-3.3): clase con
 * {@code JdbcTemplate} y SQL explícito inline (patrón C2) sobre la tabla
 * {@code projects} de {@code V1__init.sql} (INTOCABLE — columnas y
 * constraints literales). Centraliza las consultas porque es pieza
 * compartida downstream (C4 usa {@code findById}; C6 mapea los names desde
 * {@code GET /projects}).
 *
 * <p>{@code @Transactional} queda como <b>defensa documentada</b> (R3.2):
 * las 4 operaciones de C3 son mono-sentencia ({@code INSERT/UPDATE ...
 * RETURNING} — una sola ida y vuelta), así que el autocommit de
 * {@code JdbcTemplate} basta; lo multi-sentencia (batch all-or-nothing,
 * CASCADE) llega en C5/C10.</p>
 *
 * <p>El repo NO valida longitudes ni enum (la validación de borde es del
 * controller, R3.3/CD-3.7): nunca recibe un {@code status} fuera del enum
 * del CHECK ni combina SQL dinámico por filtros; su única "excepción" llega
 * de la BD ({@code projects_name_unique} violada →
 * {@code DuplicateKeyException}, traducida arriba en el controller a 409 —
 * R-1: el garante de la carrera es la constraint, no este código).</p>
 */
@Repository
public class ProjectsRepository {

    /**
     * Única fuente de columnas para las 3 lecturas (CD-3.2): la lista, la
     * ficha y el RETURNING del INSERT/UPDATE. {@code has_key} es DERIVADO en
     * SQL ({@code api_key_hash IS NOT NULL}) — nunca columna, nunca
     * post-cálculo disperso (R3.7; Schema §4 regla 11).
     */
    static final String COLS =
            "id, name, description, status, created_at, updated_at, api_key_prefix, "
                    + "(api_key_hash IS NOT NULL) AS has_key";

    /** CD-3.10: recent-first con desempate por identidad (idioma de logs en V1). */
    private static final String SELECT_TODAS =
            "SELECT " + COLS + " FROM projects ORDER BY created_at DESC, id DESC";

    /** CD-3.3/CD-3.7: el WHERE solo cuando el controller ya validó el valor. */
    private static final String SELECT_FILTRADAS =
            "SELECT " + COLS + " FROM projects WHERE status = ?"
                    + " ORDER BY created_at DESC, id DESC";

    private static final String SELECT_POR_ID =
            "SELECT " + COLS + " FROM projects WHERE id = ?";

    /**
     * CD-3.4/R-1: INSERT DIRECTO sin SELECT previo (check-then-insert
     * descartado) y SIN segunda lectura: el {@code RETURNING} devuelve la
     * fila recién creada con sus DEFAULTs de V1 aplicados
     * ({@code status='ACTIVE'}, {@code created_at/updated_at = now()});
     * nunca menciona columnas de la key ni {@code status} (defaults solos).
     */
    private static final String INSERT_CON_RETORNO =
            "INSERT INTO projects (name, description) VALUES (?, ?) RETURNING " + COLS;

    /** Partes fijas del UPDATE (la rama del SET la elige {@link #conjuntoSet}). */
    static final String SQL_UPDATE_PREFIX = "UPDATE projects SET ";
    static final String SQL_UPDATE_SUFFIX = " WHERE id = ? RETURNING " + COLS;

    /**
     * RowMapper único (CD-3.2, R3.8): {@code TIMESTAMPTZ} llega como
     * {@code OffsetDateTime} y se formatea con el formatter público de C2
     * ({@code LoginController.EXPIRES_AT_FORMATTER}, {yyyy-MM-dd'T'HH:mm:ss'Z'}
     * UTC) — REUTILIZADO POR REFERENCIA, jamás una segunda convención de
     * formateo en este paquete.
     */
    private static final RowMapper<ProjectDto> FILA = (rs, numeroFila) -> new ProjectDto(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class)
                    .format(LoginController.EXPIRES_AT_FORMATTER),
            rs.getObject("updated_at", OffsetDateTime.class)
                    .format(LoginController.EXPIRES_AT_FORMATTER),
            rs.getString("api_key_prefix"),
            rs.getBoolean("has_key"));

    private final JdbcTemplate jdbcTemplate;

    public ProjectsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Lista ARRAY PLANO (R3.4/L10: sin paginación — keyset exclusivo de
     * logs, C6). Con filter → {@code WHERE status = ?} exacto (CD-3.7: el
     * valor ya venía validado fail-fast en el borde); sin filter → todas.
     */
    @Transactional // defensa documentada (R3.2): mono-sentencia
    public List<ProjectDto> findAll(String status) {
        if (status == null) {
            return jdbcTemplate.query(SELECT_TODAS, FILA);
        }
        return jdbcTemplate.query(SELECT_FILTRADAS, FILA, status);
    }

    /** Ficha por id ({@code Optional.empty()} → 404 del controller, CD-3.8). */
    @Transactional
    public Optional<ProjectDto> findById(long id) {
        List<ProjectDto> filas = jdbcTemplate.query(SELECT_POR_ID, FILA, id);
        return filas.isEmpty() ? Optional.empty() : Optional.of(filas.get(0));
    }

    /**
     * Creación (R3.14, CD-3.4): RETURNING en una sola ida — sin
     * {@code GeneratedKeyHolder} ni re-lectura. {@code description} nulo
     * pasa como parámetro NULL en los varargs (el driver lo infiere como
     * {@code text}). La violación de {@code projects_name_unique} NO se
     * captura aquí: el repo no traduce errores (R-1 — el handler 409 vive
     * en el controller).
     */
    @Transactional
    public ProjectDto insert(String name, String description) {
        return jdbcTemplate.queryForObject(INSERT_CON_RETORNO, FILA, name, description);
    }

    /**
     * Edición parcial (R3.6/gate G2, CD-3.5): el SET lo construye
     * {@link #conjuntoSet} solo con los campos presentes del requisito, y el
     * orden de enlazado de parámetros replica EXACTAMENTE el orden de los
     * {@code ?} de esa rama ({@code name, description, status}); {@code id}
     * es el último {@code ?}. {@code updated_at = now()} lo incluye SIEMPRE
     * la rama del SET. La llamada con conjunto vacío se rechaza aquí
     * lanzando {@code IllegalArgumentException} (el controller no invoca
     * {@code update} con {@code {}}, G2).
     */
    @Transactional
    public ProjectDto update(long id, UpdateProjectRequest campos) {
        String set = conjuntoSet(campos.camposPresentes());
        List<Object> parametros = new ArrayList<>();
        if (campos.camposPresentes().contains(UpdateProjectRequest.CAMPO_NAME)) {
            parametros.add(campos.name());
        }
        if (campos.camposPresentes().contains(UpdateProjectRequest.CAMPO_DESCRIPTION)) {
            parametros.add(campos.description());
        }
        if (campos.camposPresentes().contains(UpdateProjectRequest.CAMPO_STATUS)) {
            parametros.add(campos.status());
        }
        parametros.add(id);
        return jdbcTemplate.queryForObject(
                SQL_UPDATE_PREFIX + set + SQL_UPDATE_SUFFIX, FILA, parametros.toArray());
    }

    /**
     * Función pura del SET (CD-3.5, R-4): cadena de {@code if} sobre los
     * 7 combos NO vacíos de {@code {name, description, status}} — sin
     * {@code StringBuilder} frágil. Cada rama SIEMPRE incluye
     * {@code updated_at = now()} (V1 sin trigger: el DEFAULT solo cubre el
     * INSERT) y NINGUNA menciona
     * {@code api_key_hash}/{@code api_key_prefix}/{@code key_created_at}
     * (archivar es SOLO {@code status} — reglas 3/11 del Schema, R3.16).
     * Sin {@code COALESCE}: {@code description = ?} con parámetro {@code null}
     * materializa la limpieza G2 ({@code {"description": null}} → columnas
     * NULL); ante el combo vacío lanza {@code IllegalArgumentException}
     * (contrato "≥1 campo" — el controller nunca llega con vacío).
     */
    static String conjuntoSet(Set<String> presentes) {
        boolean conNombre = presentes.contains(UpdateProjectRequest.CAMPO_NAME);
        boolean conDescripcion = presentes.contains(UpdateProjectRequest.CAMPO_DESCRIPTION);
        boolean conStatus = presentes.contains(UpdateProjectRequest.CAMPO_STATUS);
        if (conNombre && conDescripcion && conStatus) {
            return "name = ?, description = ?, status = ?, updated_at = now()";
        }
        if (conNombre && conDescripcion) {
            return "name = ?, description = ?, updated_at = now()";
        }
        if (conNombre && conStatus) {
            return "name = ?, status = ?, updated_at = now()";
        }
        if (conDescripcion && conStatus) {
            return "description = ?, status = ?, updated_at = now()";
        }
        if (conNombre) {
            return "name = ?, updated_at = now()";
        }
        if (conDescripcion) {
            return "description = ?, updated_at = now()";
        }
        if (conStatus) {
            return "status = ?, updated_at = now()";
        }
        throw new IllegalArgumentException(
                "conjuntoSet exige al menos 1 campo presente (G2: el controller"
                        + " no llama a update con {})");
    }
}