package com.tim8.oblak.auth;


import com.tim8.oblak.auth.request.*;
import com.tim8.oblak.auth.response.*;
import com.tim8.oblak.security.*;
import com.tim8.oblak.user.Role;
import com.tim8.oblak.user.User;
import com.tim8.oblak.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
                       PasswordPolicyService passwordPolicy) {  // NOVO
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordPolicy = passwordPolicy;
    }

    public AuthResponse register(RegisterRequest req) {
        passwordPolicy.validate(req.password(), req.username());

        if (userRepository.findByUsername(req.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Korisnicko ime je vec zauzeto");
        }

        User user = new User();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password()));
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Neispravno korisnicko ime ili lozinka"));

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Neispravno korisnicko ime ili lozinka");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest req) {
        RefreshToken rt;
        try {
            rt = refreshTokenService.verifyAndGet(req.refreshToken());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }

        refreshTokenService.revoke(rt.getToken());
        return buildAuthResponse(rt.getUser());
    }

    public void logout(RefreshRequest req) {
        refreshTokenService.revoke(req.refreshToken());
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