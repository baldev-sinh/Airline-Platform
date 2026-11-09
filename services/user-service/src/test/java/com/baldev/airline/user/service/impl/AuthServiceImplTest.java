package com.baldev.airline.user.service.impl;

import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.UnauthorizedException;
import com.baldev.airline.user.payload.request.LoginRequest;
import com.baldev.airline.user.payload.request.SignupRequest;
import com.baldev.airline.user.payload.response.AuthResponse;
import com.baldev.airline.common.event.UserRegisteredEvent;
import com.baldev.airline.common.security.resource.AccessTokenDecoders;
import com.baldev.airline.common.security.resource.AccessTokenValidators;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import com.baldev.airline.common.security.TokenRevocations;
import com.baldev.airline.user.config.JwtProvider;
import com.baldev.airline.user.config.SecurityProperties;
import com.baldev.airline.user.config.SigningKeys;
import com.baldev.airline.user.config.UserServiceConfig;
import com.baldev.airline.user.service.impl.RefreshTokenService.Rotation;
import com.baldev.airline.user.model.User;
import com.baldev.airline.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private AuthServiceImpl authService;
    private JwtDecoder verifier;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Security(
                        new SecurityProperties.Jwt("airline-user-service", "airline-api"),
                        new SecurityProperties.Signing(null, null, List.of(), true),
                        new SecurityProperties.Tokens(Duration.ofMinutes(15), Duration.ofDays(14))),
                null);
        Clock clock = Clock.systemUTC();
        SigningKeys keys = new SigningKeys(properties);
        verifier = AccessTokenDecoders.fixed(keys.verificationKeys(),
                AccessTokenValidators.of("airline-user-service", "airline-api", TokenRevocations.NONE, clock));
        when(refreshTokens.issue(anyLong(), any())).thenAnswer(inv ->
                new Rotation(inv.getArgument(0), "refresh-" + inv.getArgument(0), Instant.now().plusSeconds(3600)));
        authService = new AuthServiceImpl(userRepository, encoder, new JwtProvider(keys, properties, clock),
                refreshTokens, verifier, TokenRevocations.NONE, events, clock);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });
    }

    @Test
    void cannotSelfRegisterAsSystemAdmin() {
        assertThatThrownBy(() -> authService.signup(signup("a@b.com", UserRole.ROLE_SYSTEM_ADMIN), "test"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void signupDefaultsToCustomerRoleAndNormalizesEmail() {
        AuthResponse response = authService.signup(signup("  Mixed@Case.COM ", null), "test");

        assertThat(response.getUser().getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(response.getUser().getEmail()).isEqualTo("mixed@case.com");
        assertThat(response.getJwt()).isNotBlank();
        assertThat(response.getRefreshToken()).isEqualTo("refresh-7");
        verify(events).publishEvent(isA(UserRegisteredEvent.class));
    }

    @Test
    void issuedTokensVerifyAgainstThePublishedKeys() throws Exception {
        AuthResponse response = authService.signup(signup("v@b.com", UserRole.ROLE_AIRLINE_OWNER), "test");

        Jwt token = verifier.decode(response.getJwt());

        assertThat(token.getSubject()).isEqualTo("7");
        assertThat(token.getClaimAsStringList("roles")).containsExactly("ROLE_AIRLINE_OWNER");
        assertThat(token.getId()).isNotBlank();
        assertThat(response.getAccessTokenExpiresAt()).isAfter(Instant.now().plusSeconds(14 * 60));
    }

    @Test
    void duplicateEmailIsAConflict() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
        assertThatThrownBy(() -> authService.signup(signup("a@b.com", null), "test")).isInstanceOf(ConflictException.class);
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
            authService.login(request, "test");
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private static SignupRequest signup(String email, UserRole role) {
        return SignupRequest.builder().email(email).password("long-enough-password").fullName("Test User").role(role).build();
    }
}
