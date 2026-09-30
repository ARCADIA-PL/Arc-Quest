package org.arcadia.arc_quest.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.quest.icon.IconFrameSelection;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconContext;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconSession;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconsClient;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.EntityPortraits;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test-only gallery draws production frame selections; it does not implement substitute icons. */
final class ObjectiveIconAuditGallery extends Screen {
    private final List<ObjectiveIconClientAuditFixtures.Sample> samples;
    private final ObjectiveIconSession session = new ObjectiveIconSession();
    private final Map<String, IconFrameSelection> frames = new LinkedHashMap<>();
    private boolean complete;
    ObjectiveIconAuditGallery(String title, List<ObjectiveIconClientAuditFixtures.Sample> samples) {
        super(Component.literal(title));
        this.samples = List.copyOf(samples);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try { renderGallery(graphics); }
        catch (Throwable error) { ObjectiveIconClientAudit.fail(error); }
    }
    private void renderGallery(GuiGraphics graphics) {
        session.beginFrame();
        for (int i = 0; i < samples.size(); i++) {
            var objective = samples.get(i).objective();
            session.resolve(new ObjectiveIconContext("audit_gallery", "samples", i, objective, 0,
                    objective.getRequiredCount(), ObjectiveIconsClient.generation()));
        }
        // Resolve the complete batch before preparing the six real offscreen head portraits.
        EntityPortraits.prepare();
        EntityPortraits.prepare();
        frames.clear();
        graphics.fill(0, 0, width, height, 0xFF101820);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, "Production EntityPortraits + ObjectiveIconSession | no entity instances", width / 2, 30, 0x9FC5D8);
        int cols = 4, cellWidth = Math.max(1, (width - 32) / cols);
        int rows = Math.max(1, (samples.size() + cols - 1) / cols);
        int cellHeight = Math.max(1, (height - 85) / rows);
        int size = Math.max(8, Math.min(96, Math.min(cellWidth - 22, cellHeight - 39)));
        complete = true;
        for (int i = 0; i < samples.size(); i++) {
            var sample = samples.get(i);
            int x = 16 + i % cols * cellWidth, y = 54 + i / cols * cellHeight;
            graphics.fill(x + 2, y + 2, x + cellWidth - 4, y + cellHeight - 4, 0xFF1B2834);
            var context = new ObjectiveIconContext("audit_gallery", "samples", i, sample.objective(), 0,
                    sample.objective().getRequiredCount(), ObjectiveIconsClient.generation());
            var resolved = session.resolve(context);
            var selected = session.select(context, false, true);
            frames.put(sample.objective().getObjectiveId(), selected);
            selected.render(graphics, x + (cellWidth - size) / 2, y + 8, size);
            boolean matches = resolved.available() == sample.expectedAvailable();
            complete &= matches;
            graphics.drawCenteredString(font, font.plainSubstrByWidth(sample.label(), cellWidth - 12),
                    x + cellWidth / 2, y + cellHeight - 29, 0xFFFFFF);
            String state = selected.isItem() ? "candidate " + (selected.index() + 1) + "/" + selected.candidateCount()
                    : selected.available() ? "2D visual ready" : "no icon";
            graphics.drawCenteredString(font, state, x + cellWidth / 2, y + cellHeight - 16, matches ? 0x8FDEA8 : 0xFF9C86);
        }
        graphics.drawString(font, "Resource generation: " + ObjectiveIconsClient.generation()
                + " | actual GUI scale: " + minecraft.getWindow().getGuiScale(), 16, height - 17, 0x9FC5D8);
        session.endFrame();
    }
    boolean complete() { return complete && frames.size() == samples.size(); }
    IconFrameSelection frame(String objectiveId) { return frames.get(objectiveId); }
    Map<String, IconFrameSelection> frames() { return Map.copyOf(frames); }
}
