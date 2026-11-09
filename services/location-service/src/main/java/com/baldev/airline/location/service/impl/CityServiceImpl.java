package com.baldev.airline.location.service.impl;

import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.location.mapper.CityMapper;
import com.baldev.airline.location.model.City;
import com.baldev.airline.location.payload.request.CityRequest;
import com.baldev.airline.location.payload.response.CityResponse;
import com.baldev.airline.location.repository.CityRepository;
import com.baldev.airline.location.service.CityService;
import com.baldev.airline.location.config.LocationCacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// Every public method runs in a transaction, so lazy relations can be mapped to responses (open-in-view is off).
@Transactional
public class CityServiceImpl implements CityService {


    private final CityRepository cityRepository;

    @Override
    public CityResponse createCity(CityRequest request) {

        if(cityRepository.existsByCityCode(request.getCityCode())){
            throw new ConflictException("city with given code already exist");
        }

        City city = CityMapper.toEntity(request);
        City saved = cityRepository.save(city);

        return CityMapper.toResponse(saved);
    }

    @Override
    @Cacheable(cacheNames = LocationCacheConfig.CITIES, key = "#id")
    public CityResponse getCityById(Long id) {
        City city = cityRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("city not exists with given id: " + id)
                );
        return CityMapper.toResponse(city);
    }

    @Override
    // Airport responses embed their city, so cached airports are dropped as well.
    @Caching(evict = {
            @CacheEvict(cacheNames = LocationCacheConfig.CITIES, key = "#id"),
            @CacheEvict(cacheNames = LocationCacheConfig.AIRPORTS, allEntries = true),
            @CacheEvict(cacheNames = LocationCacheConfig.ALL_AIRPORTS, allEntries = true),
            @CacheEvict(cacheNames = LocationCacheConfig.AIRPORTS_BY_CITY, allEntries = true)
    })
    public CityResponse updateCity(Long id, CityRequest request) {
        City city = cityRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("city not exists with given id: " + id)
                );

        // Only a changed code can collide; keeping the city's own code is not a conflict.
        if (request.getCityCode() != null && !request.getCityCode().equals(city.getCityCode())
                && cityRepository.existsByCityCode(request.getCityCode())) {
            throw new ConflictException("city with given code already exist:" + request.getCityCode());
        }

        City updatedCity = cityRepository.save(CityMapper.updateEntity(city, request));

        return CityMapper.toResponse(updatedCity);
    }

    @Override
    // Airport responses embed their city, so cached airports are dropped as well.
    @Caching(evict = {
            @CacheEvict(cacheNames = LocationCacheConfig.CITIES, key = "#id"),
            @CacheEvict(cacheNames = LocationCacheConfig.AIRPORTS, allEntries = true),
            @CacheEvict(cacheNames = LocationCacheConfig.ALL_AIRPORTS, allEntries = true),
            @CacheEvict(cacheNames = LocationCacheConfig.AIRPORTS_BY_CITY, allEntries = true)
    })
    public void deleteCity(Long id) {
        City city = cityRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("city not exists with given id: " + id)
                );

        cityRepository.delete(city);
    }

    @Override
    public Page<CityResponse> getAllCities(Pageable pageable) {
        return cityRepository
                .findAll(pageable)
                .map(CityMapper::toResponse);
    }

    @Override
    public Page<CityResponse> searchCities(String keyword, Pageable pageable) {
        return cityRepository
                .searchByKeyword(keyword, pageable)
                .map(CityMapper::toResponse);
    }

    @Override
    public Page<CityResponse> getCitiesByCountryCode(String countryCode, Pageable pageable) {
        return cityRepository
                .findByCountryCodeIgnoreCase(countryCode, pageable)
                .map(CityMapper::toResponse);
    }

    @Override
    public boolean cityExists(String cityCode) {
        return cityRepository.existsByCityCode(cityCode);
    }

}
