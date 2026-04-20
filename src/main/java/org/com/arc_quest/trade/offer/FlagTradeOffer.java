package org.com.arc_quest.trade.offer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.api.ITradeOffer;

/**
 * Flag 交易物 —— 设置或要求全局 Flag。
 * <p>
 * 作为成本时：要求玩家已有指定 Flag（不扣除）。
 * 作为奖励时：为玩家设置 Flag。
 */
public final class FlagTradeOffer implements ITradeOffer {

    private final String flag;
    private final boolean isCost;

    public FlagTradeOffer(String flag, boolean isCost) {
        this.flag = flag;
        this.isCost = isCost;
    }

    public static FlagTradeOffer requireFlag(String flag) {
        return new FlagTradeOffer(flag, true);
    }

    public static FlagTradeOffer rewardFlag(String flag) {
        return new FlagTradeOffer(flag, false);
    }

    @Override
    public boolean canAfford(ServerPlayer player) {
        if (!isCost) return true;
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        return cap != null && cap.hasFlag(flag);
    }

    @Override
    public void execute(ServerPlayer player) {
        if (!isCost) {
            IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
            if (cap != null) {
                cap.setFlag(flag);
            }
        }
    }

    @Override
    public Component describe() {
        return Component.translatable(isCost ? "arc_quest.trade.flag.require" : "arc_quest.trade.flag.reward", flag);
    }

    @Override
    public String getType() {
        return "flag";
    }
}
