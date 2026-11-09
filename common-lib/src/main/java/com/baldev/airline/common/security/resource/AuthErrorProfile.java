package com.baldev.airline.common.security.resource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The exact RFC 9457 problem-details bodies that authentication and authorization failures produce. The gateway
 * and the services have always used different {@code code} values and slightly different shapes; both are kept
 * byte for byte, because clients (the web app, other services) rely on them.
 *
 * <table>
 *   <caption>Bodies per profile</caption>
 *   <tr><th>Outcome</th><th>{@link #GATEWAY}</th><th>{@link #SERVICE}</th></tr>
 *   <tr><td>missing or invalid token</td><td>401 UNAUTHORIZED</td><td>401 INVALID_TOKEN + WWW-Authenticate</td></tr>
 *   <tr><td>revoked token</td><td>401 UNAUTHORIZED</td><td>401 TOKEN_REVOKED + WWW-Authenticate</td></tr>
 *   <tr><td>signing keys unavailable</td><td>503 SERVICE_UNAVAILABLE</td><td>503 AUTH_UNAVAILABLE</td></tr>
 *   <tr><td>wrong role</td><td>403 FORBIDDEN</td><td>(services do not authorize by role)</td></tr>
 *   <tr><td>denied path</td><td>404 NOT_FOUND</td><td>-</td></tr>
 * </table>
 */
public enum AuthErrorProfile {

    /**
     * The API gateway: Spring's {@code ProblemDetail} shape with {@code instance} and no {@code type}, as rendered
     * by GlobalExceptionHandler, and generic codes derived from the status.
     */
    GATEWAY {
        @Override
        void write(HttpServletRequest request, HttpServletResponse response, AuthFailure failure) throws IOException {
            switch (failure) {
                case REVOKED -> gateway(request, response, HttpStatus.UNAUTHORIZED, "Access token has been revoked");
                case KEYS_UNAVAILABLE -> gateway(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                        "Authentication is temporarily unavailable");
                case FORBIDDEN -> gateway(request, response, HttpStatus.FORBIDDEN, "Insufficient permissions");
                case NOT_FOUND -> gateway(request, response, HttpStatus.NOT_FOUND, "Not found");
                default -> gateway(request, response, HttpStatus.UNAUTHORIZED, "Missing or invalid access token");
            }
        }
    },

    /** Business services: {@code type about:blank}, no {@code instance}, token-specific codes, a Bearer challenge. */
    SERVICE {
        @Override
        void write(HttpServletRequest request, HttpServletResponse response, AuthFailure failure) throws IOException {
            switch (failure) {
                case KEYS_UNAVAILABLE -> service(response, HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE",
                        "Authentication is temporarily unavailable, please retry");
                case REVOKED -> service(response, HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED", "Access token has been revoked");
                case UNSUPPORTED_SCHEME -> service(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Unsupported authorization scheme");
                case MISSING -> service(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Missing access token");
                case FORBIDDEN -> service(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permissions");
                case NOT_FOUND -> service(response, HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
                default -> service(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid access token");
            }
        }
    };

    /** Why a request was refused; decided by {@link AuthFailures#classify}. */
    public enum AuthFailure { MISSING, UNSUPPORTED_SCHEME, INVALID, REVOKED, KEYS_UNAVAILABLE, FORBIDDEN, NOT_FOUND }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PROBLEM_JSON = "application/problem+json";

    abstract void write(HttpServletRequest request, HttpServletResponse response, AuthFailure failure) throws IOException;

    private static void gateway(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("status", status.value());
        body.put("title", status.getReasonPhrase());
        body.put("code", status.name());
        send(response, status, body);
    }

    private static void service(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("code", code);
        send(response, status, body);
    }

    private static void send(HttpServletResponse response, HttpStatus status, Map<String, Object> body) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(PROBLEM_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSON.writeValueAsString(body));
        response.getWriter().flush();
    }
}
