package com.projectlog.query.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectlog.query.CursorCodec;
import com.projectlog.query.FiltrosConsulta;
import com.projectlog.query.dto.LogDto;
import com.projectlog.query.dto.LogListItemDto;
import com.projectlog.query.dto.LogsPaginaResponse;
import com.projectlog.query.repository.LogsQueryRepository;
import com.projectlog.query.repository.LogsQueryRepository.FilaConsulta;

/**
 * C6 (R6.5–R6.11, R6.21–R6.24): los 2 endpoints literales del Schema §3.2
 * — {@code GET /api/v1/panel/logs} (listado keyset) y
 * {@code GET /api/v1/panel/logs/{id}} (detalle). NUNCA {@code /api/v1/logs}
 * (la ingesta vive aparte — surface §3.2). Controller FINO: validación
 * MANUAL fail-fast sin {@code @Valid} (no existe starter-validation en el
 * classpath — patrón C3); los 8 checks corren EN ORDEN EXACTO y TODOS antes
 * de invocar la BD (R6.8/P6 — si alguno falla, el repositorio NUNCA se
 * llama).
 *
 * <p>SIN {@code @PreAuthorize} y SIN toque de seguridad (D-6.7): la cadena 2
 * del {@code SecurityConfig} ({@code securityMatcher("/api/v1/panel/**")},
 * {@code @Order(2)}, {@code anyRequest().authenticated()}) cubre ambos paths
 * nata — sin JWT → 401 {@code invalid_token} del entry point del PANEL;
 * una API key jamás autentica allí (X-API-Key ignorado).</p>
 *
 * <p>Vocabulario de errores APROBADO (R6.22, S6-6 — vocabulario cerrado
 * §3.1): {@code filter_from_to_inverted} · {@code filter_invalid_value} ·
 * {@code filter_limit_invalid} · {@code filter_cursor_invalid} ·
 * {@code not_found}. Los 3 cases sin literal textual (project_id
 * no-numérico, from/to no-parseable, limit no-numérico) REUTILIZAN
 * {@code filter_invalid_value}/{@code filter_limit_invalid} con su
 * {@code field} (gate P2). El estado del recorrido vive ÍNTEGRAMENTE en el
 * token opaco ({@code next_cursor}) — SIN {@code HttpSession} (stateless de
 * la cadena 2).</p>
 */
@RestController
@RequestMapping("/api/v1/panel/logs")
public class LogsController {

    /** Codes EXACTOS de R6.21 (ningún code nuevo fuera de la tabla R6.22). */
    static final String CODE_FILTER_FROM_TO_INVERTED = "filter_from_to_inverted";
    static final String CODE_FILTER_INVALID_VALUE = "filter_invalid_value";
    static final String CODE_FILTER_LIMIT_INVALID = "filter_limit_invalid";
    static final String CODE_FILTER_CURSOR_INVALID = "filter_cursor_invalid";
    static final String CODE_NOT_FOUND = "not_found";

    /** Messages EXACTOS de R6.21/R6.22 (cortos, español — patrón C3). */
    private static final String MSG_FROM_TO_INVERTED =
            "El rango temporal no es válido: from es posterior a to";
    private static final String MSG_ENVIRONMENT_INVALID =
            "Valor de environment no válido; use development, staging o production";
    private static final String MSG_LEVEL_INVALID =
            "Valor de level no válido; use TRACE, DEBUG, INFO, WARN, ERROR o FATAL";
    private static final String MSG_PROJECT_ID_INVALID =
            "El filtro project_id debe ser un número";
    private static final String MSG_FROM_INVALID =
            "El filtro from debe ser una fecha ISO-8601 con zona horaria";
    private static final String MSG_TO_INVALID =
            "El filtro to debe ser una fecha ISO-8601 con zona horaria";
    private static final String MSG_LIMIT_INVALID =
            "El filtro limit debe ser un número entre 1 y 200";
    private static final String MSG_CURSOR_INVALID =
            "El parámetro cursor no es válido";

    /**
     * C3, reutilizado por referencia (MSG_NOT_FOUND de ProjectsController);
     * duplicado aquí como constant PROPIA del paquete query — mismo LITERAL
     * anclado en AppFlow §5 fila 404 ("No existe (quizás fue eliminado)") —
     * para que query NO dependa de projects.* (patrón entry point de C5).
     */
    static final String MSG_NOT_FOUND = "No existe (quizás fue eliminado)";

    /** defecto 50 del Schema §3.3 (limit AUSENTE — TRD §2.7). */
    private static final int LIMIT_DEFECTO = 50;
    /** rango legal 1..200 inclusive del Schema §3.3. */
    private static final int LIMIT_MIN = 1;
    private static final int LIMIT_MAX = 200;

    private final LogsQueryRepository logsQueryRepository;
    private final ObjectMapper objectMapper;

    public LogsController(LogsQueryRepository logsQueryRepository,
            ObjectMapper objectMapper) {
        this.logsQueryRepository = logsQueryRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code GET /api/v1/panel/logs} (R6.7/R6.8/R6.24): los 8 query params
     * llegan TODOS como {@code String} ({@code required = false} — binding
     * directo tipado produciría el 400 genérico de Spring, fuera del
     * contrato, R-6.c; nota firma: {@code limit} DEBE ser String — el boceto
     * "Integer limit" del design §4 lo ratifica con su propio comentario) y
     * se PARSEAN manualmente en cadena de 8 checks en orden, primer-fallo-
     * gana, TODOS antes de la BD. {@code text} NO lleva check (sin cap —
     * S6-10/D-6.10).
     */
    @GetMapping
    public ResponseEntity<Object> listar(
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String limit) {

        // — check 1: limit (presente no-numérico, incl. "" vacío, o fuera de
        //   1..200) → filter_limit_invalid; MISMO cuerpo para las 2 causas
        //   (anti-oráculo S6-6); defecto 50 cuando AUSENTE —
        int limite;
        if (limit != null) {
            int parseado;
            try {
                parseado = Integer.parseInt(limit);
            } catch (NumberFormatException ex) {
                return error(HttpStatus.BAD_REQUEST, CODE_FILTER_LIMIT_INVALID,
                        MSG_LIMIT_INVALID, "limit");
            }
            if (parseado < LIMIT_MIN || parseado > LIMIT_MAX) {
                return error(HttpStatus.BAD_REQUEST, CODE_FILTER_LIMIT_INVALID,
                        MSG_LIMIT_INVALID, "limit");
            }
            limite = parseado;
        } else {
            limite = LIMIT_DEFECTO;
        }

        // — check 2: project_id presente no-numérico → filter_invalid_value —
        Long projectIdNum = null;
        if (projectId != null) {
            try {
                projectIdNum = Long.parseLong(projectId);
            } catch (NumberFormatException ex) {
                return error(HttpStatus.BAD_REQUEST, CODE_FILTER_INVALID_VALUE,
                        MSG_PROJECT_ID_INVALID, "project_id");
            }
        }

        // — check 3: environment fuera del enum cerrado ("" incluido;
        //   case-sensitive, sin normalizar) → filter_invalid_value —
        if (environment != null && !"development".equals(environment)
                && !"staging".equals(environment)
                && !"production".equals(environment)) {
            return error(HttpStatus.BAD_REQUEST, CODE_FILTER_INVALID_VALUE,
                    MSG_ENVIRONMENT_INVALID, "environment");
        }

        // — check 4: level idem —
        if (level != null && !"TRACE".equals(level) && !"DEBUG".equals(level)
                && !"INFO".equals(level) && !"WARN".equals(level)
                && !"ERROR".equals(level) && !"FATAL".equals(level)) {
            return error(HttpStatus.BAD_REQUEST, CODE_FILTER_INVALID_VALUE,
                    MSG_LEVEL_INVALID, "level");
        }

        // — checks 5 y 6: from/to presentes NO parseables (incl. naive sin
        //   offset — patrón S5-5 de C5: OffsetDateTime.parse exige offset) —
        OffsetDateTime desde = null;
        if (from != null) {
            try {
                desde = OffsetDateTime.parse(from);
            } catch (Exception ex) { // DateTimeParseException
                return error(HttpStatus.BAD_REQUEST, CODE_FILTER_INVALID_VALUE,
                        MSG_FROM_INVALID, "from");
            }
        }
        OffsetDateTime hasta = null;
        if (to != null) {
            try {
                hasta = OffsetDateTime.parse(to);
            } catch (Exception ex) {
                return error(HttpStatus.BAD_REQUEST, CODE_FILTER_INVALID_VALUE,
                        MSG_TO_INVALID, "to");
            }
        }

        // — check 7: from > to (ambos parseados, sobre los Instants;
        //   from == to es LEGAL — rango de punto) → filter_from_to_inverted
        //   SIN field (R6.22) —
        if (desde != null && hasta != null
                && desde.toInstant().isAfter(hasta.toInstant())) {
            return error(HttpStatus.BAD_REQUEST, CODE_FILTER_FROM_TO_INVERTED,
                    MSG_FROM_TO_INVERTED);
        }

        // — check 8: cursor presente ilegible (cualquiera de los 6 pasos del
        //   decode — SIN oráculo de causa) → filter_cursor_invalid —
        CursorCodec.Cursor cr = null;
        if (cursor != null) {
            try {
                cr = CursorCodec.decodificar(cursor);
            } catch (CursorCodec.CursorInvalidoException ex) {
                return error(HttpStatus.BAD_REQUEST, CODE_FILTER_CURSOR_INVALID,
                        MSG_CURSOR_INVALID, "cursor");
            }
        }

        // — Solo tras los 8: patron ILIKE (null JAMÁS llega a patronIlike:
        //   text AUSENTE → patron null directo al repo — rama IS NULL del
        //   estamento único; text PRESENTE (incl. "") → %<escapado>%) y UNA
        //   llamada a la BD —
        String patron = text == null ? null : FiltrosConsulta.patronIlike(text);
        OffsetDateTime cursorR =
                cr == null ? null : OffsetDateTime.ofInstant(cr.r(), ZoneOffset.UTC);
        Long cursorI = cr == null ? null : cr.i();

        List<FilaConsulta> filas = logsQueryRepository.listar(projectIdNum,
                environment, level, desde, hasta, patron, cursorR, cursorI,
                limite);

        if (filas.size() > limite) {
            // Hay página siguiente: la ÚLTIMA fila ENTREGADA alimenta el
            // cursor — JAMÁS la fila extra (limite-1 = índice de la última
            // entregada), y con el received_at RAW a micros (R6.15-c: jamás
            // el String renderizado C2). subList es vista: la extra se
            // descarta sin transportarla (R6.24).
            List<LogListItemDto> items = filas.subList(0, limite).stream()
                    .map(FilaConsulta::item).toList();
            String nextCursor = CursorCodec.codificar(
                    filas.get(limite - 1).received_at(),
                    filas.get(limite - 1).id());
            return ResponseEntity.ok(new LogsPaginaResponse(items, nextCursor));
        }
        // Sin fila extra → fin de resultados; el listado VACÍO sin cursor es
        // estado LEGÍTIMO: 200 {"items": [], "next_cursor": null} (R6.12 —
        // la clave EXPLÍCITA con null, jamás omitida).
        List<LogListItemDto> items = filas.stream()
                .map(FilaConsulta::item).toList();
        return ResponseEntity.ok(new LogsPaginaResponse(items, null));
    }

    /**
     * {@code GET /{id}} (R6.6/R6.11): 200 {@code LogDto} (exception/metadata
     * nulls incluidos — default Jackson) | {@code Optional.empty()} → 404
     * {@code not_found} con el cuerpo EXACTO SIN {@code field} (R6.22) —
     * NUNCA lista vacía ni redirección. El detalle NO lleva checks de query
     * params (R6.8). El {@code /logs/abc} (id no-numérico) queda RATIFICADO
     * sin toque: Spring no mapea la variable y responde su error de
     * matching/argumento (patrón {@code GET /projects/{id}} de C3 — sin
     * handler, sin escenario, CD-3.9).
     */
    @GetMapping("/{id}")
    public ResponseEntity<Object> obtener(@PathVariable long id) {
        Optional<LogDto> fila = logsQueryRepository.porId(id);
        return fila.<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, CODE_NOT_FOUND,
                        MSG_NOT_FOUND));
    }

    /**
     * Helper de cuerpo estándar §3.1 {@code {code, message, field?}}
     * (R6.23/S6-8): réplica EXACTA del de C3 ({@code LinkedHashMap}: code,
     * message, field SOLO si aplica; {@code objectMapper.valueToTree};
     * {@code MediaType.APPLICATION_JSON}).
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