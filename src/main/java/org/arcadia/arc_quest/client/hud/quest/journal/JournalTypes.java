package org.arcadia.arc_quest.client.hud.quest.journal;

import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;

public class JournalTypes {

    public enum Tab {ACTIVE, COMPLETED, FAILED}

    public record QuestListEntry(String questId, String displayName, QuestState state, QuestDefinition def) {
    }

    public static class ChoiceButtonRect {
        public int x, y, w, h, choiceIndex;
        public String phaseId;

        public ChoiceButtonRect(int x, int y, int w, int h, int idx, String phaseId) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            choiceIndex = idx;
            this.phaseId = phaseId;
        }
    }

    public static class PhaseTagRect {
        public int x, y, w, h;
        public String phaseId;

        public PhaseTagRect(int x, int y, int w, int h, String phaseId) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.phaseId = phaseId;
        }
    }
}