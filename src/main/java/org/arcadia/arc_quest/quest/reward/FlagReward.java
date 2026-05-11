package org.arcadia.arc_quest.quest.reward;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;

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
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return;
        if (set) {
            cap.setFlag(flag);
        } else {
            cap.removeFlag(flag);
        }
    }

    @Override
    public String describe() {
        return (set ? "SetFlag" : "ClearFlag") + "(" + flag + ")";
    }
}
