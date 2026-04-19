package org.com.arc_quest.trade.offer;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.trade.api.ITradeOffer;

/**
 * 命令交易物 —— 以服务器权限执行命令。
 * <p>
 * 通常仅作为奖励侧使用。支持 {player} 占位符。
 */
public final class CommandTradeOffer implements ITradeOffer {

    private final String commandTemplate;
    private final Component displayText;

    public CommandTradeOffer(String commandTemplate, Component displayText) {
        this.commandTemplate = commandTemplate;
        this.displayText = displayText;
    }

    public CommandTradeOffer(String commandTemplate) {
        this(commandTemplate, Component.translatable("arc_quest.trade.command", commandTemplate));
    }

    public static CommandTradeOffer reward(String command, String description) {
        return new CommandTradeOffer(command, Component.translatable(description));
    }

    @Override
    public boolean canAfford(ServerPlayer player) {
        return true;
    }

    @Override
    public void execute(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        String resolved = commandTemplate.replace("{player}", player.getGameProfile().getName());
        String cmd = resolved.startsWith("/") ? resolved.substring(1) : resolved;
        CommandSourceStack source = server.createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(4);
        server.getCommands().performPrefixedCommand(source, cmd);
    }

    @Override
    public Component describe() {
        return displayText;
    }

    @Override
    public String getType() {
        return "command";
    }
}
