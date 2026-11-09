package com.baldev.airline.seat.service;


import com.baldev.airline.common.enums.CabinClassType;
import com.baldev.airline.seat.payload.request.CabinClassRequest;
import com.baldev.airline.seat.payload.response.CabinClassResponse;
import java.util.List;

public interface CabinClassService {

    CabinClassResponse createCabinClass(CabinClassRequest cabinClassRequest);

    CabinClassResponse getCabinClassById(Long id);

    List<CabinClassResponse> getCabinClassesByAircraftId(Long aircraftId);

    CabinClassResponse getByAircraftIdAndName(Long aircraftId, CabinClassType name);

    CabinClassResponse updateCabinClass(Long id, CabinClassRequest cabinClassRequest);

    void deleteCabinClass(Long id);


}
