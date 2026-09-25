package com.medicationradar.controller;

import com.medicationradar.service.AiKeyService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final String model;
    private final AiKeyService aiKeyService;
    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    public HealthController(@Value("${app.deepseek.model}") String model,
                            AiKeyService aiKeyService,
                            JdbcTemplate jdbcTemplate,
                            RedisConnectionFactory redisConnectionFactory) {
        this.model = model;
        this.aiKeyService = aiKeyService;
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping
    public Map<String, Object> health() {
        return Map.of("ok", true, "model", model, "keyConfigured", aiKeyService.configured());
    }

    /** 就绪检查：MySQL 必须可用；Redis 未装/未启动不阻塞（限流/令牌注销自动降级） */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        boolean mysql = checkMySql();
        Map<String, Object> result = Map.of("ok", mysql, "mysql", mysql,
                "redis", checkRedis() ? "UP" : "DEGRADED",
                "keyConfigured", aiKeyService.configured());
        return ResponseEntity.status(mysql ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }

    private boolean checkMySql() {
        try {
            return Integer.valueOf(1).equals(jdbcTemplate.queryForObject("SELECT 1", Integer.class));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean checkRedis() {
        try (var connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
