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
     * 供网络包处理器调用的公共方法。
     * 当收到服务器同步任务状态时调用此方法触发立绘。
     */
    public static void handleVisualTrigger(QuestDefinition quest, SplashType type, String phaseName) {
        if (quest == null) return;

        // 根据类型获取对应的纹理资源
        quest.getSplashConfig(type).ifPresent(asset -> {
            QuestSplashRenderer.trigger(quest, type, asset.texture());
        });
    }
}