package com.baldev.airline.seat.repository;


import com.baldev.airline.common.enums.CabinClassType;
import com.baldev.airline.seat.model.CabinClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CabinClassRepository extends JpaRepository<CabinClass, Long> {

    List<CabinClass> findByAircraftId(Long aircraftId);

    CabinClass findByAircraftIdAndName(Long aircraftId, CabinClassType name);

    boolean existsByCodeAndAircraftId(String code, Long aircraftId);

    boolean existsByCodeAndAircraftIdAndIdNot(String code, Long aircraftId, Long id);
}
