package org.arcadia.arc_quest.npc.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.arcadia.arc_quest.core.identity.EntityRef;
import org.arcadia.arc_quest.condition.ConditionEvaluator;
import org.arcadia.arc_quest.data.registry.RegistrySourceInfo;
import org.arcadia.arc_quest.data.registry.RegistrySourceType;
import org.arcadia.arc_quest.npc.spec.NpcBindingSpec;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class NpcBindingRegistry {

    public static final NpcBindingRegistry INSTANCE = new NpcBindingRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();

    private Map<EntityType<?>, List<NpcSpec>> codeSpecsByType = new LinkedHashMap<>();
    private volatile Map<EntityType<?>, List<NpcSpec>> datapackSpecsByType = Map.of();
    private volatile Map<EntityType<?>, List<NpcSpec>> specsByType = Map.of();
    private volatile Map<String, RegistrySourceInfo> sourceInfoByBindingId = Map.of();
    private final AtomicLong snapshotEpoch = new AtomicLong();
    private final Set<String> codeBindingIds = ConcurrentHashMap.newKeySet();
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private boolean frozen;

    private NpcBindingRegistry() {
    }

    public synchronized void register(NpcSpec spec) {
        registerCode(spec);
    }

    public synchronized void registerCode(NpcSpec spec) {
        if (frozen) {
            throw new IllegalStateException("NpcBindingRegistry is frozen - cannot register code npc spec for '" + spec.entityType + "'");
        }
        EntityType<?> entityType = EntityType.byString(spec.entityType).orElse(null);
        if (entityType == null) {
            LOGGER.warn("[NpcBindingRegistry] Unknown entity type '{}', skipping npc spec", spec.entityType);
            return;
        }

        ensureUniqueCodeBindingIds(spec);
        Map<EntityType<?>, List<NpcSpec>> next = mutableSnapshot(codeSpecsByType);
        List<NpcSpec> specs = new ArrayList<>(next.getOrDefault(entityType, List.of()));
        specs.add(copySpec(spec));
        specs.sort(NpcBindingRegistry::compareSpecs);
        next.put(entityType, List.copyOf(specs));
        codeSpecsByType = publishSnapshot(next);
        rebuildMergedSnapshot();
        snapshotEpoch.incrementAndGet();
        LOGGER.debug("[NpcBindingRegistry] Registered npc spec for entity type: {}", spec.entityType);
    }

    public synchronized void replaceAll(Collection<NpcSpec> specs) {
        replaceDatapackSnapshot(specs, snapshotEpoch.incrementAndGet());
    }

    public synchronized void replaceDatapackSnapshot(Collection<NpcSpec> specs, long epoch) {
        Map<EntityType<?>, List<NpcSpec>> staged = new LinkedHashMap<>();
        for (NpcSpec spec : specs) {
            EntityType<?> entityType = EntityType.byString(spec.entityType).orElse(null);
            if (entityType == null) {
                LOGGER.warn("[NpcBindingRegistry] Unknown entity type '{}', skipping npc spec", spec.entityType);
                continue;
            }
            staged.computeIfAbsent(entityType, ignored -> new ArrayList<>()).add(copySpec(spec));
        }
        datapackSpecsByType = publishSnapshot(staged);
        rebuildMergedSnapshot();
        snapshotEpoch.set(epoch);
        LOGGER.info("[NpcBindingRegistry] Published npc binding snapshot. specs={}, entityTypes={}, epoch={}",
                size(), specsByType.size(), epoch);
    }

    public synchronized List<NpcSpec> getDatapackSnapshot() {
        List<NpcSpec> snapshot = new ArrayList<>();
        datapackSpecsByType.values().forEach(specs -> specs.forEach(spec -> snapshot.add(copySpec(spec))));
        return List.copyOf(snapshot);
    }

    public synchronized void freeze() {
        if (frozen) return;
        frozen = true;
        codeSpecsByType = publishSnapshot(codeSpecsByType);
        rebuildMergedSnapshot();
        LOGGER.info("[NpcBindingRegistry] Frozen. code={}, datapack={}, merged={}", codeSize(), datapackSize(), size());
    }

    public synchronized void registerCodeBindingId(String bindingId) {
        if (bindingId != null && !bindingId.isBlank() && codeBindingIds.add(bindingId)) {
            rebuildMergedSnapshot();
        }
    }

    public synchronized void clear() {
        codeSpecsByType = new LinkedHashMap<>();
        datapackSpecsByType = Map.of();
        specsByType = Map.of();
        sourceInfoByBindingId = Map.of();
        codeBindingIds.clear();
        frozen = false;
        snapshotEpoch.incrementAndGet();
        LOGGER.info("[NpcBindingRegistry] Cleared all npc bindings");
    }

    public boolean hasBindingsFor(EntityType<?> entityType) {
        return specsByType.containsKey(entityType);
    }

    @Nullable
    public String resolveDialogueId(Entity entity, ServerPlayer player) {
        return resolve(entity, player).dialogueId();
    }

    @Nullable
    public NpcSpec resolveSpec(Entity entity, ServerPlayer player) {
        return resolve(entity, player).npcSpec();
    }

    public NpcResolution resolve(Entity entity, ServerPlayer player) {
        EntityType<?> type = entity.getType();
        Map<EntityType<?>, List<NpcSpec>> snapshot = specsByType;
        Set<String> reservedCodeBindings = Set.copyOf(codeBindingIds);
        List<NpcSpec> specs = snapshot.get(type);
        List<NpcResolution.CandidateTrace> traces = new ArrayList<>();
        if (specs == null || specs.isEmpty()) {
            return new NpcResolution(EntityRef.of(entity), null, null, null, traces);
        }

        for (NpcSpec spec : specs) {
            if (!evaluateCondition(spec.interactCondition, player, entity, "<spec>", traces,
                    NpcResolution.Outcome.SPEC_CONDITION_REJECTED)) {
                continue;
            }

            if (spec.bindings != null && !spec.bindings.isEmpty()) {
                for (NpcBindingSpec binding : spec.bindings) {
                    String bindingId = bindingId(binding);
                    if (reservedCodeBindings.contains(bindingId)) {
                        traces.add(trace(binding, null, NpcResolution.Outcome.CODE_BINDING_RESERVED,
                                "binding is owned by a code extension"));
                        continue;
                    }
                    if (!evaluateCondition(binding.condition, player, entity, bindingId, traces,
                            NpcResolution.Outcome.BINDING_CONDITION_REJECTED)) {
                        continue;
                    }

                    String dialogueId = resolveBindingDialogueId(entity, binding, traces);
                    if (dialogueId == null || dialogueId.isBlank()) {
                        continue;
                    }

                    traces.add(trace(binding, dialogueId, NpcResolution.Outcome.MATCHED, "matched"));
                    LOGGER.info("[NpcBinding] Matched entityRef={}, player={}, bindingId={}, dialogueId={}",
                            EntityRef.of(entity), player.getUUID(), bindingId, dialogueId);
                    return new NpcResolution(EntityRef.of(entity), copySpec(spec), copyBinding(binding),
                            dialogueId, traces);
                }
            }
        }

        return new NpcResolution(EntityRef.of(entity), null, null, null, traces);
    }

    public int size() {
        return specsByType.values().stream().mapToInt(List::size).sum();
    }

    public int codeSize() {
        return codeSpecsByType.values().stream().mapToInt(List::size).sum();
    }

    public int datapackSize() {
        return datapackSpecsByType.values().stream().mapToInt(List::size).sum();
    }

    public boolean isFrozen() {
        return frozen;
    }

    @Nullable
    public RegistrySourceInfo getSourceInfo(String bindingId) {
        return sourceInfoByBindingId.get(bindingId);
    }

    public long getSnapshotEpoch() {
        return snapshotEpoch.get();
    }

    public Set<EntityType<?>> getAllEntityTypes() {
        return Collections.unmodifiableSet(specsByType.keySet());
    }

    public List<NpcSpec> getSpecsFor(EntityType<?> entityType) {
        List<NpcSpec> specs = specsByType.get(entityType);
        return specs == null ? List.of() : specs.stream().map(NpcBindingRegistry::copySpec).toList();
    }

    private boolean evaluateCondition(@Nullable org.arcadia.arc_quest.condition.ConditionSpec condition,
                                      ServerPlayer player, Entity entity, String bindingId,
                                      List<NpcResolution.CandidateTrace> traces,
                                      NpcResolution.Outcome rejectedOutcome) {
        if (condition == null || condition.isAlways()) return true;
        try {
            boolean matched = conditionEvaluator.evaluateWithEntity(condition, player, entity);
            if (!matched) {
                traces.add(new NpcResolution.CandidateTrace(bindingId, Integer.MIN_VALUE, null,
                        rejectedOutcome, condition.condition));
            }
            return matched;
        } catch (RuntimeException exception) {
            traces.add(new NpcResolution.CandidateTrace(bindingId, Integer.MIN_VALUE, null,
                    NpcResolution.Outcome.EVALUATION_ERROR, exception.getClass().getSimpleName()));
            LOGGER.warn("[NpcBinding] Condition evaluation failed: entityRef={}, player={}, bindingId={}",
                    EntityRef.of(entity), player.getUUID(), bindingId, exception);
            return false;
        }
    }

    @Nullable
    private String resolveBindingDialogueId(Entity entity, NpcBindingSpec binding,
                                            List<NpcResolution.CandidateTrace> traces) {
        if (binding.dialogueIdFromNbt != null && !binding.dialogueIdFromNbt.isBlank()) {
            String nbtKey = binding.dialogueIdFromNbt;
            CompoundTag persistentData = entity.getPersistentData();
            String dialogueId = readNonBlankString(persistentData, nbtKey);
            if (dialogueId == null) {
                CompoundTag fullNbt = new CompoundTag();
                entity.saveWithoutId(fullNbt);
                dialogueId = readNonBlankString(fullNbt, nbtKey);
            }
            if (dialogueId == null) {
                traces.add(trace(binding, null, NpcResolution.Outcome.NBT_VALUE_MISSING, nbtKey));
            }
            return dialogueId;
        }

        if (binding.dialogueId == null || binding.dialogueId.isBlank()) {
            traces.add(trace(binding, null, NpcResolution.Outcome.DIALOGUE_ID_MISSING,
                    "no dialogueId or dialogueIdFromNbt"));
            return null;
        }
        return binding.dialogueId;
    }

    @Nullable
    private static String readNonBlankString(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        String value = tag.getString(key);
        return value.isBlank() ? null : value;
    }

    private static NpcResolution.CandidateTrace trace(NpcBindingSpec binding, @Nullable String dialogueId,
                                                       NpcResolution.Outcome outcome, String detail) {
        return new NpcResolution.CandidateTrace(bindingId(binding), binding.priority, dialogueId, outcome, detail);
    }

    private static NpcSpec copySpec(NpcSpec source) {
        NpcSpec copy = new NpcSpec();
        copy.entityType = source.entityType;
        copy.cancelVanillaInteract = source.cancelVanillaInteract;
        copy.dialogueDistance = source.dialogueDistance;
        copy.shouldLookAtPlayer = source.shouldLookAtPlayer;
        copy.shouldStopMoving = source.shouldStopMoving;
        copy.interactionPolicy = source.interactionPolicy != null
                ? source.interactionPolicy : org.arcadia.arc_quest.npc.spec.NpcInteractionPolicy.PARALLEL_PRIVATE;
        copy.interactCondition = source.interactCondition;
        copy.onDialogueStartCommands = source.onDialogueStartCommands != null
                ? new ArrayList<>(source.onDialogueStartCommands) : new ArrayList<>();
        copy.onDialogueEndCommands = source.onDialogueEndCommands != null
                ? new ArrayList<>(source.onDialogueEndCommands) : new ArrayList<>();
        copy.bindings = source.bindings != null ? source.bindings.stream()
                .map(NpcBindingRegistry::copyBinding)
                .sorted(NpcBindingRegistry::compareBindings)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)) : new ArrayList<>();
        return copy;
    }

    private synchronized void rebuildMergedSnapshot() {
        Set<String> codeSpecBindingIds = collectBindingIds(codeSpecsByType);
        Map<EntityType<?>, List<NpcSpec>> merged = new LinkedHashMap<>();
        Map<String, RegistrySourceInfo> sources = new LinkedHashMap<>();
        int[] loadOrder = {0};

        datapackSpecsByType.forEach((entityType, specs) -> {
            for (NpcSpec spec : specs) {
                NpcSpec filtered = copySpecFilteringBindings(spec, codeSpecBindingIds);
                if (filtered.bindings.isEmpty()) continue;
                merged.computeIfAbsent(entityType, ignored -> new ArrayList<>()).add(filtered);
                for (NpcBindingSpec binding : filtered.bindings) {
                    sources.put(bindingId(binding), new RegistrySourceInfo(
                            RegistrySourceType.DATAPACK, entityType.toString(), loadOrder[0]++, null));
                }
            }
        });

        codeSpecsByType.forEach((entityType, specs) -> {
            for (NpcSpec spec : specs) {
                NpcSpec copied = copySpec(spec);
                merged.computeIfAbsent(entityType, ignored -> new ArrayList<>()).add(copied);
                for (NpcBindingSpec binding : copied.bindings) {
                    String bindingId = bindingId(binding);
                    boolean overridesDatapack = containsBinding(datapackSpecsByType, bindingId);
                    sources.put(bindingId, new RegistrySourceInfo(
                            RegistrySourceType.CODE, "code", loadOrder[0]++,
                            overridesDatapack ? "datapack_ignored_due_to_code_priority" : null));
                }
            }
        });
        for (String bindingId : codeBindingIds) {
            sources.putIfAbsent(bindingId, new RegistrySourceInfo(RegistrySourceType.CODE, "code_extension",
                    loadOrder[0]++, "datapack_binding_reserved_by_code_extension"));
        }
        specsByType = publishSnapshot(merged);
        sourceInfoByBindingId = Collections.unmodifiableMap(sources);
    }

    private void ensureUniqueCodeBindingIds(NpcSpec spec) {
        Set<String> existing = collectBindingIds(codeSpecsByType);
        if (spec.bindings == null) return;
        for (NpcBindingSpec binding : spec.bindings) {
            String bindingId = bindingId(binding);
            if (!bindingId.isBlank() && !existing.add(bindingId)) {
                throw new IllegalStateException("Duplicate code npc binding ID: " + bindingId);
            }
        }
    }

    private static Set<String> collectBindingIds(Map<EntityType<?>, List<NpcSpec>> snapshot) {
        Set<String> ids = new LinkedHashSet<>();
        snapshot.values().forEach(specs -> specs.forEach(spec -> {
            if (spec.bindings != null) spec.bindings.forEach(binding -> {
                String id = bindingId(binding);
                if (!id.isBlank()) ids.add(id);
            });
        }));
        return ids;
    }

    private static boolean containsBinding(Map<EntityType<?>, List<NpcSpec>> snapshot, String bindingId) {
        if (bindingId.isBlank()) return false;
        return snapshot.values().stream().flatMap(Collection::stream)
                .filter(spec -> spec.bindings != null).flatMap(spec -> spec.bindings.stream())
                .anyMatch(binding -> bindingId.equals(bindingId(binding)));
    }

    private static NpcSpec copySpecFilteringBindings(NpcSpec source, Set<String> excludedBindingIds) {
        NpcSpec copy = copySpec(source);
        copy.bindings.removeIf(binding -> excludedBindingIds.contains(bindingId(binding)));
        return copy;
    }

    private static Map<EntityType<?>, List<NpcSpec>> mutableSnapshot(Map<EntityType<?>, List<NpcSpec>> source) {
        Map<EntityType<?>, List<NpcSpec>> copy = new LinkedHashMap<>();
        source.forEach((entityType, specs) -> copy.put(entityType,
                specs.stream().map(NpcBindingRegistry::copySpec).collect(java.util.stream.Collectors.toCollection(ArrayList::new))));
        return copy;
    }

    private static Map<EntityType<?>, List<NpcSpec>> publishSnapshot(Map<EntityType<?>, List<NpcSpec>> source) {
        Map<EntityType<?>, List<NpcSpec>> published = new LinkedHashMap<>();
        source.forEach((entityType, specs) -> {
            List<NpcSpec> copied = specs.stream().map(NpcBindingRegistry::copySpec)
                    .sorted(NpcBindingRegistry::compareSpecs).toList();
            published.put(entityType, copied);
        });
        return Collections.unmodifiableMap(published);
    }

    private static NpcBindingSpec copyBinding(NpcBindingSpec source) {
        NpcBindingSpec copy = new NpcBindingSpec();
        copy.bindingId = source.bindingId;
        copy.dialogueId = source.dialogueId;
        copy.dialogueIdFromNbt = source.dialogueIdFromNbt;
        copy.condition = source.condition;
        copy.priority = source.priority;
        return copy;
    }

    private static int compareSpecs(NpcSpec left, NpcSpec right) {
        NpcBindingSpec leftFirst = left.bindings.isEmpty() ? null : left.bindings.get(0);
        NpcBindingSpec rightFirst = right.bindings.isEmpty() ? null : right.bindings.get(0);
        int priorityCompare = Integer.compare(
                rightFirst != null ? rightFirst.priority : Integer.MIN_VALUE,
                leftFirst != null ? leftFirst.priority : Integer.MIN_VALUE);
        if (priorityCompare != 0) return priorityCompare;
        return bindingId(leftFirst).compareTo(bindingId(rightFirst));
    }

    private static int compareBindings(NpcBindingSpec left, NpcBindingSpec right) {
        int priorityCompare = Integer.compare(right.priority, left.priority);
        if (priorityCompare != 0) return priorityCompare;
        return bindingId(left).compareTo(bindingId(right));
    }

    private static String bindingId(@Nullable NpcBindingSpec binding) {
        return binding == null || binding.bindingId == null ? "" : binding.bindingId;
    }
}
