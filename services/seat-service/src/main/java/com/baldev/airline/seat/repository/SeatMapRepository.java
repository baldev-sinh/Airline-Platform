package com.baldev.airline.seat.repository;


import com.baldev.airline.seat.model.SeatMap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatMapRepository extends JpaRepository<SeatMap, Long> {

    SeatMap findByCabinClassId(Long cabinClassId);

    boolean existsByAirlineIdAndCabinClassIdAndName(Long airlineId, Long cabinClassId, String name);
}
