package com.medicationradar.service;

import com.medicationradar.entity.AdminOperationLog;
import com.medicationradar.mapper.AdminOperationLogMapper;
import com.medicationradar.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {
    private final AdminOperationLogMapper mapper;

    public AdminAuditService(AdminOperationLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(AuthUser admin, String operation, String targetType, Object targetId,
                       String summary, HttpServletRequest request) {
        record(admin, operation, targetType, targetId, summary, null, null, request);
    }

    public void record(AuthUser admin, String operation, String targetType, Object targetId,
                       String summary, String beforeValue, String afterValue, HttpServletRequest request) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminUserId(admin.id());
        log.setOperation(operation);
        log.setTargetType(targetType);
        log.setTargetId(targetId == null ? null : String.valueOf(targetId));
        log.setSummary(clip(summary, 500));
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        log.setIpAddress(clientIp(request));
        mapper.insert(log);
    }

    private String clip(String value, int maximum) {
        return value == null ? null : value.substring(0, Math.min(value.length(), maximum));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
    }
}
