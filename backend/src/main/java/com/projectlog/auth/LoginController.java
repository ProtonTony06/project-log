package com.projectlog.auth;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/panel/auth/login (N6, CD-6, S17/L1). Entrada
 * {@code {username, password}} → 200 {@code {token, username, expires_at}}
 * · 401 {@code {"code": "invalid_credentials", ...}}.
 *
 * <p>El MISMO 401/literal cubre las tres ramas (S17): usuario inexistente,
 * password incorrecto y cuerpo malformado ({@code @ExceptionHandler} en el
 * propio controller — no el 400 por defecto de Boot). Hardening anti
 * user-enumeración de tiempo: ante usuario inexistente se ejecuta
 * {@code matches} contra un hash dummy constante para igualar la latencia
 * de ambas ramas.</p>
 *
 * <p>{@code expires_at} sale del {@code exp} del token (CD-6, sin
 * re-derivar), formateado determinista UTC con sufijo Z y precisión de
 * segundos (riesgo 7, L2). Sin logging de credenciales ni token (S16).</p>
 */
@RestController
public class LoginController {

    /** Formatter compartido con {@link MeController} (E2.5: idéntico formato Z). */
    public static final DateTimeFormatter EXPIRES_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC);

    /** Literal único de los 3 fallos (anti user-enumeración, S17). */
    static final String CODE_INVALID_CREDENTIALS = "invalid_credentials";

    /**
     * Hash dummy constante durante la vida del proceso, generado con el
     * PROPIO encoder en el constructor (garantizado PHC parseable, S17):
     * su {@code matches} cuesta lo mismo que un hash real ⇒ ambas ramas
     * (usuario inexistente / password mal) tienen latencia equivalente.
     */
    private final String hashDummy;

    private final JdbcTemplate jdbcTemplate;

    private final PasswordEncoder passwordEncoder;

    private final TokenService tokenService;

    private final ObjectMapper objectMapper;

    public LoginController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
            TokenService tokenService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.hashDummy = passwordEncoder.encode(
                "projectlog-dummy-check-para-igualar-tiempo");
    }

    @PostMapping("/api/v1/panel/auth/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest request) {
        String username = request.username();
        String password = request.password();
        if (username == null || password == null) {
            return credencialesInvalidas();
        }
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT password_hash FROM users WHERE username = ?",
                String.class, username);
        String hash = hashes.isEmpty() ? hashDummy : hashes.get(0);
        if (!passwordEncoder.matches(password, hash)) {
            return credencialesInvalidas(); // mismo camino para ambas causas (S17)
        }
        TokenService.IssuedToken emitted = tokenService.issue(username, "ADMIN");
        return ResponseEntity.ok(new LoginResponse(
                emitted.token(),
                username,
                EXPIRES_AT_FORMATTER.format(emitted.expiresAt())));
    }

    /**
     * Cuerpo malformado (JSON no parseable) → 401 {@code invalid_credentials},
     * NO el 400 por defecto (S17/CD-6).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> cuerpoMalformado(
            HttpMessageNotReadableException ex) {
        return credencialesInvalidas();
    }

    private ResponseEntity<Object> credencialesInvalidas() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.valueToTree(Map.of(
                        "code", CODE_INVALID_CREDENTIALS,
                        "message", "Credenciales inválidas")));
    }
}