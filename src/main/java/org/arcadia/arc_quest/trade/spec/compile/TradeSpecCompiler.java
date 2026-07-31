package org.arcadia.arc_quest.trade.spec.compile;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import org.arcadia.arc_quest.condition.ConditionBridge;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.dialogue.spec.DialogueTextSpec;
import org.arcadia.arc_quest.trade.api.*;
import org.arcadia.arc_quest.trade.offer.*;
import org.arcadia.arc_quest.trade.spec.*;
import org.arcadia.arc_quest.trade.spec.validate.TradeSpecValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TradeSpecCompiler {

    private final TradeSpecValidator validator = new TradeSpecValidator();

    public TradeShopDefinition compile(TradeShopSpec spec) {
        var report = validator.validate(spec);
        if (report.hasErrors()) {
            throw new TradeCompileException("TradeSpec validation failed for '" + (spec == null ? "null" : spec.shopId) + "'");
        }

        List<TradeCategory> categories = compileCategories(spec.categories);

        LinkedHashMap<String, TradeEntry> entries = new LinkedHashMap<>();
        for (Map.Entry<String, TradeEntrySpec> e : spec.entries.entrySet()) {
            entries.put(e.getKey(), compileEntry(e.getValue(), categories));
        }

        SoundEvent openSound = parseNullableSound(spec.openSound);
        SoundEvent closeSound = parseNullableSound(spec.closeSound);

        return new TradeShopDefinition(
                spec.shopId,
                compileTradeText(spec.displayName),
                spec.description != null ? compileTradeText(spec.description) : null,
                categories,
                entries,
                ConditionBridge.toQuestCondition(spec.openCondition),
                spec.simpleMode,
                spec.themeColor,
                openSound,
                closeSound
        );
    }

    private List<TradeCategory> compileCategories(List<TradeCategorySpec> specs) {
        List<TradeCategory> categories = new ArrayList<>();
        if (specs == null) return categories;

        for (TradeCategorySpec spec : specs) {
            int color = 0xFFFFFFFF;
            if (spec.formatting != null && !spec.formatting.isBlank()) {
                ChatFormatting fmt = ChatFormatting.getByName(spec.formatting);
                if (fmt != null && fmt.getColor() != null) {
                    color = 0xFF000000 | fmt.getColor();
                }
            }
            categories.add(new TradeCategory(
                    spec.categoryId,
                    compileTradeText(spec.displayName).resolveFallback(),
                    spec.sortOrder,
                    color
            ));
        }
        return categories;
    }

    private TradeEntry compileEntry(TradeEntrySpec spec, List<TradeCategory> categories) {
        List<ITradeOffer> costs = TradeOfferSpecCompiler.compileOffers(spec.costs, true);
        List<ITradeOffer> rewards = TradeOfferSpecCompiler.compileOffers(spec.rewards, false);

        TradeCategory category = resolveCategory(spec.category, categories);

        SoundEvent purchaseSuccessSound = parseNullableSound(spec.purchaseSuccessSound);
        SoundEvent purchaseFailSound = parseNullableSound(spec.purchaseFailSound);
        SoundEvent cooldownSound = parseNullableSound(spec.cooldownSound);
        SoundEvent limitReachedSound = parseNullableSound(spec.limitReachedSound);
        SoundEvent conditionFailSound = parseNullableSound(spec.conditionFailSound);

        return new TradeEntry(
                spec.entryId,
                compileTradeText(spec.displayName),
                spec.description != null ? compileTradeText(spec.description) : null,
                costs,
                rewards,
                category,
                ConditionBridge.toQuestCondition(spec.visibleCondition),
                ConditionBridge.toQuestCondition(spec.canBuyCondition),
                parseCooldownType(spec.cooldownType),
                spec.cooldownValue,
                spec.resetTimeTicks,
                spec.maxPurchases,
                blankToNull(spec.rewardIcon) != null ? ResourceLocation.tryParse(spec.rewardIcon) : null,
                blankToNull(spec.costIcon) != null ? ResourceLocation.tryParse(spec.costIcon) : null,
                spec.sortOrder,
                spec.themeColor,
                compileResetCondition(spec.purchaseResetCondition),
                purchaseSuccessSound,
                purchaseFailSound,
                cooldownSound,
                limitReachedSound,
                conditionFailSound
        );
    }

    private TradeCategory resolveCategory(String categoryId, List<TradeCategory> categories) {
        if (categoryId == null || categoryId.isBlank()) return null;
        return categories.stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst().orElse(null);
    }

    private java.util.function.Predicate<net.minecraft.server.level.ServerPlayer> compileResetCondition(
            org.arcadia.arc_quest.condition.ConditionSpec spec) {
        var condition = ConditionBridge.toQuestCondition(spec);
        if (condition == null) return null;
        return player -> {
            var data = org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager.get(player);
            if (data == null) return false;
            return condition.test(player, data.getCompletedQuestLocations(), data.getAllFlags(), data.getAllVariables());
        };
    }

    private TradeText compileTradeText(DialogueTextSpec spec) {
        if (spec == null) return TradeText.literal("");
        if ("translatable".equals(spec.mode)) {
            return TradeText.translatable(spec.value);
        }
        return TradeText.literal(spec.value);
    }

    private CooldownType parseCooldownType(String type) {
        if (type == null || type.isBlank()) return CooldownType.NONE;
        return switch (type.toUpperCase()) {
            case "SECONDS" -> CooldownType.SECONDS;
            case "GAME_DAY" -> CooldownType.GAME_DAY;
            case "GAME_TICK" -> CooldownType.GAME_TICK;
            default -> CooldownType.NONE;
        };
    }

    private SoundEvent parseNullableSound(String id) {
        if (id == null || id.isBlank()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        return BuiltInRegistries.SOUND_EVENT.get(rl);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
