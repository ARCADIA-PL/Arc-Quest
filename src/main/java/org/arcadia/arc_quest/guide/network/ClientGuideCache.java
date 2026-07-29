package org.arcadia.arc_quest.guide.network;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.guide.GuideSplashRenderer;

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
    private final LinkedHashMap<ResourceLocation, Integer> guideProgress = new LinkedHashMap<>();
    private final Deque<PendingOpenRequest> pendingOpenRequests = new ArrayDeque<>();
    private boolean initialized;

    private ClientGuideCache() {
    }

    public void applySync(Collection<ResourceLocation> unlocked, Collection<ResourceLocation> seen) {
        applySync(unlocked, seen, Map.of());
    }

    public void applySync(Collection<ResourceLocation> unlocked, Collection<ResourceLocation> seen,
                          Map<ResourceLocation, Integer> progress) {
        LinkedHashSet<ResourceLocation> previousUnlocked = new LinkedHashSet<>(unlockedGuides);
        unlockedGuides.clear();
        seenGuides.clear();
        if (unlocked != null) {
            unlockedGuides.addAll(unlocked);
        }
        if (seen != null) {
            seenGuides.addAll(seen);
        }
        guideProgress.clear();
        if (progress != null) guideProgress.putAll(progress);
        if (initialized) {
            for (ResourceLocation guideId : unlockedGuides) {
                if (!previousUnlocked.contains(guideId)) GuideSplashRenderer.trigger(guideId);
            }
        }
        initialized = true;
    }

    public void applyLocalSeen(ResourceLocation guideId) {
        if (guideId != null) {
            seenGuides.add(guideId);
        }
    }

    public void applyLocalProgress(ResourceLocation guideId, int pageIndex) {
        if (guideId != null) guideProgress.put(guideId, Math.max(0, pageIndex));
    }

    public void requestOpen(ResourceLocation guideId, int initialPage, boolean markSeenOnClose) {
        if (guideId == null) return;
        PendingOpenRequest request = new PendingOpenRequest(
                guideId, Math.max(0, initialPage), markSeenOnClose);
        if (!pendingOpenRequests.contains(request)) pendingOpenRequests.addLast(request);
    }

    public Optional<PendingOpenRequest> consumePendingOpenRequest() {
        return Optional.ofNullable(pendingOpenRequests.pollFirst());
    }

    public Optional<PendingOpenRequest> getPendingOpenRequest() {
        return Optional.ofNullable(pendingOpenRequests.peekFirst());
    }

    public void clear() {
        unlockedGuides.clear();
        seenGuides.clear();
        guideProgress.clear();
        pendingOpenRequests.clear();
        initialized = false;
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

    public int getProgress(ResourceLocation guideId) {
        return Math.max(0, guideProgress.getOrDefault(guideId, 0));
    }

    public Map<ResourceLocation, Integer> getAllProgress() {
        return Collections.unmodifiableMap(guideProgress);
    }

    public boolean hasUnreadGuides() {
        for (ResourceLocation id : unlockedGuides) if (!seenGuides.contains(id)) return true;
        return false;
    }

    public record PendingOpenRequest(ResourceLocation guideId, int initialPage, boolean markSeenOnClose) {
    }
}
