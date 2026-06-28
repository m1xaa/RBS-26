package com.tim8.oblak.auth;

import com.tim8.oblak.audit.AuditAction;
import com.tim8.oblak.audit.AuditEvent;
import com.tim8.oblak.audit.AuditOutcome;
import com.tim8.oblak.audit.AuditService;
import com.tim8.oblak.audit.IpResolver;
import com.tim8.oblak.auth.request.*;
import com.tim8.oblak.auth.response.*;
import com.tim8.oblak.security.*;
import com.tim8.oblak.user.Role;
import com.tim8.oblak.user.User;
import com.tim8.oblak.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordPolicyService passwordPolicy;
    private final AuditService auditService;
    private final IpResolver ipResolver;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       PasswordPolicyService passwordPolicy,
                       AuditService auditService,
                       IpResolver ipResolver) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
        this.ipResolver = ipResolver;
    }

    public AuthResponse register(RegisterRequest req) {
        log.info("Registration attempt for username='{}'", req.username());

        passwordPolicy.validate(req.password(), req.username());

        if (userRepository.findByUsername(req.username()).isPresent()) {
            log.warn("Registration failed — username already taken: '{}'", req.username());
            auditService.record(AuditEvent.builder(AuditAction.USER_REGISTER, AuditOutcome.FAILURE)
                    .actor(req.username())
                    .detail("Username already taken")
                    .ip(ipResolver.resolve())
                    .build());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Korisnicko ime je vec zauzeto");
        }

        User user = new User();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        userRepository.save(user);

        auditService.record(AuditEvent.builder(AuditAction.USER_REGISTER, AuditOutcome.SUCCESS)
                .actor(req.username())
                .ip(ipResolver.resolve())
                .build());

        log.info("User registered successfully: username='{}'", req.username());
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        log.info("Login attempt for username='{}'", req.username());

        User user = userRepository.findByUsername(req.username()).orElse(null);

        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            log.warn("Login failed for username='{}'", req.username());
            // Note: actor is set to the attempted username even if it doesn't exist —
            // useful for detecting credential stuffing. Do NOT put "user not found" vs
            // "wrong password" in the detail to avoid user enumeration via audit log leaks.
            auditService.record(AuditEvent.builder(AuditAction.USER_LOGIN, AuditOutcome.FAILURE)
                    .actor(req.username())
                    .detail("Invalid credentials")
                    .ip(ipResolver.resolve())
                    .build());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Neispravno korisnicko ime ili lozinka");
        }

        auditService.record(AuditEvent.builder(AuditAction.USER_LOGIN, AuditOutcome.SUCCESS)
                .actor(user.getUsername())
                .ip(ipResolver.resolve())
                .build());

        log.info("Login successful for username='{}'", req.username());
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest req) {
        log.debug("Token refresh requested");

        RefreshToken rt;
        try {
            rt = refreshTokenService.verifyAndGet(req.refreshToken());
        } catch (RuntimeException e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            auditService.record(AuditEvent.builder(AuditAction.TOKEN_REFRESH, AuditOutcome.FAILURE)
                    .detail(e.getMessage())
                    .ip(ipResolver.resolve())
                    .build());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }

        refreshTokenService.revoke(rt.getToken());

        auditService.record(AuditEvent.builder(AuditAction.TOKEN_REFRESH, AuditOutcome.SUCCESS)
                .actor(rt.getUser().getUsername())
                .ip(ipResolver.resolve())
                .build());

        log.debug("Refresh token rotated for username='{}'", rt.getUser().getUsername());
        return buildAuthResponse(rt.getUser());
    }

    public void logout(RefreshRequest req) {
        log.debug("Logout requested — revoking refresh token");
        refreshTokenService.revoke(req.refreshToken());

        auditService.record(AuditEvent.builder(AuditAction.USER_LOGOUT, AuditOutcome.SUCCESS)
                .ip(ipResolver.resolve())
                .build());

        log.info("Refresh token revoked successfully");
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user.getUsername(), String.valueOf(user.getRole()));
        RefreshToken refreshToken = refreshTokenService.create(user);
        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                user.getUsername(),
                jwtService.getExpirationMs()
        );
    }
}