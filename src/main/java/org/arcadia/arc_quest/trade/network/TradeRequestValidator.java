package org.arcadia.arc_quest.trade.network;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Trade C2S 请求统一校验与拒绝原因码。
 */
public final class TradeRequestValidator {

    private TradeRequestValidator() {
    }

    public static @Nullable ServerPlayer requirePlayer(@Nullable ServerPlayer player,
                                                       String action,
                                                       String shopId,
                                                       Logger logger) {
        if (player != null) {
            return player;
        }
        reject(RejectCodeDictionary.Code.PLAYER_MISSING, action, null, shopId, "sender is null", logger);
        return null;
    }

    public static @Nullable ArcQuestPlayer requireData(ServerPlayer player,
                                                       String action,
                                                       String shopId,
                                                       Logger logger) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data != null) {
            return data;
        }
        reject(RejectCodeDictionary.Code.DATA_MISSING, action, player, shopId, "quest capability missing", logger);
        return null;
    }

    public static @Nullable TradeShopDefinition requireShop(String shopId,
                                                            ServerPlayer player,
                                                            String action,
                                                            Logger logger) {
        TradeShopDefinition shop = TradeRegistry.get(shopId);
        if (shop != null) {
            return shop;
        }
        reject(RejectCodeDictionary.Code.SHOP_NOT_FOUND, action, player, shopId, "shop not found", logger);
        return null;
    }

    public static void reject(RejectCodeDictionary.Code code,
                              String action,
                              @Nullable ServerPlayer player,
                              String shopId,
                              String detail,
                              Logger logger) {
        String playerName = player != null ? player.getName().getString() : "-";
        logger.warn("[Trade-Guard] reject code={} action={} player={} shop={} detail={}",
                code.name(), action, playerName, shopId, detail);
    }

    public static String toErrorKey(RejectCodeDictionary.Code code) {
        return RejectCodeDictionary.errorKey(RejectCodeDictionary.Domain.TRADE, code);
    }
}
