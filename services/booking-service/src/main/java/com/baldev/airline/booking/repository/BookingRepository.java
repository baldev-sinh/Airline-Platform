package com.baldev.airline.booking.repository;

import com.baldev.airline.booking.model.Booking;
import com.baldev.airline.common.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"passengers", "tickets"})
    List<Booking> findByUserIdOrderByBookingDateDesc(Long userId);

    @EntityGraph(attributePaths = {"passengers", "tickets"})
    Optional<Booking> findWithDetailsById(Long id);

    /** Serializes state transitions (payment events vs. user cancellation) on one booking. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    /**
     * Airline back-office search. Uses EXISTS for the passenger match (not a fetch join) so the database does
     * the paging instead of Hibernate paging in memory.
     */
    @Query(value = """
            select b from Booking b
            where b.airlineId = :airlineId
              and (:pattern is null
                   or lower(b.bookingReference) like :pattern
                   or lower(b.contactInfo.email) like :pattern
                   or lower(b.contactInfo.phone) like :pattern
                   or exists (select 1 from Passenger p where p.booking = b
                              and (lower(p.firstName) like :pattern
                                   or lower(p.lastName) like :pattern
                                   or lower(p.email) like :pattern)))
              and (:status is null or b.status = :status)
              and (:flightInstanceId is null or b.flightInstanceId = :flightInstanceId)
            """)
    Page<Booking> searchByAirline(@Param("airlineId") Long airlineId,
                                  @Param("pattern") String pattern,
                                  @Param("status") BookingStatus status,
                                  @Param("flightInstanceId") Long flightInstanceId,
                                  Pageable pageable);

    boolean existsByBookingReference(String bookingReference);
}
