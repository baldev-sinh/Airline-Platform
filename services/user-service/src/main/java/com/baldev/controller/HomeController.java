package com.baldev.controller;

import com.baldev.payload.response.ApiResponse;
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
