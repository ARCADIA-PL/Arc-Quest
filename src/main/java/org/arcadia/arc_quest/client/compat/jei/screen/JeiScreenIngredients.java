package org.arcadia.arc_quest.client.compat.jei.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.quest.icon.IconFrameSelection;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconContext;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconsClient;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.client.compat.jei.JeiCatalogClient;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;
import org.arcadia.arc_quest.client.hud.gacha.GachaScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.api.JeiDisplayAdapters;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Render-side bridge with no JEI types: safe when JEI is absent. */
public final class JeiScreenIngredients {
    private static final Map<Screen, Frame> FRAMES = new WeakHashMap<>();
    private static boolean runtimeAvailable;
    private static Supplier<List<Component>> queryHints = List::of;
    private JeiScreenIngredients() {}

    static void setRuntimeAvailable(boolean available) {
        runtimeAvailable = available;
        if (!available) queryHints = List::of;
        FRAMES.clear();
    }

    static void setQueryHints(Supplier<List<Component>> hints) { queryHints = hints; }
    public static List<Component> queryHints() { return runtimeAvailable ? List.copyOf(queryHints.get()) : List.of(); }
    public static boolean isRuntimeAvailable() { return runtimeAvailable; }

    /** Recorded after the containing row: the visible item takes precedence over its tag group. */
    public static void objectiveCandidate(Screen screen, GuiGraphics graphics, ObjectiveIconContext context,
                                          IconFrameSelection selected, double x, double y, double width, double height) {
        ObjectiveEntry objective = context.objective();
        if (objective == null || objective.isHidden() || !selected.available()) return;
        boolean explicitItem = objective.getIcon().mode() == ObjectiveIconSpec.Mode.ITEM;
        record(screen, graphics, x, y, width, height, true, !explicitItem, () -> {
            if (selected.generation() != ObjectiveIconsClient.generation() || !currentObjective(screen, context)) return List.of();
            var authorized = explicitItem ? List.<JeiIngredient>of() : objectiveIngredients(screen, context);
            return objectiveIconIngredients(objective, selected, authorized);
        });
    }

    /** Explicit ITEM icons are independent lookup links; automatic candidates keep material semantics. */
    static List<JeiIngredient> objectiveIconIngredients(ObjectiveEntry objective, IconFrameSelection selected,
                                                       List<JeiIngredient> authorized) {
        if (objective == null || objective.isHidden() || objective.getIcon().mode() == ObjectiveIconSpec.Mode.NONE
                || selected == null || !selected.available()) return List.of();
        if (objective.getIcon().mode() == ObjectiveIconSpec.Mode.ITEM) {
            ItemStack shown = selected.stack();
            if (shown.isEmpty() || !objective.getIcon().item().equals(BuiltInRegistries.ITEM.getKey(shown.getItem()))) return List.of();
            return List.of(JeiIngredient.of(shown, 1, false));
        }
        return selected.isItem() ? candidateIngredients(authorized, selected.stack()) : authorized;
    }

    static List<JeiIngredient> candidateIngredients(List<JeiIngredient> authorized, ItemStack selected) {
        if (selected == null || selected.isEmpty()) return List.of();
        List<JeiIngredient> result = new ArrayList<>();
        for (JeiIngredient ingredient : authorized) {
            var matching = ingredient.alternatives().stream()
                    .filter(stack -> ingredient.exactNbt() ? ItemStack.isSameItemSameTags(stack, selected)
                            : stack.is(selected.getItem())).toList();
            if (!matching.isEmpty()) result.add(new JeiIngredient(matching, ingredient.amount(), ingredient.consumed(), ingredient.description(), ingredient.exactNbt()));
        }
        return List.copyOf(result);
    }

    public static void begin(Screen screen, boolean enabled) {
        if (!runtimeAvailable || Minecraft.getInstance().screen != screen) return;
        FRAMES.clear();
        FRAMES.put(screen, new Frame(enabled, screen.width, screen.height));
    }

    static void screenChanged() { FRAMES.clear(); }

    /** Begin a topmost modal; lower layers must never answer ingredient queries. */
    public static void modal(Screen screen, boolean enabled) {
        Frame frame = frame(screen);
        if (frame == null) return;
        frame.regions.clear();
        frame.enabled = enabled;
    }

    public static void pushClip(Screen screen, double x1, double y1, double x2, double y2) {
        Frame frame = frame(screen);
        if (frame != null) frame.clips.push(frame.clips.peek().intersect(new JeiHitBounds(x1, y1, x2, y2)));
    }

    public static void popClip(Screen screen) {
        Frame frame = frame(screen);
        if (frame != null && frame.clips.size() > 1) frame.clips.pop();
    }

    public static void enableScissor(Screen screen, GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.enableScissor(x1, y1, x2, y2);
        pushClip(screen, x1, y1, x2, y2);
    }

    public static void disableScissor(Screen screen, GuiGraphics graphics) {
        graphics.disableScissor();
        popClip(screen);
    }

    public static void objective(Screen screen, GuiGraphics graphics, ObjectiveEntry objective,
                                 double x, double y, double width, double height) {
        if (objective == null || objective.isHidden()) return;
        record(screen, graphics, x, y, width, height, () -> objectiveIngredients(screen, objective));
    }

    public static void objective(Screen screen, GuiGraphics graphics, ObjectiveIconContext context,
                                 double x, double y, double width, double height) {
        if (context.objective().isHidden()) return;
        record(screen, graphics, x, y, width, height, () -> objectiveIngredients(screen, context));
    }

    /** Resolve the exact phase/index, even if callers reuse one ObjectiveEntry in several phases. */
    public static List<JeiIngredient> objectiveIngredients(Screen screen, ObjectiveIconContext context) {
        if (!currentObjective(screen, context)) return List.of();
        String key = context.objective().hasObjectiveId() ? context.objective().getObjectiveId() : Integer.toString(context.objectiveIndex());
        var source = JeiCatalogClient.find("quest/" + context.questId() + "/phase/" + context.phaseId() + "/objective/" + key);
        return source != null && source.kind() == JeiCatalogEntry.Kind.QUEST_REQUIREMENT ? source.inputs() : List.of();
    }

    private static boolean currentObjective(Screen screen, ObjectiveIconContext context) {
        if (context.objective().isHidden() || !(screen instanceof QuestJournalScreen journal)
                || !context.questId().equals(journal.getSelectedQuestId())) return false;
        var quest = QuestRegistry.get(context.questId());
        var phase = quest == null ? null : quest.getPhase(context.phaseId());
        int index = context.objectiveIndex();
        return phase != null && index >= 0 && index < phase.getObjectives().size()
                && phase.getObjectives().get(index) == context.objective();
    }

    public static void reward(Screen screen, GuiGraphics graphics, IReward reward,
                              double x, double y, double width, double height) {
        if (reward == null) return;
        record(screen, graphics, x, y, width, height, () -> rewardIngredients(screen, reward));
    }

    public static void rewardIcon(Screen screen, GuiGraphics graphics, IReward reward, ItemStack shown,
                                  double x, double y, double width, double height) {
        if (reward == null) return;
        recordIcon(screen, graphics, shown, x, y, width, height, () -> rewardIngredients(screen, reward));
    }

    public static void objectiveIcon(Screen screen, GuiGraphics graphics, ObjectiveEntry objective, ItemStack shown,
                                     double x, double y, double width, double height) {
        if (objective == null || objective.isHidden()) return;
        recordIcon(screen, graphics, shown, x, y, width, height, () -> objectiveIngredients(screen, objective));
    }

    public static void collectionItem(Screen screen, GuiGraphics graphics, String questId, String phaseId,
            String bindingId, ItemStack shown, double x, double y, double width, double height) {
        if (shown == null || shown.isEmpty()) return;
        ItemStack selected = shown.copy();
        record(screen, graphics, x, y, width, height, true, false, () -> {
            if (!(screen instanceof QuestJournalScreen journal) || !questId.equals(journal.getSelectedQuestId())) return List.of();
            var progress = org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE
                    .getCollectionBindingProgress(questId, phaseId, bindingId);
            var quest = QuestRegistry.get(questId);
            var phase = quest == null ? null : quest.getPhase(phaseId);
            var binding = phase == null || phase.getCollectionSheet() == null ? null
                    : phase.getCollectionSheet().getBinding(bindingId);
            var entry = binding == null || quest.getCollectionConfig() == null ? null
                    : quest.getCollectionConfig().getEntry(binding.getEntryId());
            if (progress == null || !progress.visible() || !progress.revealed() || entry == null) return List.of();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(selected.getItem());
            boolean authorized = (entry.getIcon().mode() == ObjectiveIconSpec.Mode.ITEM && itemId.equals(entry.getIcon().item()))
                    || entry.getRelatedItems().contains(itemId)
                    || (entry.getSubjectKind() == org.arcadia.arc_quest.quest.api.CollectionSubjectKind.ITEM
                        && (itemId.equals(entry.getSubjectId()) || (entry.getItemTag() != null && selected.is(
                                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, entry.getItemTag())))))
                    || binding.getObjectiveIds().stream().map(phase::getObjectiveIndex).filter(i -> i >= 0)
                        .map(i -> phase.getObjectives().get(i)).filter(o -> !o.isHidden())
                        .anyMatch(o -> org.arcadia.arc_quest.quest.api.ObjectiveItemResolver.matches(o, selected)
                                || (o.getIcon().mode() == ObjectiveIconSpec.Mode.ITEM && itemId.equals(o.getIcon().item())));
            return authorized ? List.of(JeiIngredient.of(selected, 1, false)) : List.of();
        });
    }

    public static void collectionRewardItem(Screen screen, GuiGraphics graphics, String questId,
            String nodeId, ItemStack shown, double x, double y, double width, double height) {
        if (shown == null || shown.isEmpty()) return;
        ItemStack selected = shown.copy();
        record(screen, graphics, x, y, width, height, true, false, () -> {
            if (!(screen instanceof QuestJournalScreen journal) || !questId.equals(journal.getSelectedQuestId())) return List.of();
            var quest = QuestRegistry.get(questId);
            var data = org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE.getCollectionData(questId);
            var node = quest == null ? null : org.arcadia.arc_quest.quest.logic.profile.collection.CollectionRewardResolver
                    .findRewardNode(quest, quest.getCollectionConfig(), nodeId);
            if (node == null || data == null || !data.isRewardUnlocked(nodeId)) return List.of();
            var ingredients = node.getRewards().stream().filter(java.util.Objects::nonNull)
                    .flatMap(reward -> JeiDisplayAdapters.reward(reward, null).ingredients().stream()).toList();
            return candidateIngredients(ingredients, selected);
        });
    }

    public static void collectionEntryRewardItem(Screen screen, GuiGraphics graphics, String questId, String phaseId,
            String bindingId, String rewardId, ItemStack shown, double x, double y, double width, double height) {
        if (shown == null || shown.isEmpty()) return;
        ItemStack selected = shown.copy();
        record(screen, graphics, x, y, width, height, true, false, () -> {
            if (!(screen instanceof QuestJournalScreen journal) || !questId.equals(journal.getSelectedQuestId())) return List.of();
            var binding = org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE.getCollectionBindingProgress(questId, phaseId, bindingId);
            if (binding == null || !binding.revealed()) return List.of();
            var reward = binding.entryRewards().stream().filter(row -> row.definition().rewardId().equals(rewardId)
                    && (row.unlocked() || row.claimed())).findFirst().orElse(null);
            if (reward == null) return List.of();
            var ingredients = reward.definition().rewards().stream()
                    .flatMap(value -> JeiDisplayAdapters.reward(value, null).ingredients().stream()).toList();
            return candidateIngredients(ingredients, selected);
        });
    }

    public static List<JeiIngredient> objectiveIngredients(Screen screen, ObjectiveEntry objective) {
        if (objective == null || objective.isHidden() || !(screen instanceof QuestJournalScreen journal)) return List.of();
        String questId = journal.getSelectedQuestId();
        if (questId == null) return List.of();
        var quest = QuestRegistry.get(questId);
        if (quest == null) return List.of();
        for (var phase : quest.getAllPhases()) {
            int index = phase.getObjectives().indexOf(objective);
            if (index < 0) continue;
            String key = objective.hasObjectiveId() ? objective.getObjectiveId() : Integer.toString(index);
            var source = JeiCatalogClient.find("quest/" + questId + "/phase/" + phase.getPhaseId() + "/objective/" + key);
            if (source != null && source.kind() == JeiCatalogEntry.Kind.QUEST_REQUIREMENT) return source.inputs();
        }
        return List.of();
    }

    public static List<JeiIngredient> rewardIngredients(Screen screen, IReward reward) {
        if (reward == null || !(screen instanceof QuestJournalScreen journal)) return List.of();
        String questId = journal.getSelectedQuestId();
        if (questId == null) return List.of();
        List<JeiIngredient> authorized = JeiCatalogClient.entries().stream()
                .filter(entry -> entry.kind() == JeiCatalogEntry.Kind.QUEST_REWARD && entry.navigationTarget().equals(questId))
                .flatMap(entry -> entry.outputs().stream()).toList();
        return approvedIngredients(JeiDisplayAdapters.reward(reward, null).ingredients(), authorized);
    }

    public static void offer(Screen screen, GuiGraphics graphics, ITradeOffer offer, boolean consumed,
                             double x, double y, double width, double height) {
        if (offer == null) return;
        record(screen, graphics, x, y, width, height, () -> JeiDisplayAdapters.offer(offer, null, consumed).ingredients());
    }

    public static void tradeRewards(Screen screen, GuiGraphics graphics, TradeEntry entry,
                                    double x, double y, double width, double height) {
        record(screen, graphics, x, y, width, height, () -> {
            JeiCatalogEntry authorized = authorizedTrade(screen, entry);
            if (authorized == null) return List.of();
            List<JeiIngredient> rewards = authorized.outputs();
            if (!rewards.isEmpty() || entry.getRewardIcon() != null) return rewards;
            return authorized.inputs();
        });
    }

    public static void tradeCost(Screen screen, GuiGraphics graphics, TradeEntry entry, int costIndex,
                                  double x, double y, double width, double height) {
        record(screen, graphics, x, y, width, height, () -> {
            JeiCatalogEntry authorized = authorizedTrade(screen, entry);
            if (authorized == null || costIndex < 0 || costIndex >= entry.getCosts().size()) return List.of();
            return approvedOffer(entry.getCosts().get(costIndex), authorized.inputs());
        });
    }

    public static void tradeRewardIcon(Screen screen, GuiGraphics graphics, TradeEntry entry, ItemStack shown,
                                       double x, double y, double width, double height) {
        recordIcon(screen, graphics, shown, x, y, width, height, () -> {
            JeiCatalogEntry authorized = authorizedTrade(screen, entry);
            return authorized == null ? List.of() : authorized.outputs();
        });
    }

    public static void tradeCostIcon(Screen screen, GuiGraphics graphics, TradeEntry entry, int costIndex,
                                     ItemStack shown, double x, double y, double width, double height) {
        recordIcon(screen, graphics, shown, x, y, width, height, () -> {
            JeiCatalogEntry authorized = authorizedTrade(screen, entry);
            if (authorized == null || costIndex < 0 || costIndex >= entry.getCosts().size()) return List.of();
            return approvedOffer(entry.getCosts().get(costIndex), authorized.inputs());
        });
    }

    public static void gachaReward(GachaScreen screen, GuiGraphics graphics, GachaItem item,
                                    double x, double y, double width, double height) {
        record(screen, graphics, x, y, width, height, () -> JeiCatalogClient.entries().stream()
                .filter(entry -> entry.kind() == JeiCatalogEntry.Kind.GACHA
                        && entry.navigationTarget().equals(screen.getShopId())
                        && entry.navigationDetail().equals(item.getItemId()))
                .findFirst().map(JeiCatalogEntry::outputs).orElse(List.of()));
    }

    public static void gachaCost(GachaScreen screen, GuiGraphics graphics, ITradeOffer offer,
                                  double x, double y, double width, double height) {
        record(screen, graphics, x, y, width, height, () -> JeiCatalogClient.entries().stream()
                .filter(entry -> entry.kind() == JeiCatalogEntry.Kind.GACHA
                        && entry.navigationTarget().equals(screen.getShopId()))
                .findFirst().map(entry -> approvedOffer(offer, entry.inputs())).orElse(List.of()));
    }

    public static void gachaRewardIcon(GachaScreen screen, GuiGraphics graphics, GachaItem item, ItemStack shown,
                                       double x, double y, double width, double height) {
        recordIcon(screen, graphics, shown, x, y, width, height, () -> JeiCatalogClient.entries().stream()
                .filter(entry -> entry.kind() == JeiCatalogEntry.Kind.GACHA
                        && entry.navigationTarget().equals(screen.getShopId())
                        && entry.navigationDetail().equals(item.getItemId()))
                .findFirst().map(JeiCatalogEntry::outputs).orElse(List.of()));
    }

    public static void gachaCostIcon(GachaScreen screen, GuiGraphics graphics, ITradeOffer offer, ItemStack shown,
                                     double x, double y, double width, double height) {
        recordIcon(screen, graphics, shown, x, y, width, height, () -> JeiCatalogClient.entries().stream()
                .filter(entry -> entry.kind() == JeiCatalogEntry.Kind.GACHA
                        && entry.navigationTarget().equals(screen.getShopId()))
                .findFirst().map(entry -> approvedOffer(offer, entry.inputs())).orElse(List.of()));
    }

    private static JeiCatalogEntry authorizedTrade(Screen screen, TradeEntry entry) {
        if (!(screen instanceof AbstractTradeScreen trade)) return null;
        return JeiCatalogClient.entries().stream().filter(candidate -> candidate.kind() == JeiCatalogEntry.Kind.TRADE
                && candidate.navigationTarget().equals(trade.getShopId())
                && candidate.navigationDetail().equals(entry.getEntryId())).findFirst().orElse(null);
    }

    private static List<JeiIngredient> approvedOffer(ITradeOffer offer, List<JeiIngredient> authorized) {
        if (offer == null) return List.of();
        return approvedIngredients(JeiDisplayAdapters.offer(offer, null, true).ingredients(), authorized);
    }

    private static List<JeiIngredient> approvedIngredients(List<JeiIngredient> display, List<JeiIngredient> authorized) {
        List<ItemStack> shown = display.stream()
                .flatMap(ingredient -> ingredient.alternatives().stream()).toList();
        List<JeiIngredient> result = new ArrayList<>();
        for (JeiIngredient ingredient : authorized) {
            List<ItemStack> matches = ingredient.alternatives().stream()
                    .filter(stack -> shown.stream().anyMatch(shownStack -> ItemStack.isSameItemSameTags(stack, shownStack))).toList();
            if (!matches.isEmpty()) result.add(new JeiIngredient(matches, ingredient.amount(), ingredient.consumed(), ingredient.description(), ingredient.exactNbt()));
        }
        return result;
    }

    public static void guide(Screen screen, GuiGraphics graphics, GuideDefinition guide, int page,
                             double x, double y, double width, double height) {
        if (!ClientGuideCache.INSTANCE.isUnlocked(guide.getId())) return;
        record(screen, graphics, x, y, width, height, () -> JeiCatalogClient.entries().stream()
                .filter(entry -> entry.kind() == JeiCatalogEntry.Kind.GUIDE && entry.navigationTarget().equals(guide.getId().toString())
                        && (page < 0 || entry.navigationDetail().equals(Integer.toString(page))))
                .flatMap(entry -> entry.inputs().stream()).toList());
    }

    /** Decorative guide covers retain only their author's explicit, unlocked associations. */
    public static void guideIcon(Screen screen, GuiGraphics graphics, GuideDefinition guide, int page, ItemStack shown,
                                 double x, double y, double width, double height) {
        if (!ClientGuideCache.INSTANCE.isUnlocked(guide.getId())) return;
        ItemStack displayed = shown == null ? ItemStack.EMPTY : shown.copy();
        record(screen, graphics, x, y, width, height, true, () -> {
            if (!ClientGuideCache.INSTANCE.isUnlocked(guide.getId())) return List.of();
            List<JeiIngredient> authorized = JeiCatalogClient.entries().stream()
                    .filter(entry -> entry.kind() == JeiCatalogEntry.Kind.GUIDE
                            && entry.navigationTarget().equals(guide.getId().toString())
                            && (page < 0 || entry.navigationDetail().equals(Integer.toString(page))))
                    .flatMap(entry -> entry.inputs().stream()).toList();
            return guideIconIngredients(authorized, displayed);
        });
    }

    static List<JeiIngredient> guideIconIngredients(List<JeiIngredient> authorized, ItemStack displayed) {
        List<JeiIngredient> current = candidateIngredients(authorized, displayed);
        return current.isEmpty() ? List.copyOf(authorized) : current;
    }

    /** Register a dedicated icon, resolving only its displayed candidate within authorized ingredients. */
    public static void recordIcon(Screen screen, GuiGraphics graphics, ItemStack shown,
                                   double x, double y, double width, double height,
                                   Supplier<List<JeiIngredient>> ingredients) {
        if (shown == null || shown.isEmpty()) return;
        ItemStack displayed = shown.copy();
        record(screen, graphics, x, y, width, height, true,
                () -> candidateIngredients(ingredients.get(), displayed));
    }

    /** GUI coordinates, clipped to the foreground frame; false when JEI is unavailable. */
    public static boolean hasItemIconAt(Screen screen, double mouseX, double mouseY) {
        return underMouse(screen, mouseX, mouseY)
                .filter(region -> region.allowsPrimaryClick() && !region.stacks().isEmpty()).isPresent();
    }

    public static void record(Screen screen, GuiGraphics graphics, double x, double y, double width, double height,
                              Supplier<List<JeiIngredient>> ingredients) {
        record(screen, graphics, x, y, width, height, false, ingredients);
    }

    /** Only dedicated display icons opt in; business rows keep their primary-click actions. */
    private static void record(Screen screen, GuiGraphics graphics, double x, double y, double width, double height,
                               boolean allowsPrimaryClick, Supplier<List<JeiIngredient>> ingredients) {
        record(screen, graphics, x, y, width, height, allowsPrimaryClick, true, ingredients);
    }

    private static void record(Screen screen, GuiGraphics graphics, double x, double y, double width, double height,
                               boolean allowsPrimaryClick, boolean requiresCatalog, Supplier<List<JeiIngredient>> ingredients) {
        Frame frame = frame(screen);
        if (frame == null || !frame.enabled || width <= 0 || height <= 0) return;
        Matrix4f pose = graphics.pose().last().pose();
        JeiHitBounds bounds = JeiHitBounds.transformed(x, y, width, height,
                pose.m00(), pose.m01(), pose.m10(), pose.m11(), pose.m30(), pose.m31()).intersect(frame.clips.peek());
        if (!bounds.empty()) frame.regions.add(new Region(bounds, allowsPrimaryClick, ingredients, requiresCatalog));
    }

    static Optional<Region> underMouse(Screen screen, double x, double y) {
        Frame frame = frame(screen);
        if (frame == null || !frame.enabled || System.nanoTime() - frame.created > 1_000_000_000L) return Optional.empty();
        boolean currentCatalog = !frame.catalog.isEmpty() && frame.catalog == JeiCatalogClient.entries();
        for (int index = frame.regions.size() - 1; index >= 0; index--) {
            Region region = frame.regions.get(index);
            if (region.bounds.contains(x, y)) {
                return region.requiresCatalog && !currentCatalog ? Optional.empty() : Optional.of(region);
            }
        }
        return Optional.empty();
    }

    private static Frame frame(Screen screen) {
        return runtimeAvailable && screen != null && Minecraft.getInstance().screen == screen ? FRAMES.get(screen) : null;
    }

    record Region(JeiHitBounds bounds, boolean allowsPrimaryClick, Supplier<List<JeiIngredient>> ingredients, boolean requiresCatalog) {
        List<ItemStack> stacks() {
            try {
                return ingredients.get().stream().flatMap(ingredient -> ingredient.alternatives().stream())
                        .filter(stack -> !stack.isEmpty()).toList();
            } catch (RuntimeException ignored) {
                // Read-only addon adapters cannot take down the active business UI.
                return List.of();
            }
        }
    }

    private static final class Frame {
        private final long created = System.nanoTime();
        private final List<JeiCatalogEntry> catalog = JeiCatalogClient.entries();
        private final List<Region> regions = new ArrayList<>();
        private final Deque<JeiHitBounds> clips = new ArrayDeque<>();
        private boolean enabled;
        private Frame(boolean enabled, int width, int height) {
            this.enabled = enabled;
            clips.push(new JeiHitBounds(0, 0, width, height));
        }
    }
}
