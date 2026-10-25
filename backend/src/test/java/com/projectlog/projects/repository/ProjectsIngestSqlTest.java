package com.projectlog.projects.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * E5.15 [L] (SELECT del lookup B1, R5.11/R5.11-a): pin del literal EXACTO de
 * {@code SELECT_POR_API_KEY_HASH} — constante package-private de C5 que SOLO
 * es visible desde el paquete {@code projects.repository}; este pin vive
 * JUNTO a {@code ProjectsKeySqlTest} por eso mismo (D-9 del design: la
 * visibilidad del repo del panel no se ensancha con un {@code public}).
 *
 * <p>JUnit puro, SIN Spring ni BD (patrón {@code ProjectsKeySqlTest}: el unit
 * verifica el LITERAL; la EJECUCIÓN la certifica el smoke de C5 en pila real
 * — E5.21 match real → 202 y E5.24 falsificada/revocada → 401, dirección de
 * LECTURA R-2 del bytea).</p>
 */
class ProjectsIngestSqlTest {

    @Test
    void elSelectPorApiKeyHashPinaElLiteralExactoDeR511a() {
        assertThat(ProjectsRepository.SELECT_POR_API_KEY_HASH).isEqualTo(
                "SELECT " + ProjectsRepository.COLS
                        + " FROM projects WHERE api_key_hash = ?");
    }

    @Test
    void elSelectUsaLaProyeccionCOLSinColumnasExtra() {
        // proyección EXACTA con has_key derivado; la tabla y el WHERE literal
        assertThat(ProjectsRepository.SELECT_POR_API_KEY_HASH).contains(
                "FROM projects WHERE api_key_hash = ?");
        assertThat(ProjectsRepository.SELECT_POR_API_KEY_HASH).contains(
                "SELECT id, name, description, status, created_at, updated_at,"
                        + " api_key_prefix, "
                        + "(api_key_hash IS NOT NULL) AS has_key");
        // D-C5-1: el hash NO viaja en la proyección (ni SELECT api_key_hash)
        assertThat(ProjectsRepository.SELECT_POR_API_KEY_HASH).doesNotContain(
                "SELECT api_key_hash");

    }

    @Test
    void elSelectNoEsQueryForObjectYEsReadonlySobreProjects() {
        // sin columnas de escritura (UPDATE/DELETE/INSERT ausentes — solo SELECT)
        assertThat(ProjectsRepository.SELECT_POR_API_KEY_HASH).doesNotContain(
                "UPDATE", "DELETE", "INSERT");
        // la tabla es projects (NO logs — ese SQL vive en ingest/LogsRepository)
        assertThat(ProjectsRepository.SELECT_POR_API_KEY_HASH).contains("FROM projects");
    }
}