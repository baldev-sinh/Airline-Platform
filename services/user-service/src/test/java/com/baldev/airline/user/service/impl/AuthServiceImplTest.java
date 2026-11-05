package com.baldev.airline.user.service.impl;

import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.UnauthorizedException;
import com.baldev.airline.common.payload.request.LoginRequest;
import com.baldev.airline.common.payload.request.SignupRequest;
import com.baldev.airline.common.payload.response.AuthResponse;
import com.baldev.airline.user.config.JwtProvider;
import com.baldev.airline.user.config.SecurityProperties;
import com.baldev.airline.user.model.User;
import com.baldev.airline.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Security(new SecurityProperties.Jwt(
                        "test-secret-test-secret-test-secret-0123456789", "airline-user-service", Duration.ofHours(1))),
                null);
        Clock clock = Clock.systemUTC();
        authService = new AuthServiceImpl(userRepository, encoder, new JwtProvider(properties, clock), clock);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });
    }

    @Test
    void cannotSelfRegisterAsSystemAdmin() {
        assertThatThrownBy(() -> authService.signup(signup("a@b.com", UserRole.ROLE_SYSTEM_ADMIN)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void signupDefaultsToCustomerRoleAndNormalizesEmail() {
        AuthResponse response = authService.signup(signup("  Mixed@Case.COM ", null));

        assertThat(response.getUser().getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(response.getUser().getEmail()).isEqualTo("mixed@case.com");
        assertThat(response.getJwt()).isNotBlank();
    }

    @Test
    void duplicateEmailIsAConflict() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
        assertThatThrownBy(() -> authService.signup(signup("a@b.com", null))).isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownEmailAndWrongPasswordFailIdentically() {
        when(userRepository.findByEmail("known@b.com")).thenReturn(Optional.of(User.builder()
                .id(1L).email("known@b.com").password(encoder.encode("correct-password")).role(UserRole.ROLE_USER)
                .fullName("K").build()));

        Throwable unknown = catchLogin("unknown@b.com", "whatever-password");
        Throwable wrong = catchLogin("known@b.com", "wrong-password");

        assertThat(unknown).isInstanceOf(UnauthorizedException.class);
        assertThat(wrong).isInstanceOf(UnauthorizedException.class).hasMessage(unknown.getMessage());
    }

    @Test
    void disabledAccountCannotLogIn() {
        when(userRepository.findByEmail("off@b.com")).thenReturn(Optional.of(User.builder()
                .id(1L).email("off@b.com").password(encoder.encode("correct-password")).role(UserRole.ROLE_USER)
                .fullName("K").enabled(false).build()));
        assertThat(catchLogin("off@b.com", "correct-password")).isInstanceOf(UnauthorizedException.class);
    }

    private Throwable catchLogin(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        try {
            authService.login(request);
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private static SignupRequest signup(String email, UserRole role) {
        return SignupRequest.builder().email(email).password("long-enough-password").fullName("Test User").role(role).build();
    }
}
