package com.baldev.controller;

import com.baldev.payload.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping
    public ApiResponse HomeController() {
        ApiResponse apiResponse = new ApiResponse(
                "Booking Service orchestrates flight bookings and manages Booking, " +
                        "Passenger, and Ticket records."
        );
        return apiResponse;
    }
}
