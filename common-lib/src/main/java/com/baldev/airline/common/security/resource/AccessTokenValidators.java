package com.baldev.airline.common.security.resource;

import com.baldev.airline.common.security.TokenRevocations;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * The claim checks every access token must pass after its signature has been verified. Any failure rejects the
 * token (401). Spring's {@link DelegatingOAuth2TokenValidator} runs all of its validators and collects every
 * error, so the revocation check, which needs a well-formed subject and costs a Redis round trip, runs only
 * after all the others have passed.
 */
public final class AccessTokenValidators {

    /** Tolerated difference between the issuer's clock and ours. */
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private AccessTokenValidators() {
    }

    /**
     * @return {@code exp} required and not passed (30 s skew), {@code nbf} if present, the expected issuer and
     *         audience, a numeric subject (our user id), and not revoked
     */
    public static OAuth2TokenValidator<Jwt> of(String issuer, String audience, TokenRevocations revocations, Clock clock) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setAllowEmptyExpiryClaim(false);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> claims = new DelegatingOAuth2TokenValidator<>(List.of(
                timestamps,
                new JwtIssuerValidator(issuer),
                new JwtAudienceValidator(audience),
                AccessTokenValidators::numericSubject));
        OAuth2TokenValidator<Jwt> revocation = notRevoked(revocations);
        return jwt -> {
            OAuth2TokenValidatorResult result = claims.validate(jwt);
            return result.hasErrors() ? result : revocation.validate(jwt);
        };
    }

    /** The subject is our user id; anything else cannot be mapped to a caller. */
    static OAuth2TokenValidatorResult numericSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        try {
            if (subject != null && Long.parseLong(subject) > 0) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
                "The subject must be a user id", null));
    }

    /**
     * Checked last, so only otherwise valid tokens cost a Redis round trip. Revocation survives the token's
     * stateless nature: logout revokes one {@code jti}, "sign out everywhere" every token issued before a point.
     */
    static OAuth2TokenValidator<Jwt> notRevoked(TokenRevocations revocations) {
        return jwt -> revocations.isRevoked(jwt.getId(), Long.valueOf(jwt.getSubject()), jwt.getIssuedAt())
                ? OAuth2TokenValidatorResult.failure(new OAuth2Error(AuthFailures.TOKEN_REVOKED,
                        "Access token has been revoked", null))
                : OAuth2TokenValidatorResult.success();
    }
}
