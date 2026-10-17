package org.arcadia.arc_quest.dialogue.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.dialogue.registry.DialogueActionTypes;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DialogueCustomActionFailureTest {
    @Test void registeredCustomFailureStopsFollowingReward() {
        var type = ResourceLocation.fromNamespaceAndPath("arcq_test", "failed_custom");
        var failure = new IllegalStateException("Rejected ingredient submission");
        var rewards = new AtomicInteger();
        DialogueActionTypes.register(type, (player, session, data) -> { throw failure; });
        var result = DialogueActionSequence.execute(List.of("custom", "reward"), () -> true, action -> {
            if (action.equals("custom")) DialogueActionTypes.execute(type, null, null, new CompoundTag());
            else rewards.incrementAndGet();
        });
        assertEquals(DialogueActionSequence.Status.FAILED, result.status());
        assertSame(failure, result.failure());
        assertEquals(0, rewards.get());
    }

    @Test void missingCustomHandlerCannotSilentlySucceed() {
        var type = ResourceLocation.fromNamespaceAndPath("arcq_test", "unregistered_custom");
        assertThrows(IllegalStateException.class, () -> DialogueActionTypes.execute(type, null, null, new CompoundTag()));
    }
}
