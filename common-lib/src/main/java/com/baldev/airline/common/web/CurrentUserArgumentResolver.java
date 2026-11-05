package com.baldev.airline.common.web;

import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.common.exception.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves {@link CurrentUser} controller parameters from the gateway identity headers. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest request, WebDataBinderFactory binderFactory) {
        String userId = request.getHeader(RequestHeaders.USER_ID);
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
        try {
            return new CurrentUser(
                    Long.valueOf(userId.trim()),
                    request.getHeader(RequestHeaders.USER_EMAIL),
                    parseRoles(request.getHeader(RequestHeaders.USER_ROLES)));
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Invalid identity");
        }
    }

    static Set<UserRole> parseRoles(String header) {
        if (header == null || header.isBlank()) {
            return EnumSet.noneOf(UserRole.class);
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .flatMap(role -> Arrays.stream(UserRole.values()).filter(r -> r.name().equals(role)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(UserRole.class)));
    }
}
