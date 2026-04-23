package org.com.arc_quest.client.questmarker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.com.arc_quest.questmarker.api.QuestMarkerData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 在 3D 世界空间内渲染标记图标：
 *  - 竖直光柱（从标记位置向上，脉冲动画）
 *  - Billboard 浮空形状（始终朝向相机）
 *  - 浮空文字（SEE_THROUGH 风格）
 */
public class MarkerWorldRenderer {

    private long startTime = System.currentTimeMillis();

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        float time = (System.currentTimeMillis() - startTime) / 1000.0f;

        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        for (QuestMarkerData marker : QuestMarkerManager.INSTANCE.all()) {
            if (!marker.isActive()) continue;

            float rx = (float) (marker.getWorldX() - camX);
            float ry = (float) (marker.getWorldY() - camY);
            float rz = (float) (marker.getWorldZ() - camZ);

            int color = marker.getColorARGB();
            double dist = marker.distanceTo(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());

            if (dist > 120.0) continue;

            float scale = (float) Math.max(0.03f, Math.min(0.10f, 2.5 / (dist + 1.0)));

            pose.pushPose();
            pose.translate(rx, ry, rz);

            renderBeam(pose, time, color, dist);

            pose.pushPose();
            applyBillboard(pose, camera);
            pose.scale(scale, scale, scale);
            renderBillboardIcon(pose, marker, time, color);
            pose.popPose();

            pose.pushPose();
            applyBillboard(pose, camera);
            float textScale = scale * 0.55f;
            pose.scale(-textScale, -textScale, textScale);
            renderFloatingText(pose, bufferSource, mc.font, marker, dist, color);
            pose.popPose();

            pose.popPose();
        }

        bufferSource.endBatch();
    }

    /* ────────── Billboard：面朝相机 ────────── */
    private void applyBillboard(PoseStack pose, Camera camera) {
        Quaternionf q = new Quaternionf(camera.rotation());
        q.conjugate();
        pose.mulPose(q);
    }

    /* ────────── 竖直光柱 ────────── */
    private void renderBeam(PoseStack pose, float time, int color, double dist) {
        float beamHeight = 6.0f;
        float beamRadius = 0.07f;
        int segments = 8;

        float distFade = (float) Math.max(0.05f, 1.0f - dist / 120.0);
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.5f);
        float fa = extractA(color) * distFade * (0.5f + 0.5f * pulse);
        float fr = extractR(color), fg = extractG(color), fb = extractB(color);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f mat = pose.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < segments; i++) {
            float a1 = (float) (2.0 * Math.PI * i / segments);
            float a2 = (float) (2.0 * Math.PI * (i + 1) / segments);
            float x1 = (float) Math.cos(a1) * beamRadius, z1 = (float) Math.sin(a1) * beamRadius;
            float x2 = (float) Math.cos(a2) * beamRadius, z2 = (float) Math.sin(a2) * beamRadius;
            buf.vertex(mat, x1, 0,          z1).color(fr, fg, fb, 0.0f).endVertex();
            buf.vertex(mat, x2, 0,          z2).color(fr, fg, fb, 0.0f).endVertex();
            buf.vertex(mat, x2, beamHeight, z2).color(fr, fg, fb, fa).endVertex();
            buf.vertex(mat, x1, beamHeight, z1).color(fr, fg, fb, fa).endVertex();
        }

        tess.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /* ────────── Billboard 图标 ────────── */
    private void renderBillboardIcon(PoseStack pose, QuestMarkerData marker, float time, int color) {
        float fr = extractR(color), fg = extractG(color), fb = extractB(color), fa = extractA(color);
        float pulse = 0.9f + 0.1f * (float) Math.sin(time * 3.0f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        switch (marker.getType()) {
            case QUEST_MAIN, QUEST -> {
                drawBillboardDiamond(pose, 0, 50 * pulse, fr, fg, fb, fa);
                drawBillboardDiamond(pose, 0, 28 * pulse, 0f, 0f, 0f, 0.85f);
                drawBillboardDiamond(pose, 0, 20 * pulse, fr, fg, fb, fa);
            }
            case QUEST_SIDE -> {
                drawBillboardDiamond(pose, 0, 50 * pulse, fr, fg, fb, fa);
                drawBillboardDiamond(pose, 0, 38 * pulse, 0f, 0f, 0f, 0.8f);
            }
            case NPC_INTERACT, NPC, INTERACT -> {
                drawBillboardCircle(pose, 45 * pulse, 24, fr, fg, fb, fa);
                drawBillboardCircle(pose, 30 * pulse, 24, 0f, 0f, 0f, 0.85f);
                drawBillboardCircle(pose, 18 * pulse, 24, fr, fg, fb, fa);
            }
            case ENEMY_TARGET, ENEMY -> {
                drawBillboardCircle(pose, 46 * pulse, 8, fr, fg, fb, fa);
                drawBillboardLine(pose, -28, -28, 28, 28, 8, fr, fg, fb, fa);
                drawBillboardLine(pose, 28, -28, -28, 28, 8, fr, fg, fb, fa);
            }
            case LOCATION -> drawBillboardCircle(pose, 45 * pulse, 6, fr, fg, fb, fa);
            default -> drawBillboardCircle(pose, 40 * pulse, 16, fr, fg, fb, fa);
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /* ────────── 浮空文字 ────────── */
    private void renderFloatingText(PoseStack pose, MultiBufferSource bufferSource,
                                    Font font, QuestMarkerData marker, double dist, int color) {
        pose.translate(0, -120, 0);

        String name = marker.getLabel();
        String distStr = String.format("%.0fm", dist);
        float nameW = font.width(name);
        float distW = font.width(distStr);
        int solidColor = color | 0xFF000000;

        font.drawInBatch(name, -nameW / 2.0f, 0, solidColor, false,
                pose.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH, 0x55000000, 15728880);

        if (marker.isShowDistance()) {
            font.drawInBatch(distStr, -distW / 2.0f, 12, 0xFFAAAAAA, false,
                    pose.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH, 0x44000000, 15728880);
        }
    }

    /* ═══════ Billboard 基础图元 ═══════ */

    private void drawBillboardDiamond(PoseStack pose, float yOffset, float size,
                                      float r, float g, float b, float a) {
        Matrix4f mat = pose.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(mat,     0, yOffset + size, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat,  size, yOffset,        0).color(r, g, b, a).endVertex();
        buf.vertex(mat,     0, yOffset - size, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, -size, yOffset,        0).color(r, g, b, a).endVertex();
        tess.end();
    }

    private void drawBillboardCircle(PoseStack pose, float radius, int segments,
                                     float r, float g, float b, float a) {
        Matrix4f mat = pose.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (2.0 * Math.PI * i / segments);
            float a2 = (float) (2.0 * Math.PI * (i + 1) / segments);
            buf.vertex(mat, 0, 0, 0).color(r, g, b, a).endVertex();
            buf.vertex(mat, (float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius, 0).color(r, g, b, a).endVertex();
            buf.vertex(mat, (float) Math.cos(a2) * radius, (float) Math.sin(a2) * radius, 0).color(r, g, b, a).endVertex();
        }
        tess.end();
    }

    private void drawBillboardLine(PoseStack pose, float x1, float y1, float x2, float y2,
                                   float width, float r, float g, float b, float a) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = (-dy / len) * (width * 0.5f), ny = (dx / len) * (width * 0.5f);

        Matrix4f mat = pose.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(mat, x1 + nx, y1 + ny, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, x1 - nx, y1 - ny, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, x2 - nx, y2 - ny, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, x2 + nx, y2 + ny, 0).color(r, g, b, a).endVertex();
        tess.end();
    }

    /* ────────── 颜色分量 ────────── */
    private static float extractA(int c) { return ((c >> 24) & 0xFF) / 255.0f; }
    private static float extractR(int c) { return ((c >> 16) & 0xFF) / 255.0f; }
    private static float extractG(int c) { return ((c >>  8) & 0xFF) / 255.0f; }
    private static float extractB(int c) { return ( c        & 0xFF) / 255.0f; }
}
