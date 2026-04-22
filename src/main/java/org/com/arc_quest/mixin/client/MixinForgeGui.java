package org.com.arc_quest.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.com.arc_quest.client.gui.shop.AbstractTradeScreen;
import org.com.arc_quest.client.gui.dialogue.DialogueScreen;
import org.com.arc_quest.client.gui.quest.QuestJournalScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ForgeGui.class, remap = false)
public class MixinForgeGui {

    /**
     * 拦截 ForgeGui 的 shouldDrawSurvivalElements 方法
     * 从根源切断：血量、饥饿值、氧气槽、护甲值 的渲染逻辑
     */
    @Inject(method = "shouldDrawSurvivalElements", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcQuest$hideSurvivalElements(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.screen instanceof QuestJournalScreen ||
            mc.screen instanceof DialogueScreen ||
                mc.screen instanceof AbstractTradeScreen) {
            cir.setReturnValue(false);
        }
    }
}