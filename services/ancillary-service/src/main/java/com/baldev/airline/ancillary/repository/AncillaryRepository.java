package com.baldev.airline.ancillary.repository;

import com.baldev.airline.ancillary.model.Ancillary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AncillaryRepository extends JpaRepository<Ancillary, Long> {

    List<Ancillary> findByAirlineId(Long airlineId);
}
