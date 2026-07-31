package org.arcadia.arc_quest.trade.gacha.spec.compile;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.dialogue.spec.DialogueTextSpec;
import org.arcadia.arc_quest.condition.ConditionBridge;
import org.arcadia.arc_quest.trade.api.*;
import org.arcadia.arc_quest.trade.gacha.api.*;
import org.arcadia.arc_quest.trade.gacha.spec.*;
import org.arcadia.arc_quest.trade.gacha.spec.validate.GachaSpecValidator;
import org.arcadia.arc_quest.trade.offer.*;
import org.arcadia.arc_quest.trade.spec.TradeCategorySpec;
import org.arcadia.arc_quest.trade.spec.TradeOfferSpec;
import org.arcadia.arc_quest.trade.spec.compile.TradeOfferSpecCompiler;

import java.util.*;

import static org.arcadia.arc_quest.trade.spec.validate.TradeSpecValidator.VALID_OFFER_TYPES;

public final class GachaSpecCompiler {

    private final GachaSpecValidator validator = new GachaSpecValidator();

    public GachaShopDefinition compile(GachaShopSpec spec) {
        var report = validator.validate(spec);
        if (report.hasErrors()) {
            throw new GachaCompileException("GachaSpec validation failed for '" + (spec == null ? "null" : spec.shopId) + "'");
        }

        List<TradeCategory> categories = compileCategories(spec.categories);

        LinkedHashMap<String, TradeEntry> entries = new LinkedHashMap<>();

        GachaPool gachaPool = compilePool(spec.pools);

        List<ITradeOffer> drawCosts;
        if (spec.drawCosts != null && !spec.drawCosts.isEmpty()) {
            drawCosts = TradeOfferSpecCompiler.compileOffers(spec.drawCosts, true);
        } else if (spec.drawCost != null) {
            drawCosts = List.of(TradeOfferSpecCompiler.compileOffer(spec.drawCost, true));
        } else {
            drawCosts = List.of();
        }

        PityConfig pityConfig = null;
        if (spec.pity != null) {
            pityConfig = compilePity(spec.pity);
        }

        GachaShopDefinition def = new GachaShopDefinition(
                spec.shopId,
                compileTradeText(spec.displayName),
                spec.description != null ? compileTradeText(spec.description) : null,
                categories,
                entries,
                ConditionBridge.toQuestCondition(spec.openCondition),
                spec.simpleMode,
                spec.themeColor,
                parseNullableSound(spec.openSound),
                parseNullableSound(spec.closeSound),
                gachaPool,
                drawCosts,
                parseCooldownType(spec.cooldownType),
                spec.cooldownValue,
                spec.resetTimeTicks,
                ConditionBridge.toQuestCondition(spec.drawCondition),
                spec.maxDraws,
                ConditionBridge.toQuestCondition(spec.resetCondition),
                spec.resetOnLimitReached,
                spec.resetPityOnEarlyTrigger,
                pityConfig,
                parseNullableSound(spec.drawCooldownSound),
                parseNullableSound(spec.drawLimitReachedSound),
                parseNullableSound(spec.drawConditionFailSound),
                parseNullableSound(spec.drawFailSound)
        );

        if (spec.rarities != null) {
            for (GachaRaritySpec r : spec.rarities) {
                GachaItem.Rarity rarity = GachaItem.Rarity.valueOf(r.rarity);
                def.setRarityConfig(rarity, r.color, parseNullableSound(r.drawSuccessSound));
            }
        }

        return def;
    }

    private List<TradeCategory> compileCategories(List<? extends TradeCategorySpec> specs) {
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

    private GachaPool compilePool(List<GachaPoolSpec> specs) {
        List<GachaItem> allItems = new ArrayList<>();
        if (specs == null) return new GachaPool(allItems);

        for (GachaPoolSpec poolSpec : specs) {
            if (poolSpec.items == null) continue;
            for (GachaItemSpec itemSpec : poolSpec.items) {
                GachaItem item = compileGachaItem(itemSpec);
                if (item != null) {
                    allItems.add(item);
                }
            }
        }
        return new GachaPool(allItems);
    }

    private GachaItem compileGachaItem(GachaItemSpec spec) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(spec.item));
        if (item == null) throw new GachaCompileException("Unknown item: " + spec.item);

        GachaItem.Rarity rarity = parseRarity(spec.rarity);

        ITradeOffer reward = spec.reward != null
                ? TradeOfferSpecCompiler.compileOffer(spec.reward, false)
                : new ItemTradeOffer(item, spec.minCount, false);
        GachaItem result = new GachaItem(
                spec.itemId,
                new ItemStack(item, spec.maxCount),
                reward,
                spec.weight,
                rarity,
                spec.countsTowardsPity,
                spec.minCount,
                spec.maxCount,
                ConditionBridge.toQuestCondition(spec.visibleCondition),
                blankToNull(spec.rewardIcon) != null ? ResourceLocation.tryParse(spec.rewardIcon) : null,
                spec.themeColor,
                parseNullableSound(spec.drawSuccessSound),
                spec.sortOrder
        );
        if (spec.weightModifiers != null) {
            for (GachaWeightModifierSpec modifier : spec.weightModifiers) {
                var condition = ConditionBridge.toQuestCondition(modifier.condition);
                if (condition != null) result.addWeightModifier(condition, modifier.weightDelta);
            }
        }
        return result;
    }

    private GachaItem.Rarity parseRarity(String rarity) {
        if (rarity == null || rarity.isBlank()) return GachaItem.Rarity.RARE;
        try {
            return GachaItem.Rarity.valueOf(rarity.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GachaItem.Rarity.RARE;
        }
    }

    private PityConfig compilePity(PityConfigSpec spec) {
        GachaItem.Rarity targetRarity = parseRarity(spec.targetRarity);
        return new PityConfig(
                spec.threshold,
                spec.guaranteedItemId != null && !spec.guaranteedItemId.isBlank() ? spec.guaranteedItemId : null,
                spec.guaranteedItemId != null && !spec.guaranteedItemId.isBlank() ? null : targetRarity,
                parseCooldownType(spec.resetCooldownType),
                spec.resetCooldownValue,
                ConditionBridge.toQuestCondition(spec.resetCondition),
                spec.resetOnTrigger
        );
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
