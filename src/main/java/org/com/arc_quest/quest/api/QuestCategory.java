package org.com.arc_quest.quest.api;

import net.minecraft.network.chat.Component;

/**
 * 任务分类——对应 UI 中的 Tab 页签。
 * 仿星铁：开拓任务 / 同行任务 / 日常委托 / 冒险任务 / 活动任务
 */
public enum QuestCategory {

    ARCHON("archon", "arc_quest.category.archon", 0xFFD700),
    COMPANION("companion", "arc_quest.category.companion", 0x00BFFF),
    DAILY("daily", "arc_quest.category.daily", 0x90EE90),
    ADVENTURE("adventure", "arc_quest.category.adventure", 0xDDA0DD),
    EVENT("event", "arc_quest.category.event", 0xFF6347);

    private final String id;
    private final String translationKey;
    private final int themeColor;

    QuestCategory(String id, String translationKey, int themeColor) {
        this.id = id;
        this.translationKey = translationKey;
        this.themeColor = themeColor;
    }

    public String getId() {
        return this.id;
    }

    public Component getDisplayName() {
        return Component.translatable(this.translationKey);
    }

    public int getThemeColor() {
        return this.themeColor;
    }
}