package org.arcadia.arc_quest.trade.spec.compile;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.offer.CommandTradeOffer;
import org.arcadia.arc_quest.trade.offer.CompositeTradeOffer;
import org.arcadia.arc_quest.trade.offer.EffectTradeOffer;
import org.arcadia.arc_quest.trade.offer.FlagTradeOffer;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.arcadia.arc_quest.trade.spec.TradeOfferSpec;

import java.util.ArrayList;
import java.util.List;

public final class TradeOfferSpecCompiler {

    private TradeOfferSpecCompiler() {
    }

    public static List<ITradeOffer> compileOffers(List<TradeOfferSpec> specs, boolean isCost) {
        List<ITradeOffer> offers = new ArrayList<>();
        if (specs == null) return offers;
        for (TradeOfferSpec spec : specs) {
            ITradeOffer offer = compileOffer(spec, isCost);
            if (offer != null) offers.add(offer);
        }
        return offers;
    }

    public static ITradeOffer compileOffer(TradeOfferSpec spec, boolean isCost) {
        if (spec == null || spec.type == null) return null;
        return switch (spec.type) {
            case "item" -> compileItem(spec, isCost);
            case "command" -> new CommandTradeOffer(spec.command, "player".equalsIgnoreCase(spec.executeAs));
            case "effect" -> new EffectTradeOffer(requireEffect(spec.effectId), spec.duration, spec.amplifier, isCost);
            case "flag" -> new FlagTradeOffer(spec.flagName, isCost);
            case "composite" -> new CompositeTradeOffer(compileOffers(spec.offers, isCost));
            default -> throw new TradeCompileException("Unknown offer type: " + spec.type);
        };
    }

    private static ITradeOffer compileItem(TradeOfferSpec spec, boolean isCost) {
        ResourceLocation customIcon = parseNullableId(spec.customIcon, "customIcon");
        if (spec.itemTag != null && !spec.itemTag.isBlank()) {
            if (!isCost) throw new TradeCompileException("Item tags are only supported for costs");
            return new ItemTradeOffer(TagKey.create(Registries.ITEM, requireId(spec.itemTag, "itemTag")),
                    spec.count, true, customIcon);
        }

        Item item = requireItem(spec.itemId);
        if (spec.nbt == null || spec.nbt.isBlank()) {
            return new ItemTradeOffer(item, spec.count, isCost, customIcon);
        }
        try {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(TagParser.parseTag(spec.nbt)));
            return new ItemTradeOffer(stack, spec.count, isCost, customIcon);
        } catch (Exception ex) {
            throw new TradeCompileException("Invalid item nbt for " + spec.itemId + ": " + ex.getMessage());
        }
    }

    private static Item requireItem(String value) {
        ResourceLocation id = requireId(value, "itemId");
        return BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new TradeCompileException("Unknown item: " + value));
    }

    private static MobEffect requireEffect(String value) {
        ResourceLocation id = requireId(value, "effectId");
        return BuiltInRegistries.MOB_EFFECT.getOptional(id)
                .orElseThrow(() -> new TradeCompileException("Unknown effect: " + value));
    }

    private static ResourceLocation requireId(String value, String field) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new TradeCompileException("Invalid " + field + ": " + value);
        return id;
    }

    private static ResourceLocation parseNullableId(String value, String field) {
        return value == null || value.isBlank() ? null : requireId(value, field);
    }
}
