package com.baldev.airline.booking.service.support;

import com.baldev.airline.booking.repository.BookingRepository;
import com.baldev.airline.booking.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates customer-facing identifiers. Uses {@link SecureRandom}: record locators double as a lookup
 * credential (with the surname) at airports and in manage-booking flows, so they must not be guessable.
 */
@Component
@RequiredArgsConstructor
public class ReferenceGenerator {

    /** No 0/O or 1/I, which customers confuse when reading a locator aloud. */
    private static final char[] LOCATOR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LOCATOR_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;

    /** 3-digit IATA airline accounting code that prefixes e-ticket numbers. */
    @Value("${airline.booking.ticket-prefix:999}")
    private String ticketPrefix;

    public String newBookingReference() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            StringBuilder sb = new StringBuilder(LOCATOR_LENGTH);
            for (int i = 0; i < LOCATOR_LENGTH; i++) {
                sb.append(LOCATOR_ALPHABET[random.nextInt(LOCATOR_ALPHABET.length)]);
            }
            String candidate = sb.toString();
            if (!bookingRepository.existsByBookingReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique booking reference");
    }

    public String newTicketNumber() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = ticketPrefix + String.format("%010d", random.nextLong(10_000_000_000L));
            if (!ticketRepository.existsByTicketNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique ticket number");
    }
}
