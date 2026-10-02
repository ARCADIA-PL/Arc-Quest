package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.spec.*;
import java.lang.reflect.Field;
import java.util.List;

/** Authoring policy for unified collection fields; old documents keep their legacy edit surface. */
final class CollectionEditorSchema {
    private CollectionEditorSchema() { }

    static boolean visible(Object owner, Field field) {
        if (owner instanceof CollectionEntrySpecData entry && entry.gameplayVersion == 2)
            return !List.of("researchObjectives", "researchAfterDiscovery").contains(field.getName());
        return true;
    }

    static String label(Object owner, String field) {
        if (!(owner instanceof CollectionEntrySpecData || owner instanceof EntryRequirementBindingSpecData
                || owner instanceof CollectionOutcomeSpecData || owner instanceof CollectionEntryRewardSpecData
                || owner instanceof CollectionRecordRequirementSpecData || owner instanceof CollectionContentBlockSpecData)) return field;
        if (!List.of("gameplayVersion", "outcomes", "outcomeId", "outcomeIds", "objectiveIds", "recordRequirements",
                "legacyResearchObjectives", "legacyResearchOutcomeMappings", "previewVisibility", "rewards", "revealStepId").contains(field)) return field;
        return Component.translatable("arc_quest.editor.collection.field." + field).getString();
    }

    static String hintKey(Object value, Object context, String field) {
        if (value instanceof CollectionEntrySpecData entry)
            return entry.gameplayVersion == 2 ? "entry" : "legacy";
        if (value instanceof EntryRequirementBindingSpecData) return "binding";
        if (value instanceof CollectionOutcomeSpecData || "outcomes".equals(field)) return "outcome";
        if ("outcomeIds".equals(field)) return "source";
        if ("legacyResearchObjectives".equals(field) || "legacyResearchOutcomeMappings".equals(field)) return "migration";
        if (value instanceof CollectionEntryRewardSpecData || "rewards".equals(field)
                && (context instanceof CollectionEntrySpecData || context instanceof EntryRequirementBindingSpecData)) return "reward";
        return "";
    }

    static void initializeNewElement(Object value, Object listOwner) {
        if (value instanceof CollectionEntrySpecData entry) entry.gameplayVersion = 2;
        if (value instanceof CollectionEntryRewardSpecData reward) {
            reward.previewVisibility = "PUBLIC";
            if (listOwner instanceof EntryRequirementBindingSpecData) reward.trigger = "BINDING_COMPLETE";
        }
    }
}
