package org.com.arc_quest.trade.network;

import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.com.arc_quest.trade.registry.TradeRegistry;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Locale;

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
        reject(RejectCode.PLAYER_MISSING, action, null, shopId, "sender is null", logger);
        return null;
    }

    public static @Nullable IQuestCapability requireCapability(ServerPlayer player,
                                                               String action,
                                                               String shopId,
                                                               Logger logger) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        if (cap != null) {
            return cap;
        }
        reject(RejectCode.CAPABILITY_MISSING, action, player, shopId, "quest capability missing", logger);
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
        reject(RejectCode.SHOP_NOT_FOUND, action, player, shopId, "shop not found", logger);
        return null;
    }

    public static void reject(RejectCode code,
                              String action,
                              @Nullable ServerPlayer player,
                              String shopId,
                              String detail,
                              Logger logger) {
        String playerName = player != null ? player.getName().getString() : "-";
        logger.warn("[Trade-Guard] reject code={} action={} player={} shop={} detail={}",
                code.name(), action, playerName, shopId, detail);
    }

    public static String toErrorKey(RejectCode code) {
        return "arcquest.trade.reject." + code.name().toLowerCase(Locale.ROOT);
    }

    public enum RejectCode {
        PLAYER_MISSING,
        CAPABILITY_MISSING,
        SHOP_NOT_FOUND,
        UNKNOWN
    }
}
