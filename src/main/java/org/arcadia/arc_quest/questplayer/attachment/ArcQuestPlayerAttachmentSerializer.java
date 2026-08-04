package org.arcadia.arc_quest.questplayer.attachment;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.slf4j.Logger;

public final class ArcQuestPlayerAttachmentSerializer
        implements IAttachmentSerializer<CompoundTag, ArcQuestPlayerAttachment> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FORMAT_VERSION = 1;
    private static final String SNAPSHOT_KEY = "Snapshot";

    @Override
    public ArcQuestPlayerAttachment read(IAttachmentHolder holder,
                                         CompoundTag tag,
                                         HolderLookup.Provider provider) {
        if (tag.getInt("FormatVersion") != FORMAT_VERSION
                || !tag.contains(SNAPSHOT_KEY, Tag.TAG_COMPOUND)) {
            LOGGER.error("[ArcQuestPersistence] Ignoring invalid ArcQuest player attachment");
            return ArcQuestPlayerAttachment.empty();
        }

        long revision = Math.max(0L, tag.getLong("Revision"));
        long writtenAt = Math.max(0L, tag.getLong("WrittenAt"));
        return ArcQuestPlayerAttachment.of(revision, writtenAt, tag.getCompound(SNAPSHOT_KEY));
    }

    @Override
    public CompoundTag write(ArcQuestPlayerAttachment attachment, HolderLookup.Provider provider) {
        if (attachment == null || attachment.isEmpty()) return null;

        CompoundTag tag = new CompoundTag();
        tag.putInt("FormatVersion", FORMAT_VERSION);
        tag.putLong("Revision", attachment.revision());
        tag.putLong("WrittenAt", attachment.writtenAt());
        tag.put(SNAPSHOT_KEY, attachment.snapshot());
        return tag;
    }
}
