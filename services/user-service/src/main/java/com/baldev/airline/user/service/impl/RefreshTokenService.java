package com.baldev.airline.user.service.impl;

import com.baldev.airline.common.exception.UnauthorizedException;
import com.baldev.airline.common.security.TokenRevocations;
import com.baldev.airline.user.config.SecurityProperties;
import com.baldev.airline.user.model.RefreshToken;
import com.baldev.airline.user.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Issues, rotates and revokes refresh tokens (see {@link RefreshToken} for the rotation and reuse rules). */
@Slf4j
@Service
public class RefreshTokenService {

    /** The user a refresh was accepted for, and the next refresh token (shown once, never stored in clear). */
    public record Rotation(Long userId, String refreshToken, Instant expiresAt) {
    }

    private static final String INVALID = "Invalid or expired refresh token";

    private final RefreshTokenRepository repository;
    private final TokenRevocations revocations;
    private final Duration ttl;
    private final Duration accessTokenTtl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, TokenRevocations revocations,
                               SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.revocations = revocations;
        this.ttl = properties.security().tokens().refreshTokenTtl();
        this.accessTokenTtl = properties.security().tokens().accessTokenTtl();
        this.clock = clock;
    }

    /** Starts a new family (a new sign-in). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Rotation issue(Long userId, String userAgent) {
        return create(userId, UUID.randomUUID().toString(), userAgent).rotation();
    }

    /**
     * Exchanges a refresh token for the next one. A reused (already rotated or revoked) token revokes its whole
     * family and every access token of the user, because someone else holds a copy.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String refreshToken, String userAgent) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHashForUpdate(hash(refreshToken))
                .orElseThrow(() -> new UnauthorizedException(INVALID));
        if (current.getRevokedAt() != null) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            revocations.revokeAllIssuedBefore(current.getUserId(), now, accessTokenTtl);
            log.warn("SECURITY: reuse of a revoked refresh token for userId={} family={}; revoked {} token(s) and "
                    + "all access tokens", current.getUserId(), current.getFamilyId(), revoked);
            throw new UnauthorizedException(INVALID);
        }
        if (!current.isActive(now)) {
            throw new UnauthorizedException(INVALID);
        }
        Created next = create(current.getUserId(), current.getFamilyId(), userAgent);
        current.setRevokedAt(now);
        current.setReplacedById(next.entity().getId());
        return next.rotation();
    }

    /** Logout on one device: revokes the token's family. Unknown tokens are ignored (logout is idempotent). */
    @Transactional
    public void revokeFamily(String refreshToken) {
        repository.findByTokenHashForUpdate(hash(refreshToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    /** "Sign out everywhere": every refresh token and every access token issued until now. */
    @Transactional
    public int revokeAll(Long userId) {
        Instant now = clock.instant();
        int revoked = repository.revokeAllForUser(userId, now);
        revocations.revokeAllIssuedBefore(userId, now, accessTokenTtl);
        return revoked;
    }

    /** Expired tokens are useless; keep them a week for forensic queries, then delete them. */
    @Scheduled(cron = "${airline.security.tokens.cleanup-cron:0 15 4 * * *}")
    @Transactional
    public void deleteExpired() {
        int deleted = repository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(7)));
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }

    private record Created(RefreshToken entity, Rotation rotation) {
    }

    private Created create(Long userId, String familyId, String userAgent) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        RefreshToken saved = repository.saveAndFlush(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(value))
                .familyId(familyId)
                .createdAt(now)
                .expiresAt(expiresAt)
                .userAgent(userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent)
                .build());
        return new Created(saved, new Rotation(userId, value, expiresAt));
    }

    static String hash(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException(INVALID);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
