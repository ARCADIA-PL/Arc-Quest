package org.arcadia.arc_quest.client.events;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.gacha.GachaResultRenderer;
import org.arcadia.arc_quest.client.hud.gacha.GachaScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;

@EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public class ClientHudEvents {

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        int w = event.getGuiGraphics().guiWidth();
        int h = event.getGuiGraphics().guiHeight();

        QuestSplashRenderer.render(event.getGuiGraphics(), partialTick, w, h);

        if (GachaResultRenderer.INSTANCE.isActive()) {
            GachaResultRenderer.INSTANCE.render(event.getGuiGraphics(), w, h, partialTick);
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
            if (event.getScrollDeltaY() > 0) QuestIntelPanel.scrollBack();
            else if (event.getScrollDeltaY() < 0) QuestIntelPanel.scrollForward();
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
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        // 打开任务/对话/交易/抽卡界面时隐藏血量/饥饿/护甲/氧气
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof QuestJournalScreen
                || mc.screen instanceof DialogueScreen
                || mc.screen instanceof AbstractTradeScreen
                || mc.screen instanceof GachaScreen)) {
            return;
        }
        ResourceLocation n = event.getName();
        if (n.equals(VanillaGuiLayers.PLAYER_HEALTH) || n.equals(VanillaGuiLayers.FOOD_LEVEL)
                || n.equals(VanillaGuiLayers.ARMOR_LEVEL) || n.equals(VanillaGuiLayers.AIR_LEVEL) || n.equals(VanillaGuiLayers.CHAT)) {
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
