package org.arcadia.arc_quest.client.hud.guide;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconsClient;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.PortraitRenderState;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resource lookup is bounded and invalidated with icon resources, never decoded per frame. */
public final class GuideImageRenderer {
    private static final Map<ResourceLocation, Boolean> AVAILABLE = new LinkedHashMap<>(32, .75f, true);
    private static long generation = -1;
    private GuideImageRenderer() {}

    public static boolean available(GuideMediaDefinition media) {
        if (media == null || media.getType() != GuideMediaType.IMAGE || media.getTexture() == null) return false;
        if (generation != ObjectiveIconsClient.generation()) {
            AVAILABLE.clear();
            generation = ObjectiveIconsClient.generation();
        }
        Boolean exists = AVAILABLE.get(media.getTexture());
        if (exists == null) {
            exists = Minecraft.getInstance().getResourceManager().getResource(media.getTexture()).isPresent();
            AVAILABLE.put(media.getTexture(), exists);
            while (AVAILABLE.size() > 128) AVAILABLE.remove(AVAILABLE.keySet().iterator().next());
        }
        return exists;
    }

    /** Caller owns clipping in its screen's coordinates, including custom journal scaling. */
    public static void draw(GuiGraphics graphics, GuideMediaDefinition media, int x, int y, int width, int height,
                            GuideImageLayout.Fit fit, float alpha) {
        if (alpha <= 0 || !available(media)) return;
        var box = GuideImageLayout.measure(x, y, width, height, media.getWidth(), media.getHeight(), fit);
        if (box.width() <= 0 || box.height() <= 0) return;
        try (PortraitRenderState ignored = new PortraitRenderState()) {
            graphics.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, Math.min(1, alpha));
            graphics.blit(media.getTexture(), box.x(), box.y(), box.width(), box.height(), 0f, 0f,
                    Math.max(1, media.getWidth()), Math.max(1, media.getHeight()),
                    Math.max(1, media.getWidth()), Math.max(1, media.getHeight()));
        }
    }
}
