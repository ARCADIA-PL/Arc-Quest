package org.com.arc_quest.dialogue.registry;

import com.mojang.logging.LogUtils;
import org.com.arc_quest.dialogue.api.DialogueTree;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局对话树注册表（线程安全，支持热重载）。
 */
public final class DialogueRegistry {

    public static final DialogueRegistry INSTANCE = new DialogueRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, DialogueTree> trees = new ConcurrentHashMap<>();

    /** NPC ID → 对话树 ID 映射。 */
    private final Map<String, String> npcBindings = new ConcurrentHashMap<>();

    private DialogueRegistry() {}

    public void register(DialogueTree tree) {
        List<String> errors = tree.validate();
        if (!errors.isEmpty()) {
            LOGGER.warn("[DialogueRegistry] Dialogue '{}' has validation errors:", tree.dialogueId());
            errors.forEach(e -> LOGGER.warn("  - {}", e));
        }
        trees.put(tree.dialogueId(), tree);
        LOGGER.debug("[DialogueRegistry] Registered dialogue: {}", tree.dialogueId());
    }

    public DialogueTree get(String dialogueId) {
        return trees.get(dialogueId);
    }

    public Collection<DialogueTree> getAll() {
        return Collections.unmodifiableCollection(trees.values());
    }

    public Set<String> getAllIds() {
        return Collections.unmodifiableSet(trees.keySet());
    }

    /** 绑定 NPC ID 到对话树。 */
    public void bindNpc(String npcId, String dialogueId) {
        npcBindings.put(npcId, dialogueId);
    }

    /** 获取 NPC 绑定的对话树 ID。 */
    public String getDialogueForNpc(String npcId) {
        return npcBindings.get(npcId);
    }

    /** 清空所有（重载前调用）。 */
    public void clearAll() {
        trees.clear();
        npcBindings.clear();
        LOGGER.info("[DialogueRegistry] Cleared all dialogue trees and NPC bindings.");
    }
}