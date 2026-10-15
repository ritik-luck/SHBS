package com.selfhealing.backend.service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RateLimitSyncService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitSyncService.class);
    private static final String RATE_LIMIT_FLAG = "rate_limit:tightened";

    private final StringRedisTemplate redis;
    private final RateLimiterRegistry rateLimiterRegistry;
    private boolean currentlyTightened = false;

    // Normal limit: 5 requests per period (from properties)
    private static final int NORMAL_LIMIT = 5;
    // Tightened limit: 1 request per period
    private static final int TIGHTENED_LIMIT = 1;

    public RateLimitSyncService(StringRedisTemplate redis, RateLimiterRegistry rateLimiterRegistry) {
        this.redis = redis;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Scheduled(fixedRate = 5000)
    public void syncRateLimits() {
        try {
            boolean shouldBeTightened = Boolean.TRUE.equals(redis.hasKey(RATE_LIMIT_FLAG));

            if (shouldBeTightened && !currentlyTightened) {
                log.warn("Redis flag 'rate_limit:tightened' detected. Dynamically tightening local API rate limit to {} req/period", TIGHTENED_LIMIT);
                RateLimiter apiLimiter = rateLimiterRegistry.rateLimiter("apiLimiter");
                apiLimiter.changeLimitForPeriod(TIGHTENED_LIMIT);
                currentlyTightened = true;
            } else if (!shouldBeTightened && currentlyTightened) {
                log.info("Redis flag 'rate_limit:tightened' expired. Restoring local API rate limit to {} req/period", NORMAL_LIMIT);
                RateLimiter apiLimiter = rateLimiterRegistry.rateLimiter("apiLimiter");
                apiLimiter.changeLimitForPeriod(NORMAL_LIMIT);
                currentlyTightened = false;
            }
        } catch (Exception e) {
            log.warn("Failed to sync dynamic rate limits from Redis: {}", e.getMessage());
        }
    }
}
