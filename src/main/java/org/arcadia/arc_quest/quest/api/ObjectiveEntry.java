package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

public final class ObjectiveEntry {

    private final ObjectiveType type;
    private final ResourceLocation targetId;
    private final int requiredCount;
    private final QuestText displayText;
    private final boolean hidden;
    private final boolean optional;
    private final Map<String, String> extraData;
    @Nullable
    private final ToIntFunction<ServerPlayer> countModifier;

    public ObjectiveEntry(ObjectiveType type,
                          ResourceLocation targetId,
                          int requiredCount,
                          Component displayText,
                          boolean hidden,
                          boolean optional,
                          Map<String, String> extraData) {
        this(type, targetId, requiredCount, QuestText.component(displayText), hidden, optional, extraData, null);
    }

    public ObjectiveEntry(ObjectiveType type,
                          ResourceLocation targetId,
                          int requiredCount,
                          QuestText displayText,
                          boolean hidden,
                          boolean optional,
                          Map<String, String> extraData,
                          @Nullable ToIntFunction<ServerPlayer> countModifier) {
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
        this.countModifier = countModifier;
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

    public int resolveRequiredCount(@Nullable ServerPlayer player) {
        if (player == null || countModifier == null) return requiredCount;
        return Math.max(1, countModifier.applyAsInt(player));
    }

    public Component getDisplayText() {
        return this.displayText.resolve(null, QuestTextContext.empty());
    }

    public Component getDisplayText(@Nullable ServerPlayer player, @Nullable QuestTextContext ctx) {
        return this.displayText.resolve(player, ctx);
    }

    public QuestText getDisplayQuestText() {
        return this.displayText;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public boolean isOptional() {
        return this.optional;
    }

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
