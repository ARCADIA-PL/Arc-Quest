package org.arcadia.arc_quest.client.hud.quest.tracker;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackerObjectiveWidget {

    private final Map<String, ObjectiveTextCache> textCache = new HashMap<>();
    private final Component completePrefix = Component.translatable("arc_quest.hud.objective_complete_prefix");
    private final Component activePrefix = Component.translatable("arc_quest.hud.objective_active_prefix");
    private float[] objReveal = new float[0];
    private int[] lastKnownProgress = new int[0];
    private float[] objPulse = new float[0];
    private boolean[] objCompletedFlag = new boolean[0];
    private float[] objCompleteAnim = new float[0];
    private float[] animProgressRatio = new float[0];

    public void reset() {
        objReveal = new float[0];
        objPulse = new float[0];
        lastKnownProgress = new int[0];
        objCompletedFlag = new boolean[0];
        objCompleteAnim = new float[0];
        animProgressRatio = new float[0];
        textCache.clear();
    }

    private void ensureArraySize(int size) {
        if (objReveal.length != size) {
            float[] nr = new float[size], np = new float[size], nc = new float[size], nRatio = new float[size];
            int[] ni = new int[size];
            boolean[] nb = new boolean[size];
            Arrays.fill(ni, -1);

            int c = Math.min(objReveal.length, size);
            System.arraycopy(objReveal, 0, nr, 0, c);
            System.arraycopy(objPulse, 0, np, 0, c);
            System.arraycopy(lastKnownProgress, 0, ni, 0, Math.min(lastKnownProgress.length, size));
            System.arraycopy(objCompletedFlag, 0, nb, 0, Math.min(objCompletedFlag.length, size));
            System.arraycopy(objCompleteAnim, 0, nc, 0, Math.min(objCompleteAnim.length, size));
            System.arraycopy(animProgressRatio, 0, nRatio, 0, Math.min(animProgressRatio.length, size));

            objReveal = nr;
            objPulse = np;
            lastKnownProgress = ni;
            objCompletedFlag = nb;
            objCompleteAnim = nc;
            animProgressRatio = nRatio;
        }
    }

    public void render(GuiGraphics g, Font font, QuestRuntimeData tracked, String displayedPhaseId, List<ObjectiveEntry> objectives, int themeColor, float dt, float alpha, float wipeAlpha, float wipeDrift, int panelX, int textX, int textY) {
        int objCount = objectives.size();
        ensureArraySize(objCount);
        String phaseId = displayedPhaseId != null ? displayedPhaseId : tracked.getCurrentPhaseId();

        for (int i = 0; i < objCount; i++) {
            ObjectiveEntry obj = objectives.get(i);
            int progress = tracked.getObjectiveProgress(phaseId, i);
            int required = obj.getRequiredCount();
            boolean complete = progress >= required;

            objReveal[i] = TrackerConstants.lerp(objReveal[i], 1f, 0.12f + i * 0.02f, dt);
            float objAlpha = alpha * wipeAlpha * TrackerConstants.easeOutCubic(Math.min(1f, objReveal[i]));

            if (progress != lastKnownProgress[i] && lastKnownProgress[i] >= 0) objPulse[i] = 1f;
            lastKnownProgress[i] = progress;
            objPulse[i] = TrackerConstants.lerp(objPulse[i], 0f, 0.12f, dt);

            boolean was = objCompletedFlag[i];
            objCompletedFlag[i] = complete;
            if (!was && complete) objCompleteAnim[i] = 1f;
            objCompleteAnim[i] = TrackerConstants.lerp(objCompleteAnim[i], 0f, 0.08f, dt);

            float targetRatio = required > 0 ? (float) progress / required : 0f;
            float diff = targetRatio - animProgressRatio[i];
            if (Math.abs(diff) > 0.001f) {
                float rate = targetRatio > animProgressRatio[i] ? 12.0f : 15.0f;
                float lerpFactor = 1.0f - (float) Math.exp(-rate * dt);
                animProgressRatio[i] += diff * lerpFactor;
            } else {
                animProgressRatio[i] = targetRatio;
            }
            float displayRatio = animProgressRatio[i];

            if (objAlpha < 0.02f) {
                textY += TrackerConstants.OBJ_ROW_HEIGHT + TrackerConstants.PROGRESS_BAR_H + 6;
                continue;
            }

            float rowSlide = (1f - TrackerConstants.easeOutCubic(Math.min(1f, objReveal[i]))) * 30f;
            int rowX = textX + (int) rowSlide + (int) wipeDrift;
            int aInt = (int) (255 * objAlpha);

            float cScale = 1f;
            int cGlow = 0;
            if (objCompleteAnim[i] > 0.05f) {
                float t = objCompleteAnim[i];
                cScale = 1f + 0.15f * TrackerConstants.easeOutCubic(t) * (float) Math.sin(t * Math.PI);
                cGlow = (int) (255 * t * objAlpha);
            }

            ObjectiveTextCache cachedText = getTextCache(obj, progress, required, font);
            String objText = complete ? cachedText.completeText : cachedText.activeText;
            String progressText = cachedText.progressText;

            int textColor = complete ? HudAnimUtil.withAlpha(0x88FF88, aInt) : HudAnimUtil.withAlpha(0xCCCCCC, aInt);
            if (objPulse[i] > 0.05f) {
                textColor = HudAnimUtil.lerpColor(
                        textColor,
                        HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * objPulse[i] * objAlpha)),
                        objPulse[i]
                );
            }
            if (cGlow > 0) {
                textColor = HudAnimUtil.lerpColor(
                        textColor,
                        HudAnimUtil.withAlpha(0xFFFFFF, cGlow),
                        objCompleteAnim[i] * 0.7f
                );
            }

            int numW = (int) (cachedText.progressWidth * 0.8f);
            int numX = panelX + TrackerConstants.PANEL_WIDTH - TrackerConstants.PADDING - numW + (int) wipeDrift;

            int maxObjTextWidth = (int) ((numX - rowX - 8) / 0.85f);
            String safeObjText = getSafeObjectiveText(cachedText, objText, complete, Math.max(10, maxObjTextWidth), font);

            g.pose().pushPose();
            g.pose().translate(rowX, textY, 0);
            g.pose().scale(0.85f * cScale, 0.85f * cScale, 1f);
            g.drawString(font, safeObjText, 0, 0, textColor, true);
            g.pose().popPose();

            g.pose().pushPose();
            g.pose().translate(numX, textY + 1, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, progressText, 0, 0, textColor, true);
            g.pose().popPose();

            textY += TrackerConstants.OBJ_ROW_HEIGHT;

            int barW = TrackerConstants.PANEL_WIDTH - TrackerConstants.ACCENT_WIDTH - TrackerConstants.PADDING * 2 - (int) rowSlide;
            int fillW = (int) (barW * displayRatio);

            int bgC = HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x33 * objAlpha));
            int fgC = complete
                    ? HudAnimUtil.withAlpha(0x66FF66, (int) (0xCC * objAlpha))
                    : HudAnimUtil.withAlpha(themeColor, (int) (0xCC * objAlpha));
            int tipC = HudAnimUtil.withAlpha(0xFFFFFF, (int) (0xFF * objAlpha));

            if (objPulse[i] > 0.05f) {
                fgC = HudAnimUtil.lerpColor(
                        fgC,
                        HudAnimUtil.withAlpha(0xFFFFFF, (int) (200 * objPulse[i] * objAlpha)),
                        objPulse[i] * 0.5f
                );
            }

            RenderSystem.enableBlend();
            g.fill(rowX, textY, rowX + barW, textY + TrackerConstants.PROGRESS_BAR_H, bgC);
            if (fillW > 0) {
                g.fill(rowX, textY, rowX + fillW, textY + TrackerConstants.PROGRESS_BAR_H, fgC);
                g.fill(rowX + fillW - 2, textY - 1, rowX + fillW, textY + TrackerConstants.PROGRESS_BAR_H + 1, tipC);
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            textY += TrackerConstants.PROGRESS_BAR_H + 6;
        }
    }

    private ObjectiveTextCache getTextCache(ObjectiveEntry obj, int progress, int required, Font font) {
        String key = obj.getDisplayText().getContents().toString();
        ObjectiveTextCache cache = textCache.computeIfAbsent(key, k -> new ObjectiveTextCache());
        String resolvedBody = obj.getDisplayText().getString();
        if (!resolvedBody.equals(cache.resolvedBody)) {
            cache.resolvedBody = resolvedBody;
            cache.activeText = activePrefix.getString() + resolvedBody;
            cache.completeText = completePrefix.getString() + resolvedBody;
            cache.lastActiveWidth = Integer.MIN_VALUE;
            cache.lastCompleteWidth = Integer.MIN_VALUE;
            cache.lastActiveSafe = null;
            cache.lastCompleteSafe = null;
        }
        if (cache.lastProgress != progress || cache.lastRequired != required) {
            cache.lastProgress = progress;
            cache.lastRequired = required;
            cache.progressText = progress + "/" + required;
            cache.progressWidth = font.width(cache.progressText);
        }
        return cache;
    }

    private String getSafeObjectiveText(ObjectiveTextCache cache, String text, boolean complete, int maxWidth, Font font) {
        if (complete) {
            if (cache.lastCompleteWidth != maxWidth) {
                cache.lastCompleteSafe = font.plainSubstrByWidth(text, maxWidth);
                cache.lastCompleteWidth = maxWidth;
            }
            return cache.lastCompleteSafe;
        }
        if (cache.lastActiveWidth != maxWidth) {
            cache.lastActiveSafe = font.plainSubstrByWidth(text, maxWidth);
            cache.lastActiveWidth = maxWidth;
        }
        return cache.lastActiveSafe;
    }

    private static class ObjectiveTextCache {
        String resolvedBody = "";
        String activeText = "";
        String completeText = "";
        String lastActiveSafe;
        String lastCompleteSafe;
        int lastActiveWidth = Integer.MIN_VALUE;
        int lastCompleteWidth = Integer.MIN_VALUE;
        int lastProgress = Integer.MIN_VALUE;
        int lastRequired = Integer.MIN_VALUE;
        String progressText = "";
        int progressWidth = 0;
    }
}