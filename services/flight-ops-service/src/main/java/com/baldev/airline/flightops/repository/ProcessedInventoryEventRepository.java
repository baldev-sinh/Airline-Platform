package com.baldev.airline.flightops.repository;

import com.baldev.airline.flightops.model.ProcessedInventoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedInventoryEventRepository extends JpaRepository<ProcessedInventoryEvent, String> {
}
