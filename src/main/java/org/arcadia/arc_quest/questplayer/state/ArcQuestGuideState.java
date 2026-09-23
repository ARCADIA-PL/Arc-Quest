package org.arcadia.arc_quest.questplayer.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ArcQuestGuideState {

    private final LinkedHashSet<ResourceLocation> unlockedGuides = new LinkedHashSet<>();
    private final LinkedHashSet<ResourceLocation> seenGuides = new LinkedHashSet<>();
    private final LinkedHashMap<ResourceLocation, Integer> guideProgress = new LinkedHashMap<>();
    private boolean dirty;

    public boolean isUnlocked(ResourceLocation guideId) {
        return unlockedGuides.contains(guideId);
    }

    public boolean isSeen(ResourceLocation guideId) {
        return seenGuides.contains(guideId);
    }

    public boolean unlock(ResourceLocation guideId) {
        boolean changed = unlockedGuides.add(Objects.requireNonNull(guideId, "guideId"));
        if (changed) dirty = true;
        return changed;
    }

    public boolean revokeUnlock(ResourceLocation guideId) {
        boolean changed = unlockedGuides.remove(guideId);
        changed |= seenGuides.remove(guideId);
        changed |= guideProgress.remove(guideId) != null;
        if (changed) dirty = true;
        return changed;
    }

    public boolean markSeen(ResourceLocation guideId) {
        boolean changed = seenGuides.add(Objects.requireNonNull(guideId, "guideId"));
        if (changed) dirty = true;
        return changed;
    }

    public boolean markAllUnlockedSeen() {
        boolean changed = seenGuides.addAll(unlockedGuides);
        if (changed) dirty = true;
        return changed;
    }

    public boolean clearSeen(ResourceLocation guideId) {
        boolean changed = seenGuides.remove(guideId);
        if (changed) dirty = true;
        return changed;
    }

    public Set<ResourceLocation> getUnlockedGuides() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(unlockedGuides));
    }

    public Set<ResourceLocation> getSeenGuides() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(seenGuides));
    }

    public int getProgress(ResourceLocation guideId) {
        return Math.max(0, guideProgress.getOrDefault(guideId, 0));
    }

    public boolean setProgress(ResourceLocation guideId, int pageIndex) {
        Objects.requireNonNull(guideId, "guideId");
        int normalized = Math.max(0, pageIndex);
        if (guideProgress.getOrDefault(guideId, 0) == normalized) return false;
        guideProgress.put(guideId, normalized);
        dirty = true;
        return true;
    }

    public Map<ResourceLocation, Integer> getAllProgress() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(guideProgress));
    }

    public void writeToRoot(CompoundTag root) {
        ListTag unlockedGuideList = new ListTag();
        for (ResourceLocation id : unlockedGuides) unlockedGuideList.add(StringTag.valueOf(id.toString()));
        root.put("UnlockedGuides", unlockedGuideList);

        ListTag seenGuideList = new ListTag();
        for (ResourceLocation id : seenGuides) seenGuideList.add(StringTag.valueOf(id.toString()));
        root.put("SeenGuides", seenGuideList);

        CompoundTag progressTag = new CompoundTag();
        guideProgress.forEach((id, page) -> progressTag.putInt(id.toString(), Math.max(0, page)));
        root.put("GuideProgress", progressTag);
    }

    public void readFromRoot(CompoundTag root) {
        unlockedGuides.clear();
        seenGuides.clear();
        guideProgress.clear();

        ListTag unlockedGuideList = root.getList("UnlockedGuides", 8);
        for (int i = 0; i < unlockedGuideList.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(unlockedGuideList.getString(i));
            if (id != null) unlockedGuides.add(id);
        }

        ListTag seenGuideList = root.getList("SeenGuides", 8);
        for (int i = 0; i < seenGuideList.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(seenGuideList.getString(i));
            if (id != null) seenGuides.add(id);
        }
        CompoundTag progressTag = root.getCompound("GuideProgress");
        for (String key : progressTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) guideProgress.put(id, Math.max(0, progressTag.getInt(key)));
        }
        dirty = false;
    }

    public void clear() {
        unlockedGuides.clear();
        seenGuides.clear();
        guideProgress.clear();
        dirty = true;
    }

    public void copyFrom(ArcQuestGuideState source) {
        Objects.requireNonNull(source, "source");
        if (source == this) return;
        unlockedGuides.clear();
        unlockedGuides.addAll(source.unlockedGuides);
        seenGuides.clear();
        seenGuides.addAll(source.seenGuides);
        guideProgress.clear();
        guideProgress.putAll(source.guideProgress);
        dirty = source.dirty;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }
}
