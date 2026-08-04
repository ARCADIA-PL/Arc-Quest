package org.arcadia.arc_quest.questplayer;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.attachment.ArcQuestAttachments;
import org.arcadia.arc_quest.questplayer.attachment.ArcQuestPlayerAttachment;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;
import org.slf4j.Logger;

import java.util.UUID;

public final class AttachmentArcQuestPlayerRepository implements ArcQuestPlayerRepository {

    public static final AttachmentArcQuestPlayerRepository INSTANCE = new AttachmentArcQuestPlayerRepository();

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ArcQuestPlayerRepository legacyRepository = SavedDataArcQuestPlayerRepository.INSTANCE;

    private AttachmentArcQuestPlayerRepository() {
    }

    @Override
    public CompoundTag loadSnapshot(ServerPlayer player, UUID playerUuid) {
        CompoundTag attachmentSnapshot = player.getExistingData(ArcQuestAttachments.PLAYER_DATA)
                .map(attachment -> attachment.isEmpty() ? new CompoundTag() : attachment.snapshot())
                .orElseGet(CompoundTag::new);
        CompoundTag legacySnapshot = legacyRepository.loadSnapshot(player, playerUuid);
        CompoundTag selected = ArcQuestPlayerPersistenceMetadata.newer(attachmentSnapshot, legacySnapshot);

        long attachmentRevision = ArcQuestPlayerPersistenceMetadata.revision(attachmentSnapshot);
        long legacyRevision = ArcQuestPlayerPersistenceMetadata.revision(legacySnapshot);
        if (!selected.isEmpty() && (attachmentSnapshot.isEmpty() || legacyRevision > attachmentRevision)) {
            player.setData(ArcQuestAttachments.PLAYER_DATA, ArcQuestPlayerAttachment.fromSnapshot(selected));
            LOGGER.info("[ArcQuestPersistence] Migrated player {} from legacy SavedData revision {} to attachment",
                    player.getGameProfile().getName(), legacyRevision);
        }
        return selected;
    }

    @Override
    public void saveSnapshot(ServerPlayer player, UUID playerUuid, CompoundTag snapshot) {
        player.setData(ArcQuestAttachments.PLAYER_DATA, ArcQuestPlayerAttachment.fromSnapshot(snapshot));
    }

    @Override
    public void deleteSnapshot(ServerPlayer player, UUID playerUuid) {
        player.removeData(ArcQuestAttachments.PLAYER_DATA);
        legacyRepository.deleteSnapshot(player, playerUuid);
    }
}
