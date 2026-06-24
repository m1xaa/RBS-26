package com.tim8.oblak.security.ratelimit;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TokenBucketRateLimiter {

    private static final Duration BUCKET_IDLE_TTL = Duration.ofMinutes(10);
    private static final long CLEANUP_INTERVAL_MILLIS = Duration.ofMinutes(1).toMillis();

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMillis;

    @Autowired
    public TokenBucketRateLimiter(RateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    TokenBucketRateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.lastCleanupMillis = new AtomicLong(clock.millis());
    }

    public TokenBucket.ConsumptionResult tryConsume(String key) {
        cleanupIfNeeded();
        TokenBucket bucket = buckets.computeIfAbsent(key, ignored -> new TokenBucket(
                properties.getCapacity(),
                properties.getRefillTokens(),
                properties.getRefillPeriod(),
                clock
        ));
        return bucket.tryConsume();
    }

    private void cleanupIfNeeded() {
        long now = clock.millis();
        long lastCleanup = lastCleanupMillis.get();
        if (now - lastCleanup < CLEANUP_INTERVAL_MILLIS
                || !lastCleanupMillis.compareAndSet(lastCleanup, now)) {
            return;
        }

        buckets.entrySet().removeIf(entry -> entry.getValue().isIdleLongerThan(BUCKET_IDLE_TTL));
    }
}
