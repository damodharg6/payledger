package com.payledger.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payledger.common.security.JwtTokenService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for dashboard login, including the defence against user enumeration.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    @Mock
    DashboardUserRepository users;

    @Mock
    MerchantRepository merchants;

    @Mock
    PasswordEncoder passwords;

    @Mock
    JwtTokenService tokens;

    AuthService auth;
    Merchant merchant;
    DashboardUser user;

    @BeforeEach
    void setUp() {
        when(passwords.encode(anyString())).thenReturn("$2a$10$dummy");
        auth = new AuthService(users, merchants, passwords, tokens);
        merchant = new Merchant(UUID.randomUUID(), "Chai Point", "owner@chai.in", "h", null, NOW);
        user = new DashboardUser(UUID.randomUUID(), merchant.getId(), "owner@chai.in", "$2a$10$real",
                DashboardUser.Role.ADMIN, NOW);
    }

    @Test
    void correctPasswordReturnsBearerToken() {
        when(users.findByEmail("owner@chai.in")).thenReturn(Optional.of(user));
        when(passwords.matches("s3cret-password", "$2a$10$real")).thenReturn(true);
        when(merchants.findById(merchant.getId())).thenReturn(Optional.of(merchant));
        when(tokens.issue(user.getId(), merchant.getId(), "Chai Point", "owner@chai.in", "ADMIN")).thenReturn("jwt");
        when(tokens.ttlSeconds()).thenReturn(3600L);

        LoginResponse r = auth.login(new LoginRequest(" Owner@Chai.in ", "s3cret-password"));

        assertThat(r.accessToken()).isEqualTo("jwt");
        assertThat(r.tokenType()).isEqualTo("Bearer");
        assertThat(r.expiresInSeconds()).isEqualTo(3600);
        assertThat(r.merchantId()).isEqualTo(merchant.getId());
    }

    @Test
    void wrongPasswordIsRejected() {
        when(users.findByEmail("owner@chai.in")).thenReturn(Optional.of(user));
        when(passwords.matches("wrong", "$2a$10$real")).thenReturn(false);

        assertThatThrownBy(() -> auth.login(new LoginRequest("owner@chai.in", "wrong")))
                .isInstanceOf(BadCredentialsException.class).hasMessage(AuthService.INVALID);
        verify(tokens, never()).issue(any(), any(), any(), any(), any());
    }

    @Test
    void unknownEmailGetsTheSameErrorAndStillRunsBcrypt() {
        when(users.findByEmail("nobody@x.in")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login(new LoginRequest("nobody@x.in", "whatever")))
                .isInstanceOf(BadCredentialsException.class).hasMessage(AuthService.INVALID);
        verify(passwords).matches(eq("whatever"), eq("$2a$10$dummy"));
    }
}
