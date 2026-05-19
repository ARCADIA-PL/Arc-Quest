package org.arcadia.arc_quest.quest.reward;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

/**
 * 设置或移除全局 Flag 的奖励。
 */
public final class FlagReward implements IReward {

    private final String flag;
    private final boolean set;

    private FlagReward(String flag, boolean set) {
        this.flag = flag;
        this.set = set;
    }

    public static FlagReward set(String flag) {
        return new FlagReward(flag, true);
    }

    public static FlagReward clear(String flag) {
        return new FlagReward(flag, false);
    }

    public String getFlag() {
        return flag;
    }

    public boolean isSet() {
        return set;
    }

    @Override
    public void grant(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        if (set) {
            data.setFlag(flag);
        } else {
            data.removeFlag(flag);
        }
    }

    @Override
    public String describe() {
        return (set ? "SetFlag" : "ClearFlag") + "(" + flag + ")";
    }
}
