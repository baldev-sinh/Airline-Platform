package com.baldev.airline.user.service;

import com.baldev.airline.user.payload.request.LoginRequest;
import com.baldev.airline.user.payload.request.SignupRequest;
import com.baldev.airline.user.payload.response.AuthResponse;
public interface AuthService {

    AuthResponse login(LoginRequest request, String userAgent);

    AuthResponse signup(SignupRequest request, String userAgent);

    /** Exchanges a refresh token for a new access token and the next refresh token (rotation). */
    AuthResponse refresh(String refreshToken, String userAgent);

    /** Ends one session: revokes the refresh token's family and, if given, the presented access token. */
    void logout(String refreshToken, String bearerToken);

    /** Ends every session of the user, on every device. */
    void logoutEverywhere(Long userId);
}
