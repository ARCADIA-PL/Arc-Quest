package org.arcadia.arc_quest.guide.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;

public final class GuideTextContext {

    private final ServerPlayer player;
    @Nullable
    private final ArcQuestPlayer questData;
    private final Map<String, Object> vars;

    public GuideTextContext(ServerPlayer player, @Nullable ArcQuestPlayer questData, Map<String, Object> vars) {
        this.player = player;
        this.questData = questData;
        this.vars = vars == null ? Map.of() : Map.copyOf(vars);
    }

    public static GuideTextContext empty() {
        return new GuideTextContext(null, null, Map.of());
    }

    public static GuideTextContext of(ServerPlayer player, @Nullable ArcQuestPlayer questData) {
        return new GuideTextContext(player, questData, Map.of());
    }

    public ServerPlayer player() {
        return player;
    }

    @Nullable
    public ArcQuestPlayer questData() {
        return questData;
    }

    public Map<String, Object> vars() {
        return vars;
    }

    @Nullable
    public Object get(String key) {
        return vars.get(key);
    }
}
