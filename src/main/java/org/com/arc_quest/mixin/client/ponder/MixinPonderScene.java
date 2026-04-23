package org.com.arc_quest.mixin.client.ponder;

import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 允许 PonderScene.renderOverlay 在 screen 为 null 时安全跳过文字气泡渲染。
 * 用于在 QuestIntelPanel 等嵌入式场景中渲染 PonderScene 而无需实际的 PonderUI。
 */
@Mixin(value = PonderScene.class, remap = false)
public class MixinPonderScene {

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void arcQuest$guardNullScreen(PonderUI screen, GuiGraphics graphics, float partialTicks,
                                          CallbackInfo ci) {
        if (screen != null) return;

        // screen 为 null 时，用 Minecraft 的窗口尺寸模拟渲染文字气泡
        // 跳过依赖 screen 的 TextWindowElement，其余 PonderOverlayElement 正常渲染
        PonderScene self = (PonderScene) (Object) this;
        graphics.pose().pushPose();
        self.forEachVisible(PonderOverlayElement.class, e -> {
            // TextWindowElement 会调用 screen.width 等，跳过以防 NPE
            // 其他不依赖 screen 的 overlay 元素正常渲染
            try {
                e.render(self, null, graphics, partialTicks);
            } catch (NullPointerException ignored) {
                // TextWindowElement 调用 screen 时会 NPE，安全忽略
            }
        });
        graphics.pose().popPose();
        ci.cancel();
    }
}
