package com.baldev.airline.user.controller;

import com.baldev.airline.common.payload.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping
    public ApiResponse HomeController() {
        ApiResponse apiResponse = new ApiResponse(
                "hay everyone! im user service of airline system");
        return apiResponse;
    }
}
