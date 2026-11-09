package com.baldev.airline.booking.client.dto;

import java.math.BigDecimal;

import com.baldev.airline.common.enums.CoverageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Client-side copy of ancillary-service's API model; only this service's view of the contract. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceCoverageResponse {
    private Long id;
    private Long ancillaryId;
    private String ancillaryName;
    private CoverageType coverageType;
    private String name;
    private String description;
    private BigDecimal coverageAmount;
    private Boolean isFlat;
    private String claimCondition;
    private String emergencyContact;
    private Integer displayOrder;
    private Boolean active;
}
