package com.baldev.airline.airlinecore.service.impl;

import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.airlinecore.mapper.AircraftMapper;
import com.baldev.airline.airlinecore.model.Aircraft;
import com.baldev.airline.airlinecore.model.Airline;
import com.baldev.airline.common.payload.request.AircraftRequest;
import com.baldev.airline.common.payload.response.AircraftResponse;
import com.baldev.airline.airlinecore.repository.AircraftRepository;
import com.baldev.airline.airlinecore.repository.AirlineRepository;
import com.baldev.airline.airlinecore.service.AircraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AircraftServiceImpl implements AircraftService {

    private final AirlineRepository airlineRepository;
    private final AircraftRepository aircraftRepository;

    @Override
    public AircraftResponse createAircraft(AircraftRequest request, Long ownerId) {
        Airline airline=airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(
                        ()->new ResourceNotFoundException("airline not exist for this ownerId")
                );

        Aircraft aircraft= AircraftMapper.toEntity(request,airline);

        if(aircraftRepository.existsByCode(aircraft.getCode())){
            throw new ConflictException("code already exist with another aircraft");
        }

        if(aircraft.getSeatingCapacity()<aircraft.getTotalSeats()){
            throw new BusinessRuleException("seating capacity can't exceed to total seat");
        }
        return AircraftMapper.toResponse(
                aircraftRepository.save(aircraft)
        );
    }

    @Override
    public AircraftResponse getAircraftById(Long id) {
        Aircraft aircraft= aircraftRepository.findById(id)
                .orElseThrow(
                        ()->new ResourceNotFoundException("Aircraft not exist with id")
                );
        return AircraftMapper.toResponse(aircraft);
    }

    @Override
    public List<AircraftResponse> listAllAircraftByOwner(Long ownerId) {
        Airline airline=airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(
                        ()-> new ResourceNotFoundException("this owner don't have airline")
                );

        return aircraftRepository.findByAirlineId(airline.getId())
                .stream()
                .map(AircraftMapper::toResponse).toList();
    }

    @Override
    public AircraftResponse updateAircraft(Long id, AircraftRequest request, Long ownerId) {
        Airline airline=airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(
                        ()-> new ResourceNotFoundException("this owner don't have airline")
                );
        Aircraft aircraft=aircraftRepository.findByIdAndAirlineId(id,airline.getId());
        if(aircraft==null){
            throw new ResourceNotFoundException("Aircraft not exist with id");
        }
        if(request.getCode()!=null
                && !aircraft.getCode().equals(request.getCode())
                && aircraftRepository.existsByCode(request.getCode())){
            throw new ConflictException("code already exist with another aircraft");
        }
        AircraftMapper.updateEntity(aircraft, request);
        return AircraftMapper.toResponse(
                aircraftRepository.save(aircraft)
        );
    }

    @Override
    public void deleteAircraft(Long id, Long ownerId) {
        Airline airline=airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(
                        ()-> new ResourceNotFoundException("this owner don't have airline")
                );
        Aircraft aircraft=aircraftRepository.findByIdAndAirlineId(id,airline.getId());
        if(aircraft==null){
            throw new ResourceNotFoundException("Aircraft not exist with id");
        }
        aircraftRepository.delete(aircraft);

    }
}
