package com.baldev.airline.booking.repository;

import com.baldev.airline.common.enums.BookingStatus;
import com.baldev.airline.booking.model.Booking;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    long countByFlightInstanceId(Long flightInstanceId);

    @Query("""
                        select distinct b from Booking b
                        left join fetch b.passengers p
                        where b.airlineId=:airlineId
                        and (:search is null  or LOWER(b.bookingReference) like lower(concat('%',:search, '%') )
                            or lower(p.firstName) like lower(concat('%',:search, '%') )
                            or lower(p.lastName) like lower(concat('%',:search, '%') )
                            or lower(p.email) like lower(concat('%',:search, '%') )
                            or lower(b.contactInfo.email) like lower(concat('%',:search, '%') )
                            or lower(b.contactInfo.phone) like lower(concat('%',:search, '%') )
            
                        )
                        and (:status is null or b.status = :status)
                        and(:flightInstanceId is null or b.flightInstanceId = :flightInstanceId)
            """)
    List<Booking> findByAirlineWithFilter(
            @Param("airlineId") Long airlineId,
            @Param("search") String search,
            @Param("status") BookingStatus status,
            @Param("flightInstanceId") Long flightInstanceId,
            Sort sort
    );

    boolean existsByBookingReference(String bookingReference);
}
