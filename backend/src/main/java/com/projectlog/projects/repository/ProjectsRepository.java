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
 * compartida downstream (C4 usa {@code findById} y añade aquí los 2
 * estamientos ADITIVOS de key — R4.2/R4.3; C6 mapea los names desde
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

    /**
     * C4 (R4.3, literal EXACTO del spec): (1) generar/regenerar key —
     * mono-sentencia; el {@code RETURNING key_created_at} alimenta la 201
     * ({@code created_at}). Los 3 campos de key van JUNTOS (invariante del
     * Schema §2) y el digest del hash viaja ya calculado (escritura CIEGA:
     * este repo no calcula ni re-hashea nada). Package-private por el pin
     * E4.13 de este mismo paquete (mismo patrón que {@link #COLS} de C3).
     */
    static final String UPDATE_REGENERAR_KEY =
            "UPDATE projects"
                    + " SET api_key_hash = ?, api_key_prefix = ?,"
                    + " key_created_at = now(), updated_at = now()"
                    + " WHERE id = ?"
                    + " RETURNING key_created_at";

    /**
     * C4 (R4.3, literal EXACTO del spec): (2) revocar — los tres a NULL
     * JUNTOS (invariante Schema §2); fila 0 → {@code false} → 404 del
     * controller. El WHERE es {@code id = ?} a SECAS (cierre S1 del spec de
     * C4: SIN {@code api_key_hash IS NOT NULL} — el revoke es IDEMPOTENTE y
     * el {@code updated_at} avanza también en un no-op lógico). Package-
     * private por el pin E4.13 de este mismo paquete.
     */
    static final String UPDATE_REVOCAR_KEY =
            "UPDATE projects"
                    + " SET api_key_hash = NULL, api_key_prefix = NULL,"
                    + " key_created_at = NULL, updated_at = now()"
                    + " WHERE id = ?"
                    + " RETURNING id";

    /**
     * C4 (R4.4): generar/regenerar key (es el MISMO endpoint), con 404 por
     * empty-RETURNING — SIN pre-SELECT (una sola ida; seguro ante carreras:
     * el UPDATE sobre id inexistente es no-op y no escribe). Con {@code List}
     * (NUNCA {@code queryForObject}: la fila-0 es el caso legal del 404).
     * El formateo de {@code key_created_at} vive AQUÍ (cierre S4: el repo
     * formatea las fechas — formatter público de C2, reutilizado por
     * referencia — y el controller nunca toca fechas).
     *
     * @param id    id del proyecto (previamente leído por el controller con
     *              {@link #findById} — rama 404/409 ANTES de generar la key)
     * @param hash  digest SHA-256 binario (32 bytes) de la key COMPLETA
     * @param p     los primeros 10 chars de la key ({@code api_key_prefix})
     * @return {@code Optional} del {@code key_created_at} recién escrito en
     *         formato ISO-8601 UTC {@code Z}; vacío si el id no existe (→ 404)
     */
    @Transactional // defensa documentada (R3.2): mono-sentencia
    public Optional<String> regenerateKey(long id, byte[] hash, String p) {
        List<OffsetDateTime> filas = jdbcTemplate.query(UPDATE_REGENERAR_KEY,
                (rs, numeroFila) -> rs.getObject("key_created_at", OffsetDateTime.class),
                hash, p, id);
        return filas.isEmpty() ? Optional.empty()
                : Optional.of(filas.get(0).format(LoginController.EXPIRES_AT_FORMATTER));
    }

    /**
     * C4 (R4.4, cierre S1): revocar — los TRES campos de key a NULL juntos
     * (invariante Schema §2), idempotente (también {@code true} cuando el
     * proyecto ya estaba sin key — Decisión 2 del gate de C4; la fila SÍ se
     * reescribe: {@code updated_at} avanza). 404 por empty-RETURNING, sin
     * pre-SELECT. SIN chequeo de {@code status} (Decisión 3 REVISADA:
     * permitido también sobre ARCHIVADO). NUNCA {@code queryForObject}.
     *
     * @return {@code true} si la fila existió (→ 204 del controller);
     *         {@code false} si el id no existe (→ 404)
     */
    @Transactional
    public boolean revokeKey(long id) {
        List<Long> filas = jdbcTemplate.query(UPDATE_REVOCAR_KEY,
                (rs, numeroFila) -> rs.getLong("id"), id);
        return !filas.isEmpty();
    }
}