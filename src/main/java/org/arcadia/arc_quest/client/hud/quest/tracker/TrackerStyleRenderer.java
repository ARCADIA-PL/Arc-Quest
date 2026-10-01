package org.arcadia.arc_quest.client.hud.quest.tracker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Two bounded, read-only HUD views. Neither layout nor rendering reads inventories. */
final class TrackerStyleRenderer {
    private static final long SNAPSHOT_INTERVAL_MS = 100;
    private static final int BODY_TOP = 24;
    private static final int FOCUS_BODY_TOP = 21;
    private static final float ROW_SCALE = 0.85f;
    private static final float SMALL_SCALE = 0.75f;

    record Detail(TrackerStyleModel.Objective objective, Component text, String value) {}
    record Lane(String id, Component name, TrackerStyleModel.Summary summary,
                Component status, boolean focused) {}
    record Snapshot(Component title, Lane focus, List<Lane> lanes, List<Detail> details,
                    int otherObjectives, int otherPhases, Component emptyMessage) {}
    private record Row(Detail detail, List<FormattedCharSequence> lines, int height, int valueWidth) {}
    private record LaneRow(Lane lane, FormattedCharSequence name, String count, int height) {}
    private record Layout(Snapshot snapshot, TrackerStyle style, Font font, Language language, int width,
                          List<Row> rows, List<LaneRow> lanes, int height) {}

    private QuestRuntimeData cachedRuntime;
    private QuestDefinition cachedDefinition;
    private String cachedPhase;
    private List<ObjectiveEntry> cachedObjectives;
    private List<String> cachedOrder = List.of();
    private long capturedAt;
    private Snapshot cached;
    private Snapshot example;
    private Layout layout;

    void reset() {
        cachedRuntime = null;
        cachedDefinition = null;
        cachedPhase = null;
        cachedObjectives = null;
        cachedOrder = List.of();
        cached = null;
        example = null;
        layout = null;
    }

    Snapshot snapshot(QuestRuntimeData runtime, QuestDefinition definition, String phaseId,
                      List<ObjectiveEntry> objectives, List<String> activeOrder, long now) {
        if (cached != null && runtime == cachedRuntime && definition == cachedDefinition
                && Objects.equals(phaseId, cachedPhase) && objectives == cachedObjectives
                && activeOrder.equals(cachedOrder) && now >= capturedAt
                && now - capturedAt < SNAPSHOT_INTERVAL_MS) return cached;

        List<TrackerStyleModel.Objective> metrics = metrics(runtime, phaseId, objectives);
        Component phaseName = definition.isCollectionQuest()
                ? text("tracker.collection_progress")
                : ClientQuestCache.INSTANCE.getPhaseDisplayComponent(runtime.getQuestId(), phaseId);
        Lane focus = new Lane(phaseId, phaseName, TrackerStyleModel.summarize(metrics),
                definition.isCollectionQuest() ? null : phaseStatus(runtime, phaseId), true);
        List<Lane> lanes = new ArrayList<>();
        int otherPhases = 0;
        if (definition.isCollectionQuest()) {
            // Collection entries can be undiscovered. Use only the existing public
            // collection summary; never enumerate collection definition entries.
            lanes.add(focus);
        } else {
            List<String> order = new ArrayList<>();
            for (String id : activeOrder) {
                if (definition.getPhase(id) != null && !order.contains(id)) order.add(id);
            }
            if (!order.contains(phaseId)) order.add(phaseId);
            List<Integer> visible = TrackerStyleModel.phaseWindow(order.size(), order.indexOf(phaseId),
                    TrackerStyleModel.PHASE_LIMIT);
            for (int index : visible) {
                String id = order.get(index);
                if (Objects.equals(id, phaseId)) lanes.add(focus);
                else {
                    PhaseDefinition phase = definition.getPhase(id);
                    if (phase == null) continue;
                    lanes.add(new Lane(id, ClientQuestCache.INSTANCE.getPhaseDisplayComponent(runtime.getQuestId(), id),
                            TrackerStyleModel.summarize(metrics(runtime, id, phase.getObjectives())),
                            phaseStatus(runtime, id), false));
                }
            }
            otherPhases = Math.max(0, order.size() - lanes.size());
        }
        List<TrackerStyleModel.Objective> selected = TrackerStyleModel.unfinished(metrics, TrackerStyleModel.DETAIL_LIMIT);
        List<Detail> details = new ArrayList<>(selected.size());
        for (TrackerStyleModel.Objective metric : selected) {
            // Keep the original phase index after filtering, including dynamic counts.
            ObjectiveEntry objective = objectives.get(metric.index());
            Component label = objective.getDisplayText();
            if (objective.isOptional()) label = label.copy().append(" · ").append(text("tracker_style.optional"));
            String value = metric.numeric()
                    ? metric.trackable() ? metric.progress() + "/" + metric.required() : Integer.toString(metric.progress())
                    : "";
            details.add(new Detail(metric, label, value));
        }
        boolean hasProgress = false, allReady = true;
        for (TrackerStyleModel.Objective metric : metrics) {
            // Hidden progress stays authoritative without exposing its text.
            if (metric.trackable()) {
                hasProgress = true;
                if (!metric.complete()) allReady = false;
            }
        }
        Component emptyMessage = focus.status() != null ? focus.status()
                : text(hasProgress && allReady ? "tracker_style.ready" : "tracker_style.empty");
        cached = new Snapshot(ClientQuestCache.INSTANCE.getQuestDisplayComponent(runtime.getQuestId()), focus,
                List.copyOf(lanes), List.copyOf(details),
                Math.max(0, TrackerStyleModel.unfinishedCount(metrics) - selected.size()), otherPhases, emptyMessage);
        cachedRuntime = runtime;
        cachedDefinition = definition;
        cachedPhase = phaseId;
        cachedObjectives = objectives;
        cachedOrder = List.copyOf(activeOrder);
        capturedAt = now;
        return cached;
    }

    private static List<TrackerStyleModel.Objective> metrics(QuestRuntimeData runtime, String phaseId,
                                                            List<ObjectiveEntry> objectives) {
        List<TrackerStyleModel.Objective> result = new ArrayList<>(objectives.size());
        for (int index = 0; index < objectives.size(); index++) {
            ObjectiveEntry objective = objectives.get(index);
            int progress = runtime.getObjectiveProgress(phaseId, index);
            String explicit = objective.getExtra("tracker_progress");
            if (explicit != null) {
                try { progress = Integer.parseInt(explicit); }
                catch (NumberFormatException ignored) { /* Same fallback as the classic tracker. */ }
            }
            int required = runtime.getRequiredCount(phaseId, index, objective.getRequiredCount());
            boolean information = explicit != null && objective.getTargetId().getPath().equals("collection_tracker/claimable_rewards");
            result.add(new TrackerStyleModel.Objective(index, progress, required,
                    explicit != null || objective.getType().isCounting() && required > 1,
                    !objective.getType().equals(ObjectiveType.NULL) && !information, objective.isHidden()));
        }
        return result;
    }

    private static Component phaseStatus(QuestRuntimeData runtime, String phaseId) {
        if (runtime.isPhasePendingManualAdvance(phaseId)) return text("toast.phase_pending_confirm");
        if (runtime.isPhaseCompleted(phaseId)) return text("tracker_style.phase_complete");
        return null;
    }

    Snapshot example() {
        if (example == null) {
            var wood = new TrackerStyleModel.Objective(0, 3, 8, true, true, false);
            var table = new TrackerStyleModel.Objective(1, 0, 1, false, true, false);
            Lane first = new Lane("example_first", previewText("phase"),
                    TrackerStyleModel.summarize(List.of(wood, table)), null, true);
            Lane second = new Lane("example_second", previewText("phase").copy().append(" II"),
                    new TrackerStyleModel.Summary(1, 2, 0.75), null, false);
            example = new Snapshot(previewText("title"), first, List.of(first, second),
                    List.of(new Detail(wood, previewText("objective1"), "3/8"),
                            new Detail(table, previewText("objective2"), "")),
                    0, 0, text("tracker_style.empty"));
        }
        return example;
    }

    int height(Font font, int width, Snapshot snapshot, TrackerStyle style) {
        return layout(font, width, snapshot, style).height();
    }

    private Layout layout(Font font, int width, Snapshot snapshot, TrackerStyle style) {
        if (layout != null && layout.snapshot() == snapshot && layout.style() == style
                && layout.font() == font && layout.language() == Language.getInstance()
                && layout.width() == width) return layout;
        List<Row> rows = new ArrayList<>();
        int rowHeight = 0;
        for (Detail detail : snapshot.details()) {
            int valueWidth = detail.value().isEmpty() ? 0 : (int) Math.ceil(font.width(detail.value()) * ROW_SCALE) + 8;
            int available = Math.max(12, (int) ((width - 29 - valueWidth) / ROW_SCALE));
            List<FormattedCharSequence> lines = font.split(detail.text(), available);
            if (lines.size() > 2) lines = List.copyOf(lines.subList(0, 2));
            int textHeight = Math.max(font.lineHeight, (int) Math.ceil(Math.max(1, lines.size())
                    * (font.lineHeight + 1) * ROW_SCALE));
            int height = textHeight + (style == TrackerStyle.OVERVIEW ? 10 : 3);
            rows.add(new Row(detail, lines, height, valueWidth));
            rowHeight += height;
        }
        List<LaneRow> lanes = new ArrayList<>();
        int phaseHeight = 0;
        for (Lane lane : snapshot.lanes()) {
            String count = lane.summary().total() == 0 ? "—"
                    : lane.summary().completed() + "/" + lane.summary().total()
                    + " · " + Math.round(lane.summary().progress() * 100) + "%";
            int nameWidth = Math.max(10, (int) ((width - 31) / ROW_SCALE) - font.width(count) - 10);
            int height = lane.status() == null ? 29 : 40;
            lanes.add(new LaneRow(lane, StyledTextUtil.fitSingleLine(font, lane.name(), nameWidth), count, height));
            phaseHeight += height + 3;
        }
        int messageHeight = snapshot.details().isEmpty() ? 17 : 0;
        int moreHeight = snapshot.otherObjectives() > 0 ? 12 : 0;
        int bodyHeight = style == TrackerStyle.FOCUS ? 16 + rowHeight + messageHeight + moreHeight
                : 13 + phaseHeight + (snapshot.otherPhases() > 0 ? 12 : 0) + 22
                + rowHeight + messageHeight + moreHeight;
        layout = new Layout(snapshot, style, font, Language.getInstance(), width, List.copyOf(rows), List.copyOf(lanes),
                (style == TrackerStyle.FOCUS ? FOCUS_BODY_TOP : BODY_TOP) + bodyHeight + 5);
        return layout;
    }

    void render(GuiGraphics graphics, Font font, int panelWidth, int panelHeight, Snapshot snapshot,
                TrackerStyle style, QuestRuntimeData runtime, int theme, float alpha,
                float wipeAlpha, float wipeDrift) {
        int backgroundAlpha = (int) ((style == TrackerStyle.FOCUS ? 42 : 135) * alpha);
        graphics.fill(0, 0, panelWidth, panelHeight, color(style == TrackerStyle.FOCUS ? 0x080D14 : 0x101923, backgroundAlpha));
        if (style == TrackerStyle.FOCUS) {
            graphics.fill(0, 0, 1, panelHeight, color(theme, (int) (210 * alpha)));
            graphics.fill(1, 0, panelWidth, 1, color(theme, (int) (75 * alpha)));
        } else {
            graphics.fill(0, 0, panelWidth, 3, color(theme, (int) (240 * alpha)));
            graphics.fill(0, panelHeight - 1, panelWidth, panelHeight, color(0xFFFFFF, (int) (38 * alpha)));
        }
        float combined = alpha * wipeAlpha;
        int textAlpha = (int) (255 * combined);
        if (textAlpha <= 4) return;
        Layout current = layout(font, panelWidth, snapshot, style);
        if (runtime == null) {
            graphics.drawString(font, StyledTextUtil.fitSingleLine(font, snapshot.title(), panelWidth - 16),
                    8, 7, color(0xFFFFFF, textAlpha), true);
        } else {
            // Reuse the original title path, including icon, deadline, and urgency pulse.
            TrackerTitleWidget.renderTitle(graphics, runtime, 8, 7, alpha, wipeAlpha, font, panelWidth, 0);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(wipeDrift, 0, 0);
        try {
            int y;
            if (style == TrackerStyle.FOCUS) y = renderFocusHeader(graphics, current, theme, combined);
            else y = renderOverviewHeader(graphics, current, theme, combined);
            renderDetails(graphics, current, y, theme, combined);
        } finally {
            graphics.pose().popPose();
        }
    }

    private int renderFocusHeader(GuiGraphics graphics, Layout current, int theme, float alpha) {
        Font font = current.font();
        Snapshot snapshot = current.snapshot();
        TrackerStyleModel.Summary summary = snapshot.focus().summary();
        Component state = snapshot.focus().status() != null ? snapshot.focus().status()
                : summary.total() > 0 ? text("tracker_style.remaining", summary.remaining()) : Component.empty();
        int stateWidth = Math.min((current.width() - 24) / 2, (int) Math.ceil(font.width(state) * SMALL_SCALE));
        drawFit(graphics, font, snapshot.focus().name(), 8, FOCUS_BODY_TOP, current.width() - 24 - stateWidth,
                ROW_SCALE, color(theme, (int) (255 * alpha)));
        drawFit(graphics, font, state, current.width() - 8 - stateWidth, FOCUS_BODY_TOP + 1, stateWidth,
                SMALL_SCALE, color(0xAFBCC9, (int) (235 * alpha)));
        graphics.fill(8, FOCUS_BODY_TOP + 12, current.width() - 8, FOCUS_BODY_TOP + 13, color(0xFFFFFF, (int) (32 * alpha)));
        return FOCUS_BODY_TOP + 16;
    }

    private int renderOverviewHeader(GuiGraphics graphics, Layout current, int theme, float alpha) {
        int y = BODY_TOP;
        Font font = current.font();
        Snapshot snapshot = current.snapshot();
        drawFit(graphics, font, text("tracker_style.overview", snapshot.lanes().size() + snapshot.otherPhases()),
                8, y, current.width() - 16, SMALL_SCALE, color(0xB2C3D4, (int) (245 * alpha)));
        y += 13;
        for (LaneRow row : current.lanes()) {
            Lane lane = row.lane();
            int countWidth = (int) Math.ceil(font.width(row.count()) * ROW_SCALE);
            graphics.fill(6, y, current.width() - 6, y + row.height(),
                    color(lane.focused() ? theme : 0xFFFFFF, (int) ((lane.focused() ? 30 : 12) * alpha)));
            if (lane.focused()) graphics.fill(6, y, 8, y + row.height(), color(theme, (int) (235 * alpha)));
            drawScaled(graphics, font, row.name(), 13, y + 5, ROW_SCALE,
                    color(lane.focused() ? 0xF4F8FF : 0xAAB8C8, (int) (255 * alpha)));
            drawScaled(graphics, font, Component.literal(row.count()), current.width() - 13 - countWidth,
                    y + 5, ROW_SCALE, color(lane.focused() ? theme : 0xAEBAC6, (int) (255 * alpha)));
            if (lane.status() != null) drawFit(graphics, font, lane.status(), 13, y + 16,
                    current.width() - 26, SMALL_SCALE, color(0xB6D7B1, (int) (235 * alpha)));
            bar(graphics, 13, y + row.height() - 7, current.width() - 26, 2,
                    lane.summary().progress(), lane.focused() ? theme : 0x9CAFC0, alpha);
            y += row.height() + 3;
        }
        if (snapshot.otherPhases() > 0) {
            drawFit(graphics, font, text("parallel_more", snapshot.otherPhases()), 13, y, current.width() - 26,
                    SMALL_SCALE, color(0x94A5B7, (int) (230 * alpha)));
            y += 12;
        }
        graphics.fill(8, y + 3, current.width() - 8, y + 4, color(theme, (int) (90 * alpha)));
        drawFit(graphics, font, text("tracker_style.current").copy().append(" · ").append(snapshot.focus().name()),
                8, y + 9, current.width() - 16, SMALL_SCALE, color(theme, (int) (250 * alpha)));
        return y + 22;
    }

    private void renderDetails(GuiGraphics graphics, Layout current, int y, int theme, float alpha) {
        Font font = current.font();
        for (Row row : current.rows()) {
            var metric = row.detail().objective();
            int marker = color(theme, (int) (220 * alpha));
            if (metric.trackable()) {
                graphics.fill(8, y + 2, 12, y + 3, marker);
                graphics.fill(8, y + 5, 12, y + 6, marker);
                graphics.fill(8, y + 3, 9, y + 5, marker);
                graphics.fill(11, y + 3, 12, y + 5, marker);
            } else graphics.fill(9, y + 3, 11, y + 5, marker);
            for (int index = 0; index < row.lines().size(); index++) {
                drawScaled(graphics, font, row.lines().get(index), 17,
                        y + Math.round(index * (font.lineHeight + 1) * ROW_SCALE), ROW_SCALE,
                        color(0xD7E0EB, (int) (255 * alpha)));
            }
            if (!row.detail().value().isEmpty()) {
                int valueWidth = row.valueWidth() - 8;
                drawScaled(graphics, font, Component.literal(row.detail().value()), current.width() - 8 - valueWidth,
                        y, ROW_SCALE, color(0xAABBCD, (int) (250 * alpha)));
            }
            if (current.style() == TrackerStyle.OVERVIEW && metric.trackable()) {
                bar(graphics, 17, y + row.height() - 7, current.width() - 25, 2, metric.ratio(), theme, alpha);
            }
            y += row.height();
        }
        if (current.rows().isEmpty()) {
            drawFit(graphics, font, current.snapshot().emptyMessage(), 8, y + 2, current.width() - 16,
                    ROW_SCALE, color(0xC3D7C6, (int) (250 * alpha)));
            y += 17;
        }
        if (current.snapshot().otherObjectives() > 0) {
            drawFit(graphics, font, text("tracker_style.more_objectives", current.snapshot().otherObjectives()),
                    17, y, current.width() - 25, SMALL_SCALE, color(0x94A5B7, (int) (235 * alpha)));
        }
    }

    private static void bar(GuiGraphics graphics, int x, int y, int width, int height,
                            double progress, int theme, float alpha) {
        graphics.fill(x, y, x + width, y + height, color(0xFFFFFF, (int) (35 * alpha)));
        int filled = (int) Math.round(width * Math.max(0, Math.min(1, progress)));
        if (filled > 0) graphics.fill(x, y, x + filled, y + height, color(theme, (int) (225 * alpha)));
    }

    private static void drawFit(GuiGraphics graphics, Font font, Component text, int x, int y,
                                int width, float scale, int color) {
        drawScaled(graphics, font, StyledTextUtil.fitSingleLine(font, text, Math.max(1, (int) (width / scale))),
                x, y, scale, color);
    }

    private static void drawScaled(GuiGraphics graphics, Font font, Component text, int x, int y, float scale, int color) {
        drawScaled(graphics, font, text.getVisualOrderText(), x, y, scale, color);
    }

    private static void drawScaled(GuiGraphics graphics, Font font, FormattedCharSequence text,
                                    int x, int y, float scale, int color) {
        if ((color >>> 24) <= 4) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, true);
        graphics.pose().popPose();
    }

    private static int color(int rgb, int alpha) { return HudAnimUtil.withAlpha(rgb, Math.max(0, Math.min(255, alpha))); }
    private static Component text(String key, Object... args) { return Component.translatable("arc_quest.hud." + key, args); }
    private static Component previewText(String key) { return Component.translatable("gui.arc_quest.tracker_layout.preview." + key); }
}
