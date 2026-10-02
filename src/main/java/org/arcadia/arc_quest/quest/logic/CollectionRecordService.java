package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.registry.CollectionEntryRegistry;
import org.arcadia.arc_quest.quest.tracking.ObjectiveKey;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.*;
import java.util.function.Predicate;

/** Indexed permanent event records. Neither accepting nor abandoning a Quest resets this store. */
public final class CollectionRecordService {
    public enum Scope { DISCOVERY, RESEARCH }
    public record RuleRef(CollectionEntryDefinition entry, ObjectiveEntry objective, Scope scope) {}
    private static volatile Map<ResourceLocation, CollectionEntryDefinition> indexedDefinitions = Map.of();
    private static volatile Map<ObjectiveKey, List<RuleRef>> rulesByEvent = Map.of();
    private static volatile List<RuleRef> locationRules = List.of();
    private CollectionRecordService() {}

    /** Tag members are expanded once per registry/tag generation, rather than scanning every entry per event. */
    public static synchronized void rebuildIndex() {
        Map<ResourceLocation, CollectionEntryDefinition> definitions = CollectionEntryRegistry.serverSnapshot();
        Map<ObjectiveKey, List<RuleRef>> rules = new LinkedHashMap<>();
        List<RuleRef> locations = new ArrayList<>();
        for (CollectionEntryDefinition entry : definitions.values()) {
            index(entry, entry.getDiscoveryObjectives(), Scope.DISCOVERY, rules, locations);
            index(entry, entry.getResearchObjectives(), Scope.RESEARCH, rules, locations);
        }
        Map<ObjectiveKey, List<RuleRef>> immutable = new LinkedHashMap<>();
        rules.forEach((key, refs) -> immutable.put(key, List.copyOf(refs)));
        rulesByEvent = Map.copyOf(immutable);
        locationRules = List.copyOf(locations);
        indexedDefinitions = definitions;
    }

    private static void index(CollectionEntryDefinition entry, List<ObjectiveEntry> objectives, Scope scope,
                              Map<ObjectiveKey, List<RuleRef>> index, List<RuleRef> locations) {
        for (ObjectiveEntry objective : objectives) {
            RuleRef rule = new RuleRef(entry, objective, scope);
            if (ObjectiveType.REACH_LOCATION.equals(objective.getType()) && objective.getExtra("x") != null) locations.add(rule);
            List<ResourceLocation> targets = ObjectiveItemResolver.isItemObjective(objective) && objective.hasTargetTag()
                    ? ObjectiveItemResolver.targetIds(objective) : List.of(objective.getTargetId());
            for (ResourceLocation target : targets) {
                index.computeIfAbsent(new ObjectiveKey(objective.getType(), target), ignored -> new ArrayList<>()).add(rule);
            }
        }
    }

    public static List<RuleRef> rulesFor(ObjectiveType type, ResourceLocation target) {
        ensureIndex();
        return rulesByEvent.getOrDefault(new ObjectiveKey(type, target), List.of());
    }

    private static void ensureIndex() {
        if (indexedDefinitions != CollectionEntryRegistry.serverSnapshot()) rebuildIndex();
    }

    public static Set<ResourceLocation> dispatch(ServerPlayer player, ObjectiveType type, ResourceLocation target, int amount) {
        if (player == null || type == null || target == null || amount <= 0) return Set.of();
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return Set.of();
        List<RuleRef> matches = rulesFor(type, target);
        Map<ResourceLocation, CollectionEntryRewardService.Knowledge> before = knowledgeBefore(data.getCollectionRecords(), matches);
        Set<ResourceLocation> changed = applyMatchedRules(data.getCollectionRecords(), matches, amount,
                entry -> eligible(player, data, entry));
        updateRewards(player, data, before, changed);
        publish(player, data, changed);
        return changed;
    }

    /** Pure reducer shared by event handling and regression tests. Matching is done by the event index. */
    public static Set<ResourceLocation> applyMatchedRules(CollectionRecordState records, List<RuleRef> matches,
                                                         int amount, Predicate<CollectionEntryDefinition> eligible) {
        if (amount <= 0 || matches.isEmpty()) return Set.of();
        Set<ResourceLocation> changed = new LinkedHashSet<>();
        Map<ResourceLocation, Boolean> eligibility = new HashMap<>();
        // Discovery is processed first, allowing the same real event to unlock and research an entry once.
        for (Scope scope : Scope.values()) {
            for (RuleRef rule : matches) {
                if (rule.scope() != scope) continue;
                CollectionEntryDefinition entry = rule.entry();
                ResourceLocation id = entry.getEntryId();
                if (!eligibility.computeIfAbsent(id, ignored -> eligible.test(entry))) continue;
                if (scope == Scope.DISCOVERY && records.isDiscovered(id)) continue;
                if (scope == Scope.RESEARCH && entry.isResearchAfterDiscovery() && !records.isDiscovered(id)) continue;
                String stepId = scope == Scope.DISCOVERY
                        ? CollectionProgressProjector.discoveryKey(rule.objective().getObjectiveId())
                        : CollectionProgressProjector.researchKey(rule.objective().getObjectiveId());
                int maximum = rule.objective().getRequiredCount();
                if (records.increment(id, stepId, amount, maximum)) changed.add(id);
                if (scope == Scope.DISCOVERY && records.getProgress(id, stepId) >= maximum && records.discover(id)) changed.add(id);
            }
        }
        return Set.copyOf(changed);
    }

    /** Inventory on login may establish a discovery fact; it never fabricates pickup/craft/research actions. */
    public static void discoverInventory(ServerPlayer player, Map<ResourceLocation, Integer> inventory) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        LegacyCollectionMigration.migrate(data);
        CollectionEntryRewardService.reconcilePermanent(data);
        if (inventory.isEmpty()) { publish(player, data, data.getCollectionRecords().getDirtyEntryIds()); return; }
        CollectionRecordState records = data.getCollectionRecords();
        Map<RuleRef, Integer> held = new LinkedHashMap<>();
        for (var item : inventory.entrySet()) {
            for (RuleRef rule : rulesFor(ObjectiveType.COLLECT, item.getKey())) {
                if (rule.scope() == Scope.DISCOVERY) {
                    held.merge(rule, item.getValue(), (left, right) -> (int) Math.min(Integer.MAX_VALUE, (long) left + right));
                }
            }
        }
        Map<ResourceLocation, CollectionEntryRewardService.Knowledge> before = knowledgeBefore(records, List.copyOf(held.keySet()));
        Set<ResourceLocation> changed = importInventoryDiscovery(records, held, entry -> eligible(player, data, entry));
        updateRewards(player, data, before, changed);
        publish(player, data, data.getCollectionRecords().getDirtyEntryIds());
    }

    static Set<ResourceLocation> importInventoryDiscovery(CollectionRecordState records, Map<RuleRef, Integer> held,
                                                          Predicate<CollectionEntryDefinition> eligible) {
        Set<ResourceLocation> changed = new LinkedHashSet<>();
        for (var match : held.entrySet()) {
            RuleRef rule = match.getKey(); var entry = rule.entry();
            if (rule.scope() != Scope.DISCOVERY || records.isDiscovered(entry.getEntryId())
                    || records.isEntryReset(entry.getEntryId()) || !eligible.test(entry)) continue;
            String key = CollectionProgressProjector.discoveryKey(rule.objective().getObjectiveId());
            if (records.importProgress(entry.getEntryId(), key, match.getValue(), rule.objective().getRequiredCount())) changed.add(entry.getEntryId());
            if (records.getProgress(entry.getEntryId(), key) >= rule.objective().getRequiredCount() && records.discover(entry.getEntryId())) changed.add(entry.getEntryId());
        }
        return Set.copyOf(changed);
    }

    /** Called every second, only for explicitly registered coordinate rules. No entity proximity scanning. */
    public static void tickLocations(ServerPlayer player) {
        ensureIndex();
        if (locationRules.isEmpty()) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        List<RuleRef> matches = new ArrayList<>();
        for (RuleRef rule : locationRules) {
            ObjectiveEntry objective = rule.objective();
            String dimension = firstNonBlank(objective.getExtra("dimension"), objective.getExtra("dim"), objective.getExtra("world"));
            if (!dimension.isEmpty() && !dimension.equals(player.level().dimension().location().toString())) continue;
            double x = number(objective.getExtra("x")), y = number(objective.getExtra("y")), z = number(objective.getExtra("z"));
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;
            double dx = player.getX() - x, dy = player.getY() - y, dz = player.getZ() - z;
            int radius = Math.max(0, objective.getExtraInt("radius", 4));
            if (dx * dx + dy * dy + dz * dz <= (double) radius * radius) matches.add(rule);
        }
        Map<ResourceLocation, CollectionEntryRewardService.Knowledge> before = knowledgeBefore(data.getCollectionRecords(), matches);
        Set<ResourceLocation> changed = applyMatchedRules(data.getCollectionRecords(), matches, 1, entry -> eligible(player, data, entry));
        updateRewards(player, data, before, changed);
        publish(player, data, changed);
    }

    private static boolean eligible(ServerPlayer player, ArcQuestPlayer data, CollectionEntryDefinition entry) {
        for (ICondition condition : entry.getRecordConditions()) {
            if (!condition.test(player, data.getCompletedQuestLocations(), data.getAllFlags(), data.getAllVariables())) return false;
        }
        return true;
    }

    private static Map<ResourceLocation, CollectionEntryRewardService.Knowledge> knowledgeBefore(CollectionRecordState records, List<RuleRef> matches) {
        Map<ResourceLocation, CollectionEntryRewardService.Knowledge> before = new LinkedHashMap<>();
        for (RuleRef rule : matches) if (!rule.entry().getRewards().isEmpty())
            before.putIfAbsent(rule.entry().getEntryId(), CollectionEntryRewardService.knowledge(rule.entry(), records));
        return before;
    }
    private static void updateRewards(ServerPlayer player, ArcQuestPlayer data,
            Map<ResourceLocation, CollectionEntryRewardService.Knowledge> before, Set<ResourceLocation> changed) {
        for (ResourceLocation id : changed) {
            CollectionEntryDefinition entry = CollectionEntryRegistry.getServerEntry(id);
            if (entry != null && before.containsKey(id)) CollectionEntryRewardService.updatePermanent(player, data, entry, before.get(id));
        }
    }

    private static void publish(ServerPlayer player, ArcQuestPlayer data, Set<ResourceLocation> changed) {
        if (changed.isEmpty()) return;
        ArcQuestNetwork.syncCollectionRecords(player, changed);
        CollectionSheetService.refresh(player);
        ArcQuestPlayerManager.persistSnapshot(player, data);
    }

    private static String firstNonBlank(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return ""; }
    private static double number(String value) { try { return Double.parseDouble(value); } catch (RuntimeException e) { return Double.NaN; } }
}
