package org.arcadia.arc_quest.client.hud.quest.tracker;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;

/** Package access to the same production projection and renderer used by the overlay. */
public final class CollectionTrackerAuditProbe {
    private final CollectionTrackerRenderer renderer = new CollectionTrackerRenderer();
    private CollectionTrackerRenderer.Snapshot snapshot;
    public void update(QuestDefinition definition, QuestRuntimeData runtime, String phase, long now) {
        snapshot = renderer.snapshot(definition, runtime, phase, now, false);
    }
    public boolean focused() { return snapshot != null && snapshot.focus() != null; }
    public boolean finishing() { return snapshot != null && snapshot.finishing(); }
    public boolean ready() { return snapshot != null && snapshot.readyMessage(); }
    public boolean hidden() { return snapshot != null && snapshot.hide(); }
    public TrackerLayout.Frame renderRestoredHud(GuiGraphics graphics, int width, int height) throws ReflectiveOperationException {
        var panel = QuestTrackerPanel.INSTANCE;
        var synchronize = QuestTrackerPanel.class.getDeclaredMethod("syncTrackedFocus");
        synchronize.setAccessible(true); synchronize.invoke(panel);
        var render = QuestTrackerPanel.class.getDeclaredMethod("renderPanel", GuiGraphics.class,
                int.class, int.class, float.class, TrackerLayout.Settings.class, TrackerStyle.class, boolean.class);
        render.setAccessible(true);
        // The disconnected menu must bypass only player/screen visibility guards. Calling
        // the actual panel directly prevents renderPreview's example fallback from passing.
        return (TrackerLayout.Frame) render.invoke(panel, graphics, width, height, 0f,
                TrackerLayout.DEFAULT, TrackerStyle.FOCUS, true);
    }
    public void render(GuiGraphics graphics, QuestDefinition definition, QuestRuntimeData runtime, float alpha) {
        if (snapshot == null || snapshot.hide()) return;
        int width = 220;
        var font = Minecraft.getInstance().font;
        renderer.render(graphics, font, runtime, definition, snapshot, width, renderer.height(font, width, snapshot),
                TrackerStyle.FOCUS, 0x62B894, alpha);
    }
}
