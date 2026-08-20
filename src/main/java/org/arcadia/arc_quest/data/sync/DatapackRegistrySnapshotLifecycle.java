package org.arcadia.arc_quest.data.sync;

import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.npc.runtime.NpcBindingRegistry;
import org.arcadia.arc_quest.quest.editor.QuestAuthoringSnapshotRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;

import java.util.List;
import java.util.Map;

public final class DatapackRegistrySnapshotLifecycle {

    private DatapackRegistrySnapshotLifecycle() {
    }

    public static void clear(long epoch) {
        QuestRegistry.replaceDatapackSnapshot(Map.of());
        QuestAuthoringSnapshotRegistry.replaceDatapackSnapshot(Map.of());
        DialogueRegistry.INSTANCE.replaceDatapackSnapshot(List.of(), Map.of(), Map.of(), epoch);
        NpcBindingRegistry.INSTANCE.replaceDatapackSnapshot(List.of(), epoch);
        TradeRegistry.replaceDatapackSnapshot(Map.of());
        GachaRegistry.replaceDatapackSnapshot(Map.of());
        GuideRegistry.replaceDatapackSnapshot(Map.of());
    }
}
