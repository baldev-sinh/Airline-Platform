package com.baldev.airline.user.service.impl;

import com.baldev.airline.common.exception.UnauthorizedException;
import com.baldev.airline.common.security.TokenRevocations;
import com.baldev.airline.user.config.SecurityProperties;
import com.baldev.airline.user.model.RefreshToken;
import com.baldev.airline.user.repository.RefreshTokenRepository;
import com.baldev.airline.user.service.impl.RefreshTokenService.Rotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final TokenRevocations revocations = mock(TokenRevocations.class);
    private final AtomicLong ids = new AtomicLong(100);
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(new SecurityProperties.Security(
                new SecurityProperties.Jwt("iss", "aud"), new SecurityProperties.Signing(null, null, List.of(), true),
                new SecurityProperties.Tokens(Duration.ofMinutes(15), Duration.ofDays(14))), null);
        service = new RefreshTokenService(repository, revocations, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.saveAndFlush(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            t.setId(ids.incrementAndGet());
            return t;
        });
    }

    @Test
    void issuedTokensAreRandomAndOnlyTheirHashIsStored() {
        Rotation a = service.issue(7L, "ua");
        Rotation b = service.issue(7L, "ua");

        assertThat(a.refreshToken()).isNotEqualTo(b.refreshToken()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(a.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void rotationSpendsTheTokenAndContinuesTheFamily() {
        RefreshToken current = token("family-1", null, NOW.plusSeconds(60));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("old"))).thenReturn(Optional.of(current));

        Rotation next = service.rotate("old", "ua");

        assertThat(next.userId()).isEqualTo(7L);
        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(current.getReplacedById()).isEqualTo(101L);
        verify(repository, never()).revokeFamily(anyString(), any());
    }

    @Test
    void reusingASpentTokenRevokesTheWholeFamilyAndAllAccessTokens() {
        RefreshToken spent = token("family-1", NOW.minusSeconds(30), NOW.plusSeconds(60));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("stolen"))).thenReturn(Optional.of(spent));

        assertThatThrownBy(() -> service.rotate("stolen", "ua")).isInstanceOf(UnauthorizedException.class);

        verify(repository).revokeFamily("family-1", NOW);
        verify(revocations).revokeAllIssuedBefore(eq(7L), eq(NOW), eq(Duration.ofMinutes(15)));
    }

    @Test
    void expiredAndUnknownTokensAreRejected() {
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("expired")))
                .thenReturn(Optional.of(token("f", null, NOW.minusSeconds(1))));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("unknown"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("expired", "ua")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.rotate("unknown", "ua")).isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).revokeFamily(anyString(), any());
    }

    private static RefreshToken token(String family, Instant revokedAt, Instant expiresAt) {
        return RefreshToken.builder().id(1L).userId(7L).familyId(family).tokenHash("h").createdAt(NOW.minusSeconds(600))
                .expiresAt(expiresAt).revokedAt(revokedAt).build();
    }
}
