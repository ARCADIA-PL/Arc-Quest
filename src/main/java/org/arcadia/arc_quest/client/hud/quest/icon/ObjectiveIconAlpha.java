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
import net.minecraft.client.renderer.GameRenderer;
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
import java.util.LinkedHashMap;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

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
    private static long cacheHits, cacheMisses;
    private record ItemTileKey(String item, int size, int pixels) {}
    private record ItemTile(TextureTarget target, RenderType renderType) {}
    // Opt-in atlas snapshots only: at most 256 icons AND 4M RGBA/depth pixels (~32 MiB).
    private static final LinkedHashMap<ItemTileKey, ItemTile> itemTiles = new LinkedHashMap<>(128, .75f, true);
    private static long tilePixels;
    private static final LinkedHashMap<String, Boolean> staticItems = new LinkedHashMap<>(128, .75f, true);

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

    /** Opt-in audit counters; normal clients do not update these or allocate snapshots. */
    public static long[] performanceCounters() {
        return new long[]{itemCalls, opaqueItemCalls, offscreenPasses, guiFlushes, targetAllocations, cacheHits, cacheMisses};
    }

    /** Reuse static atlas icons across frames, including alpha/focus changes. Dynamic icons stay live. */
    public static boolean tryRenderCachedItem(GuiGraphics graphics, ItemStack stack, int x, int y, int size, float alpha) {
        if (stack == null || stack.isEmpty() || size <= 0 || normalizedAlpha(alpha) == 0) return true;
        String key = ObjectiveIconSession.itemKey(stack);
        if (Boolean.getBoolean("arc_quest.atlas.disableIconCache") || stack.hasFoil()
                || !staticItem(key, stack)) {
            return false;
        }
        if (MEASURE) itemCalls++;
        render(graphics, x, y, size, alpha, 150, 0, 0, (destination, left, top, edge) -> {
            destination.pose().pushPose();
            try {
                destination.pose().translate(left, top, 0);
                destination.pose().scale(edge / 16f, edge / 16f, 1);
                destination.renderFakeItem(stack, 0, 0);
            } finally { destination.pose().popPose(); }
        }, key);
        return true;
    }

    private static boolean staticItem(String key, ItemStack stack) {
        Boolean known = staticItems.get(key);
        if (known != null) return known;
        var mc = Minecraft.getInstance();
        var model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
        boolean result = !model.isCustomRenderer() && model.getOverrides() == ItemOverrides.EMPTY;
        if (result) for (var pass : model.getRenderPasses(stack, false)) {
            if (pass.isCustomRenderer()) { result = false; break; }
            for (int face = -1; face < 6; face++) {
                var quads = pass.getQuads(null, face < 0 ? null : Direction.values()[face], RandomSource.create(42));
                if (quads.stream().anyMatch(quad -> quad.getSprite().contents().getUniqueFrames().limit(2).count() > 1)) {
                    result = false; break;
                }
            }
            if (!result) break;
        }
        staticItems.put(key, result);
        if (staticItems.size() > 256) staticItems.remove(staticItems.keySet().iterator().next());
        return result;
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
        render(graphics, x, y, size, inputAlpha, depth, inputOutline, outlineWidth, draw, null);
    }

    private static void render(GuiGraphics graphics, int x, int y, int size, float inputAlpha, float depth,
                                float inputOutline, float outlineWidth, Draw draw, String itemKey) {
        float alpha = normalizedAlpha(inputAlpha);
        float outline = normalizedOutline(inputOutline);
        if (alpha == 0 || size <= 0) return;
        if (alpha == 1 && outline == 0 && itemKey == null) {
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
        // Oversized icons keep the full-resolution live path instead of upscaling a cached tile.
        ItemTileKey tileKey = itemKey != null && pixels <= 256 ? new ItemTileKey(itemKey, size, pixels) : null;
        ItemTile ready = tileKey == null ? null : itemTiles.get(tileKey);
        if (ready != null) {
            if (MEASURE) cacheHits++;
            // Native buffer submission owns shader/blend/depth state, just like GUI text and
            // backgrounds. A hit needs no GL state queries, framebuffer switch or explicit flush.
            var quad = graphics.bufferSource().getBuffer(ready.renderType());
            float uv = pixels / (float) ready.target().width;
            float left = x - padding, top = y - padding, right = left + canvas, bottom = top + canvas;
            quad.vertex(pose, left, top, depth).uv(0, uv).color(alpha, alpha, alpha, alpha).endVertex();
            quad.vertex(pose, left, bottom, depth).uv(0, 0).color(alpha, alpha, alpha, alpha).endVertex();
            quad.vertex(pose, right, bottom, depth).uv(uv, 0).color(alpha, alpha, alpha, alpha).endVertex();
            quad.vertex(pose, right, top, depth).uv(uv, uv).color(alpha, alpha, alpha, alpha).endVertex();
            return;
        }

        // Finish the caller's batches before switching the framebuffer. Even GuiGraphics.flush()
        // changes depth state, so it is included in the outer save/restore scope.
        TextureTarget target = null;
        boolean cached = false;
        long targetGeneration = generation;
        try (PortraitRenderState outer = new PortraitRenderState(true)) {
            graphics.flush();
            if (MEASURE) guiFlushes++;
            var tile = tileKey == null ? null : itemTiles.get(tileKey);
            target = tile == null ? null : tile.target();
            cached = target != null;
            if (cached && MEASURE) cacheHits++;
            if (!cached) try (PortraitRenderState offscreen = new PortraitRenderState(true)) {
                if (MEASURE) { offscreenPasses++; if (tileKey != null) cacheMisses++; }
                try {
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                    target = tileKey == null ? targets.pollFirst() : null;
                    if (target == null) {
                        int targetSize = tileKey == null ? MAX_TARGET_SIZE : pixels;
                        target = new TextureTarget(targetSize, targetSize, true, Minecraft.ON_OSX);
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
                    if (MEASURE) guiFlushes++;
                } finally {
                    discard(Buffers.ITEMS);
                    for (BufferBuilder fixed : Buffers.FIXED.values()) discard(fixed);
                }
            }
            if (tileKey != null && !cached && targetGeneration == generation) {
                itemTiles.put(tileKey, new ItemTile(target, TileRenderType.forTexture(target.getColorTextureId())));
                tilePixels += (long) target.width * target.height;
                cached = true;
                while (itemTiles.size() > 256 || tilePixels > 4L * 1024 * 1024) {
                    var iterator = itemTiles.entrySet().iterator();
                    TextureTarget oldest = iterator.next().getValue().target();
                    iterator.remove();
                    tilePixels -= (long) oldest.width * oldest.height;
                    // RenderTarget.destroyBuffers unbinds the current framebuffer as a side effect.
                    try (PortraitRenderState eviction = new PortraitRenderState(true)) { oldest.destroyBuffers(); }
                }
            }

            composite(graphics, target, x, y, canvas, padding, pixels, scale, depth, alpha, outline, outlineWidth);
        } finally {
            if (target != null && !cached) {
                if (tileKey == null) release(target, targetGeneration);
                else try (PortraitRenderState ignored = new PortraitRenderState()) { target.destroyBuffers(); }
            }
        }
    }

    private static void composite(GuiGraphics graphics, TextureTarget target, int x, int y,
                                  int canvas, int padding, int pixels, double scale, float depth,
                                  float alpha, float outline, float outlineWidth) {
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
                float step = (float) (Math.max(1, pixels * outlineWidth / (canvas * Math.max(scale, .0001))) / target.width);
                shader.safeGetUniform("OutlineStep").set(step, step);
                shader.safeGetUniform("OutlineOpacity").set(outline);
                RenderSystem.setShader(() -> shader);
            } else RenderSystem.setShader(ObjectiveIconShaders::premultiplied);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            RenderSystem.setShaderColor(alpha, alpha, alpha, alpha);
            float maxUv = pixels / (float) target.width;
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
    }

    private static final class TileRenderType extends RenderType {
        private TileRenderType() {
            super("arc_quest_cached_item", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                    256, false, false, () -> {}, () -> {});
        }
        static RenderType forTexture(int texture) {
            return create("arc_quest_cached_item", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                    256, false, false, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new EmptyTextureStateShard(() -> RenderSystem.setShaderTexture(0, texture), () -> {}))
                            .setTransparencyState(new TransparencyStateShard("premultiplied_icon", () -> {
                                RenderSystem.enableBlend();
                                RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                                        GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                            }, () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); }))
                            .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
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
        staticItems.clear();
        try (PortraitRenderState ignored = new PortraitRenderState()) {
            for (ItemTile tile : itemTiles.values()) tile.target().destroyBuffers();
            itemTiles.clear();
            tilePixels = 0;
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
