package org.com.arc_quest.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface MixinGameRendererAccessor {

    @Invoker("getFov")
    double arcQuest$invokeGetFov(Camera camera, float partialTick, boolean useFOVSetting);
}
