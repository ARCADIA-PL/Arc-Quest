package org.com.arc_quest.trade.gacha.network;

import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.com.arc_quest.trade.network.RejectCodeDictionary;

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
        reject(RejectCodeDictionary.Code.PLAYER_MISSING, action, null, shopId, "sender is null");
        return null;
    }

    public static @Nullable IQuestCapability requireCapability(ServerPlayer player, String action, String shopId) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        if (cap != null) {
            return cap;
        }
        reject(RejectCodeDictionary.Code.CAPABILITY_MISSING, action, player, shopId, "quest capability missing");
        return null;
    }

    public static @Nullable GachaShopDefinition requireShop(String shopId, ServerPlayer player, String action) {
        GachaShopDefinition shop = GachaRegistry.get(shopId);
        if (shop != null) {
            return shop;
        }
        reject(RejectCodeDictionary.Code.SHOP_NOT_FOUND, action, player, shopId, "shop not found");
        return null;
    }

    public static void reject(RejectCodeDictionary.Code code,
                              String action,
                              @Nullable ServerPlayer player,
                              String shopId,
                              String detail) {
        String playerName = player != null ? player.getName().getString() : "-";
        Arc_quest.LOGGER.warn("[Gacha-Guard] reject code={} action={} player={} shop={} detail={}",
                code.name(), action, playerName, shopId, detail);
    }

    public static RejectCodeDictionary.Code fromDrawFailedReasonName(@Nullable String failedReasonName) {
        return RejectCodeDictionary.fromGachaFailReasonName(failedReasonName);
    }

    public static String toErrorKey(RejectCodeDictionary.Code code) {
        return RejectCodeDictionary.errorKey(RejectCodeDictionary.Domain.GACHA, code);
    }
}
