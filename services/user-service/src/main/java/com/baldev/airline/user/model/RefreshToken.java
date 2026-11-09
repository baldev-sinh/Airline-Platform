package com.baldev.airline.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A refresh token. Only its SHA-256 hash is stored, so a database leak does not leak usable tokens.
 *
 * <p>Every token belongs to a {@code familyId} (one sign-in on one device). Refreshing revokes the presented token
 * and issues the next one in the same family. Presenting a token that was already used means it was stolen (the
 * thief and the owner both hold it), so the whole family is revoked and the user must sign in again.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "refresh_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "tokenHash"),
        indexes = {
                @Index(name = "idx_refresh_token_family", columnList = "familyId"),
                @Index(name = "idx_refresh_token_user", columnList = "userId"),
                @Index(name = "idx_refresh_token_expires", columnList = "expiresAt")
        })
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** Hex SHA-256 of the token value. */
    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 36)
    private String familyId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    /** The token that replaced this one on refresh; {@code null} if revoked by logout or never used. */
    private Long replacedById;

    @Column(length = 255)
    private String userAgent;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
