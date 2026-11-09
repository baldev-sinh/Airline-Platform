package com.baldev.airline.common.security.resource;

import com.baldev.airline.common.security.resource.AuthErrorProfile.AuthFailure;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/** Turns Spring Security's authentication exceptions into the outcome our clients are told about. */
final class AuthFailures {

    /** OAuth2 error code used by {@link AccessTokenValidators#notRevoked} for a revoked token. */
    static final String TOKEN_REVOKED = "token_revoked";
    /** OAuth2 error codes used by {@link AccessTokenBearerTokenResolver}. */
    static final String MISSING_TOKEN = "missing_token";
    static final String UNSUPPORTED_SCHEME = "unsupported_scheme";

    private AuthFailures() {
    }

    static AuthFailure classify(AuthenticationException exception) {
        // The decoder could not reach the issuer's keys (nothing cached): the server's problem, not the token's.
        if (exception instanceof AuthenticationServiceException) {
            return AuthFailure.KEYS_UNAVAILABLE;
        }
        if (exception instanceof OAuth2AuthenticationException oauth) {
            String code = oauth.getError() != null ? oauth.getError().getErrorCode() : null;
            if (MISSING_TOKEN.equals(code)) {
                return AuthFailure.MISSING;
            }
            if (UNSUPPORTED_SCHEME.equals(code)) {
                return AuthFailure.UNSUPPORTED_SCHEME;
            }
        }
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwtValidationException validation) {
                for (OAuth2Error error : validation.getErrors()) {
                    if (TOKEN_REVOKED.equals(error.getErrorCode())) {
                        return AuthFailure.REVOKED;
                    }
                }
            }
        }
        return AuthFailure.INVALID;
    }
}
