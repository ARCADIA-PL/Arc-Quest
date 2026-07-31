package org.arcadia.arc_quest.trade.offer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.util.CommandExecutor;

/**
 * 命令交易物 —— 以服务器权限（op级别4）执行命令。
 * <p>
 * 通常仅作为奖励侧使用。支持 {@code {player}} 占位符。
 * 命令执行委托 {@link CommandExecutor#runAsServer}。
 */
public final class CommandTradeOffer implements ITradeOffer {

    private final String commandTemplate;
    private final Component displayText;
    private final boolean executeAsPlayer;

    public CommandTradeOffer(String commandTemplate, Component displayText) {
        this(commandTemplate, displayText, false);
    }

    public CommandTradeOffer(String commandTemplate, Component displayText, boolean executeAsPlayer) {
        this.commandTemplate = commandTemplate;
        this.displayText = displayText;
        this.executeAsPlayer = executeAsPlayer;
    }

    public CommandTradeOffer(String commandTemplate) {
        this(commandTemplate, Component.translatable("arc_quest.trade.command", commandTemplate));
    }

    public CommandTradeOffer(String commandTemplate, boolean executeAsPlayer) {
        this(commandTemplate, Component.translatable("arc_quest.trade.command", commandTemplate), executeAsPlayer);
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
        if (executeAsPlayer) {
            CommandExecutor.runAsPlayer(player, commandTemplate);
        } else {
            CommandExecutor.runAsServer(player, commandTemplate);
        }
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
