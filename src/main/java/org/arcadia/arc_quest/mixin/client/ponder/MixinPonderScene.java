package org.arcadia.arc_quest.mixin.client.ponder;

import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.ponder.IntelPonderUIStub;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 当 QuestIntelPanel 传入 null screen 调用 renderOverlay 时，
 * 注入一个动态尺寸的 stub PonderUI，使 TextWindowElement 能正确渲染。
 */
@Mixin(value = PonderScene.class, remap = false)
public class MixinPonderScene {

    private static void renderTextElementSafely(
            TextWindowElement twe, PonderScene scene,
            int w, int h, Font font,
            GuiGraphics graphics, float pt) {
        // 获取或构造一个 stub PonderUI
        PonderUI stub = IntelPonderUIStub.getOrCreate(w, h, font);
        if (stub == null) return;
        try {
            twe.render(scene, stub, graphics, pt);
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void arcQuest$injectStubScreen(PonderUI screen, GuiGraphics graphics,
                                           float partialTicks, CallbackInfo ci) {
        if (screen != null) return;

        PonderScene self = (PonderScene) (Object) this;
        int sceneW = QuestIntelPanel.getLastSceneAreaW();
        int sceneH = QuestIntelPanel.getLastSceneAreaH();
        Font font = Minecraft.getInstance().font;

        graphics.pose().pushPose();
        self.forEachVisible(PonderOverlayElement.class, element -> {
            if (element instanceof TextWindowElement twe) {
                // TextWindowElement 需要 screen.width / screen.height / getFontRenderer()
                // 用反射临时注入到已有 PonderUI 实例（来自已打开的屏幕），或创建轻量代理
                renderTextElementSafely(twe, self, sceneW, sceneH, font, graphics, partialTicks);
            } else {
                try {
                    element.render(self, null, graphics, partialTicks);
                } catch (Exception ignored) {
                }
            }
        });
        graphics.pose().popPose();
        ci.cancel();
    }
}
