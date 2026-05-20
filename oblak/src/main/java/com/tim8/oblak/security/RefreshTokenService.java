package com.tim8.oblak.security;

import com.tim8.oblak.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final long refreshExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs  // 7 dana
    ) {
        this.repository = repository;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public RefreshToken create(User user) {
        RefreshToken rt = new RefreshToken();
        rt.setToken(UUID.randomUUID().toString());
        rt.setUser(user);
        rt.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));
        rt.setRevoked(false);
        return repository.save(rt);
    }

    public RefreshToken verifyAndGet(String token) {
        RefreshToken rt = repository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Refresh token ne postoji"));

        if (rt.isRevoked()) {
            throw new RuntimeException("Refresh token je opozvan");
        }
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            repository.delete(rt);
            throw new RuntimeException("Refresh token je istekao");
        }
        return rt;
    }

    public void revoke(String token) {
        repository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            repository.save(rt);
        });
    }
}