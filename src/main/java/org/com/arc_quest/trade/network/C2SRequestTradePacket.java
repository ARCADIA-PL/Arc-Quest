package org.com.arc_quest.trade.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.com.arc_quest.api.event.TradeOpenedEvent;
import org.com.arc_quest.api.event.TradePurchaseFailedEvent;
import org.com.arc_quest.api.event.TradePurchasedSuccessEvent;
import org.com.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.network.SyncObservability;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.com.arc_quest.trade.registry.TradeRegistry;
import org.com.arc_quest.trade.runtime.TradeEntryStateResolver;
import org.com.arc_quest.trade.runtime.TradeSession;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 客户端→服务端：请求执行交易 / 打开交易窗口。
 */
public class C2SRequestTradePacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, Map<String, Integer>> LAST_TRADE_SYNC_FINGERPRINTS = new ConcurrentHashMap<>();
    private static final Map<UUID, ActiveTradeContext> ACTIVE_TRADE_CONTEXTS = new ConcurrentHashMap<>();
    private static final long ACTIVE_TRADE_CONTEXT_TTL_MS = 20000L;

    public static void syncState(ServerPlayer player, TradeShopDefinition shop, ScreenType clientScreenType) {
        touchActiveTradeContext(player, shop.getShopId(), clientScreenType);
        refreshTradeData(player, shop, clientScreenType);
    }

    public static void pushSyncForActiveShop(ServerPlayer player, String reason) {
        ActiveTradeContext context = ACTIVE_TRADE_CONTEXTS.get(player.getUUID());
        if (context == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - context.lastSeenMs() > ACTIVE_TRADE_CONTEXT_TTL_MS) {
            ACTIVE_TRADE_CONTEXTS.remove(player.getUUID());
            return;
        }

        TradeShopDefinition shop = TradeRegistry.get(context.shopId());
        if (shop == null) {
            ACTIVE_TRADE_CONTEXTS.remove(player.getUUID());
            return;
        }

        LOGGER.debug("[Trade-Push] Active shop sync push: player={}, shop={}, screenType={}, reason={}",
                player.getName().getString(), context.shopId(), context.screenType(), reason);

        refreshTradeData(player, shop, context.screenType());
        touchActiveTradeContext(player, context.shopId(), context.screenType());
    }

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
            ServerPlayer player = TradeRequestValidator.requirePlayer(ctx.get().getSender(), "trade_request", pkt.shopId, LOGGER);
            if (player == null) return;

            TradeShopDefinition shop = TradeRequestValidator.requireShop(pkt.shopId, player, "trade_request", LOGGER);
            if (shop == null) {
                sendGuardTradeFail(player, pkt, RejectCodeDictionary.Code.SHOP_NOT_FOUND);
                return;
            }

            IQuestCapability cap = TradeRequestValidator.requireCapability(player, "trade_request", pkt.shopId, LOGGER);
            if (cap == null) {
                sendGuardTradeFail(player, pkt, RejectCodeDictionary.Code.CAPABILITY_MISSING);
                return;
            }

            switch (pkt.action) {
                case OPEN_FULL -> handleOpen(player, shop, false);
                case OPEN_SIMPLE -> handleOpen(player, shop, true);
                case PURCHASE -> handlePurchase(player, shop, pkt.entryId, pkt.currentScreenType);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void sendGuardTradeFail(ServerPlayer player,
                                           C2SRequestTradePacket pkt,
                                           RejectCodeDictionary.Code code) {
        ArcQuestNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                S2COpenTradePacket.tradeFail(
                        pkt.shopId,
                        pkt.entryId,
                        S2COpenTradePacket.FailReason.GENERIC,
                        TradeRequestValidator.toErrorKey(code)
                )
        );
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
        handleOpen(player, shop, simple);
        
        DialogueSessionManager manager = DialogueSessionManager.INSTANCE;
        if (manager.isInDialogue(player)) {
            manager.setRestoreNodeId(player, restoreNodeId);
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
        
        // 获取商店音效 ID
        String openSoundId = shop.getOpenSound() != null ? 
                ResourceLocation.fromNamespaceAndPath(
                        Objects.requireNonNull(ForgeRegistries.SOUND_EVENTS.getKey(shop.getOpenSound())).getNamespace(),
                        Objects.requireNonNull(ForgeRegistries.SOUND_EVENTS.getKey(shop.getOpenSound())).getPath()
                ).toString() : "";
        String closeSoundId = shop.getCloseSound() != null ? 
                ResourceLocation.fromNamespaceAndPath(
                        Objects.requireNonNull(ForgeRegistries.SOUND_EVENTS.getKey(shop.getCloseSound())).getNamespace(),
                        Objects.requireNonNull(ForgeRegistries.SOUND_EVENTS.getKey(shop.getCloseSound())).getPath()
                ).toString() : "";
        
        S2COpenTradePacket response = simple
                ? S2COpenTradePacket.openSimple(shop.getShopId(), snap.purchases(), snap.maxPurchases(),
                        snap.lastPurchaseTimes(), snap.purchaseGameTimes(), snap.purchaseDayTimes(),
                        snap.cooldownTypes(), snap.cooldownValues(), snap.resetTimeTicks(),
                        snap.visibility(), snap.canBuyConditions(),
                        openSoundId, closeSoundId)
                : S2COpenTradePacket.openFull(shop.getShopId(), snap.purchases(), snap.maxPurchases(),
                        snap.lastPurchaseTimes(), snap.purchaseGameTimes(), snap.purchaseDayTimes(),
                        snap.cooldownTypes(), snap.cooldownValues(), snap.resetTimeTicks(),
                        snap.visibility(), snap.canBuyConditions(),
                        openSoundId, closeSoundId);

        ArcQuestNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                response);

        markTradeSynced(player, shop.getShopId(), snap);
        touchActiveTradeContext(player, shop.getShopId(), simple ? ScreenType.SIMPLE : ScreenType.FULL);

        // 发布 Forge 事件（供附属模组监听）
        // 注意：商店打开时没有 NPC 上下文，npc 参数为 null
        MinecraftForge.EVENT_BUS.post(new TradeOpenedEvent(player, shop.getShopId(), null));
    }

    private static void handlePurchase(ServerPlayer player, TradeShopDefinition shop,
                                        String entryId, ScreenType clientScreenType) {
        TradeSession session = new TradeSession(player, shop);
        TradeSession.TradeResult result = session.executeTrade(entryId);

        S2COpenTradePacket.FailReason reason = S2COpenTradePacket.FailReason.GENERIC;
        String errorKey = result.errorKey();
        if (errorKey != null) {
            if (errorKey.contains("cooldown")) reason = S2COpenTradePacket.FailReason.COOLDOWN;
            else if (errorKey.contains("max_purchases") || errorKey.contains("limit")) reason = S2COpenTradePacket.FailReason.LIMIT_REACHED;
            else if (errorKey.contains("condition") || errorKey.contains("visible")) reason = S2COpenTradePacket.FailReason.CONDITION_FAIL;
            else if (errorKey.contains("afford")) reason = S2COpenTradePacket.FailReason.CANNOT_AFFORD;
        }

        S2COpenTradePacket response = result.succeeded()
                ? S2COpenTradePacket.tradeSuccess(shop.getShopId(), entryId)
                : S2COpenTradePacket.tradeFail(shop.getShopId(), entryId, reason, errorKey, result.shortfallLines());

        ArcQuestNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                response);
        
        // 发布 Forge 事件（供附属模组监听）
        if (result.succeeded()) {
            MinecraftForge.EVENT_BUS.post(new TradePurchasedSuccessEvent(player, shop.getShopId(), entryId));
        } else {
            // 转换失败原因
            TradePurchaseFailedEvent.FailureReason failureReason = switch (reason) {
                case COOLDOWN -> TradePurchaseFailedEvent.FailureReason.ON_COOLDOWN;
                case LIMIT_REACHED -> TradePurchaseFailedEvent.FailureReason.MAX_PURCHASES_REACHED;
                case CONDITION_FAIL -> TradePurchaseFailedEvent.FailureReason.CONDITION_NOT_MET;
                case CANNOT_AFFORD -> TradePurchaseFailedEvent.FailureReason.INSUFFICIENT_FUNDS;
                default -> TradePurchaseFailedEvent.FailureReason.UNKNOWN;
            };
            MinecraftForge.EVENT_BUS.post(new TradePurchaseFailedEvent(
                    player, shop.getShopId(), entryId, failureReason));
        }

        if (clientScreenType != ScreenType.NONE) {
            touchActiveTradeContext(player, shop.getShopId(), clientScreenType);
        }

        refreshTradeData(player, shop, clientScreenType);
    }

    /**
     * 刷新交易界面数据（不重建会话，仅同步最新状态）。
     */
    /**
     * 刷新交易界面数据（不重建会话，仅同步最新状态）。
     * 启用变化检测：状态无变化时不发包。
     */
    private static void refreshTradeData(ServerPlayer player, TradeShopDefinition shop, ScreenType clientScreenType) {
        TradeSession session = new TradeSession(player, shop);
        TradeSnapshot snap = buildTradeSnapshot(player, shop, session);

        if (!shouldSendTradeSync(player, shop.getShopId(), snap)) {
            SyncObservability.recordDropped("trade", shop.getShopId(), player.getName().getString(), false);
            return;
        }

        S2CSyncTradeStatePacket refreshPkt = new S2CSyncTradeStatePacket(
                shop.getShopId(),
                snap.purchases(),
                snap.maxPurchases(),
                snap.lastPurchaseTimes(),
                snap.purchaseGameTimes(),
                snap.purchaseDayTimes(),
                snap.cooldownTypes(),
                snap.cooldownValues(),
                snap.resetTimeTicks(),
                snap.visibility(),
                snap.canBuyConditions()
        );

        ArcQuestNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                refreshPkt
        );
        SyncObservability.recordSent("trade", shop.getShopId(), player.getName().getString(), true);
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
                var cooldownRecord = cap.getTradeDataStore().getCooldown(shop.getShopId(), entry.getEntryId());
                lastPurchaseTimes[i] = cooldownRecord.realTime();
                purchaseGameTimes[i] = cooldownRecord.gameTime();
                purchaseDayTimes[i]  = cooldownRecord.dayTime();
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

    private static void touchActiveTradeContext(ServerPlayer player, String shopId, ScreenType screenType) {
        if (player == null || shopId == null || shopId.isEmpty()) {
            return;
        }
        ScreenType effectiveType = screenType != null ? screenType : ScreenType.FULL;
        if (effectiveType == ScreenType.NONE) {
            effectiveType = ScreenType.FULL;
        }
        ACTIVE_TRADE_CONTEXTS.put(player.getUUID(),
                new ActiveTradeContext(shopId, effectiveType, System.currentTimeMillis()));
    }

    private static record ActiveTradeContext(String shopId, ScreenType screenType, long lastSeenMs) {
    }

    private static boolean shouldSendTradeSync(ServerPlayer player, String shopId, TradeSnapshot snap) {
        int fp = buildTradeFingerprint(snap);
        Map<String, Integer> playerMap = LAST_TRADE_SYNC_FINGERPRINTS
                .computeIfAbsent(player.getUUID(), __ -> new ConcurrentHashMap<>());
        Integer old = playerMap.get(shopId);
        if (old != null && old == fp) {
            return false;
        }
        playerMap.put(shopId, fp);
        return true;
    }

    private static void markTradeSynced(ServerPlayer player, String shopId, TradeSnapshot snap) {
        int fp = buildTradeFingerprint(snap);
        LAST_TRADE_SYNC_FINGERPRINTS
                .computeIfAbsent(player.getUUID(), __ -> new ConcurrentHashMap<>())
                .put(shopId, fp);
    }

    private static int buildTradeFingerprint(TradeSnapshot snap) {
        int h = 1;
        h = 31 * h + Arrays.hashCode(snap.purchases());
        h = 31 * h + Arrays.hashCode(snap.maxPurchases());
        h = 31 * h + Arrays.hashCode(snap.lastPurchaseTimes());
        h = 31 * h + Arrays.hashCode(snap.purchaseGameTimes());
        h = 31 * h + Arrays.hashCode(snap.purchaseDayTimes());
        h = 31 * h + Arrays.hashCode(snap.cooldownTypes());
        h = 31 * h + Arrays.hashCode(snap.cooldownValues());
        h = 31 * h + Arrays.hashCode(snap.resetTimeTicks());
        h = 31 * h + Arrays.hashCode(snap.visibility());
        h = 31 * h + Arrays.hashCode(snap.canBuyConditions());
        return h;
    }
}
