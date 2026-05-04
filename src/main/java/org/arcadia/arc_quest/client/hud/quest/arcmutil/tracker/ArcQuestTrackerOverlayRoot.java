package org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.tracker.TrackerConstants;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiTickContext;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayRoot;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiRect;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiText;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiWrappedText;
import org.arcadia.arc_quest.mutil.screen.ArcScissorStack;

import java.util.ArrayList;
import java.util.List;

public class ArcQuestTrackerOverlayRoot extends ArcOverlayRoot {
    private static final int CONTENT_X = TrackerConstants.ACCENT_WIDTH + TrackerConstants.PADDING;
    private static final int PANEL_WIDTH = TrackerConstants.PANEL_WIDTH;
    private static final int OBJECTIVE_WIDTH = PANEL_WIDTH - CONTENT_X - TrackerConstants.PADDING;

    private final QuestTrackerViewModel model;
    private final ArcGuiRect shadow;
    private final ArcGuiRect panel;
    private final ArcGuiRect topLine;
    private final ArcGuiRect accent;
    private final ArcGuiRect contentRule;
    private final ArcGuiText title;
    private final ArcGuiText phase;
    private final ArcQuestTrackerPhaseStrip phaseStrip;
    private final ArcGuiWrappedText description;
    private final ArcQuestTrackerCollectionSummary collectionSummary;
    private final List<ArcQuestTrackerObjectiveRow> objectiveRows = new ArrayList<>();
    private final ArcScissorStack scissorStack = new ArcScissorStack();

    public ArcQuestTrackerOverlayRoot(Minecraft minecraft, QuestTrackerViewModel model) {
        super(minecraft, "arc_quest_tracker");
        this.model = model;
        shadow = new ArcGuiRect(3, 4, PANEL_WIDTH, 90, 0x33000000);
        panel = new ArcGuiRect(0, 0, PANEL_WIDTH, 90, 0xAA071019);
        topLine = new ArcGuiRect(0, 0, PANEL_WIDTH, 1, 0x334FC3F7);
        accent = new ArcGuiRect(0, 0, TrackerConstants.ACCENT_WIDTH, 90, 0xFF4FC3F7);
        contentRule = new ArcGuiRect(CONTENT_X, TrackerConstants.PADDING + 25, OBJECTIVE_WIDTH, 1, 0x224FC3F7);
        title = new ArcGuiText(CONTENT_X, TrackerConstants.PADDING, "").setColor(0xFFFFFFFF);
        phase = new ArcGuiText(CONTENT_X, TrackerConstants.PADDING + 14, "").setColor(0xFF4FC3F7);
        phaseStrip = new ArcQuestTrackerPhaseStrip(CONTENT_X, TrackerConstants.PADDING + 27, OBJECTIVE_WIDTH);
        description = new ArcGuiWrappedText(CONTENT_X, TrackerConstants.PADDING + 31, OBJECTIVE_WIDTH, "").setColor(0xFFD6E8FF).setShadow(true);
        collectionSummary = new ArcQuestTrackerCollectionSummary(CONTENT_X, 0, OBJECTIVE_WIDTH);
        addChild(shadow);
        addChild(panel);
        panel.addChild(topLine);
        panel.addChild(accent);
        panel.addChild(contentRule);
        panel.addChild(title);
        panel.addChild(phase);
        panel.addChild(phaseStrip);
        panel.addChild(description);
        panel.addChild(collectionSummary);
    }

    @Override
    protected void tick(ArcGuiTickContext context, int refX, int refY) {
        if (!model.visible) {
            setVisible(false);
            return;
        }
        setVisible(true);
        int panelH = Math.max(72, model.targetHeight);
        int virtualWidth = model.virtualScreenWidth > 0 ? model.virtualScreenWidth : context.screenWidth();
        int x = Math.round(virtualWidth - PANEL_WIDTH - TrackerConstants.MARGIN_RIGHT + model.panelSlide * (PANEL_WIDTH + TrackerConstants.MARGIN_RIGHT + 20f));
        int y = TrackerConstants.MARGIN_TOP + model.pushDownOffset;
        setPosition(x, y);
        setOpacity(model.panelReveal);
        panel.setSize(PANEL_WIDTH, panelH);
        shadow.setSize(PANEL_WIDTH, panelH);
        panel.setColor(0xAA071019);
        topLine.setSize(PANEL_WIDTH, 1);
        topLine.setColor((0x66 << 24) | (model.themeColor & 0x00FFFFFF));
        contentRule.setColor((0x33 << 24) | (model.themeColor & 0x00FFFFFF));
        accent.setSize(TrackerConstants.ACCENT_WIDTH, panelH);
        accent.setColor((0xFF << 24) | (model.themeColor & 0x00FFFFFF));
        if (model.isDirty()) applyModel();
        applyTransientState();
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        float scale = model.uiScale <= 0f ? 1f : model.uiScale;
        int panelH = Math.max(72, model.targetHeight);
        int clipW = Math.max(TrackerConstants.ACCENT_WIDTH + 1, Math.round(PANEL_WIDTH * model.wipeReveal));
        scissorStack.push(graphics,
                Math.round((x - 5) * scale),
                Math.round((y - 5) * scale),
                Math.round((clipW + 10) * scale),
                Math.round((panelH + 10) * scale));
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);
        super.draw(graphics, context, refX, refY, inheritedOpacity);
        graphics.pose().popPose();
        scissorStack.pop(graphics);
    }

    private void applyTransientState() {
        float contentOpacity = model.wipeAlpha;
        int drift = Math.round(model.wipeDrift);
        title.setOpacity(contentOpacity);
        phase.setOpacity(contentOpacity);
        phaseStrip.setOpacity(contentOpacity);
        description.setOpacity(contentOpacity);
        collectionSummary.setOpacity(contentOpacity);
        phase.setX(CONTENT_X + drift);
        phaseStrip.setX(CONTENT_X + drift);
        description.setX(CONTENT_X + drift);
        collectionSummary.setX(CONTENT_X + drift);
        for (ArcQuestTrackerObjectiveRow row : objectiveRows) {
            row.setOpacity(contentOpacity);
            row.setX(CONTENT_X + drift);
        }
    }

    private void applyModel() {
        title.setText(model.questTitle);
        phase.setText(model.phaseName);
        phase.setColor(model.themeColor);
        boolean hasParallelPhases = model.activePhases.size() > 1;
        phaseStrip.setVisible(hasParallelPhases);
        if (hasParallelPhases) phaseStrip.apply(model.activePhases, model.themeColor);
        int descriptionY = hasParallelPhases ? TrackerConstants.PADDING + 42 : TrackerConstants.PADDING + 31;
        description.setY(descriptionY);
        description.setText(model.phaseDescription);
        int cursorY = descriptionY + Math.max(18, description.getHeight() + 7);
        if (model.collectionQuest) {
            collectionSummary.setVisible(true);
            collectionSummary.setY(cursorY);
            collectionSummary.apply(model.collection, model.themeColor);
            cursorY += collectionSummary.getHeight() + 6;
            ensureObjectiveRows(Math.max(1, model.collection.visibleEntryLines.size() + model.collection.rewardLines.size()));
            int index = 0;
            for (String line : model.collection.visibleEntryLines) {
                configureCollectionLine(index++, cursorY, line, 0xFFE8E8E8);
                cursorY += 13;
            }
            for (String line : model.collection.rewardLines) {
                configureCollectionLine(index++, cursorY, line, 0xFFFFD166);
                cursorY += 13;
            }
            hideRowsFrom(index);
        } else {
            collectionSummary.setVisible(false);
            ensureObjectiveRows(Math.max(1, model.objectives.size()));
            int index = 0;
            for (QuestTrackerObjectiveViewModel objective : model.objectives) {
                ArcQuestTrackerObjectiveRow row = objectiveRows.get(index);
                row.setVisible(true);
                row.setY(cursorY);
                row.apply(objective, model.themeColor);
                index++;
                cursorY += row.getHeight() + 4;
            }
            hideRowsFrom(index);
        }
        model.markClean();
    }

    private void ensureObjectiveRows(int count) {
        while (objectiveRows.size() < count) {
            ArcQuestTrackerObjectiveRow row = new ArcQuestTrackerObjectiveRow(CONTENT_X, 0, OBJECTIVE_WIDTH);
            objectiveRows.add(row);
            panel.addChild(row);
        }
    }

    private void configureCollectionLine(int index, int y, String text, int color) {
        ArcQuestTrackerObjectiveRow row = objectiveRows.get(index);
        row.setVisible(true);
        row.setY(y);
        row.applyLine(text, color);
    }

    private void hideRowsFrom(int index) {
        for (int i = index; i < objectiveRows.size(); i++) objectiveRows.get(i).setVisible(false);
    }
}
