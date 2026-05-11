package org.arcadia.arc_quest.trade.builder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeText;
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
    private TradeText displayNameText;
    @Nullable
    private TradeText descriptionText;
    @Nullable
    private TradeCategory category;
    @Nullable
    private ICondition visibleCondition;
    @Nullable
    private ICondition canBuyCondition;
    private CooldownType cooldownType = CooldownType.NONE;
    private long cooldownValue = 0;
    private int resetTimeTicks = 0;
    private int maxPurchases = -1;
    @Nullable
    private ResourceLocation rewardIcon;
    @Nullable
    private ResourceLocation costIcon;
    private int sortOrder = 0;
    private int themeColor = -1;
    @Nullable
    private Predicate<ServerPlayer> purchaseResetCondition;
    @Nullable
    private SoundEvent purchaseSuccessSound;
    @Nullable
    private SoundEvent purchaseFailSound;
    @Nullable
    private SoundEvent cooldownSound;
    @Nullable
    private SoundEvent limitReachedSound;
    @Nullable
    private SoundEvent conditionFailSound;

    private TradeEntryBuilder(String entryId) {
        this.entryId = Objects.requireNonNull(entryId);
    }

    public static TradeEntryBuilder create(String entryId) {
        return new TradeEntryBuilder(entryId);
    }

    public TradeEntryBuilder displayName(String literal) {
        displayNameText = TradeText.literal(literal);
        return this;
    }

    public TradeEntryBuilder displayName(Component name) {
        displayNameText = TradeText.component(name);
        return this;
    }

    public TradeEntryBuilder description(String literal) {
        descriptionText = TradeText.literal(literal);
        return this;
    }

    public TradeEntryBuilder description(Component desc) {
        descriptionText = TradeText.component(desc);
        return this;
    }

    public TradeEntryBuilder displayName(TradeText text) {
        displayNameText = text;
        return this;
    }

    public TradeEntryBuilder description(TradeText text) {
        descriptionText = text;
        return this;
    }

    public TradeEntryBuilder category(TradeCategory cat) {
        category = cat;
        return this;
    }

    public TradeEntryBuilder sortOrder(int order) {
        sortOrder = order;
        return this;
    }

    public TradeEntryBuilder rewardIcon(ResourceLocation texture) {
        rewardIcon = texture;
        return this;
    }

    public TradeEntryBuilder costIcon(ResourceLocation texture) {
        costIcon = texture;
        return this;
    }

    public TradeEntryBuilder themeColor(int color) {
        themeColor = color;
        return this;
    }

    public TradeEntryBuilder themeColor(ChatFormatting formatting) {
        Integer rgb = formatting.getColor();
        if (rgb != null) themeColor = 0xFF000000 | rgb;
        return this;
    }

    public TradeEntryBuilder themeColorRGB(int r, int g, int b) {
        themeColor = (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        return this;
    }

    public TradeEntryBuilder cost(ITradeOffer offer) {
        costs.add(offer);
        return this;
    }

    public TradeEntryBuilder costItem(Item item, int count) {
        costs.add(ItemTradeOffer.cost(item, count));
        return this;
    }

    public TradeEntryBuilder costItem(Item item, int count, ResourceLocation icon) {
        costs.add(new ItemTradeOffer(item, count, true, icon));
        return this;
    }

    public TradeEntryBuilder costItemTag(TagKey<Item> itemTag, int count) {
        costs.add(ItemTradeOffer.costTag(itemTag, count));
        return this;
    }

    public TradeEntryBuilder costItemTag(TagKey<Item> itemTag, int count, ResourceLocation icon) {
        costs.add(new ItemTradeOffer(itemTag, count, true, icon));
        return this;
    }

    public TradeEntryBuilder costItemDynamic(Item item, int previewCount, ToIntFunction<ServerPlayer> resolver) {
        costs.add(ItemTradeOffer.cost(item, previewCount, resolver));
        return this;
    }

    public TradeEntryBuilder costItemTagDynamic(TagKey<Item> itemTag, int previewCount, ToIntFunction<ServerPlayer> resolver) {
        costs.add(ItemTradeOffer.costTag(itemTag, previewCount, resolver));
        return this;
    }

    public TradeEntryBuilder costFlag(String flag) {
        costs.add(FlagTradeOffer.requireFlag(flag));
        return this;
    }

    public TradeEntryBuilder reward(ITradeOffer offer) {
        rewards.add(offer);
        return this;
    }

    public TradeEntryBuilder rewardItem(Item item, int count) {
        rewards.add(ItemTradeOffer.reward(item, count));
        return this;
    }

    public TradeEntryBuilder rewardEffect(MobEffect effect, int durationSeconds, int amplifier) {
        rewards.add(EffectTradeOffer.reward(effect, durationSeconds * 20, amplifier));
        return this;
    }

    public TradeEntryBuilder rewardEffect(MobEffect effect, int durationSeconds) {
        rewards.add(EffectTradeOffer.reward(effect, durationSeconds));
        return this;
    }

    public TradeEntryBuilder rewardFlag(String flag) {
        rewards.add(FlagTradeOffer.rewardFlag(flag));
        return this;
    }

    public TradeEntryBuilder rewardCommand(String command, String displayText) {
        rewards.add(CommandTradeOffer.reward(command, displayText));
        return this;
    }

    public TradeEntryBuilder visibleCondition(ICondition cond) {
        visibleCondition = cond;
        return this;
    }

    public TradeEntryBuilder canBuyCondition(ICondition cond) {
        canBuyCondition = cond;
        return this;
    }

    public TradeEntryBuilder condition(ICondition cond) {
        visibleCondition = cond;
        canBuyCondition = cond;
        return this;
    }

    public TradeEntryBuilder maxPurchases(int max) {
        maxPurchases = max;
        return this;
    }

    public TradeEntryBuilder unlimited() {
        maxPurchases = -1;
        return this;
    }

    public TradeEntryBuilder purchaseResetCondition(Predicate<ServerPlayer> condition) {
        purchaseResetCondition = condition;
        return this;
    }

    public TradeEntryBuilder purchaseResetByCooldown() {
        purchaseResetCondition = player -> false;
        return this;
    }

    public TradeEntryBuilder cooldown(long seconds) {
        cooldownType = CooldownType.SECONDS;
        cooldownValue = Math.max(0, seconds);
        return this;
    }

    public TradeEntryBuilder cooldownGameDay() {
        cooldownType = CooldownType.GAME_DAY;
        cooldownValue = 1;
        return this;
    }

    public TradeEntryBuilder cooldownGameTick(int resetTick) {
        cooldownType = CooldownType.GAME_TICK;
        cooldownValue = 0;
        resetTimeTicks = Math.max(0, Math.min(resetTick, 24000));
        return this;
    }

    public TradeEntryBuilder purchaseSuccessSound(SoundEvent sound) {
        purchaseSuccessSound = sound;
        return this;
    }

    public TradeEntryBuilder purchaseSuccessSound(Holder.Reference<SoundEvent> sound) {
        purchaseSuccessSound = sound.get();
        return this;
    }

    public TradeEntryBuilder purchaseFailSound(SoundEvent sound) {
        purchaseFailSound = sound;
        return this;
    }

    public TradeEntryBuilder purchaseFailSound(Holder.Reference<SoundEvent> sound) {
        purchaseFailSound = sound.get();
        return this;
    }

    public TradeEntryBuilder cooldownSound(SoundEvent sound) {
        cooldownSound = sound;
        return this;
    }

    public TradeEntryBuilder cooldownSound(Holder.Reference<SoundEvent> sound) {
        cooldownSound = sound.get();
        return this;
    }

    public TradeEntryBuilder limitReachedSound(SoundEvent sound) {
        limitReachedSound = sound;
        return this;
    }

    public TradeEntryBuilder limitReachedSound(Holder.Reference<SoundEvent> sound) {
        limitReachedSound = sound.get();
        return this;
    }

    public TradeEntryBuilder conditionFailSound(SoundEvent sound) {
        conditionFailSound = sound;
        return this;
    }

    public TradeEntryBuilder conditionFailSound(Holder.Reference<SoundEvent> sound) {
        conditionFailSound = sound.get();
        return this;
    }

    public TradeEntry build() {
        if (displayNameText == null) displayNameText = TradeText.literal(entryId);
        if (rewards.isEmpty()) throw new IllegalStateException("TradeEntry '" + entryId + "' has no rewards defined");
        return new TradeEntry(
                entryId, displayNameText, descriptionText,
                List.copyOf(costs), List.copyOf(rewards),
                category, visibleCondition, canBuyCondition,
                cooldownType, cooldownValue, resetTimeTicks,
                maxPurchases, rewardIcon, costIcon, sortOrder, themeColor,
                purchaseResetCondition, purchaseSuccessSound, purchaseFailSound,
                cooldownSound, limitReachedSound, conditionFailSound
        );
    }
}
