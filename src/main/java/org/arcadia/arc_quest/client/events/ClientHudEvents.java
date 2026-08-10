package org.arcadia.arc_quest.client.events;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.editor.quest.QuestEditorScreen;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.gacha.GachaResultRenderer;
import org.arcadia.arc_quest.client.hud.gacha.GachaScreen;
import org.arcadia.arc_quest.client.hud.guide.GuidePopupOverlay;
import org.arcadia.arc_quest.client.hud.guide.GuideSplashRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
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

        if (GuideSplashRenderer.isActive()) {
            GuideSplashRenderer.render(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth());
        }

        GuidePopupOverlay.INSTANCE.render(null, event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof QuestEditorScreen) return;
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

        if (GuideSplashRenderer.isActive()) {
            GuideSplashRenderer.render(event.getGuiGraphics(), event.getScreen().width);
        }
        GuidePopupOverlay.INSTANCE.render(event.getScreen(), event.getGuiGraphics(),
                event.getScreen().width, event.getScreen().height, event.getPartialTick());
    }

    @SubscribeEvent
    public static void onScreenMouseClickPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof QuestEditorScreen) return;
        if (GuidePopupOverlay.INSTANCE.isActive()) {
            GuidePopupOverlay.INSTANCE.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
            event.setCanceled(true);
            return;
        }

        if (QuestIntelPanel.isActive()) {
            if (event.getButton() == 0) {
                double mx = event.getMouseX();
                double my = event.getMouseY();
                int sw = event.getScreen().width;
                int sh = event.getScreen().height;
                if (event.getScreen() instanceof QuestJournalScreen qjs) {
                    float scale = qjs.getUiScale();
                    mx /= scale;
                    my /= scale;
                    sw = qjs.getScaledWidth();
                    sh = qjs.getScaledHeight();
                }
                QuestIntelPanel.handleMouseClick(mx, my, sw, sh);
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
    public static void onInputMouseClickPre(InputEvent.MouseButton.Pre event) {
        if (Minecraft.getInstance().screen != null) return;

        if (QuestSplashRenderer.isActive() && event.getButton() == 0) {
            if (QuestSplashRenderer.mouseClicked()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenKeyPressPre(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof QuestEditorScreen) return;
        if (GuidePopupOverlay.INSTANCE.isActive()) {
            GuidePopupOverlay.INSTANCE.keyPressed(event.getKeyCode());
            event.setCanceled(true);
            return;
        }

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
        if (event.getScreen() instanceof QuestEditorScreen) return;
        if (GuidePopupOverlay.INSTANCE.isActive()) {
            GuidePopupOverlay.INSTANCE.mouseScrolled(event.getScrollDelta());
            event.setCanceled(true);
            return;
        }

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

    @SubscribeEvent
    public static void onScreenMouseDraggedPre(ScreenEvent.MouseDragged.Pre event) {
        if (event.getScreen() instanceof QuestEditorScreen) return;
        if (!GuidePopupOverlay.INSTANCE.isActive()) return;
        GuidePopupOverlay.INSTANCE.mouseDragged(
                event.getMouseX(), event.getMouseY(), event.getMouseButton());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenMouseReleasedPre(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getScreen() instanceof QuestEditorScreen) return;
        if (!GuidePopupOverlay.INSTANCE.isActive()) return;
        GuidePopupOverlay.INSTANCE.mouseReleased(event.getButton());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        // 打开任务/对话/交易/抽卡界面时隐藏生存状态与聊天 HUD
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof QuestJournalScreen
                || mc.screen instanceof QuestEditorScreen
                || mc.screen instanceof DialogueScreen
                || mc.screen instanceof AbstractTradeScreen
                || mc.screen instanceof GachaScreen)) {
            return;
        }
        ResourceLocation overlayId = event.getOverlay().id();
        if (overlayId.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                || overlayId.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || overlayId.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                || overlayId.equals(VanillaGuiOverlay.AIR_LEVEL.id())
                || overlayId.equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
            event.setCanceled(true);
        }
    }

    public static void handleVisualTrigger(QuestDefinition quest, SplashType type, String phaseId) {
        if (quest == null) return;
        if ((type == SplashType.PHASE_START || type == SplashType.PHASE_COMPLETE)
                && phaseId != null && !phaseId.isEmpty()) {
            PhaseDefinition phase = quest.getPhase(phaseId);
            if (phase != null) {
                phase.getSplashConfig(type).ifPresent(asset ->
                        QuestSplashRenderer.trigger(quest, phase, type, asset.texture()));
            }
            return;
        }
        quest.getSplashConfig(type).ifPresent(asset -> {
            QuestSplashRenderer.trigger(quest, type, asset.texture());
        });
    }
}
