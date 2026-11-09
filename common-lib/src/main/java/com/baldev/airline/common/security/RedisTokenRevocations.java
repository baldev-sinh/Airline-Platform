package com.baldev.airline.common.security;

import com.baldev.airline.common.security.AccessTokenVerifier.VerifiedToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Revocations in Redis, shared by user-service (writes) and the gateway and services (read). Keys expire on
 * their own once every token they could affect has expired, so the store never grows:
 * <ul>
 *   <li>{@code airline:auth:revoked-jti:<jti>} until that token's expiry</li>
 *   <li>{@code airline:auth:revoked-before:<userId>} = epoch second, for one access-token lifetime</li>
 * </ul>
 *
 * <p>If Redis cannot be reached the check fails open by default (a revoked token works until it expires, at
 * most one access-token lifetime) so a Redis outage does not log every customer out. Set
 * {@code airline.security.revocation.fail-open=false} to reject tokens instead.
 */
public class RedisTokenRevocations implements TokenRevocations {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenRevocations.class);
    static final String JTI_PREFIX = "airline:auth:revoked-jti:";
    static final String USER_PREFIX = "airline:auth:revoked-before:";

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final boolean failOpen;

    public RedisTokenRevocations(StringRedisTemplate redis, Clock clock, boolean failOpen) {
        this.redis = redis;
        this.clock = clock;
        this.failOpen = failOpen;
    }

    @Override
    public boolean isRevoked(VerifiedToken token) {
        try {
            List<String> values = redis.opsForValue().multiGet(List.of(
                    JTI_PREFIX + token.tokenId(), USER_PREFIX + token.userId()));
            if (values == null) {
                return false;
            }
            if (values.get(0) != null) {
                return true;
            }
            String before = values.get(1);
            // Inclusive: iat has one-second precision, so a token minted in the same second as the revocation
            // (e.g. by whoever held a stolen refresh token) must not survive it. A sign-in within that very
            // second is revoked too and simply has to be repeated.
            return before != null && token.issuedAt() != null
                    && token.issuedAt().getEpochSecond() <= Long.parseLong(before);
        } catch (RuntimeException e) {
            log.warn("Token revocation check failed ({}); {}", e.toString(), failOpen ? "allowing" : "rejecting");
            return !failOpen;
        }
    }

    @Override
    public void revokeToken(String tokenId, Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (tokenId != null && !ttl.isNegative() && !ttl.isZero()) {
            redis.opsForValue().set(JTI_PREFIX + tokenId, "1", ttl);
        }
    }

    @Override
    public void revokeAllIssuedBefore(Long userId, Instant before, Duration accessTokenLifetime) {
        redis.opsForValue().set(USER_PREFIX + userId, String.valueOf(before.getEpochSecond()),
                accessTokenLifetime.plusMinutes(1));
    }
}
