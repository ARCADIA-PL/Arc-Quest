package org.arcadia.arc_quest.client.events;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.QuestHudMigrationFlags;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class ClientQuestHudDebugCommands {
    private ClientQuestHudDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arcquestclient")
                .then(Commands.literal("tracker")
                        .then(Commands.literal("on").executes(ctx -> setTracker(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setTracker(ctx.getSource(), false)))
                        .then(Commands.literal("toggle").executes(ctx -> toggleTracker(ctx.getSource()))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("on").executes(ctx -> setDebug(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setDebug(ctx.getSource(), false)))
                        .then(Commands.literal("toggle").executes(ctx -> toggleDebug(ctx.getSource())))));
    }

    private static int setTracker(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        QuestHudMigrationFlags.setArcTrackerEnabled(enabled);
        source.sendSuccess(() -> Component.literal("ArcMutil Quest Tracker: " + (enabled ? "ON" : "OFF")), false);
        return 1;
    }

    private static int toggleTracker(net.minecraft.commands.CommandSourceStack source) {
        QuestHudMigrationFlags.toggleArcTracker();
        source.sendSuccess(() -> Component.literal("ArcMutil Quest Tracker: " + (QuestHudMigrationFlags.ARC_TRACKER_ENABLED ? "ON" : "OFF")), false);
        return 1;
    }

    private static int setDebug(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        QuestHudMigrationFlags.setArcDebugOverlay(enabled);
        source.sendSuccess(() -> Component.literal("ArcMutil Quest HUD Debug: " + (enabled ? "ON" : "OFF")), false);
        return 1;
    }

    private static int toggleDebug(net.minecraft.commands.CommandSourceStack source) {
        QuestHudMigrationFlags.toggleDebugOverlay();
        source.sendSuccess(() -> Component.literal("ArcMutil Quest HUD Debug: " + (QuestHudMigrationFlags.ARC_DEBUG_OVERLAY ? "ON" : "OFF")), false);
        return 1;
    }
}
