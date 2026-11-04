package com.projectlog.projects.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.projectlog.projects.dto.CreateProjectRequest;
import com.projectlog.projects.dto.ProjectDto;
import com.projectlog.projects.dto.SummaryDto;
import com.projectlog.projects.dto.UpdateProjectRequest;
import com.projectlog.projects.repository.ProjectsRepository;
import com.projectlog.query.repository.LogsBorradoRepository;
import com.projectlog.query.repository.LogsQueryRepository;

/**
 * {@code ProjectsController} (R3.2/R3.3, CD-3.6): controller FINO — sin capa
 * de servicio y SIN {@code @Valid}/Bean Validation (no existe
 * starter-validation en el classpath: {@code @Valid} sería no-op silencioso);
 * la validación es MANUAL con constantes de literal y cuerpos de error vía
 * {@code ObjectMapper} (patrón {@code LoginController.credencialesInvalidas()}
 * de C2).
 *
 * <p>Los 4 endpoints literales del Schema §3.2 (L1) nacen PROTEGIDOS por la
 * cadena 2 de {@code SecurityConfig} ({@code /api/v1/panel/**} →
 * {@code anyRequest().authenticated()} — cero líneas de seguridad tocadas,
 * S19). El 401 sigue siendo EXCLUSIVO del entry point de C2.</p>
 *
 * <p>Vocabulario de errores APROBADO (G1/G3, L5/S3.1): {@code field_required}
 * · {@code field_too_long} · {@code filter_invalid_value} ·
 * {@code name_duplicate} (409) · {@code not_found} (404) — cero literales
 * nuevos sin ancla; los mensajes son descriptivos en español y el cuerpo
 * estándar es {@code {code, message, field?}}.</p>
 *
 * <p>C10 (D-C10-1/D-C10-2/D-C10-5): 3 operaciones destructivas del flujo 8
 * — {@code DELETE /{id}} (8a), {@code DELETE /{id}/logs} (8c) con la doble
 * confirmación por cabecera {@code X-Confirm-Name} y el orden fail-fast
 * EXACTO: (1) cabecera ausente → 400 {@code confirm_required} SIN tocar BD;
 * (2) {@code findById} vacío → 404; (3) Igualdad EXACTA contra el name de BD
 * → 400 {@code confirm_mismatch} (los errors de confirmación NO llevan
 * {@code field} — patrón ApiKeyController); (4) el DELETE. La purga responde
 * 200 {@code {"deleted": N}} con el N REAL del retorno de
 * {@code JdbcTemplate.update} (primer cuerpo de un DELETE del proyecto) y
 * desambigua N=0 con un re-{@code findById} (M4). El {@code GET /{id}/summary}
 * sirve el {@code SummaryDto} §3.3 vía {@code LogsQueryRepository.resumen}
 * (flujo 9). {@code include=summary} SOLO existe en el listado (M3) y se
 * valida fail-fast ANTES de BD DESPUÉS del check de {@code status} (D-C10-5).
 * Archivado NO bloquea los DELETE (M5).</p>
 */
@RestController
@RequestMapping("/api/v1/panel/projects")
public class ProjectsController {

    /** Literales APROBADOS del vocabulario (L5) — sin inventos. */
    static final String CODE_FIELD_REQUIRED = "field_required";
    static final String CODE_FIELD_TOO_LONG = "field_too_long";
    static final String CODE_FILTER_INVALID_VALUE = "filter_invalid_value";
    static final String CODE_NAME_DUPLICATE = "name_duplicate";
    static final String CODE_NOT_FOUND = "not_found";

    /** C10, D-C10-1: codes de la doble confirmación (8a/8c) — sin ancla
     * textual en el vocabulario C3 y CON ancla en §3.3/flujo 8 (AppFlow). */
    static final String CODE_CONFIRM_REQUIRED = "confirm_required";
    static final String CODE_CONFIRM_MISMATCH = "confirm_mismatch";

    /** Mensajes con ancla: 409 = AppFlow flujo 2; 404 = AppFlow §5 (fila 404);
     * confirm_required/mismatch = §3.3/flujo 8 (el mismatch ES el texto que
     * la UI pinta dentro del modal — REQ-C10-UI-2). Los errores de
     * confirmación NO llevan {@code field} (patrón ApiKeyController). */
    static final String MSG_NAME_DUPLICATE = "Ya existe un proyecto con ese nombre";
    static final String MSG_NOT_FOUND = "No existe (quizás fue eliminado)";
    static final String MSG_CONFIRM_REQUIRED =
            "La cabecera X-Confirm-Name es obligatoria";
    static final String MSG_CONFIRM_MISMATCH = "La confirmación no coincide";
    private static final String MSG_CUERPO_ILEGIBLE =
            "El cuerpo de la petición debe ser JSON válido";

    /** Límites LITERALES del Schema §3.3 (R3.13): name 1..200; description ≤2000. */
    private static final int LIMITE_NAME = 200;
    private static final int LIMITE_DESCRIPTION = 2000;

    /** Único valor legal del query param {@code include} (§3.2 — M3: SOLO
     * en el LISTADO; la ficha consume el endpoint dedicado). */
    static final String INCLUDE_SUMMARY = "summary";

    private final ProjectsRepository projectsRepository;
    private final LogsQueryRepository logsQueryRepository;
    private final LogsBorradoRepository logsBorradoRepository;
    private final ObjectMapper objectMapper;

    public ProjectsController(ProjectsRepository projectsRepository,
            LogsQueryRepository logsQueryRepository,
            LogsBorradoRepository logsBorradoRepository,
            ObjectMapper objectMapper) {
        this.projectsRepository = projectsRepository;
        this.logsQueryRepository = logsQueryRepository;
        this.logsBorradoRepository = logsBorradoRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code GET /api/v1/panel/projects} (R3.4/R3.10, CD-3.7/CD-3.10/CD-3.11):
     * {@code status} se valida contra el enum cerrado ANTES de tocar la BBDD
     * (fail-fast G3 — {@code ?status=} presente y vacío TAMBIÉN es valor
     * inválido, nunca "sin filtro"); sin query → todas las filas; respuesta
     * 200 con array plano {@code [ProjectDto]} (L10: sin
     * {@code {items, next_cursor}} — keyset exclusivo de logs, C6); vacío →
     * {@code 200 []} legítimo. Minúsculas no se normalizan (CHECK literal).
     *
     * <p>C10 (D-C10-5, M3): {@code include} presente ≠ {@code summary} →
     * {@code 400 filter_invalid_value} con {@code field=include} — ANTES de
     * BD y SIEMPRE DESPUÉS del check de {@code status} (orden additivo
     * determinista: (1) status, (2) include). Con
     * {@code include=summary} cada elemento compone {@code summary} vía
     * {@link LogsQueryRepository#resumen} (3N idas aceptadas por diseño —
     * D-C10-3); sin include, byte-idéntico a C8 (clave AUSENTE — NON_NULL).</p>
     */
    @GetMapping
    public ResponseEntity<Object> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String include) {
        // (1) status — byte-idéntico a C3 y SIEMPRE primero (D-C10-5)
        if (status != null && !"ACTIVE".equals(status) && !"ARCHIVED".equals(status)) {
            return error(HttpStatus.BAD_REQUEST, CODE_FILTER_INVALID_VALUE,
                    "Valor de status no válido; use ACTIVE o ARCHIVED", "status");
        }
        // (2) include — único valor legal: summary (§3.2/M3); fail-fast SIN BD
        if (include != null && !INCLUDE_SUMMARY.equals(include)) {
            return error(HttpStatus.BAD_REQUEST, CODE_FILTER_INVALID_VALUE,
                    "Valor de include no válido; use summary", "include");
        }
        List<ProjectDto> filas = projectsRepository.findAll(status);
        if (include == null) {
            return ResponseEntity.ok(filas);
        }
        return ResponseEntity.ok(filas.stream()
                .map(p -> p.conResumen(logsQueryRepository.resumen(p.id())))
                .toList());
    }

    /** {@code GET /{id}} (R3.11, CD-3.8): 404 {@code not_found} con cuerpo
     * estándar — NUNCA respuesta vacía ni lista vacía (AppFlow §5). El DTO
     * NO lleva {@code summary} (M3: la ficha consume {@code GET /{id}/summary}) —
     * la clave queda AUSENTE por el NON_NULL (G4-a). */
    @GetMapping("/{id}")
    public ResponseEntity<Object> obtener(@PathVariable long id) {
        Optional<ProjectDto> fila = projectsRepository.findById(id);
        return fila.<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND,
                        MSG_NOT_FOUND));
    }

    /**
     * C10 (flujo 9, M3/G4-a): {@code GET /{id}/summary} — endpoint DEDICADO
     * de la ficha: {@code findById} → 404 (sin consultar logs, E3.19: nulo-
     * imposible después — REQ-C10-RESUMEN-6 del spec) → {@code resumen(id)}
     * → 200 con el {@code SummaryDto} EXACTO de §3.3 (4 claves SIEMPRE —
     * estado vacío {@code {"by_level": {}, "by_environment": {}, "total": 0,
     * "last_log_at": null}}). Archivados cuentan igual (M5).
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<Object> resumen(@PathVariable long id) {
        if (projectsRepository.findById(id).isEmpty()) {
            return error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, MSG_NOT_FOUND);
        }
        return ResponseEntity.ok(logsQueryRepository.resumen(id));
    }

    /**
     * C10 (flujo 8a, REQ-C10-DEL-1..5): {@code DELETE /{id}} — borrado
     * DEFINITIVO del proyecto y sus logs. Orden fail-fast EXACTO (D-C10-1):
     * (1) cabecera {@code X-Confirm-Name} ausente → 400
     * {@code confirm_required} SIN tocar BD; (2) {@code findById} vacío →
     * 404 {@code not_found} (MSG_NOT_FOUND C3); (3) igualdad EXACTA
     * {@code String} contra el name de BD (SIN trim, diferenciando
     * mayúsculas/espacios — el trim es solo del input de la UI) → 400
     * {@code confirm_mismatch} «La confirmación no coincide» (sin
     * {@code field}); (4) borrado → 204 SIN cuerpo (false ⇒ 404 de carrera).
     * La CASCADE de logs y la muerte de la key las ejecuta el MOTOR en la
     * MISMA transacción (D-C10-2). Archivado NO bloquea (M5: sin rama de
     * status).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> eliminar(@PathVariable long id,
            @RequestHeader(name = "X-Confirm-Name", required = false)
            String confirmacion) {
        // (1) cabecera obligatoria — cero E/S (fail-fast más-barato-primero)
        if (confirmacion == null) {
            return error(HttpStatus.BAD_REQUEST, CODE_CONFIRM_REQUIRED,
                    MSG_CONFIRM_REQUIRED);
        }
        // (2) existencia
        Optional<ProjectDto> fila = projectsRepository.findById(id);
        if (fila.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, MSG_NOT_FOUND);
        }
        // (3) igualdad EXACTA (String.equals, sin trim)
        if (!fila.get().name().equals(confirmacion)) {
            return error(HttpStatus.BAD_REQUEST, CODE_CONFIRM_MISMATCH,
                    MSG_CONFIRM_MISMATCH);
        }
        // (4) borrado — la CASCADE la ejecuta el motor (D-C10-2)
        if (!projectsRepository.borrar(id)) {
            return error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, MSG_NOT_FOUND);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * C10 (flujo 8c, REQ-C10-PURGA-1..5): {@code DELETE /{id}/logs} — purga
     * de TODOS los logs; el proyecto y su API key SOBREVIVEN (fila de
     * {@code projects} intocada). Cabecera y orden fail-fast idénticos al
     * 8a (D-C10-1). Devuelve 200 con el cuerpo EXACTO {@code {"deleted": N}}
     * (LinkedHashMap + ObjectMapper — primer cuerpo de un DELETE del
     * proyecto, gap 6); el N ES el retorno de filas de
     * {@code JdbcTemplate.update}. Desambiguación N=0 (M4/D-C10-2):
     * re-{@code findById} — vacío ⇒ 404 (estado «proyecto inexistente»,
     * incluye la carrera del CASCADE); presente ⇒ 200 {@code {"deleted": 0}}
     * (estado vacío informativo, NUNCA error).
     */
    @DeleteMapping("/{id}/logs")
    public ResponseEntity<Object> purgar(@PathVariable long id,
            @RequestHeader(name = "X-Confirm-Name", required = false)
            String confirmacion) {
        // (1) cabecera obligatoria — cero E/S
        if (confirmacion == null) {
            return error(HttpStatus.BAD_REQUEST, CODE_CONFIRM_REQUIRED,
                    MSG_CONFIRM_REQUIRED);
        }
        // (2) existencia
        Optional<ProjectDto> fila = projectsRepository.findById(id);
        if (fila.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, MSG_NOT_FOUND);
        }
        // (3) igualdad EXACTA (String.equals, sin trim)
        if (!fila.get().name().equals(confirmacion)) {
            return error(HttpStatus.BAD_REQUEST, CODE_CONFIRM_MISMATCH,
                    MSG_CONFIRM_MISMATCH);
        }
        // (4) purga — el N ES el retorno del UPDATE/DELETE (D-C10-2)
        int n = logsBorradoRepository.purgarPorProyecto(id);
        if (n == 0 && projectsRepository.findById(id).isEmpty()) {
            // carrera: el proyecto desapareció (con sus logs, CASCADE) —
            // NUNCA 200 {"deleted": 0} sobre inexistente (REQ-C10-PURGA-4)
            return error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, MSG_NOT_FOUND);
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("deleted", n);
        return ResponseEntity.ok(objectMapper.valueToTree(cuerpo));
    }

    /**
     * {@code POST} (R3.14, CD-3.6): validación de borde con límites
     * literales (name ausente/vacío → 400 {@code field_required}; > 200, sin
     * trimming → 400 {@code field_too_long}; description > 2000 → idem; ""
     * es legal para description); INSERT directo (R-1) → 201 con el DTO de
     * la fila reciéntemente creada. La carrera de la
     * {@code projects_name_unique} la traduce el handler ÚNICO a 409.
     */
    @PostMapping
    public ResponseEntity<Object> crear(@RequestBody CreateProjectRequest peticion) {
        if (peticion.name() == null || peticion.name().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, CODE_FIELD_REQUIRED,
                    "El campo name es obligatorio", "name");
        }
        if (peticion.name().length() > LIMITE_NAME) {
            return error(HttpStatus.BAD_REQUEST, CODE_FIELD_TOO_LONG,
                    "El campo name no puede superar " + LIMITE_NAME
                            + " caracteres", "name");
        }
        if (peticion.description() != null
                && peticion.description().length() > LIMITE_DESCRIPTION) {
            return error(HttpStatus.BAD_REQUEST, CODE_FIELD_TOO_LONG,
                    "El campo description no puede superar " + LIMITE_DESCRIPTION
                            + " caracteres", "description");
        }
        ProjectDto creado = projectsRepository.insert(
                peticion.name(), peticion.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /**
     * {@code PATCH /{id}} (G2/G3, CD-3.5/CD-3.6/CD-3.8): semántica parcial
     * con {@link UpdateProjectRequest#desdeJson(JsonNode)}. Orden
     * determinista "primer fallo gana" (CD-3.6): primero los {@code null}
     * explícitos (G2/G3), luego el enum ANTES de tocar la BBDD (regla 10 /
     * R-2: un status inválido que escapara sería 500 por el CHECK — prohibido),
     * luego longitudes/vacío. Después: {@code {}}
     * → 200 SIN llamar a {@code update} (idempotencia G2, DTO actual con
     * {@code updated_at} intacto); con campos → {@code findById} (404 sin
     * UPDATE, E3.19) → {@code update} → 200 (o 409 vía el handler único).
     * El archivado/desarchivado ES este PATCH con
     * {@code {"status": "ARCHIVED"|"ACTIVE"}} — nunca DELETE (R3.16).
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Object> editar(@PathVariable long id,
            @RequestBody JsonNode nodo) {
        UpdateProjectRequest peticion = UpdateProjectRequest.desdeJson(nodo);
        Set<String> presentes = peticion.camposPresentes();

        // 1) nulls explícitos (G2/G3): no existe valor legal nulo para name/status
        if (presentes.contains(UpdateProjectRequest.CAMPO_NAME)
                && peticion.name() == null) {
            return error(HttpStatus.BAD_REQUEST, CODE_FIELD_REQUIRED,
                    "El campo name es obligatorio", "name");
        }
        if (presentes.contains(UpdateProjectRequest.CAMPO_STATUS)
                && peticion.status() == null) {
            return error(HttpStatus.BAD_REQUEST, CODE_FIELD_REQUIRED,
                    "El campo status es obligatorio", "status");
        }
        // 2) enum ANTES de BD (regla 10 / R-2): fail-fast, nunca 500 por el CHECK
        if (presentes.contains(UpdateProjectRequest.CAMPO_STATUS)
                && !"ACTIVE".equals(peticion.status())
                && !"ARCHIVED".equals(peticion.status())) {
            return error(HttpStatus.BAD_REQUEST, CODE_FILTER_INVALID_VALUE,
                    "Valor de status no válido; use ACTIVE o ARCHIVED", "status");
        }
        // 3) longitudes / vacío (límites literales §3.3, sin trimming)
        if (presentes.contains(UpdateProjectRequest.CAMPO_NAME)
                && peticion.name() != null && peticion.name().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, CODE_FIELD_REQUIRED,
                    "El campo name es obligatorio", "name");
        }
        if (presentes.contains(UpdateProjectRequest.CAMPO_NAME)
                && peticion.name() != null
                && peticion.name().length() > LIMITE_NAME) {
            return error(HttpStatus.BAD_REQUEST, CODE_FIELD_TOO_LONG,
                    "El campo name no puede superar " + LIMITE_NAME
                            + " caracteres", "name");
        }
        if (presentes.contains(UpdateProjectRequest.CAMPO_DESCRIPTION)
                && peticion.description() != null
                && peticion.description().length() > LIMITE_DESCRIPTION) {
            return error(HttpStatus.BAD_REQUEST, CODE_FIELD_TOO_LONG,
                    "El campo description no puede superar " + LIMITE_DESCRIPTION
                            + " caracteres", "description");
        }

        // G2: {} → 200 idempotente SIN UPDATE (el controller no llama a repo.update)
        if (presentes.isEmpty()) {
            Optional<ProjectDto> fila = projectsRepository.findById(id);
            return fila.<ResponseEntity<Object>>map(ResponseEntity::ok)
                    .orElseGet(() -> error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND,
                            MSG_NOT_FOUND));
        }

        // 404 ANTES de UPDATE (E3.19/CD-3.8): el UPDATE jamás va sobre id inexistente
        Optional<ProjectDto> fila = projectsRepository.findById(id);
        if (fila.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, MSG_NOT_FOUND);
        }
        ProjectDto actualizado = projectsRepository.update(id, peticion);
        return ResponseEntity.ok(actualizado);
    }

    /**
     * Handler ÚNICO de la UNIQUE (R-1, CD-3.4): Spring JDBC traduce el 23505
     * de {@code projects_name_unique} a {@code DuplicateKeyException}
     * (subclase de {@code DataIntegrityViolationException}) — este punto
     * materializa el 409 {@code name_duplicate} (mensaje AppFlow flujo 2;
     * datos conservados) y SIRVE a POST y PATCH juntos. NO se mapea
     * {@code DataIntegrityViolationException} genérico: enmascararía otras
     * violaciones como 409 (el genérico cae a 500 — correcto en C3).
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Object> nombreDuplicado(DuplicateKeyException ex) {
        return error(HttpStatus.CONFLICT, CODE_NAME_DUPLICATE, MSG_NAME_DUPLICATE,
                "name");
    }

    /**
     * Cuerpo no parseable / ausente → 400 {@code field_required} SIN
     * {@code field} (CD-3.9: no se reutiliza {@code filter_invalid_value} ni
     * se inventa {@code body_invalid}); el PATCH con JSON válido pero vacío
     * {@code {}} es 200 (G2) — solo el JSON ilegible es 400.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> cuerpoIlegible(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, CODE_FIELD_REQUIRED,
                MSG_CUERPO_ILEGIBLE);
    }

    /**
     * Helper de cuerpo estándar §3.1 {@code {code, message, field?}} (CD-3.6):
     * la clave {@code field} SOLO cuando aplica — {@code Map.of} no admite
     * {@code null}.
     */
    private ResponseEntity<Object> error(HttpStatus estado, String codigo,
            String mensaje, String... campo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("code", codigo);
        cuerpo.put("message", mensaje);
        if (campo.length > 0 && campo[0] != null) {
            cuerpo.put("field", campo[0]);
        }
        return ResponseEntity.status(estado)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.valueToTree(cuerpo));
    }
}