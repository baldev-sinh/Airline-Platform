package com.baldev.service;


import com.baldev.enums.CabinClassType;
import com.baldev.payload.request.CabinClassRequest;
import com.baldev.payload.response.CabinClassResponse;

import java.util.List;

public interface CabinClassService {

    CabinClassResponse createCabinClass(CabinClassRequest cabinClassRequest) throws Exception;

    CabinClassResponse getCabinClassById(Long id) throws Exception;

    List<CabinClassResponse> getCabinClassesByAircraftId(Long aircraftId);

    CabinClassResponse getByAircraftIdAndName(Long aircraftId, CabinClassType name);

    CabinClassResponse updateCabinClass(Long id, CabinClassRequest cabinClassRequest) throws Exception;

    void deleteCabinClass(Long id) throws Exception;


}
