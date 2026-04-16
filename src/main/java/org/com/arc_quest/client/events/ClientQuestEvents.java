package org.com.arc_quest.client.events;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.client.gui.render.QuestSplashRenderer;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.SplashType;

@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, value = Dist.CLIENT)
public class ClientQuestEvents {

    /**
     * 将 SplashRenderer 挂载到原版 GUI 渲染管线末端
     */
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        // 确保它覆盖在所有东西的最上层
        QuestSplashRenderer.render(event.getGuiGraphics(), event.getPartialTick(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight());
    }

    /**
     * 供你的网络包处理器（PacketHandler）调用的公共方法。
     * 当收到服务器同步任务状态时调用此方法。
     */
    public static void handleVisualTrigger(QuestDefinition quest, SplashType type, String phaseName) {
        quest.getSplashConfig(type).ifPresent(asset -> {
            String title = quest.getDisplayName().getString();
            String subtitle = "";

            String finalSubtitle = subtitle;
            switch (type) {
                case QUEST_ACQUIRED -> subtitle = "ARES // NEW QUEST ACQUIRED";
                case QUEST_COMPLETED -> subtitle = "ARES // QUEST COMPLETED";
                case PHASE_START -> {
                    subtitle = "PHASE START // " + phaseName;
                    // 如果 Phase 自身配置了立绘，优先使用 Phase 的
                    if (phaseName != null && quest.getPhase(phaseName) != null) {
                        quest.getPhase(phaseName).getSplashConfig(SplashType.PHASE_START)
                                .ifPresent(pAsset -> QuestSplashRenderer.trigger(pAsset, title, finalSubtitle, quest.getThemeColor()));
                        return; // 提前返回以防止触发两次
                    }
                }
                case PHASE_COMPLETE -> subtitle = "PHASE COMPLETE // " + phaseName;
            }

            QuestSplashRenderer.trigger(asset, title, subtitle, quest.getThemeColor());
        });
    }
}