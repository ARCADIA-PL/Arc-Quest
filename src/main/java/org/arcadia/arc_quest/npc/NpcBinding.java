package org.arcadia.arc_quest.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * NPC 绑定注册器 —— 在 {@code getDialogueTreeId} 方法内使用。
 * <p>
 * Extension 内部通过 {@link #bindDialogueForNpc(String, String)} 注册绑定并返回对话 ID，
 * 调用方可通过 {@link #getEntries()} 获取所有命中记录用于日志追踪。
 */
public final class NpcBinding {

    private final List<Entry> entries = new ArrayList<>();

    public String bindDialogueForNpc(String bindingId, String dialogueId) {
        entries.add(new Entry(bindingId, dialogueId));
        return dialogueId;
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public record Entry(String bindingId, String dialogueId) {}
}