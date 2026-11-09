package com.baldev.airline.gateway.security;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.test.context.DynamicPropertyRegistry;

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
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Black-box fixtures for the gateway security parity tests. They depend on nothing inside the gateway's security
 * implementation, so the same tests run unchanged before and after the move to Spring Security:
 * <ul>
 *   <li>{@link Backend}: one local HTTP server standing in for user-service (it publishes the JWKS) and for every
 *       routed service (it answers 200 and records the headers it received).</li>
 *   <li>{@link #token}: RS256 access tokens shaped exactly like user-service's (kid, jti, iss, aud, sub, iat, exp,
 *       email, roles), plus deliberately broken variants.</li>
 * </ul>
 */
final class ParityFixtures {

    static final String ISSUER = "airline-user-service";
    static final String AUDIENCE = "airline-api";
    static final String KID = "parity-key";
    static final KeyPair KEYS = rsa();
    static final KeyPair OTHER_KEYS = rsa();

    static final List<String> SERVICES = List.of("user-service", "airline-core-service", "location-service",
            "flight-ops-service", "pricing-service", "seat-service", "ancillary-service", "booking-service",
            "payment-service", "notification-service");

    private ParityFixtures() {
    }

    /** Points every routed service (and the JWKS lookup through user-service) at {@code baseUrl}. */
    static void routeAllServicesTo(DynamicPropertyRegistry registry, String baseUrl) {
        for (String service : SERVICES) {
            registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri", () -> baseUrl);
        }
    }

    // ------------------------------------------------------------------ tokens

    /** Claims and header of a token; start from {@link #valid} and change one thing per test. */
    record Spec(String kid, String issuer, String audience, String subject, List<String> roles,
                Instant issuedAt, Instant expiresAt, KeyPair keys, String jti) {

        static Spec valid(long userId, String... roles) {
            Instant now = Instant.now();
            return new Spec(KID, ISSUER, AUDIENCE, String.valueOf(userId), List.of(roles),
                    now.minusSeconds(5), now.plusSeconds(900), KEYS, UUID.randomUUID().toString());
        }

        Spec kid(String value) {
            return new Spec(value, issuer, audience, subject, roles, issuedAt, expiresAt, keys, jti);
        }

        Spec issuer(String value) {
            return new Spec(kid, value, audience, subject, roles, issuedAt, expiresAt, keys, jti);
        }

        Spec audience(String value) {
            return new Spec(kid, issuer, value, subject, roles, issuedAt, expiresAt, keys, jti);
        }

        Spec subject(String value) {
            return new Spec(kid, issuer, audience, value, roles, issuedAt, expiresAt, keys, jti);
        }

        Spec issuedAt(Instant value) {
            return new Spec(kid, issuer, audience, subject, roles, value, expiresAt, keys, jti);
        }

        Spec expiresAt(Instant value) {
            return new Spec(kid, issuer, audience, subject, roles, issuedAt, value, keys, jti);
        }

        Spec signedBy(KeyPair value) {
            return new Spec(kid, issuer, audience, subject, roles, issuedAt, expiresAt, value, jti);
        }
    }

    static String token(Spec spec) {
        var builder = Jwts.builder();
        if (spec.kid() != null) {
            builder.header().keyId(spec.kid()).and();
        }
        return builder
                .id(spec.jti())
                .issuer(spec.issuer())
                .audience().add(spec.audience()).and()
                .subject(spec.subject())
                .issuedAt(Date.from(spec.issuedAt()))
                .expiration(Date.from(spec.expiresAt()))
                .claim("email", "parity@example.com")
                .claim("roles", spec.roles())
                .signWith(spec.keys().getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    static String token(long userId, String... roles) {
        return token(Spec.valid(userId, roles));
    }

    /** HS256 with a well-known key: must never be accepted, whatever the payload says. */
    static String hmacToken() {
        return Jwts.builder().header().keyId(KID).and()
                .issuer(ISSUER).audience().add(AUDIENCE).and().subject("1")
                .claim("roles", List.of("ROLE_SYSTEM_ADMIN"))
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(new byte[32])).compact();
    }

    /** {@code alg: none} with an admin payload and no signature. */
    static String unsignedToken() {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        long now = Instant.now().getEpochSecond();
        String header = "{\"alg\":\"none\",\"kid\":\"" + KID + "\"}";
        String payload = "{\"iss\":\"" + ISSUER + "\",\"aud\":\"" + AUDIENCE + "\",\"sub\":\"1\",\"iat\":" + now
                + ",\"exp\":" + (now + 600) + ",\"roles\":[\"ROLE_SYSTEM_ADMIN\"]}";
        return b64.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".";
    }

    static String jwks() {
        RSAPublicKey key = (RSAPublicKey) KEYS.getPublic();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + KID + "\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\""
                + b64.encodeToString(unsigned(key.getModulus())) + "\",\"e\":\""
                + b64.encodeToString(unsigned(key.getPublicExponent())) + "\"}]}";
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
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

    // ------------------------------------------------------------------ backend stub

    /**
     * Local stand-in for every backend. {@code GET /.well-known/jwks.json} publishes {@link #KEYS} (or fails when
     * {@code jwksAvailable} is false); any other request answers 200 {@code {"proxied":true}} and records its
     * headers under {@code METHOD path}.
     */
    static final class Backend implements AutoCloseable {

        private final HttpServer server;
        private final Map<String, Map<String, List<String>>> received = new ConcurrentHashMap<>();
        volatile boolean jwksAvailable = true;

        Backend() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        /** Headers (lower-case names) of the last proxied request to {@code method path}, or null if none arrived. */
        Map<String, List<String>> headersOf(String method, String path) {
            return received.get(method + " " + path);
        }

        void forget() {
            received.clear();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/.well-known/jwks.json".equals(path)) {
                if (jwksAvailable) {
                    respond(exchange, 200, jwks());
                } else {
                    respond(exchange, 500, "{\"error\":\"down\"}");
                }
                return;
            }
            Map<String, List<String>> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(java.util.Locale.ROOT), List.copyOf(values)));
            received.put(exchange.getRequestMethod() + " " + path, headers);
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, "{\"proxied\":true}");
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

}
