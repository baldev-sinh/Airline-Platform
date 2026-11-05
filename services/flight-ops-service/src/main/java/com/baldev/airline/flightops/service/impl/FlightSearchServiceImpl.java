package com.baldev.airline.flightops.service.impl;

import com.baldev.airline.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.BigDecimal;

import com.baldev.airline.flightops.client.AirlineClient;
import com.baldev.airline.flightops.client.LocationClient;
import com.baldev.airline.flightops.client.PricingClient;
import com.baldev.airline.flightops.client.SeatClient;
import com.baldev.airline.flightops.mapper.FlightInstanceMapper;
import com.baldev.airline.flightops.model.FlightInstance;
import com.baldev.airline.common.payload.request.FlightSearchRequest;
import com.baldev.airline.common.payload.response.*;
import com.baldev.airline.flightops.repository.FlightInstanceRepository;
import com.baldev.airline.flightops.service.FlightSearchService;
import com.baldev.airline.flightops.service.specification.FlightInstanceSpecification;
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
            Map<Long, Long> cabinClassIdByAircraft = new HashMap<>();
            Map<Long, FareResponse>mergedFareMap=new HashMap<>();
            
            List<FlightInstance> filtered=new ArrayList<>();
            
            for(FlightInstance fi:instances){

                Long cabinClassId = cabinClassIdByAircraft.computeIfAbsent(fi.getFlight().getAircraftId(), aircraftId -> {
                    CabinClassResponse cabin = seatClient.getCabinClassByAircraftIdAndName(request.getCabinClass(), aircraftId);
                    return cabin != null ? cabin.getId() : null;
                });

                if (cabinClassId==null) continue;

                FareResponse fare;
                try {
                    fare = pricingClient.getLowestFareForFlightAndCabinClass(fi.getFlight().getId(), cabinClassId);
                } catch (ResourceNotFoundException noFare) {
                    continue; // flight not yet priced in this cabin: not sellable, so not shown
                }

                if(fare==null) continue;

                BigDecimal price = fare.getTotalPrice();
                if (request.getMinPrice() != null && (price == null || price.compareTo(request.getMinPrice()) < 0)) continue;
                if (request.getMaxPrice() != null && (price == null || price.compareTo(request.getMaxPrice()) > 0)) continue;

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
            case "arrival" -> Sort.by(direction, "arrivalDateTime");
            case "duration" -> JpaSort.unsafe(direction,
                    "TIMESTAMPDIFF(MINUTE, departureDateTime, arrivalDateTime)");
            default -> Sort.by(direction, "departureDateTime");
        };
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),sort);
    }


}
