package org.arcadia.arc_quest.client.hud.quest.journal.history;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.spec.io.QuestTextComponentCodec;

import java.util.Objects;

public final class QuestChangeHistoryEntry {
    public String id = "";
    public long timeMs = 0L;
    public String worldKey = "default";
    public String playerKey = "local";

    public String questId = "";
    public String questName = "";
    /** Unresolved author text captured at the time of the event; old logs keep their string fallback. */
    public String questNameComponentJson;
    public String phaseId = "";
    public String phaseName = "";
    public String phaseNameComponentJson;
    public String objectiveId = "";
    public String rewardId = "";
    public String categoryId = "";

    public QuestChangeHistoryType type = QuestChangeHistoryType.SYSTEM_SYNC;
    public QuestChangeHistoryCategory category = QuestChangeHistoryCategory.SYSTEM;

    public String title = "";
    public String detail = "";
    public String detailComponentJson;
    public String beforeValue = "";
    public String afterValue = "";

    public int themeColor = 0x90A4AE;
    public int sortPriority = 0;

    private transient TextSnapshot questNameSnapshot;
    private transient TextSnapshot phaseNameSnapshot;
    private transient TextSnapshot detailSnapshot;

    public void setQuestName(Component component) {
        Component text = component == null ? Component.empty() : component;
        questName = text.getString();
        questNameComponentJson = QuestTextComponentCodec.encode(text);
    }

    public void setPhaseName(Component component) {
        Component text = component == null ? Component.empty() : component;
        phaseName = text.getString();
        phaseNameComponentJson = QuestTextComponentCodec.encode(text);
    }

    public void setDetail(Component component) {
        Component text = component == null ? Component.empty() : component;
        detail = text.getString();
        detailComponentJson = QuestTextComponentCodec.encode(text);
    }

    public Component questNameComponent() {
        if (questNameSnapshot == null) questNameSnapshot = new TextSnapshot();
        return questNameSnapshot.resolve(questNameComponentJson, questName);
    }

    public Component phaseNameComponent() {
        if (phaseNameSnapshot == null) phaseNameSnapshot = new TextSnapshot();
        return phaseNameSnapshot.resolve(phaseNameComponentJson, phaseName);
    }

    public Component detailComponent() {
        if (detailSnapshot == null) detailSnapshot = new TextSnapshot();
        return detailSnapshot.resolve(detailComponentJson, detail);
    }

    public String dedupeKey() {
        return type.name() + "|" + questId + "|" + phaseId + "|" + objectiveId + "|" + rewardId + "|" + categoryId + "|" + afterValue;
    }

    private static final class TextSnapshot {
        private String json;
        private String fallback;
        private Component component;

        Component resolve(String json, String fallback) {
            if (component == null || !Objects.equals(this.json, json) || !Objects.equals(this.fallback, fallback)) {
                this.json = json;
                this.fallback = fallback;
                component = Component.literal(fallback == null ? "" : fallback);
                if (json != null && !json.isBlank()) {
                    try {
                        component = QuestTextComponentCodec.decode(json);
                    } catch (IllegalArgumentException ignored) {
                        // A damaged text snapshot must not discard other fields or the entire history file.
                    }
                }
            }
            return component.copy();
        }
    }
}
