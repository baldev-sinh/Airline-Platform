package com.baldev.airline.airlinecore.controller;

import com.baldev.airline.common.payload.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping
    public ApiResponse homeController() {
        ApiResponse apiResponse = new ApiResponse(
                "hay everyone, im airline core service & i will Manages airlines, " +
                        "aircraft fleet, aircraft models, " +
                        "and operational inventory for the airline system."
        );
        return apiResponse;
    }
}
