package org.arcadia.arc_quest.npc.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.arcadia.arc_quest.condition.ConditionEvaluator;
import org.arcadia.arc_quest.npc.spec.NpcBindingSpec;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcBindingRegistry {

    public static final NpcBindingRegistry INSTANCE = new NpcBindingRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<EntityType<?>, List<NpcSpec>> specsByType = new ConcurrentHashMap<>();
    private final Set<String> codeBindingIds = ConcurrentHashMap.newKeySet();
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

    private NpcBindingRegistry() {
    }

    public void register(NpcSpec spec) {
        EntityType<?> entityType = EntityType.byString(spec.entityType).orElse(null);
        if (entityType == null) {
            LOGGER.warn("[NpcBindingRegistry] Unknown entity type '{}', skipping npc spec", spec.entityType);
            return;
        }

        if (spec.bindings != null) {
            spec.bindings.removeIf(binding -> {
                if (binding.bindingId != null && codeBindingIds.contains(binding.bindingId)) {
                    LOGGER.debug("[NpcBindingRegistry] Binding '{}' overridden by code, skipping", binding.bindingId);
                    return true;
                }
                return false;
            });
        }

        specsByType.computeIfAbsent(entityType, k -> new ArrayList<>()).add(spec);
        LOGGER.debug("[NpcBindingRegistry] Registered npc spec for entity type: {}", spec.entityType);
    }

    public void registerCodeBindingId(String bindingId) {
        if (bindingId != null && !bindingId.isBlank()) {
            codeBindingIds.add(bindingId);
        }
    }

    public void clear() {
        specsByType.clear();
        LOGGER.info("[NpcBindingRegistry] Cleared all npc bindings");
    }

    public boolean hasBindingsFor(EntityType<?> entityType) {
        return specsByType.containsKey(entityType);
    }

    @Nullable
    public String resolveDialogueId(Entity entity, ServerPlayer player) {
        EntityType<?> type = entity.getType();
        List<NpcSpec> specs = specsByType.get(type);
        if (specs == null || specs.isEmpty()) {
            return null;
        }

        for (NpcSpec spec : specs) {
            if (spec.interactCondition != null && !spec.interactCondition.isAlways()) {
                if (!conditionEvaluator.evaluateWithEntity(spec.interactCondition, player, entity)) {
                    continue;
                }
            }

            if (spec.bindings == null || spec.bindings.isEmpty()) {
                continue;
            }

            List<NpcBindingSpec> sorted = new ArrayList<>(spec.bindings);
            sorted.sort((a, b) -> Integer.compare(b.priority, a.priority));

            for (NpcBindingSpec binding : sorted) {
                if (binding.condition != null && !binding.condition.isAlways()) {
                    if (!conditionEvaluator.evaluateWithEntity(binding.condition, player, entity)) {
                        continue;
                    }
                }

                if (binding.dialogueIdFromNbt != null && !binding.dialogueIdFromNbt.isBlank()) {
                    String nbtKey = binding.dialogueIdFromNbt;
                    CompoundTag persistentData = entity.getPersistentData();
                    if (persistentData.contains(nbtKey)) {
                        return persistentData.getString(nbtKey);
                    }
                    CompoundTag fullNbt = new CompoundTag();
                    entity.saveWithoutId(fullNbt);
                    if (fullNbt.contains(nbtKey)) {
                        return fullNbt.getString(nbtKey);
                    }
                    continue;
                }

                if (binding.dialogueId != null && !binding.dialogueId.isBlank()) {
                    return binding.dialogueId;
                }
            }
        }

        return null;
    }

    @Nullable
    public NpcSpec resolveSpec(Entity entity, ServerPlayer player) {
        EntityType<?> type = entity.getType();
        List<NpcSpec> specs = specsByType.get(type);
        if (specs == null || specs.isEmpty()) {
            return null;
        }

        for (NpcSpec spec : specs) {
            if (spec.interactCondition != null && !spec.interactCondition.isAlways()) {
                if (!conditionEvaluator.evaluateWithEntity(spec.interactCondition, player, entity)) {
                    continue;
                }
            }

            if (spec.bindings != null && !spec.bindings.isEmpty()) {
                for (NpcBindingSpec binding : spec.bindings) {
                    boolean hasDialogue = (binding.dialogueId != null && !binding.dialogueId.isBlank())
                            || (binding.dialogueIdFromNbt != null && !binding.dialogueIdFromNbt.isBlank());
                    if (hasDialogue) {
                        if (binding.condition == null || binding.condition.isAlways()
                                || conditionEvaluator.evaluateWithEntity(binding.condition, player, entity)) {
                            return spec;
                        }
                    }
                }
            }
        }

        return null;
    }

    public int size() {
        return specsByType.values().stream().mapToInt(List::size).sum();
    }
}