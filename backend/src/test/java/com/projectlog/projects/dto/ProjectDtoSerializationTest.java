package com.projectlog.projects.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * E3.25 [L] (CD-3.1/CD-3.2, R3.8, G4): serializar un {@code ProjectDto}
 * produce EXACTAMENTE las claves del Schema §3.3 — {@code id, name,
 * description, status, created_at, updated_at, api_key_prefix, has_key} —
 * <b>sin {@code summary}</b> (C10); {@code description: null} admitido;
 * {@code status} literal {@code "ACTIVE"}; {@code has_key} booleano; y las
 * fechas que el RowMapper entrega ya formateadas llevan sufijo {@code Z}
 * (regex sobre el String — el test fija la forma del record, no el
 * formatter).
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
}