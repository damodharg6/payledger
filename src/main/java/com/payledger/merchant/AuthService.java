package com.payledger.merchant;

import com.payledger.common.security.JwtTokenService;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard login: checks email + password and returns a JWT.
 * <p>
 * "Wrong email" and "wrong password" give the same error, and an unknown email still runs one
 * BCrypt check against a dummy hash. Otherwise an attacker could tell which emails exist by the
 * message or by how fast the answer comes back (user enumeration). Rate limiting of login
 * attempts is planned for v2.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final String INVALID = "Invalid email or password";

    private final DashboardUserRepository users;
    private final MerchantRepository merchants;
    private final PasswordEncoder passwords;
    private final JwtTokenService tokens;
    private final String dummyHash;

    public AuthService(DashboardUserRepository users, MerchantRepository merchants, PasswordEncoder passwords,
                       JwtTokenService tokens) {
        this.users = users;
        this.merchants = merchants;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode("timing-equaliser-not-a-real-password");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        var user = users.findByEmail(email);
        if (user.isEmpty()) {
            passwords.matches(request.password(), dummyHash);  // same work as a real check
            throw new BadCredentialsException(INVALID);
        }
        DashboardUser u = user.get();
        if (!passwords.matches(request.password(), u.getPasswordHash())) {
            log.info("Failed dashboard login for user {}", u.getId());
            throw new BadCredentialsException(INVALID);
        }
        Merchant merchant = merchants.findById(u.getMerchantId())
                .orElseThrow(() -> new IllegalStateException("Dashboard user " + u.getId() + " has no merchant"));
        String token = tokens.issue(u.getId(), merchant.getId(), merchant.getName(), u.getEmail(), u.getRole().name());
        return new LoginResponse(token, "Bearer", tokens.ttlSeconds(), merchant.getId(), merchant.getName(),
                u.getEmail(), u.getRole());
    }
}
