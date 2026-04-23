package org.com.arc_quest.client.gui.render;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.mixin.client.ponder.MixinPonderUIStubAccessor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 最小可用的 PonderUI 实例，用于向 TextWindowElement 提供 width/height/font。
 *
 * <p>PonderUI 构造器需要 List&lt;PonderScene&gt;，无法直接 new；
 * 这里借助第一个场景注册的真实 sceneId 来构造，然后通过
 * {@link MixinPonderUIStubAccessor} 强制覆写宽高和字体。
 */
public final class IntelPonderUIStub {

    @Nullable
    private static PonderUI cached = null;
    private static int cachedW = -1;
    private static int cachedH = -1;

    private IntelPonderUIStub() {}

    /**
     * 获取或创建一个尺寸匹配的 stub PonderUI。
     * 如果 QuestIntelPanel 已有活跃场景，利用其 sceneId 构造；否则返回 null。
     */
    @Nullable
    public static PonderUI getOrCreate(int w, int h, Font font) {
        if (cachedW == w && cachedH == h && cached != null) {
            return cached;
        }

        // 尝试用当前激活的场景 ID 构造（不触发 PonderIndex 的完整注册流程）
        try {
            PonderUI ui = buildStub(w, h, font);
            if (ui != null) {
                cached = ui;
                cachedW = w;
                cachedH = h;
            }
            return cached;
        } catch (Exception e) {
            return null;
        }
    }

    /** 已过时的旧签名，保留兼容 */
    @Nullable
    public static PonderUI get(int w, int h, Font font) {
        return getOrCreate(w, h, font);
    }

    public static void invalidate() {
        cached = null;
        cachedW = -1;
        cachedH = -1;
    }

    @Nullable
    private static PonderUI buildStub(int w, int h, Font font) {
        // QuestIntelPanel 提供当前活跃的 sceneId
        List<PonderScene> scenes =
                QuestIntelPanel.getActiveScenes();
        if (scenes == null || scenes.isEmpty()) return null;

        // 用 PonderUI.of(ResourceLocation) 构造
        ResourceLocation loc = scenes.get(0).getLocation();
        PonderUI ui;
        try {
            ui = PonderUI.of(loc);
        } catch (Exception e) {
            return null;
        }

        // 通过 Mixin Accessor 覆写宽高和字体
        if (ui instanceof MixinPonderUIStubAccessor acc) {
            acc.arcQuest$setWidth(w);
            acc.arcQuest$setHeight(h);
            acc.arcQuest$setFont(font);
        }
        return ui;
    }
}
