package com.baldev.airline.booking.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.baldev.airline.common.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** An electronic ticket: issued per passenger only once the booking is paid. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_ticket_number", columnNames = "ticketNumber"))
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 13-digit e-ticket number: 3-digit airline accounting code + 10-digit serial. */
    @Column(nullable = false, length = 13)
    private String ticketNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    private Instant issuedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;
}
