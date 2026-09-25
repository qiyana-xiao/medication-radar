package com.medicationradar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicationradar.common.ApiException;
import com.medicationradar.entity.Interaction;
import com.medicationradar.entity.Medication;
import com.medicationradar.mapper.InteractionMapper;
import com.medicationradar.mapper.MedicationMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MedicationService {
    private final MedicationMapper medicationMapper;
    private final InteractionMapper interactionMapper;
    private final ObjectMapper objectMapper;

    public MedicationService(MedicationMapper medicationMapper, InteractionMapper interactionMapper,
                             ObjectMapper objectMapper) {
        this.medicationMapper = medicationMapper;
        this.interactionMapper = interactionMapper;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> search(String query, int limit) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<Medication> rows = medicationMapper.selectList(new LambdaQueryWrapper<Medication>()
                .eq(Medication::getStatus, "active")
                .and(wrapper -> wrapper.like(Medication::getName, keyword)
                        .or().like(Medication::getAliases, keyword)
                        .or().like(Medication::getCategory, keyword))
                .orderByAsc(Medication::getName)
                .last("LIMIT " + safeLimit));
        return rows.stream().map(this::toSummary).toList();
    }

    public List<Map<String, Object>> listAll() {
        return medicationMapper.selectList(new LambdaQueryWrapper<Medication>().orderByAsc(Medication::getName))
                .stream().map(this::toView).toList();
    }

    public Map<String, Object> page(int page, int pageSize, String query, String category) {
        int safeSize = Math.max(1, Math.min(pageSize, 200));
        String keyword = query == null ? "" : query.trim();
        String safeCategory = (category == null || category.isBlank() || "全部".equals(category)) ? null : category;

        // 第一优先级：药名/别名/分类命中；为空再退回适应症检索，避免搜药名时混入适应症无关药
        Page<Medication> result = selectPage(page, safeSize, keyword, safeCategory, false);
        if (!keyword.isEmpty() && (result.getTotal() == 0 || result.getRecords().isEmpty())) {
            result = selectPage(page, safeSize, keyword, safeCategory, true);
        }
        return Map.of("items", result.getRecords().stream().map(this::toView).toList(),
                "total", result.getTotal(), "page", result.getCurrent(), "pageSize", result.getSize(),
                "totalPages", Math.max(1, result.getPages()));
    }

    private Page<Medication> selectPage(int page, int size, String keyword, String category, boolean byIndication) {
        LambdaQueryWrapper<Medication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Medication::getStatus, "active");
        if (category != null) {
            wrapper.eq(Medication::getCategory, category);
        }
        if (keyword != null && !keyword.isEmpty()) {
            if (byIndication) {
                wrapper.and(value -> value.like(Medication::getIndications, keyword));
            } else {
                wrapper.and(value -> value.like(Medication::getName, keyword)
                        .or().like(Medication::getAliases, keyword)
                        .or().like(Medication::getCategory, keyword));
            }
        }
        wrapper.orderByAsc(Medication::getName);
        return medicationMapper.selectPage(new Page<>(Math.max(page, 1), size), wrapper);
    }

    public List<Map<String, Object>> stats() {
        return medicationMapper.selectMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Medication>()
                        .select("COALESCE(NULLIF(category, ''), '未分类') AS category", "COUNT(*) AS n")
                        .groupBy("category").orderByDesc("n"));
    }

    public List<Map<String, Object>> interactions() {
        return interactionMapper.selectList(new LambdaQueryWrapper<Interaction>()
                        .orderByAsc(Interaction::getDrugA, Interaction::getDrugB))
                .stream().map(this::toInteractionView).toList();
    }

    private static final List<String> DOSAGE_FORM_SUFFIXES = List.of(
            "缓释胶囊", "缓释片", "肠溶胶囊", "肠溶片", "分散片", "咀嚼片", "泡腾片",
            "软胶囊", "注射液", "口服溶液", "口服液", "粉针剂", "气雾剂", "胶囊剂", "滴丸",
            "颗粒", "糖浆", "乳膏", "软膏", "栓剂", "片剂", "胶囊", "片", "丸", "栓");

    public Medication findByNameOrAlias(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        // 原名精确匹配失败后，剥离常见剂型后缀再试（如"氨氯地平片"→"氨氯地平"）
        for (String candidate : drugNameCandidates(normalized)) {
            Medication hit = matchExactOrAlias(candidate);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private List<String> drugNameCandidates(String name) {
        List<String> candidates = new java.util.ArrayList<>();
        candidates.add(name);
        for (String suffix : DOSAGE_FORM_SUFFIXES) {
            if (name.length() > suffix.length() + 1 && name.endsWith(suffix)) {
                String stripped = name.substring(0, name.length() - suffix.length());
                candidates.add(stripped);
                break;
            }
        }
        return candidates;
    }

    private Medication matchExactOrAlias(String name) {
        Medication exact = medicationMapper.selectOne(new LambdaQueryWrapper<Medication>()
                .eq(Medication::getStatus, "active")
                .eq(Medication::getName, name).last("LIMIT 1"));
        if (exact != null) {
            return exact;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return medicationMapper.selectList(new LambdaQueryWrapper<Medication>()
                        .eq(Medication::getStatus, "active"))
                .stream()
                .filter(item -> aliases(item).stream()
                        .anyMatch(alias -> alias.toLowerCase(java.util.Locale.ROOT).equals(lower)))
                .findFirst().orElse(null);
    }

    public Interaction findInteraction(String drugA, String drugB) {
        return interactionMapper.selectOne(new LambdaQueryWrapper<Interaction>()
                .and(value -> value.eq(Interaction::getDrugA, drugA).eq(Interaction::getDrugB, drugB)
                        .or().eq(Interaction::getDrugA, drugB).eq(Interaction::getDrugB, drugA))
                .last("LIMIT 1"));
    }

    public Map<String, Object> medicationReference(Medication value) {
        if (value == null) {
            return Map.of();
        }
        return toView(value);
    }

    public Map<String, Object> get(Long id) {
        Medication row = medicationMapper.selectById(id);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "药物不存在");
        }
        return toView(row);
    }

    public Medication create(MedicationInput input) {
        ensureUniqueName(input.name(), null);
        Medication entity = fromInput(input);
        medicationMapper.insert(entity);
        return entity;
    }

    public Medication update(Long id, MedicationInput input) {
        Medication existing = medicationMapper.selectById(id);
        if (existing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "药物不存在");
        }
        ensureUniqueName(input.name(), id);
        Medication entity = fromInput(input);
        entity.setId(id);
        entity.setVersion((existing.getVersion() == null ? 0 : existing.getVersion()) + 1);
        medicationMapper.updateById(entity);
        return medicationMapper.selectById(id);
    }

    public void disable(Long id, String reason) {
        Medication existing = medicationMapper.selectById(id);
        if (existing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "药物不存在");
        }
        if ("inactive".equals(existing.getStatus())) {
            return;
        }
        Medication update = new Medication();
        update.setId(id);
        update.setStatus("inactive");
        update.setDisabledReason(reason);
        medicationMapper.updateById(update);
    }

    public boolean isReferencedByReport(String drugName) {
        return medicationMapper.isReferencedByReport(drugName) > 0;
    }

    public Medication getById(Long id) {
        return medicationMapper.selectById(id);
    }

    private void ensureUniqueName(String name, Long ignoredId) {
        if (name == null || name.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "药物名称不能为空");
        }
        LambdaQueryWrapper<Medication> wrapper = new LambdaQueryWrapper<Medication>()
                .eq(Medication::getName, name.trim());
        if (ignoredId != null) {
            wrapper.ne(Medication::getId, ignoredId);
        }
        if (medicationMapper.selectCount(wrapper) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该药物已存在");
        }
    }

    private Medication fromInput(MedicationInput input) {
        Medication value = new Medication();
        value.setName(input.name().trim());
        try {
            value.setAliases(objectMapper.writeValueAsString(input.aliases() == null ? List.of() : input.aliases()));
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "药物别名格式不正确");
        }
        value.setCategory(orEmpty(input.category()));
        value.setIngredients(orEmpty(input.ingredients()));
        value.setAppearance(orEmpty(input.appearance()));
        value.setSpecification(orEmpty(input.specification()));
        value.setDosageForm(orEmpty(input.dosageForm()));
        value.setIndications(orEmpty(input.indications()));
        value.setUsageDosage(orEmpty(input.usageDosage()));
        value.setAdverseReactions(orEmpty(input.adverseReactions()));
        value.setContraindications(orEmpty(input.contraindications()));
        value.setPrecautions(orEmpty(input.precautions()));
        value.setSpecialPopulations(orEmpty(input.specialPopulations()));
        value.setPharmacology(orEmpty(input.pharmacology()));
        value.setTherapeuticDuplication(orEmpty(input.duplication()));
        value.setMonitor(orEmpty(input.monitor()));
        value.setNotes(orEmpty(input.notes()));
        return value;
    }

    private Map<String, Object> toSummary(Medication value) {
        return Map.of("id", value.getId(), "name", value.getName(), "aliases", aliases(value),
                "category", orEmpty(value.getCategory()), "dosageForm", orEmpty(value.getDosageForm()),
                "specification", orEmpty(value.getSpecification()));
    }

    private Map<String, Object> toView(Medication value) {
        return Map.ofEntries(
                Map.entry("id", value.getId()), Map.entry("name", value.getName()),
                Map.entry("aliases", aliases(value)), Map.entry("category", orEmpty(value.getCategory())),
                Map.entry("ingredients", orEmpty(value.getIngredients())), Map.entry("appearance", orEmpty(value.getAppearance())),
                Map.entry("specification", orEmpty(value.getSpecification())), Map.entry("dosageForm", orEmpty(value.getDosageForm())),
                Map.entry("indications", orEmpty(value.getIndications())), Map.entry("usageDosage", orEmpty(value.getUsageDosage())),
                Map.entry("adverseReactions", orEmpty(value.getAdverseReactions())), Map.entry("contraindications", orEmpty(value.getContraindications())),
                Map.entry("precautions", orEmpty(value.getPrecautions())), Map.entry("specialPopulations", orEmpty(value.getSpecialPopulations())),
                Map.entry("pharmacology", orEmpty(value.getPharmacology())), Map.entry("therapeuticDuplication", orEmpty(value.getTherapeuticDuplication())),
                Map.entry("monitor", orEmpty(value.getMonitor())), Map.entry("notes", orEmpty(value.getNotes())),
                Map.entry("status", value.getStatus() == null ? "active" : value.getStatus()),
                Map.entry("version", value.getVersion() == null ? 1 : value.getVersion()));
    }

    private Map<String, Object> toInteractionView(Interaction value) {
        return Map.of("id", value.getId(), "drugA", value.getDrugA(), "drugB", value.getDrugB(),
                "severity", value.getSeverity(), "mechanism", orEmpty(value.getMechanism()),
                "effect", orEmpty(value.getEffect()), "recommendation", orEmpty(value.getRecommendation()));
    }

    private List<String> aliases(Medication value) {
        if (value.getAliases() == null || value.getAliases().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value.getAliases(), new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Collections.emptyList();
        }
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    public record MedicationInput(String name, List<String> aliases, String category, String ingredients,
                                  String appearance, String specification, String dosageForm,
                                  String indications, String usageDosage, String adverseReactions,
                                  String contraindications, String precautions, String specialPopulations,
                                  String pharmacology, String duplication, String monitor, String notes) {
    }
}
