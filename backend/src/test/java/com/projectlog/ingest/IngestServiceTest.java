package com.projectlog.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * E5.18/E5.19/E5.20 [L] (design §13, tasks T5.15): unit puro Mockito SIN
 * Spring — repo mock + {@code List<IngestListener>} de UN mock. Cubre el
 * parse de timestamp (D-11), el received_at UNA vez común al lote (S5-7,
 * ArgumentCaptor sobre el insert) y el seam tras-COMMIT con args exactos y
 * jamás ante 400/rollback (S5-10, D-12).
 */
class IngestServiceTest {

    private static final long PROJECT_ID = 7L;

    private static LogIngestRequest registroValido() {
        return new LogIngestRequest("INFO", "2026-10-16T22:31:04Z", "hola",
                null, null, "development", null, null);
    }

    private static LogValidado filaDe(LogIngestRequest r) {
        return new LogValidado(r.level(), r.environment(),
                IngestService.parsearTimestamp(r.timestamp()), r.message(),
                r.exception(), r.metadata() == null ? null : r.metadata().toString(),
                r.source(), r.correlationId());
    }

    // ------------------------------------------------------------------
    // E5.18 (D-11): ISO_OFFSET_DATE_TIME — Z y offsets SÍ, naive NO
    // ------------------------------------------------------------------

    @Test
    void timestampParseAceptaZyOffsetsYRechazaNaive() {
        assertThat(IngestService.parsearTimestamp("2026-10-16T22:31:04Z"))
                .isEqualTo(OffsetDateTime.parse("2026-10-16T22:31:04Z"));
        assertThat(IngestService.parsearTimestamp("2026-10-16T22:31:04+02:00"))
                .isEqualTo(OffsetDateTime.parse("2026-10-16T22:31:04+02:00"));
        assertThat(IngestService.parsearTimestamp(
                "2026-10-16T22:31:04.123+05:30"))
                .isEqualTo(OffsetDateTime.parse("2026-10-16T22:31:04.123+05:30"));
        // naive → DateTimeParseException → el 400 timestamp_without_timezone
        assertThatThrownBy(() -> IngestService.parsearTimestamp(
                "2026-10-16T22:31:04"))
                .isInstanceOf(DateTimeParseException.class);
    }

    // ------------------------------------------------------------------
    // E5.19 (S5-7): UN insert, UN received_at común, SIEMPRE al seam
    // ------------------------------------------------------------------

    @Test
    void received_atFijadoUnaVezComunAlLote() {
        LogsRepository repo = mock(LogsRepository.class);
        IngestListener oyente = mock(IngestListener.class);
        IngestService service = new IngestService(repo,
                new com.fasterxml.jackson.databind.ObjectMapper(), List.of(oyente));
        List<Long> idsMock = List.of(101L, 102L, 103L);
        when(repo.insert(Mockito.anyLong(), Mockito.anyList(),
                Mockito.any(OffsetDateTime.class))).thenReturn(idsMock);

        Optional<IngestService.ErrorValidacion> resultado = service.registrar(
                PROJECT_ID, List.of(registroValido(), registroValido(),
                        registroValido()), true);

        assertThat(resultado).isEmpty();
        // UNA invocación al insert de TODO el lote (una sentencia, D-4)
        ArgumentCaptor<OffsetDateTime> recibido =
                ArgumentCaptor.forClass(OffsetDateTime.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LogValidado>> filas =
                ArgumentCaptor.forClass(List.class);
        verify(repo, times(1)).insert(Mockito.eq(PROJECT_ID), filas.capture(),
                recibido.capture());
        assertThat(filas.getValue()).hasSize(3);
        assertThat(filas.getValue()).allSatisfy(fila -> assertThat(fila)
                .isEqualTo(filaDe(registroValido())));
        // el MISMO received_at viaja al seam (portado, S5-7)
        ArgumentCaptor<OffsetDateTime> seam =
                ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(oyente, times(1)).onLotePersistido(Mockito.eq(PROJECT_ID),
                seam.capture(), Mockito.eq(idsMock));
        assertThat(seam.getValue()).isEqualTo(recibido.getValue());
    }

    // ------------------------------------------------------------------
    // QP-EX-1 (hueco S5-2): campos de CONFIANZA del servidor falsificados
    // por el cliente JAMÁS se leen ni persisten
    // ------------------------------------------------------------------

    @Test
    void camposDeConfianzaFalsificadosPorElClienteIgnorados() throws Exception {
        // JSON con los 3 falsificados (id / project_id / received_at) y sus
        // variantes camelCase — SÓLO los 8 nombres literales del Schema §3.4
        // se leen (S5-2/CD-3.9): ni el parse ni el insert los ven
        String jsonFalsificado = "{\"level\":\"INFO\","
                + "\"timestamp\":\"2026-10-16T22:31:04Z\",\"message\":\"hola\","
                + "\"environment\":\"development\","
                + "\"project_id\":999,\"received_at\":\"1999-01-01T00:00:00Z\","
                + "\"id\":999,\"projectId\":999,"
                + "\"receivedAt\":\"1999-01-01T00:00:00Z\"}";
        LogIngestRequest leido = LogIngestRequest.desdeJson(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(jsonFalsificado));
        // el record porta SOLO los 8 componentes literales (sin
        // project_id/received_at/id por construcción)
        assertThat(java.util.Arrays.stream(
                        LogIngestRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("level", "timestamp", "message", "exception",
                        "metadata", "environment", "source", "correlationId");
        assertThat(leido).isEqualTo(registroValido());

        LogsRepository repo = mock(LogsRepository.class);
        IngestService service = new IngestService(repo,
                new com.fasterxml.jackson.databind.ObjectMapper(), List.of());
        when(repo.insert(Mockito.anyLong(), Mockito.anyList(),
                Mockito.any(OffsetDateTime.class))).thenReturn(List.of(77L));

        Optional<IngestService.ErrorValidacion> resultado = service.registrar(
                PROJECT_ID, List.of(leido), false);

        assertThat(resultado).isEmpty();
        ArgumentCaptor<Long> projectId =
                ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<OffsetDateTime> recibido =
                ArgumentCaptor.forClass(OffsetDateTime.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LogValidado>> filas =
                ArgumentCaptor.forClass(List.class);
        verify(repo, times(1)).insert(projectId.capture(), filas.capture(),
                recibido.capture());
        // project_id SIEMPRE el del principal (parámetro 7), jamás el 999
        assertThat(projectId.getValue()).isEqualTo(PROJECT_ID);
        // received_at SIEMPRE side-server, jamás el 1999 falsificado
        assertThat(recibido.getValue()).isNotEqualTo(
                OffsetDateTime.parse("1999-01-01T00:00:00Z"));
        assertThat(recibido.getValue()).isAfter(
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        // la fila persistida proviene EXCLUSIVAMENTE de los 8 literales
        assertThat(filas.getValue()).containsExactly(filaDe(leido));
    }

    // ------------------------------------------------------------------
    // E5.20 (S5-10/D-12): el seam tras-COMMIT con args EXACTOS
    // ------------------------------------------------------------------

    @Test
    void seamIngestListenerSoloTrasCommitConArgsExactos() {
        // (a) batch de 3 válido → 1 invocación con los ids EN ORDEN de VALUES
        LogsRepository repo = mock(LogsRepository.class);
        IngestListener oyente = mock(IngestListener.class);
        IngestService service = new IngestService(repo,
                new com.fasterxml.jackson.databind.ObjectMapper(), List.of(oyente));
        List<Long> idsMock = List.of(11L, 12L, 13L);
        when(repo.insert(Mockito.anyLong(), Mockito.anyList(),
                Mockito.any(OffsetDateTime.class))).thenReturn(idsMock);
        service.registrar(PROJECT_ID, List.of(registroValido(), registroValido(),
                registroValido()), true);
        verify(oyente, times(1)).onLotePersistido(Mockito.eq(PROJECT_ID),
                any(OffsetDateTime.class), Mockito.eq(idsMock));

        // (b) individual válido → invocado con lista de 1
        LogsRepository repoIndividual = mock(LogsRepository.class);
        IngestListener oyenteIndividual = mock(IngestListener.class);
        IngestService serviceIndividual = new IngestService(repoIndividual,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                List.of(oyenteIndividual));
        when(repoIndividual.insert(Mockito.anyLong(), Mockito.anyList(),
                Mockito.any(OffsetDateTime.class))).thenReturn(List.of(99L));
        serviceIndividual.registrar(PROJECT_ID, List.of(registroValido()), false);
        verify(oyenteIndividual, times(1)).onLotePersistido(
                Mockito.eq(PROJECT_ID), any(OffsetDateTime.class),
                Mockito.eq(List.of(99L)));

        // (c1) 400 de validación → el listener JAMÁS se invoca (y nada toca
        // la BD de logs: all-or-nothing antes del insert)
        LogsRepository repo400 = mock(LogsRepository.class);
        IngestListener oyente400 = mock(IngestListener.class);
        IngestService service400 = new IngestService(repo400,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                List.of(oyente400));
        Optional<IngestService.ErrorValidacion> fallo = service400.registrar(
                PROJECT_ID, List.of(registroValido(),
                        new LogIngestRequest("error", "2026-10-16T22:31:04Z",
                                "hola", null, null, "development", null, null)),
                true);
        assertThat(fallo).isPresent();
        assertThat(fallo.get().code()).isEqualTo(IngestService.CODE_LEVEL_INVALID);
        assertThat(fallo.get().index()).isEqualTo(1); // 0-based en batch
        verifyNoInteractions(repo400, oyente400);

        // (c2) insert que falla (rollback, D-12) → propagado SIN seam
        LogsRepository repoRollback = mock(LogsRepository.class);
        IngestListener oyenteRollback = mock(IngestListener.class);
        IngestService serviceRollback = new IngestService(repoRollback,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                List.of(oyenteRollback));
        when(repoRollback.insert(anyLong(), anyList(),
                any(OffsetDateTime.class)))
                .thenThrow(new RuntimeException("fallo-aleatorio-de-pila"));
        assertThatThrownBy(() -> serviceRollback.registrar(PROJECT_ID,
                List.of(registroValido()), false))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(oyenteRollback);
    }
}