package com.baldev.airline.common.security;

import com.baldev.airline.common.security.AccessTokenVerifier.Reason;
import com.baldev.airline.common.security.AccessTokenVerifier.Rejected;
import com.baldev.airline.common.security.AccessTokenVerifier.VerifiedToken;
import com.baldev.airline.common.web.RequestHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Makes every service verify the caller itself instead of trusting the gateway's identity headers
 * ({@code airline.security.service-auth.mode=jwt}).
 *
 * <ul>
 *   <li>Bearer token present: verified (signature, issuer, audience, expiry, revocation). The identity headers the
 *       application sees are replaced with the token's identity, so a forged {@code X-User-Id} has no effect.</li>
 *   <li>No token: identity headers are removed, so the request is anonymous; endpoints that need a user answer 401.</li>
 *   <li>Invalid or revoked token: 401. Issuer keys unavailable: 503 (try again), never a silent anonymous request.</li>
 * </ul>
 *
 * Applies to {@code /api/**} only. {@code /internal/**} is service-to-service and protected by the network
 * (and mTLS in a service mesh).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "bearer ";
    private static final Set<String> IDENTITY_HEADERS = Set.of(
            RequestHeaders.USER_ID.toLowerCase(Locale.ROOT),
            RequestHeaders.USER_EMAIL.toLowerCase(Locale.ROOT),
            RequestHeaders.USER_ROLES.toLowerCase(Locale.ROOT));

    private final AccessTokenVerifier verifier;

    public JwtAuthenticationFilter(AccessTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            chain.doFilter(new IdentityRequest(request, Map.of()), response);
            return;
        }
        if (!header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            reject(response, 401, "INVALID_TOKEN", "Unsupported authorization scheme");
            return;
        }
        VerifiedToken token;
        try {
            token = verifier.verify(header.substring(BEARER.length()).trim());
        } catch (Rejected e) {
            if (e.reason() == Reason.KEYS_UNAVAILABLE) {
                reject(response, 503, "AUTH_UNAVAILABLE", "Authentication is temporarily unavailable, please retry");
            } else {
                reject(response, 401, e.reason() == Reason.REVOKED ? "TOKEN_REVOKED" : "INVALID_TOKEN", e.getMessage());
            }
            return;
        }
        Map<String, String> identity = new java.util.HashMap<>();
        identity.put(RequestHeaders.USER_ID.toLowerCase(Locale.ROOT), String.valueOf(token.userId()));
        if (token.email() != null) {
            identity.put(RequestHeaders.USER_EMAIL.toLowerCase(Locale.ROOT), token.email());
        }
        identity.put(RequestHeaders.USER_ROLES.toLowerCase(Locale.ROOT), String.join(",", token.roles()));
        chain.doFilter(new IdentityRequest(request, identity), response);
    }

    private static void reject(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (status == 401) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + (status == 401 ? "Unauthorized" : "Service Unavailable")
                + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }

    /** Request whose identity headers are exactly {@code identity} (lower-case names), whatever the client sent. */
    static final class IdentityRequest extends HttpServletRequestWrapper {

        private final Map<String, String> identity;

        IdentityRequest(HttpServletRequest request, Map<String, String> identity) {
            super(request);
            this.identity = identity;
        }

        @Override
        public String getHeader(String name) {
            String key = name.toLowerCase(Locale.ROOT);
            return IDENTITY_HEADERS.contains(key) ? identity.get(key) : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String key = name.toLowerCase(Locale.ROOT);
            if (!IDENTITY_HEADERS.contains(key)) {
                return super.getHeaders(name);
            }
            String value = identity.get(key);
            return value == null ? Collections.emptyEnumeration() : Collections.enumeration(Set.of(value));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            for (Enumeration<String> e = super.getHeaderNames(); e.hasMoreElements(); ) {
                String name = e.nextElement();
                if (!IDENTITY_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    names.add(name);
                }
            }
            names.addAll(identity.keySet());
            return Collections.enumeration(names);
        }
    }
}
