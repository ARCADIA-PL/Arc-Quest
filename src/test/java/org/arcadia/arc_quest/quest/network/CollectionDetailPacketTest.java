package org.arcadia.arc_quest.quest.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.CollectionDetailDocument;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionDetailPacketTest {
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }
    private C2SCollectionDetailRequest request(String binding, long epoch) {
        return new C2SCollectionDetailRequest(UUID.randomUUID(), epoch, "test:catalogue", "phase", binding, "run");
    }
    @Test void roundTripRetainsComponentsQuantitiesAndExactItemNbt() {
        var stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("example.custom_sword"));
        var custom = new CompoundTag(); custom.putInt("example", 42);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        var document = new CollectionDetailDocument(Component.translatable("test.title"), List.of(
                new CollectionDetailDocument.Section(Component.translatable("test.cost"), Component.empty(), Component.translatable("test.hint"),
                        List.of(new CollectionDetailDocument.Line(List.of(stack), stack.getHoverName(), 129, 7)))));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            var packet = new S2CCollectionDetailPacket(request("sword", 1), document);
            S2CCollectionDetailPacket.encode(packet, buffer);
            var copy = S2CCollectionDetailPacket.decode(buffer);
            assertEquals(packet.request(), copy.request());
            assertEquals(document.title(), copy.document().title());
            var line = copy.document().sections().get(0).lines().get(0);
            assertEquals(129, line.required()); assertEquals(7, line.owned());
            assertEquals(stack.getComponents(), line.alternatives().get(0).getComponents());
            assertEquals(stack.getHoverName(), line.label());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void providerAndWireCardinalityAreBounded() {
        var section = new CollectionDetailDocument.Section(Component.empty(), Component.empty(), Component.empty(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new CollectionDetailDocument(Component.empty(), Collections.nCopies(17, section)));
        assertThrows(IllegalArgumentException.class, () -> new CollectionDetailDocument.Line(List.of(), Component.empty(), -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CollectionDetailDocument.Line(List.of(), Component.empty(), 1, -2));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            C2SCollectionDetailRequest.encode(request("sword", 1), buffer);
            buffer.writeUtf(Component.Serializer.toJson(Component.empty(), buffer.registryAccess())); buffer.writeVarInt(17);
            assertThrows(RuntimeException.class, () -> S2CCollectionDetailPacket.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void delayedRepliesDoNotStarveAndOnlyCurrentSelectionCanBeApplied() {
        var cache = new ClientCollectionDetails();
        var key = new ClientCollectionDetails.Key(1, 0, "test:catalogue", "phase", "a", "run");
        var first = cache.request(key, 0);
        assertNull(cache.request(key, 1000));
        assertNull(cache.request(key, 4000));
        assertTrue(cache.accept(new S2CCollectionDetailPacket(first, CollectionDetailDocument.EMPTY)));
        var refresh = cache.request(key, 4001); assertNotNull(refresh);
        assertNull(cache.request(key, 4002));
        var nextKey = new ClientCollectionDetails.Key(1, 0, "test:catalogue", "phase", "b", "run");
        var next = cache.request(nextKey, 4002); assertNotNull(next);
        assertFalse(cache.accept(new S2CCollectionDetailPacket(refresh, CollectionDetailDocument.EMPTY)));
        assertNull(cache.document());
        assertTrue(cache.accept(new S2CCollectionDetailPacket(next, CollectionDetailDocument.EMPTY)));
    }
    @Test void runReloadSessionAndCloseInvalidateInFlightReplies() {
        for (var replacement : List.of(
                new ClientCollectionDetails.Key(2, 0, "test:q", "phase", "a", "run"),
                new ClientCollectionDetails.Key(1, 1, "test:q", "phase", "a", "run"),
                new ClientCollectionDetails.Key(1, 0, "test:q", "phase", "a", "new_run"))) {
            var cache = new ClientCollectionDetails();
            var old = cache.request(new ClientCollectionDetails.Key(1, 0, "test:q", "phase", "a", "run"), 0);
            var fresh = cache.request(replacement, 1);
            assertFalse(cache.accept(new S2CCollectionDetailPacket(old, CollectionDetailDocument.EMPTY)));
            cache.clear();
            assertFalse(cache.accept(new S2CCollectionDetailPacket(fresh, CollectionDetailDocument.EMPTY)));
        }
    }
}
