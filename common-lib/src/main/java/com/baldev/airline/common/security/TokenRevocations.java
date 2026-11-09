package com.baldev.airline.common.security;

import com.baldev.airline.common.security.AccessTokenVerifier.VerifiedToken;

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
        public boolean isRevoked(VerifiedToken token) {
            return false;
        }

        @Override
        public void revokeToken(String tokenId, Instant expiresAt) {
        }

        @Override
        public void revokeAllIssuedBefore(Long userId, Instant before, Duration accessTokenLifetime) {
        }
    };

    boolean isRevoked(VerifiedToken token);

    /** Revokes one access token until it would have expired anyway. */
    void revokeToken(String tokenId, Instant expiresAt);

    /** Revokes every access token of {@code userId} issued before {@code before}. */
    void revokeAllIssuedBefore(Long userId, Instant before, Duration accessTokenLifetime);
}
