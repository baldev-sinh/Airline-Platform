package com.baldev.airline.seat.repository;


import com.baldev.airline.seat.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    boolean existsBySeatMapId(Long id);

    List<Seat> findBySeatMapId(Long id);
}
