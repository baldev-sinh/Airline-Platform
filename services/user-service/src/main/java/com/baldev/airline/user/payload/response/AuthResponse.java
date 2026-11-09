package com.baldev.airline.user.payload.response;

import com.baldev.airline.user.payload.dto.UserDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Result of signup, login and refresh. {@code jwt} is the short-lived access token (send it as
 * {@code Authorization: Bearer}); {@code refreshToken} is single-use: exchange it at {@code POST /auth/refresh} for
 * a new pair before {@code accessTokenExpiresAt}, and keep only the newest one.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    private String jwt;
    private String message;
    private String title;
    private UserDTO user;

    private String tokenType = "Bearer";
    private Instant accessTokenExpiresAt;
    private String refreshToken;
    private Instant refreshTokenExpiresAt;
}
