package org.arcadia.arc_quest.client.hud.quest.history;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.quest.QuestIconRenderer;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.VisualAsset;

import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class QuestHistoryImageRenderer {

    private static final int MAX_CACHED_IMAGES = 128;
    private static final Map<ResourceLocation, ImageInfo> IMAGE_INFO = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, ImageInfo> eldest) {
            return size() > MAX_CACHED_IMAGES;
        }
    };
    private static final Set<ResourceLocation> PENDING = new HashSet<>();
    private static ResourceManager cachedResourceManager;

    private QuestHistoryImageRenderer() {
    }

    public static RenderResult renderCover(GuiGraphics graphics, VisualAsset asset, int x, int y, int width, int height,
                                           float alpha, boolean blurred, int themeColor) {
        if (asset == null || !asset.enabled()) return RenderResult.UNAVAILABLE;
        refreshCacheOwner();
        if (asset.item() != null && !asset.item().isEmpty()) {
            if (blurred) return RenderResult.UNAVAILABLE;
            renderLoadingBackground(graphics, x, y, width, height, alpha, themeColor, false);
            int size = Math.min(width, height) - 14;
            int iconX = x + (width - size) / 2;
            int iconY = y + (height - size) / 2;
            QuestIconRenderer.renderIcon(graphics, asset, iconX, iconY, size, size, alpha);
            return RenderResult.DRAWN;
        }

        ResourceLocation texture = asset.texture();
        if (texture == null) return RenderResult.UNAVAILABLE;
        ImageInfo info = IMAGE_INFO.get(texture);
        if (info == null) {
            requestImageInfo(texture);
            renderLoadingBackground(graphics, x, y, width, height, alpha, themeColor, true);
            return RenderResult.LOADING;
        }
        if (!info.available()) return RenderResult.UNAVAILABLE;

        UvRect uv = coverUv(info.width(), info.height(), width, height, Math.max(0.1f, asset.scale()));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (blurred) {
            renderLowResolution(graphics, texture, x, y, width, height, uv, info, asset, alpha);
            graphics.fill(x, y, x + width, y + height, HudAnimUtil.withAlpha(0x05070A, Math.round(128 * alpha)));
        } else {
            applyTint(asset, alpha);
            blit(graphics, texture, x, y, width, height, uv, info);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        return RenderResult.DRAWN;
    }

    private static void renderLowResolution(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                            int width, int height, UvRect uv, ImageInfo info,
                                            VisualAsset asset, float alpha) {
        int columns = 16;
        int rows = 10;
        applyTint(asset, alpha);
        for (int row = 0; row < rows; row++) {
            int top = y + row * height / rows;
            int bottom = y + (row + 1) * height / rows;
            float sampleV = uv.v() + uv.height() * (row + 0.5f) / rows;
            int sourceY = Math.max(0, Math.min(info.height() - 1, Math.round(sampleV * (info.height() - 1))));
            for (int column = 0; column < columns; column++) {
                int left = x + column * width / columns;
                int right = x + (column + 1) * width / columns;
                float sampleU = uv.u() + uv.width() * (column + 0.5f) / columns;
                int sourceX = Math.max(0, Math.min(info.width() - 1, Math.round(sampleU * (info.width() - 1))));
                graphics.blit(texture, left, top, right - left, bottom - top,
                        sourceX, sourceY, 1, 1, info.width(), info.height());
            }
        }
    }

    private static void applyTint(VisualAsset asset, float alpha) {
        int tint = asset.tintColor();
        float tintAlpha = ((tint >>> 24) & 0xFF) / 255f;
        float red = ((tint >>> 16) & 0xFF) / 255f;
        float green = ((tint >>> 8) & 0xFF) / 255f;
        float blue = (tint & 0xFF) / 255f;
        RenderSystem.setShaderColor(red, green, blue, alpha * tintAlpha);
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                             UvRect uv, ImageInfo info) {
        float sourceX = uv.u() * info.width();
        float sourceY = uv.v() * info.height();
        int sourceWidth = Math.max(1, Math.round(uv.width() * info.width()));
        int sourceHeight = Math.max(1, Math.round(uv.height() * info.height()));
        graphics.blit(texture, x, y, width, height, sourceX, sourceY,
                sourceWidth, sourceHeight, info.width(), info.height());
    }

    private static UvRect coverUv(int imageWidth, int imageHeight, int targetWidth, int targetHeight, float scale) {
        float sourceAspect = imageWidth / (float) imageHeight;
        float targetAspect = targetWidth / (float) targetHeight;
        float visibleWidth = sourceAspect > targetAspect ? targetAspect / sourceAspect : 1f;
        float visibleHeight = sourceAspect < targetAspect ? sourceAspect / targetAspect : 1f;
        visibleWidth = Math.min(1f, visibleWidth / scale);
        visibleHeight = Math.min(1f, visibleHeight / scale);
        float u = (1f - visibleWidth) * 0.5f;
        float v = (1f - visibleHeight) * 0.5f;
        return new UvRect(u, v, visibleWidth, visibleHeight);
    }

    private static void renderLoadingBackground(GuiGraphics graphics, int x, int y, int width, int height,
                                                float alpha, int themeColor, boolean animate) {
        graphics.fill(x, y, x + width, y + height, HudAnimUtil.withAlpha(0x10141A, Math.round(230 * alpha)));
        if (!animate) return;
        int sweepWidth = Math.max(12, width / 5);
        int travel = width + sweepWidth;
        int sweepX = x - sweepWidth + (int) ((Util.getMillis() / 12L) % travel);
        int left = Math.max(x, sweepX);
        int right = Math.min(x + width, sweepX + sweepWidth);
        if (left < right) {
            graphics.fill(left, y, right, y + height, HudAnimUtil.withAlpha(themeColor, Math.round(22 * alpha)));
        }
    }

    private static void requestImageInfo(ResourceLocation texture) {
        if (!PENDING.add(texture)) return;
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        CompletableFuture.supplyAsync(() -> readImageInfo(resourceManager, texture), Util.ioPool())
                .whenComplete((info, error) -> Minecraft.getInstance().execute(() -> {
                    if (resourceManager != cachedResourceManager) return;
                    PENDING.remove(texture);
                    IMAGE_INFO.put(texture, error == null && info != null ? info : ImageInfo.MISSING);
                }));
    }

    private static void refreshCacheOwner() {
        ResourceManager current = Minecraft.getInstance().getResourceManager();
        if (current == cachedResourceManager) return;
        cachedResourceManager = current;
        IMAGE_INFO.clear();
        PENDING.clear();
    }

    private static ImageInfo readImageInfo(ResourceManager resourceManager, ResourceLocation texture) {
        Optional<Resource> resource = resourceManager.getResource(texture);
        if (resource.isEmpty()) return ImageInfo.MISSING;
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            return new ImageInfo(image.getWidth(), image.getHeight(), true);
        } catch (Exception exception) {
            Arc_Quest.LOGGER.warn("[ArcQuest] Failed to read quest history image metadata: {}", texture, exception);
            return ImageInfo.MISSING;
        }
    }

    public enum RenderResult {
        DRAWN,
        LOADING,
        UNAVAILABLE
    }

    private record ImageInfo(int width, int height, boolean available) {
        private static final ImageInfo MISSING = new ImageInfo(1, 1, false);
    }

    private record UvRect(float u, float v, float width, float height) {
        private UvRect shifted(float du, float dv) {
            float shiftedU = Math.max(0f, Math.min(1f - width, u + du));
            float shiftedV = Math.max(0f, Math.min(1f - height, v + dv));
            return new UvRect(shiftedU, shiftedV, width, height);
        }
    }
}
