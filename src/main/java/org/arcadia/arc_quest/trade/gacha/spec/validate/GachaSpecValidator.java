package org.arcadia.arc_quest.trade.gacha.spec.validate;

import org.arcadia.arc_quest.trade.gacha.spec.*;
import org.arcadia.arc_quest.trade.spec.TradeOfferSpec;
import org.arcadia.arc_quest.trade.spec.validate.TradeSpecValidator;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class GachaSpecValidator {

    private static final Set<String> VALID_RARITIES = Set.of("LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON");
    private static final Set<String> VALID_COOLDOWN_TYPES = Set.of("NONE", "SECONDS", "GAME_DAY", "GAME_TICK");

    public GachaValidationReport validate(GachaShopSpec spec) {
        GachaValidationReport report = new GachaValidationReport();

        if (spec == null) {
            report.add(GachaValidationIssue.Severity.ERROR, "gacha", "GachaShopSpec is null");
            return report;
        }

        if (spec.shopId == null || spec.shopId.isBlank()) {
            report.add(GachaValidationIssue.Severity.ERROR, "shopId", "shopId is required");
        }

        if (spec.displayName == null || spec.displayName.value == null || spec.displayName.value.isBlank()) {
            report.add(GachaValidationIssue.Severity.ERROR, "displayName", "displayName is required");
        }

        if (spec.cooldownType != null && !spec.cooldownType.isBlank() && !VALID_COOLDOWN_TYPES.contains(spec.cooldownType)) {
            report.add(GachaValidationIssue.Severity.ERROR, "cooldownType", "Invalid cooldownType: " + spec.cooldownType);
        }

        boolean hasDrawCost = (spec.drawCosts != null && !spec.drawCosts.isEmpty()) || spec.drawCost != null;
        if (!hasDrawCost) {
            report.add(GachaValidationIssue.Severity.ERROR, "drawCost", "At least one drawCost or drawCosts entry is required");
        } else if (spec.drawCosts != null && !spec.drawCosts.isEmpty()) {
            for (int i = 0; i < spec.drawCosts.size(); i++) {
                TradeOfferSpec offer = spec.drawCosts.get(i);
                String prefix = "drawCosts[" + i + "]";
                if (offer.type == null || !TradeSpecValidator.VALID_OFFER_TYPES.contains(offer.type)) {
                    report.add(GachaValidationIssue.Severity.ERROR, prefix + ".type", "Invalid offer type: " + offer.type);
                } else if ("item".equals(offer.type) && (offer.itemId == null || offer.itemId.isBlank())) {
                    report.add(GachaValidationIssue.Severity.ERROR, prefix + ".itemId", "itemId is required");
                }
            }
        } else if (spec.drawCost != null) {
            if (spec.drawCost.type == null || !TradeSpecValidator.VALID_OFFER_TYPES.contains(spec.drawCost.type)) {
                report.add(GachaValidationIssue.Severity.ERROR, "drawCost.type", "Invalid offer type: " + spec.drawCost.type);
            } else if ("item".equals(spec.drawCost.type) && (spec.drawCost.itemId == null || spec.drawCost.itemId.isBlank())) {
                report.add(GachaValidationIssue.Severity.ERROR, "drawCost.itemId", "itemId is required");
            }
        }

        if (spec.rarities == null || spec.rarities.isEmpty()) {
            report.add(GachaValidationIssue.Severity.ERROR, "rarities", "At least one rarity is required");
        } else {
            Set<String> rarityNames = new HashSet<>();
            for (int i = 0; i < spec.rarities.size(); i++) {
                GachaRaritySpec r = spec.rarities.get(i);
                String prefix = "rarities[" + i + "]";
                if (r.rarity == null || !VALID_RARITIES.contains(r.rarity)) {
                    report.add(GachaValidationIssue.Severity.ERROR, prefix + ".rarity", "Invalid rarity: " + r.rarity);
                } else if (!rarityNames.add(r.rarity)) {
                    report.add(GachaValidationIssue.Severity.ERROR, prefix + ".rarity", "Duplicate rarity: " + r.rarity);
                }
            }
        }

        if (spec.pity != null && spec.pity.threshold <= 0) {
            report.add(GachaValidationIssue.Severity.ERROR, "pity.threshold", "threshold must be > 0");
        }

        if (spec.pools == null || spec.pools.isEmpty()) {
            report.add(GachaValidationIssue.Severity.ERROR, "pools", "At least one pool is required");
        } else {
            Set<String> rarityNames = spec.rarities != null
                    ? spec.rarities.stream().map(r -> r.rarity).collect(Collectors.toSet())
                    : Set.of();
            for (int pi = 0; pi < spec.pools.size(); pi++) {
                GachaPoolSpec pool = spec.pools.get(pi);
                String poolPrefix = "pools[" + pi + "]";
                if (pool.items == null || pool.items.isEmpty()) {
                    report.add(GachaValidationIssue.Severity.ERROR, poolPrefix + ".items", "Pool has no items");
                    continue;
                }
                Set<String> itemIds = new HashSet<>();
                for (int ii = 0; ii < pool.items.size(); ii++) {
                    GachaItemSpec item = pool.items.get(ii);
                    String prefix = poolPrefix + ".items[" + ii + "]";

                    if (item.itemId == null || item.itemId.isBlank()) {
                        report.add(GachaValidationIssue.Severity.ERROR, prefix + ".itemId", "itemId is required");
                    } else if (!itemIds.add(item.itemId)) {
                        report.add(GachaValidationIssue.Severity.ERROR, prefix + ".itemId", "Duplicate itemId: " + item.itemId);
                    }

                    if (item.item == null || item.item.isBlank()) {
                        report.add(GachaValidationIssue.Severity.ERROR, prefix + ".item", "item is required");
                    }

                    if (item.weight <= 0) {
                        report.add(GachaValidationIssue.Severity.ERROR, prefix + ".weight", "weight must be > 0");
                    }

                    if (item.rarity != null && !item.rarity.isBlank() && !rarityNames.isEmpty() && !rarityNames.contains(item.rarity)) {
                        report.add(GachaValidationIssue.Severity.WARN, prefix + ".rarity",
                                "Rarity '" + item.rarity + "' not in rarities list");
                    }
                }
            }
        }

        return report;
    }
}
