package org.arcadia.arc_quest.client.hud.quest.icon;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.LinkedHashMap;
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
import java.util.SequencedMap;

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
    private static final boolean MEASURE = Boolean.getBoolean("arc_quest.shop.performance.audit");
    private static long itemCalls, opaqueItemCalls, offscreenPasses, guiFlushes, targetAllocations;

    private static final class Buffers {
        static final ByteBufferBuilder ITEMS = new ByteBufferBuilder(4096);
        static final ByteBufferBuilder QUAD = new ByteBufferBuilder(256);
        static final SequencedMap<RenderType, ByteBufferBuilder> FIXED = fixed();

        private static SequencedMap<RenderType, ByteBufferBuilder> fixed() {
            var builders = new LinkedHashMap<RenderType, ByteBufferBuilder>();
            // Match the native GUI buffer order. Foil uses VertexMultiConsumer and must hold
            // distinct base/glint builders simultaneously; immediate(oneBuilder) cannot do that.
            for (RenderType type : List.of(Sheets.solidBlockSheet(), Sheets.cutoutBlockSheet(),
                    Sheets.bannerSheet(), Sheets.translucentCullBlockSheet(), Sheets.shieldSheet(),
                    Sheets.bedSheet(), Sheets.shulkerBoxSheet(), Sheets.signSheet(),
                    Sheets.hangingSignSheet(), Sheets.chestSheet(), RenderType.translucentMovingBlock(),
                    RenderType.armorEntityGlint(), RenderType.glint(),
                    RenderType.glintTranslucent(), RenderType.entityGlint(),
                    RenderType.entityGlintDirect(), RenderType.waterMask())) {
                builders.put(type, new ByteBufferBuilder(Math.min(4096, type.bufferSize())));
            }
            return builders;
        }
    }

    private ObjectiveIconAlpha() { }

    /** Opt-in audit counters; normal clients do not update these or allocate snapshots. */
    public static long[] performanceCounters() {
        return new long[]{itemCalls, opaqueItemCalls, offscreenPasses, guiFlushes, targetAllocations};
    }

    public static void renderItem(GuiGraphics graphics, ItemStack stack, int x, int y, int size, float alpha) {
        renderOutlinedItem(graphics, stack, x, y, size, alpha, 0);
    }

    /** White outer silhouette, one GUI pixel wide; only an active outline needs an extra layer. */
    public static void renderOutlinedItem(GuiGraphics graphics, ItemStack stack, int x, int y, int size,
                                          float alpha, float outlineOpacity) {
        renderOutlinedItem(graphics, stack, x, y, size, alpha, outlineOpacity, 1);
    }

    /** Outline width is in GUI pixels and does not grow with the current item pose scale. */
    public static void renderOutlinedItem(GuiGraphics graphics, ItemStack stack, int x, int y, int size,
                                          float alpha, float outlineOpacity, float outlineWidth) {
        if (stack == null || stack.isEmpty()) return;
        if (!Float.isFinite(outlineWidth) || outlineWidth <= 0) outlineOpacity = 0;
        if (MEASURE) {
            itemCalls++;
            if (normalizedAlpha(alpha) == 1 && normalizedOutline(outlineOpacity) == 0) opaqueItemCalls++;
        }
        render(graphics, x, y, size, alpha, 150, outlineOpacity, outlineWidth, (destination, left, top, edge) -> {
            destination.pose().pushPose();
            try {
                destination.pose().translate(left, top, 0);
                destination.pose().scale(edge / 16f, edge / 16f, 1);
                destination.renderFakeItem(stack, 0, 0);
            } finally { destination.pose().popPose(); }
        });
    }

    static void renderVisual(GuiGraphics graphics, ObjectiveIconVisual visual, int x, int y, int size, float alpha) {
        render(graphics, x, y, size, alpha, 0, 0, 0, visual::render);
    }

    private static void render(GuiGraphics graphics, int x, int y, int size, float inputAlpha, float depth,
                               float inputOutline, float outlineWidth, Draw draw) {
        float alpha = normalizedAlpha(inputAlpha);
        float outline = normalizedOutline(inputOutline);
        if (alpha == 0 || size <= 0) return;
        if (alpha == 1 && outline == 0) {
            draw.render(graphics, x, y, size);
            return;
        }
        RenderSystem.assertOnRenderThread();
        if (MEASURE) offscreenPasses++;
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
            if (MEASURE) guiFlushes++;
            try (PortraitRenderState offscreen = new PortraitRenderState(true)) {
                try {
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                    target = targets.pollFirst();
                    if (target == null) {
                        target = new TextureTarget(MAX_TARGET_SIZE, MAX_TARGET_SIZE, true, Minecraft.ON_OSX);
                        if (MEASURE) targetAllocations++;
                    }
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
                    RenderSystem.getModelViewStack().identity();
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setShaderFogStart(1000);
                    RenderSystem.setShaderFogEnd(1001);
                    // Re-entry flushes this GuiGraphics before borrowing the shared builder.
                    // Reuse the native storage across draws, including nested provider draws.
                    var buffer = MultiBufferSource.immediateWithBuffers(Buffers.FIXED, Buffers.ITEMS);
                    GuiGraphics layer = new GuiGraphics(Minecraft.getInstance(), buffer);
                    draw.render(layer, padding, padding, size);
                    layer.flush();
                    if (MEASURE) guiFlushes++;
                } finally {
                    discard(Buffers.ITEMS);
                    for (ByteBufferBuilder fixed : Buffers.FIXED.values()) discard(fixed);
                }
            }

            // The framebuffer contains premultiplied RGB. Scale RGB and A once, then use ONE
            // rather than SRC_ALPHA; applying alpha a second time would darken soft PNG edges.
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableCull();
            if (outline > 0) {
                var shader = ObjectiveIconShaders.outline();
                // Convert GUI width to FBO texels, independently of the icon's hover scale.
                // At tiny GUI scales retain at least one physical pixel for a continuous edge.
                float step = (float) (Math.max(1, pixels * outlineWidth / (canvas * Math.max(scale, .0001))) / MAX_TARGET_SIZE);
                shader.safeGetUniform("OutlineStep").set(step, step);
                shader.safeGetUniform("OutlineOpacity").set(outline);
                RenderSystem.setShader(() -> shader);
            } else RenderSystem.setShader(ObjectiveIconShaders::premultiplied);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            RenderSystem.setShaderColor(alpha, alpha, alpha, alpha);
            float maxUv = pixels / (float) MAX_TARGET_SIZE;
            Matrix4f matrix = graphics.pose().last().pose();
            float left = x - padding, right = left + canvas, top = y - padding, bottom = top + canvas;
            BufferBuilder quad = new BufferBuilder(Buffers.QUAD, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            try {
                quad.addVertex(matrix, left, top, depth).setUv(0, maxUv);
                quad.addVertex(matrix, left, bottom, depth).setUv(0, 0);
                quad.addVertex(matrix, right, bottom, depth).setUv(maxUv, 0);
                quad.addVertex(matrix, right, top, depth).setUv(maxUv, maxUv);
                BufferUploader.drawWithShader(quad.buildOrThrow());
            } finally { discard(Buffers.QUAD); }
        } finally {
            if (target != null) release(target, targetGeneration);
        }
    }

    /** Prevents steady 254/255 UI interpolation from allocating an offscreen draw every frame. */
    static float normalizedAlpha(float alpha) {
        if (!Float.isFinite(alpha) || alpha <= 0) return 0;
        return alpha >= OPAQUE_THRESHOLD ? 1 : alpha;
    }

    /** End the hover tail instead of retaining an offscreen pass for sub-pixel opacity forever. */
    static float normalizedOutline(float opacity) {
        if (!Float.isFinite(opacity) || opacity <= 1f / 255f) return 0;
        return Math.min(1, opacity);
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

    private static void discard(ByteBufferBuilder builder) {
        builder.clear();
    }

    @FunctionalInterface private interface Draw {
        void render(GuiGraphics graphics, int x, int y, int size);
    }
}
