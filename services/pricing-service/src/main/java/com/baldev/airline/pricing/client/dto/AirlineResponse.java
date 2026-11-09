package com.baldev.airline.pricing.client.dto;

import com.baldev.airline.common.embeddable.Support;
import com.baldev.airline.common.enums.AirlineStatus;

import lombok.*;

import java.time.Instant;

/** Client-side copy of airline-core-service's API model; only this service's view of the contract. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirlineResponse {

    private Long id;

    private String iataCode;
    private String icaoCode;

    private String name;
    private String alias;

    private String logoUrl;
    private String website;

    private AirlineStatus status;
    private String alliance;

    private Instant createdAt;
    private Instant updatedAt;

    private Long ownerId;
    private UserDTO owner;
    private Long updatedById;

    private CityResponse headquartersCity;
    private Support support;
}
