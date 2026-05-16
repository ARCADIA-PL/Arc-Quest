package org.arcadia.arc_quest.trade.spec.compile;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
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
        List<ITradeOffer> costs = compileOffers(spec.costs, true);
        List<ITradeOffer> rewards = compileOffers(spec.rewards, false);

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
                null,
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

    private List<ITradeOffer> compileOffers(List<TradeOfferSpec> specs, boolean isCost) {
        List<ITradeOffer> offers = new ArrayList<>();
        if (specs == null) return offers;

        for (TradeOfferSpec spec : specs) {
            ITradeOffer offer = compileOffer(spec, isCost);
            if (offer != null) {
                offers.add(offer);
            }
        }
        return offers;
    }

    private ITradeOffer compileOffer(TradeOfferSpec spec, boolean isCost) {
        if (spec == null || spec.type == null) return null;

        return switch (spec.type) {
            case "item" -> {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(spec.itemId));
                if (item == null) throw new TradeCompileException("Unknown item: " + spec.itemId);
                yield new ItemTradeOffer(item, spec.count, isCost);
            }
            case "command" -> new CommandTradeOffer(spec.command);
            case "effect" -> {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(spec.effectId));
                if (effect == null) throw new TradeCompileException("Unknown effect: " + spec.effectId);
                yield new EffectTradeOffer(effect, spec.duration, spec.amplifier, isCost);
            }
            case "flag" -> new FlagTradeOffer(spec.flagName, isCost);
            case "composite" -> new CompositeTradeOffer(compileOffers(spec.offers, isCost));
            default -> throw new TradeCompileException("Unknown offer type: " + spec.type);
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
        return ForgeRegistries.SOUND_EVENTS.getValue(rl);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
