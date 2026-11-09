package com.baldev.airline.ancillary.service.impl;

import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.ancillary.client.AirlineClient;
import com.baldev.airline.ancillary.mapper.AncillaryMapper;
import com.baldev.airline.ancillary.mapper.InsuranceCoverageMapper;
import com.baldev.airline.ancillary.model.Ancillary;
import com.baldev.airline.ancillary.model.InsuranceCoverage;
import com.baldev.airline.ancillary.payload.request.AncillaryRequest;
import com.baldev.airline.ancillary.client.dto.AirlineResponse;
import com.baldev.airline.ancillary.payload.response.AncillaryResponse;
import com.baldev.airline.ancillary.payload.response.InsuranceCoverageResponse;
import com.baldev.airline.ancillary.repository.AncillaryRepository;
import com.baldev.airline.ancillary.repository.InsuranceCoverageRepository;
import com.baldev.airline.ancillary.service.AncillaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
// Every public method runs in a transaction, so lazy relations can be mapped to responses (open-in-view is off).
@Transactional
public class AncillaryServiceImpl implements AncillaryService {


    private final AncillaryRepository ancillaryRepository;
    private final InsuranceCoverageRepository coverageRepository;
    private final AirlineClient airlineClient;

    @Override
    public AncillaryResponse createAncillary(Long userId, AncillaryRequest request) {

        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId);

        Ancillary ancillary = Ancillary.builder()
                .type(request.getType())
                .subType(request.getSubType())
                .rfisc(request.getRfisc())
                .name(request.getName())
                .description(request.getDescription())
                .metadata(request.getMetadata())
                .displayOrder(request.getDisplayOrder())
                // The airline's id, not its owner's user id: extras are listed (and must be isolated) per airline.
                .airlineId(airlineResponse.getId())
                .build();

        Ancillary saved = ancillaryRepository.save(ancillary);
        return AncillaryMapper.toResponse(saved, null);
    }

    @Override
    public AncillaryResponse getById(Long id) {
        Ancillary ancillary = ancillaryRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Ancillary not found")
                );

//        fetch insurance coverages by ancillary
        List<InsuranceCoverage> coverages = coverageRepository
                .findByAncillaryId(ancillary.getId());
        List<InsuranceCoverageResponse> coverageResponses = coverages.stream()
                .map(InsuranceCoverageMapper::toResponse)
                .toList();

        return AncillaryMapper.toResponse(ancillary, coverageResponses);
    }

    @Override
    public List<AncillaryResponse> getByAirlineId(Long userId) {
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId);
        return ancillaryRepository.findByAirlineId(airlineResponse.getId())
                .stream()
                .map(
                        ancillary -> {
//                            fetch insurance coverages by ancillary
                            List<InsuranceCoverage> coverages = coverageRepository
                                    .findByAncillaryId(ancillary.getId());
                            List<InsuranceCoverageResponse> coverageResponses = coverages.stream()
                                    .map(InsuranceCoverageMapper::toResponse)
                                    .toList();
                            return AncillaryMapper.toResponse(ancillary, coverageResponses);
                        }
                ).collect(Collectors.toList());
    }

    @Override
    public AncillaryResponse updateAncillary(Long id, AncillaryRequest request) {
        Ancillary ancillary = ancillaryRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Ancillary not found")
                );

        ancillary.setType(request.getType());
        ancillary.setSubType(request.getSubType());
        ancillary.setRfisc(request.getRfisc());
        ancillary.setName(request.getName());
        ancillary.setDescription(request.getDescription());
        ancillary.setMetadata(request.getMetadata());
        ancillary.setDisplayOrder(request.getDisplayOrder());

        Ancillary updated = ancillaryRepository.save(ancillary);

        // fetch insurance coverages by ancillary
        List<InsuranceCoverage> coverages = coverageRepository
                .findByAncillaryId(ancillary.getId());
        List<InsuranceCoverageResponse> coverageResponses = coverages.stream()
                .map(InsuranceCoverageMapper::toResponse)
                .toList();

        return AncillaryMapper.toResponse(updated, coverageResponses);
    }

    @Override
    public void deleteAncillary(Long id) {
        Ancillary ancillary = ancillaryRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Ancillary not found")
                );

        ancillaryRepository.delete(ancillary);
    }
}
