package org.arcadia.arc_quest.client.hud.questmarker;

import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;

import java.util.HashMap;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MarkerHudRenderer implements LayeredDraw.Layer {

    public static final MarkerHudRenderer INSTANCE = new MarkerHudRenderer();

    private static final float EDGE_PADDING = 30f;
    private static final float OFFSCREEN_INSET = 25f;

    private static final float POSITION_SMOOTH_ONSCREEN = 0.24f;
    private static final float POSITION_SMOOTH_OFFSCREEN = 0.14f;

    private static final float ANIMATION_SPEED = 6.66f;
    private static final float TIER_TRANSITION_SPEED = 18.0f;
    private static final float OCCLUSION_TRANSITION_SPEED = 12.0f;
    private static final float FAR_OFFSCREEN_SCALE_BOOST = 0.18f;

    private static final long OFFSCREEN_ENTER_DELAY_MS = 60L;
    private static final long OFFSCREEN_EXIT_DELAY_MS = 90L;
    private static final String ENTITY_MARKER_GUID_KEY = "arc_quest.marker_guid";
    private static double closestDistanceThreshold = 3.0;
    private static double nearDistanceThreshold = 25.0;
    private static double farDistanceThreshold = 50.0;
    private final Map<String, MarkerVisualState> stateMap = new HashMap<>();
    private long lastTimeMs = System.currentTimeMillis();

    private MarkerHudRenderer() {
    }

    public static void setDistanceThresholds(double closest, double near, double far) {
        closestDistanceThreshold = closest;
        nearDistanceThreshold = Math.max(closest, near);
        farDistanceThreshold = Math.max(nearDistanceThreshold, far);
    }

    public static double getClosestDistanceThreshold() {
        return closestDistanceThreshold;
    }

    public static double getNearDistanceThreshold() {
        return nearDistanceThreshold;
    }

    public static double getFarDistanceThreshold() {
        return farDistanceThreshold;
    }

    public void clearVisualState() {
        stateMap.clear();
        lastTimeMs = System.currentTimeMillis();
    }

    private static float[] insetFromEdge(float x, float y, float cx, float cy, float inset) {
        float dx = x - cx;
        float dy = y - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-6f) return new float[]{x, y};
        return new float[]{x - (dx / len) * inset, y - (dy / len) * inset};
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static double lerp(double from, double to, float t) {
        return from + (to - from) * t;
    }

    private static float lerpAngle(float from, float to, float t) {
        float diff = normalizeAngle(to - from);
        return from + diff * t;
    }

    private static float normalizeAngle(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2.0);
        while (a < -Math.PI) a += (float) (Math.PI * 2.0);
        return a;
    }

    private static int withAlpha(int rgb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static int normalizeColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        return alpha == 0 ? 0xFFFFFFFF : argb;
    }

    private static String getEntityMarkerGuid(Entity entity) {
        if (entity == null) return "";
        return entity.getPersistentData().getString(ENTITY_MARKER_GUID_KEY);
    }

    private static Entity findEntityByUuid(ClientLevel level, String uuidString) {
        if (uuidString == null || uuidString.isEmpty()) return null;
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (Exception ignored) {
            return null;
        }
        for (Entity e : level.entitiesForRendering()) {
            if (uuid.equals(e.getUUID())) return e;
        }
        return null;
    }

    private static Entity findEntityByGuid(ClientLevel level, String guid) {
        if (guid == null || guid.isEmpty()) return null;
        for (Entity e : level.entitiesForRendering()) {
            if (guid.equals(getEntityMarkerGuid(e))) return e;
        }
        return null;
    }

    private static boolean matchesBinding(QuestMarkerData marker, Entity entity) {
        if (entity == null || !entity.isAlive()) return false;
        boolean hasUuid = marker.hasEntityUuidBinding();
        boolean hasGuid = marker.hasEntityGuidBinding();
        if (hasUuid && !marker.getFollowEntityUuid().equals(entity.getUUID().toString())) return false;
        if (hasGuid) {
            String entityGuid = getEntityMarkerGuid(entity);
            if (hasUuid) {
                if (entityGuid != null && !entityGuid.isEmpty() && !marker.getFollowEntityGuid().equals(entityGuid))
                    return false;
            } else {
                if (entityGuid == null || entityGuid.isEmpty()) return false;
                if (!marker.getFollowEntityGuid().equals(entityGuid)) return false;
            }
        }
        return hasUuid || hasGuid;
    }

    @Override
    public void render(GuiGraphics gui, DeltaTracker deltaTracker) {
        int sw = gui.guiWidth();
        int sh = gui.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;
        if (mc.screen instanceof DialogueScreen) return;

        Player player = mc.player;
        Font font = mc.font;
        Camera camera = mc.gameRenderer.getMainCamera();

        double px = lerp(player.xo, player.getX(), partialTick);
        double py = lerp(player.yo, player.getY(), partialTick) + player.getEyeHeight();
        double pz = lerp(player.zo, player.getZ(), partialTick);

        Vec3 camPos = camera.getPosition();
        boolean isFirstPerson = mc.options.getCameraType().isFirstPerson();

        long now = System.currentTimeMillis();
        float dt = (now - lastTimeMs) / 1000.0f;
        if (dt > 0.1f) dt = 0.1f;
        lastTimeMs = now;
        float time = (now % 100000L) / 1000.0f;

        float cx = sw * 0.5f;
        float cy = sh * 0.5f;

        stateMap.keySet().removeIf(id -> !QuestMarkerManager.INSTANCE.has(id));
        String currentDim = player.level().dimension().location().toString();

        AABB playerOcclusionBox = player.getBoundingBox().inflate(0.15);

        for (QuestMarkerData marker : QuestMarkerManager.INSTANCE.all().stream()
                .sorted(Comparator.comparingInt(QuestMarkerData::getPriority))
                .toList()) {
            if (!marker.isActive()) continue;
            if (!currentDim.equals(marker.getDimension())) continue;
            if (marker.hasEntityBinding() && !marker.hasEntityGuidBinding() && !marker.hasEntityUuidBinding()) continue;

            double targetX = marker.getWorldX();
            double targetY = marker.getWorldY();
            double targetZ = marker.getWorldZ();
            MarkerVisualState st = stateMap.computeIfAbsent(marker.getId(), ignored -> new MarkerVisualState());

            if (marker.hasEntityBinding()) {
                Entity entity = null;
                if (marker.getFollowEntityId() >= 0) {
                    Entity fast = mc.level.getEntity(marker.getFollowEntityId());
                    if (matchesBinding(marker, fast)) entity = fast;
                }
                if (entity == null && marker.hasEntityGuidBinding()) {
                    if (now < st.nextEntityResolveMs) continue;
                    Entity byGuid = findEntityByGuid(mc.level, marker.getFollowEntityGuid());
                    if (matchesBinding(marker, byGuid)) entity = byGuid;
                }
                if (entity == null && marker.hasEntityUuidBinding()) {
                    if (now < st.nextEntityResolveMs) continue;
                    Entity byUuid = findEntityByUuid(mc.level, marker.getFollowEntityUuid());
                    if (matchesBinding(marker, byUuid)) entity = byUuid;
                }
                if (entity == null) {
                    st.nextEntityResolveMs = now + 250L;
                    continue;
                }
                st.nextEntityResolveMs = 0L;

                targetX = lerp(entity.xo, entity.getX(), partialTick);
                targetZ = lerp(entity.zo, entity.getZ(), partialTick);
                double lerpedY = lerp(entity.yo, entity.getY(), partialTick);

                targetY = lerpedY + (marker.getAttachPoint() == QuestMarkerData.EntityAttachPoint.HEAD
                        ? entity.getEyeHeight() + entity.getBbHeight() * 0.7
                        : entity.getBbHeight() * 0.5);
            }

            MarkerProjection.ScreenResult proj = MarkerProjection.project(targetX, targetY, targetZ, EDGE_PADDING);

            double dxDist = targetX - px;
            double dyDist = targetY - py;
            double dzDist = targetZ - pz;
            double dist = Math.sqrt(dxDist * dxDist + dyDist * dyDist + dzDist * dzDist);

            float targetTier = DistanceTier.getTierForDistance(dist).getTargetValue();
            boolean isQuestActive = marker.getState() == QuestMarkerState.ACTIVE;
            float targetActive = isQuestActive ? 1.0f : 0.0f;

            boolean isOccluded = false;
            if (!isFirstPerson && proj.onScreen) {
                Vec3 markerPos = new Vec3(targetX, targetY, targetZ);
                Optional<Vec3> hit = playerOcclusionBox.clip(camPos, markerPos);
                if (hit.isPresent()) isOccluded = true;
            }

            float targetOcclusionAlpha = isOccluded ? 0.15f : 1.0f;

            float tierDistRatio = 0.0f;
            if (dist <= closestDistanceThreshold) {
                tierDistRatio = (float) (dist / Math.max(1.0, closestDistanceThreshold));
            } else if (dist <= nearDistanceThreshold) {
                float range = (float) (nearDistanceThreshold - closestDistanceThreshold);
                tierDistRatio = range > 0 ? (float) ((dist - closestDistanceThreshold) / range) : 0f;
            } else if (dist <= farDistanceThreshold) {
                float range = (float) (farDistanceThreshold - nearDistanceThreshold);
                tierDistRatio = range > 0 ? (float) ((dist - nearDistanceThreshold) / range) : 0f;
            } else {
                float range = 50.0f;
                tierDistRatio = (float) Math.min(1.0, (dist - farDistanceThreshold) / range);
            }

            float targetDistAlpha = 1.0f - (tierDistRatio * 0.65f);

            float dxCenter = proj.x - cx;
            float dyCenter = proj.y - cy;
            float screenDist = (float) Math.sqrt(dxCenter * dxCenter + dyCenter * dyCenter);
            float hoverFactor = 1.0f - Math.max(0f, Math.min(1f, (screenDist - 12f) / 24f));

            float targetDynamicAlpha = lerp(targetDistAlpha, 1.0f, hoverFactor);

            if (!st.initialized) {
                st.x = proj.x;
                st.y = proj.y;
                st.angle = normalizeAngle(proj.edgeAngle);
                st.offscreenStable = !proj.onScreen;
                st.transitionProgress = st.offscreenStable ? 1.0f : 0.0f;
                st.distanceTier = targetTier;
                st.occlusionAlpha = targetOcclusionAlpha;
                st.activeProgress = targetActive;
                st.dynamicDistanceAlpha = targetDynamicAlpha;
                st.switchTs = now;
                st.initialized = true;
            } else {
                boolean targetOff = !proj.onScreen;
                if (targetOff != st.offscreenStable) {
                    long need = targetOff ? OFFSCREEN_ENTER_DELAY_MS : OFFSCREEN_EXIT_DELAY_MS;
                    if (now - st.switchTs >= need) {
                        st.offscreenStable = targetOff;
                        st.switchTs = now;
                    }
                } else {
                    st.switchTs = now;
                }

                if (st.offscreenStable) {
                    st.transitionProgress = Math.min(1.0f, st.transitionProgress + dt * ANIMATION_SPEED);
                } else {
                    st.transitionProgress = Math.max(0.0f, st.transitionProgress - dt * ANIMATION_SPEED);
                }

                float tierLerpFactor = Math.min(1.0f, dt * TIER_TRANSITION_SPEED);
                st.distanceTier = lerp(st.distanceTier, targetTier, tierLerpFactor);

                float occLerpFactor = Math.min(1.0f, dt * OCCLUSION_TRANSITION_SPEED);
                st.occlusionAlpha = lerp(st.occlusionAlpha, targetOcclusionAlpha, occLerpFactor);

                st.dynamicDistanceAlpha = lerp(st.dynamicDistanceAlpha, targetDynamicAlpha, dt * 10.0f);

                st.activeProgress = lerp(st.activeProgress, targetActive, dt * 5.0f);

                float[] anchor = insetFromEdge(proj.x, proj.y, cx, cy, OFFSCREEN_INSET);
                float blendedTargetX = lerp(proj.x, anchor[0], st.transitionProgress);
                float blendedTargetY = lerp(proj.y, anchor[1], st.transitionProgress);

                if (!st.offscreenStable && st.transitionProgress <= 0.01f) {
                    st.x = proj.x;
                    st.y = proj.y;
                } else {
                    float pSmooth = st.offscreenStable ? POSITION_SMOOTH_OFFSCREEN : POSITION_SMOOTH_ONSCREEN;
                    st.x = lerp(st.x, blendedTargetX, pSmooth);
                    st.y = lerp(st.y, blendedTargetY, pSmooth);
                }

                if (st.offscreenStable) {
                    for (MarkerVisualState other : stateMap.values()) {
                        if (other != st && other.offscreenStable && other.transitionProgress > 0.5f) {
                            float dx = st.x - other.x;
                            float dy = st.y - other.y;
                            float distSqr = dx * dx + dy * dy;
                            float minSpace = 32f;

                            if (distSqr < minSpace * minSpace) {
                                if (distSqr < 0.001f) {
                                    int hash = marker.getId().hashCode();
                                    dx = ((hash & 0xFF) / 255.0f) - 0.5f;
                                    dy = (((hash >>> 8) & 0xFF) / 255.0f) - 0.5f;
                                    distSqr = dx * dx + dy * dy;
                                }
                                float pDist = (float) Math.sqrt(distSqr);
                                float push = (minSpace - pDist) * 0.15f;
                                st.x += (dx / pDist) * push;
                                st.y += (dy / pDist) * push;
                            }
                        }
                    }
                }

                float targetAngle = st.offscreenStable ? normalizeAngle(proj.edgeAngle) : 0f;
                st.angle = lerpAngle(st.angle, targetAngle, dt * 15f);
            }

            int color = normalizeColor(marker.getColorARGB());
            color = withAlpha(color, Math.round(((color >>> 24) & 0xFF)
                    * styleFloat(marker, "opacity", 1.0f, 0.05f, 1.0f)));

            float baseLightX = 0.15f;
            float baseLightY = -1.0f;
            float deflectX = (cx - st.x) / cx * 0.35f;
            float deflectY = (cy - st.y) / cy * 0.35f;

            float lx = baseLightX + deflectX;
            float ly = baseLightY + deflectY;
            float lightLen = (float) Math.sqrt(lx * lx + ly * ly);
            lx = lightLen > 0 ? lx / lightLen : 0;
            ly = lightLen > 0 ? ly / lightLen : -1;

            if (st.transitionProgress < 0.99f) {
                renderOnScreenMarker(gui, font, marker, st, st.x, st.y, color, dist, time, st.transitionProgress, lx, ly, st.distanceTier, st.occlusionAlpha, st.activeProgress, st.dynamicDistanceAlpha);
            }
            if (st.transitionProgress > 0.01f && marker.isAllowOffscreenArrow()) {
                renderOffscreenMarker(gui, font, st, marker, st.x, st.y, color, dist, st.angle, st.transitionProgress, lx, ly, st.distanceTier, time, st.activeProgress);
            }
        }
    }

    private void renderOnScreenMarker(GuiGraphics gui, Font font, QuestMarkerData marker, MarkerVisualState st, float x, float y, int color, double dist, float time, float progress, float lightX, float lightY, float tier, float occlusionAlpha, float activeProgress, float dynamicDistanceAlpha) {
        float alphaFade = 1.0f - progress;
        int originalAlpha = (color >> 24) & 0xFF;

        int currentAlpha = (int) (originalAlpha * alphaFade * occlusionAlpha * dynamicDistanceAlpha);

        if (currentAlpha <= 5) return;

        int accentColor = withAlpha(color, currentAlpha);

        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        float markerScale = styleFloat(marker, "scale", 1.0f, 0.5f, 2.0f);
        gui.pose().scale(markerScale, markerScale, 1.0f);

        float perspectiveScale = 1.0f;
        if (dist > closestDistanceThreshold) {
            float pFactor = 25.0f / (float) (dist + 25.0 - closestDistanceThreshold);
            perspectiveScale = Math.max(0.5f, pFactor);
        }

        if (tier <= 1.0f) {
            perspectiveScale = lerp(perspectiveScale, 0.85f, tier);
        } else {
            perspectiveScale = lerp(0.85f, 1.0f, tier - 1.0f);
        }

        float styleScale;
        if (tier <= 1.0f) {
            styleScale = lerp(1.0f, 0.95f, tier);
        } else {
            styleScale = lerp(0.95f, 1.0f, tier - 1.0f);
        }
        float finalScale = perspectiveScale * styleScale;

        gui.pose().pushPose();
        float ease = progress * progress;
        float scaleX = (1.0f - ease) * finalScale;
        float scaleY = (1.0f + ease * 0.8f) * finalScale;
        gui.pose().scale(scaleX, scaleY, 1.0f);

        MarkerRhombusRenderer.draw(gui, accentColor, time, activeProgress, lightX, lightY, tier);

        gui.pose().popPose();

        String name = marker.getLabelComponent().getString();
        updateLabelCache(st, font, name);
        float baseRadius = tier <= 1.0f ? lerp(16f, 10f, tier) : lerp(10f, 4f, tier - 1.0f);
        float scaledRadius = baseRadius * finalScale;

        float textScale = 1.0f;
        float extraSpacing = 0f;

        if (dist <= closestDistanceThreshold) {
            textScale = 1.0f;
            extraSpacing = 2f;
        } else if (dist <= nearDistanceThreshold) {
            float t = (float) ((dist - closestDistanceThreshold) / (nearDistanceThreshold - closestDistanceThreshold));
            textScale = lerp(1.0f, 0.85f, t);
            extraSpacing = lerp(2f, 3f, t);
        } else if (dist <= farDistanceThreshold) {
            float t = (float) ((dist - nearDistanceThreshold) / (farDistanceThreshold - nearDistanceThreshold));
            textScale = lerp(0.85f, 0.7f, t);
            extraSpacing = lerp(4f, 6f, t);
        } else {
            textScale = 0.7f;
            extraSpacing = 6f; // 极远距离时将文字远远推开，防止遮挡微小标记
        }

        gui.pose().pushPose();
        gui.pose().scale(textScale, textScale, 1.0f);

        // 使用倒数计算相对坐标，确保缩放后依然能完美锚定在标记外侧
        float invScale = 1.0f / textScale;
        int nameOffsetY = (int) (((-scaledRadius - extraSpacing) * invScale) - font.lineHeight);
        int distOffsetY = (int) ((scaledRadius + extraSpacing) * invScale);

        drawTextWithBlackOutline(gui, font, name, -st.cachedLabelWidth / 2, nameOffsetY, accentColor, currentAlpha, currentAlpha < 90 || dist > farDistanceThreshold);
        if (marker.isShowDistance()) {
            updateDistanceCache(st, font, dist);
            drawTextWithBlackOutline(gui, font, st.cachedDistanceText, -st.cachedDistanceWidth / 2, distOffsetY, accentColor, currentAlpha, currentAlpha < 90 || dist > farDistanceThreshold);
        }

        gui.pose().popPose(); // 恢复文本缩放
        gui.pose().popPose(); // 恢复整体位移
    }

    private void renderOffscreenMarker(GuiGraphics gui, Font font, MarkerVisualState st, QuestMarkerData marker, float x, float y, int color, double dist, float angle, float progress, float lightX, float lightY, float tier, float time, float activeProgress) {
        int originalAlpha = (color >> 24) & 0xFF;
        int currentAlpha = (int) (originalAlpha * progress);
        if (currentAlpha <= 5) return;
        int accentColor = withAlpha(color, currentAlpha);

        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        float markerScale = styleFloat(marker, "scale", 1.0f, 0.5f, 2.0f);
        float farVisibility = Math.max(0f, Math.min(1f, tier - 1.0f));
        float offscreenScale = markerScale * (1.0f + FAR_OFFSCREEN_SCALE_BOOST * farVisibility);
        gui.pose().scale(offscreenScale, offscreenScale, 1.0f);
        gui.pose().pushPose();

        float ease = 1.0f - (1.0f - progress) * (1.0f - progress);
        float scaleX = ease;
        float scaleY = 1.0f + (1.0f - ease) * 0.8f;
        gui.pose().scale(scaleX, scaleY, 1.0f);

        float rotRad = angle + (float) Math.PI / 2f;
        float rotDeg = (float) Math.toDegrees(rotRad);
        gui.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));

        float cosA = (float) Math.cos(-rotRad);
        float sinA = (float) Math.sin(-rotRad);
        float localLightX = lightX * cosA - lightY * sinA;
        float localLightY = lightX * sinA + lightY * cosA;

        MarkerPointerRenderer.draw(gui, accentColor, time, activeProgress, localLightX, localLightY, tier);
        gui.pose().popPose();

        updateDistanceCache(st, font, dist);
        int distW = st.cachedDistanceWidth;

        // ================= 【优化核心 2】边缘指示器文字排版适配 =================
        float textScale = 1.0f;
        if (dist > nearDistanceThreshold && dist <= farDistanceThreshold) {
            float t = (float) ((dist - nearDistanceThreshold) / (farDistanceThreshold - nearDistanceThreshold));
            textScale = lerp(1.0f, 0.9f, t);
        } else if (dist > farDistanceThreshold) {
            textScale = 0.9f;
        }

        gui.pose().pushPose();
        gui.pose().scale(textScale, textScale, 1.0f);

        float invScale = 1.0f / textScale;
        // 动态计算基础推离半径（文字越小，为了保证不遮挡变大的箭头，稍微推远一点）
        float currentOffset = (30f + (1.0f - textScale) * 15f) * invScale;

        float txRel = -(float) Math.cos(angle) * currentOffset;
        float tyRel = -(float) Math.sin(angle) * currentOffset;

        drawTextWithBlackOutline(gui, font, st.cachedDistanceText, (int) (txRel - distW / 2.0f), (int) (tyRel - font.lineHeight / 2.0f), accentColor, currentAlpha, currentAlpha < 100 || dist > farDistanceThreshold);

        gui.pose().popPose();
        gui.pose().popPose();
    }

    private void drawTextWithBlackOutline(GuiGraphics gui, Font font, String text, int x, int y, int textColor, int alpha, boolean lightweight) {
        if (alpha <= 5) return;
        int outlineColor = (alpha << 24) | 0x000000;
        int mainColor = (alpha << 24) | (textColor & 0xFFFFFF);

        if (lightweight) {
            gui.drawString(font, text, x + 1, y + 1, outlineColor, false);
        } else {
            gui.drawString(font, text, x - 1, y, outlineColor, false);
            gui.drawString(font, text, x + 1, y, outlineColor, false);
            gui.drawString(font, text, x, y - 1, outlineColor, false);
            gui.drawString(font, text, x, y + 1, outlineColor, false);
        }
        gui.drawString(font, text, x, y, mainColor, false);
    }

    private void updateLabelCache(MarkerVisualState st, Font font, String label) {
        if (!label.equals(st.cachedLabel)) {
            st.cachedLabel = label;
            st.cachedLabelWidth = font.width(label);
        }
    }

    private void updateDistanceCache(MarkerVisualState st, Font font, double dist) {
        int meters = Math.max(0, (int) Math.round(dist));
        if (meters != st.cachedDistanceMeters) {
            st.cachedDistanceMeters = meters;
            st.cachedDistanceText = meters + "m";
            st.cachedDistanceWidth = font.width(st.cachedDistanceText);
        }
    }

    private static float styleFloat(QuestMarkerData marker,
                                    String key,
                                    float fallback,
                                    float minimum,
                                    float maximum) {
        String value = marker.getStyleHint(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Math.max(minimum, Math.min(maximum, Float.parseFloat(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public enum DistanceTier {
        CLOSEST(2.0f),
        NEAR(0.0f),
        MEDIUM(1.0f),
        FAR(2.0f);

        private final float targetValue;

        DistanceTier(float targetValue) {
            this.targetValue = targetValue;
        }

        public static DistanceTier getTierForDistance(double distance) {
            if (distance <= closestDistanceThreshold) return CLOSEST;
            if (distance < nearDistanceThreshold) return NEAR;
            if (distance < farDistanceThreshold) return MEDIUM;
            return FAR;
        }

        public float getTargetValue() {
            return targetValue;
        }
    }

    private static class MarkerVisualState {
        float x;
        float y;
        float angle;
        boolean offscreenStable;
        long switchTs;
        float transitionProgress;
        float distanceTier;
        float occlusionAlpha = 1.0f;
        float activeProgress = 0.0f;

        float dynamicDistanceAlpha = 1.0f;
        String cachedLabel = "";
        int cachedLabelWidth = 0;
        int cachedDistanceMeters = Integer.MIN_VALUE;
        String cachedDistanceText = "";
        int cachedDistanceWidth = 0;

        boolean initialized;
        long nextEntityResolveMs;
    }
}
