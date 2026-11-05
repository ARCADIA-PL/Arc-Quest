package org.arcadia.arc_quest.integration.storage;

import net.minecraft.nbt.CompoundTag;

/** Implemented on the optional network, serialized in the same NBT as its resources. */
public interface StoragePaymentReceipts {
    String TAG = "ArcQuestPayments";
    CompoundTag arcQuest$payments();
}
