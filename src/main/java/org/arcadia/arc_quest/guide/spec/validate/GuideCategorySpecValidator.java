package org.arcadia.arc_quest.guide.spec.validate;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.spec.GuideCategorySpec;
import org.arcadia.arc_quest.guide.spec.GuideTextSpec;

public final class GuideCategorySpecValidator {

    public static ResourceLocation parseCategoryId(String rawCategory) {
        if (blank(rawCategory)) {
            return null;
        }
        String normalized = rawCategory.trim().toLowerCase();
        if (!normalized.contains(":")) {
            normalized = "arc_quest:" + normalized;
        }
        return ResourceLocation.tryParse(normalized);
    }

    static void validateText(GuideValidationReport report, GuideTextSpec spec, String path) {
        if (spec == null) {
            report.add(GuideValidationIssue.Severity.ERROR, path, "GuideTextSpec is required");
            return;
        }
        if (blank(spec.mode)) {
            report.add(GuideValidationIssue.Severity.ERROR, path + ".mode", "GuideTextSpec mode is required");
            return;
        }
        if (!"literal".equals(spec.mode) && !"translatable".equals(spec.mode)) {
            report.add(GuideValidationIssue.Severity.ERROR, path + ".mode", "Unsupported GuideTextSpec mode: " + spec.mode);
        }
        if (spec.value == null || spec.value.isBlank()) {
            report.add(GuideValidationIssue.Severity.ERROR, path + ".value", "GuideTextSpec value is required");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public GuideValidationReport validate(GuideCategorySpec spec) {
        GuideValidationReport report = new GuideValidationReport();
        if (spec == null) {
            report.add(GuideValidationIssue.Severity.ERROR, "guideCategory", "GuideCategorySpec is null");
            return report;
        }

        requireId(report, spec.id, "id", "Guide category id is required");
        if (!isValidId(spec.id)) {
            report.add(GuideValidationIssue.Severity.ERROR, "id", "Invalid guide category id: " + spec.id);
        }
        validateText(report, spec.displayName, "displayName");
        if (spec.themeColor < 0x000000 || spec.themeColor > 0xFFFFFF) {
            report.add(GuideValidationIssue.Severity.ERROR, "themeColor", "themeColor must be between 0x000000 and 0xFFFFFF");
        }
        if (!blank(spec.iconTexture) && ResourceLocation.tryParse(spec.iconTexture) == null) {
            report.add(GuideValidationIssue.Severity.ERROR, "iconTexture", "Invalid iconTexture id: " + spec.iconTexture);
        }
        return report;
    }

    private void requireId(GuideValidationReport report, String value, String path, String message) {
        if (blank(value)) {
            report.add(GuideValidationIssue.Severity.ERROR, path, message);
        }
    }

    private boolean isValidId(String raw) {
        return parseCategoryId(raw) != null;
    }
}
