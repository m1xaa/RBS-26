package com.tim8.oblak.auth.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String username;
    private String role;
    private long expiresInMs;
}
