package com.medicationradar.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class HttpClientConfig {
    @Bean
    RestClientCustomizer deepSeekTimeoutCustomizer(
            @Value("${app.deepseek.timeout-seconds}") long timeoutSeconds) {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 10)));
            factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
            builder.requestFactory(factory);
        };
    }
}
