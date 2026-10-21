package com.projectlog.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectlog.auth.JwtAuthFilter;
import com.projectlog.auth.JwtPrincipal;
import com.projectlog.auth.TokenService;
import com.projectlog.config.CorsConfig;
import com.projectlog.config.RestAuthenticationEntryPoint;
import com.projectlog.config.SecurityConfig;
import com.projectlog.projects.controller.ProjectsController;
import com.projectlog.projects.dto.ProjectDto;
import com.projectlog.projects.dto.UpdateProjectRequest;
import com.projectlog.projects.repository.ProjectsRepository;

/**
 * E3.26 [L] + 401 [L] (CD-3.13.1, G4): {@code @WebMvcTest} sobre
 * {@code ProjectsController} con el {@code ProjectsRepository} mockeado —
 * réplica del fixture de {@code PanelAuthMvcTest} (C2): cadena 2 importada
 * ({@code SecurityConfig + JwtAuthFilter + RestAuthenticationEntryPoint +
 * CorsConfig}), JWT real construido con un {@code TokenService} local de
 * test y {@code @ActiveProfiles("test")} que respeta el
 * {@code application-test.yml} dummy del validador C1 (INTOCABLE).
 *
 * <p>SIN contenedor ni BD (G4) — cubre la matriz completa R3.15, los
 * bordes de E3.26 (límites 200/2000 exactos, fail-fast de {@code status} en
 * query y PATCH con {@code verifyNoInteractions}), el {@code {} → 200} sin
 * invocar {@code update} (G2/E3.15), el combo "limpia" de
 * {@code {"description": null}}, la vía del 409
 * ({@code DuplicateKeyException} del mock → 409 {@code name_duplicate},
 * R-1), el 404 y el DTO sin {@code summary}; más el 401 del entry point en
 * las 4 rutas sin JWT (el código nuevo nace tras el filtro — S19).</p>
 */
@ActiveProfiles("test")
@WebMvcTest(ProjectsController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, CorsConfig.class})
class ProjectsControllerMvcTest {

    /** ≥32 bytes UTF-8, solo para CONSTRUIR el JWT de prueba (string). */
    private static final String SECRETO_TEST =
            "secreto-de-test-solo-para-la-suite-c3-abcd1234abcd1234";

    private static final String URL_LISTA = "/api/v1/panel/projects";
    private static final String URL_FICHA = URL_LISTA + "/7";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProjectsRepository projectsRepository;

    @MockitoBean
    private TokenService tokenService;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** JWT real (string) firmado con un TokenService local de test. */
    private static String jwtDePrueba() {
        return new TokenService(SECRETO_TEST, 8).issue("admin", "ADMIN").token();
    }

    /** Stub del mock para el filtro + credencial Bearer de la petición. */
    private String bearer() {
        when(tokenService.verify(anyString())).thenReturn(Optional.of(
                new JwtPrincipal("admin", "ADMIN", Instant.now().plusSeconds(3600))));
        return "Bearer " + jwtDePrueba();
    }

    private static ProjectDto proyecto(Long id, String name, String status) {
        return new ProjectDto(id, name, "descripción de prueba", status,
                "2026-10-21T10:00:00Z", "2026-10-21T11:30:05Z", null, false);
    }

    private static List<String> clavesDe(String cuerpo) throws Exception {
        JsonNode nodo = new ObjectMapper().readTree(cuerpo);
        List<String> claves = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(claves::add);
        return claves;
    }

    // ------------------------------------------------------------------
    // POST — 201 / límites válidos / claves exactas
    // ------------------------------------------------------------------

    @Test
    void postValidoResponde201ConValoresDelDto() throws Exception {
        String bearer = bearer();
        when(projectsRepository.insert("payments-service", "Ingesta del homelab"))
                .thenReturn(proyecto(1L, "payments-service", "ACTIVE"));

        mockMvc.perform(post(URL_LISTA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"payments-service\","
                                + "\"description\":\"Ingesta del homelab\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("payments-service"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.has_key").value(false))
                .andExpect(jsonPath("$.api_key_prefix").doesNotExist())
                .andExpect(jsonPath("$.summary").doesNotExist());
    }

    @Test
    void postConLimitesValidos200Y2000SeAceptan() throws Exception {
        String bearer = bearer();
        String name200 = "n".repeat(200);
        String desc2000 = "d".repeat(2000);
        when(projectsRepository.insert(name200, desc2000))
                .thenReturn(proyecto(2L, name200, "ACTIVE"));

        mockMvc.perform(post(URL_LISTA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", name200, "description", desc2000))))
                .andExpect(status().isCreated());
        Mockito.verify(projectsRepository).insert(name200, desc2000);
    }

    @Test
    void postConClavesDeSalidaExactasDelSchema33() throws Exception {
        String bearer = bearer();
        when(projectsRepository.insert(any(), any()))
                .thenReturn(new ProjectDto(1L, "payments-service", null, "ACTIVE",
                        "2026-10-21T10:00:00Z", "2026-10-21T11:30:05Z", null, false));

        var resultado = mockMvc.perform(post(URL_LISTA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"payments-service\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        List<String> claves = clavesDe(resultado.getResponse().getContentAsString());
        assertThat(claves).containsExactlyInAnyOrder("id", "name", "description",
                "status", "created_at", "updated_at", "api_key_prefix", "has_key");
        assertThat(claves).doesNotContain("summary"); // C10: AUSENTE
        JsonNode nodo = new ObjectMapper()
                .readTree(resultado.getResponse().getContentAsString());
        assertThat(nodo.get("description").isNull()).isTrue(); // sin description
    }

    // ------------------------------------------------------------------
    // POST — 400 de borde (G1, CD-3.6)
    // ------------------------------------------------------------------

    @Test
    void postNameAusenteOVacioResponde400FieldRequired() throws Exception {
        String bearer = bearer();

        mockMvc.perform(post(URL_LISTA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("field_required"))
                .andExpect(jsonPath("$.field").value("name"));
        Mockito.verifyNoInteractions(projectsRepository);

        mockMvc.perform(post(URL_LISTA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("field_required"))
                .andExpect(jsonPath("$.field").value("name"));
        Mockito.verifyNoInteractions(projectsRepository);
    }

    @Test
    void postNameDe201CaracteresResponde400FieldTooLong() throws Exception {
        String bearer = bearer();
        mockMvc.perform(post(URL_LISTA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "x".repeat(201)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("field_too_long"))
                .andExpect(jsonPath("$.field").value("name"));
        Mockito.verifyNoInteractions(projectsRepository);
    }

    @Test
    void postDescriptionDe2001CaracteresResponde400FieldTooLong() throws Exception {
        String bearer = bearer();
        mockMvc.perform(post(URL_LISTA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "legal", "description", "l".repeat(2001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("field_too_long"))
                .andExpect(jsonPath("$.field").value("description"));
        Mockito.verifyNoInteractions(projectsRepository);
    }

    @Test
    void postCuerpoIlegibleResponde400FieldRequiredSinField() throws Exception {
        String bearer = bearer();
        mockMvc.perform(post(URL_LISTA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("field_required"))
                .andExpect(jsonPath("$.field").doesNotExist()); // CD-3.9
        Mockito.verifyNoInteractions(projectsRepository);
    }

    // ------------------------------------------------------------------
    // GET lista — fail-fast de ?status= (G3, CD-3.7/CD-3.11) y array plano
    // ------------------------------------------------------------------

    @Test
    void getStatusInvalidoResponde400FailFastSinTocarElRepositorio() throws Exception {
        String bearer = bearer();
        for (String invalido : new String[] {"active", "ARCHIVE", ""}) {
            mockMvc.perform(get(URL_LISTA)
                            .header("Authorization", bearer)
                            .param("status", invalido))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                    .andExpect(jsonPath("$.field").value("status"));
            Mockito.verifyNoInteractions(projectsRepository); // antes de BD
        }
    }

    @Test
    void getStatusValidoInvocaFindAllConElValorLiteral() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findAll("ACTIVE"))
                .thenReturn(List.of(proyecto(1L, "a", "ACTIVE")));

        mockMvc.perform(get(URL_LISTA)
                        .header("Authorization", bearer)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
        Mockito.verify(projectsRepository).findAll("ACTIVE");
    }

    @Test
    void getListaSinStatusEsArrayPlanoDeTodos() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findAll(null)).thenReturn(List.of(
                proyecto(1L, "a", "ACTIVE"), proyecto(2L, "b", "ARCHIVED")));

        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.items").doesNotExist()); // sin {items,...}
        Mockito.verify(projectsRepository).findAll(null);
    }

    @Test
    void getListaVaciaEs200ArrayVacioLegitimo() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findAll(null)).thenReturn(List.of());
        mockMvc.perform(get(URL_LISTA).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------
    // GET ficha — 200 / 404 (CD-3.8)
    // ------------------------------------------------------------------

    @Test
    void getFichaExistenteResponde200ConClavesExactas() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findById(7L)).thenReturn(
                Optional.of(proyecto(7L, "payments-service", "ACTIVE")));

        var resultado = mockMvc.perform(get(URL_FICHA)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        List<String> claves = clavesDe(resultado.getResponse().getContentAsString());
        assertThat(claves).containsExactlyInAnyOrder("id", "name", "description",
                "status", "created_at", "updated_at", "api_key_prefix", "has_key");
        assertThat(claves).doesNotContain("summary");
    }

    @Test
    void getFichaInexistenteResponde404NotFound() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findById(999999L)).thenReturn(Optional.empty());

        mockMvc.perform(get(URL_LISTA + "/999999")
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message")
                        .value("No existe (quizás fue eliminado)"))
                .andExpect(jsonPath("$.field").doesNotExist());
    }

    // ------------------------------------------------------------------
    // PATCH — matriz R3.15 completa (G2/G3, CD-3.5/CD-3.6/CD-3.8)
    // ------------------------------------------------------------------

    @Test
    void patchNameNullResponde400FieldRequiredSinTocarBd() throws Exception {
        String bearer = bearer();
        mockMvc.perform(patch(URL_FICHA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("field_required"))
                .andExpect(jsonPath("$.field").value("name"));
        Mockito.verifyNoInteractions(projectsRepository);
    }

    @Test
    void patchStatusNullResponde400FieldRequiredSinTocarBd() throws Exception {
        String bearer = bearer();
        mockMvc.perform(patch(URL_FICHA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("field_required"))
                .andExpect(jsonPath("$.field").value("status"));
        Mockito.verifyNoInteractions(projectsRepository);
    }

    @Test
    void patchStatusFueraDelEnumResponde400AntesDeBdNunca500() throws Exception {
        String bearer = bearer();
        for (String invalido : new String[] {"ACTIVO", "active", "ARCHIVE"}) {
            mockMvc.perform(patch(URL_FICHA)
                            .header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"" + invalido + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("filter_invalid_value"))
                    .andExpect(jsonPath("$.field").value("status"));
            Mockito.verifyNoInteractions(projectsRepository); // regla 10 / R-2
        }
    }

    @Test
    void patchNameLargoResponde400FieldTooLong() throws Exception {
        String bearer = bearer();
        mockMvc.perform(patch(URL_FICHA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "n".repeat(201)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("field_too_long"))
                .andExpect(jsonPath("$.field").value("name"));
        Mockito.verifyNoInteractions(projectsRepository);
    }

    @Test
    void patchDescriptionLargaResponde400FieldTooLong() throws Exception {
        String bearer = bearer();
        mockMvc.perform(patch(URL_FICHA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("description", "d".repeat(2001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("field_too_long"))
                .andExpect(jsonPath("$.field").value("description"));
        Mockito.verifyNoInteractions(projectsRepository);
    }

    @Test
    void patchVacioResponde200IdempotenteSinLlamarUpdate() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findById(7L)).thenReturn(
                Optional.of(proyecto(7L, "payments-service", "ACTIVE")));

        mockMvc.perform(patch(URL_FICHA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                // G2/E3.15: el DTO actual con updated_at INTACTO
                .andExpect(jsonPath("$.updated_at").value("2026-10-21T11:30:05Z"))
                .andExpect(jsonPath("$.name").value("payments-service"));
        Mockito.verify(projectsRepository, Mockito.never())
                .update(Mockito.anyLong(), Mockito.any(UpdateProjectRequest.class));
    }

    @Test
    void patchDescriptionNullLimpiaYActualizaConElComboCorrecto() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findById(7L)).thenReturn(
                Optional.of(proyecto(7L, "payments-service", "ACTIVE")));
        when(projectsRepository.update(Mockito.anyLong(), any(UpdateProjectRequest.class)))
                .thenReturn(proyecto(7L, "payments-service", "ACTIVE"));

        mockMvc.perform(patch(URL_FICHA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateProjectRequest> captor =
                ArgumentCaptor.forClass(UpdateProjectRequest.class);
        Mockito.verify(projectsRepository).update(Mockito.eq(7L), captor.capture());
        UpdateProjectRequest recibido = captor.getValue();
        assertThat(recibido.camposPresentes())
                .containsExactly("description"); // presente (sin valor)
        assertThat(recibido.description()).isNull(); // valor null → columna NULL
    }

    @Test
    void patchCampoDesconocidoSeIgnoraYAplicaSoloElLegal() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findById(7L)).thenReturn(
                Optional.of(proyecto(7L, "payments-service", "ACTIVE")));
        when(projectsRepository.update(Mockito.anyLong(), any(UpdateProjectRequest.class)))
                .thenReturn(proyecto(7L, "payments-service", "ACTIVE"));

        mockMvc.perform(patch(URL_FICHA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nmae\":\"x\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()); // CD-3.9: desconocido ignorado

        ArgumentCaptor<UpdateProjectRequest> captor =
                ArgumentCaptor.forClass(UpdateProjectRequest.class);
        Mockito.verify(projectsRepository).update(Mockito.eq(7L), captor.capture());
        assertThat(captor.getValue().camposPresentes())
                .containsExactly("status");
    }

    @Test
    void patchChoqueDeUniqueResponde409NameDuplicate() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findById(7L)).thenReturn(
                Optional.of(proyecto(7L, "payments-service", "ACTIVE")));
        Mockito.doThrow(new DuplicateKeyException("projects_name_unique"))
                .when(projectsRepository)
                .update(Mockito.anyLong(), any(UpdateProjectRequest.class));

        // R-1 en [L] (E3.26): la vía del mock que lanza la excepción → 409
        mockMvc.perform(patch(URL_FICHA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"otro-proyecto\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("name_duplicate"))
                .andExpect(jsonPath("$.message")
                        .value("Ya existe un proyecto con ese nombre"))
                .andExpect(jsonPath("$.field").value("name"));
    }

    @Test
    void patchIdInexistenteResponde404SinEjecutarUpdate() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findById(999999L)).thenReturn(Optional.empty());

        mockMvc.perform(patch(URL_LISTA + "/999999")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nuevo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        Mockito.verify(projectsRepository, Mockito.never())
                .update(Mockito.anyLong(), Mockito.any(UpdateProjectRequest.class));
    }

    @Test
    void patchStatusArchivedResponde200YDevuelveElDtoRetornado() throws Exception {
        String bearer = bearer();
        when(projectsRepository.findById(7L)).thenReturn(
                Optional.of(proyecto(7L, "payments-service", "ACTIVE")));
        when(projectsRepository.update(Mockito.anyLong(), any(UpdateProjectRequest.class)))
                .thenReturn(proyecto(7L, "payments-service", "ARCHIVED"));

        mockMvc.perform(patch(URL_FICHA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.name").value("payments-service"));
    }

    // ------------------------------------------------------------------
    // 401 de la cadena 2 en las 4 rutas sin JWT (S19 — [L] adicional)
    // ------------------------------------------------------------------

    @Test
    void sinJwtLasCuatroRutasResponde401InvalidToken() throws Exception {
        // sin stub: verify() del mock devuelve Optional.empty() ⇒ 401 del entry point
        mockMvc.perform(get(URL_LISTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"));
        mockMvc.perform(post(URL_LISTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"));
        mockMvc.perform(get(URL_FICHA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"));
        mockMvc.perform(patch(URL_FICHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_token"));
        Mockito.verifyNoInteractions(projectsRepository);
    }
}