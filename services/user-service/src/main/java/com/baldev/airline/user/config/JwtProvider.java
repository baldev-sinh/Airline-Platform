package com.baldev.airline.user.config;

import com.baldev.airline.user.model.User;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues short-lived RS256 access tokens. The gateway and every service verify them with the public keys from
 * {@code /.well-known/jwks.json} (signature, issuer, audience, expiry) and check revocation.
 */
@Component
public class JwtProvider {

    /** An issued access token and the facts needed to revoke it. */
    public record AccessToken(String value, String tokenId, Instant expiresAt) {
    }

    private final SigningKeys keys;
    private final SecurityProperties.Jwt jwt;
    private final Duration ttl;
    private final Clock clock;

    public JwtProvider(SigningKeys keys, SecurityProperties properties, Clock clock) {
        this.keys = keys;
        this.jwt = properties.security().jwt();
        this.ttl = properties.security().tokens().accessTokenTtl();
        this.clock = clock;
    }

    public AccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .header().keyId(keys.currentKeyId()).and()
                .id(tokenId)
                .issuer(jwt.issuer())
                .audience().add(jwt.audience()).and()
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .signWith(keys.privateKey(), Jwts.SIG.RS256)
                .compact();
        return new AccessToken(token, tokenId, expiresAt);
    }

    public Duration accessTokenTtl() {
        return ttl;
    }
}
