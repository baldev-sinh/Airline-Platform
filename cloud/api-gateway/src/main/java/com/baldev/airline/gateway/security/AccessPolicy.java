package com.baldev.airline.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Coarse-grained, edge-level authorization. Rules are evaluated top to bottom; the first match wins, and
 * anything unmatched is denied. Fine-grained checks (does this user own this booking? does this airline own
 * this flight?) stay in the services, which never trust the client for ownership.
 *
 * <p>Keep this table the single place where public and privileged endpoints are declared.
 */
@Component
public class AccessPolicy {

    public enum Access {
        /** No token required. */
        PUBLIC,
        /** Any valid token. */
        AUTHENTICATED,
        /** ROLE_AIRLINE_OWNER (or system admin). */
        AIRLINE_OWNER,
        /** ROLE_SYSTEM_ADMIN only. */
        ADMIN,
        /** Never reachable from outside (responds 404). */
        DENY
    }

    private static final Set<HttpMethod> ANY = Set.of();
    private static final Set<HttpMethod> GET = Set.of(HttpMethod.GET);
    private static final Set<HttpMethod> POST = Set.of(HttpMethod.POST);
    private static final Set<HttpMethod> WRITE = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    /** Airline-managed catalogue: anyone signed in can read, only airline owners can change. */
    private static final List<String> AIRLINE_MANAGED = List.of(
            "/api/aircrafts/**", "/api/flights/**", "/api/flight-instances/**", "/api/flight-schedules/**",
            "/api/fares/**", "/api/fare-rules/**", "/api/baggage-policies/**",
            "/api/cabin-classes/**", "/api/seat-maps/**", "/api/flight-instance-cabins/**",
            "/api/meals/**", "/api/ancillaries/**", "/api/insurance-coverages/**",
            "/api/flight-meals/**", "/api/flight-cabin-ancillaries/**");

    private record Rule(Set<HttpMethod> methods, PathPattern pattern, Access access) {
        boolean matches(HttpMethod method, PathContainer path) {
            return (methods.isEmpty() || methods.contains(method)) && pattern.matches(path);
        }
    }

    private final List<Rule> rules = new ArrayList<>();
    private final PathPatternParser parser = PathPatternParser.defaultInstance;

    public AccessPolicy() {
        // ---- Never exposed ----
        rule(ANY, "/internal/**", Access.DENY);
        rule(ANY, "/actuator/**", Access.DENY);
        rule(POST, "/api/fares/batch-by-ids", Access.DENY);

        // ---- Public ----
        rule(POST, "/auth/login", Access.PUBLIC);
        rule(POST, "/auth/signup", Access.PUBLIC);
        rule(GET, "/api/flights/search", Access.PUBLIC);
        rule(GET, "/api/cities/**", Access.PUBLIC);
        rule(GET, "/api/airports/**", Access.PUBLIC);
        rule(GET, "/api/airlines/dropdown", Access.PUBLIC);
        // Authenticated by the provider's HMAC signature, not by a user token.
        rule(POST, "/api/payments/webhooks/razorpay", Access.PUBLIC);

        // ---- System administration ----
        rule(WRITE, "/api/cities/**", Access.ADMIN);
        rule(WRITE, "/api/airports/**", Access.ADMIN);
        rule(GET, "/api/airlines", Access.ADMIN);
        rule(POST, "/api/airlines/{id}/approve", Access.ADMIN);
        rule(POST, "/api/airlines/{id}/suspend", Access.ADMIN);
        rule(POST, "/api/airlines/{id}/ban", Access.ADMIN);
        rule(GET, "/api/users/profile", Access.AUTHENTICATED);
        rule(ANY, "/api/users/**", Access.ADMIN);
        rule(GET, "/api/payments", Access.ADMIN);
        rule(Set.of(HttpMethod.DELETE), "/api/bookings/{id}", Access.ADMIN);

        // ---- Airline back office ----
        rule(GET, "/api/bookings/airline", Access.AIRLINE_OWNER);
        rule(WRITE, "/api/airlines", Access.AIRLINE_OWNER);
        rule(Set.of(HttpMethod.DELETE), "/api/airlines/{id}", Access.AIRLINE_OWNER);
        // Fare search is a read even though it uses POST for its body.
        rule(POST, "/api/fares/search", Access.AUTHENTICATED);
        for (String pattern : AIRLINE_MANAGED) {
            rule(WRITE, pattern, Access.AIRLINE_OWNER);
        }

        // ---- Everything else under /api requires sign-in; anything not listed is denied ----
        rule(ANY, "/api/**", Access.AUTHENTICATED);
    }

    public Access resolve(HttpMethod method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        for (Rule rule : rules) {
            if (rule.matches(method, container)) {
                return rule.access();
            }
        }
        return Access.DENY;
    }

    private void rule(Set<HttpMethod> methods, String pattern, Access access) {
        rules.add(new Rule(methods, parser.parse(pattern), access));
    }
}
