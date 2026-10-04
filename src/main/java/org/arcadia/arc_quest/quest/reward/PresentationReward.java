package org.arcadia.arc_quest.quest.reward;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.arcadia.arc_quest.quest.api.QuestTextContext;

/** A client label has no authority to execute or grant its original custom reward. */
public final class PresentationReward implements IReward {
    private final QuestText text;

    public PresentationReward(QuestText text) { this.text = java.util.Objects.requireNonNull(text); }

    @Override public void grant(ServerPlayer player) {
        throw new UnsupportedOperationException("Presentation rewards cannot be granted");
    }

    @Override public String describe() { return describeComponent().getString(); }

    @Override public Component describeComponent() { return text.resolve(null, QuestTextContext.empty()); }
}
