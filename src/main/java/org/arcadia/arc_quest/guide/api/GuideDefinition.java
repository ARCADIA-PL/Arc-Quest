package org.arcadia.arc_quest.guide.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.ICondition;

import javax.annotation.Nullable;
import java.util.*;

public final class GuideDefinition {

    private final ResourceLocation id;
    private final GuideCategory category;
    private final GuideText title;
    private final int sortOrder;
    private final boolean hidden;
    private final boolean repeatablePopup;
    private final List<ICondition> unlockConditions;
    private final List<GuidePageDefinition> pages;

    public GuideDefinition(ResourceLocation id,
                           GuideCategory category,
                           GuideText title,
                           int sortOrder,
                           boolean hidden,
                           boolean repeatablePopup,
                           List<ICondition> unlockConditions,
                           List<GuidePageDefinition> pages) {
        this.id = Objects.requireNonNull(id, "id");
        this.category = Objects.requireNonNull(category, "category");
        this.title = Objects.requireNonNull(title, "title");
        this.sortOrder = sortOrder;
        this.hidden = hidden;
        this.repeatablePopup = repeatablePopup;
        this.unlockConditions = Collections.unmodifiableList(List.copyOf(unlockConditions == null ? List.of() : unlockConditions));
        this.pages = Collections.unmodifiableList(List.copyOf(Objects.requireNonNull(pages, "pages")));
        if (this.pages.isEmpty()) {
            throw new IllegalArgumentException("Guide '" + id + "' must contain at least one page");
        }
    }

    public ResourceLocation getId() {
        return id;
    }

    public GuideCategory getCategory() {
        return category;
    }

    public Component getTitle() {
        return title.resolve(null, GuideTextContext.empty());
    }

    public Component getTitle(@Nullable ServerPlayer player, @Nullable GuideTextContext context) {
        return title.resolve(player, context);
    }

    public GuideText getTitleText() {
        return title;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isRepeatablePopup() {
        return repeatablePopup;
    }

    public List<ICondition> getUnlockConditions() {
        return unlockConditions;
    }

    public List<GuidePageDefinition> getPages() {
        return pages;
    }

    public GuidePageDefinition getPage(int index) {
        if (index < 0 || index >= pages.size()) {
            throw new IndexOutOfBoundsException("Guide '" + id + "' page index out of bounds: " + index + " (size=" + pages.size() + ")");
        }
        return pages.get(index);
    }

    public int getPageCount() {
        return pages.size();
    }

    public boolean canUnlock(@Nullable ServerPlayer player,
                             Set<ResourceLocation> completedQuests,
                             Set<String> flags,
                             Map<String, Integer> variables) {
        for (ICondition cond : unlockConditions) {
            if (!cond.test(player, completedQuests, flags, variables)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Guide[" + id + ", pages=" + pages.size() + "]";
    }
}
