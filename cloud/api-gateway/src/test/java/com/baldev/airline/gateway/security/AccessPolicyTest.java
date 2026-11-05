package com.baldev.airline.gateway.security;

import com.baldev.airline.gateway.security.AccessPolicy.Access;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/** The access table is security-critical: every row here is a regression guard. */
class AccessPolicyTest {

    private final AccessPolicy policy = new AccessPolicy();

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            // never exposed
            "POST,   /internal/payments,                    DENY",
            "GET,    /internal/users/1,                     DENY",
            "GET,    /actuator/env,                         DENY",
            "POST,   /api/fares/batch-by-ids,               DENY",
            "GET,    /unknown/path,                         DENY",
            "DELETE, /auth/login,                           DENY",
            // public
            "POST,   /auth/login,                           PUBLIC",
            "POST,   /auth/signup,                          PUBLIC",
            "GET,    /api/flights/search,                   PUBLIC",
            "GET,    /api/airports/12,                      PUBLIC",
            "GET,    /api/airlines/dropdown,                PUBLIC",
            "POST,   /api/payments/webhooks/razorpay,       PUBLIC",
            // admin
            "POST,   /api/airlines/7/approve,               ADMIN",
            "POST,   /api/airlines/7/ban,                   ADMIN",
            "GET,    /api/airlines,                         ADMIN",
            "POST,   /api/cities,                           ADMIN",
            "DELETE, /api/airports/3,                       ADMIN",
            "GET,    /api/users,                            ADMIN",
            "GET,    /api/users/42,                         ADMIN",
            "GET,    /api/payments,                         ADMIN",
            "DELETE, /api/bookings/9,                       ADMIN",
            // airline back office
            "POST,   /api/flights,                          AIRLINE_OWNER",
            "PUT,    /api/fares/5,                          AIRLINE_OWNER",
            "DELETE, /api/seat-maps/5,                      AIRLINE_OWNER",
            "GET,    /api/bookings/airline,                 AIRLINE_OWNER",
            "POST,   /api/airlines,                         AIRLINE_OWNER",
            // signed-in customers
            "GET,    /api/users/profile,                    AUTHENTICATED",
            "POST,   /api/bookings,                         AUTHENTICATED",
            "GET,    /api/bookings/9,                       AUTHENTICATED",
            "PATCH,  /api/bookings/9/cancel,                AUTHENTICATED",
            "POST,   /api/payments/verify,                  AUTHENTICATED",
            "GET,    /api/fares/5,                          AUTHENTICATED",
            "POST,   /api/fares/search,                     AUTHENTICATED",
    })
    void resolvesAccessLevel(String method, String path, Access expected) {
        assertThat(policy.resolve(HttpMethod.valueOf(method), path)).isEqualTo(expected);
    }
}
