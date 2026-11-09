package com.baldev.airline.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns domain events (user.registered, booking.confirmed, booking.cancelled, booking.refunded) into customer
 * notifications: an e-mail and an entry in the customer's in-app inbox. No other service calls it; it only
 * listens, so a notification outage can never fail a booking or a payment.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
