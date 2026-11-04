package com.projectlog.projects.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * E3.25 [L] (CD-3.1/CD-3.2, R3.8, G4; C10 — T10.2): serializar un
 * {@code ProjectDto} produce EXACTAMENTE las claves del Schema §3.3 —
 * {@code id, name, description, status, created_at, updated_at,
 * api_key_prefix, has_key} — y, en las formas PREVIAS a C10, la clave
 * {@code summary} queda <b>AUSENTE</b> (NON_NULL — no es "sin summary"
 * como excepción: en C10 es el comportamiento del record, con summary
 * null ⇒ clave ausente); {@code description: null} admitido;
 * {@code status} literal {@code "ACTIVE"}; {@code has_key} booleano; y las
 * fechas que el RowMapper entrega ya formateadas llevan sufijo {@code Z}
 * (regex sobre el String — el test fija la forma del record, no el
 * formatter).
 *
 * <p>C10 (D-C10-4/G4-a): con {@code conResumen} el objeto completo lleva
 * las 8 claves + {@code summary}; el {@code SummaryDto} serializa SIEMPRE
 * sus 4 claves literales {@code by_level/by_environment/total/last_log_at}
 * (null EXPLÍCITO de {@code last_log_at} — literal §3.3/flujo 9), las fechas
 * formateadas cumplen la regex ISO-Z, y el estado vacío es
 * {@code {}/{} · 0 · null}.</p>
 *
 * <p>JUnit puro con el {@code ObjectMapper} de Jackson: SIN Spring context,
 * SIN BD, SIN contenedor (G4) — corre en segundos.</p>
 */
class ProjectDtoSerializationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ISO_Z_REGEX =
            "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$";

    private static final String[] CLAVES_33 = {
        "id", "name", "description", "status",
        "created_at", "updated_at", "api_key_prefix", "has_key"
    };

    @Test
    void serializaConLasClavesExactasDelSchema33SinSummary() throws Exception {
        ProjectDto dto = new ProjectDto(
                7L, "payments-service", "Ingesta del homelab", "ACTIVE",
                "2026-10-21T10:00:00Z", "2026-10-21T11:30:05Z", null, false);
        JsonNode nodo = MAPPER.readTree(MAPPER.writeValueAsString(dto));

        List<String> claves = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(claves::add);
        assertThat(claves).containsExactlyInAnyOrder(CLAVES_33);
        assertThat(claves).doesNotContain("summary"); // C10: AUSENTE de verdad

        assertThat(nodo.get("id").asLong()).isEqualTo(7L);
        assertThat(nodo.get("name").asText()).isEqualTo("payments-service");
        assertThat(nodo.get("description").asText())
                .isEqualTo("Ingesta del homelab");
        assertThat(nodo.get("status").asText()).isEqualTo("ACTIVE"); // literal
        assertThat(nodo.get("api_key_prefix").isNull()).isTrue(); // C3: null
        assertThat(nodo.get("has_key").isBoolean()).isTrue();
        assertThat(nodo.get("has_key").asBoolean()).isFalse();
    }

    @Test
    void descriptionNullAdmitidoYFechasConSufijoZ() throws Exception {
        ProjectDto dto = new ProjectDto(
                9L, "cert-bot", null, "ARCHIVED",
                "2026-10-19T15:30:52Z", "2026-10-21T09:12:44Z", null, false);
        JsonNode nodo = MAPPER.readTree(MAPPER.writeValueAsString(dto));

        assertThat(nodo.get("description").isNull()).isTrue(); // null admitido
        assertThat(nodo.get("status").asText()).isEqualTo("ARCHIVED");
        assertThat(nodo.get("created_at").asText()).matches(ISO_Z_REGEX);
        assertThat(nodo.get("updated_at").asText()).matches(ISO_Z_REGEX);
    }

    // ==================================================================
    // C10 (T10.2 — D-C10-4/G4-a, REQ-C10-RESUMEN-2/4)
    // ==================================================================

    @Test
    void conResumenElObjetoCompletoLlevaLas8ClavesMasSummaryYSummary4Literales()
            throws Exception {
        ProjectDto base = new ProjectDto(
                7L, "payments-service", "Ingesta del homelab", "ACTIVE",
                "2026-10-21T10:00:00Z", "2026-10-21T11:30:05Z", null, false);
        ProjectDto dto = base.conResumen(new SummaryDto(
                Map.of("ERROR", 12L, "WARN", 3L),
                Map.of("production", 40L), 55L, "2026-10-16T22:31:04Z"));
        JsonNode nodo = MAPPER.readTree(MAPPER.writeValueAsString(dto));

        List<String> claves = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(claves::add);
        assertThat(claves).containsExactlyInAnyOrder(
                "id", "name", "description", "status",
                "created_at", "updated_at", "api_key_prefix", "has_key",
                "summary"); // 9.º componente presente SOLO con include (§3.3)
        List<String> clavesResumen = new ArrayList<>();
        nodo.get("summary").fieldNames().forEachRemaining(clavesResumen::add);
        // 4 claves LITERALES del §3.3 — SIEMPRE (sin NON_NULL interno)
        assertThat(clavesResumen).containsExactlyInAnyOrder(
                "by_level", "by_environment", "total", "last_log_at");
        assertThat(nodo.get("summary").get("by_level").get("ERROR").asLong())
                .isEqualTo(12L);
        assertThat(nodo.get("summary").get("by_environment")
                .get("production").asLong()).isEqualTo(40L);
        assertThat(nodo.get("summary").get("total").asLong()).isEqualTo(55L);
    }

    @Test
    void summaryDeProyectoSinLogsEsMapasVaciosTotalCeroYLastLogAtNullExplicito()
            throws Exception {
        // Literal §3.3/flujo 9 (estado vacío informativo, M4): la forma EXACTA
        // es {"by_level": {}, "by_environment": {}, "total": 0,
        // "last_log_at": null} — null EXPLÍCITO (SummaryDto NO lleva
        // @JsonInclude: sus 4 claves viajan SIEMPRE).
        SummaryDto vacio = new SummaryDto(Map.of(), Map.of(), 0L, null);
        JsonNode nodo = MAPPER.readTree(MAPPER.writeValueAsString(vacio));

        assertThat(nodo.get("by_level").isObject()).isTrue();
        assertThat(nodo.get("by_level").size()).isZero(); // {} — sin claves a 0
        assertThat(nodo.get("by_environment").isObject()).isTrue();
        assertThat(nodo.get("by_environment").size()).isZero();
        assertThat(nodo.get("total").asLong()).isZero();
        assertThat(nodo.get("last_log_at").isNull()).isTrue(); // null EXPLÍCITO
        assertThat(MAPPER.writeValueAsString(vacio)).isEqualTo(
                "{\"by_level\":{},\"by_environment\":{},\"total\":0,"
                        + "\"last_log_at\":null}");
    }

    @Test
    void lastLogAtConDatoCumpleLaRegexIsoZ() throws Exception {
        // REQ-C10-RESUMEN-4: ISO-8601 UTC con sufijo Z (§3.1). El test fija
        // la forma del String YA formateado (el formatter C2 vive en el repo).
        SummaryDto conDato = new SummaryDto(
                Map.of("INFO", 24_118L), Map.of("production", 23_988L),
                25_330L, "2026-10-19T21:47:58Z");
        JsonNode nodo = MAPPER.readTree(MAPPER.writeValueAsString(conDato));
        assertThat(nodo.get("last_log_at").asText()).matches(ISO_Z_REGEX);
        // total = SUMAR de logs del proyecto (long)
        assertThat(nodo.get("total").asLong()).isEqualTo(25_330L);
    }
}