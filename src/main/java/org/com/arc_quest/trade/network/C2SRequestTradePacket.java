package org.com.arc_quest.trade.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.com.arc_quest.trade.registry.TradeRegistry;
import org.com.arc_quest.trade.runtime.TradeEntryStateResolver;
import org.com.arc_quest.trade.runtime.TradeSession;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端→服务端：请求执行交易 / 打开交易窗口。
 */
public class C2SRequestTradePacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Action {
        OPEN_FULL,
        OPEN_SIMPLE,
        PURCHASE
    }

    /** 客户端当前打开的界面类型（用于刷新时保持类型一致） */
    public enum ScreenType {
        NONE,
        SIMPLE,
        FULL
    }

    private final Action action;
    private final String shopId;
    private final String entryId;
    private final ScreenType currentScreenType;

    public C2SRequestTradePacket(Action action, String shopId, String entryId) {
        this(action, shopId, entryId, ScreenType.NONE);
    }

    public C2SRequestTradePacket(Action action, String shopId, String entryId, ScreenType currentScreenType) {
        this.action = action;
        this.shopId = shopId;
        this.entryId = entryId != null ? entryId : "";
        this.currentScreenType = currentScreenType != null ? currentScreenType : ScreenType.NONE;
    }

    public static C2SRequestTradePacket openFull(String shopId) {
        return new C2SRequestTradePacket(Action.OPEN_FULL, shopId, null);
    }

    public static C2SRequestTradePacket openSimple(String shopId) {
        return new C2SRequestTradePacket(Action.OPEN_SIMPLE, shopId, null);
    }

    public static C2SRequestTradePacket purchase(String shopId, String entryId) {
        return new C2SRequestTradePacket(Action.PURCHASE, shopId, entryId);
    }

    public static C2SRequestTradePacket purchaseWithScreenType(String shopId, String entryId, ScreenType screenType) {
        return new C2SRequestTradePacket(Action.PURCHASE, shopId, entryId, screenType);
    }

    public static C2SRequestTradePacket refresh(String shopId, ScreenType screenType) {
        return new C2SRequestTradePacket(
                screenType == ScreenType.SIMPLE ? Action.OPEN_SIMPLE : Action.OPEN_FULL,
                shopId, null, screenType);
    }

    // ── 序列化 ──

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(shopId);
        buf.writeUtf(entryId);
        buf.writeEnum(currentScreenType);
    }

    public static C2SRequestTradePacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String shopId = buf.readUtf();
        String entryId = buf.readUtf();
        ScreenType screenType = buf.readEnum(ScreenType.class);
        return new C2SRequestTradePacket(action, shopId, entryId, screenType);
    }

    public static void handle(C2SRequestTradePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            TradeShopDefinition shop = TradeRegistry.get(pkt.shopId);
            if (shop == null) {
                LOGGER.warn("[Trade] Unknown shop '{}' requested by {}",
                        pkt.shopId, player.getName().getString());
                return;
            }

            switch (pkt.action) {
                case OPEN_FULL   -> handleOpen(player, shop, false);
                case OPEN_SIMPLE -> handleOpen(player, shop, true);
                case PURCHASE    -> handlePurchase(player, shop, pkt.entryId, pkt.currentScreenType);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Server-side open handler — callable from DialogueAction or packet handling.
     */
    public static void handleServerOpen(ServerPlayer player, TradeShopDefinition shop, boolean simple) {
        handleOpen(player, shop, simple);
    }

    /**
     * 从对话中打开商店，确保不中断当前的对话会话。
     */
    public static void handleServerOpenFromDialogue(ServerPlayer player, TradeShopDefinition shop, boolean simple, String restoreNodeId) {
        LOGGER.info("[C2SRequestTradePacket] Opening shop '{}' from dialogue, restoreNodeId: {}, simple: {}", 
                shop.getShopId(), restoreNodeId, simple);
        handleOpen(player, shop, simple);
        
        DialogueSessionManager manager = DialogueSessionManager.INSTANCE;
        if (manager.isInDialogue(player)) {
            manager.setRestoreNodeId(player, restoreNodeId);
            LOGGER.info("[C2SRequestTradePacket] Saved restoreNodeId for player {}", player.getName().getString());
        } else {
            LOGGER.warn("[C2SRequestTradePacket] Player {} is not in dialogue", player.getName().getString());
        }
    }

    private static void handleOpen(ServerPlayer player, TradeShopDefinition shop, boolean simple) {
        TradeSession session = new TradeSession(player, shop);

        for (TradeEntry entry : shop.getAllEntries()) {
            if (entry.hasLimit() || entry.hasCooldown()) {
                checkAndResetPurchases(session, entry.getEntryId(), entry);
            }
        }

        TradeSnapshot snap = buildTradeSnapshot(player, shop, session);
        S2COpenTradePacket response = simple
                ? S2COpenTradePacket.openSimple(shop.getShopId(), snap.purchases(), snap.maxPurchases(),
                        snap.lastPurchaseTimes(), snap.purchaseGameTimes(), snap.purchaseDayTimes(),
                        snap.cooldownTypes(), snap.cooldownValues(), snap.resetTimeTicks(),
                        snap.visibility(), snap.canBuyConditions())
                : S2COpenTradePacket.openFull(shop.getShopId(), snap.purchases(), snap.maxPurchases(),
                        snap.lastPurchaseTimes(), snap.purchaseGameTimes(), snap.purchaseDayTimes(),
                        snap.cooldownTypes(), snap.cooldownValues(), snap.resetTimeTicks(),
                        snap.visibility(), snap.canBuyConditions());

        ArcQuestNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                response);
    }

    private static void handlePurchase(ServerPlayer player, TradeShopDefinition shop,
                                        String entryId, ScreenType clientScreenType) {
        TradeSession session = new TradeSession(player, shop);
        TradeSession.TradeResult result = session.executeTrade(entryId);

        S2COpenTradePacket response = result.succeeded()
                ? S2COpenTradePacket.tradeSuccess(shop.getShopId())
                : S2COpenTradePacket.tradeFail(shop.getShopId(), result.errorKey());

        ArcQuestNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                response);

        refreshTradeData(player, shop, clientScreenType);
    }

    /**
     * 刷新交易界面数据（不重建会话，仅同步最新状态）。
     */
    private static void refreshTradeData(ServerPlayer player, TradeShopDefinition shop, ScreenType clientScreenType) {
        LOGGER.info("[Trade-Packet] Refreshing trade data: shop={}, screenType={}", shop.getShopId(), clientScreenType);

        TradeSession session = new TradeSession(player, shop);
        TradeSnapshot snap = buildTradeSnapshot(player, shop, session);

        S2COpenTradePacket refreshPkt = (clientScreenType == ScreenType.SIMPLE)
                ? S2COpenTradePacket.openSimple(shop.getShopId(), snap.purchases(), snap.maxPurchases(),
                        snap.lastPurchaseTimes(), snap.purchaseGameTimes(), snap.purchaseDayTimes(),
                        snap.cooldownTypes(), snap.cooldownValues(), snap.resetTimeTicks(),
                        snap.visibility(), snap.canBuyConditions())
                : S2COpenTradePacket.openFull(shop.getShopId(), snap.purchases(), snap.maxPurchases(),
                        snap.lastPurchaseTimes(), snap.purchaseGameTimes(), snap.purchaseDayTimes(),
                        snap.cooldownTypes(), snap.cooldownValues(), snap.resetTimeTicks(),
                        snap.visibility(), snap.canBuyConditions());

        LOGGER.info("[Trade-Packet] Sending refresh packet: {} entries, type={}", snap.purchases().length, clientScreenType);
        ArcQuestNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                refreshPkt);
    }

    /**
     * 为商店所有商品构建网络传输快照数据，供 handleOpen 和 refreshTradeData 共用。
     */
    private static TradeSnapshot buildTradeSnapshot(ServerPlayer player,
                                                     TradeShopDefinition shop,
                                                     TradeSession session) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        List<TradeEntry> allEntries = new ArrayList<>(shop.getAllEntries());
        int count = allEntries.size();

        int[] purchases      = new int[count];
        int[] maxPurchases   = new int[count];
        long[] lastPurchaseTimes  = new long[count];
        long[] purchaseGameTimes  = new long[count];
        long[] purchaseDayTimes   = new long[count];
        int[] cooldownTypes   = new int[count];
        long[] cooldownValues = new long[count];
        int[] resetTimeTicks  = new int[count];
        boolean[] visibility      = new boolean[count];
        boolean[] canBuyConditions = new boolean[count];

        for (int i = 0; i < count; i++) {
            TradeEntry entry = allEntries.get(i);
            purchases[i]    = session.getPurchaseCount(entry.getEntryId());
            maxPurchases[i] = entry.getMaxPurchases();

            if (entry.hasCooldown()) {
                lastPurchaseTimes[i] = cap.getTradeLastPurchaseTime(shop.getShopId(), entry.getEntryId());
                ProgressKey key = ProgressKey.ofTrade(shop.getShopId(), entry.getEntryId());
                var storeEntry = cap.getDialogueProgress().getChoiceSelection(key);
                purchaseGameTimes[i] = storeEntry.exists() ? storeEntry.gameTime() : 0;
                purchaseDayTimes[i]  = storeEntry.exists() ? storeEntry.dayTime()  : 0;
                cooldownTypes[i]  = entry.getCooldownType().ordinal();
                cooldownValues[i] = entry.getCooldownValue();
                resetTimeTicks[i] = entry.getResetTimeTicks();

                LOGGER.debug("[Trade-Packet] Entry {}: id={}, purchases={}, lastTime={}, gameT={}, dayT={}, cdType={}, cdVal={}",
                        i, entry.getEntryId(), purchases[i], lastPurchaseTimes[i],
                        purchaseGameTimes[i], purchaseDayTimes[i], cooldownTypes[i], cooldownValues[i]);
            }

            visibility[i]       = session.isEntryVisible(entry);
            canBuyConditions[i] = session.canPurchase(entry);
        }

        return new TradeSnapshot(purchases, maxPurchases, lastPurchaseTimes, purchaseGameTimes,
                purchaseDayTimes, cooldownTypes, cooldownValues, resetTimeTicks, visibility, canBuyConditions);
    }

    /**
     * 商店快照数据载体，避免多个方法间传递大量数组参数。
     */
    private record TradeSnapshot(
            int[] purchases,
            int[] maxPurchases,
            long[] lastPurchaseTimes,
            long[] purchaseGameTimes,
            long[] purchaseDayTimes,
            int[] cooldownTypes,
            long[] cooldownValues,
            int[] resetTimeTicks,
            boolean[] visibility,
            boolean[] canBuyConditions
    ) {}

    /**
     * 检查并重置过期的购买次数和冷却。
     */
    private static void checkAndResetPurchases(TradeSession session, String entryId, TradeEntry entry) {
        ServerPlayer player = session.getPlayer();
        var cap = QuestCapabilityProvider.getOrNull(player);

        boolean shouldReset = TradeEntryStateResolver.shouldResetByCooldown(player, cap, session.getShop().getShopId(), entry);

        if (!shouldReset && entry.hasLimit()) {
            var resetCondition = entry.getPurchaseResetCondition();
            if (resetCondition != null) {
                try {
                    shouldReset = resetCondition.test(player);
                    if (shouldReset) {
                        LOGGER.info("[Trade] Purchase limit reset by custom condition for entry={}", entryId);
                    }
                } catch (Exception e) {
                    LOGGER.warn("[Trade] Error evaluating purchase reset condition for entry={}: {}",
                            entryId, e.getMessage());
                }
            }
        }

        if (shouldReset) {
            TradeEntryStateResolver.resetPurchaseAndCooldown(cap, session.getShop().getShopId(), entryId);
        }
    }
}
