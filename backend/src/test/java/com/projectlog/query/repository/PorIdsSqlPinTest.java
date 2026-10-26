package com.projectlog.query.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.Connection;
import java.lang.reflect.RecordComponent;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectlog.query.dto.LogDto;

/**
 * E7.19 [L] — pin de {@code SELECT_POR_IDS} + {@code porIds} (R7.24–R7.26,
 * D-7.11): patrón {@code LogsRepoSqlTest} de C6 — JUnit + Mockito puro SIN
 * Spring ni BD: los constants package-private son visibles desde este MISMO
 * paquete {@code query/repository} (sin reflection, sin visibilidad
 * ensanchada). Pina: (a) el LITERAL EXACTO (11 columnas EN ESE orden,
 * {@code WHERE id = ANY (?)}, 1 placeholder, SIN ORDER BY); (b) la firma
 * {@code porIds(List<Long>)} y el record {@code FilaBatch(LogDto,
 * received_at)}; (c) REORDEN en Java con filas DESORDENADAS del mock e ids
 * [3,1,2] → salida [3,1,2] (el SELECT no ordena — nada de array_position);
 * (d) faltantes IGNORADOS sin error ([3,9,1] → [3,1]); (e) el MISMO
 * {@code FILA_DETALLE} sirve al delegante {@code FILA_BATCH} (LogDto
 * completo de 11 + instante RAW de la MISMA fila — render truncado vs RAW
 * con micros); (f) guard defensivo: {@code porIds(null)}/vacía → lista
 * vacía y CERO query (sin tocar la BD).
 */
class PorIdsSqlPinTest {

    private JdbcTemplate jdbcTemplate;

    private LogsQueryRepository repo;

    private static final OffsetDateTime TS =
            OffsetDateTime.parse("2026-10-16T22:31:05.123456Z");

    /** ResultSet mock de UNA fila de SELECT_POR_IDS (metadata/exception null). */
    private static ResultSet fila(Long id) throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getLong("project_id")).thenReturn(7L);
        when(rs.getString("level")).thenReturn("WARN");
        when(rs.getString("environment")).thenReturn("production");
        when(rs.getObject("timestamp", OffsetDateTime.class)).thenReturn(TS);
        when(rs.getObject("received_at", OffsetDateTime.class)).thenReturn(TS);
        when(rs.getString("message")).thenReturn("mensaje-" + id);
        when(rs.getString("exception")).thenReturn(null);
        when(rs.getString("metadata")).thenReturn(null);
        when(rs.getString("source")).thenReturn("test-porids");
        when(rs.getString("correlation_id")).thenReturn("corr-" + id);
        return rs;
    }

    /** Stub del JdbcTemplate: filas del SELECT llegan DESORDENADAS (2,3,1). */
    @BeforeEach
    void preparar() throws SQLException {
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        repo = new LogsQueryRepository(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(
                eq(LogsQueryRepository.SELECT_POR_IDS),
                any(PreparedStatementSetter.class),
                any(RowMapper.class))).thenAnswer(invocacion -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<LogsQueryRepository.FilaBatch> mapper =
                            (RowMapper<LogsQueryRepository.FilaBatch>)
                                    invocacion.getArgument(2);
                    List<LogsQueryRepository.FilaBatch> deLaBd =
                            new ArrayList<>();
                    deLaBd.add(mapper.mapRow(fila(2L), 0));
                    deLaBd.add(mapper.mapRow(fila(3L), 1));
                    deLaBd.add(mapper.mapRow(fila(1L), 2));
                    return deLaBd;
                });
    }

    @Test
    void selectPorIdsPinaElLiteralExactoDeR725() {
        assertThat(LogsQueryRepository.SELECT_POR_IDS).isEqualTo(
                "SELECT id, project_id, level, environment, timestamp, received_at,"
                        + " message, exception, metadata, source, correlation_id"
                        + " FROM logs WHERE id = ANY (?)");
        // 11 columnas EN ESE orden + WHERE id = ANY (?), SIN ORDER BY.
        assertThat(LogsQueryRepository.SELECT_POR_IDS)
                .startsWith("SELECT id, project_id, level, environment, timestamp, received_at,")
                .contains("FROM logs WHERE id = ANY (?)")
                .doesNotContain("ORDER BY")
                .doesNotContain("array_position");
        // 1 placeholder EXACTO (R7.25)
        assertThat(LogsQueryRepository.SELECT_POR_IDS.chars()
                .filter(c -> c == '?').count()).isEqualTo(1);
    }

    @Test
    void firmaPorIdsYRecordFilaBatchPineados() throws Exception {
        // firma pública por uso compilado: porIds(List<Long>) → List<FilaBatch>
        List<LogsQueryRepository.FilaBatch> salida = repo.porIds(List.of(3L));
        assertThat(salida).hasSize(1);
        // record FilaBatch(LogDto log, OffsetDateTime received_at) por
        // reflexión SÓLO de nombres/tipos (sin instanciar con nulls raros)
        assertThat(LogsQueryRepository.FilaBatch.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("log", "received_at");
        assertThat(LogsQueryRepository.FilaBatch.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(LogDto.class, OffsetDateTime.class);
    }

    @Test
    void reordenEnJavaSegunLaListaDeEntradaConFilasDesordenadas() {
        // ids EN ORDEN DEL LOTE [3,1,2]; el mock devuelve filas 2,3,1 —
        // la salida DEBE salir [3,1,2] (reorden en Java — el SELECT no ordena).
        List<LogsQueryRepository.FilaBatch> salida = repo.porIds(List.of(3L, 1L, 2L));
        assertThat(salidaid(salida)).containsExactly(3L, 1L, 2L);
        // mensaje por fila (cada LogDto es de su id — mapping delegante OK)
        assertThat(salida.get(0).log().message()).isEqualTo("mensaje-3");
        assertThat(salida.get(2).log().message()).isEqualTo("mensaje-2");
    }

    private static List<Long> salidaid(List<LogsQueryRepository.FilaBatch> l) {
        return l.stream().map(f -> f.log().id()).toList();
    }

    @Test
    void faltantesIgnoradosSinError() {
        // ids [3,9,1] con el mock SIN la 9 → salida [3,1], sin error.
        List<LogsQueryRepository.FilaBatch> salida = repo.porIds(List.of(3L, 9L, 1L));
        assertThat(salidaid(salida)).containsExactly(3L, 1L);
    }

    @Test
    void elDeleganteFilaBatchSirveElMismoFilaDetalleConInstanteRaw() {
        List<LogsQueryRepository.FilaBatch> salida = repo.porIds(List.of(1L));
        LogsQueryRepository.FilaBatch unico = salida.get(0);
        LogDto log = unico.log();
        // LogDto COMPLETO de 11 campos (pin del delegante: mismo mapping)
        assertThat(log.id()).isEqualTo(1L);
        assertThat(log.project_id()).isEqualTo(7L);
        assertThat(log.level()).isEqualTo("WARN");
        assertThat(log.environment()).isEqualTo("production");
        assertThat(log.timestamp()).isEqualTo("2026-10-16T22:31:05Z"); // C2 truncado
        assertThat(log.received_at()).isEqualTo("2026-10-16T22:31:05Z");
        assertThat(log.message()).isEqualTo("mensaje-1");
        assertThat(log.exception()).isNull();
        assertThat(log.metadata()).isNull();
        assertThat(log.source()).isEqualTo("test-porids");
        assertThat(log.correlation_id()).isEqualTo("corr-1");
        // el instante RAW de la MISMA fila (micros preservados — §1.D):
        // NO el render truncado.
        assertThat(unico.received_at()).isEqualTo(OffsetDateTime.parse(
                "2026-10-16T22:31:05.123456Z"));
    }

    @Test
    void guardDefensivoNullOVaciaCeroQuery() {
        assertThat(repo.porIds(null)).isEmpty();
        assertThat(repo.porIds(List.of())).isEmpty();
        // CERO query (sin tocar la BD — D-7.11)
        verify(jdbcTemplate, never()).query(any(String.class),
                any(PreparedStatementSetter.class), any(RowMapper.class));
    }

    @Test
    void binderUsaCreateArrayOfBigintConElArrayCompleto() throws SQLException {
        List<Long> ids = List.of(3L, 1L, 2L);
        repo.porIds(ids);
        // captura del PreparedStatementSetter + ejecución contra un mock:
        // pin del bind por createArrayOf("bigint", …) (driver PG17 — D-7.11).
        org.mockito.ArgumentCaptor<PreparedStatementSetter> captor =
                ArgumentCaptor.forClass(PreparedStatementSetter.class);
        verify(jdbcTemplate).query(eq(LogsQueryRepository.SELECT_POR_IDS),
                captor.capture(), any(RowMapper.class));
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        Connection cnx = Mockito.mock(Connection.class);
        Array array = Mockito.mock(Array.class);
        when(ps.getConnection()).thenReturn(cnx);
        when(cnx.createArrayOf(eq("bigint"), any())).thenReturn(array);
        captor.getValue().setValues(ps);
        verify(ps).setArray(eq(1), eq(array));
        verify(cnx).createArrayOf(eq("bigint"), eq(ids.toArray(new Long[0])));
    }
}