package com.baldev.airline.flightops.repository;

import com.baldev.airline.flightops.model.FlightInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FlightInstanceRepository extends JpaRepository<FlightInstance, Long>,
        JpaSpecificationExecutor<FlightInstance> {

    @Query("""
                select fi from FlightInstance fi
                where fi.airlineId = :airlineId
                and (:departureAirportId is null or fi.departureAirportId = :departureAirportId)
                and (:arrivalAirportId is null or fi.arrivalAirportId = :arrivalAirportId)
                and (:flightId is null or fi.flight.id = :flightId)
                and (:dayStart is null or fi.departureDateTime >= :dayStart)
                and (:dayEnd is null or fi.departureDateTime < :dayEnd)
            """)
    Page<FlightInstance> findByAirlineId(@Param("airlineId") Long airlineId,
                                         @Param("departureAirportId") Long departureAirportId,
                                         @Param("arrivalAirportId") Long arrivalAirportId,
                                         @Param("flightId") Long flightId,
                                         @Param("dayStart") LocalDateTime dayStart,
                                         @Param("dayEnd") LocalDateTime dayEnd,
                                         Pageable pageable);

    Optional<FlightInstance> findByIdAndAirlineId(Long id, Long airlineId);

    /**
     * Atomically reserves inventory; returns 0 (no update) if fewer than {@code seats} remain, so concurrent
     * bookings can never drive availability negative.
     */
    @Modifying
    @Query("""
                update FlightInstance fi set fi.availableSeats = fi.availableSeats - :seats
                where fi.id = :id and fi.availableSeats >= :seats
            """)
    int decrementAvailableSeats(@Param("id") Long id, @Param("seats") int seats);

    @Modifying
    @Query("""
                update FlightInstance fi set fi.availableSeats = fi.availableSeats + :seats
                where fi.id = :id and fi.availableSeats + :seats <= fi.totalSeats
            """)
    int incrementAvailableSeats(@Param("id") Long id, @Param("seats") int seats);
}
