package com.baldev.airline.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.SecurityException;

import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Verifies access tokens issued by user-service: RS256 signature against the issuer's published keys (JWKS),
 * issuer, audience, expiry (30 s clock skew), and revocation. Used by the API gateway and, independently, by every
 * service, so a request that bypasses the gateway gains nothing by forging identity headers.
 */
public class AccessTokenVerifier {

    public static final String ALGORITHM = "RS256";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    /** The identity in a verified token. */
    public record VerifiedToken(Long userId, String email, List<String> roles, String tokenId, Instant issuedAt,
                                Instant expiresAt) {
    }

    /** Why a token was not accepted. {@link Reason#KEYS_UNAVAILABLE} is the issuer's fault, not the client's. */
    public enum Reason { MISSING, INVALID, REVOKED, KEYS_UNAVAILABLE }

    public static final class Rejected extends Exception {
        private final Reason reason;

        public Rejected(Reason reason, String message) {
            super(message, null, false, false);
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    private final JwksKeyResolver keys;
    private final TokenRevocations revocations;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public AccessTokenVerifier(JwksKeyResolver keys, TokenRevocations revocations, String issuer, String audience,
                               Clock clock) {
        this.keys = keys;
        this.revocations = revocations;
        this.issuer = issuer;
        this.audience = audience;
        this.clock = clock;
    }

    /** @return the identity in {@code token}; never returns an unverified identity. */
    public VerifiedToken verify(String token) throws Rejected {
        if (token == null || token.isBlank()) {
            throw new Rejected(Reason.MISSING, "Missing access token");
        }
        Jws<Claims> jws;
        try {
            jws = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(ProtectedHeader header) {
                            // Only RS256: never let the token choose a weaker or symmetric algorithm.
                            if (!ALGORITHM.equals(header.getAlgorithm())) {
                                throw new SecurityException("Unexpected algorithm " + header.getAlgorithm());
                            }
                            Key key = keys.resolve(header.getKeyId());
                            if (key == null) {
                                throw new SecurityException("Unknown signing key " + header.getKeyId());
                            }
                            return key;
                        }
                    })
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .clockSkewSeconds(CLOCK_SKEW.toSeconds())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);
        } catch (JwksKeyResolver.KeysUnavailableException e) {
            throw new Rejected(Reason.KEYS_UNAVAILABLE, "Token signing keys are unavailable");
        } catch (JwtException | IllegalArgumentException e) {
            throw new Rejected(Reason.INVALID, "Invalid access token");
        }

        Claims claims = jws.getPayload();
        VerifiedToken verified;
        try {
            verified = new VerifiedToken(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    roles(claims),
                    claims.getId(),
                    Optional.ofNullable(claims.getIssuedAt()).map(Date::toInstant).orElse(null),
                    Optional.ofNullable(claims.getExpiration()).map(Date::toInstant).orElse(null));
        } catch (RuntimeException e) {
            throw new Rejected(Reason.INVALID, "Invalid access token");
        }
        if (revocations.isRevoked(verified.tokenId(), verified.userId(), verified.issuedAt())) {
            throw new Rejected(Reason.REVOKED, "Access token has been revoked");
        }
        return verified;
    }

    private static List<String> roles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
