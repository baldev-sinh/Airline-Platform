package com.baldev.airline.pricing.service.impl;

import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;

import com.baldev.airline.pricing.mapper.BaggagePolicyMapper;
import com.baldev.airline.pricing.model.BaggagePolicy;
import com.baldev.airline.pricing.model.Fare;
import com.baldev.airline.common.payload.request.BaggagePolicyRequest;
import com.baldev.airline.common.payload.response.BaggagePolicyResponse;
import com.baldev.airline.pricing.repository.BaggagePolicyRepository;
import com.baldev.airline.pricing.repository.FareRepository;
import com.baldev.airline.pricing.service.BaggagePolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BaggagePolicyServiceImpl implements BaggagePolicyService {

    private final FareRepository fareRepository;
    private final BaggagePolicyRepository baggagePolicyRepository;

    @Override
    public BaggagePolicyResponse createBaggagePolicy(BaggagePolicyRequest request) {
        Fare fare=fareRepository.findById(request.getFareId())
                .orElseThrow(
                        ()-> new ResourceNotFoundException("baggage policy not found")
                );
        if(baggagePolicyRepository.existsByFareId(fare.getId())){
            throw new ConflictException("baggage policy already exists");
        }
        BaggagePolicy baggagePolicy= BaggagePolicyMapper.toEntity(request,fare);
        BaggagePolicy saved=baggagePolicyRepository.save(baggagePolicy);
        return BaggagePolicyMapper.toResponse(saved);
    }

    @Override
    public BaggagePolicyResponse getBaggagePolicyById(Long id) {
        BaggagePolicy baggagePolicy=baggagePolicyRepository.findById(id)
                .orElseThrow(
                        ()->new ResourceNotFoundException("policy not found with id")
                );
        return BaggagePolicyMapper.toResponse(baggagePolicy);
    }

    @Override
    public BaggagePolicyResponse getBaggagePolicyByFareId(Long fareId) {
        BaggagePolicy baggagePolicy=baggagePolicyRepository.findByFareId(fareId);
        return  BaggagePolicyMapper.toResponse(baggagePolicy);
    }

    @Override
    public List<BaggagePolicyResponse> getBaggagePoliciesByAirlineId(Long airlineId) {
        return baggagePolicyRepository.findByAirlineId(airlineId)
                .stream().map(
                        BaggagePolicyMapper::toResponse
                ).collect(Collectors.toList());
    }

    @Override
    public BaggagePolicyResponse updateBaggagePolicy(Long id, BaggagePolicyRequest request) {
        BaggagePolicy baggagePolicy=baggagePolicyRepository.findById(id)
                .orElseThrow(
                        ()->new ResourceNotFoundException("policy not found with id")
                );
        BaggagePolicyMapper.updateEntity(request,baggagePolicy);
        BaggagePolicy saved=baggagePolicyRepository.save(baggagePolicy);
        return BaggagePolicyMapper.toResponse(saved);
    }

    @Override
    public void deleteBaggagePolicy(Long id) {
        BaggagePolicy policy = baggagePolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Baggage policy not found with id: " + id));
        baggagePolicyRepository.delete(policy);
    }
}
