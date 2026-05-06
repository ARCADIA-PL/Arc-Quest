package org.arcadia.arc_quest.client.events;

import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorScreen;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEditorCommandHandler {

    private ClientEditorCommandHandler() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("aq_editor")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc != null) {
                                mc.setScreen(new QuestEditorScreen());
                            }
                            return Command.SINGLE_SUCCESS;
                        })
        );
    }
}
