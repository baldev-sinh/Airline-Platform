package com.baldev.airline.common.web;

/**
 * Identity headers set by the API gateway after it validates the caller's JWT.
 *
 * <p>The gateway strips any client-supplied copies of these headers before forwarding, so services may trust
 * them <em>only</em> because services are not reachable except through the gateway (private network).
 */
public final class RequestHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLES = "X-User-Roles";

    private RequestHeaders() {
    }
}
