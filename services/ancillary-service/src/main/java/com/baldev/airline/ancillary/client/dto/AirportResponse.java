package com.baldev.airline.ancillary.client.dto;

import com.baldev.airline.common.embeddable.Address;
import com.baldev.airline.common.embeddable.GeoCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZoneId;

/** Client-side copy of location-service's API model; only this service's view of the contract. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirportResponse {

    private Long id;
    private String iataCode;
    private String name;
    private String detailedName;
    private ZoneId timeZone;
    private Address address;
    private CityResponse city;
    private GeoCode geoCode;

}
