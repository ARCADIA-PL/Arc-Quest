package org.arcadia.arc_quest.client.hud.quest.arcmutil.splash;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;

public final class ArcQuestSplashViewModel {
    public static final float TIME_ENTER = 700f;
    public static final float TIME_HOLD = 2900f;
    public static final float TIME_EXIT = 500f;
    public static final float MAX_DRIFT = 3.0f;
    public static final float FLY_DISTANCE = 4.0f;
    public static final int FRAME_W = 400;
    public static final int FRAME_H = 225;

    public final QuestDefinition quest;
    public final SplashType type;
    public final ResourceLocation texture;
    public final long startTime;
    public State state = State.ENTER;
    public long exitStartTime = 0L;
    public float skipStartX = 0f;
    public float lastRenderX = 0f;

    public ArcQuestSplashViewModel(QuestDefinition quest, SplashType type, ResourceLocation texture, long startTime) {
        this.quest = quest;
        this.type = type;
        this.texture = texture;
        this.startTime = startTime;
    }

    public boolean update(long now) {
        long elapsed = now - startTime;
        if (state == State.ENTER && elapsed >= TIME_ENTER) {
            state = State.HOLD;
        }
        if (state == State.HOLD && elapsed >= TIME_ENTER + TIME_HOLD) {
            state = State.EXIT;
            exitStartTime = now;
            skipStartX = lastRenderX;
        }
        return state != State.EXIT || now - exitStartTime < TIME_EXIT;
    }

    public int themeColor() {
        return type == SplashType.QUEST_FAILED ? 0xFF1111 : quest.getVisualConfig().getThemeColor();
    }

    public enum State {ENTER, HOLD, EXIT}
}
