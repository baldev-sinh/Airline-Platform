package com.baldev.airline.user.service;

import com.baldev.airline.common.payload.dto.UserDTO;
import com.baldev.airline.common.payload.response.AuthResponse;

public interface AuthService {

    AuthResponse login(String email, String password);
    AuthResponse signup(UserDTO req);
}