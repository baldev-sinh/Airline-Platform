package com.baldev.airline.common.web;

import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.common.exception.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves {@link CurrentUser} controller parameters from the access token that Spring Security verified for this
 * request (a {@code JwtAuthenticationToken}): {@code sub} is the user id, plus the {@code email} and {@code roles}
 * claims. Request headers are never consulted, so a caller cannot claim to be someone else. Without a verified
 * token the request is rejected with 401.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final boolean SECURITY_PRESENT = ClassUtils.isPresent(
            "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken",
            CurrentUserArgumentResolver.class.getClassLoader());

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest request, WebDataBinderFactory binderFactory) {
        return (SECURITY_PRESENT ? VerifiedToken.currentUser() : Optional.<CurrentUser>empty())
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    static Set<UserRole> parseRoles(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return EnumSet.noneOf(UserRole.class);
        }
        return names.stream()
                .map(String::trim)
                .flatMap(role -> Arrays.stream(UserRole.values()).filter(r -> r.name().equals(role)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(UserRole.class)));
    }

    /** Kept separate so the resolver loads in apps without Spring Security (it then never finds a caller). */
    private static final class VerifiedToken {

        static Optional<CurrentUser> currentUser() {
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token)) {
                return Optional.empty();
            }
            var jwt = token.getToken();
            try {
                return Optional.of(new CurrentUser(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("email"),
                        parseRoles(jwt.getClaimAsStringList("roles"))));
            } catch (NumberFormatException e) {
                // The decoder rejects non-numeric subjects; this only guards against a misconfigured decoder.
                return Optional.empty();
            }
        }
    }
}
