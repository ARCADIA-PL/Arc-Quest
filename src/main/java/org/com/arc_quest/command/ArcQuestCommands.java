package org.com.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.slf4j.Logger;

/**
 * 管理员命令集（主注册入口）。
 * <p>
 * 采用模块化设计，将不同功能的命令拆分到独立的类中：
 * <ul>
 *   <li>{@link QuestCommands} - 任务管理命令</li>
 *   <li>{@link DialogueCommands} - 对话管理命令</li>
 *   <li>{@link TradeCommands} - 交易管理命令</li>
 *   <li>{@link GachaCommands} - 抽奖管理命令</li>
 *   <li>{@link AdminCommands} - 管理员功能命令</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ArcQuestCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 注册根命令并设置权限
        dispatcher.register(
                Commands.literal("arcquest")
                        .requires(src -> src.hasPermission(2))
                        // 委托给各个子模块注册具体命令
                        .then(QuestCommands.registerSubtree(dispatcher))
                        .then(DialogueCommands.registerSubtree(dispatcher))
                        .then(TradeCommands.registerSubtree(dispatcher))
                        .then(GachaCommands.registerSubtree(dispatcher))
                        .then(AdminCommands.registerSubtree(dispatcher))
        );

        LOGGER.info("[ArcQuest] Commands registered with modular structure.");
    }
}