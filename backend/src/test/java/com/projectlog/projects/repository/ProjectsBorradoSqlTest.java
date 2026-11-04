package com.projectlog.projects.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * C10 (T10.4, REQ-C10-DEL-5) — pin SQL puro patrón D-9 del literal
 * {@code SQL_BORRAR} (patrón {@code LogsQuerySqlTest}/D-9: JUnit + Mockito
 * SIN Spring ni BD — los constants package-private son visibles desde este
 * MISMO paquete {@code projects/repository}). Pina:
 * <ul>
 *   <li>(a) el LITERAL EXACTO {@code "DELETE FROM projects WHERE id = ?"} —
 *       UNA sentencia, SIN pasos intermedios (sin sub-DELETE de logs, sin
 *       segunda lectura, sin RETURNING): la purga de logs y la muerte de la
 *       key las ejecuta el MOTOR por la FK {@code logs_project_fk
 *       ON DELETE CASCADE} de V1 en la MISMA transacción (D-C10-2);</li>
 *   <li>(b) exactamente 1 placeholder (sin IN-list: cero SQL dinámico —
 *       frontera E1/C6);</li>
 *   <li>(c) el método {@code borrar(long)} DELEGA al literal pinado y mapea
 *       el retorno de {@code update} a {@code boolean}: {@code update == 1}
 *       → {@code true}; {@code update == 0} → {@code false} (⇒ 404 de
 *       carrera del controller);</li>
 *   <li>(d) {@code @Transactional} sobre {@code borrar} — defensa
 *       documentada (R3.2): mono-sentencia.</li>
 * </ul>
 * La EJECUCIÓN real (logs→0, key muere con ingesta → 401
 * {@code api_key_invalid}) la certifica el smoke C10 contra PG17.
 */
class ProjectsBorradoSqlTest {

    private JdbcTemplate jdbcTemplate;

    private ProjectsRepository repo;

    @BeforeEach
    void preparar() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repo = new ProjectsRepository(jdbcTemplate);
    }

    @Test
    void sqlBorrarPinaElLiteralExactoMonoSentencia() {
        // (a) literal EXACTO del spec/design D-C10-2 — REQ-C10-DEL-5:
        // UNA sentencia sin pasos intermedios
        assertThat(ProjectsRepository.SQL_BORRAR)
                .isEqualTo("DELETE FROM projects WHERE id = ?");
        // (b) 1 placeholder EXACTO (sin IN-lists, sin SQL dinámico)
        assertThat(ProjectsRepository.SQL_BORRAR.chars()
                .filter(c -> c == '?').count()).isEqualTo(1L);
        // sin sentencias encadenadas ni sub-DELETEs: la CASCADE es del MOTOR
        assertThat(ProjectsRepository.SQL_BORRAR).doesNotContain(";");
        assertThat(ProjectsRepository.SQL_BORRAR).doesNotContain("logs");
        assertThat(ProjectsRepository.SQL_BORRAR).doesNotContain("SELECT");
        assertThat(ProjectsRepository.SQL_BORRAR).doesNotContain("RETURNING");
    }

    @Test
    void borrarDelegaAlLiteralPinadoYRetornaUpdateIgualAUno() {
        // (c) update == 1 → true (fila existió y se borró, CASCADE del motor)
        when(jdbcTemplate.update(eq(ProjectsRepository.SQL_BORRAR),
                eq(7L))).thenReturn(1);
        assertThat(repo.borrar(7L)).isTrue();
        verify(jdbcTemplate).update(eq(ProjectsRepository.SQL_BORRAR), eq(7L));
        verifyNoMoreInteractions(jdbcTemplate); // mono-sentencia: UNA sola ida
    }

    @Test
    void borrarConCeroFilasRetornaFalseCarrera404() {
        // (c) update == 0 → false (la fila desapareció entre el findById del
        // controller y este DELETE ⇒ 404 de carrera — patrón empty-RETURNING C4)
        when(jdbcTemplate.update(eq(ProjectsRepository.SQL_BORRAR),
                eq(999999L))).thenReturn(0);
        assertThat(repo.borrar(999999L)).isFalse();
        verify(jdbcTemplate).update(eq(ProjectsRepository.SQL_BORRAR),
                eq(999999L));
    }

    @Test
    void borrarLlevaTransactionalDefensaDocumentadaR32() throws Exception {
        // (d) @Transactional sobre borrar — defensa documentada (R3.2):
        // mono-sentencia (la CASCADE es la misma transacción del MOTOR)
        Method borrar = ProjectsRepository.class.getMethod("borrar", long.class);
        assertThat(borrar.isAnnotationPresent(Transactional.class)).isTrue();
    }
}