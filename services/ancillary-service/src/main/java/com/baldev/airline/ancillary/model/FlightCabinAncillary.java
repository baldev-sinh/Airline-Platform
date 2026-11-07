package com.baldev.airline.ancillary.model;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class FlightCabinAncillary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long flightId;

    @Column(nullable = false)
    private Long cabinClassId;

    @ManyToOne
    private Ancillary ancillary;

    private Boolean available;

    private Integer maxQuantity;

    private BigDecimal price;

    @Builder.Default
    private Boolean includedInFare = false;
}
