package com.baldev.airline.user.service.impl;

import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.UnauthorizedException;
import com.baldev.airline.common.payload.request.LoginRequest;
import com.baldev.airline.common.payload.request.SignupRequest;
import com.baldev.airline.common.payload.response.AuthResponse;
import com.baldev.airline.user.config.JwtProvider;
import com.baldev.airline.user.mapper.UserMapper;
import com.baldev.airline.user.model.User;
import com.baldev.airline.user.repository.UserRepository;
import com.baldev.airline.user.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final Set<UserRole> SELF_REGISTRABLE = Set.of(UserRole.ROLE_USER, UserRole.ROLE_AIRLINE_OWNER);
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final Clock clock;
    /** Hash checked when the email is unknown, so response time does not reveal whether an account exists. */
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider,
                           Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-equalization");
    }

    @Override
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalize(request.getEmail());
        UserRole role = request.getRole() != null ? request.getRole() : UserRole.ROLE_USER;
        if (!SELF_REGISTRABLE.contains(role)) {
            throw new BusinessRuleException("ROLE_NOT_ALLOWED", "This role cannot be self-registered");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }

        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(role)
                .fullName(request.getFullName().trim())
                .lastLogin(LocalDateTime.now(clock))
                .build());
        log.info("User registered id={} role={}", user.getId(), role);

        return authResponse(user, "Welcome " + user.getFullName(), "Registered Successfully!");
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<User> found = userRepository.findByEmail(normalize(request.getEmail()));
        String hash = found.map(User::getPassword).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), hash);

        if (found.isEmpty() || !passwordMatches || !found.get().isEnabled()) {
            log.info("Failed login attempt");
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        User user = found.get();
        user.setLastLogin(LocalDateTime.now(clock));
        return authResponse(user, "Welcome Back " + user.getFullName(), "Login Successfully!");
    }

    private AuthResponse authResponse(User user, String title, String message) {
        AuthResponse response = new AuthResponse();
        response.setJwt(jwtProvider.generateToken(user));
        response.setUser(UserMapper.toDTO(user));
        response.setTitle(title);
        response.setMessage(message);
        return response;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
