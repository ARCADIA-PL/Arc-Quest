package org.arcadia.arc_quest.questplayer;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.capability.ArcQuestCapabilities;
import org.arcadia.arc_quest.questplayer.capability.ArcQuestPlayerCapability;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;
import org.slf4j.Logger;

import java.util.UUID;

public final class CapabilityArcQuestPlayerRepository implements ArcQuestPlayerRepository {

    public static final CapabilityArcQuestPlayerRepository INSTANCE = new CapabilityArcQuestPlayerRepository();

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ArcQuestPlayerRepository legacyRepository = SavedDataArcQuestPlayerRepository.INSTANCE;

    private CapabilityArcQuestPlayerRepository() {
    }

    @Override
    public CompoundTag loadSnapshot(ServerPlayer player, UUID playerUuid) {
        ArcQuestPlayerCapability capability = resolveCapability(player);
        CompoundTag capabilitySnapshot = capability == null ? new CompoundTag() : capability.snapshot();
        CompoundTag legacySnapshot = legacyRepository.loadSnapshot(player, playerUuid);
        CompoundTag selected = ArcQuestPlayerPersistenceMetadata.newer(capabilitySnapshot, legacySnapshot);

        long legacyRevision = ArcQuestPlayerPersistenceMetadata.revision(legacySnapshot);
        if (capability != null && !selected.isEmpty()
                && (capability.isEmpty()
                || ArcQuestPlayerPersistenceMetadata.compare(legacySnapshot, capabilitySnapshot) > 0)) {
            capability.replaceSnapshot(selected);
            LOGGER.info("[ArcQuestPersistence] Migrated player {} from legacy SavedData revision {} to capability",
                    player.getGameProfile().getName(), legacyRevision);
        }
        return selected;
    }

    @Override
    public void saveSnapshot(ServerPlayer player, UUID playerUuid, CompoundTag snapshot) {
        ArcQuestPlayerCapability capability = resolveCapability(player);
        if (capability != null) {
            capability.replaceSnapshot(snapshot);
            return;
        }

        legacyRepository.saveSnapshot(player, playerUuid, snapshot);
        LOGGER.error("[ArcQuestPersistence] Player capability missing for {}; saved to legacy SavedData fallback",
                player.getGameProfile().getName());
    }

    @Override
    public void deleteSnapshot(ServerPlayer player, UUID playerUuid) {
        ArcQuestPlayerCapability capability = resolveCapability(player);
        if (capability != null) capability.clear();
        legacyRepository.deleteSnapshot(player, playerUuid);
    }

    private static ArcQuestPlayerCapability resolveCapability(ServerPlayer player) {
        return player.getCapability(ArcQuestCapabilities.PLAYER_DATA).resolve().orElse(null);
    }
}
