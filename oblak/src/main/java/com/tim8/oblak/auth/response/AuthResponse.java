package com.tim8.oblak.auth.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String username,
        long accessExpirationMs
) {}