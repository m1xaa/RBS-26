package com.tim8.oblak.auth;

import com.tim8.oblak.auth.request.*;
import com.tim8.oblak.auth.response.*;
import com.tim8.oblak.security.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('USER')")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAnyRole('USER')")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest req) {
        authService.logout(req);
        return ResponseEntity.ok().build();
    }
}