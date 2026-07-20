package org.arcadia.arc_quest.core.identity;

import java.util.Objects;
import java.util.UUID;

public record PlayerSessionRef(UUID playerUuid, long loginEpoch) {

    public PlayerSessionRef {
        Objects.requireNonNull(playerUuid, "playerUuid");
        if (loginEpoch <= 0L) {
            throw new IllegalArgumentException("loginEpoch must be positive");
        }
    }
}
