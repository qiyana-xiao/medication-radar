package com.medicationradar.controller;

import com.medicationradar.common.ApiException;
import com.medicationradar.security.AuthUser;
import com.medicationradar.service.AdminAuditService;
import com.medicationradar.service.AiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 服务密钥管理（仅管理员）：密钥以 AES-GCM 密文保存到 app_settings 表，
 * 界面保存后立即生效，无需重启；删除后回退到 .env 中配置的密钥（如有）。
 */
@RestController
@RequestMapping("/api/admin/ai-key")
public class AiKeyController {

    private final AiKeyService aiKeyService;
    private final AdminAuditService auditService;

    public AiKeyController(AiKeyService aiKeyService, AdminAuditService auditService) {
        this.aiKeyService = aiKeyService;
        this.auditService = auditService;
    }

    @GetMapping
    public Map<String, Object> status() {
        return Map.of(
                "configured", aiKeyService.configured(),
                "masked", aiKeyService.maskedKey() == null ? "" : aiKeyService.maskedKey(),
                "source", aiKeyService.source());
    }

    @PutMapping
    public Map<String, Object> save(@RequestBody Map<String, String> body,
                                    @AuthenticationPrincipal AuthUser admin,
                                    HttpServletRequest request) {
        String key = body.get("key");
        if (key == null || key.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密钥不能为空");
        }
        aiKeyService.saveKey(key);
        auditService.record(admin, "UPDATE", "ai_key", null,
                "管理员通过界面更新 AI 服务密钥（已加密保存）", request);
        return Map.of(
                "configured", aiKeyService.configured(),
                "masked", aiKeyService.maskedKey() == null ? "" : aiKeyService.maskedKey(),
                "source", aiKeyService.source());
    }

    @DeleteMapping
    public Map<String, Object> clear(@AuthenticationPrincipal AuthUser admin,
                                     HttpServletRequest request) {
        aiKeyService.clearKey();
        auditService.record(admin, "DELETE", "ai_key", null,
                "管理员清除界面配置的 AI 服务密钥", request);
        return Map.of(
                "configured", aiKeyService.configured(),
                "masked", aiKeyService.maskedKey() == null ? "" : aiKeyService.maskedKey(),
                "source", aiKeyService.source());
    }
}
