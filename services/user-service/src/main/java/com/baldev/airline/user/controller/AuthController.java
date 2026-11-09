package com.baldev.airline.user.controller;

import com.baldev.airline.user.payload.request.LoginRequest;
import com.baldev.airline.user.payload.request.SignupRequest;
import com.baldev.airline.user.payload.response.AuthResponse;
import com.baldev.airline.user.payload.RefreshTokenRequest;
import com.baldev.airline.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public authentication endpoints (rate limited at the gateway). */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER = "Bearer ";

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request,
                                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request, userAgent));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.login(request, userAgent);
    }

    /** Rotates the refresh token: the presented one is spent, use the one in the response from now on. */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request,
                                @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.refresh(request.getRefreshToken(), userAgent);
    }

    /** Ends this session. Always 204, so it cannot be used to probe which tokens exist. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request,
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String bearer = authorization != null && authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())
                ? authorization.substring(BEARER.length()).trim() : null;
        authService.logout(request != null ? request.getRefreshToken() : null, bearer);
        return ResponseEntity.noContent().build();
    }
}
