package org.arcadia.arc_quest.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.Arc_Quest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI 音效管理器（涵盖交易、任务、对话）。
 * <p>
 * 负责统一管理所有客户端 GUI 相关的音效，确保音效播放逻辑与渲染/业务逻辑解耦。
 */
public final class GuiSoundManager {

    // --- 任务系统 ---
    public static final String CHAPTER_START = "chapter_start";
    public static final String CHAPTER_FAIL = "chapter_fail";
    public static final String CHAPTER_COMPLETE = "chapter_complete";
    public static final String PHASE_START = "phase_start";
    public static final String PHASE_COMPLETE = "phase_complete";
    // --- 对话系统 ---
    public static final String SAY = "say";
    public static final String CHOICE = "choice";
    // --- 交易系统 ---
    public static final String PURCHASE_SUCCESS = "purchase_success";

    // ════════════════════════════════════════
    //  预定义音效键常量
    // ════════════════════════════════════════
    public static final String PURCHASE_FAIL = "purchase_fail";
    public static final String PURCHASE_LOCKED = "purchase_locked";
    private static final Map<String, SoundEvent> SOUND_CACHE = new ConcurrentHashMap<>();
    private static final Minecraft MC = Minecraft.getInstance();
    // 默认音效路径前缀
    private static final String PREFIX = Arc_Quest.MOD_ID + ":trade.";

    /**
     * 注册一个音效事件到缓存中。
     */
    public static void registerSound(String key, ResourceLocation location) {
        SOUND_CACHE.put(key, SoundEvent.createVariableRangeEvent(location));
    }

    public static void registerSound(String key, SoundEvent soundEvent) {
        SOUND_CACHE.put(key, SoundEvent.createVariableRangeEvent(soundEvent.getLocation()));
    }

    /**
     * 播放指定的音效。
     *
     * @param key 音效键（如 "purchase_success", "chapter_start"）
     */
    public static void play(String key) {
        if (MC.player == null || MC.getSoundManager() == null) return;

        SoundEvent sound = SOUND_CACHE.get(key);
        if (sound != null) {
            MC.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
        }
    }

    /**
     * 直接播放一个 SoundEvent 对象。
     *
     * @param soundEvent 音效事件
     */
    public static void play(SoundEvent soundEvent) {
        if (MC.player == null || soundEvent == null) return;
        MC.getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0F));
    }

    public static void play(Holder.Reference<SoundEvent> soundEvent) {
        if (MC.player == null || soundEvent == null) return;
        MC.getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0F));
    }

    /**
     * 初始化默认音效映射（如果模组资源文件中定义了这些音效）。
     * 注意：实际音效文件需要在 assets/arc_quest/sounds.json 中定义。
     */
    public static void initDefaults() {
        registerSound(CHAPTER_START, ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gui.chapter_start"));
        registerSound(CHAPTER_FAIL, ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gui.chapter_fail"));
        registerSound(CHAPTER_COMPLETE, ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gui.chapter_complete"));
        registerSound(PHASE_START, ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gui.phase_start"));
        registerSound(PHASE_COMPLETE, ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gui.phase_complete"));
        registerSound(SAY, ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gui.say"));
        registerSound(CHOICE, ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gui.choice"));
        registerSound(PURCHASE_SUCCESS, ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gui.purchase_success"));
        registerSound(PURCHASE_FAIL, ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gui.purchase_fail"));
        registerSound(PURCHASE_LOCKED, ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gui.purchase_locked"));
    }
}
