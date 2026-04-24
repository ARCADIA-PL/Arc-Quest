package org.com.arc_quest.trade.network;

import java.util.Locale;

/**
 * Trade/Gacha 统一拒绝原因码字典与 errorKey 映射。
 */
public final class RejectCodeDictionary {

    private RejectCodeDictionary() {
    }

    public enum Domain {
        TRADE,
        GACHA
    }

    public enum Code {
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

    public static String errorKey(Domain domain, Code code) {
        return "arcquest."
                + domain.name().toLowerCase(Locale.ROOT)
                + ".reject."
                + code.name().toLowerCase(Locale.ROOT);
    }
}
