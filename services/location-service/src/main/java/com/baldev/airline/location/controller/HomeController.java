package com.baldev.airline.location.controller;

import com.baldev.airline.common.payload.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public ApiResponse welcome(){
        ApiResponse response = new ApiResponse("Hello, I am location service of airline microservices");
        return response;
    }
}
