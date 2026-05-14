package org.arcadia.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.slf4j.Logger;

/**
 * 管理员命令集（主注册入口）。
 */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ArcQuestCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("arcquest")
                        .requires(src -> src.hasPermission(2))
                        .then(QuestCommands.registerSubtree(dispatcher))
                        .then(DialogueCommands.registerSubtree(dispatcher))
                        .then(TradeCommands.registerSubtree(dispatcher))
                        .then(GachaCommands.registerSubtree(dispatcher))
                        .then(NpcCommands.registerSubtree(dispatcher))
                        .then(AdminCommands.registerSubtree(dispatcher))
                        .then(Commands.literal("reload_arcquest")
                                .executes(ArcQuestHotReloadCommand::reloadArcQuest))
        );

        MarkerTestCommand.register(dispatcher);
        ArcQuestHotReloadCommand.register(dispatcher);

        LOGGER.info("[ArcQuest] Commands registered with modular structure.");
    }
}
