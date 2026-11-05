package com.baldev.airline.flightops.controller;


import com.baldev.airline.common.payload.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping
    public ApiResponse HomeController() {
        ApiResponse apiResponse = new ApiResponse(
                "Flight Operations Service manages Flights, " +
                        "Flight Schedules, and Flight Instances. " +
                        "It represents the core operational flight lifecycle."
        );
        return apiResponse;
    }
}
