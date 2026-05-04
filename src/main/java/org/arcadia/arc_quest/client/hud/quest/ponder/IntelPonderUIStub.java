package org.arcadia.arc_quest.client.hud.quest.ponder;

import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.intel.ArcQuestIntelPanelElement;
import org.arcadia.arc_quest.mixin.client.ponder.MixinPonderUIStubAccessor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Ponder overlay compatibility layer for ArcMutil intel panels.
 *
 * <p>Create's TextWindowElement expects a real PonderUI screen for dimensions and font.
 * ArcQuest renders ponder scenes inside an ArcMutil HUD element, so this class builds and
 * caches a size-matched PonderUI stub and exposes safe overlay rendering helpers.</p>
 */
public final class IntelPonderUIStub {

    @Nullable
    private static PonderUI cached = null;
    @Nullable
    private static ResourceLocation cachedSceneId = null;
    private static int cachedW = -1;
    private static int cachedH = -1;
    @Nullable
    private static OverlayContext activeOverlayContext = null;

    private IntelPonderUIStub() {
    }

    @Nullable
    public static PonderUI getOrCreate(int w, int h, Font font) {
        List<PonderScene> scenes = ArcQuestIntelPanelElement.getActiveScenes();
        if (scenes == null || scenes.isEmpty()) return null;
        return getOrCreate(scenes.get(0), w, h, font);
    }

    @Nullable
    public static PonderUI getOrCreate(PonderScene scene, int w, int h, Font font) {
        if (scene == null || font == null || w <= 0 || h <= 0) return null;
        ResourceLocation sceneId = scene.getLocation();
        if (cachedW == w && cachedH == h && cached != null && sceneId.equals(cachedSceneId)) {
            configure(cached, w, h, font);
            return cached;
        }

        try {
            PonderUI ui = buildStub(sceneId, w, h, font);
            if (ui != null) {
                cached = ui;
                cachedSceneId = sceneId;
                cachedW = w;
                cachedH = h;
            }
            return cached;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public static PonderUI get(int w, int h, Font font) {
        return getOrCreate(w, h, font);
    }

    public static void renderOverlay(PonderScene scene, GuiGraphics graphics, float partialTicks, int w, int h, Font font) {
        if (scene == null || graphics == null || font == null) return;
        PonderUI stub = getOrCreate(scene, w, h, font);
        if (stub != null) {
            scene.renderOverlay(stub, graphics, partialTicks);
            return;
        }
        renderOverlaySafely(scene, graphics, partialTicks, w, h, font);
    }

    public static void renderOverlaySafely(PonderScene scene, GuiGraphics graphics, float partialTicks, int w, int h, Font font) {
        if (scene == null || graphics == null || font == null) return;
        withOverlayContext(scene, w, h, font, () -> {
            graphics.pose().pushPose();
            scene.forEachVisible(PonderOverlayElement.class, element -> renderElementSafely(scene, element, graphics, partialTicks));
            graphics.pose().popPose();
        });
    }

    public static void withOverlayContext(PonderScene scene, int w, int h, Font font, Runnable renderer) {
        if (renderer == null) return;
        OverlayContext previous = activeOverlayContext;
        activeOverlayContext = new OverlayContext(scene, Math.max(1, w), Math.max(1, h), font == null ? Minecraft.getInstance().font : font);
        try {
            renderer.run();
        } finally {
            activeOverlayContext = previous;
        }
    }

    @Nullable
    public static OverlayContext getActiveOverlayContext() {
        return activeOverlayContext;
    }

    public static void invalidate() {
        cached = null;
        cachedSceneId = null;
        cachedW = -1;
        cachedH = -1;
        activeOverlayContext = null;
    }

    private static void renderElementSafely(PonderScene scene, PonderOverlayElement element, GuiGraphics graphics, float partialTicks) {
        if (element instanceof TextWindowElement textWindow) {
            renderTextElementSafely(textWindow, scene, graphics, partialTicks);
            return;
        }
        try {
            element.render(scene, null, graphics, partialTicks);
        } catch (Exception ignored) {
        }
    }

    private static void renderTextElementSafely(TextWindowElement textWindow, PonderScene scene, GuiGraphics graphics, float partialTicks) {
        OverlayContext context = activeOverlayContext;
        if (context == null) return;
        PonderUI stub = getOrCreate(context.scene(), context.width(), context.height(), context.font());
        if (stub == null) return;
        try {
            textWindow.render(scene, stub, graphics, partialTicks);
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private static PonderUI buildStub(ResourceLocation sceneId, int w, int h, Font font) {
        if (sceneId == null) return null;
        PonderUI ui;
        try {
            ui = PonderUI.of(sceneId);
        } catch (Exception ignored) {
            return null;
        }
        configure(ui, w, h, font);
        return ui;
    }

    private static void configure(PonderUI ui, int w, int h, Font font) {
        if (ui instanceof MixinPonderUIStubAccessor acc) {
            acc.arcQuest$setWidth(w);
            acc.arcQuest$setHeight(h);
            acc.arcQuest$setFont(font);
        }
    }

    public record OverlayContext(PonderScene scene, int width, int height, Font font) {
    }
}
