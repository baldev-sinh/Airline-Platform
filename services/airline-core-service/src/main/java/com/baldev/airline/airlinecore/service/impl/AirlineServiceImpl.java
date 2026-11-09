package com.baldev.airline.airlinecore.service.impl;

import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.common.enums.AirlineStatus;
import com.baldev.airline.airlinecore.mapper.AirlineMapper;
import com.baldev.airline.airlinecore.model.Airline;
import com.baldev.airline.airlinecore.payload.request.AirlineRequest;
import com.baldev.airline.airlinecore.payload.response.AirlineDropdownItem;
import com.baldev.airline.airlinecore.payload.response.AirlineResponse;
import com.baldev.airline.airlinecore.repository.AirlineRepository;
import com.baldev.airline.airlinecore.service.AirlineService;
import com.baldev.airline.airlinecore.config.AirlineCoreCacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
// Every public method runs in a transaction, so lazy relations can be mapped to responses (open-in-view is off).
@Transactional
public class AirlineServiceImpl implements AirlineService {

    private final AirlineRepository airlineRepository;

    @Override
    @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRLINE_BY_OWNER, key = "#ownerId")
    public AirlineResponse createAirline(AirlineRequest request, Long ownerId) {
        if (airlineRepository.findByOwnerId(ownerId).isPresent()) {
            throw new ConflictException("AIRLINE_EXISTS", "This account already owns an airline");
        }

        Airline airline= AirlineMapper.toEntity(request,ownerId);

        Airline savedAirline= airlineRepository.save(airline);
        return AirlineMapper.toResponse(savedAirline);
    }

    @Override
    @Cacheable(cacheNames = AirlineCoreCacheConfig.AIRLINE_BY_OWNER, key = "#ownerId")
    public AirlineResponse getAirlineByOwner(Long ownerId) {
        Airline airline=airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(
                        ()->new ResourceNotFoundException("airline not found with ownerId "+ownerId)
                );
        return AirlineMapper.toResponse(airline);
    }

    @Override
    @Cacheable(cacheNames = AirlineCoreCacheConfig.AIRLINES, key = "#id")
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
    // Aircraft responses embed the airline name, so they are dropped too.
    @Caching(evict = {
            @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRLINE_BY_OWNER, key = "#ownerId"),
            @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRLINES, key = "#result.id"),
            @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRLINE_DROPDOWN, allEntries = true),
            @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRCRAFT, allEntries = true)
    })
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
    @Caching(evict = {
            @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRLINE_BY_OWNER, key = "#ownerId"),
            @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRLINES, key = "#id"),
            @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRLINE_DROPDOWN, allEntries = true),
            @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRCRAFT, allEntries = true)
    })
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
    // Approve/suspend/ban must reach every service at once: drop the owner lookup other services rely on.
    @Caching(evict = {
            @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRLINES, key = "#airlineId"),
            @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRLINE_BY_OWNER, key = "#result.ownerId", condition = "#result.ownerId != null"),
            @CacheEvict(cacheNames = AirlineCoreCacheConfig.AIRLINE_DROPDOWN, allEntries = true)
    })
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
    @Cacheable(cacheNames = AirlineCoreCacheConfig.AIRLINE_DROPDOWN, key = "'active'")
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
