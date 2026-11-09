package com.baldev.airline.common.security;


import java.time.Duration;
import java.time.Instant;

/**
 * Revoked access tokens. Access tokens are short-lived and stateless; revocation covers the window until they
 * expire: logout revokes one token ({@code jti}), "sign out everywhere", a password change or a ban revokes every
 * token issued to a user before a point in time.
 */
public interface TokenRevocations {

    TokenRevocations NONE = new TokenRevocations() {
        @Override
        public boolean isRevoked(String tokenId, Long userId, Instant issuedAt) {
            return false;
        }

        @Override
        public void revokeToken(String tokenId, Instant expiresAt) {
        }

        @Override
        public void revokeAllIssuedBefore(Long userId, Instant before, Duration accessTokenLifetime) {
        }
    };

    /**
     * @param tokenId  the token's {@code jti}
     * @param userId   the token's subject
     * @param issuedAt the token's {@code iat}, or null
     */
    boolean isRevoked(String tokenId, Long userId, Instant issuedAt);

    /** Revokes one access token until it would have expired anyway. */
    void revokeToken(String tokenId, Instant expiresAt);

    /** Revokes every access token of {@code userId} issued before {@code before}. */
    void revokeAllIssuedBefore(Long userId, Instant before, Duration accessTokenLifetime);
}
