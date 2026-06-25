package com.tim8.oblak.auth;

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

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       PasswordPolicyService passwordPolicy) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordPolicy = passwordPolicy;
    }

    public AuthResponse register(RegisterRequest req) {
        log.info("Registration attempt for username='{}'", req.username());

        passwordPolicy.validate(req.password(), req.username());

        if (userRepository.findByUsername(req.username()).isPresent()) {
            log.warn("Registration failed — username already taken: '{}'", req.username());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Korisnicko ime je vec zauzeto");
        }

        User user = new User();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        userRepository.save(user);

        log.info("User registered successfully: username='{}'", req.username());
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        log.info("Login attempt for username='{}'", req.username());

        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> {
                    log.warn("Login failed — username not found: '{}'", req.username());
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Neispravno korisnicko ime ili lozinka");
                });

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            log.warn("Login failed — incorrect password for username='{}'", req.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Neispravno korisnicko ime ili lozinka");
        }

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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }

        refreshTokenService.revoke(rt.getToken());
        log.debug("Refresh token rotated for username='{}'", rt.getUser().getUsername());

        return buildAuthResponse(rt.getUser());
    }

    public void logout(RefreshRequest req) {
        log.debug("Logout requested — revoking refresh token");
        refreshTokenService.revoke(req.refreshToken());
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