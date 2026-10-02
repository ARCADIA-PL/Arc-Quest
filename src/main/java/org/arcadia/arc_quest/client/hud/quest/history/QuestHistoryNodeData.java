package org.arcadia.arc_quest.client.hud.quest.history;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.VisualAsset;
import org.arcadia.arc_quest.quest.api.CollectionEntryDefinition;
import org.arcadia.arc_quest.quest.api.CollectionRequirementMode;
import org.arcadia.arc_quest.quest.data.CollectionBindingProgress;
import org.arcadia.arc_quest.quest.data.CollectionSheetProgress;

record QuestHistoryNodeData(
        String id,
        int x,
        int y,
        int depth,
        boolean completed,
        boolean active,
        boolean reached,
        Component displayName,
        PhaseDefinition phase,
        VisualAsset image,
        CollectionSheetProgress sheet,
        CollectionBindingProgress binding,
        CollectionEntryDefinition entry,
        boolean optional) {
    QuestHistoryNodeData(String id, int x, int y, int depth, boolean completed, boolean active, boolean reached,
            Component displayName, PhaseDefinition phase, VisualAsset image) {
        this(id, x, y, depth, completed, active, reached, displayName, phase, image, null, null, null, false);
    }
    boolean isBinding() { return binding != null; }
    String phaseId() { return phase.getPhaseId(); }

    Component renderTitle() {
        if (reached) return displayName;
        return Component.translatable("arc_quest.hud.history." + (isBinding() ? "collection_hidden" : "collection_waiting"));
    }

    int targetCount() {
        if (sheet != null) return sheet.target();
        if (!isBinding() || !reached) return 0;
        int required = (int) binding.requirements().stream().filter(requirement -> !requirement.optional()).count();
        var definition = phase.getCollectionSheet().getBinding(binding.bindingId());
        return required > 0 && definition != null && definition.getRequirementMode() == CollectionRequirementMode.ANY ? 1 : required;
    }

    int completedCount() {
        if (sheet != null) return sheet.completed();
        int target = targetCount();
        if (target == 0) return 0;
        if (completed) return target;
        int done = (int) binding.requirements().stream().filter(requirement -> !requirement.optional() && requirement.complete()).count();
        // A terminal run retains its achieved gate even if permanent knowledge grows later.
        return Math.min(done, target - 1);
    }
}
