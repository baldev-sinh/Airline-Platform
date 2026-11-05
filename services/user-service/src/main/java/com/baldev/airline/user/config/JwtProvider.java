package com.baldev.airline.user.config;

import com.baldev.airline.user.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Issues short-lived access tokens. The gateway validates signature, expiry and issuer. */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final SecurityProperties.Jwt jwt;
    private final Clock clock;

    public JwtProvider(SecurityProperties properties, Clock clock) {
        this.jwt = properties.security().jwt();
        this.key = Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(jwt.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwt.expiration())))
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .signWith(key)
                .compact();
    }
}
