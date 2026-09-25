package com.medicationradar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicationradar.common.ApiException;
import com.medicationradar.entity.Medication;
import com.medicationradar.security.AuthUser;
import com.medicationradar.service.AdminAuditService;
import com.medicationradar.service.MedicationService;
import com.medicationradar.service.MedicationService.MedicationInput;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medications")
public class MedicationController {
    private final MedicationService service;
    private final AdminAuditService auditService;
    private final ObjectMapper objectMapper;

    public MedicationController(MedicationService service, AdminAuditService auditService,
                                ObjectMapper objectMapper) {
        this.service = service;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam(defaultValue = "") String q,
                                            @RequestParam(defaultValue = "10") int limit) {
        return service.search(q, limit);
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return service.listAll();
    }

    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize,
                                    @RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "") String cat) {
        return service.page(page, pageSize, q, cat);
    }

    @GetMapping("/stats")
    public List<Map<String, Object>> stats() {
        return service.stats();
    }

    @GetMapping("/interactions")
    public List<Map<String, Object>> interactions() {
        return service.interactions();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> create(@RequestBody MedicationInput input,
                                      @AuthenticationPrincipal AuthUser admin, HttpServletRequest request) {
        Medication created = service.create(input);
        auditService.record(admin, "CREATE", "MEDICATION", created.getId(),
                "Created medication: " + created.getName(), null, toJson(input), request);
        return Map.of("id", created.getId(), "name", created.getName());
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable Long id, @RequestBody MedicationInput input,
                                      @AuthenticationPrincipal AuthUser admin, HttpServletRequest request) {
        Medication before = service.getById(id);
        if (before == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "药物不存在");
        }
        String beforeJson = toJson(before);
        Medication after = service.update(id, input);
        auditService.record(admin, "UPDATE", "MEDICATION", id,
                "Updated medication: " + input.name(), beforeJson, toJson(after), request);
        return Map.of("ok", true);
    }

    @PatchMapping("/{id}/disable")
    @Transactional
    public Map<String, Object> disable(@PathVariable Long id, @RequestBody Map<String, String> body,
                                       @AuthenticationPrincipal AuthUser admin, HttpServletRequest request) {
        Medication before = service.getById(id);
        if (before == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "药物不存在");
        }
        if (service.isReferencedByReport(before.getName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该药物已被历史报告引用，不能停用");
        }
        String reason = body.getOrDefault("reason", "");
        if (reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请填写停用原因");
        }
        service.disable(id, reason);
        auditService.record(admin, "DISABLE", "MEDICATION", id,
                "Disabled medication: " + before.getName(), toJson(before), null, request);
        return Map.of("ok", true);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id,
                                      @AuthenticationPrincipal AuthUser admin, HttpServletRequest request) {
        Medication before = service.getById(id);
        if (before == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "药物不存在");
        }
        if (service.isReferencedByReport(before.getName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该药物已被历史报告引用，不能删除");
        }
        service.disable(id, "管理员删除");
        auditService.record(admin, "DELETE", "MEDICATION", id,
                "Deleted medication: " + before.getName(), toJson(before), null, request);
        return Map.of("ok", true);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
