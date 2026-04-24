package org.com.arc_quest.trade.gacha.network;

import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;

import javax.annotation.Nullable;

/**
 * Gacha C2S 请求统一校验与拒绝原因码。
 */
public final class GachaRequestValidator {

    private GachaRequestValidator() {
    }

    public static @Nullable ServerPlayer requirePlayer(@Nullable ServerPlayer player, String action, String shopId) {
        if (player != null) {
            return player;
        }
        reject(GachaRejectCode.PLAYER_MISSING, action, null, shopId, "sender is null");
        return null;
    }

    public static @Nullable IQuestCapability requireCapability(ServerPlayer player, String action, String shopId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        if (cap != null) {
            return cap;
        }
        reject(GachaRejectCode.CAPABILITY_MISSING, action, player, shopId, "quest capability missing");
        return null;
    }

    public static @Nullable GachaShopDefinition requireShop(String shopId, ServerPlayer player, String action) {
        GachaShopDefinition shop = GachaRegistry.get(shopId);
        if (shop != null) {
            return shop;
        }
        reject(GachaRejectCode.SHOP_NOT_FOUND, action, player, shopId, "shop not found");
        return null;
    }

    public static void reject(GachaRejectCode code,
                              String action,
                              @Nullable ServerPlayer player,
                              String shopId,
                              String detail) {
        String playerName = player != null ? player.getName().getString() : "-";
        Arc_quest.LOGGER.warn("[Gacha-Guard] reject code={} action={} player={} shop={} detail={}",
                code.name(), action, playerName, shopId, detail);
    }

    public static GachaRejectCode fromDrawFailedReasonName(@Nullable String failedReasonName) {
        if (failedReasonName == null || failedReasonName.isEmpty()) {
            return GachaRejectCode.UNKNOWN;
        }
        return switch (failedReasonName) {
            case "NOT_VISIBLE" -> GachaRejectCode.SESSION_NOT_VISIBLE;
            case "MAX_DRAWS_REACHED" -> GachaRejectCode.SESSION_MAX_DRAWS_REACHED;
            case "ON_COOLDOWN" -> GachaRejectCode.SESSION_ON_COOLDOWN;
            case "CONDITION_NOT_MET" -> GachaRejectCode.SESSION_CONDITION_NOT_MET;
            case "CANNOT_AFFORD" -> GachaRejectCode.CANNOT_AFFORD;
            default -> GachaRejectCode.UNKNOWN;
        };
    }

    public enum GachaRejectCode {
        PLAYER_MISSING,
        CAPABILITY_MISSING,
        SHOP_NOT_FOUND,

        PRE_DRAW_CANCELLED,
        CANNOT_AFFORD,
        SESSION_NOT_VISIBLE,
        SESSION_MAX_DRAWS_REACHED,
        SESSION_ON_COOLDOWN,
        SESSION_CONDITION_NOT_MET,

        DRAW_RESULT_EMPTY,
        PENDING_STORE_FAILED,
        PENDING_CONFIRM_MISSING,

        UNKNOWN
    }
}
