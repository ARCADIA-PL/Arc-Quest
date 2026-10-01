package org.arcadia.arc_quest.client.hud.quest.tracker;

import java.util.ArrayList;
import java.util.List;

/** Pure selection and aggregation rules; indices always refer to the source phase. */
public final class TrackerStyleModel {
    public static final int DETAIL_LIMIT = 3;
    public static final int PHASE_LIMIT = 4;

    private TrackerStyleModel() {}

    public record Objective(int index, int progress, int required, boolean numeric,
                            boolean trackable, boolean hidden) {
        public Objective {
            progress = Math.max(0, progress);
            required = Math.max(1, required);
        }
        public boolean complete() { return trackable && progress >= required; }
        public double ratio() {
            if (!trackable) return 0;
            if (!numeric) return complete() ? 1 : 0;
            return Math.min(1.0, (double) progress / required);
        }
    }

    public record Summary(int completed, int total, double progress) {
        public int remaining() { return total - completed; }
    }

    /** Every visible objective contributes equal weight, regardless of its item count. */
    public static Summary summarize(List<Objective> objectives) {
        int completed = 0, total = 0;
        double progress = 0;
        for (Objective objective : objectives) {
            if (objective.hidden() || !objective.trackable()) continue;
            total++;
            if (objective.complete()) completed++;
            progress += objective.ratio();
        }
        return new Summary(completed, total, total == 0 ? 0 : progress / total);
    }

    /** Completed rows disappear and the next source rows move into the small checklist. */
    public static List<Objective> unfinished(List<Objective> objectives, int limit) {
        if (limit <= 0) return List.of();
        List<Objective> result = new ArrayList<>(Math.min(limit, objectives.size()));
        for (Objective objective : objectives) {
            if (objective.hidden() || objective.complete()) continue;
            result.add(objective);
            if (result.size() == limit) break;
        }
        return List.copyOf(result);
    }

    public static int unfinishedCount(List<Objective> objectives) {
        int total = 0;
        for (Objective objective : objectives) {
            if (!objective.hidden() && !objective.complete()) total++;
        }
        return total;
    }

    /** Preserve lane ordering, but always include the selected lane in a bounded HUD. */
    public static List<Integer> phaseWindow(int count, int focusedIndex, int limit) {
        int visible = Math.min(Math.max(0, count), Math.max(0, limit));
        if (visible == 0) return List.of();
        int focus = Math.max(0, Math.min(count - 1, focusedIndex));
        int start = focus >= visible ? Math.min(count - visible, focus - visible + 1) : 0;
        List<Integer> result = new ArrayList<>(visible);
        for (int i = 0; i < visible; i++) result.add(start + i);
        return List.copyOf(result);
    }
}
