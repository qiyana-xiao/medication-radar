package com.medicationradar.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 同成分累计超量检查。
 * 日最大量数据集中维护，每条标注来源；来源未确认的标 TODO: 待医学审核。
 */
@Service
public class IngredientOverdoseChecker {

    public record OverdoseWarning(String ingredient, double dailyTotalMg, double maxDailyMg,
                                  List<String> drugNames, String source) {
    }

    private static final Map<String, LimitEntry> DAILY_LIMITS = new LinkedHashMap<>();

    static {
        // 来源已确认的常见成分日最大量（成人）
        DAILY_LIMITS.put("对乙酰氨基酚", new LimitEntry(4000, "中国药典/WHO"));
        DAILY_LIMITS.put("布洛芬", new LimitEntry(2400, "中国药典/FDA"));
        DAILY_LIMITS.put("阿司匹林", new LimitEntry(4000, "中国药典"));
        DAILY_LIMITS.put("氯苯那敏", new LimitEntry(24, "中国药典"));
        DAILY_LIMITS.put("伪麻黄碱", new LimitEntry(240, "中国药典/FDA"));
        DAILY_LIMITS.put("右美沙芬", new LimitEntry(120, "中国药典/FDA"));
        DAILY_LIMITS.put("咖啡因", new LimitEntry(400, "EFSA/中国药典"));
        DAILY_LIMITS.put("马来酸氯苯那敏", new LimitEntry(24, "中国药典"));
        DAILY_LIMITS.put("盐酸伪麻黄碱", new LimitEntry(240, "中国药典/FDA"));
        DAILY_LIMITS.put("氢氯噻嗪", new LimitEntry(50, "中国药典")); // TODO: 待医学审核
        DAILY_LIMITS.put("格列本脲", new LimitEntry(15, "中国药典"));
        DAILY_LIMITS.put("二甲双胍", new LimitEntry(2550, "FDA/中国指南"));
        DAILY_LIMITS.put("阿托伐他汀", new LimitEntry(80, "FDA/中国药典"));
        DAILY_LIMITS.put("华法林", new LimitEntry(999999, "按INR调整，无固定mg上限")); // 特殊药物，不按mg计
    }

    public List<OverdoseWarning> check(List<ResolvedItem> resolvedItems) {
        Map<String, Accumulator> accum = new LinkedHashMap<>();
        for (ResolvedItem item : resolvedItems) {
            List<String> ingredients = parseIngredients(item.ingredients());
            for (String ing : ingredients) {
                String key = ing.toLowerCase(Locale.ROOT);
                accum.computeIfAbsent(key, k -> new Accumulator(ing)).add(item.displayName(), item.dailyDoseMg());
            }
        }

        List<OverdoseWarning> warnings = new ArrayList<>();
        for (Accumulator acc : accum.values()) {
            LimitEntry limit = DAILY_LIMITS.get(acc.canonicalName);
            if (limit == null) {
                limit = DAILY_LIMITS.get(acc.canonicalName.toLowerCase(Locale.ROOT));
            }
            if (limit != null && acc.totalMg > limit.maxDailyMg) {
                warnings.add(new OverdoseWarning(
                        acc.canonicalName, acc.totalMg, limit.maxDailyMg,
                        acc.drugNames, limit.source));
            }
        }
        return warnings;
    }

    private List<String> parseIngredients(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : raw.split("[,，、;/\\s]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public record ResolvedItem(String displayName, String ingredients, double dailyDoseMg) {
    }

    private static class Accumulator {
        final String canonicalName;
        double totalMg;
        final List<String> drugNames = new ArrayList<>();

        Accumulator(String canonicalName) {
            this.canonicalName = canonicalName;
        }

        void add(String drugName, double doseMg) {
            if (doseMg > 0) {
                totalMg += doseMg;
                drugNames.add(drugName);
            }
        }
    }

    private record LimitEntry(double maxDailyMg, String source) {
    }
}
