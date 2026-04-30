package org.arcadia.arc_quest.quest.network;

import java.util.Locale;

/**
 * Quest 域统一拒绝码字典（用于 action 结果归因与日志聚合）。
 */
public final class QuestRejectCodeDictionary {

    private QuestRejectCodeDictionary() {
    }

    public static String errorKey(Code code) {
        return "arcquest.quest.reject." + code.name().toLowerCase(Locale.ROOT);
    }

    public enum Code {
        OK,
        QUEST_NOT_FOUND,
        ALREADY_ACTIVE,
        ALREADY_COMPLETED_NOT_REPEATABLE,
        UNLOCK_CONDITION_NOT_MET,
        NO_INITIAL_PHASE,

        NOT_ACTIVE,
        PHASE_NOT_FOUND,
        INVALID_CHOICE_INDEX,
        CHOICE_CONDITION_NOT_MET,
        CHOICE_TARGET_PHASE_MISSING,

        CHAPTER_SHOP_NOT_CONFIGURED,
        CHAPTER_SHOP_NOT_ACCESSIBLE,
        CHAPTER_SHOP_DEFINITION_NOT_FOUND,

        UNKNOWN
    }
}
