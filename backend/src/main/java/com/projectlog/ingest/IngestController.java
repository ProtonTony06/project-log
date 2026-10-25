package com.projectlog.ingest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C5 (R5.7–R5.10, S5-1, S5-3, D-2/D-3/D-7/D-10, Decisión T5-1): borde de
 * ingesta con EXACTAMENTE 2 mappings (S5-12e: GET/PUT/DELETE aquí → 405 por
 * defecto de Spring, fuera de contrato). El controller SOLO MATERIALIZA el
 * error (Decisión T5-1: los constants de los 400 por campo viven en
 * {@link IngestService}; sin dependencia inversa) — la VALIDACIÓN vive en el
 * service, la TRANSACCIÓN en el repo, y la 202 sale SOLO tras retornar el
 * insert (COMMIT, D-4). SIN sub-DTO de lote: `@RequestBody JsonNode` (S5-1);
 * los errors de parse/Content-Type se capturan con el handler del propio
 * controller, D-10 (nunca 415 fuera del contrato §3.1). SIN logger (R5.15).
 *
 * <p>Tabla de gates EXACTA del design §6 (R5.8; orden de puertas):
 * tamaño — EN EL FILTRO (barrera 413), credencial — EN EL FILTRO (401) —
 * aquí SOLO gate 3 (403, key VÁLIDA, ANTES de validar campos), gate de lote
 * (solo batch, sobre el {@code JsonNode} ANTES de parsear elemento alguno) y
 * materialización de la validación del service. Gate 0 (D-2): si el
 * principal no es {@link IngestPrincipal} (defecto estructural — JAMÁS debe
 * ocurrir sobre la cadena 1) → 401 honesto REUTILIZANDO los constants del
 * entry point, en lugar de un 500 enmascarado; un 401 o 500 MENTIROSO
 * sería el defecto.</p>
 */
@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {

    // — Constants D-7, propietario de SUS 3 gates (Decisión T5-1: el
    // service posee los de validación por campo; estos 3 son del borde —
    // literales R5.10 byte-a-byte) —
    static final String CODE_PROJECT_ARCHIVED = "project_archived";
    private static final String MSG_PROJECT_ARCHIVED =
            "El proyecto está archivado: no acepta logs";
    static final String CODE_BATCH_EMPTY = "batch_empty";
    private static final String MSG_BATCH_EMPTY =
            "El campo logs debe contener al menos 1 registro";
    static final String CODE_BATCH_TOO_LARGE = "batch_too_large";

    private static final String CAMPO_LOGS = "logs";

    private final IngestService ingestService;
    private final ObjectMapper objectMapper;

    /** Límite del lote (R5.14): bind int nativo, conversión UNA vez. */
    private final int limiteLote;

    /** Constructor (D-3: el ObjectMapper es el bean único de Boot). */
    public IngestController(IngestService ingestService, ObjectMapper objectMapper,
            @Value("${projectlog.ingest.max-batch-size:200}") int limiteLote) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
        this.limiteLote = limiteLote;
    }

    /** POST /api/v1/ingest — log individual (R5.7). */
    @PostMapping
    public ResponseEntity<Object> ingest(
            @RequestBody(required = false) JsonNode cuerpo) {
        return procesar(cuerpo, false);
    }

    /** POST /api/v1/ingest/batch — batch de 1…200 (R5.7). */
    @PostMapping("/batch")
    public ResponseEntity<Object> ingestBatch(
            @RequestBody(required = false) JsonNode cuerpo) {
        return procesar(cuerpo, true);
    }

    /** Ambos endpoints unificados (design §6). */
    private ResponseEntity<Object> procesar(JsonNode cuerpo, boolean lote) {
        // — Gate 0 (D-2, defecto estructural): principal no es IngestPrincipal
        // → 401 honesto con el MISMO cuerpo del entry point (jamás 500). —
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null
                && authentication.getPrincipal()
                        instanceof IngestPrincipal principal)) {
            return error(HttpStatus.UNAUTHORIZED,
                    IngestAuthenticationEntryPoint.CODE_API_KEY_INVALID,
                    IngestAuthenticationEntryPoint.MSG_API_KEY_INVALID,
                    null, null);
        }
        // — Gate 3 (403, key VÁLIDA): ANTES de la validación de campos (R5.8:
        // un pedido con cuerpo inválido a proyecto archivado responde 403) —
        if ("ARCHIVED".equals(principal.status())) {
            return error(HttpStatus.FORBIDDEN, CODE_PROJECT_ARCHIVED,
                    MSG_PROJECT_ARCHIVED, null, null);
        }
        // — Gate de lote (solo batch, S5-3/R5.24-c): sobre el JsonNode ANTES
        // de parsear elemento alguno; NADIE re-especializa el 413 en 400
        // (R-13) —
        List<LogIngestRequest> registros = new ArrayList<>();
        if (lote) {
            JsonNode logs = cuerpo == null ? null : cuerpo.path(CAMPO_LOGS);
            if (!logs.isArray() || logs.size() == 0) {
                return error(HttpStatus.BAD_REQUEST, CODE_BATCH_EMPTY,
                        MSG_BATCH_EMPTY, CAMPO_LOGS, null);
            }
            if (logs.size() > limiteLote) {
                return error(HttpStatus.BAD_REQUEST, CODE_BATCH_TOO_LARGE,
                        "El campo logs no puede contener más de " + limiteLote
                                + " registros", CAMPO_LOGS, null);
            }
            for (JsonNode elemento : logs) {
                registros.add(LogIngestRequest.desdeJson(elemento));
            }
        } else {
            registros.add(LogIngestRequest.desdeJson(cuerpo));
        }
        // — Validación + INSERT (D-4) en el service; 400 materializado si
        // hay fallo ( primer-fallo-gana ya decidió el service, R-9) —
        return ingestService.registrar(principal.projectId(), registros, lote)
                .<ResponseEntity<Object>>map(fallo -> error(HttpStatus.BAD_REQUEST,
                        fallo.code(), fallo.message(), fallo.field(), fallo.index()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.ACCEPTED) // 202
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.valueToTree(Map.of("accepted",
                                registros.size()))));
    }

    /**
     * Handler de ilegibilidad (S5-1/D-10): cuerpo no-JSON, vacío o Content-Type
     * sin converter → 400 por URI — individual → {@code environment_required}
     * (field environment), batch → {@code batch_empty} (field logs). El MISMO
     * 400 que un {@code {}} vacío — sin 415, sin codes nuevos (CD-3.9).
     * ({@code required = false} en el {@code @RequestBody} para que el cuerpo
     * ausente también caiga AQUÍ y no en un 400 genérico de Spring.)
     */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<Object> cuerpoIlegible(
            jakarta.servlet.http.HttpServletRequest peticion) {
        boolean batch = peticion.getRequestURI().endsWith("/batch");
        return batch
                ? error(HttpStatus.BAD_REQUEST, CODE_BATCH_EMPTY,
                        MSG_BATCH_EMPTY, CAMPO_LOGS, null)
                : error(HttpStatus.BAD_REQUEST,
                        IngestService.CODE_ENVIRONMENT_REQUIRED,
                        IngestService.MSG_ENVIRONMENT_REQUIRED,
                        LogIngestRequest.CAMPO_ENVIRONMENT, null);
    }

    /**
     * Helper `error` — réplica LITERAL de C3 (ProjectsController) con `index`
     * añadido (LinkedHashMap en orden code, message, field, index; claves
     * solo si aplican; `valueToTree`; MediaType APPLICATION_JSON). Un solo
     * método cubre los 4 usos (401 honesto, 403, 400 de lote, 400 de
     * ErrorValidacion).
     */
    private ResponseEntity<Object> error(HttpStatus estado, String code,
            String message, String field, Integer index) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("code", code);
        cuerpo.put("message", message);
        if (field != null) {
            cuerpo.put("field", field);
        }
        if (index != null) {
            cuerpo.put("index", index);
        }
        return ResponseEntity.status(estado)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.valueToTree(cuerpo));
    }
}