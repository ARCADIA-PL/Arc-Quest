package org.arcadia.arc_quest.client.events;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.gacha.GachaResultRenderer;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.QuestArcHudController;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.ArcQuestPanelInputRouter;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.splash.ArcQuestSplashManager;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public class ClientHudEvents {

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        if (GachaResultRenderer.INSTANCE.isActive()) {
            GachaResultRenderer.INSTANCE.render(event.getGuiGraphics(),
                    event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight(),
                    event.getPartialTick());
        }

    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof QuestJournalScreen)) {
            QuestArcHudController.INSTANCE.render(event.getGuiGraphics(), event.getPartialTick());
        }

        if (GachaResultRenderer.INSTANCE.isActive()) {
            GachaResultRenderer.INSTANCE.render(event.getGuiGraphics(),
                    event.getScreen().width,
                    event.getScreen().height,
                    event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onScreenMouseClickPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (ArcQuestPanelInputRouter.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton(), event.getScreen().width, event.getScreen().height)) {
            event.setCanceled(true);
            return;
        }

        if (ArcQuestSplashManager.isActive()) {
            if (ArcQuestSplashManager.mouseClicked()) {
                event.setCanceled(true);
            }
        }

        if (GachaResultRenderer.INSTANCE.isActive()) {
            event.setCanceled(true);
            if (event.getButton() == 0) {
                GachaResultRenderer.INSTANCE.mouseClicked();
            }
        }
    }

    @SubscribeEvent
    public static void onScreenMouseReleasePre(ScreenEvent.MouseButtonReleased.Pre event) {
        if (ArcQuestPanelInputRouter.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseDragPre(ScreenEvent.MouseDragged.Pre event) {
        if (ArcQuestPanelInputRouter.mouseDragged(event.getMouseX(), event.getMouseY(), 0)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenKeyPressPre(ScreenEvent.KeyPressed.Pre event) {
        if (ArcQuestPanelInputRouter.keyPressed(event.getKeyCode(), event.getScanCode())) {
            event.setCanceled(true);
            return;
        }

        if (ArcQuestSplashManager.isActive()) {
            event.setCanceled(true);
            return;
        }

        if (GachaResultRenderer.INSTANCE.isActive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenScrollPre(ScreenEvent.MouseScrolled.Pre event) {
        if (ArcQuestPanelInputRouter.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
            event.setCanceled(true);
            return;
        }

        if (ArcQuestSplashManager.isActive()) {
            event.setCanceled(true);
            return;
        }

        if (GachaResultRenderer.INSTANCE.isActive()) {
            event.setCanceled(true);
        }
    }

    public static void handleVisualTrigger(QuestDefinition quest, SplashType type, String phaseName) {
        if (quest == null) return;
        quest.getSplashConfig(type).ifPresent(asset -> {
            ArcQuestSplashManager.trigger(quest, type, asset.texture());
        });
    }
}
