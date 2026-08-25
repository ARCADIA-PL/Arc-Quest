package org.arcadia.arc_quest.dialogue.registry;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.registry.LayeredRegistrySnapshot;
import org.arcadia.arc_quest.data.registry.RegistrySourceInfo;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

public final class DialogueRegistry {
    public static final DialogueRegistry INSTANCE = new DialogueRegistry();
    private Map<String, DialogueTree> codeTrees = new LinkedHashMap<>();
    private Map<String, String> codeNpcBindings = new LinkedHashMap<>();
    private Map<EntityType<?>, String> codeEntityBindings = new LinkedHashMap<>();
    private Map<EntityType<?>, BiFunction<Entity, ServerPlayer, String>> codeEntityDynamicBindings = new LinkedHashMap<>();
    private volatile DatapackSnapshot datapackSnapshot = DatapackSnapshot.empty(0L);
    private volatile LayeredRegistrySnapshot<String, DialogueTree> treeSnapshot = LayeredRegistrySnapshot.empty(codeTrees);
    private final AtomicLong nextDatapackEpoch = new AtomicLong();
    private boolean frozen;

    private DialogueRegistry() {
    }

    public void register(DialogueTree tree) {
        registerCode(tree);
    }

    public synchronized void registerCode(DialogueTree tree) {
        ensureMutable("register dialogue '" + tree.dialogueId() + "'");
        validate(tree);
        if (codeTrees.containsKey(tree.dialogueId())) {
            throw new IllegalStateException("Duplicate dialogue ID: " + tree.dialogueId());
        }
        codeTrees.put(tree.dialogueId(), tree);
        rebuildTreeSnapshot(datapackSnapshot.trees());
        ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Registered code dialogue: {}", tree.dialogueId());
    }

    public synchronized void registerDatapack(DialogueTree tree) {
        validate(tree);
        DatapackSnapshot current = datapackSnapshot;
        Map<String, DialogueTree> trees = new LinkedHashMap<>(current.trees());
        trees.put(tree.dialogueId(), tree);
        publishDatapack(trees, current.npcBindings(), current.entityBindings(), current.dynamicEntityBindings());
    }

    public synchronized void freeze() {
        if (frozen) return;
        frozen = true;
        codeTrees = Collections.unmodifiableMap(new LinkedHashMap<>(codeTrees));
        codeNpcBindings = Collections.unmodifiableMap(new LinkedHashMap<>(codeNpcBindings));
        codeEntityBindings = Collections.unmodifiableMap(new LinkedHashMap<>(codeEntityBindings));
        codeEntityDynamicBindings = Collections.unmodifiableMap(new LinkedHashMap<>(codeEntityDynamicBindings));
        rebuildTreeSnapshot(datapackSnapshot.trees());
        ArcQuestLog.info(ArcQuestLog.Category.DIALOGUE, "Frozen. code={}, datapack={}, merged={}", codeSize(), datapackSize(), size());
    }

    private void validate(DialogueTree tree) {
        List<String> errors = tree.validate();
        if (!errors.isEmpty()) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Dialogue '{}' has validation errors:", tree.dialogueId());
            errors.forEach(error -> ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "  - {}", error));
        }
    }

    @Nullable
    public DialogueTree get(String dialogueId) {
        if (dialogueId == null || dialogueId.isBlank()) return null;
        DialogueTree tree = treeSnapshot.merged().get(dialogueId);
        if (tree == null && !dialogueId.contains(":")) {
            tree = treeSnapshot.merged().get(Arc_Quest.MOD_ID + ":" + dialogueId);
        }
        return tree;
    }

    @Nullable
    public DialogueTree getCodeDefinition(String dialogueId) {
        if (dialogueId == null || dialogueId.isBlank()) return null;
        DialogueTree tree = codeTrees.get(dialogueId);
        if (tree == null && !dialogueId.contains(":")) tree = codeTrees.get(Arc_Quest.MOD_ID + ":" + dialogueId);
        return tree;
    }

    @Nullable
    public RegistrySourceInfo getSourceInfo(String dialogueId) {
        if (dialogueId == null || dialogueId.isBlank()) return null;
        RegistrySourceInfo source = treeSnapshot.sources().get(dialogueId);
        if (source == null && !dialogueId.contains(":")) source = treeSnapshot.sources().get(Arc_Quest.MOD_ID + ":" + dialogueId);
        return source;
    }

    public Collection<DialogueTree> getAll() {
        return treeSnapshot.merged().values();
    }

    public Set<String> getAllIds() {
        return treeSnapshot.merged().keySet();
    }

    public int datapackSize() {
        return treeSnapshot.datapack().size();
    }

    public int codeSize() {
        return codeTrees.size();
    }

    public int size() {
        return treeSnapshot.merged().size();
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void bindNpc(String npcId, String dialogueId) {
        bindNpcCode(npcId, dialogueId);
    }

    public synchronized void bindNpcCode(String npcId, String dialogueId) {
        ensureMutable("bind npc '" + npcId + "'");
        codeNpcBindings.put(npcId, dialogueId);
    }

    public synchronized void bindNpcDatapack(String npcId, String dialogueId) {
        DatapackSnapshot current = datapackSnapshot;
        Map<String, String> bindings = new LinkedHashMap<>(current.npcBindings());
        bindings.put(npcId, dialogueId);
        publishDatapack(current.trees(), bindings, current.entityBindings(), current.dynamicEntityBindings());
    }

    @Nullable
    public String getDialogueForNpc(String npcId) {
        String dialogueId = codeNpcBindings.get(npcId);
        return dialogueId != null ? dialogueId : datapackSnapshot.npcBindings().get(npcId);
    }

    public void bindEntity(EntityType<?> entityType, String dialogueId) {
        bindEntityCode(entityType, dialogueId);
    }

    public synchronized void bindEntityCode(EntityType<?> entityType, String dialogueId) {
        ensureMutable("bind entity type '" + entityType + "'");
        codeEntityBindings.put(entityType, dialogueId);
    }

    public synchronized void bindEntityDatapack(EntityType<?> entityType, String dialogueId) {
        DatapackSnapshot current = datapackSnapshot;
        Map<EntityType<?>, String> bindings = new LinkedHashMap<>(current.entityBindings());
        bindings.put(entityType, dialogueId);
        publishDatapack(current.trees(), current.npcBindings(), bindings, current.dynamicEntityBindings());
    }

    public void bindEntityDynamic(EntityType<?> entityType, BiFunction<Entity, ServerPlayer, String> selector) {
        bindEntityDynamicCode(entityType, selector);
    }

    public synchronized void bindEntityDynamicCode(EntityType<?> entityType,
                                                   BiFunction<Entity, ServerPlayer, String> selector) {
        ensureMutable("bind dynamic entity type '" + entityType + "'");
        codeEntityDynamicBindings.put(entityType, selector);
    }

    public synchronized void bindEntityDynamicDatapack(EntityType<?> entityType,
                                                        BiFunction<Entity, ServerPlayer, String> selector) {
        DatapackSnapshot current = datapackSnapshot;
        Map<EntityType<?>, BiFunction<Entity, ServerPlayer, String>> bindings =
                new LinkedHashMap<>(current.dynamicEntityBindings());
        bindings.put(entityType, selector);
        publishDatapack(current.trees(), current.npcBindings(), current.entityBindings(), bindings);
    }

    @Nullable
    public String getDialogueForEntity(Entity entity, ServerPlayer player) {
        EntityType<?> type = entity.getType();
        String dynamicResult = applyDynamicSelector(codeEntityDynamicBindings.get(type), entity, player, type);
        if (dynamicResult != null) return dynamicResult;
        DatapackSnapshot current = datapackSnapshot;
        dynamicResult = applyDynamicSelector(current.dynamicEntityBindings().get(type), entity, player, type);
        if (dynamicResult != null) return dynamicResult;
        String dialogueId = codeEntityBindings.get(type);
        return dialogueId != null ? dialogueId : current.entityBindings().get(type);
    }

    @Nullable
    private String applyDynamicSelector(@Nullable BiFunction<Entity, ServerPlayer, String> selector,
                                        Entity entity, ServerPlayer player, EntityType<?> type) {
        if (selector == null) return null;
        try {
            return selector.apply(entity, player);
        } catch (Exception exception) {
            ArcQuestLog.error(ArcQuestLog.Category.DIALOGUE, "Error in dynamic entity binding for {}", type, exception);
            return null;
        }
    }

    public synchronized void clearDatapack() {
        publishDatapack(Map.of(), Map.of(), Map.of(), Map.of());
    }

    public synchronized void clearAll() {
        codeTrees = new LinkedHashMap<>();
        codeNpcBindings = new LinkedHashMap<>();
        codeEntityBindings = new LinkedHashMap<>();
        codeEntityDynamicBindings = new LinkedHashMap<>();
        frozen = false;
        publishDatapack(Map.of(), Map.of(), Map.of(), Map.of());
    }

    public synchronized long replaceDatapack(Collection<DialogueTree> trees,
                                             Map<String, String> npcBindings,
                                             Map<EntityType<?>, String> entityBindings) {
        return replaceDatapackSnapshot(trees, npcBindings, entityBindings, nextDatapackEpoch.incrementAndGet());
    }

    public synchronized long replaceDatapackSnapshot(Collection<DialogueTree> trees,
                                                     Map<String, String> npcBindings,
                                                     Map<EntityType<?>, String> entityBindings,
                                                     long epoch) {
        Map<String, DialogueTree> treesById = new LinkedHashMap<>();
        for (DialogueTree tree : trees) {
            validate(tree);
            if (treesById.putIfAbsent(tree.dialogueId(), tree) != null) {
                throw new IllegalStateException("Duplicate datapack dialogue ID: " + tree.dialogueId());
            }
        }
        nextDatapackEpoch.accumulateAndGet(epoch, Math::max);
        publishDatapack(treesById, npcBindings, entityBindings, Map.of(), epoch);
        return epoch;
    }

    public long getDatapackEpoch() {
        return datapackSnapshot.epoch();
    }

    public DatapackSnapshotView getDatapackSnapshot() {
        DatapackSnapshot current = datapackSnapshot;
        return new DatapackSnapshotView(List.copyOf(current.trees().values()), current.npcBindings(),
                current.entityBindings(), current.epoch());
    }

    private long publishDatapack(Map<String, DialogueTree> trees,
                                 Map<String, String> npcBindings,
                                 Map<EntityType<?>, String> entityBindings,
                                 Map<EntityType<?>, BiFunction<Entity, ServerPlayer, String>> dynamicEntityBindings) {
        long epoch = nextDatapackEpoch.incrementAndGet();
        publishDatapack(trees, npcBindings, entityBindings, dynamicEntityBindings, epoch);
        return epoch;
    }

    private void publishDatapack(Map<String, DialogueTree> trees,
                                 Map<String, String> npcBindings,
                                 Map<EntityType<?>, String> entityBindings,
                                 Map<EntityType<?>, BiFunction<Entity, ServerPlayer, String>> dynamicEntityBindings,
                                 long epoch) {
        datapackSnapshot = new DatapackSnapshot(Map.copyOf(trees), Map.copyOf(npcBindings),
                Map.copyOf(entityBindings), Map.copyOf(dynamicEntityBindings), epoch);
        rebuildTreeSnapshot(trees);
    }

    private void rebuildTreeSnapshot(Map<String, DialogueTree> datapackTrees) {
        LayeredRegistrySnapshot<String, DialogueTree> next = LayeredRegistrySnapshot.create(codeTrees, datapackTrees);
        next.sources().forEach((id, source) -> {
            if (source.ignoredReason() != null) {
                ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Datapack dialogue '{}' ignored because code-defined dialogue has priority.", id);
            }
        });
        treeSnapshot = next;
    }

    private void ensureMutable(String operation) {
        if (frozen) throw new IllegalStateException("DialogueRegistry is frozen - cannot " + operation);
    }

    public record DatapackSnapshotView(List<DialogueTree> trees, Map<String, String> npcBindings,
                                       Map<EntityType<?>, String> entityBindings, long epoch) {
    }

    private record DatapackSnapshot(
            Map<String, DialogueTree> trees,
            Map<String, String> npcBindings,
            Map<EntityType<?>, String> entityBindings,
            Map<EntityType<?>, BiFunction<Entity, ServerPlayer, String>> dynamicEntityBindings,
            long epoch
    ) {
        private static DatapackSnapshot empty(long epoch) {
            return new DatapackSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), epoch);
        }
    }
}
