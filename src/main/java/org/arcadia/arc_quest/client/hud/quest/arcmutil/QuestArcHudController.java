package org.arcadia.arc_quest.client.hud.quest.arcmutil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker.ArcQuestTrackerOverlayRoot;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker.QuestTrackerPresenter;
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
        host.register(trackerRoot);
        initialized = true;
    }

    public void tick() {
        ensureInitialized();
        if (QuestHudMigrationFlags.ARC_TRACKER_ENABLED) trackerPresenter.tick(org.arcadia.arc_quest.mutil.core.ArcGuiTickContextFactory.create(Minecraft.getInstance()));
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

    @Nullable
    public String getTrackedQuestId() {
        return trackerPresenter.getTrackedQuestId();
    }

    @Nullable
    public String getTrackedPhaseId() {
        return trackerPresenter.getTrackedPhaseId();
    }
}
