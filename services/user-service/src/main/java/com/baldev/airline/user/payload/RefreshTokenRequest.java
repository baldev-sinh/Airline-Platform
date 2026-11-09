package com.baldev.airline.user.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Body of {@code POST /auth/refresh} and {@code POST /auth/logout}. */
@Data
@NoArgsConstructor
public class RefreshTokenRequest {

    @NotBlank
    @Size(max = 128)
    @ToString.Exclude
    private String refreshToken;

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
