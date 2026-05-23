package org.arcadia.arc_quest.guide.builder;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuidePageDefinition;
import org.arcadia.arc_quest.guide.api.GuideText;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.api.ICondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GuideBuilder {

    private final ResourceLocation id;
    private final List<ICondition> unlockConditions = new ArrayList<>();
    private final List<GuidePageDefinition> pages = new ArrayList<>();
    private GuideCategory category = GuideCategory.BASICS;
    private GuideText title;
    private int sortOrder;
    private boolean hidden;
    private boolean repeatablePopup;

    private GuideBuilder(ResourceLocation id) {
        this.id = id;
    }

    public static GuideBuilder create(ResourceLocation id) {
        return new GuideBuilder(id);
    }

    public static GuideBuilder create(String id) {
        return id.contains(":") ? new GuideBuilder(ResourceLocation.parse(id)) : new GuideBuilder(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, id));
    }

    public GuideBuilder category(GuideCategory category) {
        this.category = Objects.requireNonNull(category, "category");
        return this;
    }

    public GuideBuilder title(String literal) {
        this.title = GuideText.literal(literal);
        return this;
    }

    public GuideBuilder title(Component component) {
        this.title = GuideText.component(component);
        return this;
    }

    public GuideBuilder title(GuideText text) {
        this.title = text;
        return this;
    }

    public GuideBuilder sortOrder(int order) {
        this.sortOrder = order;
        return this;
    }

    public GuideBuilder hidden() {
        this.hidden = true;
        return this;
    }

    public GuideBuilder repeatablePopup() {
        this.repeatablePopup = true;
        return this;
    }

    public GuideBuilder unlockCondition(ICondition condition) {
        this.unlockConditions.add(Objects.requireNonNull(condition, "condition"));
        return this;
    }

    public GuideBuilder requiresQuest(ResourceLocation questId) {
        return unlockCondition(ICondition.questCompleted(questId));
    }

    public GuideBuilder requiresFlag(String flag) {
        return unlockCondition(ICondition.flagSet(flag));
    }

    public GuideBuilder page(GuidePageBuilder pageBuilder) {
        return page(Objects.requireNonNull(pageBuilder, "pageBuilder").build());
    }

    public GuideBuilder page(GuidePageDefinition page) {
        this.pages.add(Objects.requireNonNull(page, "page"));
        return this;
    }

    public GuideBuilder imagePage(ResourceLocation texture, Component description) {
        return page(GuidePageBuilder.create().image(texture, 180, 90).description(description));
    }

    public GuideBuilder ponderPage(ResourceLocation sceneId, Component description) {
        return page(GuidePageBuilder.create().ponder(sceneId).description(description));
    }

    public GuideBuilder ponderQuestPhasePage(String questId, String phaseId, Component description) {
        return page(GuidePageBuilder.create().ponderScene(questId, phaseId).description(description));
    }

    public GuideDefinition build() {
        if (title == null) {
            title = GuideText.literal(id.getPath());
        }
        if (pages.isEmpty()) {
            throw new IllegalStateException("Guide '" + id + "' has no pages");
        }
        return new GuideDefinition(id, category, title, sortOrder, hidden, repeatablePopup, new ArrayList<>(unlockConditions), new ArrayList<>(pages));
    }

    public GuideDefinition buildAndRegister() {
        GuideDefinition definition = build();
        GuideRegistry.register(definition);
        return definition;
    }
}
