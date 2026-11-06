package com.baldev.airline.seat.repository;


import com.baldev.airline.seat.model.FlightInstanceCabin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightInstanceCabinRepository extends JpaRepository<FlightInstanceCabin
        , Long> {

    Page<FlightInstanceCabin> findByFlightInstanceId(Long flightInstanceId, Pageable pageable);

    FlightInstanceCabin findByFlightInstanceIdAndCabinClassId(Long flightInstanceId, Long cabinClassId);

    /**
     * Adds {@code delta} to the booked-seat counter in a single UPDATE, never going below zero. The database
     * applies concurrent calls one after another, so two bookings confirmed at once on different pods both count.
     * A read-modify-write in Java would let one of them overwrite the other.
     */
    @Modifying
    @Query("""
                update FlightInstanceCabin c
                set c.bookedSeats = greatest(coalesce(c.bookedSeats, 0) + :delta, 0)
                where c.id = :id
            """)
    int adjustBookedSeats(@Param("id") Long id, @Param("delta") int delta);
}
