package com.medicationradar.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean allow(String scope, String identity, int maximum, Duration window) {
        String key = "medication-radar:rate:" + scope + ":" + identity;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, window);
            }
            return count == null || count <= maximum;
        } catch (RuntimeException exception) {
            log.warn("Redis rate limit unavailable; request allowed for scope={}", scope);
            return true;
        }
    }
}
