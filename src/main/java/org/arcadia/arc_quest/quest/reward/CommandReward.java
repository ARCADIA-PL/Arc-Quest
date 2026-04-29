package org.arcadia.arc_quest.quest.reward;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.util.CommandExecutor;

/**
 * 以服务器权限（op级别4）执行命令的奖励。
 * <p>
 * 支持 {@code {player}} 占位符，会被替换为玩家名称。
 * 命令执行委托 {@link CommandExecutor#runAsServer}。
 */
public final class CommandReward implements IReward {

    private final String commandTemplate;

    public CommandReward(String commandTemplate) {
        this.commandTemplate = commandTemplate;
    }

    @Override
    public void grant(ServerPlayer player) {
        CommandExecutor.runAsServer(player, this.commandTemplate);
    }

    @Override
    public String describe() {
        return "Command(" + this.commandTemplate + ")";
    }
}
