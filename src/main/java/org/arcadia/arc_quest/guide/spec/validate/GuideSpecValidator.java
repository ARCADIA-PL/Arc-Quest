package org.arcadia.arc_quest.guide.spec.validate;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.guide.spec.*;

import java.util.List;

public final class GuideSpecValidator {

    public static final List<String> VALID_MEDIA_TYPES = List.of("none", "image", "ponder");

    public GuideValidationReport validate(GuideSpec spec) {
        GuideValidationReport report = new GuideValidationReport();
        if (spec == null) {
            report.add(GuideValidationIssue.Severity.ERROR, "guide", "GuideSpec is null");
            return report;
        }

        require(report, spec.id, "id", "Guide id is required");
        if (!blank(spec.id) && ResourceLocation.tryParse(spec.id) == null) {
            report.add(GuideValidationIssue.Severity.ERROR, "id", "Invalid guide id: " + spec.id);
        }

        validateCategory(report, spec.category, "category");
        GuideCategorySpecValidator.validateText(report, spec.title, "title");

        if (empty(spec.pages)) {
            report.add(GuideValidationIssue.Severity.ERROR, "pages", "Guide must contain at least one page");
        } else {
            for (int i = 0; i < spec.pages.size(); i++) {
                validatePage(report, spec.pages.get(i), "pages[" + i + "]");
            }
        }

        if (spec.unlockConditions != null) {
            for (int i = 0; i < spec.unlockConditions.size(); i++) {
                validateCondition(report, spec.unlockConditions.get(i), "unlockConditions[" + i + "]");
            }
        }
        return report;
    }

    private void validateCategory(GuideValidationReport report, String rawCategory, String path) {
        if (blank(rawCategory)) {
            report.add(GuideValidationIssue.Severity.ERROR, path, "Guide category is required");
            return;
        }
        ResourceLocation id = GuideCategorySpecValidator.parseCategoryId(rawCategory);
        if (id == null) {
            report.add(GuideValidationIssue.Severity.ERROR, path, "Invalid guide category id: " + rawCategory);
        }
    }

    private void validatePage(GuideValidationReport report, GuidePageSpec page, String path) {
        if (page == null) {
            report.add(GuideValidationIssue.Severity.ERROR, path, "GuidePageSpec is required");
            return;
        }
        GuideCategorySpecValidator.validateText(report, page.description, path + ".description");
        validateMedia(report, page.media, path + ".media");
    }

    private void validateMedia(GuideValidationReport report, GuideMediaSpec media, String path) {
        if (media == null) {
            report.add(GuideValidationIssue.Severity.ERROR, path, "GuideMediaSpec is required");
            return;
        }
        String normalizedType = normalizeMediaType(media.type);
        if (!VALID_MEDIA_TYPES.contains(normalizedType)) {
            report.add(GuideValidationIssue.Severity.ERROR, path + ".type", "Invalid guide media type: " + media.type);
            return;
        }
        switch (normalizedType) {
            case "image" -> {
                if (blank(media.texture)) {
                    report.add(GuideValidationIssue.Severity.ERROR, path + ".texture", "image media requires texture");
                } else if (ResourceLocation.tryParse(media.texture) == null) {
                    report.add(GuideValidationIssue.Severity.ERROR, path + ".texture", "Invalid texture id: " + media.texture);
                }
            }
            case "ponder" -> {
                if (blank(media.sceneId)) {
                    report.add(GuideValidationIssue.Severity.ERROR, path + ".sceneId", "ponder media requires sceneId");
                } else if (ResourceLocation.tryParse(media.sceneId) == null) {
                    report.add(GuideValidationIssue.Severity.ERROR, path + ".sceneId", "Invalid sceneId: " + media.sceneId);
                }
            }
            default -> {
            }
        }
    }

    private void validateCondition(GuideValidationReport report, ConditionSpec condition, String path) {
        if (condition == null || condition.isAlways()) {
            return;
        }
        String type = condition.condition;
        if (blank(type)) {
            report.add(GuideValidationIssue.Severity.ERROR, path + ".condition", "Condition type is required");
            return;
        }
        if (type.startsWith("minecraft:")) {
            return;
        }
        switch (type) {
            case "arc_quest:always" -> {
            }
            case "arc_quest:quest_completed", "arc_quest:quest_accepted", "arc_quest:quest_not_started" ->
                    require(report, condition.questId, path + ".questId", type + " requires questId");
            case "arc_quest:quest_phase", "arc_quest:quest_phase_completed", "arc_quest:quest_phase_reached" -> {
                require(report, condition.questId, path + ".questId", type + " requires questId");
                require(report, condition.phaseId, path + ".phaseId", type + " requires phaseId");
            }
            case "arc_quest:has_flag", "arc_quest:not_has_flag" ->
                    require(report, condition.flag, path + ".flag", type + " requires flag");
            case "arc_quest:variable_check" -> {
                require(report, condition.key, path + ".key", "variable_check requires key");
                require(report, condition.op, path + ".op", "variable_check requires op");
            }
            case "arc_quest:and", "arc_quest:or" -> {
                if (empty(condition.conditions)) {
                    report.add(GuideValidationIssue.Severity.ERROR, path + ".conditions", type + " requires conditions list");
                } else {
                    for (int i = 0; i < condition.conditions.size(); i++) {
                        validateCondition(report, condition.conditions.get(i), path + ".conditions[" + i + "]");
                    }
                }
            }
            case "arc_quest:not" -> {
                if (condition.inner == null) {
                    report.add(GuideValidationIssue.Severity.ERROR, path + ".inner", "not requires inner condition");
                } else {
                    validateCondition(report, condition.inner, path + ".inner");
                }
            }
            default -> report.add(GuideValidationIssue.Severity.ERROR, path + ".condition", "Unsupported condition type: " + type);
        }
    }

    private String normalizeMediaType(String raw) {
        return blank(raw) ? "" : raw.trim().toLowerCase();
    }

    private void require(GuideValidationReport report, String value, String path, String message) {
        if (blank(value)) {
            report.add(GuideValidationIssue.Severity.ERROR, path, message);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean empty(List<?> values) {
        return values == null || values.isEmpty();
    }
}
