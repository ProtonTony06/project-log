package com.projectlog.projects.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import com.projectlog.projects.dto.UpdateProjectRequest;

/**
 * E4.13 [L] (cierre S1 del spec C4 + frontera R4.16a heredada): pin de los
 * DOS estamientos NUEVOS de key de {@link ProjectsRepository} — literales
 * EXACTOS de R4.3.
 *
 * <p>JUnit puro, SIN Spring ni BD (patrón {@code ProjectsUpdateSqlTest} de
 * C3: el unit verifica la CONSTRUCCIÓN/contenido del literal; la EJECUCIÓN
 * la verifica el smoke de C4 en pila real — psql E4.19/E4.20/E4.22/E4.24).
 * Divergencia D-3 preservada (design §7/D-R5): las constantes son
 * package-private y este test vive en el MISMO paquete, sin reflection.</p>
 */
class ProjectsKeySqlTest {

    private static final String UPDATED_AT = "updated_at = now()";

    private static final String[] COLUMNAS_DE_KEY = {
        "api_key_hash", "api_key_prefix", "key_created_at"
    };

    // ------------------------------------------------------------------
    // (1) UPDATE_REGENERAR_KEY — literales exactos de R4.3
    // ------------------------------------------------------------------

    @Test
    void updateRegenerarKeyPinaElLiteralExactoDeR43() {
        assertThat(ProjectsRepository.UPDATE_REGENERAR_KEY).isEqualTo(
                "UPDATE projects"
                        + " SET api_key_hash = ?, api_key_prefix = ?,"
                        + " key_created_at = now(), updated_at = now()"
                        + " WHERE id = ?"
                        + " RETURNING key_created_at");
        // los tres sub-literales del E4.13, por separado también:
        assertThat(ProjectsRepository.UPDATE_REGENERAR_KEY).contains(
                "SET api_key_hash = ?, api_key_prefix = ?,"
                        + " key_created_at = now(), updated_at = now()");
        assertThat(ProjectsRepository.UPDATE_REGENERAR_KEY).contains("WHERE id = ?");
        assertThat(ProjectsRepository.UPDATE_REGENERAR_KEY).contains("RETURNING key_created_at");
    }

    // ------------------------------------------------------------------
    // (2) UPDATE_REVOCAR_KEY — literales exactos de R4.3, WHERE a secas (S1)
    // ------------------------------------------------------------------

    @Test
    void updateRevocarKeyPinaElLiteralExactoYElWhereASecas() {
        assertThat(ProjectsRepository.UPDATE_REVOCAR_KEY).isEqualTo(
                "UPDATE projects"
                        + " SET api_key_hash = NULL, api_key_prefix = NULL,"
                        + " key_created_at = NULL, updated_at = now()"
                        + " WHERE id = ?"
                        + " RETURNING id");
        assertThat(ProjectsRepository.UPDATE_REVOCAR_KEY).contains(
                "SET api_key_hash = NULL, api_key_prefix = NULL,"
                        + " key_created_at = NULL, updated_at = now()");
        assertThat(ProjectsRepository.UPDATE_REVOCAR_KEY).contains("WHERE id = ? RETURNING id");
        // cierre S1: SIN api_key_hash IS NOT NULL en el WHERE
        assertThat(ProjectsRepository.UPDATE_REVOCAR_KEY).doesNotContain("IS NOT NULL");
        // solo columnas de key + updated_at: NINGUNA columna de contenido.
        // (el substring " created_at" con espacio excluye created_at de C1 sin
        // colisionar con key_created_at, que va INTACTO por orden del S1)
        assertThat(ProjectsRepository.UPDATE_REVOCAR_KEY).doesNotContain("name");
        assertThat(ProjectsRepository.UPDATE_REVOCAR_KEY).doesNotContain("description");
        assertThat(ProjectsRepository.UPDATE_REVOCAR_KEY).doesNotContain("status");
        assertThat(ProjectsRepository.UPDATE_REVOCAR_KEY).doesNotContain(" created_at");
    }

    // ------------------------------------------------------------------
    // Frontera R4.16a (re-anclado heredado de E3.19/R-4): conjuntoSet
    // SIGUE sin mencionar columnas de key; el UPDATE de C3 INTACTO
    // ------------------------------------------------------------------

    @Test
    void conjuntoSetSigueSinMencionarColumnasDeKey() {
        Set<String>[] combos = new Set[] {
            Set.of(UpdateProjectRequest.CAMPO_NAME),
            Set.of(UpdateProjectRequest.CAMPO_DESCRIPTION),
            Set.of(UpdateProjectRequest.CAMPO_STATUS),
            Set.of(UpdateProjectRequest.CAMPO_NAME, UpdateProjectRequest.CAMPO_DESCRIPTION),
            Set.of(UpdateProjectRequest.CAMPO_NAME, UpdateProjectRequest.CAMPO_STATUS),
            Set.of(UpdateProjectRequest.CAMPO_DESCRIPTION, UpdateProjectRequest.CAMPO_STATUS),
            Set.of(UpdateProjectRequest.CAMPO_NAME,
                    UpdateProjectRequest.CAMPO_DESCRIPTION,
                    UpdateProjectRequest.CAMPO_STATUS)
        };
        for (Set<String> combo : combos) {
            String set = ProjectsRepository.conjuntoSet(combo);
            for (String columnaDeKey : COLUMNAS_DE_KEY) {
                assertThat(set).doesNotContain(columnaDeKey); // regla 11: key INTACTA
            }
        }
    }

    @Test
    void elUpdateDeC3SigueSiendoElLiteralDelCorteAnterior() {
        // el envoltorio C3 no cambió NI UNA línea (R4.2) y sigue SIN columnas de key
        String sql = ProjectsRepository.SQL_UPDATE_PREFIX
                + ProjectsRepository.conjuntoSet(
                        Set.of(UpdateProjectRequest.CAMPO_STATUS))
                + ProjectsRepository.SQL_UPDATE_SUFFIX;
        assertThat(sql).isEqualTo(
                "UPDATE projects SET status = ?, updated_at = now()"
                        + " WHERE id = ? RETURNING " + ProjectsRepository.COLS);
        // el prefijo de escritura (lo ÚNICO que puede fijar columnas) no menciona keys;
        // el SUFFIX contiene api_key_* solo en los DERIVADOS de lectura (COLS) de C3
        assertThat(ProjectsRepository.SQL_UPDATE_PREFIX)
                .doesNotContain("api_key", "key_created_at");
    }
}