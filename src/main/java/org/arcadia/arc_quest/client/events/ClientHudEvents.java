package org.arcadia.arc_quest.client.events;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.gacha.GachaResultRenderer;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public class ClientHudEvents {

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        QuestSplashRenderer.render(event.getGuiGraphics(), event.getPartialTick(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight());

        if (GachaResultRenderer.INSTANCE.isActive()) {
            GachaResultRenderer.INSTANCE.render(event.getGuiGraphics(),
                    event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight(),
                    event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (QuestSplashRenderer.isActive()) {
            QuestSplashRenderer.render(event.getGuiGraphics(), event.getPartialTick(),
                    event.getScreen().width,
                    event.getScreen().height);
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
        // Intel 面板优先处理：转发鼠标点击，然后 cancel 防止穿透
        if (QuestIntelPanel.isActive()) {
            if (event.getButton() == 0) {
                QuestIntelPanel.handleMouseClick(
                        event.getMouseX(), event.getMouseY(),
                        event.getScreen().width, event.getScreen().height);
            }
            event.setCanceled(true);
            return;
        }

        if (QuestSplashRenderer.isActive()) {
            if (QuestSplashRenderer.mouseClicked()) {
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
    public static void onScreenKeyPressPre(ScreenEvent.KeyPressed.Pre event) {
        // Intel 面板优先处理键盘
        if (QuestIntelPanel.isActive()) {
            int key = event.getKeyCode();
            int scan = event.getScanCode();
            Minecraft mc = Minecraft.getInstance();

            if (key == 256 || mc.options.keyInventory.matches(key, scan)) {
                QuestIntelPanel.dismiss();
            } else if (key == 32) {
                QuestIntelPanel.togglePause();
            } else if (mc.options.keyLeft.matches(key, scan)) {
                QuestIntelPanel.scrollBack();
            } else if (mc.options.keyRight.matches(key, scan)) {
                QuestIntelPanel.scrollForward();
            }
            event.setCanceled(true);
            return;
        }

        if (QuestSplashRenderer.isActive()) {
            event.setCanceled(true);
            return;
        }

        if (GachaResultRenderer.INSTANCE.isActive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenScrollPre(ScreenEvent.MouseScrolled.Pre event) {
        if (QuestIntelPanel.isActive()) {
            if (event.getScrollDelta() > 0) QuestIntelPanel.scrollBack();
            else if (event.getScrollDelta() < 0) QuestIntelPanel.scrollForward();
            event.setCanceled(true);
            return;
        }

        if (QuestSplashRenderer.isActive()) {
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
            QuestSplashRenderer.trigger(quest, type, asset.texture());
        });
    }
}
