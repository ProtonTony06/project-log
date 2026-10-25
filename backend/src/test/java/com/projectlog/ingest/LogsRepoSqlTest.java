package com.projectlog.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * E5.15 [L] (INSERT de C5, R5.11-b, R-4): pin de los TRES estamentos de
 * {@code LogsRepository} — literales EXACTOS del spec. JUnit puro, SIN
 * Spring ni BD (patrón {@code ProjectsKeySqlTest} de C3/C4: el unit verifica
 * el LITERAL; la EJECUCIÓN la certifica el smoke de C5 en pila real —
 * E5.21/E5.22/E5.23). Los constants son package-private y este test vive en
 * el MISMO paquete (D-9 del design: sin reflection, sin visibilidad
 * ensanchada).
 */
class LogsRepoSqlTest {

    @Test
    void laCabeceraPinaElLiteralExactoDeR511b() {
        assertThat(LogsRepository.INSERT_LOGS_CABECERA).isEqualTo(
                "INSERT INTO logs (project_id, level, environment, timestamp,"
                        + " received_at, message, exception, metadata, source,"
                        + " correlation_id) VALUES ");
    }

    @Test
    void laFilaPinaElCastJsonbEnLaOctavaPosicion() {
        assertThat(LogsRepository.INSERT_LOGS_FILA).isEqualTo(
                "(?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)");
        // R-4: el CAST es OBLIGATORIO en la 8.ª posición (metadata);
        // 10 placeholders y 10 columnas por fila.
        assertThat(LogsRepository.INSERT_LOGS_FILA.chars()
                .filter(c -> c == '?').count()).isEqualTo(10);
        // el CAST es lo ÚNICO no-placeholder y va tras los 7 primeros ?
        assertThat(LogsRepository.INSERT_LOGS_FILA)
                .startsWith("(?, ?, ?, ?, ?, ?, ?, CAST");
    }

    @Test
    void elRetornoPinaLaFirmaExacta() {
        assertThat(LogsRepository.INSERT_LOGS_RETORNO).isEqualTo(" RETURNING id");
    }

    @Test
    void laCabeceraMencionaLasDiezColumnasDeV1() {
        // (patrón ProjectsKeySqlTest: los sub-literales por separado también)
        assertThat(LogsRepository.INSERT_LOGS_CABECERA).contains(
                "INSERT INTO logs (project_id, level, environment, timestamp,"
                        + " received_at, message, exception, metadata, source,"
                        + " correlation_id) VALUES ");
        // SIN columnas fuera del contrato (received_at es del servidor;
        // id lo genera la BD y no entra en la lista). " id" (con espacio
        // delante) NO aparece en la cabecera — "project_id,"/"correlation_id)"
        // son sub-literales legítimos de las columnas del contrato
        assertThat(LogsRepository.INSERT_LOGS_CABECERA).doesNotContain(" id");
        assertThat(LogsRepository.INSERT_LOGS_CABECERA).doesNotContain(
                "api_key");
    }
}