package com.projectlog.query.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * E6.2 [L] — pin de los DOS records (R6.9/R6.10, P5/D1 del gate): patrón
 * {@code ProjectDtoSerializationTest} (JUnit puro, SIN Spring). LogListItemDto
 * con EXACTAMENTE los 9 componentes (nombres literales §3.3) — la fuga de
 * {@code exception}/{@code metadata} en la LISTA es IMPOSIBLE de compilar
 * (el pin lo afirma estructuralmente: no existe el campo); LogDto con
 * EXACTAMENTE los 11 — {@code metadata} DE TIPO {@code JsonNode} (pin del
 * tipo — cierre D-S6-c). Serialización: claves correctas y, con valores
 * null, los nombres siguen APARECIENDO en el JSON (default Jackson —
 * R6.11/R6.13; sin config extra en application.yml).
 */
class DtosLogsPinTest {

    private static final List<String> NOMBRES_LISTADO = List.of(
            "id", "project_id", "level", "environment", "timestamp",
            "received_at", "message", "source", "correlation_id");

    private static List<String> componentes(Class<?> tipo) {
        List<String> nombres = new ArrayList<>();
        for (var componente : tipo.getRecordComponents()) {
            nombres.add(componente.getName());
        }
        return nombres;
    }

    @Test
    void logListItemDtoTieneExactamenteLos9ComponentesLiterales() {
        assertThat(LogListItemDto.class.getRecordComponents()).hasSize(9);
        assertThat(componentes(LogListItemDto.class))
                .containsExactlyElementsOf(NOMBRES_LISTADO);
        // (a): en orden EXACTO del spec
        assertThat(componentes(LogListItemDto.class).get(1)).isEqualTo("project_id");
        assertThat(componentes(LogListItemDto.class).get(8)).isEqualTo("correlation_id");
    }

    @Test
    void laFugaDeExceptionYMetadataEnLaListaEsImposibleDeCompilar() {
        // R6.9/P5: el list-item NO declara los campos — el pin lo afirma
        // estructuralmente (no existe el accessor ni el componente).
        assertThat(componentes(LogListItemDto.class))
                .doesNotContain("exception", "metadata");
        for (var componente : LogListItemDto.class.getRecordComponents()) {
            assertThat(componente.getName()).isNotIn(List.of("exception", "metadata"));
        }
    }

    @Test
    void logDtoTieneExactamenteLos11ComponentesConMetadataJsonNode() {
        assertThat(LogDto.class.getRecordComponents()).hasSize(11);
        assertThat(componentes(LogDto.class)).startsWith(NOMBRES_LISTADO.get(0))
                .containsSubsequence(NOMBRES_LISTADO);
        var tipos = new ArrayList<String>();
        for (var componente : LogDto.class.getRecordComponents()) {
            tipos.add(componente.getType().getSimpleName());
        }
        assertThat(tipos).containsSubsequence("String", "JsonNode"); // exception, metadata
        // pin del tipo de metadata (R6.10 — cierre D-S6-c)
        assertThat(LogDto.class.getRecordComponents()[8].getType())
                .isEqualTo(com.fasterxml.jackson.databind.JsonNode.class);
        // exception es String
        assertThat(LogDto.class.getRecordComponents()[7].getType())
                .isEqualTo(String.class);
    }

    @Test
    void serializacionEmiteLasClavesExactasYLosNullsSinOmitirse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogListItemDto item = new LogListItemDto(1L, 5L, "ERROR", "production",
                "2026-10-25T09:12:34Z", "2026-10-25T09:12:34Z", "m", null, null);
        List<String> claves = new ArrayList<>();
        mapper.readTree(mapper.writeValueAsString(item))
                .fieldNames().forEachRemaining(claves::add);
        assertThat(claves).containsExactlyElementsOf(NOMBRES_LISTADO);

        // LogDto con exception/metadata NULOS: los 2 nombres siguen en el JSON
        LogDto nulo = new LogDto(2L, 5L, "INFO", "development",
                "2026-10-25T09:12:35Z", "2026-10-25T09:12:35Z", "m2", null, null,
                null, null);
        String json = mapper.writeValueAsString(nulo);
        assertThat(json).contains("\"exception\":null").contains("\"metadata\":null")
                .contains("\"source\":null").contains("\"correlation_id\":null");

        // LogDto con valores: exception sale tal cual, metadata es un OBJETO
        LogDto completo = new LogDto(3L, 5L, "ERROR", "production",
                "2026-10-25T09:12:36Z", "2026-10-25T09:12:36Z", "m3",
                "java.lang.NullPointerException", mapper.readTree(
                        "{\"span\":\"checkout\",\"attempt\":2}"),
                "src", "corr");
        var nodo = mapper.readTree(mapper.writeValueAsString(completo));
        assertThat(nodo.get("exception").asText()).isEqualTo("java.lang.NullPointerException");
        assertThat(nodo.get("metadata").get("span").asText()).isEqualTo("checkout");
        assertThat(nodo.get("metadata").get("attempt").asInt()).isEqualTo(2);
        assertThat(nodo.get("metadata").isTextual()).isFalse(); // NUNCA el String crudo
    }

    @Test
    void logsPaginaResponseEmiteItemsYNextCursorConNullExplicito() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var pagina = new LogsPaginaResponse(List.of(), null);
        String json = mapper.writeValueAsString(pagina);
        var nodo = mapper.readTree(json);
        assertThat(nodo.has("items")).isTrue();
        // R6.11: la clave EXPLÍCITA con null (NO omitirse)
        assertThat(nodo.has("next_cursor")).isTrue();
        assertThat(nodo.get("next_cursor").isNull()).isTrue();
        assertThat(json).contains("\"next_cursor\":null");
    }
}