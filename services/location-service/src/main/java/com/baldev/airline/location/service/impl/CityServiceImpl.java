package com.baldev.airline.location.service.impl;

import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.location.mapper.CityMapper;
import com.baldev.airline.location.model.City;
import com.baldev.airline.common.payload.request.CityRequest;
import com.baldev.airline.common.payload.response.CityResponse;
import com.baldev.airline.location.repository.CityRepository;
import com.baldev.airline.location.service.CityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
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
    public CityResponse getCityById(Long id) {
        City city = cityRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("city not exists with given id: " + id)
                );
        return CityMapper.toResponse(city);
    }

    @Override
    public CityResponse updateCity(Long id, CityRequest request) {
        City city = cityRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("city not exists with given id: " + id)
                );

        if(cityRepository.existsByCityCode(request.getCityCode())){
            throw new ConflictException("city with given code already exist:" + request.getCityCode());
        }

        City updatedCity = cityRepository.save(CityMapper.updateEntity(city, request));

        return CityMapper.toResponse(updatedCity);
    }

    @Override
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
