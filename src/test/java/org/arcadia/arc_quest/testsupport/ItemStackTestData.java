package org.arcadia.arc_quest.testsupport;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Test fixtures must replace immutable components when changing custom data. */
public final class ItemStackTestData {
    private ItemStackTestData() {}

    public static CompoundTag read(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void setString(ItemStack stack, String key, String value) {
        CompoundTag tag = read(stack);
        tag.putString(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void setTag(ItemStack stack, String key, Tag value) {
        CompoundTag tag = read(stack);
        tag.put(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
