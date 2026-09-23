package org.arcadia.arc_quest.questplayer.state;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ArcQuestProfileState {

    private final Set<String> flags = new ObjectOpenHashSet<>();
    private final Map<String, Integer> variables;
    private boolean dirty;

    public ArcQuestProfileState(Map<String, Integer> variables) {
        this.variables = Objects.requireNonNull(variables);
    }

    public boolean setFlag(String flag) {
        boolean changed = flags.add(flag);
        if (changed) dirty = true;
        return changed;
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public boolean removeFlag(String flag) {
        boolean changed = flags.remove(flag);
        if (changed) dirty = true;
        return changed;
    }

    public Set<String> getAllFlags() {
        return Collections.unmodifiableSet(flags);
    }

    public int getVariable(String key) {
        return variables.getOrDefault(key, 0);
    }

    public void setVariable(String key, int value) {
        variables.put(key, value);
        dirty = true;
    }

    public void incrementVariable(String key, int amount) {
        variables.put(key, variables.getOrDefault(key, 0) + amount);
        dirty = true;
    }

    public Map<String, Integer> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public void writeToRoot(CompoundTag root) {
        ListTag flagList = new ListTag();
        for (String f : flags) flagList.add(StringTag.valueOf(f));
        root.put("Flags", flagList);

        CompoundTag varsTag = new CompoundTag();
        for (var e : variables.entrySet()) varsTag.putInt(e.getKey(), e.getValue());
        root.put("Variables", varsTag);
    }

    public void readFromRoot(CompoundTag root) {
        flags.clear();
        variables.clear();

        ListTag flagList = root.getList("Flags", 8);
        for (int i = 0; i < flagList.size(); i++) flags.add(flagList.getString(i));

        CompoundTag varsTag = root.getCompound("Variables");
        for (String key : varsTag.getAllKeys()) variables.put(key, varsTag.getInt(key));
        dirty = false;
    }

    public void clear() {
        flags.clear();
        variables.clear();
        dirty = true;
    }

    public void copyFrom(ArcQuestProfileState source) {
        Objects.requireNonNull(source, "source");
        if (source == this) return;
        flags.clear();
        flags.addAll(source.flags);
        variables.clear();
        variables.putAll(source.variables);
        dirty = source.dirty;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }
}
