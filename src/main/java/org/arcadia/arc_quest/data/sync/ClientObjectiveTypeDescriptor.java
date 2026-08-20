package org.arcadia.arc_quest.data.sync;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveType;

public record ClientObjectiveTypeDescriptor(ResourceLocation id, boolean counting, boolean builtin,
                                            String displayKey, boolean requireTargetId,
                                            String defaultTargetKind) {
    public ClientObjectiveTypeDescriptor {
        if (id == null) throw new IllegalArgumentException("Objective type id must not be null");
        displayKey = displayKey == null ? "" : displayKey;
        defaultTargetKind = defaultTargetKind == null ? "generic" : defaultTargetKind;
    }

    public static ClientObjectiveTypeDescriptor from(ObjectiveType type) {
        return new ClientObjectiveTypeDescriptor(type.getId(), type.isCounting(), type.isBuiltin(),
                type.getDisplayKey(), type.requiresTargetId(), type.getDefaultTargetKind());
    }

    public ObjectiveType createClientType() {
        return new ObjectiveType(id, counting, builtin, displayKey, requireTargetId, defaultTargetKind);
    }
}
