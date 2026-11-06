package org.arcadia.arc_quest.quest.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
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
        stack.setHoverName(Component.translatable("example.custom_sword"));
        stack.getOrCreateTag().putInt("example", 42);
        var document = new CollectionDetailDocument(Component.translatable("test.title"), List.of(
                new CollectionDetailDocument.Section(Component.translatable("test.cost"), Component.empty(), Component.translatable("test.hint"),
                        List.of(new CollectionDetailDocument.Line(List.of(stack), stack.getHoverName(), 129, 7))),
                CollectionDetailDocument.Section.itemGrid(Component.literal("Equipment"), List.of(stack))));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new S2CCollectionDetailPacket(request("sword", 1), document);
            S2CCollectionDetailPacket.encode(packet, buffer);
            var copy = S2CCollectionDetailPacket.decode(buffer);
            assertEquals(packet.request(), copy.request());
            assertEquals(document.title(), copy.document().title());
            var line = copy.document().sections().get(0).lines().get(0);
            assertEquals(129, line.required()); assertEquals(7, line.owned());
            assertEquals(stack.getTag(), line.alternatives().get(0).getTag());
            assertEquals(stack.getHoverName(), line.label());
            assertEquals(CollectionDetailDocument.Layout.ITEM_GRID, copy.document().sections().get(1).layout());
            assertEquals(stack.getTag(), copy.document().sections().get(1).lines().get(0).alternatives().get(0).getTag());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void providerAndWireCardinalityAreBounded() {
        var section = new CollectionDetailDocument.Section(Component.empty(), Component.empty(), Component.empty(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new CollectionDetailDocument(Component.empty(), Collections.nCopies(17, section)));
        assertThrows(IllegalArgumentException.class, () -> new CollectionDetailDocument.Line(List.of(), Component.empty(), -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CollectionDetailDocument.Line(List.of(), Component.empty(), 1, -2));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            C2SCollectionDetailRequest.encode(request("sword", 1), buffer);
            buffer.writeUtf(Component.Serializer.toJson(Component.empty())); buffer.writeVarInt(17);
            assertThrows(RuntimeException.class, () -> S2CCollectionDetailPacket.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void sourceCardsRetainQualityLinkAndIndependentSourceTooltip() {
        var link = new CollectionDetailDocument.Link(net.minecraft.resources.ResourceLocation.parse("test:gem"), "ancient");
        var line = new CollectionDetailDocument.Line(List.of(new ItemStack(Items.DIAMOND)), Component.literal("Diamond"),
                0, -1, List.of(Component.literal("Container: 1–3")), link);
        var document = new CollectionDetailDocument(Component.empty(), List.of(new CollectionDetailDocument.Section(
                Component.literal("Valuable"), Component.empty(), Component.empty(), List.of(line), CollectionDetailDocument.Layout.LARGE_ITEM_GRID)));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var request = new C2SCollectionDetailRequest(UUID.randomUUID(), 1, "test:q", "p", "b", "r", "ancient");
            S2CCollectionDetailPacket.encode(new S2CCollectionDetailPacket(request, document), buffer);
            var copy = S2CCollectionDetailPacket.decode(buffer);
            assertEquals(request, copy.request());
            assertEquals(link, copy.document().sections().get(0).lines().get(0).link());
            assertEquals(line.details(), copy.document().sections().get(0).lines().get(0).details());
        } finally { buffer.release(); }
        var cache = new ClientCollectionDetails();
        var old = cache.request(new ClientCollectionDetails.Key(1, 0, "test:q", "p", "b", "r", "epic"), 0);
        cache.request(new ClientCollectionDetails.Key(1, 0, "test:q", "p", "b", "r", "ancient"), 1);
        assertFalse(cache.accept(new S2CCollectionDetailPacket(old, document)));
        assertNull(cache.document());
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
    @Test void everyDetailLayoutRetainsStatusColorAndExactVariantPath() {
        var link = new CollectionDetailDocument.Link(net.minecraft.resources.ResourceLocation.parse("test:gem"), "mythic");
        var line = new CollectionDetailDocument.Line(List.of(new ItemStack(Items.AMETHYST_SHARD)), Component.literal("Flawless"),
                0, -1, List.of(Component.literal("Recipe materials")), link);
        var sections = Arrays.stream(CollectionDetailDocument.Layout.values()).map(layout -> new CollectionDetailDocument.Section(
                Component.literal("Section"), Component.literal("Cutting").withStyle(s -> s.withColor(0xD7C48D)),
                Component.literal("Verified source"), List.of(line), layout)).toList();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CCollectionDetailPacket.encode(new S2CCollectionDetailPacket(request("gem", 1),
                    new CollectionDetailDocument(Component.empty(), sections)), buffer);
            var decoded = S2CCollectionDetailPacket.decode(buffer).document().sections();
            assertEquals(sections.size(), decoded.size());
            for (int i = 0; i < sections.size(); i++) {
                assertEquals(sections.get(i).layout(), decoded.get(i).layout());
                assertEquals(0xD7C48D, decoded.get(i).status().getStyle().getColor().getValue());
                assertEquals(link, decoded.get(i).lines().get(0).link());
                assertEquals(line.details(), decoded.get(i).lines().get(0).details());
            }
        } finally { buffer.release(); }
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
