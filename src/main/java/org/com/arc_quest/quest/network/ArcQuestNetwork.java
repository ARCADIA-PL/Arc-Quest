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
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.trade.gacha.network.*;
import org.com.arc_quest.trade.network.C2SRequestTradePacket;
import org.com.arc_quest.trade.network.S2COpenTradePacket;

/**
 * Arc Quest 网络通信中心。
 * <p>
 * 使用 Forge {@link SimpleChannel} 进行 S2C / C2S 数据包注册与发送。
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

        // --- C2S: Trade request ---
        CHANNEL.registerMessage(
                packetId++,
                C2SRequestTradePacket.class,
                C2SRequestTradePacket::encode,
                C2SRequestTradePacket::decode,
                C2SRequestTradePacket::handle
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

        // --- S2C: Open gacha ---
        CHANNEL.registerMessage(
                packetId++,
                S2COpenGachaPacket.class,
                S2COpenGachaPacket::encode,
                S2COpenGachaPacket::decode,
                S2COpenGachaPacket::handle
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
        
        // --- C2S: Request to open gacha screen ---
        CHANNEL.registerMessage(
                packetId++,
                C2SOpenGachaPacket.class,
                C2SOpenGachaPacket::encode,
                C2SOpenGachaPacket::decode,
                C2SOpenGachaPacket::handle
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
    //  便捷发送方法（服务端调用）
    // ═══════════════════════════════════════════════════════

    /**
     * 全量同步 — 登录/重生/维度切换时使用
     */
    public static void syncFullData(ServerPlayer player, IQuestCapability cap) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncFullDataPacket(cap));
    }

    /**
     * 单任务状态同步
     */
    public static void syncQuestState(ServerPlayer player, QuestRuntimeData data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncQuestStatePacket(data));
    }


    /**
     * 增量进度同步 - 仅同步变化的字段，体积极小。
     */
    public static void syncDeltaProgress(ServerPlayer player,
                                         String questId,
                                         int objectiveIndex,
                                         int newProgress) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CDeltaProgressPacket(questId, objectiveIndex, newProgress));
    }

    /**
     * Flags / Variables 同步
     */
    public static void syncFlagsAndVars(ServerPlayer player, IQuestCapability cap) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncFlagsVarsPacket(cap));
    }

    // ═══════════════════════════════════════════════════════
    //  便捷发送方法（客户端调用）
    // ═══════════════════════════════════════════════════════

    /**
     * 客户端发送任务操作请求
     */
    public static void sendQuestAction(C2SRequestQuestActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    /**
     * 客户端发送对话选择
     */
    public static void sendDialogueChoice(C2SDialogueChoicePacket packet) {
        CHANNEL.sendToServer(packet);
    }

    /**
     * 服务端发送对话打开包
     */
    public static void sendToPlayer(ServerPlayer player, S2COpenDialoguePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Client sends trade request
     */
    public static void sendTradeRequest(C2SRequestTradePacket packet) {
        CHANNEL.sendToServer(packet);
    }

    /**
     * Server sends trade packet
     */
    public static void sendTradePacket(ServerPlayer player, S2COpenTradePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}