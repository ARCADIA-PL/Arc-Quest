package org.arcadia.arc_quest.client.hud.ponder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.ponder.ArcQuestPonderSceneRegistry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EmbeddedPonderScenePanel {

    private static final Vector3f DIFFUSE_0 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    private static final Vector3f DIFFUSE_1 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Set<ResourceLocation> WARNED_MISSING_SCENES = new HashSet<>();

    @Nullable
    private EmbeddedPonderSceneHandle handle;
    @Nullable
    private ResourceLocation boundSceneId;
    private int themeColor = 0x4FC3F7;

    public boolean bind(ResourceLocation sceneId, int themeColor, boolean autoplay) {
        if (sceneId == null) {
            unbind();
            return false;
        }
        if (isBoundTo(sceneId)) {
            return handle != null && handle.isValid();
        }
        unbind();
        this.themeColor = themeColor;
        boundSceneId = sceneId;

        if (!ArcQuestPonderSceneRegistry.hasScene(sceneId) || !PonderIndex.getSceneAccess().doScenesExistForId(sceneId)) {
            warnMissing(sceneId);
            return false;
        }

        List<PonderScene> scenes = PonderIndex.getSceneAccess().compile(sceneId);
        if (scenes == null || scenes.isEmpty()) {
            warnMissing(sceneId);
            return false;
        }

        handle = new EmbeddedPonderSceneHandle(sceneId, scenes, !autoplay, themeColor);
        return handle.isValid();
    }

    public void unbind() {
        if (handle != null) {
            handle.release();
        }
        handle = null;
        boundSceneId = null;
    }

    public boolean hasBoundScene() {
        return handle != null && handle.isValid();
    }

    public boolean isBoundTo(ResourceLocation sceneId) {
        return boundSceneId != null && boundSceneId.equals(sceneId) && handle != null && handle.isValid();
    }

    public void tick() {
        if (handle != null) {
            handle.tick();
        }
    }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        g.fill(x, y, x + w, y + h, 0x501A2430);
        g.fill(x, y, x + w, y + 1, 0xFF000000 | (themeColor & 0x00FFFFFF));
        g.fill(x, y + h - 1, x + w, y + h, 0xFF000000 | (themeColor & 0x00FFFFFF));
        g.fill(x, y, x + 1, y + h, 0xFF000000 | (themeColor & 0x00FFFFFF));
        g.fill(x + w - 1, y, x + w, y + h, 0xFF000000 | (themeColor & 0x00FFFFFF));

        if (handle == null || !handle.isValid()) {
            renderMissing(g, x, y, w, h);
            return;
        }

        PonderScene scene = handle.currentScene();
        int sceneX = x + 6;
        int sceneY = y + 6;
        int sceneW = Math.max(8, w - 12);
        int sceneH = Math.max(8, h - 12);
        renderScene(g, scene, sceneX, sceneY, sceneW, sceneH, partialTick);

        var font = Minecraft.getInstance().font;
        String counter = (handle.getSceneIndex() + 1) + " / " + handle.getSceneCount();
        g.drawString(font, Component.literal(counter), x + 8, y + 8, 0xB8C7D9, false);
        g.drawString(font, Component.literal(handle.isPaused() ? "Paused" : "Playing"), x + w - 50, y + 8, 0xB8C7D9, false);

        drawButton(g, x + 6, y + h - 18, 14, 12, "◄", canPrev(), mouseX, mouseY);
        drawButton(g, x + 24, y + h - 18, 14, 12, handle.isPaused() ? "▶" : "‖", true, mouseX, mouseY);
        drawButton(g, x + 42, y + h - 18, 14, 12, "↺", true, mouseX, mouseY);
        drawButton(g, x + 60, y + h - 18, 14, 12, "►", canNext(), mouseX, mouseY);
    }

    public boolean mouseClicked(double mx, double my, int button, int x, int y, int w, int h) {
        if (handle == null || !handle.isValid() || button != 0) {
            return false;
        }
        if (hit(mx, my, x + 6, y + h - 18, 14, 12) && canPrev()) {
            handle.scrollBack();
            return true;
        }
        if (hit(mx, my, x + 24, y + h - 18, 14, 12)) {
            handle.togglePause();
            return true;
        }
        if (hit(mx, my, x + 42, y + h - 18, 14, 12)) {
            handle.replay();
            return true;
        }
        if (hit(mx, my, x + 60, y + h - 18, 14, 12) && canNext()) {
            handle.scrollForward();
            return true;
        }
        return false;
    }

    public void onScreenClosed() {
        unbind();
    }

    @Nullable
    public EmbeddedPonderSceneHandle getHandle() {
        return handle;
    }

    private boolean canPrev() {
        return handle != null && handle.isValid() && handle.getSceneIndex() > 0;
    }

    private boolean canNext() {
        return handle != null && handle.isValid() && handle.getSceneIndex() < handle.getSceneCount() - 1;
    }

    private void renderMissing(GuiGraphics g, int x, int y, int w, int h) {
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, Component.translatable("guide.arc_quest.ponder_missing"), x + w / 2, y + h / 2 - 10, 0xFF8888);
        if (boundSceneId != null) {
            g.drawCenteredString(font, Component.literal(boundSceneId.toString()), x + w / 2, y + h / 2 + 4, 0xC0D0E0);
        }
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = hit(mouseX, mouseY, x, y, w, h);
        int border = enabled ? (hovered ? 0xFFBFE9FF : (0xFF000000 | (themeColor & 0x00FFFFFF))) : 0xFF44515D;
        int fill = enabled ? (hovered ? 0x60305060 : 0x40182028) : 0x20101010;
        int text = enabled ? 0xE6EDF7 : 0x6C7885;
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        g.drawCenteredString(Minecraft.getInstance().font, label, x + w / 2, y + 2, text);
    }

    private boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void renderScene(GuiGraphics g, PonderScene scene, int areaX, int areaY, int areaW, int areaH, float pt) {
        SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.backupProjectionMatrix();

        g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);

        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        proj.translate(0, 0, 800);
        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);

        PoseStack ms = g.pose();
        ms.pushPose();
        ms.translate(areaX, areaY, -800);
        RenderSystem.setupLevelDiffuseLighting(DIFFUSE_0, DIFFUSE_1, ms.last().pose());

        scene.getTransform().updateScreenParams(areaW, areaH, 0);
        scene.getTransform().apply(ms, pt);
        scene.getTransform().updateSceneRVE(pt);
        scene.renderScene(buffer, g, pt);
        buffer.draw();

        ms.popPose();
        g.disableScissor();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.disableDepthTest();
    }

    private void warnMissing(ResourceLocation sceneId) {
        if (WARNED_MISSING_SCENES.add(sceneId)) {
            org.arcadia.arc_quest.Arc_Quest.LOGGER.warn("[Guide] Missing embedded ponder scene '{}'", sceneId);
        }
    }
}
