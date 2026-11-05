package com.baldev.service.impl;


import com.baldev.client.AirlineClient;
import com.baldev.client.LocationClient;
import com.baldev.client.PricingClient;
import com.baldev.client.SeatClient;
import com.baldev.mapper.FlightInstanceMapper;
import com.baldev.model.FlightInstance;
import com.baldev.payload.request.FlightSearchRequest;
import com.baldev.payload.response.*;
import com.baldev.repository.FlightInstanceRepository;
import com.baldev.service.FlightSearchService;
import com.baldev.service.specification.FlightInstanceSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class FlightSearchServiceImpl implements FlightSearchService {

    private final FlightInstanceRepository flightInstanceRepository;
    private final PricingClient pricingClient;
    private final SeatClient seatClient;
    private final AirlineClient airlineClient;
    private final LocationClient locationClient;

    @Override
    public Page<FlightInstanceResponse> searchFlights(
            FlightSearchRequest request,
            Pageable pageable
    ) {
        Pageable sortedPageable=applySort(
                pageable,
                request.getSortBy(),
                request.getSortOrder()
        );

        Specification<FlightInstance> specification=
                FlightInstanceSpecification.buildSearchSpec(request);

        Page<FlightInstance> dbPage=flightInstanceRepository.findAll(
                specification, sortedPageable);

        if(dbPage.isEmpty()){
            return Page.empty(sortedPageable);
        }

        List<FlightInstance> instances=new ArrayList<>(dbPage.getContent());
        
        Map<Long, FareResponse> fareMap=Collections.emptyMap();
        
        if(request.getCabinClass()!=null){
            final boolean hasPriceFilter=request.getMinPrice()!=null
                    && request.getMaxPrice()!=null;
            Map<Long, FareResponse>mergedFareMap=new HashMap<>();
            
            List<FlightInstance> filtered=new ArrayList<>();
            
            for(FlightInstance fi:instances){

                CabinClassResponse cabinClassResponse = seatClient.getCabinClassByAircraftIdAndName(
                        request.getCabinClass(),
                        fi.getFlight().getAircraftId()
                );

                Long cabinClassId=cabinClassResponse.getId();

                if (cabinClassId==null) continue;

                FareResponse fare=pricingClient.getLowestFareForFlightAndCabinClass(
                        fi.getFlight().getId(),
                        cabinClassId
                );

                if(fare==null) continue;

                if(hasPriceFilter){
                    Double price=fare.getTotalPrice();
                    if(price==null) continue;
                    if(price< request.getMinPrice())continue;
                    if(price>request.getMaxPrice()) continue;
                }

                mergedFareMap.put(fi.getFlight().getId(),fare);
                filtered.add(fi);
            }
            fareMap=mergedFareMap;
            instances=filtered;

            if (instances.isEmpty()){
                return Page.empty(sortedPageable);
            }
        }

        List<FlightInstanceResponse> responses=enrichWithExternalData(instances,fareMap);


        return new PageImpl<>(responses,sortedPageable,dbPage.getTotalElements());
    }

    private List<FlightInstanceResponse> enrichWithExternalData(
            List<FlightInstance> instances,
            Map<Long, FareResponse> fareMap) {

        Map<Long, AirlineResponse> airlineCache=new HashMap<>();
        Map<Long, AirportResponse> airportCache= new HashMap<>();
        Map<Long, AircraftResponse> aircraftCache= new HashMap<>();

        List<FlightInstanceResponse> results=new ArrayList<>(instances.size());

        for(FlightInstance fi:instances){
            AircraftResponse aircraft=aircraftCache.computeIfAbsent(
                    fi.getFlight().getAircraftId(),airlineClient::getAircraftById
            );

            AirlineResponse airline = airlineCache.computeIfAbsent(
                    fi.getAirlineId(), airlineClient::getAirlineById);

            AirportResponse depAirport = airportCache.computeIfAbsent(
                    fi.getDepartureAirportId(), locationClient::getAirportById);

            AirportResponse arrAirport = airportCache.computeIfAbsent(
                    fi.getArrivalAirportId(), locationClient::getAirportById);

            FlightInstanceResponse response= FlightInstanceMapper.toResponse(
                    fi,aircraft,airline,depAirport,arrAirport
            );
            response.setFare(fareMap.get(fi.getFlight().getId()));
            results.add(response);
        }

        return results;
    }



    private Pageable applySort(Pageable pageable, String sortBy, String sortOrder) {
        Sort.Direction direction="desc".equalsIgnoreCase(sortOrder)?
                Sort.Direction.DESC : Sort.Direction.ASC;

        Sort sort=(sortBy==null || sortBy.isBlank())
                ? Sort.by(direction, "departureDateTime")
                : switch (sortBy.toLowerCase()){
            case "arrival" -> Sort.by(direction, "arrivalDateTimme");
            case "duration" -> JpaSort.unsafe(direction,
                    "TIMESTAMPDIFF(MINUTE, departureDateTime, arrivalDateTime)");
            default -> Sort.by(direction, "departureDateTime");
        };
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),sort);
    }


}
