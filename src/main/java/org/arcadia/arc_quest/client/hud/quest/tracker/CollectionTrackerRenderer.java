package org.arcadia.arc_quest.client.hud.quest.tracker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconSession;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionEntryIcons;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.JournalDetailCollection;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingController;
import org.arcadia.arc_quest.client.quest.tracking.CollectionTrackingFocusSelector;
import org.arcadia.arc_quest.client.quest.tracking.QuestTrackingPresentationState;
import org.arcadia.arc_quest.quest.api.CollectionEntryDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.List;

/** Dedicated read-only Collection view. It never manufactures CUSTOM objectives or handles mouse input. */
final class CollectionTrackerRenderer {
    record Snapshot(String questId, String phaseId, Component phaseName, CollectionSheetProgress sheet,
                    CollectionBindingProgress focus, CollectionEntryDefinition entry,
                    List<CollectionRequirementProgress> requirements, int completedRequirements, int requirementCount,
                    boolean finishing, boolean readyMessage, boolean hide) {}
    private final CollectionTrackerFeedback feedback = new CollectionTrackerFeedback();
    private final ObjectiveIconSession icons = new ObjectiveIconSession();

    void reset() { feedback.reset(); icons.clear(); }

    Snapshot snapshot(QuestDefinition def, QuestRuntimeData runtime, String phaseId, long now, boolean preview) {
        String questId = runtime.getQuestId();
        var state = QuestTrackingPresentationState.INSTANCE;
        var controller = ClientQuestTrackingController.INSTANCE;
        if (!preview) controller.ensureCollectionFocus(questId);
        String storedPhase = state.phaseIdFor(questId);
        String focusId = state.collectionBindingIdFor(questId);
        if (focusId != null && storedPhase != null && def.getPhase(storedPhase) != null
                && def.getPhase(storedPhase).hasCollectionSheet()
                && (runtime.isPhaseActive(storedPhase) || runtime.isPhasePendingManualAdvance(storedPhase))) {
            phaseId = storedPhase;
        }
        var sheet = sheet(questId, phaseId);
        var focus = focusId == null ? null : sheet.binding(focusId);
        if (focus != null && (!focus.visible() || !focus.revealed() && !focus.hasPublicClue())) focus = null;
        if (preview && (focus == null || focus.complete())) {
            var example = controller.selectCollectionFocus(questId, phaseId, null);
            if (example != null) {
                phaseId = example.phaseId();
                sheet = sheet(questId, phaseId);
                focus = example.binding();
            }
        }
        boolean ready = sheet.complete() && runtime.isPhasePendingManualAdvance(phaseId);
        var response = feedback.update(questId + "/" + CollectionTrackingFocusSelector.runId(runtime) + "/" + phaseId,
                focus == null ? "" : focus.bindingId(), ready, focus != null && focus.complete(), now);
        if (!preview && (focus == null || !response.keepFocus())) {
            state.clearCollectionFocus(questId);
            var next = controller.selectCollectionFocus(questId, phaseId, null);
            if (next == null) {
                focus = null;
            } else {
                phaseId = next.phaseId();
                sheet = sheet(questId, phaseId);
                focus = next.binding();
                state.focusCollection(questId, phaseId, focus.bindingId(), CollectionTrackingFocusSelector.runId(runtime));
                ready = sheet.complete() && runtime.isPhasePendingManualAdvance(phaseId);
                response = feedback.update(questId + "/" + CollectionTrackingFocusSelector.runId(runtime) + "/" + phaseId,
                        focus.bindingId(), ready, false, now);
            }
        }
        // Anonymous clue tracking never resolves an entity, item or portrait from the hidden definition.
        var entry = focus == null || !focus.revealed() ? null : def.getCollectionConfig().getEntry(focus.entryId());
        var visibleRequirements = focus == null ? List.<CollectionRequirementProgress>of()
                : focus.requirements().stream().filter(r -> r.objective() == null || !r.objective().isHidden()).toList();
        var requirements = visibleRequirements.stream().filter(r -> !r.complete()).limit(2).toList();
        int completed = (int) visibleRequirements.stream().filter(CollectionRequirementProgress::complete).count();
        Component phaseName = def.getAllPhases().size() <= 1 ? Component.empty()
                : ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, phaseId);
        return new Snapshot(questId, phaseId, phaseName, sheet, focus, entry, requirements, completed, visibleRequirements.size(),
                response.focusComplete(), response.readyMessage(), !preview && (focus == null && !response.readyMessage() || response.hide()));
    }

    private static CollectionSheetProgress sheet(String questId, String phaseId) {
        var sheet = ClientQuestCache.INSTANCE.getCollectionSheetProgress(questId, phaseId);
        return sheet == null ? CollectionSheetProgress.EMPTY : sheet;
    }

    int height(Font font, int width, Snapshot snapshot) {
        int height = 23;
        if (!snapshot.phaseName().getString().isEmpty()) height += font.lineHeight + 5;
        if (snapshot.entry() != null) {
            height += 35 + font.lineHeight + 12;
            for (var requirement : snapshot.requirements()) height += rowHeight(font, width, requirement);
            if (snapshot.finishing()) height += font.lineHeight + 5;
        } else if (snapshot.focus() != null && snapshot.focus().hasPublicClue()) {
            height += 2 * (font.lineHeight + 5) + Math.min(2,
                    Math.max(1, font.split(snapshot.focus().publicClue(), Math.max(12, width - 18)).size())) * (font.lineHeight + 2) + 7;
        } else {
            if (snapshot.readyMessage()) height += font.lineHeight + 6;
        }
        return Math.max(58, height + 9);
    }

    private static int rowHeight(Font font, int width, CollectionRequirementProgress requirement) {
        String value = requirement.current() + "/" + requirement.target();
        return Math.min(2, Math.max(1, font.split(requirement.label(), Math.max(12, width - 32 - font.width(value))).size()))
                * (font.lineHeight + 2) + 7;
    }

    void render(GuiGraphics g, Font font, QuestRuntimeData runtime, QuestDefinition def, Snapshot snapshot,
                int width, int height, TrackerStyle style, int theme, float alpha) {
        int a = (int) (255 * alpha);
        if (a <= 4) return;
        if (style == TrackerStyle.CLASSIC) {
            HudAnimUtil.drawAccentPanel(g, 0, 0, width, height, color(0x000000, (int) (85 * alpha)),
                    color(theme, a), TrackerConstants.ACCENT_WIDTH);
        } else {
            // A slim open edge keeps the collection's specimen focus readable without a thick frame.
            g.fill(0, 0, width, height, color(0x08120F, (int) (76 * alpha)));
            g.fill(0, 0, 2, height - 9, color(theme, (int) (190 * alpha)));
            g.fill(2, height - 9, 4, height - 7, color(theme, (int) (160 * alpha)));
            g.fill(4, height - 7, 16, height - 6, color(theme, (int) (120 * alpha)));
        }
        TrackerTitleWidget.renderTitle(g, runtime, 9, 7, alpha, 1, font, width, 0);
        int x = 9, y = 23, available = width - 18;
        if (!snapshot.phaseName().getString().isEmpty()) {
            fit(g, font, snapshot.phaseName(), x, y, available, 0xBBBBBB, a);
            y += font.lineHeight + 5;
        }
        if (snapshot.entry() != null && snapshot.focus() != null) {
            icons.beginFrame();
            var context = CollectionEntryIcons.context(snapshot.questId(), snapshot.phaseId(), snapshot.focus().bindingId(), snapshot.entry());
            var selected = icons.select(context, false, true);
            int offset = selected.available() ? 34 : 0;
            if (selected.available()) selected.render(g, x, y, 26, alpha);
            fit(g, font, snapshot.entry().getDisplayName(), x + offset, y + 6, available - offset, 0xEEEEEE, a);
            icons.endFrame();
            y += 35;
            Component count = JournalDetailCollection.text("tracker_sheet_count", snapshot.sheet().completed(), snapshot.sheet().target());
            fit(g, font, JournalDetailCollection.text("tracker_requirement_summary", snapshot.completedRequirements(), snapshot.requirementCount()), x, y,
                    available - font.width(count) - 8, 0xAAAAAA, a);
            g.drawString(font, count, width - 9 - font.width(count), y, color(theme, a), false);
            y += font.lineHeight + 5;
            bar(g, x, y, available, snapshot.completedRequirements(), snapshot.requirementCount(), theme, a);
            y += 7;
            for (var requirement : snapshot.requirements()) {
                String value = requirement.current() + "/" + requirement.target();
                var lines = font.split(requirement.label(), Math.max(12, available - font.width(value) - 12));
                g.drawString(font, value, width - 9 - font.width(value), y, color(0xCCCCCC, a), false);
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    g.drawString(font, lines.get(i), x, y, color(0xDDDDDD, a), false);
                    y += font.lineHeight + 2;
                }
                bar(g, x, y, available, requirement.current(), requirement.target(), theme, a);
                y += 7;
            }
            if (snapshot.finishing()) fit(g, font, JournalDetailCollection.text("entry_achieved"), x, y, available, 0x9AD6AA, a);
        } else if (snapshot.focus() != null && snapshot.focus().hasPublicClue()) {
            fit(g, font, JournalDetailCollection.text("unknown_entry"), x, y, available, 0xEEEEEE, a);
            y += font.lineHeight + 5;
            var lines = font.split(snapshot.focus().publicClue(), Math.max(12, available));
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                g.drawString(font, lines.get(i), x, y, color(0xCCCCCC, a), false);
                y += font.lineHeight + 2;
            }
            y += 5;
            Component count = JournalDetailCollection.text("tracker_sheet_count", snapshot.sheet().completed(), snapshot.sheet().target());
            fit(g, font, count, x, y, available, theme, a);
        } else {
            if (snapshot.readyMessage()) fit(g, font, JournalDetailCollection.text("ready_brief"), x, y, available, 0x9AD6AA, a);
        }
    }

    private static void fit(GuiGraphics g, Font font, Component label, int x, int y, int width, int rgb, int alpha) {
        g.drawString(font, StyledTextUtil.fitSingleLine(font, label, Math.max(1, width)), x, y, color(rgb, alpha), false);
    }
    private static int color(int rgb, int alpha) { return HudAnimUtil.withAlpha(rgb, Math.max(0, Math.min(255, alpha))); }
    private static void bar(GuiGraphics g, int x, int y, int width, int current, int target, int theme, int alpha) {
        g.fill(x, y, x + width, y + 2, color(0xFFFFFF, alpha / 8));
        int fill = target <= 0 ? 0 : Math.max(0, Math.min(width, (int) (width * (double) current / target)));
        if (fill > 0) g.fill(x, y, x + fill, y + 2, color(theme, alpha));
    }
}
