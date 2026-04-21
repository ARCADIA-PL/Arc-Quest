package org.com.arc_quest.dialogue.api;

import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 单个条件台词（SayIf）。
 * <p>
 * <b>每个 SayIf 分支必须有唯一的 ID</b>，用于事件监听、调试和网络同步。
 * </p>
 */
public record ConditionalSay(
        @Nonnull String sayId,      // 强制：分支标识符
        String text, 
        @Nullable SoundEvent soundEvent
) {
    
    /**
     * 创建 SayIf（必须提供 ID）。
     * 
     * @param sayId 分支标识符（不能为 null 或空）
     * @param text 文本内容
     * @return ConditionalSay 实例
     * @throws IllegalArgumentException 如果 sayId 为 null 或空
     */
    public static ConditionalSay of(String sayId, String text) {
        if (sayId == null || sayId.isEmpty()) {
            throw new IllegalArgumentException("SayIf ID cannot be null or empty");
        }
        return new ConditionalSay(sayId, text, null);
    }

    /**
     * 创建带音效的 SayIf（必须提供 ID）。
     * 
     * @param sayId 分支标识符（不能为 null 或空）
     * @param text 文本内容
     * @param sound 音效
     * @return ConditionalSay 实例
     * @throws IllegalArgumentException 如果 sayId 为 null 或空
     */
    public static ConditionalSay of(String sayId, String text, SoundEvent sound) {
        if (sayId == null || sayId.isEmpty()) {
            throw new IllegalArgumentException("SayIf ID cannot be null or empty");
        }
        return new ConditionalSay(sayId, text, sound);
    }
    
    /**
     * 检查是否有有效的 SayIf ID。
     */
    public boolean hasValidId() {
        return sayId != null && !sayId.isEmpty();
    }
}
