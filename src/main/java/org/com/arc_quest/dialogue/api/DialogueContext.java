package org.com.arc_quest.dialogue.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * 对话运行时上下文 —— 在服务端创建，可选传输到客户端。
 * <p>
 * 核心用途：
 * <ul>
 *   <li>在 {@link org.com.arc_quest.dialogue.runtime.DialogueSession#processText} 中进行变量替换</li>
 *   <li>传递 NPC 动态状态（声望、任务进度等）</li>
 *   <li>通过网络包发送给客户端用于 UI 定制</li>
 * </ul>
 * <p>
 * 文本占位符格式：{@code {key}}，例如 {@code "你的声望：{reputation}"}
 */
public class DialogueContext {

    private final CompoundTag data;

    public DialogueContext() {
        this.data = new CompoundTag();
    }

    public DialogueContext(CompoundTag data) {
        this.data = data != null ? data.copy() : new CompoundTag();
    }

    // ═══════════════════════════════════════════════════════
    //  写入（服务端使用）
    // ═══════════════════════════════════════════════════════

    public DialogueContext put(String key, String value) {
        data.putString(key, value);
        return this;
    }

    public DialogueContext put(String key, int value) {
        data.putInt(key, value);
        return this;
    }

    public DialogueContext put(String key, boolean value) {
        data.putBoolean(key, value);
        return this;
    }

    public DialogueContext put(String key, float value) {
        data.putFloat(key, value);
        return this;
    }

    // ═══════════════════════════════════════════════════════
    //  读取
    // ═══════════════════════════════════════════════════════

    public String getString(String key, String defaultValue) {
        return data.contains(key, Tag.TAG_STRING) ? data.getString(key) : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        return data.contains(key, Tag.TAG_INT) ? data.getInt(key) : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return data.contains(key) ? data.getBoolean(key) : defaultValue;
    }

    public float getFloat(String key, float defaultValue) {
        return data.contains(key, Tag.TAG_FLOAT) ? data.getFloat(key) : defaultValue;
    }

    public boolean has(String key) {
        return data.contains(key);
    }

    // ═══════════════════════════════════════════════════════
    //  文本变量替换
    // ═══════════════════════════════════════════════════════

    /**
     * 将文本中的 {@code {key}} 占位符替换为 context 中对应的值。
     * <p>
     * 例: {@code "你好，{playerName}！声望：{reputation}"} →
     *     {@code "你好，Steve！声望：42"}
     *
     * @param template 包含 {key} 占位符的原始文本
     * @return 替换后的文本
     */
    public String resolve(String template) {
        if (template == null || template.isEmpty()) return template;

        String result = template;
        for (String key : data.getAllKeys()) {
            String placeholder = "{" + key + "}";
            if (result.contains(placeholder)) {
                Tag tag = data.get(key);
                String value;
                if (tag instanceof StringTag st) {
                    value = st.getAsString();
                } else if (tag instanceof NumericTag nt) {
                    // 整数不带小数点
                    Number num = nt.getAsNumber();
                    if (num instanceof Float || num instanceof Double) {
                        value = String.format("%.1f", num.doubleValue());
                    } else {
                        value = String.valueOf(num.intValue());
                    }
                } else {
                    value = tag != null ? tag.getAsString() : "";
                }
                result = result.replace(placeholder, value);
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════
    //  合并
    // ═══════════════════════════════════════════════════════

    /**
     * 将另一个 context 的所有键值合并到当前 context（覆盖同名键）。
     */
    public DialogueContext merge(DialogueContext other) {
        if (other != null && !other.isEmpty()) {
            this.data.merge(other.data);
        }
        return this;
    }

    // ═══════════════════════════════════════════════════════
    //  序列化
    // ═══════════════════════════════════════════════════════

    public CompoundTag toTag() {
        return data.copy();
    }

    public static DialogueContext fromTag(CompoundTag tag) {
        return new DialogueContext(tag);
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public String toString() {
        return "DialogueContext" + data;
    }
}