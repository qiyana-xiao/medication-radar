package com.medicationradar.controller;

import com.medicationradar.common.ApiException;
import com.medicationradar.security.AuthUser;
import com.medicationradar.service.AnalysisService;
import com.medicationradar.service.RateLimitService;
import com.medicationradar.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyze")
public class AnalysisController {
    private final AnalysisService analysisService;
    private final ReportService reportService;
    private final RateLimitService rateLimitService;

    public AnalysisController(AnalysisService analysisService, ReportService reportService,
                              RateLimitService rateLimitService) {
        this.analysisService = analysisService;
        this.reportService = reportService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public Map<String, Object> analyze(@RequestBody AnalysisInput input,
                                       @AuthenticationPrincipal AuthUser user,
                                       HttpServletRequest request) {
        if (user != null && !user.canAnalyze()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "管理员不能发起用药分析");
        }
        if (!rateLimitService.allow("analyze", clientIp(request), 6, Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
        List<Map<String, Object>> baseMeds = input.baseMedications() == null ? List.of() : input.baseMedications();
        List<Map<String, Object>> newMeds = input.newMedications() == null ? List.of() : input.newMedications();

        if (baseMeds.isEmpty() && newMeds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请至少填写一种药物");
        }
        int total = baseMeds.size() + newMeds.size();
        if (total > 15) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "单次分析最多支持 15 种药物");
        }
        validateMedNames(baseMeds, "长期用药");
        validateMedNames(newMeds, "新增用药");

        Map<String, Object> patient = input.patient() == null ? Map.of() : input.patient();
        List<Map<String, Object>> allMeds = new java.util.ArrayList<>();
        baseMeds.stream().map(m -> tagged(m, "base")).forEach(allMeds::add);
        newMeds.stream().map(m -> tagged(m, "new")).forEach(allMeds::add);

        try {
            Map<String, Object> report = analysisService.analyze(patient, baseMeds, newMeds);
            Long reportId = user == null ? null : reportService.save(user.id(), patient, allMeds, report);
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("reportId", reportId);
            response.put("saved", user != null);
            response.put("report", report);
            return response;
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "报告生成失败：" + safeMessage(exception));
        }
    }

    private void validateMedNames(List<Map<String, Object>> meds, String groupLabel) {
        for (Map<String, Object> medication : meds) {
            Object name = medication == null ? null : medication.get("name");
            if (name == null || name.toString().trim().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, groupLabel + "中存在未填写药名的条目");
            }
        }
    }

    private Map<String, Object> tagged(Map<String, Object> med, String group) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>(med);
        copy.put("group", group);
        return copy;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "外部模型服务异常" : message;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
    }

    public record AnalysisInput(Map<String, Object> patient,
                                List<Map<String, Object>> baseMedications,
                                List<Map<String, Object>> newMedications) {
    }
}
