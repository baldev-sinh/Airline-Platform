package com.baldev.service;

import com.baldev.payload.dto.UserDTO;
import com.baldev.payload.response.AuthResponse;

public interface AuthService {

    AuthResponse login(String email, String password) throws Exception;
    AuthResponse signup(UserDTO req) throws Exception;
}