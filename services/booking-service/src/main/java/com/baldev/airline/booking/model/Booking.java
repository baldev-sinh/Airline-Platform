package com.baldev.airline.booking.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.baldev.airline.common.embeddable.ContactInfo;
import com.baldev.airline.common.enums.BookingStatus;
import com.baldev.airline.common.enums.CabinClassType;
import com.baldev.airline.common.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A booking (PNR). Flight and price data are snapshotted at sale time, as in real reservation systems: the
 * booking stays readable if another service is down, and later schedule/fare edits never change what the
 * customer bought or paid.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(indexes = {
        @Index(name = "idx_booking_user", columnList = "userId"),
        @Index(name = "idx_booking_airline_status", columnList = "airlineId, status"),
        @Index(name = "idx_booking_flight_instance", columnList = "flightInstanceId")
}, uniqueConstraints = @UniqueConstraint(name = "uk_booking_reference", columnNames = "bookingReference"))
@EntityListeners(AuditingEntityListener.class)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 6-character record locator shown to the customer. */
    @Column(nullable = false, length = 6)
    private String bookingReference;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long flightId;

    @Column(nullable = false)
    private Long flightInstanceId;

    @Column(nullable = false)
    private Long airlineId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CabinClassType cabinClass = CabinClassType.ECONOMY;

    @Column(nullable = false)
    private Long fareId;

    private boolean flexibleTicket;
    private LocalDateTime ticketTimeLimit;

    // ---- Segment snapshot ----
    @Column(length = 10)
    private String flightNumber;
    @Column(length = 100)
    private String airlineName;
    @Column(length = 3)
    private String departureAirportCode;
    @Column(length = 3)
    private String arrivalAirportCode;
    @Column(length = 100)
    private String departureCity;
    @Column(length = 100)
    private String arrivalCity;
    private LocalDateTime departureDateTime;
    private LocalDateTime arrivalDateTime;
    @Column(length = 100)
    private String fareName;

    // ---- Price breakdown (per booking, all passengers) ----
    @Column(length = 3)
    private String currency;
    @Column(precision = 12, scale = 2)
    private BigDecimal fareAmount;
    @Column(precision = 12, scale = 2)
    private BigDecimal seatAmount;
    @Column(precision = 12, scale = 2)
    private BigDecimal ancillaryAmount;
    @Column(precision = 12, scale = 2)
    private BigDecimal mealAmount;
    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Passenger> passengers = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "booking_seat_instance_ids", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "seat_instance_id", nullable = false)
    @Builder.Default
    private List<Long> seatInstanceIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "booking_ancillary_ids", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "ancillary_id", nullable = false)
    @Builder.Default
    private List<Long> ancillaryIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "booking_meal_ids", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "meal_id", nullable = false)
    @Builder.Default
    private List<Long> mealIds = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Ticket> tickets = new HashSet<>();

    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(length = 255)
    private String cancellationReason;

    private Instant cancelledAt;

    private boolean ticketIssued;

    // ---- Refund (cancelled ticketed bookings, or payments that arrived after cancellation) ----
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RefundStatus refundStatus = RefundStatus.NONE;
    @Column(precision = 12, scale = 2)
    private BigDecimal refundAmount;
    /** Provider refund id once processed (Razorpay rfnd_...). */
    @Column(length = 64)
    private String refundReference;
    private Instant refundedAt;

    @Embedded
    private ContactInfo contactInfo;

    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant bookingDate;

    @UpdateTimestamp
    private Instant lastModified;

    public void addPassenger(Passenger passenger) {
        passengers.add(passenger);
        passenger.setBooking(this);
    }

    public void addTicket(Ticket ticket) {
        tickets.add(ticket);
        ticket.setBooking(this);
    }
}
