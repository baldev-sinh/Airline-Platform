package com.baldev.airline.location.service.impl;

import com.baldev.airline.location.mapper.AirportMapper;
import com.baldev.airline.location.model.Airport;
import com.baldev.airline.location.model.City;
import com.baldev.airline.common.payload.request.AirportRequest;
import com.baldev.airline.common.payload.response.AirportResponse;
import com.baldev.airline.location.repository.AirportRepository;
import com.baldev.airline.location.repository.CityRepository;
import com.baldev.airline.location.service.AirportService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AirportServiceImpl implements AirportService {


    private final AirportRepository airportRepository;
    private final CityRepository cityRepository;

    @Override
    public AirportResponse createAirport(AirportRequest request) throws Exception {

        if(airportRepository.findByIataCode(request.getIataCode()).isPresent()){
            throw new Exception("Airport with Iata Code Already Exist");
        }

        City city = cityRepository.findById(request.getCityId())
                .orElseThrow(() -> new Exception("City not found"));
        Airport airport= AirportMapper .toEntity(request);
        airport.setCity(city);

        Airport savedAirport = airportRepository.save(airport);

        return AirportMapper.toResponse(savedAirport);
    }

    @Override
    @Cacheable(cacheNames = "airports", key = "#id")
    public AirportResponse getAirportById(Long id) throws Exception {
        Airport airport= airportRepository.findById(id).orElseThrow(
                ()-> new Exception("airport not exist with provided id")
        );
        return AirportMapper.toResponse(airport);
    }

    @Override
    @Cacheable(cacheNames = "allAirports")
    public List<AirportResponse> getAllAirports() {
        return airportRepository.findAll().stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Caching(evict={
            @CacheEvict(cacheNames = "airports",key = "#id"),
            @CacheEvict(cacheNames = "allAirports", allEntries = true),
            @CacheEvict(cacheNames = "airportsByCity", allEntries = true)
    })
    public AirportResponse updateAirport(Long id, AirportRequest request) throws Exception {
        Airport existingAirport = airportRepository.findById(id).orElseThrow(
                ()-> new Exception("airport not exist with id "+id)
        );
        if(request.getIataCode()!=null
                && !existingAirport.getIataCode().equals(request.getIataCode())
                && airportRepository.findByIataCode(request.getIataCode()).isPresent()
        ){
            throw new Exception("Airport with Iata Code Already Exist");
        }
        AirportMapper.updateEntity(request, existingAirport);
        Airport updatedAirport=airportRepository.save(existingAirport);
        return AirportMapper.toResponse(updatedAirport);
    }

    @Override
    @Caching(evict={
            @CacheEvict(cacheNames = "airports",key = "#id"),
            @CacheEvict(cacheNames = "allAirports", allEntries = true),
            @CacheEvict(cacheNames = "airportsByCity", allEntries = true)
    })
    public void deleteAirport(Long id) throws Exception {
        Airport airport= airportRepository.findById(id).orElseThrow(
                ()-> new Exception("airport not exist with provided id")
        );
        airportRepository.delete(airport);
    }

    @Override
    @Cacheable(cacheNames = "airportsByCity", key="#cityId")
    public List<AirportResponse> getAirportByCityId(Long cityId) {
        return airportRepository.findByCityId(cityId).stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }
}
