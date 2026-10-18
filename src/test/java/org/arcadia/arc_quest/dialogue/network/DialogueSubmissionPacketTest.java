package org.arcadia.arc_quest.dialogue.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.dialogue.api.DialogueSubmissionDefinition;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DialogueSubmissionPacketTest {
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void bundleRoundTripsMissingIngredientsAndExactCounts() {
        var original = new S2CDialogueSubmissionPacket(UUID.randomUUID(), 17, UUID.randomUUID(),
                Component.literal("Recipe"), List.of(
                new S2CDialogueSubmissionPacket.Row("first", new ItemStack(Items.DIAMOND), 4, 2),
                new S2CDialogueSubmissionPacket.Row("missing", new ItemStack(Items.IRON_INGOT), 1, 0)),
                false, true, true);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.encode(buffer);
            var decoded = S2CDialogueSubmissionPacket.decode(buffer);
            assertTrue(decoded.bundle());
            assertTrue(decoded.rejected());
            assertEquals(5, decoded.requiredTotal());
            assertEquals(0, decoded.rows().get(1).canSubmit());
            assertEquals(4, decoded.rows().get(0).required());
            assertEquals(original.token(), decoded.token());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void originalSampleConstructorStillLimitsTheWholeCatalogueToOne() {
        var packet = new S2CDialogueSubmissionPacket(UUID.randomUUID(), 1, UUID.randomUUID(), Component.literal("Samples"),
                List.of(new S2CDialogueSubmissionPacket.Row("a", new ItemStack(Items.DIAMOND), 1, 1),
                        new S2CDialogueSubmissionPacket.Row("b", new ItemStack(Items.IRON_INGOT), 1, 1)), false, false);
        assertFalse(packet.bundle());
        assertEquals(1, packet.requiredTotal());
        assertNull(new DialogueSubmissionDefinition("test:old", Component.literal("Old"), List.of()).bundle());
    }

    @Test void invalidAndDuplicateIngredientsCannotCreateAmbiguousPayments() {
        var ingredient = new DialogueSubmissionDefinition.Ingredient("minecraft:diamond", 1);
        assertThrows(IllegalArgumentException.class, () -> new DialogueSubmissionDefinition.Ingredient("minecraft:diamond", 0));
        assertThrows(IllegalArgumentException.class, () -> new DialogueSubmissionDefinition.Ingredient("minecraft:diamond", 4097));
        assertThrows(IllegalArgumentException.class, () -> new DialogueSubmissionDefinition.Bundle(List.of(), p -> true, List.of(), "done"));
        assertThrows(IllegalArgumentException.class, () -> new DialogueSubmissionDefinition.Bundle(
                List.of(ingredient, ingredient), p -> true, List.of(), "done"));
    }

    @Test void invalidWireQuantitiesCannotEnableOverfilledOrNegativeRows() {
        assertThrows(IllegalArgumentException.class, () -> new S2CDialogueSubmissionPacket.Row("a", ItemStack.EMPTY, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new S2CDialogueSubmissionPacket.Row("a", ItemStack.EMPTY, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new S2CDialogueSubmissionPacket.Row("a", ItemStack.EMPTY, 1, -1));
    }
}
