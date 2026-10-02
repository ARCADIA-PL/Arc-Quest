package org.arcadia.arc_quest.client.hud.quest.tracker;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.gacha.GachaResultRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.JournalDetailParallelPhase;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.JournalDetailCollection;
import org.arcadia.arc_quest.client.quest.tracking.QuestTrackingPresentationState;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingController;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingStore;
import org.arcadia.arc_quest.config.ArcQuestTrackerConfig;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class QuestTrackerPanel implements IGuiOverlay {

    public static final QuestTrackerPanel INSTANCE = new QuestTrackerPanel();

    private final TrackerObjectiveWidget objectiveWidget = new TrackerObjectiveWidget();
    private final TrackerStyleRenderer styleRenderer = new TrackerStyleRenderer();
    private final TrackerCollectionProgressAdapter collectionProgressAdapter = new TrackerCollectionProgressAdapter();
    private final CollectionTrackerRenderer collectionRenderer = new CollectionTrackerRenderer();
    private final TrackerNewQuestIndicator newQuestIndicator = new TrackerNewQuestIndicator();
    private float panelReveal = 0f;
    private float panelSlide = 1f;
    private float currentPanelH = -1f;
    private float currentPanelY = TrackerConstants.MARGIN_TOP;
    private long lastRenderTime = 0;
    private float dt = 0f;
    private String trackedQuestId = null;
    private String trackedPhaseId = null;
    private String targetPhaseId = null;
    private String displayedPhaseId = null;
    private boolean isPhaseTransitioning = false;
    private boolean phaseWipingOut = false;
    private long phaseTransitionStart = 0;
    private long completionDismissStart = 0;
    private List<String> activePhaseOrder = new ArrayList<>();
    private int currentThemeColor = TrackerConstants.COLOR_ACCENT_DEFAULT;

    public void setTrackedQuest(String questId) {
        if (!Objects.equals(trackedQuestId, questId)) {
            trackedQuestId = questId;
            trackedPhaseId = null;
            displayedPhaseId = null;
            targetPhaseId = null;
            currentPanelH = -1f;
            activePhaseOrder.clear();
            resetObjectiveAnimations();
        }
    }

    public void setTrackedFocus(String questId, String phaseId) {
        if (Objects.equals(trackedQuestId, questId) && Objects.equals(trackedPhaseId, phaseId)) return;
        trackedQuestId = questId;
        trackedPhaseId = phaseId;
        displayedPhaseId = null;
        targetPhaseId = null;
        currentPanelH = -1f;
        activePhaseOrder.clear();
        resetObjectiveAnimations();
    }

    public String getTrackedPhaseId() {
        return trackedPhaseId;
    }

    public String getTrackedQuestId() {
        return trackedQuestId;
    }

    public void resetPanelAnimation() {
        panelReveal = 0f;
        panelSlide = 1f;
        currentPanelH = -1f;
        currentPanelY = TrackerConstants.MARGIN_TOP;
    }

    public void clearClientSession() {
        setTrackedQuest(null);
        resetObjectiveAnimations();
        resetPanelAnimation();
        lastRenderTime = 0;
        isPhaseTransitioning = false;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        syncTrackedFocus();
        render(graphics, screenWidth, screenHeight, partialTick);
    }

    private void syncTrackedFocus() {
        String questId = ClientQuestCache.INSTANCE.isFullSyncApplied()
                ? ClientQuestTrackingStore.INSTANCE.trackedQuestId() : null;
        String phaseId = questId == null ? null : ClientQuestTrackingController.INSTANCE.trackedPhaseId();
        if (questId != null && phaseId != null) setTrackedFocus(questId, phaseId);
        else if (!Objects.equals(trackedQuestId, questId) || trackedPhaseId != null) {
            setTrackedFocus(questId, null);
        }
    }

    public void render(GuiGraphics g, int screenWidth, int screenHeight, float partialTick) {
        renderPanel(g, screenWidth, screenHeight, partialTick, ArcQuestTrackerConfig.layout(),
                ArcQuestTrackerConfig.style(), false);
    }

    /** Call on a separate panel instance: editing never drives the live HUD's animation state. */
    public TrackerLayout.Frame renderPreview(GuiGraphics g, int screenWidth, int screenHeight,
                                              float partialTick, TrackerLayout.Settings settings) {
        return renderPreview(g, screenWidth, screenHeight, partialTick, settings, ArcQuestTrackerConfig.style());
    }

    public TrackerLayout.Frame renderPreview(GuiGraphics g, int screenWidth, int screenHeight,
                                              float partialTick, TrackerLayout.Settings settings, TrackerStyle style) {
        syncTrackedFocus();
        if (style == null) style = TrackerStyle.CLASSIC;
        TrackerLayout.Frame frame = renderPanel(g, screenWidth, screenHeight, partialTick, settings, style, true);
        return frame != null ? frame : renderExample(g, screenWidth, screenHeight, settings, style);
    }

    private TrackerLayout.Frame renderPanel(GuiGraphics g, int screenWidth, int screenHeight,
                                            float partialTick, TrackerLayout.Settings settings,
                                            TrackerStyle style, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        if (settings == null) settings = TrackerLayout.DEFAULT;
        if (style == null) style = TrackerStyle.CLASSIC;
        if (!preview && (mc.player == null || mc.options.hideGui)) return null;

        boolean isBlockingScreen = !preview && (mc.screen != null ||
                QuestSplashRenderer.isActive() ||
                GachaResultRenderer.INSTANCE.isActive());

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = preview ? 1f : Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        QuestRuntimeData tracked = resolveTrackedQuest();
        if (tracked == null || (preview && tracked.getState() != QuestState.ACTIVE)) return null;

        String questId = tracked.getQuestId();
        currentThemeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, TrackerConstants.COLOR_ACCENT_DEFAULT);

        QuestDefinition def = QuestRegistry.get(ResourceLocation.tryParse(questId));
        if (def == null) return null;

        boolean modernCollection = JournalDetailCollection.usesSheets(def);
        boolean legacyCollection = def.isCollectionQuest() && !modernCollection;

        syncActivePhaseOrder(tracked, def);
        boolean isActive = tracked.getState() == QuestState.ACTIVE;
        boolean shouldShow = isActive && !isBlockingScreen;
        handleDismiss(tracked, now, isActive);

        String collectionPhaseId = modernCollection ? resolvePreferredPhaseId(tracked, def) : "";
        PhaseDefinition collectionPhase = modernCollection ? def.getPhase(collectionPhaseId) : null;
        boolean collectionView = modernCollection && collectionPhase != null && collectionPhase.hasCollectionSheet();
        CollectionTrackerRenderer.Snapshot collectionSnapshot = collectionView
                ? collectionRenderer.snapshot(def, tracked, collectionPhaseId, now, preview) : null;
        if (collectionSnapshot != null && collectionSnapshot.hide()) shouldShow = false;

        if (isActive) {
            String actualPhaseId = legacyCollection ? collectionProgressAdapter.phaseId() : resolvePreferredPhaseId(tracked, def);
            if (def.isCollectionQuest()) {
                displayedPhaseId = actualPhaseId;
                targetPhaseId = actualPhaseId;
                isPhaseTransitioning = false;
            } else if (shouldShow) {
                if (displayedPhaseId == null) {
                    displayedPhaseId = actualPhaseId;
                    targetPhaseId = actualPhaseId;
                } else if (!Objects.equals(actualPhaseId, targetPhaseId)) {
                    targetPhaseId = actualPhaseId;
                    if (panelReveal > 0.5f) {
                        isPhaseTransitioning = true;
                        phaseWipingOut = true;
                        phaseTransitionStart = now;
                    } else {
                        displayedPhaseId = actualPhaseId;
                        resetObjectiveAnimations();
                    }
                }
            }
        } else {
            displayedPhaseId = null;
            targetPhaseId = null;
            isPhaseTransitioning = false;
            activePhaseOrder.clear();
        }

        if (preview) {
            displayedPhaseId = targetPhaseId;
            isPhaseTransitioning = false;
            panelReveal = 1f;
            panelSlide = 0f;
        }
        float wipeReveal = 1f, wipeDrift = 0f, wipeAlpha = 1f;
        if (isPhaseTransitioning) {
            long elapsed = now - phaseTransitionStart;
            if (phaseWipingOut) {
                float t = elapsed / TrackerConstants.TIME_WIPE_OUT;
                if (t >= 1f) {
                    t = 1f;
                    phaseWipingOut = false;
                    displayedPhaseId = targetPhaseId;
                    resetObjectiveAnimations();
                    phaseTransitionStart = now;
                }
                float ease = (float) Math.pow(t, 4.0);
                wipeReveal = 1f - ease;
                wipeDrift = ease * 30f;
                wipeAlpha = 1f - ease;
            } else {
                float t = elapsed / TrackerConstants.TIME_WIPE_IN;
                if (t >= 1f) {
                    t = 1f;
                    isPhaseTransitioning = false;
                }
                float ease = (float) (1.0 - Math.pow(1.0 - t, 5.0));
                wipeReveal = ease;
                wipeDrift = -(1f - ease) * 30f;
                wipeAlpha = ease;
            }
        }

        panelReveal = TrackerConstants.lerp(panelReveal, shouldShow ? 1f : 0f, 0.15f, dt);
        if (completionDismissStart == 0)
            panelSlide = TrackerConstants.lerp(panelSlide, shouldShow ? 0f : 1f, 0.15f, dt);

        if (panelReveal < 0.01f && !shouldShow) return null;

        PhaseDefinition phase = legacyCollection ? null : resolveDisplayedPhase(def, tracked);
        if (!legacyCollection && phase == null) return null;

        List<ObjectiveEntry> objectives = legacyCollection ? collectionProgressAdapter.buildObjectives(def, tracked, trackedPhaseId) : phase.getObjectives();
        Font font = mc.font;
        int indicatorExtraHeight = newQuestIndicator.additionalHeight(questId);

        double guiScale = Math.max(1.0, mc.getWindow().getGuiScale());
        int panelWidth = TrackerLayout.contentWidth(screenWidth, guiScale);

        boolean showPhaseLanes = !legacyCollection && activePhaseOrder.size() > 1;
        String layoutPhaseId = legacyCollection ? collectionProgressAdapter.phaseId() : displayedPhaseId;
        TrackerStyleRenderer.Snapshot styledSnapshot = style == TrackerStyle.CLASSIC || collectionView ? null
                : styleRenderer.snapshot(tracked, def, layoutPhaseId, objectives, activePhaseOrder, now);
        int targetH;
        if (collectionView) targetH = collectionRenderer.height(font, panelWidth, collectionSnapshot);
        else if (style == TrackerStyle.CLASSIC) {
            targetH = TrackerConstants.PADDING + TrackerConstants.TITLE_HEIGHT + TrackerConstants.GAP_AFTER_TITLE;
            if (showPhaseLanes) targetH += TrackerParallelWidget.computeHeight(activePhaseOrder);
            else if (!legacyCollection) {
                targetH += TrackerTitleWidget.computePhaseNameHeight(tracked, displayedPhaseId, font, panelWidth);
            }
            targetH += legacyCollection ? 0 : TrackerTitleWidget.computeDescriptionHeight(phase, font, panelWidth);
            targetH += objectiveWidget.computeHeight(font, tracked, layoutPhaseId, objectives, panelWidth);
            targetH += TrackerConstants.PADDING;
        } else {
            targetH = styleRenderer.height(font, panelWidth, styledSnapshot, style);
        }
        targetH += indicatorExtraHeight;

        if (currentPanelH < 0 || preview) currentPanelH = targetH;
        currentPanelH = TrackerConstants.lerp(currentPanelH, targetH, 0.15f, dt);
        int panelH = (int) currentPanelH;
        // Notifications now occupy the independent left HUD slot.
        TrackerLayout.Frame frame = TrackerLayout.resolve(screenWidth, screenHeight, guiScale, panelH,
                settings, 0);
        currentPanelY = (float) frame.y();
        float uiScale = (float) frame.uiScale();
        // Slide out towards the nearest edge, including when users move the tracker to the left.
        double slideDistance = settings.x() < 0.5 ? -frame.right() - 2 : screenWidth - frame.x() + 2;
        double screenX = frame.x() + panelSlide * slideDistance;
        int panelX = 0, panelY = 0;

        // 完美 Scissor 计算（完全对齐 Journal 逻辑）
        float currentW = Math.max((float) TrackerConstants.ACCENT_WIDTH + 1f, panelWidth * wipeReveal);
        int scX1 = Math.min(screenWidth, Math.max(0, (int) Math.floor(screenX - 2 * uiScale)));
        int scY1 = Math.min(screenHeight, Math.max(0, (int) Math.floor(currentPanelY - 2 * uiScale)));
        int scX2 = Math.max(scX1,
                Math.min(screenWidth, (int) Math.ceil(screenX + currentW * uiScale)));
        int scY2 = Math.max(scY1,
                Math.min(screenHeight, (int) Math.ceil(currentPanelY + panelH * uiScale)));

        // 1. 在正确空间进行裁剪
        g.enableScissor(scX1, scY1, scX2, scY2);

        // 2. 推入矩阵，进行统一缩放
        g.pose().pushPose();
        g.pose().translate(screenX, currentPanelY, 0);
        g.pose().scale(uiScale, uiScale, 1.0f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        if (collectionView) {
            collectionRenderer.render(g, font, tracked, def, collectionSnapshot, panelWidth, panelH,
                    style, currentThemeColor, panelReveal * wipeAlpha);
        } else if (style == TrackerStyle.CLASSIC) {
            int bgAlpha = (int) (0x55 * panelReveal);
            int accentAlpha = (int) (0xFF * panelReveal);
            HudAnimUtil.drawAccentPanel(g, panelX, panelY, panelWidth, panelH, bgAlpha << 24,
                    (accentAlpha << 24) | (currentThemeColor & 0x00FFFFFF), TrackerConstants.ACCENT_WIDTH);

            int textX = panelX + TrackerConstants.ACCENT_WIDTH + TrackerConstants.PADDING;
            int textY = panelY + TrackerConstants.PADDING;
            TrackerTitleWidget.renderTitle(g, tracked, textX, textY, panelReveal, wipeAlpha, font, panelWidth, 0);
            textY += TrackerConstants.TITLE_HEIGHT + TrackerConstants.GAP_AFTER_TITLE;
            if (showPhaseLanes) {
                textY = TrackerParallelWidget.render(g, font, tracked, def, activePhaseOrder,
                        displayedPhaseId, currentThemeColor, textX + (int) wipeDrift, textY,
                        panelReveal, wipeAlpha, panelWidth);
            } else if (!legacyCollection) {
                textY = TrackerTitleWidget.renderPhaseName(g, tracked, displayedPhaseId, currentThemeColor,
                        textX + (int) wipeDrift, textY, panelReveal, wipeAlpha, font, panelWidth);
            }
            if (!legacyCollection) {
                textY = TrackerTitleWidget.renderDescription(g, phase, textX + (int) wipeDrift,
                        textY, panelReveal, wipeAlpha, font, panelWidth);
            }
            objectiveWidget.render(g, font, tracked, layoutPhaseId, objectives, currentThemeColor, dt,
                    panelReveal, wipeAlpha, wipeDrift, panelX, textX, textY, panelWidth);
        } else {
            styleRenderer.render(g, font, panelWidth, panelH, styledSnapshot, style, tracked,
                    currentThemeColor, panelReveal, wipeAlpha, wipeDrift);
        }
        newQuestIndicator.render(g, font, panelX, panelY, panelH, panelWidth,
                questId, panelReveal, now);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        g.pose().popPose();
        // 3. 完美闭环
        g.disableScissor();
        return frame;
    }

    /** A read-only example keeps layout editing available from the title screen as well. */
    private TrackerLayout.Frame renderExample(GuiGraphics graphics, int screenWidth, int screenHeight,
                                               TrackerLayout.Settings settings, TrackerStyle style) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        double guiScale = mc.getWindow().getGuiScale();
        int panelWidth = TrackerLayout.contentWidth(screenWidth, guiScale);
        if (style != TrackerStyle.CLASSIC) {
            TrackerStyleRenderer.Snapshot snapshot = styleRenderer.example();
            int panelHeight = styleRenderer.height(font, panelWidth, snapshot, style);
            TrackerLayout.Frame frame = TrackerLayout.resolve(screenWidth, screenHeight, guiScale, panelHeight, settings, 0);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(frame.x(), frame.y(), 0);
                graphics.pose().scale((float) frame.uiScale(), (float) frame.uiScale(), 1);
                styleRenderer.render(graphics, font, panelWidth, panelHeight, snapshot, style, null,
                        TrackerConstants.COLOR_ACCENT_DEFAULT, 1, 1, 0);
            } finally {
                graphics.pose().popPose();
            }
            return frame;
        }
        int textX = TrackerConstants.ACCENT_WIDTH + TrackerConstants.PADDING;
        int contentWidth = panelWidth - textX - TrackerConstants.PADDING;
        String[] counts = {"3/8", "0/1"};
        List<List<FormattedCharSequence>> rows = List.of(
                font.split(Component.translatable("gui.arc_quest.tracker_layout.preview.objective1"),
                        contentWidth - font.width(counts[0]) - 8),
                font.split(Component.translatable("gui.arc_quest.tracker_layout.preview.objective2"),
                        contentWidth - font.width(counts[1]) - 8));
        int headerHeight = TrackerConstants.PADDING + TrackerConstants.TITLE_HEIGHT
                + TrackerConstants.GAP_AFTER_TITLE + font.lineHeight + 4;
        int panelHeight = headerHeight + TrackerConstants.PADDING;
        for (var row : rows) panelHeight += row.size() * font.lineHeight + TrackerConstants.PROGRESS_BAR_H + 6;
        TrackerLayout.Frame frame = TrackerLayout.resolve(screenWidth, screenHeight, guiScale, panelHeight, settings, 0);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(frame.x(), frame.y(), 0);
            graphics.pose().scale((float) frame.uiScale(), (float) frame.uiScale(), 1);
            HudAnimUtil.drawAccentPanel(graphics, 0, 0, panelWidth, panelHeight, 0x55000000,
                    TrackerConstants.COLOR_ACCENT_DEFAULT, TrackerConstants.ACCENT_WIDTH);
            graphics.drawString(font, StyledTextUtil.fitSingleLine(font,
                            Component.translatable("gui.arc_quest.tracker_layout.preview.title"), contentWidth),
                    textX, TrackerConstants.PADDING, 0xFFFFFFFF, false);
            int textY = TrackerConstants.PADDING + TrackerConstants.TITLE_HEIGHT + TrackerConstants.GAP_AFTER_TITLE;
            graphics.drawString(font, StyledTextUtil.fitSingleLine(font,
                            Component.translatable("gui.arc_quest.tracker_layout.preview.phase"), contentWidth),
                    textX, textY, TrackerConstants.COLOR_ACCENT_DEFAULT, false);
            textY = headerHeight;
            for (int i = 0; i < rows.size(); i++) {
                graphics.drawString(font, counts[i], panelWidth - TrackerConstants.PADDING - font.width(counts[i]),
                        textY, 0xFFBBBBBB, false);
                for (var line : rows.get(i)) {
                    graphics.drawString(font, line, textX, textY, 0xFFDDDDDD, false);
                    textY += font.lineHeight;
                }
                graphics.fill(textX, textY + 1, textX + contentWidth,
                        textY + 1 + TrackerConstants.PROGRESS_BAR_H, 0x44FFFFFF);
                if (i == 0) graphics.fill(textX, textY + 1, textX + contentWidth * 3 / 8,
                        textY + 1 + TrackerConstants.PROGRESS_BAR_H, TrackerConstants.COLOR_ACCENT_DEFAULT);
                textY += TrackerConstants.PROGRESS_BAR_H + 6;
            }
        } finally {
            graphics.pose().popPose();
        }
        return frame;
    }

    private void syncActivePhaseOrder(QuestRuntimeData tracked, QuestDefinition def) {
        String questId = tracked.getQuestId();
        List<String> customOrder = JournalDetailParallelPhase.getCustomOrder(questId);
        List<String> next = new ArrayList<>();

        if (customOrder != null && !customOrder.isEmpty()) {
            for (String pid : customOrder) {
                if (tracked.isPhaseActive(pid) && def.getPhase(pid) != null) next.add(pid);
            }
            for (String pid : tracked.getActivePhaseIds()) {
                if (!next.contains(pid) && def.getPhase(pid) != null) next.add(pid);
            }
        } else {
            if (def.isCollectionQuest() && !JournalDetailCollection.usesSheets(def)) {
                for (String pid : def.getPhaseIds()) {
                    PhaseDefinition phase = def.getPhase(pid);
                    if (phase != null && phase.getCollectionEntryConfig() != null) next.add(pid);
                }
            } else {
                for (String pid : def.getPhaseIds()) if (tracked.isPhaseActive(pid)) next.add(pid);
            }
            if (next.isEmpty()) next.addAll(tracked.getActivePhaseIds());
        }
        activePhaseOrder = next;
    }

    private String resolvePreferredPhaseId(QuestRuntimeData tracked, QuestDefinition def) {
        if (trackedPhaseId != null && !trackedPhaseId.isEmpty()) {
            PhaseDefinition trackedPhase = def.getPhase(trackedPhaseId);
            if (trackedPhase != null && (tracked.isPhaseActive(trackedPhaseId) || trackedPhase.getCollectionEntryConfig() != null
                    || trackedPhase.hasCollectionSheet() && (tracked.isPhasePendingManualAdvance(trackedPhaseId)
                    || QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(tracked.getQuestId()) != null)))
                return trackedPhaseId;
        }
        String current = tracked.getCurrentPhaseId();
        if (current != null && !current.isEmpty() && tracked.isPhaseActive(current) && def.getPhase(current) != null)
            return current;
        for (String pid : activePhaseOrder) if (def.getPhase(pid) != null) return pid;
        return "";
    }

    private PhaseDefinition resolveDisplayedPhase(QuestDefinition def, QuestRuntimeData tracked) {
        if (displayedPhaseId != null && !displayedPhaseId.isEmpty()) {
            PhaseDefinition p = def.getPhase(displayedPhaseId);
            if (p != null) return p;
        }
        String fallback = resolvePreferredPhaseId(tracked, def);
        if (!fallback.isEmpty()) {
            displayedPhaseId = fallback;
            return def.getPhase(fallback);
        }
        return null;
    }

    private QuestRuntimeData resolveTrackedQuest() {
        if (trackedQuestId == null || trackedQuestId.isEmpty()) return null;
        QuestRuntimeData data = ClientQuestCache.INSTANCE.getActiveQuest(trackedQuestId);
        if (data != null) return data;
        trackedQuestId = null;
        trackedPhaseId = null;
        displayedPhaseId = null;
        targetPhaseId = null;
        currentPanelH = -1f;
        activePhaseOrder.clear();
        resetObjectiveAnimations();
        return null;
    }

    private void handleDismiss(QuestRuntimeData tracked, long now, boolean shouldShow) {
        if (tracked != null && (tracked.getState() == QuestState.COMPLETED || tracked.getState() == QuestState.FAILED)) {
            if (completionDismissStart == 0) completionDismissStart = now;
            float elapsed = now - completionDismissStart;
            if (elapsed >= TrackerConstants.DISMISS_DELAY)
                panelSlide = TrackerConstants.easeInCubic(Math.min(1f, (elapsed - TrackerConstants.DISMISS_DELAY) / TrackerConstants.DISMISS_SLIDE_TIME));
        } else completionDismissStart = 0;
    }

    private void resetObjectiveAnimations() {
        objectiveWidget.reset();
        styleRenderer.reset();
        collectionProgressAdapter.reset();
        collectionRenderer.reset();
        panelSlide = 1f;
        completionDismissStart = 0;
    }

}
