package org.arcadia.arc_quest.questplayer.attachment;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.arcadia.arc_quest.Arc_Quest;

public final class ArcQuestAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Arc_Quest.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ArcQuestPlayerAttachment>> PLAYER_DATA =
            ATTACHMENT_TYPES.register("player_data", () -> AttachmentType
                    .builder(ArcQuestPlayerAttachment::empty)
                    .serialize(new ArcQuestPlayerAttachmentSerializer())
                    .copyOnDeath()
                    .build());

    private ArcQuestAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
