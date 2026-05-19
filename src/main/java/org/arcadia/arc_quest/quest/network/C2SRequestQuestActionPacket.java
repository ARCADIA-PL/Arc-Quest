package org.arcadia.arc_quest.quest.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.arcadia.arc_quest.api.event.ChapterShopOpenEvent;
import org.arcadia.arc_quest.quest.api.ChapterShopType;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary.Code;
import org.arcadia.arc_quest.quest.network.SyncObservability.Reason;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.gacha.runtime.GachaScreenOpener;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * C2S：客户端请求任务操作。
 */
public class C2SRequestQuestActionPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Action action;
    private final String questId;
    private final int transitionIndex;
    private final String phaseId; // 仅 CHOOSE 时有效；其余可为空字符串

    private C2SRequestQuestActionPacket(Action action, String questId, int transitionIndex, String phaseId) {
        this.action = action;
        this.questId = questId;
        this.transitionIndex = transitionIndex;
        this.phaseId = phaseId == null ? "" : phaseId;
    }

    public static C2SRequestQuestActionPacket accept(String questId) {
        return new C2SRequestQuestActionPacket(Action.ACCEPT, questId, -1, "");
    }

    public static C2SRequestQuestActionPacket abandon(String questId) {
        return new C2SRequestQuestActionPacket(Action.ABANDON, questId, -1, "");
    }

    // 新：带 phaseId 的 choose
    public static C2SRequestQuestActionPacket choose(String questId, String phaseId, int transitionIndex) {
        return new C2SRequestQuestActionPacket(Action.CHOOSE, questId, transitionIndex, phaseId);
    }

    // 兼容旧调用：phaseId 为空
    public static C2SRequestQuestActionPacket choose(String questId, int transitionIndex) {
        return new C2SRequestQuestActionPacket(Action.CHOOSE, questId, transitionIndex, "");
    }

    public static C2SRequestQuestActionPacket openChapterShop(String questId) {
        return new C2SRequestQuestActionPacket(Action.OPEN_CHAPTER_SHOP, questId, -1, "");
    }

    public static C2SRequestQuestActionPacket confirmPhaseAdvance(String questId, String phaseId) {
        return new C2SRequestQuestActionPacket(Action.CONFIRM_PHASE_ADVANCE, questId, -1, phaseId);
    }

    public static void encode(C2SRequestQuestActionPacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
        buf.writeUtf(pkt.questId, 256);
        buf.writeVarInt(pkt.transitionIndex);
        buf.writeUtf(pkt.phaseId, 256);
    }

    public static C2SRequestQuestActionPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String questId = buf.readUtf(256);
        int idx = buf.readVarInt();
        String phaseId = buf.readUtf(256);
        return new C2SRequestQuestActionPacket(action, questId, idx, phaseId);
    }

    public static void handle(C2SRequestQuestActionPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            switch (pkt.action) {
                case ACCEPT -> {
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.ACTION, Reason.QUEST_ACCEPT);
                    Code code = QuestProgressHandler.acceptQuestWithCode(sender, pkt.questId);
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.RESULT, toResultReason(pkt.action, code));
                    LOGGER.debug("[ArcQuest] C2S ACCEPT quest={}, code={}, player={}",
                            pkt.questId, code, sender.getGameProfile().getName());
                    ArcQuestNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new S2CQuestActionResultPacket(pkt.action, pkt.questId, code)
                    );
                }
                case ABANDON -> {
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.ACTION, Reason.QUEST_ABANDON);
                    Code code = QuestProgressHandler.abandonQuestWithCode(sender, pkt.questId);
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.RESULT, toResultReason(pkt.action, code));
                    LOGGER.debug("[ArcQuest] C2S ABANDON quest={}, code={}, player={}",
                            pkt.questId, code, sender.getGameProfile().getName());
                    ArcQuestNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new S2CQuestActionResultPacket(pkt.action, pkt.questId, code)
                    );
                }
                case CHOOSE -> {
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.ACTION, Reason.QUEST_CHOOSE);
                    Code code = QuestProgressHandler.handlePlayerChoiceWithCode(
                            sender, pkt.questId, pkt.phaseId, pkt.transitionIndex
                    );
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.RESULT, toResultReason(pkt.action, code));
                    LOGGER.debug("[ArcQuest] C2S CHOOSE quest={}, phase={}, idx={}, code={}, player={}",
                            pkt.questId, pkt.phaseId, pkt.transitionIndex, code, sender.getGameProfile().getName());
                    ArcQuestNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new S2CQuestActionResultPacket(pkt.action, pkt.questId, code)
                    );
                }
                case CONFIRM_PHASE_ADVANCE -> {
                    Code code = QuestProgressHandler.confirmManualPhaseAdvance(sender, pkt.questId, pkt.phaseId);
                    ArcQuestNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new S2CQuestActionResultPacket(pkt.action, pkt.questId, code)
                    );
                }
                case OPEN_CHAPTER_SHOP -> {
                    Code code = openChapterShopWithCode(sender, pkt.questId);
                    SyncObservability.trace("quest", pkt.questId, sender.getGameProfile().getName(),
                            SyncObservability.Stage.RESULT, toResultReason(pkt.action, code));
                    LOGGER.debug("[ArcQuest] C2S OPEN_CHAPTER_SHOP quest={}, code={}, player={}",
                            pkt.questId, code, sender.getGameProfile().getName());
                    String resolvedShopId = resolveChapterShopId(pkt.questId);
                    MinecraftForge.EVENT_BUS.post(new ChapterShopOpenEvent(sender, pkt.questId, resolvedShopId, code));
                    ArcQuestNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new S2CQuestActionResultPacket(pkt.action, pkt.questId, code)
                    );
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static Reason toResultReason(Action action, Code code) {
        boolean ok = code == Code.OK;
        return switch (action) {
            case ACCEPT -> ok ? Reason.QUEST_ACCEPT_SUCCESS : Reason.QUEST_ACCEPT_REJECTED;
            case ABANDON -> ok ? Reason.QUEST_ABANDON_SUCCESS : Reason.QUEST_ABANDON_REJECTED;
            case CHOOSE -> ok ? Reason.QUEST_CHOOSE_SUCCESS : Reason.QUEST_CHOOSE_REJECTED;
            case OPEN_CHAPTER_SHOP -> ok ? Reason.QUEST_CHOOSE_SUCCESS : Reason.QUEST_CHOOSE_REJECTED;
            case CONFIRM_PHASE_ADVANCE -> ok ? Reason.QUEST_CHOOSE_SUCCESS : Reason.QUEST_CHOOSE_REJECTED;
            case CLAIM_COLLECTION_REWARD -> ok ? Reason.QUEST_CHOOSE_SUCCESS : Reason.QUEST_CHOOSE_REJECTED;
        };
    }

    private static Code openChapterShopWithCode(ServerPlayer player, String questId) {
        ResourceLocation questRl = ResourceLocation.tryParse(questId);
        if (questRl == null) return Code.QUEST_NOT_FOUND;

        var def = QuestRegistry.get(questRl);
        if (def == null) return Code.QUEST_NOT_FOUND;
        if (!def.hasChapterShop()) return Code.CHAPTER_SHOP_NOT_CONFIGURED;

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return Code.NOT_ACTIVE;

        boolean canAccess = data.isQuestActive(questId)
                || (def.isChapterShopPersistent() && data.isQuestCompleted(questId));
        if (!canAccess) return Code.CHAPTER_SHOP_NOT_ACCESSIBLE;

        String shopId = def.getChapterShopId();
        if (shopId == null || shopId.isEmpty()) return Code.CHAPTER_SHOP_NOT_CONFIGURED;

        ChapterShopType shopType = def.getChapterShopType();
        if (shopType == null) shopType = ChapterShopType.TRADE;

        switch (shopType) {
            case TRADE -> {
                TradeShopDefinition shop = TradeRegistry.get(shopId);
                if (shop == null) return Code.CHAPTER_SHOP_DEFINITION_NOT_FOUND;
                C2SRequestTradePacket.handleServerOpen(player, shop, false);
                return Code.OK;
            }
            case GACHA -> {
                GachaShopDefinition shop = GachaRegistry.get(shopId);
                if (shop == null) return Code.CHAPTER_SHOP_DEFINITION_NOT_FOUND;
                GachaScreenOpener.openGachaScreen(player, shop, data);
                return Code.OK;
            }
            default -> {
                return Code.CHAPTER_SHOP_DEFINITION_NOT_FOUND;
            }
        }
    }

    private static String resolveChapterShopId(String questId) {
        ResourceLocation questRl = ResourceLocation.tryParse(questId);
        if (questRl == null) return "";
        var def = QuestRegistry.get(questRl);
        if (def == null || !def.hasChapterShop() || def.getChapterShopId() == null) return "";
        return def.getChapterShopId();
    }

    public enum Action {
        ACCEPT,
        ABANDON,
        CHOOSE,
        OPEN_CHAPTER_SHOP,
        CONFIRM_PHASE_ADVANCE,
        CLAIM_COLLECTION_REWARD
    }
}