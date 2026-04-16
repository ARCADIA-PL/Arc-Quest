package org.com.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变的单个目标定义。
 * <p>
 * 注意：这是"模板"而非运行时——运行时进度存储在 Capability 中 (Phase 2)。
 */
public final class ObjectiveEntry {

    private final ObjectiveType type;
    private final ResourceLocation targetId;
    private final int requiredCount;
    private final Component displayText;
    private final boolean hidden;
    private final boolean optional;
    private final Map<String, String> extraData;

    public ObjectiveEntry(ObjectiveType type,
                          ResourceLocation targetId,
                          int requiredCount,
                          Component displayText,
                          boolean hidden,
                          boolean optional,
                          Map<String, String> extraData) {
        Objects.requireNonNull(type, "ObjectiveType must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(displayText, "displayText must not be null");
        if (requiredCount < 1) {
            throw new IllegalArgumentException("requiredCount must be >= 1, got " + requiredCount);
        }
        this.type = type;
        this.targetId = targetId;
        this.requiredCount = requiredCount;
        this.displayText = displayText;
        this.hidden = hidden;
        this.optional = optional;
        this.extraData = Collections.unmodifiableMap(extraData);
    }

    public ObjectiveType getType() {
        return this.type;
    }

    public ResourceLocation getTargetId() {
        return this.targetId;
    }

    public int getRequiredCount() {
        return this.requiredCount;
    }

    public Component getDisplayText() {
        return this.displayText;
    }

    /** 隐藏目标——不在 HUD 上显示，直到完成后才揭示 */
    public boolean isHidden() {
        return this.hidden;
    }

    /** 可选目标——不阻塞阶段完成 */
    public boolean isOptional() {
        return this.optional;
    }

    /**
     * 扩展数据。
     * 例如 REACH_LOCATION 可存 "x","y","z","radius"；
     * CUSTOM 可存 "callback_class" 等。
     */
    public Map<String, String> getExtraData() {
        return this.extraData;
    }

    @Nullable
    public String getExtra(String key) {
        return this.extraData.get(key);
    }

    public int getExtraInt(String key, int fallback) {
        String val = this.extraData.get(key);
        if (val == null) return fallback;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return "ObjectiveEntry{" + type + " " + targetId + " x" + requiredCount + "}";
    }
}