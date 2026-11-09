package com.baldev.airline.common.security.resource;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

/**
 * Builds the {@link JwtDecoder} that Spring Security's resource server uses to turn a bearer token into a
 * verified {@link Jwt}: signature first (RS256 only, key chosen by {@code kid}), then {@link AccessTokenValidators}.
 */
public final class AccessTokenDecoders {

    /** Re-fetch the issuer's keys at most this often when a token names an unknown key id. */
    static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(30);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private AccessTokenDecoders() {
    }

    /**
     * Keys from the issuer's JWKS document (gateway and services).
     *
     * <ul>
     *   <li>cached for {@code maxAge}, re-fetched sooner when a token names an unknown key (rotation), at most every
     *       30 s so forged key ids cannot hammer the issuer;</li>
     *   <li>if the issuer is unreachable, the last keys it published keep working; only when none were ever
     *       fetched does verification fail with "unavailable" (503, never 401).</li>
     * </ul>
     *
     * @param client sends the request; with a load-balancer interceptor, {@code http://user-service/...} is resolved
     *               through service discovery
     */
    public static JwtDecoder remote(String jwksUri, Duration maxAge, RestClient client, OAuth2TokenValidator<Jwt> validator) {
        URL url;
        try {
            url = URI.create(jwksUri).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid airline.security.jwt.jwks-uri: " + jwksUri, e);
        }
        AtomicBoolean fetched = new AtomicBoolean();
        JWKSource<SecurityContext> keys = JWKSourceBuilder.create(url, retriever(client, fetched))
                .cache(maxAge.toMillis(), JWKSourceBuilder.DEFAULT_CACHE_REFRESH_TIMEOUT)
                .refreshAheadCache(false)
                .rateLimited(MIN_REFRESH_INTERVAL.toMillis())
                .retrying(false)
                .outageTolerantForever()
                .build();
        return decoder(unknownKeyIsNotAnOutage(keys, fetched), validator);
    }

    /**
     * A token naming a key we do not have makes Nimbus refetch the key set; inside the rate limit it refuses with
     * {@link RateLimitReachedException}. Once keys have been fetched that simply means "no such key" (a bad token,
     * 401). Only before the first successful fetch is it an outage (503).
     */
    private static JWKSource<SecurityContext> unknownKeyIsNotAnOutage(JWKSource<SecurityContext> keys, AtomicBoolean fetched) {
        return (selector, context) -> {
            try {
                return keys.get(selector, context);
            } catch (KeySourceException e) {
                if (fetched.get() && isRateLimited(e)) {
                    return List.of();
                }
                throw e;
            }
        };
    }

    private static boolean isRateLimited(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof RateLimitReachedException) {
                return true;
            }
        }
        return false;
    }

    /** A fixed set of public keys by key id (the issuer verifying its own tokens, or tests). */
    public static JwtDecoder fixed(Map<String, RSAPublicKey> keysById, OAuth2TokenValidator<Jwt> validator) {
        List<JWK> keys = keysById.entrySet().stream()
                .<JWK>map(e -> new RSAKey.Builder(e.getValue()).keyID(e.getKey()).algorithm(JWSAlgorithm.RS256).build())
                .toList();
        return decoder(new ImmutableJWKSet<>(new JWKSet(keys)), validator);
    }

    /** A plain client with short timeouts; {@code customizer} adds e.g. the load-balancer interceptor. */
    public static RestClient jwksClient(UnaryOperator<RestClient.Builder> customizer) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return customizer.apply(RestClient.builder().requestFactory(factory)).build();
    }

    private static JwtDecoder decoder(JWKSource<SecurityContext> keys, OAuth2TokenValidator<Jwt> validator) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(requireKeyId(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys)));
        // Claims are checked by Spring's validators (our clock skew, required exp); turn off Nimbus' own checks.
        processor.setJWTClaimsSetVerifier((claims, context) -> {
        });
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** Our tokens always name their key; a token without {@code kid} is rejected instead of tried against every key. */
    private static JWSKeySelector<SecurityContext> requireKeyId(JWSKeySelector<SecurityContext> delegate) {
        return (header, context) -> header.getKeyID() == null || header.getKeyID().isBlank()
                ? List.of()
                : delegate.selectJWSKeys(header, context);
    }

    private static ResourceRetriever retriever(RestClient client, AtomicBoolean fetched) {
        return url -> {
            try {
                ResponseEntity<String> response = client.get().uri(url.toURI())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .toEntity(String.class);
                MediaType type = response.getHeaders().getContentType();
                fetched.set(true);
                return new Resource(response.getBody(), type != null ? type.toString() : null);
            } catch (RuntimeException | java.net.URISyntaxException e) {
                throw new IOException("Fetching the token signing keys from " + url + " failed: " + e.getMessage(), e);
            }
        };
    }
}
