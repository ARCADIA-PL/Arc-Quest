package org.arcadia.arc_quest.trade.offer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.arcadia.arc_quest.trade.api.ITradeOffer;

import java.util.Objects;

/**
 * 效果交易物 —— 给予玩家药水效果。
 * <p>
 * 通常仅作为奖励侧使用。作为成本侧时，检查玩家是否拥有该效果并移除。
 */
public final class EffectTradeOffer implements ITradeOffer {

    private final MobEffect effect;
    private final int durationTicks;
    private final int amplifier;
    private final boolean isCost;

    public EffectTradeOffer(MobEffect effect, int durationTicks, int amplifier, boolean isCost) {
        Objects.requireNonNull(effect);
        this.effect = effect;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.isCost = isCost;
    }

    public static EffectTradeOffer reward(MobEffect effect, int durationTicks, int amplifier) {
        return new EffectTradeOffer(effect, durationTicks, amplifier, false);
    }

    public static EffectTradeOffer reward(MobEffect effect, int durationSeconds) {
        return new EffectTradeOffer(effect, durationSeconds * 20, 0, false);
    }

    @Override
    public boolean canAfford(ServerPlayer player) {
        if (!isCost) return true;
        return player.hasEffect(effect);
    }

    @Override
    public void execute(ServerPlayer player) {
        if (isCost) {
            player.removeEffect(effect);
        } else {
            player.addEffect(new MobEffectInstance(effect, durationTicks, amplifier));
        }
    }

    @Override
    public Component describe() {
        int seconds = durationTicks / 20;
        String roman = amplifier > 0 ? " " + toRoman(amplifier + 1) : "";
        return Component.translatable("arc_quest.trade.effect", effect.getDisplayName(), roman, seconds);
    }

    @Override
    public int getDisplayAmount() {
        return 0;
    }

    @Override
    public String getType() {
        return "effect";
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
