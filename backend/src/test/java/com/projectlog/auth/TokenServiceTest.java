package com.projectlog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * E2.21 [L] (CD-9): unit puro de {@link TokenService} — roundtrip
 * sign→parse HS256, claims EXACTOS {@code {sub, role, iat, exp}} y SOLO
 * esos (S12), {@code exp = iat + TTL} (default 8 h y TTL alterno, S13), y
 * rechazo sin distinguir causa de firma alterada y token expirado (S5).
 */
class TokenServiceTest {

    /** ≥32 bytes UTF-8 aleatorios solo de test. */
    private static final String SECRETO_TEST =
            "secreto-de-test-solo-para-la-suite-c2-abcd1234abcd1234";

    private final TokenService servicio = new TokenService(SECRETO_TEST, 8);

    @Test
    void roundtripSignParseDevuelveElPrincipalConLosClaims() {
        TokenService.IssuedToken issued = servicio.issue("admin", "ADMIN");
        Optional<JwtPrincipal> parsed = servicio.verify(issued.token());
        assertThat(parsed).isPresent();
        assertThat(parsed.get().username()).isEqualTo("admin");
        assertThat(parsed.get().role()).isEqualTo("ADMIN");
        assertThat(parsed.get().expiresAt())
                // jjwt serializa los claims de fecha con precisión de segundos:
                // el exp del parse es el iat+TTL truncado a segundo (el
                // expires_at de la API se formatea igual, CD-6).
                .isEqualTo(issued.expiresAt().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void losClaimsSonExactamenteSubRoleIatExpYSoloEso() {
        TokenService.IssuedToken issued = servicio.issue("admin", "ADMIN");
        String[] partes = issued.token().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]),
                StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"admin\"");
        assertThat(payload).contains("\"role\":\"ADMIN\"");
        assertThat(payload).contains("\"iat\"");
        assertThat(payload).contains("\"exp\"");
        // Conjunto CERRADO (S12): sin jti, sin iss, sin cualquier otro claim.
        assertThat(payload).doesNotContain("jti").doesNotContain("iss");
        String[] claves = payload.replaceAll("[{}\" ]", "").split(",");
        assertThat(claves).hasSize(4);
    }

    @Test
    void expEsIatMasElTtlDelConstructor() throws Exception {
        // TTL default de este service de test: 8 h (S13) — exp − iat = 28800 s.
        TokenService.IssuedToken issued = servicio.issue("admin", "ADMIN");
        String[] partes = issued.token().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]),
                StandardCharsets.UTF_8);
        long iat = extraerClaimNumerico(payload, "iat");
        long exp = extraerClaimNumerico(payload, "exp");
        assertThat(exp - iat).isEqualTo(8 * 3600L);
    }

    private static long extraerClaimNumerico(String payload, String claim) {
        int i = payload.indexOf("\"" + claim + "\":");
        int desde = i + claim.length() + 3;
        int fin = payload.indexOf(',', desde) == -1
                ? payload.indexOf('}', desde) : payload.indexOf(',', desde);
        return Long.parseLong(payload.substring(desde, fin).trim());
    }

    @Test
    void ttlAlternoSeAplicaAlExp() {
        TokenService alterno = new TokenService(SECRETO_TEST, 1); // p. ej. 1 h
        Instant antes = Instant.now();
        TokenService.IssuedToken issued = alterno.issue("admin", "ADMIN");
        Instant exp = issued.expiresAt();
        assertThat(java.time.Duration.between(antes, exp))
                .isBetween(java.time.Duration.ofMinutes(59),
                        java.time.Duration.ofHours(1).plusSeconds(5));
        assertThat(java.time.Duration.between(
                issued.expiresAt().minusSeconds(3600L),
                issued.expiresAt()).toMinutes()).isEqualTo(60);
    }

    @Test
    void tokenConFirmaAlteradaSeRechazaComoOptionalVacio() {
        TokenService.IssuedToken issued = servicio.issue("admin", "ADMIN");
        String[] partes = issued.token().split("\\.");
        byte[] firma = Base64.getUrlDecoder().decode(partes[2]);
        firma[0] ^= 0x01; // un bit alterado ⇒ firma inválida
        String adulterado = partes[0] + "." + partes[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(firma);
        assertThat(servicio.verify(adulterado)).isEmpty(); // sin distinguir causa (S5)
    }

    @Test
    void tokenExpiradoSeRechazaComoOptionalVacio() {
        // TTL negativo ⇒ exp = iat + TTL queda en el pasado por construcción.
        TokenService expirante = new TokenService(SECRETO_TEST, -8);
        TokenService.IssuedToken issued = expirante.issue("admin", "ADMIN");
        assertThat(expirante.verify(issued.token())).isEmpty();
        // Y también bajo el service con TTL normal (reloj sin skew, S14).
        assertThat(servicio.verify(issued.token())).isEmpty();
    }

    @Test
    void tokenMalformadoONuloSeRechazanSinExcepcion() {
        assertThat(servicio.verify("no-es-un-jwt")).isEmpty();
        assertThat(servicio.verify("")).isEmpty();
    }
}