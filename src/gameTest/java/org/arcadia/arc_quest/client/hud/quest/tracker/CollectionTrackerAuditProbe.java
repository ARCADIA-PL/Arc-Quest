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
    public void render(GuiGraphics graphics, QuestDefinition definition, QuestRuntimeData runtime, float alpha) {
        if (snapshot == null || snapshot.hide()) return;
        int width = 220;
        var font = Minecraft.getInstance().font;
        renderer.render(graphics, font, runtime, definition, snapshot, width, renderer.height(font, width, snapshot),
                TrackerStyle.FOCUS, 0x62B894, alpha);
    }
}
