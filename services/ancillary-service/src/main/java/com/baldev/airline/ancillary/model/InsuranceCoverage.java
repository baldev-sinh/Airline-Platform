package com.baldev.airline.ancillary.model;

import java.math.BigDecimal;

import com.baldev.airline.common.enums.CoverageType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Ancillary ancillary;

    @Column(nullable = false)
    private CoverageType coverageType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private BigDecimal coverageAmount;

    private String emergencyContact;

    private boolean isFlat = true;
    private String claimCondition;
    private Integer displayOrder;
    private boolean active = true;


}
