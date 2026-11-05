package com.baldev.controller;

import com.baldev.payload.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping()
    public ApiResponse HomeController() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage("Payment Service handles payment processing, " +
                "payment status tracking, and refunds for bookings.");
        return apiResponse;
    }
}
