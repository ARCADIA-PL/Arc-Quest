package org.com.arc_quest.mixin.client.ponder;

import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 PonderUI / Screen 的 width、height、font setter，供 IntelPonderUIStub 使用。 */
@Mixin(value = PonderUI.class, remap = false)
public interface MixinPonderUIStubAccessor {

    @Accessor("width")
    void arcQuest$setWidth(int width);

    @Accessor("height")
    void arcQuest$setHeight(int height);

    @Accessor("font")
    void arcQuest$setFont(Font font);
}
