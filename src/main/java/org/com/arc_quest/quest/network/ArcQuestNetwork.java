package org.com.arc_quest.quest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestRuntimeData;

/**
 * Arc Quest 网络通信中心。
 * <p>
 * 使用 Forge {@link SimpleChannel} 进行 S2C / C2S 数据包注册与发送。
 */
public final class ArcQuestNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Arc_quest.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private ArcQuestNetwork() {}

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

        // ─── S2C：单目标进度同步 ───
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncObjectivePacket.class,
                S2CSyncObjectivePacket::encode,
                S2CSyncObjectivePacket::decode,
                S2CSyncObjectivePacket::handle
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
    }

    // ═══════════════════════════════════════════════════════
    //  便捷发送方法（服务端调用）
    // ═══════════════════════════════════════════════════════

    /** 全量同步 — 登录/重生/维度切换时使用 */
    public static void syncFullData(ServerPlayer player, IQuestCapability cap) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncFullDataPacket(cap));
    }

    /** 单任务状态同步 */
    public static void syncQuestState(ServerPlayer player, QuestRuntimeData data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncQuestStatePacket(data));
    }

    /** 单目标进度同步（轻量级，高频） */
    public static void syncObjectiveProgress(ServerPlayer player,
                                             String questId,
                                             int objIndex,
                                             int newProgress) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncObjectivePacket(questId, objIndex, newProgress));
    }

    /** Flags / Variables 同步 */
    public static void syncFlagsAndVars(ServerPlayer player, IQuestCapability cap) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncFlagsVarsPacket(cap));
    }

    // ═══════════════════════════════════════════════════════
    //  便捷发送方法（客户端调用）
    // ═══════════════════════════════════════════════════════

    /** 客户端发送任务操作请求 */
    public static void sendQuestAction(C2SRequestQuestActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }
}