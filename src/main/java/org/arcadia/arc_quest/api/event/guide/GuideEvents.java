package org.arcadia.arc_quest.api.event.guide;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;
import org.arcadia.arc_quest.guide.api.GuideDefinition;

import java.util.Set;

/** Guide 玩家状态事件集合。 */
public final class GuideEvents {
    private GuideEvents() {
    }

    public abstract static class GuideEvent extends Event {
        private final ServerPlayer player;
        private final ResourceLocation guideId;
        private final GuideDefinition guide;

        protected GuideEvent(ServerPlayer player, ResourceLocation guideId, GuideDefinition guide) {
            this.player = player;
            this.guideId = guideId;
            this.guide = guide;
        }

        public ServerPlayer getPlayer() { return player; }
        public ResourceLocation getGuideId() { return guideId; }
        public GuideDefinition getGuide() { return guide; }
    }

    /** Guide 首次解锁后触发。 */
    public static final class Unlocked extends GuideEvent {
        private final UnlockSource source;

        public Unlocked(ServerPlayer player, ResourceLocation guideId,
                        GuideDefinition guide, UnlockSource source) {
            super(player, guideId, guide);
            this.source = source;
        }

        public UnlockSource getSource() { return source; }
    }

    /** Guide 首次标记为已读后触发。 */
    public static final class Seen extends GuideEvent {
        public Seen(ServerPlayer player, ResourceLocation guideId, GuideDefinition guide) {
            super(player, guideId, guide);
        }
    }

    /** Guide 阅读页进度发生实际变化后触发。 */
    public static final class ProgressChanged extends GuideEvent {
        private final int oldPageIndex;
        private final int newPageIndex;

        public ProgressChanged(ServerPlayer player, ResourceLocation guideId, GuideDefinition guide,
                               int oldPageIndex, int newPageIndex) {
            super(player, guideId, guide);
            this.oldPageIndex = oldPageIndex;
            this.newPageIndex = newPageIndex;
        }

        public int getOldPageIndex() { return oldPageIndex; }
        public int getNewPageIndex() { return newPageIndex; }
        public boolean hasReachedFinalPage() {
            return newPageIndex >= Math.max(0, getGuide().getPageCount() - 1);
        }
    }

    /** “全部已读”操作实际更新状态后触发。 */
    public static final class MarkedAllSeen extends Event {
        private final ServerPlayer player;
        private final Set<ResourceLocation> guideIds;

        public MarkedAllSeen(ServerPlayer player, Set<ResourceLocation> guideIds) {
            this.player = player;
            this.guideIds = Set.copyOf(guideIds);
        }

        public ServerPlayer getPlayer() { return player; }
        public Set<ResourceLocation> getGuideIds() { return guideIds; }
        public int getChangedCount() { return guideIds.size(); }
    }

    public enum UnlockSource {
        DIRECT,
        ELIGIBILITY,
        BULK
    }
}
