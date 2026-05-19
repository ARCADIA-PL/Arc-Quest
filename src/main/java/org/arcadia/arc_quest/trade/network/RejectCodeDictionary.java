package org.arcadia.arc_quest.trade.network;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Trade/Gacha 统一拒绝原因码字典与 errorKey 映射。
 */
public final class RejectCodeDictionary {

    private RejectCodeDictionary() {
    }

    public static String errorKey(Domain domain, Code code) {
        return "arcquest."
                + domain.name().toLowerCase(Locale.ROOT)
                + ".reject."
                + code.name().toLowerCase(Locale.ROOT);
    }

    public static Code fromTradeErrorKey(@Nullable String errorKey) {
        if (errorKey == null || errorKey.isEmpty()) {
            return Code.UNKNOWN;
        }
        String key = errorKey.toLowerCase(Locale.ROOT);
        if (key.contains("cooldown") || key.contains("on_cooldown")) {
            return Code.SESSION_ON_COOLDOWN;
        }
        if (key.contains("max_purchases") || key.contains("limit")) {
            return Code.SESSION_MAX_DRAWS_REACHED;
        }
        if (key.contains("not_visible") || key.endsWith(".not_visible")) {
            return Code.SESSION_NOT_VISIBLE;
        }
        if (key.contains("condition") || key.contains("blocked")) {
            return Code.SESSION_CONDITION_NOT_MET;
        }
        if (key.contains("cannot_afford") || key.contains("afford") || key.contains("insufficient")) {
            return Code.CANNOT_AFFORD;
        }
        return Code.UNKNOWN;
    }

    public static Code fromGachaFailReasonName(@Nullable String failedReasonName) {
        if (failedReasonName == null || failedReasonName.isEmpty()) {
            return Code.UNKNOWN;
        }
        return switch (failedReasonName) {
            case "NOT_VISIBLE" -> Code.SESSION_NOT_VISIBLE;
            case "MAX_DRAWS_REACHED" -> Code.SESSION_MAX_DRAWS_REACHED;
            case "ON_COOLDOWN" -> Code.SESSION_ON_COOLDOWN;
            case "CONDITION_NOT_MET" -> Code.SESSION_CONDITION_NOT_MET;
            case "CANNOT_AFFORD" -> Code.CANNOT_AFFORD;
            default -> Code.UNKNOWN;
        };
    }

    public enum Domain {
        TRADE,
        GACHA
    }

    public enum Code {
        PLAYER_MISSING,
        DATA_MISSING,
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
