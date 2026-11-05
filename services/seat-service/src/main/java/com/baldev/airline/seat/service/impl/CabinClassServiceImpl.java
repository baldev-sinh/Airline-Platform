package com.baldev.airline.seat.service.impl;

import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;

import com.baldev.airline.common.enums.CabinClassType;
import com.baldev.airline.seat.mapper.CabinClassMapper;
import com.baldev.airline.seat.model.CabinClass;
import com.baldev.airline.common.payload.request.CabinClassRequest;
import com.baldev.airline.common.payload.response.CabinClassResponse;
import com.baldev.airline.seat.repository.CabinClassRepository;
import com.baldev.airline.seat.service.CabinClassService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class CabinClassServiceImpl implements CabinClassService {

    private final CabinClassRepository cabinClassRepository;

    @Override
    public CabinClassResponse createCabinClass(CabinClassRequest request) {

        if (cabinClassRepository.existsByCodeAndAircraftId(
                request.getCode(),
                request.getAircraftId())
        ) {
            throw new ConflictException("cabin class with code already exist");
        }
        CabinClass cabinClass = CabinClassMapper.toEntity(request);

        CabinClass savedCabin = cabinClassRepository.save(cabinClass);

        return CabinClassMapper.toResponse(savedCabin, null);
    }

    @Override
    public CabinClassResponse getCabinClassById(Long id) {
        CabinClass cabinClass = cabinClassRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("cabin class not found with id")
        );
        return CabinClassMapper.toResponse(cabinClass, cabinClass.getSeatMap());
    }

    @Override
    public List<CabinClassResponse> getCabinClassesByAircraftId(Long aircraftId) {
        return cabinClassRepository.findByAircraftId(aircraftId)
                .stream()
                .map(cc -> CabinClassMapper.toResponse(cc, cc.getSeatMap()))
                .collect(Collectors.toList());
    }

    @Override
    public CabinClassResponse getByAircraftIdAndName(Long aircraftId, CabinClassType name) {
        CabinClass cabinClass = cabinClassRepository
                .findByAircraftIdAndName(aircraftId, name);
        return CabinClassMapper.toResponse(cabinClass, null);
    }

    @Override
    public CabinClassResponse updateCabinClass(Long id, CabinClassRequest cabinClassRequest) {
        CabinClass cabinClass = cabinClassRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("cabin class not found with id")
        );
//        existing cabin code=> PEC
//        request => BC

//        existing cabin code => BC
//        request => BC, busunes

        if (cabinClassRepository.existsByCodeAndAircraftIdAndIdNot(
                cabinClassRequest.getCode().toUpperCase(),
                cabinClass.getAircraftId(),
                cabinClass.getId()
        )) {
            throw new ConflictException("cabin class with code already exist");
        }


        CabinClassMapper.updateEntity(cabinClassRequest, cabinClass);
        CabinClass updated = cabinClassRepository.save(cabinClass);
        return CabinClassMapper.toResponse(updated, updated.getSeatMap());
    }

    @Override
    public void deleteCabinClass(Long id) {
        CabinClass cabinClass = cabinClassRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("cabin class not found with id")
        );
        cabinClassRepository.delete(cabinClass);
    }
}
