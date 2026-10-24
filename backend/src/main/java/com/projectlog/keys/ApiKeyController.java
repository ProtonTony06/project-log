package com.projectlog.keys;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.projectlog.projects.dto.ProjectDto;
import com.projectlog.projects.repository.ProjectsRepository;

/**
 * {@code ApiKeyController} (R4.6–R4.10, design §1.1/§2): controller FINO —
 * EXACTAMENTE los 2 mappings del Schema §3.2 (rutas COMPLETAS literales, sin
 * {@code @RequestMapping} de clase; nacen bajo la cadena 2 de SecurityConfig
 * → 401 sin JWT automático), SIN {@code @Valid}, SIN {@code @RequestBody}
 * (Schema §3.2: entrada "—" — no hay 400) y SIN {@code @ExceptionHandler}
 * (cierre S6: la {@code DuplicateKeyException} del índice parcial único NO
 * se captura — riesgo teórico R-4 → 500 honesto).
 */
@RestController
public class ApiKeyController {

    /**
     * Duplicados de C3 con ancla (cierre S5): en {@code ProjectsController}
     * son package-private ({@code CODE_NOT_FOUND}/{@code MSG_NOT_FOUND}) y
     * {@code error()} es private — invisibles para el paquete {@code keys}.
     * Literales citados, no redefinidos (patrón C3 → C2).
     */
    static final String CODE_NOT_FOUND = "not_found";
    static final String MSG_NOT_FOUND = "No existe (quizás fue eliminado)";

    /**
     * Anclado: {@code code} = contrato del Schema §3.4 (403 en INGESTA, C5);
     * en el panel C4 lo porta un 409 — Decisión 3 REVISADA del gate
     * 2026-10-24. El {@code message} es el literal del cierre S3 del spec.
     */
    static final String CODE_PROJECT_ARCHIVED = "project_archived";
    static final String MSG_PROJECT_ARCHIVED =
            "El proyecto está archivado: desarchívalo antes de generar o"
                    + " regenerar su API key";

    private final ProjectsRepository projectsRepository;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    ApiKeyController(ProjectsRepository projectsRepository,
            ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.projectsRepository = projectsRepository;
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code POST /api/v1/panel/projects/{id}/api-key/regenerate} (R4.8,
     * cierre S2): orden DETERMINISTA de ramas — (1) 401 sin JWT (cadena 2,
     * previo al controller); (2) 404 si {@code findById} vacío; (3) 409
     * {@code project_archived} si {@code status == "ARCHIVED"} (el 409
     * ANTECEDE a la generación: una key JAMÁS se genera y descarta sobre un
     * archivado); (4) generar {@code newKey() → prefixDe → sha256}; (5)
     * escribir vía {@code regenerateKey}; (6) 404 de carrera si el
     * RETURNING viene vacío; (7) 201 con {@code ApiKeyResponse(key, prefix,
     * created_at)}.
     */
    @PostMapping("/api/v1/panel/projects/{id}/api-key/regenerate")
    public ResponseEntity<Object> regenerar(@PathVariable long id) {
        Optional<ProjectDto> fila = projectsRepository.findById(id);
        if (fila.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, MSG_NOT_FOUND);
        }
        if ("ARCHIVED".equals(fila.get().status())) {
            return error(HttpStatus.CONFLICT, CODE_PROJECT_ARCHIVED,
                    MSG_PROJECT_ARCHIVED);
        }
        String key = apiKeyService.newKey();
        String prefix = apiKeyService.prefixDe(key);
        byte[] hash = apiKeyService.sha256(key);
        Optional<String> creado = projectsRepository.regenerateKey(id, hash, prefix);
        return creado.<ResponseEntity<Object>>map(ts -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(new ApiKeyResponse(key, prefix, ts)))
                .orElseGet(() -> error(HttpStatus.NOT_FOUND,
                        CODE_NOT_FOUND, MSG_NOT_FOUND));
    }

    /**
     * {@code DELETE /api/v1/panel/projects/{id}/api-key} (R4.9, Decisión 3
     * REVISADA): SIN chequeo de {@code status} — permitido también sobre
     * ARCHIVADO, sin rama extra. {@code revokeKey} → {@code true} → 204 SIN
     * cuerpo (idempotente, Decisión 2: también cuando el proyecto ya estaba
     * sin key); {@code false} → 404 cuerpo estándar C3.
     */
    @DeleteMapping("/api/v1/panel/projects/{id}/api-key")
    public ResponseEntity<Object> revocar(@PathVariable long id) {
        return projectsRepository.revokeKey(id)
                ? ResponseEntity.status(HttpStatus.NO_CONTENT).build()
                : error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, MSG_NOT_FOUND);
    }

    /**
     * Helper de cuerpo estándar §3.1 (design §4, D-4/D-5): PRIVATE réplica
     * del de C3 pero CON EXACTAMENTE 3 parámetros — sin varargs {@code
     * field}, porque en C4 NINGÚN error lleva {@code field} (404 y 409 van
     * sin él, R4.7). {@code LinkedHashMap} ordenado {@code code → message}
     * + {@code ObjectMapper.valueToTree} + {@code MediaType.APPLICATION_JSON}.
     */
    private ResponseEntity<Object> error(HttpStatus estado, String codigo,
            String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("code", codigo);
        cuerpo.put("message", mensaje);
        return ResponseEntity.status(estado)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.valueToTree(cuerpo));
    }
}