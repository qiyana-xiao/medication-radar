package com.medicationradar.config;

import java.time.Duration;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
public class RedisConfig {
    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        RedisCacheConfiguration knowledge = cacheConfiguration(Duration.ofMinutes(30));
        RedisCacheConfiguration search = cacheConfiguration(Duration.ofMinutes(5));
        return builder -> builder
                .withCacheConfiguration("medicationDetails", knowledge)
                .withCacheConfiguration("medicationSearch", search)
                .withCacheConfiguration("knowledgeStats", search)
                .withCacheConfiguration("interactions", knowledge);
    }

    @Bean
    CacheErrorHandler cacheErrorHandler() {
        return new SimpleCacheErrorHandler() {
            private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisConfig.class);

            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache,
                                            Object key) {
                log.warn("Cache read failed for cache={}; falling back to MySQL", cache.getName());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache,
                                            Object key, Object value) {
                log.warn("Cache write failed for cache={}; response remains valid", cache.getName());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache,
                                              Object key) {
                log.warn("Cache eviction failed for cache={}", cache.getName());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                log.warn("Cache clear failed for cache={}", cache.getName());
            }
        };
    }

    private RedisCacheConfiguration cacheConfiguration(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()));
    }
}
