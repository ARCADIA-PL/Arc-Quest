package org.com.arc_quest.quest.reward;

import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.IReward;

/**
 * 设置/移除全局 Flag 的"奖励"。
 * 实际上是副作用，但用 IReward 管道统一处理最简洁。
 * 注意：真正的 flag 写入在 Phase 2 Capability 里实现，
 * 此处先定义好结构，grant() 中通过 Capability 写入。
 */
public final class FlagReward implements IReward {

    private final String flag;
    private final boolean set; // true = 设置, false = 移除

    public FlagReward(String flag, boolean set) {
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
        return this.flag;
    }

    public boolean isSet() {
        return this.set;
    }

    @Override
    public void grant(ServerPlayer player) {
        // 暂留接口
    }

    @Override
    public String describe() {
        return (this.set ? "SetFlag" : "ClearFlag") + "(" + this.flag + ")";
    }
}