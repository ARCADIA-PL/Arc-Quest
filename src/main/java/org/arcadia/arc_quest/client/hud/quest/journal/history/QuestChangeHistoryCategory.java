package org.arcadia.arc_quest.client.hud.quest.journal.history;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudText;

import java.util.Locale;

public enum QuestChangeHistoryCategory {
    QUEST("QUEST"),
    PHASE("PHASE"),
    OBJECTIVE("OBJ"),
    COLLECTION("COLL"),
    REWARD("REWARD"),
    SYSTEM("SYS");

    private final String shortLabel;

    QuestChangeHistoryCategory(String shortLabel) {
        this.shortLabel = shortLabel;
    }

    public String shortLabel() {
        return shortLabel;
    }

    public Component shortLabelComponent() {
        return HudText.of("history.category." + name().toLowerCase(Locale.ROOT));
    }
}
