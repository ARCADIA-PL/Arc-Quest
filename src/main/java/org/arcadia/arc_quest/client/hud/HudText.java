package org.arcadia.arc_quest.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** HUD 文本翻译入口。 */
public final class HudText {
    private HudText() {
    }

    /** 返回可继续设置样式的 HUD 翻译组件。 */
    public static MutableComponent of(String key, Object... args) {
        return Component.translatable("arc_quest.hud." + key, args);
    }

    /** 返回 HUD 翻译文本的本地化字符串。 */
    public static String string(String key, Object... args) {
        return of(key, args).getString();
    }
}
