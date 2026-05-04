package org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.mutil.screen.ArcScaleResolver;
import org.arcadia.arc_quest.mutil.theme.ArcPanelChrome;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.QuestHudBlockState;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.QuestHudSelectors;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.mutil.core.ArcGuiTickContext;
import org.arcadia.arc_quest.mutil.presenter.ArcHudPresenter;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.capability.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import javax.annotation.Nullable;
import java.util.Objects;

public class QuestTrackerPresenter implements ArcHudPresenter {
    private final QuestTrackerViewModel model = new QuestTrackerViewModel();
    private String trackedQuestId;
    private String trackedPhaseId;
    private String targetPhaseId;
    private String displayedPhaseId;
    private boolean phaseTransitioning;
    private boolean phaseWipingOut;
    private long phaseTransitionStart;
    private long completionDismissStart;
    private int overlayPressure;

    public QuestTrackerViewModel model() {
        return model;
    }

    public void setTrackedQuest(@Nullable String questId) {
        if (Objects.equals(trackedQuestId, questId)) return;
        trackedQuestId = questId;
        trackedPhaseId = null;
        displayedPhaseId = null;
        targetPhaseId = null;
        phaseTransitioning = false;
        phaseWipingOut = false;
        completionDismissStart = 0;
        model.panelSlide = 1f;
        model.markDirty();
    }

    public void setTrackedFocus(@Nullable String questId, @Nullable String phaseId) {
        trackedQuestId = questId;
        trackedPhaseId = phaseId;
        displayedPhaseId = null;
        targetPhaseId = null;
        phaseTransitioning = false;
        phaseWipingOut = false;
        completionDismissStart = 0;
        model.panelSlide = 1f;
        model.markDirty();
    }

    @Nullable
    public String getTrackedQuestId() {
        return trackedQuestId;
    }

    @Nullable
    public String getTrackedPhaseId() {
        return trackedPhaseId;
    }

    public void setOverlayPressure(int pixels) {
        int clamped = Math.max(0, Math.min(48, pixels));
        if (overlayPressure == clamped) return;
        overlayPressure = clamped;
        model.markDirty();
    }

    @Override
    public void tick(ArcGuiTickContext context) {
        tick(context, QuestHudBlockState.current());
    }

    public void tick(ArcGuiTickContext context, QuestHudBlockState blockState) {
        if (blockState.hideAll()) {
            fade(false, context.deltaTime());
            return;
        }

        QuestHudSelectors.ResolvedTrackedQuest resolved = QuestHudSelectors.resolveTrackedQuest(trackedQuestId);
        if (resolved.requestedInvalidated()) {
            trackedQuestId = null;
            trackedPhaseId = null;
            displayedPhaseId = null;
        }

        QuestRuntimeData tracked = resolved.data();
        if (tracked == null) {
            fade(false, context.deltaTime());
            model.markDirty();
            return;
        }
        if (trackedQuestId == null) trackedQuestId = tracked.getQuestId();

        QuestDefinition definition = QuestRegistry.get(ResourceLocation.tryParse(tracked.getQuestId()));
        if (definition == null) {
            fade(false, context.deltaTime());
            return;
        }

        boolean shouldShow = tracked.getState() == QuestState.ACTIVE && !blockState.hideTracker();
        handleDismiss(tracked, context.nowMs(), tracked.getState() == QuestState.ACTIVE);
        updatePhaseState(tracked, definition, shouldShow, context.nowMs());
        updateWipeState(context.nowMs());
        fade(shouldShow, context.deltaTime());
        rebuildModel(tracked, definition, context);
    }

    private void fade(boolean shouldShow, float deltaTime) {
        model.panelReveal = ArcQuestTrackerConstants.lerp(model.panelReveal, shouldShow ? 1f : 0f, 0.15f, deltaTime);
        if (completionDismissStart == 0) model.panelSlide = ArcQuestTrackerConstants.lerp(model.panelSlide, shouldShow ? 0f : 1f, 0.15f, deltaTime);
        boolean visible = model.panelReveal >= 0.01f || shouldShow;
        if (model.visible != visible) {
            model.visible = visible;
            model.markDirty();
        }
    }

    private void updatePhaseState(QuestRuntimeData tracked, QuestDefinition definition, boolean shouldShow, long now) {
        if (tracked.getState() != QuestState.ACTIVE) {
            displayedPhaseId = null;
            targetPhaseId = null;
            phaseTransitioning = false;
            return;
        }
        String actualPhaseId = resolvePhaseId(tracked, definition);
        if (!shouldShow) return;
        if (displayedPhaseId == null || displayedPhaseId.isEmpty()) {
            displayedPhaseId = actualPhaseId;
            targetPhaseId = actualPhaseId;
            model.markDirty();
        } else if (!Objects.equals(actualPhaseId, targetPhaseId)) {
            targetPhaseId = actualPhaseId;
            if (model.panelReveal > 0.5f) {
                phaseTransitioning = true;
                phaseWipingOut = true;
                phaseTransitionStart = now;
            } else {
                displayedPhaseId = actualPhaseId;
            }
            model.markDirty();
        }
    }

    private void updateWipeState(long now) {
        model.wipeReveal = 1f;
        model.wipeDrift = 0f;
        model.wipeAlpha = 1f;
        if (!phaseTransitioning) return;
        long elapsed = now - phaseTransitionStart;
        if (phaseWipingOut) {
            float t = Math.min(1f, elapsed / ArcQuestTrackerConstants.TIME_WIPE_OUT);
            float ease = (float) Math.pow(t, 4.0);
            model.wipeReveal = 1f - ease;
            model.wipeDrift = ease * 30f;
            model.wipeAlpha = 1f - ease;
            if (t >= 1f) {
                phaseWipingOut = false;
                displayedPhaseId = targetPhaseId;
                phaseTransitionStart = now;
                model.markDirty();
            }
        } else {
            float t = Math.min(1f, elapsed / ArcQuestTrackerConstants.TIME_WIPE_IN);
            float ease = (float) (1.0 - Math.pow(1.0 - t, 5.0));
            model.wipeReveal = ease;
            model.wipeDrift = -(1f - ease) * 30f;
            model.wipeAlpha = ease;
            if (t >= 1f) phaseTransitioning = false;
        }
    }

    private void handleDismiss(QuestRuntimeData tracked, long now, boolean active) {
        if (tracked != null && (tracked.getState() == QuestState.COMPLETED || tracked.getState() == QuestState.FAILED)) {
            if (completionDismissStart == 0) completionDismissStart = now;
            float elapsed = now - completionDismissStart;
            if (elapsed >= ArcQuestTrackerConstants.DISMISS_DELAY) {
                model.panelSlide = ArcQuestTrackerConstants.easeInCubic(Math.min(1f, (elapsed - ArcQuestTrackerConstants.DISMISS_DELAY) / ArcQuestTrackerConstants.DISMISS_SLIDE_TIME));
            }
        } else if (active) {
            completionDismissStart = 0;
        }
    }

    private void rebuildModel(QuestRuntimeData tracked, QuestDefinition definition, ArcGuiTickContext context) {
        String phaseId = displayedPhaseId != null && !displayedPhaseId.isEmpty() ? displayedPhaseId : resolvePhaseId(tracked, definition);
        PhaseDefinition phase = phaseId.isEmpty() ? null : definition.getPhase(phaseId);
        if (phase == null) return;

        model.questId = tracked.getQuestId();
        model.questTitle = ClientQuestCache.INSTANCE.getQuestDisplayName(tracked.getQuestId());
        model.phaseId = phaseId;
        model.phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(tracked.getQuestId(), phaseId);
        model.phaseDescription = phase.getDescription().getString();
        model.themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(tracked.getQuestId(), ArcQuestTrackerConstants.COLOR_ACCENT_DEFAULT);
        model.collectionQuest = definition.isCollectionQuest();
        model.pushDownOffset = QuestToastManager.getPushDownOffset() + overlayPressure;
        model.uiScale = ArcScaleResolver.resolveUniversalUiScale(context.screenWidth(), context.screenHeight());
        model.virtualScreenWidth = Math.round(context.screenWidth() / model.uiScale);

        model.activePhases.clear();
        for (String id : tracked.getActivePhaseIds()) {
            QuestTrackerPhaseViewModel p = new QuestTrackerPhaseViewModel();
            p.phaseId = id;
            p.phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(tracked.getQuestId(), id);
            p.active = true;
            p.displayed = Objects.equals(id, phaseId);
            model.activePhases.add(p);
        }

        model.objectives.clear();
        model.collection.visibleEntryLines.clear();
        model.collection.rewardLines.clear();
        if (definition.isCollectionQuest()) rebuildCollection(tracked);
        else rebuildObjectives(tracked, phaseId, phase);

        int contentRows = definition.isCollectionQuest() ? Math.max(2, model.collection.visibleEntryLines.size()) : Math.max(1, model.objectives.size());
        model.targetHeight = ArcQuestTrackerConstants.PADDING + ArcQuestTrackerConstants.TITLE_HEIGHT + 16 + 28 + contentRows * 18 + ArcQuestTrackerConstants.PADDING;
        model.markDirty();
    }

    private String resolvePhaseId(QuestRuntimeData tracked, QuestDefinition definition) {
        if (trackedPhaseId != null && tracked.isPhaseActive(trackedPhaseId) && definition.getPhase(trackedPhaseId) != null) {
            return trackedPhaseId;
        }
        String current = tracked.getCurrentPhaseId();
        if (current != null && !current.isEmpty() && definition.getPhase(current) != null) {
            return current;
        }
        for (String id : tracked.getActivePhaseIds()) {
            if (definition.getPhase(id) != null) {
                return id;
            }
        }
        return displayedPhaseId == null ? "" : displayedPhaseId;
    }

    private void rebuildObjectives(QuestRuntimeData tracked, String phaseId, PhaseDefinition phase) {
        int index = 0;
        for (ObjectiveEntry objective : phase.getObjectives()) {
            QuestTrackerObjectiveViewModel o = new QuestTrackerObjectiveViewModel();
            o.key = objective.getTargetId().toString() + ":" + index;
            o.text = objective.getDisplayText().getString();
            o.progress = tracked.getObjectiveProgress(phaseId, index);
            o.required = objective.getRequiredCount();
            o.complete = o.progress >= o.required;
            o.progressVisual = o.required <= 0 ? 1f : Math.min(1f, o.progress / (float) o.required);
            model.objectives.add(o);
            index++;
        }
    }

    private void rebuildCollection(QuestRuntimeData tracked) {
        CollectionRuntimeData data = tracked.getCollectionData();
        model.collection.completedCount = tracked.getCompletedPhaseIds().size();
        model.collection.discoveredCount = data == null ? 0 : data.getDiscoveredPhaseIds().size();
        model.collection.totalCount = Math.max(model.collection.completedCount, model.collection.discoveredCount);
        model.collection.visibleEntryLines.add("Discovered: " + model.collection.discoveredCount);
        model.collection.visibleEntryLines.add("Completed: " + model.collection.completedCount);
        if (data != null) model.collection.rewardLines.add("Rewards unlocked: " + data.getUnlockedRewardIds().size());
    }
}
