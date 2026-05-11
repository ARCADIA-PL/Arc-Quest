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
import java.util.function.BiFunction;

/**
 * 全局对话树注册表（线程安全，支持代码层 / 数据包层分离）。
 */
public final class DialogueRegistry {

    public static final DialogueRegistry INSTANCE = new DialogueRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, DialogueTree> codeTrees = new ConcurrentHashMap<>();
    private final Map<String, DialogueTree> datapackTrees = new ConcurrentHashMap<>();

    private final Map<String, String> codeNpcBindings = new ConcurrentHashMap<>();
    private final Map<String, String> datapackNpcBindings = new ConcurrentHashMap<>();

    private final Map<EntityType<?>, String> codeEntityBindings = new ConcurrentHashMap<>();
    private final Map<EntityType<?>, String> datapackEntityBindings = new ConcurrentHashMap<>();

    private final Map<EntityType<?>, BiFunction<Entity, ServerPlayer, String>> codeEntityDynamicBindings =
            new ConcurrentHashMap<>();
    private final Map<EntityType<?>, BiFunction<Entity, ServerPlayer, String>> datapackEntityDynamicBindings =
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

    public void registerDatapack(DialogueTree tree) {
        validate(tree);
        datapackTrees.put(tree.dialogueId(), tree);
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
        DialogueTree tree = datapackTrees.get(dialogueId);
        return tree != null ? tree : codeTrees.get(dialogueId);
    }

    public Collection<DialogueTree> getAll() {
        return Collections.unmodifiableCollection(getMergedTrees().values());
    }

    public Set<String> getAllIds() {
        return Collections.unmodifiableSet(getMergedTrees().keySet());
    }

    private Map<String, DialogueTree> getMergedTrees() {
        Map<String, DialogueTree> merged = new LinkedHashMap<>(codeTrees);
        merged.putAll(datapackTrees);
        return merged;
    }

    public void bindNpc(String npcId, String dialogueId) {
        bindNpcCode(npcId, dialogueId);
    }

    public void bindNpcCode(String npcId, String dialogueId) {
        codeNpcBindings.put(npcId, dialogueId);
    }

    public void bindNpcDatapack(String npcId, String dialogueId) {
        datapackNpcBindings.put(npcId, dialogueId);
    }

    @Nullable
    public String getDialogueForNpc(String npcId) {
        String dialogueId = datapackNpcBindings.get(npcId);
        return dialogueId != null ? dialogueId : codeNpcBindings.get(npcId);
    }

    public void bindEntity(EntityType<?> entityType, String dialogueId) {
        bindEntityCode(entityType, dialogueId);
    }

    public void bindEntityCode(EntityType<?> entityType, String dialogueId) {
        codeEntityBindings.put(entityType, dialogueId);
        LOGGER.debug("[DialogueRegistry] Bound code entity type {} → dialogue '{}'", entityType, dialogueId);
    }

    public void bindEntityDatapack(EntityType<?> entityType, String dialogueId) {
        datapackEntityBindings.put(entityType, dialogueId);
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

    public void bindEntityDynamicDatapack(EntityType<?> entityType,
                                          BiFunction<Entity, ServerPlayer, String> selector) {
        datapackEntityDynamicBindings.put(entityType, selector);
        LOGGER.debug("[DialogueRegistry] Bound datapack dynamic entity type {} → selector", entityType);
    }

    @Nullable
    public String getDialogueForEntity(Entity entity, ServerPlayer player) {
        EntityType<?> type = entity.getType();

        String dynamicResult = applyDynamicSelector(datapackEntityDynamicBindings.get(type), entity, player, type);
        if (dynamicResult != null) {
            return dynamicResult;
        }

        dynamicResult = applyDynamicSelector(codeEntityDynamicBindings.get(type), entity, player, type);
        if (dynamicResult != null) {
            return dynamicResult;
        }

        String dialogueId = datapackEntityBindings.get(type);
        return dialogueId != null ? dialogueId : codeEntityBindings.get(type);
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

    public void clearDatapack() {
        datapackTrees.clear();
        datapackNpcBindings.clear();
        datapackEntityBindings.clear();
        datapackEntityDynamicBindings.clear();
        LOGGER.info("[DialogueRegistry] Cleared datapack dialogue trees and bindings.");
    }

    public void clearAll() {
        codeTrees.clear();
        datapackTrees.clear();
        codeNpcBindings.clear();
        datapackNpcBindings.clear();
        codeEntityBindings.clear();
        datapackEntityBindings.clear();
        codeEntityDynamicBindings.clear();
        datapackEntityDynamicBindings.clear();
        LOGGER.info("[DialogueRegistry] Cleared all dialogue trees and bindings.");
    }
}
