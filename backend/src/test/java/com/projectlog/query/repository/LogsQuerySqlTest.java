package com.projectlog.query.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * E6.11 [L] — pin de los DOS estamentos de C6 (R6.16/D-C6-1): literales
 * EXACTOS byte-a-byte del §1.G. JUnit puro, SIN Spring ni BD (patrón
 * {@code LogsRepoSqlTest}/D-9: los constants son package-private y este
 * test vive en el MISMO paquete {@code query/repository} — sin reflection,
 * sin visibilidad ensanchada). El pin D-S6-a cuanta **17 placeholders**
 * desde el LITERAL (12 de los 6 filtros + cursorR×3 + cursorI×1 + limit×1) —
 * NO la lista de 16 de R6.17 (D-R6.1: implementar 16 con el literal de 17
 * descolgaría el binding con index-out-of-range). La EJECUCIÓN real la
 * certifica el smoke (E6.16–E6.23) contra PG17; la frontera "el repo nunca
 * se llama ante un 400" la pina E6.10 (verifyNoInteractions — mismo ID
 * E6.11 fused del spec).
 */
class LogsQuerySqlTest {

    @Test
    void selectListadoPinaElLiteralExactoDeR616ConDc61() {
        assertThat(LogsQueryRepository.SELECT_LISTADO).isEqualTo(
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
                        + " LIMIT ? + 1");
    }

    @Test
    void selectListadoTieneExactamente17PlaceholdersDc61GuardaY7Casts() {
        String sql = LogsQueryRepository.SELECT_LISTADO;
        // (f) D-S6-a/D-R6.1: 12 (6 filtros duplicados IS-NULL-CAST/igualdad)
        // + cursorR×3 (guard IS NULL + < + =) + cursorI×1 + limit×1 = 17
        assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(17);
        // (b) los 7 CASTs ESPECÍFICOS en sus posiciones IS-NULL (D-C6-1)
        assertThat(sql.split("CAST\\(\\? AS ", -1)).hasSize(8); // 7 + 1
        long castsBigint = sql.split("CAST\\(\\? AS bigint\\)", -1).length - 1;
        long castsText = sql.split("CAST\\(\\? AS text\\)", -1).length - 1;
        long castsTimestamptz =
                sql.split("CAST\\(\\? AS timestamptz\\)", -1).length - 1;
        assertThat(castsBigint).isEqualTo(1);  // project_id
        assertThat(castsText).isEqualTo(3);    // environment, level, patron ILIKE
        assertThat(castsTimestamptz).isEqualTo(3); // from, to, cursorR guard
        // (c) la GUARDA del keyset con el patrón (x IS NULL OR ...) y AMBAS
        // comparaciones (< + = + id <)
        assertThat(sql).contains(
                "AND (CAST(? AS timestamptz) IS NULL"
                        + " OR (received_at < ? OR (received_at = ? AND id < ?)))");
        // (d) ORDER BY keyset
        assertThat(sql).contains("ORDER BY received_at DESC, id DESC");
        // (e) LIMIT ? + 1 VERBATIM (sonda P-5)
        assertThat(sql).endsWith("LIMIT ? + 1");
        // las 6 igualdades de filtro
        assertThat(sql).contains("project_id = ?")
                .contains("environment = ?").contains("level = ?")
                .contains("message ILIKE ?")
                .contains("received_at >= ?").contains("received_at <= ?");
    }

    @Test
    void selectListadoSinEscapeExplicitoYSinFugaDeExceptionNiMetadata() {
        String sql = LogsQueryRepository.SELECT_LISTADO;
        // (h) S6-5: SIN la palabra ESCAPE (el \ es el default de PG)
        assertThat(sql).doesNotContain("ESCAPE");
        // (i) la fuga P5: el LISTADO jamás transporta exception/metadata
        assertThat(sql).doesNotContain("exception").doesNotContain("metadata");
        // y las 9 columnas del listado en orden
        assertThat(sql).startsWith(
                "SELECT id, project_id, level, environment, timestamp, received_at,"
                        + " message, source, correlation_id");
    }

    @Test
    void selectPorIdPinaLas11ColumnasConExceptionYMetadataYWhereId() {
        assertThat(LogsQueryRepository.SELECT_POR_ID).isEqualTo(
                "SELECT id, project_id, level, environment, timestamp, received_at,"
                        + " message, exception, metadata, source, correlation_id"
                        + " FROM logs WHERE id = ?");
        // 11 columnas: exception y metadata SÍ presentes (solo el detalle)
        assertThat(LogsQueryRepository.SELECT_POR_ID).contains("exception")
                .contains("metadata");
        assertThat(LogsQueryRepository.SELECT_POR_ID.chars()
                .filter(c -> c == '?').count()).isEqualTo(1);
    }

    // ==================================================================
    // C10 (T10.6 — D-C10-3, REQ-C10-RESUMEN-3/4): pins de los 3
    // SELECT_RESUMEN_* — literales EXACTOS, GROUP BY + CASE del orden
    // maqueta-04, ORDER BY environment, COUNT(*)+MAX(received_at)
    // ==================================================================

    @Test
    void selectResumenNivelesPinaElLiteralConGroupByYCaseDelOrdenMaqueta04() {
        // literal EXACTO D-C10-3 con el ORDEN maqueta-04 del spec (REQ-C10-UI-6
        // /maqueta 04: «FATAL 1 · ERROR 148 · WARN 63 · DEBUG 1 000 · INFO
        // 24 118» — DEBUG ANTES de INFO: mapeo del CASE FATAL 1 · ERROR 2 ·
        // WARN 3 · DEBUG 4 · INFO 5 · ELSE 6; decisión C10 del orquestador,
        // nota c10-nota-orden-bandas-resumen-case-info-debug.md)
        assertThat(LogsQueryRepository.SELECT_RESUMEN_NIVELES).isEqualTo(
                "SELECT level, COUNT(*) FROM logs WHERE project_id = ? GROUP BY level"
                        + " ORDER BY CASE level WHEN 'FATAL' THEN 1 WHEN 'ERROR' THEN 2"
                        + " WHEN 'WARN' THEN 3 WHEN 'DEBUG' THEN 4 WHEN 'INFO' THEN 5"
                        + " ELSE 6 END");
        // 1 placeholder (cero SQL dinámico — frontera E1/C6)
        assertThat(LogsQueryRepository.SELECT_RESUMEN_NIVELES.chars()
                .filter(c -> c == '?').count()).isEqualTo(1L);
        // GROUP BY + CASE presentes; solo claves PRESENTES el lo materializa
        // el Java (LinkedHashMap) — el SQL no rellena niveles a 0. El DEBUG
        // (4) precede al INFO (5) — orden maqueta-04, NO alfabético
        assertThat(LogsQueryRepository.SELECT_RESUMEN_NIVELES)
                .contains("GROUP BY level").contains("WHEN 'FATAL' THEN 1")
                .contains("WHEN 'DEBUG' THEN 4").contains("WHEN 'INFO' THEN 5")
                .contains("ELSE 6")
                .doesNotContain(";").doesNotContain("COALESCE")
                .doesNotContain("exception");
    }

    @Test
    void selectResumenEntornosPinaElLiteralConOrderByEnvironment() {
        // entornos ALFABÉTICOS (ORDER BY environment — literal D-C10-3)
        assertThat(LogsQueryRepository.SELECT_RESUMEN_ENTORNOS).isEqualTo(
                "SELECT environment, COUNT(*) FROM logs WHERE project_id = ?"
                        + " GROUP BY environment ORDER BY environment");
        assertThat(LogsQueryRepository.SELECT_RESUMEN_ENTORNOS.chars()
                .filter(c -> c == '?').count()).isEqualTo(1L);
        assertThat(LogsQueryRepository.SELECT_RESUMEN_ENTORNOS)
                .contains("GROUP BY environment").contains("ORDER BY environment")
                .doesNotContain("CASE").doesNotContain(";");
    }

    @Test
    void selectResumenTotalesPinaElLiteralCountMaxYSinExceptionMetadata() {
        // TOTALES: total = COUNT(*); last_log_at = MAX(received_at) — con
        // proyecto sin logs devuelve UNA fila con COUNT=0 y MAX=NULL (nunca
        // queryForObject sobre ausencia real — D-C10-3/M4)
        assertThat(LogsQueryRepository.SELECT_RESUMEN_TOTALES).isEqualTo(
                "SELECT COUNT(*), MAX(received_at) FROM logs WHERE project_id = ?");
        assertThat(LogsQueryRepository.SELECT_RESUMEN_TOTALES.chars()
                .filter(c -> c == '?').count()).isEqualTo(1L);
        assertThat(LogsQueryRepository.SELECT_RESUMEN_TOTALES)
                .contains("COUNT(*)").contains("MAX(received_at)")
                .doesNotContain("GROUP BY").doesNotContain("ORDER BY")
                .doesNotContain(";").doesNotContain("exception");
    }
}