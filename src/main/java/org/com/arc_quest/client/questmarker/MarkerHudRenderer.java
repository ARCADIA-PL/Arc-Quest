package org.com.arc_quest.client.questmarker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.com.arc_quest.questmarker.api.QuestMarkerData;
import org.com.arc_quest.questmarker.api.QuestMarkerType;

public class MarkerHudRenderer {

    private long startTime = System.currentTimeMillis();

    @SubscribeEvent
    public void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        GuiGraphics gui = event.getGuiGraphics();
        Player player = mc.player;
        Font font = mc.font;

        float time = (System.currentTimeMillis() - startTime) / 1000.0f;

        double px = player.getX();
        double py = player.getEyeY();
        double pz = player.getZ();

        for (QuestMarkerData marker : QuestMarkerManager.INSTANCE.all()) {
            if (!marker.isActive()) continue;

            MarkerProjection.ScreenResult result = MarkerProjection.project(
                    marker.getWorldX(), marker.getWorldY(), marker.getWorldZ());

            float sx = result.x;
            float sy = result.y;
            double dist = marker.distanceTo(px, py, pz);
            int color = marker.getColorARGB();
            float sizeFactor = (float) Math.max(0.5, Math.min(1.5, 30.0 / (dist + 5.0)));

            if (result.onScreen) {
                MarkerRenderUtil.drawPulseRing(gui, sx, sy, 8 * sizeFactor, time, 0.8f, color);

                switch (resolveRenderType(marker.getType())) {
                    case QUEST_MAIN -> {
                        MarkerRenderUtil.drawDiamond(gui, sx, sy, 7 * sizeFactor, color);
                        MarkerRenderUtil.drawDiamond(gui, sx, sy, 4 * sizeFactor, 0xFF000000);
                        MarkerRenderUtil.drawDiamond(gui, sx, sy, 3 * sizeFactor, color);
                    }
                    case QUEST_SIDE -> MarkerRenderUtil.drawDiamondOutline(gui, sx, sy, 7 * sizeFactor, 2, color);
                    case NPC_INTERACT -> MarkerRenderUtil.drawCircle(gui, sx, sy, 6 * sizeFactor, 24, color);
                    case ENEMY_TARGET -> {
                        MarkerRenderUtil.drawArrow(gui, sx, sy, (float) Math.toRadians(45), 6 * sizeFactor, color);
                        MarkerRenderUtil.drawArrow(gui, sx, sy, (float) Math.toRadians(225), 6 * sizeFactor, color);
                    }
                    case LOCATION -> MarkerRenderUtil.drawCircle(gui, sx, sy, 5 * sizeFactor, 6, color);
                    default -> MarkerRenderUtil.drawCircle(gui, sx, sy, 5 * sizeFactor, 16, color);
                }

                float boxW = 60 * sizeFactor;
                float boxH = 24 * sizeFactor;
                MarkerRenderUtil.drawCyberneticEdge(gui, sx, sy - 16 * sizeFactor,
                        boxW, boxH, 4 * sizeFactor, (color & 0x00FFFFFF) | 0x88000000);

                String name = marker.getLabel();
                int textW = font.width(name);
                gui.drawString(font, name, (int)(sx - textW / 2.0f), (int)(sy - 22 * sizeFactor), color, true);

                if (marker.isShowDistance()) {
                    String distText = String.format("%.0fm", dist);
                    int distW = font.width(distText);
                    gui.drawString(font, distText,
                            (int)(sx - distW / 2.0f), (int)(sy + 10 * sizeFactor), 0xAAFFFFFF, false);
                }

            } else if (marker.isAllowOffscreenArrow()) {
                float arrowAngle = result.edgeAngle;
                MarkerRenderUtil.drawArrow(gui, sx, sy, arrowAngle, 10 * sizeFactor, color);

                String distText = String.format("%.0fm", dist);
                int distW = font.width(distText);
                float textOffX = -(float) Math.cos(arrowAngle) * 18;
                float textOffY = -(float) Math.sin(arrowAngle) * 18;
                gui.drawString(font, distText,
                        (int)(sx + textOffX - distW / 2.0f), (int)(sy + textOffY - 4),
                        (color & 0x00FFFFFF) | 0xCC000000, false);
            }
        }
    }

    /** 将兼容别名类型规范化为主渲染类型 */
    private static QuestMarkerType resolveRenderType(QuestMarkerType type) {
        return switch (type) {
            case QUEST -> QuestMarkerType.QUEST_MAIN;
            case NPC, INTERACT -> QuestMarkerType.NPC_INTERACT;
            case ENEMY -> QuestMarkerType.ENEMY_TARGET;
            default -> type;
        };
    }
}
