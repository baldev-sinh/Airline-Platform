package com.baldev.services.impl;

import com.baldev.mapper.InsuranceCoverageMapper;
import com.baldev.model.Ancillary;
import com.baldev.model.InsuranceCoverage;
import com.baldev.payload.request.InsuranceCoverageRequest;
import com.baldev.payload.response.InsuranceCoverageResponse;
import com.baldev.repository.AncillaryRepository;
import com.baldev.repository.InsuranceCoverageRepository;
import com.baldev.services.InsuranceCoverageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InsuranceCoverageServiceImpl implements InsuranceCoverageService {

    private final AncillaryRepository ancillaryRepository;
    private final InsuranceCoverageRepository insuranceCoverageRepository;

    @Override
    public InsuranceCoverageResponse createCoverage(InsuranceCoverageRequest request) throws Exception {
        Ancillary ancillary = ancillaryRepository.findById(request.getAncillaryId())
                .orElseThrow(
                        () -> new Exception("ancillary not found with provided id")
                );

        InsuranceCoverage coverage = InsuranceCoverageMapper.toEntity(request, ancillary);

        InsuranceCoverage saved = insuranceCoverageRepository.save(coverage);


        return InsuranceCoverageMapper.toResponse(saved);
    }

    @Override
    public InsuranceCoverageResponse updateCoverage(Long id, InsuranceCoverageRequest request) throws Exception {
        InsuranceCoverage insuranceCoverage = insuranceCoverageRepository.findById(
                id
        ).orElseThrow(
                () -> new Exception("insurance coverage not found with id")
        );

        Ancillary ancillary = null;
        if (request.getAncillaryId() != null) {
            ancillary = ancillaryRepository.findById(request.getAncillaryId())
                    .orElseThrow(
                            () -> new Exception("ancillary not found with provided id")
                    );
        }
        InsuranceCoverageMapper.updateEntityFromRequest(insuranceCoverage, request, ancillary);
        InsuranceCoverage saved = insuranceCoverageRepository.save(insuranceCoverage);
        return InsuranceCoverageMapper.toResponse(saved);
    }

    @Override
    public void deleteCoverage(Long id) throws Exception {
        InsuranceCoverage insuranceCoverage = insuranceCoverageRepository.findById(
                id
        ).orElseThrow(
                () -> new Exception("insurance coverage not found with id")
        );
        insuranceCoverageRepository.delete(insuranceCoverage);
    }

    @Override
    public InsuranceCoverageResponse getCoverage(Long id) throws Exception {
        InsuranceCoverage insuranceCoverage = insuranceCoverageRepository.findById(
                id
        ).orElseThrow(
                () -> new Exception("insurance coverage not found with id")
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
