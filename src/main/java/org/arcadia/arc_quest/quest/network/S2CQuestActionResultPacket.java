package org.arcadia.arc_quest.quest.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;

/**
 * S2C：Quest action 的标准结果回包（含 reject code）。
 */
public final class S2CQuestActionResultPacket implements CustomPacketPayload {

    public static final Type<S2CQuestActionResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "quest_action_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CQuestActionResultPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CQuestActionResultPacket::encode, S2CQuestActionResultPacket::decode);

    private final C2SRequestQuestActionPacket.Action action;
    private final String questId;
    private final String codeName;

    public S2CQuestActionResultPacket(C2SRequestQuestActionPacket.Action action,
                                      String questId,
                                      QuestRejectCodeDictionary.Code code) {
        this.action = action;
        this.questId = questId;
        codeName = (code != null ? code : QuestRejectCodeDictionary.Code.UNKNOWN).name();
    }

    private S2CQuestActionResultPacket(C2SRequestQuestActionPacket.Action action,
                                       String questId,
                                       String codeName) {
        this.action = action;
        this.questId = questId;
        this.codeName = codeName;
    }

    public static void encode(S2CQuestActionResultPacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
        buf.writeUtf(pkt.questId);
        buf.writeUtf(pkt.codeName);
    }

    public static S2CQuestActionResultPacket decode(FriendlyByteBuf buf) {
        return new S2CQuestActionResultPacket(
                buf.readEnum(C2SRequestQuestActionPacket.Action.class),
                buf.readUtf(),
                buf.readUtf()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CQuestActionResultPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            QuestRejectCodeDictionary.Code code;
            try {
                code = QuestRejectCodeDictionary.Code.valueOf(pkt.codeName);
            } catch (Exception ignored) {
                code = QuestRejectCodeDictionary.Code.UNKNOWN;
            }

            if (code == QuestRejectCodeDictionary.Code.OK) {
                return;
            }

            Component msg = toClientMessage(pkt.action, pkt.questId, code);
            mc.player.displayClientMessage(msg, true);
        });
    }

    private static Component toClientMessage(C2SRequestQuestActionPacket.Action action,
                                             String questId,
                                             QuestRejectCodeDictionary.Code code) {
        String actionKey = switch (action) {
            case ACCEPT -> "arc_quest.quest_action.accept";
            case ABANDON -> "arc_quest.quest_action.abandon";
            case CHOOSE -> "arc_quest.quest_action.choose";
            case OPEN_CHAPTER_SHOP -> "arc_quest.quest_action.open_chapter_shop";
            case CLAIM_COLLECTION_REWARD -> "arc_quest.quest_action.claim_collection_reward";
            case CONFIRM_PHASE_ADVANCE -> "arc_quest.quest_action.confirm_phase_advance";
        };

        String reasonKey = switch (code) {
            case QUEST_NOT_FOUND -> "arc_quest.quest_reject.quest_not_found";
            case ALREADY_ACTIVE -> "arc_quest.quest_reject.already_active";
            case ALREADY_COMPLETED_NOT_REPEATABLE -> "arc_quest.quest_reject.already_completed_not_repeatable";
            case UNLOCK_CONDITION_NOT_MET -> "arc_quest.quest_reject.unlock_condition_not_met";
            case NO_INITIAL_PHASE -> "arc_quest.quest_reject.no_initial_phase";
            case NOT_ACTIVE -> "arc_quest.quest_reject.not_active";
            case ABANDON_NOT_ALLOWED -> "arc_quest.quest_reject.abandon_not_allowed";
            case PHASE_NOT_FOUND -> "arc_quest.quest_reject.phase_not_found";
            case INVALID_CHOICE_INDEX -> "arc_quest.quest_reject.invalid_choice_index";
            case CHOICE_CONDITION_NOT_MET -> "arc_quest.quest_reject.choice_condition_not_met";
            case CHOICE_TARGET_PHASE_MISSING -> "arc_quest.quest_reject.choice_target_phase_missing";
            case CHAPTER_SHOP_NOT_CONFIGURED -> "arc_quest.quest_reject.chapter_shop_not_configured";
            case CHAPTER_SHOP_NOT_ACCESSIBLE -> "arc_quest.quest_reject.chapter_shop_not_accessible";
            case CHAPTER_SHOP_DEFINITION_NOT_FOUND -> "arc_quest.quest_reject.chapter_shop_definition_not_found";
            case COLLECTION_REWARD_ID_INVALID -> "arc_quest.quest_reject.collection_reward_id_invalid";
            case COLLECTION_REWARD_NOT_UNLOCKED -> "arc_quest.quest_reject.collection_reward_not_unlocked";
            case COLLECTION_REWARD_ALREADY_CLAIMED -> "arc_quest.quest_reject.collection_reward_already_claimed";
            case COLLECTION_REWARD_NOT_MANUAL -> "arc_quest.quest_reject.collection_reward_not_manual";
            case COLLECTION_REWARD_NODE_NOT_FOUND -> "arc_quest.quest_reject.collection_reward_node_not_found";
            case COLLECTION_DATA_MISSING -> "arc_quest.quest_reject.collection_data_missing";
            case COLLECTION_CONFIG_MISSING -> "arc_quest.quest_reject.collection_config_missing";
            default -> "arc_quest.quest_reject.unknown";
        };

        return Component.translatable("arc_quest.quest_action_result.template",
                Component.translatable(actionKey),
                Component.translatable(reasonKey),
                questId);
    }
}
