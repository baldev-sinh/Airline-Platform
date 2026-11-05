package com.baldev.airline.payment.controller;

import com.baldev.airline.common.payload.response.ApiResponse;
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
