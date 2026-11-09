package com.baldev.airline.gateway.config;

import com.baldev.airline.gateway.security.AuthenticationFilter;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Path-to-service routing. Authorization is NOT expressed here: every route runs the same
 * {@link AuthenticationFilter}, which consults the central AccessPolicy. Only /auth and /api paths are routed;
 * /internal and /actuator on backend services are therefore unreachable from outside.
 */
@Configuration
public class RouteConfig {

    private final AuthenticationFilter authenticationFilter;

    public RouteConfig(AuthenticationFilter authenticationFilter) {
        this.authenticationFilter = authenticationFilter;
    }

    @Bean
    public RouterFunction<ServerResponse> userServiceRoutes() {
        return route("user-service", "/auth/**", "/api/users/**");
    }

    @Bean
    public RouterFunction<ServerResponse> airlineCoreRoutes() {
        return route("airline-core-service", "/api/airlines", "/api/airlines/**", "/api/aircrafts", "/api/aircrafts/**");
    }

    @Bean
    public RouterFunction<ServerResponse> locationRoutes() {
        return route("location-service", "/api/cities", "/api/cities/**", "/api/airports", "/api/airports/**");
    }

    @Bean
    public RouterFunction<ServerResponse> flightOpsRoutes() {
        return route("flight-ops-service", "/api/flights", "/api/flights/**",
                "/api/flight-instances", "/api/flight-instances/**",
                "/api/flight-schedules", "/api/flight-schedules/**");
    }

    @Bean
    public RouterFunction<ServerResponse> pricingRoutes() {
        return route("pricing-service", "/api/fares", "/api/fares/**", "/api/fare-rules", "/api/fare-rules/**",
                "/api/baggage-policies", "/api/baggage-policies/**");
    }

    @Bean
    public RouterFunction<ServerResponse> seatRoutes() {
        return route("seat-service", "/api/cabin-classes", "/api/cabin-classes/**", "/api/seat-maps", "/api/seat-maps/**",
                "/api/seats", "/api/seats/**", "/api/flight-instance-cabins", "/api/flight-instance-cabins/**");
    }

    @Bean
    public RouterFunction<ServerResponse> ancillaryRoutes() {
        return route("ancillary-service", "/api/meals", "/api/meals/**", "/api/ancillaries", "/api/ancillaries/**",
                "/api/insurance-coverages", "/api/insurance-coverages/**", "/api/flight-meals", "/api/flight-meals/**",
                "/api/flight-cabin-ancillaries", "/api/flight-cabin-ancillaries/**");
    }

    @Bean
    public RouterFunction<ServerResponse> bookingRoutes() {
        return route("booking-service", "/api/bookings", "/api/bookings/**");
    }

    @Bean
    public RouterFunction<ServerResponse> paymentRoutes() {
        return route("payment-service", "/api/payments", "/api/payments/**");
    }

    @Bean
    public RouterFunction<ServerResponse> notificationRoutes() {
        return route("notification-service", "/api/notifications", "/api/notifications/**");
    }

    private RouterFunction<ServerResponse> route(String serviceId, String... paths) {
        var builder = GatewayRouterFunctions.route(serviceId + "-routes");
        for (String path : paths) {
            builder = builder.route(RequestPredicates.path(path), HandlerFunctions.http());
        }
        return builder
                .before(authenticationFilter)
                .filter(LoadBalancerFilterFunctions.lb(serviceId))
                .build();
    }
}
