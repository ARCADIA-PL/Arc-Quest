package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Guide 客户端状态镜像缓存。
 *
 * <p>UI 层必须只从这里读取 Guide 解锁/已读状态，
 * 不直接伪造本地权威状态。
 */
public final class ClientGuideCache {

    public static final ClientGuideCache INSTANCE = new ClientGuideCache();

    private final LinkedHashSet<ResourceLocation> unlockedGuides = new LinkedHashSet<>();
    private final LinkedHashSet<ResourceLocation> seenGuides = new LinkedHashSet<>();

    private ClientGuideCache() {
    }

    public void applySync(Collection<ResourceLocation> unlocked, Collection<ResourceLocation> seen) {
        unlockedGuides.clear();
        seenGuides.clear();
        if (unlocked != null) {
            unlockedGuides.addAll(unlocked);
        }
        if (seen != null) {
            seenGuides.addAll(seen);
        }
    }

    public void clear() {
        unlockedGuides.clear();
        seenGuides.clear();
    }

    public boolean isUnlocked(ResourceLocation guideId) {
        return unlockedGuides.contains(guideId);
    }

    public boolean isSeen(ResourceLocation guideId) {
        return seenGuides.contains(guideId);
    }

    public Set<ResourceLocation> getUnlockedGuides() {
        return Collections.unmodifiableSet(unlockedGuides);
    }

    public Set<ResourceLocation> getSeenGuides() {
        return Collections.unmodifiableSet(seenGuides);
    }
}
