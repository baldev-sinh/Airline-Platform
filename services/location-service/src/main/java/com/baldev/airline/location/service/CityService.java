package com.baldev.airline.location.service;

import com.baldev.airline.common.payload.request.CityRequest;
import com.baldev.airline.common.payload.response.CityResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CityService {

    CityResponse createCity(CityRequest request) throws Exception;

    CityResponse getCityById(Long id) throws Exception;

    CityResponse updateCity(Long id, CityRequest request) throws Exception;

    void deleteCity(Long id) throws Exception;

    Page<CityResponse> getAllCities(Pageable pageable);

    Page<CityResponse> searchCities(String keyword, Pageable pageable);

    Page<CityResponse> getCitiesByCountryCode(String countryCode, Pageable pageable);

    boolean cityExists(String cityCode);

}
