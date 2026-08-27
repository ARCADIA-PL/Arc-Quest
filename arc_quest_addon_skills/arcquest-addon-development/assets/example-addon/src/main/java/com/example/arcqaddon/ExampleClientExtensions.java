package com.example.arcqaddon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueOverlayRegistry;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerPresentationRegistry;

@Mod.EventBusSubscriber(
        modid = ExampleArcQuestAddon.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ExampleClientExtensions {
    private ExampleClientExtensions() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ExampleClientExtensions::registerClientExtensions);
    }

    private static void registerClientExtensions() {
        QuestMarkerPresentationRegistry.registerLabelResolver(marker ->
                marker.getId().startsWith(ExampleArcQuestAddon.MOD_ID + ":")
                        ? Component.translatable("marker.example_arcq_addon.target")
                        : null);

        QuestMarkerPresentationRegistry.registerColorResolver(marker ->
                marker.getId().startsWith(ExampleArcQuestAddon.MOD_ID + ":")
                        ? 0xFF73A85A
                        : null);

        DialogueOverlayRegistry.register(new DialogueOverlayRegistry.Overlay() {
            @Override
            public boolean shouldRender(DialogueScreen screen, String dialogueId) {
                return dialogueId.equals(ExampleDialogueContent.FOREMAN_DIALOGUE_ID.toString());
            }

            @Override
            public void render(GuiGraphics graphics, DialogueScreen screen, String dialogueId,
                               int mouseX, int mouseY, float partialTick) {
                graphics.fill(8, 8, 12, 28, 0xFF73A85A);
            }
        });
    }
}
