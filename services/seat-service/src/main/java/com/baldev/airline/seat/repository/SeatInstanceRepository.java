package com.baldev.airline.seat.repository;

import com.baldev.airline.common.enums.SeatAvailabilityStatus;
import com.baldev.airline.seat.model.SeatInstance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SeatInstanceRepository extends JpaRepository<SeatInstance, Long> {

    /**
     * Row-locks the requested seats (SELECT ... FOR UPDATE) in id order. Locking in a deterministic order
     * prevents deadlocks between two checkouts that request overlapping seats.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeatInstance s where s.id in :ids order by s.id")
    List<SeatInstance> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeatInstance s where s.heldByBookingId = :bookingId order by s.id")
    List<SeatInstance> findByBookingForUpdate(@Param("bookingId") Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                select s from SeatInstance s
                where s.status = :status and s.holdExpiresAt < :now
                order by s.id
            """)
    List<SeatInstance> findExpiredHoldsForUpdate(@Param("status") SeatAvailabilityStatus status,
                                                 @Param("now") Instant now,
                                                 org.springframework.data.domain.Limit limit);

    boolean existsByFlightInstanceId(Long flightInstanceId);
}
