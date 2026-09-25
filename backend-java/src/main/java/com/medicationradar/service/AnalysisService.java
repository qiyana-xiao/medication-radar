package com.medicationradar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medicationradar.entity.Interaction;
import com.medicationradar.entity.Medication;
import com.medicationradar.entity.MedicationContraindication;
import com.medicationradar.mapper.MedicationContraindicationMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {
    private static final String SYSTEM_PROMPT = """
            你是一名资深临床药师，负责对老年患者的用药方案做药物相互作用与用药安全分析。

            严格规则：
            1. 对本地知识库已收录的药物：只能使用【药物参考】和【已知相互作用】中提供的事实；未覆盖的组合只能基于可靠药理学常识谨慎推断，并将 grounded 设为 false、confidence 设为"低"，在 mechanism 中写明"推断"。
            2. 对【外部检索药物】（本地知识库未收录）：先判断能否基于公开权威资料（如国家药监局批准的药品说明书、人卫用药助手等公开信息）可靠确认该药品真实存在且成分明确。能确认的正常纳入分析，但涉及该药的所有条目必须 grounded=false、evidenceLevel="低"、confidence="低"，并在 mechanism 开头注明「AI外部检索」；不能确认的（如生造名称、成分不明）必须 identified=false，且严禁对其输出任何分析：不得写入 interactions、scoreBreakdown、patientWarnings、duplicates、dangerSignals。
            3. 只做安全提示，不诊断、不开处方、不推荐具体品牌。
            4. interactions 只能包含输入药单中实际存在的药物两两组合（外部检索药物也在药单内），严禁引入清单外的药物。
            5. 风险分值必须为 0-100 整数，且低=0-30、中=31-60、高=61-85、极高=86-100。
            6. 面向普通患者和家属，说明具体可观察症状和可执行建议。
            7. 如果存在【同成分超量警告】，必须作为最高优先级风险处理，severity 设为"严重"。
            8. 只能输出一个合法 JSON 对象，不要输出 Markdown 或 JSON 之外的文字，数组无内容时使用 []。
            9. overall.score、overall.riskLevel、overall.scoreBreakdown 由引擎根据【确定性禁忌/关注项】计算，你输出的这些字段将被引擎覆盖，不要自行增减或调整分值。
            10. 禁忌级风险（引擎标记为"禁忌"的命中项）对应的结论必须在 headline 和 action 中体现为不建议使用、咨询医生更换药物，不能弱化为"建议复核"。
            11. interactions 中的 commonSideEffects 写该药对一般人群的常见不良反应；bodyImpact 必须结合患者的疾病、体征等个人情况说明特异影响。两者不得混写。
            12. patientWarnings 的 detail 面向患者及家属（可用"您"）；每条必须另附 doctorNote 字段：面向医生的一句话决策参考（例如"血压 145/90 未达标，拟加用新药时注意对血压的影响，可评估当前降压方案"），不复述 detail，不得出现"告知/咨询医生"等患者向表述。
            13. missingInfo 用建设性表述（"建议补充××，以便更准确评估××"），让用户知道补充后能改善什么；只有确属无法判断的维度才写"无法评估"。
            """;

    private final MedicationService medicationService;
    private final DeepSeekService deepSeekService;
    private final IngredientOverdoseChecker overdoseChecker;
    private final MedicationContraindicationMapper contraindicationMapper;

    public AnalysisService(MedicationService medicationService, DeepSeekService deepSeekService,
                           IngredientOverdoseChecker overdoseChecker,
                           MedicationContraindicationMapper contraindicationMapper) {
        this.medicationService = medicationService;
        this.deepSeekService = deepSeekService;
        this.overdoseChecker = overdoseChecker;
        this.contraindicationMapper = contraindicationMapper;
    }

    public Map<String, Object> analyze(Map<String, Object> patient,
                                       List<Map<String, Object>> baseInputs,
                                       List<Map<String, Object>> newInputs) {
        List<ResolvedMedication> baseResolved = resolveAll(baseInputs);
        List<ResolvedMedication> newResolved = resolveAll(newInputs);
        List<ResolvedMedication> allResolved = new ArrayList<>();
        allResolved.addAll(baseResolved);
        allResolved.addAll(newResolved);

        // 1) 库外药：本地药物表查无此药 → 交由 AI 外部检索分析（显著标注）；AI 无法可靠确认的才硬拦截
        List<String> notFoundNames = allResolved.stream()
                .filter(r -> r.matched() == null)
                .map(ResolvedMedication::name)
                .distinct()
                .toList();

        // 2) 已收录但结构化禁忌规则暂未收录的药：可参与分析，但禁忌评分维度不覆盖（missingInfo 明示）
        List<String> noRuleNames = allResolved.stream()
                .filter(r -> r.matched() != null)
                .filter(r -> countRules(r.matched().getId()) == 0)
                .map(ResolvedMedication::name)
                .distinct()
                .toList();

        // 3) 可评估药 = 已收录的药（有无禁忌规则均可，禁忌规则为空则该维度不评分）；库外药待 AI 外部检索确认
        List<ResolvedMedication> assessableBase = baseResolved.stream()
                .filter(r -> r.matched() != null).toList();
        List<ResolvedMedication> assessableNew = newResolved.stream()
                .filter(r -> r.matched() != null).toList();
        List<ResolvedMedication> assessableAll = new ArrayList<>();
        assessableAll.addAll(assessableBase);
        assessableAll.addAll(assessableNew);

        // 4) 本地确定性禁忌/关注评估（严重程度条件）
        List<ContraindicationHit> contraHits = evaluateContraindications(patient, assessableAll);

        // 5) 相互作用 / 成分超量只针对可评估药物
        List<Map<String, Object>> pairs = buildInteractionPairs(assessableBase, assessableNew);

        List<IngredientOverdoseChecker.ResolvedItem> overdoseItems = assessableAll.stream()
                .map(r -> new IngredientOverdoseChecker.ResolvedItem(
                        r.name(),
                        r.matched().getIngredients(),
                        estimateDailyDoseMg(r)))
                .toList();
        List<IngredientOverdoseChecker.OverdoseWarning> overdoseWarnings = overdoseChecker.check(overdoseItems);

        // 6) LLM 分析（库外药走外部检索规则；AI 不可用且无本地可评估药时降级为确定性拦截报告）
        String prompt = buildPrompt(patient, baseResolved, newResolved, notFoundNames,
                pairs, overdoseWarnings, contraHits);
        Map<String, Object> report;
        try {
            report = new LinkedHashMap<>(deepSeekService.generateJson(SYSTEM_PROMPT, prompt));
        } catch (RuntimeException exception) {
            if (assessableAll.isEmpty()) {
                return buildUnrecognizedReport(patient, baseResolved, newResolved, notFoundNames);
            }
            throw exception;
        }

        // 7) 外部检索药物落定：AI 确认可识别的参与分析（引擎强制标注推断属性），
        //    AI 无法确认的（identified=false 或缺失）按未收录处理，其分析输出一律剔除
        ExternalOutcome external = processExternalDrugs(report, notFoundNames);
        List<String> unassessableNames = external.unidentifiable();
        if (assessableAll.isEmpty() && external.identified().isEmpty()) {
            return buildUnrecognizedReport(patient, baseResolved, newResolved, notFoundNames);
        }

        // 8) 引擎覆盖确定性评分（本地评分是唯一权威，风险分 = 各风险项分值累加：
        //    禁忌命中 + 知识库相互作用 + 同成分超量，均为本地确定性事实，LLM 分值一律被覆盖；
        //    外部检索推断不计分，但推断出严重风险时按保守原则上调等级）
        List<Map<String, Object>> breakdown = buildBreakdown(contraHits);
        int totalScore = contraHits.stream().mapToInt(ContraindicationHit::score).sum();
        totalScore += addDeterministicScores(breakdown, pairs, overdoseWarnings);
        totalScore = Math.min(totalScore, 100);
        String riskLevel = mapScore(totalScore);
        boolean hasSevereHit = contraHits.stream().anyMatch(ContraindicationHit::matchedSevere);
        if (hasSevereHit && !"极高".equals(riskLevel)) {
            riskLevel = "高"; // 禁忌命中时等级下限为"高"，与"不建议使用"结论一致
        }
        if (hasSevereExternalInteraction(report, external.identified()) && "低".equals(riskLevel)) {
            riskLevel = "中"; // AI 外部检索推断的严重风险未经本地规则核对，等级保守上调
        }
        if (report.get("overall") instanceof Map<?, ?> overall) {
            ((Map<String, Object>) overall).put("scoreBreakdown", breakdown);
            ((Map<String, Object>) overall).put("score", totalScore);
            ((Map<String, Object>) overall).put("riskLevel", riskLevel);
        }

        // 9) 后置校验：剔除 LLM 对无法评估药物的任何分析输出
        sanitizeReport(report, unassessableNames);

        // 10) P0-2 禁忌级命中 → 引擎强制覆盖结论：明确写"不建议使用，请咨询医生更换药物"
        overrideContraindicationConclusion(report, contraHits);

        // 11) 引擎注入禁忌/关注提示与缺失信息；外部检索分析整体标注
        injectContraindicationWarnings(report, contraHits);
        injectNoRuleHints(report, noRuleNames);
        injectUnresolvableHints(report, unassessableNames);
        markExternalAnalysis(report, external.identified());

        List<Map<String, Object>> overdoseWarningMaps = overdoseWarnings.stream()
                .map(w -> Map.<String, Object>of(
                        "ingredient", w.ingredient(),
                        "dailyTotalMg", w.dailyTotalMg(),
                        "maxDailyMg", w.maxDailyMg(),
                        "drugNames", w.drugNames(),
                        "source", w.source()))
                .toList();
        report.put("overdoseWarnings", overdoseWarningMaps);

        List<Map<String, Object>> knowledgeVersion = assessableAll.stream()
                .map(r -> Map.<String, Object>of(
                        "id", r.matched().getId(),
                        "name", r.matched().getName(),
                        "version", r.matched().getVersion() == null ? 1 : r.matched().getVersion()))
                .toList();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("generatedAt", Instant.now().toString());
        meta.put("model", deepSeekService.model());
        meta.put("baseMedications", baseResolved.stream().map(r -> Map.of(
                "name", r.name(), "found", r.matched() != null)).toList());
        meta.put("newMedications", newResolved.stream().map(r -> Map.of(
                "name", r.name(), "found", r.matched() != null)).toList());
        meta.put("unresolvedMedications", unassessableNames);
        meta.put("externalMedications", external.profiles());
        meta.put("noRuleMedications", noRuleNames);
        meta.put("knowledgeVersion", knowledgeVersion);
        if (patient != null) {
            if (patient.get("vitals") != null) {
                meta.put("vitals", patient.get("vitals"));
            }
            if (patient.get("symptoms") != null) {
                meta.put("symptoms", patient.get("symptoms"));
            }
        }
        report.put("_meta", meta);
        return report;
    }

    private Map<String, Object> buildUnrecognizedReport(Map<String, Object> patient,
                                                        List<ResolvedMedication> baseResolved,
                                                        List<ResolvedMedication> newResolved,
                                                        List<String> unassessableNames) {
        String nameText = String.join("、", unassessableNames);

        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("riskLevel", "未收录");
        overall.put("score", 0);
        overall.put("headline", "「" + nameText + "」未收录本地药品知识库，AI 外部检索也无法可靠确认，无法评估其安全性");
        overall.put("action", "无法评估");
        overall.put("scoreBreakdown", List.of());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("forFamily", "您输入的药物（" + nameText + "）未收录在本地药品知识库中，AI 外部检索也无法可靠确认该药品信息，系统无法评估其相互作用与用药风险。"
                + "请核对药品通用名（商品名可能未收录）后重新分析，或通过人卫用药助手（pharmacy.pmphai.com）查询说明书后咨询医生、药师。");
        summary.put("forProfessional", "未收录药物：" + nameText + "。本地知识库查无此药，AI 外部检索亦无法可靠确认，未纳入分析，不参与评分。");

        List<Map<String, Object>> warnings = List.of(Map.of(
                "level", "提示",
                "title", "该药未收录，无法评估",
                "detail", nameText + " 未收录在本地药品知识库中，AI 外部检索也无法可靠确认该药品，未进行风险分析，不参与评分。"
                        + "请核对药品通用名后重试，或通过人卫用药助手查询说明书后咨询医生或药师。"));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("overall", overall);
        report.put("summary", summary);
        report.put("interactions", List.of());
        report.put("duplicates", List.of());
        report.put("patientWarnings", warnings);
        report.put("missingInfo", List.of("「" + nameText + "」未收录本地药品知识库，且 AI 外部检索无法可靠确认该药品，无法评估其安全性"));
        report.put("dietLifestyle", Map.of("dietPrinciples", List.of(), "watchList", List.of()));
        report.put("suggestions", List.of("核对药品通用名（商品名可能未收录）后重新分析",
                "通过人卫用药助手（pharmacy.pmphai.com）查询该药说明书",
                "携带药品说明书咨询医生或药师"));
        report.put("dangerSignals", List.of());
        report.put("disclaimer", "本报告仅供参考，不替代医师或药师的诊断与处方建议。");
        report.put("overdoseWarnings", List.of());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("generatedAt", Instant.now().toString());
        meta.put("model", "deterministic-gate");
        meta.put("baseMedications", baseResolved.stream().map(r -> Map.of(
                "name", r.name(), "found", false)).toList());
        meta.put("newMedications", newResolved.stream().map(r -> Map.of(
                "name", r.name(), "found", false)).toList());
        meta.put("unresolvedMedications", unassessableNames);
        meta.put("knowledgeVersion", List.of());
        if (patient != null) {
            if (patient.get("vitals") != null) {
                meta.put("vitals", patient.get("vitals"));
            }
            if (patient.get("symptoms") != null) {
                meta.put("symptoms", patient.get("symptoms"));
            }
        }
        report.put("_meta", meta);
        return report;
    }

    @SuppressWarnings("unchecked")
    private void sanitizeReport(Map<String, Object> report, List<String> unresolvedNames) {
        if (unresolvedNames.isEmpty()) {
            return;
        }
        if (report.get("interactions") instanceof List<?> interactions) {
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Object item : interactions) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> m = (Map<String, Object>) map;
                    String a = String.valueOf(m.getOrDefault("drugA", ""));
                    String b = String.valueOf(m.getOrDefault("drugB", ""));
                    if (!unresolvedNames.contains(a) && !unresolvedNames.contains(b)) {
                        kept.add(m);
                    }
                }
            }
            report.put("interactions", kept);
        }
        if (report.get("overall") instanceof Map<?, ?> overall
                && overall.get("scoreBreakdown") instanceof List<?> breakdown) {
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Object item : breakdown) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> m = (Map<String, Object>) map;
                    String text = String.valueOf(m.getOrDefault("item", ""))
                            + String.valueOf(m.getOrDefault("reason", ""));
                    if (unresolvedNames.stream().noneMatch(text::contains)) {
                        kept.add(m);
                    }
                }
            }
            ((Map<String, Object>) overall).put("scoreBreakdown", kept);
        }
        if (report.get("patientWarnings") instanceof List<?> warnings) {
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Object item : warnings) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> m = (Map<String, Object>) map;
                    String text = String.valueOf(m.getOrDefault("title", ""))
                            + String.valueOf(m.getOrDefault("detail", ""));
                    if (unresolvedNames.stream().noneMatch(text::contains)) {
                        kept.add(m);
                    }
                }
            }
            report.put("patientWarnings", kept);
        }
    }

    private long countRules(Long medicationId) {
        return contraindicationMapper.selectCount(new LambdaQueryWrapper<MedicationContraindication>()
                .eq(MedicationContraindication::getMedicationId, medicationId)
                .eq(MedicationContraindication::getStatus, 1));
    }

    // 外部检索药物落定：读取 LLM 的 externalProfiles，可确认的参与分析（引擎兜底标注推断属性），
    // 不可确认的按未收录处理。同时把 externalProfiles 从报告顶层移除（正式入口在 _meta）。
    @SuppressWarnings("unchecked")
    private ExternalOutcome processExternalDrugs(Map<String, Object> report, List<String> externalCandidates) {
        List<String> identified = new ArrayList<>();
        List<String> unidentifiable = new ArrayList<>();
        List<Map<String, Object>> profiles = new ArrayList<>();
        if (!externalCandidates.isEmpty()) {
            Map<String, Map<String, Object>> profileByName = new LinkedHashMap<>();
            if (report.get("externalProfiles") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?>) {
                        Map<String, Object> map = (Map<String, Object>) item;
                        String name = String.valueOf(map.getOrDefault("name", "")).trim();
                        if (!name.isEmpty()) {
                            profileByName.put(name, map);
                        }
                    }
                }
            }
            for (String candidate : externalCandidates) {
                Map<String, Object> profile = findProfile(profileByName, candidate);
                boolean ok = profile != null && Boolean.TRUE.equals(profile.get("identified"));
                if (ok) {
                    identified.add(candidate);
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("name", candidate);
                    p.put("composition", stringOrNull(profile.get("composition")));
                    p.put("drugClass", stringOrNull(profile.get("drugClass")));
                    p.put("usage", stringOrNull(profile.get("usage")));
                    p.put("cautions", stringOrNull(profile.get("cautions")));
                    profiles.add(p);
                } else {
                    unidentifiable.add(candidate);
                }
            }
            report.remove("externalProfiles");
        }
        // 引擎兜底：涉及外部检索药物的相互作用一律按"推断"标注，防止 LLM 漏标
        if (!identified.isEmpty() && report.get("interactions") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> m = (Map<String, Object>) map;
                    String a = String.valueOf(m.getOrDefault("drugA", ""));
                    String b = String.valueOf(m.getOrDefault("drugB", ""));
                    if (identified.contains(a) || identified.contains(b)) {
                        m.put("external", true);
                        m.put("grounded", false);
                        m.put("evidenceLevel", "低");
                    }
                }
            }
        }
        return new ExternalOutcome(identified, unidentifiable, profiles);
    }

    private Map<String, Object> findProfile(Map<String, Map<String, Object>> profileByName, String candidate) {
        Map<String, Object> exact = profileByName.get(candidate);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Map<String, Object>> entry : profileByName.entrySet()) {
            if (entry.getKey().contains(candidate) || candidate.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean hasSevereExternalInteraction(Map<String, Object> report, List<String> identified) {
        if (identified.isEmpty() || !(report.get("interactions") instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                Map<String, Object> map = (Map<String, Object>) item;
                String a = String.valueOf(map.getOrDefault("drugA", ""));
                String b = String.valueOf(map.getOrDefault("drugB", ""));
                if ((identified.contains(a) || identified.contains(b))
                        && "严重".equals(String.valueOf(map.get("severity")))) {
                    return true;
                }
            }
        }
        return false;
    }

    // 未收录且 AI 无法确认的药：missingInfo 明示，不参与本次评估
    private void injectUnresolvableHints(Map<String, Object> report, List<String> names) {
        if (names.isEmpty()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (report.get("missingInfo") instanceof List<?> mi) {
            for (Object item : mi) {
                missing.add(String.valueOf(item));
            }
        }
        for (String name : names) {
            missing.add("「" + name + "」未收录本地知识库，且 AI 外部检索无法可靠确认该药品，未参与本次评估；"
                    + "建议核对通用名，或通过人卫用药助手查询后重新分析");
        }
        report.put("missingInfo", missing);
    }

    // 外部检索分析整体标注：overall 标记 + headline 提示（置于禁忌覆盖之后，避免被覆盖）
    @SuppressWarnings("unchecked")
    private void markExternalAnalysis(Map<String, Object> report, List<String> identified) {
        if (identified.isEmpty() || !(report.get("overall") instanceof Map<?, ?> overallRaw)) {
            return;
        }
        Map<String, Object> overall = (Map<String, Object>) overallRaw;
        overall.put("externalAnalysis", true);
        String headline = String.valueOf(overall.getOrDefault("headline", ""));
        if (!headline.contains("AI 外部检索")) {
            overall.put("headline", headline + "（本次含 AI 外部检索分析内容，请核实后使用）");
        }
    }

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    // 严重程度判定：重度 / 非重度 / 未说明
    private List<ContraindicationHit> evaluateContraindications(Map<String, Object> patient,
                                                                List<ResolvedMedication> assessableAll) {
        List<ContraindicationHit> hits = new ArrayList<>();
        if (patient == null || assessableAll.isEmpty()) {
            return hits;
        }
        List<String> conditions = extractConditions(patient);
        String conditionText = String.join("、", conditions);
        for (ResolvedMedication r : assessableAll) {
            List<MedicationContraindication> rules = contraindicationMapper.selectList(
                    new LambdaQueryWrapper<MedicationContraindication>()
                            .eq(MedicationContraindication::getMedicationId, r.matched().getId())
                            .eq(MedicationContraindication::getStatus, 1));
            for (MedicationContraindication rule : rules) {
                String keyword = rule.getConditionKeyword();
                if (keyword == null || keyword.isBlank() || !conditionText.contains(keyword)) {
                    continue;
                }
                String severity = detectSeverity(conditions, keyword, patient);
                boolean severeRequired = "重度".equals(rule.getSeverityRequired());
                if (!severeRequired) {
                    hits.add(new ContraindicationHit(r.name(), keyword, severity,
                            rule.getSeverityRequired(), true, false, rule.getScore(),
                            nullToEmpty(rule.getNote()), nullToEmpty(rule.getSource())));
                } else if ("重度".equals(severity)) {
                    hits.add(new ContraindicationHit(r.name(), keyword, severity,
                            rule.getSeverityRequired(), true, false, rule.getScore(),
                            nullToEmpty(rule.getNote()), nullToEmpty(rule.getSource())));
                } else if ("非重度".equals(severity)) {
                    continue;
                } else {
                    // 信息不足：提及该病但未说明严重程度 → 降级"需关注"，不扣分
                    String note = rule.getNote() == null || rule.getNote().isBlank()
                            ? "若您的" + keyword + "属于重度，则该药禁用，请向医生确认。"
                            : rule.getNote();
                    hits.add(new ContraindicationHit(r.name(), keyword, "未说明",
                            rule.getSeverityRequired(), false, true, 0, note,
                            nullToEmpty(rule.getSource())));
                }
            }
        }
        return hits;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractConditions(Map<String, Object> patient) {
        Object c = patient.get("conditions");
        if (c == null) {
            return List.of();
        }
        if (c instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim)
                    .filter(s -> !s.isEmpty()).collect(Collectors.toList());
        }
        if (c instanceof String s) {
            return s.isBlank() ? List.of() : List.of(s.trim());
        }
        return List.of();
    }

    private String detectSeverity(List<String> conditions, String keyword, Map<String, Object> patient) {
        for (String cond : conditions) {
            if (!cond.contains(keyword)) {
                continue;
            }
            if (containsAny(cond, SEVERE_WORDS)) {
                return "重度";
            }
            if (containsAny(cond, MILD_WORDS)) {
                return "非重度";
            }
            break;
        }
        // 高血压可依据血压值判断严重程度（分级标准：中国高血压防治指南）
        if ("高血压".equals(keyword)) {
            String bp = bloodPressureSeverity(patient);
            if (bp != null) {
                return bp;
            }
        }
        return "未说明";
    }

    // 血压分级：3级（≥180 或 ≥110）= 重度；1-2级（140-179/90-109）= 非重度
    private String bloodPressureSeverity(Map<String, Object> patient) {
        Object vitals = patient.get("vitals");
        if (!(vitals instanceof Map<?, ?> vm)) {
            return null;
        }
        Object sObj = vm.get("systolic");
        Object dObj = vm.get("diastolic");
        if (sObj == null || dObj == null) {
            return null;
        }
        try {
            double s = Double.parseDouble(String.valueOf(sObj));
            double d = Double.parseDouble(String.valueOf(dObj));
            if (s >= 180 || d >= 110) {
                return "重度";
            }
            if (s >= 140 || d >= 90) {
                return "非重度";
            }
            return "非重度";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean containsAny(String text, List<String> words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static final List<String> SEVERE_WORDS = List.of(
            "重度", "严重", "重症", "终末期", "失代偿", "急性", "危重", "晚期");
    private static final List<String> MILD_WORDS = List.of(
            "轻度", "轻微", "早期", "初期", "稳定期");

    private List<Map<String, Object>> buildBreakdown(List<ContraindicationHit> hits) {
        List<Map<String, Object>> breakdown = new ArrayList<>();
        for (ContraindicationHit h : hits) {
            Map<String, Object> m = new LinkedHashMap<>();
            String suffix = "重度".equals(h.severityFound()) ? "（重度）"
                    : "未说明".equals(h.severityFound()) ? "（严重程度未明）" : "";
            m.put("item", h.drugName() + "：" + h.condition() + suffix);
            m.put("score", h.score());
            m.put("deduction", h.score()); // 兼容旧字段，P0-3 统一迁移至 score
            String reason = h.note();
            if (h.degraded()) {
                reason = "信息不足，未按禁忌扣分。" + h.note();
            }
            if (!h.source().isBlank()) {
                reason += "（依据：" + h.source() + "）";
            }
            m.put("reason", reason);
            breakdown.add(m);
        }
        return breakdown;
    }

    private String mapScore(int score) {
        if (score >= 86) {
            return "极高";
        }
        if (score >= 61) {
            return "高";
        }
        if (score >= 31) {
            return "中";
        }
        return "低";
    }

    // 确定性计分：相互作用按知识库记录的严重程度计分，同成分超量每条固定计分。
    // 分值设计沿用本项目"风险项累加"体系，与禁忌规则分值同级；超量阈值的医学出处见 IngredientOverdoseChecker 常量注释。
    // 重复用药由 AI 锚定生成、无法确定性复核，故不参与计分，仅作提示展示。
    private static final Map<String, Integer> INTERACTION_SEVERITY_SCORES = Map.of(
            "严重", 20, "中等", 12, "轻微", 6, "需关注", 3);
    private static final int OVERDOSE_SCORE = 20;

    private int addDeterministicScores(List<Map<String, Object>> breakdown,
                                       List<Map<String, Object>> pairs,
                                       List<IngredientOverdoseChecker.OverdoseWarning> overdoseWarnings) {
        int added = 0;
        for (Map<String, Object> pair : pairs) {
            if (!(pair.get("known") instanceof Map<?, ?> known)
                    || !(pair.get("pair") instanceof List<?> names) || names.size() < 2) {
                continue;
            }
            String severity = String.valueOf(known.get("severity"));
            Integer points = INTERACTION_SEVERITY_SCORES.get(severity);
            if (points == null) {
                continue;
            }
            Object effectObj = known.get("effect");
            String effect = effectObj == null ? "" : String.valueOf(effectObj).trim();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("item", names.get(0) + " × " + names.get(1) + "（" + severity + "）");
            entry.put("score", points);
            entry.put("deduction", points); // 兼容旧字段，P0-3 统一迁移至 score
            entry.put("reason", effect.isEmpty()
                    ? "知识库记录的相互作用（依据：本地相互作用规则库）"
                    : "知识库记录：" + effect + "（依据：本地相互作用规则库）");
            breakdown.add(entry);
            added += points;
        }
        for (IngredientOverdoseChecker.OverdoseWarning w : overdoseWarnings) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("item", "同成分超量：" + w.ingredient() + "（" + String.join("、", w.drugNames()) + "）");
            entry.put("score", OVERDOSE_SCORE);
            entry.put("deduction", OVERDOSE_SCORE); // 兼容旧字段，P0-3 统一迁移至 score
            entry.put("reason", "累计日剂量 " + w.dailyTotalMg() + "mg，超过上限 " + w.maxDailyMg()
                    + "mg（依据：" + w.source() + "）");
            breakdown.add(entry);
            added += OVERDOSE_SCORE;
        }
        return added;
    }

    @SuppressWarnings("unchecked")
    private void injectContraindicationWarnings(Map<String, Object> report, List<ContraindicationHit> hits) {
        if (hits.isEmpty()) {
            return;
        }
        List<Map<String, Object>> warnings = new ArrayList<>();
        if (report.get("patientWarnings") instanceof List<?> existing) {
            for (Object item : existing) {
                if (item instanceof Map<?, ?> m) {
                    warnings.add((Map<String, Object>) m);
                }
            }
        }
        List<String> missing = new ArrayList<>();
        if (report.get("missingInfo") instanceof List<?> mi) {
            for (Object item : mi) {
                missing.add(String.valueOf(item));
            }
        }
        for (ContraindicationHit h : hits) {
            if (h.matchedSevere()) {
                warnings.add(Map.of(
                        "level", "危险",
                        "title", h.drugName() + "对您的" + h.condition() + "（重度）属于禁忌",
                        "detail", h.note()));
            } else if (h.degraded()) {
                warnings.add(Map.of(
                        "level", "警告",
                        "title", "信息不足：" + h.drugName() + "可能与您的" + h.condition() + "冲突",
                        "detail", h.note()));
                missing.add("缺少" + h.condition() + "严重程度，无法确认" + h.drugName() + "是否属于禁忌，暂按需关注处理");
            }
        }
        report.put("patientWarnings", warnings);
        report.put("missingInfo", missing);
    }

    // P0-2 禁忌级命中：引擎强制覆盖结论，明确写"不建议使用，请咨询医生更换药物"
    @SuppressWarnings("unchecked")
    private void overrideContraindicationConclusion(Map<String, Object> report,
                                                    List<ContraindicationHit> contraHits) {
        List<ContraindicationHit> severeHits = contraHits.stream()
                .filter(ContraindicationHit::matchedSevere).toList();
        if (severeHits.isEmpty() || !(report.get("overall") instanceof Map<?, ?> overallRaw)) {
            return;
        }
        Map<String, Object> overall = (Map<String, Object>) overallRaw;

        List<Map<String, Object>> items = new ArrayList<>();
        List<String> drugNames = new ArrayList<>();
        List<String> pairTexts = new ArrayList<>();
        for (ContraindicationHit h : severeHits) {
            if (!drugNames.contains(h.drugName())) {
                drugNames.add(h.drugName());
            }
            String pair = h.drugName() + " × 重度" + h.condition();
            if (!pairTexts.contains(pair)) {
                pairTexts.add(pair);
            }
            items.add(new LinkedHashMap<>(Map.of(
                    "drug", h.drugName(),
                    "condition", h.condition(),
                    "severity", h.severityFound(),
                    "note", h.note(),
                    "source", h.source(),
                    "alternatives", extractAlternatives(report, h.drugName()))));
        }
        overall.put("contraindicated", true);
        overall.put("contraindicationItems", items);
        overall.put("action", "不建议使用，请咨询医生更换药物");

        StringBuilder headline = new StringBuilder();
        headline.append("「").append(String.join("、", drugNames)).append("」对您属于禁忌范围（")
                .append(String.join("；", pairTexts))
                .append("），不建议使用，请咨询医生更换其他药物。");
        List<String> alts = items.stream()
                .flatMap(i -> ((List<String>) i.get("alternatives")).stream())
                .distinct()
                .toList();
        if (!alts.isEmpty()) {
            headline.append("可向医生咨询更安全的替代方向：")
                    .append(String.join("；", alts))
                    .append("。是否更换、如何更换请务必由医生或药师决定。");
        }
        overall.put("headline", headline.toString());
    }

    // 从 LLM 相互作用分析中提取更安全替代方案，提升到结论区
    @SuppressWarnings("unchecked")
    private List<String> extractAlternatives(Map<String, Object> report, String drugName) {
        List<String> result = new ArrayList<>();
        if (!(report.get("interactions") instanceof List<?> interactions)) {
            return result;
        }
        for (Object item : interactions) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) item;
            String a = String.valueOf(map.getOrDefault("drugA", ""));
            String b = String.valueOf(map.getOrDefault("drugB", ""));
            if (drugName.equals(a) || drugName.equals(b)) {
                if (map.get("alternatives") instanceof List<?> alts) {
                    for (Object alt : alts) {
                        String s = String.valueOf(alt).trim();
                        if (!s.isEmpty() && !"null".equals(s) && !result.contains(s)) {
                            result.add(s);
                        }
                    }
                }
            }
        }
        return result;
    }

    // 已收录但禁忌规则未结构化的药：明确提示禁忌评分维度未覆盖，其余维度正常评估
    private void injectNoRuleHints(Map<String, Object> report, List<String> noRuleNames) {
        if (noRuleNames.isEmpty()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (report.get("missingInfo") instanceof List<?> mi) {
            for (Object item : mi) {
                missing.add(String.valueOf(item));
            }
        }
        for (String name : noRuleNames) {
            missing.add("「" + name + "」的结构化禁忌规则暂未收录本地规则库，禁忌评分维度未覆盖（药物相互作用等其他维度照常评估）");
        }
        report.put("missingInfo", missing);
    }

    private List<ResolvedMedication> resolveAll(List<Map<String, Object>> inputs) {
        List<ResolvedMedication> resolved = new ArrayList<>();
        for (Map<String, Object> input : inputs) {
            String inputName = String.valueOf(input.getOrDefault("name", "")).trim();
            Medication hit = medicationService.findByNameOrAlias(inputName);
            resolved.add(new ResolvedMedication(input, hit == null ? inputName : hit.getName(), hit));
        }
        return resolved;
    }

    private List<Map<String, Object>> buildInteractionPairs(List<ResolvedMedication> base,
                                                            List<ResolvedMedication> newMeds) {
        List<Map<String, Object>> pairs = new ArrayList<>();
        for (int i = 0; i < newMeds.size(); i++) {
            for (int j = i + 1; j < newMeds.size(); j++) {
                pairs.add(buildPair(newMeds.get(i), newMeds.get(j)));
            }
        }
        for (ResolvedMedication newMed : newMeds) {
            for (ResolvedMedication baseMed : base) {
                pairs.add(buildPair(newMed, baseMed));
            }
        }
        return pairs;
    }

    private Map<String, Object> buildPair(ResolvedMedication a, ResolvedMedication b) {
        Interaction interaction = medicationService.findInteraction(a.name(), b.name());
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("pair", List.of(a.name(), b.name()));
        pair.put("known", interaction == null ? null : Map.of(
                "severity", interaction.getSeverity(),
                "mechanism", nullToEmpty(interaction.getMechanism()),
                "effect", nullToEmpty(interaction.getEffect()),
                "recommendation", nullToEmpty(interaction.getRecommendation())));
        return pair;
    }

    private double estimateDailyDoseMg(ResolvedMedication r) {
        Object doseObj = r.input().get("dose");
        Object freqObj = r.input().get("frequency");
        if (doseObj == null) return 0;
        String doseStr = String.valueOf(doseObj).trim();
        double mg = parseMg(doseStr);
        if (mg <= 0) return 0;
        int times = estimateDailyTimes(freqObj == null ? "" : String.valueOf(freqObj));
        return mg * Math.max(1, times);
    }

    private double parseMg(String dose) {
        if (dose == null || dose.isBlank()) return 0;
        String lower = dose.toLowerCase();
        try {
            if (lower.contains("g") && !lower.contains("mg")) {
                return Double.parseDouble(lower.replaceAll("[^0-9.]", "")) * 1000;
            }
            return Double.parseDouble(lower.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int estimateDailyTimes(String frequency) {
        if (frequency.contains("三次") || frequency.contains("3次")) return 3;
        if (frequency.contains("两次") || frequency.contains("2次")) return 2;
        if (frequency.contains("一次") || frequency.contains("1次")) return 1;
        if (frequency.contains("每周")) return 0;
        if (frequency.contains("必要")) return 1;
        return 1;
    }

    private String buildPrompt(Map<String, Object> patient,
                               List<ResolvedMedication> baseMeds,
                               List<ResolvedMedication> newMeds,
                               List<String> externalNames,
                               List<Map<String, Object>> pairs,
                               List<IngredientOverdoseChecker.OverdoseWarning> overdoseWarnings,
                               List<ContraindicationHit> contraHits) {
        StringBuilder prompt = new StringBuilder("# 患者信息\n").append(patient).append("\n");

        prompt.append("\n# 长期/基础用药（患者平时在吃的药）\n");
        if (baseMeds.isEmpty()) {
            prompt.append("无\n");
        } else {
            for (int i = 0; i < baseMeds.size(); i++) {
                ResolvedMedication r = baseMeds.get(i);
                prompt.append(i + 1).append(". ").append(r.name())
                        .append(externalNames.contains(r.name()) ? "（本地知识库未收录，AI 外部检索分析）" : "")
                        .append("，剂量：").append(r.input().getOrDefault("dose", "未提供"))
                        .append("，频次：").append(r.input().getOrDefault("frequency", "未提供")).append('\n');
            }
        }

        prompt.append("\n# 本次新增/拟用药物（患者想问能不能吃的药）\n");
        if (newMeds.isEmpty()) {
            prompt.append("无\n");
        } else {
            for (int i = 0; i < newMeds.size(); i++) {
                ResolvedMedication r = newMeds.get(i);
                prompt.append(i + 1).append(". ").append(r.name())
                        .append(externalNames.contains(r.name()) ? "（本地知识库未收录，AI 外部检索分析）" : "")
                        .append("，剂量：").append(r.input().getOrDefault("dose", "未提供"))
                        .append("，频次：").append(r.input().getOrDefault("frequency", "未提供")).append('\n');
            }
        }

        if (!externalNames.isEmpty()) {
            prompt.append("\n# 外部检索药物（本地知识库未收录，处理规则见系统提示第 2 条）\n");
            prompt.append(String.join("、", externalNames)).append('\n');
            prompt.append("请逐个判断能否基于公开权威资料可靠确认，并在 JSON 顶层输出 externalProfiles 数组：")
                    .append("{\"name\":\"与药单一致的名称\",\"identified\":true或false,\"composition\":\"主要成分\",")
                    .append("\"drugClass\":\"药理分类\",\"usage\":\"主要用途\",\"cautions\":\"关键安全提示（成分与合并用药要点）\"}。")
                    .append("能确认的按系统提示第 2 条正常分析；不能确认的严禁输出任何分析。\n");
        }

        if (!contraHits.isEmpty()) {
            prompt.append("\n# 确定性禁忌/关注项（引擎已判定，必须原文呈现，分值由引擎计算并覆盖，不得增减）\n");
            for (ContraindicationHit h : contraHits) {
                prompt.append("- ").append(h.drugName()).append(" × 患者").append(h.condition());
                if ("重度".equals(h.severityFound())) {
                    prompt.append("（重度）→ 禁忌");
                } else if (h.degraded()) {
                    prompt.append("（严重程度未明）→ 需关注：" ).append(h.note());
                } else {
                    prompt.append(" → ").append(h.severityFound()).append("，不命中");
                }
                prompt.append('\n');
            }
        }

        if (!overdoseWarnings.isEmpty()) {
            prompt.append("\n# 同成分超量警告（最高优先级，必须作为严重风险处理）\n");
            for (var w : overdoseWarnings) {
                prompt.append("- ").append(w.ingredient())
                        .append("：累计 ").append(w.dailyTotalMg()).append("mg，超过日最大量 ")
                        .append(w.maxDailyMg()).append("mg，来源药物：")
                        .append(String.join("、", w.drugNames()))
                        .append("（来源：").append(w.source()).append("）\n");
            }
        }

        prompt.append("\n# 药物参考（知识库锚点，请优先依据；外部检索药物不在参考之列）\n");
        for (ResolvedMedication r : baseMeds) {
            if (r.matched() != null) {
                prompt.append("- [基础]").append(r.name()).append(": ")
                        .append(medicationService.medicationReference(r.matched()))
                        .append('\n');
            }
        }
        for (ResolvedMedication r : newMeds) {
            if (r.matched() != null) {
                prompt.append("- [新增]").append(r.name()).append(": ")
                        .append(medicationService.medicationReference(r.matched()))
                        .append('\n');
            }
        }

        prompt.append("\n# 已知相互作用（仅以下组合有知识库记录）\n").append(pairs).append('\n');

        prompt.append("\n请输出完整结构：");
        prompt.append("{\"overall\":{\"riskLevel\":\"低|中|高|极高\",\"score\":0,\"headline\":\"\",\"action\":\"可以按说明书自行服用|可以吃，但需注意××|建议先问医生或药师|出现危险信号，立即就医\",\"scoreBreakdown\":[{\"item\":\"\",\"score\":0,\"reason\":\"\"}]},");
        prompt.append("\"summary\":{\"forFamily\":\"\",\"forProfessional\":\"\"},");
        prompt.append("\"interactions\":[{\"drugA\":\"\",\"drugB\":\"\",\"severity\":\"严重|中等|轻微|需关注\",");
        prompt.append("\"mechanism\":\"\",\"clinicalEffect\":\"\",\"commonSideEffects\":\"\",\"bodyImpact\":\"\",\"dietAdvice\":\"\",");
        prompt.append("\"recommendation\":\"\",\"whatToDo\":\"\",\"whenToSeekHelp\":\"\",");
        prompt.append("\"evidenceLevel\":\"高|中|低\",\"doseAdjustment\":\"\",\"alternatives\":[\"\"],");
        prompt.append("\"grounded\":true,\"confidence\":\"高|中|低\"}],");
        prompt.append("\"duplicates\":[{\"category\":\"\",\"drugs\":[],\"reason\":\"\"}],");
        prompt.append("\"externalProfiles\":[{\"name\":\"\",\"identified\":false,\"composition\":\"\",\"drugClass\":\"\",\"usage\":\"\",\"cautions\":\"\"}],");
        prompt.append("\"patientWarnings\":[{\"title\":\"\",\"detail\":\"\",\"level\":\"危险|警告|提示\",\"doctorNote\":\"\"}],");
        prompt.append("\"missingInfo\":[\"建议补充××，以便更准确评估××\"],");
        prompt.append("\"dietLifestyle\":{\"dietPrinciples\":[],\"watchList\":[]},\"suggestions\":[],");
        prompt.append("\"dangerSignals\":[\"胸痛\",\"呼吸困难\",\"黑便或呕血\",\"意识改变\"],");
        prompt.append("\"disclaimer\":\"本报告仅供参考，不替代医师或药师的诊断与处方建议。\"}");
        return prompt.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record ResolvedMedication(Map<String, Object> input, String name, Medication matched) {
        public String displayName() {
            return name;
        }
    }

    public record ContraindicationHit(String drugName, String condition, String severityFound,
                                      String severityRequired, boolean matchedSevere, boolean degraded,
                                      int score, String note, String source) {
    }

    // 外部检索药物的处理结果：identified = AI 确认可分析；unidentifiable = 未收录且 AI 无法确认；profiles = 展示用档案
    public record ExternalOutcome(List<String> identified, List<String> unidentifiable,
                                  List<Map<String, Object>> profiles) {
    }
}
