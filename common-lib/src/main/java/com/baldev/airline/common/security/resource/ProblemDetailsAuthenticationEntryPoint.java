package com.baldev.airline.common.security.resource;

import com.baldev.airline.common.security.resource.AuthErrorProfile.AuthFailure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;

/**
 * Spring Security calls an {@link AuthenticationEntryPoint} when a request is not (or not validly) authenticated.
 * This one answers with our problem-details body instead of Spring's bare {@code WWW-Authenticate} header:
 * 401 for a missing, invalid or revoked token, 503 when the issuer's keys are unavailable, and 404 for requests
 * the gateway hides altogether ({@code notFound} matches them).
 */
public class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthErrorProfile profile;
    private final RequestMatcher notFound;

    public ProblemDetailsAuthenticationEntryPoint(AuthErrorProfile profile) {
        this(profile, request -> false);
    }

    public ProblemDetailsAuthenticationEntryPoint(AuthErrorProfile profile, RequestMatcher notFound) {
        this.profile = profile;
        this.notFound = notFound;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        AuthFailure failure = notFound.matches(request) ? AuthFailure.NOT_FOUND : AuthFailures.classify(exception);
        profile.write(request, response, failure);
    }
}
