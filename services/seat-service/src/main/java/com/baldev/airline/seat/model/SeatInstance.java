package com.baldev.airline.seat.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.baldev.airline.common.enums.SeatAvailabilityStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/** One physical seat on one dated flight; the unit of seat inventory. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(indexes = {
        @Index(name = "idx_seat_instance_flight_instance", columnList = "flightInstanceId"),
        @Index(name = "idx_seat_instance_held_by_booking", columnList = "heldByBookingId"),
        @Index(name = "idx_seat_instance_status_expiry", columnList = "status, holdExpiresAt")
})
@EntityListeners(AuditingEntityListener.class)
public class SeatInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long flightId;

    @ManyToOne(fetch = FetchType.LAZY)
    private FlightInstanceCabin flightInstanceCabin;

    @Column(nullable = false)
    private Long flightInstanceId;

    @ManyToOne
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SeatAvailabilityStatus status = SeatAvailabilityStatus.AVAILABLE;

    /** Booking currently holding or owning this seat; null while AVAILABLE. */
    private Long heldByBookingId;

    /** When a HELD seat is automatically released if the booking has not been confirmed. */
    private Instant holdExpiresAt;

    @Column(precision = 12, scale = 2)
    private BigDecimal fare;

    @Column(precision = 12, scale = 2)
    private BigDecimal premiumSupercharge;

    private Long flightScheduleId;

    @Version
    private Long version;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public boolean isAvailable() {
        return status == SeatAvailabilityStatus.AVAILABLE;
    }

    public boolean isBooked() {
        return status == SeatAvailabilityStatus.BOOKED || status == SeatAvailabilityStatus.OCCUPIED;
    }

    public void hold(Long bookingId, Instant expiresAt) {
        this.status = SeatAvailabilityStatus.HELD;
        this.heldByBookingId = bookingId;
        this.holdExpiresAt = expiresAt;
    }

    public void book() {
        this.status = SeatAvailabilityStatus.BOOKED;
        this.holdExpiresAt = null;
    }

    public void release() {
        this.status = SeatAvailabilityStatus.AVAILABLE;
        this.heldByBookingId = null;
        this.holdExpiresAt = null;
    }
}
