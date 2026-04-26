package org.com.arc_quest.quest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.com.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.questmarker.api.QuestMarkerData;
import org.com.arc_quest.trade.gacha.network.C2SConfirmDrawPacket;
import org.com.arc_quest.trade.gacha.network.C2SDrawGachaPacket;
import org.com.arc_quest.trade.gacha.network.C2SGachaControlPacket;
import org.com.arc_quest.trade.gacha.network.S2CDrawFailedPacket;
import org.com.arc_quest.trade.gacha.network.S2CDrawResultPacket;
import org.com.arc_quest.trade.gacha.network.S2CGachaStatePacket;
import org.com.arc_quest.trade.gacha.runtime.GachaScreenOpener;
import org.com.arc_quest.trade.network.C2SRequestTradePacket;
import org.com.arc_quest.trade.network.C2SRequestTradeSyncPacket;
import org.com.arc_quest.trade.network.S2COpenTradePacket;
import org.com.arc_quest.trade.network.S2CSyncTradeStatePacket;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Arc Quest 网络通信中心。
 * 使用 Forge SimpleChannel 进行 S2C / C2S 数据包注册与发送。
 */
public final class ArcQuestNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private ArcQuestNetwork() {
    }

    /**
     * 在 Mod 构造器（FMLCommonSetupEvent）中调用。
     */
    public static void register() {
        // ─── S2C：全量同步 ───
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncFullDataPacket.class,
                S2CSyncFullDataPacket::encode,
                S2CSyncFullDataPacket::decode,
                S2CSyncFullDataPacket::handle
        );

        // ─── S2C：单任务状态同步 ───
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncQuestStatePacket.class,
                S2CSyncQuestStatePacket::encode,
                S2CSyncQuestStatePacket::decode,
                S2CSyncQuestStatePacket::handle
        );

        // ─── S2C：增量进度同步 ───
        CHANNEL.registerMessage(
                packetId++,
                S2CDeltaProgressPacket.class,
                S2CDeltaProgressPacket::encode,
                S2CDeltaProgressPacket::decode,
                S2CDeltaProgressPacket::handle
        );

        // ─── S2C：Flags / Variables 同步 ───
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncFlagsVarsPacket.class,
                S2CSyncFlagsVarsPacket::encode,
                S2CSyncFlagsVarsPacket::decode,
                S2CSyncFlagsVarsPacket::handle
        );

        // ─── C2S：玩家请求（接受/放弃/选择）───
        CHANNEL.registerMessage(
                packetId++,
                C2SRequestQuestActionPacket.class,
                C2SRequestQuestActionPacket::encode,
                C2SRequestQuestActionPacket::decode,
                C2SRequestQuestActionPacket::handle
        );

        // ─── S2C：任务动作结果（标准拒绝码）───
        CHANNEL.registerMessage(
                packetId++,
                S2CQuestActionResultPacket.class,
                S2CQuestActionResultPacket::encode,
                S2CQuestActionResultPacket::decode,
                S2CQuestActionResultPacket::handle
        );

        // ─── S2C：打开对话界面 ───
        CHANNEL.registerMessage(
                packetId++,
                S2COpenDialoguePacket.class,
                S2COpenDialoguePacket::encode,
                S2COpenDialoguePacket::decode,
                S2COpenDialoguePacket::handle
        );

        // ─── C2S：对话选择 ───
        CHANNEL.registerMessage(
                packetId++,
                C2SDialogueChoicePacket.class,
                C2SDialogueChoicePacket::encode,
                C2SDialogueChoicePacket::decode,
                C2SDialogueChoicePacket::handle
        );

        // --- S2C: Trade window ---
        CHANNEL.registerMessage(
                packetId++,
                S2COpenTradePacket.class,
                S2COpenTradePacket::encode,
                S2COpenTradePacket::decode,
                S2COpenTradePacket::handle
        );

        // --- S2C: Trade state sync ---
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncTradeStatePacket.class,
                S2CSyncTradeStatePacket::encode,
                S2CSyncTradeStatePacket::decode,
                S2CSyncTradeStatePacket::handle
        );

        // --- C2S: Trade request ---
        CHANNEL.registerMessage(
                packetId++,
                C2SRequestTradePacket.class,
                C2SRequestTradePacket::encode,
                C2SRequestTradePacket::decode,
                C2SRequestTradePacket::handle
        );

        // --- C2S: Trade state sync request ---
        CHANNEL.registerMessage(
                packetId++,
                C2SRequestTradeSyncPacket.class,
                C2SRequestTradeSyncPacket::encode,
                C2SRequestTradeSyncPacket::decode,
                C2SRequestTradeSyncPacket::handle
        );

        // --- C2S: Gacha control (open/sync) ---
        CHANNEL.registerMessage(
                packetId++,
                C2SGachaControlPacket.class,
                C2SGachaControlPacket::encode,
                C2SGachaControlPacket::decode,
                C2SGachaControlPacket::handle
        );

        // --- S2C: Gacha state (open/sync) ---
        CHANNEL.registerMessage(
                packetId++,
                S2CGachaStatePacket.class,
                S2CGachaStatePacket::encode,
                S2CGachaStatePacket::decode,
                S2CGachaStatePacket::handle
        );

        // --- C2S: Gacha draw request ---
        CHANNEL.registerMessage(
                packetId++,
                C2SDrawGachaPacket.class,
                C2SDrawGachaPacket::encode,
                C2SDrawGachaPacket::decode,
                C2SDrawGachaPacket::handle
        );

        // --- C2S: Confirm gacha draw (grant reward after animation) ---
        CHANNEL.registerMessage(
                packetId++,
                C2SConfirmDrawPacket.class,
                C2SConfirmDrawPacket::encode,
                C2SConfirmDrawPacket::decode,
                C2SConfirmDrawPacket::handle
        );

        // --- S2C: Gacha draw result ---
        CHANNEL.registerMessage(
                packetId++,
                S2CDrawResultPacket.class,
                S2CDrawResultPacket::encode,
                S2CDrawResultPacket::decode,
                S2CDrawResultPacket::handle
        );

        // --- S2C: Gacha draw failed ---
        CHANNEL.registerMessage(
                packetId++,
                S2CDrawFailedPacket.class,
                S2CDrawFailedPacket::encode,
                S2CDrawFailedPacket::decode,
                S2CDrawFailedPacket::handle
        );

        // --- S2C: Sync quest markers ---
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncMarkersPacket.class,
                S2CSyncMarkersPacket::encode,
                S2CSyncMarkersPacket::decode,
                S2CSyncMarkersPacket::handle
        );
    }

    // ═══════════════════════════════════════════════════════
    // 服务端便捷发送方法
    // ═══════════════════════════════════════════════════════

    /**
     * 全量同步（登录/重生/维度切换）
     */
    public static void syncFullData(ServerPlayer player, IQuestCapability cap) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncFullDataPacket(cap));
        syncMarkers(player, cap);
    }

    /**
     * 单任务状态同步
     */
    public static void syncQuestState(ServerPlayer player, QuestRuntimeData data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncQuestStatePacket(data));

        pushSyncForActiveUIs(player, null, "quest_state_sync");
    }

    /**
     * 增量进度同步（小包）
     */
    public static void syncDeltaProgress(ServerPlayer player,
                                         String questId,
                                         String phaseId,
                                         int objectiveIndex,
                                         int newProgress) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CDeltaProgressPacket(questId, phaseId, objectiveIndex, newProgress));

        pushSyncForActiveUIs(player, null, "delta_progress_sync");
    }

    public static void syncDeltaProgress(ServerPlayer player,
                                         String questId,
                                         int objectiveIndex,
                                         int newProgress) {
        syncDeltaProgress(player, questId, "", objectiveIndex, newProgress);
    }

    /**
     * Flags / Variables 同步
     */
    public static void syncFlagsAndVars(ServerPlayer player, IQuestCapability cap) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncFlagsVarsPacket(cap));

        pushSyncForActiveUIs(player, cap, "flags_vars_sync");
    }

    /**
     * Quest 同步后统一触发 Trade + Gacha 活跃界面 push-first。
     */
    private static void pushSyncForActiveUIs(ServerPlayer player,
                                             @Nullable IQuestCapability cap,
                                             String reason) {
        SyncObservability.trace("quest", "active_ui", player.getName().getString(), SyncObservability.Stage.ACTION, reason);
        C2SRequestTradePacket.pushSyncForActiveShop(player, reason);

        IQuestCapability resolved = (cap != null) ? cap : QuestCapabilityProvider.getOrNull(player);
        if (resolved != null) {
            GachaScreenOpener.pushSync(player, resolved, reason);
        }
    }

    // ═══════════════════════════════════════════════════════
    // 客户端便捷发送方法
    // ═══════════════════════════════════════════════════════

    public static void sendQuestAction(C2SRequestQuestActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendDialogueChoice(C2SDialogueChoicePacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendTradeRequest(C2SRequestTradePacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendTradeSyncRequest(C2SRequestTradeSyncPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendGachaControl(C2SGachaControlPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendDrawGacha(C2SDrawGachaPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendConfirmDraw(C2SConfirmDrawPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    // ═══════════════════════════════════════════════════════
    // 服务端定向发送（S2C）
    // ═══════════════════════════════════════════════════════

    public static void sendToPlayer(ServerPlayer player, S2COpenDialoguePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendTradePacket(ServerPlayer player, S2COpenTradePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendTradeStatePacket(ServerPlayer player, S2CSyncTradeStatePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendGachaStatePacket(ServerPlayer player, S2CGachaStatePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendDrawResultPacket(ServerPlayer player, S2CDrawResultPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendDrawFailedPacket(ServerPlayer player, S2CDrawFailedPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void syncMarkers(ServerPlayer player, IQuestCapability cap) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CSyncMarkersPacket());

        List<S2CSyncMarkersPacket.MarkerEntry> entries = cap.getAllMarkers().values().stream()
                .map(ArcQuestNetwork::toMarkerEntry)
                .toList();

        if (!entries.isEmpty()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CSyncMarkersPacket(entries));
        }
    }

    private static S2CSyncMarkersPacket.MarkerEntry toMarkerEntry(QuestMarkerData m) {
        return new S2CSyncMarkersPacket.MarkerEntry(
                m.getId(),
                m.getType().name(),
                m.getWorldX(),
                m.getWorldY(),
                m.getWorldZ(),
                m.getLabel(),
                m.getDimension(),
                m.getColorARGB(),
                m.getState().name(),
                m.isShowDistance(),
                m.isAllowOffscreenArrow()
        );
    }
}
