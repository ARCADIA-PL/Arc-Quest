package org.arcadia.arc_quest.questplayer.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ArcQuestGuideState {

    private final LinkedHashSet<ResourceLocation> unlockedGuides = new LinkedHashSet<>();
    private final LinkedHashSet<ResourceLocation> seenGuides = new LinkedHashSet<>();
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
        if (changed) dirty = true;
        return changed;
    }

    public boolean markSeen(ResourceLocation guideId) {
        boolean changed = seenGuides.add(Objects.requireNonNull(guideId, "guideId"));
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

    public void writeToRoot(CompoundTag root) {
        ListTag unlockedGuideList = new ListTag();
        for (ResourceLocation id : unlockedGuides) unlockedGuideList.add(StringTag.valueOf(id.toString()));
        root.put("UnlockedGuides", unlockedGuideList);

        ListTag seenGuideList = new ListTag();
        for (ResourceLocation id : seenGuides) seenGuideList.add(StringTag.valueOf(id.toString()));
        root.put("SeenGuides", seenGuideList);
    }

    public void readFromRoot(CompoundTag root) {
        unlockedGuides.clear();
        seenGuides.clear();

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
        dirty = false;
    }

    public void clear() {
        unlockedGuides.clear();
        seenGuides.clear();
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }
}
