package org.arcadia.arc_quest.client.hud.quest.ponder;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.mixin.client.ponder.MixinPonderUIStubAccessor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 通用 PonderUI 实例，用于向底层 TextWindowElement 提供 width/height/font。
 * 现已支持动态上下文绑定，完美适配 Intel 面板与 Guide Embedded 面板。
 */
public final class IntelPonderUIStub {

    @Nullable private static PonderUI cached = null;
    private static int cachedW = -1;
    private static int cachedH = -1;
    @Nullable private static ResourceLocation cachedSceneId = null;

    @Nullable private static ResourceLocation currentContext = null;

    private IntelPonderUIStub() {}

    /**
     * 在渲染 Overlay 前绑定当前 Scene，让底层 Mixin 能正确获取 UI 上下文
     */
    public static void setContext(@Nullable ResourceLocation sceneId) {
        currentContext = sceneId;
    }

    @Nullable
    public static PonderUI getOrCreate(int w, int h, Font font) {
        ResourceLocation targetId = currentContext;

        if (targetId == null) {
            List<PonderScene> scenes = QuestIntelPanel.getActiveScenes();
            if (scenes != null && !scenes.isEmpty()) {
                targetId = scenes.get(0).getLocation();
            }
        }

        if (targetId == null) return null;

        if (cachedW == w && cachedH == h && targetId.equals(cachedSceneId) && cached != null) {
            return cached;
        }

        try {
            PonderUI ui = PonderUI.of(targetId);
            if (ui instanceof MixinPonderUIStubAccessor acc) {
                acc.arcQuest$setWidth(w);
                acc.arcQuest$setHeight(h);
                acc.arcQuest$setFont(font);
            }
            cached = ui;
            cachedW = w;
            cachedH = h;
            cachedSceneId = targetId;
            return cached;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static PonderUI get(int w, int h, Font font) {
        return getOrCreate(w, h, font);
    }

    public static void invalidate() {
        cached = null;
        cachedW = -1;
        cachedH = -1;
        cachedSceneId = null;
    }
}