package com.baldev.airline.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Validates access tokens issued by user-service: signature, expiry (with small clock skew) and issuer. */
@Component
public class JwtVerifier {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final SecretKey key;
    private final String issuer;

    public JwtVerifier(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer();
    }

    public record VerifiedToken(Long userId, String email, List<String> roles) {
    }

    /** @return the verified identity, or empty if the token is missing, malformed, expired or forged. */
    public Optional<VerifiedToken> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clockSkewSeconds(CLOCK_SKEW.toSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            return Optional.of(new VerifiedToken(userId, claims.get("email", String.class), roles(claims)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static List<String> roles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
