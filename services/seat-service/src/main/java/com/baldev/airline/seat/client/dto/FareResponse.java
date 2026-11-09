package com.baldev.airline.seat.client.dto;

import java.math.BigDecimal;

import com.baldev.airline.common.enums.CabinClassType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Client-side copy of pricing-service's API model; only this service's view of the contract. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareResponse {

    private Long id;
    private String name;
    private Character rbdCode;
    private Long flightId;
    private Long cabinClassId;
    private CabinClassType cabinClass;

    // Pricing
    private BigDecimal baseFare;
    private BigDecimal taxesAndFees;
    private BigDecimal airlineFees;
    private BigDecimal currentPrice;
    private BigDecimal totalPrice;
    private String fareLabel;

    // Seat benefits
    private Boolean extraSeatSpace;
    private Boolean preferredSeatChoice;
    private Boolean advanceSeatSelection;
    private Boolean guaranteedSeatTogether;

    // Boarding benefits
    private Boolean priorityBoarding;
    private Boolean priorityCheckin;
    private Boolean fastTrackSecurity;

    // In-flight benefits
    private Boolean complimentaryMeals;
    private Boolean premiumMealChoice;
    private Boolean inFlightInternet;
    private Boolean inFlightEntertainment;
    private Boolean complimentaryBeverages;

    // Flexibility benefits
    private Boolean freeDateChange;
    private Boolean partialRefund;
    private Boolean fullRefund;

    // Premium service benefits
    private Boolean loungeAccess;
    private Boolean airportTransfer;

    // Relationships
    private Long fareRulesId;
    private FareRulesResponse fareRules;
    private BaggagePolicyResponse baggagePolicy;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;
}
