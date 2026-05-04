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
    private final ArcGuiRect panel;
    private final ArcGuiRect accent;
    private final ArcGuiText title;
    private final ArcGuiText phase;
    private final ArcGuiWrappedText description;
    private final List<ArcGuiText> phaseLines = new ArrayList<>();
    private final List<ArcGuiText> objectiveLines = new ArrayList<>();
    private final List<ArcGuiRect> progressBackgrounds = new ArrayList<>();
    private final List<ArcGuiRect> progressFills = new ArrayList<>();
    private final ArcScissorStack scissorStack = new ArcScissorStack();

    public ArcQuestTrackerOverlayRoot(Minecraft minecraft, QuestTrackerViewModel model) {
        super(minecraft, "arc_quest_tracker");
        this.model = model;
        panel = new ArcGuiRect(0, 0, PANEL_WIDTH, 90, 0x55000000);
        accent = new ArcGuiRect(0, 0, TrackerConstants.ACCENT_WIDTH, 90, 0xFF4FC3F7);
        title = new ArcGuiText(CONTENT_X, TrackerConstants.PADDING, "").setColor(0xFFFFFFFF);
        phase = new ArcGuiText(CONTENT_X, TrackerConstants.PADDING + 14, "").setColor(0xFF4FC3F7);
        description = new ArcGuiWrappedText(CONTENT_X, TrackerConstants.PADDING + 29, OBJECTIVE_WIDTH, "").setColor(0xFFD6E8FF).setShadow(true);
        addChild(panel);
        panel.addChild(accent);
        panel.addChild(title);
        panel.addChild(phase);
        panel.addChild(description);
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
        panel.setColor(0x55000000);
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
        description.setOpacity(contentOpacity);
        phase.setX(CONTENT_X + drift);
        description.setX(CONTENT_X + drift);
        for (ArcGuiText line : objectiveLines) {
            line.setOpacity(contentOpacity);
            line.setX(CONTENT_X + drift);
        }
        for (ArcGuiRect background : progressBackgrounds) background.setX(CONTENT_X + drift);
        for (ArcGuiRect fill : progressFills) fill.setX(CONTENT_X + drift);
    }

    private void applyModel() {
        title.setText(model.questTitle);
        phase.setText(model.phaseName);
        phase.setColor(model.themeColor);
        description.setText(model.phaseDescription);
        int cursorY = TrackerConstants.PADDING + 29 + Math.max(18, description.getHeight() + 6);
        ensurePhaseLineCount(Math.max(0, model.activePhases.size() - 1));
        if (model.activePhases.size() > 1) {
            StringBuilder builder = new StringBuilder();
            for (QuestTrackerPhaseViewModel phaseModel : model.activePhases) {
                if (builder.length() > 0) builder.append("  ");
                builder.append(phaseModel.displayed ? "◆ " : "◇ ").append(phaseModel.phaseName);
            }
            phase.setText(builder.toString());
        }
        int count = model.collectionQuest ? Math.max(1, model.collection.visibleEntryLines.size() + model.collection.rewardLines.size()) : Math.max(1, model.objectives.size());
        ensureLineCount(count);
        int index = 0;
        if (model.collectionQuest) {
            for (String line : model.collection.visibleEntryLines) {
                configureLine(index++, cursorY, line, 0xFFE8E8E8);
                cursorY += 12;
            }
            for (String line : model.collection.rewardLines) {
                configureLine(index++, cursorY, line, 0xFFFFD166);
                cursorY += 12;
            }
        } else {
            for (QuestTrackerObjectiveViewModel objective : model.objectives) {
                String text = objective.complete ? "✓ " + objective.text : "• " + objective.text + " " + objective.progress + "/" + objective.required;
                configureLine(index, cursorY, text, objective.complete ? 0xFF7CFFB2 : 0xFFE8E8E8);
                configureProgress(index, cursorY + 11, objective.progressVisual, objective.complete ? 0xFF7CFFB2 : model.themeColor);
                index++;
                cursorY += 18;
            }
        }
        for (int i = index; i < objectiveLines.size(); i++) {
            objectiveLines.get(i).setVisible(false);
            if (i < progressBackgrounds.size()) progressBackgrounds.get(i).setVisible(false);
            if (i < progressFills.size()) progressFills.get(i).setVisible(false);
        }
        model.markClean();
    }

    private void ensureLineCount(int count) {
        while (objectiveLines.size() < count) {
            ArcGuiText line = new ArcGuiText(CONTENT_X, 0, OBJECTIVE_WIDTH, "").setColor(0xFFE8E8E8);
            ArcGuiRect progressBg = new ArcGuiRect(CONTENT_X, 0, OBJECTIVE_WIDTH, 2, 0x33000000);
            ArcGuiRect progressFill = new ArcGuiRect(CONTENT_X, 0, 0, 2, model.themeColor);
            objectiveLines.add(line);
            progressBackgrounds.add(progressBg);
            progressFills.add(progressFill);
            panel.addChild(line);
            panel.addChild(progressBg);
            panel.addChild(progressFill);
        }
    }

    private void ensurePhaseLineCount(int count) {
        while (phaseLines.size() < count) {
            ArcGuiText line = new ArcGuiText(CONTENT_X, 0, OBJECTIVE_WIDTH, "").setColor(0xFF9FB7D8);
            phaseLines.add(line);
            panel.addChild(line);
        }
        for (ArcGuiText line : phaseLines) line.setVisible(false);
    }

    private void configureLine(int index, int y, String text, int color) {
        ArcGuiText line = objectiveLines.get(index);
        line.setVisible(true);
        line.setY(y);
        line.setText(text);
        line.setColor(color);
        if (index < progressBackgrounds.size()) progressBackgrounds.get(index).setVisible(false);
        if (index < progressFills.size()) progressFills.get(index).setVisible(false);
    }

    private void configureProgress(int index, int y, float progress, int color) {
        ArcGuiRect background = progressBackgrounds.get(index);
        ArcGuiRect fill = progressFills.get(index);
        background.setVisible(true);
        background.setY(y);
        fill.setVisible(true);
        fill.setY(y);
        fill.setWidth(Math.round(OBJECTIVE_WIDTH * Math.max(0f, Math.min(1f, progress))));
        fill.setColor(color);
    }
}
