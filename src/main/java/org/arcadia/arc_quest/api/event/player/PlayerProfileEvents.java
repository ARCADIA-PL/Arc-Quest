package org.arcadia.arc_quest.api.event.player;

import net.minecraftforge.eventbus.api.Event;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.UUID;

/**
 * 玩家 Arc Quest flag 与整数变量事件集合。
 * <p>事件反映每一次真实内存状态变化；事务回滚可能产生方向相反的后续事件。</p>
 */
public final class PlayerProfileEvents {
    private PlayerProfileEvents() {
    }

    public abstract static class ProfileEvent extends Event {
        private final UUID playerId;
        private final ArcQuestPlayer playerData;

        protected ProfileEvent(UUID playerId, ArcQuestPlayer playerData) {
            this.playerId = playerId;
            this.playerData = playerData;
        }

        public UUID getPlayerId() { return playerId; }
        public ArcQuestPlayer getPlayerData() { return playerData; }
    }

    /** flag 被添加或移除后触发。 */
    public static final class FlagChanged extends ProfileEvent {
        private final String flag;
        private final boolean present;

        public FlagChanged(UUID playerId, ArcQuestPlayer playerData, String flag, boolean present) {
            super(playerId, playerData);
            this.flag = flag;
            this.present = present;
        }

        public String getFlag() { return flag; }
        public boolean isPresent() { return present; }
    }

    /** 整数变量发生实际变化后触发。 */
    public static final class VariableChanged extends ProfileEvent {
        private final String variable;
        private final int oldValue;
        private final int newValue;

        public VariableChanged(UUID playerId, ArcQuestPlayer playerData, String variable,
                               int oldValue, int newValue) {
            super(playerId, playerData);
            this.variable = variable;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getVariable() { return variable; }
        public int getOldValue() { return oldValue; }
        public int getNewValue() { return newValue; }
        public int getDelta() { return newValue - oldValue; }
    }
}
