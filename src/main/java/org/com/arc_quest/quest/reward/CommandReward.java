package org.com.arc_quest.quest.reward;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.IReward;

/**
 * 以服务器权限执行指令的奖励。
 * 支持占位符 {player} 会被替换为玩家名。
 */
public final class CommandReward implements IReward {

    private final String commandTemplate;

    public CommandReward(String commandTemplate) {
        this.commandTemplate = commandTemplate;
    }

    @Override
    public void grant(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        String resolved = this.commandTemplate.replace("{player}", player.getGameProfile().getName());
        CommandSourceStack source = server.createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(4);

        server.getCommands().performPrefixedCommand(source, resolved);
    }

    @Override
    public String describe() {
        return "Command(" + this.commandTemplate + ")";
    }
}