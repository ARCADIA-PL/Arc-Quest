package org.arcadia.arc_quest.guide.network;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

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
    private PendingOpenRequest pendingOpenRequest;

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

    public void applyLocalSeen(ResourceLocation guideId) {
        if (guideId != null) {
            seenGuides.add(guideId);
        }
    }

    public void requestOpen(ResourceLocation guideId, int initialPage, boolean markSeenOnClose) {
        pendingOpenRequest = new PendingOpenRequest(guideId, Math.max(0, initialPage), markSeenOnClose);
    }

    public Optional<PendingOpenRequest> consumePendingOpenRequest() {
        PendingOpenRequest request = pendingOpenRequest;
        pendingOpenRequest = null;
        return Optional.ofNullable(request);
    }

    public Optional<PendingOpenRequest> getPendingOpenRequest() {
        return Optional.ofNullable(pendingOpenRequest);
    }

    public void clear() {
        unlockedGuides.clear();
        seenGuides.clear();
        pendingOpenRequest = null;
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

    public record PendingOpenRequest(ResourceLocation guideId, int initialPage, boolean markSeenOnClose) {
    }
}
