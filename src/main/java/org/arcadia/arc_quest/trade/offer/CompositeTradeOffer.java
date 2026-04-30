package org.arcadia.arc_quest.trade.offer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.trade.api.CostShortfallLine;
import org.arcadia.arc_quest.trade.api.ITradeOffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 组合交易物 —— 将多个 ITradeOffer 组合为一个。
 * <p>
 * 所有子项必须全部满足 canAfford 才算通过，执行时全部执行。
 */
public final class CompositeTradeOffer implements ITradeOffer {

    private final List<ITradeOffer> children;
    private final Component displayOverride;

    public CompositeTradeOffer(List<ITradeOffer> children, Component displayOverride) {
        this.children = Collections.unmodifiableList(children);
        this.displayOverride = displayOverride;
    }

    public CompositeTradeOffer(List<ITradeOffer> children) {
        this(children, null);
    }

    public static CompositeTradeOffer of(ITradeOffer... offers) {
        return new CompositeTradeOffer(List.of(offers));
    }

    @Override
    public boolean canAfford(ServerPlayer player) {
        for (ITradeOffer child : children) {
            if (!child.canAfford(player)) return false;
        }
        return true;
    }

    @Override
    public void execute(ServerPlayer player) {
        for (ITradeOffer child : children) {
            child.execute(player);
        }
    }

    @Override
    public Component describe() {
        if (displayOverride != null) return displayOverride;
        if (children.size() == 1) return children.get(0).describe();

        var builder = Component.empty();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) builder.append(Component.literal(" + "));
            builder.append(children.get(i).describe());
        }
        return builder;
    }

    @Override
    public List<CostShortfallLine> buildShortfallLines(ServerPlayer player) {
        List<CostShortfallLine> lines = new ArrayList<>();
        for (ITradeOffer child : children) {
            lines.addAll(child.buildShortfallLines(player));
        }
        return lines;
    }

    @Override
    public String getType() {
        return "composite";
    }

    public List<ITradeOffer> getChildren() {
        return children;
    }
}
