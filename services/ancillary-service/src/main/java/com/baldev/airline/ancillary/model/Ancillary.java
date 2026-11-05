package com.baldev.airline.ancillary.model;

import com.baldev.airline.common.domain.AncillaryMetadata;
import com.baldev.airline.common.enums.AncillaryType;
import com.baldev.airline.ancillary.service.AncillaryMetadataConverter;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class Ancillary {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AncillaryType type;

    private String subType;

    private String rfisc;

    @Column(nullable = false)
    private String name;

    private String description;

    @Convert(converter = AncillaryMetadataConverter.class)
    private AncillaryMetadata metadata;

    private Integer displayOrder;

    private Long airlineId;


}
