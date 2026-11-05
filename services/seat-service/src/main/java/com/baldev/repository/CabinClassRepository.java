package com.baldev.repository;


import com.baldev.enums.CabinClassType;
import com.baldev.model.CabinClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CabinClassRepository extends JpaRepository<CabinClass, Long> {

    List<CabinClass> findByAircraftId(Long aircraftId);

    CabinClass findByAircraftIdAndName(Long aircraftId, CabinClassType name);

    boolean existsByCodeAndAircraftId(String code, Long aircraftId);

    boolean existsByCodeAndAircraftIdAndIdNot(String code, Long aircraftId, Long id);
}
