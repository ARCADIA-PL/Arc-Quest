package org.arcadia.arc_quest.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 统一命令执行工具类。
 * <p>
 * 解决原有三处命令执行逻辑（{@code CommandReward}、{@code CommandTradeOffer}、
 * {@code DialogueAction.RunCommand}）权限模型不一致、占位符处理分散的问题。
 *
 * <h2>权限语义</h2>
 * <ul>
 *   <li>{@link #runAsServer} — op权限(4)，适合奖励/交易等"系统赋予"场景</li>
 *   <li>{@link #runAsPlayer} — 玩家自身权限，适合对话选项等"玩家触发"场景</li>
 * </ul>
 *
 * <h2>占位符</h2>
 * 两种方式均支持 {@code {player}} 替换为玩家名称。
 */
public final class CommandExecutor {

    private CommandExecutor() {
    }

    /**
     * 以服务器权限（op级别4）执行命令，适用于奖励/交易等服务端赋予场景。
     *
     * @param player   执行命令的关联玩家（用于占位符替换和上下文）
     * @param template 命令模板，支持 {@code {player}} 占位符，可含或不含前缀 "/"
     */
    public static void runAsServer(ServerPlayer player, String template) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        String resolved = resolvePlaceholders(player, template);
        String cmd = stripSlash(resolved);

        CommandSourceStack source = server.createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(4);
        server.getCommands().performPrefixedCommand(source, cmd);
    }

    /**
     * 以玩家自身权限执行命令，适用于对话选项等玩家触发场景。
     *
     * @param player   执行命令的玩家
     * @param template 命令模板，支持 {@code {player}} 占位符，可含或不含前缀 "/"
     */
    public static void runAsPlayer(ServerPlayer player, String template) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        String resolved = resolvePlaceholders(player, template);
        String cmd = stripSlash(resolved);

        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    /**
     * 替换命令模板中的 {@code {player}} 占位符为玩家名称。
     * 可在调用 {@link #runAsPlayer}/{@link #runAsServer} 前对模板做额外处理（如 session 变量替换），
     * 然后传入已替换好的字符串。
     */
    public static String resolvePlaceholders(ServerPlayer player, String template) {
        return template.replace("{player}", player.getGameProfile().getName());
    }

    private static String stripSlash(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }
}
