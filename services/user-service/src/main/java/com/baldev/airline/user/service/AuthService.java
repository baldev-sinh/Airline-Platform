package com.baldev.airline.user.service;

import com.baldev.airline.common.payload.request.LoginRequest;
import com.baldev.airline.common.payload.request.SignupRequest;
import com.baldev.airline.common.payload.response.AuthResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse signup(SignupRequest request);
}
