package com.baldev.airline.airlinecore.service.impl;

import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.common.enums.AirlineStatus;
import com.baldev.airline.airlinecore.mapper.AirlineMapper;
import com.baldev.airline.airlinecore.model.Airline;
import com.baldev.airline.common.payload.request.AirlineRequest;
import com.baldev.airline.common.payload.response.AirlineDropdownItem;
import com.baldev.airline.common.payload.response.AirlineResponse;
import com.baldev.airline.airlinecore.repository.AirlineRepository;
import com.baldev.airline.airlinecore.service.AirlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AirlineServiceImpl implements AirlineService {

    private final AirlineRepository airlineRepository;

    @Override
    public AirlineResponse createAirline(AirlineRequest request, Long ownerId) {
        if (airlineRepository.findByOwnerId(ownerId).isPresent()) {
            throw new ConflictException("AIRLINE_EXISTS", "This account already owns an airline");
        }

        Airline airline= AirlineMapper.toEntity(request,ownerId);

        Airline savedAirline= airlineRepository.save(airline);
        return AirlineMapper.toResponse(savedAirline);
    }

    @Override
    public AirlineResponse getAirlineByOwner(Long ownerId) {
        Airline airline=airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(
                        ()->new ResourceNotFoundException("airline not found with ownerId "+ownerId)
                );
        return AirlineMapper.toResponse(airline);
    }

    @Override
    public AirlineResponse getAirlineById(Long id) {
        Airline airline=airlineRepository.findById(id)
                .orElseThrow(
                        ()->new ResourceNotFoundException("airline not found with ownerId "+id)
                );
        return AirlineMapper.toResponse(airline);
    }

    @Override
    public Page<AirlineResponse> getAllAirlines(Pageable pageable) {
        return airlineRepository.findAll(pageable).map(
                AirlineMapper::toResponse
        );
    }

    @Override
    public AirlineResponse updateAirline(AirlineRequest request, Long ownerId) {
        Airline airline=airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(
                        ()->new ResourceNotFoundException("airline not found with ownerId "+ownerId)
                );
        AirlineMapper.updateEntity(airline,request);
        Airline savedAirline= airlineRepository.save(airline);
        return AirlineMapper.toResponse(savedAirline);
    }

    @Override
    public void deleteAirline(Long id, Long ownerId) {
        Airline airline=airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(
                        ()->new ResourceNotFoundException("airline not found with ownerId "+ownerId)
                );
        if (!airline.getId().equals(id)) {
            throw new ResourceNotFoundException("airline not found with id " + id);
        }
        airlineRepository.delete(airline);
    }

    @Override
    public AirlineResponse changeStatusByAdmin(Long airlineId, AirlineStatus status) {
        Airline airline=airlineRepository.findById(airlineId)
                .orElseThrow(
                        ()->new ResourceNotFoundException("airline not found with ownerId "+airlineId)
                );
        airline.setStatus(status);
        Airline updatedAirline = airlineRepository.save(airline);
        return AirlineMapper.toResponse(updatedAirline);
    }

    @Override
    public List<AirlineDropdownItem> getAirlineDropdown() {
        return airlineRepository.findByStatus(AirlineStatus.ACTIVE)
                .stream()
                .map(a->AirlineDropdownItem.builder()
                        .id(a.getId())
                        .name(a.getName())
                        .iataCode(a.getIataCode())
                        .icaoCode(a.getIcaoCode())
                        .logoUrl(a.getLogoUrl())
                        .build()).toList();

    }
}
