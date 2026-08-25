package org.arcadia.arc_quest.command;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;

/**
 * 管理员命令集（主注册入口）。
 */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public class ArcQuestCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("arcquest")
                        .requires(src -> src.hasPermission(2))
                        .then(QuestCommands.registerSubtree(dispatcher))
                        .then(DebugCommands.registerSubtree())
                        .then(DialogueCommands.registerSubtree(dispatcher))
                        .then(TradeCommands.registerSubtree(dispatcher))
                        .then(GachaCommands.registerSubtree(dispatcher))
                        .then(GuideCommands.registerSubtree(dispatcher))
                        .then(ArcQuestSnapshotCommands.registerSubtree(dispatcher))
                        .then(NpcCommands.registerSubtree(dispatcher))
                        .then(AdminCommands.registerSubtree(dispatcher))
                        .then(QuestEditorCommands.registerSubtree())
                        .then(Commands.literal("reload_arcquest")
                                .executes(ArcQuestHotReloadCommand::reloadArcQuest))
        );

        MarkerTestCommand.register(dispatcher);
        ArcQuestHotReloadCommand.register(dispatcher);

        ArcQuestLog.info(ArcQuestLog.Category.COMMAND, "Commands registered with modular structure.");
    }
}
