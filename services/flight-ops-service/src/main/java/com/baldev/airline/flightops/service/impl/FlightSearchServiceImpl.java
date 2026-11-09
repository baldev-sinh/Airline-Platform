package com.baldev.airline.flightops.service.impl;

import com.baldev.airline.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;

import com.baldev.airline.flightops.client.PricingClient;
import com.baldev.airline.flightops.client.SeatClient;
import com.baldev.airline.flightops.model.FlightInstance;
import com.baldev.airline.flightops.payload.request.FlightSearchRequest;
import com.baldev.airline.flightops.client.dto.CabinClassResponse;
import com.baldev.airline.flightops.client.dto.FareResponse;
import com.baldev.airline.flightops.payload.response.FlightInstanceResponse;
import com.baldev.airline.flightops.repository.FlightInstanceRepository;
import com.baldev.airline.flightops.service.FlightSearchService;
import com.baldev.airline.flightops.service.specification.FlightInstanceSpecification;
import com.baldev.airline.flightops.service.support.FlightResponseAssembler;
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
    private final FlightResponseAssembler assembler;

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
        // Airline/aircraft/airport details only decorate results: if those services are down the page is
        // still served, marked partial (see ReferenceData). One lookup per distinct id for the whole page.
        List<FlightInstanceResponse> results = assembler.toInstanceResponses(instances);
        for (FlightInstanceResponse response : results) {
            response.setFare(fareMap.get(response.getFlightId()));
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
