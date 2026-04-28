package org.com.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Map;

public record QuestTextContext(
        @Nullable QuestDefinition quest,
        @Nullable PhaseDefinition phase,
        @Nullable String phaseId,
        Map<String, Object> vars
) {
    public QuestTextContext {
        vars = vars == null ? Map.of() : Map.copyOf(vars);
    }

    public static QuestTextContext empty() {
        return new QuestTextContext(null, null, null, Map.of());
    }

    public Component phaseDisplayNameOrEmpty() {
        return phase == null ? Component.empty() : phase.getDisplayName();
    }
}
