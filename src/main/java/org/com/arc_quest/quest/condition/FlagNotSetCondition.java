package org.com.arc_quest.quest.condition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public final class FlagNotSetCondition implements ICondition {

    private final String flag;

    public FlagNotSetCondition(String flag) {
        this.flag = flag;
    }

    @Override
    public boolean test(@Nullable ServerPlayer player,
                        Set<ResourceLocation> completedQuests,
                        Set<String> flags,
                        Map<String, Integer> variables) {
        return !flags.contains(this.flag);
    }

    @Override
    public String describe() {
        return "FlagNotSet(" + this.flag + ")";
    }
}