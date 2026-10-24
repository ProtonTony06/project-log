package com.projectlog.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectlog.auth.JwtAuthFilter;
import com.projectlog.auth.JwtPrincipal;
import com.projectlog.auth.TokenService;
import com.projectlog.config.CorsConfig;
import com.projectlog.config.RestAuthenticationEntryPoint;
import com.projectlog.config.SecurityConfig;
import com.projectlog.projects.dto.ProjectDto;
import com.projectlog.projects.repository.ProjectsRepository;

/**
 * E4.1, E4.2, E4.4–E4.10 [L] (design §10, suite [L]): réplica LITERAL del
 * patrón de {@code PanelAuthMvcTest}/{@code ProjectsControllerMvcTest} de
 * C2/C3 — {@code @WebMvcTest} sobre {@link ApiKeyController} con la cadena 2
 * importada EXACTA ({@code SecurityConfig + JwtAuthFilter +
 * RestAuthenticationEntryPoint + CorsConfig}) + {@code @ActiveProfiles("test")}
 * (application-test.yml INTOCABLE) + {@code @MockitoBean} de
 * {@code ProjectsRepository} y {@code ApiKeyService} (el service se ancla
 * como bean inyectable — design §1.2). Sin contenedor ni BD; el JWT de
 * prueba se emite con un {@code TokenService} local (patrón C3).
 */
@ActiveProfiles("test")
@WebMvcTest(ApiKeyController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, CorsConfig.class})
class ApiKeyControllerMvcTest {

    /** ≥32 bytes UTF-8, solo para CONSTRUIR el JWT de prueba (string). */
    private static final String SECRETO_TEST =
            "secreto-de-test-solo-para-la-suite-c4-abcd1234abcd1234";

    private static final String URL_REGEN =
            "/api/v1/panel/projects/7/api-key/regenerate";
    private static final String URL_REVOCAR =
            "/api/v1/panel/projects/7/api-key";

    /** Key FIXED del mock: casa el literal plk_ de 47 chars (E4.2). */
    private static final String KEY_FIXTURE =
            "plk_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectsRepository projectsRepository;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private TokenService tokenService;

    // ------------------------------------------------------------------
    // Helpers (patrón C3)
    // ------------------------------------------------------------------

    private String bearer() {
        when(tokenService.verify(Mockito.anyString())).thenReturn(Optional.of(
                new JwtPrincipal("admin", "ADMIN", Instant.now().plusSeconds(3600))));
        return "Bearer " + new TokenService(SECRETO_TEST, 8).issue("admin", "ADMIN").token();
    }

    private void stubServicios() {
        when(apiKeyService.newKey()).thenReturn(KEY_FIXTURE);
        when(apiKeyService.prefixDe(KEY_FIXTURE)).thenReturn(KEY_FIXTURE.substring(0, 10));
        when(apiKeyService.sha256(KEY_FIXTURE))
                .thenReturn(new byte[] {1, 2, 3}); // digest del mock: escritura ciega
    }

    private static ProjectDto proyecto(Long id, String status) {
        return new ProjectDto(id, "payments-service", "descripción de prueba",
                status, "2026-10-21T10:00:00Z", "2026-10-21T11:30:05Z", null, false);
    }

    private static List<String> clavesDe(String cuerpo) throws Exception {
        JsonNode nodo = new ObjectMapper().readTree(cuerpo);
        List<String> claves = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(claves::add);
        return claves;
    }

    // ------------------------------------------------------------------
    // POST regenerate — rama feliz y claves EXACTAS del contrato (201)
    // ------------------------------------------------------------------

    @Test
    void regenerateSobreProyectoActivoResponde201ConClavesExactas() throws Exception {
        String bearer = bearer();
        stubServicios();
        when(projectsRepository.findById(7L)).thenReturn(
                Optional.of(proyecto(7L, "ACTIVE")));
        when(projectsRepository.regenerateKey(Mockito.eq(7L),
                Mockito.any(byte[].class), Mockito.eq(KEY_FIXTURE.substring(0, 10))))
                .thenReturn(Optional.of("2026-10-24T12:34:56Z"));

        MvcResult resultado = mockMvc.perform(post(URL_REGEN)
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(KEY_FIXTURE))
                .andExpect(jsonPath("$.prefix").value(KEY_FIXTURE.substring(0, 10)))
                .andExpect(jsonPath("$.created_at").value("2026-10-24T12:34:56Z"))
                .andReturn();
        // E4.1: claves JSON EXACTAS key, prefix, created_at — EN ESE ORDEN
        assertThat(clavesDe(resultado.getResponse().getContentAsString()))
                .containsExactly("key", "prefix", "created_at");
    }

    @Test
    void regenerateKeyNaceCasandoLiteralDeFormatoPlk() throws Exception {
        String bearer = bearer();
        stubServicios();
        when(projectsRepository.findById(7L)).thenReturn(
                Optional.of(proyecto(7L, "ACTIVE")));
        when(projectsRepository.regenerateKey(Mockito.anyLong(),
                Mockito.any(byte[].class), Mockito.anyString()))
                .thenReturn(Optional.of("2026-10-24T12:34:56Z"));

        MvcResult resultado = mockMvc.perform(post(URL_REGEN)
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode nodo = new ObjectMapper()
                .readTree(resultado.getResponse().getContentAsString());
        // 47 chars totales, literal ^plk_[A-Za-z0-9_-]{43}$ (E4.2)
        assertThat(nodo.get("key").asText()).matches("^plk_[A-Za-z0-9_-]{43}$");
        assertThat(nodo.get("prefix").asText())
                .isEqualTo(KEY_FIXTURE.substring(0, 10)); // = key.substring(0, 10)
    }

    @Test
    void created_atConFormatoFormatterDeC2() throws Exception {
        String bearer = bearer();
        stubServicios();
        when(projectsRepository.findById(7L)).thenReturn(
                Optional.of(proyecto(7L, "ACTIVE")));
        when(projectsRepository.regenerateKey(Mockito.anyLong(),
                Mockito.any(byte[].class), Mockito.anyString()))
                .thenReturn(Optional.of("2026-10-24T12:34:56Z"));

        MvcResult resultado = mockMvc.perform(post(URL_REGEN)
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode nodo = new ObjectMapper()
                .readTree(resultado.getResponse().getContentAsString());
        // cierre S4: formato C2 EXPIRES_AT_FORMATTER (yyyy-MM-dd'T'HH:mm:ss'Z')
        assertThat(nodo.get("created_at").asText())
                .matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$");
    }

    // ------------------------------------------------------------------
    // POST regenerate — 404 y 409 ANTES de la generación (orden R4.8)
    // ------------------------------------------------------------------

    @Test
    void regenerateProjectoInexistenteResponde404CuerpoEstandar() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(post(URL_REGEN).header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message")
                        .value("No existe (quizás fue eliminado)"))
                .andExpect(jsonPath("$.field").doesNotExist());
        // orden R4.8: la generación/escritura jamás ocurre para un id inexistente
        Mockito.verifyNoInteractions(apiKeyService);
        Mockito.verify(projectsRepository, Mockito.never())
                .regenerateKey(Mockito.anyLong(), Mockito.any(), Mockito.anyString());
    }

    @Test
    void regenerateSobreArchivadoResponde409ProjectArchived() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findById(7L)).thenReturn(
                Optional.of(proyecto(7L, "ARCHIVED")));

        mockMvc.perform(post(URL_REGEN).header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("project_archived"))
                .andExpect(jsonPath("$.message")
                        .value("El proyecto está archivado: desarchívalo antes"
                                + " de generar o regenerar su API key"))
                .andExpect(jsonPath("$.field").doesNotExist());
        // cierre S2: el 409 ANTECEDE a la generación (ni newKey ni sha256 ni write)
        Mockito.verifyNoInteractions(apiKeyService);
        Mockito.verify(projectsRepository, Mockito.never())
                .regenerateKey(Mockito.anyLong(), Mockito.any(), Mockito.anyString());
    }

    // ------------------------------------------------------------------
    // DELETE — sin chequeo de status (Decisión 3 revisada), 204/404
    // ------------------------------------------------------------------

    @Test
    void deleteSobreArchivadoPermitidoResponde204() throws Exception {
        String bearer = bearer();
        // Decisión 3 revisada: NO hay rama de status — el mock no necesita
        // findById (el controller NUNCA lo consulta en la ruta DELETE)
        when(projectsRepository.revokeKey(7L)).thenReturn(true);

        mockMvc.perform(delete(URL_REVOCAR).header("Authorization", bearer))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        Mockito.verify(projectsRepository, Mockito.never())
                .findById(Mockito.anyLong()); // sin chequeo previo: sin rama
        // QP-07 [M]: revokeKey invocado UNA VEZ con el id correcto
        Mockito.verify(projectsRepository).revokeKey(7L);
    }

    @Test
    void deleteProyectoInexistenteResponde404() throws Exception {
        String bearer = bearer();
        when(projectsRepository.revokeKey(7L)).thenReturn(false); // empty-RETURNING

        mockMvc.perform(delete(URL_REVOCAR).header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message")
                        .value("No existe (quizás fue eliminado)"))
                .andExpect(jsonPath("$.field").doesNotExist());
    }

    @Test
    void deleteExitosoResponde204SinCuerpo() throws Exception {
        String bearer = bearer();
        when(projectsRepository.revokeKey(7L)).thenReturn(true);

        mockMvc.perform(delete(URL_REVOCAR).header("Authorization", bearer))
                .andExpect(status().isNoContent())
                .andExpect(content().string("")); // Done 2/Decisión 2: SIN cuerpo
    }

    // ------------------------------------------------------------------
    // 401 de la cadena 2 en las 2 rutas sin JWT (E4.9)
    // ------------------------------------------------------------------

    @Test
    void sinJwtAmbasRutasResponde401InvalidToken() throws Exception {
        mockMvc.perform(post(URL_REGEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"));
        mockMvc.perform(delete(URL_REVOCAR))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"));
        Mockito.verifyNoInteractions(projectsRepository);
        Mockito.verifyNoInteractions(apiKeyService);
    }
}