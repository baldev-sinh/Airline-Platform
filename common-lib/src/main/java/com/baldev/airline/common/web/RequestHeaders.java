package com.baldev.airline.common.web;

/**
 * Identity header names that clients might try to send. Nothing trusts them: the API gateway removes them from
 * every proxied request, and services take the caller's identity only from the verified access token
 * ({@link CurrentUser}). Kept so the gateway knows what to strip.
 */
public final class RequestHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLES = "X-User-Roles";

    private RequestHeaders() {
    }
}
