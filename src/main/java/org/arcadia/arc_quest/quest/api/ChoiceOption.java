package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 对话/交互分支选项。
 * <p>
 * 当阶段设置了 choices 时，UI 弹出选项面板；
 * 玩家选择后：设置 flagToSet → 跳转到 targetPhaseId。
 */
public final class ChoiceOption {

    private final Component displayText;
    private final String flagToSet;
    private final String targetPhaseId;
    @Nullable
    private final ICondition visibleCondition;

    public ChoiceOption(Component displayText,
                        String flagToSet,
                        String targetPhaseId,
                        @Nullable ICondition visibleCondition) {
        Objects.requireNonNull(displayText);
        Objects.requireNonNull(flagToSet);
        Objects.requireNonNull(targetPhaseId);
        this.displayText = displayText;
        this.flagToSet = flagToSet;
        this.targetPhaseId = targetPhaseId;
        this.visibleCondition = visibleCondition;
    }

    public Component getDisplayText() {
        return this.displayText;
    }

    public String getFlagToSet() {
        return this.flagToSet;
    }

    public String getTargetPhaseId() {
        return this.targetPhaseId;
    }

    @Nullable
    public ICondition getVisibleCondition() {
        return this.visibleCondition;
    }
}