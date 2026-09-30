package org.arcadia.arc_quest.integration.jei.gacha;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.time.CooldownStatus;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogProvider;
import org.arcadia.arc_quest.integration.jei.api.JeiDisplayAdapters;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import org.arcadia.arc_quest.integration.jei.trade.ChapterShopVisibility;
import org.arcadia.arc_quest.integration.jei.trade.JeiAvailability;
import org.arcadia.arc_quest.integration.jei.trade.JeiCatalogNotes;
import org.arcadia.arc_quest.quest.api.ChapterShopType;
import org.arcadia.arc_quest.quest.api.QuestConditionContext;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.gacha.runtime.GachaEntryStateResolver;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.arcadia.arc_quest.integration.jei.trade.JeiCatalogNotes.text;

/** One catalog recipe per mutually exclusive outcome; never treats the decorative stack as a reward. */
public final class GachaJeiCatalogProvider implements JeiCatalogProvider {
    @Override
    public void collect(ServerPlayer player, ArcQuestPlayer data, Consumer<JeiCatalogEntry> output) {
        for (GachaShopDefinition shop : GachaRegistry.getAllShops()) {
            try {
                if (!ChapterShopVisibility.canDisplay(shop.getShopId(), ChapterShopType.GACHA, data)
                        || !GachaEntryStateResolver.isVisible(player, data, shop)) continue;
                GachaOddsSnapshot odds = GachaOddsSnapshot.capture(shop, player, data);
                List<JeiIngredient> inputs = new ArrayList<>();
                List<Component> notes = commonNotes(player, data, shop, odds);
                for (var cost : shop.getDrawCosts()) {
                    JeiCatalogNotes.append(JeiDisplayAdapters.offer(cost, player, true), true, inputs, notes);
                }
                for (GachaOddsSnapshot.Outcome outcome : odds.outcomes()) {
                    try {
                        output.accept(describe(player, shop, inputs, notes, outcome));
                    } catch (RuntimeException failure) {
                        ArcQuestLog.warn(ArcQuestLog.Category.GACHA, "Skipping JEI gacha outcome {}/{}",
                                shop.getShopId(), outcome.item().getItemId(), failure);
                    }
                }
            } catch (RuntimeException failure) {
                // Do not renormalize a partial pool after a failed visibility/weight calculation.
                ArcQuestLog.warn(ArcQuestLog.Category.GACHA, "Skipping JEI gacha shop {}", shop.getShopId(), failure);
            }
        }
    }

    private static JeiCatalogEntry describe(ServerPlayer player, GachaShopDefinition shop, List<JeiIngredient> inputs,
                                             List<Component> common, GachaOddsSnapshot.Outcome outcome) {
        GachaItem item = outcome.item();
        List<JeiIngredient> outputs = new ArrayList<>();
        List<Component> notes = new ArrayList<>(common);
        if (item.getReward() instanceof ItemTradeOffer reward) {
            // The draw transaction overrides the direct item's count; composite offers execute once.
            // createRewardStack also rejects invalid tag/cost offers and preserves exact reward NBT.
            var stack = reward.createRewardStack(item.getMinCount());
            outputs.add(JeiIngredient.of(stack, item.getMinCount(), false));
            notes.add(text("gacha_quantity", "Item quantity if selected: %s–%s (inclusive)", item.getMinCount(), item.getMaxCount()));
        } else if (item.getReward() != null) {
            JeiCatalogNotes.append(JeiDisplayAdapters.offer(item.getReward(), player, false), false, outputs, notes);
            notes.add(text("gacha_offer_once", "If selected, the reward offer runs once with its own quantities."));
        } else throw new IllegalArgumentException("Gacha outcome has no reward");
        notes.add(text("gacha_rarity", "Rarity: %s", Component.translatableWithFallback(
                "arc_quest.gacha.rarity." + item.getRarity().getName(), item.getRarity().getName())));
        notes.add(text("gacha_weights", "Base weight: %s; current server weight: %s", outcome.baseWeight(), outcome.effectiveWeight()));
        notes.add(text("gacha_ordinary_chance", "Ordinary draw chance in this server snapshot: ≈%s%%", percent(outcome.ordinaryPercent())));
        notes.add(text("gacha_next_chance", "Next-draw chance before event hooks, including current pity: ≈%s%%", percent(outcome.nextPercent())));
        Component rewardTitle = outputs.isEmpty() ? item.getReward().describe() : outputs.get(0).description();
        return new JeiCatalogEntry("gacha/" + shop.getShopId().length() + ":" + shop.getShopId() + "/" + item.getItemId(),
                JeiCatalogEntry.Kind.GACHA, rewardTitle, inputs, outputs, notes, shop.getShopId(), item.getItemId());
    }

    private static List<Component> commonNotes(ServerPlayer player, ArcQuestPlayer data, GachaShopDefinition shop,
                                                GachaOddsSnapshot odds) {
        List<Component> notes = new ArrayList<>();
        notes.add(text("gacha_source", "Prize pool: %s", shop.getShopDefinition().getDisplayName(player, data)));
        notes.add(text("gacha_alternative", "One possible result per draw; other catalog outcomes are alternatives, not additional rewards."));
        notes.add(text("gacha_snapshot", "Server-calculated snapshot. Draw events and later state changes can change the final probabilities."));
        if (!odds.hasNextOutcome()) notes.add(text("gacha_no_outcome", "No eligible result for the next draw in this snapshot."));
        var pity = shop.getPityConfig();
        if (pity != null) {
            notes.add(text("gacha_pity_counter", "Pity counter: %s / %s; checked before the draw",
                    data.getGachaPityCounter(shop.getShopId()), pity.getPityThreshold()));
            if (odds.pityActive()) notes.add(text("gacha_pity_active", "The next draw uses the pity pool in this snapshot."));
            if (shop.shouldResetPityOnEarlyTrigger() && pity.getGuaranteedRarity() != null) notes.add(text("gacha_pity_early_reset",
                    "An ordinary result of the configured pity rarity resets the pity counter."));
            notes.add(text("gacha_pity_reset", "A triggered pity result resets the counter after the paid draw is recorded."));
        }

        int used = data.getGachaDrawCount(shop.getShopId());
        CooldownStatus cooldown = CooldownStatus.inactive();
        boolean reset = false;
        if (shop.hasCooldown()) {
            var record = data.getGachaDataStore().getDrawCooldown(shop.getShopId());
            var now = CoreProcessors.get().time().capture(player);
            cooldown = CoreProcessors.get().cooldowns().evaluate(record,
                    shop.getCooldownType().toCorePolicy(shop.getCooldownValue(), shop.getResetTimeTicks()), now);
            // Project GachaSession.checkAndResetDrawCount without firing reset events or mutating data.
            reset = shop.hasLimit() && used > 0 && used >= shop.getMaxDraws()
                    && shop.shouldResetOnLimitReached() && record.exists()
                    && (record.dayTime() > now.dayTime() || !cooldown.active());
        }
        var conditionContext = new QuestConditionContext(player, data.getCompletedQuestLocations(), data.getAllFlags(), data.getAllVariables());
        if (!reset && shop.hasLimit() && used > 0 && shop.getResetCondition() != null) {
            reset = CoreProcessors.get().conditions().evaluate(shop.getResetCondition(), conditionContext);
        }
        boolean qualified = shop.getDrawCondition() == null
                || CoreProcessors.get().conditions().evaluate(shop.getDrawCondition(), conditionContext);
        JeiAvailability state = JeiAvailability.project(used, shop.getMaxDraws(), cooldown.active(), reset, qualified);
        JeiCatalogNotes.availability(notes, state, shop.getMaxDraws());
        JeiCatalogNotes.cooldown(notes, shop.getCooldownType(), shop.getCooldownValue(), shop.getResetTimeTicks(),
                shop.getMaxDraws(), cooldown, state.cooldownActive());
        if (shop.hasLimit() && shop.hasCooldown() && !shop.shouldResetOnLimitReached()) {
            notes.add(text("gacha_no_cooldown_quota_reset", "This pool does not automatically restore an exhausted quota through cooldown."));
        }
        return notes;
    }

    // Significant digits keep tiny positive odds distinguishable from impossible outcomes.
    static String percent(double percent) {
        return BigDecimal.valueOf(percent).round(new MathContext(6)).stripTrailingZeros().toEngineeringString();
    }
}
