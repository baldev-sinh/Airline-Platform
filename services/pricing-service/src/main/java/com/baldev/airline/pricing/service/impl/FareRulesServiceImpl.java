package com.baldev.airline.pricing.service.impl;

import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;

import com.baldev.airline.pricing.mapper.FareRuleMapper;
import com.baldev.airline.pricing.model.Fare;
import com.baldev.airline.pricing.model.FareRules;
import com.baldev.airline.pricing.payload.request.FareRulesRequest;
import com.baldev.airline.pricing.payload.response.FareRulesResponse;
import com.baldev.airline.pricing.repository.FareRepository;
import com.baldev.airline.pricing.repository.FareRuleRepository;
import com.baldev.airline.pricing.service.FareRulesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
// Every public method runs in a transaction, so lazy relations can be mapped to responses (open-in-view is off).
@Transactional
public class FareRulesServiceImpl implements FareRulesService {

    private final FareRepository fareRepository;
    private final FareRuleRepository fareRuleRepository;

    @Override
    public FareRulesResponse createFareRules(FareRulesRequest request) {
        Fare fare=fareRepository.findById(request.getFareId())
                .orElseThrow(()-> new ResourceNotFoundException("Fare not found"));

        if(fareRuleRepository.existsByFareId(fare.getId())){
            throw new ConflictException("Fare already exists");
        }

        FareRules fareRules= FareRuleMapper.toEntity(request,fare);
        FareRules savedFareRules= fareRuleRepository.save(fareRules);

        return FareRuleMapper.toResponse(savedFareRules);
    }

    @Override
    public FareRulesResponse getFareRulesById(Long id) {
        FareRules fareRules= fareRuleRepository.findById(id).orElseThrow(
                ()-> new ResourceNotFoundException("fare rule not found")
        );
        return FareRuleMapper.toResponse(fareRules);
    }

    @Override
    public FareRulesResponse getFareRulesByFareId(Long fareId) {
        FareRules fareRules = fareRuleRepository.findByFareId(fareId);
        if (fareRules == null) {
            // A fare without rules is a normal case (booking-service then applies its default refund policy).
            throw new ResourceNotFoundException("No fare rules for fare " + fareId);
        }
        return FareRuleMapper.toResponse(fareRules);
    }

    @Override
    public List<FareRulesResponse> getFareRulesByAirlineId(Long airlineId) {
        return fareRuleRepository.findByAirlineId(airlineId).stream()
                .map(FareRuleMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public FareRulesResponse updateFareRules(Long id, FareRulesRequest request) {

        FareRules fareRules= fareRuleRepository.findById(id).orElseThrow(
                ()-> new ResourceNotFoundException("fare rule not found")
        );
        FareRuleMapper.updateEntity(request,fareRules);
        FareRules savedFareRules= fareRuleRepository.save(fareRules);
        return FareRuleMapper.toResponse(savedFareRules);
    }

    @Override
    public void deleteFareRules(Long id) {
        FareRules fareRules = fareRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Fare rules not found with id: " + id));
        fareRuleRepository.delete(fareRules);
    }
}
