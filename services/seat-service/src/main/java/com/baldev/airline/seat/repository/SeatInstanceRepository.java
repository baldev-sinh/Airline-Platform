package com.baldev.airline.seat.repository;


import com.baldev.airline.seat.model.SeatInstance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatInstanceRepository extends JpaRepository<SeatInstance, Long> {

}
