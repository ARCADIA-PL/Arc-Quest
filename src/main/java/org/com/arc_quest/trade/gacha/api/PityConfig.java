package org.com.arc_quest.trade.gacha.api;

import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.quest.api.ICondition;
import org.jetbrains.annotations.Nullable;

/**
 * 保底系统配置。
 * <p>
 * 支持指定保底抽奖项、条件重置和冷却系统，与商店系统保持一致。
 */
public class PityConfig {
    
    private final int pityThreshold;              // 保底触发阈值（抽奖次数）
    @Nullable
    private final String guaranteedItemId;        // 保底指定的抽奖项 ID（null 则按稀有度）
    @Nullable
    private final GachaItem.Rarity guaranteedRarity; // 保底稀有度（当 guaranteedItemId 为 null 时使用）
    private final CooldownType resetCooldownType; // 重置冷却类型
    private final int resetCooldownValue;         // 重置冷却值
    @Nullable
    private final ICondition resetCondition;      // 重置条件（满足时重置计数）
    private final boolean resetOnTrigger;         // 触发后是否重置计数
    
    public PityConfig(int pityThreshold, 
                      @Nullable String guaranteedItemId,
                      @Nullable GachaItem.Rarity guaranteedRarity,
                      CooldownType resetCooldownType,
                      int resetCooldownValue,
                      @Nullable ICondition resetCondition,
                      boolean resetOnTrigger) {
        if (guaranteedItemId == null && guaranteedRarity == null) {
            throw new IllegalArgumentException("Either guaranteedItemId or guaranteedRarity must be specified");
        }
        this.pityThreshold = pityThreshold;
        this.guaranteedItemId = guaranteedItemId;
        this.guaranteedRarity = guaranteedRarity;
        this.resetCooldownType = resetCooldownType;
        this.resetCooldownValue = resetCooldownValue;
        this.resetCondition = resetCondition;
        this.resetOnTrigger = resetOnTrigger;
    }
    
    /**
     * 简化构造函数（按稀有度保底，无冷却和条件）。
     */
    public PityConfig(int pityThreshold, GachaItem.Rarity guaranteedRarity, boolean resetOnTrigger) {
        this(pityThreshold, null, guaranteedRarity, CooldownType.NONE, 0, null, resetOnTrigger);
    }
    
    /**
     * 简化构造函数（指定物品 ID 保底，无冷却和条件）。
     */
    public PityConfig(int pityThreshold, String guaranteedItemId, boolean resetOnTrigger) {
        this(pityThreshold, guaranteedItemId, null, CooldownType.NONE, 0, null, resetOnTrigger);
    }
    
    public int getPityThreshold() { return pityThreshold; }
    @Nullable
    public String getGuaranteedItemId() { return guaranteedItemId; }
    @Nullable
    public GachaItem.Rarity getGuaranteedRarity() { return guaranteedRarity; }
    public CooldownType getResetCooldownType() { return resetCooldownType; }
    public int getResetCooldownValue() { return resetCooldownValue; }
    @Nullable
    public ICondition getResetCondition() { return resetCondition; }
    public boolean shouldResetOnTrigger() { return resetOnTrigger; }
}
