package com.projectlog.ingest;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * C5 (R5.12, R5.24, S5-3…S5-8, D-7/D-11/D-12): ÚNICO propietario del ORDEN
 * de validación (primer-fallo-gana, R-9) — el método público
 * {@link #registrar} NO es transaccional (D-4: la ÚNICA {@code @Transactional}
 * del corte vive en {@code LogsRepository.insert}; el COMMIT ocurre al
 * retornar y el seam {@code IngestListener} es la sentencia siguiente —
 * tras-COMMIT ESTRUCTURAL). Validación COMPLETA del lote ANTES de tocar la
 * BD (regla 6: "el lote se valida completo antes de tocar la BBDD… nada se
 * persiste"); {@code received_at} se calcula UNA vez, tras TODA la
 * validación, inmediatamente ANTES del insert (S5-7) — en un 400 nunca se
 * calcula y en cualquier caso NADA se persiste.
 *
 * <p><b>Decisión T5-1 (ratificación D-7)</b>: los 6 constants de los 400 por
 * campo viven AQUÍ (propietario del CONTENIDO del {@code ErrorValidacion};
 * los 11 literales son byte-a-byte los de R5.10/S5-13). El
 * {@code IngestController} SOLO MATERIALIZA el error y jamás hay dependencia
 * inversa service→controller. SIN captura de
 * {@code DataIntegrityViolationException}/{@code DuplicateKeyException}
 * (R5.12-b: fallo real teórico → 500 honesto, política S6 de C4; el rollback
 * del proxy del repo cubre). El service JAMÁS toca HTTP. Clases nuevas SIN
 * loggers (R5.15).</p>
 */
@Service
public class IngestService {

    // — Constants de los 400 por campo (Decisión T5-1; literales R5.10) —
    static final String CODE_ENVIRONMENT_REQUIRED = "environment_required";
    static final String MSG_ENVIRONMENT_REQUIRED =
            "El campo environment es obligatorio (development, staging o"
                    + " production)";
    static final String CODE_TIMESTAMP_WITHOUT_TIMEZONE =
            "timestamp_without_timezone";
    static final String MSG_TIMESTAMP_WITHOUT_TIMEZONE =
            "El campo timestamp debe ser ISO-8601 con zona horaria";
    static final String CODE_LEVEL_INVALID = "level_invalid";
    static final String MSG_LEVEL_INVALID =
            "El campo level no es un nivel válido (TRACE, DEBUG, INFO, WARN,"
                    + " ERROR o FATAL)";
    static final String CODE_MESSAGE_REQUIRED = "message_required";
    static final String MSG_MESSAGE_REQUIRED = "El campo message es obligatorio";
    static final String CODE_FIELD_TOO_LONG = "field_too_long";
    static final String CODE_METADATA_NOT_OBJECT = "metadata_not_object";
    static final String MSG_METADATA_NOT_OBJECT =
            "El campo metadata debe ser un objeto JSON";

    /** Límites EXACTOS del Schema §4.1/R5.10 (KiB ⇒ bytes UTF-8; texto 200 chars). */
    static final int LIMITE_MESSAGE_BYTES = 32768;
    static final int LIMITE_EXCEPTION_BYTES = 131072;
    static final int LIMITE_METADATA_BYTES = 65536;
    static final int LIMITE_TEXTO_CHARS = 200; // source / correlation_id

    /** Enums cerrados case-sensitive EXACTO (S5-4/S5-6: sin normalización). */
    static final Set<String> ENTORNOS =
            Set.of("development", "staging", "production");
    static final Set<String> NIVELES =
            Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");

    /**
     * Record del 400 de validación (S5-3): {@code index} 0-based SOLO en
     * batch (posición en el array JSON), {@code null} en individual.
     */
    public record ErrorValidacion(String code, String message, String field,
            Integer index) {
    }

    private final LogsRepository logsRepository;
    private final ObjectMapper objectMapper;

    /** Seam C7 como LISTA inyectada — Spring inyecta lista VACÍA si no hay
     * beans (cero clientes en C5; S5-10 descarta Optional: no modela 0..n). */
    private final List<IngestListener> oyentes;

    /** Constructor (D-3: el ObjectMapper es el bean único de Boot, jamás
     * instancia suelta). */
    public IngestService(LogsRepository logsRepository, ObjectMapper objectMapper,
            List<IngestListener> oyentes) {
        this.logsRepository = logsRepository;
        this.objectMapper = objectMapper;
        this.oyentes = oyentes;
    }

    /** Método público ÚNICO (NO transaccional — D-4). */
    public Optional<ErrorValidacion> registrar(long projectId,
            List<LogIngestRequest> registros, boolean lote) {

        // — 1. Validación COMPLETA antes de tocar la BD (regla 6; R5.24:
        // índices crecientes y primer-fallo-gana). UNA SOLA pasada por
        // registro: el timestamp se PARSEA una vez (check 2) y la metadata
        // se SERIALIZA una vez (check 7) — cada uno alimenta tanto la medida
        // del límite como el parámetro del INSERT (S5-8 / R-D3) —
        List<LogValidado> filas = new ArrayList<>(registros.size());
        for (int i = 0; i < registros.size(); i++) {
            Integer index = lote ? i : null;
            ErrorValidacion fallo = validarYMaterializar(registros.get(i),
                    index, filas);
            if (fallo != null) {
                return Optional.of(fallo);
            }
        }

        // — 2. received_at UNA vez, tras TODA la validación (S5-7) —
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);

        // — 3. INSERT único (el @Transactional del repo = COMMIT al retornar,
        // D-4); sin captura de DIVE/Duplicate (500 honesto, R5.12-b) —
        List<Long> ids = logsRepository.insert(projectId, filas, receivedAt);

        // — 4. Seam tras-COMMIT ESTRUCTURAL (sentencia siguiente a COMMIT,
        // fuera de todo scope transaccional; TAMBIÉN en individual — ids de 1,
        // el frame batch de C7 para un log individual es batch de 1, §3.5) —
        oyentes.forEach(o -> o.onLotePersistido(projectId, receivedAt, ids));

        // — 5. La 202 la emite el controller —
        return Optional.empty();
    }

    /**
     * Los 9 checks POR REGISTRO en el orden determinista R5.24 — bytes UTF-8
     * para message/exception/metadata, {@code String.length()} para
     * source/correlation_id (S5-8; Punto 2 del gate); al primer fallo
     * {@code return} el error SIN haber tocado la BD (construir el record en
     * memoria no ES tocar la BD: nada persiste hasta el INSERT). Añade el
     * {@code LogValidado} a {@code filas} SOLO si todo el registro pasó.
     */
    private ErrorValidacion validarYMaterializar(LogIngestRequest r,
            Integer index, List<LogValidado> filas) {
        // 1. environment — UN code para las 4 variantes (S5-4: ausente, "",
        //    no textual (coerción → ""), fuera del enum)
        if (r.environment() == null || r.environment().isBlank()
                || !ENTORNOS.contains(r.environment())) {
            return error(CODE_ENVIRONMENT_REQUIRED, MSG_ENVIRONMENT_REQUIRED,
                    LogIngestRequest.CAMPO_ENVIRONMENT, index);
        }
        // 2. timestamp — ausente/no-parseable/naive → el MISMO code (S5-5);
        //    el OffsetDateTime parseado es ya la moneda del INSERT (una
        //    sola pasada, D-11)
        if (r.timestamp() == null) {
            return error(CODE_TIMESTAMP_WITHOUT_TIMEZONE,
                    MSG_TIMESTAMP_WITHOUT_TIMEZONE,
                    LogIngestRequest.CAMPO_TIMESTAMP, index);
        }
        OffsetDateTime timestamp;
        try {
            timestamp = parsearTimestamp(r.timestamp());
        } catch (DateTimeParseException ex) {
            return error(CODE_TIMESTAMP_WITHOUT_TIMEZONE,
                    MSG_TIMESTAMP_WITHOUT_TIMEZONE,
                    LogIngestRequest.CAMPO_TIMESTAMP, index);
        }
        // 3. level — enum cerrado case-sensitive, sin normalización (S5-6)
        if (r.level() == null || !NIVELES.contains(r.level())) {
            return error(CODE_LEVEL_INVALID, MSG_LEVEL_INVALID,
                    LogIngestRequest.CAMPO_LEVEL, index);
        }
        // 4. message required (isBlank cubre "" y "   " — patrón C3)
        if (r.message() == null || r.message().isBlank()) {
            return error(CODE_MESSAGE_REQUIRED, MSG_MESSAGE_REQUIRED,
                    LogIngestRequest.CAMPO_MESSAGE, index);
        }
        // 5. message > 32768 bytes UTF-8 (una sola obtención, S5-8)
        if (r.message().getBytes(StandardCharsets.UTF_8).length
                > LIMITE_MESSAGE_BYTES) {
            return error(CODE_FIELD_TOO_LONG,
                    msgLimite(LogIngestRequest.CAMPO_MESSAGE,
                            LIMITE_MESSAGE_BYTES, "bytes"),
                    LogIngestRequest.CAMPO_MESSAGE, index);
        }
        // 6. exception opcional > 131072 bytes
        if (r.exception() != null
                && r.exception().getBytes(StandardCharsets.UTF_8).length
                        > LIMITE_EXCEPTION_BYTES) {
            return error(CODE_FIELD_TOO_LONG,
                    msgLimite(LogIngestRequest.CAMPO_EXCEPTION,
                            LIMITE_EXCEPTION_BYTES, "bytes"),
                    LogIngestRequest.CAMPO_EXCEPTION, index);
        }
        // 7. metadata: presente NO objeto → metadata_not_object; objeto → UNA
        //    serialización cuyo String alimenta medida Y parámetro (R-D3);
        //    {} (objeto vacío) VÁLIDO
        String metadataJson = null;
        if (r.metadata() != null) {
            if (!r.metadata().isObject()) {
                return error(CODE_METADATA_NOT_OBJECT, MSG_METADATA_NOT_OBJECT,
                        LogIngestRequest.CAMPO_METADATA, index);
            }
            metadataJson = serializarMetadata(r.metadata());
            if (metadataJson.getBytes(StandardCharsets.UTF_8).length
                    > LIMITE_METADATA_BYTES) {
                return error(CODE_FIELD_TOO_LONG,
                        msgLimite(LogIngestRequest.CAMPO_METADATA,
                                LIMITE_METADATA_BYTES, "bytes"),
                        LogIngestRequest.CAMPO_METADATA, index);
            }
        }
        // 8. source > 200 CARACTERES
        if (r.source() != null && r.source().length() > LIMITE_TEXTO_CHARS) {
            return error(CODE_FIELD_TOO_LONG,
                    msgLimite(LogIngestRequest.CAMPO_SOURCE,
                            LIMITE_TEXTO_CHARS, "caracteres"),
                    LogIngestRequest.CAMPO_SOURCE, index);
        }
        // 9. correlation_id > 200 CARACTERES
        if (r.correlationId() != null
                && r.correlationId().length() > LIMITE_TEXTO_CHARS) {
            return error(CODE_FIELD_TOO_LONG,
                    msgLimite(LogIngestRequest.CAMPO_CORRELATION_ID,
                            LIMITE_TEXTO_CHARS, "caracteres"),
                    LogIngestRequest.CAMPO_CORRELATION_ID, index);
        }
        filas.add(new LogValidado(r.level(), r.environment(), timestamp,
                r.message(), r.exception(), metadataJson, r.source(),
                r.correlationId()));
        return null;
    }

    /** UNA serialización por registro (S5-8): el bean único de Boot, D-3. */
    private String serializarMetadata(JsonNode metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "metadata no serializable (el JsonNode ya lo leyó"
                            + " Jackson — inalcanzable en este flujo)", ex);
        }
    }

    /** Compuesto R5.10 (patrón C3 R3.13): "El campo <f> no puede superar <l> <u>". */
    private static String msgLimite(String campo, long limite, String unidad) {
        return "El campo " + campo + " no puede superar " + limite + " " + unidad;
    }

    private static ErrorValidacion error(String code, String message,
            String field, Integer index) {
        return new ErrorValidacion(code, message, field, index);
    }

    /** D-11 (E5.18, testable en puro): ISO_OFFSET_DATE_TIME del JDK —
     * acepta {@code Z} y {@code ±hh:mm}, RECHAZA el naive. */
    static OffsetDateTime parsearTimestamp(String valor) {
        return OffsetDateTime.parse(valor);
    }
}