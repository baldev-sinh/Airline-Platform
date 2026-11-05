package com.baldev.airline.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "seat-service")
public interface SeatClient {

    @PostMapping("/api/seat-instances/price/price")
    Double calculateSeatPrice(@RequestBody List<Long> seatInstanceIds);

}
