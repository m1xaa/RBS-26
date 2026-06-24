package com.tim8.oblak.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTests {

    private final MutableClock clock = new MutableClock();

    @Test
    void allowsRequestsUntilCapacityIsExhausted() {
        TokenBucket bucket = new TokenBucket(2, 1, Duration.ofSeconds(1), clock);

        assertThat(bucket.tryConsume().allowed()).isTrue();
        assertThat(bucket.tryConsume().allowed()).isTrue();

        TokenBucket.ConsumptionResult rejected = bucket.tryConsume();
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterMillis()).isEqualTo(1000);
    }

    @Test
    void refillsTokensAfterRefillPeriod() {
        TokenBucket bucket = new TokenBucket(1, 1, Duration.ofSeconds(1), clock);
        bucket.tryConsume();

        clock.advance(Duration.ofMillis(999));
        assertThat(bucket.tryConsume().allowed()).isFalse();

        clock.advance(Duration.ofMillis(1));
        assertThat(bucket.tryConsume().allowed()).isTrue();
    }

    private static class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-06-24T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
