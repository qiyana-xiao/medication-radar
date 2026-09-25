package com.medicationradar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicationradar.common.ApiException;
import com.medicationradar.entity.Report;
import com.medicationradar.mapper.ReportMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private final ReportMapper reportMapper;
    private final ObjectMapper objectMapper;

    public ReportService(ReportMapper reportMapper, ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> list(Long userId, int page, int pageSize, String filter, String query) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(pageSize, 50));
        String safeFilter = List.of("all", "high", "favorite").contains(filter) ? filter : "all";
        String keyword = query == null ? "" : query.trim();
        long total = reportMapper.countOwned(userId, safeFilter, keyword);
        List<Map<String, Object>> items = reportMapper.selectOwnedPage(userId, safeFilter, keyword,
                        safeSize, (safePage - 1) * safeSize)
                .stream().map(this::toSummary).toList();
        return Map.of("total", total, "page", safePage, "pageSize", safeSize, "items", items);
    }

    public Map<String, Object> get(Long userId, Long id) {
        Report row = findOwned(userId, id);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("patient", parseObject(row.getPatient()));
        result.put("medications", parseList(row.getMedications()));
        result.put("report", parseObject(row.getReport()));
        result.put("riskLevel", row.getRiskLevel());
        result.put("score", row.getScore());
        result.put("favorite", Boolean.TRUE.equals(row.getFavorite()));
        result.put("knowledgeVersion", row.getKnowledgeVersion() == null ? null : parseList(row.getKnowledgeVersion()));
        result.put("createdAt", row.getCreatedAt());
        return result;
    }

    public boolean toggleFavorite(Long userId, Long id) {
        Report row = findOwned(userId, id);
        boolean next = !Boolean.TRUE.equals(row.getFavorite());
        Report update = new Report();
        update.setId(row.getId());
        update.setFavorite(next);
        reportMapper.updateById(update);
        return next;
    }

    public void delete(Long userId, Long id) {
        int changed = reportMapper.delete(new LambdaQueryWrapper<Report>()
                .eq(Report::getId, id).eq(Report::getUserId, userId));
        if (changed == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "报告不存在");
        }
    }

    public Long save(Long userId, Object patient, Object medications, Map<String, Object> generated) {
        Map<String, Object> overall = generated.get("overall") instanceof Map<?, ?> value
                ? castMap(value) : Map.of();
        String risk = String.valueOf(overall.getOrDefault("riskLevel", "中"));
        if (!List.of("低", "中", "高", "极高", "未收录").contains(risk)) {
            risk = "中";
        }
        int score = overall.get("score") instanceof Number number ? number.intValue() : 0;
        score = Math.max(0, Math.min(score, 100));

        String knowledgeVersionJson = null;
        Object metaObj = generated.get("_meta");
        if (metaObj instanceof Map<?, ?> meta) {
            Object kv = meta.get("knowledgeVersion");
            if (kv != null) {
                knowledgeVersionJson = writeJson(kv);
            }
        }

        Report report = new Report();
        report.setUserId(userId);
        report.setPatient(writeJson(patient));
        report.setMedications(writeJson(medications));
        report.setReport(writeJson(generated));
        report.setRiskLevel(risk);
        report.setScore(score);
        report.setFavorite(false);
        report.setKnowledgeVersion(knowledgeVersionJson);
        reportMapper.insert(report);
        return report.getId();
    }

    public Map<String, Object> stats(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>(reportMapper.selectTotals(userId));
        result.put("riskDist", reportMapper.selectRiskDistribution(userId));
        result.put("topDrugs", reportMapper.selectTopDrugs(userId));
        result.put("trend", reportMapper.selectTrend(userId));
        return result;
    }

    public Map<String, Object> globalStats() {
        Map<String, Object> result = new LinkedHashMap<>(reportMapper.selectGlobalTotals());
        result.put("riskDist", reportMapper.selectGlobalRiskDistribution());
        result.put("topDrugs", reportMapper.selectGlobalTopDrugs());
        result.put("trend", reportMapper.selectGlobalTrend());
        return result;
    }

    private Report findOwned(Long userId, Long id) {
        Report row = reportMapper.selectOne(new LambdaQueryWrapper<Report>()
                .eq(Report::getId, id).eq(Report::getUserId, userId));
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "报告不存在");
        }
        return row;
    }

    private Map<String, Object> toSummary(Report row) {
        List<Map<String, Object>> medications = parseList(row.getMedications());
        return Map.of("id", row.getId(), "patient", parseObject(row.getPatient()),
                "medications", medications.stream().map(item -> String.valueOf(item.get("name"))).toList(),
                "riskLevel", row.getRiskLevel(), "score", row.getScore(),
                "favorite", Boolean.TRUE.equals(row.getFavorite()), "createdAt", row.getCreatedAt());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "报告数据格式不正确");
        }
    }

    private Map<String, Object> parseObject(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored report JSON is invalid", exception);
        }
    }

    private List<Map<String, Object>> parseList(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored medication JSON is invalid", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }
}
