package org.arcadia.arc_quest.trade.builder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.tags.TagKey;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.offer.CommandTradeOffer;
import org.arcadia.arc_quest.trade.offer.EffectTradeOffer;
import org.arcadia.arc_quest.trade.offer.FlagTradeOffer;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class TradeEntryBuilder {

    private final String entryId;
    private final List<ITradeOffer> costs = new ArrayList<>();
    private final List<ITradeOffer> rewards = new ArrayList<>();
    private Component displayName;
    @Nullable private Component description;
    @Nullable private TradeCategory category;
    @Nullable private ICondition visibleCondition;
    @Nullable private ICondition canBuyCondition;
    private CooldownType cooldownType = CooldownType.NONE;
    private long cooldownValue = 0;
    private int resetTimeTicks = 0;
    private int maxPurchases = -1;
    @Nullable private ResourceLocation rewardIcon;
    @Nullable private ResourceLocation costIcon;
    private int sortOrder = 0;
    private int themeColor = -1;
    @Nullable private Predicate<ServerPlayer> purchaseResetCondition;
    @Nullable private SoundEvent purchaseSuccessSound;
    @Nullable private SoundEvent purchaseFailSound;
    @Nullable private SoundEvent cooldownSound;
    @Nullable private SoundEvent limitReachedSound;
    @Nullable private SoundEvent conditionFailSound;

    private TradeEntryBuilder(String entryId) {
        this.entryId = Objects.requireNonNull(entryId);
    }

    public static TradeEntryBuilder create(String entryId) {
        return new TradeEntryBuilder(entryId);
    }

    public TradeEntryBuilder displayName(String literal) { this.displayName = Component.literal(literal); return this; }
    public TradeEntryBuilder displayName(Component name) { this.displayName = name; return this; }
    public TradeEntryBuilder description(String literal) { this.description = Component.literal(literal); return this; }
    public TradeEntryBuilder description(Component desc) { this.description = desc; return this; }
    public TradeEntryBuilder category(TradeCategory cat) { this.category = cat; return this; }
    public TradeEntryBuilder sortOrder(int order) { this.sortOrder = order; return this; }
    public TradeEntryBuilder rewardIcon(ResourceLocation texture) { this.rewardIcon = texture; return this; }
    public TradeEntryBuilder costIcon(ResourceLocation texture) { this.costIcon = texture; return this; }

    public TradeEntryBuilder themeColor(int color) { this.themeColor = color; return this; }
    public TradeEntryBuilder themeColor(ChatFormatting formatting) {
        Integer rgb = formatting.getColor();
        if (rgb != null) this.themeColor = 0xFF000000 | rgb;
        return this;
    }
    public TradeEntryBuilder themeColorRGB(int r, int g, int b) {
        this.themeColor = (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        return this;
    }

    public TradeEntryBuilder cost(ITradeOffer offer) { this.costs.add(offer); return this; }
    public TradeEntryBuilder costItem(Item item, int count) { this.costs.add(ItemTradeOffer.cost(item, count)); return this; }
    public TradeEntryBuilder costItem(Item item, int count, ResourceLocation icon) {
        this.costs.add(new ItemTradeOffer(item, count, true, icon));
        return this;
    }
    public TradeEntryBuilder costItemTag(TagKey<Item> itemTag, int count) {
        this.costs.add(ItemTradeOffer.costTag(itemTag, count));
        return this;
    }
    public TradeEntryBuilder costItemTag(TagKey<Item> itemTag, int count, ResourceLocation icon) {
        this.costs.add(new ItemTradeOffer(itemTag, count, true, icon));
        return this;
    }
    public TradeEntryBuilder costItemDynamic(Item item, int previewCount, ToIntFunction<ServerPlayer> resolver) {
        this.costs.add(ItemTradeOffer.cost(item, previewCount, resolver));
        return this;
    }
    public TradeEntryBuilder costItemTagDynamic(TagKey<Item> itemTag, int previewCount, ToIntFunction<ServerPlayer> resolver) {
        this.costs.add(ItemTradeOffer.costTag(itemTag, previewCount, resolver));
        return this;
    }
    public TradeEntryBuilder costFlag(String flag) { this.costs.add(FlagTradeOffer.requireFlag(flag)); return this; }

    public TradeEntryBuilder reward(ITradeOffer offer) { this.rewards.add(offer); return this; }
    public TradeEntryBuilder rewardItem(Item item, int count) { this.rewards.add(ItemTradeOffer.reward(item, count)); return this; }
    public TradeEntryBuilder rewardEffect(MobEffect effect, int durationSeconds, int amplifier) {
        this.rewards.add(EffectTradeOffer.reward(effect, durationSeconds * 20, amplifier));
        return this;
    }
    public TradeEntryBuilder rewardEffect(MobEffect effect, int durationSeconds) {
        this.rewards.add(EffectTradeOffer.reward(effect, durationSeconds));
        return this;
    }
    public TradeEntryBuilder rewardFlag(String flag) { this.rewards.add(FlagTradeOffer.rewardFlag(flag)); return this; }
    public TradeEntryBuilder rewardCommand(String command, String displayText) {
        this.rewards.add(CommandTradeOffer.reward(command, displayText));
        return this;
    }

    public TradeEntryBuilder visibleCondition(ICondition cond) { this.visibleCondition = cond; return this; }
    public TradeEntryBuilder canBuyCondition(ICondition cond) { this.canBuyCondition = cond; return this; }
    public TradeEntryBuilder condition(ICondition cond) { this.visibleCondition = cond; this.canBuyCondition = cond; return this; }

    public TradeEntryBuilder maxPurchases(int max) { this.maxPurchases = max; return this; }
    public TradeEntryBuilder unlimited() { this.maxPurchases = -1; return this; }
    public TradeEntryBuilder purchaseResetCondition(Predicate<ServerPlayer> condition) {
        this.purchaseResetCondition = condition;
        return this;
    }
    public TradeEntryBuilder purchaseResetByCooldown() {
        this.purchaseResetCondition = player -> false;
        return this;
    }

    public TradeEntryBuilder cooldown(long seconds) { this.cooldownType = CooldownType.SECONDS; this.cooldownValue = Math.max(0, seconds); return this; }
    public TradeEntryBuilder cooldownGameDay() { this.cooldownType = CooldownType.GAME_DAY; this.cooldownValue = 1; return this; }
    public TradeEntryBuilder cooldownGameTick(int resetTick) {
        this.cooldownType = CooldownType.GAME_TICK;
        this.cooldownValue = 0;
        this.resetTimeTicks = Math.max(0, Math.min(resetTick, 24000));
        return this;
    }

    public TradeEntryBuilder purchaseSuccessSound(SoundEvent sound) { this.purchaseSuccessSound = sound; return this; }
    public TradeEntryBuilder purchaseSuccessSound(Holder.Reference<SoundEvent> sound) { this.purchaseSuccessSound = sound.get(); return this; }
    public TradeEntryBuilder purchaseFailSound(SoundEvent sound) { this.purchaseFailSound = sound; return this; }
    public TradeEntryBuilder purchaseFailSound(Holder.Reference<SoundEvent> sound) { this.purchaseFailSound = sound.get(); return this; }
    public TradeEntryBuilder cooldownSound(SoundEvent sound) { this.cooldownSound = sound; return this; }
    public TradeEntryBuilder cooldownSound(Holder.Reference<SoundEvent> sound) { this.cooldownSound = sound.get(); return this; }
    public TradeEntryBuilder limitReachedSound(SoundEvent sound) { this.limitReachedSound = sound; return this; }
    public TradeEntryBuilder limitReachedSound(Holder.Reference<SoundEvent> sound) { this.limitReachedSound = sound.get(); return this; }
    public TradeEntryBuilder conditionFailSound(SoundEvent sound) { this.conditionFailSound = sound; return this; }
    public TradeEntryBuilder conditionFailSound(Holder.Reference<SoundEvent> sound) { this.conditionFailSound = sound.get(); return this; }

    public TradeEntry build() {
        if (displayName == null) displayName = Component.literal(entryId);
        if (rewards.isEmpty()) throw new IllegalStateException("TradeEntry '" + entryId + "' has no rewards defined");
        return new TradeEntry(
                entryId, displayName, description,
                List.copyOf(costs), List.copyOf(rewards),
                category, visibleCondition, canBuyCondition,
                cooldownType, cooldownValue, resetTimeTicks,
                maxPurchases, rewardIcon, costIcon, sortOrder, themeColor,
                purchaseResetCondition, purchaseSuccessSound, purchaseFailSound,
                cooldownSound, limitReachedSound, conditionFailSound
        );
    }
}
