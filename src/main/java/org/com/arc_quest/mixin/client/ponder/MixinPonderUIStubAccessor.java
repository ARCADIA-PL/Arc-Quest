package org.com.arc_quest.mixin.client.ponder;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 Screen 的 width、height、font setter，供 IntelPonderUIStub 注入假尺寸。 */
@Mixin(Screen.class)
public interface MixinPonderUIStubAccessor {

    @Accessor("width")
    void arcQuest$setWidth(int width);

    @Accessor("height")
    void arcQuest$setHeight(int height);

    @Accessor("font")
    void arcQuest$setFont(Font font);
}
