package com.tim8.oblak.security.ratelimit;

import java.time.Clock;
import java.time.Duration;

public class TokenBucket {

    private final int capacity;
    private final int refillTokens;
    private final long refillPeriodMillis;
    private final Clock clock;

    private int tokens;
    private long lastRefillMillis;
    private long lastAccessMillis;

    public TokenBucket(int capacity, int refillTokens, Duration refillPeriod, Clock clock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Token bucket capacity must be greater than zero.");
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("Token bucket refill tokens must be greater than zero.");
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("Token bucket refill period must be positive.");
        }
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodMillis = refillPeriod.toMillis();
        this.clock = clock;
        this.tokens = capacity;
        this.lastRefillMillis = clock.millis();
        this.lastAccessMillis = lastRefillMillis;
    }

    public synchronized ConsumptionResult tryConsume() {
        refill();
        lastAccessMillis = clock.millis();

        if (tokens > 0) {
            tokens--;
            return new ConsumptionResult(true, tokens, 0);
        }

        return new ConsumptionResult(false, 0, millisUntilNextToken());
    }

    public synchronized boolean isIdleLongerThan(Duration maxIdleTime) {
        return clock.millis() - lastAccessMillis > maxIdleTime.toMillis();
    }

    private void refill() {
        long now = clock.millis();
        long elapsedMillis = now - lastRefillMillis;
        if (elapsedMillis < refillPeriodMillis) {
            return;
        }

        long periods = elapsedMillis / refillPeriodMillis;
        long tokensToAdd = periods * refillTokens;
        tokens = (int) Math.min(capacity, tokens + tokensToAdd);
        lastRefillMillis += periods * refillPeriodMillis;
    }

    private long millisUntilNextToken() {
        return Math.max(1, refillPeriodMillis - (clock.millis() - lastRefillMillis));
    }

    public record ConsumptionResult(boolean allowed, int remainingTokens, long retryAfterMillis) {
    }
}
