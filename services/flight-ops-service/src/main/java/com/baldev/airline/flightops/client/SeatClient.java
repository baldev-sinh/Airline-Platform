package com.baldev.airline.flightops.client;


import com.baldev.airline.common.enums.CabinClassType;
import com.baldev.airline.common.payload.response.CabinClassResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "seat-service")
public interface SeatClient {

    @PostMapping("/api/seat-instances/price/price")
    Double calculateSeatPrice(@RequestBody List<Long> seatInstanceIds);

    @GetMapping("/api/cabin-classes/aircraft/{id}/name/{cabinClass}")
    CabinClassResponse getCabinClassByAircraftIdAndName(
            @PathVariable CabinClassType cabinClass,
            @PathVariable Long id
    );

}
