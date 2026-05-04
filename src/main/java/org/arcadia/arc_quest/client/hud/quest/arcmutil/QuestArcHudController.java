package org.arcadia.arc_quest.client.hud.quest.arcmutil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker.ArcQuestTrackerOverlayRoot;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker.QuestTrackerPresenter;
import org.arcadia.arc_quest.mutil.core.ArcGuiTickContextFactory;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayHost;
import org.arcadia.arc_quest.mutil.perf.ArcGuiProfiler;

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
        ArcGuiProfiler.setEnabled(QuestHudMigrationFlags.ARC_DEBUG_OVERLAY);
        if (QuestHudMigrationFlags.ARC_TRACKER_ENABLED) trackerPresenter.tick(ArcGuiTickContextFactory.create(Minecraft.getInstance()));
        host.tickAll();
    }

    public void render(GuiGraphics graphics, float partialTick) {
        ensureInitialized();
        host.renderAll(graphics, partialTick);
        if (QuestHudMigrationFlags.ARC_DEBUG_OVERLAY) renderDebug(graphics);
    }

    private void renderDebug(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        graphics.drawString(minecraft.font, "Arc Quest HUD: tracker=" + QuestHudMigrationFlags.ARC_TRACKER_ENABLED, 6, 6, 0xFFFFFFFF, true);
        graphics.drawString(minecraft.font, trackerPresenter.dumpDebug(), 6, 17, 0xFFB6E3FF, true);
        graphics.drawString(minecraft.font, ArcGuiProfiler.dumpLastFrame(), 6, 28, 0xFFB6FFB6, true);
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
