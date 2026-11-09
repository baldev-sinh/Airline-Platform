package com.baldev.airline.user.controller;

import com.baldev.airline.user.config.Jwk;
import com.baldev.airline.user.config.SigningKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Public keys for verifying access tokens (RFC 7517 JWK Set). Fetched by the gateway and every service through
 * Eureka; not routed by the gateway (it only routes /auth and /api), so it is reachable inside the network only.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final SigningKeys keys;

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, List<Jwk>>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(Map.of("keys", keys.publishedKeys()));
    }
}
