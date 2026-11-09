package com.baldev.airline.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Public keys of the token issuer (user-service), from its JWKS document, cached by key id ({@code kid}).
 *
 * <p>Keys are refreshed every {@code maxAge}, and immediately when a token names an unknown {@code kid} (key
 * rotation), at most once per {@code minRefreshInterval} so forged kids cannot hammer the issuer. If the issuer is
 * unreachable, the last known keys keep working.
 */
public class JwksKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(JwksKeyResolver.class);

    /** One entry of a JWKS document (RFC 7517), as fetched. */
    public record Jwk(String kty, String kid, String use, String alg, String n, String e) {
    }

    /** Thrown when no key can be obtained at all (issuer unreachable and nothing cached). */
    public static class KeysUnavailableException extends RuntimeException {
        public KeysUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Supplier<List<Jwk>> fetcher;
    private final Clock clock;
    private final Duration maxAge;
    private final Duration minRefreshInterval;

    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant fetchedAt = Instant.EPOCH;
    private volatile Instant lastAttempt = Instant.EPOCH;

    public JwksKeyResolver(Supplier<List<Jwk>> fetcher, Clock clock, Duration maxAge, Duration minRefreshInterval) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.maxAge = maxAge;
        this.minRefreshInterval = minRefreshInterval;
    }

    /** A resolver over a fixed set of keys (tests, or an issuer verifying its own tokens). */
    public static JwksKeyResolver fixed(Map<String, PublicKey> keys) {
        JwksKeyResolver resolver = new JwksKeyResolver(List::of, Clock.systemUTC(), Duration.ofDays(36500), Duration.ofDays(36500));
        resolver.keys = Map.copyOf(keys);
        resolver.fetchedAt = Instant.now();
        resolver.lastAttempt = Instant.now();
        return resolver;
    }

    /** @return the key for {@code kid}, or {@code null} if the issuer does not publish it. */
    public PublicKey resolve(String kid) {
        if (kid == null) {
            return null;
        }
        Instant now = clock.instant();
        PublicKey key = keys.get(kid);
        boolean stale = now.isAfter(fetchedAt.plus(maxAge));
        if ((key == null || stale) && now.isAfter(lastAttempt.plus(minRefreshInterval))) {
            refresh(now);
            key = keys.get(kid);
        }
        return key;
    }

    private synchronized void refresh(Instant now) {
        if (!now.isAfter(lastAttempt.plus(minRefreshInterval))) {
            return; // another thread just refreshed
        }
        lastAttempt = now;
        try {
            Map<String, PublicKey> fresh = new HashMap<>();
            for (Jwk jwk : fetcher.get()) {
                if ("RSA".equals(jwk.kty()) && jwk.kid() != null && (jwk.use() == null || "sig".equals(jwk.use()))) {
                    fresh.put(jwk.kid(), toPublicKey(jwk));
                }
            }
            keys = Map.copyOf(fresh);
            fetchedAt = now;
            log.debug("Loaded {} signing keys from the issuer: {}", fresh.size(), fresh.keySet());
        } catch (RuntimeException e) {
            if (keys.isEmpty()) {
                throw new KeysUnavailableException("Token signing keys are unavailable", e);
            }
            log.warn("Refreshing signing keys failed; using the {} cached keys: {}", keys.size(), e.toString());
        }
    }

    static PublicKey toPublicKey(Jwk jwk) {
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, decoder.decode(jwk.n())),
                    new BigInteger(1, decoder.decode(jwk.e())));
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid RSA JWK " + jwk.kid(), e);
        }
    }
}
