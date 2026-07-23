package org.arcadia.arc_quest.dialogue.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * 全局对话树注册表（线程安全，支持代码层 / 数据包层分离）。
 */
public final class DialogueRegistry {

    public static final DialogueRegistry INSTANCE = new DialogueRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, DialogueTree> codeTrees = new ConcurrentHashMap<>();
    private volatile DatapackSnapshot datapackSnapshot = DatapackSnapshot.empty(0L);
    private final AtomicLong nextDatapackEpoch = new AtomicLong();

    private final Map<String, String> codeNpcBindings = new ConcurrentHashMap<>();

    private final Map<EntityType<?>, String> codeEntityBindings = new ConcurrentHashMap<>();

    private final Map<EntityType<?>, BiFunction<Entity, ServerPlayer, String>> codeEntityDynamicBindings =
            new ConcurrentHashMap<>();

    private DialogueRegistry() {
    }

    public void register(DialogueTree tree) {
        registerCode(tree);
    }

    public void registerCode(DialogueTree tree) {
        validate(tree);
        codeTrees.put(tree.dialogueId(), tree);
        LOGGER.debug("[DialogueRegistry] Registered code dialogue: {}", tree.dialogueId());
    }

    public synchronized void registerDatapack(DialogueTree tree) {
        validate(tree);
        DatapackSnapshot current = datapackSnapshot;
        Map<String, DialogueTree> trees = new LinkedHashMap<>(current.trees());
        trees.put(tree.dialogueId(), tree);
        publishDatapack(trees, current.npcBindings(), current.entityBindings(), current.dynamicEntityBindings());
        LOGGER.debug("[DialogueRegistry] Registered datapack dialogue: {}", tree.dialogueId());
    }

    private void validate(DialogueTree tree) {
        List<String> errors = tree.validate();
        if (!errors.isEmpty()) {
            LOGGER.warn("[DialogueRegistry] Dialogue '{}' has validation errors:", tree.dialogueId());
            errors.forEach(e -> LOGGER.warn("  - {}", e));
        }
    }

    @Nullable
    public DialogueTree get(String dialogueId) {
        DialogueTree tree = getMergedTree(dialogueId);
        if (tree != null) {
            return tree;
        }
        if (!dialogueId.contains(":")) {
            return getMergedTree(Arc_Quest.MOD_ID + ":" + dialogueId);
        }
        return null;
    }

    @Nullable
    private DialogueTree getMergedTree(String dialogueId) {
        DialogueTree tree = codeTrees.get(dialogueId);
        return tree != null ? tree : datapackSnapshot.trees().get(dialogueId);
    }

    public Collection<DialogueTree> getAll() {
        return Collections.unmodifiableCollection(getMergedTrees().values());
    }

    public Set<String> getAllIds() {
        return Collections.unmodifiableSet(getMergedTrees().keySet());
    }

    public int datapackSize() {
        return datapackSnapshot.trees().size();
    }

    public int codeSize() {
        return codeTrees.size();
    }

    public int size() {
        return getMergedTrees().size();
    }

    private Map<String, DialogueTree> getMergedTrees() {
        Map<String, DialogueTree> merged = new LinkedHashMap<>(datapackSnapshot.trees());
        merged.putAll(codeTrees);
        return merged;
    }

    public void bindNpc(String npcId, String dialogueId) {
        bindNpcCode(npcId, dialogueId);
    }

    public void bindNpcCode(String npcId, String dialogueId) {
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

    public void bindEntityCode(EntityType<?> entityType, String dialogueId) {
        codeEntityBindings.put(entityType, dialogueId);
        LOGGER.debug("[DialogueRegistry] Bound code entity type {} → dialogue '{}'", entityType, dialogueId);
    }

    public synchronized void bindEntityDatapack(EntityType<?> entityType, String dialogueId) {
        DatapackSnapshot current = datapackSnapshot;
        Map<EntityType<?>, String> bindings = new LinkedHashMap<>(current.entityBindings());
        bindings.put(entityType, dialogueId);
        publishDatapack(current.trees(), current.npcBindings(), bindings, current.dynamicEntityBindings());
        LOGGER.debug("[DialogueRegistry] Bound datapack entity type {} → dialogue '{}'", entityType, dialogueId);
    }

    public void bindEntityDynamic(EntityType<?> entityType,
                                  BiFunction<Entity, ServerPlayer, String> selector) {
        bindEntityDynamicCode(entityType, selector);
    }

    public void bindEntityDynamicCode(EntityType<?> entityType,
                                      BiFunction<Entity, ServerPlayer, String> selector) {
        codeEntityDynamicBindings.put(entityType, selector);
        LOGGER.debug("[DialogueRegistry] Bound code dynamic entity type {} → selector", entityType);
    }

    public synchronized void bindEntityDynamicDatapack(EntityType<?> entityType,
                                                        BiFunction<Entity, ServerPlayer, String> selector) {
        DatapackSnapshot current = datapackSnapshot;
        Map<EntityType<?>, BiFunction<Entity, ServerPlayer, String>> bindings =
                new LinkedHashMap<>(current.dynamicEntityBindings());
        bindings.put(entityType, selector);
        publishDatapack(current.trees(), current.npcBindings(), current.entityBindings(), bindings);
        LOGGER.debug("[DialogueRegistry] Bound datapack dynamic entity type {} → selector", entityType);
    }

    @Nullable
    public String getDialogueForEntity(Entity entity, ServerPlayer player) {
        EntityType<?> type = entity.getType();

        String dynamicResult = applyDynamicSelector(codeEntityDynamicBindings.get(type), entity, player, type);
        if (dynamicResult != null) {
            return dynamicResult;
        }

        DatapackSnapshot snapshot = datapackSnapshot;
        dynamicResult = applyDynamicSelector(snapshot.dynamicEntityBindings().get(type), entity, player, type);
        if (dynamicResult != null) {
            return dynamicResult;
        }

        String dialogueId = codeEntityBindings.get(type);
        return dialogueId != null ? dialogueId : snapshot.entityBindings().get(type);
    }

    @Nullable
    private String applyDynamicSelector(@Nullable BiFunction<Entity, ServerPlayer, String> selector,
                                        Entity entity, ServerPlayer player, EntityType<?> type) {
        if (selector == null) {
            return null;
        }
        try {
            return selector.apply(entity, player);
        } catch (Exception e) {
            LOGGER.error("[DialogueRegistry] Error in dynamic entity binding for {}: {}", type, e.getMessage());
            return null;
        }
    }

    public synchronized void clearDatapack() {
        publishDatapack(Map.of(), Map.of(), Map.of(), Map.of());
        LOGGER.info("[DialogueRegistry] Cleared datapack dialogue trees and bindings.");
    }

    public synchronized void clearAll() {
        codeTrees.clear();
        codeNpcBindings.clear();
        codeEntityBindings.clear();
        codeEntityDynamicBindings.clear();
        publishDatapack(Map.of(), Map.of(), Map.of(), Map.of());
        LOGGER.info("[DialogueRegistry] Cleared all dialogue trees and bindings.");
    }

    public synchronized long replaceDatapack(Collection<DialogueTree> trees,
                                             Map<String, String> npcBindings,
                                             Map<EntityType<?>, String> entityBindings) {
        Map<String, DialogueTree> treesById = new LinkedHashMap<>();
        for (DialogueTree tree : trees) {
            validate(tree);
            treesById.put(tree.dialogueId(), tree);
        }
        return publishDatapack(treesById, npcBindings, entityBindings, Map.of());
    }

    public long getDatapackEpoch() {
        return datapackSnapshot.epoch();
    }

    private long publishDatapack(Map<String, DialogueTree> trees,
                                 Map<String, String> npcBindings,
                                 Map<EntityType<?>, String> entityBindings,
                                 Map<EntityType<?>, BiFunction<Entity, ServerPlayer, String>> dynamicEntityBindings) {
        long epoch = nextDatapackEpoch.incrementAndGet();
        datapackSnapshot = new DatapackSnapshot(
                Map.copyOf(trees),
                Map.copyOf(npcBindings),
                Map.copyOf(entityBindings),
                Map.copyOf(dynamicEntityBindings),
                epoch
        );
        return epoch;
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
