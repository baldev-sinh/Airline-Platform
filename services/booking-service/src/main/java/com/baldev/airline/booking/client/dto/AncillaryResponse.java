package com.baldev.airline.booking.client.dto;

import com.baldev.airline.common.domain.AncillaryMetadata;
import com.baldev.airline.common.enums.AncillaryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Client-side copy of ancillary-service's API model; only this service's view of the contract. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AncillaryResponse {

    private Long id;
    private AncillaryType type;
    private String subType;
    private String rfisc;
    private String name;
    private String description;
    private String categoryDisplayName;
    private String categoryIcon;
    private String iconUrl;
    private AncillaryMetadata metadata;

    private List<InsuranceCoverageResponse> coverages;
    private Integer displayOrder;
    private Long airlineId;
}
