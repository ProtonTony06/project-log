package com.projectlog.query.repository;

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
 * C10 (T10.5, REQ-C10-LOGDEL-1/PURGA-1/5) — pin SQL puro patrón D-9 de los
 * DOS literales de {@code LogsBorradoRepository} (JUnit + Mockito SIN Spring
 * ni BD — constants package-private visibles desde este MISMO paquete
 * {@code query/repository}, patrón {@code LogsQuerySqlTest}/{@code
 * PorIdsSqlPinTest}). Pina:
 * <ul>
 *   <li>(a) {@code SQL_DELETE_POR_ID} = {@code "DELETE FROM logs WHERE id = ?"}
 *       (8b — literal EXACTO del spec §3.3, D-C10-2);</li>
 *   <li>(b) {@code SQL_DELETE_POR_PROYECTO} = {@code "DELETE FROM logs WHERE
 *       project_id = ?"} (8c — el N del retorno ES el {@code deleted: N});</li>
 *   <li>(c) 1 placeholder c/u (sin IN-lists — cero SQL dinámico), sin
 *       sentencias encadenadas, sin sub-SELECTs (mono-sentencia pura);</li>
 *   <li>(d) {@code borrarPorId}/{@code purgarPorProyecto} DELEGAN a SU
 *       literal pinado con el id correcto y devuelven el {@code int} de
 *       {@code update} (0 ⇒ 404 del 8b; el N ES el deleted de la purga);</li>
 *   <li>(e) {@code @Transactional} en ambos — defensa documentada (R3.2);
 *       mono-sentencia.</li>
 * </ul>
 * La EJECUCIÓN real (CASCADE del 8a, key sobrevive con ingesta 202 —
 * REQ-C10-PURGA-2/DEL-5) la certifica el smoke C10 contra PG17.
 */
class LogsBorradoSqlTest {

    private JdbcTemplate jdbcTemplate;

    private LogsBorradoRepository repo;

    @BeforeEach
    void preparar() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repo = new LogsBorradoRepository(jdbcTemplate);
    }

    @Test
    void sqlDeletePorIdPinaElLiteralExactoDel8b() {
        // (a) REQ-C10-LOGDEL-1: UN log, por SU id — literal EXACTO D-C10-2
        assertThat(LogsBorradoRepository.SQL_DELETE_POR_ID)
                .isEqualTo("DELETE FROM logs WHERE id = ?");
        assertThat(LogsBorradoRepository.SQL_DELETE_POR_ID.chars()
                .filter(c -> c == '?').count()).isEqualTo(1L);
        assertThat(LogsBorradoRepository.SQL_DELETE_POR_ID)
                .doesNotContain(";").doesNotContain("projects")
                .doesNotContain("SELECT").doesNotContain("RETURNING");
    }

    @Test
    void sqlDeletePorProyectoPinaElLiteralExactoDel8c() {
        // (b) REQ-C10-PURGA-1/5: TODOS los logs del proyecto — el número de
        // filas del DELETE ES el deleted:N (primer retorno de update del
        // proyecto, gap 6/R1). El índice logs_dashboard_idx (project_id, …)
        // sirve la purga por su PREFIJO — sin migración.
        assertThat(LogsBorradoRepository.SQL_DELETE_POR_PROYECTO)
                .isEqualTo("DELETE FROM logs WHERE project_id = ?");
        assertThat(LogsBorradoRepository.SQL_DELETE_POR_PROYECTO.chars()
                .filter(c -> c == '?').count()).isEqualTo(1L);
        assertThat(LogsBorradoRepository.SQL_DELETE_POR_PROYECTO)
                .doesNotContain(";").doesNotContain("SELECT");
    }

    @Test
    void borrarPorIdDelegaAlLiteralYRetornaElNumeroDeFilas() {
        // (d) ≥1 ⇒ 204 del controller; 0 ⇒ 404 (el log ya no existía)
        when(jdbcTemplate.update(eq(LogsBorradoRepository.SQL_DELETE_POR_ID),
                eq(48211L))).thenReturn(1);
        assertThat(repo.borrarPorId(48211L)).isEqualTo(1);
        verify(jdbcTemplate).update(eq(LogsBorradoRepository.SQL_DELETE_POR_ID),
                eq(48211L));
        verifyNoMoreInteractions(jdbcTemplate); // mono-sentencia: UNA sola ida

        when(jdbcTemplate.update(eq(LogsBorradoRepository.SQL_DELETE_POR_ID),
                eq(999999L))).thenReturn(0);
        assertThat(repo.borrarPorId(999999L)).isZero(); // ⇒ 404, LOGDEL-3
    }

    @Test
    void purgarPorProyectoDelegaAlLiteralYRetornaElDeletedN() {
        // (d) REQ-C10-PURGA-1: el N ES el retorno de JdbcTemplate.update;
        // 0 filas es caso LEGAL (el des-ambiguo N=0 lo resuelve el controller
        // con re-findById — M4/D-C10-2, jamás este repo).
        when(jdbcTemplate.update(
                eq(LogsBorradoRepository.SQL_DELETE_POR_PROYECTO),
                eq(3L))).thenReturn(55);
        assertThat(repo.purgarPorProyecto(3L)).isEqualTo(55);
        verify(jdbcTemplate).update(
                eq(LogsBorradoRepository.SQL_DELETE_POR_PROYECTO), eq(3L));
        verifyNoMoreInteractions(jdbcTemplate); // el proyecto SOBREVIVE — la
        // purga NUNCA toca la fila de projects (REQ-C10-PURGA-2)

        when(jdbcTemplate.update(
                eq(LogsBorradoRepository.SQL_DELETE_POR_PROYECTO),
                eq(9L))).thenReturn(0);
        assertThat(repo.purgarPorProyecto(9L)).isZero(); // estado vacío (M4)
    }

    @Test
    void losDosMetodosLlevanTransactionalDefensaDocumentadaR32()
            throws Exception {
        // (e) ambas mutaciones llevan @Transactional // defensa documentada
        // (R3.2) — mono-sentencia (sin CASCADE aquí: los DELETE son directos)
        Method borrarPorId = LogsBorradoRepository.class
                .getMethod("borrarPorId", long.class);
        Method purgarPorProyecto = LogsBorradoRepository.class
                .getMethod("purgarPorProyecto", long.class);
        assertThat(borrarPorId.isAnnotationPresent(Transactional.class))
                .isTrue();
        assertThat(purgarPorProyecto.isAnnotationPresent(Transactional.class))
                .isTrue();
    }
}