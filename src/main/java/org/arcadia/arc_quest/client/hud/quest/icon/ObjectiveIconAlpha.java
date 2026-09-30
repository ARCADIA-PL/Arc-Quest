package org.arcadia.arc_quest.client.hud.quest.icon;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.PortraitRenderState;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.util.ArrayDeque;
import java.util.List;
import java.util.SortedMap;

/**
 * Group opacity for icons. Item render types write vertex alpha=1, may disable blending, and
 * include independent glint passes. Therefore opacity is applied after their native GUI draw,
 * not as a shaderColor assumption or a changed item vertex alpha/cutout threshold.
 */
public final class ObjectiveIconAlpha {
    private static final int MAX_TARGET_SIZE = 512;
    private static final int MAX_CACHED_TARGETS = 4;
    private static final float OPAQUE_THRESHOLD = 254f / 255f;
    // Active layers are removed from this pool, so a nested public visual receives its own target.
    private static final ArrayDeque<TextureTarget> targets = new ArrayDeque<>();
    private static long generation;

    private static final class Buffers {
        static final BufferBuilder ITEMS = new BufferBuilder(4096);
        static final BufferBuilder QUAD = new BufferBuilder(256);
        static final SortedMap<RenderType, BufferBuilder> FIXED = fixed();

        private static SortedMap<RenderType, BufferBuilder> fixed() {
            var builders = new Object2ObjectLinkedOpenHashMap<RenderType, BufferBuilder>();
            // Match the native GUI buffer order. Foil uses VertexMultiConsumer and must hold
            // distinct base/glint builders simultaneously; immediate(oneBuilder) cannot do that.
            for (RenderType type : List.of(Sheets.solidBlockSheet(), Sheets.cutoutBlockSheet(),
                    Sheets.bannerSheet(), Sheets.translucentCullBlockSheet(), Sheets.shieldSheet(),
                    Sheets.bedSheet(), Sheets.shulkerBoxSheet(), Sheets.signSheet(),
                    Sheets.hangingSignSheet(), Sheets.chestSheet(), RenderType.translucentNoCrumbling(),
                    RenderType.armorGlint(), RenderType.armorEntityGlint(), RenderType.glint(),
                    RenderType.glintDirect(), RenderType.glintTranslucent(), RenderType.entityGlint(),
                    RenderType.entityGlintDirect(), RenderType.waterMask())) {
                builders.put(type, new BufferBuilder(Math.min(4096, type.bufferSize())));
            }
            return builders;
        }
    }

    private ObjectiveIconAlpha() { }

    public static void renderItem(GuiGraphics graphics, ItemStack stack, int x, int y, int size, float alpha) {
        if (stack == null || stack.isEmpty()) return;
        render(graphics, x, y, size, alpha, 150, (destination, left, top, edge) -> {
            destination.pose().pushPose();
            try {
                destination.pose().translate(left, top, 0);
                destination.pose().scale(edge / 16f, edge / 16f, 1);
                destination.renderFakeItem(stack, 0, 0);
            } finally { destination.pose().popPose(); }
        });
    }

    static void renderVisual(GuiGraphics graphics, ObjectiveIconVisual visual, int x, int y, int size, float alpha) {
        render(graphics, x, y, size, alpha, 0, visual::render);
    }

    private static void render(GuiGraphics graphics, int x, int y, int size, float inputAlpha, float depth, Draw draw) {
        float alpha = normalizedAlpha(inputAlpha);
        if (alpha == 0 || size <= 0) return;
        if (alpha == 1) {
            draw.render(graphics, x, y, size);
            return;
        }
        RenderSystem.assertOnRenderThread();
        int padding = Math.max(2, size / 4);
        int canvas = size + 2 * padding;
        Matrix4f pose = graphics.pose().last().pose();
        double scale = Math.max(Math.hypot(pose.m00(), pose.m01()), Math.hypot(pose.m10(), pose.m11()));
        int pixels = Math.max(1, Math.min(MAX_TARGET_SIZE,
                (int) Math.ceil(canvas * Minecraft.getInstance().getWindow().getGuiScale() * scale)));

        // Finish the caller's batches before switching the framebuffer. Even GuiGraphics.flush()
        // changes depth state, so it is included in the outer save/restore scope.
        TextureTarget target = null;
        long targetGeneration = generation;
        try (PortraitRenderState outer = new PortraitRenderState(true)) {
            graphics.flush();
            try (PortraitRenderState offscreen = new PortraitRenderState(true)) {
                try {
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                    target = targets.pollFirst();
                    if (target == null) target = new TextureTarget(MAX_TARGET_SIZE, MAX_TARGET_SIZE, true, Minecraft.ON_OSX);
                    RenderSystem.disableScissor();
                    RenderSystem.colorMask(true, true, true, true);
                    RenderSystem.depthMask(true);
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthFunc(GL11.GL_LEQUAL);
                    RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                    target.setClearColor(0, 0, 0, 0);
                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(false);
                    RenderSystem.viewport(0, 0, pixels, pixels);
                    RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, canvas, canvas, 0, -1000, 1000),
                            VertexSorting.ORTHOGRAPHIC_Z);
                    RenderSystem.getModelViewStack().setIdentity();
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setShaderFogStart(1000);
                    RenderSystem.setShaderFogEnd(1001);
                    // Re-entry flushes this GuiGraphics before borrowing the shared builder.
                    // BufferBuilder owns unmanaged memory and cannot be freed through its public
                    // API, so avoid allocating a new builder for every nested provider draw.
                    var buffer = MultiBufferSource.immediateWithBuffers(Buffers.FIXED, Buffers.ITEMS);
                    GuiGraphics layer = new GuiGraphics(Minecraft.getInstance(), buffer);
                    draw.render(layer, padding, padding, size);
                    layer.flush();
                } finally {
                    discard(Buffers.ITEMS);
                    for (BufferBuilder fixed : Buffers.FIXED.values()) discard(fixed);
                }
            }

            // The framebuffer contains premultiplied RGB. Scale RGB and A once, then use ONE
            // rather than SRC_ALPHA; applying alpha a second time would darken soft PNG edges.
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableCull();
            RenderSystem.setShader(ObjectiveIconShaders::premultiplied);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            RenderSystem.setShaderColor(alpha, alpha, alpha, alpha);
            float maxUv = pixels / (float) MAX_TARGET_SIZE;
            Matrix4f matrix = graphics.pose().last().pose();
            float left = x - padding, right = left + canvas, top = y - padding, bottom = top + canvas;
            BufferBuilder quad = Buffers.QUAD;
            try {
                quad.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                quad.vertex(matrix, left, top, depth).uv(0, maxUv).endVertex();
                quad.vertex(matrix, left, bottom, depth).uv(0, 0).endVertex();
                quad.vertex(matrix, right, bottom, depth).uv(maxUv, 0).endVertex();
                quad.vertex(matrix, right, top, depth).uv(maxUv, maxUv).endVertex();
                BufferUploader.drawWithShader(quad.end());
            } finally { discard(quad); }
        } finally {
            if (target != null) release(target, targetGeneration);
        }
    }

    /** Prevents steady 254/255 UI interpolation from allocating an offscreen draw every frame. */
    static float normalizedAlpha(float alpha) {
        if (!Float.isFinite(alpha) || alpha <= 0) return 0;
        return alpha >= OPAQUE_THRESHOLD ? 1 : alpha;
    }

    /** Releases every idle layer on session exit and resource invalidation, on the render thread. */
    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ObjectiveIconAlpha::clear);
            return;
        }
        generation++;
        if (targets.isEmpty()) return;
        try (PortraitRenderState ignored = new PortraitRenderState()) {
            TextureTarget target;
            while ((target = targets.pollFirst()) != null) target.destroyBuffers();
        }
    }

    private static void release(TextureTarget target, long targetGeneration) {
        if (targetGeneration == generation && targets.size() < MAX_CACHED_TARGETS) targets.addFirst(target);
        else {
            try (PortraitRenderState ignored = new PortraitRenderState()) { target.destroyBuffers(); }
        }
    }

    private static void discard(BufferBuilder builder) {
        try {
            if (builder.building()) {
                var unfinished = builder.endOrDiscardIfEmpty();
                if (unfinished != null) unfinished.release();
            }
        } finally { builder.discard(); }
    }

    @FunctionalInterface private interface Draw {
        void render(GuiGraphics graphics, int x, int y, int size);
    }
}
