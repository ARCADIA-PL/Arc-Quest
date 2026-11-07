package org.arcadia.arc_quest.inventory;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoverableSettlementTest {
    @Test void firstAccessAlreadyReservesTheBuiltInDurableStorageParticipant() {
        // ResourceLocation is not a compile-time constant: it must exist before INSTANCE's constructor runs.
        assertNotNull(RecoverableSettlement.STORAGE);
        assertThrows(IllegalArgumentException.class, () -> RecoverableSettlement.INSTANCE.register(
                RecoverableSettlement.STORAGE, new RecoverableSettlement.Participant() {
                    public RecoverableSettlement.State state(MinecraftServer s, UUID o, String r, CompoundTag p) {
                        throw new AssertionError("Replacement participant must never run");
                    }
                    public void commit(MinecraftServer s, UUID o, String r, CompoundTag p) { throw new AssertionError(); }
                    public void cancel(MinecraftServer s, UUID o, String r, CompoundTag p) { throw new AssertionError(); }
                    public void finish(MinecraftServer s, UUID o, String r, CompoundTag p) { throw new AssertionError(); }
                }));
    }
}
