package org.arcadia.arc_quest.npc.runtime;

import org.arcadia.arc_quest.core.identity.EntityRef;
import org.arcadia.arc_quest.npc.spec.NpcBindingSpec;
import org.arcadia.arc_quest.npc.spec.NpcSpec;

import javax.annotation.Nullable;
import java.util.List;

public record NpcResolution(
        EntityRef entityRef,
        @Nullable NpcSpec npcSpec,
        @Nullable NpcBindingSpec binding,
        @Nullable String dialogueId,
        List<CandidateTrace> candidates
) {

    public NpcResolution {
        candidates = List.copyOf(candidates);
    }

    public boolean matched() {
        return npcSpec != null && binding != null && dialogueId != null && !dialogueId.isBlank();
    }

    public enum Outcome {
        MATCHED,
        SPEC_CONDITION_REJECTED,
        BINDING_CONDITION_REJECTED,
        CODE_BINDING_RESERVED,
        DIALOGUE_ID_MISSING,
        NBT_VALUE_MISSING,
        EVALUATION_ERROR
    }

    public record CandidateTrace(
            String bindingId,
            int priority,
            @Nullable String dialogueId,
            Outcome outcome,
            String detail
    ) {
    }
}
