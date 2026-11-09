package com.baldev.airline.gateway.security;

import com.baldev.airline.common.web.RequestHeaders;
import com.baldev.airline.gateway.security.AccessPolicy.Access;
import com.baldev.airline.common.security.AccessTokenVerifier;
import com.baldev.airline.common.security.AccessTokenVerifier.Reason;
import com.baldev.airline.common.security.AccessTokenVerifier.Rejected;
import com.baldev.airline.common.security.AccessTokenVerifier.VerifiedToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.Optional;
import java.util.function.Function;

/**
 * Applied to every route before proxying:
 * <ol>
 *   <li>Always removes client-supplied identity headers, so they can never be spoofed.</li>
 *   <li>Resolves the endpoint's access level from {@link AccessPolicy}.</li>
 *   <li>Validates the bearer token when required (RS256 against user-service's JWKS, issuer, audience, expiry,
 *       revocation) and enforces the role.</li>
 *   <li>Forwards the verified identity to the service as {@link RequestHeaders} values.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements Function<ServerRequest, ServerRequest> {

    private static final String BEARER = "Bearer ";
    private static final String ROLE_ADMIN = "ROLE_SYSTEM_ADMIN";
    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";

    private final AccessPolicy accessPolicy;
    private final AccessTokenVerifier verifier;

    @Override
    public ServerRequest apply(ServerRequest request) {
        Access access = accessPolicy.resolve(request.method(), request.path());

        if (access == Access.DENY) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (access == Access.PUBLIC) {
            return ServerRequest.from(request).headers(AuthenticationFilter::stripIdentity).build();
        }

        VerifiedToken token;
        try {
            token = verifier.verify(bearerToken(request).orElse(null));
        } catch (Rejected e) {
            if (e.reason() == Reason.KEYS_UNAVAILABLE) {
                // The issuer is down, not the client's token: ask for a retry instead of logging the user out.
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Authentication is temporarily unavailable");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    e.reason() == Reason.REVOKED ? "Access token has been revoked" : "Missing or invalid access token");
        }

        boolean isAdmin = token.roles().contains(ROLE_ADMIN);
        boolean allowed = switch (access) {
            case ADMIN -> isAdmin;
            case AIRLINE_OWNER -> isAdmin || token.roles().contains(ROLE_AIRLINE_OWNER);
            default -> true;
        };
        if (!allowed) {
            log.info("Forbidden: userId={} roles={} {} {}", token.userId(), token.roles(), request.method(), request.path());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }

        return ServerRequest.from(request)
                .headers(headers -> {
                    stripIdentity(headers);
                    headers.set(RequestHeaders.USER_ID, String.valueOf(token.userId()));
                    if (token.email() != null) {
                        headers.set(RequestHeaders.USER_EMAIL, token.email());
                    }
                    headers.set(RequestHeaders.USER_ROLES, String.join(",", token.roles()));
                })
                .build();
    }

    private static Optional<String> bearerToken(ServerRequest request) {
        String header = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return Optional.empty();
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private static void stripIdentity(HttpHeaders headers) {
        headers.remove(RequestHeaders.USER_ID);
        headers.remove(RequestHeaders.USER_EMAIL);
        headers.remove(RequestHeaders.USER_ROLES);
    }
}
