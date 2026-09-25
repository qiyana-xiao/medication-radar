package com.medicationradar.security;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenRevocationService {
    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);
    private final StringRedisTemplate redisTemplate;

    public TokenRevocationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null || expiresAt == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(tokenId), "1", ttl);
        } catch (RuntimeException exception) {
            log.warn("Redis unavailable while revoking JWT");
        }
    }

    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(tokenId)));
        } catch (RuntimeException exception) {
            log.warn("Redis unavailable while checking JWT revocation");
            return false;
        }
    }

    private String key(String tokenId) {
        return "medication-radar:jwt:revoked:" + tokenId;
    }
}
