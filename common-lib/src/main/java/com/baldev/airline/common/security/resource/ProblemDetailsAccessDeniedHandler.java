package com.baldev.airline.common.security.resource;

import com.baldev.airline.common.security.resource.AuthErrorProfile.AuthFailure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;

/**
 * Spring Security calls an {@link AccessDeniedHandler} when an authenticated caller lacks the required role.
 * Answers 403 with our problem-details body, or 404 for requests the gateway hides ({@code notFound}), so a
 * signed-in caller cannot tell a hidden endpoint from a missing one either.
 */
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAccessDeniedHandler.class);

    private final AuthErrorProfile profile;
    private final RequestMatcher notFound;

    public ProblemDetailsAccessDeniedHandler(AuthErrorProfile profile, RequestMatcher notFound) {
        this.profile = profile;
        this.notFound = notFound;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        if (notFound.matches(request)) {
            profile.write(request, response, AuthFailure.NOT_FOUND);
            return;
        }
        Authentication caller = SecurityContextHolder.getContext().getAuthentication();
        if (caller != null) {
            log.info("Forbidden: userId={} roles={} {} {}", caller.getName(),
                    caller.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
                    request.getMethod(), request.getRequestURI());
        }
        profile.write(request, response, AuthFailure.FORBIDDEN);
    }
}
