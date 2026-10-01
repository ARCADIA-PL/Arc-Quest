package org.arcadia.arc_quest.integration.jei.network;

import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.testsupport.ItemStackTestData;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class JeiCatalogCodecTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftRegistryTestBootstrap.initialize();
    }

    @Test
    void roundTripsAlternativesNbtLargeQuantitiesAndRichComponents() {
        ItemStack template = new ItemStack(Items.DIAMOND_SWORD, 200);
        ItemStackTestData.setString(template, "ArcQTemplate", "named_reward");
        template.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("符文剑").withStyle(ChatFormatting.GOLD));
        template.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("A named ArcQ reward"))));
        template.set(net.minecraft.core.component.DataComponents.DAMAGE, 17);
        Component title = Component.translatableWithFallback("test.jei.title", "Source: %s",
                Component.literal("任务").withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.BOLD)
                .append(Component.literal("!"));
        Component detail = Component.literal("NBT template / one of these alternatives").withStyle(ChatFormatting.ITALIC);
        var input = new JeiIngredient(List.of(template, new ItemStack(Items.IRON_SWORD)), 512, true, detail);
        var output = new JeiIngredient(List.of(new ItemStack(Items.EMERALD)), 65_537, false, Component.literal("Reward"));
        var entry = new JeiCatalogEntry("test:source", JeiCatalogEntry.Kind.TRADE, title, List.of(input), List.of(output),
                List.of(Component.literal("说明\nsecond line").withStyle(ChatFormatting.DARK_GREEN)), "test:shop", "page/2");
        var result = JeiCatalogCodec.decode(JeiCatalogCodec.encode(List.of(entry)));
        assertEquals(1, result.size());
        var decoded = result.get(0);
        assertTrue(entry.sameContent(decoded), "Independent decoded stacks preserve semantic content");
        assertEquals(entry.id(), decoded.id());
        assertEquals(entry.kind(), decoded.kind());
        assertEquals(Component.Serializer.toJson(entry.title(), net.minecraft.core.RegistryAccess.EMPTY), Component.Serializer.toJson(decoded.title(), net.minecraft.core.RegistryAccess.EMPTY));
        assertEquals(Component.Serializer.toJson(entry.notes().get(0), net.minecraft.core.RegistryAccess.EMPTY), Component.Serializer.toJson(decoded.notes().get(0), net.minecraft.core.RegistryAccess.EMPTY));
        assertEquals("test:shop", decoded.navigationTarget());
        assertEquals("page/2", decoded.navigationDetail());
        assertEquals(512, decoded.inputs().get(0).amount(), "Quantity is a VarInt, not the NBT byte Count");
        assertEquals(65_537, decoded.outputs().get(0).amount());
        assertTrue(decoded.inputs().get(0).consumed());
        assertFalse(decoded.outputs().get(0).consumed());
        assertEquals(2, decoded.inputs().get(0).alternatives().size());
        ItemStack decodedTemplate = decoded.inputs().get(0).alternatives().get(0);
        assertTrue(decodedTemplate.is(Items.DIAMOND_SWORD));
        assertEquals(template.getComponents(), decodedTemplate.getComponents());
        assertEquals(17, decodedTemplate.getDamageValue());
        assertEquals(template.getHoverName(), decodedTemplate.getHoverName());
        assertEquals(1, decodedTemplate.getCount());
        assertEquals(200, template.getCount(), "Preparing a packet must not mutate its source template");
        ItemStackTestData.setString(decodedTemplate, "ArcQTemplate", "mutated");
        assertEquals("named_reward", ItemStackTestData.read(decoded.inputs().get(0).alternatives().get(0)).getString("ArcQTemplate"));
        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    @Test
    void semanticComparisonKeepsEqualCopiesAndDetectsQuantityNbtConsumptionAndDescriptionChanges() {
        var stack = new ItemStack(Items.DIAMOND_SWORD);
        ItemStackTestData.setString(stack, "binding", "known");
        var original = new JeiIngredient(List.of(stack), 256, true, Component.literal("Cost"));
        assertTrue(original.sameContent(new JeiIngredient(List.of(stack.copy()), 256, true, Component.literal("Cost"))));
        assertFalse(original.sameContent(new JeiIngredient(List.of(stack), 257, true, Component.literal("Cost"))));
        assertFalse(original.sameContent(new JeiIngredient(List.of(stack), 256, false, Component.literal("Cost"))));
        assertFalse(original.sameContent(new JeiIngredient(List.of(stack), 256, true, Component.literal("Changed"))));
        ItemStack changedNbt = stack.copy();
        ItemStackTestData.setString(changedNbt, "binding", "new");
        assertFalse(original.sameContent(new JeiIngredient(List.of(changedNbt), 256, true, Component.literal("Cost"))));
        assertFalse(original.sameContent(new JeiIngredient(List.of(new ItemStack(Items.IRON_SWORD)), 256, true, Component.literal("Cost"))));
        assertFalse(original.sameContent(null));
    }

    @Test
    void roundTripsNbtOnNonDamageableTemplate() {
        ItemStack template = new ItemStack(Items.PAPER, 80);
        assertFalse(template.isDamageableItem());
        CompoundTag predicate = new CompoundTag();
        predicate.putString("permission", "quest:completed");
        predicate.putIntArray("checksum", new int[]{1, 2, 3, 999});
        ItemStackTestData.setTag(template, "ArcQExactMatch", predicate);
        var ingredient = new JeiIngredient(List.of(template), 300, true, Component.literal("Named token"), true);
        var entry = new JeiCatalogEntry("test:token", JeiCatalogEntry.Kind.QUEST_REQUIREMENT, Component.literal("Token"),
                List.of(ingredient), List.of(), List.of(), "test:quest", "delivery");
        var decoded = JeiCatalogCodec.decode(JeiCatalogCodec.encode(List.of(entry))).get(0);
        assertEquals(ItemStackTestData.read(template), ItemStackTestData.read(decoded.inputs().get(0).alternatives().get(0)));
        assertEquals(300, decoded.inputs().get(0).amount());
        assertTrue(decoded.inputs().get(0).exactNbt());
        assertTrue(entry.sameContent(decoded));
    }

    @Test
    void roundTripsDynamicRegistryEnchantmentsAndProfileComponents() {
        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var sharpness = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
        var enchantments = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        enchantments.set(sharpness, 4);
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ArcQTester");
        profile.getProperties().put("textures", new com.mojang.authlib.properties.Property("textures", "skin-payload"));
        var head = new ItemStack(Items.PLAYER_HEAD);
        head.set(net.minecraft.core.component.DataComponents.PROFILE,
                new net.minecraft.world.item.component.ResolvableProfile(profile));
        var entry = new JeiCatalogEntry("test:components", JeiCatalogEntry.Kind.TRADE, Component.literal("Components"),
                List.of(new JeiIngredient(List.of(sword, head), 256, true, Component.empty(), true)),
                List.of(), List.of(), "test:shop", "entry");

        var decoded = JeiCatalogCodec.decode(JeiCatalogCodec.encode(List.of(entry), registries), registries).get(0);
        var alternatives = decoded.inputs().get(0).alternatives();
        assertEquals(4, alternatives.get(0).get(net.minecraft.core.component.DataComponents.ENCHANTMENTS).getLevel(sharpness));
        var decodedProfile = alternatives.get(1).get(net.minecraft.core.component.DataComponents.PROFILE).gameProfile();
        assertEquals(profile.getId(), decodedProfile.getId());
        assertEquals(profile.getProperties().get("textures"), decodedProfile.getProperties().get("textures"));
        assertTrue(entry.sameContent(decoded));
    }

    @Test
    void semanticEntryComparisonDetectsNavigationNotesAndSlotChanges() {
        var input = new JeiIngredient(List.of(new ItemStack(Items.EMERALD)), 5, true, Component.literal("Cost"));
        var original = new JeiCatalogEntry("test:stable", JeiCatalogEntry.Kind.TRADE, Component.literal("Title"),
                List.of(input), List.of(), List.of(Component.literal("Note")), "test:shop", "entry");
        assertTrue(original.sameContent(new JeiCatalogEntry(original.id(), original.kind(), original.title().copy(),
                List.of(new JeiIngredient(input.alternatives(), input.amount(), input.consumed(), input.description())),
                List.of(), original.notes(), original.navigationTarget(), original.navigationDetail())));
        for (JeiCatalogEntry changed : List.of(
                new JeiCatalogEntry(original.id(), original.kind(), original.title(), original.inputs(), original.outputs(), original.notes(), "test:other", original.navigationDetail()),
                new JeiCatalogEntry(original.id(), original.kind(), original.title(), original.inputs(), original.outputs(), original.notes(), original.navigationTarget(), "other_entry"),
                new JeiCatalogEntry(original.id(), original.kind(), original.title(), original.inputs(), original.outputs(), List.of(Component.literal("Changed")), original.navigationTarget(), original.navigationDetail()),
                new JeiCatalogEntry(original.id(), original.kind(), original.title(), List.of(new JeiIngredient(input.alternatives(), 6, true, input.description())), original.outputs(), original.notes(), original.navigationTarget(), original.navigationDetail()),
                new JeiCatalogEntry(original.id(), original.kind(), Component.literal("Other title"), original.inputs(), original.outputs(), original.notes(), original.navigationTarget(), original.navigationDetail()))) {
            assertFalse(original.sameContent(changed));
        }
        assertFalse(original.sameContent(null));
    }

    @Test
    void emptyCatalogRoundTripsAndTrailingBytesAreRejected() {
        byte[] bytes = JeiCatalogCodec.encode(List.of());
        assertEquals(List.of(), JeiCatalogCodec.decode(bytes));
        assertThrows(IllegalArgumentException.class, () -> JeiCatalogCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
    }

    @Test
    void rejectsDuplicateEntryIdsOnReceive() {
        var entry = emptyEntry("test:duplicate");
        byte[] bytes = JeiCatalogCodec.encode(List.of(entry, entry));
        assertThrows(IllegalArgumentException.class, () -> JeiCatalogCodec.decode(bytes));
    }

    @Test
    void rejectsOversizedAndNegativeEntryCountsBeforeAllocation() {
        for (int count : new int[]{-1, JeiCatalogCodec.MAX_ENTRIES + 1, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> JeiCatalogCodec.decode(packet(buf -> buf.writeVarInt(count))));
        }
        assertThrows(IllegalArgumentException.class, () -> JeiCatalogCodec.encode(
                Collections.nCopies(JeiCatalogCodec.MAX_ENTRIES + 1, emptyEntry("test:large"))));
        assertThrows(IllegalArgumentException.class, () -> JeiCatalogCodec.decode(new byte[JeiCatalogCodec.MAX_BYTES + 1]));
    }

    @Test
    void rejectsOversizedSlotsAlternativesAndNotes() {
        assertThrows(IllegalArgumentException.class, () -> JeiCatalogCodec.decode(packet(buf -> {
            entryHeader(buf);
            buf.writeVarInt(4097);
        })));
        assertThrows(IllegalArgumentException.class, () -> JeiCatalogCodec.decode(packet(buf -> {
            entryHeader(buf);
            buf.writeVarInt(1);
            buf.writeVarInt(1);
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            text(buf, Component.empty());
            buf.writeVarInt(65_537);
        })));
        assertThrows(IllegalArgumentException.class, () -> JeiCatalogCodec.decode(packet(buf -> {
            entryHeader(buf);
            buf.writeVarInt(0);
            buf.writeVarInt(0);
            buf.writeVarInt(4097);
        })));
    }

    @Test
    void rejectsZeroAndNegativeIngredientAmounts() {
        for (int amount : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> JeiCatalogCodec.decode(packet(buf -> {
                entryHeader(buf);
                buf.writeVarInt(1);
                buf.writeVarInt(amount);
                buf.writeBoolean(true);
                buf.writeBoolean(false);
                text(buf, Component.literal("Malformed amount"));
                buf.writeVarInt(0);
            })));
        }
    }

    @Test
    void rejectsNullComponentAndTruncatedCatalog() {
        assertThrows(IllegalArgumentException.class, () -> JeiCatalogCodec.decode(packet(buf -> {
            buf.writeVarInt(1);
            buf.writeUtf("test:invalid");
            buf.writeEnum(JeiCatalogEntry.Kind.GUIDE);
            buf.writeUtf("null");
        })));
        byte[] bytes = JeiCatalogCodec.encode(List.of(emptyEntry("test:short")));
        assertThrows(RuntimeException.class, () -> JeiCatalogCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
    }

    private static JeiCatalogEntry emptyEntry(String id) {
        return new JeiCatalogEntry(id, JeiCatalogEntry.Kind.GUIDE, Component.literal("Guide"),
                List.of(), List.of(), List.of(), "test:guide", "0");
    }

    private static void entryHeader(FriendlyByteBuf buf) {
        buf.writeVarInt(1);
        buf.writeUtf("test:malformed");
        buf.writeEnum(JeiCatalogEntry.Kind.TRADE);
        text(buf, Component.literal("Malformed"));
    }

    private static void text(FriendlyByteBuf buf, Component component) {
        buf.writeUtf(Component.Serializer.toJson(component, net.minecraft.core.RegistryAccess.EMPTY));
    }

    private static byte[] packet(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally { buf.release(); }
    }
}
