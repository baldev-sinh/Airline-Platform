package com.baldev.airline.booking.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Client-side copy of location-service's API model; only this service's view of the contract. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CityResponse {

    private Long id;
    private String name;
    private String cityCode;
    private String countryName;
    private String countryCode;
    private String regionCode;
    private String timeZoneOffset;
}
