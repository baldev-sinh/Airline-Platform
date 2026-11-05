package com.baldev.airline.ancillary.repository;

import com.baldev.airline.ancillary.model.InsuranceCoverage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsuranceCoverageRepository extends JpaRepository<InsuranceCoverage, Long> {

    List<InsuranceCoverage> findByAncillaryId(Long ancillaryId);

    List<InsuranceCoverage> findByAncillaryIdAndActiveTrue(Long ancillaryId);
}
