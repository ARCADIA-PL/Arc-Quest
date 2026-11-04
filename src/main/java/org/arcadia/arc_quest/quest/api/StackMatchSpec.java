package org.arcadia.arc_quest.quest.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Typed NBT identity; list matching is deliberately ordered and exact. */
public record StackMatchSpec(Mode mode, String snbt) {
    private static final java.util.Map<String, CompoundTag> PARSED =
            java.util.Collections.synchronizedMap(
                    new java.util.LinkedHashMap<String, CompoundTag>(128, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(
                                java.util.Map.Entry<String, CompoundTag> eldest) {
                            return size() > 2048;
                        }
                    });

    public enum Mode {
        ITEM,
        NBT_SUBSET,
        EXACT_NBT
    }

    public StackMatchSpec {
        mode = mode == null ? Mode.ITEM : mode;
        snbt = snbt == null ? "" : snbt;
        parse(snbt);
    }

    public static CompoundTag parse(String text) {
        return parsed(text).copy();
    }

    private static CompoundTag parsed(String text) {
        if (text == null || text.isBlank()) return new CompoundTag();
        if (text.length() > 32768)
            throw new IllegalArgumentException("Subject SNBT exceeds 32768 characters");
        CompoundTag cached = PARSED.get(text);
        if (cached != null) return cached;
        int depth = 0;
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (char c : text.toCharArray()) {
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quoted = false;
            } else if (c == '\"' || c == '\'') {
                quoted = true;
                quote = c;
            } else if (c == '{' || c == '[') {
                if (++depth > 64)
                    throw new IllegalArgumentException("Subject SNBT nesting exceeds 64");
            } else if (c == '}' || c == ']') depth--;
        }
        try {
            CompoundTag value = TagParser.parseTag(text);
            PARSED.put(text, value);
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid subject SNBT", e);
        }
    }

    public boolean matches(ItemStack expected, ItemStack actual) {
        if (expected.isEmpty() || actual.isEmpty() || !expected.is(actual.getItem())) return false;
        if (mode == Mode.ITEM) return true;
        CompoundTag wanted =
                snbt.isBlank()
                        ? (expected.hasTag() ? expected.getTag() : new CompoundTag())
                        : parsed(snbt);
        CompoundTag found = actual.hasTag() ? actual.getTag() : new CompoundTag();
        return mode == Mode.EXACT_NBT ? wanted.equals(found) : subset(wanted, found);
    }

    public boolean matches(ItemStack actual, ResourceLocation item) {
        return matches(new ItemStack(BuiltInRegistries.ITEM.get(item)), actual);
    }

    private static boolean subset(CompoundTag expected, CompoundTag actual) {
        for (String key : expected.getAllKeys()) {
            Tag e = expected.get(key), a = actual.get(key);
            if (a == null || e.getId() != a.getId()) return false;
            if (e instanceof CompoundTag c) {
                if (!subset(c, (CompoundTag) a)) return false;
            } else if (!e.equals(a)) return false;
        }
        return true;
    }
}
