package com.baldev.airline.seat.controller;

import com.baldev.airline.common.payload.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping()
    public ApiResponse HomeController() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage("hello everyone im seat service of airline microservices," +
                "seat Service manages cabin configuration, seat maps, and seat inventory for flights." +
                "It handles availability and seat status in real time.");
        return apiResponse;
    }
}
