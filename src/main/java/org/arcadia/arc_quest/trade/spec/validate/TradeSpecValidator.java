package org.arcadia.arc_quest.trade.spec.validate;

import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.trade.spec.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class TradeSpecValidator {

    public static final Set<String> VALID_OFFER_TYPES = Set.of("item", "command", "effect", "flag", "composite");
    private static final Set<String> VALID_COOLDOWN_TYPES = Set.of("NONE", "SECONDS", "GAME_DAY", "GAME_TICK");

    public TradeValidationReport validate(TradeShopSpec spec) {
        TradeValidationReport report = new TradeValidationReport();

        if (spec == null) {
            report.add(TradeValidationIssue.Severity.ERROR, "trade", "TradeShopSpec is null");
            return report;
        }

        if (spec.shopId == null || spec.shopId.isBlank()) {
            report.add(TradeValidationIssue.Severity.ERROR, "shopId", "shopId is required");
        }

        if (spec.displayName == null || spec.displayName.value == null || spec.displayName.value.isBlank()) {
            report.add(TradeValidationIssue.Severity.ERROR, "displayName", "displayName is required");
        }

        if (spec.categories != null) {
            Set<String> categoryIds = new HashSet<>();
            for (int ci = 0; ci < spec.categories.size(); ci++) {
                TradeCategorySpec cat = spec.categories.get(ci);
                if (cat.categoryId == null || cat.categoryId.isBlank()) {
                    report.add(TradeValidationIssue.Severity.ERROR, "categories[" + ci + "].categoryId", "categoryId is required");
                } else if (!categoryIds.add(cat.categoryId)) {
                    report.add(TradeValidationIssue.Severity.ERROR, "categories[" + ci + "].categoryId", "Duplicate categoryId: " + cat.categoryId);
                }
            }
        }

        if (spec.openCondition != null) {
            validateCondition(report, spec.openCondition, "openCondition");
        }

        if (spec.entries == null || spec.entries.isEmpty()) {
            report.add(TradeValidationIssue.Severity.WARN, "entries", "Trade shop has no entries");
        } else {
            Set<String> entryIds = new HashSet<>();
            for (var entry : spec.entries.entrySet()) {
                String mapKey = entry.getKey();
                TradeEntrySpec e = entry.getValue();
                if (e == null) continue;
                String prefix = "entries." + mapKey;

                if (e.entryId == null || e.entryId.isBlank()) {
                    report.add(TradeValidationIssue.Severity.ERROR, prefix + ".entryId", "entryId is required");
                } else if (!entryIds.add(e.entryId)) {
                    report.add(TradeValidationIssue.Severity.ERROR, prefix + ".entryId", "Duplicate entryId: " + e.entryId);
                }

                if (e.rewards == null || e.rewards.isEmpty()) {
                    report.add(TradeValidationIssue.Severity.ERROR, prefix + ".rewards", "At least one reward is required");
                }

                if (e.costs != null) {
                    validateOffers(report, e.costs, prefix + ".costs");
                }

                if (e.rewards != null) {
                    validateOffers(report, e.rewards, prefix + ".rewards");
                }

                if (e.category != null && !e.category.isBlank()) {
                    Set<String> catIds = spec.categories != null
                            ? spec.categories.stream().map(c -> c.categoryId).collect(Collectors.toSet())
                            : Set.of();
                    if (!catIds.contains(e.category)) {
                        report.add(TradeValidationIssue.Severity.WARN, prefix + ".category",
                                "Category '" + e.category + "' not found in categories, prefix = " + prefix + ", available: " + catIds
                        );
                    }
                }

                if (e.visibleCondition != null) {
                    validateCondition(report, e.visibleCondition, prefix + ".visibleCondition");
                }

                if (e.canBuyCondition != null) {
                    validateCondition(report, e.canBuyCondition, prefix + ".canBuyCondition");
                }

                if (e.cooldownType != null && !e.cooldownType.isBlank() && !VALID_COOLDOWN_TYPES.contains(e.cooldownType)) {
                    report.add(TradeValidationIssue.Severity.ERROR, prefix + ".cooldownType",
                            "Invalid cooldownType: " + e.cooldownType);
                }
            }
        }

        return report;
    }

    private void validateOffers(TradeValidationReport report, List<TradeOfferSpec> offers, String prefix) {
        for (int i = 0; i < offers.size(); i++) {
            TradeOfferSpec offer = offers.get(i);
            String p = prefix + "[" + i + "]";

            if (offer.type == null || !VALID_OFFER_TYPES.contains(offer.type)) {
                report.add(TradeValidationIssue.Severity.ERROR, p + ".type", "Invalid offer type: " + offer.type);
                continue;
            }

            switch (offer.type) {
                case "item":
                    if (offer.itemId == null || offer.itemId.isBlank()) {
                        report.add(TradeValidationIssue.Severity.ERROR, p + ".itemId", "itemId is required for item offer");
                    }
                    break;
                case "command":
                    if (offer.command == null || offer.command.isBlank()) {
                        report.add(TradeValidationIssue.Severity.ERROR, p + ".command", "command is required for command offer");
                    }
                    break;
                case "effect":
                    if (offer.effectId == null || offer.effectId.isBlank()) {
                        report.add(TradeValidationIssue.Severity.ERROR, p + ".effectId", "effectId is required for effect offer");
                    }
                    break;
                case "flag":
                    if (offer.flagName == null || offer.flagName.isBlank()) {
                        report.add(TradeValidationIssue.Severity.ERROR, p + ".flagName", "flagName is required for flag offer");
                    }
                    break;
                case "composite":
                    if (offer.offers == null || offer.offers.isEmpty()) {
                        report.add(TradeValidationIssue.Severity.ERROR, p + ".offers", "At least one sub-offer is required for composite offer");
                    } else {
                        validateOffers(report, offer.offers, p + ".offers");
                    }
                    break;
            }
        }
    }

    private void validateCondition(TradeValidationReport report, ConditionSpec condition, String prefix) {
        if (condition == null) return;
        if (condition.condition != null && !condition.condition.isEmpty()
                && !"arc_quest:always".equals(condition.condition)) {
            if (condition.inner != null) {
                validateCondition(report, condition.inner, prefix + ".inner");
            }
            if (condition.conditions != null) {
                for (int i = 0; i < condition.conditions.size(); i++) {
                    validateCondition(report, condition.conditions.get(i), prefix + ".conditions[" + i + "]");
                }
            }
        }
    }
}
