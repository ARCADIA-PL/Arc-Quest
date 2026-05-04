package org.arcadia.arc_quest.client.hud.quest.arcmutil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.ArcQuestLegacyPanelOverlayRoot;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.toast.ArcQuestCenterToastManager;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.toast.ArcQuestCenterToastOverlayRoot;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.toast.ArcQuestToastOverlayRoot;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker.ArcQuestTrackerOverlayRoot;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker.QuestTrackerPresenter;
import org.arcadia.arc_quest.mutil.core.ArcGuiTickContextFactory;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayHost;

import javax.annotation.Nullable;

public final class QuestArcHudController {
    public static final QuestArcHudController INSTANCE = new QuestArcHudController();

    private final ArcOverlayHost host = new ArcOverlayHost();
    private final QuestTrackerPresenter trackerPresenter = new QuestTrackerPresenter();
    private boolean initialized;

    private QuestArcHudController() {
    }

    public void ensureInitialized() {
        if (initialized) return;
        ArcQuestTrackerOverlayRoot trackerRoot = new ArcQuestTrackerOverlayRoot(Minecraft.getInstance(), trackerPresenter.model());
        ArcQuestToastOverlayRoot toastRoot = new ArcQuestToastOverlayRoot(Minecraft.getInstance());
        ArcQuestCenterToastOverlayRoot centerToastRoot = new ArcQuestCenterToastOverlayRoot(Minecraft.getInstance());
        ArcQuestLegacyPanelOverlayRoot panelRoot = new ArcQuestLegacyPanelOverlayRoot(Minecraft.getInstance());
        host.register(trackerRoot);
        host.register(toastRoot);
        host.register(centerToastRoot);
        host.register(panelRoot);
        initialized = true;
    }

    public void tick() {
        ensureInitialized();
        QuestHudBlockState blockState = QuestHudBlockState.current();
        ArcQuestCenterToastManager.tick(blockState.freezeToasts());
        trackerPresenter.tick(ArcGuiTickContextFactory.create(Minecraft.getInstance()), blockState);
        host.tickAll();
    }

    public void render(GuiGraphics graphics, float partialTick) {
        ensureInitialized();
        host.renderAll(graphics, partialTick);
    }

    public void setTrackedQuest(@Nullable String questId) {
        trackerPresenter.setTrackedQuest(questId);
    }

    public void setTrackedFocus(@Nullable String questId, @Nullable String phaseId) {
        trackerPresenter.setTrackedFocus(questId, phaseId);
    }

    public void setOverlayPressure(int pixels) {
        trackerPresenter.setOverlayPressure(pixels);
    }

    @Nullable
    public String getTrackedQuestId() {
        return trackerPresenter.getTrackedQuestId();
    }

    @Nullable
    public String getTrackedPhaseId() {
        return trackerPresenter.getTrackedPhaseId();
    }
}
