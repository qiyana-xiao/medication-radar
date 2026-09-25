package com.medicationradar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DeepSeekService {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiKeyService aiKeyService;
    private final String model;

    public DeepSeekService(RestClient.Builder builder, ObjectMapper objectMapper,
                           AiKeyService aiKeyService,
                           @Value("${app.deepseek.base-url}") String baseUrl,
                           @Value("${app.deepseek.model}") String model) {
        this.restClient = builder.baseUrl(baseUrl.replaceAll("/$", "")).build();
        this.objectMapper = objectMapper;
        this.aiKeyService = aiKeyService;
        this.model = model;
    }

    public Map<String, Object> generateJson(String systemPrompt, String userPrompt) {
        String apiKey = aiKeyService.effectiveKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "未配置 AI 密钥：请由管理员登录后在「知识库管理 → AI 服务设置」中填写 DeepSeek API Key");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        JsonNode response = restClient.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .body(body).retrieve().body(JsonNode.class);
        JsonNode contentNode = response == null ? null : response.at("/choices/0/message/content");
        if (contentNode == null || !contentNode.isTextual()) {
            throw new IllegalStateException("模型返回内容为空");
        }
        String content = stripMarkdownFence(contentNode.asText());
        try {
            return objectMapper.readValue(content, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模型未返回合法 JSON", exception);
        }
    }

    public String model() {
        return model;
    }

    private String stripMarkdownFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }
}
