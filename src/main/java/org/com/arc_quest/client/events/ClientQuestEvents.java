package org.com.arc_quest.client.events;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.client.gui.render.QuestSplashRenderer;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.SplashType;

@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, value = Dist.CLIENT)
public class ClientQuestEvents {

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        QuestSplashRenderer.render(event.getGuiGraphics(), event.getPartialTick(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight());
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (QuestSplashRenderer.isActive()) {
            QuestSplashRenderer.render(event.getGuiGraphics(), event.getPartialTick(),
                    event.getScreen().width,
                    event.getScreen().height);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseClickPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (QuestSplashRenderer.isActive()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenKeyPressPre(ScreenEvent.KeyPressed.Pre event) {
        if (QuestSplashRenderer.isActive()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenScrollPre(ScreenEvent.MouseScrolled.Pre event) {
        if (QuestSplashRenderer.isActive()) event.setCanceled(true);
    }

    public static void handleVisualTrigger(QuestDefinition quest, SplashType type, String phaseName) {
        if (quest == null) return;
        quest.getSplashConfig(type).ifPresent(asset -> {
            QuestSplashRenderer.trigger(quest, type, asset.texture());
        });
    }
}