package com.baldev.airline.pricing.service.impl;

import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;

import com.baldev.airline.pricing.mapper.FareMapper;
import com.baldev.airline.pricing.model.Fare;
import com.baldev.airline.pricing.payload.request.FareRequest;
import com.baldev.airline.pricing.payload.response.FareResponse;
import com.baldev.airline.pricing.repository.FareRepository;
import com.baldev.airline.pricing.service.FareService;
import com.baldev.airline.pricing.config.PricingCacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
// Every public method runs in a transaction, so lazy relations can be mapped to responses (open-in-view is off).
@Transactional
public class FareServiceImpl implements FareService {

    private final FareRepository fareRepository;

    @Override
    public FareResponse createFare(FareRequest request) {
        if(fareRepository.existsByFlightIdAndCabinClassIdAndName(
                request.getFlightId(),
                request.getCabinClassId(),
                request.getName()
        )){
            throw new ConflictException("fare already exist with provided name");
        }
        Fare fare = FareMapper.toEntity(request);
        Fare saved=fareRepository.save(fare);
        return FareMapper.toResponse(saved);
    }

    @Override
    @Cacheable(cacheNames = PricingCacheConfig.FARES, key = "#id")
    public FareResponse getFareById(Long id) {
        Fare fare = fareRepository.findById(id).orElseThrow(
                ()-> new ResourceNotFoundException("Fare not found with given Id")
        );
        return FareMapper.toResponse(fare);
    }

    @Override
    public List<FareResponse> getFaresByFlightIdAndCabinClassId(Long flightId, Long cabinClassId) {
        return fareRepository.findByFlightIdAndCabinClassId(
                flightId,cabinClassId
        ).stream().map(
                FareMapper::toResponse
        ).toList();
    }

    @Override
    @CacheEvict(cacheNames = PricingCacheConfig.FARES, key = "#id")
    public FareResponse updateFare(Long id, FareRequest request) {
        Fare fare = fareRepository.findById(id).orElseThrow(
                ()-> new ResourceNotFoundException("Fare not found with given Id")
        );

        if(fareRepository.existsByFlightIdAndCabinClassIdAndNameAndIdNot(
                request.getFlightId(),
                request.getCabinClassId(),
                request.getName(),
                fare.getId()
        )) {
            throw new ConflictException("fare already exist with provided name");
        }
        FareMapper.updateEntity(request,fare);
        Fare updated=fareRepository.save(fare);
        return FareMapper.toResponse(updated);
    }

    @Override
    @CacheEvict(cacheNames = PricingCacheConfig.FARES, key = "#id")
    public void deleteFare(Long id) {
        Fare fare = fareRepository.findById(id).orElseThrow(
                ()-> new ResourceNotFoundException("Fare not found with given Id")
        );
        fareRepository.delete(fare);
    }

    @Override
    public List<FareResponse> getFares() {
        return fareRepository.findAll().stream().map(FareMapper::toResponse).toList();
    }
/*
 Flight 101:
  Fare 1 → ₹5000
  Fare 2 → ₹4500
  Fare 3 → ₹6000
*
flightIds    = [101, 102, 103]
cabinClassId = 2  (Economy)
*
[ Flight 101 → ₹5000
Flight 101 → ₹4500
Flight 101 → ₹6000

Flight 102 → ₹7000
Flight 102 → ₹6500 ]
*

{ 101 → Fare 4500
102 → Fare 6500
}

{
  101 → FareResponse(price=4500)
  102 → FareResponse(price=6500)
}
*/

    @Override
    public Map<Long, FareResponse> getLowestFarePerFlight(List<Long> flightIds, Long cabinClassId) {
        if(flightIds==null || flightIds.isEmpty())return Map.of();

        List<Fare> fares = fareRepository.findByFlightIdInAndCabinClassId(
                flightIds,cabinClassId
        );

        Map<Long, FareResponse> result=fares.stream()
                .collect(Collectors.toMap(
                        Fare::getFlightId,
                        fare -> fare,
                        (existing, candidate)->
                                candidate.getTotalPrice().compareTo(existing.getTotalPrice()) < 0
                        ?candidate:existing
                )).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e-> FareMapper.toResponse(e.getValue())
                ));

        return result;
    }

    @Override
    public FareResponse getLowestFareForFlightAndCabin(Long flightId, Long cabinClassId) {
        List<Fare> fares=fareRepository.findByFlightIdAndCabinClassId(
                flightId,cabinClassId
        );
        Fare lowestFare=fares.stream()
                .min(Comparator.comparing(Fare::getTotalPrice))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No fare found for flight " + flightId + " and cabin class " + cabinClassId));

        return FareMapper.toResponse(lowestFare);
    }

    @Override
    public Map<Long, FareResponse> getFaresByIds(List<Long> ids) {
        List<Fare> fares=fareRepository.findAllById(ids);

//        [fare response, fare response]

//        {
//            1 => fare response,
//            2 => fare response
//        }
        return fares.stream().collect(Collectors.toMap(Fare::getId, FareMapper::toResponse));

    }
}
