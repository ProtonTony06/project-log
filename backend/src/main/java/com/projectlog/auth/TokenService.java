package com.projectlog.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

/**
 * Encapsula sign y parse/verify del JWT (S15, CD-3). Algoritmo HS256 (S11)
 * con la clave derivada de los BYTES UTF-8 CRUDOS de {@code JWT_SECRET} —
 * exactamente la misma convención que mide el validador de fail-fast de C1
 * (≥32 bytes UTF-8); sin Base64 ni HKDF: una sola convención de derivación
 * en todo el sistema (S11).
 *
 * <p>Claims conjunto CERRADO {@code {sub, role, iat, exp}} (S12/L3) — sin
 * {@code jti}, sin {@code iss}. {@code exp = iat + TTL} con TTL de
 * {@code projectlog.security.jwt-ttl-hours} (default 8, S13).</p>
 *
 * <p>Sin margen de clock-skew (S14): jjwt deja
 * {@code allowedClockSkewSeconds = 0} por defecto y NO se invoca
 * {@code clockSkewSeconds(...)} — reloj host NTP-sincronizado (TRD §9), red
 * interna con un único firmante; añadir margen solo amplía la ventana.</p>
 *
 * <p>API 0.13.0 exclusivamente no deprecada: {@code Jwts.SIG.HS256},
 * {@code parser().verifyWith(...)}, {@code parseSignedClaims(...)}; la
 * superficie compilable es solo jjwt-api (S10). El servicio no loguea nada
 * (S16) — jamás el token ni fragmentos.</p>
 */
@Component
public class TokenService {

    private final SecretKey secretKey;

    private final long ttlSeconds;

    public TokenService(
            @Value("${JWT_SECRET}") String jwtSecret,
            @Value("${projectlog.security.jwt-ttl-hours}") long ttlHours) {
        // S11: bytes UTF-8 crudos, SIN Base64/HKDF — misma unidad que mide C1.
        this.secretKey = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.ttlSeconds = ttlHours * 3600L;
    }

    /**
     * Emite un token con claims exactos {@code {sub, role, iat, exp}} y
     * devuelve token + {@code exp} juntos (CD-6: {@code expires_at} de las
     * respuestas deriva de aquí sin re-parsear el token).
     */
    public IssuedToken issue(String username, String role) {
        Instant iat = Instant.now();
        Instant exp = iat.plusSeconds(ttlSeconds); // S13: exp = iat + TTL
        String token = Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(iat))
                .expiration(Date.from(exp))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, exp);
    }

    /**
     * Verifica firma y vigencia; devuelve vacío ante CUALQUIER fallo
     * (malformado, firma inválida, expirado) sin distinguir causa (S5: un
     * único literal de 401, sin oráculo).
     */
    public Optional<JwtPrincipal> verify(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new JwtPrincipal(
                    claims.getSubject(),
                    claims.get("role", String.class),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            // MalformedJwtException / SignatureException / ExpiredJwtException /
            // IllegalArgumentException(token nulo o vacío) — mismo resultado.
            return Optional.empty();
        }
    }

    /** Resultado de {@link #issue}: token compacto + su expiración (CD-6). */
    public record IssuedToken(String token, Instant expiresAt) {
    }
}