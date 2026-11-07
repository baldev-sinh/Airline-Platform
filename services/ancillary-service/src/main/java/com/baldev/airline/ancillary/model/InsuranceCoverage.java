package com.baldev.airline.ancillary.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private CoverageType coverageType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private BigDecimal coverageAmount;

    private String emergencyContact;

    @Builder.Default
    private boolean isFlat = true;
    private String claimCondition;
    private Integer displayOrder;
    @Builder.Default
    private boolean active = true;


}
