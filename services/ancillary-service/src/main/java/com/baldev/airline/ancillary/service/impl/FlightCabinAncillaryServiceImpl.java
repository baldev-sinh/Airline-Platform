package com.baldev.airline.ancillary.service.impl;

import java.util.function.Function;
import java.util.Map;
import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.util.Money;
import com.baldev.airline.common.payload.request.PriceQuoteRequest;
import java.math.BigDecimal;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.common.enums.AncillaryType;
import com.baldev.airline.ancillary.mapper.FlightCabinAncillaryMapper;
import com.baldev.airline.ancillary.mapper.InsuranceCoverageMapper;
import com.baldev.airline.ancillary.model.Ancillary;
import com.baldev.airline.ancillary.model.FlightCabinAncillary;
import com.baldev.airline.ancillary.model.InsuranceCoverage;
import com.baldev.airline.common.payload.request.FlightCabinAncillaryRequest;
import com.baldev.airline.common.payload.response.FlightCabinAncillaryResponse;
import com.baldev.airline.common.payload.response.InsuranceCoverageResponse;
import com.baldev.airline.ancillary.repository.AncillaryRepository;
import com.baldev.airline.ancillary.repository.FlightCabinAncillaryRepository;
import com.baldev.airline.ancillary.repository.InsuranceCoverageRepository;
import com.baldev.airline.ancillary.service.FlightCabinAncillaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlightCabinAncillaryServiceImpl implements FlightCabinAncillaryService {

    private final FlightCabinAncillaryRepository flightCabinAncillaryRepository;
    private final AncillaryRepository ancillaryRepository;
    private final InsuranceCoverageRepository insuranceCoverageRepository;

    @Override
    public FlightCabinAncillaryResponse create(FlightCabinAncillaryRequest request) {
        Ancillary ancillary = ancillaryRepository.findById(request.getAncillaryId())
                .orElseThrow(() -> new ResourceNotFoundException("ancillary not found"));

        FlightCabinAncillary fCabinAncillary = FlightCabinAncillary.builder()
                .flightId(request.getFlightId())
                .cabinClassId(request.getCabinClassId())
                .ancillary(ancillary)
                .available(request.getAvailable())
                .maxQuantity(request.getMaxQuantity())
                .price(request.getPrice())
                .includedInFare(request.getIncludedInFare())
                .build();

        FlightCabinAncillary saved = flightCabinAncillaryRepository.save(fCabinAncillary);

        return convertToResponse(saved);
    }

    @Override
    public FlightCabinAncillaryResponse getById(Long id) {
        FlightCabinAncillary flightCabinAncillary = flightCabinAncillaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("flight cabin ancillary not found"));
        return convertToResponse(flightCabinAncillary);
    }

    @Override
    public List<FlightCabinAncillaryResponse> getByFlightAndCabinClass(Long flightId, Long cabinClassId) {
        return flightCabinAncillaryRepository.findByFlightIdAndCabinClassId(
                flightId, cabinClassId
        ).stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    public List<FlightCabinAncillaryResponse> getAllByIds(List<Long> ids) {
        return flightCabinAncillaryRepository.findAllById(ids)
                .stream()
                .map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    public FlightCabinAncillaryResponse getByFlightIdAndCabinClassIdAndType(
            Long flightId, Long cabinClassId,
            AncillaryType type) {
        FlightCabinAncillary flightCabinAncillary =
                flightCabinAncillaryRepository.findByFlightIdAndCabinClassIdAndAncillary_Type(
                        flightId, cabinClassId, type
                );
        return convertToResponse(flightCabinAncillary);
    }

    @Override
    public List<FlightCabinAncillaryResponse> getAllByFlightIdAndCabinClassIdAndType(Long flightId, Long cabinClassId, AncillaryType type) {
        return flightCabinAncillaryRepository.findAllByFlightIdAndCabinClassIdAndAncillary_Type(
                flightId, cabinClassId, type
        ).stream().map(
                this::convertToResponse
        ).collect(Collectors.toList());
    }

    @Override
    public FlightCabinAncillaryResponse update(Long id, FlightCabinAncillaryRequest request) {
        FlightCabinAncillary flightCabinAncillary = flightCabinAncillaryRepository.findById(
                id
        ).orElseThrow(() -> new ResourceNotFoundException("Flight cabin ancillary not found"));

        flightCabinAncillary.setAvailable(request.getAvailable());
        flightCabinAncillary.setMaxQuantity(request.getMaxQuantity());
        flightCabinAncillary.setPrice(request.getPrice());
        flightCabinAncillary.setIncludedInFare(request.getIncludedInFare());

        FlightCabinAncillary saved = flightCabinAncillaryRepository.save(flightCabinAncillary);
        return convertToResponse(saved);
    }

    @Override
    public void delete(Long id) {
        FlightCabinAncillary flightCabinAncillary = flightCabinAncillaryRepository.findById(
                id
        ).orElseThrow(() -> new ResourceNotFoundException("Flight cabin ancillary not found"));
        flightCabinAncillaryRepository.delete(flightCabinAncillary);
    }

    @Override
    public BigDecimal calculateAncillaryPrice(PriceQuoteRequest request) {
        List<Long> ids = request.getItemIds();
        if (ids == null || ids.isEmpty()) {
            return Money.normalize(BigDecimal.ZERO);
        }
        Map<Long, FlightCabinAncillary> byId = flightCabinAncillaryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(FlightCabinAncillary::getId, Function.identity()));

        Map<Long, Long> quantities = ids.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Long> entry : quantities.entrySet()) {
            FlightCabinAncillary item = byId.get(entry.getKey());
            if (item == null || !request.getFlightId().equals(item.getFlightId())
                    || (request.getCabinClassId() != null && !request.getCabinClassId().equals(item.getCabinClassId()))) {
                throw new ResourceNotFoundException("Ancillary " + entry.getKey() + " is not offered on this flight");
            }
            if (!Boolean.TRUE.equals(item.getAvailable())) {
                throw new BusinessRuleException("ANCILLARY_UNAVAILABLE", "Ancillary " + item.getId() + " is not available");
            }
            if (item.getMaxQuantity() != null && entry.getValue() > item.getMaxQuantity()) {
                throw new BusinessRuleException("ANCILLARY_QUANTITY_EXCEEDED",
                        "At most " + item.getMaxQuantity() + " of ancillary " + item.getId() + " allowed");
            }
            if (!Boolean.TRUE.equals(item.getIncludedInFare())) {
                total = total.add(Money.orZero(item.getPrice()).multiply(BigDecimal.valueOf(entry.getValue())));
            }
        }
        return Money.normalize(total);
    }

    private FlightCabinAncillaryResponse convertToResponse(FlightCabinAncillary ancillary) {
        List<InsuranceCoverage> coverages = insuranceCoverageRepository
                .findByAncillaryId(ancillary.getId());
        List<InsuranceCoverageResponse> coverageResponses = coverages.stream().map(
                InsuranceCoverageMapper::toResponse
        ).toList();

        return FlightCabinAncillaryMapper.toResponse(ancillary, coverageResponses);
    }
}
