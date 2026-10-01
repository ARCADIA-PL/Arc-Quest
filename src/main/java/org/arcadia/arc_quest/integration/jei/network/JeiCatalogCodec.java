package org.arcadia.arc_quest.integration.jei.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Bounded, registry-aware wire format; item counts are separate from stack NBT's byte-sized Count. */
public final class JeiCatalogCodec {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int CHUNK_BYTES = 64 * 1024;
    public static final int MAX_ENTRIES = 100_000;
    private static final int MAX_SLOTS = 4096, MAX_ALTERNATIVES = 65_536, MAX_NOTES = 4096;
    private static final int MAX_TEXT = 32_768, MAX_ID = 4096;
    private JeiCatalogCodec() {}

    public static byte[] encode(List<JeiCatalogEntry> entries) { return encode(entries, RegistryAccess.EMPTY); }

    public static byte[] encode(List<JeiCatalogEntry> entries, HolderLookup.Provider registries) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(256, MAX_BYTES));
        try {
            writeSize(buffer, entries.size(), MAX_ENTRIES);
            for (JeiCatalogEntry entry : entries) {
                buffer.writeUtf(entry.id(), MAX_ID);
                buffer.writeEnum(entry.kind());
                writeText(buffer, entry.title(), registries);
                writeIngredients(buffer, entry.inputs(), registries);
                writeIngredients(buffer, entry.outputs(), registries);
                writeSize(buffer, entry.notes().size(), MAX_NOTES);
                for (Component note : entry.notes()) writeText(buffer, note, registries);
                buffer.writeUtf(entry.navigationTarget(), MAX_ID);
                buffer.writeUtf(entry.navigationDetail(), MAX_ID);
                if (buffer.readableBytes() > MAX_BYTES) throw new IllegalArgumentException("JEI catalog exceeds 16 MiB");
            }
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally { buffer.release(); }
    }

    public static List<JeiCatalogEntry> decode(byte[] bytes) { return decode(bytes, RegistryAccess.EMPTY); }

    public static List<JeiCatalogEntry> decode(byte[] bytes, HolderLookup.Provider registries) {
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Oversized JEI catalog");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            int count = readSize(buffer, MAX_ENTRIES);
            List<JeiCatalogEntry> result = new ArrayList<>(count);
            var ids = new HashSet<String>();
            for (int index = 0; index < count; index++) {
                String id = buffer.readUtf(MAX_ID);
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate JEI entry id");
                var kind = buffer.readEnum(JeiCatalogEntry.Kind.class);
                Component title = readText(buffer, registries);
                var inputs = readIngredients(buffer, registries);
                var outputs = readIngredients(buffer, registries);
                int noteCount = readSize(buffer, MAX_NOTES);
                List<Component> notes = new ArrayList<>(noteCount);
                for (int note = 0; note < noteCount; note++) notes.add(readText(buffer, registries));
                result.add(new JeiCatalogEntry(id, kind, title, inputs, outputs, notes,
                        buffer.readUtf(MAX_ID), buffer.readUtf(MAX_ID)));
            }
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing JEI catalog bytes");
            return List.copyOf(result);
        } finally { buffer.release(); }
    }

    private static void writeIngredients(FriendlyByteBuf buffer, List<JeiIngredient> ingredients, HolderLookup.Provider registries) {
        writeSize(buffer, ingredients.size(), MAX_SLOTS);
        for (JeiIngredient ingredient : ingredients) {
            buffer.writeVarInt(ingredient.amount());
            buffer.writeBoolean(ingredient.consumed());
            buffer.writeBoolean(ingredient.exactNbt());
            writeText(buffer, ingredient.description(), registries);
            var alternatives = ingredient.alternatives();
            writeSize(buffer, alternatives.size(), MAX_ALTERNATIVES);
            // Inventory share tags may deliberately omit fields used by a configured
            // template. Catalog entries are already server-authorized display data.
            for (ItemStack stack : alternatives) buffer.writeNbt((CompoundTag) stack.save(registries));
        }
    }
    private static List<JeiIngredient> readIngredients(FriendlyByteBuf buffer, HolderLookup.Provider registries) {
        int count = readSize(buffer, MAX_SLOTS);
        List<JeiIngredient> ingredients = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int amount = buffer.readVarInt();
            boolean consumed = buffer.readBoolean();
            boolean exactNbt = buffer.readBoolean();
            Component description = readText(buffer, registries);
            int alternativesCount = readSize(buffer, MAX_ALTERNATIVES);
            List<ItemStack> alternatives = new ArrayList<>(alternativesCount);
            for (int alternative = 0; alternative < alternativesCount; alternative++) {
                CompoundTag serialized = buffer.readNbt();
                if (serialized == null) throw new IllegalArgumentException("Missing catalog item template");
                ItemStack stack = ItemStack.parse(registries, serialized).orElseThrow(() -> new IllegalArgumentException("Invalid catalog item components"));
                if (stack.isEmpty()) throw new IllegalArgumentException("Invalid catalog item template");
                alternatives.add(stack);
            }
            ingredients.add(new JeiIngredient(alternatives, amount, consumed, description, exactNbt));
        }
        return List.copyOf(ingredients);
    }
    private static void writeText(FriendlyByteBuf buffer, Component value, HolderLookup.Provider registries) { buffer.writeUtf(Component.Serializer.toJson(value, registries), MAX_TEXT); }
    private static Component readText(FriendlyByteBuf buffer, HolderLookup.Provider registries) {
        String json = buffer.readUtf(MAX_TEXT);
        Component value;
        try {
            value = Component.Serializer.fromJson(json, registries);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid catalog component", exception);
        }
        if (value == null) throw new IllegalArgumentException("Invalid catalog component");
        return value;
    }
    private static void writeSize(FriendlyByteBuf buffer, int size, int maximum) {
        if (size < 0 || size > maximum) throw new IllegalArgumentException("Catalog collection exceeds bounds");
        buffer.writeVarInt(size);
    }
    private static int readSize(FriendlyByteBuf buffer, int maximum) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximum) throw new IllegalArgumentException("Catalog collection exceeds bounds");
        return size;
    }
}
