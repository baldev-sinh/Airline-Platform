package com.baldev.airline.pricing.client.dto;

import com.baldev.airline.common.enums.AircraftStatus;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/** Client-side copy of airline-core-service's API model; only this service's view of the contract. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AircraftResponse {

    private Long id;
    private String code;
    private String model;
    private String manufacturer;
    private Integer seatingCapacity;
    private Integer economySeats;
    private Integer premiumEconomySeats;
    private Integer businessSeats;
    private Integer firstClassSeats;
    private Integer rangeKm;
    private Integer cruisingSpeedKmh;
    private Integer maxAltitudeFt;
    private Integer yearOfManufacture;
    private LocalDate registrationDate;
    private LocalDate nextMaintenanceDate;
    private AircraftStatus status;
    private Boolean isAvailable;

    private Long airlineId;
    private String airlineName;
    private String airlineIataCode;

    private Long currentAirportId;
    private Long currentAirportCity;
    private String currentAirportCode;
    private String currentAirportName;

    private Integer totalSeats;
    private Boolean requiresMaintenance;
    private Boolean isOperational;

    private Instant createdAt;
    private Instant updatedAt;
}
