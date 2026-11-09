package com.baldev.airline.common.security.parity;

import com.baldev.airline.common.web.CurrentUser;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A minimal "business service" built only from common-lib's auto-configuration, with endpoints shaped like the
 * real ones: an endpoint that needs the caller ({@link CurrentUser}), an anonymous read, a write, and an internal
 * endpoint. Used by the service security parity tests.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        // Unrelated infrastructure whose optional dependencies are only partly on common-lib's test classpath.
        "org.springframework.cloud.openfeign.FeignAutoConfiguration",
        "com.baldev.airline.common.feign.FeignAutoConfiguration",
        "com.baldev.airline.common.kafka.KafkaErrorHandlingAutoConfiguration",
        "com.baldev.airline.common.outbox.OutboxAutoConfiguration",
        "com.baldev.airline.common.cache.RedisCachingAutoConfiguration"
})
@Import(ServiceParityApp.Endpoints.class)
public class ServiceParityApp {

    static final String ISSUER = "airline-user-service";
    static final String AUDIENCE = "airline-api";
    static final String KID = "parity-key";
    static final KeyPair KEYS = rsa();

    @RestController
    static class Endpoints {

        @GetMapping("/api/me")
        Map<String, Object> me(CurrentUser user) {
            Map<String, Object> body = new TreeMap<>();
            body.put("id", user.id());
            body.put("email", user.email());
            body.put("roles", user.roles().stream().map(Enum::name).sorted().toList());
            return body;
        }

        @GetMapping("/api/public")
        Map<String, Object> publicRead(@RequestHeader(value = "X-User-Id", required = false) String claimed) {
            return Map.of("ok", true);
        }

        @PostMapping("/api/things")
        Map<String, Object> write(CurrentUser user) {
            return Map.of("createdBy", user.id());
        }

        @GetMapping("/internal/thing")
        Map<String, Object> internalRead() {
            return Map.of("internal", true);
        }

        @PostMapping("/internal/thing")
        Map<String, Object> internalWrite() {
            return Map.of("internal", true);
        }
    }

    // ------------------------------------------------------------------ tokens and JWKS

    static String token(long userId, String... roles) {
        return token(String.valueOf(userId), KID, ISSUER, Instant.now().plusSeconds(900), roles);
    }

    static String token(String subject, String kid, String issuer, Instant expiresAt, String... roles) {
        var builder = Jwts.builder();
        if (kid != null) {
            builder.header().keyId(kid).and();
        }
        return builder.id(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience().add(AUDIENCE).and()
                .subject(subject)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(expiresAt))
                .claim("email", "parity@example.com")
                .claim("roles", List.of(roles))
                .signWith(KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /** Serves {@link #KEYS} as a JWKS document, or 500 when {@code available} is false. */
    static final class Jwks implements AutoCloseable {

        private final HttpServer server;
        volatile boolean available = true;

        Jwks() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/.well-known/jwks.json", exchange -> {
                byte[] body = (available ? document() : "{}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(available ? 200 : 500, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
        }

        String uri() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json";
        }

        private static String document() {
            RSAPublicKey key = (RSAPublicKey) KEYS.getPublic();
            Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
            return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + KID + "\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\""
                    + b64.encodeToString(unsigned(key.getModulus())) + "\",\"e\":\""
                    + b64.encodeToString(unsigned(key.getPublicExponent())) + "\"}]}";
        }

        private static byte[] unsigned(BigInteger value) {
            byte[] bytes = value.toByteArray();
            return bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
