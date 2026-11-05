package com.baldev.airline.pricing.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.baldev.airline.common.util.Money;
import java.math.BigDecimal;
import com.baldev.airline.common.embeddable.*;
import com.baldev.airline.common.enums.CabinClassType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Fare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Character rbdCode;

    @Column(nullable = false)
    private Long flightId;

    @Column(nullable = false)
    private Long cabinClassId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private CabinClassType cabinClass;

    @Column(nullable = false)
    private BigDecimal baseFare;

    private BigDecimal taxesAndFees;
    private BigDecimal airlineFees;

    @Column(nullable = false)
    private BigDecimal currentPrice;


    private String fareLabel;


    @OneToOne(mappedBy = "fare", cascade = CascadeType.ALL, orphanRemoval = true)
    private BaggagePolicy baggagePolicy;


    @OneToOne(mappedBy = "fare", cascade = CascadeType.ALL, orphanRemoval = true)
    private FareRules fareRules;

    @Embedded
    private SeatBenefits seatBenefits=new SeatBenefits();

    @Embedded
    private BoardingBenefits boardingBenefits = new BoardingBenefits();

    @Embedded
    @Builder.Default
    private InFlightBenefits inFlightBenefits = new InFlightBenefits();

    @Embedded
    @Builder.Default
    private FlexibilityBenefits flexibilityBenefits = new FlexibilityBenefits();


    @Embedded
    @Builder.Default
    private PremiumServiceBenefits premiumServiceBenefits = new PremiumServiceBenefits();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    /**
     * All-inclusive price per passenger. {@code currentPrice} is the live selling price (it can differ from the
     * components when revenue management adjusts it); when absent, the total is the sum of its components.
     */
    public BigDecimal getTotalPrice() {
        if (currentPrice != null) {
            return Money.normalize(currentPrice);
        }
        return Money.sum(baseFare, taxesAndFees, airlineFees);
    }


}
