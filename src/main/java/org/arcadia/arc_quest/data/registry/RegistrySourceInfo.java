package org.arcadia.arc_quest.data.registry;

import org.jetbrains.annotations.Nullable;

public record RegistrySourceInfo(
        RegistrySourceType sourceType,
        String sourceId,
        int loadOrder,
        @Nullable String ignoredReason
) {
}
