package com.baldev.airline.ancillary.service.impl;

import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.ancillary.mapper.InsuranceCoverageMapper;
import com.baldev.airline.ancillary.model.Ancillary;
import com.baldev.airline.ancillary.model.InsuranceCoverage;
import com.baldev.airline.ancillary.payload.request.InsuranceCoverageRequest;
import com.baldev.airline.ancillary.payload.response.InsuranceCoverageResponse;
import com.baldev.airline.ancillary.repository.AncillaryRepository;
import com.baldev.airline.ancillary.repository.InsuranceCoverageRepository;
import com.baldev.airline.ancillary.service.InsuranceCoverageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
// Every public method runs in a transaction, so lazy relations can be mapped to responses (open-in-view is off).
@Transactional
public class InsuranceCoverageServiceImpl implements InsuranceCoverageService {

    private final AncillaryRepository ancillaryRepository;
    private final InsuranceCoverageRepository insuranceCoverageRepository;

    @Override
    public InsuranceCoverageResponse createCoverage(InsuranceCoverageRequest request) {
        Ancillary ancillary = ancillaryRepository.findById(request.getAncillaryId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("ancillary not found with provided id")
                );

        InsuranceCoverage coverage = InsuranceCoverageMapper.toEntity(request, ancillary);

        InsuranceCoverage saved = insuranceCoverageRepository.save(coverage);


        return InsuranceCoverageMapper.toResponse(saved);
    }

    @Override
    public InsuranceCoverageResponse updateCoverage(Long id, InsuranceCoverageRequest request) {
        InsuranceCoverage insuranceCoverage = insuranceCoverageRepository.findById(
                id
        ).orElseThrow(
                () -> new ResourceNotFoundException("insurance coverage not found with id")
        );

        Ancillary ancillary = null;
        if (request.getAncillaryId() != null) {
            ancillary = ancillaryRepository.findById(request.getAncillaryId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("ancillary not found with provided id")
                    );
        }
        InsuranceCoverageMapper.updateEntityFromRequest(insuranceCoverage, request, ancillary);
        InsuranceCoverage saved = insuranceCoverageRepository.save(insuranceCoverage);
        return InsuranceCoverageMapper.toResponse(saved);
    }

    @Override
    public void deleteCoverage(Long id) {
        InsuranceCoverage insuranceCoverage = insuranceCoverageRepository.findById(
                id
        ).orElseThrow(
                () -> new ResourceNotFoundException("insurance coverage not found with id")
        );
        insuranceCoverageRepository.delete(insuranceCoverage);
    }

    @Override
    public InsuranceCoverageResponse getCoverage(Long id) {
        InsuranceCoverage insuranceCoverage = insuranceCoverageRepository.findById(
                id
        ).orElseThrow(
                () -> new ResourceNotFoundException("insurance coverage not found with id")
        );
        return InsuranceCoverageMapper.toResponse(insuranceCoverage);
    }

    @Override
    public List<InsuranceCoverageResponse> getCoverageByAncillaryId(Long ancillaryId) {
        return insuranceCoverageRepository.findByAncillaryId(ancillaryId)
                .stream().map(InsuranceCoverageMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<InsuranceCoverageResponse> getActiveCoverageByAncillaryId(Long ancillaryId) {
        return insuranceCoverageRepository.findByAncillaryIdAndActiveTrue(ancillaryId)
                .stream().map(InsuranceCoverageMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<InsuranceCoverageResponse> getAllCoverages() {
        return insuranceCoverageRepository.findAll()
                .stream().map(
                        InsuranceCoverageMapper::toResponse
                ).collect(Collectors.toList());
    }
}
