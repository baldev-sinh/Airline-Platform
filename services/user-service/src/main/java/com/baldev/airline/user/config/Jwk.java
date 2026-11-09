package com.baldev.airline.user.config;

/** One public key of the JWK Set (RFC 7517) published at {@code /.well-known/jwks.json}. */
public record Jwk(String kty, String kid, String use, String alg, String n, String e) {
}
