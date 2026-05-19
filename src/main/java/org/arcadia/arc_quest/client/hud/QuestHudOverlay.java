package org.arcadia.arc_quest.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.fml.loading.FMLPaths;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.quest.toast.BranchChoiceToast;
import org.arcadia.arc_quest.client.hud.quest.toast.PhaseUpdateToast;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.client.hud.quest.tracker.QuestTrackerPanel;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class QuestHudOverlay implements IGuiOverlay {

    public static final QuestHudOverlay INSTANCE = new QuestHudOverlay();

    private static final int POPUP_H = 36;
    private static final int LEFT_BASE_X = 20;
    private static final Gson GSON = new GsonBuilder().create();
    private static Path trackedCacheFile = null;

    private final QuestTrackerPanel trackerPanel = new QuestTrackerPanel();

    private long lastRenderTime = 0;
    private float dt = 0f;

    private String lastTrackedQuestId = null;
    private String lastKnownPhaseId = null;
    private Set<String> lastKnownActivePhaseIds = new LinkedHashSet<>();

    private PhaseUpdateToast phaseUpdateToast = null;
    private BranchChoiceToast branchChoiceToast = null;

    private float currentPhasePopupY = -1;
    private float currentBranchToastY = -1;

    private boolean trackedSelectionLoaded = false;

    private QuestHudOverlay() {
    }

    private static Path getTrackedCacheFile() {
        if (trackedCacheFile != null) return trackedCacheFile;
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            if (configDir == null) return null;
            trackedCacheFile = configDir.resolve("arc_quest_tracked_cache.json");
            return trackedCacheFile;
        } catch (Exception ignored) {
            return null;
        }
    }

    public void setTrackedQuest(String questId) {
        trackerPanel.setTrackedQuest(questId);
        saveTrackedSelectionAsync();
    }

    public void setTrackedFocus(String questId, String phaseId) {
        trackerPanel.setTrackedFocus(questId, phaseId);
        saveTrackedSelectionAsync();
    }

    public String getTrackedPhaseId() {
        return trackerPanel.getTrackedPhaseId();
    }

    public String getTrackedQuestId() {
        return trackerPanel.getTrackedQuestId();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (!trackedSelectionLoaded) loadTrackedSelectionFromDisk();
        if (mc.player == null || mc.options.hideGui) return;

        boolean isSplashActive = QuestSplashRenderer.isActive();
        boolean isBlockingScreen = isSplashActive || mc.screen instanceof QuestJournalScreen || mc.screen instanceof DialogueScreen;

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        Map<String, QuestRuntimeData> active = ClientQuestCache.INSTANCE.getAllActiveQuests();
        QuestRuntimeData tracked = resolveTrackedQuest(active);

        if (tracked == null) {
            resetPhaseTrackingState();
            if (!isSplashActive && !isBlockingScreen) {
                QuestToastManager.render(g, screenWidth, screenHeight);
            }
            return;
        }

        String questId = tracked.getQuestId();
        int currentThemeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, 0xFF4FC3F7);

        updatePhasePopup(tracked, currentThemeColor);

        int centerY = screenHeight / 2;
        int gap = 8;

        boolean isPhaseActive = phaseUpdateToast != null;
        boolean isBranchActive = branchChoiceToast != null;

        float targetPhaseY = centerY - POPUP_H / 2f;
        float targetBranchY = centerY - POPUP_H / 2f;

        if (isPhaseActive && isBranchActive) {
            float totalH = POPUP_H * 2 + gap;
            targetPhaseY = centerY - totalH / 2f;
            targetBranchY = targetPhaseY + POPUP_H + gap;
        } else if (isPhaseActive) {
            targetBranchY = targetPhaseY;
        } else if (isBranchActive) {
            targetPhaseY = targetBranchY;
        }

        if (currentPhasePopupY < 0 || !isPhaseActive) currentPhasePopupY = targetPhaseY;
        if (currentBranchToastY < 0 || !isBranchActive) currentBranchToastY = targetBranchY;

        if (isPhaseActive || isBranchActive) {
            currentPhasePopupY = HudAnimUtil.lerp(currentPhasePopupY, targetPhaseY, 0.15f, dt);
            currentBranchToastY = HudAnimUtil.lerp(currentBranchToastY, targetBranchY, 0.15f, dt);
        }

        if (isPhaseActive) {
            if (!phaseUpdateToast.render(g, mc.font, LEFT_BASE_X, (int) currentPhasePopupY, 1f, isBlockingScreen)) {
                phaseUpdateToast = null;
            }
        }
        if (isBranchActive) {
            if (!branchChoiceToast.render(g, LEFT_BASE_X, (int) currentBranchToastY, 1f, partialTick, isBlockingScreen)) {
                branchChoiceToast = null;
                currentBranchToastY = -1;
            }
        }

        // 解除强行截断，让 TrackerPanel 内部处理状态机，从而触发滑出/滑入动画！
        trackerPanel.render(g, screenWidth, screenHeight, partialTick);

        if (!isSplashActive && !isBlockingScreen) {
            QuestToastManager.render(g, screenWidth, screenHeight);
        }
    }

    private void updatePhasePopup(QuestRuntimeData tracked, int themeColor) {
        String questId = tracked.getQuestId();

        if (!questId.equals(lastTrackedQuestId)) {
            lastTrackedQuestId = questId;
            lastKnownPhaseId = tracked.getCurrentPhaseId();
            lastKnownActivePhaseIds = new LinkedHashSet<>(tracked.getActivePhaseIds());
            return;
        }

        String currentPhaseId = tracked.getCurrentPhaseId();
        Set<String> currentActive = new LinkedHashSet<>(tracked.getActivePhaseIds());

        String addedPhase = null;
        for (String pid : currentActive) {
            if (!lastKnownActivePhaseIds.contains(pid)) {
                addedPhase = pid;
                break;
            }
        }

        String removedPhase = null;
        for (String pid : lastKnownActivePhaseIds) {
            if (!currentActive.contains(pid)) {
                removedPhase = pid;
                break;
            }
        }

        if (addedPhase != null && !addedPhase.isEmpty()) {
            String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, addedPhase);
            phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor, PhaseUpdateToast.Kind.ADDED);
        } else if (removedPhase != null && !removedPhase.isEmpty()) {
            String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, removedPhase);
            phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor, PhaseUpdateToast.Kind.COMPLETED);
        } else if (lastKnownPhaseId != null && !lastKnownPhaseId.equals(currentPhaseId) && currentPhaseId != null && !currentPhaseId.isEmpty()) {
            String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, currentPhaseId);
            phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor, PhaseUpdateToast.Kind.SWITCHED);
        }

        lastKnownPhaseId = currentPhaseId;
        lastKnownActivePhaseIds = currentActive;
    }

    private QuestRuntimeData resolveTrackedQuest(Map<String, QuestRuntimeData> active) {
        String trackedQuestId = trackerPanel.getTrackedQuestId();
        QuestRuntimeData data = trackedQuestId != null ? ClientQuestCache.INSTANCE.getActiveQuest(trackedQuestId) : null;

        if (data == null && trackedQuestId != null) {
            trackerPanel.setTrackedQuest(null);
            saveTrackedSelectionAsync();
            trackedQuestId = null;
        }

        if (data == null && !active.isEmpty()) {
            data = ClientQuestCache.INSTANCE.resolveTrackedQuest(null);
            if (data != null) {
                trackerPanel.setTrackedQuest(data.getQuestId());
                saveTrackedSelectionAsync();
            }
        }

        return data;
    }

    private void resetPhaseTrackingState() {
        lastTrackedQuestId = null;
        lastKnownPhaseId = null;
        lastKnownActivePhaseIds.clear();
    }

    private void loadTrackedSelectionFromDisk() {
        trackedSelectionLoaded = true;
        Path cacheFile = getTrackedCacheFile();
        if (cacheFile == null || !Files.exists(cacheFile)) return;
        try {
            TrackedSelection saved = GSON.fromJson(Files.readString(cacheFile), TrackedSelection.class);
            if (saved != null && saved.questId != null && !saved.questId.isEmpty()) {
                trackerPanel.setTrackedFocus(saved.questId, saved.phaseId);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveTrackedSelectionAsync() {
        Path cacheFile = getTrackedCacheFile();
        if (cacheFile == null) return;
        TrackedSelection snapshot = new TrackedSelection(trackerPanel.getTrackedQuestId(), trackerPanel.getTrackedPhaseId());
        CompletableFuture.runAsync(() -> {
            try {
                Files.writeString(cacheFile, GSON.toJson(snapshot));
            } catch (Exception ignored) {
            }
        });
    }

    public void showBranchChoiceToast(String questId) {
        showBranchChoiceToast(questId, null);
    }

    public void showBranchChoiceToast(String questId, String phaseId) {
        if (branchChoiceToast != null && branchChoiceToast.sameTarget(questId, phaseId)) return;
        branchChoiceToast = new BranchChoiceToast(questId, phaseId);
    }

    public void clearBranchChoiceToast() {
        if (branchChoiceToast != null) branchChoiceToast.dismiss();
    }

    public void showPhaseUpdateToast(String phaseName, int themeColor, PhaseUpdateToast.Kind kind) {
        if (phaseName == null || phaseName.isEmpty()) return;
        phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor, kind);
    }

    public void clearBranchChoiceToast(String questId, String phaseId) {
        if (branchChoiceToast != null && branchChoiceToast.sameTarget(questId, phaseId))
            branchChoiceToast.dismiss();
    }

    public String getBranchChoiceQuestId() {
        return branchChoiceToast != null ? branchChoiceToast.getQuestId() : null;
    }

    private record TrackedSelection(String questId, String phaseId) {
    }
}