package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
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
    public record EventRules(ArcQuestPlayer owner, List<RuleRef> rules, Map<ResourceLocation, Long> generations) {
        public EventRules { rules = List.copyOf(rules); generations = Map.copyOf(generations); }
        public List<RuleRef> currentRules(ArcQuestPlayer current) {
            if (current == null || current != owner) return List.of();
            return rules.stream().filter(rule -> current.getCollectionRecords().getGeneration(rule.entry().getEntryId())
                    == generations.getOrDefault(rule.entry().getEntryId(), -1L)).toList();
        }
    }
    private static volatile Map<ResourceLocation, CollectionEntryDefinition> indexedDefinitions = Map.of();
    private static volatile Map<ObjectiveKey, List<RuleRef>> rulesByEvent = Map.of();
    private static volatile List<RuleRef> locationRules = List.of();
    private static final Map<QuestDefinition, LegacyIndex> legacyIndices = Collections.synchronizedMap(new WeakHashMap<>());
    private CollectionRecordService() {}

    /** Tag members are expanded once per registry/tag generation, rather than scanning every entry per event. */
    public static synchronized void rebuildIndex() {
        Map<ResourceLocation, CollectionEntryDefinition> definitions = CollectionEntryRegistry.serverSnapshot();
        Map<ObjectiveKey, List<RuleRef>> rules = new LinkedHashMap<>();
        List<RuleRef> locations = new ArrayList<>();
        for (CollectionEntryDefinition entry : definitions.values()) {
            index(entry, entry.getDiscoveryObjectives(), Scope.DISCOVERY, rules, locations);
            if (!entry.isUnifiedGameplay()) index(entry, entry.getResearchObjectives(), Scope.RESEARCH, rules, locations);
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
        return dispatch(player, type, target, amount, false);
    }

    public static Set<ResourceLocation> dispatch(ServerPlayer player, ObjectiveType type, ResourceLocation target, int amount, boolean crafted) {
        if (amount <= 0) return Set.of();
        return applyFrozen(player, freezeRules(player, type, target, crafted), amount);
    }

    /** Permanent recipients and record-condition eligibility share the real event's initial snapshot. */
    public static EventRules freezeRules(ServerPlayer player, ObjectiveType type, ResourceLocation target, boolean crafted) {
        if (player == null || type == null || target == null) return new EventRules(null, List.of(), Map.of());
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return new EventRules(null, List.of(), Map.of());
        Map<CollectionEntryDefinition, Boolean> eligibility = new IdentityHashMap<>();
        List<RuleRef> matches = combinedRules(player, data, type, target).stream()
                .filter(rule -> !ObjectiveType.COLLECT.equals(type) || CollectMode.from(rule.objective()).acceptsAcquisition(crafted))
                .filter(rule -> eligibility.computeIfAbsent(rule.entry(), entry -> eligible(player, data, entry))).toList();
        return freezeRules(data, matches);
    }

    static EventRules freezeRules(ArcQuestPlayer data, List<RuleRef> rules) {
        Map<ResourceLocation, Long> generations = new LinkedHashMap<>();
        for (var rule : rules) generations.put(rule.entry().getEntryId(), data.getCollectionRecords().getGeneration(rule.entry().getEntryId()));
        return new EventRules(data, List.copyOf(rules), Map.copyOf(generations));
    }

    public static Set<ResourceLocation> applyFrozen(ServerPlayer player, EventRules event, int amount) {
        if (player == null || amount <= 0) return Set.of();
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        List<RuleRef> matches = event.currentRules(data);
        if (matches.isEmpty()) return Set.of();
        Map<CollectionEntryDefinition, CollectionEntryRewardService.Knowledge> before = knowledgeBefore(data.getCollectionRecords(), matches);
        Set<ResourceLocation> changed = applyMatchedRules(data.getCollectionRecords(), matches, amount, entry -> true);
        updateRewards(player, data, before, changed);
        publish(player, data, changed);
        return changed;
    }

    private static List<RuleRef> combinedRules(ServerPlayer player, ArcQuestPlayer data,
                                              ObjectiveType type, ResourceLocation target) {
        List<RuleRef> result = new ArrayList<>(rulesFor(type, target));
        for (QuestRuntimeData run : data.getAllActiveQuests().values()) {
            if (run.getState() != QuestState.ACTIVE) continue;
            var definition = CollectionRunAccess.resolve(player.server, run);
            if (definition != null) result.addAll(legacyRulesFor(run, definition, type, target));
        }
        return List.copyOf(result);
    }

    /** An old accepted investigation alone keeps its legacy rules alive; a new v2 sheet never indexes research. */
    static List<RuleRef> legacyRulesFor(QuestRuntimeData run, QuestDefinition definition,
                                       ObjectiveType type, ResourceLocation target) {
        if (run == null || run.getState() != QuestState.ACTIVE || definition.getCollectionConfig() == null) return List.of();
        LegacyIndex index = legacyIndices.computeIfAbsent(definition, CollectionRecordService::legacyIndex);
        List<RuleRef> result = new ArrayList<>(index.direct().getOrDefault(new ObjectiveKey(type, target), List.of()));
        for (RuleRef rule : index.tagged().getOrDefault(type, List.of()))
            if (ObjectiveItemResolver.matches(rule.objective(), target, run)) result.add(rule);
        return List.copyOf(result);
    }

    private static LegacyIndex legacyIndex(QuestDefinition definition) {
        Map<ObjectiveKey, List<RuleRef>> direct = new HashMap<>();
        Map<ObjectiveType, List<RuleRef>> tagged = new HashMap<>();
        List<RuleRef> locations = new ArrayList<>();
        if (definition.getCollectionConfig() != null) for (var entry : definition.getCollectionConfig().getEntries()) {
            if (entry.isUnifiedGameplay()) continue;
            for (Scope scope : Scope.values()) for (var objective : scope == Scope.DISCOVERY
                    ? entry.getDiscoveryObjectives() : entry.getResearchObjectives()) {
                var rule = new RuleRef(entry, objective, scope);
                if (ObjectiveType.REACH_LOCATION.equals(objective.getType())) locations.add(rule);
                if (ObjectiveItemResolver.isItemObjective(objective) && objective.hasTargetTag())
                    tagged.computeIfAbsent(objective.getType(), ignored -> new ArrayList<>()).add(rule);
                else direct.computeIfAbsent(new ObjectiveKey(objective.getType(), objective.getTargetId()), ignored -> new ArrayList<>()).add(rule);
            }
        }
        return new LegacyIndex(direct, tagged, List.copyOf(locations));
    }
    private record LegacyIndex(Map<ObjectiveKey, List<RuleRef>> direct, Map<ObjectiveType, List<RuleRef>> tagged,
                               List<RuleRef> locations) { }

    /** Pure reducer shared by event handling and regression tests. Matching is done by the event index. */
    public static Set<ResourceLocation> applyMatchedRules(CollectionRecordState records, List<RuleRef> matches,
                                                         int amount, Predicate<CollectionEntryDefinition> eligible) {
        if (amount <= 0 || matches.isEmpty()) return Set.of();
        Set<ResourceLocation> changed = new LinkedHashSet<>();
        Map<CollectionEntryDefinition, Boolean> eligibility = new IdentityHashMap<>();
        // Discovery is processed first, allowing the same real event to unlock and research an entry once.
        for (Scope scope : Scope.values()) {
            Map<RuleKey, RuleRef> unique = new LinkedHashMap<>();
            for (RuleRef rule : matches) {
                if (rule.scope() != scope || scope == Scope.RESEARCH && rule.entry().isUnifiedGameplay()) continue;
                if (!eligibility.computeIfAbsent(rule.entry(), eligible::test)) continue;
                if (scope == Scope.RESEARCH && rule.entry().isResearchAfterDiscovery()
                        && !records.isDiscovered(rule.entry().getEntryId())) continue;
                RuleKey key = new RuleKey(rule.entry().getEntryId(), scope, rule.objective().getObjectiveId());
                RuleRef previous = unique.get(key);
                // Deduplicate eligible legacy versions only; a locked higher threshold cannot suppress another version's action.
                if (previous == null || rule.objective().getRequiredCount() > previous.objective().getRequiredCount()) unique.put(key, rule);
            }
            for (RuleRef rule : unique.values()) {
                CollectionEntryDefinition entry = rule.entry();
                ResourceLocation id = entry.getEntryId();
                if (scope == Scope.DISCOVERY && records.isDiscovered(id)) continue;
                String stepId = scope == Scope.DISCOVERY
                        ? CollectionProgressProjector.discoveryKey(rule.objective().getObjectiveId())
                        : CollectionProgressProjector.researchKey(rule.objective().getObjectiveId());
                int maximum = Math.max(rule.objective().getRequiredCount(), records.getProgress(id, stepId));
                if (records.increment(id, stepId, amount, maximum)) changed.add(id);
                if (scope == Scope.DISCOVERY && records.getProgress(id, stepId) >= maximum && records.discover(id)) changed.add(id);
            }
        }
        return Set.copyOf(changed);
    }

    private record RuleKey(ResourceLocation entryId, Scope scope, String objectiveId) { }

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
        Map<CollectionEntryDefinition, CollectionEntryRewardService.Knowledge> before = knowledgeBefore(records, List.copyOf(held.keySet()));
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
        applyFrozen(player, freezeLocationRules(player), 1);
    }

    public static EventRules freezeLocationRules(ServerPlayer player) {
        ensureIndex();
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return new EventRules(null, List.of(), Map.of());
        List<RuleRef> candidates = new ArrayList<>(locationRules);
        for (var run : data.getAllActiveQuests().values()) {
            if (run.getState() != QuestState.ACTIVE) continue;
            var definition = CollectionRunAccess.resolve(player.server, run);
            if (definition == null || definition.getCollectionConfig() == null) continue;
            candidates.addAll(legacyIndices.computeIfAbsent(definition, CollectionRecordService::legacyIndex).locations());
        }
        if (candidates.isEmpty()) return freezeRules(data, List.of());
        List<RuleRef> matches = new ArrayList<>();
        Map<CollectionEntryDefinition, Boolean> eligibility = new IdentityHashMap<>();
        for (RuleRef rule : candidates) {
            ObjectiveEntry objective = rule.objective();
            String dimension = firstNonBlank(objective.getExtra("dimension"), objective.getExtra("dim"), objective.getExtra("world"));
            if (!dimension.isEmpty() && !dimension.equals(player.level().dimension().location().toString())) continue;
            double x = number(objective.getExtra("x")), y = number(objective.getExtra("y")), z = number(objective.getExtra("z"));
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;
            double dx = player.getX() - x, dy = player.getY() - y, dz = player.getZ() - z;
            int radius = Math.max(0, objective.getExtraInt("radius", 4));
            if (dx * dx + dy * dy + dz * dz <= (double) radius * radius
                    && eligibility.computeIfAbsent(rule.entry(), entry -> eligible(player, data, entry))) matches.add(rule);
        }
        return freezeRules(data, matches);
    }

    private static boolean eligible(ServerPlayer player, ArcQuestPlayer data, CollectionEntryDefinition entry) {
        for (ICondition condition : entry.getRecordConditions()) {
            if (!condition.test(player, data.getCompletedQuestLocations(), data.getAllFlags(), data.getAllVariables())) return false;
        }
        return true;
    }

    private static Map<CollectionEntryDefinition, CollectionEntryRewardService.Knowledge> knowledgeBefore(CollectionRecordState records, List<RuleRef> matches) {
        Map<CollectionEntryDefinition, CollectionEntryRewardService.Knowledge> before = new LinkedHashMap<>();
        for (RuleRef rule : matches) if (!rule.entry().getRewards().isEmpty())
            before.putIfAbsent(rule.entry(), CollectionEntryRewardService.knowledge(rule.entry(), records));
        return before;
    }
    private static void updateRewards(ServerPlayer player, ArcQuestPlayer data,
            Map<CollectionEntryDefinition, CollectionEntryRewardService.Knowledge> before, Set<ResourceLocation> changed) {
        for (var entry : before.entrySet()) {
            if (changed.contains(entry.getKey().getEntryId())) CollectionEntryRewardService.updatePermanent(player, data, entry.getKey(), entry.getValue());
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
