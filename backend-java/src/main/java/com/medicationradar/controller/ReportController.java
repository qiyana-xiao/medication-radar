package com.medicationradar.controller;

import com.medicationradar.common.ApiException;
import com.medicationradar.security.AuthUser;
import com.medicationradar.service.ReportService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AuthUser user,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int pageSize,
                                    @RequestParam(defaultValue = "all") String filter,
                                    @RequestParam(defaultValue = "") String q) {
        ensureNotAdmin(user);
        return service.list(user.id(), page, pageSize, filter, q);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        ensureNotAdmin(user);
        return service.get(user.id(), id);
    }

    @PostMapping("/{id}/favorite")
    public Map<String, Object> favorite(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        ensureNotAdmin(user);
        return Map.of("favorite", service.toggleFavorite(user.id(), id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        ensureNotAdmin(user);
        service.delete(user.id(), id);
        return Map.of("ok", true);
    }

    private void ensureNotAdmin(AuthUser user) {
        if (user != null && user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "管理员不能查看患者报告");
        }
    }
}
