package com.baldev.airline.common.security.resource;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Tells Spring Security's bearer-token filter where to find the access token, and where not to look at all.
 *
 * <ul>
 *   <li>Requests that {@code appliesTo} does not match have no token as far as security is concerned, whatever
 *       their {@code Authorization} header says. At the gateway that is the public endpoints (an expired token
 *       must not block a sign-in or a search); in services it is everything outside {@code /api/**}
 *       ({@code /internal} calls carry a relayed token that may have expired mid-request).</li>
 *   <li>A non-Bearer {@code Authorization} header, or an empty Bearer token, is rejected (401), not ignored.</li>
 *   <li>Tokens are only read from the header, never from query parameters or form bodies.</li>
 * </ul>
 */
public class AccessTokenBearerTokenResolver implements BearerTokenResolver {

    private static final String BEARER = "bearer ";

    private final RequestMatcher appliesTo;

    public AccessTokenBearerTokenResolver(RequestMatcher appliesTo) {
        this.appliesTo = appliesTo;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (!appliesTo.matches(request)) {
            return null;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            return null;
        }
        if (!header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            throw rejected(AuthFailures.UNSUPPORTED_SCHEME, "Unsupported authorization scheme");
        }
        String token = header.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            throw rejected(AuthFailures.MISSING_TOKEN, "Missing access token");
        }
        return token;
    }

    private static OAuth2AuthenticationException rejected(String code, String description) {
        return new OAuth2AuthenticationException(new BearerTokenError(code, HttpStatus.UNAUTHORIZED, description, null),
                description);
    }
}
