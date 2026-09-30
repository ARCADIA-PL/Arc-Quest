package org.arcadia.arc_quest.integration.jei.trade;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.time.CooldownStatus;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogProvider;
import org.arcadia.arc_quest.integration.jei.api.JeiDisplayAdapters;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import org.arcadia.arc_quest.quest.api.ChapterShopType;
import org.arcadia.arc_quest.quest.api.QuestConditionContext;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.api.TradeTextContext;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.arcadia.arc_quest.trade.runtime.TradeEntryStateResolver;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.arcadia.arc_quest.integration.jei.trade.JeiCatalogNotes.text;

/** Server-authorized ordinary, simple, chapter and dynamic registry trade definitions. */
public final class TradeJeiCatalogProvider implements JeiCatalogProvider {
    @Override
    public void collect(ServerPlayer player, ArcQuestPlayer data, Consumer<JeiCatalogEntry> output) {
        for (TradeShopDefinition shop : TradeRegistry.getAll()) {
            try {
                if (!ChapterShopVisibility.canDisplay(shop.getShopId(), ChapterShopType.TRADE, data)
                        || !shop.canOpen(player, data.getCompletedQuestLocations(), data.getAllFlags(), data.getAllVariables())) continue;
                for (TradeEntry entry : shop.getAllEntries()) {
                    try {
                        if (TradeEntryStateResolver.isVisible(player, data, entry)) output.accept(describe(player, data, shop, entry));
                    } catch (RuntimeException failure) {
                        ArcQuestLog.warn(ArcQuestLog.Category.TRADE, "Skipping JEI trade entry {}/{}",
                                shop.getShopId(), entry.getEntryId(), failure);
                    }
                }
            } catch (RuntimeException failure) {
                ArcQuestLog.warn(ArcQuestLog.Category.TRADE, "Skipping JEI trade shop {}", shop.getShopId(), failure);
            }
        }
    }

    private static JeiCatalogEntry describe(ServerPlayer player, ArcQuestPlayer data,
                                             TradeShopDefinition shop, TradeEntry entry) {
        var textContext = TradeTextContext.of(player, shop.getShopId(), data);
        List<JeiIngredient> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        List<Component> notes = new ArrayList<>();
        notes.add(text("shop_source", "Shop: %s", shop.getDisplayName(player, data)));
        Component description = entry.getDescription(player, textContext);
        if (description != null) notes.add(description);
        for (var cost : entry.getCosts()) JeiCatalogNotes.append(JeiDisplayAdapters.offer(cost, player, true), true, inputs, notes);
        for (var reward : entry.getRewards()) JeiCatalogNotes.append(JeiDisplayAdapters.offer(reward, player, false), false, outputs, notes);

        int used = data.getTradeDataStore().getPurchaseCount(shop.getShopId(), entry.getEntryId());
        CooldownStatus cooldown = CooldownStatus.inactive();
        if (entry.hasCooldown()) {
            cooldown = CoreProcessors.get().cooldowns().evaluate(
                    data.getTradeDataStore().getCooldown(shop.getShopId(), entry.getEntryId()),
                    entry.getCooldownType().toCorePolicy(entry.getCooldownValue(), entry.getResetTimeTicks()),
                    CoreProcessors.get().time().capture(player));
        }
        boolean reset = entry.hasCooldown() && (!entry.hasLimit() || used >= entry.getMaxPurchases()) && !cooldown.active();
        if (!reset && entry.hasLimit() && used > 0 && entry.getPurchaseResetCondition() != null) {
            reset = entry.getPurchaseResetCondition().test(player);
        }
        boolean qualified = entry.getCanBuyCondition() == null || CoreProcessors.get().conditions().evaluate(
                entry.getCanBuyCondition(), new QuestConditionContext(player, data.getCompletedQuestLocations(),
                        data.getAllFlags(), data.getAllVariables()));
        JeiAvailability state = JeiAvailability.project(used, entry.getMaxPurchases(), cooldown.active(), reset, qualified);
        JeiCatalogNotes.availability(notes, state, entry.getMaxPurchases());
        JeiCatalogNotes.cooldown(notes, entry.getCooldownType(), entry.getCooldownValue(), entry.getResetTimeTicks(),
                entry.getMaxPurchases(), cooldown, state.cooldownActive());
        return new JeiCatalogEntry("trade/" + shop.getShopId().length() + ":" + shop.getShopId() + "/" + entry.getEntryId(),
                JeiCatalogEntry.Kind.TRADE, entry.getDisplayName(player, textContext), inputs, outputs, notes,
                shop.getShopId(), entry.getEntryId());
    }
}
