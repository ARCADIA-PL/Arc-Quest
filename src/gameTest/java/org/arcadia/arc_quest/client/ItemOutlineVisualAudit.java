package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconAlpha;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.PortraitRenderState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.file.Path;
import java.util.ArrayDeque;

/** Same-frame native product and cost pairs expose silhouette-only changes against unoutlined draws. */
final class ItemOutlineVisualAudit extends Screen {
    private static final int SIZE = 32, COST_SIZE = 12, MARGIN = 6;
    private final ItemStack sword = new ItemStack(Items.IRON_SWORD);
    private final ItemStack emerald = new ItemStack(Items.EMERALD);
    private int plainX, outlinedX, iconY, costY;
    private long[] frameCounters;

    ItemOutlineVisualAudit() { super(Component.literal("Native product / cost silhouette outline pixel audit")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        graphics.fill(0, 0, width, height, 0xFF101820);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
        plainX = width / 2 - 76; outlinedX = width / 2 + 44; iconY = height / 2 - 48; costY = iconY + 80;
        graphics.drawCenteredString(font, "Native sword / 32px", plainX + SIZE / 2, iconY - 22, 0xFFB0C4D0);
        graphics.drawCenteredString(font, "1 GUI-pixel outline", outlinedX + SIZE / 2, iconY - 22, 0xFFB0C4D0);
        graphics.drawCenteredString(font, "Native cost / 12px", plainX + SIZE / 2, costY - 22, 0xFFB0C4D0);
        graphics.drawCenteredString(font, "0.5 GUI-pixel outline", outlinedX + SIZE / 2, costY - 22, 0xFFB0C4D0);
        graphics.flush();
        long[] before = ObjectiveIconAlpha.performanceCounters();
        ObjectiveIconAlpha.renderItem(graphics, sword, plainX, iconY, SIZE, 1);
        ObjectiveIconAlpha.renderOutlinedItem(graphics, sword, outlinedX, iconY, SIZE, 1, 1);
        int inset = (SIZE - COST_SIZE) / 2;
        ObjectiveIconAlpha.renderItem(graphics, emerald, plainX + inset, costY, COST_SIZE, 1);
        ObjectiveIconAlpha.renderOutlinedItem(graphics, emerald, outlinedX + inset, costY, COST_SIZE, 1, 1, .5f);
        graphics.flush();
        long[] after = ObjectiveIconAlpha.performanceCounters();
        frameCounters = new long[after.length];
        for (int i = 0; i < after.length; i++) frameCounters[i] = after[i] - before[i];
    }

    record PairResult(String item, int logicalSize, float logicalStrokeWidth, int silhouetteWhitePixels, int preservedInteriorPixels, double interiorMeanError,
                  int emptyCornerPixels, int exteriorPixels, double maximumExteriorError,
                  int nativeForegroundPixels, int expectedOutlinePixels, int outlinePixelMismatches, double maximumForegroundError) {}
    record Result(PairResult product, PairResult cost, long nativeItemCalls, long offscreenPasses) {}
    record TargetDump(String lastRenderedItem, int texture, int width, int height, int nonzeroAlphaPixels, int nonzeroRgbPixels,
                      int maximumAlpha, int left, int top, int right, int bottom, Path file) {}

    /** Failure-only readback of the last returned FBO; never redraw, clear, or mutate its contents. */
    static TargetDump dumpReusableTarget(Path file) throws Exception {
        var field = ObjectiveIconAlpha.class.getDeclaredField("targets"); field.setAccessible(true);
        var pool = (ArrayDeque<?>) field.get(null);
        require(pool.peekFirst() instanceof TextureTarget, "No returned item target is available for failure diagnostics");
        var target = (TextureTarget) pool.peekFirst();
        try (var ignored = new PortraitRenderState()) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTextureForSetup(target.getColorTextureId());
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            require(width > 0 && height > 0, "Returned item target texture has no storage");
            try (var pixels = new NativeImage(width, height, false)) {
                pixels.downloadTexture(0, false); pixels.flipY();
                int visible = 0, rgb = 0, maximum = 0, left = width, top = height, right = -1, bottom = -1;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int pixel = pixels.getPixelRGBA(x, y), alpha = pixel >>> 24;
                    if ((pixel & 0xFFFFFF) != 0) rgb++;
                    if (alpha == 0) continue;
                    visible++; maximum = Math.max(maximum, alpha);
                    left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y);
                }
                pixels.writeToFile(file);
                return new TargetDump("emerald_cost", target.getColorTextureId(), width, height, visible, rgb, maximum, left, top, right, bottom, file);
            }
        }
    }

    Result verifyPixels(NativeImage image) {
        var product = verifyPair(image, "iron_sword", plainX, outlinedX, iconY, SIZE, 1);
        int inset = (SIZE - COST_SIZE) / 2;
        var cost = verifyPair(image, "emerald_cost", plainX + inset, outlinedX + inset, costY, COST_SIZE, .5f);
        require(frameCounters != null && frameCounters[0] == 4 && frameCounters[1] == 2 && frameCounters[2] == 2,
                "Product/cost pairs must make four native item calls, two direct calls and two offscreen passes: " + java.util.Arrays.toString(frameCounters));
        return new Result(product, cost, frameCounters[0], frameCounters[2]);
    }
    private PairResult verifyPair(NativeImage image, String item, int plainLeft, int outlineLeft, int top, int size, float strokeWidth) {
        var plain = sample(image, plainLeft, top, size);
        var outlined = sample(image, outlineLeft, top, size);
        require(plain.width() == outlined.width() && plain.height() == outlined.height(), "Paired item sample dimensions differ");
        int background = image.getPixelRGBA(0, image.getHeight() - 1);
        int w = plain.width(), h = plain.height();
        boolean[] mask = new boolean[w * h];
        int minX = w, minY = h, maxX = -1, maxY = -1, filled = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            // Both samples come from the same lossless framebuffer over a flat background.
            // Mean-luminance thresholds discard the sword's real dark outline: (24,24,24)
            // differs from this background (16,24,32) by only 5.33 per channel on average.
            mask[i] = (plain.pixels()[i] & 0xFFFFFF) != (background & 0xFFFFFF);
            if (mask[i]) { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); filled++; }
        }
        require(filled >= 80, "Native item baseline did not render: " + item);
        int strokeRadius = Math.max(1, (int) Math.ceil(image.getWidth() / (double) width * strokeWidth));
        int radius = strokeRadius + 1;
        int white = 0, interior = 0, corners = 0, exterior = 0;
        int expectedRing = 0, ringMismatches = 0;
        double interiorError = 0, maximumExteriorError = 0;
        double maximumForegroundError = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x, a = plain.pixels()[i], b = outlined.pixels()[i];
            boolean nearby = nearby(mask, w, h, x, y, radius);
            if (mask[i]) maximumForegroundError = Math.max(maximumForegroundError, difference(a, b));
            else {
                boolean expectedWhite = nearby(mask, w, h, x, y, strokeRadius);
                if (expectedWhite) expectedRing++;
                if (expectedWhite ? !white(b) : difference(a, b) >= 3) ringMismatches++;
            }
            if (difference(a, background) < 3 && white(b)) {
                require(nearby, "White pixels escaped the actual item silhouette at " + item + "/" + x + "," + y);
                white++;
            }
            if (interior(mask, w, h, x, y, radius)) { interior++; interiorError += difference(a, b); }
            if (!nearby) {
                double error = difference(a, b);
                maximumExteriorError = Math.max(maximumExteriorError, error); exterior++;
                boolean nearX = Math.abs(x - minX) <= radius + 2 || Math.abs(x - maxX) <= radius + 2;
                boolean nearY = Math.abs(y - minY) <= radius + 2 || Math.abs(y - maxY) <= radius + 2;
                if (nearX && nearY) {
                    corners++;
                    require(error < 3, "An empty bounding-box corner acquired a rectangular outline at " + x + "," + y);
                }
            }
        }
        require(ringMismatches == 0 && white == expectedRing,
                "White outline does not match the exact dilation ring: item=" + item + " guiStroke=" + strokeWidth + " expected=" + expectedRing
                        + " actual=" + white + " mismatches=" + ringMismatches);
        require(maximumForegroundError < 3, "Outline changed native foreground colors: maximumError=" + maximumForegroundError);
        require(white >= Math.max(16, w / 2), "Native item lacks a visible white silhouette: item=" + item + " whitePixels=" + white);
        require(interior >= 20 && interiorError / interior < 4,
                "Outline recolored the original item interior: samples=" + interior + " meanError=" + interiorError / Math.max(1, interior));
        require(corners >= 16 && exterior > filled && maximumExteriorError < 3,
                "Outline filled the item rectangle/background: corners=" + corners + " exterior=" + exterior + " maxError=" + maximumExteriorError);
        return new PairResult(item, size, strokeWidth, white, interior, interiorError / interior, corners, exterior, maximumExteriorError,
                filled, expectedRing, ringMismatches, maximumForegroundError);
    }
    private ObjectiveIconPixelAudit.Sample sample(NativeImage image, int x, int y, int size) {
        return ObjectiveIconPixelAudit.sample(image, width, height,
                new ObjectiveIconPixelAudit.Rect(x - MARGIN, y - MARGIN, size + MARGIN * 2, size + MARGIN * 2));
    }
    private static boolean nearby(boolean[] mask, int w, int h, int x, int y, int radius) {
        for (int yy = Math.max(0, y - radius); yy <= Math.min(h - 1, y + radius); yy++)
            for (int xx = Math.max(0, x - radius); xx <= Math.min(w - 1, x + radius); xx++) if (mask[yy * w + xx]) return true;
        return false;
    }
    private static boolean interior(boolean[] mask, int w, int h, int x, int y, int radius) {
        if (x < radius || y < radius || x + radius >= w || y + radius >= h) return false;
        for (int yy = y - radius; yy <= y + radius; yy++)
            for (int xx = x - radius; xx <= x + radius; xx++) if (!mask[yy * w + xx]) return false;
        return true;
    }
    private static boolean white(int pixel) {
        int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
        return Math.min(r, Math.min(g, b)) >= 200 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 12;
    }
    private static double difference(int a, int b) { return ObjectiveIconPixelAudit.difference(a, b); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
