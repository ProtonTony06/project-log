package com.projectlog.projects.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import com.projectlog.projects.dto.UpdateProjectRequest;

/**
 * CD-3.5 / CD-3.13.3 ([L] adicional, G4): fija la CONSTRUCCIÓN de la
 * constante SQL del PATCH — la función pura {@code conjuntoSet(Set<String>)}
 * con sus 7 SET literales, el envoltorio
 * {@code UPDATE projects SET … WHERE id = ? RETURNING …} y el rechazo del
 * combo vacío ({@code IllegalArgumentException}, contrato "≥1 campo").
 *
 * <p>JUnit puro: SIN Spring context, SIN {@code JdbcTemplate} real, SIN BD
 * (R3.18 — el unit verifica la CONSTRUCCIÓN de la constante, nunca su
 * ejecución; la ejecución la verifica el smoke en la pila real y el
 * review).</p>
 */
class ProjectsUpdateSqlTest {

    private static final String UPDATED_AT = "updated_at = now()";

    private static final String[] COLUMNAS_DE_KEY = {
        "api_key_hash", "api_key_prefix", "key_created_at"
    };

    @Test
    void comboConUnCampoProduceElSetLiteralDeCd35() {
        assertThat(ProjectsRepository.conjuntoSet(
                Set.of(UpdateProjectRequest.CAMPO_NAME)))
                .isEqualTo("name = ?, updated_at = now()");
        assertThat(ProjectsRepository.conjuntoSet(
                Set.of(UpdateProjectRequest.CAMPO_DESCRIPTION)))
                .isEqualTo("description = ?, updated_at = now()");
        assertThat(ProjectsRepository.conjuntoSet(
                Set.of(UpdateProjectRequest.CAMPO_STATUS)))
                .isEqualTo("status = ?, updated_at = now()");
    }

    @Test
    void comboConDosCamposProduceElSetLiteralDeCd35() {
        assertThat(ProjectsRepository.conjuntoSet(
                Set.of(UpdateProjectRequest.CAMPO_NAME,
                        UpdateProjectRequest.CAMPO_DESCRIPTION)))
                .isEqualTo("name = ?, description = ?, updated_at = now()");
        assertThat(ProjectsRepository.conjuntoSet(
                Set.of(UpdateProjectRequest.CAMPO_NAME,
                        UpdateProjectRequest.CAMPO_STATUS)))
                .isEqualTo("name = ?, status = ?, updated_at = now()");
        assertThat(ProjectsRepository.conjuntoSet(
                Set.of(UpdateProjectRequest.CAMPO_DESCRIPTION,
                        UpdateProjectRequest.CAMPO_STATUS)))
                .isEqualTo("description = ?, status = ?, updated_at = now()");
    }

    @Test
    void comboConLosTresCamposProduceElSetLiteralDeCd35() {
        assertThat(ProjectsRepository.conjuntoSet(
                Set.of(UpdateProjectRequest.CAMPO_NAME,
                        UpdateProjectRequest.CAMPO_DESCRIPTION,
                        UpdateProjectRequest.CAMPO_STATUS)))
                .isEqualTo("name = ?, description = ?, status = ?, updated_at = now()");
    }

    @Test
    void todasLasRamasIncluyenUpdatedAtYNunganTochanColumnasDeKey() {
        // los 7 combos no vacíos de {name, description, status}
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
            assertThat(set).contains(UPDATED_AT); // SIEMPRE refresca (R-4/R3.6)
            for (String columnaDeKey : COLUMNAS_DE_KEY) {
                assertThat(set).doesNotContain(columnaDeKey); // regla 11: la key INTACTA
            }
        }
    }

    @Test
    void elEnvoltorioUpdateUnePrefijoYComboYSufijoLiterales() {
        String sql = ProjectsRepository.SQL_UPDATE_PREFIX
                + ProjectsRepository.conjuntoSet(
                        Set.of(UpdateProjectRequest.CAMPO_STATUS))
                + ProjectsRepository.SQL_UPDATE_SUFFIX;
        assertThat(sql).isEqualTo(
                "UPDATE projects SET status = ?, updated_at = now()"
                        + " WHERE id = ? RETURNING " + ProjectsRepository.COLS);
    }

    @Test
    void lasPartesFijasSonLasLiteralesDeCd35() {
        assertThat(ProjectsRepository.SQL_UPDATE_PREFIX).isEqualTo("UPDATE projects SET ");
        assertThat(ProjectsRepository.SQL_UPDATE_SUFFIX)
                .isEqualTo(" WHERE id = ? RETURNING "
                        + "id, name, description, status, created_at, updated_at, "
                        + "api_key_prefix, (api_key_hash IS NOT NULL) AS has_key");
    }

    @Test
    void elComboVacioSeRechazaConIllegalArgument() {
        assertThatThrownBy(() -> ProjectsRepository.conjuntoSet(Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}